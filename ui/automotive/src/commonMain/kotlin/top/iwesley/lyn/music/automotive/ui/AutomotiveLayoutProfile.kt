package top.iwesley.lyn.music.automotive.ui

import androidx.compose.ui.unit.Dp
import top.iwesley.lyn.music.core.model.PlatformDescriptor

internal enum class AutomotiveOrientation { Portrait, Landscape }

/** Car layouts follow the available surface, including after rotation or resizing. */
internal data class AutomotiveLayoutProfile(val maxWidth: Dp, val maxHeight: Dp) {
    val orientation: AutomotiveOrientation
        get() = if (maxWidth > maxHeight) AutomotiveOrientation.Landscape else AutomotiveOrientation.Portrait
    val isLandscape: Boolean get() = orientation == AutomotiveOrientation.Landscape
    val isPortrait: Boolean get() = !isLandscape
    val isExpandedLayout: Boolean get() = isLandscape
    val isCompactLayout: Boolean get() = isPortrait
    val usesPortraitLibraryNavigation: Boolean get() = isPortrait
}

internal fun PlatformDescriptor.supportsOfflineDownloadUiActions(): Boolean = true

internal fun PlatformDescriptor.usesTouchOfflineDownloadUi(): Boolean = true
