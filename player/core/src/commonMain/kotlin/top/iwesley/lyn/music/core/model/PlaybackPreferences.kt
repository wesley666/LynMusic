package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.flow.StateFlow

interface PlaybackPreferencesStore : AutoPlayOnStartupPreferencesStore {
    val useSambaCache: StateFlow<Boolean>
    val playbackVolume: StateFlow<Float>

    suspend fun setUseSambaCache(enabled: Boolean)
    suspend fun setPlaybackVolume(volume: Float)
}

const val DEFAULT_PLAYBACK_VOLUME: Float = 1f

fun normalizePlaybackVolume(volume: Float?): Float {
    val rawVolume = volume ?: return DEFAULT_PLAYBACK_VOLUME
    return if (rawVolume.isFinite()) {
        rawVolume.coerceIn(0f, 1f)
    } else {
        DEFAULT_PLAYBACK_VOLUME
    }
}
