package top.iwesley.lyn.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import top.iwesley.lyn.music.core.model.AppTab
import top.iwesley.lyn.music.core.model.ArtworkLoader
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaybackMode
import top.iwesley.lyn.music.core.model.PlaybackPreferencesStore
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.repository.DefaultLyricsRepository
import top.iwesley.lyn.music.data.repository.DefaultPlaybackRepository
import top.iwesley.lyn.music.data.repository.DefaultSettingsRepository
import top.iwesley.lyn.music.data.repository.RoomImportSourceRepository
import top.iwesley.lyn.music.data.repository.RoomLibraryRepository
import top.iwesley.lyn.music.feature.importing.ImportIntent
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.importing.ImportStore
import top.iwesley.lyn.music.feature.library.LibraryIntent
import top.iwesley.lyn.music.feature.library.LibraryState
import top.iwesley.lyn.music.feature.library.LibraryStore
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.feature.player.PlayerStore
import top.iwesley.lyn.music.feature.settings.SettingsIntent
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.feature.settings.SettingsStore
import top.iwesley.lyn.music.ui.LynMusicTheme
import top.iwesley.lyn.music.ui.heroGlow

class LynMusicAppComponent(
    val platform: PlatformDescriptor,
    val libraryStore: LibraryStore,
    val importStore: ImportStore,
    val playerStore: PlayerStore,
    val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
    private val onDispose: suspend () -> Unit,
) {
    private var disposed = false

    fun dispose() {
        if (disposed) return
        disposed = true
        runBlocking {
            onDispose()
        }
        scope.cancel()
    }
}

fun buildLynMusicAppComponent(
    platform: PlatformDescriptor,
    database: LynMusicDatabase,
    importSourceGateway: ImportSourceGateway,
    playbackGateway: PlaybackGateway,
    playbackPreferencesStore: PlaybackPreferencesStore,
    secureCredentialStore: SecureCredentialStore,
    lyricsHttpClient: top.iwesley.lyn.music.core.model.LyricsHttpClient,
    logger: DiagnosticLogger = NoopDiagnosticLogger,
    @Suppress("UNUSED_PARAMETER")
    artworkLoader: ArtworkLoader = object : ArtworkLoader {
        override suspend fun resolve(track: Track): String? = track.artworkLocator
    },
): LynMusicAppComponent {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val libraryRepository = RoomLibraryRepository(database)
    val importSourceRepository = RoomImportSourceRepository(database, importSourceGateway, secureCredentialStore)
    val playbackRepository = DefaultPlaybackRepository(database, playbackGateway, scope)
    val settingsRepository = DefaultSettingsRepository(database, playbackPreferencesStore)
    val lyricsRepository = DefaultLyricsRepository(database, lyricsHttpClient, logger)
    scope.launch {
        settingsRepository.ensureDefaults()
    }

    return LynMusicAppComponent(
        platform = platform,
        libraryStore = LibraryStore(libraryRepository, scope),
        importStore = ImportStore(importSourceRepository, platform.capabilities, scope),
        playerStore = PlayerStore(playbackRepository, lyricsRepository, scope),
        settingsStore = SettingsStore(settingsRepository, scope),
        scope = scope,
        onDispose = {
            playbackRepository.close()
        },
    )
}

@Composable
fun App(component: LynMusicAppComponent) {
    DisposableEffect(component) {
        onDispose { component.dispose() }
    }

    val libraryState by component.libraryStore.state.collectAsState()
    val importState by component.importStore.state.collectAsState()
    val playerState by component.playerStore.state.collectAsState()
    val settingsState by component.settingsStore.state.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Library) }

    LynMusicTheme {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val compact = maxWidth < 900.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            ) {
                if (compact) {
                    MobileShell(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        platform = component.platform,
                        libraryState = libraryState,
                        importState = importState,
                        playerState = playerState,
                        settingsState = settingsState,
                        onLibraryIntent = component.libraryStore::dispatch,
                        onImportIntent = component.importStore::dispatch,
                        onPlayerIntent = component.playerStore::dispatch,
                        onSettingsIntent = component.settingsStore::dispatch,
                    )
                } else {
                    DesktopShell(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        platform = component.platform,
                        libraryState = libraryState,
                        importState = importState,
                        playerState = playerState,
                        settingsState = settingsState,
                        onLibraryIntent = component.libraryStore::dispatch,
                        onImportIntent = component.importStore::dispatch,
                        onPlayerIntent = component.playerStore::dispatch,
                        onSettingsIntent = component.settingsStore::dispatch,
                    )
                }

                AnimatedVisibility(
                    visible = playerState.snapshot.currentTrack != null && !playerState.isExpanded,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding(),
                ) {
                    MiniPlayerBar(
                        state = playerState,
                        onPlayerIntent = component.playerStore::dispatch,
                    )
                }

                AnimatedVisibility(
                    visible = playerState.isExpanded,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    PlayerOverlay(
                        state = playerState,
                        onPlayerIntent = component.playerStore::dispatch,
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileShell(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    platform: PlatformDescriptor,
    libraryState: LibraryState,
    importState: ImportState,
    playerState: PlayerState,
    settingsState: SettingsState,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onImportIntent: (ImportIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onSettingsIntent: (SettingsIntent) -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple(AppTab.Library, Icons.Rounded.LibraryMusic, "曲库"),
                    Triple(AppTab.Sources, Icons.Rounded.FolderOpen, "来源"),
                    Triple(AppTab.Settings, Icons.Rounded.Settings, "设置"),
                ).forEach { (tab, icon, label) ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = if (playerState.snapshot.currentTrack != null) 86.dp else 0.dp),
        ) {
            HeroHeader(platform = platform, snapshot = playerState.snapshot)
            TabContent(
                selectedTab = selectedTab,
                libraryState = libraryState,
                importState = importState,
                settingsState = settingsState,
                onLibraryIntent = onLibraryIntent,
                onImportIntent = onImportIntent,
                onPlayerIntent = onPlayerIntent,
                onSettingsIntent = onSettingsIntent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DesktopShell(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    platform: PlatformDescriptor,
    libraryState: LibraryState,
    importState: ImportState,
    playerState: PlayerState,
    settingsState: SettingsState,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onImportIntent: (ImportIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onSettingsIntent: (SettingsIntent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            HeroHeader(platform = platform, snapshot = playerState.snapshot, compact = true)
            DesktopNav(selectedTab = selectedTab, onTabSelected = onTabSelected)
            if (playerState.snapshot.currentTrack != null) {
                MiniPlayerBar(state = playerState, onPlayerIntent = onPlayerIntent, compact = true)
            }
        }

        TabContent(
            selectedTab = selectedTab,
            libraryState = libraryState,
            importState = importState,
            settingsState = settingsState,
            onLibraryIntent = onLibraryIntent,
            onImportIntent = onImportIntent,
            onPlayerIntent = onPlayerIntent,
            onSettingsIntent = onSettingsIntent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DesktopNav(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                Triple(AppTab.Library, Icons.Rounded.LibraryMusic, "曲库"),
                Triple(AppTab.Sources, Icons.Rounded.FolderOpen, "来源"),
                Triple(AppTab.Settings, Icons.Rounded.Settings, "设置"),
            ).forEach { (tab, icon, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (selectedTab == tab) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else Color.Transparent,
                        )
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(label, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun HeroHeader(
    platform: PlatformDescriptor,
    snapshot: PlaybackSnapshot,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface.copy(alpha = 0.7f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            colors.heroGlow,
                            colors.secondary.copy(alpha = 0.15f),
                            colors.surface.copy(alpha = 0.15f),
                        ),
                    ),
                )
                .padding(if (compact) 18.dp else 22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "LynMusic",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "本地音乐与自定义歌词 API 的多端播放器",
                    color = colors.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(platform.name) },
                        leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) },
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(snapshot.queue.size.toString() + " 首队列") },
                        leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TabContent(
    selectedTab: AppTab,
    libraryState: LibraryState,
    importState: ImportState,
    settingsState: SettingsState,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onImportIntent: (ImportIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onSettingsIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (selectedTab) {
        AppTab.Library -> LibraryTab(
            state = libraryState,
            onLibraryIntent = onLibraryIntent,
            onPlayerIntent = onPlayerIntent,
            modifier = modifier,
        )

        AppTab.Sources -> SourcesTab(
            state = importState,
            onImportIntent = onImportIntent,
            modifier = modifier,
        )

        AppTab.Settings -> SettingsTab(
            state = settingsState,
            onSettingsIntent = onSettingsIntent,
            modifier = modifier,
        )
    }
}

@Composable
private fun LibraryTab(
    state: LibraryState,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onLibraryIntent(LibraryIntent.SearchChanged(it)) },
                label = { Text("搜索歌曲 / 艺人 / 专辑") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(title = "歌曲", value = state.tracks.size.toString(), icon = Icons.Rounded.LibraryMusic, modifier = Modifier.weight(1f))
                StatCard(title = "专辑", value = state.albums.size.toString(), icon = Icons.Rounded.Album, modifier = Modifier.weight(1f))
                StatCard(title = "艺人", value = state.artists.size.toString(), icon = Icons.Rounded.Tune, modifier = Modifier.weight(1f))
            }
        }
        item {
            SectionTitle(title = "你的曲库", subtitle = "支持本地文件夹、Samba、WebDAV 与自定义歌词联动。")
        }
        if (state.filteredTracks.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "曲库还是空的",
                    body = "先到“来源”页导入本地文件夹、Samba 或 WebDAV，扫描完成后会出现在这里。",
                )
            }
        } else {
            itemsIndexed(state.filteredTracks, key = { _, item -> item.id }) { index, track ->
                TrackRow(
                    track = track,
                    index = index,
                    onClick = {
                        onPlayerIntent(PlayerIntent.PlayTracks(state.filteredTracks, index))
                    },
                )
            }
        }
    }
}

@Composable
private fun SourcesTab(
    state: ImportState,
    onImportIntent: (ImportIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(title = "导入来源", subtitle = "本地文件夹原地索引，Samba 与 WebDAV 作为远程音乐库。")
        if (state.message != null) {
            BannerCard(message = state.message, onDismiss = { onImportIntent(ImportIntent.ClearMessage) })
        }
        ElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("本地文件夹", fontWeight = FontWeight.Bold)
                Text("通过系统文件夹选择器授予目录权限并建立索引。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { onImportIntent(ImportIntent.ImportLocalFolder) },
                    enabled = state.capabilities.supportsLocalFolderImport && !state.isWorking,
                ) {
                    Icon(Icons.Rounded.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("选择文件夹")
                }
            }
        }
        ElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Samba / SMB", fontWeight = FontWeight.Bold)
                if (!state.capabilities.supportsSambaImport) {
                    Text("当前平台建议通过系统 Files 挂载 SMB 后，再用本地文件夹方式接入。")
                }
                OutlinedTextField(value = state.sambaLabel, onValueChange = { onImportIntent(ImportIntent.SambaLabelChanged(it)) }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = state.sambaServer, onValueChange = { onImportIntent(ImportIntent.SambaServerChanged(it)) }, label = { Text("服务器地址") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp))
                    OutlinedTextField(
                        value = state.sambaPort,
                        onValueChange = { onImportIntent(ImportIntent.SambaPortChanged(it)) },
                        label = { Text("端口") },
                        modifier = Modifier.width(140.dp),
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                OutlinedTextField(
                    value = state.sambaPath,
                    onValueChange = { onImportIntent(ImportIntent.SambaPathChanged(it)) },
                    label = { Text("路径（Share/子目录）") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = state.sambaUsername, onValueChange = { onImportIntent(ImportIntent.SambaUsernameChanged(it)) }, label = { Text("用户名") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp))
                    OutlinedTextField(value = state.sambaPassword, onValueChange = { onImportIntent(ImportIntent.SambaPasswordChanged(it)) }, label = { Text("密码（选填）") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp))
                }
                Button(
                    onClick = { onImportIntent(ImportIntent.AddSambaSource) },
                    enabled = !state.isWorking,
                ) {
                    Icon(Icons.Rounded.CloudSync, null)
                    Spacer(Modifier.width(8.dp))
                    Text("连接并扫描")
                }
            }
        }
        ElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("WebDAV", fontWeight = FontWeight.Bold)
                if (!state.capabilities.supportsWebDavImport) {
                    Text("当前平台暂未开放应用内 WebDAV 导入。")
                }
                OutlinedTextField(
                    value = state.webDavLabel,
                    onValueChange = { onImportIntent(ImportIntent.WebDavLabelChanged(it)) },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                )
                OutlinedTextField(
                    value = state.webDavRootUrl,
                    onValueChange = { onImportIntent(ImportIntent.WebDavRootUrlChanged(it)) },
                    label = { Text("根 URL") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.webDavUsername,
                        onValueChange = { onImportIntent(ImportIntent.WebDavUsernameChanged(it)) },
                        label = { Text("用户名（选填）") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                    )
                    OutlinedTextField(
                        value = state.webDavPassword,
                        onValueChange = { onImportIntent(ImportIntent.WebDavPasswordChanged(it)) },
                        label = { Text("密码（选填）") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("允许自签名证书", fontWeight = FontWeight.Medium)
                    Switch(
                        checked = state.webDavAllowInsecureTls,
                        onCheckedChange = { onImportIntent(ImportIntent.WebDavAllowInsecureTlsChanged(it)) },
                    )
                }
                Button(
                    onClick = { onImportIntent(ImportIntent.AddWebDavSource) },
                    enabled = state.capabilities.supportsWebDavImport && !state.isWorking,
                ) {
                    Icon(Icons.Rounded.CloudSync, null)
                    Spacer(Modifier.width(8.dp))
                    Text("连接并扫描")
                }
            }
        }
        SectionTitle(title = "已连接来源", subtitle = "重新扫描会刷新索引与歌词缓存匹配。")
        if (state.sources.isEmpty()) {
            EmptyStateCard(
                title = "还没有任何来源",
                body = "添加来源后，歌曲会在曲库里汇总显示，播放页会根据当前歌曲去匹配歌词。",
            )
        } else {
            state.sources.forEach { source ->
                SourceCard(
                    state = source,
                    enabled = !state.isWorking,
                    onRescan = { onImportIntent(ImportIntent.RescanSource(source.source.id)) },
                    onDelete = { onImportIntent(ImportIntent.DeleteSource(source.source.id)) },
                )
            }
        }
    }
}

@Composable
private fun SettingsTab(
    state: SettingsState,
    onSettingsIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(title = "歌词 API", subtitle = "声明式适配 JSON / XML / LRC / 纯文本返回。")
        if (state.message != null) {
            BannerCard(message = state.message, onDismiss = { onSettingsIntent(SettingsIntent.ClearMessage) })
        }
        ElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Samba 播放", fontWeight = FontWeight.Bold)
                Text(
                    "打开后会先缓存到本地再播放；关闭后桌面端直接返回 SMB 播放链接。移动端当前仍回退到缓存。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("使用本地缓存播放", fontWeight = FontWeight.Medium)
                    Switch(
                        checked = state.useSambaCache,
                        onCheckedChange = { onSettingsIntent(SettingsIntent.UseSambaCacheChanged(it)) },
                    )
                }
            }
        }
        ElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(value = state.name, onValueChange = { onSettingsIntent(SettingsIntent.NameChanged(it)) }, label = { Text("歌词源名称") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                OutlinedTextField(value = state.urlTemplate, onValueChange = { onSettingsIntent(SettingsIntent.UrlChanged(it)) }, label = { Text("URL 模板") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EnumSelector(label = "Method", values = RequestMethod.entries, selected = state.method, onSelected = { onSettingsIntent(SettingsIntent.MethodChanged(it)) }, modifier = Modifier.weight(1f))
                    EnumSelector(label = "Format", values = LyricsResponseFormat.entries, selected = state.responseFormat, onSelected = { onSettingsIntent(SettingsIntent.ResponseFormatChanged(it)) }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = state.queryTemplate, onValueChange = { onSettingsIntent(SettingsIntent.QueryChanged(it)) }, label = { Text("Query 模板") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                OutlinedTextField(value = state.bodyTemplate, onValueChange = { onSettingsIntent(SettingsIntent.BodyChanged(it)) }, label = { Text("Body 模板") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                OutlinedTextField(value = state.headersTemplate, onValueChange = { onSettingsIntent(SettingsIntent.HeadersChanged(it)) }, label = { Text("请求头，每行 Key: Value") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                OutlinedTextField(value = state.extractor, onValueChange = { onSettingsIntent(SettingsIntent.ExtractorChanged(it)) }, label = { Text("提取规则") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                OutlinedTextField(
                    value = state.priority,
                    onValueChange = { onSettingsIntent(SettingsIntent.PriorityChanged(it)) },
                    label = { Text("优先级") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("启用歌词源", fontWeight = FontWeight.Medium)
                    Switch(checked = state.enabled, onCheckedChange = { onSettingsIntent(SettingsIntent.EnabledChanged(it)) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onSettingsIntent(SettingsIntent.Save) }) { Text("保存") }
                    OutlinedButton(onClick = { onSettingsIntent(SettingsIntent.SelectConfig(null)) }) { Text("新建") }
                    if (state.editingId != null) {
                        TextButton(onClick = { onSettingsIntent(SettingsIntent.Delete) }) { Text("删除") }
                    }
                }
            }
        }

        SectionTitle(title = "已有配置", subtitle = "常见规则: `json:lyrics.lrc`、`json:[0].syncedLyrics`、`json-lines:data.lines|time,text`、`xml:lyrics`。")
        if (state.configs.isEmpty()) {
            EmptyStateCard(
                title = "还没有歌词源",
                body = "添加一个可用的 API 后，播放页会按优先级自动请求并缓存歌词。",
            )
        } else {
            state.configs.forEach { config ->
                LyricsSourceCard(config = config, onClick = { onSettingsIntent(SettingsIntent.SelectConfig(config)) })
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(
    state: PlayerState,
    onPlayerIntent: (PlayerIntent) -> Unit,
    compact: Boolean = false,
) {
    val track = state.snapshot.currentTrack ?: return
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 0.dp else 18.dp, vertical = 12.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPlayerIntent(PlayerIntent.ExpandedChanged(true)) }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            VinylPlaceholder(size = 50.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(state.snapshot.currentDisplayTitle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.snapshot.currentDisplayArtistName ?: "未知艺人", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipPrevious) }) { Icon(Icons.Rounded.SkipPrevious, null) }
            IconButton(onClick = { onPlayerIntent(PlayerIntent.TogglePlayPause) }) {
                Icon(if (state.snapshot.isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle, null, modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipNext) }) { Icon(Icons.Rounded.SkipNext, null) }
        }
    }
}

@Composable
private fun PlayerOverlay(
    state: PlayerState,
    onPlayerIntent: (PlayerIntent) -> Unit,
) {
    val track = state.snapshot.currentTrack ?: return
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wide = maxWidth >= 860.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(onClick = { onPlayerIntent(PlayerIntent.ExpandedChanged(false)) }) { Text("收起") }
                        AssistChip(onClick = {}, label = { Text(modeLabel(state.snapshot.mode)) }, leadingIcon = {
                            Icon(
                                if (state.snapshot.mode == PlaybackMode.SHUFFLE) Icons.Rounded.Shuffle else Icons.Rounded.Repeat,
                                null,
                            )
                        })
                    }
                    if (wide) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            PlayerInfoPane(
                                snapshot = state.snapshot,
                                track = track,
                                modifier = Modifier
                                    .weight(0.42f)
                                    .fillMaxHeight(),
                            )
                            PlayerLyricsPane(
                                state = state,
                                modifier = Modifier
                                    .weight(0.58f)
                                    .fillMaxHeight(),
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            PlayerInfoPane(
                                snapshot = state.snapshot,
                                track = track,
                                modifier = Modifier.fillMaxWidth(),
                                compact = true,
                            )
                            PlayerLyricsPane(
                                state = state,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    PlayerBottomControls(
                        snapshot = state.snapshot,
                        onPlayerIntent = onPlayerIntent,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerInfoPane(
    snapshot: PlaybackSnapshot,
    track: Track,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            VinylPlaceholder(
                size = if (compact) 180.dp else 260.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    snapshot.currentDisplayTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    snapshot.currentDisplayArtistName ?: "未知艺人",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    snapshot.currentDisplayAlbumTitle ?: "本地曲目",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(formatDuration(snapshot.durationMs)) },
                    leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) },
                )
                AssistChip(
                    onClick = {},
                    label = { Text(track.sourceId.substringBefore('-').uppercase()) },
                    leadingIcon = { Icon(Icons.Rounded.Album, null) },
                )
            }
            Text(
                track.relativePath,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlayerLyricsPane(
    state: PlayerState,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.highlightedLineIndex, state.lyrics?.sourceId, state.lyrics?.isSynced) {
        val lyrics = state.lyrics ?: return@LaunchedEffect
        if (!lyrics.isSynced) return@LaunchedEffect
        val index = state.highlightedLineIndex
        if (index !in lyrics.lines.indices) return@LaunchedEffect
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            listState.scrollToItem(index)
            withFrameNanos { }
        }
        val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return@LaunchedEffect
        val viewportCenter = (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
        val itemCenter = itemInfo.offset + itemInfo.size / 2
        val delta = (itemCenter - viewportCenter).toFloat()
        if (abs(delta) > 1f) {
            listState.scrollBy(delta)
        }
    }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionTitle(
                title = "歌词",
                subtitle = if (state.isLyricsLoading) "正在请求歌词..." else "支持 JSON、XML、LRC 与纯文本。",
            )
            if (state.lyrics == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyStateCard(
                        title = "暂时没有歌词",
                        body = "会先使用本地缓存与内嵌歌词，拿不到时再按当前标题和歌手请求。",
                    )
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val centerPadding = (maxHeight / 2 - 28.dp).coerceAtLeast(72.dp)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(vertical = centerPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(state.lyrics.lines) { index, line ->
                            val distance = if (state.highlightedLineIndex >= 0) {
                                abs(index - state.highlightedLineIndex)
                            } else {
                                Int.MAX_VALUE
                            }
                            val targetAlpha = when {
                                state.highlightedLineIndex < 0 -> 0.92f
                                distance == 0 -> 1f
                                distance == 1 -> 0.72f
                                distance == 2 -> 0.5f
                                else -> 0.34f
                            }
                            val targetScale = when {
                                state.highlightedLineIndex < 0 -> 1f
                                distance == 0 -> 1.08f
                                distance == 1 -> 1.01f
                                else -> 1f
                            }
                            val animatedAlpha by animateFloatAsState(targetValue = targetAlpha)
                            val animatedScale by animateFloatAsState(targetValue = targetScale)
                            val animatedColor by animateColorAsState(
                                targetValue = if (index == state.highlightedLineIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                text = line.text,
                                style = if (index == state.highlightedLineIndex) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                color = animatedColor,
                                textAlign = TextAlign.Center,
                                fontWeight = if (index == state.highlightedLineIndex) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer(
                                        alpha = animatedAlpha,
                                        scaleX = animatedScale,
                                        scaleY = animatedScale,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerBottomControls(
    snapshot: PlaybackSnapshot,
    onPlayerIntent: (PlayerIntent) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlaybackProgress(snapshot, onPlayerIntent)
            PlaybackVolume(snapshot, onPlayerIntent)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onPlayerIntent(PlayerIntent.CycleMode) }) {
                    Icon(if (snapshot.mode == PlaybackMode.SHUFFLE) Icons.Rounded.Shuffle else Icons.Rounded.Repeat, null)
                }
                IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipPrevious) }) {
                    Icon(Icons.Rounded.SkipPrevious, null, modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = { onPlayerIntent(PlayerIntent.TogglePlayPause) }) {
                    Icon(
                        if (snapshot.isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                        null,
                        modifier = Modifier.size(76.dp),
                    )
                }
                IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipNext) }) {
                    Icon(Icons.Rounded.SkipNext, null, modifier = Modifier.size(38.dp))
                }
            }
        }
    }
}

@Composable
private fun PlaybackProgress(
    snapshot: PlaybackSnapshot,
    onPlayerIntent: (PlayerIntent) -> Unit,
) {
    val duration = snapshot.durationMs.coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Slider(
            value = snapshot.positionMs.coerceIn(0L, duration).toFloat(),
            onValueChange = { onPlayerIntent(PlayerIntent.SeekTo(it.toLong())) },
            valueRange = 0f..duration.toFloat(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(snapshot.positionMs))
            Text(formatDuration(snapshot.durationMs))
        }
    }
}

@Composable
private fun PlaybackVolume(
    snapshot: PlaybackSnapshot,
    onPlayerIntent: (PlayerIntent) -> Unit,
) {
    val volume = snapshot.volume.coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                Text("音量")
            }
            Text("${(volume * 100).roundToInt()}%")
        }
        Slider(
            value = volume,
            onValueChange = { onPlayerIntent(PlayerIntent.SetVolume(it)) },
            valueRange = 0f..1f,
        )
    }
}

@Composable
private fun TrackRow(
    track: Track,
    index: Int,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text((index + 1).toString().padStart(2, '0'), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            VinylPlaceholder(size = 52.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(track.artistName ?: "未知艺人", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(formatDuration(track.durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SourceCard(
    state: top.iwesley.lyn.music.core.model.SourceWithStatus,
    enabled: Boolean,
    onRescan: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(shape = RoundedCornerShape(26.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
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
                            top.iwesley.lyn.music.core.model.ImportSourceType.WEBDAV -> state.source.rootReference
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRescan, enabled = enabled) {
                        Icon(Icons.Rounded.Sync, null)
                        Spacer(Modifier.width(6.dp))
                        Text("重扫")
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AssistChip(onClick = {}, label = { Text("${state.indexState?.trackCount ?: 0} 首歌曲") }, leadingIcon = { Icon(Icons.Rounded.LibraryMusic, null) })
                AssistChip(onClick = {}, label = { Text(if (state.indexState?.lastError == null) "扫描正常" else "扫描失败") }, leadingIcon = { Icon(Icons.Rounded.CloudSync, null) })
            }
            state.indexState?.lastError?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun LyricsSourceCard(
    config: LyricsSourceConfig,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(config.name, fontWeight = FontWeight.Bold)
                Text(if (config.enabled) "已启用" else "已停用", color = MaterialTheme.colorScheme.primary)
            }
            Text(config.urlTemplate, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(config.method.name) }, leadingIcon = { Icon(Icons.Rounded.CloudSync, null) })
                AssistChip(onClick = {}, label = { Text(config.responseFormat.name) }, leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) })
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BannerCard(
    message: String,
    onDismiss: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
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
private fun EmptyStateCard(
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
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VinylPlaceholder(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFDE2E4),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                        Color(0xFF3A2226),
                    ),
                ),
            )
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size / 4)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f)),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Enum<T>> EnumSelector(
    label: String,
    values: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                values.forEach { value ->
                    val active = value == selected
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
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

private fun modeLabel(mode: PlaybackMode): String {
    return when (mode) {
        PlaybackMode.ORDER -> "顺序播放"
        PlaybackMode.SHUFFLE -> "随机播放"
        PlaybackMode.REPEAT_ONE -> "单曲循环"
    }
}

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1_000).coerceAtLeast(0L)
    val minutesPart = seconds / 60
    val secondsPart = seconds % 60
    return minutesPart.toString().padStart(2, '0') + ":" + secondsPart.toString().padStart(2, '0')
}
