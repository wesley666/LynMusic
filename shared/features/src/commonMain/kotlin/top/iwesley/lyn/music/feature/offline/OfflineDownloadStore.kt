package top.iwesley.lyn.music.feature.offline

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.uiPlural

import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.uiErrorText
import top.iwesley.lyn.music.core.model.plus

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.OfflineDownload
import top.iwesley.lyn.music.core.model.OfflineDownloadStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.OfflineDownloadRepository
import top.iwesley.lyn.music.core.model.offlineDownloadSourceType

data class OfflineDownloadState(
    val downloadsByTrackId: Map<String, OfflineDownload> = emptyMap(),
    val availableSpaceBytes: Long? = null,
    val availableSpaceLoading: Boolean = false,
    val activeBatchDownload: ActiveBatchDownloadState? = null,
    val message: UiText? = null,
)

data class ActiveBatchDownloadState(
    val trackIds: List<String>,
    val processedCount: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val estimatedTotalBytes: Long = 0L,
    val unknownCount: Int = 0,
    val approximate: Boolean = false,
) {
    val totalCount: Int
        get() = trackIds.size
}

sealed interface OfflineDownloadIntent {
    data class Download(val track: Track, val quality: NavidromeAudioQuality = NavidromeAudioQuality.Original) :
        OfflineDownloadIntent

    data class DownloadMany(
        val tracks: List<Track>,
        val quality: NavidromeAudioQuality = NavidromeAudioQuality.Original,
    ) : OfflineDownloadIntent

    data class Cancel(val trackId: String) : OfflineDownloadIntent
    data class Delete(val trackId: String) : OfflineDownloadIntent
    data class ShowMessage(val message: UiText) : OfflineDownloadIntent
    data object CancelActiveBatchDownload : OfflineDownloadIntent
    data object RefreshAvailableSpace : OfflineDownloadIntent
    data object ClearMessage : OfflineDownloadIntent
}

sealed interface OfflineDownloadEffect

class OfflineDownloadStore(
    private val repository: OfflineDownloadRepository,
    private val storeScope: CoroutineScope,
) : BaseStore<OfflineDownloadState, OfflineDownloadIntent, OfflineDownloadEffect>(
    initialState = OfflineDownloadState(),
    scope = storeScope,
) {
    private val jobsByTrackId = mutableMapOf<String, Job>()
    private var batchDownloadJob: Job? = null

    init {
        storeScope.launch {
            repository.restoreIncompleteDownloads()
            repository.downloads.collect { downloads ->
                updateState { state -> state.copy(downloadsByTrackId = downloads) }
            }
        }
    }

    override suspend fun handleIntent(intent: OfflineDownloadIntent) {
        when (intent) {
            is OfflineDownloadIntent.Download -> startDownload(intent.track, intent.quality)
            is OfflineDownloadIntent.DownloadMany -> startBatchDownload(intent.tracks, intent.quality)
            is OfflineDownloadIntent.Cancel -> cancelDownload(intent.trackId)
            is OfflineDownloadIntent.Delete -> deleteDownload(intent.trackId)
            is OfflineDownloadIntent.ShowMessage -> updateState { it.copy(message = intent.message) }
            OfflineDownloadIntent.CancelActiveBatchDownload -> cancelActiveBatchDownload()
            OfflineDownloadIntent.RefreshAvailableSpace -> refreshAvailableSpace()
            OfflineDownloadIntent.ClearMessage -> updateState { it.copy(message = null) }
        }
    }

    private suspend fun startBatchDownload(
        tracks: List<Track>,
        quality: NavidromeAudioQuality,
    ) {
        if (batchDownloadJob?.isActive == true) {
            updateState { state -> state.copy(message = uiText(Res.string.offline_batch_already_running)) }
            return
        }
        val uniqueTracks = tracks.distinctBy { it.id }
        if (uniqueTracks.isEmpty()) {
            updateState { state -> state.copy(message = uiText(Res.string.offline_track_selection_required)) }
            return
        }
        val currentDownloads = state.value.downloadsByTrackId
        val tracksToDownload = uniqueTracks.filter { track ->
            !shouldSkipBatchDownload(track, currentDownloads[track.id], quality) &&
                jobsByTrackId[track.id]?.isActive != true
        }
        val skippedCountAtStart = uniqueTracks.size - tracksToDownload.size
        if (tracksToDownload.isEmpty()) {
            updateState { state ->
                state.copy(message = batchDownloadSummaryMessage(0, 0, skippedCountAtStart))
            }
            return
        }
        val estimate = estimateBatchDownloadSize(
            tracks = tracksToDownload,
            downloadsByTrackId = currentDownloads,
            quality = quality,
        )
        if (estimate.totalBytes > 0L || estimate.unknownCount > 0) {
            val availableSpaceResult = repository.availableSpaceBytes()
            val availableSpaceBytes = availableSpaceResult.getOrNull()
            if (availableSpaceResult.isSuccess) {
                updateState { state -> state.copy(availableSpaceBytes = availableSpaceBytes) }
            }
            val insufficientSpaceMessage = batchDownloadInsufficientSpaceMessage(estimate, availableSpaceBytes)
            if (insufficientSpaceMessage != null) {
                updateState { state -> state.copy(message = insufficientSpaceMessage) }
                return
            }
        }
        batchDownloadJob = storeScope.launch {
            var successCount = 0
            var failureCount = 0
            var skippedCount = skippedCountAtStart
            var processedCount = 0
            val activeTrackIds = tracksToDownload.map { it.id }
            updateState { state ->
                state.copy(
                    activeBatchDownload = ActiveBatchDownloadState(
                        trackIds = activeTrackIds,
                        estimatedTotalBytes = estimate.totalBytes,
                        unknownCount = estimate.unknownCount,
                        approximate = estimate.approximate,
                    ),
                )
            }
            tracksToDownload.forEach { track ->
                if (!isActive) return@launch
                val deferred = async {
                    repository.download(track, quality)
                }
                jobsByTrackId[track.id] = deferred
                val result = try {
                    deferred.await()
                } catch (throwable: CancellationException) {
                    if (!isActive) throw throwable
                    null
                } catch (throwable: Throwable) {
                    Result.failure(throwable)
                } finally {
                    if (jobsByTrackId[track.id] == deferred) {
                        jobsByTrackId.remove(track.id)
                    }
                }
                when {
                    result == null -> skippedCount += 1
                    result.isSuccess -> successCount += 1
                    else -> failureCount += 1
                }
                processedCount += 1
                updateState { state ->
                    state.copy(
                        activeBatchDownload = state.activeBatchDownload?.copy(
                            processedCount = processedCount,
                            successCount = successCount,
                            failureCount = failureCount,
                        ),
                    )
                }
            }
            updateState { state ->
                state.copy(
                    activeBatchDownload = null,
                    message = batchDownloadSummaryMessage(successCount, failureCount, skippedCount),
                )
            }
            batchDownloadJob = null
        }
    }

    private fun startDownload(track: Track, quality: NavidromeAudioQuality) {
        if (jobsByTrackId[track.id]?.isActive == true) return
        jobsByTrackId[track.id] = storeScope.launch {
            repository.download(track, quality)
                .onSuccess {
                    updateState { state -> state.copy(message = uiText(Res.string.offline_download_completed, track.title)) }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    updateState { state ->
                        state.copy(message = throwable.uiErrorText(uiText(Res.string.offline_download_failed_notice)))
                    }
                }
            jobsByTrackId.remove(track.id)
        }
    }

    private suspend fun cancelDownload(trackId: String) {
        jobsByTrackId.remove(trackId)?.cancel()
        repository.cancelDownload(trackId)
        updateState { it.copy(message = uiText(Res.string.offline_download_cancelled)) }
    }

    private suspend fun cancelActiveBatchDownload() {
        val activeBatchDownload = state.value.activeBatchDownload ?: return
        val remainingTrackIds = activeBatchDownload.trackIds.drop(
            activeBatchDownload.processedCount.coerceIn(0, activeBatchDownload.totalCount),
        )
        val runningTrackIds = remainingTrackIds.filter { trackId ->
            jobsByTrackId[trackId]?.isActive == true
        }
        batchDownloadJob?.cancel()
        batchDownloadJob = null
        runningTrackIds.forEach { trackId ->
            jobsByTrackId.remove(trackId)?.cancel()
            repository.cancelDownload(trackId)
        }
        updateState {
            it.copy(
                activeBatchDownload = null,
                message = uiText(Res.string.offline_batch_cancelled),
            )
        }
    }

    private suspend fun deleteDownload(trackId: String) {
        repository.deleteDownload(trackId)
            .onSuccess {
                updateState { state -> state.copy(message = uiText(Res.string.offline_music_deleted)) }
            }
            .onFailure { throwable ->
                updateState { state -> state.copy(message = throwable.uiErrorText(uiText(Res.string.offline_music_delete_failed))) }
            }
    }

    private suspend fun refreshAvailableSpace() {
        if (state.value.availableSpaceLoading) return
        updateState { it.copy(availableSpaceLoading = true) }
        val result = repository.availableSpaceBytes()
        updateState { state ->
            state.copy(
                availableSpaceBytes = result.getOrNull(),
                availableSpaceLoading = false,
            )
        }
    }
}

internal fun shouldSkipBatchDownload(
    track: Track,
    download: OfflineDownload?,
    quality: NavidromeAudioQuality,
): Boolean {
    val sourceType = offlineDownloadSourceType(track) ?: return true
    if (sourceType == ImportSourceType.LOCAL_FOLDER) return true
    if (download?.status != OfflineDownloadStatus.Completed || !download.hasLocalFileReference) {
        return false
    }
    return !sourceType.supportsSubsonicAudioQuality() || download.quality == quality
}

private fun ImportSourceType.supportsSubsonicAudioQuality(): Boolean {
    return this == ImportSourceType.NAVIDROME || this == ImportSourceType.SUBSONIC
}

internal fun batchDownloadSummaryMessage(
    successCount: Int,
    failureCount: Int,
    skippedCount: Int,
): UiText {
    val parts = buildList {
        if (successCount > 0) add(uiPlural(Res.plurals.offline_completed_track_count, (successCount).toInt(), successCount))
        if (failureCount > 0) add(uiPlural(Res.plurals.offline_failed_track_count, (failureCount).toInt(), failureCount))
        if (skippedCount > 0) add(uiPlural(Res.plurals.offline_skipped_track_count, (skippedCount).toInt(), skippedCount))
    }
    return if (parts.isEmpty()) {
        uiText(Res.string.offline_no_tracks_required)
    } else {
        uiText(Res.string.offline_batch_completed_summary, UiText.Joined(parts, separatorText = uiText(Res.string.ui_list_separator)))
    }
}
