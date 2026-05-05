package top.iwesley.lyn.music.cast

import top.iwesley.lyn.music.core.model.PlaybackSnapshot

data class CastSessionForegroundCallbacks(
    val togglePlayPause: suspend () -> Unit = {},
    val skipNext: suspend () -> Unit = {},
    val skipPrevious: suspend () -> Unit = {},
    val stopCast: suspend () -> Unit = {},
)

data class CastSessionForegroundState(
    val snapshot: PlaybackSnapshot = PlaybackSnapshot(),
    val castState: CastSessionState = CastSessionState(),
)

interface CastSessionForegroundPlatformService {
    fun bind(callbacks: CastSessionForegroundCallbacks)
    suspend fun start()
    suspend fun update(state: CastSessionForegroundState)
    suspend fun stop()
    suspend fun close()
}

object UnsupportedCastSessionForegroundPlatformService : CastSessionForegroundPlatformService {
    override fun bind(callbacks: CastSessionForegroundCallbacks) = Unit
    override suspend fun start() = Unit
    override suspend fun update(state: CastSessionForegroundState) = Unit
    override suspend fun stop() = Unit
    override suspend fun close() = Unit
}
