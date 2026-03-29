package top.iwesley.lyn.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import top.iwesley.lyn.music.core.model.AppTab
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.ArtworkLoader
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
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
import top.iwesley.lyn.music.platform.rememberPlatformArtworkBitmap
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
    artworkCacheStore: ArtworkCacheStore = object : ArtworkCacheStore {
        override suspend fun cache(locator: String, cacheKey: String): String? = locator
    },
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
    val lyricsRepository = DefaultLyricsRepository(database, lyricsHttpClient, artworkCacheStore, logger)
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
            Column(
                modifier = Modifier.navigationBarsPadding(),
            ) {
                AnimatedVisibility(visible = playerState.snapshot.currentTrack != null && !playerState.isExpanded) {
                    MiniPlayerBar(
                        state = playerState,
                        onPlayerIntent = onPlayerIntent,
                    )
                }
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
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
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
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
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
            AnimatedVisibility(visible = playerState.snapshot.currentTrack != null && !playerState.isExpanded) {
                MiniPlayerBar(
                    state = playerState,
                    onPlayerIntent = onPlayerIntent,
                    compact = false,
                )
            }
        }
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
        ElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Workflow JSON 导入", fontWeight = FontWeight.Bold)
                Text(
                    "用于导入多阶段歌词源，支持搜歌 -> 选歌 -> 拉歌词。当前只支持粘贴一个 provider 的 JSON 文件。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.workflowJsonInput,
                    onValueChange = { onSettingsIntent(SettingsIntent.WorkflowJsonChanged(it)) },
                    label = { Text("Workflow JSON") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp),
                    shape = RoundedCornerShape(18.dp),
                    minLines = 10,
                    maxLines = 18,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onSettingsIntent(SettingsIntent.ImportWorkflow) }) { Text("导入 Workflow") }
                    if (state.viewingWorkflowId != null) {
                        OutlinedButton(onClick = { onSettingsIntent(SettingsIntent.ViewWorkflow(null)) }) { Text("清空查看") }
                    }
                }
            }
        }

        SectionTitle(title = "已有配置", subtitle = "Direct 源继续走声明式 extractor；Workflow 源通过 JSON 导入并参与同一优先级链路。")
        if (state.sources.isEmpty()) {
            EmptyStateCard(
                title = "还没有歌词源",
                body = "添加一个可用的 API 后，播放页会按优先级自动请求并缓存歌词。",
            )
        } else {
            state.sources.forEach { source ->
                LyricsSourceCard(
                    source = source,
                    onClick = {
                        when (source) {
                            is LyricsSourceConfig -> onSettingsIntent(SettingsIntent.SelectConfig(source))
                            is top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig -> onSettingsIntent(SettingsIntent.ViewWorkflow(source))
                        }
                    },
                    onToggleEnabled = {
                        onSettingsIntent(SettingsIntent.ToggleSourceEnabled(source.id, !source.enabled))
                    },
                    onDelete = {
                        onSettingsIntent(SettingsIntent.DeleteSource(source.id))
                    },
                )
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
        color = Color(0xFF232325),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                            Color(0xFF232325),
                        ),
                        radius = 1400f,
                    ),
                ),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wide = maxWidth >= 980.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 26.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(onClick = { onPlayerIntent(PlayerIntent.ExpandedChanged(false)) }) { Text("收起") }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (state.snapshot.mode == PlaybackMode.SHUFFLE) Icons.Rounded.Shuffle else Icons.Rounded.Repeat,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                            )
                            Text(
                                modeLabel(state.snapshot.mode),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                            )
                        }
                    }
                    if (wide) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(34.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlayerInfoPane(
                                snapshot = state.snapshot,
                                track = track,
                                modifier = Modifier
                                    .weight(0.46f)
                                    .fillMaxHeight(),
                            )
                            PlayerLyricsPane(
                                state = state,
                                track = track,
                                onPlayerIntent = onPlayerIntent,
                                modifier = Modifier
                                    .weight(0.54f)
                                    .fillMaxHeight(),
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            PlayerInfoPane(
                                snapshot = state.snapshot,
                                track = track,
                                modifier = Modifier.fillMaxWidth(),
                                compact = true,
                            )
                            PlayerLyricsPane(
                                state = state,
                                track = track,
                                onPlayerIntent = onPlayerIntent,
                                modifier = Modifier.weight(1f),
                                compact = true,
                            )
                        }
                    }
                    PlayerBottomControls(
                        snapshot = state.snapshot,
                        track = track,
                        wide = wide,
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
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        )
        TurntableArmDecoration(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = if (compact) 46.dp else 88.dp, y = if (compact) 22.dp else 34.dp),
            compact = compact,
        )
        VinylPlaceholder(
            size = if (compact) 250.dp else 420.dp,
            artworkLocator = snapshot.currentDisplayArtworkLocator,
            spinning = snapshot.isPlaying,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun PlayerLyricsPane(
    state: PlayerState,
    track: Track,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
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
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 8.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    state.snapshot.currentDisplayTitle,
                    style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.96f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "专辑：${state.snapshot.currentDisplayAlbumTitle ?: "本地曲目"}    歌手：${state.snapshot.currentDisplayArtistName ?: "未知艺人"}    来源：${track.sourceId.substringBefore('-').uppercase()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = if (compact) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.relativePath,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerSectionChip(label = "歌词", active = true)
                PlayerSectionChip(label = "信息", active = false)
                PlayerSectionChip(label = modeLabel(state.snapshot.mode), active = false)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (state.isLyricsLoading) "正在请求歌词..." else "歌词",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.88f),
                )
                OutlinedButton(
                    onClick = { onPlayerIntent(PlayerIntent.OpenManualLyricsSearch) },
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("手动搜索")
                }
            }
            if (state.lyrics == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyStateCard(
                        title = "暂时没有歌词",
                        body = "会先使用本地缓存与内嵌歌词，拿不到时再按当前标题和歌手请求。",
                    )
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val centerPadding = (maxHeight / 2 - 36.dp).coerceAtLeast(if (compact) 56.dp else 86.dp)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = if (compact) 540.dp else 520.dp),
                        state = listState,
                        contentPadding = PaddingValues(vertical = centerPadding),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
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
                                    Color.White.copy(alpha = 0.96f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                                },
                            )
                            Text(
                                text = line.text,
                                style = if (index == state.highlightedLineIndex) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                                color = animatedColor,
                                textAlign = TextAlign.Start,
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
        if (state.isManualLyricsSearchVisible) {
            ManualLyricsSearchOverlay(
                state = state,
                onPlayerIntent = onPlayerIntent,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ManualLyricsSearchOverlay(
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
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable { onPlayerIntent(PlayerIntent.DismissManualLyricsSearch) },
        )
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .padding(horizontal = 20.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2D)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "手动搜索歌词",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor,
                        )
                        Text(
                            "修改标题、歌手、专辑后重新向已启用歌词源搜索。",
                            color = secondaryTextColor,
                        )
                    }
                    TextButton(onClick = { onPlayerIntent(PlayerIntent.DismissManualLyricsSearch) }) {
                        Text("关闭", color = primaryTextColor)
                    }
                }
                OutlinedTextField(
                    value = state.manualLyricsTitle,
                    onValueChange = { onPlayerIntent(PlayerIntent.ManualLyricsTitleChanged(it)) },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    colors = textFieldColors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.manualLyricsArtistName,
                        onValueChange = { onPlayerIntent(PlayerIntent.ManualLyricsArtistChanged(it)) },
                        label = { Text("歌手") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        singleLine = true,
                        colors = textFieldColors,
                    )
                    OutlinedTextField(
                        value = state.manualLyricsAlbumTitle,
                        onValueChange = { onPlayerIntent(PlayerIntent.ManualLyricsAlbumChanged(it)) },
                        label = { Text("专辑") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        singleLine = true,
                        colors = textFieldColors,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { onPlayerIntent(PlayerIntent.DismissManualLyricsSearch) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryTextColor),
                    ) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { onPlayerIntent(PlayerIntent.SearchManualLyrics) },
                        enabled = !state.isManualLyricsSearchLoading && state.manualLyricsTitle.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.14f),
                            contentColor = primaryTextColor,
                            disabledContainerColor = Color.White.copy(alpha = 0.08f),
                            disabledContentColor = secondaryTextColor,
                        ),
                    ) {
                        Text(if (state.isManualLyricsSearchLoading) "搜索中..." else "搜索")
                    }
                }
                state.manualLyricsError?.let { error ->
                    ElevatedCard(
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                when {
                    state.isManualLyricsSearchLoading -> {
                        Text(
                            "正在请求已启用的歌词源...",
                            color = secondaryTextColor,
                        )
                    }

                    state.manualLyricsResults.isNotEmpty() || state.manualWorkflowSongResults.isNotEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (state.manualLyricsResults.isNotEmpty()) {
                                Text("直接歌词结果", color = primaryTextColor, fontWeight = FontWeight.SemiBold)
                                state.manualLyricsResults.forEach { candidate ->
                                    ElevatedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onPlayerIntent(PlayerIntent.ApplyManualLyricsCandidate(candidate)) },
                                        colors = CardDefaults.elevatedCardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                                        shape = RoundedCornerShape(20.dp),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
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
                                Text("Workflow 歌曲候选", color = primaryTextColor, fontWeight = FontWeight.SemiBold)
                                state.manualWorkflowSongResults.forEach { candidate ->
                                    ElevatedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onPlayerIntent(PlayerIntent.ApplyWorkflowSongCandidate(candidate)) },
                                        colors = CardDefaults.elevatedCardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                                        shape = RoundedCornerShape(20.dp),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
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
                                                        "${seconds}s",
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
                        EmptyStateCard(
                            title = "没有找到可用歌词",
                            body = "当前已启用歌词源都没有返回可解析结果，可以继续修改标题、歌手或专辑再试。",
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
private fun PlayerBottomControls(
    snapshot: PlaybackSnapshot,
    track: Track,
    wide: Boolean,
    onPlayerIntent: (PlayerIntent) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.16f),
                                colors.heroGlow.copy(alpha = 0.12f),
                                colors.surface.copy(alpha = 0.08f),
                            ),
                        ),
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            formatDuration(snapshot.positionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                        Text(
                            formatDuration(snapshot.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                    if (wide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                buildString {
                                    append(snapshot.currentDisplayTitle)
                                    append("  ")
                                    append(snapshot.currentDisplayArtistName ?: "未知艺人")
                                    append("  ")
                                    append(track.sourceId.substringBefore('-').uppercase())
                                },
                                modifier = Modifier.weight(0.30f),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                modifier = Modifier.weight(0.40f),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { onPlayerIntent(PlayerIntent.CycleMode) }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        if (snapshot.mode == PlaybackMode.SHUFFLE) Icons.Rounded.Shuffle else Icons.Rounded.Repeat,
                                        null,
                                        modifier = Modifier.size(15.dp),
                                        tint = Color.White.copy(alpha = 0.92f),
                                    )
                                }
                                IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipPrevious) }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Rounded.SkipPrevious, null, modifier = Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.92f))
                                }
                                IconButton(onClick = { onPlayerIntent(PlayerIntent.TogglePlayPause) }, modifier = Modifier.size(40.dp)) {
                                    Icon(
                                        if (snapshot.isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                                        null,
                                        modifier = Modifier.size(28.dp),
                                        tint = Color.White.copy(alpha = 0.96f),
                                    )
                                }
                                IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipNext) }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Rounded.SkipNext, null, modifier = Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.92f))
                                }
                            }
                            Box(modifier = Modifier.weight(0.30f)) {
                                PlaybackVolume(snapshot, onPlayerIntent, sliderWidthFraction = 0.5f)
                            }
                        }
                    } else {
                        PlaybackVolume(snapshot, onPlayerIntent)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { onPlayerIntent(PlayerIntent.CycleMode) }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    if (snapshot.mode == PlaybackMode.SHUFFLE) Icons.Rounded.Shuffle else Icons.Rounded.Repeat,
                                    null,
                                    modifier = Modifier.size(15.dp),
                                    tint = Color.White.copy(alpha = 0.92f),
                                )
                            }
                            IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipPrevious) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.SkipPrevious, null, modifier = Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.92f))
                            }
                            IconButton(onClick = { onPlayerIntent(PlayerIntent.TogglePlayPause) }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    if (snapshot.isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                                    null,
                                    modifier = Modifier.size(28.dp),
                                    tint = Color.White.copy(alpha = 0.96f),
                                )
                            }
                            IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipNext) }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.SkipNext, null, modifier = Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.92f))
                            }
                        }
                    }
                }
            }
        }
        PlaybackProgress(
            snapshot = snapshot,
            onPlayerIntent = onPlayerIntent,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 5.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            showTimeLabels = false,
            floating = true,
        )
    }
}

@Composable
private fun PlayerSectionChip(
    label: String,
    active: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (active) {
            Color.White.copy(alpha = 0.18f)
        } else {
            Color.White.copy(alpha = 0.06f)
        },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (active) Color.White.copy(alpha = 0.96f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun TurntableArmDecoration(
    modifier: Modifier = Modifier,
    compact: Boolean,
) {
    Box(
        modifier = modifier.size(if (compact) 140.dp else 210.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 24.dp else 32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 10.dp else 12.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.96f)),
            )
        }
        Box(
            modifier = Modifier
                .offset(x = if (compact) 18.dp else 24.dp, y = if (compact) 18.dp else 22.dp)
                .size(width = if (compact) 94.dp else 138.dp, height = if (compact) 8.dp else 10.dp)
                .graphicsLayer(rotationZ = 42f)
                .clip(RoundedCornerShape(100))
                .background(Color.White.copy(alpha = 0.96f)),
        )
        Box(
            modifier = Modifier
                .offset(x = if (compact) 86.dp else 126.dp, y = if (compact) 80.dp else 118.dp)
                .size(width = if (compact) 40.dp else 54.dp, height = if (compact) 8.dp else 10.dp)
                .graphicsLayer(rotationZ = 4f)
                .clip(RoundedCornerShape(100))
                .background(Color.White.copy(alpha = 0.96f)),
        )
        Box(
            modifier = Modifier
                .offset(x = if (compact) 112.dp else 166.dp, y = if (compact) 78.dp else 116.dp)
                .size(width = if (compact) 18.dp else 24.dp, height = if (compact) 18.dp else 24.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.9f)),
        )
    }
}

@Composable
private fun PlaybackProgress(
    snapshot: PlaybackSnapshot,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
    showTimeLabels: Boolean = true,
    floating: Boolean = false,
) {
    val duration = snapshot.durationMs.coerceAtLeast(1L)
    val progressFraction = (snapshot.positionMs.coerceIn(0L, duration).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val particles = remember { mutableStateListOf<ProgressFlowerParticle>() }
    var lastEmissionFraction by remember { mutableStateOf(progressFraction) }
    var lastEmissionNanos by remember { mutableStateOf(0L) }
    var animationFrameNanos by remember { mutableStateOf(0L) }
    LaunchedEffect(snapshot.positionMs, snapshot.isPlaying, duration) {
        val currentFraction = progressFraction
        if (currentFraction < lastEmissionFraction) {
            lastEmissionFraction = currentFraction
            lastEmissionNanos = 0L
            return@LaunchedEffect
        }
        if (!snapshot.isPlaying || duration <= 1L || currentFraction <= 0f) {
            lastEmissionFraction = currentFraction
            return@LaunchedEffect
        }
        val now = withFrameNanos { it }
        val movedEnough = currentFraction - lastEmissionFraction >= 0.018f
        val throttled = lastEmissionNanos == 0L || now - lastEmissionNanos >= 240_000_000L
        if (movedEnough && throttled) {
            val seed = (now % Int.MAX_VALUE.toLong()).toInt().absoluteValue
            repeat(3) { index ->
                particles += buildProgressFlowerParticle(
                    progressFraction = currentFraction,
                    seed = seed + index * 97,
                    bornAtNanos = now,
                )
            }
            lastEmissionFraction = currentFraction
            lastEmissionNanos = now
        }
    }
    LaunchedEffect(particles.size) {
        if (particles.isEmpty()) return@LaunchedEffect
        while (particles.isNotEmpty()) {
            withFrameNanos { now ->
                animationFrameNanos = now
                particles.removeAll { particle -> now - particle.bornAtNanos >= FLOWER_PARTICLE_LIFETIME_NANOS }
            }
        }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (floating) 12.dp else 22.dp),
        ) {
            val thumbInsetPx = with(LocalDensity.current) { if (floating) 6.dp.toPx() else 8.dp.toPx() }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (floating) 0.dp else 4.dp),
            ) {
                particles.forEach { particle ->
                    drawProgressFlowerParticle(
                        particle = particle,
                        nowNanos = animationFrameNanos,
                        thumbInsetPx = thumbInsetPx,
                    )
                }
            }
            Slider(
                modifier = Modifier
                    .align(if (floating) Alignment.Center else Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(8.dp)
                    .graphicsLayer(scaleY = if (floating) 0.36f else 0.44f),
                colors = playerSliderColors(),
                value = snapshot.positionMs.coerceIn(0L, duration).toFloat(),
                onValueChange = { onPlayerIntent(PlayerIntent.SeekTo(it.toLong())) },
                valueRange = 0f..duration.toFloat(),
            )
        }
        if (showTimeLabels) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(snapshot.positionMs), style = MaterialTheme.typography.labelSmall, color = Color.White)
                Text(formatDuration(snapshot.durationMs), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

@Composable
private fun PlaybackVolume(
    snapshot: PlaybackSnapshot,
    onPlayerIntent: (PlayerIntent) -> Unit,
    sliderWidthFraction: Float = 1f,
) {
    val volume = snapshot.volume.coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Text("音量", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
            Text("${(volume * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Slider(
                modifier = Modifier
                    .fillMaxWidth(sliderWidthFraction.coerceIn(0.2f, 1f))
                    .height(8.dp)
                    .graphicsLayer(scaleY = 0.44f),
                colors = playerSliderColors(),
                value = volume,
                onValueChange = { onPlayerIntent(PlayerIntent.SetVolume(it)) },
                valueRange = 0f..1f,
            )
        }
    }
}

@Composable
private fun playerSliderColors() = SliderDefaults.colors(
    thumbColor = Color.White.copy(alpha = 0.98f),
    activeTrackColor = Color.White.copy(alpha = 0.96f),
    inactiveTrackColor = Color.White.copy(alpha = 0.24f),
    activeTickColor = Color.White.copy(alpha = 0.96f),
    inactiveTickColor = Color.White.copy(alpha = 0.24f),
)

private data class ProgressFlowerParticle(
    val progressFraction: Float,
    val bornAtNanos: Long,
    val driftX: Float,
    val liftY: Float,
    val sway: Float,
    val bloomRadius: Float,
    val colorIndex: Int,
    val rotationOffset: Float,
)

private fun buildProgressFlowerParticle(
    progressFraction: Float,
    seed: Int,
    bornAtNanos: Long,
): ProgressFlowerParticle {
    val normalized = seed.absoluteValue
    return ProgressFlowerParticle(
        progressFraction = progressFraction,
        bornAtNanos = bornAtNanos,
        driftX = (((normalized % 24) - 12).toFloat()) * 2.1f,
        liftY = 22f + (normalized % 32).toFloat(),
        sway = 6f + (normalized % 9).toFloat(),
        bloomRadius = 3.6f + ((normalized % 4).toFloat() * 0.8f),
        colorIndex = normalized % FLOWER_PARTICLE_COLORS.size,
        rotationOffset = (normalized % 360).toFloat() * (PI.toFloat() / 180f),
    )
}

private fun DrawScope.drawProgressFlowerParticle(
    particle: ProgressFlowerParticle,
    nowNanos: Long,
    thumbInsetPx: Float,
) {
    if (nowNanos <= particle.bornAtNanos) return
    val age = ((nowNanos - particle.bornAtNanos).toFloat() / FLOWER_PARTICLE_LIFETIME_NANOS.toFloat()).coerceIn(0f, 1f)
    if (age >= 1f) return
    val easeOut = 1f - (1f - age) * (1f - age)
    val usableWidth = (size.width - thumbInsetPx * 2f).coerceAtLeast(0f)
    val baseX = thumbInsetPx + usableWidth * particle.progressFraction
    val baseY = size.height - 8.dp.toPx()
    val swayOffset = sin((age * 1.8f + particle.rotationOffset) * PI.toFloat()) * particle.sway
    val center = Offset(
        x = baseX + particle.driftX * easeOut + swayOffset,
        y = baseY - particle.liftY * easeOut,
    )
    val petalColor = FLOWER_PARTICLE_COLORS[particle.colorIndex].copy(alpha = (1f - age) * 0.92f)
    val petalOrbit = particle.bloomRadius * 1.55f
    val rotation = particle.rotationOffset + age * 2.4f
    repeat(5) { index ->
        val angle = rotation + index * ((2f * PI.toFloat()) / 5f)
        val petalCenter = Offset(
            x = center.x + cos(angle) * petalOrbit,
            y = center.y + sin(angle) * petalOrbit,
        )
        drawCircle(
            color = petalColor,
            radius = particle.bloomRadius,
            center = petalCenter,
        )
    }
    drawCircle(
        color = Color(0xFFFFF2A8).copy(alpha = (1f - age) * 0.98f),
        radius = particle.bloomRadius * 0.82f,
        center = center,
    )
}

private val FLOWER_PARTICLE_COLORS = listOf(
    Color(0xFFFF5D9E),
    Color(0xFFFFB341),
    Color(0xFF7DFF93),
    Color(0xFF68D5FF),
    Color(0xFFC792FF),
)

private const val FLOWER_PARTICLE_LIFETIME_NANOS = 900_000_000L

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
    source: top.iwesley.lyn.music.core.model.LyricsSourceDefinition,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
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
                Text(source.name, fontWeight = FontWeight.Bold)
                Text(if (source.enabled) "已启用" else "已停用", color = MaterialTheme.colorScheme.primary)
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
                        AssistChip(onClick = {}, label = { Text(source.method.name) }, leadingIcon = { Icon(Icons.Rounded.CloudSync, null) })
                        AssistChip(onClick = {}, label = { Text(source.responseFormat.name) }, leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) })
                    }

                    is top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig -> {
                        AssistChip(onClick = {}, label = { Text("WORKFLOW") }, leadingIcon = { Icon(Icons.Rounded.CloudSync, null) })
                        AssistChip(onClick = {}, label = { Text("${source.lyrics.steps.size} 步") }, leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) })
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
    artworkLocator: String? = null,
    spinning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val artworkBitmap = rememberPlatformArtworkBitmap(artworkLocator)
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
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), CircleShape)
            .graphicsLayer { rotationZ = rotation.value },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size * 0.58f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (artworkBitmap != null) {
                Image(
                    bitmap = artworkBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(size / 4)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
                )
            }
        }
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
