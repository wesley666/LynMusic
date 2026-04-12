package top.iwesley.lyn.music

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor

class LayoutProfileTest {
    @Test
    fun `mobile portrait layout uses shared mobile and portrait rules`() {
        val profile = buildLayoutProfile(
            maxWidth = 420.dp,
            maxHeight = 900.dp,
            platform = androidPlatform(),
        )

        assertTrue(profile.isMobilePlatform)
        assertTrue(profile.isPortrait)
        assertTrue(profile.isCompactShell)
        assertTrue(profile.usesPortraitMiniPlayer)
        assertTrue(profile.usesTapToRevealPlaybackLyrics)
    }

    @Test
    fun `desktop landscape layout uses shared wide and desktop rules`() {
        val profile = buildLayoutProfile(
            maxWidth = 1200.dp,
            maxHeight = 800.dp,
            platform = desktopPlatform(),
        )

        assertFalse(profile.isMobilePlatform)
        assertTrue(profile.isLandscape)
        assertTrue(profile.isDesktopLayout)
        assertTrue(profile.isWideLayout)
        assertFalse(profile.usesTapToRevealPlaybackLyrics)
    }

    private fun androidPlatform(): PlatformDescriptor = PlatformDescriptor(
        name = "Android",
        capabilities = emptyCapabilities(),
    )

    private fun desktopPlatform(): PlatformDescriptor = PlatformDescriptor(
        name = "Desktop",
        capabilities = emptyCapabilities(),
    )

    private fun emptyCapabilities(): PlatformCapabilities = PlatformCapabilities(
        supportsLocalFolderImport = false,
        supportsSambaImport = false,
        supportsWebDavImport = false,
        supportsNavidromeImport = false,
        supportsSystemMediaControls = false,
    )
}
