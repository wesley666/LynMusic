package top.iwesley.lyn.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.PlatformDescriptor

data class DesktopWindowChrome(
    val immersiveTitleBarEnabled: Boolean = false,
    val topInset: Dp = 0.dp,
    val dragRegionHeight: Dp = 0.dp,
)

val LocalPlatformDescriptor = staticCompositionLocalOf<PlatformDescriptor> {
    error("No PlatformDescriptor provided.")
}

val LocalDesktopWindowChrome = staticCompositionLocalOf { DesktopWindowChrome() }

val currentPlatformDescriptor: PlatformDescriptor
    @Composable
    @ReadOnlyComposable
    get() = LocalPlatformDescriptor.current

val currentDesktopWindowChrome: DesktopWindowChrome
    @Composable
    @ReadOnlyComposable
    get() = LocalDesktopWindowChrome.current
