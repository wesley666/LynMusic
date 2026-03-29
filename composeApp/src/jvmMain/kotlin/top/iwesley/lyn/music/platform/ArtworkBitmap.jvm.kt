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
    val rawTarget = normalizeArtworkLocator(locator)?.trim().orEmpty()
    if (rawTarget.isBlank()) return@withContext null
    val target = if (parseNavidromeCoverLocator(rawTarget) != null) {
        val actualUrl = NavidromeLocatorRuntime.resolveCoverArtUrl(rawTarget).orEmpty()
        if (actualUrl.isBlank()) return@withContext null
        val cacheFile = File(File(System.getProperty("user.home")), ".lynmusic/artwork-cache").apply { mkdirs() }
            .resolve("${rawTarget.stableHash()}${artworkExtension(actualUrl)}")
        if (!cacheFile.exists() || cacheFile.length() <= 0L) {
            URL(actualUrl).openStream().use { input ->
                Files.copy(input, cacheFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }
        cacheFile.absolutePath
    } else {
        rawTarget
    }
    if (target.isBlank()) return@withContext null
    runCatching {
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

private fun artworkExtension(locator: String): String {
    val path = runCatching { URI(locator).path }.getOrNull().orEmpty()
    val extension = path.substringAfterLast('.', "").lowercase()
    return if (extension in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")) ".$extension" else ".img"
}

private fun String.stableHash(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
