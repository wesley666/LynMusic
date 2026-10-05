package top.iwesley.lyn.music

import top.iwesley.lyn.music.feature.importing.folderSourceEndpointText
import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.plus
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.lastErrorUiText

import top.iwesley.lyn.music.core.model.uiPlural

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
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
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.ArtworkTintTheme
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
import top.iwesley.lyn.music.core.model.PlaybackMode
import top.iwesley.lyn.music.core.model.PlayerArtworkStyle
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.deriveArtworkTintTheme
import top.iwesley.lyn.music.core.model.derivePlaybackArtworkBackgroundPalette
import top.iwesley.lyn.music.core.model.displayWebDavRootUrl
import top.iwesley.lyn.music.core.model.offlineDownloadSourceType
import top.iwesley.lyn.music.core.model.supportsOfflineDownload
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.feature.importing.formatImportScanSummary
import top.iwesley.lyn.music.feature.offline.ActiveBatchDownloadState
import top.iwesley.lyn.music.feature.offline.OfflineDownloadIntent
import top.iwesley.lyn.music.feature.offline.batchDownloadSizeEstimateLabel
import top.iwesley.lyn.music.feature.offline.estimateBatchDownloadSize
import top.iwesley.lyn.music.feature.offline.estimatedNavidromeTranscodedSizeBytes
import top.iwesley.lyn.music.feature.offline.formatOfflineDownloadSizeLabel
import top.iwesley.lyn.music.ui.mainShellColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class OfflineDownloadUiState(
    val downloadsByTrackId: Map<String, OfflineDownload> = emptyMap(),
    val availableSpaceBytes: Long? = null,
    val availableSpaceLoading: Boolean = false,
    val activeBatchDownload: ActiveBatchDownloadState? = null,
    val onIntent: ((OfflineDownloadIntent) -> Unit)? = null,
)

internal val LocalOfflineDownloadUiState = staticCompositionLocalOf { OfflineDownloadUiState() }

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

internal data class OfflineBatchDownloadStatusSummary(
    val label: UiText,
    val progress: Float?,
)

internal fun offlineBatchDownloadStatusSummary(
    activeBatchDownload: ActiveBatchDownloadState?,
    downloadsByTrackId: Map<String, OfflineDownload>,
): OfflineBatchDownloadStatusSummary? {
    val batch = activeBatchDownload?.takeIf { it.totalCount > 0 } ?: return null
    val processedCount = batch.processedCount.coerceIn(0, batch.totalCount)
    val downloadedBytes = batch.trackIds.fold(0L) { total, trackId ->
        saturatedAdd(
            total,
            downloadsByTrackId[trackId]?.downloadedBytes?.coerceAtLeast(0L) ?: 0L,
        )
    }
    val prefix = uiPlural(Res.plurals.offline_download_progress, batch.totalCount, processedCount, batch.totalCount)
    if (batch.unknownCount > 0) {
        return OfflineBatchDownloadStatusSummary(
            label = uiText(Res.string.offline_batch_size_summary, prefix, formatOfflineDownloadSizeLabel(downloadedBytes), batch.unknownCount),
            progress = null,
        )
    }
    val estimatedTotalBytes = batch.estimatedTotalBytes.takeIf { it > 0L }
        ?: return OfflineBatchDownloadStatusSummary(
            label = uiText(Res.string.offline_waiting_summary, prefix),
            progress = null,
        )
    val totalLabel = if (batch.approximate) {
        uiText(Res.string.about_named_app, formatOfflineDownloadSizeLabel(estimatedTotalBytes))
    } else {
        UiText.Raw(formatOfflineDownloadSizeLabel(estimatedTotalBytes))
    }
    return OfflineBatchDownloadStatusSummary(
        label = prefix + UiText.Raw(" · ${formatOfflineDownloadSizeLabel(downloadedBytes)} / ") + totalLabel,
        progress = (downloadedBytes.toDouble() / estimatedTotalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat(),
    )
}

@Composable
internal fun OfflineBatchDownloadStatusBar(
    modifier: Modifier = Modifier,
) {
    val offlineUiState = LocalOfflineDownloadUiState.current
    val activeBatchDownload = offlineUiState.activeBatchDownload
    val onIntent = offlineUiState.onIntent
    val shellColors = mainShellColors
    val appDensity = LocalDensity.current
    var showCancelConfirmation by remember { mutableStateOf(false) }
    LaunchedEffect(activeBatchDownload) {
        if (activeBatchDownload == null) {
            showCancelConfirmation = false
        }
    }
    val summary = offlineBatchDownloadStatusSummary(
        activeBatchDownload = activeBatchDownload,
        downloadsByTrackId = offlineUiState.downloadsByTrackId,
    ) ?: return
    if (showCancelConfirmation && activeBatchDownload != null && onIntent != null) {
        Dialog(onDismissRequest = { showCancelConfirmation = false }) {
            CompositionLocalProvider(LocalDensity provides appDensity) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = shellColors.navContainer),
                    border = BorderStroke(1.dp, shellColors.cardBorder),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = uiString(Res.string.offline_cancel_batch_title),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = uiString(Res.string.offline_cancel_batch_description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { showCancelConfirmation = false },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) {
                                Text(uiString(Res.string.offline_continue_download))
                            }
                            TextButton(
                                onClick = {
                                    showCancelConfirmation = false
                                    onIntent(OfflineDownloadIntent.CancelActiveBatchDownload)
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text(uiString(Res.string.offline_cancel_download))
                            }
                        }
                    }
                }
            }
        }
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = shellColors.navContainer),
        border = BorderStroke(1.dp, shellColors.cardBorder),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary.label.displayText(),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = { showCancelConfirmation = true },
                    enabled = onIntent != null,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = uiString(Res.string.offline_cancel_batch_action),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            val progress = summary.progress
            if (progress == null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = shellColors.cardBorder,
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = shellColors.cardBorder,
                )
            }
        }
    }
}

private fun saturatedAdd(left: Long, right: Long): Long {
    if (right <= 0L) return left
    return if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
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
    return currentPlatformDescriptor.supportsOfflineDownloadUiActions()
}

@Composable
internal fun FavoriteToggleButton(
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
    showFavoriteButton: Boolean = true,
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
    val effectiveShowFavoriteButton = showFavoriteButton && !selectionMode
    Column(modifier = Modifier.fillMaxWidth()) {
        TrackActionContainer(
            track = track,
            onClick = rowClick,
            enableOfflineActions = !selectionMode,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
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
                            .then(artistClick?.let { Modifier.clickable(onClick = it) } ?: Modifier),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    offlineRowIndicatorState?.let { OfflineDownloadRowIndicator(it) }
                }
            }
            if (showAlbumTitle) {
                Box(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.albumTitle?.trim()?.takeIf { it.isNotEmpty() } ?: uiString(Res.string.common_unknown_album),
                        modifier = albumClick?.let { Modifier.clickable(onClick = it) } ?: Modifier,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier.width(
                    when {
                        effectiveShowFavoriteButton && showDuration -> 112.dp
                        effectiveShowFavoriteButton || showDuration -> 56.dp
                        else -> 0.dp
                    },
                ),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (effectiveShowFavoriteButton) {
                    FavoriteToggleButton(
                        isFavorite = isFavorite,
                        onClick = onToggleFavorite,
                    )
                }
                if (showDuration) {
                    Text(
                        formatDuration(track.durationMs),
                        modifier = Modifier.width(56.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    )
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
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun TrackActionContainer(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enableOfflineActions: Boolean = true,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit,
) {
    val offlineUiState = LocalOfflineDownloadUiState.current
    val onOfflineIntent = offlineUiState.onIntent
    val supportsOfflinePlatform = currentPlatformDescriptor.supportsOfflineDownloadUiActions()
    val supportsActions = enableOfflineActions && supportsOfflinePlatform && onOfflineIntent != null &&
        supportsOfflineDownload(track)
    val download = offlineUiState.downloadsByTrackId[track.id]
    val touchOfflineUi = currentPlatformDescriptor.usesTouchOfflineDownloadUi()
    var desktopMenuExpanded by remember(track.id) { mutableStateOf(false) }
    var mobileSheetVisible by remember(track.id) { mutableStateOf(false) }
    val interactionModifier = if (!supportsActions) {
        Modifier.clickable(onClick = onClick)
    } else if (touchOfflineUi) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { mobileSheetVisible = true },
        )
    } else {
        Modifier
            .pointerInput(track.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            event.changes.forEach { it.consume() }
                            desktopMenuExpanded = true
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
        if (supportsActions && !touchOfflineUi && desktopMenuExpanded) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { desktopMenuExpanded = false },
                containerColor = mainShellColors.navContainer,
            ) {
                TrackOfflineActionMenuItems(
                    track = track,
                    download = download,
                    onIntent = onOfflineIntent,
                    onDismiss = { desktopMenuExpanded = false },
                )
            }
        }
    }
    if (supportsActions && touchOfflineUi && mobileSheetVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val appDensity = LocalDensity.current
        ModalBottomSheet(
            onDismissRequest = { mobileSheetVisible = false },
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
                        onDismiss = { mobileSheetVisible = false },
                    )
                }
            }
        }
    }
}

@Composable
internal fun TrackOfflineActionMenuItems(
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
internal fun LyricsSourceCard(
    source: top.iwesley.lyn.music.core.model.LyricsSourceDefinition,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    source.name,
                    modifier = Modifier.weight(1f, fill = false),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PriorityBadge(priority = source.priority)
            }
            Text(
                if (source.enabled) uiString(Res.string.common_enabled) else uiString(Res.string.common_inactive),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            when (source) {
                is LyricsSourceConfig -> source.urlTemplate
                is top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig -> "Workflow JSON · ${source.search.request.url}"
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (source) {
                is LyricsSourceConfig -> {
                    MainShellAssistChip(
                        onClick = {},
                        label = { Text(source.method.name) },
                        leadingIcon = { Icon(Icons.Rounded.CloudSync, null) })
                    MainShellAssistChip(
                        onClick = {},
                        label = { Text(source.responseFormat.name) },
                        leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) })
                }

                is top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig -> {
                    MainShellAssistChip(
                        onClick = {},
                        label = { Text(uiString(Res.string.settings_workflow_source)) },
                        leadingIcon = { Icon(Icons.Rounded.CloudSync, null) })
                    MainShellAssistChip(
                        onClick = {},
                        label = { Text(uiString(Res.plurals.workflow_step_count, (source.lyrics.steps.size).toInt(), source.lyrics.steps.size)) },
                        leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onToggleEnabled) {
                Text(if (source.enabled) uiString(Res.string.common_deactivate) else uiString(Res.string.common_enable))
            }
            TextButton(onClick = onDelete) {
                Text(uiString(Res.string.common_delete))
            }
        }
    }
}

@Composable
internal fun PriorityBadge(
    priority: Int,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(
            containerColor = shellColors.selectedContainer,
        ),
        border = BorderStroke(1.dp, shellColors.selectedBorder),
    ) {
        Text(
            text = "P$priority",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
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
internal fun ToastCard(
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
internal fun EmptyStateCard(
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
internal fun PlayerArtworkDisplay(
    style: PlayerArtworkStyle,
    artworkSize: Dp,
    artworkBitmap: ImageBitmap? = null,
    artworkLocator: String? = null,
    artworkCacheKey: String? = null,
    spinning: Boolean = false,
    enableArtworkTint: Boolean = false,
    vinylArtworkDiameterFraction: Float = PLAYER_ARTWORK_DEFAULT_VINYL_ARTWORK_DIAMETER_FRACTION,
    vinylInnerGlowDiameterFraction: Float = PLAYER_ARTWORK_DEFAULT_VINYL_INNER_GLOW_DIAMETER_FRACTION,
    maxArtworkDecodeSizePx: Int = ArtworkDecodeSize.Thumbnail,
    retainPreviousArtworkWhileLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    when (style) {
        PlayerArtworkStyle.VINYL -> VinylPlaceholder(
            vinylSize = artworkSize,
            artworkBitmap = artworkBitmap,
            artworkLocator = artworkLocator,
            artworkCacheKey = artworkCacheKey,
            spinning = spinning,
            enableArtworkTint = enableArtworkTint,
            artworkDiameterFraction = vinylArtworkDiameterFraction,
            innerGlowDiameterFraction = vinylInnerGlowDiameterFraction,
            maxArtworkDecodeSizePx = maxArtworkDecodeSizePx,
            retainPreviousArtworkWhileLoading = retainPreviousArtworkWhileLoading,
            modifier = modifier,
        )

        PlayerArtworkStyle.HALF_RECORD -> HalfOutRecordArtwork(
            artworkSize = artworkSize,
            artworkBitmap = artworkBitmap,
            artworkLocator = artworkLocator,
            artworkCacheKey = artworkCacheKey,
            spinning = spinning,
            maxArtworkDecodeSizePx = maxArtworkDecodeSizePx,
            retainPreviousArtworkWhileLoading = retainPreviousArtworkWhileLoading,
            modifier = modifier,
        )

        PlayerArtworkStyle.MINIMAL_COVER -> MinimalCoverArtwork(
            artworkSize = artworkSize,
            artworkBitmap = artworkBitmap,
            artworkLocator = artworkLocator,
            artworkCacheKey = artworkCacheKey,
            maxArtworkDecodeSizePx = maxArtworkDecodeSizePx,
            retainPreviousArtworkWhileLoading = retainPreviousArtworkWhileLoading,
            modifier = modifier,
        )
    }
}

internal fun playerArtworkDisplayVisualWidthFactor(style: PlayerArtworkStyle): Float {
    return when (style) {
        PlayerArtworkStyle.HALF_RECORD -> 1.24f
        PlayerArtworkStyle.VINYL,
        PlayerArtworkStyle.MINIMAL_COVER -> 1f
    }
}

internal fun playerArtworkDisplayVisualHeightFactor(style: PlayerArtworkStyle): Float {
    return when (style) {
        PlayerArtworkStyle.VINYL,
        PlayerArtworkStyle.HALF_RECORD,
        PlayerArtworkStyle.MINIMAL_COVER -> 1f
    }
}

@Composable
private fun HalfOutRecordArtwork(
    artworkSize: Dp,
    artworkBitmap: ImageBitmap?,
    artworkLocator: String?,
    artworkCacheKey: String?,
    spinning: Boolean,
    maxArtworkDecodeSizePx: Int,
    retainPreviousArtworkWhileLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val visualWidth = artworkSize * playerArtworkDisplayVisualWidthFactor(PlayerArtworkStyle.HALF_RECORD)
    val sleeveSize = artworkSize * 0.82f
    val recordSize = artworkSize * 0.86f
    val sleeveRadius = artworkSize * 0.035f
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (!spinning) return@LaunchedEffect
        while (true) {
            val start = rotation.value % 360f
            rotation.snapTo(start)
            rotation.animateTo(
                targetValue = start + 360f,
                animationSpec = tween(
                    durationMillis = 18_000,
                    easing = LinearEasing,
                ),
            )
        }
    }
    Box(
        modifier = modifier.size(artworkSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(visualWidth)
                .height(artworkSize),
            contentAlignment = Alignment.CenterStart,
        ) {
            HalfOutRecordDisc(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(recordSize)
                    .graphicsLayer { rotationZ = rotation.value },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(sleeveSize)
                    .clip(RoundedCornerShape(sleeveRadius))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.70f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(sleeveRadius),
                    ),
            ) {
                if (artworkBitmap != null && artworkLocator.isNullOrBlank()) {
                    Image(
                        bitmap = artworkBitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    LynArtworkImage(
                        artworkLocator = artworkLocator,
                        contentDescription = null,
                        artworkCacheKey = artworkCacheKey,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        maxDecodeSizePx = maxArtworkDecodeSizePx,
                        retainPreviousWhileLoading = retainPreviousArtworkWhileLoading,
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.08f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.18f),
                                ),
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun HalfOutRecordDisc(
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val label = "LynMusic"
        val labelFontSize = if (maxWidth < 260.dp) 10.sp else 13.sp
        val labelTextStyle = MaterialTheme.typography.labelSmall.copy(
            fontSize = labelFontSize,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        )
        val textMeasurer = rememberTextMeasurer(cacheSize = label.length)
        val labelCharacters = remember(label) { label.map { it.toString() } }
        val labelCharacterWidthsPx = remember(labelCharacters, labelTextStyle, textMeasurer) {
            labelCharacters.map { character ->
                textMeasurer.measure(
                    text = character,
                    style = labelTextStyle,
                    maxLines = 1,
                    softWrap = false,
                ).size.width.toFloat()
            }
        }
        val density = LocalDensity.current
        val labelRadius = maxWidth * 0.40f
        val labelRadiusPx = with(density) { labelRadius.toPx() }
        val labelGapPx = with(density) { 1.dp.toPx() }
        val labelCenterAngle = -41f
        val labelTotalArcLengthPx = remember(labelCharacterWidthsPx, labelGapPx) {
            labelCharacterWidthsPx.sum() + labelGapPx * (labelCharacterWidthsPx.size - 1).coerceAtLeast(0)
        }
        val labelTotalAngle = if (labelRadiusPx > 0f) {
            labelTotalArcLengthPx / labelRadiusPx * 180f / PI.toFloat()
        } else {
            0f
        }
        val labelStartAngle = labelCenterAngle - labelTotalAngle / 2f
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = min(size.width, size.height) / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF303238),
                        0.22f to Color(0xFF15161A),
                        0.70f to Color(0xFF07080A),
                        1.0f to Color(0xFF020304),
                    ),
                ),
                radius = radius,
            )
            repeat(28) { index ->
                val fraction = (index + 2) / 31f
                val ringRadius = radius * fraction
                val ringAlpha = 0.10f - fraction * 0.055f
                if (ringAlpha > 0f) {
                    drawCircle(
                        color = Color.White.copy(alpha = ringAlpha),
                        radius = ringRadius,
                        style = Stroke(width = if (index % 5 == 0) 1.5f else 0.8f),
                    )
                }
            }
            val highlightCenter = Offset(center.x + radius * 0.34f, center.y - radius * 0.36f)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color.White.copy(alpha = 0.24f),
                        0.48f to Color.White.copy(alpha = 0.10f),
                        1.0f to Color.Transparent,
                    ),
                    center = highlightCenter,
                    radius = radius * 0.42f,
                ),
                radius = radius * 0.42f,
                center = highlightCenter,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = radius * 0.14f,
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.36f),
                radius = radius * 0.052f,
            )
        }
        var consumedArcLengthPx = 0f
        labelCharacters.forEachIndexed { index, character ->
            val characterWidthPx = labelCharacterWidthsPx.getOrElse(index) { 0f }
            val characterCenterArcLengthPx = consumedArcLengthPx + characterWidthPx / 2f
            val angleOffset = if (labelRadiusPx > 0f) {
                characterCenterArcLengthPx / labelRadiusPx * 180f / PI.toFloat()
            } else {
                0f
            }
            val angle = labelStartAngle + angleOffset
            val radians = angle * PI.toFloat() / 180f
            Text(
                text = character,
                color = Color.White.copy(alpha = 0.16f),
                style = labelTextStyle,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(
                        x = labelRadius * cos(radians.toDouble()).toFloat(),
                        y = labelRadius * sin(radians.toDouble()).toFloat(),
                    )
                    .graphicsLayer { rotationZ = angle + 90f },
            )
            consumedArcLengthPx += characterWidthPx + labelGapPx
        }
    }
}

@Composable
private fun MinimalCoverArtwork(
    artworkSize: Dp,
    artworkBitmap: ImageBitmap?,
    artworkLocator: String?,
    artworkCacheKey: String?,
    maxArtworkDecodeSizePx: Int,
    retainPreviousArtworkWhileLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val radius = artworkSize * 0.05f
    Box(
        modifier = modifier
            .size(artworkSize)
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.76f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(radius),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkBitmap != null && artworkLocator.isNullOrBlank()) {
            Image(
                bitmap = artworkBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            LynArtworkImage(
                artworkLocator = artworkLocator,
                contentDescription = null,
                artworkCacheKey = artworkCacheKey,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                maxDecodeSizePx = maxArtworkDecodeSizePx,
                retainPreviousWhileLoading = retainPreviousArtworkWhileLoading,
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.White.copy(alpha = 0.05f),
                            0.62f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.16f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
internal fun VinylPlaceholder(
    vinylSize: Dp,
    artworkBitmap: ImageBitmap? = null,
    artworkLocator: String? = null,
    artworkCacheKey: String? = null,
    spinning: Boolean = false,
    enableArtworkTint: Boolean = false,
    artworkDiameterFraction: Float = DEFAULT_VINYL_ARTWORK_DIAMETER_FRACTION,
    innerGlowDiameterFraction: Float = DEFAULT_VINYL_INNER_GLOW_DIAMETER_FRACTION,
    maxArtworkDecodeSizePx: Int = ArtworkDecodeSize.Thumbnail,
    retainPreviousArtworkWhileLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val normalizedArtworkDiameterFraction = artworkDiameterFraction.coerceIn(0.2f, 1f)
    val normalizedInnerGlowDiameterFraction = innerGlowDiameterFraction
        .coerceIn(normalizedArtworkDiameterFraction, 1f)
    val resolvedArtworkBitmap = artworkBitmap
    val palette = rememberVinylArtworkPalette(
        artworkBitmap = resolvedArtworkBitmap,
        enabled = enableArtworkTint,
    )
    val animatedRimColor by animateColorAsState(
        targetValue = palette?.rimColor ?: Color.White.copy(alpha = 0.18f),
        label = "vinyl-rim-color",
    )
    val animatedGlowColor by animateColorAsState(
        targetValue = palette?.glowColor ?: Color.Transparent,
        label = "vinyl-glow-color",
    )
    val animatedInnerGlowColor by animateColorAsState(
        targetValue = palette?.innerGlowColor ?: Color.Transparent,
        label = "vinyl-inner-glow-color",
    )
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (!spinning) return@LaunchedEffect
        while (true) {
            val start = rotation.value % 360f
            rotation.snapTo(start)
            rotation.animateTo(
                targetValue = start + 360f,
                animationSpec = tween(
                    durationMillis = 18_000,
                    easing = LinearEasing,
                ),
            )
        }
    }
    Box(
        modifier = modifier.size(vinylSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.56f to Color.Transparent,
                            0.82f to animatedGlowColor.copy(alpha = 0.22f),
                            1.0f to Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .size(vinylSize)
                .graphicsLayer { rotationZ = rotation.value },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val radius = min(size.width, size.height) / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFF292A2E),
                            0.42f to Color(0xFF141518),
                            0.78f to Color(0xFF090A0C),
                            1.0f to Color(0xFF040506),
                        ),
                    ),
                    radius = radius,
                )
                val ringEnd = radius * 0.94f
                val ringStart = (radius * normalizedArtworkDiameterFraction).coerceAtMost(ringEnd)
                if (ringStart < ringEnd) {
                    repeat(14) { index ->
                        val fraction = index / 13f
                        val ringRadius = ringStart + (ringEnd - ringStart) * fraction
                        val ringAlpha = 0.055f - fraction * 0.03f
                        if (ringAlpha > 0f) {
                            drawCircle(
                                color = Color.White.copy(alpha = ringAlpha),
                                radius = ringRadius,
                                style = Stroke(width = if (index % 4 == 0) 1.6f else 1.0f),
                            )
                        }
                    }
                }
                drawCircle(
                    color = animatedRimColor.copy(alpha = if (enableArtworkTint) 0.55f else 0.22f),
                    radius = radius - 2f,
                    style = Stroke(width = 3.5f),
                )
            }
            Box(
                modifier = Modifier
                    .size(vinylSize * normalizedInnerGlowDiameterFraction)
                    .background(
                        Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to animatedInnerGlowColor.copy(alpha = 0.24f),
                                0.68f to animatedInnerGlowColor.copy(alpha = 0.08f),
                                1.0f to Color.Transparent,
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .size(vinylSize * normalizedArtworkDiameterFraction)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (resolvedArtworkBitmap != null && artworkLocator.isNullOrBlank()) {
                    Image(
                        bitmap = resolvedArtworkBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    LynArtworkImage(
                        artworkLocator = artworkLocator,
                        contentDescription = null,
                        artworkCacheKey = artworkCacheKey,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        maxDecodeSizePx = maxArtworkDecodeSizePx,
                        retainPreviousWhileLoading = retainPreviousArtworkWhileLoading,
                    )
                }
            }
        }
    }
}

private const val PLAYER_ARTWORK_DEFAULT_VINYL_ARTWORK_DIAMETER_FRACTION = 0.70f
private const val PLAYER_ARTWORK_DEFAULT_VINYL_INNER_GLOW_DIAMETER_FRACTION = 0.80f
private const val DEFAULT_VINYL_ARTWORK_DIAMETER_FRACTION = 0.70f
private const val DEFAULT_VINYL_INNER_GLOW_DIAMETER_FRACTION = 0.62f

internal data class VinylArtworkPalette(
    val rimColor: Color,
    val glowColor: Color,
    val innerGlowColor: Color,
)

internal data class PlaybackArtworkBackgroundColors(
    val baseColor: Color,
    val primaryColor: Color,
    val secondaryColor: Color,
    val tertiaryColor: Color,
)

internal fun VinylArtworkPalette.toArtworkTintTheme(): ArtworkTintTheme {
    return ArtworkTintTheme(
        rimColorArgb = rimColor.toArgbInt(),
        glowColorArgb = glowColor.toArgbInt(),
        innerGlowColorArgb = innerGlowColor.toArgbInt(),
    )
}

@Composable
internal fun rememberPlaybackArtworkBackgroundPalette(
    artworkBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    enabled: Boolean,
): PlaybackArtworkBackgroundColors? {
    return remember(artworkBitmap, enabled) {
        if (!enabled || artworkBitmap == null) {
            null
        } else {
            derivePlaybackArtworkBackgroundColors(artworkBitmap)
        }
    }
}

private fun derivePlaybackArtworkBackgroundColors(
    artworkBitmap: androidx.compose.ui.graphics.ImageBitmap,
): PlaybackArtworkBackgroundColors? {
    val palette = derivePlaybackArtworkBackgroundPalette(sampleImageBitmapPixels(artworkBitmap)) ?: return null
    return PlaybackArtworkBackgroundColors(
        baseColor = composeColorFromArgb(palette.baseColorArgb),
        primaryColor = composeColorFromArgb(palette.primaryColorArgb),
        secondaryColor = composeColorFromArgb(palette.secondaryColorArgb),
        tertiaryColor = composeColorFromArgb(palette.tertiaryColorArgb),
    )
}

@Composable
internal fun rememberVinylArtworkPalette(
    artworkBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    enabled: Boolean,
): VinylArtworkPalette? {
    return remember(artworkBitmap, enabled) {
        if (!enabled || artworkBitmap == null) {
            null
        } else {
            deriveVinylArtworkPalette(artworkBitmap)
        }
    }
}

private fun deriveVinylArtworkPalette(
    artworkBitmap: androidx.compose.ui.graphics.ImageBitmap,
): VinylArtworkPalette? {
    val theme = deriveArtworkTintTheme(sampleImageBitmapPixels(artworkBitmap)) ?: return null
    return VinylArtworkPalette(
        rimColor = composeColorFromArgb(theme.rimColorArgb),
        glowColor = composeColorFromArgb(theme.glowColorArgb),
        innerGlowColor = composeColorFromArgb(theme.innerGlowColorArgb),
    )
}

private fun sampleImageBitmapPixels(artworkBitmap: androidx.compose.ui.graphics.ImageBitmap): List<Int> {
    val pixelMap = artworkBitmap.toPixelMap()
    val stepX = max(1, pixelMap.width / 24)
    val stepY = max(1, pixelMap.height / 24)
    return buildList {
        for (y in 0 until pixelMap.height step stepY) {
            for (x in 0 until pixelMap.width step stepX) {
                add(pixelMap[x, y].toArgbInt())
            }
        }
    }
}

internal fun composeColorFromArgb(argb: Int): Color {
    return Color(
        red = ((argb ushr 16) and 0xFF) / 255f,
        green = ((argb ushr 8) and 0xFF) / 255f,
        blue = (argb and 0xFF) / 255f,
        alpha = ((argb ushr 24) and 0xFF) / 255f,
    )
}

private fun Color.toArgbInt(): Int {
    val alphaInt = (alpha * 255f).roundToInt().coerceIn(0, 255)
    val redInt = (red * 255f).roundToInt().coerceIn(0, 255)
    val greenInt = (green * 255f).roundToInt().coerceIn(0, 255)
    val blueInt = (blue * 255f).roundToInt().coerceIn(0, 255)
    return (alphaInt shl 24) or (redInt shl 16) or (greenInt shl 8) or blueInt
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T : Enum<T>> EnumSelector(
    label: String,
    values: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    MainShellElevatedCard(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(label, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                values.forEach { value ->
                    val active = value == selected
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (active) shellColors.selectedContainer else shellColors.navContainer,
                        modifier = Modifier.clickable { onSelected(value) },
                    ) {
                        Text(
                            value.name,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun modeLabel(mode: PlaybackMode): String {
    return when (mode) {
        PlaybackMode.ORDER -> uiString(Res.string.player_sequential_mode)
        PlaybackMode.SHUFFLE -> uiString(Res.string.player_shuffle_mode)
        PlaybackMode.REPEAT_ONE -> uiString(Res.string.player_repeat_one_mode)
    }
}

internal fun playbackModeIcon(mode: PlaybackMode): ImageVector {
    return when (mode) {
        PlaybackMode.ORDER -> Icons.Rounded.Repeat
        PlaybackMode.SHUFFLE -> Icons.Rounded.Shuffle
        PlaybackMode.REPEAT_ONE -> Icons.Rounded.RepeatOne
    }
}

internal fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1_000).coerceAtLeast(0L)
    val minutesPart = seconds / 60
    val secondsPart = seconds % 60
    return minutesPart.toString().padStart(2, '0') + ":" + secondsPart.toString().padStart(2, '0')
}

internal fun formatLyricsCandidateDuration(durationSeconds: Int): String {
    val totalSeconds = durationSeconds.coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return buildString {
        append(hours.toString().padStart(2, '0'))
        append(':')
        append(minutes.toString().padStart(2, '0'))
        append(':')
        append(seconds.toString().padStart(2, '0'))
    }
}

@Composable
internal fun trackDisplayFormat(track: Track): String {
    return track.relativePath
        .substringAfterLast('.', "")
        .takeIf { it.isNotBlank() }
        ?.uppercase()
        ?: uiString(Res.string.common_unknown)
}

internal fun formatTrackAudioQuality(track: Track): String? {
    val bitDepth = track.bitDepth?.takeIf { it > 0 }?.let { "${it}bit" }
    val samplingRate = track.samplingRate?.takeIf { it > 0 }?.let(::formatSamplingRate)
    val bitDepthAndSamplingRate = listOfNotNull(bitDepth, samplingRate)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" / ")
    return listOfNotNull(
        bitDepthAndSamplingRate,
        track.bitRate?.takeIf { it > 0 }?.let { "${it}kbps" },
        track.channelCount?.takeIf { it > 0 }?.let { "${it}ch" },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

internal fun formatTrackTechnicalSummaryText(track: Track): UiText = UiText.Joined(listOfNotNull(
    track.relativePath.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.uppercase()?.let(UiText::Raw) ?: uiText(Res.string.common_unknown),
    formatTrackAudioQuality(track)?.let(UiText::Raw),
    formatTrackSizeText(track.sizeBytes),
))
@Composable
internal fun formatTrackTechnicalSummary(track: Track): String = formatTrackTechnicalSummaryText(track).displayText()

private fun formatSamplingRate(samplingRateHz: Int): String {
    val decimals = if (samplingRateHz % 1_000 == 0) 0 else 1
    return "${roundTo(samplingRateHz / 1_000.0, decimals)}kHz"
}

internal fun formatTrackSizeText(sizeBytes: Long): UiText {
    if (sizeBytes <= 0L) return uiText(Res.string.common_unknown)
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    val value = when {
        sizeBytes >= gb -> "${roundTo((sizeBytes / gb), 2)} GB"
        sizeBytes >= mb -> "${roundTo((sizeBytes / mb), 1)} MB"
        sizeBytes >= kb -> "${roundTo((sizeBytes / kb), 0)} KB"
        else -> "$sizeBytes B"
    }
    return UiText.Raw(value)
}

@Composable
internal fun formatTrackSize(sizeBytes: Long): String = formatTrackSizeText(sizeBytes).displayText()

private fun roundTo(value: Double, decimals: Int): String {
    if (decimals <= 0) return value.roundToInt().toString()
    val factor = 10.0.pow(decimals)
    val rounded = (value * factor).roundToInt() / factor
    return rounded.toString()
}

@Composable
internal fun BannerCard(message: top.iwesley.lyn.music.core.model.UiText, onDismiss: () -> Unit) =
    BannerCard(message.displayText(), onDismiss)

@Composable
internal fun ToastCard(message: top.iwesley.lyn.music.core.model.UiText, modifier: Modifier = Modifier) =
    ToastCard(message.displayText(), modifier)

@Composable
private fun DownloadMenuTrailingSizeText(value: top.iwesley.lyn.music.core.model.UiText) = DownloadMenuTrailingSizeText(value.displayText())
