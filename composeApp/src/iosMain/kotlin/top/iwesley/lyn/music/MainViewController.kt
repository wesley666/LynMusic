package top.iwesley.lyn.music

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.remember
import top.iwesley.lyn.music.platform.createIosAppComponent

fun MainViewController() = ComposeUIViewController {
    val appComponentResult = remember { runCatching { createIosAppComponent() } }
    val appComponent = appComponentResult.getOrNull()
    if (appComponent != null) {
        App(appComponent)
    } else {
        StartupDatabaseErrorScreen(
            error = appComponentResult.exceptionOrNull(),
            showDetails = false,
        )
    }
}
