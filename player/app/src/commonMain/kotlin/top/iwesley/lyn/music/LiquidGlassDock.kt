/*
 * Glass treatment and deformation inspired by Kyant0's LiquidBottomTabs (Apache-2.0).
 * https://github.com/Kyant0/AndroidLiquidGlass/blob/2.0.0/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt
 * Adapted for LynMusic: theme colors, capability tiers, accessible actions and release-only navigation.
 * See third_party/AndroidLiquidGlass-LICENSE.txt.
 */
package top.iwesley.lyn.music

import top.iwesley.lyn.music.resources.*

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.roundToInt

internal val LocalDockBottomInset = staticCompositionLocalOf { 0.dp }

/** Applied inside scrolling content, not to the viewport, so artwork can pass behind the Dock. */
@Composable
internal fun dockContentPadding(base: PaddingValues): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = base.calculateStartPadding(direction),
        top = base.calculateTopPadding(),
        end = base.calculateEndPadding(direction),
        bottom = base.calculateBottomPadding() + LocalDockBottomInset.current,
    )
}

internal enum class DockGlassLevel { Full, Blur, Translucent }

internal fun dockGlassLevel(): DockGlassLevel = when {
    isRuntimeShaderSupported() -> DockGlassLevel.Full
    isRenderEffectSupported() -> DockGlassLevel.Blur
    else -> DockGlassLevel.Translucent
}

/** Only pointer releases and semantic clicks invoke onSelect; animation never navigates. */
@Composable
internal fun LiquidGlassDock(
    selectedIndex: Int,
    backdrop: Backdrop,
    showUpdateBadge: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    level: DockGlassLevel = dockGlassLevel(),
) {
    val colors = MaterialTheme.colorScheme
    val light = colors.surface.luminance() > 0.5f
    val surface = colors.surface.copy(alpha = if (level == DockGlassLevel.Translucent) 0.94f else 0.4f)
    val shape = RoundedCornerShape(percent = 50)
    val direction = LocalLayoutDirection.current
    val density = LocalDensity.current
    val isLtr = direction == LayoutDirection.Ltr
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val select by rememberUpdatedState(onSelect)
    val currentSelection by rememberUpdatedState(selectedIndex)
    var dragging by remember { mutableStateOf(false) }
    var dragIndex by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var stretch by remember { mutableFloatStateOf(0f) }
    val pressProgress by animateFloatAsState(
        if (enabled && (pressed || dragging)) 1f else 0f,
        spring(dampingRatio = 0.7f, stiffness = 400f),
    )
    val position by animateFloatAsState(
        if (dragging) dragIndex else selectedIndex.toFloat(),
        if (dragging) snap() else spring(dampingRatio = 0.8f, stiffness = 500f),
    )
    val deformation by animateFloatAsState(
        if (dragging) stretch else 0f,
        spring(dampingRatio = 0.8f, stiffness = 500f),
    )
    LaunchedEffect(selectedIndex, enabled) {
        dragging = false
        stretch = 0f
    }

    BoxWithConstraints(modifier.height(64.dp).selectableGroup()) {
        val tabWidth = (maxWidth - 8.dp) / 4
        val insetPx = with(density) { 4.dp.toPx() }
        val tabWidthPx = (constraints.maxWidth - 2f * insetPx).coerceAtLeast(1f) / 4f
        fun indexAt(x: Float): Float {
            val physical = ((x - insetPx) / tabWidthPx - 0.5f).coerceIn(0f, 3f)
            return if (isLtr) physical else 3f - physical
        }
        // Resizing, disabling or external navigation cancels any in-flight drag.
        DisposableEffect(constraints.maxWidth, density.density, direction, selectedIndex, enabled) {
            onDispose { dragging = false; stretch = 0f }
        }
        val gesture = Modifier.pointerInput(constraints.maxWidth, density.density, direction, selectedIndex, enabled) {
            if (enabled) detectHorizontalDragGestures(
                onDragStart = { offset ->
                    dragIndex = indexAt(offset.x)
                    dragging = true
                },
                onDragEnd = {
                    val target = dragIndex.roundToInt().coerceIn(0, 3)
                    dragging = false
                    stretch = 0f
                    select(target)
                },
                onDragCancel = { dragging = false; stretch = 0f },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    dragIndex = indexAt(change.position.x)
                    stretch = (amount / tabWidthPx).coerceIn(-0.2f, 0.2f)
                },
            )
        }
        val panel = if (level == DockGlassLevel.Translucent) {
            Modifier.graphicsLayer {
                val scale = 1f + 16.dp.toPx() / size.width * pressProgress
                scaleX = scale; scaleY = scale
            }.shadow(8.dp, shape).background(surface, shape)
                .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), shape)
        } else {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(4.dp.toPx())
                    if (level == DockGlassLevel.Full) lens(32.dp.toPx(), 36.dp.toPx())
                },
                layerBlock = {
                    val scale = 1f + 16.dp.toPx() / size.width * pressProgress
                    scaleX = scale; scaleY = scale
                },
                onDrawSurface = { drawRect(surface) },
            )
        }
        Box(Modifier.fillMaxSize().then(gesture)) {
            Box(Modifier.fillMaxSize().then(panel))
            val lensSurface = (if (light) Color.Black else Color.White).copy(alpha = 0.1f)
            val selection = if (level == DockGlassLevel.Translucent) {
                Modifier.graphicsLayer {
                    scaleX = (1f + 0.25f * pressProgress) / (1f - deformation * 0.75f)
                    scaleY = (1f + 0.39f * pressProgress) * (1f - deformation * 0.25f)
                }.background(lensSurface, shape)
            } else {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        blur(4.dp.toPx())
                        if (level == DockGlassLevel.Full) {
                            lens((12f + 4f * pressProgress).dp.toPx(), (16f + 6f * pressProgress).dp.toPx(), chromaticAberration = true)
                        }
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.5f + 0.5f * pressProgress) },
                    shadow = { Shadow(alpha = 0.15f * pressProgress) },
                    innerShadow = { InnerShadow(radius = 8.dp, alpha = 0.15f * pressProgress) },
                    layerBlock = {
                        scaleX = (1f + 0.25f * pressProgress) / (1f - deformation * 0.75f)
                        scaleY = (1f + 0.39f * pressProgress) * (1f - deformation * 0.25f)
                    },
                    onDrawSurface = { drawRect(lensSurface) },
                )
            }
            Box(
                Modifier.align(Alignment.CenterStart)
                    .padding(start = 4.dp)
                    .graphicsLayer { translationX = (if (isLtr) 1f else -1f) * position * tabWidthPx }
                    .width(tabWidth).height(56.dp).then(selection),
            )
            val icons = listOf(Icons.Rounded.LibraryMusic, Icons.Rounded.FavoriteBorder, Icons.Rounded.Person, Icons.Rounded.MoreHoriz)
            val labels = listOf(uiString(Res.string.library_title), uiString(Res.string.favorites_collection_title), uiString(Res.string.navigation_my_music), uiString(Res.string.common_more))
            Row(Modifier.fillMaxSize().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                icons.forEachIndexed { index, icon ->
                    Box(
                        modifier = Modifier.weight(1f).height(56.dp)
                            .selectable(
                                selected = selectedIndex == index,
                                enabled = enabled,
                                role = Role.Tab,
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { select(index) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.material3.LocalContentColor provides
                                if ((if (dragging) dragIndex.roundToInt() else currentSelection) == index) colors.primary
                                else colors.onSurfaceVariant,
                        ) {
                            BadgedIcon(
                                imageVector = icon,
                                contentDescription = labels[index],
                                showBadge = index == 3 && showUpdateBadge,
                                modifier = Modifier.size(28.dp).graphicsLayer {
                                    scaleX = 1f + 0.2f * pressProgress
                                    scaleY = scaleX
                                },
                                iconModifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
