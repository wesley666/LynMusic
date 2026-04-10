package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.ImportedTrackCandidate
import top.iwesley.lyn.music.core.model.buildWebDavLocator
import top.iwesley.lyn.music.core.model.resolveWebDavRelativePath

internal data class WebDavListedResource(
    val href: String,
    val isDirectory: Boolean,
    val name: String?,
    val contentLength: Long,
    val modifiedAt: Long,
)

internal data class WebDavResolvedResource(
    val relativePath: String,
    val isDirectory: Boolean,
    val fileName: String,
    val contentLength: Long,
    val modifiedAt: Long,
)

internal fun resolveWebDavListedResource(
    rootUrl: String,
    currentDirectory: String,
    resource: WebDavListedResource,
): WebDavResolvedResource? {
    val relativePath = resolveWebDavRelativePath(rootUrl, resource.href) ?: return null
    if (relativePath.isBlank()) return null
    if (relativePath.trim('/') == currentDirectory.trim('/')) return null
    return WebDavResolvedResource(
        relativePath = relativePath,
        isDirectory = resource.isDirectory,
        fileName = resource.name ?: relativePath.substringAfterLast('/'),
        contentLength = resource.contentLength.coerceAtLeast(0L),
        modifiedAt = resource.modifiedAt.coerceAtLeast(0L),
    )
}

internal fun buildWebDavImportedTrackCandidate(
    sourceId: String,
    resource: WebDavResolvedResource,
): ImportedTrackCandidate {
    val fallbackTitle = resource.fileName.substringBeforeLast('.')
    return ImportedTrackCandidate(
        title = fallbackTitle,
        mediaLocator = buildWebDavLocator(sourceId, resource.relativePath),
        relativePath = resource.relativePath,
        sizeBytes = resource.contentLength,
        modifiedAt = resource.modifiedAt,
    )
}

internal fun buildWebDavImportedTrackCandidate(
    sourceId: String,
    resource: WebDavResolvedResource,
    metadata: RemoteAudioMetadata,
    storeArtwork: (ByteArray) -> String? = { null },
): ImportedTrackCandidate {
    val fallbackTitle = resource.fileName.substringBeforeLast('.')
    return ImportedTrackCandidate(
        title = metadata.title?.trim()?.takeIf { it.isNotBlank() } ?: fallbackTitle,
        artistName = metadata.artistName?.trim()?.takeIf { it.isNotBlank() },
        albumTitle = metadata.albumTitle?.trim()?.takeIf { it.isNotBlank() },
        durationMs = metadata.durationMs?.coerceAtLeast(0L) ?: 0L,
        trackNumber = metadata.trackNumber,
        discNumber = metadata.discNumber,
        mediaLocator = buildWebDavLocator(sourceId, resource.relativePath),
        relativePath = resource.relativePath,
        artworkLocator = metadata.artworkBytes?.takeIf { it.isNotEmpty() }?.let(storeArtwork),
        embeddedLyrics = metadata.embeddedLyrics?.trim()?.takeIf { it.isNotBlank() },
        sizeBytes = resource.contentLength,
        modifiedAt = resource.modifiedAt,
    )
}

internal fun buildWebDavRangeHeader(
    position: Long,
    requestedLength: Long,
): String? {
    if (position < 0L || requestedLength == 0L) return null
    if (position == 0L && requestedLength < 0L) return null
    val end = if (requestedLength < 0L) {
        ""
    } else {
        (position + requestedLength - 1L).toString()
    }
    return "bytes=$position-$end"
}
