package top.iwesley.lyn.music.platform

import java.io.Closeable
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

internal interface AndroidCastProxyResource : Closeable {
    val mimeType: String
    val length: Long?

    suspend fun open(start: Long, length: Long?): InputStream

    override fun close() = Unit
}

internal fun InputStream.skipFully(target: Long) {
    var skipped = 0L
    while (skipped < target) {
        val delta = skip(target - skipped)
        if (delta <= 0L) {
            if (read() < 0) {
                throw EOFException("Unable to skip to requested position $target")
            }
            skipped += 1L
        } else {
            skipped += delta
        }
    }
}

internal fun InputStream.copyLimitedTo(output: OutputStream, length: Long?) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var remaining = length ?: Long.MAX_VALUE
    while (remaining > 0L) {
        val readLength = minOf(buffer.size.toLong(), remaining).toInt()
        val read = read(buffer, 0, readLength)
        if (read < 0) break
        output.write(buffer, 0, read)
        remaining -= read.toLong()
    }
}
