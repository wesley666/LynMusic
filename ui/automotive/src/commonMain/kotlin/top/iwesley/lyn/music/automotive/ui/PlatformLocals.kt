package top.iwesley.lyn.music.automotive.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import top.iwesley.lyn.music.core.model.PlatformDescriptor

internal val LocalPlatformDescriptor = staticCompositionLocalOf<PlatformDescriptor> {
    error("No automotive platform services provided.")
}

internal val currentPlatformDescriptor: PlatformDescriptor
    @Composable
    @ReadOnlyComposable
    get() = LocalPlatformDescriptor.current
