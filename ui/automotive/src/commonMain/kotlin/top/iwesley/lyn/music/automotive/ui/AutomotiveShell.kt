package top.iwesley.lyn.music.automotive.ui

import top.iwesley.lyn.music.*

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.uiPlural
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.UiText

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Menu
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
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailItemDefaults
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
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
import top.iwesley.lyn.music.feature.library.LibrarySourceFilterOption
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
import top.iwesley.lyn.music.automotive.ui.platform.PlatformBackHandler
import top.iwesley.lyn.music.ui.LynMusicTheme
import top.iwesley.lyn.music.ui.mainShellColors

internal val mobilePrimaryNavigationTabs: List<AppTab> = listOf(AppTab.Library, AppTab.My)
internal val mobileLibraryHubTabs: List<AppTab> = listOf(AppTab.Library, AppTab.Favorites, AppTab.Playlists)

internal val automotivePortraitNavigationTabs = listOf(AppTab.Library, AppTab.Favorites, AppTab.My)
internal val automotiveCollectionTabs = listOf(AppTab.Favorites, AppTab.Playlists)

internal fun automotivePrimaryNavigationTabs(portraitNavigation: Boolean): List<AppTab> =
    if (portraitNavigation) automotivePortraitNavigationTabs else mobilePrimaryNavigationTabs

internal fun libraryHubTabs(selectedTab: AppTab, portraitNavigation: Boolean): List<AppTab> = when {
    !portraitNavigation -> mobileLibraryHubTabs
    selectedTab == AppTab.Library -> listOf(AppTab.Library)
    else -> automotiveCollectionTabs
}

internal fun automotiveNavigationGroup(tab: AppTab): AppTab =
    if (tab in automotiveCollectionTabs) AppTab.Favorites else tab

internal fun automotiveLibraryRootLabelText(root: LibraryBrowserRootView): UiText = when (root) {
    LibraryBrowserRootView.Tracks -> uiText(Res.string.library_tracks_title)
    LibraryBrowserRootView.Albums -> uiText(Res.string.library_albums_title)
    LibraryBrowserRootView.Artists -> uiText(Res.string.library_artists_title)
    LibraryBrowserRootView.Folders -> uiText(Res.string.library_folders_title)
}

@Composable
internal fun automotiveLibraryRootLabel(root: LibraryBrowserRootView): String = automotiveLibraryRootLabelText(root).displayText()

internal fun resolveAutomotiveTab(tab: AppTab): AppTab =
    if (tab == AppTab.Tags) AppTab.Library else tab

internal val automotiveMoreNavigationTabs = listOf(AppTab.Sources, AppTab.Settings)

internal val automotiveLandscapeNavigationTabs =
    listOf(AppTab.My, AppTab.Library, AppTab.Favorites, AppTab.Playlists) + automotiveMoreNavigationTabs

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

internal fun mobileLibraryHubTabLabelText(tab: AppTab): UiText {
    return when (tab) {
        AppTab.Library -> uiText(Res.string.library_title)
        AppTab.Favorites -> uiText(Res.string.favorites_likes_title)
        AppTab.Playlists -> uiText(Res.string.playlists_title)
        AppTab.My -> uiText(Res.string.navigation_my_music)
        AppTab.Tags -> uiText(Res.string.tags_editor_title)
        AppTab.Sources -> uiText(Res.string.sources_title)
        AppTab.Settings -> uiText(Res.string.settings_title)
    }
}

@Composable
internal fun mobileLibraryHubTabLabel(tab: AppTab): String = mobileLibraryHubTabLabelText(tab).displayText()

internal fun mobileLibraryHubSearchPlaceholderText(tab: AppTab): UiText {
    return when (tab) {
        AppTab.Library -> uiText(Res.string.library_search_placeholder)
        AppTab.Favorites -> uiText(Res.string.favorites_search_placeholder)
        AppTab.Playlists -> uiText(Res.string.playlists_search_placeholder)
        else -> uiText(Res.string.common_search)
    }
}

@Composable
internal fun mobileLibraryHubSearchPlaceholder(tab: AppTab): String = mobileLibraryHubSearchPlaceholderText(tab).displayText()

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AutomotivePortraitShell(
    portraitNavigation: Boolean,
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
    onImportIntent: (ImportIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onSettingsIntent: (SettingsIntent) -> Unit,
    onOpenBackgroundRunSettings: () -> Unit,
    libraryNavigationTarget: LibraryNavigationTarget? = null,
    onLibraryNavigationHandled: () -> Unit = {},
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    mobilePortraitMiniPlayer: Boolean,
    hideMiniPlayerBar: Boolean,
    onOpenAddToPlaylist: () -> Unit,
    onTabChanged: (AppTab) -> Unit = onTabSelected,
) {
    val presentation = currentPlayerPresentation()
    val navigationTabs = automotivePrimaryNavigationTabs(portraitNavigation)
    var lastCollectionTab by rememberSaveable { mutableStateOf(AppTab.Favorites) }
    val contentStateHolder = rememberSaveableStateHolder()
    val dockBackdrop = if (portraitNavigation) rememberLayerBackdrop() else null
    val miniPlayerGlass = dockBackdrop?.let { miniPlayerGlassStyle(it) }
    val layoutDirection = LocalLayoutDirection.current
    val contentBehindDock = portraitNavigation &&
        (isMobileLibraryHubTab(selectedTab) || selectedTab == AppTab.My)
    LaunchedEffect(selectedTab) {
        if (selectedTab in automotiveCollectionTabs) lastCollectionTab = selectedTab
    }
    val shellColors = mainShellColors
    val mobileNavIconSize = 29.dp
    val moreTabs = automotiveMoreNavigationTabs
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
        if (portraitNavigation && tab in automotiveCollectionTabs) lastCollectionTab = tab
        if (isMobileLibraryHubTab(selectedTab) &&
            (!isMobileLibraryHubTab(tab) ||
                (portraitNavigation && automotiveNavigationGroup(selectedTab) != automotiveNavigationGroup(tab)))
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
                                !presentation.isExpanded &&
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
                        onOpenQueue = { presentation.showQueue(true) },
                        mobile = true,
                        mobilePortraitMiniPlayer = mobilePortraitMiniPlayer,
                        glassStyle = miniPlayerGlass,
                    )
                }
                if (dockBackdrop != null) {
                    // MobileMiniPlayerBar already contributes 10dp of bottom padding.
                    if (effectivePlayerSnapshot.currentTrack != null && !presentation.isExpanded && !hideMiniPlayerBar) {
                        Spacer(Modifier.height(2.dp))
                    }
                    Box(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LiquidGlassDock(
                            selectedIndex = when {
                                isMoreSheetVisible || isMoreSelected -> 3
                                else -> navigationTabs.indexOf(automotiveNavigationGroup(selectedTab)).coerceAtLeast(0)
                            },
                            backdrop = dockBackdrop,
                            showUpdateBadge = showSettingsUpdateBadge,
                            enabled = !isMoreSheetVisible && !presentation.isExpanded,
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
                        val label = if (portraitNavigation && tab == AppTab.Favorites) uiString(Res.string.favorites_collection_title) else mobileLibraryHubTabLabel(tab)
                        NavigationBarItem(
                            selected = if (portraitNavigation) automotiveNavigationGroup(selectedTab) == tab
                            else isMobilePrimaryNavigationSelected(selectedTab, tab),
                            onClick = {
                                isMoreSheetVisible = false
                                selectMobileTab(if (portraitNavigation && tab == AppTab.Favorites) lastCollectionTab else tab)
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
                                contentDescription = uiString(Res.string.common_more),
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
                    key = if (portraitNavigation) automotiveNavigationGroup(selectedTab).name else "legacy",
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
                        importState = importState,
                        settingsState = settingsState,
                        onMyIntent = onMyIntent,
                        onLibraryIntent = onLibraryIntent,
                        onOnlineLibraryIntent = onOnlineLibraryIntent,
                        onPlaylistsIntent = onPlaylistsIntent,
                        onOnlinePlaylistsIntent = onOnlinePlaylistsIntent,
                        onFavoritesIntent = onFavoritesIntent,
                        onOnlineFavoritesIntent = onOnlineFavoritesIntent,
                        onImportIntent = onImportIntent,
                        onPlayerIntent = onPlayerIntent,
                        onSettingsIntent = onSettingsIntent,
                        onOpenBackgroundRunSettings = onOpenBackgroundRunSettings,
                        libraryNavigationTarget = libraryNavigationTarget,
                        onLibraryNavigationHandled = onLibraryNavigationHandled,
                        onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
                        compact = true,
                        mobileLibraryHub = true,
                        playerExpanded = presentation.isExpanded,
                        onTabSelected = ::selectMobileTab,
                        onTabChanged = onTabChanged,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    if (isMoreSheetVisible) {
        AutomotiveMoreSheet(
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
private fun AutomotiveMoreSheet(
    selectedTab: AppTab,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    onSelect: (AppTab) -> Unit,
    tabs: List<AppTab>,
    showSettingsUpdateBadge: Boolean,
) {
    val shellColors = mainShellColors
    val items = tabs.map { tab ->
            val icon = when (tab) {
                AppTab.Tags -> Icons.Rounded.Tune
                AppTab.Sources -> Icons.Rounded.FolderOpen
                AppTab.Settings -> Icons.Rounded.Settings
                else -> Icons.Rounded.MoreHoriz
            }
            Triple(tab, icon, mobileLibraryHubTabLabel(tab))
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
                text = uiString(Res.string.common_more),
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
internal fun AutomotiveLandscapeShell(
    navigationRailState: WideNavigationRailState,
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
    onImportIntent: (ImportIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onSettingsIntent: (SettingsIntent) -> Unit,
    onOpenBackgroundRunSettings: () -> Unit,
    libraryNavigationTarget: LibraryNavigationTarget? = null,
    onLibraryNavigationHandled: () -> Unit = {},
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    onOpenAddToPlaylist: () -> Unit,
    onTabChanged: (AppTab) -> Unit = onTabSelected,
) {
    val presentation = currentPlayerPresentation()
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
                .fillMaxHeight()
                .background(shellColors.navContainer),
        ) {
            AutomotiveSideNavigation(
                state = navigationRailState,
                selectedTab = selectedTab,
                showSettingsUpdateBadge = settingsState.appUpdateHasNewVersion == true,
                onTabSelected = onTabSelected,
            )
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
                importState = importState,
                settingsState = settingsState,
                onMyIntent = onMyIntent,
                onLibraryIntent = onLibraryIntent,
                onOnlineLibraryIntent = onOnlineLibraryIntent,
                onPlaylistsIntent = onPlaylistsIntent,
                onOnlinePlaylistsIntent = onOnlinePlaylistsIntent,
                onFavoritesIntent = onFavoritesIntent,
                onOnlineFavoritesIntent = onOnlineFavoritesIntent,
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
                onTabChanged = onTabChanged,
                modifier = Modifier.weight(1f),
            )
            OfflineBatchDownloadStatusBar()
            LynMusicTheme(
                themeTokens = CLASSIC_APP_THEME_TOKENS,
                textPalette = AppThemeTextPalette.White,
            ) {
                MiniPlayerBarVisibility(
                    visible = (effectivePlayerSnapshot.currentTrack != null || playerState.snapshot.isHydratingPlayback) &&
                            !presentation.isExpanded,
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
                    onOpenQueue = { presentation.showQueue(true) },
                    compact = false,
                )
            }
        }
    }
}

@Composable
internal fun AutomotiveSideNavigation(
    state: WideNavigationRailState,
    selectedTab: AppTab,
    showSettingsUpdateBadge: Boolean,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    val primary = MaterialTheme.colorScheme.primary
    val inactiveContent = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
    val expanded = state.targetValue == WideNavigationRailValue.Expanded
    val scope = rememberCoroutineScope()
    val toggleLabel = uiString(
        if (expanded) Res.string.navigation_collapse else Res.string.navigation_expand,
    )
    val defaultExpandedPadding = WideNavigationRailItemDefaults.IndicatorExpandedPadding
    val itemIndicatorPadding = WideNavigationRailItemDefaults.indicatorPadding(
        railExpanded = expanded,
        expandedPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = defaultExpandedPadding.calculateTopPadding(),
            bottom = defaultExpandedPadding.calculateBottomPadding(),
        ),
    )
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 76.dp) {
        WideNavigationRail(
            state = state,
            modifier = modifier.testTag("automotive-navigation-rail"),
            colors = WideNavigationRailDefaults.colors(containerColor = shellColors.navContainer),
            arrangement = Arrangement.Top,
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            // Keep a bounded, scrollable column inside the rail: the rail itself measures against
            // a finite height. Intrinsic width lets expanded labels determine the native rail width.
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .verticalScroll(rememberScrollState())
                    .testTag("automotive-navigation-scroll"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { scope.launch { state.toggle() } },
                        modifier = Modifier.size(76.dp).testTag("automotive-navigation-toggle"),
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.AutoMirrored.Rounded.MenuOpen else Icons.Rounded.Menu,
                            contentDescription = toggleLabel,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    if (expanded) {
                        Text(
                            text = "LynMusic",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp, end = 10.dp),
                        )
                    }
                }
                automotiveLandscapeNavigationTabs.forEach { tab ->
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
                    Box(Modifier.fillMaxWidth()) {
                        WideNavigationRailItem(
                            selected = selected,
                            onClick = { onTabSelected(tab) },
                            railExpanded = expanded,
                            indicatorPadding = itemIndicatorPadding,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 76.dp)
                                .testTag("automotive-navigation-${tab.name}"),
                            colors = WideNavigationRailItemDefaults.colors(
                                selectedIconColor = primary,
                                selectedTextColor = primary,
                                selectedIndicatorColor = Color.Transparent,
                                unselectedIconColor = inactiveContent,
                                unselectedTextColor = inactiveContent,
                            ),
                            icon = {
                                BadgedIcon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    showBadge = tab == AppTab.Settings && showSettingsUpdateBadge,
                                    modifier = Modifier.size(28.dp).testTag("automotive-navigation-icon-${tab.name}"),
                                    iconModifier = Modifier.size(28.dp),
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    textAlign = if (expanded) TextAlign.Start else TextAlign.Center,
                                    modifier = Modifier
                                        .layout { measurable, constraints ->
                                            // Fill the label's remaining space so Material's centered
                                            // content spans the row. Reserve its 20dp outer space,
                                            // 14dp indicator edges, 28dp icon and 8dp icon-label gap.
                                            val labelConstraints = if (expanded && constraints.hasBoundedWidth) {
                                                val width = (constraints.maxWidth - 84.dp.roundToPx()).coerceAtLeast(0)
                                                constraints.copy(minWidth = width, maxWidth = width)
                                            } else {
                                                constraints
                                            }
                                            val placeable = measurable.measure(labelConstraints)
                                            layout(placeable.width, placeable.height) {
                                                placeable.placeRelative(0, 0)
                                            }
                                        }
                                        .testTag("automotive-navigation-label-${tab.name}"),
                                )
                            },
                        )
                        if (selected) {
                            Box(
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 12.dp)
                                    .size(width = 3.dp, height = 28.dp)
                                    .background(primary, RoundedCornerShape(50))
                                    .testTag("automotive-navigation-marker"),
                            )
                        }
                    }
                }
            }
        }
    }
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
    importState: ImportState,
    settingsState: SettingsState,
    onMyIntent: (MyIntent) -> Unit,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onOnlineLibraryIntent: (OnlineLibraryIntent) -> Unit,
    onPlaylistsIntent: (PlaylistsIntent) -> Unit,
    onOnlinePlaylistsIntent: (OnlinePlaylistsIntent) -> Unit,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onOnlineFavoritesIntent: (OnlineFavoritesIntent) -> Unit,
    onImportIntent: (ImportIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onSettingsIntent: (SettingsIntent) -> Unit,
    onOpenBackgroundRunSettings: () -> Unit,
    libraryNavigationTarget: LibraryNavigationTarget? = null,
    onLibraryNavigationHandled: () -> Unit = {},
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    compact: Boolean,
    mobileLibraryHub: Boolean,
    playerExpanded: Boolean = false,
    onTabSelected: (AppTab) -> Unit,
    onTabChanged: (AppTab) -> Unit = onTabSelected,
    modifier: Modifier = Modifier,
) {
    MusicDetailHost(onPlayerIntent, modifier, showDuration = !compact) {
        RootTabContent(
            selectedTab = selectedTab,
            platform = platform,
            myState = myState,
            libraryState = libraryState,
            onlineLibraryState = onlineLibraryState,
            playlistsState = playlistsState,
            onlinePlaylistsState = onlinePlaylistsState,
            favoritesState = favoritesState,
            onlineFavoritesState = onlineFavoritesState,
            importState = importState,
            settingsState = settingsState,
            onMyIntent = onMyIntent,
            onLibraryIntent = onLibraryIntent,
            onOnlineLibraryIntent = onOnlineLibraryIntent,
            onPlaylistsIntent = onPlaylistsIntent,
            onOnlinePlaylistsIntent = onOnlinePlaylistsIntent,
            onFavoritesIntent = onFavoritesIntent,
            onOnlineFavoritesIntent = onOnlineFavoritesIntent,
            onImportIntent = onImportIntent,
            onPlayerIntent = onPlayerIntent,
            onSettingsIntent = onSettingsIntent,
            onOpenBackgroundRunSettings = onOpenBackgroundRunSettings,
            libraryNavigationTarget = libraryNavigationTarget,
            onLibraryNavigationHandled = onLibraryNavigationHandled,
            onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
            compact = compact,
            mobileLibraryHub = mobileLibraryHub,
            playerExpanded = playerExpanded,
            onTabSelected = onTabSelected,
            onTabChanged = onTabChanged,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun RootTabContent(
    selectedTab: AppTab,
    platform: PlatformDescriptor,
    myState: MyState,
    libraryState: LibraryState,
    onlineLibraryState: OnlineLibraryState,
    playlistsState: PlaylistsState,
    onlinePlaylistsState: OnlinePlaylistsState,
    favoritesState: FavoritesState,
    onlineFavoritesState: OnlineFavoritesState,
    importState: ImportState,
    settingsState: SettingsState,
    onMyIntent: (MyIntent) -> Unit,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onOnlineLibraryIntent: (OnlineLibraryIntent) -> Unit,
    onPlaylistsIntent: (PlaylistsIntent) -> Unit,
    onOnlinePlaylistsIntent: (OnlinePlaylistsIntent) -> Unit,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onOnlineFavoritesIntent: (OnlineFavoritesIntent) -> Unit,
    onImportIntent: (ImportIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onSettingsIntent: (SettingsIntent) -> Unit,
    onOpenBackgroundRunSettings: () -> Unit,
    libraryNavigationTarget: LibraryNavigationTarget? = null,
    onLibraryNavigationHandled: () -> Unit = {},
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    compact: Boolean,
    mobileLibraryHub: Boolean,
    playerExpanded: Boolean = false,
    onTabSelected: (AppTab) -> Unit,
    onTabChanged: (AppTab) -> Unit = onTabSelected,
    modifier: Modifier = Modifier,
) {
    val resolvedSelectedTab = resolveAutomotiveTab(selectedTab)
    LaunchedEffect(selectedTab, resolvedSelectedTab) {
        if (resolvedSelectedTab != selectedTab) {
            onTabChanged(resolvedSelectedTab)
        }
    }
    val libraryEntry = LocalAutomotiveLibraryEntry.current ?: rememberAutomotiveLibraryEntry()
    if (resolvedSelectedTab == AppTab.Library) {
        libraryEntry {
            AutomotiveLibraryContent(
                libraryState = libraryState,
                onlineLibraryState = onlineLibraryState,
                favoritesState = favoritesState,
                importState = importState,
                onLibraryIntent = onLibraryIntent,
                onOnlineLibraryIntent = onOnlineLibraryIntent,
                onFavoritesIntent = onFavoritesIntent,
                onPlayerIntent = onPlayerIntent,
                libraryNavigationTarget = libraryNavigationTarget,
                onLibraryNavigationHandled = onLibraryNavigationHandled,
                onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
                compact = compact,
                modifier = modifier,
            )
        }
        return
    }
    if (mobileLibraryHub && isMobileLibraryHubTab(resolvedSelectedTab)) {
        MobileLibraryHubTab(
            selectedTab = resolvedSelectedTab,
            playlistsState = playlistsState,
            onlinePlaylistsState = onlinePlaylistsState,
            favoritesState = favoritesState,
            onlineFavoritesState = onlineFavoritesState,
            importState = importState,
            onPlaylistsIntent = onPlaylistsIntent,
            onOnlinePlaylistsIntent = onOnlinePlaylistsIntent,
            onFavoritesIntent = onFavoritesIntent,
            onOnlineFavoritesIntent = onOnlineFavoritesIntent,
            onPlayerIntent = onPlayerIntent,
            onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
            playerExpanded = playerExpanded,
            onTabSelected = onTabSelected,
            onTabChanged = onTabChanged,
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

        AppTab.Library -> Unit // Handled by the shared, saveable library entry above.

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
            showDuration = true,
            onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
            modifier = modifier,
        )

        AppTab.Tags -> Unit

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

// Owned above the responsive shell so orientation changes restore the same browser entry.
internal val LocalAutomotiveLibraryEntry =
    staticCompositionLocalOf<(@Composable (@Composable () -> Unit) -> Unit)?> { null }

@Composable
internal fun rememberAutomotiveLibraryEntry(): @Composable (@Composable () -> Unit) -> Unit {
    val stateHolder = rememberSaveableStateHolder()
    return remember(stateHolder) {
        movableContentOf<@Composable () -> Unit> { content ->
            stateHolder.SaveableStateProvider("library", content)
        }
    }
}

@Composable
internal fun AutomotiveLibraryContent(
    libraryState: LibraryState,
    onlineLibraryState: OnlineLibraryState,
    favoritesState: FavoritesState,
    importState: ImportState,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onOnlineLibraryIntent: (OnlineLibraryIntent) -> Unit,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    libraryNavigationTarget: LibraryNavigationTarget? = null,
    onLibraryNavigationHandled: () -> Unit = {},
    onOpenLibraryNavigationTarget: ((LibraryNavigationTarget) -> Unit)? = null,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val rootState = rememberSaveable { mutableStateOf(LibraryBrowserRootView.Tracks) }
    var rootClickRequestKey by rememberSaveable { mutableStateOf(0) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchAutoFocus by remember { mutableStateOf(false) }
    var inlineSearchFocused by remember { mutableStateOf(false) }
    val online = onlineLibraryState.sourceId != null
    val query = if (online) onlineLibraryState.query else libraryState.query
    val searchEnabled = !(online && rootState.value == LibraryBrowserRootView.Folders)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val presentation = currentPlayerPresentation()
    val updateQuery: (String) -> Unit = {
        if (online) onOnlineLibraryIntent(OnlineLibraryIntent.SearchChanged(it))
        else onLibraryIntent(LibraryIntent.SearchChanged(it))
    }
    fun exitSearch() {
        updateQuery("")
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        searchAutoFocus = false
        inlineSearchFocused = false
        searchExpanded = false
    }
    LaunchedEffect(presentation.isExpanded) {
        if (presentation.isExpanded) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            searchAutoFocus = false
        }
    }
    val sourceMenu = MobileLibraryHubSourceMenu(
        selectedSourceFilter = libraryState.selectedSourceFilter,
        sourceFilterOptions = libraryState.sourceFilterOptions,
        onlineSourceOptions = importState.onlineNavidromeSourceOptions(),
        selectedOnlineSourceId = onlineLibraryState.sourceId,
        onSourceFilterChanged = {
            onOnlineLibraryIntent(OnlineLibraryIntent.SelectSource(null))
            onLibraryIntent(LibraryIntent.SourceFilterChanged(it))
        },
        onOnlineSourceSelected = { onOnlineLibraryIntent(OnlineLibraryIntent.SelectSource(it)) },
    )
    LibraryTab(
        state = libraryState,
        favoritesState = favoritesState,
        importState = importState,
        onlineState = onlineLibraryState,
        onLibraryIntent = onLibraryIntent,
        onFavoritesIntent = onFavoritesIntent,
        onOnlineIntent = onOnlineLibraryIntent,
        onPlayerIntent = onPlayerIntent,
        showDuration = !compact,
        showSearchField = false,
        showInlineBatchOperationButton = false,
        navigationTarget = libraryNavigationTarget,
        onNavigationHandled = onLibraryNavigationHandled,
        onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
        rootState = rootState,
        rootClickRequestKey = rootClickRequestKey,
        rootPagerScrollEnabled = !searchExpanded && (!searchEnabled || (!inlineSearchFocused && query.isBlank())),
        header = { actions ->
            // Register after browser detail/selection handlers: back first exits search.
            PlatformBackHandler(enabled = searchExpanded || inlineSearchFocused || query.isNotBlank()) {
                exitSearch()
            }
            val batchAction = actions.onBatchSelection?.let { MobileLibraryHubBatchAction(it) }
            val sortMenu = if (actions.showTrackSort) MobileLibraryHubSortMenu(
                selectedTrackSortMode = libraryState.selectedTrackSortMode,
                onTrackSortChanged = { onLibraryIntent(LibraryIntent.TrackSortChanged(it)) },
            ) else null
            val onRootClick: (LibraryBrowserRootView) -> Unit = {
                rootState.value = it
                rootClickRequestKey += 1
            }
            val onSearchClick = {
                searchAutoFocus = true
                searchExpanded = true
            }
            BoxWithConstraints(Modifier.fillMaxWidth().testTag("automotive-library-header")) {
                val buttonSize = maxOf(48.dp, LocalMinimumInteractiveComponentSize.current)
                val actionWidth = buttonSize * (1 + (if (sortMenu != null) 1 else 0) + (if (batchAction != null) 1 else 0))
                val inlineSearchFits = maxWidth - 36.dp - 16.dp - actionWidth -
                    desktopLibrarySearchFieldWidthDp().dp >= 192.dp
                // Capture focus before removing the inline field; its disposal clears focus.
                val wasEditingInline = inlineSearchFocused
                LaunchedEffect(compact, inlineSearchFits, query) {
                    if (!searchExpanded && (compact || !inlineSearchFits) &&
                        (query.isNotBlank() || wasEditingInline) && searchEnabled
                    ) {
                        searchAutoFocus = wasEditingInline
                        searchExpanded = true
                    }
                }
                when {
                    searchExpanded -> MobileLibraryHubSearchBar(
                        query = query,
                        placeholder = mobileLibraryHubSearchPlaceholder(AppTab.Library),
                        autoFocus = searchAutoFocus,
                        onQueryChanged = updateQuery,
                        onBack = ::exitSearch,
                        onClearOrClose = { if (query.isBlank()) exitSearch() else updateQuery("") },
                    )
                    compact -> MobileLibraryHubTabStrip(
                        selectedPage = rootState.value.ordinal,
                        rootTabs = LibraryBrowserRootView.entries,
                        onRootTabClick = onRootClick,
                        searchEnabled = searchEnabled,
                        batchAction = batchAction,
                        sourceMenu = sourceMenu,
                        sortMenu = sortMenu,
                        onSearchClick = onSearchClick,
                        onTabClick = { _, _ -> },
                    )
                    else -> LandscapeLibraryTabStrip(
                        root = rootState.value,
                        onRootClick = onRootClick,
                        query = query,
                        onQueryChanged = updateQuery,
                        inlineSearch = inlineSearchFits,
                        searchEnabled = searchEnabled,
                        onSearchClick = onSearchClick,
                        onSearchFocusChanged = { inlineSearchFocused = it },
                        sourceMenu = sourceMenu,
                        sortMenu = sortMenu,
                        batchAction = batchAction,
                    )
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun LandscapeLibraryTabStrip(
    root: LibraryBrowserRootView,
    onRootClick: (LibraryBrowserRootView) -> Unit,
    query: String,
    onQueryChanged: (String) -> Unit,
    inlineSearch: Boolean,
    searchEnabled: Boolean,
    onSearchClick: () -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
    sourceMenu: MobileLibraryHubSourceMenu,
    sortMenu: MobileLibraryHubSortMenu?,
    batchAction: MobileLibraryHubBatchAction?,
) {
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 20.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).testTag("automotive-library-tabs").horizontalScroll(rememberScrollState())) {
            LibraryBrowserRootView.entries.forEach { tab ->
                MobileLibraryHubTabItem(
                    label = automotiveLibraryRootLabel(tab),
                    selected = tab == root,
                    onClick = { onRootClick(tab) },
                    compact = true,
                    modifier = Modifier.testTag("automotive-library-tab-${tab.name}"),
                )
            }
        }
        if (inlineSearch) {
            DesktopLibrarySearchField(
                query = query,
                onQueryChanged = onQueryChanged,
                placeholder = mobileLibraryHubSearchPlaceholder(AppTab.Library),
                containerColor = mainShellColors.cardBorder,
                enabled = searchEnabled,
                modifier = Modifier.testTag("automotive-library-inline-search")
                    .onFocusChanged { onSearchFocusChanged(it.isFocused) },
            )
        } else {
            IconButton(onClick = onSearchClick, enabled = searchEnabled) {
                Icon(Icons.Rounded.Search, contentDescription = uiString(Res.string.common_search))
            }
        }
        LibraryBrowserToolbarActions(
            sourceFilterOptions = sourceMenu.sourceFilterOptions,
            selectedSourceFilter = sourceMenu.selectedSourceFilter,
            onlineSourceOptions = sourceMenu.onlineSourceOptions,
            selectedOnlineSourceId = sourceMenu.selectedOnlineSourceId,
            sourceFilterMenuExpanded = sourceMenuExpanded,
            onSourceFilterMenuExpandedChange = { sourceMenuExpanded = it },
            onSourceFilterChanged = sourceMenu.onSourceFilterChanged,
            onOnlineSourceSelected = sourceMenu.onOnlineSourceSelected,
            showTrackSortMenu = sortMenu != null,
            selectedTrackSortMode = sortMenu?.selectedTrackSortMode ?: TrackSortMode.TITLE,
            trackSortMenuExpanded = sortMenuExpanded,
            onTrackSortMenuExpandedChange = { sortMenuExpanded = it },
            onTrackSortChanged = { sortMenu?.onTrackSortChanged(it) },
            actionButton = batchAction?.let { action ->
                {
                    IconButton(onClick = action.onClick) {
                        Icon(Icons.Rounded.Checklist, contentDescription = uiString(Res.string.library_batch_actions))
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MobileLibraryHubTab(
    selectedTab: AppTab,
    playlistsState: PlaylistsState,
    onlinePlaylistsState: OnlinePlaylistsState,
    favoritesState: FavoritesState,
    onlineFavoritesState: OnlineFavoritesState,
    importState: ImportState,
    onPlaylistsIntent: (PlaylistsIntent) -> Unit,
    onOnlinePlaylistsIntent: (OnlinePlaylistsIntent) -> Unit,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onOnlineFavoritesIntent: (OnlineFavoritesIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    playerExpanded: Boolean,
    onTabSelected: (AppTab) -> Unit,
    onTabChanged: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hubTabs = automotiveCollectionTabs
    val initialPage = hubTabs.indexOf(selectedTab).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { hubTabs.size }
    var pagerAlignmentRequest by remember { mutableIntStateOf(0) }
    val alignmentRequest = pagerAlignmentRequest
    var pagerAligning by remember(pagerState, selectedTab, hubTabs, alignmentRequest) { mutableStateOf(true) }
    val latestSelectedTab by rememberUpdatedState(selectedTab)
    val latestHubTabs by rememberUpdatedState(hubTabs)
    val latestOnTabChanged by rememberUpdatedState(onTabChanged)
    val foreground by rememberUpdatedState(LocalMusicDetailIsForeground.current)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isSearchMode by rememberSaveable { mutableStateOf(false) }
    var searchBarAutoFocus by remember { mutableStateOf(false) }
    var playlistSearchQuery by rememberSaveable { mutableStateOf("") }
    var favoritesRefreshHoldActive by rememberSaveable { mutableStateOf(false) }
    var favoritesRefreshHoldKey by rememberSaveable { mutableStateOf(0) }
    var playlistsRefreshHoldActive by rememberSaveable { mutableStateOf(false) }
    var playlistsRefreshHoldKey by rememberSaveable { mutableStateOf(0) }
    var favoritesBatchSelectionRequestKey by rememberSaveable { mutableStateOf(0) }
    var playlistsBatchSelectionRequestKey by rememberSaveable { mutableStateOf(0) }
    val favoritesPullRefreshState = rememberPullToRefreshState()
    val playlistsPullRefreshState = rememberPullToRefreshState()
    val activeSearchTab = hubTabs.getOrElse(pagerState.currentPage) { hubTabs.first() }
    val activeSearchQuery = when (activeSearchTab) {
        AppTab.Favorites -> onlineFavoritesState.sourceId?.let { onlineFavoritesState.query } ?: favoritesState.query
        AppTab.Playlists -> playlistSearchQuery
        else -> ""
    }
    val selectedTabSearchQuery = when (selectedTab) {
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
        AppTab.Favorites -> onlineFavoritesState.sourceId != null
        AppTab.Playlists -> onlinePlaylistsState.sourceId != null
        else -> false
    }
    val batchOperationAvailable = !activeTabOnline && mobileLibraryHubCanBatchOperate(
        tab = activeSearchTab,
        libraryVisibleTrackCount = 0,
        favoritesVisibleTrackCount = favoritesState.filteredTracks.size,
        playlistDetailVisibleTrackCount = playlistDetailVisibleTrackCount,
        selectedPlaylistId = playlistsState.selectedPlaylistId,
    )

    fun updateActiveSearchQuery(query: String) {
        when (activeSearchTab) {
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
            AppTab.Favorites -> favoritesBatchSelectionRequestKey += 1
            AppTab.Playlists -> playlistsBatchSelectionRequestKey += 1
            else -> Unit
        }
    }

    LaunchedEffect(pagerState, selectedTab, hubTabs, alignmentRequest) {
        pagerAligning = true
        try {
            val targetPage = hubTabs.indexOf(selectedTab).coerceAtLeast(0)
            if (pagerState.currentPage != targetPage || pagerState.isScrollInProgress) {
                pagerState.scrollToPage(targetPage)
            }
        } finally {
            pagerAligning = false
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
    // Restoring or aligning the pager must never be treated as a navigation click.
    LaunchedEffect(pagerState, selectedTab, hubTabs, alignmentRequest) {
        snapshotFlow {
            if (!foreground || pagerAligning || pagerState.isScrollInProgress) null
            else pagerState.settledPage
        }
            .collect { page ->
                val tab = page?.let { latestHubTabs.getOrNull(it) }
                if (foreground && !pagerAligning && !pagerState.isScrollInProgress &&
                    page == pagerState.settledPage && tab != null && tab != latestSelectedTab
                ) {
                    latestOnTabChanged(tab)
                }
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
                selectedPage = pagerState.currentPage,
                tabs = hubTabs,
                batchAction = if (batchOperationAvailable) {
                    MobileLibraryHubBatchAction(onClick = ::requestBatchSelection)
                } else {
                    null
                },
                sourceMenu = when (activeSearchTab) {
                    AppTab.Favorites -> MobileLibraryHubSourceMenu(
                        selectedSourceFilter = favoritesState.selectedSourceFilter,
                        sourceFilterOptions = favoritesState.sourceFilterOptions,
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
                        sourceFilterOptions = playlistsState.sourceFilterOptions,
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
                onTabClick = { _, tab ->
                    pagerAligning = true
                    onTabSelected(tab)
                    pagerAlignmentRequest++
                },
            )
        }
        val pageContent: @Composable (Int) -> Unit = { page ->
            when (hubTabs.getOrElse(page) { hubTabs.first() }) {
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
                            onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
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
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = !isSearchMode,
            key = { page -> hubTabs[page].name },
        ) { page -> pageContent(page) }
    }
}

private data class MobileLibraryHubSourceMenu(
    val selectedSourceFilter: LibrarySourceFilter,
    val sourceFilterOptions: List<LibrarySourceFilterOption>,
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
            modifier = Modifier.weight(1f).then(if (rootTabs.isNotEmpty()) Modifier.testTag("automotive-library-tabs") else Modifier)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(if (rootTabs.isEmpty()) 8.dp else 0.dp),
        ) {
            if (rootTabs.isNotEmpty()) rootTabs.forEachIndexed { index, root ->
                MobileLibraryHubTabItem(
                    label = automotiveLibraryRootLabel(root),
                    modifier = Modifier.testTag("automotive-library-tab-${root.name}"),
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
                        contentDescription = uiString(Res.string.common_more_actions),
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
                contentDescription = uiString(Res.string.common_search),
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
                        text = { Text(uiString(Res.string.library_batch_actions)) },
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
                        text = { Text(uiString(Res.string.sources_title)) },
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
                        text = { Text(uiString(Res.string.common_sort)) },
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
                    text = { Text(uiString(Res.string.sources_title)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                        )
                    },
                    onClick = { onLayerChanged(MobileLibraryHubMenuLayer.Root) },
                )
                sourceMenu?.sourceFilterOptions.orEmpty().forEach { option ->
                    val filter = option.filter
                    val isSelected = sourceMenu?.selectedOnlineSourceId == null &&
                        filter == sourceMenu?.selectedSourceFilter
                    DropdownMenuItem(
                        text = { Text(option.label.displayText(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                        text = { Text(option.label.displayText(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                    text = { Text(uiString(Res.string.common_sort)) },
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
                contentDescription = uiString(Res.string.navigation_exit_search),
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
                contentDescription = if (query.isBlank()) uiString(Res.string.navigation_close_search) else uiString(Res.string.search_clear_query),
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
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
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
