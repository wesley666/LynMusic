package top.iwesley.lyn.music.platform

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v22Tag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import top.iwesley.lyn.music.core.model.AudioTagSnapshot
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportedTrackCandidate
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.warn

object JvmAudioTagReader {
    private val artworkDirectory = File(File(System.getProperty("user.home")), ".lynmusic/artwork").apply {
        mkdirs()
    }

    fun read(path: Path, relativePath: String, logger: DiagnosticLogger): ImportedTrackCandidate {
        return readInternal(
            path = path,
            relativePath = relativePath,
            logger = logger,
            allowTagOnlyFallback = false,
        )
    }

    fun readRemoteSnippet(path: Path, relativePath: String, logger: DiagnosticLogger): ImportedTrackCandidate {
        return readInternal(
            path = path,
            relativePath = relativePath,
            logger = logger,
            allowTagOnlyFallback = true,
        )
    }

    fun readSnapshot(
        path: Path,
        relativePath: String,
        logger: DiagnosticLogger = NoopDiagnosticLogger,
    ): AudioTagSnapshot {
        val fallbackTitle = relativePath.fallbackTitle()
        return runCatching {
            val audioFile = AudioFileIO.read(path.toFile())
            buildSnapshot(
                audioFile = audioFile,
                path = path,
                relativePath = relativePath,
                fallbackTitle = fallbackTitle,
            )
        }.onFailure {
            logger.warn(METADATA_LOG_TAG) {
                "snapshot-fallback path=$relativePath reason=${it.message ?: it::class.simpleName}"
            }
        }.getOrElse {
            AudioTagSnapshot(
                title = fallbackTitle,
            )
        }
    }

    fun requiredRemoteSnippetBytes(path: Path, relativePath: String): Long? {
        if (relativePath.substringAfterLast('.', "").lowercase() != "mp3") return null
        val tagSize = runCatching { AbstractID3v2Tag.getV2TagSizeIfExists(path.toFile()) }.getOrNull() ?: return null
        return tagSize.takeIf { it > AbstractID3v2Tag.TAG_HEADER_LENGTH }
    }

    private fun readInternal(
        path: Path,
        relativePath: String,
        logger: DiagnosticLogger,
        allowTagOnlyFallback: Boolean,
    ): ImportedTrackCandidate {
        val fallbackTitle = relativePath.fallbackTitle()
        return runCatching {
            readWithAudioHeader(path, relativePath, fallbackTitle)
        }.recoverCatching { throwable ->
            if (!allowTagOnlyFallback) throw throwable
            readMp3TagOnly(path, relativePath, fallbackTitle)
                ?: throw throwable
        }.onSuccess { candidate ->
            logger.debug(METADATA_LOG_TAG) {
                buildMetadataLogMessage(
                    relativePath = relativePath,
                    candidate = candidate,
                )
            }
        }.getOrElse {
            logger.warn(METADATA_LOG_TAG) {
                "parse-fallback path=$relativePath reason=${it.message ?: it::class.simpleName} title=$fallbackTitle"
            }
            ImportedTrackCandidate(
                title = fallbackTitle,
                mediaLocator = path.pathString,
                relativePath = relativePath,
                sizeBytes = runCatching { Files.size(path) }.getOrDefault(0L),
                modifiedAt = Files.getLastModifiedTime(path).toMillis(),
            )
        }
    }

    private fun readWithAudioHeader(
        path: Path,
        relativePath: String,
        fallbackTitle: String,
    ): ImportedTrackCandidate {
        val audioFile = AudioFileIO.read(path.toFile())
        return buildCandidate(
            path = path,
            relativePath = relativePath,
            fallbackTitle = fallbackTitle,
            tag = audioFile.tag,
            durationMs = audioFile.audioHeader.trackLength.toLong().coerceAtLeast(0L) * 1_000L,
        )
    }

    private fun readMp3TagOnly(
        path: Path,
        relativePath: String,
        fallbackTitle: String,
    ): ImportedTrackCandidate? {
        if (relativePath.substringAfterLast('.', "").lowercase() != "mp3") return null
        val tagSize = requiredRemoteSnippetBytes(path, relativePath)?.toInt() ?: return null
        if (Files.size(path) < tagSize) return null
        val buffer = FileInputStream(path.toFile()).channel.use { channel ->
            ByteBuffer.allocate(tagSize).apply {
                channel.read(this, 0)
                rewind()
            }
        }
        val tag = listOf<(ByteBuffer, String) -> Tag>(
            { data, fileName -> ID3v24Tag(data, fileName) },
            { data, fileName -> ID3v23Tag(data, fileName) },
            { data, fileName -> ID3v22Tag(data, fileName) },
        ).firstNotNullOfOrNull { reader ->
            runCatching { reader(buffer.duplicate().rewindAndReturn(), path.name) }.getOrNull()
        } ?: return null
        return buildCandidate(
            path = path,
            relativePath = relativePath,
            fallbackTitle = fallbackTitle,
            tag = tag,
            durationMs = 0L,
        )
    }

    private fun buildCandidate(
        path: Path,
        relativePath: String,
        fallbackTitle: String,
        tag: Tag?,
        durationMs: Long,
    ): ImportedTrackCandidate {
        return ImportedTrackCandidate(
            title = tag.firstNonBlank(FieldKey.TITLE) ?: fallbackTitle,
            artistName = tag.firstNonBlank(FieldKey.ARTIST, FieldKey.ALBUM_ARTIST),
            albumTitle = tag.firstNonBlank(FieldKey.ALBUM),
            durationMs = durationMs,
            trackNumber = tag.firstInt(FieldKey.TRACK),
            discNumber = tag.firstInt(FieldKey.DISC_NO),
            mediaLocator = path.pathString,
            relativePath = relativePath,
            artworkLocator = tag?.firstArtwork?.binaryData?.takeIf { it.isNotEmpty() }?.let { bytes ->
                storeArtwork(path, bytes)
            },
            embeddedLyrics = tag.firstLyrics(),
            sizeBytes = runCatching { Files.size(path) }.getOrDefault(0L),
            modifiedAt = Files.getLastModifiedTime(path).toMillis(),
        )
    }

    private fun buildSnapshot(
        audioFile: AudioFile,
        path: Path,
        relativePath: String,
        fallbackTitle: String,
    ): AudioTagSnapshot {
        val tag = audioFile.tag
        return AudioTagSnapshot(
            title = tag.firstNonBlank(FieldKey.TITLE) ?: fallbackTitle,
            artistName = tag.firstNonBlank(FieldKey.ARTIST),
            albumTitle = tag.firstNonBlank(FieldKey.ALBUM),
            albumArtist = tag.firstNonBlank(FieldKey.ALBUM_ARTIST),
            year = tag.firstInt(FieldKey.YEAR, FieldKey.ALBUM_YEAR),
            genre = tag.firstNonBlank(FieldKey.GENRE),
            comment = tag.firstNonBlank(FieldKey.COMMENT),
            composer = tag.firstNonBlank(FieldKey.COMPOSER),
            isCompilation = tag.firstBoolean(FieldKey.IS_COMPILATION),
            tagLabel = tagLabelFor(audioFile),
            trackNumber = tag.firstInt(FieldKey.TRACK),
            discNumber = tag.firstInt(FieldKey.DISC_NO),
            embeddedLyrics = tag.firstLyrics(),
            artworkLocator = tag?.firstArtwork?.binaryData?.takeIf { it.isNotEmpty() }?.let { bytes ->
                storeArtwork(path, bytes)
            },
        )
    }

    private fun storeArtwork(path: Path, bytes: ByteArray): String {
        val fileName = buildString {
            append(path.toAbsolutePath().normalize().toString().hashCode().toUInt().toString(16))
            append('-')
            append(Files.getLastModifiedTime(path).toMillis())
            append(inferArtworkFileExtension(bytes = bytes))
        }
        val target = File(artworkDirectory, fileName)
        if (!target.exists() || target.length() != bytes.size.toLong()) {
            target.writeBytes(bytes)
        }
        return target.absolutePath
    }
}

private fun ByteBuffer.rewindAndReturn(): ByteBuffer = apply { rewind() }

private fun String.fallbackTitle(): String {
    val fileName = substringAfterLast('/').ifBlank { this }
    return fileName.substringBeforeLast('.', fileName)
}

private fun Tag?.firstNonBlank(vararg keys: FieldKey): String? {
    val current = this ?: return null
    return keys.firstNotNullOfOrNull { key ->
        current.getFirst(key)?.trim()?.takeIf { it.isNotBlank() }
    }
}

private fun Tag?.firstInt(vararg keys: FieldKey): Int? {
    val current = this ?: return null
    return keys.firstNotNullOfOrNull { key ->
        current.getFirst(key)
            ?.trim()
            ?.substringBefore('/')
            ?.substringBefore('.')
            ?.toIntOrNull()
    }
}

private fun Tag?.firstBoolean(key: FieldKey): Boolean {
    val value = this?.getFirst(key)?.trim()?.lowercase().orEmpty()
    return value == "1" || value == "true" || value == "yes"
}

private fun Tag?.firstLyrics(): String? {
    val current = this ?: return null
    return runCatching { current.getFirst(FieldKey.LYRICS) }
        .getOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private fun buildMetadataLogMessage(
    relativePath: String,
    candidate: ImportedTrackCandidate,
): String {
    return buildString {
        append("parsed path=")
        append(relativePath)
        append(" title=")
        append(candidate.title)
        append(" artist=")
        append(candidate.artistName.orEmpty())
        append(" album=")
        append(candidate.albumTitle.orEmpty())
        append(" durationMs=")
        append(candidate.durationMs)
        append(" track=")
        append(candidate.trackNumber?.toString().orEmpty())
        append(" disc=")
        append(candidate.discNumber?.toString().orEmpty())
        append(" artwork=")
        append(candidate.artworkLocator != null)
        append(" lyrics=")
        append(candidate.embeddedLyrics.toLyricsPreview())
    }
}

private fun String?.toLyricsPreview(maxLength: Int = 80): String {
    val text = this?.lineSequence()
        ?.map { it.trim() }
        ?.firstOrNull { it.isNotBlank() }
        .orEmpty()
    if (text.isBlank()) return "none"
    return text.take(maxLength)
}

private const val METADATA_LOG_TAG = "Metadata"

private fun tagLabelFor(audioFile: AudioFile): String? {
    val tag = audioFile.tag ?: return null
    return when (tag) {
        is ID3v24Tag -> "ID3v2.4"
        is ID3v23Tag -> "ID3v2.3"
        is ID3v22Tag -> "ID3v2.2"
        else -> tag::class.simpleName?.removeSuffix("Tag")
    }
}
