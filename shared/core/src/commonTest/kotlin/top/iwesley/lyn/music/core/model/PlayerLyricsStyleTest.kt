package top.iwesley.lyn.music.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerLyricsStyleTest {
    @Test
    fun `translation defaults on for new old and invalid preferences`() {
        assertTrue(PlayerLyricsStyle().showTranslation)
        for (value in listOf(null, "-1|1.25|imported:abc", "-1|1.25|imported:abc|", "-1|1.25|imported:abc|invalid")) {
            assertTrue(decodePlayerLyricsStyle(value).showTranslation, value)
        }
        val legacy = decodePlayerLyricsStyle("-1|1.25|imported:abc")
        assertEquals(1.25f, legacy.fontScale)
        assertEquals("imported:abc", legacy.fontKey)
    }

    @Test
    fun `translation preference survives normalization and round trip`() {
        for (showTranslation in listOf(true, false)) {
            val style = PlayerLyricsStyle(0xffabc123.toInt(), 1.25f, "imported:abc", showTranslation)
            assertEquals(style, decodePlayerLyricsStyle(encodePlayerLyricsStyle(style)))
        }
        assertFalse(PlayerLyricsStyle(fontScale = Float.NaN, showTranslation = false).normalized().showTranslation)
    }
}
