package top.iwesley.lyn.music.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.feature.details.MusicDetailUnavailableException
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.resources.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArtistDetailPage(
    route: MusicDetailRoute,
    navigation: MusicDetailNavigation,
    onPlayerIntent: (PlayerIntent) -> Unit,
    showDuration: Boolean,
) {
    val state = navigation.entry(route)
    val tabs = ArtistDetailTab.entries
    val pagerState = rememberPagerState(initialPage = state.selectedArtistTab.ordinal) { tabs.size }
    val selected = tabs[pagerState.currentPage]
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page -> state.selectedArtistTab = tabs[page] }
    }
    val songsList = rememberSaveable(saver = LazyListState.Saver) { state.contentListState }
    val albumsList = rememberSaveable(saver = LazyListState.Saver) { state.albumListState }
    val favoritesList = rememberSaveable(saver = LazyListState.Saver) { state.favoriteListState }
    val headerState = rememberSaveable(saver = TopAppBarState.Saver) { state.artistHeaderState }
    val headerScroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = headerState,
        canScroll = { !pagerState.isScrollInProgress },
    )
    val showCompactTitle by remember(headerState) {
        derivedStateOf { headerState.collapsedFraction > 0.5f }
    }
    SideEffect {
        state.contentListState = songsList
        state.albumListState = albumsList
        state.favoriteListState = favoritesList
        state.artistHeaderState = headerState
    }
    val tracks = state.artistTracks.data.orEmpty()
    val albums = state.artistAlbums.data.orEmpty()
    val bindings = LocalTrackListActionBindings.current
    val favorites = tracks.filter { bindings.favoriteState?.invoke(it)?.isFavorite == true }
    val songsReady = !state.artistTracks.loading && state.artistTracks.error == null && state.artistTracks.data != null
    val albumsReady = !state.artistAlbums.loading && state.artistAlbums.error == null && state.artistAlbums.data != null
    val title = route.title?.takeIf(String::isNotBlank) ?: uiString(Res.string.common_unknown_artist)
    val generation = navigation.generation
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .testTag("music-detail-page-artist")) {
        val compactHeader = maxHeight < 420.dp
        val horizontalHeader = maxWidth >= 600.dp || maxHeight < 560.dp
        val padding = if (maxWidth >= 600.dp) 24.dp else 20.dp
        Column(Modifier.fillMaxSize().padding(horizontal = padding)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navigation.pop(route.entryId, generation) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, uiString(Res.string.common_back))
                }
                Text(
                    if (showCompactTitle) title else uiString(Res.string.music_detail_artist),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CollapsingArtistHeader(headerState) {
                ArtistHeader(title, songsReady.thenCount(tracks.size), albumsReady.thenCount(albums.size), horizontalHeader, compactHeader)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                PrimaryTabRow(selectedTabIndex = selected.ordinal,
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.background,
                    indicator = {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(selected.ordinal, matchContentSize = true),
                            height = 2.dp,
                        )
                    },
                    divider = {},
                ) {
                    ArtistDetailTab.entries.forEach { tab ->
                        val count = when (tab) {
                            ArtistDetailTab.Songs -> songsReady.thenCount(tracks.size)
                            ArtistDetailTab.Albums -> albumsReady.thenCount(albums.size)
                            ArtistDetailTab.Favorites -> songsReady.thenCount(favorites.size)
                        }
                        val label = uiString(when (tab) {
                            ArtistDetailTab.Songs -> Res.string.music_detail_songs
                            ArtistDetailTab.Albums -> Res.string.music_detail_albums
                            ArtistDetailTab.Favorites -> Res.string.music_detail_favorites
                        })
                        Tab(selected = selected == tab, onClick = { scope.launch { pagerState.animateScrollToPage(tab.ordinal) } },
                            modifier = Modifier.testTag("artist-tab-${tab.name}"),
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                            text = {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (tab == ArtistDetailTab.Favorites) Icon(Icons.Rounded.Favorite, null, Modifier.size(16.dp))
                                    Text("$label ${count ?: "—"}", maxLines = 1, style = MaterialTheme.typography.labelLarge)
                                }
                            })
                    }
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth().testTag("artist-pager"),
                key = { tabs[it].name },
            ) { page ->
                val tab = tabs[page]
                val displayTracks = if (tab == ArtistDetailTab.Favorites) favorites else tracks
                val loading = if (tab == ArtistDetailTab.Albums) state.artistAlbums.loading else state.artistTracks.loading
                val error = if (tab == ArtistDetailTab.Albums) state.artistAlbums.error else state.artistTracks.error
                val retry = if (tab == ArtistDetailTab.Albums) state.artistAlbums::retry else state.artistTracks::retry
                val statusList = rememberLazyListState()
                val indexWidth = rememberListIndexColumnWidth(displayTracks.size)
                val list = when (tab) {
                    ArtistDetailTab.Songs -> songsList
                    ArtistDetailTab.Albums -> albumsList
                    ArtistDetailTab.Favorites -> favoritesList
                }
                LazyColumn(Modifier.fillMaxSize().nestedScroll(headerScroll.nestedScrollConnection)
                    .testTag("artist-content-${tab.name}"), state = if (loading || error != null) statusList else list,
                    contentPadding = dockContentPadding(PaddingValues(top = 12.dp, bottom = 20.dp))) {
                    when {
                        loading -> item("loading") {
                            Row(Modifier.padding(vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                Text(uiString(Res.string.music_detail_loading))
                            }
                        }
                        error != null -> item("error") {
                            Column(Modifier.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(uiString(if (error is MusicDetailUnavailableException) Res.string.music_detail_source_unavailable else Res.string.music_detail_load_failed))
                                Button(onClick = retry) { Text(uiString(Res.string.common_retry)) }
                            }
                        }
                        tab == ArtistDetailTab.Albums -> {
                            if (albums.isEmpty()) item("empty") { ArtistEmptyState(uiString(Res.string.music_detail_albums_empty)) }
                            items(albums, key = { "album:${it.album.id}" }) { item ->
                                AlbumRow(item.album, item.artworkLocator, item.artworkCacheKey, onClick = {
                                    navigation.open(if (route.sourceId != null) LibraryNavigationTarget.OnlineAlbum(
                                        route.sourceId, item.album.id, item.album.title, item.album.artistName, item.artworkLocator,
                                    ) else LibraryNavigationTarget.Album(item.album.id, route.librarySourceFilter))
                                })
                            }
                        }
                        else -> {
                            if (displayTracks.isNotEmpty()) item("actions") {
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(uiString(Res.plurals.common_track_count, displayTracks.size, displayTracks.size),
                                        style = MaterialTheme.typography.bodyMedium)
                                    Button(onClick = { onPlayerIntent(PlayerIntent.PlayTracks(displayTracks, 0)) },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                                        Text(uiString(if (tab == ArtistDetailTab.Favorites) Res.string.music_detail_play_favorites else Res.string.player_play_all))
                                    }
                                }
                            } else item("empty") {
                                ArtistEmptyState(uiString(if (tab == ArtistDetailTab.Favorites) Res.string.music_detail_favorites_empty else Res.string.library_detail_tracks_empty))
                            }
                            itemsIndexed(displayTracks, key = { _, track -> "track:${track.sourceId}:${track.id}" }) { index, track ->
                                val targets = if (route.sourceId != null) deriveOnlineTrackLibraryNavigationTargets(track, route.sourceId)
                                    else deriveTrackLibraryNavigationTargets(track, route.librarySourceFilter)
                                TrackRow(track, index, isFavorite = false, onToggleFavorite = {}, indexColumnWidth = indexWidth,
                                    menuContextKey = Triple(route.entryId, tab, selected), showDuration = showDuration,
                                    onArtistClick = targets.artistTarget?.let { target -> { navigation.open(target) } },
                                    onAlbumClick = targets.albumTarget?.let { target -> { navigation.open(target) } },
                                    onClick = { onPlayerIntent(PlayerIntent.PlayTracks(displayTracks, index)) })
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Boolean.thenCount(count: Int): Int? = if (this) count else null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsingArtistHeader(state: TopAppBarState, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth().clipToBounds()) { measurables, constraints ->
        // Measure the expanded header even when its visible height is zero.
        val header = measurables.single().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val limit = -header.height.toFloat()
        if (state.heightOffsetLimit != limit) {
            val fraction = state.collapsedFraction
            state.heightOffsetLimit = limit
            state.heightOffset = limit * fraction
        }
        val visibleHeight = (header.height + state.heightOffset).roundToInt()
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(header.width, visibleHeight) {
            header.placeRelative(0, state.heightOffset.roundToInt())
        }
    }
}

@Composable
private fun ArtistHeader(title: String, trackCount: Int?, albumCount: Int?, horizontal: Boolean, compact: Boolean) {
    val counts = listOf(
        trackCount?.let { uiString(Res.plurals.common_track_count, it, it) } ?: "—",
        albumCount?.let { uiString(Res.plurals.common_album_count, it, it) } ?: "—",
    ).joinToString(" · ")
    val avatar: @Composable () -> Unit = {
        Box(Modifier.size(if (compact) 48.dp else if (horizontal) 80.dp else 88.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center) {
            Text(title.take(1), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
        }
    }
    val labels: @Composable () -> Unit = {
        Text(title, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(counts, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (horizontal) Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 8.dp else 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically) {
        avatar()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { labels() }
    } else Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        avatar()
        labels()
    }
}

@Composable
private fun ArtistEmptyState(message: String) {
    Text(message, Modifier.fillMaxWidth().padding(vertical = 28.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
