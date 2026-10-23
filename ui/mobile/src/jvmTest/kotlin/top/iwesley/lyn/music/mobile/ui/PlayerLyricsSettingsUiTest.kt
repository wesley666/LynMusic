package top.iwesley.lyn.music.mobile.ui

import top.iwesley.lyn.music.*

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlin.test.*
import java.nio.file.Files
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.platform.lyricsSharePreviewFontFamily

class PlayerLyricsSettingsUiTest {
    @Test fun `corrupt imported font falls back before text layout`() {
        val file = Files.createTempFile("invalid-lyrics-font", ".ttf").toFile()
        try {
            file.writeText("not a font")
            assertNull(lyricsSharePreviewFontFamily("imported:broken", "Broken", file.absolutePath))
        } finally { file.delete() }
    }

    @Test fun `timing buttons preserve old precision and clamp at both limits`() {
        assertEquals(100L, stepPlayerLyricsOffset(0, earlier = true))
        assertEquals(-100L, stepPlayerLyricsOffset(0, earlier = false))
        assertEquals(225L, stepPlayerLyricsOffset(125, earlier = true))
        assertEquals(25L, stepPlayerLyricsOffset(125, earlier = false))
        assertEquals(30_000L, stepPlayerLyricsOffset(29_950, earlier = true))
        assertEquals(-30_000L, stepPlayerLyricsOffset(-29_950, earlier = false))
        assertEquals(30_000L, stepPlayerLyricsOffset(30_000, earlier = true))
        assertEquals(-30_000L, stepPlayerLyricsOffset(-30_000, earlier = false))
        assertEquals("0", formatPlayerLyricsOffsetMagnitude(0))
        assertEquals("0.125", formatPlayerLyricsOffsetMagnitude(125))
        assertEquals("0.05", formatPlayerLyricsOffsetMagnitude(-50))
        assertEquals("30", formatPlayerLyricsOffsetMagnitude(-30_000))
    }

    @Test fun `size buttons traverse all five percent steps and stop at limits`() {
        var value = 0.75f
        repeat(20) { value = stepPlayerLyricsFontScale(value, increase = true) }
        assertEquals(1.75f, value)
        assertEquals(1.75f, stepPlayerLyricsFontScale(value, increase = true))
        repeat(20) { value = stepPlayerLyricsFontScale(value, increase = false) }
        assertEquals(0.75f, value)
        assertEquals(0.75f, stepPlayerLyricsFontScale(value, increase = false))
        assertEquals(1.2f, stepPlayerLyricsFontScale(1.15f, increase = true))
    }

    @Test fun `glass aligns windows by screen position and defers unknown coordinates`() {
        assertEquals(Offset(-25f, -500f), lyricsGlassSourceOffset(Offset(100f, 50f), Offset(125f, 550f)))
        assertEquals(Offset(-25f, -500f), lyricsGlassSourceOffset(Offset(400f, 350f), Offset(425f, 850f)))
        assertNull(lyricsGlassSourceOffset(Offset.Unspecified, Offset.Zero))
        assertNull(lyricsGlassSourceOffset(Offset.Zero, Offset.Unspecified))
    }

    @Test fun `font scaling preserves line-height hierarchy and default typography`() {
        val original = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontFamily = FontFamily.SansSerif)
        assertEquals(original, original.withPlayerLyricsStyle(PlayerLyricsStyle(), null))
        val changed = original.withPlayerLyricsStyle(PlayerLyricsStyle(fontScale = 1.75f), FontFamily.Monospace)
        assertEquals(35.sp, changed.fontSize)
        assertEquals(49.sp, changed.lineHeight)
        assertEquals(FontFamily.Monospace, changed.fontFamily)
    }

    @Test fun `stored style rejects nonimported fonts and corrupt scale`() {
        assertNull(PlayerLyricsStyle(fontKey = "Serif").normalized().fontKey)
        assertEquals(1f, PlayerLyricsStyle(fontScale = Float.NaN).normalized().fontScale)
        val value = PlayerLyricsStyle(0xffabc123.toInt(), 1.25f, "imported:abc")
        assertEquals(value, decodePlayerLyricsStyle(encodePlayerLyricsStyle(value)))
        assertEquals(PlayerLyricsStyle(), decodePlayerLyricsStyle(null))
    }
}
