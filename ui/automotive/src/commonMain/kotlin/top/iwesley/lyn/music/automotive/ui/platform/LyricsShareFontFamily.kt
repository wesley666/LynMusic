package top.iwesley.lyn.music.automotive.ui.platform

import top.iwesley.lyn.music.*

import androidx.compose.ui.text.font.FontFamily

expect fun lyricsSharePreviewFontFamily(
    fontKey: String?,
    displayName: String?,
    fontFilePath: String? = null,
): FontFamily?
