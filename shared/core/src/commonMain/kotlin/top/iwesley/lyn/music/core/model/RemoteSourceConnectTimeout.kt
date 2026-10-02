package top.iwesley.lyn.music.core.model

import kotlin.concurrent.Volatile
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext

/**
 * Caps the connect timeout of remote-source requests made while an alternate server address is still
 * available, so an unreachable LAN address falls back quickly instead of waiting for the full timeout.
 *
 * The cap only lasts until the address answers once: after [markReachable], later requests in the same
 * operation (e.g. the remaining pages of a scan) use the normal timeouts again. It is read when each request
 * starts, so requests already in flight before the first answer keep the cap.
 */
class RemoteSourceConnectTimeout(
    val millis: Long,
) : AbstractCoroutineContextElement(Key) {
    @Volatile
    var reachable: Boolean = false
        private set

    fun markReachable() {
        reachable = true
    }

    companion object Key : CoroutineContext.Key<RemoteSourceConnectTimeout>
}

const val REMOTE_SOURCE_FALLBACK_CONNECT_TIMEOUT_MILLIS = 5_000L

suspend fun currentRemoteSourceConnectTimeoutMillis(): Long? =
    currentCoroutineContext()[RemoteSourceConnectTimeout]
        ?.takeUnless { it.reachable }
        ?.millis
        ?.takeIf { it > 0L }

/** Records that the current address answered, lifting the fallback cap for the rest of the operation. */
suspend fun markCurrentRemoteSourceReachable() {
    currentCoroutineContext()[RemoteSourceConnectTimeout]?.markReachable()
}

/** Resolves the connect timeout for a request: the shorter of the request's own timeout and the fallback cap. */
suspend fun LyricsRequest.effectiveConnectTimeoutMillis(): Long? {
    val requestTimeout = timeoutMillis?.takeIf { it > 0L }
    val fallbackCap = currentRemoteSourceConnectTimeoutMillis()
    return listOfNotNull(requestTimeout, fallbackCap).minOrNull()
}
