package top.iwesley.lyn.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import top.iwesley.lyn.music.cast.CastBackgroundRunSettingsOpener
import top.iwesley.lyn.music.cast.CastNotificationPermissionRequester
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.AppTab
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaylistKind
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.resolveAppThemeTextPalette
import top.iwesley.lyn.music.core.model.resolveAppThemeTokens
import top.iwesley.lyn.music.data.repository.DefaultPlaybackRepository
import top.iwesley.lyn.music.data.repository.LyricsRepository
import top.iwesley.lyn.music.data.repository.PlayerRuntimeServices
import top.iwesley.lyn.music.feature.favorites.FavoritesIntent
import top.iwesley.lyn.music.feature.favorites.FavoritesStore
import top.iwesley.lyn.music.feature.importing.ImportStore
import top.iwesley.lyn.music.feature.library.LibraryStore
import top.iwesley.lyn.music.feature.my.MyStore
import top.iwesley.lyn.music.feature.offline.OfflineDownloadIntent
import top.iwesley.lyn.music.feature.offline.OfflineDownloadStore
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.player.PlayerStore
import top.iwesley.lyn.music.feature.playlists.PlaylistsIntent
import top.iwesley.lyn.music.feature.playlists.PlaylistsStore
import top.iwesley.lyn.music.feature.settings.SettingsEffect
import top.iwesley.lyn.music.feature.settings.SettingsStore
import top.iwesley.lyn.music.feature.tags.MusicTagsStore
import top.iwesley.lyn.music.ui.LynMusicTheme
import top.iwesley.lyn.music.ui.mainShellColors

internal val defaultSelectedAppTab: AppTab = AppTab.Library

class LynMusicAppComponent(
    val platform: PlatformDescriptor,
    val logger: DiagnosticLogger,
    val myStore: MyStore,
    val libraryStore: LibraryStore,
    val playlistsStore: PlaylistsStore,
    val favoritesStore: FavoritesStore,
    val musicTagsStore: MusicTagsStore,
    val importStore: ImportStore,
    val offlineDownloadStore: OfflineDownloadStore,
    val playerStore: PlayerStore,
    val settingsStore: SettingsStore,
    val lyricsRepository: LyricsRepository,
    val artworkCacheStore: ArtworkCacheStore,
    val appDisplayScalePreset: StateFlow<AppDisplayScalePreset>,
    val castBackgroundRunSettingsOpener: CastBackgroundRunSettingsOpener,
    val castNotificationPermissionRequester: CastNotificationPermissionRequester,
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
        playbackPreferencesStore = playerRuntimeServices.playbackPreferencesStore,
        scope = sharedGraph.scope,
        systemPlaybackControlsPlatformService = playerRuntimeServices.systemPlaybackControlsPlatformService,
        logger = sharedGraph.logger,
        playbackStatsReporter = sharedGraph.playbackStatsReporter,
        hydrateImmediately = false,
    )
    return LynMusicAppComponent(
        platform = sharedGraph.platform,
        logger = sharedGraph.logger,
        myStore = sharedGraph.myStore,
        libraryStore = sharedGraph.libraryStore,
        playlistsStore = sharedGraph.playlistsStore,
        favoritesStore = sharedGraph.favoritesStore,
        musicTagsStore = sharedGraph.musicTagsStore,
        importStore = sharedGraph.importStore,
        offlineDownloadStore = sharedGraph.offlineDownloadStore,
        playerStore = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = sharedGraph.lyricsRepository,
            storeScope = sharedGraph.scope,
            castGateway = playerRuntimeServices.castGateway,
            castMediaUrlResolver = playerRuntimeServices.castMediaUrlResolver,
            castSessionForegroundPlatformService = playerRuntimeServices.castSessionForegroundPlatformService,
            lyricsSharePlatformService = playerRuntimeServices.lyricsSharePlatformService,
            lyricsShareFontLibraryPlatformService = playerRuntimeServices.lyricsShareFontLibraryPlatformService,
            lyricsShareFontPreferencesStore = playerRuntimeServices.lyricsShareFontPreferencesStore,
            artworkCacheStore = sharedGraph.artworkCacheStore,
            logger = sharedGraph.logger,
        ),
        settingsStore = sharedGraph.settingsStore,
        lyricsRepository = sharedGraph.lyricsRepository,
        artworkCacheStore = sharedGraph.artworkCacheStore,
        appDisplayScalePreset = sharedGraph.appDisplayScalePreset,
        castBackgroundRunSettingsOpener = playerRuntimeServices.castBackgroundRunSettingsOpener,
        castNotificationPermissionRequester = playerRuntimeServices.castNotificationPermissionRequester,
        scope = sharedGraph.scope,
        onDispose = {
            playerRuntimeServices.castSessionForegroundPlatformService.close()
            playerRuntimeServices.castGateway.release()
            playerRuntimeServices.castMediaUrlResolver.release()
            playbackRepository.close()
        },
    )
}

@Composable
fun App(
    component: LynMusicAppComponent,
    desktopWindowChrome: DesktopWindowChrome = DesktopWindowChrome(),
) {
    ConfigureLynArtworkImageLoader()

    DisposableEffect(component) {
        onDispose { component.dispose() }
    }

    val libraryState by component.libraryStore.state.collectAsState()
    val myState by component.myStore.state.collectAsState()
    val playlistsState by component.playlistsStore.state.collectAsState()
    val favoritesState by component.favoritesStore.state.collectAsState()
    val musicTagsState by component.musicTagsStore.state.collectAsState()
    val importState by component.importStore.state.collectAsState()
    val offlineDownloadState by component.offlineDownloadStore.state.collectAsState()
    val playerState by component.playerStore.state.collectAsState()
    val settingsState by component.settingsStore.state.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(defaultSelectedAppTab) }
    var pendingPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var pendingLibraryNavigationTarget by remember { mutableStateOf<LibraryNavigationTarget?>(null) }
    var isMusicTagsMobileEditorVisible by rememberSaveable { mutableStateOf(false) }
    var startupHydrationStarted by remember(component) { mutableStateOf(false) }
    var pendingCastNotificationPermissionDeviceId by rememberSaveable(component) { mutableStateOf<String?>(null) }
    var castNotificationPermissionWarningShown by rememberSaveable(component) { mutableStateOf(false) }
    val appCoroutineScope = rememberCoroutineScope()
    fun showCastNotificationPermissionWarningOnce() {
        if (!castNotificationPermissionWarningShown) {
            castNotificationPermissionWarningShown = true
            component.playerStore.dispatch(PlayerIntent.CastNotificationPermissionDenied)
        }
    }

    fun continueCastWithoutRequestingNotificationPermission(deviceId: String) {
        pendingCastNotificationPermissionDeviceId = null
        showCastNotificationPermissionWarningOnce()
        component.playerStore.dispatch(PlayerIntent.CastToDevice(deviceId))
    }

    val onPlayerIntent: (PlayerIntent) -> Unit = remember(component, appCoroutineScope) {
        { intent ->
            if (intent is PlayerIntent.CastToDevice) {
                if (component.castNotificationPermissionRequester.isRequestNeeded()) {
                    pendingCastNotificationPermissionDeviceId = intent.deviceId
                } else {
                    component.playerStore.dispatch(intent)
                }
            } else {
                component.playerStore.dispatch(intent)
            }
        }
    }
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

    CompositionLocalProvider(
        LocalPlatformDescriptor provides component.platform,
        LocalDesktopWindowChrome provides desktopWindowChrome,
        LocalArtworkCacheStore provides component.artworkCacheStore,
        LocalOfflineDownloadUiState provides OfflineDownloadUiState(
            downloadsByTrackId = offlineDownloadState.downloadsByTrackId,
            availableSpaceBytes = offlineDownloadState.availableSpaceBytes,
            availableSpaceLoading = offlineDownloadState.availableSpaceLoading,
            activeBatchDownload = offlineDownloadState.activeBatchDownload,
            onIntent = component.offlineDownloadStore::dispatch,
        ),
    ) {
        LaunchedEffect(component.platform, selectedTab) {
            val resolvedTab = resolveAppTabForPlatform(selectedTab, component.platform)
            if (resolvedTab != selectedTab) {
                selectedTab = resolvedTab
            }
        }
        LaunchedEffect(component) {
            withFrameNanos { }
            component.playerStore.startHydration()
            activateStartupStores(
                component = component,
                selectedTab = selectedTab,
                pendingPlaylistTrack = pendingPlaylistTrack,
            )
            startupHydrationStarted = true
        }
        LaunchedEffect(startupHydrationStarted, selectedTab, pendingPlaylistTrack) {
            if (!startupHydrationStarted) return@LaunchedEffect
            activateStartupStores(
                component = component,
                selectedTab = selectedTab,
                pendingPlaylistTrack = pendingPlaylistTrack,
            )
        }
        LaunchedEffect(component) {
            component.settingsStore.effects.collect { effect ->
                when (effect) {
                    SettingsEffect.LyricsShareFontsChanged ->
                        component.playerStore.dispatch(PlayerIntent.InvalidateLyricsShareFontCache)
                }
            }
        }
        LynMusicTheme(
            themeTokens = shellThemeTokens,
            textPalette = shellTextPalette,
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
            ) {
                val density = LocalDensity.current
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
                offlineDownloadState.message?.let { message ->
                    LaunchedEffect(message) {
                        kotlinx.coroutines.delay(2_500)
                        component.offlineDownloadStore.dispatch(OfflineDownloadIntent.ClearMessage)
                    }
                }
                val layoutProfile = buildLayoutProfile(
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                    platform = component.platform,
                    density = density,
                )
                val compact = layoutProfile.isCompactLayout
                val mobilePortraitMiniPlayer = layoutProfile.isCompactLayout
                val shellColors = mainShellColors
                val effectivePlayerSnapshot = playerState.effectiveSnapshot
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
                            myState = myState,
                            libraryState = libraryState,
                            playlistsState = playlistsState,
                            favoritesState = favoritesState,
                            musicTagsState = musicTagsState,
                            musicTagsEffects = component.musicTagsStore.effects,
                            importState = importState,
                            playerState = playerState,
                            settingsState = settingsState,
                            onMyIntent = component.myStore::dispatch,
                            onLibraryIntent = component.libraryStore::dispatch,
                            onPlaylistsIntent = component.playlistsStore::dispatch,
                            onFavoritesIntent = component.favoritesStore::dispatch,
                            onMusicTagsIntent = component.musicTagsStore::dispatch,
                            onImportIntent = component.importStore::dispatch,
                            onPlayerIntent = onPlayerIntent,
                            onSettingsIntent = component.settingsStore::dispatch,
                            onOpenBackgroundRunSettings = component.castBackgroundRunSettingsOpener::openSettings,
                            libraryNavigationTarget = pendingLibraryNavigationTarget,
                            onLibraryNavigationHandled = { pendingLibraryNavigationTarget = null },
                            onOpenLibraryNavigationTarget = { target ->
                                pendingLibraryNavigationTarget = target
                                selectedTab = AppTab.Library
                            },
                            mobilePortraitMiniPlayer = mobilePortraitMiniPlayer,
                            hideMiniPlayerBar = selectedTab == AppTab.Tags && isMusicTagsMobileEditorVisible,
                            onMobileEditorVisibilityChanged = {
                                isMusicTagsMobileEditorVisible = it
                            },
                            onOpenAddToPlaylist = {
                                pendingPlaylistTrack = effectivePlayerSnapshot.currentTrack
                            },
                        )
                    } else {
                        DesktopShell(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            platform = component.platform,
                            myState = myState,
                            libraryState = libraryState,
                            playlistsState = playlistsState,
                            favoritesState = favoritesState,
                            musicTagsState = musicTagsState,
                            musicTagsEffects = component.musicTagsStore.effects,
                            importState = importState,
                            playerState = playerState,
                            settingsState = settingsState,
                            onMyIntent = component.myStore::dispatch,
                            onLibraryIntent = component.libraryStore::dispatch,
                            onPlaylistsIntent = component.playlistsStore::dispatch,
                            onFavoritesIntent = component.favoritesStore::dispatch,
                            onMusicTagsIntent = component.musicTagsStore::dispatch,
                            onImportIntent = component.importStore::dispatch,
                            onPlayerIntent = onPlayerIntent,
                            onSettingsIntent = component.settingsStore::dispatch,
                            onOpenBackgroundRunSettings = component.castBackgroundRunSettingsOpener::openSettings,
                            libraryNavigationTarget = pendingLibraryNavigationTarget,
                            onLibraryNavigationHandled = { pendingLibraryNavigationTarget = null },
                            onOpenLibraryNavigationTarget = { target ->
                                pendingLibraryNavigationTarget = target
                                selectedTab = AppTab.Library
                            },
                            onOpenAddToPlaylist = {
                                pendingPlaylistTrack = effectivePlayerSnapshot.currentTrack
                            },
                        )
                    }

                    LynMusicTheme(
                        themeTokens = shellThemeTokens,
                        textPalette = shellTextPalette,
                    ) {
                        PlayerDrawerHost(
                            visible = playerState.isExpanded,
                            platform = component.platform,
                            logger = component.logger,
                            state = playerState,
                            showCompactPlayerLyrics = settingsState.showCompactPlayerLyrics,
                            lyricsShareThemeTokens = shellThemeTokens,
                            lyricsShareTextPalette = shellTextPalette,
                            onPlayerIntent = onPlayerIntent,
                            isFavorite = effectivePlayerSnapshot.currentTrack?.id in favoritesState.favoriteTrackIds,
                            onToggleFavorite = {
                                effectivePlayerSnapshot.currentTrack?.let { track ->
                                    component.favoritesStore.dispatch(
                                        FavoritesIntent.ToggleFavorite(
                                            track
                                        )
                                    )
                                }
                            },
                            onOpenAddToPlaylist = {
                                pendingPlaylistTrack = effectivePlayerSnapshot.currentTrack
                            },
                            onOpenQueue = {
                                component.playerStore.dispatch(
                                    PlayerIntent.QueueVisibilityChanged(
                                        true
                                    )
                                )
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
                        onPlayerIntent = onPlayerIntent,
                        drawerSide = if (component.platform.isAndroidAutomotivePlatform()) {
                            QueueDrawerSide.Start
                        } else {
                            QueueDrawerSide.End
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (playerState.isManualLyricsSearchVisible) {
                        ManualLyricsSearchOverlay(
                            state = playerState,
                            onPlayerIntent = onPlayerIntent,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    pendingCastNotificationPermissionDeviceId?.let { deviceId ->
                        AlertDialog(
                            onDismissRequest = {
                                continueCastWithoutRequestingNotificationPermission(deviceId)
                            },
                            shape = RoundedCornerShape(28.dp),
                            containerColor = shellColors.cardContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            title = {
                                Text("允许投屏通知")
                            },
                            text = {
                                Text("支持应用退到后台后仍然能发起投屏下一首音乐")
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        pendingCastNotificationPermissionDeviceId = null
                                        appCoroutineScope.launch {
                                            val granted =
                                                component.castNotificationPermissionRequester.requestIfNeeded()
                                            if (!granted) {
                                                showCastNotificationPermissionWarningOnce()
                                            }
                                            component.playerStore.dispatch(PlayerIntent.CastToDevice(deviceId))
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary,
                                    ),
                                ) {
                                    Text("继续")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        continueCastWithoutRequestingNotificationPermission(deviceId)
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                ) {
                                    Text("取消")
                                }
                            },
                        )
                    }
                    pendingPlaylistTrack?.let { track ->
                        PlaylistAddDialog(
                            track = track,
                            isLoadingTargets = playlistsState.isLoadingContent,
                            targets = buildPlaylistAddTargets(
                                playlists = playlistsState.playlists,
                                favoriteTrackIds = favoritesState.favoriteTrackIds,
                                trackId = track.id,
                            ),
                            compact = compact,
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
                    offlineDownloadState.message?.let { message ->
                        ToastCard(
                            message = message,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 20.dp, vertical = 144.dp)
                                .navigationBarsPadding(),
                        )
                    }
                }
            }
        }
    }
}

private fun activateStartupStores(
    component: LynMusicAppComponent,
    selectedTab: AppTab,
    pendingPlaylistTrack: Track?,
) {
    when (resolveAppTabForPlatform(selectedTab, component.platform)) {
        AppTab.My -> component.myStore.ensureStarted()
        AppTab.Library -> component.libraryStore.ensureStarted()
        AppTab.Favorites -> component.favoritesStore.ensureContentStarted()
        AppTab.Playlists -> component.playlistsStore.ensureContentStarted()
        AppTab.Tags -> component.musicTagsStore.ensureStarted()
        AppTab.Sources, AppTab.Settings -> Unit
    }
    if (pendingPlaylistTrack != null) {
        component.playlistsStore.ensureContentStarted()
    }
}
