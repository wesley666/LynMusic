package top.iwesley.lyn.music.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.posix.closedir
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.remove
import platform.posix.rename
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.isCompleteArtworkPayload

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
            val cachePrefix = cacheKey.stableArtworkCacheHash()
            findIosArtworkCacheFile(directory, cachePrefix)?.let { return@runCatching it }
            val payload = readIosRemoteBytes(target) ?: return@runCatching null
            if (!isCompleteArtworkPayload(payload)) return@runCatching null
            val fileName = "$cachePrefix${artworkCacheExtension(target, payload)}"
            writeIosArtworkCacheFileAtomically(directory, fileName, payload)
        }.getOrNull()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun findIosArtworkCacheFile(directory: String, cachePrefix: String): String? {
    val handle = opendir(directory) ?: return null
    return try {
        while (true) {
            val entry = readdir(handle)?.pointed ?: break
            val name = entry.d_name.toKString()
            if (name == "." || name == "..") continue
            if (!name.startsWith(cachePrefix)) continue
            if (name.contains(IOS_ARTWORK_CACHE_TEMP_MARKER)) continue
            val path = "$directory/$name"
            val valid = readIosLocalBytes(path)?.let { isCompleteArtworkPayload(it) } == true
            if (valid) {
                return path
            }
            remove(path)
        }
        null
    } finally {
        closedir(handle)
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

private fun writeIosArtworkCacheFileAtomically(
    directory: String,
    fileName: String,
    payload: ByteArray,
): String? {
    if (!isCompleteArtworkPayload(payload)) return null
    val output = "$directory/$fileName"
    if (readIosLocalBytes(output)?.let { isCompleteArtworkPayload(it) } == true) {
        return output
    }
    remove(output)
    val temporary = "$output$IOS_ARTWORK_CACHE_TEMP_MARKER${platform.Foundation.NSUUID.UUID().UUIDString}"
    return runCatching {
        if (!writeIosFileBytes(temporary, payload)) {
            return@runCatching null
        }
        val written = readIosLocalBytes(temporary) ?: return@runCatching null
        if (written.size != payload.size || !isCompleteArtworkPayload(written)) {
            return@runCatching null
        }
        if (readIosLocalBytes(output)?.let { isCompleteArtworkPayload(it) } == true) {
            return@runCatching output
        }
        remove(output)
        if (rename(temporary, output) != 0) {
            return@runCatching null
        }
        output.takeIf { readIosLocalBytes(it)?.let { bytes -> isCompleteArtworkPayload(bytes) } == true }
    }.also {
        remove(temporary)
    }.getOrNull()
}

private const val IOS_ARTWORK_CACHE_TEMP_MARKER = ".tmp-"
