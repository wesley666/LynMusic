package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.PlaylistSummary

fun filterPlaylistsByQuery(
    playlists: List<PlaylistSummary>,
    query: String,
): List<PlaylistSummary> {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isBlank()) return playlists
    return playlists.filter { playlist ->
        playlist.name.lowercase().contains(normalizedQuery)
    }
}
