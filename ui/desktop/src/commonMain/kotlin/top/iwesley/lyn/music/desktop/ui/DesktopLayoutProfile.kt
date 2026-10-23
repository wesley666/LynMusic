package top.iwesley.lyn.music.desktop.ui

import top.iwesley.lyn.music.*

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min

internal enum class LayoutOrientation { Portrait, Landscape }

/** Layout policy owned by this UI. Platform identity never chooses a page or shell. */
internal data class LayoutProfile(val maxWidth: Dp, val maxHeight: Dp) {
    val orientation = if (maxWidth > maxHeight) LayoutOrientation.Landscape else LayoutOrientation.Portrait
    val isPortrait get() = orientation == LayoutOrientation.Portrait
    val isLandscape get() = orientation == LayoutOrientation.Landscape
    val isExpandedLayout get() = true
    val isCompactLayout get() = !isExpandedLayout
    val isExpandedDevice get() = true
    val usesPortraitLibraryNavigation get() = false
}

internal fun buildLayoutProfile(maxWidth: Dp, maxHeight: Dp) = LayoutProfile(maxWidth, maxHeight)
