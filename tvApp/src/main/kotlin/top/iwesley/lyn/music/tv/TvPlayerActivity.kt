package top.iwesley.lyn.music.tv

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import java.io.File
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import top.iwesley.lyn.music.LynMusicAppComponent
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.PlaybackAudioFormat
import top.iwesley.lyn.music.core.model.PlaybackMode
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.derivePlaybackArtworkBackgroundPalette
import top.iwesley.lyn.music.core.model.effectiveAppDisplayDensity
import top.iwesley.lyn.music.core.model.normalizedArtworkCacheLocator
import top.iwesley.lyn.music.core.model.parseNavidromeSongLocator
import top.iwesley.lyn.music.core.model.resolveArtworkCacheTarget
import top.iwesley.lyn.music.core.model.resolveArtworkDecodeSampleSize
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.feature.favorites.FavoritesIntent
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.tv.ui.TvMainTheme

class TvPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER

        setContent {
            val component = TvAppComponentHolder.current()
            if (component == null) {
                TvMainTheme {
                    TvPlayerUnavailableScreen(
                        message = "播放页不可用，请返回主界面重新打开。",
                        onBack = ::finish,
                    )
                }
                return@setContent
            }

            val appDisplayScalePreset by component.appDisplayScalePreset.collectAsState()
            ProvideTvPlayerDensity(appDisplayScalePreset = appDisplayScalePreset) {
                TvPlayerApp(
                    component = component,
                    onBack = ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        internal fun createIntent(context: Context): Intent {
            return Intent(context, TvPlayerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }
}

@Composable
private fun TvPlayerApp(
    component: LynMusicAppComponent,
    onBack: () -> Unit,
) {
    ConfigureTvImageLoader()

    val playerState by component.playerStore.state.collectAsState()
    val favoritesState by component.favoritesStore.state.collectAsState()

    LaunchedEffect(component) {
        component.playerStore.startHydration()
        component.favoritesStore.ensureContentStarted()
    }

    TvMainTheme {
        TvPlayerScreen(
            state = playerState,
            favoriteTrackIds = favoritesState.favoriteTrackIds,
            artworkCacheStore = component.artworkCacheStore,
            onPlayerIntent = component.playerStore::dispatch,
            onToggleFavorite = { track ->
                component.favoritesStore.dispatch(FavoritesIntent.ToggleFavorite(track))
            },
            onBack = onBack,
        )
    }
}

@Composable
private fun TvPlayerScreen(
    state: PlayerState,
    favoriteTrackIds: Set<String>,
    artworkCacheStore: ArtworkCacheStore,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val snapshot = state.snapshot
    val track = snapshot.currentTrack
    if (track == null) {
        TvPlayerUnavailableScreen(
            message = "还没有正在播放的歌曲。",
            onBack = onBack,
        )
        return
    }

    val artworkModel = rememberTvPlayerArtworkModel(
        artworkLocator = snapshot.currentDisplayArtworkLocator,
        artworkCacheKey = trackArtworkCacheKey(track),
        artworkCacheStore = artworkCacheStore,
    )
    val backgroundColors = rememberTvPlayerBackgroundColors(artworkModel)
    val lyricsTitle = remember(
        snapshot.currentDisplayArtistName,
        snapshot.currentDisplayTitle,
        track.title,
    ) {
        val artistName = snapshot.currentDisplayArtistName
            ?.takeIf { it.isNotBlank() }
            ?: "未知艺人"
        val title = snapshot.currentDisplayTitle.ifBlank { track.title }
        "$artistName $title"
    }
    val playPauseFocusRequester = remember { FocusRequester() }
    val queueButtonFocusRequester = remember { FocusRequester() }
    var initialPlayPauseFocusRequested by remember { mutableStateOf(false) }
    LaunchedEffect(state.isQueueVisible) {
        if (!initialPlayPauseFocusRequested && !state.isQueueVisible) {
            withFrameNanos { }
            playPauseFocusRequester.requestFocus()
            initialPlayPauseFocusRequested = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TvPlayerBackground(
            artworkModel = artworkModel,
            colors = backgroundColors,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 56.dp, top = 46.dp, end = 56.dp, bottom = 38.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(38.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvPlayerInfoPane(
                    snapshot = snapshot,
                    track = track,
                    artworkModel = artworkModel,
                    modifier = Modifier
                        .weight(0.48f)
                        .fillMaxHeight(),
                )
                TvPlayerLyricsPane(
                    title = lyricsTitle,
                    state = state,
                    modifier = Modifier
                        .weight(0.52f)
                        .fillMaxHeight(),
                )
            }
            TvPlayerBottomControls(
                snapshot = snapshot,
                isFavorite = track.id in favoriteTrackIds,
                playPauseFocusRequester = playPauseFocusRequester,
                queueButtonFocusRequester = queueButtonFocusRequester,
                onPlayerIntent = onPlayerIntent,
                onToggleFavorite = { onToggleFavorite(track) },
            )
        }
        TvPlayerQueueDrawer(
            state = state,
            queueButtonFocusRequester = queueButtonFocusRequester,
            onPlayerIntent = onPlayerIntent,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun TvPlayerBackground(
    artworkModel: String?,
    colors: TvPlayerBackgroundColors?,
    modifier: Modifier = Modifier,
) {
    val defaultBaseColor = MaterialTheme.colorScheme.background
    val baseColor by animateColorAsState(
        targetValue = colors?.baseColor ?: defaultBaseColor,
        label = "tv-player-background-base",
    )
    val primaryColor by animateColorAsState(
        targetValue = colors?.primaryColor ?: Color.Transparent,
        label = "tv-player-background-primary",
    )
    val secondaryColor by animateColorAsState(
        targetValue = colors?.secondaryColor ?: Color.Transparent,
        label = "tv-player-background-secondary",
    )
    val tertiaryColor by animateColorAsState(
        targetValue = colors?.tertiaryColor ?: Color.Transparent,
        label = "tv-player-background-tertiary",
    )

    Box(
        modifier = modifier.background(baseColor),
    ) {
        if (!artworkModel.isNullOrBlank()) {
            Image(
                painter = rememberAsyncImagePainter(model = artworkModel),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = 1.14f, scaleY = 1.14f)
                    .blur(82.dp)
                    .alpha(0.28f),
            )
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = maxOf(size.width, size.height)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.58f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.10f, size.height * 0.72f),
                    radius = radius * 0.80f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondaryColor.copy(alpha = 0.46f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.96f, size.height * 0.18f),
                    radius = radius * 0.76f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tertiaryColor.copy(alpha = 0.34f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.52f, size.height * 0.48f),
                    radius = radius * 0.64f,
                ),
            )
            drawRect(color = Color.Black.copy(alpha = 0.36f))
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.20f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.48f),
                    ),
                    startY = 0f,
                    endY = size.height,
                ),
            )
        }
    }
}

@Composable
private fun TvPlayerInfoPane(
    snapshot: PlaybackSnapshot,
    track: Track,
    artworkModel: String?,
    modifier: Modifier = Modifier,
) {
    val audioQuality = remember(
        track,
        snapshot.currentPlaybackAudioFormat,
        snapshot.currentNavidromeAudioQuality,
    ) {
        formatTvCurrentPlaybackAudioQuality(
            track = track,
            audioFormat = snapshot.currentPlaybackAudioFormat,
            navidromeQuality = snapshot.currentNavidromeAudioQuality,
        ) ?: formatTvTrackAudioQuality(track)
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TvPlayerArtwork(
            artworkModel = artworkModel,
            modifier = Modifier
                .fillMaxWidth(0.76f)
                .aspectRatio(1f),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = snapshot.currentDisplayTitle.ifBlank { track.title },
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = snapshot.currentDisplayArtistName ?: "未知艺人",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = snapshot.currentDisplayAlbumTitle ?: "未知专辑",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            audioQuality?.let { quality ->
                Text(
                    text = quality,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TvPlayerArtwork(
    artworkModel: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clip(TvPlayerArtworkShape),
    ) {
        TvPlayerArtworkImage(
            model = artworkModel,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun TvPlayerLyricsPane(
    title: String,
    state: PlayerState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.94f),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TvLyricsContent(
            lyrics = state.lyrics,
            highlightedLineIndex = state.highlightedLineIndex,
            isLyricsLoading = state.isLyricsLoading,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun TvLyricsContent(
    lyrics: LyricsDocument?,
    highlightedLineIndex: Int,
    isLyricsLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val visibleLines = remember(lyrics) {
        lyrics?.lines
            ?.mapIndexedNotNull { index, line ->
                line.text.trim().takeIf { it.isNotBlank() }?.let { text ->
                    TvVisibleLyricsLine(rawIndex = index, text = text)
                }
            }
            .orEmpty()
    }
    val highlightedVisibleIndex = remember(visibleLines, highlightedLineIndex) {
        visibleLines.indexOfFirst { it.rawIndex == highlightedLineIndex }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(highlightedVisibleIndex, visibleLines.size) {
        if (highlightedVisibleIndex >= 0) {
            listState.animateScrollToItem((highlightedVisibleIndex - 3).coerceAtLeast(0))
        }
    }

    when {
        isLyricsLoading -> TvPlayerMessagePanel(message = "歌词加载中...", modifier = modifier)
        visibleLines.isEmpty() -> TvPlayerMessagePanel(message = "暂无歌词", modifier = modifier)
        else -> LazyColumn(
            state = listState,
            modifier = modifier.focusGroup(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(visibleLines, key = { _, line -> line.rawIndex }) { _, line ->
                val highlighted = line.rawIndex == highlightedLineIndex
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

@Composable
private fun TvPlayerBottomControls(
    snapshot: PlaybackSnapshot,
    isFavorite: Boolean,
    playPauseFocusRequester: FocusRequester,
    queueButtonFocusRequester: FocusRequester,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TvPlayerProgressArea(
            snapshot = snapshot,
            onPlayerIntent = onPlayerIntent,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onPlayerIntent(PlayerIntent.CycleMode) }) {
                Icon(
                    imageVector = tvPlaybackModeIcon(snapshot.mode),
                    contentDescription = "播放模式",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipPrevious) }) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一首")
            }
            IconButton(
                onClick = { onPlayerIntent(PlayerIntent.TogglePlayPause) },
                modifier = Modifier.focusRequester(playPauseFocusRequester),
            ) {
                Icon(
                    imageVector = if (snapshot.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (snapshot.isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipNext) }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "下一首")
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消喜欢" else "喜欢",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(
                onClick = { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(true)) },
                modifier = Modifier.focusRequester(queueButtonFocusRequester),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "播放列表",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun TvPlayerProgressArea(
    snapshot: PlaybackSnapshot,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    var pendingSeekPositionMs by remember(snapshot.currentTrack?.id, snapshot.durationMs, snapshot.canSeek) {
        mutableStateOf<Long?>(null)
    }
    val duration = snapshot.durationMs.coerceAtLeast(1L)
    val displayPositionMs = (pendingSeekPositionMs ?: snapshot.positionMs).coerceIn(0L, duration)
    val progressFraction = tvPlaybackProgress(displayPositionMs, duration)
    val progressColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.94f),
        label = "tv-player-progress-color",
    )
    val trackColor by animateColorAsState(
        targetValue = if (isFocused) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.16f),
        label = "tv-player-progress-track-color",
    )
    val timeColor by animateColorAsState(
        targetValue = if (isFocused) Color.White.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.68f),
        label = "tv-player-progress-time-color",
    )
    fun updatePendingSeek(deltaMs: Long): Boolean {
        if (!snapshot.canSeek || snapshot.durationMs <= 0L) return false
        pendingSeekPositionMs = ((pendingSeekPositionMs ?: snapshot.positionMs) + deltaMs)
            .coerceIn(0L, snapshot.durationMs)
        return true
    }
    fun commitPendingSeek(): Boolean {
        val target = pendingSeekPositionMs ?: return false
        pendingSeekPositionMs = null
        onPlayerIntent(PlayerIntent.SeekTo(target.coerceIn(0L, snapshot.durationMs)))
        return true
    }

    Column(
        modifier = modifier
            .onFocusChanged { isFocused = it.hasFocus || it.isFocused }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionLeft -> when (event.type) {
                        KeyEventType.KeyDown -> updatePendingSeek(-TV_PLAYER_SEEK_STEP_MS)
                        KeyEventType.KeyUp -> commitPendingSeek()
                        else -> false
                    }
                    Key.DirectionRight -> when (event.type) {
                        KeyEventType.KeyDown -> updatePendingSeek(TV_PLAYER_SEEK_STEP_MS)
                        KeyEventType.KeyUp -> commitPendingSeek()
                        else -> false
                    }
                    else -> false
                }
            }
            .focusable()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTvPlayerDuration(displayPositionMs),
                color = timeColor,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = formatTvPlayerDuration(snapshot.durationMs),
                color = timeColor,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            val trackHeightPx = with(LocalDensity.current) { 5.dp.toPx() }
            TvRoundedSliderTrack(
                progressFraction = progressFraction,
                progressColor = progressColor,
                trackColor = trackColor,
                trackHeightPx = trackHeightPx,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
            )
            Slider(
                value = displayPositionMs.toFloat(),
                onValueChange = { value ->
                    if (snapshot.canSeek && snapshot.durationMs > 0L) {
                        pendingSeekPositionMs = value.toLong().coerceIn(0L, snapshot.durationMs)
                    }
                },
                onValueChangeFinished = { commitPendingSeek() },
                valueRange = 0f..duration.toFloat(),
                enabled = snapshot.canSeek && snapshot.durationMs > 0L,
                colors = tvPlayerTransparentSliderColors(progressColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .graphicsLayer(scaleY = 0.44f),
            )
        }
    }
}

@Composable
private fun TvRoundedSliderTrack(
    progressFraction: Float,
    progressColor: Color,
    trackColor: Color,
    trackHeightPx: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val trackWidth = size.width.coerceAtLeast(0f)
        if (trackWidth <= 0f || trackHeightPx <= 0f) return@Canvas
        val top = (size.height - trackHeightPx) / 2f
        val radius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, top),
            size = Size(trackWidth, trackHeightPx),
            cornerRadius = radius,
        )
        val activeWidth = (trackWidth * progressFraction.coerceIn(0f, 1f)).coerceIn(0f, trackWidth)
        if (activeWidth > 0f) {
            drawRoundRect(
                color = progressColor,
                topLeft = Offset(0f, top),
                size = Size(activeWidth, trackHeightPx),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun tvPlayerTransparentSliderColors(thumbColor: Color) = SliderDefaults.colors(
    thumbColor = thumbColor,
    activeTrackColor = Color.Transparent,
    inactiveTrackColor = Color.Transparent,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
    disabledThumbColor = Color.Transparent,
    disabledActiveTrackColor = Color.Transparent,
    disabledInactiveTrackColor = Color.Transparent,
)

@Composable
private fun TvPlayerQueueDrawer(
    state: PlayerState,
    queueButtonFocusRequester: FocusRequester,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    val listState = rememberLazyListState()
    val closeFocusRequester = remember { FocusRequester() }
    val queueItemFocusRequester = remember { FocusRequester() }
    var queueWasVisible by remember { mutableStateOf(false) }
    val focusTargetIndex = remember(state.isQueueVisible, snapshot.currentIndex, snapshot.queue.size) {
        when {
            !state.isQueueVisible || snapshot.queue.isEmpty() -> -1
            snapshot.currentIndex in snapshot.queue.indices -> snapshot.currentIndex
            else -> 0
        }
    }
    LaunchedEffect(state.isQueueVisible, focusTargetIndex, snapshot.queue.size) {
        if (!state.isQueueVisible) return@LaunchedEffect
        if (focusTargetIndex >= 0) {
            listState.scrollToItem((focusTargetIndex - 2).coerceAtLeast(0))
            withFrameNanos { }
            queueItemFocusRequester.requestFocus()
        } else {
            withFrameNanos { }
            closeFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(state.isQueueVisible) {
        if (state.isQueueVisible) {
            queueWasVisible = true
        } else if (queueWasVisible) {
            withFrameNanos { }
            queueButtonFocusRequester.requestFocus()
            queueWasVisible = false
        }
    }
    if (state.isQueueVisible) {
        BackHandler(onBack = { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(false)) })
    }

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = state.isQueueVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 140)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.44f))
                    .clickable { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(false)) },
            )
        }
        AnimatedVisibility(
            visible = state.isQueueVisible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(durationMillis = 160)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing),
            ) + fadeOut(animationSpec = tween(durationMillis = 140)),
        ) {
            TvPlayerQueuePanel(
                snapshot = snapshot,
                listState = listState,
                focusTargetIndex = focusTargetIndex,
                queueItemFocusRequester = queueItemFocusRequester,
                closeFocusRequester = closeFocusRequester,
                onClose = { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(false)) },
                onPlayQueueIndex = { index -> onPlayerIntent(PlayerIntent.PlayQueueIndex(index)) },
            )
        }
    }
}

@Composable
private fun TvPlayerQueuePanel(
    snapshot: PlaybackSnapshot,
    listState: LazyListState,
    focusTargetIndex: Int,
    queueItemFocusRequester: FocusRequester,
    closeFocusRequester: FocusRequester,
    onClose: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(430.dp)
            .clip(TvPlayerQueuePanelShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .padding(horizontal = 22.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "播放列表",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "${snapshot.queue.size} 首 · ${tvPlaybackModeLabel(snapshot.mode)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.focusRequester(closeFocusRequester),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭播放列表",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (snapshot.queue.isEmpty()) {
            TvPlayerMessagePanel(
                message = "当前没有播放列表",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(snapshot.queue, key = { _, track -> track.id }) { index, track ->
                    TvPlayerQueueTrackRow(
                        track = track,
                        index = index,
                        isCurrent = index == snapshot.currentIndex,
                        isPlaying = index == snapshot.currentIndex && snapshot.isPlaying,
                        focusRequester = queueItemFocusRequester.takeIf { index == focusTargetIndex },
                        onClick = { onPlayQueueIndex(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvPlayerQueueTrackRow(
    track: Track,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val containerColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White.copy(alpha = 0.18f)
            isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            else -> Color.White.copy(alpha = 0.07f)
        },
        label = "tv-player-queue-row-container",
    )
    val contentColor = if (isFocused) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(TvPlayerQueueRowShape)
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.60f),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(30.dp),
        )
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.58f),
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = track.title,
                color = contentColor,
                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistName?.takeIf { it.isNotBlank() } ?: "未知艺人",
                color = Color.White.copy(alpha = if (isFocused) 0.82f else 0.62f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatTvPlayerDuration(track.durationMs),
            color = Color.White.copy(alpha = if (isFocused) 0.82f else 0.60f),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun TvPlayerArtworkImage(
    model: String?,
    modifier: Modifier = Modifier,
) {
    val painter = rememberAsyncImagePainter(model = model)
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(82.dp),
            )
        } else {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun rememberTvPlayerArtworkModel(
    artworkLocator: String?,
    artworkCacheKey: String?,
    artworkCacheStore: ArtworkCacheStore,
): String? {
    val model by produceState<String?>(initialValue = null, artworkLocator, artworkCacheKey, artworkCacheStore) {
        val normalized = normalizedArtworkCacheLocator(artworkLocator)
        value = when {
            normalized == null -> null
            artworkCacheKey != null -> artworkCacheStore.cache(normalized, artworkCacheKey)
                ?: resolveArtworkCacheTarget(normalized)
            else -> resolveArtworkCacheTarget(normalized)
        }
    }
    return model
}

@Composable
private fun rememberTvPlayerBackgroundColors(
    artworkModel: String?,
): TvPlayerBackgroundColors? {
    val colors by produceState<TvPlayerBackgroundColors?>(initialValue = null, artworkModel) {
        value = withContext(Dispatchers.IO) {
            deriveTvPlayerBackgroundColors(artworkModel)
        }
    }
    return colors
}

@Composable
private fun TvPlayerMessagePanel(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun TvPlayerUnavailableScreen(
    message: String,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.48f)
                .clip(TvPlayerPanelShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "无法打开播放页",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun ProvideTvPlayerDensity(
    appDisplayScalePreset: AppDisplayScalePreset,
    content: @Composable () -> Unit,
) {
    val currentDensity = LocalDensity.current
    val fixedDensity = remember(currentDensity.fontScale, appDisplayScalePreset) {
        Density(
            density = effectiveAppDisplayDensity(tvPlayerStableDensityScale(), appDisplayScalePreset),
            fontScale = currentDensity.fontScale,
        )
    }
    CompositionLocalProvider(LocalDensity provides fixedDensity) {
        content()
    }
}

private fun tvPlaybackModeIcon(mode: PlaybackMode): ImageVector {
    return when (mode) {
        PlaybackMode.ORDER -> Icons.Rounded.Repeat
        PlaybackMode.SHUFFLE -> Icons.Rounded.Shuffle
        PlaybackMode.REPEAT_ONE -> Icons.Rounded.RepeatOne
    }
}

private fun tvPlaybackModeLabel(mode: PlaybackMode): String {
    return when (mode) {
        PlaybackMode.ORDER -> "顺序播放"
        PlaybackMode.SHUFFLE -> "随机播放"
        PlaybackMode.REPEAT_ONE -> "单曲循环"
    }
}

private fun tvPlaybackProgress(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

private fun formatTvCurrentPlaybackAudioQuality(
    track: Track,
    audioFormat: PlaybackAudioFormat?,
    navidromeQuality: NavidromeAudioQuality?,
): String? {
    val navidromeFallbackBitRate = navidromeQuality
        ?.takeIf { parseNavidromeSongLocator(track.mediaLocator) != null }
        ?.let(::formatTvNavidromeBitRateFallback)
    return listOfNotNull(
        audioFormat?.samplingRateHz?.takeIf { it > 0 }?.let(::formatTvSamplingRate),
        audioFormat?.bitRateBps?.takeIf { it > 0 }?.let(::formatTvPlaybackBitRate) ?: navidromeFallbackBitRate,
        audioFormat?.channelCount?.takeIf { it > 0 }?.let { "${it}ch" },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun formatTvTrackAudioQuality(track: Track): String? {
    return listOfNotNull(
        track.bitDepth?.takeIf { it > 0 }?.let { "${it}bit" },
        track.samplingRate?.takeIf { it > 0 }?.let(::formatTvSamplingRate),
        track.bitRate?.takeIf { it > 0 }?.let { "${it}kbps" },
        track.channelCount?.takeIf { it > 0 }?.let { "${it}ch" },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun formatTvNavidromeBitRateFallback(quality: NavidromeAudioQuality): String {
    return quality.maxBitRateKbps?.let { "${it}kbps" } ?: "原始"
}

private fun formatTvSamplingRate(samplingRateHz: Int): String {
    if (samplingRateHz % 1_000 == 0) return "${samplingRateHz / 1_000}kHz"
    val rounded = (samplingRateHz / 100.0).roundToInt() / 10.0
    return "${rounded}kHz"
}

private fun formatTvPlaybackBitRate(bitRateBps: Int): String {
    return "${(bitRateBps / 1_000.0).roundToInt()}kbps"
}

private fun formatTvPlayerDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun deriveTvPlayerBackgroundColors(artworkModel: String?): TvPlayerBackgroundColors? {
    if (artworkModel.isNullOrBlank()) return null
    return runCatching {
        val bitmap = decodeTvPlayerBackgroundBitmap(artworkModel, TV_PLAYER_PALETTE_DECODE_SIZE_PX) ?: return@runCatching null
        try {
            val palette = derivePlaybackArtworkBackgroundPalette(sampleTvPlayerBitmapPixels(bitmap))
                ?: return@runCatching null
            TvPlayerBackgroundColors(
                baseColor = composeTvColorFromArgb(palette.baseColorArgb),
                primaryColor = composeTvColorFromArgb(palette.primaryColorArgb),
                secondaryColor = composeTvColorFromArgb(palette.secondaryColorArgb),
                tertiaryColor = composeTvColorFromArgb(palette.tertiaryColorArgb),
            )
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()
}

private fun decodeTvPlayerBackgroundBitmap(target: String, maxDecodeSizePx: Int): Bitmap? {
    return when {
        target.startsWith("http://", ignoreCase = true) ||
            target.startsWith("https://", ignoreCase = true) -> {
            val payload = URL(target).openStream().use { it.readBytes() }
            decodeTvPlayerArtworkBytes(payload, maxDecodeSizePx)
        }
        target.startsWith("file://", ignoreCase = true) -> {
            val path = runCatching { File(URI(target)).absolutePath }
                .getOrElse { target.removePrefix("file://") }
            decodeTvPlayerArtworkFile(path, maxDecodeSizePx)
        }
        target.startsWith("content://", ignoreCase = true) -> null
        else -> decodeTvPlayerArtworkFile(target, maxDecodeSizePx)
    }
}

private fun decodeTvPlayerArtworkBytes(
    payload: ByteArray,
    maxDecodeSizePx: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(payload, 0, payload.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    return BitmapFactory.decodeByteArray(
        payload,
        0,
        payload.size,
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = resolveArtworkDecodeSampleSize(
                sourceWidth = bounds.outWidth,
                sourceHeight = bounds.outHeight,
                targetSize = maxDecodeSizePx.coerceAtLeast(1),
            )
        },
    )
}

private fun decodeTvPlayerArtworkFile(
    path: String,
    maxDecodeSizePx: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = resolveArtworkDecodeSampleSize(
                sourceWidth = bounds.outWidth,
                sourceHeight = bounds.outHeight,
                targetSize = maxDecodeSizePx.coerceAtLeast(1),
            )
        },
    )
}

private fun sampleTvPlayerBitmapPixels(bitmap: Bitmap): List<Int> {
    val stepX = max(1, bitmap.width / 24)
    val stepY = max(1, bitmap.height / 24)
    return buildList {
        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                add(bitmap.getPixel(x, y))
            }
        }
    }
}

private fun composeTvColorFromArgb(argb: Int): Color {
    return Color(
        red = ((argb ushr 16) and 0xFF) / 255f,
        green = ((argb ushr 8) and 0xFF) / 255f,
        blue = (argb and 0xFF) / 255f,
        alpha = ((argb ushr 24) and 0xFF) / 255f,
    )
}

private fun tvPlayerStableDensityScale(): Float {
    val stableDpi = DisplayMetrics.DENSITY_DEVICE_STABLE.takeIf { it > 0 } ?: DisplayMetrics.DENSITY_DEFAULT
    return stableDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()
}

private data class TvPlayerBackgroundColors(
    val baseColor: Color,
    val primaryColor: Color,
    val secondaryColor: Color,
    val tertiaryColor: Color,
)

private data class TvVisibleLyricsLine(
    val rawIndex: Int,
    val text: String,
)

private val TvPlayerPanelShape = RoundedCornerShape(22.dp)
private val TvPlayerArtworkShape = RoundedCornerShape(26.dp)
private val TvPlayerQueuePanelShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
private val TvPlayerQueueRowShape = RoundedCornerShape(16.dp)
private const val TV_PLAYER_SEEK_STEP_MS = 10_000L
private const val TV_PLAYER_PALETTE_DECODE_SIZE_PX = 384
