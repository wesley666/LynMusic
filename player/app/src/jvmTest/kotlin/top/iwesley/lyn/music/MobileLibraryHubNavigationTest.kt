package top.iwesley.lyn.music

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.AppTab
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaylistSummary

class MobileLibraryHubNavigationTest {

    @Test
    fun `legacy mobile primary navigation only contains my and library`() {
        assertEquals(listOf(AppTab.Library, AppTab.My), mobilePrimaryNavigationTabs)
        assertFalse(AppTab.Favorites in mobilePrimaryNavigationTabs)
        assertFalse(AppTab.Playlists in mobilePrimaryNavigationTabs)
    }

    @Test
    fun `portrait phone tablet and automotive navigation includes collection`() {
        for (name in listOf(ANDROID_PLATFORM_NAME, IOS_PLATFORM_NAME, ANDROID_AUTOMOTIVE_PLATFORM_NAME)) {
            val platform = PlatformDescriptor(name, emptyCapabilities())
            for ((width, height) in listOf(390 to 844, 800 to 1280)) {
                val layout = buildLayoutProfile(width.dp, height.dp, platform)
                assertTrue(layout.usesPortraitLibraryNavigation)
                assertEquals(
                    listOf(AppTab.Library, AppTab.Favorites, AppTab.My),
                    primaryNavigationTabs(layout.usesPortraitLibraryNavigation),
                )
            }
        }
    }

    @Test
    fun `likes and playlists highlight collection instead of library on phones`() {
        assertEquals(AppTab.Library, phoneNavigationGroup(AppTab.Library))
        assertEquals(AppTab.Favorites, phoneNavigationGroup(AppTab.Favorites))
        assertEquals(AppTab.Favorites, phoneNavigationGroup(AppTab.Playlists))
        assertEquals(AppTab.My, phoneNavigationGroup(AppTab.My))
        assertEquals(AppTab.Settings, phoneNavigationGroup(AppTab.Settings))
    }

    @Test
    fun `phone library and collection have separate pagers`() {
        assertEquals(listOf(AppTab.Library), libraryHubTabs(AppTab.Library, phoneNavigation = true))
        assertEquals(listOf(AppTab.Favorites, AppTab.Playlists), libraryHubTabs(AppTab.Favorites, true))
        assertEquals(listOf(AppTab.Favorites, AppTab.Playlists), libraryHubTabs(AppTab.Playlists, true))
        assertEquals(mobileLibraryHubTabs, libraryHubTabs(AppTab.Library, false))
    }

    @Test
    fun `phone root categories retain folder page for unsupported online sources`() {
        assertEquals(
            listOf("歌曲", "专辑", "艺人", "文件夹"),
            LibraryBrowserRootView.entries.map(::phoneLibraryRootLabel),
        )
        assertEquals(3, LibraryBrowserRootView.Folders.ordinal)
    }

    @Test
    fun `landscape and desktop or television portrait navigation stays unchanged`() {
        for (name in listOf(ANDROID_PLATFORM_NAME, IOS_PLATFORM_NAME, "Desktop", ANDROID_TV_PLATFORM_NAME, ANDROID_AUTOMOTIVE_PLATFORM_NAME)) {
            val platform = PlatformDescriptor(name, emptyCapabilities())
            val landscape = buildLayoutProfile(1280.dp, 800.dp, platform)
            assertFalse(landscape.usesPortraitLibraryNavigation)
            assertEquals(mobilePrimaryNavigationTabs, primaryNavigationTabs(landscape.usesPortraitLibraryNavigation))
            if (name == "Desktop" || name == ANDROID_TV_PLATFORM_NAME) {
                assertFalse(buildLayoutProfile(800.dp, 1280.dp, platform).usesPortraitLibraryNavigation)
            }
            assertEquals(listOf(AppTab.My, AppTab.Library, AppTab.Favorites, AppTab.Playlists), desktopNavigationTabs(platform).take(4))
        }
    }

    @Test
    fun `default selected app tab is library`() {
        assertEquals(AppTab.Library, defaultSelectedAppTab)
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
    fun `mobile library hub batch operation availability follows active page`() {
        assertTrue(
            mobileLibraryHubCanBatchOperate(
                tab = AppTab.Library,
                libraryVisibleTrackCount = 1,
                favoritesVisibleTrackCount = 0,
                playlistDetailVisibleTrackCount = 0,
                selectedPlaylistId = null,
            ),
        )
        assertTrue(
            mobileLibraryHubCanBatchOperate(
                tab = AppTab.Favorites,
                libraryVisibleTrackCount = 0,
                favoritesVisibleTrackCount = 1,
                playlistDetailVisibleTrackCount = 0,
                selectedPlaylistId = null,
            ),
        )
        assertTrue(
            mobileLibraryHubCanBatchOperate(
                tab = AppTab.Playlists,
                libraryVisibleTrackCount = 0,
                favoritesVisibleTrackCount = 0,
                playlistDetailVisibleTrackCount = 1,
                selectedPlaylistId = "playlist-1",
            ),
        )
        assertFalse(
            mobileLibraryHubCanBatchOperate(
                tab = AppTab.Playlists,
                libraryVisibleTrackCount = 0,
                favoritesVisibleTrackCount = 0,
                playlistDetailVisibleTrackCount = 1,
                selectedPlaylistId = null,
            ),
        )
        assertFalse(
            mobileLibraryHubCanBatchOperate(
                tab = AppTab.My,
                libraryVisibleTrackCount = 1,
                favoritesVisibleTrackCount = 1,
                playlistDetailVisibleTrackCount = 1,
                selectedPlaylistId = "playlist-1",
            ),
        )
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

    @Test
    fun `automotive navigation hides music tags entry`() {
        val platform = automotivePlatform()

        assertFalse(supportsMusicTagsEntry(platform))
        assertFalse(AppTab.Tags in mobileMoreNavigationTabs(platform))
        assertFalse(AppTab.Tags in desktopNavigationTabs(platform))
        assertFalse(isAppTabAvailableForPlatform(AppTab.Tags, platform))
        assertEquals(AppTab.Library, resolveAppTabForPlatform(AppTab.Tags, platform))
    }

    @Test
    fun `non automotive navigation keeps music tags entry`() {
        val platform = androidPlatform()

        assertTrue(supportsMusicTagsEntry(platform))
        assertTrue(AppTab.Tags in mobileMoreNavigationTabs(platform))
        assertTrue(AppTab.Tags in desktopNavigationTabs(platform))
        assertTrue(isAppTabAvailableForPlatform(AppTab.Tags, platform))
        assertEquals(AppTab.Tags, resolveAppTabForPlatform(AppTab.Tags, platform))
    }

    private fun androidPlatform(): PlatformDescriptor {
        return PlatformDescriptor(
            name = ANDROID_PLATFORM_NAME,
            capabilities = emptyCapabilities(),
        )
    }

    private fun automotivePlatform(): PlatformDescriptor {
        return PlatformDescriptor(
            name = ANDROID_AUTOMOTIVE_PLATFORM_NAME,
            capabilities = emptyCapabilities(),
        )
    }

    private fun emptyCapabilities(): PlatformCapabilities {
        return PlatformCapabilities(
            supportsLocalFolderImport = false,
            supportsSambaImport = false,
            supportsWebDavImport = false,
            supportsNavidromeImport = false,
            supportsSystemMediaControls = false,
        )
    }
}
