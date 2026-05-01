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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    var showingDailyRecommendation by rememberSaveable { mutableStateOf(false) }
    if (showingDailyRecommendation) {
        DailyRecommendationDetail(
            tracks = state.dailyRecommendationTracks,
            isLoading = state.isGeneratingDailyRecommendation,
            showDuration = !isMobile,
            onBack = { showingDailyRecommendation = false },
            onPlayTrack = { index ->
                buildDailyRecommendationPlayIntent(state.dailyRecommendationTracks, index)?.let(onPlayerIntent)
            },
            modifier = modifier,
        )
        return
    }
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
            DailyRecommendationCard(
                tracks = state.dailyRecommendationTracks,
                isLoading = state.isGeneratingDailyRecommendation,
                onOpen = { showingDailyRecommendation = true },
                onPlayAll = {
                    buildDailyRecommendationPlayIntent(state.dailyRecommendationTracks, 0)?.let(onPlayerIntent)
                },
            )
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
private fun DailyRecommendationCard(
    tracks: List<Track>,
    isLoading: Boolean,
    onOpen: () -> Unit,
    onPlayAll: () -> Unit,
) {
    MainShellElevatedCard {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onOpen)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SectionTitle(
                        title = "每日推荐",
                        subtitle = if (tracks.isEmpty()) "今日歌单" else "今日 ${tracks.size} 首",
                    )
                }
                OutlinedButton(
                    onClick = onPlayAll,
                    enabled = tracks.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "播放全部",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            when {
                isLoading -> RecentLoadingRow(text = "正在生成每日推荐")
                tracks.isEmpty() -> InlineEmptyState(
                    title = "暂无每日推荐",
                    body = "曲库有歌曲后会生成今日推荐。",
                )
                else -> DailyRecommendationPreview(tracks = tracks)
            }
        }
    }
}

@Composable
private fun DailyRecommendationPreview(tracks: List<Track>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tracks.take(4).forEach { track ->
            TrackArtworkThumbnail(
                artworkLocator = track.artworkLocator,
                modifier = Modifier.size(56.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = tracks.firstOrNull()?.title.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "为今天挑选的歌曲",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DailyRecommendationDetail(
    tracks: List<Track>,
    isLoading: Boolean,
    showDuration: Boolean,
    onBack: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "返回",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    SectionTitle(
                        title = "每日推荐",
                        subtitle = if (tracks.isEmpty()) "今日歌单" else "${tracks.size} 首",
                    )
                }
                OutlinedButton(
                    onClick = { onPlayTrack(0) },
                    enabled = tracks.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "播放全部",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        if (isLoading || tracks.isEmpty()) {
            item {
                MainShellElevatedCard {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (isLoading) {
                            RecentLoadingRow(text = "正在生成每日推荐")
                        } else {
                            InlineEmptyState(
                                title = "暂无每日推荐",
                                body = "曲库有歌曲后会生成今日推荐。",
                            )
                        }
                    }
                }
            }
        } else {
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                DailyRecommendationTrackRow(
                    track = track,
                    index = index,
                    showDuration = showDuration,
                    onClick = { onPlayTrack(index) },
                )
            }
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
private fun DailyRecommendationTrackRow(
    track: Track,
    index: Int,
    showDuration: Boolean,
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
                    text = buildTrackMetadataSubtitle(track),
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
private fun RecentLoadingRow(text: String = "正在加载最近播放") {
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
            text = text,
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
    return "${buildTrackMetadataSubtitle(track)} · ${formatRecentStats(recentTrack.playCount, recentTrack.lastPlayedAt)}"
}

private fun buildTrackMetadataSubtitle(track: Track): String {
    val artist = track.artistName ?: "未知艺人"
    val album = track.albumTitle?.takeIf { it.isNotBlank() }
    return if (album == null) artist else "$artist · $album"
}

internal fun buildDailyRecommendationPlayIntent(
    tracks: List<Track>,
    startIndex: Int,
): PlayerIntent.PlayTracks? {
    if (tracks.isEmpty() || startIndex !in tracks.indices) return null
    return PlayerIntent.PlayTracks(
        tracks = tracks,
        startIndex = startIndex,
    )
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
