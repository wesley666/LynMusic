package top.iwesley.lyn.music.core.model

import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Reads a [SignedRemoteStream] from [startByte] on and survives a body read that breaks after the response arrived
 * (e.g. a LAN address that stops answering mid-song): it moves to the next address, or re-resolves a single one,
 * and continues from the byte it reached with a Range request. Blocking; use it on I/O threads only.
 *
 * A seek or read at the end of the resource returns end of stream only when that end is proven (a known length, or
 * a 416 whose `Content-Range` gives it); any other 416 stays an error. [cancel] aborts a blocked read or open from
 * another thread; [bindToCurrentCoroutine] wires it to coroutine cancellation.
 */
class SignedRemoteStreamInputStream(
    private val stream: SignedRemoteStream,
    startByte: Long = 0L,
) : InputStream() {
    @Volatile
    private var response: SignedHttpStream? = null
    private var consecutiveFailures = 0

    /** The stream generation this reader's current response was opened at; see [SignedRemoteStream.moveToNextCandidate]. */
    private var openedGeneration: Int = stream.generation

    @Volatile
    private var cancelled = false

    @Volatile
    private var closed = false

    @Volatile
    private var openJob: Job? = null

    private var cancellationWatcher: Job? = null

    /** Absolute offset of the next byte this stream returns. */
    var position: Long = startByte
        private set

    /** Content type of the first response, once opened. */
    var contentType: String? = null
        private set

    /** Full resource length, from the first response or a 416's `Content-Range`; null while unknown. */
    var totalLength: Long? = null
        private set

    private val atProvenEnd: Boolean get() = totalLength?.let { position >= it } == true

    /** Opens the response now, so a failure surfaces here instead of on the first read. Blocking. */
    fun ensureOpen() {
        checkNotCancelled()
        if (response != null || atProvenEnd) return
        val opened = runCatchingProvenEnd {
            runBlocking {
                openJob = coroutineContext[Job]
                try {
                    stream.openStream(startByte = position)
                } finally {
                    openJob = null
                }
            }
        } ?: return
        accept(opened)
    }

    /** Suspending variant of [ensureOpen] for coroutine callers (downloads, the cast proxy); cancellable. */
    suspend fun ensureOpenSuspending() {
        checkNotCancelled()
        if (response != null || atProvenEnd) return
        val opened = runCatchingProvenEnd { stream.openStream(startByte = position) } ?: return
        accept(opened)
    }

    /**
     * Cancels this reader when the calling coroutine is cancelled: a watcher child closes the connection, which
     * makes a read blocked on the socket fail at once instead of after the read timeout. [close] releases it.
     */
    suspend fun bindToCurrentCoroutine() {
        cancellationWatcher?.cancel()
        cancellationWatcher = CoroutineScope(currentCoroutineContext()).launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                if (!closed) cancel()
            }
        }
    }

    /** Continues from [offset]; the new position is opened immediately, so an unreachable one fails here. */
    fun seekTo(offset: Long) {
        require(offset >= 0L) { "Negative offset $offset" }
        checkNotCancelled()
        closeResponse()
        position = offset
        consecutiveFailures = 0
        ensureOpen()
    }

    override fun read(): Int {
        val single = ByteArray(1)
        return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xff
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (true) {
            checkNotCancelled()
            try {
                ensureOpen()
                val current = response ?: return -1
                val read = current.inputStream.read(buffer, offset, length)
                if (read > 0) {
                    position += read
                    consecutiveFailures = 0
                } else if (read < 0) {
                    // A closed connection looks like a normal end; only the promised length tells them apart.
                    current.endExclusive?.takeIf { position < it }?.let { throw RemoteStreamTruncatedException(position, it) }
                }
                return read
            } catch (failure: IOException) {
                closeResponse()
                checkNotCancelled()
                val maxFailures = maxOf(2, stream.candidates.size)
                val recoverable = failure is RemoteStreamTruncatedException || stream.isAddressFallbackAllowed(failure)
                if (!recoverable || ++consecutiveFailures >= maxFailures) throw failure
                stream.moveToNextCandidate(expectedGeneration = openedGeneration)
            }
        }
    }

    /** Aborts a blocked open or read from another thread; later calls fail with [CancellationException]. */
    fun cancel() {
        cancelled = true
        openJob?.cancel()
        // Abort, not close: the reading thread holds the input stream's lock until its blocked read returns.
        response?.abort()
    }

    override fun close() {
        closed = true
        cancellationWatcher?.cancel()
        cancellationWatcher = null
        closeResponse()
    }

    private fun accept(opened: SignedHttpStream) {
        if (cancelled) {
            opened.close()
            throw CancellationException("Stream reader cancelled")
        }
        response = opened
        openedGeneration = stream.generation
        if (contentType == null) contentType = opened.contentType
        if (totalLength == null) totalLength = opened.totalLength
    }

    /** Runs an open; a 416 that proves [position] is at or past the end leaves the reader at end of stream. */
    private inline fun runCatchingProvenEnd(open: () -> SignedHttpStream): SignedHttpStream? {
        return try {
            open()
        } catch (failure: RemoteSourceHttpStatusException) {
            val provenLength = failure.takeIf { it.statusCode == 416 }?.contentRange?.unsatisfiedRangeLength()
            if (provenLength == null || position < provenLength) throw failure
            if (totalLength == null) totalLength = provenLength
            null
        }
    }

    private fun checkNotCancelled() {
        if (cancelled) throw CancellationException("Stream reader cancelled")
    }

    private fun closeResponse() {
        response?.close()
        response = null
    }
}

/** The resource length from a 416's unsatisfied-range `Content-Range` (`bytes`, `*`, `/`, length), or null. */
private fun String.unsatisfiedRangeLength(): Long? {
    val value = trim()
    if (!value.startsWith("bytes */", ignoreCase = true)) return null
    return value.substringAfter("*/").trim().toLongOrNull()
}
