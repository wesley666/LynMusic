package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.DEFAULT_LYRICS_SHARE_FONT_FAMILY
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.LyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.LyricsShareTemplate
import top.iwesley.lyn.music.core.model.LyricsSearchApplyMode
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
import top.iwesley.lyn.music.core.model.LyricsSharePlatformService
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.UnsupportedLyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.WorkflowSongCandidate
import top.iwesley.lyn.music.core.model.UnsupportedLyricsSharePlatformService
import top.iwesley.lyn.music.core.model.buildLyricsShareSuggestedName
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator
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
    val selectedLyricsShareTemplate: LyricsShareTemplate = LyricsShareTemplate.NOTE,
    val supportsLyricsShareFontSelection: Boolean = false,
    val availableLyricsShareFonts: List<LyricsShareFontOption> = emptyList(),
    val selectedLyricsShareFontFamily: String? = null,
    val isLyricsShareFontsLoading: Boolean = false,
    val lyricsShareFontsError: String? = null,
    val selectedLyricsLineIndices: Set<Int> = emptySet(),
    val shareCardModel: LyricsShareCardModel? = null,
    val sharePreviewBytes: ByteArray? = null,
    val sharePreviewSelection: Set<Int> = emptySet(),
    val sharePreviewTemplate: LyricsShareTemplate? = null,
    val sharePreviewFontFamilyName: String? = null,
    val sharePreviewError: String? = null,
    val isShareRendering: Boolean = false,
    val isShareSaving: Boolean = false,
    val isShareCopying: Boolean = false,
    val shareMessage: String? = null,
    val message: String? = null,
) {
    val hasFreshSharePreview: Boolean
        get() = sharePreviewBytes != null &&
            sharePreviewSelection == selectedLyricsLineIndices &&
            sharePreviewTemplate == selectedLyricsShareTemplate &&
            sharePreviewFontFamilyName == selectedLyricsShareFontFamily
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
    data class ApplyManualLyricsCandidate(
        val candidate: LyricsSearchCandidate,
        val mode: LyricsSearchApplyMode = LyricsSearchApplyMode.FULL,
    ) : PlayerIntent
    data class ApplyWorkflowSongCandidate(
        val candidate: WorkflowSongCandidate,
        val mode: LyricsSearchApplyMode = LyricsSearchApplyMode.FULL,
    ) : PlayerIntent
    data object OpenLyricsShare : PlayerIntent
    data object DismissLyricsShare : PlayerIntent
    data class LyricsShareTemplateChanged(val template: LyricsShareTemplate) : PlayerIntent
    data object RequestLyricsShareFonts : PlayerIntent
    data class LyricsShareFontChanged(val familyName: String) : PlayerIntent
    data class ToggleLyricsLineSelection(val index: Int) : PlayerIntent
    data object ClearLyricsSelection : PlayerIntent
    data object BuildLyricsSharePreview : PlayerIntent
    data object SaveLyricsShareImage : PlayerIntent
    data object CopyLyricsShareImage : PlayerIntent
    data object ClearLyricsShareMessage : PlayerIntent
    data object ClearMessage : PlayerIntent
}

sealed interface PlayerEffect

class PlayerStore(
    private val playbackRepository: PlaybackRepository,
    private val lyricsRepository: LyricsRepository,
    private val storeScope: CoroutineScope,
    private val lyricsSharePlatformService: LyricsSharePlatformService = UnsupportedLyricsSharePlatformService,
    private val lyricsShareFontPreferencesStore: LyricsShareFontPreferencesStore = UnsupportedLyricsShareFontPreferencesStore,
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
) : BaseStore<PlayerState, PlayerIntent, PlayerEffect>(
    initialState = PlayerState(
        supportsLyricsShareFontSelection =
            lyricsShareFontPreferencesStore !== UnsupportedLyricsShareFontPreferencesStore,
    ),
    scope = storeScope,
) {
    private var currentLyricsTrackId: String? = null
    private var currentLyricsRequestKey: String? = null
    private var currentLyricsLoadRequestId: Long = 0L
    private var lyricsLoadJob: Job? = null
    private var currentSharePreviewRequestId: Long = 0L
    private var lastPlaybackErrorKey: PlaybackErrorKey? = null

    init {
        storeScope.launch {
            playbackRepository.snapshot.collect { snapshot ->
                val previousTrackId = state.value.snapshot.currentTrack?.id
                val trackChanged = previousTrackId != snapshot.currentTrack?.id
                val playbackErrorKey = snapshot.errorMessage?.let { message ->
                    PlaybackErrorKey(trackId = snapshot.currentTrack?.id, message = message)
                }
                val shouldShowPlaybackError = playbackErrorKey != null && playbackErrorKey != lastPlaybackErrorKey
                lastPlaybackErrorKey = playbackErrorKey
                if (trackChanged || snapshot.currentTrack == null) {
                    invalidateLyricsSharePreviewRequests()
                }
                updateState { current ->
                    current.copy(
                        snapshot = snapshot,
                        isLyricsLoading = if (trackChanged || snapshot.currentTrack == null) false else current.isLyricsLoading,
                        lyrics = if (trackChanged || snapshot.currentTrack == null) null else current.lyrics,
                        isQueueVisible = if (snapshot.currentTrack == null) false else current.isQueueVisible,
                        highlightedLineIndex = findHighlightedLine(
                            lyrics = if (trackChanged || snapshot.currentTrack == null) null else current.lyrics,
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
                        message = when {
                            shouldShowPlaybackError -> snapshot.errorMessage
                            trackChanged -> null
                            else -> current.message
                        },
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
                    launchAutomaticLyricsLoad(
                        track = track,
                        lookupTrack = lookupTrack ?: track,
                        requestKey = requestKey,
                    )
                } else if (track == null) {
                    cancelAutomaticLyricsLoad(resetTracking = true)
                    updateState { it.copy(isLyricsLoading = false, lyrics = null, highlightedLineIndex = -1) }
                }
            }
        }
    }

    fun startHydration() {
        storeScope.launch {
            playbackRepository.hydratePersistedQueueIfNeeded()
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
            is PlayerIntent.ApplyManualLyricsCandidate -> applyManualLyricsCandidate(intent.candidate, intent.mode)
            is PlayerIntent.ApplyWorkflowSongCandidate -> applyWorkflowSongCandidate(intent.candidate, intent.mode)
            PlayerIntent.OpenLyricsShare -> openLyricsShare()
            PlayerIntent.DismissLyricsShare -> dismissLyricsShare()
            is PlayerIntent.LyricsShareTemplateChanged -> updateLyricsShareTemplate(intent.template)
            PlayerIntent.RequestLyricsShareFonts -> loadLyricsShareFonts()
            is PlayerIntent.LyricsShareFontChanged -> updateLyricsShareFont(intent.familyName)
            is PlayerIntent.ToggleLyricsLineSelection -> toggleLyricsLineSelection(intent.index)
            PlayerIntent.ClearLyricsSelection -> clearLyricsSelection()
            PlayerIntent.BuildLyricsSharePreview -> rebuildLyricsSharePreview()
            PlayerIntent.SaveLyricsShareImage -> saveLyricsShareImage()
            PlayerIntent.CopyLyricsShareImage -> copyLyricsShareImage()
            PlayerIntent.ClearLyricsShareMessage -> updateState {
                it.copy(shareMessage = null, sharePreviewError = null)
            }
            PlayerIntent.ClearMessage -> updateState { it.copy(message = null) }
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
                message = null,
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
                message = null,
            )
        }
    }

    private suspend fun openLyricsShare() {
        val currentState = state.value
        val snapshot = currentState.snapshot
        val lyrics = currentState.lyrics ?: return
        if (snapshot.currentTrack == null) return
        val supportsFontSelection = supportsLyricsShareFontSelection()
        val savedFontFamily = if (supportsFontSelection) {
            lyricsShareFontPreferencesStore.selectedLyricsShareFontFamily.value
        } else {
            null
        }
        var selectedFontFamily = currentState.selectedLyricsShareFontFamily
            ?: savedFontFamily
            ?: DEFAULT_LYRICS_SHARE_FONT_FAMILY.takeIf { supportsFontSelection }
        if (supportsFontSelection && currentState.availableLyricsShareFonts.isNotEmpty()) {
            val resolvedFontFamily = resolveLyricsShareFontFamilySelection(
                availableFonts = currentState.availableLyricsShareFonts,
                preferredFontFamily = selectedFontFamily,
            )
            if (resolvedFontFamily != null && resolvedFontFamily != savedFontFamily) {
                lyricsShareFontPreferencesStore.setSelectedLyricsShareFontFamily(resolvedFontFamily)
            }
            selectedFontFamily = resolvedFontFamily
        }
        val defaultSelection = currentState.highlightedLineIndex
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
                selectedLyricsShareFontFamily = selectedFontFamily,
                selectedLyricsLineIndices = defaultSelection,
                shareCardModel = deriveLyricsShareCardModel(
                    snapshot = snapshot,
                    lyrics = lyrics,
                    selectedLineIndices = defaultSelection,
                    template = it.selectedLyricsShareTemplate,
                    fontFamilyName = selectedFontFamily,
                ),
                sharePreviewBytes = null,
                sharePreviewSelection = emptySet(),
                sharePreviewTemplate = null,
                sharePreviewFontFamilyName = null,
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

    private suspend fun updateLyricsShareTemplate(template: LyricsShareTemplate) {
        if (template == state.value.selectedLyricsShareTemplate) return
        invalidateLyricsSharePreviewRequests()
        updateState { current ->
            val snapshot = current.snapshot
            val lyrics = current.lyrics
            current.copy(
                selectedLyricsShareTemplate = template,
                shareCardModel = deriveLyricsShareCardModel(
                    snapshot = snapshot,
                    lyrics = lyrics,
                    selectedLineIndices = current.selectedLyricsLineIndices,
                    template = template,
                    fontFamilyName = current.selectedLyricsShareFontFamily,
                ),
                sharePreviewError = null,
                isShareRendering = false,
                isShareSaving = false,
                isShareCopying = false,
                shareMessage = null,
            )
        }
        if (state.value.isLyricsShareVisible && state.value.selectedLyricsLineIndices.isNotEmpty()) {
            rebuildLyricsSharePreview()
        }
    }

    private suspend fun updateLyricsShareFont(familyName: String) {
        val current = state.value
        if (!supportsLyricsShareFontSelection()) return
        if (familyName == current.selectedLyricsShareFontFamily) return
        if (current.availableLyricsShareFonts.none { it.familyName == familyName }) return
        lyricsShareFontPreferencesStore.setSelectedLyricsShareFontFamily(familyName)
        invalidateLyricsSharePreviewRequests()
        updateState {
            val snapshot = it.snapshot
            val lyrics = it.lyrics
            it.copy(
                selectedLyricsShareFontFamily = familyName,
                shareCardModel = deriveLyricsShareCardModel(
                    snapshot = snapshot,
                    lyrics = lyrics,
                    selectedLineIndices = it.selectedLyricsLineIndices,
                    template = it.selectedLyricsShareTemplate,
                    fontFamilyName = familyName,
                ),
                sharePreviewError = null,
                isShareRendering = false,
                isShareSaving = false,
                isShareCopying = false,
                shareMessage = null,
            )
        }
        if (state.value.isLyricsShareVisible && state.value.selectedLyricsLineIndices.isNotEmpty()) {
            rebuildLyricsSharePreview()
        }
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
                shareCardModel = deriveLyricsShareCardModel(
                    snapshot = snapshot,
                    lyrics = lyrics,
                    selectedLineIndices = updatedSelection,
                    template = it.selectedLyricsShareTemplate,
                    fontFamilyName = it.selectedLyricsShareFontFamily,
                ),
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
                sharePreviewTemplate = null,
                sharePreviewFontFamilyName = null,
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
                message = null,
            )
        }
    }

    private suspend fun searchManualLyrics() {
        val current = state.value
        val currentTrack = current.snapshot.currentTrack ?: return
        val title = current.manualLyricsTitle.trim()
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
            artistName = current.manualLyricsArtistName.trim().ifBlank { null },
            albumTitle = current.manualLyricsAlbumTitle.trim().ifBlank { null },
        )
        val includeTrackProvidedCandidate = current.matchesCurrentLyricsLookupTrack()
        updateState {
            it.copy(
                isManualLyricsSearchLoading = true,
                hasManualLyricsSearchResult = false,
                manualLyricsResults = emptyList(),
                manualWorkflowSongResults = emptyList(),
                manualLyricsError = null,
                message = null,
            )
        }
        val directResult = runCatching {
            lyricsRepository.searchLyricsCandidates(
                track = searchTrack,
                includeTrackProvidedCandidate = includeTrackProvidedCandidate,
            )
        }
        val workflowResult = runCatching { lyricsRepository.searchWorkflowSongCandidates(searchTrack) }
        updateState { latest ->
            latest.copy(
                isManualLyricsSearchLoading = false,
                hasManualLyricsSearchResult = true,
                manualLyricsResults = directResult.getOrDefault(emptyList()),
                manualWorkflowSongResults = workflowResult.getOrDefault(emptyList()),
                manualLyricsError = directResult.exceptionOrNull()?.message ?: workflowResult.exceptionOrNull()?.message,
                message = null,
            )
        }
    }

    private suspend fun applyManualLyricsCandidate(
        candidate: LyricsSearchCandidate,
        mode: LyricsSearchApplyMode,
    ) {
        val track = state.value.snapshot.currentTrack ?: return
        val snapshot = state.value.snapshot
        val result = runCatching {
            lyricsRepository.applyLyricsCandidate(track.id, candidate, mode)
        }.getOrElse { throwable ->
            updateState { it.copy(message = throwable.message ?: applyFailureMessage(mode)) }
            return
        }
        val document = result.document ?: state.value.lyrics
        val artworkLocator = result.artworkLocator
        if (mode != LyricsSearchApplyMode.LYRICS_ONLY && !artworkLocator.isNullOrBlank()) {
            logger.debug(PLAYER_LOG_TAG) {
                "playback-artwork-override source=manual-lyrics track=${track.id} locator=$artworkLocator"
            }
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
                message = null,
            )
        }
    }

    private suspend fun applyWorkflowSongCandidate(
        candidate: WorkflowSongCandidate,
        mode: LyricsSearchApplyMode,
    ) {
        val track = state.value.snapshot.currentTrack ?: return
        val snapshot = state.value.snapshot
        val result = runCatching {
            lyricsRepository.applyWorkflowSongCandidate(track.id, candidate, mode)
        }.getOrElse { throwable ->
            updateState { it.copy(message = throwable.message ?: applyFailureMessage(mode)) }
            return
        }
        val document = result.document ?: state.value.lyrics
        val artworkLocator = result.artworkLocator
        if (mode != LyricsSearchApplyMode.LYRICS_ONLY && !artworkLocator.isNullOrBlank()) {
            logger.debug(PLAYER_LOG_TAG) {
                "playback-artwork-override source=manual-workflow track=${track.id} locator=$artworkLocator"
            }
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
                message = null,
            )
        }
    }

    private suspend fun rebuildLyricsSharePreview(): ByteArray? {
        val snapshot = state.value.snapshot
        val lyrics = state.value.lyrics
        val model = deriveLyricsShareCardModel(
            snapshot = snapshot,
            lyrics = lyrics,
            selectedLineIndices = state.value.selectedLyricsLineIndices,
            template = state.value.selectedLyricsShareTemplate,
            fontFamilyName = state.value.selectedLyricsShareFontFamily,
        )
        if (!state.value.isLyricsShareVisible || model == null) {
            updateState {
                it.copy(
                    shareCardModel = model,
                    sharePreviewBytes = null,
                    sharePreviewSelection = emptySet(),
                    sharePreviewTemplate = null,
                    sharePreviewFontFamilyName = null,
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
                        sharePreviewTemplate = model.template,
                        sharePreviewFontFamilyName = model.fontFamilyName,
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
        template: LyricsShareTemplate,
        fontFamilyName: String?,
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
            template = template,
            lyricsLines = selectedLines,
            fontFamilyName = fontFamilyName,
        )
    }

    private suspend fun loadLyricsShareFonts() {
        if (!supportsLyricsShareFontSelection()) return
        val currentState = state.value
        if (currentState.availableLyricsShareFonts.isNotEmpty() || currentState.isLyricsShareFontsLoading) return
        updateState {
            it.copy(
                isLyricsShareFontsLoading = true,
                lyricsShareFontsError = null,
            )
        }
        val result = lyricsSharePlatformService.listAvailableFontFamilies()
        result.fold(
            onSuccess = { fonts ->
                val normalizedFonts = fonts
                    .mapNotNull { option ->
                        option.familyName.trim().takeIf { it.isNotEmpty() }?.let { normalizedFamilyName ->
                            LyricsShareFontOption(
                                familyName = normalizedFamilyName,
                                previewText = option.previewText.ifBlank { normalizedFamilyName },
                                isPrioritized = option.isPrioritized,
                            )
                        }
                    }
                    .distinctBy { it.familyName.lowercase() }
                val resolvedFontFamily = resolveLyricsShareFontFamilySelection(
                    availableFonts = normalizedFonts,
                    preferredFontFamily = currentState.selectedLyricsShareFontFamily
                        ?: lyricsShareFontPreferencesStore.selectedLyricsShareFontFamily.value
                        ?: DEFAULT_LYRICS_SHARE_FONT_FAMILY,
                )
                if (normalizedFonts.isEmpty() || resolvedFontFamily == null) {
                    updateState {
                        it.copy(
                            isLyricsShareFontsLoading = false,
                            lyricsShareFontsError = "读取系统字体失败",
                        )
                    }
                    return
                }
                if (lyricsShareFontPreferencesStore.selectedLyricsShareFontFamily.value != resolvedFontFamily) {
                    lyricsShareFontPreferencesStore.setSelectedLyricsShareFontFamily(resolvedFontFamily)
                }
                val shouldRebuild = currentState.isLyricsShareVisible &&
                    currentState.selectedLyricsLineIndices.isNotEmpty() &&
                    currentState.selectedLyricsShareFontFamily != resolvedFontFamily
                updateState { latest ->
                    latest.copy(
                        availableLyricsShareFonts = normalizedFonts,
                        selectedLyricsShareFontFamily = resolvedFontFamily,
                        isLyricsShareFontsLoading = false,
                        lyricsShareFontsError = null,
                        shareCardModel = if (latest.isLyricsShareVisible) {
                            deriveLyricsShareCardModel(
                                snapshot = latest.snapshot,
                                lyrics = latest.lyrics,
                                selectedLineIndices = latest.selectedLyricsLineIndices,
                                template = latest.selectedLyricsShareTemplate,
                                fontFamilyName = resolvedFontFamily,
                            )
                        } else {
                            latest.shareCardModel
                        },
                    )
                }
                if (shouldRebuild) {
                    rebuildLyricsSharePreview()
                }
            },
            onFailure = {
                updateState { current ->
                    current.copy(
                        isLyricsShareFontsLoading = false,
                        lyricsShareFontsError = "读取系统字体失败",
                    )
                }
            },
        )
    }

    private fun supportsLyricsShareFontSelection(): Boolean {
        return lyricsShareFontPreferencesStore !== UnsupportedLyricsShareFontPreferencesStore
    }

    private fun invalidateLyricsSharePreviewRequests() {
        currentSharePreviewRequestId += 1
    }

    private fun launchAutomaticLyricsLoad(
        track: Track,
        lookupTrack: Track,
        requestKey: String?,
    ) {
        val requestId = nextLyricsLoadRequestId(
            trackId = track.id,
            requestKey = requestKey,
        )
        val previousJob = lyricsLoadJob
        updateState { it.copy(isLyricsLoading = true, lyrics = null, highlightedLineIndex = -1) }
        lyricsLoadJob = storeScope.launch {
            previousJob?.cancelAndJoin()
            if (!isLatestLyricsRequest(requestId, track.id, requestKey)) return@launch
            val result = runCatching { lyricsRepository.getLyrics(lookupTrack) }.getOrNull()
            if (!isActive || !isLatestLyricsRequest(requestId, track.id, requestKey)) return@launch
            val lyrics = result?.document
            val artworkLocator = result?.artworkLocator
            if (!artworkLocator.isNullOrBlank() && isLatestLyricsRequest(requestId, track.id, requestKey)) {
                logger.debug(PLAYER_LOG_TAG) {
                    "playback-artwork-override source=auto-lyrics track=${track.id} locator=$artworkLocator"
                }
                playbackRepository.overrideCurrentTrackArtwork(artworkLocator)
            }
            updateState { latest ->
                if (!isLatestLyricsRequest(requestId, track.id, requestKey)) {
                    latest
                } else {
                    latest.copy(
                        isLyricsLoading = false,
                        lyrics = lyrics,
                        highlightedLineIndex = findHighlightedLine(lyrics, latest.snapshot.positionMs),
                    )
                }
            }
        }
    }

    private fun cancelAutomaticLyricsLoad(resetTracking: Boolean) {
        lyricsLoadJob?.cancel()
        lyricsLoadJob = null
        currentLyricsLoadRequestId += 1
        if (resetTracking) {
            currentLyricsTrackId = null
            currentLyricsRequestKey = null
        }
    }

    private fun nextLyricsLoadRequestId(
        trackId: String,
        requestKey: String?,
    ): Long {
        lyricsLoadJob?.cancel()
        currentLyricsTrackId = trackId
        currentLyricsRequestKey = requestKey
        currentLyricsLoadRequestId += 1
        return currentLyricsLoadRequestId
    }

    private fun isLatestLyricsRequest(
        requestId: Long,
        trackId: String,
        requestKey: String?,
    ): Boolean {
        return requestId == currentLyricsLoadRequestId &&
            trackId == currentLyricsTrackId &&
            requestKey == currentLyricsRequestKey
    }

    private fun nextLyricsSharePreviewRequestId(): Long {
        currentSharePreviewRequestId += 1
        return currentSharePreviewRequestId
    }

    private fun applyFailureMessage(mode: LyricsSearchApplyMode): String {
        return if (mode == LyricsSearchApplyMode.ARTWORK_ONLY) {
            "封面应用失败。"
        } else {
            "歌词应用失败。"
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

private fun PlayerState.clearLyricsShareState(): PlayerState {
    return copy(
        isLyricsShareVisible = false,
        selectedLyricsLineIndices = emptySet(),
        shareCardModel = null,
        sharePreviewBytes = null,
        sharePreviewSelection = emptySet(),
        sharePreviewTemplate = null,
        sharePreviewFontFamilyName = null,
        sharePreviewError = null,
        isShareRendering = false,
        isShareSaving = false,
        isShareCopying = false,
        shareMessage = null,
    )
}

private fun resolveLyricsShareFontFamilySelection(
    availableFonts: List<LyricsShareFontOption>,
    preferredFontFamily: String?,
): String? {
    if (availableFonts.isEmpty()) return null
    fun resolveCandidate(value: String?): String? {
        val normalizedValue = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return availableFonts.firstOrNull { it.familyName.equals(normalizedValue, ignoreCase = true) }?.familyName
    }
    return resolveCandidate(preferredFontFamily)
        ?: resolveCandidate(DEFAULT_LYRICS_SHARE_FONT_FAMILY)
        ?: availableFonts.first().familyName
}

private data class PlaybackErrorKey(
    val trackId: String?,
    val message: String,
)

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

private fun PlayerState.matchesCurrentLyricsLookupTrack(): Boolean {
    val lookupTrack = snapshot.toLyricsLookupTrack() ?: return false
    return manualLyricsTitle.trim() == lookupTrack.title.trim() &&
        manualLyricsArtistName.trim() == lookupTrack.artistName.orEmpty().trim() &&
        manualLyricsAlbumTitle.trim() == lookupTrack.albumTitle.orEmpty().trim()
}

private const val PLAYER_LOG_TAG = "Player"
