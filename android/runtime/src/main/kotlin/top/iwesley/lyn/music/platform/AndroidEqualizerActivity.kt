package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.ProvideUiLanguage

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.platform.nativeUiString as uiString

import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.AppThemePalette
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.clampEqualizerLevel
import top.iwesley.lyn.music.core.model.deriveAppThemePalette
import top.iwesley.lyn.music.core.model.effectiveAppDisplayDensity
import top.iwesley.lyn.music.core.model.equalizerMillibelsToDecibels
import top.iwesley.lyn.music.core.model.formatEqualizerFrequencyLabel
import top.iwesley.lyn.music.core.model.resolveAppThemeTextPalette
import top.iwesley.lyn.music.core.model.resolveAppThemeTokens
import kotlin.math.roundToInt

class AndroidEqualizerActivity : ComponentActivity() {
    private var services: AndroidEqualizerActivityServices? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_EQUALIZER_LOCK_PORTRAIT, true)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val activityServices = createAndroidEqualizerActivityServices(this)
        services = activityServices
        setContent {
            ProvideUiLanguage {
                val appDisplayScalePreset by activityServices.appDisplayScalePreset.collectAsState()
                val selectedTheme by activityServices.selectedTheme.collectAsState()
                val customThemeTokens by activityServices.customThemeTokens.collectAsState()
                val textPalettePreferences by activityServices.textPalettePreferences.collectAsState()
                val themeTokens = remember(selectedTheme, customThemeTokens) {
                    resolveAppThemeTokens(selectedTheme, customThemeTokens)
                }
                val textPalette = remember(selectedTheme, textPalettePreferences) {
                    resolveAppThemeTextPalette(selectedTheme, textPalettePreferences)
                }
                SideEffect {
                    val systemBarStyle = if (textPalette == AppThemeTextPalette.Black) {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    }
                    enableEdgeToEdge(
                        statusBarStyle = systemBarStyle,
                        navigationBarStyle = systemBarStyle,
                    )
                }
                ProvideFixedAndroidEqualizerDensity(appDisplayScalePreset = appDisplayScalePreset) {
                    AndroidEqualizerTheme(
                        themeTokens = themeTokens,
                        textPalette = textPalette,
                    ) {
                        EqualizerActivityScreen(
                            service = activityServices.equalizerService,
                            onBack = ::finish,
                        )
                    }
                }


            }
}
    }

    override fun onDestroy() {
        services?.close()
        services = null
        super.onDestroy()
    }
}

@Composable
private fun EqualizerActivityScreen(
    service: AndroidEqualizerUiService,
    onBack: () -> Unit,
) {
    val state by service.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            EqualizerTopBar(
                enabled = state.enabled,
                controlsEnabled = state.supported,
                onEnabledChanged = { enabled ->
                    coroutineScope.launch { service.setEnabled(enabled) }
                },
                onBack = onBack,
            )
            EqualizerPresetRow(
                state = state,
                onPresetSelected = { presetName ->
                    coroutineScope.launch { service.selectPreset(presetName) }
                },
            )
            EqualizerBands(
                state = state,
                onBandLevelChanged = { band, level ->
                    coroutineScope.launch {
                        service.setBandLevel(band.centerFrequencyHz, level)
                    }
                },
            )
            EqualizerFooter(
                state = state,
                onReset = {
                    coroutineScope.launch { service.reset() }
                },
            )
        }
    }
}

@Composable
private fun EqualizerBackIcon(
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onBackground,
) {
    Canvas(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        },
    ) {
        val strokeWidth = 3.dp.toPx()
        val centerY = size.height / 2f
        val startX = size.width * 0.18f
        val endX = size.width * 0.82f
        val arrowX = size.width * 0.43f
        drawLine(
            color = tint,
            start = Offset(endX, centerY),
            end = Offset(startX, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(startX, centerY),
            end = Offset(arrowX, size.height * 0.25f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(startX, centerY),
            end = Offset(arrowX, size.height * 0.75f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun EqualizerTopBar(
    enabled: Boolean,
    controlsEnabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(52.dp)) {
                EqualizerBackIcon(
                    contentDescription = uiString(Res.string.common_back),
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = uiString(Res.string.settings_equalizer_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChanged,
            enabled = controlsEnabled,
        )
    }
}

@Composable
private fun EqualizerPresetRow(
    state: AndroidEqualizerState,
    onPresetSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = state.active && state.enabled,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = state.selectedPresetName ?: uiString(Res.string.common_custom),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                DropdownMenuItem(
                    text = {
                        EqualizerPresetMenuText(
                            text = uiString(Res.string.common_custom),
                            selected = state.selectedPresetName == null,
                        )
                    },
                    onClick = {
                        expanded = false
                        onPresetSelected(null)
                    },
                )
                state.presets.forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            EqualizerPresetMenuText(
                                text = preset.name,
                                selected = state.selectedPresetName == preset.name,
                            )
                        },
                        onClick = {
                            expanded = false
                            onPresetSelected(preset.name)
                        },
                    )
                }
            }
        }
        Text(
            text = when {
                !state.supported -> uiString(Res.string.common_unavailable)
                !state.active -> uiString(Res.string.equalizer_waiting_for_playback)
                state.enabled -> uiString(Res.string.equalizer_enabled_state)
                else -> uiString(Res.string.equalizer_disabled_state)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EqualizerPresetMenuText(
    text: String,
    selected: Boolean,
) {
    Text(
        text = text,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EqualizerBands(
    state: AndroidEqualizerState,
    onBandLevelChanged: (AndroidEqualizerBand, Int) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDbLabel(state.maxLevelMb),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = formatDbLabel(0),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = formatDbLabel(state.minLevelMb),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            contentPadding = PaddingValues(horizontal = 6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(state.bands, key = { it.centerFrequencyHz }) { band ->
                EqualizerBandColumn(
                    band = band,
                    minLevelMb = state.minLevelMb,
                    maxLevelMb = state.maxLevelMb,
                    enabled = state.active && state.enabled,
                    onLevelChanged = { level -> onBandLevelChanged(band, level) },
                )
            }
        }
        if (state.bands.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(28.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.errorMessage?.nativeDisplayText() ?: uiString(Res.string.equalizer_no_adjustable_bands),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EqualizerBandColumn(
    band: AndroidEqualizerBand,
    minLevelMb: Int,
    maxLevelMb: Int,
    enabled: Boolean,
    onLevelChanged: (Int) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VerticalEqualizerSlider(
            valueMb = band.levelMb,
            minLevelMb = minLevelMb,
            maxLevelMb = maxLevelMb,
            enabled = enabled,
            onValueChanged = onLevelChanged,
        )
        Text(
            text = formatEqualizerFrequencyLabel(band.centerFrequencyHz),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun VerticalEqualizerSlider(
    valueMb: Int,
    minLevelMb: Int,
    maxLevelMb: Int,
    enabled: Boolean,
    onValueChanged: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant
    val thumbColor = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
    val thumbStroke = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val safeMin = minOf(minLevelMb, maxLevelMb)
    val safeMax = maxOf(minLevelMb, maxLevelMb)
    val coercedValue = clampEqualizerLevel(valueMb, safeMin, safeMax)
    val onPointerValue: (Float, Float) -> Unit = { y, height ->
        if (enabled && height > 0f && safeMax > safeMin) {
            val fraction = (1f - (y / height)).coerceIn(0f, 1f)
            val raw = safeMin + ((safeMax - safeMin) * fraction).roundToInt()
            onValueChanged(raw)
        }
    }
    Canvas(
        modifier = Modifier
            .size(width = 58.dp, height = 292.dp)
            .pointerInput(enabled, safeMin, safeMax) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> onPointerValue(offset.y, size.height.toFloat()) },
                    onVerticalDrag = { change, _ ->
                        onPointerValue(change.position.y, size.height.toFloat())
                    },
                )
            },
    ) {
        val trackTop = 10.dp.toPx()
        val trackBottom = size.height - 38.dp.toPx()
        val trackX = size.width / 2f
        val trackStroke = 5.dp.toPx()
        val range = (safeMax - safeMin).takeIf { it > 0 } ?: 1
        val fraction = (coercedValue - safeMin).toFloat() / range.toFloat()
        val thumbY = trackBottom - (trackBottom - trackTop) * fraction
        val zeroFraction = (0 - safeMin).toFloat() / range.toFloat()
        val zeroY = trackBottom - (trackBottom - trackTop) * zeroFraction.coerceIn(0f, 1f)
        drawLine(
            color = inactiveColor,
            start = Offset(trackX, trackTop),
            end = Offset(trackX, trackBottom),
            strokeWidth = trackStroke,
        )
        drawLine(
            color = activeColor.copy(alpha = if (enabled) 0.95f else 0.22f),
            start = Offset(trackX, zeroY),
            end = Offset(trackX, thumbY),
            strokeWidth = trackStroke,
        )
        drawCircle(
            color = inactiveColor.copy(alpha = 0.42f),
            radius = 10.dp.toPx(),
            center = Offset(trackX, zeroY),
        )
        drawCircle(
            color = thumbColor,
            radius = 25.dp.toPx(),
            center = Offset(trackX, thumbY),
        )
        drawCircle(
            color = thumbStroke.copy(alpha = if (enabled) 0.92f else 0.45f),
            radius = 25.dp.toPx(),
            center = Offset(trackX, thumbY),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
        )
        val gripColor = thumbStroke.copy(alpha = if (enabled) 0.72f else 0.35f)
        val gripHalfWidth = with(density) { 11.dp.toPx() }
        repeat(3) { index ->
            val y = thumbY + (index - 1) * 7.dp.toPx()
            drawLine(
                color = gripColor,
                start = Offset(trackX - gripHalfWidth, y),
                end = Offset(trackX + gripHalfWidth, y),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

@Composable
private fun EqualizerFooter(
    state: AndroidEqualizerState,
    onReset: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        state.errorMessage?.takeIf { !state.supported || !state.active }?.let { message ->
            Text(
                text = message.nativeDisplayText(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onReset,
                enabled = state.active,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(uiString(Res.string.common_reset))
            }
        }
    }
}

private fun formatDbLabel(levelMb: Int): String {
    val decibels = equalizerMillibelsToDecibels(levelMb)
    val sign = if (decibels > 0f) "+" else ""
    return if (levelMb % 100 == 0) {
        "$sign${levelMb / 100} dB"
    } else {
        "$sign${((decibels * 10f).roundToInt() / 10f)} dB"
    }
}

@Composable
private fun ProvideFixedAndroidEqualizerDensity(
    appDisplayScalePreset: AppDisplayScalePreset,
    content: @Composable () -> Unit,
) {
    val currentDensity = LocalDensity.current
    val fixedDensity = remember(currentDensity.density, currentDensity.fontScale, appDisplayScalePreset) {
        Density(
            density = effectiveAppDisplayDensity(
                androidEqualizerStableDensityScale(currentDensity.density),
                appDisplayScalePreset,
            ),
            fontScale = currentDensity.fontScale,
        )
    }
    CompositionLocalProvider(LocalDensity provides fixedDensity) {
        content()
    }
}

@Composable
private fun AndroidEqualizerTheme(
    themeTokens: AppThemeTokens,
    textPalette: AppThemeTextPalette,
    content: @Composable () -> Unit,
) {
    val palette = remember(themeTokens, textPalette) {
        deriveAppThemePalette(
            tokens = themeTokens,
            textPalette = textPalette,
        )
    }
    MaterialTheme(
        colorScheme = palette.toEqualizerColorScheme(),
        typography = Typography(),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Color(palette.onBackgroundArgb),
        ) {
            content()
        }
    }
}

private fun AppThemePalette.toEqualizerColorScheme(): ColorScheme {
    return darkColorScheme(
        primary = Color(primaryArgb),
        onPrimary = Color(onPrimaryArgb),
        primaryContainer = Color(selectedContainerArgb),
        onPrimaryContainer = Color(onBackgroundArgb),
        secondary = Color(secondaryArgb),
        onSecondary = Color(onSecondaryArgb),
        secondaryContainer = Color(selectedContainerArgb),
        onSecondaryContainer = Color(onBackgroundArgb),
        tertiary = Color(tertiaryArgb),
        onTertiary = Color(onTertiaryArgb),
        tertiaryContainer = Color(cardContainerArgb),
        onTertiaryContainer = Color(onSurfaceArgb),
        background = Color(backgroundArgb),
        onBackground = Color(onBackgroundArgb),
        surface = Color(surfaceArgb),
        onSurface = Color(onSurfaceArgb),
        surfaceVariant = Color(surfaceVariantArgb),
        onSurfaceVariant = Color(onSurfaceVariantArgb),
        surfaceTint = Color(primaryArgb),
        inverseSurface = Color(onSurfaceArgb),
        inverseOnSurface = Color(surfaceArgb),
        inversePrimary = Color(secondaryArgb),
        outline = Color(outlineArgb),
        outlineVariant = Color(cardBorderArgb),
        scrim = Color(0x99000000.toInt()),
    )
}

private fun androidEqualizerStableDensityScale(fallbackDensity: Float): Float {
    val fallbackDpi = (fallbackDensity.takeIf { it > 0f } ?: 1f) * DisplayMetrics.DENSITY_DEFAULT
    val stableDpi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        DisplayMetrics.DENSITY_DEVICE_STABLE
    } else {
        fallbackDpi.roundToInt()
    }.takeIf { it > 0 } ?: fallbackDpi.roundToInt()
    return stableDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()
}
