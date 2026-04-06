package top.iwesley.lyn.music.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class MetadataCharsetTest {

    @Test
    fun `latin1 bytes decode correctly`() {
        val bytes = byteArrayOf(0x48, 0xE9.toByte(), 0x6C, 0x6C, 0x6F)
        assertEquals("Héllo", decodeMetadataBytes(bytes, MetadataCharset.LATIN1))
    }

    @Test
    fun `utf16 bytes honor little endian bom`() {
        val bytes = byteArrayOf(
            0xFF.toByte(), 0xFE.toByte(),
            0x48, 0x00,
            0x69, 0x00,
        )
        assertEquals("Hi", decodeMetadataBytes(bytes, MetadataCharset.UTF16))
    }

    @Test
    fun `utf16be bytes decode correctly`() {
        val bytes = byteArrayOf(
            0x00, 0x48,
            0x00, 0x69,
        )
        assertEquals("Hi", decodeMetadataBytes(bytes, MetadataCharset.UTF16BE))
    }
}
