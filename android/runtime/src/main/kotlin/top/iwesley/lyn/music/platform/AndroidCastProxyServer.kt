package top.iwesley.lyn.music.platform

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.error

internal class AndroidCastProxyServer(
    private val registry: AndroidCastProxySessionRegistry,
    private val logger: DiagnosticLogger,
) {
    private val mutex = Mutex()
    private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var activePort: Int? = null

    suspend fun ensureStarted(): Int = mutex.withLock {
        activePort?.let { return@withLock it }
        val server = embeddedServer(CIO, host = "0.0.0.0", port = 0) {
            installRoutes()
        }
        server.start(wait = false)
        val port = server.engine.resolvedConnectors().firstOrNull()?.port
            ?: error("无法获取投屏代理端口。")
        engine = server
        activePort = port
        logger.info(CAST_PROXY_LOG_TAG) { "server-started port=$port" }
        port
    }

    suspend fun stopIfIdle() {
        if (!registry.isEmpty()) return
        stop()
    }

    suspend fun stop() = mutex.withLock {
        val server = engine ?: return@withLock
        activePort = null
        engine = null
        withContext(Dispatchers.IO) {
            server.stop(gracePeriodMillis = 300L, timeoutMillis = 1_000L)
        }
        logger.info(CAST_PROXY_LOG_TAG) { "server-stopped" }
    }

    private fun Application.installRoutes() {
        routing {
            route("/cast/stream/{token}") {
                head { call.handleStream(headOnly = true) }
                get { call.handleStream(headOnly = false) }
            }
        }
    }

    private suspend fun ApplicationCall.handleStream(headOnly: Boolean) {
        val token = parameters["token"].orEmpty()
        val entry = registry.get(token)
        if (entry == null) {
            respondBytes(ByteArray(0), status = HttpStatusCode.NotFound)
            return
        }
        val resource = entry.resource
        val rangeHeader = request.header(HttpHeaders.Range)
        val range = parseRangeHeader(rangeHeader, resource.length)
        if (range is ParsedRange.Invalid) {
            response.header(HttpHeaders.AcceptRanges, "bytes")
            response.header(HttpHeaders.ContentRange, "bytes */${resource.length?.toString() ?: "*"}")
            respondBytes(ByteArray(0), status = HttpStatusCode.RequestedRangeNotSatisfiable)
            return
        }
        val resolvedRange = (range as? ParsedRange.Valid)?.range
        val status = if (resolvedRange != null) HttpStatusCode.PartialContent else HttpStatusCode.OK
        val responseLength = resolvedRange?.length ?: resource.length
        val contentType = parseContentType(resource.mimeType)
        response.header(HttpHeaders.AcceptRanges, "bytes")
        response.header(HttpHeaders.CacheControl, "no-store")
        responseLength?.let { response.header(HttpHeaders.ContentLength, it.toString()) }
        resolvedRange?.let { byteRange ->
            val total = resource.length?.toString() ?: "*"
            response.header(
                HttpHeaders.ContentRange,
                "bytes ${byteRange.start}-${byteRange.endInclusive}/$total",
            )
        }
        if (headOnly) {
            respondOutputStream(
                contentType = contentType,
                status = status,
                contentLength = responseLength,
            ) {
                // HEAD only advertises stream metadata; never open the source.
            }
            return
        }
        respondOutputStream(
            contentType = contentType,
            status = status,
            contentLength = responseLength,
        ) {
            val start = resolvedRange?.start ?: 0L
            val requestedLength = resolvedRange?.length
            try {
                resource.open(start = start, length = requestedLength).use { input ->
                    input.copyLimitedTo(this, requestedLength)
                }
            } catch (throwable: Throwable) {
                logger.error(CAST_PROXY_LOG_TAG, throwable) {
                    "stream-failed token=$token start=$start length=${requestedLength ?: -1L}"
                }
                throw throwable
            }
        }
    }
}

private sealed interface ParsedRange {
    data object None : ParsedRange
    data object Invalid : ParsedRange
    data class Valid(val range: ByteRange) : ParsedRange
}

private data class ByteRange(
    val start: Long,
    val endInclusive: Long,
) {
    val length: Long
        get() = endInclusive - start + 1L
}

private fun parseRangeHeader(header: String?, totalLength: Long?): ParsedRange {
    val value = header?.trim().orEmpty()
    if (value.isBlank()) return ParsedRange.None
    if (!value.startsWith("bytes=", ignoreCase = true)) return ParsedRange.Invalid
    if (value.substringAfter('=').contains(',')) return ParsedRange.Invalid
    if (totalLength == null || totalLength <= 0L) return ParsedRange.Invalid
    val spec = value.substringAfter('=').trim()
    val startPart = spec.substringBefore('-').trim()
    val endPart = spec.substringAfter('-', missingDelimiterValue = "").trim()
    val range = if (startPart.isBlank()) {
        val suffixLength = endPart.toLongOrNull() ?: return ParsedRange.Invalid
        if (suffixLength <= 0L) return ParsedRange.Invalid
        val start = (totalLength - suffixLength).coerceAtLeast(0L)
        ByteRange(start = start, endInclusive = totalLength - 1L)
    } else {
        val start = startPart.toLongOrNull() ?: return ParsedRange.Invalid
        val end = endPart.toLongOrNull() ?: (totalLength - 1L)
        if (start < 0L || start >= totalLength || end < start) return ParsedRange.Invalid
        ByteRange(start = start, endInclusive = minOf(end, totalLength - 1L))
    }
    return ParsedRange.Valid(range)
}

private fun parseContentType(mimeType: String): ContentType {
    return runCatching { ContentType.parse(mimeType) }.getOrDefault(ContentType.Application.OctetStream)
}

internal const val CAST_PROXY_LOG_TAG = "CastProxy"
