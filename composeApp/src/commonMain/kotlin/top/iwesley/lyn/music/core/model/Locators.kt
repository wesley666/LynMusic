package top.iwesley.lyn.music.core.model

import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLParameter

private const val SAMBA_SCHEME = "lynmusic-smb://"
const val DEFAULT_SAMBA_PORT = 445

data class SambaPath(
    val shareName: String,
    val directoryPath: String = "",
)

fun buildSambaLocator(sourceId: String, relativePath: String): String {
    val normalizedPath = relativePath.trimStart('/')
    return SAMBA_SCHEME + sourceId + "/" + normalizedPath.encodeURLParameter()
}

fun parseSambaLocator(locator: String): Pair<String, String>? {
    if (!locator.startsWith(SAMBA_SCHEME)) return null
    val payload = locator.removePrefix(SAMBA_SCHEME)
    val dividerIndex = payload.indexOf('/')
    if (dividerIndex <= 0) return null
    val sourceId = payload.substring(0, dividerIndex)
    val relativePath = payload.substring(dividerIndex + 1).decodeURLPart()
    return sourceId to relativePath
}

fun parseSambaPath(path: String?): SambaPath? {
    val normalized = normalizeSambaPath(path)
    if (normalized.isBlank()) return null
    val shareName = normalized.substringBefore("/")
    if (shareName.isBlank()) return null
    val directoryPath = normalized.substringAfter("/", "")
    return SambaPath(
        shareName = shareName,
        directoryPath = directoryPath,
    )
}

fun normalizeSambaPath(path: String?): String {
    return path.orEmpty()
        .trim()
        .replace('\\', '/')
        .trim('/')
}

fun formatSambaEndpoint(server: String?, port: Int?, path: String?): String {
    val host = server.orEmpty().trim()
    val hostWithPort = when {
        host.isBlank() -> ""
        port == null || port == DEFAULT_SAMBA_PORT -> host
        else -> "$host:$port"
    }
    val normalizedPath = normalizeSambaPath(path)
    return when {
        hostWithPort.isBlank() -> normalizedPath
        normalizedPath.isBlank() -> hostWithPort
        else -> "$hostWithPort/$normalizedPath"
    }
}

fun joinSambaPath(left: String, right: String): String {
    return listOf(left.trim('/'), right.trim('/'))
        .filter { it.isNotBlank() }
        .joinToString("/")
}

fun buildDirectSambaUrl(
    server: String,
    port: Int?,
    shareName: String,
    remotePath: String,
    username: String = "",
    password: String = "",
): String {
    val host = server.trim()
    val hostWithPort = if (port == null || port == DEFAULT_SAMBA_PORT) host else "$host:$port"
    val fullPath = joinSambaPath(shareName, remotePath)
    val auth = if (username.isBlank()) {
        ""
    } else {
        val encodedUser = username.encodeURLParameter()
        val encodedPassword = password.encodeURLParameter()
        if (password.isBlank()) "$encodedUser@" else "$encodedUser:$encodedPassword@"
    }
    return "smb://$auth$hostWithPort/${encodeSambaUrlPath(fullPath)}"
}

fun encodeSambaUrlPath(path: String): String {
    return normalizeSambaPath(path)
        .split("/")
        .filter { it.isNotBlank() }
        .joinToString("/") { segment -> segment.encodeURLParameter() }
}
