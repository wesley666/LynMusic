package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val APP_LANGUAGE_PREFERENCE_KEY = "app_language"

enum class AppLanguage(val storageValue: String, val nativeName: String) {
    System("system", ""),
    SimplifiedChinese("zh-Hans", "简体中文"),
    TraditionalChinese("zh-Hant", "繁體中文"),
    English("en", "English"),
}

fun appLanguageOrDefault(value: String?): AppLanguage =
    AppLanguage.entries.firstOrNull { it.storageValue == value } ?: AppLanguage.System

fun resolveSystemAppLanguage(tag: String): AppLanguage {
    val parts = tag.replace('_', '-').lowercase().split('-')
    if (parts.firstOrNull() != "zh") return AppLanguage.English
    return when {
        "hant" in parts -> AppLanguage.TraditionalChinese
        "hans" in parts -> AppLanguage.SimplifiedChinese
        parts.any { it in setOf("tw", "hk", "mo") } -> AppLanguage.TraditionalChinese
        else -> AppLanguage.SimplifiedChinese
    }
}

interface AppLanguagePreferencesStore {
    val appLanguage: StateFlow<AppLanguage>
    suspend fun setAppLanguage(language: AppLanguage)
}

/** Application-wide UI preference. It never changes a platform's process/default locale. */
object AppLanguageRuntime {
    private val state = MutableStateFlow(LanguageRuntimeState())
    private val selectionMutex = Mutex()
    val appLanguage: StateFlow<AppLanguage> = LanguageRuntimeStateFlow(state) { it.language }
    val effectiveLanguage: StateFlow<AppLanguage> = LanguageRuntimeStateFlow(state) { it.effectiveLanguage }
    /** A round trip to the same language must still invalidate labels read during the transition. */
    val effectiveLanguageRevision: StateFlow<Long> = LanguageRuntimeStateFlow(state) { it.effectiveLanguageRevision }

    fun install(store: AppLanguagePreferencesStore, systemLanguageTag: String) {
        updateState { LanguageRuntimeState(store.appLanguage.value, systemLanguageTag, store) }
    }

    fun updateSystemLanguage(tag: String) {
        // Retry against the latest state if a manual selection changes concurrently.
        updateState { it.copy(systemTag = tag) }
    }

    fun update(language: AppLanguage) {
        updateState { it.copy(language = language) }
    }

    suspend fun select(language: AppLanguage) {
        selectionMutex.withLock {
            state.value.preferences?.setAppLanguage(language)
            update(language)
        }
    }

    private fun updateState(transform: (LanguageRuntimeState) -> LanguageRuntimeState) {
        state.update { current ->
            val next = transform(current)
            val revision = current.effectiveLanguageRevision + if (next.effectiveLanguage != current.effectiveLanguage) 1L else 0L
            next.copy(effectiveLanguageRevision = revision)
        }
    }
}

private data class LanguageRuntimeState(
    val language: AppLanguage = AppLanguage.System,
    val systemTag: String = "en",
    val preferences: AppLanguagePreferencesStore? = null,
    val effectiveLanguageRevision: Long = 0L,
) {
    val effectiveLanguage: AppLanguage
        get() = if (language == AppLanguage.System) resolveSystemAppLanguage(systemTag) else language
}

/** Synchronous views of the same atomic state, without separate mutable values or a background cache. */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class LanguageRuntimeStateFlow<T : Any>(
    private val state: StateFlow<LanguageRuntimeState>,
    private val transform: (LanguageRuntimeState) -> T,
) : StateFlow<T> {
    override val value: T get() = transform(state.value)
    override val replayCache: List<T> get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        var previous: T? = null
        return state.collect(FlowCollector { current ->
            val value = transform(current)
            if (value != previous) {
                previous = value
                collector.emit(value)
            }
        })
    }
}
