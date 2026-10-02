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
 */
suspend fun readRemoteSourceUrlBytes(url: String): ByteArray {
    val connection = URL(url).openConnection().apply {
        connectTimeout = remoteSourceUrlConnectTimeoutMillis()
        readTimeout = REMOTE_SOURCE_URL_DEFAULT_TIMEOUT_MILLIS
    }
    try {
        if (connection is HttpURLConnection) {
            val statusCode = connection.responseCode
            markCurrentRemoteSourceReachable()
            if (statusCode !in 200..299) throw IOException("HTTP $statusCode")
        }
        return connection.getInputStream().use { input ->
            markCurrentRemoteSourceReachable()
            input.readBytes()
        }
    } finally {
        (connection as? HttpURLConnection)?.disconnect()
    }
}
