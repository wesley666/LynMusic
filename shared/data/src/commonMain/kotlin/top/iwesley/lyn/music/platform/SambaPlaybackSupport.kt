package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.DEFAULT_SAMBA_PORT
import top.iwesley.lyn.music.core.model.formatSambaEndpoint
import top.iwesley.lyn.music.core.model.joinSambaPath
import top.iwesley.lyn.music.core.model.normalizeSambaPath
import top.iwesley.lyn.music.core.model.parseSambaLocator
import top.iwesley.lyn.music.core.model.resolveSambaRemoteFile
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.uiText
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
    /** Path identifying the file within the source for cache names; includes the share once a source spans several. */
    val cacheKeyPath: String = remotePath,
)

private const val SAMBA_CACHE_FILE_NAME_MAX_LENGTH = 200

/**
 * Cache file name for a Samba track. The readable part replaces non-ASCII characters, so different paths can collapse to
 * the same text (e.g. `音乐/a.mp3` and `备份/a.mp3`); a hash of the full original path keeps every name unique.
 */
fun buildSambaCacheFileName(sourceId: String, cacheKeyPath: String): String {
    val sanitized = cacheKeyPath.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val hash = cacheKeyPath.hashCode().toUInt().toString(16)
    // Keep the extension last so players can still sniff the format from the cached file.
    val extension = sanitized.substringAfterLast('.', "").take(10).let { if (it.isEmpty()) "" else ".$it" }
    val readable = "$sourceId-${sanitized.removeSuffix(extension)}"
        .take(SAMBA_CACHE_FILE_NAME_MAX_LENGTH - hash.length - extension.length - 1)
    return "$readable-$hash$extension"
}

fun shouldUseAndroidSambaDirectPlayback(locator: String, useSambaCache: Boolean): Boolean {
    return !useSambaCache && parseSambaLocator(locator) != null
}

/** Port stored for a Samba row; the `shareName` column holds it (rows from before ports were added hold a real share name). */
fun ImportSourceEntity.sambaPort(): Int? = shareName?.toIntOrNull()

/** Root of a Samba row as `share/sub/path`, blank for sources rooted at the server. Old rows split the share into `shareName`. */
fun ImportSourceEntity.sambaRootPath(): String {
    val legacyShareName = shareName
    return when {
        legacyShareName?.toIntOrNull() != null -> normalizeSambaPath(directoryPath)
        legacyShareName.isNullOrBlank() -> normalizeSambaPath(directoryPath)
        else -> normalizeSambaPath(joinSambaPath(legacyShareName, directoryPath.orEmpty()))
    }
}

fun resolveSambaSourceSpec(
    source: ImportSourceEntity,
    locatorRelativePath: String,
    fallbackRelativePath: String = locatorRelativePath,
): ResolvedSambaSourceSpec {
    val parsedPort = source.sambaPort()
    val storedPath = source.sambaRootPath()
    val sambaPath = resolveSambaRemoteFile(storedPath, locatorRelativePath)
        ?: throw UiTextException(uiText(Res.string.samba_path_missing_share))
    return ResolvedSambaSourceSpec(
        sourceId = source.id,
        endpoint = formatSambaEndpoint(source.server.orEmpty(), parsedPort, storedPath),
        server = source.server.orEmpty(),
        port = parsedPort ?: DEFAULT_SAMBA_PORT,
        shareName = sambaPath.shareName,
        remotePath = sambaPath.directoryPath,
        relativePath = fallbackRelativePath.ifBlank { locatorRelativePath },
        username = source.username.orEmpty(),
        credentialKey = source.credentialKey,
        // Legacy sources live in one share, so their cache names stay as before; server-rooted ones span shares.
        cacheKeyPath = if (storedPath.isBlank()) joinSambaPath(sambaPath.shareName, sambaPath.directoryPath) else sambaPath.directoryPath,
    )
}

fun buildAndroidSambaSourceReference(
    endpoint: String,
    shareName: String,
    remotePath: String,
): String {
    return "endpoint=$endpoint share=$shareName remotePath=$remotePath"
}
