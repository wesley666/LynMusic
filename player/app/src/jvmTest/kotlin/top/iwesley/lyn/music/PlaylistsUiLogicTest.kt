package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.PlaylistDetail
import top.iwesley.lyn.music.core.model.PlaylistSummary

class PlaylistsUiLogicTest {
    @Test
    fun `detail loading stays hidden when no playlist is selected`() {
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
    fun `detail loading shows while selected playlist detail is still missing`() {
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
    fun `detail loading shows and hides stale detail when detail id does not match selection`() {
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
    fun `detail loading hides when matching playlist detail is ready`() {
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

    private fun samplePlaylists(): List<PlaylistSummary> = listOf(
        PlaylistSummary(id = "playlist-1", name = "晨跑"),
        PlaylistSummary(id = "playlist-2", name = "通勤"),
    )
}
