package top.iwesley.lyn.music.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.posix.closedir
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.remove
import top.iwesley.lyn.music.core.model.AppStorageCategory
import top.iwesley.lyn.music.core.model.AppStorageCategoryUsage
import top.iwesley.lyn.music.core.model.AppStorageGateway
import top.iwesley.lyn.music.core.model.AppStorageSnapshot

fun createIosAppStorageGateway(): AppStorageGateway = IosAppStorageGateway()

private class IosAppStorageGateway : AppStorageGateway {
    override suspend fun loadStorageSnapshot(): Result<AppStorageSnapshot> = withContext(Dispatchers.Default) {
        runCatching {
            val categories = listOf(
                AppStorageCategoryUsage(
                    category = AppStorageCategory.Artwork,
                    sizeBytes = topLevelDirectorySizeBytes(iosArtworkCacheDirectory()),
                ),
                AppStorageCategoryUsage(
                    category = AppStorageCategory.LyricsShareTemp,
                    sizeBytes = fileSizeBytes(NSTemporaryDirectory() + "lynmusic-lyrics-share.png"),
                ),
            )
            AppStorageSnapshot(
                totalSizeBytes = categories.sumOf { it.sizeBytes },
                categories = categories,
                paths = listOf(
                    iosArtworkCacheDirectory(),
                    NSTemporaryDirectory(),
                ).map(::normalizeStoragePath).distinct(),
            )
        }
    }

    override suspend fun clearCategory(category: AppStorageCategory): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            when (category) {
                AppStorageCategory.Artwork -> {
                    clearTopLevelDirectory(iosArtworkCacheDirectory())
                    ensureIosDirectory(iosArtworkCacheDirectory())
                    Unit
                }

                AppStorageCategory.LyricsShareTemp -> {
                    remove(NSTemporaryDirectory() + "lynmusic-lyrics-share.png")
                    Unit
                }

                AppStorageCategory.PlaybackCache,
                AppStorageCategory.OfflineDownloads,
                AppStorageCategory.TagEditTemp,
                -> Unit
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun topLevelDirectorySizeBytes(path: String): Long {
    val handle = opendir(path) ?: return 0L
    return try {
        var total = 0L
        while (true) {
            val entry = readdir(handle)?.pointed ?: break
            val name = entry.d_name.toKString()
            if (name == "." || name == "..") continue
            total += fileSizeBytes("$path/$name")
        }
        total
    } finally {
        closedir(handle)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun clearTopLevelDirectory(path: String) {
    val handle = opendir(path) ?: return
    try {
        while (true) {
            val entry = readdir(handle)?.pointed ?: break
            val name = entry.d_name.toKString()
            if (name == "." || name == "..") continue
            remove("$path/$name")
        }
    } finally {
        closedir(handle)
    }
}

private fun fileSizeBytes(path: String): Long {
    val payload = readIosLocalBytes(path) ?: return 0L
    return payload.size.toLong()
}

private fun normalizeStoragePath(path: String): String {
    val trimmed = path.trim().trimEnd('/')
    return trimmed.ifBlank { "/" }
}

@OptIn(ExperimentalForeignApi::class)
private fun ensureIosDirectory(path: String) {
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = path,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
}
