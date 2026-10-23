package top.iwesley.lyn.music.automotive.ui.platform

import top.iwesley.lyn.music.*

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Composable
internal actual fun PlayerLyricsSettingsWindow(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Popup(onDismissRequest = onDismiss, properties = PopupProperties(focusable = true, dismissOnClickOutside = false), content = content)
}
