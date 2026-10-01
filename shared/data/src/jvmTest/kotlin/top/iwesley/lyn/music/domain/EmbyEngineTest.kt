package top.iwesley.lyn.music.domain

import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiErrorText

import java.net.URI
import java.net.URLDecoder
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.EmbyCredential
import top.iwesley.lyn.music.core.model.EmbySourceDraft
import top.iwesley.lyn.music.core.model.IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS
import top.iwesley.lyn.music.core.model.ImportScanPhase
import top.iwesley.lyn.music.core.model.ImportScanProgress
import top.iwesley.lyn.music.core.model.ImportScanProgressSink
import top.iwesley.lyn.music.core.model.LyricsLine
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.buildEmbyCoverLocator
import top.iwesley.lyn.music.core.model.buildEmbySongLocator

class EmbyEngineTest {
    @Test
    fun `normalizes Emby base URL`() {
        assertEquals(
            "https://emby.example.com/base",
            normalizeEmbyBaseUrl(" https://emby.example.com:443/base/ "),
        )
        assertFailsWith<IllegalArgumentException> {
            normalizeEmbyBaseUrl("https://emby.example.com/music?api_key=token")
        }
    }

    @Test
    fun `authenticate posts Emby credentials and returns user token`() = runTest {
        val httpClient = RecordingEmbyHttpClient { request ->
            assertEquals(RequestMethod.POST, request.method)
            assertEquals("https://emby.example.com/base/Users/AuthenticateByName", request.url)
            assertEquals("application/json", request.headers["Content-Type"])
            assertTrue(request.headers["X-Emby-Authorization"].orEmpty().contains("Client=\"LynMusic\""))
            assertTrue(request.headers["X-Emby-Authorization"].orEmpty().contains("DeviceId=\"device-1\""))
            assertEquals("""{"Username":"demo","Pw":"secret"}""", request.body)
            assertEquals(IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS, request.timeoutMillis)
            LyricsHttpResponse(
                statusCode = 200,
                body = """{"AccessToken":"access-token","User":{"Id":"user-1"}}""",
            )
        }

        val credential = authenticateEmby(
            draft = EmbySourceDraft(
                label = "",
                baseUrl = "https://emby.example.com/base/",
                username = "demo",
                password = "secret",
            ),
            deviceId = "device-1",
            httpClient = httpClient,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )

        assertEquals(EmbyCredential(userId = "user-1", accessToken = "access-token"), credential)
        assertEquals(1, httpClient.requests.size)
    }

    @Test
    fun `test connection validates retained Emby credential against server`() = runTest {
        val httpClient = RecordingEmbyHttpClient { request ->
            assertEquals(RequestMethod.GET, request.method)
            assertEquals("https://emby.example.com/base/Users/user-1/Items", URI(request.url).let {
                "${it.scheme}://${it.host}${it.path}"
            })
            assertEquals("1", request.queryParam("Limit"))
            assertEquals("access-token", request.headers["X-Emby-Token"])
            assertTrue(request.headers["X-Emby-Authorization"].orEmpty().contains("DeviceId=\"device-1\""))
            assertEquals(IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS, request.timeoutMillis)
            LyricsHttpResponse(
                statusCode = 200,
                body = """{"Items":[],"TotalRecordCount":0}""",
            )
        }

        testEmbyConnection(
            draft = EmbySourceDraft(
                label = "",
                baseUrl = "https://emby.example.com/base/",
                username = "demo",
                password = "",
            ),
            credential = EmbyCredential(userId = "user-1", accessToken = "access-token"),
            deviceId = "device-1",
            httpClient = httpClient,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )

        assertEquals(1, httpClient.requests.size)
    }

    @Test
    fun `scan maps supported Emby audio items and reports unsupported audio`() = runTest {
        val httpClient = RecordingEmbyHttpClient { request ->
            assertEquals(RequestMethod.GET, request.method)
            assertTrue(request.url.startsWith("https://emby.example.com/Users/user-1/Items?"))
            assertEquals("true", request.queryParam("Recursive"))
            assertEquals("Audio", request.queryParam("MediaTypes"))
            assertEquals("Audio", request.queryParam("IncludeItemTypes"))
            assertEquals("MediaSources,Path", request.queryParam("Fields"))
            assertEquals("false", request.queryParam("EnableImages"))
            assertEquals(null, request.queryParam("EnableImageTypes"))
            assertEquals("0", request.queryParam("StartIndex"))
            assertEquals("100", request.queryParam("Limit"))
            assertEquals("access-token", request.headers["X-Emby-Token"])
            assertTrue(request.headers["X-Emby-Authorization"].orEmpty().contains("DeviceId=\"device-1\""))
            assertEquals(IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS, request.timeoutMillis)
            LyricsHttpResponse(
                statusCode = 200,
                body = """
                    {
                      "Items": [
                        {
                          "Id": "song-1",
                          "Name": "Track One",
                          "Album": "Album One",
                          "Artists": ["Artist One"],
                          "RunTimeTicks": 1850000000,
                          "IndexNumber": 4,
                          "ParentIndexNumber": 1,
                          "Size": 1234567,
                          "Container": "flac",
                          "Bitrate": 900000,
                          "AlbumId": "album-1",
                          "AlbumPrimaryImageTag": "album-tag",
                          "MediaSources": [
                            {
                              "Size": 1234567,
                              "Container": "flac",
                              "MediaStreams": [
                                {
                                  "Type": "Audio",
                                  "Channels": 2,
                                  "SampleRate": 44100,
                                  "BitDepth": 16
                                }
                              ]
                            }
                          ]
                        },
                        {
                          "Id": "song-2",
                          "Name": "Unsupported",
                          "Container": "wma"
                        }
                      ],
                      "TotalRecordCount": 2
                    }
                """.trimIndent(),
            )
        }

        val progressEvents = mutableListOf<ImportScanProgress>()
        val report = scanEmbyLibrary(
            draft = EmbySourceDraft(
                label = "Emby",
                baseUrl = "https://emby.example.com",
                username = "demo",
                password = "",
            ),
            credential = EmbyCredential(userId = "user-1", accessToken = "access-token"),
            deviceId = "device-1",
            sourceId = "emby-1",
            httpClient = httpClient,
            supportedImportExtensions = setOf("flac", "mp3"),
            progressSink = ImportScanProgressSink { progressEvents += it },
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )

        assertEquals(2, report.discoveredAudioFileCount)
        assertEquals(2, report.totalTrackCount)
        assertEquals(1, report.tracks.size)
        assertEquals(1, report.failures.size)
        assertEquals("Artist One/Album One/Track One.flac", report.tracks.single().relativePath)
        val track = report.tracks.single()
        assertEquals("Track One", track.title)
        assertEquals("Artist One", track.artistName)
        assertEquals("Album One", track.albumTitle)
        assertEquals(185000L, track.durationMs)
        assertEquals(4, track.trackNumber)
        assertEquals(1, track.discNumber)
        assertEquals(1234567L, track.sizeBytes)
        assertEquals(900000, track.bitRate)
        assertEquals(2, track.channelCount)
        assertEquals(44100, track.samplingRate)
        assertEquals(16, track.bitDepth)
        assertEquals(buildEmbySongLocator("emby-1", "song-1"), track.mediaLocator)
        assertEquals(buildEmbyCoverLocator("emby-1", "album-1"), track.artworkLocator)
        assertEquals(
            listOf(
                ImportScanProgress(
                    sourceId = "emby-1",
                    phase = ImportScanPhase.Scanning,
                    importedTrackCount = 0,
                    totalTrackCount = 2,
                ),
                ImportScanProgress(
                    sourceId = "emby-1",
                    phase = ImportScanPhase.Scanning,
                    importedTrackCount = 1,
                    totalTrackCount = 2,
                ),
            ),
            progressEvents,
        )
    }

    @Test
    fun `scan retries timed out Emby items page with smaller page sizes`() = runTest {
        val httpClient = RecordingEmbyHttpClient { request ->
            val startIndex = request.queryParam("StartIndex")?.toIntOrNull() ?: error("Missing StartIndex")
            val limit = request.queryParam("Limit")?.toIntOrNull() ?: error("Missing Limit")
            if (startIndex == 100 && limit == 100) {
                error("Socket timeout has expired")
            }
            when (startIndex) {
                0 -> {
                    assertEquals(100, limit)
                    embyItemsResponse(startIndex = 0, count = 100, totalRecordCount = 126)
                }

                100 -> {
                    assertEquals(50, limit)
                    embyItemsResponse(startIndex = 100, count = 25, totalRecordCount = 126)
                }

                125 -> {
                    assertEquals(50, limit)
                    embyItemsResponse(startIndex = 125, count = 1, totalRecordCount = 126)
                }

                else -> error("Unexpected StartIndex $startIndex")
            }
        }

        val report = scanEmbyLibrary(
            draft = EmbySourceDraft(
                label = "Emby",
                baseUrl = "https://emby.example.com",
                username = "demo",
                password = "",
            ),
            credential = EmbyCredential(userId = "user-1", accessToken = "access-token"),
            deviceId = "device-1",
            sourceId = "emby-1",
            httpClient = httpClient,
            supportedImportExtensions = setOf("flac"),
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )

        assertEquals(126, report.discoveredAudioFileCount)
        assertEquals(126, report.totalTrackCount)
        assertEquals(126, report.tracks.size)
        assertEquals(listOf("0", "100", "100", "125"), httpClient.requests.map { it.queryParam("StartIndex") })
        assertEquals(listOf("100", "100", "50", "50"), httpClient.requests.map { it.queryParam("Limit") })
    }

    @Test
    fun `scan does not retry non timeout Emby items failures`() = runTest {
        val httpClient = RecordingEmbyHttpClient {
            error("Emby server unavailable")
        }

        val failure = assertFailsWith<IllegalStateException> {
            scanEmbyLibrary(
                draft = EmbySourceDraft(
                    label = "Emby",
                    baseUrl = "https://emby.example.com",
                    username = "demo",
                    password = "",
                ),
                credential = EmbyCredential(userId = "user-1", accessToken = "access-token"),
                deviceId = "device-1",
                sourceId = "emby-1",
                httpClient = httpClient,
                supportedImportExtensions = setOf("flac"),
            )
        }

        assertEquals("Emby Items 请求失败：Emby server unavailable", resolveUiText(failure.uiErrorText(), AppLanguage.SimplifiedChinese))
        assertEquals(1, httpClient.requests.size)
        assertEquals("100", httpClient.requests.single().queryParam("Limit"))
    }

    @Test
    fun `scan reports Emby total even when no item can be imported`() = runTest {
        val httpClient = RecordingEmbyHttpClient {
            LyricsHttpResponse(
                statusCode = 200,
                body = """
                    {
                      "Items": [
                        {
                          "Id": "song-1",
                          "Name": "Unsupported",
                          "Container": "wma"
                        }
                      ],
                      "TotalRecordCount": 1
                    }
                """.trimIndent(),
            )
        }

        val progressEvents = mutableListOf<ImportScanProgress>()
        val report = scanEmbyLibrary(
            draft = EmbySourceDraft(
                label = "Emby",
                baseUrl = "https://emby.example.com",
                username = "demo",
                password = "",
            ),
            credential = EmbyCredential(userId = "user-1", accessToken = "access-token"),
            deviceId = "device-1",
            sourceId = "emby-1",
            httpClient = httpClient,
            supportedImportExtensions = setOf("flac"),
            progressSink = ImportScanProgressSink { progressEvents += it },
        )

        assertEquals(1, report.discoveredAudioFileCount)
        assertEquals(1, report.totalTrackCount)
        assertTrue(report.tracks.isEmpty())
        assertEquals(1, report.failures.size)
        assertEquals(
            listOf(
                ImportScanProgress(
                    sourceId = "emby-1",
                    phase = ImportScanPhase.Scanning,
                    importedTrackCount = 0,
                    totalTrackCount = 1,
                ),
            ),
            progressEvents,
        )
    }

    @Test
    fun `builds Emby playback and cover URLs`() {
        assertEquals(
            "https://emby.example.com/base/Audio/song-1/stream?Static=true&api_key=token",
            buildEmbyStreamUrl("https://emby.example.com/base", "song-1", "token"),
        )
        assertEquals(
            "https://emby.example.com/base/Items/song-1/Download?api_key=token",
            buildEmbyDownloadUrl("https://emby.example.com/base", "song-1", "token"),
        )
        assertEquals(
            "https://emby.example.com/base/Items/cover-1/Images/Primary?api_key=token",
            buildEmbyCoverArtUrl("https://emby.example.com/base", "cover-1", "token"),
        )
        assertEquals(
            EmbyCredential(userId = "user-1", accessToken = "token"),
            parseEmbyCredential(serializeEmbyCredential(EmbyCredential(userId = "user-1", accessToken = "token"))),
        )
    }

    @Test
    fun `remote Emby operations use authenticated API endpoints`() = runTest {
        val source = EmbyResolvedSource(
            sourceId = "emby-1",
            baseUrl = "https://emby.example.com/base",
            credential = EmbyCredential(userId = "user-1", accessToken = "token"),
            deviceId = "device-1",
        )
        val httpClient = RecordingEmbyHttpClient { request ->
            assertEquals("token", request.headers["X-Emby-Token"])
            val uri = URI(request.url)
            when (uri.path) {
                "/base/Users/user-1/FavoriteItems/song-1" -> {
                    assertEquals(RequestMethod.POST, request.method)
                    LyricsHttpResponse(200, "")
                }

                "/base/Users/user-1/FavoriteItems/song-1/Delete" -> {
                    assertEquals(RequestMethod.POST, request.method)
                    LyricsHttpResponse(200, "")
                }

                "/base/Users/user-1/Items" -> {
                    assertEquals(RequestMethod.GET, request.method)
                    when {
                        request.queryParam("Filters") == "IsFavorite" -> LyricsHttpResponse(
                            200,
                            """{"Items":[{"Id":"song-1"}],"TotalRecordCount":1}""",
                        )

                        request.queryParam("IncludeItemTypes") == "Playlist" -> LyricsHttpResponse(
                            200,
                            """{"Items":[{"Id":"pl-1","Name":"Road Trip"}],"TotalRecordCount":1}""",
                        )

                        else -> LyricsHttpResponse(
                            200,
                            """
                                {
                                  "Items": [
                                    {
                                      "Id": "song-1",
                                      "AlbumId": "album-1",
                                      "UserData": {
                                        "LastPlayedDate": "2026-04-06T09:08:31.500Z",
                                        "PlayCount": 3
                                      }
                                    }
                                  ],
                                  "TotalRecordCount": 1
                                }
                            """.trimIndent(),
                        )
                    }
                }

                "/base/Playlists" -> {
                    assertEquals(RequestMethod.POST, request.method)
                    assertEquals("Road Trip", request.queryParam("Name"))
                    assertEquals("Audio", request.queryParam("MediaType"))
                    LyricsHttpResponse(200, """{"Id":"pl-1","Name":"Road Trip"}""")
                }

                "/base/Playlists/pl-1/Items" -> when (request.method) {
                    RequestMethod.GET -> LyricsHttpResponse(
                        200,
                        """{"Items":[{"Id":"song-1","PlaylistItemId":"entry-1"}],"TotalRecordCount":1}""",
                    )

                    RequestMethod.POST -> {
                        assertEquals("song-1", request.queryParam("Ids"))
                        LyricsHttpResponse(200, "")
                    }

                    else -> error("Unexpected method ${request.method}")
                }

                "/base/Playlists/pl-1/Items/Delete" -> {
                    assertEquals(RequestMethod.POST, request.method)
                    assertEquals("entry-1", request.queryParam("EntryIds"))
                    LyricsHttpResponse(200, "")
                }

                "/base/Users/user-1/Items/pl-1" -> {
                    assertEquals(RequestMethod.GET, request.method)
                    LyricsHttpResponse(200, """{"Id":"pl-1","Name":"Road Trip","Type":"Playlist"}""")
                }

                "/base/Items/pl-1" -> when (request.method) {
                    RequestMethod.POST -> {
                        assertEquals("pl-1", request.queryParam("ItemId"))
                        assertTrue(request.body.orEmpty().contains("\"Name\":\"Renamed Trip\""))
                        LyricsHttpResponse(204, "")
                    }

                    RequestMethod.DELETE -> LyricsHttpResponse(204, "")
                    else -> error("Unexpected method ${request.method}")
                }

                "/base/Users/user-1/PlayingItems/song-1",
                "/base/Users/user-1/PlayedItems/song-1",
                -> {
                    assertEquals(RequestMethod.POST, request.method)
                    LyricsHttpResponse(200, "")
                }

                else -> error("Unexpected request ${request.method} ${request.url}")
            }
        }

        setEmbyFavorite(httpClient, source, itemId = "song-1", favorite = true)
        setEmbyFavorite(httpClient, source, itemId = "song-1", favorite = false)
        assertEquals(listOf(EmbyFavoriteItem("song-1", favoritedAt = null)), fetchEmbyFavorites(httpClient, source))
        assertEquals(listOf(EmbyPlaylistSummaryPayload("pl-1", "Road Trip")), fetchEmbyPlaylists(httpClient, source))
        assertEquals(EmbyPlaylistSummaryPayload("pl-1", "Road Trip"), createEmbyPlaylist(httpClient, source, "Road Trip"))
        addEmbyPlaylistItem(httpClient, source, "pl-1", "song-1")
        assertEquals(listOf(EmbyPlaylistEntryPayload("song-1", "entry-1")), fetchEmbyPlaylistEntries(httpClient, source, "pl-1"))
        removeEmbyPlaylistEntries(httpClient, source, "pl-1", listOf("entry-1"))
        updateEmbyPlaylistName(httpClient, source, "pl-1", "Renamed Trip")
        deleteEmbyPlaylist(httpClient, source, "pl-1")
        reportEmbyNowPlaying(httpClient, source, "song-1")
        submitEmbyPlay(httpClient, source, "song-1")
        assertEquals(
            listOf(
                EmbyRecentTrackPayload(
                    itemId = "song-1",
                    albumId = "album-1",
                    playedAt = Instant.parse("2026-04-06T09:08:31.500Z").toEpochMilliseconds(),
                    playCount = 3,
                ),
            ),
            fetchEmbyRecentTracks(httpClient, source, limit = 10),
        )
    }

    @Test
    fun `parses Emby lyrics payloads`() {
        val document = parseEmbyLyricsPayload(
            """
                {
                  "Lyrics": [
                    {"Start": 10000000, "Text": "hello"},
                    {"Start": 20500000, "Text": "world"}
                  ]
                }
            """.trimIndent(),
        )

        assertEquals(EMBY_LYRICS_SOURCE_ID, document?.sourceId)
        assertEquals(
            listOf(
                LyricsLine(timestampMs = 1_000L, text = "hello"),
                LyricsLine(timestampMs = 2_050L, text = "world"),
            ),
            document?.lines,
        )
    }
}

private class RecordingEmbyHttpClient(
    private val responder: (LyricsRequest) -> LyricsHttpResponse,
) : LyricsHttpClient {
    val requests = mutableListOf<LyricsRequest>()

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        requests += request
        return runCatching { responder(request) }
    }
}

private fun embyItemsResponse(
    startIndex: Int,
    count: Int,
    totalRecordCount: Int,
): LyricsHttpResponse {
    val items = (startIndex until startIndex + count).joinToString(",") { index ->
        """
            {
              "Id": "song-$index",
              "Name": "Track $index",
              "Album": "Album",
              "Artists": ["Artist"],
              "RunTimeTicks": 10000000,
              "Container": "flac",
              "AlbumId": "album-1"
            }
        """.trimIndent()
    }
    return LyricsHttpResponse(
        statusCode = 200,
        body = """{"Items":[$items],"TotalRecordCount":$totalRecordCount}""",
    )
}

private fun LyricsRequest.queryParam(name: String): String? {
    val query = URI(url).rawQuery ?: return null
    return query.split("&")
        .map { part -> part.substringBefore("=") to part.substringAfter("=", "") }
        .firstOrNull { (key, _) -> key == name }
        ?.second
        ?.let { value -> URLDecoder.decode(value, "UTF-8") }
}
