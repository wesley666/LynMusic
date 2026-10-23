package top.iwesley.lyn.music.desktop.ui

import top.iwesley.lyn.music.*

import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.plus
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.UiText

import top.iwesley.lyn.music.core.model.uiPlural

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import top.iwesley.lyn.music.core.model.OfflineDownload
import top.iwesley.lyn.music.feature.offline.ActiveBatchDownloadState
import top.iwesley.lyn.music.feature.offline.OfflineDownloadIntent
import top.iwesley.lyn.music.feature.offline.formatOfflineDownloadSizeLabel
import top.iwesley.lyn.music.ui.mainShellColors
import kotlin.math.max

data class OfflineDownloadUiState(
    val downloadsByTrackId: Map<String, OfflineDownload> = emptyMap(),
    val availableSpaceBytes: Long? = null,
    val availableSpaceLoading: Boolean = false,
    val activeBatchDownload: ActiveBatchDownloadState? = null,
    val onIntent: ((OfflineDownloadIntent) -> Unit)? = null,
)

val LocalOfflineDownloadUiState = staticCompositionLocalOf { OfflineDownloadUiState() }

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
fun OfflineBatchDownloadStatusBar(
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
