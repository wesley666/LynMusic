package top.iwesley.lyn.music.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.closedir
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.memcpy
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.remove
import platform.posix.rename
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.isCompleteArtworkPayload
import top.iwesley.lyn.music.core.model.normalizedArtworkCacheLocator
import top.iwesley.lyn.music.core.model.resolveArtworkCacheTarget
import top.iwesley.lyn.music.core.model.stableArtworkCacheHash

@Composable
actual fun rememberPlatformArtworkBitmap(locator: String?, cacheRemote: Boolean): ImageBitmap? {
    val fallbackBitmap = rememberBundledDefaultCoverBitmap()
    if (locator.isNullOrBlank()) return fallbackBitmap
    val bitmap by produceState<ImageBitmap?>(initialValue = fallbackBitmap, locator, cacheRemote, fallbackBitmap) {
        value = loadNativeArtworkBitmap(locator, cacheRemote)
    }
    return bitmap ?: fallbackBitmap
}

private suspend fun loadNativeArtworkBitmap(locator: String?, cacheRemote: Boolean): ImageBitmap? = withContext(Dispatchers.Default) {
    runCatching {
        val payload = loadNativeArtworkBytes(locator, cacheRemote) ?: return@runCatching null
        Image.makeFromEncoded(payload).toComposeImageBitmap()
    }.getOrNull()
}

private suspend fun loadNativeArtworkBytes(locator: String?, cacheRemote: Boolean): ByteArray? = withContext(Dispatchers.Default) {
    runCatching {
        val normalizedLocator = normalizedArtworkCacheLocator(locator) ?: return@runCatching null
        val target = resolveArtworkCacheTarget(normalizedLocator) ?: return@runCatching null
        when {
            isRemoteArtworkTarget(target) -> {
                val cacheDirectory = nativeArtworkCacheDirectory()
                val cachePrefix = normalizedLocator.stableArtworkCacheHash()
                val existingCachePath = findNativeArtworkCachePath(cacheDirectory, cachePrefix)
                if (existingCachePath != null) {
                    readLocalBytes(existingCachePath)
                } else {
                    val payload = readRemoteBytes(target)
                    if (payload == null || !isCompleteArtworkPayload(payload)) return@runCatching null
                    if (cacheRemote) {
                        writeNativeArtworkCacheFileAtomically(
                            directory = cacheDirectory,
                            fileName = "$cachePrefix${inferArtworkFileExtension(locator = target, bytes = payload)}",
                            payload = payload,
                        )
                    }
                    payload
                }
            }

            target.startsWith("file://", ignoreCase = true) ->
                readLocalBytes(NSURL.URLWithString(target)?.path ?: target.removePrefix("file://"))

            else -> readLocalBytes(target)
        }
    }.getOrNull()
}

@OptIn(ExperimentalForeignApi::class)
private fun nativeArtworkCacheDirectory(): String {
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

@OptIn(ExperimentalForeignApi::class)
private fun findNativeArtworkCachePath(directory: String, cachePrefix: String): String? {
    val handle = opendir(directory) ?: return null
    return try {
        while (true) {
            val entry = readdir(handle)?.pointed ?: break
            val name = entry.d_name.toKString()
            if (name == "." || name == "..") continue
            if (!name.startsWith(cachePrefix)) continue
            if (name.contains(NATIVE_ARTWORK_CACHE_TEMP_MARKER)) continue
            val path = "$directory/$name"
            val valid = readLocalBytes(path)?.let { isCompleteArtworkPayload(it) } == true
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

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private suspend fun readRemoteBytes(target: String): ByteArray? {
    val url = NSURL.URLWithString(target) ?: return null
    return NSData.create(contentsOfURL = url, options = 0u, error = null)?.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun readLocalBytes(path: String): ByteArray? {
    val file = fopen(path, "rb") ?: return null
    return try {
        if (fseek(file, 0, SEEK_END) != 0) return null
        val byteCount = ftell(file).toInt()
        if (byteCount < 0) return null
        if (fseek(file, 0, SEEK_SET) != 0) return null
        val byteArray = ByteArray(byteCount)
        val bytesRead = byteArray.usePinned { pinned ->
            fread(
                pinned.addressOf(0).reinterpret<ByteVar>(),
                1.convert(),
                byteCount.convert(),
                file,
            ).toInt()
        }
        if (bytesRead != byteCount) return null
        byteArray
    } finally {
        fclose(file)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun writeLocalBytes(path: String, bytes: ByteArray): Boolean {
    val file = fopen(path, "wb") ?: return false
    return try {
        val written = bytes.usePinned { pinned ->
            fwrite(
                pinned.addressOf(0),
                1.convert(),
                bytes.size.convert(),
                file,
            ).toInt()
        }
        written == bytes.size
    } finally {
        fclose(file)
    }
}

private fun isRemoteArtworkTarget(target: String): Boolean {
    return target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)
}

private fun writeNativeArtworkCacheFileAtomically(
    directory: String,
    fileName: String,
    payload: ByteArray,
): String? {
    if (!isCompleteArtworkPayload(payload)) return null
    val output = "$directory/$fileName"
    if (readLocalBytes(output)?.let { isCompleteArtworkPayload(it) } == true) {
        return output
    }
    remove(output)
    val temporary = "$output$NATIVE_ARTWORK_CACHE_TEMP_MARKER${NSUUID.UUID().UUIDString}"
    return runCatching {
        if (!writeLocalBytes(temporary, payload)) {
            return@runCatching null
        }
        val written = readLocalBytes(temporary) ?: return@runCatching null
        if (written.size != payload.size || !isCompleteArtworkPayload(written)) {
            return@runCatching null
        }
        if (readLocalBytes(output)?.let { isCompleteArtworkPayload(it) } == true) {
            return@runCatching output
        }
        remove(output)
        if (rename(temporary, output) != 0) {
            return@runCatching null
        }
        output.takeIf { readLocalBytes(it)?.let { bytes -> isCompleteArtworkPayload(bytes) } == true }
    }.also {
        remove(temporary)
    }.getOrNull()
}

private const val NATIVE_ARTWORK_CACHE_TEMP_MARKER = ".tmp-"

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val byteCount = length.toInt()
    if (byteCount <= 0) return ByteArray(0)
    val byteArray = ByteArray(byteCount)
    byteArray.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return byteArray
}
