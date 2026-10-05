package top.iwesley.lyn.music.core.model

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Call
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A remote media resource reachable through several signed candidates (LAN/WAN addresses). Every [open] signs a
 * fresh request, refreshes expired credentials once, moves to the next address on transport failures and finally
 * re-resolves the candidates once. The address that worked is remembered for later reads and seeks.
 *
 * When a server ignores Range (answers a ranged request with the whole body), the resource is downloaded once into
 * a temporary file and later opens are served from it; owners call [close] when done to delete that file.
 */
class SignedRemoteStream(
    candidates: List<RemotePlaybackUrlCandidate>,
    private val hooks: SignedRemoteStreamHooks,
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 15_000,
) {
    private val lock = Any()

    @Volatile
    var candidates: List<RemotePlaybackUrlCandidate> = candidates.also { require(it.isNotEmpty()) { "No stream candidates." } }
        private set

    @Volatile
    var selectedIndex: Int = 0
        private set

    /**
     * Changes whenever the address choice changes (switch, move, re-resolve). Consumers that saw a failure pass the
     * generation they opened with to [moveToNextCandidate], so two of them reacting to the same failure move only once.
     */
    @Volatile
    var generation: Int = 0
        private set

    private val materializeMutex = Mutex()

    @Volatile
    private var materialized: MaterializedResource? = null

    /** Set when the next [open] must re-resolve first; holds the candidates to avoid (empty after a network change). */
    @Volatile
    private var pendingReresolve: List<RemotePlaybackUrlCandidate>? = null

    val currentCandidate: RemotePlaybackUrlCandidate get() = candidates[selectedIndex.coerceIn(0, candidates.lastIndex)]

    /** Makes [index] the preferred candidate for the next [open]; false when there is no such candidate. */
    fun switchTo(index: Int): Boolean = synchronized(lock) {
        if (index !in candidates.indices) return false
        if (selectedIndex != index) {
            selectedIndex = index
            generation += 1
        }
        true
    }

    /** Resolves the candidates again before the next [open], e.g. after the network changed under an FN Connect route. */
    fun invalidateForReresolve() {
        // The network changed, so the old address is not known to be bad; probing decides again.
        pendingReresolve = emptyList()
    }

    /**
     * Gives up on the current address after a failure the stream could not see itself (e.g. a body read that timed out
     * after the response arrived): the next [open] starts from the next candidate, or re-resolves a single one.
     * With [expectedGeneration], nothing happens when someone already moved on since that generation; returns
     * whether this call moved.
     */
    fun moveToNextCandidate(expectedGeneration: Int? = null): Boolean = synchronized(lock) {
        if (expectedGeneration != null && expectedGeneration != generation) return false
        if (candidates.size > 1) {
            selectedIndex = (selectedIndex + 1) % candidates.size
        } else {
            pendingReresolve = listOf(currentCandidate)
        }
        generation += 1
        true
    }

    /** Deletes the temporary copy of a resource whose server ignored Range; the stream stays usable afterwards. */
    fun close() {
        val resource = synchronized(lock) { materialized.also { materialized = null } }
        resource?.file?.delete()
    }

    /** Whether another address may answer where [failure] happened; the same rule [openStream] uses. */
    fun isAddressFallbackAllowed(failure: Throwable): Boolean = hooks.isAddressFallbackAllowed(failure)

    /** Blocking variant of [openStream] for player I/O threads (ExoPlayer loaders, VLC callbacks). */
    fun open(startByte: Long, endByteInclusive: Long? = null): SignedHttpStream =
        runBlocking { openStream(startByte, endByteInclusive) }

    suspend fun openStream(startByte: Long, endByteInclusive: Long? = null): SignedHttpStream {
        materialized?.let { return it.open(startByte, endByteInclusive) }
        pendingReresolve?.let { failed ->
            pendingReresolve = null
            reresolve(failed)
        }
        var lastFailure: Throwable? = null
        val failed = mutableListOf<RemotePlaybackUrlCandidate>()
        for (round in 0..1) {
            val snapshot = candidates
            val first = selectedIndex.coerceIn(0, snapshot.lastIndex)
            val order = (first until snapshot.size) + (0 until first)
            for ((position, index) in order.withIndex()) {
                val connectTimeout = signedStreamConnectTimeoutMillis(
                    candidate = snapshot[index],
                    hasFallback = position < order.lastIndex,
                    defaultMillis = connectTimeoutMillis,
                )
                try {
                    val response = openWithReauthorization(snapshot, index, startByte, endByteInclusive, connectTimeout)
                    val rangeRequested = startByte > 0L || endByteInclusive != null
                    val stream = if (rangeRequested && response.statusCode == HttpURLConnection.HTTP_OK) {
                        materialize(response, startByte, endByteInclusive)
                    } else {
                        response
                    }
                    synchronized(lock) {
                        if ((candidates === snapshot || candidates.size == snapshot.size) && selectedIndex != index) {
                            selectedIndex = index
                            generation += 1
                        }
                    }
                    hooks.onSucceeded(currentCandidate)
                    return stream
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    // A call cancelled with this coroutine fails with a plain IOException; that is no address failure.
                    currentCoroutineContext().ensureActive()
                    lastFailure = throwable
                    if (!hooks.isAddressFallbackAllowed(throwable)) throw throwable
                    snapshot.getOrNull(index)?.let(failed::add)
                }
            }
            if (round == 0 && !reresolve(failed)) break
        }
        throw lastFailure ?: IOException("No usable stream address.")
    }

    private suspend fun openWithReauthorization(
        snapshot: List<RemotePlaybackUrlCandidate>,
        index: Int,
        startByte: Long,
        endByteInclusive: Long?,
        connectTimeout: Int,
    ): SignedHttpStream {
        val candidate = candidates.takeIf { it.size == snapshot.size }?.getOrNull(index) ?: snapshot[index]
        try {
            return openCandidate(candidate, startByte, endByteInclusive, connectTimeout)
        } catch (failure: RemoteSourceHttpStatusException) {
            // A CDN reached through a redirect never saw our credentials; refreshing them would not help.
            if (!sameOrigin(failure.finalUrl, candidate.value) || !hooks.isAuthFailure(candidate, failure.statusCode, failure.body)) throw failure
            val refreshed = hooks.reauthorize(candidate) ?: throw failure
            synchronized(lock) {
                val current = candidates
                if (index in current.indices && current[index].value == candidate.value) {
                    candidates = current.toMutableList().also { it[index] = refreshed }
                }
            }
            return openCandidate(refreshed, startByte, endByteInclusive, connectTimeout)
        }
    }

    private suspend fun openCandidate(
        candidate: RemotePlaybackUrlCandidate,
        startByte: Long,
        endByteInclusive: Long?,
        connectTimeout: Int,
    ): SignedHttpStream = withContext(Dispatchers.IO) {
        val activeCall = AtomicReference<Call?>()
        cancelOnCancellation(onCancel = { activeCall.get()?.cancel() }) {
            openSignedHttpStream(
            url = candidate.value,
            headers = candidate.headers,
            signHeaders = hooks::sign,
            redirectHeaders = hooks::redirectHeaders,
            startByte = startByte,
            endByteInclusive = endByteInclusive,
            connectTimeoutMillis = connectTimeout,
            readTimeoutMillis = readTimeoutMillis,
            skipIgnoredRange = false,
            onCall = activeCall::set,
            )
        }
    }

    /**
     * The server answered a ranged request with the whole body: keep that body in a temporary file (once, even when
     * several opens race) and serve the requested range from it, instead of re-downloading up to the offset each time.
     */
    private suspend fun materialize(
        response: SignedHttpStream,
        startByte: Long,
        endByteInclusive: Long?,
    ): SignedHttpStream = materializeMutex.withLock {
        materialized?.let { existing ->
            response.close()
            return@withLock existing.open(startByte, endByteInclusive)
        }
        val file = withContext(Dispatchers.IO) { File.createTempFile("lynmusic-stream-", ".media") }
        try {
            withContext(Dispatchers.IO) {
                cancelOnCancellation(onCancel = { response.abort() }) {
                response.use { body ->
                    var written = 0L
                    file.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            ensureActive()
                            val read = body.inputStream.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                        }
                    }
                    body.contentLength?.takeIf { written < it }?.let { throw RemoteStreamTruncatedException(written, it) }
                }
                }
            }
            val resource = MaterializedResource(file, response.finalUrl, response.contentType)
            materialized = resource
            resource.open(startByte, endByteInclusive)
        } catch (throwable: Throwable) {
            file.delete()
            throw throwable
        }
    }

    private suspend fun reresolve(failedCandidates: List<RemotePlaybackUrlCandidate>): Boolean {
        val fresh = runCatching { hooks.reresolve(failedCandidates) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: return false
        synchronized(lock) {
            candidates = fresh
            selectedIndex = 0
        }
        return true
    }
}

/**
 * Connect timeout for one candidate: a LAN address with another address still to try gets the short fallback cap
 * (it is the one likely to be unreachable away from home), everything else the normal timeout. Mirrors the cap the
 * URL-based remote sources apply.
 */
fun signedStreamConnectTimeoutMillis(
    candidate: RemotePlaybackUrlCandidate,
    hasFallback: Boolean,
    defaultMillis: Int,
): Int {
    if (!hasFallback || candidate.addressKind != "LAN") return defaultMillis
    return minOf(defaultMillis.toLong(), REMOTE_SOURCE_FALLBACK_CONNECT_TIMEOUT_MILLIS).toInt()
}

/**
 * Runs blocking [block] and calls [onCancel] from another thread as soon as the calling coroutine is cancelled, so a
 * call blocked on a socket can be aborted instead of waiting for its timeout. A normal finish never calls it.
 */
internal suspend fun <T> cancelOnCancellation(onCancel: () -> Unit, block: () -> T): T = coroutineScope {
    val finished = AtomicBoolean(false)
    val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            if (!finished.get()) onCancel()
        }
    }
    try {
        block()
    } finally {
        finished.set(true)
        watcher.cancel()
    }
}

/** A resource downloaded whole because its server ignored Range. */
private class MaterializedResource(
    val file: File,
    val finalUrl: String,
    val contentType: String?,
) {
    fun open(startByte: Long, endByteInclusive: Long?): SignedHttpStream {
        val length = file.length()
        if (startByte >= length && length > 0L || startByte > length) {
            // Answer like a server would, so readers can tell a seek to the end from a real failure.
            throw RemoteSourceHttpStatusException(416, finalUrl, contentType, body = null, contentRange = "bytes */$length")
        }
        val end = endByteInclusive?.let { minOf(it + 1, length) } ?: length
        return FileSignedHttpStream(file, startByte, end, finalUrl, contentType)
    }
}

private fun sameOrigin(left: String, right: String): Boolean {
    val a = runCatching { URL(left) }.getOrNull() ?: return false
    val b = runCatching { URL(right) }.getOrNull() ?: return false
    fun URL.origin() = "${protocol.lowercase()}://${host.lowercase()}:${if (port == -1) defaultPort else port}"
    return a.origin() == b.origin()
}
