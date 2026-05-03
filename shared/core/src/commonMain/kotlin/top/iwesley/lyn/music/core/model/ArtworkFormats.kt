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

fun isCompleteArtworkPayload(bytes: ByteArray): Boolean {
    return when {
        hasJpegSignature(bytes) -> hasJpegEndMarker(bytes)
        hasPngSignature(bytes) -> hasPngEndMarker(bytes)
        hasWebpSignature(bytes) -> hasCompleteWebpLength(bytes)
        hasGifSignature(bytes) -> bytes.lastOrNull() == GIF_TRAILER
        hasBmpSignature(bytes) -> hasCompleteBmpLength(bytes)
        else -> false
    }
}

fun isWebpArtworkPayload(bytes: ByteArray): Boolean = hasWebpSignature(bytes)

private fun detectArtworkExtensionFromBytes(bytes: ByteArray?): String? {
    if (bytes == null || bytes.isEmpty()) return null
    if (hasJpegSignature(bytes)) {
        return ".jpg"
    }
    if (hasPngSignature(bytes)) {
        return ".png"
    }
    if (hasWebpSignature(bytes)) {
        return ".webp"
    }
    if (hasGifSignature(bytes)) {
        return ".gif"
    }
    if (hasBmpSignature(bytes)) {
        return ".bmp"
    }
    return null
}

private fun hasJpegSignature(bytes: ByteArray): Boolean {
    return bytes.size >= 3 &&
        bytes[0] == 0xFF.toByte() &&
        bytes[1] == 0xD8.toByte() &&
        bytes[2] == 0xFF.toByte()
}

private fun hasJpegEndMarker(bytes: ByteArray): Boolean {
    return bytes.size >= 4 &&
        bytes[bytes.lastIndex - 1] == 0xFF.toByte() &&
        bytes[bytes.lastIndex] == 0xD9.toByte()
}

private fun hasPngSignature(bytes: ByteArray): Boolean {
    return bytes.size >= PNG_SIGNATURE.size &&
        PNG_SIGNATURE.indices.all { index -> bytes[index] == PNG_SIGNATURE[index] }
}

private fun hasPngEndMarker(bytes: ByteArray): Boolean {
    return bytes.size >= PNG_END_MARKER.size &&
        PNG_END_MARKER.indices.all { index ->
            bytes[bytes.size - PNG_END_MARKER.size + index] == PNG_END_MARKER[index]
        }
}

private fun hasWebpSignature(bytes: ByteArray): Boolean {
    return bytes.size >= 12 &&
        bytes[0] == 'R'.code.toByte() &&
        bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() &&
        bytes[3] == 'F'.code.toByte() &&
        bytes[8] == 'W'.code.toByte() &&
        bytes[9] == 'E'.code.toByte() &&
        bytes[10] == 'B'.code.toByte() &&
        bytes[11] == 'P'.code.toByte()
}

private fun hasCompleteWebpLength(bytes: ByteArray): Boolean {
    if (!hasWebpSignature(bytes)) return false
    val declaredPayloadSize = bytes.littleEndianIntAt(offset = 4) ?: return false
    return declaredPayloadSize >= 4 && bytes.size >= declaredPayloadSize + 8
}

private fun hasGifSignature(bytes: ByteArray): Boolean {
    return bytes.size >= 6 &&
        bytes[0] == 'G'.code.toByte() &&
        bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() &&
        bytes[3] == '8'.code.toByte() &&
        (bytes[4] == '7'.code.toByte() || bytes[4] == '9'.code.toByte()) &&
        bytes[5] == 'a'.code.toByte()
}

private fun hasBmpSignature(bytes: ByteArray): Boolean {
    return bytes.size >= 2 &&
        bytes[0] == 'B'.code.toByte() &&
        bytes[1] == 'M'.code.toByte()
}

private fun hasCompleteBmpLength(bytes: ByteArray): Boolean {
    if (!hasBmpSignature(bytes)) return false
    val declaredSize = bytes.littleEndianIntAt(offset = 2) ?: return false
    return declaredSize > 0 && bytes.size >= declaredSize
}

private fun ByteArray.littleEndianIntAt(offset: Int): Int? {
    if (offset < 0 || size < offset + 4) return null
    return (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)
}

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(),
    0x50,
    0x4E,
    0x47,
    0x0D,
    0x0A,
    0x1A,
    0x0A,
)

private val PNG_END_MARKER = byteArrayOf(
    0x00,
    0x00,
    0x00,
    0x00,
    0x49,
    0x45,
    0x4E,
    0x44,
    0xAE.toByte(),
    0x42,
    0x60,
    0x82.toByte(),
)

private val GIF_TRAILER = 0x3B.toByte()
