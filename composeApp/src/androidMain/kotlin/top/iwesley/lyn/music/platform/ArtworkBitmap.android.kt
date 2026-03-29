package top.iwesley.lyn.music.platform

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator
import top.iwesley.lyn.music.core.model.parseNavidromeCoverLocator

@Composable
actual fun rememberPlatformArtworkBitmap(locator: String?): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, locator) {
        value = loadAndroidArtworkBitmap(context, locator)
    }
    return bitmap
}

private suspend fun loadAndroidArtworkBitmap(context: Context, locator: String?): ImageBitmap? = withContext(Dispatchers.IO) {
    val rawTarget = normalizeArtworkLocator(locator)?.trim().orEmpty()
    if (rawTarget.isBlank()) return@withContext null
    val target = if (parseNavidromeCoverLocator(rawTarget) != null) {
        val actualUrl = NavidromeLocatorRuntime.resolveCoverArtUrl(rawTarget).orEmpty()
        if (actualUrl.isBlank()) return@withContext null
        val cacheFile = File(context.cacheDir, "artwork-cache").apply { mkdirs() }
            .resolve("${rawTarget.stableHash()}${artworkExtension(actualUrl)}")
        if (!cacheFile.exists() || cacheFile.length() <= 0L) {
            URL(actualUrl).openStream().use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        cacheFile.absolutePath
    } else {
        rawTarget
    }
    if (target.isBlank()) return@withContext null
    runCatching {
        val bitmap = when {
            target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true) ->
                URL(target).openStream().use { BitmapFactory.decodeStream(it) }

            target.startsWith("file://", ignoreCase = true) ->
                BitmapFactory.decodeFile(target.removePrefix("file://"))

            else -> BitmapFactory.decodeFile(target)
        }
        bitmap?.asImageBitmap()
    }.getOrNull()
}

private fun artworkExtension(locator: String): String {
    val path = runCatching { java.net.URI(locator).path }.getOrNull().orEmpty()
    val extension = path.substringAfterLast('.', "").lowercase()
    return if (extension in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")) ".$extension" else ".img"
}

private fun String.stableHash(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
