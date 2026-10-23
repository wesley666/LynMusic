package top.iwesley.lyn.music.mobile.ui

import top.iwesley.lyn.music.*

import kotlinx.coroutines.test.runTest


import top.iwesley.lyn.music.resources.*

import androidx.compose.ui.unit.dp
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
    fun `detail loading stays hidden when no playlist is selected`() = runTest {
        val state = buildPlaylistDetailPresentationState(
            selectedPlaylistId = null,
            detail = null,
            playlists = samplePlaylists(),
        )

        assertFalse(state.shouldShowDetailPane)
        assertFalse(state.isDetailSwitchLoading)
        assertNull(state.resolvedDetail)
        assertNull(state.requestedPlaylistName)
    }

    @Test
    fun `detail loading shows while selected playlist detail is still missing`() = runTest {
        val state = buildPlaylistDetailPresentationState(
            selectedPlaylistId = "playlist-2",
            detail = null,
            playlists = samplePlaylists(),
        )

        assertTrue(state.shouldShowDetailPane)
        assertTrue(state.isDetailSwitchLoading)
        assertNull(state.resolvedDetail)
        assertEquals("通勤", state.requestedPlaylistName)
    }

    @Test
    fun `detail loading shows and hides stale detail when detail id does not match selection`() = runTest {
        val state = buildPlaylistDetailPresentationState(
            selectedPlaylistId = "playlist-2",
            detail = PlaylistDetail(id = "playlist-1", name = "晨跑"),
            playlists = samplePlaylists(),
        )

        assertTrue(state.shouldShowDetailPane)
        assertTrue(state.isDetailSwitchLoading)
        assertNull(state.resolvedDetail)
        assertEquals("通勤", state.requestedPlaylistName)
    }

    @Test
    fun `detail loading hides when matching playlist detail is ready`() = runTest {
        val detail = PlaylistDetail(id = "playlist-2", name = "通勤")
        val state = buildPlaylistDetailPresentationState(
            selectedPlaylistId = "playlist-2",
            detail = detail,
            playlists = samplePlaylists(),
        )

        assertTrue(state.shouldShowDetailPane)
        assertFalse(state.isDetailSwitchLoading)
        assertEquals(detail, state.resolvedDetail)
        assertEquals("通勤", state.requestedPlaylistName)
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













    @Test
    fun `playlist track trailing width follows duration visibility`() = runTest {
        assertEquals(112.dp, playlistTrackTrailingWidth(selectionMode = false, showDuration = true))
        assertEquals(48.dp, playlistTrackTrailingWidth(selectionMode = false, showDuration = false))
        assertEquals(56.dp, playlistTrackTrailingWidth(selectionMode = true, showDuration = true))
        assertEquals(0.dp, playlistTrackTrailingWidth(selectionMode = true, showDuration = false))
    }

    private fun samplePlaylists(): List<PlaylistSummary> = listOf(
        PlaylistSummary(id = "playlist-1", name = "晨跑"),
        PlaylistSummary(id = "playlist-2", name = "通勤"),
    )
}
