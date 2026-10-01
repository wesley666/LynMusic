package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.resolveUiString
import top.iwesley.lyn.music.core.model.checkUi
import top.iwesley.lyn.music.core.model.UiTextException
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FontMgr
import top.iwesley.lyn.music.core.model.DEFAULT_LYRICS_SHARE_FONT_PREVIEW_TEXT
import top.iwesley.lyn.music.core.model.LyricsShareFontKind
import top.iwesley.lyn.music.core.model.LyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.JvmAppDataDirectory
import top.iwesley.lyn.music.core.model.buildLyricsShareImportedFontKey
import top.iwesley.lyn.music.core.model.parseLyricsShareImportedFontHash

class JvmLyricsShareFontLibraryPlatformService(
    private val rootDirectory: File = JvmAppDataDirectory.resolve("lyrics-share-fonts"),
) : LyricsShareFontLibraryPlatformService {
    override suspend fun listImportedFonts(): Result<List<LyricsShareFontOption>> = withContext(Dispatchers.IO) {
        runCatching {
            rootDirectory.mkdirs()
            rootDirectory.listFiles()
                .orEmpty()
                .filter { it.isFile }
                .mapNotNull(::toImportedFontOption)
                .sortedBy { it.displayName.lowercase() }
        }
    }

    override suspend fun importFont(): Result<LyricsShareFontOption?> = withContext(Dispatchers.IO) {
        runCatching {
            rootDirectory.mkdirs()
            val selectedPath = JvmNativeFilePicker.pickOpenFile(
                title = resolveUiString(Res.string.desktop_picker_font_title),
                extensionFilter = JvmFileExtensionFilter(
                    description = resolveUiString(Res.string.desktop_picker_font_files),
                    rawExtensions = listOf("ttf", "otf"),
                ),
            ) ?: return@runCatching null
            val originalFile = selectedPath.toFile()
            val extension = normalizeImportedLyricsShareFontExtension(originalFile.name)
                ?: throw UiTextException(uiText(Res.string.font_import_format_unsupported))
            val bytes = Files.readAllBytes(selectedPath)
            val contentHash = sha256Hex(bytes)
            val sanitizedOriginalName = sanitizeImportedLyricsShareFontName(originalFile.nameWithoutExtension)
            val outputFile = File(rootDirectory, "${contentHash}__${sanitizedOriginalName}.$extension")
            if (!outputFile.exists()) {
                val tempFile = File(rootDirectory, "$contentHash.importing.$extension")
                tempFile.writeBytes(bytes)
                validateJvmImportedFontFile(tempFile)
                if (!tempFile.renameTo(outputFile)) {
                    tempFile.copyTo(outputFile, overwrite = true)
                    tempFile.delete()
                }
            }
            toImportedFontOption(outputFile)
        }
    }

    override suspend fun deleteImportedFont(fontKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveImportedFontFile(fontKey) ?: return@runCatching Unit
            if (file.exists() && !file.delete()) {
                throw UiTextException(uiText(Res.string.font_file_delete_failed))
            }
        }
    }

    override suspend fun resolveImportedFontPath(fontKey: String): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            resolveImportedFontFile(fontKey)?.absolutePath
        }
    }

    private fun toImportedFontOption(file: File): LyricsShareFontOption? {
        val metadata = parseImportedLyricsShareFontFile(file.name) ?: return null
        return LyricsShareFontOption(
            fontKey = metadata.fontKey,
            displayName = metadata.displayName,
            previewText = DEFAULT_LYRICS_SHARE_FONT_PREVIEW_TEXT,
            isPrioritized = true,
            kind = LyricsShareFontKind.IMPORTED,
            fontFilePath = file.absolutePath,
        )
    }

    private fun resolveImportedFontFile(fontKey: String): File? {
        val contentHash = parseLyricsShareImportedFontHash(fontKey) ?: return null
        rootDirectory.mkdirs()
        return rootDirectory.listFiles()
            .orEmpty()
            .firstOrNull { file ->
                file.isFile && parseImportedLyricsShareFontFile(file.name)?.fontKey == buildLyricsShareImportedFontKey(contentHash)
            }
    }
}

internal fun validateJvmImportedFontFile(file: File) {
    checkUi(FontMgr.default.makeFromFile(file.absolutePath) != null) { uiText(Res.string.lyrics_font_file_unreadable) }
}

private data class ImportedLyricsShareFontFileMetadata(
    val fontKey: String,
    val displayName: String,
)

private fun parseImportedLyricsShareFontFile(fileName: String): ImportedLyricsShareFontFileMetadata? {
    val separatorIndex = fileName.indexOf("__")
    if (separatorIndex <= 0) return null
    val extension = normalizeImportedLyricsShareFontExtension(fileName)
        ?: return null
    val contentHash = fileName.substring(0, separatorIndex).trim().lowercase().takeIf { it.isNotBlank() } ?: return null
    val rawDisplayName = fileName
        .substring(separatorIndex + 2)
        .removeSuffix(".$extension")
        .trim()
        .takeIf { it.isNotBlank() }
        ?: return null
    return ImportedLyricsShareFontFileMetadata(
        fontKey = buildLyricsShareImportedFontKey(contentHash),
        displayName = rawDisplayName,
    )
}

internal fun normalizeImportedLyricsShareFontExtension(fileName: String): String? {
    val normalized = fileName.substringAfterLast('.', "").trim().lowercase()
    return normalized.takeIf { it == "ttf" || it == "otf" }
}

internal fun sanitizeImportedLyricsShareFontName(name: String): String {
    return name
        .trim()
        .replace(Regex("""[\\/:*?"<>|]+"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim('_', ' ')
        .ifBlank { "font" }
}

internal fun sha256Hex(bytes: ByteArray): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
