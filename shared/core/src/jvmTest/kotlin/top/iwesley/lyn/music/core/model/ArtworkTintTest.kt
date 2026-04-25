package top.iwesley.lyn.music.core.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtworkTintTest {

    @Test
    fun `playback background palette extracts distinct colored accents`() {
        val palette = assertNotNull(
            derivePlaybackArtworkBackgroundPalette(
                buildList {
                    repeat(32) { add(opaqueColor(red = 16, green = 154, blue = 216)) }
                    repeat(28) { add(opaqueColor(red = 220, green = 104, blue = 42)) }
                    repeat(24) { add(opaqueColor(red = 68, green = 174, blue = 104)) }
                    repeat(12) { add(opaqueColor(red = 238, green = 238, blue = 238)) }
                },
            ),
        )

        assertNotEquals(palette.primaryColorArgb, palette.secondaryColorArgb)
        assertNotEquals(palette.primaryColorArgb, palette.tertiaryColorArgb)
        assertTrue(hueDistance(colorHue(palette.primaryColorArgb), colorHue(palette.secondaryColorArgb)) >= 20f)
        assertTrue(colorLightness(palette.baseColorArgb) < colorLightness(palette.primaryColorArgb))
    }

    @Test
    fun `playback background palette derives neutral accents for grayscale artwork`() {
        val palette = assertNotNull(
            derivePlaybackArtworkBackgroundPalette(
                listOf(
                    opaqueColor(red = 34, green = 34, blue = 34),
                    opaqueColor(red = 86, green = 86, blue = 86),
                    opaqueColor(red = 126, green = 126, blue = 126),
                    opaqueColor(red = 200, green = 200, blue = 200),
                ),
            ),
        )

        assertTrue(colorLightness(palette.baseColorArgb) >= 0.15f)
        assertTrue(colorLightness(palette.primaryColorArgb) > colorLightness(palette.baseColorArgb))
        assertNotEquals(palette.primaryColorArgb, palette.secondaryColorArgb)
    }

    @Test
    fun `playback background palette ignores only extreme grayscale artwork`() {
        assertNull(
            derivePlaybackArtworkBackgroundPalette(
                listOf(
                    opaqueColor(red = 255, green = 255, blue = 255),
                    opaqueColor(red = 2, green = 2, blue = 2),
                ),
            ),
        )
    }

    @Test
    fun `playback background palette derives companion colors for single hue artwork`() {
        val palette = assertNotNull(
            derivePlaybackArtworkBackgroundPalette(
                List(80) { opaqueColor(red = 218, green = 44, blue = 66) },
            ),
        )

        assertNotEquals(palette.primaryColorArgb, palette.secondaryColorArgb)
        assertNotEquals(palette.secondaryColorArgb, palette.tertiaryColorArgb)
        assertTrue(colorLightness(palette.baseColorArgb) <= 0.22f)
    }
}

private fun opaqueColor(red: Int, green: Int, blue: Int): Int {
    return (0xFF shl 24) or
        (red.coerceIn(0, 255) shl 16) or
        (green.coerceIn(0, 255) shl 8) or
        blue.coerceIn(0, 255)
}

private fun colorHue(argb: Int): Float {
    val red = ((argb ushr 16) and 0xFF) / 255f
    val green = ((argb ushr 8) and 0xFF) / 255f
    val blue = (argb and 0xFF) / 255f
    val maxChannel = max(red, max(green, blue))
    val minChannel = min(red, min(green, blue))
    val delta = maxChannel - minChannel
    if (delta == 0f) return 0f
    return when (maxChannel) {
        red -> 60f * (((green - blue) / delta).positiveModulo(6f))
        green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }
}

private fun colorLightness(argb: Int): Float {
    val red = ((argb ushr 16) and 0xFF) / 255f
    val green = ((argb ushr 8) and 0xFF) / 255f
    val blue = (argb and 0xFF) / 255f
    return (max(red, max(green, blue)) + min(red, min(green, blue))) / 2f
}

private fun hueDistance(first: Float, second: Float): Float {
    val diff = abs(first - second) % 360f
    return min(diff, 360f - diff)
}

private fun Float.positiveModulo(divisor: Float): Float {
    val remainder = this % divisor
    return if (remainder < 0f) remainder + divisor else remainder
}
