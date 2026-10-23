package top.iwesley.lyn.music.desktop.ui.platform

import androidx.compose.runtime.Composable

@Composable
internal expect fun PlayerLyricsSettingsWindow(onDismiss: () -> Unit, content: @Composable () -> Unit)
