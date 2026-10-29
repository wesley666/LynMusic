package top.iwesley.lyn.music.domain

import io.ktor.http.URLBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.LyricsHttpClient

enum class NavidromeTranscodeOffsetSupport { Supported, Unsupported, Unknown }

/** Session-only cache. A failed probe is retryable; changing credentials or addresses retires old work. */
class NavidromeTranscodeCapabilities(
    private val scope: CoroutineScope,
    private val httpClient: LyricsHttpClient,
) {
    private class Entry(val source: NavidromeResolvedSource, val task: Deferred<NavidromeTranscodeOffsetSupport>)
    private val mutex = Mutex()
    private val entries = mutableMapOf<String, Entry>()

    suspend fun support(source: NavidromeResolvedSource): NavidromeTranscodeOffsetSupport {
        if (source.sourceType != ImportSourceType.NAVIDROME) return NavidromeTranscodeOffsetSupport.Unsupported
        val key = source.sourceId ?: source.baseUrl
        // The selector is mutable routing state, not server configuration.
        val identity = source.copy(addressSelector = null)
        val entry = mutex.withLock {
            entries[key]?.takeIf { it.source == identity } ?: run {
                entries.remove(key)?.task?.cancel()
                Entry(identity, scope.async(start = CoroutineStart.LAZY) {
                    withTimeoutOrNull(30_000L) {
                        requestNavidromeTranscodeOffsetSupport(httpClient, source)
                    } ?: NavidromeTranscodeOffsetSupport.Unknown
                }).also { entries[key] = it }
            }
        }
        val result = entry.task.await()
        if (result == NavidromeTranscodeOffsetSupport.Unknown) {
            mutex.withLock { if (entries[key] === entry) entries.remove(key) }
        }
        return result
    }
}

internal suspend fun requestNavidromeTranscodeOffsetSupport(
    httpClient: LyricsHttpClient,
    source: NavidromeResolvedSource,
): NavidromeTranscodeOffsetSupport = try {
    val payload = requestNavidromeJson(httpClient, source, "getOpenSubsonicExtensions", timeoutMillis = 30_000L)
    val extensions = payload["openSubsonicExtensions"] as? JsonArray
    if (extensions == null) {
        NavidromeTranscodeOffsetSupport.Unknown
    } else if (extensions.any { element ->
        val extension = element as? JsonObject
        (extension?.get("name") as? JsonPrimitive)?.contentOrNull == "transcodeOffset" &&
            (extension["versions"] as? JsonArray)?.any { (it as? JsonPrimitive)?.intOrNull == 1 } == true
    }) {
        NavidromeTranscodeOffsetSupport.Supported
    } else if (extensions.any { element ->
        val extension = element as? JsonObject
        val versions = extension?.get("versions") as? JsonArray
        (extension?.get("name") as? JsonPrimitive)?.contentOrNull == null ||
            versions == null || versions.any { (it as? JsonPrimitive)?.intOrNull == null }
    }) {
        NavidromeTranscodeOffsetSupport.Unknown
    } else {
        NavidromeTranscodeOffsetSupport.Unsupported
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    NavidromeTranscodeOffsetSupport.Unknown
}

/** Rebuild from the original signed URL, preserving its authentication, quality and server subpath. */
fun navidromeStreamUrlAtOffset(url: String, offsetSeconds: Long): String {
    require(offsetSeconds >= 0L)
    return URLBuilder(url).apply {
        parameters.remove("timeOffset")
        if (offsetSeconds > 0L) parameters.append("timeOffset", offsetSeconds.toString())
    }.buildString()
}
