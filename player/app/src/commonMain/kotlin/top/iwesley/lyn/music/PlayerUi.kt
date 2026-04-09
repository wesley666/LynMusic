package top.iwesley.lyn.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.platform.PlatformBackHandler
import top.iwesley.lyn.music.platform.rememberPlatformArtworkBitmap
import top.iwesley.lyn.music.ui.LynMusicTheme
import top.iwesley.lyn.music.ui.heroGlow
import top.iwesley.lyn.music.ui.mainShellColors

@Composable
internal fun PlayerDrawerHost(
    visible: Boolean,
    platform: PlatformDescriptor,
    logger: DiagnosticLogger,
    state: PlayerState,
    lyricsShareThemeTokens: AppThemeTokens,
    lyricsShareTextPalette: AppThemeTextPalette,
    onPlayerIntent: (PlayerIntent) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenAddToPlaylist: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(
                    durationMillis = 320,
                    easing = FastOutSlowInEasing,
                ),
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = 320,
                    easing = FastOutSlowInEasing,
                ),
            ),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(
                    durationMillis = 260,
                    easing = FastOutLinearInEasing,
                ),
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = 220,
                    easing = FastOutLinearInEasing,
                ),
            ),
        ) {
            PlayerOverlay(
                platform = platform,
                logger = logger,
                state = state,
                lyricsShareThemeTokens = lyricsShareThemeTokens,
                lyricsShareTextPalette = lyricsShareTextPalette,
                onPlayerIntent = onPlayerIntent,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onOpenAddToPlaylist = onOpenAddToPlaylist,
                onOpenQueue = onOpenQueue,
                onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
            )
        }
    }
}

@Composable
internal fun MiniPlayerBarVisibility(
    visible: Boolean,
    state: PlayerState,
    onPlayerIntent: (PlayerIntent) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenAddToPlaylist: () -> Unit,
    onOpenQueue: () -> Unit,
    compact: Boolean = false,
    mobile: Boolean = false,
    mobilePortraitMiniPlayer: Boolean = false,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(
                durationMillis = 220,
                delayMillis = 110,
                easing = FastOutSlowInEasing,
            ),
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = 160,
                delayMillis = 110,
                easing = FastOutSlowInEasing,
            ),
        ),
        exit = slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(
                durationMillis = 150,
                easing = FastOutLinearInEasing,
            ),
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = 110,
                easing = FastOutLinearInEasing,
            ),
        ),
    ) {
        MiniPlayerBar(
            state = state,
            onPlayerIntent = onPlayerIntent,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onOpenAddToPlaylist = onOpenAddToPlaylist,
            onOpenQueue = onOpenQueue,
            compact = compact,
            mobile = mobile,
            mobilePortraitMiniPlayer = mobilePortraitMiniPlayer,
        )
    }
}

@Composable
internal fun QueueDrawer(
    state: PlayerState,
    compact: Boolean,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.snapshot.currentTrack == null) return
    val shellColors = mainShellColors
    val listState = rememberLazyListState()
    PlatformBackHandler(
        enabled = state.isQueueVisible,
        onBack = { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(false)) },
    )
    LaunchedEffect(state.isQueueVisible, state.snapshot.currentIndex, state.snapshot.queue.size) {
        if (state.isQueueVisible && state.snapshot.currentIndex in state.snapshot.queue.indices) {
            listState.scrollToItem((state.snapshot.currentIndex - 2).coerceAtLeast(0))
        }
    }
    AnimatedVisibility(
        visible = state.isQueueVisible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(durationMillis = 220)),
        exit = fadeOut(animationSpec = tween(durationMillis = 180)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f))
                    .clickable { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(false)) },
            )
            Card(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(if (compact) 340.dp else 420.dp),
                shape = RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp),
                colors = CardDefaults.cardColors(containerColor = shellColors.cardContainer),
                border = BorderStroke(1.dp, shellColors.cardBorder),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("播放队列", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            Text(
                                "${state.snapshot.queue.size} 首 · ${modeLabel(state.snapshot.mode)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(false)) }) {
                            Text("关闭")
                        }
                    }
                    if (state.snapshot.queue.isEmpty()) {
                        EmptyStateCard(
                            title = "当前没有播放队列",
                            body = "从曲库或喜欢页播放歌曲后，这里会显示当前队列。",
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            itemsIndexed(state.snapshot.queue, key = { _, item -> item.id }) { index, track ->
                                QueueTrackRow(
                                    track = track,
                                    index = index,
                                    isCurrent = index == state.snapshot.currentIndex,
                                    isPlaying = index == state.snapshot.currentIndex && state.snapshot.isPlaying,
                                    onClick = { onPlayerIntent(PlayerIntent.PlayQueueIndex(index)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(
    state: PlayerState,
    onPlayerIntent: (PlayerIntent) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenAddToPlaylist: () -> Unit,
    onOpenQueue: () -> Unit,
    compact: Boolean = false,
    mobile: Boolean = false,
    mobilePortraitMiniPlayer: Boolean = false,
) {
    val snapshot = state.snapshot
    if (snapshot.currentTrack == null) return
    val miniPlayerLyricsText = rememberMiniPlayerLyricsText(state)
    if (mobile) {
        MobileMiniPlayerBar(
            snapshot = snapshot,
            lyricsText = miniPlayerLyricsText,
            showPortraitLyrics = mobilePortraitMiniPlayer,
            onPlayerIntent = onPlayerIntent,
            onOpenQueue = onOpenQueue,
        )
        return
    }
    val miniPlayerActionTint = LocalContentColor.current.takeOrElse { MaterialTheme.colorScheme.onSurface }
    val miniPlayerFavoriteTint = if (isFavorite) Color(0xFFE5484D) else miniPlayerActionTint
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 0.dp else 18.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.42f))
            .border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(28.dp),
            )
            .clickable { onPlayerIntent(PlayerIntent.ExpandedChanged(true)) }
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        VinylPlaceholder(
            vinylSize = 50.dp,
            artworkLocator = snapshot.currentDisplayArtworkLocator,
            spinning = snapshot.isPlaying,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = snapshot.currentDisplayTitle,
                    modifier = Modifier.widthIn(max = if (compact) 120.dp else 180.dp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = snapshot.currentDisplayArtistName ?: "未知艺人",
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatDuration(snapshot.positionMs)}/${formatDuration(snapshot.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                )
            }
            if (miniPlayerLyricsText != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = miniPlayerLyricsText,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MiniPlayerProgressScrubber(
                        snapshot = snapshot,
                        onPlayerIntent = onPlayerIntent,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                MiniPlayerProgressScrubber(
                    snapshot = snapshot,
                    onPlayerIntent = onPlayerIntent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        FavoriteToggleButton(
            isFavorite = isFavorite,
            onClick = onToggleFavorite,
            tint = miniPlayerFavoriteTint,
        )
        AddToPlaylistButton(onClick = onOpenAddToPlaylist, tint = miniPlayerActionTint)
        QueueToggleButton(onClick = onOpenQueue, tint = miniPlayerActionTint)
        IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipPrevious) }) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = null)
        }
        IconButton(onClick = { onPlayerIntent(PlayerIntent.TogglePlayPause) }) {
            Icon(
                imageVector = if (snapshot.isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
            )
        }
        IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipNext) }) {
            Icon(Icons.Rounded.SkipNext, contentDescription = null)
        }
    }
}

@Composable
private fun MobileMiniPlayerBar(
    snapshot: PlaybackSnapshot,
    lyricsText: String?,
    showPortraitLyrics: Boolean,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onOpenQueue: () -> Unit,
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 72.dp.toPx() }
    val maxVisualOffsetPx = with(density) { 84.dp.toPx() }
    var dragOffsetPx by remember(snapshot.currentTrack?.id) { mutableStateOf(0f) }
    val showLyrics = showPortraitLyrics && !lyricsText.isNullOrBlank()
    val animatedDragOffsetPx by animateFloatAsState(
        targetValue = dragOffsetPx,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "mobile-mini-player-drag-offset",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.42f))
            .border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(26.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .offset { IntOffset(animatedDragOffsetPx.roundToInt(), 0) }
                .pointerInput(snapshot.currentTrack?.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount ->
                            dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(
                                -maxVisualOffsetPx,
                                maxVisualOffsetPx,
                            )
                        },
                        onDragEnd = {
                            val finalOffset = dragOffsetPx
                            dragOffsetPx = 0f
                            when {
                                finalOffset <= -swipeThresholdPx -> onPlayerIntent(PlayerIntent.SkipNext)
                                finalOffset >= swipeThresholdPx -> onPlayerIntent(PlayerIntent.SkipPrevious)
                            }
                        },
                        onDragCancel = { dragOffsetPx = 0f },
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onPlayerIntent(PlayerIntent.ExpandedChanged(true)) }
                .padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VinylPlaceholder(
                vinylSize = 46.dp,
                artworkLocator = snapshot.currentDisplayArtworkLocator,
                spinning = snapshot.isPlaying,
            )
            if (showLyrics) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(0.56f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = snapshot.currentDisplayTitle,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.96f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = snapshot.currentDisplayArtistName ?: "未知艺人",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = lyricsText.orEmpty(),
                        modifier = Modifier.weight(0.44f),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = snapshot.currentDisplayTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.96f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = snapshot.currentDisplayArtistName ?: "未知艺人",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        QueueToggleButton(
            onClick = onOpenQueue,
            tint = Color.White.copy(alpha = 0.96f),
            buttonSize = 46.dp,
            iconSize = 25.dp,
        )
        IconButton(
            onClick = { onPlayerIntent(PlayerIntent.TogglePlayPause) },
            modifier = Modifier.size(50.dp),
        ) {
            Icon(
                imageVector = if (snapshot.isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.96f),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun rememberMiniPlayerLyricsText(state: PlayerState): String? {
    return remember(
        state.lyrics,
        state.highlightedLineIndex,
        state.isLyricsLoading,
    ) {
        resolveMiniPlayerLyricsText(
            lyrics = state.lyrics,
            highlightedLineIndex = state.highlightedLineIndex,
            isLyricsLoading = state.isLyricsLoading,
        )
    }
}

internal fun isMobilePortraitMiniPlayerLayout(
    maxWidth: Dp,
    maxHeight: Dp,
): Boolean {
    return maxHeight >= maxWidth
}

internal fun resolveMiniPlayerLyricsText(
    lyrics: LyricsDocument?,
    highlightedLineIndex: Int,
    isLyricsLoading: Boolean,
): String? {
    val highlighted = lyrics
        ?.lines
        ?.getOrNull(highlightedLineIndex)
        ?.text
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    if (highlighted != null) {
        return highlighted
    }
    val fallback = lyrics
        ?.lines
        ?.firstOrNull { line -> line.text.trim().isNotEmpty() }
        ?.text
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    return fallback ?: if (isLyricsLoading) "正在准备歌词" else null
}

@Composable
private fun MiniPlayerPlaybackProgress(
    snapshot: PlaybackSnapshot,
    modifier: Modifier = Modifier,
) {
    val duration = snapshot.durationMs.coerceAtLeast(1L)
    val progressFraction = (snapshot.positionMs.coerceIn(0L, duration).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    RoundedSliderTrack(
        progressFraction = progressFraction,
        modifier = modifier.height(8.dp),
        trackHeightPx = with(LocalDensity.current) { 3.dp.toPx() },
    )
}

@Composable
private fun MiniPlayerProgressScrubber(
    snapshot: PlaybackSnapshot,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = snapshot.durationMs.coerceAtLeast(1L)
    Box(
        modifier = modifier.height(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        MiniPlayerPlaybackProgress(
            snapshot = snapshot,
            modifier = Modifier.fillMaxWidth(),
        )
        Slider(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .graphicsLayer(scaleY = 0.36f),
            colors = transparentTrackSliderColors(),
            value = snapshot.positionMs.coerceIn(0L, duration).toFloat(),
            onValueChange = { onPlayerIntent(PlayerIntent.SeekTo(it.toLong())) },
            valueRange = 0f..duration.toFloat(),
        )
    }
}

@Composable
private fun PlayerOverlay(
    platform: PlatformDescriptor,
    logger: DiagnosticLogger,
    state: PlayerState,
    lyricsShareThemeTokens: AppThemeTokens,
    lyricsShareTextPalette: AppThemeTextPalette,
    onPlayerIntent: (PlayerIntent) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenAddToPlaylist: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
) {
    val track = state.snapshot.currentTrack ?: return
    PlatformBackHandler(onBack = { onPlayerIntent(PlayerIntent.ExpandedChanged(false)) })
    val defaultBackgroundColor = Color(0xFF232325)
    val playbackStatusColor = Color.White.copy(alpha = 0.6f)
    val artworkBitmap = rememberPlatformArtworkBitmap(state.snapshot.currentDisplayArtworkLocator)
    val artworkPalette = rememberVinylArtworkPalette(
        artworkBitmap = artworkBitmap,
        enabled = true,
    )
    val backgroundTopTint by animateColorAsState(
        targetValue = artworkPalette?.innerGlowColor?.copy(alpha = 0.22f) ?: Color.Transparent,
        label = "player-background-top-tint",
    )
    val backgroundMidTint by animateColorAsState(
        targetValue = artworkPalette?.glowColor?.copy(alpha = 0.18f) ?: Color.Transparent,
        label = "player-background-mid-tint",
    )
    val backgroundAccentTint by animateColorAsState(
        targetValue = artworkPalette?.rimColor?.copy(alpha = 0.12f) ?: Color.Transparent,
        label = "player-background-accent-tint",
    )
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = defaultBackgroundColor,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                backgroundTopTint,
                                backgroundMidTint,
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                backgroundAccentTint,
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            val wide = maxWidth >= 980.dp
            val useTapToRevealLyrics =
                isMobilePlaybackPlatform(platform) &&
                    maxHeight >= maxWidth
            LaunchedEffect(platform.name, maxWidth, maxHeight) {
                logger.debug(PLAYER_UI_LOG_TAG) {
                    "platform=${platform.name} maxWidth=$maxWidth maxHeight=$maxHeight wide=$wide useTapToRevealLyrics=$useTapToRevealLyrics"
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 26.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = { onPlayerIntent(PlayerIntent.ExpandedChanged(false)) }) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "收起播放页",
                            tint = Color.White.copy(alpha = 0.92f),
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    if (wide) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = playbackModeIcon(state.snapshot.mode),
                                contentDescription = null,
                                tint = playbackStatusColor,
                            )
                            Text(
                                text = modeLabel(state.snapshot.mode),
                                color = playbackStatusColor,
                            )
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { onPlayerIntent(PlayerIntent.OpenLyricsShare) },
                                enabled = state.lyrics != null && !state.isLyricsLoading,
                                modifier = Modifier.size(52.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = "分享歌词",
                                    tint = if (state.lyrics != null && !state.isLyricsLoading) {
                                        Color.White.copy(alpha = 0.92f)
                                    } else {
                                        Color.White.copy(alpha = 0.42f)
                                    },
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            IconButton(
                                onClick = { onPlayerIntent(PlayerIntent.OpenManualLyricsSearch) },
                                modifier = Modifier.size(52.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "手动搜索",
                                    tint = Color.White.copy(alpha = 0.92f),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
                if (useTapToRevealLyrics) {
                    MobilePlayerPrimaryPane(
                        state = state,
                        track = track,
                        onPlayerIntent = onPlayerIntent,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                } else if (wide) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(34.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerInfoPane(
                            snapshot = state.snapshot,
                            track = track,
                            modifier = Modifier
                                .weight(0.46f)
                                .fillMaxHeight(),
                        )
                        PlayerLyricsPane(
                            state = state,
                            track = track,
                            onPlayerIntent = onPlayerIntent,
                            onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
                            modifier = Modifier
                                .weight(0.54f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        PlayerInfoPane(
                            snapshot = state.snapshot,
                            track = track,
                            modifier = Modifier.fillMaxWidth(),
                            compact = true,
                        )
                        PlayerLyricsPane(
                            state = state,
                            track = track,
                            onPlayerIntent = onPlayerIntent,
                            modifier = Modifier.weight(1f),
                            compact = true,
                        )
                    }
                }
                PlayerBottomControls(
                    snapshot = state.snapshot,
                    track = track,
                    mobilePlayback = isMobilePlaybackPlatform(platform),
                    wide = wide,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onOpenAddToPlaylist = onOpenAddToPlaylist,
                    onOpenQueue = onOpenQueue,
                    onPlayerIntent = onPlayerIntent,
                    onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
                )
            }
            if (state.isLyricsShareVisible) {
                LynMusicTheme(
                    themeTokens = lyricsShareThemeTokens,
                    textPalette = lyricsShareTextPalette,
                ) {
                    LyricsShareOverlay(
                        platform = platform,
                        state = state,
                        onPlayerIntent = onPlayerIntent,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MobilePlayerPrimaryPane(
    state: PlayerState,
    track: Track,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lyricsVisible by rememberSaveable(track.id) { mutableStateOf(false) }

    if (lyricsVisible) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { lyricsVisible = false },
        ) {
            PlayerLyricsPane(
                state = state,
                track = track,
                onPlayerIntent = onPlayerIntent,
                modifier = Modifier.fillMaxSize(),
                compact = true,
            )
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(30.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { lyricsVisible = true },
    ) {
        PlayerInfoPane(
            snapshot = state.snapshot,
            track = track,
            modifier = Modifier.fillMaxSize(),
            compact = true,
        )
    }
}

internal fun isMobilePlaybackPlatform(platform: PlatformDescriptor): Boolean {
    return platform.name == "Android" || platform.name == "iPhone / iPad"
}

@Composable
private fun PlayerInfoPane(
    snapshot: PlaybackSnapshot,
    track: Track,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        )
        VinylPlaceholder(
            vinylSize = if (compact) 300.dp else 420.dp,
            artworkLocator = snapshot.currentDisplayArtworkLocator,
            spinning = snapshot.isPlaying,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun PlayerBottomControls(
    snapshot: PlaybackSnapshot,
    track: Track,
    mobilePlayback: Boolean,
    wide: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenAddToPlaylist: () -> Unit,
    onOpenQueue: () -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
) {
    val favoriteTint = if (isFavorite) Color(0xFFE5484D) else Color.White.copy(alpha = 0.96f)
    val modeButtonSize = 42.dp
    val modeIconSize = 22.dp
    val skipButtonSize = 45.dp
    val skipIconSize = 27.dp
    val playButtonSize = 60.dp
    val playIconSize = 42.dp
    if (!wide) {
        val artistNavigationTarget = remember(
            mobilePlayback,
            snapshot.currentDisplayArtistName,
            track.artistName,
        ) {
            if (!mobilePlayback) {
                null
            } else {
                derivePlaybackLibraryNavigationTargets(snapshot, track).artistTarget
            }
        }
        val mobileTopActionButtonSize = 58.dp
        val mobileTopActionIconSize = 30.dp
        val mobileBottomActionButtonSize = 62.dp
        val mobileBottomActionIconSize = 32.dp
        val mobileModeButtonSize = 54.dp
        val mobileModeIconSize = 26.dp
        val mobileSkipButtonSize = 58.dp
        val mobileSkipIconSize = 32.dp
        val mobilePlayButtonSize = 76.dp
        val mobilePlayIconSize = 52.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 5.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = snapshot.currentDisplayTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = 0.96f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = snapshot.currentDisplayArtistName ?: "未知艺人",
                        modifier = if (artistNavigationTarget != null) {
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenLibraryNavigationTarget(artistNavigationTarget) }
                        } else {
                            Modifier
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AddToPlaylistButton(
                        onClick = onOpenAddToPlaylist,
                        tint = Color.White.copy(alpha = 0.96f),
                        buttonSize = mobileTopActionButtonSize,
                        iconSize = mobileTopActionIconSize,
                    )
                    FavoriteToggleButton(
                        isFavorite = isFavorite,
                        onClick = onToggleFavorite,
                        tint = favoriteTint,
                        buttonSize = mobileTopActionButtonSize,
                        iconSize = mobileTopActionIconSize,
                    )
                }
            }
            PlaybackProgress(
                snapshot = snapshot,
                onPlayerIntent = onPlayerIntent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                showTimeLabels = false,
                floating = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatDuration(snapshot.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
                Text(
                    text = formatDuration(snapshot.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onPlayerIntent(PlayerIntent.CycleMode) }, modifier = Modifier.size(mobileModeButtonSize)) {
                    Icon(
                        imageVector = playbackModeIcon(snapshot.mode),
                        contentDescription = null,
                        modifier = Modifier.size(mobileModeIconSize),
                        tint = Color.White.copy(alpha = 0.92f),
                    )
                }
                IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipPrevious) }, modifier = Modifier.size(mobileSkipButtonSize)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = null,
                        modifier = Modifier.size(mobileSkipIconSize),
                        tint = Color.White.copy(alpha = 0.92f),
                    )
                }
                IconButton(onClick = { onPlayerIntent(PlayerIntent.TogglePlayPause) }, modifier = Modifier.size(mobilePlayButtonSize)) {
                    Icon(
                        imageVector = if (snapshot.isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(mobilePlayIconSize),
                        tint = Color.White.copy(alpha = 0.96f),
                    )
                }
                IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipNext) }, modifier = Modifier.size(mobileSkipButtonSize)) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = null,
                        modifier = Modifier.size(mobileSkipIconSize),
                        tint = Color.White.copy(alpha = 0.92f),
                    )
                }
                QueueToggleButton(
                    onClick = onOpenQueue,
                    tint = Color.White.copy(alpha = 0.96f),
                    buttonSize = mobileBottomActionButtonSize,
                    iconSize = mobileBottomActionIconSize,
                )
            }
        }
        return
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatDuration(snapshot.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
                Text(
                    text = formatDuration(snapshot.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = buildString {
                            append(snapshot.currentDisplayTitle)
                            append("  ")
                            append(snapshot.currentDisplayArtistName ?: "未知艺人")
                            append("  ")
                            append(track.sourceId.substringBefore('-').uppercase())
                        },
                        modifier = Modifier.weight(0.30f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.weight(0.40f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { onPlayerIntent(PlayerIntent.CycleMode) }, modifier = Modifier.size(modeButtonSize)) {
                            Icon(
                                imageVector = playbackModeIcon(snapshot.mode),
                                contentDescription = null,
                                modifier = Modifier.size(modeIconSize),
                                tint = Color.White.copy(alpha = 0.92f),
                            )
                        }
                        IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipPrevious) }, modifier = Modifier.size(skipButtonSize)) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = null,
                                modifier = Modifier.size(skipIconSize),
                                tint = Color.White.copy(alpha = 0.92f),
                            )
                        }
                        IconButton(onClick = { onPlayerIntent(PlayerIntent.TogglePlayPause) }, modifier = Modifier.size(playButtonSize)) {
                            Icon(
                                imageVector = if (snapshot.isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                                contentDescription = null,
                                modifier = Modifier.size(playIconSize),
                                tint = Color.White.copy(alpha = 0.96f),
                            )
                        }
                        IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipNext) }, modifier = Modifier.size(skipButtonSize)) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = null,
                                modifier = Modifier.size(skipIconSize),
                                tint = Color.White.copy(alpha = 0.92f),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.weight(0.30f),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QueueToggleButton(
                            onClick = onOpenQueue,
                            tint = Color.White.copy(alpha = 0.96f),
                        )
                        AddToPlaylistButton(
                            onClick = onOpenAddToPlaylist,
                            tint = Color.White.copy(alpha = 0.96f),
                        )
                        FavoriteToggleButton(
                            isFavorite = isFavorite,
                            onClick = onToggleFavorite,
                            tint = favoriteTint,
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            PlaybackVolume(snapshot, onPlayerIntent, sliderWidthFraction = 0.5f)
                        }
                    }
                }
            }
        }
        PlaybackProgress(
            snapshot = snapshot,
            onPlayerIntent = onPlayerIntent,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 5.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            showTimeLabels = false,
            floating = true,
        )
    }
}

@Composable
private fun QueueToggleButton(
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    buttonSize: androidx.compose.ui.unit.Dp = 48.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(buttonSize)) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = "播放队列",
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun AddToPlaylistButton(
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    buttonSize: androidx.compose.ui.unit.Dp = 48.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(buttonSize)) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "加入歌单",
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun PlaybackProgress(
    snapshot: PlaybackSnapshot,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
    showTimeLabels: Boolean = true,
    floating: Boolean = false,
) {
    val duration = snapshot.durationMs.coerceAtLeast(1L)
    val progressFraction = (snapshot.positionMs.coerceIn(0L, duration).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val particles = remember { mutableStateListOf<ProgressFlowerParticle>() }
    var lastEmissionFraction by remember { mutableStateOf(progressFraction) }
    var lastEmissionNanos by remember { mutableStateOf(0L) }
    var animationFrameNanos by remember { mutableStateOf(0L) }
    LaunchedEffect(snapshot.positionMs, snapshot.isPlaying, duration) {
        val currentFraction = progressFraction
        if (currentFraction < lastEmissionFraction) {
            lastEmissionFraction = currentFraction
            lastEmissionNanos = 0L
            return@LaunchedEffect
        }
        if (!snapshot.isPlaying || duration <= 1L || currentFraction <= 0f) {
            lastEmissionFraction = currentFraction
            return@LaunchedEffect
        }
        val now = withFrameNanos { it }
        val movedEnough = currentFraction - lastEmissionFraction >= 0.018f
        val throttled = lastEmissionNanos == 0L || now - lastEmissionNanos >= 240_000_000L
        if (movedEnough && throttled) {
            val seed = (now % Int.MAX_VALUE.toLong()).toInt().absoluteValue
            repeat(3) { index ->
                particles += buildProgressFlowerParticle(
                    progressFraction = currentFraction,
                    seed = seed + index * 97,
                    bornAtNanos = now,
                )
            }
            lastEmissionFraction = currentFraction
            lastEmissionNanos = now
        }
    }
    LaunchedEffect(particles.size) {
        if (particles.isEmpty()) return@LaunchedEffect
        while (particles.isNotEmpty()) {
            withFrameNanos { now ->
                animationFrameNanos = now
                particles.removeAll { particle -> now - particle.bornAtNanos >= FLOWER_PARTICLE_LIFETIME_NANOS }
            }
        }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (floating) 12.dp else 22.dp),
        ) {
            val thumbInsetPx = with(LocalDensity.current) { if (floating) 6.dp.toPx() else 8.dp.toPx() }
            val sliderAlignment = if (floating) Alignment.Center else Alignment.BottomCenter
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (floating) 0.dp else 4.dp),
            ) {
                particles.forEach { particle ->
                    drawProgressFlowerParticle(
                        particle = particle,
                        nowNanos = animationFrameNanos,
                        thumbInsetPx = thumbInsetPx,
                    )
                }
            }
            RoundedSliderTrack(
                progressFraction = progressFraction,
                modifier = Modifier
                    .align(sliderAlignment)
                    .fillMaxWidth()
                    .height(8.dp),
                trackHeightPx = with(LocalDensity.current) { if (floating) 3.dp.toPx() else 4.dp.toPx() },
            )
            Slider(
                modifier = Modifier
                    .align(sliderAlignment)
                    .fillMaxWidth()
                    .height(8.dp)
                    .graphicsLayer(scaleY = if (floating) 0.36f else 0.44f),
                colors = transparentTrackSliderColors(),
                value = snapshot.positionMs.coerceIn(0L, duration).toFloat(),
                onValueChange = { onPlayerIntent(PlayerIntent.SeekTo(it.toLong())) },
                valueRange = 0f..duration.toFloat(),
            )
        }
        if (showTimeLabels) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDuration(snapshot.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
                Text(
                    text = formatDuration(snapshot.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun RoundedSliderTrack(
    progressFraction: Float,
    modifier: Modifier = Modifier,
    trackHeightPx: Float,
) {
    Canvas(modifier = modifier) {
        val trackWidth = size.width.coerceAtLeast(0f)
        if (trackWidth <= 0f || trackHeightPx <= 0f) return@Canvas
        val top = (size.height - trackHeightPx) / 2f
        val radius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.24f),
            topLeft = Offset(0f, top),
            size = Size(trackWidth, trackHeightPx),
            cornerRadius = radius,
        )
        val activeWidth = (trackWidth * progressFraction.coerceIn(0f, 1f)).coerceIn(0f, trackWidth)
        if (activeWidth > 0f) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.96f),
                topLeft = Offset(0f, top),
                size = Size(activeWidth, trackHeightPx),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun PlaybackVolume(
    snapshot: PlaybackSnapshot,
    onPlayerIntent: (PlayerIntent) -> Unit,
    sliderWidthFraction: Float = 1f,
) {
    val volume = snapshot.volume.coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${(volume * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            val sliderModifier =
                Modifier
                    .fillMaxWidth(sliderWidthFraction.coerceIn(0.2f, 1f))
                    .height(8.dp)
            RoundedSliderTrack(
                progressFraction = volume,
                modifier = sliderModifier,
                trackHeightPx = with(LocalDensity.current) { 4.dp.toPx() },
            )
            Slider(
                modifier = Modifier
                    .then(sliderModifier)
                    .graphicsLayer(scaleY = 0.44f),
                colors = transparentTrackSliderColors(),
                value = volume,
                onValueChange = { onPlayerIntent(PlayerIntent.SetVolume(it)) },
                valueRange = 0f..1f,
            )
        }
    }
}

@Composable
private fun playerSliderColors() = SliderDefaults.colors(
    thumbColor = Color.White.copy(alpha = 0.98f),
    activeTrackColor = Color.White.copy(alpha = 0.96f),
    inactiveTrackColor = Color.White.copy(alpha = 0.24f),
    activeTickColor = Color.White.copy(alpha = 0.96f),
    inactiveTickColor = Color.White.copy(alpha = 0.24f),
)

@Composable
private fun transparentTrackSliderColors() = SliderDefaults.colors(
    thumbColor = Color.White.copy(alpha = 0.98f),
    activeTrackColor = Color.Transparent,
    inactiveTrackColor = Color.Transparent,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

private data class ProgressFlowerParticle(
    val progressFraction: Float,
    val bornAtNanos: Long,
    val driftX: Float,
    val liftY: Float,
    val sway: Float,
    val bloomRadius: Float,
    val colorIndex: Int,
    val rotationOffset: Float,
)

private fun buildProgressFlowerParticle(
    progressFraction: Float,
    seed: Int,
    bornAtNanos: Long,
): ProgressFlowerParticle {
    val normalized = seed.absoluteValue
    return ProgressFlowerParticle(
        progressFraction = progressFraction,
        bornAtNanos = bornAtNanos,
        driftX = (((normalized % 24) - 12).toFloat()) * 2.1f,
        liftY = 22f + (normalized % 32).toFloat(),
        sway = 6f + (normalized % 9).toFloat(),
        bloomRadius = 3.6f + ((normalized % 4).toFloat() * 0.8f),
        colorIndex = normalized % FLOWER_PARTICLE_COLORS.size,
        rotationOffset = (normalized % 360).toFloat() * (PI.toFloat() / 180f),
    )
}

private fun DrawScope.drawProgressFlowerParticle(
    particle: ProgressFlowerParticle,
    nowNanos: Long,
    thumbInsetPx: Float,
) {
    if (nowNanos <= particle.bornAtNanos) return
    val age = ((nowNanos - particle.bornAtNanos).toFloat() / FLOWER_PARTICLE_LIFETIME_NANOS.toFloat()).coerceIn(0f, 1f)
    if (age >= 1f) return
    val easeOut = 1f - (1f - age) * (1f - age)
    val usableWidth = (size.width - thumbInsetPx * 2f).coerceAtLeast(0f)
    val baseX = thumbInsetPx + usableWidth * particle.progressFraction
    val baseY = size.height - 8.dp.toPx()
    val swayOffset = sin((age * 1.8f + particle.rotationOffset) * PI.toFloat()) * particle.sway
    val center = Offset(
        x = baseX + particle.driftX * easeOut + swayOffset,
        y = baseY - particle.liftY * easeOut,
    )
    val petalColor = FLOWER_PARTICLE_COLORS[particle.colorIndex].copy(alpha = (1f - age) * 0.92f)
    val petalOrbit = particle.bloomRadius * 1.55f
    val rotation = particle.rotationOffset + age * 2.4f
    repeat(5) { index ->
        val angle = rotation + index * ((2f * PI.toFloat()) / 5f)
        val petalCenter = Offset(
            x = center.x + cos(angle) * petalOrbit,
            y = center.y + sin(angle) * petalOrbit,
        )
        drawCircle(
            color = petalColor,
            radius = particle.bloomRadius,
            center = petalCenter,
        )
    }
    drawCircle(
        color = Color(0xFFFFF2A8).copy(alpha = (1f - age) * 0.98f),
        radius = particle.bloomRadius * 0.82f,
        center = center,
    )
}

private val FLOWER_PARTICLE_COLORS = listOf(
    Color(0xFFFF5D9E),
    Color(0xFFFFB341),
    Color(0xFF7DFF93),
    Color(0xFF68D5FF),
    Color(0xFFC792FF),
)

private const val FLOWER_PARTICLE_LIFETIME_NANOS = 900_000_000L
private const val PLAYER_UI_LOG_TAG = "PlayerUi"

@Composable
private fun QueueTrackRow(
    track: Track,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val shellColors = mainShellColors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isCurrent) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(
                    text = (index + 1).toString().padStart(2, '0'),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildString {
                        append(track.artistName ?: "未知艺人")
                        if (isCurrent) {
                            append(if (isPlaying) " · 正在播放" else " · 当前歌曲")
                        }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatDuration(track.durationMs),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 42.dp)
                .height(1.dp)
                .background(shellColors.cardBorder),
        )
    }
}
