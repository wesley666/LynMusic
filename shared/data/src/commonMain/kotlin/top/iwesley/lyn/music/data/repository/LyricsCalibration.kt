package top.iwesley.lyn.music.data.repository

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.LyricsCacheEntity
import top.iwesley.lyn.music.data.db.LyricsOffsetEntity
import top.iwesley.lyn.music.domain.*

sealed interface LyricsLoadResult {
    data class Found(val lyrics: ResolvedLyricsResult) : LyricsLoadResult
    data object Removed : LyricsLoadResult
    data class Unavailable(val cause: Throwable? = null) : LyricsLoadResult
}

/** Also bridges the Android UI and overlay service's separate Room instances. */
data class LyricsChange(val trackId: String?, val contentChanged: Boolean)

object LyricsChanges {
    private val mutableChanges = MutableSharedFlow<LyricsChange>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val changes = mutableChanges.asSharedFlow()
    fun publish(trackId: String?, contentChanged: Boolean = true) {
        mutableChanges.tryEmit(LyricsChange(trackId, contentChanged))
    }
}

internal fun LyricsCacheEntity.versionOrNull(): String? =
    parseCachedLyrics(originalSourceId ?: sourceId, rawPayload)?.let(::lyricsVersion)

internal suspend fun LynMusicDatabase.currentCachedLyricsVersion(track: Track): String? {
    val rows = lyricsCacheDao().getByTrack(track.id)
    val preferredSource = when {
        parseEmbySongLocator(track.mediaLocator) != null -> EMBY_LYRICS_SOURCE_ID
        parseFnMusicSongLocator(track.mediaLocator) != null -> FN_MUSIC_LYRICS_SOURCE_ID
        else -> parseSubsonicCompatibleSongLocator(track.mediaLocator)?.sourceType?.let(::lyricsSourceIdFor)
            ?: SAME_NAME_LRC_SOURCE_ID
    }
    return rows.firstOrNull { it.sourceId == MANUAL_LYRICS_OVERRIDE_SOURCE_ID }?.versionOrNull()
        ?: rows.firstOrNull { it.selectedForPlayback }?.versionOrNull()
        ?: rows.firstOrNull { it.sourceId == preferredSource }?.versionOrNull()
        ?: rows.firstNotNullOfOrNull { it.versionOrNull() }
}

internal suspend fun LynMusicDatabase.readLyricsOffset(track: Track, version: String): Long? =
    immediateWriteTransaction {
        // A late reader may neither activate an obsolete version nor delete a newer calibration.
        if (currentCachedLyricsVersion(track) != version) return@immediateWriteTransaction null
        val saved = lyricsOffsetDao().get(track.id)
        if (saved != null && saved.lyricsVersion != version) lyricsOffsetDao().delete(track.id)
        saved?.takeIf { it.lyricsVersion == version }?.offsetMs ?: 0L
    }

internal suspend fun LynMusicDatabase.saveLyricsOffset(track: Track, version: String, offsetMs: Long): Boolean {
    val saved = immediateWriteTransaction {
        if (currentCachedLyricsVersion(track) != version) return@immediateWriteTransaction false
        lyricsOffsetDao().upsert(LyricsOffsetEntity(track.id, version, normalizeUserLyricsOffset(offsetMs)))
        true
    }
    if (saved) LyricsChanges.publish(track.id, contentChanged = false)
    return saved
}

/** Display metadata can require a reload without invalidating the timing calibration. */
internal suspend fun LynMusicDatabase.writeLyricsCache(item: LyricsCacheEntity) {
    val needsReload = immediateWriteTransaction {
        val previous = lyricsCacheDao().getByTrackIdAndSourceId(item.trackId, item.sourceId)
        val oldVersion = previous?.versionOrNull()
        val newVersion = item.versionOrNull()
        lyricsCacheDao().upsert(item.copy(selectedForPlayback = previous?.selectedForPlayback ?: item.selectedForPlayback))
        if (oldVersion != newVersion) {
            val offset = lyricsOffsetDao().get(item.trackId)
            if (offset != null && offset.lyricsVersion != newVersion &&
                (offset.lyricsVersion == oldVersion || item.sourceId == MANUAL_LYRICS_OVERRIDE_SOURCE_ID)) {
                lyricsOffsetDao().delete(item.trackId)
            }
        }
        oldVersion != newVersion || (
            previous?.rawPayload != item.rawPayload &&
                parseStructuredLyricsPayload(previous?.rawPayload.orEmpty())?.originalLanguage !=
                parseStructuredLyricsPayload(item.rawPayload)?.originalLanguage
            )
    }
    if (needsReload) LyricsChanges.publish(item.trackId)
}

/** Select the source itself so future tag/file updates remain visible, without pinning a copy. */
internal suspend fun LynMusicDatabase.selectTrackProvidedLyrics(item: LyricsCacheEntity, manualArtworkLocator: String?) {
    immediateWriteTransaction {
        val dao = lyricsCacheDao()
        dao.clearPlaybackSelection(item.trackId)
        dao.upsert(item.copy(selectedForPlayback = true))
        if (manualArtworkLocator == null) {
            dao.deleteByTrackIdAndSourceId(item.trackId, MANUAL_LYRICS_OVERRIDE_SOURCE_ID)
        } else {
            dao.upsert(LyricsCacheEntity(
                trackId = item.trackId,
                sourceId = MANUAL_LYRICS_OVERRIDE_SOURCE_ID,
                rawPayload = "",
                updatedAt = item.updatedAt,
                artworkLocator = manualArtworkLocator,
            ))
        }
        val offset = lyricsOffsetDao().get(item.trackId)
        if (offset != null && offset.lyricsVersion != item.versionOrNull()) lyricsOffsetDao().delete(item.trackId)
    }
    LyricsChanges.publish(item.trackId)
}

internal suspend fun LynMusicDatabase.removeLyricsCache(trackId: String, sourceId: String) {
    val changed = immediateWriteTransaction {
        val previous = lyricsCacheDao().getByTrackIdAndSourceId(trackId, sourceId) ?: return@immediateWriteTransaction false
        lyricsCacheDao().deleteByTrackIdAndSourceId(trackId, sourceId)
        if (lyricsOffsetDao().get(trackId)?.lyricsVersion == previous.versionOrNull()) {
            lyricsOffsetDao().delete(trackId)
        }
        previous.versionOrNull() != null
    }
    if (changed) LyricsChanges.publish(trackId)
}

internal suspend fun LynMusicDatabase.reconcileSourceLyricsOffsets(prefix: String) {
    immediateWriteTransaction {
        lyricsOffsetDao().getByPrefix(prefix).forEach { saved ->
            val track = trackDao().getByIds(listOf(saved.trackId)).firstOrNull()?.toDomain()
            if (track == null || currentCachedLyricsVersion(track) != saved.lyricsVersion) {
                lyricsOffsetDao().delete(saved.trackId)
            }
        }
    }
    LyricsChanges.publish(null)
}
