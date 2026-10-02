package top.iwesley.lyn.music

import java.io.File
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.normalizedArtworkCacheLocator
import top.iwesley.lyn.music.core.model.parseEmbyCoverLocator
import top.iwesley.lyn.music.core.model.parseSubsonicCompatibleCoverLocator
import top.iwesley.lyn.music.core.model.resolveArtworkCacheTarget

internal actual suspend fun resolveLynArtworkTarget(
    locator: String?,
    cacheKey: String?,
    cacheRemote: Boolean,
    artworkCacheStore: ArtworkCacheStore,
): LynResolvedArtworkTarget? = withContext(Dispatchers.IO) {
    val normalized = normalizedArtworkCacheLocator(locator) ?: return@withContext null
    val cachedTarget = if (cacheRemote) {
        runCatching { artworkCacheStore.cache(normalized, cacheKey ?: normalized)?.locator }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.takeIf { parseSubsonicCompatibleCoverLocator(it) == null && parseEmbyCoverLocator(it) == null }
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

internal actual fun coilArtworkData(target: String): String {
    val trimmed = target.trim()
    return when {
        trimmed.startsWith("/", ignoreCase = false) -> "file://$trimmed"
        else -> trimmed
    }
}

private fun String.toArtworkTargetFile(): File? {
    val trimmed = trim()
    return when {
        trimmed.startsWith("file://", ignoreCase = true) ->
            runCatching { File(URI(trimmed)) }.getOrNull()
                ?: File(trimmed.removePrefix("file://"))

        trimmed.startsWith("/", ignoreCase = false) -> File(trimmed)
        else -> null
    }
}
