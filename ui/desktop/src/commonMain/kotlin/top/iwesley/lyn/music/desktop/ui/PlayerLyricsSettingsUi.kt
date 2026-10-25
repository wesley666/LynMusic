package top.iwesley.lyn.music.desktop.ui

import top.iwesley.lyn.music.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.MAX_USER_LYRICS_OFFSET_MS
import top.iwesley.lyn.music.core.model.PlayerLyricsStyle
import top.iwesley.lyn.music.core.model.formatThemeHexColor
import top.iwesley.lyn.music.core.model.normalizeUserLyricsOffset
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.desktop.ui.platform.PlayerLyricsSettingsWindow
import top.iwesley.lyn.music.platform.lyricsSharePreviewFontFamily
import top.iwesley.lyn.music.resources.*
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun LyricsStyleButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.size(52.dp)) {
        Icon(Icons.Rounded.FormatPaint, uiString(Res.string.player_lyrics_style), tint = Color.White)
    }
}

@Composable
fun rememberPlayerLyricsFont(state: PlayerState, onIntent: (PlayerIntent) -> Unit): FontFamily? {
    val key = state.lyricsStyle.fontKey
    val path = state.lyricsFontPath
    val family = remember(key, path) {
        if (key == null || path == null) null else lyricsSharePreviewFontFamily(key, displayName = null, fontFilePath = path)
    }
    LaunchedEffect(key, path, family) {
        if (key != null && path != null && family == null) onIntent(PlayerIntent.LyricsFontFailed(key))
    }
    return family
}

fun TextStyle.withPlayerLyricsStyle(style: PlayerLyricsStyle, family: FontFamily?): TextStyle = copy(
    fontSize = fontSize * style.fontScale,
    lineHeight = if (lineHeight.isSp) lineHeight * style.fontScale else lineHeight,
    fontFamily = family ?: fontFamily,
)

internal fun stepPlayerLyricsFontScale(scale: Float, increase: Boolean): Float =
    ((scale * 100).roundToInt() + if (increase) 5 else -5).coerceIn(75, 175) / 100f

internal fun stepPlayerLyricsOffset(offsetMs: Long, earlier: Boolean): Long =
    normalizeUserLyricsOffset(normalizeUserLyricsOffset(offsetMs) + if (earlier) 100 else -100)

/** Display old millisecond-precision calibrations as saved, without rounding them to the button step. */
internal fun formatPlayerLyricsOffsetMagnitude(offsetMs: Long): String {
    val magnitude = abs(normalizeUserLyricsOffset(offsetMs))
    val decimal = (magnitude % 1000).toString().padStart(3, '0').trimEnd('0')
    return "${magnitude / 1000}${if (decimal.isEmpty()) "" else ".$decimal"}"
}

internal val PlayerLyricsPresetColors = listOf(
    0xFFFFFFFF.toInt(), 0xFFFFD166.toInt(), 0xFF75E2BB.toInt(), 0xFF73C7FF.toInt(), 0xFFFF7B9C.toInt(),
)

internal enum class LyricsSettingsChild { Font, Color }

@Composable
fun PlayerLyricsSettingsOverlay(
    state: PlayerState,
    mobile: Boolean,
    onIntent: (PlayerIntent) -> Unit,
    backdrop: PlayerLyricsBackdrop? = null,
) {
    val presentation = currentPlayerPresentation()
    // These windows belong to the overlay session, not to a song or a style revision.
    var child by remember { mutableStateOf<LyricsSettingsChild?>(null) }
    val dismissTop: () -> Unit = {
        if (child != null) child = null else presentation.closeLyricsStyle()
    }
    LyricsSettingsPanelWindow(uiString(Res.string.player_lyrics_style), mobile, backdrop, dismissTop) {
        LyricsStyleControls(state, onIntent, onOpenFont = { child = LyricsSettingsChild.Font }, onOpenColor = { child = LyricsSettingsChild.Color })
    }
    when (child) {
        LyricsSettingsChild.Font -> LyricsSettingsPanelWindow(uiString(Res.string.player_lyrics_font), mobile, backdrop, onDismiss = { child = null }) {
            LyricsFontChoices(state, onIntent)
        }
        LyricsSettingsChild.Color -> LyricsSettingsPanelWindow(uiString(Res.string.player_lyrics_custom_color), mobile, backdrop, onDismiss = { child = null }) {
            LyricsColorPicker(state.lyricsStyle.highlightArgb) { color ->
                onIntent(PlayerIntent.LyricsStyleChanged(state.lyricsStyle.copy(highlightArgb = color)))
            }
        }
        null -> Unit
    }
}

@Composable
private fun LyricsSettingsPanelWindow(
    title: String,
    mobile: Boolean,
    backdrop: PlayerLyricsBackdrop?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    PlayerLyricsSettingsWindow(onDismiss = onDismiss) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp)) {
                LyricsGlassPanel(
                    backdrop,
                    Modifier.align(if (mobile) Alignment.BottomCenter else Alignment.CenterStart)
                        .widthIn(max = 420.dp).fillMaxWidth()
                        .heightIn(max = maxHeight * if (mobile) 0.55f else 0.92f),
                ) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                            Text(title, Modifier.padding(horizontal = 40.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd).size(40.dp)) {
                                Icon(Icons.Rounded.Close, uiString(Res.string.player_lyrics_close), modifier = Modifier.size(20.dp))
                            }
                        }
                        Column(
                            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            content = content,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.LyricsStyleControls(
    state: PlayerState,
    onIntent: (PlayerIntent) -> Unit,
    onOpenFont: () -> Unit,
    onOpenColor: () -> Unit,
) {
    val style = state.lyricsStyle
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .toggleable(value = style.showTranslation, role = Role.Switch) { showTranslation ->
                onIntent(PlayerIntent.LyricsStyleChanged(style.copy(showTranslation = showTranslation)))
            }.heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            uiString(Res.string.player_lyrics_show_translation), Modifier.weight(1f),
            color = LyricsSettingsSecondaryColor, style = MaterialTheme.typography.bodyMedium,
        )
        Switch(
            checked = style.showTranslation,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color.White.copy(alpha = 0.32f),
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White.copy(alpha = 0.78f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.4f),
            ),
        )
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiString(Res.string.player_lyrics_size), color = LyricsSettingsSecondaryColor, style = MaterialTheme.typography.bodyMedium)
        LyricsSettingsPill("A−", uiString(Res.string.player_lyrics_size_decrease), enabled = style.fontScale > 0.75f, onClick = {
            onIntent(PlayerIntent.LyricsStyleChanged(style.copy(fontScale = stepPlayerLyricsFontScale(style.fontScale, false))))
        })
        Text("${(style.fontScale * 100).roundToInt()}%", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        LyricsSettingsPill("A+", uiString(Res.string.player_lyrics_size_increase), enabled = style.fontScale < 1.75f, onClick = {
            onIntent(PlayerIntent.LyricsStyleChanged(style.copy(fontScale = stepPlayerLyricsFontScale(style.fontScale, true))))
        })
    }
    val selectedFont = state.availableLyricsFonts.firstOrNull { it.fontKey == style.fontKey }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenFont).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(uiString(Res.string.player_lyrics_font), color = LyricsSettingsSecondaryColor, style = MaterialTheme.typography.bodyMedium)
        Text(selectedFont?.displayName ?: uiString(Res.string.player_lyrics_default_font), Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, modifier = Modifier.size(20.dp))
    }
    Text(uiString(Res.string.player_lyrics_highlight), color = LyricsSettingsSecondaryColor, style = MaterialTheme.typography.bodyMedium)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = 6) {
        PlayerLyricsPresetColors.forEach { color ->
            LyricsColorSwatch(color, style.highlightArgb == color, formatThemeHexColor(color)) {
                onIntent(PlayerIntent.LyricsStyleChanged(style.copy(highlightArgb = color)))
            }
        }
        LyricsColorSwatch(null, style.highlightArgb !in PlayerLyricsPresetColors, uiString(Res.string.player_lyrics_custom_color), onOpenColor)
    }
    val trackId = state.effectiveSnapshot.currentTrack?.id
    val version = state.lyricsVersion
    val enabled = state.originalLyrics?.isSynced == true && version != null && trackId == state.lyricsTrackId
    val offset = state.lyricsUserOffsetMs
    val applyOffset: (Long) -> Unit = { value ->
        if (enabled && trackId != null) onIntent(PlayerIntent.LyricsOffsetChanged(trackId, version, value))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(uiString(Res.string.player_lyrics_offset), Modifier.weight(1f), color = LyricsSettingsSecondaryColor, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { applyOffset(0) }, enabled = enabled && offset != 0L, colors = lyricsSettingsTextButtonColors()) {
            Text(uiString(Res.string.player_lyrics_reset_offset))
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LyricsSettingsPill(uiString(Res.string.player_lyrics_later), uiString(Res.string.player_lyrics_later_step), enabled && offset > -MAX_USER_LYRICS_OFFSET_MS, Icons.AutoMirrored.Rounded.Undo) {
            applyOffset(stepPlayerLyricsOffset(offset, earlier = false))
        }
        val timing = when {
            offset == 0L -> uiString(Res.string.player_lyrics_offset_unchanged)
            offset > 0L -> uiString(Res.string.player_lyrics_offset_earlier_value, formatPlayerLyricsOffsetMagnitude(offset))
            else -> uiString(Res.string.player_lyrics_offset_later_value, formatPlayerLyricsOffsetMagnitude(offset))
        }
        Text(timing, Modifier.weight(1f), color = Color.White.copy(alpha = if (enabled) 1f else 0.45f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        LyricsSettingsPill(uiString(Res.string.player_lyrics_earlier), uiString(Res.string.player_lyrics_earlier_step), enabled && offset < MAX_USER_LYRICS_OFFSET_MS, Icons.AutoMirrored.Rounded.Redo) {
            applyOffset(stepPlayerLyricsOffset(offset, earlier = true))
        }
    }
    Text(uiString(Res.string.player_lyrics_offset_hint), color = LyricsSettingsSecondaryColor, style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { onIntent(PlayerIntent.LyricsStyleChanged(PlayerLyricsStyle())) }, modifier = Modifier.align(Alignment.CenterHorizontally), colors = lyricsSettingsTextButtonColors()) {
        Text(uiString(Res.string.player_lyrics_reset_style))
    }
}

private val LyricsSettingsSecondaryColor = Color.White.copy(alpha = 0.78f)

@Composable
private fun lyricsSettingsTextButtonColors() = ButtonDefaults.textButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.35f))

@Composable
private fun LyricsSettingsPill(label: String, description: String, enabled: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    TextButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.heightIn(min = 44.dp).semantics { contentDescription = description },
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = Color.White.copy(alpha = 0.14f), contentColor = Color.White,
            disabledContainerColor = Color.White.copy(alpha = 0.07f), disabledContentColor = Color.White.copy(alpha = 0.35f),
        ),
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(label, maxLines = 1)
    }
}

@Composable
private fun LyricsColorSwatch(color: Int?, isSelected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick).semantics {
            contentDescription = label
            selected = isSelected
        },
        contentAlignment = Alignment.Center,
    ) {
        if (color == null) {
            // A color-wheel control, not a static image: the gradient represents selectable hues.
            Canvas(Modifier.size(32.dp)) {
                drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent)))
            }
        } else {
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(color)))
        }
        if (isSelected) {
            Icon(Icons.Rounded.Check, null, tint = if (color == null || Color(color).luminance() > 0.4f) Color(0xFF33333B) else Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ColumnScope.LyricsFontChoices(state: PlayerState, onIntent: (PlayerIntent) -> Unit) {
    if (state.isLyricsFontsLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.White)
    LyricsFontRow(uiString(Res.string.player_lyrics_default_font), state.lyricsStyle.fontKey == null, null) {
        onIntent(PlayerIntent.LyricsStyleChanged(state.lyricsStyle.copy(fontKey = null)))
    }
    state.availableLyricsFonts.forEach { font ->
        val family = remember(font.fontKey, font.fontFilePath) { lyricsSharePreviewFontFamily(font.fontKey, font.displayName, font.fontFilePath) }
        LyricsFontRow(font.displayName, state.lyricsStyle.fontKey == font.fontKey, family) {
            onIntent(PlayerIntent.LyricsStyleChanged(state.lyricsStyle.copy(fontKey = font.fontKey)))
        }
    }
}

@Composable
private fun LyricsColorPicker(argb: Int, onColorChange: (Int) -> Unit) {
    var hsv by remember { mutableStateOf(argbToThemePickerHsv(argb)) }
    LaunchedEffect(argb) {
        if (themePickerHsvToArgb(hsv) != argb) hsv = argbToThemePickerHsv(argb)
    }
    val panelLabel = uiString(Res.string.player_lyrics_color_panel)
    val panelState = uiString(Res.string.player_lyrics_color_panel_value, (hsv.saturation * 100).roundToInt(), (hsv.value * 100).roundToInt())
    val moreSaturation = uiString(Res.string.player_lyrics_more_saturation)
    val lessSaturation = uiString(Res.string.player_lyrics_less_saturation)
    val moreBrightness = uiString(Res.string.player_lyrics_more_brightness)
    val lessBrightness = uiString(Res.string.player_lyrics_less_brightness)
    val hueLabel = uiString(Res.string.player_lyrics_hue)
    fun adjustPanel(saturationDelta: Float = 0f, valueDelta: Float = 0f): Boolean {
        hsv = hsv.copy(saturation = (hsv.saturation + saturationDelta).coerceIn(0f, 1f), value = (hsv.value + valueDelta).coerceIn(0f, 1f))
        onColorChange(themePickerHsvToArgb(hsv))
        return true
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(argb)).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape))
            Text(formatThemeHexColor(argb), fontFamily = FontFamily.Monospace)
        }
        ThemeSaturationValuePanel(hsv.hue, hsv.saturation, hsv.value, modifier = Modifier.widthIn(max = 240.dp).semantics {
            contentDescription = panelLabel
            stateDescription = panelState
            customActions = listOf(
                CustomAccessibilityAction(moreSaturation) { adjustPanel(saturationDelta = 0.05f) },
                CustomAccessibilityAction(lessSaturation) { adjustPanel(saturationDelta = -0.05f) },
                CustomAccessibilityAction(moreBrightness) { adjustPanel(valueDelta = 0.05f) },
                CustomAccessibilityAction(lessBrightness) { adjustPanel(valueDelta = -0.05f) },
            )
        }, onSelectionChange = {
            hsv = hsv.copy(saturation = it.saturation, value = it.value)
            onColorChange(themePickerHsvToArgb(hsv))
        })
        ThemeHueSlider(hsv.hue, modifier = Modifier.padding(vertical = 8.dp).semantics {
            contentDescription = hueLabel
            progressBarRangeInfo = ProgressBarRangeInfo(hsv.hue, 0f..360f)
            setProgress { value ->
                hsv = hsv.copy(hue = value.coerceIn(0f, 360f))
                onColorChange(themePickerHsvToArgb(hsv))
                true
            }
        }, onHueChange = {
            hsv = hsv.copy(hue = it)
            onColorChange(themePickerHsvToArgb(hsv))
        })
    }
}

@Composable
private fun LyricsFontRow(label: String, isSelected: Boolean, family: FontFamily?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .semantics { selected = isSelected }.padding(vertical = 6.dp, horizontal = 12.dp).heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, fontFamily = family, modifier = Modifier.weight(1f))
        if (isSelected) Icon(Icons.Rounded.Check, null, modifier = Modifier.size(22.dp))
    }
}
