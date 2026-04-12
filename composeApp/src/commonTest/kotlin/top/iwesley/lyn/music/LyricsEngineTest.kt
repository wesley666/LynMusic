package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.data.repository.defaultWorkflowLyricsSourceConfigs
import top.iwesley.lyn.music.data.repository.defaultLyricsSourceConfigs
import top.iwesley.lyn.music.data.repository.LRCLIB_JSON_MAP_EXTRACTOR
import top.iwesley.lyn.music.data.repository.sanitizeLrclibQueryTemplate
import top.iwesley.lyn.music.domain.PRESET_OIAPI_QQMUSIC_SOURCE_ID
import top.iwesley.lyn.music.domain.PRESET_OIAPI_QQMUSIC_SOURCE_NAME
import top.iwesley.lyn.music.domain.PRESET_OIAPI_QQMUSIC_SOURCE_PRIORITY
import top.iwesley.lyn.music.domain.buildLyricsRequest
import top.iwesley.lyn.music.domain.parseLyricsPayload
import top.iwesley.lyn.music.domain.parseLyricsPayloadResult
import top.iwesley.lyn.music.domain.parseLyricsPayloadResults

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
    fun `json lines extractor supports nested time paths`() {
        val config = LyricsSourceConfig(
            id = "cfg",
            name = "Nested JSON",
            urlTemplate = "https://lyrics.example",
            responseFormat = LyricsResponseFormat.JSON,
            extractor = "json-lines:time.total,text",
        )
        val payload = """
            [
              {
                "text": "第一句",
                "time": {
                  "total": 30.88
                }
              },
              {
                "text": "第二句",
                "time": {
                  "total": 34.24
                }
              }
            ]
        """.trimIndent()

        val lyrics = parseLyricsPayload(config, payload)

        assertEquals(2, lyrics?.lines?.size)
        assertEquals(30_880, lyrics?.lines?.first()?.timestampMs)
        assertEquals("第一句", lyrics?.lines?.first()?.text)
        assertEquals(34_240, lyrics?.lines?.last()?.timestampMs)
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
                "duration": "218.0",
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
    fun `json map extractor can expand multiple candidates from array root`() {
        val config = LyricsSourceConfig(
            id = "cfg",
            name = "Mapped",
            urlTemplate = "https://lyrics.example/search",
            responseFormat = LyricsResponseFormat.JSON,
            extractor = "json-map:lyrics=plainLyrics,title=trackName,artist=artistName,album=albumName,durationSeconds=duration,id=id,coverUrl=cover",
        )
        val payload = """
            [
              {
                "id": "song-1",
                "trackName": "Blue Sky",
                "artistName": "Nova",
                "albumName": "Horizons",
                "duration": 218.0,
                "plainLyrics": "hello",
                "cover": "https://img.example.com/blue-sky.jpg"
              },
              {
                "id": "song-2",
                "trackName": "Night Drive",
                "artistName": "Nova",
                "albumName": "Horizons",
                "duration": 221.3,
                "plainLyrics": "world",
                "cover": ""
              }
            ]
        """.trimIndent()

        val parsed = parseLyricsPayloadResults(config, payload)

        assertEquals(2, parsed.size)
        assertEquals("song-1", parsed[0].itemId)
        assertEquals("Blue Sky", parsed[0].title)
        assertEquals(218, parsed[0].durationSeconds)
        assertEquals("https://img.example.com/blue-sky.jpg", parsed[0].artworkLocator)
        assertEquals("song-2", parsed[1].itemId)
        assertEquals("Night Drive", parsed[1].title)
        assertEquals(221, parsed[1].durationSeconds)
        assertEquals(null, parsed[1].artworkLocator)
    }

    @Test
    fun `json map extractor can parse lrcapi style payload with cover and mixed lyrics formats`() {
        val config = LyricsSourceConfig(
            id = "managed-lrcapi",
            name = "LrcAPI",
            urlTemplate = "https://api.lrc.cx/jsonapi",
            responseFormat = LyricsResponseFormat.JSON,
            extractor = "json-map:lyrics=lyrics|lrc,title=title,artist=artist,album=album,durationSeconds=duration,id=id,coverUrl=cover",
        )
        val payload = """
            [
              {
                "album": "魔鬼的情诗",
                "artist": "陈升",
                "cover": "",
                "duration": 312,
                "id": "95245987644134159528983016213969260445",
                "lyrics": "別讓我哭\n\n因為有山 才能依偎著雲",
                "source": "lrclib",
                "title": "别让我哭"
              },
              {
                "album": "国语歌曲精华60",
                "artist": "林全杰",
                "cover": "https://p2.music.126.net/knNOB-JbjalGScjDIOIrrw==/109951167790254711.jpg",
                "duration": 326,
                "id": "59402190333159901765762989026506192433",
                "lrc": "[00:42.085]因为有山 才能依偎着云\n[00:48.269]然而它们可以生活在一起",
                "source": "netease",
                "title": "别让我哭"
              }
            ]
        """.trimIndent()

        val parsed = parseLyricsPayloadResults(config, payload)

        assertEquals(2, parsed.size)
        assertEquals(false, parsed[0].document.isSynced)
        assertEquals(true, parsed[1].document.isSynced)
        assertEquals(312, parsed[0].durationSeconds)
        assertEquals(326, parsed[1].durationSeconds)
        assertEquals(null, parsed[0].artworkLocator)
        assertEquals(
            "https://p2.music.126.net/knNOB-JbjalGScjDIOIrrw==/109951167790254711.jpg",
            parsed[1].artworkLocator,
        )
    }

    @Test
    fun `json map extractor can fallback lyrics across multiple fields`() {
        val config = LyricsSourceConfig(
            id = "cfg",
            name = "Fallback Lyrics",
            urlTemplate = "https://lyrics.example/search",
            responseFormat = LyricsResponseFormat.JSON,
            extractor = "json-map:lyrics=lyrics|syncedLyrics|plainLyrics,title=title",
        )
        val payload = """
            {
              "title": "Blue Sky",
              "lyrics": "",
              "syncedLyrics": "",
              "plainLyrics": "第一句\n第二句"
            }
        """.trimIndent()

        val parsed = parseLyricsPayloadResult(config, payload)

        assertEquals("Blue Sky", parsed?.title)
        assertEquals(false, parsed?.document?.isSynced)
        assertEquals(listOf("第一句", "第二句"), parsed?.document?.lines?.map { it.text })
    }

    @Test
    fun `json map extractor supports lyrics fallback with root path syntax`() {
        val config = LyricsSourceConfig(
            id = "cfg",
            name = "Fallback Root",
            urlTemplate = "https://lyrics.example/search",
            responseFormat = LyricsResponseFormat.JSON,
            extractor = "json-map:[0]|lyrics=lyrics|syncedLyrics|plainLyrics,title=title,id=id",
        )
        val payload = """
            [
              {
                "id": "song-1",
                "title": "Blue Sky",
                "lyrics": "",
                "syncedLyrics": "[00:01.00]hello\n[00:03.50]world",
                "plainLyrics": "plain"
              }
            ]
        """.trimIndent()

        val parsed = parseLyricsPayloadResult(config, payload)

        assertEquals("song-1", parsed?.itemId)
        assertEquals("Blue Sky", parsed?.title)
        assertEquals(true, parsed?.document?.isSynced)
        assertEquals(listOf("hello", "world"), parsed?.document?.lines?.map { it.text })
    }

    @Test
    fun `default lyrics sources include unified lrclib entry`() {
        val configs = defaultLyricsSourceConfigs()

        assertEquals(1, configs.size)
        assertEquals("lrclib", configs[0].id)
        assertEquals(LRCLIB_JSON_MAP_EXTRACTOR, configs[0].extractor)
        assertEquals("LRCLIB", configs[0].name)
        assertTrue(configs.all { it.urlTemplate == "https://lrclib.net/api/search" })
        assertTrue(configs.all { it.queryTemplate == "track_name={title}&artist_name={artist}" })
    }

    @Test
    fun `default workflow lyrics sources include oiapi qqmusic entry`() {
        val configs = defaultWorkflowLyricsSourceConfigs()

        assertEquals(1, configs.size)
        assertEquals(PRESET_OIAPI_QQMUSIC_SOURCE_ID, configs[0].id)
        assertEquals(PRESET_OIAPI_QQMUSIC_SOURCE_NAME, configs[0].name)
        assertEquals(PRESET_OIAPI_QQMUSIC_SOURCE_PRIORITY, configs[0].priority)
        assertEquals(true, configs[0].enabled)
        assertEquals("https://oiapi.net/api/QQMusicLyric", configs[0].search.request.url)
        assertEquals(LyricsResponseFormat.JSON, configs[0].search.request.responseFormat)
        assertEquals(0.9, configs[0].selection.minScore)
        assertEquals("data.content", configs[0].lyrics.steps.single().payloadPath)
        assertEquals("message", configs[0].lyrics.steps.single().fallbackPayloadPath)
        assertEquals(LyricsResponseFormat.LRC, configs[0].lyrics.steps.single().format)
    }

    @Test
    fun `lrclib query template drops album and duration params`() {
        val sanitized = sanitizeLrclibQueryTemplate(
            "track_name={title}&artist_name={artist}&album_name={album}&duration={duration_seconds}&foo=bar",
        )

        assertEquals("track_name={title}&artist_name={artist}&foo=bar", sanitized)
    }
}
