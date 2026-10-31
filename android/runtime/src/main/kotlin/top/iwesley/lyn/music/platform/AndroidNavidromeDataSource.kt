package top.iwesley.lyn.music.platform

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.InterruptedIOException
import java.io.IOException

/** Unknown-length reads need the extractor duration checked before allowing a native retry. */
internal class NavidromeSequentialReadException(cause: IOException) : IOException("Navidrome transcode read failed", cause)

internal fun Throwable.isNavidromeSequentialReadFailure(): Boolean {
    val visited = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && visited.add(current)) {
        if (current is NavidromeSequentialReadException) return true
        current = current.cause
    }
    return false
}

internal enum class NavidromeRangeSupport { Bytes, None, Unknown }

internal data class NavidromeStreamResponse(
    val ranges: NavidromeRangeSupport,
    val durationMs: Long? = null,
)

internal fun navidromeStreamResponse(
    code: Int,
    headers: Map<String, List<String>>,
    rangeProbe: Boolean = false,
): NavidromeStreamResponse {
    fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
    val contentRange = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)", RegexOption.IGNORE_CASE)
        .matchEntire(header("Content-Range").orEmpty().trim())
    val validContentRange = contentRange?.let {
        val start = it.groupValues[1].toLongOrNull()
        val end = it.groupValues[2].toLongOrNull()
        val total = it.groupValues[3].toLongOrNull()
        start != null && end != null && start <= end && (it.groupValues[3] == "*" || (total != null && total > end))
    } == true
    val ranges = when {
        code == 206 && validContentRange -> NavidromeRangeSupport.Bytes
        header("Accept-Ranges")?.trim()?.equals("none", ignoreCase = true) == true -> NavidromeRangeSupport.None
        header("Accept-Ranges")?.trim()?.equals("bytes", ignoreCase = true) == true -> NavidromeRangeSupport.Bytes
        rangeProbe && code == 200 -> NavidromeRangeSupport.None
        else -> NavidromeRangeSupport.Unknown
    }
    val duration = header("X-Content-Duration")?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it > 0 && it < Long.MAX_VALUE / 1000.0 }?.let { (it * 1000).toLong() }
    return NavidromeStreamResponse(ranges, duration)
}

/** Exo owns transport; sequential read failures are handed to whole-track recovery. */
@UnstableApi
internal class AndroidNavidromeDataSource(
    private val upstream: HttpDataSource,
    private val onResponse: (NavidromeStreamResponse) -> Unit,
    private val probeFactory: HttpDataSource.Factory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(3_000).setReadTimeoutMs(3_000),
    private val readRecovery: Boolean = false,
) : DataSource {
    private var observed = false
    private var unknownLength = false
    private var bytesRead = 0L

    override fun open(dataSpec: DataSpec): Long {
        val length = upstream.open(dataSpec)
        unknownLength = length == C.LENGTH_UNSET.toLong()
        bytesRead = 0L
        if (!observed) {
            observed = true
            var response = navidromeStreamResponse(upstream.responseCode, upstream.responseHeaders)
            if (response.ranges == NavidromeRangeSupport.Unknown && dataSpec.position == 0L) {
                val probe = probeFactory.createDataSource()
                try {
                    if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
                    probe.open(dataSpec.buildUpon().setPosition(0L).setLength(1L).build())
                    val probed = navidromeStreamResponse(probe.responseCode, probe.responseHeaders, rangeProbe = true)
                    response = response.copy(ranges = probed.ranges, durationMs = response.durationMs ?: probed.durationMs)
                } catch (interrupted: InterruptedIOException) {
                    if (Thread.currentThread().isInterrupted) throw interrupted
                    // A probe timeout must not turn a successfully opened audio stream into a failure.
                } catch (_: Exception) {
                    // Unknown preserves the existing Exo behaviour.
                } finally {
                    runCatching { probe.close() }
                }
            }
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
            onResponse(response)
        }
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = try {
        upstream.read(buffer, offset, length).also { if (it > 0) bytesRead += it }
    } catch (error: IOException) {
        if (unknownLength && (bytesRead > 0L || readRecovery) &&
            !Thread.currentThread().isInterrupted) {
            throw NavidromeSequentialReadException(error)
        }
        throw error
    }
    override fun getUri(): Uri? = upstream.uri
    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders
    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)
    override fun close() = upstream.close()
}
