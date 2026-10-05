package top.iwesley.lyn.music

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.UiText

import top.iwesley.lyn.music.core.model.plus

import top.iwesley.lyn.music.core.model.reasonUiText
import top.iwesley.lyn.music.core.model.uiPlural
import top.iwesley.lyn.music.core.model.formatSambaEndpoint
import top.iwesley.lyn.music.core.model.uiText

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecentActors
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.ImportScanPhase
import top.iwesley.lyn.music.core.model.ImportScanProgress
import top.iwesley.lyn.music.core.model.ImportScanSummary
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.core.model.FnMusicConnectionMode
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.LocalFolderPickerMode
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.SubsonicAuthMode
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.feature.favorites.FavoritesIntent
import top.iwesley.lyn.music.feature.favorites.FavoritesState
import top.iwesley.lyn.music.feature.importing.ImportIntent
import top.iwesley.lyn.music.feature.importing.ImportScanOperation
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.importing.RemoteFolderRow
import top.iwesley.lyn.music.feature.importing.RemoteFolderTreeState
import top.iwesley.lyn.music.feature.importing.PendingLargeNavidromeAction
import top.iwesley.lyn.music.feature.library.LibraryAlbumUiItem
import top.iwesley.lyn.music.feature.library.LibraryArtistUiItem
import top.iwesley.lyn.music.feature.library.LibraryBrowserActions
import top.iwesley.lyn.music.feature.library.LibraryBrowserCount
import top.iwesley.lyn.music.feature.library.LibraryIntent
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.LibraryState
import top.iwesley.lyn.music.feature.library.LibraryTrackUiItem
import top.iwesley.lyn.music.feature.library.LibraryBrowserUiState
import top.iwesley.lyn.music.feature.library.TrackSortMode
import top.iwesley.lyn.music.feature.library.deriveVisibleAlbums
import top.iwesley.lyn.music.feature.library.libraryAlbumId
import top.iwesley.lyn.music.feature.library.libraryArtistId
import top.iwesley.lyn.music.feature.library.toBrowserUiState
import top.iwesley.lyn.music.feature.offline.OfflineDownloadIntent
import top.iwesley.lyn.music.feature.offline.batchDownloadInsufficientSpaceMessage
import top.iwesley.lyn.music.feature.offline.batchDownloadSizeEstimateLabel
import top.iwesley.lyn.music.feature.offline.estimateBatchDownloadSize
import top.iwesley.lyn.music.feature.online.OnlineFavoritesIntent
import top.iwesley.lyn.music.feature.online.OnlineFavoritesState
import top.iwesley.lyn.music.feature.online.OnlineLibraryIntent
import top.iwesley.lyn.music.feature.online.OnlineLibraryState
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.platform.PlatformBackHandler
import top.iwesley.lyn.music.ui.mainShellColors
import kotlin.math.roundToInt

internal enum class LibraryTabMessageDismissTarget {
    OnlineLibrary,
    Favorites,
}

internal fun <T> libraryTabBrowserMessage(
    isOnlineMode: Boolean,
    onlineErrorMessage: T?,
    favoritesMessage: T?,
): T? {
    return if (isOnlineMode) onlineErrorMessage else favoritesMessage
}

internal fun libraryTabMessageDismissTarget(isOnlineMode: Boolean): LibraryTabMessageDismissTarget {
    return if (isOnlineMode) {
        LibraryTabMessageDismissTarget.OnlineLibrary
    } else {
        LibraryTabMessageDismissTarget.Favorites
    }
}

@Composable
internal fun LibraryTab(
    state: LibraryState,
    favoritesState: FavoritesState,
    importState: ImportState,
    onlineState: OnlineLibraryState,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onOnlineIntent: (OnlineLibraryIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    showFavoriteButton: Boolean = true,
    showDuration: Boolean = true,
    showSearchField: Boolean = true,
    navigationTarget: LibraryNavigationTarget? = null,
    onNavigationHandled: () -> Unit = {},
    onOpenLibraryNavigationTarget: ((LibraryNavigationTarget) -> Unit)? = null,
    batchSelectionRequestKey: Int = 0,
    showInlineBatchOperationButton: Boolean = true,
    rootSelectorStyle: LibraryRootSelectorStyle = LibraryRootSelectorStyle.Default,
    phoneRootState: MutableState<LibraryBrowserRootView>? = null,
    phoneRootClickRequestKey: Int = 0,
    rootPagerScrollEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val onlineSourceOptions = remember(importState.sources) {
        importState.onlineNavidromeSourceOptions()
    }
    val isOnlineMode = onlineState.sourceId != null
    val browserMessage = libraryTabBrowserMessage(
        isOnlineMode = isOnlineMode,
        onlineErrorMessage = onlineState.errorMessage,
        favoritesMessage = favoritesState.message,
    )
    val browserState = if (isOnlineMode) {
        onlineState.toBrowserUiState(message = browserMessage)
    } else {
        state.toBrowserUiState(
            favoriteTrackIds = favoritesState.favoriteTrackIds,
            message = browserMessage,
        )
    }
    val browserActions = LibraryBrowserActions(
        onSearchChanged = {
            if (isOnlineMode) {
                onOnlineIntent(OnlineLibraryIntent.SearchChanged(it))
            } else {
                onLibraryIntent(LibraryIntent.SearchChanged(it))
            }
        },
        onSourceFilterChanged = {
            onOnlineIntent(OnlineLibraryIntent.SelectSource(sourceId = null))
            onLibraryIntent(LibraryIntent.SourceFilterChanged(it))
        },
        onOnlineSourceSelected = { sourceId -> onOnlineIntent(OnlineLibraryIntent.SelectSource(sourceId)) },
        onTrackSortChanged = { onLibraryIntent(LibraryIntent.TrackSortChanged(it)) },
        onToggleFavorite = { onFavoritesIntent(FavoritesIntent.ToggleFavorite(it)) },
        onDismissMessage = {
            when (libraryTabMessageDismissTarget(isOnlineMode)) {
                LibraryTabMessageDismissTarget.OnlineLibrary -> onOnlineIntent(OnlineLibraryIntent.ClearError)
                LibraryTabMessageDismissTarget.Favorites -> onFavoritesIntent(FavoritesIntent.ClearMessage)
            }
        },
        onLoadMoreTracks = { onOnlineIntent(OnlineLibraryIntent.LoadMoreTracks) },
        onLoadMoreAlbums = { onOnlineIntent(OnlineLibraryIntent.LoadMoreAlbums) },
        onLoadMoreArtists = { onOnlineIntent(OnlineLibraryIntent.LoadMoreArtists) },
        onPrepareOnlineAlbumNavigation = { sourceId, albumId, albumTitle, artistName, artworkLocator ->
            onOnlineIntent(
                OnlineLibraryIntent.PrepareAlbumNavigation(
                    sourceId = sourceId,
                    albumId = albumId,
                    albumTitle = albumTitle,
                    artistName = artistName,
                    artworkLocator = artworkLocator,
                ),
            )
        },
        onPrepareOnlineArtistNavigation = { sourceId, artistId, artistName ->
            onOnlineIntent(
                OnlineLibraryIntent.PrepareArtistNavigation(
                    sourceId = sourceId,
                    artistId = artistId,
                    artistName = artistName,
                ),
            )
        },
        onLoadAlbumTracks = { onOnlineIntent(OnlineLibraryIntent.LoadAlbumTracks(it)) },
        onLoadArtistAlbums = { onOnlineIntent(OnlineLibraryIntent.LoadArtistAlbums(it)) },
        onAlbumClick = { album ->
            if (isOnlineMode) {
                onOnlineIntent(OnlineLibraryIntent.LoadAlbumTracks(album.id))
            }
        },
        onArtistClick = { artist ->
            if (isOnlineMode) {
                onOnlineIntent(OnlineLibraryIntent.LoadArtistAlbums(artist.id))
            }
        },
        onPlayTracks = { tracks, index -> onPlayerIntent(PlayerIntent.PlayTracks(tracks, index)) },
    )
    LibraryBrowserTab(
        state = browserState,
        actions = browserActions,
        onlineSourceOptions = onlineSourceOptions,
        strings = LibraryBrowserStrings(
            searchLabel = uiString(Res.string.library_search_with_folders_placeholder),
            sectionTitle = "",
            sectionSubtitle = "",
            songsIcon = Icons.Rounded.LibraryMusic,
            emptyCollectionTitle = uiString(Res.string.library_empty_title),
            emptyCollectionBody = uiString(Res.string.library_empty_import_hint),
            emptyFilterBody = uiString(Res.string.library_source_filter_empty_hint),
            emptySearchBody = uiString(Res.string.library_search_filter_empty_hint),
            trackLabel = uiString(Res.string.library_tracks_title),
            albumLabel = uiString(Res.string.library_albums_title),
            artistLabel = uiString(Res.string.library_artists_title),
            folderLabel = uiString(Res.string.library_folders_title),
        ),
        showFavoriteButton = showFavoriteButton && !isOnlineMode,
        showDuration = showDuration,
        showSearchField = showSearchField,
        showTrackSortActionButton = showSearchField && !isOnlineMode,
        showFolderBrowser = !isOnlineMode,
        rootSelectorStyle = rootSelectorStyle,
        phoneRootState = phoneRootState,
        phoneRootClickRequestKey = phoneRootClickRequestKey,
        rootPagerScrollEnabled = rootPagerScrollEnabled,
        navigationTarget = navigationTarget,
        onNavigationHandled = onNavigationHandled,
        onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
        batchSelectionRequestKey = batchSelectionRequestKey,
        showInlineBatchOperationButton = showInlineBatchOperationButton,
        modifier = modifier,
    )
}

@Composable
internal fun FavoritesTab(
    state: FavoritesState,
    importState: ImportState,
    onlineState: OnlineFavoritesState,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onOnlineIntent: (OnlineFavoritesIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    showFavoriteButton: Boolean = true,
    showDuration: Boolean = true,
    showSearchField: Boolean = true,
    showRefreshActionButton: Boolean = true,
    onOpenLibraryNavigationTarget: ((LibraryNavigationTarget) -> Unit)? = null,
    batchSelectionRequestKey: Int = 0,
    showInlineBatchOperationButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val onlineSourceOptions = remember(importState.sources) {
        importState.onlineNavidromeSourceOptions()
    }
    val isOnlineMode = onlineState.sourceId != null
    val browserState = if (isOnlineMode) {
        onlineState.toBrowserUiState(message = onlineState.errorMessage ?: onlineState.message ?: state.message)
    } else {
        state.toBrowserUiState(message = state.message)
    }
    val browserActions = LibraryBrowserActions(
        onSearchChanged = {
            if (isOnlineMode) {
                onOnlineIntent(OnlineFavoritesIntent.SearchChanged(it))
            } else {
                onFavoritesIntent(FavoritesIntent.SearchChanged(it))
            }
        },
        onSourceFilterChanged = {
            onOnlineIntent(OnlineFavoritesIntent.SelectSource(sourceId = null))
            onFavoritesIntent(FavoritesIntent.SourceFilterChanged(it))
        },
        onOnlineSourceSelected = { sourceId -> onOnlineIntent(OnlineFavoritesIntent.SelectSource(sourceId)) },
        onTrackSortChanged = { onFavoritesIntent(FavoritesIntent.TrackSortChanged(it)) },
        onToggleFavorite = {
            if (isOnlineMode) {
                val sourceId = onlineState.sourceId
                if (sourceId != null) {
                    onOnlineIntent(
                        OnlineFavoritesIntent.SetFavorite(
                            sourceId = sourceId,
                            track = it,
                            favorite = false,
                        )
                    )
                }
            } else {
                onFavoritesIntent(FavoritesIntent.ToggleFavorite(it))
            }
        },
        onDismissMessage = {
            onOnlineIntent(OnlineFavoritesIntent.ClearMessage)
            onFavoritesIntent(FavoritesIntent.ClearMessage)
        },
        onLoadMoreTracks = { onOnlineIntent(OnlineFavoritesIntent.LoadMore) },
        onPlayTracks = { tracks, index -> onPlayerIntent(PlayerIntent.PlayTracks(tracks, index)) },
    )
    LibraryBrowserTab(
        state = browserState,
        actions = browserActions,
        onlineSourceOptions = onlineSourceOptions,
        strings = LibraryBrowserStrings(
            searchLabel = uiString(Res.string.library_search_placeholder),
            sectionTitle = "",
            sectionSubtitle = "",
            songsIcon = Icons.Rounded.Favorite,
            emptyCollectionTitle = uiString(Res.string.favorites_empty_title),
            emptyCollectionBody = uiString(Res.string.favorites_empty_hint),
            emptyFilterBody = uiString(Res.string.favorites_source_filter_empty_hint),
            emptySearchBody = uiString(Res.string.library_search_filter_empty_hint),
            trackLabel = uiString(Res.string.favorites_tracks_title),
            albumLabel = uiString(Res.string.favorites_albums_title),
            artistLabel = uiString(Res.string.favorites_artists_title),
            folderLabel = uiString(Res.string.favorites_folders_title),
        ),
        actionButton = if (showRefreshActionButton && (state.canRefreshRemote || isOnlineMode)) {
            {
                IconButton(
                    onClick = {
                        if (isOnlineMode) {
                            onOnlineIntent(OnlineFavoritesIntent.Refresh)
                        } else {
                            onFavoritesIntent(FavoritesIntent.Refresh)
                        }
                    },
                    enabled = if (isOnlineMode) !onlineState.isLoading else !state.isRefreshing,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = if (state.isRefreshing || onlineState.isLoading) uiString(Res.string.common_refreshing) else uiString(Res.string.common_refresh),
                    )
                }
            }
        } else {
            null
        },
        showFavoriteButton = showFavoriteButton,
        showDuration = showDuration,
        showSearchField = showSearchField,
        showTrackSortActionButton = showSearchField && !isOnlineMode,
        onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
        batchSelectionRequestKey = batchSelectionRequestKey,
        showInlineBatchOperationButton = showInlineBatchOperationButton && !isOnlineMode,
        modifier = modifier,
    )
}

internal data class OnlineSourceOption(
    val sourceId: String,
    val label: UiText,
)

internal fun ImportState.onlineNavidromeSourceOptions(): List<OnlineSourceOption> {
    return sources
        .asSequence()
        .map { it.source }
        .filter {
            it.enabled &&
                it.type == ImportSourceType.NAVIDROME &&
                it.indexMode == ImportSourceIndexMode.ONLINE
        }
        .map { source ->
            OnlineSourceOption(
                sourceId = source.id,
                label = UiText.Raw(source.label.trim().ifBlank { source.id }) + uiText(Res.string.source_online_badge),
            )
        }
        .toList()
}

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
    val folderLabel: String,
)

internal enum class LibraryBrowserRootView {
    Tracks,
    Albums,
    Artists,
    Folders,
}

internal enum class LibraryRootSelectorStyle {
    Default,
    CompactHero,
}

internal data class LibraryRootSelectorItem(
    val rootView: LibraryBrowserRootView,
    val title: UiText,
    val value: String,
)

internal data class LibraryRootSelectorModel(
    val style: LibraryRootSelectorStyle,
    val defaultItems: List<LibraryRootSelectorItem>,
    val heroItem: LibraryRootSelectorItem?,
    val secondaryItems: List<LibraryRootSelectorItem>,
    val playAllEnabled: Boolean,
)

internal fun buildLibraryRootSelectorModel(
    style: LibraryRootSelectorStyle,
    trackCount: LibraryBrowserCount,
    albumCount: LibraryBrowserCount,
    artistCount: LibraryBrowserCount,
    folderCount: Int,
    showFolderBrowser: Boolean,
    playAllEnabled: Boolean = trackCount.loaded > 0,
): LibraryRootSelectorModel {
    val trackItem = LibraryRootSelectorItem(
        rootView = LibraryBrowserRootView.Tracks,
        title = uiText(Res.string.library_tracks_title),
        value = trackCount.displayValue(),
    )
    val albumItem = LibraryRootSelectorItem(
        rootView = LibraryBrowserRootView.Albums,
        title = uiText(Res.string.library_albums_title),
        value = albumCount.displayValue(),
    )
    val artistItem = LibraryRootSelectorItem(
        rootView = LibraryBrowserRootView.Artists,
        title = uiText(Res.string.library_artists_title),
        value = artistCount.displayValue(),
    )
    val folderItem = LibraryRootSelectorItem(
        rootView = LibraryBrowserRootView.Folders,
        title = uiText(Res.string.library_folders_title),
        value = folderCount.coerceAtLeast(0).toString(),
    )
    val defaultItems = buildList {
        add(trackItem)
        add(albumItem)
        add(artistItem)
        if (showFolderBrowser) add(folderItem)
    }
    return when (style) {
        LibraryRootSelectorStyle.Default -> LibraryRootSelectorModel(
            style = style,
            defaultItems = defaultItems,
            heroItem = null,
            secondaryItems = emptyList(),
            playAllEnabled = false,
        )

        LibraryRootSelectorStyle.CompactHero -> LibraryRootSelectorModel(
            style = style,
            defaultItems = emptyList(),
            heroItem = trackItem.copy(title = uiText(Res.string.library_all_tracks_label)),
            secondaryItems = buildList {
                add(albumItem)
                add(artistItem)
                if (showFolderBrowser) add(folderItem)
            },
            playAllEnabled = playAllEnabled,
        )
    }
}

internal data class LibraryFolderKey(
    val sourceId: String,
    val path: String,
) {
    val stableId: String
        get() = "${sourceId.length}:$sourceId:$path"
}

internal data class LibraryFolderNode(
    val key: LibraryFolderKey,
    val name: String,
    val sourceLabel: String,
    val sourceId: String,
    val path: String,
    val trackCount: Int,
    val directTrackCount: Int,
    val childFolderCount: Int,
)

internal data class LibraryFolderTree(
    val rootFolders: List<LibraryFolderNode>,
    val nodesByKey: Map<LibraryFolderKey, LibraryFolderNode>,
    val childFoldersByKey: Map<LibraryFolderKey, List<LibraryFolderNode>>,
    val directTracksByKey: Map<LibraryFolderKey, List<Track>>,
) {
    val folderCount: Int = nodesByKey.size
}

internal fun deriveLibraryFolderTree(
    tracks: List<Track>,
    sourceLabelsById: Map<String, String>,
): LibraryFolderTree {
    val statsByKey = linkedMapOf<LibraryFolderKey, MutableLibraryFolderStats>()
    val childPathsByKey = linkedMapOf<LibraryFolderKey, MutableSet<String>>()
    val directTracksByKey = linkedMapOf<LibraryFolderKey, MutableList<Track>>()
    tracks.forEach { track ->
        val sourceId = track.sourceId
        val rootKey = LibraryFolderKey(sourceId = sourceId, path = "")
        val pathSegments = normalizedLibraryFolderPathSegments(track.relativePath)
        val parentSegments = pathSegments.dropLast(1)
        statsByKey.getOrPut(rootKey) { MutableLibraryFolderStats() }.trackCount += 1
        if (parentSegments.isEmpty()) {
            statsByKey.getOrPut(rootKey) { MutableLibraryFolderStats() }.directTrackCount += 1
            directTracksByKey.getOrPut(rootKey) { mutableListOf() }.add(track)
        } else {
            childPathsByKey.getOrPut(rootKey) { linkedSetOf() }.add(parentSegments.first())
            parentSegments.indices.forEach { index ->
                val folderPath = parentSegments.take(index + 1).joinToString("/")
                val folderKey = LibraryFolderKey(sourceId = sourceId, path = folderPath)
                statsByKey.getOrPut(folderKey) { MutableLibraryFolderStats() }.trackCount += 1
                if (index == parentSegments.lastIndex) {
                    statsByKey.getOrPut(folderKey) { MutableLibraryFolderStats() }.directTrackCount += 1
                    directTracksByKey.getOrPut(folderKey) { mutableListOf() }.add(track)
                } else {
                    val childPath = parentSegments.take(index + 2).joinToString("/")
                    childPathsByKey.getOrPut(folderKey) { linkedSetOf() }.add(childPath)
                }
            }
        }
    }
    val nodesByKey = statsByKey.mapValues { (key, stats) ->
        val sourceLabel = sourceLabelsById[key.sourceId]?.trim()?.takeIf { it.isNotBlank() } ?: key.sourceId
        LibraryFolderNode(
            key = key,
            name = if (key.path.isBlank()) sourceLabel else key.path.substringAfterLast('/'),
            sourceLabel = sourceLabel,
            sourceId = key.sourceId,
            path = key.path,
            trackCount = stats.trackCount,
            directTrackCount = stats.directTrackCount,
            childFolderCount = childPathsByKey[key]?.size ?: 0,
        )
    }
    val childFoldersByKey = childPathsByKey.mapValues { (key, childPaths) ->
        childPaths.mapNotNull { childPath ->
            nodesByKey[LibraryFolderKey(sourceId = key.sourceId, path = childPath)]
        }.sortedWith(LIBRARY_FOLDER_NODE_COMPARATOR)
    }
    return LibraryFolderTree(
        rootFolders = nodesByKey.values
            .filter { it.path.isBlank() }
            .sortedWith(LIBRARY_FOLDER_NODE_COMPARATOR),
        nodesByKey = nodesByKey,
        childFoldersByKey = childFoldersByKey,
        directTracksByKey = directTracksByKey.mapValues { (_, tracks) ->
            tracks.sortedWith(LIBRARY_FOLDER_TRACK_COMPARATOR)
        },
    )
}

internal fun normalizedLibraryFolderPathSegments(relativePath: String): List<String> {
    return relativePath
        .replace('\\', '/')
        .split('/')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

internal fun libraryFolderSummaryLabelText(folder: LibraryFolderNode): UiText {
    return if (folder.childFolderCount > 0) {
        uiText(Res.string.library_folder_track_and_subfolder_counts,
            uiPlural(Res.plurals.common_track_count, folder.trackCount, folder.trackCount),
            uiPlural(Res.plurals.ui_subfolders_count, folder.childFolderCount, folder.childFolderCount),
        )
    } else {
        uiPlural(Res.plurals.common_track_count, (folder.trackCount).toInt(), folder.trackCount)
    }
}

@Composable
internal fun libraryFolderSummaryLabel(folder: LibraryFolderNode): String = libraryFolderSummaryLabelText(folder).displayText()

internal fun libraryFolderDetailSubtitleText(folder: LibraryFolderNode): UiText {
    return if (folder.path.isBlank()) {
        uiText(Res.string.library_source_root_folder)
    } else {
        UiText.Raw(folder.path)
    }
}

@Composable
internal fun libraryFolderDetailSubtitle(folder: LibraryFolderNode): String = libraryFolderDetailSubtitleText(folder).displayText()

private data class LibraryFolderDetailScrollPosition(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
)

private val LibraryFolderScrollPositionsSaver = listSaver<MutableMap<String, LibraryFolderDetailScrollPosition>, Any>(
    save = { positions ->
        positions.entries.flatMap { (key, position) ->
            listOf(key, position.firstVisibleItemIndex, position.firstVisibleItemScrollOffset)
        }
    },
    restore = { values ->
        values.chunked(3).associate { entry ->
            (entry[0] as String) to LibraryFolderDetailScrollPosition(entry[1] as Int, entry[2] as Int)
        }.toMutableMap()
    },
)

private data class MutableLibraryFolderStats(
    var trackCount: Int = 0,
    var directTrackCount: Int = 0,
)

private val LIBRARY_FOLDER_NODE_COMPARATOR = compareBy<LibraryFolderNode> { it.name.lowercase() }
    .thenBy { it.sourceLabel.lowercase() }
    .thenBy { it.sourceId }
    .thenBy { it.path.lowercase() }

private val LIBRARY_FOLDER_TRACK_COMPARATOR = compareBy<Track> {
    normalizedLibraryFolderPathSegments(it.relativePath).lastOrNull().orEmpty().lowercase()
}.thenBy { it.title.lowercase() }.thenBy { it.id }

internal fun resolveTrackRowLibraryNavigationTargets(
    track: Track,
    showDuration: Boolean,
    metadataNavigationEnabled: Boolean,
    preferredSourceFilter: LibrarySourceFilter = LibrarySourceFilter.ALL,
): PlaybackLibraryNavigationTargets {
    return if (showDuration && metadataNavigationEnabled) {
        deriveTrackLibraryNavigationTargets(
            track = track,
            preferredSourceFilter = preferredSourceFilter,
        )
    } else {
        PlaybackLibraryNavigationTargets(albumTarget = null, artistTarget = null)
    }
}

private fun prepareOnlineNavigationTarget(
    target: LibraryNavigationTarget,
    actions: LibraryBrowserActions,
) {
    when (target) {
        is LibraryNavigationTarget.OnlineAlbum -> {
            actions.onPrepareOnlineAlbumNavigation(
                target.sourceId,
                target.albumId,
                target.albumTitle,
                target.artistName,
                target.artworkLocator,
            )
        }

        is LibraryNavigationTarget.OnlineArtist -> {
            actions.onPrepareOnlineArtistNavigation(
                target.sourceId,
                target.artistId,
                target.artistName,
            )
        }

        is LibraryNavigationTarget.Album,
        is LibraryNavigationTarget.Artist,
        -> Unit
    }
}

@Composable
private fun LibraryBrowserTab(
    state: LibraryBrowserUiState,
    actions: LibraryBrowserActions,
    onlineSourceOptions: List<OnlineSourceOption> = emptyList(),
    strings: LibraryBrowserStrings,
    showFavoriteButton: Boolean = true,
    showDuration: Boolean = true,
    showSearchField: Boolean = true,
    showTrackSortActionButton: Boolean = true,
    showFolderBrowser: Boolean = false,
    rootSelectorStyle: LibraryRootSelectorStyle = LibraryRootSelectorStyle.Default,
    actionButton: (@Composable () -> Unit)? = null,
    navigationTarget: LibraryNavigationTarget? = null,
    onNavigationHandled: () -> Unit = {},
    onOpenLibraryNavigationTarget: ((LibraryNavigationTarget) -> Unit)? = null,
    batchSelectionRequestKey: Int = 0,
    showInlineBatchOperationButton: Boolean = true,
    phoneRootState: MutableState<LibraryBrowserRootView>? = null,
    phoneRootClickRequestKey: Int = 0,
    rootPagerScrollEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val tracksListState = rememberLazyListState()
    val albumsListState = rememberLazyListState()
    val artistsListState = rememberLazyListState()
    val foldersListState = rememberLazyListState()
    val albumDetailListState = rememberLazyListState()
    val artistDetailListState = rememberLazyListState()
    val folderDetailListState = rememberLazyListState()
    val visibleTracks = remember(state.tracks) { state.tracks.map(LibraryTrackUiItem::track) }
    val visibleAlbums = remember(state.albums) { state.albums.map(LibraryAlbumUiItem::album) }
    val visibleArtists = remember(state.artists) { state.artists.map(LibraryArtistUiItem::artist) }
    val folderDetailScrollPositions = rememberSaveable(saver = LibraryFolderScrollPositionsSaver) {
        mutableMapOf<String, LibraryFolderDetailScrollPosition>()
    }
    var displayedFolderStableId by rememberSaveable { mutableStateOf<String?>(null) }
    var sourceFilterMenuExpanded by remember { mutableStateOf(false) }
    var trackSortMenuExpanded by remember { mutableStateOf(false) }
    val defaultRootState = rememberSaveable { mutableStateOf(LibraryBrowserRootView.Tracks) }
    var rootView by (phoneRootState ?: defaultRootState)
    var previousRootView by remember { mutableStateOf(rootView) }
    var lastHandledRootClickRequestKey by rememberSaveable { mutableStateOf(0) }
    var selectedArtistId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFolderSourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFolderPath by rememberSaveable { mutableStateOf<String?>(null) }
    var lastAppliedOnlineContextTarget by remember { mutableStateOf<LibraryNavigationTarget?>(null) }
    fun selectedFolderStableId(): String? {
        val sourceId = selectedFolderSourceId ?: return null
        return LibraryFolderKey(sourceId = sourceId, path = selectedFolderPath.orEmpty()).stableId
    }
    fun saveSelectedFolderScrollPosition() {
        val stableId = selectedFolderStableId() ?: return
        folderDetailScrollPositions[stableId] = LibraryFolderDetailScrollPosition(
            firstVisibleItemIndex = folderDetailListState.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = folderDetailListState.firstVisibleItemScrollOffset,
        )
    }
    fun selectFolder(folder: LibraryFolderNode) {
        saveSelectedFolderScrollPosition()
        selectedFolderSourceId = folder.sourceId
        selectedFolderPath = folder.path
    }
    fun navigateBackFromSelectedFolder() {
        saveSelectedFolderScrollPosition()
        val destination = resolveLibraryFolderBackDestination(
            selectedFolderSourceId = selectedFolderSourceId,
            selectedFolderPath = selectedFolderPath,
        )
        selectedFolderSourceId = destination.sourceId
        selectedFolderPath = destination.path
    }
    when (resolveLibraryBrowserBackTarget(selectedArtistId, selectedAlbumId, selectedFolderSourceId)) {
        LibraryBrowserBackTarget.Album -> {
            PlatformBackHandler { selectedAlbumId = null }
        }

        LibraryBrowserBackTarget.Artist -> {
            PlatformBackHandler {
                selectedArtistId = null
                selectedAlbumId = null
            }
        }

        LibraryBrowserBackTarget.Folder -> {
            PlatformBackHandler { navigateBackFromSelectedFolder() }
        }

        null -> Unit
    }
    val tracksByArtistId = remember(visibleTracks) {
        visibleTracks.groupBy(Track::artistLibraryIdOrNull)
    }
    val tracksByAlbumId = remember(visibleTracks) {
        visibleTracks.groupBy(Track::albumLibraryIdOrNull)
    }
    val artistAlbumCountById = remember(tracksByArtistId) {
        tracksByArtistId.entries
            .mapNotNull { (artistId, tracks) ->
                artistId?.let { it to deriveVisibleAlbums(tracks).size }
            }
            .toMap()
    }
    val selectedArtistItem = remember(state.artists, state.onlineArtistItemsById, state.isOnline, selectedArtistId) {
        val artistId = selectedArtistId
        when {
            artistId == null -> null
            state.isOnline -> state.artists.firstOrNull { it.id == artistId }
                ?: state.onlineArtistItemsById[artistId]
            else -> state.artists.firstOrNull { it.id == artistId }
        }
    }
    val selectedArtist = selectedArtistItem?.artist
    val onlineArtistAlbumItems = selectedArtistId
        ?.let { state.onlineArtistAlbumsById[it] }
        .orEmpty()
    val isLoadingOnlineArtistAlbums = selectedArtistId in state.loadingArtistAlbumIds
    val localArtistTracks = remember(tracksByArtistId, selectedArtistId) {
        tracksByArtistId[selectedArtistId].orEmpty().sortedWith(ARTIST_DETAIL_TRACK_COMPARATOR)
    }
    val artistTracks = if (state.isOnline && selectedArtistId != null) {
        emptyList()
    } else {
        localArtistTracks
    }
    val localArtistAlbumItems = remember(localArtistTracks) {
        deriveVisibleAlbums(localArtistTracks)
            .map { album -> LibraryAlbumUiItem(id = album.id, album = album) }
    }
    val artistAlbumItems = if (state.isOnline && selectedArtistId != null) {
        onlineArtistAlbumItems
    } else {
        localArtistAlbumItems
    }
    val artistAlbums = remember(artistAlbumItems) {
        artistAlbumItems.map(LibraryAlbumUiItem::album)
    }
    val selectedArtistTrackCount = if (state.isOnline) {
        selectedArtistItem?.trackCount
    } else {
        artistTracks.size
    }
    val selectedArtistAlbumCount = if (state.isOnline) {
        selectedArtistItem?.albumCount
            ?: artistAlbumItems.size.takeIf {
                selectedArtistId != null &&
                    selectedArtistId in state.onlineArtistAlbumsById &&
                    !isLoadingOnlineArtistAlbums
            }
    } else {
        artistAlbumItems.size
    }
    val isLoadingOnlineAlbumTracks = selectedAlbumId in state.loadingAlbumIds
    val selectedAlbumItem =
        remember(
            visibleAlbums,
            state.albums,
            artistAlbumItems,
            selectedAlbumId,
            selectedArtistId,
            state.isOnline,
            state.onlineAlbumItemsById,
        ) {
            when {
                selectedAlbumId == null -> null
                state.isOnline && selectedArtistId != null -> artistAlbumItems.firstOrNull { it.id == selectedAlbumId }
                    ?: state.onlineAlbumItemsById[selectedAlbumId]
                selectedArtistId != null -> artistAlbumItems.firstOrNull { it.id == selectedAlbumId }
                state.isOnline -> state.albums.firstOrNull { it.id == selectedAlbumId }
                    ?: state.onlineAlbumItemsById[selectedAlbumId]
                else -> state.albums.firstOrNull { it.id == selectedAlbumId }
            }
        }
    val selectedAlbum = selectedAlbumItem?.album
    val albumTracks = remember(
        tracksByAlbumId,
        artistTracks,
        selectedAlbumId,
        selectedArtistId,
        state.onlineAlbumTracksById,
        state.isOnline,
    ) {
        val albumId = selectedAlbumId
        when {
            albumId == null -> emptyList()
            state.isOnline -> state.onlineAlbumTracksById[albumId].orEmpty()
            selectedArtistId != null -> artistTracks.filter { it.albumLibraryIdOrNull() == albumId }
            else -> tracksByAlbumId[albumId].orEmpty()
        }.sortedWith(ALBUM_DETAIL_TRACK_COMPARATOR)
    }
    val folderTree = remember(visibleTracks, state.sourceLabelsById, currentUiLanguage) {
        deriveLibraryFolderTree(
            tracks = visibleTracks,
            sourceLabelsById = state.sourceLabelsById,
        )
    }
    val selectedFolderKey = selectedFolderSourceId?.let { sourceId ->
        LibraryFolderKey(sourceId = sourceId, path = selectedFolderPath.orEmpty())
    }
    val selectedFolder = selectedFolderKey?.let { folderTree.nodesByKey[it] }
    val selectedFolderChildren = selectedFolderKey?.let { folderTree.childFoldersByKey[it].orEmpty() }.orEmpty()
    val selectedFolderTracks = selectedFolderKey?.let { folderTree.directTracksByKey[it].orEmpty() }.orEmpty()
    val selectedFolderDetailItemCount = if (selectedFolder == null) {
        0
    } else {
        val childItems = if (selectedFolderChildren.isEmpty()) 0 else 1 + selectedFolderChildren.size
        val trackItems = 1 + if (selectedFolderTracks.isEmpty()) 1 else selectedFolderTracks.size
        2 + childItems + trackItems
    }
    val rootSelectorModel = remember(
        currentUiLanguage,
        rootSelectorStyle,
        state.trackCount,
        state.albumCount,
        state.artistCount,
        folderTree.folderCount,
        showFolderBrowser,
        visibleTracks.isNotEmpty(),
    ) {
        buildLibraryRootSelectorModel(
            style = rootSelectorStyle,
            trackCount = state.trackCount,
            albumCount = state.albumCount,
            artistCount = state.artistCount,
            folderCount = folderTree.folderCount,
            showFolderBrowser = showFolderBrowser,
            playAllEnabled = visibleTracks.isNotEmpty(),
        )
    }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedTrackIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var batchQualitySheetVisible by rememberSaveable { mutableStateOf(false) }
    var lastHandledBatchSelectionRequestKey by rememberSaveable { mutableStateOf(0) }
    var pendingBatchDownloadTracks by remember { mutableStateOf(emptyList<Track>()) }
    val batchVisibleTracks = if (
        rootView == LibraryBrowserRootView.Tracks &&
        selectedArtistId == null &&
        selectedAlbumId == null
    ) {
        visibleTracks
    } else {
        emptyList()
    }
    val selectedBatchTracks = remember(batchVisibleTracks, selectedTrackIds) {
        selectedTracksInVisibleOrder(batchVisibleTracks, selectedTrackIds)
    }
    val allVisibleBatchTracksSelected = batchVisibleTracks.isNotEmpty() &&
        batchVisibleTracks.all { it.id in selectedTrackIds }
    val offlineUiState = LocalOfflineDownloadUiState.current
    val onOfflineDownloadIntent = offlineUiState.onIntent
    val selectedBatchDownloadSizeEstimate = remember(
        selectedBatchTracks,
        offlineUiState.downloadsByTrackId,
    ) {
        estimateBatchDownloadSize(
            tracks = selectedBatchTracks,
            downloadsByTrackId = offlineUiState.downloadsByTrackId,
        )
    }
    val supportsBatchDownload = state.capabilities.canBatchDownload &&
        supportsBatchOfflineDownloadActions() &&
        onOfflineDownloadIntent != null
    val inlineBatchOperationButtonVisible = showInlineBatchOperationButton
    fun exitSelectionMode() {
        selectionMode = false
        selectedTrackIds = emptyList()
        batchQualitySheetVisible = false
        pendingBatchDownloadTracks = emptyList()
    }
    fun startBatchDownload(tracks: List<Track>, quality: NavidromeAudioQuality) {
        val insufficientSpaceMessage = batchDownloadInsufficientSpaceMessage(
            estimate = estimateBatchDownloadSize(
                tracks = tracks,
                downloadsByTrackId = offlineUiState.downloadsByTrackId,
                quality = quality,
            ),
            availableSpaceBytes = offlineUiState.availableSpaceBytes,
        )
        if (insufficientSpaceMessage != null) {
            if (batchQualitySheetVisible) {
                batchQualitySheetVisible = false
                pendingBatchDownloadTracks = emptyList()
            }
            onOfflineDownloadIntent?.invoke(OfflineDownloadIntent.ShowMessage(insufficientSpaceMessage))
            return
        }
        onOfflineDownloadIntent?.invoke(OfflineDownloadIntent.DownloadMany(tracks, quality))
        exitSelectionMode()
    }
    fun requestBatchDownload() {
        val tracks = selectedBatchTracks
        if (tracks.isEmpty()) return
        if (hasNavidromeTracks(tracks)) {
            pendingBatchDownloadTracks = tracks
            batchQualitySheetVisible = true
        } else {
            startBatchDownload(tracks, NavidromeAudioQuality.Original)
        }
    }
    PlatformBackHandler(enabled = selectionMode) {
        exitSelectionMode()
    }
    LaunchedEffect(selectionMode, supportsBatchDownload) {
        if (selectionMode && supportsBatchDownload) {
            onOfflineDownloadIntent(OfflineDownloadIntent.RefreshAvailableSpace)
        }
    }
    LaunchedEffect(batchVisibleTracks) {
        val pruned = pruneSelectedTrackIds(selectedTrackIds, batchVisibleTracks)
        if (pruned != selectedTrackIds) {
            selectedTrackIds = pruned
        }
        if (selectionMode && batchVisibleTracks.isEmpty()) {
            exitSelectionMode()
        }
    }
    LaunchedEffect(batchSelectionRequestKey, supportsBatchDownload, batchVisibleTracks) {
        if (batchSelectionRequestKey <= lastHandledBatchSelectionRequestKey) {
            return@LaunchedEffect
        }
        val shouldEnterSelectionMode = shouldHandleBatchSelectionRequest(
            requestKey = batchSelectionRequestKey,
            lastHandledRequestKey = lastHandledBatchSelectionRequestKey,
            supportsBatchDownload = supportsBatchDownload,
            hasVisibleTracks = batchVisibleTracks.isNotEmpty(),
        )
        lastHandledBatchSelectionRequestKey = batchSelectionRequestKey
        if (shouldEnterSelectionMode) {
            selectionMode = true
        }
    }
    LaunchedEffect(
        navigationTarget,
        state.query,
        state.isOnline,
        state.sourceId,
        state.selectedSourceFilter,
        visibleAlbums,
        visibleArtists,
    ) {
        val target = navigationTarget
        if (target == null) {
            lastAppliedOnlineContextTarget = null
            return@LaunchedEffect
        }
        if (lastAppliedOnlineContextTarget != null && lastAppliedOnlineContextTarget != target) {
            lastAppliedOnlineContextTarget = null
        }
        when (
            val command = resolveLibraryNavigationCommand(
                target = target,
                query = state.query,
                isOnline = state.isOnline,
                onlineSourceId = state.sourceId,
                selectedSourceFilter = state.selectedSourceFilter,
                availableSourceFilters = state.availableSourceFilters,
                filteredAlbums = visibleAlbums,
                filteredArtists = visibleArtists,
            )
        ) {
            is LibraryNavigationCommand.ApplyContext -> {
                if (shouldClearNavigationQuery(command.clearQuery, state.query)) {
                    actions.onSearchChanged("")
                }
                if (state.selectedSourceFilter != command.sourceFilter) {
                    actions.onSourceFilterChanged(command.sourceFilter)
                }
            }

            is LibraryNavigationCommand.ApplyOnlineContext -> {
                if (shouldClearNavigationQuery(command.clearQuery, state.query)) {
                    actions.onSearchChanged("")
                }
                if (shouldApplyOnlineNavigationContext(target, lastAppliedOnlineContextTarget)) {
                    prepareOnlineNavigationTarget(target, actions)
                    lastAppliedOnlineContextTarget = target
                }
            }

            is LibraryNavigationCommand.Navigate -> {
                prepareOnlineNavigationTarget(target, actions)
                when (target) {
                    is LibraryNavigationTarget.OnlineAlbum -> {
                        actions.onLoadAlbumTracks(target.albumId)
                    }

                    is LibraryNavigationTarget.OnlineArtist -> {
                        actions.onLoadArtistAlbums(target.artistId)
                    }

                    is LibraryNavigationTarget.Album,
                    is LibraryNavigationTarget.Artist,
                    -> Unit
                }
                // External album/artist navigation owns the detail target; pager synchronization
                // must not treat it as a user switching back to a category's root list.
                previousRootView = command.resolution.rootView
                rootView = command.resolution.rootView
                selectedArtistId = command.resolution.selectedArtistId
                selectedAlbumId = command.resolution.selectedAlbumId
                lastAppliedOnlineContextTarget = null
                onNavigationHandled()
            }
        }
    }

    LaunchedEffect(rootView, phoneRootClickRequestKey) {
        if (phoneRootState != null &&
            (previousRootView != rootView || phoneRootClickRequestKey != lastHandledRootClickRequestKey)
        ) {
            saveSelectedFolderScrollPosition()
            exitSelectionMode()
            selectedArtistId = null
            selectedAlbumId = null
            selectedFolderSourceId = null
            selectedFolderPath = null
        }
        previousRootView = rootView
        lastHandledRootClickRequestKey = phoneRootClickRequestKey
    }

    LaunchedEffect(
        rootView,
        state.isOnline,
        visibleAlbums,
        visibleArtists,
        selectedArtistId,
        selectedAlbumId,
        artistAlbums,
        selectedFolderKey,
        folderTree.nodesByKey,
    ) {
        when (rootView) {
            LibraryBrowserRootView.Tracks -> {
                if (selectedArtistId != null) selectedArtistId = null
                if (selectedAlbumId != null) selectedAlbumId = null
                if (selectedFolderSourceId != null) selectedFolderSourceId = null
                if (selectedFolderPath != null) selectedFolderPath = null
            }

            LibraryBrowserRootView.Albums -> {
                if (selectedArtistId != null) selectedArtistId = null
                if (selectedFolderSourceId != null) selectedFolderSourceId = null
                if (selectedFolderPath != null) selectedFolderPath = null
                if (
                    !state.isOnline &&
                    selectedAlbumId != null &&
                    visibleAlbums.none { it.id == selectedAlbumId }
                ) {
                    selectedAlbumId = null
                }
            }

            LibraryBrowserRootView.Artists -> {
                if (selectedFolderSourceId != null) selectedFolderSourceId = null
                if (selectedFolderPath != null) selectedFolderPath = null
                if (
                    !state.isOnline &&
                    selectedArtistId != null &&
                    visibleArtists.none { it.id == selectedArtistId }
                ) {
                    selectedArtistId = null
                    selectedAlbumId = null
                } else if (
                    !state.isOnline &&
                    selectedAlbumId != null &&
                    artistAlbums.none { it.id == selectedAlbumId }
                ) {
                    selectedAlbumId = null
                }
            }

            LibraryBrowserRootView.Folders -> {
                if (!showFolderBrowser && phoneRootState == null) {
                    rootView = LibraryBrowserRootView.Tracks
                    selectedFolderSourceId = null
                    selectedFolderPath = null
                    return@LaunchedEffect
                }
                if (selectedArtistId != null) selectedArtistId = null
                if (selectedAlbumId != null) selectedAlbumId = null
                if (selectedFolderKey != null && selectedFolderKey !in folderTree.nodesByKey) {
                    selectedFolderSourceId = null
                    selectedFolderPath = null
                }
            }
        }
    }

    LaunchedEffect(selectedFolderKey?.stableId, selectedFolder != null) {
        val stableId = selectedFolderKey?.stableId ?: return@LaunchedEffect
        if (selectedFolderDetailItemCount <= 0) return@LaunchedEffect
        // The list state already restores the current folder's exact scroll position.
        // Only apply the per-folder history when navigating to a different folder.
        if (displayedFolderStableId == stableId) return@LaunchedEffect
        val position = folderDetailScrollPositions[stableId] ?: LibraryFolderDetailScrollPosition(
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
        )
        val itemIndex = position.firstVisibleItemIndex.coerceIn(0, selectedFolderDetailItemCount - 1)
        folderDetailListState.scrollToItem(
            index = itemIndex,
            scrollOffset = position.firstVisibleItemScrollOffset.coerceAtLeast(0),
        )
        displayedFolderStableId = stableId
    }

    fun selectRootView(view: LibraryBrowserRootView) {
        if (selectionMode) {
            exitSelectionMode()
        }
        saveSelectedFolderScrollPosition()
        previousRootView = view
        rootView = view
        selectedArtistId = null
        selectedAlbumId = null
        selectedFolderSourceId = null
        selectedFolderPath = null
    }
    fun trackRowNavigationTargets(track: Track): PlaybackLibraryNavigationTargets {
        val onlineSourceId = state.sourceId
        if (
            state.isOnline &&
            onlineSourceId != null &&
            showDuration &&
            onOpenLibraryNavigationTarget != null
        ) {
            return deriveOnlineTrackLibraryNavigationTargets(
                track = track,
                sourceId = onlineSourceId,
            )
        }
        return resolveTrackRowLibraryNavigationTargets(
            track = track,
            showDuration = showDuration,
            metadataNavigationEnabled = onOpenLibraryNavigationTarget != null,
            preferredSourceFilter = state.selectedSourceFilter,
        )
    }
    fun navigationTargetClick(target: LibraryNavigationTarget?): (() -> Unit)? {
        val handler = onOpenLibraryNavigationTarget ?: return null
        return target?.let { resolvedTarget ->
            { handler(resolvedTarget) }
        }
    }

    val shellColors = mainShellColors
    val searchFieldContainerColor = shellColors.cardBorder
    val showTrackSortMenu = showTrackSortActionButton &&
        rootView == LibraryBrowserRootView.Tracks &&
        selectedArtistId == null &&
        selectedAlbumId == null
    val batchOperationButton: (@Composable () -> Unit)? = if (
        supportsBatchDownload &&
        inlineBatchOperationButtonVisible &&
        !selectionMode &&
        batchVisibleTracks.isNotEmpty()
    ) {
        {
            IconButton(onClick = { selectionMode = true }) {
                Icon(
                    imageVector = Icons.Rounded.Checklist,
                    contentDescription = uiString(Res.string.library_batch_actions),
                )
            }
        }
    } else {
        null
    }
    val combinedActionButton: (@Composable () -> Unit)? = when {
        batchOperationButton != null && actionButton != null -> {
            {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    batchOperationButton()
                    actionButton()
                }
            }
        }

        batchOperationButton != null -> batchOperationButton
        else -> actionButton
    }
    val searchFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = searchFieldContainerColor,
        unfocusedContainerColor = searchFieldContainerColor,
        disabledContainerColor = searchFieldContainerColor,
        focusedBorderColor = searchFieldContainerColor,
        unfocusedBorderColor = searchFieldContainerColor,
        disabledBorderColor = searchFieldContainerColor,
    )
    val tracksStatFocusRequester = remember { FocusRequester() }
    LaunchedEffect(phoneRootState == null) {
        if (phoneRootState == null) tracksStatFocusRequester.requestFocus()
    }
    val activeRootView = rootView
    val pageContent: @Composable (LibraryBrowserRootView) -> Unit = { pageRoot ->
        val rootView = pageRoot
        val selectedAlbum = selectedAlbum.takeIf { pageRoot == activeRootView }
        val selectedArtist = selectedArtist.takeIf { pageRoot == activeRootView }
        val selectedFolder = selectedFolder.takeIf { pageRoot == activeRootView }
        val activeListState = when {
            selectedAlbum != null -> albumDetailListState
            rootView == LibraryBrowserRootView.Artists && selectedArtist != null -> artistDetailListState
            rootView == LibraryBrowserRootView.Folders && selectedFolder != null -> folderDetailListState
            rootView == LibraryBrowserRootView.Albums -> albumsListState
            rootView == LibraryBrowserRootView.Artists -> artistsListState
            rootView == LibraryBrowserRootView.Folders -> foldersListState
            else -> tracksListState
        }
        val useDesktopToolbar = useDesktopLibraryBrowserToolbar(
            showSearchField = showSearchField,
            showDuration = showDuration,
        )


        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = activeListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = dockContentPadding(PaddingValues(start = 20.dp, top = 20.dp, end = 42.dp, bottom = 20.dp)),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showSearchField || combinedActionButton != null) {
                    item {
                        if (showSearchField) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                    //.height(56.dp)
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LibraryBrowserSearchField(
                                    query = state.query,
                                    onQueryChanged = actions.onSearchChanged,
                                    placeholder = strings.searchLabel,
                                    useDesktopToolbar = useDesktopToolbar,
                                    containerColor = searchFieldContainerColor,
                                    colors = searchFieldColors,
                                    modifier = if (useDesktopToolbar) {
                                        Modifier
                                    } else {
                                        Modifier.weight(1f)
                                    },
                                    nonDesktopTrailingIcon = if (useDesktopToolbar) {
                                        null
                                    } else {
                                        {
                                            LibraryBrowserToolbarActions(
                                                availableSourceFilters = state.availableSourceFilters,
                                                selectedSourceFilter = state.selectedSourceFilter,
                                                onlineSourceOptions = onlineSourceOptions,
                                                selectedOnlineSourceId = state.sourceId,
                                                sourceFilterMenuExpanded = sourceFilterMenuExpanded,
                                                onSourceFilterMenuExpandedChange = { sourceFilterMenuExpanded = it },
                                                onSourceFilterChanged = actions.onSourceFilterChanged,
                                                onOnlineSourceSelected = actions.onOnlineSourceSelected,
                                                showTrackSortMenu = showTrackSortMenu,
                                                selectedTrackSortMode = state.selectedTrackSortMode,
                                                trackSortMenuExpanded = trackSortMenuExpanded,
                                                onTrackSortMenuExpandedChange = { trackSortMenuExpanded = it },
                                                onTrackSortChanged = actions.onTrackSortChanged,
                                                actionButton = combinedActionButton,
                                            )
                                        }
                                    },
                                )
                                if (useDesktopToolbar) {
                                    Spacer(Modifier.weight(1f))
                                    LibraryBrowserToolbarActions(
                                        availableSourceFilters = state.availableSourceFilters,
                                        selectedSourceFilter = state.selectedSourceFilter,
                                        onlineSourceOptions = onlineSourceOptions,
                                        selectedOnlineSourceId = state.sourceId,
                                        sourceFilterMenuExpanded = sourceFilterMenuExpanded,
                                        onSourceFilterMenuExpandedChange = { sourceFilterMenuExpanded = it },
                                        onSourceFilterChanged = actions.onSourceFilterChanged,
                                        onOnlineSourceSelected = actions.onOnlineSourceSelected,
                                        showTrackSortMenu = showTrackSortMenu,
                                        selectedTrackSortMode = state.selectedTrackSortMode,
                                        trackSortMenuExpanded = trackSortMenuExpanded,
                                        onTrackSortMenuExpandedChange = { trackSortMenuExpanded = it },
                                        onTrackSortChanged = actions.onTrackSortChanged,
                                        actionButton = combinedActionButton,
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                combinedActionButton?.invoke()
                            }
                        }
                    }
                }
                if (selectionMode && pageRoot == activeRootView) {
                    item {
                        TrackSelectionActionBar(
                            selectedCount = selectedBatchTracks.size,
                            downloadSizeEstimateLabel = batchDownloadSizeEstimateLabel(selectedBatchDownloadSizeEstimate).displayText(),
                            allVisibleSelected = allVisibleBatchTracksSelected,
                            hasVisibleTracks = batchVisibleTracks.isNotEmpty(),
                            onToggleSelectAll = {
                                selectedTrackIds = toggleAllVisibleTrackSelection(selectedTrackIds, batchVisibleTracks)
                            },
                            onDownloadSelected = ::requestBatchDownload,
                            onCancelSelection = ::exitSelectionMode,
                        )
                    }
                }
                if (phoneRootState == null) item {
                    LibraryRootSelector(
                        model = rootSelectorModel,
                        selectedRootView = rootView,
                        songsIcon = strings.songsIcon,
                        tracksStatFocusRequester = tracksStatFocusRequester,
                        onSelectRootView = ::selectRootView,
                        onPlayAllTracks = {
                            if (visibleTracks.isNotEmpty()) {
                                actions.onPlayTracks(visibleTracks, 0)
                            }
                        },
                    )
                }
                state.message?.let { message ->
                    item {
                        BannerCard(
                            message = message,
                            onDismiss = actions.onDismissMessage,
                        )
                    }
                }
                when {
                    phoneRootState != null && rootView == LibraryBrowserRootView.Folders && !showFolderBrowser -> {
                        item {
                            EmptyStateCard(
                                title = uiString(Res.string.library_source_folder_browsing_unsupported),
                                body = uiString(Res.string.library_folder_local_source_required),
                            )
                        }
                    }
                    selectedAlbum != null -> {
                        item {
                            DetailBackButton(onClick = { selectedAlbumId = null })
                        }
                        item {
                            DetailSummaryCard(
                                title = selectedAlbum.title.ifBlank { uiString(Res.string.common_unknown_album) },
                                subtitle = selectedAlbum.artistName ?: uiString(Res.string.common_unknown_artist),
                                supportingText = if (isLoadingOnlineAlbumTracks) {
                                    uiString(Res.string.library_tracks_loading)
                                } else {
                                    uiString(Res.plurals.common_track_count, (albumTracks.size).toInt(), albumTracks.size)
                                },
                                artworkLocator = albumTracks.firstOrNull()?.artworkLocator
                                    ?: selectedAlbumItem?.artworkLocator,
                                artworkCacheKey = albumTracks.firstOrNull()?.let(::trackArtworkCacheKey),
                            )
                        }
                        item {
                            SectionTitle(title = uiString(Res.string.library_tracks_title), subtitle = uiString(Res.string.library_album_visible_tracks_description))
                        }
                        if (isLoadingOnlineAlbumTracks && albumTracks.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = uiString(Res.string.library_album_tracks_loading),
                                    body = uiString(Res.string.library_online_album_loading_hint),
                                )
                            }
                        } else if (albumTracks.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = uiString(Res.string.library_album_tracks_empty_title),
                                    body = uiString(Res.string.library_album_tracks_filter_empty_hint),
                                )
                            }
                        } else {
                            itemsIndexed(albumTracks, key = { _, item -> item.id }) { index, track ->
                                val navigationTargets = trackRowNavigationTargets(track)
                                TrackRow(
                                    track = track,
                                    index = index,
                                    isFavorite = track.id in state.favoriteTrackIds,
                                    onToggleFavorite = { actions.onToggleFavorite(track) },
                                    showFavoriteButton = showFavoriteButton,
                                    showDuration = showDuration,
                                    onArtistClick = navigationTargetClick(navigationTargets.artistTarget),
                                    onAlbumClick = navigationTargetClick(navigationTargets.albumTarget),
                                    onClick = {
                                        actions.onPlayTracks(albumTracks, index)
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
                                title = selectedArtist.name.ifBlank { uiString(Res.string.common_unknown_artist) },
                                subtitle = artistSummaryLabel(
                                    trackCount = selectedArtistTrackCount,
                                    albumCount = selectedArtistAlbumCount,
                                ),
                                supportingText = if (state.isOnline) {
                                    uiString(Res.string.library_online_artist_detail_title)
                                } else {
                                    uiString(Res.string.library_artist_filtered_detail_description)
                                },
                                artworkLocator = if (state.isOnline) null else artistTracks.firstOrNull()?.artworkLocator,
                                artworkCacheKey = if (state.isOnline) {
                                    null
                                } else {
                                    artistTracks.firstOrNull()?.let(::trackArtworkCacheKey)
                                },
                            )
                        }
                        item {
                            SectionTitle(
                                title = uiString(Res.string.library_albums_title),
                                subtitle = if (state.isOnline) uiString(Res.string.library_navidrome_artist_albums_description) else uiString(Res.string.library_artist_visible_albums_description),
                            )
                        }
                        if (isLoadingOnlineArtistAlbums && artistAlbumItems.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = uiString(Res.string.library_artist_albums_loading),
                                    body = uiString(Res.string.library_navidrome_artist_albums_loading_hint),
                                )
                            }
                        } else if (artistAlbumItems.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = if (state.isOnline) uiString(Res.string.library_artist_albums_empty_title) else uiString(Res.string.library_artist_album_metadata_missing),
                                    body = if (state.isOnline) {
                                        uiString(Res.string.library_navidrome_artist_albums_empty_hint)
                                    } else {
                                        uiString(Res.string.library_artist_album_tags_missing_hint)
                                    },
                                )
                            }
                        } else {
                            items(artistAlbumItems, key = { it.id }) { albumItem ->
                                val album = albumItem.album
                                val fallbackArtworkTrack = if (state.isOnline) {
                                    null
                                } else {
                                    artistTracks.firstOrNull { it.albumLibraryIdOrNull() == album.id }
                                }
                                AlbumRow(
                                    album = album,
                                    artworkLocator = albumItem.artworkLocator ?: fallbackArtworkTrack?.artworkLocator,
                                    artworkCacheKey = if (albumItem.artworkLocator == null) {
                                        fallbackArtworkTrack?.let(::trackArtworkCacheKey)
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        actions.onAlbumClick(albumItem)
                                        selectedAlbumId = album.id
                                    },
                                )
                            }
                        }
                        if (!state.isOnline) {
                            item {
                                SectionTitle(title = uiString(Res.string.library_tracks_title), subtitle = uiString(Res.string.library_artist_visible_tracks_description))
                            }
                            if (artistTracks.isEmpty()) {
                                item {
                                    EmptyStateCard(
                                        title = uiString(Res.string.library_artist_tracks_empty_title),
                                        body = uiString(Res.string.library_artist_tracks_filter_empty_hint),
                                    )
                                }
                            } else {
                                itemsIndexed(artistTracks, key = { _, item -> item.id }) { index, track ->
                                    val navigationTargets = trackRowNavigationTargets(track)
                                    TrackRow(
                                        track = track,
                                        index = index,
                                        isFavorite = track.id in state.favoriteTrackIds,
                                        onToggleFavorite = { actions.onToggleFavorite(track) },
                                        showFavoriteButton = showFavoriteButton,
                                        showDuration = showDuration,
                                        onArtistClick = navigationTargetClick(navigationTargets.artistTarget),
                                        onAlbumClick = navigationTargetClick(navigationTargets.albumTarget),
                                        onClick = {
                                            actions.onPlayTracks(artistTracks, index)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    rootView == LibraryBrowserRootView.Folders && selectedFolder != null -> {
                        item {
                            DetailBackButton(
                                onClick = ::navigateBackFromSelectedFolder,
                            )
                        }
                        item {
                            DetailSummaryCard(
                                title = selectedFolder.name,
                                subtitle = libraryFolderDetailSubtitle(selectedFolder),
                                supportingText = libraryFolderSummaryLabel(selectedFolder),
                                artworkLocator = selectedFolderTracks.firstOrNull()?.artworkLocator,
                                artworkCacheKey = selectedFolderTracks.firstOrNull()?.let(::trackArtworkCacheKey),
                            )
                        }
                        if (selectedFolderChildren.isNotEmpty()) {
                            item {
                                SectionTitle(title = uiString(Res.string.library_folders_title), subtitle = uiString(Res.string.library_subfolders_description))
                            }
                            items(selectedFolderChildren, key = { it.key.stableId }) { folder ->
                                FolderRow(
                                    folder = folder,
                                    onClick = { selectFolder(folder) },
                                )
                            }
                        }
                        item {
                            SectionTitle(title = uiString(Res.string.library_tracks_title), subtitle = uiString(Res.string.library_folder_tracks_description))
                        }
                        if (selectedFolderTracks.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = uiString(Res.string.library_folder_direct_tracks_empty),
                                    body = if (selectedFolderChildren.isEmpty()) {
                                        uiString(Res.string.library_folder_tracks_filter_empty_hint)
                                    } else {
                                        uiString(Res.string.library_subfolders_browse_hint)
                                    },
                                )
                            }
                        } else {
                            itemsIndexed(selectedFolderTracks, key = { _, item -> item.id }) { index, track ->
                                val navigationTargets = trackRowNavigationTargets(track)
                                TrackRow(
                                    track = track,
                                    index = index,
                                    isFavorite = track.id in state.favoriteTrackIds,
                                    onToggleFavorite = { actions.onToggleFavorite(track) },
                                    showFavoriteButton = showFavoriteButton,
                                    showDuration = showDuration,
                                    onArtistClick = navigationTargetClick(navigationTargets.artistTarget),
                                    onAlbumClick = navigationTargetClick(navigationTargets.albumTarget),
                                    onClick = {
                                        actions.onPlayTracks(selectedFolderTracks, index)
                                    },
                                )
                            }
                        }
                    }

                    else -> {
                        val currentItemCount = when (rootView) {
                            LibraryBrowserRootView.Tracks -> visibleTracks.size
                            LibraryBrowserRootView.Albums -> visibleAlbums.size
                            LibraryBrowserRootView.Artists -> visibleArtists.size
                            LibraryBrowserRootView.Folders -> folderTree.rootFolders.size
                        }
                        val currentLabel = when (rootView) {
                            LibraryBrowserRootView.Tracks -> strings.trackLabel
                            LibraryBrowserRootView.Albums -> strings.albumLabel
                            LibraryBrowserRootView.Artists -> strings.artistLabel
                            LibraryBrowserRootView.Folders -> strings.folderLabel
                        }
                        item {
                            if (phoneRootState != null && rootView == LibraryBrowserRootView.Tracks) {
                                PhoneLibraryTrackActions(
                                    loadedCount = visibleTracks.size,
                                    count = state.trackCount,
                                    isOnline = state.isOnline,
                                    onPlay = { actions.onPlayTracks(visibleTracks, 0) },
                                )
                            } else {
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
                        }
                        if (currentItemCount == 0) {
                            item {
                                when {
                                    state.isLoading -> EmptyStateCard(
                                        title = uiString(Res.string.library_loading_named_category, currentLabel),
                                        body = uiString(Res.string.library_background_indexing_hint),
                                    )

                                    state.allTrackCount == 0 -> EmptyStateCard(
                                        title = strings.emptyCollectionTitle,
                                        body = strings.emptyCollectionBody,
                                    )

                                    state.selectedSourceFilter != LibrarySourceFilter.ALL -> EmptyStateCard(
                                        title = uiString(Res.string.library_source_category_empty_title, currentLabel),
                                        body = strings.emptyFilterBody,
                                    )

                                    else -> EmptyStateCard(
                                        title = uiString(Res.string.library_category_filter_empty_title, currentLabel),
                                        body = strings.emptySearchBody,
                                    )
                                }
                            }
                        } else {
                            when (rootView) {
                                LibraryBrowserRootView.Tracks -> {
                                    itemsIndexed(
                                        state.tracks,
                                        key = { _, item -> item.id }) { index, trackItem ->
                                        val track = trackItem.track
                                        val navigationTargets = trackRowNavigationTargets(track)
                                        TrackRow(
                                            track = track,
                                            index = index,
                                            isFavorite = trackItem.isFavorite,
                                            onToggleFavorite = { actions.onToggleFavorite(track) },
                                            showFavoriteButton = showFavoriteButton,
                                            showDuration = showDuration,
                                            onArtistClick = navigationTargetClick(navigationTargets.artistTarget),
                                            onAlbumClick = navigationTargetClick(navigationTargets.albumTarget),
                                            selectionMode = selectionMode,
                                            selected = track.id in selectedTrackIds,
                                            onSelectionToggle = {
                                                selectedTrackIds = toggleTrackSelection(selectedTrackIds, track.id)
                                            },
                                            onClick = {
                                                actions.onPlayTracks(visibleTracks, index)
                                            },
                                        )
                                    }
                                    if (state.capabilities.canLoadMoreTracks) {
                                        item {
                                            LibraryLoadMoreRow(
                                                isLoading = state.isLoadingMoreTracks,
                                                count = state.trackCount,
                                                onLoadMore = actions.onLoadMoreTracks,
                                            )
                                        }
                                    }
                                }

                                LibraryBrowserRootView.Albums -> {
                                    items(state.albums, key = { it.id }) { albumItem ->
                                        val album = albumItem.album
                                        val fallbackArtworkTrack = tracksByAlbumId[album.id].orEmpty().firstOrNull()
                                        AlbumRow(
                                            album = album,
                                            artworkLocator = albumItem.artworkLocator ?: fallbackArtworkTrack?.artworkLocator,
                                            artworkCacheKey = if (albumItem.artworkLocator == null) {
                                                fallbackArtworkTrack?.let(::trackArtworkCacheKey)
                                            } else {
                                                null
                                            },
                                            onClick = {
                                                actions.onAlbumClick(albumItem)
                                                selectedAlbumId = album.id
                                            },
                                        )
                                    }
                                    if (state.capabilities.canLoadMoreAlbums) {
                                        item {
                                            LibraryLoadMoreRow(
                                                isLoading = state.isLoadingMoreAlbums,
                                                count = state.albumCount,
                                                onLoadMore = actions.onLoadMoreAlbums,
                                            )
                                        }
                                    }
                                }

                                LibraryBrowserRootView.Artists -> {
                                    items(state.artists, key = { it.id }) { artistItem ->
                                        val artist = artistItem.artist
                                        ArtistRow(
                                            artist = artist,
                                            trackCount = artistItem.trackCount ?: if (state.isOnline) null else artist.trackCount,
                                            albumCount = artistItem.albumCount
                                                ?: if (state.isOnline) null else artistAlbumCountById[artist.id] ?: 0,
                                            onClick = {
                                                actions.onArtistClick(artistItem)
                                                selectedArtistId = artist.id
                                                selectedAlbumId = null
                                            },
                                        )
                                    }
                                    if (state.capabilities.canLoadMoreArtists) {
                                        item {
                                            LibraryLoadMoreRow(
                                                isLoading = state.isLoadingMoreArtists,
                                                count = state.artistCount,
                                                onLoadMore = actions.onLoadMoreArtists,
                                            )
                                        }
                                    }
                                }

                                LibraryBrowserRootView.Folders -> {
                                    items(folderTree.rootFolders, key = { it.key.stableId }) { folder ->
                                        FolderRow(
                                            folder = folder,
                                            onClick = { selectFolder(folder) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            LibraryFastScrollbar(
                listState = activeListState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 8.dp, top = 20.dp, bottom = 20.dp + LocalDockBottomInset.current),
            )
        }
    }
    if (phoneRootState != null) {
        val roots = LibraryBrowserRootView.entries
        val pagerState = rememberPagerState(initialPage = rootView.ordinal) { roots.size }
        LaunchedEffect(rootView) {
            if (pagerState.currentPage != rootView.ordinal) pagerState.scrollToPage(rootView.ordinal)
        }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { page ->
                    if (pagerState.isScrollInProgress && roots[page] != rootView) {
                        selectRootView(roots[page])
                    }
                }
        }
        HorizontalPager(
            state = pagerState,
            modifier = modifier.fillMaxSize(),
            userScrollEnabled = rootPagerScrollEnabled && !selectionMode &&
                selectedAlbumId == null && selectedArtistId == null && selectedFolderSourceId == null,
            key = { roots[it].name },
        ) { page -> pageContent(roots[page]) }
    } else {
        Box(modifier = modifier.fillMaxSize()) { pageContent(rootView) }
    }
    if (batchQualitySheetVisible) {
        BatchDownloadQualityBottomSheet(
            selectedCount = pendingBatchDownloadTracks.size,
            tracks = pendingBatchDownloadTracks,
            downloadsByTrackId = offlineUiState.downloadsByTrackId,
            onQualitySelected = { quality ->
                startBatchDownload(pendingBatchDownloadTracks, quality)
            },
            onDismiss = {
                batchQualitySheetVisible = false
                pendingBatchDownloadTracks = emptyList()
            },
        )
    }
}

@Composable
private fun PhoneLibraryTrackActions(
    loadedCount: Int,
    count: LibraryBrowserCount,
    isOnline: Boolean,
    onPlay: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val enabled = loadedCount > 0
    val playColor = if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val totalCount = count.total
    val partiallyLoaded = isOnline && (count.hasMore || (totalCount ?: loadedCount) > loadedCount)
    val countLabel = when {
        partiallyLoaded && totalCount != null -> uiPlural(Res.plurals.library_loaded_track_progress, totalCount, loadedCount, totalCount).displayText()
        partiallyLoaded -> uiString(Res.plurals.library_loaded_track_count, (loadedCount).toInt(), loadedCount)
        else -> uiString(Res.plurals.common_track_count_short, (loadedCount).toInt(), loadedCount)
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onPlay,
                )
                .alpha(if (enabled && isPressed) 0.6f else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = playColor,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (partiallyLoaded) uiString(Res.string.library_play_loaded_tracks) else uiString(Res.string.player_play_all),
                color = playColor,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
        Text(
            text = countLabel,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun LibraryLoadMoreRow(
    isLoading: Boolean,
    count: LibraryBrowserCount? = null,
    onLoadMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        count?.let {
            Text(
                text = libraryLoadMoreStatusLabel(it),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = onLoadMore,
            enabled = !isLoading,
        ) {
            Text(if (isLoading) uiString(Res.string.library_loading_status) else uiString(Res.string.library_load_more_action))
        }
    }
}

internal fun libraryLoadMoreStatusLabelText(count: LibraryBrowserCount): UiText {
    val loaded = count.loaded.coerceAtLeast(0)
    val total = count.total?.coerceAtLeast(0)
    return if (total != null) {
        uiText(Res.string.library_visible_count_progress, loaded, total)
    } else if (count.hasMore) {
        uiText(Res.string.library_visible_count_partial, loaded)
    } else {
        uiText(Res.string.library_visible_count, loaded)
    }
}

@Composable
internal fun libraryLoadMoreStatusLabel(count: LibraryBrowserCount): String = libraryLoadMoreStatusLabelText(count).displayText()

@Composable
private fun LibraryRootSelector(
    model: LibraryRootSelectorModel,
    selectedRootView: LibraryBrowserRootView,
    songsIcon: ImageVector,
    tracksStatFocusRequester: FocusRequester,
    onSelectRootView: (LibraryBrowserRootView) -> Unit,
    onPlayAllTracks: () -> Unit,
) {
    when (model.style) {
        LibraryRootSelectorStyle.Default -> DefaultLibraryRootSelector(
            items = model.defaultItems,
            selectedRootView = selectedRootView,
            songsIcon = songsIcon,
            tracksStatFocusRequester = tracksStatFocusRequester,
            onSelectRootView = onSelectRootView,
        )

        LibraryRootSelectorStyle.CompactHero -> CompactLibraryRootSelector(
            model = model,
            selectedRootView = selectedRootView,
            songsIcon = songsIcon,
            tracksStatFocusRequester = tracksStatFocusRequester,
            onSelectRootView = onSelectRootView,
            onPlayAllTracks = onPlayAllTracks,
        )
    }
}

@Composable
private fun DefaultLibraryRootSelector(
    items: List<LibraryRootSelectorItem>,
    selectedRootView: LibraryBrowserRootView,
    songsIcon: ImageVector,
    tracksStatFocusRequester: FocusRequester,
    onSelectRootView: (LibraryBrowserRootView) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { item ->
            val focusModifier = if (item.rootView == LibraryBrowserRootView.Tracks) {
                Modifier.focusRequester(tracksStatFocusRequester)
            } else {
                Modifier
            }
            StatCard(
                title = item.title.displayText(),
                value = item.value,
                icon = defaultLibraryRootIcon(item.rootView, songsIcon),
                selected = selectedRootView == item.rootView,
                onClick = { onSelectRootView(item.rootView) },
                modifier = Modifier
                    .weight(1f)
                    .then(focusModifier),
            )
        }
    }
}

@Composable
private fun CompactLibraryRootSelector(
    model: LibraryRootSelectorModel,
    selectedRootView: LibraryBrowserRootView,
    songsIcon: ImageVector,
    tracksStatFocusRequester: FocusRequester,
    onSelectRootView: (LibraryBrowserRootView) -> Unit,
    onPlayAllTracks: () -> Unit,
) {
    val heroItem = model.heroItem ?: return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CompactLibraryHeroCard(
            item = heroItem,
            selected = selectedRootView == heroItem.rootView,
            songsIcon = songsIcon,
            tracksStatFocusRequester = tracksStatFocusRequester,
            playAllEnabled = model.playAllEnabled,
            onSelectTracks = { onSelectRootView(heroItem.rootView) },
            onPlayAllTracks = onPlayAllTracks,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            model.secondaryItems.forEach { item ->
                CompactLibrarySmallCard(
                    item = item,
                    icon = compactLibraryRootIcon(item.rootView, songsIcon),
                    selected = selectedRootView == item.rootView,
                    onClick = { onSelectRootView(item.rootView) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CompactLibraryHeroCard(
    item: LibraryRootSelectorItem,
    selected: Boolean,
    songsIcon: ImageVector,
    tracksStatFocusRequester: FocusRequester,
    playAllEnabled: Boolean,
    onSelectTracks: () -> Unit,
    onPlayAllTracks: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.92f)
    }
    val contentColor = MaterialTheme.colorScheme.onSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .focusRequester(tracksStatFocusRequester)
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor)
            .clickable(onClick = onSelectTracks)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(contentColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = songsIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = item.title.displayText(),
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.value,
                style = MaterialTheme.typography.headlineMedium,
                color = contentColor,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onPlayAllTracks,
            enabled = playAllEnabled,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(contentColor.copy(alpha = if (playAllEnabled) 0.22f else 0.12f)),
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = uiString(Res.string.library_play_all_tracks),
                tint = contentColor.copy(alpha = if (playAllEnabled) 1f else 0.48f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun CompactLibrarySmallCard(
    item: LibraryRootSelectorItem,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    val shape = RoundedCornerShape(24.dp)
    val accentColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .clip(shape)
            .background(shellColors.cardContainer)
            .border(BorderStroke(1.dp, shellColors.cardBorder), shape)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) {
                            accentColor.copy(alpha = 0.15f)
                        } else {
                            Color.Transparent
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = item.value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.title.displayText(),
                style = MaterialTheme.typography.bodyMedium,
                color = shellColors.secondaryText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun defaultLibraryRootIcon(
    view: LibraryBrowserRootView,
    songsIcon: ImageVector,
): ImageVector {
    return when (view) {
        LibraryBrowserRootView.Tracks -> songsIcon
        LibraryBrowserRootView.Albums -> Icons.Rounded.Album
        LibraryBrowserRootView.Artists -> Icons.Rounded.RecentActors
        LibraryBrowserRootView.Folders -> Icons.Rounded.FolderOpen
    }
}

private fun compactLibraryRootIcon(
    view: LibraryBrowserRootView,
    songsIcon: ImageVector,
): ImageVector {
    return defaultLibraryRootIcon(view, songsIcon)
}

@Composable
private fun FolderRow(
    folder: LibraryFolderNode,
    onClick: () -> Unit,
) {
    val shellColors = mainShellColors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(shellColors.cardContainer)
                    .border(
                        border = BorderStroke(1.dp, shellColors.cardBorder),
                        shape = RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = libraryFolderSummaryLabel(folder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 88.dp)
                .height(1.dp)
                .background(shellColors.cardBorder),
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

internal fun desktopLibrarySearchFieldWidthDp(): Int = 200

internal fun desktopLibrarySearchFieldHeightDp(): Int = 40

internal fun desktopLibrarySearchFieldCornerRadiusDp(): Int = 8

internal fun shouldShowDesktopLibrarySearchClearButton(query: String): Boolean = query.isNotBlank()

internal fun useDesktopLibraryBrowserToolbar(
    showSearchField: Boolean,
    showDuration: Boolean,
): Boolean = showSearchField && showDuration

internal data class DesktopLibraryToolbarActions(
    val showsSourceFilter: Boolean,
    val showsTrackSort: Boolean,
    val showsActionButton: Boolean,
)

internal fun resolveDesktopLibraryToolbarActions(
    showSearchField: Boolean,
    showDuration: Boolean,
    showTrackSortMenu: Boolean,
    hasActionButton: Boolean,
): DesktopLibraryToolbarActions {
    val usesDesktopToolbar = useDesktopLibraryBrowserToolbar(showSearchField, showDuration)
    return DesktopLibraryToolbarActions(
        showsSourceFilter = usesDesktopToolbar,
        showsTrackSort = usesDesktopToolbar && showTrackSortMenu,
        showsActionButton = usesDesktopToolbar && hasActionButton,
    )
}

@Composable
private fun LibraryBrowserSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    placeholder: String,
    useDesktopToolbar: Boolean,
    containerColor: Color,
    colors: TextFieldColors,
    nonDesktopTrailingIcon: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (useDesktopToolbar) {
        DesktopLibrarySearchField(
            query = query,
            onQueryChanged = onQueryChanged,
            placeholder = placeholder,
            containerColor = containerColor,
            modifier = modifier,
        )
    } else {
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
            modifier = modifier,
            shape = RoundedCornerShape(22.dp),
            colors = colors,
            trailingIcon = nonDesktopTrailingIcon,
        )
    }
}

@Composable
private fun DesktopLibrarySearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    placeholder: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    var textFieldValueState by remember {
        mutableStateOf(librarySearchTextFieldValueFor(query))
    }
    LaunchedEffect(query) {
        if (query != textFieldValueState.text) {
            textFieldValueState = librarySearchTextFieldValueFor(query)
        }
    }

    val shape = RoundedCornerShape(desktopLibrarySearchFieldCornerRadiusDp().dp)
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
    )
    BasicTextField(
        value = textFieldValueState,
        onValueChange = { updatedValue ->
            textFieldValueState = updatedValue
            if (updatedValue.composition == null && updatedValue.text != query) {
                onQueryChanged(updatedValue.text)
            }
        },
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .width(desktopLibrarySearchFieldWidthDp().dp)
            .height(desktopLibrarySearchFieldHeightDp().dp)
            .clip(shape)
            .background(containerColor),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 18.dp,
                        end = if (shouldShowDesktopLibrarySearchClearButton(textFieldValueState.text)) {
                            4.dp
                        } else {
                            18.dp
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (textFieldValueState.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (shouldShowDesktopLibrarySearchClearButton(textFieldValueState.text)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                textFieldValueState = librarySearchTextFieldValueFor("")
                                onQueryChanged("")
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = uiString(Res.string.search_clear_query),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    )
}

private fun librarySearchTextFieldValueFor(value: String): TextFieldValue {
    return TextFieldValue(
        text = value,
        selection = TextRange(value.length),
    )
}

@Composable
private fun LibraryBrowserToolbarActions(
    availableSourceFilters: List<LibrarySourceFilter>,
    selectedSourceFilter: LibrarySourceFilter,
    onlineSourceOptions: List<OnlineSourceOption>,
    selectedOnlineSourceId: String?,
    sourceFilterMenuExpanded: Boolean,
    onSourceFilterMenuExpandedChange: (Boolean) -> Unit,
    onSourceFilterChanged: (LibrarySourceFilter) -> Unit,
    onOnlineSourceSelected: (String) -> Unit,
    showTrackSortMenu: Boolean,
    selectedTrackSortMode: TrackSortMode,
    trackSortMenuExpanded: Boolean,
    onTrackSortMenuExpandedChange: (Boolean) -> Unit,
    onTrackSortChanged: (TrackSortMode) -> Unit,
    actionButton: (@Composable () -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            IconButton(onClick = { onSourceFilterMenuExpandedChange(true) }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = uiString(Res.string.library_select_source),
                )
            }
            LibrarySourceFilterDropdownMenu(
                expanded = sourceFilterMenuExpanded,
                availableSourceFilters = availableSourceFilters,
                selectedSourceFilter = selectedSourceFilter,
                onlineSourceOptions = onlineSourceOptions,
                selectedOnlineSourceId = selectedOnlineSourceId,
                onDismiss = { onSourceFilterMenuExpandedChange(false) },
                onSourceFilterChanged = { filter ->
                    onSourceFilterMenuExpandedChange(false)
                    onSourceFilterChanged(filter)
                },
                onOnlineSourceSelected = { sourceId ->
                    onSourceFilterMenuExpandedChange(false)
                    onOnlineSourceSelected(sourceId)
                },
            )
        }
        if (showTrackSortMenu) {
            Box {
                IconButton(onClick = { onTrackSortMenuExpandedChange(true) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = uiString(Res.string.library_sort_tracks),
                    )
                }
                TrackSortDropdownMenu(
                    expanded = trackSortMenuExpanded,
                    selectedTrackSortMode = selectedTrackSortMode,
                    onDismiss = { onTrackSortMenuExpandedChange(false) },
                    onTrackSortChanged = { mode ->
                        onTrackSortMenuExpandedChange(false)
                        onTrackSortChanged(mode)
                    },
                )
            }
        }
        actionButton?.invoke()
    }
}

@Composable
private fun librarySourceFilterButtonLabel(filter: LibrarySourceFilter): String {
    return when (filter) {
        LibrarySourceFilter.ALL -> uiString(Res.string.source_filter_all)
        LibrarySourceFilter.LOCAL_FOLDER -> uiString(Res.string.source_local_folder_label)
        LibrarySourceFilter.SAMBA -> "Samba"
        LibrarySourceFilter.WEBDAV -> "WebDAV"
        LibrarySourceFilter.NAVIDROME -> "Navidrome"
        LibrarySourceFilter.SUBSONIC -> "Subsonic"
        LibrarySourceFilter.EMBY -> "Emby"
        LibrarySourceFilter.FN_MUSIC -> uiString(Res.string.fn_music_name)
        LibrarySourceFilter.DOWNLOADED -> uiString(Res.string.offline_downloaded_status)
    }
}

@Composable
private fun librarySourceFilterMenuLabel(filter: LibrarySourceFilter): String {
    return when (filter) {
        LibrarySourceFilter.ALL -> uiString(Res.string.common_all)
        else -> librarySourceFilterButtonLabel(filter)
    }
}

@Composable
private fun LibrarySourceFilterDropdownMenu(
    expanded: Boolean,
    availableSourceFilters: List<LibrarySourceFilter>,
    selectedSourceFilter: LibrarySourceFilter,
    onlineSourceOptions: List<OnlineSourceOption>,
    selectedOnlineSourceId: String?,
    onDismiss: () -> Unit,
    onSourceFilterChanged: (LibrarySourceFilter) -> Unit,
    onOnlineSourceSelected: (String) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = mainShellColors.navContainer,
    ) {
        availableSourceFilters.forEach { filter ->
            val isSelected = selectedOnlineSourceId == null && filter == selectedSourceFilter
            DropdownMenuItem(
                text = { Text(librarySourceFilterMenuLabel(filter)) },
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
        onlineSourceOptions.forEach { option ->
            val isSelected = option.sourceId == selectedOnlineSourceId
            DropdownMenuItem(
                text = { Text(option.label.displayText()) },
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
}

@Composable
private fun TrackSortDropdownMenu(
    expanded: Boolean,
    selectedTrackSortMode: TrackSortMode,
    onDismiss: () -> Unit,
    onTrackSortChanged: (TrackSortMode) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = mainShellColors.navContainer,
    ) {
        TrackSortMode.entries.forEach { mode ->
            val isSelected = mode == selectedTrackSortMode
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

@Composable
internal fun trackSortModeLabel(mode: TrackSortMode): String {
    return when (mode) {
        TrackSortMode.TITLE -> uiString(Res.string.common_title)
        TrackSortMode.ARTIST -> uiString(Res.string.library_artists_title)
        TrackSortMode.ALBUM -> uiString(Res.string.library_albums_title)
        TrackSortMode.PLAY_COUNT -> uiString(Res.string.library_play_count_sort)
        TrackSortMode.ADDED_AT -> uiString(Res.string.library_date_added_sort)
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
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    var pendingDeleteSourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var failureDetailSummary by remember { mutableStateOf<ImportScanSummary?>(null) }
    var showLocalFolderPickerModeDialog by rememberSaveable { mutableStateOf(false) }
    var showAddSourceMenu by remember { mutableStateOf(false) }
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
    val activeScanOperation = state.activeScanOperation
    val activeScanProgress = state.scanProgress
    val activeScanSourceLabel = remember(state.sources, activeScanProgress?.sourceId) {
        activeScanProgress?.sourceId?.let { sourceId ->
            state.sources.firstOrNull { it.source.id == sourceId }?.source?.label
        }
    }
    val localFolderClickAction = remember(platform.name, state.capabilities.supportsSystemLocalFolderPicker) {
        resolveLocalFolderImportClickAction(platform)
    }
    LaunchedEffect(state.isWorking) {
        if (state.isWorking) {
            showLocalFolderPickerModeDialog = false
            showAddSourceMenu = false
        }
    }
    val startLocalFolderImport = {
        when (localFolderClickAction) {
            LocalFolderImportClickAction.ShowPickerModeDialog -> {
                showLocalFolderPickerModeDialog = true
            }

            LocalFolderImportClickAction.ImportBuiltIn -> {
                onImportIntent(
                    ImportIntent.ImportLocalFolderWithPickerMode(LocalFolderPickerMode.BuiltIn),
                )
            }

            LocalFolderImportClickAction.ImportAutomatic -> {
                onImportIntent(ImportIntent.ImportLocalFolder)
            }
        }
    }
    state.editingSource?.let { editingSource ->
        val editingSourceStatus = state.sources.firstOrNull { it.source.id == editingSource.sourceId }
        val editingScanProgress = state.scanProgress?.takeIf {
            activeScanOperation == ImportScanOperation.UpdateRemote(editingSource.sourceId) &&
                it.sourceId == editingSource.sourceId
        }
        RemoteSourceEditorDialog(
            state = editingSource,
            isWorking = state.isWorking,
            isSavingScan = activeScanOperation == ImportScanOperation.UpdateRemote(editingSource.sourceId),
            sourceIndexMode = editingSourceStatus?.source?.indexMode ?: ImportSourceIndexMode.LOCAL_INDEX,
            currentTrackCount = editingSourceStatus?.indexState?.trackCount,
            remoteTrackCount = editingSourceStatus?.indexState?.remoteTrackCount,
            scanProgress = editingScanProgress,
            constrainWidth = !isMobileSourcesPlatform(platform),
            testMessage = state.testMessage?.displayText(),
            folderTree = state.remoteFolderTree,
            allowManualSambaShare = state.capabilities.supportsSambaImport,
            fieldColors = importFieldColors,
            onDismiss = { onImportIntent(ImportIntent.DismissRemoteSourceEditor) },
            onIntent = onImportIntent,
        )
    }
    state.creatingSourceType?.let { creatingType ->
        val isCreating = activeScanOperation == ImportScanOperation.CreateRemote(creatingType)
        RemoteSourceCreatorDialog(
            type = creatingType,
            state = state,
            isCreating = isCreating,
            scanProgress = state.scanProgress?.takeIf { isCreating },
            constrainWidth = !isMobileSourcesPlatform(platform),
            fieldColors = importFieldColors,
            onIntent = onImportIntent,
        )
    }
    if (showLocalFolderPickerModeDialog) {
        LocalFolderPickerModeDialog(
            isWorking = state.isWorking,
            onDismiss = { showLocalFolderPickerModeDialog = false },
            onSelectMode = { mode ->
                showLocalFolderPickerModeDialog = false
                onImportIntent(ImportIntent.ImportLocalFolderWithPickerMode(mode))
            },
        )
    }
    pendingDeleteSource?.let { source ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSourceId = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = shellColors.cardContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(uiString(Res.string.source_delete_action)) },
            text = { Text(uiString(Res.string.source_delete_confirmation, source.source.label)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteSourceId = null
                        onImportIntent(ImportIntent.DeleteSource(source.source.id))
                    },
                    enabled = !state.isWorking,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(uiString(Res.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteSourceId = null },
                    enabled = !state.isWorking,
                ) {
                    Text(uiString(Res.string.common_cancel))
                }
            },
        )
    }
    failureDetailSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { failureDetailSummary = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = shellColors.cardContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(uiString(Res.string.import_scan_failures_title)) },
            text = {
                if (summary.failures.isEmpty()) {
                    Text(uiString(Res.string.import_scan_failures_empty_hint))
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(summary.failures) { failure ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(failure.relativePath, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    failure.reasonUiText().displayText(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { failureDetailSummary = null }) {
                    Text(uiString(Res.string.common_got_it))
                }
            },
        )
    }
    state.pendingLargeNavidromeImport?.let { pending ->
        val action = pending.action
        LargeNavidromeLibraryDialog(
            trackCount = pending.remoteTrackCount,
            sourceLabel = (action as? PendingLargeNavidromeAction.Rescan)?.sourceLabel,
            isRescan = action is PendingLargeNavidromeAction.Rescan,
            isWorking = state.isWorking,
            onDismiss = { onImportIntent(ImportIntent.DismissLargeNavidromeChoice) },
            onUseOnline = { onImportIntent(ImportIntent.ConfirmLargeNavidromeOnlineMode) },
            onImportAll = { onImportIntent(ImportIntent.ConfirmLargeNavidromeFullImport) },
        )
    }
    state.testMessage?.let { message ->
        LaunchedEffect(message) {
            delay(5_000)
            onImportIntent(ImportIntent.ClearTestMessage)
        }
    }
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.message?.let { message ->
                BannerCard(message = message, onDismiss = { onImportIntent(ImportIntent.ClearMessage) })
            }
            activeScanProgress?.let { progress ->
                ImportScanProgressCard(
                    progress = progress,
                    sourceLabel = activeScanSourceLabel,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SectionTitle(title = uiString(Res.string.source_connected_list_title), subtitle = uiString(Res.string.source_management_hint))
                }
                Box {
                    FilledTonalButton(
                        onClick = { showAddSourceMenu = true },
                        enabled = !state.isWorking,
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(uiString(Res.string.source_add_action))
                    }
                    AddSourceTypeMenu(
                        expanded = showAddSourceMenu,
                        capabilities = state.capabilities,
                        onDismiss = { showAddSourceMenu = false },
                        onSelect = { type ->
                            showAddSourceMenu = false
                            if (type == ImportSourceType.LOCAL_FOLDER) {
                                startLocalFolderImport()
                            } else {
                                onImportIntent(ImportIntent.OpenRemoteSourceCreator(type))
                            }
                        },
                    )
                }
            }
            if (state.sources.isEmpty()) {
                EmptyStateCard(
                    title = uiString(Res.string.sources_empty_title),
                    body = uiString(Res.string.library_source_import_hint),
                )
            } else {
                state.sources.forEach { source ->
                    SourceCard(
                        state = source,
                        enabled = !state.isWorking,
                        compact = compact,
                        onEdit = if (source.source.type == ImportSourceType.LOCAL_FOLDER) {
                            if (state.capabilities.supportsLocalFolderReauthorization) {
                                { onImportIntent(ImportIntent.ReauthorizeLocalFolder(source.source.id)) }
                            } else {
                                null
                            }
                        } else {
                            { onImportIntent(ImportIntent.OpenRemoteSourceEditor(source.source.id)) }
                        },
                        editLabel = if (source.source.type == ImportSourceType.LOCAL_FOLDER) uiString(Res.string.source_reauthorize_folder) else uiString(Res.string.common_edit),
                        onToggleEnabled = {
                            onImportIntent(
                                ImportIntent.ToggleSourceEnabled(
                                    sourceId = source.source.id,
                                    enabled = !source.source.enabled,
                                ),
                            )
                        },
                        onRescan = if (source.source.enabled) {
                            { onImportIntent(ImportIntent.RescanSource(source.source.id)) }
                        } else {
                            null
                        },
                        isRescanning = activeScanOperation == ImportScanOperation.RescanSource(source.source.id),
                        onDelete = { pendingDeleteSourceId = source.source.id },
                        scanSummary = state.latestScanSummariesBySourceId[source.source.id],
                        scanProgress = state.scanProgress?.takeIf {
                            (activeScanOperation == ImportScanOperation.RescanSource(source.source.id) ||
                                activeScanOperation == ImportScanOperation.ReauthorizeLocalFolder(source.source.id)) &&
                                it.sourceId == source.source.id
                        },
                        onShowScanFailures = { failureDetailSummary = it },
                    )
                }
            }
        }
        if (state.editingSource == null && state.creatingSourceType == null) {
            state.testMessage?.let { message ->
                ToastCard(
                    message = message,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun SubsonicAuthModeSelector(
    selected: SubsonicAuthMode,
    enabled: Boolean,
    onSelect: (SubsonicAuthMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SubsonicAuthMode.entries.forEach { mode ->
            val label = when (mode) {
                SubsonicAuthMode.PASSWORD -> uiString(Res.string.source_username_password_authentication)
                SubsonicAuthMode.API_KEY -> "API Key"
            }
            if (mode == selected) {
                Button(
                    onClick = { onSelect(mode) },
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(label)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelect(mode) },
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(label)
                }
            }
        }
    }
}

/** FN Music's connection choice: LAN/WAN server addresses, or an FN ID resolved through FN Connect. */
@Composable
private fun FnMusicConnectionFields(
    connectionMode: FnMusicConnectionMode,
    baseUrl: String,
    wanBaseUrl: String,
    fnId: String,
    accessCode: String,
    isEditing: Boolean,
    enabled: Boolean,
    fieldColors: androidx.compose.material3.TextFieldColors,
    onConnectionModeChange: (FnMusicConnectionMode) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onWanBaseUrlChange: (String) -> Unit,
    onFnIdChange: (String) -> Unit,
    onAccessCodeChange: (String) -> Unit,
) {
    Text(uiString(Res.string.fn_music_connection_mode_label), fontWeight = FontWeight.Medium)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FnMusicConnectionMode.entries.forEach { mode ->
            val label = when (mode) {
                FnMusicConnectionMode.ADDRESS -> uiString(Res.string.fn_music_connection_mode_address)
                FnMusicConnectionMode.FN_CONNECT -> uiString(Res.string.fn_music_connection_mode_fn_connect)
            }
            if (mode == connectionMode) {
                Button(
                    onClick = { onConnectionModeChange(mode) },
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(label)
                }
            } else {
                OutlinedButton(
                    onClick = { onConnectionModeChange(mode) },
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(label)
                }
            }
        }
    }
    when (connectionMode) {
        FnMusicConnectionMode.ADDRESS -> {
            ImeAwareOutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text(uiString(Res.string.source_lan_address_label)) },
                placeholder = { Text("http://192.168.1.2:5666") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = fieldColors,
            )
            ImeAwareOutlinedTextField(
                value = wanBaseUrl,
                onValueChange = onWanBaseUrlChange,
                label = { Text(uiString(Res.string.source_wan_address_label)) },
                placeholder = { Text("https://music.example.com") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = fieldColors,
            )
        }

        FnMusicConnectionMode.FN_CONNECT -> {
            ImeAwareOutlinedTextField(
                value = fnId,
                onValueChange = onFnIdChange,
                label = { Text(uiString(Res.string.fn_music_fn_id_label)) },
                placeholder = { Text(uiString(Res.string.fn_music_fn_id_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = fieldColors,
            )
            ImeAwareOutlinedTextField(
                value = accessCode,
                onValueChange = onAccessCodeChange,
                label = {
                    Text(
                        if (isEditing) {
                            uiString(Res.string.fn_music_access_code_keep_hint)
                        } else {
                            uiString(Res.string.fn_music_access_code_label)
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = fieldColors,
            )
        }
    }
}

@Composable
private fun LargeNavidromeLibraryDialog(
    trackCount: Int,
    sourceLabel: String?,
    isRescan: Boolean,
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onUseOnline: () -> Unit,
    onImportAll: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isWorking) onDismiss()
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = mainShellColors.cardContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(uiString(Res.string.source_navidrome_large_library_title)) },
        text = {
            Text(
                if (isRescan) {
                    uiPlural(Res.plurals.source_online_rescan_confirmation, trackCount, sourceLabel.orEmpty().ifBlank { uiString(Res.string.source_navidrome_label) }, trackCount).displayText()
                } else {
                    uiString(Res.plurals.source_online_import_all_hint, (trackCount).toInt(), trackCount)
                },
            )
        },
        confirmButton = {
            Button(
                onClick = onUseOnline,
                enabled = !isWorking,
            ) {
                Text(if (isWorking) uiString(Res.string.common_processing) else uiString(Res.string.source_online_mode_label))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isWorking,
                ) {
                    Text(uiString(Res.string.common_cancel))
                }
                TextButton(
                    onClick = onImportAll,
                    enabled = !isWorking,
                ) {
                    Text(if (isRescan) uiString(Res.string.source_continue_rescan) else uiString(Res.string.source_import_all_action))
                }
            }
        },
    )
}

@Composable
private fun LocalFolderPickerModeDialog(
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onSelectMode: (LocalFolderPickerMode) -> Unit,
) {
    val appDensity = LocalDensity.current
    Dialog(
        onDismissRequest = {
            if (!isWorking) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalDensity provides appDensity) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                MainShellElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .widthIn(max = 460.dp)
                        .heightIn(max = 560.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = uiString(Res.string.source_folder_manager_title),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = uiString(Res.string.source_system_folder_manager_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = { onSelectMode(localFolderPickerDialogSystemMode()) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isWorking,
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                            ) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(uiString(Res.string.source_system_folder_manager))
                            }
                            OutlinedButton(
                                onClick = { onSelectMode(LocalFolderPickerMode.BuiltIn) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isWorking,
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                            ) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(uiString(Res.string.source_builtin_folder_manager))
                            }
                        }
                        TextButton(
                            onClick = onDismiss,
                            enabled = !isWorking,
                            modifier = Modifier.align(Alignment.End),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            Text(uiString(Res.string.common_cancel))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportScanProgressCard(
    progress: ImportScanProgress,
    sourceLabel: String?,
) {
    MainShellElevatedCard(shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (progress.phase == ImportScanPhase.Persisting) {
                    sourceLabel?.let { uiString(Res.string.source_updating_named_type, it) } ?: uiString(Res.string.library_updating_status)
                } else {
                    sourceLabel?.let { uiString(Res.string.source_scanning_named_type, it) } ?: uiString(Res.string.source_scanning_title)
                },
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val fraction = importScanProgressFraction(progress)
            if (fraction == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = importScanProgressLabel(progress),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

internal fun localFolderPickerDialogSystemMode(): LocalFolderPickerMode {
    return LocalFolderPickerMode.Automatic
}

internal enum class LocalFolderImportClickAction {
    ShowPickerModeDialog,
    ImportBuiltIn,
    ImportAutomatic,
}

internal fun resolveLocalFolderImportClickAction(platform: PlatformDescriptor): LocalFolderImportClickAction {
    if (!platform.supportsLocalFolderPickerModeChoice()) {
        return LocalFolderImportClickAction.ImportAutomatic
    }
    return if (platform.capabilities.supportsSystemLocalFolderPicker) {
        LocalFolderImportClickAction.ShowPickerModeDialog
    } else {
        LocalFolderImportClickAction.ImportBuiltIn
    }
}

internal fun PlatformDescriptor.supportsLocalFolderPickerModeChoice(): Boolean {
    return name == ANDROID_PLATFORM_NAME || isAndroidAutomotivePlatform()
}

@Composable
private fun AddSourceTypeMenu(
    expanded: Boolean,
    capabilities: PlatformCapabilities,
    onDismiss: () -> Unit,
    onSelect: (ImportSourceType) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = mainShellColors.navContainer,
    ) {
        ADD_SOURCE_MENU_TYPES.forEach { type ->
            DropdownMenuItem(
                text = { Text(addSourceTypeLabel(type)) },
                leadingIcon = {
                    Icon(
                        imageVector = if (type == ImportSourceType.LOCAL_FOLDER) Icons.Rounded.FolderOpen else Icons.Rounded.CloudSync,
                        contentDescription = null,
                    )
                },
                enabled = capabilities.supportsAddingSource(type),
                onClick = { onSelect(type) },
            )
        }
    }
}

private val ADD_SOURCE_MENU_TYPES = listOf(
    ImportSourceType.LOCAL_FOLDER,
    ImportSourceType.NAVIDROME,
    ImportSourceType.SUBSONIC,
    ImportSourceType.EMBY,
    ImportSourceType.FN_MUSIC,
    ImportSourceType.SAMBA,
    ImportSourceType.WEBDAV,
)

/** Samba stays selectable everywhere: platforms without in-app SMB show a system-mount hint in its form instead. */
private fun PlatformCapabilities.supportsAddingSource(type: ImportSourceType): Boolean = when (type) {
    ImportSourceType.LOCAL_FOLDER -> supportsLocalFolderImport
    ImportSourceType.SAMBA -> true
    ImportSourceType.WEBDAV -> supportsWebDavImport
    ImportSourceType.NAVIDROME -> supportsNavidromeImport
    ImportSourceType.SUBSONIC -> supportsSubsonicImport
    ImportSourceType.EMBY -> supportsEmbyImport
    ImportSourceType.FN_MUSIC -> supportsFnMusicImport
}

@Composable
private fun addSourceTypeLabel(type: ImportSourceType): String = when (type) {
    ImportSourceType.LOCAL_FOLDER -> uiString(Res.string.source_local_folder_label)
    ImportSourceType.SAMBA -> "Samba / SMB"
    ImportSourceType.WEBDAV -> "WebDAV"
    ImportSourceType.NAVIDROME -> "Navidrome"
    ImportSourceType.SUBSONIC -> "Subsonic / OpenSubsonic"
    ImportSourceType.EMBY -> "Emby"
    ImportSourceType.FN_MUSIC -> uiString(Res.string.fn_music_name)
}

@Composable
private fun RemoteSourceCreatorDialog(
    type: ImportSourceType,
    state: ImportState,
    isCreating: Boolean,
    scanProgress: ImportScanProgress?,
    constrainWidth: Boolean,
    fieldColors: androidx.compose.material3.TextFieldColors,
    onIntent: (ImportIntent) -> Unit,
) {
    val shellColors = mainShellColors
    val scansFiles = type == ImportSourceType.SAMBA || type == ImportSourceType.WEBDAV
    SourceFormDialog(
        title = uiString(Res.string.source_add_named_type, addSourceTypeLabel(type)),
        subtitle = null,
        isWorking = state.isWorking,
        primaryLabel = when {
            isCreating && scansFiles -> uiString(Res.string.source_scanning_status)
            isCreating -> uiString(Res.string.common_syncing)
            scansFiles -> uiString(Res.string.source_connect_and_scan)
            else -> uiString(Res.string.source_connect_and_sync)
        },
        primaryLoading = isCreating,
        primaryEnabled = state.remoteFolderTree?.selected?.isNotEmpty() ?: true,
        scanProgress = scanProgress,
        constrainWidth = constrainWidth,
        testMessage = state.testMessage?.displayText(),
        onDismiss = { onIntent(ImportIntent.DismissRemoteSourceCreator) },
        onTest = {
            when (type) {
                ImportSourceType.SAMBA -> ImportIntent.TestSambaSource
                ImportSourceType.WEBDAV -> ImportIntent.TestWebDavSource
                ImportSourceType.NAVIDROME -> ImportIntent.TestNavidromeSource
                ImportSourceType.SUBSONIC -> ImportIntent.TestSubsonicSource
                ImportSourceType.EMBY -> ImportIntent.TestEmbySource
                ImportSourceType.FN_MUSIC -> ImportIntent.TestFnMusicSource
                ImportSourceType.LOCAL_FOLDER -> null
            }?.let(onIntent)
        },
        onPrimary = {
            when (type) {
                ImportSourceType.SAMBA -> ImportIntent.AddSambaSource
                ImportSourceType.WEBDAV -> ImportIntent.AddWebDavSource
                ImportSourceType.NAVIDROME -> ImportIntent.AddNavidromeSource
                ImportSourceType.SUBSONIC -> ImportIntent.AddSubsonicSource
                ImportSourceType.EMBY -> ImportIntent.AddEmbySource
                ImportSourceType.FN_MUSIC -> ImportIntent.AddFnMusicSource
                ImportSourceType.LOCAL_FOLDER -> null
            }?.let(onIntent)
        },
    ) {
        when (type) {
            ImportSourceType.NAVIDROME -> {
                ImeAwareOutlinedTextField(
                    value = state.navidromeLabel,
                    onValueChange = { onIntent(ImportIntent.NavidromeLabelChanged(it)) },
                    label = { Text(uiString(Res.string.common_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.navidromeBaseUrl,
                    onValueChange = { onIntent(ImportIntent.NavidromeBaseUrlChanged(it)) },
                    label = { Text(uiString(Res.string.source_lan_address_label)) },
                    placeholder = { Text("http://192.168.31.115:32768") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.navidromeWanBaseUrl,
                    onValueChange = { onIntent(ImportIntent.NavidromeWanBaseUrlChanged(it)) },
                    label = { Text(uiString(Res.string.source_wan_address_label)) },
                    placeholder = { Text("https://music.example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImeAwareOutlinedTextField(
                        value = state.navidromeUsername,
                        onValueChange = { onIntent(ImportIntent.NavidromeUsernameChanged(it)) },
                        label = { Text(uiString(Res.string.common_username)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors,
                    )
                    ImeAwareOutlinedTextField(
                        value = state.navidromePassword,
                        onValueChange = { onIntent(ImportIntent.NavidromePasswordChanged(it)) },
                        label = { Text(uiString(Res.string.common_password)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors,
                    )
                }
            }

            ImportSourceType.SUBSONIC -> {
                ImeAwareOutlinedTextField(
                    value = state.subsonicLabel,
                    onValueChange = { onIntent(ImportIntent.SubsonicLabelChanged(it)) },
                    label = { Text(uiString(Res.string.common_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.subsonicBaseUrl,
                    onValueChange = { onIntent(ImportIntent.SubsonicBaseUrlChanged(it)) },
                    label = { Text(uiString(Res.string.source_lan_address_label)) },
                    placeholder = { Text("https://music.example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.subsonicWanBaseUrl,
                    onValueChange = { onIntent(ImportIntent.SubsonicWanBaseUrlChanged(it)) },
                    label = { Text(uiString(Res.string.source_wan_address_label)) },
                    placeholder = { Text("https://music.example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors,
                )
                SubsonicAuthModeSelector(
                    selected = state.subsonicAuthMode,
                    enabled = !state.isWorking,
                    onSelect = { onIntent(ImportIntent.SubsonicAuthModeChanged(it)) },
                )
                if (state.subsonicAuthMode == SubsonicAuthMode.PASSWORD) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ImeAwareOutlinedTextField(
                            value = state.subsonicUsername,
                            onValueChange = { onIntent(ImportIntent.SubsonicUsernameChanged(it)) },
                            label = { Text(uiString(Res.string.common_username)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = fieldColors,
                        )
                        ImeAwareOutlinedTextField(
                            value = state.subsonicCredential,
                            onValueChange = { onIntent(ImportIntent.SubsonicCredentialChanged(it)) },
                            label = { Text(uiString(Res.string.common_password)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = fieldColors,
                        )
                    }
                } else {
                    ImeAwareOutlinedTextField(
                        value = state.subsonicCredential,
                        onValueChange = { onIntent(ImportIntent.SubsonicCredentialChanged(it)) },
                        label = { Text(uiString(Res.string.settings_api_key)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors,
                    )
                }
            }

            ImportSourceType.EMBY -> {
                ImeAwareOutlinedTextField(
                    value = state.embyLabel,
                    onValueChange = { onIntent(ImportIntent.EmbyLabelChanged(it)) },
                    label = { Text(uiString(Res.string.common_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.embyBaseUrl,
                    onValueChange = { onIntent(ImportIntent.EmbyBaseUrlChanged(it)) },
                    label = { Text(uiString(Res.string.source_lan_address_label)) },
                    placeholder = { Text("https://emby.example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.embyWanBaseUrl,
                    onValueChange = { onIntent(ImportIntent.EmbyWanBaseUrlChanged(it)) },
                    label = { Text(uiString(Res.string.source_wan_address_label)) },
                    placeholder = { Text("https://music.example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImeAwareOutlinedTextField(
                        value = state.embyUsername,
                        onValueChange = { onIntent(ImportIntent.EmbyUsernameChanged(it)) },
                        label = { Text(uiString(Res.string.common_username)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors,
                    )
                    ImeAwareOutlinedTextField(
                        value = state.embyPassword,
                        onValueChange = { onIntent(ImportIntent.EmbyPasswordChanged(it)) },
                        label = { Text(uiString(Res.string.common_password)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors,
                    )
                }
            }

            ImportSourceType.FN_MUSIC -> {
                ImeAwareOutlinedTextField(
                    value = state.fnMusicLabel,
                    onValueChange = { onIntent(ImportIntent.FnMusicLabelChanged(it)) },
                    label = { Text(uiString(Res.string.common_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors,
                )
                FnMusicConnectionFields(
                    connectionMode = state.fnMusicConnectionMode,
                    baseUrl = state.fnMusicBaseUrl,
                    wanBaseUrl = state.fnMusicWanBaseUrl,
                    fnId = state.fnMusicId,
                    accessCode = state.fnMusicAccessCode,
                    isEditing = false,
                    enabled = !state.isWorking,
                    fieldColors = fieldColors,
                    onConnectionModeChange = { onIntent(ImportIntent.FnMusicConnectionModeChanged(it)) },
                    onBaseUrlChange = { onIntent(ImportIntent.FnMusicBaseUrlChanged(it)) },
                    onWanBaseUrlChange = { onIntent(ImportIntent.FnMusicWanBaseUrlChanged(it)) },
                    onFnIdChange = { onIntent(ImportIntent.FnMusicIdChanged(it)) },
                    onAccessCodeChange = { onIntent(ImportIntent.FnMusicAccessCodeChanged(it)) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImeAwareOutlinedTextField(
                        value = state.fnMusicUsername,
                        onValueChange = { onIntent(ImportIntent.FnMusicUsernameChanged(it)) },
                        label = { Text(uiString(Res.string.common_username)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors,
                    )
                    ImeAwareOutlinedTextField(
                        value = state.fnMusicPassword,
                        onValueChange = { onIntent(ImportIntent.FnMusicPasswordChanged(it)) },
                        label = { Text(uiString(Res.string.common_password)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors,
                    )
                }
            }

            ImportSourceType.SAMBA -> {
                if (!state.capabilities.supportsSambaImport) {
                    Text(uiString(Res.string.source_samba_system_mount_hint))
                }
                ImeAwareOutlinedTextField(
                    value = state.sambaLabel,
                    onValueChange = { onIntent(ImportIntent.SambaLabelChanged(it)) },
                    label = { Text(uiString(Res.string.common_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImeAwareOutlinedTextField(
                        value = state.sambaServer,
                        onValueChange = { onIntent(ImportIntent.SambaServerChanged(it)) },
                        label = { Text(uiString(Res.string.common_server_address)) },
                        placeholder = { Text("192.168.31.115") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors
                    )
                    ImeAwareOutlinedTextField(
                        value = state.sambaPort,
                        onValueChange = { onIntent(ImportIntent.SambaPortChanged(it)) },
                        label = { Text(uiString(Res.string.common_port)) },
                        placeholder = { Text("445") },
                        modifier = Modifier.width(140.dp),
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImeAwareOutlinedTextField(
                        value = state.sambaUsername,
                        onValueChange = { onIntent(ImportIntent.SambaUsernameChanged(it)) },
                        label = { Text(uiString(Res.string.common_username)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors
                    )
                    ImeAwareOutlinedTextField(
                        value = state.sambaPassword,
                        onValueChange = { onIntent(ImportIntent.SambaPasswordChanged(it)) },
                        label = { Text(uiString(Res.string.source_optional_password_placeholder)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors
                    )
                }
                state.remoteFolderTree?.let { tree ->
                    RemoteFolderTreeSection(
                        tree = tree,
                        rootName = formatSambaEndpoint(state.sambaServer, state.sambaPort.toIntOrNull(), null),
                        enabled = !state.isWorking,
                        allowManualShare = state.capabilities.supportsSambaImport,
                        fieldColors = fieldColors,
                        onIntent = onIntent,
                    )
                }
            }

            ImportSourceType.WEBDAV -> {
                ImeAwareOutlinedTextField(
                    value = state.webDavLabel,
                    onValueChange = { onIntent(ImportIntent.WebDavLabelChanged(it)) },
                    label = { Text(uiString(Res.string.common_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.webDavRootUrl,
                    onValueChange = { onIntent(ImportIntent.WebDavRootUrlChanged(it)) },
                    label = { Text(uiString(Res.string.source_server_address_label)) },
                    placeholder = { Text(uiString(Res.string.source_webdav_url_example)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImeAwareOutlinedTextField(
                        value = state.webDavUsername,
                        onValueChange = { onIntent(ImportIntent.WebDavUsernameChanged(it)) },
                        label = { Text(uiString(Res.string.source_optional_username_placeholder)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors,
                    )
                    ImeAwareOutlinedTextField(
                        value = state.webDavPassword,
                        onValueChange = { onIntent(ImportIntent.WebDavPasswordChanged(it)) },
                        label = { Text(uiString(Res.string.source_optional_password_placeholder)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(uiString(Res.string.source_self_signed_certificates_label), fontWeight = FontWeight.Medium)
                    Switch(
                        checked = state.webDavAllowInsecureTls,
                        onCheckedChange = {
                            onIntent(
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
                state.remoteFolderTree?.let { tree ->
                    RemoteFolderTreeSection(
                        tree = tree,
                        rootName = state.webDavRootUrl.trim(),
                        enabled = !state.isWorking,
                        allowManualShare = false,
                        fieldColors = fieldColors,
                        onIntent = onIntent,
                    )
                }
            }

            ImportSourceType.LOCAL_FOLDER -> Unit
        }
    }
}

@Composable
private fun RemoteSourceEditorDialog(
    state: top.iwesley.lyn.music.feature.importing.RemoteSourceEditorState,
    isWorking: Boolean,
    isSavingScan: Boolean,
    sourceIndexMode: ImportSourceIndexMode,
    currentTrackCount: Int?,
    remoteTrackCount: Int?,
    scanProgress: ImportScanProgress?,
    constrainWidth: Boolean,
    testMessage: String?,
    folderTree: RemoteFolderTreeState?,
    allowManualSambaShare: Boolean,
    fieldColors: androidx.compose.material3.TextFieldColors,
    onDismiss: () -> Unit,
    onIntent: (ImportIntent) -> Unit,
) {
    val shellColors = mainShellColors
    SourceFormDialog(
        title = when (state.type) {
            ImportSourceType.SAMBA -> uiString(Res.string.source_edit_samba_title)
            ImportSourceType.WEBDAV -> uiString(Res.string.source_edit_webdav_title)
            ImportSourceType.NAVIDROME -> uiString(Res.string.source_edit_navidrome_title)
            ImportSourceType.SUBSONIC -> uiString(Res.string.source_edit_subsonic_title)
            ImportSourceType.EMBY -> uiString(Res.string.source_edit_emby_title)
            ImportSourceType.FN_MUSIC -> uiString(Res.string.source_edit_fn_music_title)
            ImportSourceType.LOCAL_FOLDER -> uiString(Res.string.source_edit_title)
        },
        subtitle = remoteSourceEditorTrackCountLabel(
            indexMode = sourceIndexMode,
            currentTrackCount = currentTrackCount,
            remoteTrackCount = remoteTrackCount,
        ),
        isWorking = isWorking,
        primaryLabel = if (isSavingScan) uiString(Res.string.source_rescanning_status) else uiString(Res.string.source_save_and_rescan),
        primaryLoading = isSavingScan,
        primaryEnabled = folderTree?.selected?.isNotEmpty() ?: true,
        scanProgress = scanProgress,
        constrainWidth = constrainWidth,
        testMessage = testMessage,
        onDismiss = onDismiss,
        onTest = { onIntent(ImportIntent.TestRemoteSource) },
        onPrimary = { onIntent(ImportIntent.SaveRemoteSource) },
    ) {
        if (state.hasStoredCredential) {
            Text(
                uiString(Res.string.source_saved_credentials_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ImeAwareOutlinedTextField(
            value = state.label,
            onValueChange = { onIntent(ImportIntent.RemoteSourceLabelChanged(it)) },
            label = { Text(uiString(Res.string.common_name)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = fieldColors,
        )
        when (state.type) {
            ImportSourceType.SAMBA -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImeAwareOutlinedTextField(
                        value = state.server,
                        onValueChange = { onIntent(ImportIntent.RemoteSourceServerChanged(it)) },
                        label = { Text(uiString(Res.string.common_server_address)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors,
                    )
                    ImeAwareOutlinedTextField(
                        value = state.port,
                        onValueChange = { onIntent(ImportIntent.RemoteSourcePortChanged(it)) },
                        label = { Text(uiString(Res.string.common_port)) },
                        modifier = Modifier.width(140.dp),
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors,
                    )
                }
            }

            ImportSourceType.WEBDAV -> {
                ImeAwareOutlinedTextField(
                    value = state.rootUrl,
                    onValueChange = { onIntent(ImportIntent.RemoteSourceRootUrlChanged(it)) },
                    label = { Text(uiString(Res.string.source_webdav_root_url_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors,
                )
            }

            ImportSourceType.NAVIDROME,
            ImportSourceType.SUBSONIC,
            ImportSourceType.EMBY,
            -> {
                ImeAwareOutlinedTextField(
                    value = state.rootUrl,
                    onValueChange = { onIntent(ImportIntent.RemoteSourceRootUrlChanged(it)) },
                    label = { Text(uiString(Res.string.source_lan_address_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.wanRootUrl,
                    onValueChange = { onIntent(ImportIntent.RemoteSourceWanRootUrlChanged(it)) },
                    label = { Text(uiString(Res.string.source_wan_address_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors,
                )
            }

            ImportSourceType.FN_MUSIC -> FnMusicConnectionFields(
                connectionMode = state.fnMusicConnectionMode,
                baseUrl = state.rootUrl,
                wanBaseUrl = state.wanRootUrl,
                fnId = state.fnMusicId,
                accessCode = state.fnMusicAccessCode,
                isEditing = true,
                enabled = !isWorking,
                fieldColors = fieldColors,
                onConnectionModeChange = { onIntent(ImportIntent.RemoteSourceFnMusicConnectionModeChanged(it)) },
                onBaseUrlChange = { onIntent(ImportIntent.RemoteSourceRootUrlChanged(it)) },
                onWanBaseUrlChange = { onIntent(ImportIntent.RemoteSourceWanRootUrlChanged(it)) },
                onFnIdChange = { onIntent(ImportIntent.RemoteSourceFnMusicIdChanged(it)) },
                onAccessCodeChange = { onIntent(ImportIntent.RemoteSourceFnMusicAccessCodeChanged(it)) },
            )

            ImportSourceType.LOCAL_FOLDER -> Unit
        }
        if (state.type == ImportSourceType.SUBSONIC) {
            SubsonicAuthModeSelector(
                selected = state.subsonicAuthMode,
                enabled = !isWorking,
                onSelect = { onIntent(ImportIntent.RemoteSourceSubsonicAuthModeChanged(it)) },
            )
        }
        if (state.type == ImportSourceType.SUBSONIC && state.subsonicAuthMode == SubsonicAuthMode.API_KEY) {
            ImeAwareOutlinedTextField(
                value = state.password,
                onValueChange = { onIntent(ImportIntent.RemoteSourcePasswordChanged(it)) },
                label = {
                    Text(if (state.hasStoredCredential) uiString(Res.string.source_saved_api_key_placeholder) else "API Key")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = fieldColors,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ImeAwareOutlinedTextField(
                    value = state.username,
                    onValueChange = { onIntent(ImportIntent.RemoteSourceUsernameChanged(it)) },
                    label = {
                        Text(if (state.type == ImportSourceType.WEBDAV) uiString(Res.string.source_optional_username_placeholder) else uiString(Res.string.common_username))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.password,
                    onValueChange = { onIntent(ImportIntent.RemoteSourcePasswordChanged(it)) },
                    label = {
                        Text(if (state.hasStoredCredential) uiString(Res.string.source_saved_password_placeholder) else uiString(Res.string.common_password))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors,
                )
            }
        }
        if (state.type == ImportSourceType.WEBDAV) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(uiString(Res.string.source_self_signed_certificates_label), fontWeight = FontWeight.Medium)
                Switch(
                    checked = state.allowInsecureTls,
                    onCheckedChange = { onIntent(ImportIntent.RemoteSourceAllowInsecureTlsChanged(it)) },
                    colors = SwitchDefaults.colors(
                        uncheckedThumbColor = MaterialTheme.colorScheme.background,
                        uncheckedBorderColor = shellColors.cardBorder,
                    ),
                )
            }
        }
        folderTree?.let { tree ->
            RemoteFolderTreeSection(
                tree = tree,
                rootName = if (state.type == ImportSourceType.SAMBA) {
                    formatSambaEndpoint(state.server, state.port.toIntOrNull(), state.path)
                } else {
                    state.rootUrl.trim()
                },
                enabled = !isWorking,
                allowManualShare = allowManualSambaShare,
                fieldColors = fieldColors,
                onIntent = onIntent,
            )
        }
    }
}

/**
 * Folder picker for Samba/WebDAV sources: browses the server lazily and lets the user tick folders to scan. A ticked
 * folder covers everything below it, so its sub-folders show as ticked and locked.
 */
@Composable
private fun RemoteFolderTreeSection(
    tree: RemoteFolderTreeState,
    rootName: String,
    enabled: Boolean,
    /** Typing a share only helps where Samba scanning works in-app. */
    allowManualShare: Boolean,
    fieldColors: androidx.compose.material3.TextFieldColors,
    onIntent: (ImportIntent) -> Unit,
) {
    val rootNode = tree.nodes[RemoteFolderTreeState.ROOT]
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(uiString(Res.string.source_folders_title), fontWeight = FontWeight.Medium)
                Text(
                    text = if (tree.selected.isEmpty()) {
                        uiString(Res.string.source_folders_hint)
                    } else {
                        uiPlural(Res.plurals.source_selected_folder_count, tree.selected.size, tree.selected.size).displayText()
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(
                onClick = { onIntent(ImportIntent.LoadRemoteFolder()) },
                enabled = enabled && rootNode?.isLoading != true,
            ) {
                Text(uiString(if (tree.isBrowsed) Res.string.source_reload_folders else Res.string.source_browse_folders))
            }
        }
        if (!tree.rootSelectable && rootNode?.isLoading == true) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        tree.visibleRows(rootName).forEach { row ->
            when (row) {
                is RemoteFolderRow.Folder -> RemoteFolderTreeFolderRow(row = row, enabled = enabled, onIntent = onIntent)
                is RemoteFolderRow.Message -> Row(
                    modifier = Modifier.padding(start = (row.depth * 20 + 12).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.text.displayText(),
                        modifier = Modifier.weight(1f, fill = false),
                        color = if (row.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (row.isError) {
                        TextButton(onClick = { onIntent(ImportIntent.LoadRemoteFolder(row.parentPath)) }, enabled = enabled) {
                            Text(uiString(Res.string.source_reload_folders))
                        }
                    }
                }
            }
        }
        if (tree.canAddManualShare && allowManualShare) {
            ManualSambaShareField(enabled = enabled, fieldColors = fieldColors, onIntent = onIntent)
        }
    }
}

@Composable
private fun RemoteFolderTreeFolderRow(
    row: RemoteFolderRow.Folder,
    enabled: Boolean,
    onIntent: (ImportIntent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (row.depth * 20).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (row.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = { onIntent(ImportIntent.ToggleRemoteFolderExpanded(row.path)) }, enabled = enabled) {
                    Icon(
                        imageVector = if (row.isExpanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                    )
                }
            }
        }
        Checkbox(
            checked = row.isChecked,
            onCheckedChange = { onIntent(ImportIntent.ToggleRemoteFolderSelected(row.path)) },
            enabled = enabled && !row.isLockedByAncestor,
        )
        Text(
            text = row.name,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Browse a share by typing its name: hidden shares the listing skips, or any share when listing fails. */
@Composable
private fun ManualSambaShareField(
    enabled: Boolean,
    fieldColors: androidx.compose.material3.TextFieldColors,
    onIntent: (ImportIntent) -> Unit,
) {
    var shareName by remember { mutableStateOf("") }
    Text(
        uiString(Res.string.source_samba_manual_share_hint),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ImeAwareOutlinedTextField(
            value = shareName,
            onValueChange = { shareName = it },
            label = { Text(uiString(Res.string.source_samba_manual_share)) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = fieldColors,
        )
        OutlinedButton(
            onClick = {
                onIntent(ImportIntent.AddManualSambaShare(shareName))
                shareName = ""
            },
            enabled = enabled && shareName.isNotBlank(),
        ) {
            Text(uiString(Res.string.source_samba_manual_share_add))
        }
    }
}

/** Content-sized add/edit dialog with a bounded form and fixed header, actions and feedback. */
@Composable
private fun SourceFormDialog(
    title: String,
    subtitle: String?,
    isWorking: Boolean,
    primaryLabel: String,
    primaryLoading: Boolean,
    primaryEnabled: Boolean = true,
    scanProgress: ImportScanProgress?,
    constrainWidth: Boolean,
    testMessage: String?,
    onDismiss: () -> Unit,
    onTest: () -> Unit,
    onPrimary: () -> Unit,
    fields: @Composable ColumnScope.() -> Unit,
) {
    val appDensity = LocalDensity.current
    Dialog(
        onDismissRequest = {
            if (!isWorking) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalDensity provides appDensity) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                val dialogMaxHeight = maxHeight * 0.9f
                MainShellElevatedCard(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .then(
                            if (constrainWidth) {
                                Modifier
                                    .fillMaxWidth(0.72f)
                                    .widthIn(max = 372.dp)
                            } else {
                                Modifier.fillMaxWidth()
                            },
                        )
                        .heightIn(max = dialogMaxHeight),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                title,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            subtitle?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                        ) {
                            // Budget feedback from the space left after the fixed header and actions.
                            val feedbackSpacing = minOf(8.dp, maxHeight / 4f)
                            val feedbackMaxHeight = minOf(120.dp, (maxHeight - feedbackSpacing) / 3f)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(feedbackSpacing),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    content = fields,
                                )
                                testMessage?.let { message ->
                                    key(message) {
                                        ToastCard(
                                            message = message,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = feedbackMaxHeight)
                                                .verticalScroll(rememberScrollState()),
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                enabled = !isWorking,
                            ) {
                                Text(uiString(Res.string.common_cancel))
                            }
                            Spacer(Modifier.width(12.dp))
                            OutlinedButton(
                                onClick = onTest,
                                enabled = !isWorking,
                            ) {
                                Text(
                                    text = uiString(Res.string.source_test_connection),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = onPrimary,
                                enabled = !isWorking && primaryEnabled,
                            ) {
                                if (primaryLoading) {
                                    ButtonLoadingIndicator()
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = primaryLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        scanProgress?.let { progress ->
                            RemoteSourceEditorScanProgress(progress)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteSourceEditorScanProgress(progress: ImportScanProgress) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val fraction = importScanProgressFraction(progress)
        if (fraction == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = importScanProgressLabel(progress),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

internal fun remoteSourceEditorTrackCountLabelText(
    indexMode: ImportSourceIndexMode,
    currentTrackCount: Int?,
    remoteTrackCount: Int?,
): UiText {
    return if (indexMode == ImportSourceIndexMode.ONLINE) {
        remoteTrackCount?.let { uiPlural(Res.plurals.source_remote_library_track_count, (it.coerceAtLeast(0)).toInt(), it.coerceAtLeast(0)) } ?: uiText(Res.string.library_remote_track_count_unknown)
    } else {
        currentTrackCount?.let { uiPlural(Res.plurals.source_imported_track_count, (it.coerceAtLeast(0)).toInt(), it.coerceAtLeast(0)) } ?: uiText(Res.string.library_imported_tracks_empty_title)
    }
}

@Composable
internal fun remoteSourceEditorTrackCountLabel(
    indexMode: ImportSourceIndexMode,
    currentTrackCount: Int?,
    remoteTrackCount: Int?,
): String = remoteSourceEditorTrackCountLabelText(indexMode, currentTrackCount, remoteTrackCount).displayText()

private fun isMobileSourcesPlatform(platform: PlatformDescriptor): Boolean {
    return platform.name == "Android" || platform.name == "iPhone / iPad"
}
