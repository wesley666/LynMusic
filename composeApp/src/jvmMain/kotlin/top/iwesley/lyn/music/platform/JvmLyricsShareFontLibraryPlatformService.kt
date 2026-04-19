package top.iwesley.lyn.music.platform

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface
import top.iwesley.lyn.music.core.model.DEFAULT_LYRICS_SHARE_FONT_PREVIEW_TEXT
import top.iwesley.lyn.music.core.model.LyricsShareFontKind
import top.iwesley.lyn.music.core.model.LyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.buildLyricsShareImportedFontKey
import top.iwesley.lyn.music.core.model.parseLyricsShareImportedFontHash

class JvmLyricsShareFontLibraryPlatformService(
    private val rootDirectory: File = File(File(System.getProperty("user.home")), ".lynmusic/lyrics-share-fonts"),
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
                title = "导入歌词分享字体",
                extensionFilter = JvmFileExtensionFilter(
                    description = "字体文件",
                    rawExtensions = listOf("ttf", "otf"),
                ),
            ) ?: return@runCatching null
            val originalFile = selectedPath.toFile()
            val extension = normalizeImportedLyricsShareFontExtension(originalFile.name)
                ?: error("仅支持导入 .ttf 或 .otf 字体。")
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
                error("删除字体文件失败。")
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
        val previewBytes = runCatching {
            renderJvmImportedFontPreview(file.absolutePath)
        }.getOrNull()
        return LyricsShareFontOption(
            fontKey = metadata.fontKey,
            displayName = metadata.displayName,
            previewText = DEFAULT_LYRICS_SHARE_FONT_PREVIEW_TEXT,
            isPrioritized = true,
            kind = LyricsShareFontKind.IMPORTED,
            previewPngBytes = previewBytes,
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

private fun validateJvmImportedFontFile(file: File) {
    checkNotNull(FontMgr.default.makeFromFile(file.absolutePath)) { "无法加载所选字体文件。" }
}

private fun renderJvmImportedFontPreview(fontPath: String): ByteArray {
    val typeface = FontMgr.default.makeFromFile(fontPath) ?: error("无法渲染字体预览。")
    val surface = Surface.makeRasterN32Premul(480, 120)
    val canvas = surface.canvas
    canvas.clear(0xFFF7F4EE.toInt())
    val paint = Paint().apply {
        isAntiAlias = true
        color = 0xFF2E2A24.toInt()
    }
    val font = Font(typeface, 42f)
    canvas.drawString(DEFAULT_LYRICS_SHARE_FONT_PREVIEW_TEXT, 28f, 74f, font, paint)
    val encoded = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG, 100)
        ?: error("无法编码字体预览。")
    return encoded.bytes
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
