package top.iwesley.lyn.music.automotive.ui.platform

import top.iwesley.lyn.music.*

import androidx.compose.runtime.Composable

@Composable
internal expect fun PlayerLyricsSettingsWindow(onDismiss: () -> Unit, content: @Composable () -> Unit)
