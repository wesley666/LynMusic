package top.iwesley.lyn.music.automotive

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import top.iwesley.lyn.music.ArtworkDecodeSize
import top.iwesley.lyn.music.LibraryNavigationTarget
import top.iwesley.lyn.music.PlayerLyricsPane
import top.iwesley.lyn.music.VinylPlaceholder
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.derivePlaybackLibraryNavigationTargets
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.formatDuration
import top.iwesley.lyn.music.playbackModeIcon

@Composable
internal fun AutomotiveLandscapePlayerOverlayContent(
    state: PlayerState,
    track: Track,
    artworkBitmap: ImageBitmap?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth < 900.dp) 28.dp else 44.dp
        val paneGap = if (maxWidth < 900.dp) 24.dp else 36.dp
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(paneGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AutomotivePlaybackPane(
                state = state,
                track = track,
                artworkBitmap = artworkBitmap,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onOpenQueue = onOpenQueue,
                onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
                onPlayerIntent = onPlayerIntent,
                modifier = Modifier
                    .weight(0.46f)
                    .fillMaxHeight(),
            )
            AutomotiveLyricsPane(
                state = state,
                track = track,
                onPlayerIntent = onPlayerIntent,
                modifier = Modifier
                    .weight(0.54f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun AutomotivePlaybackPane(
    state: PlayerState,
    track: Track,
    artworkBitmap: ImageBitmap?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AutomotiveRoundIconButton(
                icon = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "收起播放页",
                onClick = { onPlayerIntent(PlayerIntent.ExpandedChanged(false)) },
                buttonSize = 64.dp,
                iconSize = 34.dp,
            )
            AutomotiveRoundIconButton(
                icon = Icons.Rounded.Search,
                contentDescription = "搜索歌词",
                onClick = { onPlayerIntent(PlayerIntent.OpenManualLyricsSearch) },
                buttonSize = 64.dp,
                iconSize = 30.dp,
            )
        }
        AutomotiveTrackAndProgress(
            snapshot = snapshot,
            track = track,
            artworkBitmap = artworkBitmap,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
            onPlayerIntent = onPlayerIntent,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        AutomotivePlaybackControls(
            snapshot = snapshot,
            onOpenQueue = onOpenQueue,
            onPlayerIntent = onPlayerIntent,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AutomotiveTrackAndProgress(
    snapshot: PlaybackSnapshot,
    track: Track,
    artworkBitmap: ImageBitmap?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val compactVertical = maxHeight < 420.dp
        val artworkSize = minOf(
            maxWidth * if (compactVertical) 0.52f else 0.62f,
            maxHeight * if (compactVertical) 0.42f else 0.48f,
        ).coerceIn(
            minimumValue = if (compactVertical) 140.dp else 170.dp,
            maximumValue = if (compactVertical) 230.dp else 320.dp,
        )
        val titleStyle =
            if (compactVertical) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium
        val inlineActionButtonSize = if (compactVertical) 44.dp else 52.dp
        val inlineActionIconSize = if (compactVertical) 24.dp else 28.dp
        val progressWidthFraction = if (compactVertical) 0.9f else 0.86f
        val progressTopGap = if (compactVertical) 18.dp else 30.dp
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AutomotiveSwipeableArtwork(
                snapshot = snapshot,
                artworkBitmap = artworkBitmap,
                artworkSize = artworkSize,
                onPlayerIntent = onPlayerIntent,
            )
            Spacer(Modifier.height(if (compactVertical) 10.dp else 18.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compactVertical) 4.dp else 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = snapshot.currentDisplayTitle,
                        style = titleStyle,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = 0.96f),
                        maxLines = if (compactVertical) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(if (compactVertical) 2.dp else 4.dp))
                    AutomotiveRoundIconButton(
                        icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isFavorite) "取消喜欢" else "喜欢",
                        onClick = onToggleFavorite,
                        buttonSize = inlineActionButtonSize,
                        iconSize = inlineActionIconSize,
                        tint = if (isFavorite) Color(0xFFE5484D) else Color.White.copy(alpha = 0.9f),
                    )
                }
                AutomotiveMetadataNavigationRow(
                    snapshot = snapshot,
                    track = track,
                    style = if (compactVertical) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
                )
            }
            Spacer(Modifier.height(progressTopGap))
            AutomotivePlaybackProgress(
                snapshot = snapshot,
                onPlayerIntent = onPlayerIntent,
                modifier = Modifier.fillMaxWidth(progressWidthFraction),
            )
        }
    }
}

@Composable
private fun AutomotiveSwipeableArtwork(
    snapshot: PlaybackSnapshot,
    artworkBitmap: ImageBitmap?,
    artworkSize: Dp,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 72.dp.toPx() }
    val maxVisualOffsetPx = with(density) {
        minOf(artworkSize * 0.32f, 132.dp).toPx()
    }
    var dragOffsetPx by remember(snapshot.currentTrack?.id) { mutableStateOf(0f) }
    val animatedDragOffsetPx by animateFloatAsState(
        targetValue = dragOffsetPx,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "automotive-artwork-swipe-offset",
    )
    Box(
        modifier = modifier
            .size(artworkSize)
            .offset { IntOffset(animatedDragOffsetPx.roundToInt(), 0) }
            .pointerInput(snapshot.currentTrack?.id, swipeThresholdPx, maxVisualOffsetPx) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        dragOffsetPx = resolveAutomotiveArtworkDragOffsetPx(
                            currentOffsetPx = dragOffsetPx,
                            dragAmountPx = dragAmount,
                            maxVisualOffsetPx = maxVisualOffsetPx,
                        )
                    },
                    onDragEnd = {
                        val swipeIntent = resolveAutomotiveArtworkSwipeIntent(
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
            vinylSize = artworkSize,
            artworkBitmap = artworkBitmap,
            artworkLocator = snapshot.currentDisplayArtworkLocator,
            artworkCacheKey = snapshot.currentTrack?.let(::trackArtworkCacheKey),
            spinning = snapshot.isPlaying,
            enableArtworkTint = true,
            artworkDiameterFraction = 0.76f,
            innerGlowDiameterFraction = 0.72f,
            maxArtworkDecodeSizePx = ArtworkDecodeSize.Player,
            retainPreviousArtworkWhileLoading = true,
        )
    }
}

@Composable
private fun AutomotiveMetadataNavigationRow(
    snapshot: PlaybackSnapshot,
    track: Track,
    style: TextStyle,
    color: Color,
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigationTargets = remember(
        snapshot.currentDisplayAlbumTitle,
        snapshot.currentDisplayArtistName,
        track.albumTitle,
        track.artistName,
    ) {
        derivePlaybackLibraryNavigationTargets(snapshot, track)
    }
    val artistLabel = automotiveMetadataValue(
        primary = snapshot.currentDisplayArtistName,
        fallback = track.artistName,
    ) ?: "未知艺人"
    val albumLabel = automotiveMetadataValue(
        primary = snapshot.currentDisplayAlbumTitle,
        fallback = track.albumTitle,
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AutomotiveMetadataText(
            text = artistLabel,
            target = navigationTargets.artistTarget,
            style = style,
            color = color,
            onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (albumLabel != null) {
            Text(
                text = " · ",
                style = style,
                fontWeight = FontWeight.Medium,
                color = color,
                maxLines = 1,
            )
            AutomotiveMetadataText(
                text = albumLabel,
                target = navigationTargets.albumTarget,
                style = style,
                color = color,
                onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun AutomotiveMetadataText(
    text: String,
    target: LibraryNavigationTarget?,
    style: TextStyle,
    color: Color,
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.then(
            if (target != null) {
                Modifier.clickable { onOpenLibraryNavigationTarget(target) }
            } else {
                Modifier
            },
        ),
        style = style,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun AutomotivePlaybackProgress(
    snapshot: PlaybackSnapshot,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember(snapshot.currentTrack?.id, snapshot.durationMs) {
        mutableStateOf<Float?>(null)
    }
    val progressFraction = dragFraction ?: resolveAutomotivePlayerProgressFraction(snapshot)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            AutomotiveRoundedSliderTrack(
                progressFraction = progressFraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                trackHeightPx = with(LocalDensity.current) { 6.dp.toPx() },
            )
            Slider(
                value = progressFraction.coerceIn(0f, 1f),
                onValueChange = { dragFraction = it },
                onValueChangeFinished = {
                    val targetPositionMs = resolveAutomotivePlayerSeekPositionMs(dragFraction, snapshot)
                    dragFraction = null
                    if (targetPositionMs != null) {
                        onPlayerIntent(PlayerIntent.SeekTo(targetPositionMs))
                    }
                },
                enabled = snapshot.canSeek && snapshot.durationMs > 0L,
                valueRange = 0f..1f,
                colors = automotiveTransparentSliderColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .graphicsLayer(scaleY = 0.58f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(snapshot.positionMs),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.74f),
            )
            Text(
                text = formatDuration(snapshot.durationMs),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.74f),
            )
        }
    }
}

@Composable
private fun AutomotiveRoundedSliderTrack(
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
private fun automotiveTransparentSliderColors() = SliderDefaults.colors(
    thumbColor = Color.White.copy(alpha = 0.98f),
    activeTrackColor = Color.Transparent,
    inactiveTrackColor = Color.Transparent,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
    disabledThumbColor = Color.White.copy(alpha = 0.52f),
    disabledActiveTrackColor = Color.Transparent,
    disabledInactiveTrackColor = Color.Transparent,
    disabledActiveTickColor = Color.Transparent,
    disabledInactiveTickColor = Color.Transparent,
)

@Composable
private fun AutomotivePlaybackControls(
    snapshot: PlaybackSnapshot,
    onOpenQueue: () -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val narrowControls = maxWidth < 400.dp
        val compactControls = maxWidth < 560.dp
        val actionButtonSize = when {
            narrowControls -> 40.dp
            compactControls -> 48.dp
            else -> 60.dp
        }
        val skipButtonSize = when {
            narrowControls -> 46.dp
            compactControls -> 54.dp
            else -> 68.dp
        }
        val playButtonSize = when {
            narrowControls -> 60.dp
            compactControls -> 70.dp
            else -> 84.dp
        }
        val actionIconSize = when {
            narrowControls -> 22.dp
            compactControls -> 24.dp
            else -> 28.dp
        }
        val skipIconSize = when {
            narrowControls -> 26.dp
            compactControls -> 30.dp
            else -> 34.dp
        }
        val playIconSize = when {
            narrowControls -> 46.dp
            compactControls -> 54.dp
            else -> 60.dp
        }
        val controlGap = when {
            narrowControls -> 0.dp
            compactControls -> 4.dp
            else -> 8.dp
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AutomotiveRoundIconButton(
                icon = playbackModeIcon(snapshot.mode),
                contentDescription = "切换播放模式",
                onClick = { onPlayerIntent(PlayerIntent.CycleMode) },
                buttonSize = actionButtonSize,
                iconSize = actionIconSize,
            )
            Spacer(Modifier.width(controlGap))
            AutomotiveRoundIconButton(
                icon = Icons.Rounded.SkipPrevious,
                contentDescription = "上一首",
                onClick = { onPlayerIntent(PlayerIntent.SkipPrevious) },
                buttonSize = skipButtonSize,
                iconSize = skipIconSize,
            )
            Spacer(Modifier.width(controlGap))
            AutomotiveRoundIconButton(
                icon = if (snapshot.isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                contentDescription = if (snapshot.isPlaying) "暂停" else "播放",
                onClick = { onPlayerIntent(PlayerIntent.TogglePlayPause) },
                buttonSize = playButtonSize,
                iconSize = playIconSize,
            )
            Spacer(Modifier.width(controlGap))
            AutomotiveRoundIconButton(
                icon = Icons.Rounded.SkipNext,
                contentDescription = "下一首",
                onClick = { onPlayerIntent(PlayerIntent.SkipNext) },
                buttonSize = skipButtonSize,
                iconSize = skipIconSize,
            )
            Spacer(Modifier.width(controlGap))
            AutomotiveRoundIconButton(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = "播放队列",
                onClick = onOpenQueue,
                buttonSize = actionButtonSize,
                iconSize = actionIconSize,
            )
        }
    }
}

@Composable
private fun AutomotiveLyricsPane(
    state: PlayerState,
    track: Track,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        PlayerLyricsPane(
            state = state,
            track = track,
            onPlayerIntent = onPlayerIntent,
            pure = true,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AutomotiveRoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 64.dp,
    iconSize: Dp = 30.dp,
    tint: Color = Color.White.copy(alpha = 0.92f),
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(buttonSize),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.46f),
            modifier = Modifier.size(iconSize),
        )
    }
}

internal fun resolveAutomotivePlayerProgressFraction(snapshot: PlaybackSnapshot): Float {
    if (snapshot.durationMs <= 0L) return 0f
    return (snapshot.positionMs.toFloat() / snapshot.durationMs.toFloat()).coerceIn(0f, 1f)
}

internal fun resolveAutomotivePlayerSeekPositionMs(
    fraction: Float?,
    snapshot: PlaybackSnapshot,
): Long? {
    if (fraction == null || !snapshot.canSeek || snapshot.durationMs <= 0L) {
        return null
    }
    return (snapshot.durationMs * fraction.coerceIn(0f, 1f)).roundToLong()
}

internal fun resolveAutomotiveArtworkDragOffsetPx(
    currentOffsetPx: Float,
    dragAmountPx: Float,
    maxVisualOffsetPx: Float,
): Float {
    if (maxVisualOffsetPx <= 0f) return 0f
    return (currentOffsetPx + dragAmountPx).coerceIn(-maxVisualOffsetPx, maxVisualOffsetPx)
}

internal fun resolveAutomotiveArtworkSwipeIntent(
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

private fun automotiveMetadataValue(
    primary: String?,
    fallback: String?,
): String? {
    return primary?.trim()?.takeIf { it.isNotBlank() }
        ?: fallback?.trim()?.takeIf { it.isNotBlank() }
}
