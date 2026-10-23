package top.iwesley.lyn.music.automotive.ui.platform

import top.iwesley.lyn.music.*

import android.graphics.Typeface
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
            // Decode eagerly so a corrupt imported font can fall back before text layout.
            if (file.isFile) FontFamily(Typeface.createFromFile(file)) else null
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
