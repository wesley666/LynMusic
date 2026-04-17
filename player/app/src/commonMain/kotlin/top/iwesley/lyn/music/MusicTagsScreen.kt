package top.iwesley.lyn.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.tags.MusicTagsDraft
import top.iwesley.lyn.music.feature.tags.MusicTagsEffect
import top.iwesley.lyn.music.feature.tags.MusicTagsIntent
import top.iwesley.lyn.music.feature.tags.MusicTagsRowMetadata
import top.iwesley.lyn.music.feature.tags.MusicTagsState
import top.iwesley.lyn.music.platform.PlatformBackHandler
import top.iwesley.lyn.music.platform.rememberPlatformArtworkBitmap
import top.iwesley.lyn.music.platform.rememberPlatformImageBitmap
import top.iwesley.lyn.music.ui.mainShellColors

private val MusicTagsTableWidth = 940.dp

@Composable
fun MusicTagsTab(
    platform: PlatformDescriptor,
    state: MusicTagsState,
    effects: Flow<MusicTagsEffect>,
    onMusicTagsIntent: (MusicTagsIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onMobileEditorVisibilityChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isDesktop = platform.name == "Desktop"
    val isAndroid = platform.name == "Android"
    DisposableEffect(Unit) {
        onDispose { onMobileEditorVisibilityChanged(false) }
    }
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is MusicTagsEffect.PlayTracks -> {
                    onPlayerIntent(PlayerIntent.PlayTracks(effect.tracks, effect.startIndex))
                }
            }
        }
    }
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        state.message?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(2_500)
                onMusicTagsIntent(MusicTagsIntent.ClearMessage)
            }
        }
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (isDesktop) {
                DesktopMusicTagsLayout(
                    state = state,
                    onMusicTagsIntent = onMusicTagsIntent,
                    onActivateTrack = { onMusicTagsIntent(MusicTagsIntent.ActivateTrack(it)) },
                )
            } else {
                MobileMusicTagsLayout(
                    supportsWrite = isAndroid,
                    state = state,
                    onMusicTagsIntent = onMusicTagsIntent,
                    onMobileEditorVisibilityChanged = onMobileEditorVisibilityChanged,
                )
            }
            if (state.showDiscardChangesDialog) {
                MusicTagsDiscardChangesDialog(
                    onDismissRequest = { onMusicTagsIntent(MusicTagsIntent.DismissDiscardSelection) },
                    onConfirm = { onMusicTagsIntent(MusicTagsIntent.ConfirmDiscardSelection) },
                    confirmLabel = "放弃并切换",
                )
            }
            if (state.onlineLyricsSearch.isVisible) {
                LyricsSearchOverlayDialog(
                    state = LyricsSearchDialogState(
                        headerTitle = "在线搜索歌词",
                        headerSubtitle = buildString {
                            append(state.draft.title.ifBlank { state.selectedTrack?.title ?: "当前编辑器" })
                            state.draft.artistName.takeIf { it.isNotBlank() }?.let {
                                append(" · ")
                                append(it)
                            }
                        },
                        title = state.onlineLyricsSearch.title,
                        artistName = state.onlineLyricsSearch.artistName,
                        albumTitle = state.onlineLyricsSearch.albumTitle,
                        isLoading = state.onlineLyricsSearch.isLoading,
                        hasResult = state.onlineLyricsSearch.hasResult,
                        directResults = state.onlineLyricsSearch.directResults,
                        workflowResults = state.onlineLyricsSearch.workflowResults,
                        error = state.onlineLyricsSearch.error,
                    ),
                    strings = LyricsSearchDialogStrings(
                        formSubtitle = "修改标题、歌手、专辑后重新向已启用歌词源搜索。",
                        resultsAppliedSubtitle = "点选任一结果后选择应用方式。",
                    ),
                    onDismiss = { onMusicTagsIntent(MusicTagsIntent.DismissOnlineLyricsSearch) },
                    onTitleChanged = { onMusicTagsIntent(MusicTagsIntent.OnlineLyricsTitleChanged(it)) },
                    onArtistChanged = { onMusicTagsIntent(MusicTagsIntent.OnlineLyricsArtistChanged(it)) },
                    onAlbumChanged = { onMusicTagsIntent(MusicTagsIntent.OnlineLyricsAlbumChanged(it)) },
                    onSearch = { onMusicTagsIntent(MusicTagsIntent.SearchOnlineLyrics) },
                    onApplyDirectCandidate = { candidate, mode ->
                        onMusicTagsIntent(MusicTagsIntent.ApplyOnlineLyricsCandidate(candidate, mode))
                    },
                    onApplyWorkflowCandidate = { candidate, mode ->
                        onMusicTagsIntent(MusicTagsIntent.ApplyOnlineWorkflowSongCandidate(candidate, mode))
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            state.message?.let { message ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(20.dp),
                ) {
                    MusicTagsToast(message = message)
                }
            }
        }
    }
}

@Composable
private fun DesktopMusicTagsLayout(
    state: MusicTagsState,
    onMusicTagsIntent: (MusicTagsIntent) -> Unit,
    onActivateTrack: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        MusicTagsTrackPane(
            state = state,
            onMusicTagsIntent = onMusicTagsIntent,
            onActivateTrack = onActivateTrack,
            modifier = Modifier
                .weight(1.18f)
                .fillMaxHeight(),
        )
        MusicTagsEditorPane(
            state = state,
            readOnly = false,
            onMusicTagsIntent = onMusicTagsIntent,
            modifier = Modifier
                .weight(0.92f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun MobileMusicTagsLayout(
    supportsWrite: Boolean,
    state: MusicTagsState,
    onMusicTagsIntent: (MusicTagsIntent) -> Unit,
    onMobileEditorVisibilityChanged: (Boolean) -> Unit,
) {
    var detailTrackId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDiscardBackDialog by rememberSaveable { mutableStateOf(false) }
    val detailTrack = state.tracks.firstOrNull { it.id == detailTrackId }
    val layoutSpacing = if (detailTrack == null) 14.dp else 10.dp
    fun requestBackToList() {
        if (state.isDirty) {
            showDiscardBackDialog = true
        } else {
            detailTrackId = null
        }
    }
    SideEffect {
        onMobileEditorVisibilityChanged(detailTrack != null)
    }
    LaunchedEffect(detailTrackId, state.isDirty) {
        if (detailTrackId == null || !state.isDirty) {
            showDiscardBackDialog = false
        }
    }
    PlatformBackHandler(
        enabled = canNavigateBackFromMusicTagsDetail(detailTrackId),
        onBack = ::requestBackToList,
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = if (detailTrack == null) 16.dp else 0.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(layoutSpacing),
    ) {
        if (detailTrack == null) {
            MusicTagsHeader(
                title = "音乐标签",
                infoText = if (supportsWrite) {
                    "已授予文件权限的本地目录支持写回；未授权目录会以只读方式打开。"
                } else {
                    "当前设备仅支持查看本地标签。"
                },
            )
            MusicTagsTrackPane(
                state = state,
                showHeader = false,
                onMusicTagsIntent = { intent ->
                    when (intent) {
                        is MusicTagsIntent.SelectTrack -> {
                            detailTrackId = intent.trackId
                            onMusicTagsIntent(intent)
                        }

                        else -> onMusicTagsIntent(intent)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = detailTrack.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = ::requestBackToList) {
                    Text("返回列表")
                }
            }
            MusicTagsEditorPane(
                state = state,
                showHeader = false,
                compactLayout = true,
                readOnly = !state.canWriteSelected,
                readOnlyHint = when {
                    state.isLoadingSelected -> null
                    supportsWrite && !state.canWriteSelected ->
                        "当前歌曲通过 SAF 只读导入，暂不能保存修改。请在来源页重新扫描并授予“管理所有文件”权限。"

                    !supportsWrite -> "当前设备暂不支持写回音频标签。"
                    else -> null
                },
                onMusicTagsIntent = onMusicTagsIntent,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (showDiscardBackDialog) {
        MusicTagsDiscardChangesDialog(
            onDismissRequest = { showDiscardBackDialog = false },
            onConfirm = {
                showDiscardBackDialog = false
                onMusicTagsIntent(MusicTagsIntent.ResetDraft)
                detailTrackId = null
            },
            confirmLabel = "放弃并返回",
        )
    }
}

@Composable
private fun MusicTagsDiscardChangesDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String,
) {
    val shellColors = mainShellColors
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("放弃未保存修改？") },
        text = { Text("当前歌曲的标签还没有保存，切换后这些改动会丢失。") },
        containerColor = shellColors.cardContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("继续编辑")
            }
        },
    )
}

@Composable
private fun MusicTagsTrackPane(
    state: MusicTagsState,
    showHeader: Boolean = true,
    headerSubtitle: String? = "仅显示本地文件夹来源。左侧单选歌曲，右侧编辑标签。",
    onMusicTagsIntent: (MusicTagsIntent) -> Unit,
    onActivateTrack: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (showHeader) {
                MusicTagsHeader(
                    title = "本地歌曲",
                    subtitle = headerSubtitle,
                )
            }
            if (state.isLoadingContent) {
                MusicTagsEmptyState(
                    title = "正在加载本地歌曲",
                    body = "本地歌曲列表会在页面显示后继续异步整理，请稍候。",
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (state.tracks.isEmpty()) {
                MusicTagsEmptyState(
                    title = "还没有本地歌曲",
                    body = "先在来源页导入本地文件夹，这里才会出现可编辑的歌曲列表。",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val horizontalScroll = rememberScrollState()
                    val showHorizontalBar = maxWidth < MusicTagsTableWidth
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                width = 1.dp,
                                color = shellColors.cardBorder,
                                shape = RoundedCornerShape(20.dp),
                            )
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.18f)),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .horizontalScroll(horizontalScroll),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .width(MusicTagsTableWidth)
                                        .fillMaxHeight(),
                                ) {
                                    MusicTagsTableHeader()
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(vertical = 4.dp),
                                    ) {
                                        items(state.tracks, key = { it.id }) { track ->
                                            LaunchedEffect(track.id) {
                                                onMusicTagsIntent(MusicTagsIntent.EnsureRowMetadata(track.id))
                                            }
                                            MusicTagsTrackRow(
                                                track = track,
                                                rowMetadata = state.rowMetadata[track.id],
                                                selected = state.selectedTrackId == track.id,
                                                onClick = { onMusicTagsIntent(MusicTagsIntent.SelectTrack(track.id)) },
                                                onDoubleClick = onActivateTrack?.let { handler ->
                                                    { handler(track.id) }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            if (showHorizontalBar) {
                                MusicTagsHorizontalScrollBar(
                                    scrollState = horizontalScroll,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicTagsEditorPane(
    state: MusicTagsState,
    showHeader: Boolean = true,
    compactLayout: Boolean = false,
    readOnly: Boolean,
    readOnlyHint: String? = null,
    onMusicTagsIntent: (MusicTagsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedTrack = state.selectedTrack
    val contentPadding = if (compactLayout) {
        PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 8.dp)
    } else {
        PaddingValues(18.dp)
    }
    val sectionSpacing = if (compactLayout) 10.dp else 14.dp
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
    ) {
        if (selectedTrack == null) {
            MusicTagsEmptyState(
                title = "选择一首歌曲",
                body = "从左侧列表选择本地歌曲后，这里会显示完整标签和封面预览。",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val scrollState = rememberScrollState()
            val fieldResetKey = selectedTrack.id
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing),
            ) {
                if (showHeader) {
                    MusicTagsHeader(
                        title = if (readOnly) "标签详情" else "标签编辑器",
                        subtitle = selectedTrack.relativePath.ifBlank { selectedTrack.mediaLocator },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (!readOnly && state.canWriteSelected) {
                        TextButton(
                            onClick = { onMusicTagsIntent(MusicTagsIntent.OpenOnlineLyricsSearch) },
                            enabled = !state.isLoadingSelected && !state.isSaving && !state.isRefreshing,
                        ) {
                            Text("在线搜索")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(
                        onClick = { onMusicTagsIntent(MusicTagsIntent.RefreshSelected) },
                        enabled = !state.isLoadingSelected && !state.isSaving && !state.isRefreshing,
                    ) {
                        Icon(Icons.Rounded.Sync, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.isRefreshing) "刷新中…" else "刷新")
                    }
                }
                if (state.isLoadingSelected) {
                    MusicTagsNoteCard("正在读取音频标签…")
                }
                if (!state.isLoadingSelected && readOnlyHint != null) {
                    MusicTagsNoteCard(readOnlyHint)
                }
                val previewBitmap = rememberPlatformImageBitmap(state.draft.pendingArtworkBytes)
                val artworkBitmap = rememberPlatformArtworkBitmap(
                    if (state.draft.clearArtwork) null else state.draft.artworkLocator,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MusicTagsArtworkPreview(
                        artworkContent = {
                            when {
                                previewBitmap != null -> androidx.compose.foundation.Image(
                                    bitmap = previewBitmap,
                                    contentDescription = "新封面预览",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )

                                artworkBitmap != null -> androidx.compose.foundation.Image(
                                    bitmap = artworkBitmap,
                                    contentDescription = "歌曲封面",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )

                                else -> {
                                    Icon(
                                        Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                    )
                                }
                            }
                        },
                    )
                    MusicTagsField(
                        label = "标题",
                        value = state.draft.title,
                        readOnly = readOnly,
                        onValueChange = { onMusicTagsIntent(MusicTagsIntent.TitleChanged(it)) },
                        resetKey = fieldResetKey,
                    )
                    MusicTagsField(
                        label = "艺术家",
                        value = state.draft.artistName,
                        readOnly = readOnly,
                        onValueChange = { onMusicTagsIntent(MusicTagsIntent.ArtistChanged(it)) },
                        resetKey = fieldResetKey,
                    )
                    MusicTagsField(
                        label = "专辑",
                        value = state.draft.albumTitle,
                        readOnly = readOnly,
                        onValueChange = { onMusicTagsIntent(MusicTagsIntent.AlbumChanged(it)) },
                        resetKey = fieldResetKey,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MusicTagsField(
                            label = "年份",
                            value = state.draft.year,
                            readOnly = readOnly,
                            onValueChange = { onMusicTagsIntent(MusicTagsIntent.YearChanged(it)) },
                            modifier = Modifier.weight(1f),
                            resetKey = fieldResetKey,
                        )
                        MusicTagsField(
                            label = "音轨",
                            value = state.draft.trackNumber,
                            readOnly = readOnly,
                            onValueChange = { onMusicTagsIntent(MusicTagsIntent.TrackNumberChanged(it)) },
                            modifier = Modifier.weight(1f),
                            resetKey = fieldResetKey,
                        )
                        MusicTagsField(
                            label = "光盘编号",
                            value = state.draft.discNumber,
                            readOnly = readOnly,
                            onValueChange = { onMusicTagsIntent(MusicTagsIntent.DiscNumberChanged(it)) },
                            modifier = Modifier.weight(1f),
                            resetKey = fieldResetKey,
                        )
                    }
                    MusicTagsField(
                        label = "流派",
                        value = state.draft.genre,
                        readOnly = readOnly,
                        onValueChange = { onMusicTagsIntent(MusicTagsIntent.GenreChanged(it)) },
                        resetKey = fieldResetKey,
                    )
                    MusicTagsField(
                        label = "注释",
                        value = state.draft.comment,
                        readOnly = readOnly,
                        onValueChange = { onMusicTagsIntent(MusicTagsIntent.CommentChanged(it)) },
                        minLines = 3,
                        resetKey = fieldResetKey,
                    )
                    MusicTagsField(
                        label = "专辑艺术家",
                        value = state.draft.albumArtist,
                        readOnly = readOnly,
                        onValueChange = { onMusicTagsIntent(MusicTagsIntent.AlbumArtistChanged(it)) },
                        resetKey = fieldResetKey,
                    )
                    MusicTagsField(
                        label = "作曲家",
                        value = state.draft.composer,
                        readOnly = readOnly,
                        onValueChange = { onMusicTagsIntent(MusicTagsIntent.ComposerChanged(it)) },
                        resetKey = fieldResetKey,
                    )
                    MusicTagsLyricsField(
                        label = "嵌入歌词",
                        value = state.draft.embeddedLyrics,
                        readOnly = readOnly,
                        onValueChange = { onMusicTagsIntent(MusicTagsIntent.EmbeddedLyricsChanged(it)) },
                        minLines = 8,
                        resetKey = fieldResetKey,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = state.draft.isCompilation,
                            onCheckedChange = if (readOnly) null else { checked ->
                                onMusicTagsIntent(MusicTagsIntent.CompilationChanged(checked))
                            },
                            enabled = !readOnly,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("合辑")
                    }
                    MusicTagsNoteCard(
                        "标签格式：${state.rowMetadata[selectedTrack.id]?.tagLabel ?: state.selectedSnapshot?.tagLabel ?: "读取后显示"}",
                    )
                }
                if (!readOnly) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(
                                onClick = { onMusicTagsIntent(MusicTagsIntent.ResetDraft) },
                                enabled = state.isDirty && !state.isSaving,
                            ) {
                                Text("重置")
                            }
                            TextButton(
                                onClick = { onMusicTagsIntent(MusicTagsIntent.PickArtwork) },
                                enabled = state.canWriteSelected && !state.isSaving,
                            ) {
                                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                                if (!compactLayout) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("更换封面")
                                }
                            }
                            TextButton(
                                onClick = { onMusicTagsIntent(MusicTagsIntent.ClearArtwork) },
                                enabled = state.canWriteSelected &&
                                    !state.isSaving &&
                                    (state.draft.artworkLocator != null || state.draft.pendingArtworkBytes != null),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = null)
                                if (!compactLayout) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("清除封面")
                                }
                            }
                            Button(
                                onClick = { onMusicTagsIntent(MusicTagsIntent.Save) },
                                enabled = state.canWriteSelected && state.isDirty && !state.isSaving,
                            ) {
                                Text(if (state.isSaving) "保存中…" else "保存")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicTagsTrackRow(
    track: Track,
    rowMetadata: MusicTagsRowMetadata?,
    selected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else Color.Transparent,
            )
            .let { baseModifier ->
                if (onDoubleClick != null) {
                    baseModifier.combinedClickable(
                        onClick = onClick,
                        onDoubleClick = onDoubleClick,
                    )
                } else {
                    baseModifier.clickable(onClick = onClick)
                }
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MusicTagsTrackFileCell(
            track = track,
            width = 180.dp,
        )
        MusicTagsTableCell(track.title, 170.dp)
        MusicTagsTableCell(track.artistName.orEmpty(), 150.dp)
        MusicTagsTableCell(rowMetadata?.albumArtist.orEmpty(), 160.dp)
        MusicTagsTableCell(track.albumTitle.orEmpty(), 180.dp)
        MusicTagsTableCell(track.discNumber?.toString().orEmpty(), 84.dp)
    }
}

@Composable
private fun MusicTagsTrackFileCell(
    track: Track,
    width: androidx.compose.ui.unit.Dp,
) {
    val artworkBitmap = rememberPlatformArtworkBitmap(track.artworkLocator)
    Box(
        modifier = Modifier.width(width),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center,
            ) {
                if (artworkBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = artworkBitmap,
                        contentDescription = "歌曲封面",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                    )
                }
            }
            Text(
                text = track.relativePath.substringAfterLast('/'),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MusicTagsHorizontalScrollBar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier = modifier.height(18.dp),
    ) {
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val maxScrollPx = scrollState.maxValue.toFloat()
        val minThumbWidthPx = with(density) { 44.dp.toPx() }
        val thumbWidthPx = if (maxScrollPx <= 0f || trackWidthPx <= 0f) {
            trackWidthPx
        } else {
            val contentWidthPx = trackWidthPx + maxScrollPx
            (trackWidthPx * trackWidthPx / contentWidthPx).coerceIn(minThumbWidthPx, trackWidthPx)
        }
        val maxThumbOffsetPx = (trackWidthPx - thumbWidthPx).coerceAtLeast(0f)
        val thumbOffsetPx = if (maxScrollPx <= 0f || maxThumbOffsetPx <= 0f) {
            0f
        } else {
            (scrollState.value.toFloat() / maxScrollPx) * maxThumbOffsetPx
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbOffsetPx.roundToInt(), 0) }
                    .width(with(density) { thumbWidthPx.toDp() })
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.78f))
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            if (scrollState.maxValue <= 0 || maxThumbOffsetPx <= 0f) return@rememberDraggableState
                            val deltaRatio = delta / maxThumbOffsetPx
                            val target = (scrollState.value + deltaRatio * scrollState.maxValue)
                                .roundToInt()
                                .coerceIn(0, scrollState.maxValue)
                            scope.launch {
                                scrollState.scrollTo(target)
                            }
                        },
                    ),
            )
        }
    }
}

@Composable
private fun MusicTagsTableHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MusicTagsTableCell("文件名", 180.dp, fontWeight = FontWeight.Bold)
            MusicTagsTableCell("标题", 170.dp, fontWeight = FontWeight.Bold)
            MusicTagsTableCell("艺术家", 150.dp, fontWeight = FontWeight.Bold)
            MusicTagsTableCell("专辑艺术家", 160.dp, fontWeight = FontWeight.Bold)
            MusicTagsTableCell("专辑", 180.dp, fontWeight = FontWeight.Bold)
            MusicTagsTableCell("光盘编号", 84.dp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MusicTagsTableCell(
    value: String,
    width: androidx.compose.ui.unit.Dp,
    fontWeight: FontWeight? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = Modifier.width(width),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = value.ifBlank { " " },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = color,
            fontWeight = fontWeight,
        )
    }
}

@Composable
private fun MusicTagsArtworkPreview(
    artworkContent: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val shellColors = mainShellColors
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.28f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(12.dp)
                .border(
                    width = 1.dp,
                    color = shellColors.cardBorder,
                    shape = RoundedCornerShape(20.dp),
                )
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            artworkContent()
        }
    }
}

@Composable
private fun MusicTagsField(
    label: String,
    value: String,
    readOnly: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    minLines: Int = 1,
    resetKey: Any? = null,
) {
    val shellColors = mainShellColors
    ImeAwareOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        label = { Text(label) },
        modifier = modifier,
        minLines = minLines,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = shellColors.cardBorder,
            unfocusedBorderColor = shellColors.cardBorder,
            disabledBorderColor = shellColors.cardBorder,
        ),
        resetKey = resetKey,
    )
}

@Composable
private fun MusicTagsLyricsField(
    label: String,
    value: String,
    readOnly: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    minLines: Int = 8,
    resetKey: Any? = null,
) {
    val shellColors = mainShellColors
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 32)
    val horizontalScroll = rememberScrollState()
    var textFieldValueState by remember(resetKey) {
        mutableStateOf(musicTagsLyricsTextFieldValueFor(value))
    }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewportWidthPx by remember { mutableStateOf(0) }

    LaunchedEffect(value, resetKey) {
        if (value != textFieldValueState.text) {
            textFieldValueState = musicTagsLyricsTextFieldValueFor(value)
            horizontalScroll.scrollTo(0)
        }
    }
    LaunchedEffect(resetKey) {
        horizontalScroll.scrollTo(0)
    }

    val textColor = if (readOnly) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor)
    val contentHorizontalPadding = 16.dp
    val contentVerticalPadding = 10.dp
    val extraLineWidth = 12.dp
    val measuredTextWidthPx = remember(textFieldValueState.text, textStyle, textMeasurer) {
        textMeasurer.measure(
            text = textFieldValueState.text.ifEmpty { " " },
            style = textStyle,
            softWrap = false,
        ).size.width
    }
    val measuredTextWidth = with(density) { measuredTextWidthPx.toDp() }
    val cursorPaddingPx = with(density) { contentHorizontalPadding.toPx() }
    val cursorMarginPx = with(density) { 24.dp.toPx() }

    LaunchedEffect(
        textFieldValueState.selection,
        textLayoutResult,
        viewportWidthPx,
        horizontalScroll.maxValue,
    ) {
        val layoutResult = textLayoutResult ?: return@LaunchedEffect
        if (viewportWidthPx <= 0 || horizontalScroll.maxValue <= 0) return@LaunchedEffect
        val cursorOffset = textFieldValueState.selection.end.coerceIn(0, textFieldValueState.text.length)
        val cursorRect = layoutResult.getCursorRect(cursorOffset)
        val cursorLeft = cursorRect.left + cursorPaddingPx
        val cursorRight = cursorRect.right + cursorPaddingPx
        val viewportStart = horizontalScroll.value.toFloat()
        val viewportEnd = viewportStart + viewportWidthPx
        val target = when {
            cursorRight + cursorMarginPx > viewportEnd -> {
                (cursorRight + cursorMarginPx - viewportWidthPx).roundToInt()
            }

            cursorLeft - cursorMarginPx < viewportStart -> {
                (cursorLeft - cursorMarginPx).roundToInt()
            }

            else -> null
        } ?: return@LaunchedEffect
        horizontalScroll.scrollTo(target.coerceIn(0, horizontalScroll.maxValue))
    }

    BoxWithConstraints(modifier = modifier) {
        val scrollContentWidth = maxWidth.coerceAtLeast(
            measuredTextWidth + contentHorizontalPadding * 2 + extraLineWidth,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = shellColors.cardBorder,
                    shape = RoundedCornerShape(18.dp),
                )
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                .padding(top = 8.dp, bottom = 8.dp),
        ) {
            Column {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = contentHorizontalPadding),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { viewportWidthPx = it.width }
                        .horizontalScroll(horizontalScroll),
                ) {
                    BasicTextField(
                        value = textFieldValueState,
                        onValueChange = { updatedValue ->
                            textFieldValueState = updatedValue
                            if (updatedValue.composition == null && updatedValue.text != value) {
                                onValueChange(updatedValue.text)
                            }
                        },
                        modifier = Modifier
                            .width(scrollContentWidth)
                            .padding(
                                horizontal = contentHorizontalPadding,
                                vertical = contentVerticalPadding,
                            ),
                        readOnly = readOnly,
                        textStyle = textStyle,
                        minLines = minLines,
                        maxLines = Int.MAX_VALUE,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        onTextLayout = { textLayoutResult = it },
                    )
                }
                if (horizontalScroll.maxValue > 0) {
                    MusicTagsHorizontalScrollBar(
                        scrollState = horizontalScroll,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 2.dp, end = 12.dp, bottom = 0.dp),
                    )
                }
            }
        }
    }
}

private fun musicTagsLyricsTextFieldValueFor(value: String): TextFieldValue {
    return TextFieldValue(
        text = value,
        selection = TextRange.Zero,
    )
}

@Composable
private fun MusicTagsHeader(
    title: String,
    subtitle: String? = null,
    infoText: String? = null,
) {
    val shellColors = mainShellColors
    var showInfoDialog by rememberSaveable(title, infoText) { mutableStateOf(false) }
    val resolvedInfoText = infoText?.takeIf { it.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
            if (resolvedInfoText != null) {
                IconButton(onClick = { showInfoDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "查看说明",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (showInfoDialog && resolvedInfoText != null) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = shellColors.cardContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("${title}说明") },
            text = { Text(resolvedInfoText) },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("知道了")
                }
            },
        )
    }
}

@Composable
private fun MusicTagsNoteCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MusicTagsEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
            )
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MusicTagsToast(message: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.76f)),
    ) {
        Text(
            text = message,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
