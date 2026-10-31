package top.iwesley.lyn.music.data.repository

import androidx.room.Room
import io.ktor.http.encodedPath
import io.ktor.http.parseUrl
import java.io.Closeable
import java.net.SocketTimeoutException
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import top.iwesley.lyn.music.domain.NavidromeResolvedSource
import top.iwesley.lyn.music.domain.RemoteSourceAddressSelector
import top.iwesley.lyn.music.domain.requestNavidromeSongPage

class NavidromeOnlineAddressTest {
    @Test
    fun `saved online sources load every library view and paginate with single or dual addresses`() = runTest {
        for ((lan, wan) in listOf(LAN to "", "" to WAN, LAN to WAN)) {
            for (network in NetworkConnectionType.entries) {
                OnlineAddressFixture(network).use { fixture ->
                    val id = fixture.save(lan, wan)
                    val stored = assertNotNull(fixture.database.importSourceDao().getById(id))
                    assertEquals(lan, stored.rootReference)
                    assertEquals(wan.takeIf(String::isNotBlank), stored.wanRootReference)
                    val expectedHost = when {
                        lan.isBlank() -> "wan.example"
                        wan.isBlank() -> "lan.example"
                        network == NetworkConnectionType.MOBILE -> "wan.example"
                        else -> "lan.example"
                    }
                    val repository = fixture.repository
                    val first = repository.tracks(id, 0, 1)
                    assertEquals("Song 0", first.items.single().title)
                    assertEquals(2, first.totalCount)
                    assertTrue(first.hasMore)
                    val next = repository.tracks(id, 1, 1)
                    assertEquals("Song 1", next.items.single().title)
                    assertFalse(next.hasMore)
                    assertEquals("Album", repository.albums(id, 0, 10).items.single().album.title)
                    assertEquals("Artist", repository.artists(id, 0, 10).items.single().artist.name)
                    val search = repository.search(id, "Song", 0, 10)
                    assertEquals("Song", search.tracks.single().title)
                    assertEquals("Album", search.albums.single().album.title)
                    assertEquals("Artist", search.artists.single().artist.name)
                    assertEquals("Song", repository.favoriteTracks(id, 0, 10, "").items.single().title)
                    assertEquals("Playlist", repository.playlists(id).single().name)
                    assertEquals(setOf(expectedHost), fixture.http.requests.map { it.parsedUrl().host }.toSet())
                    assertEquals(listOf("0", "1"), fixture.http.requests
                        .filter { it.parsedUrl().encodedPath.endsWith("/api/song") }
                        .map { it.parsedUrl().parameters["_start"] })
                    assertTrue(fixture.http.requests.all { it.timeoutMillis == IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS })
                    assertEquals(0, fixture.database.trackDao().count())
                }
            }
        }
    }

    @Test
    fun `saved online sources tolerate missing and whitespace optional addresses`() = runTest {
        for ((lan, wan) in listOf(LAN to null, LAN to "", LAN to " \t ", " \t " to WAN)) {
            OnlineAddressFixture().use { fixture ->
                val id = fixture.save(LAN, WAN)
                val stored = assertNotNull(fixture.database.importSourceDao().getById(id))
                fixture.database.importSourceDao().upsert(stored.copy(rootReference = lan, wanRootReference = wan))
                assertEquals("Album", fixture.repository.albums(id, 0, 10).items.single().album.title)
                assertEquals("Song 0", fixture.repository.tracks(id, 0, 1).items.single().title)
                assertEquals(setOf(if (lan.isBlank()) "wan.example" else "lan.example"),
                    fixture.http.requests.map { it.parsedUrl().host }.toSet())
            }
        }
    }

    @Test
    fun `invalid saved online addresses report Navidrome before making requests`() = runTest {
        for ((lan, wan) in listOf("" to null, " \t " to " ", "invalid" to null, "" to "invalid")) {
            OnlineAddressFixture().use { fixture ->
                val id = fixture.save(LAN, WAN)
                val stored = assertNotNull(fixture.database.importSourceDao().getById(id))
                fixture.database.importSourceDao().upsert(stored.copy(rootReference = lan, wanRootReference = wan))
                val failure = assertFails { fixture.repository.tracks(id, 0, 1) }
                assertIs<UiTextFailure>(failure)
                val message = resolveUiText(failure.uiErrorText(), AppLanguage.English)
                assertTrue(message.contains("Navidrome"), message)
                assertFalse(message.contains("Subsonic"), message)
                assertTrue(fixture.http.requests.isEmpty())
            }
        }
    }

    @Test
    fun `native paging accepts a WAN-only resolved source`() = runTest {
        val http = OnlineAddressHttpClient()
        val page = requestNavidromeSongPage(http, nativeSource(lan = "", wan = WAN), start = 1, pageSize = 1)
        assertEquals("Song 1", page.songs.single().title)
        assertEquals(listOf("wan.example/auth/login", "wan.example/api/song"), http.requests.map { it.endpoint() })
    }

    @Test
    fun `native paging follows network changes`() = runTest {
        val network = OnlineAddressNetwork(NetworkConnectionType.WIFI)
        val source = nativeSource(selector = RemoteSourceAddressSelector(network))
        val http = OnlineAddressHttpClient()
        for ((index, type) in listOf(NetworkConnectionType.WIFI, NetworkConnectionType.MOBILE, NetworkConnectionType.WIFI).withIndex()) {
            network.publish(type)
            requestNavidromeSongPage(http, source, start = index, pageSize = 1)
            val host = if (type == NetworkConnectionType.MOBILE) "wan.example" else "lan.example"
            assertEquals(listOf("$host/auth/login", "$host/api/song"), http.requests.takeLast(2).map { it.endpoint() })
        }
    }

    @Test
    fun `native paging retries a complete login and page on transport failure and remembers the address`() = runTest {
        for (network in NetworkConnectionType.entries) {
            for (phase in listOf("/auth/login", "/api/song")) {
                val failedHost = if (network == NetworkConnectionType.WIFI) "lan.example" else "wan.example"
                val alternate = if (network == NetworkConnectionType.WIFI) "wan.example" else "lan.example"
                val source = nativeSource(selector = RemoteSourceAddressSelector(OnlineAddressNetwork(network)))
                val http = OnlineAddressHttpClient { request ->
                    if (request.endpoint() == "$failedHost$phase") Result.failure(SocketTimeoutException("timeout")) else null
                }
                val page = requestNavidromeSongPage(http, source, start = 0, pageSize = 1, timeoutMillis = 25_000)
                assertEquals("Song 0", page.songs.single().title)
                val firstAttempt = if (phase == "/auth/login") listOf("$failedHost/auth/login")
                    else listOf("$failedHost/auth/login", "$failedHost/api/song")
                assertEquals(firstAttempt + listOf("$alternate/auth/login", "$alternate/api/song"), http.requests.map { it.endpoint() })
                requestNavidromeSongPage(http, source, start = 1, pageSize = 1, timeoutMillis = 25_000)
                assertEquals(listOf("$alternate/auth/login", "$alternate/api/song"), http.requests.takeLast(2).map { it.endpoint() })
                assertTrue(http.requests.all { it.timeoutMillis == 25_000L })
            }
        }
    }

    @Test
    fun `native paging only retries retryable HTTP failures`() = runTest {
        for (phase in listOf("/auth/login", "/api/song")) {
            for (status in listOf(401, 403, 404, 408, 503)) {
                val http = OnlineAddressHttpClient { request ->
                    if (request.endpoint() == "lan.example$phase") Result.success(LyricsHttpResponse(status, "")) else null
                }
                val result = runCatching { requestNavidromeSongPage(http, nativeSource(), 0, 1) }
                val retryable = status == 408 || status == 503
                assertEquals(retryable, result.isSuccess, "$phase HTTP $status")
                val alternateRequests = http.requests.filter { it.parsedUrl().host == "wan.example" }
                assertEquals(if (retryable) listOf("wan.example/auth/login", "wan.example/api/song") else emptyList(),
                    alternateRequests.map { it.endpoint() })
                if (!retryable) assertTrue(resolveUiText(result.exceptionOrNull()!!.uiErrorText(), AppLanguage.English).contains(status.toString()))
            }
        }
    }

    @Test
    fun `native paging propagates returned and thrown cancellation without fallback`() = runTest {
        for (phase in listOf("/auth/login", "/api/song")) {
            for (returned in listOf(false, true)) {
                val cancellation = CancellationException("cancelled")
                val http = OnlineAddressHttpClient { request ->
                    if (!request.parsedUrl().encodedPath.endsWith(phase)) null
                    else if (returned) Result.failure(cancellation) else throw cancellation
                }
                val failure = assertFailsWith<CancellationException> { requestNavidromeSongPage(http, nativeSource(), 0, 1) }
                assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it === cancellation })
                assertTrue(http.requests.none { it.parsedUrl().host == "wan.example" })
            }
        }
    }

    @Test
    fun `native paging preserves direct single-address requests without a selector`() = runTest {
        val http = OnlineAddressHttpClient()
        val page = requestNavidromeSongPage(http, nativeSource().copy(sourceId = null, addressSelector = null), -1, 0)
        assertEquals("Song 0", page.songs.single().title)
        val url = http.requests.last().parsedUrl()
        assertEquals("0", url.parameters["_start"])
        assertEquals("1", url.parameters["_end"])
    }
}

private const val LAN = "https://lan.example"
private const val WAN = "https://wan.example"

private fun nativeSource(
    lan: String = LAN,
    wan: String? = WAN,
    selector: RemoteSourceAddressSelector = RemoteSourceAddressSelector(),
) = NavidromeResolvedSource(lan, wan, "online-source", selector, username = "user", password = "password")

private class OnlineAddressNetwork(type: NetworkConnectionType) : NetworkConnectionTypeProvider {
    override val networkConnectionState = MutableStateFlow(NetworkConnectionState(type))
    fun publish(type: NetworkConnectionType) {
        networkConnectionState.value = NetworkConnectionState(type, networkConnectionState.value.version + 1)
    }
}

private fun LyricsRequest.parsedUrl() = requireNotNull(parseUrl(url))
private fun LyricsRequest.endpoint() = parsedUrl().let { it.host + it.encodedPath }

private class OnlineAddressHttpClient(
    private val overrideResponse: (LyricsRequest) -> Result<LyricsHttpResponse>? = { null },
) : LyricsHttpClient {
    val requests = mutableListOf<LyricsRequest>()

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        requests += request
        overrideResponse(request)?.let { return it }
        val url = request.parsedUrl()
        val song = """{"id":"song","title":"Song","path":"Song.flac"}"""
        val album = """{"id":"album","name":"Album"}"""
        val artist = """{"id":"artist","name":"Artist"}"""
        val payload = when (url.encodedPath) {
            "/auth/login" -> return Result.success(LyricsHttpResponse(200, """{"token":"token-${url.host}"}"""))
            "/api/song" -> {
                assertEquals("Bearer token-${url.host}", request.headers["X-ND-Authorization"])
                val start = url.parameters["_start"] ?: error("Missing page offset")
                return Result.success(LyricsHttpResponse(200,
                    """[{"id":"song-$start","title":"Song $start","path":"Song $start.flac"}]""",
                    mapOf("X-Total-Count" to "2")))
            }
            "/rest/getAlbumList2" -> """"albumList2":{"album":[$album]}"""
            "/rest/getArtists" -> """"artists":{"index":[{"artist":[$artist]}]}"""
            "/rest/search3" -> """"searchResult3":{"song":[$song],"album":[$album],"artist":[$artist]}"""
            "/rest/getStarred2" -> """"starred2":{"song":[$song]}"""
            "/rest/getPlaylists" -> """"playlists":{"playlist":[{"id":"playlist","name":"Playlist","songCount":1}]}"""
            else -> error("Unexpected request: ${request.url}")
        }
        return Result.success(LyricsHttpResponse(200, """{"subsonic-response":{"status":"ok",$payload}}"""))
    }
}

private class OnlineAddressFixture(network: NetworkConnectionType = NetworkConnectionType.WIFI) : Closeable {
    private val path = Files.createTempFile("lynmusic-online-address", ".db")
    val database = buildLynMusicDatabase(Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()))
    private val credentials = object : SecureCredentialStore {
        val values = mutableMapOf<String, String>()
        override suspend fun get(key: String) = values[key]
        override suspend fun put(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }
    val http = OnlineAddressHttpClient()
    val repository = NavidromeOnlineRepository(database, credentials, http,
        addressSelector = RemoteSourceAddressSelector(OnlineAddressNetwork(network)))

    suspend fun save(lan: String, wan: String): String = RoomImportSourceRepository(database, UnusedOnlineAddressGateway, credentials)
        .addNavidromeSourceOnline(NavidromeSourceDraft("Online", lan, wan, "user", "password"), remoteTrackCount = 2)
        .getOrThrow().sourceId

    override fun close() {
        database.close()
        Files.deleteIfExists(path)
    }
}

private object UnusedOnlineAddressGateway : ImportSourceGateway {
    override suspend fun pickLocalFolder(): LocalFolderSelection? = error("No folder request expected")
    override suspend fun scanLocalFolder(selection: LocalFolderSelection, sourceId: String): ImportScanReport = error("No scan expected")
    override suspend fun testSamba(draft: SambaSourceDraft): Unit = error("No Samba request expected")
    override suspend fun scanSamba(draft: SambaSourceDraft, sourceId: String): ImportScanReport = error("No scan expected")
    override suspend fun testWebDav(draft: WebDavSourceDraft): Unit = error("No WebDAV request expected")
    override suspend fun scanWebDav(draft: WebDavSourceDraft, sourceId: String): ImportScanReport = error("No scan expected")
    override suspend fun testNavidrome(draft: NavidromeSourceDraft): Unit = error("No connection test expected")
    override suspend fun scanNavidrome(draft: NavidromeSourceDraft, sourceId: String): ImportScanReport = error("No scan expected")
}
