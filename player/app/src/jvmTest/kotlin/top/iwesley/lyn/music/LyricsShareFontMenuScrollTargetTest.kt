package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricsShareFontMenuScrollTargetTest {
    @Test
    fun `middle item is positioned near menu center`() {
        val offset = calculateLyricsShareFontMenuScrollOffsetPx(
            selectedIndex = 5,
            itemCount = 12,
            itemHeightPx = 56,
            menuMaxHeightPx = 320,
        )

        assertEquals(148, offset)
    }

    @Test
    fun `first item clamps to top`() {
        val offset = calculateLyricsShareFontMenuScrollOffsetPx(
            selectedIndex = 0,
            itemCount = 12,
            itemHeightPx = 56,
            menuMaxHeightPx = 320,
        )

        assertEquals(0, offset)
    }

    @Test
    fun `last item clamps to bottom`() {
        val offset = calculateLyricsShareFontMenuScrollOffsetPx(
            selectedIndex = 11,
            itemCount = 12,
            itemHeightPx = 56,
            menuMaxHeightPx = 320,
        )

        assertEquals(352, offset)
    }

    @Test
    fun `short list keeps zero scroll offset`() {
        val offset = calculateLyricsShareFontMenuScrollOffsetPx(
            selectedIndex = 2,
            itemCount = 4,
            itemHeightPx = 56,
            menuMaxHeightPx = 320,
        )

        assertEquals(0, offset)
    }

    @Test
    fun `missing selection keeps zero scroll offset`() {
        val offset = calculateLyricsShareFontMenuScrollOffsetPx(
            selectedIndex = -1,
            itemCount = 12,
            itemHeightPx = 56,
            menuMaxHeightPx = 320,
        )

        assertEquals(0, offset)
    }

    @Test
    fun `scrollbar stays hidden when list cannot scroll`() {
        val metrics = calculateLyricsShareFontMenuScrollbarMetrics(
            scrollValue = 0,
            maxScrollValue = 0,
            trackHeightPx = 320,
        )

        assertFalse(metrics.isVisible)
        assertEquals(0f, metrics.thumbHeightPx)
        assertEquals(0f, metrics.thumbOffsetPx)
    }

    @Test
    fun `scrollbar metrics reflect current scroll position`() {
        val metrics = calculateLyricsShareFontMenuScrollbarMetrics(
            scrollValue = 140,
            maxScrollValue = 280,
            trackHeightPx = 320,
        )

        assertTrue(metrics.isVisible)
        assertEquals(144f, metrics.thumbHeightPx)
        assertEquals(88f, metrics.thumbOffsetPx)
    }

    @Test
    fun `scrollbar thumb clamps near bottom when fully scrolled`() {
        val metrics = calculateLyricsShareFontMenuScrollbarMetrics(
            scrollValue = 280,
            maxScrollValue = 280,
            trackHeightPx = 320,
        )

        assertTrue(metrics.isVisible)
        assertEquals(176f, metrics.thumbOffsetPx)
    }

    @Test
    fun `track click maps to proportional scroll value`() {
        val scrollValue = calculateLyricsShareFontMenuScrollbarTargetScrollValue(
            pointerY = 80f,
            trackHeightPx = 320,
            maxScrollValue = 280,
        )

        assertEquals(70, scrollValue)
    }

    @Test
    fun `track drag clamps to scroll range`() {
        val top = calculateLyricsShareFontMenuScrollbarTargetScrollValue(
            pointerY = -40f,
            trackHeightPx = 320,
            maxScrollValue = 280,
        )
        val bottom = calculateLyricsShareFontMenuScrollbarTargetScrollValue(
            pointerY = 480f,
            trackHeightPx = 320,
            maxScrollValue = 280,
        )

        assertEquals(0, top)
        assertEquals(280, bottom)
    }
}
