package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.DEFAULT_LYRICS_SHARE_FONT_PREVIEW_TEXT
import top.iwesley.lyn.music.core.model.LyricsShareFontOption

internal val skiaLyricsShareLanguageHints = arrayOf("zh-Hans", "zh-Hant", "ja", "ko", "en")

internal val skiaLyricsShareFallbackFontFamilies = arrayOf<String?>(
    "PingFang SC",
    "Times New Roman"
)

internal fun skiaLyricsShareFallbackFontFamilyNames(): List<String> {
    return skiaLyricsShareFallbackFontFamilies
        .mapNotNull { familyName -> familyName?.trim()?.takeIf { it.isNotEmpty() } }
        .distinctBy { it.lowercase() }
}

internal fun isSkiaLyricsShareAlphabeticFamilyName(
    familyName: String,
): Boolean {
    val firstChar = familyName.trim().firstOrNull()?.uppercaseChar() ?: return false
    return firstChar in 'A'..'Z'
}

internal fun prioritizeIosLyricsShareFontFamilyNames(
    availableFonts: List<String>,
): List<LyricsShareFontOption> {
    val availableByName = linkedMapOf<String, String>()
    availableFonts.forEach { familyName ->
        val normalizedFamilyName = familyName.trim().takeIf { it.isNotEmpty() } ?: return@forEach
        val normalizedKey = normalizedFamilyName.lowercase()
        if (!availableByName.containsKey(normalizedKey)) {
            availableByName[normalizedKey] = normalizedFamilyName
        }
    }
    val prioritizedKeys = linkedSetOf<String>()
    val prioritizedFonts = skiaLyricsShareFallbackFontFamilyNames().mapNotNull { candidateFamilyName ->
        availableByName[candidateFamilyName.lowercase()]?.let { resolvedFamilyName ->
            prioritizedKeys += resolvedFamilyName.lowercase()
            LyricsShareFontOption(
                fontKey = resolvedFamilyName,
                displayName = resolvedFamilyName,
                previewText = DEFAULT_LYRICS_SHARE_FONT_PREVIEW_TEXT,
                isPrioritized = true,
            )
        }
    }
    val remainingFamilies = availableByName.values
        .filterNot { familyName -> familyName.lowercase() in prioritizedKeys }
    val (alphabeticFamilies, nonAlphabeticFamilies) = remainingFamilies.partition(::isSkiaLyricsShareAlphabeticFamilyName)
    val otherFonts = (alphabeticFamilies + nonAlphabeticFamilies).map { familyName ->
        LyricsShareFontOption(
            fontKey = familyName,
            displayName = familyName,
            previewText = DEFAULT_LYRICS_SHARE_FONT_PREVIEW_TEXT,
        )
    }
    return prioritizedFonts + otherFonts
}
