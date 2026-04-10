package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import top.iwesley.lyn.music.platform.RemoteAudioMetadata
import top.iwesley.lyn.music.platform.WebDavListedResource
import top.iwesley.lyn.music.platform.buildWebDavImportedTrackCandidate
import top.iwesley.lyn.music.platform.buildWebDavRangeHeader
import top.iwesley.lyn.music.platform.resolveWebDavListedResource

class WebDavSupportTest {

    @Test
    fun `listed resource ignores current directory placeholder`() {
        assertNull(
            resolveWebDavListedResource(
                rootUrl = "https://dav.example.com/music/",
                currentDirectory = "",
                resource = WebDavListedResource(
                    href = "https://dav.example.com/music/",
                    isDirectory = true,
                    name = "",
                    contentLength = 0L,
                    modifiedAt = 0L,
                ),
            ),
        )

        assertNull(
            resolveWebDavListedResource(
                rootUrl = "https://dav.example.com/music/",
                currentDirectory = "Albums",
                resource = WebDavListedResource(
                    href = "https://dav.example.com/music/Albums/",
                    isDirectory = true,
                    name = "Albums",
                    contentLength = 0L,
                    modifiedAt = 0L,
                ),
            ),
        )
    }

    @Test
    fun `listed resource mapping preserves nested paths and candidate metadata`() {
        val resolved = resolveWebDavListedResource(
            rootUrl = "https://dav.example.com/music/",
            currentDirectory = "试听",
            resource = WebDavListedResource(
                href = "https://dav.example.com/music/%E8%AF%95%E5%90%AC/Track%201.flac",
                isDirectory = false,
                name = null,
                contentLength = 4096L,
                modifiedAt = 12345L,
            ),
        )

        requireNotNull(resolved)
        assertEquals("试听/Track 1.flac", resolved.relativePath)
        assertEquals("Track 1.flac", resolved.fileName)
        val candidate = buildWebDavImportedTrackCandidate("source-1", resolved)
        assertEquals(
            "lynmusic-webdav://source-1/%E8%AF%95%E5%90%AC%2FTrack%201.flac",
            candidate.mediaLocator,
        )
        assertEquals("Track 1", candidate.title)
        assertEquals(4096L, candidate.sizeBytes)
        assertEquals(12345L, candidate.modifiedAt)
    }

    @Test
    fun `range header handles open ended bounded and zero length requests`() {
        assertNull(buildWebDavRangeHeader(position = 0L, requestedLength = -1L))
        assertEquals("bytes=128-", buildWebDavRangeHeader(position = 128L, requestedLength = -1L))
        assertEquals("bytes=128-191", buildWebDavRangeHeader(position = 128L, requestedLength = 64L))
        assertNull(buildWebDavRangeHeader(position = 0L, requestedLength = 0L))
    }

    @Test
    fun `metadata candidate preserves remote tags and artwork`() {
        val resolved = resolveWebDavListedResource(
            rootUrl = "https://dav.example.com/music/",
            currentDirectory = "Albums",
            resource = WebDavListedResource(
                href = "https://dav.example.com/music/Albums/Track%2001.flac",
                isDirectory = false,
                name = "Track 01.flac",
                contentLength = 8192L,
                modifiedAt = 56789L,
            ),
        )

        requireNotNull(resolved)
        val candidate = buildWebDavImportedTrackCandidate(
            sourceId = "source-2",
            resource = resolved,
            metadata = RemoteAudioMetadata(
                title = "Actual Title",
                artistName = "Artist",
                albumTitle = "Album",
                durationMs = 123_000L,
                trackNumber = 1,
                discNumber = 2,
                artworkBytes = byteArrayOf(1, 2, 3),
                embeddedLyrics = "hello",
            ),
            storeArtwork = { "artwork://cached" },
        )

        assertEquals("Actual Title", candidate.title)
        assertEquals("Artist", candidate.artistName)
        assertEquals("Album", candidate.albumTitle)
        assertEquals(123_000L, candidate.durationMs)
        assertEquals(1, candidate.trackNumber)
        assertEquals(2, candidate.discNumber)
        assertEquals("artwork://cached", candidate.artworkLocator)
        assertEquals("hello", candidate.embeddedLyrics)
        assertEquals(8192L, candidate.sizeBytes)
        assertEquals(56789L, candidate.modifiedAt)
    }
}
