package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.diagnosticMessage
import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.ImportScanFailure
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.checkUi
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiFailureTextOrNull
import top.iwesley.lyn.music.core.model.uiText

internal fun checkIosAudioFileAvailable(available: Boolean) {
    checkUi(available) { uiText(Res.string.ios_audio_file_unavailable) }
}

internal fun iosAudioImportFailure(relativePath: String, throwable: Throwable): ImportScanFailure {
    val fallback = uiText(Res.string.ios_audio_file_read_failed)
    val originalDetail = throwable.message?.takeIf { it.isNotBlank() }
    return ImportScanFailure(
        relativePath = relativePath,
        reason = originalDetail ?: (fallback).diagnosticMessage(),
        reasonText = throwable.uiFailureTextOrNull() ?: originalDetail?.let(UiText::Raw) ?: fallback,
    )
}
