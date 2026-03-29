package top.iwesley.lyn.music

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.remember
import top.iwesley.lyn.music.platform.createIosAppComponent

fun MainViewController() = ComposeUIViewController {
    val appComponent = remember { createIosAppComponent() }
    App(appComponent)
}
