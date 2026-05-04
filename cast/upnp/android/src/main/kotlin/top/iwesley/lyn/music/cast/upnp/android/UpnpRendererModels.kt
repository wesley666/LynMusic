package top.iwesley.lyn.music.cast.upnp.android

data class UpnpRendererMedia(
    val uri: String,
    val title: String,
    val mediaType: UpnpRendererMediaType = UpnpRendererMediaType.Audio,
    val artistName: String? = null,
    val albumTitle: String? = null,
    val artworkUri: String? = null,
    val mimeType: String? = null,
    val durationMs: Long = 0L,
    val metadata: String = "",
)

enum class UpnpRendererMediaType {
    Audio,
    Video,
}

interface UpnpMediaRendererCallback {
    fun onSetMedia(media: UpnpRendererMedia): Boolean
    fun onPlay(): Boolean
    fun onPause(): Boolean
    fun onStop(): Boolean
    fun onSeek(positionMs: Long): Boolean
    fun onSetVolume(volumePercent: Int): Boolean
    fun onSetMute(muted: Boolean): Boolean
}

enum class UpnpRendererTransportState(val upnpValue: String) {
    NoMediaPresent("NO_MEDIA_PRESENT"),
    Stopped("STOPPED"),
    Playing("PLAYING"),
    PausedPlayback("PAUSED_PLAYBACK"),
    Transitioning("TRANSITIONING"),
}
