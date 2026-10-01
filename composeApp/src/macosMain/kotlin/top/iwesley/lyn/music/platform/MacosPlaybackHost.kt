package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.resources.*

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.*

data class MacPlaybackHostState(
    val title: String = "",
    val hasLoadedFile: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val canSeek: Boolean = false,
    val volume: Float = 1f,
    val errorMessage: String? = null,
)

class MacPlaybackHostController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gateway = ApplePlaybackGateway(platformLabel = "macOS")
    private val mutableState = MutableStateFlow(MacPlaybackHostState())

    private val errorText = MutableStateFlow<UiText?>(null)

    init {
        scope.launch {
            observeResolvedUiText(errorText) { message, isCurrent ->
                mutableState.update { if (isCurrent()) it.copy(errorMessage = message) else it }
            }
        }
        scope.launch {
            gateway.state.collect { gatewayState ->
                errorText.value = gatewayState.playbackErrorText()
                mutableState.update {
                    it.copy(
                        title = gatewayState.metadataTitle?.takeIf(String::isNotBlank) ?: it.title,
                        isPlaying = gatewayState.isPlaying,
                        positionMs = gatewayState.positionMs,
                        durationMs = gatewayState.durationMs,
                        canSeek = gatewayState.canSeek,
                        volume = gatewayState.volume,
                    )
                }
            }
        }
    }

    fun currentState(): MacPlaybackHostState = mutableState.value

    fun openLocalFile(path: String) {
        val normalizedPath = path.trim()
        if (normalizedPath.isBlank()) {
            errorText.value = uiText(Res.string.mac_select_file)
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
        errorText.value = null
        mutableState.update {
            it.copy(
                title = title,
                hasLoadedFile = true,
                positionMs = 0L,
                durationMs = 0L,
                canSeek = false,
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
        if (!mutableState.value.canSeek) return
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

/** SwiftUI owns persistence, while the playback controller stays alive during UI language changes. */
fun configureMacUiLanguage(savedValue: String, systemLanguageTag: String): String {
    AppLanguageRuntime.updateSystemLanguage(systemLanguageTag)
    AppLanguageRuntime.update(appLanguageOrDefault(savedValue))
    return AppLanguageRuntime.effectiveLanguage.value.storageValue
}
