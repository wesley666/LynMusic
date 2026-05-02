package top.iwesley.lyn.music.feature.offline

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.OfflineDownload
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.OfflineDownloadRepository

data class OfflineDownloadState(
    val downloadsByTrackId: Map<String, OfflineDownload> = emptyMap(),
    val availableSpaceBytes: Long? = null,
    val availableSpaceLoading: Boolean = false,
    val message: String? = null,
)

sealed interface OfflineDownloadIntent {
    data class Download(val track: Track, val quality: NavidromeAudioQuality = NavidromeAudioQuality.Original) :
        OfflineDownloadIntent

    data class Cancel(val trackId: String) : OfflineDownloadIntent
    data class Delete(val trackId: String) : OfflineDownloadIntent
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
            is OfflineDownloadIntent.Cancel -> cancelDownload(intent.trackId)
            is OfflineDownloadIntent.Delete -> deleteDownload(intent.trackId)
            OfflineDownloadIntent.RefreshAvailableSpace -> refreshAvailableSpace()
            OfflineDownloadIntent.ClearMessage -> updateState { it.copy(message = null) }
        }
    }

    private fun startDownload(track: Track, quality: NavidromeAudioQuality) {
        if (jobsByTrackId[track.id]?.isActive == true) return
        jobsByTrackId[track.id] = storeScope.launch {
            repository.download(track, quality)
                .onSuccess {
                    updateState { state -> state.copy(message = "离线下载完成：${track.title}") }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    updateState { state ->
                        state.copy(message = throwable.message ?: "离线下载失败。")
                    }
                }
            jobsByTrackId.remove(track.id)
        }
    }

    private suspend fun cancelDownload(trackId: String) {
        jobsByTrackId.remove(trackId)?.cancel()
        repository.cancelDownload(trackId)
        updateState { it.copy(message = "已取消离线下载。") }
    }

    private suspend fun deleteDownload(trackId: String) {
        repository.deleteDownload(trackId)
            .onSuccess {
                updateState { state -> state.copy(message = "离线音乐已删除。") }
            }
            .onFailure { throwable ->
                updateState { state -> state.copy(message = throwable.message ?: "离线音乐删除失败。") }
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
