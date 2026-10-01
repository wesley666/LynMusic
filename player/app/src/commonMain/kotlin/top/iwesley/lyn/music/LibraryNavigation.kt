package top.iwesley.lyn.music

import androidx.compose.runtime.Composable

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.uiText


import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.libraryAlbumId
import top.iwesley.lyn.music.feature.library.libraryArtistId

internal sealed interface LibraryNavigationTarget {
    data class Album(
        val albumId: String,
        val preferredSourceFilter: LibrarySourceFilter = LibrarySourceFilter.ALL,
    ) : LibraryNavigationTarget

    data class Artist(
        val artistId: String,
        val preferredSourceFilter: LibrarySourceFilter = LibrarySourceFilter.ALL,
    ) : LibraryNavigationTarget

    data class OnlineAlbum(
        val sourceId: String,
        val albumId: String,
        val albumTitle: String? = null,
        val artistName: String? = null,
        val artworkLocator: String? = null,
    ) : LibraryNavigationTarget

    data class OnlineArtist(
        val sourceId: String,
        val artistId: String,
        val artistName: String? = null,
    ) : LibraryNavigationTarget
}

internal data class PlaybackLibraryNavigationTargets(
    val albumTarget: LibraryNavigationTarget?,
    val artistTarget: LibraryNavigationTarget?,
)

internal data class LibraryNavigationResolution(
    val rootView: LibraryBrowserRootView,
    val selectedArtistId: String? = null,
    val selectedAlbumId: String? = null,
)

internal sealed interface LibraryNavigationCommand {
    data class ApplyContext(
        val sourceFilter: LibrarySourceFilter,
        val clearQuery: Boolean,
    ) : LibraryNavigationCommand

    data class ApplyOnlineContext(
        val sourceId: String,
        val clearQuery: Boolean,
    ) : LibraryNavigationCommand

    data class Navigate(val resolution: LibraryNavigationResolution) : LibraryNavigationCommand
}

internal fun shouldClearNavigationQuery(clearQuery: Boolean, query: String): Boolean {
    return clearQuery && query.isNotBlank()
}

internal fun shouldApplyOnlineNavigationContext(
    target: LibraryNavigationTarget,
    lastAppliedOnlineContextTarget: LibraryNavigationTarget?,
): Boolean {
    return when (target) {
        is LibraryNavigationTarget.OnlineAlbum,
        is LibraryNavigationTarget.OnlineArtist,
        -> target != lastAppliedOnlineContextTarget

        is LibraryNavigationTarget.Album,
        is LibraryNavigationTarget.Artist,
        -> false
    }
}

internal fun derivePlaybackLibraryNavigationTargets(
    snapshot: PlaybackSnapshot,
    track: Track,
): PlaybackLibraryNavigationTargets {
    val artistName = normalizedLibraryNavigationValue(snapshot.currentDisplayArtistName)
        ?: normalizedLibraryNavigationValue(track.artistName)
    val albumTitle = normalizedLibraryNavigationValue(snapshot.currentDisplayAlbumTitle)
        ?: normalizedLibraryNavigationValue(track.albumTitle)
    return deriveLibraryNavigationTargets(
        artistName = artistName,
        albumTitle = albumTitle,
    )
}

internal fun deriveTrackLibraryNavigationTargets(track: Track): PlaybackLibraryNavigationTargets {
    return deriveLibraryNavigationTargets(
        artistName = normalizedLibraryNavigationValue(track.artistName),
        albumTitle = normalizedLibraryNavigationValue(track.albumTitle),
    )
}

internal fun deriveTrackLibraryNavigationTargets(
    track: Track,
    preferredSourceFilter: LibrarySourceFilter,
): PlaybackLibraryNavigationTargets {
    return deriveLibraryNavigationTargets(
        artistName = normalizedLibraryNavigationValue(track.artistName),
        albumTitle = normalizedLibraryNavigationValue(track.albumTitle),
        preferredSourceFilter = preferredSourceFilter,
    )
}

internal fun deriveOnlinePlaybackLibraryNavigationTargets(
    snapshot: PlaybackSnapshot,
    track: Track,
    sourceId: String,
): PlaybackLibraryNavigationTargets {
    val artistName = normalizedLibraryNavigationValue(snapshot.currentDisplayArtistName)
        ?: normalizedLibraryNavigationValue(track.artistName)
    val albumTitle = normalizedLibraryNavigationValue(snapshot.currentDisplayAlbumTitle)
        ?: normalizedLibraryNavigationValue(track.albumTitle)
    return deriveOnlineLibraryNavigationTargets(
        track = track,
        sourceId = sourceId,
        artistName = artistName,
        albumTitle = albumTitle,
    )
}

internal fun deriveOnlineTrackLibraryNavigationTargets(
    track: Track,
    sourceId: String,
): PlaybackLibraryNavigationTargets {
    return deriveOnlineLibraryNavigationTargets(
        track = track,
        sourceId = sourceId,
        artistName = normalizedLibraryNavigationValue(track.artistName),
        albumTitle = normalizedLibraryNavigationValue(track.albumTitle),
    )
}

private fun deriveOnlineLibraryNavigationTargets(
    track: Track,
    sourceId: String,
    artistName: String?,
    albumTitle: String?,
): PlaybackLibraryNavigationTargets {
    return PlaybackLibraryNavigationTargets(
        albumTarget = track.albumId.normalizedLibraryNavigationValueOrNull()?.let { albumId ->
            LibraryNavigationTarget.OnlineAlbum(
                sourceId = sourceId,
                albumId = albumId,
                albumTitle = albumTitle,
                artistName = artistName,
                artworkLocator = track.artworkLocator,
            )
        },
        artistTarget = track.artistId.normalizedLibraryNavigationValueOrNull()?.let { artistId ->
            LibraryNavigationTarget.OnlineArtist(
                sourceId = sourceId,
                artistId = artistId,
                artistName = artistName,
            )
        },
    )
}

private fun deriveLibraryNavigationTargets(
    artistName: String?,
    albumTitle: String?,
    preferredSourceFilter: LibrarySourceFilter = LibrarySourceFilter.ALL,
): PlaybackLibraryNavigationTargets {
    return PlaybackLibraryNavigationTargets(
        albumTarget = albumTitle?.let {
            LibraryNavigationTarget.Album(
                albumId = libraryAlbumId(artistName, it),
                preferredSourceFilter = preferredSourceFilter,
            )
        },
        artistTarget = artistName?.let {
            LibraryNavigationTarget.Artist(
                artistId = libraryArtistId(it),
                preferredSourceFilter = preferredSourceFilter,
            )
        },
    )
}

internal fun resolveLibraryNavigationCommand(
    target: LibraryNavigationTarget,
    query: String,
    isOnline: Boolean = false,
    onlineSourceId: String? = null,
    selectedSourceFilter: LibrarySourceFilter,
    availableSourceFilters: List<LibrarySourceFilter>,
    filteredAlbums: List<Album>,
    filteredArtists: List<Artist>,
): LibraryNavigationCommand {
    when (target) {
        is LibraryNavigationTarget.OnlineAlbum -> {
            if (!isOnline || onlineSourceId != target.sourceId || query.isNotBlank()) {
                return LibraryNavigationCommand.ApplyOnlineContext(
                    sourceId = target.sourceId,
                    clearQuery = query.isNotBlank(),
                )
            }
            return LibraryNavigationCommand.Navigate(
                LibraryNavigationResolution(
                    rootView = LibraryBrowserRootView.Albums,
                    selectedAlbumId = target.albumId,
                ),
            )
        }

        is LibraryNavigationTarget.OnlineArtist -> {
            if (!isOnline || onlineSourceId != target.sourceId || query.isNotBlank()) {
                return LibraryNavigationCommand.ApplyOnlineContext(
                    sourceId = target.sourceId,
                    clearQuery = query.isNotBlank(),
                )
            }
            return LibraryNavigationCommand.Navigate(
                LibraryNavigationResolution(
                    rootView = LibraryBrowserRootView.Artists,
                    selectedArtistId = target.artistId,
                ),
            )
        }

        is LibraryNavigationTarget.Album,
        is LibraryNavigationTarget.Artist,
        -> Unit
    }
    val targetSourceFilter = target.navigationSourceFilter
        .takeIf { it == LibrarySourceFilter.ALL || it in availableSourceFilters }
        ?: LibrarySourceFilter.ALL
    if (query.isNotBlank() || selectedSourceFilter != targetSourceFilter) {
        return LibraryNavigationCommand.ApplyContext(
            sourceFilter = targetSourceFilter,
            clearQuery = query.isNotBlank(),
        )
    }
    return LibraryNavigationCommand.Navigate(
        when (target) {
            is LibraryNavigationTarget.Album -> {
                LibraryNavigationResolution(
                    rootView = LibraryBrowserRootView.Albums,
                    selectedAlbumId = target.albumId.takeIf { albumId ->
                        filteredAlbums.any { it.id == albumId }
                    },
                )
            }

            is LibraryNavigationTarget.Artist -> {
                LibraryNavigationResolution(
                    rootView = LibraryBrowserRootView.Artists,
                    selectedArtistId = target.artistId.takeIf { artistId ->
                        filteredArtists.any { it.id == artistId }
                    },
                )
            }

            is LibraryNavigationTarget.OnlineAlbum,
            is LibraryNavigationTarget.OnlineArtist,
            -> throw top.iwesley.lyn.music.core.model.UiTextException(uiText(Res.string.navigation_online_resolution_required))
        },
    )
}

private val LibraryNavigationTarget.navigationSourceFilter: LibrarySourceFilter
    get() = when (this) {
        is LibraryNavigationTarget.Album -> preferredSourceFilter
        is LibraryNavigationTarget.Artist -> preferredSourceFilter
        is LibraryNavigationTarget.OnlineAlbum,
        is LibraryNavigationTarget.OnlineArtist,
        -> LibrarySourceFilter.ALL
    }

private fun normalizedLibraryNavigationValue(value: String?): String? {
    return value?.trim()?.takeIf { it.isNotBlank() }
}

private fun String?.normalizedLibraryNavigationValueOrNull(): String? {
    return normalizedLibraryNavigationValue(this)
}
