package top.iwesley.lyn.music.cast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

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
        gateway.stopCast()
        gateway.stopDiscovery()

        assertFalse(gateway.isSupported)
        assertEquals(CastSessionStatus.Unsupported, gateway.state.value.status)
        assertTrue(gateway.state.value.errorMessage.orEmpty().contains("暂不支持"))
    }

    @Test
    fun `direct cast uri only accepts http and https`() {
        assertTrue(isDirectCastUri("https://example.com/song.mp3"))
        assertTrue(isDirectCastUri("http://example.com/song.mp3"))
        assertFalse(isDirectCastUri("smb://server/share/song.mp3"))
        assertFalse(isDirectCastUri("file:///music/song.mp3"))
    }

    @Test
    fun `mime type is inferred from common audio extensions`() {
        assertEquals("audio/mpeg", inferCastMimeType("https://example.com/a.mp3?token=1"))
        assertEquals("audio/flac", inferCastMimeType("https://example.com/a.FLAC"))
        assertEquals(DEFAULT_CAST_AUDIO_MIME_TYPE, inferCastMimeType("https://example.com/stream"))
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
