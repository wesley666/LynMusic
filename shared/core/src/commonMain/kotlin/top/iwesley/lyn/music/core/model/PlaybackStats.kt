package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.CancellationException

interface PlaybackStatsReporter {
    suspend fun reportNowPlaying(track: Track, atMillis: Long)
    suspend fun submitPlay(track: Track, atMillis: Long)
}

object NoopPlaybackStatsReporter : PlaybackStatsReporter {
    override suspend fun reportNowPlaying(track: Track, atMillis: Long) = Unit

    override suspend fun submitPlay(track: Track, atMillis: Long) = Unit
}

class CompositePlaybackStatsReporter(
    private val reporters: List<PlaybackStatsReporter>,
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
) : PlaybackStatsReporter {
    override suspend fun reportNowPlaying(track: Track, atMillis: Long) {
        reporters.forEachIndexed { index, reporter ->
            runCatching {
                reporter.reportNowPlaying(track, atMillis)
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                logger.warn(PLAYBACK_STATS_LOG_TAG) {
                    "reporter-failed event=now-playing index=$index track=${track.id} " +
                        "cause=${throwable.message.orEmpty()}"
                }
            }
        }
    }

    override suspend fun submitPlay(track: Track, atMillis: Long) {
        reporters.forEachIndexed { index, reporter ->
            runCatching {
                reporter.submitPlay(track, atMillis)
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                logger.warn(PLAYBACK_STATS_LOG_TAG) {
                    "reporter-failed event=submit-play index=$index track=${track.id} " +
                        "cause=${throwable.message.orEmpty()}"
                }
            }
        }
    }
}

private const val PLAYBACK_STATS_LOG_TAG = "PlaybackStats"
