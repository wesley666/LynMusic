package top.iwesley.lyn.music.desktop.ui

import top.iwesley.lyn.music.*

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.UiText

import top.iwesley.lyn.music.core.model.plus

import top.iwesley.lyn.music.core.model.uiPlural
import top.iwesley.lyn.music.core.model.formatSambaEndpoint
import top.iwesley.lyn.music.core.model.uiText

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.iwesley.lyn.music.core.model.ImportScanProgress
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.core.model.FnMusicConnectionMode
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.LocalFolderPickerMode
import top.iwesley.lyn.music.core.model.SubsonicAuthMode
import top.iwesley.lyn.music.feature.importing.ImportIntent
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.importing.RemoteFolderRow
import top.iwesley.lyn.music.feature.importing.RemoteFolderTreeState
import top.iwesley.lyn.music.ui.mainShellColors

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
internal fun FnMusicConnectionFields(
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
    clearAccessCode: Boolean = false,
    onClearAccessCodeChange: (Boolean) -> Unit = {},
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
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = fieldColors,
            )
            ImeAwareOutlinedTextField(
                value = wanBaseUrl,
                onValueChange = onWanBaseUrlChange,
                label = { Text(uiString(Res.string.source_wan_address_label)) },
                placeholder = { Text("https://music.example.com") },
                enabled = enabled,
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
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = fieldColors,
            )
        }
    }
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
        enabled = enabled && !clearAccessCode,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = fieldColors,
    )
    if (isEditing) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(uiString(Res.string.fn_music_clear_access_code_label), modifier = Modifier.weight(1f))
            Switch(checked = clearAccessCode, onCheckedChange = onClearAccessCodeChange, enabled = enabled)
        }
    }
}

@Composable
fun LargeNavidromeLibraryDialog(
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
fun LocalFolderPickerModeDialog(
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

internal fun localFolderPickerDialogSystemMode(): LocalFolderPickerMode {
    return LocalFolderPickerMode.Automatic
}

@Composable
fun RemoteSourceCreatorDialog(
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
fun RemoteSourceEditorDialog(
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
                clearAccessCode = state.clearFnMusicAccessCode,
                onClearAccessCodeChange = { onIntent(ImportIntent.RemoteSourceFnMusicClearAccessCodeChanged(it)) },
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
