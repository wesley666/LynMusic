package top.iwesley.lyn.music.mobile.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Composable
internal actual fun PlayerLyricsSettingsWindow(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Popup(onDismissRequest = onDismiss, properties = PopupProperties(focusable = true, dismissOnClickOutside = false), content = content)
}
