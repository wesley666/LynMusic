package top.iwesley.lyn.music.data.db

import kotlin.test.Test
import kotlin.test.assertEquals

class ImportSourceSelectedDirectoriesTest {
    @Test
    fun `no selection is stored blank so rows read like those from before folder selection`() {
        assertEquals("", encodeSelectedDirectories(emptyList()))
        assertEquals(emptyList(), decodeSelectedDirectories(""))
    }

    @Test
    fun `selected folders round trip normalized`() {
        val encoded = encodeSelectedDirectories(listOf("Media/Music/", "Backup", "Media/Music/Live"))
        assertEquals(listOf("Backup", "Media/Music"), decodeSelectedDirectories(encoded))
    }

    @Test
    fun `unreadable values fall back to the whole root`() {
        assertEquals(emptyList(), decodeSelectedDirectories("not json"))
    }
}
