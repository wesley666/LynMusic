package top.iwesley.lyn.music.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
                    onStop = TvUpnpRendererRouter::stopPlayback,
                    onBack = ::finish,
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
            primary = Color(0xFF7DD3FC),
            background = Color(0xFF0E1116),
            surface = Color(0xFF171B22),
            surfaceVariant = Color(0xFF242A34),
            onPrimary = Color(0xFF082F49),
            onSurface = Color(0xFFF2F5F8),
            onSurfaceVariant = Color(0xFFB9C2CC),
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
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 72.dp, vertical = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            ArtworkPanel(
                artworkUri = state.artworkUri,
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Column(
                modifier = Modifier.weight(0.58f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = statusLabel(state),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.errorMessage != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = listOfNotNull(state.artistName, state.albumTitle).joinToString(" · ")
                        .ifBlank { "来自外部投屏" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(42.dp))
                PlaybackProgress(state)
                Spacer(Modifier.height(34.dp))
                ControlRow(
                    state = state,
                    onPlay = onPlay,
                    onPause = onPause,
                    onStop = onStop,
                    onBack = onBack,
                )
            }
        }
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
private fun PlaybackProgress(state: TvRendererSessionState) {
    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = formatDuration(state.positionMs),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = formatDuration(state.durationMs),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ControlRow(
    state: TvRendererSessionState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    val hasMedia = state.hasMedia
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = if (state.status == TvRendererPlaybackStatus.Playing) onPause else onPlay,
            enabled = hasMedia,
            modifier = Modifier.height(56.dp),
        ) {
            Icon(
                imageVector = if (state.status == TvRendererPlaybackStatus.Playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(if (state.status == TvRendererPlaybackStatus.Playing) "暂停" else "播放")
        }
        Button(
            onClick = onStop,
            enabled = hasMedia,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.height(56.dp),
        ) {
            Icon(Icons.Rounded.Stop, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("停止")
        }
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.height(56.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("返回")
        }
    }
}

private fun statusLabel(state: TvRendererSessionState): String {
    state.errorMessage?.takeIf { it.isNotBlank() }?.let { return it }
    if (!state.rendererRunning) return "DLNA 接收未启动"
    return when (state.status) {
        TvRendererPlaybackStatus.Idle -> "等待音乐投屏"
        TvRendererPlaybackStatus.Ready -> "已接收音乐"
        TvRendererPlaybackStatus.Buffering -> "正在缓冲"
        TvRendererPlaybackStatus.Playing -> "正在投屏播放"
        TvRendererPlaybackStatus.Paused -> "已暂停"
        TvRendererPlaybackStatus.Stopped -> "已停止"
        TvRendererPlaybackStatus.Failed -> "投屏播放失败"
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
