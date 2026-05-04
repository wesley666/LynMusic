package top.iwesley.lyn.music.cast.upnp.android

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
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
}
