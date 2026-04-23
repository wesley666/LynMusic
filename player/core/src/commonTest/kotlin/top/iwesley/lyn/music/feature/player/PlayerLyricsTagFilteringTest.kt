package top.iwesley.lyn.music.feature.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerLyricsTagFilteringTest {
    @Test
    fun `recognizes whitelisted structure tags`() {
        assertTrue(isPlayerLyricsStructureTagLine("[Verse]"))
        assertTrue(isPlayerLyricsStructureTagLine(" [Chorus 2] "))
        assertTrue(isPlayerLyricsStructureTagLine("[Pre-Chorus]"))
        assertTrue(isPlayerLyricsStructureTagLine("[Instrumental 3]"))
    }

    @Test
    fun `does not match normal lyrics or unsupported bracket content`() {
        assertFalse(isPlayerLyricsStructureTagLine("第一句 [Verse]"))
        assertFalse(isPlayerLyricsStructureTagLine("[Verse: Jay]"))
        assertFalse(isPlayerLyricsStructureTagLine("[00:12.00]第一句"))
        assertFalse(isPlayerLyricsStructureTagLine("第一句"))
    }
}
