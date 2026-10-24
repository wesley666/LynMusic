@file:OptIn(org.jetbrains.compose.resources.InternalResourceApi::class)
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package top.iwesley.lyn.music.resources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import org.jetbrains.compose.resources.ComposeEnvironment
import org.jetbrains.compose.resources.LanguageQualifier
import org.jetbrains.compose.resources.LocalComposeEnvironment
import org.jetbrains.compose.resources.RegionQualifier
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.ScriptQualifier
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.jetbrains.compose.resources.rememberResourceEnvironment

/**
 * Locale overrides use Compose resources' internal environment API only in this file.
 * Recheck this integration when upgrading the library.
 * No default Locale or platform language preference is changed.
 */
fun uiResourceEnvironment(
    languageTag: String,
    base: ResourceEnvironment? = null,
): ResourceEnvironment {
    val (language, region) = when (languageTag) {
        "en" -> "en" to ""
        "zh-CN" -> "zh" to "CN"
        "zh-TW" -> "zh" to "TW"
        else -> error("Unsupported UI resource language: $languageTag")
    }
    val environment = base ?: getSystemResourceEnvironment()
    return ResourceEnvironment(
        language = LanguageQualifier(language),
        script = ScriptQualifier(""),
        region = RegionQualifier(region),
        theme = environment.theme,
        density = environment.density,
    )
}

/** Replaces only the resource environment, leaving the existing composition intact. */
@Composable
fun ProvideUiResourceLanguage(languageTag: String, content: @Composable () -> Unit) {
    val base = rememberResourceEnvironment()
    val environment = remember(base, languageTag) { uiResourceEnvironment(languageTag, base) }
    val provider = remember(environment) {
        object : ComposeEnvironment {
            @Composable override fun rememberEnvironment(): ResourceEnvironment = environment
        }
    }
    CompositionLocalProvider(LocalComposeEnvironment provides provider, content = content)
}
