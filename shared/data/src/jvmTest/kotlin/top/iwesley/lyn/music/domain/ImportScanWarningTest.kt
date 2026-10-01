package top.iwesley.lyn.music.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*

class ImportScanWarningTest {
    private val languages = listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese, AppLanguage.English)

    @Test fun nativeAndLegacyNavidromeWarningsSurviveBothReportConversions() = runTest {
        for (native in listOf(true, false)) {
            val client = EmptyLibraryClient(native)
            val draft = NavidromeSourceDraft("用户来源", "https://server.example", username = "u", password = "p")
            val report = scanNavidromeLibrary(draft, "source", client, setOf("flac"))
            assertTrue(report.tracks.isEmpty())
            assertEquals(0, report.discoveredAudioFileCount)
            assertWarning(report.warnings, "Navidrome")
            val streamed = scanNavidromeLibraryStreaming(draft, "source", client, setOf("flac"))
            assertEquals(0, streamed.importedTrackCount)
            assertWarning(streamed.warnings, "Navidrome")
            assertEquals(report.warnings, streamed.warnings)
            val calls = client.calls
            for (language in languages) resolveUiText(streamed.warnings.single().warningUiText(), language)
            assertEquals(calls, client.calls)
        }
    }

    @Test fun subsonicAndEmbyEmptyLibraryWarningsKeepServiceNamesAndCounts() = runTest {
        val subsonic = scanSubsonicLibrary(
            SubsonicSourceDraft("用户来源", "https://server.example", username = "u", credential = "p"),
            "source", EmptyLibraryClient(false), setOf("flac"),
        )
        assertWarning(subsonic.warnings, "Subsonic")
        assertEquals(0, subsonic.discoveredAudioFileCount)
        val emby = scanEmbyLibrary(
            EmbySourceDraft("用户来源", "https://server.example", username = "u", password = ""),
            EmbyCredential("user", "token"), "device", "source", EmptyLibraryClient(false), setOf("flac"),
        )
        assertWarning(emby.warnings, "Emby")
        assertEquals(0, emby.totalTrackCount)
        assertEquals(0, emby.discoveredAudioFileCount)
    }

    private suspend fun assertWarning(warnings: List<ImportScanWarning>, service: String) {
        val warning = warnings.single()
        assertEquals("当前 $service 账号下没有可同步的歌曲。", warning.diagnostic)
        assertNotNull(warning.text)
        for (language in languages) {
            val expected = when (language) {
                AppLanguage.English -> "This $service account has no songs available to sync."
                AppLanguage.SimplifiedChinese -> "当前 $service 账号下没有可同步的歌曲。"
                else -> "目前 $service 帳號下沒有可同步的歌曲。"
            }
            assertEquals(expected, resolveUiText(warning.warningUiText(), language))
        }
    }
}

private class EmptyLibraryClient(private val native: Boolean) : LyricsHttpClient {
    var calls = 0
    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        calls++
        val body = when {
            request.url.contains("/rest/ping") -> """{"subsonic-response":{"status":"ok","type":"navidrome","serverVersion":"${if (native) "0.55.0" else "0.43.0"}"}}"""
            request.url.contains("/auth/login") -> """{"token":"token"}"""
            request.url.contains("/api/song") -> "[]"
            request.url.contains("/rest/getArtists") -> """{"subsonic-response":{"status":"ok","artists":{"index":[]}}}"""
            request.url.contains("/Users/user/Items") -> """{"Items":[],"TotalRecordCount":0}"""
            else -> error("Unexpected request: ${request.url}")
        }
        return Result.success(LyricsHttpResponse(200, body))
    }
}
