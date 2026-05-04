package top.iwesley.lyn.music.tv

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.iwesley.lyn.music.cast.upnp.android.UpnpRendererMedia

internal class TvRendererActivityPlaybackSession(
    context: Context,
    override val route: TvRendererRoute,
) : TvRendererPlaybackSession {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutablePlayer = MutableStateFlow<ExoPlayer?>(null)
    private var currentMedia: UpnpRendererMedia? = null
    private var volumePercent: Int = 100
    private var muted: Boolean = false
    private var progressScheduled = false

    val player: StateFlow<ExoPlayer?> = mutablePlayer.asStateFlow()

    fun start() {
        runOnMain {
            ensurePlayer()
            scheduleProgressSync()
        }
    }

    fun stopAndRelease() {
        runOnMain {
            stop()
            progressScheduled = false
            mutablePlayer.value?.release()
            mutablePlayer.value = null
            currentMedia = null
        }
    }

    override fun setMedia(media: UpnpRendererMedia): Boolean {
        runOnMain {
            currentMedia = media
            val mediaItem = MediaItem.Builder()
                .setUri(media.uri)
                .apply {
                    media.mimeType?.takeIf { it.isNotBlank() }?.let(::setMimeType)
                }
                .build()
            ensurePlayer().apply {
                setMediaItem(mediaItem)
                prepare()
            }
            TvUpnpRendererRouter.updateSessionPlaybackState(
                session = this,
                status = TvRendererPlaybackStatus.Ready,
                positionMs = 0L,
                durationMs = media.durationMs,
            )
        }
        return true
    }

    override fun play(): Boolean {
        runOnMain {
            ensurePlayer().play()
            syncPlayerState()
        }
        return true
    }

    override fun pause(): Boolean {
        runOnMain {
            mutablePlayer.value?.pause()
            syncPlayerState()
        }
        return true
    }

    override fun stop(): Boolean {
        runOnMain {
            mutablePlayer.value?.pause()
            mutablePlayer.value?.seekTo(0L)
            TvUpnpRendererRouter.updateSessionPlaybackState(
                session = this,
                status = if (currentMedia != null) TvRendererPlaybackStatus.Stopped else TvRendererPlaybackStatus.Idle,
                positionMs = 0L,
                durationMs = currentDurationMs(),
            )
        }
        return true
    }

    override fun seekTo(positionMs: Long): Boolean {
        runOnMain {
            mutablePlayer.value?.seekTo(positionMs.coerceAtLeast(0L))
            syncPlayerState()
        }
        return true
    }

    override fun setVolume(volumePercent: Int): Boolean {
        runOnMain {
            this.volumePercent = volumePercent.coerceIn(0, 100)
            applyPlayerVolume()
        }
        return true
    }

    override fun setMute(muted: Boolean): Boolean {
        runOnMain {
            this.muted = muted
            applyPlayerVolume()
        }
        return true
    }

    private fun ensurePlayer(): ExoPlayer {
        mutablePlayer.value?.let { return it }
        return ExoPlayer.Builder(appContext).build().also { exoPlayer ->
            if (route == TvRendererRoute.Music) {
                exoPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                )
            }
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    syncPlayerState()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    syncPlayerState()
                }

                override fun onPlayerError(error: PlaybackException) {
                    TvUpnpRendererRouter.updateSessionPlaybackState(
                        session = this@TvRendererActivityPlaybackSession,
                        status = TvRendererPlaybackStatus.Failed,
                        positionMs = currentPositionMs(),
                        durationMs = currentDurationMs(),
                        errorMessage = error.message ?: "投屏播放失败",
                    )
                }
            })
            mutablePlayer.value = exoPlayer
            applyPlayerVolume()
        }
    }

    private fun syncPlayerState() {
        val exoPlayer = mutablePlayer.value ?: return
        val status = when {
            exoPlayer.playbackState == Player.STATE_BUFFERING -> TvRendererPlaybackStatus.Buffering
            exoPlayer.isPlaying -> TvRendererPlaybackStatus.Playing
            exoPlayer.playbackState == Player.STATE_ENDED -> TvRendererPlaybackStatus.Stopped
            currentMedia != null -> TvRendererPlaybackStatus.Paused
            else -> TvRendererPlaybackStatus.Idle
        }
        TvUpnpRendererRouter.updateSessionPlaybackState(
            session = this,
            status = status,
            positionMs = currentPositionMs(),
            durationMs = currentDurationMs(),
        )
    }

    private fun currentPositionMs(): Long {
        return mutablePlayer.value?.currentPosition?.coerceAtLeast(0L) ?: 0L
    }

    private fun currentDurationMs(): Long {
        val playerDuration = mutablePlayer.value?.duration
            ?.takeIf { it != C.TIME_UNSET && it > 0L }
        return playerDuration ?: currentMedia?.durationMs?.coerceAtLeast(0L) ?: 0L
    }

    private fun applyPlayerVolume() {
        mutablePlayer.value?.volume = if (muted) {
            0f
        } else {
            volumePercent.coerceIn(0, 100) / 100f
        }
    }

    private fun scheduleProgressSync() {
        if (progressScheduled) return
        progressScheduled = true
        mainHandler.post(object : Runnable {
            override fun run() {
                if (!progressScheduled) return
                syncPlayerState()
                mainHandler.postDelayed(this, PROGRESS_SYNC_INTERVAL_MS)
            }
        })
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}

private const val PROGRESS_SYNC_INTERVAL_MS = 1_000L
