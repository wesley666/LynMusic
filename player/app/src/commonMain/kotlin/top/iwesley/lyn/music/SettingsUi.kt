package top.iwesley.lyn.music

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import lynmusic.player.app.generated.resources.Res
import lynmusic.player.app.generated.resources.about_app_wechat_qr
import org.jetbrains.compose.resources.imageResource
import top.iwesley.lyn.music.core.model.AppStorageCategory
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.BuildMetadata
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.deriveAppThemePalette
import top.iwesley.lyn.music.core.model.formatThemeHexColor
import top.iwesley.lyn.music.core.model.presetThemeTokens
import top.iwesley.lyn.music.core.model.resolveAppThemeTextPalette
import top.iwesley.lyn.music.feature.settings.CustomThemeColorRole
import top.iwesley.lyn.music.feature.settings.SettingsIntent
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.platform.PlatformBackHandler
import top.iwesley.lyn.music.platform.lyricsSharePreviewFontFamily
import top.iwesley.lyn.music.ui.mainShellColors
import kotlin.math.roundToInt

@Composable
internal fun SettingsTab(
    platform: PlatformDescriptor,
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
            AppThemeId.Custom,
        )
    }
    val availableSections = remember(platform.name) {
        settingsSectionsForPlatform(platform)
    }
    val defaultSection = remember(platform.name) {
        defaultSettingsSection(platform)
    }
    var desktopSelectedSectionName by rememberSaveable(platform.name) {
        mutableStateOf(
            defaultSection.name
        )
    }
    var mobileDetailSectionName by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(availableSections, desktopSelectedSectionName) {
        if (availableSections.none { it.name == desktopSelectedSectionName }) {
            desktopSelectedSectionName = defaultSection.name
        }
    }
    LaunchedEffect(availableSections, mobileDetailSectionName) {
        if (mobileDetailSectionName != null && availableSections.none { it.name == mobileDetailSectionName }) {
            mobileDetailSectionName = null
        }
    }
    val desktopSelectedSection =
        resolveSettingsSection(desktopSelectedSectionName)?.takeIf { it in availableSections } ?: defaultSection
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
            val density = LocalDensity.current
            val layoutProfile = buildLayoutProfile(
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                platform = currentPlatformDescriptor,
                density = density,
            )
            val desktopLayout = layoutProfile.isExpandedLayout
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
                        sections = availableSections,
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
                        SettingsSection.General -> GeneralSettingsPane(
                            state = state,
                            onSettingsIntent = onSettingsIntent,
                            showHeading = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )

                        SettingsSection.Theme -> ThemeSettingsPane(
                            state = state,
                            selectedThemeTextPalette = selectedThemeTextPalette,
                            themeDisplayOrder = themeDisplayOrder,
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
                            platformName = platform.name,
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
                            sections = availableSections,
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
                                SettingsSection.General -> GeneralSettingsPane(
                                    state = state,
                                    onSettingsIntent = onSettingsIntent,
                                    showHeading = false,
                                    modifier = detailModifier,
                                )

                                SettingsSection.Theme -> ThemeSettingsPane(
                                    state = state,
                                    selectedThemeTextPalette = selectedThemeTextPalette,
                                    themeDisplayOrder = themeDisplayOrder,
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
                                    platformName = platform.name,
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
    sections: List<SettingsSection>,
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
        sections.forEach { section ->
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
private fun GeneralSettingsPane(
    state: SettingsState,
    onSettingsIntent: (SettingsIntent) -> Unit,
    showHeading: Boolean,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    val isMobilePlatform = currentPlatformDescriptor.isMobilePlatform()
    val showAppDisplayScaleSetting =
        currentPlatformDescriptor.capabilities.supportsAppDisplayScaleAdjustment
    val showCompactPlayerLyricsSetting = isMobilePlatform
    val showNavidromeAudioQualitySetting = isMobilePlatform
    val showDesktopVlcSettings = currentPlatformDescriptor.isPCPlatform()
    val manualPath = state.desktopVlcManualPath?.takeIf { it.isNotBlank() }
    val autoDetectedPath = state.desktopVlcAutoDetectedPath?.takeIf { it.isNotBlank() }
    val effectivePath = state.desktopVlcEffectivePath?.takeIf { it.isNotBlank() }
    val currentPath = effectivePath ?: "未自动识别到 VLC 路径"
    val currentSource = if (manualPath != null) "手动指定" else "自动识别"
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showHeading) {
            SectionTitle(
                title = "通用",
                subtitle = "管理播放页歌词显示和平台相关通用配置。",
            )
        }
        MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "启动应用后自动播放",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "启动时恢复上次播放队列和进度，并自动开始播放。",
                        style = MaterialTheme.typography.bodySmall,
                        color = shellColors.secondaryText,
                    )
                }
                Switch(
                    checked = state.autoPlayOnStartup,
                    onCheckedChange = { enabled ->
                        onSettingsIntent(SettingsIntent.AutoPlayOnStartupChanged(enabled))
                    },
                    colors = SwitchDefaults.colors(),
                )
            }
        }
        if (showDesktopVlcSettings) {
            MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "VLC 播放器路径",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "当前生效路径会在下次启动时用于初始化桌面播放器。",
                        style = MaterialTheme.typography.bodySmall,
                        color = shellColors.secondaryText,
                    )
                    AboutAppFieldRow(
                        label = "当前路径",
                        value = currentPath,
                        monospace = true,
                    )
                    AboutDeviceFieldRow(
                        label = "来源",
                        value = currentSource,
                    )
                    if (manualPath != null && autoDetectedPath != null) {
                        AboutAppFieldRow(
                            label = "自动识别路径",
                            value = autoDetectedPath,
                            monospace = true,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { onSettingsIntent(SettingsIntent.PickDesktopVlcPath) },
                        ) {
                            Text("选择 VLC 路径")
                        }
                        if (manualPath != null) {
                            OutlinedButton(
                                onClick = { onSettingsIntent(SettingsIntent.ClearDesktopVlcManualPath) },
                            ) {
                                Text("恢复自动识别")
                            }
                        }
                    }
                    Text(
                        text = "保存后将在下次启动时生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = shellColors.secondaryText,
                    )
                }
            }
        }
        if (showCompactPlayerLyricsSetting) {
            MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "显示播放页歌词",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "仅在移动端非分栏播放页显示黑胶下方歌词。",
                            style = MaterialTheme.typography.bodySmall,
                            color = shellColors.secondaryText,
                        )
                    }
                    Switch(
                        checked = state.showCompactPlayerLyrics,
                        onCheckedChange = { enabled ->
                            onSettingsIntent(SettingsIntent.ShowCompactPlayerLyricsChanged(enabled))
                        },
                        colors = SwitchDefaults.colors(),
                    )
                }
            }
        }
        if (showNavidromeAudioQualitySetting) {
            MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Navidrome 播放音质",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "仅影响 Navidrome 曲目，设置会在下一次加载歌曲时生效。",
                            style = MaterialTheme.typography.bodySmall,
                            color = shellColors.secondaryText,
                        )
                    }
                    NavidromeAudioQualitySettingRow(
                        title = "WiFi",
                        selected = state.navidromeWifiAudioQuality,
                        onSelected = { quality ->
                            onSettingsIntent(SettingsIntent.NavidromeWifiAudioQualityChanged(quality))
                        },
                    )
                    NavidromeAudioQualitySettingRow(
                        title = "移动网络",
                        selected = state.navidromeMobileAudioQuality,
                        onSelected = { quality ->
                            onSettingsIntent(SettingsIntent.NavidromeMobileAudioQualityChanged(quality))
                        },
                    )
                }
            }
        }
        if (showAppDisplayScaleSetting) {
            MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "显示大小",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "仅影响应用内界面大小，不修改系统显示大小。",
                            style = MaterialTheme.typography.bodySmall,
                            color = shellColors.secondaryText,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppDisplayScalePreset.entries.forEach { preset ->
                            val selected = state.appDisplayScalePreset == preset
                            if (selected) {
                                Button(
                                    onClick = {
                                        onSettingsIntent(SettingsIntent.AppDisplayScalePresetChanged(preset))
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(appDisplayScalePresetLabel(preset))
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        onSettingsIntent(SettingsIntent.AppDisplayScalePresetChanged(preset))
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(appDisplayScalePresetLabel(preset))
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
private fun NavidromeAudioQualitySettingRow(
    title: String,
    selected: NavidromeAudioQuality,
    onSelected: (NavidromeAudioQuality) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavidromeAudioQuality.entries.forEach { quality ->
                val isSelected = quality == selected
                if (isSelected) {
                    Button(
                        onClick = { onSelected(quality) },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                    ) {
                        Text(
                            text = navidromeAudioQualityLabel(quality),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelected(quality) },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                    ) {
                        Text(
                            text = navidromeAudioQualityLabel(quality),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSettingsPane(
    state: SettingsState,
    selectedThemeTextPalette: AppThemeTextPalette,
    themeDisplayOrder: List<AppThemeId>,
    onSettingsIntent: (SettingsIntent) -> Unit,
    showHeading: Boolean,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    var activeColorRole by remember { mutableStateOf<CustomThemeColorRole?>(null) }
    LaunchedEffect(state.selectedTheme) {
        if (state.selectedTheme != AppThemeId.Custom) {
            activeColorRole = null
        }
    }
    activeColorRole?.let { role ->
        ThemeColorPickerDialog(
            label = customThemeColorLabel(role),
            initialArgb = state.customThemeTokens.colorFor(role),
            onDismiss = { activeColorRole = null },
            onConfirm = { argb ->
                activeColorRole = null
                onSettingsIntent(SettingsIntent.CustomThemeColorUpdated(role, argb))
            },
        )
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
                                    themeId,
                                ),
                                textPalette = resolveAppThemeTextPalette(
                                    themeId,
                                    state.textPalettePreferences,
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
                                it,
                            ),
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
                        text = "点击颜色项选择并立即应用，页面会保留只读色值作为参考。",
                        color = shellColors.secondaryText,
                    )
                    ThemeColorPickerRow(
                        label = "主背景色",
                        argb = state.customThemeTokens.backgroundArgb,
                        onClick = { activeColorRole = CustomThemeColorRole.Background },
                    )
                    ThemeColorPickerRow(
                        label = "主色",
                        argb = state.customThemeTokens.accentArgb,
                        onClick = { activeColorRole = CustomThemeColorRole.Accent },
                    )
                    ThemeColorPickerRow(
                        label = "选中 / 落焦色",
                        argb = state.customThemeTokens.focusArgb,
                        onClick = { activeColorRole = CustomThemeColorRole.Focus },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
    LaunchedEffect(state.supportsLyricsShareFontImport) {
        if (state.supportsLyricsShareFontImport) {
            onSettingsIntent(SettingsIntent.LoadLyricsShareImportedFonts)
        }
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
                title = "歌词",
                subtitle = "配置歌词 API 和搜索源。",
            )
        }
        if (state.supportsLyricsShareFontImport) {
            LyricsShareFontImportCard(
                state = state,
                onSettingsIntent = onSettingsIntent,
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

@Composable
private fun LyricsShareFontImportCard(
    state: SettingsState,
    onSettingsIntent: (SettingsIntent) -> Unit,
) {
    MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("歌词分享字体", fontWeight = FontWeight.Bold)
            Text(
                "导入 .ttf / .otf 字体后，可在歌词分享页里直接选择并参与最终出图。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.importedLyricsShareFonts.isEmpty()) {
                        "尚未导入字体"
                    } else {
                        "已导入 ${state.importedLyricsShareFonts.size} 个字体"
                    },
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
                Button(
                    onClick = { onSettingsIntent(SettingsIntent.ImportLyricsShareFont) },
                    enabled = !state.importingLyricsShareFont && state.deletingLyricsShareFontKey == null,
                ) {
                    Text(if (state.importingLyricsShareFont) "导入中..." else "导入字体")
                }
            }
            when {
                state.lyricsShareFontsLoading -> {
                    Text("正在读取已导入字体...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                state.importedLyricsShareFonts.isEmpty() -> {
                    Text("当前没有已导入字体。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.importedLyricsShareFonts.forEach { option ->
                            LyricsShareImportedFontRow(
                                option = option,
                                deleting = state.deletingLyricsShareFontKey == option.fontKey,
                                onDelete = {
                                    onSettingsIntent(SettingsIntent.DeleteLyricsShareImportedFont(option.fontKey))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsShareImportedFontRow(
    option: LyricsShareFontOption,
    deleting: Boolean,
    onDelete: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(1.dp, mainShellColors.cardBorder),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(14.dp),
    ) {
        val compactLayout = maxWidth < 420.dp
        if (compactLayout) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LyricsShareImportedFontName(
                        displayName = option.displayName,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onDelete,
                        enabled = !deleting,
                        modifier = Modifier.size(40.dp),
                    ) {
                        if (deleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "删除字体",
                            )
                        }
                    }
                }
                LyricsShareImportedFontPreview(
                    option = option,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LyricsShareImportedFontPreview(
                    option = option,
                    modifier = Modifier.size(width = 144.dp, height = 56.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LyricsShareImportedFontName(option.displayName)
                }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !deleting,
                ) {
                    Text(if (deleting) "删除中..." else "删除")
                }
            }
        }
    }
}

@Composable
private fun LyricsShareImportedFontPreview(
    option: LyricsShareFontOption,
    modifier: Modifier = Modifier,
) {
    val previewFontFamily = lyricsSharePreviewFontFamily(
        fontKey = option.fontKey,
        displayName = option.displayName,
        fontFilePath = option.fontFilePath,
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = option.previewText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = previewFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LyricsShareImportedFontName(
    displayName: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = displayName,
        modifier = modifier,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
    )
}

private fun settingsSectionTitle(section: SettingsSection): String {
    return when (section) {
        SettingsSection.General -> "通用"
        SettingsSection.Theme -> "主题"
        SettingsSection.Lyrics -> "歌词"
        SettingsSection.Storage -> "空间管理"
        SettingsSection.AboutDevice -> "关于本机"
        SettingsSection.AboutApp -> "关于应用"
    }
}

private fun settingsSectionSubtitle(section: SettingsSection): String {
    return when (section) {
        SettingsSection.General -> "管理播放页歌词显示和平台通用配置。"
        SettingsSection.Theme -> "切换预置主题、自定义颜色和文字颜色。"
        SettingsSection.Lyrics -> "配置歌词 API、搜索源和播放缓存。"
        SettingsSection.Storage -> "查看并清理缓存占用。"
        SettingsSection.AboutDevice -> "查看系统、屏幕和硬件信息。"
        SettingsSection.AboutApp -> "查看开发者、项目地址和公众号信息。"
    }
}

private fun settingsSectionIcon(section: SettingsSection): ImageVector {
    return when (section) {
        SettingsSection.General -> Icons.Rounded.Tune
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
    val localDensity = LocalDensity.current
    val showsAndroidDisplayMetrics =
        currentPlatformDescriptor.capabilities.supportsAppDisplayScaleAdjustment
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
            if (showsAndroidDisplayMetrics) {
                AboutDeviceFieldRow(
                    label = "应用 DP 分辨率",
                    value = deviceInfoDpResolutionValue(
                        widthPx = snapshot?.resolutionWidthPx,
                        heightPx = snapshot?.resolutionHeightPx,
                        density = localDensity.density,
                        loading = state.deviceInfoLoading,
                    ),
                )
                AboutDeviceFieldRow(
                    label = "系统 DP 分辨率",
                    value = deviceInfoDpResolutionValue(
                        widthPx = snapshot?.resolutionWidthPx,
                        heightPx = snapshot?.resolutionHeightPx,
                        density = snapshot?.systemDensityScale,
                        loading = state.deviceInfoLoading,
                    ),
                )
                AboutDeviceFieldRow(
                    label = "应用像素密度",
                    value = deviceInfoDensityValue(
                        density = localDensity.density,
                        loading = false,
                    ),
                )
                AboutDeviceFieldRow(
                    label = "系统像素密度",
                    value = deviceInfoDensityValue(
                        density = snapshot?.systemDensityScale,
                        loading = state.deviceInfoLoading,
                    ),
                )
            } else {
                AboutDeviceFieldRow(
                    label = "DP 分辨率",
                    value = deviceInfoDpResolutionValue(
                        widthPx = snapshot?.resolutionWidthPx,
                        heightPx = snapshot?.resolutionHeightPx,
                        density = localDensity.density,
                        loading = state.deviceInfoLoading,
                    ),
                )
                AboutDeviceFieldRow(
                    label = "像素密度",
                    value = deviceInfoDensityValue(
                        density = localDensity.density,
                        loading = false,
                    ),
                )
            }
            AboutDeviceFieldRow(
                label = "字体缩放",
                value = deviceInfoFontScaleValue(localDensity.fontScale),
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
    platformName: String,
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
//                Text(
//                    text = ABOUT_APP_SUMMARY,
//                    color = shellColors.secondaryText,
//                    style = MaterialTheme.typography.bodySmall,
//                )
            }
        }
        AboutDeviceInfoCard(title = "基本信息") {
            AboutAppFieldRow(
                label = "版本号",
                value = BuildMetadata.versionDisplay,
                monospace = true,
            )
            AboutAppFieldRow(
                label = "平台名称",
                value = platformName,
            )
            AboutAppFieldRow(
                label = "编译时间",
                value = BuildMetadata.buildTimeUtc,
                monospace = true,
            )
        }
        AboutDeviceInfoCard(title = "开发者") {
            AboutAppFieldRow(
                label = "名称",
                value = ABOUT_APP_DEVELOPER,
            )
        }
        AboutDeviceInfoCard(title = "项目地址") {
            AboutAppLinkFieldRow(
                label = "地址",
                value = ABOUT_APP_PROJECT_URL,
                url = ABOUT_APP_PROJECT_URL,
            )
        }
        AboutDeviceInfoCard(title = "微信公众号") {
            AboutAppFieldRow(
                label = "账号",
                value = ABOUT_APP_WECHAT_ACCOUNT,
            )
            Text(
                text = "公众号二维码",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AboutAppQrImage(
                modifier = Modifier
                    .fillMaxWidth(0.58f)
                    .widthIn(max = 220.dp)
                    .aspectRatio(1f)
                    .align(Alignment.CenterHorizontally),
            )
            Text(
                text = "扫码关注公众号，获取更新和交流信息。",
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
private fun AboutAppLinkFieldRow(
    label: String,
    value: String,
    url: String,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
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
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable { uriHandler.openUri(url) },
        )
    }
}

@Composable
private fun AboutAppQrImage(
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, shellColors.cardBorder, RoundedCornerShape(22.dp))
            .padding(12.dp),
    ) {
        Image(
            bitmap = imageResource(Res.drawable.about_app_wechat_qr),
            contentDescription = "公众号二维码",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
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

private fun appDisplayScalePresetLabel(preset: AppDisplayScalePreset): String {
    return when (preset) {
        AppDisplayScalePreset.Compact -> "紧凑"
        AppDisplayScalePreset.Default -> "默认"
        AppDisplayScalePreset.Large -> "大号"
    }
}

internal fun navidromeAudioQualityLabel(quality: NavidromeAudioQuality): String {
    return when (quality) {
        NavidromeAudioQuality.Original -> "原始"
        NavidromeAudioQuality.Kbps320 -> "320kbps"
        NavidromeAudioQuality.Kbps192 -> "192kbps"
        NavidromeAudioQuality.Kbps128 -> "128kbps"
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

private fun deviceInfoDisplayValue(value: String?, loading: Boolean): String {
    return when {
        value != null && value.isNotBlank() -> value
        loading -> "正在读取..."
        else -> "不可用"
    }
}

internal fun deviceInfoDpResolutionValue(
    widthPx: Int?,
    heightPx: Int?,
    density: Float?,
    loading: Boolean,
): String {
    val resolvedWidth = widthPx?.takeIf { it > 0 }
    val resolvedHeight = heightPx?.takeIf { it > 0 }
    if (resolvedWidth == null || resolvedHeight == null) {
        return if (loading) "正在读取..." else "不可用"
    }
    val resolvedDensity = density?.takeIf { it.isFinite() && it > 0f } ?: return "不可用"
    val widthDp = (resolvedWidth / resolvedDensity).roundToInt()
    val heightDp = (resolvedHeight / resolvedDensity).roundToInt()
    return "$widthDp × $heightDp dp"
}

internal fun deviceInfoDensityValue(
    density: Float?,
    loading: Boolean,
): String {
    val resolvedDensity = density?.takeIf { it.isFinite() && it > 0f }
        ?: return if (loading) "正在读取..." else "不可用"
    return formatDeviceInfoDecimal(resolvedDensity)?.let { "$it px/dp" } ?: "不可用"
}

internal fun deviceInfoFontScaleValue(fontScale: Float): String {
    return formatDeviceInfoDecimal(fontScale)?.let { "${it}x" } ?: "不可用"
}

internal fun formatDeviceInfoDecimal(value: Float): String? {
    if (!value.isFinite() || value <= 0f) return null
    val scaled = (value * 100).roundToInt()
    val integerPart = scaled / 100
    val fractionalPart = scaled % 100
    return when {
        fractionalPart == 0 -> integerPart.toString()
        fractionalPart % 10 == 0 -> "$integerPart.${fractionalPart / 10}"
        else -> "$integerPart.${fractionalPart.toString().padStart(2, '0')}"
    }
}

private fun deviceInfoMemoryValue(totalMemoryBytes: Long?, loading: Boolean): String {
    return totalMemoryBytes?.takeIf { it > 0L }?.let(::formatStorageSize)
        ?: if (loading) "正在读取..." else "不可用"
}

private const val ABOUT_APP_NAME = "LynMusic"
private const val ABOUT_APP_SUMMARY =
    "以下为开发者、项目地址和公众号信息。"
private const val ABOUT_APP_DEVELOPER = "锋风"
private const val ABOUT_APP_PROJECT_URL = "https://github.com/wesley666/LynMusic"
private const val ABOUT_APP_WECHAT_ACCOUNT = "锋风"

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
private fun ThemeColorPickerRow(
    label: String,
    argb: Int,
    onClick: () -> Unit,
) {
    val shellColors = mainShellColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(shellColors.navContainer.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .border(1.dp, shellColors.cardBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeSwatch(
            argb = argb,
            modifier = Modifier.size(42.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = formatThemeHexColor(argb),
                color = shellColors.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = "选择颜色",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
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

private fun AppThemeTokens.colorFor(role: CustomThemeColorRole): Int {
    return when (role) {
        CustomThemeColorRole.Background -> backgroundArgb
        CustomThemeColorRole.Accent -> accentArgb
        CustomThemeColorRole.Focus -> focusArgb
    }
}

private fun customThemeColorLabel(role: CustomThemeColorRole): String {
    return when (role) {
        CustomThemeColorRole.Background -> "主背景色"
        CustomThemeColorRole.Accent -> "主色"
        CustomThemeColorRole.Focus -> "选中 / 落焦色"
    }
}
