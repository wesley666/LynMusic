package top.iwesley.lyn.music

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.SwingWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import top.iwesley.lyn.music.platform.createJvmAppComponent

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    installJvmUncaughtExceptionHandler()
    application {
        val appComponentResult = remember { runCatching { createJvmAppComponent() } }
        val desktopWindowChrome = remember {
            defaultDesktopWindowChrome(System.getProperty("os.name").orEmpty())
        }
        val windowState = rememberWindowState(
            size = DpSize(1440.dp, 900.dp),
        )
        SwingWindow(
            onCloseRequest = ::exitApplication,
            title = "LynMusic",
            state = windowState,
            icon = painterResource("desktop-icon.png"),
            init = { composeWindow ->
                composeWindow.minimumSize = Dimension(1200, 720)
                applyDesktopWindowChrome(composeWindow, desktopWindowChrome)
            },
        ) {
            val appComponent = appComponentResult.getOrNull()
            if (appComponent != null) {
                App(
                    component = appComponent,
                    desktopWindowChrome = desktopWindowChrome,
                )
            } else {
                StartupDatabaseErrorScreen(
                    error = appComponentResult.exceptionOrNull(),
                    showDetails = true,
                )
            }
        }
    }
}

internal fun defaultDesktopWindowChrome(osName: String): DesktopWindowChrome {
    return if (isJvmMacOs(osName)) {
        DesktopWindowChrome(
            immersiveTitleBarEnabled = true,
            topInset = 40.dp,
            dragRegionHeight = 40.dp,
        )
    } else {
        DesktopWindowChrome()
    }
}

internal fun isJvmMacOs(osName: String): Boolean {
    return osName.contains("mac", ignoreCase = true)
}

internal fun macOsImmersiveAwtClientProperties(): Map<String, Any> {
    return linkedMapOf(
        "apple.awt.fullWindowContent" to true,
        "apple.awt.transparentTitleBar" to true,
        "apple.awt.windowTitleVisible" to false,
    )
}

internal fun applyDesktopWindowChrome(
    window: java.awt.Window,
    desktopWindowChrome: DesktopWindowChrome,
) {
    if (!desktopWindowChrome.immersiveTitleBarEnabled || window !is javax.swing.RootPaneContainer) return
    macOsImmersiveAwtClientProperties().forEach { (key, value) ->
        window.rootPane.putClientProperty(key, value)
    }
}
