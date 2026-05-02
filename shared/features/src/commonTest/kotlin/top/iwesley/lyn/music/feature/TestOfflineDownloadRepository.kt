package top.iwesley.lyn.music.feature

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.OfflineDownload
import top.iwesley.lyn.music.core.model.OfflineDownloadStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.data.repository.OfflineDownloadRepository

internal class TestOfflineDownloadRepository(
    initialDownloads: Map<String, OfflineDownload> = emptyMap(),
    var nextAvailableSpaceBytes: Long? = null,
    var downloadGate: CompletableDeferred<Unit>? = null,
) : OfflineDownloadRepository {
    private val mutableDownloads = MutableStateFlow(initialDownloads)
    var availableSpaceCalls = 0
    val downloadRequests = mutableListOf<Pair<String, NavidromeAudioQuality>>()
    val cancelRequests = mutableListOf<String>()
    var failingTrackIds: Set<String> = emptySet()
    val downloadGatesByTrackId: MutableMap<String, CompletableDeferred<Unit>> = mutableMapOf()

    override val downloads: Flow<Map<String, OfflineDownload>> = mutableDownloads.asStateFlow()

    fun updateDownloads(downloads: Map<String, OfflineDownload>) {
        mutableDownloads.value = downloads
    }

    override suspend fun restoreIncompleteDownloads() = Unit

    override suspend fun download(track: Track, quality: NavidromeAudioQuality): Result<Unit> {
        downloadRequests += track.id to quality
        (downloadGatesByTrackId[track.id] ?: downloadGate)?.await()
        if (track.id in failingTrackIds) {
            return Result.failure(IllegalStateException("下载失败：${track.title}"))
        }
        return Result.success(Unit)
    }

    override suspend fun cancelDownload(trackId: String): Result<Unit> {
        cancelRequests += trackId
        return Result.success(Unit)
    }
    override suspend fun deleteDownload(trackId: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteDownloadsBySource(sourceId: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteAllDownloads(): Result<Unit> = Result.success(Unit)
    override suspend fun resolveOfflineMediaLocator(trackId: String): String? = null
    override suspend fun availableSpaceBytes(): Result<Long?> {
        availableSpaceCalls += 1
        return Result.success(nextAvailableSpaceBytes)
    }
}

internal fun testOfflineDownload(
    trackId: String,
    sourceId: String,
    status: OfflineDownloadStatus = OfflineDownloadStatus.Completed,
    localMediaLocator: String? = "file:///offline/$trackId.mp3",
): OfflineDownload {
    return OfflineDownload(
        trackId = trackId,
        sourceId = sourceId,
        originalMediaLocator = "remote://$trackId",
        localMediaLocator = localMediaLocator,
        status = status,
        downloadedBytes = 1_024L,
        totalBytes = 1_024L,
    )
}
