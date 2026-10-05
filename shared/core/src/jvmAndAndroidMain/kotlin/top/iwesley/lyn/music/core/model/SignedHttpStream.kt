package top.iwesley.lyn.music.core.model

import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL

/**
 * A remote-source answer that is not usable media: a non-2xx status, or a JSON body where media was expected (FN Music
 * reports auth and business errors as JSON with HTTP 200). The message stays `HTTP <code>` so address fallback
 * classification keeps working; [finalUrl] is the URL after redirects and [body] holds a JSON body when there was one.
 */
class RemoteSourceHttpStatusException(
    val statusCode: Int,
    val finalUrl: String,
    val contentType: String?,
    val body: String?,
    /** The `Content-Range` header; on a 416 its unsatisfied-range form gives the resource length. */
    val contentRange: String? = null,
) : IOException("HTTP $statusCode" + (body?.let { " unexpected JSON response: ${it.take(200)}" } ?: ""))

/**
 * The connection ended before the bytes its response announced: [position] was reached, [expectedEnd] was promised.
 * `HttpURLConnection` reports this as a plain end of stream, so readers that know the length raise it themselves.
 * The message names the closed connection so address fallback classification treats it as a transport failure.
 */
class RemoteStreamTruncatedException(
    val position: Long,
    val expectedEnd: Long,
) : IOException("Connection closed early at byte $position of $expectedEnd")

/**
 * An open media response: from the network ([openSignedHttpStream]) or from a file that holds the whole resource
 * (when the server ignored Range, see [SignedRemoteStream]). [finalUrl] is the URL after redirects.
 */
interface SignedHttpStream : AutoCloseable {
    val inputStream: InputStream
    val statusCode: Int
    val finalUrl: String
    val contentType: String?

    /** Length of this response body, or null when the server did not say. */
    val contentLength: Long?

    /** Full resource length from `Content-Range` (206) or `Content-Length` (200). */
    val totalLength: Long?

    /**
     * Absolute offset just past the last byte this response promised: the range end of a 206, or the full length of a
     * 200 (whose body starts at 0). Null when the server did not say, e.g. a chunked response.
     */
    val endExclusive: Long?

    /**
     * Breaks the connection from another thread so a read blocked on it fails at once. Unlike [close] it must not
     * touch [inputStream], whose read holds a lock the blocked thread owns.
     */
    fun abort() = close()
}

internal class HttpSignedHttpStream(
    private val call: Call,
    private val response: Response,
    override val inputStream: InputStream,
    override val finalUrl: String,
) : SignedHttpStream {
    override val statusCode: Int = response.code

    override val contentType: String? get() = response.header("Content-Type")

    override val contentLength: Long? get() = response.header("Content-Length")?.trim()?.toLongOrNull()?.takeIf { it >= 0L }

    override val totalLength: Long?
        get() = if (statusCode == HttpURLConnection.HTTP_PARTIAL) {
            response.header("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull()
        } else {
            contentLength
        }

    override val endExclusive: Long? get() = endExclusiveOf(response)

    override fun close() {
        runCatching { inputStream.close() }
        response.close()
    }

    companion object {
        fun endExclusiveOf(response: Response): Long? = if (response.code == HttpURLConnection.HTTP_PARTIAL) {
            response.header("Content-Range")
                ?.substringAfter(' ', "")
                ?.substringBefore('/')
                ?.substringAfter('-', "")
                ?.trim()
                ?.toLongOrNull()
                ?.plus(1)
        } else {
            response.header("Content-Length")?.trim()?.toLongOrNull()?.takeIf { it >= 0L }
        }
    }

    /** Cancelling the call fails a read blocked on its socket at once, from any thread. */
    override fun abort() {
        call.cancel()
    }
}

/** Serves `[start, endExclusive)` of a fully downloaded resource as if the server had answered the Range request. */
internal class FileSignedHttpStream(
    file: File,
    private val start: Long,
    override val endExclusive: Long,
    override val finalUrl: String,
    override val contentType: String?,
) : SignedHttpStream {
    private val channelStream = RandomAccessFile(file, "r").also { it.seek(start) }

    override val totalLength: Long = channelStream.length()
    override val contentLength: Long = (endExclusive - start).coerceAtLeast(0L)
    override val statusCode: Int = if (start == 0L && endExclusive == totalLength) HttpURLConnection.HTTP_OK else HttpURLConnection.HTTP_PARTIAL

    override val inputStream: InputStream = object : InputStream() {
        private var remaining = contentLength

        override fun read(): Int {
            if (remaining <= 0L) return -1
            return channelStream.read().also { if (it >= 0) remaining -= 1 }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (remaining <= 0L) return -1
            val read = channelStream.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
            if (read > 0) remaining -= read
            return read
        }

        override fun close() {
            channelStream.close()
        }
    }

    override fun close() {
        runCatching { channelStream.close() }
    }
}

/**
 * Opens a GET for [url] whose [headers] must be signed per request (FN Music's `authx` carries a timestamp).
 * [signHeaders] runs before every hop; redirects are followed by hand so [redirectHeaders] decides which headers
 * may travel to the next URL (null refuses the redirect). A server that ignores the Range header answers 200, in
 * which case the body is skipped forward to [startByte]. Every call is reported to [onCall], so a caller can
 * cancel it from another thread while it waits for headers.
 */
fun openSignedHttpStream(
    url: String,
    headers: Map<String, String>,
    signHeaders: (url: String, headers: Map<String, String>) -> Map<String, String>,
    redirectHeaders: (fromUrl: String, toUrl: String, headers: Map<String, String>) -> Map<String, String>?,
    startByte: Long = 0L,
    endByteInclusive: Long? = null,
    connectTimeoutMillis: Int = SIGNED_HTTP_DEFAULT_TIMEOUT_MILLIS,
    readTimeoutMillis: Int = SIGNED_HTTP_DEFAULT_TIMEOUT_MILLIS,
    /** False returns a 200 to a ranged request as-is (body from 0), so the caller can handle the ignored Range. */
    skipIgnoredRange: Boolean = true,
    onCall: ((Call) -> Unit)? = null,
): SignedHttpStream {
    val client = signedHttpClient.newBuilder()
        .connectTimeout(connectTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
        .build()
    var currentUrl = url
    var currentHeaders = headers
    repeat(SIGNED_HTTP_MAX_REDIRECTS + 1) {
        val request = Request.Builder().url(currentUrl).get().apply {
            signHeaders(currentUrl, currentHeaders).forEach { (name, value) -> header(name, value) }
            // Range offsets and lengths refer to the stored bytes; OkHttp's transparent gzip would change both.
            header("Accept-Encoding", "identity")
            if (startByte > 0L || endByteInclusive != null) {
                header("Range", "bytes=$startByte-${endByteInclusive?.toString().orEmpty()}")
            }
        }.build()
        val call = client.newCall(request)
        onCall?.invoke(call)
        val response = call.execute()
        try {
            val statusCode = response.code
            val location = response.header("Location")
            if (statusCode in 300..399 && location != null) {
                val next = response.request.url.resolve(location)?.toString()
                    ?: throw IOException("Refused redirect, HTTP $statusCode")
                currentHeaders = redirectHeaders(currentUrl, next, currentHeaders)
                    ?: throw IOException("Refused redirect, HTTP $statusCode")
                currentUrl = next
                response.close()
                return@repeat
            }
            val contentType = response.header("Content-Type")
            if (statusCode !in 200..299 || contentType?.contains("json", ignoreCase = true) == true) {
                throw RemoteSourceHttpStatusException(
                    statusCode = statusCode,
                    finalUrl = currentUrl,
                    contentType = contentType,
                    body = response.jsonErrorBodyOrNull(),
                    contentRange = response.header("Content-Range"),
                )
            }
            val rangeStart = if (statusCode == HttpURLConnection.HTTP_PARTIAL) {
                response.header("Content-Range")?.substringAfter(' ', "")?.substringBefore('-')?.trim()?.toLongOrNull() ?: startByte
            } else {
                0L
            }
            val input = TruncationReportingInputStream(response.body.byteStream(), rangeStart) {
                HttpSignedHttpStream.endExclusiveOf(response)
            }
            if (skipIgnoredRange && startByte > 0L && statusCode == HttpURLConnection.HTTP_OK) {
                var remaining = startByte
                while (remaining > 0L) {
                    val skipped = input.skip(remaining)
                    if (skipped <= 0L) {
                        if (input.read() < 0) throw IOException("Stream ended before offset $startByte")
                        remaining -= 1
                    } else {
                        remaining -= skipped
                    }
                }
            }
            return HttpSignedHttpStream(call, response, input, currentUrl)
        } catch (throwable: Throwable) {
            response.close()
            throw throwable
        }
    }
    throw IOException("Too many redirects")
}

/**
 * OkHttp reports a body that ends before its declared length as `ProtocolException("unexpected end of stream")`;
 * turn that into [RemoteStreamTruncatedException] with the absolute byte reached, so every reader treats it as the
 * dropped connection it is (and may resume elsewhere) instead of an unclassified protocol error.
 */
private class TruncationReportingInputStream(
    source: InputStream,
    private var position: Long,
    private val endExclusive: () -> Long?,
) : FilterInputStream(source) {
    override fun read(): Int = guard { super.read().also { if (it >= 0) position += 1 } }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        guard { super.read(buffer, offset, length).also { if (it > 0) position += it } }

    override fun skip(count: Long): Long = guard { super.skip(count).also { if (it > 0) position += it } }

    private inline fun <T> guard(block: () -> T): T {
        try {
            return block()
        } catch (failure: ProtocolException) {
            throw RemoteStreamTruncatedException(position, endExclusive() ?: -1L).apply { initCause(failure) }
        }
    }
}

/** One shared pool for signed media requests; redirects are followed by hand to control which headers travel. */
private val signedHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
}

private fun Response.jsonErrorBodyOrNull(): String? {
    if (header("Content-Type")?.contains("json", ignoreCase = true) != true) return null
    return runCatching { body.byteStream().use { it.readAtMost(SIGNED_HTTP_MAX_ERROR_BODY_BYTES) }.decodeToString() }.getOrNull()
}

/** The JSON body of an error answer (small, bounded), or null when the answer is not JSON. */
internal fun HttpURLConnection.jsonErrorBodyOrNull(): String? {
    if (contentType?.contains("json", ignoreCase = true) != true) return null
    val stream = runCatching { if (responseCode in 200..299) inputStream else errorStream }.getOrNull() ?: return null
    return runCatching { stream.use { it.readAtMost(SIGNED_HTTP_MAX_ERROR_BODY_BYTES) }.decodeToString() }.getOrNull()
}

private fun InputStream.readAtMost(limit: Int): ByteArray {
    val buffer = ByteArray(limit)
    var total = 0
    while (total < limit) {
        val read = read(buffer, total, limit - total)
        if (read < 0) break
        total += read
    }
    return buffer.copyOf(total)
}

private const val SIGNED_HTTP_MAX_REDIRECTS = 5
private const val SIGNED_HTTP_DEFAULT_TIMEOUT_MILLIS = 15_000
private const val SIGNED_HTTP_MAX_ERROR_BODY_BYTES = 64 * 1024
