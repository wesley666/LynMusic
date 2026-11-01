package top.iwesley.lyn.music.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.feature.details.MusicDetailUnavailableException
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.resources.*

@Composable
internal fun AlbumDetailPage(
    route: MusicDetailRoute,
    navigation: MusicDetailNavigation,
    onPlayerIntent: (PlayerIntent) -> Unit,
    showDuration: Boolean,
) = MusicDetailPage(route, navigation, onPlayerIntent, showDuration)

@Composable
internal fun ArtistDetailPage(
    route: MusicDetailRoute,
    navigation: MusicDetailNavigation,
    onPlayerIntent: (PlayerIntent) -> Unit,
    showDuration: Boolean,
) = MusicDetailPage(route, navigation, onPlayerIntent, showDuration)

@Composable
private fun MusicDetailPage(
    route: MusicDetailRoute,
    navigation: MusicDetailNavigation,
    onPlayerIntent: (PlayerIntent) -> Unit,
    showDuration: Boolean,
) {
    val state = navigation.entry(route)
    val list = rememberSaveable(saver = LazyListState.Saver) { state.contentListState }
    SideEffect { state.contentListState = list }
    val currentGeneration = navigation.generation
    val back = { navigation.pop(route.entryId, currentGeneration) }
    val data = state.data
    val background = MaterialTheme.colorScheme.background
    if (state.loading || state.error != null || data == null) {
        Column(
            modifier = Modifier.fillMaxSize().testTag("music-detail-page-${route.kind}")
                .background(background).verticalScroll(rememberScrollState())
                .padding(dockContentPadding(PaddingValues(20.dp))),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DetailBackButton(onClick = back)
            if (state.loading) {
                CircularProgressIndicator()
                Text(uiString(Res.string.music_detail_loading))
            } else {
                EmptyStateCard(
                    title = uiString(Res.string.library_detail_open_failed),
                    body = uiString(if (state.error is MusicDetailUnavailableException) Res.string.music_detail_source_unavailable else Res.string.music_detail_load_failed),
                )
                Button(onClick = state::retry) { Text(uiString(Res.string.common_retry)) }
            }
        }
        return
    }
    val request = route.request()
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("music-detail-page-${route.kind}").background(background), state = list,
        contentPadding = dockContentPadding(PaddingValues(20.dp)), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("back") { DetailBackButton(onClick = back) }
        item("summary") {
            DetailSummaryCard(
                title = data.title.ifBlank { uiString(if (route.kind == "album") Res.string.common_unknown_album else Res.string.common_unknown_artist) },
                subtitle = if (route.kind == "album") data.artistName ?: uiString(Res.string.common_unknown_artist) else uiString(Res.string.library_artists_title),
                supportingText = if (route.kind == "artist" && route.sourceId != null) uiString(Res.plurals.common_album_count, data.albums.size, data.albums.size) else uiString(Res.plurals.common_track_count, data.tracks.size, data.tracks.size),
                artworkLocator = data.artworkLocator,
                artworkCacheKey = data.artworkCacheKey,
            )
        }
        if (data.tracks.isNotEmpty()) item("play") {
            TextButton(onClick = { onPlayerIntent(PlayerIntent.PlayTracks(data.tracks, 0)) }) { Text(uiString(Res.string.player_play_all)) }
        }
        if (data.albums.isNotEmpty()) {
            item("albums") { SectionTitle(uiString(Res.string.library_albums_title), "") }
            items(data.albums, key = { "album:${it.album.id}" }) { item ->
                AlbumRow(item.album, item.artworkLocator, artworkCacheKey = item.artworkCacheKey, onClick = {
                    navigation.open(if (route.sourceId != null) LibraryNavigationTarget.OnlineAlbum(
                        route.sourceId, item.album.id, item.album.title, item.album.artistName, item.artworkLocator,
                    ) else LibraryNavigationTarget.Album(item.album.id, request.sourceFilter))
                })
            }
        }
        if (data.tracks.isNotEmpty()) item("tracks") { SectionTitle(uiString(Res.string.library_tracks_title), "") }
        itemsIndexed(data.tracks, key = { _, track -> "track:${track.sourceId}:${track.id}" }) { index, track ->
            val targets = if (route.sourceId != null) deriveOnlineTrackLibraryNavigationTargets(track, route.sourceId)
                else deriveTrackLibraryNavigationTargets(track, request.sourceFilter)
            TrackRow(
                track = track, index = index, isFavorite = false, onToggleFavorite = {},
                menuContextKey = route.entryId,
                showDuration = showDuration,
                onArtistClick = targets.artistTarget?.let { target -> { navigation.open(target) } },
                onAlbumClick = targets.albumTarget?.let { target -> { navigation.open(target) } },
                onClick = { onPlayerIntent(PlayerIntent.PlayTracks(data.tracks, index)) },
            )
        }
        if (data.tracks.isEmpty() && data.albums.isEmpty()) item("empty") {
            EmptyStateCard(uiString(Res.string.library_detail_tracks_empty), "")
        }
    }
}
