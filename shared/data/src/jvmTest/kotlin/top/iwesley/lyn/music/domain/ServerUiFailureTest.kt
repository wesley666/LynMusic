package top.iwesley.lyn.music.domain

import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import top.iwesley.lyn.music.core.model.*

class ServerUiFailureTest {
    @Test fun httpFallbackUsesStatusCodesFromActualServiceFailures() = runTest {
        for (status in listOf(400, 401, 403, 404, 408, 500, 503)) {
            val errors = mutableListOf(failure { authenticateEmby(embyDraft(), "device", response(status, "raw body")) })
            for (type in listOf(ImportSourceType.NAVIDROME, ImportSourceType.SUBSONIC)) {
                val source = NavidromeResolvedSource("https://server.test", username = "u", password = "p", sourceType = type)
                errors += failure { requestNavidromeJson(response(status, "raw body"), source, "ping") }
            }
            errors.forEach { error ->
                val expected = status == 408 || status in 500..599
                assertIs<UiTextArgumentException>(error)
                assertEquals(expected, isRemoteSourceAddressFallbackAllowed(error))
                assertEquals(expected, isRemoteSourceAddressFallbackAllowed(IllegalStateException("wrapper", error)))
                for (language in listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese)) {
                    assertTrue(resolveUiText(error.uiErrorText(), language).contains(status.toString()))
                    assertEquals(expected, isRemoteSourceAddressFallbackAllowed(error))
                }
            }
        }
    }

    @Test fun opaqueTransportFailureIsRetryableButValidationAndAuthorizationAreNot() = runTest {
        val original = IOException("broken pipe")
        val error = failure { authenticateEmby(embyDraft(), "device", client { Result.failure(original) }) }
        assertSame(original, error.cause)
        assertIs<UiTextException>(error)
        assertTrue(isRemoteSourceAddressFallbackAllowed(IllegalStateException("wrapper", error)))
        val validation = failure { authenticateEmby(embyDraft().copy(password = ""), "device", response(200, "{}")) }
        assertFalse(isRemoteSourceAddressFallbackAllowed(validation))
        val authorization = failure { authenticateEmby(embyDraft(), "device", response(403, "")) }
        assertFalse(isRemoteSourceAddressFallbackAllowed(IllegalStateException("network wrapper", authorization)))
    }

    @Test fun subsonicRequestsRetryWanOnlyForRetryableHttpStatus() = runTest {
        for (type in listOf(ImportSourceType.NAVIDROME, ImportSourceType.SUBSONIC)) {
            for (status in listOf(503, 408, 401, 403, 404)) {
                val requests = mutableListOf<String>()
                val source = NavidromeResolvedSource(
                    baseUrl = "https://lan.example", wanBaseUrl = "https://wan.example",
                    sourceId = "source", addressSelector = RemoteSourceAddressSelector(),
                    username = "u", password = "p", sourceType = type,
                )
                val result = runCatching {
                    requestNavidromeJson(client { request ->
                        requests += request.url
                        if (request.url.startsWith("https://lan.example")) Result.success(LyricsHttpResponse(status, ""))
                        else Result.success(LyricsHttpResponse(200, """{"subsonic-response":{"status":"ok"}}"""))
                    }, source, "ping")
                }
                val retryable = status == 503 || status == 408
                assertEquals(retryable, result.isSuccess)
                assertEquals(if (retryable) 2 else 1, requests.size)
                assertTrue(requests.first().startsWith("https://lan.example"))
                if (retryable) assertTrue(requests.last().startsWith("https://wan.example"))
            }
        }
    }

    @Test fun embyNetworkFailureKeepsCauseAndRawDetails() = runTest {
        val original = IOException("网络原文 %1\$s")
        val error = failure { authenticateEmby(embyDraft(), "device", client { Result.failure(original) }) }
        assertSame(original, error.cause)
        assertDescriptions(error,
            "Emby AuthenticateByName request failed: 网络原文 %1\$s",
            "Emby AuthenticateByName 请求失败：网络原文 %1\$s",
            "Emby AuthenticateByName 請求失敗：网络原文 %1\$s")
    }

    @Test fun embyHttpAndMissingResponseFieldsAreDescribed() = runTest {
        val http = failure { authenticateEmby(embyDraft(), "device", response(503, "server 原文")) }
        assertDescriptions(http, "Emby AuthenticateByName failed, HTTP 503", "Emby AuthenticateByName 失败，HTTP 503", "Emby AuthenticateByName 失敗，HTTP 503")
        val missing = failure { authenticateEmby(embyDraft(), "device", response(200, "{}")) }
        assertDescriptions(missing, "The Emby response is missing AccessToken.", "Emby 响应缺少 AccessToken。", "Emby 回應缺少 AccessToken。")
        val wrongShape = failure { authenticateEmby(embyDraft(), "device", response(200, "[]")) }
        assertEquals("Emby AuthenticateByName did not return a JSON object.", resolveUiText(wrongShape.uiErrorText(), AppLanguage.English))
    }

    @Test fun bothSubsonicServicesDescribeNetworkHttpAndFailureStatus() = runTest {
        for (type in listOf(ImportSourceType.NAVIDROME, ImportSourceType.SUBSONIC)) {
            val source = NavidromeResolvedSource("https://server.test", username = "u", password = "p", sourceType = type)
            val original = IOException("原始详情")
            val network = failure { requestNavidromeJson(client { Result.failure(original) }, source, "ping") }
            assertSame(original, network.cause)
            assertEquals("${source.displayName} ping request failed: 原始详情", resolveUiText(network.uiErrorText(), AppLanguage.English))
            val http = failure { requestNavidromeJson(response(502, "raw"), source, "ping") }
            assertEquals("${source.displayName} ping failed, HTTP 502", resolveUiText(http.uiErrorText(), AppLanguage.English))
            val noMessage = failure { requestNavidromeJson(response(200, """{"subsonic-response":{"status":"failed"}}"""), source, "ping") }
            assertDescriptions(noMessage,
                "${source.displayName} ping returned a failure status.",
                "${source.displayName} ping 返回失败状态。",
                "${source.displayName} ping 回應失敗狀態。")
            val serverMessage = failure { requestNavidromeJson(response(200, """{"subsonic-response":{"status":"failed","error":{"message":"服务器原文 %1${'$'}s"}}}"""), source, "ping") }
            assertEquals(UiText.Raw("服务器原文 %1\$s"), serverMessage.uiErrorDetail())
        }
    }

    @Test fun nativeApiFailuresRemainDescribedWithoutChangingProbeFallback() = runTest {
        val draft = NavidromeSourceDraft(label = "", baseUrl = "https://server.test", username = "u", password = "p")
        val error = failure {
            probeNavidromeLibrary(draft, client { request ->
                if (request.url.contains("/rest/ping")) Result.success(LyricsHttpResponse(200, """{"subsonic-response":{"status":"ok","serverVersion":"0.55.0"}}"""))
                else Result.failure(IOException("原文"))
            })
        }
        assertEquals("Navidrome /auth/login request failed: 原文", resolveUiText(error.uiErrorText(), AppLanguage.English))
    }

    private fun embyDraft() = EmbySourceDraft(label = "", baseUrl = "https://server.test", username = "u", password = "p")
    private fun response(status: Int, body: String) = client { Result.success(LyricsHttpResponse(status, body)) }
    private fun client(action: (LyricsRequest) -> Result<LyricsHttpResponse>) = object : LyricsHttpClient {
        override suspend fun request(request: LyricsRequest) = action(request)
    }
    private suspend fun failure(action: suspend () -> Any?): Throwable = checkNotNull(runCatching { action() }.exceptionOrNull())
    private suspend fun assertDescriptions(error: Throwable, english: String, simplified: String, traditional: String) {
        assertIs<UiTextFailure>(error)
        val text = error.uiErrorText()
        listOf(AppLanguage.English to english, AppLanguage.SimplifiedChinese to simplified, AppLanguage.TraditionalChinese to traditional, AppLanguage.English to english)
            .forEach { (language, expected) -> assertEquals(expected, resolveUiText(text, language)) }
    }
}
