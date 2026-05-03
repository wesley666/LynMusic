package top.iwesley.lyn.music.platform

import android.content.Context
import android.system.Os
import java.io.File
import java.net.URI
import java.net.URL
import kotlinx.coroutines.flow.Flow
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.ArtworkCacheVersionRegistry
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.isCompleteArtworkPayload
import top.iwesley.lyn.music.core.model.resolveArtworkCacheTarget
import top.iwesley.lyn.music.core.model.stableArtworkCacheHash

fun createAndroidArtworkCacheStore(context: Context): ArtworkCacheStore = AndroidArtworkCacheStore(context)

private class AndroidArtworkCacheStore(
    context: Context,
) : ArtworkCacheStore {
    private val directory = File(context.cacheDir, "artwork-cache").apply { mkdirs() }
    private val versionRegistry = ArtworkCacheVersionRegistry()

    override suspend fun cache(locator: String, cacheKey: String, replaceExisting: Boolean): String? {
        return runCatching {
            val target = resolveArtworkCacheTarget(locator) ?: return@runCatching null
            if (target.isBlank()) return@runCatching null
            val effectiveCacheKey = cacheKey.ifBlank { locator }
            val primaryPrefix = effectiveCacheKey.stableArtworkCacheHash()
            val legacyPrefix = locator.stableArtworkCacheHash().takeIf { it != primaryPrefix }
            if (target.startsWith("file://", ignoreCase = true)) {
                val file = runCatching { File(URI(target)) }.getOrNull()
                    ?: return@runCatching target
                val promoted = promoteLocalArtworkFile(
                    source = file,
                    cachePrefix = primaryPrefix,
                    locator = target,
                    replaceExisting = replaceExisting,
                )
                promoted?.takeIf { it.changed }?.let { versionRegistry.bump(effectiveCacheKey) }
                return@runCatching promoted?.file?.absolutePath ?: file.absolutePath
            }
            if (!target.startsWith("http://", ignoreCase = true) && !target.startsWith("https://", ignoreCase = true)) {
                val file = File(target)
                val promoted = promoteLocalArtworkFile(
                    source = file,
                    cachePrefix = primaryPrefix,
                    locator = target,
                    replaceExisting = replaceExisting,
                )
                promoted?.takeIf { it.changed }?.let { versionRegistry.bump(effectiveCacheKey) }
                return@runCatching promoted?.file?.absolutePath ?: target
            }
            if (!replaceExisting) {
                findValidArtworkCacheFile(primaryPrefix)
                    ?.let { return@runCatching it.absolutePath }
                legacyPrefix
                    ?.let(::findValidArtworkCacheFile)
                    ?.let { legacy ->
                        val promoted = promoteArtworkCacheFile(
                            source = legacy,
                            cachePrefix = primaryPrefix,
                            replaceExisting = false,
                        )
                        promoted?.takeIf { it.changed }?.let { versionRegistry.bump(effectiveCacheKey) }
                        return@runCatching promoted?.file?.absolutePath ?: legacy.absolutePath
                    }
            }
            val payload = URL(target).openStream().use { it.readBytes() }
            if (!isCompleteArtworkPayload(payload)) return@runCatching null
            val fileName = "$primaryPrefix${inferArtworkFileExtension(locator = target, bytes = payload)}"
            val written = writeArtworkCacheFileAtomically(
                fileName = fileName,
                payload = payload,
                cachePrefix = primaryPrefix,
                replaceExisting = replaceExisting,
            )
            written?.takeIf { it.changed }?.let { versionRegistry.bump(effectiveCacheKey) }
            written?.file?.absolutePath
        }.getOrNull()
    }

    override suspend fun hasCached(cacheKey: String): Boolean {
        val cachePrefix = cacheKey.ifBlank { return false }.stableArtworkCacheHash()
        return findValidArtworkCacheFile(cachePrefix) != null
    }

    override fun observeVersion(cacheKey: String): Flow<Long> = versionRegistry.observe(cacheKey)

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
    ): ArtworkCacheFileResult? {
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
    ): ArtworkCacheFileResult? {
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
    ): ArtworkCacheFileResult? {
        if (!source.isFile || source.length() <= 0L) return null
        val output = File(directory, fileName)
        if (!replaceExisting) {
            findValidArtworkCacheFile(cachePrefix)?.let { return ArtworkCacheFileResult(it, changed = false) }
        }
        return runCatching {
            if (source.canonicalPath == output.canonicalPath) {
                return@runCatching ArtworkCacheFileResult(output, changed = false)
            }
            if (replaceExisting) {
                deleteArtworkCacheFiles(cachePrefix)
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
            }?.let { ArtworkCacheFileResult(it, changed = true) }
        }.getOrNull()
    }

    private fun writeArtworkCacheFileAtomically(
        fileName: String,
        payload: ByteArray,
        cachePrefix: String,
        replaceExisting: Boolean,
    ): ArtworkCacheFileResult? {
        if (!isCompleteArtworkPayload(payload)) return null
        val output = File(directory, fileName)
        if (!replaceExisting && output.exists() && output.length() > 0L) {
            if (runCatching { isCompleteArtworkPayload(output.readBytes()) }.getOrDefault(false)) {
                return ArtworkCacheFileResult(output, changed = false)
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
                return@runCatching ArtworkCacheFileResult(output, changed = false)
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
            }?.let { ArtworkCacheFileResult(it, changed = true) }
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

private data class ArtworkCacheFileResult(
    val file: File,
    val changed: Boolean,
)

private const val ARTWORK_CACHE_TEMP_MARKER = ".tmp-"
