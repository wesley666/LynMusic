package top.iwesley.lyn.music.platform

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
import top.iwesley.lyn.music.core.model.DEFAULT_SAMBA_PORT
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.formatSambaEndpoint
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.joinSambaPath
import top.iwesley.lyn.music.core.model.normalizeSambaPath
import top.iwesley.lyn.music.core.model.parseSambaLocator
import top.iwesley.lyn.music.core.model.parseSambaPath
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import uk.co.caprica.vlcj.media.callback.CallbackMedia
import uk.co.caprica.vlcj.media.callback.seekable.SeekableCallbackMedia

internal data class JvmSambaPlaybackTarget(
    val media: CallbackMedia,
    val sourceReference: String,
)

internal fun shouldUseJvmSambaCallback(locator: String, useSambaCache: Boolean): Boolean {
    return !useSambaCache && parseSambaLocator(locator) != null
}

internal fun buildJvmSambaPlaybackTarget(trackId: String): String = "samba-callback://$trackId"

private data class JvmSambaSourceContext(
    val sourceId: String,
    val endpoint: String,
    val server: String,
    val port: Int,
    val shareName: String,
    val remotePath: String,
    val username: String,
    val password: String,
)

private data class JvmSambaOpenedStream(
    val client: SMBClient,
    val connection: Connection,
    val session: Session,
    val share: DiskShare,
    val file: SmbFile,
    var offset: Long,
)

internal suspend fun resolveJvmSambaPlaybackTarget(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: String,
    logger: DiagnosticLogger,
): JvmSambaPlaybackTarget? {
    val samba = parseSambaLocator(locator) ?: return null
    val context = resolveJvmSambaSourceContext(
        database = database,
        secureCredentialStore = secureCredentialStore,
        locator = samba,
    )
    val size = resolveJvmSambaFileSize(context)
    logger.info(SAMBA_LOG_TAG) {
        "callback-size source=${context.sourceId} endpoint=${context.endpoint} share=${context.shareName} remotePath=${context.remotePath} size=$size"
    }
    return JvmSambaPlaybackTarget(
        media = object : SeekableCallbackMedia() {
            private var currentStream: JvmSambaOpenedStream? = null

            override fun onGetSize(): Long = size.coerceAtLeast(0L)

            override fun onOpen(): Boolean {
                return runCatching {
                    currentStream = openJvmSambaStream(context, 0L)
                    logger.debug(SAMBA_LOG_TAG) {
                        "callback-open source=${context.sourceId} endpoint=${context.endpoint} share=${context.shareName} remotePath=${context.remotePath} offset=0"
                    }
                    true
                }.onFailure { throwable ->
                    logger.error(SAMBA_LOG_TAG, throwable) {
                        "callback-failed stage=open source=${context.sourceId} endpoint=${context.endpoint} share=${context.shareName} remotePath=${context.remotePath}"
                    }
                }.getOrDefault(false)
            }

            override fun onRead(buffer: ByteArray, bufferSize: Int): Int {
                val stream = currentStream ?: return -1
                return try {
                    val read = stream.file.read(buffer, stream.offset, 0, bufferSize)
                    if (read > 0) {
                        logger.debug(SAMBA_LOG_TAG) {
                            "callback-read source=${context.sourceId} endpoint=${context.endpoint} share=${context.shareName} remotePath=${context.remotePath} offset=${stream.offset} bytes=$read"
                        }
                        stream.offset += read.toLong()
                    }
                    read
                } catch (throwable: Throwable) {
                    logger.error(SAMBA_LOG_TAG, throwable) {
                        "callback-failed stage=read source=${context.sourceId} endpoint=${context.endpoint} share=${context.shareName} remotePath=${context.remotePath} offset=${stream.offset}"
                    }
                    throw throwable.asJvmSambaIOException("读取")
                }
            }

            override fun onSeek(offset: Long): Boolean {
                if (offset < 0L) return false
                return runCatching {
                    closeJvmSambaStream(currentStream, logger, context, reason = "seek-reopen")
                    currentStream = openJvmSambaStream(context, offset)
                    logger.debug(SAMBA_LOG_TAG) {
                        "callback-seek source=${context.sourceId} endpoint=${context.endpoint} share=${context.shareName} remotePath=${context.remotePath} offset=$offset"
                    }
                    true
                }.onFailure { throwable ->
                    logger.error(SAMBA_LOG_TAG, throwable) {
                        "callback-failed stage=seek source=${context.sourceId} endpoint=${context.endpoint} share=${context.shareName} remotePath=${context.remotePath} offset=$offset"
                    }
                }.getOrDefault(false)
            }

            override fun onClose() {
                closeJvmSambaStream(currentStream, logger, context, reason = "close")
                currentStream = null
            }
        },
        sourceReference = buildJvmSambaSourceReference(
            endpoint = context.endpoint,
            shareName = context.shareName,
            remotePath = context.remotePath,
        ),
    )
}

private suspend fun resolveJvmSambaSourceContext(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    locator: Pair<String, String>,
): JvmSambaSourceContext {
    val source = database.importSourceDao().getById(locator.first)?.takeIf { it.enabled }
        ?: error("Samba 来源不可用。")
    val shareName = source.shareName
    val storedPort = shareName?.toIntOrNull()
    val storedPath = when {
        storedPort != null -> normalizeSambaPath(source.directoryPath)
        shareName.isNullOrBlank() -> normalizeSambaPath(source.directoryPath)
        else -> normalizeSambaPath(joinSambaPath(shareName, source.directoryPath.orEmpty()))
    }
    val sambaPath = parseSambaPath(storedPath)
        ?: error("SMB source path is missing a share name.")
    val endpoint = formatSambaEndpoint(source.server.orEmpty(), storedPort, storedPath)
    val password = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
    val username = source.username.orEmpty()
    val remotePath = joinSambaPath(sambaPath.directoryPath, locator.second)
    return JvmSambaSourceContext(
        sourceId = locator.first,
        endpoint = endpoint,
        server = source.server.orEmpty(),
        port = storedPort ?: DEFAULT_SAMBA_PORT,
        shareName = sambaPath.shareName,
        remotePath = remotePath,
        username = username,
        password = password,
    )
}

private fun resolveJvmSambaFileSize(context: JvmSambaSourceContext): Long {
    val client = SMBClient()
    return try {
        client.connect(context.server, context.port).use { connection ->
            val session = connection.authenticate(
                AuthenticationContext(context.username, context.password.toCharArray(), ""),
            )
            session.use {
                val share = session.connectShare(context.shareName) as DiskShare
                share.use {
                    share.getFileInformation(context.remotePath)
                        .standardInformation
                        .endOfFile
                }
            }
        }
    } catch (throwable: Throwable) {
        throw throwable.asJvmSambaIOException("探测大小")
    } finally {
        runCatching { client.close() }
    }
}

private fun openJvmSambaStream(
    context: JvmSambaSourceContext,
    offset: Long,
): JvmSambaOpenedStream {
    val client = SMBClient()
    return try {
        val connection = client.connect(context.server, context.port)
        val session = connection.authenticate(
            AuthenticationContext(context.username, context.password.toCharArray(), ""),
        )
        val share = session.connectShare(context.shareName) as DiskShare
        val file = share.openFile(
            context.remotePath,
            setOf(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        )
        JvmSambaOpenedStream(
            client = client,
            connection = connection,
            session = session,
            share = share,
            file = file,
            offset = offset,
        )
    } catch (throwable: Throwable) {
        runCatching { client.close() }
        throw throwable.asJvmSambaIOException("打开")
    }
}

private fun closeJvmSambaStream(
    stream: JvmSambaOpenedStream?,
    logger: DiagnosticLogger,
    context: JvmSambaSourceContext,
    reason: String,
) {
    val activeStream = stream ?: return
    logger.debug(SAMBA_LOG_TAG) {
        "callback-close source=${context.sourceId} endpoint=${context.endpoint} share=${context.shareName} remotePath=${context.remotePath} reason=$reason offset=${activeStream.offset}"
    }
    runCatching { activeStream.file.close() }
    runCatching { activeStream.share.close() }
    runCatching { activeStream.session.close() }
    runCatching { activeStream.connection.close() }
    runCatching { activeStream.client.close() }
}

internal fun buildJvmSambaSourceReference(
    endpoint: String,
    shareName: String,
    remotePath: String,
): String {
    return "endpoint=$endpoint share=$shareName remotePath=$remotePath"
}

private fun Throwable.asJvmSambaIOException(operation: String): IOException {
    val detail = message?.takeIf { it.isNotBlank() }
        ?: this::class.simpleName
        ?: "未知错误"
    return IOException("Samba $operation 失败：$detail", this)
}
