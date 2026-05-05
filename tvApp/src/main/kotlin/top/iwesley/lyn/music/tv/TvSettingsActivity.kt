package top.iwesley.lyn.music.tv

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.ButtonDefaults as TvButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults as TvOutlinedButtonDefaults
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.LynMusicAppComponent
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.AppStorageCategory
import top.iwesley.lyn.music.core.model.AppStorageCategoryUsage
import top.iwesley.lyn.music.core.model.BuildMetadata
import top.iwesley.lyn.music.core.model.DeviceInfoSnapshot
import top.iwesley.lyn.music.core.model.GlobalDiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.displayWebDavRootUrl
import top.iwesley.lyn.music.core.model.effectiveAppDisplayDensity
import top.iwesley.lyn.music.core.model.formatSambaEndpoint
import top.iwesley.lyn.music.feature.importing.ImportIntent
import top.iwesley.lyn.music.feature.importing.ImportScanOperation
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.importing.RemoteSourceEditorState
import top.iwesley.lyn.music.feature.importing.formatImportScanSummary
import top.iwesley.lyn.music.feature.settings.SettingsIntent
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.platform.AndroidLocalFolderPicker
import top.iwesley.lyn.music.tv.ui.TvMainTheme

private val TvSettingsPanelShape = RoundedCornerShape(18.dp)
private val TvSettingsItemShape = RoundedCornerShape(14.dp)

class TvSettingsActivity : ComponentActivity() {
    private lateinit var localFolderPicker: AndroidLocalFolderPicker

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        localFolderPicker = AndroidLocalFolderPicker(this, GlobalDiagnosticLogger)

        setContent {
            val component = TvAppComponentHolder.current()
            if (component == null) {
                TvMainTheme {
                    TvSettingsUnavailableScreen(onBack = ::finish)
                }
                return@setContent
            }
            val appDisplayScalePreset by component.appDisplayScalePreset.collectAsState()
            ProvideTvSettingsDensity(appDisplayScalePreset) {
                TvSettingsApp(
                    component = component,
                    pickLocalFolder = localFolderPicker::pickLocalFolder,
                    onBack = ::finish,
                )
            }
        }
    }

    companion object {
        internal fun createIntent(context: Context): Intent {
            return Intent(context, TvSettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }
}

@Composable
private fun TvSettingsApp(
    component: LynMusicAppComponent,
    pickLocalFolder: suspend () -> LocalFolderSelection?,
    onBack: () -> Unit,
) {
    val importState by component.importStore.state.collectAsState()
    val settingsState by component.settingsStore.state.collectAsState()

    TvMainTheme {
        TvSettingsScreen(
            platformName = component.platform.name,
            importState = importState,
            settingsState = settingsState,
            onImportIntent = component.importStore::dispatch,
            onSettingsIntent = component.settingsStore::dispatch,
            pickLocalFolder = pickLocalFolder,
            onBack = onBack,
        )
    }
}

@Composable
private fun TvSettingsScreen(
    platformName: String,
    importState: ImportState,
    settingsState: SettingsState,
    onImportIntent: (ImportIntent) -> Unit,
    onSettingsIntent: (SettingsIntent) -> Unit,
    pickLocalFolder: suspend () -> LocalFolderSelection?,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var selectedSection by remember { mutableStateOf(TvSettingsSection.Sources) }
    val sourcesFocusRequester = remember { FocusRequester() }
    val storageFocusRequester = remember { FocusRequester() }
    val aboutDeviceFocusRequester = remember { FocusRequester() }
    val aboutAppFocusRequester = remember { FocusRequester() }
    val contentInitialFocusRequester = remember { FocusRequester() }
    val focusCoordinator = remember { TvSettingsFocusCoordinator() }
    val selectedSectionFocusRequester = when (selectedSection) {
        TvSettingsSection.Sources -> sourcesFocusRequester
        TvSettingsSection.Storage -> storageFocusRequester
        TvSettingsSection.AboutDevice -> aboutDeviceFocusRequester
        TvSettingsSection.AboutApp -> aboutAppFocusRequester
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 46.dp, top = 38.dp, end = 48.dp, bottom = 38.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        TvSettingsNavigationPane(
            selectedSection = selectedSection,
            sourcesFocusRequester = sourcesFocusRequester,
            storageFocusRequester = storageFocusRequester,
            aboutDeviceFocusRequester = aboutDeviceFocusRequester,
            aboutAppFocusRequester = aboutAppFocusRequester,
            contentFocusRequester = contentInitialFocusRequester,
            focusCoordinator = focusCoordinator,
            onSectionSelected = { selectedSection = it },
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight(),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            when (selectedSection) {
                TvSettingsSection.Sources -> TvSourcesSettingsPane(
                    state = importState,
                    onIntent = onImportIntent,
                    pickLocalFolder = pickLocalFolder,
                    initialFocusRequester = contentInitialFocusRequester,
                    leftFocusRequester = selectedSectionFocusRequester,
                    focusCoordinator = focusCoordinator,
                    modifier = Modifier.fillMaxSize(),
                )

                TvSettingsSection.Storage -> TvStorageSettingsPane(
                    state = settingsState,
                    onIntent = onSettingsIntent,
                    initialFocusRequester = contentInitialFocusRequester,
                    leftFocusRequester = selectedSectionFocusRequester,
                    focusCoordinator = focusCoordinator,
                    modifier = Modifier.fillMaxSize(),
                )

                TvSettingsSection.AboutDevice -> TvAboutDeviceSettingsPane(
                    state = settingsState,
                    onIntent = onSettingsIntent,
                    initialFocusRequester = contentInitialFocusRequester,
                    leftFocusRequester = selectedSectionFocusRequester,
                    focusCoordinator = focusCoordinator,
                    modifier = Modifier.fillMaxSize(),
                )

                TvSettingsSection.AboutApp -> TvAboutAppSettingsPane(
                    platformName = platformName,
                    initialFocusRequester = contentInitialFocusRequester,
                    leftFocusRequester = selectedSectionFocusRequester,
                    focusCoordinator = focusCoordinator,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun TvSettingsNavigationPane(
    selectedSection: TvSettingsSection,
    sourcesFocusRequester: FocusRequester,
    storageFocusRequester: FocusRequester,
    aboutDeviceFocusRequester: FocusRequester,
    aboutAppFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    focusCoordinator: TvSettingsFocusCoordinator,
    onSectionSelected: (TvSettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(TvSettingsPanelShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "设置",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        TvSettingsSection.entries.forEach { section ->
            TvSettingsSectionCard(
                section = section,
                selected = selectedSection == section,
                focusRequester = when (section) {
                    TvSettingsSection.Sources -> sourcesFocusRequester
                    TvSettingsSection.Storage -> storageFocusRequester
                    TvSettingsSection.AboutDevice -> aboutDeviceFocusRequester
                    TvSettingsSection.AboutApp -> aboutAppFocusRequester
                },
                rightFocusRequester = contentFocusRequester,
                focusCoordinator = focusCoordinator,
                onSelected = { onSectionSelected(section) },
            )
        }
    }
}

@Composable
private fun TvSettingsSectionCard(
    section: TvSettingsSection,
    selected: Boolean,
    focusRequester: FocusRequester,
    rightFocusRequester: FocusRequester,
    focusCoordinator: TvSettingsFocusCoordinator,
    onSelected: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val selectIfNeeded = { if (!selected) onSelected() }
    val focusedContentColor = MaterialTheme.colorScheme.background
    val primaryContentColor = MaterialTheme.colorScheme.onSurface
    val subtitleColor = when {
        focused -> focusedContentColor.copy(alpha = 0.78f)
        selected -> Color.White.copy(alpha = 0.82f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        onClick = selectIfNeeded,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .focusRequester(focusRequester)
            .focusProperties {
                right = rightFocusRequester
            }
            .onFocusChanged { focusState ->
                focused = focusState.isFocused
                if (focusState.isFocused) {
                    if (focusCoordinator.restoreContentFocusIfRequested()) {
                        return@onFocusChanged
                    }
                    selectIfNeeded()
                }
            },
        scale = CardDefaults.scale(focusedScale = 1.01f, pressedScale = 1.01f),
        shape = CardDefaults.shape(
            shape = TvSettingsItemShape,
            focusedShape = TvSettingsItemShape,
            pressedShape = TvSettingsItemShape,
        ),
        colors = CardDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = Color.White,
            focusedContentColor = MaterialTheme.colorScheme.background,
            pressedContainerColor = Color.White,
            pressedContentColor = MaterialTheme.colorScheme.background,
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
            pressedBorder = Border.None,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                section.icon,
                contentDescription = null,
                tint = if (focused) focusedContentColor else if (selected) Color.White else primaryContentColor,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    section.title,
                    color = if (focused) focusedContentColor else if (selected) Color.White else primaryContentColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    section.subtitle,
                    color = subtitleColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TvSourcesSettingsPane(
    state: ImportState,
    onIntent: (ImportIntent) -> Unit,
    pickLocalFolder: suspend () -> LocalFolderSelection?,
    initialFocusRequester: FocusRequester,
    leftFocusRequester: FocusRequester,
    focusCoordinator: TvSettingsFocusCoordinator,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<SourceWithStatus?>(null) }
    val listState = rememberLazyListState()
    val showPageTestMessage = state.testMessage != null &&
        state.creatingSourceType == null &&
        state.editingSource == null
    val sourceFocusRows = remember(
        state.capabilities,
        state.sources,
        state.message,
        showPageTestMessage,
    ) {
        buildList {
            if (state.message != null) {
                add(listOf("sources:message:clear"))
            }
            if (showPageTestMessage) {
                add(listOf("sources:test-message:clear"))
            }
            val addSourceRow = buildList {
                if (state.capabilities.supportsLocalFolderImport) {
                    add("sources:add:local")
                }
                if (state.capabilities.supportsSambaImport) {
                    add("sources:add:samba")
                }
                if (state.capabilities.supportsWebDavImport) {
                    add("sources:add:webdav")
                }
                if (state.capabilities.supportsNavidromeImport) {
                    add("sources:add:navidrome")
                }
            }
            if (addSourceRow.isNotEmpty()) {
                add(addSourceRow)
            }
            state.sources.forEach { sourceWithStatus ->
                val source = sourceWithStatus.source
                val sourceRow = buildList {
                    add("sources:${source.id}:rescan")
                    add("sources:${source.id}:toggle")
                    if (source.type != ImportSourceType.LOCAL_FOLDER) {
                        add("sources:${source.id}:edit")
                    }
                    add("sources:${source.id}:delete")
                }
                add(sourceRow)
            }
        }
    }
    val sourcesFallbackFocusKey = "sources:fallback"
    val focusRows = sourceFocusRows.ifEmpty { listOf(listOf(sourcesFallbackFocusKey)) }
    val focusChain = rememberTvSettingsFocusChain(
        focusRows = focusRows,
        initialFocusRequester = initialFocusRequester,
        leftFocusRequester = leftFocusRequester,
        listState = listState,
        focusCoordinator = focusCoordinator,
    )
    var previousCreatingSourceType by remember { mutableStateOf<ImportSourceType?>(null) }
    LaunchedEffect(state.creatingSourceType, focusChain) {
        if (previousCreatingSourceType != null && state.creatingSourceType == null) {
            focusChain.requestRestoreAfterAction()
        }
        previousCreatingSourceType = state.creatingSourceType
    }

    pendingDelete?.let { sourceWithStatus ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除来源") },
            text = { Text("确认删除“${sourceWithStatus.source.label.ifBlank { sourceTypeTitle(sourceWithStatus.source.type) }}”吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onIntent(ImportIntent.DeleteSource(sourceWithStatus.source.id))
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
    state.editingSource?.let { editor ->
        TvRemoteSourceEditorDialog(
            editor = editor,
            state = state,
            onIntent = onIntent,
        )
    }
    state.creatingSourceType?.let { type ->
        TvRemoteSourceCreatorDialog(
            type = type,
            state = state,
            onIntent = onIntent,
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier.tvSettingsScrollableFocus(listState, leftFocusRequester),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TvSettingsPaneHeader(
                title = "来源",
                subtitle = "管理本机、Samba、WebDAV 和 Navidrome 音乐来源。",
            )
        }
        if (sourceFocusRows.isEmpty()) {
            item {
                TvSettingsScrollAnchor(
                    modifier = Modifier.tvSettingsFocusTarget(sourcesFallbackFocusKey, focusChain),
                )
            }
        }
        state.message?.let { message ->
            item {
                TvSettingsMessageCard(
                    message = message,
                    onClear = { onIntent(ImportIntent.ClearMessage) },
                    focusKey = "sources:message:clear",
                    focusChain = focusChain,
                )
            }
        }
        if (showPageTestMessage) {
            item {
                TvSettingsMessageCard(
                    message = state.testMessage.orEmpty(),
                    onClear = { onIntent(ImportIntent.ClearTestMessage) },
                    focusKey = "sources:test-message:clear",
                    focusChain = focusChain,
                )
            }
        }
        item {
            TvAddSourcePanel(
                capabilities = state.capabilities,
                state = state,
                onIntent = onIntent,
                pickLocalFolder = pickLocalFolder,
                focusChain = focusChain,
            )
        }
        if (state.sources.isEmpty()) {
            item {
                TvSettingsEmptyCard(
                    title = "还没有来源",
                    body = "添加本地文件夹、Samba、WebDAV 或 Navidrome 后，曲库会开始扫描音乐。",
                )
            }
        } else {
            items(state.sources, key = { sourceWithStatus -> sourceWithStatus.source.id }) { sourceWithStatus ->
                TvSourceCard(
                    sourceWithStatus = sourceWithStatus,
                    latestSummary = state.latestScanSummariesBySourceId[sourceWithStatus.source.id],
                    working = state.isWorking,
                    activeScanOperation = state.activeScanOperation,
                    onIntent = onIntent,
                    onDelete = { pendingDelete = sourceWithStatus },
                    focusChain = focusChain,
                )
            }
        }
    }
}

@Composable
private fun TvAddSourcePanel(
    capabilities: PlatformCapabilities,
    state: ImportState,
    onIntent: (ImportIntent) -> Unit,
    pickLocalFolder: suspend () -> LocalFolderSelection?,
    focusChain: TvSettingsFocusChain,
) {
    val coroutineScope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TvSettingsPanelShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("新增来源", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (capabilities.supportsLocalFolderImport) {
                TvSettingsActionButton(
                    onClick = {
                        coroutineScope.launch {
                            pickLocalFolder()?.let { selection ->
                                onIntent(ImportIntent.ImportSelectedLocalFolder(selection))
                            }
                        }
                    },
                    enabled = !state.isWorking,
                    style = TvSettingsActionButtonStyle.Filled,
                    focusKey = "sources:add:local",
                    focusChain = focusChain,
                    restoreFocusAfterClick = false,
                ) { contentColor ->
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = contentColor)
                    Spacer(Modifier.width(8.dp))
                    Text("本地文件夹", color = contentColor)
                }
            }
            if (capabilities.supportsSambaImport) {
                TvAddTypeButton(
                    label = "Samba",
                    selected = state.creatingSourceType == ImportSourceType.SAMBA,
                    focusKey = "sources:add:samba",
                    focusChain = focusChain,
                    restoreFocusAfterClick = false,
                ) {
                    onIntent(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.SAMBA))
                }
            }
            if (capabilities.supportsWebDavImport) {
                TvAddTypeButton(
                    label = "WebDAV",
                    selected = state.creatingSourceType == ImportSourceType.WEBDAV,
                    focusKey = "sources:add:webdav",
                    focusChain = focusChain,
                    restoreFocusAfterClick = false,
                ) {
                    onIntent(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.WEBDAV))
                }
            }
            if (capabilities.supportsNavidromeImport) {
                TvAddTypeButton(
                    label = "Navidrome",
                    selected = state.creatingSourceType == ImportSourceType.NAVIDROME,
                    focusKey = "sources:add:navidrome",
                    focusChain = focusChain,
                    restoreFocusAfterClick = false,
                ) {
                    onIntent(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.NAVIDROME))
                }
            }
        }
    }
}

private enum class TvSettingsActionButtonStyle {
    Filled,
    Outlined,
    Selected,
}

@Composable
private fun TvSettingsActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TvSettingsActionButtonStyle = TvSettingsActionButtonStyle.Outlined,
    focusKey: String? = null,
    focusChain: TvSettingsFocusChain? = null,
    restoreFocusAfterClick: Boolean = true,
    content: @Composable (Color) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusedContentColor = MaterialTheme.colorScheme.background
    val normalContentColor = when (style) {
        TvSettingsActionButtonStyle.Filled,
        TvSettingsActionButtonStyle.Selected -> Color.White

        TvSettingsActionButtonStyle.Outlined -> MaterialTheme.colorScheme.onSurface
    }
    val targetKey = focusKey
    val targetChain = focusChain
    val focusTarget = if (targetKey != null && targetChain?.contains(targetKey) == true) {
        targetKey to targetChain
    } else {
        null
    }
    val focusable = focusTarget != null
    val contentColor = if (focused) focusedContentColor else normalContentColor
    var buttonModifier = modifier.onFocusChanged { focused = it.isFocused }
    focusTarget?.let { (targetKey, targetChain) ->
        buttonModifier = buttonModifier.tvSettingsFocusTarget(targetKey, targetChain)
    }
    val click = click@{
        if (!enabled) {
            return@click
        }
        if (restoreFocusAfterClick) {
            focusChain?.requestRestoreAfterAction()
        }
        onClick()
    }
    val buttonEnabled = enabled || focusable

    when (style) {
        TvSettingsActionButtonStyle.Filled,
        TvSettingsActionButtonStyle.Selected -> TvButton(
            onClick = click,
            enabled = buttonEnabled,
            modifier = buttonModifier,
            colors = TvButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = normalContentColor,
                focusedContainerColor = Color.White,
                focusedContentColor = focusedContentColor,
                pressedContainerColor = Color.White,
                pressedContentColor = focusedContentColor,
            ),
        ) {
            content(contentColor)
        }

        TvSettingsActionButtonStyle.Outlined -> TvOutlinedButton(
            onClick = click,
            enabled = buttonEnabled,
            modifier = buttonModifier,
            colors = TvOutlinedButtonDefaults.colors(
                focusedContainerColor = Color.White,
                focusedContentColor = focusedContentColor,
                pressedContainerColor = Color.White,
                pressedContentColor = focusedContentColor,
            ),
        ) {
            content(contentColor)
        }
    }
}

@Composable
private fun TvAddTypeButton(
    label: String,
    selected: Boolean,
    focusKey: String,
    focusChain: TvSettingsFocusChain,
    restoreFocusAfterClick: Boolean = true,
    onClick: () -> Unit,
) {
    TvSettingsActionButton(
        onClick = onClick,
        style = if (selected) TvSettingsActionButtonStyle.Selected else TvSettingsActionButtonStyle.Outlined,
        focusKey = focusKey,
        focusChain = focusChain,
        restoreFocusAfterClick = restoreFocusAfterClick,
    ) { contentColor ->
        Text(label, color = contentColor)
    }
}

@Composable
private fun TvRemoteSourceCreatorDialog(
    type: ImportSourceType,
    state: ImportState,
    onIntent: (ImportIntent) -> Unit,
) {
    Dialog(
        onDismissRequest = { onIntent(ImportIntent.DismissRemoteSourceCreator) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 720.dp, max = 880.dp)
                    .heightIn(max = 720.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = "新增${sourceTypeTitle(type)}来源",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (type) {
                        ImportSourceType.SAMBA -> TvSambaSourceForm(state = state, onIntent = onIntent)
                        ImportSourceType.WEBDAV -> TvWebDavSourceForm(state = state, onIntent = onIntent)
                        ImportSourceType.NAVIDROME -> TvNavidromeSourceForm(state = state, onIntent = onIntent)
                        ImportSourceType.LOCAL_FOLDER -> Unit
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        state.testMessage?.let { message ->
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    TvSettingsActionButton(
                        onClick = {
                            when (type) {
                                ImportSourceType.SAMBA -> onIntent(ImportIntent.TestSambaSource)
                                ImportSourceType.WEBDAV -> onIntent(ImportIntent.TestWebDavSource)
                                ImportSourceType.NAVIDROME -> onIntent(ImportIntent.TestNavidromeSource)
                                ImportSourceType.LOCAL_FOLDER -> Unit
                            }
                        },
                        enabled = !state.isWorking,
                    ) { contentColor ->
                        Text("测试", color = contentColor)
                    }
                    TvSettingsActionButton(
                        onClick = { onIntent(ImportIntent.DismissRemoteSourceCreator) },
                    ) { contentColor ->
                        Text("取消", color = contentColor)
                    }
                    TvSettingsActionButton(
                        onClick = {
                            when (type) {
                                ImportSourceType.SAMBA -> onIntent(ImportIntent.AddSambaSource)
                                ImportSourceType.WEBDAV -> onIntent(ImportIntent.AddWebDavSource)
                                ImportSourceType.NAVIDROME -> onIntent(ImportIntent.AddNavidromeSource)
                                ImportSourceType.LOCAL_FOLDER -> Unit
                            }
                        },
                        enabled = !state.isWorking,
                        style = TvSettingsActionButtonStyle.Filled,
                    ) { contentColor ->
                        Text("添加并扫描", color = contentColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSambaSourceForm(
    state: ImportState,
    onIntent: (ImportIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TvSettingsTextField(
            label = "名称",
            value = state.sambaLabel,
            onValueChange = { onIntent(ImportIntent.SambaLabelChanged(it)) },
            placeholder = "Samba",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvSettingsTextField(
                label = "服务器",
                value = state.sambaServer,
                onValueChange = { onIntent(ImportIntent.SambaServerChanged(it)) },
                placeholder = "192.168.31.115",
                modifier = Modifier.weight(1f),
            )
            TvSettingsTextField(
                label = "端口",
                value = state.sambaPort,
                onValueChange = { onIntent(ImportIntent.SambaPortChanged(it)) },
                placeholder = "445",
                modifier = Modifier.width(140.dp),
            )
        }
        TvSettingsTextField(
            label = "路径",
            value = state.sambaPath,
            onValueChange = { onIntent(ImportIntent.SambaPathChanged(it)) },
            placeholder = "共享文件/Music",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvSettingsTextField(
                label = "用户名",
                value = state.sambaUsername,
                onValueChange = { onIntent(ImportIntent.SambaUsernameChanged(it)) },
                modifier = Modifier.weight(1f),
            )
            TvSettingsTextField(
                label = "密码",
                value = state.sambaPassword,
                onValueChange = { onIntent(ImportIntent.SambaPasswordChanged(it)) },
                modifier = Modifier.weight(1f),
                password = true,
            )
        }
    }
}

@Composable
private fun TvWebDavSourceForm(
    state: ImportState,
    onIntent: (ImportIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TvSettingsTextField(
            label = "名称",
            value = state.webDavLabel,
            onValueChange = { onIntent(ImportIntent.WebDavLabelChanged(it)) },
            placeholder = "WebDAV",
        )
        TvSettingsTextField(
            label = "根地址",
            value = state.webDavRootUrl,
            onValueChange = { onIntent(ImportIntent.WebDavRootUrlChanged(it)) },
            placeholder = "http://192.168.31.115:5005/共享文件/music/",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvSettingsTextField(
                label = "用户名",
                value = state.webDavUsername,
                onValueChange = { onIntent(ImportIntent.WebDavUsernameChanged(it)) },
                modifier = Modifier.weight(1f),
            )
            TvSettingsTextField(
                label = "密码",
                value = state.webDavPassword,
                onValueChange = { onIntent(ImportIntent.WebDavPasswordChanged(it)) },
                modifier = Modifier.weight(1f),
                password = true,
            )
        }
        TvSettingsSwitchRow(
            title = "允许不安全 TLS",
            checked = state.webDavAllowInsecureTls,
            onCheckedChange = { onIntent(ImportIntent.WebDavAllowInsecureTlsChanged(it)) },
        )
    }
}

@Composable
private fun TvNavidromeSourceForm(
    state: ImportState,
    onIntent: (ImportIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TvSettingsTextField(
            label = "名称",
            value = state.navidromeLabel,
            onValueChange = { onIntent(ImportIntent.NavidromeLabelChanged(it)) },
            placeholder = "Navidrome",
        )
        TvSettingsTextField(
            label = "服务器地址",
            value = state.navidromeBaseUrl,
            onValueChange = { onIntent(ImportIntent.NavidromeBaseUrlChanged(it)) },
            placeholder = "http://192.168.31.115:32700",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvSettingsTextField(
                label = "用户名",
                value = state.navidromeUsername,
                onValueChange = { onIntent(ImportIntent.NavidromeUsernameChanged(it)) },
                modifier = Modifier.weight(1f),
            )
            TvSettingsTextField(
                label = "密码",
                value = state.navidromePassword,
                onValueChange = { onIntent(ImportIntent.NavidromePasswordChanged(it)) },
                modifier = Modifier.weight(1f),
                password = true,
            )
        }
    }
}

@Composable
private fun TvSourceCard(
    sourceWithStatus: SourceWithStatus,
    latestSummary: top.iwesley.lyn.music.core.model.ImportScanSummary?,
    working: Boolean,
    activeScanOperation: ImportScanOperation?,
    onIntent: (ImportIntent) -> Unit,
    onDelete: () -> Unit,
    focusChain: TvSettingsFocusChain,
) {
    val source = sourceWithStatus.source
    val busy = activeScanOperation?.sourceIdOrNull() == source.id || (working && activeScanOperation == null)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TvSettingsPanelShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvSettingsIconBox(sourceTypeIcon(source.type))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.label.ifBlank { sourceTypeTitle(source.type) },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (source.enabled) "已启用" else "已停用",
                        color = if (source.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(sourceTypeTitle(source.type), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = sourceDisplayReference(source),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            }
        }
        Text(
            text = sourceStatusText(sourceWithStatus, latestSummary),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvSettingsActionButton(
                onClick = { onIntent(ImportIntent.RescanSource(source.id)) },
                enabled = !working,
                focusKey = "sources:${source.id}:rescan",
                focusChain = focusChain,
            ) { contentColor ->
                Icon(Icons.Rounded.Sync, contentDescription = null, tint = contentColor)
                Spacer(Modifier.width(6.dp))
                Text("重扫", color = contentColor)
            }
            TvSettingsActionButton(
                onClick = { onIntent(ImportIntent.ToggleSourceEnabled(source.id, !source.enabled)) },
                enabled = !working,
                focusKey = "sources:${source.id}:toggle",
                focusChain = focusChain,
            ) { contentColor ->
                Text(if (source.enabled) "停用" else "启用", color = contentColor)
            }
            if (source.type != ImportSourceType.LOCAL_FOLDER) {
                TvSettingsActionButton(
                    onClick = { onIntent(ImportIntent.OpenRemoteSourceEditor(source.id)) },
                    enabled = !working,
                    focusKey = "sources:${source.id}:edit",
                    focusChain = focusChain,
                    restoreFocusAfterClick = false,
                ) { contentColor ->
                    Icon(Icons.Rounded.Edit, contentDescription = null, tint = contentColor)
                    Spacer(Modifier.width(6.dp))
                    Text("编辑", color = contentColor)
                }
            }
            TvSettingsActionButton(
                onClick = onDelete,
                enabled = !working,
                focusKey = "sources:${source.id}:delete",
                focusChain = focusChain,
                restoreFocusAfterClick = false,
            ) { contentColor ->
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = contentColor)
                Spacer(Modifier.width(6.dp))
                Text("删除", color = contentColor)
            }
        }
    }
}

@Composable
private fun TvRemoteSourceEditorDialog(
    editor: RemoteSourceEditorState,
    state: ImportState,
    onIntent: (ImportIntent) -> Unit,
) {
    Dialog(
        onDismissRequest = { onIntent(ImportIntent.DismissRemoteSourceEditor) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 720.dp, max = 880.dp)
                    .heightIn(max = 720.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = "编辑${sourceTypeTitle(editor.type)}来源",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TvSettingsTextField("名称", editor.label, { onIntent(ImportIntent.RemoteSourceLabelChanged(it)) })
                    when (editor.type) {
                        ImportSourceType.SAMBA -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TvSettingsTextField("服务器", editor.server, { onIntent(ImportIntent.RemoteSourceServerChanged(it)) }, modifier = Modifier.weight(1f))
                                TvSettingsTextField("端口", editor.port, { onIntent(ImportIntent.RemoteSourcePortChanged(it)) }, "可选", Modifier.width(140.dp))
                            }
                            TvSettingsTextField("路径", editor.path, { onIntent(ImportIntent.RemoteSourcePathChanged(it)) })
                        }

                        ImportSourceType.WEBDAV -> {
                            TvSettingsTextField("根地址", editor.rootUrl, { onIntent(ImportIntent.RemoteSourceRootUrlChanged(it)) })
                            TvSettingsSwitchRow(
                                title = "允许不安全 TLS",
                                checked = editor.allowInsecureTls,
                                onCheckedChange = { onIntent(ImportIntent.RemoteSourceAllowInsecureTlsChanged(it)) },
                            )
                        }

                        ImportSourceType.NAVIDROME -> {
                            TvSettingsTextField("服务器地址", editor.rootUrl, { onIntent(ImportIntent.RemoteSourceRootUrlChanged(it)) })
                        }

                        ImportSourceType.LOCAL_FOLDER -> Unit
                    }
                    TvSettingsTextField("用户名", editor.username, { onIntent(ImportIntent.RemoteSourceUsernameChanged(it)) })
                    TvSettingsTextField(
                        label = "密码",
                        value = editor.password,
                        onValueChange = { onIntent(ImportIntent.RemoteSourcePasswordChanged(it)) },
                        placeholder = if (editor.hasStoredCredential) "留空则沿用已保存密码" else "",
                        password = true,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        state.testMessage?.let { message ->
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    TvSettingsActionButton(
                        onClick = { onIntent(ImportIntent.TestRemoteSource) },
                        enabled = !state.isWorking,
                    ) { contentColor ->
                        Text("测试", color = contentColor)
                    }
                    TvSettingsActionButton(
                        onClick = { onIntent(ImportIntent.DismissRemoteSourceEditor) },
                    ) { contentColor ->
                        Text("取消", color = contentColor)
                    }
                    TvSettingsActionButton(
                        onClick = { onIntent(ImportIntent.SaveRemoteSource) },
                        enabled = !state.isWorking,
                        style = TvSettingsActionButtonStyle.Filled,
                    ) { contentColor ->
                        Text("保存并扫描", color = contentColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun TvStorageSettingsPane(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    initialFocusRequester: FocusRequester,
    leftFocusRequester: FocusRequester,
    focusCoordinator: TvSettingsFocusCoordinator,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        onIntent(SettingsIntent.LoadStorageUsage(force = false))
    }
    val categories = remember(state.storageSnapshot) {
        val supported = state.storageSnapshot?.categories.orEmpty().associateBy { it.category }
        storageCategoryOrder.mapNotNull { supported[it] }
    }
    val refreshEnabled = !state.storageLoading && state.clearingStorageCategory == null
    val storageFocusRows = remember(categories, state.message) {
        buildList {
            if (state.message != null) {
                add(listOf("storage:message:clear"))
            }
            add(listOf("storage:refresh"))
            categories.forEach { usage ->
                add(listOf("storage:${usage.category.name}:clear"))
            }
        }
    }
    val storageFallbackFocusKey = "storage:fallback"
    val focusChain = rememberTvSettingsFocusChain(
        focusRows = storageFocusRows.ifEmpty { listOf(listOf(storageFallbackFocusKey)) },
        initialFocusRequester = initialFocusRequester,
        leftFocusRequester = leftFocusRequester,
        listState = listState,
        focusCoordinator = focusCoordinator,
    )
    LazyColumn(
        state = listState,
        modifier = modifier.tvSettingsScrollableFocus(listState, leftFocusRequester),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TvSettingsPaneHeader(
                title = "空间管理",
                subtitle = "查看 TV 端可管理缓存，并按分类清理。",
            )
        }
        if (storageFocusRows.isEmpty()) {
            item {
                TvSettingsScrollAnchor(
                    modifier = Modifier.tvSettingsFocusTarget(storageFallbackFocusKey, focusChain),
                )
            }
        }
        state.message?.let { message ->
            item {
                TvSettingsMessageCard(
                    message = message,
                    onClear = { onIntent(SettingsIntent.ClearMessage) },
                    focusKey = "storage:message:clear",
                    focusChain = focusChain,
                )
            }
        }
        item {
            TvSettingsInfoCard(title = "当前可管理空间") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = state.storageSnapshot?.let { formatTvStorageSize(it.totalSizeBytes) }
                                ?: if (state.storageLoading) "正在统计空间..." else "暂未读取",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        val paths = state.storageSnapshot?.paths.orEmpty().filter { it.isNotBlank() }
                        if (paths.isNotEmpty()) {
                            Text(
                                text = paths.joinToString("\n"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    TvSettingsActionButton(
                        onClick = { onIntent(SettingsIntent.LoadStorageUsage(force = true)) },
                        enabled = refreshEnabled,
                        focusKey = "storage:refresh",
                        focusChain = focusChain,
                    ) { contentColor ->
                        Icon(Icons.Rounded.Sync, contentDescription = null, tint = contentColor)
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.storageLoading) "刷新中" else "刷新", color = contentColor)
                    }
                }
            }
        }
        if (categories.isEmpty()) {
            item {
                TvSettingsEmptyCard(
                    title = if (state.storageLoading) "正在统计空间" else "没有可管理空间",
                    body = if (state.storageLoading) "正在读取当前平台支持的存储目录。" else "当前平台还没有暴露可清理的空间分类。",
                )
            }
        } else {
            itemsIndexed(categories, key = { _, usage -> usage.category.name }) { index, usage ->
                TvStorageCategoryCard(
                    usage = usage,
                    clearing = state.clearingStorageCategory == usage.category,
                    actionEnabled = usage.sizeBytes > 0L &&
                        !state.storageLoading &&
                        state.clearingStorageCategory != usage.category,
                    focusKey = "storage:${usage.category.name}:clear",
                    focusChain = focusChain,
                    onClear = { onIntent(SettingsIntent.ClearStorageCategory(usage.category)) },
                )
            }
        }
    }
}

@Composable
private fun TvStorageCategoryCard(
    usage: AppStorageCategoryUsage,
    clearing: Boolean,
    actionEnabled: Boolean,
    focusKey: String,
    focusChain: TvSettingsFocusChain,
    onClear: () -> Unit,
) {
    TvSettingsInfoCard(title = tvStorageCategoryTitle(usage.category)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tvStorageCategoryDescription(usage.category), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = formatTvStorageSize(usage.sizeBytes),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            TvSettingsActionButton(
                onClick = onClear,
                enabled = actionEnabled,
                focusKey = focusKey,
                focusChain = focusChain,
            ) { contentColor ->
                Text(if (clearing) "清理中..." else "清除", color = contentColor)
            }
        }
    }
}

@Composable
private fun TvAboutDeviceSettingsPane(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    initialFocusRequester: FocusRequester,
    leftFocusRequester: FocusRequester,
    focusCoordinator: TvSettingsFocusCoordinator,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        onIntent(SettingsIntent.LoadDeviceInfo(force = false))
    }
    val focusChain = rememberTvSettingsFocusChain(
        focusRows = listOf(listOf("device:refresh")),
        initialFocusRequester = initialFocusRequester,
        leftFocusRequester = leftFocusRequester,
        listState = listState,
        focusCoordinator = focusCoordinator,
    )
    LazyColumn(
        state = listState,
        modifier = modifier.tvSettingsScrollableFocus(listState, leftFocusRequester),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TvSettingsPaneHeader(
                title = "关于本机",
                subtitle = "查看当前 TV 或车机的系统、屏幕和硬件信息。",
            )
        }
        item {
            TvSettingsInfoCard(title = state.deviceInfoSnapshot?.deviceModel?.takeIf { it.isNotBlank() } ?: "设备信息") {
                TvSettingsActionButton(
                    onClick = { onIntent(SettingsIntent.LoadDeviceInfo(force = true)) },
                    enabled = !state.deviceInfoLoading,
                    focusKey = "device:refresh",
                    focusChain = focusChain,
                ) { contentColor ->
                    Icon(Icons.Rounded.Sync, contentDescription = null, tint = contentColor)
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.deviceInfoLoading) "读取中" else "刷新", color = contentColor)
                }
            }
        }
        item {
            TvDeviceInfoGroup(
                title = "系统",
                rows = listOf(
                    "系统名称" to deviceInfoValue(state.deviceInfoSnapshot?.systemName, state.deviceInfoLoading),
                    "系统版本" to deviceInfoValue(state.deviceInfoSnapshot?.systemVersion, state.deviceInfoLoading),
                    "设备型号" to deviceInfoValue(state.deviceInfoSnapshot?.deviceModel, state.deviceInfoLoading),
                ),
            )
        }
        item {
            val snapshot = state.deviceInfoSnapshot
            TvDeviceInfoGroup(
                title = "显示",
                rows = listOf(
                    "分辨率" to deviceInfoValue(snapshot?.resolution, state.deviceInfoLoading),
                    "应用 DP 分辨率" to deviceDpResolution(snapshot, density.density, state.deviceInfoLoading),
                    "系统像素密度" to deviceDensity(snapshot?.systemDensityScale, state.deviceInfoLoading),
                    "字体缩放" to "%.2f".format(density.fontScale),
                ),
            )
        }
        item {
            TvDeviceInfoGroup(
                title = "硬件",
                rows = listOf(
                    "CPU" to deviceInfoValue(state.deviceInfoSnapshot?.cpuDescription, state.deviceInfoLoading),
                    "内存" to (
                        state.deviceInfoSnapshot?.totalMemoryBytes?.let(::formatTvStorageSize)
                            ?: if (state.deviceInfoLoading) "读取中..." else "不可用"
                        ),
                ),
            )
        }
    }
}

@Composable
private fun TvAboutAppSettingsPane(
    platformName: String,
    initialFocusRequester: FocusRequester,
    leftFocusRequester: FocusRequester,
    focusCoordinator: TvSettingsFocusCoordinator,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val focusChain = rememberTvSettingsFocusChain(
        focusRows = listOf(listOf("about-app:scroll")),
        initialFocusRequester = initialFocusRequester,
        leftFocusRequester = leftFocusRequester,
        listState = listState,
        focusCoordinator = focusCoordinator,
    )
    LazyColumn(
        state = listState,
        modifier = modifier.tvSettingsScrollableFocus(listState, leftFocusRequester),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TvSettingsPaneHeader(
                title = "关于应用",
                subtitle = "查看版本、开发者、项目地址和公众号信息。",
            )
        }
        item {
            TvSettingsScrollAnchor(
                modifier = Modifier.tvSettingsFocusTarget("about-app:scroll", focusChain),
            )
        }
        item {
            TvDeviceInfoGroup(
                title = "基本信息",
                rows = listOf(
                    "应用名称" to "LynMusic",
                    "版本号" to BuildMetadata.versionDisplay,
                    "平台名称" to platformName,
                    "编译时间" to BuildMetadata.buildTimeUtc,
                ),
            )
        }
        item {
            TvDeviceInfoGroup(
                title = "开发者",
                rows = listOf(
                    "名称" to "Wesley",
                    "项目地址" to "https://github.com/wesley666/LynMusic",
                ),
            )
        }
        item {
            TvSettingsInfoCard(title = "微信公众号") {
                TvSettingsFieldRow(label = "账号", value = "锋风")
                Text(
                    text = "公众号二维码",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TvAboutAppQrImage(
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .fillMaxWidth(0.38f)
                            .aspectRatio(1f),
                    )
                }
                Text(
                    text = "扫码关注公众号，获取更新和交流信息。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun TvDeviceInfoGroup(
    title: String,
    rows: List<Pair<String, String>>,
) {
    TvSettingsInfoCard(title = title) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.filter { it.second.isNotBlank() }.forEach { (label, value) ->
                TvSettingsFieldRow(label = label, value = value)
            }
        }
    }
}

@Composable
private fun TvSettingsPaneHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun TvSettingsInfoCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TvSettingsPanelShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun TvAboutAppQrImage(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.about_app_wechat_qr),
            contentDescription = "公众号二维码",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun TvSettingsScrollAnchor(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .focusable(),
    )
}

@Composable
private fun Modifier.tvSettingsScrollableFocus(
    listState: LazyListState,
    leftFocusRequester: FocusRequester,
): Modifier {
    val coroutineScope = rememberCoroutineScope()
    return this
        .focusGroup()
        .focusProperties {
            left = leftFocusRequester
        }
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems <= 0) return@onKeyEvent false
            val targetIndex = when (event.key) {
                Key.DirectionDown -> {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                        ?: listState.firstVisibleItemIndex
                    (lastVisible + 1).coerceAtMost(totalItems - 1)
                }

                Key.DirectionUp -> (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                else -> return@onKeyEvent false
            }
            if (targetIndex == listState.firstVisibleItemIndex) return@onKeyEvent false
            coroutineScope.launch {
                listState.animateScrollToItem(targetIndex)
            }
            true
        }
}

@Composable
private fun rememberTvSettingsFocusChain(
    focusRows: List<List<String>>,
    initialFocusRequester: FocusRequester,
    leftFocusRequester: FocusRequester,
    listState: LazyListState,
    focusCoordinator: TvSettingsFocusCoordinator,
): TvSettingsFocusChain {
    val coroutineScope = rememberCoroutineScope()
    val focusKeys = remember(focusRows) { focusRows.flatten() }
    val initialKey = focusKeys.firstOrNull()
    val requesters = remember(focusKeys, initialKey, initialFocusRequester) {
        focusKeys.associateWith { key ->
            if (key == initialKey) initialFocusRequester else FocusRequester()
        }
    }
    val chain = remember(focusRows, focusKeys, requesters, listState, coroutineScope, leftFocusRequester, focusCoordinator) {
        TvSettingsFocusChain(
            focusRows = focusRows,
            requesters = requesters,
            listState = listState,
            coroutineScope = coroutineScope,
            leftFocusRequester = leftFocusRequester,
            focusCoordinator = focusCoordinator,
        )
    }
    SideEffect {
        focusCoordinator.activeContentFocusChain = chain
    }
    LaunchedEffect(chain, focusRows) {
        if (focusCoordinator.restoreContentFocusIfRequested()) {
            return@LaunchedEffect
        }
    }
    return chain
}

private class TvSettingsFocusCoordinator {
    var activeContentFocusChain: TvSettingsFocusChain? = null
    var lastContentFocusKey: String? = null
        private set
    private var contentRestoreRequested = false

    fun markContentFocused(key: String) {
        lastContentFocusKey = key
        clearContentFocusRestore()
    }

    fun requestContentFocusRestore() {
        contentRestoreRequested = true
    }

    fun clearContentFocusRestore() {
        contentRestoreRequested = false
    }

    fun restoreContentFocusIfRequested(): Boolean {
        if (!contentRestoreRequested) return false
        val restored = activeContentFocusChain?.restoreFocus() == true
        if (restored) {
            contentRestoreRequested = false
        }
        return restored
    }
}

private class TvSettingsFocusChain(
    private val focusRows: List<List<String>>,
    private val requesters: Map<String, FocusRequester>,
    private val listState: LazyListState,
    private val coroutineScope: CoroutineScope,
    val leftFocusRequester: FocusRequester,
    private val focusCoordinator: TvSettingsFocusCoordinator,
) {
    private val attachedKeys = mutableSetOf<String>()
    private var lastFocusedKey: String? = null

    fun requesterFor(key: String): FocusRequester {
        return requesters.getValue(key)
    }

    fun contains(key: String): Boolean {
        return requesters.containsKey(key)
    }

    fun attach(key: String) {
        attachedKeys += key
    }

    fun detach(key: String) {
        attachedKeys -= key
    }

    fun markFocused(key: String) {
        lastFocusedKey = key
        focusCoordinator.markContentFocused(key)
    }

    fun requestRestoreAfterAction() {
        focusCoordinator.requestContentFocusRestore()
        coroutineScope.launch {
            withFrameNanos { }
            focusCoordinator.restoreContentFocusIfRequested()
        }
    }

    fun restoreFocus(): Boolean {
        val focusKeys = focusRows.flatten()
        val candidates = buildList {
            val preferredKey = focusCoordinator.lastContentFocusKey ?: lastFocusedKey
            preferredKey?.takeIf { it in requesters }?.let(::add)
            val rowIndex = focusRows.indexOfFirst { row -> preferredKey in row }
            if (rowIndex >= 0) {
                focusRows[rowIndex].forEach(::add)
                focusRows.drop(rowIndex + 1).forEach { row -> row.firstOrNull()?.let(::add) }
                focusRows.take(rowIndex).asReversed().forEach { row -> row.firstOrNull()?.let(::add) }
            }
            focusKeys.forEach(::add)
        }.distinct()
        return candidates.any(::requestFocusSafely)
    }

    fun moveFrom(
        key: String,
        direction: Int,
        allowScrollFallback: Boolean = true,
    ): Boolean {
        val rowIndex = focusRows.indexOfFirst { row -> key in row }
        if (rowIndex < 0) return true
        val columnIndex = focusRows[rowIndex].indexOf(key).coerceAtLeast(0)
        val nextRows = if (direction > 0) {
            focusRows.drop(rowIndex + 1)
        } else {
            focusRows.take(rowIndex).asReversed()
        }
        val nextKey = nextRows.firstNotNullOfOrNull { row ->
            row.getOrNull(columnIndex)?.takeIf(attachedKeys::contains)
                ?: row.firstOrNull(attachedKeys::contains)
        }
        if (nextKey != null) {
            requestFocusSafely(nextKey)
            return true
        }
        if (!allowScrollFallback) {
            return false
        }
        scrollList(direction)
        return true
    }

    fun moveHorizontal(key: String, direction: Int): Boolean {
        val row = focusRows.firstOrNull { key in it } ?: return if (direction < 0) moveLeft() else true
        val columnIndex = row.indexOf(key)
        val candidates = if (direction > 0) {
            row.drop(columnIndex + 1)
        } else {
            row.take(columnIndex).asReversed()
        }
        val nextKey = candidates.firstOrNull(attachedKeys::contains)
        if (nextKey != null) {
            requestFocusSafely(nextKey)
            return true
        }
        return if (direction < 0) moveLeft() else true
    }

    fun moveLeft(): Boolean {
        leftFocusRequester.requestFocus()
        return true
    }

    private fun requestFocusSafely(key: String): Boolean {
        return try {
            requesters[key]?.requestFocus() ?: return false
            true
        } catch (_: IllegalStateException) {
            false
        }
    }

    private fun scrollList(direction: Int) {
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems <= 0) return
        val targetIndex = if (direction > 0) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: listState.firstVisibleItemIndex
            (lastVisible + 1).coerceAtMost(totalItems - 1)
        } else {
            (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
        }
        if (targetIndex == listState.firstVisibleItemIndex && listState.firstVisibleItemScrollOffset == 0) return
        coroutineScope.launch {
            listState.animateScrollToItem(targetIndex)
        }
    }
}

@Composable
private fun Modifier.tvSettingsFocusTarget(
    key: String,
    focusChain: TvSettingsFocusChain,
): Modifier {
    DisposableEffect(focusChain, key) {
        focusChain.attach(key)
        onDispose { focusChain.detach(key) }
    }
    return this
        .focusRequester(focusChain.requesterFor(key))
        .focusProperties {
            left = focusChain.leftFocusRequester
        }
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                focusChain.markFocused(key)
            }
        }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionUp -> focusChain.moveFrom(key, direction = -1)
                Key.DirectionDown -> focusChain.moveFrom(key, direction = 1)
                Key.DirectionLeft -> focusChain.moveHorizontal(key, direction = -1)
                Key.DirectionRight -> focusChain.moveHorizontal(key, direction = 1)
                else -> false
            }
        }
}

@Composable
private fun TvSettingsFieldRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(180.dp))
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TvSettingsMessageCard(
    message: String,
    onClear: () -> Unit,
    focusKey: String? = null,
    focusChain: TvSettingsFocusChain? = null,
) {
    TvSettingsInfoCard(title = "提示") {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TvSettingsActionButton(
            onClick = onClear,
            focusKey = focusKey,
            focusChain = focusChain,
        ) { contentColor ->
            Text("知道了", color = contentColor)
        }
    }
}

@Composable
private fun TvSettingsEmptyCard(
    title: String,
    body: String,
) {
    TvSettingsInfoCard(title = title) {
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TvSettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier.fillMaxWidth(),
    password: Boolean = false,
    focusKey: String? = null,
    focusChain: TvSettingsFocusChain? = null,
) {
    val targetKey = focusKey
    val targetChain = focusChain
    val fieldModifier = if (targetKey != null && targetChain?.contains(targetKey) == true) {
        modifier.tvSettingsTextFieldFocusTarget(targetKey, targetChain)
    } else {
        modifier
    }.height(72.dp)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder) }
        } else {
            null
        },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = fieldModifier,
    )
}

@Composable
private fun Modifier.tvSettingsTextFieldFocusTarget(
    key: String,
    focusChain: TvSettingsFocusChain,
): Modifier {
    DisposableEffect(focusChain, key) {
        focusChain.attach(key)
        onDispose { focusChain.detach(key) }
    }
    return this
        .focusRequester(focusChain.requesterFor(key))
        .focusProperties {
            left = focusChain.leftFocusRequester
        }
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                focusChain.markFocused(key)
            }
        }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionUp -> focusChain.moveFrom(key, direction = -1, allowScrollFallback = false)
                Key.DirectionDown -> focusChain.moveFrom(key, direction = 1, allowScrollFallback = false)
                Key.DirectionLeft -> focusChain.moveHorizontal(key, direction = -1)
                Key.DirectionRight -> focusChain.moveHorizontal(key, direction = 1)
                else -> false
            }
        }
}

@Composable
private fun TvSettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    focusKey: String? = null,
    focusChain: TvSettingsFocusChain? = null,
) {
    val targetKey = focusKey
    val targetChain = focusChain
    val switchModifier = if (targetKey != null && targetChain?.contains(targetKey) == true) {
        Modifier.tvSettingsFocusTarget(targetKey, targetChain)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = switchModifier,
        )
    }
}

@Composable
private fun TvSettingsIconBox(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun TvSettingsUnavailableScreen(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("设置页不可用", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text("请返回主界面重新打开。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onBack) {
                Text("返回")
            }
        }
    }
}

@Composable
private fun ProvideTvSettingsDensity(
    appDisplayScalePreset: AppDisplayScalePreset,
    content: @Composable () -> Unit,
) {
    val currentDensity = LocalDensity.current
    val fixedDensity = remember(currentDensity.fontScale, appDisplayScalePreset) {
        Density(
            density = effectiveAppDisplayDensity(tvSettingsStableDensityScale(), appDisplayScalePreset),
            fontScale = currentDensity.fontScale,
        )
    }
    CompositionLocalProvider(LocalDensity provides fixedDensity) {
        content()
    }
}

private fun tvSettingsStableDensityScale(): Float {
    val stableDpi = DisplayMetrics.DENSITY_DEVICE_STABLE.takeIf { it > 0 } ?: DisplayMetrics.DENSITY_DEFAULT
    return stableDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()
}

private enum class TvSettingsSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    Sources("来源", "导入和扫描音乐", Icons.Rounded.Folder),
    Storage("空间管理", "缓存和本机文件", Icons.Rounded.Storage),
    AboutDevice("关于本机", "系统和硬件信息", Icons.Rounded.Info),
    AboutApp("关于应用", "版本和项目地址", Icons.Rounded.Settings),
}

private fun ImportScanOperation.sourceIdOrNull(): String? {
    return when (this) {
        is ImportScanOperation.RescanSource -> sourceId
        is ImportScanOperation.UpdateRemote -> sourceId
        ImportScanOperation.CreateLocalFolder,
        is ImportScanOperation.CreateRemote -> null
    }
}

private fun sourceTypeTitle(type: ImportSourceType): String {
    return when (type) {
        ImportSourceType.LOCAL_FOLDER -> "本地文件夹"
        ImportSourceType.SAMBA -> "Samba"
        ImportSourceType.WEBDAV -> "WebDAV"
        ImportSourceType.NAVIDROME -> "Navidrome"
    }
}

private fun sourceTypeIcon(type: ImportSourceType): ImageVector {
    return when (type) {
        ImportSourceType.LOCAL_FOLDER -> Icons.Rounded.Folder
        ImportSourceType.SAMBA,
        ImportSourceType.WEBDAV,
        ImportSourceType.NAVIDROME -> Icons.Rounded.Cloud
    }
}

private fun sourceDisplayReference(source: ImportSource): String {
    return when (source.type) {
        ImportSourceType.LOCAL_FOLDER -> source.rootReference
        ImportSourceType.SAMBA -> formatSambaEndpoint(source.server, source.port, source.path)
        ImportSourceType.WEBDAV -> displayWebDavRootUrl(source.rootReference)
        ImportSourceType.NAVIDROME -> source.rootReference
    }
}

private fun sourceStatusText(
    sourceWithStatus: SourceWithStatus,
    latestSummary: top.iwesley.lyn.music.core.model.ImportScanSummary?,
): String {
    latestSummary?.let { return formatImportScanSummary(it) }
    val status = sourceWithStatus.indexState
    return when {
        status?.lastError?.isNotBlank() == true -> "上次扫描失败：${status.lastError}"
        status != null -> "曲目 ${status.trackCount} 首 · ${formatTimestamp(status.lastScannedAt)}"
        else -> "尚未扫描"
    }
}

private fun formatTimestamp(value: Long?): String {
    if (value == null || value <= 0L) return "未扫描"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
}

private val storageCategoryOrder = listOf(
    AppStorageCategory.Artwork,
    AppStorageCategory.PlaybackCache,
    AppStorageCategory.OfflineDownloads,
    AppStorageCategory.LyricsShareTemp,
    AppStorageCategory.TagEditTemp,
)

private fun tvStorageCategoryTitle(category: AppStorageCategory): String {
    return when (category) {
        AppStorageCategory.Artwork -> "封面缓存"
        AppStorageCategory.PlaybackCache -> "播放缓存"
        AppStorageCategory.OfflineDownloads -> "离线音乐"
        AppStorageCategory.LyricsShareTemp -> "歌词分享临时文件"
        AppStorageCategory.TagEditTemp -> "标签编辑临时文件"
    }
}

private fun tvStorageCategoryDescription(category: AppStorageCategory): String {
    return when (category) {
        AppStorageCategory.Artwork -> "下载封面、扫描封面和标签编辑生成的本地封面文件。"
        AppStorageCategory.PlaybackCache -> "SMB 播放时落到本地的临时音频缓存。"
        AppStorageCategory.OfflineDownloads -> "手动下载到本机的非本地音乐文件。"
        AppStorageCategory.LyricsShareTemp -> "生成歌词分享图时写入的临时图片。"
        AppStorageCategory.TagEditTemp -> "编辑标签封面时写入的临时中转文件。"
    }
}

private fun formatTvStorageSize(sizeBytes: Long): String {
    if (sizeBytes < 1024L) return "$sizeBytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = sizeBytes.toDouble() / 1024.0
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index += 1
    }
    return if (value >= 10.0) {
        "%.0f %s".format(value, units[index])
    } else {
        "%.1f %s".format(value, units[index])
    }
}

private fun deviceInfoValue(value: String?, loading: Boolean): String {
    return value?.takeIf { it.isNotBlank() } ?: if (loading) "读取中..." else "不可用"
}

private fun deviceDpResolution(
    snapshot: DeviceInfoSnapshot?,
    density: Float,
    loading: Boolean,
): String {
    val width = snapshot?.resolutionWidthPx
    val height = snapshot?.resolutionHeightPx
    if (width == null || height == null || density <= 0f) return if (loading) "读取中..." else "不可用"
    return "${(width / density).toInt()} × ${(height / density).toInt()} dp"
}

private fun deviceDensity(value: Float?, loading: Boolean): String {
    return value?.takeIf { it > 0f }?.let { "%.2f".format(it) } ?: if (loading) "读取中..." else "不可用"
}
