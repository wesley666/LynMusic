package top.iwesley.lyn.music.desktop.ui

import androidx.compose.ui.unit.dp
import kotlin.test.*

class LayoutOwnershipTest {
    @Test fun `default destination remains library`() {
        assertEquals(top.iwesley.lyn.music.core.model.AppTab.Library, defaultSelectedAppTab)
    }
    @Test fun `dimensions follow this UI layout policy`() {
        for ((w, h) in listOf(760 to 1000, 1440 to 900, 480 to 800)) {
            assertTrue(buildLayoutProfile(w.dp, h.dp).isExpandedLayout)
            assertFalse(buildLayoutProfile(w.dp, h.dp).usesPortraitLibraryNavigation)
        }
    }
}
