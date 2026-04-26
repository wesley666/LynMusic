package top.iwesley.lyn.music.platform

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import top.iwesley.lyn.music.core.model.SAME_NAME_LRC_MAX_BYTES
import top.iwesley.lyn.music.core.model.sameNameLyricsFileName

internal fun readAndroidLocalSameNameLyricsFile(audioFile: File): String? {
    val lrcFileName = sameNameLyricsFileName(audioFile.name) ?: return null
    val lyricsFile = File(audioFile.parentFile ?: return null, lrcFileName)
    if (!lyricsFile.isFile) return null
    val sizeBytes = lyricsFile.length()
    if (sizeBytes <= 0L || sizeBytes > SAME_NAME_LRC_MAX_BYTES) return null
    return decodeAndroidSameNameLyricsBytes(lyricsFile.readBytes())
}

internal fun readSameNameLyricsStream(input: InputStream): ByteArray {
    val maxBytes = (SAME_NAME_LRC_MAX_BYTES + 1L).toInt()
    val buffer = ByteArray(maxBytes)
    var totalRead = 0
    while (totalRead < maxBytes) {
        val read = input.read(buffer, totalRead, maxBytes - totalRead)
        if (read <= 0) break
        totalRead += read
    }
    return if (totalRead == buffer.size) buffer else buffer.copyOf(totalRead)
}

internal fun decodeAndroidSameNameLyricsBytes(bytes: ByteArray): String? {
    if (bytes.isEmpty() || bytes.size > SAME_NAME_LRC_MAX_BYTES) return null
    val payload = bytes.dropUtf8Bom()
    return decodeAndroidText(payload, Charsets.UTF_8)
        ?: decodeAndroidText(payload, Charset.forName("GB18030"))
        ?: decodeAndroidText(payload, Charset.forName("GBK"))
}

private fun ByteArray.dropUtf8Bom(): ByteArray {
    return if (
        size >= 3 &&
        this[0] == 0xEF.toByte() &&
        this[1] == 0xBB.toByte() &&
        this[2] == 0xBF.toByte()
    ) {
        copyOfRange(3, size)
    } else {
        this
    }
}

private fun decodeAndroidText(bytes: ByteArray, charset: Charset): String? {
    return try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
            .trim()
            .takeIf { it.isNotBlank() }
    } catch (_: CharacterCodingException) {
        null
    }
}
