package top.iwesley.lyn.music

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.AppTab
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.CLASSIC_APP_THEME_TOKENS
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaylistSummary
import top.iwesley.lyn.music.feature.favorites.FavoritesIntent
import top.iwesley.lyn.music.feature.favorites.FavoritesState
import top.iwesley.lyn.music.feature.importing.ImportIntent
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.library.LibraryIntent
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.LibraryState
import top.iwesley.lyn.music.feature.library.TrackSortMode
import top.iwesley.lyn.music.feature.library.matchesLibrarySourceFilter
import top.iwesley.lyn.music.feature.my.MyIntent
import top.iwesley.lyn.music.feature.my.MyState
import top.iwesley.lyn.music.feature.online.OnlineFavoritesIntent
import top.iwesley.lyn.music.feature.online.OnlineFavoritesState
import top.iwesley.lyn.music.feature.online.OnlineLibraryIntent
import top.iwesley.lyn.music.feature.online.OnlineLibraryState
import top.iwesley.lyn.music.feature.online.OnlinePlaylistsIntent
import top.iwesley.lyn.music.feature.online.OnlinePlaylistsState
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

internal val mobilePrimaryNavigationTabs: List<AppTab> = listOf(AppTab.Library, AppTab.My)
internal val mobileLibraryHubTabs: List<AppTab> = listOf(AppTab.Library, AppTab.Favorites, AppTab.Playlists)

internal val phonePrimaryNavigationTabs = listOf(AppTab.Library, AppTab.Favorites, AppTab.My)
internal val phoneCollectionTabs = listOf(AppTab.Favorites, AppTab.Playlists)

internal fun primaryNavigationTabs(phoneNavigation: Boolean): List<AppTab> =
    if (phoneNavigation) phonePrimaryNavigationTabs else mobilePrimaryNavigationTabs

internal fun libraryHubTabs(selectedTab: AppTab, phoneNavigation: Boolean): List<AppTab> = when {
    !phoneNavigation -> mobileLibraryHubTabs
    selectedTab == AppTab.Library -> listOf(AppTab.Library)
    else -> phoneCollectionTabs
}

internal fun phoneNavigationGroup(tab: AppTab): AppTab =
    if (tab in phoneCollectionTabs) AppTab.Favorites else tab

internal fun phoneLibraryRootLabel(root: LibraryBrowserRootView): String = when (root) {
    LibraryBrowserRootView.Tracks -> "歌曲"
    LibraryBrowserRootView.Albums -> "专辑"
    LibraryBrowserRootView.Artists -> "艺人"
    LibraryBrowserRootView.Folders -> "文件夹"
}

internal fun supportsMusicTagsEntry(platform: PlatformDescriptor): Boolean {
    return !platform.isAndroidAutomotivePlatform()
}

internal fun isAppTabAvailableForPlatform(tab: AppTab, platform: PlatformDescriptor): Boolean {
    return tab != AppTab.Tags || supportsMusicTagsEntry(platform)
}

internal fun resolveAppTabForPlatform(tab: AppTab, platform: PlatformDescriptor): AppTab {
    return if (isAppTabAvailableForPlatform(tab, platform)) tab else AppTab.Library
}

internal fun mobileMoreNavigationTabs(platform: PlatformDescriptor): List<AppTab> {
    return if (supportsMusicTagsEntry(platform)) {
        listOf(AppTab.Tags, AppTab.Sources, AppTab.Settings)
    } else {
        listOf(AppTab.Sources, AppTab.Settings)
    }
}

internal fun desktopNavigationTabs(platform: PlatformDescriptor): List<AppTab> {
    val primaryTabs = listOf(AppTab.My, AppTab.Library, AppTab.Favorites, AppTab.Playlists)
    val secondaryTabs = if (supportsMusicTagsEntry(platform)) {
        listOf(AppTab.Tags, AppTab.Sources, AppTab.Settings)
    } else {
        listOf(AppTab.Sources, AppTab.Settings)
    }
    return primaryTabs + secondaryTabs
}

internal fun isMobileLibraryHubTab(tab: AppTab): Boolean = tab in mobileLibraryHubTabs

internal fun mobileLibraryHubSupportsTrackSort(tab: AppTab): Boolean {
    return tab == AppTab.Library || tab == AppTab.Favorites
}

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

internal fun mobileLibraryHubSearchPlaceholder(tab: AppTab): String {
    return when (tab) {
        AppTab.Library -> "搜索歌曲 / 艺人 / 专辑"
        AppTab.Favorites -> "搜索喜欢的歌曲 / 艺人 / 专辑"
        AppTab.Playlists -> "搜索歌单"
        else -> "搜索"
    }
}

internal fun mobileLibraryHubShowsSourceMenu(tab: AppTab): Boolean {
    return tab == AppTab.Library || tab == AppTab.Favorites || tab == AppTab.Playlists
}

internal fun mobileLibraryHubUsesPullToRefresh(tab: AppTab): Boolean {
    return tab == AppTab.Favorites || tab == AppTab.Playlists
}

internal fun mobileLibraryHubCanBatchOperate(
    tab: AppTab,
    libraryVisibleTrackCount: Int,
    favoritesVisibleTrackCount: Int,
    playlistDetailVisibleTrackCount: Int,
    selectedPlaylistId: String?,
): Boolean {
    return when (tab) {
        AppTab.Library -> libraryVisibleTrackCount > 0
        AppTab.Favorites -> favoritesVisibleTrackCount > 0
        AppTab.Playlists -> selectedPlaylistId != null && playlistDetailVisibleTrackCount > 0
        else -> false
    }
}

internal fun mobileLibraryHubRefreshIndicatorVisible(
    isRefreshing: Boolean,
    isMinimumHoldActive: Boolean,
): Boolean {
    return isRefreshing || isMinimumHoldActive
}

private fun mobileLibraryHubSourceFilterButtonLabel(filter: LibrarySourceFilter): String {
    return when (filter) {
        LibrarySourceFilter.ALL -> "全部来源"
        LibrarySourceFilter.LOCAL_FOLDER -> "本地文件夹"
        LibrarySourceFilter.SAMBA -> "Samba"
        LibrarySourceFilter.WEBDAV -> "WebDAV"
        LibrarySourceFilter.NAVIDROME -> "Navidrome"
        LibrarySourceFilter.SUBSONIC -> "Subsonic"
        LibrarySourceFilter.EMBY -> "Emby"
        LibrarySourceFilter.DOWNLOADED -> "已下载"
    }
}

private fun mobileLibraryHubSourceFilterMenuLabel(filter: LibrarySourceFilter): String {
    return when (filter) {
        LibrarySourceFilter.ALL -> "全部"
        else -> mobileLibraryHubSourceFilterButtonLabel(filter)
    }
}

internal fun filterMobileLibraryHubPlaylists(
    playlists: List<PlaylistSummary>,
    query: String,
): List<PlaylistSummary> {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isBlank()) return playlists
    return playlists.filter { playlist ->
        playlist.name.lowercase().contains(normalizedQuery)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileShell(
    phoneNavigation: Boolean,
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    platform: PlatformDescriptor,
    myState: MyState,
    libraryState: LibraryState,
    onlineLibraryState: OnlineLibraryState,
    playlistsState: PlaylistsState,
    onlinePlaylistsState: OnlinePlaylistsState,
    favoritesState: FavoritesState,
    onlineFavoritesState: OnlineFavoritesState,
    musicTagsState: MusicTagsState,
    musicTagsEffects: Flow<MusicTagsEffect>,
    importState: ImportState,
    playerState: PlayerState,
    settingsState: SettingsState,
    onMyIntent: (MyIntent) -> Unit,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onOnlineLibraryIntent: (OnlineLibraryIntent) -> Unit,
    onPlaylistsIntent: (PlaylistsIntent) -> Unit,
    onOnlinePlaylistsIntent: (OnlinePlaylistsIntent) -> Unit,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onOnlineFavoritesIntent: (OnlineFavoritesIntent) -> Unit,
    onMusicTagsIntent: (MusicTagsIntent) -> Unit,
    onImportIntent: (ImportIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onSettingsIntent: (SettingsIntent) -> Unit,
    onOpenBackgroundRunSettings: () -> Unit,
    libraryNavigationTarget: LibraryNavigationTarget? = null,
    onLibraryNavigationHandled: () -> Unit = {},
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    mobilePortraitMiniPlayer: Boolean,
    hideMiniPlayerBar: Boolean,
    onMobileEditorVisibilityChanged: (Boolean) -> Unit,
    onOpenAddToPlaylist: () -> Unit,
) {
    val navigationTabs = primaryNavigationTabs(phoneNavigation)
    var lastCollectionTab by rememberSaveable { mutableStateOf(AppTab.Favorites) }
    val contentStateHolder = rememberSaveableStateHolder()
    val dockBackdrop = if (phoneNavigation) rememberLayerBackdrop() else null
    val layoutDirection = LocalLayoutDirection.current
    val contentBehindDock = phoneNavigation &&
        (isMobileLibraryHubTab(selectedTab) || selectedTab == AppTab.My)
    LaunchedEffect(selectedTab) {
        if (selectedTab in phoneCollectionTabs) lastCollectionTab = selectedTab
    }
    val shellColors = mainShellColors
    val mobileNavIconSize = 29.dp
    val moreTabs = remember(platform) { mobileMoreNavigationTabs(platform) }
    val isMoreSelected = selectedTab in moreTabs
    val showSettingsUpdateBadge = settingsState.appUpdateHasNewVersion == true
    val effectivePlayerSnapshot = playerState.effectiveSnapshot
    val keyboardController = LocalSoftwareKeyboardController.current
    var isMoreSheetVisible by rememberSaveable { mutableStateOf(false) }
    val moreSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val playerFavoriteBinding = playerFavoriteBinding(
        track = effectivePlayerSnapshot.currentTrack,
        localFavoriteTrackIds = favoritesState.favoriteTrackIds,
        onlineFavoritesState = onlineFavoritesState,
        importState = importState,
    )

    fun selectMobileTab(tab: AppTab) {
        if (phoneNavigation && tab in phoneCollectionTabs) lastCollectionTab = tab
        if (isMobileLibraryHubTab(selectedTab) &&
            (!isMobileLibraryHubTab(tab) ||
                (phoneNavigation && phoneNavigationGroup(selectedTab) != phoneNavigationGroup(tab)))
        ) {
            keyboardController?.hide()
        }
        onTabSelected(tab)
    }

    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier.navigationBarsPadding(),
            ) {
                OfflineBatchDownloadStatusBar()
                LynMusicTheme(
                    themeTokens = CLASSIC_APP_THEME_TOKENS,
                    textPalette = AppThemeTextPalette.White,
                ) {
                    MiniPlayerBarVisibility(
                        visible = (effectivePlayerSnapshot.currentTrack != null || playerState.snapshot.isHydratingPlayback) &&
                                !playerState.isExpanded &&
                                !hideMiniPlayerBar,
                        state = playerState,
                        onPlayerIntent = onPlayerIntent,
                        isFavorite = playerFavoriteBinding.isFavorite,
                        canToggleFavorite = playerFavoriteBinding.canToggleFavorite,
                        onToggleFavorite = {
                            if (playerFavoriteBinding.canToggleFavorite) {
                                effectivePlayerSnapshot.currentTrack?.let { track ->
                                    val onlineSourceId = playerFavoriteBinding.onlineSourceId
                                    if (onlineSourceId != null) {
                                        onOnlineFavoritesIntent(
                                            OnlineFavoritesIntent.SetFavorite(
                                                sourceId = onlineSourceId,
                                                track = track,
                                                favorite = !playerFavoriteBinding.isFavorite,
                                            )
                                        )
                                    } else {
                                        onFavoritesIntent(FavoritesIntent.ToggleFavorite(track))
                                    }
                                }
                            }
                        },
                        onOpenAddToPlaylist = onOpenAddToPlaylist,
                        onOpenQueue = { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(true)) },
                        mobile = true,
                        mobilePortraitMiniPlayer = mobilePortraitMiniPlayer,
                    )
                }
                if (dockBackdrop != null) {
                    // MobileMiniPlayerBar already contributes 10dp of bottom padding.
                    if (effectivePlayerSnapshot.currentTrack != null && !playerState.isExpanded && !hideMiniPlayerBar) {
                        Spacer(Modifier.height(2.dp))
                    }
                    Box(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LiquidGlassDock(
                            selectedIndex = when {
                                isMoreSheetVisible || isMoreSelected -> 3
                                else -> navigationTabs.indexOf(phoneNavigationGroup(selectedTab)).coerceAtLeast(0)
                            },
                            backdrop = dockBackdrop,
                            showUpdateBadge = showSettingsUpdateBadge,
                            enabled = !isMoreSheetVisible && !playerState.isExpanded,
                            onSelect = { index ->
                                if (index == 3) {
                                    isMoreSheetVisible = true
                                } else {
                                    isMoreSheetVisible = false
                                    val tab = navigationTabs[index]
                                    selectMobileTab(if (tab == AppTab.Favorites) lastCollectionTab else tab)
                                }
                            },
                            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                        )
                    }
                } else NavigationBar(containerColor = shellColors.navContainer) {
                    navigationTabs.forEach { tab ->
                        val label = if (phoneNavigation && tab == AppTab.Favorites) "收藏" else mobileLibraryHubTabLabel(tab)
                        NavigationBarItem(
                            selected = if (phoneNavigation) phoneNavigationGroup(selectedTab) == tab
                            else isMobilePrimaryNavigationSelected(selectedTab, tab),
                            onClick = {
                                isMoreSheetVisible = false
                                selectMobileTab(if (phoneNavigation && tab == AppTab.Favorites) lastCollectionTab else tab)
                            },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        AppTab.My -> Icons.Rounded.Person
                                        AppTab.Favorites -> Icons.Rounded.FavoriteBorder
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
                            BadgedIcon(
                                imageVector = Icons.Rounded.MoreHoriz,
                                contentDescription = "更多",
                                showBadge = showSettingsUpdateBadge,
                                modifier = Modifier.size(mobileNavIconSize),
                                iconModifier = Modifier.size(mobileNavIconSize),
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
        val contentPadding = if (contentBehindDock) PaddingValues(
            start = padding.calculateStartPadding(layoutDirection),
            top = padding.calculateTopPadding(),
            end = padding.calculateEndPadding(layoutDirection),
        ) else padding
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (dockBackdrop != null) {
                    Modifier.layerBackdrop(dockBackdrop).background(MaterialTheme.colorScheme.background)
                } else Modifier)
                .padding(contentPadding),
        ) {
            CompositionLocalProvider(
                LocalDockBottomInset provides if (contentBehindDock) padding.calculateBottomPadding() else 0.dp,
            ) {
                contentStateHolder.SaveableStateProvider(
                    key = if (phoneNavigation) phoneNavigationGroup(selectedTab).name else "legacy",
                ) {
                    TabContent(
                        selectedTab = selectedTab,
                        platform = platform,
                        myState = myState,
                        libraryState = libraryState,
                        onlineLibraryState = onlineLibraryState,
                        playlistsState = playlistsState,
                        onlinePlaylistsState = onlinePlaylistsState,
                        favoritesState = favoritesState,
                        onlineFavoritesState = onlineFavoritesState,
                        musicTagsState = musicTagsState,
                        musicTagsEffects = musicTagsEffects,
                        importState = importState,
                        settingsState = settingsState,
                        onMyIntent = onMyIntent,
                        onLibraryIntent = onLibraryIntent,
                        onOnlineLibraryIntent = onOnlineLibraryIntent,
                        onPlaylistsIntent = onPlaylistsIntent,
                        onOnlinePlaylistsIntent = onOnlinePlaylistsIntent,
                        onFavoritesIntent = onFavoritesIntent,
                        onOnlineFavoritesIntent = onOnlineFavoritesIntent,
                        onMusicTagsIntent = onMusicTagsIntent,
                        onImportIntent = onImportIntent,
                        onPlayerIntent = onPlayerIntent,
                        onSettingsIntent = onSettingsIntent,
                        onOpenBackgroundRunSettings = onOpenBackgroundRunSettings,
                        libraryNavigationTarget = libraryNavigationTarget,
                        onLibraryNavigationHandled = onLibraryNavigationHandled,
                        onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
                        onMobileEditorVisibilityChanged = onMobileEditorVisibilityChanged,
                        compact = true,
                        mobileLibraryHub = true,
                        phoneLibraryNavigation = phoneNavigation,
                        playerExpanded = playerState.isExpanded,
                        onTabSelected = ::selectMobileTab,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    if (isMoreSheetVisible) {
        MobileMoreSheet(
            selectedTab = selectedTab,
            sheetState = moreSheetState,
            onDismiss = { isMoreSheetVisible = false },
            onSelect = { tab ->
                isMoreSheetVisible = false
                selectMobileTab(tab)
            },
            tabs = moreTabs,
            showSettingsUpdateBadge = showSettingsUpdateBadge,
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
    tabs: List<AppTab>,
    showSettingsUpdateBadge: Boolean,
) {
    val shellColors = mainShellColors
    val items = remember(tabs) {
        tabs.map { tab ->
            val icon = when (tab) {
                AppTab.Tags -> Icons.Rounded.Tune
                AppTab.Sources -> Icons.Rounded.FolderOpen
                AppTab.Settings -> Icons.Rounded.Settings
                else -> Icons.Rounded.MoreHoriz
            }
            Triple(tab, icon, mobileLibraryHubTabLabel(tab))
        }
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
                    BadgedIcon(
                        imageVector = icon,
                        contentDescription = label,
                        showBadge = tab == AppTab.Settings && showSettingsUpdateBadge,
                        modifier = Modifier.size(24.dp),
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onSecondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
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
    onlineLibraryState: OnlineLibraryState,
    playlistsState: PlaylistsState,
    onlinePlaylistsState: OnlinePlaylistsState,
    favoritesState: FavoritesState,
    onlineFavoritesState: OnlineFavoritesState,
    musicTagsState: MusicTagsState,
    musicTagsEffects: Flow<MusicTagsEffect>,
    importState: ImportState,
    playerState: PlayerState,
    settingsState: SettingsState,
    onMyIntent: (MyIntent) -> Unit,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onOnlineLibraryIntent: (OnlineLibraryIntent) -> Unit,
    onPlaylistsIntent: (PlaylistsIntent) -> Unit,
    onOnlinePlaylistsIntent: (OnlinePlaylistsIntent) -> Unit,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onOnlineFavoritesIntent: (OnlineFavoritesIntent) -> Unit,
    onMusicTagsIntent: (MusicTagsIntent) -> Unit,
    onImportIntent: (ImportIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onSettingsIntent: (SettingsIntent) -> Unit,
    onOpenBackgroundRunSettings: () -> Unit,
    libraryNavigationTarget: LibraryNavigationTarget? = null,
    onLibraryNavigationHandled: () -> Unit = {},
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    onOpenAddToPlaylist: () -> Unit,
) {
    val shellColors = mainShellColors
    val effectivePlayerSnapshot = playerState.effectiveSnapshot
    val playerFavoriteBinding = playerFavoriteBinding(
        track = effectivePlayerSnapshot.currentTrack,
        localFavoriteTrackIds = favoritesState.favoriteTrackIds,
        onlineFavoritesState = onlineFavoritesState,
        importState = importState,
    )
    Row(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
                .background(shellColors.navContainer),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                HeroHeader()
                Spacer(Modifier.height(40.dp))
                DesktopNav(
                    selectedTab = selectedTab,
                    platform = platform,
                    showSettingsUpdateBadge = settingsState.appUpdateHasNewVersion == true,
                    onTabSelected = onTabSelected,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(shellColors.cardBorder.copy(alpha = 0.72f)),
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
                onlineLibraryState = onlineLibraryState,
                playlistsState = playlistsState,
                onlinePlaylistsState = onlinePlaylistsState,
                favoritesState = favoritesState,
                onlineFavoritesState = onlineFavoritesState,
                musicTagsState = musicTagsState,
                musicTagsEffects = musicTagsEffects,
                importState = importState,
                settingsState = settingsState,
                onMyIntent = onMyIntent,
                onLibraryIntent = onLibraryIntent,
                onOnlineLibraryIntent = onOnlineLibraryIntent,
                onPlaylistsIntent = onPlaylistsIntent,
                onOnlinePlaylistsIntent = onOnlinePlaylistsIntent,
                onFavoritesIntent = onFavoritesIntent,
                onOnlineFavoritesIntent = onOnlineFavoritesIntent,
                onMusicTagsIntent = onMusicTagsIntent,
                onImportIntent = onImportIntent,
                onPlayerIntent = onPlayerIntent,
                onSettingsIntent = onSettingsIntent,
                onOpenBackgroundRunSettings = onOpenBackgroundRunSettings,
                libraryNavigationTarget = libraryNavigationTarget,
                onLibraryNavigationHandled = onLibraryNavigationHandled,
                onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
                compact = false,
                mobileLibraryHub = false,
                onTabSelected = onTabSelected,
                modifier = Modifier.weight(1f),
            )
            OfflineBatchDownloadStatusBar()
            LynMusicTheme(
                themeTokens = CLASSIC_APP_THEME_TOKENS,
                textPalette = AppThemeTextPalette.White,
            ) {
                MiniPlayerBarVisibility(
                    visible = (effectivePlayerSnapshot.currentTrack != null || playerState.snapshot.isHydratingPlayback) &&
                            !playerState.isExpanded,
                    state = playerState,
                    onPlayerIntent = onPlayerIntent,
                    isFavorite = playerFavoriteBinding.isFavorite,
                    canToggleFavorite = playerFavoriteBinding.canToggleFavorite,
                    onToggleFavorite = {
                        if (playerFavoriteBinding.canToggleFavorite) {
                            effectivePlayerSnapshot.currentTrack?.let { track ->
                                val onlineSourceId = playerFavoriteBinding.onlineSourceId
                                if (onlineSourceId != null) {
                                    onOnlineFavoritesIntent(
                                        OnlineFavoritesIntent.SetFavorite(
                                            sourceId = onlineSourceId,
                                            track = track,
                                            favorite = !playerFavoriteBinding.isFavorite,
                                        )
                                    )
                                } else {
                                    onFavoritesIntent(FavoritesIntent.ToggleFavorite(track))
                                }
                            }
                        }
                    },
                    onOpenAddToPlaylist = onOpenAddToPlaylist,
                    onOpenQueue = { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(true)) },
                    compact = false,
                    automotiveLandscape = shouldUseAutomotiveLandscapeMiniPlayer(platform),
                )
            }
        }
    }
}

@Composable
private fun DesktopNav(
    selectedTab: AppTab,
    platform: PlatformDescriptor,
    showSettingsUpdateBadge: Boolean,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    val primary = MaterialTheme.colorScheme.primary
    val inactiveContent = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
    val selectedBackground = shellColors.selectedContainer.copy(alpha = 0.64f)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        desktopNavigationTabs(platform).forEach { tab ->
            val icon = when (tab) {
                AppTab.My -> Icons.Rounded.Person
                AppTab.Library -> Icons.Rounded.LibraryMusic
                AppTab.Favorites -> Icons.Rounded.FavoriteBorder
                AppTab.Playlists -> Icons.AutoMirrored.Rounded.List
                AppTab.Tags -> Icons.Rounded.LocalOffer
                AppTab.Sources -> Icons.Rounded.FolderOpen
                AppTab.Settings -> Icons.Rounded.Settings
            }
            val label = mobileLibraryHubTabLabel(tab)
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
                BadgedIcon(
                    imageVector = icon,
                    contentDescription = label,
                    showBadge = tab == AppTab.Settings && showSettingsUpdateBadge,
                    modifier = Modifier.size(24.dp),
                    tint = if (selected) primary else inactiveContent,
                )
                Spacer(Modifier.width(18.dp))
                Text(
                    label,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) primary else inactiveContent,
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
        color = MaterialTheme.colorScheme.onSurface,
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
    onlineLibraryState: OnlineLibraryState,
    playlistsState: PlaylistsState,
    onlinePlaylistsState: OnlinePlaylistsState,
    favoritesState: FavoritesState,
    onlineFavoritesState: OnlineFavoritesState,
    musicTagsState: MusicTagsState,
    musicTagsEffects: Flow<MusicTagsEffect>,
    importState: ImportState,
    settingsState: SettingsState,
    onMyIntent: (MyIntent) -> Unit,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onOnlineLibraryIntent: (OnlineLibraryIntent) -> Unit,
    onPlaylistsIntent: (PlaylistsIntent) -> Unit,
    onOnlinePlaylistsIntent: (OnlinePlaylistsIntent) -> Unit,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onOnlineFavoritesIntent: (OnlineFavoritesIntent) -> Unit,
    onMusicTagsIntent: (MusicTagsIntent) -> Unit,
    onImportIntent: (ImportIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onSettingsIntent: (SettingsIntent) -> Unit,
    onOpenBackgroundRunSettings: () -> Unit,
    libraryNavigationTarget: LibraryNavigationTarget? = null,
    onLibraryNavigationHandled: () -> Unit = {},
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    onMobileEditorVisibilityChanged: (Boolean) -> Unit = {},
    compact: Boolean,
    mobileLibraryHub: Boolean,
    phoneLibraryNavigation: Boolean = false,
    playerExpanded: Boolean = false,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolvedSelectedTab = resolveAppTabForPlatform(selectedTab, platform)
    LaunchedEffect(selectedTab, resolvedSelectedTab) {
        if (resolvedSelectedTab != selectedTab) {
            onTabSelected(resolvedSelectedTab)
        }
    }
    if (mobileLibraryHub && isMobileLibraryHubTab(resolvedSelectedTab)) {
        MobileLibraryHubTab(
            selectedTab = resolvedSelectedTab,
            phoneNavigation = phoneLibraryNavigation,
            libraryState = libraryState,
            onlineLibraryState = onlineLibraryState,
            playlistsState = playlistsState,
            onlinePlaylistsState = onlinePlaylistsState,
            favoritesState = favoritesState,
            onlineFavoritesState = onlineFavoritesState,
            importState = importState,
            onLibraryIntent = onLibraryIntent,
            onOnlineLibraryIntent = onOnlineLibraryIntent,
            onPlaylistsIntent = onPlaylistsIntent,
            onOnlinePlaylistsIntent = onOnlinePlaylistsIntent,
            onFavoritesIntent = onFavoritesIntent,
            onOnlineFavoritesIntent = onOnlineFavoritesIntent,
            onPlayerIntent = onPlayerIntent,
            libraryNavigationTarget = libraryNavigationTarget,
            onLibraryNavigationHandled = onLibraryNavigationHandled,
            playerExpanded = playerExpanded,
            onTabSelected = onTabSelected,
            modifier = modifier,
        )
        return
    }

    when (resolvedSelectedTab) {
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
            importState = importState,
            onlineState = onlineLibraryState,
            onLibraryIntent = onLibraryIntent,
            onFavoritesIntent = onFavoritesIntent,
            onOnlineIntent = onOnlineLibraryIntent,
            onPlayerIntent = onPlayerIntent,
            showDuration = !platform.isMobilePlatform(),
            navigationTarget = libraryNavigationTarget,
            onNavigationHandled = onLibraryNavigationHandled,
            onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
            rootSelectorStyle = if (compact) {
                LibraryRootSelectorStyle.CompactHero
            } else {
                LibraryRootSelectorStyle.Default
            },
            modifier = modifier,
        )

        AppTab.Playlists -> PlaylistsTab(
            state = playlistsState,
            importState = importState,
            onlineState = onlinePlaylistsState,
            onPlaylistsIntent = onPlaylistsIntent,
            onOnlineIntent = onOnlinePlaylistsIntent,
            onPlayerIntent = onPlayerIntent,
            modifier = modifier,
        )

        AppTab.Favorites -> FavoritesTab(
            state = favoritesState,
            importState = importState,
            onlineState = onlineFavoritesState,
            onFavoritesIntent = onFavoritesIntent,
            onOnlineIntent = onOnlineFavoritesIntent,
            onPlayerIntent = onPlayerIntent,
            showDuration = !platform.isMobilePlatform(),
            onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
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
            compact = compact,
            modifier = modifier,
        )

        AppTab.Settings -> SettingsTab(
            platform = platform,
            state = settingsState,
            onSettingsIntent = onSettingsIntent,
            onOpenBackgroundRunSettings = onOpenBackgroundRunSettings,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MobileLibraryHubTab(
    selectedTab: AppTab,
    phoneNavigation: Boolean,
    libraryState: LibraryState,
    onlineLibraryState: OnlineLibraryState,
    playlistsState: PlaylistsState,
    onlinePlaylistsState: OnlinePlaylistsState,
    favoritesState: FavoritesState,
    onlineFavoritesState: OnlineFavoritesState,
    importState: ImportState,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onOnlineLibraryIntent: (OnlineLibraryIntent) -> Unit,
    onPlaylistsIntent: (PlaylistsIntent) -> Unit,
    onOnlinePlaylistsIntent: (OnlinePlaylistsIntent) -> Unit,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onOnlineFavoritesIntent: (OnlineFavoritesIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    libraryNavigationTarget: LibraryNavigationTarget?,
    onLibraryNavigationHandled: () -> Unit,
    playerExpanded: Boolean,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hubTabs = libraryHubTabs(selectedTab, phoneNavigation)
    val phoneLibrary = phoneNavigation && selectedTab == AppTab.Library
    val phoneRootState = rememberSaveable { mutableStateOf(LibraryBrowserRootView.Tracks) }
    var phoneRootClickRequestKey by rememberSaveable { mutableStateOf(0) }
    val initialPage = hubTabs.indexOf(selectedTab).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { hubTabs.size }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isSearchMode by rememberSaveable { mutableStateOf(false) }
    var searchBarAutoFocus by remember { mutableStateOf(false) }
    var playlistSearchQuery by rememberSaveable { mutableStateOf("") }
    var favoritesRefreshHoldActive by rememberSaveable { mutableStateOf(false) }
    var favoritesRefreshHoldKey by rememberSaveable { mutableStateOf(0) }
    var playlistsRefreshHoldActive by rememberSaveable { mutableStateOf(false) }
    var playlistsRefreshHoldKey by rememberSaveable { mutableStateOf(0) }
    var libraryBatchSelectionRequestKey by rememberSaveable { mutableStateOf(0) }
    var favoritesBatchSelectionRequestKey by rememberSaveable { mutableStateOf(0) }
    var playlistsBatchSelectionRequestKey by rememberSaveable { mutableStateOf(0) }
    val favoritesPullRefreshState = rememberPullToRefreshState()
    val playlistsPullRefreshState = rememberPullToRefreshState()
    val activeSearchTab = hubTabs.getOrElse(pagerState.currentPage) { hubTabs.first() }
    val activeSearchQuery = when (activeSearchTab) {
        AppTab.Library -> onlineLibraryState.sourceId?.let { onlineLibraryState.query } ?: libraryState.query
        AppTab.Favorites -> onlineFavoritesState.sourceId?.let { onlineFavoritesState.query } ?: favoritesState.query
        AppTab.Playlists -> playlistSearchQuery
        else -> ""
    }
    val selectedTabSearchQuery = when (selectedTab) {
        AppTab.Library -> onlineLibraryState.sourceId?.let { onlineLibraryState.query } ?: libraryState.query
        AppTab.Favorites -> onlineFavoritesState.sourceId?.let { onlineFavoritesState.query } ?: favoritesState.query
        AppTab.Playlists -> playlistSearchQuery
        else -> ""
    }
    val playlistDetailVisibleTrackCount = remember(
        playlistsState.selectedPlaylist,
        playlistsState.selectedPlaylistId,
        playlistsState.selectedSourceFilter,
        playlistsState.sourceTypesById,
        playlistsState.offlineDownloadsByTrackId,
    ) {
        playlistsState.selectedPlaylist
            ?.takeIf { it.id == playlistsState.selectedPlaylistId }
            ?.tracks
            .orEmpty()
            .count { entry ->
                matchesLibrarySourceFilter(
                    track = entry.track,
                    selectedSourceFilter = playlistsState.selectedSourceFilter,
                    sourceTypesById = playlistsState.sourceTypesById,
                    offlineDownloadsByTrackId = playlistsState.offlineDownloadsByTrackId,
                )
            }
    }
    val activeTabOnline = when (activeSearchTab) {
        AppTab.Library -> onlineLibraryState.sourceId != null
        AppTab.Favorites -> onlineFavoritesState.sourceId != null
        AppTab.Playlists -> onlinePlaylistsState.sourceId != null
        else -> false
    }
    val batchOperationAvailable = (!phoneLibrary || phoneRootState.value == LibraryBrowserRootView.Tracks) && !activeTabOnline && mobileLibraryHubCanBatchOperate(
        tab = activeSearchTab,
        libraryVisibleTrackCount = libraryState.filteredTracks.size,
        favoritesVisibleTrackCount = favoritesState.filteredTracks.size,
        playlistDetailVisibleTrackCount = playlistDetailVisibleTrackCount,
        selectedPlaylistId = playlistsState.selectedPlaylistId,
    )

    fun updateActiveSearchQuery(query: String) {
        when (activeSearchTab) {
            AppTab.Library -> {
                if (onlineLibraryState.sourceId != null) {
                    onOnlineLibraryIntent(OnlineLibraryIntent.SearchChanged(query))
                } else {
                    onLibraryIntent(LibraryIntent.SearchChanged(query))
                }
            }
            AppTab.Favorites -> {
                if (onlineFavoritesState.sourceId != null) {
                    onOnlineFavoritesIntent(OnlineFavoritesIntent.SearchChanged(query))
                } else {
                    onFavoritesIntent(FavoritesIntent.SearchChanged(query))
                }
            }
            AppTab.Playlists -> playlistSearchQuery = query
            else -> Unit
        }
    }

    fun clearActiveSearchQuery() {
        updateActiveSearchQuery("")
    }

    fun exitSearchMode() {
        clearActiveSearchQuery()
        searchBarAutoFocus = false
        isSearchMode = false
    }

    fun enterSearchMode(autoFocus: Boolean) {
        searchBarAutoFocus = autoFocus
        isSearchMode = true
    }

    fun startFavoritesRefreshHold() {
        favoritesRefreshHoldActive = true
        favoritesRefreshHoldKey += 1
    }

    fun startPlaylistsRefreshHold() {
        playlistsRefreshHoldActive = true
        playlistsRefreshHoldKey += 1
    }

    fun requestBatchSelection() {
        when (activeSearchTab) {
            AppTab.Library -> libraryBatchSelectionRequestKey += 1
            AppTab.Favorites -> favoritesBatchSelectionRequestKey += 1
            AppTab.Playlists -> playlistsBatchSelectionRequestKey += 1
            else -> Unit
        }
    }

    LaunchedEffect(selectedTab) {
        val targetPage = hubTabs.indexOf(selectedTab).coerceAtLeast(0)
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }
    LaunchedEffect(playerExpanded) {
        if (playerExpanded) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            searchBarAutoFocus = false
        }
    }
    LaunchedEffect(selectedTab, selectedTabSearchQuery) {
        if (isMobileLibraryHubTab(selectedTab) && !isSearchMode && selectedTabSearchQuery.isNotBlank()) {
            enterSearchMode(autoFocus = false)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                onTabSelected(hubTabs.getOrElse(page) { hubTabs.first() })
            }
    }
    LaunchedEffect(favoritesRefreshHoldKey) {
        if (favoritesRefreshHoldKey <= 0) return@LaunchedEffect
        delay(2_000)
        favoritesRefreshHoldActive = false
    }
    LaunchedEffect(favoritesState.isRefreshing) {
        if (favoritesState.isRefreshing) {
            startFavoritesRefreshHold()
        }
    }
    LaunchedEffect(onlineFavoritesState.isLoading) {
        if (onlineFavoritesState.isLoading) {
            startFavoritesRefreshHold()
        }
    }
    LaunchedEffect(playlistsRefreshHoldKey) {
        if (playlistsRefreshHoldKey <= 0) return@LaunchedEffect
        delay(2_000)
        playlistsRefreshHoldActive = false
    }
    LaunchedEffect(playlistsState.isRefreshing) {
        if (playlistsState.isRefreshing) {
            startPlaylistsRefreshHold()
        }
    }
    LaunchedEffect(onlinePlaylistsState.isLoading, onlinePlaylistsState.isMutating) {
        if (onlinePlaylistsState.isLoading || onlinePlaylistsState.isMutating) {
            startPlaylistsRefreshHold()
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        if (isSearchMode) {
            MobileLibraryHubSearchBar(
                query = activeSearchQuery,
                placeholder = mobileLibraryHubSearchPlaceholder(activeSearchTab),
                autoFocus = searchBarAutoFocus,
                onQueryChanged = ::updateActiveSearchQuery,
                onBack = ::exitSearchMode,
                onClearOrClose = {
                    if (activeSearchQuery.isBlank()) {
                        exitSearchMode()
                    } else {
                        clearActiveSearchQuery()
                    }
                },
            )
        } else {
            MobileLibraryHubTabStrip(
                selectedPage = if (phoneLibrary) phoneRootState.value.ordinal else pagerState.currentPage,
                tabs = hubTabs,
                rootTabs = if (phoneLibrary) LibraryBrowserRootView.entries else emptyList(),
                onRootTabClick = {
                    phoneRootState.value = it
                    phoneRootClickRequestKey += 1
                },
                searchEnabled = !(phoneLibrary && phoneRootState.value == LibraryBrowserRootView.Folders && activeTabOnline),
                batchAction = if (batchOperationAvailable) {
                    MobileLibraryHubBatchAction(onClick = ::requestBatchSelection)
                } else {
                    null
                },
                sourceMenu = when (activeSearchTab) {
                    AppTab.Library -> MobileLibraryHubSourceMenu(
                        selectedSourceFilter = libraryState.selectedSourceFilter,
                        availableSourceFilters = libraryState.availableSourceFilters,
                        onlineSourceOptions = importState.onlineNavidromeSourceOptions(),
                        selectedOnlineSourceId = onlineLibraryState.sourceId,
                        onSourceFilterChanged = { filter ->
                            onOnlineLibraryIntent(OnlineLibraryIntent.SelectSource(sourceId = null))
                            onLibraryIntent(LibraryIntent.SourceFilterChanged(filter))
                        },
                        onOnlineSourceSelected = { sourceId ->
                            onOnlineLibraryIntent(OnlineLibraryIntent.SelectSource(sourceId))
                        },
                    )

                    AppTab.Favorites -> MobileLibraryHubSourceMenu(
                        selectedSourceFilter = favoritesState.selectedSourceFilter,
                        availableSourceFilters = favoritesState.availableSourceFilters,
                        onlineSourceOptions = importState.onlineNavidromeSourceOptions(),
                        selectedOnlineSourceId = onlineFavoritesState.sourceId,
                        onSourceFilterChanged = { filter ->
                            onOnlineFavoritesIntent(OnlineFavoritesIntent.SelectSource(sourceId = null))
                            onFavoritesIntent(FavoritesIntent.SourceFilterChanged(filter))
                        },
                        onOnlineSourceSelected = { sourceId ->
                            onOnlineFavoritesIntent(OnlineFavoritesIntent.SelectSource(sourceId))
                        },
                    )

                    AppTab.Playlists -> MobileLibraryHubSourceMenu(
                        selectedSourceFilter = playlistsState.selectedSourceFilter,
                        availableSourceFilters = playlistsState.availableSourceFilters,
                        onlineSourceOptions = importState.onlineNavidromeSourceOptions(),
                        selectedOnlineSourceId = onlinePlaylistsState.sourceId,
                        onSourceFilterChanged = { filter ->
                            onOnlinePlaylistsIntent(OnlinePlaylistsIntent.SelectSource(sourceId = null))
                            onPlaylistsIntent(PlaylistsIntent.SourceFilterChanged(filter))
                        },
                        onOnlineSourceSelected = { sourceId ->
                            onOnlinePlaylistsIntent(OnlinePlaylistsIntent.SelectSource(sourceId))
                        },
                    )

                    else -> null
                },
                sortMenu = when (activeSearchTab) {
                    AppTab.Library -> if (onlineLibraryState.sourceId == null && (!phoneLibrary || phoneRootState.value == LibraryBrowserRootView.Tracks)) MobileLibraryHubSortMenu(
                        selectedTrackSortMode = libraryState.selectedTrackSortMode,
                        onTrackSortChanged = { mode ->
                            onLibraryIntent(LibraryIntent.TrackSortChanged(mode))
                        },
                    ) else null

                    AppTab.Favorites -> if (onlineFavoritesState.sourceId == null) MobileLibraryHubSortMenu(
                        selectedTrackSortMode = favoritesState.selectedTrackSortMode,
                        onTrackSortChanged = { mode ->
                            onFavoritesIntent(FavoritesIntent.TrackSortChanged(mode))
                        },
                    ) else null

                    else -> null
                },
                onSearchClick = {
                    if (activeSearchTab == AppTab.Playlists) {
                        if (onlinePlaylistsState.sourceId != null) {
                            onOnlinePlaylistsIntent(OnlinePlaylistsIntent.SelectPlaylist(null))
                        } else {
                            onPlaylistsIntent(PlaylistsIntent.BackToList)
                        }
                    }
                    enterSearchMode(autoFocus = true)
                },
                onTabClick = { index, tab ->
                    onTabSelected(tab)
                    coroutineScope.launch {
                        pagerState.scrollToPage(index)
                    }
                },
            )
        }
        val pageContent: @Composable (Int) -> Unit = { page ->
            when (hubTabs.getOrElse(page) { hubTabs.first() }) {
                AppTab.Library -> LibraryTab(
                    state = libraryState,
                    favoritesState = favoritesState,
                    importState = importState,
                    onlineState = onlineLibraryState,
                    onLibraryIntent = onLibraryIntent,
                    onFavoritesIntent = onFavoritesIntent,
                    onOnlineIntent = onOnlineLibraryIntent,
                    onPlayerIntent = onPlayerIntent,
                    showDuration = false,
                    showSearchField = false,
                    batchSelectionRequestKey = libraryBatchSelectionRequestKey,
                    showInlineBatchOperationButton = false,
                    navigationTarget = libraryNavigationTarget,
                    onNavigationHandled = onLibraryNavigationHandled,
                    rootSelectorStyle = LibraryRootSelectorStyle.CompactHero,
                    phoneRootState = if (phoneLibrary) phoneRootState else null,
                    phoneRootClickRequestKey = if (phoneLibrary) phoneRootClickRequestKey else 0,
                    rootPagerScrollEnabled = !isSearchMode,
                    modifier = Modifier.fillMaxSize(),
                )

                AppTab.Favorites -> {
                    val isOnlineFavoritesMode = onlineFavoritesState.sourceId != null
                    val isFavoritesRefreshing = mobileLibraryHubRefreshIndicatorVisible(
                        isRefreshing = if (isOnlineFavoritesMode) onlineFavoritesState.isLoading else favoritesState.isRefreshing,
                        isMinimumHoldActive = favoritesRefreshHoldActive,
                    )
                    PullToRefreshBox(
                        isRefreshing = isFavoritesRefreshing,
                        onRefresh = {
                            startFavoritesRefreshHold()
                            if (isOnlineFavoritesMode) {
                                onOnlineFavoritesIntent(OnlineFavoritesIntent.Refresh)
                            } else {
                                onFavoritesIntent(FavoritesIntent.Refresh)
                            }
                        },
                        enabled = if (isOnlineFavoritesMode) {
                            onlineFavoritesState.sourceId != null
                        } else {
                            favoritesState.canRefreshRemote
                        },
                        modifier = Modifier.fillMaxSize(),
                        state = favoritesPullRefreshState,
                        indicator = {
                            PullToRefreshDefaults.Indicator(
                                isRefreshing = isFavoritesRefreshing,
                                state = favoritesPullRefreshState,
                                modifier = Modifier.align(Alignment.TopCenter),
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                    ) {
                        FavoritesTab(
                            state = favoritesState,
                            importState = importState,
                            onlineState = onlineFavoritesState,
                            onFavoritesIntent = onFavoritesIntent,
                            onOnlineIntent = onOnlineFavoritesIntent,
                            onPlayerIntent = onPlayerIntent,
                            showDuration = false,
                            showSearchField = false,
                            showRefreshActionButton = false,
                            batchSelectionRequestKey = favoritesBatchSelectionRequestKey,
                            showInlineBatchOperationButton = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                AppTab.Playlists -> {
                    val isOnlinePlaylistsMode = onlinePlaylistsState.sourceId != null
                    val isPlaylistsRefreshing = mobileLibraryHubRefreshIndicatorVisible(
                        isRefreshing = if (isOnlinePlaylistsMode) {
                            onlinePlaylistsState.isLoading || onlinePlaylistsState.isMutating
                        } else {
                            playlistsState.isRefreshing
                        },
                        isMinimumHoldActive = playlistsRefreshHoldActive,
                    )
                    PullToRefreshBox(
                        isRefreshing = isPlaylistsRefreshing,
                        onRefresh = {
                            startPlaylistsRefreshHold()
                            if (isOnlinePlaylistsMode) {
                                onOnlinePlaylistsIntent(OnlinePlaylistsIntent.Refresh)
                            } else {
                                onPlaylistsIntent(PlaylistsIntent.Refresh)
                            }
                        },
                        enabled = if (isOnlinePlaylistsMode) onlinePlaylistsState.sourceId != null else true,
                        modifier = Modifier.fillMaxSize(),
                        state = playlistsPullRefreshState,
                        indicator = {
                            PullToRefreshDefaults.Indicator(
                                isRefreshing = isPlaylistsRefreshing,
                                state = playlistsPullRefreshState,
                                modifier = Modifier.align(Alignment.TopCenter),
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                    ) {
                        PlaylistsTab(
                            state = playlistsState,
                            importState = importState,
                            onlineState = onlinePlaylistsState,
                            onPlaylistsIntent = onPlaylistsIntent,
                            onOnlineIntent = onOnlinePlaylistsIntent,
                            onPlayerIntent = onPlayerIntent,
                            playlistSearchQuery = playlistSearchQuery,
                            showRefreshActionButton = false,
                            showSourceFilterActionButton = false,
                            batchSelectionRequestKey = playlistsBatchSelectionRequestKey,
                            showInlineBatchOperationButton = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                else -> Unit
            }
        }
        if (phoneLibrary) {
            Box(Modifier.weight(1f)) { pageContent(0) }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = !isSearchMode,
                key = { page -> hubTabs[page].name },
            ) { page -> pageContent(page) }
        }
    }
}

private data class MobileLibraryHubSourceMenu(
    val selectedSourceFilter: LibrarySourceFilter,
    val availableSourceFilters: List<LibrarySourceFilter>,
    val onlineSourceOptions: List<OnlineSourceOption> = emptyList(),
    val selectedOnlineSourceId: String? = null,
    val onSourceFilterChanged: (LibrarySourceFilter) -> Unit,
    val onOnlineSourceSelected: (String) -> Unit = {},
)

private data class MobileLibraryHubSortMenu(
    val selectedTrackSortMode: TrackSortMode,
    val onTrackSortChanged: (TrackSortMode) -> Unit,
)

private data class MobileLibraryHubBatchAction(
    val onClick: () -> Unit,
)

private enum class MobileLibraryHubMenuLayer {
    Root,
    Source,
    Sort,
}

@Composable
private fun MobileLibraryHubTabStrip(
    selectedPage: Int,
    tabs: List<AppTab> = mobileLibraryHubTabs,
    rootTabs: List<LibraryBrowserRootView> = emptyList(),
    onRootTabClick: (LibraryBrowserRootView) -> Unit = {},
    searchEnabled: Boolean = true,
    batchAction: MobileLibraryHubBatchAction?,
    sourceMenu: MobileLibraryHubSourceMenu?,
    sortMenu: MobileLibraryHubSortMenu?,
    onSearchClick: () -> Unit,
    onTabClick: (Int, AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var menuLayer by remember { mutableStateOf(MobileLibraryHubMenuLayer.Root) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(start = 20.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(if (rootTabs.isEmpty()) 8.dp else 0.dp),
        ) {
            if (rootTabs.isNotEmpty()) rootTabs.forEachIndexed { index, root ->
                MobileLibraryHubTabItem(
                    label = phoneLibraryRootLabel(root),
                    compact = true,
                    selected = selectedPage == index,
                    onClick = { onRootTabClick(root) },
                )
            } else tabs.forEachIndexed { index, tab ->
                MobileLibraryHubTabItem(
                    label = mobileLibraryHubTabLabel(tab),
                    selected = selectedPage == index,
                    onClick = { onTabClick(index, tab) },
                )
            }
        }
        if (batchAction != null || sourceMenu != null || sortMenu != null) {
            Box {
                IconButton(
                    onClick = {
                        menuLayer = MobileLibraryHubMenuLayer.Root
                        menuExpanded = true
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "更多操作",
                    )
                }
                MobileLibraryHubActionsDropdownMenu(
                    expanded = menuExpanded,
                    layer = menuLayer,
                    batchAction = batchAction,
                    sourceMenu = sourceMenu,
                    sortMenu = sortMenu,
                    onLayerChanged = { menuLayer = it },
                    onDismiss = { menuExpanded = false },
                    onSourceFilterChanged = { filter ->
                        menuExpanded = false
                        sourceMenu?.onSourceFilterChanged(filter)
                    },
                    onOnlineSourceSelected = { sourceId ->
                        menuExpanded = false
                        sourceMenu?.onOnlineSourceSelected(sourceId)
                    },
                    onTrackSortChanged = { mode ->
                        menuExpanded = false
                        sortMenu?.onTrackSortChanged(mode)
                    },
                )
            }
        }
        IconButton(onClick = onSearchClick, enabled = searchEnabled) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "搜索",
            )
        }
    }
}

@Composable
private fun MobileLibraryHubActionsDropdownMenu(
    expanded: Boolean,
    layer: MobileLibraryHubMenuLayer,
    batchAction: MobileLibraryHubBatchAction?,
    sourceMenu: MobileLibraryHubSourceMenu?,
    sortMenu: MobileLibraryHubSortMenu?,
    onLayerChanged: (MobileLibraryHubMenuLayer) -> Unit,
    onDismiss: () -> Unit,
    onSourceFilterChanged: (LibrarySourceFilter) -> Unit,
    onOnlineSourceSelected: (String) -> Unit,
    onTrackSortChanged: (TrackSortMode) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = mainShellColors.navContainer,
    ) {
        when (layer) {
            MobileLibraryHubMenuLayer.Root -> {
                if (batchAction != null) {
                    DropdownMenuItem(
                        text = { Text("批量操作") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Checklist,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            onDismiss()
                            batchAction.onClick()
                        },
                    )
                }
                if (sourceMenu != null) {
                    DropdownMenuItem(
                        text = { Text("来源") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                            )
                        },
                        onClick = { onLayerChanged(MobileLibraryHubMenuLayer.Source) },
                    )
                }
                if (sortMenu != null) {
                    DropdownMenuItem(
                        text = { Text("排序") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = null,
                            )
                        },
                        onClick = { onLayerChanged(MobileLibraryHubMenuLayer.Sort) },
                    )
                }
            }

            MobileLibraryHubMenuLayer.Source -> {
                DropdownMenuItem(
                    text = { Text("来源") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                        )
                    },
                    onClick = { onLayerChanged(MobileLibraryHubMenuLayer.Root) },
                )
                sourceMenu?.availableSourceFilters.orEmpty().forEach { filter ->
                    val isSelected = sourceMenu?.selectedOnlineSourceId == null &&
                        filter == sourceMenu?.selectedSourceFilter
                    DropdownMenuItem(
                        text = { Text(mobileLibraryHubSourceFilterMenuLabel(filter)) },
                        trailingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                )
                            }
                        } else {
                            null
                        },
                        onClick = { onSourceFilterChanged(filter) },
                    )
                }
                sourceMenu?.onlineSourceOptions.orEmpty().forEach { option ->
                    val isSelected = option.sourceId == sourceMenu?.selectedOnlineSourceId
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        trailingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                )
                            }
                        } else {
                            null
                        },
                        onClick = { onOnlineSourceSelected(option.sourceId) },
                    )
                }
            }

            MobileLibraryHubMenuLayer.Sort -> {
                DropdownMenuItem(
                    text = { Text("排序") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                        )
                    },
                    onClick = { onLayerChanged(MobileLibraryHubMenuLayer.Root) },
                )
                TrackSortMode.entries.forEach { mode ->
                    val isSelected = mode == sortMenu?.selectedTrackSortMode
                    DropdownMenuItem(
                        text = { Text(trackSortModeLabel(mode)) },
                        trailingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                )
                            }
                        } else {
                            null
                        },
                        onClick = { onTrackSortChanged(mode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileLibraryHubSearchBar(
    query: String,
    placeholder: String,
    autoFocus: Boolean,
    onQueryChanged: (String) -> Unit,
    onBack: () -> Unit,
    onClearOrClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val shellColors = mainShellColors
    val searchFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = shellColors.cardBorder,
        unfocusedContainerColor = shellColors.cardBorder,
        disabledContainerColor = shellColors.cardBorder,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
    )
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = {
                keyboardController?.hide()
                onBack()
            },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "退出搜索",
            )
        }
        ImeAwareOutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            placeholder = {
                Text(
                    text = placeholder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            shape = RoundedCornerShape(22.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = searchFieldColors,
        )
        IconButton(
            onClick = {
                if (query.isBlank()) {
                    keyboardController?.hide()
                }
                onClearOrClose()
            },
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = if (query.isBlank()) "关闭搜索" else "清空搜索",
            )
        }
    }
}

@Composable
private fun MobileLibraryHubTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f)
    Box(
        modifier = modifier
            .height(44.dp)
            .widthIn(min = if (compact) 48.dp else 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 12.dp),
    ) {
        Text(
            text = label,
            color = if (selected) activeColor else inactiveColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center),
        )
        Box(
            modifier = Modifier
                .size(width = 24.dp, height = 3.dp)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (selected) activeColor else Color.Transparent),
        )
    }
}
