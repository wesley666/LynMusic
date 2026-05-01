package top.iwesley.lyn.music.platform

import android.content.Context
import java.io.File
import java.net.URI
import java.net.URL
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.isCompleteArtworkPayload
import top.iwesley.lyn.music.core.model.resolveArtworkCacheTarget
import top.iwesley.lyn.music.core.model.stableArtworkCacheHash

fun createAndroidArtworkCacheStore(context: Context): ArtworkCacheStore = AndroidArtworkCacheStore(context)

private class AndroidArtworkCacheStore(
    context: Context,
) : ArtworkCacheStore {
    private val directory = File(context.cacheDir, "artwork-cache").apply { mkdirs() }

    override suspend fun cache(locator: String, cacheKey: String): String? {
        return runCatching {
            val target = resolveArtworkCacheTarget(locator) ?: return@runCatching null
            if (target.isBlank()) return@runCatching null
            if (target.startsWith("file://", ignoreCase = true)) {
                return@runCatching runCatching { File(URI(target)).absolutePath }.getOrNull()
            }
            if (!target.startsWith("http://", ignoreCase = true) && !target.startsWith("https://", ignoreCase = true)) {
                return@runCatching target
            }
            val cachePrefix = cacheKey.stableArtworkCacheHash()
            findValidArtworkCacheFile(cachePrefix)
                ?.let { return@runCatching it.absolutePath }
            val payload = URL(target).openStream().use { it.readBytes() }
            if (!isCompleteArtworkPayload(payload)) return@runCatching null
            val fileName = "$cachePrefix${inferArtworkFileExtension(locator = target, bytes = payload)}"
            writeArtworkCacheFileAtomically(fileName, payload)?.absolutePath
        }.getOrNull()
    }

    private fun findValidArtworkCacheFile(cachePrefix: String): File? {
        return directory.listFiles()
            ?.asSequence()
            ?.filter { file ->
                file.isFile &&
                    file.name.startsWith(cachePrefix) &&
                    !file.name.contains(ARTWORK_CACHE_TEMP_MARKER) &&
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

    private fun writeArtworkCacheFileAtomically(fileName: String, payload: ByteArray): File? {
        if (!isCompleteArtworkPayload(payload)) return null
        val output = File(directory, fileName)
        if (output.exists() && output.length() > 0L) {
            if (runCatching { isCompleteArtworkPayload(output.readBytes()) }.getOrDefault(false)) {
                return output
            }
            runCatching { output.delete() }
        }
        val temporary = File(directory, "$fileName$ARTWORK_CACHE_TEMP_MARKER${System.nanoTime()}")
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
}

private const val ARTWORK_CACHE_TEMP_MARKER = ".tmp-"
