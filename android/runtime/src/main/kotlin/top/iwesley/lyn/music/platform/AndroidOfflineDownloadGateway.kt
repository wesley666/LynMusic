package top.iwesley.lyn.music.platform

import android.content.Context
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.io.File
import java.io.InputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.time.Clock
import top.iwesley.lyn.music.core.model.DEFAULT_SAMBA_PORT
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.OfflineDownloadGateway
import top.iwesley.lyn.music.core.model.OfflineDownloadProgress
import top.iwesley.lyn.music.core.model.OfflineDownloadResult
import top.iwesley.lyn.music.core.model.OfflineDownloadStatus
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildBasicAuthorizationHeader
import top.iwesley.lyn.music.core.model.buildWebDavTrackUrl
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.parseNavidromeSongLocator
import top.iwesley.lyn.music.core.model.parseSambaLocator
import top.iwesley.lyn.music.core.model.parseWebDavLocator
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.domain.resolveNavidromeDownloadUrl
import top.iwesley.lyn.music.domain.resolveNavidromeStreamUrl

fun createAndroidOfflineDownloadGateway(
    context: Context,
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    logger: DiagnosticLogger,
): OfflineDownloadGateway = AndroidOfflineDownloadGateway(
    context = context.applicationContext,
    database = database,
    secureCredentialStore = secureCredentialStore,
    logger = logger,
)

private class AndroidOfflineDownloadGateway(
    context: Context,
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val logger: DiagnosticLogger,
) : OfflineDownloadGateway {
    private val rootDirectory = File(context.filesDir, "offline")
    private val defaultClient = OkHttpClient()
    private val insecureClient by lazy {
        OkHttpClient.Builder()
            .sslSocketFactory(trustAllSslContext.socketFactory, trustAllManager)
            .hostnameVerifier(trustAllHostnameVerifier)
            .build()
    }

    override suspend fun download(
        track: Track,
        quality: NavidromeAudioQuality,
        onProgress: suspend (OfflineDownloadProgress) -> Unit,
    ): OfflineDownloadResult = withContext(Dispatchers.IO) {
        rootDirectory.mkdirs()
        val finalFile = File(rootDirectory, offlineFileName(track, quality))
        val partFile = File(rootDirectory, "${finalFile.name}.part")
        partFile.delete()
        try {
            val totalBytes = when {
                parseNavidromeSongLocator(track.mediaLocator) != null -> {
                    val requestUrl = if (quality == NavidromeAudioQuality.Original) {
                        resolveNavidromeDownloadUrl(database, secureCredentialStore, track.mediaLocator)
                    } else {
                        resolveNavidromeStreamUrl(database, secureCredentialStore, track.mediaLocator, quality)
                    } ?: error("Navidrome 来源不可用。")
                    downloadHttpFile(
                        requestUrl = requestUrl,
                        authorizationHeader = null,
                        allowInsecureTls = false,
                        target = partFile,
                        onProgress = onProgress,
                    )
                }

                parseWebDavLocator(track.mediaLocator) != null -> downloadWebDav(track, partFile, onProgress)
                parseSambaLocator(track.mediaLocator) != null -> downloadSamba(track, partFile, onProgress)
                else -> error("本地音乐不需要离线下载。")
            }
            require(partFile.length() > 0L) { "下载文件为空。" }
            if (finalFile.exists()) {
                finalFile.delete()
            }
            check(partFile.renameTo(finalFile)) { "离线文件写入失败。" }
            logger.info(OFFLINE_LOG_TAG) {
                "download-complete track=${track.id} path=${finalFile.absolutePath} size=${finalFile.length()}"
            }
            OfflineDownloadResult(
                localMediaLocator = finalFile.absolutePath,
                sizeBytes = finalFile.length(),
                totalBytes = totalBytes ?: finalFile.length(),
            )
        } catch (throwable: Throwable) {
            partFile.delete()
            throw throwable
        }
    }

    override suspend fun delete(localMediaLocator: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            File(localMediaLocator).takeIf { it.exists() }?.delete()
            Unit
        }
    }

    override suspend fun exists(localMediaLocator: String): Boolean = withContext(Dispatchers.IO) {
        File(localMediaLocator).isFile
    }

    override suspend fun clearAll(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            clearDirectory(rootDirectory)
            rootDirectory.mkdirs()
            Unit
        }
    }

    override suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        directorySizeBytes(rootDirectory)
    }

    override suspend fun cleanupPartialFiles(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            rootDirectory.listFiles().orEmpty()
                .filter { it.isFile && it.name.endsWith(".part") }
                .forEach { it.delete() }
        }
    }

    private suspend fun downloadWebDav(
        track: Track,
        target: File,
        onProgress: suspend (OfflineDownloadProgress) -> Unit,
    ): Long? {
        val webDav = parseWebDavLocator(track.mediaLocator) ?: return null
        val source = database.importSourceDao().getById(webDav.first)?.takeIf { it.enabled }
            ?: error("WebDAV 来源不可用。")
        val password = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
        return downloadHttpFile(
            requestUrl = buildWebDavTrackUrl(source.rootReference, webDav.second),
            authorizationHeader = buildBasicAuthorizationHeader(source.username.orEmpty(), password),
            allowInsecureTls = source.allowInsecureTls,
            target = target,
            onProgress = onProgress,
        )
    }

    private suspend fun downloadSamba(
        track: Track,
        target: File,
        onProgress: suspend (OfflineDownloadProgress) -> Unit,
    ): Long? {
        val samba = parseSambaLocator(track.mediaLocator) ?: return null
        val source = database.importSourceDao().getById(samba.first)?.takeIf { it.enabled }
            ?: error("Samba 来源不可用。")
        val spec = resolveSambaSourceSpec(source, samba.second)
        val password = spec.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
        var downloadedBytes = 0L
        SMBClient().use { client ->
            client.connect(spec.server, spec.port.takeIf { it > 0 } ?: DEFAULT_SAMBA_PORT).use { connection ->
                val session = connection.authenticate(
                    AuthenticationContext(spec.username, password.toCharArray(), ""),
                )
                val share = session.connectShare(spec.shareName) as DiskShare
                share.openFile(
                    spec.remotePath,
                    setOf(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null,
                ).use { smbFile ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var offset = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = smbFile.read(buffer, offset)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            offset += read
                            downloadedBytes += read
                            onProgress(OfflineDownloadProgress(downloadedBytes = downloadedBytes))
                        }
                    }
                }
            }
        }
        return null
    }

    private suspend fun downloadHttpFile(
        requestUrl: String,
        authorizationHeader: String?,
        allowInsecureTls: Boolean,
        target: File,
        onProgress: suspend (OfflineDownloadProgress) -> Unit,
    ): Long? {
        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .apply {
                authorizationHeader?.let { header("Authorization", it) }
            }
            .build()
        val response = (if (allowInsecureTls) insecureClient else defaultClient).newCall(request).execute()
        response.use { activeResponse ->
            if (!activeResponse.isSuccessful) {
                error("下载失败，HTTP ${activeResponse.code}。")
            }
            val totalBytes = activeResponse.body.contentLength().takeIf { it > 0L }
            activeResponse.body.byteStream().use { input ->
                writeStream(input, target, totalBytes, onProgress)
            }
            return totalBytes
        }
    }

    private suspend fun writeStream(
        input: InputStream,
        target: File,
        totalBytes: Long?,
        onProgress: suspend (OfflineDownloadProgress) -> Unit,
    ) {
        target.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var downloadedBytes = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                downloadedBytes += read
                onProgress(OfflineDownloadProgress(downloadedBytes = downloadedBytes, totalBytes = totalBytes))
            }
        }
    }
}

internal suspend fun resolveAndroidOfflinePlaybackFile(
    database: LynMusicDatabase,
    track: Track,
): File? {
    val row = database.offlineDownloadDao().getByTrackId(track.id) ?: return null
    val path = row.localMediaLocator?.takeIf { it.isNotBlank() } ?: return null
    val file = File(path)
    if (file.isFile) return file
    database.offlineDownloadDao().upsert(
        row.copy(
            localMediaLocator = null,
            status = OfflineDownloadStatus.Failed.name,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
            errorMessage = "离线文件不存在。",
        ),
    )
    return null
}

private fun offlineFileName(track: Track, quality: NavidromeAudioQuality): String {
    val extension = when {
        parseNavidromeSongLocator(track.mediaLocator) != null && quality != NavidromeAudioQuality.Original -> "mp3"
        else -> track.relativePath.substringAfterLast('.', "").lowercase().ifBlank { "audio" }
    }
    val key = "${track.id}-${quality.name}".hashCode().toUInt().toString(16)
    return "$key.$extension"
}

private fun directorySizeBytes(root: File): Long {
    if (!root.exists()) return 0L
    if (root.isFile) return root.length()
    return root.listFiles().orEmpty().sumOf(::directorySizeBytes)
}

private fun clearDirectory(root: File) {
    if (!root.exists()) return
    root.listFiles().orEmpty().forEach { file ->
        if (file.isDirectory) clearDirectory(file)
        file.delete()
    }
}

private val trustAllSslContext: SSLContext by lazy {
    SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
    }
}

private val trustAllManager = object : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private val trustAllHostnameVerifier = HostnameVerifier { _, _ -> true }
private const val OFFLINE_LOG_TAG = "OfflineDownload"
