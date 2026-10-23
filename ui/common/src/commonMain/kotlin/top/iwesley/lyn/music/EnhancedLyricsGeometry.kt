package top.iwesley.lyn.music

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathSegment
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import kotlin.math.max
import kotlin.math.min

internal data class LyricsFillFragment(val rect: Rect, val isRightToLeft: Boolean)

internal data class LyricsSegmentGeometry(val fragments: List<LyricsFillFragment>)

internal data class LyricsFillEdge(val rect: Rect, val fragment: LyricsFillFragment)

internal data class LyricsFillMask(
    val solidRects: List<Rect>,
    val edges: List<LyricsFillEdge>,
)

/** Logical offsets come from the original line; shaping and wrapping belong to the text layout. */
internal fun buildEnhancedLyricsGeometry(
    text: String,
    segmentTexts: List<String>,
    layout: TextLayoutResult,
): List<LyricsSegmentGeometry>? {
    if (text.isEmpty() || segmentTexts.isEmpty() || segmentTexts.joinToString("") != text ||
        layout.layoutInput.text.text != text
    ) return null

    var offset = 0
    return segmentTexts.map { segment ->
        val fragments = mutableListOf<LyricsFillFragment>()
        val end = offset + segment.length
        if (offset < end) {
            for (lineIndex in layout.getLineForOffset(offset)..layout.getLineForOffset(end - 1)) {
                val rangeStart = max(offset, layout.getLineStart(lineIndex))
                val rangeEnd = min(end, layout.getLineEnd(lineIndex))
                if (rangeStart >= rangeEnd) continue
                // Selection paths respect complete glyph clusters. getBoundingBox on a UTF-16
                // continuation can instead return the *next* glyph on the Skia backend.
                val rects = layout.getPathForRange(rangeStart, rangeEnd).selectionRects() ?: return null
                val runs = rects.mapNotNull { bounds ->
                    val left = max(bounds.left, layout.getLineLeft(lineIndex))
                    val right = min(bounds.right, layout.getLineRight(lineIndex))
                    if (right <= left || bounds.height <= 0f) return@mapNotNull null
                    val rect = Rect(left, layout.getLineTop(lineIndex), right, layout.getLineBottom(lineIndex))
                    val inset = min(1f, rect.width / 4f)
                    val firstOffset = try {
                        min(
                            layout.getOffsetForPosition(Offset(rect.left + inset, rect.center.y)),
                            layout.getOffsetForPosition(Offset(rect.right - inset, rect.center.y)),
                        ).coerceIn(rangeStart, rangeEnd - 1)
                    } catch (_: IllegalArgumentException) {
                        // JVM hit testing can reject Unicode directional isolates. Let the
                        // caller draw the original line instead of failing during drawing.
                        return null
                    }
                    firstOffset to LyricsFillFragment(
                        rect,
                        layout.getBidiRunDirection(firstOffset) == ResolvedTextDirection.Rtl,
                    )
                }.sortedBy { it.first }
                // A selection can contain separate font and bidi runs on the same visual line.
                // Merge only touching runs with the same direction, in logical reading order.
                for ((_, fragment) in runs) {
                    val previous = fragments.lastOrNull()
                    if (previous != null && previous.rect.top == fragment.rect.top &&
                        previous.isRightToLeft == fragment.isRightToLeft &&
                        fragment.rect.left <= previous.rect.right + 0.5f &&
                        fragment.rect.right >= previous.rect.left - 0.5f
                    ) {
                        fragments[fragments.lastIndex] = previous.copy(rect = Rect(
                            min(previous.rect.left, fragment.rect.left), previous.rect.top,
                            max(previous.rect.right, fragment.rect.right), previous.rect.bottom,
                        ))
                    } else {
                        fragments += fragment
                    }
                }
            }
        }
        if (fragments.isEmpty() && segment.any { !it.isWhitespace() }) return null
        offset = end
        LyricsSegmentGeometry(fragments)
    }.takeIf { words -> words.any { it.fragments.isNotEmpty() } }
}

/** Both Compose backends expose text selections as closed, rectangular contours. */
private fun Path.selectionRects(): List<Rect>? {
    val rects = mutableListOf<Rect>()
    var bounds: Rect? = null
    for (segment in this) {
        val points = segment.points
        when (segment.type) {
            PathSegment.Type.Move -> {
                if (bounds != null) return null
                bounds = Rect(points[0], points[1], points[0], points[1])
            }
            PathSegment.Type.Line -> {
                val current = bounds ?: return null
                if (points[0] != points[2] && points[1] != points[3]) return null
                bounds = Rect(
                    min(current.left, points[2]), min(current.top, points[3]),
                    max(current.right, points[2]), max(current.bottom, points[3]),
                )
            }
            PathSegment.Type.Close -> {
                bounds?.let { rects += it }
                bounds = null
            }
            PathSegment.Type.Done -> Unit
            else -> return null
        }
        if (points.any { !it.isFinite() }) return null
    }
    return rects.takeIf { bounds == null }
}

internal fun buildLyricsFillMask(
    geometry: List<LyricsSegmentGeometry>,
    fractions: List<Float>,
    edgeWidthPx: Float,
): LyricsFillMask {
    val solids = mutableListOf<Rect>()
    val edges = mutableListOf<LyricsFillEdge>()
    geometry.forEachIndexed { index, word ->
        val fraction = fractions.getOrElse(index) { 0f }.coerceIn(0f, 1f)
        if (fraction <= 0f) return@forEachIndexed
        if (fraction >= 1f) {
            solids += word.fragments.map { it.rect }
            return@forEachIndexed
        }
        var remaining = word.fragments.sumOf { it.rect.width.toDouble() }.toFloat() * fraction
        for (fragment in word.fragments) {
            if (remaining <= 0f) break
            val rect = fragment.rect
            if (remaining >= rect.width) {
                solids += rect
                remaining -= rect.width
                continue
            }
            val boundary = if (fragment.isRightToLeft) rect.right - remaining else rect.left + remaining
            val halfEdge = min(edgeWidthPx.coerceAtLeast(0f), rect.width * 0.6f) / 2f
            val edgeStart = (boundary - halfEdge).coerceAtLeast(rect.left)
            val edgeEnd = (boundary + halfEdge).coerceAtMost(rect.right)
            val solid = if (fragment.isRightToLeft) {
                Rect(edgeEnd, rect.top, rect.right, rect.bottom)
            } else {
                Rect(rect.left, rect.top, edgeStart, rect.bottom)
            }
            if (solid.width > 0f) solids += solid
            if (edgeEnd > edgeStart) edges += LyricsFillEdge(Rect(edgeStart, rect.top, edgeEnd, rect.bottom), fragment)
            break
        }
    }
    return LyricsFillMask(solids, edges)
}
