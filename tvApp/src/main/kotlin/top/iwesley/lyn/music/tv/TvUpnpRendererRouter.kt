package top.iwesley.lyn.music.tv

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import top.iwesley.lyn.music.cast.upnp.android.AndroidUpnpMediaRenderer
import top.iwesley.lyn.music.cast.upnp.android.UpnpMediaRendererCallback
import top.iwesley.lyn.music.cast.upnp.android.UpnpRendererMedia
import top.iwesley.lyn.music.cast.upnp.android.UpnpRendererMediaType
import top.iwesley.lyn.music.cast.upnp.android.UpnpRendererTransportState

internal enum class TvRendererRoute {
    Music,
    Video,
}

internal enum class TvRendererPlaybackStatus {
    Idle,
    Ready,
    Buffering,
    Playing,
    Paused,
    Stopped,
    Failed,
}

internal data class TvRendererSessionState(
    val rendererRunning: Boolean = false,
    val route: TvRendererRoute? = null,
    val status: TvRendererPlaybackStatus = TvRendererPlaybackStatus.Idle,
    val title: String = "等待投屏",
    val artistName: String? = null,
    val albumTitle: String? = null,
    val artworkUri: String? = null,
    val uri: String? = null,
    val mimeType: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volumePercent: Int = 100,
    val muted: Boolean = false,
    val errorMessage: String? = null,
) {
    val hasMedia: Boolean
        get() = uri != null
}

internal interface TvRendererPlaybackSession {
    val route: TvRendererRoute

    fun setMedia(media: UpnpRendererMedia): Boolean
    fun play(): Boolean
    fun pause(): Boolean
    fun stop(): Boolean
    fun seekTo(positionMs: Long): Boolean
    fun setVolume(volumePercent: Int): Boolean
    fun setMute(muted: Boolean): Boolean
}

internal object TvUpnpRendererRouter : UpnpMediaRendererCallback {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(TvRendererSessionState())
    private val pendingCommands = ArrayDeque<TvRendererCommand>()
    private var appContext: Context? = null
    private var renderer: AndroidUpnpMediaRenderer? = null
    private var currentMedia: UpnpRendererMedia? = null
    private var currentRoute: TvRendererRoute? = null
    private var activeSession: TvRendererPlaybackSession? = null
    private var awaitingRouteLaunch = false

    val state: StateFlow<TvRendererSessionState> = mutableState.asStateFlow()

    fun start(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        runOnMain {
            if (renderer == null) {
                renderer = AndroidUpnpMediaRenderer(
                    context = applicationContext,
                    callback = this,
                )
            }
            val error = renderer?.start()
            updateState {
                it.copy(
                    rendererRunning = error == null,
                    errorMessage = error,
                    status = when {
                        error != null -> TvRendererPlaybackStatus.Failed
                        it.hasMedia -> it.status
                        else -> TvRendererPlaybackStatus.Idle
                    },
                )
            }
        }
    }

    fun release() {
        runOnMain {
            activeSession?.stop()
            activeSession = null
            pendingCommands.clear()
            awaitingRouteLaunch = false
            currentMedia = null
            currentRoute = null
            renderer?.release()
            renderer = null
            mutableState.value = TvRendererSessionState()
        }
    }

    fun registerSession(session: TvRendererPlaybackSession) {
        runOnMain {
            activeSession = session
            awaitingRouteLaunch = false
            val media = currentMedia?.takeIf { currentRoute == session.route } ?: return@runOnMain
            if (!session.setMedia(media)) {
                markFailed("投屏播放失败")
                return@runOnMain
            }
            session.setVolume(state.value.volumePercent)
            session.setMute(state.value.muted)
            drainPendingCommands(session)
        }
    }

    fun unregisterSession(session: TvRendererPlaybackSession) {
        runOnMain {
            if (activeSession !== session) return@runOnMain
            activeSession = null
            pendingCommands.clear()
            awaitingRouteLaunch = false
            updateState {
                it.copy(
                    status = if (it.hasMedia) TvRendererPlaybackStatus.Stopped else TvRendererPlaybackStatus.Idle,
                    positionMs = 0L,
                    errorMessage = null,
                )
            }
            syncRendererTransportState()
        }
    }

    fun play() {
        if (!state.value.hasMedia) return
        runOnMain { dispatchCommand(TvRendererCommand.Play) }
    }

    fun pause() {
        if (!state.value.hasMedia) return
        runOnMain { dispatchCommand(TvRendererCommand.Pause) }
    }

    fun stopPlayback() {
        runOnMain { dispatchCommand(TvRendererCommand.Stop) }
    }

    fun seekTo(positionMs: Long) {
        if (!state.value.hasMedia) return
        runOnMain { dispatchCommand(TvRendererCommand.Seek(positionMs.coerceAtLeast(0L))) }
    }

    fun updateSessionPlaybackState(
        session: TvRendererPlaybackSession,
        status: TvRendererPlaybackStatus,
        positionMs: Long,
        durationMs: Long,
        errorMessage: String? = null,
    ) {
        runOnMain {
            if (activeSession !== session) return@runOnMain
            updateState {
                it.copy(
                    status = status,
                    positionMs = positionMs.coerceAtLeast(0L),
                    durationMs = durationMs.coerceAtLeast(0L),
                    errorMessage = errorMessage,
                )
            }
            syncRendererTransportState()
        }
    }

    override fun onSetMedia(media: UpnpRendererMedia): Boolean {
        val route = media.routeOrNull() ?: return false
        val context = appContext ?: return false
        runOnMain {
            currentMedia = media
            currentRoute = route
            pendingCommands.clear()
            updateState {
                it.copy(
                    rendererRunning = true,
                    route = route,
                    status = TvRendererPlaybackStatus.Ready,
                    title = media.title,
                    artistName = media.artistName,
                    albumTitle = media.albumTitle,
                    artworkUri = media.artworkUri,
                    uri = media.uri,
                    mimeType = media.mimeType,
                    positionMs = 0L,
                    durationMs = media.durationMs,
                    errorMessage = null,
                )
            }
            renderer?.updateTransportState(UpnpRendererTransportState.Stopped, 0L, media.durationMs)
            val session = activeSession
            if (session != null && session.route == route) {
                awaitingRouteLaunch = false
                if (!session.setMedia(media)) {
                    markFailed("投屏播放失败")
                }
            } else {
                activeSession?.stop()
                awaitingRouteLaunch = true
                launchRouteActivity(context, route)
            }
        }
        return true
    }

    override fun onPlay(): Boolean {
        if (!state.value.hasMedia) return false
        runOnMain { dispatchCommand(TvRendererCommand.Play) }
        return true
    }

    override fun onPause(): Boolean {
        if (!state.value.hasMedia) return false
        runOnMain { dispatchCommand(TvRendererCommand.Pause) }
        return true
    }

    override fun onStop(): Boolean {
        runOnMain { dispatchCommand(TvRendererCommand.Stop) }
        return true
    }

    override fun onSeek(positionMs: Long): Boolean {
        if (!state.value.hasMedia) return false
        runOnMain { dispatchCommand(TvRendererCommand.Seek(positionMs.coerceAtLeast(0L))) }
        return true
    }

    override fun onSetVolume(volumePercent: Int): Boolean {
        val clamped = volumePercent.coerceIn(0, 100)
        runOnMain {
            updateState { it.copy(volumePercent = clamped, errorMessage = null) }
            renderer?.updateVolume(clamped, state.value.muted)
            dispatchCommand(TvRendererCommand.SetVolume(clamped))
        }
        return true
    }

    override fun onSetMute(muted: Boolean): Boolean {
        runOnMain {
            updateState { it.copy(muted = muted, errorMessage = null) }
            renderer?.updateVolume(state.value.volumePercent, muted)
            dispatchCommand(TvRendererCommand.SetMute(muted))
        }
        return true
    }

    private fun dispatchCommand(command: TvRendererCommand) {
        if (command == TvRendererCommand.Stop) {
            pendingCommands.clear()
            activeSession?.stop()
            updateState {
                it.copy(
                    status = if (it.hasMedia) TvRendererPlaybackStatus.Stopped else TvRendererPlaybackStatus.Idle,
                    positionMs = 0L,
                    errorMessage = null,
                )
            }
            syncRendererTransportState()
            return
        }

        val session = activeSession
        if (session != null && session.route == currentRoute) {
            if (!command.applyTo(session)) {
                markFailed("投屏控制失败")
            }
            return
        }

        val route = currentRoute ?: return
        val context = appContext ?: return
        if (awaitingRouteLaunch || command.launchesActivityWhenInactive) {
            pendingCommands.add(command)
            awaitingRouteLaunch = true
            launchRouteActivity(context, route)
        } else if (command == TvRendererCommand.Pause) {
            updateState {
                it.copy(
                    status = if (it.hasMedia) TvRendererPlaybackStatus.Paused else TvRendererPlaybackStatus.Idle,
                    errorMessage = null,
                )
            }
            syncRendererTransportState()
        }
    }

    private fun drainPendingCommands(session: TvRendererPlaybackSession) {
        while (pendingCommands.isNotEmpty()) {
            val command = pendingCommands.removeFirst()
            if (!command.applyTo(session)) {
                markFailed("投屏控制失败")
                pendingCommands.clear()
                return
            }
        }
    }

    private fun syncRendererTransportState() {
        val current = state.value
        val transportState = when (current.status) {
            TvRendererPlaybackStatus.Idle -> UpnpRendererTransportState.NoMediaPresent
            TvRendererPlaybackStatus.Ready,
            TvRendererPlaybackStatus.Paused -> UpnpRendererTransportState.PausedPlayback
            TvRendererPlaybackStatus.Buffering -> UpnpRendererTransportState.Transitioning
            TvRendererPlaybackStatus.Playing -> UpnpRendererTransportState.Playing
            TvRendererPlaybackStatus.Stopped,
            TvRendererPlaybackStatus.Failed -> UpnpRendererTransportState.Stopped
        }
        renderer?.updateTransportState(transportState, current.positionMs, current.durationMs)
    }

    private fun markFailed(message: String) {
        updateState {
            it.copy(
                status = TvRendererPlaybackStatus.Failed,
                errorMessage = message,
            )
        }
        syncRendererTransportState()
    }

    private fun launchRouteActivity(context: Context, route: TvRendererRoute) {
        val activityClass = when (route) {
            TvRendererRoute.Music -> MusicActivity::class.java
            TvRendererRoute.Video -> VideoActivity::class.java
        }
        val intent = Intent(context, activityClass)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { context.startActivity(intent) }
    }

    private fun updateState(transform: (TvRendererSessionState) -> TvRendererSessionState) {
        mutableState.update(transform)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}

private sealed interface TvRendererCommand {
    val launchesActivityWhenInactive: Boolean
        get() = false

    fun applyTo(session: TvRendererPlaybackSession): Boolean

    data object Play : TvRendererCommand {
        override val launchesActivityWhenInactive = true
        override fun applyTo(session: TvRendererPlaybackSession) = session.play()
    }

    data object Pause : TvRendererCommand {
        override fun applyTo(session: TvRendererPlaybackSession) = session.pause()
    }

    data object Stop : TvRendererCommand {
        override fun applyTo(session: TvRendererPlaybackSession) = session.stop()
    }

    data class Seek(val positionMs: Long) : TvRendererCommand {
        override val launchesActivityWhenInactive = true
        override fun applyTo(session: TvRendererPlaybackSession) = session.seekTo(positionMs)
    }

    data class SetVolume(val volumePercent: Int) : TvRendererCommand {
        override fun applyTo(session: TvRendererPlaybackSession) = session.setVolume(volumePercent)
    }

    data class SetMute(val muted: Boolean) : TvRendererCommand {
        override fun applyTo(session: TvRendererPlaybackSession) = session.setMute(muted)
    }
}

private fun UpnpRendererMedia.routeOrNull(): TvRendererRoute? {
    return when (mediaType) {
        UpnpRendererMediaType.Audio -> TvRendererRoute.Music
        UpnpRendererMediaType.Video -> TvRendererRoute.Video
    }
}
