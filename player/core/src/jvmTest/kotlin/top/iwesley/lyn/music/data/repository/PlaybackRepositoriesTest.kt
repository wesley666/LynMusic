package top.iwesley.lyn.music.data.repository

import androidx.room.Room
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.core.model.PlaybackMode
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.SystemPlaybackControlCallbacks
import top.iwesley.lyn.music.core.model.SystemPlaybackControlsPlatformService
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.LyricsCacheEntity
import top.iwesley.lyn.music.data.db.PlaybackQueueSnapshotEntity
import top.iwesley.lyn.music.data.db.TrackEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackRepositoriesTest {

    @Test
    fun `manual next advances to next track in repeat one`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(database, gateway, scope)

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 1)
            advanceUntilIdle()
            repository.cycleMode()
            repository.cycleMode()
            advanceUntilIdle()

            repository.skipNext()
            advanceUntilIdle()

            assertEquals(2, repository.snapshot.value.currentIndex)
            assertEquals("track-3", repository.snapshot.value.currentTrack?.id)
            assertEquals("track-3", gateway.loadCalls.last().track.id)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `manual previous switches track in repeat one even after five seconds`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(database, gateway, scope)

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 1)
            advanceUntilIdle()
            repository.cycleMode()
            repository.cycleMode()
            advanceUntilIdle()
            gateway.updateState { it.copy(positionMs = 6_000L) }
            advanceUntilIdle()

            repository.skipPrevious()
            advanceUntilIdle()

            assertEquals(0, repository.snapshot.value.currentIndex)
            assertEquals("track-1", repository.snapshot.value.currentTrack?.id)
            assertEquals(emptyList(), gateway.seekCalls)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `natural completion stays on current track in repeat one`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(database, gateway, scope)

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 1)
            advanceUntilIdle()
            repository.cycleMode()
            repository.cycleMode()
            advanceUntilIdle()
            val loadCountBeforeCompletion = gateway.loadCalls.size

            gateway.emitCompletion()
            advanceUntilIdle()

            assertEquals(1, repository.snapshot.value.currentIndex)
            assertEquals("track-2", repository.snapshot.value.currentTrack?.id)
            assertEquals(loadCountBeforeCompletion + 1, gateway.loadCalls.size)
            assertEquals("track-2", gateway.loadCalls.last().track.id)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `restore queue and live refresh use manual artwork override`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val trackEntity = TrackEntity(
            id = "track-1",
            sourceId = "local-1",
            title = "First Song",
            artistId = null,
            artistName = "Artist A",
            albumId = null,
            albumTitle = "Album A",
            durationMs = 180_000L,
            trackNumber = null,
            discNumber = null,
            mediaLocator = "file:///music/track-1.mp3",
            relativePath = "First Song.mp3",
            artworkLocator = "/tmp/original.jpg",
            sizeBytes = 0L,
            modifiedAt = 0L,
        )
        database.trackDao().upsertAll(listOf(trackEntity))
        database.lyricsCacheDao().upsert(
            LyricsCacheEntity(
                trackId = "track-1",
                sourceId = MANUAL_LYRICS_OVERRIDE_SOURCE_ID,
                rawPayload = "manual line",
                updatedAt = 1L,
                artworkLocator = "/tmp/manual.jpg",
            ),
        )
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = "track-1",
                currentIndex = 0,
                positionMs = 0L,
                mode = PlaybackMode.ORDER.name,
                updatedAt = 1L,
            ),
        )
        val repository = DefaultPlaybackRepository(database, gateway, scope)

        try {
            advanceUntilIdle()
            assertEquals("/tmp/manual.jpg", repository.snapshot.value.currentTrack?.artworkLocator)
            assertEquals("/tmp/manual.jpg", gateway.loadCalls.single().track.artworkLocator)

            database.lyricsCacheDao().deleteByTrackIdAndSourceId("track-1", MANUAL_LYRICS_OVERRIDE_SOURCE_ID)
            database.trackDao().upsertAll(listOf(trackEntity.copy(modifiedAt = 1L)))
            advanceUntilIdle()

            assertEquals("/tmp/original.jpg", repository.snapshot.value.currentTrack?.artworkLocator)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `temporary playback artwork override survives live track refresh`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val trackEntity = TrackEntity(
            id = "track-1",
            sourceId = "local-1",
            title = "First Song",
            artistId = null,
            artistName = "Artist A",
            albumId = null,
            albumTitle = "Album A",
            durationMs = 180_000L,
            trackNumber = null,
            discNumber = null,
            mediaLocator = "file:///music/track-1.mp3",
            relativePath = "First Song.mp3",
            artworkLocator = "/tmp/original.jpg",
            sizeBytes = 0L,
            modifiedAt = 0L,
        )
        database.trackDao().upsertAll(listOf(trackEntity))
        val repository = DefaultPlaybackRepository(database, gateway, scope)

        try {
            advanceUntilIdle()
            repository.playTracks(
                tracks = listOf(
                    sampleTrack("track-1", "First Song").copy(artworkLocator = "/tmp/original.jpg"),
                ),
                startIndex = 0,
            )
            advanceUntilIdle()

            repository.overrideCurrentTrackArtwork("https://img.example.com/override.jpg")
            advanceUntilIdle()
            assertEquals("https://img.example.com/override.jpg", repository.snapshot.value.currentDisplayArtworkLocator)

            database.trackDao().upsertAll(listOf(trackEntity.copy(modifiedAt = 1L)))
            advanceUntilIdle()

            assertEquals("/tmp/original.jpg", repository.snapshot.value.currentTrack?.artworkLocator)
            assertEquals("https://img.example.com/override.jpg", repository.snapshot.value.metadataArtworkLocator)
            assertEquals("https://img.example.com/override.jpg", repository.snapshot.value.currentDisplayArtworkLocator)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `play tracks surfaces gateway load failure without throwing`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway(loadFailure = IllegalStateException("No route to host"))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(database, gateway, scope)

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 0)
            advanceUntilIdle()

            assertEquals("track-1", repository.snapshot.value.currentTrack?.id)
            assertEquals(false, repository.snapshot.value.isPlaying)
            assertEquals("访问歌曲失败：No route to host", repository.snapshot.value.errorMessage)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `restore queue surfaces gateway load failure without throwing`() = runTest {
        val database = createTestDatabase()
        database.trackDao().upsertAll(
            listOf(sampleTrackEntity("track-1", "First Song")),
        )
        database.playbackQueueSnapshotDao().upsert(
            PlaybackQueueSnapshotEntity(
                queueTrackIds = "track-1",
                currentIndex = 0,
                positionMs = 12_000L,
                mode = PlaybackMode.ORDER.name,
                updatedAt = 1L,
            ),
        )
        val gateway = FakePlaybackGateway(loadFailure = IllegalStateException("No route to host"))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(database, gateway, scope)

        try {
            advanceUntilIdle()

            assertEquals("track-1", repository.snapshot.value.currentTrack?.id)
            assertEquals(false, repository.snapshot.value.isPlaying)
            assertEquals(12_000L, repository.snapshot.value.positionMs)
            assertEquals("访问歌曲失败：No route to host", repository.snapshot.value.errorMessage)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `system controls service receives snapshot updates`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val systemControls = FakeSystemPlaybackControlsPlatformService()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(database, gateway, scope, systemControls)

        try {
            advanceUntilIdle()

            repository.playTracks(sampleTracks(), startIndex = 1)
            advanceUntilIdle()
            gateway.updateState { it.copy(positionMs = 42_000L, isPlaying = true) }
            advanceUntilIdle()

            assertEquals("track-2", systemControls.lastSnapshot?.currentTrack?.id)
            assertEquals(true, systemControls.lastSnapshot?.isPlaying)
            assertEquals(42_000L, systemControls.lastSnapshot?.positionMs)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun `system controls callbacks route to repository commands`() = runTest {
        val database = createTestDatabase()
        val gateway = FakePlaybackGateway()
        val systemControls = FakeSystemPlaybackControlsPlatformService()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val repository = DefaultPlaybackRepository(database, gateway, scope, systemControls)

        try {
            advanceUntilIdle()
            repository.playTracks(sampleTracks(), startIndex = 0)
            advanceUntilIdle()

            systemControls.callbacks.skipNext()
            advanceUntilIdle()
            assertEquals("track-2", repository.snapshot.value.currentTrack?.id)

            systemControls.callbacks.seekTo(12_345L)
            advanceUntilIdle()
            assertEquals(12_345L, gateway.seekCalls.last())

            systemControls.callbacks.pause()
            advanceUntilIdle()
            assertEquals(false, repository.snapshot.value.isPlaying)

            systemControls.callbacks.play()
            advanceUntilIdle()
            assertEquals(true, repository.snapshot.value.isPlaying)
        } finally {
            repository.close()
            scope.cancel()
            database.close()
        }
    }
}

private fun createTestDatabase(): LynMusicDatabase {
    val path = Files.createTempFile("lynmusic-playback", ".db")
    return buildLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
    )
}

private fun sampleTracks(): List<Track> {
    return listOf(
        sampleTrack("track-1", "First Song"),
        sampleTrack("track-2", "Second Song"),
        sampleTrack("track-3", "Third Song"),
    )
}

private fun sampleTrack(id: String, title: String): Track {
    return Track(
        id = id,
        sourceId = "local-1",
        title = title,
        artistName = "Artist A",
        albumTitle = "Album A",
        durationMs = 180_000L,
        mediaLocator = "file:///music/$id.mp3",
        relativePath = "$title.mp3",
    )
}

private fun sampleTrackEntity(id: String, title: String): TrackEntity {
    return TrackEntity(
        id = id,
        sourceId = "local-1",
        title = title,
        artistId = null,
        artistName = "Artist A",
        albumId = null,
        albumTitle = "Album A",
        durationMs = 180_000L,
        trackNumber = null,
        discNumber = null,
        mediaLocator = "file:///music/$id.mp3",
        relativePath = "$title.mp3",
        artworkLocator = null,
        sizeBytes = 0L,
        modifiedAt = 0L,
    )
}

private class FakePlaybackGateway(
    private val loadFailure: Throwable? = null,
) : PlaybackGateway {
    private val mutableState = MutableStateFlow(PlaybackGatewayState())

    val loadCalls = mutableListOf<LoadCall>()
    val seekCalls = mutableListOf<Long>()

    override val state: StateFlow<PlaybackGatewayState> = mutableState.asStateFlow()

    override suspend fun load(track: Track, playWhenReady: Boolean, startPositionMs: Long) {
        loadFailure?.let { throwable ->
            mutableState.value = mutableState.value.copy(
                isPlaying = false,
                positionMs = startPositionMs,
                durationMs = 0L,
                errorMessage = "访问歌曲失败：${throwable.message ?: throwable::class.simpleName.orEmpty()}",
            )
            throw throwable
        }
        loadCalls += LoadCall(track, playWhenReady, startPositionMs)
        mutableState.value = mutableState.value.copy(
            isPlaying = playWhenReady,
            positionMs = startPositionMs,
            durationMs = track.durationMs,
            errorMessage = null,
        )
    }

    override suspend fun play() {
        mutableState.value = mutableState.value.copy(isPlaying = true)
    }

    override suspend fun pause() {
        mutableState.value = mutableState.value.copy(isPlaying = false)
    }

    override suspend fun seekTo(positionMs: Long) {
        seekCalls += positionMs
        mutableState.value = mutableState.value.copy(positionMs = positionMs)
    }

    override suspend fun setVolume(volume: Float) {
        mutableState.value = mutableState.value.copy(volume = volume)
    }

    override suspend fun release() = Unit

    fun updateState(transform: (PlaybackGatewayState) -> PlaybackGatewayState) {
        mutableState.value = transform(mutableState.value)
    }

    fun emitCompletion() {
        mutableState.value = mutableState.value.copy(
            completionCount = mutableState.value.completionCount + 1,
        )
    }
}

private class FakeSystemPlaybackControlsPlatformService : SystemPlaybackControlsPlatformService {
    var callbacks: SystemPlaybackControlCallbacks = SystemPlaybackControlCallbacks()
    var lastSnapshot: PlaybackSnapshot? = null

    override fun bind(callbacks: SystemPlaybackControlCallbacks) {
        this.callbacks = callbacks
    }

    override suspend fun updateSnapshot(snapshot: PlaybackSnapshot) {
        lastSnapshot = snapshot
    }

    override suspend fun close() = Unit
}

private data class LoadCall(
    val track: Track,
    val playWhenReady: Boolean,
    val startPositionMs: Long,
)
