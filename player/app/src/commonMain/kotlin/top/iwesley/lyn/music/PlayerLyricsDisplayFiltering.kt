package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine
import top.iwesley.lyn.music.domain.EnhancedLyricsDisplayLine
import top.iwesley.lyn.music.domain.EnhancedLyricsPresentation
import top.iwesley.lyn.music.feature.player.isPlayerLyricsStructureTagLine

internal data class VisiblePlayerLyricsLine(
    val rawIndex: Int,
    val line: LyricsLine,
    val enhancedLine: EnhancedLyricsDisplayLine? = null,
)

internal fun buildVisiblePlayerLyricsLines(
    lyrics: LyricsDocument,
    enhancedLyricsPresentation: EnhancedLyricsPresentation? = null,
): List<VisiblePlayerLyricsLine> {
    return lyrics.lines.mapIndexedNotNull { rawIndex, line ->
        if (isPlayerLyricsStructureTagLine(line.text)) {
            return@mapIndexedNotNull null
        }
        VisiblePlayerLyricsLine(
            rawIndex = rawIndex,
            line = line,
            enhancedLine = enhancedLyricsPresentation?.lines?.getOrNull(rawIndex),
        )
    }
}

internal fun resolveVisiblePlayerLyricsHighlightedIndex(
    visibleLines: List<VisiblePlayerLyricsLine>,
    highlightedRawIndex: Int,
): Int {
    if (visibleLines.isEmpty() || highlightedRawIndex < 0) return -1
    visibleLines.indexOfFirst { it.rawIndex == highlightedRawIndex }
        .takeIf { it >= 0 }
        ?.let { return it }
    visibleLines.indexOfFirst { it.rawIndex > highlightedRawIndex }
        .takeIf { it >= 0 }
        ?.let { return it }
    return visibleLines.indexOfLast { it.rawIndex < highlightedRawIndex }
}

internal fun resolveVisiblePlayerLyricsScrollTarget(
    lyrics: LyricsDocument?,
    visibleLines: List<VisiblePlayerLyricsLine>,
    highlightedRawIndex: Int,
): Int? {
    if (lyrics == null || visibleLines.isEmpty()) return null
    return when (val highlightedVisibleIndex = resolveVisiblePlayerLyricsHighlightedIndex(visibleLines, highlightedRawIndex)) {
        in visibleLines.indices -> highlightedVisibleIndex
        else -> if (lyrics.isSynced) 0 else null
    }
}

internal fun resolveVisiblePlayerLyricsSelectedIndices(
    visibleLines: List<VisiblePlayerLyricsLine>,
    selectedRawIndices: Set<Int>,
): Set<Int> {
    if (visibleLines.isEmpty() || selectedRawIndices.isEmpty()) return emptySet()
    return visibleLines.mapIndexedNotNull { visibleIndex, line ->
        visibleIndex.takeIf { line.rawIndex in selectedRawIndices }
    }.toSet()
}
