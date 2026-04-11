package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SettingsNavigationTest {
    @Test
    fun `desktop settings defaults to general section`() {
        assertEquals(SettingsSection.General, defaultSettingsSection(desktopPlatform()))
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
