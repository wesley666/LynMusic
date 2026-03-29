package top.iwesley.lyn.music.platform

import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator
import top.iwesley.lyn.music.core.model.parseNavidromeCoverLocator

fun createJvmArtworkCacheStore(): ArtworkCacheStore = JvmArtworkCacheStore()

private class JvmArtworkCacheStore : ArtworkCacheStore {
    private val directory = File(File(System.getProperty("user.home")), ".lynmusic/artwork-cache").apply {
        mkdirs()
    }

    override suspend fun cache(locator: String, cacheKey: String): String? {
        val rawTarget = normalizeArtworkLocator(locator)?.trim().orEmpty()
        if (rawTarget.isBlank()) return null
        val target = if (parseNavidromeCoverLocator(rawTarget) != null) {
            NavidromeLocatorRuntime.resolveCoverArtUrl(rawTarget).orEmpty()
        } else {
            rawTarget
        }
        if (target.isBlank()) return null
        if (target.startsWith("file://", ignoreCase = true)) {
            return runCatching { Paths.get(URI(target)).toAbsolutePath().normalize().toString() }.getOrNull()
        }
        if (!target.startsWith("http://", ignoreCase = true) && !target.startsWith("https://", ignoreCase = true)) {
            return target
        }
        val extension = artworkExtension(target)
        val fileName = "${cacheKey.stableHash()}$extension"
        val output = File(directory, fileName)
        if (output.exists() && output.length() > 0L) {
            return output.absolutePath
        }
        URL(target).openStream().use { input ->
            Files.copy(input, output.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        return output.absolutePath.takeIf { output.length() > 0L }
    }
}

private fun artworkExtension(locator: String): String {
    val path = runCatching { URI(locator).path }.getOrNull().orEmpty()
    val extension = path.substringAfterLast('.', "").lowercase()
    return if (extension in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")) ".$extension" else ".img"
}

private fun String.stableHash(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
