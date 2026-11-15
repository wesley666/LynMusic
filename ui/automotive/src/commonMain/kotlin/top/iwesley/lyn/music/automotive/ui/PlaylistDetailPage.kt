package top.iwesley.lyn.music.automotive.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.PlaylistSummary
import top.iwesley.lyn.music.core.model.PlaylistDetail
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.library.*
import top.iwesley.lyn.music.feature.offline.*
import top.iwesley.lyn.music.feature.online.*
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.playlists.*
import top.iwesley.lyn.music.resources.*

internal data class PlaylistHeaderLayout(val split: Boolean, val width: Dp, val cover: Dp, val fontScale: Float) {
    companion object {
        val Saver = listSaver<PlaylistHeaderLayout, Any>(
            save = { listOf(it.split, it.width.value, it.cover.value, it.fontScale) },
            restore = { PlaylistHeaderLayout(it[0] as Boolean, (it[1] as Float).dp, (it[2] as Float).dp, it[3] as Float) },
        )
    }
}

internal class PlaylistDetailEntryState {
    var list = LazyListState()
    var summaryScroll = ScrollState(0)
    var headerLayout: PlaylistHeaderLayout? = null
    var selectionMode = false
    var selectedTrackIds: List<String> = emptyList()
    var importVisible = false
    var loadFailure by mutableStateOf<String?>(null)
}

internal data class PlaylistDetailRow(val track: Track, val rawIndex: Int, val key: String)

internal fun playlistDetailRows(detail: PlaylistDetail, state: PlaylistsState, route: MusicDetailRoute): List<PlaylistDetailRow> {
    val occurrences = mutableMapOf<Pair<String, String>, Int>()
    return detail.tracks.mapIndexedNotNull { index, entry ->
        val track = entry.track
        val identity = track.sourceId to track.id
        val occurrence = occurrences[identity] ?: 0
        occurrences[identity] = occurrence + 1
        val visible = route.sourceId != null || matchesLibrarySourceFilter(track, state.selectedSourceFilter,
            state.sourceTypesById, state.offlineDownloadsByTrackId)
        if (!visible) null else PlaylistDetailRow(track, index,
            "${track.sourceId.length}:${track.sourceId}${track.id.length}:${track.id}:$occurrence")
    }
}

@Composable
internal fun PlaylistDetailPage(
    route: MusicDetailRoute,
    navigation: MusicDetailNavigation,
    state: PlaylistsState,
    online: OnlinePlaylistsState,
    importState: ImportState,
    onLocalIntent: (PlaylistsIntent) -> Unit,
    onOnlineIntent: (OnlinePlaylistsIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    allowSplit: Boolean,
) {
    val entry = navigation.playlistEntry(route)
    val list = rememberSaveable(saver = LazyListState.Saver) { entry.list }
    val statusScroll = androidx.compose.foundation.rememberScrollState()
    val summaryScroll = rememberSaveable(saver = ScrollState.Saver) { entry.summaryScroll }
    var selectionMode by rememberSaveable { mutableStateOf(entry.selectionMode) }
    var selectedTrackIds by rememberSaveable { mutableStateOf(entry.selectedTrackIds) }
    var importVisible by remember { mutableStateOf(entry.importVisible) }
    var menuVisible by remember { mutableStateOf(false) }
    var qualityVisible by remember { mutableStateOf(false) }
    var pendingTracks by remember { mutableStateOf(emptyList<Track>()) }
    val isOnline = route.sourceId != null
    val sourceReady = online.sourceId == route.sourceId
    val playlists = if (isOnline) online.playlists.takeIf { sourceReady }.orEmpty() else state.playlists
    val summary = playlists.firstOrNull { it.id == route.entityId }
    val selectedId = if (isOnline) online.selectedPlaylistId else state.selectedPlaylistId
    val rawDetail = (if (isOnline) online.selectedPlaylist else state.selectedPlaylist)
        ?.takeIf { it.id == route.entityId && selectedId == route.entityId && (!isOnline || sourceReady) }
    val currentDetail by rememberUpdatedState(rawDetail)
    val rows = remember(rawDetail, state.selectedSourceFilter, state.sourceTypesById, state.offlineDownloadsByTrackId, route) {
        rawDetail?.let { playlistDetailRows(it, state, route) }.orEmpty()
    }
    val tracks = rows.map { it.track }
    val sourceMissing = isOnline && importState.onlineNavidromeSourceOptions().none { it.sourceId == route.sourceId }
    val failure = if (isOnline && sourceReady && selectedId == route.entityId && !online.isLoadingDetail)
        online.errorMessage?.displayText() ?: entry.loadFailure else null
    // A list refresh can remove the selection before its detail request finishes.
    val listReady = if (isOnline) sourceReady && !online.isLoading else !state.isLoadingContent
    val unavailable = sourceMissing || (listReady && selectedId == null && summary == null)
    val title = rawDetail?.name ?: summary?.name ?: route.title.orEmpty()
    val offline = LocalOfflineDownloadUiState.current
    val onOffline = offline.onIntent
    val supportsBatch = !isOnline && supportsBatchOfflineDownloadActions() && onOffline != null
    val selectedTracks = selectedTracksInVisibleOrder(tracks, selectedTrackIds)
    val estimate = remember(selectedTracks, offline.downloadsByTrackId) {
        estimateBatchDownloadSize(selectedTracks, offline.downloadsByTrackId)
    }
    val generation = navigation.generation
    val foreground by rememberUpdatedState(LocalMusicDetailIsForeground.current)
    fun active() = foreground && navigation.top.entryId == route.entryId && navigation.generation == generation
    fun exitSelection() {
        selectionMode = false
        selectedTrackIds = emptyList()
        qualityVisible = false
        pendingTracks = emptyList()
    }
    fun startDownload(downloadTracks: List<Track>, quality: NavidromeAudioQuality) {
        if (!active()) return
        val message = batchDownloadInsufficientSpaceMessage(
            estimateBatchDownloadSize(downloadTracks, offline.downloadsByTrackId, quality), offline.availableSpaceBytes)
        if (message != null) {
            onOffline?.invoke(OfflineDownloadIntent.ShowMessage(message))
            qualityVisible = false
            pendingTracks = emptyList()
            return
        }
        onOffline?.invoke(OfflineDownloadIntent.DownloadMany(downloadTracks, quality))
        exitSelection()
    }
    fun requestDownload() {
        if (selectedTracks.isEmpty()) return
        if (hasNavidromeTracks(selectedTracks)) {
            pendingTracks = selectedTracks
            qualityVisible = true
        } else startDownload(selectedTracks, NavidromeAudioQuality.Original)
    }
    fun dismissImport() {
        importVisible = false
        if (isOnline) onOnlineIntent(OnlinePlaylistsIntent.ClearPlaylistImportReport)
        else onLocalIntent(PlaylistsIntent.ClearPlaylistImportReport)
    }
    fun back() {
        if (!active()) return
        when {
            menuVisible -> menuVisible = false
            importVisible -> dismissImport()
            qualityVisible -> qualityVisible = false
            selectionMode -> exitSelection()
            else -> navigation.pop(route.entryId, generation)
        }
    }
    MusicDetailBackHandler(menuVisible || importVisible || qualityVisible || selectionMode, ::back)
    LaunchedEffect(selectionMode, supportsBatch) {
        if (selectionMode && supportsBatch) onOffline?.invoke(OfflineDownloadIntent.RefreshAvailableSpace)
    }
    LaunchedEffect(tracks) {
        selectedTrackIds = pruneSelectedTrackIds(selectedTrackIds, tracks)
        if (tracks.isEmpty()) exitSelection()
    }
    SideEffect {
        entry.list = list
        entry.summaryScroll = summaryScroll
        entry.selectionMode = selectionMode
        entry.selectedTrackIds = selectedTrackIds
        entry.importVisible = importVisible
    }
    val actions = buildList {
        add(ListItemMenuAction("import", uiText(Res.string.playlist_import_title), Icons.AutoMirrored.Rounded.List,
            enabled = canShowPlaylistImportAction(rawDetail) && !(if (isOnline) online.isImporting else state.isImporting),
            onClick = { if (active()) importVisible = true }))
        if (supportsBatch) add(ListItemMenuAction("batch", uiText(Res.string.library_batch_actions), Icons.Rounded.Checklist,
            enabled = tracks.isNotEmpty(), onClick = { if (active()) selectionMode = true }))
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("music-detail-page-playlist")) {
        val pageWidth = maxWidth
        val padding = if (pageWidth >= 600.dp) 24.dp else 20.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = padding).heightIn(min = 48.dp).testTag("playlist-toolbar"),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, uiString(Res.string.common_back)) }
                Text(uiString(Res.string.playlists_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box {
                    ListItemMoreButton(uiString(Res.string.track_more_actions, title)) { menuVisible = true }
                    ListItemActionMenu(title, actions, ListItemMenuPresentation.BottomSheet, menuVisible, { menuVisible = false })
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val visibleHeight = (maxHeight - LocalDockBottomInset.current).coerceAtLeast(0.dp)
                val contentWidth = (pageWidth - padding * 2).coerceAtLeast(0.dp)
                val leftWidth = ((contentWidth - 32.dp) * 0.30f).coerceIn(220.dp, 288.dp)
                val scale = maxOf(1f, LocalDensity.current.fontScale)
                val split = allowSplit && pageWidth >= 900.dp && visibleHeight >= 240.dp &&
                    contentWidth - leftWidth - 32.dp >= 320.dp * scale
                val coverSize = when {
                    split -> minOf(leftWidth, if (visibleHeight < 360.dp) 160.dp else 288.dp)
                    visibleHeight < 360.dp -> 112.dp
                    pageWidth >= 600.dp -> 220.dp
                    else -> 176.dp
                }.coerceAtMost(contentWidth)
                val layout = PlaylistHeaderLayout(split, contentWidth, coverSize, LocalDensity.current.fontScale)
                var previousLayout by rememberSaveable(stateSaver = PlaylistHeaderLayout.Saver) {
                    mutableStateOf(entry.headerLayout ?: layout)
                }
                val resetHeader = previousLayout != layout && list.firstVisibleItemIndex == 0
                SideEffect {
                    previousLayout = layout
                    entry.headerLayout = layout
                    if (resetHeader) list.requestScrollToItem(0)
                }
                if (rawDetail == null || unavailable) {
                    Column(Modifier.fillMaxSize().verticalScroll(statusScroll)
                        .padding(dockContentPadding(PaddingValues(horizontal = padding, vertical = 24.dp)))
                        .testTag(if (unavailable || failure != null) "playlist-error" else "playlist-loading"),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        when {
                            unavailable -> Text(uiString(Res.string.music_detail_source_unavailable))
                            failure != null -> {
                                Text(failure)
                                Button(onClick = {
                                    if (active()) onOnlineIntent(OnlinePlaylistsIntent.SelectPlaylist(route.entityId))
                                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiString(Res.string.common_retry)) }
                            }
                            else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                Text(uiString(Res.string.music_detail_loading))
                            }
                        }
                    }
                } else {
                    val message = if (isOnline) online.errorMessage ?: online.message else state.message
                    val menuContext = route.entryId to rawDetail.tracks
                    Row(Modifier.fillMaxSize().padding(horizontal = padding)
                        .testTag(if (split) "playlist-content-split" else "playlist-content-stacked"),
                        horizontalArrangement = Arrangement.spacedBy(if (split) 32.dp else 0.dp)) {
                        val play: (() -> Unit)? = if (tracks.isEmpty() || selectionMode) null else ({
                            if (active()) onPlayerIntent(PlayerIntent.PlayTracks(tracks, 0))
                        })
                        val header: @Composable () -> Unit = {
                            PlaylistSummaryContent(title, summary, route, rawDetail, coverSize, tracks.size, play)
                        }
                        if (split) Column(Modifier.width(leftWidth).fillMaxHeight().verticalScroll(summaryScroll)
                            .padding(dockContentPadding(PaddingValues(top = 24.dp, bottom = 20.dp)))
                            .testTag("playlist-summary-scroll")) { header() }
                        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                            val wideRows = maxWidth >= 520.dp * scale
                            val measuredIndexWidth = rememberListIndexColumnWidth(tracks.size)
                            val indexWidth = maxOf(measuredIndexWidth, if (selectionMode) 48.dp else 0.dp)
                            LazyColumn(Modifier.fillMaxSize().testTag("playlist-tracks"), state = list,
                                contentPadding = dockContentPadding(PaddingValues(top = if (split) 24.dp else 16.dp, bottom = 20.dp))) {
                                item("playlist-header") {
                                    if (!split) header() else PlaylistTrackColumns(indexWidth, wideRows, showMenu = !selectionMode)
                                    if (selectionMode) TrackSelectionActionBar(
                                        selectedCount = selectedTracks.size,
                                        downloadSizeEstimateLabel = batchDownloadSizeEstimateLabel(estimate).displayText(),
                                        allVisibleSelected = tracks.isNotEmpty() && tracks.all { it.id in selectedTrackIds },
                                        hasVisibleTracks = tracks.isNotEmpty(),
                                        onToggleSelectAll = { selectedTrackIds = toggleAllVisibleTrackSelection(selectedTrackIds, tracks) },
                                        onDownloadSelected = ::requestDownload, onCancelSelection = ::exitSelection)
                                    if (message != null) BannerCard(message, onDismiss = {
                                        if (isOnline) onOnlineIntent(OnlinePlaylistsIntent.ClearMessage) else onLocalIntent(PlaylistsIntent.ClearMessage)
                                    })
                                    if (isOnline && online.isMutating) LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                                itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                                    val targets = if (isOnline) deriveOnlineTrackLibraryNavigationTargets(row.track, checkNotNull(route.sourceId))
                                        else deriveTrackLibraryNavigationTargets(row.track, state.selectedSourceFilter)
                                    TrackRow(row.track, index, isFavorite = false, onToggleFavorite = {},
                                        menuContextKey = menuContext to row.key, showDuration = wideRows,
                                        durationColumnWidth = 56.dp * scale, indexColumnWidth = indexWidth,
                                        selectionControlSize = 48.dp,
                                        selectionMode = selectionMode, selected = row.track.id in selectedTrackIds,
                                        onSelectionToggle = { selectedTrackIds = toggleTrackSelection(selectedTrackIds, row.track.id) },
                                        onArtistClick = targets.artistTarget?.let { target -> { if (active()) navigation.open(target) } },
                                        onAlbumClick = targets.albumTarget?.let { target -> { if (active()) navigation.open(target) } },
                                        onRemoveFromPlaylist = if (online.isMutating || online.isImporting || state.isImporting) null else ({
                                            if (active() && currentDetail == rawDetail) {
                                                if (isOnline) onOnlineIntent(OnlinePlaylistsIntent.RemoveTrack(route.entityId, row.rawIndex))
                                                else onLocalIntent(PlaylistsIntent.RemoveTrackFromPlaylist(route.entityId, row.track.id))
                                            }
                                        }), onClick = { if (active()) onPlayerIntent(PlayerIntent.PlayTracks(tracks, index)) })
                                }
                                if (tracks.isEmpty()) item("empty") {
                                    val filtered = rawDetail.tracks.isNotEmpty()
                                    Box(Modifier.padding(vertical = 20.dp).testTag("playlist-empty")) {
                                        EmptyStateCard(uiString(if (filtered) Res.string.playlist_source_tracks_empty else Res.string.playlist_empty_title),
                                            if (filtered) uiString(Res.string.playlist_source_filter_hint, librarySourceFilterButtonLabel(LibrarySourceFilter.ALL).displayText())
                                            else uiString(Res.string.playlists_add_current_track_hint))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (importVisible && rawDetail != null) PlaylistTextImportDialog(
        playlistName = title, isOnlineMode = isOnline, isImporting = if (isOnline) online.isImporting else state.isImporting,
        report = if (isOnline) online.playlistImportReport else state.playlistImportReport,
        onDismiss = ::dismissImport, onImport = {
            if (active() && currentDetail == rawDetail) {
                if (isOnline) onOnlineIntent(OnlinePlaylistsIntent.ImportPlaylistText(route.entityId, it))
                else onLocalIntent(PlaylistsIntent.ImportPlaylistText(route.entityId, it))
            }
        })
    if (qualityVisible) BatchDownloadQualityBottomSheet(pendingTracks.size, tracks = pendingTracks,
        downloadsByTrackId = offline.downloadsByTrackId, onQualitySelected = { startDownload(pendingTracks, it) },
        onDismiss = { qualityVisible = false; pendingTracks = emptyList() })
}

@Composable
private fun PlaylistSummaryContent(title: String, summary: PlaylistSummary?, route: MusicDetailRoute,
    detail: PlaylistDetail, coverSize: Dp, count: Int, onPlay: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("playlist-summary"), horizontalAlignment = Alignment.CenterHorizontally) {
        val ownArtwork = summary?.artworkLocator?.takeIf(String::isNotBlank)
        val artwork = ownArtwork ?: route.artworkLocator?.takeIf(String::isNotBlank)
        val artworkKey = if (ownArtwork != null) summary.artworkCacheKey else route.artworkCacheKey
        val covers = remember(detail.tracks) { detail.tracks.map { it.track }.filter { !it.artworkLocator.isNullOrBlank() }
            .distinctBy { it.artworkLocator }.take(4) }
        Box(Modifier.size(coverSize).shadow(6.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).testTag("playlist-cover")) {
            when {
                artwork != null -> LynArtworkImage(artworkLocator = artwork, artworkCacheKey = artworkKey,
                    contentDescription = null, modifier = Modifier.fillMaxSize(), maxDecodeSizePx = ArtworkDecodeSize.Preview)
                covers.size == 4 -> Column(Modifier.fillMaxSize()) {
                    for (row in 0..1) Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (column in 0..1) {
                            val track = covers[row * 2 + column]
                            LynArtworkImage(track.artworkLocator, null, modifier = Modifier.weight(1f).fillMaxHeight(),
                                artworkCacheKey = trackArtworkCacheKey(track), maxDecodeSizePx = ArtworkDecodeSize.Card)
                        }
                    }
                }
                else -> LynArtworkImage(artworkLocator = covers.firstOrNull()?.artworkLocator, contentDescription = null,
                    artworkCacheKey = covers.firstOrNull()?.let(::trackArtworkCacheKey), modifier = Modifier.fillMaxSize(),
                    maxDecodeSizePx = ArtworkDecodeSize.Preview)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(20.dp))
        TrackListActions(uiString(Res.plurals.common_track_count, count, count), uiString(Res.string.player_play_all), onPlay,
            modifier = Modifier.testTag("playlist-actions"), playModifier = Modifier.testTag("playlist-play-all"))
    }
}

@Composable
private fun PlaylistTrackColumns(indexWidth: Dp, wide: Boolean, showMenu: Boolean) {
    if (!wide) Text(uiString(Res.string.music_detail_songs), Modifier.padding(bottom = 16.dp), style = MaterialTheme.typography.titleMedium)
    else Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.width(indexWidth))
        Spacer(Modifier.width(52.dp))
        Text(uiString(Res.string.music_detail_songs), Modifier.weight(1.45f), style = MaterialTheme.typography.bodySmall)
        Text(uiString(Res.string.music_detail_albums), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Row {
            Text(uiString(Res.string.music_detail_duration),
                Modifier.width(56.dp * maxOf(1f, LocalDensity.current.fontScale)).testTag("playlist-column-duration"),
                textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (showMenu) Spacer(Modifier.width(48.dp))
        }
    }
}
