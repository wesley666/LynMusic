package top.iwesley.lyn.music.cast

import top.iwesley.lyn.music.core.model.diagnosticMessage
import top.iwesley.lyn.music.resources.*

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.AppLanguageRuntime
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiText

data class CastDevice(
    val id: String,
    val name: String,
    val description: String? = null,
    val modelName: String? = null,
    val manufacturer: String? = null,
    val location: String? = null,
)

fun CastDevice.displayNameText(): UiText =
    name.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) } ?: uiText(Res.string.cast_unknown_device)

data class CastMediaRequest(
    val uri: String,
    val title: String,
    val artistName: String? = null,
    val albumTitle: String? = null,
    val mimeType: String = DEFAULT_CAST_AUDIO_MIME_TYPE,
    val durationMs: Long = 0L,
    val artworkUri: String? = null,
)

enum class CastSessionStatus {
    Idle,
    Searching,
    Connecting,
    Casting,
    Failed,
    Unsupported,
}

data class CastPlaybackState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val canSeek: Boolean = false,
    val isEnded: Boolean = false,
    val lastUpdatedAtMs: Long = 0L,
)

data class CastSessionState(
    val status: CastSessionStatus = CastSessionStatus.Idle,
    val devices: List<CastDevice> = emptyList(),
    val selectedDeviceId: String? = null,
    val selectedDeviceName: String? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    val playback: CastPlaybackState? = null,
    val revision: Long = 0L,
    val messageText: UiText? = null,
    val errorText: UiText? = null,
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
    suspend fun playCast()
    suspend fun pauseCast()
    suspend fun seekCast(positionMs: Long)
    suspend fun stopCast()
    suspend fun release()
}

object UnsupportedCastGateway : CastGateway {
    private val unsupportedState = MutableStateFlow(
        CastSessionState(
            status = CastSessionStatus.Unsupported,
            errorMessage = (uiText(Res.string.cast_platform_unsupported)).diagnosticMessage(),
            errorText = uiText(Res.string.cast_platform_unsupported),
        ),
    )

    override val state: StateFlow<CastSessionState> = unsupportedState.asStateFlow()
    override val isSupported: Boolean = false

    override suspend fun startDiscovery() = Unit
    override suspend fun stopDiscovery() = Unit
    override suspend fun cast(deviceId: String, request: CastMediaRequest) = Unit
    override suspend fun playCast() = Unit
    override suspend fun pauseCast() = Unit
    override suspend fun seekCast(positionMs: Long) = Unit
    override suspend fun stopCast() = Unit
    override suspend fun release() = Unit
}

fun castSessionStatusText(state: CastSessionState): UiText {
    state.errorText?.let { return it }
    state.errorMessage?.takeIf { it.isNotBlank() }?.let {
        return uiText(Res.string.ui_error_with_details, UiText.Raw(it))
    }
    state.messageText?.let { return it }
    return when (state.status) {
        CastSessionStatus.Idle -> uiText(Res.string.cast_search_nearby)
        CastSessionStatus.Searching -> uiText(Res.string.cast_searching)
        CastSessionStatus.Connecting -> state.selectedDeviceName?.takeIf { it.isNotBlank() }?.let { uiText(Res.string.cast_connecting_device, it) }
            ?: uiText(Res.string.cast_connecting)
        CastSessionStatus.Casting -> state.selectedDeviceName?.takeIf { it.isNotBlank() }?.let { uiText(Res.string.cast_connected_device, it) }
            ?: uiText(Res.string.cast_casting)
        CastSessionStatus.Failed -> uiText(Res.string.cast_failed)
        CastSessionStatus.Unsupported -> uiText(Res.string.cast_platform_unsupported)
    }
}

suspend fun castSessionStatusLabel(state: CastSessionState): String =
    resolveUiText(castSessionStatusText(state), AppLanguageRuntime.effectiveLanguage.value)

fun isDirectCastUri(uri: String): Boolean {
    val trimmed = uri.trim()
    return trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
}

fun directCastUriOrNull(uri: String?): String? {
    val trimmed = uri?.trim().orEmpty()
    return trimmed.takeIf(::isDirectCastUri)
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
    artworkUri: String? = null,
    mimeType: String = inferCastMimeType(uri),
): CastMediaRequest {
    val normalizedUri = uri.trim()
    return CastMediaRequest(
        uri = normalizedUri,
        title = track.title,
        artistName = track.artistName,
        albumTitle = track.albumTitle,
        mimeType = mimeType.ifBlank { inferCastMimeType(normalizedUri) },
        durationMs = durationMs.coerceAtLeast(0L),
        artworkUri = directCastUriOrNull(artworkUri),
    )
}

const val DEFAULT_CAST_AUDIO_MIME_TYPE = "audio/mpeg"
