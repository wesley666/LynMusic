package top.iwesley.lyn.music.feature.player

import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.repository.LyricsLoadResult
import top.iwesley.lyn.music.data.repository.ResolvedLyricsResult
import top.iwesley.lyn.music.core.model.ArtworkWritePolicy
import top.iwesley.lyn.music.core.model.ArtworkCacheResult

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.uiErrorDetail
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.playbackErrorText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.uiErrorText
import top.iwesley.lyn.music.core.model.plus

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator
import top.iwesley.lyn.music.core.model.parseSubsonicCompatibleCoverLocator
import top.iwesley.lyn.music.core.model.parseSubsonicCompatibleSongLocator
import top.iwesley.lyn.music.core.model.parseEmbyCoverLocator
import top.iwesley.lyn.music.core.model.parseEmbySongLocator
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.LyricsRepository
import top.iwesley.lyn.music.data.repository.PlaybackHydrationResult
import top.iwesley.lyn.music.data.repository.PlaybackRepository

const val MIN_SLEEP_TIMER_MINUTES = 1
const val MAX_SLEEP_TIMER_MINUTES = 999
const val SLEEP_TIMER_TICK_MS = 1_000L
private val PLAYBACK_UNSEEKABLE_MESSAGE: UiText get() = uiText(Res.string.player_transcoding_seek_unavailable)

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
    val isLyricsLoading: Boolean = false,
    val hasLyricsLookupCompleted: Boolean = false,
    val lyrics: LyricsDocument? = null,
    val originalLyrics: LyricsDocument? = null,
    val lyricsVersion: String? = null,
    val lyricsTrackId: String? = null,
    val lyricsUserOffsetMs: Long = 0L,
    val lyricsStyle: PlayerLyricsStyle = PlayerLyricsStyle(),
    val availableLyricsFonts: List<LyricsShareFontOption> = emptyList(),
    val isLyricsFontsLoading: Boolean = false,
    val lyricsFontPath: String? = null,
    val highlightedLineIndex: Int = -1,
    val manualLyricsSearchSession: PlayerTaskSession? = null,
    val manualLyricsApplicationRequestId: Long? = null,
    val manualLyricsTitle: String = "",
    val manualLyricsArtistName: String = "",
    val manualLyricsAlbumTitle: String = "",
    val isManualLyricsSearchLoading: Boolean = false,
    val hasManualLyricsSearchResult: Boolean = false,
    val manualLyricsResults: List<LyricsSearchCandidate> = emptyList(),
    val manualWorkflowSongResults: List<WorkflowSongCandidate> = emptyList(),
    val manualLyricsError: UiText? = null,
    val lyricsShareSession: PlayerTaskSession? = null,
    val isLyricsShareInitializing: Boolean = false,
    val lyricsShareInitializationRequestId: Long? = null,
    val selectedLyricsShareTemplate: LyricsShareTemplate = LyricsShareTemplate.NOTE,
    val supportsLyricsShareFontSelection: Boolean = false,
    val availableLyricsShareFonts: List<LyricsShareFontOption> = emptyList(),
    val selectedLyricsShareFontKey: String? = null,
    // Kept across share sessions so late persistence/results cannot reclaim a newer user choice.
    val lyricsShareFontChoiceRevision: Long = 0L,
    val selectedLyricsShareFontDisplayName: String? = null,
    val isLyricsShareFontsLoading: Boolean = false,
    val lyricsShareFontsRequestId: Long? = null,
    val lyricsShareFontsError: UiText? = null,
    val selectedLyricsLineIndices: Set<Int> = emptySet(),
    val shareCardModel: LyricsShareCardModel? = null,
    val sharePreviewBytes: ByteArray? = null,
    val sharePreviewModel: LyricsShareCardModel? = null,
    val sharePreviewSelection: Set<Int> = emptySet(),
    val sharePreviewError: UiText? = null,
    val isShareRendering: Boolean = false,
    val lyricsSharePreviewRequestId: Long? = null,
    // Retained after completion/clearing so an older submission cannot reclaim preview state.
    val lyricsSharePreviewRevision: Long = 0L,
    val isShareSaving: Boolean = false,
    val lyricsShareSaveRequestId: Long? = null,
    val isShareCopying: Boolean = false,
    val lyricsShareCopyRequestId: Long? = null,
    val sleepTimer: SleepTimerState = SleepTimerState(),
    val castState: CastSessionState = CastSessionState(),
    val castQueueIndex: Int? = null,
    val castDiscoverySession: PlayerTaskSession? = null,
    val castMessage: UiText? = null,
    val operationNotices: List<PlayerOperationNotice> = emptyList(),
    val shareMessage: UiText? = null,
    val message: UiText? = null,
) {
    val sharePreviewTemplate: LyricsShareTemplate? get() = sharePreviewModel?.template
    val sharePreviewFontKey: String? get() = sharePreviewModel?.fontKey

    val hasFreshSharePreview: Boolean
        get() = sharePreviewBytes != null && sharePreviewModel != null &&
            sharePreviewModel == lyricsShareModel() &&
            sharePreviewSelection == selectedLyricsLineIndices

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
    data class PlayTransientTracks(val tracks: List<Track>, val startIndex: Int) : PlayerIntent
    data class PlayQueueIndex(val index: Int) : PlayerIntent
    data object TogglePlayPause : PlayerIntent
    data object SkipNext : PlayerIntent
    data object SkipPrevious : PlayerIntent
    data class SeekTo(val positionMs: Long) : PlayerIntent
    data class SetVolume(val value: Float) : PlayerIntent
    data object CycleMode : PlayerIntent
    data class StartSleepTimer(val minutes: Int) : PlayerIntent
    data object CancelSleepTimer : PlayerIntent
    data object BeginCastDiscovery : PlayerIntent
    data object EndCastDiscovery : PlayerIntent
    data object RefreshCastDevices : PlayerIntent
    data class CastToDevice(val deviceId: String) : PlayerIntent
    data object StopCast : PlayerIntent
    data object ClearCastMessage : PlayerIntent
    data object CastNotificationPermissionDenied : PlayerIntent
    data object BeginManualLyricsSearch : PlayerIntent
    data object EndManualLyricsSearch : PlayerIntent
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
    data object RequestLyricsFonts : PlayerIntent
    data class LyricsStyleChanged(val style: PlayerLyricsStyle) : PlayerIntent
    data class LyricsOffsetChanged(val trackId: String, val version: String, val offsetMs: Long) : PlayerIntent
    data class LyricsFontFailed(val fontKey: String) : PlayerIntent
    data object SessionHandled : PlayerIntent
    data object LyricsSettingsHandled : PlayerIntent
    data object BeginLyricsShare : PlayerIntent
    data object EndLyricsShare : PlayerIntent
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
    data class DismissOperationNotice(val id: Long) : PlayerIntent
    data object ClearMessage : PlayerIntent
}

sealed interface PlayerEffect {
    data object TransientPlaybackStarted : PlayerEffect
}

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
    private val playerLyricsStylePreferencesStore: PlayerLyricsStylePreferencesStore = InMemoryPlayerLyricsStylePreferencesStore(),
    private val artworkCacheStore: ArtworkCacheStore = object : ArtworkCacheStore {
        override suspend fun cache(locator: String, cacheKey: String, policy: ArtworkWritePolicy): ArtworkCacheResult? = ArtworkCacheResult(locator, true)
    },
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
) : BaseStore<PlayerState, PlayerIntent, PlayerEffect>(
    initialState = PlayerState(
        lyricsStyle = playerLyricsStylePreferencesStore.playerLyricsStyle.value,
        supportsLyricsShareFontSelection =
            lyricsShareFontPreferencesStore !== UnsupportedLyricsShareFontPreferencesStore,
    ),
    scope = storeScope,
) {
    // Committed writes belong to the Store, not to a cancellable search/share session.
    private val operationSequence = MutableStateFlow(0L)
    private val lyricsApplications = Channel<SubmittedLyricsApplication>(Channel.UNLIMITED)
    private val latestLyricsApplication = MutableStateFlow<SubmittedLyricsApplication?>(null)
    private var nextSessionId = 0L
    private var manualLyricsSessionJob: Job? = null
    private var manualLyricsRequestJob: Job? = null
    private var castDiscoveryJob: Job? = null
    private val castDiscoveryMutex = Mutex()

    private fun newSession() = PlayerTaskSession(++nextSessionId, state.value.effectiveSnapshot.currentTrack?.id)

    private fun endManualLyricsSearch() {
        manualLyricsSessionJob?.cancel()
        manualLyricsSessionJob = null
        manualLyricsRequestJob = null
        dismissManualLyricsSearch()
    }

    private fun beginManualLyricsSearch() {
        lyricsShare.close()
        endManualLyricsSearch()
        if (state.value.effectiveSnapshot.currentTrack == null) return
        manualLyricsSessionJob = SupervisorJob(storeScope.coroutineContext[Job])
        openManualLyricsSearch(newSession())
    }

    private fun beginCastDiscovery() {
        castDiscoveryJob?.cancel()
        val session = newSession()
        updateState { it.copy(castDiscoverySession = session) }
        castDiscoveryJob = storeScope.launch {
            castDiscoveryMutex.withLock {
                if (state.value.castDiscoverySession == session) openCastSheet(session)
            }
        }
    }

    private fun endCastDiscovery() {
        castDiscoveryJob?.cancel()
        updateState { it.copy(castDiscoverySession = null, castMessage = null) }
        storeScope.launch {
            castDiscoveryMutex.withLock {
                if (state.value.castDiscoverySession == null) castGateway.stopDiscovery()
            }
        }
    }

    private var currentLyricsTrackId: String? = null
    private var currentLyricsRequestKey: String? = null
    private var currentLyricsLoadRequestId: Long = 0L
    private var lyricsLoadJob: Job? = null
    private val artworkUpdater = PlaybackArtworkUpdater(storeScope, artworkCacheStore, playbackRepository, logger)
    private var lastPlaybackErrorKey: PlaybackErrorKey? = null
    private var sleepTimerJob: Job? = null
    private var currentCastProxySession: CastProxySession? = null
    private var castResumeSession: CastResumeSession? = null
    private var isStoppingCastExplicitly = false
    private var lastAutoAdvancedEndedTrackId: String? = null

    private val lyricsSettings = PlayerLyricsSettings(
        storeScope, lyricsRepository, playerLyricsStylePreferencesStore, lyricsShareFontLibraryPlatformService,
        state = { state.value }, update = { transform -> updateState(transform) },
    )

    private val lyricsShare = PlayerLyricsShare(
        storeScope, lyricsSharePlatformService, lyricsShareFontLibraryPlatformService,
        lyricsShareFontPreferencesStore, states = state, updateState = ::updateState,
        newSession = ::newSession, closeSearch = ::endManualLyricsSearch,
        refreshLyricsFonts = lyricsSettings::refreshFonts,
        nextOperationId = { operationSequence.updateAndGet { it + 1 } },
    )

    init {
        storeScope.launch {
            for (request in lyricsApplications) {
                try {
                    applySubmittedLyrics(request)
                } catch (error: Throwable) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    reportApplicationFailure(request, error)
                } finally {
                    latestLyricsApplication.compareAndSet(request, null)
                }
            }
        }
        storeScope.launch {
            lyricsRepository.changes.collect { change ->
                val track = state.value.effectiveSnapshot.currentTrack ?: return@collect
                if (change.trackId != null && change.trackId != track.id) return@collect
                if (change.contentChanged) {
                    cancelAutomaticLyricsLoad(resetTracking = true)
                    syncAutomaticLyricsForEffectiveSnapshot()
                } else {
                    lyricsSettings.refreshOffset()
                }
            }
        }
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

    /** Restores business playback only; each host owns its startup presentation policy. */
    suspend fun restorePlayback(): PlayerRestorationResult = storeScope.async {
        val result = playbackRepository.hydratePersistedQueueIfNeeded()
        val snapshot = if (result is PlaybackHydrationResult.Restored || result == PlaybackHydrationResult.ExistingPlayback) {
            playbackRepository.snapshot.first { !it.isHydratingPlayback }
        } else {
            playbackRepository.snapshot.value
        }
        PlayerRestorationResult(result, snapshot)
    }.await()

    override fun reduceStateImmediately(intent: PlayerIntent): PlayerIntent {
        if (lyricsShare.dispatch(intent)) return PlayerIntent.SessionHandled
        return when (intent) {
            PlayerIntent.BeginManualLyricsSearch -> { beginManualLyricsSearch(); PlayerIntent.SessionHandled }
            PlayerIntent.EndManualLyricsSearch -> { endManualLyricsSearch(); PlayerIntent.SessionHandled }
            PlayerIntent.BeginCastDiscovery -> { beginCastDiscovery(); PlayerIntent.SessionHandled }
            PlayerIntent.EndCastDiscovery -> { endCastDiscovery(); PlayerIntent.SessionHandled }
            is PlayerIntent.ManualLyricsTitleChanged, is PlayerIntent.ManualLyricsArtistChanged,
            is PlayerIntent.ManualLyricsAlbumChanged -> {
                manualLyricsRequestJob?.cancel()
                if (state.value.manualLyricsSearchSession != null) {
                    when (intent) {
                        is PlayerIntent.ManualLyricsTitleChanged -> updateManualLyricsForm(title = intent.value)
                        is PlayerIntent.ManualLyricsArtistChanged -> updateManualLyricsForm(artistName = intent.value)
                        is PlayerIntent.ManualLyricsAlbumChanged -> updateManualLyricsForm(albumTitle = intent.value)
                    }
                }
                PlayerIntent.SessionHandled
            }
            is PlayerIntent.ApplyManualLyricsCandidate -> {
                submitLyricsApplication(intent, intent.mode)
                PlayerIntent.SessionHandled
            }
            is PlayerIntent.ApplyWorkflowSongCandidate -> {
                submitLyricsApplication(intent, intent.mode)
                PlayerIntent.SessionHandled
            }
            is PlayerIntent.DismissOperationNotice -> {
                updateState { it.copy(operationNotices = it.operationNotices.filterNot { notice -> notice.id == intent.id }) }
                PlayerIntent.SessionHandled
            }
            PlayerIntent.SearchManualLyrics -> {
                updateState { it.copy(manualLyricsApplicationRequestId = null) }
                manualLyricsRequestJob?.cancel()
                manualLyricsSessionJob?.let { job ->
                    manualLyricsRequestJob = storeScope.launch(job) { handleIntent(intent) }
                }
                PlayerIntent.SessionHandled
            }
            PlayerIntent.RefreshCastDevices -> {
                val session = state.value.castDiscoverySession
                castDiscoveryJob?.cancel()
                castDiscoveryJob = storeScope.launch {
                    castDiscoveryMutex.withLock {
                        if (session != null && state.value.castDiscoverySession == session) refreshCastDevices()
                    }
                }
                PlayerIntent.SessionHandled
            }
            is PlayerIntent.LyricsStyleChanged -> {
                lyricsSettings.changeStyle(intent.style)
                PlayerIntent.LyricsSettingsHandled
            }
            is PlayerIntent.LyricsOffsetChanged -> {
                lyricsSettings.changeOffset(intent.trackId, intent.version, intent.offsetMs)
                PlayerIntent.LyricsSettingsHandled
            }
            else -> intent
        }
    }

    override suspend fun handleIntent(intent: PlayerIntent) {
        when (intent) {
            PlayerIntent.RequestLyricsFonts -> lyricsSettings.refreshFonts()
            PlayerIntent.SessionHandled -> Unit
            is PlayerIntent.LyricsFontFailed -> lyricsSettings.fontFailed(intent.fontKey)
            PlayerIntent.LyricsSettingsHandled, is PlayerIntent.LyricsStyleChanged, is PlayerIntent.LyricsOffsetChanged -> Unit
            is PlayerIntent.PlayTracks -> playTracks(intent.tracks, intent.startIndex)
            is PlayerIntent.PlayTransientTracks -> playTransientTracks(intent.tracks, intent.startIndex)
            is PlayerIntent.PlayQueueIndex -> playQueueIndex(intent.index)
            PlayerIntent.TogglePlayPause -> togglePlayPause()
            PlayerIntent.SkipNext -> skipNext()
            PlayerIntent.SkipPrevious -> skipPrevious()
            is PlayerIntent.SeekTo -> seekTo(intent.positionMs)
            is PlayerIntent.SetVolume -> playbackRepository.setVolume(intent.value)
            PlayerIntent.CycleMode -> playbackRepository.cycleMode()
            is PlayerIntent.StartSleepTimer -> startSleepTimer(intent.minutes)
            PlayerIntent.CancelSleepTimer -> cancelSleepTimer()
            PlayerIntent.BeginCastDiscovery -> Unit // Handled synchronously by the session boundary.
            PlayerIntent.EndCastDiscovery -> Unit // Handled synchronously by the session boundary.
            PlayerIntent.RefreshCastDevices -> refreshCastDevices()
            is PlayerIntent.CastToDevice -> castToDevice(intent.deviceId)
            PlayerIntent.StopCast -> stopCast()
            PlayerIntent.ClearCastMessage -> updateState { it.copy(castMessage = null) }
            PlayerIntent.CastNotificationPermissionDenied -> updateState {
                it.copy(message = uiText(Res.string.cast_notification_permission_disabled))
            }
            PlayerIntent.BeginManualLyricsSearch -> Unit // Handled synchronously by the session boundary.
            PlayerIntent.EndManualLyricsSearch -> Unit // Handled synchronously by the session boundary.
            is PlayerIntent.ManualLyricsTitleChanged -> updateManualLyricsForm(title = intent.value)
            is PlayerIntent.ManualLyricsArtistChanged -> updateManualLyricsForm(artistName = intent.value)
            is PlayerIntent.ManualLyricsAlbumChanged -> updateManualLyricsForm(albumTitle = intent.value)
            PlayerIntent.SearchManualLyrics -> searchManualLyrics()
            is PlayerIntent.ApplyManualLyricsCandidate, is PlayerIntent.ApplyWorkflowSongCandidate -> Unit // Submitted synchronously.
            PlayerIntent.BeginLyricsShare, PlayerIntent.EndLyricsShare,
            is PlayerIntent.LyricsShareTemplateChanged, is PlayerIntent.ToggleLyricsLineSelection,
            PlayerIntent.ClearLyricsSelection, PlayerIntent.RequestLyricsShareFonts,
            PlayerIntent.InvalidateLyricsShareFontCache, is PlayerIntent.LyricsShareFontChanged,
            PlayerIntent.BuildLyricsSharePreview, PlayerIntent.SaveLyricsShareImage,
            PlayerIntent.CopyLyricsShareImage, PlayerIntent.CopyLyricsShareText,
            PlayerIntent.ClearLyricsShareMessage, is PlayerIntent.DismissOperationNotice -> Unit
            PlayerIntent.ClearMessage -> updateState { it.copy(message = null) }
        }
    }

    private fun openManualLyricsSearch(session: PlayerTaskSession) {
        val track = state.value.effectiveSnapshot.currentTrack ?: return
        val lookupTrack = state.value.effectiveSnapshot.toLyricsLookupTrack() ?: track
        updateState {
            it.copy(
                manualLyricsSearchSession = session,
                manualLyricsApplicationRequestId = null,
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
        logger.warn(PLAYER_LOG_TAG) {
            "store-play-tracks size=${tracks.size} startIndex=$startIndex " +
                "target=${tracks.getOrNull(startIndex)?.id.orEmpty()} " +
                "castStatus=${state.value.castState.status} selectedCast=${state.value.castState.selectedDeviceId.orEmpty()}"
        }
        if (!hasRemoteCastRoute()) {
            playbackRepository.playTracks(tracks, startIndex)
            return
        }
        if (tracks.isEmpty()) return
        logger.warn(PLAYER_LOG_TAG) {
            "store-play-tracks-cast-branch pause-local-before-cast " +
                "castStatus=${state.value.castState.status} selectedCast=${state.value.castState.selectedDeviceId.orEmpty()}"
        }
        playbackRepository.pause()
        val preparedSnapshot = playbackRepository.prepareExternalPlaybackQueue(tracks, startIndex) ?: return
        applyPlaybackSnapshot(preparedSnapshot)
        castQueueIndex(preparedSnapshot.currentIndex, pauseLocalAfterCast = false)
    }

    private suspend fun playTransientTracks(tracks: List<Track>, startIndex: Int) {
        logger.warn(PLAYER_LOG_TAG) {
            "store-play-transient-tracks size=${tracks.size} startIndex=$startIndex " +
                "target=${tracks.getOrNull(startIndex)?.id.orEmpty()} " +
                "castStatus=${state.value.castState.status} selectedCast=${state.value.castState.selectedDeviceId.orEmpty()}"
        }
        if (tracks.isEmpty()) return
        if (hasRemoteCastRoute()) {
            stopCastForTransientPlayback()
        }
        playbackRepository.playTransientTracks(tracks, startIndex)
        emitEffect(PlayerEffect.TransientPlaybackStarted)
    }

    private fun applyPlaybackSnapshot(snapshot: PlaybackSnapshot) {
        val previousTrackId = state.value.snapshot.currentTrack?.id
        val trackChanged = previousTrackId != snapshot.currentTrack?.id
        val errorText = snapshot.playbackErrorText()
        val playbackErrorKey = errorText?.let { message ->
            PlaybackErrorKey(trackId = snapshot.currentTrack?.id, message = message)
        }
        val shouldShowPlaybackError = playbackErrorKey != null && playbackErrorKey != lastPlaybackErrorKey
        lastPlaybackErrorKey = playbackErrorKey
        if (trackChanged || snapshot.currentTrack == null) {
            endManualLyricsSearch()
            lyricsShare.close()
        }
        updateState { current ->
            current.copy(
                snapshot = snapshot,
                isLyricsLoading = if (trackChanged || snapshot.currentTrack == null) false else current.isLyricsLoading,
                hasLyricsLookupCompleted = if (trackChanged || snapshot.currentTrack == null) {
                    false
                } else {
                    current.hasLyricsLookupCompleted
                },
                lyrics = if (trackChanged || snapshot.currentTrack == null) null else current.lyrics,
                highlightedLineIndex = findHighlightedLine(
                    lyrics = if (trackChanged || snapshot.currentTrack == null) null else current.lyrics,
                    positionMs = if (current.castState.status == CastSessionStatus.Casting) {
                        current.effectiveSnapshot.positionMs
                    } else {
                        snapshot.positionMs
                    },
                ),
                manualLyricsSearchSession = if (trackChanged) null else current.manualLyricsSearchSession,
                manualLyricsApplicationRequestId = if (trackChanged) null else current.manualLyricsApplicationRequestId,
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
                    shouldShowPlaybackError -> errorText
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
        logger.warn(PLAYER_LOG_TAG) {
            "store-play-queue-index index=$index current=${snapshot.currentIndex} target=${snapshot.queue.getOrNull(index)?.id.orEmpty()} " +
                "snapshotPlaying=${snapshot.isPlaying} castStatus=${state.value.castState.status}"
        }
        if (isRemoteCastActive()) {
            castQueueIndex(index)
            return
        }
        if (index == snapshot.currentIndex) return
        playbackRepository.playQueueIndex(index)
    }

    private suspend fun togglePlayPause() {
        logger.warn(PLAYER_LOG_TAG) {
            "store-toggle-play-pause snapshotPlaying=${state.value.snapshot.isPlaying} " +
                "castStatus=${state.value.castState.status}"
        }
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
                manualLyricsSearchSession = null,
                manualLyricsApplicationRequestId = null,
                isManualLyricsSearchLoading = false,
                hasManualLyricsSearchResult = false,
                manualLyricsResults = emptyList(),
                manualWorkflowSongResults = emptyList(),
                manualLyricsError = null,
                message = null,
            )
        }
    }

    private suspend fun openCastSheet(session: PlayerTaskSession) {
        if (!castGateway.isSupported) {
            updateState {
                it.copy(
                    castDiscoverySession = session,
                    castMessage = uiText(Res.string.cast_platform_unsupported_notice),
                )
            }
            return
        }
        updateState { it.copy(castDiscoverySession = session, castMessage = null) }
        refreshCastDevices()
    }

    private suspend fun refreshCastDevices() {
        if (!castGateway.isSupported) {
            updateState { it.copy(castMessage = uiText(Res.string.cast_platform_unsupported_notice)) }
            return
        }
        castGateway.startDiscovery()
    }

    private suspend fun castToDevice(deviceId: String) {
        val snapshot = state.value.snapshot
        val track = snapshot.currentTrack ?: run {
            updateState { it.copy(castMessage = uiText(Res.string.player_idle_notice)) }
            return
        }
        val resolved = resolveCastMediaRequest(track = track, snapshot = snapshot).getOrElse { error ->
            updateState { it.copy(castMessage = error.uiErrorText(uiText(Res.string.cast_track_unsupported_notice))) }
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
                    castMessage = error.uiErrorText(uiText(Res.string.cast_track_unsupported_notice)),
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

    private suspend fun stopCastForTransientPlayback() {
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
            parseSubsonicCompatibleSongLocator(locator) != null -> NavidromeLocatorRuntime.resolveStreamUrl(locator)
            parseEmbySongLocator(locator) != null -> NavidromeLocatorRuntime.resolveStreamUrl(locator)
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
            parseSubsonicCompatibleCoverLocator(artworkLocator) != null -> NavidromeLocatorRuntime.resolveCoverArtUrl(artworkLocator)
            parseEmbyCoverLocator(artworkLocator) != null -> NavidromeLocatorRuntime.resolveCoverArtUrl(artworkLocator)
            else -> null
        }
        return directCastUriOrNull(artworkUri)
    }

    private fun updateManualLyricsForm(
        title: String? = null,
        artistName: String? = null,
        albumTitle: String? = null,
    ) {
        updateState {
            it.copy(
                manualLyricsApplicationRequestId = null,
                isManualLyricsSearchLoading = false,
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
        val currentTrack = current.effectiveSnapshot.currentTrack ?: return
        val title = current.manualLyricsTitle.trim()
        if (title.isBlank()) {
            updateState {
                it.copy(
                    isManualLyricsSearchLoading = false,
                    hasManualLyricsSearchResult = false,
                    manualLyricsResults = emptyList(),
                    manualWorkflowSongResults = emptyList(),
                    manualLyricsError = uiText(Res.string.track_title_required),
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
        val session = current.manualLyricsSearchSession ?: return
        val directResult = runCatching {
            lyricsRepository.searchLyricsCandidates(
                track = searchTrack,
                includeTrackProvidedCandidate = includeTrackProvidedCandidate,
            )
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val workflowResult = runCatching { lyricsRepository.searchWorkflowSongCandidates(searchTrack) }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (state.value.manualLyricsSearchSession != session) return
        updateState { latest ->
            latest.copy(
                isManualLyricsSearchLoading = false,
                hasManualLyricsSearchResult = true,
                manualLyricsResults = directResult.getOrDefault(emptyList()),
                manualWorkflowSongResults = workflowResult.getOrDefault(emptyList()),
                manualLyricsError = directResult.exceptionOrNull()?.uiErrorText() ?: workflowResult.exceptionOrNull()?.uiErrorText(),
                message = null,
            )
        }
    }

    private fun submitLyricsApplication(intent: PlayerIntent, mode: LyricsSearchApplyMode) {
        val current = state.value
        val session = current.manualLyricsSearchSession ?: return
        val track = current.effectiveSnapshot.currentTrack ?: return
        if (session.trackId != track.id) return
        val previous = latestLyricsApplication.value
        if (previous?.session == session && previous.intent == intent &&
            current.manualLyricsApplicationRequestId == previous.id
        ) return
        val searchJob = manualLyricsSessionJob ?: return
        val request = SubmittedLyricsApplication(operationSequence.updateAndGet { it + 1 }, session, track, intent, mode, searchJob)
        manualLyricsRequestJob?.cancel() // Only the lookup; earlier committed applications keep running.
        if (mode != LyricsSearchApplyMode.LYRICS_ONLY) artworkUpdater.cancel()
        latestLyricsApplication.value = request
        // Submission must change the same state that completion CAS checks. An external "latest"
        // pointer alone cannot invalidate an older transform already waiting to publish its result.
        updateState { latest ->
            if (latest.manualLyricsSearchSession == session && latest.effectiveSnapshot.currentTrack?.id == track.id) {
                latest.copy(manualLyricsApplicationRequestId = request.id)
            } else latest
        }
        lyricsApplications.trySend(request)
    }

    private fun isCurrentApplication(request: SubmittedLyricsApplication, current: PlayerState = state.value): Boolean =
        current.manualLyricsApplicationRequestId == request.id &&
            current.effectiveSnapshot.currentTrack?.id == request.track.id &&
            current.manualLyricsSearchSession == request.session

    private suspend fun applySubmittedLyrics(request: SubmittedLyricsApplication) {
        val result = try {
            when (val intent = request.intent) {
                is PlayerIntent.ApplyManualLyricsCandidate ->
                    lyricsRepository.applyLyricsCandidate(request.track.id, intent.candidate, request.mode)
                is PlayerIntent.ApplyWorkflowSongCandidate ->
                    lyricsRepository.applyWorkflowSongCandidate(request.track.id, intent.candidate, request.mode)
                else -> error("Unsupported lyrics application")
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            reportApplicationFailure(request, error)
            return
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (result.document != null && isCurrentApplication(request)) {
            lyricsSettings.accept(request.track, ResolvedLyricsResult(
                requireNotNull(result.document), result.artworkLocator, requireNotNull(result.version),
            )) { current -> isCurrentApplication(request, current) }
        }
        // Committed artwork belongs to playback, not to the search dialog. The single application
        // worker preserves write/publication order, and the repository rechecks the original track.
        if (state.value.effectiveSnapshot.currentTrack?.id == request.track.id &&
            request.mode != LyricsSearchApplyMode.LYRICS_ONLY && !result.artworkLocator.isNullOrBlank()
        ) {
            artworkUpdater.cancel()
            playbackRepository.overrideCurrentTrackArtwork(result.artworkLocator, expectedTrackId = request.track.id)
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        updateState { current ->
            if (!isCurrentApplication(request, current)) {
                current.withOperationNotice(request.id, request.track, uiText(Res.string.lyrics_apply_completed))
            } else current.copy(
                manualLyricsSearchSession = null,
                manualLyricsApplicationRequestId = null,
                isManualLyricsSearchLoading = false,
                hasManualLyricsSearchResult = false,
                manualLyricsResults = emptyList(),
                manualWorkflowSongResults = emptyList(),
                manualLyricsError = null,
                message = null,
            )
        }
        // Cancel only the captured lookup job, never a newer session installed while publishing.
        if (state.value.manualLyricsSearchSession != request.session) request.searchJob.cancel()
    }

    private fun reportApplicationFailure(request: SubmittedLyricsApplication, error: Throwable) {
        val message = error.uiErrorText(applyFailureMessage(request.mode))
        updateState { current ->
            if (isCurrentApplication(request, current)) current.copy(manualLyricsApplicationRequestId = null, message = message)
            else current.withOperationNotice(request.id, request.track, message)
        }
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
        val artworkGeneration = artworkUpdater.generation
        val previousJob = lyricsLoadJob
        updateState {
            it.copy(
                isLyricsLoading = true,
                hasLyricsLookupCompleted = false,
                lyrics = null,
                originalLyrics = null,
                lyricsVersion = null,
                lyricsTrackId = null,
                lyricsUserOffsetMs = 0L,
                highlightedLineIndex = -1,
            )
        }
        lyricsLoadJob = storeScope.launch {
            previousJob?.cancelAndJoin()
            if (!isLatestLyricsRequest(requestId, track.id, requestKey)) return@launch
            val outcome = lyricsRepository.loadPlaybackLyrics(lookupTrack)
            if (!isActive || !isLatestLyricsRequest(requestId, track.id, requestKey)) return@launch
            val result = (outcome as? LyricsLoadResult.Found)?.lyrics
            if (result != null) {
                lyricsSettings.accept(track, result) { isLatestLyricsRequest(requestId, track.id, requestKey) }
            } else {
                updateState { it.copy(isLyricsLoading = false, hasLyricsLookupCompleted = true) }
            }
            artworkUpdater.submit(track, result?.artworkLocator, requestId) {
                artworkUpdater.generation == artworkGeneration &&
                isLatestLyricsRequest(requestId, track.id, requestKey) &&
                    state.value.snapshot.currentTrack?.id == track.id
            }
        }
    }

    private fun syncAutomaticLyricsForEffectiveSnapshot() {
        val snapshot = state.value.effectiveSnapshot
        val track = snapshot.currentTrack
        state.value.manualLyricsSearchSession?.let { if (it.trackId != track?.id) endManualLyricsSearch() }
        state.value.lyricsShareSession?.let { if (it.trackId != track?.id) lyricsShare.close() }
        if (track == null) {
            cancelAutomaticLyricsLoad(resetTracking = true)
            updateState {
                it.copy(
                    isLyricsLoading = false,
                    hasLyricsLookupCompleted = false,
                    lyrics = null,
                    highlightedLineIndex = -1,
                )
            }
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
        artworkUpdater.cancel()
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
        artworkUpdater.cancel()
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

    private fun applyFailureMessage(mode: LyricsSearchApplyMode): UiText {
        return if (mode == LyricsSearchApplyMode.ARTWORK_ONLY) {
            uiText(Res.string.lyrics_apply_artwork_failed)
        } else {
            uiText(Res.string.lyrics_apply_lyrics_failed)
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

private data class PlaybackErrorKey(
    val trackId: String?,
    val message: UiText,
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
    val lookupTrack = effectiveSnapshot.toLyricsLookupTrack() ?: return false
    return manualLyricsTitle.trim() == lookupTrack.title.trim() &&
        manualLyricsArtistName.trim() == lookupTrack.artistName.orEmpty().trim() &&
        manualLyricsAlbumTitle.trim() == lookupTrack.albumTitle.orEmpty().trim()
}

internal const val PLAYER_LOG_TAG = "Player"


private data class SubmittedLyricsApplication(
    val id: Long,
    val session: PlayerTaskSession,
    val track: Track,
    val intent: PlayerIntent,
    val mode: LyricsSearchApplyMode,
    val searchJob: Job,
)

internal fun PlayerState.withOperationNotice(id: Long, track: Track, message: UiText): PlayerState =
    copy(operationNotices = operationNotices + PlayerOperationNotice(id, UiText.Raw(track.title + "\n") + message))
