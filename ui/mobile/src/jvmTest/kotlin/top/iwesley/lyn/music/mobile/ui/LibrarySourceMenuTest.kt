@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.ui.InternalComposeUiApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package top.iwesley.lyn.music.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import java.io.File
import javax.swing.SwingUtilities
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.iwesley.lyn.music.OnlineSourceOption
import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.buildLibrarySourceFilterOptions
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class LibrarySourceMenuTest {
    @Test
    fun `local folders have one shared menu option without custom source names`() = onUiThread {
        Fixture(360).use { ui ->
            assertTrue(ui.visible("Local folder"))
            assertEquals(false, ui.visible("Local folder · Downloads"))
            assertEquals(false, ui.visible("Local folder · Other folder"))
            ui.click("Local folder")
            assertEquals(LibrarySourceFilter.LOCAL_FOLDER, ui.selected)
            ui.click("WebDAV · 公司")
            assertEquals(LibrarySourceFilter.Source("dav-2"), ui.selected)
        }
    }

    @Test
    fun `rendered source menu keeps same protocol sources separate and sends their ids`() = onUiThread {
        Fixture(360).use { ui ->
            assertTrue(ui.visible("WebDAV · 家里"))
            assertTrue(ui.visible("WebDAV · 公司"))
            assertTrue(ui.visible("Online NAS · 在线"))
            ui.click("WebDAV · 公司")
            assertEquals(LibrarySourceFilter.Source("dav-2"), ui.selected)
            ui.click("WebDAV · 家里")
            assertEquals(LibrarySourceFilter.Source("dav-1"), ui.selected)
            ui.click("Online NAS · 在线")
            assertEquals("nav-online", ui.onlineSourceId)
            ui.click("WebDAV · 公司")
            assertEquals(null, ui.onlineSourceId)
            assertEquals(LibrarySourceFilter.Source("dav-2"), ui.selected)
        }
    }

    @Test
    fun `long source labels stay within the menu in narrow and wide windows`() = onUiThread {
        for (width in listOf(360, 1100)) Fixture(width).use { ui ->
            val text = ui.node(ui.longLabel)
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(text.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts))
            assertEquals(1, layouts.single().lineCount)
            ui.capture()
            assertTrue(text.boundsInRoot.width <= width)
        }
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class Fixture(val width: Int) : AutoCloseable {
        var selected by mutableStateOf<LibrarySourceFilter>(LibrarySourceFilter.Source("dav-1"))
        var onlineSourceId by mutableStateOf<String?>(null)
        private val longName = "A very long music library name ".repeat(12)
        val longLabel = "WebDAV · ${longName.trim()}"
        private val options = buildLibrarySourceFilterOptions(
            listOf(
                ImportSource("local-1", ImportSourceType.LOCAL_FOLDER, "Downloads", "existing-root"),
                ImportSource("local-2", ImportSourceType.LOCAL_FOLDER, "Other folder", "existing-root"),
            ) + listOf("dav-1" to "家里", "dav-2" to "公司", "dav-long" to longName).map { (id, name) ->
                ImportSource(id, ImportSourceType.WEBDAV, name, "existing-root")
            },
        )
        private val scheduler = TestCoroutineScheduler()
        private var frame = 0L
        private val scene = ImageComposeScene(width, 600, coroutineContext = UnconfinedTestDispatcher(scheduler)) {
            ProvideUiResourceLanguage("en") {
                LynMusicTheme {
                    Box(Modifier.padding(20.dp).size(40.dp)) {
                        LibrarySourceFilterDropdownMenu(
                            expanded = true,
                            sourceFilterOptions = options,
                            selectedSourceFilter = selected,
                            onlineSourceOptions = listOf(OnlineSourceOption("nav-online", UiText.Raw("Online NAS · 在线"))),
                            selectedOnlineSourceId = onlineSourceId,
                            onDismiss = {},
                            onSourceFilterChanged = { selected = it; onlineSourceId = null },
                            onOnlineSourceSelected = { onlineSourceId = it },
                        )
                    }
                }
            }
        }
        init { render() }
        fun render() {
            repeat(30) {
                scheduler.runCurrent()
                Snapshot.sendApplyNotifications()
                scene.render(++frame * 16_666_667L).close()
            }
        }
        fun node(label: String): SemanticsNode {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
                .lastOrNull { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true }
                ?: error("Missing menu label: $label")
        }
        fun visible(label: String) = runCatching { node(label) }.isSuccess
        fun click(label: String) {
            val point = node(label).boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, point)
            scene.sendPointerEvent(PointerEventType.Release, point)
            render()
        }
        fun capture() {
            val output = System.getenv("LYN_SOURCE_MENU_QA_OUTPUT") ?: return
            val file = File(output, "mobile-source-menu-$width.png")
            file.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { image ->
                image.encodeToData()!!.use { file.writeBytes(it.bytes) }
            }
        }
        override fun close() = scene.close()
    }
}
