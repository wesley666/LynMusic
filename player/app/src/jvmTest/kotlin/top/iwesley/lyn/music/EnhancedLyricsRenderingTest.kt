package top.iwesley.lyn.music

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.iwesley.lyn.music.domain.EnhancedLyricsDisplayLine
import top.iwesley.lyn.music.domain.EnhancedLyricsSegment

class EnhancedLyricsRenderingTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `segment fill fractions reflect completed active and upcoming segments`() {
        val line = EnhancedLyricsDisplayLine(
            text = "你好世界",
            lineStartTimeMs = 1_000L,
            lineEndTimeMs = 4_000L,
            segments = listOf(
                EnhancedLyricsSegment(text = "你", startTimeMs = 1_000L, endTimeMs = 2_000L),
                EnhancedLyricsSegment(text = "好", startTimeMs = 2_000L, endTimeMs = 3_000L),
                EnhancedLyricsSegment(text = "世界", startTimeMs = 3_000L, endTimeMs = 4_000L),
            ),
        )

        val fillFractions = resolveEnhancedLyricsSegmentFillFractions(
            line = line,
            currentPositionMs = 2_500L,
        )

        assertContentEquals(listOf(1f, 0.5f, 0f), fillFractions)
    }

    @Test
    fun `last segment without explicit end becomes complete once playback reaches its start`() {
        val line = EnhancedLyricsDisplayLine(
            text = "最后一句",
            lineStartTimeMs = 5_000L,
            segments = listOf(
                EnhancedLyricsSegment(text = "最后", startTimeMs = 5_000L, endTimeMs = 5_600L),
                EnhancedLyricsSegment(text = "一句", startTimeMs = 5_600L, endTimeMs = null),
            ),
        )

        val beforeStart = resolveEnhancedLyricsSegmentFillFractions(
            line = line,
            currentPositionMs = 5_400L,
        )
        val afterStart = resolveEnhancedLyricsSegmentFillFractions(
            line = line,
            currentPositionMs = 5_650L,
        )

        assertTrue(beforeStart.first() > 0f && beforeStart.first() < 1f)
        assertEquals(0f, beforeStart.last())
        assertEquals(1f, afterStart.last())
    }

    @Test
    fun `annotated string preserves eslrc segment spacing`() {
        val line = EnhancedLyricsDisplayLine(
            text = "Test Word",
            lineStartTimeMs = 1_000L,
            lineEndTimeMs = 1_800L,
            segments = listOf(
                EnhancedLyricsSegment(text = "Test", startTimeMs = 1_000L, endTimeMs = 1_200L),
                EnhancedLyricsSegment(text = " Word", startTimeMs = 1_200L, endTimeMs = 1_800L),
            ),
        )

        val annotated = buildEnhancedLyricsAnnotatedString(
            line = line,
            currentPositionMs = 1_300L,
            activeColor = Color.White,
            inactiveColor = Color.Black,
        )

        assertEquals("Test Word", annotated.text)
    }
}
