package top.iwesley.lyn.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.PluralStringResource
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.resources.*

@Composable
fun uiString(resource: StringResource, vararg arguments: Any?): String =
    uiText(resource, *arguments).resourceText()

@Composable
fun uiString(resource: PluralStringResource, quantity: Int, vararg arguments: Any?): String =
    uiPlural(resource, quantity, *arguments).resourceText()

@Composable
fun UiText.displayText(): String = resourceText()

@Composable
fun Any?.uiDisplayText(): String = when (this) {
    is UiText -> displayText()
    else -> this?.toString().orEmpty()
}

val currentUiLanguage: AppLanguage
    @Composable get() {
        AppLanguageRuntime.effectiveLanguageRevision.collectAsState().value
        return AppLanguageRuntime.effectiveLanguage.value
    }

@Composable
fun appLanguageLabel(language: AppLanguage): String =
    if (language == AppLanguage.System) uiString(Res.string.language_follow_system) else language.nativeName

@Composable
fun LyricsShareFontOption.uiDisplayName(): String = displayNameText?.displayText() ?: displayName
