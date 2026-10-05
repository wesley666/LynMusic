package top.iwesley.lyn.music.platform

import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import top.iwesley.lyn.music.core.model.DiagnosticLogLevel
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class AndroidWebDavMetadataFailureTest {
    @org.junit.Before
    @OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
    fun initializeOfficialResourceReader() {
        org.jetbrains.compose.resources.setResourceReaderAndroidContext(org.robolectric.RuntimeEnvironment.getApplication())
    }

    @Test
    fun `tail http failures and timeouts preserve head metadata`() {
        for (timeout in listOf(false, true)) {
            val ranges = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            val head = metadataHead().copyOf(RemoteAudioMetadataProbe.HEAD_PROBE_BYTES.toInt())
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val range = request.header("Range").orEmpty()
                ranges += range
                val isHead = range.startsWith("bytes=0-")
                if (!isHead && timeout) throw SocketTimeoutException("Tail timed out")
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (isHead) 206 else 503)
                    .message(if (isHead) "Partial Content" else "Unavailable")
                    .body((if (isHead) head else ByteArray(0)).toResponseBody())
                    .build()
            }.build()
            val logger = object : DiagnosticLogger {
                override fun log(level: DiagnosticLogLevel, tag: String, message: String, throwable: Throwable?) {
                    if (level == DiagnosticLogLevel.WARN) warnings += message
                }
            }
            val metadata = assertNotNull(readMetadata(client, logger))
            val candidate = buildWebDavImportedTrackCandidate(
                sourceId = "source-1",
                resource = WebDavResolvedResource("File.m4a", false, "File.m4a", FILE_SIZE, 0L),
                metadata = metadata,
            )

            assertEquals("Head Title", candidate.title)
            assertEquals("Head Artist", candidate.artistName)
            assertEquals("Head Album", candidate.albumTitle)
            assertEquals("Head lyrics", candidate.embeddedLyrics)
            assertEquals(listOf("bytes=0-262143", "bytes=262144-1048575"), ranges)
            assertTrue(warnings.any { "metadata-tail-failed" in it })
        }
    }

    @Test
    fun `head io failure still propagates without requesting the tail`() {
        val ranges = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            ranges += chain.request().header("Range").orEmpty()
            throw IOException("Head unavailable")
        }.build()

        assertFailsWith<IOException> { readMetadata(client, NoopDiagnosticLogger) }
        assertEquals(listOf("bytes=0-262143"), ranges)
    }

    private fun readMetadata(client: OkHttpClient, logger: DiagnosticLogger): RemoteAudioMetadata? =
        readAndroidWebDavRemoteMetadata(
            callFactory = client,
            sourceId = "source-1",
            requestUrl = "https://dav.example.com/File.m4a",
            relativePath = "File.m4a",
            sizeBytes = FILE_SIZE,
            authEnabled = false,
            logger = logger,
        )

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
