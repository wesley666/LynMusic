package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.core.model.resolveUiText

import top.iwesley.lyn.music.resources.*

import kotlin.test.Test
import kotlin.test.assertEquals

class OfflineDownloadFailureTextTest {
    @Test fun xmlFailuresLocalizeContextAndPreserveServerDetails() = runTest {
        val details = "原始服务器消息 %1\$s"
        val text = offlineDownloadResponseFailureText("Subsonic", errorMessage = details, status = "failed", errorCode = "70")
        assertEquals("Subsonic download failed: $details (code=70)", resolveUiText(text, AppLanguage.English))
        assertEquals("Subsonic 下载失败：$details (code=70)", resolveUiText(text, AppLanguage.SimplifiedChinese))
        assertEquals("Subsonic 下載失敗：$details (code=70)", resolveUiText(text, AppLanguage.TraditionalChinese))
        assertEquals("offline_failure_with_code(arg1=offline_source_download_failed(arg1=Subsonic, arg2=$details), arg2=70)", UiTextException(text).message)
    }

    @Test fun xmlFallbacksAndProtocolStatusStayDistinct() = runTest {
        val xml = offlineDownloadResponseFailureText("Navidrome", isSubsonicResponse = false)
        assertEquals("Navidrome download failed: The server returned an XML response.", resolveUiText(xml, AppLanguage.English))
        assertEquals("Navidrome 下載失敗：伺服器傳回 XML 回應。", resolveUiText(xml, AppLanguage.TraditionalChinese))
        val subsonic = offlineDownloadResponseFailureText("Subsonic")
        assertEquals("Subsonic download failed: The server returned a Subsonic XML response.", resolveUiText(subsonic, AppLanguage.English))
        val status = offlineDownloadResponseFailureText("Subsonic", status = "failed")
        assertEquals("Subsonic download failed: status=failed", resolveUiText(status, AppLanguage.English))
    }

    @Test fun fileFailuresReachUiWithoutUsingChineseDiagnosticMessages() = runTest {
        val empty = UiTextArgumentException(uiText(Res.string.offline_file_empty))
        val write = UiTextException(uiText(Res.string.offline_write_failed))
        assertEquals("The downloaded file is empty.", resolveUiText(empty.uiErrorText(), AppLanguage.English))
        assertEquals("離線檔案寫入失敗。", resolveUiText(write.uiErrorText(), AppLanguage.TraditionalChinese))
        assertEquals("offline_file_empty", empty.message)
        assertEquals("offline_write_failed", write.message)
    }
}
