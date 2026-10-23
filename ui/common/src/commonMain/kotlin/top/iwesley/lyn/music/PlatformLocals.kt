package top.iwesley.lyn.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import top.iwesley.lyn.music.core.model.PlatformDescriptor

val LocalPlatformDescriptor = staticCompositionLocalOf<PlatformDescriptor> {
    error("No PlatformDescriptor provided.")
}

val currentPlatformDescriptor: PlatformDescriptor
    @Composable
    @ReadOnlyComposable
    get() = LocalPlatformDescriptor.current
