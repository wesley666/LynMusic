package top.iwesley.lyn.music.feature.library

import top.iwesley.lyn.music.core.model.UiText

import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.PlaylistDetail
import top.iwesley.lyn.music.core.model.PlaylistSummary
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.feature.favorites.FavoritesState
import top.iwesley.lyn.music.feature.online.OnlineFavoritesState
import top.iwesley.lyn.music.feature.online.OnlineLibraryState
import top.iwesley.lyn.music.feature.online.OnlinePlaylistsState
import top.iwesley.lyn.music.feature.playlists.PlaylistsState

data class LibraryBrowserUiState(
    val query: String = "",
    val tracks: List<LibraryTrackUiItem> = emptyList(),
    val albums: List<LibraryAlbumUiItem> = emptyList(),
    val artists: List<LibraryArtistUiItem> = emptyList(),
    val allTrackCount: Int = tracks.size,
    val trackCount: LibraryBrowserCount = LibraryBrowserCount.exact(tracks.size),
    val albumCount: LibraryBrowserCount = LibraryBrowserCount.exact(albums.size),
    val artistCount: LibraryBrowserCount = LibraryBrowserCount.exact(artists.size),
    val isLoading: Boolean = false,
    val isOnline: Boolean = false,
    val sourceId: String? = null,
    val selectedSourceFilter: LibrarySourceFilter = LibrarySourceFilter.ALL,
    val availableSourceFilters: List<LibrarySourceFilter> = listOf(
        LibrarySourceFilter.ALL,
        LibrarySourceFilter.DOWNLOADED,
    ),
    val selectedTrackSortMode: TrackSortMode = TrackSortMode.TITLE,
    val favoriteTrackIds: Set<String> = emptySet(),
    val sourceLabelsById: Map<String, String> = emptyMap(),
    val onlineAlbumItemsById: Map<String, LibraryAlbumUiItem> = emptyMap(),
    val onlineArtistItemsById: Map<String, LibraryArtistUiItem> = emptyMap(),
    val onlineAlbumTracksById: Map<String, List<Track>> = emptyMap(),
    val onlineArtistAlbumsById: Map<String, List<LibraryAlbumUiItem>> = emptyMap(),
    val loadingAlbumIds: Set<String> = emptySet(),
    val loadingArtistAlbumIds: Set<String> = emptySet(),
    val isLoadingMoreTracks: Boolean = false,
    val isLoadingMoreAlbums: Boolean = false,
    val isLoadingMoreArtists: Boolean = false,
    val capabilities: LibraryBrowserCapabilities = LibraryBrowserCapabilities(),
    val message: UiText? = null,
    val errorMessage: UiText? = null,
)

data class LibraryBrowserCount(
    val loaded: Int,
    val total: Int? = loaded,
    val hasMore: Boolean = false,
) {
    fun displayValue(): String {
        val loadedValue = loaded.coerceAtLeast(0)
        val totalValue = total?.coerceAtLeast(0)
        return when {
            totalValue != null -> totalValue.toString()
            hasMore -> "$loadedValue+"
            else -> loadedValue.toString()
        }
    }

    companion object {
        fun exact(value: Int): LibraryBrowserCount {
            val count = value.coerceAtLeast(0)
            return LibraryBrowserCount(loaded = count, total = count, hasMore = false)
        }
    }
}

data class LibraryTrackUiItem(
    val id: String,
    val track: Track,
    val sourceLabel: String? = null,
    val isFavorite: Boolean = false,
)

data class LibraryAlbumUiItem(
    val id: String,
    val album: Album,
    val artworkLocator: String? = null,
)

data class LibraryArtistUiItem(
    val id: String,
    val artist: Artist,
    val trackCount: Int? = null,
    val albumCount: Int? = null,
)

data class LibraryBrowserCapabilities(
    val canLoadMoreTracks: Boolean = false,
    val canLoadMoreAlbums: Boolean = false,
    val canLoadMoreArtists: Boolean = false,
    val canEditTags: Boolean = true,
    val canScanSameNameLyrics: Boolean = true,
    val canBatchDownload: Boolean = true,
    val canSortByLocalPlayCount: Boolean = true,
)

data class LibraryBrowserActions(
    val onSearchChanged: (String) -> Unit = {},
    val onSourceFilterChanged: (LibrarySourceFilter) -> Unit = {},
    val onOnlineSourceSelected: (String) -> Unit = {},
    val onTrackSortChanged: (TrackSortMode) -> Unit = {},
    val onToggleFavorite: (Track) -> Unit = {},
    val onDismissMessage: () -> Unit = {},
    val onLoadMoreTracks: () -> Unit = {},
    val onLoadMoreAlbums: () -> Unit = {},
    val onLoadMoreArtists: () -> Unit = {},
    val onPrepareOnlineAlbumNavigation: (
        sourceId: String,
        albumId: String,
        albumTitle: String?,
        artistName: String?,
        artworkLocator: String?,
    ) -> Unit = { _, _, _, _, _ -> },
    val onPrepareOnlineArtistNavigation: (
        sourceId: String,
        artistId: String,
        artistName: String?,
    ) -> Unit = { _, _, _ -> },
    val onLoadAlbumTracks: (String) -> Unit = {},
    val onLoadArtistAlbums: (String) -> Unit = {},
    val onAlbumClick: (LibraryAlbumUiItem) -> Unit = {},
    val onArtistClick: (LibraryArtistUiItem) -> Unit = {},
    val onPlayTracks: (List<Track>, Int) -> Unit = { _, _ -> },
)

data class PlaylistBrowserUiState(
    val playlists: List<PlaylistSummary> = emptyList(),
    val selectedPlaylistId: String? = null,
    val selectedPlaylist: PlaylistDetail? = null,
    val isLoading: Boolean = false,
    val isOnline: Boolean = false,
    val sourceId: String? = null,
    val errorMessage: UiText? = null,
)

fun LibraryState.toBrowserUiState(
    favoriteTrackIds: Set<String> = emptySet(),
    message: UiText? = null,
): LibraryBrowserUiState {
    val albumCountByArtistId = albumCountByArtistId(filteredTracks)
    return LibraryBrowserUiState(
        query = query,
        tracks = filteredTracks.map { track ->
            LibraryTrackUiItem(
                id = track.id,
                track = track,
                sourceLabel = sourceLabelsById[track.sourceId],
                isFavorite = track.id in favoriteTrackIds,
            )
        },
        albums = filteredAlbums.map { album -> LibraryAlbumUiItem(id = album.id, album = album) },
        artists = filteredArtists.map { artist ->
            LibraryArtistUiItem(
                id = artist.id,
                artist = artist,
                trackCount = artist.trackCount,
                albumCount = albumCountByArtistId[artist.id] ?: 0,
            )
        },
        allTrackCount = tracks.size,
        trackCount = LibraryBrowserCount.exact(filteredTracks.size),
        albumCount = LibraryBrowserCount.exact(filteredAlbums.size),
        artistCount = LibraryBrowserCount.exact(filteredArtists.size),
        isLoading = isLoadingContent,
        isOnline = false,
        selectedSourceFilter = selectedSourceFilter,
        availableSourceFilters = availableSourceFilters,
        selectedTrackSortMode = selectedTrackSortMode,
        favoriteTrackIds = favoriteTrackIds,
        sourceLabelsById = sourceLabelsById,
        message = message,
    )
}

fun FavoritesState.toBrowserUiState(
    message: UiText? = this.message,
): LibraryBrowserUiState {
    val albumCountByArtistId = albumCountByArtistId(filteredTracks)
    return LibraryBrowserUiState(
        query = query,
        tracks = filteredTracks.map { track ->
            LibraryTrackUiItem(
                id = track.id,
                track = track,
                isFavorite = track.id in favoriteTrackIds,
            )
        },
        albums = filteredAlbums.map { album -> LibraryAlbumUiItem(id = album.id, album = album) },
        artists = filteredArtists.map { artist ->
            LibraryArtistUiItem(
                id = artist.id,
                artist = artist,
                trackCount = artist.trackCount,
                albumCount = albumCountByArtistId[artist.id] ?: 0,
            )
        },
        allTrackCount = tracks.size,
        trackCount = LibraryBrowserCount.exact(filteredTracks.size),
        albumCount = LibraryBrowserCount.exact(filteredAlbums.size),
        artistCount = LibraryBrowserCount.exact(filteredArtists.size),
        isLoading = isLoadingContent,
        isOnline = false,
        selectedSourceFilter = selectedSourceFilter,
        availableSourceFilters = availableSourceFilters,
        selectedTrackSortMode = selectedTrackSortMode,
        favoriteTrackIds = favoriteTrackIds,
        message = message,
    )
}

fun OnlineLibraryState.toBrowserUiState(
    message: UiText? = errorMessage,
): LibraryBrowserUiState {
    val hasActiveQuery = query.isNotBlank()
    return LibraryBrowserUiState(
        query = query,
        tracks = tracks.map { track -> LibraryTrackUiItem(id = track.id, track = track) },
        albums = albums.map { albumItem ->
            LibraryAlbumUiItem(
                id = albumItem.album.id,
                album = albumItem.album,
                artworkLocator = albumItem.artworkLocator,
            )
        },
        artists = artists.map { artistItem ->
            LibraryArtistUiItem(
                id = artistItem.artist.id,
                artist = artistItem.artist,
                trackCount = artistItem.trackCount,
                albumCount = artistItem.albumCount,
            )
        },
        allTrackCount = if (hasActiveQuery) {
            maxOf(tracks.size, albums.size, artists.size, 1)
        } else {
            tracks.size
        },
        isLoading = isLoading,
        isOnline = true,
        sourceId = sourceId,
        trackCount = LibraryBrowserCount(
            loaded = tracks.size,
            total = totalTrackCount,
            hasMore = canLoadMoreTracks,
        ),
        albumCount = LibraryBrowserCount(
            loaded = albums.size,
            total = totalAlbumCount,
            hasMore = canLoadMoreAlbums,
        ),
        artistCount = LibraryBrowserCount(
            loaded = artists.size,
            total = totalArtistCount,
            hasMore = canLoadMoreArtists,
        ),
        onlineAlbumTracksById = selectedAlbumTracks,
        onlineAlbumItemsById = knownAlbumItemsById.mapValues { (_, albumItem) ->
            LibraryAlbumUiItem(
                id = albumItem.album.id,
                album = albumItem.album,
                artworkLocator = albumItem.artworkLocator,
            )
        },
        onlineArtistItemsById = knownArtistItemsById.mapValues { (_, artistItem) ->
            LibraryArtistUiItem(
                id = artistItem.artist.id,
                artist = artistItem.artist,
                trackCount = artistItem.trackCount,
                albumCount = artistItem.albumCount,
            )
        },
        onlineArtistAlbumsById = selectedArtistAlbumsById.mapValues { (_, albums) ->
            albums.map { albumItem ->
                LibraryAlbumUiItem(
                    id = albumItem.album.id,
                    album = albumItem.album,
                    artworkLocator = albumItem.artworkLocator,
                )
            }
        },
        loadingAlbumIds = loadingAlbumIds,
        loadingArtistAlbumIds = loadingArtistAlbumIds,
        isLoadingMoreTracks = isLoadingMoreTracks,
        isLoadingMoreAlbums = isLoadingMoreAlbums,
        isLoadingMoreArtists = isLoadingMoreArtists,
        capabilities = onlineCapabilities(
            canLoadMoreTracks = canLoadMoreTracks,
            canLoadMoreAlbums = canLoadMoreAlbums,
            canLoadMoreArtists = canLoadMoreArtists,
        ),
        message = message,
        errorMessage = errorMessage,
    )
}

private fun albumCountByArtistId(tracks: List<Track>): Map<String, Int> {
    return tracks
        .groupBy { track ->
            track.artistName
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(::libraryArtistId)
        }
        .mapNotNull { (artistId, tracksForArtist) ->
            artistId?.let { it to deriveVisibleAlbums(tracksForArtist).size }
        }
        .toMap()
}

fun OnlineFavoritesState.toBrowserUiState(
    message: UiText? = errorMessage ?: this.message,
): LibraryBrowserUiState {
    val favoriteTrackIds = tracks.mapTo(linkedSetOf()) { it.id }
    val visibleTracks = filteredTracks
    val favoriteAlbumItems = albumItemsForTracks(visibleTracks)
    val favoriteArtistItems = artistItemsForTracks(visibleTracks)
    return LibraryBrowserUiState(
        query = query,
        tracks = visibleTracks.map { track ->
            LibraryTrackUiItem(
                id = track.id,
                track = track,
                isFavorite = true,
            )
        },
        albums = favoriteAlbumItems,
        artists = favoriteArtistItems,
        allTrackCount = tracks.size,
        isLoading = isLoading,
        isOnline = true,
        sourceId = sourceId,
        trackCount = LibraryBrowserCount(
            loaded = visibleTracks.size,
            total = tracks.size.takeIf { !canLoadMore },
            hasMore = canLoadMore,
        ),
        albumCount = LibraryBrowserCount(
            loaded = favoriteAlbumItems.size,
            total = favoriteAlbumItems.size.takeIf { !canLoadMore },
            hasMore = canLoadMore,
        ),
        artistCount = LibraryBrowserCount(
            loaded = favoriteArtistItems.size,
            total = favoriteArtistItems.size.takeIf { !canLoadMore },
            hasMore = canLoadMore,
        ),
        favoriteTrackIds = favoriteTrackIds,
        onlineAlbumItemsById = favoriteAlbumItems.associateBy { it.id },
        onlineArtistItemsById = favoriteArtistItems.associateBy { it.id },
        onlineAlbumTracksById = albumTracksById(visibleTracks),
        onlineArtistAlbumsById = artistAlbumsById(visibleTracks),
        isLoadingMoreTracks = isLoadingMore,
        capabilities = onlineCapabilities(canLoadMoreTracks = canLoadMore),
        message = message,
        errorMessage = errorMessage,
    )
}

private fun artistItemsForTracks(tracks: List<Track>): List<LibraryArtistUiItem> {
    val albumCountByArtistId = albumCountByArtistId(tracks)
    return deriveVisibleArtists(tracks).map { artist ->
        LibraryArtistUiItem(
            id = artist.id,
            artist = artist,
            trackCount = artist.trackCount,
            albumCount = albumCountByArtistId[artist.id] ?: 0,
        )
    }
}

private fun albumItemsForTracks(tracks: List<Track>): List<LibraryAlbumUiItem> {
    val tracksByAlbumId = albumTracksById(tracks)
    return deriveVisibleAlbums(tracks).map { album ->
        LibraryAlbumUiItem(
            id = album.id,
            album = album,
            artworkLocator = tracksByAlbumId[album.id].orEmpty().firstArtworkLocatorOrNull(),
        )
    }
}

private fun albumTracksById(tracks: List<Track>): Map<String, List<Track>> {
    return tracks
        .groupBy { it.albumLibraryIdOrNull() }
        .mapNotNull { (albumId, albumTracks) -> albumId?.let { it to albumTracks } }
        .toMap()
}

private fun artistAlbumsById(tracks: List<Track>): Map<String, List<LibraryAlbumUiItem>> {
    return tracks
        .groupBy { it.artistLibraryIdOrNull() }
        .mapNotNull { (artistId, artistTracks) ->
            artistId?.let { it to albumItemsForTracks(artistTracks) }
        }
        .toMap()
}

private fun List<Track>.firstArtworkLocatorOrNull(): String? {
    return firstNotNullOfOrNull { track -> track.artworkLocator?.takeIf { it.isNotBlank() } }
}

private fun Track.artistLibraryIdOrNull(): String? {
    return artistName?.trim()?.takeIf { it.isNotBlank() }?.let(::libraryArtistId)
}

private fun Track.albumLibraryIdOrNull(): String? {
    val albumTitle = this.albumTitle?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return libraryAlbumId(artistName, albumTitle)
}

fun PlaylistsState.toPlaylistUiState(): PlaylistBrowserUiState {
    return PlaylistBrowserUiState(
        playlists = playlists,
        selectedPlaylistId = selectedPlaylistId,
        selectedPlaylist = selectedPlaylist,
        isLoading = isLoadingContent,
        isOnline = false,
    )
}

fun OnlinePlaylistsState.toPlaylistUiState(): PlaylistBrowserUiState {
    return PlaylistBrowserUiState(
        playlists = playlists,
        selectedPlaylistId = selectedPlaylistId,
        selectedPlaylist = selectedPlaylist,
        isLoading = isLoading,
        isOnline = true,
        sourceId = sourceId,
        errorMessage = errorMessage,
    )
}

private fun onlineCapabilities(
    canLoadMoreTracks: Boolean = false,
    canLoadMoreAlbums: Boolean = false,
    canLoadMoreArtists: Boolean = false,
): LibraryBrowserCapabilities {
    return LibraryBrowserCapabilities(
        canLoadMoreTracks = canLoadMoreTracks,
        canLoadMoreAlbums = canLoadMoreAlbums,
        canLoadMoreArtists = canLoadMoreArtists,
        canEditTags = false,
        canScanSameNameLyrics = false,
        canBatchDownload = false,
        canSortByLocalPlayCount = false,
    )
}
