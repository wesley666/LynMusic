package top.iwesley.lyn.music.cast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.Track

class CastModelsTest {
    @Test
    fun `unsupported gateway stays unsupported and ignores commands`() = runTest {
        val gateway = UnsupportedCastGateway
        val request = CastMediaRequest(
            uri = "https://example.com/song.mp3",
            title = "Song",
        )

        gateway.startDiscovery()
        gateway.cast(deviceId = "device-1", request = request)
        gateway.playCast()
        gateway.pauseCast()
        gateway.seekCast(42_000L)
        gateway.stopCast()
        gateway.stopDiscovery()

        assertFalse(gateway.isSupported)
        assertEquals(CastSessionStatus.Unsupported, gateway.state.value.status)
        assertTrue(gateway.state.value.errorMessage.orEmpty().contains("暂不支持"))
    }

    @Test
    fun `session state can carry remote playback state`() {
        val playback = CastPlaybackState(
            positionMs = 12_000L,
            durationMs = 180_000L,
            isPlaying = true,
            canSeek = true,
            isEnded = false,
            lastUpdatedAtMs = 100L,
        )
        val state = CastSessionState(
            status = CastSessionStatus.Casting,
            playback = playback,
        )

        assertEquals(playback, state.playback)
        assertTrue(state.isCasting)
    }

    @Test
    fun `direct cast uri only accepts http and https`() {
        assertTrue(isDirectCastUri("https://example.com/song.mp3"))
        assertTrue(isDirectCastUri("http://example.com/song.mp3"))
        assertFalse(isDirectCastUri("smb://server/share/song.mp3"))
        assertFalse(isDirectCastUri("file:///music/song.mp3"))
    }

    @Test
    fun `direct cast artwork uri keeps network urls and drops local urls`() {
        val track = sampleTrack()
        val networkArtwork = buildDirectCastMediaRequest(
            track = track,
            uri = "https://example.com/song.mp3",
            artworkUri = " https://img.example.com/cover.jpg?token=1 ",
        )
        val localArtwork = buildDirectCastMediaRequest(
            track = track,
            uri = "https://example.com/song.mp3",
            artworkUri = "/tmp/cover.jpg",
        )
        val fileArtwork = buildDirectCastMediaRequest(
            track = track,
            uri = "https://example.com/song.mp3",
            artworkUri = "file:///tmp/cover.jpg",
        )

        assertEquals("https://img.example.com/cover.jpg?token=1", networkArtwork.artworkUri)
        assertEquals(null, localArtwork.artworkUri)
        assertEquals(null, fileArtwork.artworkUri)
    }

    @Test
    fun `mime type is inferred from common audio extensions`() {
        assertEquals("audio/mpeg", inferCastMimeType("https://example.com/a.mp3?token=1"))
        assertEquals("audio/flac", inferCastMimeType("https://example.com/a.FLAC"))
        assertEquals(DEFAULT_CAST_AUDIO_MIME_TYPE, inferCastMimeType("https://example.com/stream"))
    }

    @Test
    fun `direct cast request can override mime type`() {
        val request = buildDirectCastMediaRequest(
            track = sampleTrack(),
            uri = "https://example.com/cast/stream/token",
            mimeType = "audio/flac",
        )

        assertEquals("audio/flac", request.mimeType)
    }

    @Test
    fun `status label prefers explicit error`() {
        val state = CastSessionState(
            status = CastSessionStatus.Searching,
            errorMessage = "网络不可用",
        )

        assertEquals("网络不可用", castSessionStatusLabel(state))
    }
}

private fun sampleTrack(): Track {
    return Track(
        id = "track-1",
        sourceId = "source-1",
        title = "Song",
        artistName = "Artist",
        albumTitle = "Album",
        durationMs = 180_000L,
        mediaLocator = "https://example.com/song.mp3",
        relativePath = "Song.mp3",
    )
}
