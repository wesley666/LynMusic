package top.iwesley.lyn.music.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.OfflineDownload
import top.iwesley.lyn.music.core.model.OfflineDownloadGateway
import top.iwesley.lyn.music.core.model.OfflineDownloadProgress
import top.iwesley.lyn.music.core.model.OfflineDownloadStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.UnsupportedOfflineDownloadGateway
import top.iwesley.lyn.music.core.model.offlineDownloadSourceType
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.OfflineDownloadEntity

interface OfflineDownloadRepository {
    val downloads: Flow<Map<String, OfflineDownload>>

    suspend fun restoreIncompleteDownloads()
    suspend fun download(track: Track, quality: NavidromeAudioQuality): Result<Unit>
    suspend fun cancelDownload(trackId: String): Result<Unit>
    suspend fun deleteDownload(trackId: String): Result<Unit>
    suspend fun deleteDownloadsBySource(sourceId: String): Result<Unit>
    suspend fun deleteAllDownloads(): Result<Unit>
    suspend fun resolveOfflineMediaLocator(trackId: String): String?
}

class DefaultOfflineDownloadRepository(
    private val database: LynMusicDatabase,
    private val gateway: OfflineDownloadGateway = UnsupportedOfflineDownloadGateway,
) : OfflineDownloadRepository {
    override val downloads: Flow<Map<String, OfflineDownload>> = database.offlineDownloadDao()
        .observeAll()
        .map { rows -> rows.associate { row -> row.trackId to row.toDomain() } }

    override suspend fun restoreIncompleteDownloads() {
        gateway.cleanupPartialFiles()
        database.offlineDownloadDao().getAll()
            .filter { row -> row.status in incompleteStatuses }
            .forEach { row ->
                database.offlineDownloadDao().upsert(
                    row.copy(
                        status = OfflineDownloadStatus.Failed.name,
                        updatedAt = offlineDownloadNow(),
                        errorMessage = "上次下载未完成。",
                    ),
                )
            }
    }

    override suspend fun download(track: Track, quality: NavidromeAudioQuality): Result<Unit> {
        return runCatching {
            val sourceType = offlineDownloadSourceType(track)
            require(sourceType != null && sourceType != ImportSourceType.LOCAL_FOLDER) {
                "本地音乐不需要离线下载。"
            }
            val existing = database.offlineDownloadDao().getByTrackId(track.id)
            val existingLocal = existing?.localMediaLocator?.takeIf { gateway.exists(it) }
            database.offlineDownloadDao().upsert(
                OfflineDownloadEntity(
                    trackId = track.id,
                    sourceId = track.sourceId,
                    originalMediaLocator = track.mediaLocator,
                    localMediaLocator = existingLocal,
                    quality = quality.name,
                    status = OfflineDownloadStatus.Pending.name,
                    downloadedBytes = 0L,
                    totalBytes = null,
                    updatedAt = offlineDownloadNow(),
                    errorMessage = null,
                ),
            )
            try {
                val result = gateway.download(track, quality) { progress ->
                    updateProgress(track.id, progress)
                }
                existingLocal
                    ?.takeIf { it != result.localMediaLocator }
                    ?.let { gateway.delete(it) }
                database.offlineDownloadDao().upsert(
                    OfflineDownloadEntity(
                        trackId = track.id,
                        sourceId = track.sourceId,
                        originalMediaLocator = track.mediaLocator,
                        localMediaLocator = result.localMediaLocator,
                        quality = quality.name,
                        status = OfflineDownloadStatus.Completed.name,
                        downloadedBytes = result.sizeBytes,
                        totalBytes = result.totalBytes ?: result.sizeBytes,
                        updatedAt = offlineDownloadNow(),
                        errorMessage = null,
                    ),
                )
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                database.offlineDownloadDao().upsert(
                    OfflineDownloadEntity(
                        trackId = track.id,
                        sourceId = track.sourceId,
                        originalMediaLocator = track.mediaLocator,
                        localMediaLocator = existingLocal,
                        quality = quality.name,
                        status = OfflineDownloadStatus.Failed.name,
                        downloadedBytes = 0L,
                        totalBytes = null,
                        updatedAt = offlineDownloadNow(),
                        errorMessage = throwable.message ?: "下载失败。",
                    ),
                )
                throw throwable
            }
        }
    }

    override suspend fun cancelDownload(trackId: String): Result<Unit> {
        return runCatching {
            database.offlineDownloadDao().deleteByTrackId(trackId)
            gateway.cleanupPartialFiles()
        }
    }

    override suspend fun deleteDownload(trackId: String): Result<Unit> {
        return runCatching {
            val row = database.offlineDownloadDao().getByTrackId(trackId)
            row?.localMediaLocator?.let { gateway.delete(it) }
            database.offlineDownloadDao().deleteByTrackId(trackId)
        }
    }

    override suspend fun deleteDownloadsBySource(sourceId: String): Result<Unit> {
        return runCatching {
            val rows = database.offlineDownloadDao().getBySourceId(sourceId)
            rows.mapNotNull { it.localMediaLocator }.forEach { gateway.delete(it) }
            database.offlineDownloadDao().deleteBySourceId(sourceId)
        }
    }

    override suspend fun deleteAllDownloads(): Result<Unit> {
        return runCatching {
            gateway.clearAll().getOrThrow()
            database.offlineDownloadDao().deleteAll()
        }
    }

    override suspend fun resolveOfflineMediaLocator(trackId: String): String? {
        val row = database.offlineDownloadDao().getByTrackId(trackId) ?: return null
        val local = row.localMediaLocator?.takeIf { it.isNotBlank() } ?: return null
        if (gateway.exists(local)) return local
        database.offlineDownloadDao().upsert(
            row.copy(
                status = OfflineDownloadStatus.Failed.name,
                localMediaLocator = null,
                updatedAt = offlineDownloadNow(),
                errorMessage = "离线文件不存在。",
            ),
        )
        return null
    }

    private suspend fun updateProgress(trackId: String, progress: OfflineDownloadProgress) {
        database.offlineDownloadDao().updateProgress(
            trackId = trackId,
            status = OfflineDownloadStatus.Downloading.name,
            downloadedBytes = progress.downloadedBytes.coerceAtLeast(0L),
            totalBytes = progress.totalBytes?.takeIf { it > 0L },
            updatedAt = offlineDownloadNow(),
            errorMessage = null,
        )
    }
}

private val incompleteStatuses = setOf(
    OfflineDownloadStatus.Pending.name,
    OfflineDownloadStatus.Downloading.name,
)

internal fun OfflineDownloadEntity.toDomain(): OfflineDownload {
    return OfflineDownload(
        trackId = trackId,
        sourceId = sourceId,
        originalMediaLocator = originalMediaLocator,
        localMediaLocator = localMediaLocator,
        quality = NavidromeAudioQuality.entries.firstOrNull { it.name == quality }
            ?: NavidromeAudioQuality.Original,
        status = OfflineDownloadStatus.entries.firstOrNull { it.name == status }
            ?: OfflineDownloadStatus.Failed,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        updatedAt = updatedAt,
        errorMessage = errorMessage,
    )
}

private fun offlineDownloadNow(): Long = Clock.System.now().toEpochMilliseconds()
