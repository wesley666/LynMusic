package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Text and language are captured together; stale asynchronous results never update controls. */
suspend fun observeResolvedUiText(
    descriptions: Flow<UiText?>,
    resolve: suspend (UiText, AppLanguage) -> String = ::resolveUiText,
    onResolved: suspend (String?, isCurrent: () -> Boolean) -> Unit,
) {
    combine(descriptions, AppLanguageRuntime.effectiveLanguageRevision) { text, revision ->
        Triple(text, revision, AppLanguageRuntime.effectiveLanguage.value)
    }.distinctUntilChanged().collectLatest { (text, revision, language) ->
        val value = text?.let { resolve(it, language) }
        currentCoroutineContext().ensureActive()
        val isCurrent = {
            revision == AppLanguageRuntime.effectiveLanguageRevision.value &&
                (descriptions !is StateFlow || descriptions.value == text)
        }
        if (isCurrent()) onResolved(value, isCurrent)
    }
}
