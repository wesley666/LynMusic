package top.iwesley.lyn.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.OfflineDownload
import top.iwesley.lyn.music.core.model.OfflineDownloadStatus
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaybackAudioFormat
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.parseNavidromeSongLocator
import top.iwesley.lyn.music.core.model.supportsOfflineDownload
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.automotive.AutomotiveLandscapePlayerOverlayContent
import top.iwesley.lyn.music.feature.offline.OfflineDownloadIntent
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.feature.player.SLEEP_TIMER_PRESET_MINUTES
import top.iwesley.lyn.music.feature.player.SleepTimerState
import top.iwesley.lyn.music.feature.player.normalizeSleepTimerMinutes
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
    showCompactPlayerLyrics: Boolean,
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
                showCompactPlayerLyrics = showCompactPlayerLyrics,
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
    drawerSide: QueueDrawerSide = QueueDrawerSide.End,
) {
    if (state.snapshot.currentTrack == null) return
    val shellColors = mainShellColors
    val listState = rememberLazyListState()
    LaunchedEffect(state.isQueueVisible, state.snapshot.currentIndex, state.snapshot.queue.size) {
        if (state.isQueueVisible && state.snapshot.currentIndex in state.snapshot.queue.indices) {
            listState.scrollToItem((state.snapshot.currentIndex - 2).coerceAtLeast(0))
        }
    }
    if (state.isQueueVisible) {
        // Register this only while visible so it takes precedence over the underlying player overlay.
        PlatformBackHandler(
            onBack = { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(false)) },
        )
    }
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.isQueueVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = 220)),
            exit = fadeOut(animationSpec = tween(durationMillis = 180)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f))
                    .clickable { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(false)) },
            )
        }
        AnimatedVisibility(
            visible = state.isQueueVisible,
            modifier = Modifier.align(queueDrawerAlignment(drawerSide)),
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> queueDrawerHorizontalSlideOffset(drawerSide, fullWidth) },
                animationSpec = tween(
                    durationMillis = 280,
                    easing = FastOutSlowInEasing,
                ),
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = 180,
                    easing = FastOutSlowInEasing,
                ),
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> queueDrawerHorizontalSlideOffset(drawerSide, fullWidth) },
                animationSpec = tween(
                    durationMillis = 220,
                    easing = FastOutLinearInEasing,
                ),
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = 160,
                    easing = FastOutLinearInEasing,
                ),
            ),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(if (compact) 340.dp else 420.dp),
                shape = queueDrawerShape(drawerSide),
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

internal enum class QueueDrawerSide {
    Start,
    End,
}

internal fun queueDrawerAlignment(side: QueueDrawerSide): Alignment {
    return when (side) {
        QueueDrawerSide.Start -> Alignment.CenterStart
        QueueDrawerSide.End -> Alignment.CenterEnd
    }
}

internal fun queueDrawerHorizontalSlideOffset(side: QueueDrawerSide, fullWidth: Int): Int {
    return when (side) {
        QueueDrawerSide.Start -> -fullWidth
        QueueDrawerSide.End -> fullWidth
    }
}

private fun queueDrawerShape(side: QueueDrawerSide): RoundedCornerShape {
    return when (side) {
        QueueDrawerSide.Start -> RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp)
        QueueDrawerSide.End -> RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp)
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
    if (snapshot.currentTrack == null) {
        if (snapshot.isHydratingPlayback) {
            MiniPlayerHydratingBar(mobile = mobile, compact = compact)
        }
        return
    }
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
            artworkCacheKey = snapshot.currentTrack?.let(::trackArtworkCacheKey),
            spinning = snapshot.isPlaying,
            retainPreviousArtworkWhileLoading = true,
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
private fun MiniPlayerHydratingBar(
    mobile: Boolean,
    compact: Boolean,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = if (mobile || compact) 0.dp else 18.dp, vertical = 12.dp)
        .clip(RoundedCornerShape(28.dp))
        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.42f))
        .border(
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            shape = RoundedCornerShape(28.dp),
        )
        .padding(horizontal = 18.dp, vertical = 14.dp)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        VinylPlaceholder(
            vinylSize = if (mobile) 42.dp else 50.dp,
            artworkLocator = null,
            spinning = false,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "正在恢复上次播放",
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "播放队列和进度会在后台继续加载。",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    val showLyrics = hasMiniPlayerLyricsContent(
        showPortraitLyrics = showPortraitLyrics,
        lyricsText = lyricsText,
    )
    var preferLyricsView by remember(snapshot.currentTrack?.id) { mutableStateOf(false) }
    LaunchedEffect(snapshot.currentTrack?.id, showLyrics) {
        if (showLyrics) {
            preferLyricsView = true
        }
    }
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
                artworkCacheKey = snapshot.currentTrack?.let(::trackArtworkCacheKey),
                spinning = snapshot.isPlaying,
                retainPreviousArtworkWhileLoading = true,
            )
            if (showLyrics && preferLyricsView) {
                Text(
                    text = lyricsText.orEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 1,
                )
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

@Composable
private fun rememberCompactPlayerLyricsText(state: PlayerState): String? {
    return remember(
        state.lyrics,
        state.highlightedLineIndex,
    ) {
        resolveCompactPlayerLyricsText(
            lyrics = state.lyrics,
            highlightedLineIndex = state.highlightedLineIndex,
        )
    }
}

private fun resolveHighlightedOrFirstLyricsText(
    lyrics: LyricsDocument?,
    highlightedLineIndex: Int,
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
    return lyrics
        ?.lines
        ?.firstOrNull { line -> line.text.trim().isNotEmpty() }
        ?.text
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

internal fun resolveMiniPlayerLyricsText(
    lyrics: LyricsDocument?,
    highlightedLineIndex: Int,
    isLyricsLoading: Boolean,
): String? {
    return resolveHighlightedOrFirstLyricsText(
        lyrics = lyrics,
        highlightedLineIndex = highlightedLineIndex,
    ) ?: if (isLyricsLoading) MINI_PLAYER_LYRICS_LOADING_TEXT else null
}

internal fun resolveCompactPlayerLyricsText(
    lyrics: LyricsDocument?,
    highlightedLineIndex: Int,
): String? {
    return resolveHighlightedOrFirstLyricsText(
        lyrics = lyrics,
        highlightedLineIndex = highlightedLineIndex,
    )
}

internal fun shouldShowCompactPlayerLyrics(
    enabled: Boolean,
    compactLyricsText: String?,
): Boolean {
    return enabled && !compactLyricsText.isNullOrBlank()
}

internal fun resolvePlayerInfoVinylSize(
    maxWidth: Dp,
    maxHeight: Dp,
    compact: Boolean,
    hasCompactLyrics: Boolean,
): Dp {
    return if (compact) {
        val widthBound = maxWidth * 0.90f //if (hasCompactLyrics) 0.90f else 0.90f
        val heightReserve = 32.dp //if (hasCompactLyrics) 32.dp else 32.dp
        val heightBound = (maxHeight - heightReserve).coerceAtLeast(180.dp)
        minOf(widthBound, heightBound).coerceIn(
            minimumValue = 220.dp,
            maximumValue = 400.dp //if (hasCompactLyrics) 400.dp else 400.dp
        )
    } else {
        minOf(maxWidth * 0.88f, maxHeight * 0.74f).coerceIn(
            minimumValue = 220.dp,
            maximumValue = 400.dp,
        )
    }
}

internal fun resolvePlayerArtworkDragOffsetPx(
    currentOffsetPx: Float,
    dragAmountPx: Float,
    maxVisualOffsetPx: Float,
): Float {
    if (maxVisualOffsetPx <= 0f) return 0f
    return (currentOffsetPx + dragAmountPx).coerceIn(-maxVisualOffsetPx, maxVisualOffsetPx)
}

internal fun resolvePlayerArtworkSwipeIntent(
    finalOffsetPx: Float,
    swipeThresholdPx: Float,
): PlayerIntent? {
    if (swipeThresholdPx <= 0f) return null
    return when {
        finalOffsetPx <= -swipeThresholdPx -> PlayerIntent.SkipNext
        finalOffsetPx >= swipeThresholdPx -> PlayerIntent.SkipPrevious
        else -> null
    }
}

internal fun resolvePlayerSeekPositionMs(
    positionMs: Long?,
    snapshot: PlaybackSnapshot,
): Long? {
    if (positionMs == null || !snapshot.canSeek || snapshot.durationMs <= 0L) {
        return null
    }
    return positionMs.coerceIn(0L, snapshot.durationMs)
}

internal fun hasMiniPlayerLyricsContent(
    showPortraitLyrics: Boolean,
    lyricsText: String?,
): Boolean {
    return showPortraitLyrics &&
        !lyricsText.isNullOrBlank() &&
        lyricsText != MINI_PLAYER_LYRICS_LOADING_TEXT
}

private const val MINI_PLAYER_LYRICS_LOADING_TEXT = "正在准备歌词"

@Composable
private fun MiniPlayerPlaybackProgress(
    snapshot: PlaybackSnapshot,
    modifier: Modifier = Modifier,
    displayPositionMs: Long = snapshot.positionMs,
) {
    val duration = snapshot.durationMs.coerceAtLeast(1L)
    val progressFraction = (displayPositionMs.coerceIn(0L, duration).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
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
    var dragPositionMs by remember(snapshot.currentTrack?.id, snapshot.durationMs) {
        mutableStateOf<Long?>(null)
    }
    val displayPositionMs = (dragPositionMs ?: snapshot.positionMs).coerceIn(0L, duration)
    Box(
        modifier = modifier.height(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        MiniPlayerPlaybackProgress(
            snapshot = snapshot,
            displayPositionMs = displayPositionMs,
            modifier = Modifier.fillMaxWidth(),
        )
        Slider(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .graphicsLayer(scaleY = 0.36f),
            colors = transparentTrackSliderColors(),
            value = displayPositionMs.toFloat(),
            onValueChange = { dragPositionMs = it.toLong() },
            onValueChangeFinished = {
                val targetPositionMs = resolvePlayerSeekPositionMs(dragPositionMs, snapshot)
                dragPositionMs = null
                if (targetPositionMs != null) {
                    onPlayerIntent(PlayerIntent.SeekTo(targetPositionMs))
                }
            },
            enabled = snapshot.canSeek && snapshot.durationMs > 0L,
            valueRange = 0f..duration.toFloat(),
        )
    }
}

@Composable
private fun PlayerOverlay(
    platform: PlatformDescriptor,
    logger: DiagnosticLogger,
    state: PlayerState,
    showCompactPlayerLyrics: Boolean,
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
    val desktopWindowChrome = currentDesktopWindowChrome
    PlatformBackHandler(onBack = { onPlayerIntent(PlayerIntent.ExpandedChanged(false)) })
    val defaultBackgroundColor = Color(0xFF232325)
    var isPureModeRequested by remember { mutableStateOf(false) }
    val artworkLocator = state.snapshot.currentDisplayArtworkLocator
    val paletteArtworkBitmap = rememberPlatformArtworkBitmap(
        locator = artworkLocator,
        maxDecodeSizePx = ArtworkDecodeSize.Palette,
    )
    val backgroundPalette = rememberPlaybackArtworkBackgroundPalette(
        artworkBitmap = paletteArtworkBitmap,
        enabled = true,
    )
    val backgroundBaseColor by animateColorAsState(
        targetValue = backgroundPalette?.baseColor ?: defaultBackgroundColor,
        label = "player-background-base",
    )
    val backgroundPrimaryColor by animateColorAsState(
        targetValue = backgroundPalette?.primaryColor ?: Color.Transparent,
        label = "player-background-primary",
    )
    val backgroundSecondaryColor by animateColorAsState(
        targetValue = backgroundPalette?.secondaryColor ?: Color.Transparent,
        label = "player-background-secondary",
    )
    val backgroundTertiaryColor by animateColorAsState(
        targetValue = backgroundPalette?.tertiaryColor ?: Color.Transparent,
        label = "player-background-tertiary",
    )
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundBaseColor,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (!artworkLocator.isNullOrBlank()) {
                LynArtworkImage(
                    artworkLocator = artworkLocator,
                    contentDescription = null,
                    artworkCacheKey = trackArtworkCacheKey(track),
                    contentScale = ContentScale.Crop,
                    maxDecodeSizePx = ArtworkDecodeSize.Player,
                    retainPreviousWhileLoading = true,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer(scaleX = 1.16f, scaleY = 1.16f)
                        .blur(86.dp)
                        .alpha(0.30f),
                )
            }
            Canvas(
                modifier = Modifier
                    .matchParentSize(),
            ) {
                val radius = maxOf(size.width, size.height)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            backgroundPrimaryColor.copy(alpha = 0.58f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.06f, size.height * 0.72f),
                        radius = radius * 0.82f,
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            backgroundSecondaryColor.copy(alpha = 0.48f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.98f, size.height * 0.16f),
                        radius = radius * 0.76f,
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            backgroundTertiaryColor.copy(alpha = 0.36f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.52f, size.height * 0.48f),
                        radius = radius * 0.64f,
                    ),
                )
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            backgroundSecondaryColor.copy(alpha = 0.24f),
                            Color.Transparent,
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
                drawRect(color = Color.Black.copy(alpha = 0.32f))
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.16f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.44f),
                        ),
                        startY = 0f,
                        endY = size.height,
                    ),
                )
            }
            val layoutProfile = buildLayoutProfile(
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                platform = platform,
                density = LocalDensity.current,
            )
            val mobilePlayback = platform.isMobilePlatform()
            val immersiveDesktopTrafficLightsInset =
                if (!mobilePlayback && desktopWindowChrome.immersiveTitleBarEnabled) {
                    0.dp
                } else {
                    0.dp
                }
            val wide = layoutProfile.isExpandedLayout
            val isPureMode = wide && isPureModeRequested
            val useTapToRevealLyrics = layoutProfile.isCompactLayout
            val useAutomotiveLandscapePlayer =
                shouldUseAutomotiveLandscapePlayerOverlay(layoutProfile)
            LaunchedEffect(wide) {
                if (!wide) {
                    isPureModeRequested = false
                }
            }
            LaunchedEffect(platform.name, maxWidth, maxHeight) {
                logger.debug(PLAYER_UI_LOG_TAG) {
                    "platform=${platform.name} maxWidth=$maxWidth maxHeight=$maxHeight orientation=${layoutProfile.orientation} wide=$wide useTapToRevealLyrics=$useTapToRevealLyrics"
                }
            }
            if (useAutomotiveLandscapePlayer) {
                AutomotiveLandscapePlayerOverlayContent(
                    state = state,
                    track = track,
                    artworkBitmap = paletteArtworkBitmap,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onOpenQueue = onOpenQueue,
                    onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
                    onPlayerIntent = onPlayerIntent,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 26.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    if (!isPureMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            IconButton(
                                onClick = { onPlayerIntent(PlayerIntent.ExpandedChanged(false)) },
                                modifier = Modifier.padding(start = immersiveDesktopTrafficLightsInset),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "收起播放页",
                                    tint = Color.White.copy(alpha = 0.92f),
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                            if (wide) {
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
                                    IconButton(
                                        onClick = { isPureModeRequested = true },
                                        modifier = Modifier.size(52.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Fullscreen,
                                            contentDescription = "纯净模式",
                                            tint = Color.White.copy(alpha = 0.92f),
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
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
                    }
                    if (useTapToRevealLyrics) {
                        MobilePlayerPrimaryPane(
                            state = state,
                            track = track,
                            artworkBitmap = null,
                            showCompactPlayerLyrics = showCompactPlayerLyrics,
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
                                artworkBitmap = null,
                                onPlayerIntent = onPlayerIntent,
                                modifier = Modifier
                                    .weight(0.5f)
                                    .fillMaxHeight(),
                            )
                            PlayerLyricsPane(
                                state = state,
                                track = track,
                                onPlayerIntent = onPlayerIntent,
                                onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
                                mobilePlayback = mobilePlayback,
                                pure = isPureMode,
                                modifier = Modifier
                                    .weight(0.5f)
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
                                artworkBitmap = null,
                                modifier = Modifier.fillMaxWidth(),
                                compact = true,
                            )
                            PlayerLyricsPane(
                                state = state,
                                track = track,
                                onPlayerIntent = onPlayerIntent,
                                mobilePlayback = mobilePlayback,
                                modifier = Modifier.weight(1f),
                                compact = true,
                            )
                        }
                    }
                    if (!isPureMode) {
                        PlayerBottomControls(
                            snapshot = state.snapshot,
                            sleepTimer = state.sleepTimer,
                            track = track,
                            mobilePlayback = mobilePlayback,
                            wide = wide,
                            isFavorite = isFavorite,
                            onToggleFavorite = onToggleFavorite,
                            onOpenAddToPlaylist = onOpenAddToPlaylist,
                            onOpenQueue = onOpenQueue,
                            onPlayerIntent = onPlayerIntent,
                            onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
                        )
                    }
                }
                if (isPureMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { isPureModeRequested = false },
                    )
                }
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
    artworkBitmap: ImageBitmap?,
    showCompactPlayerLyrics: Boolean,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val compactLyricsText = rememberCompactPlayerLyricsText(state)
    val displayCompactLyricsText = compactLyricsText.takeIf {
        shouldShowCompactPlayerLyrics(
            enabled = showCompactPlayerLyrics,
            compactLyricsText = compactLyricsText,
        )
    }
    var lyricsVisible by rememberSaveable(track.id) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(30.dp))
                .alpha(if (lyricsVisible) 0f else 1f)
                .clickable(
                    enabled = !lyricsVisible,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { lyricsVisible = true },
        ) {
            PlayerInfoPane(
                snapshot = state.snapshot,
                track = track,
                artworkBitmap = artworkBitmap,
                modifier = Modifier.fillMaxSize(),
                compact = true,
                compactLyricsText = displayCompactLyricsText,
                onPlayerIntent = onPlayerIntent,
            )
        }
        if (lyricsVisible) {
            Box(
                modifier = Modifier
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
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerInfoPane(
    snapshot: PlaybackSnapshot,
    track: Track,
    artworkBitmap: ImageBitmap? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    compactLyricsText: String? = null,
    onPlayerIntent: ((PlayerIntent) -> Unit)? = null,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val hasCompactLyrics = compact && !compactLyricsText.isNullOrBlank()
        val vinylSize = remember(maxWidth, maxHeight, compact, hasCompactLyrics) {
            resolvePlayerInfoVinylSize(
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                compact = compact,
                hasCompactLyrics = hasCompactLyrics,
            )
        }
        val compactLyricsAreaTopOffset = ((maxHeight - vinylSize) / 2) + vinylSize
        val compactLyricsAreaHeight = (maxHeight - compactLyricsAreaTopOffset).coerceAtLeast(0.dp)
        if (onPlayerIntent != null) {
            SwipeablePlayerArtwork(
                snapshot = snapshot,
                artworkBitmap = artworkBitmap,
                vinylSize = vinylSize,
                onPlayerIntent = onPlayerIntent,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            VinylPlaceholder(
                vinylSize = vinylSize,
                artworkBitmap = artworkBitmap,
                artworkLocator = snapshot.currentDisplayArtworkLocator,
                artworkCacheKey = snapshot.currentTrack?.let(::trackArtworkCacheKey),
                spinning = snapshot.isPlaying,
                artworkDiameterFraction = PLAYER_INFO_VINYL_ARTWORK_DIAMETER_FRACTION,
                innerGlowDiameterFraction = PLAYER_INFO_VINYL_INNER_GLOW_DIAMETER_FRACTION,
                maxArtworkDecodeSizePx = ArtworkDecodeSize.Player,
                retainPreviousArtworkWhileLoading = true,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (hasCompactLyrics) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = compactLyricsAreaTopOffset)
                    .fillMaxWidth()
                    .height(compactLyricsAreaHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = compactLyricsText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                        .basicMarquee(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Composable
private fun SwipeablePlayerArtwork(
    snapshot: PlaybackSnapshot,
    artworkBitmap: ImageBitmap?,
    vinylSize: Dp,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 72.dp.toPx() }
    val maxVisualOffsetPx = with(density) {
        minOf(vinylSize * 0.32f, 132.dp).toPx()
    }
    var dragOffsetPx by remember(snapshot.currentTrack?.id) { mutableStateOf(0f) }
    val animatedDragOffsetPx by animateFloatAsState(
        targetValue = dragOffsetPx,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "player-artwork-swipe-offset",
    )
    Box(
        modifier = modifier
            .size(vinylSize)
            .offset { IntOffset(animatedDragOffsetPx.roundToInt(), 0) }
            .pointerInput(snapshot.currentTrack?.id, swipeThresholdPx, maxVisualOffsetPx) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        dragOffsetPx = resolvePlayerArtworkDragOffsetPx(
                            currentOffsetPx = dragOffsetPx,
                            dragAmountPx = dragAmount,
                            maxVisualOffsetPx = maxVisualOffsetPx,
                        )
                    },
                    onDragEnd = {
                        val swipeIntent = resolvePlayerArtworkSwipeIntent(
                            finalOffsetPx = dragOffsetPx,
                            swipeThresholdPx = swipeThresholdPx,
                        )
                        dragOffsetPx = 0f
                        if (swipeIntent != null) {
                            onPlayerIntent(swipeIntent)
                        }
                    },
                    onDragCancel = { dragOffsetPx = 0f },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        VinylPlaceholder(
            vinylSize = vinylSize,
            artworkBitmap = artworkBitmap,
            artworkLocator = snapshot.currentDisplayArtworkLocator,
            artworkCacheKey = snapshot.currentTrack?.let(::trackArtworkCacheKey),
            spinning = snapshot.isPlaying,
            artworkDiameterFraction = PLAYER_INFO_VINYL_ARTWORK_DIAMETER_FRACTION,
            innerGlowDiameterFraction = PLAYER_INFO_VINYL_INNER_GLOW_DIAMETER_FRACTION,
            maxArtworkDecodeSizePx = ArtworkDecodeSize.Player,
            retainPreviousArtworkWhileLoading = true,
        )
    }
}

@Composable
private fun PlayerBottomControls(
    snapshot: PlaybackSnapshot,
    sleepTimer: SleepTimerState,
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
        val navigationTargets = remember(
            mobilePlayback,
            snapshot.currentDisplayArtistName,
            snapshot.currentDisplayAlbumTitle,
            track.artistName,
            track.albumTitle,
        ) {
            if (!mobilePlayback) {
                PlaybackLibraryNavigationTargets(albumTarget = null, artistTarget = null)
            } else {
                derivePlaybackLibraryNavigationTargets(snapshot, track)
            }
        }
        val artistNavigationTarget = navigationTargets.artistTarget
        val offlineUiState = LocalOfflineDownloadUiState.current
        val offlineDownload = offlineUiState.downloadsByTrackId[track.id]
        val onOfflineDownloadIntent = offlineUiState.onIntent
        val supportsOfflineDownloadEntry = currentPlatformDescriptor.supportsOfflineDownloadUiActions() &&
            supportsOfflineDownload(track)
        val showOfflineDownloadEntry = supportsOfflineDownloadEntry && onOfflineDownloadIntent != null
        var isMoreSheetVisible by rememberSaveable(track.id) { mutableStateOf(false) }
        var isSleepTimerSheetVisible by rememberSaveable(track.id) { mutableStateOf(false) }
        var isOfflineDownloadSheetVisible by rememberSaveable(track.id) { mutableStateOf(false) }
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
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (mobilePlayback) {
                        CompactPlayerMoreButton(
                            onClick = { isMoreSheetVisible = true },
                            tint = Color.White.copy(alpha = 0.96f),
                            buttonSize = mobileTopActionButtonSize,
                            iconSize = mobileTopActionIconSize,
                        )
                    } else {
                        AddToPlaylistButton(
                            onClick = onOpenAddToPlaylist,
                            tint = Color.White.copy(alpha = 0.96f),
                            buttonSize = mobileTopActionButtonSize,
                            iconSize = mobileTopActionIconSize,
                        )
                    }
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
        if (mobilePlayback && isMoreSheetVisible) {
            CompactPlayerMoreSheet(
                snapshot = snapshot,
                track = track,
                navigationTargets = navigationTargets,
                onDismiss = { isMoreSheetVisible = false },
                onOpenAddToPlaylist = {
                    isMoreSheetVisible = false
                    onOpenAddToPlaylist()
                },
                onOpenLibraryNavigationTarget = { target ->
                    isMoreSheetVisible = false
                    onOpenLibraryNavigationTarget(target)
                },
                sleepTimer = sleepTimer,
                showOfflineDownload = showOfflineDownloadEntry,
                offlineDownloadStatus = compactPlayerOfflineDownloadStatusLabel(offlineDownload),
                onOpenOfflineDownload = {
                    isMoreSheetVisible = false
                    isOfflineDownloadSheetVisible = true
                },
                onOpenSleepTimer = {
                    isMoreSheetVisible = false
                    isSleepTimerSheetVisible = true
                },
            )
        }
        if (
            mobilePlayback &&
            isOfflineDownloadSheetVisible &&
            supportsOfflineDownloadEntry &&
            onOfflineDownloadIntent != null
        ) {
            TrackOfflineDownloadBottomSheet(
                track = track,
                download = offlineDownload,
                onIntent = onOfflineDownloadIntent,
                onDismiss = { isOfflineDownloadSheetVisible = false },
            )
        }
        if (mobilePlayback && isSleepTimerSheetVisible) {
            SleepTimerBottomSheet(
                sleepTimer = sleepTimer,
                onDismiss = { isSleepTimerSheetVisible = false },
                onStartTimer = { minutes ->
                    onPlayerIntent(PlayerIntent.StartSleepTimer(minutes))
                    isSleepTimerSheetVisible = false
                },
                onCancelTimer = {
                    onPlayerIntent(PlayerIntent.CancelSleepTimer)
                    isSleepTimerSheetVisible = false
                },
            )
        }
        return
    }
    var isSleepTimerDialogVisible by rememberSaveable(track.id) { mutableStateOf(false) }
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
                        text = track.sourceId.substringBefore('-').uppercase(),
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
                        QueueToggleButton(
                            onClick = onOpenQueue,
                            tint = Color.White.copy(alpha = 0.96f),
                            buttonSize = skipButtonSize,
                            iconSize = modeIconSize,
                        )
                    }
                    Row(
                        modifier = Modifier.weight(0.30f),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AddToPlaylistButton(
                            onClick = onOpenAddToPlaylist,
                            tint = Color.White.copy(alpha = 0.96f),
                        )
                        SleepTimerButton(
                            sleepTimer = sleepTimer,
                            onClick = { isSleepTimerDialogVisible = true },
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
    if (isSleepTimerDialogVisible) {
        SleepTimerDialog(
            sleepTimer = sleepTimer,
            onDismiss = { isSleepTimerDialogVisible = false },
            onStartTimer = { minutes ->
                onPlayerIntent(PlayerIntent.StartSleepTimer(minutes))
                isSleepTimerDialogVisible = false
            },
            onCancelTimer = {
                onPlayerIntent(PlayerIntent.CancelSleepTimer)
                isSleepTimerDialogVisible = false
            },
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
private fun SleepTimerButton(
    sleepTimer: SleepTimerState,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    buttonSize: Dp = 48.dp,
    iconSize: Dp = 24.dp,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(buttonSize)) {
        Icon(
            imageVector = Icons.Rounded.Timer,
            contentDescription = "定时关闭",
            tint = if (sleepTimer.isActive) Color(0xFFE5484D) else tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun CompactPlayerMoreButton(
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    buttonSize: androidx.compose.ui.unit.Dp = 48.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(buttonSize)) {
        Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = "更多操作",
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactPlayerMoreSheet(
    snapshot: PlaybackSnapshot,
    track: Track,
    navigationTargets: PlaybackLibraryNavigationTargets,
    sleepTimer: SleepTimerState,
    onDismiss: () -> Unit,
    onOpenAddToPlaylist: () -> Unit,
    showOfflineDownload: Boolean,
    offlineDownloadStatus: String,
    onOpenOfflineDownload: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
) {
    val shellColors = mainShellColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appDensity = LocalDensity.current
    val artistTarget = navigationTargets.artistTarget
    val albumTarget = navigationTargets.albumTarget
    val technicalSummary = formatTrackTechnicalSummary(track)
    val currentPlaybackAudioQuality = if (currentPlatformDescriptor.isAndroidPlatform()) {
        formatAndroidCurrentPlaybackAudioQuality(
            track = track,
            audioFormat = snapshot.currentPlaybackAudioFormat,
            navidromeQuality = snapshot.currentNavidromeAudioQuality,
        )
    } else {
        formatCurrentNavidromePlaybackAudioQuality(
            track = track,
            audioQuality = snapshot.currentNavidromeAudioQuality,
        )
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = shellColors.navContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        dragHandle = {
            CompositionLocalProvider(LocalDensity provides appDensity) {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .size(width = 50.dp, height = 5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(shellColors.cardBorder.copy(alpha = 0.75f)),
                )
            }
        },
    ) {
        CompositionLocalProvider(LocalDensity provides appDensity) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "更多操作",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = compactPlayerMoreTrackTitle(snapshot, track),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = technicalSummary,
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                currentPlaybackAudioQuality?.let { qualityText ->
                    CompactPlayerMoreSheetRow(
                        icon = Icons.Rounded.GraphicEq,
                        title = "当前播放音质",
                        value = qualityText,
                        enabled = true,
                        clickable = false,
                        onClick = {},
                    )
                }
                if (showOfflineDownload) {
                    CompactPlayerMoreSheetRow(
                        icon = Icons.Rounded.Download,
                        title = "离线下载",
                        value = offlineDownloadStatus,
                        enabled = true,
                        onClick = onOpenOfflineDownload,
                    )
                }
                CompactPlayerMoreSheetRow(
                    icon = Icons.Rounded.Person,
                    title = "歌手",
                    value = compactPlayerMoreArtistLabel(snapshot, track),
                    enabled = artistTarget != null,
                    onClick = { artistTarget?.let(onOpenLibraryNavigationTarget) },
                )
                CompactPlayerMoreSheetRow(
                    icon = Icons.Rounded.Album,
                    title = "专辑",
                    value = compactPlayerMoreAlbumLabel(snapshot, track),
                    enabled = albumTarget != null,
                    onClick = { albumTarget?.let(onOpenLibraryNavigationTarget) },
                )
                CompactPlayerMoreSheetRow(
                    icon = Icons.Rounded.Add,
                    title = "加入歌单",
                    value = "收藏到歌单",
                    enabled = true,
                    onClick = onOpenAddToPlaylist,
                )
                CompactPlayerMoreSheetRow(
                    icon = Icons.Rounded.Timer,
                    title = "定时关闭",
                    value = sleepTimerStatusText(sleepTimer),
                    enabled = true,
                    onClick = onOpenSleepTimer,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackOfflineDownloadBottomSheet(
    track: Track,
    download: OfflineDownload?,
    onIntent: (OfflineDownloadIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    val shellColors = mainShellColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appDensity = LocalDensity.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = shellColors.navContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        CompositionLocalProvider(LocalDensity provides appDensity) {
            Column(modifier = Modifier.padding(bottom = 20.dp)) {
                Text(
                    text = track.title,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                )
                TrackOfflineActionMenuItems(
                    track = track,
                    download = download,
                    onIntent = onIntent,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerBottomSheet(
    sleepTimer: SleepTimerState,
    onDismiss: () -> Unit,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
) {
    val shellColors = mainShellColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appDensity = LocalDensity.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = shellColors.navContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        dragHandle = {
            CompositionLocalProvider(LocalDensity provides appDensity) {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .size(width = 50.dp, height = 5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(shellColors.cardBorder.copy(alpha = 0.75f)),
                )
            }
        },
    ) {
        CompositionLocalProvider(LocalDensity provides appDensity) {
            SleepTimerPickerContent(
                sleepTimer = sleepTimer,
                onStartTimer = onStartTimer,
                onCancelTimer = onCancelTimer,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 22.dp),
            )
        }
    }
}

@Composable
private fun SleepTimerDialog(
    sleepTimer: SleepTimerState,
    onDismiss: () -> Unit,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
) {
    val shellColors = mainShellColors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = shellColors.navContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        title = { Text("定时关闭", fontWeight = FontWeight.Bold) },
        text = {
            SleepTimerPickerContent(
                sleepTimer = sleepTimer,
                onStartTimer = onStartTimer,
                onCancelTimer = onCancelTimer,
                showTitle = false,
                useCardContainer = false,
                modifier = Modifier.widthIn(min = 360.dp, max = 480.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        },
    )
}

@Composable
private fun SleepTimerPickerContent(
    sleepTimer: SleepTimerState,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    useCardContainer: Boolean = true,
) {
    var customMinutesText by rememberSaveable { mutableStateOf("") }
    val customMinutes = parseSleepTimerCustomMinutes(customMinutesText)
    val shellColors = mainShellColors
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showTitle) {
            Text(
                text = "定时关闭",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        if (useCardContainer) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = shellColors.cardContainer.copy(alpha = 0.96f)),
                border = BorderStroke(1.dp, shellColors.cardBorder.copy(alpha = 0.45f)),
            ) {
                SleepTimerPickerControls(
                    sleepTimer = sleepTimer,
                    customMinutesText = customMinutesText,
                    customMinutes = customMinutes,
                    onCustomMinutesTextChanged = { customMinutesText = it },
                    onStartTimer = onStartTimer,
                    onCancelTimer = onCancelTimer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                )
            }
        } else {
            SleepTimerPickerControls(
                sleepTimer = sleepTimer,
                customMinutesText = customMinutesText,
                customMinutes = customMinutes,
                onCustomMinutesTextChanged = { customMinutesText = it },
                onStartTimer = onStartTimer,
                onCancelTimer = onCancelTimer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SleepTimerPickerControls(
    sleepTimer: SleepTimerState,
    customMinutesText: String,
    customMinutes: Int?,
    onCustomMinutesTextChanged: (String) -> Unit,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (sleepTimer.isActive) {
                        "已定时 ${formatSleepTimerRemaining(sleepTimer.remainingMs)}"
                    } else {
                        "选择关闭时间"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (sleepTimer.isActive) {
                TextButton(onClick = onCancelTimer) {
                    Text("关闭定时")
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SLEEP_TIMER_PRESET_MINUTES.forEach { minutes ->
                SleepTimerPresetButton(
                    minutes = minutes,
                    selected = sleepTimer.durationMinutes == minutes && sleepTimer.isActive,
                    onClick = { onStartTimer(minutes) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = customMinutesText,
                onValueChange = { onCustomMinutesTextChanged(it.filter { char -> char.isDigit() }.take(3)) },
                modifier = Modifier.weight(1f),
                label = { Text("自定义分钟") },
                singleLine = true,
            )
            Button(
                onClick = { customMinutes?.let(onStartTimer) },
                enabled = customMinutes != null,
            ) {
                Text("开始")
            }
        }
    }
}

@Composable
private fun SleepTimerPresetButton(
    minutes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        shape = RoundedCornerShape(999.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) Color(0xFFE5484D).copy(alpha = 0.12f) else Color.Transparent,
            contentColor = if (selected) Color(0xFFE5484D) else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) Color(0xFFE5484D) else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
        ),
    ) {
        Text(
            text = minutes.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CompactPlayerMoreSheetRow(
    icon: ImageVector,
    title: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    clickable: Boolean = enabled,
) {
    val shellColors = mainShellColors
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.46f)
    }
    val rowModifier = if (clickable) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(shellColors.cardContainer.copy(alpha = if (enabled) 0.82f else 0.38f))
            .then(rowModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary else contentColor,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = contentColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                color = contentColor.copy(alpha = if (enabled) 0.70f else 0.86f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun compactPlayerMoreArtistLabel(
    snapshot: PlaybackSnapshot,
    track: Track,
): String {
    return snapshot.currentDisplayArtistName?.trim()?.takeIf { it.isNotBlank() }
        ?: track.artistName?.trim()?.takeIf { it.isNotBlank() }
        ?: "未知艺人"
}

private fun compactPlayerMoreAlbumLabel(
    snapshot: PlaybackSnapshot,
    track: Track,
): String {
    return snapshot.currentDisplayAlbumTitle?.trim()?.takeIf { it.isNotBlank() }
        ?: track.albumTitle?.trim()?.takeIf { it.isNotBlank() }
        ?: "本地曲目"
}

private fun compactPlayerMoreTrackTitle(
    snapshot: PlaybackSnapshot,
    track: Track,
): String {
    return snapshot.currentDisplayTitle.ifBlank { track.title }
}

internal fun formatCurrentNavidromePlaybackAudioQuality(
    track: Track,
    audioQuality: NavidromeAudioQuality?,
): String? {
    if (parseNavidromeSongLocator(track.mediaLocator) == null) return null
    return audioQuality?.let(::navidromeAudioQualityLabel)
}

internal fun formatCurrentPlaybackAudioFormat(audioFormat: PlaybackAudioFormat?): String? {
    audioFormat ?: return null
    return listOfNotNull(
        audioFormat.samplingRateHz?.takeIf { it > 0 }?.let(::formatPlaybackSamplingRate),
        audioFormat.bitRateBps?.takeIf { it > 0 }?.let(::formatPlaybackBitRate),
        audioFormat.channelCount?.takeIf { it > 0 }?.let { "${it}ch" },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

internal fun formatAndroidCurrentPlaybackAudioQuality(
    track: Track,
    audioFormat: PlaybackAudioFormat?,
    navidromeQuality: NavidromeAudioQuality?,
): String? {
    val navidromeFallbackBitRate = navidromeQuality
        ?.takeIf { parseNavidromeSongLocator(track.mediaLocator) != null }
        ?.let(::formatNavidromePlaybackBitRateFallback)
    return listOfNotNull(
        audioFormat?.samplingRateHz?.takeIf { it > 0 }?.let(::formatPlaybackSamplingRate),
        audioFormat?.bitRateBps?.takeIf { it > 0 }?.let(::formatPlaybackBitRate) ?: navidromeFallbackBitRate,
        audioFormat?.channelCount?.takeIf { it > 0 }?.let { "${it}ch" },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

internal fun compactPlayerOfflineDownloadStatusLabel(download: OfflineDownload?): String {
    return when (download?.status) {
        OfflineDownloadStatus.Pending,
        OfflineDownloadStatus.Downloading -> "正在下载"
        OfflineDownloadStatus.Completed -> "已离线"
        OfflineDownloadStatus.Failed -> "下载失败"
        null -> "下载到本机"
    }
}

private fun formatNavidromePlaybackBitRateFallback(quality: NavidromeAudioQuality): String {
    return quality.maxBitRateKbps?.let { "${it}kbps" } ?: "原始"
}

private fun formatPlaybackSamplingRate(samplingRateHz: Int): String {
    if (samplingRateHz % 1_000 == 0) return "${samplingRateHz / 1_000}kHz"
    val rounded = (samplingRateHz / 100.0).roundToInt() / 10.0
    return "${rounded}kHz"
}

private fun formatPlaybackBitRate(bitRateBps: Int): String {
    return "${(bitRateBps / 1_000.0).roundToInt()}kbps"
}

internal fun parseSleepTimerCustomMinutes(input: String): Int? {
    return input.trim().toIntOrNull()?.let(::normalizeSleepTimerMinutes)
}

internal fun formatSleepTimerRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    val minuteText = minutes.toString().padStart(2, '0')
    val secondText = seconds.toString().padStart(2, '0')
    return if (hours > 0L) {
        "$hours:$minuteText:$secondText"
    } else {
        "$minuteText:$secondText"
    }
}

internal fun sleepTimerStatusText(sleepTimer: SleepTimerState): String {
    return if (sleepTimer.isActive) {
        "剩余 ${formatSleepTimerRemaining(sleepTimer.remainingMs)}"
    } else {
        "未开启"
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
    var dragPositionMs by remember(snapshot.currentTrack?.id, snapshot.durationMs) {
        mutableStateOf<Long?>(null)
    }
    val displayPositionMs = (dragPositionMs ?: snapshot.positionMs).coerceIn(0L, duration)
    val progressFraction = (displayPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val showFlowerParticles = !currentPlatformDescriptor.isAndroidTV() && !currentPlatformDescriptor.isAndroidAutomotivePlatform()
    val particles = remember { mutableStateListOf<ProgressFlowerParticle>() }
    var lastEmissionFraction by remember { mutableStateOf(progressFraction) }
    var lastEmissionNanos by remember { mutableStateOf(0L) }
    var animationFrameNanos by remember { mutableStateOf(0L) }
    LaunchedEffect(showFlowerParticles) {
        if (!showFlowerParticles) {
            particles.clear()
        }
    }
    LaunchedEffect(progressFraction, snapshot.isPlaying, duration, showFlowerParticles) {
        val currentFraction = progressFraction
        if (!showFlowerParticles) {
            lastEmissionFraction = currentFraction
            lastEmissionNanos = 0L
            return@LaunchedEffect
        }
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
            if (showFlowerParticles) {
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
                value = displayPositionMs.toFloat(),
                onValueChange = { dragPositionMs = it.toLong() },
                onValueChangeFinished = {
                    val targetPositionMs = resolvePlayerSeekPositionMs(dragPositionMs, snapshot)
                    dragPositionMs = null
                    if (targetPositionMs != null) {
                        onPlayerIntent(PlayerIntent.SeekTo(targetPositionMs))
                    }
                },
                enabled = snapshot.canSeek && snapshot.durationMs > 0L,
                valueRange = 0f..duration.toFloat(),
            )
        }
        if (showTimeLabels) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDuration(displayPositionMs),
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
private const val PLAYER_INFO_VINYL_ARTWORK_DIAMETER_FRACTION = 0.7f
private const val PLAYER_INFO_VINYL_INNER_GLOW_DIAMETER_FRACTION = 0.8f

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
        TrackActionContainer(
            track = track,
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
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
