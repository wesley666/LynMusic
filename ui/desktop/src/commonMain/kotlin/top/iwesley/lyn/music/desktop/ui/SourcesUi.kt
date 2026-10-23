package top.iwesley.lyn.music.desktop.ui

import top.iwesley.lyn.music.*

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.plus

import top.iwesley.lyn.music.core.model.reasonUiText

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.iwesley.lyn.music.core.model.ImportScanPhase
import top.iwesley.lyn.music.core.model.ImportScanProgress
import top.iwesley.lyn.music.core.model.ImportScanSummary
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.LocalFolderPickerMode
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.feature.importing.ImportIntent
import top.iwesley.lyn.music.feature.importing.ImportScanOperation
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.importing.PendingLargeNavidromeAction
import top.iwesley.lyn.music.ui.mainShellColors

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
    val localFolderClickAction = remember(state.capabilities.supportsSystemLocalFolderPicker) {
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
            constrainWidth = true,
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
            constrainWidth = true,
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

internal enum class LocalFolderImportClickAction {
    ShowPickerModeDialog,
    ImportBuiltIn,
    ImportAutomatic,
}

internal fun resolveLocalFolderImportClickAction(platform: PlatformDescriptor): LocalFolderImportClickAction =
    LocalFolderImportClickAction.ImportAutomatic

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
internal fun addSourceTypeLabel(type: ImportSourceType): String = when (type) {
    ImportSourceType.LOCAL_FOLDER -> uiString(Res.string.source_local_folder_label)
    ImportSourceType.SAMBA -> "Samba / SMB"
    ImportSourceType.WEBDAV -> "WebDAV"
    ImportSourceType.NAVIDROME -> "Navidrome"
    ImportSourceType.SUBSONIC -> "Subsonic / OpenSubsonic"
    ImportSourceType.EMBY -> "Emby"
    ImportSourceType.FN_MUSIC -> uiString(Res.string.fn_music_name)
}
