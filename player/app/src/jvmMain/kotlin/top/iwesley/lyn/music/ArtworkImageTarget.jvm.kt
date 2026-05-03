package top.iwesley.lyn.music

import java.io.File
import java.net.URI
import java.nio.file.Paths
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
): LynResolvedArtworkTarget? = withContext(Dispatchers.IO) {
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
    val file = target.toArtworkTargetFile()
    LynResolvedArtworkTarget(
        locator = normalized,
        target = target,
        version = file?.takeIf { it.isFile }?.let { "${it.length()}:${it.lastModified()}" },
        isLocalFile = file != null,
    )
}

private fun String.toArtworkTargetFile(): File? {
    val trimmed = trim()
    return when {
        trimmed.startsWith("file://", ignoreCase = true) ->
            runCatching { Paths.get(URI(trimmed)).toFile() }.getOrNull()
                ?: File(trimmed.removePrefix("file://"))

        trimmed.startsWith("/", ignoreCase = false) -> File(trimmed)
        else -> null
    }
}
