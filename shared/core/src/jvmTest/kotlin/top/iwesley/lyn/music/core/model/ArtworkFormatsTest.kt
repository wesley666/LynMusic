package top.iwesley.lyn.music.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtworkFormatsTest {

    @Test
    fun `complete artwork payload accepts jpeg with end marker`() {
        assertTrue(
            isCompleteArtworkPayload(
                byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0xFF.toByte(), 0xD9.toByte()),
            ),
        )
    }

    @Test
    fun `complete artwork payload rejects truncated jpeg`() {
        assertFalse(
            isCompleteArtworkPayload(
                byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00),
            ),
        )
    }

    @Test
    fun `complete artwork payload accepts png with iend marker`() {
        assertTrue(
            isCompleteArtworkPayload(
                byteArrayOf(
                    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                    0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
                    0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
                ),
            ),
        )
    }

    @Test
    fun `complete artwork payload rejects truncated png`() {
        assertFalse(
            isCompleteArtworkPayload(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
            ),
        )
    }

    @Test
    fun `complete artwork payload validates webp declared length`() {
        assertTrue(
            isCompleteArtworkPayload(
                byteArrayOf(
                    0x52, 0x49, 0x46, 0x46,
                    0x04, 0x00, 0x00, 0x00,
                    0x57, 0x45, 0x42, 0x50,
                ),
            ),
        )
        assertFalse(
            isCompleteArtworkPayload(
                byteArrayOf(
                    0x52, 0x49, 0x46, 0x46,
                    0x10, 0x00, 0x00, 0x00,
                    0x57, 0x45, 0x42, 0x50,
                ),
            ),
        )
    }

    @Test
    fun `complete artwork payload rejects unknown bytes`() {
        assertFalse(isCompleteArtworkPayload(byteArrayOf(1, 2, 3, 4)))
    }
}
