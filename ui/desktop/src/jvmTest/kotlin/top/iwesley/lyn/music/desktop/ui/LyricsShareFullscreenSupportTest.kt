package top.iwesley.lyn.music.desktop.ui
import kotlin.test.*
class LyricsShareFullscreenSupportTest {
    @Test fun `fullscreen preview uses this platforms presentation policy`() {
        assertEquals(false, shouldEnableLyricsShareFullscreen(hasPreviewContent = true))
        assertFalse(shouldEnableLyricsShareFullscreen(hasPreviewContent = false))
    }
}
