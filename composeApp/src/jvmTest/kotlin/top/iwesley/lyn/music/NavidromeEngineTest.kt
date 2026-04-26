package top.iwesley.lyn.music

import io.ktor.http.parseUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
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
            supportedImportExtensions = setOf("flac"),
        )

        val candidate = report.tracks.single()
        assertEquals(1, report.discoveredAudioFileCount)
        assertTrue(report.failures.isEmpty())
        assertTrue(report.warnings.isEmpty())
        assertEquals("Blue", candidate.title)
        assertEquals("Artist A", candidate.artistName)
        assertEquals("Album A", candidate.albumTitle)
        assertEquals(215_000L, candidate.durationMs)
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
    }

    @Test
    fun `scan library counts unsupported suffixes as failures without empty warning`() = runTest {
        val client = RoutingNavidromeHttpClient(
            responses = mapOf(
                "getArtists" to GET_ARTISTS_JSON,
                "getArtist" to GET_ARTIST_JSON,
                "getAlbum" to GET_ALBUM_UNSUPPORTED_JSON,
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
            responses = mapOf(
                "getArtists" to GET_ARTISTS_JSON,
                "getArtist" to GET_ARTIST_JSON,
                "getAlbum" to GET_ALBUM_WITH_DUPLICATES_JSON,
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
        val client = RoutingNavidromeHttpClient(
            responses = mapOf(
                "getArtists" to GET_ARTISTS_JSON,
                "getArtist" to GET_ARTIST_JSON,
                "getAlbum" to GET_EMPTY_ALBUM_JSON,
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

        assertEquals(0, report.discoveredAudioFileCount)
        assertTrue(report.tracks.isEmpty())
        assertTrue(report.failures.isEmpty())
        assertEquals(listOf("当前 Navidrome 账号下没有可同步的歌曲。"), report.warnings)
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

        val requestUrl = requireNotNull(capturedUrl)
        val parsed = requireNotNull(parseUrl(requestUrl))
        assertNotNull(lyrics)
        assertEquals(NAVIDROME_LYRICS_SOURCE_ID, lyrics.sourceId)
        assertEquals("getLyricsBySongId", parsed.encodedPath.substringAfterLast('/'))
        assertEquals("song-1", parsed.parameters["id"])
        assertEquals("demo", parsed.parameters["u"])
        assertEquals("json", parsed.parameters["f"])
        assertFalse(requestUrl.contains("plain-pass"))
        assertFalse(parsed.parameters.names().contains("p"))
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

private const val GET_ALBUM_UNSUPPORTED_JSON = """
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
          "id": "song-2",
          "title": "Bad Ogg",
          "artist": "Artist A",
          "album": "Album A",
          "duration": 180,
          "track": 5,
          "discNumber": 1,
          "size": 54321,
          "suffix": "ogg",
          "coverArt": "cover-1"
        }
      ]
    }
  }
}
"""

private const val GET_ALBUM_WITH_DUPLICATES_JSON = """
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
        },
        {
          "id": "song-1",
          "title": "Blue Duplicate",
          "artist": "Artist A",
          "album": "Album A",
          "duration": 215,
          "track": 4,
          "discNumber": 1,
          "size": 12345,
          "suffix": "flac",
          "coverArt": "cover-1"
        },
        {
          "id": "song-2",
          "title": "No Suffix",
          "artist": "Artist A",
          "album": "Album A",
          "duration": 190,
          "track": 5,
          "discNumber": 1,
          "size": 11111,
          "suffix": "",
          "coverArt": "cover-1"
        },
        {
          "id": "song-3",
          "title": "Mystery",
          "artist": "Artist A",
          "album": "Album A",
          "duration": 200,
          "track": 6,
          "discNumber": 1,
          "size": 22222,
          "suffix": "dsf",
          "coverArt": "cover-1"
        }
      ]
    }
  }
}
"""

private const val GET_EMPTY_ALBUM_JSON = """
{
  "subsonic-response": {
    "status": "ok",
    "version": "1.16.1",
    "album": {
      "id": "album-1",
      "name": "Album A",
      "artist": "Artist A",
      "coverArt": "cover-1",
      "song": []
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
