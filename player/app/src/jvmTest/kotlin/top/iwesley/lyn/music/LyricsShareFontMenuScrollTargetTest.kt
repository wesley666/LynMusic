package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import kotlin.test.Test
import kotlin.test.assertContentEquals
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

    @Test
    fun `index entries keep first occurrence order`() {
        val entries = buildLyricsShareFontMenuIndexEntries(
            listOf(
                LyricsShareFontOption("Avenir Next"),
                LyricsShareFontOption("Arial"),
                LyricsShareFontOption("Baskerville"),
                LyricsShareFontOption("Bodoni 72"),
                LyricsShareFontOption("Courier New"),
            )
        )

        assertContentEquals(
            listOf("A", "B", "C"),
            entries.map { it.label },
        )
    }

    @Test
    fun `non latin leading font maps to hash group`() {
        val entries = buildLyricsShareFontMenuIndexEntries(
            listOf(
                LyricsShareFontOption("你好字体"),
                LyricsShareFontOption(".Apple Symbols"),
                LyricsShareFontOption("Avenir Next"),
            )
        )

        assertContentEquals(
            listOf("#", "A"),
            entries.map { it.label },
        )
    }

    @Test
    fun `entry firstIndex points to first matching font`() {
        val entries = buildLyricsShareFontMenuIndexEntries(
            listOf(
                LyricsShareFontOption("Avenir Next"),
                LyricsShareFontOption("Arial"),
                LyricsShareFontOption("Baskerville"),
                LyricsShareFontOption("Courier New"),
            )
        )

        assertEquals(0, entries[0].firstIndex)
        assertEquals(2, entries[1].firstIndex)
        assertEquals(3, entries[2].firstIndex)
    }

    @Test
    fun `pointer at top maps to first index entry`() {
        val targetIndex = calculateLyricsShareFontMenuIndexTarget(
            pointerY = 0f,
            trackHeightPx = 320,
            entryCount = 4,
        )

        assertEquals(0, targetIndex)
    }

    @Test
    fun `pointer at bottom maps to last index entry`() {
        val targetIndex = calculateLyricsShareFontMenuIndexTarget(
            pointerY = 320f,
            trackHeightPx = 320,
            entryCount = 4,
        )

        assertEquals(3, targetIndex)
    }

    @Test
    fun `middle pointer maps to middle entry`() {
        val targetIndex = calculateLyricsShareFontMenuIndexTarget(
            pointerY = 150f,
            trackHeightPx = 320,
            entryCount = 4,
        )

        assertEquals(1, targetIndex)
    }

    @Test
    fun `out of range pointer clamps to valid entry`() {
        val top = calculateLyricsShareFontMenuIndexTarget(
            pointerY = -40f,
            trackHeightPx = 320,
            entryCount = 4,
        )
        val bottom = calculateLyricsShareFontMenuIndexTarget(
            pointerY = 480f,
            trackHeightPx = 320,
            entryCount = 4,
        )

        assertEquals(0, top)
        assertEquals(3, bottom)
    }

    @Test
    fun `invalid track height keeps target missing`() {
        val targetIndex = calculateLyricsShareFontMenuIndexTarget(
            pointerY = 40f,
            trackHeightPx = 0,
            entryCount = 4,
        )

        assertEquals(-1, targetIndex)
    }
}
