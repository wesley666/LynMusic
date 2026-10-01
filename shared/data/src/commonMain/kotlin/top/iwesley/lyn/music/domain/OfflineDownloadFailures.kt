package top.iwesley.lyn.music.domain

import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.resources.Res
import top.iwesley.lyn.music.resources.offline_download_http_failed

/** Preserve HTTP classification data while deferring translation until display. */
fun checkOfflineDownloadHttpStatus(statusCode: Int) {
    if (statusCode !in 200..299) {
        throw RemoteSourceHttpException(statusCode, uiText(Res.string.offline_download_http_failed, statusCode))
    }
}
