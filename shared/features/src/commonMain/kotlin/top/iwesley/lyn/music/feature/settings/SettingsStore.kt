package top.iwesley.lyn.music.feature.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSourceDefinition
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.LRCLIB_SYNCED_JSON_MAP_EXTRACTOR
import top.iwesley.lyn.music.data.repository.SettingsRepository
import top.iwesley.lyn.music.domain.MANAGED_MUSICMATCH_SOURCE_ID
import top.iwesley.lyn.music.domain.buildManagedMusicmatchWorkflowJson
import top.iwesley.lyn.music.domain.extractManagedMusicmatchUserToken
import top.iwesley.lyn.music.domain.isManagedMusicmatchSource
import top.iwesley.lyn.music.domain.parseWorkflowLyricsSourceConfig
import top.iwesley.lyn.music.domain.rewriteWorkflowLyricsSourceId

data class SettingsState(
    val sources: List<LyricsSourceDefinition> = emptyList(),
    val useSambaCache: Boolean = true,
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
    val extractor: String = LRCLIB_SYNCED_JSON_MAP_EXTRACTOR,
    val priority: String = "0",
    val enabled: Boolean = true,
    val workflowJsonInput: String = "",
    val editingWorkflowId: String? = null,
    val message: String? = null,
)

sealed interface SettingsIntent {
    data class UseSambaCacheChanged(val value: Boolean) : SettingsIntent
    data class SelectConfig(val config: LyricsSourceConfig?) : SettingsIntent
    data class SelectMusicmatch(val config: WorkflowLyricsSourceConfig?) : SettingsIntent
    data class ViewWorkflow(val config: WorkflowLyricsSourceConfig?) : SettingsIntent
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
    data object ImportWorkflow : SettingsIntent
    data object CreateNew : SettingsIntent
    data object CreateNewWorkflow : SettingsIntent
    data object SaveMusicmatch : SettingsIntent
    data object ClearMusicmatch : SettingsIntent
    data class ToggleSourceEnabled(val sourceId: String, val enabled: Boolean) : SettingsIntent
    data class DeleteSource(val sourceId: String) : SettingsIntent
    data object Save : SettingsIntent
    data object Delete : SettingsIntent
    data object ClearMessage : SettingsIntent
}

sealed interface SettingsEffect

class SettingsStore(
    private val repository: SettingsRepository,
    scope: CoroutineScope,
) : BaseStore<SettingsState, SettingsIntent, SettingsEffect>(
    initialState = SettingsState(),
    scope = scope,
) {
    init {
        scope.launch {
            repository.lyricsSources.collect { sources ->
                val managedMusicmatch = sources
                    .filterIsInstance<WorkflowLyricsSourceConfig>()
                    .firstOrNull(::isManagedMusicmatchSource)
                updateState { state ->
                    state.copy(
                        sources = sources,
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
    }

    override suspend fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.UseSambaCacheChanged -> {
                repository.setUseSambaCache(intent.value)
                updateState { it.copy(useSambaCache = intent.value) }
            }

            is SettingsIntent.SelectConfig -> updateState {
                intent.config?.toState() ?: if (it.editingId != null) {
                    it.copy(
                        editingId = null,
                        musicmatchUserToken = it.musicmatchUserToken,
                        hasMusicmatchSource = it.hasMusicmatchSource,
                        message = null,
                    )
                } else {
                    SettingsState(
                        sources = it.sources,
                        useSambaCache = it.useSambaCache,
                        musicmatchUserToken = it.musicmatchUserToken,
                        hasMusicmatchSource = it.hasMusicmatchSource,
                        workflowJsonInput = it.workflowJsonInput,
                        editingWorkflowId = it.editingWorkflowId,
                    )
                }
            }

            is SettingsIntent.SelectMusicmatch -> updateState {
                it.copy(
                    editingId = null,
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
                            workflowJsonInput = workflow.rawJson,
                            message = null,
                        )
                    }
                }
            }

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
                    val shouldClearMusicmatch = intent.sourceId == MANAGED_MUSICMATCH_SOURCE_ID
                    if (shouldClearDirect) {
                        SettingsState(
                            sources = it.sources,
                            useSambaCache = it.useSambaCache,
                            musicmatchUserToken = if (shouldClearMusicmatch) "" else it.musicmatchUserToken,
                            hasMusicmatchSource = if (shouldClearMusicmatch) false else it.hasMusicmatchSource,
                            workflowJsonInput = if (shouldClearWorkflow) "" else it.workflowJsonInput,
                            editingWorkflowId = if (shouldClearWorkflow) null else it.editingWorkflowId,
                            message = "歌词源已删除。",
                        )
                    } else {
                        it.copy(
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
                    SettingsState(
                        sources = it.sources,
                        useSambaCache = it.useSambaCache,
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

    private fun SettingsState.toConfig(forceNew: Boolean = false): LyricsSourceConfig? {
        if (name.isBlank() || urlTemplate.isBlank()) return null
        return LyricsSourceConfig(
            id = if (!forceNew && editingId != null) editingId else newLyricsSourceId("lyrics"),
            name = name,
            method = method,
            urlTemplate = urlTemplate,
            headersTemplate = headersTemplate,
            queryTemplate = queryTemplate,
            bodyTemplate = bodyTemplate,
            responseFormat = responseFormat,
            extractor = extractor,
            priority = priority.toIntOrNull() ?: 0,
            enabled = enabled,
        )
    }

    private fun LyricsSourceConfig.toState(
        sources: List<LyricsSourceDefinition> = state.value.sources,
        message: String? = null,
    ): SettingsState {
        return SettingsState(
            sources = sources,
            useSambaCache = state.value.useSambaCache,
            musicmatchUserToken = state.value.musicmatchUserToken,
            hasMusicmatchSource = state.value.hasMusicmatchSource,
            editingId = id,
            name = name,
            method = method,
            urlTemplate = urlTemplate,
            headersTemplate = headersTemplate,
            queryTemplate = queryTemplate,
            bodyTemplate = bodyTemplate,
            responseFormat = responseFormat,
            extractor = extractor,
            priority = priority.toString(),
            enabled = enabled,
            workflowJsonInput = state.value.workflowJsonInput,
            editingWorkflowId = state.value.editingWorkflowId,
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
}

private fun newLyricsSourceId(prefix: String): String {
    return "$prefix-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(1000, 9999)}"
}
