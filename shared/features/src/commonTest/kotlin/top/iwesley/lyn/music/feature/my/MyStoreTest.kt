package top.iwesley.lyn.music.feature.my

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.RecentAlbum
import top.iwesley.lyn.music.core.model.RecentTrack
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.data.repository.MyRepository

@OptIn(ExperimentalCoroutinesApi::class)
class MyStoreTest {

    @Test
    fun `ensure started collects recent content and triggers one navidrome refresh`() = runTest {
        val repository = FakeMyRepository(
            tracks = listOf(sampleRecentTrack()),
            albums = listOf(sampleRecentAlbum()),
        )
        val store = createStore(repository, testScheduler, startImmediately = false)

        store.ensureStarted()
        advanceUntilIdle()

        assertEquals(1, repository.refreshCalls)
        assertEquals(listOf("track-1"), store.state.value.recentTracks.map { it.track.id })
        assertEquals(listOf("album-1"), store.state.value.recentAlbums.map { it.album.id })
        assertEquals(false, store.state.value.isLoadingContent)
        assertEquals(false, store.state.value.isRefreshingNavidrome)
    }

    @Test
    fun `manual refresh can run after initial refresh`() = runTest {
        val repository = FakeMyRepository()
        val store = createStore(repository, testScheduler, startImmediately = false)

        store.ensureStarted()
        advanceUntilIdle()
        store.dispatch(MyIntent.RefreshNavidromeRecentPlays)
        advanceUntilIdle()

        assertEquals(2, repository.refreshCalls)
        assertNull(store.state.value.message)
    }

    @Test
    fun `refresh failure exposes message and can be cleared`() = runTest {
        val repository = FakeMyRepository(
            refreshResult = Result.failure(IllegalStateException("同步失败")),
        )
        val store = createStore(repository, testScheduler, startImmediately = false)

        store.ensureStarted()
        advanceUntilIdle()

        assertEquals("同步失败", store.state.value.message)
        assertEquals(false, store.state.value.isRefreshingNavidrome)

        store.dispatch(MyIntent.ClearMessage)
        advanceUntilIdle()

        assertNull(store.state.value.message)
    }

    private fun createStore(
        repository: FakeMyRepository,
        scheduler: TestCoroutineScheduler,
        startImmediately: Boolean = true,
    ): MyStore {
        val dispatcher = StandardTestDispatcher(scheduler)
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        return MyStore(
            repository = repository,
            storeScope = scope,
            startImmediately = startImmediately,
        )
    }
}

private class FakeMyRepository(
    tracks: List<RecentTrack> = emptyList(),
    albums: List<RecentAlbum> = emptyList(),
    private val refreshResult: Result<Unit> = Result.success(Unit),
) : MyRepository {
    private val mutableTracks = MutableStateFlow(tracks)
    private val mutableAlbums = MutableStateFlow(albums)
    var refreshCalls: Int = 0
        private set

    override val recentTracks: Flow<List<RecentTrack>> = mutableTracks.asStateFlow()
    override val recentAlbums: Flow<List<RecentAlbum>> = mutableAlbums.asStateFlow()

    override suspend fun refreshNavidromeRecentPlays(): Result<Unit> {
        refreshCalls += 1
        return refreshResult
    }
}

private fun sampleRecentTrack(): RecentTrack {
    return RecentTrack(
        track = Track(
            id = "track-1",
            sourceId = "local-1",
            title = "Blue",
            artistName = "Artist A",
            albumTitle = "Album A",
            durationMs = 180_000L,
            mediaLocator = "file:///music/Blue.flac",
            relativePath = "Artist A/Album A/Blue.flac",
        ),
        playCount = 2,
        lastPlayedAt = 200L,
    )
}

private fun sampleRecentAlbum(): RecentAlbum {
    return RecentAlbum(
        album = Album(
            id = "album-1",
            title = "Album A",
            artistName = "Artist A",
            trackCount = 1,
        ),
        playCount = 2,
        lastPlayedAt = 200L,
    )
}
