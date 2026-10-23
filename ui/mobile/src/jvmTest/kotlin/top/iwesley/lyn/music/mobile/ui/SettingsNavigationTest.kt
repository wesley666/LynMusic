package top.iwesley.lyn.music.mobile.ui
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*
class SettingsNavigationTest {
    @Test fun `settings starts at general and resolves saved selection`() {
        assertEquals(SettingsSection.General, defaultSettingsSection())
        assertEquals(SettingsSection.Theme, resolveSettingsSection("Theme"))
        assertNull(resolveSettingsSection("obsolete"))
        assertTrue(settingsSections().contains(SettingsSection.General))
        assertEquals(true, settingsSections().contains(SettingsSection.Help))
    }
    @Test fun `section detail navigation opens and returns to list`() {
        for (section in settingsSections()) {
            assertEquals(SettingsMobileNavigation.Detail(section), openSettingsMobileNavigation(section))
            assertEquals(SettingsMobileNavigation.Detail(section), toSettingsMobileNavigation(section.name))
        }
        assertEquals(SettingsMobileNavigation.List, closeSettingsMobileNavigation())
        assertEquals(SettingsMobileNavigation.List, toSettingsMobileNavigation("obsolete"))
    }
    @Test fun `system integrations follow capabilities`() {
        val platform = PlatformDescriptor("Android", PlatformCapabilities(true, true, true, true, true))
        assertFalse(shouldShowMacOsWindowCloseBehaviorSetting(platform))
        assertTrue(shouldShowMacOsWindowCloseBehaviorSetting(platform.copy(capabilities = platform.capabilities.copy(supportsMacOsWindowCloseBehavior = true))))
        assertFalse(shouldShowCustomDataLocation(platform))
        assertTrue(shouldShowCustomDataLocation(platform.copy(capabilities = platform.capabilities.copy(supportsCustomDataLocation = true))))
    }
}
