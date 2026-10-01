package top.iwesley.lyn.music

import io.ktor.http.parseUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS
import top.iwesley.lyn.music.core.model.ImportScanPhase
import top.iwesley.lyn.music.core.model.ImportScanProgress
import top.iwesley.lyn.music.core.model.ImportScanProgressSink
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.UNSUPPORTED_AUDIO_IMPORT_REASON
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.domain.NAVIDROME_LYRICS_SOURCE_ID
import top.iwesley.lyn.music.domain.NavidromeResolvedSource
import top.iwesley.lyn.music.domain.buildNavidromeStreamUrl
import top.iwesley.lyn.music.domain.normalizeNavidromeBaseUrl
import top.iwesley.lyn.music.domain.requestNavidromeLyrics
import top.iwesley.lyn.music.domain.scanNavidromeLibrary

class NavidromeEngineTest {

    @Test
    fun `normalize base url strips rest suffix`() {
        assertEquals(
            "https://demo.example.com/navidrome",
            normalizeNavidromeBaseUrl("https://demo.example.com/navidrome/rest"),
        )
    }

    @Test
    fun `stream url keeps original quality without transcoding parameters`() {
        val url = buildNavidromeStreamUrl(
            baseUrl = "https://demo.example.com/navidrome",
            username = "demo",
            password = "secret",
            songId = "song-1",
            audioQuality = NavidromeAudioQuality.Original,
        )
        val parsed = checkNotNull(parseUrl(url))

        assertEquals("song-1", parsed.parameters["id"])
        assertEquals(null, parsed.parameters["maxBitRate"])
        assertEquals(null, parsed.parameters["format"])
    }

    @Test
    fun `stream url adds mp3 transcode parameters for limited quality`() {
        val url = buildNavidromeStreamUrl(
            baseUrl = "https://demo.example.com/navidrome",
            username = "demo",
            password = "secret",
            songId = "song-1",
            audioQuality = NavidromeAudioQuality.Kbps192,
        )
        val parsed = checkNotNull(parseUrl(url))

        assertEquals("song-1", parsed.parameters["id"])
        assertEquals("192", parsed.parameters["maxBitRate"])
        assertEquals("mp3", parsed.parameters["format"])
    }

    @Test
    fun `scan library imports navidrome native song pages`() = runTest {
        val firstPage = nativeSongsJson(
            buildList {
                add(
                    nativeSongJson(
                        id = "song-1",
                        title = "Blue",
                        artist = "Artist A",
                        album = "Album A",
                        path = "Artist A/Album A/Blue.flac",
                        duration = "215.25",
                        trackNumber = 4,
                        discNumber = 1,
                        size = 12345L,
                        albumId = "cover-1",
                        bitDepth = 16,
                        sampleRate = 44_100,
                        bitRate = 880,
                        channels = 2,
                    ),
                )
                addAll(
                    (2..1000).map { index ->
                        nativeSongJson(
                            id = "song-$index",
                            title = "Song $index",
                            artist = "Artist A",
                            album = "Album A",
                            path = "Artist A/Album A/Song $index.flac",
                        )
                    },
                )
            },
        )
        val client = RoutingNavidromeHttpClient(
            nativeSongPages = mapOf(
                0 to firstPage,
                1000 to nativeSongsJson(
                    nativeSongJson(
                        id = "song-1001",
                        title = "Last",
                        artist = "Artist B",
                        album = "Album B",
                        path = "Artist B/Album B/Last.flac",
                    ),
                ),
            ),
            nativeSongHeaders = mapOf(
                0 to mapOf("x-total-count" to "1001"),
                1000 to mapOf("X-Total-Count" to "1001"),
            ),
        )

        val progressEvents = mutableListOf<ImportScanProgress>()
        val report = scanNavidromeLibrary(
            draft = NavidromeSourceDraft(
                label = "Navidrome",
                baseUrl = "https://demo.example.com/navidrome/rest",
                username = "demo",
                password = "plain-pass",
            ),
            sourceId = "nav-source",
            httpClient = client,
            supportedImportExtensions = setOf("flac"),
            progressSink = ImportScanProgressSink { progressEvents += it },
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )

        val candidate = report.tracks.first()
        assertEquals(1001, report.discoveredAudioFileCount)
        assertEquals(1001, report.tracks.size)
        assertEquals(1001, report.totalTrackCount)
        assertTrue(report.failures.isEmpty())
        assertTrue(report.warnings.isEmpty())
        assertEquals(
            listOf(
                ImportScanProgress(
                    sourceId = "nav-source",
                    phase = ImportScanPhase.Scanning,
                    importedTrackCount = 1000,
                    totalTrackCount = 1001,
                ),
                ImportScanProgress(
                    sourceId = "nav-source",
                    phase = ImportScanPhase.Scanning,
                    importedTrackCount = 1001,
                    totalTrackCount = 1001,
                ),
            ),
            progressEvents,
        )
        assertEquals("Blue", candidate.title)
        assertEquals("Artist A", candidate.artistName)
        assertEquals("Album A", candidate.albumTitle)
        assertEquals(215_250L, candidate.durationMs)
        assertEquals(4, candidate.trackNumber)
        assertEquals(1, candidate.discNumber)
        assertEquals(12345L, candidate.sizeBytes)
        assertEquals(16, candidate.bitDepth)
        assertEquals(44100, candidate.samplingRate)
        assertEquals(880, candidate.bitRate)
        assertEquals(2, candidate.channelCount)
        assertEquals("Artist A/Album A/Blue.flac", candidate.relativePath)
        assertEquals("lynmusic-navidrome://nav-source/song-1", candidate.mediaLocator)
        assertEquals("lynmusic-navidrome-cover://nav-source/cover-1", candidate.artworkLocator)
        assertEquals(
            listOf(
                IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
                IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
                IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
                IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
            ),
            client.requests.map { it.timeoutMillis },
        )
        assertEquals(
            listOf(
                "/navidrome/rest/ping",
                "/navidrome/auth/login",
                "/navidrome/api/song",
                "/navidrome/api/song",
            ),
            client.requests.map { requireNotNull(parseUrl(it.url)).encodedPath },
        )
        assertEquals("0", requireNotNull(parseUrl(client.requests[2].url)).parameters["_start"])
        assertEquals("1000", requireNotNull(parseUrl(client.requests[2].url)).parameters["_end"])
        assertEquals("path", requireNotNull(parseUrl(client.requests[2].url)).parameters["_sort"])
        assertEquals("ASC", requireNotNull(parseUrl(client.requests[2].url)).parameters["_order"])
        assertEquals("1000", requireNotNull(parseUrl(client.requests[3].url)).parameters["_start"])
        assertEquals("2000", requireNotNull(parseUrl(client.requests[3].url)).parameters["_end"])
    }

    @Test
    fun `scan library falls back to rest scan for old navidrome server version`() = runTest {
        val client = RoutingNavidromeHttpClient(
            responses = legacyScanResponses(PING_OLD_NAVIDROME_JSON),
        )

        val progressEvents = mutableListOf<ImportScanProgress>()
        val report = scanNavidromeLibrary(
            draft = NavidromeSourceDraft(
                label = "Navidrome",
                baseUrl = "https://demo.example.com/navidrome",
                username = "demo",
                password = "plain-pass",
            ),
            sourceId = "nav-source",
            httpClient = client,
            supportedImportExtensions = setOf("flac"),
            progressSink = ImportScanProgressSink { progressEvents += it },
        )

        assertEquals(1, report.tracks.size)
        assertEquals("Blue", report.tracks.single().title)
        assertEquals(null, report.totalTrackCount)
        assertEquals(listOf(1), progressEvents.map { it.importedTrackCount })
        assertEquals(
            listOf(
                "/navidrome/rest/ping",
                "/navidrome/rest/getArtists",
                "/navidrome/rest/getArtist",
                "/navidrome/rest/getAlbum",
            ),
            client.requests.map { requireNotNull(parseUrl(it.url)).encodedPath },
        )
    }

    @Test
    fun `scan library falls back to rest scan when server type is not navidrome`() = runTest {
        val client = RoutingNavidromeHttpClient(
            responses = legacyScanResponses(PING_SUBSONIC_JSON),
        )

        val report = scanNavidromeLibrary(
            draft = NavidromeSourceDraft(
                label = "Navidrome",
                baseUrl = "https://demo.example.com/navidrome",
                username = "demo",
                password = "plain-pass",
            ),
            sourceId = "nav-source",
            httpClient = client,
            supportedImportExtensions = setOf("flac"),
        )

        assertEquals(listOf("Blue"), report.tracks.map { it.title })
        assertEquals(
            listOf(
                "/navidrome/rest/ping",
                "/navidrome/rest/getArtists",
                "/navidrome/rest/getArtist",
                "/navidrome/rest/getAlbum",
            ),
            client.requests.map { requireNotNull(parseUrl(it.url)).encodedPath },
        )
    }

    @Test
    fun `scan library falls back when native login endpoint is incompatible`() = runTest {
        val client = RoutingNavidromeHttpClient(
            responses = legacyScanResponses(PING_UNKNOWN_VERSION_NAVIDROME_JSON),
            nativeLoginStatusCode = 404,
        )

        val report = scanNavidromeLibrary(
            draft = NavidromeSourceDraft(
                label = "Navidrome",
                baseUrl = "https://demo.example.com/navidrome",
                username = "demo",
                password = "plain-pass",
            ),
            sourceId = "nav-source",
            httpClient = client,
            supportedImportExtensions = setOf("flac"),
        )

        assertEquals(listOf("Blue"), report.tracks.map { it.title })
        assertEquals(null, report.totalTrackCount)
        assertEquals(
            listOf(
                "/navidrome/rest/ping",
                "/navidrome/auth/login",
                "/navidrome/rest/getArtists",
                "/navidrome/rest/getArtist",
                "/navidrome/rest/getAlbum",
            ),
            client.requests.map { requireNotNull(parseUrl(it.url)).encodedPath },
        )
    }

    @Test
    fun `scan library falls back when first native song page endpoint is incompatible`() = runTest {
        val client = RoutingNavidromeHttpClient(
            responses = legacyScanResponses(PING_UNKNOWN_VERSION_NAVIDROME_JSON),
            nativeSongStatusCode = 404,
        )

        val report = scanNavidromeLibrary(
            draft = NavidromeSourceDraft(
                label = "Navidrome",
                baseUrl = "https://demo.example.com/navidrome",
                username = "demo",
                password = "plain-pass",
            ),
            sourceId = "nav-source",
            httpClient = client,
            supportedImportExtensions = setOf("flac"),
        )

        assertEquals(listOf("Blue"), report.tracks.map { it.title })
        assertEquals(null, report.totalTrackCount)
        assertEquals(
            listOf(
                "/navidrome/rest/ping",
                "/navidrome/auth/login",
                "/navidrome/api/song",
                "/navidrome/rest/getArtists",
                "/navidrome/rest/getArtist",
                "/navidrome/rest/getAlbum",
            ),
            client.requests.map { requireNotNull(parseUrl(it.url)).encodedPath },
        )
    }

    @Test
    fun `scan library fails when native page request fails`() = runTest {
        val client = RoutingNavidromeHttpClient(
            nativeSongStatusCode = 500,
        )

        assertFailsWith<Exception> {
            scanNavidromeLibrary(
                draft = NavidromeSourceDraft(
                    label = "Navidrome",
                    baseUrl = "https://demo.example.com/navidrome",
                    username = "demo",
                    password = "plain-pass",
                ),
                sourceId = "nav-source",
                httpClient = client,
                supportedImportExtensions = setOf("flac"),
            )
        }
    }

    @Test
    fun `scan library does not fall back after native batch was emitted`() = runTest {
        val firstPage = nativeSongsJson(
            (1..1000).map { index ->
                nativeSongJson(
                    id = "song-$index",
                    title = "Song $index",
                    path = "Artist A/Album A/Song $index.flac",
                )
            },
        )
        val client = RoutingNavidromeHttpClient(
            responses = legacyScanResponses(),
            nativeSongPages = mapOf(0 to firstPage),
            nativeSongStatusCodes = mapOf(1000 to 404),
        )

        assertFailsWith<Exception> {
            scanNavidromeLibrary(
                draft = NavidromeSourceDraft(
                    label = "Navidrome",
                    baseUrl = "https://demo.example.com/navidrome",
                    username = "demo",
                    password = "plain-pass",
                ),
                sourceId = "nav-source",
                httpClient = client,
                supportedImportExtensions = setOf("flac"),
            )
        }

        val paths = client.requests.map { requireNotNull(parseUrl(it.url)).encodedPath }
        assertEquals(
            listOf(
                "/navidrome/rest/ping",
                "/navidrome/auth/login",
                "/navidrome/api/song",
                "/navidrome/api/song",
            ),
            paths,
        )
        assertFalse(paths.any { it.endsWith("/rest/getArtists") })
    }

    @Test
    fun `scan library imports without native total count header`() = runTest {
        val client = RoutingNavidromeHttpClient(
            nativeSongPages = mapOf(
                0 to nativeSongsJson(
                    nativeSongJson(
                        id = "song-1",
                        title = "Blue",
                        path = "Artist A/Album A/Blue.flac",
                    ),
                ),
            ),
        )

        val report = scanNavidromeLibrary(
            draft = NavidromeSourceDraft(
                label = "Navidrome",
                baseUrl = "https://demo.example.com/navidrome",
                username = "demo",
                password = "plain-pass",
            ),
            sourceId = "nav-source",
            httpClient = client,
            supportedImportExtensions = setOf("flac"),
        )

        assertEquals(1, report.tracks.size)
        assertEquals(null, report.totalTrackCount)
    }

    @Test
    fun `scan library counts unsupported suffixes as failures without empty warning`() = runTest {
        val client = RoutingNavidromeHttpClient(
            nativeSongPages = mapOf(
                0 to nativeSongsJson(
                    nativeSongJson(
                        id = "bad-1",
                        title = "Bad Ogg",
                        artist = "Artist A",
                        album = "Album A",
                        path = "Artist A/Album A/Bad Ogg.ogg",
                        suffix = "ogg",
                    ),
                ),
            ),
        )

        val report = scanNavidromeLibrary(
            draft = NavidromeSourceDraft(
                label = "Navidrome",
                baseUrl = "https://demo.example.com/navidrome",
                username = "demo",
                password = "plain-pass",
            ),
            sourceId = "nav-source",
            httpClient = client,
            supportedImportExtensions = setOf("flac"),
        )

        assertEquals(1, report.discoveredAudioFileCount)
        assertTrue(report.tracks.isEmpty())
        assertEquals(listOf("Artist A/Album A/Bad Ogg.ogg"), report.failures.map { it.relativePath })
        assertEquals(listOf(UNSUPPORTED_AUDIO_IMPORT_REASON), report.failures.map { it.reason })
        assertTrue(report.warnings.isEmpty())
    }

    @Test
    fun `scan library deduplicates song ids and treats blank or unknown suffix as failure`() = runTest {
        val client = RoutingNavidromeHttpClient(
            nativeSongPages = mapOf(
                0 to nativeSongsJson(
                    nativeSongJson(
                        id = "song-1",
                        title = "Blue",
                        artist = "Artist A",
                        album = "Album A",
                        path = "Artist A/Album A/Blue.flac",
                    ),
                    nativeSongJson(
                        id = "song-1",
                        title = "Duplicate Blue",
                        artist = "Artist A",
                        album = "Album A",
                        path = "Artist A/Album A/Duplicate Blue.flac",
                    ),
                    nativeSongJson(
                        id = "song-2",
                        title = "No Suffix",
                        artist = "Artist A",
                        album = "Album A",
                        path = "Artist A/Album A/No Suffix",
                        suffix = "",
                    ),
                    nativeSongJson(
                        id = "song-3",
                        title = "Mystery",
                        artist = "Artist A",
                        album = "Album A",
                        path = "Artist A/Album A/Mystery.dsf",
                        suffix = "dsf",
                    ),
                ),
            ),
        )

        val report = scanNavidromeLibrary(
            draft = NavidromeSourceDraft(
                label = "Navidrome",
                baseUrl = "https://demo.example.com/navidrome",
                username = "demo",
                password = "plain-pass",
            ),
            sourceId = "nav-source",
            httpClient = client,
            supportedImportExtensions = setOf("flac"),
        )

        assertEquals(3, report.discoveredAudioFileCount)
        assertEquals(listOf("song-1"), report.tracks.map { it.mediaLocator.substringAfterLast('/') })
        assertEquals(
            listOf("Artist A/Album A/No Suffix", "Artist A/Album A/Mystery.dsf"),
            report.failures.map { it.relativePath },
        )
        assertEquals(
            listOf(UNSUPPORTED_AUDIO_IMPORT_REASON, UNSUPPORTED_AUDIO_IMPORT_REASON),
            report.failures.map { it.reason },
        )
        assertTrue(report.warnings.isEmpty())
    }

    @Test
    fun `scan library warns only when navidrome returns no songs`() = runTest {
        val client = RoutingNavidromeHttpClient()

        val report = scanNavidromeLibrary(
            draft = NavidromeSourceDraft(
                label = "Navidrome",
                baseUrl = "https://demo.example.com/navidrome",
                username = "demo",
                password = "plain-pass",
            ),
            sourceId = "nav-source",
            httpClient = client,
            supportedImportExtensions = setOf("flac"),
        )

        assertEquals(0, report.discoveredAudioFileCount)
        assertTrue(report.tracks.isEmpty())
        assertTrue(report.failures.isEmpty())
        val warning = report.warnings.single()
        assertEquals("当前 Navidrome 账号下没有可同步的歌曲。", warning.diagnostic)
        assertNotNull(warning.text)
    }

    @Test
    fun `scan library fails when native login fails`() = runTest {
        val client = RoutingNavidromeHttpClient(
            responses = legacyScanResponses(),
            nativeLoginStatusCode = 401,
        )

        assertFailsWith<Exception> {
            scanNavidromeLibrary(
                draft = NavidromeSourceDraft(
                    label = "Navidrome",
                    baseUrl = "https://demo.example.com/navidrome",
                    username = "demo",
                    password = "plain-pass",
                ),
                sourceId = "nav-source",
                httpClient = client,
                supportedImportExtensions = setOf("flac"),
            )
        }
        assertEquals(
            listOf(
                "/navidrome/rest/ping",
                "/navidrome/auth/login",
            ),
            client.requests.map { requireNotNull(parseUrl(it.url)).encodedPath },
        )
    }

    @Test
    fun `scan library falls back when native login response has no token`() = runTest {
        val client = RoutingNavidromeHttpClient(
            responses = legacyScanResponses(PING_UNKNOWN_VERSION_NAVIDROME_JSON),
            nativeLoginBody = """{"name":"demo"}""",
        )

        val report = scanNavidromeLibrary(
            draft = NavidromeSourceDraft(
                label = "Navidrome",
                baseUrl = "https://demo.example.com/navidrome",
                username = "demo",
                password = "plain-pass",
            ),
            sourceId = "nav-source",
            httpClient = client,
            supportedImportExtensions = setOf("flac"),
        )

        assertEquals(listOf("Blue"), report.tracks.map { it.title })
    }

    @Test
    fun `scan library falls back when first native song response is not an array`() = runTest {
        val client = RoutingNavidromeHttpClient(
            responses = legacyScanResponses(PING_UNKNOWN_VERSION_NAVIDROME_JSON),
            nativeSongPages = mapOf(0 to "{invalid"),
        )

        val report = scanNavidromeLibrary(
            draft = NavidromeSourceDraft(
                label = "Navidrome",
                baseUrl = "https://demo.example.com/navidrome",
                username = "demo",
                password = "plain-pass",
            ),
            sourceId = "nav-source",
            httpClient = client,
            supportedImportExtensions = setOf("flac"),
        )

        assertEquals(listOf("Blue"), report.tracks.map { it.title })
        assertEquals(null, report.totalTrackCount)
    }

    @Test
    fun `scan library falls back when first native song array contains non object element`() = runTest {
        val client = RoutingNavidromeHttpClient(
            responses = legacyScanResponses(PING_UNKNOWN_VERSION_NAVIDROME_JSON),
            nativeSongPages = mapOf(0 to """["bad"]"""),
        )

        val report = scanNavidromeLibrary(
            draft = NavidromeSourceDraft(
                label = "Navidrome",
                baseUrl = "https://demo.example.com/navidrome",
                username = "demo",
                password = "plain-pass",
            ),
            sourceId = "nav-source",
            httpClient = client,
            supportedImportExtensions = setOf("flac"),
        )

        assertEquals(listOf("Blue"), report.tracks.map { it.title })
        assertEquals(
            listOf(
                "/navidrome/rest/ping",
                "/navidrome/auth/login",
                "/navidrome/api/song",
                "/navidrome/rest/getArtists",
                "/navidrome/rest/getArtist",
                "/navidrome/rest/getAlbum",
            ),
            client.requests.map { requireNotNull(parseUrl(it.url)).encodedPath },
        )
    }

    @Test
    fun `scan library fails when native song id is blank`() = runTest {
        val client = RoutingNavidromeHttpClient(
            nativeSongPages = mapOf(
                0 to nativeSongsJson(
                    nativeSongJson(
                        id = "",
                        title = "Blue",
                        path = "Artist A/Album A/Blue.flac",
                    ),
                ),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            scanNavidromeLibrary(
                draft = NavidromeSourceDraft(
                    label = "Navidrome",
                    baseUrl = "https://demo.example.com/navidrome",
                    username = "demo",
                    password = "plain-pass",
                ),
                sourceId = "nav-source",
                httpClient = client,
                supportedImportExtensions = setOf("flac"),
            )
        }
        assertFalse(
            client.requests
                .map { requireNotNull(parseUrl(it.url)).encodedPath }
                .any { it.endsWith("/rest/getArtists") },
        )
    }

    @Test
    fun `request lyrics uses token auth without plaintext password`() = runTest {
        var capturedRequest: LyricsRequest? = null
        val client = object : LyricsHttpClient {
            override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
                capturedRequest = request
                return Result.success(
                    LyricsHttpResponse(
                        statusCode = 200,
                        body = GET_LYRICS_BY_SONG_ID_JSON,
                    ),
                )
            }
        }

        val lyrics = requestNavidromeLyrics(
            httpClient = client,
            source = NavidromeResolvedSource(
                baseUrl = "https://demo.example.com/navidrome",
                username = "demo",
                password = "plain-pass",
            ),
            track = Track(
                id = "track-1",
                sourceId = "nav-source",
                title = "Blue",
                artistName = "Artist A",
                mediaLocator = buildNavidromeSongLocator("nav-source", "song-1"),
                relativePath = "Artist A/Album A/Blue.flac",
            ),
        )

        val request = requireNotNull(capturedRequest)
        val requestUrl = request.url
        val parsed = requireNotNull(parseUrl(requestUrl))
        assertNotNull(lyrics)
        assertEquals(NAVIDROME_LYRICS_SOURCE_ID, lyrics.sourceId)
        assertEquals("getLyricsBySongId", parsed.encodedPath.substringAfterLast('/'))
        assertEquals("song-1", parsed.parameters["id"])
        assertEquals("demo", parsed.parameters["u"])
        assertEquals("json", parsed.parameters["f"])
        assertFalse(requestUrl.contains("plain-pass"))
        assertFalse(parsed.parameters.names().contains("p"))
        assertEquals(null, request.timeoutMillis)
        assertTrue(lyrics.isSynced)
    }

    @Test
    fun `request lyrics prefers structured lyrics by song id`() = runTest {
        val client = RoutingNavidromeHttpClient(
            responses = mapOf(
                "getLyricsBySongId" to GET_LYRICS_BY_SONG_ID_JSON,
                "getLyrics" to GET_LYRICS_JSON,
            ),
        )

        val lyrics = requestNavidromeLyrics(
            httpClient = client,
            source = NavidromeResolvedSource(
                baseUrl = "https://demo.example.com/navidrome",
                username = "demo",
                password = "plain-pass",
            ),
            track = Track(
                id = "track-1",
                sourceId = "nav-source",
                title = "Blue",
                artistName = "Artist A",
                mediaLocator = buildNavidromeSongLocator("nav-source", "song-1"),
                relativePath = "Artist A/Album A/Blue.flac",
            ),
        )

        assertNotNull(lyrics)
        assertTrue(lyrics.isSynced)
        assertEquals(2, lyrics.lines.size)
        assertEquals(1_000L, lyrics.lines.first().timestampMs)
        assertEquals("first line", lyrics.lines.first().text)
    }
}

private class RoutingNavidromeHttpClient(
    private val responses: Map<String, String> = emptyMap(),
    private val nativeSongPages: Map<Int, String> = emptyMap(),
    private val nativeSongHeaders: Map<Int, Map<String, String>> = emptyMap(),
    private val nativeLoginStatusCode: Int = 200,
    private val nativeLoginBody: String = """{"token":"native-token"}""",
    private val nativeSongStatusCode: Int = 200,
    private val nativeSongStatusCodes: Map<Int, Int> = emptyMap(),
) : LyricsHttpClient {
    val requests = mutableListOf<LyricsRequest>()

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        requests += request
        val path = requireNotNull(parseUrl(request.url)).encodedPath
        if (path.endsWith("/auth/login")) {
            return Result.success(
                LyricsHttpResponse(
                    statusCode = nativeLoginStatusCode,
                    body = nativeLoginBody,
                ),
            )
        }
        if (path.endsWith("/api/song")) {
            val start = requireNotNull(parseUrl(request.url)).parameters["_start"]?.toIntOrNull() ?: 0
            return Result.success(
                LyricsHttpResponse(
                    statusCode = nativeSongStatusCodes[start] ?: nativeSongStatusCode,
                    body = nativeSongPages[start] ?: "[]",
                    headers = nativeSongHeaders[start].orEmpty(),
                ),
            )
        }
        val endpoint = path.substringAfterLast('/')
        val body = responses[endpoint]
            ?: PING_NAVIDROME_JSON.takeIf { endpoint == "ping" }
            ?: return Result.failure(IllegalArgumentException("Unexpected Navidrome endpoint: $endpoint"))
        return Result.success(LyricsHttpResponse(statusCode = 200, body = body))
    }
}

private fun nativeSongsJson(songs: List<String>): String = songs.joinToString(prefix = "[", postfix = "]")

private fun nativeSongsJson(vararg songs: String): String = nativeSongsJson(songs.toList())

private fun nativeSongJson(
    id: String,
    title: String,
    artist: String? = null,
    album: String? = null,
    path: String? = null,
    suffix: String? = "flac",
    duration: String = "215",
    trackNumber: Int? = null,
    discNumber: Int? = null,
    size: Long = 0L,
    albumId: String? = null,
    bitDepth: Int? = null,
    sampleRate: Int? = null,
    bitRate: Int? = null,
    channels: Int? = null,
): String {
    return buildString {
        append('{')
        appendJsonField("id", id)
        appendJsonField("title", title)
        artist?.let { appendJsonField("artist", it) }
        album?.let { appendJsonField("album", it) }
        path?.let { appendJsonField("path", it) }
        suffix?.let { appendJsonField("suffix", it) }
        append(""","duration":""")
        append(duration)
        trackNumber?.let { append(""","trackNumber":"""); append(it) }
        discNumber?.let { append(""","discNumber":"""); append(it) }
        append(""","size":""")
        append(size)
        albumId?.let { appendJsonField("albumId", it) }
        bitDepth?.let { append(""","bitDepth":"""); append(it) }
        sampleRate?.let { append(""","sampleRate":"""); append(it) }
        bitRate?.let { append(""","bitRate":"""); append(it) }
        channels?.let { append(""","channels":"""); append(it) }
        append('}')
    }
}

private fun StringBuilder.appendJsonField(name: String, value: String) {
    if (length > 1) append(',')
    append('"')
    append(name)
    append("\":\"")
    append(value.replace("\\", "\\\\").replace("\"", "\\\""))
    append('"')
}

private fun legacyScanResponses(pingBody: String = PING_NAVIDROME_JSON): Map<String, String> {
    return mapOf(
        "ping" to pingBody,
        "getArtists" to GET_ARTISTS_JSON,
        "getArtist" to GET_ARTIST_JSON,
        "getAlbum" to GET_ALBUM_JSON,
    )
}

private const val PING_NAVIDROME_JSON = """
{
  "subsonic-response": {
    "status": "ok",
    "version": "1.16.1",
    "type": "navidrome",
    "serverVersion": "0.59.0"
  }
}
"""

private const val PING_OLD_NAVIDROME_JSON = """
{
  "subsonic-response": {
    "status": "ok",
    "version": "1.16.1",
    "type": "navidrome",
    "serverVersion": "v0.43.0 (old)"
  }
}
"""

private const val PING_UNKNOWN_VERSION_NAVIDROME_JSON = """
{
  "subsonic-response": {
    "status": "ok",
    "version": "1.16.1",
    "type": "navidrome"
  }
}
"""

private const val PING_SUBSONIC_JSON = """
{
  "subsonic-response": {
    "status": "ok",
    "version": "1.16.1",
    "type": "subsonic",
    "serverVersion": "1.0.0"
  }
}
"""

private const val GET_ARTISTS_JSON = """
{
  "subsonic-response": {
    "status": "ok",
    "version": "1.16.1",
    "artists": {
      "ignoredArticles": "",
      "index": [
        {
          "name": "A",
          "artist": [
            {
              "id": "artist-1",
              "name": "Artist A"
            }
          ]
        }
      ]
    }
  }
}
"""

private const val GET_ARTIST_JSON = """
{
  "subsonic-response": {
    "status": "ok",
    "version": "1.16.1",
    "artist": {
      "id": "artist-1",
      "name": "Artist A",
      "album": [
        {
          "id": "album-1",
          "name": "Album A"
        }
      ]
    }
  }
}
"""

private const val GET_ALBUM_JSON = """
{
  "subsonic-response": {
    "status": "ok",
    "version": "1.16.1",
    "album": {
      "id": "album-1",
      "name": "Album A",
      "artist": "Artist A",
      "coverArt": "cover-1",
      "song": [
        {
          "id": "song-1",
          "title": "Blue",
          "artist": "Artist A",
          "album": "Album A",
          "duration": 215,
          "track": 4,
          "discNumber": 1,
          "size": 12345,
          "bitDepth": 16,
          "samplingRate": 44100,
          "bitRate": 880,
          "channelCount": 2,
          "suffix": "flac",
          "coverArt": "cover-1"
        }
      ]
    }
  }
}
"""

private const val GET_LYRICS_JSON = """
{
  "subsonic-response": {
    "status": "ok",
    "version": "1.16.1",
    "lyrics": {
      "artist": "Artist A",
      "title": "Blue",
      "value": "[00:01.00]blue sky\n[00:02.00]second line"
    }
  }
}
"""

private const val GET_LYRICS_BY_SONG_ID_JSON = """
{
  "subsonic-response": {
    "status": "ok",
    "version": "1.16.1",
    "openSubsonic": true,
    "lyricsList": {
      "structuredLyrics": [
        {
          "displayArtist": "Artist A",
          "displayTitle": "Blue",
          "lang": "und",
          "offset": 0,
          "synced": true,
          "line": [
            {
              "start": 1000,
              "value": "first line"
            },
            {
              "start": 2000,
              "value": "second line"
            }
          ]
        }
      ]
    }
  }
}
"""
