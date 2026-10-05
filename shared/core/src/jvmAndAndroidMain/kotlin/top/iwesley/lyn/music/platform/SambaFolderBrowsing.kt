package top.iwesley.lyn.music.platform

import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.SMBTransportFactories

private const val SMB_SHARE_TYPE_MASK = 0x0FFFFFFF
private const val SMB_SHARE_TYPE_DISK = 0

/** Visible disk shares on the server (srvsvc NetShareEnum), skipping printers, IPC and hidden `$` shares. */
fun listSambaDiskShares(session: Session): List<String> {
    val transport = SMBTransportFactories.SRVSVC.getTransport(session)
    return ServerService(transport).shares1
        .filter { share -> share.type and SMB_SHARE_TYPE_MASK == SMB_SHARE_TYPE_DISK && !share.netName.endsWith("$") }
        .map { it.netName }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
}

/** Sub-folders directly inside [directoryPath] of [share], sorted by name. */
fun listSambaChildDirectories(share: DiskShare, directoryPath: String): List<String> =
    share.list(directoryPath)
        .filter { info ->
            info.fileName != "." && info.fileName != ".." &&
                info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
        }
        .map { it.fileName }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
