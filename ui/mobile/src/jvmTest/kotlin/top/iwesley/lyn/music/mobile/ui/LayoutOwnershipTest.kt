package top.iwesley.lyn.music.mobile.ui

import androidx.compose.ui.unit.dp
import kotlin.test.*

class LayoutOwnershipTest {
    @Test fun `default destination remains library`() {
        assertEquals(top.iwesley.lyn.music.core.model.AppTab.Library, defaultSelectedAppTab)
    }
    @Test fun `dimensions follow this UI layout policy`() {
        assertTrue(buildLayoutProfile(480.dp, 900.dp).isCompactLayout)
        assertTrue(buildLayoutProfile(900.dp, 480.dp).isCompactLayout)
        assertTrue(buildLayoutProfile(800.dp, 1100.dp).usesPortraitLibraryNavigation)
        assertTrue(buildLayoutProfile(1280.dp, 800.dp).isExpandedLayout)
        assertFalse(buildLayoutProfile(600.dp, 600.dp).isExpandedLayout)
    }
}
