package top.iwesley.lyn.music.desktop.ui

import top.iwesley.lyn.music.*

internal enum class LibraryBrowserBackTarget {
    Album,
    Artist,
    Folder,
}

internal fun resolveLibraryBrowserBackTarget(
    selectedArtistId: String?,
    selectedAlbumId: String?,
    selectedFolderSourceId: String? = null,
): LibraryBrowserBackTarget? {
    return when {
        selectedAlbumId != null -> LibraryBrowserBackTarget.Album
        selectedArtistId != null -> LibraryBrowserBackTarget.Artist
        selectedFolderSourceId != null -> LibraryBrowserBackTarget.Folder
        else -> null
    }
}

internal data class LibraryFolderBackDestination(
    val sourceId: String?,
    val path: String?,
)

internal fun resolveLibraryFolderBackDestination(
    selectedFolderSourceId: String?,
    selectedFolderPath: String?,
): LibraryFolderBackDestination {
    val sourceId = selectedFolderSourceId ?: return LibraryFolderBackDestination(
        sourceId = null,
        path = null,
    )
    val path = selectedFolderPath.orEmpty().trim('/')
    return when {
        path.isBlank() -> LibraryFolderBackDestination(
            sourceId = null,
            path = null,
        )

        '/' !in path -> LibraryFolderBackDestination(
            sourceId = sourceId,
            path = "",
        )

        else -> LibraryFolderBackDestination(
            sourceId = sourceId,
            path = path.substringBeforeLast('/'),
        )
    }
}

internal fun canNavigateBackFromPlaylistDetail(selectedPlaylistId: String?): Boolean {
    return selectedPlaylistId != null
}

internal fun canNavigateBackFromMusicTagsDetail(detailTrackId: String?): Boolean {
    return detailTrackId != null
}
