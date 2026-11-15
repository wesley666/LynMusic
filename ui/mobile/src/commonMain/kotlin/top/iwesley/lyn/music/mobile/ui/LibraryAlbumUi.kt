package top.iwesley.lyn.music.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.ui.mainShellColors
import kotlin.math.roundToInt

internal enum class AlbumViewMode { Grid, List }

@Stable
internal class LibraryAlbumBrowserState(
    val viewMode: MutableState<AlbumViewMode> = mutableStateOf(AlbumViewMode.Grid),
    val grid: LazyGridState = LazyGridState(),
    val list: LazyListState = LazyListState(),
)

@Composable
internal fun rememberLibraryAlbumBrowserState(): LibraryAlbumBrowserState {
    val mode = rememberSaveable { mutableStateOf(AlbumViewMode.Grid) }
    val grid = rememberLazyGridState()
    val list = rememberLazyListState()
    return remember(mode, grid, list) { LibraryAlbumBrowserState(mode, grid, list) }
}

@Composable
internal fun AlbumViewMenu(state: LibraryAlbumBrowserState) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.testTag("album-view-menu")) {
            Icon(
                if (state.viewMode.value == AlbumViewMode.Grid) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.List,
                uiString(Res.string.library_album_view),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = mainShellColors.navContainer) {
            AlbumViewMenuItems(state) { expanded = false }
        }
    }
}

@Composable
internal fun AlbumViewMenuItems(state: LibraryAlbumBrowserState, onDismiss: () -> Unit) {
    AlbumViewMode.entries.forEach { mode ->
        DropdownMenuItem(
            modifier = Modifier.testTag("album-view-${mode.name}"),
            text = { Text(uiString(if (mode == AlbumViewMode.Grid) Res.string.library_grid_view else Res.string.library_list_view)) },
            leadingIcon = {
                Icon(if (mode == AlbumViewMode.Grid) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.List, null)
            },
            trailingIcon = if (state.viewMode.value == mode) {
                { Icon(Icons.Rounded.Check, null) }
            } else null,
            onClick = {
                state.viewMode.value = mode
                onDismiss()
            },
        )
    }
}

@Composable
internal fun AlbumBrowserItem(
    grid: Boolean,
    album: Album,
    artworkLocator: String?,
    artworkCacheKey: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    if (grid) AlbumGridCard(album, artworkLocator, artworkCacheKey, enabled, onClick)
    else AlbumRow(album, artworkLocator, artworkCacheKey, enabled, onClick)
}

@Composable
internal fun AlbumGridCard(
    album: Album,
    artworkLocator: String?,
    artworkCacheKey: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("album-grid-card-${album.id}")
            .clickable(enabled = enabled, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LynArtworkImage(
            artworkLocator = artworkLocator,
            artworkCacheKey = artworkCacheKey,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                .testTag("album-grid-cover-${album.id}")
                .clip(RoundedCornerShape(12.dp))
                .background(mainShellColors.cardContainer),
            contentScale = ContentScale.Crop,
            maxDecodeSizePx = ArtworkDecodeSize.Card,
        )
        Text(
            text = album.title.ifBlank { uiString(Res.string.common_unknown_album) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("album-grid-title-${album.id}"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.artistName ?: uiString(Res.string.common_unknown_artist),
            modifier = Modifier.fillMaxWidth().testTag("album-grid-artist-${album.id}"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = uiString(Res.plurals.common_track_count_short, album.trackCount, album.trackCount),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Keep the browser's headers and status items shared between the list and grid.
internal class LibraryBrowserScope {
    class Entry(val count: Int, val fullSpan: Boolean, val key: ((Int) -> Any)?, val content: @Composable (Int) -> Unit)
    val entries = mutableListOf<Entry>()

    fun item(key: Any? = null, content: @Composable () -> Unit) {
        entries += Entry(1, true, key?.let { { _: Int -> it } }) { content() }
    }

    fun <T> items(items: List<T>, key: ((T) -> Any)? = null, content: @Composable (T) -> Unit) {
        entries += Entry(items.size, false, key?.let { { index: Int -> it(items[index]) } }) { content(items[it]) }
    }

    fun <T> itemsIndexed(items: List<T>, key: ((Int, T) -> Any)? = null, content: @Composable (Int, T) -> Unit) {
        entries += Entry(items.size, false, key?.let { { index: Int -> it(index, items[index]) } }) { content(it, items[it]) }
    }
}

@Composable
internal fun LibraryBrowserLayout(
    useGrid: Boolean,
    listState: LazyListState,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    listSpacing: Dp,
    modifier: Modifier = Modifier,
    content: LibraryBrowserScope.() -> Unit,
) {
    val scope = LibraryBrowserScope().apply(content)
    BoxWithConstraints(modifier) {
        if (useGrid) {
            val cells = if (maxWidth < 600.dp) GridCells.Fixed(2) else GridCells.Adaptive(160.dp)
            LazyVerticalGrid(
                columns = cells,
                state = gridState,
                modifier = Modifier.fillMaxSize().testTag("library-album-grid"),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                scope.entries.forEach { entry ->
                    items(entry.count, key = entry.key, span = { GridItemSpan(if (entry.fullSpan) maxLineSpan else 1) },
                        contentType = { entry.fullSpan }) { entry.content(it) }
                }
            }
            val headers = scope.entries.takeWhile { it.fullSpan }.sumOf { it.count }
            val albums = scope.entries.filterNot { it.fullSpan }.sumOf { it.count }
            val footers = scope.entries.sumOf { it.count } - headers - albums
            AlbumGridFastScrollbar(
                gridState, headers, albums, footers,
                Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    .padding(end = 8.dp, top = 20.dp, bottom = 20.dp + LocalDockBottomInset.current),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(listSpacing),
            ) {
                scope.entries.forEach { entry ->
                    items(entry.count, key = entry.key, contentType = { entry.fullSpan }) { entry.content(it) }
                }
            }
            LibraryFastScrollbar(
                listState,
                Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    .padding(end = 8.dp, top = 20.dp, bottom = 20.dp + LocalDockBottomInset.current),
            )
        }
    }
}

internal data class AlbumGridRows(val headers: Int, val albums: Int, val footers: Int, val columns: Int) {
    val albumRows = (albums + columns - 1) / columns
    val total = headers + albumRows + footers

    fun itemForRow(row: Int): Int = when {
        row < headers -> row
        row < headers + albumRows -> headers + (row - headers) * columns
        else -> headers + albums + row - headers - albumRows
    }.coerceIn(0, (headers + albums + footers - 1).coerceAtLeast(0))
}

@Composable
private fun AlbumGridFastScrollbar(state: LazyGridState, headers: Int, albums: Int, footers: Int, modifier: Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val layout = state.layoutInfo
    val rows = AlbumGridRows(headers, albums, footers, layout.maxSpan.coerceAtLeast(1))
    val visible = layout.visibleItemsInfo.filter { it.row >= 0 }
    val first = visible.firstOrNull { it.index == state.firstVisibleItemIndex }
    val firstItem = visible.firstOrNull { it.index == 0 }
    val lastItem = visible.maxByOrNull { it.index }
    // A short page is fully measured already. Use only this layout pass to preserve
    // positioning within the first row, without retaining any measurements.
    val shortRange = if (visible.size == layout.totalItemsCount && firstItem != null && lastItem != null) {
        val range = (lastItem.offset.y + lastItem.size.height - firstItem.offset.y +
            layout.beforeContentPadding + layout.afterContentPadding - layout.viewportSize.height).coerceAtLeast(0)
        range.takeIf { it < firstItem.size.height + layout.mainAxisItemSpacing }
    } else null
    val position = if (first == null) 0f else first.row + state.firstVisibleItemScrollOffset.toFloat() /
        (first.size.height + layout.mainAxisItemSpacing).coerceAtLeast(1)
    val progress = when {
        !state.canScrollBackward -> 0f
        !state.canScrollForward -> 1f
        shortRange != null -> (-firstItem!!.offset.y.toFloat() / shortRange.coerceAtLeast(1)).coerceIn(0f, 1f)
        else -> (position / (rows.total - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
    }
    val visibleFraction = if (shortRange != null) {
        layout.viewportSize.height.toFloat() / (layout.viewportSize.height + shortRange).coerceAtLeast(1)
    } else {
        visible.map { it.row }.distinct().size.toFloat() / rows.total.coerceAtLeast(1)
    }
    LibraryScrollbar(
        visibleFraction = visibleFraction.coerceIn(0.12f, 0.45f),
        scrollFraction = progress,
        canScroll = state.canScrollForward || state.canScrollBackward,
        modifier = modifier.testTag("library-album-scrollbar"),
        onScrollToFraction = { fraction ->
            coroutineScope.launch {
                when {
                    fraction <= 0f -> state.scrollToItem(0)
                    fraction >= 1f -> state.scrollToItem((layout.totalItemsCount - 1).coerceAtLeast(0))
                    shortRange != null -> state.scrollToItem(0, (fraction * shortRange).roundToInt())
                    else -> state.scrollToItem(rows.itemForRow((fraction * (rows.total - 1)).roundToInt()))
                }
            }
        },
    )
}
