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
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.normalizedArtworkCacheLocator
import top.iwesley.lyn.music.core.model.resolveArtworkCacheTarget
import top.iwesley.lyn.music.core.model.stableArtworkCacheHash

@Composable
actual fun rememberPlatformArtworkBitmap(locator: String?, cacheRemote: Boolean): ImageBitmap? {
    val context = LocalContext.current
    val fallbackBitmap = rememberBundledDefaultCoverBitmap()
    if (locator.isNullOrBlank()) return fallbackBitmap
    val bitmap by produceState<ImageBitmap?>(initialValue = fallbackBitmap, locator, cacheRemote, fallbackBitmap) {
        value = loadAndroidArtworkBitmap(context, locator, cacheRemote)
    }
    return bitmap ?: fallbackBitmap
}

private suspend fun loadAndroidArtworkBitmap(
    context: Context,
    locator: String?,
    cacheRemote: Boolean,
): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val normalizedLocator = normalizedArtworkCacheLocator(locator) ?: return@runCatching null
        val target = resolveArtworkCacheTarget(normalizedLocator) ?: return@runCatching null
        val bitmap = when {
            isRemoteArtworkTarget(target) -> {
                val cacheDirectory = File(context.cacheDir, "artwork-cache").apply { mkdirs() }
                val cacheKey = normalizedLocator.stableArtworkCacheHash()
                val existingCacheFile = cacheDirectory
                    .listFiles()
                    ?.firstOrNull { file -> file.isFile && file.name.startsWith(cacheKey) && file.length() > 0L }
                if (existingCacheFile != null) {
                    BitmapFactory.decodeFile(existingCacheFile.absolutePath)
                } else {
                    val payload = URL(target).openStream().use { it.readBytes() }
                    if (payload.isEmpty()) return@runCatching null
                    if (cacheRemote) {
                        val cacheFile = cacheDirectory.resolve(
                            "$cacheKey${inferArtworkFileExtension(locator = target, bytes = payload)}",
                        )
                        runCatching { cacheFile.writeBytes(payload) }
                    }
                    BitmapFactory.decodeByteArray(payload, 0, payload.size)
                }
            }
            target.startsWith("file://", ignoreCase = true) ->
                BitmapFactory.decodeFile(runCatching { File(URI(target)).absolutePath }.getOrElse { target.removePrefix("file://") })

            else -> BitmapFactory.decodeFile(target)
        }
        bitmap?.asImageBitmap()
    }.getOrNull()
}

private fun isRemoteArtworkTarget(target: String): Boolean {
    return target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)
}
