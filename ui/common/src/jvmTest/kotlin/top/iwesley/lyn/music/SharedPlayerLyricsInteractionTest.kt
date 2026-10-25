@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package top.iwesley.lyn.music

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
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import top.iwesley.lyn.music.core.model.PlayerLyricsStyle
import top.iwesley.lyn.music.domain.parseCachedLyrics
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class SharedPlayerLyricsInteractionTest {
    @Test fun `fresh state reports and translation changes preserve the original browsing deadline`() = onUiThread {
        for ((pure, compact) in listOf(false to false, true to false, true to true)) {
            Fixture(pure, compact).use { ui ->
                ui.drag()
                ui.assertBrowsingHighlight()
                ui.scheduler.advanceTimeBy(1_200L)
                ui.state = ui.state.copy(positionMs = 11_000L, highlightedLineIndex = 5)
                ui.style = ui.style.copy(showTranslation = false)
                ui.render(40)
                ui.assertBrowsingHighlight()
                // Host state recreation and unrelated settings updates must not renew the timer.
                ui.state = ui.state.copy(hasLyricsLookupCompleted = true)
                ui.style = ui.style.copy(fontScale = 1.1f)
                ui.render(40)
                ui.scheduler.advanceTimeBy(1_199L)
                ui.render(40)
                ui.assertBrowsingHighlight()
                ui.scheduler.advanceTimeBy(2L)
                ui.render(40)
                assertEquals(Color.White, ui.layout("Lyrics line 5").layoutInput.style.color)
                ui.assertPlaybackLineCentered()
            }
        }
    }

    @Test fun `browse seek uses the calibrated document exactly once`() = onUiThread {
        for (userOffset in listOf(350L, -450L)) {
            Fixture(pure = true).use { ui ->
                val originalOffset = 200L
                val document = ui.state.lyrics!!.copy(offsetMs = originalOffset + userOffset)
                ui.state = ui.state.copy(lyrics = document, lyricsUserOffsetMs = userOffset)
                ui.render(40)
                ui.drag()
                ui.assertBrowsingHighlight()
                val target = ui.lyricNodes().single { ui.layout(it).layoutInput.style.fontWeight == FontWeight.Bold }
                val index = ui.text(target)!!.substringAfterLast(' ').toLong()
                val seek = ui.nodes().single { it.config.contains(SemanticsActions.OnClick) }
                seek.config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals(index * 2_000L - originalOffset - userOffset, ui.seekPosition)
                assertSame(document, ui.state.lyrics)
                assertEquals(userOffset, ui.state.lyricsUserOffsetMs)
                ui.render(40)
                assertEquals(Color.White, ui.layout("Lyrics line 4").layoutInput.style.color)
            }
        }
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class Fixture(pure: Boolean, compact: Boolean = false, language: String = "en") : AutoCloseable {
        val scheduler = TestCoroutineScheduler()
        private val dispatcher = UnconfinedTestDispatcher(scheduler)
        private val raw = "<tt xmlns:m=\"urn:metadata\" xml:lang=\"$language\"><body>" + (0..15).joinToString("") { index ->
            val start = index * 2
            "<p begin=\"${start}s\" end=\"${start + 2}s\"><span begin=\"${start}s\" end=\"${start + 2}s\">Lyrics line $index</span><span m:role=\"x-translation\">Translation $index</span></p>"
        } + "</body></tt>"
        var state by mutableStateOf(PlayerLyricsUiState(
            trackId = "song", trackSourceId = "local", positionMs = 9_000L, durationMs = 32_000L,
            lyrics = parseCachedLyrics("managed-lrcapi", raw)!!,
            lyricsVersion = "v1", highlightedLineIndex = 4,
        ))
        var style by mutableStateOf(PlayerLyricsStyle(highlightArgb = 0xFF00FF00.toInt()))
        var seekPosition: Long? = null
        private var frame = 0L
        private val scene = ImageComposeScene(700, 800, coroutineContext = dispatcher) {
            ProvideUiResourceLanguage("en") {
                LynMusicTheme {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        SharedPlayerLyricsPane(state, style, null, { seekPosition = it }, pure = pure, compact = compact,
                            header = { androidx.compose.material3.Text("Song") }, emptyState = {})
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
        fun assertPlaybackLineCentered() {
            val index = state.highlightedLineIndex
            val original = nodes().single { text(it) == "Lyrics line $index" }.boundsInRoot
            val translation = nodes().firstOrNull { text(it) == "Translation $index" }?.boundsInRoot
            val center = (original.top + (translation?.bottom ?: original.bottom)) / 2
            val viewportCenter = nodes().first {
                it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null
            }.boundsInRoot.center.y
            assertTrue(kotlin.math.abs(center - viewportCenter) <= 2f,
                "Playback line center $center differs from viewport center $viewportCenter")
        }
        fun assertBrowsingHighlight() {
            val highlighted = lyricNodes().single { layout(it).layoutInput.style.fontWeight == FontWeight.Bold }
            assertEquals(Color.Green, layout(highlighted).layoutInput.style.color)
        }
        fun layout(label: String) = layout(nodes().single { text(it) == label })
        fun layout(node: SemanticsNode): TextLayoutResult {
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action!!(results)
            return results.single()
        }
        fun drag(onDragging: () -> Unit = {}) {
            val start = Offset(350f, 500f)
            scene.sendPointerEvent(PointerEventType.Press, start, type = PointerType.Touch, timeMillis = 1_000L)
            scene.sendPointerEvent(PointerEventType.Move, start - Offset(0f, 40f), type = PointerType.Touch, timeMillis = 1_100L)
            render()
            scene.sendPointerEvent(PointerEventType.Move, start - Offset(0f, 130f), type = PointerType.Touch, timeMillis = 1_300L)
            render()
            onDragging()
            scene.sendPointerEvent(PointerEventType.Release, start - Offset(0f, 130f), type = PointerType.Touch, timeMillis = 1_700L)
            render(40)
        }
        override fun close() = scene.close()
    }
}
