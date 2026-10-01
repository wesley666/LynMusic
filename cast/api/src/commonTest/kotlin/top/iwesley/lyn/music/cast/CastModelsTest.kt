package top.iwesley.lyn.music.cast

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.uiPlural

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiText

class CastModelsTest {
    @Test fun statusDescriptionsFollowTheDisplayLanguageAndPreserveDeviceNames() = runTest {
        val expected = mapOf(
            CastSessionStatus.Idle to listOf("Search for nearby devices", "搜索附近设备", "搜尋附近裝置"),
            CastSessionStatus.Searching to listOf("Searching for nearby devices", "正在搜索附近设备", "正在搜尋附近裝置"),
            CastSessionStatus.Connecting to listOf("Connecting to a device", "正在连接设备", "正在連接裝置"),
            CastSessionStatus.Casting to listOf("Casting", "正在投屏", "正在投屏"),
            CastSessionStatus.Failed to listOf("Casting failed.", "投屏失败。", "投屏失敗。"),
            CastSessionStatus.Unsupported to listOf("Casting is not supported on this platform.", "当前平台暂不支持投屏。", "當前平台暫不支持投屏。"),
        )
        val languages = listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese)
        expected.forEach { (status, labels) ->
            val description = castSessionStatusText(CastSessionState(status = status))
            languages.forEachIndexed { index, language -> assertEquals(labels[index], resolveUiText(description, language)) }
        }
        val deviceName = "客厅 %1\$s Speaker"
        val state = CastSessionState(status = CastSessionStatus.Connecting, selectedDeviceName = deviceName)
        val description = castSessionStatusText(state)
        assertEquals("Connecting to $deviceName", resolveUiText(description, AppLanguage.English))
        assertEquals("正在連接 $deviceName", resolveUiText(description, AppLanguage.TraditionalChinese))
        assertEquals(deviceName, state.selectedDeviceName)
        val unnamedDevice = CastDevice("device-1", "")
        val namedDevice = CastDevice("device-2", deviceName)
        assertEquals("Unknown device", resolveUiText(unnamedDevice.displayNameText(), AppLanguage.English))
        assertEquals("未知设备", resolveUiText(unnamedDevice.displayNameText(), AppLanguage.SimplifiedChinese))
        assertEquals("未知裝置", resolveUiText(unnamedDevice.displayNameText(), AppLanguage.TraditionalChinese))
        assertEquals(deviceName, resolveUiText(namedDevice.displayNameText(), AppLanguage.English))
        assertEquals("", unnamedDevice.name)
    }

    @Test fun generatedCastMessagesTakePrecedenceOverDiagnosticStrings() = runTest {
        val state = CastSessionState(status = CastSessionStatus.Failed, errorMessage = "仅用于诊断",
            messageText = uiText(Res.string.cast_searching), errorText = uiText(Res.string.cast_device_not_found))
        assertEquals("The selected casting device was not found.", resolveUiText(castSessionStatusText(state), AppLanguage.English))
        assertEquals("未找到選中的投屏裝置。", resolveUiText(castSessionStatusText(state), AppLanguage.TraditionalChinese))
        assertEquals("仅用于诊断", state.errorMessage)
        val searching = state.copy(status = CastSessionStatus.Searching, errorMessage = null, errorText = null,
            messageText = uiPlural(Res.plurals.cast_devices_found, (2).toInt(), 2))
        assertEquals("Found 2 devices", resolveUiText(castSessionStatusText(searching), AppLanguage.English))
    }
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
        assertEquals("cast_platform_unsupported", gateway.state.value.errorMessage)
    }

    @Test
    fun `session state can carry remote playback state`() = runTest {
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
    fun `direct cast uri only accepts http and https`() = runTest {
        assertTrue(isDirectCastUri("https://example.com/song.mp3"))
        assertTrue(isDirectCastUri("http://example.com/song.mp3"))
        assertFalse(isDirectCastUri("smb://server/share/song.mp3"))
        assertFalse(isDirectCastUri("file:///music/song.mp3"))
    }

    @Test
    fun `direct cast artwork uri keeps network urls and drops local urls`() = runTest {
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
    fun `mime type is inferred from common audio extensions`() = runTest {
        assertEquals("audio/mpeg", inferCastMimeType("https://example.com/a.mp3?token=1"))
        assertEquals("audio/flac", inferCastMimeType("https://example.com/a.FLAC"))
        assertEquals(DEFAULT_CAST_AUDIO_MIME_TYPE, inferCastMimeType("https://example.com/stream"))
    }

    @Test
    fun `direct cast request can override mime type`() = runTest {
        val request = buildDirectCastMediaRequest(
            track = sampleTrack(),
            uri = "https://example.com/cast/stream/token",
            mimeType = "audio/flac",
        )

        assertEquals("audio/flac", request.mimeType)
    }

    @Test
    fun `status label prefers explicit error`() = runTest {
        val state = CastSessionState(
            status = CastSessionStatus.Searching,
            errorMessage = "网络不可用",
        )

        assertEquals("Operation failed: 网络不可用", resolveUiText(castSessionStatusText(state), AppLanguage.English))
        assertEquals("操作失敗：网络不可用", resolveUiText(castSessionStatusText(state), AppLanguage.TraditionalChinese))
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
