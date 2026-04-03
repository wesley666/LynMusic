package top.iwesley.lyn.music.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackModelsTest {

    @Test
    fun `navidrome stream placeholder title falls back to track title`() {
        val track = sampleTrack()
        val snapshot = PlaybackSnapshot(
            queue = listOf(track),
            currentIndex = 0,
            metadataTitle = "stream?id=123&u=demo&t=token&s=salt&v=1.16.1&c=LynMusic",
        )

        assertEquals("Blue Sky", snapshot.currentDisplayTitle)
    }

    @Test
    fun `navidrome stream url title falls back to track title`() {
        val track = sampleTrack()
        val snapshot = PlaybackSnapshot(
            queue = listOf(track),
            currentIndex = 0,
            metadataTitle = "https://demo.example.com/navidrome/rest/stream?id=123&u=demo&t=token&s=salt",
        )

        assertEquals("Blue Sky", snapshot.currentDisplayTitle)
    }

    @Test
    fun `regular metadata title still overrides track title`() {
        val track = sampleTrack()
        val snapshot = PlaybackSnapshot(
            queue = listOf(track),
            currentIndex = 0,
            metadataTitle = "Blue Sky (Live)",
        )

        assertEquals("Blue Sky (Live)", snapshot.currentDisplayTitle)
    }
}

private fun sampleTrack(): Track {
    return Track(
        id = "track-1",
        sourceId = "nav-source",
        title = "Blue Sky",
        artistName = "Artist A",
        albumTitle = "Album A",
        durationMs = 210_000L,
        mediaLocator = "navidrome://song/nav-source/song-1",
        relativePath = "Artist A/Album A/Blue Sky.flac",
    )
}
