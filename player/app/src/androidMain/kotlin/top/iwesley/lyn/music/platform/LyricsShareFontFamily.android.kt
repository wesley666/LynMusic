package top.iwesley.lyn.music.platform

import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import top.iwesley.lyn.music.core.model.parseLyricsShareImportedFontHash

actual fun lyricsSharePreviewFontFamily(
    fontKey: String?,
    displayName: String?,
    fontFilePath: String?,
): FontFamily? {
    val normalizedFontFilePath = fontFilePath?.trim()?.takeIf { it.isNotEmpty() }
    if (normalizedFontFilePath != null) {
        return runCatching {
            val file = File(normalizedFontFilePath)
            if (file.isFile) FontFamily(Font(file)) else null
        }.getOrNull()
    }
    if (fontKey?.let(::parseLyricsShareImportedFontHash) != null) return null
    val familyName = (fontKey ?: displayName)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when (familyName.lowercase()) {
        "serif" -> FontFamily.Serif
        "sans-serif", "sansserif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> runCatching {
            FontFamily(Typeface.create(familyName, Typeface.NORMAL))
        }.getOrNull()
    }
}
