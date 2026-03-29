package top.iwesley.lyn.music

import androidx.room.Room
import io.ktor.http.parseUrl
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.LyricsSourceConfigEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import top.iwesley.lyn.music.data.repository.DefaultLyricsRepository
import top.iwesley.lyn.music.domain.NAVIDROME_LYRICS_SOURCE_ID

class DefaultLyricsRepositoryNavidromeTest {

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
                responseFormat = LyricsResponseFormat.TEXT.name,
                extractor = "text",
                priority = 100,
                enabled = true,
            ),
        )
        val httpClient = NavidromeRepositoryHttpClient(
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

        val result = repository.getLyrics(sampleNavidromeTrack())

        assertNotNull(result)
        assertEquals(NAVIDROME_LYRICS_SOURCE_ID, result.document.sourceId)
        assertEquals(1, httpClient.navidromeLyricsRequests)
        assertEquals(0, httpClient.directRequests)
        assertEquals(listOf(NAVIDROME_LYRICS_SOURCE_ID), database.lyricsCacheDao().getByTrack("nav-track").map { it.sourceId })
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
                responseFormat = LyricsResponseFormat.TEXT.name,
                extractor = "text",
                priority = 100,
                enabled = true,
            ),
        )
        val httpClient = NavidromeRepositoryHttpClient(
            navidromeLyricsBody = """
                {
                  "subsonic-response": {
                    "status": "ok",
                    "version": "1.16.1"
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
        assertEquals("direct-fallback", result.document.sourceId)
        assertEquals(1, httpClient.navidromeLyricsRequests)
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
        )
    }
}

private class NavidromeRepositoryHttpClient(
    private val navidromeLyricsBody: String,
    private val directFallbackBody: String,
) : LyricsHttpClient {
    var navidromeLyricsRequests: Int = 0
        private set
    var directRequests: Int = 0
        private set

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        val url = requireNotNull(parseUrl(request.url))
        return when {
            url.encodedPath.endsWith("/rest/getLyrics") -> {
                navidromeLyricsRequests += 1
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
