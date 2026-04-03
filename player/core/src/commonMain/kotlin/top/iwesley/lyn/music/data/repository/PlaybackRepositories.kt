package top.iwesley.lyn.music.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackMode
import top.iwesley.lyn.music.core.model.PlaybackPreferencesStore
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.PlaybackQueueSnapshotEntity

interface PlaybackRepository {
    val snapshot: StateFlow<PlaybackSnapshot>

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
) : PlaybackRepository {
    private val mutableSnapshot = MutableStateFlow(PlaybackSnapshot())
    private var observedCompletionCount = 0L

    override val snapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot.asStateFlow()

    init {
        scope.launch {
            restoreQueue()
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
                if (completionChanged) {
                    advance(autoTriggered = true)
                }
            }
        }
    }

    override suspend fun playTracks(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val index = startIndex.coerceIn(0, tracks.lastIndex)
        mutableSnapshot.value = PlaybackSnapshot(
            queue = tracks,
            currentIndex = index,
            mode = mutableSnapshot.value.mode,
            isPlaying = true,
            positionMs = 0L,
            durationMs = tracks[index].durationMs,
            volume = mutableSnapshot.value.volume,
            metadataTitle = null,
            metadataArtistName = null,
            metadataAlbumTitle = null,
            metadataArtworkLocator = null,
        )
        gateway.load(tracks[index], playWhenReady = true, startPositionMs = 0L)
        persistSnapshot()
    }

    override suspend fun togglePlayPause() {
        if (mutableSnapshot.value.currentTrack == null) return
        if (mutableSnapshot.value.isPlaying) {
            gateway.pause()
        } else {
            gateway.play()
        }
    }

    override suspend fun skipNext() {
        advance(autoTriggered = false)
    }

    override suspend fun skipPrevious() {
        val snapshot = mutableSnapshot.value
        if (snapshot.queue.isEmpty()) return
        if (snapshot.mode != PlaybackMode.REPEAT_ONE && snapshot.positionMs > 5_000) {
            gateway.seekTo(0L)
            mutableSnapshot.update { it.copy(positionMs = 0L) }
            persistSnapshot()
            return
        }
        val previousIndex = when {
            snapshot.mode == PlaybackMode.SHUFFLE && snapshot.queue.size > 1 -> randomIndex(snapshot.queue.lastIndex, snapshot.currentIndex)
            snapshot.currentIndex > 0 -> snapshot.currentIndex - 1
            else -> 0
        }
        loadIndex(previousIndex, playWhenReady = true)
    }

    override suspend fun seekTo(positionMs: Long) {
        gateway.seekTo(positionMs)
        mutableSnapshot.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
        persistSnapshot()
    }

    override suspend fun setVolume(volume: Float) {
        val normalized = volume.coerceIn(0f, 1f)
        gateway.setVolume(normalized)
        mutableSnapshot.update { it.copy(volume = normalized) }
    }

    override suspend fun cycleMode() {
        val nextMode = when (mutableSnapshot.value.mode) {
            PlaybackMode.ORDER -> PlaybackMode.SHUFFLE
            PlaybackMode.SHUFFLE -> PlaybackMode.REPEAT_ONE
            PlaybackMode.REPEAT_ONE -> PlaybackMode.ORDER
        }
        mutableSnapshot.update { it.copy(mode = nextMode) }
        persistSnapshot()
    }

    override suspend fun overrideCurrentTrackArtwork(artworkLocator: String?) {
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

    override suspend fun close() {
        gateway.release()
    }

    private suspend fun advance(autoTriggered: Boolean) {
        val snapshot = mutableSnapshot.value
        if (snapshot.queue.isEmpty()) return
        val nextIndex = when (snapshot.mode) {
            PlaybackMode.REPEAT_ONE -> {
                if (autoTriggered) {
                    snapshot.currentIndex
                } else {
                    nextSequentialIndex(snapshot, autoTriggered = false) ?: return
                }
            }
            PlaybackMode.SHUFFLE -> randomIndex(snapshot.queue.lastIndex, snapshot.currentIndex)
            PlaybackMode.ORDER -> nextSequentialIndex(snapshot, autoTriggered) ?: return
        }
        loadIndex(nextIndex, playWhenReady = true)
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

    private suspend fun loadIndex(index: Int, playWhenReady: Boolean) {
        val queue = mutableSnapshot.value.queue
        val target = queue.getOrNull(index) ?: return
        mutableSnapshot.update {
            it.copy(
                currentIndex = index,
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
        gateway.load(target, playWhenReady = playWhenReady, startPositionMs = 0L)
        persistSnapshot()
    }

    private suspend fun restoreQueue() {
        val persisted = database.playbackQueueSnapshotDao().get() ?: return
        val ids = persisted.queueTrackIds.split(',').filter { it.isNotBlank() }
        if (ids.isEmpty()) return
        val tracks = database.trackDao().getByIds(ids)
            .associateBy { it.id }
            .let { indexed -> ids.mapNotNull { indexed[it]?.toDomain() } }
        if (tracks.isEmpty()) return
        val index = persisted.currentIndex.coerceIn(0, tracks.lastIndex)
        val mode = persisted.mode.toPlaybackMode()
        mutableSnapshot.value = PlaybackSnapshot(
            queue = tracks,
            currentIndex = index,
            mode = mode,
            isPlaying = false,
            positionMs = persisted.positionMs,
            durationMs = tracks[index].durationMs,
            metadataTitle = null,
            metadataArtistName = null,
            metadataAlbumTitle = null,
            metadataArtworkLocator = null,
        )
        gateway.load(tracks[index], playWhenReady = false, startPositionMs = persisted.positionMs)
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
}

data class PlayerRuntimeServices(
    val playbackGateway: PlaybackGateway,
    val playbackPreferencesStore: PlaybackPreferencesStore,
)

private fun now(): Long = Clock.System.now().toEpochMilliseconds()

private fun String.toPlaybackMode(): PlaybackMode {
    return runCatching { PlaybackMode.valueOf(this) }.getOrDefault(PlaybackMode.ORDER)
}
