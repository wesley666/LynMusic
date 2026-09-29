package top.iwesley.lyn.music.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.DesktopLyricsPosition
import top.iwesley.lyn.music.core.model.DesktopLyricsViewport
import top.iwesley.lyn.music.core.model.DesktopLyricsWindowLocation
import top.iwesley.lyn.music.core.model.calculateDesktopLyricsWindowLocation

class JvmDesktopLyricsOverlayWindowTest {
    @Test
    fun `normal lyrics retain their preferred width`() {
        assertEquals(
            600,
            calculateJvmDesktopLyricsWindowWidth(
                preferredWidth = 600,
                viewportWidth = 1_920,
            ),
        )
    }

    @Test
    fun `short lyrics retain the minimum width`() {
        assertEquals(
            240,
            calculateJvmDesktopLyricsWindowWidth(
                preferredWidth = 120,
                viewportWidth = 1_920,
            ),
        )
    }

    @Test
    fun `long lyrics are capped for each display width`() {
        assertEquals(
            1_824,
            calculateJvmDesktopLyricsWindowWidth(
                preferredWidth = 3_000,
                viewportWidth = 1_920,
            ),
        )
        assertEquals(
            1_184,
            calculateJvmDesktopLyricsWindowWidth(
                preferredWidth = 3_000,
                viewportWidth = 1_280,
            ),
        )
    }

    @Test
    fun `narrow displays take precedence over minimum width and reserved space`() {
        assertEquals(
            204,
            calculateJvmDesktopLyricsWindowWidth(
                preferredWidth = 600,
                viewportWidth = 300,
            ),
        )
        assertEquals(
            1,
            calculateJvmDesktopLyricsWindowWidth(
                preferredWidth = 600,
                viewportWidth = 80,
            ),
        )
    }

    @Test
    fun `capped window keeps the preferred center inside the display`() {
        val viewport = DesktopLyricsViewport(left = 0, top = 0, width = 1_920, height = 1_080)
        val windowWidth = calculateJvmDesktopLyricsWindowWidth(
            preferredWidth = 3_000,
            viewportWidth = viewport.width,
        )

        assertEquals(
            DesktopLyricsWindowLocation(x = 48, y = 512),
            calculateDesktopLyricsWindowLocation(
                position = DesktopLyricsPosition(0.5f, 0.5f),
                windowWidth = windowWidth,
                windowHeight = 56,
                viewport = viewport,
            ),
        )
    }

    @Test
    fun `default window uses the safe viewport with a side taskbar`() {
        assertEquals(
            DesktopLyricsWindowLocation(x = 208, y = 880),
            calculateJvmDesktopLyricsBottomCenterLocation(
                viewport = DesktopLyricsViewport(left = 160, top = 32, width = 1_760, height = 1_000),
                windowWidth = 1_664,
                windowHeight = 56,
            ),
        )
    }

    @Test
    fun `oversized default window is clamped to the safe viewport origin`() {
        assertEquals(
            DesktopLyricsWindowLocation(x = -1_200, y = 40),
            calculateJvmDesktopLyricsBottomCenterLocation(
                viewport = DesktopLyricsViewport(left = -1_200, top = 40, width = 800, height = 600),
                windowWidth = 900,
                windowHeight = 700,
            ),
        )
    }
}
