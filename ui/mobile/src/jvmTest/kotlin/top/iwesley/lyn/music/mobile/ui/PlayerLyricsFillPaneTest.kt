@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package top.iwesley.lyn.music.mobile.ui

import top.iwesley.lyn.music.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.PlayerLyricsStyle
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.domain.parseCachedLyrics
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class PlayerLyricsFillPaneTest {
    @Test
    fun `normal pure and compact modes share word rendering and keep translation whole`() = onUiThread {
        for ((pure, compact) in listOf(false to false, true to false, true to true)) {
            Fixture(pure, compact).use { ui ->
                val layout = ui.layout("Lyrics line 4")
                assertEquals(Color.White, layout.layoutInput.style.color) // The word renderer owns paint color.
                assertEquals(FontWeight.Bold, layout.layoutInput.style.fontWeight)
                val translation = ui.layout("Translation 4")
                assertEquals(FontWeight.Normal, translation.layoutInput.style.fontWeight)
                assertEquals(Color.White.copy(alpha = 0.9f), translation.layoutInput.style.color)
                ui.state = ui.state.copy(lyricsStyle = ui.state.lyricsStyle.copy(highlightArgb = 0xFF00FFFF.toInt()))
                ui.render(24)
                assertSame(layout.multiParagraph, ui.layout("Lyrics line 4").multiParagraph)
                ui.state = ui.state.copy(lyricsStyle = ui.state.lyricsStyle.copy(fontScale = 1.2f))
                ui.render(24)
                assertNotSame(layout.multiParagraph, ui.layout("Lyrics line 4").multiParagraph)
            }
        }
    }

    @Test
    fun `drag browsing uses whole line highlight then restores word rendering after timeout`() = onUiThread {
        Fixture(pure = true).use { ui ->
            assertEquals(Color.White, ui.layout("Lyrics line 4").layoutInput.style.color)
            ui.drag()
            val browseHighlighted = ui.lyricNodes().filter { ui.layout(it).layoutInput.style.fontWeight == FontWeight.Bold }
            assertEquals(1, browseHighlighted.size)
            assertEquals(Color.Green, ui.layout(browseHighlighted.single()).layoutInput.style.color)
            ui.scheduler.advanceTimeBy(2_401L)
            ui.scheduler.runCurrent()
            ui.render(40)
            assertEquals(Color.White, ui.layout("Lyrics line 4").layoutInput.style.color)
            assertEquals(1, ui.lyricNodes().count { ui.layout(it).layoutInput.style.fontWeight == FontWeight.Bold })
        }
    }

    @Test
    fun `playback highlight changes only when the reported highlighted index changes`() = onUiThread {
        Fixture(pure = true).use { ui ->
            ui.state = ui.state.copy(snapshot = ui.state.snapshot.copy(positionMs = 11_000L))
            ui.render(30)
            assertEquals(FontWeight.Bold, ui.layout("Lyrics line 4").layoutInput.style.fontWeight)
            assertFalse(ui.lyricNodes().filter { ui.text(it) != "Lyrics line 4" }.any { ui.layout(it).layoutInput.style.fontWeight == FontWeight.Bold })
            ui.state = ui.state.copy(highlightedLineIndex = 5)
            ui.render(40)
            assertEquals(FontWeight.Bold, ui.layout("Lyrics line 5").layoutInput.style.fontWeight)
            assertEquals(Color.White, ui.layout("Lyrics line 5").layoutInput.style.color)
        }
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class Fixture(pure: Boolean, compact: Boolean = false) : AutoCloseable {
        val scheduler = TestCoroutineScheduler()
        private val dispatcher = UnconfinedTestDispatcher(scheduler)
        private val raw = "<tt xmlns:m=\"urn:metadata\"><body>" + (0..15).joinToString("") { index ->
            val start = index * 2
            "<p begin=\"${start}s\" end=\"${start + 2}s\"><span begin=\"${start}s\" end=\"${start + 2}s\">Lyrics line $index</span><span m:role=\"x-translation\">Translation $index</span></p>"
        } + "</body></tt>"
        var state by mutableStateOf(PlayerState(
            snapshot = PlaybackSnapshot(
                queue = listOf(Track("song", "local", "Song", mediaLocator = "", relativePath = "")),
                currentIndex = 0, positionMs = 9_000L, durationMs = 32_000L,
            ),
            lyrics = parseCachedLyrics("managed-lrcapi", raw)!!,
            lyricsVersion = "v1", highlightedLineIndex = 4,
            lyricsStyle = PlayerLyricsStyle(highlightArgb = 0xFF00FF00.toInt()),
        ))
        private var frame = 0L
        private val scene = ImageComposeScene(700, 800, coroutineContext = dispatcher) {
            ProvideUiResourceLanguage("en") {
                LynMusicTheme {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        PlayerLyricsPane(state, state.snapshot.currentTrack!!, {}, pure = pure, compact = compact)
                    }
                }
            }
        }
        init { render(40) }
        fun render(count: Int = 8) = repeat(count) {
            scheduler.runCurrent()
            Snapshot.sendApplyNotifications()
            scene.render(++frame * 16_666_667L).close()
        }
        fun nodes(): List<SemanticsNode> {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
        }
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.singleOrNull()?.text
        fun lyricNodes() = nodes().filter { text(it)?.startsWith("Lyrics line ") == true }
        fun layout(label: String) = layout(nodes().single { text(it) == label })
        fun layout(node: SemanticsNode): TextLayoutResult {
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action!!(results)
            return results.single()
        }
        fun drag() {
            val start = Offset(350f, 500f)
            scene.sendPointerEvent(PointerEventType.Press, start, type = PointerType.Touch, timeMillis = 1_000L)
            scene.sendPointerEvent(PointerEventType.Move, start - Offset(0f, 40f), type = PointerType.Touch, timeMillis = 1_100L)
            render()
            scene.sendPointerEvent(PointerEventType.Move, start - Offset(0f, 130f), type = PointerType.Touch, timeMillis = 1_300L)
            render()
            scene.sendPointerEvent(PointerEventType.Release, start - Offset(0f, 130f), type = PointerType.Touch, timeMillis = 1_700L)
            render(40)
        }
        override fun close() = scene.close()
    }
}
