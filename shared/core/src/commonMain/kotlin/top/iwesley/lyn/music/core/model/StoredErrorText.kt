package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Persist a display snapshot, never a resource reference or its diagnostic key. */
suspend fun resolveUiTextForStorage(text: UiText, fallbackMessage: String): String =
    resolveUiTextForStorage(text, fallbackMessage, ::resolveUiText)

internal suspend fun resolveUiTextForStorage(
    text: UiText,
    fallbackMessage: String,
    resolve: suspend (UiText, AppLanguage) -> String,
): String {
    val language = AppLanguageRuntime.effectiveLanguage.value
    return try {
        val value = resolve(text, language)
        currentCoroutineContext().ensureActive()
        value
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        currentCoroutineContext().ensureActive()
        fallbackMessage
    }
}

/** Ordinary provider details stay verbatim; application failures are rendered before saving. */
suspend fun Throwable.errorMessageForStorage(fallback: UiText, fallbackMessage: String): String {
    if (this is CancellationException) throw this
    currentCoroutineContext().ensureActive()
    val text = uiFailureTextOrNull()
        ?: message?.takeIf { it.isNotBlank() }?.let { return it }
        ?: fallback
    return resolveUiTextForStorage(text, fallbackMessage)
}
