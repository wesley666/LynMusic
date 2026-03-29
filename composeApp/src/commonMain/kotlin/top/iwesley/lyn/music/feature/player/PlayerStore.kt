package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WorkflowSongCandidate
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.LyricsRepository
import top.iwesley.lyn.music.data.repository.PlaybackRepository

data class PlayerState(
    val snapshot: PlaybackSnapshot = PlaybackSnapshot(),
    val isExpanded: Boolean = false,
    val isLyricsLoading: Boolean = false,
    val lyrics: LyricsDocument? = null,
    val highlightedLineIndex: Int = -1,
    val isManualLyricsSearchVisible: Boolean = false,
    val manualLyricsTitle: String = "",
    val manualLyricsArtistName: String = "",
    val manualLyricsAlbumTitle: String = "",
    val isManualLyricsSearchLoading: Boolean = false,
    val hasManualLyricsSearchResult: Boolean = false,
    val manualLyricsResults: List<LyricsSearchCandidate> = emptyList(),
    val manualWorkflowSongResults: List<WorkflowSongCandidate> = emptyList(),
    val manualLyricsError: String? = null,
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
    data object OpenManualLyricsSearch : PlayerIntent
    data object DismissManualLyricsSearch : PlayerIntent
    data class ManualLyricsTitleChanged(val value: String) : PlayerIntent
    data class ManualLyricsArtistChanged(val value: String) : PlayerIntent
    data class ManualLyricsAlbumChanged(val value: String) : PlayerIntent
    data object SearchManualLyrics : PlayerIntent
    data class ApplyManualLyricsCandidate(val candidate: LyricsSearchCandidate) : PlayerIntent
    data class ApplyWorkflowSongCandidate(val candidate: WorkflowSongCandidate) : PlayerIntent
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
                val previousTrackId = state.value.snapshot.currentTrack?.id
                val trackChanged = previousTrackId != snapshot.currentTrack?.id
                updateState { current ->
                    current.copy(
                        snapshot = snapshot,
                        highlightedLineIndex = findHighlightedLine(
                            lyrics = current.lyrics,
                            positionMs = snapshot.positionMs,
                        ),
                        isManualLyricsSearchVisible = if (trackChanged) false else current.isManualLyricsSearchVisible,
                        manualLyricsTitle = if (trackChanged) "" else current.manualLyricsTitle,
                        manualLyricsArtistName = if (trackChanged) "" else current.manualLyricsArtistName,
                        manualLyricsAlbumTitle = if (trackChanged) "" else current.manualLyricsAlbumTitle,
                        isManualLyricsSearchLoading = if (trackChanged) false else current.isManualLyricsSearchLoading,
                        hasManualLyricsSearchResult = if (trackChanged) false else current.hasManualLyricsSearchResult,
                        manualLyricsResults = if (trackChanged) emptyList() else current.manualLyricsResults,
                        manualWorkflowSongResults = if (trackChanged) emptyList() else current.manualWorkflowSongResults,
                        manualLyricsError = if (trackChanged) null else current.manualLyricsError,
                    )
                }
                val track = snapshot.currentTrack
                val lookupTrack = snapshot.toLyricsLookupTrack()
                val requestKey = lookupTrack?.lyricsRequestKey()
                if (track != null && shouldLoadLyrics(track, requestKey)) {
                    currentLyricsTrackId = track.id
                    currentLyricsRequestKey = requestKey
                    updateState { it.copy(isLyricsLoading = true, lyrics = null) }
                    val result = runCatching { lyricsRepository.getLyrics(lookupTrack ?: track) }.getOrNull()
                    val lyrics = result?.document
                    val artworkLocator = result?.artworkLocator
                    if (!artworkLocator.isNullOrBlank()) {
                        playbackRepository.overrideCurrentTrackArtwork(artworkLocator)
                    }
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
            PlayerIntent.OpenManualLyricsSearch -> openManualLyricsSearch()
            PlayerIntent.DismissManualLyricsSearch -> dismissManualLyricsSearch()
            is PlayerIntent.ManualLyricsTitleChanged -> updateManualLyricsForm(title = intent.value)
            is PlayerIntent.ManualLyricsArtistChanged -> updateManualLyricsForm(artistName = intent.value)
            is PlayerIntent.ManualLyricsAlbumChanged -> updateManualLyricsForm(albumTitle = intent.value)
            PlayerIntent.SearchManualLyrics -> searchManualLyrics()
            is PlayerIntent.ApplyManualLyricsCandidate -> applyManualLyricsCandidate(intent.candidate)
            is PlayerIntent.ApplyWorkflowSongCandidate -> applyWorkflowSongCandidate(intent.candidate)
        }
    }

    private fun openManualLyricsSearch() {
        val track = state.value.snapshot.currentTrack ?: return
        val lookupTrack = state.value.snapshot.toLyricsLookupTrack() ?: track
        updateState {
            it.copy(
                isManualLyricsSearchVisible = true,
                manualLyricsTitle = lookupTrack.title,
                manualLyricsArtistName = lookupTrack.artistName.orEmpty(),
                manualLyricsAlbumTitle = lookupTrack.albumTitle.orEmpty(),
                isManualLyricsSearchLoading = false,
                hasManualLyricsSearchResult = false,
                manualLyricsResults = emptyList(),
                manualWorkflowSongResults = emptyList(),
                manualLyricsError = null,
            )
        }
    }

    private fun dismissManualLyricsSearch() {
        updateState {
            it.copy(
                isManualLyricsSearchVisible = false,
                isManualLyricsSearchLoading = false,
                hasManualLyricsSearchResult = false,
                manualLyricsResults = emptyList(),
                manualWorkflowSongResults = emptyList(),
                manualLyricsError = null,
            )
        }
    }

    private fun updateManualLyricsForm(
        title: String? = null,
        artistName: String? = null,
        albumTitle: String? = null,
    ) {
        updateState {
            it.copy(
                manualLyricsTitle = title ?: it.manualLyricsTitle,
                manualLyricsArtistName = artistName ?: it.manualLyricsArtistName,
                manualLyricsAlbumTitle = albumTitle ?: it.manualLyricsAlbumTitle,
                hasManualLyricsSearchResult = false,
                manualLyricsResults = emptyList(),
                manualWorkflowSongResults = emptyList(),
                manualLyricsError = null,
            )
        }
    }

    private suspend fun searchManualLyrics() {
        val currentTrack = state.value.snapshot.currentTrack ?: return
        val title = state.value.manualLyricsTitle.trim()
        if (title.isBlank()) {
            updateState {
                it.copy(
                    isManualLyricsSearchLoading = false,
                    hasManualLyricsSearchResult = false,
                    manualLyricsResults = emptyList(),
                    manualWorkflowSongResults = emptyList(),
                    manualLyricsError = "标题不能为空",
                )
            }
            return
        }
        val searchTrack = currentTrack.copy(
            title = title,
            artistName = state.value.manualLyricsArtistName.trim().ifBlank { null },
            albumTitle = state.value.manualLyricsAlbumTitle.trim().ifBlank { null },
        )
        updateState {
            it.copy(
                isManualLyricsSearchLoading = true,
                hasManualLyricsSearchResult = false,
                manualLyricsResults = emptyList(),
                manualWorkflowSongResults = emptyList(),
                manualLyricsError = null,
            )
        }
        val directResult = runCatching { lyricsRepository.searchLyricsCandidates(searchTrack) }
        val workflowResult = runCatching { lyricsRepository.searchWorkflowSongCandidates(searchTrack) }
        updateState { current ->
            current.copy(
                isManualLyricsSearchLoading = false,
                hasManualLyricsSearchResult = true,
                manualLyricsResults = directResult.getOrDefault(emptyList()),
                manualWorkflowSongResults = workflowResult.getOrDefault(emptyList()),
                manualLyricsError = directResult.exceptionOrNull()?.message ?: workflowResult.exceptionOrNull()?.message,
            )
        }
    }

    private suspend fun applyManualLyricsCandidate(candidate: LyricsSearchCandidate) {
        val track = state.value.snapshot.currentTrack ?: return
        val snapshot = state.value.snapshot
        val document = lyricsRepository.applyLyricsCandidate(track.id, candidate)
        updateState {
            it.copy(
                lyrics = document,
                highlightedLineIndex = findHighlightedLine(document, snapshot.positionMs),
                isManualLyricsSearchVisible = false,
                isManualLyricsSearchLoading = false,
                hasManualLyricsSearchResult = false,
                manualLyricsResults = emptyList(),
                manualWorkflowSongResults = emptyList(),
                manualLyricsError = null,
            )
        }
    }

    private suspend fun applyWorkflowSongCandidate(candidate: WorkflowSongCandidate) {
        val track = state.value.snapshot.currentTrack ?: return
        val snapshot = state.value.snapshot
        val result = lyricsRepository.applyWorkflowSongCandidate(track.id, candidate)
        val document = result.document
        val artworkLocator = result.artworkLocator ?: normalizeArtworkLocator(candidate.imageUrl)
        if (!artworkLocator.isNullOrBlank()) {
            playbackRepository.overrideCurrentTrackArtwork(artworkLocator)
        }
        updateState {
            it.copy(
                lyrics = document,
                highlightedLineIndex = findHighlightedLine(document, snapshot.positionMs),
                isManualLyricsSearchVisible = false,
                isManualLyricsSearchLoading = false,
                hasManualLyricsSearchResult = false,
                manualLyricsResults = emptyList(),
                manualWorkflowSongResults = emptyList(),
                manualLyricsError = null,
            )
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
