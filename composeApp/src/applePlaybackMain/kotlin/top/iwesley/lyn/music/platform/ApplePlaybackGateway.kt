package top.iwesley.lyn.music.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import top.iwesley.lyn.music.core.model.AppleMediaLocatorResolver
import top.iwesley.lyn.music.core.model.AppleResolvedMediaLocator
import top.iwesley.lyn.music.core.model.NavidromeAudioQualityPreferencesStore
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.NetworkConnectionTypeProvider
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.core.model.PlaybackLoadToken
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.UnsupportedNavidromeAudioQualityPreferencesStore
import top.iwesley.lyn.music.core.model.WifiNetworkConnectionTypeProvider
import top.iwesley.lyn.music.core.model.parseNavidromeSongLocator
import top.iwesley.lyn.music.core.model.resolveNavidromeAudioQualityForCurrentNetwork

internal class ApplePlaybackGateway(
    private val platformLabel: String,
    private val navidromeAudioQualityPreferencesStore: NavidromeAudioQualityPreferencesStore =
        UnsupportedNavidromeAudioQualityPreferencesStore,
    private val networkConnectionTypeProvider: NetworkConnectionTypeProvider = WifiNetworkConnectionTypeProvider,
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
                    canSeek = false,
                    completionCount = it.completionCount + 1,
                    errorMessage = null,
                )
            }
        }
        player.onFailed = { errorMessage ->
            publishState(errorOverride = errorMessage ?: "$platformLabel 播放失败。")
        }
    }

    override suspend fun load(
        track: Track,
        playWhenReady: Boolean,
        startPositionMs: Long,
        loadToken: PlaybackLoadToken,
    ) {
        if (!loadToken.isCurrent()) {
            return
        }
        stopAndResetForTrackSwitch()
        val navidrome = parseNavidromeSongLocator(track.mediaLocator)
        val navidromeAudioQuality = navidrome?.let {
            resolveNavidromeAudioQualityForCurrentNetwork(
                preferencesStore = navidromeAudioQualityPreferencesStore,
                networkConnectionTypeProvider = networkConnectionTypeProvider,
            )
        }
        val effectiveLocator = if (navidrome != null) {
            NavidromeLocatorRuntime.resolveStreamUrl(
                locator = track.mediaLocator,
                audioQuality = requireNotNull(navidromeAudioQuality),
            ) ?: track.mediaLocator
        } else {
            track.mediaLocator
        }
        if (!loadToken.isCurrent()) {
            return
        }
        when (val resolved = AppleMediaLocatorResolver.resolve(effectiveLocator)) {
            is AppleResolvedMediaLocator.Unsupported -> {
                if (!loadToken.isCurrent()) {
                    return
                }
                mutableState.update {
                    it.copy(
                        canSeek = false,
                        errorMessage = resolved.message,
                    )
                }
            }

            else -> {
                if (!loadToken.isCurrent()) {
                    return
                }
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
                        positionMs = 0L,
                        durationMs = 0L,
                        canSeek = player.canSeek(),
                        currentNavidromeAudioQuality = navidromeAudioQuality,
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
        if (!player.canSeek()) {
            mutableState.update { it.copy(canSeek = false) }
            return
        }
        player.seekTo(positionMs)
        mutableState.update {
            it.copy(
                positionMs = positionMs.coerceAtLeast(0L),
                canSeek = player.canSeek(),
                errorMessage = null,
            )
        }
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

    private fun stopAndResetForTrackSwitch() {
        player.stopAndClear()
        mutableState.update {
            it.resetForTrackSwitch(volumeOverride = player.volume())
        }
    }

    private fun publishState(errorOverride: String? = null) {
        mutableState.update {
            it.copy(
                isPlaying = player.isPlaying(),
                positionMs = player.positionMs().coerceAtLeast(0L),
                durationMs = player.durationMs()?.takeIf { value -> value > 0L } ?: it.durationMs,
                canSeek = player.canSeek(),
                volume = player.volume().coerceIn(0f, 1f),
                errorMessage = errorOverride ?: player.errorMessage(),
            )
        }
    }
}
