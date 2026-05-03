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

data class ArtworkCachedTarget(
    val target: String,
    val version: String?,
    val isLocalFile: Boolean,
)

class ArtworkCachedTargetRegistry {
    private val targets = MutableStateFlow<Map<String, ArtworkCachedTarget>>(emptyMap())

    fun peek(cacheKey: String): ArtworkCachedTarget? {
        val normalized = cacheKey.normalizedTargetKey() ?: return null
        return targets.value[normalized]
    }

    fun put(cacheKey: String, target: ArtworkCachedTarget) {
        val normalized = cacheKey.normalizedTargetKey() ?: return
        if (target.target.isBlank()) return
        targets.update { values -> values + (normalized to target) }
    }

    private fun String.normalizedTargetKey(): String? = trim().takeIf { it.isNotEmpty() }
}
