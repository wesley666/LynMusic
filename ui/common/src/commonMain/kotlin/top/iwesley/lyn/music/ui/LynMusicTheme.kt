package top.iwesley.lyn.music.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import top.iwesley.lyn.music.core.model.AppThemePalette
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.CLASSIC_APP_THEME_TOKENS
import top.iwesley.lyn.music.core.model.deriveAppThemePalette

@Immutable
data class MainShellColors(
    val appGradientTop: Color,
    val navContainer: Color,
    val cardContainer: Color,
    val cardBorder: Color,
    val selectedContainer: Color,
    val selectedBorder: Color,
    val secondaryText: Color,
    val heroGlow: Color,
)

private val LocalMainShellColors = staticCompositionLocalOf {
    deriveAppThemePalette(
        tokens = CLASSIC_APP_THEME_TOKENS,
        textPalette = AppThemeTextPalette.White,
    ).toMainShellColors()
}

val mainShellColors: MainShellColors
    @Composable
    @ReadOnlyComposable
    get() = LocalMainShellColors.current

@Composable
fun LynMusicTheme(
    themeTokens: AppThemeTokens = CLASSIC_APP_THEME_TOKENS,
    textPalette: AppThemeTextPalette = AppThemeTextPalette.White,
    content: @Composable () -> Unit,
) {
    val palette = remember(themeTokens, textPalette) {
        deriveAppThemePalette(
            tokens = themeTokens,
            textPalette = textPalette,
        )
    }
    MaterialTheme(
        colorScheme = palette.toColorScheme(),
        typography = Typography(),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Color(palette.onBackgroundArgb),
            LocalMainShellColors provides palette.toMainShellColors(),
        ) {
            content()
        }
    }
}

val ColorScheme.heroGlow: Color
    @Composable
    @ReadOnlyComposable
    get() = mainShellColors.heroGlow

private fun AppThemePalette.toColorScheme(): ColorScheme {
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

private fun AppThemePalette.toMainShellColors(): MainShellColors {
    return MainShellColors(
        appGradientTop = Color(appGradientTopArgb),
        navContainer = Color(navContainerArgb),
        cardContainer = Color(cardContainerArgb),
        cardBorder = Color(cardBorderArgb),
        selectedContainer = Color(selectedContainerArgb),
        selectedBorder = Color(selectedBorderArgb),
        secondaryText = Color(secondaryTextArgb),
        heroGlow = Color(heroGlowArgb),
    )
}
