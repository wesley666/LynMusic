package top.iwesley.lyn.music.domain

import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.NetworkConnectionState
import top.iwesley.lyn.music.core.model.NetworkConnectionType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import top.iwesley.lyn.music.core.model.FnMusicConnectionMode
import top.iwesley.lyn.music.core.model.FnMusicSourceDraft
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.buildFnMusicCoverLocator
import top.iwesley.lyn.music.core.model.buildFnMusicSongLocator
import top.iwesley.lyn.music.core.model.parseFnMusicCoverLocator
import top.iwesley.lyn.music.core.model.parseFnMusicSongLocator

class FnMusicEngineTest {
    @Test
    fun `signature matches the reference implementation`() {
        // Expected values come from the Swift FnMusicWire.signature with the same inputs.
        assertEquals(
            "nonce=123456&timestamp=1700000000000&sign=3aec888e521b8b0fada93805bd465680",
            fnMusicAuthx(
                url = "http://192.168.1.2:5666/music/api/v1/search/track?size=20&q=a%20b%2Bc&page=1",
                nonce = "123456",
                timestampMillis = 1700000000000,
            ),
        )
        assertEquals(
            "nonce=654321&timestamp=1700000000123&sign=e9e3bdd3844852b4ed2d70754b243bd0",
            fnMusicAuthx(
                url = "https://nas.example.com/music/api/v1/user/password-login",
                body = """{"deviceId":"dev","password":"abc","username":"u"}""",
                nonce = "654321",
                timestampMillis = 1700000000123,
            ),
        )
        assertEquals(
            "nonce=111111&timestamp=1700000000000&sign=7667769df49d90c70cbf7162bb536673",
            fnMusicAuthx(
                url = "https://5ddd.com/api/v1/fn/con",
                body = """{"fnId":"mynas"}""",
                key = "zIGtkc3dqZnJpd29qZXJqa2w7c",
                nonce = "111111",
                timestampMillis = 1700000000000,
            ),
        )
    }

    @Test
    fun `sha256 matches the reference implementation`() {
        assertEquals("cf8052ab56c2f915bc567c35c1afef7df3b9e7db4aaa0af3b41a899f5284548d", sha256Hex("p@ss 密码"))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(""))
    }

    @Test
    fun `builds API URLs with the music prefix and sorted query`() {
        assertEquals(
            "http://nas:5666/music/api/v1/track/list?page=1&size=2&sort=createdAt%2Casc",
            buildFnMusicApiUrl("http://nas:5666", "/track/list", listOf("sort" to "createdAt,asc", "size" to "2", "page" to "1")),
        )
        assertEquals("https://nas/x/music/api/v1/sys/config", buildFnMusicApiUrl("https://nas/x/music", "/sys/config"))
        assertEquals("https://nas/music/api/v1/sys/config", buildFnMusicApiUrl("https://nas/music/api/v1/", "/sys/config"))
        assertEquals("http://nas/music/api/v1/search/track?q=a%2Bb", buildFnMusicApiUrl("http://nas", "/search/track", listOf("q" to "a+b")))
    }

    @Test
    fun `normalizes FN IDs and base URLs`() {
        assertEquals("mynas", normalizeFnMusicId(" MyNas "))
        assertEquals("mynas", normalizeFnMusicId("mynas.5ddd.com"))
        assertEquals("my-nas_1", normalizeFnMusicId("https://my-nas_1.5ddd.com/"))
        assertFailsWith<IllegalArgumentException> { normalizeFnMusicId("nas.example.com") }
        assertFailsWith<IllegalArgumentException> { normalizeFnMusicId("bad id") }
        assertFailsWith<IllegalArgumentException> { normalizeFnMusicId("") }
        assertEquals("https://nas.example.com/base", normalizeFnMusicBaseUrl(" https://nas.example.com:443/base/ "))
        assertFailsWith<IllegalArgumentException> { normalizeFnMusicBaseUrl("ftp://nas") }
    }

    @Test
    fun `round trips locators`() {
        val song = buildFnMusicSongLocator("fnmusic-1", "a/b guid")
        assertEquals("fnmusic-1" to "a/b guid", parseFnMusicSongLocator(song))
        assertNull(parseFnMusicCoverLocator(song))
        assertEquals("fnmusic-1" to "cover", parseFnMusicCoverLocator(buildFnMusicCoverLocator("fnmusic-1", "cover")))
    }

    @Test
    fun `redirect headers drop credentials across origins and re-sign on the same origin`() {
        val headers = mapOf("Cookie" to "music-token=t", "authx" to "old", "x-access-code" to "c", "Range" to "bytes=0-")
        val crossOrigin = fnMusicRedirectHeaders("https://nas/music/api/v1/track/stream?guid=1", "https://cdn.example.com/f.flac", headers)
        assertEquals(mapOf("Range" to "bytes=0-"), crossOrigin)
        val sameOrigin = fnMusicRedirectHeaders("https://nas/music/api/v1/track/stream?guid=1", "https://nas/music/api/v1/track/stream?guid=2", headers)!!
        assertEquals("music-token=t", sameOrigin["Cookie"])
        assertTrue(sameOrigin.getValue("authx").startsWith("nonce="))
        assertNull(fnMusicRedirectHeaders("https://nas/a", "http://nas/b", headers))
        assertNull(fnMusicRedirectHeaders("https://nas/a", "https://user:pw@nas/b", headers))
    }

    @Test
    fun `scan logs in, pages tracks, skips CUE tracks and maps fields`() = runTest {
        val client = FakeFnMusicServer(
            tracks = listOf(
                trackJson("g1", "Song 1", ext = "flac", cover = "c1"),
                trackJson("g2", "Song 2", ext = "mp3"),
                """{"guid":"g3","title":"Cue","isCue":1,"audioSpec":{"extension":"flac"}}""",
                trackJson("g4", "Song 4", ext = "xyz"),
            ),
            pageSize = 2,
        )
        val report = scanFnMusicLibrary(
            draft = addressDraft(),
            sourceId = "fnmusic-1",
            deviceId = "device",
            httpClient = client,
            supportedImportExtensions = setOf("flac", "mp3"),
        )
        assertEquals(listOf("Song 1", "Song 2"), report.tracks.map { it.title })
        val first = report.tracks.first()
        assertEquals(buildFnMusicSongLocator("fnmusic-1", "g1"), first.mediaLocator)
        assertEquals(buildFnMusicCoverLocator("fnmusic-1", "c1"), first.artworkLocator)
        assertEquals("Artist", first.artistName)
        assertEquals("Album", first.albumTitle)
        assertEquals(180_000L, first.durationMs)
        assertEquals(3, report.discoveredAudioFileCount)
        assertEquals(1, report.failures.size)
        assertEquals(1, report.warnings.size)

        val login = client.requests.first()
        assertEquals(RequestMethod.POST, login.method)
        assertTrue(login.url.endsWith("/music/api/v1/user/password-login"))
        val loginBody = Json.parseToJsonElement(login.body!!) as JsonObject
        assertEquals(sha256Hex("secret"), loginBody["password"].toString().trim('"'))
        assertEquals("""{"deviceId":"device","password":"${sha256Hex("secret")}","username":"user"}""", login.body)
        val list = client.requests.first { it.url.contains("/track/list") }
        assertEquals("music-token=token-1", list.headers["Cookie"])
        assertTrue(list.headers.getValue("authx").contains("sign="))
        assertEquals("1", list.queryParam("page"))
        assertEquals("100", list.queryParam("size"))
    }

    @Test
    fun `re-authenticates once when the token expires`() = runTest {
        val client = FakeFnMusicServer(tracks = listOf(trackJson("g1", "Song 1", ext = "flac")), expireFirstToken = true)
        val report = scanFnMusicLibrary(addressDraft(), "fnmusic-1", "device", client, setOf("flac"))
        assertEquals(1, report.tracks.size)
        assertEquals(2, client.requests.count { it.url.contains("password-login") })
    }

    @Test
    fun `server error codes surface as failures`() = runTest {
        val client = RecordingFnHttpClient { request ->
            if (request.url.contains("password-login")) {
                LyricsHttpResponse(200, """{"code":10001,"msg":"wrong password"}""")
            } else {
                error("unexpected ${request.url}")
            }
        }
        val failure = assertFailsWith<Throwable> { testFnMusicConnection(addressDraft(), "device", client) }
        assertTrue(failure.message.orEmpty().contains("wrong password") || failure.toString().contains("fn_music_server_error"))
    }

    @Test
    fun `access code is sent base64 encoded`() = runTest {
        val client = FakeFnMusicServer(tracks = emptyList())
        testFnMusicConnection(addressDraft().copy(accessCode = "1234"), "device", client)
        val request = client.requests.last()
        assertEquals(Base64.getEncoder().encodeToString("1234".toByteArray()), request.headers["x-access-code"])
        assertEquals("app", request.headers["x-access-source"])
    }

    @Test
    fun `FN Connect resolves endpoints in priority order and uses relays last`() = runTest {
        val payload = Json.parseToJsonElement(
            """{"ipv4":["192.168.1.2"],"ipv6":"fe80::1","publicIpv4":["1.2.3.4"],"port":{"httpPort":5666,"httpsPort":5667}}""",
        ) as JsonObject
        val groups = fnConnectEndpointGroups("mynas", payload)
        assertEquals(listOf("http://192.168.1.2:5666", "http://[fe80::1]:5666"), groups[0].map { it.baseUrl })
        assertEquals(listOf("https://192.168.1.2:5667", "https://[fe80::1]:5667"), groups[1].map { it.baseUrl })
        assertTrue(groups[2].isEmpty())
        assertEquals(listOf("https://1.2.3.4:5667"), groups[3].map { it.baseUrl })
        assertEquals(listOf("https://mynas.5ddd.com"), groups[4].map { it.baseUrl })
        assertTrue(groups[4].single().relay)
        assertFalse(groups[0].first().relay)
    }

    @Test
    fun `FN Connect falls back to the relay with the relay cookie`() = runTest {
        val client = RecordingFnHttpClient { request ->
            when {
                request.url == "https://5ddd.com/api/v1/fn/con" -> {
                    assertTrue(request.headers.getValue("authx").contains("sign="))
                    LyricsHttpResponse(200, """{"code":0,"data":{"ipv4":["10.0.0.9"],"fn":["mynas.5ddd.com"]}}""")
                }
                request.url.startsWith("http://10.0.0.9") || request.url.startsWith("https://10.0.0.9") -> error("unreachable host")
                request.url.endsWith("/access_code_verify") -> LyricsHttpResponse(404, "")
                request.url.contains("/sys/config") -> LyricsHttpResponse(200, """{"code":0,"data":{}}""")
                request.url.contains("password-login") -> LyricsHttpResponse(200, """{"code":0,"data":{"userToken":"tok"}}""")
                request.url.contains("/track/list") -> LyricsHttpResponse(200, """{"code":0,"data":{"list":[],"total":0}}""")
                else -> error("unexpected ${request.url}")
            }
        }
        val draft = FnMusicSourceDraft(
            label = "NAS",
            connectionMode = FnMusicConnectionMode.FN_CONNECT,
            fnId = "MyNas",
            username = "user",
            password = "secret",
        )
        testFnMusicConnection(draft, "device", client)
        val list = client.requests.last()
        assertTrue(list.url.startsWith("https://mynas.5ddd.com/music/api/v1/track/list"))
        assertEquals("music-token=tok; mode=relay", list.headers["Cookie"])
    }

    @Test
    fun `FN Connect re-resolution skips the host whose media requests failed`() = runTest {
        val client = RecordingFnHttpClient { request ->
            when {
                request.url == "https://5ddd.com/api/v1/fn/con" ->
                    LyricsHttpResponse(200, """{"code":0,"data":{"ipv4":["10.0.0.9"],"fn":["mynas.5ddd.com"]}}""")
                request.url.endsWith("/access_code_verify") -> LyricsHttpResponse(404, "")
                // The LAN NAS still answers its config probe; only its media requests fail.
                request.url.contains("/sys/config") -> LyricsHttpResponse(200, """{"code":0,"data":{}}""")
                request.url.contains("password-login") -> LyricsHttpResponse(200, """{"code":0,"data":{"userToken":"tok"}}""")
                else -> error("unexpected ${request.url}")
            }
        }
        val connection = FnMusicSourceDraft("NAS", FnMusicConnectionMode.FN_CONNECT, fnId = "mynas", username = "u", password = "p")
            .toFnMusicConnection("fnmusic-exclude-test", "device")
        val fnClient = fnMusicClient(connection, client, addressSelector = null)

        val first = fnClient.mediaRequests("/track/stream", listOf("guid" to "g1")).single()
        assertEquals("http://10.0.0.9:5666/music/api/v1/track/stream?guid=g1", first.value)

        val probesBefore = client.requests.size
        fnClient.invalidateRoute()
        val retried = fnClient.mediaRequests("/track/stream", listOf("guid" to "g1"), setOf(fnMusicApiRootOf(first.value))).single()

        assertEquals("https://mynas.5ddd.com/music/api/v1/track/stream?guid=g1", retried.value)
        assertEquals("music-token=tok; mode=relay", retried.headers["Cookie"])
        val reprobed = client.requests.drop(probesBefore).map { it.url }
        assertTrue(reprobed.none { it.contains("10.0.0.9") }, "re-probed the failed LAN host: $reprobed")
        assertTrue(reprobed.any { it.startsWith("https://mynas.5ddd.com") })

        // Excluding every endpoint falls back to all of them rather than failing outright.
        fnClient.invalidateRoute()
        val everything = setOf(fnMusicApiRootOf(first.value), fnMusicApiRootOf(retried.value))
        assertEquals(first.value, fnClient.mediaRequests("/track/stream", listOf("guid" to "g1"), everything).single().value)
        FnMusicSessions.invalidate("fnmusic-exclude-test")
    }

    @Test
    fun `address mode re-resolution drops the failed address while another remains`() = runTest {
        val client = FakeFnMusicServer(tracks = emptyList())
        val connection = addressDraft().copy(wanBaseUrl = "https://music.example.com")
            .toFnMusicConnection("fnmusic-address-exclude-test", "device")
        val fnClient = fnMusicClient(connection, client, addressSelector = null)
        val lanRoot = "http://192.168.1.2:5666/music/api/v1"
        val wanRoot = "https://music.example.com/music/api/v1"

        assertEquals(listOf(lanRoot, wanRoot), fnClient.mediaRequests("/track/stream", emptyList()).map { fnMusicApiRootOf(it.value) })
        assertEquals(listOf(wanRoot), fnClient.mediaRequests("/track/stream", emptyList(), setOf(lanRoot)).map { fnMusicApiRootOf(it.value) })
        assertEquals(
            listOf(lanRoot, wanRoot),
            fnClient.mediaRequests("/track/stream", emptyList(), setOf(lanRoot, wanRoot)).map { fnMusicApiRootOf(it.value) },
        )
        FnMusicSessions.invalidate("fnmusic-address-exclude-test")
    }

    @Test
    fun `FN Connect rejects a wrong access code`() = runTest {
        val client = RecordingFnHttpClient { request ->
            when {
                request.url == "https://5ddd.com/api/v1/fn/con" -> LyricsHttpResponse(200, """{"code":0,"data":{"fn":["mynas.5ddd.com"]}}""")
                request.url.endsWith("/access_code_verify") -> LyricsHttpResponse(401, "")
                else -> error("unexpected ${request.url}")
            }
        }
        val draft = FnMusicSourceDraft("NAS", FnMusicConnectionMode.FN_CONNECT, fnId = "mynas", username = "u", password = "p", accessCode = "bad")
        assertFailsWith<FnMusicAccessCodeException> { testFnMusicConnection(draft, "device", client) }
    }

    @Test
    fun `picks the preferred lyric`() = runTest {
        val client = FakeFnMusicServer(
            tracks = emptyList(),
            lyrics = """{"list":[{"guid":"l1","content":"[00:01.00]one"},{"guid":"l2","content":"[00:02.00]two"}],"preferred":"l2"}""",
        )
        val source = FnMusicResolvedSource(
            sourceId = "fnmusic-1",
            connection = addressDraft().toFnMusicConnection("fnmusic-lyrics-test", "device"),
        )
        val document = requestFnMusicLyricsDocument(client, source, "g1", addressSelector = null)
        assertEquals("two", document?.lines?.single()?.text)
        assertEquals(FN_MUSIC_LYRICS_SOURCE_ID, document?.sourceId)
    }

    @Test
    fun `playlist mutations send sorted JSON bodies`() = runTest {
        val client = FakeFnMusicServer(tracks = emptyList())
        val source = FnMusicResolvedSource(
            sourceId = "fnmusic-1",
            connection = addressDraft().toFnMusicConnection("fnmusic-playlist-test", "device"),
        )
        addFnMusicPlaylistTracks(client, source, "p1", listOf("g1", "g2"), addressSelector = null)
        val request = client.requests.last()
        assertTrue(request.url.endsWith("/music/api/v1/playlist/add-track"))
        assertEquals("""{"guid":"p1","trackGUIDs":["g1","g2"]}""", request.body)
    }

    @Test
    fun `recognizes refused media sessions`() {
        assertTrue(isFnMusicMediaAuthFailure(401, null))
        assertTrue(isFnMusicMediaAuthFailure(403, null))
        assertTrue(isFnMusicMediaAuthFailure(200, """{"code":120001,"msg":"x"}"""))
        assertTrue(isFnMusicMediaAuthFailure(200, """{"code":5,"msg":"Invalid Token"}"""))
        assertFalse(isFnMusicMediaAuthFailure(200, """{"code":10001,"msg":"not found"}"""))
        assertFalse(isFnMusicMediaAuthFailure(404, null))
        assertFalse(isFnMusicMediaAuthFailure(500, "not json"))
    }

    @Test
    fun `replacing the session token keeps relay cookie and access code`() {
        val headers = mapOf("Cookie" to "music-token=old%2Btok; mode=relay", "x-access-code" to "MTIzNA==", "authx" to "sig")
        assertEquals("old+tok", fnMusicTokenFromHeaders(headers))
        val refreshed = withFnMusicToken(headers, "new")
        assertEquals("music-token=new; mode=relay", refreshed["Cookie"])
        assertEquals("MTIzNA==", refreshed["x-access-code"])
        assertEquals("new", fnMusicTokenFromHeaders(refreshed))
    }

    @Test
    fun `reauthorizing logs in once per stale token`() = runTest {
        val server = FakeFnMusicServer(tracks = emptyList())
        val source = FnMusicResolvedSource(
            sourceId = "fnmusic-reauth",
            connection = addressDraft().toFnMusicConnection("fnmusic-reauth-test", "device"),
        )
        val client = source.client(server, addressSelector = null)
        val first = client.reauthorize(staleToken = null)
        assertEquals("token-1", first)

        val results = coroutineScope {
            listOf(async { client.reauthorize(first) }, async { client.reauthorize(first) }).awaitAll()
        }

        assertEquals(listOf("token-2", "token-2"), results)
        assertEquals("token-2", client.reauthorize("token-1"))
        assertEquals(2, server.requests.count { it.url.contains("password-login") })
        FnMusicSessions.invalidate("fnmusic-reauth-test")
    }

    @Test
    fun `network change switches to WAN or re-resolves a LAN-only route`() {
        val mobile = NetworkConnectionState(NetworkConnectionType.MOBILE, version = 2)
        val wifi = NetworkConnectionState(NetworkConnectionType.WIFI, version = 2)
        val lan = RemoteSourceAddressKind.LAN
        val wan = RemoteSourceAddressKind.WAN
        assertEquals(RemoteStreamNetworkAction.Switch(1), remoteStreamNetworkAction(listOf(lan, wan), 0, mobile, true))
        assertEquals(RemoteStreamNetworkAction.Reresolve, remoteStreamNetworkAction(listOf(lan), 0, mobile, true))
        assertEquals(RemoteStreamNetworkAction.None, remoteStreamNetworkAction(listOf(wan), 0, mobile, true))
        assertEquals(RemoteStreamNetworkAction.None, remoteStreamNetworkAction(listOf(lan), 0, wifi, true))
        assertEquals(RemoteStreamNetworkAction.None, remoteStreamNetworkAction(listOf(lan), 0, mobile, false))
        assertEquals(
            RemoteStreamNetworkAction.None,
            remoteStreamNetworkAction(listOf(lan), 0, mobile.copy(isConnected = false), true),
        )
    }

    @Test
    fun `media requests do not ask for JSON while API calls do`() = runTest {
        val server = FakeFnMusicServer(tracks = emptyList())
        val fnClient = fnMusicClient(addressDraft().toFnMusicConnection("fnmusic-accept-test", "device"), server, addressSelector = null)

        val media = fnClient.mediaRequests("/track/stream", listOf("guid" to "g1")).single()
        fnClient.call("playlist/add-track", "/playlist/add-track", body = JsonObject(emptyMap()))

        assertNull(media.headers["Accept"])
        assertEquals("application/json", server.requests.last().headers["Accept"])
        FnMusicSessions.invalidate("fnmusic-accept-test")
    }

    @Test
    fun `a write is only resent elsewhere when it certainly never left`() = runTest {
        fun client(lanFailure: () -> Throwable) = RecordingFnHttpClient { request ->
            when {
                request.url.startsWith("http://192.168.1.2") && !request.url.contains("password-login") -> throw lanFailure()
                request.url.contains("password-login") -> LyricsHttpResponse(200, """{"code":0,"data":{"userToken":"tok"}}""")
                else -> LyricsHttpResponse(200, """{"code":0,"data":{}}""")
            }
        }
        val connection = addressDraft().copy(wanBaseUrl = "https://music.example.com")
            .toFnMusicConnection("", "device")

        val refused = client { java.net.ConnectException("Connection refused") }
        fnMusicClient(connection, refused, addressSelector = null)
            .call("playlist/add-track", "/playlist/add-track", body = JsonObject(emptyMap()))
        assertTrue(refused.requests.any { it.url == "https://music.example.com/music/api/v1/playlist/add-track" })

        val timedOut = client { java.net.SocketTimeoutException("Read timed out") }
        assertFailsWith<FnMusicAmbiguousWriteException> {
            fnMusicClient(connection, timedOut, addressSelector = null)
                .call("playlist/add-track", "/playlist/add-track", body = JsonObject(emptyMap()))
        }
        assertTrue(timedOut.requests.none { it.url == "https://music.example.com/music/api/v1/playlist/add-track" })

        val reads = client { java.net.SocketTimeoutException("Read timed out") }
        fnMusicClient(connection, reads, addressSelector = null).call("playlist/list", "/playlist/list")
        assertTrue(reads.requests.any { it.url.startsWith("https://music.example.com/music/api/v1/playlist/list") })
    }

    @Test
    fun `writes forbid the HTTP library from resending them while reads and login allow it`() = runTest {
        val server = FakeFnMusicServer(tracks = emptyList())
        val fnClient = fnMusicClient(addressDraft().toFnMusicConnection("fnmusic-retry-test", "device"), server, addressSelector = null)

        fnClient.call("playlist/list", "/playlist/list")
        fnClient.call("playlist/add-track", "/playlist/add-track", body = JsonObject(emptyMap()))

        assertTrue(server.requests.single { it.url.contains("password-login") }.allowTransportRetry)
        assertTrue(server.requests.single { it.url.contains("/playlist/list") }.allowTransportRetry)
        assertFalse(server.requests.single { it.url.contains("/playlist/add-track") }.allowTransportRetry)
        FnMusicSessions.invalidate("fnmusic-retry-test")
    }

    @Test
    fun `classifies failures that prove a request never left`() {
        assertTrue(isRequestNotSentFailure(java.net.ConnectException("refused")))
        assertTrue(isRequestNotSentFailure(IllegalStateException("wrapped", java.net.UnknownHostException("nas"))))
        assertFalse(isRequestNotSentFailure(java.net.SocketTimeoutException("Read timed out")))
        assertFalse(isRequestNotSentFailure(java.io.IOException("Connection reset")))
    }

    @Test
    fun `a listing that changes mid-walk is walked again instead of failing or missing tracks`() = runTest {
        var walk = 0
        val client = RecordingFnHttpClient { request ->
            when {
                request.url.contains("password-login") -> LyricsHttpResponse(200, """{"code":0,"data":{"userToken":"tok"}}""")
                request.url.contains("/track/list") -> {
                    val page = request.queryParam("page")!!.toInt()
                    if (page == 1) walk += 1
                    // First walk: a track lands while paging, so page 2 reports a new total.
                    val (items, total) = when {
                        page == 1 -> listOf(trackJson("g1", "One", "flac")) to (if (walk == 1) 2 else 3)
                        walk == 1 -> listOf(trackJson("g2", "Two", "flac")) to 3
                        else -> listOf(trackJson("g2", "Two", "flac"), trackJson("g3", "Three", "flac")) to 3
                    }
                    LyricsHttpResponse(200, """{"code":0,"data":{"list":[${items.joinToString(",")}],"total":$total}}""")
                }
                else -> error("unexpected ${request.url}")
            }
        }
        // Page size 1 on the first walk would need a custom pager; the default size still shows the restart.
        val report = scanFnMusicLibrary(addressDraft(), "fnmusic-1", "device", client, setOf("flac"))

        assertEquals(listOf("One", "Two", "Three"), report.tracks.map { it.title })
        assertEquals(2, walk)
    }

    @Test
    fun `a listing that never settles fails after the retries`() = runTest {
        var calls = 0
        val client = RecordingFnHttpClient { request ->
            when {
                request.url.contains("password-login") -> LyricsHttpResponse(200, """{"code":0,"data":{"userToken":"tok"}}""")
                request.url.contains("/track/list") -> {
                    calls += 1
                    val page = request.queryParam("page")!!.toInt()
                    // Every walk sees the total move between page 1 and page 2.
                    val total = if (page == 1) 2 else 3
                    LyricsHttpResponse(200, """{"code":0,"data":{"list":[${trackJson("g$page", "T$page", "flac")}],"total":$total}}""")
                }
                else -> error("unexpected ${request.url}")
            }
        }

        assertFailsWith<Throwable> { scanFnMusicLibrary(addressDraft(), "fnmusic-1", "device", client, setOf("flac")) }
        assertEquals(6, calls)
    }

    @Test
    fun `parallel metadata lookups keep the listing order`() = runTest {
        val tracks = (1..12).map { """{"guid":"g$it","title":"T$it"}""" }
        val client = RecordingFnHttpClient { request ->
            when {
                request.url.contains("password-login") -> LyricsHttpResponse(200, """{"code":0,"data":{"userToken":"tok"}}""")
                request.url.contains("/track/list") ->
                    LyricsHttpResponse(200, """{"code":0,"data":{"list":[${tracks.joinToString(",")}],"total":${tracks.size}}}""")
                request.url.contains("/track/metadata") -> {
                    val guid = request.queryParam("guid")!!
                    LyricsHttpResponse(
                        200,
                        """{"code":0,"data":{"track":{"guid":"$guid","title":"T${guid.drop(1)}"},"audioSpec":{"extension":"flac","duration":1000}}}""",
                    )
                }
                else -> error("unexpected ${request.url}")
            }
        }

        val report = scanFnMusicLibrary(addressDraft(), "fnmusic-1", "device", client, setOf("flac"))

        assertEquals((1..12).map { "T$it" }, report.tracks.map { it.title })
        assertTrue(report.tracks.all { it.durationMs == 1000L })
    }

    @Test
    fun `a cached FN Connect route is not reused against an exclusion or after a network change`() = runTest {
        var resolutions = 0
        val client = RecordingFnHttpClient { request ->
            when {
                request.url == "https://5ddd.com/api/v1/fn/con" -> {
                    resolutions += 1
                    LyricsHttpResponse(200, """{"code":0,"data":{"ipv4":["10.0.0.9"],"fn":["mynas.5ddd.com"]}}""")
                }
                request.url.endsWith("/access_code_verify") -> LyricsHttpResponse(404, "")
                request.url.contains("/sys/config") -> LyricsHttpResponse(200, """{"code":0,"data":{}}""")
                request.url.contains("password-login") -> LyricsHttpResponse(200, """{"code":0,"data":{"userToken":"tok"}}""")
                else -> error("unexpected ${request.url}")
            }
        }
        val network = kotlinx.coroutines.flow.MutableStateFlow(NetworkConnectionState(NetworkConnectionType.WIFI, version = 1))
        val selector = RemoteSourceAddressSelector(
            object : top.iwesley.lyn.music.core.model.NetworkConnectionTypeProvider {
                override val networkConnectionState = network
            },
        )
        val connection = FnMusicSourceDraft("NAS", FnMusicConnectionMode.FN_CONNECT, fnId = "mynas", username = "u", password = "p")
            .toFnMusicConnection("fnmusic-route-test", "device")
        val fnClient = fnMusicClient(connection, client, selector)

        val lan = fnClient.mediaRequests("/track/stream", listOf("guid" to "g1")).single()
        assertEquals(1, resolutions)
        assertEquals(lan.value, fnClient.mediaRequests("/track/stream", listOf("guid" to "g1")).single().value)
        assertEquals(1, resolutions)

        // No invalidateRoute(): the cached LAN route alone must not satisfy an exclusion of its host.
        val relay = fnClient.mediaRequests("/track/stream", listOf("guid" to "g1"), setOf(fnMusicApiRootOf(lan.value))).single()
        assertTrue(relay.value.startsWith("https://mynas.5ddd.com"))
        assertEquals(2, resolutions)

        network.value = NetworkConnectionState(NetworkConnectionType.MOBILE, version = 2)
        fnClient.mediaRequests("/track/stream", listOf("guid" to "g1"))
        assertEquals(3, resolutions)
        FnMusicSessions.invalidate("fnmusic-route-test")
    }

    private fun addressDraft() = FnMusicSourceDraft(
        label = "NAS",
        connectionMode = FnMusicConnectionMode.ADDRESS,
        baseUrl = "http://192.168.1.2:5666",
        username = "user",
        password = "secret",
    )
}

private fun trackJson(guid: String, title: String, ext: String, cover: String? = null): String = """
    {
      "guid": "$guid",
      "title": "$title",
      "artists": [{"guid": "a1", "name": "Artist"}],
      "album": {"guid": "al1", "name": "Album"${cover?.let { ""","coverId":"$it"""" } ?: ""}},
      "trackNo": 1,
      "audioSpec": {"extension": "$ext", "duration": 180000, "size": 1234, "sampleRate": 44100, "bitDepth": 16}
    }
""".trimIndent()

/** A minimal FN Music server: login, paged track list, lyrics and writes. */
private class FakeFnMusicServer(
    private val tracks: List<String>,
    private val pageSize: Int? = null,
    private val expireFirstToken: Boolean = false,
    private val lyrics: String = """{"list":[]}""",
) : LyricsHttpClient {
    val requests = mutableListOf<LyricsRequest>()
    private var logins = 0

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        requests += request
        return runCatching { respond(request) }
    }

    private fun respond(request: LyricsRequest): LyricsHttpResponse {
        val url = request.url
        if (url.contains("/user/password-login")) {
            logins += 1
            return ok("""{"userToken":"token-$logins"}""")
        }
        if (expireFirstToken && request.headers["Cookie"] == "music-token=token-1") {
            return LyricsHttpResponse(200, """{"code":120001,"msg":"invalid token"}""")
        }
        return when {
            url.contains("/track/list") -> {
                val page = request.queryParam("page")!!.toInt()
                val size = pageSize ?: request.queryParam("size")!!.toInt()
                val items = tracks.drop((page - 1) * size).take(size)
                ok("""{"list":[${items.joinToString(",")}],"total":${tracks.size}}""")
            }
            url.contains("/lyric/list") -> ok(lyrics)
            url.contains("/playlist/") -> ok("{}")
            else -> error("unexpected $url")
        }
    }

    private fun ok(data: String) = LyricsHttpResponse(200, """{"code":0,"msg":"ok","data":$data}""")
}

private class RecordingFnHttpClient(
    private val responder: (LyricsRequest) -> LyricsHttpResponse,
) : LyricsHttpClient {
    val requests = mutableListOf<LyricsRequest>()

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        requests += request
        return runCatching { responder(request) }
    }
}

private fun LyricsRequest.queryParam(name: String): String? {
    val query = URI(url).rawQuery ?: return null
    return query.split("&")
        .map { part -> part.substringBefore("=") to part.substringAfter("=", "") }
        .firstOrNull { (key, _) -> key == name }
        ?.second
        ?.let { value -> URLDecoder.decode(value, "UTF-8") }
}
