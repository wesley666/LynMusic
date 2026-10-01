package top.iwesley.lyn.music

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.uiPlural
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.UiText

import top.iwesley.lyn.music.core.model.AppLanguage

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
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
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
import lynmusic.player.app.generated.resources.Res as PlayerRes
import lynmusic.player.app.generated.resources.about_app_wechat_qr
import org.jetbrains.compose.resources.imageResource
import top.iwesley.lyn.music.core.model.AppStorageCategory
import top.iwesley.lyn.music.core.model.AppDataLocationChangeMode
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.BuildMetadata
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.LynMusicUpdateLinks
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlayerArtworkStyle
import top.iwesley.lyn.music.core.model.deriveAppThemePalette
import top.iwesley.lyn.music.core.model.formatThemeHexColor
import top.iwesley.lyn.music.core.model.presetThemeTokens
import top.iwesley.lyn.music.core.model.resolveAppThemeTextPalette
import top.iwesley.lyn.music.feature.settings.CustomThemeColorRole
import top.iwesley.lyn.music.feature.settings.AppUpdateUiStatus
import top.iwesley.lyn.music.feature.settings.SettingsIntent
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.feature.settings.toAppUpdateUiModel
import top.iwesley.lyn.music.platform.PlatformBackHandler
import top.iwesley.lyn.music.platform.lyricsSharePreviewFontFamily
import top.iwesley.lyn.music.ui.mainShellColors
import kotlin.math.roundToInt

@Composable
internal fun SettingsTab(
    platform: PlatformDescriptor,
    state: SettingsState,
    onSettingsIntent: (SettingsIntent) -> Unit,
    onOpenBackgroundRunSettings: () -> Unit,
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
            title = { Text(uiString(Res.string.lyrics_source_delete_title)) },
            text = { Text(uiString(Res.string.lyrics_source_delete_confirmation, pendingLyricsSourceDeleteName)) },
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
                    Text(uiString(Res.string.common_delete))
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
                    Text(uiString(Res.string.common_cancel))
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
    val mobileNavigation = when (val navigation = toSettingsMobileNavigation(mobileDetailSectionName)) {
        SettingsMobileNavigation.List -> navigation
        is SettingsMobileNavigation.Detail ->
            if (navigation.section in availableSections) navigation else SettingsMobileNavigation.List
    }
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
                        showAppUpdateBadge = state.appUpdateHasNewVersion == true,
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
                            supportsCustomDataLocation = shouldShowCustomDataLocation(platform),
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
                            state = state,
                            onSettingsIntent = onSettingsIntent,
                            showHeading = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )

                        SettingsSection.Help -> HelpSettingsPane(
                            onOpenBackgroundRunSettings = onOpenBackgroundRunSettings,
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
                            showAppUpdateBadge = state.appUpdateHasNewVersion == true,
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
                                    supportsCustomDataLocation = shouldShowCustomDataLocation(platform),
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
                                    state = state,
                                    onSettingsIntent = onSettingsIntent,
                                    showHeading = false,
                                    modifier = detailModifier,
                                )

                                SettingsSection.Help -> HelpSettingsPane(
                                    onOpenBackgroundRunSettings = onOpenBackgroundRunSettings,
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
    showAppUpdateBadge: Boolean,
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
            title = uiString(Res.string.settings_title),
            subtitle = ""
        )
        sections.forEach { section ->
            SettingsSectionListItem(
                section = section,
                selected = desktop && selectedSection == section,
                showSubtitle = !desktop,
                showUpdateBadge = showAppUpdateBadge && section == SettingsSection.AboutApp,
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
    showUpdateBadge: Boolean,
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
            BadgedIcon(
                imageVector = settingsSectionIcon(section),
                contentDescription = null,
                showBadge = showUpdateBadge,
                modifier = Modifier.size(24.dp),
                tint = iconTint,
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
private fun HelpSettingsPane(
    onOpenBackgroundRunSettings: () -> Unit,
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
                title = uiString(Res.string.settings_help_title),
                subtitle = uiString(Res.string.settings_help_description),
            )
        }
        MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = uiString(Res.string.cast_background_troubleshooting_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = uiString(Res.string.cast_background_suspension_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = shellColors.secondaryText,
                        )
                    }
                }
                Text(
                    text = uiString(Res.string.cast_background_permission_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onOpenBackgroundRunSettings,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(uiString(Res.string.cast_allow_background_operation))
                }
            }
        }
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
    val showMacOsWindowCloseBehaviorSetting =
        shouldShowMacOsWindowCloseBehaviorSetting(currentPlatformDescriptor)
    val showCompactPlayerLyricsSetting = isMobilePlatform
    val showPlayerArtworkStyleSetting = shouldShowPlayerArtworkStyleSetting(currentPlatformDescriptor)
    val showAutoOpenPlayerOnStartupSetting =
        shouldShowAutoOpenPlayerOnStartupSetting(currentPlatformDescriptor)
    val showDesktopLyricsSetting = currentPlatformDescriptor.capabilities.supportsDesktopLyrics
    val showMenuBarLyricsControlsSetting =
        currentPlatformDescriptor.capabilities.supportsMenuBarLyricsControls
    val showNavidromeAudioQualitySetting = isMobilePlatform || currentPlatformDescriptor.isAndroidPlatform()
    val showAndroidExtensionDecoderSetting =
        currentPlatformDescriptor.capabilities.supportsAndroidExtensionDecoder
    val showDesktopVlcSettings = currentPlatformDescriptor.isPCPlatform()
    val manualPath = state.desktopVlcManualPath?.takeIf { it.isNotBlank() }
    val autoDetectedPath = state.desktopVlcAutoDetectedPath?.takeIf { it.isNotBlank() }
    val effectivePath = state.desktopVlcEffectivePath?.takeIf { it.isNotBlank() }
    val currentPath = effectivePath ?: uiString(Res.string.vlc_path_not_detected)
    val currentSource = if (manualPath != null) uiString(Res.string.vlc_path_manually_selected) else uiString(Res.string.vlc_path_auto_detected)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showHeading) {
            SectionTitle(
                title = uiString(Res.string.settings_general_title),
                subtitle = uiString(Res.string.settings_general_summary),
            )
        }
        AppLanguageSettings(state.appLanguage) { language ->
            onSettingsIntent(SettingsIntent.AppLanguageChanged(language))
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
                        text = uiString(Res.string.player_startup_autoplay_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = uiString(Res.string.player_startup_autoplay_hint),
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
        if (showAutoOpenPlayerOnStartupSetting) {
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
                            text = uiString(Res.string.player_startup_fullscreen_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = uiString(Res.string.player_startup_fullscreen_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = shellColors.secondaryText,
                        )
                    }
                    Switch(
                        checked = state.autoOpenPlayerOnStartup,
                        onCheckedChange = { enabled ->
                            onSettingsIntent(SettingsIntent.AutoOpenPlayerOnStartupChanged(enabled))
                        },
                        colors = SwitchDefaults.colors(),
                    )
                }
            }
        }
        if (showMacOsWindowCloseBehaviorSetting) {
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
                            text = uiString(Res.string.window_close_minimize_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = uiString(Res.string.window_close_minimize_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = shellColors.secondaryText,
                        )
                    }
                    Switch(
                        checked = state.minimizeWindowOnClose,
                        onCheckedChange = { enabled ->
                            onSettingsIntent(SettingsIntent.MinimizeWindowOnCloseChanged(enabled))
                        },
                        colors = SwitchDefaults.colors(),
                    )
                }
            }
        }
        if (showDesktopLyricsSetting) {
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
                            text = uiString(Res.string.desktop_lyrics_settings_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = uiString(Res.string.desktop_lyrics_settings_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = shellColors.secondaryText,
                        )
                    }
                    Switch(
                        checked = state.showDesktopLyrics,
                        onCheckedChange = { enabled ->
                            onSettingsIntent(SettingsIntent.ShowDesktopLyricsChanged(enabled))
                        },
                        colors = SwitchDefaults.colors(),
                    )
                }
            }
        }
        if (showMenuBarLyricsControlsSetting) {
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
                            text = uiString(Res.string.mac_menu_bar_lyrics_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = uiString(Res.string.mac_menu_bar_lyrics_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = shellColors.secondaryText,
                        )
                    }
                    Switch(
                        checked = state.showMenuBarLyricsControls,
                        onCheckedChange = { enabled ->
                            onSettingsIntent(SettingsIntent.ShowMenuBarLyricsControlsChanged(enabled))
                        },
                        colors = SwitchDefaults.colors(),
                    )
                }
            }
        }
        if (showDesktopVlcSettings) {
            MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = uiString(Res.string.vlc_player_path_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = uiString(Res.string.vlc_path_startup_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = shellColors.secondaryText,
                    )
                    AboutAppFieldRow(
                        label = uiString(Res.string.data_location_current_path),
                        value = currentPath,
                        monospace = true,
                    )
                    AboutDeviceFieldRow(
                        label = uiString(Res.string.sources_title),
                        value = currentSource,
                    )
                    if (manualPath != null && autoDetectedPath != null) {
                        AboutAppFieldRow(
                            label = uiString(Res.string.vlc_detected_path_label),
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
                            Text(uiString(Res.string.vlc_select_path_action))
                        }
                        if (manualPath != null) {
                            OutlinedButton(
                                onClick = { onSettingsIntent(SettingsIntent.ClearDesktopVlcManualPath) },
                            ) {
                                Text(uiString(Res.string.vlc_use_detected_path))
                            }
                        }
                    }
                    Text(
                        text = uiString(Res.string.settings_restart_effect_hint),
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
                            text = uiString(Res.string.player_lyrics_visibility_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = uiString(Res.string.player_lyrics_visibility_hint),
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
        if (showPlayerArtworkStyleSetting) {
            MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = uiString(Res.string.player_artwork_style_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = uiString(Res.string.player_artwork_style_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = shellColors.secondaryText,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerArtworkStyle.entries.forEach { style ->
                            val selected = state.playerArtworkStyle == style
                            if (selected) {
                                Button(
                                    onClick = {
                                        onSettingsIntent(SettingsIntent.PlayerArtworkStyleChanged(style))
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = playerArtworkStyleLabel(style),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        onSettingsIntent(SettingsIntent.PlayerArtworkStyleChanged(style))
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = playerArtworkStyleLabel(style),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
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
                            text = uiString(Res.string.playback_remote_quality_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = uiString(Res.string.playback_remote_quality_change_hint),
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
                        title = uiString(Res.string.playback_mobile_data_quality),
                        selected = state.navidromeMobileAudioQuality,
                        onSelected = { quality ->
                            onSettingsIntent(SettingsIntent.NavidromeMobileAudioQualityChanged(quality))
                        },
                    )
                }
            }
        }
        if (showAndroidExtensionDecoderSetting) {
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
                            text = uiString(Res.string.playback_ffmpeg_decoder_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = uiString(Res.string.playback_ffmpeg_decoder_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = shellColors.secondaryText,
                        )
                    }
                    Switch(
                        checked = state.useAndroidExtensionDecoder,
                        onCheckedChange = { enabled ->
                            onSettingsIntent(SettingsIntent.AndroidExtensionDecoderChanged(enabled))
                        },
                        colors = SwitchDefaults.colors(),
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
                            text = uiString(Res.string.display_scale_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = uiString(Res.string.display_app_scaling_hint),
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

internal fun shouldShowAutoOpenPlayerOnStartupSetting(platform: PlatformDescriptor): Boolean {
    return !platform.isAndroidTV()
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
                title = uiString(Res.string.theme_settings_title),
                subtitle = uiString(Res.string.theme_settings_description),
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
                        text = uiString(Res.string.theme_custom_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = uiString(Res.string.theme_color_selection_hint),
                        color = shellColors.secondaryText,
                    )
                    ThemeColorPickerRow(
                        label = uiString(Res.string.theme_background_color),
                        argb = state.customThemeTokens.backgroundArgb,
                        onClick = { activeColorRole = CustomThemeColorRole.Background },
                    )
                    ThemeColorPickerRow(
                        label = uiString(Res.string.theme_primary_color),
                        argb = state.customThemeTokens.accentArgb,
                        onClick = { activeColorRole = CustomThemeColorRole.Accent },
                    )
                    ThemeColorPickerRow(
                        label = uiString(Res.string.theme_selection_color),
                        argb = state.customThemeTokens.focusArgb,
                        onClick = { activeColorRole = CustomThemeColorRole.Focus },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { onSettingsIntent(SettingsIntent.ResetCustomTheme) }) {
                            Text(uiString(Res.string.theme_reset_custom_colors))
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
                title = uiString(Res.string.settings_lyrics_title),
                subtitle = uiString(Res.string.lyrics_sources_settings_summary),
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
                    uiString(Res.string.lyrics_lrcapi_reserved_source_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (state.hasLrcApiSource) uiString(Res.string.workflow_saved_to_sources) else uiString(Res.string.common_not_configured),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    if (state.hasLrcApiSource) {
                        MainShellAssistChip(
                            onClick = {},
                            label = { Text(uiString(Res.string.settings_direct_source)) },
                            leadingIcon = { Icon(Icons.Rounded.CloudSync, null) },
                        )
                    }
                }
                ImeAwareOutlinedTextField(
                    value = state.lrcApiUrl,
                    onValueChange = { onSettingsIntent(SettingsIntent.LrcApiUrlChanged(it)) },
                    label = { Text(uiString(Res.string.lyrics_lrcapi_request_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    colors = settingsFieldColors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onSettingsIntent(SettingsIntent.SaveLrcApi) }) {
                        Text(uiString(Res.string.lyrics_lrcapi_save_action))
                    }
                    OutlinedButton(
                        onClick = { onSettingsIntent(SettingsIntent.ClearLrcApi) },
                        enabled = state.hasLrcApiSource,
                    ) {
                        Text(uiString(Res.string.lyrics_lrcapi_clear_action))
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
                    uiString(Res.string.lyrics_musicmatch_reserved_source_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (state.hasMusicmatchSource) uiString(Res.string.workflow_saved_to_sources) else uiString(Res.string.common_not_configured),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    if (state.hasMusicmatchSource) {
                        MainShellAssistChip(
                            onClick = {},
                            label = { Text(uiString(Res.string.settings_workflow_source)) },
                            leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) },
                        )
                    }
                }
                ImeAwareOutlinedTextField(
                    value = state.musicmatchUserToken,
                    onValueChange = { onSettingsIntent(SettingsIntent.MusicmatchUserTokenChanged(it)) },
                    label = { Text(uiString(Res.string.settings_usertoken)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    colors = settingsFieldColors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onSettingsIntent(SettingsIntent.SaveMusicmatch) }) {
                        Text(uiString(Res.string.lyrics_musicmatch_save_action))
                    }
                    if (state.hasMusicmatchSource || state.musicmatchUserToken.isNotBlank()) {
                        OutlinedButton(onClick = { onSettingsIntent(SettingsIntent.ClearMusicmatch) }) {
                            Text(uiString(Res.string.lyrics_musicmatch_clear_action))
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
                    label = { Text(uiString(Res.string.lyrics_source_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = settingsFieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.urlTemplate,
                    onValueChange = { onSettingsIntent(SettingsIntent.UrlChanged(it)) },
                    label = { Text(uiString(Res.string.lyrics_source_url_template)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = settingsFieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.queryTemplate,
                    onValueChange = { onSettingsIntent(SettingsIntent.QueryChanged(it)) },
                    label = { Text(uiString(Res.string.lyrics_source_query_template)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = settingsFieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.headersTemplate,
                    onValueChange = { onSettingsIntent(SettingsIntent.HeadersChanged(it)) },
                    label = { Text(uiString(Res.string.lyrics_source_headers_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = settingsFieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.extractor,
                    onValueChange = { onSettingsIntent(SettingsIntent.ExtractorChanged(it)) },
                    label = { Text(uiString(Res.string.lyrics_source_extraction_rules)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = settingsFieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.priority,
                    onValueChange = { onSettingsIntent(SettingsIntent.PriorityChanged(it)) },
                    label = { Text(uiString(Res.string.lyrics_source_priority_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = settingsFieldColors,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(uiString(Res.string.lyrics_source_enable_label), fontWeight = FontWeight.Medium)
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
                        Text(if (state.editingId != null) uiString(Res.string.common_save) else uiString(Res.string.lyrics_source_new_action))
                    }
                    OutlinedButton(onClick = {
                        onSettingsIntent(
                            if (state.editingId != null) SettingsIntent.CreateNew else SettingsIntent.SelectConfig(
                                null
                            )
                        )
                    }) {
                        Text(if (state.editingId != null) uiString(Res.string.lyrics_source_new_action) else uiString(Res.string.common_clear))
                    }
                    if (state.editingId != null) {
                        TextButton(
                            onClick = onRequestDeleteEditingSource,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text(uiString(Res.string.common_delete))
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
                Text(uiString(Res.string.settings_workflow_json), fontWeight = FontWeight.Bold)
                Text(
                    uiString(Res.string.workflow_editor_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ImeAwareOutlinedTextField(
                    value = state.workflowJsonInput,
                    onValueChange = { onSettingsIntent(SettingsIntent.WorkflowJsonChanged(it)) },
                    label = { Text(uiString(Res.string.settings_workflow_json)) },
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
                        Text(if (state.editingWorkflowId != null) uiString(Res.string.workflow_save_action) else uiString(Res.string.workflow_new_action))
                    }
                    if (state.editingWorkflowId != null || state.workflowJsonInput.isNotBlank()) {
                        OutlinedButton(onClick = {
                            onSettingsIntent(
                                if (state.editingWorkflowId != null) SettingsIntent.CreateNewWorkflow else SettingsIntent.ViewWorkflow(
                                    null
                                )
                            )
                        }) {
                            Text(if (state.editingWorkflowId != null) uiString(Res.string.workflow_new_action) else uiString(Res.string.lyrics_source_clear_editor))
                        }
                    }
                }
            }
        }

        SectionTitle(
            title = uiString(Res.string.lyrics_source_existing_configurations),
            subtitle = uiString(Res.string.lyrics_source_kinds_description)
        )
        if (state.sources.isEmpty()) {
            EmptyStateCard(
                title = uiString(Res.string.lyrics_sources_empty_title),
                body = uiString(Res.string.lyrics_source_priority_hint),
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
            Text(uiString(Res.string.font_share_library_title), fontWeight = FontWeight.Bold)
            Text(
                uiString(Res.string.font_import_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.importedLyricsShareFonts.isEmpty()) {
                        uiString(Res.string.font_imported_list_empty_title)
                    } else {
                        uiString(Res.plurals.font_imported_count, (state.importedLyricsShareFonts.size).toInt(), state.importedLyricsShareFonts.size)
                    },
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
                Button(
                    onClick = { onSettingsIntent(SettingsIntent.ImportLyricsShareFont) },
                    enabled = !state.importingLyricsShareFont && state.deletingLyricsShareFontKey == null,
                ) {
                    Text(if (state.importingLyricsShareFont) uiString(Res.string.font_importing_progress) else uiString(Res.string.font_import_action))
                }
            }
            when {
                state.lyricsShareFontsLoading -> {
                    Text(uiString(Res.string.font_imported_list_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                state.importedLyricsShareFonts.isEmpty() -> {
                    Text(uiString(Res.string.font_imported_list_empty_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        displayName = option.uiDisplayName(),
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
                                contentDescription = uiString(Res.string.font_delete_action),
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
                    LyricsShareImportedFontName(option.uiDisplayName())
                }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !deleting,
                ) {
                    Text(if (deleting) uiString(Res.string.font_deleting_progress) else uiString(Res.string.common_delete))
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
        displayName = option.uiDisplayName(),
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

@Composable
private fun settingsSectionTitle(section: SettingsSection): String {
    return when (section) {
        SettingsSection.General -> uiString(Res.string.settings_general_title)
        SettingsSection.Theme -> uiString(Res.string.theme_settings_title)
        SettingsSection.Lyrics -> uiString(Res.string.settings_lyrics_title)
        SettingsSection.Storage -> uiString(Res.string.settings_storage_management_title)
        SettingsSection.AboutDevice -> uiString(Res.string.about_device_title)
        SettingsSection.AboutApp -> uiString(Res.string.about_app_title)
        SettingsSection.Help -> uiString(Res.string.settings_help_title)
    }
}

@Composable
private fun settingsSectionSubtitle(section: SettingsSection): String {
    return when (section) {
        SettingsSection.General -> uiString(Res.string.settings_general_description)
        SettingsSection.Theme -> uiString(Res.string.theme_settings_summary)
        SettingsSection.Lyrics -> uiString(Res.string.lyrics_sources_settings_description)
        SettingsSection.Storage -> uiString(Res.string.storage_cleanup_summary)
        SettingsSection.AboutDevice -> uiString(Res.string.device_info_summary)
        SettingsSection.AboutApp -> uiString(Res.string.about_app_information_hint)
        SettingsSection.Help -> uiString(Res.string.settings_help_description)
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
        SettingsSection.Help -> Icons.AutoMirrored.Rounded.HelpOutline
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
        state.deviceInfoLoading -> uiString(Res.string.device_info_loading_progress)
        else -> uiString(Res.string.about_device_title)
    }.orEmpty()
    val summarySubtitle = snapshot?.let {
        buildString {
            append(it.systemName.ifBlank { uiString(Res.string.device_system_label) })
            append(" · ")
            append(it.systemVersion.ifBlank { uiString(Res.string.about_version_unavailable) })
        }
    } ?: if (state.deviceInfoLoading) {
        uiString(Res.string.device_info_loading_description)
    } else {
        uiString(Res.string.device_info_summary)
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
                title = uiString(Res.string.about_device_title),
                subtitle = uiString(Res.string.device_info_description),
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
        AboutDeviceInfoCard(title = uiString(Res.string.device_system_label)) {
            AboutDeviceFieldRow(
                label = uiString(Res.string.device_system_name),
                value = deviceInfoDisplayValue(snapshot?.systemName, state.deviceInfoLoading),
            )
            AboutDeviceFieldRow(
                label = uiString(Res.string.device_system_version),
                value = deviceInfoDisplayValue(snapshot?.systemVersion, state.deviceInfoLoading),
            )
            snapshot?.deviceModel?.takeIf { it.isNotBlank() }?.let { model ->
                AboutDeviceFieldRow(
                    label = uiString(Res.string.device_model_label),
                    value = model,
                )
            }
        }
        AboutDeviceInfoCard(title = uiString(Res.string.device_display_label)) {
            AboutDeviceFieldRow(
                label = uiString(Res.string.display_resolution_label),
                value = deviceInfoDisplayValue(snapshot?.resolution, state.deviceInfoLoading),
            )
            if (showsAndroidDisplayMetrics) {
                AboutDeviceFieldRow(
                    label = uiString(Res.string.display_app_resolution_dp),
                    value = deviceInfoDpResolutionValue(
                        widthPx = snapshot?.resolutionWidthPx,
                        heightPx = snapshot?.resolutionHeightPx,
                        density = localDensity.density,
                        loading = state.deviceInfoLoading,
                    ),
                )
                AboutDeviceFieldRow(
                    label = uiString(Res.string.display_system_resolution_dp),
                    value = deviceInfoDpResolutionValue(
                        widthPx = snapshot?.resolutionWidthPx,
                        heightPx = snapshot?.resolutionHeightPx,
                        density = snapshot?.systemDensityScale,
                        loading = state.deviceInfoLoading,
                    ),
                )
                AboutDeviceFieldRow(
                    label = uiString(Res.string.display_app_density),
                    value = deviceInfoDensityValue(
                        density = localDensity.density,
                        loading = false,
                    ),
                )
                AboutDeviceFieldRow(
                    label = uiString(Res.string.display_system_density),
                    value = deviceInfoDensityValue(
                        density = snapshot?.systemDensityScale,
                        loading = state.deviceInfoLoading,
                    ),
                )
            } else {
                AboutDeviceFieldRow(
                    label = uiString(Res.string.display_resolution_dp),
                    value = deviceInfoDpResolutionValue(
                        widthPx = snapshot?.resolutionWidthPx,
                        heightPx = snapshot?.resolutionHeightPx,
                        density = localDensity.density,
                        loading = state.deviceInfoLoading,
                    ),
                )
                AboutDeviceFieldRow(
                    label = uiString(Res.string.display_density_label),
                    value = deviceInfoDensityValue(
                        density = localDensity.density,
                        loading = false,
                    ),
                )
            }
            AboutDeviceFieldRow(
                label = uiString(Res.string.display_font_scale),
                value = deviceInfoFontScaleValue(localDensity.fontScale),
            )
        }
        AboutDeviceInfoCard(title = uiString(Res.string.device_hardware_label)) {
            AboutDeviceFieldRow(
                label = "CPU",
                value = deviceInfoDisplayValue(snapshot?.cpuDescriptionText?.displayText(), state.deviceInfoLoading),
            )
            AboutDeviceFieldRow(
                label = uiString(Res.string.device_memory_label),
                value = deviceInfoMemoryValue(snapshot?.totalMemoryBytes, state.deviceInfoLoading),
            )
        }
    }
}

@Composable
private fun AboutAppSettingsPane(
    platformName: String,
    state: SettingsState,
    onSettingsIntent: (SettingsIntent) -> Unit,
    showHeading: Boolean,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    val uriHandler = LocalUriHandler.current
    val appUpdateUiModel = state.toAppUpdateUiModel()
    val appUpdateChecking = appUpdateUiModel.status == AppUpdateUiStatus.Checking
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showHeading) {
            SectionTitle(
                title = uiString(Res.string.about_app_title),
                subtitle = uiString(Res.string.about_app_information_hint),
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
        AboutDeviceInfoCard(title = uiString(Res.string.common_basic_information)) {
            AboutAppFieldRow(
                label = uiString(Res.string.about_version),
                value = BuildMetadata.versionDisplay,
                monospace = true,
            )
            AboutAppFieldRow(
                label = uiString(Res.string.device_platform_label),
                value = platformName,
            )
            AboutAppFieldRow(
                label = uiString(Res.string.about_build_time),
                value = BuildMetadata.buildTimeUtc,
                monospace = true,
            )
        }
        AboutDeviceInfoCard(title = uiString(Res.string.settings_app_updates_title)) {
            when (appUpdateUiModel.status) {
                AppUpdateUiStatus.Checking -> {
                    Text(
                        text = appUpdateUiModel.message?.displayText().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = shellColors.secondaryText,
                    )
                }

                AppUpdateUiStatus.Error -> {
                    Text(
                        text = appUpdateUiModel.message?.displayText().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                AppUpdateUiStatus.UpdateAvailable -> {
                    AboutAppFieldRow(
                        label = uiString(Res.string.update_latest_version),
                        value = appUpdateUiModel.latestVersion.orEmpty(),
                        monospace = true,
                    )
                    Text(
                        text = appUpdateUiModel.message?.displayText().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = shellColors.secondaryText,
                    )
                }

                AppUpdateUiStatus.UpToDate -> {
                    Text(
                        text = appUpdateUiModel.message?.displayText().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = shellColors.secondaryText,
                    )
                }

                AppUpdateUiStatus.Idle -> Unit
            }
            appUpdateUiModel.errorMessage?.let { errorMessage ->
                Text(
                    text = errorMessage.uiDisplayText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = { onSettingsIntent(SettingsIntent.CheckAppUpdate) },
                    enabled = !appUpdateChecking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (appUpdateChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (appUpdateChecking) uiString(Res.string.common_checking) else uiString(Res.string.update_check_action))
                }
                if (appUpdateUiModel.status == AppUpdateUiStatus.UpdateAvailable) {
                    Button(
                        onClick = {
                            uriHandler.openUri(appUpdateUiModel.downloadUrl)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(uiString(Res.string.update_open_download_page))
                    }
                }
            }
        }
        AboutDeviceInfoCard(title = uiString(Res.string.about_developer_label)) {
            AboutAppFieldRow(
                label = uiString(Res.string.common_name),
                value = ABOUT_APP_DEVELOPER,
            )
        }
        AboutDeviceInfoCard(title = uiString(Res.string.about_project_website)) {
            AboutAppLinkFieldRow(
                label = uiString(Res.string.lyrics_source_address_label),
                value = LynMusicUpdateLinks.PROJECT_URL,
                url = LynMusicUpdateLinks.PROJECT_URL,
            )
        }
        AboutDeviceInfoCard(title = uiString(Res.string.about_wechat_account)) {
            AboutAppFieldRow(
                label = uiString(Res.string.common_account),
                value = ABOUT_APP_WECHAT_ACCOUNT,
            )
            Text(
                text = uiString(Res.string.about_wechat_qr_code),
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
                text = uiString(Res.string.about_wechat_follow_hint),
                style = MaterialTheme.typography.bodySmall,
                color = shellColors.secondaryText,
            )
        }
    }
}

@Composable
private fun StorageSettingsPane(
    state: SettingsState,
    supportsCustomDataLocation: Boolean,
    onSettingsIntent: (SettingsIntent) -> Unit,
    showHeading: Boolean,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    val categoryOrder = remember {
        listOf(
            AppStorageCategory.Artwork,
            AppStorageCategory.PlaybackCache,
            AppStorageCategory.OfflineDownloads,
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
        onSettingsIntent(SettingsIntent.LoadStorageUsage(force = true))
    }
    val pendingDataRootPath = state.pendingDataRootPath
    when {
        state.dataLocationRestartRequired -> {
            AlertDialog(
                onDismissRequest = {},
                containerColor = shellColors.navContainer,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 0.dp,
                title = { Text(uiString(Res.string.settings_restart_required)) },
                text = {
                    Text(uiString(Res.string.data_location_saved_restart_hint))
                },
                confirmButton = {
                    Button(onClick = { onSettingsIntent(SettingsIntent.ConfirmDataLocationRestart) }) {
                        Text(uiString(Res.string.common_exit_app))
                    }
                },
            )
        }

        state.dataLocationDiscardConfirmationRequired -> {
            AlertDialog(
                onDismissRequest = {
                    if (!state.dataLocationBusy) {
                        onSettingsIntent(SettingsIntent.CancelDiscardDataLocation)
                    }
                },
                containerColor = shellColors.navContainer,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 0.dp,
                title = { Text(uiString(Res.string.data_location_discard_confirmation_title)) },
                text = {
                    Text(uiString(Res.string.data_location_discard_confirmation_description))
                },
                confirmButton = {
                    Button(
                        onClick = { onSettingsIntent(SettingsIntent.ConfirmDiscardDataLocation) },
                        enabled = !state.dataLocationBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(uiString(Res.string.data_location_discard_permanently_action))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { onSettingsIntent(SettingsIntent.CancelDiscardDataLocation) },
                        enabled = !state.dataLocationBusy,
                    ) {
                        Text(uiString(Res.string.common_cancel))
                    }
                },
            )
        }

        pendingDataRootPath != null -> {
            AlertDialog(
                onDismissRequest = {
                    if (!state.dataLocationBusy) {
                        onSettingsIntent(SettingsIntent.CancelDataLocationSelection)
                    }
                },
                containerColor = shellColors.navContainer,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 0.dp,
                title = { Text(uiString(Res.string.data_location_change_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(uiString(Res.string.data_location_new_folder_label))
                        Text(
                            text = pendingDataRootPath,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(uiString(Res.string.data_location_old_data_strategy_hint))
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onSettingsIntent(
                                SettingsIntent.RequestDataLocationChange(AppDataLocationChangeMode.Migrate),
                            )
                        },
                        enabled = !state.dataLocationBusy,
                    ) {
                        Text(uiString(Res.string.data_location_migrate_old_data))
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                onSettingsIntent(
                                    SettingsIntent.RequestDataLocationChange(AppDataLocationChangeMode.Discard),
                                )
                            },
                            enabled = !state.dataLocationBusy,
                        ) {
                            Text(uiString(Res.string.data_location_discard_old_data))
                        }
                        TextButton(
                            onClick = { onSettingsIntent(SettingsIntent.CancelDataLocationSelection) },
                            enabled = !state.dataLocationBusy,
                        ) {
                            Text(uiString(Res.string.common_cancel))
                        }
                    }
                },
            )
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
                title = uiString(Res.string.settings_storage_management_title),
                subtitle = uiString(Res.string.storage_management_description),
            )
        }
        if (supportsCustomDataLocation) {
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
                        Text(uiString(Res.string.data_location_title), fontWeight = FontWeight.Bold)
                        Text(
                            text = state.currentDataRootPath.ifBlank { uiString(Res.string.data_location_reading_path_status) },
                            color = shellColors.secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            uiString(Res.string.data_location_new_folder_hint),
                            color = shellColors.secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        state.pendingDataCleanupRootPath?.let { cleanupPath ->
                            Text(
                                uiString(Res.string.data_location_pending_cleanup_path, cleanupPath),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    if (state.pendingDataCleanupRootPath != null) {
                        OutlinedButton(
                            onClick = { onSettingsIntent(SettingsIntent.RetryDataLocationCleanup) },
                            enabled = !state.dataLocationBusy,
                        ) {
                            Text(if (state.dataLocationBusy) uiString(Res.string.data_location_cleanup_status) else uiString(Res.string.data_location_retry_cleanup))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSettingsIntent(SettingsIntent.PickDataLocation) },
                            enabled = !state.dataLocationBusy && !state.dataLocationRestartRequired,
                        ) {
                            Text(if (state.dataLocationBusy) uiString(Res.string.common_processing) else uiString(Res.string.data_location_change_action))
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(uiString(Res.string.storage_manageable_usage), fontWeight = FontWeight.Bold)
                        Text(
                            text = state.storageSnapshot?.let { formatStorageSize(it.totalSizeBytes) }
                                ?: if (state.storageLoading) uiString(Res.string.storage_calculating_usage_progress) else uiString(Res.string.common_not_read),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            uiString(Res.string.storage_cleanup_exclusions_hint),
                            color = shellColors.secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (storagePaths.isNotEmpty()) {
                            Text(
                                uiString(Res.string.storage_paths_title),
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
                        Text(if (state.storageLoading) uiString(Res.string.common_refreshing) else uiString(Res.string.common_refresh))
                    }
                }
            }
        }
        if (categories.isEmpty()) {
            EmptyStateCard(
                title = if (state.storageLoading) uiString(Res.string.storage_calculating_usage) else uiString(Res.string.storage_no_manageable_data),
                body = if (state.storageLoading) {
                    uiString(Res.string.storage_reading_locations_description)
                } else {
                    uiString(Res.string.storage_no_cleanup_categories)
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
                    Text(if (clearing) uiString(Res.string.storage_cleaning_progress) else uiString(Res.string.common_clean_up))
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
            bitmap = imageResource(PlayerRes.drawable.about_app_wechat_qr),
            contentDescription = uiString(Res.string.about_wechat_qr_code),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun storageCategoryTitle(category: AppStorageCategory): String {
    return when (category) {
        AppStorageCategory.Artwork -> uiString(Res.string.storage_artwork_cache)
        AppStorageCategory.PlaybackCache -> uiString(Res.string.storage_playback_cache)
        AppStorageCategory.OfflineDownloads -> uiString(Res.string.storage_offline_music)
        AppStorageCategory.LyricsShareTemp -> uiString(Res.string.storage_lyrics_share_temporary_files)
        AppStorageCategory.TagEditTemp -> uiString(Res.string.storage_tags_temporary_files)
    }
}

@Composable
private fun storageCategoryDescription(category: AppStorageCategory): String {
    return when (category) {
        AppStorageCategory.Artwork -> uiString(Res.string.storage_artwork_cache_description)
        AppStorageCategory.PlaybackCache -> uiString(Res.string.storage_samba_cache_description)
        AppStorageCategory.OfflineDownloads -> uiString(Res.string.storage_offline_music_description)
        AppStorageCategory.LyricsShareTemp -> uiString(Res.string.storage_lyrics_share_temporary_description)
        AppStorageCategory.TagEditTemp -> uiString(Res.string.storage_tags_temporary_description)
    }
}

@Composable
private fun appDisplayScalePresetLabel(preset: AppDisplayScalePreset): String {
    return when (preset) {
        AppDisplayScalePreset.Compact -> uiString(Res.string.display_scale_compact)
        AppDisplayScalePreset.Default -> uiString(Res.string.common_default)
        AppDisplayScalePreset.Large -> uiString(Res.string.display_scale_large)
    }
}

@Composable
private fun playerArtworkStyleLabel(style: PlayerArtworkStyle): String {
    return when (style) {
        PlayerArtworkStyle.VINYL -> uiString(Res.string.player_artwork_style_vinyl)
        PlayerArtworkStyle.HALF_RECORD -> uiString(Res.string.player_artwork_style_record)
        PlayerArtworkStyle.MINIMAL_COVER -> uiString(Res.string.player_artwork_style_minimal)
    }
}

internal fun shouldShowPlayerArtworkStyleSetting(platform: PlatformDescriptor): Boolean {
    return platform.isMobilePlatform() || platform.isPCPlatform() || platform.isAndroidAutomotivePlatform()
}

internal fun shouldShowMacOsWindowCloseBehaviorSetting(platform: PlatformDescriptor): Boolean {
    return platform.capabilities.supportsMacOsWindowCloseBehavior
}

internal fun navidromeAudioQualityLabelText(quality: NavidromeAudioQuality): UiText {
    return when (quality) {
        NavidromeAudioQuality.Original -> uiText(Res.string.common_original)
        NavidromeAudioQuality.Kbps320 -> UiText.Raw("320kbps")
        NavidromeAudioQuality.Kbps192 -> UiText.Raw("192kbps")
        NavidromeAudioQuality.Kbps128 -> UiText.Raw("128kbps")
    }
}

@Composable
internal fun navidromeAudioQualityLabel(quality: NavidromeAudioQuality): String = navidromeAudioQualityLabelText(quality).displayText()

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

@Composable
private fun deviceInfoDisplayValue(value: String?, loading: Boolean): String {
    return when {
        value != null && value.isNotBlank() -> value
        loading -> uiString(Res.string.device_info_reading_status)
        else -> uiString(Res.string.common_unavailable)
    }
}

internal fun deviceInfoDpResolutionValueText(
    widthPx: Int?,
    heightPx: Int?,
    density: Float?,
    loading: Boolean,
): UiText {
    val resolvedWidth = widthPx?.takeIf { it > 0 }
    val resolvedHeight = heightPx?.takeIf { it > 0 }
    if (resolvedWidth == null || resolvedHeight == null) {
        return if (loading) uiText(Res.string.device_info_reading_status) else uiText(Res.string.common_unavailable)
    }
    val resolvedDensity = density?.takeIf { it.isFinite() && it > 0f } ?: return uiText(Res.string.common_unavailable)
    val widthDp = (resolvedWidth / resolvedDensity).roundToInt()
    val heightDp = (resolvedHeight / resolvedDensity).roundToInt()
    return UiText.Raw("$widthDp × $heightDp dp")
}

@Composable
internal fun deviceInfoDpResolutionValue(
    widthPx: Int?,
    heightPx: Int?,
    density: Float?,
    loading: Boolean,
): String = deviceInfoDpResolutionValueText(widthPx, heightPx, density, loading).displayText()

internal fun deviceInfoDensityValueText(
    density: Float?,
    loading: Boolean,
): UiText {
    val resolvedDensity = density?.takeIf { it.isFinite() && it > 0f }
        ?: return if (loading) uiText(Res.string.device_info_reading_status) else uiText(Res.string.common_unavailable)
    return formatDeviceInfoDecimal(resolvedDensity)?.let { UiText.Raw("$it px/dp") } ?: uiText(Res.string.common_unavailable)
}

@Composable
internal fun deviceInfoDensityValue(
    density: Float?,
    loading: Boolean,
): String = deviceInfoDensityValueText(density, loading).displayText()

internal fun deviceInfoFontScaleValueText(fontScale: Float): UiText {
    return formatDeviceInfoDecimal(fontScale)?.let { UiText.Raw("${it}x") } ?: uiText(Res.string.common_unavailable)
}

@Composable
internal fun deviceInfoFontScaleValue(fontScale: Float): String = deviceInfoFontScaleValueText(fontScale).displayText()

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

@Composable
private fun deviceInfoMemoryValue(totalMemoryBytes: Long?, loading: Boolean): String {
    return totalMemoryBytes?.takeIf { it > 0L }?.let(::formatStorageSize)
        ?: if (loading) uiString(Res.string.device_info_reading_status) else uiString(Res.string.common_unavailable)
}

private const val ABOUT_APP_NAME = "LynMusic"
private val ABOUT_APP_SUMMARY: String @Composable get() = uiString(Res.string.about_app_information_summary)
private val ABOUT_APP_DEVELOPER: String @Composable get() = uiString(Res.string.about_author_name)
private val ABOUT_APP_WECHAT_ACCOUNT: String @Composable get() = uiString(Res.string.about_author_name)

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
                    append(if (themeId == AppThemeId.Custom) uiString(Res.string.theme_custom_colors_title) else uiString(Res.string.theme_preset_label))
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
            text = uiString(Res.string.theme_text_color_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = uiString(Res.string.theme_text_color_scope_hint, themeDisplayName(selectedTheme)),
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
            text = uiString(Res.string.theme_select_color_title),
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

@Composable
private fun themeDisplayName(themeId: AppThemeId): String {
    return when (themeId) {
        AppThemeId.Classic -> uiString(Res.string.theme_classic_dark)
        AppThemeId.Forest -> uiString(Res.string.theme_forest)
        AppThemeId.Ocean -> uiString(Res.string.theme_classic_light)
        AppThemeId.Sand -> uiString(Res.string.theme_sandstone)
        AppThemeId.Custom -> uiString(Res.string.common_custom)
    }
}

@Composable
private fun themeTextPaletteLabel(textPalette: AppThemeTextPalette): String {
    return when (textPalette) {
        AppThemeTextPalette.White -> uiString(Res.string.theme_white_text)
        AppThemeTextPalette.Black -> uiString(Res.string.theme_black_text)
    }
}

private fun AppThemeTokens.colorFor(role: CustomThemeColorRole): Int {
    return when (role) {
        CustomThemeColorRole.Background -> backgroundArgb
        CustomThemeColorRole.Accent -> accentArgb
        CustomThemeColorRole.Focus -> focusArgb
    }
}

@Composable
private fun customThemeColorLabel(role: CustomThemeColorRole): String {
    return when (role) {
        CustomThemeColorRole.Background -> uiString(Res.string.theme_background_color)
        CustomThemeColorRole.Accent -> uiString(Res.string.theme_primary_color)
        CustomThemeColorRole.Focus -> uiString(Res.string.theme_selection_color)
    }
}
