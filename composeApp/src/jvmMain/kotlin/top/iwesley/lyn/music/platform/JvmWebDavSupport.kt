package top.iwesley.lyn.music.platform

import com.github.sardine.Sardine
import com.github.sardine.SardineFactory
import com.github.sardine.impl.SardineException
import com.github.sardine.impl.SardineImpl
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.ssl.SSLContexts
import uk.co.caprica.vlcj.media.callback.CallbackMedia
import uk.co.caprica.vlcj.media.callback.seekable.SeekableCallbackMedia
import top.iwesley.lyn.music.core.model.buildBasicAuthorizationHeader
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportScanFailure
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportedTrackCandidate
import top.iwesley.lyn.music.core.model.SAME_NAME_LRC_MAX_BYTES
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.buildWebDavLocator
import top.iwesley.lyn.music.core.model.buildWebDavTrackUrl
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.describeWebDavHttpFailure
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.normalizeWebDavRootUrl
import top.iwesley.lyn.music.core.model.parseWebDavLocator
import top.iwesley.lyn.music.core.model.sameNameLyricsRelativePath
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.data.db.LynMusicDatabase

internal data class JvmWebDavPlaybackTarget(
    val media: CallbackMedia,
    val requestUrl: String,
)

internal suspend fun scanJvmWebDav(
    draft: WebDavSourceDraft,
    sourceId: String,
    logger: DiagnosticLogger,
): ImportScanReport {
    val rootUrl = normalizeWebDavRootUrl(draft.rootUrl)
    val authEnabled = draft.username.isNotBlank()
    val tracks = mutableListOf<ImportedTrackCandidate>()
    val failures = mutableListOf<ImportScanFailure>()
    val startedAt = System.currentTimeMillis()
    logger.info(WEBDAV_LOG_TAG) {
        "scan-start source=$sourceId rootUrl=$rootUrl auth=$authEnabled insecureTls=${draft.allowInsecureTls} client=sardine"
    }
    return runCatching {
        val sardine = buildJvmSardine(
            rootUrl = rootUrl,
            username = draft.username,
            password = draft.password,
            allowInsecureTls = draft.allowInsecureTls,
        )
        try {
            val discoveredAudioFileCount = collectJvmWebDavTracks(
                sardine = sardine,
                rootUrl = rootUrl,
                relativeDirectory = "",
                sourceId = sourceId,
                username = draft.username,
                password = draft.password,
                allowInsecureTls = draft.allowInsecureTls,
                authEnabled = authEnabled,
                logger = logger,
                sink = tracks,
                failures = failures,
            )
            ImportScanReport(
                tracks = tracks,
                discoveredAudioFileCount = discoveredAudioFileCount,
                failures = failures,
            )
        } finally {
            sardine.shutdownQuietly()
        }
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

internal fun testJvmWebDavConnection(
    draft: WebDavSourceDraft,
    logger: DiagnosticLogger,
) {
    val rootUrl = normalizeWebDavRootUrl(draft.rootUrl)
    val authEnabled = draft.username.isNotBlank()
    val sardine = buildJvmSardine(
        rootUrl = rootUrl,
        username = draft.username,
        password = draft.password,
        allowInsecureTls = draft.allowInsecureTls,
    )
    try {
        logger.debug(WEBDAV_LOG_TAG) {
            "test-connection rootUrl=$rootUrl auth=$authEnabled insecureTls=${draft.allowInsecureTls} client=sardine"
        }
        val resource = sardine.list(rootUrl.ensureTrailingSlash(), 0)
            .firstOrNull()
            ?: error("WebDAV 根目录不可访问。")
        if (!resource.isDirectory) {
            error("WebDAV 根 URL 不是目录。")
        }
    } catch (throwable: Throwable) {
        throw throwable.asJvmWebDavIOException("测试连接", authEnabled)
    } finally {
        sardine.shutdownQuietly()
    }
}

internal suspend fun resolveJvmWebDavPlaybackTarget(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
    logger: DiagnosticLogger,
): JvmWebDavPlaybackTarget? {
    val webDav = parseWebDavLocator(locator) ?: return null
    val source = database.importSourceDao().getById(webDav.first)?.takeIf { it.enabled } ?: return null
    val password = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
    val requestUrl = buildWebDavTrackUrl(source.rootReference, webDav.second)
    val authEnabled = source.username.orEmpty().isNotBlank()
    val mediaSize = resolveJvmWebDavContentLength(
        requestUrl = requestUrl,
        username = source.username.orEmpty(),
        password = password,
        allowInsecureTls = source.allowInsecureTls,
    )
    logger.info(WEBDAV_LOG_TAG) {
        "play-start source=${webDav.first} url=$requestUrl auth=$authEnabled insecureTls=${source.allowInsecureTls} size=$mediaSize client=http-range"
    }
    return JvmWebDavPlaybackTarget(
        media = object : SeekableCallbackMedia() {
            private var currentStream: JvmWebDavOpenedStream? = null

            override fun onGetSize(): Long = mediaSize.coerceAtLeast(0L)

            override fun onOpen(): Boolean {
                currentStream = openJvmWebDavStream(
                    requestUrl = requestUrl,
                    username = source.username.orEmpty(),
                    password = password,
                    allowInsecureTls = source.allowInsecureTls,
                    startByte = 0L,
                )
                return true
            }

            override fun onRead(buffer: ByteArray, bufferSize: Int): Int {
                val stream = currentStream ?: return -1
                return stream.inputStream.read(buffer, 0, bufferSize)
            }

            override fun onSeek(offset: Long): Boolean {
                if (offset < 0L) return false
                return runCatching {
                    closeJvmWebDavStream(currentStream)
                    currentStream = openJvmWebDavStream(
                        requestUrl = requestUrl,
                        username = source.username.orEmpty(),
                        password = password,
                        allowInsecureTls = source.allowInsecureTls,
                        startByte = offset,
                    )
                    true
                }.getOrElse { throwable ->
                    logger.warn(WEBDAV_LOG_TAG) {
                        "play-seek-failed source=${webDav.first} url=$requestUrl offset=$offset reason=${throwable.message.orEmpty()}"
                    }
                    false
                }
            }

            override fun onClose() {
                closeJvmWebDavStream(currentStream)
                currentStream = null
            }
        },
        requestUrl = requestUrl,
    )
}

internal suspend fun requestJvmWebDavMetadata(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
    logger: DiagnosticLogger,
): ImportedTrackCandidate? {
    val webDav = parseWebDavLocator(locator) ?: return null
    val source = database.importSourceDao().getById(webDav.first)?.takeIf { it.enabled } ?: return null
    val password = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
    val requestUrl = buildWebDavTrackUrl(source.rootReference, webDav.second)
    val extension = webDav.second.substringAfterLast('.', "").ifBlank { "bin" }
    val tempFile = File.createTempFile("lynmusic-webdav-meta-", ".$extension")
    return runCatching {
        var bytesRead = downloadJvmWebDavHead(
            requestUrl = requestUrl,
            username = source.username.orEmpty(),
            password = password,
            allowInsecureTls = source.allowInsecureTls,
            rangeBytes = WEBDAV_METADATA_RANGE_BYTES,
            target = tempFile,
        )
        val requiredBytes = JvmAudioTagReader.requiredRemoteSnippetBytes(tempFile.toPath(), webDav.second)
        if (requiredBytes != null && requiredBytes > bytesRead && requiredBytes <= WEBDAV_MAX_METADATA_RANGE_BYTES) {
            bytesRead = downloadJvmWebDavHead(
                requestUrl = requestUrl,
                username = source.username.orEmpty(),
                password = password,
                allowInsecureTls = source.allowInsecureTls,
                rangeBytes = requiredBytes,
                target = tempFile,
            )
            logger.debug(WEBDAV_LOG_TAG) {
                "metadata-range-expand source=${webDav.first} url=$requestUrl bytes=$bytesRead"
            }
        }
        logger.debug(WEBDAV_LOG_TAG) {
            "metadata-range-read source=${webDav.first} url=$requestUrl bytes=$bytesRead"
        }
        val candidate = JvmAudioTagReader.readRemoteSnippet(tempFile.toPath(), webDav.second, logger)
        candidate.takeIf { it.hasMeaningfulMetadata(webDav.second) }
    }.onSuccess { candidate ->
        logger.info(WEBDAV_LOG_TAG) {
            if (candidate == null) {
                "metadata-miss source=${webDav.first} url=$requestUrl"
            } else {
                "metadata-hit source=${webDav.first} url=$requestUrl title=${candidate.title} artist=${candidate.artistName.orEmpty()} album=${candidate.albumTitle.orEmpty()}"
            }
        }
    }.onFailure { throwable ->
        logger.warn(WEBDAV_LOG_TAG) {
            "metadata-failed source=${webDav.first} url=$requestUrl reason=${throwable.message.orEmpty()}"
        }
    }.getOrNull().also {
        tempFile.delete()
    }
}

internal suspend fun readJvmWebDavSameNameLyrics(
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
    val authEnabled = source.username.orEmpty().isNotBlank()
    val bytes = downloadJvmWebDavOptionalFile(
        requestUrl = requestUrl,
        username = source.username.orEmpty(),
        password = password,
        allowInsecureTls = source.allowInsecureTls,
        authEnabled = authEnabled,
        operation = "读取同名歌词",
    ) ?: return null
    if (bytes.isEmpty() || bytes.size > SAME_NAME_LRC_MAX_BYTES) return null
    logger.debug(WEBDAV_LOG_TAG) {
        "same-name-lrc-read source=${webDav.first} url=$requestUrl bytes=${bytes.size}"
    }
    return decodeJvmSameNameLyricsBytes(bytes)
}

private fun collectJvmWebDavTracks(
    sardine: Sardine,
    rootUrl: String,
    relativeDirectory: String,
    sourceId: String,
    username: String,
    password: String,
    allowInsecureTls: Boolean,
    authEnabled: Boolean,
    logger: DiagnosticLogger,
    sink: MutableList<ImportedTrackCandidate>,
    failures: MutableList<ImportScanFailure>,
): Int {
    var discoveredAudioFileCount = 0
    val directoryUrl = if (relativeDirectory.isBlank()) {
        rootUrl
    } else {
        buildWebDavTrackUrl(rootUrl, relativeDirectory).ensureTrailingSlash()
    }
    logger.debug(WEBDAV_LOG_TAG) {
        "propfind source=$sourceId url=$directoryUrl client=sardine"
    }
    val resources = try {
        sardine.list(directoryUrl, 1)
    } catch (throwable: Throwable) {
        throw throwable.asJvmWebDavIOException("扫描", authEnabled)
    }

    resources.forEach { resource ->
        val resolved = resolveWebDavListedResource(
            rootUrl = rootUrl,
            currentDirectory = relativeDirectory,
            resource = WebDavListedResource(
                href = resource.href.toString(),
                isDirectory = resource.isDirectory,
                name = resource.name,
                contentLength = resource.contentLength ?: 0L,
                modifiedAt = resource.modified?.time ?: 0L,
            ),
        ) ?: return@forEach
        if (resolved.isDirectory) {
            discoveredAudioFileCount += collectJvmWebDavTracks(
                sardine = sardine,
                rootUrl = rootUrl,
                relativeDirectory = resolved.relativePath,
                sourceId = sourceId,
                username = username,
                password = password,
                allowInsecureTls = allowInsecureTls,
                authEnabled = authEnabled,
                logger = logger,
                sink = sink,
                failures = failures,
            )
        } else if (isSupportedJvmWebDavAudio(resolved.fileName)) {
            discoveredAudioFileCount += 1
            val requestUrl = buildWebDavTrackUrl(rootUrl, resolved.relativePath)
            runCatching {
                resolveJvmWebDavScanCandidate(
                    sourceId = sourceId,
                    resource = resolved,
                    requestUrl = requestUrl,
                    username = username,
                    password = password,
                    allowInsecureTls = allowInsecureTls,
                    authEnabled = authEnabled,
                    logger = logger,
                )
            }.onFailure { throwable ->
                logger.warn(WEBDAV_LOG_TAG) {
                    "metadata-failed source=$sourceId url=$requestUrl reason=${throwable.message.orEmpty()}"
                }
            }.recoverCatching {
                buildWebDavImportedTrackCandidate(sourceId, resolved)
            }.onSuccess { candidate ->
                sink += candidate
            }.onFailure { throwable ->
                failures += ImportScanFailure(
                    relativePath = resolved.relativePath,
                    reason = scanFailureReason(throwable),
                )
            }
        }
    }
    return discoveredAudioFileCount
}

private fun resolveJvmWebDavScanCandidate(
    sourceId: String,
    resource: WebDavResolvedResource,
    requestUrl: String,
    username: String,
    password: String,
    allowInsecureTls: Boolean,
    authEnabled: Boolean,
    logger: DiagnosticLogger,
): ImportedTrackCandidate {
    val fallback = buildWebDavImportedTrackCandidate(sourceId, resource)
    if (resource.contentLength <= 0L) return fallback
    val metadata = readJvmWebDavRemoteMetadata(
        requestUrl = requestUrl,
        username = username,
        password = password,
        allowInsecureTls = allowInsecureTls,
        relativePath = resource.relativePath,
        sizeBytes = resource.contentLength,
        authEnabled = authEnabled,
        logger = logger,
        sourceId = sourceId,
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
        storeArtwork = { bytes -> storeJvmWebDavArtwork(resource.relativePath, bytes) },
    )
    logger.info(WEBDAV_LOG_TAG) {
        "metadata-hit source=$sourceId url=$requestUrl title=${candidate.title} artist=${candidate.artistName.orEmpty()} album=${candidate.albumTitle.orEmpty()}"
    }
    return candidate
}

private fun readJvmWebDavRemoteMetadata(
    requestUrl: String,
    username: String,
    password: String,
    allowInsecureTls: Boolean,
    relativePath: String,
    sizeBytes: Long,
    authEnabled: Boolean,
    logger: DiagnosticLogger,
    sourceId: String,
): RemoteAudioMetadata? {
    val initialHeadBytes = sizeBytes
        .coerceAtMost(RemoteAudioMetadataProbe.HEAD_PROBE_BYTES)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
    if (initialHeadBytes <= 0) return null
    var totalProbeBytes = initialHeadBytes.toLong()
    var headBytes = downloadJvmWebDavRange(
        requestUrl = requestUrl,
        username = username,
        password = password,
        allowInsecureTls = allowInsecureTls,
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
        headBytes = downloadJvmWebDavRange(
            requestUrl = requestUrl,
            username = username,
            password = password,
            allowInsecureTls = allowInsecureTls,
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
            downloadJvmWebDavRange(
                requestUrl = requestUrl,
                username = username,
                password = password,
                allowInsecureTls = allowInsecureTls,
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

private fun buildJvmSardine(
    rootUrl: String,
    username: String,
    password: String,
    allowInsecureTls: Boolean,
): Sardine {
    val sardine = if (allowInsecureTls) {
        val sslContext = SSLContexts.custom()
            .loadTrustMaterial(null) { _, _ -> true }
            .build()
        val builder = HttpClientBuilder.create()
            .setSSLSocketFactory(SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE))
        if (username.isBlank()) {
            SardineImpl(builder)
        } else {
            SardineImpl(builder, username, password)
        }
    } else if (username.isBlank()) {
        SardineFactory.begin()
    } else {
        SardineFactory.begin(username, password)
    }
    if (username.isNotBlank()) {
        sardine.enablePreemptiveAuthentication(URI(rootUrl).toURL())
    }
    return sardine
}

private fun Throwable.asJvmWebDavIOException(operation: String, authEnabled: Boolean): IOException {
    val sardineException = this as? SardineException
    if (sardineException != null) {
        return IOException(
            describeWebDavHttpFailure(
                operation = operation,
                statusCode = sardineException.statusCode,
                authSent = authEnabled,
                serverDetail = sardineException.responsePhrase,
            ),
            this,
        )
    }
    return if (this is IOException) this else IOException(
        "WebDAV $operation 失败: ${message ?: this::class.simpleName.orEmpty()}",
        this,
    )
}

private fun Sardine?.shutdownQuietly() {
    val instance = this ?: return
    runCatching { instance.shutdown() }
}

private data class JvmWebDavOpenedStream(
    val connection: HttpURLConnection,
    val inputStream: InputStream,
)

private fun closeJvmWebDavStream(stream: JvmWebDavOpenedStream?) {
    val activeStream = stream ?: return
    runCatching { activeStream.inputStream.close() }
    activeStream.connection.disconnect()
}

private fun resolveJvmWebDavContentLength(
    requestUrl: String,
    username: String,
    password: String,
    allowInsecureTls: Boolean,
): Long {
    val connection = openJvmWebDavConnection(
        requestUrl = requestUrl,
        username = username,
        password = password,
        allowInsecureTls = allowInsecureTls,
    )
    return try {
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Range", "bytes=0-0")
        connection.connect()
        when (val statusCode = connection.responseCode) {
            HttpURLConnection.HTTP_PARTIAL -> {
                parseContentRangeTotalLength(connection.getHeaderField("Content-Range"))
                    ?: connection.getHeaderFieldLong("Content-Length", 0L)
            }

            in 200..299 -> connection.getHeaderFieldLong("Content-Length", 0L)
            else -> throw IOException("WebDAV content length probe failed with HTTP $statusCode")
        }
    } finally {
        connection.inputStreamOrNull()?.close()
        connection.disconnect()
    }
}

private fun openJvmWebDavStream(
    requestUrl: String,
    username: String,
    password: String,
    allowInsecureTls: Boolean,
    startByte: Long,
): JvmWebDavOpenedStream {
    val connection = openJvmWebDavConnection(
        requestUrl = requestUrl,
        username = username,
        password = password,
        allowInsecureTls = allowInsecureTls,
    )
    return try {
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        if (startByte > 0L) {
            connection.setRequestProperty("Range", "bytes=$startByte-")
        }
        connection.connect()
        val statusCode = connection.responseCode
        if (statusCode !in 200..299) {
            throw IOException("WebDAV playback stream failed with HTTP $statusCode")
        }
        val inputStream = connection.inputStream
        if (startByte > 0L && statusCode == HttpURLConnection.HTTP_OK) {
            skipJvmWebDavBytes(inputStream, startByte)
        }
        JvmWebDavOpenedStream(connection, inputStream)
    } catch (throwable: Throwable) {
        connection.disconnect()
        throw throwable
    }
}

fun downloadJvmWebDavHead(
    requestUrl: String,
    username: String,
    password: String,
    allowInsecureTls: Boolean,
    rangeBytes: Long,
    target: File,
    ): Long {
    val bytes = downloadJvmWebDavRange(
        requestUrl = requestUrl,
        username = username,
        password = password,
        allowInsecureTls = allowInsecureTls,
        startByte = 0L,
        length = rangeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        authEnabled = username.isNotBlank(),
        operation = "标签探测",
    )
    target.outputStream().use { output ->
        output.write(bytes)
    }
    return target.length()
}

private fun downloadJvmWebDavRange(
    requestUrl: String,
    username: String,
    password: String,
    allowInsecureTls: Boolean,
    startByte: Long,
    length: Int,
    authEnabled: Boolean,
    operation: String,
    allowFullResponseFallback: Boolean = true,
): ByteArray {
    if (length <= 0) return ByteArray(0)
    val connection = openJvmWebDavConnection(
        requestUrl = requestUrl,
        username = username,
        password = password,
        allowInsecureTls = allowInsecureTls,
    )
    return try {
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty(
            "Range",
            buildWebDavRangeHeader(
                position = startByte,
                requestedLength = length.toLong(),
            ) ?: "bytes=0-${length - 1}",
        )
        connection.connect()
        val statusCode = connection.responseCode
        if (statusCode !in 200..299) {
            throw IOException(
                describeWebDavHttpFailure(
                    operation = operation,
                    statusCode = statusCode,
                    authSent = authEnabled,
                    serverDetail = connection.getHeaderField("WWW-Authenticate").orEmpty().ifBlank { connection.responseMessage },
                ),
            )
        }
        if (startByte > 0L && statusCode == HttpURLConnection.HTTP_OK && !allowFullResponseFallback) {
            return ByteArray(0)
        }
        connection.inputStream.use { input ->
            if (startByte > 0L && statusCode == HttpURLConnection.HTTP_OK) {
                skipJvmWebDavBytes(input, startByte)
            }
            readJvmWebDavBytes(input, length)
        }
    } finally {
        connection.disconnect()
    }
}

private fun downloadJvmWebDavOptionalFile(
    requestUrl: String,
    username: String,
    password: String,
    allowInsecureTls: Boolean,
    authEnabled: Boolean,
    operation: String,
): ByteArray? {
    val requestedLength = (SAME_NAME_LRC_MAX_BYTES + 1L).toInt()
    val connection = openJvmWebDavConnection(
        requestUrl = requestUrl,
        username = username,
        password = password,
        allowInsecureTls = allowInsecureTls,
    )
    return try {
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Range", "bytes=0-${requestedLength - 1}")
        connection.connect()
        when (val statusCode = connection.responseCode) {
            HttpURLConnection.HTTP_NOT_FOUND -> null
            in 200..299 -> connection.inputStream.use { input ->
                readJvmWebDavBytes(input, requestedLength)
            }

            else -> throw IOException(
                describeWebDavHttpFailure(
                    operation = operation,
                    statusCode = statusCode,
                    authSent = authEnabled,
                    serverDetail = connection.getHeaderField("WWW-Authenticate").orEmpty().ifBlank { connection.responseMessage },
                ),
            )
        }
    } finally {
        connection.inputStreamOrNull()?.close()
        connection.disconnect()
    }
}

private fun parseContentRangeTotalLength(headerValue: String?): Long? {
    val value = headerValue?.trim().orEmpty()
    if (value.isEmpty()) return null
    return CONTENT_RANGE_TOTAL_PATTERN.matchEntire(value)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
}

private fun skipJvmWebDavBytes(inputStream: InputStream, bytesToSkip: Long) {
    var remaining = bytesToSkip
    while (remaining > 0L) {
        val skipped = inputStream.skip(remaining)
        if (skipped > 0L) {
            remaining -= skipped
            continue
        }
        val nextByte = inputStream.read()
        if (nextByte == -1) {
            throw IOException("WebDAV stream ended before reaching requested offset $bytesToSkip")
        }
        remaining -= 1L
    }
}

private fun readJvmWebDavBytes(inputStream: InputStream, length: Int): ByteArray {
    val buffer = ByteArray(length)
    var totalRead = 0
    while (totalRead < length) {
        val read = inputStream.read(buffer, totalRead, length - totalRead)
        if (read <= 0) break
        totalRead += read
    }
    return if (totalRead == buffer.size) buffer else buffer.copyOf(totalRead)
}

private fun storeJvmWebDavArtwork(relativePath: String, bytes: ByteArray): String? {
    if (bytes.isEmpty()) return null
    val fileName = buildString {
        append(relativePath.hashCode().toUInt().toString(16))
        append('-')
        append(bytes.contentHashCode().toUInt().toString(16))
        append(inferArtworkFileExtension(bytes = bytes))
    }
    val target = File(jvmWebDavArtworkDirectory, fileName)
    if (!target.exists() || target.length() != bytes.size.toLong()) {
        target.writeBytes(bytes)
    }
    return target.absolutePath
}

private fun openJvmWebDavConnection(
    requestUrl: String,
    username: String,
    password: String,
    allowInsecureTls: Boolean,
): HttpURLConnection {
    val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
        buildBasicAuthorizationHeader(username, password)?.let { header ->
            setRequestProperty("Authorization", header)
        }
    }
    if (allowInsecureTls && connection is HttpsURLConnection) {
        connection.sslSocketFactory = insecureSslContext.socketFactory
        connection.hostnameVerifier = insecureHostnameVerifier
    }
    return connection
}

private fun HttpURLConnection.inputStreamOrNull(): InputStream? {
    return runCatching { inputStream }.getOrNull() ?: runCatching { errorStream }.getOrNull()
}

private fun ImportedTrackCandidate.hasMeaningfulMetadata(relativePath: String): Boolean {
    val fallbackTitle = relativePath.substringAfterLast('/').substringBeforeLast('.')
    return title != fallbackTitle ||
        !artistName.isNullOrBlank() ||
        !albumTitle.isNullOrBlank() ||
        durationMs > 0L ||
        trackNumber != null ||
        discNumber != null ||
        artworkLocator != null ||
        !embeddedLyrics.isNullOrBlank()
}

private val insecureSslContext: SSLContext by lazy {
    SSLContext.getInstance("TLS").apply {
        init(
            null,
            arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                },
            ),
            SecureRandom(),
        )
    }
}

private val insecureHostnameVerifier: HostnameVerifier = HostnameVerifier { _, _ -> true }

private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"

private fun isSupportedJvmWebDavAudio(fileName: String): Boolean {
    return fileName.substringAfterLast('.', "").lowercase() in setOf("mp3", "m4a", "aac", "wav", "flac", "ape")
}

private fun scanFailureReason(throwable: Throwable): String {
    return throwable.message?.takeIf { it.isNotBlank() }
        ?: throwable::class.simpleName
        ?: "读取失败。"
}

private val jvmWebDavArtworkDirectory = File(File(System.getProperty("user.home")), ".lynmusic/artwork").apply {
    mkdirs()
}

private const val WEBDAV_LOG_TAG = "WebDav"
const val WEBDAV_METADATA_RANGE_BYTES = 262_144L
private const val WEBDAV_MAX_METADATA_RANGE_BYTES = 2_097_152L
private val CONTENT_RANGE_TOTAL_PATTERN = Regex("""bytes\s+\d+-\d+/(\d+)""")
