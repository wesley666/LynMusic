package top.iwesley.lyn.music.core.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ArtworkTintTheme(
    val rimColorArgb: Int,
    val glowColorArgb: Int,
    val innerGlowColorArgb: Int,
)

data class PlaybackArtworkBackgroundPalette(
    val baseColorArgb: Int,
    val primaryColorArgb: Int,
    val secondaryColorArgb: Int,
    val tertiaryColorArgb: Int,
)

fun deriveArtworkTintTheme(sampledPixels: Iterable<Int>): ArtworkTintTheme? {
    val binCount = 18
    val bins = Array(binCount) { ArgbColorAccumulator() }
    sampledPixels.forEach { argb ->
        val alpha = argbAlpha(argb)
        if (alpha < 0.35f) return@forEach
        val hsl = argbToHsl(argb)
        val saturation = hsl[1]
        val lightness = hsl[2]
        if (saturation < 0.16f) return@forEach
        if (lightness < 0.12f || lightness > 0.84f) return@forEach
        val weight = saturation * (1f - abs(lightness - 0.52f)).coerceAtLeast(0.18f)
        if (weight <= 0f) return@forEach
        val binIndex = (((hsl[0] / 360f) * binCount).toInt()).coerceIn(0, binCount - 1)
        bins[binIndex].add(argb, weight)
    }
    val dominant = bins.maxByOrNull { it.weight }?.takeIf { it.weight > 0f }?.averageColorArgb() ?: return null
    val hsl = argbToHsl(dominant)
    val safeHue = hsl[0]
    val safeSaturation = min(max(hsl[1], 0.24f), 0.58f)
    return ArtworkTintTheme(
        rimColorArgb = hslToArgb(safeHue, safeSaturation, 0.64f),
        glowColorArgb = hslToArgb(safeHue, safeSaturation, 0.42f),
        innerGlowColorArgb = hslToArgb(safeHue, safeSaturation * 0.82f, 0.56f),
    )
}

fun derivePlaybackArtworkBackgroundPalette(sampledPixels: Iterable<Int>): PlaybackArtworkBackgroundPalette? {
    val binCount = 24
    val bins = Array(binCount) { ArgbColorAccumulator() }
    val neutralAccumulator = NeutralPlaybackColorAccumulator()
    sampledPixels.forEach { argb ->
        val alpha = argbAlpha(argb)
        if (alpha < 0.35f) return@forEach
        val hsl = argbToHsl(argb)
        val saturation = hsl[1]
        val lightness = hsl[2]
        if (saturation < 0.14f) {
            neutralAccumulator.add(lightness)
            return@forEach
        }
        if (lightness < 0.10f || lightness > 0.88f) return@forEach
        val lightnessWeight = (1f - abs(lightness - 0.50f)).coerceAtLeast(0.12f)
        val weight = saturation * saturation * lightnessWeight
        if (weight <= 0f) return@forEach
        val binIndex = (((hsl[0] / 360f) * binCount).toInt()).coerceIn(0, binCount - 1)
        bins[binIndex].add(argb, weight)
    }

    val rankedColors = bins
        .filter { it.weight > 0f }
        .sortedByDescending { it.weight }
        .map { it.averageColorArgb() }
    val selectedColors = mutableListOf<Int>()
    for (color in rankedColors) {
        val hue = argbToHsl(color)[0]
        val distinct = selectedColors.all { existing ->
            hueDistance(hue, argbToHsl(existing)[0]) >= PLAYBACK_BACKGROUND_MIN_HUE_DISTANCE
        }
        if (distinct) {
            selectedColors += color
            if (selectedColors.size == PLAYBACK_BACKGROUND_COLOR_COUNT) break
        }
    }
    if (selectedColors.isEmpty()) return neutralAccumulator.toPaletteOrNull()

    val seedColor = selectedColors.first()
    while (selectedColors.size < PLAYBACK_BACKGROUND_COLOR_COUNT) {
        val hueOffset = if (selectedColors.size == 1) 38f else -44f
        selectedColors += derivePlaybackNeighborColorArgb(seedColor, hueOffset)
    }

    return PlaybackArtworkBackgroundPalette(
        baseColorArgb = tonePlaybackBackgroundColor(
            argb = seedColor,
            saturationScale = 0.46f,
            minSaturation = 0.16f,
            maxSaturation = 0.30f,
            lightness = 0.17f,
        ),
        primaryColorArgb = tonePlaybackBackgroundColor(
            argb = selectedColors[0],
            saturationScale = 0.86f,
            minSaturation = 0.24f,
            maxSaturation = 0.58f,
            lightness = 0.36f,
        ),
        secondaryColorArgb = tonePlaybackBackgroundColor(
            argb = selectedColors[1],
            saturationScale = 0.78f,
            minSaturation = 0.22f,
            maxSaturation = 0.54f,
            lightness = 0.32f,
        ),
        tertiaryColorArgb = tonePlaybackBackgroundColor(
            argb = selectedColors[2],
            saturationScale = 0.72f,
            minSaturation = 0.18f,
            maxSaturation = 0.48f,
            lightness = 0.28f,
        ),
    )
}

fun argbWithAlpha(argb: Int, alpha: Float): Int {
    val clampedAlpha = alpha.coerceIn(0f, 1f)
    return ((clampedAlpha * 255f).roundToInt().coerceIn(0, 255) shl 24) or (argb and 0x00FFFFFF)
}

private const val PLAYBACK_BACKGROUND_COLOR_COUNT = 3
private const val PLAYBACK_BACKGROUND_MIN_HUE_DISTANCE = 28f

private fun argbAlpha(argb: Int): Float = ((argb ushr 24) and 0xFF) / 255f

private class NeutralPlaybackColorAccumulator {
    private var weightedLightness = 0f
    private var weight = 0f

    fun add(lightness: Float) {
        if (lightness < 0.07f || lightness > 0.92f) return
        val itemWeight = (1f - abs(lightness - 0.48f)).coerceAtLeast(0.16f)
        weightedLightness += lightness * itemWeight
        weight += itemWeight
    }

    fun toPaletteOrNull(): PlaybackArtworkBackgroundPalette? {
        if (weight <= 0f) return null
        val averageLightness = (weightedLightness / weight).coerceIn(0.20f, 0.62f)
        val baseLightness = (averageLightness * 0.44f).coerceIn(0.16f, 0.22f)
        val primaryLightness = (averageLightness * 0.76f).coerceIn(0.30f, 0.42f)
        val secondaryLightness = (averageLightness * 0.68f).coerceIn(0.26f, 0.36f)
        val tertiaryLightness = (averageLightness * 0.58f).coerceIn(0.23f, 0.32f)
        return PlaybackArtworkBackgroundPalette(
            baseColorArgb = hslToArgb(214f, 0.16f, baseLightness),
            primaryColorArgb = hslToArgb(205f, 0.18f, primaryLightness),
            secondaryColorArgb = hslToArgb(168f, 0.12f, secondaryLightness),
            tertiaryColorArgb = hslToArgb(254f, 0.10f, tertiaryLightness),
        )
    }
}

private fun derivePlaybackNeighborColorArgb(seedArgb: Int, hueOffset: Float): Int {
    val hsl = argbToHsl(seedArgb)
    return hslToArgb(
        hue = hsl[0] + hueOffset,
        saturation = hsl[1].coerceIn(0.24f, 0.52f),
        lightness = hsl[2].coerceIn(0.30f, 0.58f),
    )
}

private fun tonePlaybackBackgroundColor(
    argb: Int,
    saturationScale: Float,
    minSaturation: Float,
    maxSaturation: Float,
    lightness: Float,
): Int {
    val hsl = argbToHsl(argb)
    return hslToArgb(
        hue = hsl[0],
        saturation = (hsl[1] * saturationScale).coerceIn(minSaturation, maxSaturation),
        lightness = lightness,
    )
}

private fun hueDistance(first: Float, second: Float): Float {
    val diff = abs(first - second) % 360f
    return min(diff, 360f - diff)
}

private fun argbToHsl(argb: Int): FloatArray {
    val red = ((argb ushr 16) and 0xFF) / 255f
    val green = ((argb ushr 8) and 0xFF) / 255f
    val blue = (argb and 0xFF) / 255f
    val max = max(red, max(green, blue))
    val min = min(red, min(green, blue))
    val delta = max - min
    val lightness = (max + min) / 2f
    if (delta == 0f) return floatArrayOf(0f, 0f, lightness)
    val saturation = delta / (1f - abs(2f * lightness - 1f)).coerceAtLeast(1e-6f)
    val hue = when (max) {
        red -> 60f * (((green - blue) / delta).modPositive(6f))
        green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }
    return floatArrayOf(hue, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))
}

private fun hslToArgb(hue: Float, saturation: Float, lightness: Float): Int {
    val clampedHue = ((hue % 360f) + 360f) % 360f
    val clampedSaturation = saturation.coerceIn(0f, 1f)
    val clampedLightness = lightness.coerceIn(0f, 1f)
    if (clampedSaturation <= 0f) {
        val channel = (clampedLightness * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel shl 16) or (channel shl 8) or channel
    }
    val chroma = (1f - abs(2f * clampedLightness - 1f)) * clampedSaturation
    val huePrime = clampedHue / 60f
    val second = chroma * (1f - abs((huePrime % 2f) - 1f))
    val (redPrime, greenPrime, bluePrime) = when {
        huePrime < 1f -> Triple(chroma, second, 0f)
        huePrime < 2f -> Triple(second, chroma, 0f)
        huePrime < 3f -> Triple(0f, chroma, second)
        huePrime < 4f -> Triple(0f, second, chroma)
        huePrime < 5f -> Triple(second, 0f, chroma)
        else -> Triple(chroma, 0f, second)
    }
    val match = clampedLightness - chroma / 2f
    val red = ((redPrime + match) * 255f).roundToInt().coerceIn(0, 255)
    val green = ((greenPrime + match) * 255f).roundToInt().coerceIn(0, 255)
    val blue = ((bluePrime + match) * 255f).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}

private fun Float.modPositive(divisor: Float): Float {
    val remainder = this % divisor
    return if (remainder < 0f) remainder + divisor else remainder
}

private class ArgbColorAccumulator {
    var red = 0f
    var green = 0f
    var blue = 0f
    var weight = 0f

    fun add(argb: Int, itemWeight: Float) {
        red += ((argb ushr 16) and 0xFF) * itemWeight
        green += ((argb ushr 8) and 0xFF) * itemWeight
        blue += (argb and 0xFF) * itemWeight
        weight += itemWeight
    }

    fun averageColorArgb(): Int {
        if (weight <= 0f) return 0
        val avgRed = (red / weight).roundToInt().coerceIn(0, 255)
        val avgGreen = (green / weight).roundToInt().coerceIn(0, 255)
        val avgBlue = (blue / weight).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (avgRed shl 16) or (avgGreen shl 8) or avgBlue
    }
}
