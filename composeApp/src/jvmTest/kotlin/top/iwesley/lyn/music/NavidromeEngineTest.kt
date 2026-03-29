package top.iwesley.lyn.music

import io.ktor.http.parseUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.domain.NAVIDROME_LYRICS_SOURCE_ID
import top.iwesley.lyn.music.domain.NavidromeResolvedSource
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
    fun `scan library maps navidrome artists albums and songs`() = runTest {
        val client = RoutingNavidromeHttpClient(
            responses = mapOf(
                "getArtists" to GET_ARTISTS_JSON,
                "getArtist" to GET_ARTIST_JSON,
                "getAlbum" to GET_ALBUM_JSON,
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
        )

        val candidate = report.tracks.single()
        assertEquals("Blue", candidate.title)
        assertEquals("Artist A", candidate.artistName)
        assertEquals("Album A", candidate.albumTitle)
        assertEquals(215_000L, candidate.durationMs)
        assertEquals(4, candidate.trackNumber)
        assertEquals(1, candidate.discNumber)
        assertEquals(12345L, candidate.sizeBytes)
        assertEquals("Artist A/Album A/Blue.flac", candidate.relativePath)
        assertEquals("lynmusic-navidrome://nav-source/song-1", candidate.mediaLocator)
        assertEquals("lynmusic-navidrome-cover://nav-source/cover-1", candidate.artworkLocator)
    }

    @Test
    fun `request lyrics uses token auth without plaintext password`() = runTest {
        var capturedUrl: String? = null
        val client = object : LyricsHttpClient {
            override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
                capturedUrl = request.url
                return Result.success(
                    LyricsHttpResponse(
                        statusCode = 200,
                        body = GET_LYRICS_JSON,
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
                mediaLocator = "lynmusic-navidrome://nav-source/song-1",
                relativePath = "Artist A/Album A/Blue.flac",
            ),
        )

        val requestUrl = requireNotNull(capturedUrl)
        val parsed = requireNotNull(parseUrl(requestUrl))
        assertNotNull(lyrics)
        assertEquals(NAVIDROME_LYRICS_SOURCE_ID, lyrics.sourceId)
        assertEquals("getLyrics", parsed.encodedPath.substringAfterLast('/'))
        assertEquals("demo", parsed.parameters["u"])
        assertEquals("json", parsed.parameters["f"])
        assertFalse(requestUrl.contains("plain-pass"))
        assertFalse(parsed.parameters.names().contains("p"))
    }
}

private class RoutingNavidromeHttpClient(
    private val responses: Map<String, String>,
) : LyricsHttpClient {
    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        val endpoint = requireNotNull(parseUrl(request.url)).encodedPath.substringAfterLast('/')
        val body = responses[endpoint]
            ?: return Result.failure(IllegalArgumentException("Unexpected Navidrome endpoint: $endpoint"))
        return Result.success(LyricsHttpResponse(statusCode = 200, body = body))
    }
}

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
