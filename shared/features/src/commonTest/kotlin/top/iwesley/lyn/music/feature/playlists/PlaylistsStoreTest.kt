package top.iwesley.lyn.music.feature.playlists

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.PlaylistDetail
import top.iwesley.lyn.music.core.model.PlaylistKind
import top.iwesley.lyn.music.core.model.PlaylistSummary
import top.iwesley.lyn.music.core.model.PlaylistTrackEntry
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.data.repository.ImportSourceRepository
import top.iwesley.lyn.music.data.repository.PlaylistRepository
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsStoreTest {

    @Test
    fun `create playlist selects newly created playlist`() = runTest {
        val repository = FakePlaylistRepository()
        val importSources = FakePlaylistsImportSourceRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = PlaylistsStore(repository, importSources, scope)

        advanceUntilIdle()
        store.dispatch(PlaylistsIntent.CreatePlaylist("晨跑"))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(listOf("晨跑"), state.playlists.map { it.name })
        assertEquals("晨跑", state.selectedPlaylist?.name)
        scope.cancel()
    }

    @Test
    fun `create playlist and add track forwards both operations`() = runTest {
        val repository = FakePlaylistRepository()
        val importSources = FakePlaylistsImportSourceRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = PlaylistsStore(repository, importSources, scope)
        val track = samplePlaylistTrack()

        advanceUntilIdle()
        store.dispatch(PlaylistsIntent.CreatePlaylistAndAddTrack("通勤", track))
        advanceUntilIdle()

        assertEquals(listOf(track.id), repository.addedTrackIds)
        assertEquals(listOf("通勤"), store.state.value.playlists.map { it.name })
        val selected = store.state.value.selectedPlaylist
        assertNotNull(selected)
        assertEquals(listOf(track.id), selected.tracks.map { it.track.id })
        scope.cancel()
    }

    @Test
    fun `navidrome playlists auto refresh only runs once after first page activation`() = runTest {
        val repository = FakePlaylistRepository()
        val importSources = FakePlaylistsImportSourceRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        PlaylistsStore(repository, importSources, scope)

        advanceUntilIdle()
        assertEquals(0, repository.refreshCalls)

        importSources.updateSources(
            listOf(source("nav-1", ImportSourceType.NAVIDROME, "Navidrome")),
        )
        advanceUntilIdle()
        assertEquals(1, repository.refreshCalls)

        importSources.updateSources(
            listOf(source("nav-1", ImportSourceType.NAVIDROME, "Navidrome")),
        )
        advanceUntilIdle()
        assertEquals(1, repository.refreshCalls)

        importSources.updateSources(
            listOf(
                source("nav-1", ImportSourceType.NAVIDROME, "Navidrome"),
                source("nav-2", ImportSourceType.NAVIDROME, "备用"),
            ),
        )
        advanceUntilIdle()
        assertEquals(1, repository.refreshCalls)
        scope.cancel()
    }

    @Test
    fun `repository failure surfaces message`() = runTest {
        val repository = FakePlaylistRepository(createError = IllegalStateException("歌单已存在"))
        val importSources = FakePlaylistsImportSourceRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = PlaylistsStore(repository, importSources, scope)

        advanceUntilIdle()
        store.dispatch(PlaylistsIntent.CreatePlaylist("重复"))
        advanceUntilIdle()

        assertEquals("歌单已存在", store.state.value.message)
        scope.cancel()
    }

    @Test
    fun `source filter follows available sources and falls back to all`() = runTest {
        val repository = FakePlaylistRepository()
        val importSources = FakePlaylistsImportSourceRepository(
            listOf(
                source("local-1", ImportSourceType.LOCAL_FOLDER, "下载目录"),
                source("nav-1", ImportSourceType.NAVIDROME, "Navidrome"),
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = PlaylistsStore(repository, importSources, scope)

        advanceUntilIdle()
        assertEquals(
            listOf(
                LibrarySourceFilter.ALL,
                LibrarySourceFilter.LOCAL_FOLDER,
                LibrarySourceFilter.NAVIDROME,
            ),
            store.state.value.availableSourceFilters,
        )

        store.dispatch(PlaylistsIntent.SourceFilterChanged(LibrarySourceFilter.NAVIDROME))
        advanceUntilIdle()
        assertEquals(LibrarySourceFilter.NAVIDROME, store.state.value.selectedSourceFilter)

        importSources.updateSources(
            listOf(source("local-1", ImportSourceType.LOCAL_FOLDER, "下载目录")),
        )
        advanceUntilIdle()

        assertEquals(LibrarySourceFilter.ALL, store.state.value.selectedSourceFilter)
        assertEquals(
            listOf(
                LibrarySourceFilter.ALL,
                LibrarySourceFilter.LOCAL_FOLDER,
            ),
            store.state.value.availableSourceFilters,
        )
        scope.cancel()
    }
}

private class FakePlaylistRepository(
    private val createError: Throwable? = null,
) : PlaylistRepository {
    private val mutablePlaylists = MutableStateFlow<List<PlaylistSummary>>(emptyList())
    private val mutableDetails = MutableStateFlow<Map<String, PlaylistDetail>>(emptyMap())
    private var nextId = 1

    var refreshCalls: Int = 0
        private set
    val addedTrackIds = mutableListOf<String>()

    override val playlists: Flow<List<PlaylistSummary>> = mutablePlaylists.asStateFlow()

    override fun observePlaylistDetail(playlistId: String): Flow<PlaylistDetail?> {
        return mutableDetails.asStateFlow().map { it[playlistId] }
    }

    override suspend fun createPlaylist(name: String): Result<PlaylistSummary> {
        createError?.let { return Result.failure(it) }
        val summary = PlaylistSummary(
            id = "playlist-${nextId++}",
            name = name,
            kind = PlaylistKind.USER,
            updatedAt = nextId.toLong(),
        )
        mutablePlaylists.value = listOf(summary) + mutablePlaylists.value
        mutableDetails.value = mutableDetails.value + (
            summary.id to PlaylistDetail(
                id = summary.id,
                name = summary.name,
                kind = PlaylistKind.USER,
                updatedAt = summary.updatedAt,
            )
        )
        return Result.success(summary)
    }

    override suspend fun addTrackToPlaylist(playlistId: String, track: Track): Result<Unit> {
        addedTrackIds += track.id
        mutablePlaylists.value = mutablePlaylists.value.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(
                    trackCount = playlist.trackCount + 1,
                    memberTrackIds = playlist.memberTrackIds + track.id,
                    updatedAt = playlist.updatedAt + 1,
                )
            } else {
                playlist
            }
        }
        val current = mutableDetails.value.getValue(playlistId)
        mutableDetails.value = mutableDetails.value + (
            playlistId to current.copy(
                tracks = current.tracks + PlaylistTrackEntry(track = track, sourceLabel = track.sourceId),
            )
        )
        return Result.success(Unit)
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Result<Unit> {
        mutablePlaylists.value = mutablePlaylists.value.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(
                    trackCount = (playlist.trackCount - 1).coerceAtLeast(0),
                    memberTrackIds = playlist.memberTrackIds - trackId,
                )
            } else {
                playlist
            }
        }
        val current = mutableDetails.value.getValue(playlistId)
        mutableDetails.value = mutableDetails.value + (
            playlistId to current.copy(
                tracks = current.tracks.filterNot { it.track.id == trackId },
            )
        )
        return Result.success(Unit)
    }

    override suspend fun refreshNavidromePlaylists(): Result<Unit> {
        refreshCalls += 1
        return Result.success(Unit)
    }
}

private class FakePlaylistsImportSourceRepository(
    sources: List<SourceWithStatus> = emptyList(),
) : ImportSourceRepository {
    private val mutableSources = MutableStateFlow(sources)

    fun updateSources(sources: List<SourceWithStatus>) {
        mutableSources.value = sources
    }

    override fun observeSources(): Flow<List<SourceWithStatus>> = mutableSources.asStateFlow()

    override suspend fun importLocalFolder(): Result<Unit> = Result.success(Unit)

    override suspend fun testSambaSource(draft: SambaSourceDraft): Result<Unit> = Result.success(Unit)

    override suspend fun testUpdatedSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun addSambaSource(draft: SambaSourceDraft): Result<Unit> = Result.success(Unit)

    override suspend fun updateSambaSource(
        sourceId: String,
        draft: SambaSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun testWebDavSource(draft: WebDavSourceDraft): Result<Unit> = Result.success(Unit)

    override suspend fun testUpdatedWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun addWebDavSource(draft: WebDavSourceDraft): Result<Unit> = Result.success(Unit)

    override suspend fun updateWebDavSource(
        sourceId: String,
        draft: WebDavSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun testNavidromeSource(draft: NavidromeSourceDraft): Result<Unit> = Result.success(Unit)

    override suspend fun testUpdatedNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun addNavidromeSource(draft: NavidromeSourceDraft): Result<Unit> = Result.success(Unit)

    override suspend fun updateNavidromeSource(
        sourceId: String,
        draft: NavidromeSourceDraft,
        keepExistingCredentialWhenBlankPassword: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun rescanSource(sourceId: String): Result<Unit> = Result.success(Unit)

    override suspend fun setSourceEnabled(sourceId: String, enabled: Boolean): Result<Unit> = Result.success(Unit)

    override suspend fun deleteSource(sourceId: String): Result<Unit> = Result.success(Unit)
}

private fun source(
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

private fun samplePlaylistTrack(): Track {
    return Track(
        id = "track-local-1",
        sourceId = "local-1",
        title = "Morning Light",
        artistName = "Artist A",
        albumTitle = "Album One",
        durationMs = 210_000L,
        mediaLocator = "file:///music/morning-light.mp3",
        relativePath = "Artist A/Morning Light.mp3",
    )
}
