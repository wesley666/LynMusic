package top.iwesley.lyn.music.platform

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.resolveUiText

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class AndroidOfflineDownloadFailureTextTest {
    @org.junit.Before
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    fun initializeOfficialResourceReader() {
        org.jetbrains.compose.resources.setResourceReaderAndroidContext(org.robolectric.RuntimeEnvironment.getApplication())
    }

    @Test fun actualXmlSniffPreservesServerDetailsAndLocalizesApplicationContext() = runTest {
        val bytes = """<subsonic-response status="failed"><error code="70" message="原始服务端消息"/></subsonic-response>""".encodeToByteArray()
        val prefix = OfflineDownloadSniffPrefix(bytes, bytes.size)
        val text = assertNotNull(prefix.subsonicResponseFailureText("Subsonic", "audio/mpeg"))
        assertEquals("Subsonic download failed: 原始服务端消息 (code=70)", resolveUiText(text, AppLanguage.English))
        assertEquals("Subsonic 下載失敗：原始服务端消息 (code=70)", resolveUiText(text, AppLanguage.TraditionalChinese))
    }

    @Test fun xmlContentTypeHasTranslatedFallbackAndAudioHasNoFailure() = runTest {
        val bytes = "not a subsonic document".encodeToByteArray()
        val prefix = OfflineDownloadSniffPrefix(bytes, bytes.size)
        val text = assertNotNull(prefix.subsonicResponseFailureText("Navidrome", "application/xml; charset=utf-8"))
        assertEquals("Navidrome download failed: The server returned an XML response.", resolveUiText(text, AppLanguage.English))
        assertNull(prefix.subsonicResponseFailureText("Navidrome", "audio/mpeg"))
    }
}
