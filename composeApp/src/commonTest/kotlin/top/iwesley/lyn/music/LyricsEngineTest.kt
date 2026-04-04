package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.data.repository.defaultLyricsSourceConfigs
import top.iwesley.lyn.music.data.repository.LRCLIB_PLAIN_JSON_MAP_EXTRACTOR
import top.iwesley.lyn.music.data.repository.LRCLIB_SYNCED_JSON_MAP_EXTRACTOR
import top.iwesley.lyn.music.data.repository.sanitizeLrclibQueryTemplate
import top.iwesley.lyn.music.domain.buildLyricsRequest
import top.iwesley.lyn.music.domain.parseLyricsPayload
import top.iwesley.lyn.music.domain.parseLyricsPayloadResult

class LyricsEngineTest {

    @Test
    fun `build request interpolates track fields`() {
        val track = Track(
            id = "track-1",
            sourceId = "source-1",
            title = "Blue Sky",
            artistName = "Nova",
            albumTitle = "Horizons",
            durationMs = 218_000,
            mediaLocator = "/music/blue-sky.mp3",
            relativePath = "blue-sky.mp3",
        )
        val config = LyricsSourceConfig(
            id = "cfg",
            name = "Demo",
            method = RequestMethod.GET,
            urlTemplate = "https://lyrics.example/search",
            queryTemplate = "title={title}&artist={artist}&duration={duration_seconds}",
            responseFormat = LyricsResponseFormat.JSON,
            extractor = "json:data.lyrics",
        )

        val request = buildLyricsRequest(config, track)

        assertTrue(request.url.contains("title=Blue%20Sky"))
        assertTrue(request.url.contains("artist=Nova"))
        assertTrue(request.url.contains("duration=218"))
    }

    @Test
    fun `json payload can map array based lyrics`() {
        val config = LyricsSourceConfig(
            id = "cfg",
            name = "Demo",
            urlTemplate = "https://lyrics.example",
            responseFormat = LyricsResponseFormat.JSON,
            extractor = "json-lines:data.lines|time,text",
        )
        val payload = """
            {
              "data": {
                "lines": [
                  { "time": "00:12.50", "text": "Wake up slowly" },
                  { "time": "00:16.00", "text": "Let the city glow" }
                ]
              }
            }
        """.trimIndent()

        val lyrics = parseLyricsPayload(config, payload)

        assertEquals(2, lyrics?.lines?.size)
        assertEquals(12_500, lyrics?.lines?.first()?.timestampMs)
        assertEquals("Wake up slowly", lyrics?.lines?.first()?.text)
    }

    @Test
    fun `xml payload can extract embedded lrc block`() {
        val config = LyricsSourceConfig(
            id = "cfg",
            name = "XML",
            urlTemplate = "https://lyrics.example",
            responseFormat = LyricsResponseFormat.XML,
            extractor = "xml:lyrics",
        )
        val payload = """
            <response>
              <lyrics>[00:01.00]hello
[00:02.50]world</lyrics>
            </response>
        """.trimIndent()

        val lyrics = parseLyricsPayload(config, payload)

        assertEquals(2, lyrics?.lines?.size)
        assertEquals("world", lyrics?.lines?.last()?.text)
        assertEquals(2_500, lyrics?.lines?.last()?.timestampMs)
    }

    @Test
    fun `json extractor can read first item from array root for lrclib search`() {
        val config = LyricsSourceConfig(
            id = "cfg",
            name = "LRCLIB",
            urlTemplate = "https://lrclib.net/api/search",
            responseFormat = LyricsResponseFormat.JSON,
            extractor = "json:[0].syncedLyrics",
        )
        val payload = """
            [
              {
                "id": 1,
                "trackName": "Blue Sky",
                "artistName": "Nova",
                "syncedLyrics": "[00:01.00]hello\n[00:03.50]world"
              }
            ]
        """.trimIndent()

        val lyrics = parseLyricsPayload(config, payload)

        assertEquals(2, lyrics?.lines?.size)
        assertEquals("hello", lyrics?.lines?.first()?.text)
        assertEquals(3_500, lyrics?.lines?.last()?.timestampMs)
    }

    @Test
    fun `json map extractor can parse lyrics metadata from array item`() {
        val config = LyricsSourceConfig(
            id = "cfg",
            name = "Mapped",
            urlTemplate = "https://lyrics.example/search",
            responseFormat = LyricsResponseFormat.JSON,
            extractor = "json-map:[0]|lyrics=syncedLyrics,title=trackName,artist=artistName,album=albumName,durationSeconds=duration,id=id",
        )
        val payload = """
            [
              {
                "id": "song-42",
                "trackName": "Blue Sky",
                "artistName": "Nova",
                "albumName": "Horizons",
                "duration": "218",
                "syncedLyrics": "[00:01.00]hello\n[00:03.50]world"
              }
            ]
        """.trimIndent()

        val parsed = parseLyricsPayloadResult(config, payload)

        assertEquals(2, parsed?.document?.lines?.size)
        assertEquals("song-42", parsed?.itemId)
        assertEquals("Blue Sky", parsed?.title)
        assertEquals("Nova", parsed?.artistName)
        assertEquals("Horizons", parsed?.albumTitle)
        assertEquals(218, parsed?.durationSeconds)
    }

    @Test
    fun `default lyrics sources include lrclib synced and plain`() {
        val configs = defaultLyricsSourceConfigs()

        assertEquals(2, configs.size)
        assertEquals("lrclib-synced", configs[0].id)
        assertEquals(LRCLIB_SYNCED_JSON_MAP_EXTRACTOR, configs[0].extractor)
        assertEquals("lrclib-plain", configs[1].id)
        assertEquals(LRCLIB_PLAIN_JSON_MAP_EXTRACTOR, configs[1].extractor)
        assertTrue(configs.all { it.urlTemplate == "https://lrclib.net/api/get" })
        assertTrue(configs.all { it.queryTemplate == "track_name={title}&artist_name={artist}" })
    }

    @Test
    fun `lrclib query template drops album and duration params`() {
        val sanitized = sanitizeLrclibQueryTemplate(
            "track_name={title}&artist_name={artist}&album_name={album}&duration={duration_seconds}&foo=bar",
        )

        assertEquals("track_name={title}&artist_name={artist}&foo=bar", sanitized)
    }
}
