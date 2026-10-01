package top.iwesley.lyn.music.data.repository

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.resources.Res
import top.iwesley.lyn.music.resources.update_http_failed
import top.iwesley.lyn.music.resources.update_empty_response
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.LynMusicUpdateLinks
import top.iwesley.lyn.music.core.model.RequestMethod

class AppUpdateRepositoryTest {
    @Test
    fun `latest release parses github response`() = runTest {
        val httpClient = RecordingAppUpdateHttpClient(
            Result.success(
                LyricsHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "tag_name": "v1.0.8.1",
                          "name": "v1.0.8.1 Release",
                          "body": "支持悬浮窗歌词",
                          "html_url": "https://github.com/wesley666/LynMusic/releases/tag/v1.0.8.1",
                          "published_at": "2026-05-17T10:45:00Z"
                        }
                    """.trimIndent(),
                ),
            ),
        )

        val result = DefaultAppUpdateRepository(httpClient).latestRelease()

        val release = assertNotNull(result.getOrNull())
        assertEquals("v1.0.8.1", release.tagName)
        assertEquals("v1.0.8.1 Release", release.name)
        assertEquals("支持悬浮窗歌词", release.body)
        assertEquals("https://github.com/wesley666/LynMusic/releases/tag/v1.0.8.1", release.htmlUrl)
        assertEquals("2026-05-17T10:45:00Z", release.publishedAt)
        val request = httpClient.requests.single()
        assertEquals(RequestMethod.GET, request.method)
        assertEquals(LynMusicUpdateLinks.LATEST_RELEASE_API_URL, request.url)
        assertEquals("application/vnd.github+json", request.headers["Accept"])
    }

    @Test
    fun `latest release falls back to releases url when html url is missing`() = runTest {
        val httpClient = RecordingAppUpdateHttpClient(
            Result.success(
                LyricsHttpResponse(
                    statusCode = 200,
                    body = """{"tag_name":"v1.0.8.1","name":"","body":"","published_at":""}""",
                ),
            ),
        )

        val release = assertNotNull(DefaultAppUpdateRepository(httpClient).latestRelease().getOrNull())

        assertEquals("v1.0.8.1", release.name)
        assertEquals(LynMusicUpdateLinks.RELEASES_URL, release.htmlUrl)
    }

    @Test
    fun `latest release fails on non ok response`() = runTest {
        val httpClient = RecordingAppUpdateHttpClient(
            Result.success(LyricsHttpResponse(statusCode = 404, body = "{}")),
        )

        val result = DefaultAppUpdateRepository(httpClient).latestRelease()

        assertTrue(result.isFailure)
        val failure = assertIs<UiTextException>(result.exceptionOrNull())
        assertEquals(uiText(Res.string.update_http_failed, 404), failure.text)
        for ((language, expected) in listOf(
            AppLanguage.English to "Update check failed: HTTP 404",
            AppLanguage.SimplifiedChinese to "检查更新失败：HTTP 404",
            AppLanguage.TraditionalChinese to "檢查更新失敗：HTTP 404",
        )) {
            assertEquals(expected, resolveUiText(failure.text, language))
        }
    }

    @Test
    fun `latest release fails on empty body`() = runTest {
        val httpClient = RecordingAppUpdateHttpClient(
            Result.success(LyricsHttpResponse(statusCode = 200, body = "")),
        )

        val result = DefaultAppUpdateRepository(httpClient).latestRelease()

        assertTrue(result.isFailure)
        val failure = assertIs<UiTextException>(result.exceptionOrNull())
        assertEquals(uiText(Res.string.update_empty_response), failure.text)
        for ((language, expected) in listOf(
            AppLanguage.English to "Update check failed: the response is empty.",
            AppLanguage.SimplifiedChinese to "检查更新失败：响应为空。",
            AppLanguage.TraditionalChinese to "檢查更新失敗：回應為空。",
        )) {
            assertEquals(expected, resolveUiText(failure.text, language))
        }
    }

    @Test
    fun `latest release keeps http client failure`() = runTest {
        val httpClient = RecordingAppUpdateHttpClient(
            Result.failure(IllegalStateException("network down")),
        )

        val result = DefaultAppUpdateRepository(httpClient).latestRelease()

        assertTrue(result.isFailure)
        assertEquals("network down", result.exceptionOrNull()?.message)
    }
}

private class RecordingAppUpdateHttpClient(
    private val result: Result<LyricsHttpResponse>,
) : LyricsHttpClient {
    val requests = mutableListOf<LyricsRequest>()

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        requests += request
        return result
    }
}
