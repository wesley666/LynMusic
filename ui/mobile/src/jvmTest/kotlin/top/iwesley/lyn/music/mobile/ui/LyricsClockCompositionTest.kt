@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlin.time.ExperimentalTime::class)

package top.iwesley.lyn.music.mobile.ui

import top.iwesley.lyn.music.*

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.time.TestTimeSource
import kotlin.time.Duration.Companion.milliseconds
import top.iwesley.lyn.music.cast.CastPlaybackState
import top.iwesley.lyn.music.cast.CastSessionState
import top.iwesley.lyn.music.cast.CastSessionStatus
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.domain.EnhancedLyricsDisplayLine
import top.iwesley.lyn.music.domain.EnhancedLyricsSegment
import top.iwesley.lyn.music.feature.player.PlayerState

class LyricsClockCompositionTest {
    @Test
    fun `frame loop advances without playback reports and recomposition does not reset it`() = onUiThread {
        Fixture().use { ui ->
            val clock = ui.clock
            ui.advance(120)
            assertEquals(1_120L, clock.positionMs)
            ui.state = ui.state.copy(isLyricsFontsLoading = true)
            ui.color = Color.Blue
            ui.advance(180)
            assertSame(clock, ui.clock)
            assertEquals(1_300L, clock.positionMs)
            ui.advance(400)
            assertEquals(1_500L, clock.positionMs)
            assertFalse(ui.hasPendingFrames())
            ui.advance(500)
            assertEquals(1_500L, clock.positionMs)
            ui.state = ui.state.copy(snapshot = ui.state.snapshot.copy(positionMs = 2_000L))
            ui.render()
            ui.advance(100)
            assertEquals(2_100L, clock.positionMs)
        }
    }

    @Test
    fun `pause background browsing and invisible lines stop the frame loop`() = onUiThread {
        Fixture().use { ui ->
            ui.advance(100)
            ui.visible = false
            ui.render()
            ui.advance(100)
            assertEquals(1_100L, ui.clock.positionMs)
            assertFalse(ui.hasPendingFrames())
            ui.visible = true
            ui.render()
            assertEquals(1_200L, ui.clock.positionMs)
            ui.browsing = true
            ui.render()
            ui.advance(100)
            assertEquals(1_200L, ui.clock.positionMs)
            ui.browsing = false
            ui.render()
            assertEquals(1_300L, ui.clock.positionMs)
            ui.owner.registry.currentState = Lifecycle.State.CREATED
            ui.render()
            ui.advance(100)
            assertEquals(1_300L, ui.clock.positionMs)
            ui.owner.registry.currentState = Lifecycle.State.RESUMED
            ui.render()
            assertEquals(1_400L, ui.clock.positionMs)
            ui.state = ui.state.copy(snapshot = ui.state.snapshot.copy(isPlaying = false, positionMs = 1_350L))
            ui.render()
            ui.advance(1_000)
            assertEquals(1_350L, ui.clock.positionMs)
            assertFalse(ui.hasPendingFrames())
        }
    }

    @Test
    fun `finished segments do not schedule interpolation frames`() = onUiThread {
        Fixture().use { ui ->
            ui.line = ui.line.copy(segments = listOf(EnhancedLyricsSegment("word", 0L, 900L)))
            ui.render()
            ui.advance(200)
            assertEquals(1_000L, ui.clock.positionMs)
            assertFalse(ui.hasPendingFrames())
        }
    }

    @Test
    fun `track lyrics offset and playback source changes each realign the clock`() = onUiThread {
        Fixture().use { ui ->
            fun assertReset(update: (PlayerState) -> PlayerState) {
                val before = ui.clock
                ui.state = update(ui.state)
                ui.render()
                assertNotSame(before, ui.clock)
                assertEquals(ui.state.effectiveSnapshot.positionMs, ui.clock.positionMs)
            }
            assertReset { it.copy(lyricsVersion = "v2") }
            assertReset { it.copy(lyrics = it.lyrics!!.copy(offsetMs = 200L)) }
            assertReset { it.copy(snapshot = it.snapshot.copy(queue = listOf(it.snapshot.currentTrack!!.copy(id = "other")))) }
            assertReset { it.copy(castState = CastSessionState(
                status = CastSessionStatus.Casting, selectedDeviceId = "remote",
                playback = CastPlaybackState(positionMs = 4_000L, isPlaying = true),
            )) }
        }
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private class Fixture : AutoCloseable {
        val owner = Owner()
        private val time = TestTimeSource()
        var state by mutableStateOf(PlayerState(
            snapshot = PlaybackSnapshot(
                queue = listOf(Track("song", "local", "Song", mediaLocator = "", relativePath = "")),
                currentIndex = 0, positionMs = 1_000L, isPlaying = true,
            ),
            lyrics = LyricsDocument(listOf(LyricsLine(0L, "word")), sourceId = "local", rawPayload = ""),
            lyricsVersion = "v1",
        ))
        var line by mutableStateOf(EnhancedLyricsDisplayLine("word", 0L, segments = listOf(EnhancedLyricsSegment("word", 0L, 10_000L))))
        var visible by mutableStateOf(true)
        var browsing by mutableStateOf(false)
        var color by mutableStateOf(Color.White)
        lateinit var clock: LyricsPlaybackClock
        private var frame = 0L
        private val scene = ImageComposeScene(100, 100) {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val playbackClock = rememberLyricsPlaybackClock(state, state.snapshot.currentTrack!!, line, visible, browsing, time)
                clock = playbackClock
                Canvas(androidx.compose.ui.Modifier) {
                    drawRect(color, alpha = (playbackClock.positionMs % 1_000L).toFloat() / 1_000f)
                }
            }
        }
        init { render() }
        fun advance(ms: Int) {
            time += ms.milliseconds
            render()
        }
        fun render() = repeat(4) {
            Snapshot.sendApplyNotifications()
            scene.render(++frame * 16_666_667L).close()
        }
        fun hasPendingFrames() = scene.hasInvalidations()
        override fun close() = scene.close()
    }
}
