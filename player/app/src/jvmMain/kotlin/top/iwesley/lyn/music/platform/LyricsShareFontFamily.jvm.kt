package top.iwesley.lyn.music.platform

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.SystemFont

@OptIn(ExperimentalTextApi::class)
actual fun lyricsSharePreviewFontFamily(familyName: String?): FontFamily? {
    val normalizedFamilyName = familyName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        FontFamily(SystemFont(normalizedFamilyName))
    }.getOrNull()
}
