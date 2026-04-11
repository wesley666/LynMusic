package top.iwesley.lyn.music

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import top.iwesley.lyn.music.platform.createJvmAppComponent

fun main() = application {
    val appComponent = remember { createJvmAppComponent() }
    val windowState = rememberWindowState(
        size = DpSize(1440.dp, 900.dp),
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "LynMusic",
        state = windowState,
        icon = painterResource("desktop-icon.png"),
    ) {
        SideEffect {
            window.minimumSize = Dimension(1200, 720)
        }
        App(appComponent)
    }
}
