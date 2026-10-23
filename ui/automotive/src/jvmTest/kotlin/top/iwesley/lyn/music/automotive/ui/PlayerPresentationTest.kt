package top.iwesley.lyn.music.automotive.ui

import kotlin.test.*
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.feature.player.*

class PlayerPresentationTest {
    private class PlaybackFixture(private val shareInitializesImmediately: Boolean = true) {
        var state = PlayerState(snapshot = PlaybackSnapshot(queue = listOf(
            Track("song", "local", "Song", mediaLocator = "", relativePath = "")), currentIndex = 0))
        val intents = mutableListOf<PlayerIntent>()
        private var sequence = 0L
        fun dispatch(intent: PlayerIntent) {
            intents += intent
            when (intent) {
                PlayerIntent.BeginManualLyricsSearch -> state = state.copy(manualLyricsSearchSession = PlayerTaskSession(++sequence, "song"))
                PlayerIntent.EndManualLyricsSearch -> state = state.copy(manualLyricsSearchSession = null)
                PlayerIntent.BeginLyricsShare -> state = state.copy(lyricsShareSession = PlayerTaskSession(++sequence, "song"), isLyricsShareInitializing = !shareInitializesImmediately)
                PlayerIntent.EndLyricsShare -> state = state.copy(lyricsShareSession = null, isLyricsShareInitializing = false)
                PlayerIntent.BeginCastDiscovery -> state = state.copy(castDiscoverySession = PlayerTaskSession(++sequence, "song"))
                PlayerIntent.EndCastDiscovery -> state = state.copy(castDiscoverySession = null)
                else -> Unit
            }
        }
        fun presentation() = PlayerPresentation({ state }, ::dispatch)
    }

    @Test fun `collapsing pending share cancels it and late initialization cannot reopen it`() {
        val playback = PlaybackFixture(shareInitializesImmediately = false)
        val ui = playback.presentation()
        ui.expand()
        ui.openShare()
        val pending = playback.state
        ui.collapse()
        assertFalse(ui.isExpanded)
        assertNull(playback.state.lyricsShareSession)
        assertEquals(1, playback.intents.count { it == PlayerIntent.EndLyricsShare })
        ui.collapse()
        assertEquals(1, playback.intents.count { it == PlayerIntent.EndLyricsShare })
        ui.synchronize(pending.copy(isLyricsShareInitializing = false))
        ui.expand()
        assertFalse(ui.isLyricsShareVisible)
        ui.openShare()
        assertNotEquals(pending.lyricsShareSession, playback.state.lyricsShareSession)
        playback.state = playback.state.copy(isLyricsShareInitializing = false)
        ui.synchronize(playback.state)
        assertTrue(ui.isLyricsShareVisible)
    }

    @Test fun `collapse cancels share that became ready before the view synchronized`() {
        val playback = PlaybackFixture(shareInitializesImmediately = false)
        val ui = playback.presentation()
        ui.expand()
        ui.openShare()
        playback.state = playback.state.copy(isLyricsShareInitializing = false)
        assertFalse(ui.isLyricsShareVisible)
        ui.collapse()
        assertNull(playback.state.lyricsShareSession)
        ui.synchronize(playback.state)
        ui.expand()
        assertFalse(ui.isLyricsShareVisible)
    }

    @Test fun `collapsing an older owner cannot cancel the newer owners share`() {
        val playback = PlaybackFixture(shareInitializesImmediately = false)
        val first = playback.presentation()
        val second = playback.presentation()
        first.expand()
        second.expand()
        first.openShare()
        second.openShare()
        val newSession = playback.state.lyricsShareSession
        first.collapse()
        assertEquals(newSession, playback.state.lyricsShareSession)
        assertFalse(PlayerIntent.EndLyricsShare in playback.intents)
        playback.state = playback.state.copy(isLyricsShareInitializing = false)
        first.synchronize(playback.state)
        second.synchronize(playback.state)
        assertFalse(first.isLyricsShareVisible)
        assertTrue(second.isLyricsShareVisible)
        assertTrue(second.isExpanded)
    }

    @Test fun `share stays hidden until initialization completes for its own session`() {
        val playback = PlaybackFixture(shareInitializesImmediately = false)
        val owner = playback.presentation()
        val other = playback.presentation()
        owner.openShare()
        assertFalse(owner.isLyricsShareVisible)
        owner.synchronize(playback.state)
        assertFalse(owner.isLyricsShareVisible)
        playback.state = playback.state.copy(isLyricsShareInitializing = false)
        owner.synchronize(playback.state)
        other.synchronize(playback.state)
        assertTrue(owner.isLyricsShareVisible)
        assertFalse(other.isLyricsShareVisible)
    }

    @Test fun `disposing a pending share prevents its late initialization from opening the view`() {
        val playback = PlaybackFixture(shareInitializesImmediately = false)
        val ui = playback.presentation()
        ui.openShare()
        val pending = playback.state
        ui.dispose()
        assertNull(playback.state.lyricsShareSession)
        ui.synchronize(pending.copy(isLyricsShareInitializing = false))
        assertFalse(ui.isLyricsShareVisible)
    }

    @Test fun `old share owner cannot display or cancel the new owners initialized session`() {
        val playback = PlaybackFixture(shareInitializesImmediately = false)
        val first = playback.presentation()
        val second = playback.presentation()
        first.openShare()
        second.openShare()
        first.dispose()
        assertNotNull(playback.state.lyricsShareSession)
        playback.state = playback.state.copy(isLyricsShareInitializing = false)
        first.synchronize(playback.state)
        second.synchronize(playback.state)
        assertFalse(first.isLyricsShareVisible)
        assertTrue(second.isLyricsShareVisible)
    }

    @Test fun `two hosts sharing playback keep their presentation independent`() {
        val playback = PlaybackFixture()
        val first = playback.presentation()
        val second = playback.presentation()
        first.expand()
        first.showQueue(true)
        first.openLyricsStyle()
        assertFalse(second.isExpanded)
        assertFalse(second.isQueueVisible)
        assertFalse(second.isLyricsStyleVisible)
        first.closeLyricsStyle()
        assertEquals(listOf<PlayerIntent>(PlayerIntent.RequestLyricsFonts), playback.intents)
    }

    @Test fun `queue commands close only valid selection and empty playback clears queue`() {
        val playback = PlaybackFixture()
        val ui = playback.presentation()
        ui.showQueue(true)
        ui.onPlaybackIntent(PlayerIntent.PlayQueueIndex(99))
        assertTrue(ui.isQueueVisible)
        ui.onPlaybackIntent(PlayerIntent.PlayQueueIndex(0))
        assertFalse(ui.isQueueVisible)
        ui.showQueue(true)
        playback.state = PlayerState()
        ui.synchronize(playback.state)
        assertFalse(ui.isQueueVisible)
        ui.showQueue(true)
        assertFalse(ui.isQueueVisible)
    }

    @Test fun `disposing an older host cannot end the next hosts search session`() {
        val playback = PlaybackFixture()
        val first = playback.presentation()
        val second = playback.presentation()
        first.openSearch()
        val previousSession = playback.state.manualLyricsSearchSession
        second.openSearch()
        assertNotEquals(previousSession, playback.state.manualLyricsSearchSession)
        first.dispose()
        assertNotNull(playback.state.manualLyricsSearchSession)
        assertTrue(second.isManualLyricsSearchVisible)
        second.dispose()
        assertNull(playback.state.manualLyricsSearchSession)
    }

    @Test fun `completion and track change close only session views`() {
        val playback = PlaybackFixture()
        val ui = playback.presentation()
        ui.expand()
        ui.openLyricsStyle()
        ui.openSearch()
        playback.state = playback.state.copy(manualLyricsSearchSession = null)
        ui.synchronize(playback.state)
        assertFalse(ui.isManualLyricsSearchVisible)
        ui.openShare()
        playback.state = playback.state.copy(lyricsShareSession = null, isLyricsShareInitializing = false)
        ui.synchronize(playback.state)
        assertFalse(ui.isLyricsShareVisible)
        assertTrue(ui.isExpanded)
        assertTrue(ui.isLyricsStyleVisible)
    }

    @Test fun `disposing views ends discovery without requesting cast stop`() {
        val playback = PlaybackFixture()
        val ui = playback.presentation()
        ui.openCast()
        ui.openShare()
        ui.dispose()
        assertNull(playback.state.castDiscoverySession)
        assertNull(playback.state.lyricsShareSession)
        assertFalse(ui.isCastSheetVisible)
        assertFalse(PlayerIntent.StopCast in playback.intents)
    }
}
