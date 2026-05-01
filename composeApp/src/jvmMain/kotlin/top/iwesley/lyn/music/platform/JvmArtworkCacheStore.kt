package top.iwesley.lyn.music.platform

import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.isCompleteArtworkPayload

fun createJvmArtworkCacheStore(): ArtworkCacheStore = JvmArtworkCacheStore()

private class JvmArtworkCacheStore : ArtworkCacheStore {
    private val directory = File(File(System.getProperty("user.home")), ".lynmusic/artwork-cache").apply {
        mkdirs()
    }

    override suspend fun cache(locator: String, cacheKey: String): String? {
        return runCatching {
            val target = resolveArtworkCacheTarget(locator) ?: return@runCatching null
            if (target.isBlank()) return@runCatching null
            if (target.startsWith("file://", ignoreCase = true)) {
                return@runCatching runCatching { Paths.get(URI(target)).toAbsolutePath().normalize().toString() }.getOrNull()
            }
            if (!target.startsWith("http://", ignoreCase = true) && !target.startsWith("https://", ignoreCase = true)) {
                return@runCatching target
            }
            val cachePrefix = cacheKey.stableArtworkCacheHash()
            findValidArtworkCacheFile(cachePrefix)?.let { return@runCatching it.absolutePath }
            val payload = URL(target).openStream().use { it.readBytes() }
            if (!isCompleteArtworkPayload(payload)) return@runCatching null
            val extension = inferArtworkFileExtension(locator = target, bytes = payload)
            val fileName = "$cachePrefix$extension"
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
                val valid = runCatching { isCompleteArtworkPayload(Files.readAllBytes(file.toPath())) }.getOrDefault(false)
                if (!valid) {
                    runCatching { Files.deleteIfExists(file.toPath()) }
                }
                valid
            }
    }

    private fun writeArtworkCacheFileAtomically(fileName: String, payload: ByteArray): File? {
        if (!isCompleteArtworkPayload(payload)) return null
        val output = File(directory, fileName)
        if (output.exists() && output.length() > 0L) {
            if (runCatching { isCompleteArtworkPayload(Files.readAllBytes(output.toPath())) }.getOrDefault(false)) {
                return output
            }
            runCatching { Files.deleteIfExists(output.toPath()) }
        }
        val temporary = File(directory, "$fileName$ARTWORK_CACHE_TEMP_MARKER${System.nanoTime()}")
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
}

private const val ARTWORK_CACHE_TEMP_MARKER = ".tmp-"
