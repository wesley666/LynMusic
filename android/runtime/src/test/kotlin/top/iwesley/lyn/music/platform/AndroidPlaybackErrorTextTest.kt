package top.iwesley.lyn.music.platform

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.resources.*

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.core.model.WebDavOperation
import top.iwesley.lyn.music.core.model.playbackErrorText
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.webDavHttpFailureText

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class AndroidPlaybackErrorTextTest {
    @org.junit.Before
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    fun initializeOfficialResourceReader() {
        org.jetbrains.compose.resources.setResourceReaderAndroidContext(org.robolectric.RuntimeEnvironment.getApplication())
    }

    @Test fun wrappedSeekFailureUpdatesOnlyTheExistingPlaybackErrorFields() = runTest {
        val failure = WebDavUiEOFException(4_294_967_296L)
        val initial = PlaybackGatewayState(isPlaying = true, positionMs = 42L, durationMs = 999L,
            canSeek = true, volume = 0.7f, metadataTitle = "用户歌曲", completionCount = 2L, errorRevision = 8L)
        val updated = initial.withAndroidPlaybackFailure(IOException("Media3 source error", failure))
        assertEquals(initial.copy(
            canSeek = false, errorRevision = 9L, errorMessage = updated.errorMessage, errorText = failure.text,
        ), updated)
        assertTrue(updated.errorMessage.orEmpty().contains(checkNotNull(failure.message)))
        val text = checkNotNull(updated.playbackErrorText())
        assertEquals("Unable to reach WebDAV byte position 4294967296.", resolveUiText(text, AppLanguage.English))
        assertEquals("无法定位到 WebDAV 字节位置 4294967296。", resolveUiText(text, AppLanguage.SimplifiedChinese))
        assertEquals("無法定位到 WebDAV 位元組位置 4294967296。", resolveUiText(text, AppLanguage.TraditionalChinese))
    }

    @Test fun nestedWebDavErrorsReachPlaybackUiWithoutResettingBusinessState() = runTest {
        val description = webDavHttpFailureText(WebDavOperation.Playback, 401, true, "Basic realm=Music")
        val original = WebDavUiIOException(description)
        val wrapped = IllegalStateException("Source error", IOException("Load failed", original))
        val initial = PlaybackGatewayState(isPlaying = true, positionMs = 12_345L, durationMs = 98_765L,
            canSeek = true, volume = 0.4f, metadataTitle = "用户歌曲", completionCount = 7L, errorRevision = 2L)
        val state = initial.withAndroidPlaybackFailure(wrapped)
        assertSame(description, state.errorText)
        assertTrue(state.errorMessage.orEmpty().contains(original.message.orEmpty()))
        assertFalse(state.canSeek)
        assertEquals(initial.isPlaying, state.isPlaying)
        assertEquals(initial.positionMs, state.positionMs)
        assertEquals(initial.durationMs, state.durationMs)
        assertEquals(initial.volume, state.volume)
        assertEquals(initial.metadataTitle, state.metadataTitle)
        assertEquals(initial.completionCount, state.completionCount)
        assertEquals(3L, state.errorRevision)
        val ui = checkNotNull(state.playbackErrorText())
        assertEquals("WebDAV Basic Auth failed. Check your username and password. Server details: Basic realm=Music", resolveUiText(ui, AppLanguage.English))
        assertEquals("WebDAV Basic Auth 认证失败，请检查用户名和密码。 服务端信息: Basic realm=Music", resolveUiText(ui, AppLanguage.SimplifiedChinese))
        assertEquals("WebDAV Basic Auth 驗證失敗，請檢查使用者名稱和密碼。 伺服器資訊: Basic realm=Music", resolveUiText(ui, AppLanguage.TraditionalChinese))
        val reset = state.resetForTrackSwitch()
        assertNull(reset.errorMessage)
        assertNull(reset.errorText)
    }

    @Test fun subsequentExternalFailuresReplaceOldApplicationDescriptions() = runTest {
        val initial = PlaybackGatewayState(errorText = uiText(Res.string.samba_source_unavailable), errorRevision = 4L)
        val state = initial.withAndroidPlaybackFailure(IOException("provider 原文 %1\$s"))
        assertEquals("Operation failed: IOException: provider 原文 %1\$s", resolveUiText(checkNotNull(state.playbackErrorText()), AppLanguage.English))
        assertEquals("操作失敗：IOException: provider 原文 %1\$s", resolveUiText(checkNotNull(state.playbackErrorText()), AppLanguage.TraditionalChinese))
        assertEquals(5L, state.errorRevision)
    }

    @Test fun cyclicExternalCausesDoNotBlockErrorPublication() = runTest {
        val first = IOException("first")
        val second = IllegalStateException("second")
        first.initCause(second)
        second.initCause(first)
        val state = PlaybackGatewayState().withAndroidPlaybackFailure(first)
        assertEquals("IOException: first -> IllegalStateException: second", state.errorMessage)
        assertEquals(1L, state.errorRevision)
    }
}
