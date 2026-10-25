package top.iwesley.lyn.music

import kotlin.test.*

class TrackListActionsTest {
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
