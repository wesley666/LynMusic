package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor

class LyricsShareFullscreenSupportTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `mobile platforms support lyrics share fullscreen when preview exists`() {
        assertTrue(shouldEnableLyricsShareFullscreen(androidPlatform(), hasPreviewContent = true))
        assertTrue(shouldEnableLyricsShareFullscreen(iosPlatform(), hasPreviewContent = true))
    }

    @Test
    fun `desktop platform does not support lyrics share fullscreen`() {
        assertFalse(shouldEnableLyricsShareFullscreen(desktopPlatform(), hasPreviewContent = true))
    }

    @Test
    fun `missing preview content disables lyrics share fullscreen`() {
        assertFalse(shouldEnableLyricsShareFullscreen(androidPlatform(), hasPreviewContent = false))
    }
}

private fun androidPlatform(): PlatformDescriptor = PlatformDescriptor(
    name = "Android",
    capabilities = testPlatformCapabilities(),
)

private fun iosPlatform(): PlatformDescriptor = PlatformDescriptor(
    name = "iPhone / iPad",
    capabilities = testPlatformCapabilities(),
)

private fun desktopPlatform(): PlatformDescriptor = PlatformDescriptor(
    name = "macOS",
    capabilities = testPlatformCapabilities(),
)

private fun testPlatformCapabilities(): PlatformCapabilities = PlatformCapabilities(
    supportsLocalFolderImport = true,
    supportsSambaImport = true,
    supportsWebDavImport = true,
    supportsNavidromeImport = true,
    supportsSystemMediaControls = true,
)
