package top.iwesley.lyn.music

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import top.iwesley.lyn.music.core.model.GlobalDiagnosticLogger
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.debug
import kotlin.math.roundToInt

internal enum class LayoutOrientation {
    Portrait,
    Landscape,
}

/**
 * real device width or BoxWithConstraints
 */
internal data class LayoutProfile(
    val maxWidth: Dp,
    val maxHeight: Dp,
    val platform: PlatformDescriptor? = null,
    val density: Density? = null,
) {
    init {
        val orientationLabel =
            if (maxWidth > maxHeight) LayoutOrientation.Landscape else LayoutOrientation.Portrait
        val maxWidthPxLabel = density?.run { "${maxWidth.toPx().roundToInt()}px" }
        val maxHeightPxLabel = density?.run { "${maxHeight.toPx().roundToInt()}px" }
//        GlobalDiagnosticLogger.debug("LayoutProfile") {
//            "LayoutProfile init: platform=${platform?.name ?: "unknown"} " +
//                    "maxWidth=$maxWidth${maxWidthPxLabel?.let { " ($it)" } ?: ""} " +
//                    "maxHeight=$maxHeight${maxHeightPxLabel?.let { " ($it)" } ?: ""} " +
//                    "orientation=$orientationLabel"
//        }
    }
    val orientation: LayoutOrientation =
        if (maxWidth > maxHeight) LayoutOrientation.Landscape else LayoutOrientation.Portrait

    val isPortrait: Boolean
        get() = orientation == LayoutOrientation.Portrait

    val isLandscape: Boolean
        get() = orientation == LayoutOrientation.Landscape

    val isMobilePlatform: Boolean
        get() = platform?.isMobilePlatform() == true

    val isAndroidTV: Boolean = platform?.isAndroidTV() == true

    val isAndroidAuto: Boolean = platform?.isAndroidAutomotivePlatform() == true


//    val isCompactShell: Boolean
//        get() = maxWidth < COMPACT_SHELL_MIN_WIDTH

    /**
     * 桌面端永远走大屏布局
     * 手机不走大屏布局
     * 平板走大屏布局
     */
    val isExpandedLayout: Boolean
        get() = when {
            isAndroidTV -> isLandscape
            isAndroidAuto -> isLandscape
            isMobilePlatform -> min(maxWidth, maxHeight) >= 600.dp && isLandscape
            else -> true
        }

    val isCompactLayout: Boolean
        get() = !isExpandedLayout //手机横屏会返回true
        //get() = isMobilePlatform && isPortrait  手机横屏会返回false

    val isExpandedDevice: Boolean
        get() = !isMobilePlatform || min(maxWidth, maxHeight) >= 600.dp

//    val isWideLayout: Boolean
//        get() = maxWidth >= WIDE_LAYOUT_MIN_WIDTH
//
//    val usesStackedFields: Boolean
//        get() = maxWidth < STACKED_FIELDS_MAX_WIDTH
}

internal fun buildLayoutProfile(
    maxWidth: Dp,
    maxHeight: Dp,
    platform: PlatformDescriptor,
    density: Density? = null,
): LayoutProfile = LayoutProfile(
    maxWidth = maxWidth,
    maxHeight = maxHeight,
    platform = platform,
    density = density,
)

internal fun shouldUseAutomotiveLandscapePlayerOverlay(layoutProfile: LayoutProfile): Boolean {
    return layoutProfile.isAndroidAuto && layoutProfile.isLandscape
}


internal fun PlatformDescriptor.isPCPlatform(): Boolean {
    return name == "Desktop"
}

internal fun PlatformDescriptor.isMobilePlatform(): Boolean {
    return name == ANDROID_PLATFORM_NAME || name == IOS_PLATFORM_NAME
}

internal fun PlatformDescriptor.isAndroidPlatform(): Boolean {
    return name.contains("Android")
}

internal fun PlatformDescriptor.isAndroidTV(): Boolean {
    return name == ANDROID_TV_PLATFORM_NAME
}

internal fun PlatformDescriptor.isAndroidAutomotivePlatform(): Boolean {
    return name == ANDROID_AUTOMOTIVE_PLATFORM_NAME
}

private val COMPACT_SHELL_MIN_WIDTH = 900.dp
private val WIDE_LAYOUT_MIN_WIDTH = 980.dp
private val NARROW_ACTIONS_MAX_WIDTH = 760.dp
private val STACKED_FIELDS_MAX_WIDTH = 560.dp

const val ANDROID_PLATFORM_NAME = "Android"
const val ANDROID_AUTOMOTIVE_PLATFORM_NAME = "Android Automotive"
const val ANDROID_TV_PLATFORM_NAME = "Android TV"
const val IOS_PLATFORM_NAME = "iPhone / iPad"
