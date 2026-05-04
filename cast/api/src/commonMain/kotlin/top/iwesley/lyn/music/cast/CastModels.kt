package top.iwesley.lyn.music.cast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.iwesley.lyn.music.core.model.Track

data class CastDevice(
    val id: String,
    val name: String,
    val description: String? = null,
    val modelName: String? = null,
    val manufacturer: String? = null,
    val location: String? = null,
)

data class CastMediaRequest(
    val uri: String,
    val title: String,
    val artistName: String? = null,
    val albumTitle: String? = null,
    val mimeType: String = DEFAULT_CAST_AUDIO_MIME_TYPE,
    val durationMs: Long = 0L,
)

enum class CastSessionStatus {
    Idle,
    Searching,
    Connecting,
    Casting,
    Failed,
    Unsupported,
}

data class CastSessionState(
    val status: CastSessionStatus = CastSessionStatus.Idle,
    val devices: List<CastDevice> = emptyList(),
    val selectedDeviceId: String? = null,
    val selectedDeviceName: String? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    val revision: Long = 0L,
) {
    val isSearching: Boolean
        get() = status == CastSessionStatus.Searching

    val isConnecting: Boolean
        get() = status == CastSessionStatus.Connecting

    val isCasting: Boolean
        get() = status == CastSessionStatus.Casting
}

interface CastGateway {
    val state: StateFlow<CastSessionState>
    val isSupported: Boolean

    suspend fun startDiscovery()
    suspend fun stopDiscovery()
    suspend fun cast(deviceId: String, request: CastMediaRequest)
    suspend fun stopCast()
    suspend fun release()
}

object UnsupportedCastGateway : CastGateway {
    private val unsupportedState = MutableStateFlow(
        CastSessionState(
            status = CastSessionStatus.Unsupported,
            errorMessage = "当前平台暂不支持投屏。",
        ),
    )

    override val state: StateFlow<CastSessionState> = unsupportedState.asStateFlow()
    override val isSupported: Boolean = false

    override suspend fun startDiscovery() = Unit
    override suspend fun stopDiscovery() = Unit
    override suspend fun cast(deviceId: String, request: CastMediaRequest) = Unit
    override suspend fun stopCast() = Unit
    override suspend fun release() = Unit
}

fun castSessionStatusLabel(state: CastSessionState): String {
    state.errorMessage?.takeIf { it.isNotBlank() }?.let { return it }
    return when (state.status) {
        CastSessionStatus.Idle -> "搜索附近设备"
        CastSessionStatus.Searching -> "正在搜索附近设备"
        CastSessionStatus.Connecting -> state.selectedDeviceName?.let { "正在连接 $it" } ?: "正在连接设备"
        CastSessionStatus.Casting -> state.selectedDeviceName?.let { "已投屏到 $it" } ?: "正在投屏"
        CastSessionStatus.Failed -> "投屏失败"
        CastSessionStatus.Unsupported -> "当前平台暂不支持"
    }
}

fun isDirectCastUri(uri: String): Boolean {
    val trimmed = uri.trim()
    return trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
}

fun inferCastMimeType(uri: String): String {
    val path = uri.substringBefore('?').substringBefore('#').lowercase()
    return when {
        path.endsWith(".mp3") -> "audio/mpeg"
        path.endsWith(".m4a") || path.endsWith(".mp4") -> "audio/mp4"
        path.endsWith(".aac") -> "audio/aac"
        path.endsWith(".flac") -> "audio/flac"
        path.endsWith(".wav") -> "audio/wav"
        path.endsWith(".ogg") || path.endsWith(".oga") -> "audio/ogg"
        path.endsWith(".opus") -> "audio/opus"
        else -> DEFAULT_CAST_AUDIO_MIME_TYPE
    }
}

fun buildDirectCastMediaRequest(
    track: Track,
    uri: String,
    durationMs: Long = track.durationMs,
): CastMediaRequest {
    return CastMediaRequest(
        uri = uri,
        title = track.title,
        artistName = track.artistName,
        albumTitle = track.albumTitle,
        mimeType = inferCastMimeType(uri),
        durationMs = durationMs.coerceAtLeast(0L),
    )
}

const val DEFAULT_CAST_AUDIO_MIME_TYPE = "audio/mpeg"
