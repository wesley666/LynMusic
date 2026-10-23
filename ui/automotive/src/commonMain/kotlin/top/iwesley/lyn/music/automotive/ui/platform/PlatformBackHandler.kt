package top.iwesley.lyn.music.automotive.ui.platform

import top.iwesley.lyn.music.*

import androidx.compose.runtime.Composable

@Composable
expect fun PlatformBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
)
