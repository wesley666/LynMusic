package top.iwesley.lyn.music.feature.player

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.UiText

val DESKTOP_LYRICS_LOADING_TEXT: UiText = uiText(Res.string.player_preparing_lyrics)

fun findDesktopLyricsHighlightedLine(
    lyrics: LyricsDocument?,
    positionMs: Long,
): Int {
    val lines = lyrics?.lines ?: return -1
    val target = positionMs + lyrics.offsetMs
    return lines.indexOfLast { line ->
        line.timestampMs?.let { it <= target } ?: false
    }
}

fun resolveDesktopLyricsOverlayText(
    lyrics: LyricsDocument?,
    highlightedLineIndex: Int,
    isLyricsLoading: Boolean,
): UiText? {
    val lines = lyrics?.lines.orEmpty()
    fun lineTextAt(index: Int): String? {
        return lines.getOrNull(index)
            ?.text
            ?.trim()
            ?.takeIf { text ->
                text.isNotEmpty() && !isPlayerLyricsStructureTagLine(text)
            }
    }
    lineTextAt(highlightedLineIndex)?.let { return UiText.Raw(it) }
    for (index in highlightedLineIndex + 1 until lines.size) {
        lineTextAt(index)?.let { return UiText.Raw(it) }
    }
    for (index in highlightedLineIndex - 1 downTo 0) {
        lineTextAt(index)?.let { return UiText.Raw(it) }
    }
    return if (isLyricsLoading) DESKTOP_LYRICS_LOADING_TEXT else null
}
