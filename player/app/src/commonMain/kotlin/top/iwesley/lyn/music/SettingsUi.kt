package top.iwesley.lyn.music

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.iwesley.lyn.music.core.model.AppStorageCategory
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.deriveAppThemePalette
import top.iwesley.lyn.music.core.model.parseThemeHexColor
import top.iwesley.lyn.music.core.model.presetThemeTokens
import top.iwesley.lyn.music.core.model.resolveAppThemeTextPalette
import top.iwesley.lyn.music.feature.settings.SettingsIntent
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.platform.PlatformBackHandler
import top.iwesley.lyn.music.ui.mainShellColors
import kotlin.math.min

@Composable
internal fun SettingsTab(
    state: SettingsState,
    onSettingsIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    var pendingLyricsSourceDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingLyricsSourceDeleteName by rememberSaveable { mutableStateOf("") }
    var pendingLyricsSourceDeleteUsesEditingAction by rememberSaveable { mutableStateOf(false) }
    val settingsFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = shellColors.cardBorder,
        unfocusedBorderColor = shellColors.cardBorder,
        disabledBorderColor = shellColors.cardBorder,
    )
    val activePendingLyricsSourceDeleteId = pendingLyricsSourceDeleteId?.takeIf { pendingId ->
        pendingLyricsSourceDeleteUsesEditingAction || state.sources.any { it.id == pendingId }
    }
    LaunchedEffect(activePendingLyricsSourceDeleteId) {
        if (pendingLyricsSourceDeleteId != null && activePendingLyricsSourceDeleteId == null) {
            pendingLyricsSourceDeleteId = null
            pendingLyricsSourceDeleteName = ""
            pendingLyricsSourceDeleteUsesEditingAction = false
        }
    }
    activePendingLyricsSourceDeleteId?.let { sourceId ->
        AlertDialog(
            onDismissRequest = {
                pendingLyricsSourceDeleteId = null
                pendingLyricsSourceDeleteName = ""
                pendingLyricsSourceDeleteUsesEditingAction = false
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = shellColors.cardContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("删除歌词来源") },
            text = { Text("确认删除“$pendingLyricsSourceDeleteName”吗？删除后将不再参与歌词搜索和匹配。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val useEditingAction = pendingLyricsSourceDeleteUsesEditingAction
                        pendingLyricsSourceDeleteId = null
                        pendingLyricsSourceDeleteName = ""
                        pendingLyricsSourceDeleteUsesEditingAction = false
                        if (useEditingAction) {
                            onSettingsIntent(SettingsIntent.Delete)
                        } else {
                            onSettingsIntent(SettingsIntent.DeleteSource(sourceId))
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingLyricsSourceDeleteId = null
                        pendingLyricsSourceDeleteName = ""
                        pendingLyricsSourceDeleteUsesEditingAction = false
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }
    val selectedThemeTextPalette = remember(state.selectedTheme, state.textPalettePreferences) {
        resolveAppThemeTextPalette(
            themeId = state.selectedTheme,
            preferences = state.textPalettePreferences,
        )
    }
    val themeDisplayOrder = remember {
        listOf(
            AppThemeId.Classic,
            AppThemeId.Ocean,
            AppThemeId.Forest,
            AppThemeId.Sand,
            AppThemeId.Custom,
        )
    }
    var desktopSelectedSectionName by rememberSaveable {
        mutableStateOf(
            defaultDesktopSettingsSection().name
        )
    }
    var mobileDetailSectionName by rememberSaveable { mutableStateOf<String?>(null) }
    val desktopSelectedSection =
        resolveSettingsSection(desktopSelectedSectionName) ?: defaultDesktopSettingsSection()
    val mobileNavigation = toSettingsMobileNavigation(mobileDetailSectionName)
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        state.message?.let { message ->
            LaunchedEffect(message) {
                delay(2_500)
                onSettingsIntent(SettingsIntent.ClearMessage)
            }
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val desktopLayout = maxWidth >= 900.dp
            PlatformBackHandler(
                enabled = !desktopLayout && mobileNavigation is SettingsMobileNavigation.Detail,
                onBack = { mobileDetailSectionName = null },
            )

            if (desktopLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SettingsSectionListPane(
                        selectedSection = desktopSelectedSection,
                        desktop = true,
                        onSectionSelected = { section ->
                            desktopSelectedSectionName = section.name
                        },
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight(),
                    )
                    when (desktopSelectedSection) {
                        SettingsSection.Theme -> ThemeSettingsPane(
                            state = state,
                            selectedThemeTextPalette = selectedThemeTextPalette,
                            themeDisplayOrder = themeDisplayOrder,
                            settingsFieldColors = settingsFieldColors,
                            onSettingsIntent = onSettingsIntent,
                            showHeading = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )

                        SettingsSection.Lyrics -> LyricsSettingsPane(
                            state = state,
                            settingsFieldColors = settingsFieldColors,
                            onSettingsIntent = onSettingsIntent,
                            onRequestDeleteEditingSource = {
                                pendingLyricsSourceDeleteId = state.editingId
                                pendingLyricsSourceDeleteName = state.name
                                pendingLyricsSourceDeleteUsesEditingAction = true
                            },
                            onRequestDeleteListedSource = { sourceId, sourceName ->
                                pendingLyricsSourceDeleteId = sourceId
                                pendingLyricsSourceDeleteName = sourceName
                                pendingLyricsSourceDeleteUsesEditingAction = false
                            },
                            showHeading = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )

                        SettingsSection.Storage -> StorageSettingsPane(
                            state = state,
                            onSettingsIntent = onSettingsIntent,
                            showHeading = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )

                        SettingsSection.AboutDevice -> AboutDeviceSettingsPane(
                            state = state,
                            onSettingsIntent = onSettingsIntent,
                            showHeading = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )

                        SettingsSection.AboutApp -> AboutAppSettingsPane(
                            showHeading = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            } else {
                when (val navigation = mobileNavigation) {
                    SettingsMobileNavigation.List -> {
                        SettingsSectionListPane(
                            selectedSection = null,
                            desktop = false,
                            onSectionSelected = { section ->
                                desktopSelectedSectionName = section.name
                                mobileDetailSectionName = section.name
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    is SettingsMobileNavigation.Detail -> {
                        MobileSettingsDetailLayout(
                            section = navigation.section,
                            onBack = { mobileDetailSectionName = null },
                            modifier = Modifier.fillMaxSize(),
                        ) { detailModifier ->
                            when (navigation.section) {
                                SettingsSection.Theme -> ThemeSettingsPane(
                                    state = state,
                                    selectedThemeTextPalette = selectedThemeTextPalette,
                                    themeDisplayOrder = themeDisplayOrder,
                                    settingsFieldColors = settingsFieldColors,
                                    onSettingsIntent = onSettingsIntent,
                                    showHeading = false,
                                    modifier = detailModifier,
                                )

                                SettingsSection.Lyrics -> LyricsSettingsPane(
                                    state = state,
                                    settingsFieldColors = settingsFieldColors,
                                    onSettingsIntent = onSettingsIntent,
                                    onRequestDeleteEditingSource = {
                                        pendingLyricsSourceDeleteId = state.editingId
                                        pendingLyricsSourceDeleteName = state.name
                                        pendingLyricsSourceDeleteUsesEditingAction = true
                                    },
                                    onRequestDeleteListedSource = { sourceId, sourceName ->
                                        pendingLyricsSourceDeleteId = sourceId
                                        pendingLyricsSourceDeleteName = sourceName
                                        pendingLyricsSourceDeleteUsesEditingAction = false
                                    },
                                    showHeading = false,
                                    modifier = detailModifier,
                                )

                                SettingsSection.Storage -> StorageSettingsPane(
                                    state = state,
                                    onSettingsIntent = onSettingsIntent,
                                    showHeading = false,
                                    modifier = detailModifier,
                                )

                                SettingsSection.AboutDevice -> AboutDeviceSettingsPane(
                                    state = state,
                                    onSettingsIntent = onSettingsIntent,
                                    showHeading = false,
                                    modifier = detailModifier,
                                )

                                SettingsSection.AboutApp -> AboutAppSettingsPane(
                                    showHeading = false,
                                    modifier = detailModifier,
                                )
                            }
                        }
                    }
                }
            }
        }
        state.message?.let { message ->
            ToastCard(
                message = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun SettingsSectionListPane(
    selectedSection: SettingsSection?,
    desktop: Boolean,
    onSectionSelected: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(
            title = "设置",
            subtitle = ""
        )
        SettingsSection.entries.forEach { section ->
            SettingsSectionListItem(
                section = section,
                selected = desktop && selectedSection == section,
                showSubtitle = !desktop,
                onClick = { onSectionSelected(section) },
            )
        }
    }
}

@Composable
private fun SettingsSectionListItem(
    section: SettingsSection,
    selected: Boolean,
    showSubtitle: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    val containerColor = if (selected) shellColors.navContainer else shellColors.cardContainer
    val borderColor = shellColors.cardBorder
    val iconTint =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val titleColor = MaterialTheme.colorScheme.onSurface
    val subtitleColor =
        if (selected) MaterialTheme.colorScheme.onSurfaceVariant else shellColors.secondaryText
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = borderColor,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = settingsSectionIcon(section),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = settingsSectionTitle(section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                )
                if (showSubtitle) {
                    Text(
                        text = settingsSectionSubtitle(section),
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileSettingsDetailLayout(
    section: SettingsSection,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val shellColors = mainShellColors
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailBackButton(onClick = onBack)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = settingsSectionTitle(section),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = settingsSectionSubtitle(section),
                    style = MaterialTheme.typography.bodySmall,
                    color = shellColors.secondaryText,
                )
            }
        }
        content(Modifier.weight(1f))
    }
}

@Composable
private fun ThemeSettingsPane(
    state: SettingsState,
    selectedThemeTextPalette: AppThemeTextPalette,
    themeDisplayOrder: List<AppThemeId>,
    settingsFieldColors: androidx.compose.material3.TextFieldColors,
    onSettingsIntent: (SettingsIntent) -> Unit,
    showHeading: Boolean,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showHeading) {
            SectionTitle(
                title = "主题",
                subtitle = "切换预置主题，自定义主界面颜色，并给每个主题单独选择黑字或白字。",
            )
        }
        MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                themeDisplayOrder.chunked(2).forEach { rowThemes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowThemes.forEach { themeId ->
                            ThemePresetCard(
                                themeId = themeId,
                                selected = state.selectedTheme == themeId,
                                tokens = if (themeId == AppThemeId.Custom) state.customThemeTokens else presetThemeTokens(
                                    themeId
                                ),
                                textPalette = resolveAppThemeTextPalette(
                                    themeId,
                                    state.textPalettePreferences
                                ),
                                onClick = { onSettingsIntent(SettingsIntent.ThemeSelected(themeId)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowThemes.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                ThemeTextPaletteToggle(
                    selectedTheme = state.selectedTheme,
                    selectedPalette = selectedThemeTextPalette,
                    onSelected = {
                        onSettingsIntent(
                            SettingsIntent.ThemeTextPaletteSelected(
                                state.selectedTheme,
                                it
                            )
                        )
                    },
                )
                if (state.selectedTheme == AppThemeId.Custom) {
                    Text(
                        text = "自定义主题",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "输入 3 个基础颜色后保存，主界面会立即切换到新的自定义配色。",
                        color = shellColors.secondaryText,
                    )
                    ThemeColorField(
                        label = "主背景色",
                        value = state.customBackgroundHex,
                        previewArgb = parseThemeHexColor(state.customBackgroundHex)
                            ?: state.customThemeTokens.backgroundArgb,
                        onValueChange = {
                            onSettingsIntent(
                                SettingsIntent.CustomThemeBackgroundChanged(
                                    it
                                )
                            )
                        },
                        colors = settingsFieldColors,
                    )
                    ThemeColorField(
                        label = "主色",
                        value = state.customAccentHex,
                        previewArgb = parseThemeHexColor(state.customAccentHex)
                            ?: state.customThemeTokens.accentArgb,
                        onValueChange = {
                            onSettingsIntent(
                                SettingsIntent.CustomThemeAccentChanged(
                                    it
                                )
                            )
                        },
                        colors = settingsFieldColors,
                    )
                    ThemeColorField(
                        label = "选中 / 落焦色",
                        value = state.customFocusHex,
                        previewArgb = parseThemeHexColor(state.customFocusHex)
                            ?: state.customThemeTokens.focusArgb,
                        onValueChange = { onSettingsIntent(SettingsIntent.CustomThemeFocusChanged(it)) },
                        colors = settingsFieldColors,
                    )
                    state.themeInputError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onSettingsIntent(SettingsIntent.SaveCustomTheme) }) {
                            Text("保存自定义主题")
                        }
                        OutlinedButton(onClick = { onSettingsIntent(SettingsIntent.ResetCustomTheme) }) {
                            Text("重置自定义主题")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsSettingsPane(
    state: SettingsState,
    settingsFieldColors: androidx.compose.material3.TextFieldColors,
    onSettingsIntent: (SettingsIntent) -> Unit,
    onRequestDeleteEditingSource: () -> Unit,
    onRequestDeleteListedSource: (String, String) -> Unit,
    showHeading: Boolean,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showHeading) {
            SectionTitle(
                title = "歌词",
                subtitle = "配置歌词 API 和搜索源。",
            )
        }
        MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("LrcAPI", fontWeight = FontWeight.Bold)
                Text(
                    "专用入口只维护请求地址，保存后会自动生成保留的 Direct 歌词源。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (state.hasLrcApiSource) "已保存到歌词源列表" else "尚未配置",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    if (state.hasLrcApiSource) {
                        MainShellAssistChip(
                            onClick = {},
                            label = { Text("Direct") },
                            leadingIcon = { Icon(Icons.Rounded.CloudSync, null) },
                        )
                    }
                }
                ImeAwareOutlinedTextField(
                    value = state.lrcApiUrl,
                    onValueChange = { onSettingsIntent(SettingsIntent.LrcApiUrlChanged(it)) },
                    label = { Text("LrcAPI 请求地址") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    colors = settingsFieldColors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onSettingsIntent(SettingsIntent.SaveLrcApi) }) {
                        Text("保存 LrcAPI")
                    }
                    OutlinedButton(
                        onClick = { onSettingsIntent(SettingsIntent.ClearLrcApi) },
                        enabled = state.hasLrcApiSource,
                    ) {
                        Text("清除 LrcAPI")
                    }
                }
            }
        }
        MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Musicmatch", fontWeight = FontWeight.Bold)
                Text(
                    "专用入口只维护 usertoken，保存后会自动生成保留的 Workflow 歌词源。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (state.hasMusicmatchSource) "已保存到歌词源列表" else "尚未配置",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    if (state.hasMusicmatchSource) {
                        MainShellAssistChip(
                            onClick = {},
                            label = { Text("Workflow") },
                            leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) },
                        )
                    }
                }
                ImeAwareOutlinedTextField(
                    value = state.musicmatchUserToken,
                    onValueChange = { onSettingsIntent(SettingsIntent.MusicmatchUserTokenChanged(it)) },
                    label = { Text("Musicmatch usertoken") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    colors = settingsFieldColors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onSettingsIntent(SettingsIntent.SaveMusicmatch) }) {
                        Text("保存 Musicmatch")
                    }
                    if (state.hasMusicmatchSource || state.musicmatchUserToken.isNotBlank()) {
                        OutlinedButton(onClick = { onSettingsIntent(SettingsIntent.ClearMusicmatch) }) {
                            Text("清除 Musicmatch")
                        }
                    }
                }
            }
        }
        MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ImeAwareOutlinedTextField(
                    value = state.name,
                    onValueChange = { onSettingsIntent(SettingsIntent.NameChanged(it)) },
                    label = { Text("歌词源名称") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = settingsFieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.urlTemplate,
                    onValueChange = { onSettingsIntent(SettingsIntent.UrlChanged(it)) },
                    label = { Text("URL 模板") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = settingsFieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.queryTemplate,
                    onValueChange = { onSettingsIntent(SettingsIntent.QueryChanged(it)) },
                    label = { Text("Query 模板") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = settingsFieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.headersTemplate,
                    onValueChange = { onSettingsIntent(SettingsIntent.HeadersChanged(it)) },
                    label = { Text("请求头，每行 Key: Value") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = settingsFieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.extractor,
                    onValueChange = { onSettingsIntent(SettingsIntent.ExtractorChanged(it)) },
                    label = { Text("提取规则") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = settingsFieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.priority,
                    onValueChange = { onSettingsIntent(SettingsIntent.PriorityChanged(it)) },
                    label = { Text("优先级") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = settingsFieldColors,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("启用歌词源", fontWeight = FontWeight.Medium)
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = { onSettingsIntent(SettingsIntent.EnabledChanged(it)) },
                        colors = SwitchDefaults.colors(
                            uncheckedThumbColor = MaterialTheme.colorScheme.background,
                            uncheckedBorderColor = shellColors.cardBorder,
                        ),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onSettingsIntent(if (state.editingId != null) SettingsIntent.Save else SettingsIntent.CreateNew) }) {
                        Text(if (state.editingId != null) "保存" else "新建")
                    }
                    OutlinedButton(onClick = {
                        onSettingsIntent(
                            if (state.editingId != null) SettingsIntent.CreateNew else SettingsIntent.SelectConfig(
                                null
                            )
                        )
                    }) {
                        Text(if (state.editingId != null) "新建" else "清空")
                    }
                    if (state.editingId != null) {
                        TextButton(
                            onClick = onRequestDeleteEditingSource,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text("删除")
                        }
                    }
                }
            }
        }
        MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Workflow JSON", fontWeight = FontWeight.Bold)
                Text(
                    "用于新建或编辑多阶段歌词源，支持搜歌 -> 选歌 -> 拉歌词。当前仍直接编辑原始 JSON。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ImeAwareOutlinedTextField(
                    value = state.workflowJsonInput,
                    onValueChange = { onSettingsIntent(SettingsIntent.WorkflowJsonChanged(it)) },
                    label = { Text("Workflow JSON") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp),
                    shape = RoundedCornerShape(18.dp),
                    minLines = 10,
                    maxLines = 18,
                    colors = settingsFieldColors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onSettingsIntent(if (state.editingWorkflowId != null) SettingsIntent.ImportWorkflow else SettingsIntent.CreateNewWorkflow) }) {
                        Text(if (state.editingWorkflowId != null) "保存 Workflow" else "新建 Workflow")
                    }
                    if (state.editingWorkflowId != null || state.workflowJsonInput.isNotBlank()) {
                        OutlinedButton(onClick = {
                            onSettingsIntent(
                                if (state.editingWorkflowId != null) SettingsIntent.CreateNewWorkflow else SettingsIntent.ViewWorkflow(
                                    null
                                )
                            )
                        }) {
                            Text(if (state.editingWorkflowId != null) "新建 Workflow" else "清空编辑")
                        }
                    }
                }
            }
        }

        SectionTitle(
            title = "已有配置",
            subtitle = "Direct 源继续走声明式 extractor；Workflow 源通过 JSON 导入并参与同一优先级链路。"
        )
        if (state.sources.isEmpty()) {
            EmptyStateCard(
                title = "还没有歌词源",
                body = "添加一个可用的 API 后，播放页会按优先级自动请求并缓存歌词。",
            )
        } else {
            state.sources.forEach { source ->
                LyricsSourceCard(
                    source = source,
                    onClick = {
                        when (source) {
                            is LyricsSourceConfig -> {
                                if (top.iwesley.lyn.music.domain.isManagedLrcApiSource(source)) {
                                    onSettingsIntent(SettingsIntent.SelectLrcApi(source))
                                } else {
                                    onSettingsIntent(SettingsIntent.SelectConfig(source))
                                }
                            }

                            is top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig -> {
                                if (top.iwesley.lyn.music.domain.isManagedMusicmatchSource(source)) {
                                    onSettingsIntent(SettingsIntent.SelectMusicmatch(source))
                                } else {
                                    onSettingsIntent(SettingsIntent.ViewWorkflow(source))
                                }
                            }
                        }
                    },
                    onToggleEnabled = {
                        onSettingsIntent(
                            SettingsIntent.ToggleSourceEnabled(
                                source.id,
                                !source.enabled
                            )
                        )
                    },
                    onDelete = {
                        onRequestDeleteListedSource(source.id, source.name)
                    },
                )
            }
        }
    }
}

private fun settingsSectionTitle(section: SettingsSection): String {
    return when (section) {
        SettingsSection.Theme -> "主题"
        SettingsSection.Lyrics -> "歌词"
        SettingsSection.Storage -> "空间管理"
        SettingsSection.AboutDevice -> "关于本机"
        SettingsSection.AboutApp -> "关于应用"
    }
}

private fun settingsSectionSubtitle(section: SettingsSection): String {
    return when (section) {
        SettingsSection.Theme -> "切换预置主题、自定义颜色和文字颜色。"
        SettingsSection.Lyrics -> "配置歌词 API、搜索源和播放缓存。"
        SettingsSection.Storage -> "查看并清理缓存占用。"
        SettingsSection.AboutDevice -> "查看系统、屏幕和硬件信息。"
        SettingsSection.AboutApp -> "查看开发者、项目地址和公众号信息。"
    }
}

private fun settingsSectionIcon(section: SettingsSection): ImageVector {
    return when (section) {
        SettingsSection.Theme -> Icons.Rounded.Settings
        SettingsSection.Lyrics -> Icons.Rounded.GraphicEq
        SettingsSection.Storage -> Icons.Rounded.Storage
        SettingsSection.AboutDevice -> Icons.Rounded.Info
        SettingsSection.AboutApp -> Icons.Rounded.LibraryMusic
    }
}

@Composable
private fun AboutDeviceSettingsPane(
    state: SettingsState,
    onSettingsIntent: (SettingsIntent) -> Unit,
    showHeading: Boolean,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    val snapshot = state.deviceInfoSnapshot
    val summaryTitle = when {
        snapshot?.deviceModel?.isNotBlank() == true -> snapshot.deviceModel
        snapshot?.systemName?.isNotBlank() == true -> snapshot.systemName
        state.deviceInfoLoading -> "正在读取设备信息..."
        else -> "关于本机"
    }.orEmpty()
    val summarySubtitle = snapshot?.let {
        buildString {
            append(it.systemName.ifBlank { "系统" })
            append(" · ")
            append(it.systemVersion.ifBlank { "版本不可用" })
        }
    } ?: if (state.deviceInfoLoading) {
        "正在读取系统、屏幕和硬件信息。"
    } else {
        "查看系统、屏幕和硬件信息。"
    }
    LaunchedEffect(Unit) {
        onSettingsIntent(SettingsIntent.LoadDeviceInfo())
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showHeading) {
            SectionTitle(
                title = "关于本机",
                subtitle = "查看当前设备或主机的系统、屏幕和硬件信息。",
            )
        }
        MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = summaryTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = summarySubtitle,
                            color = shellColors.secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        AboutDeviceInfoCard(title = "系统") {
            AboutDeviceFieldRow(
                label = "系统名称",
                value = deviceInfoDisplayValue(snapshot?.systemName, state.deviceInfoLoading),
            )
            AboutDeviceFieldRow(
                label = "系统版本",
                value = deviceInfoDisplayValue(snapshot?.systemVersion, state.deviceInfoLoading),
            )
            snapshot?.deviceModel?.takeIf { it.isNotBlank() }?.let { model ->
                AboutDeviceFieldRow(
                    label = "设备型号",
                    value = model,
                )
            }
        }
        AboutDeviceInfoCard(title = "显示") {
            AboutDeviceFieldRow(
                label = "分辨率",
                value = deviceInfoDisplayValue(snapshot?.resolution, state.deviceInfoLoading),
            )
        }
        AboutDeviceInfoCard(title = "硬件") {
            AboutDeviceFieldRow(
                label = "CPU",
                value = deviceInfoDisplayValue(snapshot?.cpuDescription, state.deviceInfoLoading),
            )
            AboutDeviceFieldRow(
                label = "内存",
                value = deviceInfoMemoryValue(snapshot?.totalMemoryBytes, state.deviceInfoLoading),
            )
        }
    }
}

@Composable
private fun AboutAppSettingsPane(
    showHeading: Boolean,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showHeading) {
            SectionTitle(
                title = "关于应用",
                subtitle = "查看开发者、项目地址和公众号信息。",
            )
        }
        MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = ABOUT_APP_NAME,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = ABOUT_APP_SUMMARY,
                    color = shellColors.secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        AboutDeviceInfoCard(title = "开发者") {
            AboutAppFieldRow(
                label = "名称",
                value = ABOUT_APP_DEVELOPER,
            )
        }
        AboutDeviceInfoCard(title = "项目地址") {
            AboutAppFieldRow(
                label = "地址",
                value = ABOUT_APP_PROJECT_URL,
                monospace = true,
            )
        }
        AboutDeviceInfoCard(title = "微信公众号") {
            AboutAppFieldRow(
                label = "账号",
                value = ABOUT_APP_WECHAT_ACCOUNT,
            )
            Text(
                text = "公众号图片",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AboutAppQrPlaceholder(
                modifier = Modifier
                    .fillMaxWidth(0.58f)
                    .widthIn(max = 220.dp)
                    .aspectRatio(1f)
                    .align(Alignment.CenterHorizontally),
            )
            Text(
                text = "当前为占位示意图，后续可替换成真实二维码或宣传图。",
                style = MaterialTheme.typography.bodySmall,
                color = shellColors.secondaryText,
            )
        }
    }
}

@Composable
private fun StorageSettingsPane(
    state: SettingsState,
    onSettingsIntent: (SettingsIntent) -> Unit,
    showHeading: Boolean,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    val categoryOrder = remember {
        listOf(
            AppStorageCategory.Artwork,
            AppStorageCategory.PlaybackCache,
            AppStorageCategory.LyricsShareTemp,
            AppStorageCategory.TagEditTemp,
        )
    }
    val categories = remember(state.storageSnapshot) {
        val supported = state.storageSnapshot?.categories.orEmpty().associateBy { it.category }
        categoryOrder.mapNotNull { supported[it] }
    }
    val storagePaths = remember(state.storageSnapshot) {
        state.storageSnapshot?.paths.orEmpty().filter { it.isNotBlank() }
    }
    LaunchedEffect(Unit) {
        onSettingsIntent(SettingsIntent.LoadStorageUsage())
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showHeading) {
            SectionTitle(
                title = "空间管理",
                subtitle = "查看当前可清理缓存，并按分类手动清除。",
            )
        }
        MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("当前可清理缓存", fontWeight = FontWeight.Bold)
                        Text(
                            text = state.storageSnapshot?.let { formatStorageSize(it.totalSizeBytes) }
                                ?: if (state.storageLoading) "正在统计缓存..." else "暂未读取",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            "不含数据库、设置和凭据文件。",
                            color = shellColors.secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (storagePaths.isNotEmpty()) {
                            Text(
                                "当前空间路径",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = storagePaths.joinToString("\n"),
                                color = shellColors.secondaryText,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { onSettingsIntent(SettingsIntent.LoadStorageUsage(force = true)) },
                        enabled = !state.storageLoading && state.clearingStorageCategory == null,
                    ) {
                        Icon(Icons.Rounded.Sync, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.storageLoading) "刷新中" else "刷新")
                    }
                }
            }
        }
        if (categories.isEmpty()) {
            EmptyStateCard(
                title = if (state.storageLoading) "正在统计缓存" else "没有可管理缓存",
                body = if (state.storageLoading) {
                    "正在读取当前平台支持的缓存目录。"
                } else {
                    "当前平台还没有暴露可清理的缓存分类。"
                },
            )
        } else {
            categories.forEach { usage ->
                StorageCategoryCard(
                    category = usage.category,
                    sizeBytes = usage.sizeBytes,
                    clearing = state.clearingStorageCategory == usage.category,
                    actionEnabled = usage.sizeBytes > 0L &&
                            !state.storageLoading &&
                            state.clearingStorageCategory != usage.category,
                    onClear = { onSettingsIntent(SettingsIntent.ClearStorageCategory(usage.category)) },
                )
            }
        }
    }
}

@Composable
private fun StorageCategoryCard(
    category: AppStorageCategory,
    sizeBytes: Long,
    clearing: Boolean,
    actionEnabled: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MainShellElevatedCard(
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = storageCategoryTitle(category),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = storageCategoryDescription(category),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatStorageSize(sizeBytes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = onClear,
                    enabled = actionEnabled,
                ) {
                    Text(if (clearing) "清理中..." else "清除")
                }
            }
        }
    }
}

@Composable
private fun AboutDeviceInfoCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    MainShellElevatedCard(
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

@Composable
private fun AboutDeviceFieldRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AboutAppFieldRow(
    label: String,
    value: String,
    monospace: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = if (monospace) FontFamily.Monospace else null,
        )
    }
}

@Composable
private fun AboutAppQrPlaceholder(
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, shellColors.cardBorder, RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cells = 21
            val cellSize = min(size.width, size.height) / cells.toFloat()
            for (row in 0 until cells) {
                for (column in 0 until cells) {
                    if (!isAboutAppQrModuleFilled(row, column, cells)) continue
                    drawRect(
                        color = Color(0xFF111111),
                        topLeft = Offset(column * cellSize, row * cellSize),
                        size = Size(cellSize, cellSize),
                    )
                }
            }
        }
    }
}

private fun storageCategoryTitle(category: AppStorageCategory): String {
    return when (category) {
        AppStorageCategory.Artwork -> "封面缓存"
        AppStorageCategory.PlaybackCache -> "播放缓存"
        AppStorageCategory.LyricsShareTemp -> "歌词分享临时文件"
        AppStorageCategory.TagEditTemp -> "标签编辑临时文件"
    }
}

private fun storageCategoryDescription(category: AppStorageCategory): String {
    return when (category) {
        AppStorageCategory.Artwork -> "包含下载封面，以及扫描或标签编辑时生成的本地封面文件。"
        AppStorageCategory.PlaybackCache -> "包含 SMB 播放时落到本地的临时音频缓存。"
        AppStorageCategory.LyricsShareTemp -> "包含生成歌词分享图时写入的临时图片。"
        AppStorageCategory.TagEditTemp -> "包含编辑标签封面时写入的临时中转文件。"
    }
}

private fun formatStorageSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = sizeBytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    val formatted = when {
        unitIndex == 0 -> value.toLong().toString()
        value >= 100 -> value.toInt().toString()
        value >= 10 -> (value * 10).toInt() / 10.0
        else -> (value * 100).toInt() / 100.0
    }
    return "$formatted ${units[unitIndex]}"
}

private fun isAboutAppQrModuleFilled(
    row: Int,
    column: Int,
    cells: Int,
): Boolean {
    if (
        isAboutAppQrFinderModule(row, column, 0, 0) ||
        isAboutAppQrFinderModule(row, column, cells - 7, 0) ||
        isAboutAppQrFinderModule(row, column, 0, cells - 7)
    ) {
        return true
    }
    if (
        isAboutAppQrReservedArea(row, column, 0, 0) ||
        isAboutAppQrReservedArea(row, column, cells - 7, 0) ||
        isAboutAppQrReservedArea(row, column, 0, cells - 7)
    ) {
        return false
    }
    if (row == 6 || column == 6) {
        return (row + column) % 2 == 0
    }
    return ((row * 3 + column * 5 + row * column) % 7 == 0) || ((row + column) % 11 == 0)
}

private fun isAboutAppQrFinderModule(
    row: Int,
    column: Int,
    left: Int,
    top: Int,
): Boolean {
    val localRow = row - top
    val localColumn = column - left
    if (localRow !in 0..6 || localColumn !in 0..6) return false
    return localRow == 0 || localRow == 6 || localColumn == 0 || localColumn == 6 ||
            (localRow in 2..4 && localColumn in 2..4)
}

private fun isAboutAppQrReservedArea(
    row: Int,
    column: Int,
    left: Int,
    top: Int,
): Boolean {
    return row in (top - 1)..(top + 7) && column in (left - 1)..(left + 7)
}

private fun deviceInfoDisplayValue(value: String?, loading: Boolean): String {
    return when {
        value != null && value.isNotBlank() -> value
        loading -> "正在读取..."
        else -> "不可用"
    }
}

private fun deviceInfoMemoryValue(totalMemoryBytes: Long?, loading: Boolean): String {
    return totalMemoryBytes?.takeIf { it > 0L }?.let(::formatStorageSize)
        ?: if (loading) "正在读取..." else "不可用"
}

private const val ABOUT_APP_NAME = "LynMusic"
private const val ABOUT_APP_SUMMARY =
    "以下开发者、项目地址和公众号信息为占位演示，可后续替换成真实内容。"
private const val ABOUT_APP_DEVELOPER = "假装是开发者小林"
private const val ABOUT_APP_PROJECT_URL = "https://example.com/lynmusic-demo"
private const val ABOUT_APP_WECHAT_ACCOUNT = "假装有个公众号"

@Composable
private fun ThemePresetCard(
    themeId: AppThemeId,
    selected: Boolean,
    tokens: AppThemeTokens,
    textPalette: AppThemeTextPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    val previewPalette = remember(tokens, textPalette) {
        deriveAppThemePalette(
            tokens = tokens,
            textPalette = textPalette,
        )
    }
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(previewPalette.cardContainerArgb),
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) shellColors.selectedBorder else Color(previewPalette.cardBorderArgb),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = themeDisplayName(themeId),
                fontWeight = FontWeight.Bold,
                color = Color(previewPalette.onSurfaceArgb),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeSwatch(tokens.backgroundArgb, Modifier.weight(1f))
                ThemeSwatch(tokens.accentArgb, Modifier.weight(1f))
                ThemeSwatch(tokens.focusArgb, Modifier.weight(1f))
            }
            Text(
                text = buildString {
                    append(if (themeId == AppThemeId.Custom) "自定义主界面颜色" else "预置主题")
                    append(" · ")
                    append(themeTextPaletteLabel(textPalette))
                },
                color = Color(previewPalette.secondaryTextArgb),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ThemeTextPaletteToggle(
    selectedTheme: AppThemeId,
    selectedPalette: AppThemeTextPalette,
    onSelected: (AppThemeTextPalette) -> Unit,
) {
    val shellColors = mainShellColors
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "文字颜色",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${themeDisplayName(selectedTheme)}主题单独保存黑字或白字，不会影响播放界面。",
            color = shellColors.secondaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(AppThemeTextPalette.White, AppThemeTextPalette.Black).forEach { palette ->
                val selected = selectedPalette == palette
                if (selected) {
                    Button(
                        onClick = { onSelected(palette) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(themeTextPaletteLabel(palette))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelected(palette) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(themeTextPaletteLabel(palette))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeColorField(
    label: String,
    value: String,
    previewArgb: Int,
    onValueChange: (String) -> Unit,
    colors: androidx.compose.material3.TextFieldColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeSwatch(
            argb = previewArgb,
            modifier = Modifier.size(42.dp),
        )
        ImeAwareOutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            singleLine = true,
            colors = colors,
        )
    }
}

@Composable
private fun ThemeSwatch(
    argb: Int,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    Box(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(argb))
            .border(1.dp, shellColors.cardBorder, RoundedCornerShape(12.dp)),
    )
}

private fun themeDisplayName(themeId: AppThemeId): String {
    return when (themeId) {
        AppThemeId.Classic -> "经典黑"
        AppThemeId.Forest -> "森林"
        AppThemeId.Ocean -> "经典白"
        AppThemeId.Sand -> "砂岩"
        AppThemeId.Custom -> "自定义"
    }
}

private fun themeTextPaletteLabel(textPalette: AppThemeTextPalette): String {
    return when (textPalette) {
        AppThemeTextPalette.White -> "白字"
        AppThemeTextPalette.Black -> "黑字"
    }
}
