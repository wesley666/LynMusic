package top.iwesley.lyn.music.platform

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.AppStorageCategory
import top.iwesley.lyn.music.core.model.AppStorageCategoryUsage
import top.iwesley.lyn.music.core.model.AppStorageGateway
import top.iwesley.lyn.music.core.model.AppStorageSnapshot
import top.iwesley.lyn.music.data.db.LynMusicDatabase

fun createJvmAppStorageGateway(
    rootDirectory: File = File(File(System.getProperty("user.home")), ".lynmusic"),
    database: LynMusicDatabase? = null,
): AppStorageGateway = JvmAppStorageGateway(rootDirectory, database)

internal class JvmAppStorageGateway(
    private val rootDirectory: File,
    private val database: LynMusicDatabase? = null,
) : AppStorageGateway {
    override suspend fun loadStorageSnapshot(): Result<AppStorageSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val categories = listOf(
                AppStorageCategoryUsage(
                    category = AppStorageCategory.Artwork,
                    sizeBytes = listOf(
                        File(rootDirectory, "artwork-cache"),
                        File(rootDirectory, "artwork"),
                    ).sumOf(::directorySizeBytes),
                ),
                AppStorageCategoryUsage(
                    category = AppStorageCategory.PlaybackCache,
                    sizeBytes = directorySizeBytes(File(rootDirectory, "cache")),
                ),
                AppStorageCategoryUsage(
                    category = AppStorageCategory.OfflineDownloads,
                    sizeBytes = directorySizeBytes(File(rootDirectory, "offline")),
                ),
            )
            AppStorageSnapshot(
                totalSizeBytes = categories.sumOf { it.sizeBytes },
                categories = categories,
                paths = listOf(rootDirectory.absolutePath),
            )
        }
    }

    override suspend fun clearCategory(category: AppStorageCategory): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            when (category) {
                AppStorageCategory.Artwork -> {
                    listOf(
                        File(rootDirectory, "artwork-cache"),
                        File(rootDirectory, "artwork"),
                    ).forEach { directory ->
                        clearDirectory(directory)
                        directory.mkdirs()
                    }
                    Unit
                }

                AppStorageCategory.PlaybackCache -> {
                    val directory = File(rootDirectory, "cache")
                    clearDirectory(directory)
                    directory.mkdirs()
                    Unit
                }

                AppStorageCategory.OfflineDownloads -> {
                    val directory = File(rootDirectory, "offline")
                    clearDirectory(directory)
                    directory.mkdirs()
                    database?.offlineDownloadDao()?.deleteAll()
                    Unit
                }

                AppStorageCategory.LyricsShareTemp,
                AppStorageCategory.TagEditTemp,
                -> Unit
            }
        }
    }
}

private fun directorySizeBytes(root: File): Long {
    if (!root.exists()) return 0L
    if (root.isFile) return root.length()
    return root.listFiles().orEmpty().sumOf(::directorySizeBytes)
}

private fun clearDirectory(root: File) {
    if (!root.exists()) return
    root.listFiles().orEmpty().forEach(::deleteRecursively)
}

private fun deleteRecursively(target: File) {
    if (target.isDirectory) {
        target.listFiles().orEmpty().forEach(::deleteRecursively)
    }
    target.delete()
}
