package top.iwesley.lyn.music.core.model

const val SAME_NAME_LRC_MAX_BYTES: Long = 1_048_576L

fun sameNameLyricsFileName(audioFileName: String): String? {
    val normalized = audioFileName.trim()
    if (normalized.isBlank()) return null
    val stem = normalized.substringBeforeLast('.', missingDelimiterValue = normalized)
        .trim()
    if (stem.isBlank()) return null
    return "$stem.lrc"
}

fun sameNameLyricsRelativePath(relativePath: String): String? {
    val normalized = relativePath.trim()
        .replace('\\', '/')
        .trim('/')
    if (normalized.isBlank()) return null
    val directory = normalized.substringBeforeLast('/', missingDelimiterValue = "")
    val fileName = normalized.substringAfterLast('/')
    val lrcFileName = sameNameLyricsFileName(fileName) ?: return null
    return listOf(directory, lrcFileName)
        .filter { it.isNotBlank() }
        .joinToString("/")
}
