package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.flow.StateFlow

interface PlaybackPreferencesStore {
    val useSambaCache: StateFlow<Boolean>

    suspend fun setUseSambaCache(enabled: Boolean)
}
