package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.NonNavidromeAudioScanResult
import top.iwesley.lyn.music.core.model.classifyNonNavidromeAudioFile

internal val ANDROID_SUPPORTED_IMPORT_AUDIO_EXTENSIONS = setOf(
    "mp3",
    "m4a",
    "aac",
    "wav",
    "flac",
)

internal fun classifyAndroidScannedAudioFile(fileName: String): NonNavidromeAudioScanResult {
    return classifyNonNavidromeAudioFile(
        fileName = fileName,
        supportedImportExtensions = ANDROID_SUPPORTED_IMPORT_AUDIO_EXTENSIONS,
    )
}
