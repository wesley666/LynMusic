package top.iwesley.lyn.music.feature.favorites

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.ImportScanSummary
import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
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

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesStoreTest {

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
        store.dispatch(FavoritesIntent.SourceFilterChanged(LibrarySourceFilter.NAVIDROME))
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
    fun `selected filter falls back to all when source type disappears`() = runTest {
        val favoritesRepository = FakeFavoritesRepository(
            tracks = sampleFavoriteTracks(),
            favoriteTrackIds = setOf("track-local-1", "track-nav-1"),
        )
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = FavoritesStore(favoritesRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()
        store.dispatch(FavoritesIntent.SourceFilterChanged(LibrarySourceFilter.NAVIDROME))
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
            listOf(LibrarySourceFilter.ALL, LibrarySourceFilter.LOCAL_FOLDER),
            state.availableSourceFilters,
        )
        assertEquals(listOf("track-local-1"), state.filteredTracks.map { it.id })
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
            favoritesSourceFilter = LibrarySourceFilter.NAVIDROME,
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = FavoritesStore(favoritesRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()
        assertEquals(LibrarySourceFilter.NAVIDROME, store.state.value.selectedSourceFilter)
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

    override suspend fun testUpdatedNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun addNavidromeSource(draft: NavidromeSourceDraft): Result<ImportScanSummary> = Result.success(testScanSummary())

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
    override val librarySourceFilter = MutableStateFlow(librarySourceFilter)
    override val favoritesSourceFilter = MutableStateFlow(favoritesSourceFilter)
    override val libraryTrackSortMode = MutableStateFlow(libraryTrackSortMode)
    override val favoritesTrackSortMode = MutableStateFlow(favoritesTrackSortMode)

    override suspend fun setLibrarySourceFilter(filter: LibrarySourceFilter) {
        librarySourceFilter.value = filter
    }

    override suspend fun setFavoritesSourceFilter(filter: LibrarySourceFilter) {
        favoritesSourceFilter.value = filter
    }

    override suspend fun setLibraryTrackSortMode(mode: TrackSortMode) {
        libraryTrackSortMode.value = mode
    }

    override suspend fun setFavoritesTrackSortMode(mode: TrackSortMode) {
        favoritesTrackSortMode.value = mode
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
