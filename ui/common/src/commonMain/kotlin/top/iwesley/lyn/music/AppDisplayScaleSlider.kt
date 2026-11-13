package top.iwesley.lyn.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.ui.mainShellColors
import kotlin.math.roundToInt

@Composable
fun AppDisplayScaleSlider(
    preset: AppDisplayScalePreset,
    onSelect: (AppDisplayScalePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sliderState = remember { AppDisplayScaleSliderState(preset) }
    LaunchedEffect(preset) { sliderState.synchronize(preset) }
    val previewPreset = sliderState.previewPreset
    val valueText = uiString(
        Res.string.display_scale_value,
        displayScalePresetLabel(previewPreset),
        displayScalePercentage(previewPreset),
    )
    val sliderLabel = uiString(Res.string.display_scale_title)
    val colors = mainShellColors
    val primaryColor = MaterialTheme.colorScheme.primary
    val sliderColors = SliderDefaults.colors(
        thumbColor = primaryColor,
        activeTrackColor = primaryColor,
        inactiveTrackColor = colors.cardBorder,
        activeTickColor = colors.cardContainer,
        inactiveTickColor = colors.secondaryText.copy(alpha = 0.35f),
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Slider(
            value = sliderState.previewPosition,
            onValueChange = sliderState::preview,
            onValueChangeFinished = { sliderState.finishSelection()?.let(onSelect) },
            valueRange = 0f..AppDisplayScalePreset.entries.lastIndex.toFloat(),
            steps = AppDisplayScalePreset.entries.size - 2,
            colors = sliderColors,
            thumb = {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(colors.cardContainer, CircleShape)
                        .border(2.dp, primaryColor, CircleShape),
                )
            },
            track = { materialSliderState ->
                SliderDefaults.Track(
                    sliderState = materialSliderState,
                    modifier = Modifier.height(4.dp),
                    colors = sliderColors,
                    thumbTrackGapSize = 0.dp,
                    trackInsideCornerSize = 2.dp,
                    drawStopIndicator = null,
                    drawTick = { offset, color ->
                        drawCircle(color = color, radius = 1.5.dp.toPx(), center = offset)
                    },
                )
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                contentDescription = sliderLabel
                stateDescription = valueText
            },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppDisplayScalePreset.entries.forEach { option ->
                val selected = option == previewPreset
                Text(
                    text = displayScalePercentage(option),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) primaryColor else colors.secondaryText,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

internal class AppDisplayScaleSliderState(initialPreset: AppDisplayScalePreset) {
    var selectedPreset: AppDisplayScalePreset = initialPreset
        private set

    var previewPosition: Float by mutableFloatStateOf(initialPreset.ordinal.toFloat())
        private set

    val previewPreset: AppDisplayScalePreset
        get() = AppDisplayScalePreset.entries[previewPosition.roundToInt()]

    fun preview(position: Float) {
        previewPosition = if (position.isFinite()) {
            position.coerceIn(0f, AppDisplayScalePreset.entries.lastIndex.toFloat())
        } else {
            selectedPreset.ordinal.toFloat()
        }
    }

    fun finishSelection(): AppDisplayScalePreset? {
        val target = previewPreset
        previewPosition = target.ordinal.toFloat()
        if (target == selectedPreset) return null
        selectedPreset = target
        return target
    }

    fun synchronize(preset: AppDisplayScalePreset) {
        if (preset == selectedPreset) return
        selectedPreset = preset
        previewPosition = preset.ordinal.toFloat()
    }
}

@Composable
private fun displayScalePresetLabel(preset: AppDisplayScalePreset): String = when (preset) {
    AppDisplayScalePreset.ExtraCompact -> uiString(Res.string.display_scale_extra_compact)
    AppDisplayScalePreset.Compact -> uiString(Res.string.display_scale_compact)
    AppDisplayScalePreset.Default -> uiString(Res.string.common_default)
    AppDisplayScalePreset.Large -> uiString(Res.string.display_scale_large)
    AppDisplayScalePreset.ExtraLarge -> uiString(Res.string.display_scale_extra_large)
}

private fun displayScalePercentage(preset: AppDisplayScalePreset): String =
    "${(preset.scale * 100).roundToInt()}%"
