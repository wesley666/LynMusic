package top.iwesley.lyn.music.core.model

import top.iwesley.lyn.music.resources.*

/** Keep diagnostics unchanged; application descriptions are resolved only at display time. */
fun audioImportFailure(relativePath: String, throwable: Throwable): ImportScanFailure {
    val detail = throwable.message?.takeIf { it.isNotBlank() }
        ?: throwable::class.simpleName
    return ImportScanFailure(
        relativePath = relativePath,
        reason = detail ?: "读取失败。",
        reasonText = throwable.uiFailureTextOrNull()
            ?: if (detail == null) uiText(Res.string.common_read_failed) else null,
    )
}

/** Each warning carries its own display description and original fallback. */
fun ImportScanWarning.warningUiText(): UiText = text ?: UiText.Raw(diagnostic)
