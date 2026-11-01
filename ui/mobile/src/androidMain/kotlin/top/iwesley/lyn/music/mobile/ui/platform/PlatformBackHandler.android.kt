package top.iwesley.lyn.music.mobile.ui.platform

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled && top.iwesley.lyn.music.mobile.ui.LocalMusicDetailIsForeground.current, onBack = onBack)
}
