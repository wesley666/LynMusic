package top.iwesley.lyn.music.platform

import androidx.compose.ui.text.font.FontFamily

expect fun lyricsSharePreviewFontFamily(
    fontKey: String?,
    displayName: String?,
    fontFilePath: String? = null,
): FontFamily?
