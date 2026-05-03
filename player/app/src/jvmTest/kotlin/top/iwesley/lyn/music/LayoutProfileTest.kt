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
        assertTrue(profile.isExpandedLayout)
    }

    @Test
    fun `automotive landscape uses automotive player overlay`() {
        val profile = buildLayoutProfile(
            maxWidth = 1280.dp,
            maxHeight = 720.dp,
            platform = automotivePlatform(),
        )

        assertTrue(profile.isAndroidAuto)
        assertTrue(profile.isLandscape)
        assertTrue(profile.isExpandedLayout)
        assertTrue(shouldUseAutomotiveLandscapePlayerOverlay(profile))
    }

    @Test
    fun `automotive portrait keeps shared compact player overlay`() {
        val profile = buildLayoutProfile(
            maxWidth = 720.dp,
            maxHeight = 1280.dp,
            platform = automotivePlatform(),
        )

        assertTrue(profile.isAndroidAuto)
        assertTrue(profile.isPortrait)
        assertFalse(profile.isExpandedLayout)
        assertFalse(shouldUseAutomotiveLandscapePlayerOverlay(profile))
    }

    @Test
    fun `android landscape does not use automotive player overlay`() {
        val profile = buildLayoutProfile(
            maxWidth = 900.dp,
            maxHeight = 420.dp,
            platform = androidPlatform(),
        )

        assertTrue(profile.isLandscape)
        assertFalse(profile.isAndroidAuto)
        assertFalse(shouldUseAutomotiveLandscapePlayerOverlay(profile))
    }

    @Test
    fun `automotive supports offline download ui actions with touch menus`() {
        val platform = automotivePlatform()

        assertTrue(platform.supportsOfflineDownloadUiActions())
        assertTrue(platform.usesTouchOfflineDownloadUi())
    }

    private fun androidPlatform(): PlatformDescriptor = PlatformDescriptor(
        name = "Android",
        capabilities = emptyCapabilities(),
    )

    private fun automotivePlatform(): PlatformDescriptor = PlatformDescriptor(
        name = "Android Automotive",
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
