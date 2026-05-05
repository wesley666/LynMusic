package top.iwesley.lyn.music.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.rememberDrawerState
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.RecentAlbum
import top.iwesley.lyn.music.core.model.RecentTrack
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.normalizedArtworkCacheLocator
import top.iwesley.lyn.music.core.model.resolveArtworkCacheTarget
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.feature.favorites.FavoritesState
import top.iwesley.lyn.music.feature.library.LibraryState
import top.iwesley.lyn.music.feature.library.libraryAlbumId
import top.iwesley.lyn.music.feature.library.libraryArtistId
import top.iwesley.lyn.music.feature.my.MyState
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.tv.TvMediaDetailActivity
import top.iwesley.lyn.music.tv.TvMediaDetailSource
import top.iwesley.lyn.music.tv.TvPlayerActivity
import top.iwesley.lyn.music.tv.TvSettingsActivity

private val TvPanelShape = RoundedCornerShape(18.dp)
private val TvCardShape = RoundedCornerShape(14.dp)
private val TvNavigationItemShape = RoundedCornerShape(14.dp)
private val TvClosedDrawerWidth = 88.dp
private val TvOpenDrawerWidth = 260.dp
private val TvContentStartPadding = 112.dp

@Composable
internal fun TvMainScreen(
    state: TvMainState,
    myState: MyState,
    libraryState: LibraryState,
    favoritesState: FavoritesState,
    playerState: PlayerState,
    artworkCacheStore: ArtworkCacheStore,
    onIntent: (TvMainIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val context = LocalContext.current
    val allowContentInitialFocus = drawerState.currentValue == DrawerValue.Closed
    val myNavFocusRequester = remember { FocusRequester() }
    val libraryNavFocusRequester = remember { FocusRequester() }
    val favoritesNavFocusRequester = remember { FocusRequester() }

    BackHandler(
        enabled = state.searchDialog != null ||
            state.libraryDetail != null ||
            state.favoritesDetail != null,
    ) {
        onIntent(TvMainIntent.Back)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { drawerValue ->
            TvNavigationRail(
                drawerValue = drawerValue,
                selectedDestination = state.selectedDestination,
                myFocusRequester = myNavFocusRequester,
                libraryFocusRequester = libraryNavFocusRequester,
                favoritesFocusRequester = favoritesNavFocusRequester,
                playerState = playerState,
                artworkCacheStore = artworkCacheStore,
                onDestinationSelected = { onIntent(TvMainIntent.SelectDestination(it)) },
                onPlayerIntent = onPlayerIntent,
                onOpenPlayer = { context.startActivity(TvPlayerActivity.createIntent(context)) },
                onOpenSettings = { context.startActivity(TvSettingsActivity.createIntent(context)) },
            )
        },
        scrimBrush = SolidColor(Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = TvContentStartPadding, top = 28.dp, end = 36.dp, bottom = 26.dp),
        ) {
            when (state.selectedDestination) {
                TvMainDestination.My -> TvMyScreen(
                    state = myState,
                    artworkCacheStore = artworkCacheStore,
                    onIntent = onIntent,
                    modifier = Modifier.fillMaxSize(),
                )

                TvMainDestination.Library -> TvLibraryScreen(
                    state = libraryState,
                    favoritesState = favoritesState,
                    tvState = state,
                    artworkCacheStore = artworkCacheStore,
                    allowInitialFocus = allowContentInitialFocus,
                    onIntent = onIntent,
                    modifier = Modifier.fillMaxSize(),
                )

                TvMainDestination.Favorites -> TvFavoritesScreen(
                    state = favoritesState,
                    tvState = state,
                    artworkCacheStore = artworkCacheStore,
                    allowInitialFocus = allowContentInitialFocus,
                    onIntent = onIntent,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    state.searchDialog?.let { dialogState ->
        TvSearchDialog(
            state = dialogState,
            onTextChanged = { onIntent(TvMainIntent.SearchTextChanged(it)) },
            onSubmit = { onIntent(TvMainIntent.SubmitSearch) },
            onClear = { onIntent(TvMainIntent.ClearSearch(dialogState.target)) },
            onDismiss = { onIntent(TvMainIntent.DismissSearch) },
        )
    }
}

@Composable
private fun NavigationDrawerScope.TvNavigationRail(
    drawerValue: DrawerValue,
    selectedDestination: TvMainDestination,
    myFocusRequester: FocusRequester,
    libraryFocusRequester: FocusRequester,
    favoritesFocusRequester: FocusRequester,
    playerState: PlayerState,
    artworkCacheStore: ArtworkCacheStore,
    onDestinationSelected: (TvMainDestination) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val expanded = drawerValue == DrawerValue.Open
    Column(
        modifier = Modifier
            .width(if (expanded) TvOpenDrawerWidth else TvClosedDrawerWidth)
            .fillMaxHeight()
            .focusProperties {
                onEnter = {
                    if (requestedFocusDirection == FocusDirection.Left) {
                        when (selectedDestination) {
                            TvMainDestination.My -> myFocusRequester
                            TvMainDestination.Library -> libraryFocusRequester
                            TvMainDestination.Favorites -> favoritesFocusRequester
                        }.requestFocus()
                    }
                }
            }
            .focusGroup()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = if (expanded) 18.dp else 14.dp, vertical = 26.dp)
            .selectableGroup(),
        horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "LynMusic",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = "设置")
                }
            }
        } else {
            Spacer(Modifier.height(58.dp))
        }
        TvDestinationItem(
            destination = TvMainDestination.My,
            label = "我的",
            icon = Icons.Rounded.Person,
            selected = selectedDestination == TvMainDestination.My,
            focusRequester = myFocusRequester,
            onClick = onDestinationSelected,
        )
        TvDestinationItem(
            destination = TvMainDestination.Library,
            label = "曲库",
            icon = Icons.Rounded.LibraryMusic,
            selected = selectedDestination == TvMainDestination.Library,
            focusRequester = libraryFocusRequester,
            onClick = onDestinationSelected,
        )
        TvDestinationItem(
            destination = TvMainDestination.Favorites,
            label = "喜欢",
            icon = Icons.Rounded.Favorite,
            selected = selectedDestination == TvMainDestination.Favorites,
            focusRequester = favoritesFocusRequester,
            onClick = onDestinationSelected,
        )
        Spacer(Modifier.weight(1f))
        TvSideNowPlayingPanel(
            expanded = expanded,
            state = playerState,
            artworkCacheStore = artworkCacheStore,
            onPlayerIntent = onPlayerIntent,
            onOpenPlayer = onOpenPlayer,
        )
    }
}

@Composable
private fun NavigationDrawerScope.TvDestinationItem(
    destination: TvMainDestination,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: (TvMainDestination) -> Unit,
) {
    val selectDestination = {
        if (!selected) {
            onClick(destination)
        }
    }
    NavigationDrawerItem(
        selected = selected,
        onClick = selectDestination,
        shape = NavigationDrawerItemDefaults.shape(
            shape = TvNavigationItemShape,
            focusedShape = TvNavigationItemShape,
            pressedShape = TvNavigationItemShape,
            selectedShape = TvNavigationItemShape,
            focusedSelectedShape = TvNavigationItemShape,
            pressedSelectedShape = TvNavigationItemShape,
        ),
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    selectDestination()
                }
            },
        leadingContent = {
            androidx.tv.material3.Icon(
                imageVector = icon,
                contentDescription = null,
            )
        },
    ) {
        androidx.tv.material3.Text(label)
    }
}

@Composable
private fun TvMyScreen(
    state: MyState,
    artworkCacheStore: ArtworkCacheStore,
    onIntent: (TvMainIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dailyTracks = state.dailyRecommendationTracks
    val recentTracks = remember(state.recentTracks) { state.recentTracks.map(RecentTrack::track) }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            TvSectionHeader(
                title = "我的",
                subtitle = "最近播放和每日推荐",
                action = null,
            )
        }
        state.message?.let { message ->
            item { TvMessagePanel(message = message) }
        }
        item {
            TvFeaturedTracksPanel(
                title = "每日推荐",
                subtitle = if (state.isGeneratingDailyRecommendation) "正在生成" else "${dailyTracks.size} 首",
                icon = Icons.Rounded.Today,
                tracks = dailyTracks,
                artworkCacheStore = artworkCacheStore,
                onPlayAll = {
                    if (dailyTracks.isNotEmpty()) {
                        onIntent(TvMainIntent.PlayTracks(dailyTracks, 0))
                    }
                },
                onPlayTrack = { index -> onIntent(TvMainIntent.PlayTracks(dailyTracks, index)) },
            )
        }
        item {
            TvTrackPreviewPanel(
                title = "最近播放",
                tracks = recentTracks,
                artworkCacheStore = artworkCacheStore,
                onPlayTrack = { index -> onIntent(TvMainIntent.PlayTracks(recentTracks, index)) },
            )
        }
        item {
            TvRecentAlbumsPanel(
                albums = state.recentAlbums,
                onOpenAlbum = { recentAlbum ->
                    context.startActivity(
                        TvMediaDetailActivity.createIntent(
                            context = context,
                            source = TvMediaDetailSource.Library,
                            mode = TvMediaBrowserMode.Albums,
                            id = recentAlbum.album.id,
                            title = recentAlbum.album.title,
                            subtitle = recentAlbum.album.artistName,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TvLibraryScreen(
    state: LibraryState,
    favoritesState: FavoritesState,
    tvState: TvMainState,
    artworkCacheStore: ArtworkCacheStore,
    allowInitialFocus: Boolean,
    onIntent: (TvMainIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    TvMediaBrowserScreen(
        title = "曲库",
        emptyTitle = "曲库还是空的",
        emptyBody = "先导入本地文件夹、Samba、WebDAV 或 Navidrome，扫描完成后会出现在这里。",
        isLoading = state.isLoadingContent,
        query = state.query,
        mode = tvState.libraryMode,
        detail = tvState.libraryDetail,
        tracks = state.filteredTracks,
        albums = state.filteredAlbums,
        artists = state.filteredArtists,
        favoriteTrackIds = favoritesState.favoriteTrackIds,
        artworkCacheStore = artworkCacheStore,
        allowInitialFocus = allowInitialFocus,
        onModeSelected = { onIntent(TvMainIntent.SelectLibraryMode(it)) },
        onOpenSearch = { onIntent(TvMainIntent.OpenSearch(TvSearchTarget.Library, state.query)) },
        onClearSearch = { onIntent(TvMainIntent.ClearSearch(TvSearchTarget.Library)) },
        onOpenDetail = { detail ->
            context.startActivity(
                TvMediaDetailActivity.createIntent(
                    context = context,
                    source = TvMediaDetailSource.Library,
                    mode = detail.mode,
                    id = detail.id,
                    title = detail.title,
                    subtitle = detail.subtitle,
                ),
            )
        },
        onPlayTracks = { tracks, index -> onIntent(TvMainIntent.PlayTracks(tracks, index)) },
        onToggleFavorite = { onIntent(TvMainIntent.ToggleFavorite(it)) },
        modifier = modifier,
    )
}

@Composable
private fun TvFavoritesScreen(
    state: FavoritesState,
    tvState: TvMainState,
    artworkCacheStore: ArtworkCacheStore,
    allowInitialFocus: Boolean,
    onIntent: (TvMainIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    TvMediaBrowserScreen(
        title = "喜欢",
        emptyTitle = "还没有喜欢的歌曲",
        emptyBody = "在曲库或播放器里点亮心形后，喜欢的歌曲会出现在这里。",
        isLoading = state.isLoadingContent,
        query = state.query,
        mode = tvState.favoritesMode,
        detail = tvState.favoritesDetail,
        tracks = state.filteredTracks,
        albums = state.filteredAlbums,
        artists = state.filteredArtists,
        favoriteTrackIds = state.favoriteTrackIds,
        artworkCacheStore = artworkCacheStore,
        allowInitialFocus = allowInitialFocus,
        onModeSelected = { onIntent(TvMainIntent.SelectFavoritesMode(it)) },
        onOpenSearch = { onIntent(TvMainIntent.OpenSearch(TvSearchTarget.Favorites, state.query)) },
        onClearSearch = { onIntent(TvMainIntent.ClearSearch(TvSearchTarget.Favorites)) },
        onOpenDetail = { detail ->
            context.startActivity(
                TvMediaDetailActivity.createIntent(
                    context = context,
                    source = TvMediaDetailSource.Favorites,
                    mode = detail.mode,
                    id = detail.id,
                    title = detail.title,
                    subtitle = detail.subtitle,
                ),
            )
        },
        onPlayTracks = { tracks, index -> onIntent(TvMainIntent.PlayTracks(tracks, index)) },
        onToggleFavorite = { onIntent(TvMainIntent.ToggleFavorite(it)) },
        modifier = modifier,
    )
}

@Composable
private fun TvMediaBrowserScreen(
    title: String,
    emptyTitle: String,
    emptyBody: String,
    isLoading: Boolean,
    query: String,
    mode: TvMediaBrowserMode,
    detail: TvMediaDetail?,
    tracks: List<Track>,
    albums: List<Album>,
    artists: List<Artist>,
    favoriteTrackIds: Set<String>,
    artworkCacheStore: ArtworkCacheStore,
    allowInitialFocus: Boolean,
    onModeSelected: (TvMediaBrowserMode) -> Unit,
    onOpenSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onOpenDetail: (TvMediaDetail) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tracksTabFocusRequester = remember(title) { FocusRequester() }
    val albumsTabFocusRequester = remember(title) { FocusRequester() }
    val artistsTabFocusRequester = remember(title) { FocusRequester() }
    val currentTabFocusRequester = when (mode) {
        TvMediaBrowserMode.Tracks -> tracksTabFocusRequester
        TvMediaBrowserMode.Albums -> albumsTabFocusRequester
        TvMediaBrowserMode.Artists -> artistsTabFocusRequester
    }
    val firstListItemFocusRequester = remember(title, mode, detail) { FocusRequester() }
    val displayedTracks = remember(detail, tracks) {
        when (detail?.mode) {
            TvMediaBrowserMode.Albums -> tracks.filter { track -> matchesAlbumDetail(track, detail.id) }
            TvMediaBrowserMode.Artists -> tracks.filter { track -> matchesArtistDetail(track, detail.id) }
            TvMediaBrowserMode.Tracks,
            null -> tracks
        }
    }
    val albumArtworkTrackById by produceState<Map<String, Track>>(initialValue = emptyMap(), tracks) {
        value = withContext(Dispatchers.Default) {
            buildAlbumArtworkTrackById(tracks)
        }
    }
    var focusedListIndex by remember(title, mode, detail, tracks) { mutableStateOf<Int?>(null) }
    val topControlsCanFocus = focusedListIndex == null || focusedListIndex == 0
    val searchButtonFocusModifier = Modifier.focusProperties {
        canFocus = topControlsCanFocus
        down = currentTabFocusRequester
    }
    val hasListItems = when {
        isLoading -> false
        detail != null -> displayedTracks.isNotEmpty()
        mode == TvMediaBrowserMode.Tracks -> tracks.isNotEmpty()
        mode == TvMediaBrowserMode.Albums -> albums.isNotEmpty()
        mode == TvMediaBrowserMode.Artists -> artists.isNotEmpty()
        else -> false
    }
    LaunchedEffect(title) {
        if (allowInitialFocus) {
            runCatching { currentTabFocusRequester.requestFocus() }
        }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TvSectionHeader(
            title = detail?.title ?: title,
            subtitle = detail?.subtitle ?: browserSubtitle(query, tracks.size),
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (query.isNotBlank()) {
                        OutlinedButton(
                            onClick = onClearSearch,
                            modifier = searchButtonFocusModifier,
                        ) {
                            Text("清除搜索")
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenSearch,
                        modifier = searchButtonFocusModifier,
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("搜索")
                    }
                }
            },
        )
        TvStatsRow(
            trackCount = tracks.size,
            albumCount = albums.size,
            artistCount = artists.size,
            selectedMode = mode,
            tracksFocusRequester = tracksTabFocusRequester,
            albumsFocusRequester = albumsTabFocusRequester,
            artistsFocusRequester = artistsTabFocusRequester,
            downFocusRequester = firstListItemFocusRequester.takeIf { hasListItems },
            canFocus = topControlsCanFocus,
            onModeSelected = onModeSelected,
        )
        when {
            isLoading -> TvLoadingPanel()
            detail != null -> TvTrackList(
                tracks = displayedTracks,
                favoriteTrackIds = favoriteTrackIds,
                artworkCacheStore = artworkCacheStore,
                emptyTitle = "这里没有歌曲",
                emptyBody = "当前专辑或艺人没有可显示的歌曲。",
                onPlayTracks = onPlayTracks,
                onToggleFavorite = onToggleFavorite,
                onItemFocused = { focusedListIndex = it },
                onFocusExit = { focusedListIndex = null },
                firstItemFocusRequester = firstListItemFocusRequester,
                firstItemUpFocusRequester = currentTabFocusRequester,
                modifier = Modifier.fillMaxSize(),
            )

            mode == TvMediaBrowserMode.Tracks -> TvTrackList(
                tracks = tracks,
                favoriteTrackIds = favoriteTrackIds,
                artworkCacheStore = artworkCacheStore,
                emptyTitle = emptyTitle,
                emptyBody = if (query.isBlank()) emptyBody else "试试调整搜索词。",
                onPlayTracks = onPlayTracks,
                onToggleFavorite = onToggleFavorite,
                onItemFocused = { focusedListIndex = it },
                onFocusExit = { focusedListIndex = null },
                firstItemFocusRequester = firstListItemFocusRequester,
                firstItemUpFocusRequester = currentTabFocusRequester,
                modifier = Modifier.fillMaxSize(),
            )

            mode == TvMediaBrowserMode.Albums -> TvAlbumList(
                albums = albums,
                albumArtworkTrackById = albumArtworkTrackById,
                artworkCacheStore = artworkCacheStore,
                onOpenDetail = onOpenDetail,
                onItemFocused = { focusedListIndex = it },
                onFocusExit = { focusedListIndex = null },
                firstItemFocusRequester = firstListItemFocusRequester,
                firstItemUpFocusRequester = currentTabFocusRequester,
                modifier = Modifier.fillMaxSize(),
            )

            mode == TvMediaBrowserMode.Artists -> TvArtistList(
                artists = artists,
                onOpenDetail = onOpenDetail,
                onItemFocused = { focusedListIndex = it },
                onFocusExit = { focusedListIndex = null },
                firstItemFocusRequester = firstListItemFocusRequester,
                firstItemUpFocusRequester = currentTabFocusRequester,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TvSectionHeader(
    title: String,
    subtitle: String?,
    action: (@Composable () -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        action?.invoke()
    }
}

@Composable
private fun TvStatsRow(
    trackCount: Int,
    albumCount: Int,
    artistCount: Int,
    selectedMode: TvMediaBrowserMode,
    tracksFocusRequester: FocusRequester,
    albumsFocusRequester: FocusRequester,
    artistsFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester?,
    canFocus: Boolean,
    onModeSelected: (TvMediaBrowserMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TvStatCard(
            label = "歌曲",
            count = trackCount,
            icon = Icons.Rounded.LibraryMusic,
            selected = selectedMode == TvMediaBrowserMode.Tracks,
            modifier = Modifier.weight(1f),
            focusRequester = tracksFocusRequester,
            downFocusRequester = downFocusRequester,
            canFocus = canFocus,
            onSelected = { onModeSelected(TvMediaBrowserMode.Tracks) },
        )
        TvStatCard(
            label = "专辑",
            count = albumCount,
            icon = Icons.Rounded.Album,
            selected = selectedMode == TvMediaBrowserMode.Albums,
            modifier = Modifier.weight(1f),
            focusRequester = albumsFocusRequester,
            downFocusRequester = downFocusRequester,
            canFocus = canFocus,
            onSelected = { onModeSelected(TvMediaBrowserMode.Albums) },
        )
        TvStatCard(
            label = "艺人",
            count = artistCount,
            icon = Icons.Rounded.Person,
            selected = selectedMode == TvMediaBrowserMode.Artists,
            modifier = Modifier.weight(1f),
            focusRequester = artistsFocusRequester,
            downFocusRequester = downFocusRequester,
            canFocus = canFocus,
            onSelected = { onModeSelected(TvMediaBrowserMode.Artists) },
        )
    }
}

@Composable
private fun TvStatCard(
    label: String,
    count: Int,
    icon: ImageVector,
    selected: Boolean,
    focusRequester: FocusRequester,
    downFocusRequester: FocusRequester?,
    canFocus: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryContentColor = if (selected) Color.White.copy(alpha = 0.86f) else MaterialTheme.colorScheme.onSurfaceVariant
    val iconTint = if (selected) Color.White else MaterialTheme.colorScheme.primary
    val defaultBorder = if (selected) {
        Border.None
    } else {
        Border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = TvCardShape)
    }
    val selectIfNeeded = { if (!selected) onSelected() }
    Card(
        onClick = selectIfNeeded,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusProperties {
                this.canFocus = canFocus
                downFocusRequester?.let { down = it }
            }
            .height(72.dp)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    selectIfNeeded()
                }
            },
        shape = CardDefaults.shape(
            shape = TvCardShape,
            focusedShape = TvCardShape,
            pressedShape = TvCardShape,
        ),
        colors = CardDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = Color.White,
            pressedContainerColor = MaterialTheme.colorScheme.primary,
            pressedContentColor = Color.White,
        ),
        border = CardDefaults.border(
            border = defaultBorder,
            focusedBorder = Border.None,
            pressedBorder = Border.None,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = count.toString(),
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = label,
                    color = secondaryContentColor,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TvTrackList(
    tracks: List<Track>,
    favoriteTrackIds: Set<String>,
    artworkCacheStore: ArtworkCacheStore,
    emptyTitle: String,
    emptyBody: String,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onItemFocused: (Int) -> Unit,
    onFocusExit: () -> Unit,
    firstItemFocusRequester: FocusRequester,
    firstItemUpFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) {
        TvEmptyPanel(title = emptyTitle, body = emptyBody, modifier = modifier)
        return
    }
    LazyColumn(
        modifier = modifier
            .focusGroup()
            .onFocusChanged { focusState ->
                if (!focusState.hasFocus) {
                    onFocusExit()
                }
            },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            TvTrackRow(
                track = track,
                isFavorite = track.id in favoriteTrackIds,
                artworkCacheStore = artworkCacheStore,
                onPlay = { onPlayTracks(tracks, index) },
                onToggleFavorite = { onToggleFavorite(track) },
                onFocused = { onItemFocused(index) },
                focusRequester = firstItemFocusRequester.takeIf { index == 0 },
                upFocusRequester = firstItemUpFocusRequester.takeIf { index == 0 },
            )
        }
    }
}

@Composable
private fun TvTrackRow(
    track: Track,
    isFavorite: Boolean,
    artworkCacheStore: ArtworkCacheStore,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onFocused: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
) {
    Card(
        onClick = onPlay,
        onLongClick = onToggleFavorite,
        scale = CardDefaults.scale(focusedScale = 1.01f, pressedScale = 1.01f),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .then(
                if (focusRequester != null && upFocusRequester != null) {
                    Modifier
                        .focusRequester(focusRequester)
                        .focusProperties { up = upFocusRequester }
                } else {
                    Modifier
                },
            )
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocused()
                }
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TvArtworkImage(
                artworkLocator = track.artworkLocator,
                artworkCacheKey = trackArtworkCacheKey(track),
                artworkCacheStore = artworkCacheStore,
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = track.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(track.artistName, track.albumTitle).joinToString(" / ").ifBlank { "未知艺人" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatTvDuration(track.durationMs),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(64.dp),
            )
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "取消喜欢" else "喜欢",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun TvAlbumList(
    albums: List<Album>,
    albumArtworkTrackById: Map<String, Track>,
    artworkCacheStore: ArtworkCacheStore,
    onOpenDetail: (TvMediaDetail) -> Unit,
    onItemFocused: (Int) -> Unit,
    onFocusExit: () -> Unit,
    firstItemFocusRequester: FocusRequester,
    firstItemUpFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    if (albums.isEmpty()) {
        TvEmptyPanel(title = "没有专辑", body = "当前筛选条件下没有专辑。", modifier = modifier)
        return
    }
    LazyColumn(
        modifier = modifier
            .focusGroup()
            .onFocusChanged { focusState ->
                if (!focusState.hasFocus) {
                    onFocusExit()
                }
            },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(albums, key = { _, album -> album.id }) { index, album ->
            val artworkTrack = albumArtworkTrackById[album.id]
            TvCollectionRow(
                title = album.title,
                subtitle = listOfNotNull(album.artistName, "${album.trackCount} 首").joinToString(" / "),
                icon = Icons.Rounded.Album,
                artworkLocator = artworkTrack?.artworkLocator,
                artworkCacheKey = artworkTrack?.let(::trackArtworkCacheKey),
                artworkCacheStore = artworkTrack?.let { artworkCacheStore },
                onFocused = { onItemFocused(index) },
                focusRequester = firstItemFocusRequester.takeIf { index == 0 },
                upFocusRequester = firstItemUpFocusRequester.takeIf { index == 0 },
                onClick = {
                    onOpenDetail(
                        TvMediaDetail(
                            mode = TvMediaBrowserMode.Albums,
                            id = album.id,
                            title = album.title,
                            subtitle = album.artistName,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun TvArtistList(
    artists: List<Artist>,
    onOpenDetail: (TvMediaDetail) -> Unit,
    onItemFocused: (Int) -> Unit,
    onFocusExit: () -> Unit,
    firstItemFocusRequester: FocusRequester,
    firstItemUpFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    if (artists.isEmpty()) {
        TvEmptyPanel(title = "没有艺人", body = "当前筛选条件下没有艺人。", modifier = modifier)
        return
    }
    LazyColumn(
        modifier = modifier
            .focusGroup()
            .onFocusChanged { focusState ->
                if (!focusState.hasFocus) {
                    onFocusExit()
                }
            },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(artists, key = { _, artist -> artist.id }) { index, artist ->
            TvCollectionRow(
                title = artist.name,
                subtitle = "${artist.trackCount} 首",
                icon = Icons.Rounded.Person,
                artworkLocator = null,
                artworkCacheKey = null,
                artworkCacheStore = null,
                onFocused = { onItemFocused(index) },
                focusRequester = firstItemFocusRequester.takeIf { index == 0 },
                upFocusRequester = firstItemUpFocusRequester.takeIf { index == 0 },
                onClick = {
                    onOpenDetail(
                        TvMediaDetail(
                            mode = TvMediaBrowserMode.Artists,
                            id = artist.id,
                            title = artist.name,
                            subtitle = "${artist.trackCount} 首歌曲",
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun TvCollectionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    artworkLocator: String?,
    artworkCacheKey: String?,
    artworkCacheStore: ArtworkCacheStore?,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.01f, pressedScale = 1.01f),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .then(
                if (focusRequester != null && upFocusRequester != null) {
                    Modifier
                        .focusRequester(focusRequester)
                        .focusProperties { up = upFocusRequester }
                } else {
                    Modifier
                },
            )
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocused()
                }
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (artworkCacheStore != null) {
                TvArtworkImage(
                    artworkLocator = artworkLocator,
                    artworkCacheKey = artworkCacheKey,
                    artworkCacheStore = artworkCacheStore,
                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)),
                )
            } else {
                TvIconBox(icon = icon, modifier = Modifier.size(54.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TvFeaturedTracksPanel(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tracks: List<Track>,
    artworkCacheStore: ArtworkCacheStore,
    onPlayAll: () -> Unit,
    onPlayTrack: (Int) -> Unit,
) {
    val displayedTracks = remember(tracks) { tracks.take(30) }
    val displayedTrackIds = remember(displayedTracks) { displayedTracks.map(Track::id) }
    val playAllFocusRequester = remember { FocusRequester() }
    val trackFocusRequesters = remember(displayedTrackIds) {
        List(displayedTrackIds.size) { FocusRequester() }
    }
    var lastFocusedTrackIndex by remember(displayedTrackIds) { mutableStateOf(0) }
    var playAllFocused by remember { mutableStateOf(false) }
    val playAllDownFocusRequester = trackFocusRequesters.getOrNull(
        lastFocusedTrackIndex.coerceIn(0, trackFocusRequesters.lastIndex.coerceAtLeast(0)),
    )
    val playAllColor = if (playAllFocused) MaterialTheme.colorScheme.primary else Color.White
    val playAllDisabledColor = Color.White.copy(alpha = 0.38f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TvPanelShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(
                onClick = onPlayAll,
                enabled = displayedTracks.isNotEmpty(),
                modifier = Modifier
                    .focusRequester(playAllFocusRequester)
                    .focusProperties {
                        playAllDownFocusRequester?.let { down = it }
                    }
                    .onFocusChanged { focusState ->
                        playAllFocused = focusState.isFocused || focusState.hasFocus
                    },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = playAllColor,
                    disabledContentColor = playAllDisabledColor,
                ),
                border = BorderStroke(1.dp, if (displayedTracks.isNotEmpty()) playAllColor else playAllDisabledColor),
            ) {
                Text("全部播放")
            }
        }
        if (displayedTracks.isEmpty()) {
            Text("曲库有歌曲后会生成推荐。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth().focusGroup(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(displayedTracks, key = { _, track -> track.id }) { index, track ->
                    TvSmallTrackCard(
                        track = track,
                        artworkCacheStore = artworkCacheStore,
                        onClick = { onPlayTrack(index) },
                        focusRequester = trackFocusRequesters.getOrNull(index),
                        upFocusRequester = playAllFocusRequester,
                        onFocused = { lastFocusedTrackIndex = index },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvTrackPreviewPanel(
    title: String,
    tracks: List<Track>,
    artworkCacheStore: ArtworkCacheStore,
    onPlayTrack: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )
        if (tracks.isEmpty()) {
            TvMessagePanel(message = "暂无最近播放")
        } else {
            tracks.take(6).forEachIndexed { index, track ->
                TvTrackRow(
                    track = track,
                    isFavorite = false,
                    artworkCacheStore = artworkCacheStore,
                    onPlay = { onPlayTrack(index) },
                    onToggleFavorite = {},
                )
            }
        }
    }
}

@Composable
private fun TvSmallTrackCard(
    track: Track,
    artworkCacheStore: ArtworkCacheStore,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.01f, pressedScale = 1.01f),
        modifier = Modifier
            .width(152.dp)
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                },
            )
            .focusProperties {
                upFocusRequester?.let { up = it }
            }
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocused()
                }
            },
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvArtworkImage(
                artworkLocator = track.artworkLocator,
                artworkCacheKey = trackArtworkCacheKey(track),
                artworkCacheStore = artworkCacheStore,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)),
            )
            Text(
                track.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artistName.orEmpty().ifBlank { "未知艺人" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvRecentAlbumsPanel(
    albums: List<RecentAlbum>,
    onOpenAlbum: (RecentAlbum) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "最近专辑",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )
        if (albums.isEmpty()) {
            TvMessagePanel(message = "暂无最近播放专辑")
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(albums.take(10), key = { it.album.id }) { recentAlbum ->
                    Card(
                        onClick = { onOpenAlbum(recentAlbum) },
                        scale = CardDefaults.scale(focusedScale = 1.01f, pressedScale = 1.01f),
                        modifier = Modifier.width(180.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TvIconBox(icon = Icons.Rounded.Album, modifier = Modifier.size(54.dp))
                            Text(
                                recentAlbum.album.title,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                recentAlbum.album.artistName.orEmpty().ifBlank { "${recentAlbum.playCount} 次播放" },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSideNowPlayingPanel(
    expanded: Boolean,
    state: PlayerState,
    artworkCacheStore: ArtworkCacheStore,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    if (expanded) {
        TvSideNowPlayingExpanded(
            state = state,
            artworkCacheStore = artworkCacheStore,
            onPlayerIntent = onPlayerIntent,
            onOpenPlayer = onOpenPlayer,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        TvSideNowPlayingCollapsed(
            state = state,
            artworkCacheStore = artworkCacheStore,
            onPlayerIntent = onPlayerIntent,
            onOpenPlayer = onOpenPlayer,
        )
    }
}

@Composable
private fun TvSideNowPlayingCollapsed(
    state: PlayerState,
    artworkCacheStore: ArtworkCacheStore,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val snapshot = state.snapshot
    val currentTrack = snapshot.currentTrack
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            TvArtworkImage(
                artworkLocator = snapshot.currentDisplayArtworkLocator,
                artworkCacheKey = currentTrack?.let(::trackArtworkCacheKey),
                artworkCacheStore = artworkCacheStore,
                modifier = Modifier.fillMaxSize(),
            )
        }
        IconButton(
            onClick = { onPlayerIntent(PlayerIntent.TogglePlayPause) },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = if (snapshot.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (snapshot.isPlaying) "暂停" else "播放",
            )
        }
        IconButton(
            onClick = onOpenPlayer,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Rounded.Fullscreen, contentDescription = "播放界面")
        }
    }
}

@Composable
private fun TvSideNowPlayingExpanded(
    state: PlayerState,
    artworkCacheStore: ArtworkCacheStore,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    val currentTrack = snapshot.currentTrack
    Column(
        modifier = modifier
            .height(194.dp)
            .clip(TvPanelShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvArtworkImage(
                artworkLocator = snapshot.currentDisplayArtworkLocator,
                artworkCacheKey = currentTrack?.let(::trackArtworkCacheKey),
                artworkCacheStore = artworkCacheStore,
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = snapshot.currentDisplayTitle.ifBlank { "还没有播放" },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = snapshot.currentDisplayArtistName.orEmpty().ifBlank { "选择歌曲后开始播放" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        LinearProgressIndicator(
            progress = { playbackProgress(snapshot.positionMs, snapshot.durationMs) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = "${formatTvDuration(snapshot.positionMs)} / ${formatTvDuration(snapshot.durationMs)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipPrevious) }) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首")
            }
            IconButton(onClick = { onPlayerIntent(PlayerIntent.TogglePlayPause) }) {
                Icon(
                    imageVector = if (snapshot.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (snapshot.isPlaying) "暂停" else "播放",
                )
            }
            IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipNext) }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "下一首")
            }
            IconButton(onClick = onOpenPlayer) {
                Icon(Icons.Rounded.Fullscreen, contentDescription = "播放界面")
            }
        }
    }
}

@Composable
private fun TvArtworkImage(
    artworkLocator: String?,
    artworkCacheKey: String?,
    artworkCacheStore: ArtworkCacheStore,
    modifier: Modifier = Modifier,
) {
    val model by produceState<String?>(initialValue = null, artworkLocator, artworkCacheKey, artworkCacheStore) {
        val normalized = normalizedArtworkCacheLocator(artworkLocator)
        value = when {
            normalized == null -> null
            artworkCacheKey != null -> artworkCacheStore.cache(normalized, artworkCacheKey)
                ?: resolveArtworkCacheTarget(normalized)
            else -> resolveArtworkCacheTarget(normalized)
        }
    }
    val painter = rememberAsyncImagePainter(model = model)
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        } else {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TvIconBox(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun TvSearchDialog(
    state: TvSearchDialogState,
    onTextChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (state.target == TvSearchTarget.Library) "搜索曲库" else "搜索喜欢")
        },
        text = {
            OutlinedTextField(
                value = state.text,
                onValueChange = onTextChanged,
                singleLine = true,
                label = { Text("歌曲 / 艺人 / 专辑") },
            )
        },
        confirmButton = {
            Button(onClick = onSubmit) {
                Text("搜索")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClear) {
                    Text("清空")
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        },
    )
}

@Composable
private fun TvLoadingPanel() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun TvEmptyPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TvPanelShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TvMessagePanel(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(TvPanelShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun browserSubtitle(query: String, trackCount: Int): String {
    return if (query.isBlank()) {
        "$trackCount 首歌曲"
    } else {
        "搜索 \"$query\" · $trackCount 首歌曲"
    }
}

private fun matchesAlbumDetail(track: Track, albumId: String): Boolean {
    val directId = track.albumId?.takeIf { it.isNotBlank() }?.let { "album:${track.sourceId}:$it" }
    val visibleId = track.albumTitle
        ?.takeIf { it.isNotBlank() }
        ?.let { libraryAlbumId(track.artistName, it) }
    return directId == albumId || visibleId == albumId
}

private fun buildAlbumArtworkTrackById(tracks: List<Track>): Map<String, Track> {
    val result = LinkedHashMap<String, Track>()
    tracks.forEach { track ->
        val albumTitle = track.albumTitle?.takeIf { it.isNotBlank() } ?: return@forEach
        val albumId = libraryAlbumId(track.artistName, albumTitle)
        if (!result.containsKey(albumId)) {
            result[albumId] = track
        }
    }
    return result
}

private fun matchesArtistDetail(track: Track, artistId: String): Boolean {
    val artistName = track.artistName?.takeIf { it.isNotBlank() } ?: return false
    return libraryArtistId(artistName) == artistId
}

private fun formatTvDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun playbackProgress(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}
