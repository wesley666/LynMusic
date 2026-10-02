package top.iwesley.lyn.music.platform

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import top.iwesley.lyn.music.core.model.AppleResolvedMediaLocator
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.NavidromeLocatorResolver
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.NetworkConnectionState
import top.iwesley.lyn.music.core.model.NetworkConnectionType
import top.iwesley.lyn.music.core.model.NetworkConnectionTypeProvider
import top.iwesley.lyn.music.core.model.PlaybackLoadToken
import top.iwesley.lyn.music.core.model.RemotePlaybackUrlCandidate
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.domain.RemoteSourceAddressKind

@OptIn(ExperimentalCoroutinesApi::class)
class ApplePlaybackGatewayTest {
    private val dispatcher = StandardTestDispatcher()
    private val network = TestNetworkProvider()
    private val engine = FakePlaybackEngine()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        NavidromeLocatorRuntime.install(LanWanResolver)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun lanStreamMovesToWanWhenMobileDataConnects() = runTest(dispatcher) {
        loadRemoteTrack(playWhenReady = true)
        engine.position = 42_000L

        network.publish(NetworkConnectionType.MOBILE)
        advanceUntilIdle()

        assertEquals(AppleResolvedMediaLocator.RemoteUrl(WAN_URL), engine.loaded.last())
        assertEquals(42_000L, engine.position)
        assertTrue(engine.playing)
    }

    @Test
    fun pausedStreamStaysPausedAfterSwitchingToWan() = runTest(dispatcher) {
        val gateway = loadRemoteTrack(playWhenReady = true)
        gateway.pause()
        advanceUntilIdle()

        network.publish(NetworkConnectionType.MOBILE)
        advanceUntilIdle()

        assertEquals(AppleResolvedMediaLocator.RemoteUrl(WAN_URL), engine.loaded.last())
        assertFalse(engine.playing)
        assertFalse(gateway.state.value.isPlaying)
    }

    @Test
    fun completedTrackIsNotReloadedByNetworkChange() = runTest(dispatcher) {
        loadRemoteTrack(playWhenReady = true)
        val loadsBefore = engine.loaded.size

        engine.onCompleted?.invoke()
        network.publish(NetworkConnectionType.MOBILE)
        advanceUntilIdle()

        assertEquals(loadsBefore, engine.loaded.size)
    }

    @Test
    fun disconnectedGapDoesNotSwitch() = runTest(dispatcher) {
        loadRemoteTrack(playWhenReady = true)
        val loadsBefore = engine.loaded.size

        network.publish(NetworkConnectionType.MOBILE, isConnected = false)
        advanceUntilIdle()

        assertEquals(loadsBefore, engine.loaded.size)
    }

    @Test
    fun lanFailureFallsBackToWanWithoutPublishingError() = runTest(dispatcher) {
        val gateway = loadRemoteTrack(playWhenReady = true)

        engine.failure = NETWORK_ERROR
        engine.onFailed?.invoke(NETWORK_ERROR)
        engine.failure = null

        assertEquals(AppleResolvedMediaLocator.RemoteUrl(WAN_URL), engine.loaded.last())
        assertNull(gateway.state.value.errorMessage)
        assertTrue(engine.playing)
    }

    @Test
    fun failureOnLastCandidatePublishesError() = runTest(dispatcher) {
        val gateway = loadRemoteTrack(playWhenReady = true)
        engine.onFailed?.invoke(NETWORK_ERROR)
        val loadsAfterFallback = engine.loaded.size

        engine.onFailed?.invoke(NETWORK_ERROR)

        assertEquals(loadsAfterFallback, engine.loaded.size)
        assertNotNull(gateway.state.value.errorMessage)
    }

    @Test
    fun staleLoadDoesNotResetTheTrackCommittedWhileItWaited() = runTest(dispatcher) {
        val gateway = loadRemoteTrack(playWhenReady = true)
        val stopsBefore = engine.stopCount
        val loadsBefore = engine.loaded.size
        var checks = 0
        // Current at entry, superseded by the time its main-thread reset runs.
        val staleToken = PlaybackLoadToken { ++checks <= 1 }

        gateway.load(localTrack(), playWhenReady = true, startPositionMs = 0L, loadToken = staleToken)
        advanceUntilIdle()

        assertEquals(stopsBefore, engine.stopCount)
        assertEquals(loadsBefore, engine.loaded.size)
        assertTrue(engine.playing)
    }

    @Test
    fun cancelledLoadReleasesLocalAccessItNeverHandedOver() = runTest(dispatcher) {
        var releases = 0
        lateinit var loadJob: Job
        val gateway = ApplePlaybackGateway(
            platformLabel = "test",
            networkConnectionTypeProvider = network,
            localMediaAccessResolver = AppleLocalMediaAccessResolver { locator ->
                // Cancelled after the access was granted but before the main-thread commit takes it over.
                loadJob.cancel()
                AppleLocalMediaAccess(AppleResolvedMediaLocator.FileUrl(locator)) { releases++ }
            },
            player = engine,
        )
        advanceUntilIdle()

        loadJob = launch {
            gateway.load(localTrack(), playWhenReady = true, startPositionMs = 0L, loadToken = PlaybackLoadToken())
        }
        advanceUntilIdle()

        assertTrue(loadJob.isCancelled)
        assertEquals(1, releases)
        assertTrue(engine.loaded.isEmpty())
    }

    private fun localTrack() = Track(
        id = "local",
        sourceId = "local",
        title = "Local",
        mediaLocator = "file:///tmp/lynmusic-gateway-test.mp3",
        relativePath = "lynmusic-gateway-test.mp3",
    )

    private suspend fun TestScope.loadRemoteTrack(playWhenReady: Boolean): ApplePlaybackGateway {
        val gateway = ApplePlaybackGateway(
            platformLabel = "test",
            networkConnectionTypeProvider = network,
            player = engine,
        )
        advanceUntilIdle()
        gateway.load(
            track = Track(
                id = "track",
                sourceId = SOURCE_ID,
                title = "Track",
                mediaLocator = buildNavidromeSongLocator(SOURCE_ID, "song"),
                relativePath = "song",
            ),
            playWhenReady = playWhenReady,
            startPositionMs = 0L,
            loadToken = PlaybackLoadToken(),
        )
        advanceUntilIdle()
        assertEquals(AppleResolvedMediaLocator.RemoteUrl(LAN_URL), engine.loaded.last())
        return gateway
    }

    private class TestNetworkProvider : NetworkConnectionTypeProvider {
        private val mutableState = MutableStateFlow(NetworkConnectionState(NetworkConnectionType.WIFI))
        override val networkConnectionState: StateFlow<NetworkConnectionState> = mutableState.asStateFlow()

        fun publish(type: NetworkConnectionType, isConnected: Boolean = true) {
            val current = mutableState.value
            mutableState.value = NetworkConnectionState(type, current.version + 1L, isConnected)
        }
    }

    private class FakePlaybackEngine : ApplePlaybackEngine {
        override var onProgress: (() -> Unit)? = null
        override var onCompleted: (() -> Unit)? = null
        override var onFailed: ((String?) -> Unit)? = null
        val loaded = mutableListOf<AppleResolvedMediaLocator>()
        var playing = false
        var position = 0L
        var failure: String? = null

        override fun load(locator: AppleResolvedMediaLocator) {
            loaded += locator
            position = 0L
        }

        var stopCount = 0

        override fun stopAndClear() {
            stopCount++
            playing = false
        }

        override fun play() {
            playing = true
        }

        override fun pause() {
            playing = false
        }

        override fun seekTo(positionMs: Long) {
            position = positionMs
        }

        override fun canSeek(): Boolean = true
        override fun setVolume(volume: Float) = Unit
        override fun isPlaying(): Boolean = playing
        override fun positionMs(): Long = position
        override fun durationMs(): Long? = null
        override fun volume(): Float = 1f
        override fun errorMessage(): String? = failure
        override fun release() = Unit
    }

    private object LanWanResolver : NavidromeLocatorResolver {
        override suspend fun resolveStreamUrl(locator: String, audioQuality: NavidromeAudioQuality): String = LAN_URL

        override suspend fun resolveStreamUrlCandidates(
            locator: String,
            audioQuality: NavidromeAudioQuality,
        ): List<RemotePlaybackUrlCandidate> = listOf(
            RemotePlaybackUrlCandidate(SOURCE_ID, RemoteSourceAddressKind.LAN.name, LAN_URL),
            RemotePlaybackUrlCandidate(SOURCE_ID, RemoteSourceAddressKind.WAN.name, WAN_URL),
        )

        override suspend fun resolveCoverArtUrl(locator: String): String? = null
    }

    private companion object {
        const val SOURCE_ID = "navidrome-test"
        const val LAN_URL = "http://192.168.1.2:4533/rest/stream?id=song"
        const val WAN_URL = "https://music.example.com/rest/stream?id=song"
        const val NETWORK_ERROR = "The network connection was lost."
    }
}
