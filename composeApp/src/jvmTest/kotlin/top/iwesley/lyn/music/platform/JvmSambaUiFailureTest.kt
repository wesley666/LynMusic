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
import top.iwesley.lyn.music.core.model.SambaOperation
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.UiTextFailure
import top.iwesley.lyn.music.core.model.playbackLoadFailureText
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiErrorDetail
import top.iwesley.lyn.music.core.model.uiText

class JvmSambaUiFailureTest {
    @Test fun ioAdaptersPreserveProviderDetailsAndCausesAcrossLanguageChanges() = runTest {
        val original = IOException("server 原文 %1\$s")
        val error = original.asJvmSambaIOException(SambaOperation.ProbeSize)
        assertIs<UiTextFailure>(error)
        assertSame(original, error.cause)
        val text = playbackLoadFailureText(IllegalStateException("External wrapper", error))
        assertEquals("Failed to access the song: Samba size probe failed: server 原文 %1\$s", resolveUiText(text, AppLanguage.English))
        assertEquals("访问歌曲失败：Samba 探测大小失败：server 原文 %1\$s", resolveUiText(text, AppLanguage.SimplifiedChinese))
        assertEquals("存取歌曲失敗：Samba 大小探測失敗：server 原文 %1\$s", resolveUiText(text, AppLanguage.TraditionalChinese))
    }

    @Test fun applicationValidationDescriptionsSurviveIoConversion() = runTest {
        val original = UiTextException(uiText(Res.string.samba_path_unavailable))
        val error = original.asJvmSambaIOException(SambaOperation.Open)
        assertSame(original, error.cause)
        assertEquals("Samba open failed: The SMB path does not exist or cannot be accessed.", resolveUiText(error.uiErrorDetail(), AppLanguage.English))
        assertEquals("Samba 開啟失敗：SMB 路徑不存在或無法存取。", resolveUiText(error.uiErrorDetail(), AppLanguage.TraditionalChinese))
    }
}
