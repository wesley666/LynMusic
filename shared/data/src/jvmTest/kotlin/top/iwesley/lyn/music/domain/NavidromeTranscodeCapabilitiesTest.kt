package top.iwesley.lyn.music.domain

import io.ktor.http.Url
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NavidromeTranscodeCapabilitiesTest {
    private val source = NavidromeResolvedSource("https://lan.test/music", "https://wan.test/music", "source",
        username = "user", password = "password")

    @Test fun requiresExplicitVersionOneAndSuccessfulEnvelope() = runTest {
        val cases = listOf(
            """{"status":"ok","openSubsonicExtensions":[{"name":"transcodeOffset","versions":[1,2]}]}""" to NavidromeTranscodeOffsetSupport.Supported,
            """{"status":"ok","openSubsonicExtensions":[{"name":"transcodeOffset","versions":[2]}]}""" to NavidromeTranscodeOffsetSupport.Unsupported,
            """{"status":"ok","openSubsonicExtensions":[]}""" to NavidromeTranscodeOffsetSupport.Unsupported,
            """{"status":"ok"}""" to NavidromeTranscodeOffsetSupport.Unknown,
            """{"status":"ok","openSubsonicExtensions":[{"name":"transcodeOffset","versions":null}]}""" to NavidromeTranscodeOffsetSupport.Unknown,
            """{"status":"failed","error":{"message":"offline"}}""" to NavidromeTranscodeOffsetSupport.Unknown,
        )
        for ((payload, expected) in cases) {
            val client = Client(payload)
            assertEquals(expected, NavidromeTranscodeCapabilities(backgroundScope, client).support(source))
            val request = client.requests.single()
            assertTrue(request.url.contains("getOpenSubsonicExtensions"))
            assertEquals("user", Url(request.url).parameters["u"])
        }
    }

    @Test fun concurrentLoadsShareProbeAndConfigurationChangesInvalidateIt() = runTest {
        val client = Client()
        val gate = CompletableDeferred<Unit>()
        client.gate = gate
        val cache = NavidromeTranscodeCapabilities(backgroundScope, client)
        val first = async { cache.support(source) }
        val second = async { cache.support(source.copy()) }
        runCurrent()
        assertEquals(1, client.requests.size)
        gate.complete(Unit)
        assertEquals(NavidromeTranscodeOffsetSupport.Supported, first.await())
        assertEquals(first.await(), second.await())
        assertEquals(first.await(), cache.support(source))
        assertEquals(1, client.requests.size)
        cache.support(source.copy(password = "changed"))
        cache.support(source.copy(baseUrl = "https://new.test/music"))
        cache.support(source.copy(authMode = SubsonicAuthMode.API_KEY))
        assertEquals(4, client.requests.size)
    }

    @Test fun unknownIsRetriedButUnsupportedIsCached() = runTest {
        val client = Client("""{"status":"ok"}""")
        val cache = NavidromeTranscodeCapabilities(backgroundScope, client)
        repeat(2) { assertEquals(NavidromeTranscodeOffsetSupport.Unknown, cache.support(source)) }
        client.payload = """{"status":"ok","openSubsonicExtensions":[]}"""
        repeat(2) { assertEquals(NavidromeTranscodeOffsetSupport.Unsupported, cache.support(source)) }
        assertEquals(3, client.requests.size)
    }

    @Test fun cancelledConsumerDoesNotCancelOtherConsumers() = runTest {
        val client = Client().also { it.gate = CompletableDeferred() }
        val cache = NavidromeTranscodeCapabilities(backgroundScope, client)
        val oldTrack = async { cache.support(source) }
        runCurrent()
        oldTrack.cancel()
        val nextTrack = async { cache.support(source) }
        client.gate!!.complete(Unit)
        assertEquals(NavidromeTranscodeOffsetSupport.Supported, nextTrack.await())
        assertEquals(1, client.requests.size)
    }

    @Test fun doesNotProbeSubsonicSources() = runTest {
        val client = Client()
        assertEquals(NavidromeTranscodeOffsetSupport.Unsupported,
            NavidromeTranscodeCapabilities(backgroundScope, client).support(source.copy(sourceType = ImportSourceType.SUBSONIC)))
        assertTrue(client.requests.isEmpty())
    }

    @Test fun probeTimeoutIsUnknownAndCanBeRetried() = runTest {
        val client = Client().also { it.gate = CompletableDeferred() }
        val cache = NavidromeTranscodeCapabilities(backgroundScope, client)
        assertEquals(NavidromeTranscodeOffsetSupport.Unknown, cache.support(source))
        client.gate!!.complete(Unit)
        assertEquals(NavidromeTranscodeOffsetSupport.Supported, cache.support(source))
        assertEquals(2, client.requests.size)
    }

    @Test fun changingSourceRetiresAnInFlightProbe() = runTest {
        val client = Client().also { it.gate = CompletableDeferred() }
        val cache = NavidromeTranscodeCapabilities(backgroundScope, client)
        val old = async { cache.support(source) }
        runCurrent()
        val current = async { cache.support(source.copy(password = "new credential")) }
        runCurrent()
        assertTrue(old.isCancelled)
        client.gate!!.complete(Unit)
        assertEquals(NavidromeTranscodeOffsetSupport.Supported, current.await())
        assertEquals(2, client.requests.size)
    }

    @Test fun offsetKeepsAuthenticationQualityAndSubpathAndNeverAccumulates() {
        for (auth in SubsonicAuthMode.entries) {
            val original = buildSubsonicStreamUrl(source.baseUrl, "user", "key&+ /", auth, "song/1", NavidromeAudioQuality.Kbps192)
            val offset = navidromeStreamUrlAtOffset(original, 45L)
            val before = Url(original)
            val after = Url(offset)
            assertEquals(before.encodedPath, after.encodedPath)
            for ((name, values) in before.parameters.entries()) assertEquals(values, after.parameters.getAll(name))
            assertEquals(listOf("45"), after.parameters.getAll("timeOffset"))
            assertEquals(listOf("10"), Url(navidromeStreamUrlAtOffset(offset, 10)).parameters.getAll("timeOffset"))
            assertNull(Url(navidromeStreamUrlAtOffset(offset, 0)).parameters["timeOffset"])
        }
        assertEquals("30", Url(buildNavidromeStreamUrl(source.baseUrl, "u", "p", "id", NavidromeAudioQuality.Kbps128, 30)).parameters["timeOffset"])
        assertNull(Url(buildNavidromeStreamUrl(source.baseUrl, "u", "p", "id", timeOffsetSeconds = 30)).parameters["timeOffset"])
    }

    private class Client(var payload: String = """{"status":"ok","openSubsonicExtensions":[{"name":"transcodeOffset","versions":[1]}]}""") : LyricsHttpClient {
        val requests = mutableListOf<LyricsRequest>()
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
            requests += request
            gate?.await()
            return Result.success(LyricsHttpResponse(200, """{"subsonic-response":$payload}"""))
        }
    }
}
