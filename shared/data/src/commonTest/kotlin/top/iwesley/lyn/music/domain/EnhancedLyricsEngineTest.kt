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
    fun `eslrc parses line timestamps into enhanced segments`() {
        val rawLyrics = "[00:01.00]你[00:01.30]好[00:01.70]"

        val document = parseCachedLyrics(sourceId = "test-source", rawPayload = rawLyrics)
        val presentation = parseEnhancedLyricsPresentation(
            rawPayload = rawLyrics,
            fallbackDocument = requireNotNull(document),
        )

        assertNotNull(document)
        assertEquals(listOf("你好"), document.lines.map { it.text })
        assertEquals(listOf(1_000L), document.lines.map { it.timestampMs })
        assertNotNull(presentation)
        assertEquals("你好", presentation.lines.single().text)
        assertEquals(1_700L, presentation.lines.single().lineEndTimeMs)
        assertEquals(listOf("你", "好"), presentation.lines.single().segments.map { it.text })
        assertEquals(listOf(1_000L, 1_300L), presentation.lines.single().segments.map { it.startTimeMs })
        assertEquals(listOf(1_300L, 1_700L), presentation.lines.single().segments.map { it.endTimeMs })
    }

    @Test
    fun `eslrc merges adjacent english source and chinese translation at the same timestamp`() {
        val rawLyrics = """
            [00:08.92]Talk [00:09.67]to [00:10.15]me[00:10.63]
            [00:08.92]向[00:08.92]我[00:08.92]倾[00:08.92]诉[00:08.92]吧[00:08.92]
            [00:13.00]Spill [00:13.73]the [00:14.28]secrets[00:15.11]
            [00:13.00]吐[00:13.00]露[00:13.00]秘[00:13.00]密[00:13.00]
        """.trimIndent()

        val document = parseCachedLyrics(sourceId = "test-source", rawPayload = rawLyrics)
        val presentation = parseEnhancedLyricsPresentation(
            rawPayload = rawLyrics,
            fallbackDocument = requireNotNull(document),
        )

        assertEquals(listOf("Talk to me", "Spill the secrets"), document.lines.map { it.text })
        assertEquals(listOf(8_920L, 13_000L), document.lines.map { it.timestampMs })
        assertNotNull(presentation)
        assertEquals(listOf("向我倾诉吧", "吐露秘密"), presentation.lines.map { it.translationText })
        assertEquals(listOf("Talk ", "to ", "me"), presentation.lines.first().segments.map { it.text })
        assertEquals(listOf(8_920L, 9_670L, 10_150L), presentation.lines.first().segments.map { it.startTimeMs })
    }

    @Test
    fun `eslrc does not merge bilingual lines with different timestamps`() {
        val rawLyrics = """
            [00:08.92]Talk [00:09.67]to [00:10.15]me[00:10.63]
            [00:09.00]向[00:09.00]我[00:09.00]倾[00:09.00]诉[00:09.00]吧[00:09.00]
        """.trimIndent()

        val document = parseCachedLyrics(sourceId = "test-source", rawPayload = rawLyrics)
        val presentation = parseEnhancedLyricsPresentation(
            rawPayload = rawLyrics,
            fallbackDocument = requireNotNull(document),
        )

        assertEquals(listOf("Talk to me", "向我倾诉吧"), document.lines.map { it.text })
        assertNotNull(presentation)
        assertEquals(listOf(null, null), presentation.lines.map { it.translationText })
    }

    @Test
    fun `eslrc does not merge same timestamp lines unless first line is non cjk primary`() {
        val rawLyrics = """
            [00:01.00]你[00:01.30]好[00:01.70]
            [00:01.00]世[00:01.30]界[00:01.70]
        """.trimIndent()

        val document = parseCachedLyrics(sourceId = "test-source", rawPayload = rawLyrics)
        val presentation = parseEnhancedLyricsPresentation(
            rawPayload = rawLyrics,
            fallbackDocument = requireNotNull(document),
        )

        assertEquals(listOf("你好", "世界"), document.lines.map { it.text })
        assertNotNull(presentation)
        assertEquals(listOf(null, null), presentation.lines.map { it.translationText })
    }

    @Test
    fun `eslrc merges same timestamp non cjk source and cjk line without marker exclusions`() {
        val rawLyrics = """
            [00:00.00]Walk[00:00.17] [00:00.34]Thru[00:00.51] [00:00.68]Fire[00:00.85]
            [00:00.00]T[00:00.00]M[00:00.00]E[00:00.00]享[00:00.00]有[00:00.00]著[00:00.00]作[00:00.00]权[00:00.00]
        """.trimIndent()

        val document = parseCachedLyrics(sourceId = "test-source", rawPayload = rawLyrics)
        val presentation = parseEnhancedLyricsPresentation(
            rawPayload = rawLyrics,
            fallbackDocument = requireNotNull(document),
        )

        assertEquals(listOf("Walk Thru Fire"), document.lines.map { it.text })
        assertNotNull(presentation)
        assertEquals(listOf("TME享有著作权"), presentation.lines.map { it.translationText })
    }

    @Test
    fun `eslrc preserves offset spacing and sorts multiple lines`() {
        val rawLyrics = """
            [offset:+250]
            [00:03.00]世[00:03.40]界[00:03.90]
            [00:01.00]Test[00:01.20] Word[00:01.80]
        """.trimIndent()

        val document = parseCachedLyrics(sourceId = "test-source", rawPayload = rawLyrics)
        val presentation = parseEnhancedLyricsPresentation(
            rawPayload = rawLyrics,
            fallbackDocument = requireNotNull(document),
        )

        assertNotNull(document)
        assertEquals(250L, document.offsetMs)
        assertEquals(listOf("Test Word", "世界"), document.lines.map { it.text })
        assertEquals(listOf(1_000L, 3_000L), document.lines.map { it.timestampMs })
        assertNotNull(presentation)
        assertEquals(listOf("Test", " Word"), presentation.lines.first().segments.map { it.text })
        assertEquals(listOf(1_000L, 1_200L), presentation.lines.first().segments.map { it.startTimeMs })
        assertEquals(listOf(1_200L, 1_800L), presentation.lines.first().segments.map { it.endTimeMs })
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
    fun `multiple lrc timestamps still parse as repeated plain lines`() {
        val rawLyrics = "[00:01.00][00:02.50]同一句"

        val document = parseCachedLyrics(sourceId = "test-source", rawPayload = rawLyrics)

        assertNotNull(document)
        assertEquals(listOf(1_000L, 2_500L), document.lines.map { it.timestampMs })
        assertEquals(listOf("同一句", "同一句"), document.lines.map { it.text })
        assertNull(parseEnhancedLyricsPresentation(rawPayload = rawLyrics, fallbackDocument = document))
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
