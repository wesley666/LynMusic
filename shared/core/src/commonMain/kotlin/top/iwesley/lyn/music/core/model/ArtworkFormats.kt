package top.iwesley.lyn.music.core.model

private val KNOWN_ARTWORK_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

fun inferArtworkFileExtension(
    locator: String? = null,
    bytes: ByteArray? = null,
): String {
    detectArtworkExtensionFromBytes(bytes)?.let { return it }
    val extension = locator
        ?.substringBefore('#')
        ?.substringBefore('?')
        ?.substringAfterLast('/', "")
        ?.substringAfterLast('.', "")
        ?.lowercase()
        .orEmpty()
    return if (extension in KNOWN_ARTWORK_EXTENSIONS) ".$extension" else ".jpg"
}

private fun detectArtworkExtensionFromBytes(bytes: ByteArray?): String? {
    if (bytes == null || bytes.isEmpty()) return null
    if (
        bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() &&
        bytes[1] == 0xD8.toByte() &&
        bytes[2] == 0xFF.toByte()
    ) {
        return ".jpg"
    }
    if (
        bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() &&
        bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() &&
        bytes[3] == 0x47.toByte() &&
        bytes[4] == 0x0D.toByte() &&
        bytes[5] == 0x0A.toByte() &&
        bytes[6] == 0x1A.toByte() &&
        bytes[7] == 0x0A.toByte()
    ) {
        return ".png"
    }
    if (
        bytes.size >= 12 &&
        bytes[0] == 'R'.code.toByte() &&
        bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() &&
        bytes[3] == 'F'.code.toByte() &&
        bytes[8] == 'W'.code.toByte() &&
        bytes[9] == 'E'.code.toByte() &&
        bytes[10] == 'B'.code.toByte() &&
        bytes[11] == 'P'.code.toByte()
    ) {
        return ".webp"
    }
    if (
        bytes.size >= 6 &&
        bytes[0] == 'G'.code.toByte() &&
        bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() &&
        bytes[3] == '8'.code.toByte() &&
        (bytes[4] == '7'.code.toByte() || bytes[4] == '9'.code.toByte()) &&
        bytes[5] == 'a'.code.toByte()
    ) {
        return ".gif"
    }
    if (
        bytes.size >= 2 &&
        bytes[0] == 'B'.code.toByte() &&
        bytes[1] == 'M'.code.toByte()
    ) {
        return ".bmp"
    }
    return null
}
