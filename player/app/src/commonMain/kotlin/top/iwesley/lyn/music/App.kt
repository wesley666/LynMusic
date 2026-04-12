package top.iwesley.lyn.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import top.iwesley.lyn.music.core.model.AppTab
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.CLASSIC_APP_THEME_TOKENS
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaylistKind
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.resolveAppThemeTextPalette
import top.iwesley.lyn.music.core.model.resolveAppThemeTokens
import top.iwesley.lyn.music.data.repository.DefaultPlaybackRepository
import top.iwesley.lyn.music.data.repository.PlayerRuntimeServices
import top.iwesley.lyn.music.feature.favorites.FavoritesIntent
import top.iwesley.lyn.music.feature.favorites.FavoritesStore
import top.iwesley.lyn.music.feature.importing.ImportStore
import top.iwesley.lyn.music.feature.library.LibraryStore
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.player.PlayerStore
import top.iwesley.lyn.music.feature.playlists.PlaylistsIntent
import top.iwesley.lyn.music.feature.playlists.PlaylistsStore
import top.iwesley.lyn.music.feature.settings.SettingsStore
import top.iwesley.lyn.music.feature.tags.MusicTagsStore
import top.iwesley.lyn.music.ui.LynMusicTheme
import top.iwesley.lyn.music.ui.mainShellColors

class LynMusicAppComponent(
    val platform: PlatformDescriptor,
    val logger: DiagnosticLogger,
    val libraryStore: LibraryStore,
    val playlistsStore: PlaylistsStore,
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
        systemPlaybackControlsPlatformService = playerRuntimeServices.systemPlaybackControlsPlatformService,
        logger = sharedGraph.logger,
    )
    return LynMusicAppComponent(
        platform = sharedGraph.platform,
        logger = sharedGraph.logger,
        libraryStore = sharedGraph.libraryStore,
        playlistsStore = sharedGraph.playlistsStore,
        favoritesStore = sharedGraph.favoritesStore,
        musicTagsStore = sharedGraph.musicTagsStore,
        importStore = sharedGraph.importStore,
        playerStore = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = sharedGraph.lyricsRepository,
            storeScope = sharedGraph.scope,
            lyricsSharePlatformService = playerRuntimeServices.lyricsSharePlatformService,
            logger = sharedGraph.logger,
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
    val playlistsState by component.playlistsStore.state.collectAsState()
    val favoritesState by component.favoritesStore.state.collectAsState()
    val musicTagsState by component.musicTagsStore.state.collectAsState()
    val importState by component.importStore.state.collectAsState()
    val playerState by component.playerStore.state.collectAsState()
    val settingsState by component.settingsStore.state.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Library) }
    var pendingPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var pendingLibraryNavigationTarget by remember { mutableStateOf<LibraryNavigationTarget?>(null) }
    var isMusicTagsMobileEditorVisible by rememberSaveable { mutableStateOf(false) }
    val shellThemeTokens = remember(settingsState.selectedTheme, settingsState.customThemeTokens) {
        resolveAppThemeTokens(
            themeId = settingsState.selectedTheme,
            customThemeTokens = settingsState.customThemeTokens,
        )
    }
    val shellTextPalette =
        remember(settingsState.selectedTheme, settingsState.textPalettePreferences) {
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
            playerState.message?.let { message ->
                LaunchedEffect(message) {
                    kotlinx.coroutines.delay(2_500)
                    component.playerStore.dispatch(PlayerIntent.ClearMessage)
                }
            }
            playlistsState.message?.let { message ->
                LaunchedEffect(message) {
                    kotlinx.coroutines.delay(2_500)
                    component.playlistsStore.dispatch(PlaylistsIntent.ClearMessage)
                }
            }
            val layoutProfile = buildLayoutProfile(
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                platform = component.platform,
            )
            val compact = layoutProfile.isCompactShell
            val mobilePortraitMiniPlayer = layoutProfile.usesPortraitMiniPlayer
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
                        playlistsState = playlistsState,
                        favoritesState = favoritesState,
                        musicTagsState = musicTagsState,
                        musicTagsEffects = component.musicTagsStore.effects,
                        importState = importState,
                        playerState = playerState,
                        settingsState = settingsState,
                        onLibraryIntent = component.libraryStore::dispatch,
                        onPlaylistsIntent = component.playlistsStore::dispatch,
                        onFavoritesIntent = component.favoritesStore::dispatch,
                        onMusicTagsIntent = component.musicTagsStore::dispatch,
                        onImportIntent = component.importStore::dispatch,
                        onPlayerIntent = component.playerStore::dispatch,
                        onSettingsIntent = component.settingsStore::dispatch,
                        libraryNavigationTarget = pendingLibraryNavigationTarget,
                        onLibraryNavigationHandled = { pendingLibraryNavigationTarget = null },
                        mobilePortraitMiniPlayer = mobilePortraitMiniPlayer,
                        hideMiniPlayerBar = selectedTab == AppTab.Tags && isMusicTagsMobileEditorVisible,
                        onMobileEditorVisibilityChanged = { isMusicTagsMobileEditorVisible = it },
                        onOpenAddToPlaylist = {
                            pendingPlaylistTrack = playerState.snapshot.currentTrack
                        },
                    )
                } else {
                    DesktopShell(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        platform = component.platform,
                        libraryState = libraryState,
                        playlistsState = playlistsState,
                        favoritesState = favoritesState,
                        musicTagsState = musicTagsState,
                        musicTagsEffects = component.musicTagsStore.effects,
                        importState = importState,
                        playerState = playerState,
                        settingsState = settingsState,
                        onLibraryIntent = component.libraryStore::dispatch,
                        onPlaylistsIntent = component.playlistsStore::dispatch,
                        onFavoritesIntent = component.favoritesStore::dispatch,
                        onMusicTagsIntent = component.musicTagsStore::dispatch,
                        onImportIntent = component.importStore::dispatch,
                        onPlayerIntent = component.playerStore::dispatch,
                        onSettingsIntent = component.settingsStore::dispatch,
                        libraryNavigationTarget = pendingLibraryNavigationTarget,
                        onLibraryNavigationHandled = { pendingLibraryNavigationTarget = null },
                        onOpenAddToPlaylist = {
                            pendingPlaylistTrack = playerState.snapshot.currentTrack
                        },
                    )
                }

                LynMusicTheme(
                    themeTokens = CLASSIC_APP_THEME_TOKENS,
                    textPalette = AppThemeTextPalette.White,
                ) {
                    PlayerDrawerHost(
                        visible = playerState.isExpanded,
                        platform = component.platform,
                        logger = component.logger,
                        state = playerState,
                        lyricsShareThemeTokens = shellThemeTokens,
                        lyricsShareTextPalette = shellTextPalette,
                        onPlayerIntent = component.playerStore::dispatch,
                        isFavorite = playerState.snapshot.currentTrack?.id in favoritesState.favoriteTrackIds,
                        onToggleFavorite = {
                            playerState.snapshot.currentTrack?.let { track ->
                                component.favoritesStore.dispatch(
                                    FavoritesIntent.ToggleFavorite(
                                        track
                                    )
                                )
                            }
                        },
                        onOpenAddToPlaylist = {
                            pendingPlaylistTrack = playerState.snapshot.currentTrack
                        },
                        onOpenQueue = {
                            component.playerStore.dispatch(PlayerIntent.QueueVisibilityChanged(true))
                        },
                        onOpenLibraryNavigationTarget = { target ->
                            component.playerStore.dispatch(PlayerIntent.ExpandedChanged(false))
                            pendingLibraryNavigationTarget = target
                            selectedTab = AppTab.Library
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
                pendingPlaylistTrack?.let { track ->
                    PlaylistAddDialog(
                        track = track,
                        targets = buildPlaylistAddTargets(
                            playlists = playlistsState.playlists,
                            favoriteTrackIds = favoritesState.favoriteTrackIds,
                            trackId = track.id,
                        ),
                        onDismiss = { pendingPlaylistTrack = null },
                        onAddTarget = { target ->
                            pendingPlaylistTrack = null
                            when (target.kind) {
                                PlaylistKind.SYSTEM_LIKED -> {
                                    component.favoritesStore.dispatch(
                                        FavoritesIntent.EnsureFavorite(
                                            track
                                        )
                                    )
                                }

                                PlaylistKind.USER -> {
                                    component.playlistsStore.dispatch(
                                        PlaylistsIntent.AddTrackToPlaylist(target.id, track),
                                    )
                                }
                            }
                        },
                        onCreatePlaylistAndAdd = { name ->
                            pendingPlaylistTrack = null
                            component.playlistsStore.dispatch(
                                PlaylistsIntent.CreatePlaylistAndAddTrack(name, track),
                            )
                        },
                    )
                }
                playerState.message?.let { message ->
                    ToastCard(
                        message = message,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = 24.dp)
                            .navigationBarsPadding(),
                    )
                }
                playlistsState.message?.let { message ->
                    ToastCard(
                        message = message,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = 84.dp)
                            .navigationBarsPadding(),
                    )
                }
            }
        }
    }
}
