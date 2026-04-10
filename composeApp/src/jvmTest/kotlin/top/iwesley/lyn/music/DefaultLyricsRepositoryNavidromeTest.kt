package top.iwesley.lyn.music

import androidx.room.Room
import io.ktor.http.parseUrl
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildNavidromeCoverLocator
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.LyricsSourceConfigEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import top.iwesley.lyn.music.data.repository.DefaultLyricsRepository
import top.iwesley.lyn.music.domain.NAVIDROME_LYRICS_SOURCE_ID

class DefaultLyricsRepositoryNavidromeTest {

    @Test
    fun `navidrome manual search prepends current track candidate from navidrome lyrics api`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        val httpClient = NavidromeRepositoryHttpClient(
            navidromeStructuredLyricsBody = """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "openSubsonic": true,
                    "lyricsList": {
                      "structuredLyrics": [
                        {
                          "synced": true,
                          "line": [
                            {
                              "start": 1000,
                              "value": "navidrome line"
                            }
                          ]
                        }
                      ]
                    }
                  }
                }
            """.trimIndent(),
            navidromeLyricsBody = """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "lyrics": {
                      "value": "[00:01.00]navidrome line"
                    }
                  }
                }
            """.trimIndent(),
            directFallbackBody = "direct line",
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = httpClient,
            secureCredentialStore = MapSecureCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            logger = NoopDiagnosticLogger,
        )

        val candidates = repository.searchLyricsCandidates(sampleNavidromeTrack())

        assertEquals(1, candidates.size)
        assertEquals(NAVIDROME_LYRICS_SOURCE_ID, candidates.single().sourceId)
        assertEquals("Navidrome", candidates.single().sourceName)
        assertTrue(candidates.single().isTrackProvided)
        assertTrue(candidates.single().document.isSynced)
        assertEquals(buildNavidromeCoverLocator("nav-source", "cover-1"), candidates.single().artworkLocator)
        assertEquals(1, httpClient.navidromeStructuredLyricsRequests)
        assertEquals(0, httpClient.navidromeLegacyLyricsRequests)
    }

    @Test
    fun `navidrome track prefers navidrome lyrics over direct sources`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        database.lyricsSourceConfigDao().upsert(
            LyricsSourceConfigEntity(
                id = "direct-fallback",
                name = "Direct Fallback",
                method = "GET",
                urlTemplate = "https://lyrics.example/fallback",
                headersTemplate = "",
                queryTemplate = "",
                bodyTemplate = "",
                responseFormat = LyricsResponseFormat.JSON.name,
                extractor = "json-map:lyrics=lyrics,title=title,artist=artist,album=album,durationSeconds=duration,id=id",
                priority = 100,
                enabled = true,
            ),
        )
        val httpClient = NavidromeRepositoryHttpClient(
            navidromeStructuredLyricsBody = """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "openSubsonic": true,
                    "lyricsList": {
                      "structuredLyrics": [
                        {
                          "synced": true,
                          "line": [
                            {
                              "start": 1000,
                              "value": "navidrome line"
                            }
                          ]
                        }
                      ]
                    }
                  }
                }
            """.trimIndent(),
            navidromeLyricsBody = """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "lyrics": {
                      "value": "[00:01.00]navidrome line"
                    }
                  }
                }
            """.trimIndent(),
            directFallbackBody = """
                {
                  "id": "fallback-1",
                  "title": "Blue",
                  "artist": "Artist A",
                  "album": "Album A",
                  "duration": 215,
                  "lyrics": "direct line"
                }
            """.trimIndent(),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = httpClient,
            secureCredentialStore = MapSecureCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            logger = NoopDiagnosticLogger,
        )

        val result = repository.getLyrics(sampleNavidromeTrack())

        assertNotNull(result)
        assertEquals(NAVIDROME_LYRICS_SOURCE_ID, result.document.sourceId)
        assertTrue(result.document.isSynced)
        assertEquals(1, httpClient.navidromeStructuredLyricsRequests)
        assertEquals(0, httpClient.navidromeLegacyLyricsRequests)
        assertEquals(0, httpClient.directRequests)
        assertEquals(listOf(NAVIDROME_LYRICS_SOURCE_ID), database.lyricsCacheDao().getByTrack("nav-track").map { it.sourceId })
    }

    @Test
    fun `navidrome track falls back to legacy lyrics when structured endpoint is unsupported`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        val httpClient = NavidromeRepositoryHttpClient(
            navidromeStructuredLyricsFailure = IllegalStateException("Navidrome getLyricsBySongId 失败，HTTP 404"),
            navidromeLyricsBody = """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "lyrics": {
                      "value": "[00:01.00]legacy line"
                    }
                  }
                }
            """.trimIndent(),
            directFallbackBody = "direct line",
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = httpClient,
            secureCredentialStore = MapSecureCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            logger = NoopDiagnosticLogger,
        )

        val result = repository.getLyrics(sampleNavidromeTrack())

        assertNotNull(result)
        assertEquals(NAVIDROME_LYRICS_SOURCE_ID, result.document.sourceId)
        assertTrue(result.document.isSynced)
        assertEquals(1, httpClient.navidromeStructuredLyricsRequests)
        assertEquals(1, httpClient.navidromeLegacyLyricsRequests)
        assertEquals(0, httpClient.directRequests)
    }

    @Test
    fun `navidrome track falls back to direct lyrics when navidrome has none`() = runTest {
        val database = createTestDatabase()
        seedNavidromeSource(database)
        database.lyricsSourceConfigDao().upsert(
            LyricsSourceConfigEntity(
                id = "direct-fallback",
                name = "Direct Fallback",
                method = "GET",
                urlTemplate = "https://lyrics.example/fallback",
                headersTemplate = "",
                queryTemplate = "",
                bodyTemplate = "",
                responseFormat = LyricsResponseFormat.JSON.name,
                extractor = "json-map:lyrics=lyrics,title=title,artist=artist,album=album,durationSeconds=duration,id=id",
                priority = 100,
                enabled = true,
            ),
        )
        val httpClient = NavidromeRepositoryHttpClient(
            navidromeStructuredLyricsBody = """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1",
                    "openSubsonic": true,
                    "lyricsList": {
                      "structuredLyrics": []
                    }
                  }
                }
            """.trimIndent(),
            navidromeLyricsBody = """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1"
                  }
                }
            """.trimIndent(),
            directFallbackBody = """
                {
                  "id": "fallback-1",
                  "title": "Blue",
                  "artist": "Artist A",
                  "album": "Album A",
                  "duration": 215,
                  "lyrics": "direct line"
                }
            """.trimIndent(),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = httpClient,
            secureCredentialStore = MapSecureCredentialStore(mutableMapOf("nav-cred" to "plain-pass")),
            logger = NoopDiagnosticLogger,
        )

        val result = repository.getLyrics(sampleNavidromeTrack())

        assertNotNull(result)
        assertEquals("direct-fallback", result.document.sourceId)
        assertEquals(1, httpClient.navidromeStructuredLyricsRequests)
        assertEquals(1, httpClient.navidromeLegacyLyricsRequests)
        assertEquals(1, httpClient.directRequests)
    }

    private fun createTestDatabase(): LynMusicDatabase {
        val path = Files.createTempFile("lynmusic-navidrome", ".db")
        return buildLynMusicDatabase(
            Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
        )
    }

    private suspend fun seedNavidromeSource(database: LynMusicDatabase) {
        database.importSourceDao().upsert(
            ImportSourceEntity(
                id = "nav-source",
                type = "NAVIDROME",
                label = "Navidrome",
                rootReference = "https://demo.example.com/navidrome",
                server = null,
                shareName = null,
                directoryPath = null,
                username = "demo",
                credentialKey = "nav-cred",
                allowInsecureTls = false,
                lastScannedAt = null,
                createdAt = 1L,
            ),
        )
    }

    private fun sampleNavidromeTrack(): Track {
        return Track(
            id = "nav-track",
            sourceId = "nav-source",
            title = "Blue",
            artistName = "Artist A",
            albumTitle = "Album A",
            durationMs = 215_000L,
            mediaLocator = buildNavidromeSongLocator("nav-source", "song-1"),
            relativePath = "Artist A/Album A/Blue.flac",
            artworkLocator = buildNavidromeCoverLocator("nav-source", "cover-1"),
        )
    }
}

private class NavidromeRepositoryHttpClient(
    private val navidromeStructuredLyricsBody: String? = null,
    private val navidromeLyricsBody: String,
    private val directFallbackBody: String,
    private val navidromeStructuredLyricsFailure: Throwable? = null,
) : LyricsHttpClient {
    var navidromeStructuredLyricsRequests: Int = 0
        private set
    var navidromeLegacyLyricsRequests: Int = 0
        private set
    var directRequests: Int = 0
        private set

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        val url = requireNotNull(parseUrl(request.url))
        return when {
            url.encodedPath.endsWith("/rest/getLyricsBySongId") -> {
                navidromeStructuredLyricsRequests += 1
                navidromeStructuredLyricsFailure?.let { return Result.failure(it) }
                Result.success(LyricsHttpResponse(statusCode = 200, body = navidromeStructuredLyricsBody.orEmpty()))
            }

            url.encodedPath.endsWith("/rest/getLyrics") -> {
                navidromeLegacyLyricsRequests += 1
                Result.success(LyricsHttpResponse(statusCode = 200, body = navidromeLyricsBody))
            }

            request.url == "https://lyrics.example/fallback" -> {
                directRequests += 1
                Result.success(LyricsHttpResponse(statusCode = 200, body = directFallbackBody))
            }

            else -> Result.failure(IllegalArgumentException("Unexpected request: ${request.url}"))
        }
    }
}
