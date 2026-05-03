package top.iwesley.lyn.music.core.model

fun normalizedArtworkCacheLocator(locator: String?): String? {
    val rawTarget = normalizeArtworkLocator(locator)?.trim().orEmpty()
    return rawTarget.takeIf { it.isNotBlank() }
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

suspend fun resolveArtworkCacheTarget(locator: String?): String? {
    val rawTarget = normalizedArtworkCacheLocator(locator) ?: return null
    val target = if (parseNavidromeCoverLocator(rawTarget) != null) {
        NavidromeLocatorRuntime.resolveCoverArtUrl(rawTarget).orEmpty()
    } else {
        rawTarget
    }
    return target.takeIf { it.isNotBlank() }
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
