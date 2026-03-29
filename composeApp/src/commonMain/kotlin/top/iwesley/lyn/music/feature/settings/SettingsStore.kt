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
import top.iwesley.lyn.music.data.repository.SettingsRepository

data class SettingsState(
    val sources: List<LyricsSourceDefinition> = emptyList(),
    val useSambaCache: Boolean = true,
    val editingId: String? = null,
    val name: String = "",
    val method: RequestMethod = RequestMethod.GET,
    val urlTemplate: String = "",
    val headersTemplate: String = "",
    val queryTemplate: String = "",
    val bodyTemplate: String = "",
    val responseFormat: LyricsResponseFormat = LyricsResponseFormat.JSON,
    val extractor: String = "text",
    val priority: String = "0",
    val enabled: Boolean = true,
    val workflowJsonInput: String = "",
    val viewingWorkflowId: String? = null,
    val message: String? = null,
)

sealed interface SettingsIntent {
    data class UseSambaCacheChanged(val value: Boolean) : SettingsIntent
    data class SelectConfig(val config: LyricsSourceConfig?) : SettingsIntent
    data class ViewWorkflow(val config: WorkflowLyricsSourceConfig?) : SettingsIntent
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
                updateState { state ->
                    state.copy(sources = sources)
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
                intent.config?.toState() ?: SettingsState(sources = it.sources, useSambaCache = it.useSambaCache, workflowJsonInput = it.workflowJsonInput)
            }

            is SettingsIntent.ViewWorkflow -> updateState {
                it.copy(
                    viewingWorkflowId = intent.config?.id,
                    workflowJsonInput = intent.config?.rawJson.orEmpty(),
                    editingId = null,
                    message = null,
                )
            }

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
                    viewingWorkflowId = null,
                )
            }

            SettingsIntent.ImportWorkflow -> {
                val rawJson = state.value.workflowJsonInput.trim()
                if (rawJson.isBlank()) {
                    updateState { it.copy(message = "请先粘贴 Workflow JSON。") }
                    return
                }
                val imported = runCatching { repository.importWorkflowLyricsSource(rawJson) }
                updateState {
                    it.copy(
                        workflowJsonInput = imported.getOrNull()?.rawJson ?: it.workflowJsonInput,
                        viewingWorkflowId = imported.getOrNull()?.id,
                        message = imported.fold(
                            onSuccess = { config -> "Workflow 源 ${config.name} 已导入。" },
                            onFailure = { error -> error.message ?: "Workflow 导入失败。" },
                        ),
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
                    val shouldClearWorkflow = it.viewingWorkflowId == intent.sourceId
                    if (shouldClearDirect) {
                        SettingsState(
                            sources = it.sources,
                            useSambaCache = it.useSambaCache,
                            workflowJsonInput = if (shouldClearWorkflow) "" else it.workflowJsonInput,
                            message = "歌词源已删除。",
                        )
                    } else {
                        it.copy(
                            workflowJsonInput = if (shouldClearWorkflow) "" else it.workflowJsonInput,
                            viewingWorkflowId = if (shouldClearWorkflow) null else it.viewingWorkflowId,
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
                repository.saveLyricsSource(config)
                updateState { config.toState(sources = it.sources, message = "歌词源已保存。") }
            }

            SettingsIntent.Delete -> {
                val editingId = state.value.editingId ?: return
                repository.deleteLyricsSource(editingId)
                updateState { SettingsState(sources = it.sources, useSambaCache = it.useSambaCache, workflowJsonInput = it.workflowJsonInput, message = "歌词源已删除。") }
            }

            SettingsIntent.ClearMessage -> updateState { it.copy(message = null) }
        }
    }

    private fun SettingsState.toConfig(): LyricsSourceConfig? {
        if (name.isBlank() || urlTemplate.isBlank()) return null
        return LyricsSourceConfig(
            id = editingId ?: "lyrics-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(1000, 9999)}",
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
            message = message,
        )
    }
}
