package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.resources.*
import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.UiTextFailure
import top.iwesley.lyn.music.core.model.WebDavOperation
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiErrorDetail
import top.iwesley.lyn.music.core.model.uiText

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class AndroidWebDavUiFailureTest {
    @org.junit.Before
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    fun initializeOfficialResourceReader() {
        org.jetbrains.compose.resources.setResourceReaderAndroidContext(org.robolectric.RuntimeEnvironment.getApplication())
    }

    class HttpFailureStub : IOException("raw response") {
        fun getStatusCode(): Int = 403
        fun getResponsePhrase(): String = "Forbidden 原始详情"
    }

    @Test fun reflectedHttpFailuresCarryLocalizedDescriptionsAndTheOriginalCause() = runTest {
        val original = HttpFailureStub()
        val error = original.asAndroidWebDavIOException(WebDavOperation.TestConnection, true)
        assertIs<UiTextFailure>(error)
        assertSame(original, error.cause)
        assertEquals("WebDAV connection test failed. This account does not have access. Server details: Forbidden 原始详情",
            resolveUiText(error.uiErrorDetail(), AppLanguage.English))
    }

    @Test fun validationAndArtworkFailuresKeepResourceDescriptionsWhenWrapped() = runTest {
        listOf(Res.string.webdav_root_inaccessible, Res.string.artwork_file_unreadable, Res.string.artwork_file_missing, Res.string.artwork_selection_unreadable).forEach { key ->
            val original = UiTextException(uiText(key))
            val error = original.asAndroidWebDavIOException(WebDavOperation.TestConnection, false)
            assertSame(original, error.cause)
            assertEquals(original.text, error.uiErrorDetail())
        }
    }

    @Test fun externalIOExceptionsAndTheirDetailsRemainUnchanged() = runTest {
        val original = IOException("Original 第三方错误")
        assertSame(original, original.asAndroidWebDavIOException(WebDavOperation.Scan, false))
        val wrapped = IllegalStateException("Original 第三方错误").asAndroidWebDavIOException(WebDavOperation.Scan, false)
        assertEquals("WebDAV scan failed.\nOriginal 第三方错误", resolveUiText(wrapped.uiErrorDetail(), AppLanguage.English))
    }
}
