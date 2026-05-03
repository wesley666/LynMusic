package top.iwesley.lyn.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.time.Instant
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.RecentAlbum
import top.iwesley.lyn.music.core.model.RecentTrack
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.feature.my.MyIntent
import top.iwesley.lyn.music.feature.my.MyState
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.platform.PlatformBackHandler
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
    var detailPage by rememberSaveable { mutableStateOf<MyDetailPage?>(null) }
    val mainListState = rememberLazyListState()
    val dailyRecommendationCarouselState = rememberLazyListState()
    PlatformBackHandler(enabled = detailPage != null) {
        detailPage = null
    }
    when (detailPage) {
        MyDetailPage.DailyRecommendation -> {
            DailyRecommendationDetail(
                tracks = state.dailyRecommendationTracks,
                isLoading = state.isGeneratingDailyRecommendation,
                showDuration = !isMobile,
                onBack = { detailPage = null },
                onPlayTrack = { index ->
                    buildDailyRecommendationPlayIntent(state.dailyRecommendationTracks, index)?.let(onPlayerIntent)
                },
                modifier = modifier,
            )
            return
        }

        MyDetailPage.RecentTracks -> {
            RecentTracksDetail(
                recentTracks = state.recentTracks,
                isLoading = state.isLoadingContent,
                showDuration = !isMobile,
                onBack = { detailPage = null },
                onPlayTrack = { index ->
                    buildRecentTracksPlayIntent(state.recentTracks, index)?.let(onPlayerIntent)
                },
                modifier = modifier,
            )
            return
        }

        MyDetailPage.RecentAlbums -> {
            RecentAlbumsDetail(
                recentAlbums = state.recentAlbums,
                isLoading = state.isLoadingContent,
                compact = isMobile,
                onBack = { detailPage = null },
                onOpenAlbum = onOpenAlbum,
                modifier = modifier,
            )
            return
        }

        null -> Unit
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = mainListState,
        contentPadding = PaddingValues(
            horizontal = if (isMobile) 16.dp else 32.dp,
            vertical = if (isMobile) 16.dp else 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
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
                compact = isMobile,
                carouselState = dailyRecommendationCarouselState,
                onOpen = { detailPage = MyDetailPage.DailyRecommendation },
                onPlayAll = {
                    buildDailyRecommendationPlayIntent(state.dailyRecommendationTracks, 0)?.let(onPlayerIntent)
                },
                onPlayTrack = { index ->
                    buildDailyRecommendationPlayIntent(state.dailyRecommendationTracks, index)?.let(onPlayerIntent)
                },
            )
        }
        item {
            RecentTracksSection(
                recentTracks = state.recentTracks,
                isLoading = state.isLoadingContent,
                isMobile = isMobile,
                showDuration = !isMobile,
                onPlayTrack = { index ->
                    buildRecentTracksPlayIntent(state.recentTracks, index)?.let(onPlayerIntent)
                },
                onViewAll = { detailPage = MyDetailPage.RecentTracks },
            )
        }
        item {
            RecentAlbumsSection(
                recentAlbums = state.recentAlbums,
                isLoading = state.isLoadingContent,
                isMobile = isMobile,
                onOpenAlbum = onOpenAlbum,
                onViewAll = { detailPage = MyDetailPage.RecentAlbums },
            )
        }
    }
}

private enum class MyDetailPage {
    DailyRecommendation,
    RecentTracks,
    RecentAlbums,
}

@Composable
private fun DailyRecommendationCard(
    tracks: List<Track>,
    isLoading: Boolean,
    compact: Boolean,
    carouselState: LazyListState,
    onOpen: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayTrack: (Int) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val titleColor = colorScheme.onSurface
    val mutedColor = colorScheme.onSurfaceVariant
    val accentColor = colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "每日推荐",
                    modifier = Modifier.weight(1f),
                    color = titleColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "查看全部 >",
                    color = mutedColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when {
                isLoading -> RecentLoadingRow(text = "正在生成每日推荐")
                tracks.isEmpty() -> InlineEmptyState(
                    title = "暂无每日推荐",
                    body = "曲库有歌曲后会生成今日推荐。",
                )
                compact -> DailyRecommendationMobileCarousel(
                    tracks = tracks,
                    state = carouselState,
                    accentColor = accentColor,
                    onOpen = onOpen,
                    onPlayTrack = onPlayTrack,
                )
                else -> DailyRecommendationPreview(
                    tracks = tracks,
                    compact = compact,
                    titleColor = titleColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    onPlayAll = onPlayAll,
                    onPlayTrack = onPlayTrack,
                )
            }
        }
    }
}

@Composable
private fun DailyRecommendationMobileCarousel(
    tracks: List<Track>,
    state: LazyListState,
    accentColor: Color,
    onOpen: () -> Unit,
    onPlayTrack: (Int) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = (maxWidth * 0.68f)
            .coerceAtLeast(218.dp)
            .coerceAtMost(300.dp)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = state,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(
                items = dailyRecommendationCarouselItems(tracks),
                key = { _, track -> track.id },
            ) { index, track ->
                DailyRecommendationCarouselCard(
                    track = track,
                    modifier = Modifier
                        .width(cardWidth)
                        .height(156.dp),
                    accentColor = accentColor,
                    onOpen = onOpen,
                    onPlay = { onPlayTrack(index) },
                )
            }
        }
    }
}

@Composable
private fun DailyRecommendationCarouselCard(
    track: Track,
    modifier: Modifier,
    accentColor: Color,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(accentColor.copy(alpha = 0.18f))
            .clickable(onClick = onOpen),
    ) {
        DailyRecommendationArtwork(
            artworkLocator = track.artworkLocator,
            artworkCacheKey = trackArtworkCacheKey(track),
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(18.dp),
            accentColor = accentColor,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.28f)),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp, end = 76.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = track.title,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = track.artistName ?: "未知艺人",
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.92f))
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun DailyRecommendationPreview(
    tracks: List<Track>,
    compact: Boolean,
    titleColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onPlayAll: () -> Unit,
    onPlayTrack: (Int) -> Unit,
) {
    val heroSize = if (compact) 112.dp else 148.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DailyRecommendationHeroArtwork(
            artworkLocator = tracks.firstOrNull()?.artworkLocator,
            artworkCacheKey = tracks.firstOrNull()?.let(::trackArtworkCacheKey),
            modifier = Modifier.size(heroSize),
            accentColor = accentColor,
            onPlayAll = onPlayAll,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
        ) {
            tracks.take(3).forEachIndexed { index, track ->
                DailyRecommendationPreviewTrackRow(
                    track = track,
                    titleColor = titleColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    onPlay = { onPlayTrack(index) },
                )
            }
        }
    }
}

@Composable
private fun DailyRecommendationHeroArtwork(
    artworkLocator: String?,
    artworkCacheKey: String?,
    modifier: Modifier,
    accentColor: Color,
    onPlayAll: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
    ) {
        DailyRecommendationArtwork(
            artworkLocator = artworkLocator,
            artworkCacheKey = artworkCacheKey,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(14.dp),
            accentColor = accentColor,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.92f))
                .clickable(onClick = onPlayAll),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun DailyRecommendationPreviewTrackRow(
    track: Track,
    titleColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onPlay: () -> Unit,
) {
    TrackActionContainer(
        track = track,
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DailyRecommendationArtwork(
            artworkLocator = track.artworkLocator,
            artworkCacheKey = trackArtworkCacheKey(track),
            modifier = Modifier.size(38.dp),
            shape = RoundedCornerShape(9.dp),
            accentColor = accentColor,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = track.title,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = track.artistName ?: "未知艺人",
                color = mutedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun DailyRecommendationArtwork(
    artworkLocator: String?,
    artworkCacheKey: String? = null,
    modifier: Modifier,
    shape: RoundedCornerShape,
    accentColor: Color,
) {
    Box(
        modifier = modifier
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        LynArtworkImage(
            artworkLocator = artworkLocator,
            contentDescription = null,
            artworkCacheKey = artworkCacheKey,
            maxDecodeSizePx = ArtworkDecodeSize.Card,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
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
                MyDetailBackButton(onBack = onBack)
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
private fun RecentTracksDetail(
    recentTracks: List<RecentTrack>,
    isLoading: Boolean,
    showDuration: Boolean,
    onBack: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = if (showDuration) 32.dp else 16.dp,
            vertical = if (showDuration) 28.dp else 18.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MyDetailHeader(
                title = "最近播放歌曲",
                subtitle = if (recentTracks.isEmpty()) "" else "${recentTracks.size} 首",
                onBack = onBack,
            )
        }
        when {
            isLoading -> item { RecentLoadingRow() }
            recentTracks.isEmpty() -> item {
                InlineEmptyState(
                    title = "暂无最近播放歌曲",
                    body = "播放达到统计阈值后会显示在这里。",
                )
            }
            else -> itemsIndexed(recentTracks, key = { _, recentTrack -> recentTrack.track.id }) { index, recentTrack ->
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

@Composable
private fun RecentAlbumsDetail(
    recentAlbums: List<RecentAlbum>,
    isLoading: Boolean,
    compact: Boolean,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = if (compact) 16.dp else 32.dp,
            vertical = if (compact) 18.dp else 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MyDetailHeader(
                title = "最近播放专辑",
                subtitle = if (recentAlbums.isEmpty()) "" else "${recentAlbums.size} 张",
                onBack = onBack,
            )
        }
        when {
            isLoading -> item { RecentLoadingRow() }
            recentAlbums.isEmpty() -> item {
                InlineEmptyState(
                    title = "暂无最近播放专辑",
                    body = "播放专辑内歌曲后会显示在这里。",
                )
            }
            else -> items(recentAlbums, key = { recentAlbum -> recentAlbum.album.id }) { recentAlbum ->
                RecentAlbumRow(
                    recentAlbum = recentAlbum,
                    onClick = { onOpenAlbum(recentAlbum.album.id) },
                )
            }
        }
    }
}

@Composable
private fun MyDetailHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MyDetailBackButton(onBack = onBack)
        Box(modifier = Modifier.weight(1f)) {
            SectionTitle(
                title = title,
                subtitle = subtitle,
            )
        }
    }
}

@Composable
private fun MyDetailBackButton(
    onBack: () -> Unit,
) {
    IconButton(onClick = onBack) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回",
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun RecentTracksSection(
    recentTracks: List<RecentTrack>,
    isLoading: Boolean,
    isMobile: Boolean,
    showDuration: Boolean,
    onPlayTrack: (Int) -> Unit,
    onViewAll: () -> Unit,
) {
    val previewTracks = recentPreviewItems(recentTracks, isMobile)
    Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RecentSectionHeader(
            title = "最近播放歌曲",
            subtitle = if (recentTracks.isEmpty()) "" else "${recentTracks.size} 首",
            showViewAll = !isLoading && recentTracks.isNotEmpty(),
            onViewAll = onViewAll,
        )
        when {
            isLoading -> RecentLoadingRow()
            recentTracks.isEmpty() -> InlineEmptyState(
                title = "暂无最近播放歌曲",
                body = "播放达到统计阈值后会显示在这里。",
            )
            isMobile -> previewTracks.forEachIndexed { index, recentTrack ->
                RecentTrackRow(
                    recentTrack = recentTrack,
                    index = index,
                    showDuration = showDuration,
                    onClick = { onPlayTrack(index) },
                )
            }
            else -> RecentTrackPreviewRow(
                recentTracks = previewTracks,
                onPlayTrack = { index -> onPlayTrack(index) },
            )
        }
    }
}

@Composable
private fun RecentTrackPreviewRow(
    recentTracks: List<RecentTrack>,
    onPlayTrack: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        recentTracks.forEachIndexed { index, recentTrack ->
            RecentTrackPreviewCard(
                recentTrack = recentTrack,
                modifier = Modifier.weight(1f),
                onClick = { onPlayTrack(index) },
            )
        }
        repeat((recentPreviewLimit(isMobile = false) - recentTracks.size).coerceAtLeast(0)) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RecentTrackPreviewCard(
    recentTrack: RecentTrack,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val track = recentTrack.track
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            DailyRecommendationArtwork(
                artworkLocator = track.artworkLocator,
                artworkCacheKey = trackArtworkCacheKey(track),
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                accentColor = accentColor,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = track.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = buildTrackMetadataSubtitle(track),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RecentAlbumsSection(
    recentAlbums: List<RecentAlbum>,
    isLoading: Boolean,
    isMobile: Boolean,
    onOpenAlbum: (String) -> Unit,
    onViewAll: () -> Unit,
) {
    val previewAlbums = recentPreviewItems(recentAlbums, isMobile)
    Column(
        modifier = Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RecentSectionHeader(
            title = "最近播放专辑",
            subtitle = if (recentAlbums.isEmpty()) "" else "${recentAlbums.size} 张",
            showViewAll = !isLoading && recentAlbums.isNotEmpty(),
            onViewAll = onViewAll,
        )
        when {
            isLoading -> RecentLoadingRow()
            recentAlbums.isEmpty() -> InlineEmptyState(
                title = "暂无最近播放专辑",
                body = "播放专辑内歌曲后会显示在这里。",
            )
            isMobile -> previewAlbums.forEach { recentAlbum ->
                RecentAlbumRow(
                    recentAlbum = recentAlbum,
                    onClick = { onOpenAlbum(recentAlbum.album.id) },
                )
            }
            else -> RecentAlbumPreviewRow(
                recentAlbums = previewAlbums,
                onOpenAlbum = onOpenAlbum,
            )
        }
    }
}

@Composable
private fun RecentSectionHeader(
    title: String,
    subtitle: String,
    showViewAll: Boolean,
    onViewAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            SectionTitle(
                title = title,
                subtitle = subtitle,
            )
        }
        if (showViewAll) {
            Text(
                text = "查看全部 >",
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onViewAll)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
            )
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
        TrackActionContainer(
            track = track,
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            TrackArtworkThumbnail(
                artworkLocator = track.artworkLocator,
                artworkCacheKey = trackArtworkCacheKey(track),
            )
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
        TrackActionContainer(
            track = track,
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            TrackArtworkThumbnail(
                artworkLocator = track.artworkLocator,
                artworkCacheKey = trackArtworkCacheKey(track),
            )
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
private fun RecentAlbumPreviewRow(
    recentAlbums: List<RecentAlbum>,
    onOpenAlbum: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        recentAlbums.forEach { recentAlbum ->
            RecentAlbumPreviewCard(
                recentAlbum = recentAlbum,
                modifier = Modifier.weight(1f),
                onClick = { onOpenAlbum(recentAlbum.album.id) },
            )
        }
        repeat((recentPreviewLimit(isMobile = false) - recentAlbums.size).coerceAtLeast(0)) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RecentAlbumPreviewCard(
    recentAlbum: RecentAlbum,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DailyRecommendationArtwork(
            artworkLocator = recentAlbum.artworkLocator,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            accentColor = MaterialTheme.colorScheme.primary,
        )
        RecentAlbumText(recentAlbum = recentAlbum)
    }
}

internal fun recentPreviewLimit(isMobile: Boolean): Int {
    return if (isMobile) 3 else 6
}

internal fun recentPreviewUsesScrolling(isMobile: Boolean): Boolean {
    return false
}

internal fun <T> recentPreviewItems(
    items: List<T>,
    isMobile: Boolean,
): List<T> {
    return items.take(recentPreviewLimit(isMobile))
}

internal fun buildRecentTracksPlayIntent(
    recentTracks: List<RecentTrack>,
    startIndex: Int,
): PlayerIntent.PlayTracks? {
    if (recentTracks.isEmpty() || startIndex !in recentTracks.indices) return null
    return PlayerIntent.PlayTracks(
        tracks = recentTracks.map { it.track },
        startIndex = startIndex,
    )
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

internal fun dailyRecommendationCarouselItems(
    tracks: List<Track>,
): List<Track> = tracks.take(DAILY_RECOMMENDATION_CAROUSEL_LIMIT)

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

private const val DAILY_RECOMMENDATION_CAROUSEL_LIMIT = 10
