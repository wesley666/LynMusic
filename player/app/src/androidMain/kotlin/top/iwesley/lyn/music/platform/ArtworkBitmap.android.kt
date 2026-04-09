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
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
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
    runCatching {
        val rawTarget = normalizeArtworkLocator(locator)?.trim().orEmpty()
        if (rawTarget.isBlank()) return@runCatching null
        val target = if (parseNavidromeCoverLocator(rawTarget) != null) {
            val actualUrl = NavidromeLocatorRuntime.resolveCoverArtUrl(rawTarget).orEmpty()
            if (actualUrl.isBlank()) return@runCatching null
            val cacheDirectory = File(context.cacheDir, "artwork-cache").apply { mkdirs() }
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
                cacheFile.writeBytes(payload)
                cacheFile.absolutePath
            }
        } else {
            rawTarget
        }
        if (target.isBlank()) return@runCatching null
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

private fun String.stableHash(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
