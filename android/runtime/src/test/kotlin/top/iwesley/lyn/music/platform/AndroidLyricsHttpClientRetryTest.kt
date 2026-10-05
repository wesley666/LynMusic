package top.iwesley.lyn.music.platform

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.RequestMethod

class AndroidLyricsHttpClientRetryTest {
    private fun newClient() = AndroidLyricsHttpClient()

    @Suppress("UNUSED_PARAMETER")
    private fun closeClient(client: AndroidLyricsHttpClient) = Unit

    /**
     * The case the flag exists for: a keep-alive connection that served a GET, then a POST the server receives but
     * whose connection drops before any response. OkHttp's default retry would send that POST again.
     */
    @Test fun writeWithoutTransportRetryIsSentOnce() = runBlocking {
        val server = WriteServer(WriteReply.DROP_CONNECTION)
        assertTrue(server.post(allowTransportRetry = false).isFailure)
        assertEquals(1, server.posts.get())
    }

    /** Guards the test itself: without the flag OkHttp really does resend the POST (more than once). */
    @Test fun defaultRequestIsStillRetriedByOkHttp() = runBlocking {
        val server = WriteServer(WriteReply.DROP_CONNECTION)
        server.post(allowTransportRetry = true)
        assertTrue(server.posts.get() > 1, "expected OkHttp to resend, got ${server.posts.get()}")
    }

    /** OkHttp repeats a request answered `503` with `Retry-After: 0` even with connection retries off. */
    @Test fun writeAnswered503RetryAfterZeroIsNotResent() = runBlocking {
        val server = WriteServer(WriteReply.UNAVAILABLE_RETRY_NOW)
        assertEquals(503, server.post(allowTransportRetry = false).getOrThrow().statusCode)
        assertEquals(1, server.posts.get())
        // The one-shot body still arrives whole, with the caller's content type.
        assertEquals(WRITE_BODY, server.lastBody)
        assertEquals("application/json", server.lastContentType)

        val defaultServer = WriteServer(WriteReply.UNAVAILABLE_RETRY_NOW)
        defaultServer.post(allowTransportRetry = true)
        assertEquals(2, defaultServer.posts.get())
    }

    private enum class WriteReply { DROP_CONNECTION, UNAVAILABLE_RETRY_NOW }

    /** Answers GETs with keep-alive `200`s; counts each POST, then replies as [reply] says. */
    private inner class WriteServer(private val reply: WriteReply) {
        val posts = AtomicInteger()

        @Volatile
        var lastBody: String? = null

        @Volatile
        var lastContentType: String? = null

        /** Warms a keep-alive connection with a GET, then sends the write over it. */
        suspend fun post(allowTransportRetry: Boolean): Result<LyricsHttpResponse> {
            ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { server ->
                thread(isDaemon = true) {
                    while (!server.isClosed) {
                        val socket = runCatching { server.accept() }.getOrNull() ?: break
                        thread(isDaemon = true) { serve(socket) }
                    }
                }
                val client = newClient()
                try {
                    val base = "http://127.0.0.1:${server.localPort}"
                    assertEquals(200, client.request(LyricsRequest(RequestMethod.GET, "$base/warm")).getOrThrow().statusCode)
                    return client.request(
                        LyricsRequest(
                            method = RequestMethod.POST,
                            url = "$base/write",
                            headers = mapOf("Content-Type" to "application/json"),
                            body = WRITE_BODY,
                            allowTransportRetry = allowTransportRetry,
                        ),
                    )
                } finally {
                    closeClient(client)
                }
            }
        }

        private fun serve(socket: Socket) {
            socket.use {
                val input = socket.getInputStream().buffered()
                val output = socket.getOutputStream()
                while (true) {
                    val requestLine = input.readHttpLine() ?: return
                    var contentLength = 0
                    var contentType: String? = null
                    while (true) {
                        val header = input.readHttpLine() ?: return
                        if (header.isEmpty()) break
                        val name = header.substringBefore(':').trim()
                        val value = header.substringAfter(':').trim()
                        if (name.equals("Content-Length", ignoreCase = true)) contentLength = value.toInt()
                        if (name.equals("Content-Type", ignoreCase = true)) contentType = value
                    }
                    val body = ByteArray(contentLength).also { bytes ->
                        var read = 0
                        while (read < contentLength) {
                            val count = input.read(bytes, read, contentLength - read)
                            if (count < 0) return
                            read += count
                        }
                    }
                    val response = if (requestLine.startsWith("POST")) {
                        // Applied on the server either way.
                        posts.incrementAndGet()
                        lastBody = body.decodeToString()
                        lastContentType = contentType
                        when (reply) {
                            WriteReply.DROP_CONNECTION -> return
                            WriteReply.UNAVAILABLE_RETRY_NOW ->
                                "HTTP/1.1 503 Service Unavailable\r\nRetry-After: 0\r\nContent-Length: 0\r\n\r\n"
                        }
                    } else {
                        "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nContent-Type: text/plain\r\n\r\nok"
                    }
                    output.write(response.toByteArray())
                    output.flush()
                }
            }
        }
    }

    private fun java.io.InputStream.readHttpLine(): String? {
        val line = StringBuilder()
        while (true) {
            val byte = read()
            if (byte < 0) return null
            if (byte == '\n'.code) return line.toString().trimEnd('\r')
            line.append(byte.toChar())
        }
    }
}

private const val WRITE_BODY = "{\"id\":1,\"name\":\"歌单\"}"
