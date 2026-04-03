package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.LyricsSharePlatformService
import top.iwesley.lyn.music.core.model.WorkflowSongCandidate
import top.iwesley.lyn.music.core.model.buildLyricsShareSuggestedName
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator
import top.iwesley.lyn.music.core.model.UnsupportedLyricsSharePlatformService
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.LyricsRepository
import top.iwesley.lyn.music.data.repository.PlaybackRepository

data class PlayerState(
    val snapshot: PlaybackSnapshot = PlaybackSnapshot(),
    val isExpanded: Boolean = false,
    val isQueueVisible: Boolean = false,
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
    val isLyricsShareVisible: Boolean = false,
    val selectedLyricsLineIndices: Set<Int> = emptySet(),
    val shareCardModel: LyricsShareCardModel? = null,
    val sharePreviewBytes: ByteArray? = null,
    val sharePreviewSelection: Set<Int> = emptySet(),
    val sharePreviewError: String? = null,
    val isShareRendering: Boolean = false,
    val isShareSaving: Boolean = false,
    val isShareCopying: Boolean = false,
    val shareMessage: String? = null,
) {
    val hasFreshSharePreview: Boolean
        get() = sharePreviewBytes != null && sharePreviewSelection == selectedLyricsLineIndices
}

sealed interface PlayerIntent {
    data class PlayTracks(val tracks: List<Track>, val startIndex: Int) : PlayerIntent
    data class PlayQueueIndex(val index: Int) : PlayerIntent
    data object TogglePlayPause : PlayerIntent
    data object SkipNext : PlayerIntent
    data object SkipPrevious : PlayerIntent
    data class SeekTo(val positionMs: Long) : PlayerIntent
    data class SetVolume(val value: Float) : PlayerIntent
    data object CycleMode : PlayerIntent
    data class ExpandedChanged(val value: Boolean) : PlayerIntent
    data class QueueVisibilityChanged(val value: Boolean) : PlayerIntent
    data object OpenManualLyricsSearch : PlayerIntent
    data object DismissManualLyricsSearch : PlayerIntent
    data class ManualLyricsTitleChanged(val value: String) : PlayerIntent
    data class ManualLyricsArtistChanged(val value: String) : PlayerIntent
    data class ManualLyricsAlbumChanged(val value: String) : PlayerIntent
    data object SearchManualLyrics : PlayerIntent
    data class ApplyManualLyricsCandidate(val candidate: LyricsSearchCandidate) : PlayerIntent
    data class ApplyWorkflowSongCandidate(val candidate: WorkflowSongCandidate) : PlayerIntent
    data object OpenLyricsShare : PlayerIntent
    data object DismissLyricsShare : PlayerIntent
    data class ToggleLyricsLineSelection(val index: Int) : PlayerIntent
    data object ClearLyricsSelection : PlayerIntent
    data object BuildLyricsSharePreview : PlayerIntent
    data object SaveLyricsShareImage : PlayerIntent
    data object CopyLyricsShareImage : PlayerIntent
    data object ClearLyricsShareMessage : PlayerIntent
}

sealed interface PlayerEffect

class PlayerStore(
    private val playbackRepository: PlaybackRepository,
    private val lyricsRepository: LyricsRepository,
    private val storeScope: CoroutineScope,
    private val lyricsSharePlatformService: LyricsSharePlatformService = UnsupportedLyricsSharePlatformService,
) : BaseStore<PlayerState, PlayerIntent, PlayerEffect>(
    initialState = PlayerState(),
    scope = storeScope,
) {
    private var currentLyricsTrackId: String? = null
    private var currentLyricsRequestKey: String? = null
    private var currentSharePreviewRequestId: Long = 0L

    init {
        storeScope.launch {
            playbackRepository.snapshot.collect { snapshot ->
                val previousTrackId = state.value.snapshot.currentTrack?.id
                val trackChanged = previousTrackId != snapshot.currentTrack?.id
                if (trackChanged || snapshot.currentTrack == null) {
                    invalidateLyricsSharePreviewRequests()
                }
                updateState { current ->
                    current.copy(
                        snapshot = snapshot,
                        isQueueVisible = if (snapshot.currentTrack == null) false else current.isQueueVisible,
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
                    ).let { updated ->
                        if (trackChanged || snapshot.currentTrack == null) {
                            updated.clearLyricsShareState()
                        } else {
                            updated
                        }
                    }
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
            is PlayerIntent.PlayQueueIndex -> playQueueIndex(intent.index)
            PlayerIntent.TogglePlayPause -> playbackRepository.togglePlayPause()
            PlayerIntent.SkipNext -> playbackRepository.skipNext()
            PlayerIntent.SkipPrevious -> playbackRepository.skipPrevious()
            is PlayerIntent.SeekTo -> playbackRepository.seekTo(intent.positionMs)
            is PlayerIntent.SetVolume -> playbackRepository.setVolume(intent.value)
            PlayerIntent.CycleMode -> playbackRepository.cycleMode()
            is PlayerIntent.ExpandedChanged -> updateState { it.copy(isExpanded = intent.value) }
            is PlayerIntent.QueueVisibilityChanged -> updateState {
                it.copy(isQueueVisible = intent.value && it.snapshot.currentTrack != null)
            }
            PlayerIntent.OpenManualLyricsSearch -> openManualLyricsSearch()
            PlayerIntent.DismissManualLyricsSearch -> dismissManualLyricsSearch()
            is PlayerIntent.ManualLyricsTitleChanged -> updateManualLyricsForm(title = intent.value)
            is PlayerIntent.ManualLyricsArtistChanged -> updateManualLyricsForm(artistName = intent.value)
            is PlayerIntent.ManualLyricsAlbumChanged -> updateManualLyricsForm(albumTitle = intent.value)
            PlayerIntent.SearchManualLyrics -> searchManualLyrics()
            is PlayerIntent.ApplyManualLyricsCandidate -> applyManualLyricsCandidate(intent.candidate)
            is PlayerIntent.ApplyWorkflowSongCandidate -> applyWorkflowSongCandidate(intent.candidate)
            PlayerIntent.OpenLyricsShare -> openLyricsShare()
            PlayerIntent.DismissLyricsShare -> dismissLyricsShare()
            is PlayerIntent.ToggleLyricsLineSelection -> toggleLyricsLineSelection(intent.index)
            PlayerIntent.ClearLyricsSelection -> clearLyricsSelection()
            PlayerIntent.BuildLyricsSharePreview -> rebuildLyricsSharePreview()
            PlayerIntent.SaveLyricsShareImage -> saveLyricsShareImage()
            PlayerIntent.CopyLyricsShareImage -> copyLyricsShareImage()
            PlayerIntent.ClearLyricsShareMessage -> updateState {
                it.copy(shareMessage = null, sharePreviewError = null)
            }
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

    private suspend fun playQueueIndex(index: Int) {
        val snapshot = state.value.snapshot
        if (index !in snapshot.queue.indices) return
        if (index == snapshot.currentIndex) {
            updateState { it.copy(isQueueVisible = false) }
            return
        }
        playbackRepository.playTracks(snapshot.queue, index)
        updateState { it.copy(isQueueVisible = false) }
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

    private suspend fun openLyricsShare() {
        val snapshot = state.value.snapshot
        val lyrics = state.value.lyrics ?: return
        if (snapshot.currentTrack == null) return
        val defaultSelection = state.value.highlightedLineIndex
            .takeIf { index ->
                index in lyrics.lines.indices && lyrics.lines[index].text.trim().isNotEmpty()
            }?.let { setOf(it) }.orEmpty()
        invalidateLyricsSharePreviewRequests()
        updateState {
            it.copy(
                isManualLyricsSearchVisible = false,
                isManualLyricsSearchLoading = false,
                hasManualLyricsSearchResult = false,
                manualLyricsResults = emptyList(),
                manualWorkflowSongResults = emptyList(),
                manualLyricsError = null,
                isLyricsShareVisible = true,
                selectedLyricsLineIndices = defaultSelection,
                shareCardModel = deriveLyricsShareCardModel(snapshot, lyrics, defaultSelection),
                sharePreviewBytes = null,
                sharePreviewSelection = emptySet(),
                sharePreviewError = null,
                isShareRendering = false,
                isShareSaving = false,
                isShareCopying = false,
                shareMessage = null,
            )
        }
        if (defaultSelection.isNotEmpty()) {
            rebuildLyricsSharePreview()
        }
    }

    private fun dismissLyricsShare() {
        invalidateLyricsSharePreviewRequests()
        updateState { it.clearLyricsShareState() }
    }

    private suspend fun toggleLyricsLineSelection(index: Int) {
        val lyrics = state.value.lyrics ?: return
        val line = lyrics.lines.getOrNull(index)?.text?.trim().orEmpty()
        if (line.isEmpty()) return
        val updatedSelection = state.value.selectedLyricsLineIndices.toMutableSet().also { selected ->
            if (!selected.add(index)) {
                selected.remove(index)
            }
        }.toSet()
        invalidateLyricsSharePreviewRequests()
        updateState {
            val snapshot = it.snapshot
            it.copy(
                selectedLyricsLineIndices = updatedSelection,
                shareCardModel = deriveLyricsShareCardModel(snapshot, lyrics, updatedSelection),
                sharePreviewError = null,
                isShareRendering = false,
                isShareSaving = false,
                isShareCopying = false,
                shareMessage = null,
            )
        }
        if (updatedSelection.isNotEmpty()) {
            rebuildLyricsSharePreview()
        }
    }

    private fun clearLyricsSelection() {
        invalidateLyricsSharePreviewRequests()
        updateState {
            it.copy(
                selectedLyricsLineIndices = emptySet(),
                shareCardModel = null,
                sharePreviewBytes = null,
                sharePreviewSelection = emptySet(),
                sharePreviewError = null,
                isShareRendering = false,
                isShareSaving = false,
                isShareCopying = false,
                shareMessage = null,
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

    private suspend fun rebuildLyricsSharePreview(): ByteArray? {
        val snapshot = state.value.snapshot
        val lyrics = state.value.lyrics
        val model = deriveLyricsShareCardModel(snapshot, lyrics, state.value.selectedLyricsLineIndices)
        if (!state.value.isLyricsShareVisible || model == null) {
            updateState {
                it.copy(
                    shareCardModel = model,
                    sharePreviewBytes = null,
                    sharePreviewSelection = emptySet(),
                    sharePreviewError = null,
                    isShareRendering = false,
                )
            }
            return null
        }
        val requestId = nextLyricsSharePreviewRequestId()
        updateState {
            it.copy(
                shareCardModel = model,
                sharePreviewError = null,
                isShareRendering = true,
            )
        }
        val result = lyricsSharePlatformService.buildPreview(model)
        if (requestId != currentSharePreviewRequestId) return null
        return result.fold(
            onSuccess = { bytes ->
                updateState {
                    it.copy(
                        shareCardModel = model,
                        sharePreviewBytes = bytes,
                        sharePreviewSelection = it.selectedLyricsLineIndices,
                        sharePreviewError = null,
                        isShareRendering = false,
                    )
                }
                bytes
            },
            onFailure = { throwable ->
                updateState {
                    it.copy(
                        shareCardModel = model,
                        sharePreviewError = "生成分享图片失败: ${throwable.message.orEmpty()}",
                        isShareRendering = false,
                    )
                }
                null
            },
        )
    }

    private suspend fun saveLyricsShareImage() {
        val track = state.value.snapshot.currentTrack ?: return
        val bytes = obtainLyricsSharePreviewBytes() ?: return
        updateState { it.copy(isShareSaving = true, shareMessage = null) }
        val result = lyricsSharePlatformService.saveImage(
            pngBytes = bytes,
            suggestedName = buildLyricsShareSuggestedName(track.title),
        )
        updateState {
            it.copy(
                isShareSaving = false,
                shareMessage = result.fold(
                    onSuccess = { saved -> saved.message },
                    onFailure = { throwable -> "保存图片失败: ${throwable.message.orEmpty()}" },
                ),
            )
        }
    }

    private suspend fun copyLyricsShareImage() {
        val bytes = obtainLyricsSharePreviewBytes() ?: return
        updateState { it.copy(isShareCopying = true, shareMessage = null) }
        val result = lyricsSharePlatformService.copyImage(bytes)
        updateState {
            it.copy(
                isShareCopying = false,
                shareMessage = result.fold(
                    onSuccess = { "图片已复制" },
                    onFailure = { throwable -> "复制图片失败: ${throwable.message.orEmpty()}" },
                ),
            )
        }
    }

    private suspend fun obtainLyricsSharePreviewBytes(): ByteArray? {
        if (state.value.selectedLyricsLineIndices.isEmpty()) {
            updateState { it.copy(shareMessage = "请先选择至少一句歌词") }
            return null
        }
        if (state.value.hasFreshSharePreview) {
            state.value.sharePreviewBytes?.let { return it }
        }
        return rebuildLyricsSharePreview()
    }

    private fun deriveLyricsShareCardModel(
        snapshot: PlaybackSnapshot,
        lyrics: LyricsDocument?,
        selectedLineIndices: Set<Int>,
    ): LyricsShareCardModel? {
        val selectedLines = lyrics?.lines.orEmpty()
            .mapIndexedNotNull { index, line ->
                if (index !in selectedLineIndices) return@mapIndexedNotNull null
                line.text.trim().takeIf { it.isNotEmpty() }
            }
        if (selectedLines.isEmpty()) return null
        return LyricsShareCardModel(
            title = snapshot.currentDisplayTitle.ifBlank { snapshot.currentTrack?.title.orEmpty() },
            artistName = snapshot.currentDisplayArtistName ?: snapshot.currentTrack?.artistName,
            artworkLocator = snapshot.currentDisplayArtworkLocator,
            lyricsLines = selectedLines,
        )
    }

    private fun invalidateLyricsSharePreviewRequests() {
        currentSharePreviewRequestId += 1
    }

    private fun nextLyricsSharePreviewRequestId(): Long {
        currentSharePreviewRequestId += 1
        return currentSharePreviewRequestId
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

private fun PlayerState.clearLyricsShareState(): PlayerState {
    return copy(
        isLyricsShareVisible = false,
        selectedLyricsLineIndices = emptySet(),
        shareCardModel = null,
        sharePreviewBytes = null,
        sharePreviewSelection = emptySet(),
        sharePreviewError = null,
        isShareRendering = false,
        isShareSaving = false,
        isShareCopying = false,
        shareMessage = null,
    )
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
