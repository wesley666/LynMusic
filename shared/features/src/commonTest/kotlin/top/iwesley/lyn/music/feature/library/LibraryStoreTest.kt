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
import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.data.repository.ImportSourceRepository
import top.iwesley.lyn.music.data.repository.LibraryRepository

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryStoreTest {

    @Test
    fun `default state exposes all filter and leaves tracks unfiltered`() = runTest {
        val tracks = sampleTracks()
        val libraryRepository = FakeLibraryRepository(tracks)
        val importSourceRepository = FakeImportSourceRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, scope)

        advanceUntilIdle()

        val state = store.state.value
        assertEquals(listOf(LibrarySourceFilter.ALL), state.availableSourceFilters)
        assertEquals(LibrarySourceFilter.ALL, state.selectedSourceFilter)
        assertEquals(tracks.map { it.id }, state.filteredTracks.map { it.id })
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
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, scope)

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
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, scope)

        advanceUntilIdle()
        store.dispatch(LibraryIntent.SourceFilterChanged(LibrarySourceFilter.LOCAL_FOLDER))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(LibrarySourceFilter.LOCAL_FOLDER, state.selectedSourceFilter)
        assertEquals(listOf("track-local-1", "track-local-2"), state.filteredTracks.map { it.id })
        assertEquals(listOf("Album One"), state.filteredAlbums.map { it.title })
        assertEquals(listOf("Artist A"), state.filteredArtists.map { it.name })
        scope.cancel()
    }

    @Test
    fun `search query intersects with selected source filter`() = runTest {
        val libraryRepository = FakeLibraryRepository(sampleTracks())
        val importSourceRepository = FakeImportSourceRepository(sampleSources())
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, scope)

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
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, scope)

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
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = LibraryStore(libraryRepository, importSourceRepository, scope)

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

    override suspend fun importLocalFolder(): Result<Unit> = Result.success(Unit)

    override suspend fun addSambaSource(draft: SambaSourceDraft): Result<Unit> = Result.success(Unit)

    override suspend fun addWebDavSource(draft: WebDavSourceDraft): Result<Unit> = Result.success(Unit)

    override suspend fun addNavidromeSource(draft: NavidromeSourceDraft): Result<Unit> = Result.success(Unit)

    override suspend fun rescanSource(sourceId: String): Result<Unit> = Result.success(Unit)

    override suspend fun deleteSource(sourceId: String): Result<Unit> = Result.success(Unit)
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
