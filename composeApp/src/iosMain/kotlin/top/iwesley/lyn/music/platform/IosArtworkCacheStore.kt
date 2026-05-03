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
import platform.posix.link
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.remove
import platform.posix.rename
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.isCompleteArtworkPayload

fun createIosArtworkCacheStore(): ArtworkCacheStore = IosArtworkCacheStore()

private class IosArtworkCacheStore : ArtworkCacheStore {
    private val directory: String by lazy { iosArtworkCacheDirectory() }

    override suspend fun cache(locator: String, cacheKey: String, replaceExisting: Boolean): String? =
        withContext(Dispatchers.Default) {
        runCatching {
            val target = resolveArtworkCacheTarget(locator) ?: return@runCatching null
            val primaryPrefix = cacheKey.ifBlank { locator }.stableArtworkCacheHash()
            val legacyPrefix = locator.stableArtworkCacheHash().takeIf { it != primaryPrefix }
            if (target.startsWith("file://", ignoreCase = true)) {
                val path = filePathFromIosLocator(target)
                return@runCatching promoteIosLocalArtworkFile(
                    source = path,
                    cachePrefix = primaryPrefix,
                    locator = target,
                    replaceExisting = replaceExisting,
                ) ?: path
            }
            if (!target.startsWith("http://", ignoreCase = true) && !target.startsWith("https://", ignoreCase = true)) {
                return@runCatching promoteIosLocalArtworkFile(
                    source = target,
                    cachePrefix = primaryPrefix,
                    locator = target,
                    replaceExisting = replaceExisting,
                ) ?: target
            }
            if (!replaceExisting) {
                findIosArtworkCacheFile(directory, primaryPrefix)?.let { return@runCatching it }
                legacyPrefix
                    ?.let { findIosArtworkCacheFile(directory, it) }
                    ?.let { legacy ->
                        return@runCatching promoteIosArtworkCacheFile(
                            source = legacy,
                            cachePrefix = primaryPrefix,
                            replaceExisting = false,
                        ) ?: legacy
                    }
            }
            val payload = readIosRemoteBytes(target) ?: return@runCatching null
            if (!isCompleteArtworkPayload(payload)) return@runCatching null
            val fileName = "$primaryPrefix${artworkCacheExtension(target, payload)}"
            writeIosArtworkCacheFileAtomically(
                directory = directory,
                fileName = fileName,
                payload = payload,
                cachePrefix = primaryPrefix,
                replaceExisting = replaceExisting,
            )
        }.getOrNull()
    }

    override suspend fun hasCached(cacheKey: String): Boolean = withContext(Dispatchers.Default) {
        val cachePrefix = cacheKey.ifBlank { return@withContext false }.stableArtworkCacheHash()
        findIosArtworkCacheFile(directory, cachePrefix) != null
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

private fun promoteIosLocalArtworkFile(
    source: String,
    cachePrefix: String,
    locator: String,
    replaceExisting: Boolean,
): String? {
    val payload = readIosLocalBytes(source)?.takeIf(::isCompleteArtworkPayload) ?: return null
    val fileName = "$cachePrefix${artworkCacheExtension(locator, payload)}"
    return promoteIosArtworkCacheFile(source, cachePrefix, fileName, replaceExisting)
}

private fun promoteIosArtworkCacheFile(
    source: String,
    cachePrefix: String,
    replaceExisting: Boolean,
): String? {
    val name = source.substringAfterLast('/')
    val extension = name.substringAfterLast('.', "")
        .takeIf { it.isNotBlank() }
        ?.let { ".$it" }
        ?: ".img"
    return promoteIosArtworkCacheFile(source, cachePrefix, "$cachePrefix$extension", replaceExisting)
}

private fun promoteIosArtworkCacheFile(
    source: String,
    cachePrefix: String,
    fileName: String,
    replaceExisting: Boolean,
): String? {
    if (!replaceExisting) {
        findIosArtworkCacheFile(iosArtworkCacheDirectory(), cachePrefix)?.let { return it }
    }
    val directory = iosArtworkCacheDirectory()
    val output = "$directory/$fileName"
    if (replaceExisting) {
        deleteIosArtworkCacheFiles(directory, cachePrefix)
    }
    if (source == output) return output
    return runCatching {
        if (link(source, output) != 0) {
            val payload = readIosLocalBytes(source)?.takeIf(::isCompleteArtworkPayload) ?: return@runCatching null
            if (!writeIosFileBytes(output, payload)) return@runCatching null
        }
        output.takeIf { readIosLocalBytes(it)?.let { bytes -> isCompleteArtworkPayload(bytes) } == true }
    }.getOrNull()
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
    cachePrefix: String,
    replaceExisting: Boolean,
): String? {
    if (!isCompleteArtworkPayload(payload)) return null
    val output = "$directory/$fileName"
    if (!replaceExisting && readIosLocalBytes(output)?.let { isCompleteArtworkPayload(it) } == true) {
        return output
    }
    val temporary = "$output$IOS_ARTWORK_CACHE_TEMP_MARKER${platform.Foundation.NSUUID.UUID().UUIDString}"
    return runCatching {
        if (!writeIosFileBytes(temporary, payload)) {
            return@runCatching null
        }
        val written = readIosLocalBytes(temporary) ?: return@runCatching null
        if (written.size != payload.size || !isCompleteArtworkPayload(written)) {
            return@runCatching null
        }
        if (!replaceExisting && readIosLocalBytes(output)?.let { isCompleteArtworkPayload(it) } == true) {
            return@runCatching output
        }
        if (replaceExisting) {
            deleteIosArtworkCacheFiles(directory, cachePrefix)
        } else {
            remove(output)
        }
        if (rename(temporary, output) != 0) {
            return@runCatching null
        }
        output.takeIf { readIosLocalBytes(it)?.let { bytes -> isCompleteArtworkPayload(bytes) } == true }
    }.also {
        remove(temporary)
    }.getOrNull()
}

@OptIn(ExperimentalForeignApi::class)
private fun deleteIosArtworkCacheFiles(directory: String, cachePrefix: String) {
    val handle = opendir(directory) ?: return
    try {
        while (true) {
            val entry = readdir(handle)?.pointed ?: break
            val name = entry.d_name.toKString()
            if (name == "." || name == "..") continue
            if (!name.startsWith(cachePrefix)) continue
            if (name.contains(IOS_ARTWORK_CACHE_TEMP_MARKER)) continue
            remove("$directory/$name")
        }
    } finally {
        closedir(handle)
    }
}

private const val IOS_ARTWORK_CACHE_TEMP_MARKER = ".tmp-"
