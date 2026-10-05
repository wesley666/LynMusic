package top.iwesley.lyn.music.data.repository

import kotlinx.coroutines.CancellationException
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.PlaybackStatsReporter
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.parseFnMusicSongLocator
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.domain.RemoteSourceAddressSelector
import top.iwesley.lyn.music.domain.reportFnMusicPlay
import top.iwesley.lyn.music.domain.resolveFnMusicSource

/** Reports completed plays of FN Music tracks as `track_play` events; FN Music has no now-playing call. */
class FnMusicPlaybackStatsReporter(
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val httpClient: LyricsHttpClient,
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
    private val addressSelector: RemoteSourceAddressSelector = RemoteSourceAddressSelector(),
) : PlaybackStatsReporter {
    override suspend fun reportNowPlaying(track: Track, atMillis: Long) = Unit

    override suspend fun submitPlay(track: Track, atMillis: Long) {
        val (sourceId, guid) = parseFnMusicSongLocator(track.mediaLocator) ?: return
        if (sourceId != track.sourceId) return
        val source = resolveFnMusicSource(database, secureCredentialStore, sourceId) ?: return
        runCatching {
            reportFnMusicPlay(
                httpClient = httpClient,
                source = source,
                guid = guid,
                playedAtMillis = atMillis,
                addressSelector = addressSelector,
                logger = logger,
            )
        }.onSuccess {
            logger.info(FN_MUSIC_STATS_LOG_TAG) { "report-complete source=$sourceId track=$guid" }
        }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
            logger.warn(FN_MUSIC_STATS_LOG_TAG) {
                "report-failed source=$sourceId track=$guid cause=${throwable.message.orEmpty()}"
            }
        }
    }
}

private const val FN_MUSIC_STATS_LOG_TAG = "FnMusicStats"
