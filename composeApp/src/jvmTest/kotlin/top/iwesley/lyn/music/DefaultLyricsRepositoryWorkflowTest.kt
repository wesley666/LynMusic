package top.iwesley.lyn.music

import androidx.room.Room
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
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.LyricsSourceConfigEntity
import top.iwesley.lyn.music.data.db.WorkflowLyricsSourceConfigEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import top.iwesley.lyn.music.data.repository.DefaultLyricsRepository

class DefaultLyricsRepositoryWorkflowTest {

    @Test
    fun `workflow source participates after direct miss and manual workflow apply caches lyrics`() = runTest {
        val database = createTestDatabase()
        database.lyricsSourceConfigDao().upsert(
            LyricsSourceConfigEntity(
                id = "direct-miss",
                name = "Direct Miss",
                method = "GET",
                urlTemplate = "https://lyrics.example/direct",
                headersTemplate = "",
                queryTemplate = "",
                bodyTemplate = "",
                responseFormat = LyricsResponseFormat.TEXT.name,
                extractor = "text",
                priority = 100,
                enabled = true,
            ),
        )
        database.workflowLyricsSourceConfigDao().upsert(
            WorkflowLyricsSourceConfigEntity(
                id = "workflow-oiapi",
                name = "Workflow OIAPI",
                priority = 80,
                enabled = true,
                rawJson = WORKFLOW_JSON,
            ),
        )
        val httpClient = WorkflowHttpClient(
            mapOf(
                "https://lyrics.example/direct" to Result.success(
                    LyricsHttpResponse(statusCode = 200, body = ""),
                ),
                "https://oiapi.net/api/QQMusicLyric?keyword=Rain&page=1&limit=10&type=json" to Result.success(
                    LyricsHttpResponse(statusCode = 200, body = SEARCH_JSON),
                ),
                "https://oiapi.net/api/QQMusicLyric?id=11&format=lrc&type=json" to Result.success(
                    LyricsHttpResponse(statusCode = 200, body = LYRICS_JSON),
                ),
            ),
        )
        val artworkCacheStore = FakeArtworkCacheStore(
            cached = mapOf("https://img.test/rain.jpg" to "/tmp/lynmusic-artwork-cache/rain.jpg"),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = httpClient,
            secureCredentialStore = EmptySecureCredentialStore,
            artworkCacheStore = artworkCacheStore,
            logger = NoopDiagnosticLogger,
        )
        val track = Track(
            id = "track-1",
            sourceId = "local-1",
            title = "Rain",
            artistName = "Jay",
            albumTitle = "Album 1",
            durationMs = 181_000L,
            mediaLocator = "file:///music/rain.mp3",
            relativePath = "rain.mp3",
        )
        database.trackDao().upsertAll(
            listOf(
                top.iwesley.lyn.music.data.db.TrackEntity(
                    id = track.id,
                    sourceId = track.sourceId,
                    title = track.title,
                    artistId = null,
                    artistName = track.artistName,
                    albumId = null,
                    albumTitle = track.albumTitle,
                    durationMs = track.durationMs,
                    trackNumber = null,
                    discNumber = null,
                    mediaLocator = track.mediaLocator,
                    relativePath = track.relativePath,
                    artworkLocator = null,
                    sizeBytes = 0L,
                    modifiedAt = 0L,
                ),
            ),
        )

        val autoLyrics = repository.getLyrics(track)

        assertNotNull(autoLyrics)
        assertEquals("workflow-oiapi", autoLyrics.document.sourceId)
        assertEquals("/tmp/lynmusic-artwork-cache/rain.jpg", autoLyrics.artworkLocator)

        val workflowCandidates = repository.searchWorkflowSongCandidates(track)

        assertEquals(listOf("11", "12"), workflowCandidates.map { it.id })

        val applied = repository.applyWorkflowSongCandidate(track.id, workflowCandidates.first())
        val cachedRows = database.lyricsCacheDao().getByTrack(track.id)
        val storedTrack = database.trackDao().getByIds(listOf(track.id)).single()

        assertEquals("workflow-oiapi", applied.document.sourceId)
        assertEquals("/tmp/lynmusic-artwork-cache/rain.jpg", applied.artworkLocator)
        assertEquals(listOf("workflow-oiapi"), cachedRows.map { it.sourceId })
        assertEquals("/tmp/lynmusic-artwork-cache/rain.jpg", storedTrack.artworkLocator)
    }

    @Test
    fun `workflow candidate enrichment can populate cover url`() = runTest {
        val database = createTestDatabase()
        database.workflowLyricsSourceConfigDao().upsert(
            WorkflowLyricsSourceConfigEntity(
                id = "workflow-kugou",
                name = "Workflow Kugou",
                priority = 70,
                enabled = true,
                rawJson = KUGOU_ENRICHMENT_WORKFLOW_JSON,
            ),
        )
        val httpClient = WorkflowHttpClient(
            mapOf(
                "http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword=Rain&page=1&pagesize=10&showtype=1" to Result.success(
                    LyricsHttpResponse(statusCode = 200, body = KUGOU_SEARCH_JSON),
                ),
                "https://wwwapi.kugou.com/yy/index.php?r=play/getdata&hash=abc123&album_id=55" to Result.success(
                    LyricsHttpResponse(statusCode = 200, body = KUGOU_COVER_JSON),
                ),
            ),
        )
        val repository = DefaultLyricsRepository(
            database = database,
            httpClient = httpClient,
            secureCredentialStore = EmptySecureCredentialStore,
            logger = NoopDiagnosticLogger,
        )
        val track = Track(
            id = "track-2",
            sourceId = "local-1",
            title = "Rain",
            artistName = "",
            albumTitle = "",
            durationMs = 181_000L,
            mediaLocator = "file:///music/rain.mp3",
            relativePath = "rain.mp3",
        )

        val candidates = repository.searchWorkflowSongCandidates(track)

        assertEquals(1, candidates.size)
        assertEquals("https://img.kugou.test/cover.jpg", candidates.single().imageUrl)
    }

    private fun createTestDatabase(): LynMusicDatabase {
        val path = Files.createTempFile("lynmusic-lyrics-workflow", ".db")
        return buildLynMusicDatabase(
            Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
        )
    }
}

private class FakeArtworkCacheStore(
    private val cached: Map<String, String>,
) : ArtworkCacheStore {
    override suspend fun cache(locator: String, cacheKey: String): String? = cached[locator] ?: locator
}

private class WorkflowHttpClient(
    private val responses: Map<String, Result<LyricsHttpResponse>>,
) : LyricsHttpClient {
    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        return responses[request.url]
            ?: Result.failure(IllegalArgumentException("Unexpected request: ${request.url}"))
    }
}

private const val WORKFLOW_JSON = """
{
  "id": "workflow-oiapi",
  "name": "Workflow OIAPI",
  "kind": "workflow",
  "enabled": true,
  "priority": 80,
  "search": {
    "method": "GET",
    "url": "https://oiapi.net/api/QQMusicLyric",
    "queryTemplate": "keyword={title}&page=1&limit=10&type=json",
    "responseFormat": "json",
    "resultPath": "data",
    "mapping": {
      "id": "id",
      "title": "name",
      "artists": "singer",
      "album": "album",
      "durationSeconds": "duration",
      "coverUrl": "image"
    }
  },
  "selection": {
    "titleWeight": 0.7,
    "artistWeight": 0.2,
    "albumWeight": 0.05,
    "durationWeight": 0.05,
    "durationToleranceSeconds": 3,
    "minScore": 0.4,
    "maxCandidates": 10
  },
  "lyrics": {
    "steps": [
      {
        "method": "GET",
        "url": "https://oiapi.net/api/QQMusicLyric",
        "queryTemplate": "id={candidate.id}&format=lrc&type=json",
        "responseFormat": "json",
        "payloadPath": "data.content",
        "fallbackPayloadPath": "message",
        "format": "lrc",
        "transforms": ["trim"]
      }
    ]
  },
  "optionalFields": {
    "coverUrlField": "coverUrl"
  }
}
"""

private const val KUGOU_ENRICHMENT_WORKFLOW_JSON = """
{
  "id": "workflow-kugou",
  "name": "Workflow Kugou",
  "kind": "workflow",
  "enabled": true,
  "priority": 70,
  "search": {
    "method": "GET",
    "url": "http://mobilecdn.kugou.com/api/v3/search/song",
    "queryTemplate": "format=json&keyword={title}&page=1&pagesize=10&showtype=1",
    "responseFormat": "json",
    "resultPath": "data.info",
    "mapping": {
      "id": "hash",
      "title": "songname",
      "artists": "singername",
      "album": "album_name",
      "durationSeconds": "duration",
      "albumId": "album_id"
    }
  },
  "enrichment": {
    "steps": [
      {
        "method": "GET",
        "url": "https://wwwapi.kugou.com/yy/index.php",
        "queryTemplate": "r=play/getdata&hash={candidate.id}&album_id={candidate.albumId}",
        "responseFormat": "json",
        "capture": {
          "imageUrl": "data.img"
        }
      }
    ]
  },
  "lyrics": {
    "steps": [
      {
        "method": "GET",
        "url": "https://lyrics.kugou.com/download",
        "queryTemplate": "id={candidate.id}",
        "responseFormat": "json",
        "payloadPath": "content",
        "format": "lrc",
        "transforms": ["base64Decode", "trim"]
      }
    ]
  }
}
"""

private const val SEARCH_JSON = """
{
  "data": [
    {
      "id": 11,
      "name": "Rain",
      "singer": ["Jay"],
      "album": "Album 1",
      "duration": 181,
      "image": "https://img.test/rain.jpg"
    },
    {
      "id": 12,
      "name": "Rain",
      "singer": ["Other"],
      "album": "Album 1",
      "duration": 181,
      "image": "https://img.test/other.jpg"
    }
  ]
}
"""

private const val KUGOU_SEARCH_JSON = """
{
  "data": {
    "info": [
      {
        "hash": "abc123",
        "songname": "Rain",
        "singername": "Jay",
        "album_name": "Album 1",
        "duration": 181,
        "album_id": 55
      }
    ]
  }
}
"""

private const val KUGOU_COVER_JSON = """
{
  "data": {
    "img": "https://img.kugou.test/cover.jpg"
  }
}
"""

private const val LYRICS_JSON = """
{
  "data": {
    "content": "[00:01.00]Hello workflow"
  }
}
"""
