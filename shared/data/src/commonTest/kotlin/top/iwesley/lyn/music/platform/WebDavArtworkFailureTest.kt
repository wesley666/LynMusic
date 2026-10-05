package top.iwesley.lyn.music.platform

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class WebDavArtworkFailureTest {
    private val resource = WebDavResolvedResource(
        relativePath = "Album/File.flac",
        isDirectory = false,
        fileName = "File.flac",
        contentLength = 8192L,
        modifiedAt = 56789L,
    )
    private val metadata = RemoteAudioMetadata(
        title = "Actual Title",
        artistName = "Artist",
        albumTitle = "Album",
        durationMs = 123_000L,
        trackNumber = 1,
        discNumber = 2,
        artworkBytes = byteArrayOf(1, 2, 3),
        embeddedLyrics = "hello",
    )

    @Test
    fun `artwork failure preserves tags and reports the original exception`() {
        val failure = IllegalStateException("Artwork cache is unavailable")
        var reportedFailure: Exception? = null
        var failureCount = 0
        val candidate = buildWebDavImportedTrackCandidate(
            sourceId = "source-1",
            resource = resource,
            metadata = metadata,
            storeArtwork = { throw failure },
            onArtworkFailure = {
                reportedFailure = it
                failureCount += 1
            },
        )

        assertSame(failure, reportedFailure)
        assertEquals(1, failureCount)
        assertNull(candidate.artworkLocator)
        assertEquals("Actual Title", candidate.title)
        assertEquals("Artist", candidate.artistName)
        assertEquals("Album", candidate.albumTitle)
        assertEquals(123_000L, candidate.durationMs)
        assertEquals(1, candidate.trackNumber)
        assertEquals(2, candidate.discNumber)
        assertEquals("hello", candidate.embeddedLyrics)
        assertEquals(resource.relativePath, candidate.relativePath)
        assertEquals(resource.contentLength, candidate.sizeBytes)
        assertEquals(resource.modifiedAt, candidate.modifiedAt)
    }

    @Test
    fun `artwork failure is optional when no failure callback is provided`() {
        val candidate = buildWebDavImportedTrackCandidate(
            sourceId = "source-1",
            resource = resource,
            metadata = metadata,
            storeArtwork = { throw IllegalStateException("Cache failure") },
        )
        assertEquals("Actual Title", candidate.title)
        assertNull(candidate.artworkLocator)
    }

    @Test
    fun `artwork cancellation and errors are not downgraded to missing artwork`() {
        val cancellation = CancellationException("Cancelled")
        val error = Error("Fatal cache failure")
        var failureCount = 0
        assertSame(cancellation, assertFailsWith<CancellationException> {
            buildWebDavImportedTrackCandidate(
                "source-1", resource, metadata,
                storeArtwork = { throw cancellation },
                onArtworkFailure = { failureCount += 1 },
            )
        })
        assertSame(error, assertFailsWith<Error> {
            buildWebDavImportedTrackCandidate(
                "source-1", resource, metadata,
                storeArtwork = { throw error },
                onArtworkFailure = { failureCount += 1 },
            )
        })
        assertEquals(0, failureCount)
    }
}
