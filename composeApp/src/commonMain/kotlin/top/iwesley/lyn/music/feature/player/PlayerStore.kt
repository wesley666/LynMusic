package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.LyricsRepository
import top.iwesley.lyn.music.data.repository.PlaybackRepository

data class PlayerState(
    val snapshot: PlaybackSnapshot = PlaybackSnapshot(),
    val isExpanded: Boolean = false,
    val isLyricsLoading: Boolean = false,
    val lyrics: LyricsDocument? = null,
    val highlightedLineIndex: Int = -1,
)

sealed interface PlayerIntent {
    data class PlayTracks(val tracks: List<Track>, val startIndex: Int) : PlayerIntent
    data object TogglePlayPause : PlayerIntent
    data object SkipNext : PlayerIntent
    data object SkipPrevious : PlayerIntent
    data class SeekTo(val positionMs: Long) : PlayerIntent
    data class SetVolume(val value: Float) : PlayerIntent
    data object CycleMode : PlayerIntent
    data class ExpandedChanged(val value: Boolean) : PlayerIntent
}

sealed interface PlayerEffect

class PlayerStore(
    private val playbackRepository: PlaybackRepository,
    private val lyricsRepository: LyricsRepository,
    scope: CoroutineScope,
) : BaseStore<PlayerState, PlayerIntent, PlayerEffect>(
    initialState = PlayerState(),
    scope = scope,
) {
    private var currentLyricsTrackId: String? = null
    private var currentLyricsRequestKey: String? = null

    init {
        scope.launch {
            playbackRepository.snapshot.collect { snapshot ->
                updateState { current ->
                    current.copy(
                        snapshot = snapshot,
                        highlightedLineIndex = findHighlightedLine(
                            lyrics = current.lyrics,
                            positionMs = snapshot.positionMs,
                        ),
                    )
                }
                val track = snapshot.currentTrack
                val lookupTrack = snapshot.toLyricsLookupTrack()
                val requestKey = lookupTrack?.lyricsRequestKey()
                if (track != null && shouldLoadLyrics(track, requestKey)) {
                    currentLyricsTrackId = track.id
                    currentLyricsRequestKey = requestKey
                    updateState { it.copy(isLyricsLoading = true, lyrics = null) }
                    val lyrics = runCatching { lyricsRepository.getLyrics(lookupTrack ?: track) }.getOrNull()
                    updateState {
                        it.copy(
                            isLyricsLoading = false,
                            lyrics = lyrics,
                            highlightedLineIndex = findHighlightedLine(lyrics, snapshot.positionMs),
                        )
                    }
                } else if (track == null) {
                    currentLyricsTrackId = null
                    currentLyricsRequestKey = null
                    updateState { it.copy(isLyricsLoading = false, lyrics = null, highlightedLineIndex = -1) }
                }
            }
        }
    }

    override suspend fun handleIntent(intent: PlayerIntent) {
        when (intent) {
            is PlayerIntent.PlayTracks -> playbackRepository.playTracks(intent.tracks, intent.startIndex)
            PlayerIntent.TogglePlayPause -> playbackRepository.togglePlayPause()
            PlayerIntent.SkipNext -> playbackRepository.skipNext()
            PlayerIntent.SkipPrevious -> playbackRepository.skipPrevious()
            is PlayerIntent.SeekTo -> playbackRepository.seekTo(intent.positionMs)
            is PlayerIntent.SetVolume -> playbackRepository.setVolume(intent.value)
            PlayerIntent.CycleMode -> playbackRepository.cycleMode()
            is PlayerIntent.ExpandedChanged -> updateState { it.copy(isExpanded = intent.value) }
        }
    }

    private fun findHighlightedLine(lyrics: LyricsDocument?, positionMs: Long): Int {
        val syncedLines = lyrics?.lines ?: return -1
        val target = positionMs + lyrics.offsetMs
        return syncedLines.indexOfLast { line ->
            line.timestampMs?.let { it <= target } ?: false
        }
    }

    private fun shouldLoadLyrics(track: Track, requestKey: String?): Boolean {
        return when {
            track.id != currentLyricsTrackId -> true
            state.value.lyrics != null -> false
            requestKey != null && requestKey != currentLyricsRequestKey -> true
            else -> false
        }
    }
}

private fun PlaybackSnapshot.toLyricsLookupTrack(): Track? {
    val track = currentTrack ?: return null
    return track.copy(
        title = currentDisplayTitle,
        artistName = currentDisplayArtistName,
        albumTitle = currentDisplayAlbumTitle,
    )
}

private fun Track.lyricsRequestKey(): String {
    return listOf(id, title, artistName.orEmpty(), albumTitle.orEmpty()).joinToString("|")
}
