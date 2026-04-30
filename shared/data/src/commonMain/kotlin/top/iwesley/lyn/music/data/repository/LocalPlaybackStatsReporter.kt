package top.iwesley.lyn.music.data.repository

import top.iwesley.lyn.music.core.model.PlaybackStatsReporter
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.data.db.LynMusicDatabase

class LocalPlaybackStatsReporter(
    private val database: LynMusicDatabase,
) : PlaybackStatsReporter {
    override suspend fun reportNowPlaying(track: Track, atMillis: Long) = Unit

    override suspend fun submitPlay(track: Track, atMillis: Long) {
        val trackEntity = database.trackDao().getByIds(listOf(track.id)).firstOrNull() ?: return
        val submittedAt = atMillis.coerceAtLeast(0L)
        database.trackPlaybackStatsDao().incrementPlay(
            trackId = trackEntity.id,
            sourceId = trackEntity.sourceId,
            lastPlayedAt = submittedAt,
        )
        trackEntity.albumId
            ?.takeIf { it.isNotBlank() }
            ?.let { albumId ->
                database.albumPlaybackStatsDao().incrementPlay(
                    albumId = albumId,
                    lastPlayedAt = submittedAt,
                )
            }
    }
}
