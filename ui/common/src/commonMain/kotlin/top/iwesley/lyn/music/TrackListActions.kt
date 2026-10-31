package top.iwesley.lyn.music

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RecentActors
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.resources.*

data class TrackFavoriteUiState(
    val isFavorite: Boolean,
    val isKnown: Boolean = true,
    val canChange: Boolean = true,
)

/** App-owned callbacks always receive the selected row's Track, never the playing track. */
data class TrackListActionBindings(
    val favoriteState: ((Track) -> TrackFavoriteUiState)? = null,
    val onSetFavorite: ((Track, Boolean) -> Unit)? = null,
    val onAddToPlaylist: ((Track) -> Unit)? = null,
)

val LocalTrackListActionBindings = staticCompositionLocalOf { TrackListActionBindings() }

@Composable
fun TrackFavoriteIndicator() {
    Icon(Icons.Rounded.Favorite, uiString(Res.string.track_favorite_indicator),
        modifier = Modifier.size(14.dp), tint = Color(0xFFE5484D))
}

fun trackListMenuActions(
    favorite: TrackFavoriteUiState,
    onSetFavorite: (Boolean) -> Unit,
    onAddToPlaylist: (() -> Unit)?,
    onTrackInfo: () -> Unit,
    onOfflineDownload: (() -> Unit)?,
    onArtistClick: (() -> Unit)? = null,
    onAlbumClick: (() -> Unit)? = null,
): List<ListItemMenuAction> = buildList {
    val liked = favorite.isKnown && favorite.isFavorite
    add(ListItemMenuAction("favorite", uiText(if (liked) Res.string.favorites_remove_track else Res.string.favorites_add_track),
        if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, enabled = favorite.canChange,
        onClick = { onSetFavorite(!liked) }))
    add(ListItemMenuAction("playlist", uiText(Res.string.playlist_add_track), Icons.Rounded.Add,
        enabled = onAddToPlaylist != null, onClick = { onAddToPlaylist?.invoke() }))
    if (onArtistClick != null) {
        add(ListItemMenuAction("artist", uiText(Res.string.track_view_artist), Icons.Rounded.RecentActors, onClick = onArtistClick))
    }
    if (onAlbumClick != null) {
        add(ListItemMenuAction("album", uiText(Res.string.track_view_album), Icons.Rounded.Album, onClick = onAlbumClick))
    }
    add(ListItemMenuAction("info", uiText(Res.string.lyrics_share_track_information), Icons.Rounded.Info, onClick = onTrackInfo))
    if (onOfflineDownload != null) {
        add(ListItemMenuAction("offline", uiText(Res.string.player_offline_downloads_title), Icons.Rounded.Download, onClick = onOfflineDownload))
    }
}
