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
import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportedTrackCandidate
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.SAME_NAME_LRC_MAX_BYTES
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.buildBasicAuthorizationHeader
import top.iwesley.lyn.music.core.model.buildWebDavTrackUrl
import top.iwesley.lyn.music.core.model.describeWebDavHttpFailure
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.normalizeWebDavRootUrl
import top.iwesley.lyn.music.core.model.parseWebDavLocator
import top.iwesley.lyn.music.core.model.sameNameLyricsRelativePath
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.data.db.LynMusicDatabase

internal data class AndroidWebDavPlaybackTarget(
    val mediaSource: MediaSource,
    val requestUrl: String,
)

private data class AndroidWebDavSession(
    val rootUrl: String,
    val authEnabled: Boolean,
    val allowInsecureTls: Boolean,
    val client: OkHttpClient,
    val sardine: Sardine,
)

internal suspend fun scanAndroidWebDav(
    draft: WebDavSourceDraft,
    sourceId: String,
    artworkDirectory: File,
    logger: DiagnosticLogger,
): ImportScanReport {
    val session = createAndroidWebDavSession(
        rootUrl = draft.rootUrl,
        username = draft.username,
        password = draft.password,
        allowInsecureTls = draft.allowInsecureTls,
    )
    val tracks = mutableListOf<ImportedTrackCandidate>()
    val startedAt = System.currentTimeMillis()
    logger.info(WEBDAV_LOG_TAG) {
        "scan-start source=$sourceId rootUrl=${session.rootUrl} auth=${session.authEnabled} insecureTls=${session.allowInsecureTls} client=sardine-android"
    }
    return runCatching {
        collectAndroidWebDavTracks(
            session = session,
            relativeDirectory = "",
            sourceId = sourceId,
            artworkDirectory = artworkDirectory,
            logger = logger,
            sink = tracks,
        )
        ImportScanReport(tracks)
    }.onSuccess { report ->
        logger.info(WEBDAV_LOG_TAG) {
            "scan-complete source=$sourceId rootUrl=${session.rootUrl} trackCount=${report.tracks.size} elapsedMs=${System.currentTimeMillis() - startedAt} client=sardine-android"
        }
    }.onFailure { throwable ->
        logger.error(WEBDAV_LOG_TAG, throwable) {
            "scan-failed source=$sourceId rootUrl=${session.rootUrl} elapsedMs=${System.currentTimeMillis() - startedAt} client=sardine-android"
        }
    }.getOrThrow()
}

internal fun testAndroidWebDavConnection(
    draft: WebDavSourceDraft,
    logger: DiagnosticLogger,
) {
    val session = createAndroidWebDavSession(
        rootUrl = draft.rootUrl,
        username = draft.username,
        password = draft.password,
        allowInsecureTls = draft.allowInsecureTls,
    )
    logger.debug(WEBDAV_LOG_TAG) {
        "test-connection rootUrl=${session.rootUrl} auth=${session.authEnabled} insecureTls=${session.allowInsecureTls} client=sardine-android"
    }
    try {
        val resource = session.sardine.list(session.rootUrl.ensureTrailingSlash(), 0)
            .filterIsInstance<DavResource>()
            .firstOrNull()
            ?: error("WebDAV 根目录不可访问。")
        if (!resource.isDirectory) {
            error("WebDAV 根 URL 不是目录。")
        }
    } catch (throwable: Throwable) {
        throw throwable.asAndroidWebDavIOException("测试连接", session.authEnabled)
    }
}

internal suspend fun resolveAndroidWebDavPlaybackTarget(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
    logger: DiagnosticLogger,
): AndroidWebDavPlaybackTarget? {
    val webDav = parseWebDavLocator(locator) ?: return null
    val source = database.importSourceDao().getById(webDav.first)?.takeIf { it.enabled } ?: return null
    val password = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
    val requestUrl = buildWebDavTrackUrl(source.rootReference, webDav.second)
    val session = createAndroidWebDavSession(
        rootUrl = source.rootReference,
        username = source.username.orEmpty(),
        password = password,
        allowInsecureTls = source.allowInsecureTls,
    )
    logger.info(WEBDAV_LOG_TAG) {
        "play-start source=${webDav.first} url=$requestUrl auth=${session.authEnabled} insecureTls=${session.allowInsecureTls} client=sardine-android"
    }
    return AndroidWebDavPlaybackTarget(
        mediaSource = ProgressiveMediaSource.Factory(
            WebDavOkHttpDataSourceFactory(
                callFactory = session.client,
                authEnabled = session.authEnabled,
            ),
        ).createMediaSource(MediaItem.fromUri(Uri.parse(requestUrl))),
        requestUrl = requestUrl,
    )
}

internal suspend fun readAndroidWebDavSameNameLyrics(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    track: Track,
    logger: DiagnosticLogger,
): String? {
    val webDav = parseWebDavLocator(track.mediaLocator) ?: return null
    val source = database.importSourceDao().getById(webDav.first)
        ?.takeIf { it.enabled && it.type == ImportSourceType.WEBDAV.name }
        ?: return null
    val lyricsRelativePath = sameNameLyricsRelativePath(webDav.second) ?: return null
    val password = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
    val requestUrl = buildWebDavTrackUrl(source.rootReference, lyricsRelativePath)
    val session = createAndroidWebDavSession(
        rootUrl = source.rootReference,
        username = source.username.orEmpty(),
        password = password,
        allowInsecureTls = source.allowInsecureTls,
    )
    val bytes = downloadAndroidWebDavOptionalFile(
        callFactory = session.client,
        requestUrl = requestUrl,
        authEnabled = session.authEnabled,
        operation = "读取同名歌词",
    ) ?: return null
    if (bytes.isEmpty() || bytes.size > SAME_NAME_LRC_MAX_BYTES) return null
    logger.debug(WEBDAV_LOG_TAG) {
        "same-name-lrc-read source=${webDav.first} url=$requestUrl bytes=${bytes.size}"
    }
    return decodeAndroidSameNameLyricsBytes(bytes)
}

private fun collectAndroidWebDavTracks(
    session: AndroidWebDavSession,
    relativeDirectory: String,
    sourceId: String,
    artworkDirectory: File,
    logger: DiagnosticLogger,
    sink: MutableList<ImportedTrackCandidate>,
) {
    val directoryUrl = if (relativeDirectory.isBlank()) {
        session.rootUrl.ensureTrailingSlash()
    } else {
        buildWebDavTrackUrl(session.rootUrl, relativeDirectory).ensureTrailingSlash()
    }
    logger.debug(WEBDAV_LOG_TAG) {
        "propfind source=$sourceId url=$directoryUrl client=sardine-android"
    }
    val resources = try {
        session.sardine.list(directoryUrl, 1)
            .filterIsInstance<DavResource>()
    } catch (throwable: Throwable) {
        throw throwable.asAndroidWebDavIOException("扫描", session.authEnabled)
    }

    resources.forEach { resource ->
        val resolved = resolveWebDavListedResource(
            rootUrl = session.rootUrl,
            currentDirectory = relativeDirectory,
            resource = resource.toWebDavListedResource(),
        ) ?: return@forEach
        if (resolved.isDirectory) {
            collectAndroidWebDavTracks(
                session = session,
                relativeDirectory = resolved.relativePath,
                sourceId = sourceId,
                artworkDirectory = artworkDirectory,
                logger = logger,
                sink = sink,
            )
        } else if (isSupportedWebDavAudio(resolved.fileName)) {
            val requestUrl = buildWebDavTrackUrl(session.rootUrl, resolved.relativePath)
            sink += runCatching {
                resolveAndroidWebDavScanCandidate(
                    session = session,
                    sourceId = sourceId,
                    resource = resolved,
                    requestUrl = requestUrl,
                    artworkDirectory = artworkDirectory,
                    logger = logger,
                )
            }.onFailure { throwable ->
                logger.warn(WEBDAV_LOG_TAG) {
                    "metadata-failed source=$sourceId url=$requestUrl reason=${throwable.message.orEmpty()}"
                }
            }.getOrElse {
                buildWebDavImportedTrackCandidate(sourceId, resolved)
            }
        }
    }
}

private fun resolveAndroidWebDavScanCandidate(
    session: AndroidWebDavSession,
    sourceId: String,
    resource: WebDavResolvedResource,
    requestUrl: String,
    artworkDirectory: File,
    logger: DiagnosticLogger,
): ImportedTrackCandidate {
    val fallback = buildWebDavImportedTrackCandidate(sourceId, resource)
    if (resource.contentLength <= 0L) return fallback
    val metadata = readAndroidWebDavRemoteMetadata(
        callFactory = session.client,
        sourceId = sourceId,
        requestUrl = requestUrl,
        relativePath = resource.relativePath,
        sizeBytes = resource.contentLength,
        authEnabled = session.authEnabled,
        logger = logger,
    )
    if (metadata == null || !metadata.hasMeaningfulMetadata(resource.relativePath)) {
        logger.info(WEBDAV_LOG_TAG) {
            "metadata-miss source=$sourceId url=$requestUrl"
        }
        return fallback
    }
    val candidate = buildWebDavImportedTrackCandidate(
        sourceId = sourceId,
        resource = resource,
        metadata = metadata,
        storeArtwork = { bytes ->
            storeAndroidWebDavArtwork(
                artworkDirectory = artworkDirectory,
                relativePath = resource.relativePath,
                bytes = bytes,
            )
        },
    )
    logger.info(WEBDAV_LOG_TAG) {
        "metadata-hit source=$sourceId url=$requestUrl title=${candidate.title} artist=${candidate.artistName.orEmpty()} album=${candidate.albumTitle.orEmpty()}"
    }
    return candidate
}

private fun createAndroidWebDavSession(
    rootUrl: String,
    username: String,
    password: String,
    allowInsecureTls: Boolean,
): AndroidWebDavSession {
    val normalizedRootUrl = normalizeWebDavRootUrl(rootUrl)
    val client = buildAndroidWebDavClient(
        username = username,
        password = password,
        allowInsecureTls = allowInsecureTls,
    )
    return AndroidWebDavSession(
        rootUrl = normalizedRootUrl,
        authEnabled = username.isNotBlank(),
        allowInsecureTls = allowInsecureTls,
        client = client,
        sardine = OkHttpSardine(client),
    )
}

private fun buildAndroidWebDavClient(
    username: String,
    password: String,
    allowInsecureTls: Boolean,
): OkHttpClient {
    val builder = OkHttpClient.Builder()
    if (allowInsecureTls) {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
        builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        builder.hostnameVerifier(AlwaysTrueHostnameVerifier)
    }
    buildBasicAuthorizationHeader(username, password)?.let { authHeader ->
        builder.addInterceptor { chain ->
            val request = chain.request()
            val nextRequest = if (request.header("Authorization").isNullOrBlank()) {
                request.newBuilder()
                    .header("Authorization", authHeader)
                    .build()
            } else {
                request
            }
            chain.proceed(nextRequest)
        }
    }
    return builder.build()
}

private fun DavResource.toWebDavListedResource(): WebDavListedResource {
    return WebDavListedResource(
        href = href.toString(),
        isDirectory = isDirectory,
        name = name,
        contentLength = contentLength ?: 0L,
        modifiedAt = modified?.time ?: 0L,
    )
}

private fun readAndroidWebDavRemoteMetadata(
    callFactory: Call.Factory,
    sourceId: String,
    requestUrl: String,
    relativePath: String,
    sizeBytes: Long,
    authEnabled: Boolean,
    logger: DiagnosticLogger,
): RemoteAudioMetadata? {
    val initialHeadBytes = sizeBytes
        .coerceAtMost(RemoteAudioMetadataProbe.HEAD_PROBE_BYTES)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
    if (initialHeadBytes <= 0) return null
    var totalProbeBytes = initialHeadBytes.toLong()
    var headBytes = downloadAndroidWebDavRange(
        callFactory = callFactory,
        requestUrl = requestUrl,
        startByte = 0L,
        length = initialHeadBytes,
        authEnabled = authEnabled,
        operation = "标签探测",
    )
    val requiredHeadBytes = RemoteAudioMetadataProbe.requiredExpandedHeadBytes(relativePath, headBytes)
    if (requiredHeadBytes != null && requiredHeadBytes > headBytes.size) {
        if (requiredHeadBytes > RemoteAudioMetadataProbe.MAX_HEAD_PROBE_BYTES) {
            logger.info(WEBDAV_LOG_TAG) {
                "metadata-skip source=$sourceId url=$requestUrl reason=head-too-large requested=$requiredHeadBytes"
            }
            return null
        }
        val expandedHeadBytes = requiredHeadBytes
            .coerceAtMost(sizeBytes)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        totalProbeBytes = expandedHeadBytes.toLong()
        headBytes = downloadAndroidWebDavRange(
            callFactory = callFactory,
            requestUrl = requestUrl,
            startByte = 0L,
            length = expandedHeadBytes,
            authEnabled = authEnabled,
            operation = "标签探测",
        )
        logger.debug(WEBDAV_LOG_TAG) {
            "metadata-range-expand source=$sourceId url=$requestUrl bytes=${headBytes.size}"
        }
    }
    val tailBytes = if (RemoteAudioMetadataProbe.shouldReadTail(relativePath)) {
        val requestedTailBytes = sizeBytes
            .coerceAtMost(RemoteAudioMetadataProbe.TAIL_PROBE_BYTES)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        if (totalProbeBytes + requestedTailBytes > RemoteAudioMetadataProbe.MAX_TOTAL_PROBE_BYTES) {
            logger.info(WEBDAV_LOG_TAG) {
                "metadata-skip source=$sourceId url=$requestUrl reason=tail-over-budget requested=$requestedTailBytes"
            }
            null
        } else {
            totalProbeBytes += requestedTailBytes
            downloadAndroidWebDavRange(
                callFactory = callFactory,
                requestUrl = requestUrl,
                startByte = (sizeBytes - requestedTailBytes.toLong()).coerceAtLeast(0L),
                length = requestedTailBytes,
                authEnabled = authEnabled,
                operation = "标签探测",
                allowFullResponseFallback = false,
            )
        }
    } else {
        null
    }
    logger.debug(WEBDAV_LOG_TAG) {
        "metadata-range-read source=$sourceId url=$requestUrl head=${headBytes.size} tail=${tailBytes?.size ?: 0}"
    }
    return RemoteAudioMetadataProbe.parse(
        relativePath = relativePath,
        headBytes = headBytes,
        tailBytes = tailBytes,
    )
}

private fun downloadAndroidWebDavRange(
    callFactory: Call.Factory,
    requestUrl: String,
    startByte: Long,
    length: Int,
    authEnabled: Boolean,
    operation: String,
    allowFullResponseFallback: Boolean = true,
): ByteArray {
    if (length <= 0) return ByteArray(0)
    val request = Request.Builder()
        .url(requestUrl)
        .get()
        .header(
            "Range",
            buildWebDavRangeHeader(
                position = startByte,
                requestedLength = length.toLong(),
            ) ?: "bytes=0-${length - 1}",
        )
        .build()
    val response = callFactory.newCall(request).execute()
    return response.use { activeResponse ->
        if (!activeResponse.isSuccessful) {
            throw IOException(
                describeWebDavHttpFailure(
                    operation = operation,
                    statusCode = activeResponse.code,
                    authSent = authEnabled,
                    serverDetail = activeResponse.header("WWW-Authenticate").orEmpty().ifBlank { activeResponse.message },
                ),
            )
        }
        if (startByte > 0L && activeResponse.code == 200 && !allowFullResponseFallback) {
            return@use ByteArray(0)
        }
        val stream = activeResponse.body.byteStream()
        if (startByte > 0L && activeResponse.code == 200) {
            skipAndroidWebDavBytes(stream, startByte)
        }
        readUpTo(stream, length)
    }
}

private fun downloadAndroidWebDavOptionalFile(
    callFactory: Call.Factory,
    requestUrl: String,
    authEnabled: Boolean,
    operation: String,
): ByteArray? {
    val requestedLength = (SAME_NAME_LRC_MAX_BYTES + 1L).toInt()
    val request = Request.Builder()
        .url(requestUrl)
        .get()
        .header("Range", "bytes=0-${requestedLength - 1}")
        .build()
    val response = callFactory.newCall(request).execute()
    return response.use { activeResponse ->
        when {
            activeResponse.code == 404 -> null
            activeResponse.isSuccessful -> readUpTo(activeResponse.body.byteStream(), requestedLength)
            else -> throw IOException(
                describeWebDavHttpFailure(
                    operation = operation,
                    statusCode = activeResponse.code,
                    authSent = authEnabled,
                    serverDetail = activeResponse.header("WWW-Authenticate").orEmpty().ifBlank { activeResponse.message },
                ),
            )
        }
    }
}

private fun readUpTo(
    stream: InputStream,
    length: Int,
): ByteArray {
    val buffer = ByteArray(length)
    var totalRead = 0
    while (totalRead < length) {
        val read = stream.read(buffer, totalRead, length - totalRead)
        if (read <= 0) break
        totalRead += read
    }
    return if (totalRead == buffer.size) buffer else buffer.copyOf(totalRead)
}

private fun skipAndroidWebDavBytes(
    stream: InputStream,
    target: Long,
) {
    var skipped = 0L
    while (skipped < target) {
        val delta = stream.skip(target - skipped)
        if (delta <= 0L) {
            throw EOFException("Unable to skip to requested WebDAV position $target")
        }
        skipped += delta
    }
}

private fun storeAndroidWebDavArtwork(
    artworkDirectory: File,
    relativePath: String,
    bytes: ByteArray,
): String? {
    if (bytes.isEmpty()) return null
    artworkDirectory.mkdirs()
    val fileName = buildString {
        append(relativePath.hashCode().toUInt().toString(16))
        append('-')
        append(bytes.contentHashCode().toUInt().toString(16))
        append(inferArtworkFileExtension(bytes = bytes))
    }
    val target = File(artworkDirectory, fileName)
    if (!target.exists() || target.length() != bytes.size.toLong()) {
        target.writeBytes(bytes)
    }
    return target.absolutePath
}

private fun Throwable.asAndroidWebDavIOException(
    operation: String,
    authEnabled: Boolean,
): IOException {
    val statusCode = reflectStatusCode()
    if (statusCode != null) {
        return IOException(
            describeWebDavHttpFailure(
                operation = operation,
                statusCode = statusCode,
                authSent = authEnabled,
                serverDetail = reflectResponsePhrase() ?: message,
            ),
            this,
        )
    }
    return if (this is IOException) {
        this
    } else {
        IOException(
            "WebDAV $operation 失败: ${message ?: this::class.simpleName.orEmpty()}",
            this,
        )
    }
}

private fun Throwable.reflectStatusCode(): Int? {
    return runCatching {
        javaClass.methods
            .firstOrNull { it.name == "getStatusCode" && it.parameterCount == 0 }
            ?.invoke(this) as? Number
    }.getOrNull()?.toInt()
}

private fun Throwable.reflectResponsePhrase(): String? {
    return runCatching {
        javaClass.methods
            .firstOrNull { it.name == "getResponsePhrase" && it.parameterCount == 0 }
            ?.invoke(this) as? String
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"

private fun isSupportedWebDavAudio(fileName: String): Boolean {
    return fileName.substringAfterLast('.', "").lowercase() in setOf("mp3", "m4a", "aac", "wav", "flac")
}

private object AlwaysTrueHostnameVerifier : HostnameVerifier {
    override fun verify(hostname: String?, session: SSLSession?): Boolean = true
}

private const val WEBDAV_LOG_TAG = "WebDav"

@UnstableApi
private class WebDavOkHttpDataSourceFactory(
    private val callFactory: Call.Factory,
    private val authEnabled: Boolean,
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return WebDavOkHttpDataSource(callFactory, authEnabled)
    }
}

@UnstableApi
private class WebDavOkHttpDataSource(
    private val callFactory: Call.Factory,
    private val authEnabled: Boolean,
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
                buildWebDavRangeHeader(
                    position = dataSpec.position,
                    requestedLength = dataSpec.length,
                )?.let { header("Range", it) }
            }
            .build()
        val response = callFactory.newCall(request).execute()
        if (!response.isSuccessful) {
            val challenge = response.header("WWW-Authenticate").orEmpty()
            val detail = challenge.ifBlank { response.message }
            response.close()
            throw IOException(
                describeWebDavHttpFailure(
                    operation = "播放",
                    statusCode = response.code,
                    authSent = authEnabled,
                    serverDetail = detail,
                ),
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
