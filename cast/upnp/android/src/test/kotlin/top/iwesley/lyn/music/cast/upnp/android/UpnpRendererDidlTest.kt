package top.iwesley.lyn.music.cast.upnp.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpnpRendererDidlTest {
    @Test
    fun parseUpnpRendererMediaReadsEscapedDidlMetadata() {
        val metadata = """
            <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/"
                xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
                xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
                <item id="0" parentID="0" restricted="1">
                    <dc:title>A&amp;B &lt;Song&gt;</dc:title>
                    <upnp:artist>Artist &amp; Co</upnp:artist>
                    <upnp:album>Album</upnp:album>
                    <upnp:albumArtURI>https://music.example.test/cover?id=1&amp;token=abc</upnp:albumArtURI>
                    <res protocolInfo="http-get:*:audio/flac:*" duration="01:02:03">
                        https://music.example.test/stream?id=1&amp;token=abc
                    </res>
                </item>
            </DIDL-Lite>
        """.trimIndent()

        val media = parseUpnpRendererMedia("", metadata)

        assertNotNull(media)
        assertEquals("https://music.example.test/stream?id=1&token=abc", media.uri)
        assertEquals("A&B <Song>", media.title)
        assertEquals(UpnpRendererMediaType.Audio, media.mediaType)
        assertEquals("Artist & Co", media.artistName)
        assertEquals("Album", media.albumTitle)
        assertEquals("https://music.example.test/cover?id=1&token=abc", media.artworkUri)
        assertEquals("audio/flac", media.mimeType)
        assertEquals(3_723_000L, media.durationMs)
    }

    @Test
    fun parseUpnpRendererMediaUsesCurrentUriBeforeDidlResource() {
        val metadata = """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
                <item id="0" parentID="0" restricted="1">
                    <res protocolInfo="http-get:*:audio/mpeg:*">https://music.example.test/from-didl.mp3</res>
                </item>
            </DIDL-Lite>
        """.trimIndent()

        val media = parseUpnpRendererMedia("https://music.example.test/current.mp3", metadata)

        assertEquals("https://music.example.test/current.mp3", media?.uri)
    }

    @Test
    fun parseUpnpRendererMediaReadsVideoDidlMetadata() {
        val metadata = """
            <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/"
                xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
                <item id="0" parentID="0" restricted="1">
                    <dc:title>Concert</dc:title>
                    <res protocolInfo="http-get:*:video/mp4:*" duration="00:03:04">
                        https://video.example.test/concert.mp4
                    </res>
                </item>
            </DIDL-Lite>
        """.trimIndent()

        val media = parseUpnpRendererMedia("", metadata)

        assertNotNull(media)
        assertEquals(UpnpRendererMediaType.Video, media.mediaType)
        assertEquals("https://video.example.test/concert.mp4", media.uri)
        assertEquals("Concert", media.title)
        assertEquals("video/mp4", media.mimeType)
        assertEquals(184_000L, media.durationMs)
    }

    @Test
    fun parseUpnpRendererMediaFiltersLocalAndNonHttpArtwork() {
        val metadata = """
            <DIDL-Lite xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
                xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
                <item id="0" parentID="0" restricted="1">
                    <upnp:albumArtURI>content://media/external/audio/albumart/1</upnp:albumArtURI>
                    <res protocolInfo="http-get:*:audio/mpeg:*">https://music.example.test/song.mp3</res>
                </item>
            </DIDL-Lite>
        """.trimIndent()

        val media = parseUpnpRendererMedia("", metadata)

        assertNotNull(media)
        assertNull(media.artworkUri)
    }

    @Test
    fun supportedRendererAudioUriRejectsLocalAndMediaMismatches() {
        assertTrue(isSupportedRendererAudioUri("https://music.example.test/song.mp3", null))
        assertTrue(isSupportedRendererAudioUri("https://music.example.test/rest/stream", null))
        assertFalse(isSupportedRendererAudioUri("file:///sdcard/song.mp3", "audio/mpeg"))
        assertFalse(isSupportedRendererAudioUri("content://media/song/1", "audio/mpeg"))
        assertFalse(isSupportedRendererAudioUri("smb://server/music/song.mp3", "audio/mpeg"))
        assertFalse(isSupportedRendererAudioUri("webdav://server/music/song.mp3", "audio/mpeg"))
        assertFalse(isSupportedRendererAudioUri("https://music.example.test/cover.jpg", null))
        assertFalse(isSupportedRendererAudioUri("https://music.example.test/movie.mp4", "video/mp4"))
        assertTrue(isSupportedRendererVideoUri("https://video.example.test/movie.mp4", null))
        assertTrue(isSupportedRendererVideoUri("https://video.example.test/movie", "video/mp4"))
        assertFalse(isSupportedRendererVideoUri("https://video.example.test/poster.jpg", null))
        assertFalse(isSupportedRendererVideoUri("file:///sdcard/movie.mp4", "video/mp4"))
    }
}
