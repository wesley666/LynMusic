package top.iwesley.lyn.music

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import top.iwesley.lyn.music.platform.createJvmAppComponent

fun main() = application {
    val appComponent = remember { createJvmAppComponent() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "LynMusic",
    ) {
        App(appComponent)
    }
}
