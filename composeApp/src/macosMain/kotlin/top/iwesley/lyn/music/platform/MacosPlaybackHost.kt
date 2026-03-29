package top.iwesley.lyn.music.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.Track

data class MacPlaybackHostState(
    val title: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val errorMessage: String? = null,
)

class MacPlaybackHostController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gateway = ApplePlaybackGateway(platformLabel = "macOS")
    private val mutableState = MutableStateFlow(MacPlaybackHostState())

    init {
        scope.launch {
            gateway.state.collect { gatewayState ->
                mutableState.update {
                    it.copy(
                        title = gatewayState.metadataTitle?.takeIf(String::isNotBlank) ?: it.title,
                        isPlaying = gatewayState.isPlaying,
                        positionMs = gatewayState.positionMs,
                        durationMs = gatewayState.durationMs,
                        volume = gatewayState.volume,
                        errorMessage = gatewayState.errorMessage,
                    )
                }
            }
        }
    }

    fun currentState(): MacPlaybackHostState = mutableState.value

    fun openLocalFile(path: String) {
        val normalizedPath = path.trim()
        if (normalizedPath.isBlank()) {
            mutableState.update { it.copy(errorMessage = "请选择要播放的本地文件。") }
            return
        }
        val title = normalizedPath.substringAfterLast('/').substringBeforeLast('.')
        val track = Track(
            id = "mac-local:$normalizedPath",
            sourceId = "macos-local-host",
            title = title,
            mediaLocator = normalizedPath,
            relativePath = normalizedPath.substringAfterLast('/'),
        )
        mutableState.update {
            it.copy(
                title = title,
                positionMs = 0L,
                durationMs = 0L,
                errorMessage = null,
            )
        }
        scope.launch {
            gateway.load(track, playWhenReady = true, startPositionMs = 0L)
        }
    }

    fun play() {
        scope.launch { gateway.play() }
    }

    fun pause() {
        scope.launch { gateway.pause() }
    }

    fun seek(positionMs: Long) {
        scope.launch { gateway.seekTo(positionMs) }
    }

    fun setVolume(volume: Float) {
        scope.launch { gateway.setVolume(volume) }
    }

    fun dispose() {
        scope.launch { gateway.release() }
        scope.cancel()
    }
}

fun createMacPlaybackHostController(): MacPlaybackHostController = MacPlaybackHostController()
