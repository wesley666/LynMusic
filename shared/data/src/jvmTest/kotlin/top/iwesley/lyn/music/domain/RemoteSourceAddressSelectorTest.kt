package top.iwesley.lyn.music.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.NetworkConnectionState
import top.iwesley.lyn.music.core.model.NetworkConnectionType
import top.iwesley.lyn.music.core.model.NetworkConnectionTypeProvider
import top.iwesley.lyn.music.core.model.REMOTE_SOURCE_FALLBACK_CONNECT_TIMEOUT_MILLIS
import top.iwesley.lyn.music.core.model.RemotePlaybackUrlCandidate
import top.iwesley.lyn.music.core.model.currentRemoteSourceConnectTimeoutMillis
import top.iwesley.lyn.music.core.model.markCurrentRemoteSourceReachable
import top.iwesley.lyn.music.core.model.UiTextArgumentException
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.resources.*

class RemoteSourceAddressSelectorTest {
    @Test
    fun `offline download http failures retain status through wrapping and translation`() = runTest {
        for (statusCode in listOf(200, 206, 299)) {
            checkOfflineDownloadHttpStatus(statusCode)
        }
        for (statusCode in listOf(400, 401, 403, 404, 408, 500, 503, 599)) {
            val failure = assertFailsWith<UiTextException> { checkOfflineDownloadHttpStatus(statusCode) }
            assertEquals(statusCode, (failure as RemoteSourceHttpFailure).httpStatusCode)
            val retryable = statusCode == 408 || statusCode in 500..599
            assertEquals(retryable, isRemoteSourceAddressFallbackAllowed(failure))
            assertEquals(retryable, isRemoteSourceAddressFallbackAllowed(IllegalStateException("wrapper", failure)))
            for ((language, expected) in listOf(
                AppLanguage.English to "Download failed: HTTP $statusCode.",
                AppLanguage.SimplifiedChinese to "下载失败，HTTP $statusCode。",
                AppLanguage.TraditionalChinese to "下載失敗，HTTP $statusCode。",
            )) {
                assertEquals(expected, resolveUiText(failure.text, language))
            }
        }
    }

    @Test
    fun `offline download http timeouts and server errors attempt the alternate address`() = runTest {
        for (statusCode in listOf(408, 500, 503)) {
            val selector = RemoteSourceAddressSelector(TestNetworkConnectionTypeProvider(NetworkConnectionType.WIFI))
            val attempts = mutableListOf<RemoteSourceAddressKind>()
            val result = selector.withAddressFallback(
                sourceId = TEST_SOURCE_ID,
                sourceType = ImportSourceType.NAVIDROME,
                lanBaseUrl = LAN_URL,
                wanBaseUrl = WAN_URL,
                normalizeBaseUrl = ::identity,
            ) { candidate ->
                attempts += candidate.kind
                checkOfflineDownloadHttpStatus(if (candidate.kind == RemoteSourceAddressKind.LAN) statusCode else 200)
                "downloaded"
            }
            assertEquals("downloaded", result)
            assertEquals(listOf(RemoteSourceAddressKind.LAN, RemoteSourceAddressKind.WAN), attempts)
            assertEquals(RemoteSourceAddressKind.WAN, selector.testOrder().first().kind)
        }
    }

    @Test
    fun `offline download http authorization and client errors do not attempt the alternate address`() = runTest {
        for (statusCode in listOf(400, 401, 403, 404)) {
            val selector = RemoteSourceAddressSelector(TestNetworkConnectionTypeProvider(NetworkConnectionType.WIFI))
            val attempts = mutableListOf<RemoteSourceAddressKind>()
            assertFailsWith<UiTextException> {
                selector.withAddressFallback(
                    sourceId = TEST_SOURCE_ID,
                    sourceType = ImportSourceType.NAVIDROME,
                    lanBaseUrl = LAN_URL,
                    wanBaseUrl = WAN_URL,
                    normalizeBaseUrl = ::identity,
                ) { candidate ->
                    attempts += candidate.kind
                    checkOfflineDownloadHttpStatus(statusCode)
                }
            }
            assertEquals(listOf(RemoteSourceAddressKind.LAN), attempts)
        }
    }

    @Test
    fun `resource diagnostics never become network classification input`() {
        assertFalse(isRemoteSourceAddressFallbackAllowed(UiTextException(uiText(Res.string.source_credentials_missing, "network"))))
        assertFalse(isRemoteSourceAddressFallbackAllowed(UiTextException(uiText(Res.string.source_folder_missing, "/network/connection"))))
        assertTrue(isRemoteSourceAddressFallbackAllowed(UiTextException(UiText.Raw("HTTP 503"))))
    }

    @Test
    fun `classifier terminates for cyclic causes`() {
        val first = IllegalStateException("codec unsupported")
        val second = IllegalStateException("wrapper", first)
        first.initCause(second)
        assertFalse(isRemoteSourceAddressFallbackAllowed(first))
    }

    @Test
    fun `wifi prefers lan and mobile prefers wan`() {
        val networkProvider = TestNetworkConnectionTypeProvider(NetworkConnectionType.WIFI)
        val selector = RemoteSourceAddressSelector(networkProvider)

        assertEquals(
            listOf(RemoteSourceAddressKind.LAN, RemoteSourceAddressKind.WAN),
            selector.testOrder().map { it.kind },
        )

        networkProvider.publish(NetworkConnectionType.MOBILE)

        assertEquals(
            listOf(RemoteSourceAddressKind.WAN, RemoteSourceAddressKind.LAN),
            selector.testOrder().map { it.kind },
        )
    }

    @Test
    fun `fallback retries alternate address for retryable failures`() = runTest {
        val networkProvider = TestNetworkConnectionTypeProvider(NetworkConnectionType.WIFI)
        val selector = RemoteSourceAddressSelector(networkProvider)
        val attempts = mutableListOf<RemoteSourceAddressKind>()

        val result = selector.withAddressFallback(
            sourceId = TEST_SOURCE_ID,
            sourceType = ImportSourceType.NAVIDROME,
            lanBaseUrl = LAN_URL,
            wanBaseUrl = WAN_URL,
            normalizeBaseUrl = ::identity,
        ) { candidate ->
            attempts += candidate.kind
            if (candidate.kind == RemoteSourceAddressKind.LAN) {
                error("请求失败: timeout")
            }
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(listOf(RemoteSourceAddressKind.LAN, RemoteSourceAddressKind.WAN), attempts)
    }

    @Test
    fun `successful address is cached until network version changes`() = runTest {
        val networkProvider = TestNetworkConnectionTypeProvider(NetworkConnectionType.WIFI)
        val selector = RemoteSourceAddressSelector(networkProvider)

        selector.markSuccess(TEST_SOURCE_ID, RemoteSourceAddressKind.WAN)

        assertEquals(
            listOf(RemoteSourceAddressKind.WAN, RemoteSourceAddressKind.LAN),
            selector.testOrder().map { it.kind },
        )

        networkProvider.publish(NetworkConnectionType.WIFI)

        assertEquals(
            listOf(RemoteSourceAddressKind.LAN, RemoteSourceAddressKind.WAN),
            selector.testOrder().map { it.kind },
        )
    }

    @Test
    fun `fallback classifier uses exception class names when messages lack network keywords`() {
        assertTrue(isRemoteSourceAddressFallbackAllowed(UnknownHostException("lan.example")))
        assertTrue(isRemoteSourceAddressFallbackAllowed(SocketTimeoutException()))
        assertTrue(isRemoteSourceAddressFallbackAllowed(SSLHandshakeException("certificate path validation failed")))
    }

    @Test
    fun `fallback classifier recognizes common http status formats`() {
        assertTrue(isRemoteSourceAddressFallbackAllowed(IllegalStateException("InvalidResponseCodeException: Response code: 500")))
        assertTrue(isRemoteSourceAddressFallbackAllowed(IllegalStateException("request failed status=408")))
        assertTrue(isRemoteSourceAddressFallbackAllowed(IllegalStateException("remote error code: 503")))
        assertFalse(isRemoteSourceAddressFallbackAllowed(IllegalStateException("InvalidResponseCodeException: responseCode=401")))
    }

    @Test
    fun `fallback propagates cancellation without retrying alternate address`() = runTest {
        val networkProvider = TestNetworkConnectionTypeProvider(NetworkConnectionType.WIFI)
        val selector = RemoteSourceAddressSelector(networkProvider)
        val attempts = mutableListOf<RemoteSourceAddressKind>()

        assertFailsWith<CancellationException> {
            selector.withAddressFallback(
                sourceId = TEST_SOURCE_ID,
                sourceType = ImportSourceType.NAVIDROME,
                lanBaseUrl = LAN_URL,
                wanBaseUrl = WAN_URL,
                normalizeBaseUrl = ::identity,
            ) { candidate ->
                attempts += candidate.kind
                throw CancellationException("timeout")
            }
        }

        assertEquals(listOf(RemoteSourceAddressKind.LAN), attempts)
    }

    @Test
    fun `fallback classifier does not switch for authorization failures`() {
        assertFalse(
            isRemoteSourceAddressFallbackAllowed(
                IllegalStateException("HTTP 403 Forbidden", UnknownHostException("lan.example")),
            ),
        )
    }

    @Test
    fun `fallback classifier does not switch for playback errors without network indicators`() {
        assertFalse(isRemoteSourceAddressFallbackAllowed(IllegalStateException("codec unsupported")))
        assertFalse(isRemoteSourceAddressFallbackAllowed(IllegalStateException("media format not recognized")))
        assertFalse(isRemoteSourceAddressFallbackAllowed(IllegalStateException("decoder failed")))
    }

    @Test
    fun `remote url candidate reader retries retryable failures`() = runTest {
        val attempts = mutableListOf<String>()

        val result = readRemotePlaybackUrlCandidateWithFallback(
            candidates = TEST_REMOTE_CANDIDATES,
            read = { candidate ->
                attempts += candidate.value
                if (candidate.addressKind == RemoteSourceAddressKind.LAN.name) {
                    error("HTTP 500")
                }
                "image"
            },
            isValidPayload = { it == "image" },
        )

        assertEquals(TEST_REMOTE_CANDIDATES[1], result?.first)
        assertEquals("image", result?.second)
        assertEquals(listOf(LAN_URL, WAN_URL), attempts)
    }

    @Test
    fun `remote url candidate reader does not retry authorization failures`() = runTest {
        val attempts = mutableListOf<String>()

        assertFailsWith<IllegalStateException> {
            readRemotePlaybackUrlCandidateWithFallback(
                candidates = TEST_REMOTE_CANDIDATES,
                read = { candidate ->
                    attempts += candidate.value
                    error("HTTP 401")
                },
            )
        }

        assertEquals(listOf(LAN_URL), attempts)
    }

    @Test
    fun `remote url candidate reader does not retry successful invalid payload`() = runTest {
        val attempts = mutableListOf<String>()

        val result = readRemotePlaybackUrlCandidateWithFallback(
            candidates = TEST_REMOTE_CANDIDATES,
            read = { candidate ->
                attempts += candidate.value
                if (candidate.addressKind == RemoteSourceAddressKind.LAN.name) {
                    "html"
                } else {
                    "image"
                }
            },
            isValidPayload = { it == "image" },
        )

        assertEquals(null, result)
        assertEquals(listOf(LAN_URL), attempts)
    }

    @Test
    fun `fallback address expires after ttl even while it keeps succeeding`() {
        var now = 0L
        val networkProvider = TestNetworkConnectionTypeProvider(NetworkConnectionType.WIFI)
        val selector = RemoteSourceAddressSelector(networkProvider, ttlMillis = 1_000L, nowMillis = { now })

        selector.markSuccess(TEST_SOURCE_ID, RemoteSourceAddressKind.WAN)
        now = 600L
        selector.markSuccess(TEST_SOURCE_ID, RemoteSourceAddressKind.WAN)
        assertEquals(RemoteSourceAddressKind.WAN, selector.testOrder().first().kind)

        now = 1_200L
        selector.markSuccess(TEST_SOURCE_ID, RemoteSourceAddressKind.WAN)
        assertEquals(RemoteSourceAddressKind.WAN, selector.testOrder().first().kind)

        now = 1_300L
        assertEquals(RemoteSourceAddressKind.WAN, selector.testOrder().first().kind)
        now = 2_300L
        assertEquals(RemoteSourceAddressKind.LAN, selector.testOrder().first().kind)
    }

    @Test
    fun `fallback attempts cap connect timeout only while an alternate address remains`() = runTest {
        val networkProvider = TestNetworkConnectionTypeProvider(NetworkConnectionType.WIFI)
        val selector = RemoteSourceAddressSelector(networkProvider)
        val connectTimeouts = mutableListOf<Long?>()

        selector.withAddressFallback(
            sourceId = TEST_SOURCE_ID,
            sourceType = ImportSourceType.NAVIDROME,
            lanBaseUrl = LAN_URL,
            wanBaseUrl = WAN_URL,
            normalizeBaseUrl = ::identity,
        ) { candidate ->
            connectTimeouts += currentRemoteSourceConnectTimeoutMillis()
            if (candidate.kind == RemoteSourceAddressKind.LAN) error("请求失败: timeout")
        }

        assertEquals(listOf(REMOTE_SOURCE_FALLBACK_CONNECT_TIMEOUT_MILLIS, null), connectTimeouts)
    }

    @Test
    fun `testing each address reports the failing address even when the other works`() = runTest {
        val attempts = mutableListOf<RemoteSourceAddressKind>()

        val failure = assertFailsWith<RemoteSourceAddressTestException> {
            testEachRemoteSourceAddress(
                sourceType = ImportSourceType.NAVIDROME,
                lanBaseUrl = LAN_URL,
                wanBaseUrl = WAN_URL,
                normalizeBaseUrl = ::identity,
            ) { candidate ->
                synchronized(attempts) { attempts += candidate.kind }
                if (candidate.kind == RemoteSourceAddressKind.WAN) error("请求失败: timeout")
            }
        }

        assertEquals(setOf(RemoteSourceAddressKind.LAN, RemoteSourceAddressKind.WAN), attempts.toSet())
        assertTrue(failure.anySucceeded)
        assertEquals(
            "LAN address: connected\nWAN address: 请求失败: timeout",
            resolveUiText(failure.text, AppLanguage.English),
        )
    }

    @Test
    fun `http error on one address keeps the per address report`() = runTest {
        val failure = assertFailsWith<RemoteSourceAddressTestException> {
            testEachRemoteSourceAddress(
                sourceType = ImportSourceType.NAVIDROME,
                lanBaseUrl = LAN_URL,
                wanBaseUrl = WAN_URL,
                normalizeBaseUrl = ::identity,
            ) { candidate ->
                if (candidate.kind == RemoteSourceAddressKind.WAN) {
                    throw RemoteSourceHttpArgumentException(502, UiText.Raw("HTTP 502"))
                }
            }
        }

        assertTrue(failure.anySucceeded)
        assertEquals(
            "LAN address: connected\nWAN address: HTTP 502",
            resolveUiText(failure.text, AppLanguage.English),
        )
    }

    @Test
    fun `input validation failure is reported once instead of per address`() = runTest {
        val validation = UiTextArgumentException(UiText.Raw("username required"))

        val failure = assertFailsWith<UiTextArgumentException> {
            testEachRemoteSourceAddress(
                sourceType = ImportSourceType.NAVIDROME,
                lanBaseUrl = LAN_URL,
                wanBaseUrl = WAN_URL,
                normalizeBaseUrl = ::identity,
            ) { throw validation }
        }

        assertTrue(failure === validation)
    }

    @Test
    fun `unreachable lan address reads as expected when wan works`() = runTest {
        val failure = assertFailsWith<RemoteSourceAddressTestException> {
            testEachRemoteSourceAddress(
                sourceType = ImportSourceType.NAVIDROME,
                lanBaseUrl = LAN_URL,
                wanBaseUrl = WAN_URL,
                normalizeBaseUrl = ::identity,
            ) { candidate ->
                if (candidate.kind == RemoteSourceAddressKind.LAN) error("请求失败: timeout")
            }
        }

        assertTrue(failure.anySucceeded)
        assertEquals(
            "LAN address: unreachable (normal when you are away from that network): 请求失败: timeout\n" +
                "WAN address: connected",
            resolveUiText(failure.text, AppLanguage.English),
        )
    }

    @Test
    fun `testing each address caps only the lan connect timeout and reports total failure`() = runTest {
        val connectTimeouts = mutableMapOf<RemoteSourceAddressKind, Long?>()

        val failure = assertFailsWith<RemoteSourceAddressTestException> {
            testEachRemoteSourceAddress(
                sourceType = ImportSourceType.NAVIDROME,
                lanBaseUrl = LAN_URL,
                wanBaseUrl = WAN_URL,
                normalizeBaseUrl = ::identity,
            ) { candidate ->
                val connectTimeout = currentRemoteSourceConnectTimeoutMillis()
                synchronized(connectTimeouts) { connectTimeouts[candidate.kind] = connectTimeout }
                error("请求失败: timeout")
            }
        }

        assertFalse(failure.anySucceeded)
        assertEquals(
            mapOf(
                RemoteSourceAddressKind.LAN to REMOTE_SOURCE_FALLBACK_CONNECT_TIMEOUT_MILLIS,
                RemoteSourceAddressKind.WAN to null,
            ),
            connectTimeouts.toMap(),
        )
    }

    @Test
    fun `wan first on mobile keeps the normal connect timeout`() = runTest {
        val networkProvider = TestNetworkConnectionTypeProvider(NetworkConnectionType.MOBILE)
        val selector = RemoteSourceAddressSelector(networkProvider)
        val connectTimeouts = mutableListOf<Pair<RemoteSourceAddressKind, Long?>>()

        selector.withAddressFallback(
            sourceId = TEST_SOURCE_ID,
            sourceType = ImportSourceType.NAVIDROME,
            lanBaseUrl = LAN_URL,
            wanBaseUrl = WAN_URL,
            normalizeBaseUrl = ::identity,
        ) { candidate ->
            connectTimeouts += candidate.kind to currentRemoteSourceConnectTimeoutMillis()
            if (candidate.kind == RemoteSourceAddressKind.WAN) error("请求失败: timeout")
        }

        assertEquals(
            listOf<Pair<RemoteSourceAddressKind, Long?>>(
                RemoteSourceAddressKind.WAN to null,
                RemoteSourceAddressKind.LAN to null,
            ),
            connectTimeouts,
        )
    }

    @Test
    fun `remote url candidate reader does not cap candidates of unknown kind`() = runTest {
        val connectTimeouts = mutableListOf<Long?>()

        readRemotePlaybackUrlCandidateWithFallback(
            candidates = listOf(
                RemotePlaybackUrlCandidate(value = LAN_URL),
                RemotePlaybackUrlCandidate(value = WAN_URL),
            ),
            read = { candidate ->
                connectTimeouts += currentRemoteSourceConnectTimeoutMillis()
                if (candidate.value == LAN_URL) error("请求失败: timeout")
                "image"
            },
        )

        assertEquals(listOf<Long?>(null, null), connectTimeouts)
    }

    @Test
    fun `remote url candidate reader caps lan but not wan`() = runTest {
        val connectTimeouts = mutableListOf<Long?>()

        readRemotePlaybackUrlCandidateWithFallback(
            candidates = TEST_REMOTE_CANDIDATES,
            read = { candidate ->
                connectTimeouts += currentRemoteSourceConnectTimeoutMillis()
                if (candidate.addressKind == RemoteSourceAddressKind.LAN.name) error("请求失败: timeout")
                "image"
            },
        )
        readRemotePlaybackUrlCandidateWithFallback(
            candidates = TEST_REMOTE_CANDIDATES.reversed(),
            read = { candidate ->
                connectTimeouts += currentRemoteSourceConnectTimeoutMillis()
                if (candidate.addressKind == RemoteSourceAddressKind.WAN.name) error("请求失败: timeout")
                "image"
            },
        )

        assertEquals(listOf(REMOTE_SOURCE_FALLBACK_CONNECT_TIMEOUT_MILLIS, null, null, null), connectTimeouts)
    }

    @Test
    fun `partial success message puts the address results on a new line`() = runTest {
        assertEquals(
            "Navidrome is reachable through some addresses:\nWAN address: connected",
            resolveUiText(
                uiText(
                    Res.string.source_connection_partially_succeeded,
                    "Navidrome",
                    uiText(Res.string.source_address_test_wan_succeeded),
                ),
                AppLanguage.English,
            ),
        )
    }

    @Test
    fun `fallback connect cap is lifted once the address answers`() = runTest {
        val networkProvider = TestNetworkConnectionTypeProvider(NetworkConnectionType.WIFI)
        val selector = RemoteSourceAddressSelector(networkProvider)
        val connectTimeouts = mutableListOf<Long?>()

        selector.withAddressFallback<Unit>(
            sourceId = TEST_SOURCE_ID,
            sourceType = ImportSourceType.NAVIDROME,
            lanBaseUrl = LAN_URL,
            wanBaseUrl = WAN_URL,
            normalizeBaseUrl = ::identity,
        ) {
            connectTimeouts += currentRemoteSourceConnectTimeoutMillis()
            markCurrentRemoteSourceReachable()
            connectTimeouts += currentRemoteSourceConnectTimeoutMillis()
        }

        assertEquals(listOf(REMOTE_SOURCE_FALLBACK_CONNECT_TIMEOUT_MILLIS, null), connectTimeouts)
    }

    @Test
    fun `network change moves a lan stream to wan only on connected mobile`() {
        val kinds = listOf(RemoteSourceAddressKind.LAN, RemoteSourceAddressKind.WAN)
        val connectedMobile = NetworkConnectionState(NetworkConnectionType.MOBILE, version = 1L)

        fun indexFor(
            candidateKinds: List<RemoteSourceAddressKind?> = kinds,
            currentIndex: Int = 0,
            networkState: NetworkConnectionState = connectedMobile,
            isPlaybackActive: Boolean = true,
        ) = remoteCandidateIndexForNetworkChange(candidateKinds, currentIndex, networkState, isPlaybackActive)

        assertEquals(1, indexFor())
        assertEquals(null, indexFor(networkState = connectedMobile.copy(isConnected = false)))
        assertEquals(null, indexFor(networkState = connectedMobile.copy(type = NetworkConnectionType.WIFI)))
        assertEquals(null, indexFor(currentIndex = 1))
        assertEquals(null, indexFor(candidateKinds = listOf(RemoteSourceAddressKind.LAN)))
        assertEquals(null, indexFor(candidateKinds = listOf(null, RemoteSourceAddressKind.WAN)))
        assertEquals(null, indexFor(isPlaybackActive = false))
    }

    @Test
    fun `testing a single address rethrows its original failure`() = runTest {
        val original = IllegalStateException("HTTP 401")

        val failure = assertFailsWith<IllegalStateException> {
            testEachRemoteSourceAddress(
                sourceType = ImportSourceType.NAVIDROME,
                lanBaseUrl = "",
                wanBaseUrl = WAN_URL,
                normalizeBaseUrl = ::identity,
            ) { throw original }
        }

        assertTrue(failure === original)
    }

    private fun RemoteSourceAddressSelector.testOrder(): List<RemoteSourceBaseUrl> {
        return orderedBaseUrls(
            sourceId = TEST_SOURCE_ID,
            sourceType = ImportSourceType.NAVIDROME,
            lanBaseUrl = LAN_URL,
            wanBaseUrl = WAN_URL,
            normalizeBaseUrl = ::identity,
        )
    }

    private class TestNetworkConnectionTypeProvider(
        initialType: NetworkConnectionType,
    ) : NetworkConnectionTypeProvider {
        private val mutableState = MutableStateFlow(NetworkConnectionState(initialType))

        override val networkConnectionState: StateFlow<NetworkConnectionState> = mutableState.asStateFlow()

        fun publish(type: NetworkConnectionType) {
            val current = mutableState.value
            mutableState.value = NetworkConnectionState(type = type, version = current.version + 1L)
        }
    }
}

private fun identity(value: String): String = value

private const val TEST_SOURCE_ID = "source-1"
private const val LAN_URL = "http://lan.example"
private const val WAN_URL = "https://wan.example"
private val TEST_REMOTE_CANDIDATES = listOf(
    RemotePlaybackUrlCandidate(
        sourceId = TEST_SOURCE_ID,
        addressKind = RemoteSourceAddressKind.LAN.name,
        value = LAN_URL,
    ),
    RemotePlaybackUrlCandidate(
        sourceId = TEST_SOURCE_ID,
        addressKind = RemoteSourceAddressKind.WAN.name,
        value = WAN_URL,
    ),
)
