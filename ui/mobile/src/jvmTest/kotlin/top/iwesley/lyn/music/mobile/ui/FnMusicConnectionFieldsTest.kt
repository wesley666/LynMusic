@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package top.iwesley.lyn.music.mobile.ui

import top.iwesley.lyn.music.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.FnMusicConnectionMode
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class FnMusicConnectionFieldsTest {
    @Test
    fun `phone and desktop forms keep a masked access field across modes`() = onUiThread {
        for (width in listOf(420, 800)) {
            for (editing in listOf(false, true)) {
                Fixture(width, editing).use { ui ->
                    assertFalse(ui.accessField().config.contains(SemanticsProperties.Disabled))
                    ui.capture("address")
                    ui.clickText("FN Connect")
                    assertEquals(FnMusicConnectionMode.FN_CONNECT, ui.mode)
                    ui.accessField()
                    ui.capture("fn-connect")
                    ui.clickText("Server address")
                    ui.accessField()
                    assertEquals("typed-code", ui.code)
                    assertEquals(editing, ui.nodes().any { it.config.contains(SemanticsProperties.ToggleableState) })
                }
            }
        }
    }

    @Test
    fun `clear switch disables input and switching it off restores the typed value`() = onUiThread {
        Fixture(420, true).use { ui ->
            ui.clickClear()
            assertTrue(ui.clear)
            assertTrue(ui.accessField().config.contains(SemanticsProperties.Disabled))
            ui.capture("clearing")
            ui.clickText("FN Connect")
            assertTrue(ui.clear)
            assertTrue(ui.accessField().config.contains(SemanticsProperties.Disabled))
            ui.clickClear()
            assertFalse(ui.clear)
            assertFalse(ui.accessField().config.contains(SemanticsProperties.Disabled))
            assertEquals("typed-code", ui.code)
        }
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class Fixture(private val width: Int, private val editing: Boolean) : AutoCloseable {
        var mode by mutableStateOf(FnMusicConnectionMode.ADDRESS)
        var code by mutableStateOf("typed-code")
        var clear by mutableStateOf(false)
        private var frame = 0L
        private val scene = ImageComposeScene(width, 700) {
            ProvideUiResourceLanguage("en") {
                LynMusicTheme {
                    Column(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FnMusicConnectionFields(
                            connectionMode = mode, baseUrl = "http://nas:5666", wanBaseUrl = "https://music.example.com", fnId = "mynas",
                            accessCode = code, isEditing = editing, enabled = true, fieldColors = OutlinedTextFieldDefaults.colors(),
                            onConnectionModeChange = { mode = it }, onBaseUrlChange = {}, onWanBaseUrlChange = {}, onFnIdChange = {},
                            onAccessCodeChange = { code = it }, clearAccessCode = clear, onClearAccessCodeChange = { clear = it },
                        )
                    }
                }
            }
        }
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
        fun accessField(): SemanticsNode = nodes().single { it.config.contains(SemanticsProperties.Password) }
        fun clickText(text: String) = click(nodes().last {
            it.config.getOrNull(SemanticsProperties.Text)?.any { label -> label.text == text } == true
        })
        fun clickClear() = click(nodes().single { it.config.contains(SemanticsProperties.ToggleableState) })
        private fun click(node: SemanticsNode) {
            scene.sendPointerEvent(PointerEventType.Press, node.boundsInRoot.center)
            scene.sendPointerEvent(PointerEventType.Release, node.boundsInRoot.center)
            render()
        }
        fun capture(state: String) {
            val file = File("build/reports/fn-access-qa/${width}-${if (editing) "edit" else "create"}-$state.png")
            file.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { image ->
                image.encodeToData()!!.use { file.writeBytes(it.bytes) }
            }
        }
        override fun close() = scene.close()
    }
}
