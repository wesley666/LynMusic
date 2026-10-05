package top.iwesley.lyn.music.core.model

fun normalizedArtworkCacheLocator(locator: String?): String? {
    val rawTarget = normalizeArtworkLocator(locator)?.trim().orEmpty()
    return rawTarget.takeIf { it.isNotBlank() }
}

fun buildIosArtworkCacheLocator(fileName: String): String? {
    val normalizedFileName = normalizedIosArtworkCacheFileName(fileName) ?: return null
    return "$IOS_ARTWORK_CACHE_LOCATOR_PREFIX$normalizedFileName"
}

fun isIosArtworkCacheLocator(locator: String?): Boolean {
    return locator?.trim()?.startsWith(IOS_ARTWORK_CACHE_SCHEME_PREFIX) == true
}

fun isIosArtworkCacheBackedLocator(locator: String?): Boolean {
    return isIosArtworkCacheLocator(locator) || parseLegacyIosArtworkCacheFileName(locator) != null
}

fun parseIosArtworkCacheLocator(locator: String?): String? {
    val normalizedLocator = locator?.trim()?.takeIf { it.startsWith(IOS_ARTWORK_CACHE_LOCATOR_PREFIX) }
        ?: return null
    return normalizedIosArtworkCacheFileName(normalizedLocator.removePrefix(IOS_ARTWORK_CACHE_LOCATOR_PREFIX))
}

fun parseLegacyIosArtworkCacheFileName(locator: String?): String? {
    val rawLocator = locator?.trim().orEmpty()
    val localPath = when {
        rawLocator.startsWith('/') -> rawLocator
        rawLocator.startsWith(IOS_LOCAL_FILE_URL_PREFIX, ignoreCase = true) ->
            rawLocator.substring(IOS_FILE_URL_SCHEME_PREFIX.length)

        else -> return null
    }
    if (!localPath.contains(IOS_APP_DATA_CONTAINER_PATH_MARKER)) return null
    val markerIndex = localPath.lastIndexOf(IOS_ARTWORK_CACHE_PATH_MARKER)
    if (markerIndex < 0) return null
    val fileName = localPath.substring(markerIndex + IOS_ARTWORK_CACHE_PATH_MARKER.length)
    if (fileName.contains('/') || fileName.contains('\\')) return null
    return normalizedIosArtworkCacheFileName(fileName)
}

fun trackArtworkCacheKey(track: Track): String? {
    return albumArtworkCacheKey(
        sourceId = track.sourceId,
        albumId = track.albumId,
        artistName = track.artistName,
        albumTitle = track.albumTitle,
    ) ?: normalizedArtworkCacheLocator(track.artworkLocator)
}

fun albumArtworkCacheKey(
    sourceId: String,
    albumId: String?,
    artistName: String?,
    albumTitle: String?,
): String? {
    val normalizedSourceId = normalizeArtworkCacheKeyPart(sourceId) ?: return null
    val normalizedAlbumId = albumId?.trim()?.takeIf { it.isNotBlank() }
    if (normalizedAlbumId != null) {
        return "album:$normalizedSourceId:$normalizedAlbumId"
    }
    val normalizedAlbumTitle = normalizeArtworkCacheKeyPart(albumTitle) ?: return null
    val normalizedArtist = normalizeArtworkCacheKeyPart(artistName).orEmpty()
    return "album:$normalizedSourceId:$normalizedArtist:$normalizedAlbumTitle"
}

/**
 * A plain URL (or path) an image loader can fetch as-is. Candidates that need request headers (FN Music's signed
 * covers) are skipped: without their headers they can only fail, so callers get null and rely on the cache, which
 * reads them through [resolveArtworkCacheTargets] with their headers. FN Music covers only ever resolve to such
 * candidates, so they are answered null up front, without the login or FN Connect lookup resolving them would cost.
 */
suspend fun resolveArtworkCacheTarget(locator: String?): String? {
    if (normalizedArtworkCacheLocator(locator)?.let(::parseFnMusicCoverLocator) != null) return null
    return resolveArtworkCacheTargets(locator).firstOrNull { it.headers.isEmpty() }?.value
}

suspend fun resolveArtworkCacheTargets(locator: String?): List<RemotePlaybackUrlCandidate> {
    val rawTarget = normalizedArtworkCacheLocator(locator) ?: return emptyList()
    val targets = if (parseSubsonicCompatibleCoverLocator(rawTarget) != null) {
        NavidromeLocatorRuntime.resolveCoverArtUrlCandidates(rawTarget)
    } else if (parseEmbyCoverLocator(rawTarget) != null || parseFnMusicCoverLocator(rawTarget) != null) {
        NavidromeLocatorRuntime.resolveCoverArtUrlCandidates(rawTarget)
    } else {
        listOf(RemotePlaybackUrlCandidate(value = rawTarget))
    }
    return targets.orEmpty().filter { it.value.isNotBlank() }
}

fun String.stableArtworkCacheHash(): String {
    var hash = 14695981039346656037uL
    encodeToByteArray().forEach { byte ->
        hash = (hash xor byte.toUByte().toULong()) * 1099511628211uL
    }
    return hash.toString(16).padStart(16, '0')
}

fun ByteArray.stableArtworkBytesHash(): String {
    var hash = 14695981039346656037uL
    forEach { byte ->
        hash = (hash xor byte.toUByte().toULong()) * 1099511628211uL
    }
    return hash.toString(16).padStart(16, '0')
}

private fun normalizeArtworkCacheKeyPart(value: String?): String? {
    return value
        ?.trim()
        ?.lowercase()
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf { it.isNotBlank() }
}

private fun normalizedIosArtworkCacheFileName(fileName: String): String? {
    val candidate = fileName.trim()
    if (candidate.isEmpty() || candidate.length > IOS_ARTWORK_CACHE_FILE_NAME_MAX_LENGTH) return null
    if (!candidate.first().isLetterOrDigit()) return null
    if (candidate.any { character ->
            !character.isLetterOrDigit() && character != '.' && character != '-' && character != '_'
        }
    ) {
        return null
    }
    return candidate
}

private const val IOS_ARTWORK_CACHE_SCHEME_PREFIX = "lynmusic-ios-artwork://"
private const val IOS_ARTWORK_CACHE_LOCATOR_PREFIX = "${IOS_ARTWORK_CACHE_SCHEME_PREFIX}v1/"
private const val IOS_FILE_URL_SCHEME_PREFIX = "file://"
private const val IOS_LOCAL_FILE_URL_PREFIX = "${IOS_FILE_URL_SCHEME_PREFIX}/"
private const val IOS_APP_DATA_CONTAINER_PATH_MARKER = "/Containers/Data/Application/"
private const val IOS_ARTWORK_CACHE_PATH_MARKER = "/Library/Caches/lynmusic-artwork-cache/"
private const val IOS_ARTWORK_CACHE_FILE_NAME_MAX_LENGTH = 128
