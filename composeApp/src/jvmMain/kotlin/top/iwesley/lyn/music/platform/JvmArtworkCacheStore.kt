package top.iwesley.lyn.music.platform

import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.flow.Flow
import top.iwesley.lyn.music.core.model.ArtworkCachedTarget
import top.iwesley.lyn.music.core.model.ArtworkCachedTargetRegistry
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.ArtworkCacheVersionRegistry
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.isCompleteArtworkPayload
import top.iwesley.lyn.music.core.model.resolveArtworkCacheTarget
import top.iwesley.lyn.music.core.model.stableArtworkCacheHash

fun createJvmArtworkCacheStore(): ArtworkCacheStore = JvmArtworkCacheStore()

private class JvmArtworkCacheStore : ArtworkCacheStore {
    private val directory = File(File(System.getProperty("user.home")), ".lynmusic/artwork-cache").apply {
        mkdirs()
    }
    private val versionRegistry = ArtworkCacheVersionRegistry()
    private val targetRegistry = ArtworkCachedTargetRegistry()

    override suspend fun cache(locator: String, cacheKey: String, replaceExisting: Boolean): String? {
        return runCatching {
            val target = resolveArtworkCacheTarget(locator) ?: return@runCatching null
            if (target.isBlank()) return@runCatching null
            val effectiveCacheKey = cacheKey.ifBlank { locator }
            val primaryPrefix = effectiveCacheKey.stableArtworkCacheHash()
            val legacyPrefix = locator.stableArtworkCacheHash().takeIf { it != primaryPrefix }
            if (target.startsWith("file://", ignoreCase = true)) {
                val file = runCatching { Paths.get(URI(target)).toFile() }.getOrNull()
                    ?: return@runCatching target
                val promoted = promoteLocalArtworkFile(
                    source = file,
                    cachePrefix = primaryPrefix,
                    locator = target,
                    replaceExisting = replaceExisting,
                )
                val result = rememberArtworkTarget(effectiveCacheKey, promoted?.file ?: file)
                promoted?.takeIf { it.changed }?.let { versionRegistry.bump(effectiveCacheKey) }
                return@runCatching result
            }
            if (!target.startsWith("http://", ignoreCase = true) && !target.startsWith("https://", ignoreCase = true)) {
                val file = File(target)
                val promoted = promoteLocalArtworkFile(
                    source = file,
                    cachePrefix = primaryPrefix,
                    locator = target,
                    replaceExisting = replaceExisting,
                )
                val result = rememberArtworkTarget(effectiveCacheKey, promoted?.file ?: file)
                promoted?.takeIf { it.changed }?.let { versionRegistry.bump(effectiveCacheKey) }
                return@runCatching result
            }
            if (!replaceExisting) {
                findValidArtworkCacheFile(primaryPrefix)
                    ?.let { return@runCatching rememberArtworkTarget(effectiveCacheKey, it) }
                legacyPrefix
                    ?.let(::findValidArtworkCacheFile)
                    ?.let { legacy ->
                        val promoted = promoteArtworkCacheFile(
                            source = legacy,
                            cachePrefix = primaryPrefix,
                            replaceExisting = false,
                        )
                        val result = rememberArtworkTarget(effectiveCacheKey, promoted?.file ?: legacy)
                        promoted?.takeIf { it.changed }?.let { versionRegistry.bump(effectiveCacheKey) }
                        return@runCatching result
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
            val result = written?.file?.let { rememberArtworkTarget(effectiveCacheKey, it) }
            written?.takeIf { it.changed }?.let { versionRegistry.bump(effectiveCacheKey) }
            result
        }.getOrNull()
    }

    override suspend fun hasCached(cacheKey: String): Boolean {
        val cachePrefix = cacheKey.ifBlank { return false }.stableArtworkCacheHash()
        val file = findValidArtworkCacheFile(cachePrefix) ?: return false
        rememberArtworkTarget(cacheKey, file)
        return true
    }

    override fun observeVersion(cacheKey: String): Flow<Long> = versionRegistry.observe(cacheKey)

    override fun peekCachedTarget(cacheKey: String): ArtworkCachedTarget? {
        val cached = targetRegistry.peek(cacheKey) ?: return null
        return cached.takeIf { target ->
            !target.isLocalFile || File(target.target).isFile
        }
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
                val valid = runCatching { isCompleteArtworkPayload(Files.readAllBytes(file.toPath())) }.getOrDefault(false)
                if (!valid) {
                    runCatching { Files.deleteIfExists(file.toPath()) }
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
        val payload = runCatching { Files.readAllBytes(source.toPath()) }.getOrNull()
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
                Files.createLink(output.toPath(), source.toPath())
            }.getOrElse {
                Files.copy(source.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            output.takeIf {
                it.exists() &&
                    it.length() > 0L &&
                    runCatching { isCompleteArtworkPayload(Files.readAllBytes(it.toPath())) }.getOrDefault(false)
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
            if (runCatching { isCompleteArtworkPayload(Files.readAllBytes(output.toPath())) }.getOrDefault(false)) {
                return ArtworkCacheFileResult(output, changed = false)
            }
            runCatching { Files.deleteIfExists(output.toPath()) }
        }
        val temporary = File(directory, "$fileName$ARTWORK_CACHE_TEMP_MARKER${System.nanoTime()}")
        return runCatching {
            Files.write(temporary.toPath(), payload)
            if (Files.size(temporary.toPath()) != payload.size.toLong()) {
                return@runCatching null
            }
            if (!replaceExisting &&
                output.exists() &&
                runCatching { isCompleteArtworkPayload(Files.readAllBytes(output.toPath())) }.getOrDefault(false)
            ) {
                return@runCatching ArtworkCacheFileResult(output, changed = false)
            }
            if (replaceExisting) {
                deleteArtworkCacheFiles(cachePrefix)
            } else {
                runCatching { Files.deleteIfExists(output.toPath()) }
            }
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
            }?.let { ArtworkCacheFileResult(it, changed = true) }
        }.also {
            runCatching { Files.deleteIfExists(temporary.toPath()) }
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
                runCatching { Files.deleteIfExists(file.toPath()) }
            }
    }

    private fun rememberArtworkTarget(cacheKey: String, file: File): String {
        file.toArtworkCachedTarget()?.let { target ->
            targetRegistry.put(cacheKey, target)
        }
        return file.absolutePath
    }

    private fun File.toArtworkCachedTarget(): ArtworkCachedTarget? {
        if (!isFile || length() <= 0L) return null
        return ArtworkCachedTarget(
            target = absolutePath,
            version = "${length()}:${lastModified()}",
            isLocalFile = true,
        )
    }
}

private data class ArtworkCacheFileResult(
    val file: File,
    val changed: Boolean,
)

private const val ARTWORK_CACHE_TEMP_MARKER = ".tmp-"
