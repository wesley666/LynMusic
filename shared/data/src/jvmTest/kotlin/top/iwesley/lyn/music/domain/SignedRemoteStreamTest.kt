package top.iwesley.lyn.music.domain

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.NavidromeLocatorResolver
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.RemotePlaybackUrlCandidate
import top.iwesley.lyn.music.core.model.RemoteRequestSigner
import top.iwesley.lyn.music.core.model.RemoteSourceHttpStatusException
import top.iwesley.lyn.music.core.model.RemoteStreamTruncatedException
import top.iwesley.lyn.music.core.model.SignedRemoteStream
import top.iwesley.lyn.music.core.model.SignedRemoteStreamInputStream
import top.iwesley.lyn.music.core.model.SignedRemoteStreamHooks
import top.iwesley.lyn.music.core.model.readRemoteSourceCandidateBytes
import top.iwesley.lyn.music.core.model.signedStreamConnectTimeoutMillis

class SignedRemoteStreamTest {
    private lateinit var server: HttpServer
    private val rangeRequests = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val noRangeRequests = java.util.concurrent.atomic.AtomicInteger()
    private val base get() = "http://127.0.0.1:${server.address.port}"

    @BeforeTest
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            // A stalled handler must not hold up the next address's request.
            executor = java.util.concurrent.Executors.newCachedThreadPool()
            // Sends the first 3 bytes, then goes silent past the client's read timeout.
            createContext("/stall") { exchange ->
                exchange.sendResponseHeaders(200, PAYLOAD.size.toLong())
                exchange.responseBody.write(PAYLOAD, 0, 3)
                exchange.responseBody.flush()
                runCatching { Thread.sleep(STALL_MILLIS) }
                exchange.close()
            }
            createContext("/ranged") { exchange ->
                val range = exchange.requestHeaders.getFirst("Range")
                rangeRequests += range.orEmpty()
                val start = range?.removePrefix("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                val body = PAYLOAD.copyOfRange(start, PAYLOAD.size)
                if (range != null) {
                    exchange.responseHeaders.add("Content-Range", "bytes $start-${PAYLOAD.size - 1}/${PAYLOAD.size}")
                }
                exchange.sendResponseHeaders(if (range != null) 206 else 200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            // Promises the whole payload, sends 3 bytes and hangs up: HttpURLConnection reads that as a normal end.
            createContext("/truncated") { exchange ->
                exchange.sendResponseHeaders(200, PAYLOAD.size.toLong())
                exchange.responseBody.write(PAYLOAD, 0, 3)
                exchange.responseBody.flush()
                exchange.close()
            }
            // No length at all (chunked): the end of the body is the only signal there is.
            createContext("/chunked") { exchange ->
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.use { it.write(PAYLOAD) }
            }
            // Ignores Range and always sends the whole body, like some FN Music streams.
            createContext("/norange") { exchange ->
                noRangeRequests.incrementAndGet()
                exchange.sendResponseHeaders(200, PAYLOAD.size.toLong())
                exchange.responseBody.use { it.write(PAYLOAD) }
            }
            // 416 for a range at or past the end; `/range416-bare` omits the Content-Range that proves the length.
            createContext("/range416") { exchange ->
                if (!exchange.requestURI.path.endsWith("-bare")) {
                    exchange.responseHeaders.add("Content-Range", "bytes */${PAYLOAD.size}")
                }
                exchange.sendResponseHeaders(416, -1)
                exchange.close()
            }
            createContext("/missing") { exchange ->
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
            // Answers only the refreshed session; a stale one gets 401 or FN Music's JSON "invalid token".
            createContext("/stream") { exchange ->
                val cookie = exchange.requestHeaders.getFirst("Cookie").orEmpty()
                when {
                    cookie == "music-token=new" -> {
                        exchange.sendResponseHeaders(200, PAYLOAD.size.toLong())
                        exchange.responseBody.use { it.write(PAYLOAD) }
                    }
                    exchange.requestURI.query == "json" -> {
                        val body = """{"code":120001,"msg":"invalid token"}""".encodeToByteArray()
                        exchange.responseHeaders.add("Content-Type", "application/json")
                        exchange.sendResponseHeaders(200, body.size.toLong())
                        exchange.responseBody.use { it.write(body) }
                    }
                    else -> {
                        exchange.sendResponseHeaders(401, -1)
                        exchange.close()
                    }
                }
            }
            createContext("/to-cdn") { exchange ->
                exchange.responseHeaders.add("Location", "http://localhost:${server.address.port}/cdn")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            // Covers: the signature must match the exact path and query of each hop, and carry the session token.
            createContext("/cover-old") { exchange ->
                exchange.responseHeaders.add("Location", "/cover-new?size=640")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            createContext("/cover-new") { exchange ->
                respondIfSigned(exchange, token = "old")
            }
            createContext("/cover-auth") { exchange ->
                respondIfSigned(exchange, token = "new")
            }
            createContext("/cdn") { exchange ->
                exchange.sendResponseHeaders(403, -1)
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
    fun `refused session is refreshed once and the stream keeps the new credentials`() = runTest {
        val hooks = RecordingHooks()
        val stream = SignedRemoteStream(listOf(candidate("$base/stream", "old")), hooks)

        assertContentEquals(PAYLOAD, stream.openStream(0L).use { it.inputStream.readBytes() })
        assertContentEquals(PAYLOAD, stream.openStream(0L).use { it.inputStream.readBytes() })

        assertEquals(1, hooks.reauthorizations)
        assertEquals("music-token=new", stream.currentCandidate.headers["Cookie"])
    }

    @Test
    fun `JSON invalid token answer also refreshes the session`() = runTest {
        val hooks = RecordingHooks()
        val stream = SignedRemoteStream(listOf(candidate("$base/stream?json", "old")), hooks)

        assertContentEquals(PAYLOAD, stream.openStream(0L).use { it.inputStream.readBytes() })
        assertEquals(1, hooks.reauthorizations)
    }

    @Test
    fun `unreachable address falls back to the next one and remembers it`() = runTest {
        val hooks = RecordingHooks()
        val stream = SignedRemoteStream(
            listOf(candidate("http://127.0.0.1:1/stream", "new", kind = "LAN"), candidate("$base/stream", "new", kind = "WAN")),
            hooks,
            connectTimeoutMillis = 1_000,
        )

        stream.openStream(0L).close()

        assertEquals(1, stream.selectedIndex)
        assertEquals(listOf("WAN"), hooks.succeeded.map { it.addressKind })
    }

    @Test
    fun `re-resolves once when every address fails`() = runTest {
        val hooks = RecordingHooks(reresolved = listOf(candidate("$base/stream", "new", kind = "WAN")))
        val stream = SignedRemoteStream(listOf(candidate("http://127.0.0.1:1/stream", "new", kind = "LAN")), hooks, connectTimeoutMillis = 1_000)

        stream.openStream(0L).close()

        assertEquals(1, hooks.reresolutions)
        assertEquals("$base/stream", stream.currentCandidate.value)
    }

    @Test
    fun `network change can force a re-resolve before the next open`() = runTest {
        val hooks = RecordingHooks(reresolved = listOf(candidate("$base/stream", "new", kind = "WAN")))
        val stream = SignedRemoteStream(listOf(candidate("$base/stream", "new", kind = "LAN")), hooks)

        stream.invalidateForReresolve()
        stream.openStream(0L).close()

        assertEquals(1, hooks.reresolutions)
        assertEquals("WAN", stream.currentCandidate.addressKind)
    }

    @Test
    fun `refusal from another origin after a redirect does not log in again`() = runTest {
        val hooks = RecordingHooks()
        val stream = SignedRemoteStream(listOf(candidate("$base/to-cdn", "old")), hooks)

        val failure = assertFailsWith<RemoteSourceHttpStatusException> { stream.openStream(0L) }

        assertEquals(403, failure.statusCode)
        assertEquals(0, hooks.reauthorizations)
    }

    @Test
    fun `candidate reads refresh credentials through the installed resolver`() = runTest {
        var refreshes = 0
        NavidromeLocatorRuntime.install(
            object : NavidromeLocatorResolver {
                override suspend fun resolveStreamUrl(locator: String, audioQuality: NavidromeAudioQuality): String? = null
                override suspend fun resolveCoverArtUrl(locator: String): String? = null
                override suspend fun refreshCandidate(
                    candidate: RemotePlaybackUrlCandidate,
                    statusCode: Int,
                    body: String?,
                ): RemotePlaybackUrlCandidate? {
                    refreshes += 1
                    return candidate.copy(headers = withFnMusicToken(candidate.headers, "new"))
                }
            },
        )
        try {
            assertContentEquals(PAYLOAD, readRemoteSourceCandidateBytes(candidate("$base/stream", "old")))
            assertEquals(1, refreshes)
        } finally {
            NavidromeLocatorRuntime.install(NullResolver)
        }
    }

    @Test
    fun `moving on after a failure picks the next address or re-resolves a single one`() = runTest {
        val hooks = RecordingHooks(reresolved = listOf(candidate("$base/stream", "new", kind = "WAN")))
        val pair = SignedRemoteStream(listOf(candidate("$base/stream", "new"), candidate("$base/stream", "new", kind = "WAN")), hooks)
        pair.moveToNextCandidate()
        assertEquals(1, pair.selectedIndex)
        pair.moveToNextCandidate()
        assertEquals(0, pair.selectedIndex)

        val single = SignedRemoteStream(listOf(candidate("$base/stream", "new")), hooks)
        single.moveToNextCandidate()
        single.openStream(0L).close()
        assertEquals(1, hooks.reresolutions)
        assertEquals("WAN", single.currentCandidate.addressKind)
    }

    @Test
    fun `re-resolution is told which addresses failed`() = runTest {
        val down = "http://127.0.0.1:1/stream"
        val afterOpenFailure = RecordingHooks(reresolved = listOf(candidate("$base/stream", "new", kind = "WAN")))
        SignedRemoteStream(listOf(candidate(down, "new")), afterOpenFailure, connectTimeoutMillis = 1_000).openStream(0L).close()
        assertEquals(listOf(listOf(down)), afterOpenFailure.reresolveExclusions)

        val afterBodyFailure = RecordingHooks(reresolved = listOf(candidate("$base/stream", "new", kind = "WAN")))
        val single = SignedRemoteStream(listOf(candidate("$base/stream", "new")), afterBodyFailure)
        single.moveToNextCandidate()
        single.openStream(0L).close()
        assertEquals(listOf(listOf("$base/stream")), afterBodyFailure.reresolveExclusions)

        val afterNetworkChange = RecordingHooks(reresolved = listOf(candidate("$base/stream", "new", kind = "WAN")))
        val switched = SignedRemoteStream(listOf(candidate("$base/stream", "new")), afterNetworkChange)
        switched.invalidateForReresolve()
        switched.openStream(0L).close()
        assertEquals(listOf(emptyList<String>()), afterNetworkChange.reresolveExclusions)
    }

    @Test
    fun `a read that stalls mid-body continues from the same byte on the next address`() = runTest {
        val hooks = RecordingHooks()
        val stream = SignedRemoteStream(
            listOf(candidate("$base/stall", "new"), candidate("$base/ranged", "new", kind = "WAN")),
            hooks,
            readTimeoutMillis = 300,
        )

        val bytes = SignedRemoteStreamInputStream(stream).use { it.readBytes() }

        assertContentEquals(PAYLOAD, bytes)
        assertEquals(listOf("bytes=3-"), rangeRequests.toList())
        assertEquals(1, stream.selectedIndex)
    }

    @Test
    fun `a single stalled address is re-resolved without it and reading resumes`() = runTest {
        val hooks = RecordingHooks(reresolved = listOf(candidate("$base/ranged", "new", kind = "WAN")))
        val stream = SignedRemoteStream(listOf(candidate("$base/stall", "new")), hooks, readTimeoutMillis = 300)

        val bytes = SignedRemoteStreamInputStream(stream).use { it.readBytes() }

        assertContentEquals(PAYLOAD, bytes)
        assertEquals(listOf(listOf("$base/stall")), hooks.reresolveExclusions)
        assertEquals(listOf("bytes=3-"), rangeRequests.toList())
    }

    @Test
    fun `a response that ends early continues on the next address`() = runTest {
        val stream = SignedRemoteStream(
            listOf(candidate("$base/truncated", "new"), candidate("$base/ranged", "new", kind = "WAN")),
            RecordingHooks(),
        )

        val bytes = SignedRemoteStreamInputStream(stream).use { it.readBytes() }

        assertContentEquals(PAYLOAD, bytes)
        assertEquals(listOf("bytes=3-"), rangeRequests.toList())
        assertEquals(1, stream.selectedIndex)
    }

    @Test
    fun `a response that keeps ending early fails instead of returning partial data`() = runTest {
        val hooks = RecordingHooks(reresolved = listOf(candidate("$base/truncated", "new")))
        val stream = SignedRemoteStream(listOf(candidate("$base/truncated", "new")), hooks)

        val failure = assertFailsWith<RemoteStreamTruncatedException> {
            SignedRemoteStreamInputStream(stream).use { it.readBytes() }
        }

        assertEquals(PAYLOAD.size.toLong(), failure.expectedEnd)
        assertTrue(isRemoteSourceAddressFallbackAllowed(failure))
    }

    @Test
    fun `a response without a length ends where the body ends`() = runTest {
        val stream = SignedRemoteStream(listOf(candidate("$base/chunked", "new")), RecordingHooks())

        assertContentEquals(PAYLOAD, SignedRemoteStreamInputStream(stream).use { it.readBytes() })
    }

    @Test
    fun `seeking reopens at the new offset`() = runTest {
        val stream = SignedRemoteStream(listOf(candidate("$base/ranged", "new")), RecordingHooks())
        val reader = SignedRemoteStreamInputStream(stream)

        reader.seekTo(2)

        assertContentEquals(PAYLOAD.copyOfRange(2, PAYLOAD.size), reader.use { it.readBytes() })
        assertEquals(PAYLOAD.size.toLong(), reader.position)
    }

    @Test
    fun `resuming stops when the next address refuses for good`() = runTest {
        val stream = SignedRemoteStream(
            listOf(candidate("$base/stall", "new"), candidate("$base/missing", "new", kind = "WAN")),
            RecordingHooks(),
            readTimeoutMillis = 300,
        )

        val failure = assertFailsWith<RemoteSourceHttpStatusException> {
            SignedRemoteStreamInputStream(stream).use { it.readBytes() }
        }

        assertEquals(404, failure.statusCode)
    }

    @Test
    fun `a server that ignores Range is downloaded once and seeks are served from the copy`() = runTest {
        val stream = SignedRemoteStream(listOf(candidate("$base/norange", "new")), RecordingHooks())
        val reader = SignedRemoteStreamInputStream(stream)

        reader.seekTo(2)
        assertContentEquals(PAYLOAD.copyOfRange(2, PAYLOAD.size), reader.readBytes())
        reader.seekTo(1)
        assertEquals(PAYLOAD[1].toInt() and 0xff, reader.read())
        reader.close()
        assertEquals(1, noRangeRequests.get())

        // close() drops the copy; the next ranged open fetches the resource again.
        stream.close()
        SignedRemoteStreamInputStream(stream, startByte = 3).use { it.readBytes() }
        assertEquals(2, noRangeRequests.get())
    }

    @Test
    fun `a size probe without Range learns the length without keeping a copy`() = runTest {
        val stream = SignedRemoteStream(listOf(candidate("$base/norange", "new")), RecordingHooks())
        val before = noRangeRequests.get()

        // The players' size probe: headers only, so a Range-ignoring server is not downloaded whole at load time.
        assertEquals(PAYLOAD.size.toLong(), stream.openStream(startByte = 0L).use { it.totalLength })
        // Nothing was kept: the first ranged open still has to go to the server (and only then keeps a copy).
        stream.openStream(startByte = 1L).close()
        assertEquals(before + 2, noRangeRequests.get())
        stream.close()
    }

    @Test
    fun `seeking to a known end returns end of stream without a request`() = runTest {
        val stream = SignedRemoteStream(listOf(candidate("$base/ranged", "new")), RecordingHooks())
        val reader = SignedRemoteStreamInputStream(stream)
        reader.ensureOpen()
        val requestsBefore = rangeRequests.size

        reader.seekTo(PAYLOAD.size.toLong())

        assertEquals(-1, reader.read())
        assertEquals(requestsBefore, rangeRequests.size)
        reader.close()
    }

    @Test
    fun `a 416 is end of stream only when its Content-Range proves the offset is past the end`() = runTest {
        val proven = SignedRemoteStream(listOf(candidate("$base/range416", "new")), RecordingHooks())
        assertEquals(-1, SignedRemoteStreamInputStream(proven, startByte = PAYLOAD.size.toLong()).use { it.read() })

        val bare = SignedRemoteStream(listOf(candidate("$base/range416-bare", "new")), RecordingHooks())
        val failure = assertFailsWith<RemoteSourceHttpStatusException> {
            SignedRemoteStreamInputStream(bare, startByte = PAYLOAD.size.toLong()).use { it.read() }
        }
        assertEquals(416, failure.statusCode)

        val early = SignedRemoteStream(listOf(candidate("$base/range416", "new")), RecordingHooks())
        assertFailsWith<RemoteSourceHttpStatusException> {
            SignedRemoteStreamInputStream(early, startByte = 2).use { it.read() }
        }
    }

    @Test
    fun `two reactions to the same failure move the stream only once`() {
        val stream = SignedRemoteStream(
            listOf(candidate("$base/a", "t"), candidate("$base/b", "t", kind = "WAN"), candidate("$base/c", "t", kind = "WAN")),
            RecordingHooks(),
        )
        val seen = stream.generation

        assertTrue(stream.moveToNextCandidate(expectedGeneration = seen))
        assertFalse(stream.moveToNextCandidate(expectedGeneration = seen))
        assertEquals(1, stream.selectedIndex)
        assertTrue(stream.moveToNextCandidate(expectedGeneration = stream.generation))
        assertEquals(2, stream.selectedIndex)
    }

    @Test
    fun `cancelling a reader aborts a read blocked on a stalled address`() {
        val stream = SignedRemoteStream(listOf(candidate("$base/stall", "new")), RecordingHooks(), readTimeoutMillis = 10_000)
        val reader = SignedRemoteStreamInputStream(stream)
        reader.ensureOpen()
        val buffer = ByteArray(16)
        assertEquals(3, reader.read(buffer, 0, buffer.size))

        val canceller = Thread {
            Thread.sleep(200)
            reader.cancel()
        }.apply { start() }
        val started = System.nanoTime()
        assertFailsWith<kotlinx.coroutines.CancellationException> { reader.read(buffer, 0, buffer.size) }
        canceller.join()

        assertTrue((System.nanoTime() - started) / 1_000_000 < STALL_MILLIS / 2, "cancel did not interrupt the blocked read")
    }

    @Test
    fun `a cancelled coroutine closes the bound reader`() = kotlinx.coroutines.runBlocking {
        val stream = SignedRemoteStream(listOf(candidate("$base/stall", "new")), RecordingHooks(), readTimeoutMillis = 10_000)
        val started = System.nanoTime()
        val job = launch(kotlinx.coroutines.Dispatchers.IO) {
            SignedRemoteStreamInputStream(stream).use { reader ->
                reader.bindToCurrentCoroutine()
                reader.ensureOpenSuspending()
                reader.readBytes()
            }
        }
        kotlinx.coroutines.delay(500)
        job.cancel()
        job.join()

        assertTrue((System.nanoTime() - started) / 1_000_000 < STALL_MILLIS / 2, "cancellation waited for the stalled read")
    }

    @Test
    fun `cover reads re-sign each same-origin redirect hop`() = runTest {
        withResolver(signer = PathSigner) {
            assertContentEquals(PAYLOAD, readRemoteSourceCandidateBytes(candidate("$base/cover-old", "old", authx = "signed:/cover-old")))
        }
    }

    @Test
    fun `cover retry after a session refresh is signed again`() = runTest {
        withResolver(signer = PathSigner, refreshedToken = "new") {
            // The resolve-time signature carries the stale token; only a fresh signature matches the refreshed session.
            assertContentEquals(PAYLOAD, readRemoteSourceCandidateBytes(candidate("$base/cover-auth", "old", authx = "signed:/cover-auth")))
        }
    }

    private suspend fun withResolver(
        signer: RemoteRequestSigner,
        refreshedToken: String? = null,
        block: suspend () -> Unit,
    ) {
        NavidromeLocatorRuntime.install(
            object : NavidromeLocatorResolver {
                override suspend fun resolveStreamUrl(locator: String, audioQuality: NavidromeAudioQuality): String? = null
                override suspend fun resolveCoverArtUrl(locator: String): String? = null
                override fun requestSigner(candidate: RemotePlaybackUrlCandidate): RemoteRequestSigner = signer
                override suspend fun refreshCandidate(
                    candidate: RemotePlaybackUrlCandidate,
                    statusCode: Int,
                    body: String?,
                ): RemotePlaybackUrlCandidate? = refreshedToken?.let { candidate.copy(headers = withFnMusicToken(candidate.headers, it)) }
            },
        )
        try {
            block()
        } finally {
            NavidromeLocatorRuntime.install(NullResolver)
        }
    }

    private fun respondIfSigned(exchange: com.sun.net.httpserver.HttpExchange, token: String) {
        val uri = exchange.requestURI
        val target = uri.path + (uri.query?.let { "?$it" } ?: "")
        val expected = "signed:$target:$token"
        if (exchange.requestHeaders.getFirst("authx") == expected && exchange.requestHeaders.getFirst("Cookie") == "music-token=$token") {
            exchange.sendResponseHeaders(200, PAYLOAD.size.toLong())
            exchange.responseBody.use { it.write(PAYLOAD) }
        } else {
            exchange.sendResponseHeaders(401, -1)
            exchange.close()
        }
    }

    /** Signs for the exact path and query, plus the session token, like FN Music's `authx`. */
    private object PathSigner : RemoteRequestSigner {
        override fun sign(url: String, headers: Map<String, String>): Map<String, String> {
            val parsed = java.net.URI(url)
            val target = parsed.rawPath + (parsed.rawQuery?.let { "?$it" } ?: "")
            return headers + ("authx" to "signed:$target:${fnMusicTokenFromHeaders(headers)}")
        }

        override fun redirectHeaders(fromUrl: String, toUrl: String, headers: Map<String, String>): Map<String, String> = headers
    }

    @Test
    fun `LAN addresses with a fallback get the short connect timeout`() {
        val lan = candidate("$base/stream", "t", kind = "LAN")
        val wan = candidate("$base/stream", "t", kind = "WAN")
        assertEquals(5_000, signedStreamConnectTimeoutMillis(lan, hasFallback = true, defaultMillis = 15_000))
        assertEquals(15_000, signedStreamConnectTimeoutMillis(lan, hasFallback = false, defaultMillis = 15_000))
        assertEquals(15_000, signedStreamConnectTimeoutMillis(wan, hasFallback = true, defaultMillis = 15_000))
        assertEquals(1_000, signedStreamConnectTimeoutMillis(lan, hasFallback = true, defaultMillis = 1_000))
    }

    private fun candidate(url: String, token: String, kind: String = "LAN", authx: String? = null) = RemotePlaybackUrlCandidate(
        sourceId = "fnmusic-1",
        addressKind = kind,
        value = url,
        headers = mapOf("Cookie" to "music-token=$token") + listOfNotNull(authx?.let { "authx" to it }),
    )

    private class RecordingHooks(private val reresolved: List<RemotePlaybackUrlCandidate>? = null) : SignedRemoteStreamHooks {
        var reauthorizations = 0
        var reresolutions = 0
        val reresolveExclusions = mutableListOf<List<String>>()
        val succeeded = mutableListOf<RemotePlaybackUrlCandidate>()

        override fun sign(url: String, headers: Map<String, String>): Map<String, String> = headers

        override fun redirectHeaders(fromUrl: String, toUrl: String, headers: Map<String, String>): Map<String, String> = headers

        override fun isAuthFailure(candidate: RemotePlaybackUrlCandidate, statusCode: Int, body: String?): Boolean =
            isFnMusicMediaAuthFailure(statusCode, body)

        override suspend fun reauthorize(candidate: RemotePlaybackUrlCandidate): RemotePlaybackUrlCandidate {
            reauthorizations += 1
            return candidate.copy(headers = withFnMusicToken(candidate.headers, "new"))
        }

        override suspend fun reresolve(failedCandidates: List<RemotePlaybackUrlCandidate>): List<RemotePlaybackUrlCandidate>? {
            reresolutions += 1
            reresolveExclusions += failedCandidates.map { it.value }
            return reresolved
        }

        override fun isAddressFallbackAllowed(failure: Throwable): Boolean = isRemoteSourceAddressFallbackAllowed(failure)

        override fun onSucceeded(candidate: RemotePlaybackUrlCandidate) {
            succeeded += candidate
        }
    }

    private object NullResolver : NavidromeLocatorResolver {
        override suspend fun resolveStreamUrl(locator: String, audioQuality: NavidromeAudioQuality): String? = null
        override suspend fun resolveCoverArtUrl(locator: String): String? = null
    }

    private companion object {
        val PAYLOAD = byteArrayOf(1, 2, 3, 4, 5)

        /** How long `/stall` stays silent; well past every short read timeout and every cancel deadline. */
        const val STALL_MILLIS = 8_000L
    }
}
