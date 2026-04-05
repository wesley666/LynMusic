package top.iwesley.lyn.music

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
import top.iwesley.lyn.music.core.model.WorkflowSongCandidate
import top.iwesley.lyn.music.platform.rememberPlatformArtworkBitmap

internal data class LyricsSearchDialogState(
    val headerTitle: String,
    val headerSubtitle: String,
    val title: String,
    val artistName: String,
    val albumTitle: String,
    val isLoading: Boolean,
    val hasResult: Boolean,
    val directResults: List<LyricsSearchCandidate>,
    val workflowResults: List<WorkflowSongCandidate>,
    val error: String? = null,
)

internal data class LyricsSearchDialogStrings(
    val formSubtitle: String,
    val resultsAppliedSubtitle: String,
    val idleBody: String = "修改搜索条件后点击搜索，结果会显示在这里。",
    val emptyBody: String = "当前已启用歌词源都没有返回可解析结果，可以继续修改标题、歌手或专辑再试。",
    val resultsPlaceholderSubtitle: String = "直接歌词结果和 Workflow 歌曲候选会显示在这里。",
    val cancelLabel: String = "取消",
    val dismissLabel: String = "关闭",
    val searchIdleLabel: String = "搜索",
    val searchLoadingLabel: String = "搜索中...",
)

@Composable
internal fun LyricsSearchOverlayDialog(
    state: LyricsSearchDialogState,
    strings: LyricsSearchDialogStrings,
    onDismiss: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onArtistChanged: (String) -> Unit,
    onAlbumChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onApplyDirectCandidate: (LyricsSearchCandidate) -> Unit,
    onApplyWorkflowCandidate: (WorkflowSongCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryTextColor = Color.White.copy(alpha = 0.96f)
    val secondaryTextColor = Color.White.copy(alpha = 0.72f)
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.68f))
                .clickable { onDismiss() },
        )
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .widthIn(max = 1040.dp)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { },
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF201B19)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
            ) {
                val wideLayout = maxWidth >= 980.dp
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                state.headerTitle,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryTextColor,
                            )
                            Text(
                                state.headerSubtitle,
                                color = secondaryTextColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = onDismiss) {
                            Text(strings.dismissLabel, color = primaryTextColor)
                        }
                    }
                    if (wideLayout) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            LyricsSearchFormPane(
                                title = state.title,
                                artistName = state.artistName,
                                albumTitle = state.albumTitle,
                                isLoading = state.isLoading,
                                error = state.error,
                                strings = strings,
                                onDismiss = onDismiss,
                                onTitleChanged = onTitleChanged,
                                onArtistChanged = onArtistChanged,
                                onAlbumChanged = onAlbumChanged,
                                onSearch = onSearch,
                                modifier = Modifier
                                    .weight(0.42f)
                                    .fillMaxHeight(),
                            )
                            LyricsSearchResultsPane(
                                state = state,
                                strings = strings,
                                onApplyDirectCandidate = onApplyDirectCandidate,
                                onApplyWorkflowCandidate = onApplyWorkflowCandidate,
                                modifier = Modifier
                                    .weight(0.58f)
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
                            LyricsSearchFormPane(
                                title = state.title,
                                artistName = state.artistName,
                                albumTitle = state.albumTitle,
                                isLoading = state.isLoading,
                                error = state.error,
                                strings = strings,
                                onDismiss = onDismiss,
                                onTitleChanged = onTitleChanged,
                                onArtistChanged = onArtistChanged,
                                onAlbumChanged = onAlbumChanged,
                                onSearch = onSearch,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.42f),
                            )
                            LyricsSearchResultsPane(
                                state = state,
                                strings = strings,
                                onApplyDirectCandidate = onApplyDirectCandidate,
                                onApplyWorkflowCandidate = onApplyWorkflowCandidate,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.58f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsSearchFormPane(
    title: String,
    artistName: String,
    albumTitle: String,
    isLoading: Boolean,
    error: String?,
    strings: LyricsSearchDialogStrings,
    onDismiss: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onArtistChanged: (String) -> Unit,
    onAlbumChanged: (String) -> Unit,
    onSearch: () -> Unit,
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
        LyricsSearchSectionTitle(
            title = "搜索条件",
            subtitle = strings.formSubtitle,
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
                val stackedFields = maxWidth < 560.dp
                val buttonSpacing = if (stackedFields) 8.dp else 10.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ImeAwareOutlinedTextField(
                        value = title,
                        onValueChange = onTitleChanged,
                        label = { Text("标题") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        singleLine = true,
                        colors = textFieldColors,
                    )
                    if (stackedFields) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ImeAwareOutlinedTextField(
                                value = artistName,
                                onValueChange = onArtistChanged,
                                label = { Text("歌手") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                singleLine = true,
                                colors = textFieldColors,
                            )
                            ImeAwareOutlinedTextField(
                                value = albumTitle,
                                onValueChange = onAlbumChanged,
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
                                value = artistName,
                                onValueChange = onArtistChanged,
                                label = { Text("歌手") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp),
                                singleLine = true,
                                colors = textFieldColors,
                            )
                            ImeAwareOutlinedTextField(
                                value = albumTitle,
                                onValueChange = onAlbumChanged,
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
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryTextColor),
                        ) {
                            Text(strings.cancelLabel, maxLines = 1)
                        }
                        Button(
                            onClick = onSearch,
                            enabled = !isLoading && title.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.14f),
                                contentColor = primaryTextColor,
                                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                disabledContentColor = secondaryTextColor,
                            ),
                        ) {
                            Text(if (isLoading) strings.searchLoadingLabel else strings.searchIdleLabel, maxLines = 1)
                        }
                    }
                    error?.let {
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                            ),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(
                                text = it,
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
private fun LyricsSearchResultsPane(
    state: LyricsSearchDialogState,
    strings: LyricsSearchDialogStrings,
    onApplyDirectCandidate: (LyricsSearchCandidate) -> Unit,
    onApplyWorkflowCandidate: (WorkflowSongCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryTextColor = Color.White.copy(alpha = 0.96f)
    val secondaryTextColor = Color.White.copy(alpha = 0.72f)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LyricsSearchSectionTitle(
            title = "搜索结果",
            subtitle = when {
                state.isLoading -> "正在请求已启用的歌词源。"
                state.directResults.isNotEmpty() || state.workflowResults.isNotEmpty() -> strings.resultsAppliedSubtitle
                state.hasResult -> "当前没有可解析结果，可以继续调整搜索条件。"
                else -> strings.resultsPlaceholderSubtitle
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
                state.isLoading -> {
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

                state.directResults.isNotEmpty() || state.workflowResults.isNotEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (state.directResults.isNotEmpty()) {
                            Text("直接歌词结果", color = primaryTextColor, fontWeight = FontWeight.SemiBold)
                            state.directResults.forEach { candidate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { onApplyDirectCandidate(candidate) }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LyricsSearchArtworkThumbnail(
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
                                        candidate.title?.takeIf { it.isNotBlank() }?.let { resultTitle ->
                                            Text(
                                                resultTitle,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = primaryTextColor,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                        lyricsSearchCandidateMetadata(candidate)?.let { metadata ->
                                            Text(
                                                metadata,
                                                color = secondaryTextColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Text(
                                            lyricsSearchPreview(candidate),
                                            color = primaryTextColor.copy(alpha = 0.84f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                        if (state.workflowResults.isNotEmpty()) {
                            Text("Workflow 歌曲候选", color = primaryTextColor, fontWeight = FontWeight.SemiBold)
                            state.workflowResults.forEach { candidate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { onApplyWorkflowCandidate(candidate) }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LyricsSearchArtworkThumbnail(
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
                                                    formatLyricsSearchDuration(seconds),
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
                                            workflowSearchPreview(candidate),
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

                state.hasResult -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LyricsSearchEmptyStateCard(
                            title = "没有找到可用歌词",
                            body = strings.emptyBody,
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
                        LyricsSearchEmptyStateCard(
                            title = "准备搜索",
                            body = strings.idleBody,
                        )
                    }
                }
            }
        }
    }
}

private fun lyricsSearchPreview(candidate: LyricsSearchCandidate): String {
    return candidate.document.lines
        .map { it.text.trim() }
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString(" / ")
        .ifBlank { "歌词内容为空" }
}

private fun lyricsSearchCandidateMetadata(candidate: LyricsSearchCandidate): String? {
    return buildString {
        candidate.artistName?.takeIf { it.isNotBlank() }?.let { append(it) }
        candidate.albumTitle?.takeIf { it.isNotBlank() }?.let {
            if (isNotEmpty()) append(" · ")
            append(it)
        }
        candidate.durationSeconds?.takeIf { it > 0 }?.let {
            if (isNotEmpty()) append(" · ")
            append(formatLyricsSearchDuration(it))
        }
    }.takeIf { it.isNotBlank() }
}

private fun workflowSearchPreview(candidate: WorkflowSongCandidate): String {
    return buildString {
        append(candidate.artists.joinToString(" / ").ifBlank { "未知歌手" })
        candidate.album?.takeIf { it.isNotBlank() }?.let {
            append(" · ")
            append(it)
        }
    }.ifBlank { "歌曲候选" }
}

@Composable
private fun LyricsSearchSectionTitle(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LyricsSearchEmptyStateCard(
    title: String,
    body: String,
) {
    Card(shape = RoundedCornerShape(28.dp)) {
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
private fun LyricsSearchArtworkThumbnail(
    artworkLocator: String?,
    modifier: Modifier = Modifier,
) {
    val artworkBitmap = rememberPlatformArtworkBitmap(artworkLocator)
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
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

private fun formatLyricsSearchDuration(durationSeconds: Int): String {
    val safeSeconds = durationSeconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val seconds = safeSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
