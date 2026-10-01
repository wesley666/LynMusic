package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.ensureActive
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getPluralString
import top.iwesley.lyn.music.resources.*

/** Only resource references and raw data are carried through business state. */
sealed interface UiText {
    data class StringRef(val resource: StringResource, val arguments: List<Any?> = emptyList()) : UiText
    data class PluralRef(val resource: PluralStringResource, val quantity: Int, val arguments: List<Any?> = emptyList()) : UiText
    data class Raw(val value: String) : UiText
    data class Joined(val items: List<UiText>, val separator: String = " · ", val separatorText: UiText? = null) : UiText
}

fun uiText(resource: StringResource, vararg arguments: Any?): UiText = UiText.StringRef(resource, arguments.toList())
fun uiPlural(resource: PluralStringResource, quantity: Int, vararg arguments: Any?): UiText =
    UiText.PluralRef(resource, quantity, arguments.toList())

operator fun UiText.plus(other: UiText): UiText = UiText.Joined(listOf(this, other), "")
operator fun UiText.plus(other: String): UiText = this + UiText.Raw(other)

fun Throwable.uiErrorText(fallback: UiText = uiText(Res.string.ui_operation_failed)): UiText =
    uiFailureTextOrNull() ?: message?.takeIf { it.isNotBlank() }?.let { details ->
        uiText(Res.string.ui_error_with_context, fallback, UiText.Raw(details))
    } ?: fallback

interface UiTextFailure { val text: UiText }

fun Throwable.uiFailureTextOrNull(): UiText? {
    val visited = mutableListOf<Throwable>()
    var current: Throwable? = this
    while (current != null && visited.none { it === current }) {
        (current as? UiTextFailure)?.let { return it.text }
        visited += current
        current = current.cause
    }
    return null
}

/** Resource-independent diagnostics: constructing a failure never reads files or uses UI state. */
fun UiText.diagnosticMessage(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Joined -> items.joinToString(separatorText?.diagnosticMessage() ?: separator) { it.diagnosticMessage() }
    is UiText.StringRef -> diagnosticResourceMessage(resource.key, arguments)
    is UiText.PluralRef -> diagnosticResourceMessage(resource.key, arguments, quantity)
}

private fun diagnosticResourceMessage(key: String, arguments: List<Any?>, quantity: Int? = null): String {
    val details = buildList {
        quantity?.let { add("quantity=$it") }
        arguments.forEachIndexed { index, value ->
            add("arg${index + 1}=${if (value is UiText) value.diagnosticMessage() else value?.toString().orEmpty()}")
        }
    }
    return if (details.isEmpty()) key else "$key(${details.joinToString(", ")})"
}

open class UiTextException(override val text: UiText, cause: Throwable? = null, diagnosticMessage: String? = null) :
    IllegalStateException(diagnosticMessage ?: text.diagnosticMessage(), cause), UiTextFailure
open class UiTextArgumentException(override val text: UiText, diagnosticMessage: String? = null) :
    IllegalArgumentException(diagnosticMessage ?: text.diagnosticMessage()), UiTextFailure
class UiTextUnsupportedException(override val text: UiText, diagnosticMessage: String? = null) :
    UnsupportedOperationException(diagnosticMessage ?: text.diagnosticMessage()), UiTextFailure

inline fun requireUi(value: Boolean, lazyMessage: () -> UiText) {
    if (!value) throw UiTextArgumentException(lazyMessage())
}
inline fun checkUi(value: Boolean, lazyMessage: () -> UiText) {
    if (!value) throw UiTextException(lazyMessage())
}
fun Throwable.uiErrorDetail(): UiText = uiFailureTextOrNull() ?: UiText.Raw(message.orEmpty())

val AppLanguage.resourceLanguageTag: String get() = when (this) {
    AppLanguage.SimplifiedChinese -> "zh-CN"
    AppLanguage.TraditionalChinese -> "zh-TW"
    AppLanguage.English -> "en"
    AppLanguage.System -> AppLanguageRuntime.effectiveLanguage.value.takeUnless { it == AppLanguage.System }?.resourceLanguageTag ?: "en"
}

/** Every nested argument uses the same captured environment. */
suspend fun resolveUiText(text: UiText, language: AppLanguage): String =
    resolveUiTextInEnvironment(text, uiResourceEnvironment(language.resourceLanguageTag))

private suspend fun resolveUiTextInEnvironment(text: UiText, environment: ResourceEnvironment): String = when (text) {
    is UiText.Raw -> text.value
    is UiText.Joined -> {
        val separator = text.separatorText?.let { resolveUiTextInEnvironment(it, environment) } ?: text.separator
        val values = text.items.map { resolveUiTextInEnvironment(it, environment) }
        values.joinToString(separator)
    }
    is UiText.StringRef -> getString(environment, text.resource, *resolveUiArguments(text.arguments, environment))
    is UiText.PluralRef -> getPluralString(environment, text.resource, text.quantity, *resolveUiArguments(text.arguments, environment))
}

private suspend fun resolveUiArguments(arguments: List<Any?>, environment: ResourceEnvironment): Array<Any> =
    arguments.map { if (it is UiText) resolveUiTextInEnvironment(it, environment) else it ?: "" }.toTypedArray()

/** Suspend entry points for native controls and platform services. */
suspend fun resolveUiString(resource: StringResource, vararg arguments: Any?): String =
    resolveCurrentUiText(uiText(resource, *arguments))
suspend fun resolveUiString(resource: PluralStringResource, quantity: Int, vararg arguments: Any?): String =
    resolveCurrentUiText(uiPlural(resource, quantity, *arguments))

/** Event-driven native controls must also discard an obsolete language result. */
suspend fun resolveCurrentUiText(text: UiText): String {
    while (true) {
        val revision = AppLanguageRuntime.effectiveLanguageRevision.value
        val language = AppLanguageRuntime.effectiveLanguage.value
        val value = resolveUiText(text, language)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (revision == AppLanguageRuntime.effectiveLanguageRevision.value) return value
    }
}
