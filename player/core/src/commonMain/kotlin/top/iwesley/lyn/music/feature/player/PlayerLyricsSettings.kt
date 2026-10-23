package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.repository.LyricsRepository
import top.iwesley.lyn.music.data.repository.ResolvedLyricsResult

/** A single writer coalesces pending operations without allowing an old write to win over reset. */
internal class PlayerLyricsSettings(
    private val scope: CoroutineScope,
    private val repository: LyricsRepository,
    private val preferences: PlayerLyricsStylePreferencesStore,
    private val fonts: LyricsShareFontLibraryPlatformService,
    private val state: () -> PlayerState,
    private val update: ((PlayerState) -> PlayerState) -> Unit,
) {
    private data class Write(
        val key: String,
        val sequence: Long,
        val style: PlayerLyricsStyle? = null,
        val track: Track? = null,
        val version: String? = null,
        val offsetMs: Long = 0L,
    )

    private val writes = Channel<Write>(Channel.UNLIMITED)
    private val pending = mutableMapOf<String, Write>()
    private var sequence = 0L
    private var offsetWriteRevision = 0L
    private var fontGeneration = 0L

    init {
        scope.launch {
            for (first in writes) {
                val batch = linkedMapOf(first.key to first)
                while (true) {
                    val next = writes.tryReceive().getOrNull() ?: break
                    batch[next.key] = next
                }
                for (write in batch.values) {
                    if (pending[write.key]?.sequence != write.sequence) continue
                    try {
                        if (write.style != null) preferences.setPlayerLyricsStyle(write.style)
                        else {
                            repository.saveOffset(requireNotNull(write.track), requireNotNull(write.version), write.offsetMs)
                            offsetWriteRevision += 1
                        }
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        update { it.copy(message = error.uiErrorText()) }
                    } finally {
                        if (pending[write.key]?.sequence == write.sequence) pending.remove(write.key)
                    }
                }
            }
        }
        if (state().lyricsStyle.fontKey != null) refreshFonts()
    }

    fun changeStyle(value: PlayerLyricsStyle) {
        val style = value.normalized()
        val fontChanged = state().lyricsStyle.fontKey != style.fontKey
        update { it.copy(lyricsStyle = style, lyricsFontPath = if (fontChanged) null else it.lyricsFontPath) }
        enqueue(Write("style", ++sequence, style = style))
        if (fontChanged) refreshFonts()
    }

    fun changeOffset(trackId: String, version: String, value: Long) {
        val current = state()
        val track = current.effectiveSnapshot.currentTrack ?: return
        val original = current.originalLyrics ?: return
        if (track.id != trackId || current.lyricsTrackId != trackId || current.lyricsVersion != version || !original.isSynced) return
        val offset = normalizeUserLyricsOffset(value)
        update { it.withLyricsOffset(offset) }
        enqueue(Write(offsetKey(trackId, version), ++sequence, track = track, version = version, offsetMs = offset))
    }

    private fun enqueue(write: Write) {
        pending[write.key] = write
        writes.trySend(write)
    }

    suspend fun accept(track: Track, result: ResolvedLyricsResult, isCurrent: (PlayerState) -> Boolean): Boolean {
        val offset = readOffsetAfterWrites(track, result.version) ?: return false
        if (!isCurrent(state())) return false
        update { current ->
            if (!isCurrent(current) || current.effectiveSnapshot.currentTrack?.id != track.id) return@update current
            current.copy(
                originalLyrics = result.document,
                lyricsVersion = result.version,
                lyricsTrackId = track.id,
                isLyricsLoading = false,
                hasLyricsLookupCompleted = true,
            ).withLyricsOffset(pending[offsetKey(track.id, result.version)]?.offsetMs ?: offset)
        }
        return true
    }

    private suspend fun readOffsetAfterWrites(track: Track, version: String): Long? {
        // Pending entries disappear after saving; retry reads that straddle that completion.
        // Do not retain old values across reloads, because a lyric update may invalidate them.
        while (true) {
            val revision = offsetWriteRevision
            val offset = repository.readOffset(track, version) ?: return null
            if (revision == offsetWriteRevision) return offset
        }
    }

    suspend fun refreshOffset() {
        val current = state()
        val track = current.effectiveSnapshot.currentTrack ?: return
        val version = current.lyricsVersion ?: return
        val key = offsetKey(track.id, version)
        val operation = sequence
        if (key in pending || current.lyricsTrackId != track.id) return
        val offset = repository.readOffset(track, version) ?: return
        if (key in pending || operation != sequence) return
        update {
            if (it.effectiveSnapshot.currentTrack?.id == track.id && it.lyricsVersion == version) it.withLyricsOffset(offset)
            else it
        }
    }

    fun refreshFonts() {
        val generation = ++fontGeneration
        val requestedKey = state().lyricsStyle.fontKey
        update { it.copy(isLyricsFontsLoading = true) }
        scope.launch {
            val result = fonts.listImportedFonts()
            val path = requestedKey?.let { fonts.resolveImportedFontPath(it).getOrNull() }
            if (generation != fontGeneration) return@launch
            val imported = result.getOrDefault(emptyList()).filter {
                it.kind == LyricsShareFontKind.IMPORTED && parseLyricsShareImportedFontHash(it.fontKey) != null
            }
            update {
                it.copy(
                    availableLyricsFonts = imported,
                    isLyricsFontsLoading = false,
                    lyricsFontPath = path.takeIf { _ -> it.lyricsStyle.fontKey == requestedKey },
                )
            }
            if (requestedKey != null && path == null && state().lyricsStyle.fontKey == requestedKey) {
                changeStyle(state().lyricsStyle.copy(fontKey = null))
            }
        }
    }

    fun fontFailed(fontKey: String) {
        if (state().lyricsStyle.fontKey == fontKey) changeStyle(state().lyricsStyle.copy(fontKey = null))
    }

    private fun offsetKey(trackId: String, version: String) = "offset:$trackId:$version"
}

private fun PlayerState.withLyricsOffset(offset: Long): PlayerState {
    val adjusted = originalLyrics?.let { it.copy(offsetMs = it.offsetMs + offset) }
    return copy(
        lyrics = adjusted,
        lyricsUserOffsetMs = offset,
        highlightedLineIndex = findDesktopLyricsHighlightedLine(adjusted, effectiveSnapshot.positionMs),
    )
}
