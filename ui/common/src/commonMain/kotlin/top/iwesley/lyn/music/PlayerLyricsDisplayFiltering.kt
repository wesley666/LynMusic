package top.iwesley.lyn.music

import kotlin.math.abs
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine
import top.iwesley.lyn.music.domain.EnhancedLyricsDisplayLine
import top.iwesley.lyn.music.domain.EnhancedLyricsPresentation
import top.iwesley.lyn.music.core.model.isLyricsStructureTagLine

const val PLAYER_LYRICS_SCROLL_ANIMATION_DURATION_MS = 420
private const val PLAYER_LYRICS_SMOOTH_SCROLL_MAX_INDEX_DISTANCE = 2

data class VisiblePlayerLyricsLine(
    val rawIndex: Int,
    val line: LyricsLine,
    val enhancedLine: EnhancedLyricsDisplayLine? = null,
)

data class PlayerLyricsVisibleItemInfo(
    val index: Int,
    val offset: Int,
    val size: Int,
)

fun buildVisiblePlayerLyricsLines(
    lyrics: LyricsDocument,
    enhancedLyricsPresentation: EnhancedLyricsPresentation? = null,
): List<VisiblePlayerLyricsLine> {
    return lyrics.lines.mapIndexedNotNull { rawIndex, line ->
        if (isLyricsStructureTagLine(line.text)) {
            return@mapIndexedNotNull null
        }
        VisiblePlayerLyricsLine(
            rawIndex = rawIndex,
            line = line,
            enhancedLine = enhancedLyricsPresentation?.lines?.getOrNull(rawIndex),
        )
    }
}

fun shouldShowPlayerLyricsEmptyState(
    isLyricsLoading: Boolean,
    hasLyricsLookupCompleted: Boolean,
    lyrics: LyricsDocument?,
    visibleLines: List<VisiblePlayerLyricsLine>,
): Boolean {
    return !isLyricsLoading &&
        hasLyricsLookupCompleted &&
        (lyrics == null || visibleLines.isEmpty())
}

fun resolveVisiblePlayerLyricsHighlightedIndex(
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

fun resolveVisiblePlayerLyricsScrollTarget(
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

fun shouldAnimatePlayerLyricsScroll(
    previousTargetIndex: Int?,
    targetIndex: Int,
    isTargetVisible: Boolean,
): Boolean {
    return previousTargetIndex != null &&
        isTargetVisible &&
        abs(targetIndex - previousTargetIndex) <= PLAYER_LYRICS_SMOOTH_SCROLL_MAX_INDEX_DISTANCE
}

fun resolvePlayerLyricsBrowseTargetIndex(
    visibleLines: List<VisiblePlayerLyricsLine>,
    visibleItems: List<PlayerLyricsVisibleItemInfo>,
    viewportStartOffset: Int,
    viewportEndOffset: Int,
): Int? {
    if (visibleLines.isEmpty() || visibleItems.isEmpty()) return null
    val viewportCenter = (viewportStartOffset + viewportEndOffset) / 2
    return visibleItems
        .filter { item -> visibleLines.getOrNull(item.index)?.line?.timestampMs != null }
        .minByOrNull { item ->
            val itemCenter = item.offset + item.size / 2
            abs(itemCenter - viewportCenter)
        }
        ?.index
}

fun resolvePlayerLyricsSeekPositionMs(
    line: VisiblePlayerLyricsLine?,
    lyricsOffsetMs: Long,
    durationMs: Long,
): Long? {
    val timestampMs = line?.line?.timestampMs ?: return null
    return (timestampMs - lyricsOffsetMs).coerceIn(0L, durationMs.coerceAtLeast(0L))
}

fun resolvePlayerLyricsActiveHighlightedIndex(
    visibleLines: List<VisiblePlayerLyricsLine>,
    playbackHighlightedIndex: Int,
    browseTargetIndex: Int?,
    isBrowsing: Boolean,
): Int {
    if (!isBrowsing) return playbackHighlightedIndex
    val targetIndex = browseTargetIndex ?: return playbackHighlightedIndex
    return if (visibleLines.getOrNull(targetIndex)?.line?.timestampMs != null) {
        targetIndex
    } else {
        playbackHighlightedIndex
    }
}

fun resolveVisiblePlayerLyricsSelectedIndices(
    visibleLines: List<VisiblePlayerLyricsLine>,
    selectedRawIndices: Set<Int>,
): Set<Int> {
    if (visibleLines.isEmpty() || selectedRawIndices.isEmpty()) return emptySet()
    return visibleLines.mapIndexedNotNull { visibleIndex, line ->
        visibleIndex.takeIf { line.rawIndex in selectedRawIndices }
    }.toSet()
}

fun resolveLyricsScrollTarget(
    lyrics: LyricsDocument?,
    highlightedLineIndex: Int,
): Int? {
    val lines = lyrics?.lines ?: return null
    if (lines.isEmpty()) return null
    return when {
        highlightedLineIndex in lines.indices -> highlightedLineIndex
        lyrics.isSynced -> 0
        else -> null
    }
}
