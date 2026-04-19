package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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
    fun `index entries add favorites entry and ignore prioritized letters`() {
        val entries = buildLyricsShareFontMenuIndexEntries(
            listOf(
                LyricsShareFontOption(fontKey = "PingFang SC", displayName = "PingFang SC", isPrioritized = true),
                LyricsShareFontOption(fontKey = "Baskerville", displayName = "Baskerville", isPrioritized = true),
                LyricsShareFontOption(fontKey = "Courier New", displayName = "Courier New"),
                LyricsShareFontOption(fontKey = "Arial", displayName = "Arial"),
                LyricsShareFontOption(fontKey = "你好字体", displayName = "你好字体"),
            )
        )

        assertContentEquals(
            listOf("★", "A", "C", "#"),
            entries.map { it.label },
        )
        assertEquals(0, entries.first().firstIndex)
        assertTrue(isLyricsShareFontFavoritesIndexEntry(entries.first()))
    }

    @Test
    fun `non latin leading font maps to hash group after letters`() {
        val entries = buildLyricsShareFontMenuIndexEntries(
            listOf(
                LyricsShareFontOption(fontKey = "你好字体", displayName = "你好字体"),
                LyricsShareFontOption(fontKey = ".Apple Symbols", displayName = ".Apple Symbols"),
                LyricsShareFontOption(fontKey = "Avenir Next", displayName = "Avenir Next"),
            )
        )

        assertContentEquals(
            listOf("A", "#"),
            entries.map { it.label },
        )
    }

    @Test
    fun `index entries skip favorites entry when nothing is prioritized`() {
        val entries = buildLyricsShareFontMenuIndexEntries(
            listOf(
                LyricsShareFontOption(fontKey = "Avenir Next", displayName = "Avenir Next"),
                LyricsShareFontOption(fontKey = "Courier New", displayName = "Courier New"),
            )
        )

        assertContentEquals(
            listOf("A", "C"),
            entries.map { it.label },
        )
    }

    @Test
    fun `entry firstIndex points to first matching non prioritized font in displayed list`() {
        val entries = buildLyricsShareFontMenuIndexEntries(
            listOf(
                LyricsShareFontOption(fontKey = "Baskerville", displayName = "Baskerville", isPrioritized = true),
                LyricsShareFontOption(fontKey = "PingFang SC", displayName = "PingFang SC", isPrioritized = true),
                LyricsShareFontOption(fontKey = "Arial", displayName = "Arial"),
                LyricsShareFontOption(fontKey = "Avenir Next", displayName = "Avenir Next"),
                LyricsShareFontOption(fontKey = "Courier New", displayName = "Courier New"),
            )
        )

        assertContentEquals(
            listOf("★", "A", "C"),
            entries.map { it.label },
        )
        assertEquals(0, entries[0].firstIndex)
        assertEquals(2, entries[1].firstIndex)
        assertEquals(4, entries[2].firstIndex)
    }

    @Test
    fun `prioritized letter still appears when regular section has same letter`() {
        val entries = buildLyricsShareFontMenuIndexEntries(
            listOf(
                LyricsShareFontOption(fontKey = "PingFang SC", displayName = "PingFang SC", isPrioritized = true),
                LyricsShareFontOption(fontKey = "Arial", displayName = "Arial"),
                LyricsShareFontOption(fontKey = "Papyrus", displayName = "Papyrus"),
            )
        )

        assertContentEquals(
            listOf("★", "A", "P"),
            entries.map { it.label },
        )
        assertEquals(2, entries.last().firstIndex)
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

    @Test
    fun `button label uses imported font display name before list loads`() {
        val label = buildLyricsShareFontButtonLabel(
            selectedFontKey = "imported:abcdef123456",
            selectedFontDisplayName = "霞鹜文楷",
            availableFonts = emptyList(),
        )

        assertEquals("字体 · 霞鹜文楷", label)
    }

    @Test
    fun `button label falls back to default instead of imported font hash when name is missing`() {
        val label = buildLyricsShareFontButtonLabel(
            selectedFontKey = "imported:abcdef123456",
            availableFonts = emptyList(),
        )

        assertEquals("字体 · Serif", label)
    }

    @Test
    fun `button label prefers latest imported font display name after list loads`() {
        val label = buildLyricsShareFontButtonLabel(
            selectedFontKey = "imported:abcdef123456",
            selectedFontDisplayName = "旧名字",
            availableFonts = listOf(
                LyricsShareFontOption(
                    fontKey = "imported:abcdef123456",
                    displayName = "新名字",
                ),
            ),
        )

        assertEquals("字体 · 新名字", label)
    }
}
