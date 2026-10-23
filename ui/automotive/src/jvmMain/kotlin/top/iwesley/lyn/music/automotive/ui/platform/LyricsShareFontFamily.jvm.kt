package top.iwesley.lyn.music.automotive.ui.platform

import top.iwesley.lyn.music.*

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.platform.SystemFont
import java.io.File
import org.jetbrains.skia.FontMgr
import top.iwesley.lyn.music.core.model.parseLyricsShareImportedFontHash

@OptIn(ExperimentalTextApi::class)
actual fun lyricsSharePreviewFontFamily(
    fontKey: String?,
    displayName: String?,
    fontFilePath: String?,
): FontFamily? {
    val normalizedFontFilePath = fontFilePath?.trim()?.takeIf { it.isNotEmpty() }
    if (normalizedFontFilePath != null) {
        return runCatching {
            val file = File(normalizedFontFilePath)
            if (!file.isFile) return@runCatching null
            val decoded = FontMgr.default.makeFromFile(file.absolutePath) ?: return@runCatching null
            decoded.close()
            FontFamily(Font(file))
        }.getOrNull()
    }
    if (fontKey?.let(::parseLyricsShareImportedFontHash) != null) return null
    val normalizedFamilyName = (displayName ?: fontKey)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        FontFamily(SystemFont(normalizedFamilyName))
    }.getOrNull()
}
