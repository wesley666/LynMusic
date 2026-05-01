package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.AppTab
import top.iwesley.lyn.music.core.model.PlaylistSummary

class MobileLibraryHubNavigationTest {

    @Test
    fun `mobile primary navigation only contains my and library`() {
        assertEquals(listOf(AppTab.My, AppTab.Library), mobilePrimaryNavigationTabs)
        assertFalse(AppTab.Favorites in mobilePrimaryNavigationTabs)
        assertFalse(AppTab.Playlists in mobilePrimaryNavigationTabs)
    }

    @Test
    fun `library bottom item is selected for every mobile library hub page`() {
        assertTrue(isMobilePrimaryNavigationSelected(AppTab.Library, AppTab.Library))
        assertTrue(isMobilePrimaryNavigationSelected(AppTab.Favorites, AppTab.Library))
        assertTrue(isMobilePrimaryNavigationSelected(AppTab.Playlists, AppTab.Library))
        assertFalse(isMobilePrimaryNavigationSelected(AppTab.My, AppTab.Library))
        assertTrue(isMobilePrimaryNavigationSelected(AppTab.My, AppTab.My))
    }

    @Test
    fun `mobile library hub pages keep expected order and labels`() {
        assertEquals(listOf(AppTab.Library, AppTab.Favorites, AppTab.Playlists), mobileLibraryHubTabs)
        assertEquals(listOf("曲库", "喜欢", "歌单"), mobileLibraryHubTabs.map(::mobileLibraryHubTabLabel))
        assertEquals(AppTab.Library, mobileLibraryHubTabForPage(0))
        assertEquals(AppTab.Favorites, mobileLibraryHubTabForPage(1))
        assertEquals(AppTab.Playlists, mobileLibraryHubTabForPage(2))
        assertEquals(AppTab.Library, mobileLibraryHubTabForPage(99))
    }

    @Test
    fun `mobile library hub resolves selected tab to pager page`() {
        assertEquals(0, mobileLibraryHubPageForTab(AppTab.Library))
        assertEquals(1, mobileLibraryHubPageForTab(AppTab.Favorites))
        assertEquals(2, mobileLibraryHubPageForTab(AppTab.Playlists))
        assertEquals(0, mobileLibraryHubPageForTab(AppTab.Settings))
    }

    @Test
    fun `mobile library hub search placeholders match selected page`() {
        assertEquals("搜索歌曲 / 艺人 / 专辑", mobileLibraryHubSearchPlaceholder(AppTab.Library))
        assertEquals("搜索喜欢的歌曲 / 艺人 / 专辑", mobileLibraryHubSearchPlaceholder(AppTab.Favorites))
        assertEquals("搜索歌单", mobileLibraryHubSearchPlaceholder(AppTab.Playlists))
        assertEquals("搜索", mobileLibraryHubSearchPlaceholder(AppTab.Settings))
    }

    @Test
    fun `mobile library hub source menu shows for library hub content pages`() {
        assertTrue(mobileLibraryHubShowsSourceMenu(AppTab.Library))
        assertTrue(mobileLibraryHubShowsSourceMenu(AppTab.Favorites))
        assertTrue(mobileLibraryHubShowsSourceMenu(AppTab.Playlists))
        assertFalse(mobileLibraryHubShowsSourceMenu(AppTab.My))
    }

    @Test
    fun `mobile library hub track sort only applies to library and favorites`() {
        assertTrue(mobileLibraryHubSupportsTrackSort(AppTab.Library))
        assertTrue(mobileLibraryHubSupportsTrackSort(AppTab.Favorites))
        assertFalse(mobileLibraryHubSupportsTrackSort(AppTab.Playlists))
        assertFalse(mobileLibraryHubSupportsTrackSort(AppTab.My))
    }

    @Test
    fun `mobile library hub uses pull to refresh for remote-backed hub pages`() {
        assertFalse(mobileLibraryHubUsesPullToRefresh(AppTab.Library))
        assertTrue(mobileLibraryHubUsesPullToRefresh(AppTab.Favorites))
        assertTrue(mobileLibraryHubUsesPullToRefresh(AppTab.Playlists))
        assertFalse(mobileLibraryHubUsesPullToRefresh(AppTab.My))
    }

    @Test
    fun `mobile library hub refresh indicator stays visible while refreshing or holding`() {
        assertFalse(
            mobileLibraryHubRefreshIndicatorVisible(
                isRefreshing = false,
                isMinimumHoldActive = false,
            ),
        )
        assertTrue(
            mobileLibraryHubRefreshIndicatorVisible(
                isRefreshing = true,
                isMinimumHoldActive = false,
            ),
        )
        assertTrue(
            mobileLibraryHubRefreshIndicatorVisible(
                isRefreshing = false,
                isMinimumHoldActive = true,
            ),
        )
        assertTrue(
            mobileLibraryHubRefreshIndicatorVisible(
                isRefreshing = true,
                isMinimumHoldActive = true,
            ),
        )
    }

    @Test
    fun `mobile library hub filters playlists by name only`() {
        val playlists = listOf(
            PlaylistSummary(id = "1", name = "Daily Mix"),
            PlaylistSummary(id = "2", name = "夜晚驾驶"),
            PlaylistSummary(id = "3", name = "Workout"),
        )

        assertEquals(playlists, filterMobileLibraryHubPlaylists(playlists, ""))
        assertEquals(playlists, filterMobileLibraryHubPlaylists(playlists, "   "))
        assertEquals(listOf(playlists[0]), filterMobileLibraryHubPlaylists(playlists, "daily"))
        assertEquals(listOf(playlists[1]), filterMobileLibraryHubPlaylists(playlists, "驾驶"))
        assertEquals(emptyList(), filterMobileLibraryHubPlaylists(playlists, "album"))
    }
}
