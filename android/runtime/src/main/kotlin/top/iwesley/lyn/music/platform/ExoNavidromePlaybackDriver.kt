package top.iwesley.lyn.music.platform

import android.os.Handler
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource

@UnstableApi
internal class ExoNavidromePlaybackDriver(private val player: ExoPlayer) : NavidromePlaybackDriver {
    private val handler = Handler(player.applicationLooper)
    override val mediaId get() = player.currentMediaItem?.mediaId
    override val positionMs get() = player.currentPosition
    override val durationMs get() = player.duration
    override val seekable get() = player.isCurrentMediaItemSeekable
    override val ready get() = player.playbackState == Player.STATE_READY
    override val ended get() = player.playbackState == Player.STATE_ENDED

    override fun open(url: String, mediaId: String, playWhenReady: Boolean, onResponse: (NavidromeStreamResponse) -> Unit) {
        val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        val factory = DataSource.Factory {
            AndroidNavidromeDataSource(httpFactory.createDataSource(), { response ->
                // The session rejects responses after close or replacement, including already queued callbacks.
                handler.post { onResponse(response) }
            })
        }
        val item = MediaItem.Builder().setUri(url).setMediaId(mediaId).build()
        player.playWhenReady = playWhenReady
        player.setMediaSource(ProgressiveMediaSource.Factory(factory).createMediaSource(item))
        player.prepare()
    }

    override fun seek(positionMs: Long) { player.seekTo(positionMs) }
    override fun setPlaying(playing: Boolean) { player.playWhenReady = playing }
}
