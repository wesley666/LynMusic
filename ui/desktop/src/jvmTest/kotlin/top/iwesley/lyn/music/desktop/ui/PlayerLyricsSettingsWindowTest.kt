@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class, androidx.compose.runtime.InternalComposeApi::class)

package top.iwesley.lyn.music.desktop.ui

import top.iwesley.lyn.music.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.findDefaultNavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.player.*
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class PlayerLyricsSettingsWindowTest {
    @Test fun `opening and closing windows preserves the actual lyrics viewport and scroll position`() = onUiThread {
        Fixture(realLyrics = true).use { ui ->
            ui.escape()
            val bounds = ui.lyricsBounds
            fun scrollValue() = ui.nodes().first { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
                .config[SemanticsProperties.VerticalScrollAxisRange].value()
            val scroll = scrollValue()
            assertTrue(scroll > 0f)
            ui.presentation.isLyricsStyleVisible = true
            ui.render()
            ui.click("Font")
            assertEquals(bounds, ui.lyricsBounds)
            assertEquals(scroll, scrollValue())
            ui.escape()
            ui.click("Custom color")
            assertEquals(bounds, ui.lyricsBounds)
            assertEquals(scroll, scrollValue())
            ui.escape()
            ui.escape()
            assertEquals(bounds, ui.lyricsBounds)
            assertEquals(scroll, scrollValue())
        }
    }

    @Test fun `palette updates immediately without closing and preserves custom selection`() = onUiThread {
        Fixture().use { ui ->
            val parentBounds = ui.node("Lyrics style").boundsInRoot
            ui.capture("desktop-main")
            ui.click("Custom color")
            assertEquals("Saturation 0%, brightness 100%", ui.node("Saturation and brightness").config[SemanticsProperties.StateDescription])
            val panel = ui.node("Saturation and brightness").boundsInRoot
            ui.clickAt(Offset(panel.left + panel.width * 0.65f, panel.top + panel.height * 0.3f))
            val first = ui.state.lyricsStyle.highlightArgb
            ui.scene.sendPointerEvent(PointerEventType.Press, panel.center)
            ui.scene.sendPointerEvent(PointerEventType.Move, Offset(panel.right - 20, panel.top + 20))
            ui.render()
            ui.scene.sendPointerEvent(PointerEventType.Move, Offset(panel.left + 30, panel.bottom - 30))
            ui.scene.sendPointerEvent(PointerEventType.Release, Offset(panel.left + 30, panel.bottom - 30))
            ui.render()
            assertNotEquals(first, ui.state.lyricsStyle.highlightArgb)
            assertEquals(3, ui.scene.semanticsOwners.size)
            assertEquals(parentBounds, ui.node("Lyrics style").boundsInRoot)
            ui.capture("desktop-palette")
            ui.click("Close")
            assertEquals(2, ui.scene.semanticsOwners.size)
            assertTrue(ui.node("Custom color").config[SemanticsProperties.Selected])
            ui.click("#FFD166")
            assertTrue(ui.node("#FFD166").config[SemanticsProperties.Selected])
            assertFalse(ui.node("Custom color").config[SemanticsProperties.Selected])
        }
    }

    @Test fun `glass refreshes from player only and stops recording when closed`() = onUiThread {
        Fixture().use { ui ->
            val backdrop = assertNotNull(ui.backdrop)
            // Both points are inside the solid source circle. Only the one near its edge
            // should blend with the blue background after blur; an empty recording fails this.
            assertNotEquals(ui.pixel(30, 380), ui.pixel(340, 450))
            val before = ui.pixel(425, 440)
            ui.backgroundColor = Color(0xFF2AD462)
            ui.render()
            assertNotEquals(before, ui.pixel(425, 440))
            ui.escape()
            val lastFrame = backdrop.frameVersion
            ui.backgroundColor = Color.Red
            ui.render()
            assertEquals(lastFrame, backdrop.frameVersion)
            assertNull(ui.backdrop)
            assertTrue(backdrop.layer.isReleased)
        }
    }

    @Test fun `small mobile windows scroll without moving lyrics or their parent panel`() = onUiThread {
        Fixture(width = 360, height = 640, mobile = true).use { ui ->
            val lyricsBounds = ui.lyricsBounds
            val titleBounds = ui.node("Lyrics style").boundsInRoot
            ui.capture("small-phone-main")
            ui.state = ui.state.copy(availableLyricsFonts = ui.state.availableLyricsFonts.map {
                it.copy(displayName = it.displayName + " — a long imported font family name")
            })
            ui.render()
            ui.click("Font")
            val scrollNode = ui.nodes().last { it.config.contains(SemanticsActions.ScrollBy) }
            assertTrue(scrollNode.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 500f))
            ui.render()
            ui.capture("small-phone-fonts")
            assertEquals(lyricsBounds, ui.lyricsBounds)
            assertEquals(titleBounds, ui.node("Lyrics style").boundsInRoot)
            ui.click("Close")
            ui.click("Custom color")
            ui.capture("small-phone-palette")
            assertEquals(3, ui.scene.semanticsOwners.size)
            assertEquals(titleBounds, ui.node("Lyrics style").boundsInRoot)
        }
    }

    @Test fun `font window owns input and stays open across selection and song changes`() = onUiThread {
        Fixture().use { ui ->
            val lyricsBounds = ui.lyricsBounds
            val parentTitleBounds = ui.node("Lyrics style").boundsInRoot
            ui.click("Font")
            ui.click("Imported font 1")
            assertEquals("imported:1", ui.state.lyricsStyle.fontKey)
            ui.click("Default")
            assertNull(ui.state.lyricsStyle.fontKey)
            assertEquals(3, ui.scene.semanticsOwners.size)
            assertEquals(parentTitleBounds, ui.node("Lyrics style").boundsInRoot)
            assertEquals(lyricsBounds, ui.lyricsBounds)
            val styleBefore = ui.state.lyricsStyle
            ui.clickAt(ui.node("A+").boundsInRoot.center)
            ui.clickAt(Offset(1000f, 100f))
            assertEquals(styleBefore.fontScale, ui.state.lyricsStyle.fontScale)
            assertEquals(0, ui.backgroundClicks)
            ui.state = ui.state.copy(lyricsTrackId = "another", lyricsVersion = "updated")
            ui.render()
            assertEquals(3, ui.scene.semanticsOwners.size)
            ui.escape()
            assertEquals(2, ui.scene.semanticsOwners.size)
            ui.escape()
            assertFalse(ui.presentation.isLyricsStyleVisible)
            assertEquals(1, ui.scene.semanticsOwners.size)
            ui.presentation.isLyricsStyleVisible = true
            ui.render()
            assertEquals(2, ui.scene.semanticsOwners.size)
            assertEquals(lyricsBounds, ui.lyricsBounds)
        }
    }

    @Test fun `offset buttons preserve old precision and style reset leaves calibration alone`() = onUiThread {
        Fixture().use { ui ->
            assertTrue(ui.nodes().none { it.config.contains(SemanticsActions.SetText) })
            ui.click("Earlier by 0.1 s")
            assertEquals(225L, ui.state.lyricsUserOffsetMs)
            ui.click("Later by 0.1 s")
            assertEquals(125L, ui.state.lyricsUserOffsetMs)
            ui.click("A+")
            ui.click("Reset style")
            assertEquals(PlayerLyricsStyle(), ui.state.lyricsStyle)
            assertEquals(125L, ui.state.lyricsUserOffsetMs)
            ui.click("Reset")
            assertEquals(0L, ui.state.lyricsUserOffsetMs)
            assertTrue(ui.node("Reset").config.contains(SemanticsProperties.Disabled))
            ui.state = ui.state.copy(originalLyrics = null)
            ui.render()
            assertTrue(ui.node("Earlier by 0.1 s").config.contains(SemanticsProperties.Disabled))
        }
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class Fixture(val width: Int = 1100, val height: Int = 760, val mobile: Boolean = false, realLyrics: Boolean = false) : AutoCloseable {
        var state by mutableStateOf(PlayerState(
            snapshot = PlaybackSnapshot(queue = listOf(Track("song", "local", "Song", mediaLocator = "", relativePath = "")), currentIndex = 0),
            originalLyrics = LyricsDocument(listOf(LyricsLine(0L, "Line")), sourceId = "local", rawPayload = "Line"),
            lyrics = if (realLyrics) LyricsDocument((0..30).map { LyricsLine(it * 1000L, "Lyrics line $it") }, sourceId = "local", rawPayload = "") else null,
            highlightedLineIndex = if (realLyrics) 12 else -1,
            lyricsTrackId = "song", lyricsVersion = "v1", lyricsUserOffsetMs = 125L,
            availableLyricsFonts = (1..20).map { LyricsShareFontOption("imported:$it", "Imported font $it", kind = LyricsShareFontKind.IMPORTED) },
        ))
        var lyricsBounds = Rect.Zero
        var backgroundClicks = 0
        var backgroundColor by mutableStateOf(Color(0xFF49667F))
        var backdrop: PlayerLyricsBackdrop? = null
        private val backInput = object : NavigationEventInput() { fun complete() = dispatchOnBackCompleted() }
        val presentation = PlayerPresentation({ state }).apply { isLyricsStyleVisible = true }
        val scene = ImageComposeScene(width, height) {
            val dispatcher = findDefaultNavigationEventDispatcherOwner()!!.navigationEventDispatcher
            DisposableEffect(dispatcher) {
                dispatcher.addInput(backInput)
                onDispose { dispatcher.removeInput(backInput) }
            }
            ProvideUiResourceLanguage("en") {
                androidx.compose.runtime.CompositionLocalProvider(LocalPlayerPresentation provides presentation) {
                LynMusicTheme {
                    val backdrop = rememberPlayerLyricsBackdrop(presentation.isLyricsStyleVisible).also { backdrop = it }
                    Box(Modifier.fillMaxSize().playerLyricsBackdropSource(backdrop).clickable { backgroundClicks++ }) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawRect(backgroundColor)
                            drawCircle(Color(0xFFA47482), radius = 230f, center = Offset(150f, 340f))
                        }
                        if (realLyrics) {
                            Box(Modifier.align(Alignment.CenterEnd).width(500.dp).height(600.dp).onGloballyPositioned { lyricsBounds = it.boundsInRoot() }) {
                                PlayerLyricsPane(state, state.snapshot.currentTrack!!, onPlayerIntent = {}, pure = true)
                            }
                        } else {
                            Text("Current lyrics line", Modifier.padding(start = 600.dp, top = 300.dp).onGloballyPositioned { lyricsBounds = it.boundsInRoot() })
                        }
                        if (presentation.isLyricsStyleVisible) PlayerLyricsSettingsOverlay(state, mobile, onIntent = { intent ->
                            state = when (intent) {
                                is PlayerIntent.LyricsStyleChanged -> state.copy(lyricsStyle = intent.style)
                                is PlayerIntent.LyricsOffsetChanged -> state.copy(lyricsUserOffsetMs = intent.offsetMs)
                                else -> state
                            }
                        }, backdrop = backdrop)
                    }
                }
            }
        }
        }
        private var frame = 0L
        init { render() }
        fun render() {
            repeat(4) {
                Snapshot.sendApplyNotifications()
                scene.render(++frame * 16_666_667L).close()
            }
        }
        fun nodes(): List<SemanticsNode> {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
        }
        fun node(label: String): SemanticsNode = nodes().lastOrNull {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true ||
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
        } ?: error("Missing $label: ${nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }}")
        fun click(label: String) = clickAt(node(label).boundsInRoot.center)
        fun clickAt(point: Offset) {
            scene.sendPointerEvent(PointerEventType.Press, point)
            scene.sendPointerEvent(PointerEventType.Release, point)
            render()
        }
        fun escape() {
            // Desktop maps an unconsumed Escape key to this navigation input outside ComposeScene.
            if (!scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown))) backInput.complete()
            scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyUp))
            render()
        }
        fun capture(name: String) {
            val output = System.getenv("LYN_LYRICS_QA_OUTPUT") ?: return
            val file = File(output, "$name.png")
            file.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { image ->
                image.encodeToData()!!.use { file.writeBytes(it.bytes) }
            }
        }
        fun pixel(x: Int, y: Int): Int = scene.render(++frame * 16_666_667L).use { image ->
            image.encodeToData()!!.use { ImageIO.read(it.bytes.inputStream()).getRGB(x, y) }
        }
        override fun close() = scene.close()
    }
}
