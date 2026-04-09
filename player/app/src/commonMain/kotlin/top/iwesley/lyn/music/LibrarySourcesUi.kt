package top.iwesley.lyn.music

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.feature.favorites.FavoritesIntent
import top.iwesley.lyn.music.feature.favorites.FavoritesState
import top.iwesley.lyn.music.feature.importing.ImportIntent
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.library.LibraryIntent
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.LibraryState
import top.iwesley.lyn.music.feature.library.deriveVisibleAlbums
import top.iwesley.lyn.music.feature.library.libraryAlbumId
import top.iwesley.lyn.music.feature.library.libraryArtistId
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.platform.PlatformBackHandler
import top.iwesley.lyn.music.ui.mainShellColors
import kotlin.math.roundToInt

@Composable
internal fun LibraryTab(
    state: LibraryState,
    favoritesState: FavoritesState,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    showFavoriteButton: Boolean = true,
    showDuration: Boolean = true,
    navigationTarget: LibraryNavigationTarget? = null,
    onNavigationHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LibraryBrowserTab(
        state = LibraryBrowserPageState(
            query = state.query,
            tracks = state.tracks,
            filteredTracks = state.filteredTracks,
            filteredAlbums = state.filteredAlbums,
            filteredArtists = state.filteredArtists,
            selectedSourceFilter = state.selectedSourceFilter,
            availableSourceFilters = state.availableSourceFilters,
            favoriteTrackIds = favoritesState.favoriteTrackIds,
            message = favoritesState.message,
        ),
        strings = LibraryBrowserStrings(
            searchLabel = "搜索歌曲 / 艺人 / 专辑",
            sectionTitle = "",
            sectionSubtitle = "",
            songsIcon = Icons.Rounded.LibraryMusic,
            emptyCollectionTitle = "曲库还是空的",
            emptyCollectionBody = "先到“来源”页导入本地文件夹、Samba、WebDAV 或 Navidrome，扫描完成后会出现在这里。",
            emptyFilterBody = "试试切回“全部来源”、更换过滤项，或调整搜索词。",
            emptySearchBody = "试试调整搜索词，或切换来源过滤。",
            trackLabel = "歌曲",
            albumLabel = "专辑",
            artistLabel = "艺人",
        ),
        onSearchChanged = { onLibraryIntent(LibraryIntent.SearchChanged(it)) },
        onSourceFilterChanged = { onLibraryIntent(LibraryIntent.SourceFilterChanged(it)) },
        onToggleFavorite = { onFavoritesIntent(FavoritesIntent.ToggleFavorite(it)) },
        onDismissMessage = { onFavoritesIntent(FavoritesIntent.ClearMessage) },
        onPlayTracks = { tracks, index -> onPlayerIntent(PlayerIntent.PlayTracks(tracks, index)) },
        showFavoriteButton = showFavoriteButton,
        showDuration = showDuration,
        navigationTarget = navigationTarget,
        onNavigationHandled = onNavigationHandled,
        modifier = modifier,
    )
}

@Composable
internal fun FavoritesTab(
    state: FavoritesState,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    showFavoriteButton: Boolean = true,
    showDuration: Boolean = true,
    modifier: Modifier = Modifier,
) {
    LibraryBrowserTab(
        state = LibraryBrowserPageState(
            query = state.query,
            tracks = state.tracks,
            filteredTracks = state.filteredTracks,
            filteredAlbums = state.filteredAlbums,
            filteredArtists = state.filteredArtists,
            selectedSourceFilter = state.selectedSourceFilter,
            availableSourceFilters = state.availableSourceFilters,
            favoriteTrackIds = state.favoriteTrackIds,
            message = state.message,
        ),
        strings = LibraryBrowserStrings(
            searchLabel = "搜索喜欢的歌曲 / 艺人 / 专辑",
            sectionTitle = "",
            sectionSubtitle = "",
            songsIcon = Icons.Rounded.Favorite,
            emptyCollectionTitle = "还没有喜欢的歌曲",
            emptyCollectionBody = "在曲库或播放器里点亮心形后，喜欢的歌曲会出现在这里。",
            emptyFilterBody = "试试切回“全部来源”、更换过滤项，或去其他来源里添加喜欢。",
            emptySearchBody = "试试调整搜索词，或切换来源过滤。",
            trackLabel = "喜欢的歌曲",
            albumLabel = "喜欢的专辑",
            artistLabel = "喜欢的艺人",
        ),
        onSearchChanged = { onFavoritesIntent(FavoritesIntent.SearchChanged(it)) },
        onSourceFilterChanged = { onFavoritesIntent(FavoritesIntent.SourceFilterChanged(it)) },
        onToggleFavorite = { onFavoritesIntent(FavoritesIntent.ToggleFavorite(it)) },
        actionButton = if (state.canRefreshRemote) {
            {
                OutlinedButton(
                    onClick = { onFavoritesIntent(FavoritesIntent.Refresh) },
                    enabled = !state.isRefreshing,
                ) {
                    Icon(Icons.Rounded.Sync, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.isRefreshing) "刷新中" else "刷新")
                }
            }
        } else {
            null
        },
        onDismissMessage = { onFavoritesIntent(FavoritesIntent.ClearMessage) },
        onPlayTracks = { tracks, index -> onPlayerIntent(PlayerIntent.PlayTracks(tracks, index)) },
        showFavoriteButton = showFavoriteButton,
        showDuration = showDuration,
        modifier = modifier,
    )
}

private data class LibraryBrowserPageState(
    val query: String,
    val tracks: List<Track>,
    val filteredTracks: List<Track>,
    val filteredAlbums: List<Album>,
    val filteredArtists: List<Artist>,
    val selectedSourceFilter: LibrarySourceFilter,
    val availableSourceFilters: List<LibrarySourceFilter>,
    val favoriteTrackIds: Set<String>,
    val message: String?,
)

private data class LibraryBrowserStrings(
    val searchLabel: String,
    val sectionTitle: String,
    val sectionSubtitle: String,
    val songsIcon: ImageVector,
    val emptyCollectionTitle: String,
    val emptyCollectionBody: String,
    val emptyFilterBody: String,
    val emptySearchBody: String,
    val trackLabel: String,
    val albumLabel: String,
    val artistLabel: String,
)

internal enum class LibraryBrowserRootView {
    Tracks,
    Albums,
    Artists,
}

@Composable
private fun LibraryBrowserTab(
    state: LibraryBrowserPageState,
    strings: LibraryBrowserStrings,
    onSearchChanged: (String) -> Unit,
    onSourceFilterChanged: (LibrarySourceFilter) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    showFavoriteButton: Boolean = true,
    showDuration: Boolean = true,
    actionButton: (@Composable () -> Unit)? = null,
    onDismissMessage: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    navigationTarget: LibraryNavigationTarget? = null,
    onNavigationHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var sourceFilterMenuExpanded by remember { mutableStateOf(false) }
    var rootView by rememberSaveable { mutableStateOf(LibraryBrowserRootView.Tracks) }
    var selectedArtistId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    when (resolveLibraryBrowserBackTarget(selectedArtistId, selectedAlbumId)) {
        LibraryBrowserBackTarget.Album -> {
            PlatformBackHandler { selectedAlbumId = null }
        }

        LibraryBrowserBackTarget.Artist -> {
            PlatformBackHandler {
                selectedArtistId = null
                selectedAlbumId = null
            }
        }

        null -> Unit
    }
    val tracksByArtistId = remember(state.filteredTracks) {
        state.filteredTracks.groupBy(Track::artistLibraryIdOrNull)
    }
    val tracksByAlbumId = remember(state.filteredTracks) {
        state.filteredTracks.groupBy(Track::albumLibraryIdOrNull)
    }
    val artistAlbumCountById = remember(tracksByArtistId) {
        tracksByArtistId.entries
            .mapNotNull { (artistId, tracks) ->
                artistId?.let { it to deriveVisibleAlbums(tracks).size }
            }
            .toMap()
    }
    val selectedArtist = remember(state.filteredArtists, selectedArtistId) {
        state.filteredArtists.firstOrNull { it.id == selectedArtistId }
    }
    val artistTracks = remember(tracksByArtistId, selectedArtistId) {
        tracksByArtistId[selectedArtistId].orEmpty().sortedWith(ARTIST_DETAIL_TRACK_COMPARATOR)
    }
    val artistAlbums = remember(artistTracks) {
        deriveVisibleAlbums(artistTracks)
    }
    val selectedAlbum =
        remember(state.filteredAlbums, artistAlbums, selectedAlbumId, selectedArtistId) {
            when {
                selectedAlbumId == null -> null
                selectedArtistId != null -> artistAlbums.firstOrNull { it.id == selectedAlbumId }
                else -> state.filteredAlbums.firstOrNull { it.id == selectedAlbumId }
            }
        }
    val albumTracks = remember(tracksByAlbumId, artistTracks, selectedAlbumId, selectedArtistId) {
        when {
            selectedAlbumId == null -> emptyList()
            selectedArtistId != null -> artistTracks.filter { it.albumLibraryIdOrNull() == selectedAlbumId }
            else -> tracksByAlbumId[selectedAlbumId].orEmpty()
        }.sortedWith(ALBUM_DETAIL_TRACK_COMPARATOR)
    }
    LaunchedEffect(
        navigationTarget,
        state.query,
        state.selectedSourceFilter,
        state.filteredAlbums,
        state.filteredArtists,
    ) {
        val target = navigationTarget ?: return@LaunchedEffect
        when (
            val command = resolveLibraryNavigationCommand(
                target = target,
                query = state.query,
                selectedSourceFilter = state.selectedSourceFilter,
                filteredAlbums = state.filteredAlbums,
                filteredArtists = state.filteredArtists,
            )
        ) {
            LibraryNavigationCommand.ResetFilters -> {
                if (state.query.isNotBlank()) {
                    onSearchChanged("")
                }
                if (state.selectedSourceFilter != LibrarySourceFilter.ALL) {
                    onSourceFilterChanged(LibrarySourceFilter.ALL)
                }
            }

            is LibraryNavigationCommand.Navigate -> {
                rootView = command.resolution.rootView
                selectedArtistId = command.resolution.selectedArtistId
                selectedAlbumId = command.resolution.selectedAlbumId
                onNavigationHandled()
            }
        }
    }

    LaunchedEffect(
        rootView,
        state.filteredAlbums,
        state.filteredArtists,
        selectedArtistId,
        selectedAlbumId,
        artistAlbums,
    ) {
        when (rootView) {
            LibraryBrowserRootView.Tracks -> {
                if (selectedArtistId != null) selectedArtistId = null
                if (selectedAlbumId != null) selectedAlbumId = null
            }

            LibraryBrowserRootView.Albums -> {
                if (selectedArtistId != null) selectedArtistId = null
                if (selectedAlbumId != null && state.filteredAlbums.none { it.id == selectedAlbumId }) {
                    selectedAlbumId = null
                }
            }

            LibraryBrowserRootView.Artists -> {
                if (selectedArtistId != null && state.filteredArtists.none { it.id == selectedArtistId }) {
                    selectedArtistId = null
                    selectedAlbumId = null
                } else if (selectedAlbumId != null && artistAlbums.none { it.id == selectedAlbumId }) {
                    selectedAlbumId = null
                }
            }
        }
    }

    fun selectRootView(view: LibraryBrowserRootView) {
        rootView = view
        selectedArtistId = null
        selectedAlbumId = null
    }

    val shellColors = mainShellColors
    val searchFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = shellColors.cardBorder,
        unfocusedBorderColor = shellColors.cardBorder,
        disabledBorderColor = shellColors.cardBorder,
    )

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 42.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                ImeAwareOutlinedTextField(
                    value = state.query,
                    onValueChange = onSearchChanged,
                    label = { Text(strings.searchLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = searchFieldColors,
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        OutlinedButton(onClick = { sourceFilterMenuExpanded = true }) {
                            Icon(Icons.Rounded.Tune, null)
                            Spacer(Modifier.width(8.dp))
                            Text(librarySourceFilterButtonLabel(state.selectedSourceFilter))
                        }
                        DropdownMenu(
                            expanded = sourceFilterMenuExpanded,
                            onDismissRequest = { sourceFilterMenuExpanded = false },
                            containerColor = mainShellColors.navContainer,
                        ) {
                            state.availableSourceFilters.forEach { filter ->
                                DropdownMenuItem(
                                    text = { Text(librarySourceFilterMenuLabel(filter)) },
                                    onClick = {
                                        sourceFilterMenuExpanded = false
                                        onSourceFilterChanged(filter)
                                    },
                                )
                            }
                        }
                    }
                    actionButton?.invoke()
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        title = "歌曲",
                        value = state.filteredTracks.size.toString(),
                        icon = strings.songsIcon,
                        selected = rootView == LibraryBrowserRootView.Tracks,
                        onClick = { selectRootView(LibraryBrowserRootView.Tracks) },
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        title = "专辑",
                        value = state.filteredAlbums.size.toString(),
                        icon = Icons.Rounded.Album,
                        selected = rootView == LibraryBrowserRootView.Albums,
                        onClick = { selectRootView(LibraryBrowserRootView.Albums) },
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        title = "艺人",
                        value = state.filteredArtists.size.toString(),
                        icon = Icons.Rounded.Tune,
                        selected = rootView == LibraryBrowserRootView.Artists,
                        onClick = { selectRootView(LibraryBrowserRootView.Artists) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            state.message?.let { message ->
                item {
                    BannerCard(
                        message = message,
                        onDismiss = onDismissMessage,
                    )
                }
            }
            when {
                selectedAlbum != null -> {
                    item {
                        DetailBackButton(onClick = { selectedAlbumId = null })
                    }
                    item {
                        DetailSummaryCard(
                            title = selectedAlbum.title,
                            subtitle = selectedAlbum.artistName ?: "未知艺人",
                            supportingText = "${albumTracks.size} 首歌曲",
                            artworkLocator = albumTracks.firstOrNull()?.artworkLocator,
                        )
                    }
                    item {
                        SectionTitle(title = "歌曲", subtitle = "当前专辑下的可见歌曲。")
                    }
                    if (albumTracks.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "这个专辑暂时没有歌曲",
                                body = "当前筛选结果里已经没有这个专辑的可见歌曲。",
                            )
                        }
                    } else {
                        itemsIndexed(albumTracks, key = { _, item -> item.id }) { index, track ->
                            TrackRow(
                                track = track,
                                index = index,
                                isFavorite = track.id in state.favoriteTrackIds,
                                onToggleFavorite = { onToggleFavorite(track) },
                                showFavoriteButton = showFavoriteButton,
                                showDuration = showDuration,
                                onClick = {
                                    onPlayTracks(albumTracks, index)
                                },
                            )
                        }
                    }
                }

                rootView == LibraryBrowserRootView.Artists && selectedArtist != null -> {
                    item {
                        DetailBackButton(
                            onClick = {
                                selectedArtistId = null
                                selectedAlbumId = null
                            },
                        )
                    }
                    item {
                        DetailSummaryCard(
                            title = selectedArtist.name,
                            subtitle = "${artistTracks.size} 首歌曲 · ${artistAlbums.size} 张专辑",
                            supportingText = "当前筛选结果中的艺人详情",
                            artworkLocator = artistTracks.firstOrNull()?.artworkLocator,
                        )
                    }
                    item {
                        SectionTitle(title = "专辑", subtitle = "当前艺人下的可见专辑。")
                    }
                    if (artistAlbums.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "这个艺人下暂无专辑信息",
                                body = "当前艺人的可见歌曲还没有可用的专辑标签。",
                            )
                        }
                    } else {
                        items(artistAlbums, key = { it.id }) { album ->
                            AlbumRow(
                                album = album,
                                artworkLocator = artistTracks.firstOrNull { it.albumLibraryIdOrNull() == album.id }?.artworkLocator,
                                onClick = { selectedAlbumId = album.id },
                            )
                        }
                    }
                    item {
                        SectionTitle(title = "歌曲", subtitle = "当前艺人下的可见歌曲。")
                    }
                    if (artistTracks.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "这个艺人暂时没有歌曲",
                                body = "当前筛选结果里已经没有这个艺人的可见歌曲。",
                            )
                        }
                    } else {
                        itemsIndexed(artistTracks, key = { _, item -> item.id }) { index, track ->
                            TrackRow(
                                track = track,
                                index = index,
                                isFavorite = track.id in state.favoriteTrackIds,
                                onToggleFavorite = { onToggleFavorite(track) },
                                showFavoriteButton = showFavoriteButton,
                                showDuration = showDuration,
                                onClick = {
                                    onPlayTracks(artistTracks, index)
                                },
                            )
                        }
                    }
                }

                else -> {
                    val currentItemCount = when (rootView) {
                        LibraryBrowserRootView.Tracks -> state.filteredTracks.size
                        LibraryBrowserRootView.Albums -> state.filteredAlbums.size
                        LibraryBrowserRootView.Artists -> state.filteredArtists.size
                    }
                    val currentLabel = when (rootView) {
                        LibraryBrowserRootView.Tracks -> strings.trackLabel
                        LibraryBrowserRootView.Albums -> strings.albumLabel
                        LibraryBrowserRootView.Artists -> strings.artistLabel
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                SectionTitle(
                                    title = strings.sectionTitle,
                                    subtitle = strings.sectionSubtitle
                                )
                            }
                        }
                    }
                    if (currentItemCount == 0) {
                        item {
                            when {
                                state.tracks.isEmpty() -> EmptyStateCard(
                                    title = strings.emptyCollectionTitle,
                                    body = strings.emptyCollectionBody,
                                )

                                state.selectedSourceFilter != LibrarySourceFilter.ALL -> EmptyStateCard(
                                    title = "当前来源下没有$currentLabel",
                                    body = strings.emptyFilterBody,
                                )

                                else -> EmptyStateCard(
                                    title = "没有匹配的$currentLabel",
                                    body = strings.emptySearchBody,
                                )
                            }
                        }
                    } else {
                        when (rootView) {
                            LibraryBrowserRootView.Tracks -> {
                                itemsIndexed(
                                    state.filteredTracks,
                                    key = { _, item -> item.id }) { index, track ->
                                    TrackRow(
                                        track = track,
                                        index = index,
                                        isFavorite = track.id in state.favoriteTrackIds,
                                        onToggleFavorite = { onToggleFavorite(track) },
                                        showFavoriteButton = showFavoriteButton,
                                        showDuration = showDuration,
                                        onClick = {
                                            onPlayTracks(state.filteredTracks, index)
                                        },
                                    )
                                }
                            }

                            LibraryBrowserRootView.Albums -> {
                                items(state.filteredAlbums, key = { it.id }) { album ->
                                    AlbumRow(
                                        album = album,
                                        artworkLocator = tracksByAlbumId[album.id].orEmpty()
                                            .firstOrNull()?.artworkLocator,
                                        onClick = { selectedAlbumId = album.id },
                                    )
                                }
                            }

                            LibraryBrowserRootView.Artists -> {
                                items(state.filteredArtists, key = { it.id }) { artist ->
                                    ArtistRow(
                                        artist = artist,
                                        albumCount = artistAlbumCountById[artist.id] ?: 0,
                                        onClick = {
                                            selectedArtistId = artist.id
                                            selectedAlbumId = null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        LibraryFastScrollbar(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 8.dp, top = 20.dp, bottom = 20.dp),
        )
    }
}

private val ALBUM_DETAIL_TRACK_COMPARATOR = compareBy<Track>(
    { it.discNumber ?: Int.MAX_VALUE },
    { it.trackNumber ?: Int.MAX_VALUE },
    { it.title.lowercase() },
)

private val ARTIST_DETAIL_TRACK_COMPARATOR = compareBy<Track>(
    { it.albumTitle.orEmpty().lowercase() },
    { it.discNumber ?: Int.MAX_VALUE },
    { it.trackNumber ?: Int.MAX_VALUE },
    { it.title.lowercase() },
)

private fun Track.artistLibraryIdOrNull(): String? {
    return artistName?.trim()?.takeIf { it.isNotBlank() }?.let(::libraryArtistId)
}

private fun Track.albumLibraryIdOrNull(): String? {
    val title = albumTitle?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return libraryAlbumId(artistName, title)
}

private fun librarySourceFilterButtonLabel(filter: LibrarySourceFilter): String {
    return when (filter) {
        LibrarySourceFilter.ALL -> "全部来源"
        LibrarySourceFilter.LOCAL_FOLDER -> "本地文件夹"
        LibrarySourceFilter.SAMBA -> "Samba"
        LibrarySourceFilter.WEBDAV -> "WebDAV"
        LibrarySourceFilter.NAVIDROME -> "Navidrome"
    }
}

private fun librarySourceFilterMenuLabel(filter: LibrarySourceFilter): String {
    return when (filter) {
        LibrarySourceFilter.ALL -> "全部"
        else -> librarySourceFilterButtonLabel(filter)
    }
}

@Composable
private fun LibraryFastScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    val totalItemsCount by remember(listState) {
        derivedStateOf { listState.layoutInfo.totalItemsCount }
    }
    val visibleItemsInfo by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo }
    }
    if (totalItemsCount <= 0 || totalItemsCount <= visibleItemsInfo.size) return
    val visibleFraction by remember(totalItemsCount, visibleItemsInfo) {
        derivedStateOf {
            (visibleItemsInfo.size.toFloat() / totalItemsCount.toFloat()).coerceIn(0.12f, 0.45f)
        }
    }
    val scrollFraction by remember(listState, totalItemsCount, visibleItemsInfo) {
        derivedStateOf {
            if (totalItemsCount <= 1) {
                0f
            } else {
                val firstVisibleSize = visibleItemsInfo.firstOrNull()?.size?.takeIf { it > 0 } ?: 1
                val exactIndex =
                    listState.firstVisibleItemIndex + (listState.firstVisibleItemScrollOffset / firstVisibleSize.toFloat())
                (exactIndex / (totalItemsCount - 1).toFloat()).coerceIn(0f, 1f)
            }
        }
    }
    val thumbHeightPx = trackSize.height * visibleFraction
    val thumbOffsetPx = (trackSize.height - thumbHeightPx).coerceAtLeast(0f) * scrollFraction
    val thumbHeightDp = with(density) { thumbHeightPx.toDp() }
    val thumbOffsetDp = with(density) { thumbOffsetPx.toDp() }

    fun scrollToFraction(y: Float) {
        if (trackSize.height <= 0 || totalItemsCount <= 1) return
        val fraction = (y / trackSize.height.toFloat()).coerceIn(0f, 1f)
        val targetIndex = (fraction * (totalItemsCount - 1)).roundToInt()
        coroutineScope.launch {
            listState.scrollToItem(targetIndex)
        }
    }

    Box(
        modifier = modifier
            .width(18.dp)
            .onSizeChanged { trackSize = it }
            .pointerInput(totalItemsCount, trackSize) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> scrollToFraction(offset.y) },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        scrollToFraction(change.position.y)
                    },
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        val shellColors = mainShellColors
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(shellColors.cardBorder),
        )
        Box(
            modifier = Modifier
                .offset(y = thumbOffsetDp)
                .height(thumbHeightDp)
                .width(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.secondary)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.18f)),
                    RoundedCornerShape(999.dp),
                ),
        )
    }
}

@Composable
internal fun SourcesTab(
    platform: PlatformDescriptor,
    state: ImportState,
    onImportIntent: (ImportIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    var pendingDeleteSourceId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDeleteSource = remember(state.sources, pendingDeleteSourceId) {
        state.sources.firstOrNull { it.source.id == pendingDeleteSourceId }
    }
    LaunchedEffect(pendingDeleteSourceId, pendingDeleteSource) {
        if (pendingDeleteSourceId != null && pendingDeleteSource == null) {
            pendingDeleteSourceId = null
        }
    }
    val importFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = shellColors.cardBorder,
        unfocusedBorderColor = shellColors.cardBorder,
        disabledBorderColor = shellColors.cardBorder,
    )
    pendingDeleteSource?.let { source ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSourceId = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = shellColors.cardContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("删除来源") },
            text = { Text("确认删除“${source.source.label}”吗？已索引歌曲和相关缓存会一起移除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteSourceId = null
                        onImportIntent(ImportIntent.DeleteSource(source.source.id))
                    },
                    enabled = !state.isWorking,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteSourceId = null },
                    enabled = !state.isWorking,
                ) {
                    Text("取消")
                }
            },
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(
            title = "导入来源",
            subtitle = "本地文件夹原地索引，Samba、WebDAV 与 Navidrome 作为远程音乐库。"
        )
        state.message?.let { message ->
            BannerCard(message = message, onDismiss = { onImportIntent(ImportIntent.ClearMessage) })
        }
        MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("本地文件夹", fontWeight = FontWeight.Bold)
                Text(
                    if (platform.name == "Android") {
                        "导入时会优先请求“管理所有文件”权限，用于后续编辑音乐标签；未授权时会回退到 SAF 只读导入。"
                    } else {
                        "通过系统文件夹选择器授予目录权限并建立索引。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { onImportIntent(ImportIntent.ImportLocalFolder) },
                    enabled = state.capabilities.supportsLocalFolderImport && !state.isWorking,
                ) {
                    Icon(Icons.Rounded.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("选择文件夹")
                }
            }
        }
        MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Navidrome", fontWeight = FontWeight.Bold)
                if (!state.capabilities.supportsNavidromeImport) {
                    Text("当前平台暂未开放应用内 Navidrome 导入。")
                }
                ImeAwareOutlinedTextField(
                    value = state.navidromeLabel,
                    onValueChange = { onImportIntent(ImportIntent.NavidromeLabelChanged(it)) },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = importFieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.navidromeBaseUrl,
                    onValueChange = { onImportIntent(ImportIntent.NavidromeBaseUrlChanged(it)) },
                    label = { Text("服务器地址") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = importFieldColors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImeAwareOutlinedTextField(
                        value = state.navidromeUsername,
                        onValueChange = { onImportIntent(ImportIntent.NavidromeUsernameChanged(it)) },
                        label = { Text("用户名") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = importFieldColors,
                    )
                    ImeAwareOutlinedTextField(
                        value = state.navidromePassword,
                        onValueChange = { onImportIntent(ImportIntent.NavidromePasswordChanged(it)) },
                        label = { Text("密码") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = importFieldColors,
                    )
                }
                Button(
                    onClick = { onImportIntent(ImportIntent.AddNavidromeSource) },
                    enabled = state.capabilities.supportsNavidromeImport && !state.isWorking,
                ) {
                    Icon(Icons.Rounded.CloudSync, null)
                    Spacer(Modifier.width(8.dp))
                    Text("连接并同步")
                }
            }
        }
        MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Samba / SMB", fontWeight = FontWeight.Bold)
                if (!state.capabilities.supportsSambaImport) {
                    Text("当前平台建议通过系统 Files 挂载 SMB 后，再用本地文件夹方式接入。")
                }
                ImeAwareOutlinedTextField(
                    value = state.sambaLabel,
                    onValueChange = { onImportIntent(ImportIntent.SambaLabelChanged(it)) },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = importFieldColors
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImeAwareOutlinedTextField(
                        value = state.sambaServer,
                        onValueChange = { onImportIntent(ImportIntent.SambaServerChanged(it)) },
                        label = { Text("服务器地址") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = importFieldColors
                    )
                    ImeAwareOutlinedTextField(
                        value = state.sambaPort,
                        onValueChange = { onImportIntent(ImportIntent.SambaPortChanged(it)) },
                        label = { Text("端口") },
                        modifier = Modifier.width(140.dp),
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = importFieldColors,
                    )
                }
                ImeAwareOutlinedTextField(
                    value = state.sambaPath,
                    onValueChange = { onImportIntent(ImportIntent.SambaPathChanged(it)) },
                    label = { Text("路径（Share/子目录）") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = importFieldColors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImeAwareOutlinedTextField(
                        value = state.sambaUsername,
                        onValueChange = { onImportIntent(ImportIntent.SambaUsernameChanged(it)) },
                        label = { Text("用户名") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = importFieldColors
                    )
                    ImeAwareOutlinedTextField(
                        value = state.sambaPassword,
                        onValueChange = { onImportIntent(ImportIntent.SambaPasswordChanged(it)) },
                        label = { Text("密码（选填）") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = importFieldColors
                    )
                }
                Button(
                    onClick = { onImportIntent(ImportIntent.AddSambaSource) },
                    enabled = !state.isWorking,
                ) {
                    Icon(Icons.Rounded.CloudSync, null)
                    Spacer(Modifier.width(8.dp))
                    Text("连接并扫描")
                }
            }
        }
        MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("WebDAV", fontWeight = FontWeight.Bold)
                if (!state.capabilities.supportsWebDavImport) {
                    Text("当前平台暂未开放应用内 WebDAV 导入。")
                }
                ImeAwareOutlinedTextField(
                    value = state.webDavLabel,
                    onValueChange = { onImportIntent(ImportIntent.WebDavLabelChanged(it)) },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = importFieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.webDavRootUrl,
                    onValueChange = { onImportIntent(ImportIntent.WebDavRootUrlChanged(it)) },
                    label = { Text("根 URL") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = importFieldColors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImeAwareOutlinedTextField(
                        value = state.webDavUsername,
                        onValueChange = { onImportIntent(ImportIntent.WebDavUsernameChanged(it)) },
                        label = { Text("用户名（选填）") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = importFieldColors,
                    )
                    ImeAwareOutlinedTextField(
                        value = state.webDavPassword,
                        onValueChange = { onImportIntent(ImportIntent.WebDavPasswordChanged(it)) },
                        label = { Text("密码（选填）") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = importFieldColors,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("允许自签名证书", fontWeight = FontWeight.Medium)
                    Switch(
                        checked = state.webDavAllowInsecureTls,
                        onCheckedChange = {
                            onImportIntent(
                                ImportIntent.WebDavAllowInsecureTlsChanged(
                                    it
                                )
                            )
                        },
                        colors = SwitchDefaults.colors(
                            uncheckedThumbColor = MaterialTheme.colorScheme.background,
                            uncheckedBorderColor = shellColors.cardBorder,
                        ),
                    )
                }
                Button(
                    onClick = { onImportIntent(ImportIntent.AddWebDavSource) },
                    enabled = state.capabilities.supportsWebDavImport && !state.isWorking,
                ) {
                    Icon(Icons.Rounded.CloudSync, null)
                    Spacer(Modifier.width(8.dp))
                    Text("连接并扫描")
                }
            }
        }
        SectionTitle(title = "已连接来源", subtitle = "重新扫描会刷新索引与歌词缓存匹配。")
        if (state.sources.isEmpty()) {
            EmptyStateCard(
                title = "还没有任何来源",
                body = "添加来源后，歌曲会在曲库里汇总显示，播放页会根据当前歌曲去匹配歌词。",
            )
        } else {
            state.sources.forEach { source ->
                SourceCard(
                    state = source,
                    enabled = !state.isWorking,
                    onRescan = { onImportIntent(ImportIntent.RescanSource(source.source.id)) },
                    onDelete = { pendingDeleteSourceId = source.source.id },
                )
            }
        }
    }
}
