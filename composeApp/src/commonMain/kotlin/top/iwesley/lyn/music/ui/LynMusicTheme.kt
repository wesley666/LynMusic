package top.iwesley.lyn.music.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkPalette = darkColorScheme(
    primary = Color(0xFFFF6B6B),
    onPrimary = Color(0xFF290000),
    secondary = Color(0xFFFFA94D),
    onSecondary = Color(0xFF331500),
    tertiary = Color(0xFFFFD8A8),
    background = Color(0xFF120B0D),
    onBackground = Color(0xFFF8ECEA),
    surface = Color(0xFF1B1114),
    onSurface = Color(0xFFF8ECEA),
    surfaceVariant = Color(0xFF352227),
    onSurfaceVariant = Color(0xFFE4C9C5),
    outline = Color(0xFF9C7476),
)

private val LightPalette = lightColorScheme(
    primary = Color(0xFFE03131),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFF76707),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFFFFC078),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF2B1718),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2B1718),
    surfaceVariant = Color(0xFFFCE4E1),
    onSurfaceVariant = Color(0xFF5F4346),
    outline = Color(0xFFB38A8A),
)

@Composable
fun LynMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkPalette else LightPalette,
        typography = Typography(),
        content = content,
    )
}

val ColorScheme.heroGlow: Color
    get() = if (background.red < 0.2f) Color(0x66FF6B6B) else Color(0x33E03131)
