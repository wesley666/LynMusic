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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
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
import top.iwesley.lyn.music.core.model.Album
import top.iwesley.lyn.music.core.model.Artist
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.ArtworkTintTheme
import top.iwesley.lyn.music.core.model.CLASSIC_APP_THEME_TOKENS
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
import top.iwesley.lyn.music.core.model.deriveAppThemePalette
import top.iwesley.lyn.music.core.model.deriveArtworkTintTheme
import top.iwesley.lyn.music.core.model.parseThemeHexColor
import top.iwesley.lyn.music.core.model.presetThemeTokens
import top.iwesley.lyn.music.core.model.resolveAppThemeTextPalette
import top.iwesley.lyn.music.core.model.resolveAppThemeTokens
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
import top.iwesley.lyn.music.feature.library.deriveVisibleAlbums
import top.iwesley.lyn.music.feature.library.libraryAlbumId
import top.iwesley.lyn.music.feature.library.libraryArtistId
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
import top.iwesley.lyn.music.ui.mainShellColors

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
    val shellThemeTokens = remember(settingsState.selectedTheme, settingsState.customThemeTokens) {
        resolveAppThemeTokens(
            themeId = settingsState.selectedTheme,
            customThemeTokens = settingsState.customThemeTokens,
        )
    }
    val shellTextPalette = remember(settingsState.selectedTheme, settingsState.textPalettePreferences) {
        resolveAppThemeTextPalette(
            themeId = settingsState.selectedTheme,
            preferences = settingsState.textPalettePreferences,
        )
    }

    LynMusicTheme(
        themeTokens = shellThemeTokens,
        textPalette = shellTextPalette,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val compact = maxWidth < 900.dp
            val shellColors = mainShellColors
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
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

                LynMusicTheme(
                    themeTokens = CLASSIC_APP_THEME_TOKENS,
                    textPalette = AppThemeTextPalette.White,
                ) {
                    PlayerDrawerHost(
                        visible = playerState.isExpanded,
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
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                QueueDrawer(
                    state = playerState,
                    compact = compact,
                    onPlayerIntent = component.playerStore::dispatch,
                    modifier = Modifier.fillMaxSize(),
                )
                if (playerState.isManualLyricsSearchVisible) {
                    ManualLyricsSearchOverlay(
                        state = playerState,
                        onPlayerIntent = component.playerStore::dispatch,
                        modifier = Modifier.fillMaxSize(),
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
    val shellColors = mainShellColors
    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier.navigationBarsPadding(),
            ) {
                LynMusicTheme(
                    themeTokens = CLASSIC_APP_THEME_TOKENS,
                    textPalette = AppThemeTextPalette.White,
                ) {
                    MiniPlayerBarVisibility(
                        visible = playerState.snapshot.currentTrack != null && !playerState.isExpanded,
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
                NavigationBar(containerColor = shellColors.navContainer) {
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
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = shellColors.selectedContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
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
            LynMusicTheme(
                themeTokens = CLASSIC_APP_THEME_TOKENS,
                textPalette = AppThemeTextPalette.White,
            ) {
                MiniPlayerBarVisibility(
                    visible = playerState.snapshot.currentTrack != null && !playerState.isExpanded,
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
    val shellColors = mainShellColors
    Card(
        colors = CardDefaults.cardColors(containerColor = shellColors.navContainer),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, shellColors.cardBorder),
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
                val selected = selectedTab == tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondary
                            else Color.Transparent,
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent,
                            shape = RoundedCornerShape(18.dp),
                        )
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        label,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                    )
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
    val shellColors = mainShellColors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = shellColors.cardContainer),
        border = BorderStroke(1.dp, shellColors.cardBorder),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
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
                    color = shellColors.secondaryText,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MainShellAssistChip(
                        onClick = {},
                        label = { Text(platform.name) },
                        leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) },
                    )
                    MainShellAssistChip(
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
    LibraryBrowserTab(
        state = LibraryBrowserPageState(
            query = state.query,
            tracks = state.tracks,
            filteredTracks = state.filteredTracks,
            filteredAlbums = state.filteredAlbums,
            filteredArtists = state.filteredArtists,
            selectedSourceFilter = state.selectedSourceFilter,
            availableSourceFilters = state.availableSourceFilters,
            favoriteTrackIds = favoritesState.favoriteTrackIds,
            message = favoritesState.message,
        ),
        strings = LibraryBrowserStrings(
            searchLabel = "搜索歌曲 / 艺人 / 专辑",
            sectionTitle = "你的曲库",
            sectionSubtitle = "支持本地文件夹、Samba、WebDAV、Navidrome 与自定义歌词联动。",
            songsIcon = Icons.Rounded.LibraryMusic,
            emptyCollectionTitle = "曲库还是空的",
            emptyCollectionBody = "先到“来源”页导入本地文件夹、Samba、WebDAV 或 Navidrome，扫描完成后会出现在这里。",
            emptyFilterBody = "试试切回“全部来源”、更换过滤项，或调整搜索词。",
            emptySearchBody = "试试调整搜索词，或切换来源过滤。",
            trackLabel = "歌曲",
            albumLabel = "专辑",
            artistLabel = "艺人",
        ),
        onSearchChanged = { onLibraryIntent(LibraryIntent.SearchChanged(it)) },
        onSourceFilterChanged = { onLibraryIntent(LibraryIntent.SourceFilterChanged(it)) },
        onToggleFavorite = { onFavoritesIntent(FavoritesIntent.ToggleFavorite(it)) },
        onDismissMessage = { onFavoritesIntent(FavoritesIntent.ClearMessage) },
        onPlayTracks = { tracks, index -> onPlayerIntent(PlayerIntent.PlayTracks(tracks, index)) },
        modifier = modifier,
    )
}

@Composable
private fun FavoritesTab(
    state: FavoritesState,
    onFavoritesIntent: (FavoritesIntent) -> Unit,
    onPlayerIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LibraryBrowserTab(
        state = LibraryBrowserPageState(
            query = state.query,
            tracks = state.tracks,
            filteredTracks = state.filteredTracks,
            filteredAlbums = state.filteredAlbums,
            filteredArtists = state.filteredArtists,
            selectedSourceFilter = state.selectedSourceFilter,
            availableSourceFilters = state.availableSourceFilters,
            favoriteTrackIds = state.favoriteTrackIds,
            message = state.message,
        ),
        strings = LibraryBrowserStrings(
            searchLabel = "搜索喜欢的歌曲 / 艺人 / 专辑",
            sectionTitle = "我的喜欢",
            sectionSubtitle = "本地来源保存在本地，Navidrome 来源会和服务器收藏双向同步。",
            songsIcon = Icons.Rounded.Favorite,
            emptyCollectionTitle = "还没有喜欢的歌曲",
            emptyCollectionBody = "在曲库或播放器里点亮心形后，喜欢的歌曲会出现在这里。",
            emptyFilterBody = "试试切回“全部来源”、更换过滤项，或去其他来源里添加喜欢。",
            emptySearchBody = "试试调整搜索词，或切换来源过滤。",
            trackLabel = "喜欢的歌曲",
            albumLabel = "喜欢的专辑",
            artistLabel = "喜欢的艺人",
        ),
        onSearchChanged = { onFavoritesIntent(FavoritesIntent.SearchChanged(it)) },
        onSourceFilterChanged = { onFavoritesIntent(FavoritesIntent.SourceFilterChanged(it)) },
        onToggleFavorite = { onFavoritesIntent(FavoritesIntent.ToggleFavorite(it)) },
        onDismissMessage = { onFavoritesIntent(FavoritesIntent.ClearMessage) },
        onPlayTracks = { tracks, index -> onPlayerIntent(PlayerIntent.PlayTracks(tracks, index)) },
        modifier = modifier,
    )
}

private data class LibraryBrowserPageState(
    val query: String,
    val tracks: List<Track>,
    val filteredTracks: List<Track>,
    val filteredAlbums: List<Album>,
    val filteredArtists: List<Artist>,
    val selectedSourceFilter: LibrarySourceFilter,
    val availableSourceFilters: List<LibrarySourceFilter>,
    val favoriteTrackIds: Set<String>,
    val message: String?,
)

private data class LibraryBrowserStrings(
    val searchLabel: String,
    val sectionTitle: String,
    val sectionSubtitle: String,
    val songsIcon: ImageVector,
    val emptyCollectionTitle: String,
    val emptyCollectionBody: String,
    val emptyFilterBody: String,
    val emptySearchBody: String,
    val trackLabel: String,
    val albumLabel: String,
    val artistLabel: String,
)

private enum class LibraryBrowserRootView {
    Tracks,
    Albums,
    Artists,
}

@Composable
private fun LibraryBrowserTab(
    state: LibraryBrowserPageState,
    strings: LibraryBrowserStrings,
    onSearchChanged: (String) -> Unit,
    onSourceFilterChanged: (LibrarySourceFilter) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onDismissMessage: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var sourceFilterMenuExpanded by remember { mutableStateOf(false) }
    var rootView by rememberSaveable { mutableStateOf(LibraryBrowserRootView.Tracks) }
    var selectedArtistId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    val tracksByArtistId = remember(state.filteredTracks) {
        state.filteredTracks.groupBy(Track::artistLibraryIdOrNull)
    }
    val tracksByAlbumId = remember(state.filteredTracks) {
        state.filteredTracks.groupBy(Track::albumLibraryIdOrNull)
    }
    val artistAlbumCountById = remember(tracksByArtistId) {
        tracksByArtistId.entries
            .mapNotNull { (artistId, tracks) ->
                artistId?.let { it to deriveVisibleAlbums(tracks).size }
            }
            .toMap()
    }
    val selectedArtist = remember(state.filteredArtists, selectedArtistId) {
        state.filteredArtists.firstOrNull { it.id == selectedArtistId }
    }
    val artistTracks = remember(tracksByArtistId, selectedArtistId) {
        tracksByArtistId[selectedArtistId].orEmpty().sortedWith(ARTIST_DETAIL_TRACK_COMPARATOR)
    }
    val artistAlbums = remember(artistTracks) {
        deriveVisibleAlbums(artistTracks)
    }
    val selectedAlbum = remember(state.filteredAlbums, artistAlbums, selectedAlbumId, selectedArtistId) {
        when {
            selectedAlbumId == null -> null
            selectedArtistId != null -> artistAlbums.firstOrNull { it.id == selectedAlbumId }
            else -> state.filteredAlbums.firstOrNull { it.id == selectedAlbumId }
        }
    }
    val albumTracks = remember(tracksByAlbumId, artistTracks, selectedAlbumId, selectedArtistId) {
        when {
            selectedAlbumId == null -> emptyList()
            selectedArtistId != null -> artistTracks.filter { it.albumLibraryIdOrNull() == selectedAlbumId }
            else -> tracksByAlbumId[selectedAlbumId].orEmpty()
        }.sortedWith(ALBUM_DETAIL_TRACK_COMPARATOR)
    }

    LaunchedEffect(
        rootView,
        state.filteredAlbums,
        state.filteredArtists,
        selectedArtistId,
        selectedAlbumId,
        artistAlbums,
    ) {
        when (rootView) {
            LibraryBrowserRootView.Tracks -> {
                if (selectedArtistId != null) selectedArtistId = null
                if (selectedAlbumId != null) selectedAlbumId = null
            }

            LibraryBrowserRootView.Albums -> {
                if (selectedArtistId != null) selectedArtistId = null
                if (selectedAlbumId != null && state.filteredAlbums.none { it.id == selectedAlbumId }) {
                    selectedAlbumId = null
                }
            }

            LibraryBrowserRootView.Artists -> {
                if (selectedArtistId != null && state.filteredArtists.none { it.id == selectedArtistId }) {
                    selectedArtistId = null
                    selectedAlbumId = null
                } else if (selectedAlbumId != null && artistAlbums.none { it.id == selectedAlbumId }) {
                    selectedAlbumId = null
                }
            }
        }
    }

    fun selectRootView(view: LibraryBrowserRootView) {
        rootView = view
        selectedArtistId = null
        selectedAlbumId = null
    }

    val shellColors = mainShellColors
    val searchFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = shellColors.cardBorder,
        unfocusedBorderColor = shellColors.cardBorder,
        disabledBorderColor = shellColors.cardBorder,
    )

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 42.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                ImeAwareOutlinedTextField(
                    value = state.query,
                    onValueChange = onSearchChanged,
                    label = { Text(strings.searchLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = searchFieldColors,
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
                        containerColor = mainShellColors.navContainer,
                    ) {
                        state.availableSourceFilters.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(librarySourceFilterMenuLabel(filter)) },
                                onClick = {
                                    sourceFilterMenuExpanded = false
                                    onSourceFilterChanged(filter)
                                },
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        title = "歌曲",
                        value = state.filteredTracks.size.toString(),
                        icon = strings.songsIcon,
                        selected = rootView == LibraryBrowserRootView.Tracks,
                        onClick = { selectRootView(LibraryBrowserRootView.Tracks) },
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        title = "专辑",
                        value = state.filteredAlbums.size.toString(),
                        icon = Icons.Rounded.Album,
                        selected = rootView == LibraryBrowserRootView.Albums,
                        onClick = { selectRootView(LibraryBrowserRootView.Albums) },
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        title = "艺人",
                        value = state.filteredArtists.size.toString(),
                        icon = Icons.Rounded.Tune,
                        selected = rootView == LibraryBrowserRootView.Artists,
                        onClick = { selectRootView(LibraryBrowserRootView.Artists) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            state.message?.let { message ->
                item {
                    BannerCard(
                        message = message,
                        onDismiss = onDismissMessage,
                    )
                }
            }
            when {
                selectedAlbum != null -> {
                    item {
                        DetailBackButton(onClick = { selectedAlbumId = null })
                    }
                    item {
                        DetailSummaryCard(
                            title = selectedAlbum.title,
                            subtitle = selectedAlbum.artistName ?: "未知艺人",
                            supportingText = "${albumTracks.size} 首歌曲",
                            artworkLocator = albumTracks.firstOrNull()?.artworkLocator,
                        )
                    }
                    item {
                        SectionTitle(title = "歌曲", subtitle = "当前专辑下的可见歌曲。")
                    }
                    if (albumTracks.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "这个专辑暂时没有歌曲",
                                body = "当前筛选结果里已经没有这个专辑的可见歌曲。",
                            )
                        }
                    } else {
                        itemsIndexed(albumTracks, key = { _, item -> item.id }) { index, track ->
                            TrackRow(
                                track = track,
                                index = index,
                                isFavorite = track.id in state.favoriteTrackIds,
                                onToggleFavorite = { onToggleFavorite(track) },
                                onClick = {
                                    onPlayTracks(albumTracks, index)
                                },
                            )
                        }
                    }
                }

                rootView == LibraryBrowserRootView.Artists && selectedArtist != null -> {
                    item {
                        DetailBackButton(
                            onClick = {
                                selectedArtistId = null
                                selectedAlbumId = null
                            },
                        )
                    }
                    item {
                        DetailSummaryCard(
                            title = selectedArtist.name,
                            subtitle = "${artistTracks.size} 首歌曲 · ${artistAlbums.size} 张专辑",
                            supportingText = "当前筛选结果中的艺人详情",
                            artworkLocator = artistTracks.firstOrNull()?.artworkLocator,
                        )
                    }
                    item {
                        SectionTitle(title = "专辑", subtitle = "当前艺人下的可见专辑。")
                    }
                    if (artistAlbums.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "这个艺人下暂无专辑信息",
                                body = "当前艺人的可见歌曲还没有可用的专辑标签。",
                            )
                        }
                    } else {
                        items(artistAlbums, key = { it.id }) { album ->
                            AlbumRow(
                                album = album,
                                artworkLocator = artistTracks.firstOrNull { it.albumLibraryIdOrNull() == album.id }?.artworkLocator,
                                onClick = { selectedAlbumId = album.id },
                            )
                        }
                    }
                    item {
                        SectionTitle(title = "歌曲", subtitle = "当前艺人下的可见歌曲。")
                    }
                    if (artistTracks.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "这个艺人暂时没有歌曲",
                                body = "当前筛选结果里已经没有这个艺人的可见歌曲。",
                            )
                        }
                    } else {
                        itemsIndexed(artistTracks, key = { _, item -> item.id }) { index, track ->
                            TrackRow(
                                track = track,
                                index = index,
                                isFavorite = track.id in state.favoriteTrackIds,
                                onToggleFavorite = { onToggleFavorite(track) },
                                onClick = {
                                    onPlayTracks(artistTracks, index)
                                },
                            )
                        }
                    }
                }

                else -> {
                    val currentItemCount = when (rootView) {
                        LibraryBrowserRootView.Tracks -> state.filteredTracks.size
                        LibraryBrowserRootView.Albums -> state.filteredAlbums.size
                        LibraryBrowserRootView.Artists -> state.filteredArtists.size
                    }
                    val currentLabel = when (rootView) {
                        LibraryBrowserRootView.Tracks -> strings.trackLabel
                        LibraryBrowserRootView.Albums -> strings.albumLabel
                        LibraryBrowserRootView.Artists -> strings.artistLabel
                    }
                    item {
                        SectionTitle(title = strings.sectionTitle, subtitle = strings.sectionSubtitle)
                    }
                    if (currentItemCount == 0) {
                        item {
                            when {
                                state.tracks.isEmpty() -> EmptyStateCard(
                                    title = strings.emptyCollectionTitle,
                                    body = strings.emptyCollectionBody,
                                )

                                state.selectedSourceFilter != LibrarySourceFilter.ALL -> EmptyStateCard(
                                    title = "当前来源下没有$currentLabel",
                                    body = strings.emptyFilterBody,
                                )

                                else -> EmptyStateCard(
                                    title = "没有匹配的$currentLabel",
                                    body = strings.emptySearchBody,
                                )
                            }
                        }
                    } else {
                        when (rootView) {
                            LibraryBrowserRootView.Tracks -> {
                                itemsIndexed(state.filteredTracks, key = { _, item -> item.id }) { index, track ->
                                    TrackRow(
                                        track = track,
                                        index = index,
                                        isFavorite = track.id in state.favoriteTrackIds,
                                        onToggleFavorite = { onToggleFavorite(track) },
                                        onClick = {
                                            onPlayTracks(state.filteredTracks, index)
                                        },
                                    )
                                }
                            }

                            LibraryBrowserRootView.Albums -> {
                                items(state.filteredAlbums, key = { it.id }) { album ->
                                    AlbumRow(
                                        album = album,
                                        artworkLocator = tracksByAlbumId[album.id].orEmpty().firstOrNull()?.artworkLocator,
                                        onClick = { selectedAlbumId = album.id },
                                    )
                                }
                            }

                            LibraryBrowserRootView.Artists -> {
                                items(state.filteredArtists, key = { it.id }) { artist ->
                                    ArtistRow(
                                        artist = artist,
                                        albumCount = artistAlbumCountById[artist.id] ?: 0,
                                        onClick = {
                                            selectedArtistId = artist.id
                                            selectedAlbumId = null
                                        },
                                    )
                                }
                            }
                        }
                    }
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

private val ALBUM_DETAIL_TRACK_COMPARATOR = compareBy<Track>(
    { it.discNumber ?: Int.MAX_VALUE },
    { it.trackNumber ?: Int.MAX_VALUE },
    { it.title.lowercase() },
)

private val ARTIST_DETAIL_TRACK_COMPARATOR = compareBy<Track>(
    { it.albumTitle.orEmpty().lowercase() },
    { it.discNumber ?: Int.MAX_VALUE },
    { it.trackNumber ?: Int.MAX_VALUE },
    { it.title.lowercase() },
)

private fun Track.artistLibraryIdOrNull(): String? {
    return artistName?.trim()?.takeIf { it.isNotBlank() }?.let(::libraryArtistId)
}

private fun Track.albumLibraryIdOrNull(): String? {
    val title = albumTitle?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return libraryAlbumId(artistName, title)
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
        val shellColors = mainShellColors
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(shellColors.cardBorder),
        )
        Box(
            modifier = Modifier
                .offset(y = thumbOffsetDp)
                .height(thumbHeightDp)
                .width(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.secondary)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.18f)),
                    RoundedCornerShape(999.dp),
                ),
        )
    }
}

@Composable
private fun SourcesTab(
    state: ImportState,
    onImportIntent: (ImportIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    val importFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = shellColors.cardBorder,
        unfocusedBorderColor = shellColors.cardBorder,
        disabledBorderColor = shellColors.cardBorder,
    )
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
        MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
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
        MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Navidrome", fontWeight = FontWeight.Bold)
                if (!state.capabilities.supportsNavidromeImport) {
                    Text("当前平台暂未开放应用内 Navidrome 导入。")
                }
                ImeAwareOutlinedTextField(
                    value = state.navidromeLabel,
                    onValueChange = { onImportIntent(ImportIntent.NavidromeLabelChanged(it)) },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = importFieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.navidromeBaseUrl,
                    onValueChange = { onImportIntent(ImportIntent.NavidromeBaseUrlChanged(it)) },
                    label = { Text("服务器地址") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = importFieldColors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImeAwareOutlinedTextField(
                        value = state.navidromeUsername,
                        onValueChange = { onImportIntent(ImportIntent.NavidromeUsernameChanged(it)) },
                        label = { Text("用户名") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = importFieldColors,
                    )
                    ImeAwareOutlinedTextField(
                        value = state.navidromePassword,
                        onValueChange = { onImportIntent(ImportIntent.NavidromePasswordChanged(it)) },
                        label = { Text("密码") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = importFieldColors,
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
        MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Samba / SMB", fontWeight = FontWeight.Bold)
                if (!state.capabilities.supportsSambaImport) {
                    Text("当前平台建议通过系统 Files 挂载 SMB 后，再用本地文件夹方式接入。")
                }
                ImeAwareOutlinedTextField(value = state.sambaLabel, onValueChange = { onImportIntent(ImportIntent.SambaLabelChanged(it)) }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = importFieldColors)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImeAwareOutlinedTextField(value = state.sambaServer, onValueChange = { onImportIntent(ImportIntent.SambaServerChanged(it)) }, label = { Text("服务器地址") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp), colors = importFieldColors)
                    ImeAwareOutlinedTextField(
                        value = state.sambaPort,
                        onValueChange = { onImportIntent(ImportIntent.SambaPortChanged(it)) },
                        label = { Text("端口") },
                        modifier = Modifier.width(140.dp),
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = importFieldColors,
                    )
                }
                ImeAwareOutlinedTextField(
                    value = state.sambaPath,
                    onValueChange = { onImportIntent(ImportIntent.SambaPathChanged(it)) },
                    label = { Text("路径（Share/子目录）") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = importFieldColors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImeAwareOutlinedTextField(value = state.sambaUsername, onValueChange = { onImportIntent(ImportIntent.SambaUsernameChanged(it)) }, label = { Text("用户名") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp), colors = importFieldColors)
                    ImeAwareOutlinedTextField(value = state.sambaPassword, onValueChange = { onImportIntent(ImportIntent.SambaPasswordChanged(it)) }, label = { Text("密码（选填）") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp), colors = importFieldColors)
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
        MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("WebDAV", fontWeight = FontWeight.Bold)
                if (!state.capabilities.supportsWebDavImport) {
                    Text("当前平台暂未开放应用内 WebDAV 导入。")
                }
                ImeAwareOutlinedTextField(
                    value = state.webDavLabel,
                    onValueChange = { onImportIntent(ImportIntent.WebDavLabelChanged(it)) },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = importFieldColors,
                )
                ImeAwareOutlinedTextField(
                    value = state.webDavRootUrl,
                    onValueChange = { onImportIntent(ImportIntent.WebDavRootUrlChanged(it)) },
                    label = { Text("根 URL") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = importFieldColors,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImeAwareOutlinedTextField(
                        value = state.webDavUsername,
                        onValueChange = { onImportIntent(ImportIntent.WebDavUsernameChanged(it)) },
                        label = { Text("用户名（选填）") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = importFieldColors,
                    )
                    ImeAwareOutlinedTextField(
                        value = state.webDavPassword,
                        onValueChange = { onImportIntent(ImportIntent.WebDavPasswordChanged(it)) },
                        label = { Text("密码（选填）") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = importFieldColors,
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
    val shellColors = mainShellColors
    val settingsFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = shellColors.cardBorder,
        unfocusedBorderColor = shellColors.cardBorder,
        disabledBorderColor = shellColors.cardBorder,
    )
    val selectedThemeTextPalette = remember(state.selectedTheme, state.textPalettePreferences) {
        resolveAppThemeTextPalette(
            themeId = state.selectedTheme,
            preferences = state.textPalettePreferences,
        )
    }
    val themeDisplayOrder = remember {
        listOf(
            AppThemeId.Classic,
            AppThemeId.Ocean,
            AppThemeId.Forest,
            AppThemeId.Sand,
            AppThemeId.Custom,
        )
    }
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
            SectionTitle(
                title = "主题",
                subtitle = "切换预置主题，自定义主界面颜色，并给每个主题单独选择黑字或白字。",
            )
            MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    themeDisplayOrder.chunked(2).forEach { rowThemes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowThemes.forEach { themeId ->
                                ThemePresetCard(
                                    themeId = themeId,
                                    selected = state.selectedTheme == themeId,
                                    tokens = if (themeId == AppThemeId.Custom) state.customThemeTokens else presetThemeTokens(themeId),
                                    textPalette = resolveAppThemeTextPalette(themeId, state.textPalettePreferences),
                                    onClick = { onSettingsIntent(SettingsIntent.ThemeSelected(themeId)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowThemes.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    ThemeTextPaletteToggle(
                        selectedTheme = state.selectedTheme,
                        selectedPalette = selectedThemeTextPalette,
                        onSelected = {
                            onSettingsIntent(SettingsIntent.ThemeTextPaletteSelected(state.selectedTheme, it))
                        },
                    )
                    if (state.selectedTheme == AppThemeId.Custom) {
                        Text(
                            text = "自定义主题",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "输入 3 个基础颜色后保存，主界面会立即切换到新的自定义配色。",
                            color = shellColors.secondaryText,
                        )
                        ThemeColorField(
                            label = "主背景色",
                            value = state.customBackgroundHex,
                            previewArgb = parseThemeHexColor(state.customBackgroundHex)
                                ?: state.customThemeTokens.backgroundArgb,
                            onValueChange = { onSettingsIntent(SettingsIntent.CustomThemeBackgroundChanged(it)) },
                            colors = settingsFieldColors,
                        )
                        ThemeColorField(
                            label = "主色",
                            value = state.customAccentHex,
                            previewArgb = parseThemeHexColor(state.customAccentHex)
                                ?: state.customThemeTokens.accentArgb,
                            onValueChange = { onSettingsIntent(SettingsIntent.CustomThemeAccentChanged(it)) },
                            colors = settingsFieldColors,
                        )
                        ThemeColorField(
                            label = "选中 / 落焦色",
                            value = state.customFocusHex,
                            previewArgb = parseThemeHexColor(state.customFocusHex)
                                ?: state.customThemeTokens.focusArgb,
                            onValueChange = { onSettingsIntent(SettingsIntent.CustomThemeFocusChanged(it)) },
                            colors = settingsFieldColors,
                        )
                        state.themeInputError?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { onSettingsIntent(SettingsIntent.SaveCustomTheme) }) {
                                Text("保存自定义主题")
                            }
                            OutlinedButton(onClick = { onSettingsIntent(SettingsIntent.ResetCustomTheme) }) {
                                Text("重置自定义主题")
                            }
                        }
                    }
                }
            }
            SectionTitle(title = "歌词 API", subtitle = "声明式适配 JSON / XML / LRC / 纯文本返回。")
            MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
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
            MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
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
                            MainShellAssistChip(
                                onClick = {},
                                label = { Text("Direct") },
                                leadingIcon = { Icon(Icons.Rounded.CloudSync, null) },
                            )
                        }
                    }
                    ImeAwareOutlinedTextField(
                        value = state.lrcApiUrl,
                        onValueChange = { onSettingsIntent(SettingsIntent.LrcApiUrlChanged(it)) },
                        label = { Text("LrcAPI 请求地址") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        singleLine = true,
                        colors = settingsFieldColors,
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
            MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
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
                            MainShellAssistChip(
                                onClick = {},
                                label = { Text("Workflow") },
                                leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) },
                            )
                        }
                    }
                    ImeAwareOutlinedTextField(
                        value = state.musicmatchUserToken,
                        onValueChange = { onSettingsIntent(SettingsIntent.MusicmatchUserTokenChanged(it)) },
                        label = { Text("Musicmatch usertoken") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        singleLine = true,
                        colors = settingsFieldColors,
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
            MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ImeAwareOutlinedTextField(value = state.name, onValueChange = { onSettingsIntent(SettingsIntent.NameChanged(it)) }, label = { Text("歌词源名称") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = settingsFieldColors)
                    ImeAwareOutlinedTextField(value = state.urlTemplate, onValueChange = { onSettingsIntent(SettingsIntent.UrlChanged(it)) }, label = { Text("URL 模板") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = settingsFieldColors)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EnumSelector(label = "Method", values = RequestMethod.entries, selected = state.method, onSelected = { onSettingsIntent(SettingsIntent.MethodChanged(it)) }, modifier = Modifier.weight(1f))
                        EnumSelector(label = "Format", values = LyricsResponseFormat.entries, selected = state.responseFormat, onSelected = { onSettingsIntent(SettingsIntent.ResponseFormatChanged(it)) }, modifier = Modifier.weight(1f))
                    }
                    ImeAwareOutlinedTextField(value = state.queryTemplate, onValueChange = { onSettingsIntent(SettingsIntent.QueryChanged(it)) }, label = { Text("Query 模板") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = settingsFieldColors)
                    ImeAwareOutlinedTextField(value = state.bodyTemplate, onValueChange = { onSettingsIntent(SettingsIntent.BodyChanged(it)) }, label = { Text("Body 模板") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = settingsFieldColors)
                    ImeAwareOutlinedTextField(value = state.headersTemplate, onValueChange = { onSettingsIntent(SettingsIntent.HeadersChanged(it)) }, label = { Text("请求头，每行 Key: Value") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = settingsFieldColors)
                    ImeAwareOutlinedTextField(value = state.extractor, onValueChange = { onSettingsIntent(SettingsIntent.ExtractorChanged(it)) }, label = { Text("提取规则") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = settingsFieldColors)
                    ImeAwareOutlinedTextField(
                        value = state.priority,
                        onValueChange = { onSettingsIntent(SettingsIntent.PriorityChanged(it)) },
                        label = { Text("优先级") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = settingsFieldColors,
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
            MainShellElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Workflow JSON", fontWeight = FontWeight.Bold)
                    Text(
                        "用于新建或编辑多阶段歌词源，支持搜歌 -> 选歌 -> 拉歌词。当前仍直接编辑原始 JSON。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ImeAwareOutlinedTextField(
                        value = state.workflowJsonInput,
                        onValueChange = { onSettingsIntent(SettingsIntent.WorkflowJsonChanged(it)) },
                        label = { Text("Workflow JSON") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp),
                        shape = RoundedCornerShape(18.dp),
                        minLines = 10,
                        maxLines = 18,
                        colors = settingsFieldColors,
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
private fun ThemePresetCard(
    themeId: AppThemeId,
    selected: Boolean,
    tokens: AppThemeTokens,
    textPalette: AppThemeTextPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    val previewPalette = remember(tokens, textPalette) {
        deriveAppThemePalette(
            tokens = tokens,
            textPalette = textPalette,
        )
    }
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(previewPalette.cardContainerArgb),
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) shellColors.selectedBorder else Color(previewPalette.cardBorderArgb),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = themeDisplayName(themeId),
                fontWeight = FontWeight.Bold,
                color = Color(previewPalette.onSurfaceArgb),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeSwatch(tokens.backgroundArgb, Modifier.weight(1f))
                ThemeSwatch(tokens.accentArgb, Modifier.weight(1f))
                ThemeSwatch(tokens.focusArgb, Modifier.weight(1f))
            }
            Text(
                text = buildString {
                    append(if (themeId == AppThemeId.Custom) "自定义主界面颜色" else "预置主题")
                    append(" · ")
                    append(themeTextPaletteLabel(textPalette))
                },
                color = Color(previewPalette.secondaryTextArgb),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ThemeTextPaletteToggle(
    selectedTheme: AppThemeId,
    selectedPalette: AppThemeTextPalette,
    onSelected: (AppThemeTextPalette) -> Unit,
) {
    val shellColors = mainShellColors
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "文字颜色",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${themeDisplayName(selectedTheme)}主题单独保存黑字或白字，不会影响播放界面。",
            color = shellColors.secondaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(AppThemeTextPalette.White, AppThemeTextPalette.Black).forEach { palette ->
                val selected = selectedPalette == palette
                if (selected) {
                    Button(
                        onClick = { onSelected(palette) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(themeTextPaletteLabel(palette))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelected(palette) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(themeTextPaletteLabel(palette))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeColorField(
    label: String,
    value: String,
    previewArgb: Int,
    onValueChange: (String) -> Unit,
    colors: androidx.compose.material3.TextFieldColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeSwatch(
            argb = previewArgb,
            modifier = Modifier.size(42.dp),
        )
        ImeAwareOutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            singleLine = true,
            colors = colors,
        )
    }
}

@Composable
private fun ThemeSwatch(
    argb: Int,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    Box(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(argb))
            .border(1.dp, shellColors.cardBorder, RoundedCornerShape(12.dp)),
    )
}

private fun themeDisplayName(themeId: AppThemeId): String {
    return when (themeId) {
        AppThemeId.Classic -> "经典黑"
        AppThemeId.Forest -> "森林"
        AppThemeId.Ocean -> "经典白"
        AppThemeId.Sand -> "砂岩"
        AppThemeId.Custom -> "自定义"
    }
}

private fun themeTextPaletteLabel(textPalette: AppThemeTextPalette): String {
    return when (textPalette) {
        AppThemeTextPalette.White -> "白字"
        AppThemeTextPalette.Black -> "黑字"
    }
}

@Composable
internal fun PlayerLyricsPane(
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
            resultsAppliedSubtitle = "点选任一结果即可直接应用到当前歌曲。",
        ),
        onDismiss = { onPlayerIntent(PlayerIntent.DismissManualLyricsSearch) },
        onTitleChanged = { onPlayerIntent(PlayerIntent.ManualLyricsTitleChanged(it)) },
        onArtistChanged = { onPlayerIntent(PlayerIntent.ManualLyricsArtistChanged(it)) },
        onAlbumChanged = { onPlayerIntent(PlayerIntent.ManualLyricsAlbumChanged(it)) },
        onSearch = { onPlayerIntent(PlayerIntent.SearchManualLyrics) },
        onApplyDirectCandidate = { onPlayerIntent(PlayerIntent.ApplyManualLyricsCandidate(it)) },
        onApplyWorkflowCandidate = { onPlayerIntent(PlayerIntent.ApplyWorkflowSongCandidate(it)) },
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
                val stackedFields = maxWidth < 560.dp
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
                                onValueChange = { onPlayerIntent(PlayerIntent.ManualLyricsArtistChanged(it)) },
                                label = { Text("歌手") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                singleLine = true,
                                colors = textFieldColors,
                            )
                            ImeAwareOutlinedTextField(
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
                            ImeAwareOutlinedTextField(
                                value = state.manualLyricsArtistName,
                                onValueChange = { onPlayerIntent(PlayerIntent.ManualLyricsArtistChanged(it)) },
                                label = { Text("歌手") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(18.dp),
                                singleLine = true,
                                colors = textFieldColors,
                            )
                            ImeAwareOutlinedTextField(
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
internal fun LyricsShareOverlay(
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
internal fun FavoriteToggleButton(
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
private fun DetailBackButton(
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text("返回")
    }
}

@Composable
private fun DetailSummaryCard(
    title: String,
    subtitle: String,
    supportingText: String,
    artworkLocator: String?,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = shellColors.cardContainer,
        ),
        border = BorderStroke(1.dp, shellColors.cardBorder),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtworkThumbnail(
                artworkLocator = artworkLocator,
                modifier = Modifier.size(68.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = supportingText,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AlbumRow(
    album: Album,
    artworkLocator: String?,
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
            TrackArtworkThumbnail(artworkLocator = artworkLocator)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = album.artistName ?: "未知艺人",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${album.trackCount} 首",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun ArtistRow(
    artist: Artist,
    albumCount: Int,
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
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(shellColors.cardContainer)
                    .border(
                        border = BorderStroke(1.dp, shellColors.cardBorder),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${artist.trackCount} 首歌曲 · $albumCount 张专辑",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
private fun TrackRow(
    track: Track,
    index: Int,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
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
            Text((index + 1).toString().padStart(2, '0'), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
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
                .background(shellColors.cardBorder),
        )
    }
}

@Composable
private fun TrackArtworkThumbnail(
    artworkLocator: String?,
    modifier: Modifier = Modifier,
) {
    val artworkBitmap = rememberPlatformArtworkBitmap(artworkLocator)
    val shellColors = mainShellColors
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(shellColors.cardContainer)
            .border(
                border = BorderStroke(1.dp, shellColors.cardBorder),
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
private fun MainShellElevatedCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shellColors = mainShellColors
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = shellColors.cardContainer),
        border = BorderStroke(1.dp, shellColors.cardBorder),
        content = content,
    )
}

@Composable
private fun MainShellAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val shellColors = mainShellColors
    AssistChip(
        onClick = onClick,
        label = label,
        leadingIcon = leadingIcon,
        enabled = enabled,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = shellColors.navContainer,
            labelColor = MaterialTheme.colorScheme.onSurface,
            leadingIconContentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = shellColors.cardContainer,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = BorderStroke(1.dp, shellColors.cardBorder),
    )
}

@Composable
private fun SourceCard(
    state: top.iwesley.lyn.music.core.model.SourceWithStatus,
    enabled: Boolean,
    onRescan: () -> Unit,
    onDelete: () -> Unit,
) {
    val shellColors = mainShellColors
    ElevatedCard(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = shellColors.cardContainer),
    ) {
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
                MainShellAssistChip(onClick = {}, label = { Text("${state.indexState?.trackCount ?: 0} 首歌曲") }, leadingIcon = { Icon(Icons.Rounded.LibraryMusic, null) })
                MainShellAssistChip(onClick = {}, label = { Text(if (state.indexState?.lastError == null) "扫描正常" else "扫描失败") }, leadingIcon = { Icon(Icons.Rounded.CloudSync, null) })
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
                    MainShellAssistChip(onClick = {}, label = { Text(source.method.name) }, leadingIcon = { Icon(Icons.Rounded.CloudSync, null) })
                    MainShellAssistChip(onClick = {}, label = { Text(source.responseFormat.name) }, leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) })
                }

                is top.iwesley.lyn.music.core.model.WorkflowLyricsSourceConfig -> {
                    MainShellAssistChip(onClick = {}, label = { Text("WORKFLOW") }, leadingIcon = { Icon(Icons.Rounded.CloudSync, null) })
                    MainShellAssistChip(onClick = {}, label = { Text("${source.lyrics.steps.size} 步") }, leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) })
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
    val shellColors = mainShellColors
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(
            containerColor = shellColors.selectedContainer,
        ),
        border = BorderStroke(1.dp, shellColors.selectedBorder),
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
    val shellColors = mainShellColors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = shellColors.secondaryText)
    }
}

@Composable
private fun BannerCard(
    message: String,
    onDismiss: () -> Unit,
) {
    val shellColors = mainShellColors
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = shellColors.selectedContainer),
        border = BorderStroke(1.dp, shellColors.selectedBorder),
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
    val shellColors = mainShellColors
    Card(
        modifier = modifier.widthIn(max = 420.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = shellColors.navContainer,
        ),
        border = BorderStroke(1.dp, shellColors.cardBorder),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun EmptyStateCard(
    title: String,
    body: String,
) {
    val shellColors = mainShellColors
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = shellColors.cardContainer),
        border = BorderStroke(1.dp, shellColors.cardBorder),
    ) {
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
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shellColors = mainShellColors
    val selectedContentColor = MaterialTheme.colorScheme.onSecondary
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondary
            } else {
                shellColors.cardContainer
            },
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.secondary
            } else {
                shellColors.cardBorder
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                icon,
                null,
                tint = if (selected) selectedContentColor else MaterialTheme.colorScheme.primary,
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (selected) selectedContentColor else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                title,
                color = if (selected) selectedContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun VinylPlaceholder(
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

internal data class VinylArtworkPalette(
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
internal fun rememberVinylArtworkPalette(
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
    val shellColors = mainShellColors
    MainShellElevatedCard(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                values.forEach { value ->
                    val active = value == selected
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (active) shellColors.selectedContainer else shellColors.navContainer,
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

internal fun modeLabel(mode: PlaybackMode): String {
    return when (mode) {
        PlaybackMode.ORDER -> "顺序播放"
        PlaybackMode.SHUFFLE -> "随机播放"
        PlaybackMode.REPEAT_ONE -> "单曲循环"
    }
}

internal fun playbackModeIcon(mode: PlaybackMode): ImageVector {
    return when (mode) {
        PlaybackMode.ORDER -> Icons.Rounded.Repeat
        PlaybackMode.SHUFFLE -> Icons.Rounded.Shuffle
        PlaybackMode.REPEAT_ONE -> Icons.Rounded.RepeatOne
    }
}

internal fun formatDuration(durationMs: Long): String {
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
