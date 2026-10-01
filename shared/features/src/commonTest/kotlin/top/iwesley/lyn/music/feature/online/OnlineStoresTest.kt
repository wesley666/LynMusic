package top.iwesley.lyn.music.feature.online

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import top.iwesley.lyn.music.testing.assertLocalizedEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.ImportScanSummary
import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NavidromeLibraryProbe
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.PlaylistDetail
import top.iwesley.lyn.music.core.model.PlaylistKind
import top.iwesley.lyn.music.core.model.PlaylistSummary
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.data.repository.ImportSourceRepository
import top.iwesley.lyn.music.data.repository.NavidromeOnlineDataSource
import top.iwesley.lyn.music.data.repository.OnlineAlbumItem
import top.iwesley.lyn.music.data.repository.OnlineArtistItem
import top.iwesley.lyn.music.data.repository.OnlineLibrarySearchResult
import top.iwesley.lyn.music.data.repository.OnlinePage
import top.iwesley.lyn.music.data.repository.PlaylistImportReport
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.LibrarySourceFilterPreferencesStore
import top.iwesley.lyn.music.feature.library.TrackSortMode

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineStoresTest {
    @Test
    fun `online library restore ignores cancellation`() = runTest {
        val repository = FakeNavidromeOnlineDataSource(
            libraryFailure = CancellationException("StandaloneCoroutine was cancelled"),
        )
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlineLibrarySourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlineLibraryStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()

        assertLocalizedEquals(ONLINE_SOURCE_ID, store.state.value.sourceId)
        assertFalse(store.state.value.isLoading)
        assertNull(store.state.value.errorMessage)
        assertLocalizedEquals(1, repository.trackCalls)
        scope.cancel()
    }

    @Test
    fun `online favorites restore ignores cancellation`() = runTest {
        val repository = FakeNavidromeOnlineDataSource(
            favoritesFailure = CancellationException("StandaloneCoroutine was cancelled"),
        )
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlineFavoritesSourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlineFavoritesStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()

        assertLocalizedEquals(ONLINE_SOURCE_ID, store.state.value.sourceId)
        assertFalse(store.state.value.isLoading)
        assertNull(store.state.value.errorMessage)
        assertLocalizedEquals(1, repository.favoriteCalls)
        scope.cancel()
    }

    @Test
    fun `online playlists restore ignores cancellation`() = runTest {
        val repository = FakeNavidromeOnlineDataSource(
            playlistsFailure = CancellationException("StandaloneCoroutine was cancelled"),
        )
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlinePlaylistsSourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlinePlaylistsStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()

        assertLocalizedEquals(ONLINE_SOURCE_ID, store.state.value.sourceId)
        assertFalse(store.state.value.isLoading)
        assertNull(store.state.value.errorMessage)
        assertLocalizedEquals(1, repository.playlistCalls)
        scope.cancel()
    }

    @Test
    fun `online library remembered source is idle until store is started`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val importSourceRepository = FakeImportSourceRepository()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlineLibrarySourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlineLibraryStore(
            repository = repository,
            importSourceRepository = importSourceRepository,
            preferencesStore = preferencesStore,
            storeScope = scope,
            startImmediately = false,
        )

        advanceUntilIdle()

        assertNull(store.state.value.sourceId)
        assertLocalizedEquals(0, importSourceRepository.observeSourcesCalls)
        assertLocalizedEquals(0, repository.trackCalls)
        assertLocalizedEquals(0, repository.albumCalls)
        assertLocalizedEquals(0, repository.artistCalls)

        store.ensureStartedIfRememberedSource()
        advanceUntilIdle()

        assertLocalizedEquals(ONLINE_SOURCE_ID, store.state.value.sourceId)
        assertLocalizedEquals(1, importSourceRepository.observeSourcesCalls)
        assertLocalizedEquals(1, repository.trackCalls)
        assertLocalizedEquals(1, repository.albumCalls)
        assertLocalizedEquals(1, repository.artistCalls)

        store.ensureStartedIfRememberedSource()
        advanceUntilIdle()

        assertLocalizedEquals(1, importSourceRepository.observeSourcesCalls)
        assertLocalizedEquals(1, repository.trackCalls)
        assertLocalizedEquals(1, repository.albumCalls)
        assertLocalizedEquals(1, repository.artistCalls)
        scope.cancel()
    }

    @Test
    fun `online favorites remembered source is idle until store is started`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val importSourceRepository = FakeImportSourceRepository()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlineFavoritesSourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlineFavoritesStore(
            repository = repository,
            importSourceRepository = importSourceRepository,
            preferencesStore = preferencesStore,
            storeScope = scope,
            startImmediately = false,
        )

        advanceUntilIdle()

        assertNull(store.state.value.sourceId)
        assertLocalizedEquals(0, importSourceRepository.observeSourcesCalls)
        assertLocalizedEquals(0, repository.favoriteCalls)

        store.ensureStartedIfRememberedSource()
        advanceUntilIdle()

        assertLocalizedEquals(ONLINE_SOURCE_ID, store.state.value.sourceId)
        assertLocalizedEquals(1, importSourceRepository.observeSourcesCalls)
        assertLocalizedEquals(1, repository.favoriteCalls)

        store.ensureStartedIfRememberedSource()
        advanceUntilIdle()

        assertLocalizedEquals(1, importSourceRepository.observeSourcesCalls)
        assertLocalizedEquals(1, repository.favoriteCalls)
        scope.cancel()
    }

    @Test
    fun `online playlists remembered source is idle until store is started`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val importSourceRepository = FakeImportSourceRepository()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlinePlaylistsSourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlinePlaylistsStore(
            repository = repository,
            importSourceRepository = importSourceRepository,
            preferencesStore = preferencesStore,
            storeScope = scope,
            startImmediately = false,
        )

        advanceUntilIdle()

        assertNull(store.state.value.sourceId)
        assertLocalizedEquals(0, importSourceRepository.observeSourcesCalls)
        assertLocalizedEquals(0, repository.playlistCalls)

        store.ensureStartedIfRememberedSource()
        advanceUntilIdle()

        assertLocalizedEquals(ONLINE_SOURCE_ID, store.state.value.sourceId)
        assertLocalizedEquals(1, importSourceRepository.observeSourcesCalls)
        assertLocalizedEquals(1, repository.playlistCalls)

        store.ensureStartedIfRememberedSource()
        advanceUntilIdle()

        assertLocalizedEquals(1, importSourceRepository.observeSourcesCalls)
        assertLocalizedEquals(1, repository.playlistCalls)
        scope.cancel()
    }

    @Test
    fun `conditional online store start ignores empty remembered sources`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val librarySources = FakeImportSourceRepository()
        val favoritesSources = FakeImportSourceRepository()
        val playlistsSources = FakeImportSourceRepository()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = testScope(testScheduler)
        val libraryStore = OnlineLibraryStore(
            repository = repository,
            importSourceRepository = librarySources,
            preferencesStore = preferencesStore,
            storeScope = scope,
            startImmediately = false,
        )
        val favoritesStore = OnlineFavoritesStore(
            repository = repository,
            importSourceRepository = favoritesSources,
            preferencesStore = preferencesStore,
            storeScope = scope,
            startImmediately = false,
        )
        val playlistsStore = OnlinePlaylistsStore(
            repository = repository,
            importSourceRepository = playlistsSources,
            preferencesStore = preferencesStore,
            storeScope = scope,
            startImmediately = false,
        )

        libraryStore.ensureStartedIfRememberedSource()
        favoritesStore.ensureStartedIfRememberedSource()
        playlistsStore.ensureStartedIfRememberedSource()
        advanceUntilIdle()

        assertLocalizedEquals(0, librarySources.observeSourcesCalls)
        assertLocalizedEquals(0, favoritesSources.observeSourcesCalls)
        assertLocalizedEquals(0, playlistsSources.observeSourcesCalls)
        assertLocalizedEquals(0, repository.trackCalls)
        assertLocalizedEquals(0, repository.favoriteCalls)
        assertLocalizedEquals(0, repository.playlistCalls)
        scope.cancel()
    }

    @Test
    fun `clearing remembered online sources does not start online stores`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val librarySources = FakeImportSourceRepository()
        val favoritesSources = FakeImportSourceRepository()
        val playlistsSources = FakeImportSourceRepository()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(
            onlineLibrarySourceId = ONLINE_SOURCE_ID,
            onlineFavoritesSourceId = ONLINE_SOURCE_ID,
            onlinePlaylistsSourceId = ONLINE_SOURCE_ID,
        )
        val scope = testScope(testScheduler)
        val libraryStore = OnlineLibraryStore(
            repository = repository,
            importSourceRepository = librarySources,
            preferencesStore = preferencesStore,
            storeScope = scope,
            startImmediately = false,
        )
        val favoritesStore = OnlineFavoritesStore(
            repository = repository,
            importSourceRepository = favoritesSources,
            preferencesStore = preferencesStore,
            storeScope = scope,
            startImmediately = false,
        )
        val playlistsStore = OnlinePlaylistsStore(
            repository = repository,
            importSourceRepository = playlistsSources,
            preferencesStore = preferencesStore,
            storeScope = scope,
            startImmediately = false,
        )

        libraryStore.clearRememberedSource()
        favoritesStore.clearRememberedSource()
        playlistsStore.clearRememberedSource()
        advanceUntilIdle()

        assertNull(preferencesStore.onlineLibrarySourceId.value)
        assertNull(preferencesStore.onlineFavoritesSourceId.value)
        assertNull(preferencesStore.onlinePlaylistsSourceId.value)
        assertLocalizedEquals(0, librarySources.observeSourcesCalls)
        assertLocalizedEquals(0, favoritesSources.observeSourcesCalls)
        assertLocalizedEquals(0, playlistsSources.observeSourcesCalls)
        assertLocalizedEquals(0, repository.trackCalls)
        assertLocalizedEquals(0, repository.favoriteCalls)
        assertLocalizedEquals(0, repository.playlistCalls)
        scope.cancel()
    }

    @Test
    fun `online playlist failure clears stale success message`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlinePlaylistsSourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlinePlaylistsStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        store.dispatch(OnlinePlaylistsIntent.CreatePlaylist("New"))
        advanceUntilIdle()

        assertLocalizedEquals("playlist_created", store.state.value.message)
        assertNull(store.state.value.errorMessage)

        repository.playlistsFailure = IllegalStateException("load failed")
        store.dispatch(OnlinePlaylistsIntent.Refresh)
        advanceUntilIdle()

        assertNull(store.state.value.message)
        assertLocalizedEquals("在线歌单加载失败。\nload failed", store.state.value.errorMessage)
        scope.cancel()
    }

    @Test
    fun `online playlist success clears stale error message`() = runTest {
        val repository = FakeNavidromeOnlineDataSource(
            playlistsFailure = IllegalStateException("load failed"),
        )
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlinePlaylistsSourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlinePlaylistsStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()

        assertNull(store.state.value.message)
        assertLocalizedEquals("在线歌单加载失败。\nload failed", store.state.value.errorMessage)

        repository.playlistsFailure = null
        store.dispatch(OnlinePlaylistsIntent.CreatePlaylist("New"))
        advanceUntilIdle()

        assertLocalizedEquals("playlist_created", store.state.value.message)
        assertNull(store.state.value.errorMessage)
        scope.cancel()
    }

    @Test
    fun `online playlist add uses explicit source when current source was restored`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = testScope(testScheduler)
        val store = OnlinePlaylistsStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        assertNull(store.state.value.sourceId)

        store.dispatch(
            OnlinePlaylistsIntent.AddTrack(
                playlistId = "playlist-1",
                track = sampleTrack(),
                sourceId = ONLINE_SOURCE_ID,
            ),
        )
        advanceUntilIdle()

        assertNull(store.state.value.sourceId)
        assertLocalizedEquals(listOf(ONLINE_SOURCE_ID to "playlist-1"), repository.addTrackCalls)
        assertLocalizedEquals("playlist_track_added", store.state.value.message)
        assertNull(store.state.value.errorMessage)
        scope.cancel()
    }

    @Test
    fun `online playlists temporary source clear keeps remembered source`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val importSourceRepository = FakeImportSourceRepository()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlinePlaylistsSourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlinePlaylistsStore(
            repository = repository,
            importSourceRepository = importSourceRepository,
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        store.dispatch(OnlinePlaylistsIntent.SelectSource(sourceId = null, persist = false))
        importSourceRepository.emitSources(listOf(onlineSource(), onlineSource("nav-other")))
        advanceUntilIdle()

        assertLocalizedEquals(ONLINE_SOURCE_ID, preferencesStore.onlinePlaylistsSourceId.value)
        assertNull(store.state.value.sourceId)
        scope.cancel()
    }

    @Test
    fun `online playlists persistent source clear removes remembered source and does not restore`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val importSourceRepository = FakeImportSourceRepository()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlinePlaylistsSourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlinePlaylistsStore(
            repository = repository,
            importSourceRepository = importSourceRepository,
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        assertLocalizedEquals(ONLINE_SOURCE_ID, store.state.value.sourceId)

        store.dispatch(OnlinePlaylistsIntent.SelectSource(sourceId = null))
        importSourceRepository.emitSources(listOf(onlineSource(), onlineSource("nav-other")))
        advanceUntilIdle()

        assertNull(preferencesStore.onlinePlaylistsSourceId.value)
        assertNull(store.state.value.sourceId)
        scope.cancel()

        val restartedScope = testScope(testScheduler)
        val restartedStore = OnlinePlaylistsStore(
            repository = repository,
            importSourceRepository = importSourceRepository,
            preferencesStore = preferencesStore,
            storeScope = restartedScope,
        )

        advanceUntilIdle()
        assertNull(restartedStore.state.value.sourceId)
        restartedScope.cancel()
    }

    @Test
    fun `online library restore still reports real failures`() = runTest {
        val repository = FakeNavidromeOnlineDataSource(
            libraryFailure = IllegalStateException("boom"),
        )
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlineLibrarySourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlineLibraryStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()

        assertFalse(store.state.value.isLoading)
        assertLocalizedEquals("在线曲库加载失败。\nboom", store.state.value.errorMessage)
        scope.cancel()
    }

    @Test
    fun `online library source selection persists and loads`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = testScope(testScheduler)
        val store = OnlineLibraryStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        store.dispatch(OnlineLibraryIntent.SelectSource(ONLINE_SOURCE_ID))
        advanceUntilIdle()

        assertLocalizedEquals(ONLINE_SOURCE_ID, preferencesStore.onlineLibrarySourceId.value)
        assertLocalizedEquals(ONLINE_SOURCE_ID, store.state.value.sourceId)
        assertLocalizedEquals(listOf("track-1"), store.state.value.tracks.map { it.id })
        assertLocalizedEquals(1, repository.trackCalls)
        scope.cancel()
    }

    @Test
    fun `online library root refresh starts tracks albums and artists concurrently`() = runTest {
        val tracksStarted = CompletableDeferred<Unit>()
        val albumsStarted = CompletableDeferred<Unit>()
        val artistsStarted = CompletableDeferred<Unit>()
        val releaseRequests = CompletableDeferred<Unit>()
        val repository = FakeNavidromeOnlineDataSource(
            tracksHandler = { offset, limit ->
                tracksStarted.complete(Unit)
                releaseRequests.await()
                OnlinePage(listOf(sampleTrack()), totalCount = 1, offset = offset, limit = limit)
            },
            albumsHandler = { offset, limit ->
                albumsStarted.complete(Unit)
                releaseRequests.await()
                OnlinePage(emptyList(), totalCount = 0, offset = offset, limit = limit)
            },
            artistsHandler = { offset, limit ->
                artistsStarted.complete(Unit)
                releaseRequests.await()
                OnlinePage(emptyList(), totalCount = 0, offset = offset, limit = limit)
            },
        )
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlineLibrarySourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlineLibraryStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        runCurrent()

        assertTrue(tracksStarted.isCompleted)
        assertTrue(albumsStarted.isCompleted)
        assertTrue(artistsStarted.isCompleted)

        releaseRequests.complete(Unit)
        advanceUntilIdle()

        assertLocalizedEquals(listOf("track-1"), store.state.value.tracks.map { it.id })
        assertFalse(store.state.value.isLoading)
        scope.cancel()
    }

    @Test
    fun `online library temporary source selection does not persist`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = testScope(testScheduler)
        val store = OnlineLibraryStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        store.dispatch(OnlineLibraryIntent.SelectSource(ONLINE_SOURCE_ID, persist = false))
        advanceUntilIdle()

        assertNull(preferencesStore.onlineLibrarySourceId.value)
        assertLocalizedEquals(ONLINE_SOURCE_ID, store.state.value.sourceId)
        assertLocalizedEquals(listOf("track-1"), store.state.value.tracks.map { it.id })
        scope.cancel()
    }

    @Test
    fun `online library temporary source clear keeps remembered source`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val importSourceRepository = FakeImportSourceRepository()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlineLibrarySourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlineLibraryStore(
            repository = repository,
            importSourceRepository = importSourceRepository,
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        store.dispatch(OnlineLibraryIntent.SelectSource(sourceId = null, persist = false))
        importSourceRepository.emitSources(listOf(onlineSource(), onlineSource("nav-other")))
        advanceUntilIdle()

        assertLocalizedEquals(ONLINE_SOURCE_ID, preferencesStore.onlineLibrarySourceId.value)
        assertNull(store.state.value.sourceId)
        scope.cancel()
    }

    @Test
    fun `online library persistent source clear removes remembered source and does not restore`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val importSourceRepository = FakeImportSourceRepository()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlineLibrarySourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlineLibraryStore(
            repository = repository,
            importSourceRepository = importSourceRepository,
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        assertLocalizedEquals(ONLINE_SOURCE_ID, store.state.value.sourceId)

        store.dispatch(OnlineLibraryIntent.SelectSource(sourceId = null))
        importSourceRepository.emitSources(listOf(onlineSource(), onlineSource("nav-other")))
        advanceUntilIdle()

        assertNull(preferencesStore.onlineLibrarySourceId.value)
        assertNull(store.state.value.sourceId)
        scope.cancel()

        val restartedScope = testScope(testScheduler)
        val restartedStore = OnlineLibraryStore(
            repository = repository,
            importSourceRepository = importSourceRepository,
            preferencesStore = preferencesStore,
            storeScope = restartedScope,
        )

        advanceUntilIdle()
        assertNull(restartedStore.state.value.sourceId)
        restartedScope.cancel()
    }

    @Test
    fun `online library playback navigation does not persist source`() = runTest {
        val navigationSourceId = "nav-online-2"
        val repository = FakeNavidromeOnlineDataSource()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlineLibrarySourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlineLibraryStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(
                initialSources = listOf(onlineSource(ONLINE_SOURCE_ID), onlineSource(navigationSourceId)),
            ),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        store.dispatch(
            OnlineLibraryIntent.PrepareAlbumNavigation(
                sourceId = navigationSourceId,
                albumId = "album-2",
                albumTitle = "Album 2",
                artistName = "Artist",
                artworkLocator = null,
            ),
        )
        advanceUntilIdle()

        assertLocalizedEquals(ONLINE_SOURCE_ID, preferencesStore.onlineLibrarySourceId.value)
        assertLocalizedEquals(navigationSourceId, store.state.value.sourceId)
        assertLocalizedEquals("Album 2", store.state.value.knownAlbumItemsById.getValue("album-2").album.title)
        scope.cancel()
    }

    @Test
    fun `clearing online library search restores root snapshot without reloading root pages`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlineLibrarySourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlineLibraryStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        store.dispatch(OnlineLibraryIntent.SearchChanged("search"))
        advanceUntilIdle()

        assertLocalizedEquals(listOf("search-track"), store.state.value.tracks.map { it.id })
        assertLocalizedEquals(1, repository.searchCalls)

        store.dispatch(OnlineLibraryIntent.SearchChanged(""))
        advanceUntilIdle()

        assertLocalizedEquals(listOf("track-1"), store.state.value.tracks.map { it.id })
        assertLocalizedEquals(1, repository.trackCalls)
        assertLocalizedEquals(1, repository.albumCalls)
        assertLocalizedEquals(1, repository.artistCalls)
        assertLocalizedEquals(1, repository.searchCalls)
        scope.cancel()
    }

    @Test
    fun `clearing online library search restores loaded root pagination`() = runTest {
        val repository = FakeNavidromeOnlineDataSource(
            tracksHandler = { offset, limit ->
                when (offset) {
                    0 -> OnlinePage(listOf(sampleTrack(id = "track-1")), totalCount = 2, offset = offset, limit = limit)
                    1 -> OnlinePage(listOf(sampleTrack(id = "track-2")), totalCount = 2, offset = offset, limit = limit)
                    else -> OnlinePage(emptyList(), totalCount = 2, offset = offset, limit = limit)
                }
            },
        )
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlineLibrarySourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlineLibraryStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        store.dispatch(OnlineLibraryIntent.LoadMoreTracks)
        advanceUntilIdle()
        store.dispatch(OnlineLibraryIntent.SearchChanged("search"))
        advanceUntilIdle()
        store.dispatch(OnlineLibraryIntent.SearchChanged(""))
        advanceUntilIdle()

        assertLocalizedEquals(listOf("track-1", "track-2"), store.state.value.tracks.map { it.id })
        assertLocalizedEquals(2, repository.trackCalls)
        assertFalse(store.state.value.canLoadMoreTracks)
        scope.cancel()
    }

    @Test
    fun `late online library search does not overwrite restored root snapshot`() = runTest {
        val searchResult = CompletableDeferred<OnlineLibrarySearchResult>()
        val repository = FakeNavidromeOnlineDataSource(
            searchHandler = { _, _, _ -> searchResult.await() },
        )
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlineLibrarySourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlineLibraryStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        store.dispatch(OnlineLibraryIntent.SearchChanged("slow"))
        advanceTimeBy(300)
        runCurrent()
        store.dispatch(OnlineLibraryIntent.SearchChanged(""))
        advanceUntilIdle()

        assertLocalizedEquals(listOf("track-1"), store.state.value.tracks.map { it.id })
        searchResult.complete(OnlineLibrarySearchResult(tracks = listOf(sampleTrack(id = "late-search"))))
        advanceUntilIdle()

        assertLocalizedEquals(listOf("track-1"), store.state.value.tracks.map { it.id })
        assertLocalizedEquals(1, repository.trackCalls)
        assertLocalizedEquals(1, repository.searchCalls)
        scope.cancel()
    }

    @Test
    fun `online favorites temporary source selection does not persist`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = testScope(testScheduler)
        val store = OnlineFavoritesStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        store.dispatch(OnlineFavoritesIntent.SelectSource(ONLINE_SOURCE_ID, persist = false))
        advanceUntilIdle()

        assertNull(preferencesStore.onlineFavoritesSourceId.value)
        assertLocalizedEquals(ONLINE_SOURCE_ID, store.state.value.sourceId)
        assertLocalizedEquals(listOf("track-1"), store.state.value.tracks.map { it.id })
        scope.cancel()
    }

    @Test
    fun `online favorites temporary source clear keeps remembered source`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val importSourceRepository = FakeImportSourceRepository()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlineFavoritesSourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlineFavoritesStore(
            repository = repository,
            importSourceRepository = importSourceRepository,
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        store.dispatch(OnlineFavoritesIntent.SelectSource(sourceId = null, persist = false))
        importSourceRepository.emitSources(listOf(onlineSource(), onlineSource("nav-other")))
        advanceUntilIdle()

        assertLocalizedEquals(ONLINE_SOURCE_ID, preferencesStore.onlineFavoritesSourceId.value)
        assertNull(store.state.value.sourceId)
        scope.cancel()
    }

    @Test
    fun `online favorites persistent source clear removes remembered source and does not restore`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val importSourceRepository = FakeImportSourceRepository()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlineFavoritesSourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlineFavoritesStore(
            repository = repository,
            importSourceRepository = importSourceRepository,
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        assertLocalizedEquals(ONLINE_SOURCE_ID, store.state.value.sourceId)

        store.dispatch(OnlineFavoritesIntent.SelectSource(sourceId = null))
        importSourceRepository.emitSources(listOf(onlineSource(), onlineSource("nav-other")))
        advanceUntilIdle()

        assertNull(preferencesStore.onlineFavoritesSourceId.value)
        assertNull(store.state.value.sourceId)
        scope.cancel()

        val restartedScope = testScope(testScheduler)
        val restartedStore = OnlineFavoritesStore(
            repository = repository,
            importSourceRepository = importSourceRepository,
            preferencesStore = preferencesStore,
            storeScope = restartedScope,
        )

        advanceUntilIdle()
        assertNull(restartedStore.state.value.sourceId)
        restartedScope.cancel()
    }

    @Test
    fun `online favorites source selection is loading before refresh returns`() = runTest {
        val refreshGate = CompletableDeferred<Unit>()
        val repository = FakeNavidromeOnlineDataSource(
            favoriteTracksHandler = { offset, limit, _ ->
                refreshGate.await()
                OnlinePage(listOf(sampleTrack()), totalCount = 1, offset = offset, limit = limit)
            },
        )
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = testScope(testScheduler)
        val store = OnlineFavoritesStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        store.dispatch(OnlineFavoritesIntent.SelectSource(ONLINE_SOURCE_ID, persist = false))
        runCurrent()

        assertLocalizedEquals(ONLINE_SOURCE_ID, store.state.value.sourceId)
        assertLocalizedEquals(true, store.state.value.isLoading)
        assertLocalizedEquals(emptyList(), store.state.value.tracks)
        assertLocalizedEquals(1, repository.favoriteCalls)

        refreshGate.complete(Unit)
        advanceUntilIdle()

        assertFalse(store.state.value.isLoading)
        assertLocalizedEquals(listOf("track-1"), store.state.value.tracks.map { it.id })
        scope.cancel()
    }

    @Test
    fun `online favorite update records override when source is not selected`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = testScope(testScheduler)
        val store = OnlineFavoritesStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        store.dispatch(
            OnlineFavoritesIntent.SetFavorite(
                sourceId = ONLINE_SOURCE_ID,
                track = sampleTrack(id = "track-added"),
                favorite = true,
            ),
        )
        advanceUntilIdle()

        assertLocalizedEquals(null, store.state.value.sourceId)
        assertLocalizedEquals(
            true,
            store.state.value.favoriteOverridesBySourceId.getValue(ONLINE_SOURCE_ID).getValue("track-added"),
        )
        assertLocalizedEquals(emptyList(), store.state.value.tracks)
        scope.cancel()
    }

    @Test
    fun `online favorite update maintains current source list and override`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlineFavoritesSourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlineFavoritesStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        store.dispatch(
            OnlineFavoritesIntent.SetFavorite(
                sourceId = ONLINE_SOURCE_ID,
                track = sampleTrack(id = "track-added"),
                favorite = true,
            ),
        )
        advanceUntilIdle()

        assertLocalizedEquals(
            listOf("track-added", "track-1"),
            store.state.value.tracks.map { it.id },
        )
        assertLocalizedEquals(
            true,
            store.state.value.favoriteOverridesBySourceId.getValue(ONLINE_SOURCE_ID).getValue("track-added"),
        )

        store.dispatch(
            OnlineFavoritesIntent.SetFavorite(
                sourceId = ONLINE_SOURCE_ID,
                track = sampleTrack(id = "track-added"),
                favorite = false,
            ),
        )
        advanceUntilIdle()

        assertLocalizedEquals(listOf("track-1"), store.state.value.tracks.map { it.id })
        assertLocalizedEquals(
            false,
            store.state.value.favoriteOverridesBySourceId.getValue(ONLINE_SOURCE_ID).getValue("track-added"),
        )
        scope.cancel()
    }

    @Test
    fun `online favorite failure clears stale success message`() = runTest {
        val repository = FakeNavidromeOnlineDataSource()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlineFavoritesSourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlineFavoritesStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        store.dispatch(
            OnlineFavoritesIntent.SetFavorite(
                sourceId = ONLINE_SOURCE_ID,
                track = sampleTrack(id = "track-added"),
                favorite = true,
            ),
        )
        advanceUntilIdle()

        assertLocalizedEquals("favorites_track_added", store.state.value.message)
        assertNull(store.state.value.errorMessage)

        repository.setFavoriteFailure = IllegalStateException("favorite failed")
        store.dispatch(
            OnlineFavoritesIntent.SetFavorite(
                sourceId = ONLINE_SOURCE_ID,
                track = sampleTrack(id = "track-added"),
                favorite = false,
            ),
        )
        advanceUntilIdle()

        assertNull(store.state.value.message)
        assertLocalizedEquals("在线喜欢更新失败。\nfavorite failed", store.state.value.errorMessage)
        scope.cancel()
    }

    @Test
    fun `online favorite success clears stale error message`() = runTest {
        val repository = FakeNavidromeOnlineDataSource(
            setFavoriteFailure = IllegalStateException("favorite failed"),
        )
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(onlineFavoritesSourceId = ONLINE_SOURCE_ID)
        val scope = testScope(testScheduler)
        val store = OnlineFavoritesStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        store.dispatch(
            OnlineFavoritesIntent.SetFavorite(
                sourceId = ONLINE_SOURCE_ID,
                track = sampleTrack(id = "track-added"),
                favorite = true,
            ),
        )
        advanceUntilIdle()

        assertNull(store.state.value.message)
        assertLocalizedEquals("在线喜欢更新失败。\nfavorite failed", store.state.value.errorMessage)

        repository.setFavoriteFailure = null
        store.dispatch(
            OnlineFavoritesIntent.SetFavorite(
                sourceId = ONLINE_SOURCE_ID,
                track = sampleTrack(id = "track-added"),
                favorite = true,
            ),
        )
        advanceUntilIdle()

        assertLocalizedEquals("favorites_track_added", store.state.value.message)
        assertNull(store.state.value.errorMessage)
        scope.cancel()
    }

    @Test
    fun `online favorite update failure does not record override`() = runTest {
        val repository = FakeNavidromeOnlineDataSource(
            setFavoriteFailure = IllegalStateException("boom"),
        )
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = testScope(testScheduler)
        val store = OnlineFavoritesStore(
            repository = repository,
            importSourceRepository = FakeImportSourceRepository(),
            preferencesStore = preferencesStore,
            storeScope = scope,
        )

        advanceUntilIdle()
        store.dispatch(
            OnlineFavoritesIntent.SetFavorite(
                sourceId = ONLINE_SOURCE_ID,
                track = sampleTrack(id = "track-added"),
                favorite = true,
            ),
        )
        advanceUntilIdle()

        assertLocalizedEquals(emptyMap(), store.state.value.favoriteOverridesBySourceId)
        assertLocalizedEquals(emptyList(), store.state.value.tracks)
        scope.cancel()
    }
}

private fun testScope(testScheduler: TestCoroutineScheduler): CoroutineScope {
    return CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
}

private class FakeNavidromeOnlineDataSource(
    var libraryFailure: Throwable? = null,
    var favoritesFailure: Throwable? = null,
    var playlistsFailure: Throwable? = null,
    var setFavoriteFailure: Throwable? = null,
    private val tracksHandler: suspend (offset: Int, limit: Int) -> OnlinePage<Track> = { offset, limit ->
        OnlinePage(listOf(sampleTrack()), totalCount = 1, offset = offset, limit = limit)
    },
    private val albumsHandler: suspend (offset: Int, limit: Int) -> OnlinePage<OnlineAlbumItem> = { offset, limit ->
        OnlinePage(emptyList(), totalCount = 0, offset = offset, limit = limit)
    },
    private val artistsHandler: suspend (offset: Int, limit: Int) -> OnlinePage<OnlineArtistItem> = { offset, limit ->
        OnlinePage(emptyList(), totalCount = 0, offset = offset, limit = limit)
    },
    private val searchHandler: suspend (query: String, offset: Int, limit: Int) -> OnlineLibrarySearchResult = { _, _, _ ->
        OnlineLibrarySearchResult(tracks = listOf(sampleTrack(id = "search-track")))
    },
    private val favoriteTracksHandler: suspend (offset: Int, limit: Int, query: String) -> OnlinePage<Track> = { offset, limit, _ ->
        OnlinePage(listOf(sampleTrack()), totalCount = 1, offset = offset, limit = limit)
    },
) : NavidromeOnlineDataSource {
    var trackCalls = 0
        private set
    var albumCalls = 0
        private set
    var artistCalls = 0
        private set
    var searchCalls = 0
        private set
    var favoriteCalls = 0
        private set
    var playlistCalls = 0
        private set
    val addTrackCalls = mutableListOf<Pair<String, String>>()

    override suspend fun tracks(sourceId: String, offset: Int, limit: Int): OnlinePage<Track> {
        trackCalls += 1
        libraryFailure?.let { throw it }
        return tracksHandler(offset, limit)
    }

    override suspend fun albums(sourceId: String, offset: Int, limit: Int): OnlinePage<OnlineAlbumItem> {
        albumCalls += 1
        return albumsHandler(offset, limit)
    }

    override suspend fun artists(sourceId: String, offset: Int, limit: Int): OnlinePage<OnlineArtistItem> {
        artistCalls += 1
        return artistsHandler(offset, limit)
    }

    override suspend fun search(
        sourceId: String,
        query: String,
        offset: Int,
        limit: Int,
    ): OnlineLibrarySearchResult {
        searchCalls += 1
        return searchHandler(query, offset, limit)
    }

    override suspend fun searchTracks(
        sourceId: String,
        query: String,
        offset: Int,
        limit: Int,
    ): OnlinePage<Track> = OnlinePage(emptyList(), offset = offset, limit = limit)

    override suspend fun searchAlbums(
        sourceId: String,
        query: String,
        offset: Int,
        limit: Int,
    ): OnlinePage<OnlineAlbumItem> = OnlinePage(emptyList(), offset = offset, limit = limit)

    override suspend fun searchArtists(
        sourceId: String,
        query: String,
        offset: Int,
        limit: Int,
    ): OnlinePage<OnlineArtistItem> = OnlinePage(emptyList(), offset = offset, limit = limit)

    override suspend fun artistAlbums(sourceId: String, artistId: String): List<OnlineAlbumItem> = emptyList()

    override suspend fun albumTracks(sourceId: String, albumId: String): List<Track> = emptyList()

    override suspend fun favoriteTracks(
        sourceId: String,
        offset: Int,
        limit: Int,
        query: String,
    ): OnlinePage<Track> {
        favoriteCalls += 1
        favoritesFailure?.let { throw it }
        return favoriteTracksHandler(offset, limit, query)
    }

    override suspend fun setFavorite(sourceId: String, track: Track, favorite: Boolean) {
        setFavoriteFailure?.let { throw it }
    }

    override suspend fun playlists(sourceId: String): List<PlaylistSummary> {
        playlistCalls += 1
        playlistsFailure?.let { throw it }
        return listOf(PlaylistSummary(id = "playlist-1", name = "在线歌单", kind = PlaylistKind.USER))
    }

    override suspend fun playlistDetail(sourceId: String, playlistId: String): PlaylistDetail? = null

    override suspend fun createPlaylist(sourceId: String, name: String): PlaylistSummary {
        return PlaylistSummary(id = "playlist-new", name = name, kind = PlaylistKind.USER)
    }

    override suspend fun renamePlaylist(sourceId: String, playlistId: String, name: String) = Unit

    override suspend fun deletePlaylist(sourceId: String, playlistId: String) = Unit

    override suspend fun addTrackToPlaylist(sourceId: String, playlistId: String, track: Track) {
        addTrackCalls += sourceId to playlistId
    }

    override suspend fun importPlaylistText(
        sourceId: String,
        playlistId: String,
        text: String,
    ): PlaylistImportReport = PlaylistImportReport(addedCount = 1)

    override suspend fun removeTrackFromPlaylist(sourceId: String, playlistId: String, index: Int) = Unit
}

private class FakeImportSourceRepository(
    initialSources: List<SourceWithStatus> = listOf(onlineSource()),
) : ImportSourceRepository {
    private val sources = MutableStateFlow(initialSources)
    var observeSourcesCalls = 0
        private set

    override fun observeSources(): Flow<List<SourceWithStatus>> {
        observeSourcesCalls += 1
        return sources
    }

    fun emitSources(nextSources: List<SourceWithStatus>) {
        sources.value = nextSources
    }

    override suspend fun importLocalFolder(): Result<ImportScanSummary?> = Result.success(testScanSummary())
    override suspend fun testSambaSource(draft: SambaSourceDraft): Result<Unit> = Result.success(Unit)
    override suspend fun testUpdatedSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun addSambaSource(draft: SambaSourceDraft): Result<ImportScanSummary> = Result.success(testScanSummary())
    override suspend fun updateSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> = Result.success(testScanSummary(sourceId))

    override suspend fun testWebDavSource(draft: WebDavSourceDraft): Result<Unit> = Result.success(Unit)
    override suspend fun testUpdatedWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun addWebDavSource(draft: WebDavSourceDraft): Result<ImportScanSummary> = Result.success(testScanSummary())
    override suspend fun updateWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> = Result.success(testScanSummary(sourceId))

    override suspend fun testNavidromeSource(draft: NavidromeSourceDraft): Result<Unit> = Result.success(Unit)
    override suspend fun probeNavidromeSource(draft: NavidromeSourceDraft): Result<NavidromeLibraryProbe> {
        return Result.success(NavidromeLibraryProbe(totalTrackCount = 1))
    }

    override suspend fun testUpdatedNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun addNavidromeSource(draft: NavidromeSourceDraft): Result<ImportScanSummary> {
        return Result.success(testScanSummary())
    }

    override suspend fun addNavidromeSourceOnline(
        draft: NavidromeSourceDraft,
        remoteTrackCount: Int?,
    ): Result<ImportScanSummary> = Result.success(testScanSummary())

    override suspend fun probeExistingNavidromeSource(sourceId: String): Result<NavidromeLibraryProbe> {
        return Result.success(NavidromeLibraryProbe(totalTrackCount = 1))
    }

    override suspend fun switchNavidromeSourceToOnline(
        sourceId: String,
        remoteTrackCount: Int?,
    ): Result<ImportScanSummary> = Result.success(testScanSummary(sourceId))

    override suspend fun updateNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<ImportScanSummary> = Result.success(testScanSummary(sourceId))

    override suspend fun rescanSource(sourceId: String): Result<ImportScanSummary?> = Result.success(testScanSummary(sourceId))
    override suspend fun setSourceEnabled(sourceId: String, enabled: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun deleteSource(sourceId: String): Result<Unit> = Result.success(Unit)
}

private class FakeLibrarySourceFilterPreferencesStore(
    onlineLibrarySourceId: String? = null,
    onlineFavoritesSourceId: String? = null,
    onlinePlaylistsSourceId: String? = null,
) : LibrarySourceFilterPreferencesStore {
    override val librarySourceFilter = MutableStateFlow(LibrarySourceFilter.ALL)
    override val favoritesSourceFilter = MutableStateFlow(LibrarySourceFilter.ALL)
    override val onlineLibrarySourceId = MutableStateFlow(onlineLibrarySourceId)
    override val onlineFavoritesSourceId = MutableStateFlow(onlineFavoritesSourceId)
    override val onlinePlaylistsSourceId = MutableStateFlow(onlinePlaylistsSourceId)
    override val libraryTrackSortMode = MutableStateFlow(TrackSortMode.TITLE)
    override val favoritesTrackSortMode = MutableStateFlow(TrackSortMode.ADDED_AT)

    override suspend fun setLibrarySourceFilter(filter: LibrarySourceFilter) {
        librarySourceFilter.value = filter
    }

    override suspend fun setFavoritesSourceFilter(filter: LibrarySourceFilter) {
        favoritesSourceFilter.value = filter
    }

    override suspend fun setOnlineLibrarySourceId(sourceId: String?) {
        onlineLibrarySourceId.value = sourceId
    }

    override suspend fun setOnlineFavoritesSourceId(sourceId: String?) {
        onlineFavoritesSourceId.value = sourceId
    }

    override suspend fun setOnlinePlaylistsSourceId(sourceId: String?) {
        onlinePlaylistsSourceId.value = sourceId
    }

    override suspend fun setLibraryTrackSortMode(mode: TrackSortMode) {
        libraryTrackSortMode.value = mode
    }

    override suspend fun setFavoritesTrackSortMode(mode: TrackSortMode) {
        favoritesTrackSortMode.value = mode
    }
}

private fun onlineSource(sourceId: String = ONLINE_SOURCE_ID): SourceWithStatus {
    return SourceWithStatus(
        ImportSource(
            id = sourceId,
            type = ImportSourceType.NAVIDROME,
            label = "Navidrome $sourceId",
            rootReference = "https://navidrome.example",
            enabled = true,
            indexMode = ImportSourceIndexMode.ONLINE,
        )
    )
}

private fun sampleTrack(
    id: String = "track-1",
    title: String = "Online Song",
): Track {
    return Track(
        id = id,
        sourceId = ONLINE_SOURCE_ID,
        title = title,
        durationMs = 180_000L,
        mediaLocator = "lynmusic-navidrome://$ONLINE_SOURCE_ID/$id",
        relativePath = "$title.mp3",
    )
}

private fun testScanSummary(sourceId: String = ONLINE_SOURCE_ID): ImportScanSummary {
    return ImportScanSummary(
        sourceId = sourceId,
        discoveredAudioFileCount = 1,
        importedTrackCount = 1,
    )
}

private const val ONLINE_SOURCE_ID = "nav-online"
