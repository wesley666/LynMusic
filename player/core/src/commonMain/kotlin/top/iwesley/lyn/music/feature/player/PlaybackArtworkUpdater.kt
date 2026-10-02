package top.iwesley.lyn.music.feature.player

import top.iwesley.lyn.music.core.model.ArtworkWritePolicy

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.parseSubsonicCompatibleSongLocator
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.data.repository.PlaybackRepository

/** Artwork is optional and must never be a child of the lyrics-loading job. */
internal class PlaybackArtworkUpdater(
    private val scope: CoroutineScope,
    private val cache: ArtworkCacheStore,
    private val playback: PlaybackRepository,
    private val logger: DiagnosticLogger,
) {
    private val active = MutableStateFlow<Job?>(null)
    private val invalidation = MutableStateFlow(0L)
    val generation: Long get() = invalidation.value

    fun cancel() {
        invalidation.getAndUpdate { it + 1 }
        active.getAndUpdate { null }?.cancel()
    }

    fun submit(track: Track, locator: String?, requestId: Long, isCurrent: () -> Boolean) {
        if (locator.isNullOrBlank() || !isCurrent()) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (!isCurrent()) return@launch
                val key = trackArtworkCacheKey(track) ?: locator
                val policy = if (parseSubsonicCompatibleSongLocator(track.mediaLocator) != null) {
                    ArtworkWritePolicy.MissingOrPlaceholder
                } else {
                    ArtworkWritePolicy.KeepExisting
                }
                val cached = cache.cache(locator, key, policy) ?: return@launch
                currentCoroutineContext().ensureActive()
                if (!isCurrent()) return@launch
                // The repository checks the track again under its playback lock.
                if (cached.changed || playback.snapshot.value.currentDisplayArtworkLocator.isNullOrBlank()) {
                    // Keep the source address for casting; local consumers resolve the cached file by key.
                    playback.overrideCurrentTrackArtwork(locator, expectedTrackId = track.id)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logger.warn(PLAYER_LOG_TAG) {
                    "playback-artwork-update-failed track=${track.id} request=$requestId reason=${error.message.orEmpty()}"
                }
            }
        }
        active.getAndUpdate { job }?.cancel()
        job.start()
    }
}
