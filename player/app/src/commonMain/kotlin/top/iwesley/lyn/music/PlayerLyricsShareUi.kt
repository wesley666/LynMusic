package top.iwesley.lyn.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.ArtworkTintTheme
import top.iwesley.lyn.music.core.model.DEFAULT_LYRICS_SHARE_FONT_KEY
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
import top.iwesley.lyn.music.core.model.LyricsShareArtworkTintSpec
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsShareCardSpec
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.LyricsShareTemplate
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.argbWithAlpha
import top.iwesley.lyn.music.core.model.buildLyricsShareTitleArtistLine
import top.iwesley.lyn.music.core.model.parseLyricsShareImportedFontHash
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.domain.parseEnhancedLyricsPresentation
import top.iwesley.lyn.music.platform.PlatformBackHandler
import top.iwesley.lyn.music.platform.lyricsSharePreviewFontFamily
import top.iwesley.lyn.music.platform.rememberPlatformArtworkBitmap
import top.iwesley.lyn.music.platform.rememberPlatformImageBitmap
import top.iwesley.lyn.music.ui.mainShellColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun PlayerLyricsPane(
    state: PlayerState,
    track: Track,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onOpenLibraryNavigationTarget: ((LibraryNavigationTarget) -> Unit)? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    mobilePlayback: Boolean = false,
) {
    val listState = rememberLazyListState()
    val lyricsPrimaryTextColor = Color.White
    val lyricsSecondaryTextColor = Color.White.copy(alpha = 0.6f)
    var showTrackInfoDialog by rememberSaveable(track.id, mobilePlayback) { mutableStateOf(false) }
    val lyrics = state.lyrics
    val enhancedLyricsPresentation = remember(lyrics) {
        lyrics?.let { document ->
            parseEnhancedLyricsPresentation(
                rawPayload = document.rawPayload,
                fallbackDocument = document,
            )
        }
    }
    val visibleLyricsLines = remember(lyrics, enhancedLyricsPresentation) {
        lyrics?.let { buildVisiblePlayerLyricsLines(it, enhancedLyricsPresentation) }.orEmpty()
    }
    val highlightedVisibleIndex = remember(visibleLyricsLines, state.highlightedLineIndex) {
        resolveVisiblePlayerLyricsHighlightedIndex(visibleLyricsLines, state.highlightedLineIndex)
    }
    LaunchedEffect(track.id, lyrics, state.highlightedLineIndex, visibleLyricsLines) {
        val targetLyrics = lyrics ?: return@LaunchedEffect
        val targetIndex = resolveVisiblePlayerLyricsScrollTarget(
            lyrics = targetLyrics,
            visibleLines = visibleLyricsLines,
            highlightedRawIndex = state.highlightedLineIndex,
        ) ?: return@LaunchedEffect
        if (targetIndex !in visibleLyricsLines.indices) return@LaunchedEffect
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == targetIndex }) {
            listState.scrollToItem(targetIndex)
            withFrameNanos { }
        }
        val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
            ?: return@LaunchedEffect
        val viewportCenter =
            (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
        val itemCenter = itemInfo.offset + itemInfo.size / 2
        val delta = (itemCenter - viewportCenter).toFloat()
        if (abs(delta) > 1f) {
            listState.scrollBy(delta)
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (compact) 8.dp else 12.dp,
                    vertical = if (compact) 8.dp else 14.dp
                ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (!compact) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        state.snapshot.currentDisplayTitle,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = lyricsPrimaryTextColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (onOpenLibraryNavigationTarget != null) {
                        PlayerLyricsMetadataRow(
                            snapshot = state.snapshot,
                            track = track,
                            secondaryTextColor = lyricsSecondaryTextColor,
                            onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
                            showTrackInfo = mobilePlayback,
                            onShowTrackInfo = { showTrackInfoDialog = true },
                        )
                    } else {
                        PlayerLyricsPlainMetadataRow(
                            text = "专辑：${state.snapshot.currentDisplayAlbumTitle ?: "本地曲目"}    歌手：${state.snapshot.currentDisplayArtistName ?: "未知艺人"}",
                            secondaryTextColor = lyricsSecondaryTextColor,
                            showTrackInfo = mobilePlayback,
                            onShowTrackInfo = { showTrackInfoDialog = true },
                        )
                    }
                    if (!mobilePlayback) {
                        Text(
                            track.relativePath,
                            color = lyricsSecondaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "格式：${trackDisplayFormat(track)}    大小：${formatTrackSize(track.sizeBytes)}",
                            color = lyricsSecondaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (!compact || state.isLyricsLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.isLyricsLoading) {
                        Text(
                            "正在请求歌词...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = lyricsSecondaryTextColor,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    if (!compact) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconButton(
                                onClick = { onPlayerIntent(PlayerIntent.OpenLyricsShare) },
                                enabled = state.lyrics != null && !state.isLyricsLoading,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Share,
                                    contentDescription = "分享歌词",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (state.lyrics != null && !state.isLyricsLoading) {
                                        Color.White.copy(alpha = 0.92f)
                                    } else {
                                        lyricsSecondaryTextColor.copy(alpha = 0.45f)
                                    },
                                )
                            }
                            IconButton(
                                onClick = { onPlayerIntent(PlayerIntent.OpenManualLyricsSearch) },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = "手动搜索",
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White.copy(alpha = 0.92f),
                                )
                            }
                        }
                    }
                }
            }
            if (lyrics == null || visibleLyricsLines.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyStateCard(
                        title = "暂时没有歌词",
                        body = "会先使用本地缓存与内嵌歌词，拿不到时再按当前标题和歌手请求。",
                    )
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val centerPadding =
                        (maxHeight / 2 - 36.dp).coerceAtLeast(if (compact) 56.dp else 86.dp)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = if (compact) 540.dp else 520.dp),
                        state = listState,
                        contentPadding = PaddingValues(vertical = centerPadding),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
                    ) {
                        itemsIndexed(visibleLyricsLines) { index, visibleLine ->
                            val line = visibleLine.line
                            val enhancedLine = visibleLine.enhancedLine
                            val translationText = enhancedLine?.translationText
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() }
                            val currentLyricsPositionMs = state.snapshot.positionMs + lyrics.offsetMs
                            val distance = if (highlightedVisibleIndex >= 0) {
                                abs(index - highlightedVisibleIndex)
                            } else {
                                Int.MAX_VALUE
                            }
                            val targetAlpha = when {
                                highlightedVisibleIndex < 0 -> 0.6f
                                distance == 0 -> 1f
                                distance == 1 -> 0.72f
                                distance == 2 -> 0.5f
                                else -> 0.34f
                            }
                            val targetScale = when {
                                highlightedVisibleIndex < 0 -> 1f
                                distance == 0 -> 1.08f
                                distance == 1 -> 1.01f
                                else -> 1f
                            }
                            val animatedAlpha by animateFloatAsState(targetValue = targetAlpha)
                            val animatedScale by animateFloatAsState(targetValue = targetScale)
                            val animatedColor by animateColorAsState(
                                targetValue = if (index == highlightedVisibleIndex) {
                                    lyricsPrimaryTextColor
                                } else {
                                    lyricsPrimaryTextColor
                                },
                            )
                            val lineModifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer(
                                    alpha = animatedAlpha,
                                    scaleX = animatedScale,
                                    scaleY = animatedScale,
                                )
                            val isHighlighted = index == highlightedVisibleIndex
                            val hasEnhancedSegments = enhancedLine?.segments?.isNotEmpty() == true
                            if (translationText != null) {
                                Column(
                                    modifier = lineModifier,
                                    verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp),
                                ) {
                                    if (isHighlighted && hasEnhancedSegments) {
                                        EnhancedLyricsLineText(
                                            line = enhancedLine,
                                            currentPositionMs = currentLyricsPositionMs,
                                            activeColor = animatedColor,
                                            inactiveColor = lyricsSecondaryTextColor.copy(alpha = 0.78f),
                                            style = MaterialTheme.typography.headlineSmall,
                                            textAlign = TextAlign.Start,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    } else {
                                        Text(
                                            text = line.text,
                                            style = if (isHighlighted) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                                            color = animatedColor,
                                            textAlign = TextAlign.Start,
                                            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    Text(
                                        text = translationText,
                                        style = if (isHighlighted) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                        color = lyricsSecondaryTextColor.copy(alpha = if (isHighlighted) 0.9f else 0.72f),
                                        textAlign = TextAlign.Start,
                                        fontWeight = FontWeight.Normal,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            } else if (isHighlighted && hasEnhancedSegments) {
                                EnhancedLyricsLineText(
                                    line = enhancedLine,
                                    currentPositionMs = currentLyricsPositionMs,
                                    activeColor = animatedColor,
                                    inactiveColor = lyricsSecondaryTextColor.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.headlineSmall,
                                    textAlign = TextAlign.Start,
                                    fontWeight = FontWeight.Bold,
                                    modifier = lineModifier,
                                )
                            } else {
                                Text(
                                    text = line.text,
                                    style = if (isHighlighted) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                                    color = animatedColor,
                                    textAlign = TextAlign.Start,
                                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                                    modifier = lineModifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (showTrackInfoDialog && mobilePlayback) {
        PlayerTrackInfoDialog(
            track = track,
            onDismiss = { showTrackInfoDialog = false },
        )
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
    showTrackInfo: Boolean,
    onShowTrackInfo: () -> Unit,
) {
    val navigationTargets = remember(
        snapshot.currentDisplayAlbumTitle,
        snapshot.currentDisplayArtistName,
        track.albumTitle,
        track.artistName,
    ) {
        derivePlaybackLibraryNavigationTargets(snapshot, track)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesktopLyricsMetadataItem(
            label = "歌手：",
            text = resolvePlayerLyricsMetadataLabel(
                primary = snapshot.currentDisplayArtistName,
                fallback = track.artistName,
                fallbackLabel = "未知艺人",
            ),
            target = navigationTargets.artistTarget,
            secondaryTextColor = secondaryTextColor,
            onOpenLibraryNavigationTarget = onOpenLibraryNavigationTarget,
            modifier = Modifier.widthIn(max = 260.dp),
        )
        DesktopLyricsMetadataItem(
            label = "专辑：",
            text = resolvePlayerLyricsMetadataLabel(
                primary = snapshot.currentDisplayAlbumTitle,
                fallback = track.albumTitle,
                fallbackLabel = "本地曲目",
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
            contentDescription = "查看歌曲信息",
            modifier = Modifier.size(18.dp),
            tint = tint,
        )
    }
}

@Composable
private fun PlayerTrackInfoDialog(
    track: Track,
    onDismiss: () -> Unit,
) {
    val shellColors = mainShellColors
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = shellColors.secondaryText
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = shellColors.cardContainer,
        titleContentColor = primaryTextColor,
        textContentColor = primaryTextColor,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = "歌曲信息",
                color = primaryTextColor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerTrackInfoDialogLine(
                    label = "格式",
                    value = trackDisplayFormat(track),
                    labelColor = secondaryTextColor,
                    valueColor = primaryTextColor,
                )
                PlayerTrackInfoDialogLine(
                    label = "大小",
                    value = formatTrackSize(track.sizeBytes),
                    labelColor = secondaryTextColor,
                    valueColor = primaryTextColor,
                )
                PlayerTrackInfoDialogLine(
                    label = "路径",
                    value = track.relativePath.ifBlank { "未知" },
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
                Text("关闭")
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

internal fun resolveLyricsScrollTarget(
    lyrics: LyricsDocument?,
    highlightedLineIndex: Int,
): Int? {
    val lines = lyrics?.lines ?: return null
    if (lines.isEmpty()) return null
    return when {
        highlightedLineIndex in lines.indices -> highlightedLineIndex
        lyrics.isSynced -> 0
        else -> null
    }
}

@Composable
internal fun ManualLyricsSearchOverlay(
    state: PlayerState,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LyricsSearchOverlayDialog(
        state = LyricsSearchDialogState(
            headerTitle = "手动搜索歌词",
            headerSubtitle = buildString {
                append(state.snapshot.currentDisplayTitle.ifBlank { "当前歌曲" })
                state.snapshot.currentDisplayArtistName?.takeIf { it.isNotBlank() }?.let {
                    append(" · ")
                    append(it)
                }
            },
            title = state.manualLyricsTitle,
            artistName = state.manualLyricsArtistName,
            albumTitle = state.manualLyricsAlbumTitle,
            isLoading = state.isManualLyricsSearchLoading,
            hasResult = state.hasManualLyricsSearchResult,
            directResults = state.manualLyricsResults,
            workflowResults = state.manualWorkflowSongResults,
            error = state.manualLyricsError,
        ),
        strings = LyricsSearchDialogStrings(
            formSubtitle = "修改标题、歌手、专辑后重新向已启用歌词源搜索。",
            resultsAppliedSubtitle = "点选任一结果后选择应用方式。",
        ),
        onDismiss = { onPlayerIntent(PlayerIntent.DismissManualLyricsSearch) },
        onTitleChanged = { onPlayerIntent(PlayerIntent.ManualLyricsTitleChanged(it)) },
        onArtistChanged = { onPlayerIntent(PlayerIntent.ManualLyricsArtistChanged(it)) },
        onAlbumChanged = { onPlayerIntent(PlayerIntent.ManualLyricsAlbumChanged(it)) },
        onSearch = { onPlayerIntent(PlayerIntent.SearchManualLyrics) },
        onApplyDirectCandidate = { candidate, mode ->
            onPlayerIntent(PlayerIntent.ApplyManualLyricsCandidate(candidate, mode))
        },
        onApplyWorkflowCandidate = { candidate, mode ->
            onPlayerIntent(PlayerIntent.ApplyWorkflowSongCandidate(candidate, mode))
        },
        modifier = modifier,
    )
}

@Composable
private fun ManualLyricsSearchFormPane(
    state: PlayerState,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryTextColor = Color.White.copy(alpha = 0.96f)
    val secondaryTextColor = Color.White.copy(alpha = 0.72f)
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = primaryTextColor,
        unfocusedTextColor = primaryTextColor,
        focusedLabelColor = secondaryTextColor,
        unfocusedLabelColor = secondaryTextColor,
        cursorColor = primaryTextColor,
        focusedBorderColor = Color.White.copy(alpha = 0.34f),
        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
        focusedPlaceholderColor = secondaryTextColor,
        unfocusedPlaceholderColor = secondaryTextColor,
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(
            title = "搜索条件",
            subtitle = "修改标题、歌手、专辑后重新向已启用歌词源搜索。",
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
            ) {
                val density = LocalDensity.current
                val layoutProfile = buildLayoutProfile(
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                    platform = currentPlatformDescriptor,
                    density = density,
                )
                val stackedFields = layoutProfile.isCompactLayout
                val buttonSpacing = if (stackedFields) 8.dp else 10.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ImeAwareOutlinedTextField(
                        value = state.manualLyricsTitle,
                        onValueChange = { onPlayerIntent(PlayerIntent.ManualLyricsTitleChanged(it)) },
                        label = { Text("标题") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        singleLine = true,
                        colors = textFieldColors,
                    )
                    if (stackedFields) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ImeAwareOutlinedTextField(
                                value = state.manualLyricsArtistName,
                                onValueChange = {
                                    onPlayerIntent(
                                        PlayerIntent.ManualLyricsArtistChanged(
                                            it
                                        )
                                    )
                                },
                                label = { Text("歌手") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                singleLine = true,
                                colors = textFieldColors,
                            )
                            ImeAwareOutlinedTextField(
                                value = state.manualLyricsAlbumTitle,
                                onValueChange = {
                                    onPlayerIntent(
                                        PlayerIntent.ManualLyricsAlbumChanged(
                                            it
                                        )
                                    )
                                },
                                label = { Text("专辑") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                singleLine = true,
                                colors = textFieldColors,
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ImeAwareOutlinedTextField(
                                value = state.manualLyricsArtistName,
                                onValueChange = {
                                    onPlayerIntent(
                                        PlayerIntent.ManualLyricsArtistChanged(
                                            it
                                        )
                                    )
                                },
                                label = { Text("歌手") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp),
                                singleLine = true,
                                colors = textFieldColors,
                            )
                            ImeAwareOutlinedTextField(
                                value = state.manualLyricsAlbumTitle,
                                onValueChange = {
                                    onPlayerIntent(
                                        PlayerIntent.ManualLyricsAlbumChanged(
                                            it
                                        )
                                    )
                                },
                                label = { Text("专辑") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp),
                                singleLine = true,
                                colors = textFieldColors,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { onPlayerIntent(PlayerIntent.DismissManualLyricsSearch) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryTextColor),
                        ) {
                            Text("取消", maxLines = 1)
                        }
                        Button(
                            onClick = { onPlayerIntent(PlayerIntent.SearchManualLyrics) },
                            enabled = !state.isManualLyricsSearchLoading && state.manualLyricsTitle.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.14f),
                                contentColor = primaryTextColor,
                                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                disabledContentColor = secondaryTextColor,
                            ),
                        ) {
                            Text(
                                if (state.isManualLyricsSearchLoading) "搜索中..." else "搜索",
                                maxLines = 1
                            )
                        }
                    }
                    state.manualLyricsError?.let { error ->
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(
                                    alpha = 0.92f
                                )
                            ),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(
                                text = error,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualLyricsSearchResultsPane(
    state: PlayerState,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryTextColor = Color.White.copy(alpha = 0.96f)
    val secondaryTextColor = Color.White.copy(alpha = 0.72f)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(
            title = "搜索结果",
            subtitle = when {
                state.isManualLyricsSearchLoading -> "正在请求已启用的歌词源。"
                state.manualLyricsResults.isNotEmpty() || state.manualWorkflowSongResults.isNotEmpty() -> "点选任一结果即可直接应用到当前歌曲。"
                state.hasManualLyricsSearchResult -> "当前没有可解析结果，可以继续调整搜索条件。"
                else -> "直接歌词结果和 Workflow 歌曲候选会显示在这里。"
            },
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        ) {
            when {
                state.isManualLyricsSearchLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "正在请求已启用的歌词源...",
                            color = secondaryTextColor,
                        )
                    }
                }

                state.manualLyricsResults.isNotEmpty() || state.manualWorkflowSongResults.isNotEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (state.manualLyricsResults.isNotEmpty()) {
                            Text(
                                "直接歌词结果",
                                color = primaryTextColor,
                                fontWeight = FontWeight.SemiBold
                            )
                            state.manualLyricsResults.forEach { candidate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            onPlayerIntent(
                                                PlayerIntent.ApplyManualLyricsCandidate(
                                                    candidate
                                                )
                                            )
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TrackArtworkThumbnail(
                                        artworkLocator = candidate.artworkLocator,
                                        modifier = Modifier.size(56.dp),
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                candidate.sourceName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = primaryTextColor,
                                            )
                                            Text(
                                                "${if (candidate.document.isSynced) "同步" else "纯文本"} · ${candidate.document.lines.size} 行",
                                                color = secondaryTextColor,
                                            )
                                        }
                                        candidate.title?.takeIf { it.isNotBlank() }?.let { title ->
                                            Text(
                                                title,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = primaryTextColor,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                        manualLyricsCandidateMetadata(candidate)?.let { metadata ->
                                            Text(
                                                metadata,
                                                color = secondaryTextColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Text(
                                            manualLyricsPreview(candidate),
                                            color = primaryTextColor.copy(alpha = 0.84f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                        if (state.manualWorkflowSongResults.isNotEmpty()) {
                            Text(
                                "Workflow 歌曲候选",
                                color = primaryTextColor,
                                fontWeight = FontWeight.SemiBold
                            )
                            state.manualWorkflowSongResults.forEach { candidate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            onPlayerIntent(
                                                PlayerIntent.ApplyWorkflowSongCandidate(
                                                    candidate
                                                )
                                            )
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TrackArtworkThumbnail(
                                        artworkLocator = candidate.imageUrl,
                                        modifier = Modifier.size(56.dp),
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                candidate.sourceName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = primaryTextColor,
                                            )
                                            candidate.durationSeconds?.let { seconds ->
                                                Text(
                                                    formatLyricsCandidateDuration(seconds),
                                                    color = secondaryTextColor,
                                                )
                                            }
                                        }
                                        Text(
                                            candidate.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = primaryTextColor,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            manualWorkflowCandidatePreview(candidate),
                                            color = primaryTextColor.copy(alpha = 0.84f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                state.hasManualLyricsSearchResult -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyStateCard(
                            title = "没有找到可用歌词",
                            body = "当前已启用歌词源都没有返回可解析结果，可以继续修改标题、歌手或专辑再试。",
                        )
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyStateCard(
                            title = "准备搜索",
                            body = "修改搜索条件后点击搜索，结果会显示在这里。",
                        )
                    }
                }
            }
        }
    }
}

private fun manualLyricsPreview(candidate: LyricsSearchCandidate): String {
    return candidate.document.lines
        .map { it.text.trim() }
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString(" / ")
        .ifBlank { "歌词内容为空" }
}

private fun manualLyricsCandidateMetadata(candidate: LyricsSearchCandidate): String? {
    return buildString {
        candidate.artistName?.takeIf { it.isNotBlank() }?.let { append(it) }
        candidate.albumTitle?.takeIf { it.isNotBlank() }?.let {
            if (isNotEmpty()) append(" · ")
            append(it)
        }
        candidate.durationSeconds?.takeIf { it > 0 }?.let {
            if (isNotEmpty()) append(" · ")
            append(formatLyricsCandidateDuration(it))
        }
    }.takeIf { it.isNotBlank() }
}

private fun manualWorkflowCandidatePreview(candidate: top.iwesley.lyn.music.core.model.WorkflowSongCandidate): String {
    return buildString {
        append(candidate.artists.joinToString(" / ").ifBlank { "未知歌手" })
        candidate.album?.takeIf { it.isNotBlank() }?.let {
            append(" · ")
            append(it)
        }
    }.ifBlank { "歌曲候选" }
}

@Composable
internal fun LyricsShareOverlay(
    platform: PlatformDescriptor,
    state: PlayerState,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lyrics = state.lyrics ?: return
    val shellColors = mainShellColors
    val previewBitmap = rememberPlatformImageBitmap(state.sharePreviewBytes)
    val artworkBitmap = rememberPlatformArtworkBitmap(state.snapshot.currentDisplayArtworkLocator)
    val artworkTintTheme = rememberVinylArtworkPalette(
        artworkBitmap = artworkBitmap,
        enabled = state.selectedLyricsShareTemplate == LyricsShareTemplate.ARTWORK_TINT,
    )?.toArtworkTintTheme()
    val visibleShareLyricsLines = remember(lyrics) {
        buildVisiblePlayerLyricsLines(lyrics)
    }
    val selectedVisibleShareIndices = remember(visibleShareLyricsLines, state.selectedLyricsLineIndices) {
        resolveVisiblePlayerLyricsSelectedIndices(
            visibleLines = visibleShareLyricsLines,
            selectedRawIndices = state.selectedLyricsLineIndices,
        )
    }
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = shellColors.secondaryText
    val bannerMessage = state.sharePreviewError ?: state.shareMessage
    val exportActionsEnabled =
        state.selectedLyricsLineIndices.isNotEmpty() && !state.isShareSaving && !state.isShareCopying
    var isFullscreenPreviewVisible by remember { mutableStateOf(false) }
    val fullscreenPreviewEnabled = shouldEnableLyricsShareFullscreen(
        platform = platform,
        hasPreviewContent = state.shareCardModel != null,
    )
    LaunchedEffect(fullscreenPreviewEnabled) {
        if (!fullscreenPreviewEnabled) {
            isFullscreenPreviewVisible = false
        }
    }
    PlatformBackHandler(
        onBack = {
            if (isFullscreenPreviewVisible) {
                isFullscreenPreviewVisible = false
            } else {
                onPlayerIntent(PlayerIntent.DismissLyricsShare)
            }
        },
    )
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f))
                .clickable { onPlayerIntent(PlayerIntent.DismissLyricsShare) },
        )
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(
                    fraction = if (state.snapshot.currentTrack != null) 0.96f else 1f,
                )
                .fillMaxHeight(0.92f)
                .widthIn(max = 1040.dp)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { },
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            border = BorderStroke(1.dp, shellColors.cardBorder),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
            ) {
                val density = LocalDensity.current
                val layoutProfile = buildLayoutProfile(
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                    platform = platform,
                    density = density,
                )
                val wideLayout = layoutProfile.isExpandedLayout
                val narrowActions = layoutProfile.isCompactLayout
                val mobileActions = layoutProfile.isCompactLayout
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (!mobileActions) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "分享歌词",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = primaryTextColor,
                                )
                                Text(
                                    buildString {
                                        append(state.snapshot.currentDisplayTitle.ifBlank { "当前歌曲" })
                                        state.snapshot.currentDisplayArtistName?.takeIf { it.isNotBlank() }
                                            ?.let {
                                                append(" · ")
                                                append(it)
                                            }
                                    },
                                    color = secondaryTextColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        } else {
                            TextButton(
                                onClick = { onPlayerIntent(PlayerIntent.DismissLyricsShare) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            ) {
                                Text("关闭")
                            }
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LyricsShareTemplateToggle(
                                selectedTemplate = state.selectedLyricsShareTemplate,
                                onTemplateChanged = {
                                    onPlayerIntent(PlayerIntent.LyricsShareTemplateChanged(it))
                                },
                            )
                            if (!mobileActions) {
                                TextButton(
                                    onClick = { onPlayerIntent(PlayerIntent.DismissLyricsShare) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                ) {
                                    Text("关闭")
                                }
                            }
                        }
                    }
                    bannerMessage?.let { message ->
                        BannerCard(
                            message = message,
                            onDismiss = { onPlayerIntent(PlayerIntent.ClearLyricsShareMessage) },
                        )
                    }
                    if (wideLayout) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            LyricsShareSelectionPane(
                                lyricsLines = visibleShareLyricsLines.map { it.line.text },
                                selectedIndices = selectedVisibleShareIndices,
                                onToggle = {
                                    visibleShareLyricsLines.getOrNull(it)?.let { line ->
                                        onPlayerIntent(PlayerIntent.ToggleLyricsLineSelection(line.rawIndex))
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                            LyricsSharePreviewPane(
                                state = state,
                                previewBitmap = previewBitmap,
                                artworkTintTheme = artworkTintTheme,
                                fullscreenEnabled = fullscreenPreviewEnabled,
                                onOpenFullscreen = { isFullscreenPreviewVisible = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            LyricsSharePreviewPane(
                                state = state,
                                previewBitmap = previewBitmap,
                                artworkTintTheme = artworkTintTheme,
                                fullscreenEnabled = fullscreenPreviewEnabled,
                                onOpenFullscreen = { isFullscreenPreviewVisible = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.48f),
                                isCompactLayout = true
                            )
                            LyricsShareSelectionPane(
                                lyricsLines = visibleShareLyricsLines.map { it.line.text },
                                selectedIndices = selectedVisibleShareIndices,
                                onToggle = {
                                    visibleShareLyricsLines.getOrNull(it)?.let { line ->
                                        onPlayerIntent(PlayerIntent.ToggleLyricsLineSelection(line.rawIndex))
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.52f),
                                isCompactLayout = true
                            )
                        }
                    }
                    if (mobileActions) {
                        val mobileActionContentPadding = PaddingValues(
                            horizontal = 0.dp,
                            vertical = 12.dp,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(if (narrowActions) 8.dp else 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (state.supportsLyricsShareFontSelection) {
                                LyricsShareFontMenuButton(
                                    selectedFontKey = state.selectedLyricsShareFontKey,
                                    selectedFontDisplayName = state.selectedLyricsShareFontDisplayName,
                                    availableFonts = state.availableLyricsShareFonts,
                                    isLoading = state.isLyricsShareFontsLoading,
                                    errorMessage = state.lyricsShareFontsError,
                                    onRequestFonts = {
                                        onPlayerIntent(PlayerIntent.RequestLyricsShareFonts)
                                    },
                                    onFontSelected = {
                                        onPlayerIntent(PlayerIntent.LyricsShareFontChanged(it))
                                    },
                                    modifier = Modifier.weight(1f),
                                    iconOnly = true,
                                    contentPadding = mobileActionContentPadding,
                                )
                            }
                            OutlinedButton(
                                onClick = { onPlayerIntent(PlayerIntent.ClearLyricsSelection) },
                                enabled = state.selectedLyricsLineIndices.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = "清空选择" },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                contentPadding = mobileActionContentPadding,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ClearAll,
                                    contentDescription = null,
                                )
                            }
                            OutlinedButton(
                                onClick = { onPlayerIntent(PlayerIntent.CopyLyricsShareImage) },
                                enabled = exportActionsEnabled,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics {
                                        contentDescription =
                                            if (state.isShareCopying) "复制中" else "复制图片"
                                    },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                contentPadding = mobileActionContentPadding,
                            ) {
                                if (state.isShareCopying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = null,
                                    )
                                }
                            }
                            Button(
                                onClick = { onPlayerIntent(PlayerIntent.SaveLyricsShareImage) },
                                enabled = exportActionsEnabled,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics {
                                        contentDescription =
                                            if (state.isShareSaving) "保存中" else "保存到本地"
                                    },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = shellColors.navContainer,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                contentPadding = mobileActionContentPadding,
                            ) {
                                if (state.isShareSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (state.supportsLyricsShareFontSelection) {
                                LyricsShareFontMenuButton(
                                    selectedFontKey = state.selectedLyricsShareFontKey,
                                    selectedFontDisplayName = state.selectedLyricsShareFontDisplayName,
                                    availableFonts = state.availableLyricsShareFonts,
                                    isLoading = state.isLyricsShareFontsLoading,
                                    errorMessage = state.lyricsShareFontsError,
                                    onRequestFonts = {
                                        onPlayerIntent(PlayerIntent.RequestLyricsShareFonts)
                                    },
                                    onFontSelected = {
                                        onPlayerIntent(PlayerIntent.LyricsShareFontChanged(it))
                                    },
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            OutlinedButton(
                                onClick = { onPlayerIntent(PlayerIntent.ClearLyricsSelection) },
                                enabled = state.selectedLyricsLineIndices.isNotEmpty(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                            ) {
                                Text("清空")
                            }
                            Spacer(Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = { onPlayerIntent(PlayerIntent.CopyLyricsShareImage) },
                                enabled = exportActionsEnabled,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                            ) {
                                Text(if (state.isShareCopying) "复制中..." else "复制图片")
                            }
                            Spacer(Modifier.width(10.dp))
                            Button(
                                onClick = { onPlayerIntent(PlayerIntent.SaveLyricsShareImage) },
                                enabled = exportActionsEnabled,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = shellColors.navContainer,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) {
                                Text(if (state.isShareSaving) "保存中..." else "保存到本地")
                            }
                        }
                    }
                }
            }
        }
        if (isFullscreenPreviewVisible && fullscreenPreviewEnabled) {
            LyricsShareFullscreenPreviewOverlay(
                state = state,
                previewBitmap = previewBitmap,
                artworkTintTheme = artworkTintTheme,
                onDismiss = { isFullscreenPreviewVisible = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal fun shouldEnableLyricsShareFullscreen(
    platform: PlatformDescriptor,
    hasPreviewContent: Boolean,
): Boolean {
    return hasPreviewContent && platform.isMobilePlatform()
}

@Composable
private fun LyricsShareFontMenuButton(
    selectedFontKey: String?,
    selectedFontDisplayName: String?,
    availableFonts: List<LyricsShareFontOption>,
    isLoading: Boolean,
    errorMessage: String?,
    onRequestFonts: () -> Unit,
    onFontSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    iconOnly: Boolean = false,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    val shellColors = mainShellColors
    var expanded by remember { mutableStateOf(false) }
    var menuSessionId by remember { mutableStateOf(0) }
    var pendingMenuLoad by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val showLoading = pendingMenuLoad || isLoading
    val showError = !showLoading && availableFonts.isEmpty() && !errorMessage.isNullOrBlank()
    val selectedIndex = remember(availableFonts, selectedFontKey) {
        availableFonts.indexOfFirst { option ->
            selectedFontKey?.let { option.fontKey.equals(it, ignoreCase = true) } == true
        }
    }
    val initialScrollOffset = remember(availableFonts, selectedIndex, density) {
        calculateLyricsShareFontMenuScrollOffsetPx(
            selectedIndex = selectedIndex,
            itemCount = availableFonts.size,
            itemHeightPx = with(density) { LyricsShareFontMenuItemHeight.roundToPx() },
            menuMaxHeightPx = with(density) { LyricsShareFontMenuMaxHeight.roundToPx() },
        )
    }
    val buttonLabel = buildLyricsShareFontButtonLabel(
        selectedFontKey = selectedFontKey,
        selectedFontDisplayName = selectedFontDisplayName,
        availableFonts = availableFonts,
    )
    LaunchedEffect(expanded, isLoading, availableFonts, errorMessage) {
        if (!expanded || isLoading || availableFonts.isNotEmpty() || !errorMessage.isNullOrBlank()) {
            pendingMenuLoad = false
        }
    }

    fun requestFontsIfNeeded() {
        if (availableFonts.isNotEmpty() || isLoading || pendingMenuLoad) return
        if (!errorMessage.isNullOrBlank()) return
        pendingMenuLoad = true
        onRequestFonts()
    }

    fun retryLoadingFonts() {
        if (isLoading || pendingMenuLoad) return
        pendingMenuLoad = true
        onRequestFonts()
    }

    Box(modifier = modifier) {
        OutlinedButton(
            modifier = if (iconOnly) Modifier.fillMaxWidth() else Modifier,
            onClick = {
                if (!expanded) {
                    menuSessionId += 1
                }
                expanded = true
                requestFontsIfNeeded()
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            contentPadding = contentPadding,
        ) {
            if (iconOnly) {
                Icon(
                    imageVector = Icons.Rounded.TextFields,
                    contentDescription = buttonLabel,
                )
            } else {
                Text(buttonLabel, maxLines = 1)
            }
        }
        key(menuSessionId, showLoading, showError, availableFonts, selectedFontKey) {
            val scrollState = rememberScrollState(initial = if (availableFonts.isNotEmpty() && !showLoading) initialScrollOffset else 0)
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .widthIn(min = LyricsShareFontMenuMinWidth, max = LyricsShareFontMenuMaxWidth),
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.background,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, shellColors.cardBorder),
            ) {
                when {
                    showLoading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.5.dp,
                            )
                            Text("正在读取系统字体...")
                        }
                    }

                    showError -> {
                        Text(
                            text = requireNotNull(errorMessage),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            color = shellColors.secondaryText,
                        )
                        DropdownMenuItem(
                            text = { Text("重试") },
                            onClick = { retryLoadingFonts() },
                        )
                    }

                    else -> {
                        LyricsShareFontMenuList(
                            availableFonts = availableFonts,
                            selectedFontKey = selectedFontKey,
                            scrollState = scrollState,
                            onFontSelected = { fontKey ->
                                expanded = false
                                onFontSelected(fontKey)
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun buildLyricsShareFontButtonLabel(
    selectedFontKey: String?,
    selectedFontDisplayName: String? = null,
    availableFonts: List<LyricsShareFontOption>,
): String {
    val displayName = availableFonts.firstOrNull { option ->
        selectedFontKey?.let { option.fontKey.equals(it, ignoreCase = true) } == true
    }?.displayName
    val cachedDisplayName = selectedFontDisplayName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val fallbackName = when {
        displayName != null -> displayName
        cachedDisplayName != null -> cachedDisplayName
        selectedFontKey?.let(::parseLyricsShareImportedFontHash) != null -> DEFAULT_LYRICS_SHARE_FONT_KEY
        selectedFontKey != null -> selectedFontKey
        else -> DEFAULT_LYRICS_SHARE_FONT_KEY
    }
    return "字体 · $fallbackName"
}

@Composable
private fun LyricsShareFontMenuList(
    availableFonts: List<LyricsShareFontOption>,
    selectedFontKey: String?,
    scrollState: androidx.compose.foundation.ScrollState,
    onFontSelected: (String) -> Unit,
) {
    val density = LocalDensity.current
    val itemHeightPx = remember(density) {
        with(density) { LyricsShareFontMenuItemHeight.roundToPx() }
    }
    val indexEntries = remember(availableFonts) {
        buildLyricsShareFontMenuIndexEntries(availableFonts)
    }
    val shouldShowIndexBar by remember(scrollState.maxValue, indexEntries) {
        derivedStateOf { scrollState.maxValue > 0 && indexEntries.isNotEmpty() }
    }
    Box(
        modifier = Modifier
            .widthIn(min = LyricsShareFontMenuMinWidth, max = LyricsShareFontMenuMaxWidth)
            .heightIn(max = LyricsShareFontMenuMaxHeight),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(end = if (shouldShowIndexBar) LyricsShareFontMenuIndexReservedPadding else 0.dp),
        ) {
            availableFonts.forEach { option ->
                val previewFontFamily = lyricsSharePreviewFontFamily(
                    fontKey = option.fontKey,
                    displayName = option.displayName,
                    fontFilePath = option.fontFilePath,
                )
                val isSelected = option.fontKey == selectedFontKey
                DropdownMenuItem(
                    modifier = Modifier.height(LyricsShareFontMenuItemHeight),
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option.displayName,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = option.previewText,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = previewFontFamily,
                            )
                        }
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = { onFontSelected(option.fontKey) },
                )
            }
        }
        if (shouldShowIndexBar) {
            LyricsShareFontMenuLetterIndexBar(
                entries = indexEntries,
                scrollState = scrollState,
                itemHeightPx = itemHeightPx,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 6.dp, top = 8.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun LyricsShareFontMenuLetterIndexBar(
    entries: List<LyricsShareFontMenuIndexEntry>,
    scrollState: androidx.compose.foundation.ScrollState,
    itemHeightPx: Int,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    var activeEntryIndex by remember { mutableStateOf<Int?>(null) }
    var dragActive by remember { mutableStateOf(false) }
    val activeEntry = activeEntryIndex?.let { index -> entries.getOrNull(index) }
    val hintOffsetY = remember(activeEntryIndex, entries, trackSize.height, density) {
        val index = activeEntryIndex ?: return@remember 0.dp
        if (trackSize.height <= 0 || entries.isEmpty()) return@remember 0.dp
        val slotHeightPx = trackSize.height.toFloat() / entries.size.toFloat()
        val bubbleSizePx = with(density) { LyricsShareFontMenuIndexHintSize.roundToPx().toFloat() }
        val targetOffsetPx = slotHeightPx * index + (slotHeightPx - bubbleSizePx) / 2f
        with(density) { targetOffsetPx.roundToInt().toDp() }
    }

    LaunchedEffect(activeEntryIndex, dragActive) {
        if (activeEntryIndex != null && !dragActive) {
            delay(700)
            activeEntryIndex = null
        }
    }

    fun jumpToEntryAt(pointerY: Float) {
        val targetIndex = calculateLyricsShareFontMenuIndexTarget(
            pointerY = pointerY,
            trackHeightPx = trackSize.height,
            entryCount = entries.size,
        )
        if (targetIndex < 0) return
        activeEntryIndex = targetIndex
        val targetScrollValue = calculateLyricsShareFontMenuIndexScrollOffset(
            firstIndex = entries[targetIndex].firstIndex,
            itemHeightPx = itemHeightPx,
            maxScrollValue = scrollState.maxValue,
        )
        coroutineScope.launch {
            scrollState.scrollTo(targetScrollValue)
        }
    }

    Box(
        modifier = modifier
            .width(LyricsShareFontMenuIndexWidth)
            .onSizeChanged { trackSize = it }
            .pointerInput(entries, trackSize.height, scrollState.maxValue) {
                detectTapGestures { offset ->
                    dragActive = false
                    jumpToEntryAt(offset.y)
                }
            }
            .pointerInput(entries, trackSize.height, scrollState.maxValue) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        dragActive = true
                        jumpToEntryAt(offset.y)
                    },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        dragActive = true
                        jumpToEntryAt(change.position.y)
                    },
                    onDragEnd = {
                        dragActive = false
                        activeEntryIndex = null
                    },
                    onDragCancel = {
                        dragActive = false
                        activeEntryIndex = null
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val shellColors = mainShellColors
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            entries.forEachIndexed { index, entry ->
                val isActive = activeEntryIndex == index
                if (isLyricsShareFontFavoritesIndexEntry(entry)) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isActive) MaterialTheme.colorScheme.primary else shellColors.secondaryText,
                    )
                } else {
                    Text(
                        text = entry.label,
                        color = if (isActive) MaterialTheme.colorScheme.primary else shellColors.secondaryText,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        if (activeEntry != null) {
            ElevatedCard(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = -LyricsShareFontMenuIndexHintOffsetX, y = hintOffsetY)
                    .size(LyricsShareFontMenuIndexHintSize),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLyricsShareFontFavoritesIndexEntry(activeEntry)) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text(
                            text = activeEntry.label,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

internal fun calculateLyricsShareFontMenuScrollOffsetPx(
    selectedIndex: Int,
    itemCount: Int,
    itemHeightPx: Int,
    menuMaxHeightPx: Int,
): Int {
    if (selectedIndex < 0 || itemCount <= 0 || itemHeightPx <= 0 || menuMaxHeightPx <= 0) return 0
    val contentHeightPx = itemCount * itemHeightPx
    if (contentHeightPx <= menuMaxHeightPx) return 0
    val targetOffset = selectedIndex * itemHeightPx - (menuMaxHeightPx - itemHeightPx) / 2
    val maxOffset = contentHeightPx - menuMaxHeightPx
    return targetOffset.coerceIn(0, maxOffset)
}

internal data class LyricsShareFontMenuIndexEntry(
    val label: String,
    val firstIndex: Int,
)

internal fun isLyricsShareFontFavoritesIndexEntry(
    entry: LyricsShareFontMenuIndexEntry,
): Boolean = entry.label == LYRICS_SHARE_FONT_FAVORITES_INDEX_LABEL

internal fun buildLyricsShareFontMenuIndexEntries(
    availableFonts: List<LyricsShareFontOption>,
): List<LyricsShareFontMenuIndexEntry> {
    val entries = mutableListOf<LyricsShareFontMenuIndexEntry>()
    availableFonts.indexOfFirst { it.isPrioritized }
        .takeIf { it >= 0 }
        ?.let { firstPrioritizedIndex ->
            entries += LyricsShareFontMenuIndexEntry(
                label = LYRICS_SHARE_FONT_FAVORITES_INDEX_LABEL,
                firstIndex = firstPrioritizedIndex,
            )
        }
    val firstIndexByLabel = linkedMapOf<String, Int>()
    availableFonts.forEachIndexed { index, option ->
        if (option.isPrioritized) return@forEachIndexed
        val label = option.displayName
            .trim()
            .firstOrNull()
            ?.uppercaseChar()
            ?.takeIf { it in 'A'..'Z' }
            ?.toString()
            ?: "#"
        if (!firstIndexByLabel.containsKey(label)) {
            firstIndexByLabel[label] = index
        }
    }
    ('A'..'Z').forEach { letter ->
        firstIndexByLabel[letter.toString()]?.let { firstIndex ->
            entries += LyricsShareFontMenuIndexEntry(
                label = letter.toString(),
                firstIndex = firstIndex,
            )
        }
    }
    firstIndexByLabel["#"]?.let { firstIndex ->
        entries += LyricsShareFontMenuIndexEntry(
            label = "#",
            firstIndex = firstIndex,
        )
    }
    return entries
}

internal fun calculateLyricsShareFontMenuIndexTarget(
    pointerY: Float,
    trackHeightPx: Int,
    entryCount: Int,
): Int {
    if (trackHeightPx <= 0 || entryCount <= 0) return -1
    val fraction = (pointerY / trackHeightPx.toFloat()).coerceIn(0f, 1f)
    return (fraction * entryCount.toFloat()).toInt().coerceAtMost(entryCount - 1)
}

internal fun calculateLyricsShareFontMenuIndexScrollOffset(
    firstIndex: Int,
    itemHeightPx: Int,
    maxScrollValue: Int,
): Int {
    if (firstIndex < 0 || itemHeightPx <= 0 || maxScrollValue <= 0) return 0
    return (firstIndex * itemHeightPx).coerceIn(0, maxScrollValue)
}

private val LyricsShareFontMenuMaxHeight = 430.dp
private val LyricsShareFontMenuMinWidth = 360.dp
private val LyricsShareFontMenuMaxWidth = 560.dp
private val LyricsShareFontMenuItemHeight = 56.dp
private val LyricsShareFontMenuIndexReservedPadding = 28.dp
private val LyricsShareFontMenuIndexWidth = 24.dp
private val LyricsShareFontMenuIndexHintSize = 36.dp
private val LyricsShareFontMenuIndexHintOffsetX = 46.dp
private const val LYRICS_SHARE_FONT_FAVORITES_INDEX_LABEL = "★"

@Composable
private fun LyricsShareSelectionPane(
    lyricsLines: List<String>,
    selectedIndices: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isCompactLayout: Boolean = false,
) {
    val shellColors = mainShellColors
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(
            title = if (isCompactLayout) "" else "选句",
            subtitle = "",
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            border = BorderStroke(1.dp, shellColors.cardBorder),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(lyricsLines) { index, line ->
                    LyricsShareSelectableLine(
                        text = line,
                        selected = index in selectedIndices,
                        onClick = { onToggle(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsShareTemplateToggle(
    selectedTemplate: LyricsShareTemplate,
    onTemplateChanged: (LyricsShareTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LyricsShareTemplate.entries.forEach { template ->
            val selected = template == selectedTemplate
            TextButton(
                onClick = { onTemplateChanged(template) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = when (template) {
                        LyricsShareTemplate.NOTE -> "便签"
                        LyricsShareTemplate.ARTWORK_TINT -> "封面取色"
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LyricsShareSelectableLine(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shellColors = mainShellColors
    val normalized = text.trim()
    val enabled = normalized.isNotEmpty()
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        selected -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        selected -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.secondary else shellColors.cardBorder
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (enabled) normalized else "空白行",
                modifier = Modifier.weight(1f),
                color = contentColor,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                if (selected) "已选" else "点选",
                color = contentColor.copy(alpha = if (enabled) 0.72f else 0.45f),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun LyricsSharePreviewPane(
    state: PlayerState,
    previewBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    artworkTintTheme: ArtworkTintTheme?,
    fullscreenEnabled: Boolean,
    onOpenFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    isCompactLayout: Boolean = false,
) {
    val shellColors = mainShellColors
    val shareCardModel = state.shareCardModel
    val previewInteractionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(
            title = if (isCompactLayout) "" else "预览",
            subtitle = "" //if (fullscreenEnabled) "点击预览图全屏查看" else "",
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            border = BorderStroke(1.dp, shellColors.cardBorder),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                val previewModifier = if (fullscreenEnabled) {
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(
                            interactionSource = previewInteractionSource,
                            indication = null,
                            onClick = onOpenFullscreen,
                        )
                } else {
                    Modifier.fillMaxSize()
                }
                LyricsSharePreviewContent(
                    shareCardModel = shareCardModel,
                    previewBitmap = previewBitmap,
                    artworkTintTheme = artworkTintTheme,
                    modifier = previewModifier,
                )
                if (state.isShareRendering && shareCardModel != null) {
                    LyricsShareRenderingBadge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsSharePreviewContent(
    shareCardModel: LyricsShareCardModel?,
    previewBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    artworkTintTheme: ArtworkTintTheme?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        when {
            shareCardModel == null -> {
                EmptyStateCard(
                    title = "请选择歌词",
                    body = "点选左侧歌词行后，这里会生成一张便签样式的分享图片。",
                )
            }

            previewBitmap != null -> {
                Image(
                    bitmap = previewBitmap,
                    contentDescription = "歌词分享预览",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(22.dp)),
                    contentScale = ContentScale.Fit,
                )
            }

            else -> {
                when (shareCardModel.template) {
                    LyricsShareTemplate.NOTE -> LyricsShareNoteCard(
                        model = shareCardModel,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    LyricsShareTemplate.ARTWORK_TINT -> LyricsShareArtworkTintCard(
                        model = shareCardModel,
                        artworkTintTheme = artworkTintTheme ?: shareCardModel.artworkTintTheme,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsShareFullscreenPreviewOverlay(
    state: PlayerState,
    previewBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    artworkTintTheme: ArtworkTintTheme?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shareCardModel = state.shareCardModel ?: return
    val contentInteractionSource = remember { MutableInteractionSource() }
    val scrollState = rememberScrollState()
    PlatformBackHandler(onBack = onDismiss)
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.92f))
                .clickable { onDismiss() },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 24.dp)
                .clickable(
                    interactionSource = contentInteractionSource,
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            if (previewBitmap != null) {
                LyricsSharePreviewContent(
                    shareCardModel = shareCardModel,
                    previewBitmap = previewBitmap,
                    artworkTintTheme = artworkTintTheme,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 28.dp, bottom = 12.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 28.dp, bottom = 12.dp)
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    LyricsSharePreviewContent(
                        shareCardModel = shareCardModel,
                        previewBitmap = null,
                        artworkTintTheme = artworkTintTheme,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp),
                    )
                }
            }
            if (state.isShareRendering) {
                LyricsShareRenderingBadge(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 8.dp),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭全屏预览",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun LyricsShareRenderingBadge(
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = shellColors.navContainer),
    ) {
        Text(
            text = "更新预览中",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun LyricsShareNoteCard(
    model: LyricsShareCardModel,
    modifier: Modifier = Modifier,
) {
    val artworkBitmap = rememberPlatformArtworkBitmap(model.artworkLocator)
    val previewFontFamily = lyricsSharePreviewFontFamily(
        fontKey = model.fontKey,
        displayName = model.fontKey,
    )
    val primaryTextColor = Color(0xFF3C2E24)
    val footerTextColor = composeColorFromArgb(LyricsShareCardSpec.TEXT_FOOTER_ARGB)
    val secondaryTextColor = Color(0xFF70584B)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7EEDC)),
        border = BorderStroke(1.dp, Color(0xFFF0DEBF)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 26.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 18.dp, y = (-8).dp)
                    .size(width = 92.dp, height = 20.dp)
                    .background(Color(0xBFF6F0D4), RoundedCornerShape(6.dp)),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                LyricsShareArtworkBlock(
                    artworkBitmap = artworkBitmap,
                    modifier = Modifier.size(92.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(LyricsShareCardSpec.LYRICS_PREVIEW_LINE_GAP_DP.dp)) {
                    model.lyricsLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor,
                            fontFamily = previewFontFamily,
                        )
                    }
                }
                Text(
                    text = buildLyricsShareTitleArtistLine(model.title, model.artistName),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = footerTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = previewFontFamily,
                )
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = LyricsShareCardSpec.BRAND_TEXT,
                        style = MaterialTheme.typography.labelLarge,
                        color = secondaryTextColor.copy(alpha = 0.85f),
                        fontFamily = previewFontFamily,
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsShareArtworkTintCard(
    model: LyricsShareCardModel,
    artworkTintTheme: ArtworkTintTheme?,
    modifier: Modifier = Modifier,
) {
    val artworkBitmap = rememberPlatformArtworkBitmap(model.artworkLocator)
    val previewFontFamily = lyricsSharePreviewFontFamily(
        fontKey = model.fontKey,
        displayName = model.fontKey,
    )
    val backgroundColor = composeColorFromArgb(LyricsShareArtworkTintSpec.DEFAULT_BACKGROUND_ARGB)
    val topTint = composeColorFromArgb(
        argbWithAlpha(
            artworkTintTheme?.innerGlowColorArgb
                ?: LyricsShareArtworkTintSpec.DEFAULT_BACKGROUND_ARGB,
            if (artworkTintTheme != null) 0.22f else 0f,
        ),
    )
    val midTint = composeColorFromArgb(
        argbWithAlpha(
            artworkTintTheme?.glowColorArgb ?: LyricsShareArtworkTintSpec.DEFAULT_BACKGROUND_ARGB,
            if (artworkTintTheme != null) 0.18f else 0f,
        ),
    )
    val accentTint = composeColorFromArgb(
        argbWithAlpha(
            artworkTintTheme?.rimColorArgb ?: LyricsShareArtworkTintSpec.DEFAULT_BACKGROUND_ARGB,
            if (artworkTintTheme != null) 0.12f else 0f,
        ),
    )
    val primaryTextColor = composeColorFromArgb(LyricsShareArtworkTintSpec.TEXT_PRIMARY_ARGB)
    val footerTextColor = composeColorFromArgb(LyricsShareArtworkTintSpec.TEXT_FOOTER_ARGB)
    val secondaryTextColor = composeColorFromArgb(LyricsShareArtworkTintSpec.TEXT_SECONDARY_ARGB)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(horizontal = 28.dp, vertical = 30.dp),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                topTint,
                                midTint,
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                accentTint,
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(108.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp)),
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
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(42.dp),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(LyricsShareArtworkTintSpec.LYRICS_PREVIEW_LINE_GAP_DP.dp)) {
                    model.lyricsLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor,
                            fontFamily = previewFontFamily,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = buildLyricsShareTitleArtistLine(model.title, model.artistName),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = footerTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = previewFontFamily,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = LyricsShareCardSpec.BRAND_TEXT,
                        style = MaterialTheme.typography.labelLarge,
                        color = secondaryTextColor,
                        fontFamily = previewFontFamily,
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsShareArtworkBlock(
    artworkBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFE4D2BE)),
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
                imageVector = Icons.Rounded.Album,
                contentDescription = null,
                tint = Color(0xFF8D735F),
                modifier = Modifier.size(34.dp),
            )
        }
    }
}
