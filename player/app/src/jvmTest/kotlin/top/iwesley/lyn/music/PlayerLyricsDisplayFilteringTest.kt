package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine
import top.iwesley.lyn.music.domain.EnhancedLyricsDisplayLine
import top.iwesley.lyn.music.domain.EnhancedLyricsPresentation

class PlayerLyricsDisplayFilteringTest {
    @Test
    fun `build visible lyrics lines filters structure tags and keeps raw indices aligned`() {
        val lyrics = syncedLyricsDocument(
            LyricsLine(timestampMs = 1_000L, text = "[Verse]"),
            LyricsLine(timestampMs = 2_000L, text = "第一句"),
            LyricsLine(timestampMs = 3_000L, text = "[Chorus]"),
            LyricsLine(timestampMs = 4_000L, text = "第二句"),
        )
        val enhancedPresentation = EnhancedLyricsPresentation(
            lines = listOf(
                EnhancedLyricsDisplayLine(text = "[Verse]", lineStartTimeMs = 1_000L),
                EnhancedLyricsDisplayLine(text = "第一句", lineStartTimeMs = 2_000L, translationText = "line 1"),
                EnhancedLyricsDisplayLine(text = "[Chorus]", lineStartTimeMs = 3_000L),
                EnhancedLyricsDisplayLine(text = "第二句", lineStartTimeMs = 4_000L, translationText = "line 2"),
            ),
        )

        val visibleLines = buildVisiblePlayerLyricsLines(lyrics, enhancedPresentation)

        assertEquals(listOf(1, 3), visibleLines.map { it.rawIndex })
        assertEquals(listOf("第一句", "第二句"), visibleLines.map { it.line.text })
        assertEquals(listOf("line 1", "line 2"), visibleLines.map { it.enhancedLine?.translationText })
    }

    @Test
    fun `visible highlighted index skips hidden structure tags`() {
        val visibleLines = buildVisiblePlayerLyricsLines(
            syncedLyricsDocument(
                LyricsLine(timestampMs = 1_000L, text = "[Verse]"),
                LyricsLine(timestampMs = 2_000L, text = "第一句"),
                LyricsLine(timestampMs = 3_000L, text = "第二句"),
                LyricsLine(timestampMs = 4_000L, text = "[Outro]"),
            ),
        )

        assertEquals(0, resolveVisiblePlayerLyricsHighlightedIndex(visibleLines, highlightedRawIndex = 0))
        assertEquals(1, resolveVisiblePlayerLyricsHighlightedIndex(visibleLines, highlightedRawIndex = 2))
        assertEquals(1, resolveVisiblePlayerLyricsHighlightedIndex(visibleLines, highlightedRawIndex = 3))
        assertEquals(-1, resolveVisiblePlayerLyricsHighlightedIndex(visibleLines, highlightedRawIndex = -1))
    }

    @Test
    fun `visible lyrics scroll target respects synced and plain lyrics behavior after filtering`() {
        val syncedLyrics = syncedLyricsDocument(
            LyricsLine(timestampMs = 1_000L, text = "[Verse]"),
            LyricsLine(timestampMs = 2_000L, text = "第一句"),
        )
        val syncedVisibleLines = buildVisiblePlayerLyricsLines(syncedLyrics)
        assertEquals(
            0,
            resolveVisiblePlayerLyricsScrollTarget(
                lyrics = syncedLyrics,
                visibleLines = syncedVisibleLines,
                highlightedRawIndex = -1,
            ),
        )

        val plainLyrics = plainLyricsDocument(
            LyricsLine(timestampMs = null, text = "[Verse]"),
            LyricsLine(timestampMs = null, text = "第一句"),
        )
        val plainVisibleLines = buildVisiblePlayerLyricsLines(plainLyrics)
        assertNull(
            resolveVisiblePlayerLyricsScrollTarget(
                lyrics = plainLyrics,
                visibleLines = plainVisibleLines,
                highlightedRawIndex = -1,
            ),
        )
    }

    @Test
    fun `browse target chooses timestamped visible line closest to viewport center`() {
        val visibleLines = buildVisiblePlayerLyricsLines(
            syncedLyricsDocument(
                LyricsLine(timestampMs = 1_000L, text = "第一句"),
                LyricsLine(timestampMs = 2_000L, text = "第二句"),
                LyricsLine(timestampMs = 3_000L, text = "第三句"),
            ),
        )

        val target = resolvePlayerLyricsBrowseTargetIndex(
            visibleLines = visibleLines,
            visibleItems = listOf(
                PlayerLyricsVisibleItemInfo(index = 0, offset = 0, size = 40),
                PlayerLyricsVisibleItemInfo(index = 1, offset = 80, size = 40),
                PlayerLyricsVisibleItemInfo(index = 2, offset = 160, size = 40),
            ),
            viewportStartOffset = 0,
            viewportEndOffset = 220,
        )

        assertEquals(1, target)
    }

    @Test
    fun `browse target ignores visible lines without timestamp`() {
        val visibleLines = buildVisiblePlayerLyricsLines(
            syncedLyricsDocument(
                LyricsLine(timestampMs = null, text = "无时间轴"),
                LyricsLine(timestampMs = 2_000L, text = "第一句"),
                LyricsLine(timestampMs = 4_000L, text = "第二句"),
            ),
        )

        val target = resolvePlayerLyricsBrowseTargetIndex(
            visibleLines = visibleLines,
            visibleItems = listOf(
                PlayerLyricsVisibleItemInfo(index = 0, offset = 90, size = 40),
                PlayerLyricsVisibleItemInfo(index = 1, offset = 150, size = 40),
            ),
            viewportStartOffset = 0,
            viewportEndOffset = 220,
        )

        assertEquals(1, target)
    }

    @Test
    fun `lyrics seek position applies offset and clamps to duration`() {
        val visibleLines = buildVisiblePlayerLyricsLines(
            syncedLyricsDocument(
                LyricsLine(timestampMs = 1_000L, text = "第一句"),
                LyricsLine(timestampMs = 65_000L, text = "第二句"),
            ),
        )

        assertEquals(
            0L,
            resolvePlayerLyricsSeekPositionMs(
                line = visibleLines[0],
                lyricsOffsetMs = 2_000L,
                durationMs = 60_000L,
            ),
        )
        assertEquals(
            60_000L,
            resolvePlayerLyricsSeekPositionMs(
                line = visibleLines[1],
                lyricsOffsetMs = 2_000L,
                durationMs = 60_000L,
            ),
        )
        assertEquals("01:00", formatDuration(60_000L))
    }

    @Test
    fun `lyrics seek position is null for lines without timestamp`() {
        val visibleLines = buildVisiblePlayerLyricsLines(
            plainLyricsDocument(
                LyricsLine(timestampMs = null, text = "第一句"),
            ),
        )

        assertNull(
            resolvePlayerLyricsSeekPositionMs(
                line = visibleLines.single(),
                lyricsOffsetMs = 0L,
                durationMs = 60_000L,
            ),
        )
    }

    private fun syncedLyricsDocument(vararg lines: LyricsLine): LyricsDocument {
        return LyricsDocument(
            lines = lines.toList(),
            sourceId = "test-source",
            rawPayload = "synced",
        )
    }

    private fun plainLyricsDocument(vararg lines: LyricsLine): LyricsDocument {
        return LyricsDocument(
            lines = lines.toList(),
            sourceId = "test-source",
            rawPayload = "plain",
        )
    }
}
