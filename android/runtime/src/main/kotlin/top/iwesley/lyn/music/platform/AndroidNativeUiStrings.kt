package top.iwesley.lyn.music.platform

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.setResourceReaderAndroidContext
import top.iwesley.lyn.music.core.model.*
import java.util.Locale

/** Refreshes only the language state, leaving preferences and playback intact. */
fun refreshAndroidSystemAppLanguage(configuration: Configuration) {
    val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        configuration.locales.takeIf { !it.isEmpty }?.get(0)
    } else {
        @Suppress("DEPRECATION")
        configuration.locale
    }
    AppLanguageRuntime.updateSystemLanguage(locale?.toLanguageTag() ?: Locale.getDefault().toLanguageTag())
}

@OptIn(ExperimentalResourceApi::class)
internal fun initializeNativeUiStrings(context: Context) {
    setResourceReaderAndroidContext(context.applicationContext)
}

@Composable
fun nativeUiString(resource: StringResource, vararg arguments: Any?): String =
    uiText(resource, *arguments).resourceText()

@Composable
fun nativeUiString(resource: PluralStringResource, quantity: Int, vararg arguments: Any?): String =
    uiPlural(resource, quantity, *arguments).resourceText()

@Composable
fun UiText.nativeDisplayText(): String = resourceText()

suspend fun resolveNativeUiString(resource: StringResource, vararg arguments: Any?): String =
    resolveCurrentUiText(uiText(resource, *arguments))
