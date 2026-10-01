package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.core.model.resolveUiText

import top.iwesley.lyn.music.resources.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SambaUiTextTest {
    @Test fun operationsUseTheSelectedLanguageAndPreserveProviderDetails() = runTest {
        val details = "provider 原文 %1\$s"
        val error = IllegalStateException(details)
        val expected = listOf(
            Triple(SambaOperation.Open, "open", "開啟"),
            Triple(SambaOperation.Read, "read", "讀取"),
            Triple(SambaOperation.ProbeSize, "size probe", "大小探測"),
        )
        expected.forEach { (operation, english, traditional) ->
            val text = sambaFailureText(operation, error)
            assertEquals("Samba $english failed: $details", resolveUiText(text, AppLanguage.English))
            assertEquals("Samba ${traditional}失敗：$details", resolveUiText(text, AppLanguage.TraditionalChinese))
            assertTrue(resolveUiText(text, AppLanguage.SimplifiedChinese).endsWith(details))
        }
    }

    @Test fun applicationDetailsAndSourceReferencesRemainUnresolved() = runTest {
        val reference = "nas.local/用户的目录/%1\$s.mp3"
        val error = IllegalStateException("External wrapper", UiTextException(uiText(Res.string.samba_source_unavailable)))
        val text = sambaFailureText(SambaOperation.Open, error, reference)
        assertEquals("Samba open failed: The Samba source is unavailable. ($reference)", resolveUiText(text, AppLanguage.English))
        assertEquals("Samba 打开失败：Samba 来源不可用。（$reference）", resolveUiText(text, AppLanguage.SimplifiedChinese))
        assertEquals("Samba 開啟失敗：Samba 來源無法使用。（$reference）", resolveUiText(text, AppLanguage.TraditionalChinese))
    }

    @Test fun setupFailuresRetainNestedValidationDescriptions() = runTest {
        val text = sambaPlaybackFailureText(UiTextException(uiText(Res.string.samba_path_missing_share)))
        assertEquals("Samba playback failed: The SMB path must include a share name, such as Media or Media/Music.", resolveUiText(text, AppLanguage.English))
        assertEquals("Samba 播放失败：SMB 路径至少需要包含共享名，例如 Media 或 Media/Music。", resolveUiText(text, AppLanguage.SimplifiedChinese))
        assertEquals("Samba 播放失敗：SMB 路徑至少需要包含共用名稱，例如 Media 或 Media/Music。", resolveUiText(text, AppLanguage.TraditionalChinese))
    }

    @Test fun seekFailureFormatsPositionsAndSizesWithoutTranslatingThem() = runTest {
        for (position in 0L..2L) {
            val text = uiText(Res.string.samba_seek_exceeds_size, position, 0L)
            assertEquals("The requested position exceeds the file size: $position > 0.", resolveUiText(text, AppLanguage.English))
            assertEquals("请求位置超出文件大小：$position > 0。", resolveUiText(text, AppLanguage.SimplifiedChinese))
            assertEquals("請求位置超出檔案大小：$position > 0。", resolveUiText(text, AppLanguage.TraditionalChinese))
        }
    }
}
