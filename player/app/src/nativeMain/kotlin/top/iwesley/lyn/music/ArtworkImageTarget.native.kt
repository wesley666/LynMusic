package top.iwesley.lyn.music

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.isIosArtworkCacheBackedLocator
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
): LynResolvedArtworkTarget? = withContext(Dispatchers.Default) {
    val normalized = normalizedArtworkCacheLocator(locator) ?: return@withContext null
    val isIosCacheBackedLocator = isIosArtworkCacheBackedLocator(normalized)
    val cachedTarget = if (cacheRemote || isIosCacheBackedLocator) {
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
            .takeUnless { isIosCacheBackedLocator }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        ?: return@withContext null
    val localFilePath = target.toNativeArtworkTargetPath()
    LynResolvedArtworkTarget(
        locator = normalized,
        target = target,
        version = localFilePath?.let(::nativeArtworkFileVersion),
        isLocalFile = localFilePath != null,
    )
}

internal actual fun coilArtworkData(target: String): String {
    val trimmed = target.trim()
    return when {
        trimmed.startsWith("/", ignoreCase = false) -> "file://$trimmed"
        else -> trimmed
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun String.toNativeArtworkTargetPath(): String? {
    val trimmed = trim()
    return when {
        trimmed.startsWith("file://", ignoreCase = true) ->
            NSURL.URLWithString(trimmed)?.path ?: trimmed.removePrefix("file://")

        trimmed.startsWith("/", ignoreCase = false) -> trimmed
        else -> null
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun nativeArtworkFileVersion(path: String): String? = memScoped {
    val metadata = alloc<platform.posix.stat>()
    if (platform.posix.stat(path, metadata.ptr) != 0) return@memScoped null
    "${metadata.st_size}:${metadata.st_mtimespec.tv_sec}:${metadata.st_mtimespec.tv_nsec}"
}
