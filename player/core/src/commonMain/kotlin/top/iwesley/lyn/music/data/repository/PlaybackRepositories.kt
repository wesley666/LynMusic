package top.iwesley.lyn.music.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlin.time.Clock
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.LyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.LyricsSharePlatformService
import top.iwesley.lyn.music.core.model.LyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.NoopPlaybackStatsReporter
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackLoadToken
import top.iwesley.lyn.music.core.model.PlaybackMode
import top.iwesley.lyn.music.core.model.PlaybackPreferencesStore
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.PlaybackStatsReporter
import top.iwesley.lyn.music.core.model.SystemPlaybackControlCallbacks
import top.iwesley.lyn.music.core.model.SystemPlaybackControlsPlatformService
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.UnsupportedLyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.UnsupportedLyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedLyricsSharePlatformService
import top.iwesley.lyn.music.core.model.UnsupportedSystemPlaybackControlsPlatformService
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.normalizePlaybackVolume
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.PlaybackQueueSnapshotEntity

interface PlaybackRepository {
    val snapshot: StateFlow<PlaybackSnapshot>

    suspend fun hydratePersistedQueueIfNeeded()
    suspend fun playTracks(tracks: List<Track>, startIndex: Int)
    suspend fun playQueueIndex(index: Int)
    suspend fun togglePlayPause()
    suspend fun pause()
    suspend fun skipNext()
    suspend fun skipPrevious()
    suspend fun seekTo(positionMs: Long)
    suspend fun setVolume(volume: Float)
    suspend fun cycleMode()
    suspend fun overrideCurrentTrackArtwork(artworkLocator: String?)
    suspend fun close()
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
    @Volatile
    private var latestLoadRequestId = 0L
    @Volatile
    private var hasHydratedPersistedQueue = false
    private var observedCompletionCount = 0L
    private var playbackStatsSession: PlaybackStatsSession? = null
    private var loggedArtworkTrackId: String? = null
    private var loggedDisplayArtworkLocator: String? = null

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
        scope.launch {
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
                        currentArtworkRevision = if (currentArtworkChanged) {
                            snapshot.currentArtworkRevision + 1
                        } else {
                            snapshot.currentArtworkRevision
                        },
                    )
                }
                if (snapshotChanged) {
                    persistSnapshot()
                }
            }
        }
        scope.launch {
            gateway.state.collect { gatewayState ->
                val completionChanged = gatewayState.completionCount > observedCompletionCount
                observedCompletionCount = gatewayState.completionCount
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
                        currentArtworkRevision = it.currentArtworkRevision,
                        currentNavidromeAudioQuality = gatewayState.currentNavidromeAudioQuality,
                        currentPlaybackAudioFormat = gatewayState.currentPlaybackAudioFormat,
                        errorMessage = gatewayState.errorMessage,
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
                    persistSnapshot()
                }
            }
        }
        scope.launch {
            snapshot.collect { snapshot ->
                logDisplayArtwork(snapshot)
                systemPlaybackControlsPlatformService.updateSnapshot(snapshot)
            }
        }
    }

    override suspend fun hydratePersistedQueueIfNeeded() {
        if (hasHydratedPersistedQueue) return
        runCatching { restoreQueueAsync() }
            .onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                mutableSnapshot.update { it.copy(isHydratingPlayback = false) }
                logger.error(PLAYBACK_LOG_TAG, throwable) {
                    "hydrate-failed"
                }
            }
    }

    override suspend fun playTracks(tracks: List<Track>, startIndex: Int) {
        var loadRequest: PlaybackLoadRequest? = null
        playbackCommandMutex.withLock {
            if (tracks.isEmpty()) return@withLock
            updatePlaybackStats(mutableSnapshot.value)
            val currentSnapshot = mutableSnapshot.value
            val index = startIndex.coerceIn(0, tracks.lastIndex)
            val (queue, currentIndex) = if (currentSnapshot.mode == PlaybackMode.SHUFFLE) {
                shuffledQueueForCurrent(tracks, index)
            } else {
                tracks to index
            }
            val target = queue[currentIndex]
            mutableSnapshot.value = PlaybackSnapshot(
                queue = queue,
                orderedQueue = tracks,
                currentIndex = currentIndex,
                mode = currentSnapshot.mode,
                isHydratingPlayback = false,
                isPlaying = true,
                positionMs = 0L,
                durationMs = target.durationMs,
                canSeek = false,
                volume = currentSnapshot.volume,
                metadataTitle = null,
                metadataArtistName = null,
                metadataAlbumTitle = null,
                metadataArtworkLocator = null,
                currentArtworkRevision = 0L,
            )
            loadRequest = createLoadRequest(
                track = target,
                playWhenReady = true,
                startPositionMs = 0L,
            )
        }
        loadRequest?.let {
            loadGatewaySafely(it)
            persistSnapshot()
        }
    }

    override suspend fun playQueueIndex(index: Int) {
        val loadRequest = playbackCommandMutex.withLock {
            loadIndexLocked(index, playWhenReady = true)
        }
        loadRequest?.let {
            loadGatewaySafely(it)
            persistSnapshot()
        }
    }

    override suspend fun togglePlayPause() {
        playbackCommandMutex.withLock {
            if (mutableSnapshot.value.currentTrack == null) return
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
            persistSnapshot()
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
                persistSnapshot()
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
            persistSnapshot()
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        playbackCommandMutex.withLock {
            if (!mutableSnapshot.value.canSeek) return@withLock
            gateway.seekTo(positionMs)
            mutableSnapshot.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
            persistSnapshot()
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
            persistSnapshot()
        }
    }

    override suspend fun overrideCurrentTrackArtwork(artworkLocator: String?) {
        playbackCommandMutex.withLock {
            val snapshot = mutableSnapshot.value
            snapshot.currentTrack ?: return
            val resolvedArtworkLocator = artworkLocator?.takeIf { it.isNotBlank() } ?: return
            mutableSnapshot.update {
                it.copy(
                    metadataArtworkLocator = resolvedArtworkLocator,
                    currentArtworkRevision = it.currentArtworkRevision + 1,
                )
            }
        }
    }

    override suspend fun close() {
        updatePlaybackStats(mutableSnapshot.value)
        systemPlaybackControlsPlatformService.close()
        gateway.release()
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
                currentArtworkRevision = 0L,
                errorMessage = null,
            )
        }
        return createLoadRequest(
            track = target,
            playWhenReady = playWhenReady,
            startPositionMs = 0L,
        )
    }

    private suspend fun restoreQueueAsync() {
        val shouldHydrate = playbackCommandMutex.withLock {
            if (hasHydratedPersistedQueue) {
                false
            } else {
                hasHydratedPersistedQueue = true
                if (mutableSnapshot.value.queue.isNotEmpty()) {
                    mutableSnapshot.update { it.copy(isHydratingPlayback = false) }
                    false
                } else {
                    true
                }
            }
        }
        if (!shouldHydrate) return

        val persisted = database.playbackQueueSnapshotDao().get()
        if (persisted == null) {
            mutableSnapshot.update { it.copy(isHydratingPlayback = false) }
            return
        }
        val queueIds = persisted.queueTrackIds.split(',').filter { it.isNotBlank() }
        if (queueIds.isEmpty()) {
            mutableSnapshot.update { it.copy(isHydratingPlayback = false) }
            return
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
        val tracks = queueIds.mapNotNull { trackId -> tracksById[trackId] }
        val orderedTracks = orderedQueueIds.mapNotNull { trackId -> tracksById[trackId] }.ifEmpty { tracks }
        if (tracks.isEmpty()) {
            mutableSnapshot.update { it.copy(isHydratingPlayback = false) }
            return
        }
        val index = persisted.currentIndex.coerceIn(0, tracks.lastIndex)
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
                    currentArtworkRevision = 0L,
                )
                loadRequest = createLoadRequest(
                    track = tracks[index],
                    playWhenReady = playWhenReady,
                    startPositionMs = persisted.positionMs,
                )
            }
        }
        if (!shouldApplyRestore) return
        loadRequest?.let { loadGatewaySafely(it) }
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
                )
            }
        }
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
        scope.launch {
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
                if (throwable is CancellationException) throw throwable
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

    private suspend fun persistSnapshot() {
        val snapshot = mutableSnapshot.value
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = snapshot.queue.joinToString(",") { it.id },
                orderedQueueTrackIds = snapshot.orderedQueue.ifEmpty { snapshot.queue }.joinToString(",") { it.id },
                currentIndex = snapshot.currentIndex,
                positionMs = snapshot.positionMs,
                mode = snapshot.mode.name,
                updatedAt = now(),
            ),
        )
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

data class PlayerRuntimeServices(
    val playbackGateway: PlaybackGateway,
    val playbackPreferencesStore: PlaybackPreferencesStore,
    val lyricsSharePlatformService: LyricsSharePlatformService = UnsupportedLyricsSharePlatformService,
    val lyricsShareFontLibraryPlatformService: LyricsShareFontLibraryPlatformService =
        UnsupportedLyricsShareFontLibraryPlatformService,
    val lyricsShareFontPreferencesStore: LyricsShareFontPreferencesStore = UnsupportedLyricsShareFontPreferencesStore,
    val systemPlaybackControlsPlatformService: SystemPlaybackControlsPlatformService = UnsupportedSystemPlaybackControlsPlatformService,
)

private fun now(): Long = Clock.System.now().toEpochMilliseconds()

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
