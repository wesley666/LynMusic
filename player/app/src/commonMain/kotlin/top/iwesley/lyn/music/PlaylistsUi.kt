package top.iwesley.lyn.music

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.OfflineDownload
import top.iwesley.lyn.music.core.model.PlaylistAddTarget
import top.iwesley.lyn.music.core.model.PlaylistDetail
import top.iwesley.lyn.music.core.model.PlaylistKind
import top.iwesley.lyn.music.core.model.PlaylistSummary
import top.iwesley.lyn.music.core.model.SYSTEM_LIKED_PLAYLIST_ID
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.matchesLibrarySourceFilter
import top.iwesley.lyn.music.feature.offline.OfflineDownloadIntent
import top.iwesley.lyn.music.feature.offline.batchDownloadInsufficientSpaceMessage
import top.iwesley.lyn.music.feature.offline.batchDownloadSizeEstimateLabel
import top.iwesley.lyn.music.feature.offline.estimateBatchDownloadSize
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.playlists.PlaylistsIntent
import top.iwesley.lyn.music.feature.playlists.PlaylistsState
import top.iwesley.lyn.music.platform.PlatformBackHandler
import top.iwesley.lyn.music.platform.rememberPlatformArtworkBitmap
import top.iwesley.lyn.music.ui.mainShellColors

fun buildPlaylistAddTargets(
    playlists: List<PlaylistSummary>,
    favoriteTrackIds: Set<String>,
    trackId: String?,
): List<PlaylistAddTarget> {
    val likedTarget = PlaylistAddTarget(
        id = SYSTEM_LIKED_PLAYLIST_ID,
        name = "喜欢",
        kind = PlaylistKind.SYSTEM_LIKED,
        updatedAt = Long.MAX_VALUE,
        alreadyContainsTrack = trackId != null && trackId in favoriteTrackIds,
    )
    return listOf(likedTarget) + playlists
        .sortedWith(compareByDescending<PlaylistSummary> { it.updatedAt }.thenBy { it.name.lowercase() })
        .map { playlist ->
            PlaylistAddTarget(
                id = playlist.id,
                name = playlist.name,
                kind = playlist.kind,
                updatedAt = playlist.updatedAt,
                alreadyContainsTrack = trackId != null && trackId in playlist.memberTrackIds,
            )
        }
}

@Composable
internal fun PlaylistAddDialog(
    track: Track,
    isLoadingTargets: Boolean,
    targets: List<PlaylistAddTarget>,
    compact: Boolean = false,
    onDismiss: () -> Unit,
    onAddTarget: (PlaylistAddTarget) -> Unit,
    onCreatePlaylistAndAdd: (String) -> Unit,
) {
    if (compact) {
        PlaylistAddBottomSheet(
            track = track,
            isLoadingTargets = isLoadingTargets,
            targets = targets,
            onDismiss = onDismiss,
            onAddTarget = onAddTarget,
            onCreatePlaylistAndAdd = onCreatePlaylistAndAdd,
        )
    } else {
        var selectedTargetId by remember(track.id, targets) {
            mutableStateOf(targets.firstOrNull { !it.alreadyContainsTrack }?.id)
        }
        val selectedTarget = targets
            .takeUnless { isLoadingTargets }
            ?.firstOrNull { it.id == selectedTargetId && !it.alreadyContainsTrack }

        PlaylistAddAlertDialog(
            track = track,
            isLoadingTargets = isLoadingTargets,
            targets = targets,
            selectedTargetId = selectedTargetId,
            selectedTarget = selectedTarget,
            onSelectTarget = { selectedTargetId = it },
            onDismiss = onDismiss,
            onAddTarget = onAddTarget,
            onCreatePlaylistAndAdd = onCreatePlaylistAndAdd,
        )
    }
}

@Composable
private fun PlaylistAddAlertDialog(
    track: Track,
    isLoadingTargets: Boolean,
    targets: List<PlaylistAddTarget>,
    selectedTargetId: String?,
    selectedTarget: PlaylistAddTarget?,
    onSelectTarget: (String) -> Unit,
    onDismiss: () -> Unit,
    onAddTarget: (PlaylistAddTarget) -> Unit,
    onCreatePlaylistAndAdd: (String) -> Unit,
) {
    val shellColors = mainShellColors
    var newPlaylistName by rememberSaveable(track.id) { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = mainShellColors.cardBorder,
        unfocusedBorderColor = mainShellColors.cardBorder,
        disabledBorderColor = mainShellColors.cardBorder,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = shellColors.navContainer,
        iconContentColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(28.dp),
        title = { Text("加入歌单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = buildString {
                        append(track.title)
                        track.artistName?.takeIf { it.isNotBlank() }?.let {
                            append(" · ")
                            append(it)
                        }
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isLoadingTargets) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(shellColors.cardContainer.copy(alpha = 0.55f))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = "正在加载歌单目标…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        targets.forEach { target ->
                            val disabled = target.alreadyContainsTrack
                            val selected = selectedTargetId == target.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        when {
                                            disabled -> shellColors.cardContainer.copy(alpha = 0.45f)
                                            selected -> shellColors.selectedContainer
                                            else -> shellColors.cardContainer.copy(alpha = 0.55f)
                                        },
                                    )
                                    .clickable(enabled = !disabled) {
                                        onSelectTarget(target.id)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier.width(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = if (disabled) null else { { onSelectTarget(target.id) } },
                                        modifier = Modifier.size(20.dp),
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary,
                                            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            disabledSelectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                            disabledUnselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        ),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = target.name,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (disabled) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    Text(
                                        text = if (disabled) "已存在" else when (target.kind) {
                                            PlaylistKind.SYSTEM_LIKED -> "加入喜欢"
                                            PlaylistKind.USER -> "加入普通歌单"
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                ImeAwareOutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("新建歌单") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors,
                )
                Button(
                    onClick = {
                        onCreatePlaylistAndAdd(newPlaylistName)
                        newPlaylistName = ""
                    },
                    enabled = newPlaylistName.trim().isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        disabledContainerColor = shellColors.cardContainer,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("新建并加入")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedTarget?.let(onAddTarget) },
                enabled = selectedTarget != null,
            ) {
                Text(
                    text = "加入",
                    color = if (selectedTarget != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistAddBottomSheet(
    track: Track,
    isLoadingTargets: Boolean,
    targets: List<PlaylistAddTarget>,
    onDismiss: () -> Unit,
    onAddTarget: (PlaylistAddTarget) -> Unit,
    onCreatePlaylistAndAdd: (String) -> Unit,
) {
    val shellColors = mainShellColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appDensity = LocalDensity.current
    var createPlaylistDialogVisible by rememberSaveable(track.id) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = shellColors.navContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        dragHandle = {
            CompositionLocalProvider(LocalDensity provides appDensity) {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .size(width = 50.dp, height = 5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(shellColors.cardBorder.copy(alpha = 0.75f)),
                )
            }
        },
    ) {
        CompositionLocalProvider(LocalDensity provides appDensity) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 18.dp),
            ) {
                Text(
                    text = "收藏到歌单",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                    ),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = playlistAddTrackLabel(track),
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(shellColors.cardBorder.copy(alpha = 0.72f)),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    item(key = "create-playlist") {
                        PlaylistAddCreatePlaylistRow(
                            onClick = { createPlaylistDialogVisible = true },
                        )
                    }
                    if (isLoadingTargets) {
                        item(key = "loading") {
                            PlaylistAddLoadingRow()
                        }
                    } else if (targets.isEmpty()) {
                        item(key = "empty") {
                            PlaylistAddEmptyRow()
                        }
                    } else {
                        items(targets, key = { it.id }) { target ->
                            PlaylistAddCompactTargetRow(
                                target = target,
                                onClick = { onAddTarget(target) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (createPlaylistDialogVisible) {
        PlaylistNameDialog(
            onDismiss = { createPlaylistDialogVisible = false },
            onConfirm = { name ->
                createPlaylistDialogVisible = false
                onCreatePlaylistAndAdd(name)
            },
            confirmText = "新建并加入",
        )
    }
}

@Composable
private fun PlaylistAddCreatePlaylistRow(
    onClick: () -> Unit,
) {
    val shellColors = mainShellColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(shellColors.cardContainer.copy(alpha = 0.82f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = "新建歌单",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 24.sp,
            ),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaylistAddLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(mainShellColors.cardContainer.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
            text = "正在加载歌单目标…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaylistAddEmptyRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(mainShellColors.cardContainer.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = "暂无可加入的歌单",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaylistAddCompactTargetRow(
    target: PlaylistAddTarget,
    onClick: () -> Unit,
) {
    val shellColors = mainShellColors
    val disabled = target.alreadyContainsTrack
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                enabled = !disabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when {
                        disabled -> shellColors.cardContainer.copy(alpha = 0.45f)
                        else -> shellColors.cardContainer.copy(alpha = 0.82f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (target.kind) {
                    PlaylistKind.SYSTEM_LIKED -> Icons.Rounded.Favorite
                    PlaylistKind.USER -> Icons.AutoMirrored.Rounded.List
                },
                contentDescription = null,
                tint = when {
                    disabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                    target.kind == PlaylistKind.SYSTEM_LIKED -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            text = target.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 24.sp,
            ),
            fontWeight = FontWeight.Bold,
            color = if (disabled) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (disabled) {
            Text(
                text = "已添加",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                ),
            )
        }
    }
}

private fun playlistAddTrackLabel(track: Track): String {
    return buildString {
        append(track.title)
        track.artistName?.takeIf { it.isNotBlank() }?.let {
            append(" · ")
            append(it)
        }
    }
}

@Composable
internal fun PlaylistsTab(
    state: PlaylistsState,
    onPlaylistsIntent: (PlaylistsIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    playlistSearchQuery: String = "",
    showRefreshActionButton: Boolean = true,
    showSourceFilterActionButton: Boolean = true,
    batchSelectionRequestKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    val detail = state.selectedPlaylist
    val requestedPlaylistId = state.selectedPlaylistId
    val filteredPlaylists = remember(state.playlists, playlistSearchQuery) {
        filterMobileLibraryHubPlaylists(state.playlists, playlistSearchQuery)
    }
    val isFilteringPlaylists = playlistSearchQuery.isNotBlank() && state.playlists.isNotEmpty()
    PlatformBackHandler(
        enabled = canNavigateBackFromPlaylistDetail(requestedPlaylistId),
        onBack = { onPlaylistsIntent(PlaylistsIntent.BackToList) },
    )
    val filteredDetail = remember(
        detail,
        state.selectedSourceFilter,
        state.sourceTypesById,
        state.offlineDownloadsByTrackId,
    ) {
        detail?.let { playlistDetail ->
            val filteredTracks = playlistDetail.tracks.filter { entry ->
                matchesPlaylistSourceFilter(
                    track = entry.track,
                    selectedSourceFilter = state.selectedSourceFilter,
                    sourceTypesById = state.sourceTypesById,
                    offlineDownloadsByTrackId = state.offlineDownloadsByTrackId,
                )
            }
            playlistDetail.copy(tracks = filteredTracks)
        }
    }
    val rawDetailPresentation = remember(requestedPlaylistId, detail, state.playlists) {
        buildPlaylistDetailPresentationState(
            selectedPlaylistId = requestedPlaylistId,
            detail = detail,
            playlists = state.playlists,
        )
    }
    val filteredDetailPresentation = remember(requestedPlaylistId, filteredDetail, state.playlists) {
        buildPlaylistDetailPresentationState(
            selectedPlaylistId = requestedPlaylistId,
            detail = filteredDetail,
            playlists = state.playlists,
        )
    }
    val resolvedDetail = filteredDetailPresentation.resolvedDetail
    val resolvedRawDetail = rawDetailPresentation.resolvedDetail
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val layoutProfile = buildLayoutProfile(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            platform = currentPlatformDescriptor,
            density = density,
        )
        val wide = layoutProfile.isExpandedLayout
        if (showCreateDialog) {
            PlaylistNameDialog(
                onDismiss = { showCreateDialog = false },
                onConfirm = { name ->
                    showCreateDialog = false
                    onPlaylistsIntent(PlaylistsIntent.CreatePlaylist(name))
                },
            )
        }
        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                PlaylistListPane(
                    playlists = filteredPlaylists,
                    isLoadingContent = state.isLoadingContent,
                    selectedPlaylistId = requestedPlaylistId,
                    isRefreshing = state.isRefreshing,
                    selectedSourceFilter = state.selectedSourceFilter,
                    availableSourceFilters = state.availableSourceFilters,
                    isFilteringByQuery = isFilteringPlaylists,
                    showRefreshActionButton = showRefreshActionButton,
                    showSourceFilterActionButton = showSourceFilterActionButton,
                    onRefresh = { onPlaylistsIntent(PlaylistsIntent.Refresh) },
                    onSourceFilterChanged = { onPlaylistsIntent(PlaylistsIntent.SourceFilterChanged(it)) },
                    onCreate = { showCreateDialog = true },
                    onDelete = { onPlaylistsIntent(PlaylistsIntent.DeletePlaylist(it)) },
                    onSelect = { onPlaylistsIntent(PlaylistsIntent.SelectPlaylist(it)) },
                    modifier = Modifier.weight(0.36f).fillMaxHeight(),
                )
                PlaylistDetailPane(
                    detail = resolvedDetail,
                    isLoadingContent = state.isLoadingContent,
                    isDetailSwitchLoading = filteredDetailPresentation.isDetailSwitchLoading,
                    requestedPlaylistName = filteredDetailPresentation.requestedPlaylistName,
                    hasTracksOutsideFilter = resolvedRawDetail?.tracks?.isNotEmpty() == true &&
                        resolvedDetail?.tracks?.isEmpty() == true,
                    onBack = { onPlaylistsIntent(PlaylistsIntent.BackToList) },
                    onPlayAll = { tracks ->
                        if (tracks.isNotEmpty()) {
                            onPlayerIntent(PlayerIntent.PlayTracks(tracks, 0))
                        }
                    },
                    onPlayTrack = { tracks, index ->
                        onPlayerIntent(PlayerIntent.PlayTracks(tracks, index))
                    },
                    onRemoveTrack = { trackId ->
                        resolvedRawDetail?.id?.let { playlistId ->
                            onPlaylistsIntent(PlaylistsIntent.RemoveTrackFromPlaylist(playlistId, trackId))
                        }
                    },
                    modifier = Modifier.weight(0.64f).fillMaxHeight(),
                    showBackButton = false,
                    batchSelectionRequestKey = batchSelectionRequestKey,
                )
            }
        } else if (!filteredDetailPresentation.shouldShowDetailPane) {
            PlaylistListPane(
                playlists = filteredPlaylists,
                isLoadingContent = state.isLoadingContent,
                selectedPlaylistId = requestedPlaylistId,
                isRefreshing = state.isRefreshing,
                selectedSourceFilter = state.selectedSourceFilter,
                availableSourceFilters = state.availableSourceFilters,
                isFilteringByQuery = isFilteringPlaylists,
                showRefreshActionButton = showRefreshActionButton,
                showSourceFilterActionButton = showSourceFilterActionButton,
                onRefresh = { onPlaylistsIntent(PlaylistsIntent.Refresh) },
                onSourceFilterChanged = { onPlaylistsIntent(PlaylistsIntent.SourceFilterChanged(it)) },
                onCreate = { showCreateDialog = true },
                onDelete = { onPlaylistsIntent(PlaylistsIntent.DeletePlaylist(it)) },
                onSelect = { onPlaylistsIntent(PlaylistsIntent.SelectPlaylist(it)) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PlaylistDetailPane(
                detail = resolvedDetail,
                isLoadingContent = state.isLoadingContent,
                isDetailSwitchLoading = filteredDetailPresentation.isDetailSwitchLoading,
                requestedPlaylistName = filteredDetailPresentation.requestedPlaylistName,
                hasTracksOutsideFilter = resolvedRawDetail?.tracks?.isNotEmpty() == true &&
                    resolvedDetail?.tracks?.isEmpty() == true,
                onBack = { onPlaylistsIntent(PlaylistsIntent.BackToList) },
                onPlayAll = { tracks ->
                    if (tracks.isNotEmpty()) {
                        onPlayerIntent(PlayerIntent.PlayTracks(tracks, 0))
                    }
                },
                onPlayTrack = { tracks, index ->
                    onPlayerIntent(PlayerIntent.PlayTracks(tracks, index))
                },
                onRemoveTrack = { trackId ->
                    resolvedRawDetail?.id?.let { playlistId ->
                        onPlaylistsIntent(PlaylistsIntent.RemoveTrackFromPlaylist(playlistId, trackId))
                    }
                },
                modifier = Modifier.fillMaxSize(),
                showBackButton = true,
                batchSelectionRequestKey = batchSelectionRequestKey,
            )
        }
    }
}

internal data class PlaylistDetailPresentationState(
    val requestedPlaylistId: String?,
    val requestedPlaylistName: String?,
    val resolvedDetail: PlaylistDetail?,
    val isDetailSwitchLoading: Boolean,
    val shouldShowDetailPane: Boolean,
)

internal fun buildPlaylistDetailPresentationState(
    selectedPlaylistId: String?,
    detail: PlaylistDetail?,
    playlists: List<PlaylistSummary>,
): PlaylistDetailPresentationState {
    val resolvedDetail = detail?.takeIf { it.id == selectedPlaylistId }
    return PlaylistDetailPresentationState(
        requestedPlaylistId = selectedPlaylistId,
        requestedPlaylistName = playlists.firstOrNull { it.id == selectedPlaylistId }?.name,
        resolvedDetail = resolvedDetail,
        isDetailSwitchLoading = selectedPlaylistId != null && resolvedDetail == null,
        shouldShowDetailPane = selectedPlaylistId != null,
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PlaylistListPane(
    playlists: List<PlaylistSummary>,
    isLoadingContent: Boolean,
    selectedPlaylistId: String?,
    isRefreshing: Boolean,
    selectedSourceFilter: LibrarySourceFilter,
    availableSourceFilters: List<LibrarySourceFilter>,
    isFilteringByQuery: Boolean = false,
    showRefreshActionButton: Boolean = true,
    showSourceFilterActionButton: Boolean = true,
    onRefresh: () -> Unit,
    onSourceFilterChanged: (LibrarySourceFilter) -> Unit,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sourceFilterMenuExpanded by remember { mutableStateOf(false) }
    val mobilePlatform = currentPlatformDescriptor.isMobilePlatform()
    var menuPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeletePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeletePlaylistName by rememberSaveable { mutableStateOf("") }
    val pendingDeletePlaylist = remember(playlists, pendingDeletePlaylistId) {
        playlists.firstOrNull { it.id == pendingDeletePlaylistId }
    }
    LaunchedEffect(pendingDeletePlaylistId, pendingDeletePlaylist) {
        if (pendingDeletePlaylistId != null && pendingDeletePlaylist == null) {
            pendingDeletePlaylistId = null
            pendingDeletePlaylistName = ""
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PlaylistSectionTitle(
                    title = "歌单",
                    subtitle = "普通歌单支持本地歌曲和 Navidrome 歌曲混合收藏。",
                )
            }
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(onClick = onCreate) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "新建歌单",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (showRefreshActionButton) {
                        OutlinedButton(onClick = onRefresh) {
                            Icon(Icons.Rounded.Sync, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isRefreshing) "同步中" else "同步远端",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (showSourceFilterActionButton) {
                        Box {
                            OutlinedButton(onClick = { sourceFilterMenuExpanded = true }) {
                                Icon(Icons.Rounded.Tune, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = playlistSourceFilterButtonLabel(selectedSourceFilter),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            DropdownMenu(
                                expanded = sourceFilterMenuExpanded,
                                onDismissRequest = { sourceFilterMenuExpanded = false },
                                containerColor = mainShellColors.navContainer,
                            ) {
                                availableSourceFilters.forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(playlistSourceFilterMenuLabel(filter)) },
                                        onClick = {
                                            sourceFilterMenuExpanded = false
                                            onSourceFilterChanged(filter)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (isLoadingContent) {
                item {
                    EmptyStateCard(
                        title = "正在加载歌单",
                        body = "歌单数据会在页面显示后继续异步整理，请稍候。",
                    )
                }
            } else if (playlists.isEmpty()) {
                item {
                    if (isFilteringByQuery) {
                        EmptyStateCard(
                            title = "没有匹配的歌单",
                            body = "试试调整搜索词，或清空搜索后查看全部歌单。",
                        )
                    } else {
                        EmptyStateCard(
                            title = "还没有普通歌单",
                            body = "从播放器把当前歌曲加入歌单，或先新建一个空歌单。",
                        )
                    }
                }
            } else {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistSummaryCard(
                        playlist = playlist,
                        selected = playlist.id == selectedPlaylistId,
                        mobilePlatform = mobilePlatform,
                        menuExpanded = menuPlaylistId == playlist.id,
                        onClick = { onSelect(playlist.id) },
                        onOpenMenu = { menuPlaylistId = playlist.id },
                        onDismissMenu = {
                            if (menuPlaylistId == playlist.id) {
                                menuPlaylistId = null
                            }
                        },
                        onRequestDelete = {
                            menuPlaylistId = null
                            pendingDeletePlaylistId = playlist.id
                            pendingDeletePlaylistName = playlist.name
                        },
                    )
                }
            }
        }

        pendingDeletePlaylist?.let { playlist ->
            PlaylistDeleteDialog(
                playlistName = pendingDeletePlaylistName.ifBlank { playlist.name },
                onDismiss = {
                    pendingDeletePlaylistId = null
                    pendingDeletePlaylistName = ""
                },
                onConfirm = {
                    pendingDeletePlaylistId = null
                    pendingDeletePlaylistName = ""
                    onDelete(playlist.id)
                },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PlaylistSummaryCard(
    playlist: PlaylistSummary,
    selected: Boolean,
    mobilePlatform: Boolean,
    menuExpanded: Boolean,
    onClick: () -> Unit,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val shellColors = mainShellColors
    val cardShape = RoundedCornerShape(24.dp)
    val interactionModifier = if (mobilePlatform) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onOpenMenu,
        )
    } else {
        Modifier
            .pointerInput(onOpenMenu) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            event.changes.forEach { it.consume() }
                            onOpenMenu()
                        }
                    }
                }
            }
            .clickable(onClick = onClick)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .then(interactionModifier),
            shape = cardShape,
            colors = CardDefaults.cardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.secondary else shellColors.cardContainer,
            ),
            border = null,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PlaylistArtworkThumbnail(
                    artworkLocator = playlistSummaryArtworkLocator(playlist),
                    cornerRadius = 8.dp,
                    containerColor = if (selected) Color.Transparent else shellColors.navContainer,
                    fallbackTint = if (selected) {
                        MaterialTheme.colorScheme.onSecondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        playlist.name,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${playlist.trackCount} 首歌曲",
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = onDismissMenu,
            containerColor = shellColors.navContainer,
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "删除歌单",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = onRequestDelete,
            )
        }
    }
}

internal fun playlistSummaryArtworkLocator(playlist: PlaylistSummary): String? {
    return playlist.artworkLocator?.takeIf { it.isNotBlank() }
}

@Composable
private fun PlaylistDeleteDialog(
    playlistName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val shellColors = mainShellColors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = shellColors.navContainer,
        iconContentColor = MaterialTheme.colorScheme.error,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(28.dp),
        title = { Text("删除歌单") },
        text = { Text("确认删除“$playlistName”吗？本地和已同步的远端歌单都会一起删除。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "删除",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
private fun PlaylistDetailPane(
    detail: PlaylistDetail?,
    isLoadingContent: Boolean,
    isDetailSwitchLoading: Boolean,
    requestedPlaylistName: String?,
    hasTracksOutsideFilter: Boolean,
    onBack: () -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onRemoveTrack: (String) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean,
    batchSelectionRequestKey: Int = 0,
) {
    var selectionMode by rememberSaveable(detail?.id) { mutableStateOf(false) }
    var selectedTrackIds by rememberSaveable(detail?.id) { mutableStateOf(emptyList<String>()) }
    var batchQualitySheetVisible by rememberSaveable(detail?.id) { mutableStateOf(false) }
    var lastHandledBatchSelectionRequestKey by rememberSaveable { mutableStateOf(0) }
    var pendingBatchDownloadTracks by remember { mutableStateOf(emptyList<Track>()) }
    val visibleTracks = detail?.tracks?.map { it.track }.orEmpty()
    val selectedBatchTracks = remember(visibleTracks, selectedTrackIds) {
        selectedTracksInVisibleOrder(visibleTracks, selectedTrackIds)
    }
    val allVisibleTracksSelected = visibleTracks.isNotEmpty() && visibleTracks.all { it.id in selectedTrackIds }
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
    val showInlineBatchOperationButton = !currentPlatformDescriptor.isMobilePlatform()
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
    LaunchedEffect(visibleTracks) {
        val pruned = pruneSelectedTrackIds(selectedTrackIds, visibleTracks)
        if (pruned != selectedTrackIds) {
            selectedTrackIds = pruned
        }
        if (selectionMode && visibleTracks.isEmpty()) {
            exitSelectionMode()
        }
    }
    LaunchedEffect(batchSelectionRequestKey, supportsBatchDownload, visibleTracks) {
        if (batchSelectionRequestKey <= lastHandledBatchSelectionRequestKey) {
            return@LaunchedEffect
        }
        val shouldEnterSelectionMode = shouldHandleBatchSelectionRequest(
            requestKey = batchSelectionRequestKey,
            lastHandledRequestKey = lastHandledBatchSelectionRequestKey,
            supportsBatchDownload = supportsBatchDownload,
            hasVisibleTracks = visibleTracks.isNotEmpty(),
        )
        lastHandledBatchSelectionRequestKey = batchSelectionRequestKey
        if (shouldEnterSelectionMode) {
            selectionMode = true
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            if (isDetailSwitchLoading) {
                PlaylistDetailLoadingContent(
                    requestedPlaylistName = requestedPlaylistName,
                    showBackButton = showBackButton,
                    onBack = onBack,
                )
            } else if (isLoadingContent && detail == null) {
                EmptyStateCard(
                    title = "正在加载歌单详情",
                    body = "歌单列表和歌曲内容会在后台继续准备，请稍候。",
                )
            } else if (detail == null) {
                EmptyStateCard(
                    title = "选择一个歌单",
                    body = "左侧会列出普通歌单，点击后可以查看歌曲并直接播放。",
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (showBackButton) {
                        TextButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("返回歌单列表")
                        }
                    }
                    PlaylistSectionTitle(
                        title = detail.name,
                        subtitle = "${detail.tracks.size} 首歌曲",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { onPlayAll(detail.tracks.map { it.track }) },
                            enabled = detail.tracks.isNotEmpty(),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("播放全部")
                        }
                        if (
                            supportsBatchDownload &&
                            showInlineBatchOperationButton &&
                            !selectionMode &&
                            detail.tracks.isNotEmpty()
                        ) {
                            BatchOperationButton(onClick = { selectionMode = true })
                        }
                    }
                    if (selectionMode) {
                        TrackSelectionActionBar(
                            selectedCount = selectedBatchTracks.size,
                            downloadSizeEstimateLabel = batchDownloadSizeEstimateLabel(selectedBatchDownloadSizeEstimate),
                            allVisibleSelected = allVisibleTracksSelected,
                            hasVisibleTracks = visibleTracks.isNotEmpty(),
                            onToggleSelectAll = {
                                selectedTrackIds = toggleAllVisibleTrackSelection(selectedTrackIds, visibleTracks)
                            },
                            onDownloadSelected = ::requestBatchDownload,
                            onCancelSelection = ::exitSelectionMode,
                        )
                    }
                }
            }
        }
        when {
            detail == null -> Unit
            detail.tracks.isEmpty() -> item {
                EmptyStateCard(
                    title = if (hasTracksOutsideFilter) {
                        "当前来源下没有歌曲"
                    } else {
                        "歌单还是空的"
                    },
                    body = if (hasTracksOutsideFilter) {
                        "试试切回“${playlistSourceFilterButtonLabel(LibrarySourceFilter.ALL)}”，或更换其他来源筛选。"
                    } else {
                        "从播放器把当前歌曲加入这里后，就可以直接播放和管理了。"
                    },
                )
            }

            else -> itemsIndexed(detail.tracks, key = { _, item -> item.track.id }) { index, item ->
                PlaylistTrackRow(
                    entry = item,
                    index = index,
                    selectionMode = selectionMode,
                    selected = item.track.id in selectedTrackIds,
                    onSelectionToggle = {
                        selectedTrackIds = toggleTrackSelection(selectedTrackIds, item.track.id)
                    },
                    onClick = { onPlayTrack(detail.tracks.map { it.track }, index) },
                    onRemove = { onRemoveTrack(item.track.id) },
                )
            }
        }
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
private fun PlaylistDetailLoadingContent(
    requestedPlaylistName: String?,
    showBackButton: Boolean,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showBackButton) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("返回歌单列表")
            }
        }
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = mainShellColors.cardContainer),
            border = BorderStroke(1.dp, mainShellColors.cardBorder),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("正在打开歌单", fontWeight = FontWeight.Bold)
                    Text(
                        text = requestedPlaylistName
                            ?.takeIf { it.isNotBlank() }
                            ?.let { "正在读取“$it”中的歌曲，请稍候。" }
                            ?: "正在读取歌单中的歌曲，请稍候。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun matchesPlaylistSourceFilter(
    track: Track,
    selectedSourceFilter: LibrarySourceFilter,
    sourceTypesById: Map<String, ImportSourceType>,
    offlineDownloadsByTrackId: Map<String, OfflineDownload>,
): Boolean {
    return matchesLibrarySourceFilter(
        track = track,
        selectedSourceFilter = selectedSourceFilter,
        sourceTypesById = sourceTypesById,
        offlineDownloadsByTrackId = offlineDownloadsByTrackId,
    )
}

private fun playlistSourceFilterButtonLabel(filter: LibrarySourceFilter): String {
    return when (filter) {
        LibrarySourceFilter.ALL -> "全部来源"
        LibrarySourceFilter.LOCAL_FOLDER -> "本地文件夹"
        LibrarySourceFilter.SAMBA -> "Samba"
        LibrarySourceFilter.WEBDAV -> "WebDAV"
        LibrarySourceFilter.NAVIDROME -> "Navidrome"
        LibrarySourceFilter.DOWNLOADED -> "已下载"
    }
}

private fun playlistSourceFilterMenuLabel(filter: LibrarySourceFilter): String {
    return when (filter) {
        LibrarySourceFilter.ALL -> "全部"
        else -> playlistSourceFilterButtonLabel(filter)
    }
}

@Composable
private fun PlaylistTrackRow(
    entry: top.iwesley.lyn.music.core.model.PlaylistTrackEntry,
    index: Int,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectionToggle: (() -> Unit)? = null,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val shellColors = mainShellColors
    val rowClick = if (selectionMode) {
        onSelectionToggle ?: {}
    } else {
        onClick
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        TrackActionContainer(
            track = entry.track,
            onClick = rowClick,
            enableOfflineActions = !selectionMode,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onSelectionToggle?.invoke() },
                    modifier = Modifier.size(32.dp),
                )
            } else {
                Text((index + 1).toString().padStart(2, '0'), fontWeight = FontWeight.Bold)
            }
            PlaylistArtworkThumbnail(artworkLocator = entry.track.artworkLocator)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    buildString {
                        append(entry.track.artistName ?: "未知艺人")
                        entry.sourceLabel?.takeIf { it.isNotBlank() }?.let {
                            append(" · ")
                            append(it)
                        }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!selectionMode) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Delete, contentDescription = "移出歌单")
                }
            }
            Text(formatDuration(entry.track.durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun PlaylistNameDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    confirmText: String = "创建",
) {
    val shellColors = mainShellColors
    var name by rememberSaveable { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = shellColors.cardBorder,
        unfocusedBorderColor = shellColors.cardBorder,
        disabledBorderColor = shellColors.cardBorder,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = shellColors.navContainer,
        iconContentColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                text = "新建歌单",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                ),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            ImeAwareOutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("歌单名称") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = fieldColors,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.trim().isNotBlank(),
            ) {
                Text(
                    text = confirmText,
                    color = if (name.trim().isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
private fun PlaylistSectionTitle(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = mainShellColors.secondaryText)
    }
}

@Composable
private fun PlaylistArtworkThumbnail(
    artworkLocator: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 1.dp,
    containerColor: Color? = null,
    fallbackTint: Color? = null,
) {
    val artworkBitmap = rememberPlatformArtworkBitmap(artworkLocator)
    val resolvedContainerColor = containerColor ?: mainShellColors.cardContainer
    val resolvedFallbackTint = fallbackTint ?: MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(resolvedContainerColor)
            .padding(0.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = artworkBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.List,
                contentDescription = null,
                tint = resolvedFallbackTint,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
