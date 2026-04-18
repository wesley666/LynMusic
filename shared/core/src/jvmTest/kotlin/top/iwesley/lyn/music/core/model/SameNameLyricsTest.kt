package top.iwesley.lyn.music.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SameNameLyricsTest {
    @Test
    fun `same name lyrics file name replaces final extension with lrc`() {
        assertEquals("aaa.lrc", sameNameLyricsFileName("aaa.mp3"))
        assertEquals("aaa.lrc", sameNameLyricsFileName("aaa.flac"))
        assertEquals("aaa.lrc", sameNameLyricsFileName("aaa"))
    }

    @Test
    fun `same name lyrics relative path keeps original directory`() {
        assertEquals("Artist/Album/aaa.lrc", sameNameLyricsRelativePath("Artist/Album/aaa.mp3"))
        assertEquals("Artist/Album/aaa.lrc", sameNameLyricsRelativePath("/Artist\\Album\\aaa.flac"))
        assertEquals("aaa.lrc", sameNameLyricsRelativePath("aaa.mp3"))
    }

    @Test
    fun `same name lyrics relative path rejects blank or extension only names`() {
        assertNull(sameNameLyricsRelativePath(""))
        assertNull(sameNameLyricsRelativePath(".mp3"))
        assertNull(sameNameLyricsFileName("   "))
    }
}
