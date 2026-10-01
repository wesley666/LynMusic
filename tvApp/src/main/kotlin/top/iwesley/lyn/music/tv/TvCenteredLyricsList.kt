package top.iwesley.lyn.music.tv

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.uiString
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import top.iwesley.lyn.music.core.model.LyricsDocument

private const val TV_LYRICS_SCROLL_ANIMATION_DURATION_MS = 420
private const val TV_LYRICS_SMOOTH_SCROLL_MAX_INDEX_DISTANCE = 2

@Composable
internal fun TvCenteredLyricsList(
    lyrics: LyricsDocument?,
    highlightedLineIndex: Int,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    listModifier: Modifier = Modifier,
    loadingMessage: String = uiString(Res.string.desktop_lyrics_loading),
    emptyMessage: String = uiString(Res.string.desktop_lyrics_unavailable),
    messageContent: @Composable (String, Modifier) -> Unit,
) {
    val visibleLines = remember(lyrics) {
        lyrics?.lines
            ?.mapIndexedNotNull { index, line ->
                line.text.trim().takeIf { it.isNotBlank() }?.let { text ->
                    TvCenteredLyricsLine(rawIndex = index, text = text)
                }
            }
            .orEmpty()
    }
    val highlightedVisibleIndex = remember(visibleLines, highlightedLineIndex) {
        resolveTvCenteredLyricsHighlightedIndex(
            visibleLines = visibleLines,
            highlightedRawIndex = highlightedLineIndex,
        )
    }
    val scrollTargetIndex = remember(lyrics, visibleLines, highlightedVisibleIndex) {
        resolveTvCenteredLyricsScrollTarget(
            lyrics = lyrics,
            visibleLines = visibleLines,
            highlightedVisibleIndex = highlightedVisibleIndex,
        )
    }
    val listState = rememberLazyListState()
    var previousScrollTargetIndex by remember(lyrics) { mutableStateOf<Int?>(null) }
    LaunchedEffect(lyrics, scrollTargetIndex, visibleLines.size) {
        val targetIndex = scrollTargetIndex ?: return@LaunchedEffect
        val isTargetVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
        val shouldAnimateScroll = shouldAnimateTvCenteredLyricsScroll(
            previousTargetIndex = previousScrollTargetIndex,
            targetIndex = targetIndex,
            isTargetVisible = isTargetVisible,
        )
        previousScrollTargetIndex = targetIndex
        centerTvLyricsItem(
            listState = listState,
            targetIndex = targetIndex,
            itemCount = visibleLines.size,
            animate = shouldAnimateScroll,
        )
    }

    when {
        isLoading -> messageContent(loadingMessage, modifier)
        visibleLines.isEmpty() -> messageContent(emptyMessage, modifier)
        else -> BoxWithConstraints(modifier = modifier) {
            val centerPadding = (maxHeight / 2 - 36.dp).coerceAtLeast(56.dp)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(listModifier),
                contentPadding = PaddingValues(vertical = centerPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(visibleLines, key = { _, line -> line.rawIndex }) { index, line ->
                    val highlighted = index == highlightedVisibleIndex
                    Text(
                        text = line.text,
                        color = if (highlighted) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.74f),
                        fontWeight = if (highlighted) FontWeight.ExtraBold else FontWeight.Medium,
                        style = if (highlighted) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private suspend fun centerTvLyricsItem(
    listState: LazyListState,
    targetIndex: Int,
    itemCount: Int,
    animate: Boolean,
) {
    if (targetIndex !in 0 until itemCount) return
    if (listState.layoutInfo.visibleItemsInfo.none { it.index == targetIndex }) {
        listState.scrollToItem(targetIndex)
        withFrameNanos { }
    }
    val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
        ?: return
    val viewportCenter =
        (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
    val itemCenter = itemInfo.offset + itemInfo.size / 2
    val delta = (itemCenter - viewportCenter).toFloat()
    if (abs(delta) > 1f) {
        if (animate) {
            listState.animateScrollBy(
                value = delta,
                animationSpec = tween(
                    durationMillis = TV_LYRICS_SCROLL_ANIMATION_DURATION_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else {
            listState.scrollBy(delta)
        }
    }
}

internal fun shouldAnimateTvCenteredLyricsScroll(
    previousTargetIndex: Int?,
    targetIndex: Int,
    isTargetVisible: Boolean,
): Boolean {
    return previousTargetIndex != null &&
        isTargetVisible &&
        abs(targetIndex - previousTargetIndex) <= TV_LYRICS_SMOOTH_SCROLL_MAX_INDEX_DISTANCE
}

private fun resolveTvCenteredLyricsScrollTarget(
    lyrics: LyricsDocument?,
    visibleLines: List<TvCenteredLyricsLine>,
    highlightedVisibleIndex: Int,
): Int? {
    if (lyrics == null || visibleLines.isEmpty()) return null
    return when (highlightedVisibleIndex) {
        in visibleLines.indices -> highlightedVisibleIndex
        else -> if (lyrics.isSynced) 0 else null
    }
}

private fun resolveTvCenteredLyricsHighlightedIndex(
    visibleLines: List<TvCenteredLyricsLine>,
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

private data class TvCenteredLyricsLine(
    val rawIndex: Int,
    val text: String,
)
