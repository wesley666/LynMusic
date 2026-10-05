package top.iwesley.lyn.music.tv

import top.iwesley.lyn.music.core.model.uiPlural
import top.iwesley.lyn.music.feature.importing.RemoteFolderRow
import top.iwesley.lyn.music.feature.importing.RemoteFolderTreeState
import top.iwesley.lyn.music.feature.importing.folderSourceEndpointText
import top.iwesley.lyn.music.core.model.ProvideUiLanguage

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.lastErrorUiText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.displayText

import top.iwesley.lyn.music.uiDisplayText
import top.iwesley.lyn.music.uiString

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.KeyboardArrowDown
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.VisualTransformation
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
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.BadgedIcon
import top.iwesley.lyn.music.LynMusicAppComponent
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.AppStorageCategory
import top.iwesley.lyn.music.core.model.AppStorageCategoryUsage
import top.iwesley.lyn.music.core.model.BuildMetadata
import top.iwesley.lyn.music.core.model.DeviceInfoSnapshot
import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.FnMusicConnectionMode
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.domain.fnMusicIdOf
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.LynMusicUpdateLinks
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.SubsonicAuthMode
import top.iwesley.lyn.music.core.model.displayWebDavRootUrl
import top.iwesley.lyn.music.core.model.effectiveAppDisplayDensity
import top.iwesley.lyn.music.core.model.formatSambaEndpoint
import top.iwesley.lyn.music.feature.importing.ImportIntent
import top.iwesley.lyn.music.feature.importing.ImportScanOperation
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.importing.RemoteSourceEditorState
import top.iwesley.lyn.music.feature.importing.formatImportScanSummary
import top.iwesley.lyn.music.feature.settings.AppUpdateUiStatus
import top.iwesley.lyn.music.feature.settings.SettingsIntent
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.feature.settings.toAppUpdateUiModel
import top.iwesley.lyn.music.tv.ui.TvMainTheme

private val TvSettingsPanelShape = RoundedCornerShape(18.dp)
private val TvSettingsItemShape = RoundedCornerShape(14.dp)

class TvSettingsActivity : TvComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        var appComponentResult by mutableStateOf(tvAppComponentResult())
        setContent {
            ProvideUiLanguage {
                val component = appComponentResult.getOrNull()
                if (component == null) {
                    TvMainTheme {
                        TvSettingsUnavailableScreen(
                            onRetry = {
                                appComponentResult = tvAppComponentResult()
                            },
                            onBack = ::finish,
                        )
                    }
                    return@ProvideUiLanguage
                }
                val appDisplayScalePreset by component.appDisplayScalePreset.collectAsState()
                ProvideTvSettingsDensity(appDisplayScalePreset) {
                    TvSettingsApp(
                        component = component,
                        pickLocalFolder = { (application as LynMusicApplication).pickLocalFolder() },
                        onBack = ::finish,
                    )
                }


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

    LaunchedEffect(component) {
        component.settingsStore.dispatch(SettingsIntent.CheckAppUpdateSilently)
    }

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
    val generalFocusRequester = remember { FocusRequester() }
    val sourcesFocusRequester = remember { FocusRequester() }
    val storageFocusRequester = remember { FocusRequester() }
    val aboutDeviceFocusRequester = remember { FocusRequester() }
    val aboutAppFocusRequester = remember { FocusRequester() }
    val contentInitialFocusRequester = remember { FocusRequester() }
    val focusCoordinator = remember { TvSettingsFocusCoordinator() }
    val selectedSectionFocusRequester = when (selectedSection) {
        TvSettingsSection.General -> generalFocusRequester
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
            showAppUpdateBadge = settingsState.appUpdateHasNewVersion == true,
            generalFocusRequester = generalFocusRequester,
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
                TvSettingsSection.General -> TvLanguageSettingsPane(
                    state = settingsState,
                    onIntent = onSettingsIntent,
                    initialFocusRequester = contentInitialFocusRequester,
                    leftFocusRequester = generalFocusRequester,
                    focusCoordinator = focusCoordinator,
                )
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
                    state = settingsState,
                    onIntent = onSettingsIntent,
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
    showAppUpdateBadge: Boolean,
    generalFocusRequester: FocusRequester,
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
            text = uiString(Res.string.settings_title),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        TvSettingsSection.entries.forEach { section ->
            TvSettingsSectionCard(
                section = section,
                selected = selectedSection == section,
                showUpdateBadge = showAppUpdateBadge && section == TvSettingsSection.AboutApp,
                focusRequester = when (section) {
                    TvSettingsSection.General -> generalFocusRequester
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
    showUpdateBadge: Boolean,
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
            BadgedIcon(
                imageVector = section.icon,
                contentDescription = null,
                showBadge = showUpdateBadge,
                modifier = Modifier.size(24.dp),
                tint = if (focused) focusedContentColor else if (selected) Color.White else primaryContentColor,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    section.title.displayText(),
                    color = if (focused) focusedContentColor else if (selected) Color.White else primaryContentColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    section.subtitle.displayText(),
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
private fun TvLanguageSettingsPane(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    initialFocusRequester: FocusRequester,
    leftFocusRequester: FocusRequester,
    focusCoordinator: TvSettingsFocusCoordinator,
) {
    val listState = rememberLazyListState()
    val languages = top.iwesley.lyn.music.core.model.AppLanguage.entries
    val keys = remember { languages.map { "language:${it.storageValue}" } }
    val chain = rememberTvSettingsFocusChain(
        focusRows = keys.map { listOf(it) },
        initialFocusRequester = initialFocusRequester,
        leftFocusRequester = leftFocusRequester,
        listState = listState,
        focusCoordinator = focusCoordinator,
    )
    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TvSettingsPaneHeader(uiString(Res.string.language_title), uiString(Res.string.language_description)) }
        items(languages.size) { index ->
            val language = languages[index]
            TvSettingsActionButton(
                onClick = { onIntent(SettingsIntent.AppLanguageChanged(language)) },
                style = if (state.appLanguage == language) TvSettingsActionButtonStyle.Selected else TvSettingsActionButtonStyle.Outlined,
                focusKey = keys[index],
                focusChain = chain,
            ) { color -> Text(top.iwesley.lyn.music.appLanguageLabel(language), color = color) }
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
            addSourceFocusRows(state.capabilities).forEach(::add)
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
            title = { Text(uiString(Res.string.source_delete_action)) },
            text = { Text(uiString(Res.string.tv_source_delete_confirmation, sourceWithStatus.source.label.ifBlank { sourceTypeTitle(sourceWithStatus.source.type) })) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onIntent(ImportIntent.DeleteSource(sourceWithStatus.source.id))
                    },
                ) {
                    Text(uiString(Res.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(uiString(Res.string.common_cancel))
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
                title = uiString(Res.string.sources_title),
                subtitle = uiString(Res.string.tv_source_management_description),
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
                    message = message.uiDisplayText(),
                    onClear = { onIntent(ImportIntent.ClearMessage) },
                    focusKey = "sources:message:clear",
                    focusChain = focusChain,
                )
            }
        }
        if (showPageTestMessage) {
            item {
                TvSettingsMessageCard(
                    message = state.testMessage.uiDisplayText(),
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
                    title = uiString(Res.string.tv_sources_empty_title),
                    body = uiString(Res.string.tv_source_add_hint),
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
        Text(uiString(Res.string.tv_source_add_action), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        addSourceFocusRows(capabilities).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { focusKey ->
                    when (focusKey) {
                        "sources:add:local" -> TvSettingsActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    pickLocalFolder()?.let { selection ->
                                        onIntent(ImportIntent.ImportSelectedLocalFolder(selection))
                                    }
                                }
                            },
                            enabled = !state.isWorking,
                            style = TvSettingsActionButtonStyle.Filled,
                            focusKey = focusKey,
                            focusChain = focusChain,
                            restoreFocusAfterClick = false,
                        ) { contentColor ->
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = contentColor)
                            Spacer(Modifier.width(8.dp))
                            Text(uiString(Res.string.source_local_folder_label), color = contentColor)
                        }

                        "sources:add:samba" -> TvAddTypeButton(
                            label = "Samba",
                            selected = state.creatingSourceType == ImportSourceType.SAMBA,
                            focusKey = focusKey,
                            focusChain = focusChain,
                            restoreFocusAfterClick = false,
                        ) {
                            onIntent(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.SAMBA))
                        }

                        "sources:add:webdav" -> TvAddTypeButton(
                            label = "WebDAV",
                            selected = state.creatingSourceType == ImportSourceType.WEBDAV,
                            focusKey = focusKey,
                            focusChain = focusChain,
                            restoreFocusAfterClick = false,
                        ) {
                            onIntent(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.WEBDAV))
                        }

                        "sources:add:navidrome" -> TvAddTypeButton(
                            label = "Navidrome",
                            selected = state.creatingSourceType == ImportSourceType.NAVIDROME,
                            focusKey = focusKey,
                            focusChain = focusChain,
                            restoreFocusAfterClick = false,
                        ) {
                            onIntent(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.NAVIDROME))
                        }

                        "sources:add:subsonic" -> TvAddTypeButton(
                            label = "Subsonic",
                            selected = state.creatingSourceType == ImportSourceType.SUBSONIC,
                            focusKey = focusKey,
                            focusChain = focusChain,
                            restoreFocusAfterClick = false,
                        ) {
                            onIntent(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.SUBSONIC))
                        }

                        "sources:add:emby" -> TvAddTypeButton(
                            label = "Emby",
                            selected = state.creatingSourceType == ImportSourceType.EMBY,
                            focusKey = focusKey,
                            focusChain = focusChain,
                            restoreFocusAfterClick = false,
                        ) {
                            onIntent(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.EMBY))
                        }

                        "sources:add:fnmusic" -> TvAddTypeButton(
                            label = uiString(Res.string.fn_music_name),
                            selected = state.creatingSourceType == ImportSourceType.FN_MUSIC,
                            focusKey = focusKey,
                            focusChain = focusChain,
                            restoreFocusAfterClick = false,
                        ) {
                            onIntent(ImportIntent.OpenRemoteSourceCreator(ImportSourceType.FN_MUSIC))
                        }
                    }
                }
            }
        }
    }
}

private fun addSourceFocusRows(capabilities: PlatformCapabilities): List<List<String>> {
    val keys = buildList {
        if (capabilities.supportsLocalFolderImport) {
            add("sources:add:local")
        }
        if (capabilities.supportsSambaImport) {
            add("sources:add:samba")
        }
        if (capabilities.supportsWebDavImport) {
            add("sources:add:webdav")
        }
        if (capabilities.supportsNavidromeImport) {
            add("sources:add:navidrome")
        }
        if (capabilities.supportsSubsonicImport) {
            add("sources:add:subsonic")
        }
        if (capabilities.supportsEmbyImport) {
            add("sources:add:emby")
        }
        if (capabilities.supportsFnMusicImport) {
            add("sources:add:fnmusic")
        }
    }
    return keys.chunked(3)
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

    // One Button for every style: switching between Button and OutlinedButton would replace the focused node when the
    // style changes on click (e.g. a source type turning "selected"), dropping focus to the first settings section.
    val outlined = style == TvSettingsActionButtonStyle.Outlined
    TvButton(
        onClick = click,
        enabled = buttonEnabled,
        modifier = buttonModifier,
        scale = if (outlined) TvOutlinedButtonDefaults.scale() else TvButtonDefaults.scale(),
        glow = if (outlined) TvOutlinedButtonDefaults.glow() else TvButtonDefaults.glow(),
        shape = if (outlined) TvOutlinedButtonDefaults.shape() else TvButtonDefaults.shape(),
        colors = if (outlined) {
            TvOutlinedButtonDefaults.colors(
                focusedContainerColor = Color.White,
                focusedContentColor = focusedContentColor,
                pressedContainerColor = Color.White,
                pressedContentColor = focusedContentColor,
            )
        } else {
            TvButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = normalContentColor,
                focusedContainerColor = Color.White,
                focusedContentColor = focusedContentColor,
                pressedContainerColor = Color.White,
                pressedContentColor = focusedContentColor,
            )
        },
        border = if (outlined) TvOutlinedButtonDefaults.border() else TvButtonDefaults.border(),
        contentPadding = if (outlined) TvOutlinedButtonDefaults.ContentPadding else TvButtonDefaults.ContentPadding,
    ) {
        content(contentColor)
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
    val focusPrefix = remember(type) { "create:${type.name}" }
    val folderRootName = when (type) {
        ImportSourceType.SAMBA -> formatSambaEndpoint(state.sambaServer, state.sambaPort.toIntOrNull(), null)
        else -> state.webDavRootUrl.trim()
    }
    // Flatten the tree once: the focus rows and the rendered rows must match anyway.
    val folderRows = state.remoteFolderTree?.visibleRows(folderRootName).orEmpty()
    val treeFocusRows = tvFolderTreeFocusRows(focusPrefix, state.remoteFolderTree, folderRows, state.capabilities.supportsSambaImport)
    val focusChain = rememberTvDialogFocusChain(
        focusRows = remember(focusPrefix, type, treeFocusRows, state.fnMusicConnectionMode) {
            remoteSourceDialogFocusRows(
                prefix = focusPrefix,
                type = type,
                folderTreeRows = treeFocusRows,
                fnConnect = state.fnMusicConnectionMode == FnMusicConnectionMode.FN_CONNECT,
            )
        },
    )
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
                    text = uiString(Res.string.tv_source_add_named_type, sourceTypeTitle(type)),
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
                        ImportSourceType.SAMBA -> TvSambaSourceForm(
                            state = state,
                            onIntent = onIntent,
                            focusPrefix = focusPrefix,
                            focusChain = focusChain,
                        )

                        ImportSourceType.WEBDAV -> TvWebDavSourceForm(
                            state = state,
                            onIntent = onIntent,
                            focusPrefix = focusPrefix,
                            focusChain = focusChain,
                        )

                        ImportSourceType.NAVIDROME -> TvNavidromeSourceForm(
                            state = state,
                            onIntent = onIntent,
                            focusPrefix = focusPrefix,
                            focusChain = focusChain,
                        )

                        ImportSourceType.SUBSONIC -> TvSubsonicSourceForm(
                            state = state,
                            onIntent = onIntent,
                            focusPrefix = focusPrefix,
                            focusChain = focusChain,
                        )

                        ImportSourceType.EMBY -> TvEmbySourceForm(
                            state = state,
                            onIntent = onIntent,
                            focusPrefix = focusPrefix,
                            focusChain = focusChain,
                        )

                        ImportSourceType.FN_MUSIC -> TvFnMusicSourceForm(
                            state = state,
                            onIntent = onIntent,
                            focusPrefix = focusPrefix,
                            focusChain = focusChain,
                        )

                        ImportSourceType.LOCAL_FOLDER -> Unit
                    }
                    state.remoteFolderTree?.let { tree ->
                        TvRemoteFolderTree(
                            tree = tree,
                            rows = folderRows,
                            enabled = !state.isWorking,
                            allowManualShare = state.capabilities.supportsSambaImport,
                            onIntent = onIntent,
                            focusPrefix = focusPrefix,
                            focusChain = focusChain,
                        )
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
                                text = message.uiDisplayText(),
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
                                ImportSourceType.SUBSONIC -> onIntent(ImportIntent.TestSubsonicSource)
                                ImportSourceType.EMBY -> onIntent(ImportIntent.TestEmbySource)
                                ImportSourceType.FN_MUSIC -> onIntent(ImportIntent.TestFnMusicSource)
                                ImportSourceType.LOCAL_FOLDER -> Unit
                            }
                        },
                        enabled = !state.isWorking,
                        focusKey = "$focusPrefix:test",
                        focusChain = focusChain,
                    ) { contentColor ->
                        Text(uiString(Res.string.tv_source_test_connection), color = contentColor)
                    }
                    TvSettingsActionButton(
                        onClick = { onIntent(ImportIntent.DismissRemoteSourceCreator) },
                        focusKey = "$focusPrefix:cancel",
                        focusChain = focusChain,
                    ) { contentColor ->
                        Text(uiString(Res.string.common_cancel), color = contentColor)
                    }
                    TvSettingsActionButton(
                        onClick = {
                            when (type) {
                                ImportSourceType.SAMBA -> onIntent(ImportIntent.AddSambaSource)
                                ImportSourceType.WEBDAV -> onIntent(ImportIntent.AddWebDavSource)
                                ImportSourceType.NAVIDROME -> onIntent(ImportIntent.AddNavidromeSource)
                                ImportSourceType.SUBSONIC -> onIntent(ImportIntent.AddSubsonicSource)
                                ImportSourceType.EMBY -> onIntent(ImportIntent.AddEmbySource)
                                ImportSourceType.FN_MUSIC -> onIntent(ImportIntent.AddFnMusicSource)
                                ImportSourceType.LOCAL_FOLDER -> Unit
                            }
                        },
                        enabled = !state.isWorking && state.remoteFolderTree?.selected?.isNotEmpty() != false,
                        style = TvSettingsActionButtonStyle.Filled,
                        focusKey = "$focusPrefix:submit",
                        focusChain = focusChain,
                    ) { contentColor ->
                        Text(uiString(Res.string.tv_source_add_and_scan), color = contentColor)
                    }
                }
            }
        }
    }
}

private fun remoteSourceDialogFocusRows(
    prefix: String,
    type: ImportSourceType,
    folderTreeRows: List<List<String>> = emptyList(),
    fnConnect: Boolean = false,
): List<List<String>> {
    val buttons = listOf("$prefix:test", "$prefix:cancel", "$prefix:submit")
    return when (type) {
        ImportSourceType.SAMBA -> listOf(
            listOf("$prefix:label"),
            listOf("$prefix:server", "$prefix:port"),
            listOf("$prefix:username", "$prefix:password"),
        ) + folderTreeRows + listOf(buttons)

        ImportSourceType.WEBDAV -> listOf(
            listOf("$prefix:label"),
            listOf("$prefix:root"),
            listOf("$prefix:username", "$prefix:password"),
            listOf("$prefix:tls"),
        ) + folderTreeRows + listOf(buttons)

        ImportSourceType.NAVIDROME -> listOf(
            listOf("$prefix:label"),
            listOf("$prefix:root"),
            listOf("$prefix:username", "$prefix:password"),
            buttons,
        )

        ImportSourceType.SUBSONIC -> listOf(
            listOf("$prefix:label"),
            listOf("$prefix:root"),
            listOf("$prefix:auth"),
            listOf("$prefix:username", "$prefix:password"),
            buttons,
        )

        ImportSourceType.EMBY -> listOf(
            listOf("$prefix:label"),
            listOf("$prefix:root"),
            listOf("$prefix:username", "$prefix:password"),
            buttons,
        )

        ImportSourceType.FN_MUSIC -> listOfNotNull(
            listOf("$prefix:label"),
            listOf("$prefix:fnconnect"),
            if (fnConnect) listOf("$prefix:fnid") else listOf("$prefix:root"),
            if (fnConnect) listOf("$prefix:access") else null,
            listOf("$prefix:username", "$prefix:password"),
            buttons,
        )

        ImportSourceType.LOCAL_FOLDER -> emptyList()
    }
}

/** Focus rows for [TvRemoteFolderTree], in the same order it renders them. */
private fun tvFolderTreeFocusRows(
    prefix: String,
    tree: RemoteFolderTreeState?,
    rows: List<RemoteFolderRow>,
    allowManualShare: Boolean,
): List<List<String>> {
    tree ?: return emptyList()
    return buildList {
        add(listOf("$prefix:tree-browse"))
        rows.forEach { row ->
            when (row) {
                is RemoteFolderRow.Folder -> add(listOf("$prefix:tree:${row.path}:expand", "$prefix:tree:${row.path}:check"))
                is RemoteFolderRow.Message -> if (row.isError) add(listOf("$prefix:tree:${row.parentPath}:retry"))
            }
        }
        if (tree.canAddManualShare && allowManualShare) {
            add(listOf("$prefix:tree-share", "$prefix:tree-share-add"))
        }
    }
}

/** D-pad version of the Samba/WebDAV folder picker: OK on the arrow expands a folder, OK on the box ticks it. */
@Composable
private fun TvRemoteFolderTree(
    tree: RemoteFolderTreeState,
    rows: List<RemoteFolderRow>,
    enabled: Boolean,
    allowManualShare: Boolean,
    onIntent: (ImportIntent) -> Unit,
    focusPrefix: String,
    focusChain: TvSettingsFocusChain,
) {
    var manualShare by remember { mutableStateOf("") }
    val rootNode = tree.nodes[RemoteFolderTreeState.ROOT]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(uiString(Res.string.source_folders_title), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (tree.selected.isEmpty()) {
                        uiString(Res.string.source_folders_hint)
                    } else {
                        uiPlural(Res.plurals.source_selected_folder_count, tree.selected.size, tree.selected.size).uiDisplayText()
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TvSettingsActionButton(
                onClick = { onIntent(ImportIntent.LoadRemoteFolder()) },
                enabled = enabled && rootNode?.isLoading != true,
                focusKey = "$focusPrefix:tree-browse",
                focusChain = focusChain,
                restoreFocusAfterClick = false,
            ) { contentColor ->
                Text(
                    uiString(if (tree.isBrowsed) Res.string.source_reload_folders else Res.string.source_browse_folders),
                    color = contentColor,
                )
            }
        }
        if (!tree.rootSelectable && rootNode?.isLoading == true) {
            Text(uiString(Res.string.folder_picker_loading_folder), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rows.forEach { row ->
            when (row) {
                is RemoteFolderRow.Folder -> Row(
                    modifier = Modifier.padding(start = (row.depth * 28).dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TvSettingsActionButton(
                        onClick = { onIntent(ImportIntent.ToggleRemoteFolderExpanded(row.path)) },
                        enabled = enabled && !row.isLoading,
                        focusKey = "$focusPrefix:tree:${row.path}:expand",
                        focusChain = focusChain,
                        restoreFocusAfterClick = false,
                    ) { contentColor ->
                        Icon(
                            imageVector = if (row.isExpanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = contentColor,
                        )
                    }
                    TvSettingsActionButton(
                        onClick = { onIntent(ImportIntent.ToggleRemoteFolderSelected(row.path)) },
                        enabled = enabled && !row.isLockedByAncestor,
                        style = if (row.isChecked) TvSettingsActionButtonStyle.Selected else TvSettingsActionButtonStyle.Outlined,
                        focusKey = "$focusPrefix:tree:${row.path}:check",
                        focusChain = focusChain,
                        restoreFocusAfterClick = false,
                    ) { contentColor ->
                        Icon(
                            imageVector = if (row.isChecked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                            contentDescription = null,
                            tint = contentColor,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(row.name, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                is RemoteFolderRow.Message -> Row(
                    modifier = Modifier.padding(start = (row.depth * 28 + 12).dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = row.text.uiDisplayText(),
                        color = if (row.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (row.isError) {
                        TvSettingsActionButton(
                            onClick = {
                                // This row disappears once the retry works; park focus on a row that stays.
                                val stableKey = if (row.parentPath == RemoteFolderTreeState.ROOT) {
                                    "$focusPrefix:tree-browse"
                                } else {
                                    "$focusPrefix:tree:${row.parentPath}:expand"
                                }
                                focusChain.requestFocus(stableKey)
                                onIntent(ImportIntent.LoadRemoteFolder(row.parentPath))
                            },
                            enabled = enabled,
                            focusKey = "$focusPrefix:tree:${row.parentPath}:retry",
                            focusChain = focusChain,
                            restoreFocusAfterClick = false,
                        ) { contentColor ->
                            Text(uiString(Res.string.source_reload_folders), color = contentColor)
                        }
                    }
                }
            }
        }
        if (tree.canAddManualShare && allowManualShare) {
            Text(
                uiString(Res.string.source_samba_manual_share_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvSettingsTextField(
                    label = uiString(Res.string.source_samba_manual_share),
                    value = manualShare,
                    onValueChange = { manualShare = it },
                    modifier = Modifier.weight(1f),
                    focusKey = "$focusPrefix:tree-share",
                    focusChain = focusChain,
                )
                TvSettingsActionButton(
                    onClick = {
                        onIntent(ImportIntent.AddManualSambaShare(manualShare))
                        manualShare = ""
                    },
                    enabled = enabled && manualShare.isNotBlank(),
                    focusKey = "$focusPrefix:tree-share-add",
                    focusChain = focusChain,
                    restoreFocusAfterClick = false,
                ) { contentColor ->
                    Text(uiString(Res.string.source_samba_manual_share_add), color = contentColor)
                }
            }
        }
    }
}

@Composable
private fun TvSambaSourceForm(
    state: ImportState,
    onIntent: (ImportIntent) -> Unit,
    focusPrefix: String,
    focusChain: TvSettingsFocusChain,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TvSettingsTextField(
            label = uiString(Res.string.common_name),
            value = state.sambaLabel,
            onValueChange = { onIntent(ImportIntent.SambaLabelChanged(it)) },
            placeholder = "Samba",
            focusKey = "$focusPrefix:label",
            focusChain = focusChain,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvSettingsTextField(
                label = uiString(Res.string.tv_source_server_label),
                value = state.sambaServer,
                onValueChange = { onIntent(ImportIntent.SambaServerChanged(it)) },
                placeholder = "192.168.31.115",
                modifier = Modifier.weight(1f),
                focusKey = "$focusPrefix:server",
                focusChain = focusChain,
            )
            TvSettingsTextField(
                label = uiString(Res.string.common_port),
                value = state.sambaPort,
                onValueChange = { onIntent(ImportIntent.SambaPortChanged(it)) },
                placeholder = "445",
                modifier = Modifier.width(140.dp),
                focusKey = "$focusPrefix:port",
                focusChain = focusChain,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvSettingsTextField(
                label = uiString(Res.string.common_username),
                value = state.sambaUsername,
                onValueChange = { onIntent(ImportIntent.SambaUsernameChanged(it)) },
                modifier = Modifier.weight(1f),
                focusKey = "$focusPrefix:username",
                focusChain = focusChain,
            )
            TvSettingsTextField(
                label = uiString(Res.string.common_password),
                value = state.sambaPassword,
                onValueChange = { onIntent(ImportIntent.SambaPasswordChanged(it)) },
                modifier = Modifier.weight(1f),
                password = true,
                focusKey = "$focusPrefix:password",
                focusChain = focusChain,
            )
        }
    }
}

@Composable
private fun TvWebDavSourceForm(
    state: ImportState,
    onIntent: (ImportIntent) -> Unit,
    focusPrefix: String,
    focusChain: TvSettingsFocusChain,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TvSettingsTextField(
            label = uiString(Res.string.common_name),
            value = state.webDavLabel,
            onValueChange = { onIntent(ImportIntent.WebDavLabelChanged(it)) },
            placeholder = "WebDAV",
            focusKey = "$focusPrefix:label",
            focusChain = focusChain,
        )
        TvSettingsTextField(
            label = uiString(Res.string.source_server_address_label),
            value = state.webDavRootUrl,
            onValueChange = { onIntent(ImportIntent.WebDavRootUrlChanged(it)) },
            placeholder = uiString(Res.string.source_webdav_url_example),
            focusKey = "$focusPrefix:root",
            focusChain = focusChain,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvSettingsTextField(
                label = uiString(Res.string.common_username),
                value = state.webDavUsername,
                onValueChange = { onIntent(ImportIntent.WebDavUsernameChanged(it)) },
                modifier = Modifier.weight(1f),
                focusKey = "$focusPrefix:username",
                focusChain = focusChain,
            )
            TvSettingsTextField(
                label = uiString(Res.string.common_password),
                value = state.webDavPassword,
                onValueChange = { onIntent(ImportIntent.WebDavPasswordChanged(it)) },
                modifier = Modifier.weight(1f),
                password = true,
                focusKey = "$focusPrefix:password",
                focusChain = focusChain,
            )
        }
        TvSettingsSwitchRow(
            title = uiString(Res.string.tv_source_allow_insecure_tls),
            checked = state.webDavAllowInsecureTls,
            onCheckedChange = { onIntent(ImportIntent.WebDavAllowInsecureTlsChanged(it)) },
            focusKey = "$focusPrefix:tls",
            focusChain = focusChain,
        )
    }
}

@Composable
private fun TvNavidromeSourceForm(
    state: ImportState,
    onIntent: (ImportIntent) -> Unit,
    focusPrefix: String,
    focusChain: TvSettingsFocusChain,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TvSettingsTextField(
            label = uiString(Res.string.common_name),
            value = state.navidromeLabel,
            onValueChange = { onIntent(ImportIntent.NavidromeLabelChanged(it)) },
            placeholder = "Navidrome",
            focusKey = "$focusPrefix:label",
            focusChain = focusChain,
        )
        TvSettingsTextField(
            label = uiString(Res.string.common_server_address),
            value = state.navidromeBaseUrl,
            onValueChange = { onIntent(ImportIntent.NavidromeBaseUrlChanged(it)) },
            placeholder = "http://192.168.31.115:32700",
            focusKey = "$focusPrefix:root",
            focusChain = focusChain,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvSettingsTextField(
                label = uiString(Res.string.common_username),
                value = state.navidromeUsername,
                onValueChange = { onIntent(ImportIntent.NavidromeUsernameChanged(it)) },
                modifier = Modifier.weight(1f),
                focusKey = "$focusPrefix:username",
                focusChain = focusChain,
            )
            TvSettingsTextField(
                label = uiString(Res.string.common_password),
                value = state.navidromePassword,
                onValueChange = { onIntent(ImportIntent.NavidromePasswordChanged(it)) },
                modifier = Modifier.weight(1f),
                password = true,
                focusKey = "$focusPrefix:password",
                focusChain = focusChain,
            )
        }
    }
}

@Composable
private fun TvSubsonicSourceForm(
    state: ImportState,
    onIntent: (ImportIntent) -> Unit,
    focusPrefix: String,
    focusChain: TvSettingsFocusChain,
) {
    val apiKeyMode = state.subsonicAuthMode == SubsonicAuthMode.API_KEY
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TvSettingsTextField(
            label = uiString(Res.string.common_name),
            value = state.subsonicLabel,
            onValueChange = { onIntent(ImportIntent.SubsonicLabelChanged(it)) },
            placeholder = "Subsonic",
            focusKey = "$focusPrefix:label",
            focusChain = focusChain,
        )
        TvSettingsTextField(
            label = uiString(Res.string.common_server_address),
            value = state.subsonicBaseUrl,
            onValueChange = { onIntent(ImportIntent.SubsonicBaseUrlChanged(it)) },
            placeholder = "https://music.example.com",
            focusKey = "$focusPrefix:root",
            focusChain = focusChain,
        )
        TvSettingsSwitchRow(
            title = uiString(Res.string.tv_source_api_key_authentication),
            checked = apiKeyMode,
            onCheckedChange = {
                onIntent(
                    ImportIntent.SubsonicAuthModeChanged(
                        if (it) SubsonicAuthMode.API_KEY else SubsonicAuthMode.PASSWORD,
                    ),
                )
            },
            focusKey = "$focusPrefix:auth",
            focusChain = focusChain,
        )
        if (apiKeyMode) {
            TvSettingsTextField(
                label = "API Key",
                value = state.subsonicCredential,
                onValueChange = { onIntent(ImportIntent.SubsonicCredentialChanged(it)) },
                password = true,
                focusKey = "$focusPrefix:password",
                focusChain = focusChain,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvSettingsTextField(
                    label = uiString(Res.string.common_username),
                    value = state.subsonicUsername,
                    onValueChange = { onIntent(ImportIntent.SubsonicUsernameChanged(it)) },
                    modifier = Modifier.weight(1f),
                    focusKey = "$focusPrefix:username",
                    focusChain = focusChain,
                )
                TvSettingsTextField(
                    label = uiString(Res.string.common_password),
                    value = state.subsonicCredential,
                    onValueChange = { onIntent(ImportIntent.SubsonicCredentialChanged(it)) },
                    modifier = Modifier.weight(1f),
                    password = true,
                    focusKey = "$focusPrefix:password",
                    focusChain = focusChain,
                )
            }
        }
    }
}

@Composable
private fun TvEmbySourceForm(
    state: ImportState,
    onIntent: (ImportIntent) -> Unit,
    focusPrefix: String,
    focusChain: TvSettingsFocusChain,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TvSettingsTextField(
            label = uiString(Res.string.common_name),
            value = state.embyLabel,
            onValueChange = { onIntent(ImportIntent.EmbyLabelChanged(it)) },
            placeholder = "Emby",
            focusKey = "$focusPrefix:label",
            focusChain = focusChain,
        )
        TvSettingsTextField(
            label = uiString(Res.string.common_server_address),
            value = state.embyBaseUrl,
            onValueChange = { onIntent(ImportIntent.EmbyBaseUrlChanged(it)) },
            placeholder = "https://media.example.com",
            focusKey = "$focusPrefix:root",
            focusChain = focusChain,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvSettingsTextField(
                label = uiString(Res.string.common_username),
                value = state.embyUsername,
                onValueChange = { onIntent(ImportIntent.EmbyUsernameChanged(it)) },
                modifier = Modifier.weight(1f),
                focusKey = "$focusPrefix:username",
                focusChain = focusChain,
            )
            TvSettingsTextField(
                label = uiString(Res.string.common_password),
                value = state.embyPassword,
                onValueChange = { onIntent(ImportIntent.EmbyPasswordChanged(it)) },
                modifier = Modifier.weight(1f),
                password = true,
                focusKey = "$focusPrefix:password",
                focusChain = focusChain,
            )
        }
    }
}

@Composable
private fun TvFnMusicSourceForm(
    state: ImportState,
    onIntent: (ImportIntent) -> Unit,
    focusPrefix: String,
    focusChain: TvSettingsFocusChain,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TvSettingsTextField(
            label = uiString(Res.string.common_name),
            value = state.fnMusicLabel,
            onValueChange = { onIntent(ImportIntent.FnMusicLabelChanged(it)) },
            placeholder = uiString(Res.string.fn_music_name),
            focusKey = "$focusPrefix:label",
            focusChain = focusChain,
        )
        TvFnMusicConnectionFields(
            connectionMode = state.fnMusicConnectionMode,
            baseUrl = state.fnMusicBaseUrl,
            fnId = state.fnMusicId,
            accessCode = state.fnMusicAccessCode,
            isEditing = false,
            onConnectionModeChange = { onIntent(ImportIntent.FnMusicConnectionModeChanged(it)) },
            onBaseUrlChange = { onIntent(ImportIntent.FnMusicBaseUrlChanged(it)) },
            onFnIdChange = { onIntent(ImportIntent.FnMusicIdChanged(it)) },
            onAccessCodeChange = { onIntent(ImportIntent.FnMusicAccessCodeChanged(it)) },
            focusPrefix = focusPrefix,
            focusChain = focusChain,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvSettingsTextField(
                label = uiString(Res.string.common_username),
                value = state.fnMusicUsername,
                onValueChange = { onIntent(ImportIntent.FnMusicUsernameChanged(it)) },
                modifier = Modifier.weight(1f),
                focusKey = "$focusPrefix:username",
                focusChain = focusChain,
            )
            TvSettingsTextField(
                label = uiString(Res.string.common_password),
                value = state.fnMusicPassword,
                onValueChange = { onIntent(ImportIntent.FnMusicPasswordChanged(it)) },
                modifier = Modifier.weight(1f),
                password = true,
                focusKey = "$focusPrefix:password",
                focusChain = focusChain,
            )
        }
    }
}

/** FN Connect switch plus either the server address or the FN ID and access code. */
@Composable
private fun TvFnMusicConnectionFields(
    connectionMode: FnMusicConnectionMode,
    baseUrl: String,
    fnId: String,
    accessCode: String,
    isEditing: Boolean,
    onConnectionModeChange: (FnMusicConnectionMode) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onFnIdChange: (String) -> Unit,
    onAccessCodeChange: (String) -> Unit,
    focusPrefix: String,
    focusChain: TvSettingsFocusChain,
) {
    val fnConnect = connectionMode == FnMusicConnectionMode.FN_CONNECT
    TvSettingsSwitchRow(
        title = uiString(Res.string.fn_music_connection_mode_fn_connect),
        checked = fnConnect,
        onCheckedChange = {
            onConnectionModeChange(if (it) FnMusicConnectionMode.FN_CONNECT else FnMusicConnectionMode.ADDRESS)
        },
        focusKey = "$focusPrefix:fnconnect",
        focusChain = focusChain,
    )
    if (fnConnect) {
        TvSettingsTextField(
            label = uiString(Res.string.fn_music_fn_id_label),
            value = fnId,
            onValueChange = onFnIdChange,
            placeholder = uiString(Res.string.fn_music_fn_id_placeholder),
            focusKey = "$focusPrefix:fnid",
            focusChain = focusChain,
        )
        TvSettingsTextField(
            label = uiString(Res.string.fn_music_access_code_label),
            value = accessCode,
            onValueChange = onAccessCodeChange,
            placeholder = if (isEditing) uiString(Res.string.fn_music_access_code_keep_hint) else "",
            password = true,
            focusKey = "$focusPrefix:access",
            focusChain = focusChain,
        )
    } else {
        TvSettingsTextField(
            label = uiString(Res.string.common_server_address),
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            placeholder = "http://192.168.1.2:5666",
            focusKey = "$focusPrefix:root",
            focusChain = focusChain,
        )
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
                        text = if (source.enabled) uiString(Res.string.common_enabled) else uiString(Res.string.common_inactive),
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
                Text(uiString(Res.string.common_rescan), color = contentColor)
            }
            TvSettingsActionButton(
                onClick = { onIntent(ImportIntent.ToggleSourceEnabled(source.id, !source.enabled)) },
                enabled = !working,
                focusKey = "sources:${source.id}:toggle",
                focusChain = focusChain,
            ) { contentColor ->
                Text(if (source.enabled) uiString(Res.string.common_deactivate) else uiString(Res.string.common_enable), color = contentColor)
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
                    Text(uiString(Res.string.common_edit), color = contentColor)
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
                Text(uiString(Res.string.common_delete), color = contentColor)
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
    val focusPrefix = remember(editor.sourceId, editor.type) { "edit:${editor.sourceId}:${editor.type.name}" }
    val folderRootName = when (editor.type) {
        ImportSourceType.SAMBA -> formatSambaEndpoint(editor.server, editor.port.toIntOrNull(), editor.path)
        else -> editor.rootUrl.trim()
    }
    // Flatten the tree once: the focus rows and the rendered rows must match anyway.
    val folderRows = state.remoteFolderTree?.visibleRows(folderRootName).orEmpty()
    val treeFocusRows = tvFolderTreeFocusRows(focusPrefix, state.remoteFolderTree, folderRows, state.capabilities.supportsSambaImport)
    val focusChain = rememberTvDialogFocusChain(
        focusRows = remember(focusPrefix, editor.type, treeFocusRows, editor.fnMusicConnectionMode) {
            remoteSourceDialogFocusRows(
                prefix = focusPrefix,
                type = editor.type,
                folderTreeRows = treeFocusRows,
                fnConnect = editor.fnMusicConnectionMode == FnMusicConnectionMode.FN_CONNECT,
            )
        },
    )
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
                    text = uiString(Res.string.tv_source_edit_named_type, sourceTypeTitle(editor.type)),
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
                    TvSettingsTextField(
                        label = uiString(Res.string.common_name),
                        value = editor.label,
                        onValueChange = { onIntent(ImportIntent.RemoteSourceLabelChanged(it)) },
                        focusKey = "$focusPrefix:label",
                        focusChain = focusChain,
                    )
                    when (editor.type) {
                        ImportSourceType.SAMBA -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TvSettingsTextField(
                                    label = uiString(Res.string.tv_source_server_label),
                                    value = editor.server,
                                    onValueChange = { onIntent(ImportIntent.RemoteSourceServerChanged(it)) },
                                    modifier = Modifier.weight(1f),
                                    focusKey = "$focusPrefix:server",
                                    focusChain = focusChain,
                                )
                                TvSettingsTextField(
                                    label = uiString(Res.string.common_port),
                                    value = editor.port,
                                    onValueChange = { onIntent(ImportIntent.RemoteSourcePortChanged(it)) },
                                    placeholder = uiString(Res.string.tv_settings_optional_field),
                                    modifier = Modifier.width(140.dp),
                                    focusKey = "$focusPrefix:port",
                                    focusChain = focusChain,
                                )
                            }
                        }

                        ImportSourceType.WEBDAV -> {
                            TvSettingsTextField(
                                label = uiString(Res.string.tv_source_root_address),
                                value = editor.rootUrl,
                                onValueChange = { onIntent(ImportIntent.RemoteSourceRootUrlChanged(it)) },
                                focusKey = "$focusPrefix:root",
                                focusChain = focusChain,
                            )
                        }

                        ImportSourceType.NAVIDROME -> {
                            TvSettingsTextField(
                                label = uiString(Res.string.common_server_address),
                                value = editor.rootUrl,
                                onValueChange = { onIntent(ImportIntent.RemoteSourceRootUrlChanged(it)) },
                                focusKey = "$focusPrefix:root",
                                focusChain = focusChain,
                            )
                        }

                        ImportSourceType.SUBSONIC -> {
                            TvSettingsTextField(
                                label = uiString(Res.string.common_server_address),
                                value = editor.rootUrl,
                                onValueChange = { onIntent(ImportIntent.RemoteSourceRootUrlChanged(it)) },
                                focusKey = "$focusPrefix:root",
                                focusChain = focusChain,
                            )
                        }

                        ImportSourceType.EMBY -> {
                            TvSettingsTextField(
                                label = uiString(Res.string.common_server_address),
                                value = editor.rootUrl,
                                onValueChange = { onIntent(ImportIntent.RemoteSourceRootUrlChanged(it)) },
                                focusKey = "$focusPrefix:root",
                                focusChain = focusChain,
                            )
                        }

                        ImportSourceType.FN_MUSIC -> TvFnMusicConnectionFields(
                            connectionMode = editor.fnMusicConnectionMode,
                            baseUrl = editor.rootUrl,
                            fnId = editor.fnMusicId,
                            accessCode = editor.fnMusicAccessCode,
                            isEditing = true,
                            onConnectionModeChange = { onIntent(ImportIntent.RemoteSourceFnMusicConnectionModeChanged(it)) },
                            onBaseUrlChange = { onIntent(ImportIntent.RemoteSourceRootUrlChanged(it)) },
                            onFnIdChange = { onIntent(ImportIntent.RemoteSourceFnMusicIdChanged(it)) },
                            onAccessCodeChange = { onIntent(ImportIntent.RemoteSourceFnMusicAccessCodeChanged(it)) },
                            focusPrefix = focusPrefix,
                            focusChain = focusChain,
                        )

                        ImportSourceType.LOCAL_FOLDER -> Unit
                    }
                    if (editor.type == ImportSourceType.SUBSONIC) {
                        TvSettingsSwitchRow(
                            title = uiString(Res.string.tv_source_api_key_authentication),
                            checked = editor.subsonicAuthMode == SubsonicAuthMode.API_KEY,
                            onCheckedChange = {
                                onIntent(
                                    ImportIntent.RemoteSourceSubsonicAuthModeChanged(
                                        if (it) SubsonicAuthMode.API_KEY else SubsonicAuthMode.PASSWORD,
                                    ),
                                )
                            },
                            focusKey = "$focusPrefix:auth",
                            focusChain = focusChain,
                        )
                    }
                    if (editor.type == ImportSourceType.SUBSONIC && editor.subsonicAuthMode == SubsonicAuthMode.API_KEY) {
                        TvSettingsTextField(
                            label = "API Key",
                            value = editor.password,
                            onValueChange = { onIntent(ImportIntent.RemoteSourcePasswordChanged(it)) },
                            placeholder = if (editor.hasStoredCredential) uiString(Res.string.tv_source_saved_api_key_hint) else "",
                            password = true,
                            focusKey = "$focusPrefix:password",
                            focusChain = focusChain,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TvSettingsTextField(
                                label = uiString(Res.string.common_username),
                                value = editor.username,
                                onValueChange = { onIntent(ImportIntent.RemoteSourceUsernameChanged(it)) },
                                modifier = Modifier.weight(1f),
                                focusKey = "$focusPrefix:username",
                                focusChain = focusChain,
                            )
                            TvSettingsTextField(
                                label = uiString(Res.string.common_password),
                                value = editor.password,
                                onValueChange = { onIntent(ImportIntent.RemoteSourcePasswordChanged(it)) },
                                placeholder = if (editor.hasStoredCredential) uiString(Res.string.tv_source_saved_password_hint) else "",
                                modifier = Modifier.weight(1f),
                                password = true,
                                focusKey = "$focusPrefix:password",
                                focusChain = focusChain,
                            )
                        }
                    }
                    if (editor.type == ImportSourceType.WEBDAV) {
                        TvSettingsSwitchRow(
                            title = uiString(Res.string.tv_source_allow_insecure_tls),
                            checked = editor.allowInsecureTls,
                            onCheckedChange = { onIntent(ImportIntent.RemoteSourceAllowInsecureTlsChanged(it)) },
                            focusKey = "$focusPrefix:tls",
                            focusChain = focusChain,
                        )
                    }
                    state.remoteFolderTree?.let { tree ->
                        TvRemoteFolderTree(
                            tree = tree,
                            rows = folderRows,
                            enabled = !state.isWorking,
                            allowManualShare = state.capabilities.supportsSambaImport,
                            onIntent = onIntent,
                            focusPrefix = focusPrefix,
                            focusChain = focusChain,
                        )
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
                                text = message.uiDisplayText(),
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
                        focusKey = "$focusPrefix:test",
                        focusChain = focusChain,
                    ) { contentColor ->
                        Text(uiString(Res.string.tv_source_test_connection), color = contentColor)
                    }
                    TvSettingsActionButton(
                        onClick = { onIntent(ImportIntent.DismissRemoteSourceEditor) },
                        focusKey = "$focusPrefix:cancel",
                        focusChain = focusChain,
                    ) { contentColor ->
                        Text(uiString(Res.string.common_cancel), color = contentColor)
                    }
                    TvSettingsActionButton(
                        onClick = { onIntent(ImportIntent.SaveRemoteSource) },
                        enabled = !state.isWorking && state.remoteFolderTree?.selected?.isNotEmpty() != false,
                        style = TvSettingsActionButtonStyle.Filled,
                        focusKey = "$focusPrefix:submit",
                        focusChain = focusChain,
                    ) { contentColor ->
                        Text(uiString(Res.string.tv_source_save_and_scan), color = contentColor)
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
                title = uiString(Res.string.settings_storage_management_title),
                subtitle = uiString(Res.string.tv_storage_management_hint),
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
                    message = message.uiDisplayText(),
                    onClear = { onIntent(SettingsIntent.ClearMessage) },
                    focusKey = "storage:message:clear",
                    focusChain = focusChain,
                )
            }
        }
        item {
            TvSettingsInfoCard(title = uiString(Res.string.storage_manageable_usage)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = state.storageSnapshot?.let { formatTvStorageSize(it.totalSizeBytes) }
                                ?: if (state.storageLoading) uiString(Res.string.storage_calculating_usage_progress) else uiString(Res.string.common_not_read),
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
                        Text(if (state.storageLoading) uiString(Res.string.common_refreshing) else uiString(Res.string.common_refresh), color = contentColor)
                    }
                }
            }
        }
        if (categories.isEmpty()) {
            item {
                TvSettingsEmptyCard(
                    title = if (state.storageLoading) uiString(Res.string.storage_calculating_usage) else uiString(Res.string.storage_no_manageable_data),
                    body = if (state.storageLoading) uiString(Res.string.storage_reading_locations_description) else uiString(Res.string.storage_no_cleanup_categories),
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
                Text(if (clearing) uiString(Res.string.storage_cleaning_progress) else uiString(Res.string.common_clean_up), color = contentColor)
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
                title = uiString(Res.string.about_device_title),
                subtitle = uiString(Res.string.tv_device_information_hint),
            )
        }
        item {
            TvSettingsInfoCard(title = state.deviceInfoSnapshot?.deviceModel?.takeIf { it.isNotBlank() } ?: uiString(Res.string.tv_device_information_title)) {
                TvSettingsActionButton(
                    onClick = { onIntent(SettingsIntent.LoadDeviceInfo(force = true)) },
                    enabled = !state.deviceInfoLoading,
                    focusKey = "device:refresh",
                    focusChain = focusChain,
                ) { contentColor ->
                    Icon(Icons.Rounded.Sync, contentDescription = null, tint = contentColor)
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.deviceInfoLoading) uiString(Res.string.tv_settings_reading_label) else uiString(Res.string.common_refresh), color = contentColor)
                }
            }
        }
        item {
            TvDeviceInfoGroup(
                title = uiString(Res.string.device_system_label),
                rows = listOf(
                    uiString(Res.string.device_system_name) to deviceInfoValue(state.deviceInfoSnapshot?.systemName, state.deviceInfoLoading),
                    uiString(Res.string.device_system_version) to deviceInfoValue(state.deviceInfoSnapshot?.systemVersion, state.deviceInfoLoading),
                    uiString(Res.string.device_model_label) to deviceInfoValue(state.deviceInfoSnapshot?.deviceModel, state.deviceInfoLoading),
                ),
            )
        }
        item {
            val snapshot = state.deviceInfoSnapshot
            TvDeviceInfoGroup(
                title = uiString(Res.string.device_display_label),
                rows = listOf(
                    uiString(Res.string.display_resolution_label) to deviceInfoValue(snapshot?.resolution, state.deviceInfoLoading),
                    uiString(Res.string.display_app_resolution_dp) to deviceDpResolution(snapshot, density.density, state.deviceInfoLoading),
                    uiString(Res.string.display_system_density) to deviceDensity(snapshot?.systemDensityScale, state.deviceInfoLoading),
                    uiString(Res.string.display_font_scale) to "%.2f".format(density.fontScale),
                ),
            )
        }
        item {
            TvDeviceInfoGroup(
                title = uiString(Res.string.device_hardware_label),
                rows = listOf(
                    "CPU" to deviceInfoValue(state.deviceInfoSnapshot?.cpuDescriptionText?.displayText(), state.deviceInfoLoading),
                    uiString(Res.string.device_memory_label) to (
                        state.deviceInfoSnapshot?.totalMemoryBytes?.let(::formatTvStorageSize)
                            ?: if (state.deviceInfoLoading) uiString(Res.string.tv_settings_reading_progress) else uiString(Res.string.common_unavailable)
                        ),
                ),
            )
        }
    }
}

@Composable
private fun TvAboutAppSettingsPane(
    platformName: String,
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    initialFocusRequester: FocusRequester,
    leftFocusRequester: FocusRequester,
    focusCoordinator: TvSettingsFocusCoordinator,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val appUpdateUiModel = state.toAppUpdateUiModel()
    val appUpdateChecking = appUpdateUiModel.status == AppUpdateUiStatus.Checking
    val listState = rememberLazyListState()
    val focusChain = rememberTvSettingsFocusChain(
        focusRows = listOf(
            listOf("about-app:scroll"),
            listOf("about-app:check-update"),
            listOf("about-app:open-release"),
        ),
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
                title = uiString(Res.string.about_app_title),
                subtitle = uiString(Res.string.tv_about_app_information_hint),
            )
        }
        item {
            TvSettingsScrollAnchor(
                modifier = Modifier.tvSettingsFocusTarget("about-app:scroll", focusChain),
            )
        }
        item {
            TvDeviceInfoGroup(
                title = uiString(Res.string.common_basic_information),
                rows = listOf(
                    uiString(Res.string.tv_about_app_name) to "LynMusic",
                    uiString(Res.string.about_version) to BuildMetadata.versionDisplay,
                    uiString(Res.string.device_platform_label) to platformName,
                    uiString(Res.string.about_build_time) to BuildMetadata.buildTimeUtc,
                ),
            )
        }
        item {
            TvSettingsInfoCard(title = uiString(Res.string.settings_app_updates_title)) {
                when (appUpdateUiModel.status) {
                    AppUpdateUiStatus.Checking -> {
                        Text(
                            text = appUpdateUiModel.message.uiDisplayText(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    AppUpdateUiStatus.Error -> {
                        Text(
                            text = appUpdateUiModel.message.uiDisplayText(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    AppUpdateUiStatus.UpdateAvailable -> {
                        TvSettingsFieldRow(label = uiString(Res.string.update_latest_version), value = appUpdateUiModel.latestVersion.orEmpty())
                        Text(
                            text = appUpdateUiModel.message.uiDisplayText(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    AppUpdateUiStatus.UpToDate -> {
                        Text(
                            text = appUpdateUiModel.message.uiDisplayText(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    AppUpdateUiStatus.Idle -> Unit
                }
                appUpdateUiModel.errorMessage?.let { errorMessage ->
                    Text(
                        text = errorMessage.uiDisplayText(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TvSettingsActionButton(
                    onClick = { onIntent(SettingsIntent.CheckAppUpdate) },
                    enabled = !appUpdateChecking,
                    focusKey = "about-app:check-update",
                    focusChain = focusChain,
                ) { contentColor ->
                    if (appUpdateChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = contentColor,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (appUpdateChecking) uiString(Res.string.common_checking) else uiString(Res.string.update_check_action), color = contentColor)
                }
                if (appUpdateUiModel.status == AppUpdateUiStatus.UpdateAvailable) {
                    TvSettingsActionButton(
                        onClick = {
                            uriHandler.openUri(appUpdateUiModel.downloadUrl)
                        },
                        style = TvSettingsActionButtonStyle.Filled,
                        focusKey = "about-app:open-release",
                        focusChain = focusChain,
                    ) { contentColor ->
                        Text(uiString(Res.string.update_open_download_page), color = contentColor)
                    }
                }
            }
        }
        item {
            TvDeviceInfoGroup(
                title = uiString(Res.string.about_developer_label),
                rows = listOf(
                    uiString(Res.string.common_name) to "Wesley",
                    uiString(Res.string.about_project_website) to LynMusicUpdateLinks.PROJECT_URL,
                ),
            )
        }
        item {
            TvSettingsInfoCard(title = uiString(Res.string.about_wechat_account)) {
                TvSettingsFieldRow(label = uiString(Res.string.common_account), value = uiString(Res.string.about_author_name))
                Text(
                    text = uiString(Res.string.about_wechat_qr_code),
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
                    text = uiString(Res.string.about_wechat_follow_hint),
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
            contentDescription = uiString(Res.string.about_wechat_qr_code),
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

@Composable
private fun rememberTvDialogFocusChain(
    focusRows: List<List<String>>,
): TvSettingsFocusChain {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusKeys = remember(focusRows) { focusRows.flatten() }
    // Rows change while the dialog is open (e.g. folders expanding); keep each key's requester so focus stays put.
    val requesterCache = remember { mutableMapOf<String, FocusRequester>() }
    val requesters = remember(focusKeys) {
        focusKeys.associateWith { key -> requesterCache.getOrPut(key) { FocusRequester() } }
    }
    val chain = remember(focusRows, focusKeys, requesters, listState, coroutineScope) {
        TvSettingsFocusChain(
            focusRows = focusRows,
            requesters = requesters,
            listState = listState,
            coroutineScope = coroutineScope,
            leftFocusRequester = null,
            focusCoordinator = null,
        )
    }
    val currentChain = androidx.compose.runtime.rememberUpdatedState(chain)
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusKeys.firstOrNull()?.let { currentChain.value.requestFocus(it) }
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
    val leftFocusRequester: FocusRequester?,
    private val focusCoordinator: TvSettingsFocusCoordinator?,
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
        focusCoordinator?.markContentFocused(key)
    }

    fun requestRestoreAfterAction() {
        val coordinator = focusCoordinator ?: return
        coordinator.requestContentFocusRestore()
        coroutineScope.launch {
            withFrameNanos { }
            coordinator.restoreContentFocusIfRequested()
        }
    }

    fun restoreFocus(): Boolean {
        val focusKeys = focusRows.flatten()
        val candidates = buildList {
            val preferredKey = focusCoordinator?.lastContentFocusKey ?: lastFocusedKey
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

    fun requestFocus(key: String): Boolean {
        return requestFocusSafely(key)
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
        leftFocusRequester?.requestFocus()
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
            focusChain.leftFocusRequester?.let { left = it }
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
    TvSettingsInfoCard(title = uiString(Res.string.tv_settings_notice_title)) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TvSettingsActionButton(
            onClick = onClear,
            focusKey = focusKey,
            focusChain = focusChain,
        ) { contentColor ->
            Text(uiString(Res.string.common_got_it), color = contentColor)
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

@OptIn(ExperimentalLayoutApi::class)
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
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    var editing by remember { mutableStateOf(false) }
    var imeWasVisibleDuringEditing by remember { mutableStateOf(false) }
    var fieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length),
            ),
        )
    }

    fun enterEditing() {
        imeWasVisibleDuringEditing = false
        fieldValue = fieldValue.copy(selection = TextRange(fieldValue.text.length))
        editing = true
    }

    fun exitEditing() {
        imeWasVisibleDuringEditing = false
        editing = false
        keyboardController?.hide()
    }

    BackHandler(enabled = editing) {
        exitEditing()
    }

    LaunchedEffect(editing) {
        if (editing) {
            withFrameNanos { }
            keyboardController?.show()
        }
    }

    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(
                text = value,
                selection = TextRange(value.length),
            )
        }
    }

    LaunchedEffect(editing, imeVisible) {
        if (!editing) {
            imeWasVisibleDuringEditing = false
            return@LaunchedEffect
        }
        if (imeVisible) {
            imeWasVisibleDuringEditing = true
        } else if (imeWasVisibleDuringEditing) {
            exitEditing()
        }
    }

    val targetKey = focusKey
    val targetChain = focusChain
    val fieldModifier = if (targetKey != null && targetChain?.contains(targetKey) == true) {
        modifier.tvSettingsTextFieldFocusTarget(
            key = targetKey,
            focusChain = targetChain,
            editing = editing,
            enterEditing = ::enterEditing,
            exitEditing = ::exitEditing,
        )
    } else {
        modifier
    }.height(72.dp)
    OutlinedTextField(
        value = fieldValue,
        onValueChange = { nextValue ->
            fieldValue = nextValue
            if (nextValue.text != value) {
                onValueChange(nextValue.text)
            }
        },
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder) }
        } else {
            null
        },
        singleLine = true,
        readOnly = !editing,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done,
            showKeyboardOnFocus = false,
        ),
        keyboardActions = KeyboardActions(
            onDone = { exitEditing() },
        ),
        modifier = fieldModifier,
    )
}

@Composable
private fun Modifier.tvSettingsTextFieldFocusTarget(
    key: String,
    focusChain: TvSettingsFocusChain,
    editing: Boolean,
    enterEditing: () -> Unit,
    exitEditing: () -> Unit,
): Modifier {
    DisposableEffect(focusChain, key) {
        focusChain.attach(key)
        onDispose { focusChain.detach(key) }
    }
    return this
        .focusRequester(focusChain.requesterFor(key))
        .focusProperties {
            focusChain.leftFocusRequester?.let { left = it }
        }
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                focusChain.markFocused(key)
            } else {
                exitEditing()
            }
        }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionCenter,
                Key.Enter,
                Key.NumPadEnter,
                -> {
                    if (editing) {
                        exitEditing()
                    } else {
                        enterEditing()
                    }
                    true
                }

                Key.Back,
                Key.Escape,
                -> if (editing) {
                    exitEditing()
                    true
                } else {
                    false
                }

                Key.DirectionUp -> {
                    exitEditing()
                    focusChain.moveFrom(key, direction = -1, allowScrollFallback = false)
                }

                Key.DirectionDown -> {
                    exitEditing()
                    focusChain.moveFrom(key, direction = 1, allowScrollFallback = false)
                }

                Key.DirectionLeft -> {
                    exitEditing()
                    focusChain.moveHorizontal(key, direction = -1)
                }

                Key.DirectionRight -> {
                    exitEditing()
                    focusChain.moveHorizontal(key, direction = 1)
                }

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
private fun TvSettingsUnavailableScreen(
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiString(Res.string.tv_settings_unavailable_title), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text(uiString(Res.string.startup_component_initialization_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(onClick = onRetry) {
                    Text(uiString(Res.string.common_retry))
                }
                TvOutlinedButton(onClick = onBack) {
                    Text(uiString(Res.string.common_back))
                }
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
    val fixedDensity = remember(currentDensity.density, currentDensity.fontScale, appDisplayScalePreset) {
        Density(
            density = effectiveAppDisplayDensity(tvSettingsStableDensityScale(currentDensity.density), appDisplayScalePreset),
            fontScale = currentDensity.fontScale,
        )
    }
    CompositionLocalProvider(LocalDensity provides fixedDensity) {
        content()
    }
}

private fun tvSettingsStableDensityScale(fallbackDensity: Float): Float {
    val fallbackDpi = (fallbackDensity.takeIf { it > 0f } ?: 1f) * DisplayMetrics.DENSITY_DEFAULT
    val stableDpi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        DisplayMetrics.DENSITY_DEVICE_STABLE
    } else {
        fallbackDpi.roundToInt()
    }.takeIf { it > 0 } ?: fallbackDpi.roundToInt()
    return stableDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()
}

private enum class TvSettingsSection(
    private val titleResource: org.jetbrains.compose.resources.StringResource,
    private val subtitleResource: org.jetbrains.compose.resources.StringResource,
    val icon: ImageVector,
) {
    General(Res.string.settings_general_title, Res.string.language_description, Icons.Rounded.Settings),
    Sources(Res.string.sources_title, Res.string.tv_source_import_title, Icons.Rounded.Folder),
    Storage(Res.string.settings_storage_management_title, Res.string.tv_storage_caches_title, Icons.Rounded.Storage),
    AboutDevice(Res.string.about_device_title, Res.string.tv_device_information_description, Icons.Rounded.Info),
    AboutApp(Res.string.about_app_title, Res.string.tv_about_app_version_description, Icons.Rounded.Settings);

    val title: UiText get() = uiText(titleResource)
    val subtitle: UiText get() = uiText(subtitleResource)
}

private fun ImportScanOperation.sourceIdOrNull(): String? {
    return when (this) {
        is ImportScanOperation.RescanSource -> sourceId
        is ImportScanOperation.ReauthorizeLocalFolder -> sourceId
        is ImportScanOperation.UpdateRemote -> sourceId
        ImportScanOperation.CreateLocalFolder,
        is ImportScanOperation.CreateRemote -> null
    }
}

@Composable
private fun sourceTypeTitle(type: ImportSourceType): String {
    return when (type) {
        ImportSourceType.LOCAL_FOLDER -> uiString(Res.string.source_local_folder_label)
        ImportSourceType.SAMBA -> "Samba"
        ImportSourceType.WEBDAV -> "WebDAV"
        ImportSourceType.NAVIDROME -> "Navidrome"
        ImportSourceType.SUBSONIC -> "Subsonic"
        ImportSourceType.EMBY -> "Emby"
        ImportSourceType.FN_MUSIC -> uiString(Res.string.fn_music_name)
    }
}

private fun sourceTypeIcon(type: ImportSourceType): ImageVector {
    return when (type) {
        ImportSourceType.LOCAL_FOLDER -> Icons.Rounded.Folder
        ImportSourceType.SAMBA,
        ImportSourceType.WEBDAV,
        ImportSourceType.NAVIDROME,
        ImportSourceType.SUBSONIC,
        ImportSourceType.EMBY,
        ImportSourceType.FN_MUSIC -> Icons.Rounded.Cloud
    }
}

@Composable
private fun sourceDisplayReference(source: ImportSource): String {
    return when (source.type) {
        ImportSourceType.LOCAL_FOLDER -> source.rootReference
        ImportSourceType.SAMBA,
        ImportSourceType.WEBDAV,
        -> source.folderSourceEndpointText().displayText()
        ImportSourceType.NAVIDROME -> source.rootReference
        ImportSourceType.SUBSONIC -> source.rootReference
        ImportSourceType.EMBY -> source.rootReference
        ImportSourceType.FN_MUSIC -> fnMusicIdOf(source.rootReference)
            ?.let { uiString(Res.string.fn_music_fn_id_summary, it) }
            ?: source.rootReference
    }
}

@Composable
private fun sourceStatusText(
    sourceWithStatus: SourceWithStatus,
    latestSummary: top.iwesley.lyn.music.core.model.ImportScanSummary?,
): String {
    latestSummary?.let { return formatImportScanSummary(it).displayText() }
    val status = sourceWithStatus.indexState
    val lastError = status?.lastErrorUiText()
    return when {
        lastError != null -> uiString(Res.string.tv_source_last_scan_error, lastError)
        status != null -> uiString(Res.string.tv_source_track_summary, status.trackCount, formatTimestamp(status.lastScannedAt))
        else -> uiString(Res.string.tv_source_not_scanned_hint)
    }
}

@Composable
private fun formatTimestamp(value: Long?): String {
    if (value == null || value <= 0L) return uiString(Res.string.tv_source_not_scanned_status)
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
}

private val storageCategoryOrder = listOf(
    AppStorageCategory.Artwork,
    AppStorageCategory.PlaybackCache,
    AppStorageCategory.OfflineDownloads,
    AppStorageCategory.LyricsShareTemp,
    AppStorageCategory.TagEditTemp,
)

@Composable
private fun tvStorageCategoryTitle(category: AppStorageCategory): String {
    return when (category) {
        AppStorageCategory.Artwork -> uiString(Res.string.storage_artwork_cache)
        AppStorageCategory.PlaybackCache -> uiString(Res.string.storage_playback_cache)
        AppStorageCategory.OfflineDownloads -> uiString(Res.string.storage_offline_music)
        AppStorageCategory.LyricsShareTemp -> uiString(Res.string.storage_lyrics_share_temporary_files)
        AppStorageCategory.TagEditTemp -> uiString(Res.string.storage_tags_temporary_files)
    }
}

@Composable
private fun tvStorageCategoryDescription(category: AppStorageCategory): String {
    return when (category) {
        AppStorageCategory.Artwork -> uiString(Res.string.tv_storage_artwork_cache_description)
        AppStorageCategory.PlaybackCache -> uiString(Res.string.tv_storage_samba_cache_description)
        AppStorageCategory.OfflineDownloads -> uiString(Res.string.tv_storage_offline_music_description)
        AppStorageCategory.LyricsShareTemp -> uiString(Res.string.tv_storage_lyrics_share_temporary_description)
        AppStorageCategory.TagEditTemp -> uiString(Res.string.tv_storage_tags_temporary_description)
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

@Composable
private fun deviceInfoValue(value: String?, loading: Boolean): String {
    return value?.takeIf { it.isNotBlank() } ?: if (loading) uiString(Res.string.tv_settings_reading_progress) else uiString(Res.string.common_unavailable)
}

@Composable
private fun deviceDpResolution(
    snapshot: DeviceInfoSnapshot?,
    density: Float,
    loading: Boolean,
): String {
    val width = snapshot?.resolutionWidthPx
    val height = snapshot?.resolutionHeightPx
    if (width == null || height == null || density <= 0f) return if (loading) uiString(Res.string.tv_settings_reading_progress) else uiString(Res.string.common_unavailable)
    return "${(width / density).toInt()} × ${(height / density).toInt()} dp"
}

@Composable
private fun deviceDensity(value: Float?, loading: Boolean): String {
    return value?.takeIf { it > 0f }?.let { "%.2f".format(it) } ?: if (loading) uiString(Res.string.tv_settings_reading_progress) else uiString(Res.string.common_unavailable)
}
