package top.iwesley.lyn.music.platform

import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildWebDavTrackUrl
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.describeWebDavHttpFailure
import top.iwesley.lyn.music.core.model.parseWebDavLocator
import top.iwesley.lyn.music.data.db.LynMusicDatabase

internal class WebDavCastProxyResource private constructor(
    private val requestUrl: String,
    private val session: AndroidWebDavSession,
    override val mimeType: String,
) : AndroidCastProxyResource {
    override val length: Long? by lazy {
        runCatching {
            session.client.newCall(
                Request.Builder()
                    .url(requestUrl)
                    .head()
                    .build(),
            ).execute().use { response ->
                if (response.isSuccessful) {
                    response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L }
                        ?: response.body.contentLength().takeIf { it > 0L }
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    override suspend fun open(start: Long, length: Long?): InputStream = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .apply {
                buildWebDavRangeHeader(position = start, requestedLength = length ?: -1L)?.let { header("Range", it) }
            }
            .build()
        val response = session.client.newCall(request).execute()
        if (!response.isSuccessful) {
            val exception = response.toWebDavProxyIOException("投屏代理")
            response.close()
            throw exception
        }
        val stream = response.body.byteStream()
        if (start > 0L && response.code == 200) {
            stream.skipFully(start)
        }
        ResponseClosingInputStream(stream, response)
    }

    private fun Response.toWebDavProxyIOException(operation: String): IOException {
        return IOException(
            describeWebDavHttpFailure(
                operation = operation,
                statusCode = code,
                authSent = session.authEnabled,
                serverDetail = header("WWW-Authenticate").orEmpty().ifBlank { message },
            ),
        )
    }

    private class ResponseClosingInputStream(
        private val delegate: InputStream,
        private val response: Response,
    ) : InputStream() {
        override fun read(): Int = delegate.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
        override fun close() {
            runCatching { delegate.close() }
            response.close()
        }
    }

    companion object {
        suspend fun create(
            database: LynMusicDatabase,
            secureCredentialStore: SecureCredentialStore,
            track: Track,
            mimeType: String,
            logger: DiagnosticLogger,
        ): WebDavCastProxyResource? {
            val webDav = parseWebDavLocator(track.mediaLocator) ?: return null
            val source = database.importSourceDao().getById(webDav.first)?.takeIf { it.enabled } ?: return null
            val password = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
            val requestUrl = buildWebDavTrackUrl(source.rootReference, webDav.second)
            val session = createAndroidWebDavSession(
                rootUrl = source.rootReference,
                username = source.username.orEmpty(),
                password = password,
                allowInsecureTls = source.allowInsecureTls,
            )
            logger.debug("CastProxy") {
                "webdav-resource source=${webDav.first} url=$requestUrl auth=${session.authEnabled}"
            }
            return WebDavCastProxyResource(
                requestUrl = requestUrl,
                session = session,
                mimeType = mimeType,
            )
        }
    }
}
