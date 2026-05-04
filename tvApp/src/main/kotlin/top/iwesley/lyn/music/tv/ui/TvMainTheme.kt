package top.iwesley.lyn.music.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.ui.LynMusicTheme

private val TvMainThemeTokens = AppThemeTokens(
    backgroundArgb = 0xFF0B0D10.toInt(),
    accentArgb = 0xFFE03131.toInt(),
    focusArgb = 0xFFE03131.toInt(),
)

@Composable
internal fun TvMainTheme(
    content: @Composable () -> Unit,
) {
    LynMusicTheme(
        themeTokens = TvMainThemeTokens,
        textPalette = AppThemeTextPalette.White,
    ) {
        androidx.tv.material3.MaterialTheme(
            colorScheme = androidx.tv.material3.darkColorScheme(
                primary = Color(0xFFE03131),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFF3A1518),
                onPrimaryContainer = Color(0xFFFFDADC),
                secondary = Color(0xFFE03131),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFF3A1518),
                onSecondaryContainer = Color(0xFFFFDADC),
                tertiary = Color(0xFFFFB4AB),
                onTertiary = Color(0xFF410002),
                tertiaryContainer = Color(0xFF5F1318),
                onTertiaryContainer = Color(0xFFFFDADC),
                background = Color(0xFF0B0D10),
                onBackground = Color(0xFFF4F6F8),
                surface = Color(0xFF151922),
                onSurface = Color(0xFFF4F6F8),
                surfaceVariant = Color(0xFF1D222C),
                onSurfaceVariant = Color(0xFFC4CAD3),
                surfaceTint = Color(0xFFE03131),
                inverseSurface = Color(0xFFE4E8EE),
                inverseOnSurface = Color(0xFF1A1D23),
                error = Color(0xFFFFB4AB),
                onError = Color(0xFF690005),
                errorContainer = Color(0xFF93000A),
                onErrorContainer = Color(0xFFFFDAD6),
                border = Color.White,
                borderVariant = Color(0xFF2B313B),
                scrim = Color(0x99000000),
            ),
            content = content,
        )
    }
}
