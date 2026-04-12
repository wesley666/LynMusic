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
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File as SmbFile
import java.io.IOException
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.parseSambaLocator
import top.iwesley.lyn.music.data.db.LynMusicDatabase

internal data class AndroidSambaPlaybackTarget(
    val mediaSource: MediaSource,
    val sourceReference: String,
)

private data class AndroidSambaPlaybackContext(
    val sourceId: String,
    val endpoint: String,
    val shareName: String,
    val remotePath: String,
    val server: String,
    val port: Int,
    val username: String,
    val password: String,
)

private data class AndroidSambaOpenedStream(
    val client: SMBClient,
    val connection: Connection,
    val session: Session,
    val share: DiskShare,
    val file: SmbFile,
    val totalSize: Long,
)

internal suspend fun resolveAndroidSambaPlaybackTarget(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    track: Track,
    logger: DiagnosticLogger,
): AndroidSambaPlaybackTarget? {
    val samba = parseSambaLocator(track.mediaLocator) ?: return null
    return runCatching {
        val source = database.importSourceDao().getById(samba.first)?.takeIf { it.enabled }
            ?: error("SMB 来源不可用。")
        val spec = resolveSambaSourceSpec(
            source = source,
            locatorRelativePath = samba.second,
            fallbackRelativePath = track.relativePath.ifBlank { samba.second },
        )
        val password = spec.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
        val context = AndroidSambaPlaybackContext(
            sourceId = spec.sourceId,
            endpoint = spec.endpoint,
            shareName = spec.shareName,
            remotePath = spec.remotePath,
            server = spec.server,
            port = spec.port,
            username = spec.username,
            password = password,
        )
        AndroidSambaPlaybackTarget(
            mediaSource = ProgressiveMediaSource.Factory(
                AndroidSambaDataSourceFactory(
                    context = context,
                    logger = logger,
                ),
            ).createMediaSource(MediaItem.fromUri(Uri.parse(track.mediaLocator))),
            sourceReference = buildAndroidSambaSourceReference(
                endpoint = spec.endpoint,
                shareName = spec.shareName,
                remotePath = spec.remotePath,
            ),
        )
    }.getOrElse { throwable ->
        throw IllegalStateException(
            "Samba 直连播放失败: ${throwable.message ?: "未知错误"}",
            throwable,
        )
    }
}

@UnstableApi
private class AndroidSambaDataSourceFactory(
    private val context: AndroidSambaPlaybackContext,
    private val logger: DiagnosticLogger,
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return AndroidSambaDataSource(context, logger)
    }
}

@UnstableApi
private class AndroidSambaDataSource(
    private val context: AndroidSambaPlaybackContext,
    private val logger: DiagnosticLogger,
) : BaseDataSource(true) {
    private var activeStream: AndroidSambaOpenedStream? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var currentOffset: Long = 0L
    private var currentUri: Uri? = null
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        closeStream()
        val requestedPosition = dataSpec.position.coerceAtLeast(0L)
        return runCatching {
            val stream = openAndroidSambaStream(context)
            if (requestedPosition > stream.totalSize) {
                throw IOException("请求位置超出文件大小: $requestedPosition > ${stream.totalSize}")
            }
            activeStream = stream
            currentOffset = requestedPosition
            currentUri = dataSpec.uri
            bytesRemaining = when {
                dataSpec.length != C.LENGTH_UNSET.toLong() -> minOf(
                    dataSpec.length,
                    stream.totalSize - requestedPosition,
                )
                else -> stream.totalSize - requestedPosition
            }
            logger.info(SAMBA_LOG_TAG) {
                "direct-open source=${context.sourceId} endpoint=${context.endpoint} share=${context.shareName} " +
                    "remotePath=${context.remotePath} position=$requestedPosition length=${dataSpec.length}"
            }
            opened = true
            transferStarted(dataSpec)
            bytesRemaining
        }.onFailure { throwable ->
            logger.error(SAMBA_LOG_TAG, throwable) {
                "direct-failed stage=open source=${context.sourceId} endpoint=${context.endpoint} " +
                    "share=${context.shareName} remotePath=${context.remotePath} position=$requestedPosition"
            }
            closeStream()
        }.getOrElse { throwable ->
            throw throwable.asAndroidSambaPlaybackIOException("打开", context)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val stream = activeStream ?: return C.RESULT_END_OF_INPUT
        val bytesToRead = when {
            bytesRemaining == C.LENGTH_UNSET.toLong() -> length
            else -> minOf(length.toLong(), bytesRemaining).toInt()
        }
        return try {
            val bytesRead = stream.file.read(buffer, currentOffset, offset, bytesToRead)
            if (bytesRead <= 0) {
                C.RESULT_END_OF_INPUT
            } else {
                logger.debug(SAMBA_LOG_TAG) {
                    "direct-read source=${context.sourceId} endpoint=${context.endpoint} share=${context.shareName} " +
                        "remotePath=${context.remotePath} offset=$currentOffset bytes=$bytesRead"
                }
                currentOffset += bytesRead.toLong()
                if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                    bytesRemaining -= bytesRead.toLong()
                }
                bytesTransferred(bytesRead)
                bytesRead
            }
        } catch (throwable: Throwable) {
            logger.error(SAMBA_LOG_TAG, throwable) {
                "direct-failed stage=read source=${context.sourceId} endpoint=${context.endpoint} " +
                    "share=${context.shareName} remotePath=${context.remotePath} offset=$currentOffset"
            }
            throw throwable.asAndroidSambaPlaybackIOException("读取", context)
        }
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        logger.debug(SAMBA_LOG_TAG) {
            "direct-close source=${context.sourceId} endpoint=${context.endpoint} share=${context.shareName} " +
                "remotePath=${context.remotePath} offset=$currentOffset"
        }
        closeStream()
        currentUri = null
        bytesRemaining = C.LENGTH_UNSET.toLong()
        currentOffset = 0L
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun closeStream() {
        val stream = activeStream ?: return
        activeStream = null
        runCatching { stream.file.close() }
        runCatching { stream.share.close() }
        runCatching { stream.session.close() }
        runCatching { stream.connection.close() }
        runCatching { stream.client.close() }
    }
}

private fun openAndroidSambaStream(
    context: AndroidSambaPlaybackContext,
): AndroidSambaOpenedStream {
    val client = SMBClient()
    return try {
        val connection = client.connect(context.server, context.port)
        val session = connection.authenticate(
            AuthenticationContext(context.username, context.password.toCharArray(), ""),
        )
        val share = session.connectShare(context.shareName) as DiskShare
        val totalSize = share.getFileInformation(context.remotePath)
            .standardInformation
            .endOfFile
        val file = share.openFile(
            context.remotePath,
            setOf(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        )
        AndroidSambaOpenedStream(
            client = client,
            connection = connection,
            session = session,
            share = share,
            file = file,
            totalSize = totalSize,
        )
    } catch (throwable: Throwable) {
        runCatching { client.close() }
        throw throwable
    }
}

private fun Throwable.asAndroidSambaPlaybackIOException(
    operation: String,
    context: AndroidSambaPlaybackContext,
): IOException {
    val detail = message?.takeIf { it.isNotBlank() }
        ?: this::class.simpleName
        ?: "未知错误"
    return IOException(
        "Samba 直连播放失败: ${operation}失败: $detail (${context.endpoint}/${context.remotePath})",
        this,
    )
}

private const val SAMBA_LOG_TAG = "Samba"
