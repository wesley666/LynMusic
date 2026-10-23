package top.iwesley.lyn.music.desktop.ui
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*
class SettingsNavigationTest {
    @Test fun `settings starts at general and resolves saved selection`() {
        assertEquals(SettingsSection.General, defaultSettingsSection())
        assertEquals(SettingsSection.Theme, resolveSettingsSection("Theme"))
        assertNull(resolveSettingsSection("obsolete"))
        assertTrue(settingsSections().contains(SettingsSection.General))
        assertEquals(false, settingsSections().contains(SettingsSection.Help))
    }
    @Test fun `every desktop section can restore its sidebar selection`() {
        for (section in settingsSections()) assertEquals(section, resolveSettingsSection(section.name))
    }
    @Test fun `system integrations follow capabilities`() {
        val platform = PlatformDescriptor("Desktop", PlatformCapabilities(true, true, true, true, true))
        assertFalse(shouldShowMacOsWindowCloseBehaviorSetting(platform))
        assertTrue(shouldShowMacOsWindowCloseBehaviorSetting(platform.copy(capabilities = platform.capabilities.copy(supportsMacOsWindowCloseBehavior = true))))
        assertFalse(shouldShowCustomDataLocation(platform))
        assertTrue(shouldShowCustomDataLocation(platform.copy(capabilities = platform.capabilities.copy(supportsCustomDataLocation = true))))
    }
}
