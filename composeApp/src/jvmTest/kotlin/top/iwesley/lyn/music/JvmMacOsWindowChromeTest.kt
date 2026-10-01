package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.platform.JvmDataLocationProgress
import top.iwesley.lyn.music.platform.minimizeWindowOnCloseOrDefault

class JvmMacOsWindowChromeTest {
    @Test
    fun `default desktop window chrome enables immersive title bar on macos`() {
        val chrome = defaultDesktopWindowChrome("macOS")

        assertTrue(chrome.immersiveTitleBarEnabled)
        assertEquals(40.dp, chrome.topInset)
        assertEquals(40.dp, chrome.dragRegionHeight)
    }

    @Test
    fun `default desktop window chrome stays disabled on non macos`() {
        val chrome = defaultDesktopWindowChrome("Windows 11")

        assertFalse(chrome.immersiveTitleBarEnabled)
        assertEquals(0.dp, chrome.topInset)
        assertEquals(0.dp, chrome.dragRegionHeight)
    }

    @Test
    fun `macos immersive awt properties include required keys`() {
        assertEquals(
            linkedMapOf(
                "apple.awt.fullWindowContent" to true,
                "apple.awt.transparentTitleBar" to true,
                "apple.awt.windowTitleVisible" to false,
            ),
            macOsImmersiveAwtClientProperties(),
        )
    }

    @Test
    fun `macos close minimizes when setting is enabled`() {
        assertTrue(
            shouldMinimizeDesktopWindowOnClose(
                supportsMacOsWindowCloseBehavior = true,
                minimizeWindowOnClose = true,
            ),
        )
    }

    @Test
    fun `macos close exits when setting is disabled`() {
        assertFalse(
            shouldMinimizeDesktopWindowOnClose(
                supportsMacOsWindowCloseBehavior = true,
                minimizeWindowOnClose = false,
            ),
        )
    }

    @Test
    fun `non macos close exits even when minimize setting is enabled`() {
        assertFalse(
            shouldMinimizeDesktopWindowOnClose(
                supportsMacOsWindowCloseBehavior = false,
                minimizeWindowOnClose = true,
            ),
        )
    }

    @Test
    fun `desktop close is blocked while data location operation is in progress`() {
        assertFalse(
            shouldAllowDesktopWindowClose(
                JvmDesktopStartupState.Preparing(
                    JvmDataLocationProgress(top.iwesley.lyn.music.core.model.UiText.Raw("正在准备数据")),
                ),
            ),
        )
    }

    @Test
    fun `desktop close is blocked while normal startup is in progress`() {
        assertFalse(shouldAllowDesktopWindowClose(JvmDesktopStartupState.Starting))
    }

    @Test
    fun `desktop close is allowed after startup fails`() {
        assertTrue(
            shouldAllowDesktopWindowClose(
                JvmDesktopStartupState.ComponentFailed(IllegalStateException("启动失败")),
            ),
        )
    }

    @Test
    fun `completed persistence uses the final runtime close preference`() {
        assertFalse(
            resolveMinimizeWindowOnClosePreference(
                persistenceCompleted = true,
                currentValue = false,
                persistedValue = true,
            ),
        )
        assertTrue(
            resolveMinimizeWindowOnClosePreference(
                persistenceCompleted = true,
                currentValue = true,
                persistedValue = false,
            ),
        )
    }

    @Test
    fun `persistence timeout falls back to the committed close preference`() {
        assertTrue(
            resolveMinimizeWindowOnClosePreference(
                persistenceCompleted = false,
                currentValue = false,
                persistedValue = true,
            ),
        )
        assertFalse(
            resolveMinimizeWindowOnClosePreference(
                persistenceCompleted = false,
                currentValue = true,
                persistedValue = false,
            ),
        )
    }

    @Test
    fun `window close preference defaults to minimize for missing or invalid values`() {
        assertTrue(minimizeWindowOnCloseOrDefault(null))
        assertTrue(minimizeWindowOnCloseOrDefault("invalid"))
    }

    @Test
    fun `window close preference preserves explicit false`() {
        assertFalse(minimizeWindowOnCloseOrDefault("false"))
    }
}
