package top.iwesley.lyn.music.data.repository

import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.playbackLoadFailureText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlin.time.Clock
import top.iwesley.lyn.music.cast.CastBackgroundRunSettingsOpener
import top.iwesley.lyn.music.cast.CastGateway
import top.iwesley.lyn.music.cast.CastMediaUrlResolver
import top.iwesley.lyn.music.cast.CastNotificationPermissionRequester
import top.iwesley.lyn.music.cast.CastSessionForegroundPlatformService
import top.iwesley.lyn.music.cast.UnsupportedCastBackgroundRunSettingsOpener
import top.iwesley.lyn.music.cast.UnsupportedCastGateway
import top.iwesley.lyn.music.cast.UnsupportedCastMediaUrlResolver
import top.iwesley.lyn.music.cast.UnsupportedCastNotificationPermissionRequester
import top.iwesley.lyn.music.cast.UnsupportedCastSessionForegroundPlatformService
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.EqualizerPlatformService
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.core.model.LyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.LyricsSharePlatformService
import top.iwesley.lyn.music.core.model.LyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.MenuBarLyricsControlsPlatformService
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.NoopPlaybackStatsReporter
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.core.model.PlaybackLoadToken
import top.iwesley.lyn.music.core.model.PlaybackMode
import top.iwesley.lyn.music.core.model.PlaybackPreferencesStore
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.PlaybackStatsReporter
import top.iwesley.lyn.music.core.model.SystemPlaybackControlCallbacks
import top.iwesley.lyn.music.core.model.SystemPlaybackControlsPlatformService
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.UnsupportedEqualizerPlatformService
import top.iwesley.lyn.music.core.model.UnsupportedLyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.UnsupportedLyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedLyricsSharePlatformService
import top.iwesley.lyn.music.core.model.UnsupportedMenuBarLyricsControlsPlatformService
import top.iwesley.lyn.music.core.model.UnsupportedSystemPlaybackControlsPlatformService
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.normalizePlaybackVolume
import top.iwesley.lyn.music.core.model.parseEmbySongLocator
import top.iwesley.lyn.music.core.model.parseFnMusicSongLocator
import top.iwesley.lyn.music.core.model.parseSubsonicCompatibleSongLocator
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.PlaybackQueueSnapshotEntity

interface PlaybackRepository {
    val snapshot: StateFlow<PlaybackSnapshot>

    suspend fun hydratePersistedQueueIfNeeded(): PlaybackHydrationResult
    suspend fun playTracks(tracks: List<Track>, startIndex: Int)
    suspend fun playTransientTracks(tracks: List<Track>, startIndex: Int)
    suspend fun prepareExternalPlaybackQueue(tracks: List<Track>, startIndex: Int): PlaybackSnapshot?
    suspend fun playQueueIndex(index: Int)
    suspend fun togglePlayPause()
    suspend fun pause()
    suspend fun skipNext()
    suspend fun skipPrevious()
    suspend fun seekTo(positionMs: Long)
    suspend fun setVolume(volume: Float)
    suspend fun cycleMode()
    suspend fun overrideCurrentTrackArtwork(artworkLocator: String?, expectedTrackId: String? = null)
    suspend fun close()
}

sealed interface PlaybackHydrationResult {
    data class Restored(
        val loadToken: PlaybackLoadToken,
    ) : PlaybackHydrationResult

    data object ExistingPlayback : PlaybackHydrationResult
    data object Empty : PlaybackHydrationResult
    data object SupersededByPlayback : PlaybackHydrationResult
    data object Failed : PlaybackHydrationResult
}

class DefaultPlaybackRepository(
    private val database: LynMusicDatabase,
    private val gateway: PlaybackGateway,
    private val playbackPreferencesStore: PlaybackPreferencesStore,
    private val scope: CoroutineScope,
    private val systemPlaybackControlsPlatformService: SystemPlaybackControlsPlatformService = UnsupportedSystemPlaybackControlsPlatformService,
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
    private val shuffleRandom: Random = Random.Default,
    hydrateImmediately: Boolean = true,
    private val playbackStatsReporter: PlaybackStatsReporter = NoopPlaybackStatsReporter,
    private val currentTimeMillis: () -> Long = ::now,
) : PlaybackRepository {
    private val initialPlaybackVolume = normalizePlaybackVolume(playbackPreferencesStore.playbackVolume.value)
    private val mutableSnapshot = MutableStateFlow(
        PlaybackSnapshot(
            isHydratingPlayback = true,
            volume = initialPlaybackVolume,
        ),
    )
    private val playbackCommandMutex = Mutex()
    private val persistedQueueHydrationMutex = Mutex()
    private val closeMutex = Mutex()
    private val lifecycleJobs = mutableListOf<Job>()
    private val playbackStatsScope = CoroutineScope(
        scope.coroutineContext.minusKey(Job) + SupervisorJob(),
    )
    private val playbackStatsCommands = Channel<PlaybackStatsCommand>(Channel.UNLIMITED)
    private val playbackStatsJob by lazy {
        playbackStatsScope.launch {
            for (command in playbackStatsCommands) {
                reportPlaybackStatsCommand(command)
            }
        }
    }
    @Volatile
    private var latestLoadRequestId = 0L
    private var persistedQueueHydrationResult: PlaybackHydrationResult? = null
    @Volatile
    private var currentQueueIsTransient = false
    @Volatile
    private var hasClosed = false
    private var observedCompletionCount = 0L
    private var playbackStatsSession: PlaybackStatsSession? = null
    private var loggedArtworkTrackId: String? = null
    private var loggedDisplayArtworkLocator: String? = null
    private var lastGatewayStateLogKey: String? = null
    private var ignoredGatewayErrorRevision: Long? = null
    private var ignoredGatewayErrorMessage: String? = null
    private var ignoredGatewayErrorText: UiText? = null

    override val snapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot.asStateFlow()

    init {
        systemPlaybackControlsPlatformService.bind(
            SystemPlaybackControlCallbacks(
                play = { playCurrentTrack() },
                pause = { pauseCurrentTrack() },
                togglePlayPause = { togglePlayPause() },
                skipNext = { skipNext() },
                skipPrevious = { skipPrevious() },
                seekTo = { positionMs -> seekTo(positionMs) },
            ),
        )
        runBlocking {
            gateway.setVolume(initialPlaybackVolume)
        }
        if (hydrateImmediately) {
            runBlocking {
                hydratePersistedQueueIfNeeded()
            }
        }
        launchLifecycleJob {
            combine(
                database.trackDao().observeAll(),
                database.lyricsCacheDao().observeArtworkLocators(),
            ) { entities, artworkRows ->
                val artworkOverrides = effectiveArtworkOverridesByTrackId(artworkRows)
                entities.associate { entity ->
                    entity.id to entity.toDomain(artworkOverrides[entity.id])
                }
            }.collect { tracksById ->
                var snapshotChanged = false
                var currentTrackChanged = false
                var currentArtworkChanged = false
                mutableSnapshot.update { snapshot ->
                    if (snapshot.queue.isEmpty()) return@update snapshot
                    var queueChanged = false
                    val updatedQueue = snapshot.queue.mapIndexed { index, track ->
                        val updated = tracksById[track.id] ?: track
                        if (updated != track) {
                            queueChanged = true
                            if (index == snapshot.currentIndex) {
                                currentTrackChanged = true
                                if (updated.artworkLocator != track.artworkLocator) {
                                    currentArtworkChanged = true
                                }
                            }
                        }
                        updated
                    }
                    val orderedQueue = snapshot.orderedQueue.ifEmpty { snapshot.queue }
                    var orderedQueueChanged = false
                    val updatedOrderedQueue = orderedQueue.map { track ->
                        val updated = tracksById[track.id] ?: track
                        if (updated != track) {
                            orderedQueueChanged = true
                        }
                        updated
                    }
                    snapshotChanged = queueChanged || orderedQueueChanged
                    if (!snapshotChanged) return@update snapshot
                    snapshot.copy(
                        queue = updatedQueue,
                        orderedQueue = updatedOrderedQueue,
                        metadataTitle = if (currentTrackChanged) null else snapshot.metadataTitle,
                        metadataArtistName = if (currentTrackChanged) null else snapshot.metadataArtistName,
                        metadataAlbumTitle = if (currentTrackChanged) null else snapshot.metadataAlbumTitle,
                        metadataArtworkLocator = if (currentArtworkChanged) null else snapshot.metadataArtworkLocator,
                    )
                }
            }
        }
        launchLifecycleJob {
            gateway.state.collect { gatewayState ->
                val beforeSnapshot = mutableSnapshot.value
                val gatewayStateLogKey = listOf(
                    beforeSnapshot.currentTrack?.id.orEmpty(),
                    gatewayState.isPlaying,
                    gatewayState.canSeek,
                    gatewayState.completionCount,
                    gatewayState.errorMessage.orEmpty(),
                ).joinToString("|")
                if (gatewayStateLogKey != lastGatewayStateLogKey) {
                    lastGatewayStateLogKey = gatewayStateLogKey
                    logger.debug(PLAYBACK_LOG_TAG) {
                        "repository-gateway-state track=${beforeSnapshot.currentTrack?.id.orEmpty()} " +
                            "snapshotPlaying=${beforeSnapshot.isPlaying} gatewayPlaying=${gatewayState.isPlaying} " +
                            "position=${gatewayState.positionMs} duration=${gatewayState.durationMs} " +
                            "canSeek=${gatewayState.canSeek} completion=${gatewayState.completionCount} " +
                            "error=${gatewayState.errorMessage.orEmpty()}"
                    }
                }
                val completionChanged = gatewayState.completionCount > observedCompletionCount
                observedCompletionCount = gatewayState.completionCount
                val gatewayError = resolveGatewayError(gatewayState)
                mutableSnapshot.update {
                    it.copy(
                        isPlaying = gatewayState.isPlaying,
                        positionMs = gatewayState.positionMs,
                        durationMs = resolvePlaybackDurationMs(
                            gatewayDurationMs = gatewayState.durationMs,
                            currentTrack = it.currentTrack,
                            currentSnapshotDurationMs = it.durationMs,
                        ),
                        canSeek = gatewayState.canSeek,
                        volume = gatewayState.volume,
                        metadataTitle = gatewayState.metadataTitle,
                        metadataArtistName = gatewayState.metadataArtistName,
                        metadataAlbumTitle = gatewayState.metadataAlbumTitle,
                        metadataArtworkLocator = it.metadataArtworkLocator,
                        currentNavidromeAudioQuality = gatewayState.currentNavidromeAudioQuality,
                        currentPlaybackAudioFormat = gatewayState.currentPlaybackAudioFormat,
                        errorMessage = when (gatewayError) {
                            GatewayErrorResolution.Ignore -> it.errorMessage
                            is GatewayErrorResolution.Apply -> gatewayError.message
                        },
                        errorText = when (gatewayError) {
                            GatewayErrorResolution.Ignore -> it.errorText
                            is GatewayErrorResolution.Apply -> gatewayError.text ?: it.errorText.takeIf { _ ->
                                gatewayError.message != null && gatewayError.message == it.errorMessage
                            }
                        },
                    )
                }
                updatePlaybackStats(mutableSnapshot.value)
                val completionLoadRequest = if (completionChanged) {
                    playbackCommandMutex.withLock {
                        advanceLocked(autoTriggered = true)
                    }
                } else {
                    null
                }
                completionLoadRequest?.let {
                    loadGatewaySafely(it)
                    persistSnapshotCursorIfPersistent()
                }
            }
        }
        launchLifecycleJob {
            snapshot.collect { snapshot ->
                logDisplayArtwork(snapshot)
                systemPlaybackControlsPlatformService.updateSnapshot(snapshot)
            }
        }
    }

    override suspend fun hydratePersistedQueueIfNeeded(): PlaybackHydrationResult =
        persistedQueueHydrationMutex.withLock {
            val hasExistingPlayback = playbackCommandMutex.withLock {
                val hasCurrentTrack = mutableSnapshot.value.currentTrack != null
                if (hasCurrentTrack) {
                    mutableSnapshot.update { it.copy(isHydratingPlayback = false) }
                }
                hasCurrentTrack
            }
            if (hasExistingPlayback) {
                return@withLock PlaybackHydrationResult.ExistingPlayback
            }
            persistedQueueHydrationResult?.let { cachedResult ->
                val validatedResult = cachedResult.validateCurrentRestore()
                persistedQueueHydrationResult = validatedResult
                return@withLock validatedResult
            }
            val result = try {
                restoreQueueAsync()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                mutableSnapshot.update { it.copy(isHydratingPlayback = false) }
                logger.error(PLAYBACK_LOG_TAG, throwable) {
                    "hydrate-failed"
                }
                PlaybackHydrationResult.Failed
            }
            persistedQueueHydrationResult = result
            result
        }

    override suspend fun playTracks(tracks: List<Track>, startIndex: Int) {
        var loadRequest: PlaybackLoadRequest? = null
        playbackCommandMutex.withLock {
            if (tracks.isEmpty()) return@withLock
            logger.warn(PLAYBACK_LOG_TAG) {
                "repository-play-tracks size=${tracks.size} startIndex=$startIndex " +
                    "target=${tracks.getOrNull(startIndex)?.id.orEmpty()} current=${mutableSnapshot.value.currentTrack?.id.orEmpty()} " +
                    "snapshotPlaying=${mutableSnapshot.value.isPlaying}"
            }
            updatePlaybackStats(mutableSnapshot.value)
            val currentSnapshot = mutableSnapshot.value
            currentQueueIsTransient = false
            ignoreCurrentGatewayErrorForNextTrack()
            val nextSnapshot = buildQueueSnapshot(
                tracks = tracks,
                startIndex = startIndex,
                currentSnapshot = currentSnapshot,
                isPlaying = true,
            )
            val target = nextSnapshot.currentTrack ?: return@withLock
            mutableSnapshot.value = nextSnapshot
            loadRequest = createLoadRequest(
                track = target,
                playWhenReady = true,
                startPositionMs = 0L,
            )
        }
        loadRequest?.let {
            loadGatewaySafely(it)
            persistSnapshotContent()
        }
    }

    override suspend fun playTransientTracks(tracks: List<Track>, startIndex: Int) {
        var loadRequest: PlaybackLoadRequest? = null
        playbackCommandMutex.withLock {
            if (tracks.isEmpty()) return@withLock
            logger.warn(PLAYBACK_LOG_TAG) {
                "repository-play-transient-tracks size=${tracks.size} startIndex=$startIndex " +
                    "target=${tracks.getOrNull(startIndex)?.id.orEmpty()} current=${mutableSnapshot.value.currentTrack?.id.orEmpty()} " +
                    "snapshotPlaying=${mutableSnapshot.value.isPlaying}"
            }
            updatePlaybackStats(mutableSnapshot.value)
            val currentSnapshot = mutableSnapshot.value
            currentQueueIsTransient = true
            ignoreCurrentGatewayErrorForNextTrack()
            val nextSnapshot = buildQueueSnapshot(
                tracks = tracks,
                startIndex = startIndex,
                currentSnapshot = currentSnapshot,
                isPlaying = true,
            )
            val target = nextSnapshot.currentTrack ?: return@withLock
            mutableSnapshot.value = nextSnapshot
            loadRequest = createLoadRequest(
                track = target,
                playWhenReady = true,
                startPositionMs = 0L,
            )
        }
        loadRequest?.let {
            loadGatewaySafely(it)
        }
    }

    override suspend fun prepareExternalPlaybackQueue(tracks: List<Track>, startIndex: Int): PlaybackSnapshot? {
        var preparedSnapshot: PlaybackSnapshot? = null
        playbackCommandMutex.withLock {
            if (tracks.isEmpty()) return@withLock
            updatePlaybackStats(mutableSnapshot.value)
            val currentSnapshot = mutableSnapshot.value
            currentQueueIsTransient = false
            latestLoadRequestId += 1L
            ignoreCurrentGatewayErrorForNextTrack()
            val nextSnapshot = buildQueueSnapshot(
                tracks = tracks,
                startIndex = startIndex,
                currentSnapshot = currentSnapshot,
                isPlaying = false,
            )
            preparedSnapshot = nextSnapshot
            mutableSnapshot.value = nextSnapshot
        }
        if (preparedSnapshot != null) {
            persistSnapshotContent()
        }
        return preparedSnapshot
    }

    override suspend fun playQueueIndex(index: Int) {
        val loadRequest = playbackCommandMutex.withLock {
            logger.warn(PLAYBACK_LOG_TAG) {
                "repository-play-queue-index index=$index current=${mutableSnapshot.value.currentIndex} " +
                    "target=${mutableSnapshot.value.queue.getOrNull(index)?.id.orEmpty()} " +
                    "snapshotPlaying=${mutableSnapshot.value.isPlaying}"
            }
            loadIndexLocked(index, playWhenReady = true)
        }
        loadRequest?.let {
            loadGatewaySafely(it)
            persistSnapshotCursorIfPersistent()
        }
    }

    override suspend fun togglePlayPause() {
        playbackCommandMutex.withLock {
            if (mutableSnapshot.value.currentTrack == null) return
            logger.warn(PLAYBACK_LOG_TAG) {
                "repository-toggle-play-pause track=${mutableSnapshot.value.currentTrack?.id.orEmpty()} " +
                    "snapshotPlaying=${mutableSnapshot.value.isPlaying}"
            }
            if (mutableSnapshot.value.isPlaying) {
                gateway.pause()
            } else {
                gateway.play()
            }
        }
    }

    override suspend fun pause() {
        pauseCurrentTrack()
    }

    override suspend fun skipNext() {
        val loadRequest = playbackCommandMutex.withLock {
            advanceLocked(autoTriggered = false)
        }
        loadRequest?.let {
            loadGatewaySafely(it)
            persistSnapshotCursorIfPersistent()
        }
    }

    override suspend fun skipPrevious() {
        var loadRequest: PlaybackLoadRequest? = null
        playbackCommandMutex.withLock {
            val snapshot = mutableSnapshot.value
            if (snapshot.queue.isEmpty()) return@withLock
            if (snapshot.mode != PlaybackMode.REPEAT_ONE && snapshot.canSeek && snapshot.positionMs > 5_000) {
                gateway.seekTo(0L)
                mutableSnapshot.update { it.copy(positionMs = 0L) }
                persistSnapshotCursorIfPersistent()
                return@withLock
            }
            val previousIndex = when {
                snapshot.mode == PlaybackMode.SHUFFLE -> previousSequentialIndex(snapshot)
                snapshot.currentIndex > 0 -> snapshot.currentIndex - 1
                snapshot.mode == PlaybackMode.ORDER -> snapshot.queue.lastIndex
                else -> 0
            }
            loadRequest = loadIndexLocked(previousIndex, playWhenReady = true)
        }
        loadRequest?.let {
            loadGatewaySafely(it)
            persistSnapshotCursorIfPersistent()
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        playbackCommandMutex.withLock {
            if (!mutableSnapshot.value.canSeek) return@withLock
            gateway.seekTo(positionMs)
            mutableSnapshot.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
            persistSnapshotCursorIfPersistent()
        }
    }

    override suspend fun setVolume(volume: Float) {
        val normalized = normalizePlaybackVolume(volume)
        playbackCommandMutex.withLock {
            gateway.setVolume(normalized)
            mutableSnapshot.update { it.copy(volume = normalized) }
            playbackPreferencesStore.setPlaybackVolume(normalized)
        }
    }

    override suspend fun cycleMode() {
        playbackCommandMutex.withLock {
            val snapshot = mutableSnapshot.value
            val nextSnapshot = when (snapshot.mode) {
                PlaybackMode.ORDER -> snapshot.toShuffleSnapshot()
                PlaybackMode.SHUFFLE -> snapshot.copy(mode = PlaybackMode.REPEAT_ONE)
                PlaybackMode.REPEAT_ONE -> snapshot.toOrderSnapshot()
            }
            mutableSnapshot.value = nextSnapshot
            persistSnapshotContentIfPersistent()
        }
    }

    override suspend fun overrideCurrentTrackArtwork(artworkLocator: String?, expectedTrackId: String?) {
        playbackCommandMutex.withLock {
            val snapshot = mutableSnapshot.value
            val track = snapshot.currentTrack ?: return
            if (expectedTrackId != null && track.id != expectedTrackId) return
            val resolvedArtworkLocator = artworkLocator?.takeIf { it.isNotBlank() } ?: return
            mutableSnapshot.update {
                it.copy(
                    metadataArtworkLocator = resolvedArtworkLocator,
                )
            }
        }
    }

    override suspend fun close() {
        closeMutex.withLock {
            if (hasClosed) return@withLock
            hasClosed = true
            val failures = mutableListOf<Throwable>()
            suspend fun closeStep(name: String, block: suspend () -> Unit) {
                runCatching { block() }.onFailure { error ->
                    failures += error
                    runCatching {
                        logger.error(PLAYBACK_LOG_TAG, error) { "repository-close-failed step=$name" }
                    }.exceptionOrNull()?.let { loggingError -> error.addSuppressedSafely(loggingError) }
                }
            }
            closeStep("playback-stats-enqueue") { updatePlaybackStats(mutableSnapshot.value) }
            closeStep("playback-stats-flush") {
                playbackStatsCommands.close()
                try {
                    withTimeout(PLAYBACK_STATS_CLOSE_TIMEOUT_MS) {
                        playbackStatsJob.join()
                    }
                } finally {
                    playbackStatsScope.cancel()
                }
            }
            lifecycleJobs.forEachIndexed { index, job ->
                closeStep("lifecycle-job-$index") { job.cancelAndJoin() }
            }
            closeStep("system-controls") { systemPlaybackControlsPlatformService.close() }
            closeStep("gateway") { gateway.release() }
            failures.firstOrNull()?.let { primary ->
                failures.drop(1).forEach { failure -> primary.addSuppressedSafely(failure) }
                throw primary
            }
        }
    }

    private fun launchLifecycleJob(block: suspend CoroutineScope.() -> Unit) {
        lifecycleJobs += scope.launch(block = block)
    }

    private suspend fun playCurrentTrack() {
        playbackCommandMutex.withLock {
            if (mutableSnapshot.value.currentTrack == null) return
            gateway.play()
        }
    }

    private suspend fun pauseCurrentTrack() {
        playbackCommandMutex.withLock {
            if (mutableSnapshot.value.currentTrack == null) return
            logger.warn(PLAYBACK_LOG_TAG) {
                "repository-pause-current track=${mutableSnapshot.value.currentTrack?.id.orEmpty()} " +
                    "snapshotPlaying=${mutableSnapshot.value.isPlaying} position=${mutableSnapshot.value.positionMs}"
            }
            gateway.pause()
        }
    }

    private suspend fun advanceLocked(autoTriggered: Boolean): PlaybackLoadRequest? {
        val snapshot = mutableSnapshot.value
        if (snapshot.queue.isEmpty()) return null
        val nextIndex = when (snapshot.mode) {
            PlaybackMode.REPEAT_ONE -> {
                if (autoTriggered) {
                    snapshot.currentIndex
                } else {
                    nextSequentialIndex(snapshot)
                }
            }
            PlaybackMode.SHUFFLE -> nextSequentialIndex(snapshot)
            PlaybackMode.ORDER -> nextSequentialIndex(snapshot)
        }
        return loadIndexLocked(nextIndex, playWhenReady = true)
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

    private suspend fun loadIndexLocked(index: Int, playWhenReady: Boolean): PlaybackLoadRequest? {
        val queue = mutableSnapshot.value.queue
        val target = queue.getOrNull(index) ?: return null
        updatePlaybackStats(mutableSnapshot.value)
        ignoreCurrentGatewayErrorForNextTrack()
        mutableSnapshot.update {
            it.copy(
                currentIndex = index,
                isHydratingPlayback = false,
                isPlaying = playWhenReady,
                positionMs = 0L,
                durationMs = target.durationMs,
                canSeek = false,
                metadataTitle = null,
                metadataArtistName = null,
                metadataAlbumTitle = null,
                metadataArtworkLocator = null,
                errorMessage = null,
                errorText = null,
            )
        }
        return createLoadRequest(
            track = target,
            playWhenReady = playWhenReady,
            startPositionMs = 0L,
        )
    }

    private suspend fun restoreQueueAsync(): PlaybackHydrationResult {
        val shouldHydrate = playbackCommandMutex.withLock {
            if (mutableSnapshot.value.queue.isNotEmpty()) {
                mutableSnapshot.update { it.copy(isHydratingPlayback = false) }
                false
            } else {
                true
            }
        }
        if (!shouldHydrate) return PlaybackHydrationResult.SupersededByPlayback

        val persisted = database.playbackQueueSnapshotDao().get()
        if (persisted == null) {
            mutableSnapshot.update { it.copy(isHydratingPlayback = false) }
            return PlaybackHydrationResult.Empty
        }
        val queueIds = persisted.queueTrackIds.split(',').filter { it.isNotBlank() }
        if (queueIds.isEmpty()) {
            mutableSnapshot.update { it.copy(isHydratingPlayback = false) }
            return PlaybackHydrationResult.Empty
        }
        val orderedQueueIds = persisted.orderedQueueTrackIds
            .split(',')
            .filter { it.isNotBlank() }
            .ifEmpty { queueIds }
        val allIds = (queueIds + orderedQueueIds).distinct()
        val artworkOverrides = effectiveArtworkOverridesByTrackId(
            database.lyricsCacheDao().getArtworkLocatorsByTrackIds(allIds),
        )
        val tracksById = database.trackDao().getByIds(allIds)
            .associateBy { it.id }
            .mapValues { (trackId, entity) -> entity.toDomain(artworkOverrides[trackId]) }
        val queueFallbackTracksById = decodePlaybackQueueTrackSnapshots(persisted.queueTracksJson)
        val orderedFallbackTracksById = decodePlaybackQueueTrackSnapshots(persisted.orderedQueueTracksJson)
        val restoredTracks = queueIds.mapIndexedNotNull { index, trackId ->
            (tracksById[trackId] ?: queueFallbackTracksById[trackId] ?: orderedFallbackTracksById[trackId])
                ?.let { track -> RestoredPlaybackQueueTrack(originalIndex = index, track = track) }
        }
        val tracks = restoredTracks.map { it.track }
        val orderedTracks = orderedQueueIds.mapNotNull { trackId ->
            tracksById[trackId] ?: orderedFallbackTracksById[trackId] ?: queueFallbackTracksById[trackId]
        }.ifEmpty { tracks }
        if (tracks.isEmpty()) {
            mutableSnapshot.update { it.copy(isHydratingPlayback = false) }
            return PlaybackHydrationResult.Empty
        }
        val index = restoredTracks.restoredIndexForOriginalIndex(
            persisted.currentIndex.coerceIn(0, queueIds.lastIndex),
        )
        val mode = persisted.mode.toPlaybackMode()
        val playWhenReady = playbackPreferencesStore.autoPlayOnStartup.value
        var loadRequest: PlaybackLoadRequest? = null
        var shouldApplyRestore = false
        playbackCommandMutex.withLock {
            if (mutableSnapshot.value.queue.isNotEmpty()) {
                mutableSnapshot.update { it.copy(isHydratingPlayback = false) }
            } else {
                shouldApplyRestore = true
                mutableSnapshot.value = PlaybackSnapshot(
                    queue = tracks,
                    orderedQueue = orderedTracks,
                    currentIndex = index,
                    mode = mode,
                    isHydratingPlayback = false,
                    isPlaying = playWhenReady,
                    positionMs = persisted.positionMs,
                    durationMs = tracks[index].durationMs,
                    canSeek = false,
                    volume = mutableSnapshot.value.volume,
                    metadataTitle = null,
                    metadataArtistName = null,
                    metadataAlbumTitle = null,
                    metadataArtworkLocator = null,
                )
                loadRequest = createLoadRequest(
                    track = tracks[index],
                    playWhenReady = playWhenReady,
                    startPositionMs = persisted.positionMs,
                )
            }
        }
        if (!shouldApplyRestore) return PlaybackHydrationResult.SupersededByPlayback
        val restoreLoadRequest = loadRequest ?: return PlaybackHydrationResult.Failed
        loadGatewaySafely(restoreLoadRequest)
        return if (restoreLoadRequest.loadToken.isCurrent()) {
            PlaybackHydrationResult.Restored(restoreLoadRequest.loadToken)
        } else {
            PlaybackHydrationResult.SupersededByPlayback
        }
    }

    private fun PlaybackHydrationResult.validateCurrentRestore(): PlaybackHydrationResult =
        when (this) {
            is PlaybackHydrationResult.Restored ->
                if (loadToken.isCurrent()) this else PlaybackHydrationResult.SupersededByPlayback

            else -> this
        }

    private suspend fun loadGatewaySafely(
        request: PlaybackLoadRequest,
    ) {
        logger.debug(PLAYBACK_LOG_TAG) {
            "load-start request=${request.loadToken.requestId} track=${request.track.id} " +
                "playWhenReady=${request.playWhenReady} startPositionMs=${request.startPositionMs}"
        }
        runCatching {
            gateway.load(
                track = request.track,
                playWhenReady = request.playWhenReady,
                startPositionMs = request.startPositionMs,
                loadToken = request.loadToken,
            )
        }.onSuccess {
            if (!request.loadToken.isCurrent()) {
                logger.debug(PLAYBACK_LOG_TAG) {
                    "load-finished-stale request=${request.loadToken.requestId} track=${request.track.id}"
                }
            }
        }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
            if (!request.loadToken.isCurrent()) {
                logger.debug(PLAYBACK_LOG_TAG) {
                    "load-failed-stale request=${request.loadToken.requestId} track=${request.track.id} " +
                        "cause=${throwable.message.orEmpty()}"
                }
                return@onFailure
            }
            logger.error(PLAYBACK_LOG_TAG, throwable) {
                "load-failed request=${request.loadToken.requestId} track=${request.track.id} " +
                    "locator=${request.track.mediaLocator} playWhenReady=${request.playWhenReady} " +
                    "startPositionMs=${request.startPositionMs}"
            }
            mutableSnapshot.update {
                it.copy(
                    isPlaying = false,
                    positionMs = request.startPositionMs.coerceAtLeast(0L),
                    canSeek = false,
                    errorMessage = buildPlaybackLoadFailureMessage(throwable),
                    errorText = playbackLoadFailureText(throwable),
                )
            }
        }
    }

    private fun ignoreCurrentGatewayErrorForNextTrack() {
        val gatewayState = gateway.state.value
        val message = gatewayState.errorMessage?.takeIf { it.isNotBlank() }
        if (message == null && gatewayState.errorText == null) return
        ignoredGatewayErrorMessage = message
        ignoredGatewayErrorText = gatewayState.errorText
        ignoredGatewayErrorRevision = gatewayState.errorRevision.takeIf { it > 0L }
    }

    private fun resolveGatewayError(gatewayState: PlaybackGatewayState): GatewayErrorResolution {
        val message = gatewayState.errorMessage?.takeIf { it.isNotBlank() }
        val text = gatewayState.errorText
        if (message == null && text == null) {
            clearIgnoredGatewayError()
            return GatewayErrorResolution.Apply(null, null)
        }
        val ignoredRevision = ignoredGatewayErrorRevision
        if (ignoredRevision != null) {
            if (gatewayState.errorRevision <= ignoredRevision) {
                return GatewayErrorResolution.Ignore
            }
            clearIgnoredGatewayError()
            return GatewayErrorResolution.Apply(message, text)
        }
        if (message == ignoredGatewayErrorMessage && text == ignoredGatewayErrorText) {
            return GatewayErrorResolution.Ignore
        }
        clearIgnoredGatewayError()
        return GatewayErrorResolution.Apply(message, text)
    }

    private fun clearIgnoredGatewayError() {
        ignoredGatewayErrorRevision = null
        ignoredGatewayErrorMessage = null
        ignoredGatewayErrorText = null
    }

    private sealed interface GatewayErrorResolution {
        data object Ignore : GatewayErrorResolution
        data class Apply(val message: String?, val text: UiText?) : GatewayErrorResolution
    }

    private fun updatePlaybackStats(snapshot: PlaybackSnapshot) {
        val track = snapshot.currentTrack
        if (track == null) {
            playbackStatsSession = null
            return
        }
        val nowMs = currentTimeMillis()
        val session = playbackStatsSession
            ?.takeIf { it.matches(latestLoadRequestId, track) }
            ?: PlaybackStatsSession(
                sessionId = latestLoadRequestId,
                trackId = track.id,
                mediaLocator = track.mediaLocator,
            )
        val elapsedMs = session.lastPlayingAtMs
            ?.let { startedAt -> (nowMs - startedAt).coerceAtLeast(0L) }
            ?: 0L
        val accumulatedPlayingMs = (session.accumulatedPlayingMs + elapsedMs).coerceAtLeast(0L)
        var nextSession = session.copy(
            durationMs = playbackStatsDurationMs(snapshot, track),
            accumulatedPlayingMs = accumulatedPlayingMs,
            lastPlayingAtMs = if (snapshot.isPlaying) nowMs else null,
        )
        if (snapshot.isPlaying && !nextSession.nowPlayingReported) {
            dispatchPlaybackStatsCommand(
                PlaybackStatsCommand.ReportNowPlaying(
                    track = track,
                    atMillis = nowMs,
                ),
            )
            nextSession = nextSession.copy(nowPlayingReported = true)
        }
        if (
            !nextSession.playSubmitted &&
            accumulatedPlayingMs >= playbackStatsSubmissionThresholdMs(nextSession.durationMs)
        ) {
            dispatchPlaybackStatsCommand(
                PlaybackStatsCommand.SubmitPlay(
                    track = track,
                    atMillis = nowMs,
                ),
            )
            nextSession = nextSession.copy(playSubmitted = true)
        }
        playbackStatsSession = nextSession
    }

    private fun dispatchPlaybackStatsCommand(command: PlaybackStatsCommand) {
        playbackStatsJob.start()
        playbackStatsCommands.trySend(command).exceptionOrNull()?.let { error ->
            runCatching {
                logger.warn(PLAYBACK_LOG_TAG) {
                    "stats-enqueue-failed track=${command.track.id} event=${command.eventName} " +
                        "cause=${error.message.orEmpty()}"
                }
            }
        }
    }

    private suspend fun reportPlaybackStatsCommand(command: PlaybackStatsCommand) {
        runCatching {
            when (command) {
                is PlaybackStatsCommand.ReportNowPlaying -> {
                    playbackStatsReporter.reportNowPlaying(command.track, command.atMillis)
                }

                is PlaybackStatsCommand.SubmitPlay -> {
                    playbackStatsReporter.submitPlay(command.track, command.atMillis)
                }
            }
        }.onFailure { throwable ->
            if (throwable is CancellationException) currentCoroutineContext().ensureActive()
            runCatching {
                logger.warn(PLAYBACK_LOG_TAG) {
                    "stats-report-failed track=${command.track.id} event=${command.eventName} " +
                        "cause=${throwable.message.orEmpty()}"
                }
            }
        }
    }

    private fun createLoadRequest(
        track: Track,
        playWhenReady: Boolean,
        startPositionMs: Long,
    ): PlaybackLoadRequest {
        val requestId = latestLoadRequestId + 1L
        latestLoadRequestId = requestId
        logger.debug(PLAYBACK_LOG_TAG) {
            "load-enqueued request=$requestId track=${track.id} locator=${track.mediaLocator} " +
                "playWhenReady=$playWhenReady startPositionMs=$startPositionMs"
        }
        return PlaybackLoadRequest(
            track = track,
            playWhenReady = playWhenReady,
            startPositionMs = startPositionMs,
            loadToken = PlaybackLoadToken(requestId) { requestId == latestLoadRequestId },
        )
    }

    private suspend fun persistSnapshotContent() {
        val snapshot = mutableSnapshot.value
        val orderedQueue = snapshot.orderedQueue.ifEmpty { snapshot.queue }
        val fallbackTracks = tracksNeedingPlaybackQueueJsonFallback(snapshot.queue, orderedQueue)
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = snapshot.queue.joinToString(",") { it.id },
                orderedQueueTrackIds = orderedQueue.joinToString(",") { it.id },
                queueTracksJson = encodePlaybackQueueTrackSnapshots(fallbackTracks),
                orderedQueueTracksJson = "",
                currentIndex = snapshot.currentIndex,
                positionMs = snapshot.positionMs,
                mode = snapshot.mode.name,
                updatedAt = now(),
            ),
        )
    }

    private suspend fun tracksNeedingPlaybackQueueJsonFallback(
        queue: List<Track>,
        orderedQueue: List<Track>,
    ): List<Track> {
        val fallbackSourceIds = remotePlaybackSourceIds(queue, orderedQueue)
            .filter { sourceId ->
                database.importSourceDao()
                    .getById(sourceId)
                    ?.indexMode != ImportSourceIndexMode.LOCAL_INDEX.name
            }
            .toSet()
        if (fallbackSourceIds.isEmpty()) return emptyList()
        val fallbackQueue = queue.filter { track -> track.remotePlaybackSourceIdOrNull() in fallbackSourceIds }
        val fallbackOrderedQueue = orderedQueue.filter { track -> track.remotePlaybackSourceIdOrNull() in fallbackSourceIds }
        val persistedTrackIds = persistedTrackIds(fallbackQueue, fallbackOrderedQueue)
        return fallbackTracksForSnapshot(
            queue = fallbackQueue,
            orderedQueue = fallbackOrderedQueue,
            persistedTrackIds = persistedTrackIds,
        )
    }

    private suspend fun persistSnapshotContentIfPersistent() {
        if (!currentQueueIsTransient) {
            persistSnapshotContent()
        }
    }

    private suspend fun persistSnapshotCursorIfPersistent() {
        if (!currentQueueIsTransient) {
            persistSnapshotCursor()
        }
    }

    private suspend fun persistSnapshotCursor() {
        val snapshot = mutableSnapshot.value
        database.playbackQueueSnapshotDao().updateCursor(
            currentIndex = snapshot.currentIndex,
            positionMs = snapshot.positionMs,
            mode = snapshot.mode.name,
            updatedAt = now(),
        )
    }

    private suspend fun persistedTrackIds(
        queue: List<Track>,
        orderedQueue: List<Track>,
    ): Set<String> {
        val trackIds = (queue.asSequence() + orderedQueue.asSequence())
            .map { it.id }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        if (trackIds.isEmpty()) return emptySet()
        return trackIds
            .chunked(PLAYBACK_QUEUE_TRACK_LOOKUP_CHUNK_SIZE)
            .flatMap { chunk -> database.trackDao().getByIds(chunk).map { it.id } }
            .toSet()
    }

    private fun fallbackTracksForSnapshot(
        queue: List<Track>,
        orderedQueue: List<Track>,
        persistedTrackIds: Set<String>,
    ): List<Track> {
        val seenIds = mutableSetOf<String>()
        return buildList {
            fun addIfMissing(track: Track) {
                if (track.id !in persistedTrackIds && seenIds.add(track.id)) {
                    add(track)
                }
            }
            queue.forEach(::addIfMissing)
            orderedQueue.forEach(::addIfMissing)
        }
    }

    private fun PlaybackSnapshot.toShuffleSnapshot(): PlaybackSnapshot {
        val orderedQueue = this.orderedQueue.ifEmpty { queue }
        if (orderedQueue.isEmpty()) {
            return copy(mode = PlaybackMode.SHUFFLE, orderedQueue = orderedQueue)
        }
        val currentTrack = this.currentTrack
        val naturalIndex = currentTrack
            ?.let { track -> orderedQueue.indexOfFirst { it.id == track.id } }
            ?.takeIf { it >= 0 }
            ?: currentIndex.coerceIn(0, orderedQueue.lastIndex)
        val (shuffledQueue, shuffledIndex) = shuffledQueueForCurrent(orderedQueue, naturalIndex)
        return copy(
            queue = shuffledQueue,
            orderedQueue = orderedQueue,
            currentIndex = shuffledIndex,
            mode = PlaybackMode.SHUFFLE,
            durationMs = shuffledQueue[shuffledIndex].durationMs,
        )
    }

    private fun PlaybackSnapshot.toOrderSnapshot(): PlaybackSnapshot {
        val orderedQueue = this.orderedQueue.ifEmpty { queue }
        if (orderedQueue.isEmpty()) {
            return copy(mode = PlaybackMode.ORDER, orderedQueue = orderedQueue)
        }
        val currentTrack = this.currentTrack
        val orderedIndex = currentTrack
            ?.let { track -> orderedQueue.indexOfFirst { it.id == track.id } }
            ?.takeIf { it >= 0 }
            ?: currentIndex.coerceIn(0, orderedQueue.lastIndex)
        return copy(
            queue = orderedQueue,
            orderedQueue = orderedQueue,
            currentIndex = orderedIndex,
            mode = PlaybackMode.ORDER,
            durationMs = orderedQueue[orderedIndex].durationMs,
        )
    }

    private fun shuffledQueueForCurrent(
        orderedQueue: List<Track>,
        startIndex: Int,
    ): Pair<List<Track>, Int> {
        if (orderedQueue.isEmpty()) return emptyList<Track>() to -1
        val currentIndex = startIndex.coerceIn(0, orderedQueue.lastIndex)
        val currentTrack = orderedQueue[currentIndex]
        val remainingTracks = orderedQueue.filterIndexed { index, _ -> index != currentIndex }
        return (listOf(currentTrack) + remainingTracks.shuffled(shuffleRandom)) to 0
    }

    private fun buildQueueSnapshot(
        tracks: List<Track>,
        startIndex: Int,
        currentSnapshot: PlaybackSnapshot,
        isPlaying: Boolean,
    ): PlaybackSnapshot {
        val index = startIndex.coerceIn(0, tracks.lastIndex)
        val (queue, currentIndex) = if (currentSnapshot.mode == PlaybackMode.SHUFFLE) {
            shuffledQueueForCurrent(tracks, index)
        } else {
            tracks to index
        }
        val target = queue[currentIndex]
        return PlaybackSnapshot(
            queue = queue,
            orderedQueue = tracks,
            currentIndex = currentIndex,
            mode = currentSnapshot.mode,
            isHydratingPlayback = false,
            isPlaying = isPlaying,
            positionMs = 0L,
            durationMs = target.durationMs,
            canSeek = false,
            volume = currentSnapshot.volume,
            metadataTitle = null,
            metadataArtistName = null,
            metadataAlbumTitle = null,
            metadataArtworkLocator = null,
        )
    }

    private fun logDisplayArtwork(snapshot: PlaybackSnapshot) {
        val trackId = snapshot.currentTrack?.id
        val finalArtwork = snapshot.currentDisplayArtworkLocator?.takeIf { it.isNotBlank() }
        if (trackId == loggedArtworkTrackId && finalArtwork == loggedDisplayArtworkLocator) {
            return
        }
        loggedArtworkTrackId = trackId
        loggedDisplayArtworkLocator = finalArtwork
        logger.debug(PLAYBACK_LOG_TAG) {
            "display-artwork track=${trackId.orEmpty()} " +
                "metadata=${snapshot.metadataArtworkLocator.orEmpty()} " +
                "trackArtwork=${snapshot.currentTrack?.artworkLocator.orEmpty()} " +
                "final=${finalArtwork.orEmpty()}"
        }
    }
}

private fun Throwable.addSuppressedSafely(failure: Throwable) {
    if (failure === this || suppressedExceptions.any { suppressed -> suppressed === failure }) return
    runCatching { addSuppressed(failure) }
}

data class PlayerRuntimeServices(
    val playbackGateway: PlaybackGateway? = null,
    val playbackRepository: PlaybackRepository? = null,
    val playbackPreferencesStore: PlaybackPreferencesStore,
    val equalizerPlatformService: EqualizerPlatformService = UnsupportedEqualizerPlatformService,
    val castGateway: CastGateway = UnsupportedCastGateway,
    val castMediaUrlResolver: CastMediaUrlResolver = UnsupportedCastMediaUrlResolver,
    val castBackgroundRunSettingsOpener: CastBackgroundRunSettingsOpener =
        UnsupportedCastBackgroundRunSettingsOpener,
    val castNotificationPermissionRequester: CastNotificationPermissionRequester =
        UnsupportedCastNotificationPermissionRequester,
    val castSessionForegroundPlatformService: CastSessionForegroundPlatformService =
        UnsupportedCastSessionForegroundPlatformService,
    val lyricsSharePlatformService: LyricsSharePlatformService = UnsupportedLyricsSharePlatformService,
    val lyricsShareFontLibraryPlatformService: LyricsShareFontLibraryPlatformService =
        UnsupportedLyricsShareFontLibraryPlatformService,
    val lyricsShareFontPreferencesStore: LyricsShareFontPreferencesStore = UnsupportedLyricsShareFontPreferencesStore,
    val systemPlaybackControlsPlatformService: SystemPlaybackControlsPlatformService = UnsupportedSystemPlaybackControlsPlatformService,
    val menuBarLyricsControlsPlatformService: MenuBarLyricsControlsPlatformService =
        UnsupportedMenuBarLyricsControlsPlatformService,
    val closeDesktopResources: suspend () -> Unit = {},
)

private fun now(): Long = Clock.System.now().toEpochMilliseconds()

internal fun remotePlaybackSourceIds(
    queue: List<Track>,
    orderedQueue: List<Track>,
): Set<String> {
    return (queue.asSequence() + orderedQueue.asSequence())
        .mapNotNull { track -> track.remotePlaybackSourceIdOrNull() }
        .toSet()
}

private fun Track.remotePlaybackSourceIdOrNull(): String? {
    return parseSubsonicCompatibleSongLocator(mediaLocator)?.sourceId
        ?: parseEmbySongLocator(mediaLocator)?.first
        ?: parseFnMusicSongLocator(mediaLocator)?.first
}

internal fun playbackStatsSubmissionThresholdMs(durationMs: Long): Long {
    if (durationMs <= 0L) return PLAYBACK_STATS_FALLBACK_THRESHOLD_MS
    return minOf((durationMs / 2L).coerceAtLeast(1L), PLAYBACK_STATS_FALLBACK_THRESHOLD_MS)
}

private fun playbackStatsDurationMs(snapshot: PlaybackSnapshot, track: Track): Long {
    return when {
        snapshot.durationMs > 0L -> snapshot.durationMs
        track.durationMs > 0L -> track.durationMs
        else -> 0L
    }
}

private fun resolvePlaybackDurationMs(
    gatewayDurationMs: Long,
    currentTrack: Track?,
    currentSnapshotDurationMs: Long,
): Long {
    return when {
        gatewayDurationMs > 0L -> gatewayDurationMs
        currentTrack != null && currentTrack.durationMs > 0L -> currentTrack.durationMs
        else -> currentSnapshotDurationMs.coerceAtLeast(0L)
    }
}

private const val PLAYBACK_LOG_TAG = "Playback"
private const val PLAYBACK_STATS_FALLBACK_THRESHOLD_MS = 4 * 60 * 1000L
private const val PLAYBACK_STATS_CLOSE_TIMEOUT_MS = 2_000L
private const val PLAYBACK_QUEUE_TRACK_LOOKUP_CHUNK_SIZE = 500

private data class PlaybackQueueTrackSnapshot(
    val id: String,
    val sourceId: String,
    val title: String,
    val artistName: String?,
    val albumTitle: String?,
    val durationMs: Long,
    val mediaLocator: String,
    val relativePath: String?,
    val artworkLocator: String?,
    val albumId: String?,
    val artistId: String?,
    val remoteFavoriteHint: Boolean?,
)

private data class RestoredPlaybackQueueTrack(
    val originalIndex: Int,
    val track: Track,
)

private fun List<RestoredPlaybackQueueTrack>.restoredIndexForOriginalIndex(originalIndex: Int): Int {
    val exactIndex = indexOfFirst { it.originalIndex == originalIndex }
    if (exactIndex >= 0) return exactIndex
    val nextIndex = indexOfFirst { it.originalIndex > originalIndex }
    if (nextIndex >= 0) return nextIndex
    return indexOfLast { it.originalIndex < originalIndex }.coerceAtLeast(0)
}

private val playbackQueueSnapshotJson = Json

private fun encodePlaybackQueueTrackSnapshots(tracks: List<Track>): String {
    if (tracks.isEmpty()) return ""
    return buildString {
        append('[')
        tracks.forEachIndexed { index, track ->
            if (index > 0) append(',')
            track.appendPlaybackQueueTrackSnapshotJsonTo(this)
        }
        append(']')
    }
}

private fun decodePlaybackQueueTrackSnapshots(raw: String): Map<String, Track> {
    if (raw.isBlank()) return emptyMap()
    val array = runCatching { playbackQueueSnapshotJson.parseToJsonElement(raw) }
        .getOrNull() as? JsonArray ?: return emptyMap()
    return array
        .mapNotNull { element ->
            (element as? JsonObject)
                ?.toPlaybackQueueTrackSnapshot()
                ?.toTrack()
        }
        .associateBy { it.id }
}

private fun Track.appendPlaybackQueueTrackSnapshotJsonTo(builder: StringBuilder) {
    builder.append('{')
    var needsComma = false
    fun appendName(name: String) {
        if (needsComma) builder.append(',')
        builder.appendJsonString(name)
        builder.append(':')
        needsComma = true
    }
    fun appendStringField(name: String, value: String) {
        appendName(name)
        builder.appendJsonString(value)
    }
    fun appendOptionalStringField(name: String, value: String?) {
        val resolved = value?.takeIf { it.isNotBlank() } ?: return
        appendStringField(name, resolved)
    }

    appendStringField("id", id)
    appendStringField("sourceId", sourceId)
    appendStringField("title", title)
    appendName("durationMs")
    builder.append(durationMs)
    appendStringField("mediaLocator", mediaLocator)
    appendOptionalStringField("relativePath", relativePath)
    appendOptionalStringField("artistName", artistName)
    appendOptionalStringField("albumTitle", albumTitle)
    appendOptionalStringField("artworkLocator", artworkLocator)
    appendOptionalStringField("albumId", albumId)
    appendOptionalStringField("artistId", artistId)
    remoteFavoriteHint?.let {
        appendName("remoteFavoriteHint")
        builder.append(it)
    }
    builder.append('}')
}

private fun StringBuilder.appendJsonString(value: String) {
    append('"')
    value.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (char < ' ') {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
    }
    append('"')
}

private fun PlaybackQueueTrackSnapshot.toTrack(): Track? {
    val restoredId = id.trim().takeIf { it.isNotBlank() } ?: return null
    val restoredSourceId = sourceId.trim().takeIf { it.isNotBlank() } ?: return null
    val restoredTitle = title.trim().takeIf { it.isNotBlank() } ?: return null
    val restoredMediaLocator = mediaLocator.trim().takeIf { it.isNotBlank() } ?: return null
    val restoredRelativePath = relativePath?.takeIf { it.isNotBlank() } ?: restoredMediaLocator
    return Track(
        id = restoredId,
        sourceId = restoredSourceId,
        title = restoredTitle,
        artistName = artistName?.trim()?.takeIf { it.isNotBlank() },
        albumTitle = albumTitle?.trim()?.takeIf { it.isNotBlank() },
        durationMs = durationMs.coerceAtLeast(0L),
        mediaLocator = restoredMediaLocator,
        relativePath = restoredRelativePath,
        artworkLocator = artworkLocator?.trim()?.takeIf { it.isNotBlank() },
        albumId = albumId?.trim()?.takeIf { it.isNotBlank() },
        artistId = artistId?.trim()?.takeIf { it.isNotBlank() },
        remoteFavoriteHint = remoteFavoriteHint,
    )
}

private fun JsonObject.toPlaybackQueueTrackSnapshot(): PlaybackQueueTrackSnapshot? {
    return PlaybackQueueTrackSnapshot(
        id = stringOrNull("id") ?: return null,
        sourceId = stringOrNull("sourceId") ?: return null,
        title = stringOrNull("title") ?: return null,
        artistName = stringOrNull("artistName"),
        albumTitle = stringOrNull("albumTitle"),
        durationMs = longOrNull("durationMs") ?: 0L,
        mediaLocator = stringOrNull("mediaLocator") ?: return null,
        relativePath = stringOrNull("relativePath"),
        artworkLocator = stringOrNull("artworkLocator"),
        albumId = stringOrNull("albumId"),
        artistId = stringOrNull("artistId"),
        remoteFavoriteHint = booleanOrNull("remoteFavoriteHint"),
    )
}

private fun JsonObject.stringOrNull(key: String): String? {
    return (this[key] as? JsonPrimitive)?.contentOrNull
}

private fun JsonObject.longOrNull(key: String): Long? {
    return (this[key] as? JsonPrimitive)?.longOrNull
}

private fun JsonObject.booleanOrNull(key: String): Boolean? {
    return (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
}

private data class PlaybackStatsSession(
    val sessionId: Long,
    val trackId: String,
    val mediaLocator: String,
    val durationMs: Long = 0L,
    val accumulatedPlayingMs: Long = 0L,
    val lastPlayingAtMs: Long? = null,
    val nowPlayingReported: Boolean = false,
    val playSubmitted: Boolean = false,
) {
    fun matches(sessionId: Long, track: Track): Boolean {
        return this.sessionId == sessionId &&
            trackId == track.id &&
            mediaLocator == track.mediaLocator
    }
}

private sealed interface PlaybackStatsCommand {
    val track: Track
    val atMillis: Long
    val eventName: String

    data class ReportNowPlaying(
        override val track: Track,
        override val atMillis: Long,
    ) : PlaybackStatsCommand {
        override val eventName: String = "now-playing"
    }

    data class SubmitPlay(
        override val track: Track,
        override val atMillis: Long,
    ) : PlaybackStatsCommand {
        override val eventName: String = "submit-play"
    }
}

private data class PlaybackLoadRequest(
    val track: Track,
    val playWhenReady: Boolean,
    val startPositionMs: Long,
    val loadToken: PlaybackLoadToken,
)

private fun buildPlaybackLoadFailureMessage(throwable: Throwable): String {
    val detail = throwable.message?.takeIf { it.isNotBlank() }
        ?: throwable::class.simpleName
        ?: "未知错误"
    return "访问歌曲失败：$detail"
}

private fun String.toPlaybackMode(): PlaybackMode {
    return runCatching { PlaybackMode.valueOf(this) }.getOrDefault(PlaybackMode.ORDER)
}
