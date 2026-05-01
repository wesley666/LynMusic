package top.iwesley.lyn.music.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtworkCropTest {

    @Test
    fun `wide artwork crops to centered square`() {
        assertEquals(
            ArtworkSquareCropRect(left = 300, top = 0, size = 600),
            resolveArtworkSquareCropRect(sourceWidth = 1_200, sourceHeight = 600),
        )
    }

    @Test
    fun `tall artwork crops to centered square`() {
        assertEquals(
            ArtworkSquareCropRect(left = 0, top = 320, size = 640),
            resolveArtworkSquareCropRect(sourceWidth = 640, sourceHeight = 1_280),
        )
    }

    @Test
    fun `square artwork keeps full bounds`() {
        assertEquals(
            ArtworkSquareCropRect(left = 0, top = 0, size = 512),
            resolveArtworkSquareCropRect(sourceWidth = 512, sourceHeight = 512),
        )
    }

    @Test
    fun `invalid artwork dimensions do not resolve crop`() {
        assertNull(resolveArtworkSquareCropRect(sourceWidth = 0, sourceHeight = 512))
        assertNull(resolveArtworkSquareCropRect(sourceWidth = 512, sourceHeight = -1))
    }

    @Test
    fun `decode sample size keeps cropped dimension at least target size`() {
        assertEquals(
            8,
            resolveArtworkDecodeSampleSize(sourceWidth = 4_096, sourceHeight = 4_096, targetSize = 512),
        )
        assertEquals(
            4,
            resolveArtworkDecodeSampleSize(sourceWidth = 3_000, sourceHeight = 2_048, targetSize = 512),
        )
        assertEquals(
            1,
            resolveArtworkDecodeSampleSize(sourceWidth = 700, sourceHeight = 700, targetSize = 512),
        )
    }

    @Test
    fun `invalid decode sample inputs fall back to one`() {
        assertEquals(1, resolveArtworkDecodeSampleSize(sourceWidth = 0, sourceHeight = 512, targetSize = 512))
        assertEquals(1, resolveArtworkDecodeSampleSize(sourceWidth = 512, sourceHeight = 512, targetSize = 0))
    }
}
