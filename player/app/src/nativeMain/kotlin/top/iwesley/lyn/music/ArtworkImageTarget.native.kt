package top.iwesley.lyn.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.normalizedArtworkCacheLocator
import top.iwesley.lyn.music.core.model.parseNavidromeCoverLocator
import top.iwesley.lyn.music.core.model.resolveArtworkCacheTarget

internal actual suspend fun resolveLynArtworkTarget(
    locator: String?,
    cacheRemote: Boolean,
    artworkCacheStore: ArtworkCacheStore,
): LynResolvedArtworkTarget? = withContext(Dispatchers.Default) {
    val normalized = normalizedArtworkCacheLocator(locator) ?: return@withContext null
    val cachedTarget = if (cacheRemote) {
        runCatching { artworkCacheStore.cache(normalized, normalized) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.takeIf { parseNavidromeCoverLocator(it) == null }
    } else {
        null
    }
    val target = cachedTarget
        ?: resolveArtworkCacheTarget(normalized)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        ?: return@withContext null
    LynResolvedArtworkTarget(
        locator = normalized,
        target = target,
        version = null,
        isLocalFile = target.startsWith("/", ignoreCase = false) ||
            target.startsWith("file://", ignoreCase = true),
    )
}
