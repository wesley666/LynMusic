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
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.data.db.LynMusicDatabase
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

private class FakePlaybackGateway : PlaybackGateway {
    private val mutableState = MutableStateFlow(PlaybackGatewayState())

    val loadCalls = mutableListOf<LoadCall>()
    val seekCalls = mutableListOf<Long>()

    override val state: StateFlow<PlaybackGatewayState> = mutableState.asStateFlow()

    override suspend fun load(track: Track, playWhenReady: Boolean, startPositionMs: Long) {
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

private data class LoadCall(
    val track: Track,
    val playWhenReady: Boolean,
    val startPositionMs: Long,
)
