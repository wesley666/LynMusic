package top.iwesley.lyn.music.core.model

enum class OfflineDownloadStatus {
    Pending,
    Downloading,
    Completed,
    Failed,
}

data class OfflineDownload(
    val trackId: String,
    val sourceId: String,
    val originalMediaLocator: String,
    val localMediaLocator: String? = null,
    val quality: NavidromeAudioQuality = NavidromeAudioQuality.Original,
    val status: OfflineDownloadStatus = OfflineDownloadStatus.Pending,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val updatedAt: Long = 0L,
    val errorMessage: String? = null,
) {
    val hasLocalFileReference: Boolean
        get() = !localMediaLocator.isNullOrBlank()
}

data class OfflineDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long? = null,
)

data class OfflineDownloadResult(
    val localMediaLocator: String,
    val sizeBytes: Long,
    val totalBytes: Long? = null,
)

interface OfflineDownloadGateway {
    suspend fun download(
        track: Track,
        quality: NavidromeAudioQuality,
        onProgress: suspend (OfflineDownloadProgress) -> Unit,
    ): OfflineDownloadResult

    suspend fun delete(localMediaLocator: String): Result<Unit>
    suspend fun exists(localMediaLocator: String): Boolean
    suspend fun clearAll(): Result<Unit>
    suspend fun sizeBytes(): Long
    suspend fun availableSpaceBytes(): Long?
    suspend fun cleanupPartialFiles(): Result<Unit>
}

object UnsupportedOfflineDownloadGateway : OfflineDownloadGateway {
    private val error = IllegalStateException("当前平台暂不支持离线下载。")

    override suspend fun download(
        track: Track,
        quality: NavidromeAudioQuality,
        onProgress: suspend (OfflineDownloadProgress) -> Unit,
    ): OfflineDownloadResult {
        throw error
    }

    override suspend fun delete(localMediaLocator: String): Result<Unit> = Result.failure(error)
    override suspend fun exists(localMediaLocator: String): Boolean = false
    override suspend fun clearAll(): Result<Unit> = Result.failure(error)
    override suspend fun sizeBytes(): Long = 0L
    override suspend fun availableSpaceBytes(): Long? = null
    override suspend fun cleanupPartialFiles(): Result<Unit> = Result.failure(error)
}

fun offlineDownloadSourceType(track: Track): ImportSourceType? {
    return when {
        parseSambaLocator(track.mediaLocator) != null -> ImportSourceType.SAMBA
        parseWebDavLocator(track.mediaLocator) != null -> ImportSourceType.WEBDAV
        parseNavidromeSongLocator(track.mediaLocator) != null -> ImportSourceType.NAVIDROME
        else -> null
    }
}

fun supportsOfflineDownload(track: Track): Boolean {
    return offlineDownloadSourceType(track) != null
}
