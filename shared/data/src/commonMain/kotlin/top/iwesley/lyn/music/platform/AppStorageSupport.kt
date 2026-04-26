package top.iwesley.lyn.music.platform

fun isAndroidPlaybackCacheFileName(
    fileName: String,
    sambaSourceIds: Collection<String>,
): Boolean {
    return sambaSourceIds.any { sourceId ->
        fileName.length > sourceId.length + 1 && fileName.startsWith("$sourceId-")
    }
}
