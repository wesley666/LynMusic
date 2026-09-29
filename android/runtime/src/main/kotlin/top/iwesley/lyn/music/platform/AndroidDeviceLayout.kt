package top.iwesley.lyn.music.platform

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.roundToInt
import kotlin.math.min

/** Uses display size rather than a split-screen window or the user's display-size setting. */
@Suppress("DEPRECATION")
fun Context.isAndroidTabletIgnoringDisplaySize(): Boolean {
    val metrics = resources.displayMetrics
    val mode = (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.mode
    val width = mode?.physicalWidth?.takeIf { it > 0 } ?: metrics.widthPixels
    val height = mode?.physicalHeight?.takeIf { it > 0 } ?: metrics.heightPixels
    return isAndroidTabletDisplay(width, height, androidStableDensityScale(metrics.density) * 160f)
}

internal fun isAndroidTabletDisplay(width: Int, height: Int, stableDpi: Float): Boolean {
    if (width <= 0 || height <= 0 || !stableDpi.isFinite() || stableDpi <= 0f) return false
    return min(width, height) / (stableDpi / 160f) >= 600f
}

fun androidStableDensityScale(fallbackDensity: Float): Float {
    val fallbackDpi = (fallbackDensity.takeIf { it > 0f } ?: 1f) * DisplayMetrics.DENSITY_DEFAULT
    val stableDpi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        DisplayMetrics.DENSITY_DEVICE_STABLE
    } else {
        fallbackDpi.roundToInt()
    }.takeIf { it > 0 } ?: fallbackDpi.roundToInt()
    return stableDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()
}
