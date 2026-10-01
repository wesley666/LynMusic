package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.UiTextArgumentException
import top.iwesley.lyn.music.core.model.UiTextUnsupportedException
import android.content.Context
import android.graphics.Typeface
import androidx.activity.ComponentActivity
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.DEFAULT_LYRICS_SHARE_FONT_PREVIEW_TEXT
import top.iwesley.lyn.music.core.model.GlobalDiagnosticLogger
import top.iwesley.lyn.music.core.model.LyricsShareFontKind
import top.iwesley.lyn.music.core.model.LyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.buildLyricsShareImportedFontKey
import top.iwesley.lyn.music.core.model.parseLyricsShareImportedFontHash

class AndroidLyricsShareFontLibraryPlatformService internal constructor(
    context: Context,
    private val activityActions: AndroidActivityActions,
) : LyricsShareFontLibraryPlatformService {
    constructor(activity: ComponentActivity) : this(
        context = activity.applicationContext,
        activityActions = FixedAndroidActivityActions(activity, GlobalDiagnosticLogger),
    )

    private val context = context.applicationContext
    private val rootDirectory = File(context.filesDir, "lyrics-share-fonts")

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

    override suspend fun importFont(): Result<LyricsShareFontOption?> = runCatching {
        rootDirectory.mkdirs()
        val uri = activityActions.pickContent("*/*") ?: return@runCatching null
        withContext(Dispatchers.IO) {
            val document = DocumentFile.fromSingleUri(context, uri)
            val originalName = document?.name ?: uri.lastPathSegment.orEmpty()
            val extension = normalizeAndroidImportedLyricsShareFontExtension(originalName)
                ?: throw UiTextException(uiText(Res.string.font_import_format_unsupported))
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw UiTextException(uiText(Res.string.font_selection_unreadable))
            val contentHash = sha256HexAndroid(bytes)
            val sanitizedOriginalName = sanitizeAndroidImportedLyricsShareFontName(originalName.substringBeforeLast('.'))
            val outputFile = File(rootDirectory, "${contentHash}__${sanitizedOriginalName}.$extension")
            if (!outputFile.exists()) {
                val tempFile = File(rootDirectory, "$contentHash.importing.$extension")
                tempFile.writeBytes(bytes)
                validateAndroidImportedFontFile(tempFile)
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
        runCatching { resolveImportedFontFile(fontKey)?.absolutePath }
    }

    private fun toImportedFontOption(file: File): LyricsShareFontOption? {
        val metadata = parseAndroidImportedLyricsShareFontFile(file.name) ?: return null
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
                file.isFile && parseAndroidImportedLyricsShareFontFile(file.name)?.fontKey == buildLyricsShareImportedFontKey(contentHash)
            }
    }
}

private fun validateAndroidImportedFontFile(file: File) {
    Typeface.createFromFile(file).also { typeface ->
        check(typeface != null) { "无法加载所选字体文件。" }
    }
}

private data class AndroidImportedLyricsShareFontFileMetadata(
    val fontKey: String,
    val displayName: String,
)

private fun parseAndroidImportedLyricsShareFontFile(fileName: String): AndroidImportedLyricsShareFontFileMetadata? {
    val separatorIndex = fileName.indexOf("__")
    if (separatorIndex <= 0) return null
    val extension = normalizeAndroidImportedLyricsShareFontExtension(fileName) ?: return null
    val contentHash = fileName.substring(0, separatorIndex).trim().lowercase().takeIf { it.isNotBlank() } ?: return null
    val rawDisplayName = fileName
        .substring(separatorIndex + 2)
        .removeSuffix(".$extension")
        .trim()
        .takeIf { it.isNotBlank() }
        ?: return null
    return AndroidImportedLyricsShareFontFileMetadata(
        fontKey = buildLyricsShareImportedFontKey(contentHash),
        displayName = rawDisplayName,
    )
}

private fun normalizeAndroidImportedLyricsShareFontExtension(fileName: String): String? {
    val normalized = fileName.substringAfterLast('.', "").trim().lowercase()
    return normalized.takeIf { it == "ttf" || it == "otf" }
}

private fun sanitizeAndroidImportedLyricsShareFontName(name: String): String {
    return name
        .trim()
        .replace(Regex("""[\\/:*?"<>|]+"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim('_', ' ')
        .ifBlank { "font" }
}

private fun sha256HexAndroid(bytes: ByteArray): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
