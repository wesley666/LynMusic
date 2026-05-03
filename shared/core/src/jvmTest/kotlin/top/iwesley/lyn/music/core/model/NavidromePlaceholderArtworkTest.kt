package top.iwesley.lyn.music.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavidromePlaceholderArtworkTest {

    @Test
    fun `sha256 hex matches known vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "abc".encodeToByteArray().sha256Hex(),
        )
    }

    @Test
    fun `webp with known placeholder sha is replaceable`() {
        assertTrue(
            isReplaceableNavidromePlaceholderArtwork(
                bytes = completeWebpPayload(),
                differenceHash = null,
                sha256Hex = NAVIDROME_PLACEHOLDER_ARTWORK_SHA256,
            ),
        )
    }

    @Test
    fun `webp with similar placeholder dhash is replaceable`() {
        assertTrue(
            isReplaceableNavidromePlaceholderArtwork(
                bytes = completeWebpPayload(),
                differenceHash = 0x0c1377615911370dUL,
                sha256Hex = "not-the-sample-hash",
            ),
        )
    }

    @Test
    fun `small webp without known hash or similar dhash is not replaceable`() {
        assertFalse(
            isReplaceableNavidromePlaceholderArtwork(
                bytes = completeWebpPayload(),
                differenceHash = 0xffffffffffffffffUL,
                sha256Hex = "not-the-sample-hash",
            ),
        )
    }

    @Test
    fun `small webp is not replaceable when decode hash is unavailable and sha does not match`() {
        assertFalse(
            isReplaceableNavidromePlaceholderArtwork(
                bytes = completeWebpPayload(),
                differenceHash = null,
                sha256Hex = "not-the-sample-hash",
            ),
        )
    }

    @Test
    fun `non webp and large webp are not replaceable`() {
        assertFalse(
            isReplaceableNavidromePlaceholderArtwork(
                bytes = byteArrayOf(0x01, 0x02),
                differenceHash = 0x0c1377615911370cUL,
                sha256Hex = NAVIDROME_PLACEHOLDER_ARTWORK_SHA256,
            ),
        )
        assertFalse(
            isReplaceableNavidromePlaceholderArtwork(
                bytes = ByteArray(NAVIDROME_PLACEHOLDER_ARTWORK_MAX_BYTES) { 0x00 }.also { bytes ->
                    completeWebpPayload().copyInto(bytes)
                },
                differenceHash = 0x0c1377615911370cUL,
                sha256Hex = NAVIDROME_PLACEHOLDER_ARTWORK_SHA256,
            ),
        )
    }

    @Test
    fun `difference hash is built from 9 by 8 luminance grid`() {
        val luminance = IntArray(9 * 8) { index ->
            val x = index % 9
            if (x % 2 == 0) 255 else 0
        }

        assertEquals(
            0xaaaaaaaaaaaaaaaaUL,
            navidromeArtworkDifferenceHash(luminance),
        )
    }

    @Test
    fun `hamming distance counts changed bits`() {
        assertEquals(0, hammingDistance(0x0fUL, 0x0fUL))
        assertEquals(8, hammingDistance(0x0fUL, 0xf0UL))
    }
}

private fun completeWebpPayload(): ByteArray {
    return byteArrayOf(
        0x52,
        0x49,
        0x46,
        0x46,
        0x04,
        0x00,
        0x00,
        0x00,
        0x57,
        0x45,
        0x42,
        0x50,
    )
}
