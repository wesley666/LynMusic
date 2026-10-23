package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.parseSubsonicCompatibleSongLocator
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.online.OnlineFavoritesState

data class PlayerFavoriteBinding(
    val isFavorite: Boolean,
    val isFavoriteKnown: Boolean = true,
    val canToggleFavorite: Boolean = true,
    val onlineSourceId: String? = null,
)

fun Track.onlineNavidromeSourceIdOrNull(importState: ImportState): String? {
    val parsed = parseSubsonicCompatibleSongLocator(mediaLocator) ?: return null
    if (parsed.sourceType != ImportSourceType.NAVIDROME || parsed.sourceId != sourceId) return null
    return parsed.sourceId.takeIf { sourceId ->
        importState.sources.any { sourceState ->
            val source = sourceState.source
            source.enabled &&
                source.id == sourceId &&
                source.type == ImportSourceType.NAVIDROME &&
                source.indexMode == ImportSourceIndexMode.ONLINE
        }
    }
}

fun playerFavoriteBinding(
    track: Track?,
    localFavoriteTrackIds: Set<String>,
    onlineFavoritesState: OnlineFavoritesState,
    importState: ImportState,
): PlayerFavoriteBinding {
    if (track == null) {
        return PlayerFavoriteBinding(
            isFavorite = false,
            isFavoriteKnown = false,
            canToggleFavorite = false,
        )
    }
    val onlineSourceId = track.onlineNavidromeSourceIdOrNull(importState)
    if (onlineSourceId != null) {
        val favoritesSourceMatches = onlineFavoritesState.sourceId == onlineSourceId
        val favoriteOverride = onlineFavoritesState.favoriteOverridesBySourceId[onlineSourceId]?.get(track.id)
        val remoteFavoriteHint = track.remoteFavoriteHint
        val loadedFavorite = favoritesSourceMatches &&
            onlineFavoritesState.tracks.any { it.id == track.id }
        val favoritesListFullyLoaded = favoritesSourceMatches &&
            !onlineFavoritesState.isLoading &&
            !onlineFavoritesState.isLoadingMore &&
            !onlineFavoritesState.canLoadMore
        val isFavorite = favoriteOverride ?: remoteFavoriteHint ?: loadedFavorite
        val isFavoriteKnown = favoriteOverride != null ||
            remoteFavoriteHint != null ||
            loadedFavorite ||
            favoritesListFullyLoaded
        return PlayerFavoriteBinding(
            isFavorite = isFavorite,
            isFavoriteKnown = isFavoriteKnown,
            canToggleFavorite = true,
            onlineSourceId = onlineSourceId,
        )
    }
    return PlayerFavoriteBinding(isFavorite = track.id in localFavoriteTrackIds)
}
