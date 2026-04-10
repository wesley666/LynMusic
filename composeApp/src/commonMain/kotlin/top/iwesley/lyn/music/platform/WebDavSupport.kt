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
    return ImportedTrackCandidate(
        title = resource.fileName.substringBeforeLast('.'),
        mediaLocator = buildWebDavLocator(sourceId, resource.relativePath),
        relativePath = resource.relativePath,
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
