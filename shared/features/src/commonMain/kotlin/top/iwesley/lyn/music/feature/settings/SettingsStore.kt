package top.iwesley.lyn.music.feature.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock
import top.iwesley.lyn.music.core.model.AppStorageCategory
import top.iwesley.lyn.music.core.model.AppStorageGateway
import top.iwesley.lyn.music.core.model.AppStorageSnapshot
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.DeviceInfoGateway
import top.iwesley.lyn.music.core.model.DeviceInfoSnapshot
import top.iwesley.lyn.music.core.model.LyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.LyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSourceDefinition
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.UnsupportedAppStorageGateway
import top.iwesley.lyn.music.core.model.UnsupportedDeviceInfoGateway
import top.iwesley.lyn.music.core.model.UnsupportedLyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.UnsupportedLyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedVlcPathPickerPlatformService
import top.iwesley.lyn.music.core.model.VlcPathPickerPlatformService
import top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig
import top.iwesley.lyn.music.core.model.defaultCustomThemeTokens
import top.iwesley.lyn.music.core.model.defaultThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.withThemePalette
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.LRCLIB_JSON_MAP_EXTRACTOR
import top.iwesley.lyn.music.data.repository.SettingsRepository
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
    val sources: List<LyricsSourceDefinition> = emptyList(),
    val useSambaCache: Boolean = false,
    val showCompactPlayerLyrics: Boolean = false,
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
    val deviceInfoSnapshot: DeviceInfoSnapshot? = null,
    val deviceInfoLoading: Boolean = false,
    val deviceInfoLoaded: Boolean = false,
    val desktopVlcAutoDetectedPath: String? = null,
    val desktopVlcManualPath: String? = null,
    val desktopVlcEffectivePath: String? = null,
    val message: String? = null,
)

sealed interface SettingsIntent {
    data class UseSambaCacheChanged(val value: Boolean) : SettingsIntent
    data class ShowCompactPlayerLyricsChanged(val value: Boolean) : SettingsIntent
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
    data class LoadDeviceInfo(val force: Boolean = false) : SettingsIntent
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
}

class SettingsStore(
    private val repository: SettingsRepository,
    scope: CoroutineScope,
    private val appStorageGateway: AppStorageGateway = UnsupportedAppStorageGateway,
    private val deviceInfoGateway: DeviceInfoGateway = UnsupportedDeviceInfoGateway,
    private val lyricsShareFontLibraryPlatformService: LyricsShareFontLibraryPlatformService =
        UnsupportedLyricsShareFontLibraryPlatformService,
    private val lyricsShareFontPreferencesStore: LyricsShareFontPreferencesStore =
        UnsupportedLyricsShareFontPreferencesStore,
    private val vlcPathPickerPlatformService: VlcPathPickerPlatformService = UnsupportedVlcPathPickerPlatformService,
) : BaseStore<SettingsState, SettingsIntent, SettingsEffect>(
    initialState = SettingsState(
        supportsLyricsShareFontImport =
            lyricsShareFontLibraryPlatformService !== UnsupportedLyricsShareFontLibraryPlatformService,
    ),
    scope = scope,
) {
    init {
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

            is SettingsIntent.ThemeSelected -> {
                repository.setSelectedTheme(intent.value)
                updateState { it.copy(selectedTheme = intent.value) }
            }

            is SettingsIntent.LoadStorageUsage -> loadStorageUsage(force = intent.force)

            is SettingsIntent.ClearStorageCategory -> clearStorageCategory(intent.category)

            is SettingsIntent.LoadDeviceInfo -> loadDeviceInfo(force = intent.force)

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
                            message = failure.message ?: "选择 VLC 路径失败。",
                        )
                    }
                } else {
                    result.getOrNull()?.takeIf { it.isNotBlank() }?.let { selectedPath ->
                        repository.setDesktopVlcManualPath(selectedPath)
                        updateState {
                            it.copy(
                                desktopVlcManualPath = selectedPath,
                                desktopVlcEffectivePath = selectedPath,
                                message = "VLC 路径已保存，将在下次启动后生效。",
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
                        message = "已恢复自动识别，将在下次启动后生效。",
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
                    updateState { it.copy(message = "请先粘贴 Workflow JSON。") }
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
                            onSuccess = { config -> if (isEditingWorkflow) "Workflow 源已保存。" else "Workflow 源 ${config.name} 已导入。" },
                            onFailure = { error -> error.message ?: "Workflow 保存失败。" },
                        ),
                    )
                }
            }

            SettingsIntent.CreateNew -> {
                val config = state.value.toConfig(forceNew = true) ?: run {
                    updateState { it.copy(message = "请至少填写歌词源名称和 URL。") }
                    return
                }
                val created = runCatching { repository.saveLyricsSource(config) }
                updateState { currentState ->
                    created.fold(
                        onSuccess = { config.toState(sources = currentState.sources, message = "歌词源已新建。") },
                        onFailure = { error -> currentState.copy(message = error.message ?: "歌词源新建失败。") },
                    )
                }
            }

            SettingsIntent.CreateNewWorkflow -> {
                val rawJson = state.value.workflowJsonInput.trim()
                if (rawJson.isBlank()) {
                    updateState { it.copy(message = "请先粘贴 Workflow JSON。") }
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
                                message = "Workflow 源已新建。",
                            )
                        },
                        onFailure = { error ->
                            currentState.copy(message = error.message ?: "Workflow 新建失败。")
                        },
                    )
                }
            }

            SettingsIntent.SaveLrcApi -> {
                val url = state.value.lrcApiUrl.trim()
                if (url.isBlank()) {
                    updateState { it.copy(message = "请填写 LrcAPI 请求地址。") }
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
                            onSuccess = { "LrcAPI 已保存。" },
                            onFailure = { error -> error.message ?: "LrcAPI 保存失败。" },
                        ),
                    )
                }
            }

            SettingsIntent.ClearLrcApi -> {
                if (state.value.hasLrcApiSource) {
                    repository.deleteLyricsSource(MANAGED_LRCAPI_SOURCE_ID)
                }
                updateState {
                    it.copy(
                        lrcApiUrl = "",
                        hasLrcApiSource = false,
                        message = "LrcAPI 已清除。",
                    )
                }
            }

            SettingsIntent.SaveMusicmatch -> {
                val userToken = state.value.musicmatchUserToken.trim()
                if (userToken.isBlank()) {
                    updateState { it.copy(message = "请填写 Musicmatch usertoken。") }
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
                            onSuccess = { "Musicmatch 已保存。" },
                            onFailure = { error -> error.message ?: "Musicmatch 保存失败。" },
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
                        message = "Musicmatch 已清除。",
                    )
                }
            }

            SettingsIntent.ResetCustomTheme -> {
                val tokens = defaultCustomThemeTokens()
                repository.setCustomThemeTokens(tokens)
                updateState {
                    it.copy(
                        customThemeTokens = tokens,
                        message = "自定义主题已重置。",
                    )
                }
            }

            is SettingsIntent.ToggleSourceEnabled -> {
                repository.setLyricsSourceEnabled(intent.sourceId, intent.enabled)
                updateState {
                    it.copy(message = if (intent.enabled) "歌词源已启用。" else "歌词源已停用。")
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
                            message = "歌词源已删除。",
                        )
                    } else {
                        it.copy(
                            lrcApiUrl = if (shouldClearLrcApi) "" else it.lrcApiUrl,
                            hasLrcApiSource = if (shouldClearLrcApi) false else it.hasLrcApiSource,
                            musicmatchUserToken = if (shouldClearMusicmatch) "" else it.musicmatchUserToken,
                            hasMusicmatchSource = if (shouldClearMusicmatch) false else it.hasMusicmatchSource,
                            workflowJsonInput = if (shouldClearWorkflow) "" else it.workflowJsonInput,
                            editingWorkflowId = if (shouldClearWorkflow) null else it.editingWorkflowId,
                            message = "歌词源已删除。",
                        )
                    }
                }
            }

            SettingsIntent.Save -> {
                val config = state.value.toConfig() ?: run {
                    updateState { it.copy(message = "请至少填写歌词源名称和 URL。") }
                    return
                }
                val saved = runCatching { repository.saveLyricsSource(config) }
                updateState { currentState ->
                    saved.fold(
                        onSuccess = { config.toState(sources = currentState.sources, message = "歌词源已保存。") },
                        onFailure = { error -> currentState.copy(message = error.message ?: "歌词源保存失败。") },
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
                        message = "歌词源已删除。",
                    )
                }
            }

            SettingsIntent.ClearMessage -> updateState { it.copy(message = null) }
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
                        message = error.message ?: "缓存统计失败。",
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
                    message = clearResult.exceptionOrNull()?.message ?: "${category.displayName()}清除失败。",
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
                        message = "${category.displayName()}已清除。",
                    )
                },
                onFailure = { error ->
                    latest.copy(
                        clearingStorageCategory = null,
                        message = error.message ?: "${category.displayName()}已清除，但刷新失败。",
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
                        message = error.message ?: "读取设备信息失败。",
                    )
                },
            )
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
                        message = error.message ?: "读取已导入字体失败。",
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
                            message = "字体已导入。",
                        )
                    },
                    onFailure = { error ->
                        latest.copy(
                            importingLyricsShareFont = false,
                            message = error.message ?: "字体已导入，但刷新列表失败。",
                        )
                    },
                )
            }
        } else {
            updateState { latest ->
                latest.copy(
                    importingLyricsShareFont = false,
                    message = result.exceptionOrNull()?.message
                        ?: if (importedOption == null) "已取消导入。" else "字体导入失败。",
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
                    message = deleteResult.exceptionOrNull()?.message ?: "删除字体失败。",
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
                        message = "字体已删除。",
                    )
                },
                onFailure = { error ->
                    latest.copy(
                        deletingLyricsShareFontKey = null,
                        message = error.message ?: "字体已删除，但刷新列表失败。",
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
            message: String? = null,
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

    private fun AppStorageCategory.displayName(): String {
        return when (this) {
            AppStorageCategory.Artwork -> "封面缓存"
            AppStorageCategory.PlaybackCache -> "播放缓存"
            AppStorageCategory.LyricsShareTemp -> "歌词分享临时文件"
            AppStorageCategory.TagEditTemp -> "标签编辑临时文件"
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
        message: String? = this.message,
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
