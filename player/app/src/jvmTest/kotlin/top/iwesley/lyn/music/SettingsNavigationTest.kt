package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsNavigationTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `custom data location visibility follows dedicated platform capability`() {
        val desktop = desktopPlatform()
        assertTrue(
            shouldShowCustomDataLocation(
                desktop.copy(capabilities = desktop.capabilities.copy(supportsCustomDataLocation = true)),
            ),
        )
        assertFalse(shouldShowCustomDataLocation(desktop))
    }
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
    fun `player artwork style setting is shown on phone desktop and automotive`() {
        assertTrue(shouldShowPlayerArtworkStyleSetting(mobilePlatform()))
        assertTrue(shouldShowPlayerArtworkStyleSetting(platformNamed(IOS_PLATFORM_NAME)))
        assertTrue(shouldShowPlayerArtworkStyleSetting(desktopPlatform()))
        assertTrue(shouldShowPlayerArtworkStyleSetting(platformNamed(ANDROID_AUTOMOTIVE_PLATFORM_NAME)))
    }

    @Test
    fun `player artwork style setting is hidden on tv`() {
        assertFalse(shouldShowPlayerArtworkStyleSetting(platformNamed(ANDROID_TV_PLATFORM_NAME)))
    }

    @Test
    fun `auto open player on startup setting is shown on every non tv platform`() {
        assertTrue(shouldShowAutoOpenPlayerOnStartupSetting(mobilePlatform()))
        assertTrue(shouldShowAutoOpenPlayerOnStartupSetting(platformNamed(IOS_PLATFORM_NAME)))
        assertTrue(shouldShowAutoOpenPlayerOnStartupSetting(desktopPlatform()))
        assertTrue(shouldShowAutoOpenPlayerOnStartupSetting(platformNamed(ANDROID_AUTOMOTIVE_PLATFORM_NAME)))
    }

    @Test
    fun `auto open player on startup setting is hidden on tv`() {
        assertFalse(shouldShowAutoOpenPlayerOnStartupSetting(platformNamed(ANDROID_TV_PLATFORM_NAME)))
    }

    @Test
    fun `startup player auto open is enabled only for non tv platforms`() {
        assertTrue(shouldAutoOpenPlayerOnStartup(enabled = true, platform = mobilePlatform()))
        assertTrue(
            shouldAutoOpenPlayerOnStartup(
                enabled = true,
                platform = platformNamed(ANDROID_AUTOMOTIVE_PLATFORM_NAME),
            ),
        )
        assertFalse(shouldAutoOpenPlayerOnStartup(enabled = false, platform = mobilePlatform()))
        assertFalse(
            shouldAutoOpenPlayerOnStartup(
                enabled = true,
                platform = platformNamed(ANDROID_TV_PLATFORM_NAME),
            ),
        )
    }

    @Test
    fun `macos window close behavior setting is shown when supported`() {
        assertTrue(
            shouldShowMacOsWindowCloseBehaviorSetting(
                platformNamed("Desktop").copy(
                    capabilities = platformNamed("Desktop").capabilities.copy(
                        supportsMacOsWindowCloseBehavior = true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `macos window close behavior setting is hidden when unsupported`() {
        assertFalse(shouldShowMacOsWindowCloseBehaviorSetting(desktopPlatform()))
        assertFalse(shouldShowMacOsWindowCloseBehaviorSetting(mobilePlatform()))
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
