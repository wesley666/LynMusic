@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package top.iwesley.lyn.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import top.iwesley.lyn.music.domain.EnhancedLyricsDisplayLine
import top.iwesley.lyn.music.domain.EnhancedLyricsSegment

class EnhancedLyricsDrawingTest {
    @Test
    fun `fill advances inside glyphs with a soft edge and no seam before it`() = onUiThread {
        Fixture("MMMMMMMMMMMM").use { ui ->
            ui.positionMs = 0L
            val before = ui.image()
            assertTrue(before.pixels().any { it.red > 100 })
            assertTrue(before.pixels().all { it.green == 0 })
            var previousGreen = 0L
            for (position in listOf(200L, 350L, 500L, 650L, 800L)) {
                ui.positionMs = position
                val image = ui.image()
                val green = image.pixels().sumOf { it.green.toLong() }
                assertTrue(green > previousGreen, "fill should advance at $position")
                previousGreen = green
                assertTrue(image.pixels().count { it.red > 15 && it.green in 15..235 } > 3, "soft edge should interpolate inside glyphs")
            }
            ui.positionMs = 1_000L
            val full = ui.image()
            assertTrue(full.pixels().all { it.red == 0 })
            ui.positionMs = 500L
            val partial = ui.image()
            val geometry = buildEnhancedLyricsGeometry(ui.line.text, ui.line.segments.map { it.text }, ui.layout())!!
            val mask = buildLyricsFillMask(geometry, listOf(0.5f), 14f)
            val edge = mask.edges.single().rect
            for (x in (edge.left.toInt() - 1).coerceAtLeast(0) until (edge.right.toInt() + 1).coerceAtMost(full.width)) {
                for (y in edge.top.toInt().coerceAtLeast(0) until edge.bottom.toInt().coerceAtMost(full.height)) {
                    if (full.getRGB(x, y).green > 250) {
                        val pixel = partial.getRGB(x, y)
                        assertTrue(pixel.red + pixel.green >= 200, "dark seam in the soft edge at $x,$y")
                    }
                }
            }
            for (rect in mask.solidRects) {
                for (x in (rect.left.toInt() + 1) until (rect.right.toInt() - 1)) {
                    for (y in rect.top.toInt().coerceAtLeast(0) until rect.bottom.toInt().coerceAtMost(full.height)) {
                        if (full.getRGB(x, y).green > 250) {
                            assertTrue(partial.getRGB(x, y).green > 248, "seam at $x,$y")
                            assertEquals(0, partial.getRGB(x, y).red)
                        }
                    }
                }
            }
            ui.capture("smooth-fill-half")
        }
    }

    @Test
    fun `drawing frames and highlight colors reuse the identical text layout`() = onUiThread {
        Fixture("Test Word").use { ui ->
            val layout = ui.layout()
            val compositions = ui.compositions
            repeat(30) {
                ui.positionMs = it * 25L
                ui.image()
                assertSame(layout.multiParagraph, ui.layout().multiParagraph)
            }
            assertEquals(compositions, ui.compositions)
            ui.activeColor = Color.Cyan
            assertTrue(ui.image().pixels().any { it.green > 100 && it.blue > 100 })
            assertSame(layout.multiParagraph, ui.layout().multiParagraph)
            ui.fontSize = 42
            ui.image()
            assertNotSame(layout.multiParagraph, ui.layout().multiParagraph)
            val resized = ui.layout()
            ui.fontFamily = FontFamily.Serif
            ui.image()
            assertNotSame(resized.multiParagraph, ui.layout().multiParagraph)
            val changedFont = ui.layout()
            ui.resizeWidth(240)
            assertNotSame(changedFont.multiParagraph, ui.layout().multiParagraph)
        }
    }

    @Test
    fun `whole line shaping preserves spaces emoji and ligatures with aligned layers`() = onUiThread {
        val text = "office 👩‍👩‍👧‍👦 ffi"
        val segments = listOf("of", "fice ", "👩‍👩‍👧‍👦", " ffi")
        Fixture(text, segments, font = FontFamily.Serif).use { ui ->
            ui.positionMs = 1_000L
            val painted = ui.image()
            val layout = ui.layout()
            assertEquals(text, layout.layoutInput.text.text)
            assertEquals(listOf(text), ui.textNodes().map { it.config[SemanticsProperties.Text].single().text })
            val geometry = buildEnhancedLyricsGeometry(text, segments, layout)!!
            assertEquals(segments.size, geometry.size)
            assertTrue(geometry.all { it.fragments.isNotEmpty() })
            ui.reference = true
            val reference = ui.image()
            assertContentPixelsEqual(reference, painted)
        }
    }

    @Test
    fun `emoji and combining marks never borrow the next segment rectangle or color`() = onUiThread {
        val cases = listOf("😀", "👩‍👩‍👧‍👦", "❤️", "👍🏽", "🇨🇳", "e\u0301").map { Triple("A", it, 720) } +
            listOf(Triple("א", "👩‍👩‍👧‍👦", 720), Triple("A", "👩‍👩‍👧‍👦", 100))
        for ((prefix, cluster, width) in cases) {
            Fixture("$prefix${cluster}B", listOf(prefix, cluster, "B"), width = width, font = FontFamily.SansSerif).use { ui ->
                ui.line = ui.line.copy(lineEndTimeMs = 2_000L, segments = listOf(
                    EnhancedLyricsSegment(prefix, 0L, 200L),
                    EnhancedLyricsSegment(cluster, 200L, 800L),
                    EnhancedLyricsSegment("B", 1_500L, 2_000L),
                ))
                ui.positionMs = -1L
                val before = ui.image()
                ui.positionMs = 900L
                val painted = ui.image()
                val layout = ui.layout()
                val geometry = buildEnhancedLyricsGeometry(ui.line.text, listOf(prefix, cluster, "B"), layout)!!
                val clusterRect = geometry[1].fragments.single().rect
                val nextRect = geometry[2].fragments.single().rect
                if (layout.getLineForOffset(prefix.length) == layout.getLineForOffset(prefix.length + cluster.length)) {
                    assertTrue(clusterRect.intersect(nextRect).width <= 0.01f, "cluster must not include B: $cluster")
                }
                var redPixels = 0
                for (x in kotlin.math.ceil(nextRect.left).toInt() until nextRect.right.toInt()) {
                    for (y in nextRect.top.toInt().coerceAtLeast(0) until nextRect.bottom.toInt().coerceAtMost(painted.height)) {
                        val pixel = painted.getRGB(x, y)
                        // Fallback font line boxes may overlap in their leading. Compare B's
                        // actual glyph pixels, excluding any neighboring color-emoji pixels.
                        val original = before.getRGB(x, y)
                        if (original.red > 100 && original.green == 0) {
                            assertEquals(original, pixel, "B is not started for $cluster at $x,$y")
                            redPixels++
                        }
                    }
                }
                assertTrue(redPixels > 20, "B should remain visibly unsung for $cluster")
            }
        }
    }

    @Test
    fun `advancing time replays cached layers without entering the Skia layout path`() = onUiThread {
        Fixture("Test Word").use { ui ->
            ui.positionMs = 100L
            ui.image()
            val multiParagraph = ui.layout().multiParagraph
            val infos = multiParagraph.javaClass.getDeclaredField("paragraphInfoList")
                .apply { isAccessible = true }.get(multiParagraph) as List<*>
            val info = requireNotNull(infos.first())
            val paragraph = info.javaClass.getDeclaredField("paragraph").apply { isAccessible = true }.get(info)
            // The public Compose wrapper can stay identical while Skia re-layouts internally.
            // Leave a foreground update pending: any subsequent paragraph paint/layout would
            // consume it. Replaying the already recorded glyph layers must leave it untouched.
            val layouter = paragraph.javaClass.getDeclaredField("layouter").apply { isAccessible = true }.get(paragraph)
            val pending = layouter.javaClass.getDeclaredField("updateForeground").apply { isAccessible = true }
            val setColor = layouter.javaClass.declaredMethods.single { it.name.startsWith("setColor-") }
            setColor.invoke(layouter, Color(0xFF123456).value.toLong())
            assertTrue(pending.getBoolean(layouter))
            repeat(30) {
                ui.positionMs = 200L + it * 20L
                ui.image()
                assertTrue(pending.getBoolean(layouter), "time update must not repaint or re-layout the paragraph")
            }
            for (position in listOf(0L, 1_000L)) {
                ui.positionMs = position
                ui.image()
                assertTrue(pending.getBoolean(layouter), "endpoint drawing must also replay cached layers")
            }
        }
    }

    @Test
    fun `wrapped words fill the first visual line before the next`() = onUiThread {
        Fixture("one two three four", width = 180).use { ui ->
            val geometry = buildEnhancedLyricsGeometry(ui.line.text, listOf(ui.line.text), ui.layout())!!
            val fragments = geometry.single().fragments
            assertTrue(fragments.size > 1)
            assertTrue(fragments.zipWithNext().all { (a, b) -> a.rect.top < b.rect.top })
            val firstWidth = fragments.first().rect.width
            val total = fragments.sumOf { it.rect.width.toDouble() }.toFloat()
            val mask = buildLyricsFillMask(geometry, listOf(firstWidth / total / 2f), 14f)
            assertTrue(mask.edges.all { it.rect.top == fragments.first().rect.top })
            assertTrue(mask.solidRects.all { it.top == fragments.first().rect.top })
            ui.positionMs = 1_000L
            val painted = ui.image()
            ui.reference = true
            assertContentPixelsEqual(ui.image(), painted)
        }
    }

    @Test
    fun `rtl and mixed bidi use the actual run direction`() = onUiThread {
        Fixture("مرحبا بالعالم", direction = TextDirection.Rtl).use { ui ->
            val geometry = buildEnhancedLyricsGeometry(ui.line.text, listOf(ui.line.text), ui.layout())!!
            assertTrue(geometry.single().fragments.all { it.isRightToLeft })
            val mask = buildLyricsFillMask(geometry, listOf(0.5f), 14f)
            assertTrue(mask.solidRects.single().left >= mask.edges.single().rect.right)
            ui.positionMs = 500L
            val partial = ui.image()
            val rect = geometry.single().fragments.single().rect
            val middle = rect.center.x.toInt()
            val leftGreen = partial.greenIn(rect.left.toInt(), middle)
            val rightGreen = partial.greenIn(middle, rect.right.toInt())
            assertTrue(rightGreen > leftGreen)
            ui.capture("smooth-fill-rtl")
        }
        Fixture("Hi שלום friend").use { ui ->
            val fragments = buildEnhancedLyricsGeometry(ui.line.text, listOf(ui.line.text), ui.layout())!!.single().fragments
            assertEquals(listOf(false, true, false), fragments.map { it.isRightToLeft })
            val firstWidth = fragments[0].rect.width
            val totalWidth = fragments.sumOf { it.rect.width.toDouble() }.toFloat()
            val fraction = (firstWidth + fragments[1].rect.width / 2f) / totalWidth
            val mask = buildLyricsFillMask(listOf(LyricsSegmentGeometry(fragments)), listOf(fraction), 14f)
            assertEquals(fragments[1], mask.edges.single().fragment)
        }
    }

    @Test
    fun `directional isolate query failures fall back to the original fully highlighted line`() = onUiThread {
        val cases = listOf(
            listOf("\u2066ABC\u2069"),
            listOf("\u2067مرحبا\u2069"),
            listOf("\u2068中文\u2069"),
            listOf("A", "\u2067אב12\u2069", "C"),
        )
        for (segments in cases) {
            val text = segments.joinToString("")
            Fixture(text, segments, font = FontFamily.SansSerif).use { ui ->
                assertNull(buildEnhancedLyricsGeometry(text, segments, ui.layout()))
                ui.positionMs = 500L
                val painted = ui.image()
                assertEquals(listOf(text), ui.textNodes().map { it.config[SemanticsProperties.Text].single().text })

                // A failed layout must not leave the shared component stuck in fallback.
                val isolateLine = ui.line
                ui.line = EnhancedLyricsDisplayLine(
                    "Test Word", 0L, 1_000L, listOf(EnhancedLyricsSegment("Test Word", 0L, 1_000L)),
                )
                val recovered = ui.image()
                assertTrue(buildEnhancedLyricsGeometry(ui.line.text, listOf(ui.line.text), ui.layout()) != null)
                assertTrue(recovered.pixels().any { it.red > 100 })
                assertTrue(recovered.pixels().any { it.green > 100 })

                ui.line = isolateLine
                ui.reference = true
                assertContentPixelsEqual(ui.image(), painted)
            }
        }
    }

    @Test
    fun `invalid segmentation falls back to the original fully highlighted line`() = onUiThread {
        Fixture("Actual text", listOf("wrong text")).use { ui ->
            assertNull(buildEnhancedLyricsGeometry(ui.line.text, listOf("wrong text"), ui.layout()))
            ui.positionMs = 500L
            val painted = ui.image()
            ui.reference = true
            assertContentPixelsEqual(ui.image(), painted)
            assertFalse(ui.textNodes().any { it.config[SemanticsProperties.Text].single().text == "wrong text" })
        }
    }

    private fun assertContentPixelsEqual(expected: BufferedImage, actual: BufferedImage) {
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        assertTrue(expected.pixels().zip(actual.pixels()).all { (a, b) -> abs(a.red - b.red) <= 1 && abs(a.green - b.green) <= 1 }, "layers should share the exact original text layout")
    }

    private fun onUiThread(block: () -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private class Fixture(
        text: String,
        segments: List<String> = listOf(text),
        width: Int = 720,
        font: FontFamily = FontFamily.Monospace,
        direction: TextDirection = TextDirection.Content,
    ) : AutoCloseable {
        var line by mutableStateOf(EnhancedLyricsDisplayLine(text, 0L, 1_000L, segments.map { EnhancedLyricsSegment(it, 0L, 1_000L) }))
        var positionMs by mutableLongStateOf(0L)
        var activeColor by mutableStateOf(Color.Green)
        var fontSize by mutableStateOf(64)
        var fontFamily by mutableStateOf(font)
        var reference by mutableStateOf(false)
        var compositions = 0
        private var frame = 0L
        private val scene = ImageComposeScene(width, 600) {
            SideEffect { compositions++ }
            Box(Modifier.background(Color.Black)) {
                val style = TextStyle(fontFamily = fontFamily, fontSize = fontSize.sp, textDirection = direction)
                if (reference) {
                    Text(line.text, Modifier.fillMaxWidth(), color = activeColor, style = style, textAlign = TextAlign.Center)
                } else {
                    EnhancedLyricsLineText(
                        line, { positionMs }, activeColor, Color(0xFFCC0000), style,
                        Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    )
                }
            }
        }

        init { image() }

        fun resizeWidth(width: Int) {
            scene.constraints = Constraints.fixed(width, 600)
            image()
        }

        fun image(): BufferedImage {
            repeat(3) {
                Snapshot.sendApplyNotifications()
                scene.render(++frame * 16_666_667L).close()
            }
            return scene.render(++frame * 16_666_667L).use { image ->
                image.encodeToData()!!.use { ImageIO.read(it.bytes.inputStream()) }
            }
        }

        fun textNodes(): List<SemanticsNode> {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }.filter { it.config.contains(SemanticsProperties.Text) }
        }

        fun layout(): TextLayoutResult {
            val layouts = mutableListOf<TextLayoutResult>()
            textNodes().single().config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!(layouts)
            return layouts.single()
        }

        fun capture(name: String) {
            val output = System.getenv("LYN_LYRICS_QA_OUTPUT") ?: return
            val file = File(output, "$name.png")
            file.parentFile.mkdirs()
            ImageIO.write(image(), "png", file)
        }

        override fun close() = scene.close()
    }
}

private val Int.red: Int get() = (this shr 16) and 255
private val Int.green: Int get() = (this shr 8) and 255
private val Int.blue: Int get() = this and 255
private fun BufferedImage.pixels(): List<Int> = getRGB(0, 0, width, height, null, 0, width).toList()
private fun BufferedImage.greenIn(start: Int, end: Int): Long =
    (start.coerceAtLeast(0) until end.coerceAtMost(width)).sumOf { x -> (0 until height).sumOf { y -> getRGB(x, y).green.toLong() } }
