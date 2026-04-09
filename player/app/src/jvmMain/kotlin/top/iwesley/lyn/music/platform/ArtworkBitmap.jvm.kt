package top.iwesley.lyn.music.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import org.jetbrains.skia.Image
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator
import top.iwesley.lyn.music.core.model.parseNavidromeCoverLocator

@Composable
actual fun rememberPlatformArtworkBitmap(locator: String?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, locator) {
        value = loadJvmArtworkBitmap(locator)
    }
    return bitmap
}

private suspend fun loadJvmArtworkBitmap(locator: String?): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val rawTarget = normalizeArtworkLocator(locator)?.trim().orEmpty()
        if (rawTarget.isBlank()) return@runCatching null
        val target = if (parseNavidromeCoverLocator(rawTarget) != null) {
            val actualUrl = NavidromeLocatorRuntime.resolveCoverArtUrl(rawTarget).orEmpty()
            if (actualUrl.isBlank()) return@runCatching null
            val cacheDirectory = File(File(System.getProperty("user.home")), ".lynmusic/artwork-cache").apply { mkdirs() }
            val cacheKey = rawTarget.stableHash()
            val existingCacheFile = cacheDirectory
                .listFiles()
                ?.firstOrNull { file -> file.isFile && file.name.startsWith(cacheKey) && file.length() > 0L }
            if (existingCacheFile != null) {
                existingCacheFile.absolutePath
            } else {
                val payload = URL(actualUrl).openStream().use { it.readBytes() }
                if (payload.isEmpty()) return@runCatching null
                val cacheFile = cacheDirectory.resolve(
                    "$cacheKey${inferArtworkFileExtension(locator = actualUrl, bytes = payload)}",
                )
                Files.write(cacheFile.toPath(), payload)
                cacheFile.absolutePath
            }
        } else {
            rawTarget
        }
        if (target.isBlank()) return@runCatching null
        val bytes = when {
            target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true) ->
                URL(target).openStream().use { it.readBytes() }

            target.startsWith("file://", ignoreCase = true) ->
                Files.readAllBytes(Paths.get(URI(target)))

            else -> Files.readAllBytes(Paths.get(target))
        }
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
}

private fun String.stableHash(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
