package top.iwesley.lyn.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** Only the player is recorded. Dialogs/popups have separate roots and never enter this layer. */
@Stable
class PlayerLyricsBackdrop(val layer: GraphicsLayer) {
    var coordinates: LayoutCoordinates? by mutableStateOf(null)
    var frameVersion by mutableIntStateOf(0)
        private set
    private var nextFrame = 0

    fun recorded() {
        // Do not read frameVersion here: the source must not observe its own invalidation signal.
        frameVersion = ++nextFrame
    }
}

@Composable
fun rememberPlayerLyricsBackdrop(enabled: Boolean): PlayerLyricsBackdrop? {
    val supported = remember { BlurEffect(1f, 1f).isSupported() }
    if (!enabled || !supported) return null
    val layer = rememberGraphicsLayer()
    return remember(layer) { PlayerLyricsBackdrop(layer) }
}

fun Modifier.playerLyricsBackdropSource(backdrop: PlayerLyricsBackdrop?): Modifier {
    if (backdrop == null) return this
    return onGloballyPositioned { backdrop.coordinates = it }.drawWithContent {
        backdrop.layer.record {
            this@drawWithContent.drawContent()
        }
        drawLayer(backdrop.layer)
        backdrop.recorded()
    }
}

/** Coordinates are in physical screen pixels, including different dialog/popup window origins. */
fun lyricsGlassSourceOffset(source: Offset, destination: Offset): Offset? =
    if (source.isSpecified && destination.isSpecified) source - destination else null

@Composable
fun LyricsGlassPanel(
    backdrop: PlayerLyricsBackdrop?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(32.dp)
    Box(modifier.clip(shape).border(1.dp, Color.White.copy(alpha = 0.25f), shape)) {
        if (backdrop == null) {
            Box(Modifier.matchParentSize().background(Color(0xFF43434C).copy(alpha = 0.88f)))
        } else {
            LyricsGlassBackground(backdrop, Modifier.matchParentSize())
        }
        content()
    }
}

@Composable
private fun LyricsGlassBackground(backdrop: PlayerLyricsBackdrop, modifier: Modifier) {
    val blurredLayer = rememberGraphicsLayer()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(modifier.onGloballyPositioned { coordinates = it }.drawWithContent {
        val sourceCoordinates = backdrop.coordinates
        val destinationCoordinates = coordinates
        // Reading in the draw phase refreshes every window when the player redraws, without
        // recomposing or measuring either the lyrics or the settings content.
        val ready = backdrop.frameVersion > 0 && !backdrop.layer.isReleased &&
            sourceCoordinates?.isAttached == true && destinationCoordinates?.isAttached == true
        val offset = if (ready) lyricsGlassSourceOffset(
            sourceCoordinates.localToScreen(Offset.Zero),
            destinationCoordinates.localToScreen(Offset.Zero),
        ) else null
        if (offset != null) {
            val radius = 20.dp.toPx()
            // Sample beyond the panel before clipping, so blur does not create a dark rim.
            val padding = ceil(radius * 2).toInt()
            blurredLayer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
            blurredLayer.record(this, layoutDirection, IntSize(
                size.width.toInt() + padding * 2,
                size.height.toInt() + padding * 2,
            )) {
                translate(offset.x + padding, offset.y + padding) { drawLayer(backdrop.layer) }
            }
            translate(-padding.toFloat(), -padding.toFloat()) { drawLayer(blurredLayer) }
            drawRect(Color(0xFF777782).copy(alpha = 0.55f))
        } else {
            drawRect(Color(0xFF43434C).copy(alpha = 0.88f))
        }
        drawContent()
    })
}
