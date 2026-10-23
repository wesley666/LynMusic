package top.iwesley.lyn.music

import java.io.File
import java.net.URI
import java.nio.file.Paths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.normalizedArtworkCacheLocator
import top.iwesley.lyn.music.core.model.parseEmbyCoverLocator
import top.iwesley.lyn.music.core.model.parseFnMusicCoverLocator
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
            ?.takeIf { parseSubsonicCompatibleCoverLocator(it) == null && parseEmbyCoverLocator(it) == null && parseFnMusicCoverLocator(it) == null }
    } else {
        null
    }
    // Runs inside a composition's produceState: a failing resolver must cost the image, never crash the app.
    val target = cachedTarget
        ?: runCatching { resolveArtworkCacheTarget(normalized) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        ?: return@withContext null
    val file = target.toJvmArtworkTargetFile()
    LynResolvedArtworkTarget(
        locator = normalized,
        target = target,
        version = file?.takeIf { it.isFile }?.let { "${it.length()}:${it.lastModified()}" },
        isLocalFile = file != null,
    )
}

internal actual fun coilArtworkData(target: String): String {
    val trimmed = target.trim()
    return trimmed.toJvmArtworkTargetFile()
        ?.toPath()
        ?.toUri()
        ?.toString()
        ?: trimmed
}

internal fun String.toJvmArtworkTargetFile(): File? {
    val trimmed = trim()
    return when {
        trimmed.startsWith("file:", ignoreCase = true) ->
            runCatching { Paths.get(URI(trimmed)).toFile() }.getOrNull()
                ?: trimmed.legacyFileUrlPath()?.toAbsoluteJvmArtworkFile()

        else -> trimmed.toAbsoluteJvmArtworkFile()
    }
}

private fun String.legacyFileUrlPath(): String? {
    return when {
        startsWith("file://", ignoreCase = true) -> substring("file://".length)
        startsWith("file:", ignoreCase = true) -> substring("file:".length)
        else -> null
    }
}

private fun String.toAbsoluteJvmArtworkFile(): File? {
    return runCatching { Paths.get(this) }
        .getOrNull()
        ?.takeIf { path -> path.isAbsolute }
        ?.toFile()
}
