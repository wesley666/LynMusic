package top.iwesley.lyn.music.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteAudioMetadataProbeTest {

    @Test
    fun `mp3 parser expands id3 head and extracts artwork with unsynced lyrics`() {
        val imageBytes = byteArrayOf(1, 2, 3, 4, 5)
        val mp3Bytes = buildId3v23Tag(
            textFrame("TIT2", "MP3 Title"),
            textFrame("TPE1", "MP3 Artist"),
            textFrame("TALB", "MP3 Album"),
            textFrame("TRCK", "7/12"),
            textFrame("TPOS", "2/3"),
            textFrame("TLEN", "123456"),
            usltFrame("first line\nsecond line"),
            apicFrame(imageBytes),
        )

        val requiredBytes = RemoteAudioMetadataProbe.requiredExpandedHeadBytes(
            relativePath = "Artist/Track.mp3",
            headBytes = mp3Bytes.copyOfRange(0, 32),
        )
        val metadata = RemoteAudioMetadataProbe.parse(
            relativePath = "Artist/Track.mp3",
            headBytes = mp3Bytes,
        )

        assertEquals(mp3Bytes.size.toLong(), requiredBytes)
        assertNotNull(metadata)
        assertEquals("MP3 Title", metadata.title)
        assertEquals("MP3 Artist", metadata.artistName)
        assertEquals("MP3 Album", metadata.albumTitle)
        assertEquals(123456L, metadata.durationMs)
        assertEquals(7, metadata.trackNumber)
        assertEquals(2, metadata.discNumber)
        assertEquals("first line\nsecond line", metadata.embeddedLyrics)
        assertTrue(metadata.artworkBytes!!.contentEquals(imageBytes))
        assertTrue(metadata.hasMeaningfulMetadata("Artist/Track.mp3"))
    }

    @Test
    fun `mp3 parser converts synced lyrics into lrc`() {
        val metadata = RemoteAudioMetadataProbe.parse(
            relativePath = "Artist/Synced.mp3",
            headBytes = buildId3v23Tag(
                textFrame("TIT2", "Synced"),
                syltFrame(
                    "First line" to 1_000,
                    "Second line" to 2_500,
                ),
            ),
        )

        assertNotNull(metadata)
        assertEquals("[00:01.00]First line\n[00:02.50]Second line", metadata.embeddedLyrics)
    }

    @Test
    fun `mp3 parser skips version specific extended headers including crc`() {
        for (version in listOf(3, 4)) {
            for (withCrc in listOf(false, true)) {
                val extendedHeader = if (version == 3) {
                    be32(if (withCrc) 10 else 6) +
                        byteArrayOf((if (withCrc) 0x80 else 0).toByte(), 0) +
                        be32(0) +
                        (if (withCrc) be32(0) else ByteArray(0))
                } else {
                    syncSafe(if (withCrc) 12 else 6) +
                        byteArrayOf(1, (if (withCrc) 0x20 else 0).toByte()) +
                        (if (withCrc) byteArrayOf(5, 0, 0, 0, 0, 0) else ByteArray(0))
                }
                val frames = textFrame("TIT2", "Extended Title", version) +
                    textFrame("TPE1", "Extended Artist", version) +
                    textFrame("TALB", "Extended Album", version)
                val bytes = buildId3Tag(version, extendedHeader + frames, flags = 0x40)
                val metadata = assertNotNull(RemoteAudioMetadataProbe.parse("Track.mp3", bytes))

                assertEquals("Extended Title", metadata.title)
                assertEquals("Extended Artist", metadata.artistName)
                assertEquals("Extended Album", metadata.albumTitle)
                assertEquals(bytes.size.toLong(), RemoteAudioMetadataProbe.requiredExpandedHeadBytes("Track.mp3", bytes))
            }
        }
    }

    @Test
    fun `mp3 parser skips version four extended header with all supported flags`() {
        val extendedHeader = syncSafe(15) + byteArrayOf(1, 0x70, 0, 5, 0, 0, 0, 0, 0, 1, 0)
        val frames = textFrame("TIT2", "Updated Title", version = 4)
        val metadata = assertNotNull(
            RemoteAudioMetadataProbe.parse("Track.mp3", buildId3Tag(4, extendedHeader + frames, flags = 0x40)),
        )
        assertEquals("Updated Title", metadata.title)
    }

    @Test
    fun `mp3 parser rejects invalid and truncated extended headers`() {
        val invalidHeaders = listOf(
            3 to byteArrayOf(0, 0, 0),
            3 to (be32(5) + ByteArray(5)),
            3 to (be32(-1) + ByteArray(6)),
            3 to (be32(Int.MAX_VALUE) + ByteArray(6)),
            3 to (be32(6) + byteArrayOf(0x80.toByte(), 0) + be32(0)),
            4 to (syncSafe(5) + byteArrayOf(1, 0)),
            4 to (byteArrayOf(0x80.toByte(), 0, 0, 6, 1, 0)),
            4 to (syncSafe(6) + byteArrayOf(0, 0)),
            4 to (syncSafe(6) + byteArrayOf(1, 0x80.toByte())),
            4 to (syncSafe(6) + byteArrayOf(1, 0x20)),
            4 to (syncSafe(12) + byteArrayOf(1, 0x20, 4, 0, 0, 0, 0, 0)),
            4 to (syncSafe(12) + byteArrayOf(1, 0x20, 5, 0x80.toByte(), 0, 0, 0, 0)),
        )
        invalidHeaders.forEach { (version, header) ->
            assertNull(RemoteAudioMetadataProbe.parse("Track.mp3", buildId3Tag(version, header, flags = 0x40)))
        }
        for (version in listOf(3, 4)) {
            val header = if (version == 3) be32(6) + ByteArray(6) else syncSafe(6) + byteArrayOf(1, 0)
            val bytes = buildId3Tag(version, header + textFrame("TIT2", "Out of bounds", version), flags = 0x40)
            assertNull(RemoteAudioMetadataProbe.parse("Track.mp3", bytes.copyOf(10 + header.size - 1)))
            // Extra bytes in the file must not make a header fit past the declared tag boundary.
            syncSafe(header.size - 1).copyInto(bytes, destinationOffset = 6)
            assertNull(RemoteAudioMetadataProbe.parse("Track.mp3", bytes))
        }
    }

    @Test
    fun `mp3 parser preserves tags without extended headers for versions two and four`() {
        val payload = byteArrayOf(0) + ascii("Legacy Title")
        val legacyFrame = ascii("TT2") + byteArrayOf(0, 0, payload.size.toByte()) + payload
        val legacy = assertNotNull(RemoteAudioMetadataProbe.parse("Track.mp3", buildId3Tag(2, legacyFrame)))
        val modern = assertNotNull(
            RemoteAudioMetadataProbe.parse("Track.mp3", buildId3Tag(4, textFrame("TIT2", "Modern Title", 4))),
        )
        val frames = textFrame("TIT2", "Footer Title", version = 4)
        val footer = ascii("3DI") + byteArrayOf(4, 0, 0x10) + syncSafe(frames.size)
        val withFooter = assertNotNull(
            RemoteAudioMetadataProbe.parse("Track.mp3", buildId3Tag(4, frames, flags = 0x10) + footer),
        )
        assertEquals("Legacy Title", legacy.title)
        assertEquals("Modern Title", modern.title)
        assertEquals("Footer Title", withFooter.title)
    }

    @Test
    fun `flac parser extracts vorbis fields picture and lyrics`() {
        val pictureBytes = byteArrayOf(9, 8, 7, 6)
        val metadata = RemoteAudioMetadataProbe.parse(
            relativePath = "Artist/Album.flac",
            headBytes = buildFlacFile(
                sampleRate = 48_000,
                totalSamples = 480_000L,
                comments = listOf(
                    "TITLE=FLAC Title",
                    "ARTIST=FLAC Artist",
                    "ALBUM=FLAC Album",
                    "TRACKNUMBER=5/12",
                    "DISCNUMBER=1/2",
                    "LYRICS=flac line one\nflac line two",
                ),
                pictureBytes = pictureBytes,
            ),
        )

        assertNotNull(metadata)
        assertEquals("FLAC Title", metadata.title)
        assertEquals("FLAC Artist", metadata.artistName)
        assertEquals("FLAC Album", metadata.albumTitle)
        assertEquals(10_000L, metadata.durationMs)
        assertEquals(5, metadata.trackNumber)
        assertEquals(1, metadata.discNumber)
        assertEquals("flac line one\nflac line two", metadata.embeddedLyrics)
        assertTrue(metadata.artworkBytes!!.contentEquals(pictureBytes))
    }

    @Test
    fun `mp4 parser extracts metadata from tail moov atom`() {
        val coverBytes = byteArrayOf(5, 4, 3, 2)
        val headBytes = atom("ftyp", ascii("M4A "))
        val tailBytes = buildMp4Moov(
            title = "M4A Title",
            artist = "M4A Artist",
            album = "M4A Album",
            lyrics = "m4a lyric line",
            trackNumber = 3,
            discNumber = 2,
            durationMs = 205_000L,
            coverBytes = coverBytes,
        )

        val headOnly = RemoteAudioMetadataProbe.parse(
            relativePath = "Artist/TailOnly.m4a",
            headBytes = headBytes,
        )
        val metadata = RemoteAudioMetadataProbe.parse(
            relativePath = "Artist/TailOnly.m4a",
            headBytes = headBytes,
            tailBytes = tailBytes,
        )

        assertNull(headOnly)
        assertNotNull(metadata)
        assertEquals("M4A Title", metadata.title)
        assertEquals("M4A Artist", metadata.artistName)
        assertEquals("M4A Album", metadata.albumTitle)
        assertEquals("m4a lyric line", metadata.embeddedLyrics)
        assertEquals(3, metadata.trackNumber)
        assertEquals(2, metadata.discNumber)
        assertEquals(205_000L, metadata.durationMs)
        assertTrue(metadata.artworkBytes!!.contentEquals(coverBytes))
    }
}

private fun buildId3v23Tag(vararg frames: ByteArray): ByteArray {
    val payload = frames.fold(ByteArray(0)) { acc, frame -> acc + frame }
    return buildId3Tag(version = 3, payload = payload)
}

private fun buildId3Tag(version: Int, payload: ByteArray, flags: Int = 0): ByteArray {
    return byteArrayOf(
        'I'.code.toByte(),
        'D'.code.toByte(),
        '3'.code.toByte(),
        version.toByte(),
        0,
        flags.toByte(),
    ) + syncSafe(payload.size) + payload
}

private fun textFrame(id: String, value: String, version: Int = 3): ByteArray {
    val payload = byteArrayOf(3) + value.encodeToByteArray()
    val size = if (version == 4) syncSafe(payload.size) else be32(payload.size)
    return id.encodeToByteArray() + size + byteArrayOf(0, 0) + payload
}

private fun usltFrame(lyrics: String): ByteArray {
    val payload = byteArrayOf(3) + ascii("eng") + byteArrayOf(0) + lyrics.encodeToByteArray()
    return ascii("USLT") + be32(payload.size) + byteArrayOf(0, 0) + payload
}

private fun syltFrame(vararg lines: Pair<String, Int>): ByteArray {
    var payload = byteArrayOf(3) + ascii("eng") + byteArrayOf(2, 1, 0)
    lines.forEach { (text, timestampMs) ->
        payload += text.encodeToByteArray() + byteArrayOf(0) + be32(timestampMs)
    }
    return ascii("SYLT") + be32(payload.size) + byteArrayOf(0, 0) + payload
}

private fun apicFrame(imageBytes: ByteArray): ByteArray {
    val payload = byteArrayOf(3) +
        ascii("image/jpeg") +
        byteArrayOf(0, 3, 0) +
        imageBytes
    return ascii("APIC") + be32(payload.size) + byteArrayOf(0, 0) + payload
}

private fun buildFlacFile(
    sampleRate: Int,
    totalSamples: Long,
    comments: List<String>,
    pictureBytes: ByteArray,
): ByteArray {
    val streamInfo = ByteArray(34).also { bytes ->
        val encoded = (sampleRate.toLong() shl 44) or totalSamples
        repeat(8) { index ->
            bytes[10 + index] = ((encoded shr (56 - index * 8)) and 0xFF).toByte()
        }
    }
    val vendor = ascii("codex")
    var commentBody = le32(vendor.size) + vendor + le32(comments.size)
    comments.forEach { comment ->
        val encoded = comment.encodeToByteArray()
        commentBody += le32(encoded.size) + encoded
    }
    val pictureBody = be32(3) +
        be32("image/png".length) +
        ascii("image/png") +
        be32(0) +
        be32(0) +
        be32(0) +
        be32(0) +
        be32(0) +
        be32(pictureBytes.size) +
        pictureBytes
    return ascii("fLaC") +
        flacBlock(last = false, type = 0, payload = streamInfo) +
        flacBlock(last = false, type = 4, payload = commentBody) +
        flacBlock(last = true, type = 6, payload = pictureBody)
}

private fun buildMp4Moov(
    title: String,
    artist: String,
    album: String,
    lyrics: String,
    trackNumber: Int,
    discNumber: Int,
    durationMs: Long,
    coverBytes: ByteArray,
): ByteArray {
    val mvhdBody = ByteArray(20).also { bytes ->
        val timeScale = 1_000
        val duration = durationMs.toInt()
        be32(timeScale).copyInto(bytes, destinationOffset = 12)
        be32(duration).copyInto(bytes, destinationOffset = 16)
    }
    val ilstBody = listOf(
        textEntry(byteArrayOf(0xA9.toByte(), 'n'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte()), title),
        textEntry(byteArrayOf(0xA9.toByte(), 'A'.code.toByte(), 'R'.code.toByte(), 'T'.code.toByte()), artist),
        textEntry(byteArrayOf(0xA9.toByte(), 'a'.code.toByte(), 'l'.code.toByte(), 'b'.code.toByte()), album),
        textEntry(byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte()), lyrics),
        binaryEntry("covr", coverBytes),
        binaryEntry("trkn", byteArrayOf(0, 0, 0, trackNumber.toByte(), 0, 0, 0, 0)),
        binaryEntry("disk", byteArrayOf(0, 0, 0, discNumber.toByte(), 0, 0, 0, 0)),
    ).fold(ByteArray(0)) { acc, entry -> acc + entry }
    val metaBody = byteArrayOf(0, 0, 0, 0) + atom("ilst", ilstBody)
    val udtaBody = atom("meta", metaBody)
    return atom("moov", atom("mvhd", mvhdBody) + atom("udta", udtaBody))
}

private fun textEntry(type: ByteArray, value: String): ByteArray {
    val dataBody = ByteArray(8) + value.encodeToByteArray()
    return atom(type, atom("data", dataBody))
}

private fun binaryEntry(type: String, value: ByteArray): ByteArray {
    val dataBody = ByteArray(8) + value
    return atom(type, atom("data", dataBody))
}

private fun atom(type: String, body: ByteArray): ByteArray = atom(ascii(type), body)

private fun atom(type: ByteArray, body: ByteArray): ByteArray {
    return be32(body.size + 8) + type + body
}

private fun flacBlock(last: Boolean, type: Int, payload: ByteArray): ByteArray {
    val header = byteArrayOf(
        (((if (last) 0x80 else 0x00) or type) and 0xFF).toByte(),
        ((payload.size shr 16) and 0xFF).toByte(),
        ((payload.size shr 8) and 0xFF).toByte(),
        (payload.size and 0xFF).toByte(),
    )
    return header + payload
}

private fun ascii(value: String): ByteArray = value.encodeToByteArray()

private fun be32(value: Int): ByteArray = byteArrayOf(
    ((value ushr 24) and 0xFF).toByte(),
    ((value ushr 16) and 0xFF).toByte(),
    ((value ushr 8) and 0xFF).toByte(),
    (value and 0xFF).toByte(),
)

private fun le32(value: Int): ByteArray = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value ushr 8) and 0xFF).toByte(),
    ((value ushr 16) and 0xFF).toByte(),
    ((value ushr 24) and 0xFF).toByte(),
)

private fun syncSafe(value: Int): ByteArray = byteArrayOf(
    ((value ushr 21) and 0x7F).toByte(),
    ((value ushr 14) and 0x7F).toByte(),
    ((value ushr 7) and 0x7F).toByte(),
    (value and 0x7F).toByte(),
)
