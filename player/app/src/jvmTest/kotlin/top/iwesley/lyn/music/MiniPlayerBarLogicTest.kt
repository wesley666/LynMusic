package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine

class MiniPlayerBarLogicTest {

    @Test
    fun `mini player lyrics prefers highlighted line`() {
        val lyrics = lyricsDocument(
            LyricsLine(timestampMs = 1_000L, text = "第一句"),
            LyricsLine(timestampMs = 2_000L, text = "第二句"),
        )

        assertEquals(
            "第二句",
            resolveMiniPlayerLyricsText(
                lyrics = lyrics,
                highlightedLineIndex = 1,
                isLyricsLoading = false,
            ),
        )
    }

    @Test
    fun `mini player lyrics falls back to first non blank line`() {
        val lyrics = lyricsDocument(
            LyricsLine(timestampMs = 1_000L, text = "   "),
            LyricsLine(timestampMs = 2_000L, text = "第一句"),
            LyricsLine(timestampMs = 3_000L, text = "第二句"),
        )

        assertEquals(
            "第一句",
            resolveMiniPlayerLyricsText(
                lyrics = lyrics,
                highlightedLineIndex = -1,
                isLyricsLoading = false,
            ),
        )
    }

    @Test
    fun `mini player lyrics returns loading text while lyrics are preparing`() {
        assertEquals(
            "正在准备歌词",
            resolveMiniPlayerLyricsText(
                lyrics = null,
                highlightedLineIndex = -1,
                isLyricsLoading = true,
            ),
        )
    }

    @Test
    fun `mini player lyrics returns null when there is no lyric text and not loading`() {
        val lyrics = lyricsDocument(
            LyricsLine(timestampMs = 1_000L, text = "   "),
            LyricsLine(timestampMs = 2_000L, text = ""),
        )

        assertNull(
            resolveMiniPlayerLyricsText(
                lyrics = lyrics,
                highlightedLineIndex = -1,
                isLyricsLoading = false,
            ),
        )
    }

    @Test
    fun `mini player lyrics content is only ready for actual lyric text`() {
        assertFalse(hasMiniPlayerLyricsContent(showPortraitLyrics = true, lyricsText = null))
        assertFalse(hasMiniPlayerLyricsContent(showPortraitLyrics = true, lyricsText = ""))
        assertFalse(hasMiniPlayerLyricsContent(showPortraitLyrics = true, lyricsText = "正在准备歌词"))
        assertFalse(hasMiniPlayerLyricsContent(showPortraitLyrics = false, lyricsText = "第一句"))
        assertTrue(hasMiniPlayerLyricsContent(showPortraitLyrics = true, lyricsText = "第一句"))
    }

    @Test
    fun `compact player lyrics prefers highlighted line`() {
        val lyrics = lyricsDocument(
            LyricsLine(timestampMs = 1_000L, text = "第一句"),
            LyricsLine(timestampMs = 2_000L, text = "第二句"),
        )

        assertEquals(
            "第二句",
            resolveCompactPlayerLyricsText(
                lyrics = lyrics,
                highlightedLineIndex = 1,
            ),
        )
    }

    @Test
    fun `compact player lyrics falls back to first non blank line`() {
        val lyrics = lyricsDocument(
            LyricsLine(timestampMs = 1_000L, text = "   "),
            LyricsLine(timestampMs = 2_000L, text = "第一句"),
            LyricsLine(timestampMs = 3_000L, text = "第二句"),
        )

        assertEquals(
            "第一句",
            resolveCompactPlayerLyricsText(
                lyrics = lyrics,
                highlightedLineIndex = -1,
            ),
        )
    }

    @Test
    fun `compact player lyrics returns null when there is no actual lyric text`() {
        val lyrics = lyricsDocument(
            LyricsLine(timestampMs = 1_000L, text = "   "),
            LyricsLine(timestampMs = 2_000L, text = ""),
        )

        assertNull(
            resolveCompactPlayerLyricsText(
                lyrics = lyrics,
                highlightedLineIndex = -1,
            ),
        )
    }

    @Test
    fun `compact player lyrics does not surface loading placeholder without lyrics`() {
        assertNull(
            resolveCompactPlayerLyricsText(
                lyrics = null,
                highlightedLineIndex = -1,
            ),
        )
    }

    @Test
    fun `compact player lyrics visibility requires enabled setting and actual lyric text`() {
        assertFalse(shouldShowCompactPlayerLyrics(enabled = false, compactLyricsText = "第一句"))
        assertFalse(shouldShowCompactPlayerLyrics(enabled = true, compactLyricsText = null))
        assertFalse(shouldShowCompactPlayerLyrics(enabled = true, compactLyricsText = ""))
        assertTrue(shouldShowCompactPlayerLyrics(enabled = true, compactLyricsText = "第一句"))
    }

    @Test
    fun `player info vinyl size grows with larger compact space`() {
        val small = resolvePlayerInfoVinylSize(
            maxWidth = 320.dp,
            maxHeight = 360.dp,
            compact = true,
            hasCompactLyrics = false,
        )
        val large = resolvePlayerInfoVinylSize(
            maxWidth = 420.dp,
            maxHeight = 520.dp,
            compact = true,
            hasCompactLyrics = false,
        )

        assertTrue(large > small)
    }

    @Test
    fun `player info vinyl size keeps compact sizing identical with or without lyrics`() {
        val withoutLyrics = resolvePlayerInfoVinylSize(
            maxWidth = 390.dp,
            maxHeight = 520.dp,
            compact = true,
            hasCompactLyrics = false,
        )
        val withLyrics = resolvePlayerInfoVinylSize(
            maxWidth = 390.dp,
            maxHeight = 520.dp,
            compact = true,
            hasCompactLyrics = true,
        )

        assertEquals(withoutLyrics, withLyrics)
    }

    @Test
    fun `player info vinyl size is capped on large wide layouts`() {
        assertEquals(
            400.dp,
            resolvePlayerInfoVinylSize(
                maxWidth = 900.dp,
                maxHeight = 900.dp,
                compact = false,
                hasCompactLyrics = false,
            ),
        )
    }

    private fun lyricsDocument(vararg lines: LyricsLine): LyricsDocument {
        return LyricsDocument(
            lines = lines.toList(),
            sourceId = "test-source",
            rawPayload = "lyrics",
        )
    }
}
