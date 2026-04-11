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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.normalizedArtworkCacheLocator
import top.iwesley.lyn.music.core.model.resolveArtworkCacheTarget
import top.iwesley.lyn.music.core.model.stableArtworkCacheHash

@Composable
actual fun rememberPlatformArtworkBitmap(locator: String?, cacheRemote: Boolean): ImageBitmap? {
    val fallbackBitmap = rememberBundledDefaultCoverBitmap()
    if (locator.isNullOrBlank()) return fallbackBitmap
    val bitmap by produceState<ImageBitmap?>(initialValue = fallbackBitmap, locator, cacheRemote, fallbackBitmap) {
        value = loadJvmArtworkBitmap(locator, cacheRemote)
    }
    return bitmap ?: fallbackBitmap
}

private suspend fun loadJvmArtworkBitmap(locator: String?, cacheRemote: Boolean): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bytes = loadJvmArtworkBytes(locator, cacheRemote = cacheRemote) ?: return@runCatching null
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
}

suspend fun loadJvmArtworkBytes(
    locator: String?,
    cacheRemote: Boolean = true,
    userHomePath: String = System.getProperty("user.home"),
    remoteBytesLoader: suspend (String) -> ByteArray? = { target ->
        URI(target).toURL().openStream().use { it.readBytes() }
    },
): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val normalizedLocator = normalizedArtworkCacheLocator(locator) ?: return@runCatching null
        val target = resolveArtworkCacheTarget(normalizedLocator) ?: return@runCatching null
        when {
            isRemoteArtworkTarget(target) -> {
                val cacheDirectory = File(File(userHomePath), ".lynmusic/artwork-cache").apply { mkdirs() }
                val cachePrefix = normalizedLocator.stableArtworkCacheHash()
                val existingCacheFile = cacheDirectory
                    .listFiles()
                    ?.firstOrNull { file -> file.isFile && file.name.startsWith(cachePrefix) && file.length() > 0L }
                if (existingCacheFile != null) {
                    Files.readAllBytes(existingCacheFile.toPath())
                } else {
                    val payload = remoteBytesLoader(target)
                    if (payload == null || payload.isEmpty()) return@runCatching null
                    if (cacheRemote) {
                        val cacheFile = cacheDirectory.resolve(
                            "$cachePrefix${inferArtworkFileExtension(locator = target, bytes = payload)}",
                        )
                        runCatching { Files.write(cacheFile.toPath(), payload) }
                    }
                    payload
                }
            }

            target.startsWith("file://", ignoreCase = true) ->
                Files.readAllBytes(Paths.get(URI(target)))

            else -> Files.readAllBytes(Paths.get(target))
        }
    }.getOrNull() ?: loadBundledDefaultCoverBytes()
}

private fun isRemoteArtworkTarget(target: String): Boolean {
    return target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)
}
