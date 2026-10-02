package top.iwesley.lyn.music.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import top.iwesley.lyn.music.core.model.readRemoteSourceUrlBytes
import top.iwesley.lyn.music.core.model.ArtworkCacheEntry
import top.iwesley.lyn.music.core.model.ArtworkCacheBackend
import top.iwesley.lyn.music.core.model.ArtworkCacheCommit
import top.iwesley.lyn.music.core.model.CoordinatedArtworkCacheStore
import top.iwesley.lyn.music.core.model.PreparedArtwork
import java.net.URI
import top.iwesley.lyn.music.core.model.ArtworkCachedTarget
import top.iwesley.lyn.music.core.model.ArtworkCachedTargetRegistry
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.RemotePlaybackUrlCandidate
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.isCompleteArtworkPayload
import top.iwesley.lyn.music.core.model.isReplaceableNavidromePlaceholderArtwork
import top.iwesley.lyn.music.core.model.navidromeArtworkDifferenceHash
import top.iwesley.lyn.music.core.model.resolveArtworkCacheTargets
import top.iwesley.lyn.music.core.model.stableArtworkCacheHash
import top.iwesley.lyn.music.domain.readRemotePlaybackUrlCandidateWithFallback

fun createAndroidArtworkCacheStore(context: Context): ArtworkCacheStore {
    return SharedAndroidArtworkCacheStore.get(context.applicationContext.cacheDir)
}

internal object SharedAndroidArtworkCacheStore {
    private val stores = java.util.concurrent.ConcurrentHashMap<String, ArtworkCacheStore>()

    fun get(cacheDirectory: File): ArtworkCacheStore {
        val directory = File(cacheDirectory, "artwork-cache")
        return stores.computeIfAbsent(directory.canonicalPath) {
            CoordinatedArtworkCacheStore(AndroidArtworkCacheBackend(directory), Dispatchers.IO)
        }
    }

    internal fun resetForTesting() {
        stores.clear()
    }

}

private class AndroidArtworkCacheBackend(
    directory: File,
) : ArtworkCacheBackend {
    private val directory = directory.apply { mkdirs() }
    private val targetRegistry = ArtworkCachedTargetRegistry()
    override suspend fun prepare(locator: String, cacheKey: String, allowLegacy: Boolean): PreparedArtwork? {
        if (allowLegacy && locator != cacheKey) {
            findValidArtworkCacheFile(locator.stableArtworkCacheHash())?.let { (legacy, bytes) ->
                return PreparedArtwork(legacy.path, bytes, sourcePath = legacy.path)
            }
        }
        val targets = resolveArtworkCacheTargets(locator)
        val target = targets.firstOrNull()?.value ?: return null
        if (!isRemoteArtworkTarget(target)) {
            val file = if (target.startsWith("file:", ignoreCase = true)) {
                runCatching { File(URI(target)) }.getOrElse {
                    // Accept legacy unescaped spaces, while File(URI) still rejects remote authorities.
                    File(URI(target.replace(" ", "%20")))
                }
            } else File(target)
            if (!file.isFile) return null
            return PreparedArtwork(target, file.readBytes(), sourcePath = file.path)
        }
        val (remote, bytes) = readRemoteArtworkPayload(targets) ?: return null
        return PreparedArtwork(remote.value, bytes, remoteTarget = remote)
    }

    override suspend fun commit(cacheKey: String, artwork: PreparedArtwork, replaceExisting: Boolean): ArtworkCacheCommit? {
        val prefix = cacheKey.stableArtworkCacheHash()
        val fileName = "$prefix${inferArtworkFileExtension(artwork.locator, artwork.bytes)}"
        val sourcePath = artwork.sourcePath
        val written = if (sourcePath != null && File(sourcePath).canonicalPath == File(directory, fileName).canonicalPath) {
            ArtworkCacheFileResult(File(directory, fileName), changed = false)
        } else {
            writeArtworkCacheFileAtomically(fileName, artwork.bytes, prefix, replaceExisting)
        } ?: return null
        val result = rememberArtworkTarget(cacheKey, written.file)
        artwork.remoteTarget?.let { NavidromeLocatorRuntime.markResolvedUrlSuccess(it) }
        return ArtworkCacheCommit(result, written.changed)
    }

    private suspend fun readRemoteArtworkPayload(
        targets: List<RemotePlaybackUrlCandidate>,
    ): Pair<RemotePlaybackUrlCandidate, ByteArray>? {
        return readRemotePlaybackUrlCandidateWithFallback(
            candidates = targets,
            isRemoteUrl = ::isRemoteArtworkTarget,
            read = { target -> readRemoteSourceUrlBytes(target.value) },
            isValidPayload = ::isCompleteArtworkPayload,
        )
    }

    override suspend fun find(cacheKey: String, detectPlaceholder: Boolean): ArtworkCacheEntry? {
        val (file, payload) = findValidArtworkCacheFile(cacheKey.stableArtworkCacheHash()) ?: return null
        val locator = rememberArtworkTarget(cacheKey, file)
        val placeholder = detectPlaceholder && isReplaceableNavidromePlaceholderArtwork(
            bytes = payload,
            differenceHash = decodeAndroidArtworkDifferenceHash(payload),
        )
        return ArtworkCacheEntry(locator, placeholder)
    }

    override fun peek(cacheKey: String): ArtworkCachedTarget? {
        val cached = targetRegistry.peek(cacheKey) ?: return null
        return cached.takeIf { target ->
            !target.isLocalFile || File(target.target).isFile
        }
    }

    private fun findValidArtworkCacheFile(cachePrefix: String): Pair<File, ByteArray>? {
        return directory.listFiles()
            ?.asSequence()
            ?.filter { file ->
                file.isFile &&
                    file.name.startsWith(cachePrefix) &&
                    !file.name.contains(ARTWORK_CACHE_TEMP_MARKER) &&
                    file.length() > 0L
            }
            ?.mapNotNull { file ->
                val bytes = runCatching { file.readBytes() }.getOrNull()
                if (bytes != null && isCompleteArtworkPayload(bytes)) file to bytes else {
                    runCatching { file.delete() }
                    null
                }
            }?.firstOrNull()
    }

    private suspend fun writeArtworkCacheFileAtomically(
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
            currentCoroutineContext().ensureActive()
            if (!temporary.renameTo(output)) return@runCatching null
            deleteArtworkCacheFiles(cachePrefix, output.name)
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

    private fun deleteArtworkCacheFiles(cachePrefix: String, retainedFileName: String) {
        directory.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.startsWith(cachePrefix) &&
                    file.name != retainedFileName &&
                    !file.name.contains(ARTWORK_CACHE_TEMP_MARKER)
            }
            ?.forEach { file ->
                runCatching { file.delete() }
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

private fun decodeAndroidArtworkDifferenceHash(bytes: ByteArray): ULong? {
    return runCatching {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
        val scaled = if (source.width == 9 && source.height == 8) {
            source
        } else {
            Bitmap.createScaledBitmap(source, 9, 8, true)
        }
        try {
            navidromeArtworkDifferenceHash(
                IntArray(9 * 8) { index ->
                    val x = index % 9
                    val y = index / 9
                    scaled.getPixel(x, y).androidColorLuminance()
                },
            )
        } finally {
            if (scaled !== source) {
                scaled.recycle()
            }
            source.recycle()
        }
    }.getOrNull()
}

private fun Int.androidColorLuminance(): Int {
    return (Color.red(this) * 299 + Color.green(this) * 587 + Color.blue(this) * 114) / 1000
}

private fun isRemoteArtworkTarget(target: String): Boolean {
    return target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)
}

private const val ARTWORK_CACHE_TEMP_MARKER = ".tmp-"
