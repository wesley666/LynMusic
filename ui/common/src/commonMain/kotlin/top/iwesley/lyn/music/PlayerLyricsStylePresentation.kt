package top.iwesley.lyn.music

import androidx.compose.runtime.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import top.iwesley.lyn.music.core.model.PlayerLyricsStyle

typealias LyricsFontResolver = (fontKey: String?, displayName: String?, filePath: String?) -> FontFamily?

@Composable
fun rememberLyricsFont(
    fontKey: String?,
    filePath: String?,
    resolveFont: LyricsFontResolver,
    onFontFailed: (String) -> Unit,
): FontFamily? {
    val family = remember(fontKey, filePath) {
        if (fontKey == null || filePath == null) null else resolveFont(fontKey, null, filePath)
    }
    LaunchedEffect(fontKey, filePath, family) {
        if (fontKey != null && filePath != null && family == null) onFontFailed(fontKey)
    }
    return family
}

fun TextStyle.withPlayerLyricsStyle(style: PlayerLyricsStyle, family: FontFamily?): TextStyle = copy(
    fontSize = fontSize * style.fontScale,
    lineHeight = if (lineHeight.isSp) lineHeight * style.fontScale else lineHeight,
    fontFamily = family ?: fontFamily,
)
