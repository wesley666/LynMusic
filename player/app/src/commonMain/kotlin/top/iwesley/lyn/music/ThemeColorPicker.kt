package top.iwesley.lyn.music

import top.iwesley.lyn.music.resources.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import top.iwesley.lyn.music.core.model.formatThemeHexColor
import top.iwesley.lyn.music.ui.mainShellColors

internal data class ThemePickerHsv(
    val hue: Float,
    val saturation: Float,
    val value: Float,
)

internal data class ThemePickerPanelSelection(
    val saturation: Float,
    val value: Float,
)

internal data class ThemePickerCoordinates(
    val x: Float,
    val y: Float,
)

private const val THEME_PICKER_SIZE_FRACTION = 0.67f

@Composable
internal fun ThemeColorPickerDialog(
    label: String,
    initialArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val shellColors = mainShellColors
    var hsv by remember(initialArgb) { mutableStateOf(argbToThemePickerHsv(initialArgb)) }
    val previewArgb = remember(hsv) { themePickerHsvToArgb(hsv) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = shellColors.navContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(28.dp),
        title = { Text(uiString(Res.string.common_select_named_item, label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = uiString(Res.string.theme_color_picker_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ThemeColorPreviewSwatch(
                        argb = previewArgb,
                        modifier = Modifier.size(36.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = formatThemeHexColor(previewArgb),
                            fontFamily = FontFamily.Monospace,
                            color = mainShellColors.secondaryText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                ThemeSaturationValuePanel(
                    hue = hsv.hue,
                    saturation = hsv.saturation,
                    value = hsv.value,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth(THEME_PICKER_SIZE_FRACTION),
                    onSelectionChange = { selection ->
                        hsv = hsv.copy(
                            saturation = selection.saturation,
                            value = selection.value,
                        )
                    },
                )
                ThemeHueSlider(
                    hue = hsv.hue,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth(THEME_PICKER_SIZE_FRACTION),
                    onHueChange = { hue -> hsv = hsv.copy(hue = hue) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(previewArgb) }) {
                Text(uiString(Res.string.common_confirm), color = MaterialTheme.colorScheme.onSurface)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(uiString(Res.string.common_cancel), color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}

@Composable
private fun ThemeSaturationValuePanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onSelectionChange: (ThemePickerPanelSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val hueColor = remember(hue) { Color(themePickerHueColorArgb(hue)) }

    fun updateSelection(position: Offset) {
        onSelectionChange(
            themePickerPanelSelection(
                x = position.x,
                y = position.y,
                width = panelSize.width.toFloat(),
                height = panelSize.height.toFloat(),
            ),
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .border(1.dp, mainShellColors.cardBorder, shape)
            .onSizeChanged { panelSize = it }
            .pointerInput(panelSize) {
                detectTapGestures { offset -> updateSelection(offset) }
            }
            .pointerInput(panelSize) {
                detectDragGestures(
                    onDragStart = { offset -> updateSelection(offset) },
                    onDrag = { change, _ ->
                        change.consume()
                        updateSelection(change.position)
                    },
                )
            },
    ) {
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color.White, hueColor)),
            cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
            cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
        )
        val position = themePickerPanelCoordinates(
            saturation = saturation,
            value = value,
            width = size.width,
            height = size.height,
        )
        val center = Offset(position.x, position.y)
        drawCircle(
            color = Color.White,
            radius = 8.dp.toPx(),
            center = center,
            style = Stroke(width = 2.5.dp.toPx()),
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.36f),
            radius = 11.dp.toPx(),
            center = center,
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}

@Composable
private fun ThemeHueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    val gradientColors = remember {
        listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color(themePickerHueColorArgb(it)) }
    }

    fun updateHue(x: Float) {
        val width = trackSize.width.toFloat()
        if (width <= 0f) return
        onHueChange(themePickerHueFromSliderFraction(x / width))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(shape)
            .border(1.dp, mainShellColors.cardBorder, shape)
            .onSizeChanged { trackSize = it }
            .pointerInput(trackSize) {
                detectTapGestures { offset -> updateHue(offset.x) }
            }
            .pointerInput(trackSize) {
                detectDragGestures(
                    onDragStart = { offset -> updateHue(offset.x) },
                    onDrag = { change, _ ->
                        change.consume()
                        updateHue(change.position.x)
                    },
                )
            },
    ) {
        drawRoundRect(
            brush = Brush.horizontalGradient(gradientColors),
            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
        )
        val thumbCenter = Offset(
            x = size.width * themePickerSliderFractionFromHue(hue),
            y = size.height / 2f,
        )
        drawCircle(
            color = Color.White,
            radius = 7.dp.toPx(),
            center = thumbCenter,
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.2f),
            radius = 7.dp.toPx(),
            center = thumbCenter,
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}

@Composable
private fun ThemeColorPreviewSwatch(
    argb: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(argb))
            .border(1.dp, mainShellColors.cardBorder, RoundedCornerShape(16.dp)),
    )
}

internal fun argbToThemePickerHsv(argb: Int): ThemePickerHsv {
    val red = ((argb ushr 16) and 0xFF) / 255f
    val green = ((argb ushr 8) and 0xFF) / 255f
    val blue = (argb and 0xFF) / 255f
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == red -> {
            val segment = (green - blue) / delta
            60f * if (segment < 0f) segment + 6f else segment
        }
        max == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }
    val saturation = if (max == 0f) 0f else delta / max
    return ThemePickerHsv(
        hue = if (hue.isNaN()) 0f else hue,
        saturation = saturation,
        value = max,
    )
}

internal fun themePickerHsvToArgb(hsv: ThemePickerHsv): Int {
    val hue = normalizeHue(hsv.hue)
    val saturation = hsv.saturation.coerceIn(0f, 1f)
    val value = hsv.value.coerceIn(0f, 1f)
    if (saturation == 0f) {
        val channel = (value * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel shl 16) or (channel shl 8) or channel
    }
    val chroma = value * saturation
    val hueSection = hue / 60f
    val secondary = chroma * (1f - abs((hueSection % 2f) - 1f))
    val match = value - chroma
    val (redPrime, greenPrime, bluePrime) = when {
        hueSection < 1f -> Triple(chroma, secondary, 0f)
        hueSection < 2f -> Triple(secondary, chroma, 0f)
        hueSection < 3f -> Triple(0f, chroma, secondary)
        hueSection < 4f -> Triple(0f, secondary, chroma)
        hueSection < 5f -> Triple(secondary, 0f, chroma)
        else -> Triple(chroma, 0f, secondary)
    }
    val red = ((redPrime + match) * 255f).roundToInt().coerceIn(0, 255)
    val green = ((greenPrime + match) * 255f).roundToInt().coerceIn(0, 255)
    val blue = ((bluePrime + match) * 255f).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}

internal fun themePickerHueFromSliderFraction(fraction: Float): Float {
    return fraction.coerceIn(0f, 1f) * 360f
}

internal fun themePickerSliderFractionFromHue(hue: Float): Float {
    return when {
        hue.isNaN() -> 0f
        hue <= 0f -> 0f
        hue >= 360f -> 1f
        else -> hue / 360f
    }
}

internal fun themePickerPanelSelection(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
): ThemePickerPanelSelection {
    val safeWidth = width.coerceAtLeast(1f)
    val safeHeight = height.coerceAtLeast(1f)
    return ThemePickerPanelSelection(
        saturation = (x / safeWidth).coerceIn(0f, 1f),
        value = 1f - (y / safeHeight).coerceIn(0f, 1f),
    )
}

internal fun themePickerPanelCoordinates(
    saturation: Float,
    value: Float,
    width: Float,
    height: Float,
): ThemePickerCoordinates {
    val safeWidth = width.coerceAtLeast(1f) - 1f
    val safeHeight = height.coerceAtLeast(1f) - 1f
    return ThemePickerCoordinates(
        x = saturation.coerceIn(0f, 1f) * safeWidth,
        y = (1f - value.coerceIn(0f, 1f)) * safeHeight,
    )
}

internal fun themePickerHueColorArgb(hue: Float): Int {
    return themePickerHsvToArgb(
        ThemePickerHsv(
            hue = hue,
            saturation = 1f,
            value = 1f,
        ),
    )
}

private fun normalizeHue(hue: Float): Float {
    if (hue.isNaN() || hue.isInfinite()) return 0f
    val normalized = hue % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}
