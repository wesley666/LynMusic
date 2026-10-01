package top.iwesley.lyn.music.core.model

import top.iwesley.lyn.music.resources.*

/** Service details stay raw; application-generated context follows the selected UI language. */
fun offlineDownloadResponseFailureText(
    source: String,
    errorMessage: String? = null,
    status: String? = null,
    errorCode: String? = null,
    isSubsonicResponse: Boolean = true,
): UiText {
    val detail = errorMessage?.let { UiText.Raw(it) }
        ?: status?.let { UiText.Raw("status=$it") }
        ?: uiText(if (isSubsonicResponse) Res.string.offline_subsonic_xml_response else Res.string.offline_xml_response)
    val failure = uiText(Res.string.offline_source_download_failed, source, detail)
    return errorCode?.let { uiText(Res.string.offline_failure_with_code, failure, it) } ?: failure
}
