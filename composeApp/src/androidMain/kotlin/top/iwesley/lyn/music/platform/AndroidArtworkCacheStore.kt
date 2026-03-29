package top.iwesley.lyn.music.platform

import android.content.Context
import java.io.File
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator

fun createAndroidArtworkCacheStore(context: Context): ArtworkCacheStore = AndroidArtworkCacheStore(context)

private class AndroidArtworkCacheStore(
    context: Context,
) : ArtworkCacheStore {
    private val directory = File(context.cacheDir, "artwork-cache").apply { mkdirs() }

    override suspend fun cache(locator: String, cacheKey: String): String? {
        val target = normalizeArtworkLocator(locator)?.trim().orEmpty()
        if (target.isBlank()) return null
        if (target.startsWith("file://", ignoreCase = true)) {
            return runCatching { File(URI(target)).absolutePath }.getOrNull()
        }
        if (!target.startsWith("http://", ignoreCase = true) && !target.startsWith("https://", ignoreCase = true)) {
            return target
        }
        val fileName = "${cacheKey.stableHash()}${artworkExtension(target)}"
        val output = File(directory, fileName)
        if (output.exists() && output.length() > 0L) {
            return output.absolutePath
        }
        URL(target).openStream().use { input ->
            output.outputStream().use { out -> input.copyTo(out) }
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
