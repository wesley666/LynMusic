package top.iwesley.lyn.music.platform

import java.io.File
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.id3.ID3v22Tag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import top.iwesley.lyn.music.core.model.AudioTagPatch
import top.iwesley.lyn.music.core.model.AudioTagSnapshot
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.stableArtworkBytesHash

object AndroidAudioTagFileSupport {
    fun readSnapshot(
        file: File,
        relativePath: String,
        artworkDirectory: File,
    ): AudioTagSnapshot {
        val audioFile = AudioFileIO.read(file)
        val fallbackTitle = fallbackTitle(relativePath, file.name)
        return buildSnapshot(
            audioFile = audioFile,
            file = file,
            fallbackTitle = fallbackTitle,
            artworkDirectory = artworkDirectory,
        )
    }

    fun write(
        file: File,
        patch: AudioTagPatch,
        tempDirectory: File,
    ) {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault

        applyTextField(tag, FieldKey.TITLE, patch.title)
        applyTextField(tag, FieldKey.ARTIST, patch.artistName)
        applyTextField(tag, FieldKey.ALBUM, patch.albumTitle)
        applyTextField(tag, FieldKey.ALBUM_ARTIST, patch.albumArtist)
        applyTextField(tag, FieldKey.GENRE, patch.genre)
        applyTextField(tag, FieldKey.COMMENT, patch.comment)
        applyTextField(tag, FieldKey.COMPOSER, patch.composer)
        applyTextField(tag, FieldKey.LYRICS, patch.embeddedLyrics)
        applyNumericField(tag, FieldKey.YEAR, patch.year)
        applyNumericField(tag, FieldKey.ALBUM_YEAR, patch.year)
        applyNumericField(tag, FieldKey.TRACK, patch.trackNumber)
        applyNumericField(tag, FieldKey.DISC_NO, patch.discNumber)
        applyCompilationField(tag, patch.isCompilation)

        val artworkBytes = patch.artworkBytes
        when {
            patch.clearArtwork -> runCatching { tag.deleteArtworkField() }
            artworkBytes != null -> replaceArtwork(tag, artworkBytes, tempDirectory)
        }

        audioFile.commit()
    }

    private fun buildSnapshot(
        audioFile: AudioFile,
        file: File,
        fallbackTitle: String,
        artworkDirectory: File,
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
                storeArtwork(file, bytes, artworkDirectory)
            },
        )
    }

    private fun applyTextField(tag: Tag, key: FieldKey, value: String?) {
        val normalized = value?.trim()
        if (normalized.isNullOrEmpty()) {
            runCatching { tag.deleteField(key) }
        } else {
            tag.setField(key, normalized)
        }
    }

    private fun applyNumericField(tag: Tag, key: FieldKey, value: Int?) {
        if (value == null) {
            runCatching { tag.deleteField(key) }
        } else {
            tag.setField(key, value.toString())
        }
    }

    private fun applyCompilationField(tag: Tag, value: Boolean?) {
        if (value == null) {
            runCatching { tag.deleteField(FieldKey.IS_COMPILATION) }
        } else {
            tag.setField(tag.createCompilationField(value))
        }
    }

    private fun replaceArtwork(tag: Tag, bytes: ByteArray, tempDirectory: File) {
        tempDirectory.mkdirs()
        val tempFile = File.createTempFile(
            "lynmusic-artwork-",
            inferArtworkFileExtension(bytes = bytes),
            tempDirectory,
        )
        try {
            tempFile.writeBytes(bytes)
            runCatching { tag.deleteArtworkField() }
            tag.setField(ArtworkFactory.createArtworkFromFile(tempFile))
        } finally {
            tempFile.delete()
        }
    }

    private fun storeArtwork(file: File, bytes: ByteArray, artworkDirectory: File): String {
        artworkDirectory.mkdirs()
        val fileName = buildString {
            append(bytes.stableArtworkBytesHash())
            append(inferArtworkFileExtension(bytes = bytes))
        }
        val target = File(artworkDirectory, fileName)
        if (!target.exists() || target.length() != bytes.size.toLong()) {
            target.writeBytes(bytes)
        }
        return target.absolutePath
    }

    private fun fallbackTitle(relativePath: String, fileName: String): String {
        val rawName = relativePath.substringAfterLast('/').ifBlank { fileName }
        return rawName.substringBeforeLast('.', rawName)
    }
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

private fun tagLabelFor(audioFile: AudioFile): String? {
    val tag = audioFile.tag ?: return null
    return when (tag) {
        is ID3v24Tag -> "ID3v2.4"
        is ID3v23Tag -> "ID3v2.3"
        is ID3v22Tag -> "ID3v2.2"
        else -> tag::class.simpleName?.removeSuffix("Tag")
    }
}
