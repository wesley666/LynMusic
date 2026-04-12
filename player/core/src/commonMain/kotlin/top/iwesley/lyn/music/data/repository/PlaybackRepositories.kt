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
import top.iwesley.lyn.music.core.model.LyricsSharePlatformService
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackLoadToken
import top.iwesley.lyn.music.core.model.PlaybackMode
import top.iwesley.lyn.music.core.model.PlaybackPreferencesStore
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.SystemPlaybackControlCallbacks
import top.iwesley.lyn.music.core.model.SystemPlaybackControlsPlatformService
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.UnsupportedLyricsSharePlatformService
import top.iwesley.lyn.music.core.model.UnsupportedSystemPlaybackControlsPlatformService
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.PlaybackQueueSnapshotEntity

interface PlaybackRepository {
    val snapshot: StateFlow<PlaybackSnapshot>

    suspend fun hydratePersistedQueueIfNeeded()
    suspend fun playTracks(tracks: List<Track>, startIndex: Int)
    suspend fun togglePlayPause()
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
    private val scope: CoroutineScope,
    private val systemPlaybackControlsPlatformService: SystemPlaybackControlsPlatformService = UnsupportedSystemPlaybackControlsPlatformService,
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
    hydrateImmediately: Boolean = true,
) : PlaybackRepository {
    private val mutableSnapshot = MutableStateFlow(PlaybackSnapshot(isHydratingPlayback = true))
    private val playbackCommandMutex = Mutex()
    @Volatile
    private var latestLoadRequestId = 0L
    @Volatile
    private var hasHydratedPersistedQueue = false
    private var observedCompletionCount = 0L
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
        if (hydrateImmediately) {
            runBlocking {
                hydratePersistedQueueIfNeeded()
            }
        }
        scope.launch {
            combine(
                database.trackDao().observeAll(),
                database.lyricsCacheDao().observeBySourceId(MANUAL_LYRICS_OVERRIDE_SOURCE_ID),
            ) { entities, overrides ->
                val artworkOverrides = manualArtworkOverridesByTrackId(overrides)
                entities.associate { entity ->
                    entity.id to entity.toDomain(artworkOverrides[entity.id])
                }
            }.collect { tracksById ->
                var snapshotChanged = false
                var currentTrackChanged = false
                mutableSnapshot.update { snapshot ->
                    if (snapshot.queue.isEmpty()) return@update snapshot
                    val updatedQueue = snapshot.queue.mapIndexed { index, track ->
                        val updated = tracksById[track.id] ?: track
                        if (updated != track) {
                            snapshotChanged = true
                            if (index == snapshot.currentIndex) {
                                currentTrackChanged = true
                            }
                        }
                        updated
                    }
                    if (!snapshotChanged) return@update snapshot
                    snapshot.copy(
                        queue = updatedQueue,
                        metadataTitle = if (currentTrackChanged) null else snapshot.metadataTitle,
                        metadataArtistName = if (currentTrackChanged) null else snapshot.metadataArtistName,
                        metadataAlbumTitle = if (currentTrackChanged) null else snapshot.metadataAlbumTitle,
                        // Keep temporary artwork overrides stable across live track refreshes.
                        metadataArtworkLocator = snapshot.metadataArtworkLocator,
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
                        durationMs = gatewayState.durationMs,
                        volume = gatewayState.volume,
                        metadataTitle = gatewayState.metadataTitle,
                        metadataArtistName = gatewayState.metadataArtistName,
                        metadataAlbumTitle = gatewayState.metadataAlbumTitle,
                        metadataArtworkLocator = it.metadataArtworkLocator,
                        errorMessage = gatewayState.errorMessage,
                    )
                }
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
            val index = startIndex.coerceIn(0, tracks.lastIndex)
            mutableSnapshot.value = PlaybackSnapshot(
                queue = tracks,
                currentIndex = index,
                mode = mutableSnapshot.value.mode,
                isHydratingPlayback = false,
                isPlaying = true,
                positionMs = 0L,
                durationMs = tracks[index].durationMs,
                volume = mutableSnapshot.value.volume,
                metadataTitle = null,
                metadataArtistName = null,
                metadataAlbumTitle = null,
                metadataArtworkLocator = null,
            )
            loadRequest = createLoadRequest(
                track = tracks[index],
                playWhenReady = true,
                startPositionMs = 0L,
            )
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
            if (snapshot.mode != PlaybackMode.REPEAT_ONE && snapshot.positionMs > 5_000) {
                gateway.seekTo(0L)
                mutableSnapshot.update { it.copy(positionMs = 0L) }
                persistSnapshot()
                return@withLock
            }
            val previousIndex = when {
                snapshot.mode == PlaybackMode.SHUFFLE && snapshot.queue.size > 1 -> randomIndex(snapshot.queue.lastIndex, snapshot.currentIndex)
                snapshot.currentIndex > 0 -> snapshot.currentIndex - 1
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
            gateway.seekTo(positionMs)
            mutableSnapshot.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
            persistSnapshot()
        }
    }

    override suspend fun setVolume(volume: Float) {
        val normalized = volume.coerceIn(0f, 1f)
        playbackCommandMutex.withLock {
            gateway.setVolume(normalized)
            mutableSnapshot.update { it.copy(volume = normalized) }
        }
    }

    override suspend fun cycleMode() {
        playbackCommandMutex.withLock {
            val nextMode = when (mutableSnapshot.value.mode) {
                PlaybackMode.ORDER -> PlaybackMode.SHUFFLE
                PlaybackMode.SHUFFLE -> PlaybackMode.REPEAT_ONE
                PlaybackMode.REPEAT_ONE -> PlaybackMode.ORDER
            }
            mutableSnapshot.update { it.copy(mode = nextMode) }
            persistSnapshot()
        }
    }

    override suspend fun overrideCurrentTrackArtwork(artworkLocator: String?) {
        playbackCommandMutex.withLock {
            val snapshot = mutableSnapshot.value
            val currentTrack = snapshot.currentTrack ?: return
            val currentIndex = snapshot.currentIndex
            if (currentIndex !in snapshot.queue.indices) return
            val updatedQueue = snapshot.queue.toMutableList().also { queue ->
                queue[currentIndex] = currentTrack.copy(
                    artworkLocator = artworkLocator ?: currentTrack.artworkLocator,
                )
            }
            mutableSnapshot.update {
                it.copy(
                    queue = updatedQueue,
                    metadataArtworkLocator = artworkLocator ?: it.metadataArtworkLocator,
                )
            }
        }
    }

    override suspend fun close() {
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
                    nextSequentialIndex(snapshot, autoTriggered = false) ?: return null
                }
            }
            PlaybackMode.SHUFFLE -> randomIndex(snapshot.queue.lastIndex, snapshot.currentIndex)
            PlaybackMode.ORDER -> nextSequentialIndex(snapshot, autoTriggered) ?: return null
        }
        return loadIndexLocked(nextIndex, playWhenReady = true)
    }

    private suspend fun nextSequentialIndex(snapshot: PlaybackSnapshot, autoTriggered: Boolean): Int? {
        if (snapshot.currentIndex + 1 <= snapshot.queue.lastIndex) {
            return snapshot.currentIndex + 1
        }
        if (autoTriggered) {
            gateway.pause()
            mutableSnapshot.update { it.copy(isPlaying = false, positionMs = 0L) }
            persistSnapshot()
            return null
        }
        return 0
    }

    private suspend fun loadIndexLocked(index: Int, playWhenReady: Boolean): PlaybackLoadRequest? {
        val queue = mutableSnapshot.value.queue
        val target = queue.getOrNull(index) ?: return null
        mutableSnapshot.update {
            it.copy(
                currentIndex = index,
                isHydratingPlayback = false,
                isPlaying = playWhenReady,
                positionMs = 0L,
                durationMs = target.durationMs,
                metadataTitle = null,
                metadataArtistName = null,
                metadataAlbumTitle = null,
                metadataArtworkLocator = null,
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
        val ids = persisted.queueTrackIds.split(',').filter { it.isNotBlank() }
        if (ids.isEmpty()) {
            mutableSnapshot.update { it.copy(isHydratingPlayback = false) }
            return
        }
        val artworkOverrides = manualArtworkOverridesByTrackId(
            ids.mapNotNull { trackId ->
                database.lyricsCacheDao().getByTrackIdAndSourceId(trackId, MANUAL_LYRICS_OVERRIDE_SOURCE_ID)
            },
        )
        val tracks = database.trackDao().getByIds(ids)
            .associateBy { it.id }
            .let { indexed -> ids.mapNotNull { trackId -> indexed[trackId]?.toDomain(artworkOverrides[trackId]) } }
        if (tracks.isEmpty()) {
            mutableSnapshot.update { it.copy(isHydratingPlayback = false) }
            return
        }
        val index = persisted.currentIndex.coerceIn(0, tracks.lastIndex)
        val mode = persisted.mode.toPlaybackMode()
        var loadRequest: PlaybackLoadRequest? = null
        var shouldApplyRestore = false
        playbackCommandMutex.withLock {
            if (mutableSnapshot.value.queue.isNotEmpty()) {
                mutableSnapshot.update { it.copy(isHydratingPlayback = false) }
            } else {
                shouldApplyRestore = true
                mutableSnapshot.value = PlaybackSnapshot(
                    queue = tracks,
                    currentIndex = index,
                    mode = mode,
                    isHydratingPlayback = false,
                    isPlaying = false,
                    positionMs = persisted.positionMs,
                    durationMs = tracks[index].durationMs,
                    metadataTitle = null,
                    metadataArtistName = null,
                    metadataAlbumTitle = null,
                    metadataArtworkLocator = null,
                )
                loadRequest = createLoadRequest(
                    track = tracks[index],
                    playWhenReady = false,
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
                    errorMessage = buildPlaybackLoadFailureMessage(throwable),
                )
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
                currentIndex = snapshot.currentIndex,
                positionMs = snapshot.positionMs,
                mode = snapshot.mode.name,
                updatedAt = now(),
            ),
        )
    }

    private fun randomIndex(lastIndex: Int, currentIndex: Int): Int {
        if (lastIndex <= 0) return currentIndex.coerceAtLeast(0)
        var next = currentIndex
        while (next == currentIndex) {
            next = Random.nextInt(0, lastIndex + 1)
        }
        return next
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
    val systemPlaybackControlsPlatformService: SystemPlaybackControlsPlatformService = UnsupportedSystemPlaybackControlsPlatformService,
)

private fun now(): Long = Clock.System.now().toEpochMilliseconds()

private const val PLAYBACK_LOG_TAG = "Playback"

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
