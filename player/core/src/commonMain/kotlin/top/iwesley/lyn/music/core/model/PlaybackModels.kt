package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.flow.StateFlow

enum class PlaybackMode {
    ORDER,
    SHUFFLE,
    REPEAT_ONE,
}

data class PlaybackSnapshot(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val mode: PlaybackMode = PlaybackMode.ORDER,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val metadataTitle: String? = null,
    val metadataArtistName: String? = null,
    val metadataAlbumTitle: String? = null,
    val metadataArtworkLocator: String? = null,
    val errorMessage: String? = null,
) {
    val currentTrack: Track?
        get() = queue.getOrNull(currentIndex)

    val currentDisplayTitle: String
        get() = metadataTitle
            ?.takeIf(::isUsablePlaybackMetadataTitle)
            ?: currentTrack?.title.orEmpty()

    val currentDisplayArtistName: String?
        get() = metadataArtistName?.takeIf { it.isNotBlank() } ?: currentTrack?.artistName

    val currentDisplayAlbumTitle: String?
        get() = metadataAlbumTitle?.takeIf { it.isNotBlank() } ?: currentTrack?.albumTitle

    val currentDisplayArtworkLocator: String?
        get() = metadataArtworkLocator?.takeIf { it.isNotBlank() } ?: currentTrack?.artworkLocator
}

private fun isUsablePlaybackMetadataTitle(title: String): Boolean {
    val normalized = title.trim()
    if (normalized.isBlank()) return false
    val lowercase = normalized.lowercase()
    if (INTERNAL_PLAYBACK_TITLE_PREFIXES.any { prefix -> lowercase.startsWith(prefix) }) {
        return false
    }
    return when {
        lowercase.startsWith("stream?") -> false
        lowercase.startsWith("/rest/stream?") -> false
        lowercase.startsWith("http://") || lowercase.startsWith("https://") -> "/rest/stream?" !in lowercase
        else -> true
    }
}

private val INTERNAL_PLAYBACK_TITLE_PREFIXES = listOf(
    "imem://",
    "fd://",
)

data class PlaybackGatewayState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val metadataTitle: String? = null,
    val metadataArtistName: String? = null,
    val metadataAlbumTitle: String? = null,
    val completionCount: Long = 0L,
    val errorMessage: String? = null,
)

class PlaybackLoadToken(
    val requestId: Long = 0L,
    private val isCurrentRequest: () -> Boolean = { true },
) {
    fun isCurrent(): Boolean = isCurrentRequest()
}

interface PlaybackGateway {
    val state: StateFlow<PlaybackGatewayState>

    suspend fun load(
        track: Track,
        playWhenReady: Boolean,
        startPositionMs: Long = 0L,
        loadToken: PlaybackLoadToken = PlaybackLoadToken(),
    )
    suspend fun play()
    suspend fun pause()
    suspend fun seekTo(positionMs: Long)
    suspend fun setVolume(volume: Float)
    suspend fun release()
}
