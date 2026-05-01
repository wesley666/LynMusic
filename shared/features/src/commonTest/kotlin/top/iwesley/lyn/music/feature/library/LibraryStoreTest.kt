package top.iwesley.lyn.music.feature.library

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
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.ImportScanSummary
import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.data.repository.ImportSourceRepository
import top.iwesley.lyn.music.data.repository.LibraryRepository
import top.iwesley.lyn.music.data.repository.TrackPlaybackStat
import top.iwesley.lyn.music.data.repository.TrackPlaybackStatsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryStoreTest {

    @Test
    fun `default state exposes all filter and leaves tracks unfiltered`() = runTest {
        val tracks = sampleTracks()
        val libraryRepository = FakeLibraryRepository(tracks)
        val importSourceRepository = FakeImportSourceRepository()
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()

        val state = store.state.value
        assertEquals(listOf(LibrarySourceFilter.ALL), state.availableSourceFilters)
        assertEquals(LibrarySourceFilter.ALL, state.selectedSourceFilter)
        assertEquals(listOf("track-local-2", "track-webdav-1", "track-local-1"), state.filteredTracks.map { it.id })
        assertEquals(listOf("Album One", "Album Two"), state.filteredAlbums.map { it.title })
        assertEquals(listOf(2, 1), state.filteredAlbums.map { it.trackCount })
        assertEquals(listOf("Artist A", "Artist B"), state.filteredArtists.map { it.name })
        assertEquals(listOf(2, 1), state.filteredArtists.map { it.trackCount })
        assertEquals(2, state.visibleAlbumCount)
        assertEquals(2, state.visibleArtistCount)
        scope.cancel()
    }

    @Test
    fun `available filters reflect connected source types`() = runTest {
        val libraryRepository = FakeLibraryRepository(sampleTracks())
        val importSourceRepository = FakeImportSourceRepository(
            listOf(
                sampleSource("local-1", ImportSourceType.LOCAL_FOLDER, "下载目录"),
                sampleSource("dav-1", ImportSourceType.WEBDAV, "WebDAV 曲库"),
            ),
        )
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()

        assertEquals(
            listOf(
                LibrarySourceFilter.ALL,
                LibrarySourceFilter.LOCAL_FOLDER,
                LibrarySourceFilter.WEBDAV,
            ),
            store.state.value.availableSourceFilters,
        )
        scope.cancel()
    }

    @Test
    fun `source type filter only keeps matching tracks`() = runTest {
        val libraryRepository = FakeLibraryRepository(sampleTracks())
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()
        store.dispatch(LibraryIntent.SourceFilterChanged(LibrarySourceFilter.LOCAL_FOLDER))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(LibrarySourceFilter.LOCAL_FOLDER, state.selectedSourceFilter)
        assertEquals(listOf("track-local-2", "track-local-1"), state.filteredTracks.map { it.id })
        assertEquals(listOf("Album One"), state.filteredAlbums.map { it.title })
        assertEquals(listOf("Artist A"), state.filteredArtists.map { it.name })
        scope.cancel()
    }

    @Test
    fun `search query intersects with selected source filter`() = runTest {
        val libraryRepository = FakeLibraryRepository(sampleTracks())
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()
        store.dispatch(LibraryIntent.SourceFilterChanged(LibrarySourceFilter.WEBDAV))
        store.dispatch(LibraryIntent.SearchChanged("cloud"))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(listOf("track-webdav-1"), state.filteredTracks.map { it.id })
        assertEquals(listOf("Album Two"), state.filteredAlbums.map { it.title })
        assertEquals(listOf("Artist B"), state.filteredArtists.map { it.name })
        assertEquals(1, state.visibleAlbumCount)
        assertEquals(1, state.visibleArtistCount)
        scope.cancel()
    }

    @Test
    fun `visible counts follow current filtered tracks`() = runTest {
        val libraryRepository = FakeLibraryRepository(sampleTracks())
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()
        store.dispatch(LibraryIntent.SourceFilterChanged(LibrarySourceFilter.LOCAL_FOLDER))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(2, state.filteredTracks.size)
        assertEquals(1, state.visibleAlbumCount)
        assertEquals(1, state.visibleArtistCount)
        scope.cancel()
    }

    @Test
    fun `selected filter falls back to all when source type disappears`() = runTest {
        val libraryRepository = FakeLibraryRepository(sampleTracks())
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()
        store.dispatch(LibraryIntent.SourceFilterChanged(LibrarySourceFilter.LOCAL_FOLDER))
        advanceUntilIdle()

        importSourceRepository.updateSources(
            listOf(sampleSource("dav-1", ImportSourceType.WEBDAV, "WebDAV 曲库")),
        )
        libraryRepository.updateTracks(
            listOf(
                Track(
                    id = "track-webdav-1",
                    sourceId = "dav-1",
                    title = "Cloud Song",
                    artistName = "Artist B",
                    albumTitle = "Album Two",
                    durationMs = 185_000L,
                    mediaLocator = "https://dav.example.com/music/cloud-song.mp3",
                    relativePath = "Artist B/Cloud Song.mp3",
                ),
            ),
        )
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(LibrarySourceFilter.ALL, state.selectedSourceFilter)
        assertEquals(
            listOf(LibrarySourceFilter.ALL, LibrarySourceFilter.WEBDAV),
            state.availableSourceFilters,
        )
        assertEquals(listOf("track-webdav-1"), state.filteredTracks.map { it.id })
        scope.cancel()
    }

    @Test
    fun `selected source filter loads from persisted preferences and updates them on change`() = runTest {
        val libraryRepository = FakeLibraryRepository(sampleTracks())
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(
            librarySourceFilter = LibrarySourceFilter.WEBDAV,
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, preferencesStore, scope)

        advanceUntilIdle()
        assertEquals(LibrarySourceFilter.WEBDAV, store.state.value.selectedSourceFilter)
        assertEquals(listOf("track-webdav-1"), store.state.value.filteredTracks.map { it.id })

        store.dispatch(LibraryIntent.SourceFilterChanged(LibrarySourceFilter.LOCAL_FOLDER))
        advanceUntilIdle()

        assertEquals(LibrarySourceFilter.LOCAL_FOLDER, preferencesStore.librarySourceFilter.value)
        assertEquals(LibrarySourceFilter.LOCAL_FOLDER, store.state.value.selectedSourceFilter)
        scope.cancel()
    }

    @Test
    fun `track sort mode loads from preferences and updates filtered tracks`() = runTest {
        val libraryRepository = FakeLibraryRepository(
            sampleTracks().mapIndexed { index, track -> track.copy(addedAt = (index + 1) * 100L) },
        )
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val preferencesStore = FakeLibrarySourceFilterPreferencesStore(
            libraryTrackSortMode = TrackSortMode.PLAY_COUNT,
        )
        val trackStatsRepository = FakeTrackPlaybackStatsRepository(
            mapOf(
                "track-local-1" to TrackPlaybackStat("track-local-1", playCount = 2, lastPlayedAt = 20L),
                "track-local-2" to TrackPlaybackStat("track-local-2", playCount = 7, lastPlayedAt = 70L),
                "track-webdav-1" to TrackPlaybackStat("track-webdav-1", playCount = 4, lastPlayedAt = 40L),
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(
            repository = libraryRepository,
            importSourceRepository = importSourceRepository,
            preferencesStore = preferencesStore,
            storeScope = scope,
            trackPlaybackStatsRepository = trackStatsRepository,
        )

        advanceUntilIdle()
        assertEquals(TrackSortMode.PLAY_COUNT, store.state.value.selectedTrackSortMode)
        assertEquals(listOf("track-local-2", "track-webdav-1", "track-local-1"), store.state.value.filteredTracks.map { it.id })

        store.dispatch(LibraryIntent.TrackSortChanged(TrackSortMode.ADDED_AT))
        advanceUntilIdle()

        assertEquals(TrackSortMode.ADDED_AT, preferencesStore.libraryTrackSortMode.value)
        assertEquals(listOf("track-webdav-1", "track-local-2", "track-local-1"), store.state.value.filteredTracks.map { it.id })
        scope.cancel()
    }
}

private class FakeLibraryRepository(
    tracks: List<Track>,
) : LibraryRepository {
    private val mutableTracks = MutableStateFlow(tracks)
    private val mutableArtists = MutableStateFlow(artistsFrom(tracks))
    private val mutableAlbums = MutableStateFlow(albumsFrom(tracks))

    override val tracks: Flow<List<Track>> = mutableTracks.asStateFlow()
    override val artists: Flow<List<Artist>> = mutableArtists.asStateFlow()
    override val albums: Flow<List<Album>> = mutableAlbums.asStateFlow()

    fun updateTracks(tracks: List<Track>) {
        mutableTracks.value = tracks
        mutableArtists.value = artistsFrom(tracks)
        mutableAlbums.value = albumsFrom(tracks)
    }

    override suspend fun getTracksByIds(trackIds: List<String>): List<Track> {
        val byId = mutableTracks.value.associateBy { it.id }
        return trackIds.mapNotNull { byId[it] }
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
) : LibrarySourceFilterPreferencesStore {
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

private fun sampleTracks(): List<Track> {
    return listOf(
        Track(
            id = "track-local-1",
            sourceId = "local-1",
            title = "Local Song",
            artistName = "Artist A",
            albumTitle = "Album One",
            durationMs = 210_000L,
            mediaLocator = "file:///music/local-song.mp3",
            relativePath = "Artist A/Local Song.mp3",
        ),
        Track(
            id = "track-local-2",
            sourceId = "local-2",
            title = "Another Local",
            artistName = "Artist A",
            albumTitle = "Album One",
            durationMs = 192_000L,
            mediaLocator = "file:///music/another-local.mp3",
            relativePath = "Artist A/Another Local.mp3",
        ),
        Track(
            id = "track-webdav-1",
            sourceId = "dav-1",
            title = "Cloud Song",
            artistName = "Artist B",
            albumTitle = "Album Two",
            durationMs = 185_000L,
            mediaLocator = "https://dav.example.com/music/cloud-song.mp3",
            relativePath = "Artist B/Cloud Song.mp3",
        ),
    )
}

private fun sampleSources(): List<SourceWithStatus> {
    return listOf(
        sampleSource("local-1", ImportSourceType.LOCAL_FOLDER, "下载目录"),
        sampleSource("local-2", ImportSourceType.LOCAL_FOLDER, "NAS 缓存"),
        sampleSource("dav-1", ImportSourceType.WEBDAV, "WebDAV 曲库"),
    )
}

private fun sampleSource(id: String, type: ImportSourceType, label: String): SourceWithStatus {
    return SourceWithStatus(
        source = ImportSource(
            id = id,
            type = type,
            label = label,
            rootReference = label,
            createdAt = 0L,
        ),
    )
}

private fun artistsFrom(tracks: List<Track>): List<Artist> {
    return deriveVisibleArtists(tracks)
}

private fun albumsFrom(tracks: List<Track>): List<Album> {
    return deriveVisibleAlbums(tracks)
}
