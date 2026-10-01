package top.iwesley.lyn.music.core.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage

@Composable
fun ProvideUiLanguage(content: @Composable () -> Unit) {
    AppLanguageRuntime.effectiveLanguageRevision.collectAsState().value
    ProvideUiResourceLanguage(AppLanguageRuntime.effectiveLanguage.value.resourceLanguageTag, content)
}

@Composable
fun UiText.resourceText(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Joined -> {
        val separatorValue = separatorText?.resourceText() ?: separator
        items.map { it.resourceText() }.joinToString(separatorValue)
    }
    is UiText.StringRef -> stringResource(resource, *displayArguments(arguments))
    is UiText.PluralRef -> pluralStringResource(resource, quantity, *displayArguments(arguments))
}

@Composable
private fun displayArguments(arguments: List<Any?>): Array<Any> =
    arguments.map { if (it is UiText) it.resourceText() else it ?: "" }.toTypedArray()

@Composable
fun uiString(resource: StringResource, vararg arguments: Any?): String = uiText(resource, *arguments).resourceText()

@Composable
fun uiString(resource: PluralStringResource, quantity: Int, vararg arguments: Any?): String =
    uiPlural(resource, quantity, *arguments).resourceText()
