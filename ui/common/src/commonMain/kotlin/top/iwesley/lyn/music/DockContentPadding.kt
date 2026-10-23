package top.iwesley.lyn.music

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

val LocalDockBottomInset = staticCompositionLocalOf { 0.dp }

/** Applied inside scrolling content, not to the viewport, so artwork can pass behind the Dock. */
@Composable
fun dockContentPadding(base: PaddingValues): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = base.calculateStartPadding(direction),
        top = base.calculateTopPadding(),
        end = base.calculateEndPadding(direction),
        bottom = base.calculateBottomPadding() + LocalDockBottomInset.current,
    )
}
