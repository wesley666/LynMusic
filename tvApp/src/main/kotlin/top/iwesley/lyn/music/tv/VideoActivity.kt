package top.iwesley.lyn.music.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class VideoActivity : ComponentActivity() {
    private val playbackSession by lazy {
        TvRendererActivityPlaybackSession(this, TvRendererRoute.Video)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        TvUpnpRendererService.start(this)
        setContent {
            val state by TvUpnpRendererRouter.state.collectAsState()
            val player by playbackSession.player.collectAsState()
            VideoRendererTheme {
                VideoRendererScreen(
                    player = player,
                    state = state,
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
private fun VideoRendererTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White,
            background = Color.Black,
            surface = Color.Black,
            onSurface = Color.White,
        ),
        content = content,
    )
}

@Composable
private fun VideoRendererScreen(
    player: Player?,
    state: TvRendererSessionState,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            update = { playerView ->
                playerView.player = player
            },
            modifier = Modifier.fillMaxSize(),
        )
        LinearProgressIndicator(
            progress = { videoProgress(state) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 28.dp)
                .height(5.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.24f),
        )
    }
}

private fun videoProgress(state: TvRendererSessionState): Float {
    if (state.route != TvRendererRoute.Video || state.durationMs <= 0L) return 0f
    return (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
}
