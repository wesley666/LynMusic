package top.iwesley.lyn.music.platform

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.DiagnosticLogLevel
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger

class JvmWebDavMetadataFailureTest {
    @Test
    fun `tail http failure preserves head metadata and reports a warning`() {
        withRangeServer { url, ranges ->
            val warnings = mutableListOf<String>()
            val logger = object : DiagnosticLogger {
                override fun log(level: DiagnosticLogLevel, tag: String, message: String, throwable: Throwable?) {
                    if (level == DiagnosticLogLevel.WARN) warnings += message
                }
            }
            val metadata = assertNotNull(readMetadata(url, logger))
            val candidate = buildWebDavImportedTrackCandidate(
                sourceId = "source-1",
                resource = WebDavResolvedResource("File.m4a", false, "File.m4a", FILE_SIZE, 0L),
                metadata = metadata,
            )

            assertEquals("Head Title", candidate.title)
            assertEquals("Head Artist", candidate.artistName)
            assertEquals("Head Album", candidate.albumTitle)
            assertEquals("Head lyrics", candidate.embeddedLyrics)
            assertEquals(listOf("bytes=0-262143", "bytes=262144-1048575"), ranges.toList())
            assertTrue(warnings.any { "metadata-tail-failed" in it })
        }
    }

    @Test
    fun `head http failure still propagates without requesting the tail`() {
        withRangeServer(failHead = true) { url, ranges ->
            assertFailsWith<IOException> { readMetadata(url, NoopDiagnosticLogger) }
            assertEquals(listOf("bytes=0-262143"), ranges.toList())
        }
    }

    private fun readMetadata(url: String, logger: DiagnosticLogger): RemoteAudioMetadata? = readJvmWebDavRemoteMetadata(
        requestUrl = url,
        username = "",
        password = "",
        allowInsecureTls = false,
        relativePath = "File.m4a",
        sizeBytes = FILE_SIZE,
        authEnabled = false,
        logger = logger,
        sourceId = "source-1",
    )

    private fun withRangeServer(failHead: Boolean = false, block: (String, List<String>) -> Unit) {
        val ranges = CopyOnWriteArrayList<String>()
        val head = metadataHead().copyOf(RemoteAudioMetadataProbe.HEAD_PROBE_BYTES.toInt())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/File.m4a") { exchange ->
            try {
                val range = exchange.requestHeaders.getFirst("Range").orEmpty()
                ranges += range
                val isHead = range.startsWith("bytes=0-")
                val success = isHead && !failHead
                val body = if (success) head else "Unavailable".encodeToByteArray()
                if (success) exchange.responseHeaders.set("Content-Range", "bytes 0-${head.lastIndex}/$FILE_SIZE")
                exchange.sendResponseHeaders(if (success) 206 else 503, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            } finally {
                exchange.close()
            }
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/File.m4a", ranges)
        } finally {
            server.stop(0)
        }
    }

    private fun metadataHead(): ByteArray {
        fun atom(type: String, body: ByteArray): ByteArray =
            ByteBuffer.allocate(4).putInt(body.size + 8).array() + type.toByteArray(Charsets.ISO_8859_1) + body
        fun field(type: String, value: String): ByteArray = atom(type, atom("data", ByteArray(8) + value.encodeToByteArray()))
        val fields = field("©nam", "Head Title") + field("©ART", "Head Artist") +
            field("©alb", "Head Album") + field("©lyr", "Head lyrics")
        return atom("ftyp", "M4A ".encodeToByteArray()) +
            atom("moov", atom("udta", atom("meta", ByteArray(4) + atom("ilst", fields))))
    }

    private companion object {
        const val FILE_SIZE = 1_048_576L
    }
}
