package top.iwesley.lyn.music.automotive.ui

import top.iwesley.lyn.music.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.iwesley.lyn.music.domain.EnhancedLyricsDisplayLine
import top.iwesley.lyn.music.domain.EnhancedLyricsSegment

class LyricsPlaybackClockTest {
    @Test
    fun `interpolates reports and caps a stalled report at 500ms`() {
        val clock = LyricsPlaybackClock(1_000L, true, 0L)
        clock.tick(123L)
        assertEquals(1_123L, clock.positionMs)
        clock.tick(499L)
        assertEquals(1_499L, clock.positionMs)
        assertTrue(clock.canExtrapolate(499L))
        clock.tick(900L)
        assertEquals(1_500L, clock.positionMs)
        assertFalse(clock.canExtrapolate(900L))
    }

    @Test
    fun `unchanged reports and unrelated recompositions do not renew the anchor`() {
        val clock = LyricsPlaybackClock(1_000L, true, 0L)
        for (now in 100L..1_000L step 100L) {
            clock.report(1_000L, true, now)
            clock.tick(now)
            assertEquals(1_000L + now.coerceAtMost(500L), clock.positionMs)
        }
    }

    @Test
    fun `continuous playback reports keep extrapolation moving`() {
        val clock = LyricsPlaybackClock(0L, true, 0L)
        for (now in 500L..5_000L step 500L) {
            clock.report(now, true, now)
            clock.tick(now + 250L)
            assertEquals(now + 250L, clock.positionMs)
        }
    }

    @Test
    fun `small backward reports hold until playback catches the shown position`() {
        val clock = LyricsPlaybackClock(1_000L, true, 0L)
        clock.tick(200L)
        clock.report(1_100L, true, 200L)
        assertEquals(1_200L, clock.positionMs)
        clock.tick(250L)
        assertEquals(1_200L, clock.positionMs)
        clock.tick(310L)
        assertEquals(1_210L, clock.positionMs)
    }

    @Test
    fun `300ms backward seek and forward seek immediately adopt reported position`() {
        val clock = LyricsPlaybackClock(1_000L, true, 0L)
        clock.tick(200L)
        clock.report(900L, true, 200L)
        assertEquals(900L, clock.positionMs)
        clock.report(4_000L, true, 200L)
        assertEquals(4_000L, clock.positionMs)
    }

    @Test
    fun `pause snaps even a small backward change and resume establishes a new anchor`() {
        val clock = LyricsPlaybackClock(1_000L, true, 0L)
        clock.tick(200L)
        clock.report(1_100L, false, 200L)
        clock.tick(900L)
        assertEquals(1_100L, clock.positionMs)
        assertFalse(clock.canExtrapolate(900L))
        clock.report(1_100L, true, 1_000L)
        clock.tick(1_250L)
        assertEquals(1_350L, clock.positionMs)
    }

    @Test
    fun `returning from browsing realigns without renewing the report time`() {
        val clock = LyricsPlaybackClock(1_000L, true, 0L)
        clock.setPresentationActive(true, 0L)
        clock.tick(200L)
        clock.setPresentationActive(false, 200L)
        clock.report(1_100L, true, 200L)
        clock.setPresentationActive(true, 210L)
        assertEquals(1_110L, clock.positionMs)
        clock.tick(900L)
        assertEquals(1_600L, clock.positionMs)
    }

    @Test
    fun `completed fill does not disable jitter protection when a report steps slightly back`() {
        val clock = LyricsPlaybackClock(1_000L, true, 0L)
        clock.setPresentationActive(true, 0L)
        clock.tick(500L)
        clock.report(1_600L, true, 600L)
        clock.report(1_400L, true, 700L)
        clock.setPresentationActive(true, 700L)
        assertEquals(1_600L, clock.positionMs)
    }

    @Test
    fun `frame gate requires visible playing word lyrics outside browsing`() {
        val line = EnhancedLyricsDisplayLine("word", 0L, segments = listOf(EnhancedLyricsSegment("word", 0L, 1_000L)))
        fun enabled(playing: Boolean = true, visible: Boolean = true, lifecycle: Boolean = true, browsing: Boolean = false) =
            shouldAnimateLyricsFill(line, 500L, playing, visible, lifecycle, browsing)
        assertTrue(enabled())
        assertFalse(enabled(playing = false))
        assertFalse(enabled(visible = false))
        assertFalse(enabled(lifecycle = false))
        assertFalse(enabled(browsing = true))
        assertFalse(shouldAnimateLyricsFill(line, 1_000L, true, true, true, false))
        assertFalse(shouldAnimateLyricsFill(line.copy(segments = emptyList()), 0L, true, true, true, false))
    }

    @Test
    fun `missing end completes at start and a future segment still needs frames`() {
        val line = EnhancedLyricsDisplayLine("ab", 0L, segments = listOf(
            EnhancedLyricsSegment("a", 0L), EnhancedLyricsSegment("b", 500L),
        ))
        assertTrue(hasUnfinishedLyricsSegments(line, 0L))
        assertFalse(hasUnfinishedLyricsSegments(line, 500L))
        assertFalse(hasUnfinishedLyricsSegments(null, 0L))
    }
}
