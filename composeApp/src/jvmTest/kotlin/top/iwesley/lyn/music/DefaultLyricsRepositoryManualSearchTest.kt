package top.iwesley.lyn.music

import androidx.room.Room
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.LyricsSourceConfigEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import top.iwesley.lyn.music.data.repository.DefaultLyricsRepository

class DefaultLyricsRepositoryManualSearchTest {

    @Test
    fun `manual search returns one candidate per successful source and applying it caches for future lookup`() = runTest {
        val database = createTestDatabase()
        database.lyricsSourceConfigDao().upsert(
            lyricsSourceConfig(
                id = "source-synced",
                name = "同步源",
                priority = 20,
                urlTemplate = "https://lyrics.example/synced",
                responseFormat = LyricsResponseFormat.LRC,
                extractor = "text",
            ),
        )
        database.lyricsSourceConfigDao().upsert(
            lyricsSourceConfig(
                id = "source-plain",
                name = "纯文本源",
                priority = 10,
                urlTemplate = "https://lyrics.example/plain",
                responseFormat = LyricsResponseFormat.TEXT,
                extractor = "text",
            ),
        )
        database.lyricsSourceConfigDao().upsert(
            lyricsSourceConfig(
                id = "source-failed",
                name = "失败源",
                priority = 5,
                urlTemplate = "https://lyrics.example/failed",
                responseFormat = LyricsResponseFormat.TEXT,
                extractor = "text",
            ),
        )
        val httpClient = FakeLyricsHttpClient(
            mapOf(
                "https://lyrics.example/synced" to Result.success(
                    LyricsHttpResponse(
                        statusCode = 200,
                        body = "[00:01.00]第一句\n[00:02.00]第二句",
                    ),
                ),
                "https://lyrics.example/plain" to Result.success(
                    LyricsHttpResponse(
                        statusCode = 200,
                        body = "纯文本第一句\n纯文本第二句",
                    ),
                ),
                "https://lyrics.example/failed" to Result.failure(IllegalStateException("boom")),
            ),
        )
        val repository = DefaultLyricsRepository(database, httpClient, logger = NoopDiagnosticLogger)
        val track = sampleTrack()

        val candidates = repository.searchLyricsCandidates(track)

        assertEquals(listOf("source-synced", "source-plain"), candidates.map { it.sourceId })
        assertTrue(candidates.first().document.isSynced)
        assertFalse(candidates.last().document.isSynced)
        assertTrue(database.lyricsCacheDao().getByTrack(track.id).isEmpty())

        val applied = repository.applyLyricsCandidate(track.id, candidates.last())
        val cachedRows = database.lyricsCacheDao().getByTrack(track.id)

        assertEquals("source-plain", applied.sourceId)
        assertEquals(listOf("source-plain"), cachedRows.map { it.sourceId })

        val requestCountAfterManualSearch = httpClient.requestCount
        val cached = repository.getLyrics(track.copy(title = "不会再次发请求"))

        assertNotNull(cached)
        assertEquals("source-plain", cached.sourceId)
        assertEquals(requestCountAfterManualSearch, httpClient.requestCount)
    }

    private fun createTestDatabase(): LynMusicDatabase {
        val path = Files.createTempFile("lynmusic-lyrics-manual", ".db")
        return buildLynMusicDatabase(
            Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
        )
    }

    private fun lyricsSourceConfig(
        id: String,
        name: String,
        priority: Int,
        urlTemplate: String,
        responseFormat: LyricsResponseFormat,
        extractor: String,
    ): LyricsSourceConfigEntity {
        return LyricsSourceConfigEntity(
            id = id,
            name = name,
            priority = priority,
            urlTemplate = urlTemplate,
            method = "GET",
            headersTemplate = "",
            queryTemplate = "",
            bodyTemplate = "",
            responseFormat = responseFormat.name,
            extractor = extractor,
            enabled = true,
        )
    }

    private fun sampleTrack(): Track {
        return Track(
            id = "track-1",
            sourceId = "local-1",
            title = "原始标题",
            artistName = "原始歌手",
            albumTitle = "原始专辑",
            durationMs = 123_000L,
            mediaLocator = "file:///music/song.mp3",
            relativePath = "song.mp3",
        )
    }
}

private class FakeLyricsHttpClient(
    private val responses: Map<String, Result<LyricsHttpResponse>>,
) : LyricsHttpClient {
    var requestCount: Int = 0
        private set

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        requestCount += 1
        return responses[request.url] ?: Result.failure(IllegalArgumentException("Unexpected request: ${request.url}"))
    }
}
