package top.iwesley.lyn.music.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ArtworkCacheLocatorsTest {
    @Test
    fun `track artwork cache key prefers source and album id`() {
        val track = Track(
            id = "track-1",
            sourceId = "source-1",
            title = "Song",
            artistName = "Artist",
            albumTitle = "Album",
            albumId = "album-id",
            mediaLocator = "file:///song.flac",
            relativePath = "song.flac",
            artworkLocator = "https://img.example.com/cover.jpg",
        )

        assertEquals("album:source-1:album-id", trackArtworkCacheKey(track))
    }

    @Test
    fun `track artwork cache key falls back to normalized artist and album`() {
        val track = Track(
            id = "track-1",
            sourceId = "source-1",
            title = "Song",
            artistName = "  Artist   Name ",
            albumTitle = " Album   Title ",
            mediaLocator = "file:///song.flac",
            relativePath = "song.flac",
            artworkLocator = "https://img.example.com/cover.jpg",
        )

        assertEquals("album:source-1:artist name:album title", trackArtworkCacheKey(track))
    }

    @Test
    fun `track artwork cache key falls back to artwork locator without album`() {
        val track = Track(
            id = "track-1",
            sourceId = "source-1",
            title = "Song",
            mediaLocator = "file:///song.flac",
            relativePath = "song.flac",
            artworkLocator = " https://img.example.com/cover.jpg ",
        )

        assertEquals("https://img.example.com/cover.jpg", trackArtworkCacheKey(track))
    }

    @Test
    fun `artwork bytes hash is stable for same bytes`() {
        val bytes = byteArrayOf(1, 2, 3, 4)

        assertEquals(bytes.stableArtworkBytesHash(), byteArrayOf(1, 2, 3, 4).stableArtworkBytesHash())
    }
}
