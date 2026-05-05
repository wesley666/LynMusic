package top.iwesley.lyn.music.cast

import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track

interface CastProxySession {
    val uri: String
    val mimeType: String
    val durationMs: Long

    suspend fun close()
}

interface CastMediaUrlResolver {
    suspend fun resolve(
        track: Track,
        snapshot: PlaybackSnapshot,
    ): Result<CastProxySession>

    suspend fun release()
}

object UnsupportedCastMediaUrlResolver : CastMediaUrlResolver {
    override suspend fun resolve(
        track: Track,
        snapshot: PlaybackSnapshot,
    ): Result<CastProxySession> {
        return Result.failure(IllegalStateException("当前歌曲暂不支持投屏。"))
    }

    override suspend fun release() = Unit
}
