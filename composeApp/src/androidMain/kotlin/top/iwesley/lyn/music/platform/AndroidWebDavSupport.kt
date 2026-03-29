package top.iwesley.lyn.music.platform

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportedTrackCandidate
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.buildBasicAuthorizationHeader
import top.iwesley.lyn.music.core.model.buildWebDavLocator
import top.iwesley.lyn.music.core.model.buildWebDavTrackUrl
import top.iwesley.lyn.music.core.model.describeWebDavHttpFailure
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.normalizeWebDavRootUrl
import top.iwesley.lyn.music.core.model.parseWebDavLocator
import top.iwesley.lyn.music.core.model.parseWebDavMultistatus
import top.iwesley.lyn.music.core.model.resolveWebDavRelativePath
import top.iwesley.lyn.music.data.db.LynMusicDatabase

internal data class AndroidWebDavPlaybackTarget(
    val mediaSource: MediaSource,
    val requestUrl: String,
)

internal suspend fun scanAndroidWebDav(
    draft: WebDavSourceDraft,
    sourceId: String,
    logger: DiagnosticLogger,
): ImportScanReport {
    val rootUrl = normalizeWebDavRootUrl(draft.rootUrl)
    val authHeader = buildBasicAuthorizationHeader(draft.username, draft.password)
    val client = buildAndroidWebDavClient(draft.allowInsecureTls)
    val tracks = mutableListOf<ImportedTrackCandidate>()
    val startedAt = System.currentTimeMillis()
    logger.info(WEBDAV_LOG_TAG) {
        "scan-start source=$sourceId rootUrl=$rootUrl auth=${authHeader != null} insecureTls=${draft.allowInsecureTls}"
    }
    return runCatching {
        collectAndroidWebDavTracks(
            client = client,
            rootUrl = rootUrl,
            relativeDirectory = "",
            sourceId = sourceId,
            authHeader = authHeader,
            logger = logger,
            sink = tracks,
        )
        ImportScanReport(tracks)
    }.onSuccess { report ->
        logger.info(WEBDAV_LOG_TAG) {
            "scan-complete source=$sourceId rootUrl=$rootUrl trackCount=${report.tracks.size} elapsedMs=${System.currentTimeMillis() - startedAt}"
        }
    }.onFailure { throwable ->
        logger.error(WEBDAV_LOG_TAG, throwable) {
            "scan-failed source=$sourceId rootUrl=$rootUrl elapsedMs=${System.currentTimeMillis() - startedAt}"
        }
    }.getOrThrow()
}

internal suspend fun resolveAndroidWebDavPlaybackTarget(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
    logger: DiagnosticLogger,
): AndroidWebDavPlaybackTarget? {
    val webDav = parseWebDavLocator(locator) ?: return null
    val source = database.importSourceDao().getById(webDav.first) ?: error("Missing WebDAV source")
    val password = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
    val requestUrl = buildWebDavTrackUrl(source.rootReference, webDav.second)
    val headers = buildMap {
        buildBasicAuthorizationHeader(source.username.orEmpty(), password)?.let { put("Authorization", it) }
    }
    logger.info(WEBDAV_LOG_TAG) {
        "play-start source=${webDav.first} url=$requestUrl auth=${headers.isNotEmpty()} insecureTls=${source.allowInsecureTls}"
    }
    return AndroidWebDavPlaybackTarget(
        mediaSource = ProgressiveMediaSource.Factory(
            WebDavOkHttpDataSourceFactory(
                callFactory = buildAndroidWebDavClient(source.allowInsecureTls),
                headers = headers,
            ),
        ).createMediaSource(MediaItem.fromUri(Uri.parse(requestUrl))),
        requestUrl = requestUrl,
    )
}

private fun collectAndroidWebDavTracks(
    client: OkHttpClient,
    rootUrl: String,
    relativeDirectory: String,
    sourceId: String,
    authHeader: String?,
    logger: DiagnosticLogger,
    sink: MutableList<ImportedTrackCandidate>,
) {
    val directoryUrl = if (relativeDirectory.isBlank()) {
        rootUrl
    } else {
        buildWebDavTrackUrl(rootUrl, relativeDirectory).ensureTrailingSlash()
    }
    val request = Request.Builder()
        .url(directoryUrl)
        .header("Depth", "1")
        .header("Accept", "application/xml, text/xml, */*")
        .apply {
            authHeader?.let { header("Authorization", it) }
        }
        .method("PROPFIND", WEBDAV_PROPFIND_BODY.toRequestBody(WEBDAV_XML_MEDIA_TYPE))
        .build()
    client.newCall(request).execute().use { response ->
        val payload = response.body.string()
        logger.debug(WEBDAV_LOG_TAG) {
            "propfind source=$sourceId url=$directoryUrl status=${response.code} responseBytes=${payload.length}"
        }
        if (!response.isSuccessful) {
            val challenge = response.header("WWW-Authenticate").orEmpty()
            throw IOException(describeWebDavHttpFailure("扫描", response.code, authHeader != null, challenge))
        }
        parseWebDavMultistatus(payload).forEach { entry ->
            val relativePath = resolveWebDavRelativePath(rootUrl, entry.href) ?: return@forEach
            if (relativePath.isBlank()) return@forEach
            if (entry.isDirectory) {
                collectAndroidWebDavTracks(
                    client = client,
                    rootUrl = rootUrl,
                    relativeDirectory = relativePath,
                    sourceId = sourceId,
                    authHeader = authHeader,
                    logger = logger,
                    sink = sink,
                )
            } else if (isSupportedWebDavAudio(relativePath.substringAfterLast('/'))) {
                sink += ImportedTrackCandidate(
                    title = relativePath.substringAfterLast('/').substringBeforeLast('.'),
                    mediaLocator = buildWebDavLocator(sourceId, relativePath),
                    relativePath = relativePath,
                    modifiedAt = parseWebDavLastModified(entry.lastModified),
                )
            }
        }
    }
}

private fun buildAndroidWebDavClient(allowInsecureTls: Boolean): OkHttpClient {
    if (!allowInsecureTls) return OkHttpClient()
    val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
    }
    return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustManager)
        .hostnameVerifier(AlwaysTrueHostnameVerifier)
        .build()
}

private fun parseWebDavLastModified(value: String?): Long {
    return runCatching {
        value?.takeIf { it.isNotBlank() }
            ?.let { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            ?: 0L
    }.getOrDefault(0L)
}

private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"

private fun isSupportedWebDavAudio(fileName: String): Boolean {
    return fileName.substringAfterLast('.', "").lowercase() in setOf("mp3", "m4a", "aac", "wav")
}

private object AlwaysTrueHostnameVerifier : HostnameVerifier {
    override fun verify(hostname: String?, session: SSLSession?): Boolean = true
}

private const val WEBDAV_LOG_TAG = "WebDav"

private val WEBDAV_XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()

private const val WEBDAV_PROPFIND_BODY = """
<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:resourcetype />
    <d:getlastmodified />
  </d:prop>
</d:propfind>
"""

@UnstableApi
private class WebDavOkHttpDataSourceFactory(
    private val callFactory: Call.Factory,
    private val headers: Map<String, String>,
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return WebDavOkHttpDataSource(callFactory, headers)
    }
}

@UnstableApi
private class WebDavOkHttpDataSource(
    private val callFactory: Call.Factory,
    private val headers: Map<String, String>,
) : BaseDataSource(true) {
    private var response: Response? = null
    private var inputStream: InputStream? = null
    private var currentUri: Uri? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var responseHeaders: Map<String, List<String>> = emptyMap()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val request = Request.Builder()
            .url(dataSpec.uri.toString())
            .get()
            .apply {
                headers.forEach { (key, value) -> header(key, value) }
                if (dataSpec.position != 0L || dataSpec.length != C.LENGTH_UNSET.toLong()) {
                    val end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                        ""
                    } else {
                        (dataSpec.position + dataSpec.length - 1L).toString()
                    }
                    header("Range", "bytes=${dataSpec.position}-$end")
                }
            }
            .build()
        val response = callFactory.newCall(request).execute()
        if (!response.isSuccessful) {
            val challenge = response.header("WWW-Authenticate").orEmpty()
            response.close()
            throw IOException(
                "WebDAV stream request failed with HTTP ${response.code}" +
                    " authSent=${headers.containsKey("Authorization")}" +
                    challenge.takeIf { it.isNotBlank() }?.let { " challenge=$it" }.orEmpty(),
            )
        }
        val stream = response.body.byteStream()
        if (dataSpec.position > 0L && response.code == 200) {
            skipFully(stream, dataSpec.position)
        }
        this.response = response
        this.inputStream = stream
        this.currentUri = Uri.parse(response.request.url.toString())
        this.responseHeaders = response.headers.toMultimap()
        this.bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            response.body.contentLength() >= 0L -> response.body.contentLength()
            else -> C.LENGTH_UNSET.toLong()
        }
        this.opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val bytesToRead = when {
            bytesRemaining == C.LENGTH_UNSET.toLong() -> length
            else -> minOf(length.toLong(), bytesRemaining).toInt()
        }
        val bytesRead = inputStream?.read(buffer, offset, bytesToRead) ?: C.RESULT_END_OF_INPUT
        if (bytesRead == C.RESULT_END_OF_INPUT) {
            return C.RESULT_END_OF_INPUT
        }
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead.toLong()
        }
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = currentUri

    override fun getResponseHeaders(): Map<String, List<String>> = responseHeaders

    override fun close() {
        runCatching { inputStream?.close() }
        runCatching { response?.close() }
        inputStream = null
        response = null
        currentUri = null
        responseHeaders = emptyMap()
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun skipFully(stream: InputStream, target: Long) {
        var skipped = 0L
        while (skipped < target) {
            val delta = stream.skip(target - skipped)
            if (delta <= 0L) {
                throw EOFException("Unable to skip to requested WebDAV position $target")
            }
            skipped += delta
        }
    }
}
