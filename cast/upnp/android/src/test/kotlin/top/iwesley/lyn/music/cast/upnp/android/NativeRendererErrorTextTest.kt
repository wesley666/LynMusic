package top.iwesley.lyn.music.cast.upnp.android

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.resources.*

import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiText

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class NativeRendererErrorTextTest {
    @org.junit.Before
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    fun initializeOfficialResourceReader() {
        org.jetbrains.compose.resources.setResourceReaderAndroidContext(org.robolectric.RuntimeEnvironment.getApplication())
    }

    @Test fun receiverErrorsResolveFromNativeIdsInEveryLanguage() = runTest {
        listOf(Res.string.renderer_initialize_failed, Res.string.renderer_register_failed, Res.string.renderer_start_failed, Res.string.renderer_uninitialized).forEach { resource ->
            val key = resource.key
            val text = nativeRendererErrorText("lyn_ui:$key")
            assertEquals(uiText(resource), text)
            listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese).forEach { language ->
                resolveUiText(text, language)
            }
        }
        val text = nativeRendererErrorText("lyn_ui:renderer_start_failed")
        assertEquals("Failed to start the DLNA receiver.", resolveUiText(text, AppLanguage.English))
        assertEquals("啟動 DLNA 接收端失敗。", resolveUiText(text, AppLanguage.TraditionalChinese))
    }

    @Test fun unknownDetailsArePreservedAndBlankErrorsUseTheFallback() = runTest {
        listOf("原始详情 %1\$s", "lyn_ui:unknown", "lyn_ui:cast_start_failed").forEach { raw ->
            assertEquals("Failed to start the DLNA receiver.\n$raw", resolveUiText(nativeRendererErrorText(raw), AppLanguage.English))
        }
        assertEquals(uiText(Res.string.renderer_start_failed), nativeRendererErrorText(""))
    }
}
