package top.iwesley.lyn.music.data.repository

import top.iwesley.lyn.music.core.model.ImportScanWarning
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.errorMessageForStorage
import top.iwesley.lyn.music.core.model.resolveUiTextForStorage
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.warningUiText
import top.iwesley.lyn.music.resources.*

internal suspend fun scanErrorMessageForStorage(error: Throwable): String =
    error.errorMessageForStorage(uiText(Res.string.source_scan_failed_status), "Scan failed.")

internal suspend fun scanWarningsForStorage(warnings: List<ImportScanWarning>): String? {
    val original = warnings.joinToString("\n") { it.diagnostic }.takeIf { it.isNotBlank() } ?: return null
    if (warnings.all { it.text == null }) return original
    return resolveUiTextForStorage(
        UiText.Joined(warnings.map { it.warningUiText() }, "\n"),
        fallbackMessage = original,
    )
}
