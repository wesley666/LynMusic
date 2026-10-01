package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.ProvideUiLanguage

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.remember
import top.iwesley.lyn.music.platform.createIosAppComponent

fun MainViewController() = ComposeUIViewController {
    ProvideUiLanguage {
        val appComponentResult = remember { runCatching { createIosAppComponent() } }
        val startupAutoOpenGate = remember { StartupAutoOpenGate() }
        val appComponent = appComponentResult.getOrNull()
        if (appComponent != null) {
            App(
                component = appComponent,
                startupAutoOpenGate = startupAutoOpenGate,
            )
        } else {
            StartupDatabaseErrorScreen(
                error = appComponentResult.exceptionOrNull(),
                showDetails = false,
            )
        }


    }
}
