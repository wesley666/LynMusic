package top.iwesley.lyn.music.mobile.ui
import kotlin.test.*
class LyricsShareFullscreenSupportTest {
    @Test fun `fullscreen preview uses this platforms presentation policy`() {
        assertEquals(true, shouldEnableLyricsShareFullscreen(hasPreviewContent = true))
        assertFalse(shouldEnableLyricsShareFullscreen(hasPreviewContent = false))
    }
}
