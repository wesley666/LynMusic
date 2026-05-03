package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class ArtworkCacheVersionRegistry {
    private val versions = MutableStateFlow<Map<String, Long>>(emptyMap())

    fun observe(cacheKey: String): Flow<Long> {
        val normalized = cacheKey.normalizedVersionKey() ?: return flowOf(0L)
        return versions
            .map { values -> values[normalized] ?: 0L }
            .distinctUntilChanged()
    }

    fun bump(cacheKey: String) {
        val normalized = cacheKey.normalizedVersionKey() ?: return
        versions.update { values ->
            values + (normalized to ((values[normalized] ?: 0L) + 1L))
        }
    }

    private fun String.normalizedVersionKey(): String? = trim().takeIf { it.isNotEmpty() }
}
