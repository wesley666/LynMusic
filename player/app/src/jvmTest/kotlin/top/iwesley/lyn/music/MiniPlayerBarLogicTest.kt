package top.iwesley.lyn.music

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine

class MiniPlayerBarLogicTest {
    @Test
    fun `portrait mini player layout returns true when height is greater than width`() {
        assertTrue(buildLayoutProfile(maxWidth = 390.dp, maxHeight = 844.dp).usesPortraitMiniPlayer)
    }

    @Test
    fun `portrait mini player layout returns false when width is greater than height`() {
        assertFalse(buildLayoutProfile(maxWidth = 844.dp, maxHeight = 390.dp).usesPortraitMiniPlayer)
    }

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

    private fun lyricsDocument(vararg lines: LyricsLine): LyricsDocument {
        return LyricsDocument(
            lines = lines.toList(),
            sourceId = "test-source",
            rawPayload = "lyrics",
        )
    }
}
