package top.iwesley.lyn.music.platform

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

@UnstableApi
internal class NavidromeLoadErrorHandlingPolicy(@Volatile var recoveringOpen: Boolean = false) : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val error = loadErrorInfo.exception
        // ProgressiveMediaPeriod preserves byte position when the extractor knows the duration.
        // An unopened source reports zero; track metadata and X-Content-Duration do not populate this value.
        val nativeDurationKnown = loadErrorInfo.mediaLoadData.mediaEndTimeMs > 0L
        if (!nativeDurationKnown && (error.isNavidromeSequentialReadFailure() ||
            (recoveringOpen && error is HttpDataSource.HttpDataSourceException &&
                error.type == HttpDataSource.HttpDataSourceException.TYPE_OPEN))) return C.TIME_UNSET
        return super.getRetryDelayMsFor(loadErrorInfo)
    }
}

@UnstableApi
internal class ExoNavidromePlaybackDriver(private val player: ExoPlayer) : NavidromePlaybackDriver {
    private val handler = Handler(player.applicationLooper)
    private var currentErrorPolicy: NavidromeLoadErrorHandlingPolicy? = null
    override val mediaId get() = player.currentMediaItem?.mediaId
    override val positionMs get() = player.currentPosition
    override val durationMs get() = player.duration
    override val seekable get() = player.isCurrentMediaItemSeekable
    override val ready get() = player.playbackState == Player.STATE_READY
    override val ended get() = player.playbackState == Player.STATE_ENDED

    override fun open(url: String, mediaId: String, playWhenReady: Boolean, readRecovery: Boolean, onResponse: (NavidromeStreamResponse) -> Unit) {
        val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        val errorPolicy = NavidromeLoadErrorHandlingPolicy(readRecovery)
        currentErrorPolicy = errorPolicy
        val factory = DataSource.Factory {
            AndroidNavidromeDataSource(httpFactory.createDataSource(), { response ->
                // The session rejects responses after close or replacement, including already queued callbacks.
                handler.post { onResponse(response) }
            }, readRecovery = readRecovery)
        }
        val item = MediaItem.Builder().setUri(url).setMediaId(mediaId).build()
        player.playWhenReady = playWhenReady
        player.setMediaSource(ProgressiveMediaSource.Factory(factory)
            .setLoadErrorHandlingPolicy(errorPolicy).createMediaSource(item))
        player.prepare()
    }

    override fun seek(positionMs: Long) {
        currentErrorPolicy?.recoveringOpen = false
        player.seekTo(positionMs)
    }
    override fun setPlaying(playing: Boolean) { player.playWhenReady = playing }
    override fun scheduleRecovery(delayMs: Long, action: () -> Unit): () -> Unit {
        val task = Runnable(action)
        handler.postDelayed(task, delayMs)
        return { handler.removeCallbacks(task) }
    }
}
