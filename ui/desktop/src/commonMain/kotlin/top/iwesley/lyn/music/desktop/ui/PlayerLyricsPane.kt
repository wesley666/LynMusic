package top.iwesley.lyn.music.desktop.ui

import top.iwesley.lyn.music.*

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.uiPlural
import top.iwesley.lyn.music.core.model.UiText

import top.iwesley.lyn.music.core.model.sourceNameUiText
import top.iwesley.lyn.music.core.model.uiText

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.DEFAULT_LYRICS_SHARE_FONT_KEY
import top.iwesley.lyn.music.core.model.androidLyricsShareBuiltInFontNameText
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
import top.iwesley.lyn.music.core.model.LyricsShareArtworkTintSpec
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsShareCardSpec
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.LyricsShareTemplate
import top.iwesley.lyn.music.core.model.PlaybackArtworkBackgroundPalette
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildLyricsShareTitleArtistLine
import top.iwesley.lyn.music.core.model.parseLyricsShareImportedFontHash
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.domain.parseEnhancedLyricsPresentation
import top.iwesley.lyn.music.desktop.ui.platform.PlatformBackHandler
import top.iwesley.lyn.music.platform.lyricsSharePreviewFontFamily
import top.iwesley.lyn.music.platform.rememberPlatformArtworkBitmap
import top.iwesley.lyn.music.ui.mainShellColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun PlayerLyricsPane(
    state: PlayerState,
    track: Track,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onOpenLibraryNavigationTarget: ((LibraryNavigationTarget) -> Unit)? = null,
    onlineNavigationSourceId: String? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    pure: Boolean = false,
    mobilePlayback: Boolean = false,
) {
    val listState = key(track.id) { rememberLazyListState() }
    val lyricsFont = rememberPlayerLyricsFont(state, onPlayerIntent)
    val lyricsStyle = state.lyricsStyle
    val lyricsPrimaryTextColor = Color.White
    val lyricsSecondaryTextColor = Color.White.copy(alpha = 0.6f)
    var showTrackInfoDialog by rememberSaveable(track.id, mobilePlayback) { mutableStateOf(false) }
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
    var isLyricsBrowsing by remember(track.id, lyrics) { mutableStateOf(false) }
    var isAutoScrollingLyrics by remember(track.id, lyrics) { mutableStateOf(false) }
    var previousAutoScrollTargetIndex by remember(track.id, lyrics) { mutableStateOf<Int?>(null) }
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
        track = track,
        line = visibleLyricsLines.getOrNull(highlightedVisibleIndex)?.enhancedLine,
        isLineVisible = isPlaybackLineVisible,
        isBrowsing = isLyricsBrowsing,
    )
    val lyricsPosition = remember(fillClock, lyrics?.offsetMs) {
        { fillClock.positionMs + (lyrics?.offsetMs ?: 0L) }
    }
    LaunchedEffect(
        track.id,
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
    LaunchedEffect(track.id, lyrics, state.highlightedLineIndex, visibleLyricsLines, isLyricsBrowsing, lyricsStyle.showTranslation) {
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        state.snapshot.currentDisplayTitle,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = lyricsPrimaryTextColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (onOpenLibraryNavigationTarget != null) {
                        PlayerLyricsMetadataRow(
                            snapshot = state.snapshot,
                            track = track,
                            secondaryTextColor = lyricsSecondaryTextColor,
                            onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
                            onlineNavigationSourceId = onlineNavigationSourceId,
                            showTrackInfo = true,
                            onShowTrackInfo = { showTrackInfoDialog = true },
                        )
                    } else {
                        PlayerLyricsPlainMetadataRow(
                            text = uiString(Res.string.lyrics_share_album_artist_summary, state.snapshot.currentDisplayAlbumTitle ?: uiString(Res.string.source_local_track_label), state.snapshot.currentDisplayArtistName ?: uiString(Res.string.common_unknown_artist)),
                            secondaryTextColor = lyricsSecondaryTextColor,
                            showTrackInfo = true,
                            onShowTrackInfo = { showTrackInfoDialog = true },
                        )
                    }
                }
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
                    EmptyStateCard(
                        title = uiString(Res.string.lyrics_share_lyrics_missing),
                        body = uiString(Res.string.lyrics_share_fetch_priority_hint),
                    )
                }
            } else if (lyrics != null && visibleLyricsLines.isNotEmpty()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val centerPadding =
                        (maxHeight / 2 - 36.dp).coerceAtLeast(if (compact) 56.dp else 86.dp)
                    val browseSeekPositionMs = resolvePlayerLyricsSeekPositionMs(
                        line = visibleLyricsLines.getOrNull(browseTargetIndex ?: -1),
                        lyricsOffsetMs = lyrics.offsetMs,
                        durationMs = state.effectiveSnapshot.durationMs,
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
                                onPlayerIntent(PlayerIntent.SeekTo(browseSeekPositionMs))
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
    if (showTrackInfoDialog) {
        PlayerTrackInfoDialog(
            track = track,
            onDismiss = { showTrackInfoDialog = false },
        )
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

@Composable
private fun PlayerLyricsPlainMetadataRow(
    text: String,
    secondaryTextColor: Color,
    showTrackInfo: Boolean,
    onShowTrackInfo: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f, fill = !showTrackInfo),
            color = secondaryTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showTrackInfo) {
            PlayerTrackInfoButton(
                tint = secondaryTextColor,
                onClick = onShowTrackInfo,
            )
        }
    }
}

@Composable
private fun PlayerLyricsMetadataRow(
    snapshot: top.iwesley.lyn.music.core.model.PlaybackSnapshot,
    track: Track,
    secondaryTextColor: Color,
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    onlineNavigationSourceId: String?,
    showTrackInfo: Boolean,
    onShowTrackInfo: () -> Unit,
) {
    val navigationTargets = remember(
        snapshot.currentDisplayAlbumTitle,
        snapshot.currentDisplayArtistName,
        track.albumTitle,
        track.artistName,
        track.albumId,
        track.artistId,
        track.artworkLocator,
        onlineNavigationSourceId,
    ) {
        if (onlineNavigationSourceId != null) {
            deriveOnlinePlaybackLibraryNavigationTargets(
                snapshot = snapshot,
                track = track,
                sourceId = onlineNavigationSourceId,
            )
        } else {
            derivePlaybackLibraryNavigationTargets(snapshot, track)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesktopLyricsMetadataItem(
            label = uiString(Res.string.lyrics_share_artist_label),
            text = resolvePlayerLyricsMetadataLabel(
                primary = snapshot.currentDisplayArtistName,
                fallback = track.artistName,
                fallbackLabel = uiString(Res.string.common_unknown_artist),
            ),
            target = navigationTargets.artistTarget,
            secondaryTextColor = secondaryTextColor,
            onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
            modifier = Modifier.widthIn(max = 260.dp),
        )
        DesktopLyricsMetadataItem(
            label = uiString(Res.string.lyrics_share_album_label),
            text = resolvePlayerLyricsMetadataLabel(
                primary = snapshot.currentDisplayAlbumTitle,
                fallback = track.albumTitle,
                fallbackLabel = uiString(Res.string.source_local_track_label),
            ),
            target = navigationTargets.albumTarget,
            secondaryTextColor = secondaryTextColor,
            onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
            modifier = Modifier.weight(1f, fill = !showTrackInfo),
        )
        if (showTrackInfo) {
            PlayerTrackInfoButton(
                tint = secondaryTextColor,
                onClick = onShowTrackInfo,
            )
        }
    }
}

@Composable
private fun PlayerTrackInfoButton(
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = uiString(Res.string.lyrics_share_view_track_information),
            modifier = Modifier.size(18.dp),
            tint = tint,
        )
    }
}

@Composable
private fun PlayerTrackInfoDialog(
    track: Track,
    onDismiss: () -> Unit,
) {
    val shellColors = mainShellColors
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = shellColors.secondaryText
    val audioQuality = formatTrackAudioQuality(track)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = shellColors.cardContainer,
        titleContentColor = primaryTextColor,
        textContentColor = primaryTextColor,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = uiString(Res.string.lyrics_share_track_information),
                color = primaryTextColor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerTrackInfoDialogLine(
                    label = uiString(Res.string.lyrics_share_format_label),
                    value = trackDisplayFormat(track),
                    labelColor = secondaryTextColor,
                    valueColor = primaryTextColor,
                )
                audioQuality?.let { quality ->
                    PlayerTrackInfoDialogLine(
                        label = uiString(Res.string.lyrics_share_quality_label),
                        value = quality,
                        labelColor = secondaryTextColor,
                        valueColor = primaryTextColor,
                    )
                }
                PlayerTrackInfoDialogLine(
                    label = uiString(Res.string.lyrics_share_size_label),
                    value = formatTrackSize(track.sizeBytes),
                    labelColor = secondaryTextColor,
                    valueColor = primaryTextColor,
                )
                PlayerTrackInfoDialogLine(
                    label = uiString(Res.string.common_path),
                    value = track.relativePath.ifBlank { uiString(Res.string.common_unknown) },
                    labelColor = secondaryTextColor,
                    valueColor = primaryTextColor,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(uiString(Res.string.common_close))
            }
        },
    )
}

@Composable
private fun PlayerTrackInfoDialogLine(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = labelColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DesktopLyricsMetadataItem(
    label: String,
    text: String,
    target: LibraryNavigationTarget?,
    secondaryTextColor: Color,
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = secondaryTextColor,
            maxLines = 1,
        )
        Text(
            text = text,
            modifier = if (target != null) {
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenLibraryNavigationTarget(target) }
            } else {
                Modifier
            },
            color = secondaryTextColor,
            fontWeight = if (target != null) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun resolvePlayerLyricsMetadataLabel(
    primary: String?,
    fallback: String?,
    fallbackLabel: String,
): String {
    return primary?.trim()?.takeIf { it.isNotBlank() }
        ?: fallback?.trim()?.takeIf { it.isNotBlank() }
        ?: fallbackLabel
}

internal fun resolveLyricsScrollTarget(
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
