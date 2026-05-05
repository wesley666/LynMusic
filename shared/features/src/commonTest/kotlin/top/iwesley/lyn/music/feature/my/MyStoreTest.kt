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
            dailyRecommendation = listOf(sampleTrack("daily-1")),
        )
        val store = createStore(repository, testScheduler, startImmediately = false)

        store.ensureStarted()
        advanceUntilIdle()

        assertEquals(1, repository.refreshCalls)
        assertEquals(1, repository.ensureDailyRecommendationCalls)
        assertEquals(listOf("track-1"), store.state.value.recentTracks.map { it.track.id })
        assertEquals(listOf("album-1"), store.state.value.recentAlbums.map { it.album.id })
        assertEquals(listOf("daily-1"), store.state.value.dailyRecommendationTracks.map { it.id })
        assertEquals(false, store.state.value.isLoadingContent)
        assertEquals(false, store.state.value.isGeneratingDailyRecommendation)
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

    @Test
    fun `daily recommendation failure does not block recent content`() = runTest {
        val repository = FakeMyRepository(
            tracks = listOf(sampleRecentTrack()),
            ensureDailyRecommendationResult = Result.failure(IllegalStateException("推荐失败")),
        )
        val store = createStore(repository, testScheduler, startImmediately = false)

        store.ensureStarted()
        advanceUntilIdle()

        assertEquals(listOf("track-1"), store.state.value.recentTracks.map { it.track.id })
        assertEquals(false, store.state.value.isGeneratingDailyRecommendation)
    }

    @Test
    fun `date key change triggers daily recommendation generation again`() = runTest {
        val repository = FakeMyRepository()
        val store = createStore(repository, testScheduler, startImmediately = false)

        store.ensureStarted()
        advanceUntilIdle()
        repository.setDateKey("2026-05-02")
        advanceUntilIdle()

        assertEquals(2, repository.ensureDailyRecommendationCalls)
    }

    @Test
    fun `same date key does not trigger duplicate daily recommendation generation`() = runTest {
        val repository = FakeMyRepository()
        val store = createStore(repository, testScheduler, startImmediately = false)

        store.ensureStarted()
        advanceUntilIdle()
        repository.setDateKey("2026-05-01")
        advanceUntilIdle()

        assertEquals(1, repository.ensureDailyRecommendationCalls)
    }

    @Test
    fun `re-entering my tab refreshes current date key after store already started`() = runTest {
        val repository = FakeMyRepository()
        val store = createStore(repository, testScheduler, startImmediately = false)

        store.ensureStarted()
        advanceUntilIdle()
        repository.currentDateKey = "2026-05-02"
        store.ensureStarted()
        advanceUntilIdle()

        assertEquals(2, repository.dateKeyRefreshCalls)
        assertEquals(2, repository.ensureDailyRecommendationCalls)
    }

    @Test
    fun `candidate songs becoming available retries empty daily recommendation`() = runTest {
        val repository = FakeMyRepository(hasDailyRecommendationCandidates = false)
        val store = createStore(repository, testScheduler, startImmediately = false)

        store.ensureStarted()
        advanceUntilIdle()
        repository.setHasDailyRecommendationCandidates(true)
        advanceUntilIdle()

        assertEquals(2, repository.ensureDailyRecommendationCalls)
    }

    @Test
    fun `candidate songs becoming available does not retry existing daily recommendation`() = runTest {
        val repository = FakeMyRepository(
            dailyRecommendation = listOf(sampleTrack("daily-1")),
            hasDailyRecommendationCandidates = false,
        )
        val store = createStore(repository, testScheduler, startImmediately = false)

        store.ensureStarted()
        advanceUntilIdle()
        repository.setHasDailyRecommendationCandidates(true)
        advanceUntilIdle()

        assertEquals(1, repository.ensureDailyRecommendationCalls)
    }

    @Test
    fun `daily recommendation becoming empty retries when candidates still exist`() = runTest {
        val repository = FakeMyRepository(
            dailyRecommendation = listOf(sampleTrack("daily-1")),
            hasDailyRecommendationCandidates = true,
        )
        val store = createStore(repository, testScheduler, startImmediately = false)

        store.ensureStarted()
        advanceUntilIdle()
        repository.setDailyRecommendation(emptyList())
        advanceUntilIdle()

        assertEquals(2, repository.ensureDailyRecommendationCalls)
    }

    @Test
    fun `daily recommendation becoming empty does not retry without candidates`() = runTest {
        val repository = FakeMyRepository(
            dailyRecommendation = listOf(sampleTrack("daily-1")),
            hasDailyRecommendationCandidates = false,
        )
        val store = createStore(repository, testScheduler, startImmediately = false)

        store.ensureStarted()
        advanceUntilIdle()
        repository.setDailyRecommendation(emptyList())
        advanceUntilIdle()

        assertEquals(1, repository.ensureDailyRecommendationCalls)
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
    dailyRecommendation: List<Track> = emptyList(),
    private val refreshResult: Result<Unit> = Result.success(Unit),
    private val ensureDailyRecommendationResult: Result<Unit> = Result.success(Unit),
    hasDailyRecommendationCandidates: Boolean = false,
) : MyRepository {
    private val mutableTracks = MutableStateFlow(tracks)
    private val mutableAlbums = MutableStateFlow(albums)
    private val mutableDateKey = MutableStateFlow("2026-05-01")
    private val mutableDailyRecommendation = MutableStateFlow(dailyRecommendation)
    private val mutableHasDailyRecommendationCandidates = MutableStateFlow(hasDailyRecommendationCandidates)
    var currentDateKey: String = "2026-05-01"
    var refreshCalls: Int = 0
        private set
    var ensureDailyRecommendationCalls: Int = 0
        private set
    var dateKeyRefreshCalls: Int = 0
        private set

    override val recentTracks: Flow<List<RecentTrack>> = mutableTracks.asStateFlow()
    override val recentAlbums: Flow<List<RecentAlbum>> = mutableAlbums.asStateFlow()
    override val dailyRecommendationDateKey: Flow<String> = mutableDateKey.asStateFlow()
    override val dailyRecommendation: Flow<List<Track>> = mutableDailyRecommendation.asStateFlow()
    override val hasDailyRecommendationCandidates: Flow<Boolean> = mutableHasDailyRecommendationCandidates.asStateFlow()

    fun setDateKey(dateKey: String) {
        mutableDateKey.value = dateKey
    }

    fun setHasDailyRecommendationCandidates(value: Boolean) {
        mutableHasDailyRecommendationCandidates.value = value
    }

    fun setDailyRecommendation(value: List<Track>) {
        mutableDailyRecommendation.value = value
    }

    override fun refreshDailyRecommendationDateKey() {
        dateKeyRefreshCalls += 1
        mutableDateKey.value = currentDateKey
    }

    override suspend fun refreshNavidromeRecentPlays(): Result<Unit> {
        refreshCalls += 1
        return refreshResult
    }

    override suspend fun ensureDailyRecommendation(): Result<Unit> {
        ensureDailyRecommendationCalls += 1
        return ensureDailyRecommendationResult
    }
}

private fun sampleRecentTrack(): RecentTrack {
    return RecentTrack(
        track = sampleTrack("track-1"),
        playCount = 2,
        lastPlayedAt = 200L,
    )
}

private fun sampleTrack(id: String): Track {
    return Track(
        id = id,
        sourceId = "local-1",
        title = "Blue",
        artistName = "Artist A",
        albumTitle = "Album A",
        durationMs = 180_000L,
        mediaLocator = "file:///music/Blue.flac",
        relativePath = "Artist A/Album A/Blue.flac",
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
