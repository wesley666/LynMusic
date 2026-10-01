package top.iwesley.lyn.music.feature.online

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.uiErrorText
import top.iwesley.lyn.music.core.model.plus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.PlaylistDetail
import top.iwesley.lyn.music.core.model.PlaylistSummary
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.ImportSourceRepository
import top.iwesley.lyn.music.data.repository.NavidromeOnlineDataSource
import top.iwesley.lyn.music.data.repository.OnlineAlbumItem
import top.iwesley.lyn.music.data.repository.OnlineArtistItem
import top.iwesley.lyn.music.data.repository.OnlinePage
import top.iwesley.lyn.music.data.repository.PlaylistImportReport
import top.iwesley.lyn.music.feature.library.LibrarySourceFilterPreferencesStore

data class OnlineLibraryRootSnapshot(
    val sourceId: String,
    val tracks: List<Track> = emptyList(),
    val albums: List<OnlineAlbumItem> = emptyList(),
    val artists: List<OnlineArtistItem> = emptyList(),
    val totalTrackCount: Int? = null,
    val totalAlbumCount: Int? = null,
    val totalArtistCount: Int? = null,
    val canLoadMoreTracks: Boolean = false,
    val canLoadMoreAlbums: Boolean = false,
    val canLoadMoreArtists: Boolean = false,
)

data class OnlineLibraryState(
    val sourceId: String? = null,
    val query: String = "",
    val tracks: List<Track> = emptyList(),
    val albums: List<OnlineAlbumItem> = emptyList(),
    val artists: List<OnlineArtistItem> = emptyList(),
    val totalTrackCount: Int? = null,
    val totalAlbumCount: Int? = null,
    val totalArtistCount: Int? = null,
    val rootSnapshot: OnlineLibraryRootSnapshot? = null,
    val knownAlbumItemsById: Map<String, OnlineAlbumItem> = emptyMap(),
    val knownArtistItemsById: Map<String, OnlineArtistItem> = emptyMap(),
    val selectedAlbumTracks: Map<String, List<Track>> = emptyMap(),
    val selectedArtistAlbumsById: Map<String, List<OnlineAlbumItem>> = emptyMap(),
    val loadingAlbumIds: Set<String> = emptySet(),
    val loadingArtistAlbumIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isLoadingMoreTracks: Boolean = false,
    val isLoadingMoreAlbums: Boolean = false,
    val isLoadingMoreArtists: Boolean = false,
    val canLoadMoreTracks: Boolean = false,
    val canLoadMoreAlbums: Boolean = false,
    val canLoadMoreArtists: Boolean = false,
    val errorMessage: UiText? = null,
)

private fun OnlineLibraryRootSnapshot.restoreInto(
    state: OnlineLibraryState,
    query: String,
): OnlineLibraryState {
    return state.copy(
        query = query,
        tracks = tracks,
        albums = albums,
        artists = artists,
        totalTrackCount = totalTrackCount,
        totalAlbumCount = totalAlbumCount,
        totalArtistCount = totalArtistCount,
        knownAlbumItemsById = state.knownAlbumItemsById + albums.associateBy { it.album.id },
        knownArtistItemsById = state.knownArtistItemsById + artists.associateBy { it.artist.id },
        selectedAlbumTracks = emptyMap(),
        selectedArtistAlbumsById = emptyMap(),
        loadingAlbumIds = emptySet(),
        loadingArtistAlbumIds = emptySet(),
        isLoading = false,
        isLoadingMoreTracks = false,
        isLoadingMoreAlbums = false,
        isLoadingMoreArtists = false,
        canLoadMoreTracks = canLoadMoreTracks,
        canLoadMoreAlbums = canLoadMoreAlbums,
        canLoadMoreArtists = canLoadMoreArtists,
        errorMessage = null,
    )
}

sealed interface OnlineLibraryIntent {
    data class SelectSource(
        val sourceId: String?,
        val persist: Boolean = true,
    ) : OnlineLibraryIntent
    data class SearchChanged(val query: String) : OnlineLibraryIntent
    data object Refresh : OnlineLibraryIntent
    data object LoadMoreTracks : OnlineLibraryIntent
    data object LoadMoreAlbums : OnlineLibraryIntent
    data object LoadMoreArtists : OnlineLibraryIntent
    data class PrepareAlbumNavigation(
        val sourceId: String,
        val albumId: String,
        val albumTitle: String?,
        val artistName: String?,
        val artworkLocator: String?,
    ) : OnlineLibraryIntent

    data class PrepareArtistNavigation(
        val sourceId: String,
        val artistId: String,
        val artistName: String?,
    ) : OnlineLibraryIntent

    data class LoadAlbumTracks(val albumId: String) : OnlineLibraryIntent
    data class LoadArtistAlbums(val artistId: String) : OnlineLibraryIntent
    data object ClearError : OnlineLibraryIntent
}

sealed interface OnlineLibraryEffect

class OnlineLibraryStore(
    private val repository: NavidromeOnlineDataSource,
    private val importSourceRepository: ImportSourceRepository,
    private val preferencesStore: LibrarySourceFilterPreferencesStore,
    private val storeScope: CoroutineScope,
    startImmediately: Boolean = true,
) : BaseStore<OnlineLibraryState, OnlineLibraryIntent, OnlineLibraryEffect>(
    initialState = OnlineLibraryState(),
    scope = storeScope,
) {
    private var requestVersion = 0
    private var refreshJob: Job? = null
    private var searchJob: Job? = null
    private var hasTemporarySourceSelection = false
    private var temporarySourceId: String? = null
    private var hasStarted = false

    val rememberedSourceId: String?
        get() = preferencesStore.onlineLibrarySourceId.value.normalizedSourceIdOrNull()

    init {
        if (startImmediately) {
            ensureStarted()
        }
    }

    fun ensureStarted() {
        if (hasStarted) return
        hasStarted = true
        storeScope.launch {
            combine(
                preferencesStore.onlineLibrarySourceId,
                importSourceRepository.observeSources(),
            ) { sourceId, sources -> sourceId to sources }
                .collect { (sourceId, sources) ->
                    syncRememberedSource(sourceId, sources)
                }
        }
    }

    fun ensureStartedIfRememberedSource() {
        if (preferencesStore.onlineLibrarySourceId.value.normalizedSourceIdOrNull() != null) {
            ensureStarted()
        }
    }

    fun clearRememberedSource() {
        storeScope.launch {
            preferencesStore.setOnlineLibrarySourceId(null)
        }
    }

    override suspend fun handleIntent(intent: OnlineLibraryIntent) {
        when (intent) {
            is OnlineLibraryIntent.SelectSource -> selectSource(
                sourceId = intent.sourceId,
                persist = intent.persist,
                temporary = !intent.persist,
            )

            is OnlineLibraryIntent.SearchChanged -> {
                val nextQuery = intent.query.takeUnless { it.isBlank() }.orEmpty()
                searchJob?.cancel()
                refreshJob?.cancel()
                val version = ++requestVersion
                val rootSnapshot = state.value.rootSnapshot?.takeIf { it.sourceId == state.value.sourceId }
                if (nextQuery.isBlank() && rootSnapshot != null) {
                    updateState { rootSnapshot.restoreInto(it, nextQuery) }
                } else {
                    updateState {
                        it.copy(
                            query = nextQuery,
                            totalTrackCount = null,
                            totalAlbumCount = null,
                            totalArtistCount = null,
                            selectedAlbumTracks = emptyMap(),
                            selectedArtistAlbumsById = emptyMap(),
                            loadingAlbumIds = emptySet(),
                            loadingArtistAlbumIds = emptySet(),
                            isLoadingMoreTracks = false,
                            isLoadingMoreAlbums = false,
                            isLoadingMoreArtists = false,
                            canLoadMoreTracks = false,
                            canLoadMoreAlbums = false,
                            canLoadMoreArtists = false,
                        )
                    }
                    searchJob = storeScope.launch {
                        delay(300)
                        launchRefresh(version)
                    }
                }
            }

            OnlineLibraryIntent.Refresh -> {
                searchJob?.cancel()
                launchRefresh(version = ++requestVersion)
            }
            OnlineLibraryIntent.LoadMoreTracks -> loadMoreTracks()
            OnlineLibraryIntent.LoadMoreAlbums -> loadMoreAlbums()
            OnlineLibraryIntent.LoadMoreArtists -> loadMoreArtists()
            is OnlineLibraryIntent.PrepareAlbumNavigation -> prepareAlbumNavigation(intent)
            is OnlineLibraryIntent.PrepareArtistNavigation -> prepareArtistNavigation(intent)
            is OnlineLibraryIntent.LoadAlbumTracks -> loadAlbumTracks(intent.albumId)
            is OnlineLibraryIntent.LoadArtistAlbums -> loadArtistAlbums(intent.artistId)
            OnlineLibraryIntent.ClearError -> updateState { it.copy(errorMessage = null) }
        }
    }

    private suspend fun syncRememberedSource(
        sourceId: String?,
        sources: List<SourceWithStatus>,
    ) {
        val rememberedSourceId = sourceId.normalizedSourceIdOrNull()
        val validRememberedSourceId = rememberedSourceId?.takeIf { sources.hasOnlineNavidromeSource(it) }
        val currentSourceId = state.value.sourceId
        val rememberedSourceWasInvalid = rememberedSourceId != null && validRememberedSourceId == null
        if (rememberedSourceWasInvalid) {
            preferencesStore.setOnlineLibrarySourceId(null)
        }
        if (currentSourceId != null && !sources.hasOnlineNavidromeSource(currentSourceId)) {
            if (hasTemporarySourceSelection && temporarySourceId == currentSourceId) {
                hasTemporarySourceSelection = false
                temporarySourceId = null
            }
            selectSource(
                sourceId = validRememberedSourceId?.takeIf { it != currentSourceId },
                persist = false,
                temporary = false,
            )
            return
        }
        if (rememberedSourceWasInvalid) {
            if (currentSourceId == rememberedSourceId) {
                selectSource(sourceId = null, persist = false, temporary = false)
            }
            return
        }
        if (hasTemporarySourceSelection && currentSourceId == temporarySourceId) return
        if (currentSourceId != validRememberedSourceId) {
            selectSource(sourceId = validRememberedSourceId, persist = false, temporary = false)
        }
    }

    private suspend fun selectSource(
        sourceId: String?,
        persist: Boolean,
        temporary: Boolean,
    ) {
        val normalizedSourceId = sourceId.normalizedSourceIdOrNull()
        if (persist) {
            hasTemporarySourceSelection = false
            temporarySourceId = null
            preferencesStore.setOnlineLibrarySourceId(normalizedSourceId)
        } else if (temporary) {
            hasTemporarySourceSelection = true
            temporarySourceId = normalizedSourceId
        } else {
            hasTemporarySourceSelection = false
            temporarySourceId = null
        }
        searchJob?.cancel()
        refreshJob?.cancel()
        val version = ++requestVersion
        updateState { OnlineLibraryState(sourceId = normalizedSourceId) }
        if (normalizedSourceId != null) launchRefresh(version)
    }

    private fun launchRefresh(version: Int) {
        refreshJob?.cancel()
        refreshJob = storeScope.launch {
            refresh(version)
        }
    }

    private suspend fun refresh(version: Int) {
        val snapshot = state.value
        val sourceId = snapshot.sourceId ?: return
        val query = snapshot.query
        updateState {
            if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                it
            } else {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    tracks = emptyList(),
                    albums = emptyList(),
                    artists = emptyList(),
                    selectedAlbumTracks = emptyMap(),
                    selectedArtistAlbumsById = emptyMap(),
                    loadingAlbumIds = emptySet(),
                    loadingArtistAlbumIds = emptySet(),
                    totalTrackCount = null,
                    totalAlbumCount = null,
                    totalArtistCount = null,
                    isLoadingMoreTracks = false,
                    isLoadingMoreAlbums = false,
                    isLoadingMoreArtists = false,
                    canLoadMoreTracks = false,
                    canLoadMoreAlbums = false,
                    canLoadMoreArtists = false,
                )
            }
        }
        when (val result = runOnlineRequest {
            val normalizedQuery = query.trim()
            if (normalizedQuery.isBlank()) {
                coroutineScope {
                    val tracks = async {
                        repository.tracks(sourceId, offset = 0, limit = ONLINE_LIBRARY_PAGE_SIZE)
                    }
                    val albums = async {
                        repository.albums(sourceId, offset = 0, limit = ONLINE_LIBRARY_PAGE_SIZE)
                    }
                    val artists = async {
                        repository.artists(sourceId, offset = 0, limit = ONLINE_LIBRARY_PAGE_SIZE)
                    }
                    Triple(tracks.await(), albums.await(), artists.await())
                }
            } else {
                val result = repository.search(sourceId, normalizedQuery, offset = 0, limit = ONLINE_LIBRARY_PAGE_SIZE)
                Triple(
                    OnlinePage(result.tracks, offset = 0, limit = ONLINE_LIBRARY_PAGE_SIZE),
                    OnlinePage(result.albums, offset = 0, limit = ONLINE_LIBRARY_PAGE_SIZE),
                    OnlinePage(result.artists, offset = 0, limit = ONLINE_LIBRARY_PAGE_SIZE),
                )
            }
        }) {
            is OnlineRequestResult.Success -> {
                val (tracksPage, albumsPage, artistsPage) = result.value
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        it.copy(
                            tracks = tracksPage.items,
                            albums = albumsPage.items,
                            artists = artistsPage.items,
                            knownAlbumItemsById = it.knownAlbumItemsById + albumsPage.items.associateBy { item ->
                                item.album.id
                            },
                            knownArtistItemsById = it.knownArtistItemsById + artistsPage.items.associateBy { item ->
                                item.artist.id
                            },
                            totalTrackCount = tracksPage.totalCount ?: tracksPage.items.size.takeIf { !tracksPage.hasMore },
                            totalAlbumCount = albumsPage.totalCount ?: albumsPage.items.size.takeIf { !albumsPage.hasMore },
                            totalArtistCount = artistsPage.totalCount ?: artistsPage.items.size.takeIf { !artistsPage.hasMore },
                            rootSnapshot = if (query.trim().isBlank()) {
                                OnlineLibraryRootSnapshot(
                                    sourceId = sourceId,
                                    tracks = tracksPage.items,
                                    albums = albumsPage.items,
                                    artists = artistsPage.items,
                                    totalTrackCount = tracksPage.totalCount
                                        ?: tracksPage.items.size.takeIf { !tracksPage.hasMore },
                                    totalAlbumCount = albumsPage.totalCount
                                        ?: albumsPage.items.size.takeIf { !albumsPage.hasMore },
                                    totalArtistCount = artistsPage.totalCount
                                        ?: artistsPage.items.size.takeIf { !artistsPage.hasMore },
                                    canLoadMoreTracks = tracksPage.hasMore,
                                    canLoadMoreAlbums = albumsPage.hasMore,
                                    canLoadMoreArtists = artistsPage.hasMore,
                                )
                            } else {
                                it.rootSnapshot
                            },
                            isLoading = false,
                            canLoadMoreTracks = tracksPage.hasMore,
                            canLoadMoreAlbums = albumsPage.hasMore,
                            canLoadMoreArtists = artistsPage.hasMore,
                        )
                    }
                }
            }

            is OnlineRequestResult.Failure -> {
                val error = result.throwable
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        it.copy(isLoading = false, errorMessage = error.uiErrorText(uiText(Res.string.library_online_load_failed)))
                    }
                }
            }

            OnlineRequestResult.Cancelled -> {
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        it.copy(isLoading = false)
                    }
                }
            }
        }
    }

    private suspend fun loadMoreTracks() {
        val snapshot = state.value
        val sourceId = snapshot.sourceId ?: return
        val query = snapshot.query
        val normalizedQuery = query.trim()
        val version = requestVersion
        if (snapshot.isLoadingMoreTracks || !snapshot.canLoadMoreTracks) return
        updateState { it.copy(isLoadingMoreTracks = true, errorMessage = null) }
        when (val result = runOnlineRequest {
            if (normalizedQuery.isBlank()) {
                repository.tracks(sourceId, offset = snapshot.tracks.size, limit = ONLINE_LIBRARY_PAGE_SIZE)
            } else {
                repository.searchTracks(
                    sourceId = sourceId,
                    query = normalizedQuery,
                    offset = snapshot.tracks.size,
                    limit = ONLINE_LIBRARY_PAGE_SIZE,
                )
            }
        }) {
            is OnlineRequestResult.Success -> {
                val page = result.value
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        val nextTracks = it.tracks + page.items
                        val nextTotalTrackCount = page.totalCount ?: nextTracks.size.takeIf { !page.hasMore }
                        it.copy(
                            tracks = nextTracks,
                            totalTrackCount = nextTotalTrackCount,
                            rootSnapshot = if (normalizedQuery.isBlank()) {
                                OnlineLibraryRootSnapshot(
                                    sourceId = sourceId,
                                    tracks = nextTracks,
                                    albums = it.albums,
                                    artists = it.artists,
                                    totalTrackCount = nextTotalTrackCount,
                                    totalAlbumCount = it.totalAlbumCount,
                                    totalArtistCount = it.totalArtistCount,
                                    canLoadMoreTracks = page.hasMore,
                                    canLoadMoreAlbums = it.canLoadMoreAlbums,
                                    canLoadMoreArtists = it.canLoadMoreArtists,
                                )
                            } else {
                                it.rootSnapshot
                            },
                            isLoadingMoreTracks = false,
                            canLoadMoreTracks = page.hasMore,
                        )
                    }
                }
            }

            is OnlineRequestResult.Failure -> {
                val error = result.throwable
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        it.copy(isLoadingMoreTracks = false, errorMessage = error.uiErrorText(uiText(Res.string.library_more_tracks_load_failed)))
                    }
                }
            }

            OnlineRequestResult.Cancelled -> {
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        it.copy(isLoadingMoreTracks = false)
                    }
                }
            }
        }
    }

    private suspend fun loadMoreAlbums() {
        val snapshot = state.value
        val sourceId = snapshot.sourceId ?: return
        val query = snapshot.query
        val normalizedQuery = query.trim()
        val version = requestVersion
        if (snapshot.isLoadingMoreAlbums || !snapshot.canLoadMoreAlbums) return
        updateState { it.copy(isLoadingMoreAlbums = true, errorMessage = null) }
        when (val result = runOnlineRequest {
            if (normalizedQuery.isBlank()) {
                repository.albums(sourceId, offset = snapshot.albums.size, limit = ONLINE_LIBRARY_PAGE_SIZE)
            } else {
                repository.searchAlbums(
                    sourceId = sourceId,
                    query = normalizedQuery,
                    offset = snapshot.albums.size,
                    limit = ONLINE_LIBRARY_PAGE_SIZE,
                )
            }
        }) {
            is OnlineRequestResult.Success -> {
                val page = result.value
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        val nextAlbums = it.albums + page.items
                        val nextTotalAlbumCount = page.totalCount ?: nextAlbums.size.takeIf { !page.hasMore }
                        it.copy(
                            albums = nextAlbums,
                            knownAlbumItemsById = it.knownAlbumItemsById + page.items.associateBy { item ->
                                item.album.id
                            },
                            totalAlbumCount = nextTotalAlbumCount,
                            rootSnapshot = if (normalizedQuery.isBlank()) {
                                OnlineLibraryRootSnapshot(
                                    sourceId = sourceId,
                                    tracks = it.tracks,
                                    albums = nextAlbums,
                                    artists = it.artists,
                                    totalTrackCount = it.totalTrackCount,
                                    totalAlbumCount = nextTotalAlbumCount,
                                    totalArtistCount = it.totalArtistCount,
                                    canLoadMoreTracks = it.canLoadMoreTracks,
                                    canLoadMoreAlbums = page.hasMore,
                                    canLoadMoreArtists = it.canLoadMoreArtists,
                                )
                            } else {
                                it.rootSnapshot
                            },
                            isLoadingMoreAlbums = false,
                            canLoadMoreAlbums = page.hasMore,
                        )
                    }
                }
            }

            is OnlineRequestResult.Failure -> {
                val error = result.throwable
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        it.copy(isLoadingMoreAlbums = false, errorMessage = error.uiErrorText(uiText(Res.string.library_more_albums_load_failed)))
                    }
                }
            }

            OnlineRequestResult.Cancelled -> {
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        it.copy(isLoadingMoreAlbums = false)
                    }
                }
            }
        }
    }

    private suspend fun loadMoreArtists() {
        val snapshot = state.value
        val sourceId = snapshot.sourceId ?: return
        val query = snapshot.query
        val normalizedQuery = query.trim()
        val version = requestVersion
        if (snapshot.isLoadingMoreArtists || !snapshot.canLoadMoreArtists) return
        updateState { it.copy(isLoadingMoreArtists = true, errorMessage = null) }
        when (val result = runOnlineRequest {
            if (normalizedQuery.isBlank()) {
                repository.artists(sourceId, offset = snapshot.artists.size, limit = ONLINE_LIBRARY_PAGE_SIZE)
            } else {
                repository.searchArtists(
                    sourceId = sourceId,
                    query = normalizedQuery,
                    offset = snapshot.artists.size,
                    limit = ONLINE_LIBRARY_PAGE_SIZE,
                )
            }
        }) {
            is OnlineRequestResult.Success -> {
                val page = result.value
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        val nextArtists = it.artists + page.items
                        val nextTotalArtistCount = page.totalCount ?: nextArtists.size.takeIf { !page.hasMore }
                        it.copy(
                            artists = nextArtists,
                            knownArtistItemsById = it.knownArtistItemsById + page.items.associateBy { item ->
                                item.artist.id
                            },
                            totalArtistCount = nextTotalArtistCount,
                            rootSnapshot = if (normalizedQuery.isBlank()) {
                                OnlineLibraryRootSnapshot(
                                    sourceId = sourceId,
                                    tracks = it.tracks,
                                    albums = it.albums,
                                    artists = nextArtists,
                                    totalTrackCount = it.totalTrackCount,
                                    totalAlbumCount = it.totalAlbumCount,
                                    totalArtistCount = nextTotalArtistCount,
                                    canLoadMoreTracks = it.canLoadMoreTracks,
                                    canLoadMoreAlbums = it.canLoadMoreAlbums,
                                    canLoadMoreArtists = page.hasMore,
                                )
                            } else {
                                it.rootSnapshot
                            },
                            isLoadingMoreArtists = false,
                            canLoadMoreArtists = page.hasMore,
                        )
                    }
                }
            }

            is OnlineRequestResult.Failure -> {
                val error = result.throwable
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        it.copy(isLoadingMoreArtists = false, errorMessage = error.uiErrorText(uiText(Res.string.library_more_artists_load_failed)))
                    }
                }
            }

            OnlineRequestResult.Cancelled -> {
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        it.copy(isLoadingMoreArtists = false)
                    }
                }
            }
        }
    }

    private suspend fun loadAlbumTracks(albumId: String) {
        val snapshot = state.value
        val sourceId = snapshot.sourceId ?: return
        val version = requestVersion
        if (albumId in snapshot.loadingAlbumIds) return
        updateState {
            if (it.sourceId != sourceId || version != requestVersion) {
                it
            } else {
                it.copy(
                    errorMessage = null,
                    loadingAlbumIds = it.loadingAlbumIds + albumId,
                )
            }
        }
        when (val result = runOnlineRequest { repository.albumTracks(sourceId, albumId) }) {
            is OnlineRequestResult.Success -> {
                val tracks = result.value
                updateState {
                    if (it.sourceId != sourceId || version != requestVersion) {
                        it
                    } else {
                        it.copy(
                            selectedAlbumTracks = it.selectedAlbumTracks + (albumId to tracks),
                            loadingAlbumIds = it.loadingAlbumIds - albumId,
                        )
                    }
                }
            }

            is OnlineRequestResult.Failure -> {
                val error = result.throwable
                updateState {
                    if (it.sourceId != sourceId || version != requestVersion) {
                        it
                    } else {
                        it.copy(
                            loadingAlbumIds = it.loadingAlbumIds - albumId,
                            errorMessage = error.uiErrorText(uiText(Res.string.library_album_detail_load_failed)),
                        )
                    }
                }
            }

            OnlineRequestResult.Cancelled -> {
                updateState {
                    if (it.sourceId != sourceId || version != requestVersion) {
                        it
                    } else {
                        it.copy(loadingAlbumIds = it.loadingAlbumIds - albumId)
                    }
                }
            }
        }
    }

    private suspend fun loadArtistAlbums(artistId: String) {
        val snapshot = state.value
        val sourceId = snapshot.sourceId ?: return
        val query = snapshot.query
        val version = requestVersion
        if (artistId in snapshot.loadingArtistAlbumIds) return
        updateState {
            if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                it
            } else {
                it.copy(
                    errorMessage = null,
                    loadingArtistAlbumIds = it.loadingArtistAlbumIds + artistId,
                )
            }
        }
        when (val result = runOnlineRequest { repository.artistAlbums(sourceId, artistId) }) {
            is OnlineRequestResult.Success -> {
                val albums = result.value
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        it.copy(
                            selectedArtistAlbumsById = it.selectedArtistAlbumsById + (artistId to albums),
                            knownAlbumItemsById = it.knownAlbumItemsById + albums.associateBy { item ->
                                item.album.id
                            },
                            loadingArtistAlbumIds = it.loadingArtistAlbumIds - artistId,
                        )
                    }
                }
            }

            is OnlineRequestResult.Failure -> {
                val error = result.throwable
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        it.copy(
                            loadingArtistAlbumIds = it.loadingArtistAlbumIds - artistId,
                            errorMessage = error.uiErrorText(uiText(Res.string.library_artist_detail_load_failed)),
                        )
                    }
                }
            }

            OnlineRequestResult.Cancelled -> {
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        it.copy(loadingArtistAlbumIds = it.loadingArtistAlbumIds - artistId)
                    }
                }
            }
        }
    }

    private suspend fun prepareAlbumNavigation(intent: OnlineLibraryIntent.PrepareAlbumNavigation) {
        val sourceId = intent.sourceId.normalizedSourceIdOrNull() ?: return
        val albumId = intent.albumId.trim().takeIf { it.isNotBlank() } ?: return
        val seed = OnlineAlbumItem(
            album = Album(
                id = albumId,
                title = intent.albumTitle?.trim()?.takeIf { it.isNotBlank() } ?: "",
                artistName = intent.artistName?.trim()?.takeIf { it.isNotBlank() },
            ),
            artworkLocator = intent.artworkLocator?.trim()?.takeIf { it.isNotBlank() },
        )
        prepareNavigationSource(
            sourceId = sourceId,
            albumSeed = seed,
            artistSeed = null,
        )
    }

    private suspend fun prepareArtistNavigation(intent: OnlineLibraryIntent.PrepareArtistNavigation) {
        val sourceId = intent.sourceId.normalizedSourceIdOrNull() ?: return
        val artistId = intent.artistId.trim().takeIf { it.isNotBlank() } ?: return
        val seed = OnlineArtistItem(
            artist = Artist(
                id = artistId,
                name = intent.artistName?.trim()?.takeIf { it.isNotBlank() } ?: "",
            ),
            trackCount = null,
            albumCount = null,
        )
        prepareNavigationSource(
            sourceId = sourceId,
            albumSeed = null,
            artistSeed = seed,
        )
    }

    private suspend fun prepareNavigationSource(
        sourceId: String,
        albumSeed: OnlineAlbumItem?,
        artistSeed: OnlineArtistItem?,
    ) {
        val snapshot = state.value
        val albumSeedMap = albumSeed?.let { mapOf(it.album.id to it) }.orEmpty()
        val artistSeedMap = artistSeed?.let { mapOf(it.artist.id to it) }.orEmpty()
        if (snapshot.sourceId == sourceId && snapshot.query.isBlank()) {
            updateState {
                it.copy(
                    knownAlbumItemsById = it.knownAlbumItemsById + albumSeedMap,
                    knownArtistItemsById = it.knownArtistItemsById + artistSeedMap,
                    errorMessage = null,
                )
            }
            hasTemporarySourceSelection = true
            temporarySourceId = sourceId
            return
        }
        searchJob?.cancel()
        refreshJob?.cancel()
        val version = ++requestVersion
        updateState {
            OnlineLibraryState(
                sourceId = sourceId,
                knownAlbumItemsById = albumSeedMap,
                knownArtistItemsById = artistSeedMap,
            )
        }
        launchRefresh(version)
        hasTemporarySourceSelection = true
        temporarySourceId = sourceId
    }

    private fun OnlineLibraryState.isCurrent(sourceId: String, query: String): Boolean {
        return this.sourceId == sourceId && this.query == query
    }
}

data class OnlineFavoritesState(
    val sourceId: String? = null,
    val query: String = "",
    val tracks: List<Track> = emptyList(),
    val favoriteOverridesBySourceId: Map<String, Map<String, Boolean>> = emptyMap(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val errorMessage: UiText? = null,
    val message: UiText? = null,
) {
    val filteredTracks: List<Track>
        get() {
            val normalized = query.trim().lowercase()
            if (normalized.isBlank()) return tracks
            return tracks.filter { track ->
                track.title.lowercase().contains(normalized) ||
                    track.artistName.orEmpty().lowercase().contains(normalized) ||
                    track.albumTitle.orEmpty().lowercase().contains(normalized)
            }
        }
}

sealed interface OnlineFavoritesIntent {
    data class SelectSource(
        val sourceId: String?,
        val persist: Boolean = true,
    ) : OnlineFavoritesIntent
    data class SearchChanged(val query: String) : OnlineFavoritesIntent
    data object Refresh : OnlineFavoritesIntent
    data object LoadMore : OnlineFavoritesIntent
    data class SetFavorite(val sourceId: String, val track: Track, val favorite: Boolean) : OnlineFavoritesIntent
    data object ClearMessage : OnlineFavoritesIntent
}

sealed interface OnlineFavoritesEffect

class OnlineFavoritesStore(
    private val repository: NavidromeOnlineDataSource,
    private val importSourceRepository: ImportSourceRepository,
    private val preferencesStore: LibrarySourceFilterPreferencesStore,
    private val storeScope: CoroutineScope,
    startImmediately: Boolean = true,
) : BaseStore<OnlineFavoritesState, OnlineFavoritesIntent, OnlineFavoritesEffect>(
    initialState = OnlineFavoritesState(),
    scope = storeScope,
) {
    private var requestVersion = 0
    private var refreshJob: Job? = null
    private var searchJob: Job? = null
    private var hasTemporarySourceSelection = false
    private var temporarySourceId: String? = null
    private var hasStarted = false

    val rememberedSourceId: String?
        get() = preferencesStore.onlineFavoritesSourceId.value.normalizedSourceIdOrNull()

    init {
        if (startImmediately) {
            ensureStarted()
        }
    }

    fun ensureStarted() {
        if (hasStarted) return
        hasStarted = true
        storeScope.launch {
            combine(
                preferencesStore.onlineFavoritesSourceId,
                importSourceRepository.observeSources(),
            ) { sourceId, sources -> sourceId to sources }
                .collect { (sourceId, sources) ->
                    syncRememberedSource(sourceId, sources)
                }
        }
    }

    fun ensureStartedIfRememberedSource() {
        if (preferencesStore.onlineFavoritesSourceId.value.normalizedSourceIdOrNull() != null) {
            ensureStarted()
        }
    }

    fun clearRememberedSource() {
        storeScope.launch {
            preferencesStore.setOnlineFavoritesSourceId(null)
        }
    }

    override suspend fun handleIntent(intent: OnlineFavoritesIntent) {
        when (intent) {
            is OnlineFavoritesIntent.SelectSource -> selectSource(
                sourceId = intent.sourceId,
                persist = intent.persist,
                temporary = !intent.persist,
            )

            is OnlineFavoritesIntent.SearchChanged -> {
                updateState {
                    it.copy(
                        query = intent.query,
                        isLoadingMore = false,
                        canLoadMore = false,
                        errorMessage = null,
                    )
                }
                searchJob?.cancel()
                refreshJob?.cancel()
                val version = ++requestVersion
                searchJob = storeScope.launch {
                    delay(300)
                    launchRefresh(version)
                }
            }

            OnlineFavoritesIntent.Refresh -> {
                searchJob?.cancel()
                launchRefresh(version = ++requestVersion)
            }
            OnlineFavoritesIntent.LoadMore -> loadMore()
            is OnlineFavoritesIntent.SetFavorite -> setFavorite(intent.sourceId, intent.track, intent.favorite)
            OnlineFavoritesIntent.ClearMessage -> updateState { it.copy(message = null, errorMessage = null) }
        }
    }

    private suspend fun syncRememberedSource(
        sourceId: String?,
        sources: List<SourceWithStatus>,
    ) {
        val rememberedSourceId = sourceId.normalizedSourceIdOrNull()
        val validRememberedSourceId = rememberedSourceId?.takeIf { sources.hasOnlineNavidromeSource(it) }
        val currentSourceId = state.value.sourceId
        val rememberedSourceWasInvalid = rememberedSourceId != null && validRememberedSourceId == null
        if (rememberedSourceWasInvalid) {
            preferencesStore.setOnlineFavoritesSourceId(null)
        }
        if (currentSourceId != null && !sources.hasOnlineNavidromeSource(currentSourceId)) {
            if (hasTemporarySourceSelection && temporarySourceId == currentSourceId) {
                hasTemporarySourceSelection = false
                temporarySourceId = null
            }
            selectSource(
                sourceId = validRememberedSourceId?.takeIf { it != currentSourceId },
                persist = false,
                temporary = false,
            )
            return
        }
        if (rememberedSourceWasInvalid) {
            if (currentSourceId == rememberedSourceId) {
                selectSource(sourceId = null, persist = false, temporary = false)
            }
            return
        }
        if (hasTemporarySourceSelection && currentSourceId == temporarySourceId) return
        if (currentSourceId != validRememberedSourceId) {
            selectSource(sourceId = validRememberedSourceId, persist = false, temporary = false)
        }
    }

    private suspend fun selectSource(
        sourceId: String?,
        persist: Boolean,
        temporary: Boolean,
    ) {
        val normalizedSourceId = sourceId.normalizedSourceIdOrNull()
        if (persist) {
            hasTemporarySourceSelection = false
            temporarySourceId = null
            preferencesStore.setOnlineFavoritesSourceId(normalizedSourceId)
        } else if (temporary) {
            hasTemporarySourceSelection = true
            temporarySourceId = normalizedSourceId
        } else {
            hasTemporarySourceSelection = false
            temporarySourceId = null
        }
        searchJob?.cancel()
        refreshJob?.cancel()
        val version = ++requestVersion
        updateState {
            OnlineFavoritesState(
                sourceId = normalizedSourceId,
                favoriteOverridesBySourceId = it.favoriteOverridesBySourceId,
                isLoading = normalizedSourceId != null,
            )
        }
        if (normalizedSourceId != null) launchRefresh(version)
    }

    private fun launchRefresh(version: Int) {
        refreshJob?.cancel()
        refreshJob = storeScope.launch {
            refresh(version)
        }
    }

    private suspend fun refresh(version: Int) {
        val snapshot = state.value
        val sourceId = snapshot.sourceId ?: return
        val query = snapshot.query
        updateState {
            if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                it
            } else {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    tracks = emptyList(),
                    isLoadingMore = false,
                    canLoadMore = false,
                )
            }
        }
        when (val result = runOnlineRequest {
            repository.favoriteTracks(
                sourceId = sourceId,
                offset = 0,
                limit = ONLINE_LIBRARY_PAGE_SIZE,
                query = query,
            )
        }) {
            is OnlineRequestResult.Success -> {
                val page = result.value
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        it.copy(
                            tracks = page.items,
                            isLoading = false,
                            canLoadMore = page.hasMore,
                            errorMessage = null,
                        )
                    }
                }
            }

            is OnlineRequestResult.Failure -> {
                val error = result.throwable
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.uiErrorText(uiText(Res.string.favorites_online_load_failed)),
                            message = null,
                        )
                    }
                }
            }

            OnlineRequestResult.Cancelled -> {
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        it.copy(isLoading = false)
                    }
                }
            }
        }
    }

    private suspend fun loadMore() {
        val snapshot = state.value
        val sourceId = snapshot.sourceId ?: return
        val query = snapshot.query
        val version = requestVersion
        if (snapshot.isLoadingMore || !snapshot.canLoadMore) return
        updateState { it.copy(isLoadingMore = true, errorMessage = null) }
        when (val result = runOnlineRequest {
            repository.favoriteTracks(
                sourceId = sourceId,
                offset = snapshot.tracks.size,
                limit = ONLINE_LIBRARY_PAGE_SIZE,
                query = query,
            )
        }) {
            is OnlineRequestResult.Success -> {
                val page = result.value
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        it.copy(
                            tracks = it.tracks + page.items,
                            isLoadingMore = false,
                            canLoadMore = page.hasMore,
                            errorMessage = null,
                        )
                    }
                }
            }

            is OnlineRequestResult.Failure -> {
                val error = result.throwable
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        it.copy(
                            isLoadingMore = false,
                            errorMessage = error.uiErrorText(uiText(Res.string.favorites_more_tracks_load_failed)),
                            message = null,
                        )
                    }
                }
            }

            OnlineRequestResult.Cancelled -> {
                updateState {
                    if (!it.isCurrent(sourceId, query) || version != requestVersion) {
                        it
                    } else {
                        it.copy(isLoadingMore = false)
                    }
                }
            }
        }
    }

    private suspend fun setFavorite(sourceId: String, track: Track, favorite: Boolean) {
        when (val result = runOnlineRequest { repository.setFavorite(sourceId, track, favorite) }) {
            is OnlineRequestResult.Success -> {
                updateState {
                    val nextOverrides = it.favoriteOverridesBySourceId.withFavoriteOverride(
                        sourceId = sourceId,
                        trackId = track.id,
                        favorite = favorite,
                    )
                    if (it.sourceId != sourceId) {
                        it.copy(favoriteOverridesBySourceId = nextOverrides)
                    } else {
                        it.copy(
                            favoriteOverridesBySourceId = nextOverrides,
                            tracks = if (favorite) {
                                if (it.tracks.any { item -> item.id == track.id }) {
                                    it.tracks
                                } else {
                                    listOf(track) + it.tracks
                                }
                            } else {
                                it.tracks.filterNot { item -> item.id == track.id }
                            },
                            message = if (favorite) uiText(Res.string.favorites_track_added) else uiText(Res.string.favorites_track_removed),
                            errorMessage = null,
                        )
                    }
                }
            }

            is OnlineRequestResult.Failure -> {
                val error = result.throwable
                updateState {
                    if (it.sourceId != sourceId) {
                        it
                    } else {
                        it.copy(
                            errorMessage = error.uiErrorText(uiText(Res.string.favorites_online_update_failed)),
                            message = null,
                        )
                    }
                }
            }

            OnlineRequestResult.Cancelled -> Unit
        }
    }

    private fun OnlineFavoritesState.isCurrent(sourceId: String, query: String): Boolean {
        return this.sourceId == sourceId && this.query == query
    }
}

private fun Map<String, Map<String, Boolean>>.withFavoriteOverride(
    sourceId: String,
    trackId: String,
    favorite: Boolean,
): Map<String, Map<String, Boolean>> {
    val sourceOverrides = this[sourceId].orEmpty() + (trackId to favorite)
    return this + (sourceId to sourceOverrides)
}

data class OnlinePlaylistsState(
    val sourceId: String? = null,
    val playlists: List<PlaylistSummary> = emptyList(),
    val selectedPlaylistId: String? = null,
    val selectedPlaylist: PlaylistDetail? = null,
    val isLoading: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val isMutating: Boolean = false,
    val isImporting: Boolean = false,
    val playlistImportReport: PlaylistImportReport? = null,
    val errorMessage: UiText? = null,
    val message: UiText? = null,
)

sealed interface OnlinePlaylistsIntent {
    data class SelectSource(
        val sourceId: String?,
        val persist: Boolean = true,
    ) : OnlinePlaylistsIntent
    data object Refresh : OnlinePlaylistsIntent
    data class SelectPlaylist(val playlistId: String?) : OnlinePlaylistsIntent
    data class CreatePlaylist(val name: String) : OnlinePlaylistsIntent
    data class RenamePlaylist(val playlistId: String, val name: String) : OnlinePlaylistsIntent
    data class DeletePlaylist(val playlistId: String) : OnlinePlaylistsIntent
    data class CreatePlaylistAndAddTrack(
        val name: String,
        val track: Track,
        val sourceId: String? = null,
    ) : OnlinePlaylistsIntent

    data class AddTrack(
        val playlistId: String,
        val track: Track,
        val sourceId: String? = null,
    ) : OnlinePlaylistsIntent
    data class RemoveTrack(val playlistId: String, val index: Int) : OnlinePlaylistsIntent
    data class ImportPlaylistText(val playlistId: String, val text: String) : OnlinePlaylistsIntent
    data object ClearPlaylistImportReport : OnlinePlaylistsIntent
    data object ClearMessage : OnlinePlaylistsIntent
}

sealed interface OnlinePlaylistsEffect

class OnlinePlaylistsStore(
    private val repository: NavidromeOnlineDataSource,
    private val importSourceRepository: ImportSourceRepository,
    private val preferencesStore: LibrarySourceFilterPreferencesStore,
    private val storeScope: CoroutineScope,
    startImmediately: Boolean = true,
) : BaseStore<OnlinePlaylistsState, OnlinePlaylistsIntent, OnlinePlaylistsEffect>(
    initialState = OnlinePlaylistsState(),
    scope = storeScope,
) {
    private var listRequestVersion = 0
    private var detailRequestVersion = 0
    private var listRefreshJob: Job? = null
    private var detailJob: Job? = null
    private var hasTemporarySourceSelection = false
    private var temporarySourceId: String? = null
    private var hasStarted = false

    val rememberedSourceId: String?
        get() = preferencesStore.onlinePlaylistsSourceId.value.normalizedSourceIdOrNull()

    init {
        if (startImmediately) {
            ensureStarted()
        }
    }

    fun ensureStarted() {
        if (hasStarted) return
        hasStarted = true
        storeScope.launch {
            combine(
                preferencesStore.onlinePlaylistsSourceId,
                importSourceRepository.observeSources(),
            ) { sourceId, sources -> sourceId to sources }
                .collect { (sourceId, sources) ->
                    syncRememberedSource(sourceId, sources)
                }
        }
    }

    fun ensureStartedIfRememberedSource() {
        if (preferencesStore.onlinePlaylistsSourceId.value.normalizedSourceIdOrNull() != null) {
            ensureStarted()
        }
    }

    fun clearRememberedSource() {
        storeScope.launch {
            preferencesStore.setOnlinePlaylistsSourceId(null)
        }
    }

    override suspend fun handleIntent(intent: OnlinePlaylistsIntent) {
        when (intent) {
            is OnlinePlaylistsIntent.SelectSource -> selectSource(
                sourceId = intent.sourceId,
                persist = intent.persist,
                temporary = !intent.persist,
            )

            OnlinePlaylistsIntent.Refresh -> launchRefresh(version = ++listRequestVersion)
            is OnlinePlaylistsIntent.SelectPlaylist -> launchSelectPlaylist(
                playlistId = intent.playlistId,
                version = ++detailRequestVersion,
            )
            is OnlinePlaylistsIntent.CreatePlaylist -> mutate(uiText(Res.string.playlist_created)) {
                repository.createPlaylist(it, intent.name)
            }
            is OnlinePlaylistsIntent.RenamePlaylist -> mutate(uiText(Res.string.playlist_renamed)) {
                repository.renamePlaylist(it, intent.playlistId, intent.name)
            }
            is OnlinePlaylistsIntent.DeletePlaylist -> mutate(uiText(Res.string.playlist_deleted)) {
                repository.deletePlaylist(it, intent.playlistId)
                detailRequestVersion += 1
                updateState { current ->
                    if (current.sourceId == it) {
                        current.copy(selectedPlaylistId = null, selectedPlaylist = null, isLoadingDetail = false)
                    } else {
                        current
                    }
                }
            }
            is OnlinePlaylistsIntent.CreatePlaylistAndAddTrack -> mutate(
                successMessage = uiText(Res.string.playlist_created_with_track),
                sourceId = intent.sourceId,
            ) {
                val playlist = repository.createPlaylist(it, intent.name)
                repository.addTrackToPlaylist(it, playlist.id, intent.track)
            }
            is OnlinePlaylistsIntent.AddTrack -> mutate(
                successMessage = uiText(Res.string.playlist_track_added),
                sourceId = intent.sourceId,
            ) {
                repository.addTrackToPlaylist(it, intent.playlistId, intent.track)
            }
            is OnlinePlaylistsIntent.RemoveTrack -> mutate(uiText(Res.string.playlist_track_removed)) {
                repository.removeTrackFromPlaylist(it, intent.playlistId, intent.index)
            }
            is OnlinePlaylistsIntent.ImportPlaylistText -> importPlaylistText(intent.playlistId, intent.text)
            OnlinePlaylistsIntent.ClearPlaylistImportReport -> updateState { it.copy(playlistImportReport = null) }
            OnlinePlaylistsIntent.ClearMessage -> updateState { it.copy(message = null, errorMessage = null) }
        }
    }

    private suspend fun syncRememberedSource(
        sourceId: String?,
        sources: List<SourceWithStatus>,
    ) {
        val rememberedSourceId = sourceId.normalizedSourceIdOrNull()
        val validRememberedSourceId = rememberedSourceId?.takeIf { sources.hasOnlineNavidromeSource(it) }
        val currentSourceId = state.value.sourceId
        val rememberedSourceWasInvalid = rememberedSourceId != null && validRememberedSourceId == null
        if (rememberedSourceWasInvalid) {
            preferencesStore.setOnlinePlaylistsSourceId(null)
        }
        if (currentSourceId != null && !sources.hasOnlineNavidromeSource(currentSourceId)) {
            if (hasTemporarySourceSelection && temporarySourceId == currentSourceId) {
                hasTemporarySourceSelection = false
                temporarySourceId = null
            }
            selectSource(
                sourceId = validRememberedSourceId?.takeIf { it != currentSourceId },
                persist = false,
                temporary = false,
            )
            return
        }
        if (rememberedSourceWasInvalid) {
            if (currentSourceId == rememberedSourceId) {
                selectSource(sourceId = null, persist = false, temporary = false)
            }
            return
        }
        if (hasTemporarySourceSelection && currentSourceId == temporarySourceId) return
        if (currentSourceId != validRememberedSourceId) {
            selectSource(sourceId = validRememberedSourceId, persist = false, temporary = false)
        }
    }

    private suspend fun selectSource(
        sourceId: String?,
        persist: Boolean,
        temporary: Boolean,
    ) {
        val normalizedSourceId = sourceId.normalizedSourceIdOrNull()
        if (persist) {
            hasTemporarySourceSelection = false
            temporarySourceId = null
            preferencesStore.setOnlinePlaylistsSourceId(normalizedSourceId)
        } else if (temporary) {
            hasTemporarySourceSelection = true
            temporarySourceId = normalizedSourceId
        } else {
            hasTemporarySourceSelection = false
            temporarySourceId = null
        }
        listRefreshJob?.cancel()
        detailJob?.cancel()
        val version = ++listRequestVersion
        detailRequestVersion += 1
        updateState { OnlinePlaylistsState(sourceId = normalizedSourceId) }
        if (normalizedSourceId != null) launchRefresh(sourceId = normalizedSourceId, version = version)
    }

    private fun launchRefresh(
        sourceId: String? = state.value.sourceId,
        version: Int,
    ) {
        listRefreshJob?.cancel()
        listRefreshJob = storeScope.launch {
            refresh(sourceId = sourceId, version = version)
        }
    }

    private fun launchSelectPlaylist(
        playlistId: String?,
        sourceId: String? = state.value.sourceId,
        version: Int,
    ) {
        detailJob?.cancel()
        detailJob = storeScope.launch {
            selectPlaylist(playlistId = playlistId, sourceId = sourceId, version = version)
        }
    }

    private suspend fun refresh(
        sourceId: String? = state.value.sourceId,
        version: Int,
    ) {
        val currentSourceId = sourceId ?: return
        updateState {
            if (it.sourceId == currentSourceId && version == listRequestVersion) {
                it.copy(isLoading = true, errorMessage = null)
            } else {
                it
            }
        }
        when (val result = runOnlineRequest { repository.playlists(currentSourceId) }) {
            is OnlineRequestResult.Success -> {
                val playlists = result.value
                updateState {
                    if (it.sourceId != currentSourceId || version != listRequestVersion) return@updateState it
                    val selectedId = it.selectedPlaylistId?.takeIf { id -> playlists.any { playlist -> playlist.id == id } }
                    it.copy(
                        playlists = playlists,
                        selectedPlaylistId = selectedId,
                        selectedPlaylist = it.selectedPlaylist?.takeIf { detail -> detail.id == selectedId },
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            }

            is OnlineRequestResult.Failure -> {
                val error = result.throwable
                updateState {
                    if (it.sourceId != currentSourceId || version != listRequestVersion) {
                        it
                    } else {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.uiErrorText(uiText(Res.string.playlist_online_list_load_failed)),
                            message = null,
                        )
                    }
                }
            }

            OnlineRequestResult.Cancelled -> {
                updateState {
                    if (it.sourceId != currentSourceId || version != listRequestVersion) {
                        it
                    } else {
                        it.copy(isLoading = false)
                    }
                }
            }
        }
    }

    private suspend fun selectPlaylist(
        playlistId: String?,
        sourceId: String? = state.value.sourceId,
        version: Int,
    ) {
        val currentSourceId = sourceId
        updateState {
            if (it.sourceId == currentSourceId) {
                it.copy(selectedPlaylistId = playlistId, selectedPlaylist = null, isLoadingDetail = false)
            } else {
                it
            }
        }
        currentSourceId ?: return
        if (playlistId == null) return
        updateState {
            if (it.sourceId == currentSourceId && it.selectedPlaylistId == playlistId && version == detailRequestVersion) {
                it.copy(isLoadingDetail = true, errorMessage = null)
            } else {
                it
            }
        }
        when (val result = runOnlineRequest { repository.playlistDetail(currentSourceId, playlistId) }) {
            is OnlineRequestResult.Success -> {
                val detail = result.value
                updateState {
                    if (
                        it.sourceId != currentSourceId ||
                        it.selectedPlaylistId != playlistId ||
                        version != detailRequestVersion
                    ) {
                        it
                    } else {
                        it.copy(
                            selectedPlaylist = detail,
                            isLoadingDetail = false,
                            errorMessage = null,
                        )
                    }
                }
            }

            is OnlineRequestResult.Failure -> {
                val error = result.throwable
                updateState {
                    if (
                        it.sourceId != currentSourceId ||
                        it.selectedPlaylistId != playlistId ||
                        version != detailRequestVersion
                    ) {
                        it
                    } else {
                        it.copy(
                            isLoadingDetail = false,
                            errorMessage = error.uiErrorText(uiText(Res.string.playlist_online_detail_load_failed)),
                            message = null,
                        )
                    }
                }
            }

            OnlineRequestResult.Cancelled -> {
                updateState {
                    if (
                        it.sourceId != currentSourceId ||
                        it.selectedPlaylistId != playlistId ||
                        version != detailRequestVersion
                    ) {
                        it
                    } else {
                        it.copy(isLoadingDetail = false)
                    }
                }
            }
        }
    }

    private suspend fun importPlaylistText(playlistId: String, text: String) {
        val sourceId = state.value.sourceId ?: return
        updateState {
            if (it.sourceId == sourceId) {
                it.copy(isImporting = true, errorMessage = null, playlistImportReport = null)
            } else {
                it
            }
        }
        when (val result = runOnlineRequest { repository.importPlaylistText(sourceId, playlistId, text) }) {
            is OnlineRequestResult.Success -> {
                val report = result.value
                if (state.value.sourceId == sourceId) {
                    launchRefresh(sourceId = sourceId, version = ++listRequestVersion)
                    if (state.value.selectedPlaylistId == playlistId) {
                        launchSelectPlaylist(playlistId, sourceId = sourceId, version = ++detailRequestVersion)
                    }
                    updateState {
                        if (it.sourceId == sourceId) {
                            it.copy(
                                isImporting = false,
                                playlistImportReport = report,
                                errorMessage = null,
                            )
                        } else {
                            it
                        }
                    }
                }
            }

            is OnlineRequestResult.Failure -> {
                val error = result.throwable
                updateState {
                    if (it.sourceId == sourceId) {
                        it.copy(
                            isImporting = false,
                            errorMessage = error.uiErrorText(uiText(Res.string.playlist_online_import_failed)),
                            message = null,
                        )
                    } else {
                        it
                    }
                }
            }

            OnlineRequestResult.Cancelled -> {
                updateState {
                    if (it.sourceId == sourceId) {
                        it.copy(isImporting = false)
                    } else {
                        it
                    }
                }
            }
        }
    }

    private suspend fun mutate(
        successMessage: UiText,
        sourceId: String? = state.value.sourceId,
        block: suspend (String) -> Unit,
    ) {
        val targetSourceId = sourceId.normalizedSourceIdOrNull() ?: return
        updateState {
            if (it.sourceId == targetSourceId) {
                it.copy(isMutating = true, errorMessage = null)
            } else {
                it
            }
        }
        when (val result = runOnlineRequest { block(targetSourceId) }) {
            is OnlineRequestResult.Success -> {
                if (state.value.sourceId == targetSourceId) {
                    launchRefresh(sourceId = targetSourceId, version = ++listRequestVersion)
                    state.value.selectedPlaylistId?.let { playlistId ->
                        launchSelectPlaylist(playlistId, sourceId = targetSourceId, version = ++detailRequestVersion)
                    }
                }
                updateState {
                    if (it.sourceId == targetSourceId) {
                        it.copy(
                            isMutating = false,
                            message = successMessage,
                            errorMessage = null,
                        )
                    } else {
                        it.copy(
                            message = successMessage,
                            errorMessage = null,
                        )
                    }
                }
            }

            is OnlineRequestResult.Failure -> {
                val error = result.throwable
                updateState {
                    if (it.sourceId == targetSourceId) {
                        it.copy(
                            isMutating = false,
                            errorMessage = error.uiErrorText(uiText(Res.string.playlist_online_operation_failed)),
                            message = null,
                        )
                    } else {
                        it.copy(
                            errorMessage = error.uiErrorText(uiText(Res.string.playlist_online_operation_failed)),
                            message = null,
                        )
                    }
                }
            }

            OnlineRequestResult.Cancelled -> {
                updateState {
                    if (it.sourceId == targetSourceId) {
                        it.copy(isMutating = false)
                    } else {
                        it
                    }
                }
            }
        }
    }
}

private fun String?.normalizedSourceIdOrNull(): String? {
    return this?.trim()?.takeIf { it.isNotBlank() }
}

private fun List<SourceWithStatus>.hasOnlineNavidromeSource(sourceId: String): Boolean {
    return any { item ->
        val source = item.source
        source.id == sourceId &&
            source.enabled &&
            source.type == ImportSourceType.NAVIDROME &&
            source.indexMode == ImportSourceIndexMode.ONLINE
    }
}

private sealed interface OnlineRequestResult<out T> {
    data class Success<T>(val value: T) : OnlineRequestResult<T>
    data class Failure(val throwable: Throwable) : OnlineRequestResult<Nothing>
    data object Cancelled : OnlineRequestResult<Nothing>
}

private suspend fun <T> runOnlineRequest(block: suspend () -> T): OnlineRequestResult<T> {
    return try {
        OnlineRequestResult.Success(block())
    } catch (error: CancellationException) {
        OnlineRequestResult.Cancelled
    } catch (throwable: Throwable) {
        OnlineRequestResult.Failure(throwable)
    }
}

private const val ONLINE_LIBRARY_PAGE_SIZE = 100
