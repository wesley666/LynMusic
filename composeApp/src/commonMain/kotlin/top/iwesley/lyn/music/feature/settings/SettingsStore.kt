package top.iwesley.lyn.music.feature.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.SettingsRepository

data class SettingsState(
    val configs: List<LyricsSourceConfig> = emptyList(),
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
    val message: String? = null,
)

sealed interface SettingsIntent {
    data class UseSambaCacheChanged(val value: Boolean) : SettingsIntent
    data class SelectConfig(val config: LyricsSourceConfig?) : SettingsIntent
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
            repository.lyricsSources.collect { configs ->
                updateState { state ->
                    state.copy(configs = configs)
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
                intent.config?.toState() ?: SettingsState(configs = it.configs, useSambaCache = it.useSambaCache)
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

            SettingsIntent.Save -> {
                val config = state.value.toConfig() ?: run {
                    updateState { it.copy(message = "请至少填写歌词源名称和 URL。") }
                    return
                }
                repository.saveLyricsSource(config)
                updateState { config.toState(configs = it.configs, message = "歌词源已保存。") }
            }

            SettingsIntent.Delete -> {
                val editingId = state.value.editingId ?: return
                repository.deleteLyricsSource(editingId)
                updateState { SettingsState(configs = it.configs, useSambaCache = it.useSambaCache, message = "歌词源已删除。") }
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
        configs: List<LyricsSourceConfig> = state.value.configs,
        message: String? = null,
    ): SettingsState {
        return SettingsState(
            configs = configs,
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
            message = message,
        )
    }
}
