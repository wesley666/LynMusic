package top.iwesley.lyn.music.platform

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.UiTextFailure
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiFailureTextOrNull

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class AndroidWebDavSeekUiFailureTest {
    @org.junit.Before
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    fun initializeOfficialResourceReader() {
        org.jetbrains.compose.resources.setResourceReaderAndroidContext(org.robolectric.RuntimeEnvironment.getApplication())
    }

    @Test fun successfulSkipsLeaveStreamAtRequestedByteIncludingZeroAndEnd() = runTest {
        listOf(0L, 3L, 8L).forEach { target ->
            val stream = ByteArrayInputStream(ByteArray(8) { it.toByte() })
            skipAndroidWebDavBytes(stream, target)
            assertEquals(if (target == 8L) -1 else target.toInt(), stream.read())
        }
    }

    @Test fun helperContinuesAfterPartialSkipWithoutChangingTheAlgorithm() = runTest {
        val stream = object : ByteArrayInputStream(ByteArray(8) { it.toByte() }) {
            override fun skip(count: Long) = super.skip(minOf(count, 2L))
        }
        skipAndroidWebDavBytes(stream, 7L)
        assertEquals(7, stream.read())
    }

    @Test fun exhaustedStreamsKeepEOFClassificationPositionAndUntranslatedDiagnostics() = runTest {
        listOf(1L, 4_294_967_296L).forEach { target ->
            val failure = assertFailsWith<EOFException> { skipAndroidWebDavBytes(ByteArrayInputStream(byteArrayOf()), target) }
            assertIs<UiTextFailure>(failure)
            assertEquals(target, (failure as WebDavUiEOFException).position)
            assertEquals("Unable to skip to requested WebDAV position $target", failure.message)
            val text = checkNotNull(IOException("Media3 source error", failure).uiFailureTextOrNull())
            listOf(
                AppLanguage.English to "Unable to reach WebDAV byte position $target.",
                AppLanguage.SimplifiedChinese to "无法定位到 WebDAV 字节位置 $target。",
                AppLanguage.TraditionalChinese to "無法定位到 WebDAV 位元組位置 $target。",
                AppLanguage.English to "Unable to reach WebDAV byte position $target.",
            ).forEach { (language, expected) -> assertEquals(expected, resolveUiText(text, language)) }
        }
        val stream = ByteArrayInputStream(byteArrayOf(1, 2))
        assertFailsWith<WebDavUiEOFException> { skipAndroidWebDavBytes(stream, 3L) }
        assertEquals(-1, stream.read())
    }
}
