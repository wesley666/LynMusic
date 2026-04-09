package top.iwesley.lyn.music.platform

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import top.iwesley.lyn.music.core.model.AudioTagSnapshot
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportedTrackCandidate
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.warn

object AndroidAudioTagReader {
    fun readCandidate(
        context: Context,
        uri: Uri,
        displayName: String?,
        relativePath: String,
        artworkDirectory: File,
        logger: DiagnosticLogger,
        sizeBytes: Long = 0L,
        modifiedAt: Long = 0L,
    ): ImportedTrackCandidate {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context, uri)
            ImportedTrackCandidate(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: displayName?.substringBeforeLast('.').orEmpty(),
                artistName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                albumTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')
                    ?.toIntOrNull(),
                discNumber = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                        ?.substringBefore('/')
                        ?.toIntOrNull()
                }.getOrNull(),
                mediaLocator = uri.toString(),
                relativePath = relativePath,
                artworkLocator = retriever.embeddedPicture
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { bytes -> storeArtwork(relativePath, artworkDirectory, bytes) },
                sizeBytes = sizeBytes,
                modifiedAt = modifiedAt,
                embeddedLyrics = null,
            )
        }.onSuccess { candidate ->
            logger.debug(METADATA_LOG_TAG) {
                buildMetadataLogMessage(
                    relativePath = relativePath,
                    candidate = candidate,
                )
            }
        }.getOrElse {
            logger.warn(METADATA_LOG_TAG) {
                "parse-fallback path=$relativePath reason=${it.message ?: it::class.simpleName} title=${displayName?.substringBeforeLast('.') ?: "未知曲目"}"
            }
            ImportedTrackCandidate(
                title = displayName?.substringBeforeLast('.') ?: "未知曲目",
                mediaLocator = uri.toString(),
                relativePath = relativePath,
            )
        }.also {
            runCatching { retriever.release() }
        }
    }

    fun readSnapshot(
        context: Context,
        uri: Uri,
        displayName: String?,
        artworkDirectory: File,
    ): Result<AudioTagSnapshot> {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context, uri)
            AudioTagSnapshot(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: displayName?.substringBeforeLast('.').orEmpty(),
                artistName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                albumTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull(),
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')
                    ?.toIntOrNull(),
                discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                    ?.substringBefore('/')
                    ?.toIntOrNull(),
                embeddedLyrics = null,
                artworkLocator = retriever.embeddedPicture
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { bytes -> storeArtwork(uri.toString(), artworkDirectory, bytes) },
            )
        }.also {
            runCatching { retriever.release() }
        }
    }

    private fun storeArtwork(relativePath: String, artworkDirectory: File, bytes: ByteArray): String {
        artworkDirectory.mkdirs()
        val fileName = buildString {
            append(relativePath.hashCode().toUInt().toString(16))
            append('-')
            append(bytes.size)
            append(inferArtworkFileExtension(bytes = bytes))
        }
        val target = File(artworkDirectory, fileName)
        if (!target.exists() || target.length() != bytes.size.toLong()) {
            target.writeBytes(bytes)
        }
        return target.absolutePath
    }
}

private const val METADATA_LOG_TAG = "Metadata"

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
    }
}
