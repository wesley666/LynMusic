package top.iwesley.lyn.music.desktop.ui

import top.iwesley.lyn.music.*

import top.iwesley.lyn.music.resources.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.ui.mainShellColors

@Composable
internal fun PlayerLyricsPane(
    state: PlayerState,
    track: Track,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onOpenLibraryNavigationTarget: ((LibraryNavigationTarget) -> Unit)? = null,
    onlineNavigationSourceId: String? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    pure: Boolean = false,
    mobilePlayback: Boolean = false,
) {
    val lyricsFont = rememberPlayerLyricsFont(state, onPlayerIntent)
    var showTrackInfoDialog by rememberSaveable(track.id, mobilePlayback) { mutableStateOf(false) }
    SharedPlayerLyricsPane(
        state = state.toLyricsUiState(track),
        lyricsStyle = state.lyricsStyle,
        lyricsFont = lyricsFont,
        onSeek = { onPlayerIntent(PlayerIntent.SeekTo(it)) },
        modifier = modifier,
        compact = compact,
        pure = pure,
        header = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    state.snapshot.currentDisplayTitle,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onOpenLibraryNavigationTarget != null) {
                    PlayerLyricsMetadataRow(
                        snapshot = state.snapshot,
                        track = track,
                        secondaryTextColor = Color.White.copy(alpha = 0.6f),
                        onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
                        onlineNavigationSourceId = onlineNavigationSourceId,
                        showTrackInfo = true,
                        onShowTrackInfo = { showTrackInfoDialog = true },
                    )
                } else {
                    PlayerLyricsPlainMetadataRow(
                        text = uiString(Res.string.lyrics_share_album_artist_summary, state.snapshot.currentDisplayAlbumTitle ?: uiString(Res.string.source_local_track_label), state.snapshot.currentDisplayArtistName ?: uiString(Res.string.common_unknown_artist)),
                        secondaryTextColor = Color.White.copy(alpha = 0.6f),
                        showTrackInfo = true,
                        onShowTrackInfo = { showTrackInfoDialog = true },
                    )
                }
            }
        },
        emptyState = {
            EmptyStateCard(
                title = uiString(Res.string.lyrics_share_lyrics_missing),
                body = uiString(Res.string.lyrics_share_fetch_priority_hint),
            )
        },
    )
    if (showTrackInfoDialog) {
        PlayerTrackInfoDialog(track = track, onDismiss = { showTrackInfoDialog = false })
    }
}

@Composable
private fun PlayerLyricsPlainMetadataRow(
    text: String,
    secondaryTextColor: Color,
    showTrackInfo: Boolean,
    onShowTrackInfo: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f, fill = !showTrackInfo),
            color = secondaryTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showTrackInfo) {
            PlayerTrackInfoButton(
                tint = secondaryTextColor,
                onClick = onShowTrackInfo,
            )
        }
    }
}

@Composable
private fun PlayerLyricsMetadataRow(
    snapshot: top.iwesley.lyn.music.core.model.PlaybackSnapshot,
    track: Track,
    secondaryTextColor: Color,
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    onlineNavigationSourceId: String?,
    showTrackInfo: Boolean,
    onShowTrackInfo: () -> Unit,
) {
    val navigationTargets = remember(
        snapshot.currentDisplayAlbumTitle,
        snapshot.currentDisplayArtistName,
        track.albumTitle,
        track.artistName,
        track.albumId,
        track.artistId,
        track.artworkLocator,
        onlineNavigationSourceId,
    ) {
        if (onlineNavigationSourceId != null) {
            deriveOnlinePlaybackLibraryNavigationTargets(
                snapshot = snapshot,
                track = track,
                sourceId = onlineNavigationSourceId,
            )
        } else {
            derivePlaybackLibraryNavigationTargets(snapshot, track)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesktopLyricsMetadataItem(
            label = uiString(Res.string.lyrics_share_artist_label),
            text = resolvePlayerLyricsMetadataLabel(
                primary = snapshot.currentDisplayArtistName,
                fallback = track.artistName,
                fallbackLabel = uiString(Res.string.common_unknown_artist),
            ),
            target = navigationTargets.artistTarget,
            secondaryTextColor = secondaryTextColor,
            onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
            modifier = Modifier.widthIn(max = 260.dp),
        )
        DesktopLyricsMetadataItem(
            label = uiString(Res.string.lyrics_share_album_label),
            text = resolvePlayerLyricsMetadataLabel(
                primary = snapshot.currentDisplayAlbumTitle,
                fallback = track.albumTitle,
                fallbackLabel = uiString(Res.string.source_local_track_label),
            ),
            target = navigationTargets.albumTarget,
            secondaryTextColor = secondaryTextColor,
            onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
            modifier = Modifier.weight(1f, fill = !showTrackInfo),
        )
        if (showTrackInfo) {
            PlayerTrackInfoButton(
                tint = secondaryTextColor,
                onClick = onShowTrackInfo,
            )
        }
    }
}

@Composable
private fun PlayerTrackInfoButton(
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = uiString(Res.string.lyrics_share_view_track_information),
            modifier = Modifier.size(18.dp),
            tint = tint,
        )
    }
}

@Composable
internal fun PlayerTrackInfoDialog(
    track: Track,
    onDismiss: () -> Unit,
) {
    val shellColors = mainShellColors
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = shellColors.secondaryText
    val audioQuality = formatTrackAudioQuality(track)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = shellColors.cardContainer,
        titleContentColor = primaryTextColor,
        textContentColor = primaryTextColor,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = uiString(Res.string.lyrics_share_track_information),
                color = primaryTextColor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerTrackInfoDialogLine(
                    label = uiString(Res.string.lyrics_share_format_label),
                    value = trackDisplayFormat(track),
                    labelColor = secondaryTextColor,
                    valueColor = primaryTextColor,
                )
                audioQuality?.let { quality ->
                    PlayerTrackInfoDialogLine(
                        label = uiString(Res.string.lyrics_share_quality_label),
                        value = quality,
                        labelColor = secondaryTextColor,
                        valueColor = primaryTextColor,
                    )
                }
                PlayerTrackInfoDialogLine(
                    label = uiString(Res.string.lyrics_share_size_label),
                    value = formatTrackSize(track.sizeBytes),
                    labelColor = secondaryTextColor,
                    valueColor = primaryTextColor,
                )
                PlayerTrackInfoDialogLine(
                    label = uiString(Res.string.common_path),
                    value = track.relativePath.ifBlank { uiString(Res.string.common_unknown) },
                    labelColor = secondaryTextColor,
                    valueColor = primaryTextColor,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(uiString(Res.string.common_close))
            }
        },
    )
}

@Composable
private fun PlayerTrackInfoDialogLine(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = labelColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DesktopLyricsMetadataItem(
    label: String,
    text: String,
    target: LibraryNavigationTarget?,
    secondaryTextColor: Color,
    onOpenLibraryNavigationTarget: (LibraryNavigationTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = secondaryTextColor,
            maxLines = 1,
        )
        Text(
            text = text,
            modifier = if (target != null) {
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenLibraryNavigationTarget(target) }
            } else {
                Modifier
            },
            color = secondaryTextColor,
            fontWeight = if (target != null) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun resolvePlayerLyricsMetadataLabel(
    primary: String?,
    fallback: String?,
    fallbackLabel: String,
): String {
    return primary?.trim()?.takeIf { it.isNotBlank() }
        ?: fallback?.trim()?.takeIf { it.isNotBlank() }
        ?: fallbackLabel
}
