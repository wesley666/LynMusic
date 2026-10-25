package top.iwesley.lyn.music.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LyricsTranslationVisibilityTest {
    @Test
    fun `traditional original with timed simplified header translation keeps words but never displays translation`() {
        val raw = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                itunes:timing="Word" xml:lang="zh-Hant">
              <head><metadata><iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
                <translations><translation xml:lang="zh-Hans"><text for="L1">
                  <span begin="44.552" end="45.053" xmlns="http://www.w3.org/ns/ttml">忘了</span><span begin="45.053" end="46.284" xmlns="http://www.w3.org/ns/ttml">关 那扇门</span> <span begin="46.284" end="46.720" xmlns="http://www.w3.org/ns/ttml">那扇</span><span begin="46.720" end="47.387" xmlns="http://www.w3.org/ns/ttml">窗</span>
                </text></translation></translations>
              </iTunesMetadata></metadata></head>
              <body><div><p begin="44.552" end="47.387" itunes:key="L1">
                <span begin="44.552" end="45.053">忘了</span><span begin="45.053" end="46.284">關 那扇門</span> <span begin="46.284" end="46.720">那扇</span><span begin="46.720" end="47.387">窗</span>
              </p></div></body>
            </tt>
        """.trimIndent()
        val document = assertNotNull(parseCachedLyrics("test", raw))
        val presentation = assertNotNull(parseEnhancedLyricsPresentation(raw, document))
        val line = presentation.lines.single()

        assertEquals("zh-Hant", presentation.originalLanguage)
        assertEquals("忘了關 那扇門 那扇窗", line.text)
        assertEquals("忘了关 那扇门 那扇窗", line.translationText)
        assertEquals(listOf(44_552L, 45_053L, 46_284L, 46_720L), line.segments.map { it.startTimeMs })
        assertEquals(listOf(45_053L, 46_284L, 46_720L, 47_387L), line.segments.map { it.endTimeMs })
        assertEquals(line.text, line.segments.joinToString("") { it.text })
        assertNull(presentation.translationTextFor(line, true))
        assertNull(presentation.translationTextFor(line, false))
        assertEquals(raw, document.rawPayload)

        // Visibility metadata must not invalidate the user's existing timing calibration.
        val otherLanguage = assertNotNull(parseCachedLyrics("test", raw.replace("xml:lang=\"zh-Hant\"", "xml:lang=\"en\"")))
        assertEquals(lyricsVersion(document), lyricsVersion(otherLanguage))
    }

    @Test
    fun `only root xml language matters regardless of line translation or lookalike language attributes`() {
        for ((rootAttributes, expectedLanguage) in listOf(
            "xml:lang=\"zh-Hant\"" to "zh-Hant",
            "xml:lang=\"en\"" to "en",
            "" to null,
            "other:lang=\"zh\"" to null,
        )) {
            val raw = """
                <tt xmlns:m="urn:metadata" xmlns:other="urn:other" $rootAttributes>
                  <body><p begin="1s" xml:lang="en">第一句<span m:role="x-translation" xml:lang="zh-Hans">First translation</span></p>
                  <p begin="2s" xml:lang="zh">第二句<span m:role="x-translation" xml:lang="zh-Hans">Second translation</span></p></body>
                </tt>
            """.trimIndent()
            val presentation = presentation(raw)
            assertEquals(expectedLanguage, presentation.originalLanguage)
            presentation.lines.forEach { line ->
                assertEquals(if (expectedLanguage == "zh-Hant") null else line.translationText, presentation.translationTextFor(line, true))
                assertNull(presentation.translationTextFor(line, false))
            }
        }
    }

    @Test
    fun `Chinese language aliases and variants are hidden while unknown languages follow switch`() {
        val line = EnhancedLyricsDisplayLine("原文", 0L, translationText = " Translation ")
        for (language in listOf("zh", "zh-Hans", "zh-Hant-TW", " ZH_cn ", "zho", "chi", "cmn", "cmn-Hans", "yue", "yue-Hant")) {
            val presentation = EnhancedLyricsPresentation(listOf(line), originalLanguage = language)
            assertNull(presentation.translationTextFor(line, true), language)
            assertNull(presentation.translationTextFor(line, false), language)
        }
        for (language in listOf(null, "", "und", "xxx", "ja", "en", "zhx")) {
            val presentation = EnhancedLyricsPresentation(listOf(line), originalLanguage = language)
            assertEquals("Translation", presentation.translationTextFor(line, true), language)
            assertNull(presentation.translationTextFor(line, false), language)
            assertNull(presentation.translationTextFor(line.copy(translationText = " \n "), true))
            assertNull(presentation.translationTextFor(null, true))
        }
    }

    @Test
    fun `lrc and eslrc document language tags are retained without becoming lyric lines`() {
        for (tag in listOf("lang", "LANGUAGE")) {
            val lrc = "[$tag: zh-Hant ]\n[00:01.00]原文"
            val parsed = assertNotNull(parseStructuredLyricsPayload(lrc))
            assertEquals("zh-Hant", parsed.originalLanguage)
            assertEquals(listOf("原文"), parsed.lines.map { it.text })
            for (language in listOf("zh-Hant", "en")) {
                val raw = """
                    [$tag: $language ]
                    [00:01.00]Hello [00:02.00]world[00:03.00]
                    [00:01.00]你[00:01.00]好[00:01.00]
                """.trimIndent()
                val presentation = presentation(raw)
                val line = presentation.lines.single()
                assertEquals(language, presentation.originalLanguage)
                assertEquals("你好", line.translationText)
                assertEquals(if (language == "en") "你好" else null, presentation.translationTextFor(line, true))
                assertNull(presentation.translationTextFor(line, false))
            }
        }
    }

    @Test
    fun `Navidrome uses selected main entry language instead of another entry language`() {
        for (language in listOf("zho", "en")) {
            val raw = """
                {"lyricsList":{"structuredLyrics":[
                  {"kind":"translation","lang":"zh","synced":true,"line":[{"start":1000,"value":"其他译文"}]},
                  {"kind":"main","lang":"$language","synced":true,"line":[
                    {"start":1000,"value":"[00:01.00]Hello [00:02.00]world[00:03.00]"},
                    {"start":1000,"value":"[00:01.00]你[00:01.00]好[00:01.00]"}
                  ]}
                ]}}
            """.trimIndent()
            val presentation = presentation(raw)
            val line = presentation.lines.single()
            assertEquals(language, presentation.originalLanguage)
            assertEquals("你好", line.translationText)
            assertEquals(if (language == "en") "你好" else null, presentation.translationTextFor(line, true))
            assertNull(presentation.translationTextFor(line, false))
        }
    }

    private fun presentation(raw: String): EnhancedLyricsPresentation =
        assertNotNull(parseEnhancedLyricsPresentation(raw, assertNotNull(parseCachedLyrics("test", raw))))
}
