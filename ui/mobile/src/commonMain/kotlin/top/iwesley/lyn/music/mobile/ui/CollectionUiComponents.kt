package top.iwesley.lyn.music.mobile.ui

import top.iwesley.lyn.music.*

import top.iwesley.lyn.music.feature.importing.folderSourceEndpointText
import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.plus
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.lastErrorUiText

import top.iwesley.lyn.music.core.model.uiPlural

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.displayLocalFolderReference
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.core.model.ImportScanPhase
import top.iwesley.lyn.music.core.model.ImportScanProgress
import top.iwesley.lyn.music.core.model.ImportScanSummary
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.OfflineDownload
import top.iwesley.lyn.music.core.model.OfflineDownloadStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.offlineDownloadSourceType
import top.iwesley.lyn.music.core.model.supportsOfflineDownload
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.feature.importing.formatImportScanSummary
import top.iwesley.lyn.music.feature.offline.OfflineDownloadIntent
import top.iwesley.lyn.music.feature.offline.batchDownloadSizeEstimateLabel
import top.iwesley.lyn.music.feature.offline.estimateBatchDownloadSize
import top.iwesley.lyn.music.feature.offline.estimatedNavidromeTranscodedSizeBytes
import top.iwesley.lyn.music.feature.offline.formatOfflineDownloadSizeLabel
import top.iwesley.lyn.music.ui.mainShellColors
import kotlin.math.max
import kotlin.math.roundToInt

internal enum class OfflineDownloadRowIndicatorState {
    Downloading,
    Downloaded,
}

internal fun offlineDownloadRowIndicatorState(download: OfflineDownload?): OfflineDownloadRowIndicatorState? {
    return when (download?.status) {
        OfflineDownloadStatus.Pending,
        OfflineDownloadStatus.Downloading -> OfflineDownloadRowIndicatorState.Downloading

        OfflineDownloadStatus.Completed -> {
            if (download.hasLocalFileReference) {
                OfflineDownloadRowIndicatorState.Downloaded
            } else {
                null
            }
        }

        OfflineDownloadStatus.Failed,
        null -> null
    }
}

@Composable
internal fun OfflineDownloadRowIndicator(state: OfflineDownloadRowIndicatorState) {
    when (state) {
        OfflineDownloadRowIndicatorState.Downloading -> {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.6.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OfflineDownloadRowIndicatorState.Downloaded -> {
            Icon(
                imageVector = Icons.Rounded.DownloadDone,
                contentDescription = uiString(Res.string.offline_downloaded_music_badge),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
internal fun BatchOperationButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
    ) {
        Icon(Icons.Rounded.Checklist, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
            text = uiString(Res.string.library_batch_actions),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TrackSelectionActionBar(
    selectedCount: Int,
    downloadSizeEstimateLabel: String,
    allVisibleSelected: Boolean,
    hasVisibleTracks: Boolean,
    onToggleSelectAll: () -> Unit,
    onDownloadSelected: () -> Unit,
    onCancelSelection: () -> Unit,
) {
    val shellColors = mainShellColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(shellColors.cardContainer)
            .border(BorderStroke(1.dp, shellColors.cardBorder), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = uiString(Res.plurals.library_selected_track_count, (selectedCount).toInt(), selectedCount),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onCancelSelection) {
                Text(uiString(Res.string.common_cancel))
            }
        }
        Text(
            text = uiString(Res.string.offline_estimated_size, downloadSizeEstimateLabel),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onToggleSelectAll,
                enabled = hasVisibleTracks,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (allVisibleSelected) uiString(Res.string.common_deselect_all) else uiString(Res.string.common_select_all),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = onDownloadSelected,
                enabled = selectedCount > 0,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = uiString(Res.string.offline_download_selected_tracks),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BatchDownloadQualityBottomSheet(
    selectedCount: Int,
    tracks: List<Track>,
    downloadsByTrackId: Map<String, OfflineDownload>,
    onQualitySelected: (NavidromeAudioQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appDensity = LocalDensity.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mainShellColors.navContainer,
    ) {
        CompositionLocalProvider(LocalDensity provides appDensity) {
            Column(modifier = Modifier.padding(bottom = 20.dp)) {
                Text(
                    text = uiString(Res.string.offline_download_quality_title),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = uiString(Res.plurals.offline_download_track_count, (selectedCount).toInt(), selectedCount),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                NavidromeAudioQuality.entries.forEach { quality ->
                    DropdownMenuItem(
                        text = { Text(navidromeQualityLabel(quality)) },
                        leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                        trailingIcon = {
                            DownloadMenuTrailingSizeText(
                                batchDownloadSizeEstimateLabel(
                                    estimateBatchDownloadSize(
                                        tracks = tracks,
                                        downloadsByTrackId = downloadsByTrackId,
                                        quality = quality,
                                    ),
                                ),
                            )
                        },
                        onClick = { onQualitySelected(quality) },
                    )
                }
            }
        }
    }
}

internal fun toggleTrackSelection(
    selectedTrackIds: List<String>,
    trackId: String,
): List<String> {
    return if (trackId in selectedTrackIds) {
        selectedTrackIds.filterNot { it == trackId }
    } else {
        selectedTrackIds + trackId
    }
}

internal fun pruneSelectedTrackIds(
    selectedTrackIds: List<String>,
    visibleTracks: List<Track>,
): List<String> {
    val visibleTrackIds = visibleTracks.mapTo(mutableSetOf()) { it.id }
    return selectedTrackIds.filter { it in visibleTrackIds }
}

internal fun toggleAllVisibleTrackSelection(
    selectedTrackIds: List<String>,
    visibleTracks: List<Track>,
): List<String> {
    val visibleTrackIds = visibleTracks.map { it.id }
    if (visibleTrackIds.isEmpty()) return selectedTrackIds
    val visibleTrackIdSet = visibleTrackIds.toSet()
    val allVisibleSelected = visibleTrackIds.all { it in selectedTrackIds }
    return if (allVisibleSelected) {
        selectedTrackIds.filterNot { it in visibleTrackIdSet }
    } else {
        (selectedTrackIds + visibleTrackIds).distinct()
    }
}

internal fun selectedTracksInVisibleOrder(
    visibleTracks: List<Track>,
    selectedTrackIds: Collection<String>,
): List<Track> {
    val selectedTrackIdSet = selectedTrackIds.toSet()
    return visibleTracks.filter { it.id in selectedTrackIdSet }
}

internal fun hasNavidromeTracks(tracks: List<Track>): Boolean {
    return tracks.any { offlineDownloadSourceType(it).supportsSubsonicAudioQuality() }
}

private fun ImportSourceType?.supportsSubsonicAudioQuality(): Boolean {
    return this == ImportSourceType.NAVIDROME || this == ImportSourceType.SUBSONIC
}

internal fun shouldHandleBatchSelectionRequest(
    requestKey: Int,
    lastHandledRequestKey: Int,
    supportsBatchDownload: Boolean,
    hasVisibleTracks: Boolean,
): Boolean {
    return requestKey > 0 &&
        requestKey > lastHandledRequestKey &&
        supportsBatchDownload &&
        hasVisibleTracks
}

@Composable
@ReadOnlyComposable
internal fun supportsBatchOfflineDownloadActions(): Boolean {
    return true
}

@Composable
fun FavoriteToggleButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    buttonSize: androidx.compose.ui.unit.Dp = 48.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(buttonSize),
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (isFavorite) uiString(Res.string.favorites_remove_track) else uiString(Res.string.favorites_add_track),
            tint = if (enabled) tint else tint.copy(alpha = 0.46f),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
internal fun DetailBackButton(
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(uiString(Res.string.common_back))
    }
}

@Composable
internal fun DetailSummaryCard(
    title: String,
    subtitle: String,
    supportingText: String,
    artworkLocator: String?,
    artworkCacheKey: String? = null,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = shellColors.cardContainer,
        ),
        border = BorderStroke(1.dp, shellColors.cardBorder),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtworkThumbnail(
                artworkLocator = artworkLocator,
                artworkCacheKey = artworkCacheKey,
                modifier = Modifier.size(68.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = supportingText,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
internal fun AlbumRow(
    album: Album,
    artworkLocator: String?,
    artworkCacheKey: String? = null,
    onClick: () -> Unit,
) {
    val shellColors = mainShellColors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TrackArtworkThumbnail(
                artworkLocator = artworkLocator,
                artworkCacheKey = artworkCacheKey,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title.ifBlank { uiString(Res.string.common_unknown_album) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = album.artistName ?: uiString(Res.string.common_unknown_artist),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = uiString(Res.plurals.common_track_count_short, (album.trackCount).toInt(), album.trackCount),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 88.dp)
                .height(1.dp)
                .background(shellColors.cardBorder),
        )
    }
}

@Composable
internal fun ArtistRow(
    artist: Artist,
    trackCount: Int?,
    albumCount: Int?,
    onClick: () -> Unit,
) {
    val shellColors = mainShellColors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(shellColors.cardContainer)
                    .border(
                        border = BorderStroke(1.dp, shellColors.cardBorder),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name.ifBlank { uiString(Res.string.common_unknown_artist) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = artistSummaryLabel(trackCount, albumCount),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 88.dp)
                .height(1.dp)
                .background(shellColors.cardBorder),
        )
    }
}

internal fun artistSummaryLabelText(
    trackCount: Int?,
    albumCount: Int?,
    unknownLabel: String? = null,
): UiText {
    val parts = buildList {
        trackCount?.let { add(uiPlural(Res.plurals.common_track_count, (it).toInt(), it)) }
        albumCount?.let { add(uiPlural(Res.plurals.common_album_count, (it).toInt(), it)) }
    }
    return if (parts.isEmpty()) unknownLabel?.let(UiText::Raw) ?: uiText(Res.string.source_online_artist_label) else UiText.Joined(parts)
}

@Composable
internal fun artistSummaryLabel(
    trackCount: Int?,
    albumCount: Int?,
    unknownLabel: String? = null,
): String = artistSummaryLabelText(trackCount, albumCount, unknownLabel).displayText()

@Composable
internal fun TrackRow(
    track: Track,
    index: Int,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    showFavoriteIndicator: Boolean = true,
    menuContextKey: Any? = null,
    showDuration: Boolean = true,
    onArtistClick: (() -> Unit)? = null,
    onAlbumClick: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectionToggle: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shellColors = mainShellColors
    val showAlbumTitle = showDuration
    val artistClick = onArtistClick.takeIf { !selectionMode && showDuration && !track.artistName.isNullOrBlank() }
    val albumClick = onAlbumClick.takeIf { !selectionMode && showAlbumTitle && !track.albumTitle.isNullOrBlank() }
    val offlineDownload = LocalOfflineDownloadUiState.current.downloadsByTrackId[track.id]
    val offlineRowIndicatorState = offlineDownloadRowIndicatorState(offlineDownload)
    val rowClick = if (selectionMode) {
        onSelectionToggle ?: {}
    } else {
        onClick
    }
    val bindings = LocalTrackListActionBindings.current
    val favorite = bindings.favoriteState?.invoke(track) ?: TrackFavoriteUiState(isFavorite)
    var menuVisible by remember(track.sourceId, track.id, menuContextKey, selectionMode) { mutableStateOf(false) }
    var infoVisible by remember(track.sourceId, track.id, menuContextKey, selectionMode) { mutableStateOf(false) }
    var offlineVisible by remember(track.sourceId, track.id, menuContextKey, selectionMode) { mutableStateOf(false) }
    val canDownload = LocalOfflineDownloadUiState.current.onIntent != null && supportsOfflineDownload(track)
    val menuActions = trackListMenuActions(
        favorite = favorite,
        onSetFavorite = { value ->
            val action = bindings.onSetFavorite
            if (action != null) action(track, value) else onToggleFavorite()
        },
        onAddToPlaylist = bindings.onAddToPlaylist?.let { action -> { action(track) } },
        onTrackInfo = { infoVisible = true },
        onOfflineDownload = if (canDownload) ({ offlineVisible = true }) else null,
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        TrackActionContainer(
            track = track,
            onClick = rowClick,
            enableOfflineActions = !selectionMode,
            onOpenActions = if (selectionMode) null else ({ menuVisible = true }),
            offlineActionsRequested = offlineVisible,
            onOfflineActionsDismiss = { offlineVisible = false },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .padding(start = 14.dp, top = 12.dp, end = 0.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onSelectionToggle?.invoke() },
                    modifier = Modifier.size(32.dp),
                )
            } else {
                Text(
                    (index + 1).toString().padStart(2, '0'),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
            TrackArtworkThumbnail(
                artworkLocator = track.artworkLocator,
                artworkCacheKey = trackArtworkCacheKey(track),
            )
            Column(modifier = Modifier.weight(if (showAlbumTitle) 1.45f else 1f)) {
                Text(
                    track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        track.artistName ?: uiString(Res.string.common_unknown_artist),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .then(artistClick?.let { Modifier.combinedClickable(onClick = it, onLongClick = { menuVisible = true }) } ?: Modifier),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (showFavoriteIndicator && favorite.isKnown && favorite.isFavorite) TrackFavoriteIndicator()
                    offlineRowIndicatorState?.let { OfflineDownloadRowIndicator(it) }
                }
            }
            if (showAlbumTitle) {
                Box(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.albumTitle?.trim()?.takeIf { it.isNotEmpty() } ?: uiString(Res.string.common_unknown_album),
                        modifier = albumClick?.let { Modifier.combinedClickable(onClick = it, onLongClick = { menuVisible = true }) } ?: Modifier,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showDuration) {
                    Text(
                        formatDuration(track.durationMs),
                        modifier = Modifier.width(56.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    )
                }
                if (!selectionMode) {
                    Box {
                        ListItemMoreButton(uiString(Res.string.track_more_actions, track.title)) { menuVisible = true }
                        ListItemActionMenu(
                            title = track.title,
                            actions = menuActions,
                            presentation = ListItemMenuPresentation.BottomSheet,
                            expanded = menuVisible,
                            onDismiss = { menuVisible = false },
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 88.dp)
                .height(1.dp)
                .background(shellColors.cardBorder),
        )
    }
    if (infoVisible) PlayerTrackInfoDialog(track = track, onDismiss = { infoVisible = false })
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TrackActionContainer(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enableOfflineActions: Boolean = true,
    onOpenActions: (() -> Unit)? = null,
    offlineActionsRequested: Boolean = false,
    onOfflineActionsDismiss: () -> Unit = {},
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit,
) {
    val offlineUiState = LocalOfflineDownloadUiState.current
    val onOfflineIntent = offlineUiState.onIntent
    val supportsOfflinePlatform = true
    val supportsOfflineActions = enableOfflineActions && supportsOfflinePlatform && onOfflineIntent != null &&
        supportsOfflineDownload(track)
    val supportsActions = supportsOfflineActions || onOpenActions != null
    val download = offlineUiState.downloadsByTrackId[track.id]
    val touchOfflineUi = true
    val currentOpenActions by rememberUpdatedState(onOpenActions)
    var desktopMenuExpanded by remember(track.sourceId, track.id) { mutableStateOf(false) }
    var mobileSheetVisible by remember(track.sourceId, track.id) { mutableStateOf(false) }
    val interactionModifier = if (!supportsActions) {
        Modifier.clickable(onClick = onClick)
    } else if (touchOfflineUi) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { if (onOpenActions != null) onOpenActions() else mobileSheetVisible = true },
        )
    } else {
        Modifier
            .pointerInput(track.sourceId, track.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            event.changes.forEach { it.consume() }
                            currentOpenActions?.invoke() ?: run { desktopMenuExpanded = true }
                        }
                    }
                }
            }
            .clickable(onClick = onClick)
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = modifier.then(interactionModifier),
            verticalAlignment = verticalAlignment,
            horizontalArrangement = horizontalArrangement,
            content = content,
        )
        if (supportsOfflineActions && !touchOfflineUi && (desktopMenuExpanded || offlineActionsRequested)) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { desktopMenuExpanded = false; onOfflineActionsDismiss() },
                containerColor = mainShellColors.navContainer,
            ) {
                TrackOfflineActionMenuItems(
                    track = track,
                    download = download,
                    onIntent = onOfflineIntent,
                    onDismiss = { desktopMenuExpanded = false; onOfflineActionsDismiss() },
                )
            }
        }
    }
    if (supportsOfflineActions && touchOfflineUi && (mobileSheetVisible || offlineActionsRequested)) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val appDensity = LocalDensity.current
        ModalBottomSheet(
            onDismissRequest = { mobileSheetVisible = false; onOfflineActionsDismiss() },
            sheetState = sheetState,
            containerColor = mainShellColors.navContainer,
        ) {
            CompositionLocalProvider(LocalDensity provides appDensity) {
                Column(modifier = Modifier.padding(bottom = 20.dp)) {
                    Text(
                        text = track.title,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                    )
                    TrackOfflineActionMenuItems(
                        track = track,
                        download = download,
                        onIntent = onOfflineIntent,
                        onDismiss = { mobileSheetVisible = false; onOfflineActionsDismiss() },
                    )
                }
            }
        }
    }
}

@Composable
fun TrackOfflineActionMenuItems(
    track: Track,
    download: OfflineDownload?,
    onIntent: (OfflineDownloadIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    val offlineUiState = LocalOfflineDownloadUiState.current
    LaunchedEffect(track.id) {
        onIntent(OfflineDownloadIntent.RefreshAvailableSpace)
    }
    DownloadMenuAvailableSpaceItem(
        label = offlineAvailableSpaceLabel(
            availableSpaceBytes = offlineUiState.availableSpaceBytes,
            loading = offlineUiState.availableSpaceLoading,
        ),
    )
    val sourceType = offlineDownloadSourceType(track)
    val status = download?.status
    if (status == OfflineDownloadStatus.Pending || status == OfflineDownloadStatus.Downloading) {
        DropdownMenuItem(
            text = { Text(uiString(Res.string.offline_cancel_download)) },
            leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
            trailingIcon = { DownloadMenuTrailingSizeText(offlineDownloadProgressSizeLabel(download)) },
            onClick = {
                onDismiss()
                onIntent(OfflineDownloadIntent.Cancel(track.id))
            },
        )
        return
    }
    if (sourceType.supportsSubsonicAudioQuality()) {
        NavidromeAudioQuality.entries.forEach { quality ->
            val isCurrentOfflineQuality = isCurrentOfflineDownloadQuality(download, quality)
            val currentOfflineQualityColor = MaterialTheme.colorScheme.primary
            DropdownMenuItem(
                text = {
                    Text(
                        navidromeDownloadMenuLabel(quality, download),
                        color = if (isCurrentOfflineQuality) currentOfflineQualityColor else Color.Unspecified,
                    )
                },
                leadingIcon = {
                    Icon(
                        if (isCurrentOfflineQuality) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                        contentDescription = null,
                        tint = if (isCurrentOfflineQuality) currentOfflineQualityColor else LocalContentColor.current,
                    )
                },
                trailingIcon = { DownloadMenuTrailingSizeText(downloadMenuTrailingSizeLabel(track, download, quality)) },
                enabled = !isCurrentOfflineQuality,
                onClick = {
                    onDismiss()
                    onIntent(OfflineDownloadIntent.Download(track, quality))
                },
            )
        }
    } else {
        DropdownMenuItem(
            text = {
                Text(
                    when (status) {
                        OfflineDownloadStatus.Completed -> uiString(Res.string.offline_available_badge)
                        OfflineDownloadStatus.Failed -> uiString(Res.string.offline_retry_download)
                        else -> uiString(Res.string.offline_download_for_playback)
                    },
                )
            },
            leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
            trailingIcon = { DownloadMenuTrailingSizeText(downloadMenuTrailingSizeLabel(track, download)) },
            enabled = status != OfflineDownloadStatus.Completed,
            onClick = {
                onDismiss()
                onIntent(OfflineDownloadIntent.Download(track))
            },
        )
    }
    if (download?.hasLocalFileReference == true || status == OfflineDownloadStatus.Completed) {
        DropdownMenuItem(
            text = { Text(uiString(Res.string.storage_delete_offline_music), color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            onClick = {
                onDismiss()
                onIntent(OfflineDownloadIntent.Delete(track.id))
            },
        )
    }
}

@Composable
private fun DownloadMenuAvailableSpaceItem(label: String) {
    DropdownMenuItem(
        text = {
            Text(
                text = uiString(Res.string.offline_destination_free_space),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingIcon = { DownloadMenuTrailingSizeText(label) },
        enabled = false,
        onClick = {},
    )
}

@Composable
private fun DownloadMenuTrailingSizeText(label: String) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
    )
}

internal fun navidromeDownloadMenuLabelText(
    quality: NavidromeAudioQuality,
    download: OfflineDownload?,
): UiText {
    val prefix = when {
        isCurrentOfflineDownloadQuality(download, quality) -> uiText(Res.string.offline_downloaded_status)
        download?.status == OfflineDownloadStatus.Completed -> uiText(Res.string.offline_download_again)
        else -> uiText(Res.string.common_download)
    }
    return UiText.Joined(listOf(prefix, navidromeQualityText(quality)), " ")
}

@Composable
internal fun navidromeDownloadMenuLabel(
    quality: NavidromeAudioQuality,
    download: OfflineDownload?,
): String = navidromeDownloadMenuLabelText(quality, download).displayText()

internal fun isCurrentOfflineDownloadQuality(
    download: OfflineDownload?,
    quality: NavidromeAudioQuality,
): Boolean {
    return download?.status == OfflineDownloadStatus.Completed &&
        download.hasLocalFileReference &&
        download.quality == quality
}

private fun navidromeQualityText(quality: NavidromeAudioQuality): UiText = when (quality) {
    NavidromeAudioQuality.Original -> uiText(Res.string.player_original_quality)
    NavidromeAudioQuality.Kbps320 -> UiText.Raw("320 kbps")
    NavidromeAudioQuality.Kbps192 -> UiText.Raw("192 kbps")
    NavidromeAudioQuality.Kbps128 -> UiText.Raw("128 kbps")
}
@Composable
private fun navidromeQualityLabel(quality: NavidromeAudioQuality): String = navidromeQualityText(quality).displayText()

@Composable
private fun downloadMenuTrailingSizeLabel(
    track: Track,
    download: OfflineDownload?,
    quality: NavidromeAudioQuality? = null,
): String {
    val completedSize = download
        ?.takeIf { it.status == OfflineDownloadStatus.Completed && (quality == null || it.quality == quality) }
        ?.downloadedBytes
        ?.takeIf { it > 0L }
    if (completedSize != null) {
        return formatOfflineDownloadSize(completedSize)
    }
    if (quality != null && quality != NavidromeAudioQuality.Original) {
        return estimatedNavidromeTranscodedSizeBytes(track, quality)
            ?.let { uiString(Res.string.about_named_app, formatOfflineDownloadSize(it)) }
            ?: uiString(Res.string.common_unknown)
    }
    return track.sizeBytes.takeIf { it > 0L }?.let(::formatOfflineDownloadSize) ?: uiString(Res.string.common_unknown)
}

private fun offlineDownloadProgressSizeLabel(download: OfflineDownload?): String {
    val downloaded = formatOfflineDownloadSize(download?.downloadedBytes?.coerceAtLeast(0L) ?: 0L)
    val total = download?.totalBytes?.takeIf { it > 0L }?.let(::formatOfflineDownloadSize)
    return if (total == null) {
        downloaded
    } else {
        "$downloaded / $total"
    }
}

internal fun offlineAvailableSpaceLabelText(
    availableSpaceBytes: Long?,
    loading: Boolean,
): UiText {
    return when {
        loading -> uiText(Res.string.common_calculating)
        availableSpaceBytes == null -> uiText(Res.string.common_unknown)
        else -> UiText.Raw(formatOfflineAvailableSpaceGb(availableSpaceBytes))
    }
}

@Composable
internal fun offlineAvailableSpaceLabel(
    availableSpaceBytes: Long?,
    loading: Boolean,
): String = offlineAvailableSpaceLabelText(availableSpaceBytes, loading).displayText()

internal fun formatOfflineAvailableSpaceGb(sizeBytes: Long): String {
    val gigabytes = sizeBytes.coerceAtLeast(0L).toDouble() / 1024.0 / 1024.0 / 1024.0
    return "${(gigabytes * 10).roundToInt() / 10.0} GB"
}

private fun formatOfflineDownloadSize(sizeBytes: Long): String = formatOfflineDownloadSizeLabel(sizeBytes)

@Composable
internal fun TrackArtworkThumbnail(
    artworkLocator: String?,
    artworkCacheKey: String? = null,
    modifier: Modifier = Modifier.size(52.dp),
) {
    val shellColors = mainShellColors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(1.dp))
            .background(shellColors.cardContainer)
            .border(
                border = BorderStroke(1.dp, shellColors.cardBorder),
                shape = RoundedCornerShape(1.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        LynArtworkImage(
            artworkLocator = artworkLocator,
            contentDescription = null,
            artworkCacheKey = artworkCacheKey,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            maxDecodeSizePx = ArtworkDecodeSize.Thumbnail,
        )
    }
}

@Composable
internal fun MainShellElevatedCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shellColors = mainShellColors
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = shellColors.cardContainer),
        border = BorderStroke(1.dp, shellColors.cardBorder),
        content = content,
    )
}

@Composable
internal fun MainShellAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val shellColors = mainShellColors
    AssistChip(
        onClick = onClick,
        label = label,
        leadingIcon = leadingIcon,
        enabled = enabled,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = shellColors.navContainer,
            labelColor = MaterialTheme.colorScheme.onSurface,
            leadingIconContentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = shellColors.cardContainer,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = BorderStroke(1.dp, shellColors.cardBorder),
    )
}

@Composable
internal fun SourceCard(
    state: top.iwesley.lyn.music.core.model.SourceWithStatus,
    enabled: Boolean,
    compact: Boolean,
    onEdit: (() -> Unit)?,
    editLabel: String = uiString(Res.string.common_edit),
    onToggleEnabled: () -> Unit,
    onRescan: (() -> Unit)?,
    isRescanning: Boolean,
    onDelete: () -> Unit,
    scanSummary: ImportScanSummary? = null,
    scanProgress: ImportScanProgress? = null,
    onShowScanFailures: ((ImportScanSummary) -> Unit)? = null,
) {
    val shellColors = mainShellColors
    val sourceEnabled = state.source.enabled
    val sourceIndexMode = state.source.indexMode
    val isOnlineSource = sourceIndexMode == ImportSourceIndexMode.ONLINE
    val scanSummaryPresentation = buildSourceScanSummaryPresentation(
        summary = scanSummary,
        canShowFailures = onShowScanFailures != null,
        isOnlineSource = isOnlineSource,
        remoteTrackCount = state.indexState?.remoteTrackCount,
    )
    ElevatedCard(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = shellColors.cardContainer),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.source.label, fontWeight = FontWeight.Bold)
                    Text(
                        when (state.source.type) {
                            top.iwesley.lyn.music.core.model.ImportSourceType.LOCAL_FOLDER ->
                                displayLocalFolderReference(state.source.rootReference).displayText()
                            top.iwesley.lyn.music.core.model.ImportSourceType.SAMBA,
                            top.iwesley.lyn.music.core.model.ImportSourceType.WEBDAV,
                            -> state.source.folderSourceEndpointText().displayText()
                            top.iwesley.lyn.music.core.model.ImportSourceType.NAVIDROME,
                            top.iwesley.lyn.music.core.model.ImportSourceType.SUBSONIC,
                            top.iwesley.lyn.music.core.model.ImportSourceType.EMBY,
                            -> remoteSourceAddressSummary(
                                lanRootReference = state.source.rootReference,
                                wanRootReference = state.source.wanRootReference,
                            )
                            top.iwesley.lyn.music.core.model.ImportSourceType.FN_MUSIC ->
                                top.iwesley.lyn.music.domain.fnMusicIdOf(state.source.rootReference)
                                    ?.let { uiString(Res.string.fn_music_fn_id_summary, it) }
                                    ?: remoteSourceAddressSummary(
                                        lanRootReference = state.source.rootReference,
                                        wanRootReference = state.source.wanRootReference,
                                    )
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SourceCardActions(
                    sourceEnabled = sourceEnabled,
                    enabled = enabled,
                    onEdit = onEdit,
                    editLabel = editLabel,
                    onToggleEnabled = onToggleEnabled,
                    onRescan = onRescan,
                    isRescanning = isRescanning,
                    onDelete = onDelete,
                    compact = compact,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val trackCountLabel = importSourceTrackCountLabel(
                    indexMode = sourceIndexMode,
                    localTrackCount = state.indexState?.trackCount,
                    remoteTrackCount = state.indexState?.remoteTrackCount,
                )
                MainShellAssistChip(
                    onClick = {},
                    label = { Text(trackCountLabel) },
                    leadingIcon = { Icon(Icons.Rounded.LibraryMusic, null) })
                MainShellAssistChip(
                    onClick = {},
                    label = {
                        Text(
                            when {
                                !sourceEnabled -> uiString(Res.string.common_disabled)
                                isOnlineSource -> uiString(Res.string.source_online_mode_label)
                                state.indexState?.lastError == null -> uiString(Res.string.source_scan_success_status)
                                else -> uiString(Res.string.source_scan_failed_status)
                            },
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.CloudSync, null) })
            }
            scanProgress?.let { progress ->
                SourceScanProgressRow(progress)
            }
            scanSummaryPresentation?.let { presentation ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = presentation.summaryText.displayText(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (presentation.showFailuresButton) {
                        TextButton(onClick = { onShowScanFailures?.invoke(presentation.summary) }) {
                            Text(uiString(Res.string.import_view_failures))
                        }
                    }
                }
            }
            state.indexState?.lastErrorUiText()?.let {
                Text(
                    text = it.displayText(),
                    color = if (sourceEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SourceScanProgressRow(progress: ImportScanProgress) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val fraction = importScanProgressFraction(progress)
        if (fraction == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = importScanProgressLabel(progress),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

internal fun importSourceTrackCountLabelText(
    indexMode: ImportSourceIndexMode,
    localTrackCount: Int?,
    remoteTrackCount: Int?,
): UiText {
    return if (indexMode == ImportSourceIndexMode.ONLINE) {
        remoteTrackCount?.let { uiPlural(Res.plurals.source_remote_track_count, (it.coerceAtLeast(0)).toInt(), it.coerceAtLeast(0)) } ?: uiText(Res.string.source_online_track_count_unknown)
    } else {
        uiPlural(Res.plurals.common_track_count, (localTrackCount?.coerceAtLeast(0) ?: 0).toInt(), localTrackCount?.coerceAtLeast(0) ?: 0)
    }
}

@Composable
internal fun importSourceTrackCountLabel(
    indexMode: ImportSourceIndexMode,
    localTrackCount: Int?,
    remoteTrackCount: Int?,
): String = importSourceTrackCountLabelText(indexMode, localTrackCount, remoteTrackCount).displayText()

internal fun importScanProgressLabelText(progress: ImportScanProgress): UiText {
    if (progress.phase == ImportScanPhase.Persisting) return uiText(Res.string.startup_updating_library)
    val total = progress.totalTrackCount?.takeIf { it > 0 }
    return if (total == null) {
        uiPlural(Res.plurals.import_completed_track_count, (progress.importedTrackCount.coerceAtLeast(0)).toInt(), progress.importedTrackCount.coerceAtLeast(0))
    } else {
        uiText(Res.string.import_track_progress, progress.importedTrackCount.coerceAtLeast(0), total)
    }
}

@Composable
internal fun importScanProgressLabel(progress: ImportScanProgress): String = importScanProgressLabelText(progress).displayText()

internal fun importScanProgressFraction(progress: ImportScanProgress): Float? {
    if (progress.phase == ImportScanPhase.Persisting) return null
    val total = progress.totalTrackCount?.takeIf { it > 0 } ?: return null
    return progress.importedTrackCount.toFloat().coerceIn(0f, total.toFloat()) / total.toFloat()
}

@Composable
private fun remoteSourceAddressSummary(
    lanRootReference: String,
    wanRootReference: String?,
): String {
    val lan = lanRootReference.takeIf { it.isNotBlank() }
    val wan = wanRootReference?.takeIf { it.isNotBlank() }
    return when {
        lan != null && wan != null -> uiString(Res.string.source_network_addresses_summary, lan, wan)
        lan != null -> lan
        wan != null -> uiString(Res.string.source_wan_address_summary, wan)
        else -> ""
    }
}

@Composable
private fun SourceCardActions(
    sourceEnabled: Boolean,
    enabled: Boolean,
    onEdit: (() -> Unit)?,
    editLabel: String,
    onToggleEnabled: () -> Unit,
    onRescan: (() -> Unit)?,
    isRescanning: Boolean,
    onDelete: () -> Unit,
    compact: Boolean,
) {
    if (compact) {
        SourceCardCompactActions(
            sourceEnabled = sourceEnabled,
            enabled = enabled,
            onEdit = onEdit,
            editLabel = editLabel,
            onToggleEnabled = onToggleEnabled,
            onRescan = onRescan,
            isRescanning = isRescanning,
            onDelete = onDelete,
        )
    } else {
        SourceCardTextActions(
            sourceEnabled = sourceEnabled,
            enabled = enabled,
            onEdit = onEdit,
            editLabel = editLabel,
            onToggleEnabled = onToggleEnabled,
            onRescan = onRescan,
            isRescanning = isRescanning,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun SourceCardTextActions(
    sourceEnabled: Boolean,
    enabled: Boolean,
    onEdit: (() -> Unit)?,
    editLabel: String,
    onToggleEnabled: () -> Unit,
    onRescan: (() -> Unit)?,
    isRescanning: Boolean,
    onDelete: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            onEdit?.let { edit ->
                OutlinedButton(onClick = edit, enabled = enabled) {
                    Icon(Icons.Rounded.Tune, null)
                    Spacer(Modifier.width(6.dp))
                    Text(editLabel)
                }
            }
            if (sourceEnabled) {
                onRescan?.let { rescan ->
                    OutlinedButton(onClick = rescan, enabled = enabled) {
                        if (isRescanning) {
                            ButtonLoadingIndicator()
                        } else {
                            Icon(Icons.Rounded.Sync, null)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (isRescanning) uiString(Res.string.source_rescanning_status) else uiString(Res.string.common_rescan))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onToggleEnabled, enabled = enabled) {
                Icon(if (sourceEnabled) Icons.Rounded.Block else Icons.Rounded.CheckCircle, null)
                Spacer(Modifier.width(6.dp))
                Text(if (sourceEnabled) uiString(Res.string.common_disable) else uiString(Res.string.common_enable))
            }
            OutlinedButton(
                onClick = onDelete,
                enabled = enabled,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Rounded.Delete, null)
                Spacer(Modifier.width(6.dp))
                Text(uiString(Res.string.common_delete))
            }
        }
    }
}

@Composable
private fun SourceCardCompactActions(
    sourceEnabled: Boolean,
    enabled: Boolean,
    onEdit: (() -> Unit)?,
    editLabel: String,
    onToggleEnabled: () -> Unit,
    onRescan: (() -> Unit)?,
    isRescanning: Boolean,
    onDelete: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onEdit != null || (sourceEnabled && onRescan != null)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                onEdit?.let { edit ->
                    SourceCardIconActionButton(
                        onClick = edit,
                        enabled = enabled,
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = uiString(Res.string.source_type_label, editLabel),
                    )
                }
                if (sourceEnabled) {
                    onRescan?.let { rescan ->
                        SourceCardIconActionButton(
                            onClick = rescan,
                            enabled = enabled,
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = if (isRescanning) uiString(Res.string.source_rescanning_status) else uiString(Res.string.source_rescan_action),
                            loading = isRescanning,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SourceCardIconActionButton(
                onClick = onToggleEnabled,
                enabled = enabled,
                imageVector = if (sourceEnabled) Icons.Rounded.Block else Icons.Rounded.CheckCircle,
                contentDescription = if (sourceEnabled) uiString(Res.string.source_disable_action) else uiString(Res.string.source_enable_action),
            )
            SourceCardIconActionButton(
                onClick = onDelete,
                enabled = enabled,
                imageVector = Icons.Rounded.Delete,
                contentDescription = uiString(Res.string.source_delete_action),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SourceCardIconActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    loading: Boolean = false,
) {
    val resolvedTint = if (enabled) tint else tint.copy(alpha = 0.38f)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(SourceCardCompactActionButtonSize),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = resolvedTint,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = resolvedTint,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private val SourceCardCompactActionButtonSize = 42.dp

internal data class SourceScanSummaryPresentation(
    val summary: ImportScanSummary,
    val summaryText: UiText,
    val showFailuresButton: Boolean,
)

internal fun buildSourceScanSummaryPresentation(
    summary: ImportScanSummary?,
    canShowFailures: Boolean,
    isOnlineSource: Boolean = false,
    remoteTrackCount: Int? = null,
): SourceScanSummaryPresentation? {
    summary ?: return null
    if (isOnlineSource) {
        return SourceScanSummaryPresentation(
            summary = summary,
            summaryText = onlineSourceScanSummaryText(
                remoteTrackCount = remoteTrackCount,
                summaryDiscoveredTrackCount = summary.discoveredAudioFileCount,
            ),
            showFailuresButton = false,
        )
    }
    return SourceScanSummaryPresentation(
        summary = summary,
        summaryText = formatImportScanSummary(summary),
        showFailuresButton = summary.failedAudioFileCount > 0 && canShowFailures,
    )
}

private fun onlineSourceScanSummaryText(
    remoteTrackCount: Int?,
    summaryDiscoveredTrackCount: Int,
): UiText {
    val resolvedTrackCount = remoteTrackCount ?: summaryDiscoveredTrackCount.takeIf { it > 0 }
    return resolvedTrackCount
        ?.let { uiPlural(Res.plurals.online_source_summary, (it.coerceAtLeast(0)).toInt(), it.coerceAtLeast(0)) }
        ?: uiText(Res.string.source_online_index_exclusion_hint)
}

@Composable
internal fun ButtonLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    CircularProgressIndicator(
        modifier = modifier.size(16.dp),
        color = LocalContentColor.current,
        strokeWidth = 2.dp,
    )
}

@Composable
internal fun SectionTitle(
    title: String,
    subtitle: String,
) {
    val shellColors = mainShellColors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (title.isNotBlank()) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = shellColors.secondaryText)
        }
    }
}

@Composable
internal fun BannerCard(
    message: String,
    onDismiss: () -> Unit,
) {
    val shellColors = mainShellColors
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = shellColors.selectedContainer),
        border = BorderStroke(1.dp, shellColors.selectedBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(uiString(Res.string.common_close)) }
        }
    }
}

@Composable
fun ToastCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    Card(
        modifier = modifier.widthIn(max = 420.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = shellColors.navContainer,
        ),
        border = BorderStroke(1.dp, shellColors.cardBorder),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    body: String,
) {
    val shellColors = mainShellColors
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = shellColors.cardContainer),
        border = BorderStroke(1.dp, shellColors.cardBorder),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    val selectedContentColor = MaterialTheme.colorScheme.onSecondary
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondary
            } else {
                shellColors.cardContainer
            },
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.secondary
            } else {
                shellColors.cardBorder
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                icon,
                null,
                tint = if (selected) selectedContentColor else MaterialTheme.colorScheme.primary,
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (selected) selectedContentColor else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                title,
                color = if (selected) selectedContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun BannerCard(message: top.iwesley.lyn.music.core.model.UiText, onDismiss: () -> Unit) =
    BannerCard(message.displayText(), onDismiss)

@Composable
fun ToastCard(message: top.iwesley.lyn.music.core.model.UiText, modifier: Modifier = Modifier) =
    ToastCard(message.displayText(), modifier)

@Composable
private fun DownloadMenuTrailingSizeText(value: top.iwesley.lyn.music.core.model.UiText) = DownloadMenuTrailingSizeText(value.displayText())
