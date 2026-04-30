package top.iwesley.lyn.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.ArtworkTintTheme
import top.iwesley.lyn.music.core.model.ImportScanSummary
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.PlaybackMode
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.deriveArtworkTintTheme
import top.iwesley.lyn.music.core.model.derivePlaybackArtworkBackgroundPalette
import top.iwesley.lyn.music.core.model.displayWebDavRootUrl
import top.iwesley.lyn.music.feature.importing.formatImportScanSummary
import top.iwesley.lyn.music.platform.rememberPlatformArtworkBitmap
import top.iwesley.lyn.music.ui.mainShellColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
internal fun FavoriteToggleButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    buttonSize: androidx.compose.ui.unit.Dp = 48.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(buttonSize)) {
        Icon(
            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (isFavorite) "取消喜欢" else "标记为喜欢",
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
internal fun DetailBackButton(
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text("返回")
    }
}

@Composable
internal fun DetailSummaryCard(
    title: String,
    subtitle: String,
    supportingText: String,
    artworkLocator: String?,
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
            TrackArtworkThumbnail(artworkLocator = artworkLocator)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = album.artistName ?: "未知艺人",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${album.trackCount} 首",
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
    albumCount: Int,
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
                    text = artist.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${artist.trackCount} 首歌曲 · $albumCount 张专辑",
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

@Composable
internal fun TrackRow(
    track: Track,
    index: Int,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    showFavoriteButton: Boolean = true,
    showDuration: Boolean = true,
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
            Text(
                (index + 1).toString().padStart(2, '0'),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            TrackArtworkThumbnail(artworkLocator = track.artworkLocator)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    track.artistName ?: "未知艺人",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.width(
                    when {
                        showFavoriteButton && showDuration -> 112.dp
                        showFavoriteButton || showDuration -> 56.dp
                        else -> 0.dp
                    },
                ),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showFavoriteButton) {
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

@Composable
internal fun TrackArtworkThumbnail(
    artworkLocator: String?,
    modifier: Modifier = Modifier.size(52.dp),
) {
    val artworkBitmap = rememberPlatformArtworkBitmap(artworkLocator)
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
        if (artworkBitmap != null) {
            Image(
                bitmap = artworkBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
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
    onEdit: (() -> Unit)?,
    onToggleEnabled: () -> Unit,
    onRescan: (() -> Unit)?,
    isRescanning: Boolean,
    onDelete: () -> Unit,
    scanSummary: ImportScanSummary? = null,
    onShowScanFailures: ((ImportScanSummary) -> Unit)? = null,
) {
    val shellColors = mainShellColors
    val sourceEnabled = state.source.enabled
    val scanSummaryPresentation = buildSourceScanSummaryPresentation(
        summary = scanSummary,
        canShowFailures = onShowScanFailures != null,
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
                            top.iwesley.lyn.music.core.model.ImportSourceType.LOCAL_FOLDER -> state.source.rootReference
                            top.iwesley.lyn.music.core.model.ImportSourceType.SAMBA -> top.iwesley.lyn.music.core.model.formatSambaEndpoint(
                                server = state.source.server,
                                port = state.source.port,
                                path = state.source.path,
                            )

                            top.iwesley.lyn.music.core.model.ImportSourceType.WEBDAV ->
                                displayWebDavRootUrl(state.source.rootReference)
                            top.iwesley.lyn.music.core.model.ImportSourceType.NAVIDROME -> state.source.rootReference
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        onEdit?.let { edit ->
                            OutlinedButton(onClick = edit, enabled = enabled) {
                                Icon(Icons.Rounded.Tune, null)
                                Spacer(Modifier.width(6.dp))
                                Text("编辑")
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
                                    Text(if (isRescanning) "重扫中" else "重扫")
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onToggleEnabled, enabled = enabled) {
                            Icon(Icons.Rounded.CloudSync, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (sourceEnabled) "禁用" else "启用")
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            enabled = enabled,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Icon(Icons.Rounded.Delete, null)
                            Spacer(Modifier.width(6.dp))
                            Text("删除")
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MainShellAssistChip(
                    onClick = {},
                    label = { Text("${state.indexState?.trackCount ?: 0} 首歌曲") },
                    leadingIcon = { Icon(Icons.Rounded.LibraryMusic, null) })
                MainShellAssistChip(
                    onClick = {},
                    label = {
                        Text(
                            when {
                                !sourceEnabled -> "已禁用"
                                state.indexState?.lastError == null -> "扫描正常"
                                else -> "扫描失败"
                            },
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.CloudSync, null) })
            }
            scanSummaryPresentation?.let { presentation ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = presentation.summaryText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (presentation.showFailuresButton) {
                        TextButton(onClick = { onShowScanFailures?.invoke(presentation.summary) }) {
                            Text("查看失败")
                        }
                    }
                }
            }
            state.indexState?.lastError?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = if (sourceEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal data class SourceScanSummaryPresentation(
    val summary: ImportScanSummary,
    val summaryText: String,
    val showFailuresButton: Boolean,
)

internal fun buildSourceScanSummaryPresentation(
    summary: ImportScanSummary?,
    canShowFailures: Boolean,
): SourceScanSummaryPresentation? {
    summary ?: return null
    return SourceScanSummaryPresentation(
        summary = summary,
        summaryText = formatImportScanSummary(summary),
        showFailuresButton = summary.failedAudioFileCount > 0 && canShowFailures,
    )
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
                if (source.enabled) "已启用" else "已停用",
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
                        label = { Text("WORKFLOW") },
                        leadingIcon = { Icon(Icons.Rounded.CloudSync, null) })
                    MainShellAssistChip(
                        onClick = {},
                        label = { Text("${source.lyrics.steps.size} 步") },
                        leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onToggleEnabled) {
                Text(if (source.enabled) "停用" else "启用")
            }
            TextButton(onClick = onDelete) {
                Text("删除")
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
            TextButton(onClick = onDismiss) { Text("关闭") }
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
internal fun VinylPlaceholder(
    vinylSize: Dp,
    artworkBitmap: ImageBitmap? = null,
    artworkLocator: String? = null,
    spinning: Boolean = false,
    enableArtworkTint: Boolean = false,
    artworkDiameterFraction: Float = DEFAULT_VINYL_ARTWORK_DIAMETER_FRACTION,
    innerGlowDiameterFraction: Float = DEFAULT_VINYL_INNER_GLOW_DIAMETER_FRACTION,
    modifier: Modifier = Modifier,
) {
    val normalizedArtworkDiameterFraction = artworkDiameterFraction.coerceIn(0.2f, 1f)
    val normalizedInnerGlowDiameterFraction = innerGlowDiameterFraction
        .coerceIn(normalizedArtworkDiameterFraction, 1f)
    val resolvedArtworkBitmap = artworkBitmap ?: rememberPlatformArtworkBitmap(artworkLocator)
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
                if (resolvedArtworkBitmap != null) {
                    Image(
                        bitmap = resolvedArtworkBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(vinylSize / 4)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
                    )
                }
            }
        }
    }
}

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

internal fun modeLabel(mode: PlaybackMode): String {
    return when (mode) {
        PlaybackMode.ORDER -> "顺序播放"
        PlaybackMode.SHUFFLE -> "随机播放"
        PlaybackMode.REPEAT_ONE -> "单曲循环"
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

internal fun trackDisplayFormat(track: Track): String {
    return track.relativePath
        .substringAfterLast('.', "")
        .takeIf { it.isNotBlank() }
        ?.uppercase()
        ?: "未知"
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

internal fun formatTrackTechnicalSummary(track: Track): String {
    return listOfNotNull(
        trackDisplayFormat(track),
        formatTrackAudioQuality(track),
        formatTrackSize(track.sizeBytes),
    ).joinToString(" · ")
}

private fun formatSamplingRate(samplingRateHz: Int): String {
    val decimals = if (samplingRateHz % 1_000 == 0) 0 else 1
    return "${roundTo(samplingRateHz / 1_000.0, decimals)}kHz"
}

internal fun formatTrackSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "未知"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        sizeBytes >= gb -> "${roundTo((sizeBytes / gb), 2)} GB"
        sizeBytes >= mb -> "${roundTo((sizeBytes / mb), 1)} MB"
        sizeBytes >= kb -> "${roundTo((sizeBytes / kb), 0)} KB"
        else -> "$sizeBytes B"
    }
}

private fun roundTo(value: Double, decimals: Int): String {
    if (decimals <= 0) return value.roundToInt().toString()
    val factor = 10.0.pow(decimals)
    val rounded = (value * factor).roundToInt() / factor
    return rounded.toString()
}
