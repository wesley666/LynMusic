package top.iwesley.lyn.music.feature.library

import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.Track

fun libraryArtistId(name: String): String = "artist:${name.trim().lowercase()}"

fun libraryAlbumId(
    artistName: String?,
    albumTitle: String,
): String {
    return "album:${artistName.orEmpty().trim().lowercase()}:${albumTitle.trim().lowercase()}"
}

fun deriveVisibleArtists(tracks: List<Track>): List<Artist> {
    return tracks.asSequence()
        .mapNotNull { track ->
            track.artistName?.trim()?.takeIf { it.isNotBlank() }?.let { artistName ->
                libraryArtistId(artistName) to artistName
            }
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        .map { (artistId, names) ->
            Artist(
                id = artistId,
                name = names.first(),
                trackCount = names.size,
            )
        }
        .sortedWith(compareByDescending<Artist> { it.trackCount }.thenBy { it.name.lowercase() })
}

fun deriveVisibleAlbums(tracks: List<Track>): List<Album> {
    return tracks.asSequence()
        .mapNotNull { track ->
            val albumTitle = track.albumTitle?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            VisibleAlbumSeed(
                id = libraryAlbumId(track.artistName, albumTitle),
                title = albumTitle,
                artistName = track.artistName?.trim()?.takeIf { it.isNotBlank() },
            )
        }
        .groupBy { it.id }
        .map { (albumId, items) ->
            val first = items.first()
            Album(
                id = albumId,
                title = first.title,
                artistName = first.artistName,
                trackCount = items.size,
            )
        }
        .sortedWith(compareByDescending<Album> { it.trackCount }.thenBy { it.title.lowercase() })
}

private data class VisibleAlbumSeed(
    val id: String,
    val title: String,
    val artistName: String?,
)
