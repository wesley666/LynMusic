package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor

class PlayerBackgroundArtworkLogicTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `background artwork is hidden when platform does not support blur`() {
        assertFalse(
            shouldRenderPlaybackBackgroundArtwork(
                platform = testPlatform(supportsPlaybackBackgroundArtworkBlur = false),
                artworkLocator = "https://img.example.com/cover.jpg",
            ),
        )
    }

    @Test
    fun `background artwork is shown when platform supports blur and artwork exists`() {
        assertTrue(
            shouldRenderPlaybackBackgroundArtwork(
                platform = testPlatform(supportsPlaybackBackgroundArtworkBlur = true),
                artworkLocator = "https://img.example.com/cover.jpg",
            ),
        )
    }

    @Test
    fun `background artwork is hidden when artwork is missing`() {
        val platform = testPlatform(supportsPlaybackBackgroundArtworkBlur = true)

        assertFalse(shouldRenderPlaybackBackgroundArtwork(platform, null))
        assertFalse(shouldRenderPlaybackBackgroundArtwork(platform, ""))
        assertFalse(shouldRenderPlaybackBackgroundArtwork(platform, "   "))
    }

    @Test
    fun `platform capabilities support playback background blur by default`() {
        assertTrue(emptyCapabilities().supportsPlaybackBackgroundArtworkBlur)
    }

    private fun testPlatform(
        supportsPlaybackBackgroundArtworkBlur: Boolean,
    ): PlatformDescriptor = PlatformDescriptor(
        name = ANDROID_PLATFORM_NAME,
        capabilities = emptyCapabilities().copy(
            supportsPlaybackBackgroundArtworkBlur = supportsPlaybackBackgroundArtworkBlur,
        ),
    )

    private fun emptyCapabilities(): PlatformCapabilities = PlatformCapabilities(
        supportsLocalFolderImport = false,
        supportsSambaImport = false,
        supportsWebDavImport = false,
        supportsNavidromeImport = false,
        supportsSystemMediaControls = false,
    )
}
