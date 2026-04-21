package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import androidx.compose.ui.unit.dp

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
}
