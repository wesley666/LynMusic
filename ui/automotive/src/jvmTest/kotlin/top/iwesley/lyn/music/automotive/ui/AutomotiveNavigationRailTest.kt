@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package top.iwesley.lyn.music.automotive.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Constraints
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.resources.ProvideUiResourceLanguage
import top.iwesley.lyn.music.ui.LynMusicTheme

class AutomotiveNavigationRailTest {
    @Test
    fun `selection marker is visible without a pill in both rail states`() = onUiThread {
        Fixture(light = true).use { ui ->
            for (expanded in listOf(false, true)) {
                if (expanded) ui.toggle()
                ui.assertSelectionMarker(ui.selectedTab)
                for (tab in listOf(AppTab.My, AppTab.Library)) {
                    // Inspect the resting selection, independently of the transient press ripple.
                    ui.node("automotive-navigation-${tab.name}")
                        .config[SemanticsActions.OnClick].action!!.invoke()
                    ui.render()
                    ui.capture("selection-${if (expanded) "expanded" else "collapsed"}-${tab.name}")
                    ui.assertSelectionMarker(tab)
                }
            }
        }
    }

    @Test
    fun `toggle preserves destination and rotation preserves expansion`() = onUiThread {
        Fixture().use { ui ->
            assertEquals(96f, ui.railWidth)
            assertEquals(WideNavigationRailValue.Collapsed, ui.state.currentValue)
            ui.click("automotive-navigation-Playlists")
            repeat(3) {
                ui.toggle()
                assertTrue(ui.railWidth in 220f..360f)
                assertEquals(AppTab.Playlists, ui.selectedTab)
                ui.resize(480, 900)
                ui.resize(1280, 800)
                assertEquals(WideNavigationRailValue.Expanded, ui.state.currentValue)
                ui.toggle()
                assertEquals(96f, ui.railWidth)
                assertEquals(AppTab.Playlists, ui.selectedTab)
            }
        }
    }

    @Test
    fun `saved state restores expansion while a fresh launch starts collapsed`() = onUiThread {
        val saved = Fixture().use { ui ->
            ui.toggle()
            ui.registry.performSave()
        }
        Fixture(restored = saved).use { ui ->
            assertEquals(WideNavigationRailValue.Expanded, ui.state.currentValue)
            assertTrue(ui.railWidth in 220f..360f)
        }
        Fixture().use { ui -> assertEquals(WideNavigationRailValue.Collapsed, ui.state.currentValue) }
    }

    @Test
    fun `rapid toggle taps settle on the last requested state`() = onUiThread {
        Fixture().use { ui ->
            repeat(5) { ui.click("automotive-navigation-toggle", frames = 2) }
            ui.render()
            assertEquals(WideNavigationRailValue.Expanded, ui.state.currentValue)
            assertTrue(ui.railWidth in 220f..360f)
            assertEquals(AppTab.Library, ui.selectedTab)
        }
    }

    @Test
    fun `short landscape keeps all destinations reachable without shrinking targets`() = onUiThread {
        for (expanded in listOf(false, true)) {
            Fixture(width = 800, height = 360, fontScale = 1.5f).use { ui ->
                if (expanded) ui.toggle()
                val scroll = ui.node("automotive-navigation-scroll")
                assertTrue(scroll.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f)
                for (tab in automotiveLandscapeNavigationTabs) {
                    ui.scrollTo(tab)
                    ui.assertTouchTarget("automotive-navigation-${tab.name}")
                    ui.click("automotive-navigation-${tab.name}")
                    assertEquals(tab, ui.selectedTab)
                }
                ui.capture("short-${if (expanded) "expanded" else "collapsed"}")
                ui.scrollBy(-10000f)
                ui.assertTouchTarget("automotive-navigation-toggle")
                ui.toggle()
                assertEquals(AppTab.Settings, ui.selectedTab)
            }
        }
    }

    @Test
    fun `labels targets and update badges survive themes languages and enlarged text`() = onUiThread {
        for ((language, light, scale) in listOf(
            Triple("en", false, 1f),
            Triple("zh-CN", true, 1f),
            Triple("zh-TW", false, 1.5f),
            Triple("en", true, 1.5f),
        )) {
            Fixture(height = 900, language = language, light = light, fontScale = scale).use { ui ->
                for (expanded in listOf(false, true)) {
                    if (expanded) ui.toggle()
                    ui.assertTouchTarget("automotive-navigation-toggle")
                    val toggleLabel = when (language) {
                        "zh-CN" -> if (expanded) "收起导航" else "展开导航"
                        "zh-TW" -> if (expanded) "收起導覽" else "展開導覽"
                        else -> if (expanded) "Collapse navigation" else "Expand navigation"
                    }
                    assertEquals(listOf(toggleLabel), ui.node("automotive-navigation-toggle")
                        .config[SemanticsProperties.ContentDescription])
                    for (tab in automotiveLandscapeNavigationTabs) {
                        ui.assertTouchTarget("automotive-navigation-${tab.name}")
                        val item = ui.node("automotive-navigation-${tab.name}")
                        assertEquals(tab == AppTab.Library, item.config[SemanticsProperties.Selected])
                        assertTrue(item.config[SemanticsProperties.Text].isNotEmpty())
                    }
                    ui.assertLabelsNotClipped()
                    if (expanded) ui.assertExpandedAlignment()
                    ui.assertBadgeChangesIconPixels()
                    ui.assertNavigationPixels()
                    ui.capture("$language-${if (light) "light" else "dark"}-$scale-${if (expanded) "expanded" else "collapsed"}")
                }
            }
        }
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class Fixture(
        width: Int = 1280,
        height: Int = 800,
        language: String = "en",
        light: Boolean = false,
        fontScale: Float = 1f,
        restored: Map<String, List<Any?>>? = null,
    ) : AutoCloseable {
        val registry = SaveableStateRegistry(restored) { true }
        lateinit var state: WideNavigationRailState
        var selectedTab by mutableStateOf(AppTab.Library)
        private var badge by mutableStateOf(false)
        private var frame = 0L
        private val scene = ImageComposeScene(width, height, density = Density(1f, fontScale)) {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                ProvideUiResourceLanguage(language) {
                    LynMusicTheme(
                        themeTokens = if (light) OCEAN_APP_THEME_TOKENS else CLASSIC_APP_THEME_TOKENS,
                        textPalette = if (light) AppThemeTextPalette.Black else AppThemeTextPalette.White,
                    ) {
                        state = rememberWideNavigationRailState()
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            if (maxWidth > maxHeight) {
                                Row {
                                    AutomotiveSideNavigation(state, selectedTab, badge, { selectedTab = it })
                                    Text("Content: $selectedTab")
                                }
                            } else {
                                Text("Portrait")
                            }
                        }
                    }
                }
            }
        }
        init { render() }

        val railWidth get() = node("automotive-navigation-rail").boundsInRoot.width

        fun render(frames: Int = 90) {
            repeat(frames) {
                Snapshot.sendApplyNotifications()
                scene.render(++frame * 16_666_667L).close()
            }
        }

        private fun nodes(unmerged: Boolean = false): List<SemanticsNode> {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(if (unmerged) it.unmergedRootSemanticsNode else it.rootSemanticsNode) }
        }

        fun node(tag: String, unmerged: Boolean = false) = nodes(unmerged)
            .single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }

        fun click(tag: String, frames: Int = 90) {
            val target = node(tag)
            scene.sendPointerEvent(PointerEventType.Press, target.boundsInRoot.center, type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, target.boundsInRoot.center, type = PointerType.Touch)
            render(frames)
        }

        fun toggle() = click("automotive-navigation-toggle")

        fun resize(width: Int, height: Int) {
            scene.constraints = Constraints.fixed(width, height)
            render()
        }

        fun scrollBy(delta: Float) {
            assertTrue(node("automotive-navigation-scroll").config[SemanticsActions.ScrollBy].action!!.invoke(0f, delta))
            render()
        }

        fun scrollTo(tab: AppTab) {
            val target = node("automotive-navigation-${tab.name}")
            val viewport = node("automotive-navigation-scroll").boundsInRoot
            // Position against the full layout bounds, even when the item is currently clipped.
            val bounds = target.positionInRoot
            scrollBy(bounds.y - viewport.top)
        }

        fun assertTouchTarget(tag: String) {
            val bounds = node(tag).boundsInRoot
            assertTrue(bounds.width >= 76f && bounds.height >= 76f, "$tag is clipped or too small: $bounds")
            assertTrue(bounds.top >= 0f && bounds.bottom <= scene.constraints.maxHeight, "$tag outside viewport: $bounds")
        }

        fun assertLabelsNotClipped() {
            for (node in nodes()) {
                val results = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                for (result in results) {
                    // Paragraph width can retain the incoming max constraint even when Text
                    // wraps its intrinsic width. Check the rendered line extents instead.
                    assertFalse(result.multiParagraph.didExceedMaxLines)
                    assertTrue(result.multiParagraph.height <= result.size.height + 1f)
                    for (line in 0 until result.lineCount) {
                        assertFalse(result.isLineEllipsized(line))
                        val lineWidth = result.getLineRight(line) - result.getLineLeft(line)
                        assertTrue(lineWidth <= result.size.width + 1f,
                            "Clipped: ${result.layoutInput.text}, width=${result.size.width}, lineWidth=$lineWidth")
                    }
                }
            }
        }

        private fun pixels() = scene.render(++frame * 16_666_667L).use { image ->
            image.encodeToData()!!.use { ImageIO.read(it.bytes.inputStream()) }
        }

        fun assertSelectionMarker(tab: AppTab) {
            val marker = node("automotive-navigation-marker").boundsInRoot
            val rail = node("automotive-navigation-rail").boundsInRoot
            val item = node("automotive-navigation-${tab.name}").boundsInRoot
            assertEquals(12f, marker.left - rail.left)
            assertTrue(item.contains(marker.center), "Marker must stay beside the selected item")
            val image = pixels()
            assertEquals(OCEAN_APP_THEME_TOKENS.accentArgb,
                image.getRGB(marker.center.x.toInt(), marker.center.y.toInt()),
                "Missing marker for $tab in ${state.currentValue}: $marker, item=$item")
            val icon = node("automotive-navigation-icon-${tab.name}", unmerged = true).boundsInRoot
            // The area just outside the icon used to contain the selected pill.
            val background = image.getRGB(10, scene.constraints.maxHeight - 20)
            assertEquals(background, image.getRGB((icon.left - 5).toInt(), icon.center.y.toInt()))
        }

        fun assertExpandedAlignment() {
            val toggle = node("automotive-navigation-toggle").boundsInRoot
            val labelStart = node("automotive-navigation-label-My", unmerged = true).boundsInRoot.left
            for (tab in automotiveLandscapeNavigationTabs) {
                val icon = node("automotive-navigation-icon-${tab.name}", unmerged = true).boundsInRoot
                val label = node("automotive-navigation-label-${tab.name}", unmerged = true).boundsInRoot
                assertEquals(toggle.center.x, icon.center.x, 1f, "$tab icon must align with the menu button")
                assertEquals(labelStart, label.left, 1f, "$tab label must align with other labels")
            }
        }

        fun assertNavigationPixels() {
            val image = pixels()
            val background = image.getRGB(10, scene.constraints.maxHeight - 20)
            for (tab in automotiveLandscapeNavigationTabs) {
                val bounds = node("automotive-navigation-icon-${tab.name}", unmerged = true).boundsInRoot
                var visible = 0
                for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
                    for (x in bounds.left.toInt() until bounds.right.toInt()) {
                        if (image.getRGB(x, y) != background) visible++
                    }
                }
                assertTrue(visible >= 20, "$tab icon is missing in ${state.currentValue}")
            }
            val marker = node("automotive-navigation-marker").boundsInRoot
            assertNotEquals(background, image.getRGB(marker.center.x.toInt(), marker.center.y.toInt()))
        }

        fun assertBadgeChangesIconPixels() {
            badge = false
            render()
            val before = pixels()
            badge = true
            render()
            val after = pixels()
            val bounds = node("automotive-navigation-icon-Settings", unmerged = true).boundsInRoot
            var changed = 0
            for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
                for (x in bounds.left.toInt() until bounds.right.toInt()) {
                    if (before.getRGB(x, y) != after.getRGB(x, y)) changed++
                }
            }
            assertTrue(changed >= 20, "Settings update badge is missing")
        }

        fun capture(name: String) {
            val file = File("build/reports/automotive-ui/rail-$name.png")
            file.parentFile.mkdirs()
            ImageIO.write(pixels(), "png", file)
        }

        override fun close() = scene.close()
    }
}
