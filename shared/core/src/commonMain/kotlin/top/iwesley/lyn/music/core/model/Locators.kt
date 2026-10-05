package top.iwesley.lyn.music.core.model

import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLParameter

private const val SAMBA_SCHEME = "lynmusic-smb://"
private const val NAVIDROME_SCHEME = "lynmusic-navidrome://"
private const val NAVIDROME_COVER_SCHEME = "lynmusic-navidrome-cover://"
private const val SUBSONIC_SCHEME = "lynmusic-subsonic://"
private const val SUBSONIC_COVER_SCHEME = "lynmusic-subsonic-cover://"
private const val EMBY_SCHEME = "lynmusic-emby://"
private const val EMBY_COVER_SCHEME = "lynmusic-emby-cover://"
private const val FN_MUSIC_SCHEME = "lynmusic-fnmusic://"
private const val FN_MUSIC_COVER_SCHEME = "lynmusic-fnmusic-cover://"
const val DEFAULT_SAMBA_PORT = 445

data class SubsonicCompatibleLocator(
    val sourceId: String,
    val itemId: String,
    val sourceType: ImportSourceType,
)

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

fun buildSubsonicSongLocator(sourceId: String, songId: String): String {
    return SUBSONIC_SCHEME + sourceId.encodeURLParameter() + "/" + songId.encodeURLParameter()
}

fun parseSubsonicSongLocator(locator: String): Pair<String, String>? {
    return parseSubsonicLocator(locator, SUBSONIC_SCHEME)
}

fun buildSubsonicCoverLocator(sourceId: String, coverArtId: String): String {
    return SUBSONIC_COVER_SCHEME + sourceId.encodeURLParameter() + "/" + coverArtId.encodeURLParameter()
}

fun parseSubsonicCoverLocator(locator: String): Pair<String, String>? {
    return parseSubsonicLocator(locator, SUBSONIC_COVER_SCHEME)
}

fun buildSubsonicCompatibleSongLocator(
    sourceType: ImportSourceType,
    sourceId: String,
    songId: String,
): String {
    return when (sourceType) {
        ImportSourceType.NAVIDROME -> buildNavidromeSongLocator(sourceId, songId)
        ImportSourceType.SUBSONIC -> buildSubsonicSongLocator(sourceId, songId)
        else -> error("Unsupported Subsonic-compatible source type: $sourceType")
    }
}

fun buildSubsonicCompatibleCoverLocator(
    sourceType: ImportSourceType,
    sourceId: String,
    coverArtId: String,
): String {
    return when (sourceType) {
        ImportSourceType.NAVIDROME -> buildNavidromeCoverLocator(sourceId, coverArtId)
        ImportSourceType.SUBSONIC -> buildSubsonicCoverLocator(sourceId, coverArtId)
        else -> error("Unsupported Subsonic-compatible source type: $sourceType")
    }
}

fun parseSubsonicCompatibleSongLocator(locator: String): SubsonicCompatibleLocator? {
    parseNavidromeSongLocator(locator)?.let { (sourceId, itemId) ->
        return SubsonicCompatibleLocator(sourceId, itemId, ImportSourceType.NAVIDROME)
    }
    parseSubsonicSongLocator(locator)?.let { (sourceId, itemId) ->
        return SubsonicCompatibleLocator(sourceId, itemId, ImportSourceType.SUBSONIC)
    }
    return null
}

fun parseSubsonicCompatibleCoverLocator(locator: String): SubsonicCompatibleLocator? {
    parseNavidromeCoverLocator(locator)?.let { (sourceId, itemId) ->
        return SubsonicCompatibleLocator(sourceId, itemId, ImportSourceType.NAVIDROME)
    }
    parseSubsonicCoverLocator(locator)?.let { (sourceId, itemId) ->
        return SubsonicCompatibleLocator(sourceId, itemId, ImportSourceType.SUBSONIC)
    }
    return null
}

fun buildEmbySongLocator(sourceId: String, itemId: String): String {
    return EMBY_SCHEME + sourceId.encodeURLParameter() + "/" + itemId.encodeURLParameter()
}

fun parseEmbySongLocator(locator: String): Pair<String, String>? {
    return parseEmbyLocator(locator, EMBY_SCHEME)
}

fun buildEmbyCoverLocator(sourceId: String, itemId: String): String {
    return EMBY_COVER_SCHEME + sourceId.encodeURLParameter() + "/" + itemId.encodeURLParameter()
}

fun parseEmbyCoverLocator(locator: String): Pair<String, String>? {
    return parseEmbyLocator(locator, EMBY_COVER_SCHEME)
}

fun buildFnMusicSongLocator(sourceId: String, guid: String): String {
    return FN_MUSIC_SCHEME + sourceId.encodeURLParameter() + "/" + guid.encodeURLParameter()
}

fun parseFnMusicSongLocator(locator: String): Pair<String, String>? {
    return parseSubsonicLocator(locator, FN_MUSIC_SCHEME)
}

fun buildFnMusicCoverLocator(sourceId: String, coverId: String): String {
    return FN_MUSIC_COVER_SCHEME + sourceId.encodeURLParameter() + "/" + coverId.encodeURLParameter()
}

fun parseFnMusicCoverLocator(locator: String): Pair<String, String>? {
    return parseSubsonicLocator(locator, FN_MUSIC_COVER_SCHEME)
}

private fun parseNavidromeLocator(locator: String, scheme: String): Pair<String, String>? {
    return parseSubsonicLocator(locator, scheme)
}

private fun parseEmbyLocator(locator: String, scheme: String): Pair<String, String>? {
    return parseSubsonicLocator(locator, scheme)
}

private fun parseSubsonicLocator(locator: String, scheme: String): Pair<String, String>? {
    if (!locator.startsWith(scheme)) return null
    val payload = locator.removePrefix(scheme)
    val dividerIndex = payload.indexOf('/')
    if (dividerIndex <= 0) return null
    val sourceId = payload.substring(0, dividerIndex).decodeURLPart()
    val itemId = payload.substring(dividerIndex + 1).decodeURLPart()
    if (sourceId.isBlank() || itemId.isBlank()) return null
    return sourceId to itemId
}
