package top.iwesley.lyn.music.automotive.ui

import top.iwesley.lyn.music.*

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.uiPlural
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.UiText

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.iwesley.lyn.music.core.model.PlaylistAddTarget
import top.iwesley.lyn.music.core.model.PlaylistDetail
import top.iwesley.lyn.music.core.model.PlaylistKind
import top.iwesley.lyn.music.core.model.PlaylistSummary
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.data.repository.PlaylistImportReport
import top.iwesley.lyn.music.data.repository.PlaylistImportFailedLineIssue
import top.iwesley.lyn.music.data.repository.messageUiText
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.LibrarySourceFilterOption
import top.iwesley.lyn.music.feature.library.librarySourceFilterButtonLabel
import top.iwesley.lyn.music.feature.online.OnlinePlaylistsIntent
import top.iwesley.lyn.music.feature.online.OnlinePlaylistsState
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.playlists.PlaylistsIntent
import top.iwesley.lyn.music.feature.playlists.PlaylistsState
import top.iwesley.lyn.music.ui.mainShellColors

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
        title = { Text(uiString(Res.string.playlist_add_track)) },
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
                                text = uiString(Res.string.playlists_loading_progress),
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
                                        text = target.nameText?.displayText() ?: target.name,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (disabled) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    Text(
                                        text = if (disabled) uiString(Res.string.playlist_import_already_added_status) else when (target.kind) {
                                            PlaylistKind.SYSTEM_LIKED -> uiString(Res.string.playlist_add_to_favorites)
                                            PlaylistKind.USER -> uiString(Res.string.playlist_add_track_title)
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
                    label = { Text(uiString(Res.string.playlist_new_title)) },
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
                    Text(uiString(Res.string.playlist_create_and_add))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedTarget?.let(onAddTarget) },
                enabled = selectedTarget != null,
            ) {
                Text(
                    text = uiString(Res.string.playlist_add_action),
                    color = if (selectedTarget != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(uiString(Res.string.common_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    text = uiString(Res.string.playlist_save_action),
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
            confirmText = uiString(Res.string.playlist_create_and_add),
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
            text = uiString(Res.string.playlist_new_title),
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
            text = uiString(Res.string.playlists_loading_progress),
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
            text = uiString(Res.string.playlists_unavailable_title),
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
            text = target.nameText?.displayText() ?: target.name,
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
                text = uiString(Res.string.playlist_import_added_status),
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
    val navigation = LocalMusicDetailNavigation.current
    var showCreateDialog by remember { mutableStateOf(false) }
    val entry = LocalAutomotivePlaylistsEntry.current ?: rememberAutomotivePlaylistsEntry()
    val sourceId = onlineState.sourceId
    LaunchedEffect(showRefreshActionButton) { if (showRefreshActionButton) entry.closeSearch() }
    val isOnline = onlineState.sourceId != null
    val playlists = if (isOnline) onlineState.playlists else state.playlists
    val filteredPlaylists = remember(playlists, playlistSearchQuery, showRefreshActionButton) { filterPlaylistsByQuery(playlists, if (showRefreshActionButton) "" else playlistSearchQuery) }
    val listState = entry.list
    fun openPlaylist(playlist: PlaylistSummary) {
        if (navigation?.hasDetails != false) return
        navigation.openPlaylist(playlist, onlineState.sourceId, state.selectedSourceFilter)
    }
    LaunchedEffect(playlists, entry.pendingCreation, state.message, onlineState.errorMessage) {
        val pending = entry.pendingCreation ?: return@LaunchedEffect
        if (pending.sourceId != sourceId || navigation?.hasDetails != false ||
            (if (isOnline) onlineState.errorMessage != null else state.message != null)) {
            entry.pendingCreation = null
            return@LaunchedEffect
        }
        val created = playlists.filter { it.id !in pending.previousIds && it.name == pending.name }
        if (created.size == 1) {
            entry.pendingCreation = null
            openPlaylist(created.single())
        } else if (created.size > 1) entry.pendingCreation = null
    }
    if (showCreateDialog) {
        PlaylistNameDialog(onDismiss = { showCreateDialog = false }, onConfirm = { name ->
            showCreateDialog = false
            if (isOnline) onOnlineIntent(OnlinePlaylistsIntent.ClearMessage) else onPlaylistsIntent(PlaylistsIntent.ClearMessage)
            entry.pendingCreation = PendingPlaylistCreation(sourceId, name.trim(), playlists.mapTo(mutableSetOf()) { it.id })
            if (isOnline) onOnlineIntent(OnlinePlaylistsIntent.CreatePlaylist(name))
            else onPlaylistsIntent(PlaylistsIntent.CreatePlaylist(name))
        })
    }
    PlaylistListPane(
        playlists = filteredPlaylists,
        isLoadingContent = if (isOnline) onlineState.isLoading || onlineState.isMutating else state.isLoadingContent,
        selectedPlaylistId = null,
        isRefreshing = if (isOnline) onlineState.isLoading || onlineState.isMutating else state.isRefreshing,
        selectedSourceFilter = state.selectedSourceFilter,
        sourceFilterOptions = state.sourceFilterOptions,
        onlineSourceOptions = importState.onlineNavidromeSourceOptions(),
        selectedOnlineSourceId = onlineState.sourceId,
        isOnlineMode = isOnline,
        isFilteringByQuery = playlistSearchQuery.isNotBlank() && playlists.isNotEmpty(),
        showRefreshActionButton = showRefreshActionButton,
        showSourceFilterActionButton = showSourceFilterActionButton,
        onRefresh = {
            if (isOnline) onOnlineIntent(OnlinePlaylistsIntent.Refresh)
            else onPlaylistsIntent(PlaylistsIntent.Refresh)
        },
        onSourceFilterChanged = {
            entry.list.requestScrollToItem(0)
            entry.pendingCreation = null
            onOnlineIntent(OnlinePlaylistsIntent.SelectSource(null))
            onPlaylistsIntent(PlaylistsIntent.SourceFilterChanged(it))
        },
        onOnlineSourceSelected = {
            if (it != sourceId) {
                entry.list.requestScrollToItem(0)
                entry.pendingCreation = null
            }
            onOnlineIntent(OnlinePlaylistsIntent.SelectSource(it))
        },
        onCreate = { showCreateDialog = true },
        onRename = { id, name ->
            if (isOnline) onOnlineIntent(OnlinePlaylistsIntent.RenamePlaylist(id, name))
            else onPlaylistsIntent(PlaylistsIntent.RenamePlaylist(id, name))
        },
        onDelete = {
            if (isOnline) onOnlineIntent(OnlinePlaylistsIntent.DeletePlaylist(it))
            else onPlaylistsIntent(PlaylistsIntent.DeletePlaylist(it))
        },
        onSelect = { id -> playlists.firstOrNull { it.id == id }?.let(::openPlaylist) },
        listState = listState,
        menuContext = Triple(sourceId, state.selectedSourceFilter, playlistSearchQuery),
        modifier = modifier.fillMaxSize(),
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
    sourceFilterOptions: List<LibrarySourceFilterOption>,
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
    listState: androidx.compose.foundation.lazy.LazyListState,
    menuContext: Any?,
    modifier: Modifier = Modifier,
) {
    var sourceFilterMenuExpanded by remember { mutableStateOf(false) }
    val statusList = androidx.compose.foundation.lazy.rememberLazyListState()
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

    LaunchedEffect(menuContext) {
        menuPlaylistId = null
        pendingRenamePlaylistId = null
        pendingDeletePlaylistId = null
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val padding = if (maxWidth >= 600.dp) 24.dp else 20.dp
        LazyColumn(
            state = if (isLoadingContent) statusList else listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = dockContentPadding(PaddingValues(horizontal = padding, vertical = 20.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PlaylistSectionTitle(
                    title = uiString(Res.string.playlists_title),
                    subtitle = if (isOnlineMode) {
                        uiString(Res.string.playlists_online_sync_hint)
                    } else {
                        uiString(Res.string.playlists_supported_track_types_hint)
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
                            text = uiString(Res.string.playlist_new_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (showRefreshActionButton) {
                        OutlinedButton(onClick = onRefresh) {
                            Icon(Icons.Rounded.Sync, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isRefreshing) uiString(Res.string.common_syncing) else uiString(Res.string.playlist_sync_remote_action),
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
                                    text = selectedOnlineSourceLabel?.displayText()
                                        ?: librarySourceFilterButtonLabel(selectedSourceFilter, sourceFilterOptions).displayText(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            DropdownMenu(
                                expanded = sourceFilterMenuExpanded,
                                onDismissRequest = { sourceFilterMenuExpanded = false },
                                containerColor = mainShellColors.navContainer,
                            ) {
                                sourceFilterOptions.forEach { option ->
                                    val filter = option.filter
                                    DropdownMenuItem(
                                        text = { Text(option.label.displayText(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        trailingIcon = if (selectedOnlineSourceId == null && filter == selectedSourceFilter) {
                                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                                        } else null,
                                        onClick = {
                                            sourceFilterMenuExpanded = false
                                            onSourceFilterChanged(filter)
                                        },
                                    )
                                }
                                onlineSourceOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label.displayText(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        trailingIcon = if (option.sourceId == selectedOnlineSourceId) {
                                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                                        } else null,
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
                        title = uiString(Res.string.playlists_loading_title),
                        body = uiString(Res.string.playlist_background_indexing_hint),
                    )
                }
            } else if (playlists.isEmpty()) {
                item {
                    if (isFilteringByQuery) {
                        EmptyStateCard(
                            title = uiString(Res.string.playlists_search_empty_title),
                            body = uiString(Res.string.playlists_search_empty_hint),
                        )
                    } else {
                        EmptyStateCard(
                            title = uiString(Res.string.playlists_local_empty_title),
                            body = if (isOnlineMode) {
                                uiString(Res.string.playlists_remote_empty_hint)
                            } else {
                                uiString(Res.string.playlists_create_empty_hint)
                            },
                        )
                    }
                }
            } else {
                items(playlists, key = { "playlist:${selectedOnlineSourceId.orEmpty()}:${it.id}" }) { playlist ->
                    PlaylistSummaryCard(
                        playlist = playlist,
                        selected = playlist.id == selectedPlaylistId,
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
                title = uiString(Res.string.playlist_rename_title),
                initialName = pendingRenamePlaylistName.ifBlank { playlist.name },
                confirmText = uiString(Res.string.common_save),
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
    menuExpanded: Boolean,
    onClick: () -> Unit,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onRequestRename: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val shellColors = mainShellColors
    val cardShape = RoundedCornerShape(24.dp)
    val interactionModifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onOpenMenu)
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        uiString(Res.plurals.common_track_count, (playlist.trackCount).toInt(), playlist.trackCount),
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                ListItemMoreButton(uiString(Res.string.track_more_actions, playlist.name), onClick = onOpenMenu)
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = onDismissMenu,
            containerColor = shellColors.navContainer,
        ) {
            DropdownMenuItem(
                text = { Text(uiString(Res.string.playlist_rename_action)) },
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
                        text = uiString(Res.string.playlist_delete_title),
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
        title = { Text(uiString(Res.string.playlist_delete_title)) },
        text = {
            Text(
                if (isOnlineMode) {
                    uiString(Res.string.playlist_delete_remote_confirmation, playlistName)
                } else {
                    uiString(Res.string.playlist_delete_synced_confirmation, playlistName)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = uiString(Res.string.common_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(uiString(Res.string.common_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
internal fun PlaylistTextImportDialog(
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
                                    text = uiString(Res.string.playlist_import_title),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (isOnlineMode) {
                                        uiString(Res.string.playlist_import_remote_hint, playlistName)
                                    } else {
                                        uiString(Res.string.playlist_import_local_hint, playlistName)
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
                                    label = { Text(uiString(Res.string.playlist_import_text_label)) },
                                    placeholder = { Text(uiString(Res.string.playlist_import_text_example)) },
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
                                    Text(uiString(Res.string.common_close), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    onClick = { uriHandler.openUri(PlaylistImportAssistantUrl) },
                                ) {
                                    Text(uiString(Res.string.playlist_import_assistant_title), color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    onClick = { onImport(text) },
                                    enabled = canConfirmPlaylistImport(text, isImporting),
                                ) {
                                    Text(
                                        text = if (isImporting) uiString(Res.string.playlist_importing_status) else uiString(Res.string.playlist_import_action),
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
            PlaylistImportIssueGroup(uiString(Res.string.playlist_import_invalid_format_status), report.malformedLines.map { formatPlaylistImportLineIssue(it) })
            PlaylistImportIssueGroup(uiString(Res.string.playlist_import_no_match_status), report.notMatchedLines.map { formatPlaylistImportLineIssue(it) })
            PlaylistImportIssueGroup(
                title = uiString(Res.string.playlist_import_multiple_matches_status),
                lines = report.ambiguousLines.map { issue ->
                    uiString(Res.string.playlist_import_line_with_match_count, issue.lineNumber, issue.rawText, issue.matchCount)
                },
            )
            PlaylistImportIssueGroup(
                title = uiString(Res.string.playlist_import_failed_status),
                lines = report.failedLines.map { formatPlaylistImportFailedLineIssue(it) },
            )
            if (report.duplicateInputCount > 0) {
                Text(
                    text = uiString(Res.plurals.playlist_import_input_duplicate_count, (report.duplicateInputCount).toInt(), report.duplicateInputCount),
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

internal fun playlistImportReportSummaryText(report: PlaylistImportReport): UiText {
    val parts = mutableListOf(uiPlural(Res.plurals.playlist_import_added_track_count, (report.addedCount).toInt(), report.addedCount))
    if (report.alreadyExistsCount > 0) {
        parts += uiPlural(Res.plurals.playlist_import_existing_track_count, (report.alreadyExistsCount).toInt(), report.alreadyExistsCount)
    }
    if (report.duplicateInputCount > 0) {
        parts += uiPlural(Res.plurals.playlist_import_duplicate_track_count, (report.duplicateInputCount).toInt(), report.duplicateInputCount)
    }
    val issueCount = report.malformedLines.size +
        report.notMatchedLines.size +
        report.ambiguousLines.size +
        report.failedLines.size
    if (issueCount > 0) {
        parts += uiPlural(Res.plurals.playlist_import_failed_line_count, (issueCount).toInt(), issueCount)
    }
    return UiText.Joined(parts, separatorText = uiText(Res.string.ui_list_separator))
}

@Composable
internal fun playlistImportReportSummary(report: PlaylistImportReport): String = playlistImportReportSummaryText(report).displayText()

internal fun formatPlaylistImportFailedLineIssueText(issue: PlaylistImportFailedLineIssue): UiText =
    uiText(Res.string.playlist_import_line_with_reason, issue.lineNumber, issue.rawText, issue.messageUiText())

@Composable
internal fun formatPlaylistImportFailedLineIssue(issue: PlaylistImportFailedLineIssue): String = formatPlaylistImportFailedLineIssueText(issue).displayText()

@Composable
private fun formatPlaylistImportLineIssue(
    issue: top.iwesley.lyn.music.data.repository.PlaylistImportLineIssue,
): String {
    return uiString(Res.string.playlist_import_line_label, issue.lineNumber, issue.rawText)
}

@Composable
private fun PlaylistNameDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    title: String = uiString(Res.string.playlist_new_title),
    initialName: String = "",
    confirmText: String = uiString(Res.string.playlist_create_action),
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
                label = { Text(uiString(Res.string.playlist_name_label)) },
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
                Text(uiString(Res.string.common_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
