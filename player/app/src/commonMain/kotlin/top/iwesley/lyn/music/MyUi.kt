package top.iwesley.lyn.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.time.Instant
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.RecentAlbum
import top.iwesley.lyn.music.core.model.RecentTrack
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.feature.my.MyIntent
import top.iwesley.lyn.music.feature.my.MyState
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.ui.mainShellColors

@Composable
internal fun MyTab(
    platform: PlatformDescriptor,
    state: MyState,
    onMyIntent: (MyIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onOpenAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMobile = platform.isMobilePlatform()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = if (isMobile) 16.dp else 32.dp,
            vertical = if (isMobile) 16.dp else 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            MyHeader(
                isRefreshing = state.isRefreshingNavidrome,
                onRefresh = { onMyIntent(MyIntent.RefreshNavidromeRecentPlays) },
            )
        }
        state.message?.let { message ->
            item {
                BannerCard(
                    message = message,
                    onDismiss = { onMyIntent(MyIntent.ClearMessage) },
                )
            }
        }
        item {
            RecentTracksSection(
                recentTracks = state.recentTracks,
                isLoading = state.isLoadingContent,
                showDuration = !isMobile,
                onPlayTrack = { index ->
                    onPlayerIntent(
                        PlayerIntent.PlayTracks(
                            tracks = state.recentTracks.map(RecentTrack::track),
                            startIndex = index,
                        ),
                    )
                },
            )
        }
        item {
            RecentAlbumsSection(
                recentAlbums = state.recentAlbums,
                isLoading = state.isLoadingContent,
                isMobile = isMobile,
                onOpenAlbum = onOpenAlbum,
            )
        }
    }
}

@Composable
private fun MyHeader(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionTitle(
            title = "我的",
            subtitle = if (isRefreshing) "正在同步 Navidrome 最近播放" else "最近播放",
        )
        OutlinedButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
            modifier = Modifier.align(Alignment.Top),
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = "刷新",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun RecentTracksSection(
    recentTracks: List<RecentTrack>,
    isLoading: Boolean,
    showDuration: Boolean,
    onPlayTrack: (Int) -> Unit,
) {
    MainShellElevatedCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(
                title = "最近播放歌曲",
                subtitle = if (recentTracks.isEmpty()) "" else "${recentTracks.size} 首",
            )
            when {
                isLoading -> RecentLoadingRow()
                recentTracks.isEmpty() -> InlineEmptyState(
                    title = "暂无最近播放歌曲",
                    body = "播放达到统计阈值后会显示在这里。",
                )
                else -> recentTracks.forEachIndexed { index, recentTrack ->
                    RecentTrackRow(
                        recentTrack = recentTrack,
                        index = index,
                        showDuration = showDuration,
                        onClick = { onPlayTrack(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentAlbumsSection(
    recentAlbums: List<RecentAlbum>,
    isLoading: Boolean,
    isMobile: Boolean,
    onOpenAlbum: (String) -> Unit,
) {
    MainShellElevatedCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(
                title = "最近播放专辑",
                subtitle = if (recentAlbums.isEmpty()) "" else "${recentAlbums.size} 张",
            )
            when {
                isLoading -> RecentLoadingRow()
                recentAlbums.isEmpty() -> InlineEmptyState(
                    title = "暂无最近播放专辑",
                    body = "播放专辑内歌曲后会显示在这里。",
                )
                isMobile -> recentAlbums.forEach { recentAlbum ->
                    RecentAlbumRow(
                        recentAlbum = recentAlbum,
                        onClick = { onOpenAlbum(recentAlbum.album.id) },
                    )
                }
                else -> RecentAlbumStrip(
                    recentAlbums = recentAlbums,
                    onOpenAlbum = onOpenAlbum,
                )
            }
        }
    }
}

@Composable
private fun RecentTrackRow(
    recentTrack: RecentTrack,
    index: Int,
    showDuration: Boolean,
    onClick: () -> Unit,
) {
    val shellColors = mainShellColors
    val track = recentTrack.track
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
                text = (index + 1).toString().padStart(2, '0'),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            TrackArtworkThumbnail(artworkLocator = track.artworkLocator)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildRecentTrackSubtitle(track, recentTrack),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (showDuration) {
                Text(
                    text = formatDuration(track.durationMs),
                    modifier = Modifier.width(56.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
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
private fun RecentAlbumRow(
    recentAlbum: RecentAlbum,
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
            TrackArtworkThumbnail(artworkLocator = recentAlbum.artworkLocator)
            RecentAlbumText(
                recentAlbum = recentAlbum,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${recentAlbum.album.trackCount} 首",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
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
private fun RecentAlbumStrip(
    recentAlbums: List<RecentAlbum>,
    onOpenAlbum: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(recentAlbums, key = { it.album.id }) { recentAlbum ->
            Column(
                modifier = Modifier
                    .width(188.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onOpenAlbum(recentAlbum.album.id) }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TrackArtworkThumbnail(
                    artworkLocator = recentAlbum.artworkLocator,
                    modifier = Modifier.size(164.dp),
                )
                RecentAlbumText(recentAlbum = recentAlbum)
            }
        }
    }
}

@Composable
private fun RecentAlbumText(
    recentAlbum: RecentAlbum,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = recentAlbum.album.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = recentAlbum.album.artistName ?: "未知艺人",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = buildRecentAlbumSubtitle(recentAlbum),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RecentLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = "正在加载最近播放",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InlineEmptyState(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun buildRecentTrackSubtitle(
    track: Track,
    recentTrack: RecentTrack,
): String {
    val artist = track.artistName ?: "未知艺人"
    val album = track.albumTitle?.takeIf { it.isNotBlank() }
    val metadata = if (album == null) artist else "$artist · $album"
    return "$metadata · ${formatRecentStats(recentTrack.playCount, recentTrack.lastPlayedAt)}"
}

private fun buildRecentAlbumSubtitle(recentAlbum: RecentAlbum): String {
    return formatRecentStats(recentAlbum.playCount, recentAlbum.lastPlayedAt)
}

private fun formatRecentStats(
    playCount: Int,
    lastPlayedAt: Long,
): String {
    return "播放 ${playCount.coerceAtLeast(1)} 次 · ${formatRecentPlayedAt(lastPlayedAt)}"
}

private fun formatRecentPlayedAt(lastPlayedAt: Long): String {
    return runCatching {
        Instant.fromEpochMilliseconds(lastPlayedAt)
            .toString()
            .replace('T', ' ')
            .substringBefore('.')
            .removeSuffix("Z")
            .take(16)
    }.getOrDefault("")
}
