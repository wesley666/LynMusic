package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Platform boundary. Instances must be shared by all users of the same cache directory. */
interface ArtworkCacheBackend {
    suspend fun find(cacheKey: String, detectPlaceholder: Boolean): ArtworkCacheEntry?
    suspend fun prepare(locator: String, cacheKey: String, allowLegacy: Boolean): PreparedArtwork?
    suspend fun commit(cacheKey: String, artwork: PreparedArtwork, replaceExisting: Boolean): ArtworkCacheCommit?
    fun peek(cacheKey: String): ArtworkCachedTarget?
}

data class PreparedArtwork(
    val locator: String,
    val bytes: ByteArray,
    val sourcePath: String? = null,
    val remoteTarget: RemotePlaybackUrlCandidate? = null,
)

data class ArtworkCacheEntry(val locator: String, val isPlaceholder: Boolean)

data class ArtworkCacheCommit(val locator: String, val changed: Boolean)

/** No network or candidate-file reads take place while a cache lock is held. */
class CoordinatedArtworkCacheStore(
    private val backend: ArtworkCacheBackend,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val logger: DiagnosticLogger = GlobalDiagnosticLogger,
) : ArtworkCacheStore {
    private val locks = Array(64) { Mutex() }
    private val versions = ArtworkCacheVersionRegistry()
    private fun lock(key: String) = locks[(key.hashCode() and Int.MAX_VALUE) % locks.size]

    override suspend fun cache(
        locator: String,
        cacheKey: String,
        policy: ArtworkWritePolicy,
    ): ArtworkCacheResult? = withContext(dispatcher) {
        val key = cacheKey.ifBlank { locator }
        try {
            val allowLegacy = lock(key).withLock {
                currentCoroutineContext().ensureActive()
                val existing = if (policy != ArtworkWritePolicy.Replace) backend.find(key, policy == ArtworkWritePolicy.MissingOrPlaceholder) else null
                if (existing != null) {
                    if (policy == ArtworkWritePolicy.KeepExisting) return@withContext ArtworkCacheResult(existing.locator, false)
                    if (policy == ArtworkWritePolicy.MissingOrPlaceholder && !existing.isPlaceholder) return@withContext ArtworkCacheResult(existing.locator, false)
                }
                policy == ArtworkWritePolicy.KeepExisting || (policy == ArtworkWritePolicy.MissingOrPlaceholder && existing == null)
            }
            val artwork = backend.prepare(locator, key, allowLegacy = allowLegacy)
                ?: return@withContext null
            currentCoroutineContext().ensureActive()
            val validPayload = isCompleteArtworkPayload(artwork.bytes)
            // A readable local source can still be used by the platform decoder when
            // cache validation or promotion fails. Remote payloads never use this fallback.
            val localFallback = artwork.sourcePath
                ?.takeIf { it.isNotBlank() && artwork.remoteTarget == null }
                ?.let { ArtworkCacheResult(it, changed = false) }
            lock(key).withLock {
                currentCoroutineContext().ensureActive()
                if (policy == ArtworkWritePolicy.MissingOrPlaceholder) {
                    backend.find(key, true)?.let { existing ->
                        if (!existing.isPlaceholder) return@withContext ArtworkCacheResult(existing.locator, false)
                    }
                }
                if (policy == ArtworkWritePolicy.KeepExisting) backend.find(key, false)?.let { return@withContext ArtworkCacheResult(it.locator, false) }
                currentCoroutineContext().ensureActive()
                if (!validPayload) return@withContext localFallback
                val committed = try {
                    backend.commit(key, artwork, replaceExisting = policy != ArtworkWritePolicy.KeepExisting)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    logger.error("ArtworkCache", error) { "cache-commit-failed key=$key policy=$policy" }
                    null
                }
                if (committed == null) {
                    currentCoroutineContext().ensureActive()
                    return@withContext localFallback
                }
                if (committed.changed) versions.bump(key)
                ArtworkCacheResult(committed.locator, committed.changed)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.error("ArtworkCache", error) { "cache-failed key=$key policy=$policy" }
            null
        }
    }

    override suspend fun hasCached(cacheKey: String): Boolean = withContext(dispatcher) {
        lock(cacheKey).withLock { backend.find(cacheKey, false) != null }
    }

    override suspend fun hasReplaceableNavidromePlaceholderCached(cacheKey: String): Boolean = withContext(dispatcher) {
        lock(cacheKey).withLock { backend.find(cacheKey, true)?.isPlaceholder == true }
    }

    override fun observeVersion(cacheKey: String): Flow<Long> = versions.observe(cacheKey)
    override fun peekCachedTarget(cacheKey: String): ArtworkCachedTarget? = backend.peek(cacheKey)
}
