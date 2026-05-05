package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.cast.CastGateway
import top.iwesley.lyn.music.cast.CastMediaRequest
import top.iwesley.lyn.music.cast.CastMediaUrlResolver
import top.iwesley.lyn.music.cast.CastProxySession
import top.iwesley.lyn.music.cast.CastSessionForegroundCallbacks
import top.iwesley.lyn.music.cast.CastSessionForegroundPlatformService
import top.iwesley.lyn.music.cast.CastSessionForegroundState
import top.iwesley.lyn.music.cast.CastSessionState
import top.iwesley.lyn.music.cast.CastSessionStatus
import top.iwesley.lyn.music.cast.UnsupportedCastSessionForegroundPlatformService
import top.iwesley.lyn.music.cast.UnsupportedCastMediaUrlResolver
import top.iwesley.lyn.music.cast.UnsupportedCastGateway
import top.iwesley.lyn.music.cast.buildDirectCastMediaRequest
import top.iwesley.lyn.music.cast.directCastUriOrNull
import top.iwesley.lyn.music.cast.isDirectCastUri
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
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
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.PlaybackMode
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
import top.iwesley.lyn.music.core.model.parseNavidromeCoverLocator
import top.iwesley.lyn.music.core.model.parseNavidromeSongLocator
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.LyricsRepository
import top.iwesley.lyn.music.data.repository.PlaybackRepository

const val MIN_SLEEP_TIMER_MINUTES = 1
const val MAX_SLEEP_TIMER_MINUTES = 999
const val SLEEP_TIMER_TICK_MS = 1_000L
private const val PLAYBACK_UNSEEKABLE_MESSAGE = "歌曲可能正在转码，不支持快进。"

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
    val castState: CastSessionState = CastSessionState(),
    val castQueueIndex: Int? = null,
    val isCastSheetVisible: Boolean = false,
    val castMessage: String? = null,
    val shareMessage: String? = null,
    val message: String? = null,
) {
    val hasFreshSharePreview: Boolean
        get() = sharePreviewBytes != null &&
            sharePreviewSelection == selectedLyricsLineIndices &&
            sharePreviewTemplate == selectedLyricsShareTemplate &&
            sharePreviewFontKey == selectedLyricsShareFontKey

    val effectiveSnapshot: PlaybackSnapshot
        get() {
            val shouldUseCastSnapshot = castState.status == CastSessionStatus.Casting ||
                (castState.status == CastSessionStatus.Connecting &&
                    castQueueIndex?.let { it in snapshot.queue.indices } == true)
            if (!shouldUseCastSnapshot) return snapshot
            val effectiveIndex = castQueueIndex
                ?.takeIf { it in snapshot.queue.indices }
                ?: snapshot.currentIndex
            val track = snapshot.queue.getOrNull(effectiveIndex)
            val playback = castState.playback
            val remoteDurationMs = playback?.durationMs?.takeIf { it > 0L }
                ?: track?.durationMs?.takeIf { it > 0L }
                ?: snapshot.durationMs
            val remotePositionMs = playback?.positionMs
                ?.coerceAtLeast(0L)
                ?.let { position ->
                    remoteDurationMs.takeIf { it > 0L }?.let(position::coerceAtMost) ?: position
                }
                ?: if (castState.status == CastSessionStatus.Connecting && effectiveIndex != snapshot.currentIndex) {
                    0L
                } else {
                    snapshot.positionMs
                }
            return snapshot.copy(
                currentIndex = effectiveIndex,
                isPlaying = playback?.isPlaying
                    ?: if (castState.status == CastSessionStatus.Connecting && effectiveIndex != snapshot.currentIndex) {
                        false
                    } else {
                        snapshot.isPlaying
                    },
                positionMs = remotePositionMs,
                durationMs = remoteDurationMs,
                canSeek = playback?.canSeek ?: snapshot.canSeek,
                metadataTitle = if (effectiveIndex == snapshot.currentIndex) snapshot.metadataTitle else null,
                metadataArtistName = if (effectiveIndex == snapshot.currentIndex) snapshot.metadataArtistName else null,
                metadataAlbumTitle = if (effectiveIndex == snapshot.currentIndex) snapshot.metadataAlbumTitle else null,
                metadataArtworkLocator = if (effectiveIndex == snapshot.currentIndex) snapshot.metadataArtworkLocator else null,
                currentNavidromeAudioQuality = if (effectiveIndex == snapshot.currentIndex) {
                    snapshot.currentNavidromeAudioQuality
                } else {
                    null
                },
                currentPlaybackAudioFormat = if (effectiveIndex == snapshot.currentIndex) {
                    snapshot.currentPlaybackAudioFormat
                } else {
                    null
                },
            )
        }
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
    data object OpenCastSheet : PlayerIntent
    data object DismissCastSheet : PlayerIntent
    data object RefreshCastDevices : PlayerIntent
    data class CastToDevice(val deviceId: String) : PlayerIntent
    data object StopCast : PlayerIntent
    data object ClearCastMessage : PlayerIntent
    data object CastNotificationPermissionDenied : PlayerIntent
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
    data object CopyLyricsShareText : PlayerIntent
    data object ClearLyricsShareMessage : PlayerIntent
    data object ClearMessage : PlayerIntent
}

sealed interface PlayerEffect

class PlayerStore(
    private val playbackRepository: PlaybackRepository,
    private val lyricsRepository: LyricsRepository,
    private val storeScope: CoroutineScope,
    private val castGateway: CastGateway = UnsupportedCastGateway,
    private val castMediaUrlResolver: CastMediaUrlResolver = UnsupportedCastMediaUrlResolver,
    private val castSessionForegroundPlatformService: CastSessionForegroundPlatformService =
        UnsupportedCastSessionForegroundPlatformService,
    private val lyricsSharePlatformService: LyricsSharePlatformService = UnsupportedLyricsSharePlatformService,
    private val lyricsShareFontLibraryPlatformService: LyricsShareFontLibraryPlatformService =
        UnsupportedLyricsShareFontLibraryPlatformService,
    private val lyricsShareFontPreferencesStore: LyricsShareFontPreferencesStore = UnsupportedLyricsShareFontPreferencesStore,
    private val artworkCacheStore: ArtworkCacheStore = object : ArtworkCacheStore {
        override suspend fun cache(locator: String, cacheKey: String, replaceExisting: Boolean): String? = locator
    },
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
    private var currentCastProxySession: CastProxySession? = null
    private var castResumeSession: CastResumeSession? = null
    private var isStoppingCastExplicitly = false
    private var lastAutoAdvancedEndedTrackId: String? = null

    init {
        castSessionForegroundPlatformService.bind(
            CastSessionForegroundCallbacks(
                togglePlayPause = { togglePlayPause() },
                skipNext = { skipNext() },
                skipPrevious = { skipPrevious() },
                stopCast = { stopCast() },
            ),
        )
        storeScope.launch {
            castGateway.state.collect { castState ->
                updateState { current ->
                    val updated = current.copy(castState = castState)
                    updated.copy(
                        highlightedLineIndex = if (castState.status == CastSessionStatus.Casting) {
                            findHighlightedLine(updated.lyrics, updated.effectiveSnapshot.positionMs)
                        } else {
                            updated.highlightedLineIndex
                        },
                    )
                }
                handleCastPlaybackUpdate(castState)
                syncAutomaticLyricsForEffectiveSnapshot()
                syncCastForegroundService()
            }
        }
        storeScope.launch {
            playbackRepository.snapshot.collect { snapshot ->
                applyPlaybackSnapshot(snapshot)
                syncCastForegroundService()
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
            is PlayerIntent.PlayTracks -> playTracks(intent.tracks, intent.startIndex)
            is PlayerIntent.PlayQueueIndex -> playQueueIndex(intent.index)
            PlayerIntent.TogglePlayPause -> togglePlayPause()
            PlayerIntent.SkipNext -> skipNext()
            PlayerIntent.SkipPrevious -> skipPrevious()
            is PlayerIntent.SeekTo -> seekTo(intent.positionMs)
            is PlayerIntent.SetVolume -> playbackRepository.setVolume(intent.value)
            PlayerIntent.CycleMode -> playbackRepository.cycleMode()
            is PlayerIntent.StartSleepTimer -> startSleepTimer(intent.minutes)
            PlayerIntent.CancelSleepTimer -> cancelSleepTimer()
            PlayerIntent.OpenCastSheet -> openCastSheet()
            PlayerIntent.DismissCastSheet -> dismissCastSheet()
            PlayerIntent.RefreshCastDevices -> refreshCastDevices()
            is PlayerIntent.CastToDevice -> castToDevice(intent.deviceId)
            PlayerIntent.StopCast -> stopCast()
            PlayerIntent.ClearCastMessage -> updateState { it.copy(castMessage = null) }
            PlayerIntent.CastNotificationPermissionDenied -> updateState {
                it.copy(message = "通知权限未开启，后台投屏通知可能不显示。")
            }
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
            PlayerIntent.CopyLyricsShareText -> copyLyricsShareText()
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

    private suspend fun playTracks(tracks: List<Track>, startIndex: Int) {
        if (!hasRemoteCastRoute()) {
            playbackRepository.playTracks(tracks, startIndex)
            return
        }
        if (tracks.isEmpty()) return
        playbackRepository.pause()
        val preparedSnapshot = playbackRepository.prepareExternalPlaybackQueue(tracks, startIndex) ?: return
        applyPlaybackSnapshot(preparedSnapshot)
        castQueueIndex(preparedSnapshot.currentIndex, pauseLocalAfterCast = false)
    }

    private fun applyPlaybackSnapshot(snapshot: PlaybackSnapshot) {
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
                    positionMs = if (current.castState.status == CastSessionStatus.Casting) {
                        current.effectiveSnapshot.positionMs
                    } else {
                        snapshot.positionMs
                    },
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
                castMessage = if (trackChanged) null else current.castMessage,
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
        syncAutomaticLyricsForEffectiveSnapshot()
    }

    private suspend fun playQueueIndex(index: Int) {
        val snapshot = state.value.snapshot
        if (index !in snapshot.queue.indices) return
        if (isRemoteCastActive()) {
            castQueueIndex(index, closeQueue = true)
            return
        }
        if (index == snapshot.currentIndex) {
            updateState { it.copy(isQueueVisible = false) }
            return
        }
        playbackRepository.playQueueIndex(index)
        updateState { it.copy(isQueueVisible = false) }
    }

    private suspend fun togglePlayPause() {
        if (!isRemoteCastActive()) {
            playbackRepository.togglePlayPause()
            return
        }
        val playback = state.value.castState.playback
        if (playback?.isPlaying == true) {
            castGateway.pauseCast()
        } else {
            castGateway.playCast()
        }
    }

    private suspend fun skipNext() {
        if (!isRemoteCastActive()) {
            playbackRepository.skipNext()
            return
        }
        val nextIndex = resolveNextCastIndex(autoTriggered = false) ?: return
        castQueueIndex(nextIndex)
    }

    private suspend fun skipPrevious() {
        if (!isRemoteCastActive()) {
            playbackRepository.skipPrevious()
            return
        }
        val current = state.value.effectiveSnapshot
        if (current.mode != PlaybackMode.REPEAT_ONE && current.canSeek && current.positionMs > 5_000L) {
            castGateway.seekCast(0L)
            return
        }
        val previousIndex = resolvePreviousCastIndex() ?: return
        castQueueIndex(previousIndex)
    }

    private suspend fun seekTo(positionMs: Long) {
        if (isRemoteCastActive()) {
            if (state.value.castState.playback?.canSeek != true) {
                updateState { it.copy(message = PLAYBACK_UNSEEKABLE_MESSAGE) }
                return
            }
            castGateway.seekCast(positionMs)
            return
        }
        if (!state.value.snapshot.canSeek) {
            updateState { it.copy(message = PLAYBACK_UNSEEKABLE_MESSAGE) }
            return
        }
        playbackRepository.seekTo(positionMs)
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

    private suspend fun openCastSheet() {
        if (!castGateway.isSupported) {
            updateState {
                it.copy(
                    isCastSheetVisible = true,
                    castMessage = "当前平台暂不支持投屏。",
                )
            }
            return
        }
        updateState { it.copy(isCastSheetVisible = true, castMessage = null) }
        refreshCastDevices()
    }

    private suspend fun dismissCastSheet() {
        updateState { it.copy(isCastSheetVisible = false, castMessage = null) }
        castGateway.stopDiscovery()
    }

    private suspend fun refreshCastDevices() {
        if (!castGateway.isSupported) {
            updateState { it.copy(castMessage = "当前平台暂不支持投屏。") }
            return
        }
        castGateway.startDiscovery()
    }

    private suspend fun castToDevice(deviceId: String) {
        val snapshot = state.value.snapshot
        val track = snapshot.currentTrack ?: run {
            updateState { it.copy(castMessage = "没有正在播放的歌曲。") }
            return
        }
        val resolved = resolveCastMediaRequest(track = track, snapshot = snapshot).getOrElse { error ->
            updateState { it.copy(castMessage = error.message ?: "当前歌曲暂不支持投屏。") }
            return
        }
        closeCurrentCastProxySession()
        currentCastProxySession = resolved.proxySession
        updateState {
            it.copy(
                castQueueIndex = snapshot.currentIndex,
                castMessage = null,
            )
        }
        castGateway.cast(deviceId = deviceId, request = resolved.request)
        if (castGateway.state.value.status == CastSessionStatus.Casting) {
            castResumeSession = CastResumeSession(
                trackId = track.id,
                wasPlayingBeforeCast = snapshot.isPlaying,
            )
            lastAutoAdvancedEndedTrackId = null
            playbackRepository.pause()
        }
    }

    private suspend fun stopCast() {
        val current = state.value
        val resumeSession = castResumeSession
        val resumeIndex = current.effectiveSnapshot.currentIndex
        val resumePositionMs = current.castState.playback?.positionMs
            ?: current.effectiveSnapshot.positionMs
        isStoppingCastExplicitly = true
        try {
            castGateway.stopCast()
        } finally {
            isStoppingCastExplicitly = false
        }
        closeCurrentCastProxySession()
        castResumeSession = null
        lastAutoAdvancedEndedTrackId = null
        updateState {
            it.copy(
                castQueueIndex = null,
                castMessage = null,
            )
        }
        castSessionForegroundPlatformService.stop()
        syncAutomaticLyricsForEffectiveSnapshot()
        if (resumeSession != null) {
            resumeLocalPlaybackAfterCast(
                targetIndex = resumeIndex,
                positionMs = resumePositionMs,
                wasPlayingBeforeCast = resumeSession.wasPlayingBeforeCast,
            )
        }
    }

    private suspend fun castQueueIndex(
        index: Int,
        closeQueue: Boolean = false,
        autoTriggered: Boolean = false,
        pauseLocalAfterCast: Boolean = true,
    ) {
        val currentState = state.value
        val snapshot = currentState.snapshot
        val track = snapshot.queue.getOrNull(index) ?: return
        val deviceId = currentState.castState.selectedDeviceId ?: return
        val targetSnapshot = snapshot.forCastQueueIndex(index)
        val resolved = resolveCastMediaRequest(track = track, snapshot = targetSnapshot).getOrElse { error ->
            updateState {
                it.copy(
                    castMessage = error.message ?: "当前歌曲暂不支持投屏。",
                    isQueueVisible = if (closeQueue) false else it.isQueueVisible,
                )
            }
            return
        }
        closeCurrentCastProxySession()
        currentCastProxySession = resolved.proxySession
        lastAutoAdvancedEndedTrackId = null
        updateState {
            it.copy(
                castQueueIndex = index,
                castMessage = null,
                isQueueVisible = if (closeQueue) false else it.isQueueVisible,
            )
        }
        syncAutomaticLyricsForEffectiveSnapshot()
        castGateway.cast(deviceId = deviceId, request = resolved.request)
        if (castGateway.state.value.status == CastSessionStatus.Casting && !autoTriggered && pauseLocalAfterCast) {
            playbackRepository.pause()
        }
    }

    private suspend fun handleCastPlaybackUpdate(castState: CastSessionState) {
        if (isStoppingCastExplicitly) return
        if (castState.status != CastSessionStatus.Casting) return
        val playback = castState.playback ?: return
        if (!playback.isEnded) return
        val snapshot = state.value.effectiveSnapshot
        val track = snapshot.currentTrack ?: return
        if (lastAutoAdvancedEndedTrackId == track.id) return
        lastAutoAdvancedEndedTrackId = track.id
        val nextIndex = resolveNextCastIndex(autoTriggered = true) ?: return
        castQueueIndex(index = nextIndex, autoTriggered = true)
    }

    private suspend fun syncCastForegroundService(current: PlayerState = state.value) {
        val status = current.castState.status
        val shouldKeepService = current.effectiveSnapshot.currentTrack != null &&
            (status == CastSessionStatus.Casting ||
                (status == CastSessionStatus.Connecting && current.castState.selectedDeviceId != null))
        if (!shouldKeepService) {
            castSessionForegroundPlatformService.stop()
            return
        }
        castSessionForegroundPlatformService.start()
        castSessionForegroundPlatformService.update(
            CastSessionForegroundState(
                snapshot = current.effectiveSnapshot,
                castState = current.castState,
            ),
        )
    }

    private fun resolveNextCastIndex(autoTriggered: Boolean): Int? {
        val snapshot = state.value.effectiveSnapshot
        if (snapshot.queue.isEmpty()) return null
        return when (snapshot.mode) {
            PlaybackMode.REPEAT_ONE -> if (autoTriggered) {
                snapshot.currentIndex.coerceIn(0, snapshot.queue.lastIndex)
            } else {
                nextSequentialIndex(snapshot)
            }
            PlaybackMode.SHUFFLE,
            PlaybackMode.ORDER -> nextSequentialIndex(snapshot)
        }
    }

    private fun resolvePreviousCastIndex(): Int? {
        val snapshot = state.value.effectiveSnapshot
        if (snapshot.queue.isEmpty()) return null
        return when {
            snapshot.mode == PlaybackMode.SHUFFLE -> previousSequentialIndex(snapshot)
            snapshot.currentIndex > 0 -> snapshot.currentIndex - 1
            snapshot.mode == PlaybackMode.ORDER -> snapshot.queue.lastIndex
            else -> 0
        }
    }

    private fun nextSequentialIndex(snapshot: PlaybackSnapshot): Int {
        if (snapshot.currentIndex + 1 <= snapshot.queue.lastIndex) {
            return snapshot.currentIndex + 1
        }
        return 0
    }

    private fun previousSequentialIndex(snapshot: PlaybackSnapshot): Int {
        if (snapshot.currentIndex - 1 >= 0) {
            return snapshot.currentIndex - 1
        }
        return snapshot.queue.lastIndex
    }

    private suspend fun resumeLocalPlaybackAfterCast(
        targetIndex: Int,
        positionMs: Long,
        wasPlayingBeforeCast: Boolean,
    ) {
        val snapshot = playbackRepository.snapshot.value
        if (targetIndex in snapshot.queue.indices && targetIndex != snapshot.currentIndex) {
            playbackRepository.playQueueIndex(targetIndex)
        }
        if (positionMs > 0L) {
            playbackRepository.seekTo(positionMs)
        }
        if (wasPlayingBeforeCast) {
            if (!playbackRepository.snapshot.value.isPlaying) {
                playbackRepository.togglePlayPause()
            }
        } else {
            playbackRepository.pause()
        }
    }

    private fun isRemoteCastActive(): Boolean {
        return state.value.castState.status == CastSessionStatus.Casting
    }

    private fun hasRemoteCastRoute(): Boolean {
        val castState = state.value.castState
        return castState.selectedDeviceId != null &&
            (castState.status == CastSessionStatus.Casting || castState.status == CastSessionStatus.Connecting)
    }

    private suspend fun resolveCastMediaRequest(
        track: Track,
        snapshot: PlaybackSnapshot,
    ): Result<ResolvedCastMediaRequest> {
        val locator = track.mediaLocator.trim()
        val uri = when {
            isDirectCastUri(locator) -> locator
            parseNavidromeSongLocator(locator) != null -> NavidromeLocatorRuntime.resolveStreamUrl(locator)
            else -> null
        }?.takeIf(::isDirectCastUri)

        if (uri != null) {
            return Result.success(
                ResolvedCastMediaRequest(
                    request = buildDirectCastMediaRequest(
                        track = track,
                        uri = uri,
                        durationMs = snapshot.durationMs.takeIf { it > 0L } ?: track.durationMs,
                        artworkUri = resolveCastArtworkUri(snapshot),
                    ),
                    proxySession = null,
                ),
            )
        }

        return castMediaUrlResolver.resolve(track = track, snapshot = snapshot).map { proxySession ->
            ResolvedCastMediaRequest(
                request = buildDirectCastMediaRequest(
                    track = track,
                    uri = proxySession.uri,
                    durationMs = proxySession.durationMs.takeIf { it > 0L }
                        ?: snapshot.durationMs.takeIf { it > 0L }
                        ?: track.durationMs,
                    artworkUri = resolveCastArtworkUri(snapshot),
                    mimeType = proxySession.mimeType,
                ),
                proxySession = proxySession,
            )
        }
    }

    private suspend fun closeCurrentCastProxySession() {
        val session = currentCastProxySession ?: return
        currentCastProxySession = null
        runCatching { session.close() }
    }

    private suspend fun resolveCastArtworkUri(snapshot: PlaybackSnapshot): String? {
        val artworkLocator = snapshot.currentDisplayArtworkLocator?.trim().orEmpty()
        val artworkUri = when {
            isDirectCastUri(artworkLocator) -> artworkLocator
            parseNavidromeCoverLocator(artworkLocator) != null -> NavidromeLocatorRuntime.resolveCoverArtUrl(artworkLocator)
            else -> null
        }
        return directCastUriOrNull(artworkUri)
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

    private suspend fun copyLyricsShareText() {
        val text = buildSelectedLyricsShareText(
            lyrics = state.value.lyrics,
            selectedLineIndices = state.value.selectedLyricsLineIndices,
        ) ?: run {
            updateState { it.copy(shareMessage = "请先选择至少一句歌词") }
            return
        }
        updateState { it.copy(isShareCopying = true, shareMessage = null) }
        val result = lyricsSharePlatformService.copyText(text)
        updateState {
            it.copy(
                isShareCopying = false,
                shareMessage = result.fold(
                    onSuccess = { "文字已复制" },
                    onFailure = { throwable -> "复制文字失败: ${throwable.message.orEmpty()}" },
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
            artworkCacheKey = snapshot.currentTrack?.let(::trackArtworkCacheKey),
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
            val canOverrideCurrentArtwork = state.value.snapshot.currentTrack?.id == track.id
            if (
                !artworkLocator.isNullOrBlank() &&
                canOverrideCurrentArtwork &&
                isLatestLyricsRequest(requestId, track.id, requestKey)
            ) {
                val cacheKey = trackArtworkCacheKey(track)
                val hasAlbumArtworkCache = cacheKey?.let { key ->
                    runCatching { artworkCacheStore.hasCached(key) }.getOrDefault(false)
                } == true
                val hasReplaceablePlaceholderCache =
                    if (hasAlbumArtworkCache && parseNavidromeSongLocator(track.mediaLocator) != null) {
                        runCatching {
                            artworkCacheStore.hasReplaceableNavidromePlaceholderCached(cacheKey.orEmpty())
                        }.getOrDefault(false)
                    } else {
                        false
                    }
                if (hasAlbumArtworkCache && !hasReplaceablePlaceholderCache) {
                    logger.debug(PLAYER_LOG_TAG) {
                        "playback-artwork-override-skip source=auto-lyrics track=${track.id} key=$cacheKey locator=$artworkLocator"
                    }
                } else {
                    logger.debug(PLAYER_LOG_TAG) {
                        "playback-artwork-override source=auto-lyrics track=${track.id} key=${cacheKey.orEmpty()} locator=$artworkLocator"
                    }
                    playbackRepository.overrideCurrentTrackArtwork(artworkLocator)
                }
            }
            updateState { latest ->
                if (!isLatestLyricsRequest(requestId, track.id, requestKey)) {
                    latest
                } else {
                    latest.copy(
                        isLyricsLoading = false,
                        lyrics = lyrics,
                        highlightedLineIndex = findHighlightedLine(lyrics, latest.effectiveSnapshot.positionMs),
                    )
                }
            }
        }
    }

    private fun syncAutomaticLyricsForEffectiveSnapshot() {
        val snapshot = state.value.effectiveSnapshot
        val track = snapshot.currentTrack
        if (track == null) {
            cancelAutomaticLyricsLoad(resetTracking = true)
            updateState { it.copy(isLyricsLoading = false, lyrics = null, highlightedLineIndex = -1) }
            return
        }
        val lookupTrack = snapshot.toLyricsLookupTrack()
        val requestKey = lookupTrack?.lyricsRequestKey()
        if (shouldLoadLyrics(track, requestKey)) {
            launchAutomaticLyricsLoad(
                track = track,
                lookupTrack = lookupTrack ?: track,
                requestKey = requestKey,
            )
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

internal fun buildSelectedLyricsShareText(
    lyrics: LyricsDocument?,
    selectedLineIndices: Set<Int>,
): String? {
    val selectedLines = lyrics?.lines.orEmpty()
        .mapIndexedNotNull { index, line ->
            if (index !in selectedLineIndices) return@mapIndexedNotNull null
            line.text.trim().takeIf { text ->
                text.isNotEmpty() && !isPlayerLyricsStructureTagLine(text)
            }
        }
    return selectedLines.takeIf { it.isNotEmpty() }?.joinToString(separator = "\n")
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

private data class ResolvedCastMediaRequest(
    val request: CastMediaRequest,
    val proxySession: CastProxySession?,
)

private data class CastResumeSession(
    val trackId: String,
    val wasPlayingBeforeCast: Boolean,
)

private fun PlaybackSnapshot.forCastQueueIndex(index: Int): PlaybackSnapshot {
    if (index == currentIndex) return this
    return copy(
        currentIndex = index,
        positionMs = 0L,
        durationMs = queue.getOrNull(index)?.durationMs ?: durationMs,
        metadataTitle = null,
        metadataArtistName = null,
        metadataAlbumTitle = null,
        metadataArtworkLocator = null,
        currentNavidromeAudioQuality = null,
        currentPlaybackAudioFormat = null,
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

private fun PlayerState.matchesCurrentLyricsLookupTrack(): Boolean {
    val lookupTrack = snapshot.toLyricsLookupTrack() ?: return false
    return manualLyricsTitle.trim() == lookupTrack.title.trim() &&
        manualLyricsArtistName.trim() == lookupTrack.artistName.orEmpty().trim() &&
        manualLyricsAlbumTitle.trim() == lookupTrack.albumTitle.orEmpty().trim()
}

private const val PLAYER_LOG_TAG = "Player"
