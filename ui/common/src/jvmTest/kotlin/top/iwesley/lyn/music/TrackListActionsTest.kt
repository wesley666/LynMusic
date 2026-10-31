package top.iwesley.lyn.music

import kotlin.test.*

class TrackListActionsTest {
    @Test fun `navigation actions keep their order and are independently optional`() {
        for (artistAvailable in listOf(false, true)) {
            for (albumAvailable in listOf(false, true)) {
                val events = mutableListOf<String>()
                val actions = trackListMenuActions(
                    favorite = TrackFavoriteUiState(false),
                    onSetFavorite = { events += "favorite" },
                    onAddToPlaylist = { events += "playlist" },
                    onTrackInfo = { events += "info" },
                    onOfflineDownload = { events += "offline" },
                    onArtistClick = if (artistAvailable) ({ events += "artist" }) else null,
                    onAlbumClick = if (albumAvailable) ({ events += "album" }) else null,
                )
                val navigationIds = listOfNotNull("artist".takeIf { artistAvailable }, "album".takeIf { albumAvailable })
                assertEquals(listOf("favorite", "playlist") + navigationIds + listOf("info", "offline"), actions.map { it.id })
                actions.filter { it.id in navigationIds }.forEach { it.onClick() }
                assertEquals(navigationIds, events)
            }
        }
    }

    @Test fun `actions have stable order and omit unsupported downloads`() {
        val actions = trackListMenuActions(TrackFavoriteUiState(false), {}, {}, {}, {})
        assertEquals(listOf("favorite", "playlist", "info", "offline"), actions.map { it.id })
        assertTrue(actions.all { it.enabled })
        val unavailable = trackListMenuActions(TrackFavoriteUiState(false, canChange = false), {}, null, {}, null)
        assertEquals(listOf("favorite", "playlist", "info"), unavailable.map { it.id })
        assertFalse(unavailable[0].enabled)
        assertFalse(unavailable[1].enabled)
        assertTrue(unavailable[2].enabled)
    }

    @Test fun `unknown state requests idempotent like and known liked state requests unlike`() {
        val calls = mutableListOf<Boolean>()
        for (state in listOf(TrackFavoriteUiState(true, isKnown = false), TrackFavoriteUiState(false), TrackFavoriteUiState(true))) {
            trackListMenuActions(state, { calls += it }, null, {}, null).first().onClick()
        }
        assertEquals(listOf(true, true, false), calls)
    }
}
