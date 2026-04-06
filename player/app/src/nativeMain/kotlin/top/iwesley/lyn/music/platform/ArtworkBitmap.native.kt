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
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.posix.memcpy
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator
import top.iwesley.lyn.music.core.model.parseNavidromeCoverLocator

@Composable
actual fun rememberPlatformArtworkBitmap(locator: String?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, locator) {
        value = loadNativeArtworkBitmap(locator)
    }
    return bitmap
}

private suspend fun loadNativeArtworkBitmap(locator: String?): ImageBitmap? = withContext(Dispatchers.Default) {
    runCatching {
        val target = resolveArtworkTarget(locator) ?: return@runCatching null
        val payload = readArtworkBytes(target) ?: return@runCatching null
        Image.makeFromEncoded(payload).toComposeImageBitmap()
    }.getOrNull()
}

private suspend fun resolveArtworkTarget(locator: String?): String? {
    val rawTarget = normalizeArtworkLocator(locator)?.trim().orEmpty()
    if (rawTarget.isBlank()) return null
    return if (parseNavidromeCoverLocator(rawTarget) != null) {
        NavidromeLocatorRuntime.resolveCoverArtUrl(rawTarget)?.trim()?.takeIf { it.isNotBlank() }
    } else {
        rawTarget
    }
}

private suspend fun readArtworkBytes(target: String): ByteArray? {
    return when {
        target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true) ->
            readRemoteBytes(target)

        target.startsWith("file://", ignoreCase = true) ->
            readLocalBytes(NSURL.URLWithString(target)?.path ?: target.removePrefix("file://"))

        else -> readLocalBytes(target)
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
private fun NSData.toByteArray(): ByteArray {
    val byteCount = length.toInt()
    if (byteCount <= 0) return ByteArray(0)
    val byteArray = ByteArray(byteCount)
    byteArray.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return byteArray
}
