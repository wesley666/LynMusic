package top.iwesley.lyn.music.core.model

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val REMOTE_SOURCE_URL_DEFAULT_TIMEOUT_MILLIS = 30_000

/** Connect timeout for a remote-source URL read: the fallback cap while one applies, otherwise the default. */
suspend fun remoteSourceUrlConnectTimeoutMillis(): Int =
    currentRemoteSourceConnectTimeoutMillis()?.toInt() ?: REMOTE_SOURCE_URL_DEFAULT_TIMEOUT_MILLIS

/**
 * Reads a remote-source URL (e.g. artwork) with bounded timeouts, honouring the fallback connect cap
 * from [RemoteSourceConnectTimeout] and marking the address reachable once it answers with any status.
 * Non-2xx answers fail with `HTTP <code>` so the address fallback classifier can judge them.
 * [headers] (e.g. FN Music's signed session headers) are only sent to the URL's own origin: redirects
 * are then followed by hand and a redirect to another origin drops them. A [signer] signs every hop for its own
 * URL and decides which headers may follow a redirect.
 */
suspend fun readRemoteSourceUrlBytes(
    url: String,
    headers: Map<String, String> = emptyMap(),
    signer: RemoteRequestSigner? = null,
): ByteArray {
    var currentUrl = URL(url)
    var currentHeaders = headers
    repeat(REMOTE_SOURCE_URL_MAX_REDIRECTS + 1) {
        val connection = currentUrl.openConnection().apply {
            connectTimeout = remoteSourceUrlConnectTimeoutMillis()
            readTimeout = REMOTE_SOURCE_URL_DEFAULT_TIMEOUT_MILLIS
            val hopHeaders = signer?.sign(currentUrl.toString(), currentHeaders) ?: currentHeaders
            hopHeaders.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            if (connection is HttpURLConnection) {
                if (headers.isNotEmpty()) connection.instanceFollowRedirects = false
                val statusCode = connection.responseCode
                markCurrentRemoteSourceReachable()
                val location = connection.getHeaderField("Location")
                if (headers.isNotEmpty() && statusCode in 300..399 && location != null) {
                    val next = URL(currentUrl, location)
                    if (next.protocol != "http" && next.protocol != "https") throw IOException("HTTP $statusCode")
                    if (currentUrl.protocol == "https" && next.protocol == "http") throw IOException("HTTP $statusCode")
                    currentHeaders = when {
                        signer != null -> signer.redirectHeaders(currentUrl.toString(), next.toString(), currentHeaders)
                            ?: throw IOException("HTTP $statusCode")
                        next.origin() != currentUrl.origin() -> emptyMap()
                        else -> currentHeaders
                    }
                    currentUrl = next
                    return@repeat
                }
                val jsonError = headers.isNotEmpty() && connection.contentType?.contains("json", ignoreCase = true) == true
                if (statusCode !in 200..299 || jsonError) {
                    throw RemoteSourceHttpStatusException(
                        statusCode = statusCode,
                        finalUrl = currentUrl.toString(),
                        contentType = connection.contentType,
                        body = connection.jsonErrorBodyOrNull(),
                    )
                }
            }
            return connection.getInputStream().use { input ->
                markCurrentRemoteSourceReachable()
                input.readBytes()
            }
        } finally {
            (connection as? HttpURLConnection)?.disconnect()
        }
    }
    throw IOException("Too many redirects")
}

/**
 * Reads [candidate] like [readRemoteSourceUrlBytes]; when a signed candidate is refused (e.g. an FN Music session
 * expired), the installed resolver may refresh its credentials once via [NavidromeLocatorRuntime.refreshCandidate].
 */
suspend fun readRemoteSourceCandidateBytes(candidate: RemotePlaybackUrlCandidate): ByteArray {
    val signer = candidate.headers.takeIf { it.isNotEmpty() }?.let { NavidromeLocatorRuntime.requestSigner(candidate) }
    return try {
        readRemoteSourceUrlBytes(candidate.value, candidate.headers, signer)
    } catch (failure: RemoteSourceHttpStatusException) {
        if (candidate.headers.isEmpty()) throw failure
        val refreshed = NavidromeLocatorRuntime.refreshCandidate(candidate, failure.statusCode, failure.body) ?: throw failure
        readRemoteSourceUrlBytes(refreshed.value, refreshed.headers, signer)
    }
}

private const val REMOTE_SOURCE_URL_MAX_REDIRECTS = 5

private fun URL.origin(): String = "${protocol.lowercase()}://${host.lowercase()}:${if (port == -1) defaultPort else port}"
