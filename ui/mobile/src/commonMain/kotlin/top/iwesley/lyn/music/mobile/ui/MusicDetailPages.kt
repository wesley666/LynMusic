package top.iwesley.lyn.music.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.feature.details.AlbumDetailData
import top.iwesley.lyn.music.feature.details.MusicDetailUnavailableException
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.resources.*

internal data class AlbumHeaderLayout(
    val split: Boolean,
    val contentWidth: Dp,
    val coverSize: Dp,
    val fontScale: Float,
) {
    companion object {
        val Saver = listSaver<AlbumHeaderLayout, Any>(
            save = { listOf(it.split, it.contentWidth.value, it.coverSize.value, it.fontScale) },
            restore = { AlbumHeaderLayout(it[0] as Boolean, (it[1] as Float).dp, (it[2] as Float).dp, it[3] as Float) },
        )
    }
}

@Composable
internal fun AlbumDetailPage(
    route: MusicDetailRoute,
    navigation: MusicDetailNavigation,
    onPlayerIntent: (PlayerIntent) -> Unit,
    showDuration: Boolean,
) {
    val state = navigation.entry(route)
    val list = rememberSaveable(saver = LazyListState.Saver) { state.contentListState }
    val currentGeneration = navigation.generation
    BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .testTag("music-detail-page-album"),
    ) {
        // The shell enables wide rows only in its tablet landscape layout.
        val split = showDuration && maxWidth >= 900.dp && maxHeight >= 500.dp
        val horizontalPadding = if (maxWidth >= 600.dp) 32.dp else 20.dp
        val coverSize = when {
            split -> minOf(288.dp, (maxWidth - horizontalPadding * 2 - 48.dp) * 0.34f)
            maxHeight < 420.dp -> 112.dp
            maxWidth >= 600.dp -> 220.dp
            else -> 176.dp
        }.coerceAtMost((maxWidth - horizontalPadding * 2).coerceAtLeast(0.dp))
        val headerLayout = AlbumHeaderLayout(
            split, maxWidth - horizontalPadding * 2, coverSize, LocalDensity.current.fontScale,
        )
        var previousHeaderLayout by rememberSaveable(stateSaver = AlbumHeaderLayout.Saver) {
            mutableStateOf(state.albumHeaderLayout ?: headerLayout)
        }
        val resetHeader = previousHeaderLayout != headerLayout && list.firstVisibleItemIndex == 0
        SideEffect {
            state.contentListState = list
            state.albumHeaderLayout = headerLayout
            previousHeaderLayout = headerLayout
            // Header heights differ; a partially scrolled hero must not become several skipped songs.
            if (resetHeader) list.requestScrollToItem(0)
        }
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = horizontalPadding).height(48.dp)
                    .testTag("album-toolbar"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navigation.pop(route.entryId, currentGeneration) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, uiString(Res.string.common_back))
                }
                Text(uiString(Res.string.library_albums_title), style = MaterialTheme.typography.titleMedium)
            }
            val data = state.data
            if (state.loading || state.error != null || data == null) {
                AlbumDetailStatus(state, Modifier.weight(1f).fillMaxWidth().padding(horizontal = horizontalPadding))
            } else {
                val playAll = { onPlayerIntent(PlayerIntent.PlayTracks(data.tracks, 0)) }
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = horizontalPadding)
                        .testTag(if (split) "album-content-split" else "album-content-stacked"),
                    horizontalArrangement = Arrangement.spacedBy(if (split) 48.dp else 0.dp),
                ) {
                    if (split) {
                        Column(
                            Modifier.width(coverSize).fillMaxHeight().testTag("album-summary-scroll")
                                .verticalScroll(rememberScrollState())
                                .padding(top = 24.dp, bottom = 20.dp),
                        ) {
                            AlbumSummary(data, coverSize, playAll)
                        }
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                        val wideRows = showDuration && maxWidth >= 520.dp * maxOf(1f, LocalDensity.current.fontScale)
                        val indexWidth = rememberListIndexColumnWidth(data.tracks.size)
                        LazyColumn(
                            Modifier.fillMaxSize().testTag("album-tracks"), state = list,
                            contentPadding = dockContentPadding(PaddingValues(top = if (split) 24.dp else 16.dp, bottom = 20.dp)),
                        ) {
                            // Exactly one header in either layout keeps all song indices stable.
                            item("album-header") {
                                if (split) AlbumTrackColumns(indexWidth, wideRows)
                                else AlbumSummary(data, coverSize, playAll)
                            }
                            itemsIndexed(data.tracks, key = { _, track -> "track:${track.sourceId}:${track.id}" }) { index, track ->
                                val targets = if (route.sourceId != null) deriveOnlineTrackLibraryNavigationTargets(track, route.sourceId)
                                    else deriveTrackLibraryNavigationTargets(track, route.librarySourceFilter)
                                TrackRow(
                                    track = track, index = index, isFavorite = false, onToggleFavorite = {},
                                    indexColumnWidth = indexWidth, menuContextKey = route.entryId,
                                    showDuration = wideRows,
                                    onArtistClick = targets.artistTarget?.let { target -> { navigation.open(target) } },
                                    onAlbumClick = targets.albumTarget?.let { target -> { navigation.open(target) } },
                                    onClick = { onPlayerIntent(PlayerIntent.PlayTracks(data.tracks, index)) },
                                )
                            }
                            if (data.tracks.isEmpty()) item("empty") {
                                Box(Modifier.padding(vertical = 20.dp).testTag("album-empty")) {
                                    EmptyStateCard(uiString(Res.string.library_detail_tracks_empty), "")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumDetailStatus(state: MusicDetailEntryState, modifier: Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState())
            .padding(dockContentPadding(PaddingValues(top = 24.dp, bottom = 20.dp)))
            .testTag(if (state.loading) "album-loading" else "album-error"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.loading) {
            CircularProgressIndicator()
            Text(uiString(Res.string.music_detail_loading))
        } else {
            EmptyStateCard(
                uiString(Res.string.library_detail_open_failed),
                uiString(if (state.error is MusicDetailUnavailableException) Res.string.music_detail_source_unavailable else Res.string.music_detail_load_failed),
            )
            Button(onClick = state::retry) { Text(uiString(Res.string.common_retry)) }
        }
    }
}

@Composable
private fun AlbumSummary(data: AlbumDetailData, coverSize: Dp, onPlayAll: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("album-summary"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LynArtworkImage(
            artworkLocator = data.artworkLocator,
            artworkCacheKey = data.artworkCacheKey,
            contentDescription = null,
            modifier = Modifier.size(coverSize).shadow(6.dp, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp)).testTag("album-cover"),
            maxDecodeSizePx = ArtworkDecodeSize.Preview,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            data.title.takeIf(String::isNotBlank) ?: uiString(Res.string.common_unknown_album),
            Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            data.artistName?.takeIf(String::isNotBlank) ?: uiString(Res.string.common_unknown_artist),
            Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(20.dp))
        TrackListActions(
            countLabel = uiString(Res.plurals.common_track_count, data.tracks.size, data.tracks.size),
            playLabel = uiString(Res.string.player_play_all),
            onPlay = onPlayAll.takeIf { data.tracks.isNotEmpty() },
            modifier = Modifier.testTag("album-actions"),
            playModifier = Modifier.testTag("album-play-all"),
        )
    }
}

@Composable
private fun AlbumTrackColumns(indexWidth: Dp, wideRows: Boolean) {
    if (!wideRows) {
        Text(uiString(Res.string.music_detail_songs), Modifier.fillMaxWidth().padding(bottom = 16.dp),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        return
    }
    // Match TrackRow's fixed index, artwork, gaps, duration and menu widths.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(bottom = 8.dp).testTag("album-columns"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(uiString(Res.string.music_detail_songs), Modifier.width(indexWidth + 52.dp + 14.dp),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1.45f))
        Text(uiString(Res.string.library_albums_title), Modifier.weight(1f).testTag("album-column-album"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            Text(uiString(Res.string.music_detail_duration), Modifier.width(56.dp).testTag("album-column-duration"),
                textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(48.dp))
        }
    }
}
