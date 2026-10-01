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
import top.iwesley.lyn.music.core.model.RemotePlaybackUrlCandidate
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
