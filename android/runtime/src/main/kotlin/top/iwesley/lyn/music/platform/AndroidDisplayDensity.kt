package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.effectiveAppDisplayDensity

fun androidAppDisplayDensity(
    systemDensity: Float,
    preset: AppDisplayScalePreset,
): Float {
    val baseDensity = systemDensity.takeIf { it.isFinite() && it > 0f } ?: 1f
    return effectiveAppDisplayDensity(baseDensity, preset)
}
