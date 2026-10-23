package top.iwesley.lyn.music

import androidx.compose.ui.geometry.Rect
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
    fun `unstarted and completed masks have exact endpoint coverage`() {
        val rect = Rect(0f, 0f, 100f, 30f)
        val geometry = listOf(LyricsSegmentGeometry(listOf(LyricsFillFragment(rect, false))))
        val unstarted = buildLyricsFillMask(geometry, listOf(0f), 14f)
        assertTrue(unstarted.solidRects.isEmpty())
        assertTrue(unstarted.edges.isEmpty())
        val complete = buildLyricsFillMask(geometry, listOf(1f), 14f)
        assertEquals(listOf(rect), complete.solidRects)
        assertTrue(complete.edges.isEmpty())
    }

    @Test
    fun `soft edge is 14px and capped at 60 percent of the fragment width`() {
        fun mask(width: Float) = buildLyricsFillMask(
            listOf(LyricsSegmentGeometry(listOf(LyricsFillFragment(Rect(0f, 0f, width, 30f), false)))),
            listOf(0.5f), 14f,
        )
        assertEquals(Rect(0f, 0f, 43f, 30f), mask(100f).solidRects.single())
        assertEquals(Rect(43f, 0f, 57f, 30f), mask(100f).edges.single().rect)
        assertEquals(Rect(2f, 0f, 8f, 30f), mask(10f).edges.single().rect)
    }

    @Test
    fun `wrapped fragments consume fill distance in reading order`() {
        val first = Rect(20f, 0f, 80f, 30f)
        val second = Rect(0f, 30f, 100f, 60f)
        val geometry = listOf(LyricsSegmentGeometry(listOf(
            LyricsFillFragment(first, false), LyricsFillFragment(second, false),
        )))
        val mask = buildLyricsFillMask(geometry, listOf(0.5f), 14f)
        assertEquals(listOf(first, Rect(0f, 30f, 13f, 60f)), mask.solidRects)
        assertEquals(Rect(13f, 30f, 27f, 60f), mask.edges.single().rect)
    }

    @Test
    fun `right to left fragments fill from their right edge`() {
        val geometry = listOf(LyricsSegmentGeometry(listOf(LyricsFillFragment(Rect(0f, 0f, 100f, 30f), true))))
        val mask = buildLyricsFillMask(geometry, listOf(0.25f), 14f)
        assertEquals(listOf(Rect(82f, 0f, 100f, 30f)), mask.solidRects)
        assertEquals(Rect(68f, 0f, 82f, 30f), mask.edges.single().rect)
        assertTrue(mask.edges.single().fragment.isRightToLeft)
    }
}
