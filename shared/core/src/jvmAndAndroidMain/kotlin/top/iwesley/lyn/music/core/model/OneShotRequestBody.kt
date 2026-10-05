package top.iwesley.lyn.music.core.model

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel

/**
 * A text body Ktor's OkHttp engine sends as a one-shot `RequestBody`, so OkHttp never resends it on its own: not
 * after a connection breaks once the request went out, and not on a follow-up such as `503` with `Retry-After: 0`,
 * which OkHttp repeats regardless of `retryOnConnectionFailure`. Use it for writes that must not be applied twice
 * (see [LyricsRequest.allowTransportRetry]); a request that never left is still retried.
 */
fun oneShotTextContent(text: String, contentType: ContentType?): OutgoingContent {
    val bytes = text.encodeToByteArray()
    // Same default as Ktor's own String body.
    val type = contentType ?: ContentType.Text.Plain.withParameter("charset", "UTF-8")
    return object : OutgoingContent.ReadChannelContent() {
        override val contentType: ContentType = type
        override val contentLength: Long = bytes.size.toLong()
        override fun readFrom(): ByteReadChannel = ByteReadChannel(bytes)
    }
}
