package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import androidx.room.Room
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.DEFAULT_PLAYBACK_VOLUME
import top.iwesley.lyn.music.core.model.DesktopVlcPreferencesStore
import top.iwesley.lyn.music.core.model.PlaybackLoadToken
import top.iwesley.lyn.music.core.model.PlaybackPreferencesStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.normalizePlaybackVolume
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import top.iwesley.lyn.music.platform.JvmPlaybackGateway
import top.iwesley.lyn.music.platform.JvmVlcPlaybackMedia
import top.iwesley.lyn.music.platform.JvmVlcPlaybackRuntime
import top.iwesley.lyn.music.platform.JvmVlcRuntimeInitializationResult
import uk.co.caprica.vlcj.log.LogEventListener
import uk.co.caprica.vlcj.media.MediaEventAdapter
import uk.co.caprica.vlcj.media.callback.CallbackMedia
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

@OptIn(ExperimentalCoroutinesApi::class)
class JvmPlaybackGatewayVlcInitializationTest {
    @Test fun rejectedMediaLoadKeepsLocalizedErrorForPlayingAndPausedLoads() = runTest {
        for (playWhenReady in listOf(true, false)) {
            val database = createVlcGatewayTestDatabase()
            val runtime = FakeVlcPlaybackRuntime(startSucceeds = false)
            val gateway = createGateway(database, runtimeDispatcher = UnconfinedTestDispatcher(testScheduler), runtimeInitializer = {
                JvmVlcRuntimeInitializationResult(runtime, "/auto/vlc/lib", null, "/auto/vlc/lib")
            })
            try {
                runCurrent()
                val track = sampleVlcGatewayTrack("track-1")
                gateway.load(track, playWhenReady, startPositionMs = 0L, loadToken = PlaybackLoadToken())
                waitForStartCalls(runtime)
                val deadline = System.nanoTime() + 2_000_000_000L
                while (gateway.state.value.errorText == null && System.nanoTime() < deadline) {
                    kotlinx.coroutines.yield()
                    Thread.sleep(10L)
                }
                val text = checkNotNull(gateway.state.value.errorText)
                assertEquals("Failed to access the song: Unable to load media: file:///music/track-1.mp3", resolveUiText(text, AppLanguage.English))
                assertEquals("访问歌曲失败：无法加载媒体：file:///music/track-1.mp3", resolveUiText(text, AppLanguage.SimplifiedChinese))
                assertEquals("存取歌曲失敗：無法載入媒體：file:///music/track-1.mp3", resolveUiText(text, AppLanguage.TraditionalChinese))
                assertEquals(listOf(RuntimeStartCall(track.mediaLocator, playWhenReady)), runtime.startCalls)
                assertTrue(runtime.seekCalls.isEmpty())
            } finally { gateway.release(); database.close() }
        }
    }

    @Test
    fun `gateway creation does not wait for vlc initializer`() = runTest {
        val database = createVlcGatewayTestDatabase()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val initializerGate = CompletableDeferred<JvmVlcRuntimeInitializationResult>()
        var initializerCalls = 0
        val gateway = createGateway(
            database = database,
            runtimeDispatcher = dispatcher,
            runtimeInitializer = {
                initializerCalls += 1
                initializerGate.await()
            },
        )

        try {
            assertEquals(0, initializerCalls)

            runCurrent()

            assertEquals(1, initializerCalls)
            assertFalse(initializerGate.isCompleted)
        } finally {
            gateway.release()
            database.close()
        }
    }

    @Test
    fun `active load during vlc initialization shows initializing message and replays when ready`() = runTest {
        val database = createVlcGatewayTestDatabase()
        val desktopPrefs = FakeDesktopVlcPreferencesStore()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val runtime = FakeVlcPlaybackRuntime(nativeLibraryPath = "/auto/vlc/lib")
        val initializerGate = CompletableDeferred<JvmVlcRuntimeInitializationResult>()
        val gateway = createGateway(
            database = database,
            desktopVlcPreferencesStore = desktopPrefs,
            runtimeDispatcher = dispatcher,
            runtimeInitializer = { initializerGate.await() },
        )

        try {
            gateway.load(
                track = sampleVlcGatewayTrack("track-1"),
                playWhenReady = true,
                startPositionMs = 2_500L,
                loadToken = PlaybackLoadToken(),
            )

            assertEquals("playback_vlc_initializing", gateway.state.value.errorMessage)
            assertEquals("Initializing VLC. VLC may need to be installed.", resolveUiText(checkNotNull(gateway.state.value.errorText), AppLanguage.English))
            assertEquals("正在初始化 VLC，使用者也可能尚未安裝 VLC 播放器…", resolveUiText(checkNotNull(gateway.state.value.errorText), AppLanguage.TraditionalChinese))
            assertEquals(2_500L, gateway.state.value.positionMs)
            assertTrue(runtime.startCalls.isEmpty())

            initializerGate.complete(
                JvmVlcRuntimeInitializationResult(
                    runtime = runtime,
                    autoDetectedPath = "/auto/vlc/lib",
                    manualPath = null,
                    effectivePath = "/auto/vlc/lib",
                ),
            )
            advanceUntilIdle()
            waitForStartCalls(runtime)

            assertEquals(listOf(RuntimeStartCall("file:///music/track-1.mp3", playWhenReady = true)), runtime.startCalls)
            assertTrue(runtime.seekCalls.isEmpty())
            runtime.emitPlaying()
            advanceUntilIdle()
            assertTrue(runtime.seekCalls.isEmpty())
            runtime.emitSeekableChanged()
            advanceUntilIdle()
            assertEquals(listOf(2_500L), runtime.seekCalls)
            assertNull(gateway.state.value.errorMessage)
            assertNull(gateway.state.value.errorText)
            assertEquals("/auto/vlc/lib", desktopPrefs.desktopVlcAutoDetectedPath.value)
        } finally {
            gateway.release()
            database.close()
        }
    }

    @Test
    fun `inactive load during vlc initialization waits without showing initializing message`() = runTest {
        val database = createVlcGatewayTestDatabase()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val runtime = FakeVlcPlaybackRuntime()
        val initializerGate = CompletableDeferred<JvmVlcRuntimeInitializationResult>()
        val gateway = createGateway(
            database = database,
            runtimeDispatcher = dispatcher,
            runtimeInitializer = { initializerGate.await() },
        )

        try {
            gateway.load(
                track = sampleVlcGatewayTrack("track-1"),
                playWhenReady = false,
                startPositionMs = 0L,
                loadToken = PlaybackLoadToken(),
            )

            assertNull(gateway.state.value.errorMessage)
            assertTrue(runtime.startCalls.isEmpty())

            initializerGate.complete(
                JvmVlcRuntimeInitializationResult(
                    runtime = runtime,
                    autoDetectedPath = "/auto/vlc/lib",
                    manualPath = null,
                    effectivePath = "/auto/vlc/lib",
                ),
            )
            advanceUntilIdle()
            waitForStartCalls(runtime)

            assertEquals(listOf(RuntimeStartCall("file:///music/track-1.mp3", playWhenReady = false)), runtime.startCalls)
            assertNull(gateway.state.value.errorMessage)
        } finally {
            gateway.release()
            database.close()
        }
    }

    @Test
    fun `vlc initialization success replays only latest current pending load`() = runTest {
        val database = createVlcGatewayTestDatabase()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val runtime = FakeVlcPlaybackRuntime()
        val initializerGate = CompletableDeferred<JvmVlcRuntimeInitializationResult>()
        var currentRequestId = 1L
        val gateway = createGateway(
            database = database,
            runtimeDispatcher = dispatcher,
            runtimeInitializer = { initializerGate.await() },
        )

        try {
            gateway.load(
                track = sampleVlcGatewayTrack("track-1"),
                playWhenReady = true,
                loadToken = PlaybackLoadToken(1L) { currentRequestId == 1L },
            )
            currentRequestId = 2L
            gateway.load(
                track = sampleVlcGatewayTrack("track-2"),
                playWhenReady = true,
                loadToken = PlaybackLoadToken(2L) { currentRequestId == 2L },
            )

            initializerGate.complete(
                JvmVlcRuntimeInitializationResult(
                    runtime = runtime,
                    autoDetectedPath = "/auto/vlc/lib",
                    manualPath = null,
                    effectivePath = "/auto/vlc/lib",
                ),
            )
            advanceUntilIdle()
            waitForStartCalls(runtime)

            assertEquals(listOf(RuntimeStartCall("file:///music/track-2.mp3", playWhenReady = true)), runtime.startCalls)
        } finally {
            gateway.release()
            database.close()
        }
    }

    @Test
    fun `vlc initialization failure reports unavailable only for active pending load`() = runTest {
        val activeDatabase = createVlcGatewayTestDatabase()
        val inactiveDatabase = createVlcGatewayTestDatabase()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val activeGate = CompletableDeferred<JvmVlcRuntimeInitializationResult>()
        val inactiveGate = CompletableDeferred<JvmVlcRuntimeInitializationResult>()
        val activeGateway = createGateway(
            database = activeDatabase,
            runtimeDispatcher = dispatcher,
            runtimeInitializer = { activeGate.await() },
        )
        val inactiveGateway = createGateway(
            database = inactiveDatabase,
            runtimeDispatcher = dispatcher,
            runtimeInitializer = { inactiveGate.await() },
        )

        try {
            runCurrent()

            activeGateway.load(sampleVlcGatewayTrack("track-1"), playWhenReady = true)
            inactiveGateway.load(sampleVlcGatewayTrack("track-1"), playWhenReady = false)

            activeGate.complete(unavailableVlcResult())
            inactiveGate.complete(unavailableVlcResult())
            advanceUntilIdle()

            assertEquals("playback_vlc_unavailable", activeGateway.state.value.errorMessage)
            assertEquals("VLC was not detected. Install VLC or choose its path in Settings.",
                resolveUiText(checkNotNull(activeGateway.state.value.errorText), AppLanguage.English))
            assertEquals("未偵測到 VLC，請安裝或在設定中手動選擇 VLC 路徑。",
                resolveUiText(checkNotNull(activeGateway.state.value.errorText), AppLanguage.TraditionalChinese))
            assertNull(inactiveGateway.state.value.errorMessage)
            assertNull(inactiveGateway.state.value.errorText)
        } finally {
            activeGateway.release()
            inactiveGateway.release()
            activeDatabase.close()
            inactiveDatabase.close()
        }
    }

    @Test
    fun `stale ready load does not touch vlc runtime`() = runTest {
        val database = createVlcGatewayTestDatabase()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val runtime = FakeVlcPlaybackRuntime()
        val currentRequestId = AtomicLong(2L)
        val gateway = createGateway(
            database = database,
            runtimeDispatcher = dispatcher,
            runtimeInitializer = {
                JvmVlcRuntimeInitializationResult(
                    runtime = runtime,
                    autoDetectedPath = "/auto/vlc/lib",
                    manualPath = null,
                    effectivePath = "/auto/vlc/lib",
                )
            },
        )

        try {
            advanceUntilIdle()

            gateway.load(
                track = sampleVlcGatewayTrack("track-1"),
                playWhenReady = true,
                loadToken = PlaybackLoadToken(1L) { currentRequestId.get() == 1L },
            )

            assertEquals(0, runtime.stopCallCount)
            assertTrue(runtime.startCalls.isEmpty())
        } finally {
            gateway.release()
            database.close()
        }
    }

    @Test
    fun `pending initial seek is ignored after load token becomes stale`() = runTest {
        val database = createVlcGatewayTestDatabase()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val runtime = FakeVlcPlaybackRuntime()
        val currentRequestId = AtomicLong(1L)
        val gateway = createGateway(
            database = database,
            runtimeDispatcher = dispatcher,
            runtimeInitializer = {
                JvmVlcRuntimeInitializationResult(
                    runtime = runtime,
                    autoDetectedPath = "/auto/vlc/lib",
                    manualPath = null,
                    effectivePath = "/auto/vlc/lib",
                )
            },
        )

        try {
            advanceUntilIdle()

            gateway.load(
                track = sampleVlcGatewayTrack("track-1"),
                playWhenReady = true,
                startPositionMs = 2_500L,
                loadToken = PlaybackLoadToken(1L) { currentRequestId.get() == 1L },
            )

            assertEquals(listOf(RuntimeStartCall("file:///music/track-1.mp3", playWhenReady = true)), runtime.startCalls)
            assertTrue(runtime.seekCalls.isEmpty())

            currentRequestId.set(2L)
            runtime.emitSeekableChanged()
            advanceUntilIdle()

            assertTrue(runtime.seekCalls.isEmpty())
        } finally {
            gateway.release()
            database.close()
        }
    }

    @Test
    fun `load that becomes stale while stopping does not start stale media`() = runTest {
        val database = createVlcGatewayTestDatabase()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val runtime = FakeVlcPlaybackRuntime().apply {
            blockStopCallNumber = 1
        }
        val currentRequestId = AtomicLong(1L)
        val gateway = createGateway(
            database = database,
            runtimeDispatcher = dispatcher,
            runtimeInitializer = {
                JvmVlcRuntimeInitializationResult(
                    runtime = runtime,
                    autoDetectedPath = "/auto/vlc/lib",
                    manualPath = null,
                    effectivePath = "/auto/vlc/lib",
                )
            },
        )

        try {
            advanceUntilIdle()

            val firstLoad = async(Dispatchers.Default) {
                gateway.load(
                    track = sampleVlcGatewayTrack("track-1"),
                    playWhenReady = true,
                    loadToken = PlaybackLoadToken(1L) { currentRequestId.get() == 1L },
                )
            }
            assertTrue(runtime.blockedStopEntered.await(2, TimeUnit.SECONDS))

            currentRequestId.set(2L)
            val secondLoad = async(Dispatchers.Default) {
                gateway.load(
                    track = sampleVlcGatewayTrack("track-2"),
                    playWhenReady = true,
                    loadToken = PlaybackLoadToken(2L) { currentRequestId.get() == 2L },
                )
            }
            Thread.sleep(50L)
            currentRequestId.set(3L)
            runtime.releaseBlockedStop.countDown()

            awaitAll(firstLoad, secondLoad)

            assertEquals(1, runtime.stopCallCount)
            assertTrue(runtime.startCalls.isEmpty())
        } finally {
            runtime.releaseBlockedStop.countDown()
            gateway.release()
            database.close()
        }
    }

    @Test
    fun `concurrent ready loads serialize vlc runtime calls`() = runTest {
        val database = createVlcGatewayTestDatabase()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val runtime = FakeVlcPlaybackRuntime(nativeCallDelayMs = 25L)
        val gateway = createGateway(
            database = database,
            runtimeDispatcher = dispatcher,
            runtimeInitializer = {
                JvmVlcRuntimeInitializationResult(
                    runtime = runtime,
                    autoDetectedPath = "/auto/vlc/lib",
                    manualPath = null,
                    effectivePath = "/auto/vlc/lib",
                )
            },
        )

        try {
            advanceUntilIdle()

            awaitAll(
                async(Dispatchers.Default) {
                    gateway.load(sampleVlcGatewayTrack("track-1"), playWhenReady = true)
                },
                async(Dispatchers.Default) {
                    gateway.load(sampleVlcGatewayTrack("track-2"), playWhenReady = true)
                },
            )

            assertFalse(runtime.concurrentNativeCallDetected)
            assertEquals(2, runtime.stopCallCount)
            assertEquals(2, runtime.startCalls.size)
        } finally {
            gateway.release()
            database.close()
        }
    }

    @Test
    fun `release before late vlc initialization result releases runtime without binding listeners`() = runTest {
        val database = createVlcGatewayTestDatabase()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runtime = FakeVlcPlaybackRuntime()
        val initializerStarted = CompletableDeferred<Unit>()
        val gateway = createGateway(
            database = database,
            runtimeDispatcher = dispatcher,
            runtimeInitializer = {
                initializerStarted.complete(Unit)
                try {
                    CompletableDeferred<Unit>().await()
                    unavailableVlcResult()
                } catch (_: CancellationException) {
                    JvmVlcRuntimeInitializationResult(
                        runtime = runtime,
                        autoDetectedPath = "/auto/vlc/lib",
                        manualPath = null,
                        effectivePath = "/auto/vlc/lib",
                    )
                }
            },
        )

        runCurrent()
        initializerStarted.await()

        gateway.release()
        advanceUntilIdle()
        database.close()

        assertTrue(runtime.released)
        assertEquals(0, runtime.logListenerCount)
        assertEquals(0, runtime.mediaEventListenerCount)
        assertEquals(0, runtime.mediaPlayerEventListenerCount)
        assertTrue(runtime.startCalls.isEmpty())
    }
}

private fun createGateway(
    database: LynMusicDatabase,
    playbackPreferencesStore: PlaybackPreferencesStore = FakePlaybackPreferencesStore(),
    desktopVlcPreferencesStore: DesktopVlcPreferencesStore = FakeDesktopVlcPreferencesStore(),
    runtimeDispatcher: CoroutineDispatcher,
    runtimeInitializer: suspend () -> JvmVlcRuntimeInitializationResult,
): JvmPlaybackGateway {
    return JvmPlaybackGateway(
        database = database,
        secureCredentialStore = EmptySecureCredentialStore,
        playbackPreferencesStore = playbackPreferencesStore,
        desktopVlcPreferencesStore = desktopVlcPreferencesStore,
        logger = top.iwesley.lyn.music.core.model.NoopDiagnosticLogger,
        runtimeInitializer = runtimeInitializer,
        runtimeDispatcher = runtimeDispatcher,
    )
}

private fun createVlcGatewayTestDatabase(): LynMusicDatabase {
    val path = Files.createTempFile("lynmusic-vlc-gateway", ".db")
    return buildLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
    )
}

private fun sampleVlcGatewayTrack(id: String): Track {
    return Track(
        id = id,
        sourceId = "local-1",
        title = "Song $id",
        mediaLocator = "file:///music/$id.mp3",
        relativePath = "$id.mp3",
    )
}

private fun unavailableVlcResult(): JvmVlcRuntimeInitializationResult {
    return JvmVlcRuntimeInitializationResult(
        runtime = null,
        autoDetectedPath = null,
        manualPath = null,
        effectivePath = null,
    )
}

private suspend fun waitForStartCalls(runtime: FakeVlcPlaybackRuntime, count: Int = 1) {
    val deadlineNanos = System.nanoTime() + 2_000_000_000L
    while (runtime.startCalls.size < count && System.nanoTime() < deadlineNanos) {
        kotlinx.coroutines.yield()
        Thread.sleep(10L)
    }
}

private data class RuntimeStartCall(
    val source: String,
    val playWhenReady: Boolean,
)

private class FakeVlcPlaybackRuntime(
    override val nativeLibraryPath: String? = "/auto/vlc/lib",
    private val nativeCallDelayMs: Long = 0L,
    private val startSucceeds: Boolean = true,
) : JvmVlcPlaybackRuntime {
    val startCalls = mutableListOf<RuntimeStartCall>()
    val seekCalls = mutableListOf<Long>()
    val blockedStopEntered = CountDownLatch(1)
    val releaseBlockedStop = CountDownLatch(1)
    var blockStopCallNumber: Int? = null
    var stopCallCount = 0
    var released = false
    var logListenerCount = 0
    var mediaEventListenerCount = 0
    var mediaPlayerEventListenerCount = 0
    @Volatile
    var concurrentNativeCallDetected = false
    private var seekable = true
    private val activeNativeCallCount = AtomicInteger(0)
    private val mediaPlayerEventListeners = mutableListOf<MediaPlayerEventAdapter>()

    override fun addLogListener(listener: LogEventListener) {
        logListenerCount += 1
    }

    override fun removeLogListener(listener: LogEventListener) {
        logListenerCount = (logListenerCount - 1).coerceAtLeast(0)
    }

    override fun addMediaEventListener(listener: MediaEventAdapter) {
        mediaEventListenerCount += 1
    }

    override fun addMediaPlayerEventListener(listener: MediaPlayerEventAdapter) {
        mediaPlayerEventListenerCount += 1
        mediaPlayerEventListeners += listener
    }

    override fun stop() = recordNativeCall {
        stopCallCount += 1
        if (stopCallCount == blockStopCallNumber) {
            blockedStopEntered.countDown()
            releaseBlockedStop.await(2, TimeUnit.SECONDS)
        }
    }

    override fun start(media: JvmVlcPlaybackMedia): Boolean = recordNativeCall {
        startCalls += RuntimeStartCall(media.sourceDescription(), playWhenReady = true)
        startSucceeds
    }

    override fun startPaused(media: JvmVlcPlaybackMedia): Boolean = recordNativeCall {
        startCalls += RuntimeStartCall(media.sourceDescription(), playWhenReady = false)
        startSucceeds
    }

    override fun play() = recordNativeCall { Unit }

    override fun pause() = recordNativeCall { Unit }

    override fun canSeek(): Boolean = recordNativeCall { seekable }

    override fun setTime(positionMs: Long) = recordNativeCall {
        seekCalls += positionMs
    }

    override fun setVolume(volumePercent: Int) = recordNativeCall { Unit }

    override fun release() {
        recordNativeCall {
            released = true
        }
    }

    fun emitPlaying() {
        mediaPlayerEventListeners.toList().forEach { listener ->
            listener.playing(null)
        }
    }

    fun emitSeekableChanged(newSeekable: Int = 1) {
        mediaPlayerEventListeners.toList().forEach { listener ->
            listener.seekableChanged(null, newSeekable)
        }
    }

    private fun <T> recordNativeCall(block: () -> T): T {
        if (activeNativeCallCount.incrementAndGet() > 1) {
            concurrentNativeCallDetected = true
        }
        return try {
            if (nativeCallDelayMs > 0L) {
                Thread.sleep(nativeCallDelayMs)
            }
            block()
        } finally {
            activeNativeCallCount.decrementAndGet()
        }
    }
}

private fun JvmVlcPlaybackMedia.sourceDescription(): String {
    return when (this) {
        is JvmVlcPlaybackMedia.Source -> value
        is JvmVlcPlaybackMedia.Callback -> "callback:${value::class.simpleName}"
    }
}

private class FakePlaybackPreferencesStore : PlaybackPreferencesStore {
    private val mutableUseSambaCache = MutableStateFlow(false)
    private val mutablePlaybackVolume = MutableStateFlow(DEFAULT_PLAYBACK_VOLUME)
    private val mutableAutoPlayOnStartup = MutableStateFlow(false)

    override val useSambaCache: StateFlow<Boolean> = mutableUseSambaCache.asStateFlow()
    override val playbackVolume: StateFlow<Float> = mutablePlaybackVolume.asStateFlow()
    override val autoPlayOnStartup: StateFlow<Boolean> = mutableAutoPlayOnStartup.asStateFlow()

    override suspend fun setUseSambaCache(enabled: Boolean) {
        mutableUseSambaCache.value = enabled
    }

    override suspend fun setPlaybackVolume(volume: Float) {
        mutablePlaybackVolume.value = normalizePlaybackVolume(volume)
    }

    override suspend fun setAutoPlayOnStartup(enabled: Boolean) {
        mutableAutoPlayOnStartup.value = enabled
    }
}

private class FakeDesktopVlcPreferencesStore(
    manualPath: String? = null,
    autoDetectedPath: String? = null,
) : DesktopVlcPreferencesStore {
    private val mutableManualPath = MutableStateFlow(manualPath)
    private val mutableAutoDetectedPath = MutableStateFlow(autoDetectedPath)
    private val mutableEffectivePath = MutableStateFlow(manualPath ?: autoDetectedPath)

    override val desktopVlcManualPath: StateFlow<String?> = mutableManualPath.asStateFlow()
    override val desktopVlcAutoDetectedPath: StateFlow<String?> = mutableAutoDetectedPath.asStateFlow()
    override val desktopVlcEffectivePath: StateFlow<String?> = mutableEffectivePath.asStateFlow()

    override suspend fun setDesktopVlcManualPath(path: String?) {
        mutableManualPath.value = path
        mutableEffectivePath.value = path ?: mutableAutoDetectedPath.value
    }

    override suspend fun setDesktopVlcAutoDetectedPath(path: String?) {
        mutableAutoDetectedPath.value = path
        mutableEffectivePath.value = mutableManualPath.value ?: path
    }
}
