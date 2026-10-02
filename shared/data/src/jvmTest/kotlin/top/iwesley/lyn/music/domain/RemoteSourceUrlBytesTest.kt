package top.iwesley.lyn.music.domain

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.REMOTE_SOURCE_FALLBACK_CONNECT_TIMEOUT_MILLIS
import top.iwesley.lyn.music.core.model.RemoteSourceConnectTimeout
import top.iwesley.lyn.music.core.model.readRemoteSourceUrlBytes
import top.iwesley.lyn.music.core.model.remoteSourceUrlConnectTimeoutMillis

class RemoteSourceUrlBytesTest {
    private lateinit var server: HttpServer

    @BeforeTest
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/ok") { exchange ->
                exchange.sendResponseHeaders(200, PAYLOAD.size.toLong())
                exchange.responseBody.use { it.write(PAYLOAD) }
            }
            createContext("/missing") { exchange ->
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
            createContext("/broken") { exchange ->
                exchange.sendResponseHeaders(500, -1)
                exchange.close()
            }
            start()
        }
    }

    @AfterTest
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun `successful read returns payload and marks the address reachable`() = runTest {
        val cap = RemoteSourceConnectTimeout(REMOTE_SOURCE_FALLBACK_CONNECT_TIMEOUT_MILLIS)

        val bytes = withContext(cap) { readRemoteSourceUrlBytes(url("/ok")) }

        assertContentEquals(PAYLOAD, bytes)
        assertTrue(cap.reachable)
    }

    @Test
    fun `client error answers mark reachable and do not allow address fallback`() = runTest {
        val cap = RemoteSourceConnectTimeout(REMOTE_SOURCE_FALLBACK_CONNECT_TIMEOUT_MILLIS)

        val failure = assertFailsWith<IOException> {
            withContext(cap) { readRemoteSourceUrlBytes(url("/missing")) }
        }

        assertEquals("HTTP 404", failure.message)
        assertTrue(cap.reachable)
        assertFalse(isRemoteSourceAddressFallbackAllowed(failure))
    }

    @Test
    fun `server error answers mark reachable and allow address fallback`() = runTest {
        val cap = RemoteSourceConnectTimeout(REMOTE_SOURCE_FALLBACK_CONNECT_TIMEOUT_MILLIS)

        val failure = assertFailsWith<IOException> {
            withContext(cap) { readRemoteSourceUrlBytes(url("/broken")) }
        }

        assertEquals("HTTP 500", failure.message)
        assertTrue(cap.reachable)
        assertTrue(isRemoteSourceAddressFallbackAllowed(failure))
    }

    @Test
    fun `connect timeout follows the fallback cap`() = runTest {
        assertEquals(30_000, remoteSourceUrlConnectTimeoutMillis())
        assertEquals(
            REMOTE_SOURCE_FALLBACK_CONNECT_TIMEOUT_MILLIS.toInt(),
            withContext(RemoteSourceConnectTimeout(REMOTE_SOURCE_FALLBACK_CONNECT_TIMEOUT_MILLIS)) {
                remoteSourceUrlConnectTimeoutMillis()
            },
        )
    }

    private fun url(path: String): String = "http://127.0.0.1:${server.address.port}$path"

    private companion object {
        val PAYLOAD = byteArrayOf(1, 2, 3, 4)
    }
}
