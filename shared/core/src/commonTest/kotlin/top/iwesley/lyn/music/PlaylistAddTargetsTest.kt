package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.PlaylistKind
import top.iwesley.lyn.music.core.model.PlaylistSummary
import top.iwesley.lyn.music.core.model.SYSTEM_LIKED_PLAYLIST_ID

class PlaylistAddTargetsTest {

    @Test
    fun `liked target stays first and existing memberships are disabled`() {
        val targets = buildPlaylistAddTargets(
            playlists = listOf(
                PlaylistSummary(
                    id = "playlist-1",
                    name = "通勤",
                    kind = PlaylistKind.USER,
                    updatedAt = 20L,
                    memberTrackIds = setOf("track-1"),
                ),
                PlaylistSummary(
                    id = "playlist-2",
                    name = "夜跑",
                    kind = PlaylistKind.USER,
                    updatedAt = 10L,
                ),
            ),
            favoriteTrackIds = setOf("track-1"),
            trackId = "track-1",
        )

        assertEquals(listOf(SYSTEM_LIKED_PLAYLIST_ID, "playlist-1", "playlist-2"), targets.map { it.id })
        assertTrue(targets.first().alreadyContainsTrack)
        assertTrue(targets[1].alreadyContainsTrack)
        assertFalse(targets[2].alreadyContainsTrack)
    }

    @Test
    fun `targets remain enabled when current track is null`() {
        val targets = buildPlaylistAddTargets(
            playlists = listOf(
                PlaylistSummary(
                    id = "playlist-1",
                    name = "收藏夹",
                    kind = PlaylistKind.USER,
                    updatedAt = 1L,
                    memberTrackIds = setOf("track-1"),
                ),
            ),
            favoriteTrackIds = setOf("track-1"),
            trackId = null,
        )

        assertEquals(2, targets.size)
        assertFalse(targets[0].alreadyContainsTrack)
        assertFalse(targets[1].alreadyContainsTrack)
    }
}
