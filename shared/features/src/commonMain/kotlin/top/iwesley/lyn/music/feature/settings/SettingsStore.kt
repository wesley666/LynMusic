package top.iwesley.lyn.music.feature.settings

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.uiErrorText
import top.iwesley.lyn.music.core.model.plus

import top.iwesley.lyn.music.core.model.AppLanguage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.Clock
import top.iwesley.lyn.music.core.model.AppReleaseInfo
import top.iwesley.lyn.music.core.model.AppStorageCategory
import top.iwesley.lyn.music.core.model.AppStorageGateway
import top.iwesley.lyn.music.core.model.AppStorageSnapshot
import top.iwesley.lyn.music.core.model.AppDataLocationChangeMode
import top.iwesley.lyn.music.core.model.AppDataLocationPlatformService
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.BuildMetadata
import top.iwesley.lyn.music.core.model.DeviceInfoGateway
import top.iwesley.lyn.music.core.model.DeviceInfoSnapshot
import top.iwesley.lyn.music.core.model.DesktopLyricsPlatformService
import top.iwesley.lyn.music.core.model.DEFAULT_MINIMIZE_WINDOW_ON_CLOSE
import top.iwesley.lyn.music.core.model.LyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.LyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSourceDefinition
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.PlayerArtworkStyle
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.UnsupportedAppStorageGateway
import top.iwesley.lyn.music.core.model.UnsupportedAppDataLocationPlatformService
import top.iwesley.lyn.music.core.model.UnsupportedDeviceInfoGateway
import top.iwesley.lyn.music.core.model.UnsupportedDesktopLyricsPlatformService
import top.iwesley.lyn.music.core.model.UnsupportedLyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.UnsupportedLyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedVlcPathPickerPlatformService
import top.iwesley.lyn.music.core.model.VlcPathPickerPlatformService
import top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig
import top.iwesley.lyn.music.core.model.defaultCustomThemeTokens
import top.iwesley.lyn.music.core.model.defaultThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.isAppReleaseNewer
import top.iwesley.lyn.music.core.model.withThemePalette
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.AppUpdateRepository
import top.iwesley.lyn.music.data.repository.LRCLIB_JSON_MAP_EXTRACTOR
import top.iwesley.lyn.music.data.repository.SettingsRepository
import top.iwesley.lyn.music.data.repository.UnsupportedAppUpdateRepository
import top.iwesley.lyn.music.domain.DEFAULT_LRCAPI_URL
import top.iwesley.lyn.music.domain.MANAGED_LRCAPI_SOURCE_ID
import top.iwesley.lyn.music.domain.MANAGED_MUSICMATCH_SOURCE_ID
import top.iwesley.lyn.music.domain.buildManagedLrcApiConfig
import top.iwesley.lyn.music.domain.buildManagedMusicmatchWorkflowJson
import top.iwesley.lyn.music.domain.extractManagedLrcApiUrl
import top.iwesley.lyn.music.domain.extractManagedMusicmatchUserToken
import top.iwesley.lyn.music.domain.isManagedLrcApiSource
import top.iwesley.lyn.music.domain.isManagedMusicmatchSource
import top.iwesley.lyn.music.domain.parseWorkflowLyricsSourceConfig
import top.iwesley.lyn.music.domain.rewriteWorkflowLyricsSourceEnabled
import top.iwesley.lyn.music.domain.rewriteWorkflowLyricsSourceId

enum class CustomThemeColorRole {
    Background,
    Accent,
    Focus,
}

data class SettingsState(
    val appLanguage: AppLanguage = AppLanguage.System,
    val sources: List<LyricsSourceDefinition> = emptyList(),
    val useSambaCache: Boolean = false,
    val showCompactPlayerLyrics: Boolean = false,
    val showDesktopLyrics: Boolean = false,
    val showMenuBarLyricsControls: Boolean = false,
    val autoPlayOnStartup: Boolean = false,
    val autoOpenPlayerOnStartup: Boolean = false,
    val minimizeWindowOnClose: Boolean = DEFAULT_MINIMIZE_WINDOW_ON_CLOSE,
    val appDisplayScalePreset: AppDisplayScalePreset = AppDisplayScalePreset.Default,
    val navidromeWifiAudioQuality: NavidromeAudioQuality = NavidromeAudioQuality.Original,
    val navidromeMobileAudioQuality: NavidromeAudioQuality = NavidromeAudioQuality.Kbps192,
    val useAndroidExtensionDecoder: Boolean = false,
    val playerArtworkStyle: PlayerArtworkStyle = PlayerArtworkStyle.VINYL,
    val supportsLyricsShareFontImport: Boolean = false,
    val importedLyricsShareFonts: List<LyricsShareFontOption> = emptyList(),
    val lyricsShareFontsLoading: Boolean = false,
    val importingLyricsShareFont: Boolean = false,
    val deletingLyricsShareFontKey: String? = null,
    val selectedTheme: AppThemeId = AppThemeId.Ocean,
    val customThemeTokens: AppThemeTokens = defaultCustomThemeTokens(),
    val textPalettePreferences: AppThemeTextPalettePreferences = defaultThemeTextPalettePreferences(),
    val lrcApiUrl: String = "",
    val hasLrcApiSource: Boolean = false,
    val musicmatchUserToken: String = "",
    val hasMusicmatchSource: Boolean = false,
    val editingId: String? = null,
    val name: String = "",
    val method: RequestMethod = RequestMethod.GET,
    val urlTemplate: String = "",
    val headersTemplate: String = "",
    val queryTemplate: String = "",
    val bodyTemplate: String = "",
    val responseFormat: LyricsResponseFormat = LyricsResponseFormat.JSON,
    val extractor: String = LRCLIB_JSON_MAP_EXTRACTOR,
    val priority: String = "0",
    val enabled: Boolean = true,
    val workflowJsonInput: String = "",
    val editingWorkflowId: String? = null,
    val storageSnapshot: AppStorageSnapshot? = null,
    val storageLoading: Boolean = false,
    val storageLoaded: Boolean = false,
    val clearingStorageCategory: AppStorageCategory? = null,
    val currentDataRootPath: String = "",
    val pendingDataCleanupRootPath: String? = null,
    val pendingDataRootPath: String? = null,
    val dataLocationBusy: Boolean = false,
    val dataLocationDiscardConfirmationRequired: Boolean = false,
    val dataLocationRestartRequired: Boolean = false,
    val deviceInfoSnapshot: DeviceInfoSnapshot? = null,
    val deviceInfoLoading: Boolean = false,
    val deviceInfoLoaded: Boolean = false,
    val appUpdateChecking: Boolean = false,
    val appUpdateLatestRelease: AppReleaseInfo? = null,
    val appUpdateHasNewVersion: Boolean? = null,
    val appUpdateError: UiText? = null,
    val desktopVlcAutoDetectedPath: String? = null,
    val desktopVlcManualPath: String? = null,
    val desktopVlcEffectivePath: String? = null,
    val message: UiText? = null,
)

sealed interface SettingsIntent {
    data class AppLanguageChanged(val value: AppLanguage) : SettingsIntent
    data class UseSambaCacheChanged(val value: Boolean) : SettingsIntent
    data class ShowCompactPlayerLyricsChanged(val value: Boolean) : SettingsIntent
    data class ShowDesktopLyricsChanged(val value: Boolean) : SettingsIntent
    data class ShowMenuBarLyricsControlsChanged(val value: Boolean) : SettingsIntent
    data object RecheckDesktopLyricsPermission : SettingsIntent
    data class AutoPlayOnStartupChanged(val value: Boolean) : SettingsIntent
    data class AutoOpenPlayerOnStartupChanged(val value: Boolean) : SettingsIntent
    data class MinimizeWindowOnCloseChanged(
        val value: Boolean,
        val revision: Long = 0L,
    ) : SettingsIntent
    data class AppDisplayScalePresetChanged(val value: AppDisplayScalePreset) : SettingsIntent
    data class NavidromeWifiAudioQualityChanged(val value: NavidromeAudioQuality) : SettingsIntent
    data class NavidromeMobileAudioQualityChanged(val value: NavidromeAudioQuality) : SettingsIntent
    data class AndroidExtensionDecoderChanged(val value: Boolean) : SettingsIntent
    data class PlayerArtworkStyleChanged(val value: PlayerArtworkStyle) : SettingsIntent
    data class ThemeSelected(val value: AppThemeId) : SettingsIntent
    data class ThemeTextPaletteSelected(val themeId: AppThemeId, val value: AppThemeTextPalette) : SettingsIntent
    data class CustomThemeColorUpdated(val role: CustomThemeColorRole, val argb: Int) : SettingsIntent
    data object PickDesktopVlcPath : SettingsIntent
    data object ClearDesktopVlcManualPath : SettingsIntent
    data class SelectConfig(val config: LyricsSourceConfig?) : SettingsIntent
    data class SelectLrcApi(val config: LyricsSourceConfig?) : SettingsIntent
    data class SelectMusicmatch(val config: WorkflowLyricsSourceConfig?) : SettingsIntent
    data class ViewWorkflow(val config: WorkflowLyricsSourceConfig?) : SettingsIntent
    data class LrcApiUrlChanged(val value: String) : SettingsIntent
    data class MusicmatchUserTokenChanged(val value: String) : SettingsIntent
    data class NameChanged(val value: String) : SettingsIntent
    data class MethodChanged(val value: RequestMethod) : SettingsIntent
    data class UrlChanged(val value: String) : SettingsIntent
    data class HeadersChanged(val value: String) : SettingsIntent
    data class QueryChanged(val value: String) : SettingsIntent
    data class BodyChanged(val value: String) : SettingsIntent
    data class ResponseFormatChanged(val value: LyricsResponseFormat) : SettingsIntent
    data class ExtractorChanged(val value: String) : SettingsIntent
    data class PriorityChanged(val value: String) : SettingsIntent
    data class EnabledChanged(val value: Boolean) : SettingsIntent
    data class WorkflowJsonChanged(val value: String) : SettingsIntent
    data class LoadStorageUsage(val force: Boolean = false) : SettingsIntent
    data class ClearStorageCategory(val category: AppStorageCategory) : SettingsIntent
    data object PickDataLocation : SettingsIntent
    data object CancelDataLocationSelection : SettingsIntent
    data class RequestDataLocationChange(val mode: AppDataLocationChangeMode) : SettingsIntent
    data object ConfirmDiscardDataLocation : SettingsIntent
    data object CancelDiscardDataLocation : SettingsIntent
    data object ConfirmDataLocationRestart : SettingsIntent
    data object RetryDataLocationCleanup : SettingsIntent
    data class LoadDeviceInfo(val force: Boolean = false) : SettingsIntent
    data object CheckAppUpdate : SettingsIntent
    data object CheckAppUpdateSilently : SettingsIntent
    data object LoadLyricsShareImportedFonts : SettingsIntent
    data object ImportLyricsShareFont : SettingsIntent
    data class DeleteLyricsShareImportedFont(val fontKey: String) : SettingsIntent
    data object ImportWorkflow : SettingsIntent
    data object CreateNew : SettingsIntent
    data object CreateNewWorkflow : SettingsIntent
    data object SaveLrcApi : SettingsIntent
    data object ClearLrcApi : SettingsIntent
    data object SaveMusicmatch : SettingsIntent
    data object ClearMusicmatch : SettingsIntent
    data object ResetCustomTheme : SettingsIntent
    data class ToggleSourceEnabled(val sourceId: String, val enabled: Boolean) : SettingsIntent
    data class DeleteSource(val sourceId: String) : SettingsIntent
    data object Save : SettingsIntent
    data object Delete : SettingsIntent
    data object ClearMessage : SettingsIntent
}

sealed interface SettingsEffect {
    data object LyricsShareFontsChanged : SettingsEffect
    data object ExitApplicationRequested : SettingsEffect
}

private sealed interface DataLocationOperation {
    data object Pick : DataLocationOperation
    data object RetryCleanup : DataLocationOperation
    data class Schedule(
        val targetPath: String,
        val mode: AppDataLocationChangeMode,
    ) : DataLocationOperation
}

private data class ExecuteDataLocationOperation(
    val operation: DataLocationOperation,
) : SettingsIntent

private data object DataLocationIntentConsumed : SettingsIntent

class SettingsStore(
    private val repository: SettingsRepository,
    scope: CoroutineScope,
    private val appStorageGateway: AppStorageGateway = UnsupportedAppStorageGateway,
    private val appDataLocationPlatformService: AppDataLocationPlatformService =
        UnsupportedAppDataLocationPlatformService,
    private val deviceInfoGateway: DeviceInfoGateway = UnsupportedDeviceInfoGateway,
    private val lyricsShareFontLibraryPlatformService: LyricsShareFontLibraryPlatformService =
        UnsupportedLyricsShareFontLibraryPlatformService,
    private val lyricsShareFontPreferencesStore: LyricsShareFontPreferencesStore =
        UnsupportedLyricsShareFontPreferencesStore,
    private val vlcPathPickerPlatformService: VlcPathPickerPlatformService = UnsupportedVlcPathPickerPlatformService,
    private val appUpdateRepository: AppUpdateRepository = UnsupportedAppUpdateRepository,
    private val currentAppVersionName: String = BuildMetadata.appVersionName,
    private val desktopLyricsPlatformService: DesktopLyricsPlatformService =
        UnsupportedDesktopLyricsPlatformService,
) : BaseStore<SettingsState, SettingsIntent, SettingsEffect>(
    initialState = SettingsState(
        appLanguage = repository.appLanguage.value,
        autoOpenPlayerOnStartup = repository.autoOpenPlayerOnStartup.value,
        minimizeWindowOnClose = repository.minimizeWindowOnClose.value,
        currentDataRootPath = appDataLocationPlatformService.currentDataRootPath,
        pendingDataCleanupRootPath = appDataLocationPlatformService.pendingCleanupRootPath,
        supportsLyricsShareFontImport =
            lyricsShareFontLibraryPlatformService !== UnsupportedLyricsShareFontLibraryPlatformService,
    ),
    scope = scope,
) {
    private var desktopLyricsPermissionRequestPending = false
    private val appUpdateCheckMutex = Mutex()
    private val minimizeWindowOnCloseWriteMutex = Mutex()
    private val dataLocationOperationMutex = Mutex()
    private val minimizeWindowOnCloseRevision = MutableStateFlow(0L)
    private val pendingMinimizeWindowOnCloseWrites = MutableStateFlow(0)
    private var silentAppUpdateCheckStarted = false

    init {
        scope.launch {
            repository.appLanguage.collect { language ->
                updateState { it.copy(appLanguage = language) }
            }
        }

        scope.launch {
            repository.lyricsSources.collect { sources ->
                val managedLrcApi = sources
                    .filterIsInstance<LyricsSourceConfig>()
                    .firstOrNull(::isManagedLrcApiSource)
                val managedMusicmatch = sources
                    .filterIsInstance<WorkflowLyricsSourceConfig>()
                    .firstOrNull(::isManagedMusicmatchSource)
                updateState { state ->
                    state.copy(
                        sources = sources,
                        lrcApiUrl = when {
                            managedLrcApi != null -> extractManagedLrcApiUrl(managedLrcApi).orEmpty()
                            state.hasLrcApiSource -> ""
                            else -> state.lrcApiUrl
                        },
                        hasLrcApiSource = managedLrcApi != null,
                        musicmatchUserToken = when {
                            managedMusicmatch != null -> extractManagedMusicmatchUserToken(managedMusicmatch.rawJson).orEmpty()
                            state.hasMusicmatchSource -> ""
                            else -> state.musicmatchUserToken
                        },
                        hasMusicmatchSource = managedMusicmatch != null,
                    )
                }
            }
        }
        scope.launch {
            repository.useSambaCache.collect { enabled ->
                updateState { state -> state.copy(useSambaCache = enabled) }
            }
        }
        scope.launch {
            repository.showCompactPlayerLyrics.collect { enabled ->
                updateState { state -> state.copy(showCompactPlayerLyrics = enabled) }
            }
        }
        scope.launch {
            repository.showDesktopLyrics.collect { enabled ->
                val wasEnabled = state.value.showDesktopLyrics
                if (enabled && !desktopLyricsPlatformService.hasOverlayPermission()) {
                    repository.setShowDesktopLyrics(false)
                    desktopLyricsPlatformService.setDesktopLyricsEnabled(false)
                    desktopLyricsPlatformService.hideLyrics()
                    updateState {
                        it.copy(
                            showDesktopLyrics = false,
                            message = uiText(Res.string.desktop_lyrics_overlay_permission_disabled),
                        )
                    }
                } else {
                    if (enabled) {
                        desktopLyricsPlatformService.setDesktopLyricsEnabled(true)
                    } else if (wasEnabled) {
                        desktopLyricsPlatformService.setDesktopLyricsEnabled(false)
                        desktopLyricsPlatformService.hideLyrics()
                    }
                    updateState { state -> state.copy(showDesktopLyrics = enabled) }
                }
            }
        }
        scope.launch {
            repository.showMenuBarLyricsControls.collect { enabled ->
                updateState { state -> state.copy(showMenuBarLyricsControls = enabled) }
            }
        }
        scope.launch {
            repository.autoPlayOnStartup.collect { enabled ->
                updateState { state -> state.copy(autoPlayOnStartup = enabled) }
            }
        }
        scope.launch {
            repository.autoOpenPlayerOnStartup.collect { enabled ->
                updateState { state -> state.copy(autoOpenPlayerOnStartup = enabled) }
            }
        }
        scope.launch {
            repository.appDisplayScalePreset.collect { preset ->
                updateState { state -> state.copy(appDisplayScalePreset = preset) }
            }
        }
        scope.launch {
            repository.navidromeWifiAudioQuality.collect { quality ->
                updateState { state -> state.copy(navidromeWifiAudioQuality = quality) }
            }
        }
        scope.launch {
            repository.navidromeMobileAudioQuality.collect { quality ->
                updateState { state -> state.copy(navidromeMobileAudioQuality = quality) }
            }
        }
        scope.launch {
            repository.useAndroidExtensionDecoder.collect { enabled ->
                updateState { state -> state.copy(useAndroidExtensionDecoder = enabled) }
            }
        }
        scope.launch {
            repository.playerArtworkStyle.collect { style ->
                updateState { state -> state.copy(playerArtworkStyle = style) }
            }
        }
        scope.launch {
            repository.selectedTheme.collect { themeId ->
                updateState { state -> state.copy(selectedTheme = themeId) }
            }
        }
        scope.launch {
            repository.customThemeTokens.collect { tokens ->
                updateState { state ->
                    state.copy(
                        customThemeTokens = tokens,
                    )
                }
            }
        }
        scope.launch {
            repository.textPalettePreferences.collect { preferences ->
                updateState { state -> state.copy(textPalettePreferences = preferences) }
            }
        }
        scope.launch {
            repository.desktopVlcAutoDetectedPath.collect { path ->
                updateState { state -> state.copy(desktopVlcAutoDetectedPath = path) }
            }
        }
        scope.launch {
            repository.desktopVlcManualPath.collect { path ->
                updateState { state -> state.copy(desktopVlcManualPath = path) }
            }
        }
        scope.launch {
            repository.desktopVlcEffectivePath.collect { path ->
                updateState { state -> state.copy(desktopVlcEffectivePath = path) }
            }
        }
    }

    override fun reduceStateImmediately(intent: SettingsIntent): SettingsIntent {
        return when (intent) {
            is SettingsIntent.MinimizeWindowOnCloseChanged -> {
                val revision = minimizeWindowOnCloseRevision.updateAndGet { currentRevision ->
                    currentRevision + 1L
                }
                pendingMinimizeWindowOnCloseWrites.update { pendingWrites -> pendingWrites + 1 }
                updateState { state -> state.copy(minimizeWindowOnClose = intent.value) }
                intent.copy(revision = revision)
            }

            SettingsIntent.PickDataLocation -> claimDataLocationOperation(
                resolveOperation = { current ->
                    DataLocationOperation.Pick.takeIf {
                        !current.dataLocationBusy &&
                            current.pendingDataCleanupRootPath == null &&
                            current.pendingDataRootPath == null &&
                            !current.dataLocationRestartRequired
                    }
                },
            )

            SettingsIntent.CancelDataLocationSelection -> consumeDataLocationStateIntent { current ->
                current.copy(
                    pendingDataRootPath = null,
                    dataLocationDiscardConfirmationRequired = false,
                )
            }

            is SettingsIntent.RequestDataLocationChange -> {
                if (intent.mode == AppDataLocationChangeMode.Discard) {
                    consumeDataLocationStateIntent { current ->
                        if (
                            current.pendingDataRootPath != null &&
                            current.pendingDataCleanupRootPath == null &&
                            !current.dataLocationRestartRequired
                        ) {
                            current.copy(dataLocationDiscardConfirmationRequired = true)
                        } else {
                            current
                        }
                    }
                } else {
                    claimScheduledDataLocationChange(AppDataLocationChangeMode.Migrate)
                }
            }

            SettingsIntent.ConfirmDiscardDataLocation ->
                claimScheduledDataLocationChange(
                    mode = AppDataLocationChangeMode.Discard,
                    requireDiscardConfirmation = true,
                )

            SettingsIntent.CancelDiscardDataLocation -> consumeDataLocationStateIntent { current ->
                current.copy(dataLocationDiscardConfirmationRequired = false)
            }

            SettingsIntent.RetryDataLocationCleanup -> claimDataLocationOperation(
                resolveOperation = { current ->
                    DataLocationOperation.RetryCleanup.takeIf {
                        !current.dataLocationBusy &&
                            current.pendingDataCleanupRootPath != null &&
                            !current.dataLocationRestartRequired
                    }
                },
            )

            else -> intent
        }
    }

    private fun claimScheduledDataLocationChange(
        mode: AppDataLocationChangeMode,
        requireDiscardConfirmation: Boolean = false,
    ): SettingsIntent = claimDataLocationOperation(
        resolveOperation = { current ->
            val targetPath = current.pendingDataRootPath
            DataLocationOperation.Schedule(targetPath.orEmpty(), mode).takeIf {
                !current.dataLocationBusy &&
                    targetPath != null &&
                    current.pendingDataCleanupRootPath == null &&
                    !current.dataLocationRestartRequired &&
                    (!requireDiscardConfirmation || current.dataLocationDiscardConfirmationRequired)
            }
        },
        updateClaimedState = { current ->
            current.copy(
                dataLocationBusy = true,
                dataLocationDiscardConfirmationRequired = false,
            )
        },
    )

    private fun claimDataLocationOperation(
        resolveOperation: (SettingsState) -> DataLocationOperation?,
        updateClaimedState: (SettingsState) -> SettingsState = { current ->
            current.copy(dataLocationBusy = true)
        },
    ): SettingsIntent {
        if (!dataLocationOperationMutex.tryLock()) return DataLocationIntentConsumed
        val operation = resolveOperation(state.value)
        if (operation == null) {
            dataLocationOperationMutex.unlock()
            return DataLocationIntentConsumed
        }
        updateState(updateClaimedState)
        return ExecuteDataLocationOperation(operation)
    }

    private fun consumeDataLocationStateIntent(
        transform: (SettingsState) -> SettingsState,
    ): SettingsIntent {
        if (!dataLocationOperationMutex.tryLock()) return DataLocationIntentConsumed
        try {
            updateState(transform)
        } finally {
            dataLocationOperationMutex.unlock()
        }
        return DataLocationIntentConsumed
    }

    val persistedMinimizeWindowOnClose: Boolean
        get() = repository.minimizeWindowOnClose.value

    suspend fun awaitMinimizeWindowOnClosePersistence() {
        pendingMinimizeWindowOnCloseWrites.first { pendingWrites -> pendingWrites == 0 }
    }

    override fun onIntentHandlingCompleted(intent: SettingsIntent) {
        if (intent is SettingsIntent.MinimizeWindowOnCloseChanged) {
            pendingMinimizeWindowOnCloseWrites.update { pendingWrites ->
                (pendingWrites - 1).coerceAtLeast(0)
            }
        }
        if (intent is ExecuteDataLocationOperation) {
            try {
                updateState { current -> current.copy(dataLocationBusy = false) }
            } finally {
                dataLocationOperationMutex.unlock()
            }
        }
    }

    override suspend fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.UseSambaCacheChanged -> {
                repository.setUseSambaCache(intent.value)
                updateState { it.copy(useSambaCache = intent.value) }
            }

            is SettingsIntent.ShowCompactPlayerLyricsChanged -> {
                repository.setShowCompactPlayerLyrics(intent.value)
                updateState { it.copy(showCompactPlayerLyrics = intent.value) }
            }

            is SettingsIntent.ShowDesktopLyricsChanged -> {
                setShowDesktopLyrics(intent.value)
            }

            is SettingsIntent.ShowMenuBarLyricsControlsChanged -> {
                setShowMenuBarLyricsControls(intent.value)
            }

            SettingsIntent.RecheckDesktopLyricsPermission -> {
                recheckDesktopLyricsPermission()
            }

            is SettingsIntent.AutoPlayOnStartupChanged -> {
                repository.setAutoPlayOnStartup(intent.value)
                updateState { it.copy(autoPlayOnStartup = intent.value) }
            }

            is SettingsIntent.AutoOpenPlayerOnStartupChanged -> {
                repository.setAutoOpenPlayerOnStartup(intent.value)
                updateState { it.copy(autoOpenPlayerOnStartup = intent.value) }
            }

            is SettingsIntent.MinimizeWindowOnCloseChanged -> {
                persistMinimizeWindowOnClose(intent)
            }

            is SettingsIntent.AppLanguageChanged -> {
                try {
                    repository.setAppLanguage(intent.value)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    updateState { current ->
                        current.copy(message = error.uiErrorText(uiText(Res.string.language_preference_save_failed)))
                    }
                }
            }

            is SettingsIntent.AppDisplayScalePresetChanged -> {
                repository.setAppDisplayScalePreset(intent.value)
                updateState { it.copy(appDisplayScalePreset = intent.value) }
            }

            is SettingsIntent.NavidromeWifiAudioQualityChanged -> {
                repository.setNavidromeWifiAudioQuality(intent.value)
                updateState { it.copy(navidromeWifiAudioQuality = intent.value) }
            }

            is SettingsIntent.NavidromeMobileAudioQualityChanged -> {
                repository.setNavidromeMobileAudioQuality(intent.value)
                updateState { it.copy(navidromeMobileAudioQuality = intent.value) }
            }

            is SettingsIntent.AndroidExtensionDecoderChanged -> {
                repository.setUseAndroidExtensionDecoder(intent.value)
                updateState { it.copy(useAndroidExtensionDecoder = intent.value) }
            }

            is SettingsIntent.PlayerArtworkStyleChanged -> {
                repository.setPlayerArtworkStyle(intent.value)
                updateState { it.copy(playerArtworkStyle = intent.value) }
            }

            is SettingsIntent.ThemeSelected -> {
                repository.setSelectedTheme(intent.value)
                updateState { it.copy(selectedTheme = intent.value) }
            }

            is SettingsIntent.LoadStorageUsage -> loadStorageUsage(force = intent.force)

            is SettingsIntent.ClearStorageCategory -> clearStorageCategory(intent.category)

            is ExecuteDataLocationOperation -> {
                when (val operation = intent.operation) {
                    DataLocationOperation.Pick -> pickDataLocation()
                    DataLocationOperation.RetryCleanup -> retryDataLocationCleanup()
                    is DataLocationOperation.Schedule -> {
                        scheduleDataLocationChange(
                            targetPath = operation.targetPath,
                            mode = operation.mode,
                        )
                    }
                }
            }

            DataLocationIntentConsumed,
            SettingsIntent.PickDataLocation,
            SettingsIntent.CancelDataLocationSelection,
            is SettingsIntent.RequestDataLocationChange,
            SettingsIntent.ConfirmDiscardDataLocation,
            SettingsIntent.CancelDiscardDataLocation,
            SettingsIntent.RetryDataLocationCleanup,
            -> Unit

            SettingsIntent.ConfirmDataLocationRestart ->
                emitEffect(SettingsEffect.ExitApplicationRequested)

            is SettingsIntent.LoadDeviceInfo -> loadDeviceInfo(force = intent.force)

            SettingsIntent.CheckAppUpdate -> checkAppUpdate(silent = false)

            SettingsIntent.CheckAppUpdateSilently -> checkAppUpdate(silent = true)

            SettingsIntent.LoadLyricsShareImportedFonts -> loadLyricsShareImportedFonts()

            SettingsIntent.ImportLyricsShareFont -> importLyricsShareFont()

            is SettingsIntent.DeleteLyricsShareImportedFont -> deleteLyricsShareImportedFont(intent.fontKey)

            is SettingsIntent.ThemeTextPaletteSelected -> {
                repository.setTextPalette(intent.themeId, intent.value)
                updateState {
                    it.copy(
                        textPalettePreferences = it.textPalettePreferences.withThemePalette(intent.themeId, intent.value),
                    )
                }
            }

            is SettingsIntent.CustomThemeColorUpdated -> {
                val updatedTokens = state.value.customThemeTokens.withUpdatedColor(intent.role, intent.argb)
                repository.setCustomThemeTokens(updatedTokens)
                updateState {
                    it.copy(
                        customThemeTokens = updatedTokens,
                    )
                }
            }

            SettingsIntent.PickDesktopVlcPath -> {
                val result = vlcPathPickerPlatformService.pickVlcDirectory()
                val failure = result.exceptionOrNull()
                if (failure != null) {
                    updateState {
                        it.copy(
                            message = failure.uiErrorText(uiText(Res.string.vlc_path_selection_failed)),
                        )
                    }
                } else {
                    result.getOrNull()?.takeIf { it.isNotBlank() }?.let { selectedPath ->
                        repository.setDesktopVlcManualPath(selectedPath)
                        updateState {
                            it.copy(
                                desktopVlcManualPath = selectedPath,
                                desktopVlcEffectivePath = selectedPath,
                                message = uiText(Res.string.vlc_path_saved_restart_required),
                            )
                        }
                    }
                }
            }

            SettingsIntent.ClearDesktopVlcManualPath -> {
                repository.clearDesktopVlcManualPath()
                updateState {
                    it.copy(
                        desktopVlcManualPath = null,
                        desktopVlcEffectivePath = it.desktopVlcAutoDetectedPath,
                        message = uiText(Res.string.vlc_auto_detection_restored),
                    )
                }
            }

            is SettingsIntent.SelectConfig -> updateState {
                val config = intent.config
                when {
                    config != null && isManagedLrcApiSource(config) -> it.enterLrcApiState(config)
                    config != null -> config.toState()
                    else -> it.clearDirectEditor(message = null)
                }
            }

            is SettingsIntent.SelectLrcApi -> updateState { it.enterLrcApiState(intent.config) }

            is SettingsIntent.SelectMusicmatch -> updateState {
                it.copy(
                    editingId = null,
                    lrcApiUrl = it.lrcApiUrl,
                    hasLrcApiSource = it.hasLrcApiSource,
                    musicmatchUserToken = intent.config?.rawJson?.let(::extractManagedMusicmatchUserToken).orEmpty(),
                    hasMusicmatchSource = intent.config != null,
                    workflowJsonInput = "",
                    editingWorkflowId = null,
                    message = null,
                )
            }

            is SettingsIntent.ViewWorkflow -> updateState {
                val config = intent.config
                when {
                    config != null && isManagedMusicmatchSource(config) -> {
                        it.copy(
                            lrcApiUrl = it.lrcApiUrl,
                            hasLrcApiSource = it.hasLrcApiSource,
                            musicmatchUserToken = extractManagedMusicmatchUserToken(config.rawJson).orEmpty(),
                            hasMusicmatchSource = true,
                            workflowJsonInput = "",
                            editingWorkflowId = null,
                            message = null,
                        )
                    }

                    config == null -> {
                        if (it.editingWorkflowId != null) {
                            it.copy(
                                editingWorkflowId = null,
                                message = null,
                            )
                        } else {
                            it.copy(
                                workflowJsonInput = "",
                                message = null,
                            )
                        }
                    }

                    else -> {
                        val workflow = config
                        it.copy(
                            editingWorkflowId = workflow.id,
                            workflowJsonInput = rewriteWorkflowLyricsSourceEnabled(
                                rawJson = workflow.rawJson,
                                enabled = workflow.enabled,
                            ),
                            message = null,
                        )
                    }
                }
            }

            is SettingsIntent.LrcApiUrlChanged -> updateState { it.copy(lrcApiUrl = intent.value) }
            is SettingsIntent.MusicmatchUserTokenChanged -> updateState { it.copy(musicmatchUserToken = intent.value) }
            is SettingsIntent.NameChanged -> updateState { it.copy(name = intent.value) }
            is SettingsIntent.MethodChanged -> updateState { it.copy(method = intent.value) }
            is SettingsIntent.UrlChanged -> updateState { it.copy(urlTemplate = intent.value) }
            is SettingsIntent.HeadersChanged -> updateState { it.copy(headersTemplate = intent.value) }
            is SettingsIntent.QueryChanged -> updateState { it.copy(queryTemplate = intent.value) }
            is SettingsIntent.BodyChanged -> updateState { it.copy(bodyTemplate = intent.value) }
            is SettingsIntent.ResponseFormatChanged -> updateState { it.copy(responseFormat = intent.value) }
            is SettingsIntent.ExtractorChanged -> updateState { it.copy(extractor = intent.value) }
            is SettingsIntent.PriorityChanged -> updateState { it.copy(priority = intent.value) }
            is SettingsIntent.EnabledChanged -> updateState { it.copy(enabled = intent.value) }
            is SettingsIntent.WorkflowJsonChanged -> updateState {
                it.copy(
                    workflowJsonInput = intent.value,
                )
            }

            SettingsIntent.ImportWorkflow -> {
                val rawJson = state.value.workflowJsonInput.trim()
                if (rawJson.isBlank()) {
                    updateState { it.copy(message = uiText(Res.string.workflow_import_json_required)) }
                    return
                }
                val isEditingWorkflow = state.value.editingWorkflowId != null
                val imported = runCatching {
                    repository.saveWorkflowLyricsSource(
                        rawJson = rawJson,
                        editingId = state.value.editingWorkflowId,
                    )
                }
                updateState {
                    it.copy(
                        workflowJsonInput = imported.getOrNull()?.rawJson ?: it.workflowJsonInput,
                        editingWorkflowId = imported.getOrNull()?.id ?: it.editingWorkflowId,
                        message = imported.fold(
                            onSuccess = { config -> if (isEditingWorkflow) uiText(Res.string.workflow_source_saved) else uiText(Res.string.workflow_source_imported, config.name) },
                            onFailure = { error -> error.uiErrorText(uiText(Res.string.workflow_save_failed)) },
                        ),
                    )
                }
            }

            SettingsIntent.CreateNew -> {
                val config = state.value.toConfig(forceNew = true) ?: run {
                    updateState { it.copy(message = uiText(Res.string.lyrics_source_name_and_url_required)) }
                    return
                }
                val created = runCatching { repository.saveLyricsSource(config) }
                updateState { currentState ->
                    created.fold(
                        onSuccess = { config.toState(sources = currentState.sources, message = uiText(Res.string.lyrics_source_created)) },
                        onFailure = { error -> currentState.copy(message = error.uiErrorText(uiText(Res.string.lyrics_source_create_failed))) },
                    )
                }
            }

            SettingsIntent.CreateNewWorkflow -> {
                val rawJson = state.value.workflowJsonInput.trim()
                if (rawJson.isBlank()) {
                    updateState { it.copy(message = uiText(Res.string.workflow_import_json_required)) }
                    return
                }
                val preparedRawJson = runCatching {
                    state.value.prepareWorkflowDraftForNew(rawJson)
                }
                val created = preparedRawJson.fold(
                    onSuccess = { normalizedRawJson ->
                        runCatching { repository.saveWorkflowLyricsSource(rawJson = normalizedRawJson, editingId = null) }
                    },
                    onFailure = { error -> Result.failure(error) },
                )
                updateState { currentState ->
                    created.fold(
                        onSuccess = { config ->
                            currentState.copy(
                                workflowJsonInput = config.rawJson,
                                editingWorkflowId = config.id,
                                message = uiText(Res.string.workflow_source_created),
                            )
                        },
                        onFailure = { error ->
                            currentState.copy(message = error.uiErrorText(uiText(Res.string.workflow_create_failed)))
                        },
                    )
                }
            }

            SettingsIntent.SaveLrcApi -> {
                val url = state.value.lrcApiUrl.trim()
                if (url.isBlank()) {
                    updateState { it.copy(message = uiText(Res.string.lyrics_lrcapi_url_required)) }
                    return
                }
                val saved = runCatching {
                    repository.saveLyricsSource(buildManagedLrcApiConfig(url))
                }
                updateState { currentState ->
                    currentState.copy(
                        lrcApiUrl = url,
                        hasLrcApiSource = if (saved.isSuccess) true else currentState.hasLrcApiSource,
                        message = saved.fold(
                            onSuccess = { uiText(Res.string.lyrics_lrcapi_saved) },
                            onFailure = { error -> error.uiErrorText(uiText(Res.string.lyrics_lrcapi_save_failed)) },
                        ),
                    )
                }
            }

            SettingsIntent.ClearLrcApi -> {
                val saved = runCatching {
                    repository.saveLyricsSource(buildManagedLrcApiConfig(DEFAULT_LRCAPI_URL))
                }
                updateState { currentState ->
                    currentState.copy(
                        lrcApiUrl = if (saved.isSuccess) DEFAULT_LRCAPI_URL else currentState.lrcApiUrl,
                        hasLrcApiSource = if (saved.isSuccess) true else currentState.hasLrcApiSource,
                        message = saved.fold(
                            onSuccess = { uiText(Res.string.lyrics_lrcapi_defaults_restored) },
                            onFailure = { error -> error.uiErrorText(uiText(Res.string.lyrics_lrcapi_defaults_restore_failed)) },
                        ),
                    )
                }
            }

            SettingsIntent.SaveMusicmatch -> {
                val userToken = state.value.musicmatchUserToken.trim()
                if (userToken.isBlank()) {
                    updateState { it.copy(message = uiText(Res.string.lyrics_musicmatch_token_required)) }
                    return
                }
                val saved = runCatching {
                    repository.saveWorkflowLyricsSource(
                        rawJson = buildManagedMusicmatchWorkflowJson(userToken),
                        editingId = MANAGED_MUSICMATCH_SOURCE_ID.takeIf { state.value.hasMusicmatchSource },
                    )
                }
                updateState { currentState ->
                    currentState.copy(
                        musicmatchUserToken = userToken,
                        hasMusicmatchSource = if (saved.isSuccess) true else currentState.hasMusicmatchSource,
                        message = saved.fold(
                            onSuccess = { uiText(Res.string.lyrics_musicmatch_saved) },
                            onFailure = { error -> error.uiErrorText(uiText(Res.string.lyrics_musicmatch_save_failed)) },
                        ),
                    )
                }
            }

            SettingsIntent.ClearMusicmatch -> {
                if (state.value.hasMusicmatchSource) {
                    repository.deleteLyricsSource(MANAGED_MUSICMATCH_SOURCE_ID)
                }
                updateState {
                    it.copy(
                        musicmatchUserToken = "",
                        hasMusicmatchSource = false,
                        message = uiText(Res.string.lyrics_musicmatch_cleared),
                    )
                }
            }

            SettingsIntent.ResetCustomTheme -> {
                val tokens = defaultCustomThemeTokens()
                repository.setCustomThemeTokens(tokens)
                updateState {
                    it.copy(
                        customThemeTokens = tokens,
                        message = uiText(Res.string.theme_custom_colors_reset),
                    )
                }
            }

            is SettingsIntent.ToggleSourceEnabled -> {
                repository.setLyricsSourceEnabled(intent.sourceId, intent.enabled)
                updateState {
                    it.copy(message = if (intent.enabled) uiText(Res.string.lyrics_source_enabled_notice) else uiText(Res.string.lyrics_source_disabled_notice))
                }
            }

            is SettingsIntent.DeleteSource -> {
                repository.deleteLyricsSource(intent.sourceId)
                updateState {
                    val shouldClearDirect = it.editingId == intent.sourceId
                    val shouldClearWorkflow = it.editingWorkflowId == intent.sourceId
                    val shouldClearLrcApi = intent.sourceId == MANAGED_LRCAPI_SOURCE_ID
                    val shouldClearMusicmatch = intent.sourceId == MANAGED_MUSICMATCH_SOURCE_ID
                    if (shouldClearDirect) {
                        it.clearDirectEditor(
                            sources = it.sources,
                            useSambaCache = it.useSambaCache,
                            lrcApiUrl = if (shouldClearLrcApi) "" else it.lrcApiUrl,
                            hasLrcApiSource = if (shouldClearLrcApi) false else it.hasLrcApiSource,
                            musicmatchUserToken = if (shouldClearMusicmatch) "" else it.musicmatchUserToken,
                            hasMusicmatchSource = if (shouldClearMusicmatch) false else it.hasMusicmatchSource,
                            workflowJsonInput = if (shouldClearWorkflow) "" else it.workflowJsonInput,
                            editingWorkflowId = if (shouldClearWorkflow) null else it.editingWorkflowId,
                            message = uiText(Res.string.lyrics_source_deleted),
                        )
                    } else {
                        it.copy(
                            lrcApiUrl = if (shouldClearLrcApi) "" else it.lrcApiUrl,
                            hasLrcApiSource = if (shouldClearLrcApi) false else it.hasLrcApiSource,
                            musicmatchUserToken = if (shouldClearMusicmatch) "" else it.musicmatchUserToken,
                            hasMusicmatchSource = if (shouldClearMusicmatch) false else it.hasMusicmatchSource,
                            workflowJsonInput = if (shouldClearWorkflow) "" else it.workflowJsonInput,
                            editingWorkflowId = if (shouldClearWorkflow) null else it.editingWorkflowId,
                            message = uiText(Res.string.lyrics_source_deleted),
                        )
                    }
                }
            }

            SettingsIntent.Save -> {
                val config = state.value.toConfig() ?: run {
                    updateState { it.copy(message = uiText(Res.string.lyrics_source_name_and_url_required)) }
                    return
                }
                val saved = runCatching { repository.saveLyricsSource(config) }
                updateState { currentState ->
                    saved.fold(
                        onSuccess = { config.toState(sources = currentState.sources, message = uiText(Res.string.lyrics_source_saved)) },
                        onFailure = { error -> currentState.copy(message = error.uiErrorText(uiText(Res.string.lyrics_source_save_failed))) },
                    )
                }
            }

            SettingsIntent.Delete -> {
                val editingId = state.value.editingId ?: return
                repository.deleteLyricsSource(editingId)
                updateState {
                    it.clearDirectEditor(
                        sources = it.sources,
                        useSambaCache = it.useSambaCache,
                        lrcApiUrl = it.lrcApiUrl,
                        hasLrcApiSource = it.hasLrcApiSource,
                        musicmatchUserToken = it.musicmatchUserToken,
                        hasMusicmatchSource = it.hasMusicmatchSource,
                        workflowJsonInput = it.workflowJsonInput,
                        editingWorkflowId = it.editingWorkflowId,
                        message = uiText(Res.string.lyrics_source_deleted),
                    )
                }
            }

            SettingsIntent.ClearMessage -> updateState { it.copy(message = null) }
        }
    }

    private suspend fun persistMinimizeWindowOnClose(intent: SettingsIntent.MinimizeWindowOnCloseChanged) {
        minimizeWindowOnCloseWriteMutex.withLock {
            if (intent.revision != minimizeWindowOnCloseRevision.value) return@withLock

            try {
                repository.setMinimizeWindowOnClose(intent.value)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                updateState { currentState ->
                    if (intent.revision != minimizeWindowOnCloseRevision.value) {
                        currentState
                    } else {
                        currentState.copy(
                            minimizeWindowOnClose = repository.minimizeWindowOnClose.value,
                            message = uiText(Res.string.window_close_behavior_save_failed),
                        )
                    }
                }
            }
        }
    }

    private suspend fun setShowDesktopLyrics(enabled: Boolean) {
        if (!desktopLyricsPlatformService.isSupported) {
            repository.setShowDesktopLyrics(false)
            desktopLyricsPlatformService.setDesktopLyricsEnabled(false)
            desktopLyricsPlatformService.hideLyrics()
            updateState {
                it.copy(
                    showDesktopLyrics = false,
                    message = uiText(Res.string.desktop_lyrics_platform_unsupported),
                )
            }
            return
        }
        if (!enabled) {
            desktopLyricsPermissionRequestPending = false
            repository.setShowDesktopLyrics(false)
            desktopLyricsPlatformService.setDesktopLyricsEnabled(false)
            desktopLyricsPlatformService.hideLyrics()
            updateState { it.copy(showDesktopLyrics = false) }
            return
        }
        if (!desktopLyricsPlatformService.hasOverlayPermission()) {
            desktopLyricsPermissionRequestPending = true
            repository.setShowDesktopLyrics(false)
            updateState {
                it.copy(
                    showDesktopLyrics = false,
                    message = uiText(Res.string.desktop_lyrics_overlay_permission_required),
                )
            }
            val granted = desktopLyricsPlatformService.requestOverlayPermission()
            if (!granted) return
        }
        desktopLyricsPermissionRequestPending = false
        repository.setShowDesktopLyrics(true)
        updateState {
            it.copy(
                showDesktopLyrics = true,
                message = null,
            )
        }
    }

    private suspend fun setShowMenuBarLyricsControls(enabled: Boolean) {
        repository.setShowMenuBarLyricsControls(enabled)
        updateState {
            it.copy(
                showMenuBarLyricsControls = enabled,
                message = null,
            )
        }
    }

    private suspend fun recheckDesktopLyricsPermission() {
        if (!desktopLyricsPlatformService.isSupported) return
        val hasPermission = desktopLyricsPlatformService.hasOverlayPermission()
        when {
            desktopLyricsPermissionRequestPending && hasPermission -> {
                desktopLyricsPermissionRequestPending = false
                repository.setShowDesktopLyrics(true)
                updateState {
                    it.copy(
                        showDesktopLyrics = true,
                        message = uiText(Res.string.desktop_lyrics_enabled),
                    )
                }
            }

            state.value.showDesktopLyrics && !hasPermission -> {
                desktopLyricsPermissionRequestPending = false
                repository.setShowDesktopLyrics(false)
                desktopLyricsPlatformService.setDesktopLyricsEnabled(false)
                desktopLyricsPlatformService.hideLyrics()
                updateState {
                    it.copy(
                        showDesktopLyrics = false,
                        message = uiText(Res.string.desktop_lyrics_overlay_permission_disabled),
                    )
                }
            }
        }
    }

    private suspend fun loadStorageUsage(force: Boolean) {
        val current = state.value
        if (current.storageLoading) return
        if (!force && current.storageLoaded) return
        updateState {
            it.copy(
                storageLoading = true,
                clearingStorageCategory = null,
            )
        }
        val result = appStorageGateway.loadStorageSnapshot()
        updateState { latest ->
            result.fold(
                onSuccess = { snapshot ->
                    latest.copy(
                        storageSnapshot = snapshot,
                        storageLoading = false,
                        storageLoaded = true,
                    )
                },
                onFailure = { error ->
                    latest.copy(
                        storageLoading = false,
                        message = error.uiErrorText(uiText(Res.string.storage_usage_calculation_failed)),
                    )
                },
            )
        }
    }

    private suspend fun clearStorageCategory(category: AppStorageCategory) {
        val current = state.value
        if (current.storageLoading || current.clearingStorageCategory != null) return
        updateState { it.copy(clearingStorageCategory = category) }
        val clearResult = appStorageGateway.clearCategory(category)
        if (clearResult.isFailure) {
            updateState {
                it.copy(
                    clearingStorageCategory = null,
                    message = clearResult.exceptionOrNull()?.uiErrorText(uiText(Res.string.storage_category_cleanup_failed, category.displayName())) ?: uiText(Res.string.storage_category_cleanup_failed, category.displayName()),
                )
            }
            return
        }
        val snapshotResult = appStorageGateway.loadStorageSnapshot()
        updateState { latest ->
            snapshotResult.fold(
                onSuccess = { snapshot ->
                    latest.copy(
                        storageSnapshot = snapshot,
                        storageLoaded = true,
                        clearingStorageCategory = null,
                        message = uiText(Res.string.storage_category_cleared, category.displayName()),
                    )
                },
                onFailure = { error ->
                    latest.copy(
                        clearingStorageCategory = null,
                        message = error.uiErrorText(uiText(Res.string.storage_category_cleared_refresh_failed, category.displayName())),
                    )
                },
            )
        }
    }

    private suspend fun pickDataLocation() {
        val result = appDataLocationPlatformService.pickTargetDataRoot()
        updateState { current ->
            result.fold(
                onSuccess = { selectedPath ->
                    current.copy(
                        pendingDataRootPath = selectedPath?.takeIf { it.isNotBlank() },
                    )
                },
                onFailure = { error ->
                    current.copy(
                        message = error.uiErrorText(uiText(Res.string.data_location_selection_failed)),
                    )
                },
            )
        }
    }

    private suspend fun retryDataLocationCleanup() {
        val result = appDataLocationPlatformService.retryPendingCleanup()
        updateState { current ->
            result.fold(
                onSuccess = {
                    current.copy(
                        pendingDataCleanupRootPath = appDataLocationPlatformService.pendingCleanupRootPath,
                        message = uiText(Res.string.data_location_old_folder_cleaned),
                    )
                },
                onFailure = { error ->
                    current.copy(
                        pendingDataCleanupRootPath = appDataLocationPlatformService.pendingCleanupRootPath,
                        message = error.uiErrorText(uiText(Res.string.data_location_old_folder_cleanup_failed)),
                    )
                },
            )
        }
    }

    private suspend fun scheduleDataLocationChange(
        targetPath: String,
        mode: AppDataLocationChangeMode,
    ) {
        val result = appDataLocationPlatformService.scheduleChange(targetPath, mode)
        updateState { current ->
            result.fold(
                onSuccess = {
                    current.copy(
                        pendingDataRootPath = null,
                        dataLocationRestartRequired = true,
                    )
                },
                onFailure = { error ->
                    current.copy(
                        message = error.uiErrorText(uiText(Res.string.data_location_save_failed)),
                    )
                },
            )
        }
    }

    private suspend fun loadDeviceInfo(force: Boolean) {
        val current = state.value
        if (current.deviceInfoLoading) return
        if (!force && current.deviceInfoLoaded) return
        updateState { it.copy(deviceInfoLoading = true) }
        val result = deviceInfoGateway.loadDeviceInfoSnapshot()
        updateState { latest ->
            result.fold(
                onSuccess = { snapshot ->
                    latest.copy(
                        deviceInfoSnapshot = snapshot,
                        deviceInfoLoading = false,
                        deviceInfoLoaded = true,
                    )
                },
                onFailure = { error ->
                    latest.copy(
                        deviceInfoLoading = false,
                        message = error.uiErrorText(uiText(Res.string.device_info_read_failed)),
                    )
                },
            )
        }
    }

    private suspend fun checkAppUpdate(silent: Boolean) {
        if (silent) {
            if (silentAppUpdateCheckStarted) return
            silentAppUpdateCheckStarted = true
        }
        if (!appUpdateCheckMutex.tryLock()) return
        try {
            updateState {
                it.copy(
                    appUpdateChecking = true,
                    appUpdateError = if (silent) it.appUpdateError else null,
                    message = if (silent) it.message else null,
                )
            }
            val result = appUpdateRepository.latestRelease()
            updateState { latest ->
                result.fold(
                    onSuccess = { release ->
                        val hasNewVersion = isAppReleaseNewer(
                            currentVersionName = currentAppVersionName,
                            releaseTagName = release.tagName,
                        )
                        latest.copy(
                            appUpdateChecking = false,
                            appUpdateLatestRelease = release,
                            appUpdateHasNewVersion = hasNewVersion,
                            appUpdateError = null,
                            message = when {
                                silent -> latest.message
                                hasNewVersion -> uiText(Res.string.update_new_version_available, release.tagName)
                                else -> uiText(Res.string.update_already_latest)
                            },
                        )
                    },
                    onFailure = { error ->
                        val message = error.uiErrorText(uiText(Res.string.update_check_failed))
                        latest.copy(
                            appUpdateChecking = false,
                            appUpdateLatestRelease = latest.appUpdateLatestRelease,
                            appUpdateHasNewVersion = latest.appUpdateHasNewVersion,
                            appUpdateError = if (silent) null else message,
                            message = if (silent) latest.message else message,
                        )
                    },
                )
            }
        } finally {
            appUpdateCheckMutex.unlock()
        }
    }

    private suspend fun loadLyricsShareImportedFonts(force: Boolean = false) {
        val current = state.value
        if (!current.supportsLyricsShareFontImport) return
        if (current.lyricsShareFontsLoading || current.importingLyricsShareFont || current.deletingLyricsShareFontKey != null) {
            return
        }
        if (!force && current.importedLyricsShareFonts.isNotEmpty()) return
        updateState {
            it.copy(
                lyricsShareFontsLoading = true,
            )
        }
        val result = lyricsShareFontLibraryPlatformService.listImportedFonts()
        updateState { latest ->
            result.fold(
                onSuccess = { fonts ->
                    latest.copy(
                        importedLyricsShareFonts = fonts,
                        lyricsShareFontsLoading = false,
                    )
                },
                onFailure = { error ->
                    latest.copy(
                        lyricsShareFontsLoading = false,
                        message = error.uiErrorText(uiText(Res.string.font_imported_list_read_failed)),
                    )
                },
            )
        }
    }

    private suspend fun importLyricsShareFont() {
        val current = state.value
        if (!current.supportsLyricsShareFontImport) return
        if (current.importingLyricsShareFont || current.lyricsShareFontsLoading || current.deletingLyricsShareFontKey != null) {
            return
        }
        updateState { it.copy(importingLyricsShareFont = true) }
        val result = lyricsShareFontLibraryPlatformService.importFont()
        val importedOption = result.getOrNull()
        if (result.isSuccess && importedOption != null) {
            emitEffect(SettingsEffect.LyricsShareFontsChanged)
            val listResult = lyricsShareFontLibraryPlatformService.listImportedFonts()
            updateState { latest ->
                listResult.fold(
                    onSuccess = { fonts ->
                        latest.copy(
                            importedLyricsShareFonts = fonts,
                            importingLyricsShareFont = false,
                            message = uiText(Res.string.font_imported),
                        )
                    },
                    onFailure = { error ->
                        latest.copy(
                            importingLyricsShareFont = false,
                            message = error.uiErrorText(uiText(Res.string.font_imported_refresh_failed)),
                        )
                    },
                )
            }
        } else {
            updateState { latest ->
                latest.copy(
                    importingLyricsShareFont = false,
                    message = result.exceptionOrNull()?.uiErrorText()
                        ?: if (importedOption == null) uiText(Res.string.import_cancelled) else uiText(Res.string.font_import_failed),
                )
            }
        }
    }

    private suspend fun deleteLyricsShareImportedFont(fontKey: String) {
        val current = state.value
        if (!current.supportsLyricsShareFontImport) return
        if (current.lyricsShareFontsLoading || current.importingLyricsShareFont || current.deletingLyricsShareFontKey != null) {
            return
        }
        updateState { it.copy(deletingLyricsShareFontKey = fontKey) }
        val deleteResult = lyricsShareFontLibraryPlatformService.deleteImportedFont(fontKey)
        if (deleteResult.isFailure) {
            updateState {
                it.copy(
                    deletingLyricsShareFontKey = null,
                    message = deleteResult.exceptionOrNull()?.uiErrorText(uiText(Res.string.font_delete_failed)) ?: uiText(Res.string.font_delete_failed),
                )
            }
            return
        }
        emitEffect(SettingsEffect.LyricsShareFontsChanged)
        if (lyricsShareFontPreferencesStore.selectedLyricsShareFontKey.value == fontKey) {
            lyricsShareFontPreferencesStore.setSelectedLyricsShareFontKey(null)
        }
        val listResult = lyricsShareFontLibraryPlatformService.listImportedFonts()
        updateState { latest ->
            listResult.fold(
                onSuccess = { fonts ->
                    latest.copy(
                        importedLyricsShareFonts = fonts,
                        deletingLyricsShareFontKey = null,
                        message = uiText(Res.string.font_deleted),
                    )
                },
                onFailure = { error ->
                    latest.copy(
                        deletingLyricsShareFontKey = null,
                        message = error.uiErrorText(uiText(Res.string.font_deleted_refresh_failed)),
                    )
                },
            )
        }
    }

    private fun SettingsState.toConfig(forceNew: Boolean = false): LyricsSourceConfig? {
        if (name.isBlank() || urlTemplate.isBlank()) return null
        return LyricsSourceConfig(
            id = if (!forceNew && editingId != null) editingId else newLyricsSourceId("lyrics"),
            name = name,
            method = RequestMethod.GET,
            urlTemplate = urlTemplate,
            headersTemplate = headersTemplate,
            queryTemplate = queryTemplate,
            bodyTemplate = "",
            responseFormat = LyricsResponseFormat.JSON,
            extractor = extractor,
            priority = priority.toIntOrNull() ?: 0,
            enabled = enabled,
        )
    }

    private fun LyricsSourceConfig.toState(
            sources: List<LyricsSourceDefinition> = state.value.sources,
            message: UiText? = null,
    ): SettingsState {
        return state.value.copy(
            sources = sources,
            useSambaCache = state.value.useSambaCache,
            editingId = id,
            name = name,
            method = RequestMethod.GET,
            urlTemplate = urlTemplate,
            headersTemplate = headersTemplate,
            queryTemplate = queryTemplate,
            bodyTemplate = "",
            responseFormat = LyricsResponseFormat.JSON,
            extractor = extractor,
            priority = priority.toString(),
            enabled = enabled,
            message = message,
        )
    }

    private fun SettingsState.prepareWorkflowDraftForNew(rawJson: String): String {
        val currentEditingWorkflowId = editingWorkflowId ?: return rawJson
        val parsed = parseWorkflowLyricsSourceConfig(rawJson)
        if (parsed.id != currentEditingWorkflowId) {
            return rawJson
        }
        return rewriteWorkflowLyricsSourceId(
            rawJson = rawJson,
            newId = newLyricsSourceId("workflow"),
        )
    }

    private fun SettingsState.enterLrcApiState(config: LyricsSourceConfig?): SettingsState {
        return copy(
            editingId = null,
            name = "",
            method = RequestMethod.GET,
            urlTemplate = "",
            headersTemplate = "",
            queryTemplate = "",
            bodyTemplate = "",
            responseFormat = LyricsResponseFormat.JSON,
            extractor = LRCLIB_JSON_MAP_EXTRACTOR,
            priority = "0",
            enabled = true,
            lrcApiUrl = config?.let(::extractManagedLrcApiUrl).orEmpty(),
            hasLrcApiSource = config != null,
            workflowJsonInput = "",
            editingWorkflowId = null,
            message = null,
        )
    }

    private fun AppThemeTokens.withUpdatedColor(
        role: CustomThemeColorRole,
        argb: Int,
    ): AppThemeTokens {
        return when (role) {
            CustomThemeColorRole.Background -> copy(backgroundArgb = argb)
            CustomThemeColorRole.Accent -> copy(accentArgb = argb)
            CustomThemeColorRole.Focus -> copy(focusArgb = argb)
        }
    }

    private fun AppStorageCategory.displayName(): UiText {
        return when (this) {
            AppStorageCategory.Artwork -> uiText(Res.string.storage_artwork_cache)
            AppStorageCategory.PlaybackCache -> uiText(Res.string.storage_playback_cache)
            AppStorageCategory.OfflineDownloads -> uiText(Res.string.storage_offline_music)
            AppStorageCategory.LyricsShareTemp -> uiText(Res.string.storage_lyrics_share_temporary_files)
            AppStorageCategory.TagEditTemp -> uiText(Res.string.storage_tags_temporary_files)
        }
    }

    private fun SettingsState.clearDirectEditor(
        sources: List<LyricsSourceDefinition> = this.sources,
        useSambaCache: Boolean = this.useSambaCache,
        lrcApiUrl: String = this.lrcApiUrl,
        hasLrcApiSource: Boolean = this.hasLrcApiSource,
        musicmatchUserToken: String = this.musicmatchUserToken,
        hasMusicmatchSource: Boolean = this.hasMusicmatchSource,
        workflowJsonInput: String = this.workflowJsonInput,
        editingWorkflowId: String? = this.editingWorkflowId,
        message: UiText? = this.message,
    ): SettingsState {
        return copy(
            sources = sources,
            useSambaCache = useSambaCache,
            lrcApiUrl = lrcApiUrl,
            hasLrcApiSource = hasLrcApiSource,
            musicmatchUserToken = musicmatchUserToken,
            hasMusicmatchSource = hasMusicmatchSource,
            editingId = null,
            name = "",
            method = RequestMethod.GET,
            urlTemplate = "",
            headersTemplate = "",
            queryTemplate = "",
            bodyTemplate = "",
            responseFormat = LyricsResponseFormat.JSON,
            extractor = LRCLIB_JSON_MAP_EXTRACTOR,
            priority = "0",
            enabled = true,
            workflowJsonInput = workflowJsonInput,
            editingWorkflowId = editingWorkflowId,
            message = message,
        )
    }
}

private fun newLyricsSourceId(prefix: String): String {
    return "$prefix-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(1000, 9999)}"
}
