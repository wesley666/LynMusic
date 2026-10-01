package top.iwesley.lyn.music.feature.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine

class DesktopLyricsOverlayTextResolverTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `highlighted visible line wins`() {
        val lyrics = lyricsDocument(
            LyricsLine(timestampMs = 0, text = "第一句"),
            LyricsLine(timestampMs = 1000, text = "第二句"),
        )

        assertEquals(
            top.iwesley.lyn.music.core.model.UiText.Raw("第二句"),
            resolveDesktopLyricsOverlayText(
                lyrics = lyrics,
                highlightedLineIndex = 1,
                isLyricsLoading = false,
            ),
        )
    }

    @Test
    fun `blank and structure highlighted lines fall back to nearby visible line`() {
        val lyrics = lyricsDocument(
            LyricsLine(timestampMs = 0, text = "[Verse 1]"),
            LyricsLine(timestampMs = 1000, text = "   "),
            LyricsLine(timestampMs = 2000, text = "可见歌词"),
        )

        assertEquals(
            top.iwesley.lyn.music.core.model.UiText.Raw("可见歌词"),
            resolveDesktopLyricsOverlayText(
                lyrics = lyrics,
                highlightedLineIndex = 0,
                isLyricsLoading = false,
            ),
        )
    }

    @Test
    fun `loading fallback is shown when no lyrics text is available`() {
        assertEquals(
            DESKTOP_LYRICS_LOADING_TEXT,
            resolveDesktopLyricsOverlayText(
                lyrics = null,
                highlightedLineIndex = -1,
                isLyricsLoading = true,
            ),
        )
    }

    @Test
    fun `empty lyrics without loading hides overlay`() {
        assertNull(
            resolveDesktopLyricsOverlayText(
                lyrics = lyricsDocument(LyricsLine(timestampMs = 0, text = "[Chorus]")),
                highlightedLineIndex = 0,
                isLyricsLoading = false,
            ),
        )
    }

    @Test
    fun `highlight line is resolved from synced timestamp`() {
        val lyrics = lyricsDocument(
            LyricsLine(timestampMs = 0, text = "第一句"),
            LyricsLine(timestampMs = 1500, text = "第二句"),
        )

        assertEquals(1, findDesktopLyricsHighlightedLine(lyrics, positionMs = 1600))
    }

    private fun lyricsDocument(vararg lines: LyricsLine): LyricsDocument {
        return LyricsDocument(
            lines = lines.toList(),
            sourceId = "test",
            rawPayload = lines.joinToString("\n") { it.text },
        )
    }
}
