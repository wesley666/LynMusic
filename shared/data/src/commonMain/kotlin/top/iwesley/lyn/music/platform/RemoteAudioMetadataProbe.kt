package top.iwesley.lyn.music.platform

data class RemoteAudioMetadata(
    val title: String? = null,
    val artistName: String? = null,
    val albumTitle: String? = null,
    val durationMs: Long? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val artworkBytes: ByteArray? = null,
    val embeddedLyrics: String? = null,
)

object RemoteAudioMetadataProbe {
    const val HEAD_PROBE_BYTES = 262_144L
    const val MAX_HEAD_PROBE_BYTES = 1_048_576L
    const val TAIL_PROBE_BYTES = 786_432L
    const val MAX_TOTAL_PROBE_BYTES = MAX_HEAD_PROBE_BYTES + TAIL_PROBE_BYTES

    fun requiredExpandedHeadBytes(relativePath: String, headBytes: ByteArray): Long? {
        if (!looksLikeMp3(relativePath, headBytes)) return null
        if (headBytes.size < ID3_HEADER_SIZE || !headBytes.startsWith(ID3_MAGIC)) return null
        val flags = headBytes[5].toInt() and 0xFF
        val tagSize = readSyncSafeInt(headBytes, 6)
        if (tagSize <= 0) return null
        val footerSize = if ((flags and ID3_FLAG_FOOTER) != 0) ID3_HEADER_SIZE else 0
        return (ID3_HEADER_SIZE + tagSize + footerSize).toLong()
    }

    fun shouldReadTail(relativePath: String): Boolean = relativePath.fileExtension() in MP4_EXTENSIONS

    fun parse(
        relativePath: String,
        headBytes: ByteArray,
        tailBytes: ByteArray? = null,
    ): RemoteAudioMetadata? {
        return when {
            looksLikeFlac(headBytes) -> parseFlac(headBytes)
            looksLikeMp3(relativePath, headBytes) -> parseId3v2(headBytes)
            looksLikeMp4(relativePath, headBytes, tailBytes) -> mergeMetadata(
                parseMp4(headBytes),
                tailBytes?.let(::parseMp4),
            )

            else -> null
        }
    }
}

fun RemoteAudioMetadata.hasMeaningfulMetadata(relativePath: String): Boolean {
    val fallbackTitle = relativePath.fallbackTitle()
    return title?.trim()?.takeIf { it.isNotBlank() && it != fallbackTitle } != null ||
        artistName?.isNotBlank() == true ||
        albumTitle?.isNotBlank() == true ||
        (durationMs ?: 0L) > 0L ||
        trackNumber != null ||
        discNumber != null ||
        artworkBytes?.isNotEmpty() == true ||
        embeddedLyrics?.isNotBlank() == true
}

private class MutableRemoteAudioMetadata {
    var title: String? = null
    var artistName: String? = null
    var albumTitle: String? = null
    var durationMs: Long? = null
    var trackNumber: Int? = null
    var discNumber: Int? = null
    var artworkBytes: ByteArray? = null
    var embeddedLyrics: String? = null

    fun mergeFrom(other: RemoteAudioMetadata?) {
        if (other == null) return
        title = title ?: other.title
        artistName = artistName ?: other.artistName
        albumTitle = albumTitle ?: other.albumTitle
        durationMs = durationMs ?: other.durationMs
        trackNumber = trackNumber ?: other.trackNumber
        discNumber = discNumber ?: other.discNumber
        artworkBytes = artworkBytes ?: other.artworkBytes
        embeddedLyrics = preferLyrics(embeddedLyrics, other.embeddedLyrics)
    }

    fun build(): RemoteAudioMetadata? {
        if (
            title == null &&
            artistName == null &&
            albumTitle == null &&
            durationMs == null &&
            trackNumber == null &&
            discNumber == null &&
            artworkBytes == null &&
            embeddedLyrics == null
        ) {
            return null
        }
        return RemoteAudioMetadata(
            title = title,
            artistName = artistName,
            albumTitle = albumTitle,
            durationMs = durationMs,
            trackNumber = trackNumber,
            discNumber = discNumber,
            artworkBytes = artworkBytes,
            embeddedLyrics = embeddedLyrics,
        )
    }
}

private fun mergeMetadata(primary: RemoteAudioMetadata?, secondary: RemoteAudioMetadata?): RemoteAudioMetadata? {
    val builder = MutableRemoteAudioMetadata()
    builder.mergeFrom(primary)
    builder.mergeFrom(secondary)
    return builder.build()
}

private fun parseId3v2(bytes: ByteArray): RemoteAudioMetadata? {
    if (bytes.size < ID3_HEADER_SIZE || !bytes.startsWith(ID3_MAGIC)) return null
    val version = bytes[3].toInt() and 0xFF
    if (version !in 2..4) return null
    val flags = bytes[5].toInt() and 0xFF
    val tagSize = readSyncSafeInt(bytes, 6)
    if (tagSize <= 0) return null
    val footerSize = if ((flags and ID3_FLAG_FOOTER) != 0) ID3_HEADER_SIZE else 0
    val tagEnd = (ID3_HEADER_SIZE + tagSize + footerSize).coerceAtMost(bytes.size)
    if (tagEnd <= ID3_HEADER_SIZE) return null

    val metadata = MutableRemoteAudioMetadata()
    var position = ID3_HEADER_SIZE
    while (position < tagEnd) {
        val headerSize = when (version) {
            2 -> 6
            else -> 10
        }
        if (position + headerSize > tagEnd) break
        if (bytes[position].toInt() == 0) break
        val frameId = when (version) {
            2 -> bytes.decodeLatin1(position, position + 3)
            else -> bytes.decodeLatin1(position, position + 4)
        }
        val frameSize = when (version) {
            2 -> readUInt24BE(bytes, position + 3)
            4 -> readSyncSafeInt(bytes, position + 4)
            else -> readInt32BE(bytes, position + 4)
        }
        if (frameId.isBlank() || frameSize <= 0) break
        val payloadStart = position + headerSize
        val payloadEnd = payloadStart + frameSize
        if (payloadEnd > tagEnd) break
        val payload = bytes.copyOfRange(payloadStart, payloadEnd)
        when (frameId) {
            "TIT2", "TT2" -> metadata.title = metadata.title ?: parseId3TextFrame(payload)
            "TPE1", "TP1" -> metadata.artistName = metadata.artistName ?: parseId3TextFrame(payload)
            "TALB", "TAL" -> metadata.albumTitle = metadata.albumTitle ?: parseId3TextFrame(payload)
            "TRCK", "TRK" -> metadata.trackNumber = metadata.trackNumber ?: parseNumericFrame(payload)
            "TPOS", "TPA" -> metadata.discNumber = metadata.discNumber ?: parseNumericFrame(payload)
            "TLEN", "TLE" -> metadata.durationMs = metadata.durationMs ?: parseDurationFrame(payload)
            "USLT", "ULT" -> metadata.embeddedLyrics = preferLyrics(metadata.embeddedLyrics, parseUnsyncedLyricsFrame(payload))
            "SYLT", "SLT" -> metadata.embeddedLyrics = preferLyrics(metadata.embeddedLyrics, parseSyncedLyricsFrame(payload))
            "APIC" -> metadata.artworkBytes = metadata.artworkBytes ?: parseAttachedPictureFrame(payload, legacy = false)
            "PIC" -> metadata.artworkBytes = metadata.artworkBytes ?: parseAttachedPictureFrame(payload, legacy = true)
        }
        position = payloadEnd
    }
    return metadata.build()
}

private fun parseId3TextFrame(payload: ByteArray): String? {
    if (payload.isEmpty()) return null
    return normalizeFieldText(decodeEncodedText(payload.copyOfRange(1, payload.size), payload[0].toInt() and 0xFF))
}

private fun parseNumericFrame(payload: ByteArray): Int? {
    return parseId3TextFrame(payload)
        ?.substringBefore('/')
        ?.substringBefore('.')
        ?.trim()
        ?.toIntOrNull()
}

private fun parseDurationFrame(payload: ByteArray): Long? {
    return parseId3TextFrame(payload)?.trim()?.toLongOrNull()?.takeIf { it > 0L }
}

private fun parseUnsyncedLyricsFrame(payload: ByteArray): String? {
    if (payload.size < 5) return null
    val encoding = payload[0].toInt() and 0xFF
    val textStart = skipEncodedString(payload, 4, encoding)
    if (textStart >= payload.size) return null
    return normalizeLyricsText(decodeEncodedText(payload.copyOfRange(textStart, payload.size), encoding))
}

private fun parseSyncedLyricsFrame(payload: ByteArray): String? {
    if (payload.size < 7) return null
    val encoding = payload[0].toInt() and 0xFF
    val timestampFormat = payload[4].toInt() and 0xFF
    var position = skipEncodedString(payload, 6, encoding)
    if (position >= payload.size) return null
    val lines = mutableListOf<String>()
    while (position < payload.size) {
        val textEnd = findEncodedTerminator(payload, position, encoding)
        val resolvedTextEnd = if (textEnd >= 0) textEnd else payload.size
        val text = normalizeLyricsText(
            decodeEncodedText(payload.copyOfRange(position, resolvedTextEnd), encoding),
        )
        position = if (textEnd >= 0) {
            resolvedTextEnd + encodedTerminatorLength(encoding)
        } else {
            resolvedTextEnd
        }
        if (position + 4 > payload.size) break
        val timestamp = readUInt32BE(payload, position).toLong()
        position += 4
        if (text.isNullOrBlank()) continue
        lines += if (timestampFormat == ID3_TIMESTAMP_MILLISECONDS) {
            buildString {
                append('[')
                append(formatLrcTimestamp(timestamp))
                append(']')
                append(text)
            }
        } else {
            text
        }
    }
    return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

private fun parseAttachedPictureFrame(payload: ByteArray, legacy: Boolean): ByteArray? {
    if (payload.isEmpty()) return null
    val encoding = payload[0].toInt() and 0xFF
    val imageStart = if (legacy) {
        if (payload.size < 6) return null
        val descriptionStart = 5
        skipEncodedString(payload, descriptionStart, encoding)
    } else {
        val mimeEnd = payload.indexOfFirstNull(1)
        if (mimeEnd < 0 || mimeEnd + 2 >= payload.size) return null
        val descriptionStart = mimeEnd + 2
        skipEncodedString(payload, descriptionStart, encoding)
    }
    if (imageStart >= payload.size) return null
    return payload.copyOfRange(imageStart, payload.size).takeIf { it.isNotEmpty() }
}

private fun parseFlac(bytes: ByteArray): RemoteAudioMetadata? {
    if (!looksLikeFlac(bytes)) return null
    val metadata = MutableRemoteAudioMetadata()
    var position = FLAC_MAGIC.size
    while (position + 4 <= bytes.size) {
        val header = bytes[position].toInt() and 0xFF
        val isLast = (header and 0x80) != 0
        val blockType = header and 0x7F
        val blockLength = readUInt24BE(bytes, position + 1)
        val dataStart = position + 4
        val dataEnd = dataStart + blockLength
        if (dataEnd > bytes.size) break
        when (blockType) {
            FLAC_BLOCK_STREAMINFO -> parseFlacStreamInfo(bytes, dataStart, blockLength)?.let { duration ->
                metadata.durationMs = metadata.durationMs ?: duration
            }

            FLAC_BLOCK_VORBIS_COMMENT -> parseFlacVorbisComment(bytes, dataStart, blockLength)?.let(metadata::mergeFrom)
            FLAC_BLOCK_PICTURE -> metadata.artworkBytes = metadata.artworkBytes ?: parseFlacPicture(bytes, dataStart, blockLength)
        }
        position = dataEnd
        if (isLast) break
    }
    return metadata.build()
}

private fun parseFlacStreamInfo(bytes: ByteArray, start: Int, length: Int): Long? {
    if (length < 18) return null
    val raw = readUInt64BE(bytes, start + 10)
    val sampleRate = ((raw shr 44) and 0xFFFFF).toInt()
    val totalSamples = raw and 0xFFFFFFFFFL
    if (sampleRate <= 0 || totalSamples <= 0L) return null
    return totalSamples * 1_000L / sampleRate.toLong()
}

private fun parseFlacVorbisComment(bytes: ByteArray, start: Int, length: Int): RemoteAudioMetadata? {
    var position = start
    val end = start + length
    if (position + 4 > end) return null
    val vendorLength = readUInt32LE(bytes, position).toInt()
    position += 4 + vendorLength
    if (position + 4 > end) return null
    val commentCount = readUInt32LE(bytes, position).toInt()
    position += 4
    val metadata = MutableRemoteAudioMetadata()
    repeat(commentCount) {
        if (position + 4 > end) return@repeat
        val itemLength = readUInt32LE(bytes, position).toInt()
        position += 4
        if (position + itemLength > end) return@repeat
        val entry = bytes.copyOfRange(position, position + itemLength).decodeUtf8().trim()
        position += itemLength
        val key = entry.substringBefore('=', "").trim().uppercase()
        val value = entry.substringAfter('=', "").trim()
        when (key) {
            "TITLE" -> metadata.title = metadata.title ?: value.takeIf { it.isNotBlank() }
            "ARTIST", "ALBUMARTIST" -> metadata.artistName = metadata.artistName ?: value.takeIf { it.isNotBlank() }
            "ALBUM" -> metadata.albumTitle = metadata.albumTitle ?: value.takeIf { it.isNotBlank() }
            "TRACKNUMBER" -> metadata.trackNumber = metadata.trackNumber ?: value.substringBefore('/').toIntOrNull()
            "DISCNUMBER" -> metadata.discNumber = metadata.discNumber ?: value.substringBefore('/').toIntOrNull()
            "LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS", "LYRIC" -> {
                metadata.embeddedLyrics = preferLyrics(metadata.embeddedLyrics, normalizeLyricsText(value))
            }
        }
    }
    return metadata.build()
}

private fun parseFlacPicture(bytes: ByteArray, start: Int, length: Int): ByteArray? {
    val end = start + length
    var position = start
    if (position + 8 > end) return null
    position += 4
    val mimeLength = readUInt32BE(bytes, position)
    position += 4 + mimeLength
    if (position + 4 > end) return null
    val descriptionLength = readUInt32BE(bytes, position)
    position += 4 + descriptionLength
    if (position + 20 > end) return null
    position += 16
    val dataLength = readUInt32BE(bytes, position)
    position += 4
    val dataEnd = position + dataLength
    if (dataEnd > end) return null
    return bytes.copyOfRange(position, dataEnd).takeIf { it.isNotEmpty() }
}

private fun parseMp4(bytes: ByteArray): RemoteAudioMetadata? {
    val moov = findMp4Atom(bytes, MP4_TYPE_MOOV) ?: return null
    val metadata = MutableRemoteAudioMetadata()
    parseMp4Container(bytes, moov.bodyStart, moov.end, metadata)
    return metadata.build()
}

private fun parseMp4Container(
    bytes: ByteArray,
    start: Int,
    end: Int,
    metadata: MutableRemoteAudioMetadata,
    metaChildren: Boolean = false,
) {
    var position = if (metaChildren) {
        (start + 4).coerceAtMost(end)
    } else {
        start
    }
    while (position + MP4_MIN_HEADER_BYTES <= end) {
        val atom = readMp4Atom(bytes, position, end) ?: break
        when (atom.type) {
            MP4_TYPE_MVHD -> metadata.durationMs = metadata.durationMs ?: parseMp4MovieHeader(bytes, atom)
            MP4_TYPE_UDTA,
            MP4_TYPE_MOOV,
            MP4_TYPE_ILST,
            MP4_TYPE_TRAK,
            MP4_TYPE_MDIA,
            MP4_TYPE_MINF,
            MP4_TYPE_STBL,
            -> parseMp4Container(bytes, atom.bodyStart, atom.end, metadata)

            MP4_TYPE_META -> parseMp4Container(bytes, atom.bodyStart, atom.end, metadata, metaChildren = true)

            MP4_TYPE_NAME -> metadata.title = metadata.title ?: parseMp4TextAtom(bytes, atom)
            MP4_TYPE_ARTIST -> metadata.artistName = metadata.artistName ?: parseMp4TextAtom(bytes, atom)
            MP4_TYPE_ALBUM -> metadata.albumTitle = metadata.albumTitle ?: parseMp4TextAtom(bytes, atom)
            MP4_TYPE_LYRICS -> metadata.embeddedLyrics = preferLyrics(metadata.embeddedLyrics, parseMp4TextAtom(bytes, atom))
            MP4_TYPE_COVER -> metadata.artworkBytes = metadata.artworkBytes ?: parseMp4CoverAtom(bytes, atom)
            MP4_TYPE_TRACK -> metadata.trackNumber = metadata.trackNumber ?: parseMp4IndexAtom(bytes, atom)
            MP4_TYPE_DISC -> metadata.discNumber = metadata.discNumber ?: parseMp4IndexAtom(bytes, atom)
        }
        position = atom.end
    }
}

private fun parseMp4MovieHeader(bytes: ByteArray, atom: Mp4Atom): Long? {
    if (atom.bodyStart + 4 > atom.end) return null
    val version = bytes[atom.bodyStart].toInt() and 0xFF
    return if (version == 1) {
        if (atom.bodyStart + 28 > atom.end) null else {
            val timeScale = readUInt32BE(bytes, atom.bodyStart + 16)
            val duration = readUInt64BE(bytes, atom.bodyStart + 20)
            durationMs(timeScale, duration)
        }
    } else {
        if (atom.bodyStart + 20 > atom.end) null else {
            val timeScale = readUInt32BE(bytes, atom.bodyStart + 12)
            val duration = readUInt32BE(bytes, atom.bodyStart + 16).toLong()
            durationMs(timeScale, duration)
        }
    }
}

private fun parseMp4TextAtom(bytes: ByteArray, atom: Mp4Atom): String? {
    val payload = parseMp4DataPayload(bytes, atom) ?: return null
    val decoded = when {
        payload.hasUtf16Bom() -> decodeMetadataBytes(payload, MetadataCharset.UTF16)
        payload.looksUtf16Be() -> decodeMetadataBytes(payload, MetadataCharset.UTF16BE)
        else -> {
            val utf8 = decodeMetadataBytes(payload, MetadataCharset.UTF8)
            if ('\uFFFD' in utf8) decodeMetadataBytes(payload, MetadataCharset.LATIN1) else utf8
        }
    }
    return normalizeFieldText(decoded)
}

private fun parseMp4CoverAtom(bytes: ByteArray, atom: Mp4Atom): ByteArray? {
    return parseMp4DataPayload(bytes, atom)?.takeIf { it.isNotEmpty() }
}

private fun parseMp4IndexAtom(bytes: ByteArray, atom: Mp4Atom): Int? {
    val payload = parseMp4DataPayload(bytes, atom) ?: return null
    if (payload.size < 4) return null
    return readUInt16BE(payload, 2).takeIf { it > 0 }
}

private fun parseMp4DataPayload(bytes: ByteArray, atom: Mp4Atom): ByteArray? {
    var position = atom.bodyStart
    while (position + MP4_MIN_HEADER_BYTES <= atom.end) {
        val child = readMp4Atom(bytes, position, atom.end) ?: break
        if (child.type == MP4_TYPE_DATA) {
            val payloadStart = (child.bodyStart + 8).coerceAtMost(child.end)
            return bytes.copyOfRange(payloadStart, child.end).takeIf { it.isNotEmpty() }
        }
        position = child.end
    }
    return null
}

private data class Mp4Atom(
    val start: Int,
    val headerSize: Int,
    val size: Int,
    val type: Int,
) {
    val bodyStart: Int get() = start + headerSize
    val end: Int get() = start + size
}

private fun findMp4Atom(bytes: ByteArray, type: Int): Mp4Atom? {
    var position = 0
    while (position + MP4_MIN_HEADER_BYTES <= bytes.size) {
        val atom = readMp4Atom(bytes, position, bytes.size)
        if (atom != null && atom.type == type) return atom
        position += 1
    }
    return null
}

private fun readMp4Atom(bytes: ByteArray, start: Int, limit: Int): Mp4Atom? {
    if (start + MP4_MIN_HEADER_BYTES > limit) return null
    val size32 = readUInt32BE(bytes, start).toLong()
    val type = readUInt32BE(bytes, start + 4)
    val headerSize: Int
    val size: Long
    if (size32 == 1L) {
        if (start + 16 > limit) return null
        headerSize = 16
        size = readUInt64BE(bytes, start + 8)
    } else {
        headerSize = 8
        size = if (size32 == 0L) {
            (limit - start).toLong()
        } else {
            size32
        }
    }
    if (size < headerSize || start + size > limit) return null
    return Mp4Atom(
        start = start,
        headerSize = headerSize,
        size = size.toInt(),
        type = type,
    )
}

private fun durationMs(timeScale: Int, duration: Long): Long? {
    if (timeScale <= 0 || duration <= 0L) return null
    return duration * 1_000L / timeScale.toLong()
}

private fun preferLyrics(existing: String?, candidate: String?): String? {
    val current = existing?.trim()?.takeIf { it.isNotBlank() }
    val next = candidate?.trim()?.takeIf { it.isNotBlank() } ?: return current
    if (current == null) return next
    val currentSynced = current.contains('[') && current.contains(']')
    val nextSynced = next.contains('[') && next.contains(']')
    return when {
        nextSynced && !currentSynced -> next
        next.length > current.length -> next
        else -> current
    }
}

private fun decodeEncodedText(bytes: ByteArray, encoding: Int): String {
    if (bytes.isEmpty()) return ""
    val charset = when (encoding) {
        0 -> MetadataCharset.LATIN1
        1 -> MetadataCharset.UTF16
        2 -> MetadataCharset.UTF16BE
        3 -> MetadataCharset.UTF8
        else -> MetadataCharset.UTF8
    }
    return decodeMetadataBytes(bytes, charset)
}

private fun normalizeFieldText(value: String?): String? {
    val normalized = value
        ?.replace('\uFEFF', ' ')
        ?.replace('\u0000', ' ')
        ?.trim()
    return normalized?.takeIf { it.isNotBlank() }
}

private fun normalizeLyricsText(value: String?): String? {
    val normalized = value
        ?.replace("\uFEFF", "")
        ?.replace('\u0000', '\n')
        ?.lineSequence()
        ?.map { it.trim() }
        ?.joinToString("\n")
        ?.trim()
    return normalized?.takeIf { it.isNotBlank() }
}

private fun skipEncodedString(bytes: ByteArray, start: Int, encoding: Int): Int {
    if (start >= bytes.size) return bytes.size
    val terminatorIndex = findEncodedTerminator(bytes, start, encoding)
    return if (terminatorIndex >= 0) {
        (terminatorIndex + encodedTerminatorLength(encoding)).coerceAtMost(bytes.size)
    } else {
        bytes.size
    }
}

private fun findEncodedTerminator(bytes: ByteArray, start: Int, encoding: Int): Int {
    return if (encodedTerminatorLength(encoding) == 1) {
        bytes.indexOfFirstNull(start)
    } else {
        var position = start
        while (position + 1 < bytes.size) {
            if (bytes[position].toInt() == 0 && bytes[position + 1].toInt() == 0) return position
            position += 2
        }
        -1
    }
}

private fun encodedTerminatorLength(encoding: Int): Int = if (encoding == 1 || encoding == 2) 2 else 1

private fun ByteArray.indexOfFirstNull(start: Int): Int {
    for (index in start until size) {
        if (this[index].toInt() == 0) return index
    }
    return -1
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (index in prefix.indices) {
        if (this[index] != prefix[index]) return false
    }
    return true
}

private fun ByteArray.hasUtf16Bom(): Boolean {
    if (size < 2) return false
    val first = this[0].toInt() and 0xFF
    val second = this[1].toInt() and 0xFF
    return (first == 0xFE && second == 0xFF) || (first == 0xFF && second == 0xFE)
}

private fun ByteArray.looksUtf16Be(): Boolean {
    if (size < 4) return false
    return (this[0].toInt() == 0 && this[1].toInt() != 0) || (this[2].toInt() == 0 && this[3].toInt() != 0)
}

private fun ByteArray.decodeLatin1(start: Int, end: Int): String {
    return decodeMetadataBytes(copyOfRange(start, end), MetadataCharset.LATIN1)
}

private fun ByteArray.decodeUtf8(): String = decodeMetadataBytes(this, MetadataCharset.UTF8)

private fun looksLikeMp3(relativePath: String, headBytes: ByteArray): Boolean {
    return relativePath.fileExtension() == "mp3" || headBytes.startsWith(ID3_MAGIC)
}

private fun looksLikeFlac(headBytes: ByteArray): Boolean = headBytes.startsWith(FLAC_MAGIC)

private fun looksLikeMp4(relativePath: String, headBytes: ByteArray, tailBytes: ByteArray?): Boolean {
    if (relativePath.fileExtension() in MP4_EXTENSIONS) return true
    if (headBytes.size >= 8 && readUInt32BE(headBytes, 4) == MP4_TYPE_FTYP) return true
    return tailBytes?.let { findMp4Atom(it, MP4_TYPE_MOOV) != null } == true
}

private fun String.fileExtension(): String = substringAfterLast('.', "").lowercase()

private fun String.fallbackTitle(): String {
    val fileName = substringAfterLast('/').ifBlank { this }
    return fileName.substringBeforeLast('.', fileName)
}

private fun formatLrcTimestamp(timestampMs: Long): String {
    val totalCentiseconds = (timestampMs.coerceAtLeast(0L) / 10L)
    val minutes = totalCentiseconds / 6_000L
    val seconds = (totalCentiseconds / 100L) % 60L
    val centiseconds = totalCentiseconds % 100L
    return buildString {
        append(minutes.toString().padStart(2, '0'))
        append(':')
        append(seconds.toString().padStart(2, '0'))
        append('.')
        append(centiseconds.toString().padStart(2, '0'))
    }
}

private fun readSyncSafeInt(bytes: ByteArray, offset: Int): Int {
    if (offset + 4 > bytes.size) return 0
    return ((bytes[offset].toInt() and 0x7F) shl 21) or
        ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
        ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
        (bytes[offset + 3].toInt() and 0x7F)
}

private fun readUInt16BE(bytes: ByteArray, offset: Int): Int {
    if (offset + 2 > bytes.size) return 0
    return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
}

private fun readUInt24BE(bytes: ByteArray, offset: Int): Int {
    if (offset + 3 > bytes.size) return 0
    return ((bytes[offset].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        (bytes[offset + 2].toInt() and 0xFF)
}

private fun readInt32BE(bytes: ByteArray, offset: Int): Int {
    if (offset + 4 > bytes.size) return 0
    return ((bytes[offset].toInt() and 0xFF) shl 24) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
        (bytes[offset + 3].toInt() and 0xFF)
}

private fun readUInt32BE(bytes: ByteArray, offset: Int): Int {
    return readInt32BE(bytes, offset)
}

private fun readUInt32LE(bytes: ByteArray, offset: Int): Long {
    if (offset + 4 > bytes.size) return 0L
    return (bytes[offset].toLong() and 0xFF) or
        ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
        ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
        ((bytes[offset + 3].toLong() and 0xFF) shl 24)
}

private fun readUInt64BE(bytes: ByteArray, offset: Int): Long {
    if (offset + 8 > bytes.size) return 0L
    var value = 0L
    repeat(8) { index ->
        value = (value shl 8) or (bytes[offset + index].toLong() and 0xFF)
    }
    return value
}

private fun fourcc(a: Int, b: Int, c: Int, d: Int): Int {
    return ((a and 0xFF) shl 24) or ((b and 0xFF) shl 16) or ((c and 0xFF) shl 8) or (d and 0xFF)
}

internal enum class MetadataCharset {
    LATIN1,
    UTF16,
    UTF16BE,
    UTF8,
}

internal fun decodeMetadataBytes(bytes: ByteArray, charset: MetadataCharset): String {
    return when (charset) {
        MetadataCharset.LATIN1 -> bytes.decodeLatin1()
        MetadataCharset.UTF16 -> bytes.decodeUtf16WithBom(defaultBigEndian = true)
        MetadataCharset.UTF16BE -> bytes.decodeUtf16BigEndian()
        MetadataCharset.UTF8 -> bytes.decodeToString()
    }
}

private fun ByteArray.decodeLatin1(): String {
    return buildString(size) {
        for (byte in this@decodeLatin1) {
            append((byte.toInt() and 0xFF).toChar())
        }
    }
}

private fun ByteArray.decodeUtf16WithBom(defaultBigEndian: Boolean): String {
    if (isEmpty()) return ""
    var start = 0
    var bigEndian = defaultBigEndian
    if (size >= 2) {
        val first = this[0].toInt() and 0xFF
        val second = this[1].toInt() and 0xFF
        when {
            first == 0xFE && second == 0xFF -> {
                start = 2
                bigEndian = true
            }

            first == 0xFF && second == 0xFE -> {
                start = 2
                bigEndian = false
            }
        }
    }
    return decodeUtf16(startIndex = start, bigEndian = bigEndian)
}

private fun ByteArray.decodeUtf16BigEndian(): String {
    return decodeUtf16(startIndex = 0, bigEndian = true)
}

private fun ByteArray.decodeUtf16(startIndex: Int, bigEndian: Boolean): String {
    if (startIndex >= size) return ""
    val safeLength = size - ((size - startIndex) % 2)
    var index = startIndex
    return buildString((safeLength - startIndex) / 2) {
        while (index + 1 < safeLength) {
            val unit = readUtf16Unit(index, bigEndian)
            index += 2
            when {
                unit in 0xD800..0xDBFF && index + 1 < safeLength -> {
                    val low = readUtf16Unit(index, bigEndian)
                    if (low in 0xDC00..0xDFFF) {
                        append(unit.toChar())
                        append(low.toChar())
                        index += 2
                    } else {
                        append(unit.toChar())
                    }
                }

                else -> append(unit.toChar())
            }
        }
    }
}

private fun ByteArray.readUtf16Unit(offset: Int, bigEndian: Boolean): Int {
    val first = this[offset].toInt() and 0xFF
    val second = this[offset + 1].toInt() and 0xFF
    return if (bigEndian) {
        (first shl 8) or second
    } else {
        (second shl 8) or first
    }
}

private val ID3_MAGIC = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte())
private val FLAC_MAGIC = byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte())
private val MP4_EXTENSIONS = setOf("m4a", "aac", "mp4", "alac")

private const val ID3_HEADER_SIZE = 10
private const val ID3_FLAG_FOOTER = 0x10
private const val ID3_TIMESTAMP_MILLISECONDS = 2

private const val FLAC_BLOCK_STREAMINFO = 0
private const val FLAC_BLOCK_VORBIS_COMMENT = 4
private const val FLAC_BLOCK_PICTURE = 6

private const val MP4_MIN_HEADER_BYTES = 8
private val MP4_TYPE_FTYP = fourcc('f'.code, 't'.code, 'y'.code, 'p'.code)
private val MP4_TYPE_MOOV = fourcc('m'.code, 'o'.code, 'o'.code, 'v'.code)
private val MP4_TYPE_MVHD = fourcc('m'.code, 'v'.code, 'h'.code, 'd'.code)
private val MP4_TYPE_UDTA = fourcc('u'.code, 'd'.code, 't'.code, 'a'.code)
private val MP4_TYPE_META = fourcc('m'.code, 'e'.code, 't'.code, 'a'.code)
private val MP4_TYPE_ILST = fourcc('i'.code, 'l'.code, 's'.code, 't'.code)
private val MP4_TYPE_TRAK = fourcc('t'.code, 'r'.code, 'a'.code, 'k'.code)
private val MP4_TYPE_MDIA = fourcc('m'.code, 'd'.code, 'i'.code, 'a'.code)
private val MP4_TYPE_MINF = fourcc('m'.code, 'i'.code, 'n'.code, 'f'.code)
private val MP4_TYPE_STBL = fourcc('s'.code, 't'.code, 'b'.code, 'l'.code)
private val MP4_TYPE_DATA = fourcc('d'.code, 'a'.code, 't'.code, 'a'.code)
private val MP4_TYPE_NAME = fourcc(0xA9, 'n'.code, 'a'.code, 'm'.code)
private val MP4_TYPE_ARTIST = fourcc(0xA9, 'A'.code, 'R'.code, 'T'.code)
private val MP4_TYPE_ALBUM = fourcc(0xA9, 'a'.code, 'l'.code, 'b'.code)
private val MP4_TYPE_LYRICS = fourcc(0xA9, 'l'.code, 'y'.code, 'r'.code)
private val MP4_TYPE_COVER = fourcc('c'.code, 'o'.code, 'v'.code, 'r'.code)
private val MP4_TYPE_TRACK = fourcc('t'.code, 'r'.code, 'k'.code, 'n'.code)
private val MP4_TYPE_DISC = fourcc('d'.code, 'i'.code, 's'.code, 'k'.code)
