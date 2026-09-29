package top.iwesley.lyn.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens

internal data class MiniPlayerGlassStyle(
    val backdrop: Backdrop,
    val level: DockGlassLevel,
    val surface: Color,
    val content: Color,
    val secondaryContent: Color,
    val border: Color,
    val controlContent: Color,
    val usePlainPlaybackIcon: Boolean,
)

/** Read the application's palette before entering the legacy mini player's fixed theme. */
@Composable
internal fun miniPlayerGlassStyle(backdrop: Backdrop): MiniPlayerGlassStyle {
    val colors = MaterialTheme.colorScheme
    val isLight = colors.surface.luminance() > 0.5f
    return MiniPlayerGlassStyle(
        backdrop = backdrop,
        level = dockGlassLevel(),
        surface = colors.surface.copy(alpha = if (isLight) 0.72f else 0.65f),
        content = colors.onSurface,
        secondaryContent = colors.onSurfaceVariant,
        border = colors.outlineVariant.copy(alpha = 0.5f),
        controlContent = Color(0xFFE03131),
        usePlainPlaybackIcon = true,
    )
}

/** Draw only the background; clipping the foreground after this modifier preserves the shadow. */
internal fun Modifier.miniPlayerGlassBackground(
    style: MiniPlayerGlassStyle,
    shape: RoundedCornerShape,
): Modifier = when (style.level) {
    DockGlassLevel.Translucent -> shadow(8.dp, shape, clip = false)
        .background(style.surface, shape)
        .border(1.dp, style.border, shape)
    else -> drawBackdrop(
        backdrop = style.backdrop,
        shape = { shape },
        effects = {
            blur(8.dp.toPx())
            if (style.level == DockGlassLevel.Full) {
                lens(16.dp.toPx(), 16.dp.toPx(), chromaticAberration = false)
            }
        },
        onDrawSurface = { drawRect(style.surface) },
    )
}
