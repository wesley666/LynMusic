package top.iwesley.lyn.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import top.iwesley.lyn.music.core.model.AppTab
import top.iwesley.lyn.music.core.model.ArtworkTintTheme
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsShareArtworkTintSpec
import top.iwesley.lyn.music.core.model.LyricsShareCardSpec
import top.iwesley.lyn.music.core.model.LyricsShareTemplate
import top.iwesley.lyn.music.core.model.LyricsResponseFormat
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
import top.iwesley.lyn.music.core.model.LyricsSourceConfig
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaybackMode
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.argbWithAlpha
import top.iwesley.lyn.music.core.model.buildLyricsShareTitleArtistLine
import top.iwesley.lyn.music.core.model.deriveArtworkTintTheme
import top.iwesley.lyn.music.data.repository.DefaultPlaybackRepository
import top.iwesley.lyn.music.data.repository.PlayerRuntimeServices
import top.iwesley.lyn.music.feature.importing.ImportIntent
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.importing.ImportStore
import top.iwesley.lyn.music.feature.favorites.FavoritesIntent
import top.iwesley.lyn.music.feature.favorites.FavoritesState
import top.iwesley.lyn.music.feature.favorites.FavoritesStore
import top.iwesley.lyn.music.feature.library.LibraryIntent
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.LibraryState
import top.iwesley.lyn.music.feature.library.LibraryStore
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.feature.player.PlayerStore
import top.iwesley.lyn.music.feature.settings.SettingsIntent
import top.iwesley.lyn.music.feature.settings.SettingsState
import top.iwesley.lyn.music.feature.settings.SettingsStore
import top.iwesley.lyn.music.feature.tags.MusicTagsEffect
import top.iwesley.lyn.music.feature.tags.MusicTagsIntent
import top.iwesley.lyn.music.feature.tags.MusicTagsState
import top.iwesley.lyn.music.feature.tags.MusicTagsStore
import top.iwesley.lyn.music.platform.rememberPlatformArtworkBitmap
import top.iwesley.lyn.music.platform.rememberPlatformImageBitmap
import top.iwesley.lyn.music.ui.LynMusicTheme
import top.iwesley.lyn.music.ui.heroGlow

class LynMusicAppComponent(
    val platform: PlatformDescriptor,
    val libraryStore: LibraryStore,
    val favoritesStore: FavoritesStore,
    val musicTagsStore: MusicTagsStore,
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

fun buildPlayerAppComponent(
    sharedGraph: SharedGraph,
    playerRuntimeServices: PlayerRuntimeServices,
): LynMusicAppComponent {
    val playbackRepository = DefaultPlaybackRepository(
        database = sharedGraph.database,
        gateway = playerRuntimeServices.playbackGateway,
        scope = sharedGraph.scope,
    )
    return LynMusicAppComponent(
        platform = sharedGraph.platform,
        libraryStore = sharedGraph.libraryStore,
        favoritesStore = sharedGraph.favoritesStore,
        musicTagsStore = sharedGraph.musicTagsStore,
        importStore = sharedGraph.importStore,
        playerStore = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = sharedGraph.lyricsRepository,
            storeScope = sharedGraph.scope,
            lyricsSharePlatformService = playerRuntimeServices.lyricsSharePlatformService,
        ),
        settingsStore = sharedGraph.settingsStore,
        scope = sharedGraph.scope,
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
    val favoritesState by component.favoritesStore.state.collectAsState()
    val musicTagsState by component.musicTagsStore.state.collectAsState()
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
                        favoritesState = favoritesState,
                        musicTagsState = musicTagsState,
                        musicTagsEffects = component.musicTagsStore.effects,
                        importState = importState,
                        playerState = playerState,
                        settingsState = settingsState,
                        onLibraryIntent = component.libraryStore::dispatch,
                        onFavoritesIntent = component.favoritesStore::dispatch,
                        onMusicTagsIntent = component.musicTagsStore::dispatch,
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
                        favoritesState = favoritesState,
                        musicTagsState = musicTagsState,
                        musicTagsEffects = component.musicTagsStore.effects,
                        importState = importState,
                        playerState = playerState,
                        settingsState = settingsState,
                        onLibraryIntent = component.libraryStore::dispatch,
                        onFavoritesIntent = component.favoritesStore::dispatch,
                        onMusicTagsIntent = component.musicTagsStore::dispatch,
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
                        platform = component.platform,
                        state = playerState,
                        onPlayerIntent = component.playerStore::dispatch,
                        isFavorite = playerState.snapshot.currentTrack?.id in favoritesState.favoriteTrackIds,
                        onToggleFavorite = {
                            playerState.snapshot.currentTrack?.let { track ->
                                component.favoritesStore.dispatch(FavoritesIntent.ToggleFavorite(track))
                            }
                        },
                        onOpenQueue = {
                            component.playerStore.dispatch(PlayerIntent.QueueVisibilityChanged(true))
                        },
                    )
                }
                QueueDrawer(
                    state = playerState,
                    compact = compact,
                    onPlayerIntent = component.playerStore::dispatch,
                    modifier = Modifier.fillMaxSize(),
                )
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
    favoritesState: FavoritesState,
    musicTagsState: MusicTagsState,
    musicTagsEffects: Flow<MusicTagsEffect>,
    importState: ImportState,
    playerState: PlayerState,
    settingsState: SettingsState,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onMusicTagsIntent: (MusicTagsIntent) -> Unit,
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
                        isFavorite = playerState.snapshot.currentTrack?.id in favoritesState.favoriteTrackIds,
                        onToggleFavorite = {
                            playerState.snapshot.currentTrack?.let { track ->
                                onFavoritesIntent(FavoritesIntent.ToggleFavorite(track))
                            }
                        },
                        onOpenQueue = { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(true)) },
                    )
                }
                NavigationBar {
                    listOf(
                        Triple(AppTab.Library, Icons.Rounded.LibraryMusic, "曲库"),
                        Triple(AppTab.Favorites, Icons.Rounded.Favorite, "喜欢"),
                        Triple(AppTab.Tags, Icons.Rounded.Tune, "音乐标签"),
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
                platform = platform,
                libraryState = libraryState,
                favoritesState = favoritesState,
                musicTagsState = musicTagsState,
                musicTagsEffects = musicTagsEffects,
                importState = importState,
                settingsState = settingsState,
                onLibraryIntent = onLibraryIntent,
                onFavoritesIntent = onFavoritesIntent,
                onMusicTagsIntent = onMusicTagsIntent,
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
    favoritesState: FavoritesState,
    musicTagsState: MusicTagsState,
    musicTagsEffects: Flow<MusicTagsEffect>,
    importState: ImportState,
    playerState: PlayerState,
    settingsState: SettingsState,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onMusicTagsIntent: (MusicTagsIntent) -> Unit,
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
                platform = platform,
                libraryState = libraryState,
                favoritesState = favoritesState,
                musicTagsState = musicTagsState,
                musicTagsEffects = musicTagsEffects,
                importState = importState,
                settingsState = settingsState,
                onLibraryIntent = onLibraryIntent,
                onFavoritesIntent = onFavoritesIntent,
                onMusicTagsIntent = onMusicTagsIntent,
                onImportIntent = onImportIntent,
                onPlayerIntent = onPlayerIntent,
                onSettingsIntent = onSettingsIntent,
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(visible = playerState.snapshot.currentTrack != null && !playerState.isExpanded) {
                MiniPlayerBar(
                    state = playerState,
                    onPlayerIntent = onPlayerIntent,
                    isFavorite = playerState.snapshot.currentTrack?.id in favoritesState.favoriteTrackIds,
                    onToggleFavorite = {
                        playerState.snapshot.currentTrack?.let { track ->
                            onFavoritesIntent(FavoritesIntent.ToggleFavorite(track))
                        }
                    },
                    onOpenQueue = { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(true)) },
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
                Triple(AppTab.Favorites, Icons.Rounded.Favorite, "喜欢"),
                Triple(AppTab.Tags, Icons.Rounded.Tune, "音乐标签"),
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
    platform: PlatformDescriptor,
    libraryState: LibraryState,
    favoritesState: FavoritesState,
    musicTagsState: MusicTagsState,
    musicTagsEffects: Flow<MusicTagsEffect>,
    importState: ImportState,
    settingsState: SettingsState,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onMusicTagsIntent: (MusicTagsIntent) -> Unit,
    onImportIntent: (ImportIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    onSettingsIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (selectedTab) {
        AppTab.Library -> LibraryTab(
            state = libraryState,
            favoritesState = favoritesState,
            onLibraryIntent = onLibraryIntent,
            onFavoritesIntent = onFavoritesIntent,
            onPlayerIntent = onPlayerIntent,
            modifier = modifier,
        )

        AppTab.Favorites -> FavoritesTab(
            state = favoritesState,
            onFavoritesIntent = onFavoritesIntent,
            onPlayerIntent = onPlayerIntent,
            modifier = modifier,
        )

        AppTab.Tags -> MusicTagsTab(
            platform = platform,
            state = musicTagsState,
            effects = musicTagsEffects,
            onMusicTagsIntent = onMusicTagsIntent,
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
    favoritesState: FavoritesState,
    onLibraryIntent: (LibraryIntent) -> Unit,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var sourceFilterMenuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 42.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                Box {
                    OutlinedButton(onClick = { sourceFilterMenuExpanded = true }) {
                        Icon(Icons.Rounded.Tune, null)
                        Spacer(Modifier.width(8.dp))
                        Text(librarySourceFilterButtonLabel(state.selectedSourceFilter))
                    }
                    DropdownMenu(
                        expanded = sourceFilterMenuExpanded,
                        onDismissRequest = { sourceFilterMenuExpanded = false },
                    ) {
                        state.availableSourceFilters.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(librarySourceFilterMenuLabel(filter)) },
                                onClick = {
                                    sourceFilterMenuExpanded = false
                                    onLibraryIntent(LibraryIntent.SourceFilterChanged(filter))
                                },
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(title = "歌曲", value = state.filteredTracks.size.toString(), icon = Icons.Rounded.LibraryMusic, modifier = Modifier.weight(1f))
                    StatCard(title = "专辑", value = state.visibleAlbumCount.toString(), icon = Icons.Rounded.Album, modifier = Modifier.weight(1f))
                    StatCard(title = "艺人", value = state.visibleArtistCount.toString(), icon = Icons.Rounded.Tune, modifier = Modifier.weight(1f))
                }
            }
            favoritesState.message?.let { message ->
                item {
                    BannerCard(
                        message = message,
                        onDismiss = { onFavoritesIntent(FavoritesIntent.ClearMessage) },
                    )
                }
            }
            item {
                SectionTitle(title = "你的曲库", subtitle = "支持本地文件夹、Samba、WebDAV、Navidrome 与自定义歌词联动。")
            }
            if (state.filteredTracks.isEmpty()) {
                item {
                    when {
                        state.tracks.isEmpty() -> EmptyStateCard(
                            title = "曲库还是空的",
                            body = "先到“来源”页导入本地文件夹、Samba、WebDAV 或 Navidrome，扫描完成后会出现在这里。",
                        )

                        state.selectedSourceFilter != LibrarySourceFilter.ALL -> EmptyStateCard(
                            title = "当前来源下没有歌曲",
                            body = "试试切回“全部来源”、更换过滤项，或调整搜索词。",
                        )

                        else -> EmptyStateCard(
                            title = "没有匹配的歌曲",
                            body = "试试调整搜索词，或切换来源过滤。",
                        )
                    }
                }
            } else {
                itemsIndexed(state.filteredTracks, key = { _, item -> item.id }) { index, track ->
                    TrackRow(
                        track = track,
                        index = index,
                        isFavorite = track.id in favoritesState.favoriteTrackIds,
                        onToggleFavorite = { onFavoritesIntent(FavoritesIntent.ToggleFavorite(track)) },
                        onClick = {
                            onPlayerIntent(PlayerIntent.PlayTracks(state.filteredTracks, index))
                        },
                    )
                }
            }
        }
        LibraryFastScrollbar(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 8.dp, top = 20.dp, bottom = 20.dp),
        )
    }
}

@Composable
private fun FavoritesTab(
    state: FavoritesState,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var sourceFilterMenuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 42.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onFavoritesIntent(FavoritesIntent.SearchChanged(it)) },
                    label = { Text("搜索喜欢的歌曲 / 艺人 / 专辑") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                )
            }
            item {
                Box {
                    OutlinedButton(onClick = { sourceFilterMenuExpanded = true }) {
                        Icon(Icons.Rounded.Tune, null)
                        Spacer(Modifier.width(8.dp))
                        Text(librarySourceFilterButtonLabel(state.selectedSourceFilter))
                    }
                    DropdownMenu(
                        expanded = sourceFilterMenuExpanded,
                        onDismissRequest = { sourceFilterMenuExpanded = false },
                    ) {
                        state.availableSourceFilters.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(librarySourceFilterMenuLabel(filter)) },
                                onClick = {
                                    sourceFilterMenuExpanded = false
                                    onFavoritesIntent(FavoritesIntent.SourceFilterChanged(filter))
                                },
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(title = "歌曲", value = state.filteredTracks.size.toString(), icon = Icons.Rounded.Favorite, modifier = Modifier.weight(1f))
                    StatCard(title = "专辑", value = state.visibleAlbumCount.toString(), icon = Icons.Rounded.Album, modifier = Modifier.weight(1f))
                    StatCard(title = "艺人", value = state.visibleArtistCount.toString(), icon = Icons.Rounded.Tune, modifier = Modifier.weight(1f))
                }
            }
            state.message?.let { message ->
                item {
                    BannerCard(
                        message = message,
                        onDismiss = { onFavoritesIntent(FavoritesIntent.ClearMessage) },
                    )
                }
            }
            item {
                SectionTitle(title = "我的喜欢", subtitle = "本地来源保存在本地，Navidrome 来源会和服务器收藏双向同步。")
            }
            if (state.filteredTracks.isEmpty()) {
                item {
                    when {
                        state.tracks.isEmpty() -> EmptyStateCard(
                            title = "还没有喜欢的歌曲",
                            body = "在曲库或播放器里点亮心形后，喜欢的歌曲会出现在这里。",
                        )

                        state.selectedSourceFilter != LibrarySourceFilter.ALL -> EmptyStateCard(
                            title = "当前来源下没有喜欢的歌曲",
                            body = "试试切回“全部来源”、更换过滤项，或去其他来源里添加喜欢。",
                        )

                        else -> EmptyStateCard(
                            title = "没有匹配的喜欢歌曲",
                            body = "试试调整搜索词，或切换来源过滤。",
                        )
                    }
                }
            } else {
                itemsIndexed(state.filteredTracks, key = { _, item -> item.id }) { index, track ->
                    TrackRow(
                        track = track,
                        index = index,
                        isFavorite = true,
                        onToggleFavorite = { onFavoritesIntent(FavoritesIntent.ToggleFavorite(track)) },
                        onClick = {
                            onPlayerIntent(PlayerIntent.PlayTracks(state.filteredTracks, index))
                        },
                    )
                }
            }
        }
        LibraryFastScrollbar(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(end = 8.dp, top = 20.dp, bottom = 20.dp),
        )
    }
}

private fun librarySourceFilterButtonLabel(filter: LibrarySourceFilter): String {
    return when (filter) {
        LibrarySourceFilter.ALL -> "全部来源"
        LibrarySourceFilter.LOCAL_FOLDER -> "本地文件夹"
        LibrarySourceFilter.SAMBA -> "Samba"
        LibrarySourceFilter.WEBDAV -> "WebDAV"
        LibrarySourceFilter.NAVIDROME -> "Navidrome"
    }
}

private fun librarySourceFilterMenuLabel(filter: LibrarySourceFilter): String {
    return when (filter) {
        LibrarySourceFilter.ALL -> "全部"
        else -> librarySourceFilterButtonLabel(filter)
    }
}

@Composable
private fun LibraryFastScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    val totalItemsCount by remember(listState) {
        derivedStateOf { listState.layoutInfo.totalItemsCount }
    }
    val visibleItemsInfo by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo }
    }
    if (totalItemsCount <= 0 || totalItemsCount <= visibleItemsInfo.size) return
    val visibleFraction by remember(totalItemsCount, visibleItemsInfo) {
        derivedStateOf {
            (visibleItemsInfo.size.toFloat() / totalItemsCount.toFloat()).coerceIn(0.12f, 0.45f)
        }
    }
    val scrollFraction by remember(listState, totalItemsCount, visibleItemsInfo) {
        derivedStateOf {
            if (totalItemsCount <= 1) {
                0f
            } else {
                val firstVisibleSize = visibleItemsInfo.firstOrNull()?.size?.takeIf { it > 0 } ?: 1
                val exactIndex = listState.firstVisibleItemIndex + (listState.firstVisibleItemScrollOffset / firstVisibleSize.toFloat())
                (exactIndex / (totalItemsCount - 1).toFloat()).coerceIn(0f, 1f)
            }
        }
    }
    val thumbHeightPx = trackSize.height * visibleFraction
    val thumbOffsetPx = (trackSize.height - thumbHeightPx).coerceAtLeast(0f) * scrollFraction
    val thumbHeightDp = with(density) { thumbHeightPx.toDp() }
    val thumbOffsetDp = with(density) { thumbOffsetPx.toDp() }

    fun scrollToFraction(y: Float) {
        if (trackSize.height <= 0 || totalItemsCount <= 1) return
        val fraction = (y / trackSize.height.toFloat()).coerceIn(0f, 1f)
        val targetIndex = (fraction * (totalItemsCount - 1)).roundToInt()
        coroutineScope.launch {
            listState.scrollToItem(targetIndex)
        }
    }

    Box(
        modifier = modifier
            .width(18.dp)
            .onSizeChanged { trackSize = it }
            .pointerInput(totalItemsCount, trackSize) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> scrollToFraction(offset.y) },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        scrollToFraction(change.position.y)
                    },
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.10f)),
        )
        Box(
            modifier = Modifier
                .offset(y = thumbOffsetDp)
                .height(thumbHeightDp)
                .width(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.82f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)), RoundedCornerShape(999.dp)),
        )
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
        SectionTitle(title = "导入来源", subtitle = "本地文件夹原地索引，Samba、WebDAV 与 Navidrome 作为远程音乐库。")
        state.message?.let { message ->
            BannerCard(message = message, onDismiss = { onImportIntent(ImportIntent.ClearMessage) })
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
                Text("Navidrome", fontWeight = FontWeight.Bold)
                if (!state.capabilities.supportsNavidromeImport) {
                    Text("当前平台暂未开放应用内 Navidrome 导入。")
                }
                OutlinedTextField(
                    value = state.navidromeLabel,
                    onValueChange = { onImportIntent(ImportIntent.NavidromeLabelChanged(it)) },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                )
                OutlinedTextField(
                    value = state.navidromeBaseUrl,
                    onValueChange = { onImportIntent(ImportIntent.NavidromeBaseUrlChanged(it)) },
                    label = { Text("服务器地址") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.navidromeUsername,
                        onValueChange = { onImportIntent(ImportIntent.NavidromeUsernameChanged(it)) },
                        label = { Text("用户名") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                    )
                    OutlinedTextField(
                        value = state.navidromePassword,
                        onValueChange = { onImportIntent(ImportIntent.NavidromePasswordChanged(it)) },
                        label = { Text("密码") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                    )
                }
                Button(
                    onClick = { onImportIntent(ImportIntent.AddNavidromeSource) },
                    enabled = state.capabilities.supportsNavidromeImport && !state.isWorking,
                ) {
                    Icon(Icons.Rounded.CloudSync, null)
                    Spacer(Modifier.width(8.dp))
                    Text("连接并同步")
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
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        state.message?.let { message ->
            LaunchedEffect(message) {
                delay(2_500)
                onSettingsIntent(SettingsIntent.ClearMessage)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle(title = "歌词 API", subtitle = "声明式适配 JSON / XML / LRC / 纯文本返回。")
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
                    Text("LrcAPI", fontWeight = FontWeight.Bold)
                    Text(
                        "专用入口只维护请求地址，保存后会自动生成保留的 Direct 歌词源。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (state.hasLrcApiSource) "已保存到歌词源列表" else "尚未配置",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                        if (state.hasLrcApiSource) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Direct") },
                                leadingIcon = { Icon(Icons.Rounded.CloudSync, null) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.lrcApiUrl,
                        onValueChange = { onSettingsIntent(SettingsIntent.LrcApiUrlChanged(it)) },
                        label = { Text("LrcAPI 请求地址") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onSettingsIntent(SettingsIntent.SaveLrcApi) }) {
                            Text("保存 LrcAPI")
                        }
                        OutlinedButton(
                            onClick = { onSettingsIntent(SettingsIntent.ClearLrcApi) },
                            enabled = state.hasLrcApiSource,
                        ) {
                            Text("清除 LrcAPI")
                        }
                    }
                }
            }
            ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Musicmatch", fontWeight = FontWeight.Bold)
                    Text(
                        "专用入口只维护 usertoken，保存后会自动生成保留的 Workflow 歌词源。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (state.hasMusicmatchSource) "已保存到歌词源列表" else "尚未配置",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                        if (state.hasMusicmatchSource) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Workflow") },
                                leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.musicmatchUserToken,
                        onValueChange = { onSettingsIntent(SettingsIntent.MusicmatchUserTokenChanged(it)) },
                        label = { Text("Musicmatch usertoken") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onSettingsIntent(SettingsIntent.SaveMusicmatch) }) {
                            Text("保存 Musicmatch")
                        }
                        if (state.hasMusicmatchSource || state.musicmatchUserToken.isNotBlank()) {
                            OutlinedButton(onClick = { onSettingsIntent(SettingsIntent.ClearMusicmatch) }) {
                                Text("清除 Musicmatch")
                            }
                        }
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
                        Button(onClick = { onSettingsIntent(if (state.editingId != null) SettingsIntent.Save else SettingsIntent.CreateNew) }) {
                            Text(if (state.editingId != null) "保存" else "新建")
                        }
                        OutlinedButton(onClick = { onSettingsIntent(if (state.editingId != null) SettingsIntent.CreateNew else SettingsIntent.SelectConfig(null)) }) {
                            Text(if (state.editingId != null) "新建" else "清空")
                        }
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
                    Text("Workflow JSON", fontWeight = FontWeight.Bold)
                    Text(
                        "用于新建或编辑多阶段歌词源，支持搜歌 -> 选歌 -> 拉歌词。当前仍直接编辑原始 JSON。",
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
                        Button(onClick = { onSettingsIntent(if (state.editingWorkflowId != null) SettingsIntent.ImportWorkflow else SettingsIntent.CreateNewWorkflow) }) {
                            Text(if (state.editingWorkflowId != null) "保存 Workflow" else "新建 Workflow")
                        }
                        if (state.editingWorkflowId != null || state.workflowJsonInput.isNotBlank()) {
                            OutlinedButton(onClick = { onSettingsIntent(if (state.editingWorkflowId != null) SettingsIntent.CreateNewWorkflow else SettingsIntent.ViewWorkflow(null)) }) {
                                Text(if (state.editingWorkflowId != null) "新建 Workflow" else "清空编辑")
                            }
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
                                is LyricsSourceConfig -> {
                                    if (top.iwesley.lyn.music.domain.isManagedLrcApiSource(source)) {
                                        onSettingsIntent(SettingsIntent.SelectLrcApi(source))
                                    } else {
                                        onSettingsIntent(SettingsIntent.SelectConfig(source))
                                    }
                                }
                                is top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig -> {
                                    if (top.iwesley.lyn.music.domain.isManagedMusicmatchSource(source)) {
                                        onSettingsIntent(SettingsIntent.SelectMusicmatch(source))
                                    } else {
                                        onSettingsIntent(SettingsIntent.ViewWorkflow(source))
                                    }
                                }
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
        state.message?.let { message ->
            ToastCard(
                message = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun MiniPlayerBar(
    state: PlayerState,
    onPlayerIntent: (PlayerIntent) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    compact: Boolean = false,
) {
    if (state.snapshot.currentTrack == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 0.dp else 18.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.42f))
            .border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(28.dp),
            )
            .clickable { onPlayerIntent(PlayerIntent.ExpandedChanged(true)) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        VinylPlaceholder(
            vinylSize = 50.dp,
            artworkLocator = state.snapshot.currentDisplayArtworkLocator,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(state.snapshot.currentDisplayTitle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state.snapshot.currentDisplayArtistName ?: "未知艺人", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FavoriteToggleButton(
            isFavorite = isFavorite,
            onClick = onToggleFavorite,
        )
        QueueToggleButton(onClick = onOpenQueue)
        IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipPrevious) }) { Icon(Icons.Rounded.SkipPrevious, null) }
        IconButton(onClick = { onPlayerIntent(PlayerIntent.TogglePlayPause) }) {
            Icon(if (state.snapshot.isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle, null, modifier = Modifier.size(34.dp))
        }
        IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipNext) }) { Icon(Icons.Rounded.SkipNext, null) }
    }
}

@Composable
private fun PlayerOverlay(
    platform: PlatformDescriptor,
    state: PlayerState,
    onPlayerIntent: (PlayerIntent) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val track = state.snapshot.currentTrack ?: return
    val defaultBackgroundColor = Color(0xFF232325)
    val playbackStatusColor = Color.White.copy(alpha = 0.6f)
    val artworkBitmap = rememberPlatformArtworkBitmap(state.snapshot.currentDisplayArtworkLocator)
    val artworkPalette = rememberVinylArtworkPalette(
        artworkBitmap = artworkBitmap,
        enabled = true,
    )
    val backgroundTopTint by animateColorAsState(
        targetValue = artworkPalette?.innerGlowColor?.copy(alpha = 0.22f) ?: Color.Transparent,
        label = "player-background-top-tint",
    )
    val backgroundMidTint by animateColorAsState(
        targetValue = artworkPalette?.glowColor?.copy(alpha = 0.18f) ?: Color.Transparent,
        label = "player-background-mid-tint",
    )
    val backgroundAccentTint by animateColorAsState(
        targetValue = artworkPalette?.rimColor?.copy(alpha = 0.12f) ?: Color.Transparent,
        label = "player-background-accent-tint",
    )
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = defaultBackgroundColor,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                backgroundTopTint,
                                backgroundMidTint,
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                backgroundAccentTint,
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            val wide = maxWidth >= 980.dp
            val useTapToRevealLyrics =
                isMobilePlaybackPlatform(platform) &&
                    (maxWidth < 820.dp || maxHeight < 860.dp)
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
                    IconButton(onClick = { onPlayerIntent(PlayerIntent.ExpandedChanged(false)) }) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "收起播放页",
                            tint = Color.White.copy(alpha = 0.92f),
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            playbackModeIcon(state.snapshot.mode),
                            null,
                            tint = playbackStatusColor,
                        )
                        Text(
                            modeLabel(state.snapshot.mode),
                            color = playbackStatusColor,
                        )
                    }
                }
                if (useTapToRevealLyrics) {
                    MobilePlayerPrimaryPane(
                        state = state,
                        track = track,
                        onPlayerIntent = onPlayerIntent,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                } else if (wide) {
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
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onOpenQueue = onOpenQueue,
                    onPlayerIntent = onPlayerIntent,
                )
            }
            if (state.isLyricsShareVisible) {
                LyricsShareOverlay(
                    platform = platform,
                    state = state,
                    onPlayerIntent = onPlayerIntent,
                    modifier = Modifier.fillMaxSize(),
                )
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
}

@Composable
private fun MobilePlayerPrimaryPane(
    state: PlayerState,
    track: Track,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lyricsVisible by rememberSaveable(track.id) { mutableStateOf(false) }

    if (lyricsVisible) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { lyricsVisible = false }) {
                    Text("回到唱片")
                }
            }
            PlayerLyricsPane(
                state = state,
                track = track,
                onPlayerIntent = onPlayerIntent,
                modifier = Modifier.weight(1f),
                compact = true,
            )
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(30.dp))
            .clickable { lyricsVisible = true },
    ) {
        PlayerInfoPane(
            snapshot = state.snapshot,
            track = track,
            modifier = Modifier.fillMaxSize(),
            compact = true,
        )
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.34f)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = state.snapshot.currentDisplayTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.96f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.snapshot.currentDisplayArtistName ?: "未知艺人",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AssistChip(
                    onClick = { lyricsVisible = true },
                    label = { Text(if (state.isLyricsLoading) "正在准备歌词" else "点按唱片区查看歌词") },
                    leadingIcon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
                )
            }
        }
    }
}

private fun isMobilePlaybackPlatform(platform: PlatformDescriptor): Boolean {
    return platform.name == "Android" || platform.name == "iPhone / iPad"
}

@Composable
private fun QueueDrawer(
    state: PlayerState,
    compact: Boolean,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.snapshot.currentTrack == null) return
    val listState = rememberLazyListState()
    LaunchedEffect(state.isQueueVisible, state.snapshot.currentIndex, state.snapshot.queue.size) {
        if (state.isQueueVisible && state.snapshot.currentIndex in state.snapshot.queue.indices) {
            listState.scrollToItem((state.snapshot.currentIndex - 2).coerceAtLeast(0))
        }
    }
    AnimatedVisibility(
        visible = state.isQueueVisible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(durationMillis = 220)),
        exit = fadeOut(animationSpec = tween(durationMillis = 180)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(false)) },
            )
            Card(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(if (compact) 340.dp else 420.dp),
                shape = RoundedCornerShape(topStart = 30.dp, bottomStart = 30.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("播放队列", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            Text(
                                "${state.snapshot.queue.size} 首 · ${modeLabel(state.snapshot.mode)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onPlayerIntent(PlayerIntent.QueueVisibilityChanged(false)) }) {
                            Text("关闭")
                        }
                    }
                    if (state.snapshot.queue.isEmpty()) {
                        EmptyStateCard(
                            title = "当前没有播放队列",
                            body = "从曲库或喜欢页播放歌曲后，这里会显示当前队列。",
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            itemsIndexed(state.snapshot.queue, key = { _, item -> item.id }) { index, track ->
                                QueueTrackRow(
                                    track = track,
                                    index = index,
                                    isCurrent = index == state.snapshot.currentIndex,
                                    isPlaying = index == state.snapshot.currentIndex && state.snapshot.isPlaying,
                                    onClick = { onPlayerIntent(PlayerIntent.PlayQueueIndex(index)) },
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
        VinylPlaceholder(
            vinylSize = if (compact) 250.dp else 420.dp,
            artworkLocator = snapshot.currentDisplayArtworkLocator,
            spinning = snapshot.isPlaying,
            enableArtworkTint = true,
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
    val lyricsPrimaryTextColor = Color.White
    val lyricsSecondaryTextColor = Color.White.copy(alpha = 0.6f)
    val lyricsButtonBorder = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f))
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
                    color = lyricsPrimaryTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "专辑：${state.snapshot.currentDisplayAlbumTitle ?: "本地曲目"}    歌手：${state.snapshot.currentDisplayArtistName ?: "未知艺人"}    来源：${track.sourceId.substringBefore('-').uppercase()}",
                    color = lyricsSecondaryTextColor,
                    maxLines = if (compact) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.relativePath,
                    color = lyricsSecondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "格式：${trackDisplayFormat(track)}    大小：${formatTrackSize(track.sizeBytes)}",
                    color = lyricsSecondaryTextColor,
                    maxLines = if (compact) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { onPlayerIntent(PlayerIntent.OpenLyricsShare) },
                        enabled = state.lyrics != null && !state.isLyricsLoading,
                        shape = RoundedCornerShape(18.dp),
                        border = lyricsButtonBorder,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = lyricsSecondaryTextColor,
                            disabledContentColor = lyricsSecondaryTextColor.copy(alpha = 0.45f),
                        ),
                    ) {
                        Text("分享歌词")
                    }
                    OutlinedButton(
                        onClick = { onPlayerIntent(PlayerIntent.OpenManualLyricsSearch) },
                        shape = RoundedCornerShape(18.dp),
                        border = lyricsButtonBorder,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = lyricsSecondaryTextColor,
                            disabledContentColor = lyricsSecondaryTextColor.copy(alpha = 0.45f),
                        ),
                    ) {
                        Icon(
                            Icons.Rounded.Tune,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = lyricsSecondaryTextColor,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("手动搜索")
                    }
                }
            }
            val lyrics = state.lyrics
            if (lyrics == null) {
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
                        itemsIndexed(lyrics.lines) { index, line ->
                            val distance = if (state.highlightedLineIndex >= 0) {
                                abs(index - state.highlightedLineIndex)
                            } else {
                                Int.MAX_VALUE
                            }
                            val targetAlpha = when {
                                state.highlightedLineIndex < 0 -> 0.6f
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
                                    lyricsPrimaryTextColor
                                } else {
                                    lyricsPrimaryTextColor
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
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.68f))
                .clickable { onPlayerIntent(PlayerIntent.DismissManualLyricsSearch) },
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
                                "手动搜索歌词",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryTextColor,
                            )
                            Text(
                                buildString {
                                    append(state.snapshot.currentDisplayTitle.ifBlank { "当前歌曲" })
                                    state.snapshot.currentDisplayArtistName?.takeIf { it.isNotBlank() }?.let {
                                        append(" · ")
                                        append(it)
                                    }
                                },
                                color = secondaryTextColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { onPlayerIntent(PlayerIntent.DismissManualLyricsSearch) }) {
                            Text("关闭", color = primaryTextColor)
                        }
                    }
                    if (wideLayout) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            ManualLyricsSearchFormPane(
                                state = state,
                                onPlayerIntent = onPlayerIntent,
                                modifier = Modifier
                                    .weight(0.42f)
                                    .fillMaxHeight(),
                            )
                            ManualLyricsSearchResultsPane(
                                state = state,
                                onPlayerIntent = onPlayerIntent,
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
                            ManualLyricsSearchFormPane(
                                state = state,
                                onPlayerIntent = onPlayerIntent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.42f),
                            )
                            ManualLyricsSearchResultsPane(
                                state = state,
                                onPlayerIntent = onPlayerIntent,
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
                val stackedFields = maxWidth < 560.dp
                val buttonSpacing = if (stackedFields) 8.dp else 10.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedTextField(
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
                            OutlinedTextField(
                                value = state.manualLyricsArtistName,
                                onValueChange = { onPlayerIntent(PlayerIntent.ManualLyricsArtistChanged(it)) },
                                label = { Text("歌手") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                singleLine = true,
                                colors = textFieldColors,
                            )
                            OutlinedTextField(
                                value = state.manualLyricsAlbumTitle,
                                onValueChange = { onPlayerIntent(PlayerIntent.ManualLyricsAlbumChanged(it)) },
                                label = { Text("专辑") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                singleLine = true,
                                colors = textFieldColors,
                            )
                        }
                    } else {
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
                            Text(if (state.isManualLyricsSearchLoading) "搜索中..." else "搜索", maxLines = 1)
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
                            Text("直接歌词结果", color = primaryTextColor, fontWeight = FontWeight.SemiBold)
                            state.manualLyricsResults.forEach { candidate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { onPlayerIntent(PlayerIntent.ApplyManualLyricsCandidate(candidate)) }
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
                            Text("Workflow 歌曲候选", color = primaryTextColor, fontWeight = FontWeight.SemiBold)
                            state.manualWorkflowSongResults.forEach { candidate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { onPlayerIntent(PlayerIntent.ApplyWorkflowSongCandidate(candidate)) }
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
private fun LyricsShareOverlay(
    platform: PlatformDescriptor,
    state: PlayerState,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lyrics = state.lyrics ?: return
    val previewBitmap = rememberPlatformImageBitmap(state.sharePreviewBytes)
    val artworkBitmap = rememberPlatformArtworkBitmap(state.snapshot.currentDisplayArtworkLocator)
    val artworkTintTheme = rememberVinylArtworkPalette(
        artworkBitmap = artworkBitmap,
        enabled = state.selectedLyricsShareTemplate == LyricsShareTemplate.ARTWORK_TINT,
    )?.toArtworkTintTheme()
    val primaryTextColor = Color.White.copy(alpha = 0.96f)
    val secondaryTextColor = Color.White.copy(alpha = 0.74f)
    val bannerMessage = state.sharePreviewError ?: state.shareMessage
    val exportActionsEnabled = state.selectedLyricsLineIndices.isNotEmpty() && !state.isShareSaving && !state.isShareCopying
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.68f))
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF201B19)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
            ) {
                val wideLayout = maxWidth >= 980.dp
                val narrowActions = maxWidth < 760.dp
                val mobileActions = isMobilePlaybackPlatform(platform)
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
                                "分享歌词",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryTextColor,
                            )
                            Text(
                                buildString {
                                    append(state.snapshot.currentDisplayTitle.ifBlank { "当前歌曲" })
                                    state.snapshot.currentDisplayArtistName?.takeIf { it.isNotBlank() }?.let {
                                        append(" · ")
                                        append(it)
                                    }
                                },
                                color = secondaryTextColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
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
                            TextButton(onClick = { onPlayerIntent(PlayerIntent.DismissLyricsShare) }) {
                                Text("关闭", color = primaryTextColor)
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
                                lyricsLines = lyrics.lines.map { it.text },
                                selectedIndices = state.selectedLyricsLineIndices,
                                onToggle = { onPlayerIntent(PlayerIntent.ToggleLyricsLineSelection(it)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                            LyricsSharePreviewPane(
                                state = state,
                                previewBitmap = previewBitmap,
                                artworkTintTheme = artworkTintTheme,
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.48f),
                            )
                            LyricsShareSelectionPane(
                                lyricsLines = lyrics.lines.map { it.text },
                                selectedIndices = state.selectedLyricsLineIndices,
                                onToggle = { onPlayerIntent(PlayerIntent.ToggleLyricsLineSelection(it)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.52f),
                            )
                        }
                    }
                    if (mobileActions) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(if (narrowActions) 8.dp else 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { onPlayerIntent(PlayerIntent.ClearLyricsSelection) },
                                enabled = state.selectedLyricsLineIndices.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryTextColor),
                                contentPadding = PaddingValues(
                                    horizontal = if (narrowActions) 8.dp else 16.dp,
                                    vertical = 12.dp,
                                ),
                            ) {
                                Text("清空", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { onPlayerIntent(PlayerIntent.CopyLyricsShareImage) },
                                enabled = exportActionsEnabled,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryTextColor),
                                contentPadding = PaddingValues(
                                    horizontal = if (narrowActions) 8.dp else 16.dp,
                                    vertical = 12.dp,
                                ),
                            ) {
                                Text(if (state.isShareCopying) "复制中..." else "复制图片", maxLines = 1)
                            }
                            Button(
                                onClick = { onPlayerIntent(PlayerIntent.SaveLyricsShareImage) },
                                enabled = exportActionsEnabled,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEEE0C8),
                                    contentColor = Color(0xFF3C2E24),
                                    disabledContainerColor = Color.White.copy(alpha = 0.12f),
                                    disabledContentColor = secondaryTextColor,
                                ),
                                contentPadding = PaddingValues(
                                    horizontal = if (narrowActions) 8.dp else 16.dp,
                                    vertical = 12.dp,
                                ),
                            ) {
                                Text(if (state.isShareSaving) "保存中..." else "保存到本地", maxLines = 1)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { onPlayerIntent(PlayerIntent.ClearLyricsSelection) },
                                enabled = state.selectedLyricsLineIndices.isNotEmpty(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryTextColor),
                            ) {
                                Text("清空")
                            }
                            Spacer(Modifier.width(10.dp))
                            OutlinedButton(
                                onClick = { onPlayerIntent(PlayerIntent.CopyLyricsShareImage) },
                                enabled = exportActionsEnabled,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryTextColor),
                            ) {
                                Text(if (state.isShareCopying) "复制中..." else "复制图片")
                            }
                            Spacer(Modifier.width(10.dp))
                            Button(
                                onClick = { onPlayerIntent(PlayerIntent.SaveLyricsShareImage) },
                                enabled = exportActionsEnabled,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEEE0C8),
                                    contentColor = Color(0xFF3C2E24),
                                    disabledContainerColor = Color.White.copy(alpha = 0.12f),
                                    disabledContentColor = secondaryTextColor,
                                ),
                            ) {
                                Text(if (state.isShareSaving) "保存中..." else "保存到本地")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsShareSelectionPane(
    lyricsLines: List<String>,
    selectedIndices: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(
            title = "选句",
            subtitle = "点选任意多句歌词，空白行不会加入分享图。",
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
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
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
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
                    containerColor = if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent,
                    contentColor = if (selected) Color.White else Color.White.copy(alpha = 0.68f),
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
    val normalized = text.trim()
    val enabled = normalized.isNotEmpty()
    val containerColor = when {
        !enabled -> Color.White.copy(alpha = 0.04f)
        selected -> Color(0xFFF4E4BF)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val contentColor = when {
        !enabled -> Color.White.copy(alpha = 0.28f)
        selected -> Color(0xFF3C2E24)
        else -> Color.White.copy(alpha = 0.9f)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, if (selected) Color(0xFFD0B57C) else Color.White.copy(alpha = 0.05f)),
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
    modifier: Modifier = Modifier,
) {
    val shareCardModel = state.shareCardModel
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(
            title = "预览",
            subtitle = when {
                state.isShareRendering && previewBitmap != null -> "正在更新预览，旧图会暂时保留。"
                state.isShareRendering -> "正在生成分享图片。"
                state.selectedLyricsLineIndices.isEmpty() -> "请先选择至少一句歌词。"
                state.hasFreshSharePreview -> "当前预览会用于复制和保存。"
                else -> "选句后会自动刷新预览。"
            },
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0E5D5)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
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
                if (state.isShareRendering && shareCardModel != null) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.56f)),
                    ) {
                        Text(
                            text = "更新预览中",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsShareNoteCard(
    model: LyricsShareCardModel,
    modifier: Modifier = Modifier,
) {
    val artworkBitmap = rememberPlatformArtworkBitmap(model.artworkLocator)
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
                )
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = LyricsShareCardSpec.BRAND_TEXT,
                        style = MaterialTheme.typography.labelLarge,
                        color = secondaryTextColor.copy(alpha = 0.85f),
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
    val backgroundColor = composeColorFromArgb(LyricsShareArtworkTintSpec.DEFAULT_BACKGROUND_ARGB)
    val topTint = composeColorFromArgb(
        argbWithAlpha(
            artworkTintTheme?.innerGlowColorArgb ?: LyricsShareArtworkTintSpec.DEFAULT_BACKGROUND_ARGB,
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

@Composable
private fun PlayerBottomControls(
    snapshot: PlaybackSnapshot,
    track: Track,
    wide: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val favoriteTint = if (isFavorite) Color(0xFFE5484D) else Color.White.copy(alpha = 0.96f)
    val modeButtonSize = 42.dp
    val modeIconSize = 22.dp
    val skipButtonSize = 45.dp
    val skipIconSize = 27.dp
    val playButtonSize = 60.dp
    val playIconSize = 42.dp
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
                                IconButton(onClick = { onPlayerIntent(PlayerIntent.CycleMode) }, modifier = Modifier.size(modeButtonSize)) {
                                    Icon(
                                        playbackModeIcon(snapshot.mode),
                                        null,
                                        modifier = Modifier.size(modeIconSize),
                                        tint = Color.White.copy(alpha = 0.92f),
                                    )
                                }
                                IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipPrevious) }, modifier = Modifier.size(skipButtonSize)) {
                                    Icon(Icons.Rounded.SkipPrevious, null, modifier = Modifier.size(skipIconSize), tint = Color.White.copy(alpha = 0.92f))
                                }
                                IconButton(onClick = { onPlayerIntent(PlayerIntent.TogglePlayPause) }, modifier = Modifier.size(playButtonSize)) {
                                    Icon(
                                        if (snapshot.isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                                        null,
                                        modifier = Modifier.size(playIconSize),
                                        tint = Color.White.copy(alpha = 0.96f),
                                    )
                                }
                                IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipNext) }, modifier = Modifier.size(skipButtonSize)) {
                                    Icon(Icons.Rounded.SkipNext, null, modifier = Modifier.size(skipIconSize), tint = Color.White.copy(alpha = 0.92f))
                                }
                            }
                            Row(
                                modifier = Modifier.weight(0.30f),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                QueueToggleButton(
                                    onClick = onOpenQueue,
                                    tint = Color.White.copy(alpha = 0.96f),
                                )
                                FavoriteToggleButton(
                                    isFavorite = isFavorite,
                                    onClick = onToggleFavorite,
                                    tint = favoriteTint,
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    PlaybackVolume(snapshot, onPlayerIntent, sliderWidthFraction = 0.5f)
                                }
                            }
                        }
                    } else {
                        PlaybackVolume(snapshot, onPlayerIntent)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { onPlayerIntent(PlayerIntent.CycleMode) }, modifier = Modifier.size(modeButtonSize)) {
                                Icon(
                                    playbackModeIcon(snapshot.mode),
                                    null,
                                    modifier = Modifier.size(modeIconSize),
                                    tint = Color.White.copy(alpha = 0.92f),
                                )
                            }
                            IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipPrevious) }, modifier = Modifier.size(skipButtonSize)) {
                                Icon(Icons.Rounded.SkipPrevious, null, modifier = Modifier.size(skipIconSize), tint = Color.White.copy(alpha = 0.92f))
                            }
                            IconButton(onClick = { onPlayerIntent(PlayerIntent.TogglePlayPause) }, modifier = Modifier.size(playButtonSize)) {
                                Icon(
                                    if (snapshot.isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                                    null,
                                    modifier = Modifier.size(playIconSize),
                                    tint = Color.White.copy(alpha = 0.96f),
                                )
                            }
                            IconButton(onClick = { onPlayerIntent(PlayerIntent.SkipNext) }, modifier = Modifier.size(skipButtonSize)) {
                                Icon(Icons.Rounded.SkipNext, null, modifier = Modifier.size(skipIconSize), tint = Color.White.copy(alpha = 0.92f))
                            }
                            QueueToggleButton(
                                onClick = onOpenQueue,
                                tint = Color.White.copy(alpha = 0.96f),
                            )
                            FavoriteToggleButton(
                                isFavorite = isFavorite,
                                onClick = onToggleFavorite,
                                tint = favoriteTint,
                            )
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
private fun FavoriteToggleButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (isFavorite) "取消喜欢" else "标记为喜欢",
            tint = tint,
        )
    }
}

@Composable
private fun QueueToggleButton(
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Rounded.QueueMusic,
            contentDescription = "播放队列",
            tint = tint,
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
private fun QueueTrackRow(
    track: Track,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
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
            if (isCurrent) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(
                    (index + 1).toString().padStart(2, '0'),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    buildString {
                        append(track.artistName ?: "未知艺人")
                        if (isCurrent) {
                            append(if (isPlaying) " · 正在播放" else " · 当前歌曲")
                        }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(formatDuration(track.durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 42.dp)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f)),
        )
    }
}

@Composable
private fun TrackRow(
    track: Track,
    index: Int,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
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
            Text((index + 1).toString().padStart(2, '0'), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            TrackArtworkThumbnail(artworkLocator = track.artworkLocator)
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(track.artistName ?: "未知艺人", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FavoriteToggleButton(
                isFavorite = isFavorite,
                onClick = onToggleFavorite,
            )
            Text(formatDuration(track.durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 88.dp)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f)),
        )
    }
}

@Composable
private fun TrackArtworkThumbnail(
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
                            top.iwesley.lyn.music.core.model.ImportSourceType.NAVIDROME -> state.source.rootReference
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

@Composable
private fun PriorityBadge(
    priority: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
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
private fun ToastCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.widthIn(max = 420.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1F1A17).copy(alpha = 0.94f),
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
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
    vinylSize: Dp,
    artworkLocator: String? = null,
    spinning: Boolean = false,
    enableArtworkTint: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val artworkBitmap = rememberPlatformArtworkBitmap(artworkLocator)
    val palette = rememberVinylArtworkPalette(
        artworkBitmap = artworkBitmap,
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
                val ringStart = radius * 0.58f
                val ringEnd = radius * 0.94f
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
                drawCircle(
                    color = animatedRimColor.copy(alpha = if (enableArtworkTint) 0.55f else 0.22f),
                    radius = radius - 2f,
                    style = Stroke(width = 3.5f),
                )
            }
            Box(
                modifier = Modifier
                    .size(vinylSize * 0.62f)
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
                    .size(vinylSize * 0.58f)
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
                            .size(vinylSize / 4)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
                    )
                }
            }
        }
    }
}

private data class VinylArtworkPalette(
    val rimColor: Color,
    val glowColor: Color,
    val innerGlowColor: Color,
)

private fun VinylArtworkPalette.toArtworkTintTheme(): ArtworkTintTheme {
    return ArtworkTintTheme(
        rimColorArgb = rimColor.toArgbInt(),
        glowColorArgb = glowColor.toArgbInt(),
        innerGlowColorArgb = innerGlowColor.toArgbInt(),
    )
}

@Composable
private fun rememberVinylArtworkPalette(
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

private fun composeColorFromArgb(argb: Int): Color {
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

private fun playbackModeIcon(mode: PlaybackMode): ImageVector {
    return when (mode) {
        PlaybackMode.ORDER -> Icons.Rounded.Repeat
        PlaybackMode.SHUFFLE -> Icons.Rounded.Shuffle
        PlaybackMode.REPEAT_ONE -> Icons.Rounded.RepeatOne
    }
}

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1_000).coerceAtLeast(0L)
    val minutesPart = seconds / 60
    val secondsPart = seconds % 60
    return minutesPart.toString().padStart(2, '0') + ":" + secondsPart.toString().padStart(2, '0')
}

private fun formatLyricsCandidateDuration(durationSeconds: Int): String {
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

private fun trackDisplayFormat(track: Track): String {
    return track.relativePath
        .substringAfterLast('.', "")
        .takeIf { it.isNotBlank() }
        ?.uppercase()
        ?: "未知"
}

private fun formatTrackSize(sizeBytes: Long): String {
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
