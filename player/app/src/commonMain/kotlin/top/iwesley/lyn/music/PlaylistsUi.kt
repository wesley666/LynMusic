package top.iwesley.lyn.music

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.OfflineDownload
import top.iwesley.lyn.music.core.model.PlaylistAddTarget
import top.iwesley.lyn.music.core.model.PlaylistDetail
import top.iwesley.lyn.music.core.model.PlaylistKind
import top.iwesley.lyn.music.core.model.PlaylistSummary
import top.iwesley.lyn.music.core.model.SYSTEM_LIKED_PLAYLIST_ID
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.data.repository.PlaylistImportReport
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.matchesLibrarySourceFilter
import top.iwesley.lyn.music.feature.offline.OfflineDownloadIntent
import top.iwesley.lyn.music.feature.offline.batchDownloadInsufficientSpaceMessage
import top.iwesley.lyn.music.feature.offline.batchDownloadSizeEstimateLabel
import top.iwesley.lyn.music.feature.offline.estimateBatchDownloadSize
import top.iwesley.lyn.music.feature.online.OnlinePlaylistsIntent
import top.iwesley.lyn.music.feature.online.OnlinePlaylistsState
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.playlists.PlaylistsIntent
import top.iwesley.lyn.music.feature.playlists.PlaylistsState
import top.iwesley.lyn.music.platform.PlatformBackHandler
import top.iwesley.lyn.music.ui.mainShellColors

fun buildPlaylistAddTargets(
    playlists: List<PlaylistSummary>,
    favoriteTrackIds: Set<String>,
    trackId: String?,
    includeLiked: Boolean = true,
): List<PlaylistAddTarget> {
    val likedTarget = PlaylistAddTarget(
        id = SYSTEM_LIKED_PLAYLIST_ID,
        name = "喜欢",
        kind = PlaylistKind.SYSTEM_LIKED,
        updatedAt = Long.MAX_VALUE,
        alreadyContainsTrack = trackId != null && trackId in favoriteTrackIds,
    )
    val systemTargets = if (includeLiked) listOf(likedTarget) else emptyList()
    return systemTargets + playlists
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
    importState: ImportState,
    onlineState: OnlinePlaylistsState,
    onPlaylistsIntent: (PlaylistsIntent) -> Unit,
    onOnlineIntent: (OnlinePlaylistsIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    playlistSearchQuery: String = "",
    showRefreshActionButton: Boolean = true,
    showSourceFilterActionButton: Boolean = true,
    batchSelectionRequestKey: Int = 0,
    showInlineBatchOperationButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    val onlineSourceOptions = remember(importState.sources) {
        importState.onlineNavidromeSourceOptions()
    }
    val isOnlineMode = onlineState.sourceId != null
    val playlists = if (isOnlineMode) onlineState.playlists else state.playlists
    val detail = if (isOnlineMode) onlineState.selectedPlaylist else state.selectedPlaylist
    val requestedPlaylistId = if (isOnlineMode) onlineState.selectedPlaylistId else state.selectedPlaylistId
    val isListLoading = if (isOnlineMode) {
        onlineState.isLoading || onlineState.isMutating
    } else {
        state.isLoadingContent
    }
    val isDetailLoading = if (isOnlineMode) {
        onlineState.isLoadingDetail || onlineState.isMutating
    } else {
        state.isLoadingContent
    }
    val isRefreshing = if (isOnlineMode) {
        onlineState.isLoading || onlineState.isMutating
    } else {
        state.isRefreshing
    }
    val filteredPlaylists = remember(playlists, playlistSearchQuery) {
        filterMobileLibraryHubPlaylists(playlists, playlistSearchQuery)
    }
    val isFilteringPlaylists = playlistSearchQuery.isNotBlank() && playlists.isNotEmpty()
    PlatformBackHandler(
        enabled = canNavigateBackFromPlaylistDetail(requestedPlaylistId),
        onBack = {
            if (isOnlineMode) {
                onOnlineIntent(OnlinePlaylistsIntent.SelectPlaylist(null))
            } else {
                onPlaylistsIntent(PlaylistsIntent.BackToList)
            }
        },
    )
    val filteredDetail = remember(
        isOnlineMode,
        detail,
        state.selectedSourceFilter,
        state.sourceTypesById,
        state.offlineDownloadsByTrackId,
    ) {
        if (isOnlineMode) {
            detail
        } else {
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
    }
    val rawDetailPresentation = remember(requestedPlaylistId, detail, playlists) {
        buildPlaylistDetailPresentationState(
            selectedPlaylistId = requestedPlaylistId,
            detail = detail,
            playlists = playlists,
        )
    }
    val filteredDetailPresentation = remember(requestedPlaylistId, filteredDetail, playlists) {
        buildPlaylistDetailPresentationState(
            selectedPlaylistId = requestedPlaylistId,
            detail = filteredDetail,
            playlists = playlists,
        )
    }
    val resolvedDetail = filteredDetailPresentation.resolvedDetail
    val resolvedRawDetail = rawDetailPresentation.resolvedDetail
    LaunchedEffect(showImportDialog, resolvedRawDetail?.id) {
        if (showImportDialog && resolvedRawDetail == null) {
            showImportDialog = false
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val layoutProfile = buildLayoutProfile(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            platform = currentPlatformDescriptor,
            density = density,
        )
        val wide = layoutProfile.isExpandedLayout
        val showPlaylistTrackDuration = !layoutProfile.isCompactLayout
        if (showCreateDialog) {
            PlaylistNameDialog(
                onDismiss = { showCreateDialog = false },
                onConfirm = { name ->
                    showCreateDialog = false
                    if (isOnlineMode) {
                        onOnlineIntent(OnlinePlaylistsIntent.CreatePlaylist(name))
                    } else {
                        onPlaylistsIntent(PlaylistsIntent.CreatePlaylist(name))
                    }
                },
            )
        }
        if (showImportDialog) {
            resolvedRawDetail?.let { importTarget ->
                PlaylistTextImportDialog(
                    playlistName = importTarget.name,
                    isOnlineMode = isOnlineMode,
                    isImporting = if (isOnlineMode) onlineState.isImporting else state.isImporting,
                    report = if (isOnlineMode) onlineState.playlistImportReport else state.playlistImportReport,
                    onDismiss = {
                        showImportDialog = false
                        if (isOnlineMode) {
                            onOnlineIntent(OnlinePlaylistsIntent.ClearPlaylistImportReport)
                        } else {
                            onPlaylistsIntent(PlaylistsIntent.ClearPlaylistImportReport)
                        }
                    },
                    onImport = { text ->
                        if (isOnlineMode) {
                            onOnlineIntent(OnlinePlaylistsIntent.ImportPlaylistText(importTarget.id, text))
                        } else {
                            onPlaylistsIntent(PlaylistsIntent.ImportPlaylistText(importTarget.id, text))
                        }
                    },
                )
            }
        }
        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                PlaylistListPane(
                    playlists = filteredPlaylists,
                    isLoadingContent = isListLoading,
                    selectedPlaylistId = requestedPlaylistId,
                    isRefreshing = isRefreshing,
                    selectedSourceFilter = state.selectedSourceFilter,
                    availableSourceFilters = state.availableSourceFilters,
                    onlineSourceOptions = onlineSourceOptions,
                    selectedOnlineSourceId = onlineState.sourceId,
                    isOnlineMode = isOnlineMode,
                    isFilteringByQuery = isFilteringPlaylists,
                    showRefreshActionButton = showRefreshActionButton,
                    showSourceFilterActionButton = showSourceFilterActionButton,
                    onRefresh = {
                        if (isOnlineMode) {
                            onOnlineIntent(OnlinePlaylistsIntent.Refresh)
                        } else {
                            onPlaylistsIntent(PlaylistsIntent.Refresh)
                        }
                    },
                    onSourceFilterChanged = {
                        onOnlineIntent(OnlinePlaylistsIntent.SelectSource(sourceId = null))
                        onPlaylistsIntent(PlaylistsIntent.SourceFilterChanged(it))
                    },
                    onOnlineSourceSelected = { onOnlineIntent(OnlinePlaylistsIntent.SelectSource(it)) },
                    onCreate = { showCreateDialog = true },
                    onRename = { playlistId, name ->
                        if (isOnlineMode) {
                            onOnlineIntent(OnlinePlaylistsIntent.RenamePlaylist(playlistId, name))
                        } else {
                            onPlaylistsIntent(PlaylistsIntent.RenamePlaylist(playlistId, name))
                        }
                    },
                    onDelete = {
                        if (isOnlineMode) {
                            onOnlineIntent(OnlinePlaylistsIntent.DeletePlaylist(it))
                        } else {
                            onPlaylistsIntent(PlaylistsIntent.DeletePlaylist(it))
                        }
                    },
                    onSelect = {
                        if (isOnlineMode) {
                            onOnlineIntent(OnlinePlaylistsIntent.SelectPlaylist(it))
                        } else {
                            onPlaylistsIntent(PlaylistsIntent.SelectPlaylist(it))
                        }
                    },
                    modifier = Modifier.weight(0.36f).fillMaxHeight(),
                )
                PlaylistDetailPane(
                    detail = resolvedDetail,
                    isLoadingContent = isDetailLoading,
                    isDetailSwitchLoading = filteredDetailPresentation.isDetailSwitchLoading,
                    requestedPlaylistName = filteredDetailPresentation.requestedPlaylistName,
                    hasTracksOutsideFilter = !isOnlineMode &&
                        resolvedRawDetail?.tracks?.isNotEmpty() == true &&
                        resolvedDetail?.tracks?.isEmpty() == true,
                    onBack = {
                        if (isOnlineMode) {
                            onOnlineIntent(OnlinePlaylistsIntent.SelectPlaylist(null))
                        } else {
                            onPlaylistsIntent(PlaylistsIntent.BackToList)
                        }
                    },
                    onPlayAll = { tracks ->
                        if (tracks.isNotEmpty()) {
                            onPlayerIntent(PlayerIntent.PlayTracks(tracks, 0))
                        }
                    },
                    onPlayTrack = { tracks, index ->
                        onPlayerIntent(PlayerIntent.PlayTracks(tracks, index))
                    },
                    isImportingPlaylist = if (isOnlineMode) onlineState.isImporting else state.isImporting,
                    onImportPlaylist = { showImportDialog = true },
                    showImportPlaylistAction = true,
                    onRemoveTrack = { trackId, index ->
                        resolvedRawDetail?.id?.let { playlistId ->
                            if (isOnlineMode) {
                                onOnlineIntent(OnlinePlaylistsIntent.RemoveTrack(playlistId, index))
                            } else {
                                onPlaylistsIntent(PlaylistsIntent.RemoveTrackFromPlaylist(playlistId, trackId))
                            }
                        }
                    },
                    showTrackDuration = showPlaylistTrackDuration,
                    allowLocalIndexActions = !isOnlineMode,
                    modifier = Modifier.weight(0.64f).fillMaxHeight(),
                    showBackButton = false,
                    batchSelectionRequestKey = batchSelectionRequestKey,
                    showInlineBatchOperationButton = showInlineBatchOperationButton,
                )
            }
        } else if (!filteredDetailPresentation.shouldShowDetailPane) {
            PlaylistListPane(
                playlists = filteredPlaylists,
                isLoadingContent = isListLoading,
                selectedPlaylistId = requestedPlaylistId,
                isRefreshing = isRefreshing,
                selectedSourceFilter = state.selectedSourceFilter,
                availableSourceFilters = state.availableSourceFilters,
                onlineSourceOptions = onlineSourceOptions,
                selectedOnlineSourceId = onlineState.sourceId,
                isOnlineMode = isOnlineMode,
                isFilteringByQuery = isFilteringPlaylists,
                showRefreshActionButton = showRefreshActionButton,
                showSourceFilterActionButton = showSourceFilterActionButton,
                onRefresh = {
                    if (isOnlineMode) {
                        onOnlineIntent(OnlinePlaylistsIntent.Refresh)
                    } else {
                        onPlaylistsIntent(PlaylistsIntent.Refresh)
                    }
                },
                onSourceFilterChanged = {
                    onOnlineIntent(OnlinePlaylistsIntent.SelectSource(sourceId = null))
                    onPlaylistsIntent(PlaylistsIntent.SourceFilterChanged(it))
                },
                onOnlineSourceSelected = { onOnlineIntent(OnlinePlaylistsIntent.SelectSource(it)) },
                onCreate = { showCreateDialog = true },
                onRename = { playlistId, name ->
                    if (isOnlineMode) {
                        onOnlineIntent(OnlinePlaylistsIntent.RenamePlaylist(playlistId, name))
                    } else {
                        onPlaylistsIntent(PlaylistsIntent.RenamePlaylist(playlistId, name))
                    }
                },
                onDelete = {
                    if (isOnlineMode) {
                        onOnlineIntent(OnlinePlaylistsIntent.DeletePlaylist(it))
                    } else {
                        onPlaylistsIntent(PlaylistsIntent.DeletePlaylist(it))
                    }
                },
                onSelect = {
                    if (isOnlineMode) {
                        onOnlineIntent(OnlinePlaylistsIntent.SelectPlaylist(it))
                    } else {
                        onPlaylistsIntent(PlaylistsIntent.SelectPlaylist(it))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PlaylistDetailPane(
                detail = resolvedDetail,
                isLoadingContent = isDetailLoading,
                isDetailSwitchLoading = filteredDetailPresentation.isDetailSwitchLoading,
                requestedPlaylistName = filteredDetailPresentation.requestedPlaylistName,
                hasTracksOutsideFilter = !isOnlineMode &&
                    resolvedRawDetail?.tracks?.isNotEmpty() == true &&
                    resolvedDetail?.tracks?.isEmpty() == true,
                onBack = {
                    if (isOnlineMode) {
                        onOnlineIntent(OnlinePlaylistsIntent.SelectPlaylist(null))
                    } else {
                        onPlaylistsIntent(PlaylistsIntent.BackToList)
                    }
                },
                onPlayAll = { tracks ->
                    if (tracks.isNotEmpty()) {
                        onPlayerIntent(PlayerIntent.PlayTracks(tracks, 0))
                    }
                },
                onPlayTrack = { tracks, index ->
                    onPlayerIntent(PlayerIntent.PlayTracks(tracks, index))
                },
                isImportingPlaylist = if (isOnlineMode) onlineState.isImporting else state.isImporting,
                onImportPlaylist = { showImportDialog = true },
                showImportPlaylistAction = true,
                onRemoveTrack = { trackId, index ->
                    resolvedRawDetail?.id?.let { playlistId ->
                        if (isOnlineMode) {
                            onOnlineIntent(OnlinePlaylistsIntent.RemoveTrack(playlistId, index))
                        } else {
                            onPlaylistsIntent(PlaylistsIntent.RemoveTrackFromPlaylist(playlistId, trackId))
                        }
                    }
                },
                showTrackDuration = showPlaylistTrackDuration,
                allowLocalIndexActions = !isOnlineMode,
                modifier = Modifier.fillMaxSize(),
                showBackButton = true,
                batchSelectionRequestKey = batchSelectionRequestKey,
                showInlineBatchOperationButton = showInlineBatchOperationButton,
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
    onlineSourceOptions: List<OnlineSourceOption> = emptyList(),
    selectedOnlineSourceId: String? = null,
    isOnlineMode: Boolean = false,
    isFilteringByQuery: Boolean = false,
    showRefreshActionButton: Boolean = true,
    showSourceFilterActionButton: Boolean = true,
    onRefresh: () -> Unit,
    onSourceFilterChanged: (LibrarySourceFilter) -> Unit,
    onOnlineSourceSelected: (String) -> Unit = {},
    onCreate: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sourceFilterMenuExpanded by remember { mutableStateOf(false) }
    val mobilePlatform = currentPlatformDescriptor.isMobilePlatform()
    var menuPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRenamePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRenamePlaylistName by rememberSaveable { mutableStateOf("") }
    var pendingDeletePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeletePlaylistName by rememberSaveable { mutableStateOf("") }
    val pendingRenamePlaylist = remember(playlists, pendingRenamePlaylistId) {
        playlists.firstOrNull { it.id == pendingRenamePlaylistId }
    }
    val pendingDeletePlaylist = remember(playlists, pendingDeletePlaylistId) {
        playlists.firstOrNull { it.id == pendingDeletePlaylistId }
    }
    LaunchedEffect(pendingRenamePlaylistId, pendingRenamePlaylist) {
        if (pendingRenamePlaylistId != null && pendingRenamePlaylist == null) {
            pendingRenamePlaylistId = null
            pendingRenamePlaylistName = ""
        }
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
            contentPadding = dockContentPadding(PaddingValues(20.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PlaylistSectionTitle(
                    title = "歌单",
                    subtitle = if (isOnlineMode) {
                        "当前显示 Navidrome 远端歌单，变更会直接同步到服务器。"
                    } else {
                        "普通歌单支持本地歌曲和 Subsonic-compatible 歌曲混合收藏。"
                    },
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
                        val selectedOnlineSourceLabel = onlineSourceOptions
                            .firstOrNull { it.sourceId == selectedOnlineSourceId }
                            ?.label
                        Box {
                            OutlinedButton(onClick = { sourceFilterMenuExpanded = true }) {
                                Icon(Icons.Rounded.Tune, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = selectedOnlineSourceLabel
                                        ?: playlistSourceFilterButtonLabel(selectedSourceFilter),
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
                                onlineSourceOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            sourceFilterMenuExpanded = false
                                            onOnlineSourceSelected(option.sourceId)
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
                            body = if (isOnlineMode) {
                                "远端还没有普通歌单，可以先新建一个空歌单。"
                            } else {
                                "从播放器把当前歌曲加入歌单，或先新建一个空歌单。"
                            },
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
                        onRequestRename = {
                            menuPlaylistId = null
                            pendingRenamePlaylistId = playlist.id
                            pendingRenamePlaylistName = playlist.name
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

        pendingRenamePlaylist?.let { playlist ->
            PlaylistNameDialog(
                title = "重命名歌单",
                initialName = pendingRenamePlaylistName.ifBlank { playlist.name },
                confirmText = "保存",
                onDismiss = {
                    pendingRenamePlaylistId = null
                    pendingRenamePlaylistName = ""
                },
                onConfirm = { name ->
                    pendingRenamePlaylistId = null
                    pendingRenamePlaylistName = ""
                    onRename(playlist.id, name)
                },
            )
        }

        pendingDeletePlaylist?.let { playlist ->
            PlaylistDeleteDialog(
                playlistName = pendingDeletePlaylistName.ifBlank { playlist.name },
                isOnlineMode = isOnlineMode,
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
    onRequestRename: () -> Unit,
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
                    artworkCacheKey = playlistSummaryArtworkCacheKey(playlist),
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
                text = { Text("重命名") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                    )
                },
                onClick = onRequestRename,
            )
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

internal fun playlistSummaryArtworkCacheKey(playlist: PlaylistSummary): String? {
    return playlist.artworkCacheKey?.takeIf { it.isNotBlank() }
}

@Composable
private fun PlaylistDeleteDialog(
    playlistName: String,
    isOnlineMode: Boolean = false,
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
        text = {
            Text(
                if (isOnlineMode) {
                    "确认删除远端歌单“$playlistName”吗？删除后会同步到服务器。"
                } else {
                    "确认删除“$playlistName”吗？本地和已同步的远端歌单都会一起删除。"
                },
            )
        },
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
private fun PlaylistTextImportDialog(
    playlistName: String,
    isOnlineMode: Boolean = false,
    isImporting: Boolean,
    report: PlaylistImportReport?,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    val shellColors = mainShellColors
    val appDensity = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    var text by rememberSaveable(playlistName) { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = shellColors.cardBorder,
        unfocusedBorderColor = shellColors.cardBorder,
        disabledBorderColor = shellColors.cardBorder,
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalDensity provides appDensity) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                val dialogLayout = playlistImportDialogLayout(maxWidth = maxWidth, maxHeight = maxHeight)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = dialogLayout.outerHorizontalPadding,
                            vertical = dialogLayout.outerVerticalPadding,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.94f)
                            .widthIn(max = 460.dp)
                            .heightIn(max = dialogLayout.maxHeight),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = shellColors.navContainer),
                        border = BorderStroke(1.dp, shellColors.cardBorder),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = dialogLayout.maxHeight)
                                .padding(
                                    horizontal = dialogLayout.contentHorizontalPadding,
                                    vertical = dialogLayout.contentVerticalPadding,
                                ),
                            verticalArrangement = Arrangement.spacedBy(dialogLayout.verticalSpacing),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "导入歌单",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (isOnlineMode) {
                                        "导入到远端歌单“$playlistName”。每行使用“歌名 - 歌手”，会远端搜索 Navidrome 曲库并加入当前远端歌单。"
                                    } else {
                                        "导入到“$playlistName”。每行使用“歌名 - 歌手”，只会匹配已有曲库中的歌曲。"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                ImeAwareOutlinedTextField(
                                    value = text,
                                    onValueChange = { text = it },
                                    label = { Text("导入内容") },
                                    placeholder = { Text("喜欢你 - BEYOND\n唯一 - 邓紫棋") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    minLines = dialogLayout.textFieldLines,
                                    maxLines = dialogLayout.textFieldLines,
                                    colors = fieldColors,
                                )
                                report?.let { PlaylistImportReportContent(it) }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = onDismiss) {
                                    Text("关闭", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    onClick = { uriHandler.openUri(PlaylistImportAssistantUrl) },
                                ) {
                                    Text("助手", color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    onClick = { onImport(text) },
                                    enabled = canConfirmPlaylistImport(text, isImporting),
                                ) {
                                    Text(
                                        text = if (isImporting) "导入中" else "导入",
                                        color = if (canConfirmPlaylistImport(text, isImporting)) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistImportReportContent(report: PlaylistImportReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(mainShellColors.cardContainer.copy(alpha = 0.65f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = playlistImportReportSummary(report),
            fontWeight = FontWeight.SemiBold,
        )
        if (report.hasIssues) {
            PlaylistImportIssueGroup("格式错误", report.malformedLines.map(::formatPlaylistImportLineIssue))
            PlaylistImportIssueGroup("未匹配", report.notMatchedLines.map(::formatPlaylistImportLineIssue))
            PlaylistImportIssueGroup(
                title = "多个匹配",
                lines = report.ambiguousLines.map { issue ->
                    "第 ${issue.lineNumber} 行：${issue.rawText}（${issue.matchCount} 个匹配）"
                },
            )
            PlaylistImportIssueGroup(
                title = "加入失败",
                lines = report.failedLines.map { issue ->
                    "第 ${issue.lineNumber} 行：${issue.rawText}（${issue.message}）"
                },
            )
            if (report.duplicateInputCount > 0) {
                Text(
                    text = "输入内重复：${report.duplicateInputCount} 首",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PlaylistImportIssueGroup(
    title: String,
    lines: List<String>,
) {
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
        )
        lines.forEach { line ->
            Text(
                text = line,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

internal fun canConfirmPlaylistImport(
    text: String,
    isImporting: Boolean,
): Boolean = text.trim().isNotEmpty() && !isImporting

internal fun canShowPlaylistImportAction(detail: PlaylistDetail?): Boolean = detail != null

internal const val PlaylistImportAssistantUrl = "https://music.unmeta.cn/"

internal data class PlaylistImportDialogLayout(
    val outerHorizontalPadding: Dp,
    val outerVerticalPadding: Dp,
    val contentHorizontalPadding: Dp,
    val contentVerticalPadding: Dp,
    val verticalSpacing: Dp,
    val maxHeight: Dp,
    val textFieldLines: Int,
)

internal fun playlistImportDialogLayout(
    maxWidth: Dp,
    maxHeight: Dp,
): PlaylistImportDialogLayout {
    val outerHorizontalPadding = when {
        maxWidth < 360.dp -> 12.dp
        maxWidth < 430.dp -> 16.dp
        else -> 20.dp
    }
    val outerVerticalPadding = when {
        maxHeight < 460.dp -> 8.dp
        maxHeight < 620.dp -> 16.dp
        else -> 24.dp
    }
    val contentHorizontalPadding = when {
        maxWidth < 360.dp || maxHeight < 460.dp -> 16.dp
        maxHeight < 620.dp -> 20.dp
        else -> 24.dp
    }
    val contentVerticalPadding = when {
        maxHeight < 460.dp -> 14.dp
        maxHeight < 620.dp -> 18.dp
        else -> 22.dp
    }
    val availableHeight = maxDp(280.dp, maxHeight - outerVerticalPadding - outerVerticalPadding)
    val scaledHeightCap = maxDp(320.dp, maxHeight * 0.78f)
    val dialogMaxHeight = minDp(playlistImportDialogMaxHeight(), minDp(availableHeight, scaledHeightCap))
    return PlaylistImportDialogLayout(
        outerHorizontalPadding = outerHorizontalPadding,
        outerVerticalPadding = outerVerticalPadding,
        contentHorizontalPadding = contentHorizontalPadding,
        contentVerticalPadding = contentVerticalPadding,
        verticalSpacing = if (dialogMaxHeight < 420.dp) 10.dp else 14.dp,
        maxHeight = dialogMaxHeight,
        textFieldLines = playlistImportTextFieldLines(dialogMaxHeight),
    )
}

internal fun playlistImportTextFieldLines(dialogHeight: Dp): Int {
    return when {
        dialogHeight < 360.dp -> 2
        dialogHeight < 440.dp -> 3
        dialogHeight < 520.dp -> 4
        else -> 6
    }
}

internal fun playlistImportDialogMaxHeight(): Dp = 560.dp

private fun minDp(first: Dp, second: Dp): Dp = if (first < second) first else second

private fun maxDp(first: Dp, second: Dp): Dp = if (first > second) first else second

internal fun playlistImportReportSummary(report: PlaylistImportReport): String {
    val parts = mutableListOf("已加入 ${report.addedCount} 首")
    if (report.alreadyExistsCount > 0) {
        parts += "已存在 ${report.alreadyExistsCount} 首"
    }
    if (report.duplicateInputCount > 0) {
        parts += "重复 ${report.duplicateInputCount} 首"
    }
    val issueCount = report.malformedLines.size +
        report.notMatchedLines.size +
        report.ambiguousLines.size +
        report.failedLines.size
    if (issueCount > 0) {
        parts += "未导入 $issueCount 行"
    }
    return parts.joinToString("，")
}

private fun formatPlaylistImportLineIssue(
    issue: top.iwesley.lyn.music.data.repository.PlaylistImportLineIssue,
): String {
    return "第 ${issue.lineNumber} 行：${issue.rawText}"
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PlaylistDetailPane(
    detail: PlaylistDetail?,
    isLoadingContent: Boolean,
    isDetailSwitchLoading: Boolean,
    requestedPlaylistName: String?,
    hasTracksOutsideFilter: Boolean,
    onBack: () -> Unit,
    onPlayAll: (List<Track>) -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    isImportingPlaylist: Boolean,
    onImportPlaylist: () -> Unit,
    showImportPlaylistAction: Boolean = true,
    onRemoveTrack: (String, Int) -> Unit,
    showTrackDuration: Boolean,
    allowLocalIndexActions: Boolean = true,
    modifier: Modifier = Modifier,
    showBackButton: Boolean,
    batchSelectionRequestKey: Int = 0,
    showInlineBatchOperationButton: Boolean = true,
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
    val supportsBatchDownload = allowLocalIndexActions && supportsBatchOfflineDownloadActions() && onOfflineDownloadIntent != null
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
        contentPadding = dockContentPadding(PaddingValues(20.dp)),
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
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onPlayAll(detail.tracks.map { it.track }) },
                            enabled = detail.tracks.isNotEmpty(),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("播放全部")
                        }
                        if (showImportPlaylistAction) {
                            OutlinedButton(
                                onClick = onImportPlaylist,
                                enabled = canShowPlaylistImportAction(detail) && !isImportingPlaylist,
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.List, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isImportingPlaylist) "导入中" else "导入歌单",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (
                            supportsBatchDownload &&
                            inlineBatchOperationButtonVisible &&
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
                    onRemove = { onRemoveTrack(item.track.id, index) },
                    showDuration = showTrackDuration,
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
        LibrarySourceFilter.SUBSONIC -> "Subsonic"
        LibrarySourceFilter.EMBY -> "Emby"
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
    showDuration: Boolean,
) {
    val shellColors = mainShellColors
    val offlineDownload = LocalOfflineDownloadUiState.current.downloadsByTrackId[entry.track.id]
    val offlineRowIndicatorState = offlineDownloadRowIndicatorState(offlineDownload)
    val rowClick = if (selectionMode) {
        onSelectionToggle ?: {}
    } else {
        onClick
    }
    val trailingWidth = playlistTrackTrailingWidth(selectionMode = selectionMode, showDuration = showDuration)
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
            PlaylistArtworkThumbnail(
                artworkLocator = entry.track.artworkLocator,
                artworkCacheKey = trackArtworkCacheKey(entry.track),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        entry.track.artistName ?: "未知艺人",
                        modifier = Modifier.weight(1f, fill = false),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    offlineRowIndicatorState?.let { OfflineDownloadRowIndicator(it) }
                }
            }
            if (trailingWidth > 0.dp) {
                Row(
                    modifier = Modifier.width(trailingWidth),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!selectionMode) {
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "移出歌单",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    if (showDuration) {
                        Text(
                            text = formatDuration(entry.track.durationMs),
                            modifier = Modifier.width(56.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        )
                    }
                }
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

internal fun playlistTrackTrailingWidth(
    selectionMode: Boolean,
    showDuration: Boolean,
): Dp {
    return when {
        !selectionMode && showDuration -> 112.dp
        !selectionMode -> 48.dp
        showDuration -> 56.dp
        else -> 0.dp
    }
}

@Composable
private fun PlaylistNameDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    title: String = "新建歌单",
    initialName: String = "",
    confirmText: String = "创建",
) {
    val shellColors = mainShellColors
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
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
                text = title,
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
    artworkCacheKey: String? = null,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 1.dp,
    containerColor: Color? = null,
    fallbackTint: Color? = null,
) {
    val resolvedContainerColor = containerColor ?: mainShellColors.cardContainer
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(resolvedContainerColor)
            .padding(0.dp),
        contentAlignment = Alignment.Center,
    ) {
        LynArtworkImage(
            artworkLocator = artworkLocator,
            contentDescription = null,
            artworkCacheKey = artworkCacheKey,
            maxDecodeSizePx = ArtworkDecodeSize.Thumbnail,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
    }
}
