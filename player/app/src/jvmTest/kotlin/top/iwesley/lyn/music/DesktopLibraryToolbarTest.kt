package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLibraryToolbarTest {
    @Test
    fun `desktop library search field width is fixed`() {
        assertEquals(200, desktopLibrarySearchFieldWidthDp())
    }

    @Test
    fun `desktop library search field height and corner radius are compact`() {
        assertEquals(40, desktopLibrarySearchFieldHeightDp())
        assertEquals(8, desktopLibrarySearchFieldCornerRadiusDp())
    }

    @Test
    fun `desktop library search clear button only shows for non blank query`() {
        assertFalse(shouldShowDesktopLibrarySearchClearButton(""))
        assertFalse(shouldShowDesktopLibrarySearchClearButton("   "))
        assertTrue(shouldShowDesktopLibrarySearchClearButton("jay"))
    }

    @Test
    fun `desktop library toolbar only applies to desktop search layout`() {
        assertTrue(useDesktopLibraryBrowserToolbar(showSearchField = true, showDuration = true))
        assertFalse(useDesktopLibraryBrowserToolbar(showSearchField = true, showDuration = false))
        assertFalse(useDesktopLibraryBrowserToolbar(showSearchField = false, showDuration = true))
    }

    @Test
    fun `desktop library toolbar shows source sort and optional action buttons`() {
        assertEquals(
            DesktopLibraryToolbarActions(
                showsSourceFilter = true,
                showsTrackSort = true,
                showsActionButton = true,
            ),
            resolveDesktopLibraryToolbarActions(
                showSearchField = true,
                showDuration = true,
                showTrackSortMenu = true,
                hasActionButton = true,
            ),
        )
        assertEquals(
            DesktopLibraryToolbarActions(
                showsSourceFilter = true,
                showsTrackSort = false,
                showsActionButton = false,
            ),
            resolveDesktopLibraryToolbarActions(
                showSearchField = true,
                showDuration = true,
                showTrackSortMenu = false,
                hasActionButton = false,
            ),
        )
    }

    @Test
    fun `mobile compact hidden search does not use desktop toolbar actions`() {
        assertEquals(
            DesktopLibraryToolbarActions(
                showsSourceFilter = false,
                showsTrackSort = false,
                showsActionButton = false,
            ),
            resolveDesktopLibraryToolbarActions(
                showSearchField = false,
                showDuration = false,
                showTrackSortMenu = true,
                hasActionButton = true,
            ),
        )
    }
}
