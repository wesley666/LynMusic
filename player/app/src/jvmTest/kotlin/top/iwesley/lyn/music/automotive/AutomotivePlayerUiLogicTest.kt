package top.iwesley.lyn.music.automotive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import top.iwesley.lyn.music.core.model.PlaybackSnapshot

class AutomotivePlayerUiLogicTest {
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
}
