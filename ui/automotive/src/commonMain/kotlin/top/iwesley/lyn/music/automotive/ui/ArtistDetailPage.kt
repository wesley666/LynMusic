package top.iwesley.lyn.music.automotive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    var headerHeightLimited by remember { mutableStateOf(false) }
    val headerScroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = headerState,
        canScroll = { !pagerState.isScrollInProgress && !headerHeightLimited },
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
    val title = route.title?.takeIf(String::isNotBlank)
        ?: tracks.firstNotNullOfOrNull { it.artistName?.takeIf(String::isNotBlank) }
        ?: uiString(Res.string.common_unknown_artist)
    val generation = navigation.generation
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .testTag("music-detail-page-artist")) {
        val padding = if (maxWidth >= 600.dp) 24.dp else 20.dp
        val fontScale = maxOf(1f, LocalDensity.current.fontScale)
        val wideRows = maxWidth - padding * 2 >= 520.dp * fontScale
        val availableHeight = (maxHeight - LocalDockBottomInset.current).coerceAtLeast(0.dp)
        val compactHeader = availableHeight < 420.dp
        val horizontalHeader = maxWidth >= 600.dp || availableHeight < 560.dp
        ArtistDetailLayout(
            modifier = Modifier.fillMaxSize().padding(horizontal = padding),
            dockInset = LocalDockBottomInset.current,
            toolbar = {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("artist-toolbar"), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navigation.pop(route.entryId, generation) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, uiString(Res.string.common_back))
                    }
                    Text(
                        if (showCompactTitle || headerHeightLimited) title else uiString(Res.string.music_detail_artist),
                        modifier = Modifier.weight(1f).testTag("artist-toolbar-title"),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            header = {
                CollapsingArtistHeader(headerState, onHeightLimited = { headerHeightLimited = it }) {
                    ArtistHeader(title, songsReady.thenCount(tracks.size), albumsReady.thenCount(albums.size), horizontalHeader, compactHeader)
                }
            },
            tabs = {
                ArtistTabs(selected, listOf(songsReady.thenCount(tracks.size), albumsReady.thenCount(albums.size),
                    songsReady.thenCount(favorites.size))) { tab ->
                    scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
                }
            },
            content = {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().testTag("artist-pager"),
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
                    LazyColumn(Modifier.fillMaxSize()
                        .then(if (headerHeightLimited) Modifier else Modifier.nestedScroll(headerScroll.nestedScrollConnection))
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
                                    TrackListActions(
                                        countLabel = uiString(Res.plurals.common_track_count, displayTracks.size, displayTracks.size),
                                        playLabel = uiString(if (tab == ArtistDetailTab.Favorites) Res.string.music_detail_play_favorites else Res.string.player_play_all),
                                        onPlay = { onPlayerIntent(PlayerIntent.PlayTracks(displayTracks, 0)) },
                                        modifier = Modifier.padding(vertical = 6.dp),
                                    )
                                } else item("empty") {
                                    ArtistEmptyState(uiString(if (tab == ArtistDetailTab.Favorites) Res.string.music_detail_favorites_empty else Res.string.library_detail_tracks_empty))
                                }
                                itemsIndexed(displayTracks, key = { _, track -> "track:${track.sourceId}:${track.id}" }) { index, track ->
                                    val targets = if (route.sourceId != null) deriveOnlineTrackLibraryNavigationTargets(track, route.sourceId)
                                        else deriveTrackLibraryNavigationTargets(track, route.librarySourceFilter)
                                    TrackRow(track, index, isFavorite = false, onToggleFavorite = {}, indexColumnWidth = indexWidth,
                                        menuContextKey = Triple(route.entryId, tab, selected), showDuration = wideRows,
                                        durationColumnWidth = 56.dp * fontScale,
                                        onArtistClick = targets.artistTarget?.let { target -> { navigation.open(target) } },
                                        onAlbumClick = targets.albumTarget?.let { target -> { navigation.open(target) } },
                                        onClick = { onPlayerIntent(PlayerIntent.PlayTracks(displayTracks, index)) })
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

/** Measure the fixed bars first so the expanded header cannot consume the usable list area. */
@Composable
private fun ArtistDetailLayout(
    modifier: Modifier,
    dockInset: Dp,
    toolbar: @Composable () -> Unit,
    header: @Composable () -> Unit,
    tabs: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Layout(content = {
        Box { toolbar() }
        Box { header() }
        Box { tabs() }
        Box { content() }
    }, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val toolbarPlaceable = measurables[0].measure(loose)
        val dockHeight = dockInset.roundToPx().coerceIn(0, constraints.maxHeight)
        val tabsPlaceable = measurables[2].measure(loose.copy(
            maxHeight = (constraints.maxHeight - toolbarPlaceable.height - dockHeight).coerceAtLeast(0)))
        val usable = (constraints.maxHeight - toolbarPlaceable.height - tabsPlaceable.height - dockHeight).coerceAtLeast(0)
        val headerBudget = (usable - 96.dp.roundToPx()).coerceAtLeast(0)
        val headerPlaceable = measurables[1].measure(loose.copy(maxHeight = headerBudget))
        val contentHeight = (constraints.maxHeight - toolbarPlaceable.height - headerPlaceable.height - tabsPlaceable.height).coerceAtLeast(0)
        val contentPlaceable = measurables[3].measure(loose.copy(minHeight = contentHeight, maxHeight = contentHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            toolbarPlaceable.placeRelative(0, 0)
            headerPlaceable.placeRelative(0, toolbarPlaceable.height)
            tabsPlaceable.placeRelative(0, toolbarPlaceable.height + headerPlaceable.height)
            contentPlaceable.placeRelative(0, toolbarPlaceable.height + headerPlaceable.height + tabsPlaceable.height)
        }
    }
}

private fun Boolean.thenCount(count: Int): Int? = if (this) count else null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsingArtistHeader(state: TopAppBarState, onHeightLimited: (Boolean) -> Unit, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth().clipToBounds()) { measurables, constraints ->
        // Measure the expanded header even when its visible height is zero.
        val header = measurables.single().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val limit = -header.height.toFloat()
        if (state.heightOffsetLimit != limit) {
            val fraction = state.collapsedFraction
            state.heightOffsetLimit = limit
            state.heightOffset = limit * fraction
        }
        // A temporary size constraint must not overwrite the user's saved collapse fraction.
        val heightLimited = header.height > constraints.maxHeight
        onHeightLimited(heightLimited)
        val visibleHeight = if (heightLimited) 0 else (header.height + state.heightOffset).roundToInt()
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
        ArtistAvatar(title.take(1), if (compact) 48.dp else if (horizontal) 80.dp else 88.dp)
    }
    val labels: @Composable () -> Unit = {
        Text(title, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(counts, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (horizontal) Row(Modifier.fillMaxWidth().testTag("artist-header-horizontal").padding(vertical = if (compact) 8.dp else 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically) {
        avatar()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { labels() }
    } else Column(Modifier.fillMaxWidth().testTag("artist-header-stacked").padding(top = 4.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        avatar()
        labels()
    }
}

@Composable
private fun ArtistAvatar(letter: String, size: Dp) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val baseStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
    val fittingStyle = remember(letter, size, density, baseStyle, measurer) {
        val available = with(density) { (size - 8.dp).roundToPx().coerceAtLeast(1) }
        val natural = measurer.measure(AnnotatedString(letter), style = baseStyle, maxLines = 1, softWrap = false)
        val required = maxOf(natural.size.width, natural.size.height).coerceAtLeast(1)
        val scale = if (required > available) (available - 1).coerceAtLeast(1).toFloat() / required else 1f
        baseStyle.copy(fontSize = baseStyle.fontSize * scale, lineHeight = baseStyle.lineHeight * scale)
    }
    Box(Modifier.size(size).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape)
        .testTag("artist-avatar"), contentAlignment = Alignment.Center) {
        Text(letter, modifier = Modifier.padding(4.dp).testTag("artist-avatar-letter"), style = fittingStyle,
            maxLines = 1, softWrap = false, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ArtistEmptyState(message: String) {
    Text(message, Modifier.fillMaxWidth().padding(vertical = 28.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistTabs(selected: ArtistDetailTab, counts: List<Int?>, onSelect: (ArtistDetailTab) -> Unit) {
    val tabs = ArtistDetailTab.entries
    val labels = tabs.map { tab ->
        val label = uiString(when (tab) {
            ArtistDetailTab.Songs -> Res.string.music_detail_songs
            ArtistDetailTab.Albums -> Res.string.music_detail_albums
            ArtistDetailTab.Favorites -> Res.string.music_detail_favorites
        })
        "$label ${counts[tab.ordinal] ?: "—"}"
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelLarge
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("artist-tabs"), contentAlignment = Alignment.BottomStart) {
        val available = minOf(maxWidth, 420.dp)
        val scrollable = labels.indices.any { index ->
            val textWidth = with(density) { measurer.measure(AnnotatedString(labels[index]), style).size.width.toDp() }
            textWidth + 32.dp + (if (tabs[index] == ArtistDetailTab.Favorites) 20.dp else 0.dp) > available / tabs.size
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        val content: @Composable () -> Unit = {
            tabs.forEach { tab ->
                Tab(selected = selected == tab, onClick = { onSelect(tab) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("artist-tab-${tab.name}"),
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                    content = {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (tab == ArtistDetailTab.Favorites) Icon(Icons.Rounded.Favorite, null, Modifier.size(16.dp))
                            Text(labels[tab.ordinal], maxLines = 1, softWrap = false, style = style)
                        }
                    })
            }
        }
        val modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()
        if (scrollable) PrimaryScrollableTabRow(selectedTabIndex = selected.ordinal,
            modifier = modifier.testTag("artist-tabs-scrollable"), edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.background,
            indicator = {
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(selected.ordinal, matchContentSize = true), height = 2.dp)
            }, divider = {}, tabs = content)
        else PrimaryTabRow(selectedTabIndex = selected.ordinal, modifier = modifier,
            containerColor = MaterialTheme.colorScheme.background,
            indicator = {
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(selected.ordinal, matchContentSize = true), height = 2.dp)
            }, divider = {}, tabs = content)
    }
}
