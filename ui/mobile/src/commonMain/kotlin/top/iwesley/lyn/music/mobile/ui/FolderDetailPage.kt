package top.iwesley.lyn.music.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.feature.favorites.FavoritesIntent
import top.iwesley.lyn.music.feature.favorites.FavoritesState
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.library.LibraryState
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.ui.mainShellColors

internal class FolderDetailEntryState {
    var list = LazyListState()
}

@Composable
internal fun FolderDetailPage(
    route: MusicDetailRoute,
    navigation: MusicDetailNavigation,
    state: LibraryState,
    favorites: FavoritesState,
    importState: ImportState,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    showDuration: Boolean,
) {
    val entry = navigation.folderEntry(route)
    val list = rememberSaveable(saver = LazyListState.Saver) { entry.list }
    SideEffect { entry.list = list }
    val tree = remember(state.filteredTracks, state.sourceLabelsById) {
        deriveLibraryFolderTree(state.filteredTracks, state.sourceLabelsById)
    }
    val sourceId = checkNotNull(route.sourceId)
    val key = LibraryFolderKey(sourceId, checkNotNull(route.folderPath))
    val folder = tree.nodesByKey[key]
    val children = tree.childFoldersByKey[key].orEmpty()
    val tracks = tree.directTracksByKey[key].orEmpty()
    val source = importState.sources.firstOrNull { it.source.id == sourceId }?.source
    val unavailable = !state.isLoadingContent && (sourceId !in state.sourceLabelsById ||
        source?.let { !it.enabled || it.indexMode == ImportSourceIndexMode.ONLINE } == true)
    val title = folder?.name ?: route.title.orEmpty()
    val sourceLabel = state.sourceLabelsById[sourceId] ?: source?.label ?: sourceId
    val path = if (key.path.isEmpty()) sourceLabel else "$sourceLabel / ${key.path.replace("/", " / ")}"
    val generation = navigation.generation
    fun back() = navigation.pop(route.entryId, generation)
    fun open(child: LibraryFolderNode) {
        if (navigation.top.entryId == route.entryId && navigation.generation == generation) {
            navigation.openFolder(child, state.selectedSourceFilter)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("music-detail-page-folder")) {
        val padding = if (maxWidth >= 600.dp) 32.dp else 20.dp
        val wideRows = showDuration && maxWidth - padding * 2 >= 520.dp * maxOf(1f, LocalDensity.current.fontScale)
        val indexWidth = rememberListIndexColumnWidth(tracks.size)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = padding)
                .testTag("folder-toolbar"), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, uiString(Res.string.common_back)) }
                Text(title, Modifier.weight(1f).padding(vertical = 8.dp), textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(48.dp))
            }
            favorites.message?.let { message ->
                Box(Modifier.fillMaxWidth().padding(start = padding, end = padding, bottom = 12.dp)) {
                    BannerCard(message, onDismiss = { onFavoritesIntent(FavoritesIntent.ClearMessage) })
                }
            }
            if (unavailable || folder == null) {
                // State pages use their own list so they cannot replace the saved content anchor.
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag(
                    if (state.isLoadingContent) "folder-loading" else "folder-empty"),
                    contentPadding = dockContentPadding(PaddingValues(horizontal = padding, vertical = 20.dp))) {
                    item {
                        when {
                            state.isLoadingContent -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                Text(uiString(Res.string.music_detail_loading))
                            }
                            unavailable -> Text(uiString(Res.string.music_detail_source_unavailable))
                            else -> EmptyStateCard(uiString(Res.string.library_folder_direct_tracks_empty),
                                uiString(Res.string.library_folder_tracks_filter_empty_hint))
                        }
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(Modifier.fillMaxSize().testTag("folder-content"), state = list,
                        contentPadding = dockContentPadding(PaddingValues(start = padding, end = padding, bottom = 20.dp))) {
                        item("path") {
                            Text(path, Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp).testTag("folder-path"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        if (children.isNotEmpty()) {
                            item("folders-heading") { FolderSectionHeading(uiString(Res.string.library_folders_title), "folder-folders-heading") }
                            itemsIndexed(children, key = { _, child -> "folder:${child.key.stableId}" }) { index, child ->
                                FolderGroupedRow(index, children.size) {
                                    LibraryFolderRow(child, showDivider = index < children.lastIndex, onClick = { open(child) })
                                }
                            }
                        }
                        if (tracks.isNotEmpty()) {
                            item("songs-heading") {
                                Column(Modifier.padding(top = if (children.isNotEmpty()) 20.dp else 0.dp)) {
                                    FolderSectionHeading(uiString(Res.string.library_tracks_title), "folder-songs-heading")
                                    TrackListActions(uiString(Res.plurals.common_track_count, tracks.size, tracks.size),
                                        uiString(Res.string.player_play_all),
                                        onPlay = { onPlayerIntent(PlayerIntent.PlayTracks(tracks, 0)) },
                                        modifier = Modifier.padding(bottom = 8.dp), playModifier = Modifier.testTag("folder-play-all"))
                                }
                            }
                            itemsIndexed(tracks, key = { _, track -> "track:${track.sourceId.length}:${track.sourceId}:${track.id}" }) { index, track ->
                                val targets = deriveTrackLibraryNavigationTargets(track, state.selectedSourceFilter)
                                FolderGroupedRow(index, tracks.size, Modifier.padding(horizontal = 12.dp)) {
                                    TrackRow(track, index, isFavorite = track.id in favorites.favoriteTrackIds,
                                        onToggleFavorite = { onFavoritesIntent(FavoritesIntent.ToggleFavorite(track)) },
                                        menuContextKey = route.entryId, showDuration = wideRows, indexColumnWidth = indexWidth,
                                        showDivider = index < tracks.lastIndex,
                                        onArtistClick = targets.artistTarget?.let { target -> { navigation.open(target) } },
                                        onAlbumClick = targets.albumTarget?.let { target -> { navigation.open(target) } },
                                        onClick = { onPlayerIntent(PlayerIntent.PlayTracks(tracks, index)) })
                                }
                            }
                        }
                    }
                    LibraryFastScrollbar(listState = list, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                        .padding(end = 6.dp, bottom = LocalDockBottomInset.current + 20.dp))
                }
            }
        }
    }
}

@Composable
internal fun FolderSectionHeading(title: String, tag: String) {
    Text(title, Modifier.padding(start = 12.dp, bottom = 10.dp).testTag(tag),
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
}

/** Each row remains a lazy item; only the first and last row round the group corners. */
@Composable
internal fun FolderGroupedRow(index: Int, count: Int, contentModifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(topStart = if (index == 0) 20.dp else 0.dp,
        topEnd = if (index == 0) 20.dp else 0.dp,
        bottomStart = if (index == count - 1) 20.dp else 0.dp,
        bottomEnd = if (index == count - 1) 20.dp else 0.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(mainShellColors.cardContainer).then(contentModifier)) { content() }
}

@Composable
internal fun LibraryFolderRow(folder: LibraryFolderNode, showDivider: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp).testTag("folder-row:${folder.key.stableId}"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Rounded.FolderOpen, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(folder.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(libraryFolderSummaryLabel(folder), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
        }
        if (showDivider) Box(Modifier.fillMaxWidth().padding(start = 54.dp, end = 12.dp).height(1.dp)
            .background(mainShellColors.cardBorder))
    }
}
