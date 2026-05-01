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
import top.iwesley.lyn.music.core.model.isCompleteArtworkPayload
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
                val existingCacheFile = findValidAndroidArtworkCacheFile(cacheDirectory, cacheKey)
                if (existingCacheFile != null) {
                    BitmapFactory.decodeFile(existingCacheFile.absolutePath)
                } else {
                    val payload = URL(target).openStream().use { it.readBytes() }
                    if (!isCompleteArtworkPayload(payload)) return@runCatching null
                    if (cacheRemote) {
                        writeAndroidArtworkCacheFileAtomically(
                            directory = cacheDirectory,
                            fileName = "$cacheKey${inferArtworkFileExtension(locator = target, bytes = payload)}",
                            payload = payload,
                        )
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

private fun findValidAndroidArtworkCacheFile(directory: File, cacheKey: String): File? {
    return directory.listFiles()
        ?.asSequence()
        ?.filter { file ->
            file.isFile &&
                file.name.startsWith(cacheKey) &&
                !file.name.contains(ANDROID_ARTWORK_CACHE_TEMP_MARKER) &&
                file.length() > 0L
        }
        ?.firstOrNull { file ->
            val valid = runCatching { isCompleteArtworkPayload(file.readBytes()) }.getOrDefault(false)
            if (!valid) {
                runCatching { file.delete() }
            }
            valid
        }
}

private fun writeAndroidArtworkCacheFileAtomically(
    directory: File,
    fileName: String,
    payload: ByteArray,
): File? {
    if (!isCompleteArtworkPayload(payload)) return null
    val output = directory.resolve(fileName)
    if (output.exists() && output.length() > 0L) {
        if (runCatching { isCompleteArtworkPayload(output.readBytes()) }.getOrDefault(false)) {
            return output
        }
        runCatching { output.delete() }
    }
    val temporary = directory.resolve("$fileName$ANDROID_ARTWORK_CACHE_TEMP_MARKER${System.nanoTime()}")
    return runCatching {
        temporary.writeBytes(payload)
        if (temporary.length() != payload.size.toLong()) {
            return@runCatching null
        }
        if (output.exists() && runCatching { isCompleteArtworkPayload(output.readBytes()) }.getOrDefault(false)) {
            return@runCatching output
        }
        runCatching { output.delete() }
        if (!temporary.renameTo(output)) {
            temporary.copyTo(output, overwrite = true)
            temporary.delete()
        }
        output.takeIf {
            it.exists() &&
                it.length() > 0L &&
                runCatching { isCompleteArtworkPayload(it.readBytes()) }.getOrDefault(false)
        }
    }.also {
        if (temporary.exists()) {
            runCatching { temporary.delete() }
        }
    }.getOrNull()
}

private const val ANDROID_ARTWORK_CACHE_TEMP_MARKER = ".tmp-"
