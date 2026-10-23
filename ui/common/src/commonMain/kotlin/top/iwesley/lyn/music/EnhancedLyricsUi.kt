package top.iwesley.lyn.music

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.domain.EnhancedLyricsDisplayLine

@Composable
fun EnhancedLyricsLineText(
    line: EnhancedLyricsDisplayLine,
    currentPositionMs: () -> Long,
    activeColor: Color,
    inactiveColor: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    fontWeight: FontWeight? = null,
) {
    val layoutCache = remember { EnhancedLyricsLayoutCache() }
    val sungLayer = rememberGraphicsLayer()
    val unsungLayer = rememberGraphicsLayer()
    val segmentTexts = remember(line.segments) { line.segments.map { it.text } }
    val position by rememberUpdatedState(currentPositionMs)
    val sungColor by rememberUpdatedState(activeColor)
    val unsungColor by rememberUpdatedState(inactiveColor)
    Text(
        text = line.text,
        modifier = modifier.drawWithCache {
            val layout = layoutCache.layout
            val geometry = layoutCache.geometry(line.text, segmentTexts)
            val edgeWidth = 14.dp.toPx()
            val overlap = 1.dp.toPx()
            val solidPath = Path()
            val layerPaint = Paint()
            val maskPaint = Paint().apply { blendMode = BlendMode.DstIn }
            if (layout != null) {
                // Record colors before the frame callback. Skia relayouts a paragraph when its
                // foreground changes, even when the Compose TextLayoutResult stays the same.
                val cachedSungColor = sungColor
                val cachedUnsungColor = unsungColor
                unsungLayer.record(this, layoutDirection, layout.size) { drawText(layout, cachedUnsungColor) }
                sungLayer.record(this, layoutDirection, layout.size) { drawText(layout, cachedSungColor) }
            }
            onDrawWithContent {
                if (layout == null) {
                    val canvas = drawContext.canvas
                    val fallbackPaint = Paint().apply { colorFilter = ColorFilter.tint(sungColor) }
                    canvas.saveLayer(Rect(Offset.Zero, size), fallbackPaint)
                    drawContent()
                    canvas.restore()
                    return@onDrawWithContent
                }
                if (geometry == null) {
                    drawLayer(sungLayer)
                    return@onDrawWithContent
                }
                // Position is read only by drawing. Frames never rebuild text or measure it.
                val fractions = resolveEnhancedLyricsSegmentFillFractions(line, position())
                if (fractions.all { it >= 1f }) {
                    drawLayer(sungLayer)
                    return@onDrawWithContent
                }
                val mask = buildLyricsFillMask(geometry, fractions, edgeWidth)
                solidPath.reset()
                mask.solidRects.forEach(solidPath::addRect)
                clipPath(solidPath, ClipOp.Difference) { drawLayer(unsungLayer) }
                if (mask.solidRects.isEmpty() && mask.edges.isEmpty()) return@onDrawWithContent

                val canvas = drawContext.canvas
                val bounds = Rect(Offset.Zero, size)
                // Build one alpha mask so overlapping clusters/soft edges never double-tint text.
                canvas.saveLayer(bounds, layerPaint)
                drawLayer(sungLayer)
                canvas.saveLayer(bounds, maskPaint)
                drawPath(solidPath, Color.White)
                mask.edges.forEach { edge ->
                    val rtl = edge.fragment.isRightToLeft
                    val rect = edge.rect
                    val overlapped = if (rtl) {
                        rect.copy(right = (rect.right + overlap).coerceAtMost(edge.fragment.rect.right))
                    } else {
                        rect.copy(left = (rect.left - overlap).coerceAtLeast(edge.fragment.rect.left))
                    }
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = if (rtl) listOf(Color.Transparent, Color.White) else listOf(Color.White, Color.Transparent),
                            startX = rect.left,
                            endX = rect.right,
                        ),
                        topLeft = overlapped.topLeft,
                        size = overlapped.size,
                    )
                }
                canvas.restore()
                canvas.restore()
            }
        },
        style = style,
        // Paint colors belong to drawing, so theme/highlight changes cannot invalidate shaping.
        color = Color.White,
        textAlign = textAlign,
        fontWeight = fontWeight,
        onTextLayout = { layoutCache.layout = it },
    )
}

private class EnhancedLyricsLayoutCache {
    var layout by mutableStateOf<TextLayoutResult?>(null)
    private var cachedLayout: TextLayoutResult? = null
    private var cachedSegments: List<String>? = null
    private var cachedGeometry: List<LyricsSegmentGeometry>? = null

    fun geometry(text: String, segments: List<String>): List<LyricsSegmentGeometry>? {
        val currentLayout = layout ?: return null
        if (currentLayout.multiParagraph !== cachedLayout?.multiParagraph || cachedSegments != segments) {
            cachedLayout = currentLayout
            cachedSegments = segments
            cachedGeometry = buildEnhancedLyricsGeometry(text, segments, currentLayout)
        }
        return cachedGeometry
    }
}

internal fun resolveEnhancedLyricsSegmentFillFractions(
    line: EnhancedLyricsDisplayLine,
    currentPositionMs: Long,
): List<Float> {
    return line.segments.map { segment ->
        val endTimeMs = segment.endTimeMs
        when {
            currentPositionMs < segment.startTimeMs -> 0f
            endTimeMs != null && endTimeMs > segment.startTimeMs -> {
                ((currentPositionMs - segment.startTimeMs).toFloat() /
                    (endTimeMs - segment.startTimeMs).toFloat()).coerceIn(0f, 1f)
            }

            else -> 1f
        }
    }
}
