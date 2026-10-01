package top.iwesley.lyn.music

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.buildBasicAuthorizationHeader
import top.iwesley.lyn.music.core.model.buildWebDavLocator
import top.iwesley.lyn.music.core.model.buildWebDavTrackUrl
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.WebDavOperation
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.webDavHttpFailureText
import top.iwesley.lyn.music.resources.Res
import top.iwesley.lyn.music.resources.webdav_anonymous_basic_auth_required
import top.iwesley.lyn.music.resources.webdav_failure_with_server_detail
import top.iwesley.lyn.music.core.model.displayWebDavRootUrl
import top.iwesley.lyn.music.core.model.normalizeWebDavRootUrl
import top.iwesley.lyn.music.core.model.parseWebDavLocator
import top.iwesley.lyn.music.core.model.parseWebDavMultistatus
import top.iwesley.lyn.music.core.model.resolveWebDavRelativePath

class WebDavTest {

    @Test
    fun `normalize webdav root url drops default port and keeps trailing slash`() {
        val normalized = normalizeWebDavRootUrl(" https://dav.example.com:443/music/library ")

        assertEquals("https://dav.example.com/music/library/", normalized)
    }

    @Test
    fun `normalize webdav root url encodes chinese path segments`() {
        val normalized = normalizeWebDavRootUrl("https://dav.example.com/中文 音乐/流行")

        assertEquals(
            "https://dav.example.com/%E4%B8%AD%E6%96%87%20%E9%9F%B3%E4%B9%90/%E6%B5%81%E8%A1%8C/",
            normalized,
        )
    }

    @Test
    fun `display webdav root url decodes encoded path segments`() {
        val displayUrl = displayWebDavRootUrl(
            "https://dav.example.com/%E4%B8%AD%E6%96%87%20%E9%9F%B3%E4%B9%90/%E6%B5%81%E8%A1%8C/",
        )

        assertEquals("https://dav.example.com/中文 音乐/流行/", displayUrl)
    }

    @Test
    fun `display webdav root url keeps readable url readable`() {
        assertEquals(
            "https://dav.example.com/中文 音乐/",
            displayWebDavRootUrl("https://dav.example.com/中文 音乐/"),
        )
    }

    @Test
    fun `display webdav root url returns original value when url is invalid`() {
        assertEquals("not a url", displayWebDavRootUrl("not a url"))
    }

    @Test
    fun `webdav locator round trips relative path`() {
        val locator = buildWebDavLocator("dav-1", "流行/Blue Sky.mp3")

        assertEquals("dav-1" to "流行/Blue Sky.mp3", parseWebDavLocator(locator))
    }

    @Test
    fun `build webdav track url encodes path segments`() {
        val url = buildWebDavTrackUrl("https://dav.example.com/music/", "流行/Blue Sky #1.mp3")

        assertTrue(url.contains("%E6%B5%81%E8%A1%8C"))
        assertTrue(url.contains("Blue%20Sky%20%231.mp3"))
    }

    @Test
    fun `resolve webdav relative path handles absolute and relative hrefs`() {
        val rootUrl = "https://dav.example.com/music/library/"

        assertEquals(
            "Artist/Song One.mp3",
            resolveWebDavRelativePath(rootUrl, "https://dav.example.com/music/library/Artist/Song%20One.mp3"),
        )
        assertEquals(
            "Artist/Song Two.mp3",
            resolveWebDavRelativePath(rootUrl, "/music/library/Artist/Song%20Two.mp3"),
        )
        assertEquals(
            "Artist/Song Three.mp3",
            resolveWebDavRelativePath(rootUrl, "Artist/Song%20Three.mp3"),
        )
    }

    @Test
    fun `parse webdav multistatus extracts directories and files`() {
        val payload = """
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/music/library/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><d:collection/></d:resourcetype>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/music/library/Artist/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><d:collection/></d:resourcetype>
                    <d:getlastmodified>Tue, 01 Apr 2025 12:00:00 GMT</d:getlastmodified>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>https://dav.example.com/music/library/Artist/Song%20One.mp3</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype/>
                    <d:getlastmodified>Tue, 01 Apr 2025 12:01:00 GMT</d:getlastmodified>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/music/library/ignored.txt</d:href>
                <d:propstat>
                  <d:status>HTTP/1.1 404 Not Found</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entries = parseWebDavMultistatus(payload)

        assertEquals(3, entries.size)
        assertTrue(entries.any { it.isDirectory && it.href.contains("/Artist/") })
        assertTrue(entries.any { !it.isDirectory && it.href.contains("Song%20One.mp3") })
        assertFalse(entries.any { it.href.contains("ignored.txt") })
    }

    @Test
    fun `basic authorization header is omitted for anonymous access`() {
        assertEquals(null, buildBasicAuthorizationHeader("", ""))

        val header = buildBasicAuthorizationHeader("guest", "1234")

        assertNotNull(header)
        assertTrue(header.startsWith("Basic "))
    }

    @Test
    fun `webdav 401 without auth explains anonymous access is rejected`() = runTest {
        val serverDetail = "Basic realm=\"Restricted\""
        val text = webDavHttpFailureText(
            operation = WebDavOperation.Scan,
            statusCode = 401,
            authSent = false,
            serverDetail = serverDetail,
        )

        assertEquals(
            uiText(Res.string.webdav_failure_with_server_detail,
                uiText(Res.string.webdav_anonymous_basic_auth_required), UiText.Raw(serverDetail)),
            text,
        )
        for ((language, expected) in listOf(
            AppLanguage.English to "The WebDAV server rejected anonymous access. Enter a Basic Auth username. Server details: $serverDetail",
            AppLanguage.SimplifiedChinese to "WebDAV 服务端拒绝匿名访问，需要填写 Basic Auth 用户名。 服务端信息: $serverDetail",
            AppLanguage.TraditionalChinese to "WebDAV 伺服器拒絕匿名存取，需要填寫 Basic Auth 使用者名稱。 伺服器資訊: $serverDetail",
        )) {
            assertEquals(expected, resolveUiText(text, language))
        }
    }
}
