package top.iwesley.lyn.music.data.repository

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.resources.*

import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiText

class PlaylistImportFailureTextTest {
    @Test fun wrappedDescriptionRemainsResolvableWithoutChangingReport() = runTest {
        val description = uiText(Res.string.server_http_failed, "Navidrome", "updatePlaylist", 403)
        val issue = playlistImportFailure(7, "用户歌曲 - 用户歌手", IllegalStateException("wrapper detail", UiTextException(description)))
        val report = PlaylistImportReport(addedCount = 2, failedLines = listOf(issue))
        val expected = listOf(
            AppLanguage.English to "Navidrome updatePlaylist failed, HTTP 403",
            AppLanguage.SimplifiedChinese to "Navidrome updatePlaylist 失败，HTTP 403",
            AppLanguage.TraditionalChinese to "Navidrome updatePlaylist 失敗，HTTP 403",
            AppLanguage.English to "Navidrome updatePlaylist failed, HTTP 403",
        )
        expected.forEach { (language, text) -> assertEquals(text, resolveUiText(issue.messageUiText(), language)) }
        assertEquals(7, issue.lineNumber)
        assertEquals("用户歌曲 - 用户歌手", issue.rawText)
        assertEquals("wrapper detail", issue.message)
        assertEquals(listOf(issue), report.failedLines)
        assertEquals(2, report.addedCount)
    }

    @Test fun ordinaryAndLegacyFailuresKeepTheirOriginalDetails() = runTest {
        val detail = "服务端原文 %1\$s /用户路径"
        val issues = listOf(
            playlistImportFailure(1, "歌曲 - 歌手", IllegalStateException(detail)),
            PlaylistImportFailedLineIssue(2, "歌曲 - 歌手", detail),
        )
        listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese).forEach { language ->
            issues.forEach { assertEquals(detail, resolveUiText(it.messageUiText(), language)) }
        }
    }

    @Test fun emptyErrorKeepsLegacyDiagnosticAndLocalizesFallback() = runTest {
        listOf(IllegalStateException(), IllegalStateException(" ")).forEach { error ->
            val issue = playlistImportFailure(1, "歌曲 - 歌手", error)
            assertEquals("加入失败。", issue.message)
            assertEquals("Failed to add to playlist.", resolveUiText(issue.messageUiText(), AppLanguage.English))
            assertEquals("加入歌单失败。", resolveUiText(issue.messageUiText(), AppLanguage.SimplifiedChinese))
            assertEquals("加入歌單失敗。", resolveUiText(issue.messageUiText(), AppLanguage.TraditionalChinese))
        }
    }
}
