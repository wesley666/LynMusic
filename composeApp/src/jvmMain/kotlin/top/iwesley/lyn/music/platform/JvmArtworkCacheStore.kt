package top.iwesley.lyn.music.platform

import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import top.iwesley.lyn.music.core.model.ArtworkCacheEntry
import top.iwesley.lyn.music.core.model.ArtworkCacheBackend
import top.iwesley.lyn.music.core.model.ArtworkCacheCommit
import top.iwesley.lyn.music.core.model.CoordinatedArtworkCacheStore
import top.iwesley.lyn.music.core.model.PreparedArtwork
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import top.iwesley.lyn.music.core.model.ArtworkCachedTarget
import top.iwesley.lyn.music.core.model.ArtworkCachedTargetRegistry
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.JvmAppDataDirectory
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.RemotePlaybackUrlCandidate
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.isCompleteArtworkPayload
import top.iwesley.lyn.music.core.model.isReplaceableNavidromePlaceholderArtwork
import top.iwesley.lyn.music.core.model.resolveArtworkCacheTargets
import top.iwesley.lyn.music.core.model.stableArtworkCacheHash
import top.iwesley.lyn.music.domain.readRemotePlaybackUrlCandidateWithFallback

private val sharedJvmArtworkCaches = java.util.concurrent.ConcurrentHashMap<String, ArtworkCacheStore>()

fun createJvmArtworkCacheStore(): ArtworkCacheStore {
    val directory = JvmAppDataDirectory.resolve("artwork-cache")
    return sharedJvmArtworkCaches.computeIfAbsent(directory.canonicalPath) {
        CoordinatedArtworkCacheStore(JvmArtworkCacheBackend(directory), Dispatchers.IO)
    }
}

private class JvmArtworkCacheBackend(directory: File) : ArtworkCacheBackend {
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
            read = { target ->
                val connection = URL(target.value).openConnection().apply {
                    connectTimeout = 30_000
                    readTimeout = 30_000
                }
                try {
                    connection.getInputStream().use { it.readBytes() }
                } finally {
                    (connection as? java.net.HttpURLConnection)?.disconnect()
                }
            },
            isValidPayload = ::isCompleteArtworkPayload,
        )
    }

    override suspend fun find(cacheKey: String, detectPlaceholder: Boolean): ArtworkCacheEntry? {
        val (file, payload) = findValidArtworkCacheFile(cacheKey.stableArtworkCacheHash()) ?: return null
        val locator = rememberArtworkTarget(cacheKey, file)
        val placeholder = detectPlaceholder && isReplaceableNavidromePlaceholderArtwork(
            bytes = payload,
            differenceHash = decodeSkiaArtworkDifferenceHash(payload),
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
            currentCoroutineContext().ensureActive()
            runCatching {
                Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }.getOrElse {
                currentCoroutineContext().ensureActive()
                Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            deleteArtworkCacheFiles(cachePrefix, output.name)
            output.takeIf {
                it.exists() &&
                    it.length() > 0L &&
                    runCatching { isCompleteArtworkPayload(Files.readAllBytes(it.toPath())) }.getOrDefault(false)
            }?.let { ArtworkCacheFileResult(it, changed = true) }
        }.also {
            runCatching { Files.deleteIfExists(temporary.toPath()) }
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

private fun isRemoteArtworkTarget(target: String): Boolean {
    return target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)
}

private const val ARTWORK_CACHE_TEMP_MARKER = ".tmp-"
