package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.DEFAULT_LYRICS_SHARE_FONT_KEY
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.LyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.LyricsShareTemplate
import top.iwesley.lyn.music.core.model.LyricsSearchApplyMode
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
import top.iwesley.lyn.music.core.model.LyricsSharePlatformService
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.UnsupportedLyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.UnsupportedLyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.WorkflowSongCandidate
import top.iwesley.lyn.music.core.model.UnsupportedLyricsSharePlatformService
import top.iwesley.lyn.music.core.model.buildLyricsShareSuggestedName
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator
import top.iwesley.lyn.music.core.model.parseLyricsShareImportedFontHash
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.LyricsRepository
import top.iwesley.lyn.music.data.repository.PlaybackRepository

const val MIN_SLEEP_TIMER_MINUTES = 1
const val MAX_SLEEP_TIMER_MINUTES = 999
const val SLEEP_TIMER_TICK_MS = 1_000L

val SLEEP_TIMER_PRESET_MINUTES = listOf(10, 15, 20, 30, 45, 60)

data class SleepTimerState(
    val durationMinutes: Int? = null,
    val remainingMs: Long = 0L,
) {
    val isActive: Boolean
        get() = durationMinutes != null && remainingMs > 0L
}

fun normalizeSleepTimerMinutes(minutes: Int): Int? {
    return minutes.takeIf { it in MIN_SLEEP_TIMER_MINUTES..MAX_SLEEP_TIMER_MINUTES }
}

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
    val selectedLyricsShareFontKey: String? = null,
    val selectedLyricsShareFontDisplayName: String? = null,
    val isLyricsShareFontsLoading: Boolean = false,
    val lyricsShareFontsError: String? = null,
    val selectedLyricsLineIndices: Set<Int> = emptySet(),
    val shareCardModel: LyricsShareCardModel? = null,
    val sharePreviewBytes: ByteArray? = null,
    val sharePreviewSelection: Set<Int> = emptySet(),
    val sharePreviewTemplate: LyricsShareTemplate? = null,
    val sharePreviewFontKey: String? = null,
    val sharePreviewError: String? = null,
    val isShareRendering: Boolean = false,
    val isShareSaving: Boolean = false,
    val isShareCopying: Boolean = false,
    val sleepTimer: SleepTimerState = SleepTimerState(),
    val shareMessage: String? = null,
    val message: String? = null,
) {
    val hasFreshSharePreview: Boolean
        get() = sharePreviewBytes != null &&
            sharePreviewSelection == selectedLyricsLineIndices &&
            sharePreviewTemplate == selectedLyricsShareTemplate &&
            sharePreviewFontKey == selectedLyricsShareFontKey
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
    data class StartSleepTimer(val minutes: Int) : PlayerIntent
    data object CancelSleepTimer : PlayerIntent
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
    data object InvalidateLyricsShareFontCache : PlayerIntent
    data class LyricsShareFontChanged(val fontKey: String) : PlayerIntent
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
    private val lyricsShareFontLibraryPlatformService: LyricsShareFontLibraryPlatformService =
        UnsupportedLyricsShareFontLibraryPlatformService,
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
    private var lyricsShareFontsLoadGeneration: Long = 0L
    private var lastPlaybackErrorKey: PlaybackErrorKey? = null
    private var sleepTimerJob: Job? = null

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
            is PlayerIntent.StartSleepTimer -> startSleepTimer(intent.minutes)
            PlayerIntent.CancelSleepTimer -> cancelSleepTimer()
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
            PlayerIntent.InvalidateLyricsShareFontCache -> invalidateLyricsShareFontCache()
            is PlayerIntent.LyricsShareFontChanged -> updateLyricsShareFont(intent.fontKey)
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
        playbackRepository.playQueueIndex(index)
        updateState { it.copy(isQueueVisible = false) }
    }

    private suspend fun startSleepTimer(minutes: Int) {
        val normalizedMinutes = normalizeSleepTimerMinutes(minutes) ?: return
        sleepTimerJob?.cancelAndJoin()
        val durationMs = normalizedMinutes * 60_000L
        updateState {
            it.copy(
                sleepTimer = SleepTimerState(
                    durationMinutes = normalizedMinutes,
                    remainingMs = durationMs,
                ),
            )
        }
        sleepTimerJob = storeScope.launch {
            var remainingMs = durationMs
            while (remainingMs > 0L && isActive) {
                delay(SLEEP_TIMER_TICK_MS)
                remainingMs = (remainingMs - SLEEP_TIMER_TICK_MS).coerceAtLeast(0L)
                if (remainingMs > 0L) {
                    updateState { current ->
                        if (current.sleepTimer.isActive) {
                            current.copy(sleepTimer = current.sleepTimer.copy(remainingMs = remainingMs))
                        } else {
                            current
                        }
                    }
                }
            }
            if (isActive) {
                playbackRepository.pause()
                updateState { it.copy(sleepTimer = SleepTimerState()) }
            }
        }
    }

    private suspend fun cancelSleepTimer() {
        sleepTimerJob?.cancelAndJoin()
        sleepTimerJob = null
        updateState { it.copy(sleepTimer = SleepTimerState()) }
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
        val savedFontKey = if (supportsFontSelection) {
            lyricsShareFontPreferencesStore.selectedLyricsShareFontKey.value
        } else {
            null
        }
        var selectedFontKey = currentState.selectedLyricsShareFontKey
            ?: savedFontKey
            ?: DEFAULT_LYRICS_SHARE_FONT_KEY.takeIf { supportsFontSelection }
        var selectedFontDisplayName = currentState.selectedLyricsShareFontDisplayName
            .takeIf { currentState.selectedLyricsShareFontKey == selectedFontKey }
        var clearedMissingImportedFont = false
        var resolvedImportedFontOnOpen = false
        when (val importedFont = resolveOpeningImportedLyricsShareFont(selectedFontKey)) {
            ImportedLyricsShareFontOpenResult.Missing -> {
                lyricsShareFontPreferencesStore.setSelectedLyricsShareFontKey(null)
                selectedFontKey = DEFAULT_LYRICS_SHARE_FONT_KEY.takeIf { supportsFontSelection }
                selectedFontDisplayName = null
                clearedMissingImportedFont = true
            }

            is ImportedLyricsShareFontOpenResult.Resolved -> {
                selectedFontDisplayName = importedFont.displayName
                resolvedImportedFontOnOpen = true
            }

            null -> Unit
        }
        if (supportsFontSelection && currentState.availableLyricsShareFonts.isNotEmpty()) {
            val availableSelectedFont = currentState.availableLyricsShareFonts.firstOrNull { option ->
                selectedFontKey?.let { option.fontKey.equals(it, ignoreCase = true) } == true
            }
            if (availableSelectedFont != null) {
                selectedFontDisplayName = availableSelectedFont.displayName
            } else if (!resolvedImportedFontOnOpen) {
                val resolvedFontKey = resolveLyricsShareFontSelection(
                    availableFonts = currentState.availableLyricsShareFonts,
                    preferredFontKey = selectedFontKey,
                )
                if (resolvedFontKey != null && resolvedFontKey != savedFontKey && !clearedMissingImportedFont) {
                    lyricsShareFontPreferencesStore.setSelectedLyricsShareFontKey(resolvedFontKey)
                }
                selectedFontKey = resolvedFontKey
                selectedFontDisplayName = currentState.availableLyricsShareFonts
                    .firstOrNull { option ->
                        resolvedFontKey?.let { option.fontKey.equals(it, ignoreCase = true) } == true
                    }
                    ?.displayName
            }
        }
        val defaultSelection = resolveDefaultLyricsShareSelection(
            lyrics = lyrics,
            highlightedLineIndex = currentState.highlightedLineIndex,
        )?.let(::setOf).orEmpty()
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
                selectedLyricsShareFontKey = selectedFontKey,
                selectedLyricsShareFontDisplayName = selectedFontDisplayName,
                selectedLyricsLineIndices = defaultSelection,
                shareCardModel = deriveLyricsShareCardModel(
                    snapshot = snapshot,
                    lyrics = lyrics,
                    selectedLineIndices = defaultSelection,
                    template = it.selectedLyricsShareTemplate,
                    fontKey = selectedFontKey,
                ),
                sharePreviewBytes = null,
                sharePreviewSelection = emptySet(),
                sharePreviewTemplate = null,
                sharePreviewFontKey = null,
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
                    fontKey = current.selectedLyricsShareFontKey,
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

    private suspend fun updateLyricsShareFont(fontKey: String) {
        val current = state.value
        if (!supportsLyricsShareFontSelection()) return
        if (fontKey == current.selectedLyricsShareFontKey) return
        val selectedFont = current.availableLyricsShareFonts.firstOrNull { it.fontKey == fontKey } ?: return
        lyricsShareFontPreferencesStore.setSelectedLyricsShareFontKey(fontKey)
        invalidateLyricsSharePreviewRequests()
        updateState {
            val snapshot = it.snapshot
            val lyrics = it.lyrics
            it.copy(
                selectedLyricsShareFontKey = fontKey,
                selectedLyricsShareFontDisplayName = selectedFont.displayName,
                shareCardModel = deriveLyricsShareCardModel(
                    snapshot = snapshot,
                    lyrics = lyrics,
                    selectedLineIndices = it.selectedLyricsLineIndices,
                    template = it.selectedLyricsShareTemplate,
                    fontKey = fontKey,
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
        if (!isSelectableLyricsShareLine(line)) return
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
                    fontKey = it.selectedLyricsShareFontKey,
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
                sharePreviewFontKey = null,
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
            fontKey = state.value.selectedLyricsShareFontKey,
        )
        if (!state.value.isLyricsShareVisible || model == null) {
            updateState {
                it.copy(
                    shareCardModel = model,
                    sharePreviewBytes = null,
                    sharePreviewSelection = emptySet(),
                    sharePreviewTemplate = null,
                    sharePreviewFontKey = null,
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
                        sharePreviewFontKey = model.fontKey,
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
        fontKey: String?,
    ): LyricsShareCardModel? {
        val selectedLines = lyrics?.lines.orEmpty()
            .mapIndexedNotNull { index, line ->
                if (index !in selectedLineIndices) return@mapIndexedNotNull null
                line.text.trim().takeIf(::isSelectableLyricsShareLine)
            }
        if (selectedLines.isEmpty()) return null
        return LyricsShareCardModel(
            title = snapshot.currentDisplayTitle.ifBlank { snapshot.currentTrack?.title.orEmpty() },
            artistName = snapshot.currentDisplayArtistName ?: snapshot.currentTrack?.artistName,
            artworkLocator = snapshot.currentDisplayArtworkLocator,
            template = template,
            lyricsLines = selectedLines,
            fontKey = fontKey,
        )
    }

    private suspend fun resolveOpeningImportedLyricsShareFont(
        fontKey: String?,
    ): ImportedLyricsShareFontOpenResult? {
        val normalizedFontKey = fontKey?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        parseLyricsShareImportedFontHash(normalizedFontKey) ?: return null
        val fontPath = lyricsShareFontLibraryPlatformService.resolveImportedFontPath(normalizedFontKey)
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return ImportedLyricsShareFontOpenResult.Missing
        val displayName = lyricsShareFontLibraryPlatformService.listImportedFonts()
            .getOrNull()
            ?.firstOrNull { option -> option.fontKey.equals(normalizedFontKey, ignoreCase = true) }
            ?.displayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: parseImportedLyricsShareFontDisplayNameFromPath(fontPath)
        return ImportedLyricsShareFontOpenResult.Resolved(displayName)
    }

    private suspend fun loadLyricsShareFonts() {
        if (!supportsLyricsShareFontSelection()) return
        val currentState = state.value
        if (currentState.availableLyricsShareFonts.isNotEmpty() || currentState.isLyricsShareFontsLoading) return
        val loadGeneration = lyricsShareFontsLoadGeneration
        updateState {
            it.copy(
                isLyricsShareFontsLoading = true,
                lyricsShareFontsError = null,
            )
        }
        val result = lyricsSharePlatformService.listAvailableFontFamilies()
        if (loadGeneration != lyricsShareFontsLoadGeneration) return
        result.fold(
            onSuccess = { fonts ->
                val normalizedFonts = fonts
                    .mapNotNull { option ->
                        option.fontKey.trim().takeIf { it.isNotEmpty() }?.let { normalizedFontKey ->
                            LyricsShareFontOption(
                                fontKey = normalizedFontKey,
                                displayName = option.displayName.trim().ifBlank { normalizedFontKey },
                                previewText = option.previewText.ifBlank { option.displayName.ifBlank { normalizedFontKey } },
                                isPrioritized = option.isPrioritized,
                                kind = option.kind,
                                fontFilePath = option.fontFilePath?.trim()?.takeIf { it.isNotEmpty() },
                            )
                        }
                    }
                    .distinctBy { it.fontKey.lowercase() }
                val resolvedFontKey = resolveLyricsShareFontSelection(
                    availableFonts = normalizedFonts,
                    preferredFontKey = currentState.selectedLyricsShareFontKey
                        ?: lyricsShareFontPreferencesStore.selectedLyricsShareFontKey.value
                        ?: DEFAULT_LYRICS_SHARE_FONT_KEY,
                )
                if (normalizedFonts.isEmpty() || resolvedFontKey == null) {
                    updateState {
                        it.copy(
                            isLyricsShareFontsLoading = false,
                            lyricsShareFontsError = "读取系统字体失败",
                        )
                    }
                    return
                }
                if (lyricsShareFontPreferencesStore.selectedLyricsShareFontKey.value != resolvedFontKey) {
                    lyricsShareFontPreferencesStore.setSelectedLyricsShareFontKey(resolvedFontKey)
                }
                val resolvedFontDisplayName = normalizedFonts.firstOrNull { option ->
                    option.fontKey.equals(resolvedFontKey, ignoreCase = true)
                }?.displayName
                val shouldRebuild = currentState.isLyricsShareVisible &&
                    currentState.selectedLyricsLineIndices.isNotEmpty() &&
                    currentState.selectedLyricsShareFontKey != resolvedFontKey
                updateState { latest ->
                    latest.copy(
                        availableLyricsShareFonts = normalizedFonts,
                        selectedLyricsShareFontKey = resolvedFontKey,
                        selectedLyricsShareFontDisplayName = resolvedFontDisplayName,
                        isLyricsShareFontsLoading = false,
                        lyricsShareFontsError = null,
                        shareCardModel = if (latest.isLyricsShareVisible) {
                            deriveLyricsShareCardModel(
                                snapshot = latest.snapshot,
                                lyrics = latest.lyrics,
                                selectedLineIndices = latest.selectedLyricsLineIndices,
                                template = latest.selectedLyricsShareTemplate,
                                fontKey = resolvedFontKey,
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

    private fun invalidateLyricsShareFontCache() {
        lyricsShareFontsLoadGeneration += 1
        updateState {
            it.copy(
                availableLyricsShareFonts = emptyList(),
                isLyricsShareFontsLoading = false,
                lyricsShareFontsError = null,
            )
        }
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

    private fun resolveDefaultLyricsShareSelection(
        lyrics: LyricsDocument,
        highlightedLineIndex: Int,
    ): Int? {
        if (highlightedLineIndex !in lyrics.lines.indices) return null
        if (isSelectableLyricsShareLine(lyrics.lines[highlightedLineIndex].text)) {
            return highlightedLineIndex
        }
        for (index in highlightedLineIndex + 1 until lyrics.lines.size) {
            if (isSelectableLyricsShareLine(lyrics.lines[index].text)) {
                return index
            }
        }
        for (index in highlightedLineIndex - 1 downTo 0) {
            if (isSelectableLyricsShareLine(lyrics.lines[index].text)) {
                return index
            }
        }
        return null
    }

    private fun isSelectableLyricsShareLine(text: String): Boolean {
        val normalized = text.trim()
        return normalized.isNotEmpty() && !isPlayerLyricsStructureTagLine(normalized)
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
        selectedLyricsShareFontDisplayName = null,
        selectedLyricsLineIndices = emptySet(),
        shareCardModel = null,
        sharePreviewBytes = null,
        sharePreviewSelection = emptySet(),
        sharePreviewTemplate = null,
        sharePreviewFontKey = null,
        sharePreviewError = null,
        isShareRendering = false,
        isShareSaving = false,
        isShareCopying = false,
        shareMessage = null,
    )
}

private sealed interface ImportedLyricsShareFontOpenResult {
    data object Missing : ImportedLyricsShareFontOpenResult
    data class Resolved(val displayName: String?) : ImportedLyricsShareFontOpenResult
}

private fun parseImportedLyricsShareFontDisplayNameFromPath(path: String): String? {
    val fileName = path
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .trim()
    val separatorIndex = fileName.indexOf("__")
    if (separatorIndex <= 0) return null
    val extensionStart = fileName.lastIndexOf('.').takeIf { it > separatorIndex + 2 } ?: fileName.length
    return fileName
        .substring(separatorIndex + 2, extensionStart)
        .trim()
        .takeIf { it.isNotEmpty() }
}

private fun resolveLyricsShareFontSelection(
    availableFonts: List<LyricsShareFontOption>,
    preferredFontKey: String?,
): String? {
    if (availableFonts.isEmpty()) return null
    fun resolveCandidate(value: String?): String? {
        val normalizedValue = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return availableFonts.firstOrNull { it.fontKey.equals(normalizedValue, ignoreCase = true) }?.fontKey
    }
    return resolveCandidate(preferredFontKey)
        ?: resolveCandidate(DEFAULT_LYRICS_SHARE_FONT_KEY)
        ?: availableFonts.first().fontKey
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
