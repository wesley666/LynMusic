package top.iwesley.lyn.music.automotive.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.feature.library.*
import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.ui.mainShellColors
import kotlin.math.roundToInt

/** One grid belongs to the navigation owner, above either responsive shell. */
internal class LibraryAlbumGridState(val grid: LazyGridState = LazyGridState()) {
    var layout by mutableStateOf<List<Float>>(emptyList())

    // Only explicit search/source intents reset the position, never Store hydration.
    fun reset() { grid.requestScrollToItem(0) }

    companion object {
        val Saver = listSaver<LibraryAlbumGridState, Any>(
            save = { listOf(it.grid.firstVisibleItemIndex, it.grid.firstVisibleItemScrollOffset, it.layout) },
            restore = {
                LibraryAlbumGridState(LazyGridState(it[0] as Int, it[1] as Int)).apply {
                    @Suppress("UNCHECKED_CAST")
                    layout = it[2] as List<Float>
                }
            },
        )
    }
}

@Composable
internal fun rememberLibraryAlbumGridState() = rememberSaveable(saver = LibraryAlbumGridState.Saver) { LibraryAlbumGridState() }

internal fun albumGridContext(state: LibraryBrowserUiState): String = listOf(
    state.isOnline.toString(), state.sourceId.orEmpty(), encodeLibrarySourceFilter(state.selectedSourceFilter), state.query,
).joinToString("") { "${it.length}:$it" }

@Composable
internal fun LibraryAlbumGrid(
    state: LibraryBrowserUiState,
    page: LibraryAlbumGridState,
    tracksByAlbumId: Map<String?, List<Track>>,
    onOpenAlbum: (LibraryAlbumUiItem) -> Unit,
    onLoadMore: () -> Unit,
    onDismissMessage: () -> Unit,
    emptyContent: @Composable () -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    val context = albumGridContext(state)
    val grid = page.grid
    val status = remember(context) { LazyGridState() }
    val displayed = if (state.albums.isEmpty()) status else grid
    // Re-entering after a loading/empty page must use the saved index, not a stale
    // key from the previous data set (notably after online search debounce).
    DisposableEffect(displayed) {
        if (displayed === grid) grid.requestScrollToItem(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset)
        onDispose { }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val layout = listOf(maxWidth.value, density.density, density.fontScale)
        SideEffect {
            if (page.layout.isNotEmpty() && page.layout != layout) {
                // A different column/cover size can turn an old offset into skipped rows.
                grid.requestScrollToItem(grid.firstVisibleItemIndex)
            }
            page.layout = layout
        }
        LazyVerticalGrid(
            columns = if (maxWidth < 600.dp) GridCells.Fixed(2) else GridCells.Adaptive(160.dp),
            state = displayed,
            modifier = Modifier.fillMaxSize().testTag("automotive-library-grid-Albums"),
            contentPadding = dockContentPadding(PaddingValues(start = 20.dp, end = 28.dp, top = 20.dp, bottom = 20.dp)),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (header != null) item("album-header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") { header() }
            state.message?.let { message ->
                item("album-message", span = { GridItemSpan(maxLineSpan) }, contentType = "status") {
                    BannerCard(message, onDismissMessage)
                }
            }
            if (state.albums.isEmpty()) {
                item("album-status", span = { GridItemSpan(maxLineSpan) }, contentType = "status") { emptyContent() }
            }
            items(state.albums, key = { "$context:${it.id}" }, contentType = { "album" }) { item ->
                val fallback = tracksByAlbumId[item.album.id].orEmpty().firstOrNull()
                AlbumGridCard(
                    item = item,
                    artworkLocator = item.artworkLocator ?: fallback?.artworkLocator,
                    artworkCacheKey = if (item.artworkLocator == null) fallback?.let(::trackArtworkCacheKey) else null,
                    enabled = !state.isOnline || (state.sourceId?.isNotBlank() == true && item.remoteAlbumId?.isNotBlank() == true),
                    onClick = { onOpenAlbum(item) },
                )
            }
            if (state.capabilities.canLoadMoreAlbums) {
                item("album-more", span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
                    LibraryLoadMoreRow(state.isLoadingMoreAlbums, state.albumCount, onLoadMore, wrap = true)
                }
            }
        }
        AlbumGridFastScrollbar(
            displayed, (if (header != null) 1 else 0) + (if (state.message != null) 1 else 0) +
                (if (state.albums.isEmpty()) 1 else 0),
            state.albums.size, if (state.capabilities.canLoadMoreAlbums) 1 else 0,
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 8.dp, top = 20.dp,
                bottom = 20.dp + LocalDockBottomInset.current),
        )
    }
}

@Composable
private fun AlbumGridCard(
    item: LibraryAlbumUiItem, artworkLocator: String?, artworkCacheKey: String?, enabled: Boolean, onClick: () -> Unit,
) {
    val album = item.album
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("album-grid-card-${item.id}")
        .clickable(enabled = enabled, onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LynArtworkImage(
            artworkLocator = artworkLocator, artworkCacheKey = artworkCacheKey, contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).testTag("album-grid-cover-${item.id}")
                .clip(RoundedCornerShape(12.dp)).background(mainShellColors.cardContainer),
            contentScale = ContentScale.Crop, maxDecodeSizePx = ArtworkDecodeSize.Card,
        )
        Text(album.title.ifBlank { uiString(Res.string.common_unknown_album) },
            Modifier.fillMaxWidth().padding(top = 4.dp).testTag("album-grid-title-${item.id}"),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Text(album.artistName?.takeIf(String::isNotBlank) ?: uiString(Res.string.common_unknown_artist),
            Modifier.fillMaxWidth().testTag("album-grid-artist-${item.id}"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Text(uiString(Res.plurals.common_track_count_short, album.trackCount, album.trackCount), Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Full-span header/footer items count as one row each, albums count by column. */
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
    val scope = rememberCoroutineScope()
    val layout = state.layoutInfo
    if (!state.canScrollBackward && !state.canScrollForward) return
    val rows = AlbumGridRows(headers, albums, footers, layout.maxSpan.coerceAtLeast(1))
    val visible = layout.visibleItemsInfo.filter { it.row >= 0 }
    val first = visible.firstOrNull { it.index == state.firstVisibleItemIndex }
    val firstItem = visible.firstOrNull { it.index == 0 }
    val last = visible.maxByOrNull { it.index }
    val shortRange = if (visible.size == layout.totalItemsCount && firstItem != null && last != null) {
        val range = (last.offset.y + last.size.height - firstItem.offset.y + layout.beforeContentPadding +
            layout.afterContentPadding - layout.viewportSize.height).coerceAtLeast(0)
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
    val fraction = if (shortRange != null) layout.viewportSize.height.toFloat() /
        (layout.viewportSize.height + shortRange).coerceAtLeast(1)
        else visible.map { it.row }.distinct().size.toFloat() / rows.total.coerceAtLeast(1)
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun scrollTo(y: Float) {
        if (size.height <= 0) return
        val target = (y / size.height).coerceIn(0f, 1f)
        scope.launch {
            when {
                target <= 0f -> state.scrollToItem(0)
                target >= 1f -> state.scrollToItem((layout.totalItemsCount - 1).coerceAtLeast(0))
                shortRange != null -> state.scrollToItem(0, (target * shortRange).roundToInt())
                else -> state.scrollToItem(rows.itemForRow((target * (rows.total - 1)).roundToInt()))
            }
        }
    }
    val currentScroll by rememberUpdatedState<(Float) -> Unit>(::scrollTo)
    val thumb = size.height * fraction.coerceIn(.12f, .45f)
    Box(modifier.width(18.dp).testTag("library-album-scrollbar").onSizeChanged { size = it }
        .pointerInput(size) {
            detectVerticalDragGestures(onDragStart = { currentScroll(it.y) }, onVerticalDrag = { change, _ ->
                change.consume(); currentScroll(change.position.y)
            })
        }, contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxHeight().width(4.dp).clip(RoundedCornerShape(999.dp)).background(mainShellColors.cardBorder))
        Box(Modifier.offset(y = with(density) { ((size.height - thumb).coerceAtLeast(0f) * progress).toDp() })
            .height(with(density) { thumb.toDp() }).width(8.dp).clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.secondary)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSecondary.copy(alpha = .18f)), RoundedCornerShape(999.dp)))
    }
}
