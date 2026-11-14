package top.iwesley.lyn.music.mobile.ui

import top.iwesley.lyn.music.*

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.resources.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.PlaylistDetail
import top.iwesley.lyn.music.core.model.PlaylistSummary
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.AppLanguageRuntime

class PlaylistsUiLogicTest {

    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `playlist summary artwork locator ignores blank values`() = runTest {
        assertNull(playlistSummaryArtworkLocator(PlaylistSummary(id = "empty", name = "空")))
        assertNull(playlistSummaryArtworkLocator(PlaylistSummary(id = "blank", name = "空白", artworkLocator = " ")))
        assertEquals(
            "/art/latest.jpg",
            playlistSummaryArtworkLocator(
                PlaylistSummary(id = "cover", name = "封面", artworkLocator = "/art/latest.jpg"),
            ),
        )
        assertNull(playlistSummaryArtworkCacheKey(PlaylistSummary(id = "empty-key", name = "空")))
        assertNull(
            playlistSummaryArtworkCacheKey(
                PlaylistSummary(id = "blank-key", name = "空白", artworkCacheKey = " "),
            ),
        )
        assertEquals(
            "album:local-1:album-1",
            playlistSummaryArtworkCacheKey(
                PlaylistSummary(id = "cover-key", name = "封面", artworkCacheKey = "album:local-1:album-1"),
            ),
        )
    }

    @Test
    fun `playlist import action is available only for loaded detail`() = runTest {
        assertFalse(canShowPlaylistImportAction(null))
        assertTrue(canShowPlaylistImportAction(PlaylistDetail(id = "playlist-1", name = "晨跑")))
    }

}
