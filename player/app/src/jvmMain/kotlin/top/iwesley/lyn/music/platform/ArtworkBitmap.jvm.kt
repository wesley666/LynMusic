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
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
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
                val existingCacheFile = findValidJvmArtworkCacheFile(cacheDirectory, cachePrefix)
                if (existingCacheFile != null) {
                    Files.readAllBytes(existingCacheFile.toPath())
                } else {
                    val payload = remoteBytesLoader(target)
                    if (payload == null || !isCompleteArtworkPayload(payload)) return@runCatching null
                    if (cacheRemote) {
                        writeJvmArtworkCacheFileAtomically(
                            directory = cacheDirectory,
                            fileName = "$cachePrefix${inferArtworkFileExtension(locator = target, bytes = payload)}",
                            payload = payload,
                        )
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

private fun findValidJvmArtworkCacheFile(directory: File, cachePrefix: String): File? {
    return directory.listFiles()
        ?.asSequence()
        ?.filter { file ->
            file.isFile &&
                file.name.startsWith(cachePrefix) &&
                !file.name.contains(JVM_ARTWORK_CACHE_TEMP_MARKER) &&
                file.length() > 0L
        }
        ?.firstOrNull { file ->
            val valid = runCatching { isCompleteArtworkPayload(Files.readAllBytes(file.toPath())) }.getOrDefault(false)
            if (!valid) {
                runCatching { Files.deleteIfExists(file.toPath()) }
            }
            valid
        }
}

private fun writeJvmArtworkCacheFileAtomically(
    directory: File,
    fileName: String,
    payload: ByteArray,
): File? {
    if (!isCompleteArtworkPayload(payload)) return null
    val output = directory.resolve(fileName)
    if (output.exists() && output.length() > 0L) {
        if (runCatching { isCompleteArtworkPayload(Files.readAllBytes(output.toPath())) }.getOrDefault(false)) {
            return output
        }
        runCatching { Files.deleteIfExists(output.toPath()) }
    }
    val temporary = directory.resolve("$fileName$JVM_ARTWORK_CACHE_TEMP_MARKER${System.nanoTime()}")
    return runCatching {
        Files.write(temporary.toPath(), payload)
        if (Files.size(temporary.toPath()) != payload.size.toLong()) {
            return@runCatching null
        }
        if (output.exists() && runCatching { isCompleteArtworkPayload(Files.readAllBytes(output.toPath())) }.getOrDefault(false)) {
            return@runCatching output
        }
        runCatching { Files.deleteIfExists(output.toPath()) }
        runCatching {
            Files.move(
                temporary.toPath(),
                output.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        output.takeIf {
            it.exists() &&
                it.length() > 0L &&
                runCatching { isCompleteArtworkPayload(Files.readAllBytes(it.toPath())) }.getOrDefault(false)
        }
    }.also {
        runCatching { Files.deleteIfExists(temporary.toPath()) }
    }.getOrNull()
}

private const val JVM_ARTWORK_CACHE_TEMP_MARKER = ".tmp-"
