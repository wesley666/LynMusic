package top.iwesley.lyn.music.tv

import org.junit.Test
import org.junit.Assert.*
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.feature.player.*

class PlayerPresentationTest {
    private fun playing() = PlayerState(snapshot = PlaybackSnapshot(queue = listOf(
        Track("song", "local", "Song", mediaLocator = "", relativePath = "")), currentIndex = 0))

    @Test fun queueStateBelongsToEachTvScreen() {
        var playback = playing()
        val first = PlayerPresentation({ playback })
        val second = PlayerPresentation({ playback })
        first.showQueue(true)
        assertTrue(first.isQueueVisible)
        assertFalse(second.isQueueVisible)
        first.onPlaybackIntent(PlayerIntent.PlayQueueIndex(5))
        assertTrue(first.isQueueVisible)
        first.onPlaybackIntent(PlayerIntent.PlayQueueIndex(0))
        assertFalse(first.isQueueVisible)
        first.showQueue(true)
        playback = PlayerState()
        first.synchronize(playback)
        assertFalse(first.isQueueVisible)
    }

    @Test fun oldScreenCannotCloseNewScreensSearch() {
        var playback = playing()
        var sequence = 0L
        val dispatch: (PlayerIntent) -> Unit = {
            when (it) {
                PlayerIntent.BeginManualLyricsSearch -> playback = playback.copy(manualLyricsSearchSession = PlayerTaskSession(++sequence, "song"))
                PlayerIntent.EndManualLyricsSearch -> playback = playback.copy(manualLyricsSearchSession = null)
                else -> Unit
            }
        }
        val first = PlayerPresentation({ playback }, dispatch)
        val second = PlayerPresentation({ playback }, dispatch)
        first.openSearch()
        second.openSearch()
        first.closeSearch()
        assertNotNull(playback.manualLyricsSearchSession)
        second.closeSearch()
        assertNull(playback.manualLyricsSearchSession)
        assertFalse(second.isManualLyricsSearchVisible)
    }
}
