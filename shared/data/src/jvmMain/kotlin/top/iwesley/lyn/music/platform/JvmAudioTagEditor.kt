package top.iwesley.lyn.music.platform

import java.nio.file.Files
import java.nio.file.Path
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import top.iwesley.lyn.music.core.model.AudioTagPatch
import top.iwesley.lyn.music.core.model.AudioTagSnapshot
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension

object JvmAudioTagEditor {
    fun writeSnapshot(
        path: Path,
        relativePath: String,
        patch: AudioTagPatch,
        logger: DiagnosticLogger = NoopDiagnosticLogger,
    ): AudioTagSnapshot {
        val audioFile = AudioFileIO.read(path.toFile())
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
            artworkBytes != null -> replaceArtwork(tag, artworkBytes)
        }

        audioFile.commit()
        return JvmAudioTagReader.readSnapshot(
            path = path,
            relativePath = relativePath,
            logger = logger,
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

    private fun replaceArtwork(tag: Tag, bytes: ByteArray) {
        val tempFile = Files.createTempFile("lynmusic-artwork-", inferArtworkFileExtension(bytes = bytes))
        try {
            Files.write(tempFile, bytes)
            runCatching { tag.deleteArtworkField() }
            tag.setField(ArtworkFactory.createArtworkFromFile(tempFile.toFile()))
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }
}
