package top.iwesley.lyn.music

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.testing.libraryLoadMoreStatusLabel

import top.iwesley.lyn.music.feature.library.LibraryBrowserCount
import top.iwesley.lyn.music.resources.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLibraryToolbarTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `desktop library search field width is fixed`() = runTest {
        assertEquals(200, desktopLibrarySearchFieldWidthDp())
    }

    @Test
    fun `desktop library search field height and corner radius are compact`() = runTest {
        assertEquals(40, desktopLibrarySearchFieldHeightDp())
        assertEquals(8, desktopLibrarySearchFieldCornerRadiusDp())
    }

    @Test
    fun `desktop library search clear button only shows for non blank query`() = runTest {
        assertFalse(shouldShowDesktopLibrarySearchClearButton(""))
        assertFalse(shouldShowDesktopLibrarySearchClearButton("   "))
        assertTrue(shouldShowDesktopLibrarySearchClearButton("jay"))
    }

    @Test
    fun `desktop library toolbar only applies to desktop search layout`() = runTest {
        assertTrue(useDesktopLibraryBrowserToolbar(showSearchField = true, showDuration = true))
        assertFalse(useDesktopLibraryBrowserToolbar(showSearchField = true, showDuration = false))
        assertFalse(useDesktopLibraryBrowserToolbar(showSearchField = false, showDuration = true))
    }

    @Test
    fun `desktop library toolbar shows source sort and optional action buttons`() = runTest {
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
    fun `mobile compact hidden search does not use desktop toolbar actions`() = runTest {
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

    @Test
    fun `default library root selector keeps four peer entries`() = runTest {
        val model = buildLibraryRootSelectorModel(
            style = LibraryRootSelectorStyle.Default,
            trackCount = LibraryBrowserCount.exact(12),
            albumCount = LibraryBrowserCount.exact(3),
            artistCount = LibraryBrowserCount.exact(2),
            folderCount = 5,
            showFolderBrowser = true,
        )

        assertEquals(LibraryRootSelectorStyle.Default, model.style)
        assertEquals(
            listOf(
                LibraryBrowserRootView.Tracks,
                LibraryBrowserRootView.Albums,
                LibraryBrowserRootView.Artists,
                LibraryBrowserRootView.Folders,
            ),
            model.defaultItems.map { it.rootView },
        )
        assertEquals(null, model.heroItem)
        assertEquals(emptyList(), model.secondaryItems)
        assertFalse(model.playAllEnabled)
    }

    @Test
    fun `compact library root selector uses song hero and three secondary entries`() = runTest {
        val model = buildLibraryRootSelectorModel(
            style = LibraryRootSelectorStyle.CompactHero,
            trackCount = LibraryBrowserCount.exact(5458),
            albumCount = LibraryBrowserCount.exact(744),
            artistCount = LibraryBrowserCount.exact(293),
            folderCount = 704,
            showFolderBrowser = true,
        )

        assertEquals(LibraryRootSelectorStyle.CompactHero, model.style)
        assertEquals(
            LibraryRootSelectorItem(
                rootView = LibraryBrowserRootView.Tracks,
                title = top.iwesley.lyn.music.core.model.uiText(top.iwesley.lyn.music.resources.Res.string.library_all_tracks_label),
                value = "5458",
            ),
            model.heroItem,
        )
        assertEquals(
            listOf(
                LibraryBrowserRootView.Albums,
                LibraryBrowserRootView.Artists,
                LibraryBrowserRootView.Folders,
            ),
            model.secondaryItems.map { it.rootView },
        )
        assertTrue(model.playAllEnabled)
    }

    @Test
    fun `compact library root selector disables play all without tracks`() = runTest {
        val model = buildLibraryRootSelectorModel(
            style = LibraryRootSelectorStyle.CompactHero,
            trackCount = LibraryBrowserCount.exact(0),
            albumCount = LibraryBrowserCount.exact(0),
            artistCount = LibraryBrowserCount.exact(0),
            folderCount = 0,
            showFolderBrowser = true,
        )

        assertFalse(model.playAllEnabled)
        assertEquals("0", model.heroItem?.value)
    }

    @Test
    fun `library root selector uses remote total when available`() = runTest {
        val model = buildLibraryRootSelectorModel(
            style = LibraryRootSelectorStyle.CompactHero,
            trackCount = LibraryBrowserCount(loaded = 100, total = 450000, hasMore = true),
            albumCount = LibraryBrowserCount(loaded = 100, total = null, hasMore = true),
            artistCount = LibraryBrowserCount(loaded = 87, total = null, hasMore = false),
            folderCount = 0,
            showFolderBrowser = false,
            playAllEnabled = true,
        )

        assertEquals("450000", model.heroItem?.value)
        assertEquals("100+", model.secondaryItems.first { it.rootView == LibraryBrowserRootView.Albums }.value)
        assertEquals("87", model.secondaryItems.first { it.rootView == LibraryBrowserRootView.Artists }.value)
        assertTrue(model.playAllEnabled)
    }

    @Test
    fun `library root selector play all uses loaded tracks not remote total`() = runTest {
        val model = buildLibraryRootSelectorModel(
            style = LibraryRootSelectorStyle.CompactHero,
            trackCount = LibraryBrowserCount(loaded = 0, total = 450000, hasMore = true),
            albumCount = LibraryBrowserCount.exact(0),
            artistCount = LibraryBrowserCount.exact(0),
            folderCount = 0,
            showFolderBrowser = false,
            playAllEnabled = false,
        )

        assertEquals("450000", model.heroItem?.value)
        assertFalse(model.playAllEnabled)
    }

    @Test
    fun `library load more status describes exact and unknown totals`() = runTest {
        assertEquals(
            "已显示 100 / 共 450000",
            libraryLoadMoreStatusLabel(LibraryBrowserCount(loaded = 100, total = 450000, hasMore = true)),
        )
        assertEquals(
            "已显示 100+",
            libraryLoadMoreStatusLabel(LibraryBrowserCount(loaded = 100, total = null, hasMore = true)),
        )
        assertEquals(
            "已显示 87",
            libraryLoadMoreStatusLabel(LibraryBrowserCount(loaded = 87, total = null, hasMore = false)),
        )
    }
}
