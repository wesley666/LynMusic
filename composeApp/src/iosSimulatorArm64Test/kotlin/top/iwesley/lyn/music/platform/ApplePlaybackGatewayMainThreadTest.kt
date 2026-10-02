package top.iwesley.lyn.music.platform

import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSThread
import top.iwesley.lyn.music.core.model.AppleResolvedMediaLocator
import top.iwesley.lyn.music.core.model.PlaybackLoadToken
import top.iwesley.lyn.music.core.model.Track

/**
 * Uses the real main dispatcher: the playback repository calls the gateway through `runBlocking` on the main
 * thread (startup volume and queue restore, dispose), which must not deadlock on the gateway's main-thread hop.
 * If a hop regresses to a dispatching `Main`, the `isDispatchNeeded` check fails first; the blocking calls below
 * would otherwise hang rather than time out.
 */
class ApplePlaybackGatewayMainThreadTest {
    @Test
    fun entryPointsDoNotDeadlockWhenBlockingOnTheMainThread() {
        assertTrue(NSThread.isMainThread, "this regression test must run on the main thread")
        // The precondition the gateway relies on; fails fast instead of hanging if the hop stops being inline.
        assertFalse(Dispatchers.Main.immediate.isDispatchNeeded(EmptyCoroutineContext))
        val engine = RecordingEngine()
        val gateway = ApplePlaybackGateway(platformLabel = "test", player = engine)

        runBlocking {
            withTimeout(5_000L) {
                gateway.setVolume(0.5f)
                // Startup queue restore loads through runBlocking on the main thread too.
                gateway.load(
                    track = Track(
                        id = "local",
                        sourceId = "local",
                        title = "Local",
                        mediaLocator = "file:///tmp/lynmusic-main-thread-test.mp3",
                        relativePath = "lynmusic-main-thread-test.mp3",
                    ),
                    playWhenReady = false,
                    startPositionMs = 0L,
                    loadToken = PlaybackLoadToken(),
                )
                gateway.play()
                gateway.pause()
                gateway.release()
            }
        }

        assertEquals(
            listOf("setVolume", "stopAndClear", "load", "pause", "play", "pause", "release"),
            engine.calls,
        )
    }

    private class RecordingEngine : ApplePlaybackEngine {
        override var onProgress: (() -> Unit)? = null
        override var onCompleted: (() -> Unit)? = null
        override var onFailed: ((String?) -> Unit)? = null
        val calls = mutableListOf<String>()

        override fun load(locator: AppleResolvedMediaLocator) {
            calls += "load"
        }

        override fun stopAndClear() {
            calls += "stopAndClear"
        }

        override fun play() {
            calls += "play"
        }

        override fun pause() {
            calls += "pause"
        }

        override fun seekTo(positionMs: Long) {
            calls += "seekTo"
        }

        override fun canSeek(): Boolean = false

        override fun setVolume(volume: Float) {
            calls += "setVolume"
        }

        override fun isPlaying(): Boolean = false
        override fun positionMs(): Long = 0L
        override fun durationMs(): Long? = null
        override fun volume(): Float = 1f
        override fun errorMessage(): String? = null

        override fun release() {
            calls += "release"
        }
    }
}
