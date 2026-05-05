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
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.parseSambaLocator
import top.iwesley.lyn.music.data.db.LynMusicDatabase

internal class SambaCastProxyResource private constructor(
    private val context: SambaCastProxyContext,
    override val mimeType: String,
    override val length: Long?,
) : AndroidCastProxyResource {
    override suspend fun open(start: Long, length: Long?): InputStream = withContext(Dispatchers.IO) {
        SambaRangeInputStream(
            stream = openSambaStream(context),
            start = start,
            requestedLength = length,
        )
    }

    private class SambaRangeInputStream(
        private val stream: SambaOpenedStream,
        start: Long,
        requestedLength: Long?,
    ) : InputStream() {
        private var offset = start
        private var remaining = requestedLength ?: Long.MAX_VALUE
        private var closed = false

        override fun read(): Int {
            val buffer = ByteArray(1)
            val read = read(buffer, 0, 1)
            return if (read < 0) -1 else buffer[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            if (closed || remaining <= 0L) return -1
            val requested = minOf(len.toLong(), remaining).toInt()
            if (requested <= 0) return -1
            val read = stream.file.read(buffer, offset, off, requested)
            if (read <= 0) return -1
            offset += read.toLong()
            remaining -= read.toLong()
            return read
        }

        override fun close() {
            if (closed) return
            closed = true
            stream.close()
        }
    }

    companion object {
        suspend fun create(
            database: LynMusicDatabase,
            secureCredentialStore: SecureCredentialStore,
            track: Track,
            mimeType: String,
            logger: DiagnosticLogger,
        ): SambaCastProxyResource? {
            val samba = parseSambaLocator(track.mediaLocator) ?: return null
            val source = database.importSourceDao().getById(samba.first)?.takeIf { it.enabled }
                ?: error("SMB 来源不可用。")
            val spec = resolveSambaSourceSpec(
                source = source,
                locatorRelativePath = samba.second,
                fallbackRelativePath = track.relativePath.ifBlank { samba.second },
            )
            val password = spec.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
            val context = SambaCastProxyContext(
                server = spec.server,
                port = spec.port,
                shareName = spec.shareName,
                remotePath = spec.remotePath,
                username = spec.username,
                password = password,
            )
            val totalSize = withContext(Dispatchers.IO) {
                openSambaStream(context).use { it.totalSize }
            }
            logger.debug("CastProxy") {
                "samba-resource source=${samba.first} endpoint=${spec.endpoint} remotePath=${spec.remotePath} bytes=$totalSize"
            }
            return SambaCastProxyResource(
                context = context,
                mimeType = mimeType,
                length = totalSize,
            )
        }
    }
}

private data class SambaCastProxyContext(
    val server: String,
    val port: Int,
    val shareName: String,
    val remotePath: String,
    val username: String,
    val password: String,
)

private data class SambaOpenedStream(
    val client: SMBClient,
    val connection: Connection,
    val session: Session,
    val share: DiskShare,
    val file: SmbFile,
    val totalSize: Long,
) : AutoCloseable {
    override fun close() {
        runCatching { file.close() }
        runCatching { share.close() }
        runCatching { session.close() }
        runCatching { connection.close() }
        runCatching { client.close() }
    }
}

private fun openSambaStream(context: SambaCastProxyContext): SambaOpenedStream {
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
        SambaOpenedStream(
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
