package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
