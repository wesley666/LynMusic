package top.iwesley.lyn.music.mobile.ui

import top.iwesley.lyn.music.*

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.UiText

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.AppTab
import top.iwesley.lyn.music.core.model.PlatformDescriptor
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
import top.iwesley.lyn.music.feature.playlists.PlaylistsIntent
import top.iwesley.lyn.music.feature.playlists.PlaylistsState
import top.iwesley.lyn.music.feature.settings.SettingsIntent
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.feature.tags.MusicTagsEffect
import top.iwesley.lyn.music.feature.tags.MusicTagsIntent
import top.iwesley.lyn.music.feature.tags.MusicTagsState
import top.iwesley.lyn.music.ui.mainShellColors

@Composable
internal fun MobilePageContent(
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
    if (mobileLibraryHub && isMobileLibraryHubTab(selectedTab)) {
        MobileLibraryHubTab(
            selectedTab = selectedTab,
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
            importState = importState,
            onlineState = onlineLibraryState,
            onLibraryIntent = onLibraryIntent,
            onFavoritesIntent = onFavoritesIntent,
            onOnlineIntent = onOnlineLibraryIntent,
            onPlayerIntent = onPlayerIntent,
            showDuration = false,
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
            showDuration = false,
            onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
            modifier = modifier,
        )

        AppTab.Tags -> MusicTagsTab(
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

internal fun libraryHubTabs(selectedTab: AppTab, phoneNavigation: Boolean): List<AppTab> = when {
    !phoneNavigation -> mobileLibraryHubTabs
    selectedTab == AppTab.Library -> listOf(AppTab.Library)
    else -> phoneCollectionTabs
}

internal fun phoneLibraryRootLabelText(root: LibraryBrowserRootView): UiText = when (root) {
    LibraryBrowserRootView.Tracks -> uiText(Res.string.library_tracks_title)
    LibraryBrowserRootView.Albums -> uiText(Res.string.library_albums_title)
    LibraryBrowserRootView.Artists -> uiText(Res.string.library_artists_title)
    LibraryBrowserRootView.Folders -> uiText(Res.string.library_folders_title)
}

@Composable
internal fun phoneLibraryRootLabel(root: LibraryBrowserRootView): String = phoneLibraryRootLabelText(root).displayText()

internal fun mobileLibraryHubSupportsTrackSort(tab: AppTab): Boolean {
    return tab == AppTab.Library || tab == AppTab.Favorites
}

internal fun mobileLibraryHubPageForTab(tab: AppTab): Int {
    return mobileLibraryHubTabs.indexOf(tab).takeIf { it >= 0 } ?: 0
}

internal fun mobileLibraryHubTabForPage(page: Int): AppTab {
    return mobileLibraryHubTabs.getOrElse(page) { AppTab.Library }
}

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
                        sourceFilterOptions = libraryState.sourceFilterOptions,
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
