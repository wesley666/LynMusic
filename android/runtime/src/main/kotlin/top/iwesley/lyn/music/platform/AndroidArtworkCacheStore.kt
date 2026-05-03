package top.iwesley.lyn.music.platform

import android.content.Context
import android.system.Os
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

    override suspend fun cache(locator: String, cacheKey: String, replaceExisting: Boolean): String? {
        return runCatching {
            val target = resolveArtworkCacheTarget(locator) ?: return@runCatching null
            if (target.isBlank()) return@runCatching null
            val primaryPrefix = cacheKey.ifBlank { locator }.stableArtworkCacheHash()
            val legacyPrefix = locator.stableArtworkCacheHash().takeIf { it != primaryPrefix }
            if (target.startsWith("file://", ignoreCase = true)) {
                val file = runCatching { File(URI(target)) }.getOrNull()
                    ?: return@runCatching target
                return@runCatching promoteLocalArtworkFile(
                    source = file,
                    cachePrefix = primaryPrefix,
                    locator = target,
                    replaceExisting = replaceExisting,
                )?.absolutePath ?: file.absolutePath
            }
            if (!target.startsWith("http://", ignoreCase = true) && !target.startsWith("https://", ignoreCase = true)) {
                val file = File(target)
                return@runCatching promoteLocalArtworkFile(
                    source = file,
                    cachePrefix = primaryPrefix,
                    locator = target,
                    replaceExisting = replaceExisting,
                )?.absolutePath ?: target
            }
            if (!replaceExisting) {
                findValidArtworkCacheFile(primaryPrefix)
                    ?.let { return@runCatching it.absolutePath }
                legacyPrefix
                    ?.let(::findValidArtworkCacheFile)
                    ?.let { legacy ->
                        return@runCatching promoteArtworkCacheFile(
                            source = legacy,
                            cachePrefix = primaryPrefix,
                            replaceExisting = false,
                        )?.absolutePath ?: legacy.absolutePath
                    }
            }
            val payload = URL(target).openStream().use { it.readBytes() }
            if (!isCompleteArtworkPayload(payload)) return@runCatching null
            val fileName = "$primaryPrefix${inferArtworkFileExtension(locator = target, bytes = payload)}"
            writeArtworkCacheFileAtomically(
                fileName = fileName,
                payload = payload,
                cachePrefix = primaryPrefix,
                replaceExisting = replaceExisting,
            )?.absolutePath
        }.getOrNull()
    }

    override suspend fun hasCached(cacheKey: String): Boolean {
        val cachePrefix = cacheKey.ifBlank { return false }.stableArtworkCacheHash()
        return findValidArtworkCacheFile(cachePrefix) != null
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

    private fun promoteLocalArtworkFile(
        source: File,
        cachePrefix: String,
        locator: String,
        replaceExisting: Boolean,
    ): File? {
        if (!source.isFile || source.length() <= 0L) return null
        val payload = runCatching { source.readBytes() }.getOrNull()
            ?.takeIf(::isCompleteArtworkPayload)
            ?: return null
        val fileName = "$cachePrefix${inferArtworkFileExtension(locator = locator, bytes = payload)}"
        return promoteArtworkCacheFile(source, cachePrefix, fileName, replaceExisting)
    }

    private fun promoteArtworkCacheFile(
        source: File,
        cachePrefix: String,
        replaceExisting: Boolean,
    ): File? {
        val extension = source.name.substringAfter(cachePrefix, source.name.substringAfterLast('.', ""))
            .takeIf { it.startsWith(".") }
            ?: source.extension.takeIf { it.isNotBlank() }?.let { ".$it" }
            ?: ".img"
        return promoteArtworkCacheFile(source, cachePrefix, "$cachePrefix$extension", replaceExisting)
    }

    private fun promoteArtworkCacheFile(
        source: File,
        cachePrefix: String,
        fileName: String,
        replaceExisting: Boolean,
    ): File? {
        if (!source.isFile || source.length() <= 0L) return null
        val output = File(directory, fileName)
        if (!replaceExisting) {
            findValidArtworkCacheFile(cachePrefix)?.let { return it }
        }
        return runCatching {
            if (replaceExisting) {
                deleteArtworkCacheFiles(cachePrefix)
            }
            if (source.canonicalPath == output.canonicalPath) {
                return@runCatching output
            }
            runCatching {
                Os.link(source.absolutePath, output.absolutePath)
            }.getOrElse {
                source.copyTo(output, overwrite = true)
            }
            output.takeIf {
                it.exists() &&
                    it.length() > 0L &&
                    runCatching { isCompleteArtworkPayload(it.readBytes()) }.getOrDefault(false)
            }
        }.getOrNull()
    }

    private fun writeArtworkCacheFileAtomically(
        fileName: String,
        payload: ByteArray,
        cachePrefix: String,
        replaceExisting: Boolean,
    ): File? {
        if (!isCompleteArtworkPayload(payload)) return null
        val output = File(directory, fileName)
        if (!replaceExisting && output.exists() && output.length() > 0L) {
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
            if (!replaceExisting &&
                output.exists() &&
                runCatching { isCompleteArtworkPayload(output.readBytes()) }.getOrDefault(false)
            ) {
                return@runCatching output
            }
            if (replaceExisting) {
                deleteArtworkCacheFiles(cachePrefix)
            } else {
                runCatching { output.delete() }
            }
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

    private fun deleteArtworkCacheFiles(cachePrefix: String) {
        directory.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.startsWith(cachePrefix) &&
                    !file.name.contains(ARTWORK_CACHE_TEMP_MARKER)
            }
            ?.forEach { file ->
                runCatching { file.delete() }
            }
    }
}

private const val ARTWORK_CACHE_TEMP_MARKER = ".tmp-"
