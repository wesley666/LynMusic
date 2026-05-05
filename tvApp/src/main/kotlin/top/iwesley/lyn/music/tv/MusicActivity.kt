package top.iwesley.lyn.music.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

class MusicActivity : ComponentActivity() {
    private val playbackSession by lazy {
        TvRendererActivityPlaybackSession(this, TvRendererRoute.Music)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        TvUpnpRendererService.start(this)
        setContent {
            ConfigureTvImageLoader()
            val state by TvUpnpRendererRouter.state.collectAsState()
            MusicRendererTheme {
                MusicRendererScreen(
                    state = state,
                    onPlay = TvUpnpRendererRouter::play,
                    onPause = TvUpnpRendererRouter::pause,
                    onSeek = TvUpnpRendererRouter::seekTo,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        attachRendererSession()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        attachRendererSession()
    }

    private fun attachRendererSession() {
        TvUpnpRendererService.start(this)
        playbackSession.start()
        TvUpnpRendererRouter.registerSession(playbackSession)
    }

    override fun onStop() {
        playbackSession.stopAndRelease()
        TvUpnpRendererRouter.unregisterSession(playbackSession)
        super.onStop()
    }
}

@Composable
private fun MusicRendererTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFE03131),
            background = Color(0xFF0B0D10),
            surface = Color(0xFF151922),
            surfaceVariant = Color(0xFF1D222C),
            onPrimary = Color.White,
            onSurface = Color(0xFFF5F5F6),
            onSurfaceVariant = Color(0xFFC9CDD4),
            error = Color(0xFFFFB4AB),
        ),
        content = content,
    )
}

@Composable
private fun MusicRendererScreen(
    state: TvRendererSessionState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val progressFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        progressFocusRequester.requestFocus()
    }

    val backgroundColors = rememberTvPlaybackBackgroundColors(state.artworkUri)
    Box(modifier = Modifier.fillMaxSize()) {
        TvPlaybackArtworkBackground(
            artworkModel = state.artworkUri,
            colors = backgroundColors,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 72.dp, top = 52.dp, end = 72.dp, bottom = 44.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(54.dp),
            ) {
                ArtworkPanel(
                    artworkUri = state.artworkUri,
                    modifier = Modifier
                        .weight(0.40f)
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
                MusicRendererInfoPanel(
                    state = state,
                    modifier = Modifier
                        .weight(0.60f)
                        .fillMaxHeight(),
                )
            }
            MusicRendererBottomBar(
                state = state,
                progressFocusRequester = progressFocusRequester,
                onSeek = onSeek,
                onTogglePlayPause = {
                    if (state.status == TvRendererPlaybackStatus.Playing) {
                        onPause()
                    } else {
                        onPlay()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MusicRendererInfoPanel(
    state: TvRendererSessionState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        state.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(state.artistName, state.albumTitle).joinToString(" · ")
                .ifBlank { "来自外部投屏" },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
        LyricsPlaceholder(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 38.dp),
        )
    }
}

@Composable
private fun LyricsPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "暂无歌词",
            color = Color.White.copy(alpha = 0.62f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtworkPanel(artworkUri: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.76f),
                modifier = Modifier.size(128.dp),
            )
        }
    }
}

@Composable
private fun MusicRendererBottomBar(
    state: TvRendererSessionState,
    progressFocusRequester: FocusRequester,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        MusicRendererProgressArea(
            state = state,
            focusRequester = progressFocusRequester,
            onSeek = onSeek,
            onTogglePlayPause = onTogglePlayPause,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier.size(66.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (state.status == TvRendererPlaybackStatus.Playing) {
                    Icons.Rounded.Pause
                } else {
                    Icons.Rounded.PlayArrow
                },
                contentDescription = if (state.status == TvRendererPlaybackStatus.Playing) "暂停" else "播放",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(42.dp),
            )
        }
    }
}

@Composable
private fun MusicRendererProgressArea(
    state: TvRendererSessionState,
    focusRequester: FocusRequester,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    var pendingSeekPositionMs by remember(state.uri, state.durationMs) {
        mutableStateOf<Long?>(null)
    }
    val duration = state.durationMs.coerceAtLeast(1L)
    val displayPositionMs = (pendingSeekPositionMs ?: state.positionMs).coerceIn(0L, duration)
    val progressFraction = musicRendererProgress(displayPositionMs, duration)
    val progressColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.94f),
        label = "music-renderer-progress-color",
    )
    val trackColor by animateColorAsState(
        targetValue = if (isFocused) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.16f),
        label = "music-renderer-progress-track-color",
    )
    val timeColor by animateColorAsState(
        targetValue = if (isFocused) Color.White.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.68f),
        label = "music-renderer-progress-time-color",
    )

    fun updatePendingSeek(deltaMs: Long): Boolean {
        if (!state.hasMedia || state.durationMs <= 0L) return false
        pendingSeekPositionMs = ((pendingSeekPositionMs ?: state.positionMs) + deltaMs)
            .coerceIn(0L, state.durationMs)
        return true
    }

    fun commitPendingSeek(): Boolean {
        val target = pendingSeekPositionMs ?: return false
        pendingSeekPositionMs = null
        onSeek(target.coerceIn(0L, state.durationMs))
        return true
    }

    Row(
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.hasFocus || it.isFocused }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionLeft -> when (event.type) {
                        KeyEventType.KeyDown -> updatePendingSeek(-MUSIC_RENDERER_SEEK_STEP_MS)
                        KeyEventType.KeyUp -> commitPendingSeek()
                        else -> false
                    }

                    Key.DirectionRight -> when (event.type) {
                        KeyEventType.KeyDown -> updatePendingSeek(MUSIC_RENDERER_SEEK_STEP_MS)
                        KeyEventType.KeyUp -> commitPendingSeek()
                        else -> false
                    }

                    else -> if (event.key.isConfirmKey()) {
                        if (event.type == KeyEventType.KeyDown) {
                            true
                        } else if (event.type == KeyEventType.KeyUp) {
                            onTogglePlayPause()
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
            }
            .clip(RoundedCornerShape(16.dp))
            .focusable()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = formatDuration(displayPositionMs),
            color = timeColor,
            style = MaterialTheme.typography.labelLarge,
        )
        MusicRendererProgressTrack(
            progressFraction = progressFraction,
            progressColor = progressColor,
            trackColor = trackColor,
            modifier = Modifier
                .weight(1f)
                .height(18.dp),
        )
        Text(
            text = formatDuration(state.durationMs),
            color = timeColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun MusicRendererProgressTrack(
    progressFraction: Float,
    progressColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val trackHeightPx = with(LocalDensity.current) { 5.dp.toPx() }
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

private fun Key.isConfirmKey(): Boolean {
    return this == Key.DirectionCenter ||
        this == Key.Enter ||
        this == Key.NumPadEnter
}

private fun musicRendererProgress(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private const val MUSIC_RENDERER_SEEK_STEP_MS = 10_000L
