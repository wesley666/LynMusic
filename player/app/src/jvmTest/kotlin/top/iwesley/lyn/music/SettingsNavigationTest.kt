package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SettingsNavigationTest {
    @Test
    fun `desktop settings defaults to theme section`() {
        assertEquals(SettingsSection.Theme, defaultDesktopSettingsSection())
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
    fun `mobile navigation closes back to list`() {
        assertEquals(SettingsMobileNavigation.List, closeSettingsMobileNavigation())
    }

    @Test
    fun `missing section name resolves to mobile list`() {
        assertEquals(SettingsMobileNavigation.List, toSettingsMobileNavigation(null))
    }
}
