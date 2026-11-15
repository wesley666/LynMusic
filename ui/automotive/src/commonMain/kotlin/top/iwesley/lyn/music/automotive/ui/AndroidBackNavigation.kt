package top.iwesley.lyn.music.automotive.ui

import top.iwesley.lyn.music.*

internal enum class LibraryBrowserBackTarget {
    Album,
    Artist,
}

internal fun resolveLibraryBrowserBackTarget(
    selectedArtistId: String?,
    selectedAlbumId: String?,
): LibraryBrowserBackTarget? {
    return when {
        selectedAlbumId != null -> LibraryBrowserBackTarget.Album
        selectedArtistId != null -> LibraryBrowserBackTarget.Artist
        else -> null
    }
}

internal fun canNavigateBackFromPlaylistDetail(selectedPlaylistId: String?): Boolean {
    return selectedPlaylistId != null
}

internal fun canNavigateBackFromMusicTagsDetail(detailTrackId: String?): Boolean {
    return detailTrackId != null
}
