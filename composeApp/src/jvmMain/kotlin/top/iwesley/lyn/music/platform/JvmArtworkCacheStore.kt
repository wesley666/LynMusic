package top.iwesley.lyn.music.platform

import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import top.iwesley.lyn.music.core.model.ArtworkCacheStore

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
            val payload = URL(target).openStream().use { it.readBytes() }
            if (payload.isEmpty()) return@runCatching null
            val extension = artworkCacheExtension(target, payload)
            val fileName = "${cacheKey.stableArtworkCacheHash()}$extension"
            val output = File(directory, fileName)
            if (output.exists() && output.length() > 0L) {
                return@runCatching output.absolutePath
            }
            Files.write(output.toPath(), payload)
            output.absolutePath.takeIf { output.length() > 0L }
        }.getOrNull()
    }
}
