package top.iwesley.lyn.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.PlayerLyricsStyle
import top.iwesley.lyn.music.domain.parseEnhancedLyricsPresentation
import top.iwesley.lyn.music.resources.*
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Hosts provide navigation/header content; all lyric interaction state lives here. */
@Composable
fun SharedPlayerLyricsPane(
    state: PlayerLyricsUiState,
    lyricsStyle: PlayerLyricsStyle,
    lyricsFont: FontFamily?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    pure: Boolean = false,
    header: @Composable () -> Unit,
    emptyState: @Composable () -> Unit,
) {
    val listState = key(state.trackId) { rememberLazyListState() }
    val lyricsPrimaryTextColor = Color.White
    val lyricsSecondaryTextColor = Color.White.copy(alpha = 0.6f)
    val lyrics = state.lyrics
    val enhancedLyricsPresentation = remember(lyrics) {
        lyrics?.let { document ->
            parseEnhancedLyricsPresentation(
                rawPayload = document.rawPayload,
                fallbackDocument = document,
            )
        }
    }
    val visibleLyricsLines = remember(lyrics, enhancedLyricsPresentation) {
        lyrics?.let { buildVisiblePlayerLyricsLines(it, enhancedLyricsPresentation) }.orEmpty()
    }
    val highlightedVisibleIndex = remember(visibleLyricsLines, state.highlightedLineIndex) {
        resolveVisiblePlayerLyricsHighlightedIndex(visibleLyricsLines, state.highlightedLineIndex)
    }
    var isLyricsBrowsing by remember(state.trackId, lyrics) { mutableStateOf(false) }
    var isAutoScrollingLyrics by remember(state.trackId, lyrics) { mutableStateOf(false) }
    var previousAutoScrollTargetIndex by remember(state.trackId, lyrics) { mutableStateOf<Int?>(null) }
    val browseTargetIndex by remember(listState, visibleLyricsLines) {
        derivedStateOf {
            resolvePlayerLyricsBrowseTargetIndex(
                visibleLines = visibleLyricsLines,
                visibleItems = listState.layoutInfo.visibleItemsInfo.map { item ->
                    PlayerLyricsVisibleItemInfo(
                        index = item.index,
                        offset = item.offset,
                        size = item.size,
                    )
                },
                viewportStartOffset = listState.layoutInfo.viewportStartOffset,
                viewportEndOffset = listState.layoutInfo.viewportEndOffset,
            )
        }
    }
    val isPlaybackLineVisible by remember(listState, highlightedVisibleIndex) {
        derivedStateOf {
            val layout = listState.layoutInfo
            layout.visibleItemsInfo.any { item ->
                item.index == highlightedVisibleIndex &&
                    item.offset < layout.viewportEndOffset &&
                    item.offset + item.size > layout.viewportStartOffset
            }
        }
    }
    val fillClock = rememberLyricsPlaybackClock(
        state = state,
        line = visibleLyricsLines.getOrNull(highlightedVisibleIndex)?.enhancedLine,
        isLineVisible = isPlaybackLineVisible,
        isBrowsing = isLyricsBrowsing,
    )
    val lyricsPosition = remember(fillClock, lyrics?.offsetMs) {
        { fillClock.positionMs + (lyrics?.offsetMs ?: 0L) }
    }
    LaunchedEffect(
        state.trackId,
        lyrics?.isSynced,
        visibleLyricsLines,
        listState.isScrollInProgress,
        isAutoScrollingLyrics,
    ) {
        if (lyrics?.isSynced != true || visibleLyricsLines.none { it.line.timestampMs != null }) {
            isLyricsBrowsing = false
            return@LaunchedEffect
        }
        when {
            listState.isScrollInProgress && !isAutoScrollingLyrics -> isLyricsBrowsing = true
            !listState.isScrollInProgress && isLyricsBrowsing -> {
                delay(2_400L)
                isLyricsBrowsing = false
            }
        }
    }
    LaunchedEffect(state.trackId, lyrics, state.highlightedLineIndex, visibleLyricsLines, isLyricsBrowsing, lyricsStyle.showTranslation) {
        if (isLyricsBrowsing) return@LaunchedEffect
        // Translation visibility changes row heights without changing the lyric document.
        // Read positions after remeasurement, and never take over an active user scroll.
        withFrameNanos { }
        if (listState.isScrollInProgress && !isAutoScrollingLyrics) return@LaunchedEffect
        val targetLyrics = lyrics ?: return@LaunchedEffect
        val targetIndex = resolveVisiblePlayerLyricsScrollTarget(
            lyrics = targetLyrics,
            visibleLines = visibleLyricsLines,
            highlightedRawIndex = state.highlightedLineIndex,
        ) ?: return@LaunchedEffect
        if (targetIndex !in visibleLyricsLines.indices) return@LaunchedEffect
        val isTargetVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
        val shouldAnimateScroll = shouldAnimatePlayerLyricsScroll(
            previousTargetIndex = previousAutoScrollTargetIndex,
            targetIndex = targetIndex,
            isTargetVisible = isTargetVisible,
        )
        previousAutoScrollTargetIndex = targetIndex
        isAutoScrollingLyrics = true
        try {
            if (!isTargetVisible) {
                listState.scrollToItem(targetIndex)
                withFrameNanos { }
            }
            val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                ?: return@LaunchedEffect
            val viewportCenter =
                (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
            val itemCenter = itemInfo.offset + itemInfo.size / 2
            val delta = (itemCenter - viewportCenter).toFloat()
            if (abs(delta) > 1f) {
                if (shouldAnimateScroll) {
                    listState.animateScrollBy(
                        value = delta,
                        animationSpec = tween(
                            durationMillis = PLAYER_LYRICS_SCROLL_ANIMATION_DURATION_MS,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                } else {
                    listState.scrollBy(delta)
                }
            }
        } finally {
            isAutoScrollingLyrics = false
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (compact) 8.dp else 12.dp,
                    vertical = if (compact) 8.dp else 14.dp
                ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (!compact && !pure) {
                header()
            }
            if (state.isLyricsLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        uiString(Res.string.lyrics_share_fetching_lyrics),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = lyricsSecondaryTextColor,
                    )
                }
            }
            if (
                shouldShowPlayerLyricsEmptyState(
                    isLyricsLoading = state.isLyricsLoading,
                    hasLyricsLookupCompleted = state.hasLyricsLookupCompleted,
                    lyrics = lyrics,
                    visibleLines = visibleLyricsLines,
                )
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    emptyState()
                }
            } else if (lyrics != null && visibleLyricsLines.isNotEmpty()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val centerPadding =
                        (maxHeight / 2 - 36.dp).coerceAtLeast(if (compact) 56.dp else 86.dp)
                    val browseSeekPositionMs = resolvePlayerLyricsSeekPositionMs(
                        line = visibleLyricsLines.getOrNull(browseTargetIndex ?: -1),
                        lyricsOffsetMs = lyrics.offsetMs,
                        durationMs = state.durationMs,
                    )
                    val activeHighlightedVisibleIndex = resolvePlayerLyricsActiveHighlightedIndex(
                        visibleLines = visibleLyricsLines,
                        playbackHighlightedIndex = highlightedVisibleIndex,
                        browseTargetIndex = browseTargetIndex,
                        isBrowsing = isLyricsBrowsing,
                    )
                    val lyricsMaxWidth = when {
                        compact -> 540.dp
                        pure -> 600.dp
                        else -> 520.dp
                    }
                    val lyricsLineSpacing = when {
                        compact -> 12.dp
                        pure -> 24.dp
                        else -> 16.dp
                    }
                    val translationLineSpacing = when {
                        compact -> 3.dp
                        pure -> 6.dp
                        else -> 4.dp
                    }
                    LazyColumn(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxHeight()
                            .widthIn(max = lyricsMaxWidth)
                            .fillMaxWidth(),
                        state = listState,
                        contentPadding = PaddingValues(vertical = centerPadding),
                        verticalArrangement = Arrangement.spacedBy(lyricsLineSpacing),
                    ) {
                        itemsIndexed(visibleLyricsLines) { index, visibleLine ->
                            val line = visibleLine.line
                            val enhancedLine = visibleLine.enhancedLine
                            val translationText = enhancedLyricsPresentation?.translationTextFor(
                                enhancedLine, lyricsStyle.showTranslation,
                            )
                            val isHighlighted = index == activeHighlightedVisibleIndex
                            val targetAlpha = when {
                                pure && activeHighlightedVisibleIndex < 0 -> 0.48f
                                activeHighlightedVisibleIndex < 0 -> 0.6f
                                isHighlighted -> 1f
                                pure -> 0.34f
                                else -> 0.58f
                            }
                            val targetScale = when {
                                highlightedVisibleIndex < 0 -> 1f
                                isHighlighted -> 1.08f
                                else -> 1f
                            }
                            val animatedAlpha by animateFloatAsState(targetValue = targetAlpha)
                            val animatedScale by animateFloatAsState(targetValue = targetScale)
                            val animatedColor by animateColorAsState(
                                targetValue = if (isHighlighted) {
                                    Color(lyricsStyle.highlightArgb)
                                } else {
                                    lyricsPrimaryTextColor
                                },
                            )
                            val lineModifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer(
                                    alpha = animatedAlpha,
                                    scaleX = animatedScale,
                                    scaleY = animatedScale,
                                )
                            val hasEnhancedSegments = enhancedLine?.segments?.isNotEmpty() == true
                            if (translationText != null) {
                                Column(
                                    modifier = lineModifier,
                                    verticalArrangement = Arrangement.spacedBy(translationLineSpacing),
                                ) {
                                    if (isHighlighted && hasEnhancedSegments && !isLyricsBrowsing) {
                                        EnhancedLyricsLineText(
                                            line = enhancedLine,
                                            currentPositionMs = lyricsPosition,
                                            activeColor = animatedColor,
                                            inactiveColor = lyricsSecondaryTextColor.copy(alpha = 0.78f),
                                            style = MaterialTheme.typography.headlineSmall.withPlayerLyricsStyle(lyricsStyle, lyricsFont),
                                            textAlign = TextAlign.Center,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    } else {
                                        Text(
                                            text = line.text,
                                            style = if (isHighlighted) MaterialTheme.typography.headlineSmall.withPlayerLyricsStyle(lyricsStyle, lyricsFont) else MaterialTheme.typography.titleLarge.withPlayerLyricsStyle(lyricsStyle, lyricsFont),
                                            color = animatedColor,
                                            textAlign = TextAlign.Center,
                                            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    Text(
                                        text = translationText,
                                        style = if (isHighlighted) MaterialTheme.typography.titleMedium.withPlayerLyricsStyle(lyricsStyle, lyricsFont) else MaterialTheme.typography.bodyLarge.withPlayerLyricsStyle(lyricsStyle, lyricsFont),
                                        color = lyricsSecondaryTextColor.copy(alpha = if (isHighlighted) 0.9f else 0.72f),
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Normal,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            } else if (isHighlighted && hasEnhancedSegments && !isLyricsBrowsing) {
                                EnhancedLyricsLineText(
                                    line = enhancedLine,
                                    currentPositionMs = lyricsPosition,
                                    activeColor = animatedColor,
                                    inactiveColor = lyricsSecondaryTextColor.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.headlineSmall.withPlayerLyricsStyle(lyricsStyle, lyricsFont),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    modifier = lineModifier,
                                )
                            } else {
                                Text(
                                    text = line.text,
                                    style = if (isHighlighted) MaterialTheme.typography.headlineSmall.withPlayerLyricsStyle(lyricsStyle, lyricsFont) else MaterialTheme.typography.titleLarge.withPlayerLyricsStyle(lyricsStyle, lyricsFont),
                                    color = animatedColor,
                                    textAlign = TextAlign.Center,
                                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                                    modifier = lineModifier,
                                )
                            }
                        }
                    }
                    if (isLyricsBrowsing && browseSeekPositionMs != null) {
                        PlayerLyricsSeekTimeButton(
                            timeText = formatDuration(browseSeekPositionMs),
                            compact = compact,
                            onClick = {
                                isLyricsBrowsing = false
                                onSeek(browseSeekPositionMs)
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = if (compact) 4.dp else 18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerLyricsSeekTimeButton(
    timeText: String,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semanticsLabel1 = uiString(Res.string.lyrics_share_seek_to_position, timeText)

    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = semanticsLabel1 }
            .padding(
                horizontal = if (compact) 9.dp else 11.dp,
                vertical = if (compact) 5.dp else 6.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 15.dp else 16.dp),
            tint = Color.White.copy(alpha = 0.72f),
        )
        Text(
            text = timeText,
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
