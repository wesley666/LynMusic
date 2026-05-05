package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsNavigationTest {
    @Test
    fun `desktop settings defaults to general section`() {
        assertEquals(SettingsSection.General, defaultSettingsSection(desktopPlatform()))
    }

    @Test
    fun `desktop settings sections include general`() {
        assertTrue(settingsSectionsForPlatform(desktopPlatform()).contains(SettingsSection.General))
    }

    @Test
    fun `mobile settings sections include general`() {
        assertTrue(settingsSectionsForPlatform(mobilePlatform()).contains(SettingsSection.General))
    }

    @Test
    fun `android phone settings sections include help`() {
        assertTrue(settingsSectionsForPlatform(mobilePlatform()).contains(SettingsSection.Help))
    }

    @Test
    fun `non phone platforms hide help section`() {
        assertFalse(settingsSectionsForPlatform(desktopPlatform()).contains(SettingsSection.Help))
        assertFalse(settingsSectionsForPlatform(platformNamed(IOS_PLATFORM_NAME)).contains(SettingsSection.Help))
        assertFalse(settingsSectionsForPlatform(platformNamed(ANDROID_TV_PLATFORM_NAME)).contains(SettingsSection.Help))
        assertFalse(settingsSectionsForPlatform(platformNamed(ANDROID_AUTOMOTIVE_PLATFORM_NAME)).contains(SettingsSection.Help))
    }

    @Test
    fun `mobile navigation opens theme detail`() {
        val navigation = openSettingsMobileNavigation(SettingsSection.Theme)

        assertIs<SettingsMobileNavigation.Detail>(navigation)
        assertEquals(SettingsSection.Theme, navigation.section)
    }

    @Test
    fun `mobile navigation opens lyrics detail`() {
        val navigation = openSettingsMobileNavigation(SettingsSection.Lyrics)

        assertIs<SettingsMobileNavigation.Detail>(navigation)
        assertEquals(SettingsSection.Lyrics, navigation.section)
    }

    @Test
    fun `mobile navigation opens storage detail`() {
        val navigation = openSettingsMobileNavigation(SettingsSection.Storage)

        assertIs<SettingsMobileNavigation.Detail>(navigation)
        assertEquals(SettingsSection.Storage, navigation.section)
    }

    @Test
    fun `mobile navigation opens about device detail`() {
        val navigation = openSettingsMobileNavigation(SettingsSection.AboutDevice)

        assertIs<SettingsMobileNavigation.Detail>(navigation)
        assertEquals(SettingsSection.AboutDevice, navigation.section)
    }

    @Test
    fun `mobile navigation opens about app detail`() {
        val navigation = openSettingsMobileNavigation(SettingsSection.AboutApp)

        assertIs<SettingsMobileNavigation.Detail>(navigation)
        assertEquals(SettingsSection.AboutApp, navigation.section)
    }

    @Test
    fun `mobile navigation closes back to list`() {
        assertEquals(SettingsMobileNavigation.List, closeSettingsMobileNavigation())
    }

    @Test
    fun `missing section name resolves to mobile list`() {
        assertEquals(SettingsMobileNavigation.List, toSettingsMobileNavigation(null))
    }
}

private fun desktopPlatform(): PlatformDescriptor = PlatformDescriptor(
    name = "Desktop",
    capabilities = PlatformCapabilities(
        supportsLocalFolderImport = true,
        supportsSambaImport = true,
        supportsWebDavImport = true,
        supportsNavidromeImport = true,
        supportsSystemMediaControls = true,
    ),
)

private fun mobilePlatform(): PlatformDescriptor = PlatformDescriptor(
    name = "Android",
    capabilities = PlatformCapabilities(
        supportsLocalFolderImport = true,
        supportsSambaImport = true,
        supportsWebDavImport = true,
        supportsNavidromeImport = true,
        supportsSystemMediaControls = true,
    ),
)

private fun platformNamed(name: String): PlatformDescriptor = PlatformDescriptor(
    name = name,
    capabilities = PlatformCapabilities(
        supportsLocalFolderImport = false,
        supportsSambaImport = false,
        supportsWebDavImport = false,
        supportsNavidromeImport = false,
        supportsSystemMediaControls = false,
    ),
)
