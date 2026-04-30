package top.iwesley.lyn.music.core.model

interface PlaybackStatsReporter {
    suspend fun reportNowPlaying(track: Track, atMillis: Long)
    suspend fun submitPlay(track: Track, atMillis: Long)
}

object NoopPlaybackStatsReporter : PlaybackStatsReporter {
    override suspend fun reportNowPlaying(track: Track, atMillis: Long) = Unit

    override suspend fun submitPlay(track: Track, atMillis: Long) = Unit
}
