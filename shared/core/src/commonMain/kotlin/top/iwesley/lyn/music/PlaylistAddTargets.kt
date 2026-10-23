package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.resources.*

fun buildPlaylistAddTargets(
    playlists: List<PlaylistSummary>,
    favoriteTrackIds: Set<String>,
    trackId: String?,
    includeLiked: Boolean = true,
): List<PlaylistAddTarget> {
    val likedTarget = PlaylistAddTarget(
        id = SYSTEM_LIKED_PLAYLIST_ID,
        name = "",
        nameText = uiText(Res.string.favorites_likes_title),
        kind = PlaylistKind.SYSTEM_LIKED,
        updatedAt = Long.MAX_VALUE,
        alreadyContainsTrack = trackId != null && trackId in favoriteTrackIds,
    )
    val systemTargets = if (includeLiked) listOf(likedTarget) else emptyList()
    return systemTargets + playlists
        .sortedWith(compareByDescending<PlaylistSummary> { it.updatedAt }.thenBy { it.name.lowercase() })
        .map { playlist ->
            PlaylistAddTarget(
                id = playlist.id,
                name = playlist.name,
                kind = playlist.kind,
                updatedAt = playlist.updatedAt,
                alreadyContainsTrack = trackId != null && trackId in playlist.memberTrackIds,
            )
        }
}
