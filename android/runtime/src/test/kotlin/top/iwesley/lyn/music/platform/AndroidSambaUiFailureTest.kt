package top.iwesley.lyn.music.platform

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.resources.*

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.core.model.SambaOperation
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.UiTextFailure
import top.iwesley.lyn.music.core.model.playbackErrorText
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiErrorDetail
import top.iwesley.lyn.music.core.model.uiText

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class AndroidSambaUiFailureTest {
    @org.junit.Before
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    fun initializeOfficialResourceReader() {
        org.jetbrains.compose.resources.setResourceReaderAndroidContext(org.robolectric.RuntimeEnvironment.getApplication())
    }

    @Test fun ioAdaptersPreserveProviderDetailsAndOriginalCausesThroughPlaybackWrappers() = runTest {
        val original = IOException("server 原文 %1\$s")
        val error = original.asAndroidSambaPlaybackIOException(SambaOperation.Read, "nas.local/用户目录/song.mp3")
        assertIs<UiTextFailure>(error)
        assertSame(original, error.cause)
        val state = PlaybackGatewayState().withAndroidPlaybackFailure(IllegalStateException("Source error", error))
        assertEquals(error.uiErrorDetail(), state.errorText)
        assertEquals("Samba read failed: server 原文 %1\$s (nas.local/用户目录/song.mp3)", resolveUiText(checkNotNull(state.playbackErrorText()), AppLanguage.English))
        assertEquals("Samba 讀取失敗：server 原文 %1\$s（nas.local/用户目录/song.mp3）", resolveUiText(checkNotNull(state.playbackErrorText()), AppLanguage.TraditionalChinese))
    }

    @Test fun localValidationDescriptionsRemainLocalizedWhenConvertedToIoErrors() = runTest {
        val original = UiTextException(uiText(Res.string.samba_seek_exceeds_size, 2L, 1L))
        val error = original.asAndroidSambaPlaybackIOException(SambaOperation.Open, "nas.local/song.mp3")
        assertSame(original, error.cause)
        assertEquals("Samba open failed: The requested position exceeds the file size: 2 > 1. (nas.local/song.mp3)", resolveUiText(error.uiErrorDetail(), AppLanguage.English))
    }
}
