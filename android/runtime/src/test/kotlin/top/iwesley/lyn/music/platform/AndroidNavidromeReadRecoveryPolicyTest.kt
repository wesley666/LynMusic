package top.iwesley.lyn.music.platform

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.IOException
import java.io.InterruptedIOException
import java.lang.reflect.Proxy
import java.net.SocketTimeoutException
import kotlin.test.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidNavidromeReadRecoveryPolicyTest {
    @Test fun sequentialUnknownLengthReadsKeepTheOriginalNetworkCause() {
        val original = networkError()
        val f = source(error = original)
        f.open(spec)
        f.read(ByteArray(1), 0, 1)
        val marked = assertFailsWith<NavidromeSequentialReadException> { f.read(ByteArray(1), 0, 1) }
        assertSame(original, marked.cause)
        assertTrue(IOException("outer", marked).isNavidromeSequentialReadFailure())
        assertEquals(C.TIME_UNSET, NavidromeLoadErrorHandlingPolicy().getRetryDelayMsFor(loadError(marked)))
    }

    @Test fun completeResourcesKeepNativeReadRetries() {
        val original = networkError()
        val f = source(length = 100L, error = original)
        f.open(spec)
        f.read(ByteArray(1), 0, 1)
        assertSame(original, assertFailsWith<IOException> { f.read(ByteArray(1), 0, 1) })
        assertFalse(original.isNavidromeSequentialReadFailure())
        assertEquals(1_000L, NavidromeLoadErrorHandlingPolicy().getRetryDelayMsFor(loadError(original, count = 2)))
    }

    @Test fun aRangeDeclarationDoesNotMakeAnUnknownLengthReadSafeForNativeRetry() {
        val original = networkError()
        val f = source(ranges = "bytes", error = original, readRecovery = true)
        f.open(spec)
        f.read(ByteArray(1), 0, 1)
        val marked = assertFailsWith<NavidromeSequentialReadException> { f.read(ByteArray(1), 0, 1) }
        assertSame(original, marked.cause)
        assertEquals(C.TIME_UNSET, NavidromeLoadErrorHandlingPolicy().getRetryDelayMsFor(loadError(marked)))
        assertEquals(1_000L, NavidromeLoadErrorHandlingPolicy()
            .getRetryDelayMsFor(loadError(marked, count = 2, endTimeMs = 120_000L)))
    }

    @Test fun byteRangeResponsesWithAnActualLengthKeepNativeReadRetries() {
        val original = networkError()
        val f = source(length = 100L, ranges = "bytes", error = original, readRecovery = true)
        f.open(spec)
        f.read(ByteArray(1), 0, 1)
        assertSame(original, assertFailsWith<IOException> { f.read(ByteArray(1), 0, 1) })
        assertFalse(original.isNavidromeSequentialReadFailure())
    }

    @Test fun anErrorBeforeDeliveringAnyDataKeepsTheExistingPolicy() {
        val original = networkError()
        val f = source(error = original, deliverByte = false)
        f.open(spec)
        assertSame(original, assertFailsWith<IOException> { f.read(ByteArray(1), 0, 1) })
        assertFalse(original.isNavidromeSequentialReadFailure())
    }

    @Test fun openingFailuresAreNotConvertedToSequentialReadFailures() {
        val original = networkError(HttpDataSource.HttpDataSourceException.TYPE_OPEN)
        val f = source(error = original, failOpen = true)
        assertSame(original, assertFailsWith<IOException> { f.open(spec) })
        assertFalse(original.isNavidromeSequentialReadFailure())
    }

    @Test fun recoveryOpeningFailuresUseTheSessionBudgetWithoutNativeRetries() {
        val error = networkError(HttpDataSource.HttpDataSourceException.TYPE_OPEN)
        assertEquals(C.TIME_UNSET, NavidromeLoadErrorHandlingPolicy(recoveringOpen = true).getRetryDelayMsFor(loadError(error)))
        assertEquals(0L, NavidromeLoadErrorHandlingPolicy().getRetryDelayMsFor(loadError(error)))
    }

    @Test fun extractorDurationKeepsNativeReadRetriesIncludingDuringRecovery() {
        val error = NavidromeSequentialReadException(networkError())
        for (recovering in listOf(false, true)) {
            val info = loadError(error, count = 2, endTimeMs = 120_000L)
            assertEquals(DefaultLoadErrorHandlingPolicy().getRetryDelayMsFor(info),
                NavidromeLoadErrorHandlingPolicy(recoveringOpen = recovering).getRetryDelayMsFor(info))
        }
    }

    @Test fun zeroDurationBeforeOpeningDoesNotBypassTheRecoveryBudget() {
        for (error in listOf(NavidromeSequentialReadException(networkError()),
            networkError(HttpDataSource.HttpDataSourceException.TYPE_OPEN))) {
            assertEquals(C.TIME_UNSET, NavidromeLoadErrorHandlingPolicy(recoveringOpen = true)
                .getRetryDelayMsFor(loadError(error, endTimeMs = 0L)))
        }
    }

    @Test fun nativeReopensAfterDurationIsParsedKeepDefaultRetries() {
        val error = networkError(HttpDataSource.HttpDataSourceException.TYPE_OPEN)
        val info = loadError(error, count = 2, endTimeMs = 120_000L)
        assertEquals(1_000L, NavidromeLoadErrorHandlingPolicy(recoveringOpen = true).getRetryDelayMsFor(info))
    }

    @Test fun responseDurationDoesNotMakeAnUnknownDurationStreamSafeForNativeRetry() {
        val info = loadError(NavidromeSequentialReadException(networkError()),
            responseHeaders = mapOf("X-Content-Duration" to listOf("120")))
        assertEquals(C.TIME_UNSET, info.mediaLoadData.mediaEndTimeMs)
        assertEquals(C.TIME_UNSET, NavidromeLoadErrorHandlingPolicy().getRetryDelayMsFor(info))
    }

    @Test fun aRecoveryReadFailureBeforeTheFirstByteStillUsesTheSessionBudget() {
        val f = source(error = networkError(), deliverByte = false, readRecovery = true)
        f.open(spec)
        assertFailsWith<NavidromeSequentialReadException> { f.read(ByteArray(1), 0, 1) }
    }

    @Test fun threadCancellationIsNotConvertedToAutomaticRecovery() {
        val original = HttpDataSource.HttpDataSourceException(
            InterruptedIOException("canceled"), spec, PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            HttpDataSource.HttpDataSourceException.TYPE_READ,
        )
        val f = source(error = original, interruptRead = true)
        f.open(spec)
        f.read(ByteArray(1), 0, 1)
        try {
            assertSame(original, assertFailsWith<IOException> { f.read(ByteArray(1), 0, 1) })
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
        assertFalse(original.isNavidromeSequentialReadFailure())
    }

    @Test fun ordinaryParserAndNetworkErrorsRetainTheDefaultPolicy() {
        val policy = NavidromeLoadErrorHandlingPolicy()
        val default = DefaultLoadErrorHandlingPolicy()
        for (error in listOf(networkError(), ParserException.createForMalformedContainer("invalid MP3", null))) {
            assertEquals(default.getRetryDelayMsFor(loadError(error)), policy.getRetryDelayMsFor(loadError(error)))
        }
    }

    private val spec = DataSpec(Uri.parse("https://example.test/rest/stream?id=song"))

    private fun networkError(type: Int = HttpDataSource.HttpDataSourceException.TYPE_READ) =
        HttpDataSource.HttpDataSourceException(SocketTimeoutException("read timed out"), spec,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, type)

    private fun loadError(error: IOException, count: Int = 1, endTimeMs: Long = C.TIME_UNSET,
                          responseHeaders: Map<String, List<String>> = emptyMap()) = LoadErrorHandlingPolicy.LoadErrorInfo(
        LoadEventInfo.Builder(1L, spec, 0L).setResponseHeaders(responseHeaders).build(),
        MediaLoadData(C.DATA_TYPE_MEDIA, C.TRACK_TYPE_UNKNOWN, null, C.SELECTION_REASON_UNKNOWN, null, 0L, endTimeMs),
        error, count,
    )

    private fun source(
        length: Long = C.LENGTH_UNSET.toLong(),
        ranges: String = "none",
        error: IOException,
        deliverByte: Boolean = true,
        failOpen: Boolean = false,
        interruptRead: Boolean = false,
        readRecovery: Boolean = false,
    ): AndroidNavidromeDataSource {
        var firstRead = deliverByte
        val upstream = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(HttpDataSource::class.java)) { _, method, args ->
            when (method.name) {
                "open" -> if (failOpen) throw error else length
                "getResponseCode" -> 200
                "getResponseHeaders" -> mapOf("Accept-Ranges" to listOf(ranges))
                "getUri" -> spec.uri
                "read" -> if (firstRead) {
                    firstRead = false
                    (args!![0] as ByteArray)[args[1] as Int] = 0
                    1
                } else {
                    if (interruptRead) Thread.currentThread().interrupt()
                    throw error
                }
                else -> null
            }
        } as HttpDataSource
        return AndroidNavidromeDataSource(upstream, {}, readRecovery = readRecovery)
    }
}
