package top.iwesley.lyn.music

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.PlatformDescriptor

internal enum class LayoutOrientation {
    Portrait,
    Landscape,
}

internal data class LayoutProfile(
    val maxWidth: Dp,
    val maxHeight: Dp,
    val platform: PlatformDescriptor? = null,
) {
    val orientation: LayoutOrientation =
        if (maxWidth > maxHeight) LayoutOrientation.Landscape else LayoutOrientation.Portrait

    val isPortrait: Boolean
        get() = orientation == LayoutOrientation.Portrait

    val isLandscape: Boolean
        get() = orientation == LayoutOrientation.Landscape

    val isMobilePlatform: Boolean
        get() = platform?.isMobilePlatform() == true

    val isCompactShell: Boolean
        get() = maxWidth < COMPACT_SHELL_MIN_WIDTH

    val isDesktopLayout: Boolean
        get() = !isCompactShell

    val isWideLayout: Boolean
        get() = maxWidth >= WIDE_LAYOUT_MIN_WIDTH

    val usesNarrowActionLayout: Boolean
        get() = maxWidth < NARROW_ACTIONS_MAX_WIDTH

    val usesStackedFields: Boolean
        get() = maxWidth < STACKED_FIELDS_MAX_WIDTH

    val usesPortraitMiniPlayer: Boolean
        get() = isCompactShell && isPortrait

    val usesTapToRevealPlaybackLyrics: Boolean
        get() = isMobilePlatform && isPortrait
}

internal fun buildLayoutProfile(
    maxWidth: Dp,
    maxHeight: Dp,
    platform: PlatformDescriptor? = null,
): LayoutProfile = LayoutProfile(maxWidth = maxWidth, maxHeight = maxHeight, platform = platform)

internal fun PlatformDescriptor.isMobilePlatform(): Boolean {
    return name == ANDROID_PLATFORM_NAME || name == IOS_PLATFORM_NAME
}

private val COMPACT_SHELL_MIN_WIDTH = 900.dp
private val WIDE_LAYOUT_MIN_WIDTH = 980.dp
private val NARROW_ACTIONS_MAX_WIDTH = 760.dp
private val STACKED_FIELDS_MAX_WIDTH = 560.dp

private const val ANDROID_PLATFORM_NAME = "Android"
private const val IOS_PLATFORM_NAME = "iPhone / iPad"
