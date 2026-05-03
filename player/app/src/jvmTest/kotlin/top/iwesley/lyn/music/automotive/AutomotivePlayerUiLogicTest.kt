package top.iwesley.lyn.music.automotive

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.feature.player.PlayerIntent

class AutomotivePlayerUiLogicTest {
    @Test
    fun `track and progress layout caps normal artwork at larger car size`() {
        val layout = resolveAutomotiveTrackAndProgressLayout(
            maxWidth = 900.dp,
            maxHeight = 800.dp,
        )

        assertEquals(false, layout.compactVertical)
        assertEquals(360.dp, layout.artworkSize)
        assertEquals(360.dp, layout.artworkMaximumSize)
        assertEquals(20.dp, layout.artworkTitleGap)
        assertEquals(44.dp, layout.progressTopGap)
        assertEquals(6.dp, layout.bottomPadding)
        assertEquals(0.86f, layout.progressWidthFraction)
    }

    @Test
    fun `track and progress layout keeps compact car height conservative`() {
        val layout = resolveAutomotiveTrackAndProgressLayout(
            maxWidth = 500.dp,
            maxHeight = 400.dp,
        )

        assertEquals(true, layout.compactVertical)
        assertEquals(192.dp, layout.artworkSize)
        assertEquals(250.dp, layout.artworkMaximumSize)
        assertEquals(12.dp, layout.artworkTitleGap)
        assertEquals(26.dp, layout.progressTopGap)
        assertEquals(8.dp, layout.bottomPadding)
        assertEquals(0.9f, layout.progressWidthFraction)
    }

    @Test
    fun `progress fraction clamps to playback bounds`() {
        assertEquals(
            0.25f,
            resolveAutomotivePlayerProgressFraction(
                PlaybackSnapshot(positionMs = 25_000L, durationMs = 100_000L),
            ),
        )
        assertEquals(
            1f,
            resolveAutomotivePlayerProgressFraction(
                PlaybackSnapshot(positionMs = 120_000L, durationMs = 100_000L),
            ),
        )
        assertEquals(
            0f,
            resolveAutomotivePlayerProgressFraction(
                PlaybackSnapshot(positionMs = 10_000L, durationMs = 0L),
            ),
        )
    }

    @Test
    fun `seek position resolves only when playback is seekable`() {
        val seekableSnapshot = PlaybackSnapshot(
            durationMs = 100_000L,
            canSeek = true,
        )

        assertEquals(50_000L, resolveAutomotivePlayerSeekPositionMs(0.5f, seekableSnapshot))
        assertEquals(0L, resolveAutomotivePlayerSeekPositionMs(-1f, seekableSnapshot))
        assertEquals(100_000L, resolveAutomotivePlayerSeekPositionMs(2f, seekableSnapshot))
        assertNull(resolveAutomotivePlayerSeekPositionMs(null, seekableSnapshot))
        assertNull(
            resolveAutomotivePlayerSeekPositionMs(
                0.5f,
                seekableSnapshot.copy(canSeek = false),
            ),
        )
        assertNull(
            resolveAutomotivePlayerSeekPositionMs(
                0.5f,
                seekableSnapshot.copy(durationMs = 0L),
            ),
        )
    }

    @Test
    fun `artwork swipe resolves skip intent by threshold and direction`() {
        assertEquals(
            PlayerIntent.SkipNext,
            resolveAutomotiveArtworkSwipeIntent(
                finalOffsetPx = -72f,
                swipeThresholdPx = 72f,
            ),
        )
        assertEquals(
            PlayerIntent.SkipPrevious,
            resolveAutomotiveArtworkSwipeIntent(
                finalOffsetPx = 72f,
                swipeThresholdPx = 72f,
            ),
        )
        assertNull(
            resolveAutomotiveArtworkSwipeIntent(
                finalOffsetPx = -71.9f,
                swipeThresholdPx = 72f,
            ),
        )
        assertNull(
            resolveAutomotiveArtworkSwipeIntent(
                finalOffsetPx = 71.9f,
                swipeThresholdPx = 72f,
            ),
        )
        assertNull(
            resolveAutomotiveArtworkSwipeIntent(
                finalOffsetPx = 100f,
                swipeThresholdPx = 0f,
            ),
        )
    }

    @Test
    fun `artwork drag offset is clamped to visual bounds`() {
        assertEquals(
            40f,
            resolveAutomotiveArtworkDragOffsetPx(
                currentOffsetPx = 25f,
                dragAmountPx = 15f,
                maxVisualOffsetPx = 80f,
            ),
        )
        assertEquals(
            80f,
            resolveAutomotiveArtworkDragOffsetPx(
                currentOffsetPx = 70f,
                dragAmountPx = 20f,
                maxVisualOffsetPx = 80f,
            ),
        )
        assertEquals(
            -80f,
            resolveAutomotiveArtworkDragOffsetPx(
                currentOffsetPx = -70f,
                dragAmountPx = -20f,
                maxVisualOffsetPx = 80f,
            ),
        )
        assertEquals(
            0f,
            resolveAutomotiveArtworkDragOffsetPx(
                currentOffsetPx = 20f,
                dragAmountPx = 10f,
                maxVisualOffsetPx = 0f,
            ),
        )
    }
}
