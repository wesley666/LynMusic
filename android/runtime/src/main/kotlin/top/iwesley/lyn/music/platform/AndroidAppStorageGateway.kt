package top.iwesley.lyn.music.platform

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.AppStorageCategory
import top.iwesley.lyn.music.core.model.AppStorageCategoryUsage
import top.iwesley.lyn.music.core.model.AppStorageGateway
import top.iwesley.lyn.music.core.model.AppStorageSnapshot
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.data.db.LynMusicDatabase

fun createAndroidAppStorageGateway(
    context: Context,
    database: LynMusicDatabase,
): AppStorageGateway = AndroidAppStorageGateway(context.applicationContext, database)

private class AndroidAppStorageGateway(
    private val context: Context,
    private val database: LynMusicDatabase,
) : AppStorageGateway {
    override suspend fun loadStorageSnapshot(): Result<AppStorageSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val smbSourceIds = currentSambaSourceIds()
            val categories = listOf(
                AppStorageCategoryUsage(
                    category = AppStorageCategory.Artwork,
                    sizeBytes = androidArtworkDirectories().sumOf(::directorySizeBytes),
                ),
                AppStorageCategoryUsage(
                    category = AppStorageCategory.PlaybackCache,
                    sizeBytes = androidPlaybackCacheSizeBytes(context.cacheDir, smbSourceIds),
                ),
                AppStorageCategoryUsage(
                    category = AppStorageCategory.LyricsShareTemp,
                    sizeBytes = directorySizeBytes(File(context.cacheDir, "lyrics-share")),
                ),
                AppStorageCategoryUsage(
                    category = AppStorageCategory.TagEditTemp,
                    sizeBytes = directorySizeBytes(File(context.cacheDir, "tag-edit")),
                ),
            )
            AppStorageSnapshot(
                totalSizeBytes = categories.sumOf { it.sizeBytes },
                categories = categories,
                paths = listOf(context.cacheDir.absolutePath),
            )
        }
    }

    override suspend fun clearCategory(category: AppStorageCategory): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            when (category) {
                AppStorageCategory.Artwork -> {
                    androidArtworkDirectories().forEach { directory ->
                        clearDirectory(directory)
                        directory.mkdirs()
                    }
                    Unit
                }

                AppStorageCategory.PlaybackCache -> {
                    val smbSourceIds = currentSambaSourceIds()
                    context.cacheDir.listFiles().orEmpty()
                        .filter { it.isFile && isAndroidPlaybackCacheFileName(it.name, smbSourceIds) }
                        .forEach(::deleteRecursively)
                    Unit
                }

                AppStorageCategory.LyricsShareTemp -> {
                    val directory = File(context.cacheDir, "lyrics-share")
                    clearDirectory(directory)
                    directory.mkdirs()
                    Unit
                }

                AppStorageCategory.TagEditTemp -> {
                    val directory = File(context.cacheDir, "tag-edit")
                    clearDirectory(directory)
                    directory.mkdirs()
                    Unit
                }
            }
        }
    }

    private suspend fun currentSambaSourceIds(): List<String> {
        return database.importSourceDao().getAll()
            .filter { it.type == ImportSourceType.SAMBA.name }
            .map { it.id }
    }

    private fun androidArtworkDirectories(): List<File> {
        return listOf(
            File(context.cacheDir, "artwork-cache"),
            File(context.cacheDir, "artwork"),
        )
    }
}

internal fun androidPlaybackCacheSizeBytes(
    cacheDir: File,
    sambaSourceIds: Collection<String>,
): Long {
    return cacheDir.listFiles().orEmpty()
        .filter { it.isFile && isAndroidPlaybackCacheFileName(it.name, sambaSourceIds) }
        .sumOf(File::length)
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
