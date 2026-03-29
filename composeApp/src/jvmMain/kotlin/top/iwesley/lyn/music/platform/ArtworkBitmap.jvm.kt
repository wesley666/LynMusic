package top.iwesley.lyn.music.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator

@Composable
actual fun rememberPlatformArtworkBitmap(locator: String?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, locator) {
        value = loadJvmArtworkBitmap(locator)
    }
    return bitmap
}

private suspend fun loadJvmArtworkBitmap(locator: String?): ImageBitmap? = withContext(Dispatchers.IO) {
    val target = normalizeArtworkLocator(locator)?.trim().orEmpty()
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
