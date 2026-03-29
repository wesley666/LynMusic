package top.iwesley.lyn.music.core.model

private val GTIMG_MISSING_DOT_EXTENSIONS = listOf("jpeg", "jpg", "png", "webp", "bmp", "gif")

fun normalizeArtworkLocator(locator: String?): String? {
    val raw = locator?.trim().orEmpty()
    if (raw.isBlank()) return locator
    val match = Regex("""^(https?)://([^/?#]+)(/[^?#]*)(\?[^#]*)?(#.*)?$""", RegexOption.IGNORE_CASE)
        .matchEntire(raw)
        ?: return locator
    val host = match.groupValues[2]
    val path = match.groupValues[3]
    if (!host.equals("y.gtimg.cn", ignoreCase = true)) return locator
    if (!path.startsWith("/music/photo_new/")) return locator
    val lastSlash = path.lastIndexOf('/')
    if (lastSlash < 0 || lastSlash == path.lastIndex) return locator
    val fileName = path.substring(lastSlash + 1)
    if ('.' in fileName) return locator
    val extension = GTIMG_MISSING_DOT_EXTENSIONS.firstOrNull { ext ->
        fileName.length > ext.length && fileName.endsWith(ext, ignoreCase = true)
    } ?: return locator
    val rewrittenPath = buildString {
        append(path.substring(0, lastSlash + 1))
        append(fileName.dropLast(extension.length))
        append('.')
        append(extension)
    }
    return buildString {
        append(match.groupValues[1])
        append("://")
        append(host)
        append(rewrittenPath)
        append(match.groupValues[4])
        append(match.groupValues[5])
    }
}
