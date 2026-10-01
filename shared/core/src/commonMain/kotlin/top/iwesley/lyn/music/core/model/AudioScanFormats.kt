package top.iwesley.lyn.music.core.model

import top.iwesley.lyn.music.resources.*

private val NON_NAVIDROME_SCANNABLE_AUDIO_EXTENSIONS = setOf(
    "mp3",
    "m4a",
    "aac",
    "wav",
    "flac",
    "ape",
    "ogg",
    "opus",
    "wma",
    "aiff",
    "aif",
)

const val UNSUPPORTED_AUDIO_IMPORT_REASON = "当前平台暂不支持导入该音频格式。"

enum class NonNavidromeAudioScanResult {
    NOT_AUDIO,
    IMPORT_UNSUPPORTED,
    IMPORT_SUPPORTED,
}

fun isNonNavidromeScannableAudioFile(fileName: String): Boolean {
    return fileNameExtension(fileName) in NON_NAVIDROME_SCANNABLE_AUDIO_EXTENSIONS
}

fun classifyNonNavidromeAudioFile(
    fileName: String,
    supportedImportExtensions: Set<String>,
): NonNavidromeAudioScanResult {
    val extension = fileNameExtension(fileName) ?: return NonNavidromeAudioScanResult.NOT_AUDIO
    if (extension !in NON_NAVIDROME_SCANNABLE_AUDIO_EXTENSIONS) {
        return NonNavidromeAudioScanResult.NOT_AUDIO
    }
    return if (extension in supportedImportExtensions) {
        NonNavidromeAudioScanResult.IMPORT_SUPPORTED
    } else {
        NonNavidromeAudioScanResult.IMPORT_UNSUPPORTED
    }
}

fun unsupportedAudioImportFailure(relativePath: String): ImportScanFailure {
    return ImportScanFailure(
        relativePath = relativePath,
        reason = UNSUPPORTED_AUDIO_IMPORT_REASON,
        reasonText = uiText(Res.string.audio_import_unsupported),
    )
}

fun classifyAudioExtensionForImport(
    extension: String?,
    supportedImportExtensions: Set<String>,
): NonNavidromeAudioScanResult {
    val normalizedExtension = normalizeAudioExtension(extension)
        ?: return NonNavidromeAudioScanResult.IMPORT_UNSUPPORTED
    return if (normalizedExtension in supportedImportExtensions) {
        NonNavidromeAudioScanResult.IMPORT_SUPPORTED
    } else {
        NonNavidromeAudioScanResult.IMPORT_UNSUPPORTED
    }
}

fun normalizeAudioExtension(extension: String?): String? {
    val normalized = extension.orEmpty()
        .trim()
        .removePrefix(".")
        .lowercase()
    return normalized.takeIf { it.isNotBlank() }
}

private fun fileNameExtension(fileName: String): String? {
    val extension = fileName.substringAfterLast('.', "")
    return normalizeAudioExtension(extension)
}
