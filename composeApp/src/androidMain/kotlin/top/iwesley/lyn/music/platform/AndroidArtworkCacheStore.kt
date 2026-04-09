package top.iwesley.lyn.music.platform

import android.content.Context
import java.io.File
import java.net.URI
import java.net.URL
import top.iwesley.lyn.music.core.model.ArtworkCacheStore

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
            val payload = URL(target).openStream().use { it.readBytes() }
            if (payload.isEmpty()) return@runCatching null
            val fileName = "${cacheKey.stableArtworkCacheHash()}${artworkCacheExtension(target, payload)}"
            val output = File(directory, fileName)
            if (output.exists() && output.length() > 0L) {
                return@runCatching output.absolutePath
            }
            output.writeBytes(payload)
            output.absolutePath.takeIf { output.length() > 0L }
        }.getOrNull()
    }
}
