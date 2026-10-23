package top.iwesley.lyn.music.automotive.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.AppTab

class AutomotiveNavigationTest {
    @Test
    fun `rotation changes shell and player together including narrow landscape screens`() {
        for ((width, height) in listOf(480 to 800, 720 to 1280)) {
            val portrait = AutomotiveLayoutProfile(width.dp, height.dp)
            val landscape = AutomotiveLayoutProfile(height.dp, width.dp)
            assertTrue(portrait.usesPortraitLibraryNavigation)
            assertTrue(portrait.isCompactLayout)
            assertFalse(portrait.isLandscape)
            assertFalse(landscape.usesPortraitLibraryNavigation)
            assertTrue(landscape.isExpandedLayout)
            assertTrue(landscape.isLandscape)
        }
        assertTrue(AutomotiveLayoutProfile(600.dp, 600.dp).isCompactLayout)
    }

    @Test
    fun `portrait collections and landscape navigation retain the existing car destinations`() {
        assertEquals(listOf(AppTab.Library, AppTab.Favorites, AppTab.My), automotivePrimaryNavigationTabs(true))
        assertEquals(listOf(AppTab.Library), libraryHubTabs(AppTab.Library, true))
        assertEquals(listOf(AppTab.Favorites, AppTab.Playlists), libraryHubTabs(AppTab.Playlists, true))
        assertEquals(AppTab.Favorites, automotiveNavigationGroup(AppTab.Playlists))
        assertEquals(listOf(AppTab.Sources, AppTab.Settings), automotiveMoreNavigationTabs)
        assertEquals(AppTab.entries.filter { it != AppTab.Tags }.toSet(), automotiveLandscapeNavigationTabs.toSet())
    }

    @Test
    fun `restored unsupported tags destination returns to library without changing other destinations`() {
        assertEquals(AppTab.Library, resolveAutomotiveTab(AppTab.Tags))
        for (tab in automotiveLandscapeNavigationTabs) assertEquals(tab, resolveAutomotiveTab(tab))
    }
}
