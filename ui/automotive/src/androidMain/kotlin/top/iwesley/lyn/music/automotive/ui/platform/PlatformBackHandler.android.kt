package top.iwesley.lyn.music.automotive.ui.platform

import top.iwesley.lyn.music.*

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled && top.iwesley.lyn.music.automotive.ui.LocalMusicDetailIsForeground.current, onBack = onBack)
}
