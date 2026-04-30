package top.iwesley.lyn.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.AppTab
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.CLASSIC_APP_THEME_TOKENS
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.feature.favorites.FavoritesIntent
import top.iwesley.lyn.music.feature.favorites.FavoritesState
import top.iwesley.lyn.music.feature.importing.ImportIntent
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.library.LibraryIntent
import top.iwesley.lyn.music.feature.library.LibraryState
import top.iwesley.lyn.music.feature.my.MyIntent
import top.iwesley.lyn.music.feature.my.MyState
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.feature.playlists.PlaylistsIntent
import top.iwesley.lyn.music.feature.playlists.PlaylistsState
import top.iwesley.lyn.music.feature.settings.SettingsIntent
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.feature.tags.MusicTagsEffect
import top.iwesley.lyn.music.feature.tags.MusicTagsIntent
import top.iwesley.lyn.music.feature.tags.MusicTagsState
import top.iwesley.lyn.music.ui.LynMusicTheme
import top.iwesley.lyn.music.ui.mainShellColors

internal val mobilePrimaryNavigationTabs: List<AppTab> = listOf(AppTab.My, AppTab.Library)
internal val mobileLibraryHubTabs: List<AppTab> = listOf(AppTab.Library, AppTab.Favorites, AppTab.Playlists)

internal fun isMobileLibraryHubTab(tab: AppTab): Boolean = tab in mobileLibraryHubTabs

internal fun isMobilePrimaryNavigationSelected(
    selectedTab: AppTab,
    navTab: AppTab,
): Boolean {
    return if (navTab == AppTab.Library) {
        isMobileLibraryHubTab(selectedTab)
    } else {
        selectedTab == navTab
    }
}

internal fun mobileLibraryHubPageForTab(tab: AppTab): Int {
    return mobileLibraryHubTabs.indexOf(tab).takeIf { it >= 0 } ?: 0
}

internal fun mobileLibraryHubTabForPage(page: Int): AppTab {
    return mobileLibraryHubTabs.getOrElse(page) { AppTab.Library }
}

internal fun mobileLibraryHubTabLabel(tab: AppTab): String {
    return when (tab) {
        AppTab.Library -> "曲库"
        AppTab.Favorites -> "喜欢"
        AppTab.Playlists -> "歌单"
        AppTab.My -> "我的"
        AppTab.Tags -> "音乐标签"
        AppTab.Sources -> "来源"
        AppTab.Settings -> "设置"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileShell(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    platform: PlatformDescriptor,
    myState: MyState,
    libraryState: LibraryState,
    playlistsState: PlaylistsState,
    favoritesState: FavoritesState,
    musicTagsState: MusicTagsState,
    musicTagsEffects: Flow<MusicTagsEffect>,
    importState: ImportState,
    playerState: PlayerState,
    settingsState: SettingsState,
    onMyIntent: (MyIntent) -> Unit,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onPlaylistsIntent: (PlaylistsIntent) -> Unit,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onMusicTagsIntent: (MusicTagsIntent) -> Unit,
    onImportIntent: (ImportIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onSettingsIntent: (SettingsIntent) -> Unit,
    libraryNavigationTarget: LibraryNavigationTarget? = null,
    onLibraryNavigationHandled: () -> Unit = {},
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    mobilePortraitMiniPlayer: Boolean,
    hideMiniPlayerBar: Boolean,
    onMobileEditorVisibilityChanged: (Boolean) -> Unit,
    onOpenAddToPlaylist: () -> Unit,
) {
    val shellColors = mainShellColors
    val mobileNavIconSize = 29.dp
    val moreTabs = remember { listOf(AppTab.Tags, AppTab.Sources, AppTab.Settings) }
    val isMoreSelected = selectedTab in moreTabs
    var isMoreSheetVisible by rememberSaveable { mutableStateOf(false) }
    val moreSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier.navigationBarsPadding(),
            ) {
                LynMusicTheme(
                    themeTokens = CLASSIC_APP_THEME_TOKENS,
                    textPalette = AppThemeTextPalette.White,
                ) {
                    MiniPlayerBarVisibility(
                        visible = (playerState.snapshot.currentTrack != null || playerState.snapshot.isHydratingPlayback) &&
                                !playerState.isExpanded &&
                                !hideMiniPlayerBar,
                        state = playerState,
                        onPlayerIntent = onPlayerIntent,
                        isFavorite = playerState.snapshot.currentTrack?.id in favoritesState.favoriteTrackIds,
                        onToggleFavorite = {
                            playerState.snapshot.currentTrack?.let { track ->
                                onFavoritesIntent(FavoritesIntent.ToggleFavorite(track))
                            }
                        },
                        onOpenAddToPlaylist = onOpenAddToPlaylist,
                        onOpenQueue = { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(true)) },
                        mobile = true,
                        mobilePortraitMiniPlayer = mobilePortraitMiniPlayer,
                    )
                }
                NavigationBar(containerColor = shellColors.navContainer) {
                    mobilePrimaryNavigationTabs.forEach { tab ->
                        val label = mobileLibraryHubTabLabel(tab)
                        NavigationBarItem(
                            selected = isMobilePrimaryNavigationSelected(selectedTab, tab),
                            onClick = {
                                isMoreSheetVisible = false
                                onTabSelected(tab)
                            },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        AppTab.My -> Icons.Rounded.Person
                                        else -> Icons.Rounded.LibraryMusic
                                    },
                                    contentDescription = label,
                                    modifier = Modifier.size(mobileNavIconSize),
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                    NavigationBarItem(
                        selected = isMoreSelected,
                        onClick = { isMoreSheetVisible = true },
                        icon = {
                            Icon(
                                Icons.Rounded.MoreHoriz,
                                contentDescription = "更多",
                                modifier = Modifier.size(mobileNavIconSize),
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabContent(
                selectedTab = selectedTab,
                platform = platform,
                myState = myState,
                libraryState = libraryState,
                playlistsState = playlistsState,
                favoritesState = favoritesState,
                musicTagsState = musicTagsState,
                musicTagsEffects = musicTagsEffects,
                importState = importState,
                settingsState = settingsState,
                onMyIntent = onMyIntent,
                onLibraryIntent = onLibraryIntent,
                onPlaylistsIntent = onPlaylistsIntent,
                onFavoritesIntent = onFavoritesIntent,
                onMusicTagsIntent = onMusicTagsIntent,
                onImportIntent = onImportIntent,
                onPlayerIntent = onPlayerIntent,
                onSettingsIntent = onSettingsIntent,
                libraryNavigationTarget = libraryNavigationTarget,
                onLibraryNavigationHandled = onLibraryNavigationHandled,
                onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
                onMobileEditorVisibilityChanged = onMobileEditorVisibilityChanged,
                mobileLibraryHub = true,
                onTabSelected = onTabSelected,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (isMoreSheetVisible) {
        MobileMoreSheet(
            selectedTab = selectedTab,
            sheetState = moreSheetState,
            onDismiss = { isMoreSheetVisible = false },
            onSelect = { tab ->
                isMoreSheetVisible = false
                onTabSelected(tab)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileMoreSheet(
    selectedTab: AppTab,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    onSelect: (AppTab) -> Unit,
) {
    val shellColors = mainShellColors
    val items = remember {
        listOf(
            Triple(AppTab.Tags, Icons.Rounded.Tune, "音乐标签"),
            Triple(AppTab.Sources, Icons.Rounded.FolderOpen, "来源"),
            Triple(AppTab.Settings, Icons.Rounded.Settings, "设置"),
        )
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = shellColors.navContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "更多",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            items.forEach { (tab, icon, label) ->
                val selected = selectedTab == tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.34f),
                        )
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = label,
                        color = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun DesktopShell(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    platform: PlatformDescriptor,
    myState: MyState,
    libraryState: LibraryState,
    playlistsState: PlaylistsState,
    favoritesState: FavoritesState,
    musicTagsState: MusicTagsState,
    musicTagsEffects: Flow<MusicTagsEffect>,
    importState: ImportState,
    playerState: PlayerState,
    settingsState: SettingsState,
    onMyIntent: (MyIntent) -> Unit,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onPlaylistsIntent: (PlaylistsIntent) -> Unit,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onMusicTagsIntent: (MusicTagsIntent) -> Unit,
    onImportIntent: (ImportIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onSettingsIntent: (SettingsIntent) -> Unit,
    libraryNavigationTarget: LibraryNavigationTarget? = null,
    onLibraryNavigationHandled: () -> Unit = {},
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    onOpenAddToPlaylist: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
                .background(Color.White),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                HeroHeader()
                Spacer(Modifier.height(40.dp))
                DesktopNav(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color(0xFFE3E3E3)),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            TabContent(
                selectedTab = selectedTab,
                platform = platform,
                myState = myState,
                libraryState = libraryState,
                playlistsState = playlistsState,
                favoritesState = favoritesState,
                musicTagsState = musicTagsState,
                musicTagsEffects = musicTagsEffects,
                importState = importState,
                settingsState = settingsState,
                onMyIntent = onMyIntent,
                onLibraryIntent = onLibraryIntent,
                onPlaylistsIntent = onPlaylistsIntent,
                onFavoritesIntent = onFavoritesIntent,
                onMusicTagsIntent = onMusicTagsIntent,
                onImportIntent = onImportIntent,
                onPlayerIntent = onPlayerIntent,
                onSettingsIntent = onSettingsIntent,
                libraryNavigationTarget = libraryNavigationTarget,
                onLibraryNavigationHandled = onLibraryNavigationHandled,
                onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
                mobileLibraryHub = false,
                onTabSelected = onTabSelected,
                modifier = Modifier.weight(1f),
            )
            LynMusicTheme(
                themeTokens = CLASSIC_APP_THEME_TOKENS,
                textPalette = AppThemeTextPalette.White,
            ) {
                MiniPlayerBarVisibility(
                    visible = (playerState.snapshot.currentTrack != null || playerState.snapshot.isHydratingPlayback) &&
                            !playerState.isExpanded,
                    state = playerState,
                    onPlayerIntent = onPlayerIntent,
                    isFavorite = playerState.snapshot.currentTrack?.id in favoritesState.favoriteTrackIds,
                    onToggleFavorite = {
                        playerState.snapshot.currentTrack?.let { track ->
                            onFavoritesIntent(FavoritesIntent.ToggleFavorite(track))
                        }
                    },
                    onOpenAddToPlaylist = onOpenAddToPlaylist,
                    onOpenQueue = { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(true)) },
                    compact = false,
                )
            }
        }
    }
}

@Composable
private fun DesktopNav(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val selectedBackground = primary.copy(alpha = 0.09f)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            Triple(AppTab.My, Icons.Rounded.Person, "我的"),
            Triple(AppTab.Library, Icons.Rounded.LibraryMusic, "曲库"),
            Triple(AppTab.Playlists, Icons.AutoMirrored.Rounded.List, "歌单"),
            Triple(AppTab.Favorites, Icons.Rounded.FavoriteBorder, "喜欢"),
            Triple(AppTab.Tags, Icons.Rounded.LocalOffer, "音乐标签"),
            Triple(AppTab.Sources, Icons.Rounded.FolderOpen, "来源"),
            Triple(AppTab.Settings, Icons.Rounded.Settings, "设置"),
        ).forEach { (tab, icon, label) ->
            val selected = selectedTab == tab
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) selectedBackground else Color.Transparent)
                    .clickable { onTabSelected(tab) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .background(if (selected) primary else Color.Transparent),
                )
                Spacer(Modifier.width(17.dp))
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (selected) primary else Color(0xFF111111),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(18.dp))
                Text(
                    label,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = Color(0xFF111111),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun HeroHeader() {
    val desktopWindowChrome = currentDesktopWindowChrome
    val topPadding =
        if (desktopWindowChrome.immersiveTitleBarEnabled) desktopWindowChrome.topInset + 48.dp
        else 56.dp
    Text(
        text = "LynMusic",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.ExtraBold,
        color = Color.Black,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 42.dp, top = topPadding),
    )
}

@Composable
private fun TabContent(
    selectedTab: AppTab,
    platform: PlatformDescriptor,
    myState: MyState,
    libraryState: LibraryState,
    playlistsState: PlaylistsState,
    favoritesState: FavoritesState,
    musicTagsState: MusicTagsState,
    musicTagsEffects: Flow<MusicTagsEffect>,
    importState: ImportState,
    settingsState: SettingsState,
    onMyIntent: (MyIntent) -> Unit,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onPlaylistsIntent: (PlaylistsIntent) -> Unit,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onMusicTagsIntent: (MusicTagsIntent) -> Unit,
    onImportIntent: (ImportIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onSettingsIntent: (SettingsIntent) -> Unit,
    libraryNavigationTarget: LibraryNavigationTarget? = null,
    onLibraryNavigationHandled: () -> Unit = {},
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    onMobileEditorVisibilityChanged: (Boolean) -> Unit = {},
    mobileLibraryHub: Boolean,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (mobileLibraryHub && isMobileLibraryHubTab(selectedTab)) {
        MobileLibraryHubTab(
            selectedTab = selectedTab,
            libraryState = libraryState,
            playlistsState = playlistsState,
            favoritesState = favoritesState,
            onLibraryIntent = onLibraryIntent,
            onPlaylistsIntent = onPlaylistsIntent,
            onFavoritesIntent = onFavoritesIntent,
            onPlayerIntent = onPlayerIntent,
            libraryNavigationTarget = libraryNavigationTarget,
            onLibraryNavigationHandled = onLibraryNavigationHandled,
            onTabSelected = onTabSelected,
            modifier = modifier,
        )
        return
    }

    when (selectedTab) {
        AppTab.My -> MyTab(
            platform = platform,
            state = myState,
            onMyIntent = onMyIntent,
            onPlayerIntent = onPlayerIntent,
            onOpenAlbum = { albumId ->
                onOpenLibraryNavigationTarget(LibraryNavigationTarget.Album(albumId))
            },
            modifier = modifier,
        )

        AppTab.Library -> LibraryTab(
            state = libraryState,
            favoritesState = favoritesState,
            onLibraryIntent = onLibraryIntent,
            onFavoritesIntent = onFavoritesIntent,
            onPlayerIntent = onPlayerIntent,
            showDuration = !platform.isMobilePlatform(),
            navigationTarget = libraryNavigationTarget,
            onNavigationHandled = onLibraryNavigationHandled,
            modifier = modifier,
        )

        AppTab.Playlists -> PlaylistsTab(
            state = playlistsState,
            onPlaylistsIntent = onPlaylistsIntent,
            onPlayerIntent = onPlayerIntent,
            modifier = modifier,
        )

        AppTab.Favorites -> FavoritesTab(
            state = favoritesState,
            onFavoritesIntent = onFavoritesIntent,
            onPlayerIntent = onPlayerIntent,
            showDuration = !platform.isMobilePlatform(),
            modifier = modifier,
        )

        AppTab.Tags -> MusicTagsTab(
            platform = platform,
            state = musicTagsState,
            effects = musicTagsEffects,
            onMusicTagsIntent = onMusicTagsIntent,
            onPlayerIntent = onPlayerIntent,
            onMobileEditorVisibilityChanged = onMobileEditorVisibilityChanged,
            modifier = modifier,
        )

        AppTab.Sources -> SourcesTab(
            platform = platform,
            state = importState,
            onImportIntent = onImportIntent,
            modifier = modifier,
        )

        AppTab.Settings -> SettingsTab(
            platform = platform,
            state = settingsState,
            onSettingsIntent = onSettingsIntent,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MobileLibraryHubTab(
    selectedTab: AppTab,
    libraryState: LibraryState,
    playlistsState: PlaylistsState,
    favoritesState: FavoritesState,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onPlaylistsIntent: (PlaylistsIntent) -> Unit,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    libraryNavigationTarget: LibraryNavigationTarget?,
    onLibraryNavigationHandled: () -> Unit,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialPage = mobileLibraryHubPageForTab(selectedTab)
    val pagerState = rememberPagerState(initialPage = initialPage) { mobileLibraryHubTabs.size }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(selectedTab) {
        val targetPage = mobileLibraryHubPageForTab(selectedTab)
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                onTabSelected(mobileLibraryHubTabForPage(page))
            }
    }
    Column(modifier = modifier.fillMaxSize()) {
        SecondaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            mobileLibraryHubTabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        onTabSelected(tab)
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = {
                        Text(
                            text = mobileLibraryHubTabLabel(tab),
                            fontWeight = if (pagerState.currentPage == index) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Medium
                            },
                        )
                    },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            key = { page -> mobileLibraryHubTabForPage(page).name },
        ) { page ->
            when (mobileLibraryHubTabForPage(page)) {
                AppTab.Library -> LibraryTab(
                    state = libraryState,
                    favoritesState = favoritesState,
                    onLibraryIntent = onLibraryIntent,
                    onFavoritesIntent = onFavoritesIntent,
                    onPlayerIntent = onPlayerIntent,
                    showDuration = false,
                    navigationTarget = libraryNavigationTarget,
                    onNavigationHandled = onLibraryNavigationHandled,
                    modifier = Modifier.fillMaxSize(),
                )

                AppTab.Favorites -> FavoritesTab(
                    state = favoritesState,
                    onFavoritesIntent = onFavoritesIntent,
                    onPlayerIntent = onPlayerIntent,
                    showDuration = false,
                    modifier = Modifier.fillMaxSize(),
                )

                AppTab.Playlists -> PlaylistsTab(
                    state = playlistsState,
                    onPlaylistsIntent = onPlaylistsIntent,
                    onPlayerIntent = onPlayerIntent,
                    modifier = Modifier.fillMaxSize(),
                )

                else -> Unit
            }
        }
    }
}
