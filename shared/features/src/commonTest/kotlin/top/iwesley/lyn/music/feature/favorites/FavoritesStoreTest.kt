package top.iwesley.lyn.music.feature.favorites

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.ImportScanSummary
import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NavidromeLibraryProbe
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.OfflineDownloadStatus
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.data.repository.FavoriteTrackMetadata
import top.iwesley.lyn.music.data.repository.FavoritesRepository
import top.iwesley.lyn.music.data.repository.ImportSourceRepository
import top.iwesley.lyn.music.data.repository.TrackPlaybackStat
import top.iwesley.lyn.music.data.repository.TrackPlaybackStatsRepository
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.TrackSortMode
import top.iwesley.lyn.music.feature.TestOfflineDownloadRepository
import top.iwesley.lyn.music.feature.testOfflineDownload

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesStoreTest {

    @Test
    fun `automatic fallback cannot overwrite a source selection already being saved`() = runTest {
        val first = sampleFavoriteTracks().first().copy(sourceId = "dav-1")
        val second = first.copy(id = "second-track", sourceId = "dav-2")
        val tracks = listOf(first, second)
        val sources = FakeImportSourceRepository(listOf(
            sampleSource("dav-1", ImportSourceType.WEBDAV, "A"),
            sampleSource("dav-2", ImportSourceType.WEBDAV, "B"),
        ))
        val preferences = FakeLibrarySourceFilterPreferencesStore(favoritesSourceFilter = LibrarySourceFilter.Source("dav-1"))
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        try {
            val store = FavoritesStore(FakeFavoritesRepository(tracks, tracks.mapTo(mutableSetOf()) { it.id }), sources, preferences, scope)
            advanceUntilIdle()
            preferences.beforeSourceFilterWrite = {
                writeStarted.complete(Unit)
                releaseWrite.await()
            }
            store.dispatch(FavoritesIntent.SourceFilterChanged(LibrarySourceFilter.Source("dav-2")))
            runCurrent()
            assertEquals(true, writeStarted.isCompleted)
            preferences.beforeSourceFilterWrite = null
            sources.updateSources(listOf(sampleSource("dav-2", ImportSourceType.WEBDAV, "B")))
            runCurrent()
            assertEquals(LibrarySourceFilter.ALL, store.state.value.selectedSourceFilter)

            releaseWrite.complete(Unit)
            advanceUntilIdle()
            assertEquals(LibrarySourceFilter.Source("dav-2"), preferences.favoritesSourceFilter.value)
            assertEquals(LibrarySourceFilter.Source("dav-2"), store.state.value.selectedSourceFilter)
            assertEquals(listOf(second), store.state.value.filteredTracks)
        } finally { releaseWrite.complete(Unit); scope.cancel() }
    }

    @Test
    fun `local favorites combine named folders and keep the group after one folder is removed`() = runTest {
        val first = sampleFavoriteTracks().first()
        val second = first.copy(id = "track-local-2", sourceId = "local-2")
        val remote = sampleFavoriteTracks().last().copy(sourceId = "dav-1")
        val localSource = sampleSource("local-1", ImportSourceType.LOCAL_FOLDER, "Downloads")
        val remoteSource = sampleSource("dav-1", ImportSourceType.WEBDAV, "NAS")
        val sources = FakeImportSourceRepository(listOf(localSource, sampleSource("local-2", ImportSourceType.LOCAL_FOLDER, "Other"), remoteSource))
        val tracks = listOf(first, second, remote)
        val repository = FakeFavoritesRepository(tracks, tracks.mapTo(mutableSetOf()) { it.id })
        val preferences = FakeLibrarySourceFilterPreferencesStore(favoritesSourceFilter = LibrarySourceFilter.Source("local-2"))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        try {
            val store = FavoritesStore(repository, sources, preferences, scope)
            advanceUntilIdle()
            assertEquals(LibrarySourceFilter.LOCAL_FOLDER, store.state.value.selectedSourceFilter)
            assertEquals(LibrarySourceFilter.LOCAL_FOLDER, preferences.favoritesSourceFilter.value)
            assertEquals(setOf(first.id, second.id), store.state.value.filteredTracks.map { it.id }.toSet())

            sources.updateSources(listOf(localSource, remoteSource))
            repository.updateFavorites(listOf(first, remote), setOf(first.id, remote.id))
            advanceUntilIdle()
            assertEquals(LibrarySourceFilter.LOCAL_FOLDER, store.state.value.selectedSourceFilter)
            assertEquals(listOf(first), store.state.value.filteredTracks)

            sources.updateSources(listOf(localSource.copy(source = localSource.source.copy(enabled = false)), remoteSource))
            repository.updateFavorites(listOf(remote), setOf(remote.id))
            advanceUntilIdle()
            assertEquals(LibrarySourceFilter.ALL, store.state.value.selectedSourceFilter)
            assertEquals(LibrarySourceFilter.ALL, preferences.favoritesSourceFilter.value)
        } finally { scope.cancel() }
    }

    @Test
    fun `fallback write failures keep state ready and allow later updates and persistence recovery`() = runTest {
        for (missingOnStartup in listOf(true, false)) {
            val tracks = sampleFavoriteTracks().take(1).map { it.copy(sourceId = "dav-1") }
            val repository = FakeFavoritesRepository(tracks, tracks.mapTo(mutableSetOf()) { it.id })
            val selectedSource = sampleSource("dav-1", ImportSourceType.WEBDAV, "Remote")
            val sources = FakeImportSourceRepository(if (missingOnStartup) emptyList() else listOf(selectedSource))
            val releaseWrite = CompletableDeferred<Unit>()
            val preferences = FakeLibrarySourceFilterPreferencesStore(
                favoritesSourceFilter = LibrarySourceFilter.Source("dav-1"),
            ).apply {
                sourceFilterWriteFailure = IllegalStateException("Settings are not writable")
                beforeSourceFilterWrite = { releaseWrite.await() }
            }
            val failures = mutableListOf<Throwable>()
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob() +
                CoroutineExceptionHandler { _, error -> failures += error })
            try {
                val store = FavoritesStore(repository, sources, preferences, scope)
                advanceUntilIdle()
                if (!missingOnStartup) {
                    sources.updateSources(emptyList())
                    advanceUntilIdle()
                }
                // The preference write is still suspended; the fallback must already be visible.
                assertEquals(false, store.state.value.isLoadingContent)
                assertEquals(LibrarySourceFilter.ALL, store.state.value.selectedSourceFilter)
                releaseWrite.complete(Unit)
                advanceUntilIdle()
                assertEquals(emptyList<Throwable>(), failures)

                val updated = tracks.first().copy(id = "other-track", sourceId = "other", title = "Updated")
                sources.updateSources(listOf(sampleSource("other", ImportSourceType.WEBDAV, "Other")))
                repository.updateFavorites(listOf(updated), setOf(updated.id))
                advanceUntilIdle()
                assertEquals(listOf(updated), store.state.value.filteredTracks)
                assertEquals(emptyList<Throwable>(), failures)

                preferences.sourceFilterWriteFailure = null
                val recovered = updated.copy(title = "Recovered")
                repository.updateFavorites(listOf(recovered), setOf(recovered.id))
                advanceUntilIdle()
                assertEquals(listOf(recovered), store.state.value.filteredTracks)
                assertEquals(LibrarySourceFilter.ALL, preferences.favoritesSourceFilter.value)
            } finally { scope.cancel() }
        }
    }

    @Test
    fun `fallback write cancellation still stops its collection`() = runTest {
        val tracks = sampleFavoriteTracks().take(1).map { it.copy(sourceId = "dav-1") }
        val repository = FakeFavoritesRepository(tracks, tracks.mapTo(mutableSetOf()) { it.id })
        val sources = FakeImportSourceRepository()
        val preferences = FakeLibrarySourceFilterPreferencesStore(
            favoritesSourceFilter = LibrarySourceFilter.Source("dav-1"),
        ).apply { sourceFilterWriteFailure = CancellationException("Store cancelled") }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        try {
            val store = FavoritesStore(repository, sources, preferences, scope)
            advanceUntilIdle()
            val beforeUpdate = store.state.value.tracks
            preferences.sourceFilterWriteFailure = null
            repository.updateFavorites(tracks.map { it.copy(title = "After cancellation") }, tracks.mapTo(mutableSetOf()) { it.id })
            store.ensureContentStarted()
            advanceUntilIdle()
            assertEquals(beforeUpdate, store.state.value.tracks)
            assertEquals(LibrarySourceFilter.Source("dav-1"), preferences.favoritesSourceFilter.value)
        } finally { scope.cancel() }
    }

    @Test
    fun `same protocol favorites filter by source and clear unavailable saved source`() = runTest {
        val original = sampleFavoriteTracks().last()
        val otherTrack = original.copy(id = "track-nav-2", sourceId = "nav-2")
        val first = sampleSource("nav-1", ImportSourceType.NAVIDROME, "NAS")
        val second = sampleSource("nav-2", ImportSourceType.NAVIDROME, "NAS")
        val sources = FakeImportSourceRepository(listOf(first, second))
        val preferences = FakeLibrarySourceFilterPreferencesStore(favoritesSourceFilter = LibrarySourceFilter.Source("nav-2"))
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = FavoritesStore(
            FakeFavoritesRepository(listOf(original, otherTrack), setOf(original.id, otherTrack.id)),
            sources, preferences, scope,
        )

        advanceUntilIdle()
        assertEquals(listOf(otherTrack.id), store.state.value.filteredTracks.map { it.id })
        assertEquals(1, store.state.value.filteredAlbums.single().trackCount)
        sources.updateSources(listOf(first, second.copy(source = second.source.copy(label = "公司"))))
        advanceUntilIdle()
        assertEquals(LibrarySourceFilter.Source("nav-2"), store.state.value.selectedSourceFilter)
        sources.updateSources(listOf(first, second.copy(source = second.source.copy(indexMode = ImportSourceIndexMode.ONLINE))))
        advanceUntilIdle()
        assertEquals(LibrarySourceFilter.ALL, store.state.value.selectedSourceFilter)
        assertEquals(LibrarySourceFilter.ALL, preferences.favoritesSourceFilter.value)
        scope.cancel()
    }

    @Test
    fun `favorite ids and counts reflect current favorites`() = runTest {
        val favoritesRepository = FakeFavoritesRepository(
            tracks = sampleFavoriteTracks(),
            favoriteTrackIds = setOf("track-local-1", "track-nav-1"),
        )
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = FavoritesStore(favoritesRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()

        val state = store.state.value
        assertEquals(setOf("track-local-1", "track-nav-1"), state.favoriteTrackIds)
        assertEquals(2, state.filteredTracks.size)
        assertEquals(listOf("Album One", "Album Two"), state.filteredAlbums.map { it.title })
        assertEquals(listOf("Artist A", "Artist B"), state.filteredArtists.map { it.name })
        assertEquals(2, state.visibleAlbumCount)
        assertEquals(2, state.visibleArtistCount)
        scope.cancel()
    }

    @Test
    fun `search query intersects with selected source filter`() = runTest {
        val favoritesRepository = FakeFavoritesRepository(
            tracks = sampleFavoriteTracks(),
            favoriteTrackIds = setOf("track-local-1", "track-nav-1"),
        )
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = FavoritesStore(favoritesRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()
        store.dispatch(FavoritesIntent.SourceFilterChanged(LibrarySourceFilter.Source("nav-1")))
        store.dispatch(FavoritesIntent.SearchChanged("night"))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(listOf("track-nav-1"), state.filteredTracks.map { it.id })
        assertEquals(listOf("Album Two"), state.filteredAlbums.map { it.title })
        assertEquals(listOf("Artist B"), state.filteredArtists.map { it.name })
        assertEquals(1, state.visibleAlbumCount)
        assertEquals(1, state.visibleArtistCount)
        scope.cancel()
    }

    @Test
    fun `removing favorite track removes matching album and artist from derived lists`() = runTest {
        val tracks = sampleFavoriteTracks()
        val favoritesRepository = FakeFavoritesRepository(
            tracks = tracks,
            favoriteTrackIds = tracks.mapTo(linkedSetOf()) { it.id },
        )
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = FavoritesStore(favoritesRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()
        store.dispatch(FavoritesIntent.ToggleFavorite(tracks.first()))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(setOf("track-nav-1"), state.favoriteTrackIds)
        assertEquals(listOf("Album Two"), state.filteredAlbums.map { it.title })
        assertEquals(listOf("Artist B"), state.filteredArtists.map { it.name })
        scope.cancel()
    }

    @Test
    fun `selected filter falls back to all when selected source disappears`() = runTest {
        val favoritesRepository = FakeFavoritesRepository(
            tracks = sampleFavoriteTracks(),
            favoriteTrackIds = setOf("track-local-1", "track-nav-1"),
        )
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = FavoritesStore(favoritesRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()
        store.dispatch(FavoritesIntent.SourceFilterChanged(LibrarySourceFilter.Source("nav-1")))
        advanceUntilIdle()

        importSourceRepository.updateSources(
            listOf(sampleSource("local-1", ImportSourceType.LOCAL_FOLDER, "下载目录")),
        )
        favoritesRepository.updateFavorites(
            tracks = listOf(sampleFavoriteTracks().first()),
            favoriteTrackIds = setOf("track-local-1"),
        )
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(LibrarySourceFilter.ALL, state.selectedSourceFilter)
        assertEquals(
            listOf(LibrarySourceFilter.ALL, LibrarySourceFilter.LOCAL_FOLDER, LibrarySourceFilter.DOWNLOADED),
            state.availableSourceFilters,
        )
        assertEquals(listOf("track-local-1"), state.filteredTracks.map { it.id })
        scope.cancel()
    }

    @Test
    fun `downloaded source filter keeps local and completed offline favorites`() = runTest {
        val tracks = sampleFavoriteTracks() + listOf(
            sampleFavoriteTracks().last().copy(id = "track-nav-pending", title = "Pending Night"),
            sampleFavoriteTracks().last().copy(id = "track-nav-failed", title = "Failed Night"),
            sampleFavoriteTracks().last().copy(id = "track-nav-missing-local", title = "Missing Local Night"),
        )
        val favoritesRepository = FakeFavoritesRepository(
            tracks = tracks,
            favoriteTrackIds = tracks.mapTo(linkedSetOf()) { it.id },
        )
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val offlineDownloadRepository = TestOfflineDownloadRepository(
            mapOf(
                "track-nav-1" to testOfflineDownload("track-nav-1", "nav-1"),
                "track-nav-pending" to testOfflineDownload(
                    trackId = "track-nav-pending",
                    sourceId = "nav-1",
                    status = OfflineDownloadStatus.Pending,
                ),
                "track-nav-failed" to testOfflineDownload(
                    trackId = "track-nav-failed",
                    sourceId = "nav-1",
                    status = OfflineDownloadStatus.Failed,
                ),
                "track-nav-missing-local" to testOfflineDownload(
                    trackId = "track-nav-missing-local",
                    sourceId = "nav-1",
                    localMediaLocator = null,
                ),
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = FavoritesStore(
            favoritesRepository = favoritesRepository,
            importSourceRepository = importSourceRepository,
            preferencesStore = preferencesStore,
            storeScope = scope,
            offlineDownloadRepository = offlineDownloadRepository,
        )

        advanceUntilIdle()
        store.dispatch(FavoritesIntent.SourceFilterChanged(LibrarySourceFilter.DOWNLOADED))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(LibrarySourceFilter.DOWNLOADED, state.selectedSourceFilter)
        assertEquals(listOf("track-nav-1", "track-local-1"), state.filteredTracks.map { it.id })
        assertEquals(listOf("Album One", "Album Two"), state.filteredAlbums.map { it.title })
        assertEquals(listOf("Artist A", "Artist B"), state.filteredArtists.map { it.name })
        scope.cancel()
    }

    @Test
    fun `navidrome favorites auto refresh only runs once after first page activation`() = runTest {
        val favoritesRepository = FakeFavoritesRepository()
        val importSourceRepository = FakeImportSourceRepository(
            listOf(sampleSource("local-1", ImportSourceType.LOCAL_FOLDER, "下载目录")),
        )
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        FavoritesStore(favoritesRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()
        assertEquals(0, favoritesRepository.refreshCalls)

        importSourceRepository.updateSources(sampleSources())
        advanceUntilIdle()
        assertEquals(1, favoritesRepository.refreshCalls)

        importSourceRepository.updateSources(sampleSources())
        advanceUntilIdle()
        assertEquals(1, favoritesRepository.refreshCalls)

        importSourceRepository.updateSources(
            sampleSources() + sampleSource("nav-2", ImportSourceType.NAVIDROME, "备用 Navidrome"),
        )
        advanceUntilIdle()
        assertEquals(1, favoritesRepository.refreshCalls)
        scope.cancel()
    }

    @Test
    fun `manual refresh calls repository when navidrome source exists`() = runTest {
        val favoritesRepository = FakeFavoritesRepository()
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = FavoritesStore(favoritesRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()
        assertEquals(1, favoritesRepository.refreshCalls)

        store.dispatch(FavoritesIntent.Refresh)
        advanceUntilIdle()

        assertEquals(2, favoritesRepository.refreshCalls)
        scope.cancel()
    }

    @Test
    fun `favorites source filter loads from persisted preferences and updates them on change`() = runTest {
        val favoritesRepository = FakeFavoritesRepository(
            tracks = sampleFavoriteTracks(),
            favoriteTrackIds = setOf("track-local-1", "track-nav-1"),
        )
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(
            favoritesSourceFilter = LibrarySourceFilter.Source("nav-1"),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = FavoritesStore(favoritesRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()
        assertEquals(LibrarySourceFilter.Source("nav-1"), store.state.value.selectedSourceFilter)
        assertEquals(listOf("track-nav-1"), store.state.value.filteredTracks.map { it.id })

        store.dispatch(FavoritesIntent.SourceFilterChanged(LibrarySourceFilter.ALL))
        advanceUntilIdle()

        assertEquals(LibrarySourceFilter.ALL, preferencesStore.favoritesSourceFilter.value)
        assertEquals(LibrarySourceFilter.ALL, store.state.value.selectedSourceFilter)
        scope.cancel()
    }

    @Test
    fun `favorites default to favorited time sort and keep sort preference independent`() = runTest {
        val tracks = sampleFavoriteTracks()
        val favoritesRepository = FakeFavoritesRepository(
            tracks = tracks,
            favoriteTrackIds = tracks.mapTo(linkedSetOf()) { it.id },
            favoriteTrackMetadata = mapOf(
                "track-local-1" to FavoriteTrackMetadata("track-local-1", favoritedAt = 100L),
                "track-nav-1" to FavoriteTrackMetadata("track-nav-1", favoritedAt = 300L),
            ),
        )
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val trackStatsRepository = FakeTrackPlaybackStatsRepository(
            mapOf(
                "track-local-1" to TrackPlaybackStat("track-local-1", playCount = 9, lastPlayedAt = 90L),
                "track-nav-1" to TrackPlaybackStat("track-nav-1", playCount = 1, lastPlayedAt = 10L),
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = FavoritesStore(
            favoritesRepository = favoritesRepository,
            importSourceRepository = importSourceRepository,
            preferencesStore = preferencesStore,
            storeScope = scope,
            trackPlaybackStatsRepository = trackStatsRepository,
        )

        advanceUntilIdle()
        assertEquals(TrackSortMode.ADDED_AT, store.state.value.selectedTrackSortMode)
        assertEquals(listOf("track-nav-1", "track-local-1"), store.state.value.filteredTracks.map { it.id })

        store.dispatch(FavoritesIntent.TrackSortChanged(TrackSortMode.PLAY_COUNT))
        advanceUntilIdle()

        assertEquals(TrackSortMode.PLAY_COUNT, preferencesStore.favoritesTrackSortMode.value)
        assertEquals(listOf("track-local-1", "track-nav-1"), store.state.value.filteredTracks.map { it.id })
        scope.cancel()
    }
}

private class FakeFavoritesRepository(
    tracks: List<Track> = emptyList(),
    favoriteTrackIds: Set<String> = emptySet(),
    favoriteTrackMetadata: Map<String, FavoriteTrackMetadata> =
        favoriteTrackIds.mapIndexed { index, trackId ->
            trackId to FavoriteTrackMetadata(trackId, favoritedAt = index.toLong())
        }.toMap(),
) : FavoritesRepository {
    private val mutableTracks = MutableStateFlow(tracks)
    private val mutableFavoriteTrackIds = MutableStateFlow(favoriteTrackIds)
    private val mutableFavoriteTrackMetadata = MutableStateFlow(favoriteTrackMetadata)

    var refreshCalls: Int = 0
        private set

    override val favoriteTrackIds: Flow<Set<String>> = mutableFavoriteTrackIds.asStateFlow()
    override val favoriteTracks: Flow<List<Track>> = mutableTracks.asStateFlow()
    override val favoriteTrackMetadata: Flow<Map<String, FavoriteTrackMetadata>> =
        mutableFavoriteTrackMetadata.asStateFlow()

    fun updateFavorites(
        tracks: List<Track>,
        favoriteTrackIds: Set<String>,
        favoriteTrackMetadata: Map<String, FavoriteTrackMetadata> =
            favoriteTrackIds.mapIndexed { index, trackId ->
                trackId to FavoriteTrackMetadata(trackId, favoritedAt = index.toLong())
            }.toMap(),
    ) {
        mutableTracks.value = tracks
        mutableFavoriteTrackIds.value = favoriteTrackIds
        mutableFavoriteTrackMetadata.value = favoriteTrackMetadata
    }

    override suspend fun toggleFavorite(track: Track): Result<Boolean> {
        val nextIds = mutableFavoriteTrackIds.value.toMutableSet()
        val isNowFavorite = if (track.id in nextIds) {
            nextIds.remove(track.id)
            false
        } else {
            nextIds.add(track.id)
            true
        }
        mutableFavoriteTrackIds.value = nextIds
        mutableFavoriteTrackMetadata.value = nextIds.mapIndexed { index, trackId ->
            trackId to FavoriteTrackMetadata(trackId, favoritedAt = index.toLong())
        }.toMap()
        mutableTracks.value = if (isNowFavorite) {
            listOf(track) + mutableTracks.value.filterNot { it.id == track.id }
        } else {
            mutableTracks.value.filterNot { it.id == track.id }
        }
        return Result.success(isNowFavorite)
    }

    override suspend fun setFavorite(track: Track, favorite: Boolean): Result<Boolean> {
        val nextIds = mutableFavoriteTrackIds.value.toMutableSet()
        if (favorite) {
            nextIds.add(track.id)
        } else {
            nextIds.remove(track.id)
        }
        mutableFavoriteTrackIds.value = nextIds
        mutableFavoriteTrackMetadata.value = nextIds.mapIndexed { index, trackId ->
            trackId to FavoriteTrackMetadata(trackId, favoritedAt = index.toLong())
        }.toMap()
        mutableTracks.value = if (favorite) {
            listOf(track) + mutableTracks.value.filterNot { it.id == track.id }
        } else {
            mutableTracks.value.filterNot { it.id == track.id }
        }
        return Result.success(favorite)
    }

    override suspend fun refreshNavidromeFavorites(): Result<Unit> {
        refreshCalls += 1
        return Result.success(Unit)
    }
}

private class FakeImportSourceRepository(
    sources: List<SourceWithStatus> = emptyList(),
) : ImportSourceRepository {
    private val mutableSources = MutableStateFlow(sources)

    fun updateSources(sources: List<SourceWithStatus>) {
        mutableSources.value = sources
    }

    override fun observeSources(): Flow<List<SourceWithStatus>> = mutableSources.asStateFlow()

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
        return Result.success(NavidromeLibraryProbe(totalTrackCount = null))
    }

    override suspend fun testUpdatedNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun addNavidromeSource(draft: NavidromeSourceDraft): Result<ImportScanSummary> = Result.success(testScanSummary())

    override suspend fun addNavidromeSourceOnline(
        draft: NavidromeSourceDraft,
        remoteTrackCount: Int?,
    ): Result<ImportScanSummary> = Result.success(testScanSummary())

    override suspend fun probeExistingNavidromeSource(sourceId: String): Result<NavidromeLibraryProbe> {
        return Result.success(NavidromeLibraryProbe(totalTrackCount = null))
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

private fun testScanSummary(sourceId: String = "source-1"): ImportScanSummary {
    return ImportScanSummary(
        sourceId = sourceId,
        discoveredAudioFileCount = 1,
        importedTrackCount = 1,
    )
}

private class FakeLibrarySourceFilterPreferencesStore(
    librarySourceFilter: LibrarySourceFilter = LibrarySourceFilter.ALL,
    favoritesSourceFilter: LibrarySourceFilter = LibrarySourceFilter.ALL,
    libraryTrackSortMode: TrackSortMode = TrackSortMode.TITLE,
    favoritesTrackSortMode: TrackSortMode = TrackSortMode.ADDED_AT,
) : top.iwesley.lyn.music.feature.library.LibrarySourceFilterPreferencesStore {
    private val sourceFilterMutex = Mutex()
    var sourceFilterWriteFailure: Exception? = null
    var beforeSourceFilterWrite: (suspend () -> Unit)? = null

    override val librarySourceFilter = MutableStateFlow(librarySourceFilter)
    override val favoritesSourceFilter = MutableStateFlow(favoritesSourceFilter)
    override val libraryTrackSortMode = MutableStateFlow(libraryTrackSortMode)
    override val favoritesTrackSortMode = MutableStateFlow(favoritesTrackSortMode)
    override val onlineLibrarySourceId = MutableStateFlow<String?>(null)
    override val onlineFavoritesSourceId = MutableStateFlow<String?>(null)
    override val onlinePlaylistsSourceId = MutableStateFlow<String?>(null)

    override suspend fun setLibrarySourceFilter(filter: LibrarySourceFilter) {
        updateSourceFilter(librarySourceFilter, filter)
    }

    override suspend fun setFavoritesSourceFilter(filter: LibrarySourceFilter) {
        updateSourceFilter(favoritesSourceFilter, filter)
    }

    override suspend fun compareAndSetLibrarySourceFilter(expected: LibrarySourceFilter, filter: LibrarySourceFilter): Boolean =
        updateSourceFilter(librarySourceFilter, filter, expected)

    override suspend fun compareAndSetFavoritesSourceFilter(expected: LibrarySourceFilter, filter: LibrarySourceFilter): Boolean =
        updateSourceFilter(favoritesSourceFilter, filter, expected)

    private suspend fun updateSourceFilter(
        current: MutableStateFlow<LibrarySourceFilter>,
        filter: LibrarySourceFilter,
        expected: LibrarySourceFilter? = null,
    ): Boolean = sourceFilterMutex.withLock {
        if (expected != null && current.value != expected) return@withLock false
        beforeSourceFilterWrite?.invoke()
        sourceFilterWriteFailure?.let { throw it }
        current.value = filter
        true
    }

    override suspend fun setLibraryTrackSortMode(mode: TrackSortMode) {
        libraryTrackSortMode.value = mode
    }

    override suspend fun setFavoritesTrackSortMode(mode: TrackSortMode) {
        favoritesTrackSortMode.value = mode
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
}

private class FakeTrackPlaybackStatsRepository(
    initialStats: Map<String, TrackPlaybackStat> = emptyMap(),
) : TrackPlaybackStatsRepository {
    override val trackStats = MutableStateFlow(initialStats)
}

private fun sampleFavoriteTracks(): List<Track> {
    return listOf(
        Track(
            id = "track-local-1",
            sourceId = "local-1",
            title = "Morning Light",
            artistName = "Artist A",
            albumTitle = "Album One",
            durationMs = 210_000L,
            mediaLocator = "file:///music/morning-light.mp3",
            relativePath = "Artist A/Morning Light.mp3",
        ),
        Track(
            id = "track-nav-1",
            sourceId = "nav-1",
            title = "Night Drive",
            artistName = "Artist B",
            albumTitle = "Album Two",
            durationMs = 198_000L,
            mediaLocator = "navidrome://song/nav-1/song-1",
            relativePath = "Artist B/Album Two/Night Drive.flac",
        ),
    )
}

private fun sampleSources(): List<SourceWithStatus> {
    return listOf(
        sampleSource("local-1", ImportSourceType.LOCAL_FOLDER, "下载目录"),
        sampleSource("nav-1", ImportSourceType.NAVIDROME, "Navidrome"),
    )
}

private fun sampleSource(
    sourceId: String,
    type: ImportSourceType,
    label: String,
): SourceWithStatus {
    return SourceWithStatus(
        source = ImportSource(
            id = sourceId,
            type = type,
            label = label,
            rootReference = label,
            createdAt = 1L,
        ),
    )
}
