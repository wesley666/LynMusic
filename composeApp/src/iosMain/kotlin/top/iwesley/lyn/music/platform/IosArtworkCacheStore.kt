package top.iwesley.lyn.music.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
import top.iwesley.lyn.music.core.model.ArtworkCachedTarget
import top.iwesley.lyn.music.core.model.ArtworkCachedTargetRegistry
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.ArtworkCacheVersionRegistry
import top.iwesley.lyn.music.core.model.isCompleteArtworkPayload
import top.iwesley.lyn.music.core.model.isReplaceableNavidromePlaceholderArtwork

fun createIosArtworkCacheStore(): ArtworkCacheStore = IosArtworkCacheStore()

private class IosArtworkCacheStore : ArtworkCacheStore {
    private val directory: String by lazy { iosArtworkCacheDirectory() }
    private val versionRegistry = ArtworkCacheVersionRegistry()
    private val targetRegistry = ArtworkCachedTargetRegistry()

    override suspend fun cache(locator: String, cacheKey: String, replaceExisting: Boolean): String? =
        withContext(Dispatchers.Default) {
        runCatching {
            val target = resolveArtworkCacheTarget(locator) ?: return@runCatching null
            val effectiveCacheKey = cacheKey.ifBlank { locator }
            val primaryPrefix = effectiveCacheKey.stableArtworkCacheHash()
            val legacyPrefix = locator.stableArtworkCacheHash().takeIf { it != primaryPrefix }
            if (target.startsWith("file://", ignoreCase = true)) {
                val path = filePathFromIosLocator(target)
                val promoted = promoteIosLocalArtworkFile(
                    source = path,
                    cachePrefix = primaryPrefix,
                    locator = target,
                    replaceExisting = replaceExisting,
                )
                val result = rememberIosArtworkTarget(effectiveCacheKey, promoted?.path ?: path)
                promoted?.takeIf { it.changed }?.let { versionRegistry.bump(effectiveCacheKey) }
                return@runCatching result
            }
            if (!target.startsWith("http://", ignoreCase = true) && !target.startsWith("https://", ignoreCase = true)) {
                val promoted = promoteIosLocalArtworkFile(
                    source = target,
                    cachePrefix = primaryPrefix,
                    locator = target,
                    replaceExisting = replaceExisting,
                )
                val result = rememberIosArtworkTarget(effectiveCacheKey, promoted?.path ?: target)
                promoted?.takeIf { it.changed }?.let { versionRegistry.bump(effectiveCacheKey) }
                return@runCatching result
            }
            if (!replaceExisting) {
                findIosArtworkCacheFile(directory, primaryPrefix)
                    ?.let { return@runCatching rememberIosArtworkTarget(effectiveCacheKey, it) }
                legacyPrefix
                    ?.let { findIosArtworkCacheFile(directory, it) }
                    ?.let { legacy ->
                        val promoted = promoteIosArtworkCacheFile(
                            source = legacy,
                            cachePrefix = primaryPrefix,
                            replaceExisting = false,
                        )
                        val result = rememberIosArtworkTarget(effectiveCacheKey, promoted?.path ?: legacy)
                        promoted?.takeIf { it.changed }?.let { versionRegistry.bump(effectiveCacheKey) }
                        return@runCatching result
                    }
            }
            val payload = readIosRemoteBytes(target) ?: return@runCatching null
            if (!isCompleteArtworkPayload(payload)) return@runCatching null
            val fileName = "$primaryPrefix${artworkCacheExtension(target, payload)}"
            val written = writeIosArtworkCacheFileAtomically(
                directory = directory,
                fileName = fileName,
                payload = payload,
                cachePrefix = primaryPrefix,
                replaceExisting = replaceExisting,
            )
            val result = written?.path?.let { rememberIosArtworkTarget(effectiveCacheKey, it) }
            written?.takeIf { it.changed }?.let { versionRegistry.bump(effectiveCacheKey) }
            result
        }.getOrNull()
    }

    override suspend fun hasCached(cacheKey: String): Boolean = withContext(Dispatchers.Default) {
        val cachePrefix = cacheKey.ifBlank { return@withContext false }.stableArtworkCacheHash()
        val path = findIosArtworkCacheFile(directory, cachePrefix) ?: return@withContext false
        rememberIosArtworkTarget(cacheKey, path)
        true
    }

    override suspend fun hasReplaceableNavidromePlaceholderCached(cacheKey: String): Boolean =
        withContext(Dispatchers.Default) {
            val cachePrefix = cacheKey.ifBlank { return@withContext false }.stableArtworkCacheHash()
            val path = findIosArtworkCacheFile(directory, cachePrefix) ?: return@withContext false
            rememberIosArtworkTarget(cacheKey, path)
            val payload = readIosLocalBytes(path) ?: return@withContext false
            isReplaceableNavidromePlaceholderArtwork(
                bytes = payload,
                differenceHash = decodeSkiaArtworkDifferenceHash(payload),
            )
        }

    override fun observeVersion(cacheKey: String): Flow<Long> = versionRegistry.observe(cacheKey)

    override fun peekCachedTarget(cacheKey: String): ArtworkCachedTarget? {
        val cached = targetRegistry.peek(cacheKey) ?: return null
        return cached.takeIf { target ->
            !target.isLocalFile || NSFileManager.defaultManager.fileExistsAtPath(target.target)
        }
    }

    private fun rememberIosArtworkTarget(cacheKey: String, path: String): String {
        iosArtworkCachedTarget(path)?.let { target ->
            targetRegistry.put(cacheKey, target)
        }
        return path
    }
}

private fun iosArtworkCachedTarget(path: String): ArtworkCachedTarget? {
    val payload = readIosLocalBytes(path)?.takeIf(::isCompleteArtworkPayload) ?: return null
    return ArtworkCachedTarget(
        target = path,
        version = "${payload.size}:0",
        isLocalFile = true,
    )
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
): IosArtworkCacheFileResult? {
    val payload = readIosLocalBytes(source)?.takeIf(::isCompleteArtworkPayload) ?: return null
    val fileName = "$cachePrefix${artworkCacheExtension(locator, payload)}"
    return promoteIosArtworkCacheFile(source, cachePrefix, fileName, replaceExisting)
}

private fun promoteIosArtworkCacheFile(
    source: String,
    cachePrefix: String,
    replaceExisting: Boolean,
): IosArtworkCacheFileResult? {
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
): IosArtworkCacheFileResult? {
    if (!replaceExisting) {
        findIosArtworkCacheFile(iosArtworkCacheDirectory(), cachePrefix)
            ?.let { return IosArtworkCacheFileResult(it, changed = false) }
    }
    val directory = iosArtworkCacheDirectory()
    val output = "$directory/$fileName"
    if (source == output) return IosArtworkCacheFileResult(output, changed = false)
    if (replaceExisting) {
        deleteIosArtworkCacheFiles(directory, cachePrefix)
    }
    return runCatching {
        if (link(source, output) != 0) {
            val payload = readIosLocalBytes(source)?.takeIf(::isCompleteArtworkPayload) ?: return@runCatching null
            if (!writeIosFileBytes(output, payload)) return@runCatching null
        }
        output
            .takeIf { readIosLocalBytes(it)?.let { bytes -> isCompleteArtworkPayload(bytes) } == true }
            ?.let { IosArtworkCacheFileResult(it, changed = true) }
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
): IosArtworkCacheFileResult? {
    if (!isCompleteArtworkPayload(payload)) return null
    val output = "$directory/$fileName"
    if (!replaceExisting && readIosLocalBytes(output)?.let { isCompleteArtworkPayload(it) } == true) {
        return IosArtworkCacheFileResult(output, changed = false)
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
            return@runCatching IosArtworkCacheFileResult(output, changed = false)
        }
        if (replaceExisting) {
            deleteIosArtworkCacheFiles(directory, cachePrefix)
        } else {
            remove(output)
        }
        if (rename(temporary, output) != 0) {
            return@runCatching null
        }
        output
            .takeIf { readIosLocalBytes(it)?.let { bytes -> isCompleteArtworkPayload(bytes) } == true }
            ?.let { IosArtworkCacheFileResult(it, changed = true) }
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

private data class IosArtworkCacheFileResult(
    val path: String,
    val changed: Boolean,
)

private const val IOS_ARTWORK_CACHE_TEMP_MARKER = ".tmp-"
