package top.iwesley.lyn.music.cast.upnp.android

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.uiPlural

import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiText

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class NativeCastErrorTextTest {
    @org.junit.Before
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    fun initializeOfficialResourceReader() {
        org.jetbrains.compose.resources.setResourceReaderAndroidContext(org.robolectric.RuntimeEnvironment.getApplication())
    }

    @Test fun nativeApplicationErrorsResolveByStableIdInAllLanguages() = runTest {
        listOf(Res.string.cast_control_uninitialized, Res.string.cast_discovery_failed, Res.string.cast_device_not_found, Res.string.cast_send_uri_failed,
            Res.string.cast_start_failed, Res.string.cast_resume_failed, Res.string.cast_pause_failed, Res.string.cast_seek_failed, Res.string.cast_stop_failed).forEach { resource ->
            val key = resource.key
            val description = nativeCastErrorText("lyn_ui:$key")
            assertEquals(uiText(resource), description)
            listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese).forEach { language ->
                resolveUiText(description, language)
            }
        }
        assertEquals("The selected casting device was not found.", resolveUiText(
            nativeCastErrorText("lyn_ui:cast_device_not_found"), AppLanguage.English))
        assertEquals("未找到選中的投屏裝置。", resolveUiText(
            nativeCastErrorText("lyn_ui:cast_device_not_found"), AppLanguage.TraditionalChinese))
    }

    @Test fun externalDetailsAndUnrecognizedIdsStayVerbatim() = runTest {
        listOf("服务端 detail %1\$s", "lyn_ui:unrecognized").forEach { details ->
            val description = nativeCastErrorText(details)
            assertEquals("Casting failed.\n$details", resolveUiText(description, AppLanguage.English))
            assertEquals("投屏失敗。\n$details", resolveUiText(description, AppLanguage.TraditionalChinese))
        }
        assertEquals(uiText(Res.string.cast_pause_failed), nativeCastErrorText("", uiText(Res.string.cast_pause_failed)))
    }

    @Test fun discoveryQuantitiesAndDeviceNamesAreNotCachedAsTranslatedStrings() = runTest {
        assertEquals("Searching for nearby devices", resolveUiText(discoveryText(0), AppLanguage.English))
        for (count in 0..2) {
            assertEquals("Found $count ${if (count == 1) "device" else "devices"}",
                resolveUiText(uiPlural(Res.plurals.cast_devices_found, (count).toInt(), count), AppLanguage.English))
            assertEquals("找到 $count 台裝置", resolveUiText(uiPlural(Res.plurals.cast_devices_found, (count).toInt(), count), AppLanguage.TraditionalChinese))
        }
        assertEquals("Connecting to 客厅 %1\$s", resolveUiText(connectingText("客厅 %1\$s"), AppLanguage.English))
        assertEquals("Casting to 客厅", resolveUiText(castingText("客厅"), AppLanguage.English))
    }
}
