package top.iwesley.lyn.music.platform

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.data.repository.PlaybackHydrationResult
import top.iwesley.lyn.music.data.repository.PlaybackRepository

internal object AndroidPlaybackRuntimeRegistry {
    private val mutableRepository = MutableStateFlow<PlaybackRepository?>(null)
    val repository: StateFlow<PlaybackRepository?> = mutableRepository.asStateFlow()

    fun attach(repository: PlaybackRepository) {
        Log.i(ANDROID_SERVICE_REPOSITORY_LOG_TAG, "runtime-registry-attach repository=${repository::class.simpleName}")
        mutableRepository.value = repository
    }

    fun detach(repository: PlaybackRepository) {
        if (mutableRepository.value === repository) {
            Log.i(ANDROID_SERVICE_REPOSITORY_LOG_TAG, "runtime-registry-detach repository=${repository::class.simpleName}")
            mutableRepository.value = null
        }
    }
}

internal class AndroidServiceBackedPlaybackRepository(
    context: Context,
) : PlaybackRepository {
    private val appContext = context.applicationContext
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableSnapshot = MutableStateFlow(PlaybackSnapshot(isHydratingPlayback = true))
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override val snapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot.asStateFlow()

    init {
        connectController()
        clientScope.launch {
            AndroidPlaybackRuntimeRegistry.repository.collectLatest { repository ->
                if (repository == null) {
                    mutableSnapshot.value = PlaybackSnapshot(isHydratingPlayback = true)
                } else {
                    repository.snapshot.collect { snapshot ->
                        mutableSnapshot.value = snapshot
                    }
                }
            }
        }
    }

    override suspend fun hydratePersistedQueueIfNeeded(): PlaybackHydrationResult =
        repository().hydratePersistedQueueIfNeeded()

    override suspend fun playTracks(tracks: List<Track>, startIndex: Int) {
        Log.w(
            ANDROID_SERVICE_REPOSITORY_LOG_TAG,
            "client-play-tracks size=${tracks.size} startIndex=$startIndex target=${tracks.getOrNull(startIndex)?.id.orEmpty()}",
        )
        repository().playTracks(tracks, startIndex)
    }

    override suspend fun playTransientTracks(tracks: List<Track>, startIndex: Int) {
        Log.w(
            ANDROID_SERVICE_REPOSITORY_LOG_TAG,
            "client-play-transient-tracks size=${tracks.size} startIndex=$startIndex target=${tracks.getOrNull(startIndex)?.id.orEmpty()}",
        )
        repository().playTransientTracks(tracks, startIndex)
    }

    override suspend fun prepareExternalPlaybackQueue(
        tracks: List<Track>,
        startIndex: Int,
    ): PlaybackSnapshot? {
        return repository().prepareExternalPlaybackQueue(tracks, startIndex)
    }

    override suspend fun playQueueIndex(index: Int) {
        Log.w(ANDROID_SERVICE_REPOSITORY_LOG_TAG, "client-play-queue-index index=$index")
        repository().playQueueIndex(index)
    }

    override suspend fun togglePlayPause() {
        Log.w(ANDROID_SERVICE_REPOSITORY_LOG_TAG, "client-toggle-play-pause")
        repository().togglePlayPause()
    }

    override suspend fun pause() {
        repository().pause()
    }

    override suspend fun skipNext() {
        repository().skipNext()
    }

    override suspend fun skipPrevious() {
        repository().skipPrevious()
    }

    override suspend fun seekTo(positionMs: Long) {
        repository().seekTo(positionMs)
    }

    override suspend fun setVolume(volume: Float) {
        repository().setVolume(volume)
    }

    override suspend fun cycleMode() {
        repository().cycleMode()
    }

    override suspend fun overrideCurrentTrackArtwork(artworkLocator: String?, expectedTrackId: String?) {
        repository().overrideCurrentTrackArtwork(artworkLocator, expectedTrackId)
    }

    override suspend fun close() {
        clientScope.cancel()
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
    }

    private suspend fun repository(): PlaybackRepository {
        connectController()
        AndroidPlaybackRuntimeRegistry.repository.value?.let { return it }
        Log.i(ANDROID_SERVICE_REPOSITORY_LOG_TAG, "client-await-service-repository")
        return AndroidPlaybackRuntimeRegistry.repository.filterNotNull().first()
    }

    private fun connectController() {
        if (controllerFuture != null) return
        Log.i(ANDROID_SERVICE_REPOSITORY_LOG_TAG, "client-connect-controller")
        val token = SessionToken(
            appContext,
            ComponentName(appContext, LynPlaybackMediaLibraryService::class.java),
        )
        controllerFuture = MediaController.Builder(appContext, token).buildAsync()
    }
}

private const val ANDROID_SERVICE_REPOSITORY_LOG_TAG = "LynMusic"
