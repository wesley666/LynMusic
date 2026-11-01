package top.iwesley.lyn.music.mobile.ui

import top.iwesley.lyn.music.*

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.uiPlural
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.UiText

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

internal fun phoneNavigationGroup(tab: AppTab): AppTab =
    if (tab in phoneCollectionTabs) AppTab.Favorites else tab

internal fun mobileMoreNavigationTabs(): List<AppTab> = listOf(AppTab.Tags, AppTab.Sources, AppTab.Settings)

internal fun tabletNavigationTabs(): List<AppTab> = listOf(
    AppTab.My, AppTab.Library, AppTab.Favorites, AppTab.Playlists,
    AppTab.Tags, AppTab.Sources, AppTab.Settings,
)

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
    onTabChanged: (AppTab) -> Unit = onTabSelected,
) {
    val presentation = currentPlayerPresentation()
    val navigationTabs = primaryNavigationTabs(phoneNavigation)
    var lastCollectionTab by rememberSaveable { mutableStateOf(AppTab.Favorites) }
    val contentStateHolder = rememberSaveableStateHolder()
    val dockBackdrop = if (phoneNavigation) rememberLayerBackdrop() else null
    val miniPlayerGlass = dockBackdrop?.let { miniPlayerGlassStyle(it) }
    val layoutDirection = LocalLayoutDirection.current
    val contentBehindDock = phoneNavigation &&
        (isMobileLibraryHubTab(selectedTab) || selectedTab == AppTab.My)
    LaunchedEffect(selectedTab) {
        if (selectedTab in phoneCollectionTabs) lastCollectionTab = selectedTab
    }
    val shellColors = mainShellColors
    val mobileNavIconSize = 29.dp
    val moreTabs = remember(platform) { mobileMoreNavigationTabs() }
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
                                else -> navigationTabs.indexOf(phoneNavigationGroup(selectedTab)).coerceAtLeast(0)
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
                        val label = if (phoneNavigation && tab == AppTab.Favorites) uiString(Res.string.favorites_collection_title) else mobileLibraryHubTabLabel(tab)
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
                    key = if (phoneNavigation) phoneNavigationGroup(selectedTab).name else "legacy",
                ) {
                    MobilePageContent(
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
internal fun TabletShell(
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
                TabletNav(
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
            MobilePageContent(
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
private fun TabletNav(
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
        tabletNavigationTabs().forEach { tab ->
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
    val topPadding = 56.dp
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
