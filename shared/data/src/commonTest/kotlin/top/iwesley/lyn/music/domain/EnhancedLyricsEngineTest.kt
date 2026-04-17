package top.iwesley.lyn.music.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EnhancedLyricsEngineTest {
    @Test
    fun `enhanced lrc with line and inline timestamps parses plain lyric lines and segments`() {
        val rawLyrics = """
            [offset:+500]
            [ti:Sample Song]
            [00:01.00]<00:01.00>你<00:01.30>好
            [00:03.00]<00:03.00>世<00:03.40>界
        """.trimIndent()

        val document = parseCachedLyrics(sourceId = "test-source", rawPayload = rawLyrics)
        val presentation = parseEnhancedLyricsPresentation(
            rawPayload = rawLyrics,
            fallbackDocument = requireNotNull(document),
        )

        assertNotNull(document)
        assertEquals(500L, document.offsetMs)
        assertEquals(listOf("你好", "世界"), document.lines.map { it.text })
        assertEquals(listOf(1_000L, 3_000L), document.lines.map { it.timestampMs })
        assertNotNull(presentation)
        assertEquals(2, presentation.lines.size)
        assertEquals(listOf("你", "好"), presentation.lines.first().segments.map { it.text })
        assertEquals(listOf(1_000L, 1_300L), presentation.lines.first().segments.map { it.startTimeMs })
    }

    @Test
    fun `enhanced lrc without line timestamp uses first inline timestamp for the line`() {
        val rawLyrics = "<00:01.20>Hello <00:02.40>world"

        val document = parseCachedLyrics(sourceId = "test-source", rawPayload = rawLyrics)
        val presentation = parseEnhancedLyricsPresentation(
            rawPayload = rawLyrics,
            fallbackDocument = requireNotNull(document),
        )

        assertNotNull(document)
        assertEquals(1_200L, document.lines.single().timestampMs)
        assertEquals("Hello world", document.lines.single().text)
        assertNotNull(presentation)
        assertEquals(2, presentation.lines.single().segments.size)
    }

    @Test
    fun `metadata tags are ignored and do not leak into displayed lyric lines`() {
        val rawLyrics = """
            [ar:Artist A]
            [al:Album A]
            [by:Someone]
            [00:01.00]第一句
        """.trimIndent()

        val document = parseCachedLyrics(sourceId = "test-source", rawPayload = rawLyrics)

        assertNotNull(document)
        assertEquals(listOf("第一句"), document.lines.map { it.text })
        assertNull(parseEnhancedLyricsPresentation(rawPayload = rawLyrics, fallbackDocument = document))
    }

    @Test
    fun `ordinary lrc still parses as before`() {
        val rawLyrics = """
            [00:01.00]第一句
            [00:02.50]第二句
        """.trimIndent()

        val document = parseCachedLyrics(sourceId = "test-source", rawPayload = rawLyrics)

        assertNotNull(document)
        assertEquals(0L, document.offsetMs)
        assertEquals(listOf(1_000L, 2_500L), document.lines.map { it.timestampMs })
        assertEquals(listOf("第一句", "第二句"), document.lines.map { it.text })
    }

    @Test
    fun `plain text still falls back when no structured lyric syntax exists`() {
        val rawLyrics = "第一句\n第二句"

        val document = parseCachedLyrics(sourceId = "test-source", rawPayload = rawLyrics)

        assertNotNull(document)
        assertEquals(listOf(null, null), document.lines.map { it.timestampMs })
        assertEquals(listOf("第一句", "第二句"), document.lines.map { it.text })
    }

    @Test
    fun `navidrome structured lyrics preserves main cue lines as enhanced presentation`() {
        val rawLyrics = """
            {
              "lyricsList": {
                "structuredLyrics": [
                  {
                    "kind": "translation",
                    "synced": true,
                    "offset": 999,
                    "line": [
                      { "start": 1000, "value": "Hello" }
                    ]
                  },
                  {
                    "kind": "main",
                    "synced": true,
                    "offset": 150,
                    "line": [
                      { "start": 1000, "value": "你好" },
                      { "start": 2400, "value": "世界" }
                    ],
                    "cueLine": [
                      {
                        "index": 0,
                        "start": 1000,
                        "end": 2400,
                        "value": "你好",
                        "cue": [
                          { "start": 1000, "end": 1500, "value": "你", "byteStart": 0, "byteEnd": 2 },
                          { "start": 1500, "end": 2000, "value": "好", "byteStart": 3, "byteEnd": 5 }
                        ]
                      },
                      {
                        "index": 1,
                        "start": 2400,
                        "end": 3800,
                        "value": "世界",
                        "cue": [
                          { "start": 2400, "end": 2900, "value": "世", "byteStart": 0, "byteEnd": 2 },
                          { "start": 2900, "end": 3400, "value": "界", "byteStart": 3, "byteEnd": 5 }
                        ]
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val document = parseCachedLyrics(sourceId = NAVIDROME_LYRICS_SOURCE_ID, rawPayload = rawLyrics)
        val presentation = parseEnhancedLyricsPresentation(
            rawPayload = rawLyrics,
            fallbackDocument = requireNotNull(document),
        )

        assertNotNull(document)
        assertEquals(150L, document.offsetMs)
        assertEquals(listOf("你好", "世界"), document.lines.map { it.text })
        assertEquals(listOf(1_000L, 2_400L), document.lines.map { it.timestampMs })
        assertNotNull(presentation)
        assertEquals(150L, presentation.offsetMs)
        assertEquals(listOf("你", "好"), presentation.lines.first().segments.map { it.text })
        assertEquals(listOf(1_000L, 1_500L), presentation.lines.first().segments.map { it.startTimeMs })
    }

    @Test
    fun `navidrome structured lyrics without cue lines stay line based`() {
        val rawLyrics = """
            {
              "lyricsList": {
                "structuredLyrics": [
                  {
                    "synced": true,
                    "offset": 80,
                    "line": [
                      { "start": 1000, "value": "第一句" },
                      { "start": 2000, "value": "第二句" }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val document = parseCachedLyrics(sourceId = NAVIDROME_LYRICS_SOURCE_ID, rawPayload = rawLyrics)

        assertNotNull(document)
        assertEquals(80L, document.offsetMs)
        assertEquals(listOf("第一句", "第二句"), document.lines.map { it.text })
        assertNull(parseEnhancedLyricsPresentation(rawPayload = rawLyrics, fallbackDocument = document))
    }
}
