package top.iwesley.lyn.music.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import top.iwesley.lyn.music.core.model.AppleMediaLocatorResolver
import top.iwesley.lyn.music.core.model.AppleResolvedMediaLocator
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.core.model.Track

internal class ApplePlaybackGateway(
    private val platformLabel: String,
) : PlaybackGateway {
    private val player = AppleNativePlayer(platformLabel)
    private val mutableState = MutableStateFlow(PlaybackGatewayState(volume = 1f))

    override val state: StateFlow<PlaybackGatewayState> = mutableState.asStateFlow()

    init {
        AppleAudioSessionCoordinator.configureForPlayback()
        player.onProgress = { publishState() }
        player.onCompleted = {
            mutableState.update {
                it.copy(
                    isPlaying = false,
                    positionMs = 0L,
                    completionCount = it.completionCount + 1,
                    errorMessage = null,
                )
            }
        }
        player.onFailed = { errorMessage ->
            publishState(errorOverride = errorMessage ?: "$platformLabel 播放失败。")
        }
    }

    override suspend fun load(track: Track, playWhenReady: Boolean, startPositionMs: Long) {
        when (val resolved = AppleMediaLocatorResolver.resolve(track.mediaLocator)) {
            is AppleResolvedMediaLocator.Unsupported -> {
                player.pause()
                mutableState.update {
                    it.copy(
                        isPlaying = false,
                        positionMs = 0L,
                        durationMs = track.durationMs,
                        errorMessage = resolved.message,
                    )
                }
            }

            else -> {
                player.load(resolved)
                if (startPositionMs > 0L) {
                    player.seekTo(startPositionMs)
                }
                if (playWhenReady) {
                    player.play()
                } else {
                    player.pause()
                }
                mutableState.update {
                    it.copy(
                        isPlaying = playWhenReady,
                        positionMs = startPositionMs.coerceAtLeast(0L),
                        durationMs = track.durationMs.coerceAtLeast(0L),
                        errorMessage = null,
                    )
                }
                publishState()
            }
        }
    }

    override suspend fun play() {
        player.play()
        publishState()
    }

    override suspend fun pause() {
        player.pause()
        publishState()
    }

    override suspend fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        mutableState.update { it.copy(positionMs = positionMs.coerceAtLeast(0L), errorMessage = null) }
        publishState()
    }

    override suspend fun setVolume(volume: Float) {
        player.setVolume(volume)
        publishState()
    }

    override suspend fun release() {
        player.release()
        AppleAudioSessionCoordinator.deactivate()
    }

    private fun publishState(errorOverride: String? = null) {
        mutableState.update {
            it.copy(
                isPlaying = player.isPlaying(),
                positionMs = player.positionMs().coerceAtLeast(0L),
                durationMs = player.durationMs()?.takeIf { value -> value > 0L } ?: it.durationMs,
                volume = player.volume().coerceIn(0f, 1f),
                errorMessage = errorOverride ?: player.errorMessage(),
            )
        }
    }
}
