package top.iwesley.lyn.music.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import top.iwesley.lyn.music.core.model.ArtworkCacheStore

fun createIosArtworkCacheStore(): ArtworkCacheStore = IosArtworkCacheStore()

private class IosArtworkCacheStore : ArtworkCacheStore {
    private val directory: String by lazy { iosArtworkCacheDirectory() }

    override suspend fun cache(locator: String, cacheKey: String): String? = withContext(Dispatchers.Default) {
        runCatching {
            val target = resolveArtworkCacheTarget(locator) ?: return@runCatching null
            if (target.startsWith("file://", ignoreCase = true)) {
                return@runCatching filePathFromIosLocator(target)
            }
            if (!target.startsWith("http://", ignoreCase = true) && !target.startsWith("https://", ignoreCase = true)) {
                return@runCatching target
            }
            val payload = readIosRemoteBytes(target) ?: return@runCatching null
            val output = "$directory/${cacheKey.stableArtworkCacheHash()}${artworkCacheExtension(target, payload)}"
            if (readIosLocalBytes(output)?.isNotEmpty() == true) {
                return@runCatching output
            }
            if (!writeIosFileBytes(output, payload)) {
                return@runCatching null
            }
            output.takeIf { readIosLocalBytes(it)?.isNotEmpty() == true }
        }.getOrNull()
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun iosArtworkCacheDirectory(): String {
    val cachesUrl: NSURL = requireNotNull(
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSCachesDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ),
    )
    val directory = requireNotNull(cachesUrl.path) + "/lynmusic-artwork-cache"
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = directory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return directory
}
