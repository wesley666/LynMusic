package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.DEFAULT_SAMBA_PORT
import top.iwesley.lyn.music.core.model.formatSambaEndpoint
import top.iwesley.lyn.music.core.model.joinSambaPath
import top.iwesley.lyn.music.core.model.normalizeSambaPath
import top.iwesley.lyn.music.core.model.parseSambaLocator
import top.iwesley.lyn.music.core.model.parseSambaPath
import top.iwesley.lyn.music.data.db.ImportSourceEntity

data class ResolvedSambaSourceSpec(
    val sourceId: String,
    val endpoint: String,
    val server: String,
    val port: Int,
    val shareName: String,
    val remotePath: String,
    val relativePath: String,
    val username: String,
    val credentialKey: String?,
)

fun shouldUseAndroidSambaDirectPlayback(locator: String, useSambaCache: Boolean): Boolean {
    return !useSambaCache && parseSambaLocator(locator) != null
}

fun resolveSambaSourceSpec(
    source: ImportSourceEntity,
    locatorRelativePath: String,
    fallbackRelativePath: String = locatorRelativePath,
): ResolvedSambaSourceSpec {
    val shareName = source.shareName
    val parsedPort = shareName?.toIntOrNull()
    val storedPath = when {
        parsedPort != null -> normalizeSambaPath(source.directoryPath)
        shareName.isNullOrBlank() -> normalizeSambaPath(source.directoryPath)
        else -> normalizeSambaPath(joinSambaPath(requireNotNull(shareName), source.directoryPath.orEmpty()))
    }
    val sambaPath = parseSambaPath(storedPath)
        ?: error("SMB source path is missing a share name.")
    return ResolvedSambaSourceSpec(
        sourceId = source.id,
        endpoint = formatSambaEndpoint(source.server.orEmpty(), parsedPort, storedPath),
        server = source.server.orEmpty(),
        port = parsedPort ?: DEFAULT_SAMBA_PORT,
        shareName = sambaPath.shareName,
        remotePath = joinSambaPath(sambaPath.directoryPath, locatorRelativePath),
        relativePath = fallbackRelativePath.ifBlank { locatorRelativePath },
        username = source.username.orEmpty(),
        credentialKey = source.credentialKey,
    )
}

fun buildAndroidSambaSourceReference(
    endpoint: String,
    shareName: String,
    remotePath: String,
): String {
    return "endpoint=$endpoint share=$shareName remotePath=$remotePath"
}
