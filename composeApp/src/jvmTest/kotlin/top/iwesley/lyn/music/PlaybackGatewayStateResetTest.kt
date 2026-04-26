package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.platform.resetForTrackSwitch

class PlaybackGatewayStateResetTest {

    @Test
    fun `reset for track switch clears playback state and preserves completion count`() {
        val initial = PlaybackGatewayState(
            isPlaying = true,
            positionMs = 12_345L,
            durationMs = 98_765L,
            volume = 0.4f,
            metadataTitle = "Song",
            metadataArtistName = "Artist",
            metadataAlbumTitle = "Album",
            currentNavidromeAudioQuality = NavidromeAudioQuality.Kbps320,
            completionCount = 7L,
            errorMessage = "boom",
        )

        val reset = initial.resetForTrackSwitch(volumeOverride = 0.8f)

        assertEquals(false, reset.isPlaying)
        assertEquals(0L, reset.positionMs)
        assertEquals(0L, reset.durationMs)
        assertEquals(0.8f, reset.volume)
        assertNull(reset.metadataTitle)
        assertNull(reset.metadataArtistName)
        assertNull(reset.metadataAlbumTitle)
        assertNull(reset.currentNavidromeAudioQuality)
        assertEquals(7L, reset.completionCount)
        assertNull(reset.errorMessage)
    }
}
