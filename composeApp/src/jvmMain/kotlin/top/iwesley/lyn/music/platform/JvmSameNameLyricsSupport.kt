package top.iwesley.lyn.music.platform

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import top.iwesley.lyn.music.core.model.SAME_NAME_LRC_MAX_BYTES
import top.iwesley.lyn.music.core.model.sameNameLyricsFileName

internal fun readJvmLocalSameNameLyricsFile(audioPath: Path): String? {
    val lyricsPath = jvmSameNameLyricsPath(audioPath) ?: return null
    if (!Files.isRegularFile(lyricsPath)) return null
    val sizeBytes = Files.size(lyricsPath)
    if (sizeBytes <= 0L || sizeBytes > SAME_NAME_LRC_MAX_BYTES) return null
    return decodeJvmSameNameLyricsBytes(Files.readAllBytes(lyricsPath))
}

internal fun jvmSameNameLyricsPath(audioPath: Path): Path? {
    val lrcFileName = sameNameLyricsFileName(audioPath.fileName?.toString().orEmpty()) ?: return null
    return audioPath.parent?.resolve(lrcFileName) ?: Path.of(lrcFileName)
}

internal fun decodeJvmSameNameLyricsBytes(bytes: ByteArray): String? {
    if (bytes.isEmpty()) return null
    val payload = bytes.dropUtf8Bom()
    return decodeJvmText(payload, Charsets.UTF_8)
        ?: decodeJvmText(payload, Charset.forName("GB18030"))
        ?: decodeJvmText(payload, Charset.forName("GBK"))
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

private fun decodeJvmText(bytes: ByteArray, charset: Charset): String? {
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
