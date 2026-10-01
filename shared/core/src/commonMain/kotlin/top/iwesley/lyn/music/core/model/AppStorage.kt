package top.iwesley.lyn.music.core.model

import top.iwesley.lyn.music.resources.*

enum class AppStorageCategory {
    Artwork,
    PlaybackCache,
    OfflineDownloads,
    LyricsShareTemp,
    TagEditTemp,
}

data class AppStorageCategoryUsage(
    val category: AppStorageCategory,
    val sizeBytes: Long,
)

data class AppStorageSnapshot(
    val totalSizeBytes: Long,
    val categories: List<AppStorageCategoryUsage>,
    val paths: List<String> = emptyList(),
)

interface AppStorageGateway {
    suspend fun loadStorageSnapshot(): Result<AppStorageSnapshot>
    suspend fun clearCategory(category: AppStorageCategory): Result<Unit>
}

enum class AppDataLocationChangeMode {
    Migrate,
    Discard,
}

interface AppDataLocationPlatformService {
    val currentDataRootPath: String
    val pendingCleanupRootPath: String?

    suspend fun pickTargetDataRoot(): Result<String?>

    suspend fun scheduleChange(
        targetDataRootPath: String,
        mode: AppDataLocationChangeMode,
    ): Result<Unit>

    suspend fun retryPendingCleanup(): Result<Unit>
}

object UnsupportedAppDataLocationPlatformService : AppDataLocationPlatformService {
    override val currentDataRootPath: String = ""
    override val pendingCleanupRootPath: String? = null
    private val error = UiTextException(uiText(Res.string.data_location_platform_unsupported))

    override suspend fun pickTargetDataRoot(): Result<String?> = Result.failure(error)

    override suspend fun scheduleChange(
        targetDataRootPath: String,
        mode: AppDataLocationChangeMode,
    ): Result<Unit> = Result.failure(error)

    override suspend fun retryPendingCleanup(): Result<Unit> = Result.failure(error)
}

object UnsupportedAppStorageGateway : AppStorageGateway {
    private val error = UiTextException(uiText(Res.string.storage_management_platform_unsupported))

    override suspend fun loadStorageSnapshot(): Result<AppStorageSnapshot> = Result.failure(error)

    override suspend fun clearCategory(category: AppStorageCategory): Result<Unit> = Result.failure(error)
}
