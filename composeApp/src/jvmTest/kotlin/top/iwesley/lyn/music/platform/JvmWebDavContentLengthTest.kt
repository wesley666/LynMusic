package top.iwesley.lyn.music.platform

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.UiTextFailure
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiFailureTextOrNull

class JvmWebDavContentLengthTest {
    @Test fun successfulProbesKeepRangeHeadersLengthParsingAndCleanup() = runTest {
        listOf(
            Triple(200, mapOf("Content-Length" to "4294967296"), 4_294_967_296L),
            Triple(206, mapOf("Content-Range" to "bytes 0-0/98765", "Content-Length" to "1"), 98_765L),
            Triple(206, mapOf("Content-Range" to "bytes 0-0/*", "Content-Length" to "12"), 12L),
            Triple(200, emptyMap(), 0L),
        ).forEach { (code, headers, expected) ->
            val connection = FakeProbeConnection(code, headers)
            assertEquals(expected, probeJvmWebDavContentLength(connection, authSent = false))
            assertEquals("GET", connection.requestMethod)
            assertEquals("bytes=0-0", connection.getRequestProperty("Range"))
            assertEquals(10_000, connection.connectTimeout)
            assertEquals(10_000, connection.readTimeout)
            assertTrue(connection.instanceFollowRedirects)
            assertTrue(connection.didConnect)
            assertTrue(connection.didDisconnect)
            assertTrue(connection.body.wasClosed)
        }
    }

    @Test fun failedProbesPreserveTypeDiagnosticsAndRawDetailsInAllLanguages() = runTest {
        val detail = "服务端原始详情 %1\$s"
        listOf(401, 403, 503).forEach { code ->
            val connection = FakeProbeConnection(code, responseDetail = detail)
            val failure = assertFailsWith<IOException> { probeJvmWebDavContentLength(connection, authSent = true) }
            assertIs<WebDavUiIOException>(failure)
            assertIs<UiTextFailure>(failure)
            assertEquals("WebDAV content length probe failed with HTTP $code", failure.message)
            val wrapped = IllegalStateException("External load error", IOException("Source error", failure))
            val text = checkNotNull(wrapped.uiFailureTextOrNull())
            val explanations = when (code) {
                401 -> listOf("WebDAV playback failed. Check your credentials.", "WebDAV 播放 失败，请检查当前认证信息。", "WebDAV 播放 失敗，請檢查目前的驗證資訊。")
                403 -> listOf("WebDAV playback failed. This account does not have access.", "WebDAV 播放 失败，当前账号没有访问权限。", "WebDAV 播放 失敗，目前帳號沒有存取權限。")
                else -> listOf("WebDAV playback failed, HTTP 503.", "WebDAV 播放 失败，HTTP 503。", "WebDAV 播放 失敗，HTTP 503。")
            }
            listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese, AppLanguage.English)
                .forEachIndexed { index, language ->
                    val rendered = resolveUiText(text, language)
                    assertTrue(rendered.startsWith(explanations[index % 3]), rendered)
                    assertTrue(rendered.endsWith(detail), rendered)
                }
            assertTrue(connection.didDisconnect)
            assertTrue(connection.body.wasClosed)
        }
    }

    @Test fun authenticationDescriptionReflectsWhetherCredentialsWereSent() = runTest {
        listOf(false, true).forEach { authSent ->
            val challenge = "Basic realm=用户音乐"
            val connection = FakeProbeConnection(401, mapOf("WWW-Authenticate" to challenge), "Ignored reason")
            val failure = assertFailsWith<WebDavUiIOException> { probeJvmWebDavContentLength(connection, authSent) }
            val rendered = resolveUiText(failure.text, AppLanguage.English)
            assertTrue(rendered.startsWith(if (authSent) "WebDAV Basic Auth failed." else "The WebDAV server rejected anonymous access."), rendered)
            assertTrue(rendered.endsWith(challenge))
            assertTrue(!rendered.contains("Ignored reason"))
        }
        val failure = assertFailsWith<WebDavUiIOException> { probeJvmWebDavContentLength(FakeProbeConnection(401), false) }
        assertEquals("The WebDAV server rejected anonymous access. Enter your credentials.", resolveUiText(failure.text, AppLanguage.English))
    }

    @Test fun transportExceptionsKeepTheirOriginalIdentityAndStillDisconnect() = runTest {
        val original = IOException("provider 原文")
        val connection = FakeProbeConnection(200, connectFailure = original)
        assertSame(original, assertFailsWith<IOException> { probeJvmWebDavContentLength(connection, false) })
        assertTrue(connection.didDisconnect)
        assertTrue(connection.body.wasClosed)
    }
}

private class FakeProbeConnection(
    private val status: Int,
    private val headers: Map<String, String> = emptyMap(),
    private val responseDetail: String = "",
    private val connectFailure: IOException? = null,
) : HttpURLConnection(URL("https://example.invalid/music.flac")) {
    val body = ClosingProbeStream()
    var didConnect = false
    var didDisconnect = false
    override fun connect() {
        didConnect = true
        connectFailure?.let { throw it }
    }
    override fun disconnect() { didDisconnect = true }
    override fun usingProxy() = false
    override fun getResponseCode() = status
    override fun getResponseMessage() = responseDetail
    override fun getHeaderField(name: String): String? = headers[name]
    override fun getInputStream(): InputStream = if (status in 200..299) body else throw IOException("HTTP $status")
    override fun getErrorStream(): InputStream? = if (status !in 200..299) body else null
}

private class ClosingProbeStream : ByteArrayInputStream(byteArrayOf(0)) {
    var wasClosed = false
    override fun close() { wasClosed = true; super.close() }
}
