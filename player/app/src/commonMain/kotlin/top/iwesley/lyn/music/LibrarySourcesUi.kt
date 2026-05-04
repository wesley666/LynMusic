package top.iwesley.lyn.music

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RecentActors
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.ImportScanSummary
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.feature.favorites.FavoritesIntent
import top.iwesley.lyn.music.feature.favorites.FavoritesState
import top.iwesley.lyn.music.feature.importing.ImportIntent
import top.iwesley.lyn.music.feature.importing.ImportScanOperation
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.library.LibraryIntent
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.LibraryState
import top.iwesley.lyn.music.feature.library.TrackSortMode
import top.iwesley.lyn.music.feature.library.deriveVisibleAlbums
import top.iwesley.lyn.music.feature.library.libraryAlbumId
import top.iwesley.lyn.music.feature.library.libraryArtistId
import top.iwesley.lyn.music.feature.offline.OfflineDownloadIntent
import top.iwesley.lyn.music.feature.offline.batchDownloadInsufficientSpaceMessage
import top.iwesley.lyn.music.feature.offline.batchDownloadSizeEstimateLabel
import top.iwesley.lyn.music.feature.offline.estimateBatchDownloadSize
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
    showSearchField: Boolean = true,
    navigationTarget: LibraryNavigationTarget? = null,
    onNavigationHandled: () -> Unit = {},
    onOpenLibraryNavigationTarget: ((LibraryNavigationTarget) -> Unit)? = null,
    batchSelectionRequestKey: Int = 0,
    showInlineBatchOperationButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    LibraryBrowserTab(
        state = LibraryBrowserPageState(
            isLoadingContent = state.isLoadingContent,
            query = state.query,
            tracks = state.tracks,
            filteredTracks = state.filteredTracks,
            filteredAlbums = state.filteredAlbums,
            filteredArtists = state.filteredArtists,
            selectedSourceFilter = state.selectedSourceFilter,
            availableSourceFilters = state.availableSourceFilters,
            selectedTrackSortMode = state.selectedTrackSortMode,
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
        onTrackSortChanged = { onLibraryIntent(LibraryIntent.TrackSortChanged(it)) },
        onToggleFavorite = { onFavoritesIntent(FavoritesIntent.ToggleFavorite(it)) },
        onDismissMessage = { onFavoritesIntent(FavoritesIntent.ClearMessage) },
        onPlayTracks = { tracks, index -> onPlayerIntent(PlayerIntent.PlayTracks(tracks, index)) },
        showFavoriteButton = showFavoriteButton,
        showDuration = showDuration,
        showSearchField = showSearchField,
        showTrackSortActionButton = showSearchField,
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
    onFavoritesIntent: (FavoritesIntent) -> Unit,
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
    LibraryBrowserTab(
        state = LibraryBrowserPageState(
            isLoadingContent = state.isLoadingContent,
            query = state.query,
            tracks = state.tracks,
            filteredTracks = state.filteredTracks,
            filteredAlbums = state.filteredAlbums,
            filteredArtists = state.filteredArtists,
            selectedSourceFilter = state.selectedSourceFilter,
            availableSourceFilters = state.availableSourceFilters,
            selectedTrackSortMode = state.selectedTrackSortMode,
            favoriteTrackIds = state.favoriteTrackIds,
            message = state.message,
        ),
        strings = LibraryBrowserStrings(
            searchLabel = "搜索歌曲 / 艺人 / 专辑",
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
        onTrackSortChanged = { onFavoritesIntent(FavoritesIntent.TrackSortChanged(it)) },
        onToggleFavorite = { onFavoritesIntent(FavoritesIntent.ToggleFavorite(it)) },
        actionButton = if (showRefreshActionButton && state.canRefreshRemote) {
            {
                IconButton(
                    onClick = { onFavoritesIntent(FavoritesIntent.Refresh) },
                    enabled = !state.isRefreshing,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = if (state.isRefreshing) "刷新中" else "刷新",
                    )
                }
            }
        } else {
            null
        },
        onDismissMessage = { onFavoritesIntent(FavoritesIntent.ClearMessage) },
        onPlayTracks = { tracks, index -> onPlayerIntent(PlayerIntent.PlayTracks(tracks, index)) },
        showFavoriteButton = showFavoriteButton,
        showDuration = showDuration,
        showSearchField = showSearchField,
        showTrackSortActionButton = showSearchField,
        onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
        batchSelectionRequestKey = batchSelectionRequestKey,
        showInlineBatchOperationButton = showInlineBatchOperationButton,
        modifier = modifier,
    )
}

private data class LibraryBrowserPageState(
    val isLoadingContent: Boolean,
    val query: String,
    val tracks: List<Track>,
    val filteredTracks: List<Track>,
    val filteredAlbums: List<Album>,
    val filteredArtists: List<Artist>,
    val selectedSourceFilter: LibrarySourceFilter,
    val availableSourceFilters: List<LibrarySourceFilter>,
    val selectedTrackSortMode: TrackSortMode,
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

internal fun resolveTrackRowLibraryNavigationTargets(
    track: Track,
    showDuration: Boolean,
    metadataNavigationEnabled: Boolean,
): PlaybackLibraryNavigationTargets {
    return if (showDuration && metadataNavigationEnabled) {
        deriveTrackLibraryNavigationTargets(track)
    } else {
        PlaybackLibraryNavigationTargets(albumTarget = null, artistTarget = null)
    }
}

@Composable
private fun LibraryBrowserTab(
    state: LibraryBrowserPageState,
    strings: LibraryBrowserStrings,
    onSearchChanged: (String) -> Unit,
    onSourceFilterChanged: (LibrarySourceFilter) -> Unit,
    onTrackSortChanged: (TrackSortMode) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    showFavoriteButton: Boolean = true,
    showDuration: Boolean = true,
    showSearchField: Boolean = true,
    showTrackSortActionButton: Boolean = true,
    actionButton: (@Composable () -> Unit)? = null,
    onDismissMessage: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    navigationTarget: LibraryNavigationTarget? = null,
    onNavigationHandled: () -> Unit = {},
    onOpenLibraryNavigationTarget: ((LibraryNavigationTarget) -> Unit)? = null,
    batchSelectionRequestKey: Int = 0,
    showInlineBatchOperationButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val tracksListState = rememberLazyListState()
    val albumsListState = rememberLazyListState()
    val artistsListState = rememberLazyListState()
    val albumDetailListState = rememberLazyListState()
    val artistDetailListState = rememberLazyListState()
    var sourceFilterMenuExpanded by remember { mutableStateOf(false) }
    var trackSortMenuExpanded by remember { mutableStateOf(false) }
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
        state.filteredTracks
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
    val supportsBatchDownload = supportsBatchOfflineDownloadActions() && onOfflineDownloadIntent != null
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
        if (selectionMode) {
            exitSelectionMode()
        }
        rootView = view
        selectedArtistId = null
        selectedAlbumId = null
    }
    fun trackRowNavigationTargets(track: Track): PlaybackLibraryNavigationTargets {
        return resolveTrackRowLibraryNavigationTargets(
            track = track,
            showDuration = showDuration,
            metadataNavigationEnabled = onOpenLibraryNavigationTarget != null,
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
                    contentDescription = "批量操作",
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
    LaunchedEffect(Unit) {
        tracksStatFocusRequester.requestFocus()
    }
    val activeListState = when {
        selectedAlbum != null -> albumDetailListState
        rootView == LibraryBrowserRootView.Artists && selectedArtist != null -> artistDetailListState
        rootView == LibraryBrowserRootView.Albums -> albumsListState
        rootView == LibraryBrowserRootView.Artists -> artistsListState
        else -> tracksListState
    }
    val useDesktopToolbar = useDesktopLibraryBrowserToolbar(
        showSearchField = showSearchField,
        showDuration = showDuration,
    )


    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = activeListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 42.dp, bottom = 20.dp),
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
                                onQueryChanged = onSearchChanged,
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
                                            sourceFilterMenuExpanded = sourceFilterMenuExpanded,
                                            onSourceFilterMenuExpandedChange = { sourceFilterMenuExpanded = it },
                                            onSourceFilterChanged = onSourceFilterChanged,
                                            showTrackSortMenu = showTrackSortMenu,
                                            selectedTrackSortMode = state.selectedTrackSortMode,
                                            trackSortMenuExpanded = trackSortMenuExpanded,
                                            onTrackSortMenuExpandedChange = { trackSortMenuExpanded = it },
                                            onTrackSortChanged = onTrackSortChanged,
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
                                    sourceFilterMenuExpanded = sourceFilterMenuExpanded,
                                    onSourceFilterMenuExpandedChange = { sourceFilterMenuExpanded = it },
                                    onSourceFilterChanged = onSourceFilterChanged,
                                    showTrackSortMenu = showTrackSortMenu,
                                    selectedTrackSortMode = state.selectedTrackSortMode,
                                    trackSortMenuExpanded = trackSortMenuExpanded,
                                    onTrackSortMenuExpandedChange = { trackSortMenuExpanded = it },
                                    onTrackSortChanged = onTrackSortChanged,
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
            if (selectionMode) {
                item {
                    TrackSelectionActionBar(
                        selectedCount = selectedBatchTracks.size,
                        downloadSizeEstimateLabel = batchDownloadSizeEstimateLabel(selectedBatchDownloadSizeEstimate),
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
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        title = "歌曲",
                        value = state.filteredTracks.size.toString(),
                        icon = strings.songsIcon,
                        selected = rootView == LibraryBrowserRootView.Tracks,
                        onClick = { selectRootView(LibraryBrowserRootView.Tracks) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(tracksStatFocusRequester),
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
                        icon = Icons.Rounded.RecentActors,
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
                            artworkCacheKey = albumTracks.firstOrNull()?.let(::trackArtworkCacheKey),
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
                            val navigationTargets = trackRowNavigationTargets(track)
                            TrackRow(
                                track = track,
                                index = index,
                                isFavorite = track.id in state.favoriteTrackIds,
                                onToggleFavorite = { onToggleFavorite(track) },
                                showFavoriteButton = showFavoriteButton,
                                showDuration = showDuration,
                                onArtistClick = navigationTargetClick(navigationTargets.artistTarget),
                                onAlbumClick = navigationTargetClick(navigationTargets.albumTarget),
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
                            artworkCacheKey = artistTracks.firstOrNull()?.let(::trackArtworkCacheKey),
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
                                artworkCacheKey = artistTracks.firstOrNull { it.albumLibraryIdOrNull() == album.id }
                                    ?.let(::trackArtworkCacheKey),
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
                            val navigationTargets = trackRowNavigationTargets(track)
                            TrackRow(
                                track = track,
                                index = index,
                                isFavorite = track.id in state.favoriteTrackIds,
                                onToggleFavorite = { onToggleFavorite(track) },
                                showFavoriteButton = showFavoriteButton,
                                showDuration = showDuration,
                                onArtistClick = navigationTargetClick(navigationTargets.artistTarget),
                                onAlbumClick = navigationTargetClick(navigationTargets.albumTarget),
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
                                state.isLoadingContent -> EmptyStateCard(
                                    title = "正在加载$currentLabel",
                                    body = "歌曲数据会在首屏显示后继续异步整理，请稍候。",
                                )

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
                                    val navigationTargets = trackRowNavigationTargets(track)
                                    TrackRow(
                                        track = track,
                                        index = index,
                                        isFavorite = track.id in state.favoriteTrackIds,
                                        onToggleFavorite = { onToggleFavorite(track) },
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
                                        artworkCacheKey = tracksByAlbumId[album.id].orEmpty()
                                            .firstOrNull()?.let(::trackArtworkCacheKey),
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
            listState = activeListState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 8.dp, top = 20.dp, bottom = 20.dp),
        )
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
                            contentDescription = "清空搜索",
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
    sourceFilterMenuExpanded: Boolean,
    onSourceFilterMenuExpandedChange: (Boolean) -> Unit,
    onSourceFilterChanged: (LibrarySourceFilter) -> Unit,
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
                    contentDescription = "选择来源",
                )
            }
            LibrarySourceFilterDropdownMenu(
                expanded = sourceFilterMenuExpanded,
                availableSourceFilters = availableSourceFilters,
                selectedSourceFilter = selectedSourceFilter,
                onDismiss = { onSourceFilterMenuExpandedChange(false) },
                onSourceFilterChanged = { filter ->
                    onSourceFilterMenuExpandedChange(false)
                    onSourceFilterChanged(filter)
                },
            )
        }
        if (showTrackSortMenu) {
            Box {
                IconButton(onClick = { onTrackSortMenuExpandedChange(true) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = "歌曲排序",
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

private fun librarySourceFilterButtonLabel(filter: LibrarySourceFilter): String {
    return when (filter) {
        LibrarySourceFilter.ALL -> "全部来源"
        LibrarySourceFilter.LOCAL_FOLDER -> "本地文件夹"
        LibrarySourceFilter.SAMBA -> "Samba"
        LibrarySourceFilter.WEBDAV -> "WebDAV"
        LibrarySourceFilter.NAVIDROME -> "Navidrome"
        LibrarySourceFilter.DOWNLOADED -> "已下载"
    }
}

private fun librarySourceFilterMenuLabel(filter: LibrarySourceFilter): String {
    return when (filter) {
        LibrarySourceFilter.ALL -> "全部"
        else -> librarySourceFilterButtonLabel(filter)
    }
}

@Composable
private fun LibrarySourceFilterDropdownMenu(
    expanded: Boolean,
    availableSourceFilters: List<LibrarySourceFilter>,
    selectedSourceFilter: LibrarySourceFilter,
    onDismiss: () -> Unit,
    onSourceFilterChanged: (LibrarySourceFilter) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = mainShellColors.navContainer,
    ) {
        availableSourceFilters.forEach { filter ->
            val isSelected = filter == selectedSourceFilter
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

internal fun trackSortModeLabel(mode: TrackSortMode): String {
    return when (mode) {
        TrackSortMode.TITLE -> "标题"
        TrackSortMode.ARTIST -> "艺人"
        TrackSortMode.ALBUM -> "专辑"
        TrackSortMode.PLAY_COUNT -> "播放次数"
        TrackSortMode.ADDED_AT -> "添加时间"
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
    var failureDetailSummary by remember { mutableStateOf<ImportScanSummary?>(null) }
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
    val isLocalFolderScanning = activeScanOperation == ImportScanOperation.CreateLocalFolder
    val isNavidromeCreating = activeScanOperation == ImportScanOperation.CreateRemote(ImportSourceType.NAVIDROME)
    val isSambaCreating = activeScanOperation == ImportScanOperation.CreateRemote(ImportSourceType.SAMBA)
    val isWebDavCreating = activeScanOperation == ImportScanOperation.CreateRemote(ImportSourceType.WEBDAV)
    state.editingSource?.let { editingSource ->
        RemoteSourceEditorDialog(
            state = editingSource,
            isWorking = state.isWorking,
            isSavingScan = activeScanOperation == ImportScanOperation.UpdateRemote(editingSource.sourceId),
            constrainWidth = !isMobileSourcesPlatform(platform),
            testMessage = state.testMessage,
            fieldColors = importFieldColors,
            onDismiss = { onImportIntent(ImportIntent.DismissRemoteSourceEditor) },
            onIntent = onImportIntent,
        )
    }
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
    failureDetailSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { failureDetailSummary = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = shellColors.cardContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("扫描失败文件") },
            text = {
                if (summary.failures.isEmpty()) {
                    Text("当前扫描没有可展示的失败路径。")
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(summary.failures) { failure ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(failure.relativePath, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    failure.reason,
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
                    Text("知道了")
                }
            },
        )
    }
    state.testMessage?.let { message ->
        LaunchedEffect(message) {
            delay(2_500)
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
                    if (isLocalFolderScanning) {
                        ButtonLoadingIndicator()
                    } else {
                        Icon(Icons.Rounded.FolderOpen, null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (isLocalFolderScanning) "扫描中" else "选择文件夹")
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { onImportIntent(ImportIntent.TestNavidromeSource) },
                        enabled = state.capabilities.supportsNavidromeImport && !state.isWorking,
                    ) {
                        Text("测试连接")
                    }
                    Button(
                        onClick = { onImportIntent(ImportIntent.AddNavidromeSource) },
                        enabled = state.capabilities.supportsNavidromeImport && !state.isWorking,
                    ) {
                        if (isNavidromeCreating) {
                            ButtonLoadingIndicator()
                        } else {
                            Icon(Icons.Rounded.CloudSync, null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (isNavidromeCreating) "同步中" else "连接并同步")
                    }
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { onImportIntent(ImportIntent.TestSambaSource) },
                        enabled = !state.isWorking,
                    ) {
                        Text("测试连接")
                    }
                    Button(
                        onClick = { onImportIntent(ImportIntent.AddSambaSource) },
                        enabled = !state.isWorking,
                    ) {
                        if (isSambaCreating) {
                            ButtonLoadingIndicator()
                        } else {
                            Icon(Icons.Rounded.CloudSync, null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (isSambaCreating) "扫描中" else "连接并扫描")
                    }
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { onImportIntent(ImportIntent.TestWebDavSource) },
                        enabled = state.capabilities.supportsWebDavImport && !state.isWorking,
                    ) {
                        Text("测试连接")
                    }
                    Button(
                        onClick = { onImportIntent(ImportIntent.AddWebDavSource) },
                        enabled = state.capabilities.supportsWebDavImport && !state.isWorking,
                    ) {
                        if (isWebDavCreating) {
                            ButtonLoadingIndicator()
                        } else {
                            Icon(Icons.Rounded.CloudSync, null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (isWebDavCreating) "扫描中" else "连接并扫描")
                    }
                }
            }
        }
            SectionTitle(title = "已连接来源", subtitle = "可编辑连接参数、测试连通性，并按需启用或禁用来源。")
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
                        onEdit = if (source.source.type == ImportSourceType.LOCAL_FOLDER) {
                            null
                        } else {
                            { onImportIntent(ImportIntent.OpenRemoteSourceEditor(source.source.id)) }
                        },
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
                        onShowScanFailures = { failureDetailSummary = it },
                    )
                }
            }
        }
        if (state.editingSource == null) {
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
private fun RemoteSourceEditorDialog(
    state: top.iwesley.lyn.music.feature.importing.RemoteSourceEditorState,
    isWorking: Boolean,
    isSavingScan: Boolean,
    constrainWidth: Boolean,
    testMessage: String?,
    fieldColors: androidx.compose.material3.TextFieldColors,
    onDismiss: () -> Unit,
    onIntent: (ImportIntent) -> Unit,
) {
    val shellColors = mainShellColors
    Dialog(
        onDismissRequest = {
            if (!isWorking) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
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
                    .fillMaxHeight(0.6f),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        when (state.type) {
                            ImportSourceType.SAMBA -> "编辑 Samba 来源"
                            ImportSourceType.WEBDAV -> "编辑 WebDAV 来源"
                            ImportSourceType.NAVIDROME -> "编辑 Navidrome 来源"
                            ImportSourceType.LOCAL_FOLDER -> "编辑来源"
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 372.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.hasStoredCredential) {
                            Text(
                                "已保存凭据，密码留空会继续使用当前凭据。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ImeAwareOutlinedTextField(
                            value = state.label,
                            onValueChange = { onIntent(ImportIntent.RemoteSourceLabelChanged(it)) },
                            label = { Text("名称") },
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
                                        label = { Text("服务器地址") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = fieldColors,
                                    )
                                    ImeAwareOutlinedTextField(
                                        value = state.port,
                                        onValueChange = { onIntent(ImportIntent.RemoteSourcePortChanged(it)) },
                                        label = { Text("端口") },
                                        modifier = Modifier.width(140.dp),
                                        shape = RoundedCornerShape(18.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = fieldColors,
                                    )
                                }
                                ImeAwareOutlinedTextField(
                                    value = state.path,
                                    onValueChange = { onIntent(ImportIntent.RemoteSourcePathChanged(it)) },
                                    label = { Text("路径（Share/子目录）") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = fieldColors,
                                )
                            }

                            ImportSourceType.WEBDAV,
                            ImportSourceType.NAVIDROME,
                            -> {
                                ImeAwareOutlinedTextField(
                                    value = state.rootUrl,
                                    onValueChange = { onIntent(ImportIntent.RemoteSourceRootUrlChanged(it)) },
                                    label = {
                                        Text(if (state.type == ImportSourceType.WEBDAV) "根 URL" else "服务器地址")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = fieldColors,
                                )
                            }

                            ImportSourceType.LOCAL_FOLDER -> Unit
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ImeAwareOutlinedTextField(
                                value = state.username,
                                onValueChange = { onIntent(ImportIntent.RemoteSourceUsernameChanged(it)) },
                                label = { Text("用户名") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp),
                                colors = fieldColors,
                            )
                            ImeAwareOutlinedTextField(
                                value = state.password,
                                onValueChange = { onIntent(ImportIntent.RemoteSourcePasswordChanged(it)) },
                                label = {
                                    Text(if (state.hasStoredCredential) "密码（留空沿用）" else "密码")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp),
                                colors = fieldColors,
                            )
                        }
                        if (state.type == ImportSourceType.WEBDAV) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text("允许自签名证书", fontWeight = FontWeight.Medium)
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
                            Text("取消")
                        }
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(
                            onClick = { onIntent(ImportIntent.TestRemoteSource) },
                            enabled = !isWorking,
                        ) {
                            Text("测试连接")
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = { onIntent(ImportIntent.SaveRemoteSource) },
                            enabled = !isWorking,
                        ) {
                            if (isSavingScan) {
                                ButtonLoadingIndicator()
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (isSavingScan) "重扫中" else "保存并重扫")
                        }
                    }
                }
            }
            testMessage?.let { message ->
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

private fun isMobileSourcesPlatform(platform: PlatformDescriptor): Boolean {
    return platform.name == "Android" || platform.name == "iPhone / iPad"
}
