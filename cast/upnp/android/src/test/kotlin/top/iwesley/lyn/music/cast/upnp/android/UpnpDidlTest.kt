package top.iwesley.lyn.music.cast.upnp.android

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import top.iwesley.lyn.music.cast.CastMediaRequest

class UpnpDidlTest {
    @Test
    fun `duration is formatted as upnp time`() {
        assertEquals("00:03:05", formatUpnpDuration(185_500L))
        assertEquals("01:00:00", formatUpnpDuration(3_600_000L))
    }

    @Test
    fun `didl escapes text and resource uri`() {
        val didl = buildUpnpDidl(
            CastMediaRequest(
                uri = "https://example.com/a&b.mp3",
                title = "A < B",
                artistName = "Tom & Jerry",
                albumTitle = "\"Hits\"",
                mimeType = "audio/mpeg",
                durationMs = 123_000L,
            ),
        )

        assertContains(didl, "<dc:title>A &lt; B</dc:title>")
        assertContains(didl, "<upnp:artist>Tom &amp; Jerry</upnp:artist>")
        assertContains(didl, "<upnp:album>&quot;Hits&quot;</upnp:album>")
        assertContains(didl, "https://example.com/a&amp;b.mp3")
        assertContains(didl, "duration=\"00:02:03\"")
    }

    @Test
    fun `didl writes network album art uri`() {
        val didl = buildUpnpDidl(
            CastMediaRequest(
                uri = "https://example.com/song.mp3",
                title = "Song",
                artworkUri = "https://img.example.com/a&b.jpg",
            ),
        )

        assertContains(didl, "<upnp:albumArtURI>https://img.example.com/a&amp;b.jpg</upnp:albumArtURI>")
    }

    @Test
    fun `didl omits local album art uri`() {
        val didl = buildUpnpDidl(
            CastMediaRequest(
                uri = "https://example.com/song.mp3",
                title = "Song",
                artworkUri = "/tmp/cover.jpg",
            ),
        )

        assertFalse(didl.contains("albumArtURI"))
        assertFalse(didl.contains("/tmp/cover.jpg"))
    }
}
