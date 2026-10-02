package top.iwesley.lyn.music.platform


import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import top.iwesley.lyn.music.core.model.ArtworkCacheEntry
import top.iwesley.lyn.music.core.model.ArtworkCacheBackend
import top.iwesley.lyn.music.core.model.ArtworkCacheCommit
import top.iwesley.lyn.music.core.model.CoordinatedArtworkCacheStore
import top.iwesley.lyn.music.core.model.PreparedArtwork
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.posix.closedir
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.remove
import platform.posix.rename
import top.iwesley.lyn.music.core.model.ArtworkCachedTarget
import top.iwesley.lyn.music.core.model.ArtworkCachedTargetRegistry
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.RemotePlaybackUrlCandidate
import top.iwesley.lyn.music.core.model.buildIosArtworkCacheLocator
import top.iwesley.lyn.music.core.model.isCompleteArtworkPayload
import top.iwesley.lyn.music.core.model.isReplaceableNavidromePlaceholderArtwork
import top.iwesley.lyn.music.core.model.parseIosArtworkCacheLocator
import top.iwesley.lyn.music.core.model.parseLegacyIosArtworkCacheFileName
import top.iwesley.lyn.music.domain.readRemotePlaybackUrlCandidateWithFallback

private val sharedIosArtworkCache: ArtworkCacheStore by lazy {
    CoordinatedArtworkCacheStore(IosArtworkCacheBackend(), Dispatchers.Default)
}

fun createIosArtworkCacheStore(): ArtworkCacheStore = sharedIosArtworkCache

internal fun storeIosImportedArtwork(cacheKey: String, payload: ByteArray): String? {
    if (!isCompleteArtworkPayload(payload)) return null
    val directory = iosArtworkCacheDirectory()
    val cachePrefix = cacheKey.stableArtworkCacheHash()
    val fileName = "$cachePrefix${artworkCacheExtension("embedded", payload)}"
    val written = writeIosArtworkCacheFileAtomically(
        directory = directory,
        fileName = fileName,
        payload = payload,
        cachePrefix = cachePrefix,
        replaceExisting = true,
    ) ?: return null
    return buildIosArtworkCacheLocator(written.path.substringAfterLast('/'))
}

private class IosArtworkCacheBackend : ArtworkCacheBackend {
    private val directory: String by lazy { iosArtworkCacheDirectory() }
    private val targetRegistry = ArtworkCachedTargetRegistry()
    override suspend fun prepare(locator: String, cacheKey: String, allowLegacy: Boolean): PreparedArtwork? {
        if (allowLegacy && locator != cacheKey) {
            findIosArtworkCacheFile(directory, locator.stableArtworkCacheHash())?.let { (legacy, bytes) ->
                return PreparedArtwork(legacy, bytes, sourcePath = legacy)
            }
        }
        val targets = resolveArtworkCacheTargets(locator)
        val target = targets.firstOrNull()?.value ?: return null
        if (!isRemoteArtworkTarget(target)) {
            val path = resolveIosLocalArtworkSourcePath(target, directory) ?: return null
            val bytes = readIosLocalBytes(path) ?: return null
            return PreparedArtwork(target, bytes, sourcePath = path)
        }
        val (remote, bytes) = readIosRemoteArtworkPayload(targets) ?: return null
        return PreparedArtwork(remote.value, bytes, remoteTarget = remote)
    }

    override suspend fun commit(cacheKey: String, artwork: PreparedArtwork, replaceExisting: Boolean): ArtworkCacheCommit? {
        val prefix = cacheKey.stableArtworkCacheHash()
        val fileName = "$prefix${artworkCacheExtension(artwork.locator, artwork.bytes)}"
        val context = currentCoroutineContext()
        val written = if (artwork.sourcePath == "${iosArtworkCacheDirectory()}/$fileName") {
            IosArtworkCacheFileResult(artwork.sourcePath!!, changed = false)
        } else {
            writeIosArtworkCacheFileAtomically(
                directory = iosArtworkCacheDirectory(), fileName = fileName,
                payload = artwork.bytes, cachePrefix = prefix, replaceExisting = replaceExisting,
                renameFile = { source, target -> context.ensureActive(); rename(source, target) },
            )
        } ?: return null
        val result = rememberIosArtworkTarget(cacheKey, written.path) ?: return null
        artwork.remoteTarget?.let { NavidromeLocatorRuntime.markResolvedUrlSuccess(it) }
        return ArtworkCacheCommit(result, written.changed)
    }

    override suspend fun find(cacheKey: String, detectPlaceholder: Boolean): ArtworkCacheEntry? {
        val (path, payload) = findIosArtworkCacheFile(directory, cacheKey.stableArtworkCacheHash()) ?: return null
        targetRegistry.put(cacheKey, ArtworkCachedTarget(path, iosArtworkFileVersion(path), true))
        val placeholder = detectPlaceholder && isReplaceableNavidromePlaceholderArtwork(
            bytes = payload,
            differenceHash = decodeSkiaArtworkDifferenceHash(payload),
        )
        return ArtworkCacheEntry(path, placeholder)
    }

    override fun peek(cacheKey: String): ArtworkCachedTarget? {
        val cached = targetRegistry.peek(cacheKey) ?: return null
        return cached.takeIf { target ->
            !target.isLocalFile || NSFileManager.defaultManager.fileExistsAtPath(target.target)
        }
    }

    private fun rememberIosArtworkTarget(cacheKey: String, path: String): String? {
        val target = iosArtworkCachedTarget(path) ?: return null
        targetRegistry.put(cacheKey, target)
        return path
    }
}

private fun resolveIosLocalArtworkSourcePath(target: String, directory: String): String? {
    parseIosArtworkCacheLocator(target)?.let { fileName ->
        return validIosArtworkPath("$directory/$fileName")
    }
    val directPath = if (target.startsWith("file://", ignoreCase = true)) {
        filePathFromIosLocator(target)
    } else {
        target
    }
    validIosArtworkPath(directPath)?.let { return it }
    return relocateLegacyIosArtworkPath(directPath, directory)
}

private fun relocateLegacyIosArtworkPath(path: String, directory: String): String? {
    val safeFileName = parseLegacyIosArtworkCacheFileName(path) ?: return null
    return validIosArtworkPath("$directory/$safeFileName")
}

private fun validIosArtworkPath(path: String): String? {
    return path.takeIf { readIosLocalBytes(it)?.let(::isCompleteArtworkPayload) == true }
}

private suspend fun readIosRemoteArtworkPayload(
    targets: List<RemotePlaybackUrlCandidate>,
): Pair<RemotePlaybackUrlCandidate, ByteArray>? {
    return readRemotePlaybackUrlCandidateWithFallback(
        candidates = targets,
        isRemoteUrl = ::isRemoteArtworkTarget,
        read = { target -> readIosRemoteBytesOrThrow(target.value) },
        isValidPayload = ::isCompleteArtworkPayload,
    )
}

private fun iosArtworkCachedTarget(path: String): ArtworkCachedTarget? {
    readIosLocalBytes(path)?.takeIf(::isCompleteArtworkPayload) ?: return null
    return ArtworkCachedTarget(
        target = path,
        version = iosArtworkFileVersion(path),
        isLocalFile = true,
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun iosArtworkFileVersion(path: String): String? = memScoped {
    val metadata = alloc<platform.posix.stat>()
    if (platform.posix.stat(path, metadata.ptr) != 0) return@memScoped null
    "${metadata.st_size}:${metadata.st_mtimespec.tv_sec}:${metadata.st_mtimespec.tv_nsec}"
}

@OptIn(ExperimentalForeignApi::class)
private fun findIosArtworkCacheFile(directory: String, cachePrefix: String): Pair<String, ByteArray>? {
    val handle = opendir(directory) ?: return null
    return try {
        while (true) {
            val entry = readdir(handle)?.pointed ?: break
            val name = entry.d_name.toKString()
            if (name == "." || name == "..") continue
            if (!name.startsWith(cachePrefix)) continue
            if (name.contains(IOS_ARTWORK_CACHE_TEMP_MARKER)) continue
            val path = "$directory/$name"
            val payload = readIosLocalBytes(path)
            if (payload != null && isCompleteArtworkPayload(payload)) {
                return path to payload
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

internal fun writeIosArtworkCacheFileAtomically(
    directory: String,
    fileName: String,
    payload: ByteArray,
    cachePrefix: String,
    replaceExisting: Boolean,
    renameFile: (source: String, target: String) -> Int = { source, target -> rename(source, target) },
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
        if (renameFile(temporary, output) != 0) {
            return@runCatching null
        }
        deleteIosArtworkCacheFilesExcept(directory, cachePrefix, fileName)
        output
            .takeIf { validIosArtworkPath(it) != null }
            ?.let { IosArtworkCacheFileResult(it, changed = true) }
    }.also {
        remove(temporary)
    }.getOrNull()
}

@OptIn(ExperimentalForeignApi::class)
private fun deleteIosArtworkCacheFilesExcept(
    directory: String,
    cachePrefix: String,
    retainedFileName: String,
) {
    val handle = opendir(directory) ?: return
    try {
        while (true) {
            val entry = readdir(handle)?.pointed ?: break
            val name = entry.d_name.toKString()
            if (name == "." || name == "..") continue
            if (!name.startsWith(cachePrefix)) continue
            if (name.contains(IOS_ARTWORK_CACHE_TEMP_MARKER)) continue
            if (name == retainedFileName) continue
            remove("$directory/$name")
        }
    } finally {
        closedir(handle)
    }
}

internal data class IosArtworkCacheFileResult(
    val path: String,
    val changed: Boolean,
)

private fun isRemoteArtworkTarget(target: String): Boolean {
    return target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)
}

private const val IOS_ARTWORK_CACHE_TEMP_MARKER = ".tmp-"
