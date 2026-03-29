package top.iwesley.lyn.music.core.model

import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLParameter

private const val SAMBA_SCHEME = "lynmusic-smb://"
private const val NAVIDROME_SCHEME = "lynmusic-navidrome://"
private const val NAVIDROME_COVER_SCHEME = "lynmusic-navidrome-cover://"
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

fun buildNavidromeSongLocator(sourceId: String, songId: String): String {
    return NAVIDROME_SCHEME + sourceId.encodeURLParameter() + "/" + songId.encodeURLParameter()
}

fun parseNavidromeSongLocator(locator: String): Pair<String, String>? {
    return parseNavidromeLocator(locator, NAVIDROME_SCHEME)
}

fun buildNavidromeCoverLocator(sourceId: String, coverArtId: String): String {
    return NAVIDROME_COVER_SCHEME + sourceId.encodeURLParameter() + "/" + coverArtId.encodeURLParameter()
}

fun parseNavidromeCoverLocator(locator: String): Pair<String, String>? {
    return parseNavidromeLocator(locator, NAVIDROME_COVER_SCHEME)
}

private fun parseNavidromeLocator(locator: String, scheme: String): Pair<String, String>? {
    if (!locator.startsWith(scheme)) return null
    val payload = locator.removePrefix(scheme)
    val dividerIndex = payload.indexOf('/')
    if (dividerIndex <= 0) return null
    val sourceId = payload.substring(0, dividerIndex).decodeURLPart()
    val itemId = payload.substring(dividerIndex + 1).decodeURLPart()
    if (sourceId.isBlank() || itemId.isBlank()) return null
    return sourceId to itemId
}
