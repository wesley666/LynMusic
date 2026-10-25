package top.iwesley.lyn.music.automotive.ui

import top.iwesley.lyn.music.*

import top.iwesley.lyn.music.core.model.ProvideUiLanguage

import top.iwesley.lyn.music.resources.*

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.AppTab
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaylistKind
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.resolveAppThemeTextPalette
import top.iwesley.lyn.music.core.model.resolveAppThemeTokens
import top.iwesley.lyn.music.feature.favorites.FavoritesIntent
import top.iwesley.lyn.music.feature.offline.OfflineDownloadIntent
import top.iwesley.lyn.music.feature.online.OnlineFavoritesIntent
import top.iwesley.lyn.music.feature.online.OnlineLibraryIntent
import top.iwesley.lyn.music.feature.online.OnlinePlaylistsIntent
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.playlists.PlaylistsIntent
import top.iwesley.lyn.music.feature.settings.SettingsEffect
import top.iwesley.lyn.music.feature.settings.SettingsIntent
import top.iwesley.lyn.music.ui.LynMusicTheme
import top.iwesley.lyn.music.ui.mainShellColors

internal val defaultSelectedAppTab: AppTab = AppTab.Library

@Composable
fun AutomotiveApp(
    component: LynMusicAppComponent,
    startupAutoOpenGate: StartupAutoOpenGate,
    onExitApplicationRequest: () -> Unit = {},
    startupWarning: UiText? = null,
) {
    ProvideUiLanguage {
        ConfigureLynArtworkImageLoader()

        DisposableEffect(component) {
            onDispose { component.dispose() }
        }

        val libraryState by component.libraryStore.state.collectAsState()
        val onlineLibraryState by component.onlineLibraryStore.state.collectAsState()
        val myState by component.myStore.state.collectAsState()
        val playlistsState by component.playlistsStore.state.collectAsState()
        val onlinePlaylistsState by component.onlinePlaylistsStore.state.collectAsState()
        val favoritesState by component.favoritesStore.state.collectAsState()
        val onlineFavoritesState by component.onlineFavoritesStore.state.collectAsState()
        val importState by component.importStore.state.collectAsState()
        val offlineDownloadState by component.offlineDownloadStore.state.collectAsState()
        val playerState by component.playerStore.state.collectAsState()
        val presentation = rememberPlayerPresentation(component.playerStore, playerState)
        // Keep completion feedback queued while a search/share dialog covers the player toast.
        val operationNotice = playerState.operationNotices.firstOrNull().takeIf {
            !presentation.isManualLyricsSearchVisible && !presentation.isLyricsShareVisible
        }
        LaunchedEffect(operationNotice?.id, playerState.message) {
            if (operationNotice != null && playerState.message == null) {
                kotlinx.coroutines.delay(2_500)
                component.playerStore.dispatch(PlayerIntent.DismissOperationNotice(operationNotice.id))
            }
        }
        val settingsState by component.settingsStore.state.collectAsState()
        var visibleStartupWarning by remember(startupWarning) { mutableStateOf(startupWarning) }
        var selectedTab by rememberSaveable { mutableStateOf(defaultSelectedAppTab) }
        var pendingPlaylistTrack by remember { mutableStateOf<Track?>(null) }
        var shouldRestoreOnlinePlaylistSource by remember { mutableStateOf(false) }
        var onlinePlaylistSourceRestoreTarget by remember { mutableStateOf<String?>(null) }
        var onlinePlaylistSourceRestoreObservedChange by remember { mutableStateOf(false) }
        var pendingLibraryNavigationTarget by remember { mutableStateOf<LibraryNavigationTarget?>(null) }
        var startupHydrationStarted by remember(component) { mutableStateOf(false) }
        val pendingOnlinePlaylistSourceId = remember(pendingPlaylistTrack, importState.sources) {
            pendingPlaylistTrack?.onlineNavidromeSourceIdOrNull(importState)
        }
        val playerFavoriteBinding = playerFavoriteBinding(
            track = playerState.effectiveSnapshot.currentTrack,
            localFavoriteTrackIds = favoritesState.favoriteTrackIds,
            onlineFavoritesState = onlineFavoritesState,
            importState = importState,
        )
        val onOnlineLibraryIntent: (OnlineLibraryIntent) -> Unit = remember(component) {
            { intent ->
                if (
                    intent.shouldClearRememberedOnlineLibrarySource(
                        currentOnlineSourceId = component.onlineLibraryStore.state.value.sourceId,
                        rememberedOnlineSourceId = component.onlineLibraryStore.rememberedSourceId,
                    )
                ) {
                    component.onlineLibraryStore.clearRememberedSource()
                } else {
                    if (intent.shouldStartOnlineLibraryStore()) {
                        component.onlineLibraryStore.ensureStarted()
                    }
                    component.onlineLibraryStore.dispatch(intent)
                }
            }
        }
        val onOnlineFavoritesIntent: (OnlineFavoritesIntent) -> Unit = remember(component) {
            { intent ->
                if (
                    intent.shouldClearRememberedOnlineFavoritesSource(
                        currentOnlineSourceId = component.onlineFavoritesStore.state.value.sourceId,
                        rememberedOnlineSourceId = component.onlineFavoritesStore.rememberedSourceId,
                    )
                ) {
                    component.onlineFavoritesStore.clearRememberedSource()
                } else {
                    if (intent.shouldStartOnlineFavoritesStore()) {
                        component.onlineFavoritesStore.ensureStarted()
                    }
                    component.onlineFavoritesStore.dispatch(intent)
                }
            }
        }
        val onOnlinePlaylistsIntent: (OnlinePlaylistsIntent) -> Unit = remember(component) {
            { intent ->
                if (
                    intent.shouldClearRememberedOnlinePlaylistsSource(
                        currentOnlineSourceId = component.onlinePlaylistsStore.state.value.sourceId,
                        rememberedOnlineSourceId = component.onlinePlaylistsStore.rememberedSourceId,
                    )
                ) {
                    component.onlinePlaylistsStore.clearRememberedSource()
                } else {
                    if (intent.shouldStartOnlinePlaylistsStore()) {
                        component.onlinePlaylistsStore.ensureStarted()
                    }
                    component.onlinePlaylistsStore.dispatch(intent)
                }
            }
        }
        LaunchedEffect(
            pendingPlaylistTrack,
            shouldRestoreOnlinePlaylistSource,
            onlinePlaylistSourceRestoreTarget,
            onlinePlaylistsState.sourceId,
        ) {
            if (
                shouldRestoreOnlinePlaylistSource &&
                onlinePlaylistsState.sourceId != onlinePlaylistSourceRestoreTarget
            ) {
                onlinePlaylistSourceRestoreObservedChange = true
            }
            if (pendingPlaylistTrack != null || !shouldRestoreOnlinePlaylistSource) {
                return@LaunchedEffect
            }
            if (onlinePlaylistsState.sourceId != onlinePlaylistSourceRestoreTarget) {
                onOnlinePlaylistsIntent(
                    OnlinePlaylistsIntent.SelectSource(
                        sourceId = onlinePlaylistSourceRestoreTarget,
                        persist = false,
                    ),
                )
            } else if (onlinePlaylistSourceRestoreObservedChange) {
                shouldRestoreOnlinePlaylistSource = false
                onlinePlaylistSourceRestoreTarget = null
                onlinePlaylistSourceRestoreObservedChange = false
            }
        }
        var pendingCastNotificationPermissionDeviceId by rememberSaveable(component) { mutableStateOf<String?>(null) }
        var castNotificationPermissionWarningShown by rememberSaveable(component) { mutableStateOf(false) }
        val appCoroutineScope = rememberCoroutineScope()
        fun openAddToPlaylist(track: Track?) {
            val onlineSourceId = track?.onlineNavidromeSourceIdOrNull(importState)
            if (onlineSourceId != null) {
                onlinePlaylistSourceRestoreTarget = onlinePlaylistsState.sourceId
                shouldRestoreOnlinePlaylistSource = true
                onlinePlaylistSourceRestoreObservedChange = onlinePlaylistsState.sourceId == onlineSourceId
                if (onlinePlaylistsState.sourceId != onlineSourceId) {
                    onOnlinePlaylistsIntent(
                        OnlinePlaylistsIntent.SelectSource(
                            sourceId = onlineSourceId,
                            persist = false,
                        ),
                    )
                }
            } else {
                shouldRestoreOnlinePlaylistSource = false
                onlinePlaylistSourceRestoreTarget = null
                onlinePlaylistSourceRestoreObservedChange = false
            }
            pendingPlaylistTrack = track
        }

        fun closeAddToPlaylist() {
            pendingPlaylistTrack = null
        }

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
                presentation.onPlaybackIntent(intent)
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
        val latestAddToPlaylist by rememberUpdatedState<(Track) -> Unit> { openAddToPlaylist(it) }
        val trackListBindings = remember(component, favoritesState.favoriteTrackIds,
            onlineFavoritesState, importState) {
            TrackListActionBindings(
                favoriteState = { track ->
                    val binding = playerFavoriteBinding(track, favoritesState.favoriteTrackIds, onlineFavoritesState, importState)
                    TrackFavoriteUiState(binding.isFavorite, binding.isFavoriteKnown,
                        binding.canToggleFavorite)
                },
                onSetFavorite = { track, favorite ->
                    val sourceId = track.onlineNavidromeSourceIdOrNull(importState)
                    if (sourceId != null) {
                        onOnlineFavoritesIntent(OnlineFavoritesIntent.SetFavorite(sourceId, track, favorite))
                    } else {
                        component.favoritesStore.dispatch(FavoritesIntent.ToggleFavorite(track))
                    }
                },
                onAddToPlaylist = { latestAddToPlaylist(it) },
            )
        }
        CompositionLocalProvider(
            LocalPlayerPresentation provides presentation,
            LocalTrackListActionBindings provides trackListBindings,
            LocalPlatformDescriptor provides component.platform,
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
                val resolvedTab = resolveAutomotiveTab(selectedTab)
                if (resolvedTab != selectedTab) {
                    selectedTab = resolvedTab
                }
            }
            LaunchedEffect(component) {
                withFrameNanos { }
                launch {
                    val restoration = startupAutoOpenGate.restore(
                        requested = settingsState.autoOpenPlayerOnStartup,
                    ) { component.playerStore.restorePlayback() }
                    if (restoration.autoOpenRequested && restoration.result.hasCurrentPlayback) {
                        presentation.expand()
                    }
                }
                component.settingsStore.dispatch(SettingsIntent.CheckAppUpdateSilently)
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

                        SettingsEffect.ExitApplicationRequested -> onExitApplicationRequest()
                    }
                }
            }
            LynMusicTheme(
                themeTokens = shellThemeTokens,
                textPalette = shellTextPalette,
            ) {
                visibleStartupWarning?.let { warning ->
                    AlertDialog(
                        onDismissRequest = { visibleStartupWarning = null },
                        title = { Text(uiString(Res.string.startup_old_data_cleanup_incomplete)) },
                        text = {
                            Text(
                                warning.displayText(),
                                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { visibleStartupWarning = null }) {
                                Text(uiString(Res.string.common_got_it))
                            }
                        },
                    )
                }
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
                    onlinePlaylistsState.message?.let { message ->
                        LaunchedEffect(message) {
                            kotlinx.coroutines.delay(2_500)
                            onOnlinePlaylistsIntent(OnlinePlaylistsIntent.ClearMessage)
                        }
                    }
                    onlinePlaylistsState.errorMessage?.let { message ->
                        LaunchedEffect(message) {
                            kotlinx.coroutines.delay(2_500)
                            onOnlinePlaylistsIntent(OnlinePlaylistsIntent.ClearMessage)
                        }
                    }
                    onlineFavoritesState.message?.let { message ->
                        LaunchedEffect(message) {
                            kotlinx.coroutines.delay(2_500)
                            onOnlineFavoritesIntent(OnlineFavoritesIntent.ClearMessage)
                        }
                    }
                    onlineFavoritesState.errorMessage?.let { message ->
                        LaunchedEffect(message) {
                            kotlinx.coroutines.delay(2_500)
                            onOnlineFavoritesIntent(OnlineFavoritesIntent.ClearMessage)
                        }
                    }
                    offlineDownloadState.message?.let { message ->
                        LaunchedEffect(message) {
                            kotlinx.coroutines.delay(2_500)
                            component.offlineDownloadStore.dispatch(OfflineDownloadIntent.ClearMessage)
                        }
                    }
                    val layoutProfile = AutomotiveLayoutProfile(maxWidth = maxWidth, maxHeight = maxHeight)
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
                            AutomotivePortraitShell(
                                portraitNavigation = layoutProfile.usesPortraitLibraryNavigation,
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it },
                                platform = component.platform,
                                myState = myState,
                                libraryState = libraryState,
                                onlineLibraryState = onlineLibraryState,
                                playlistsState = playlistsState,
                                onlinePlaylistsState = onlinePlaylistsState,
                                favoritesState = favoritesState,
                                onlineFavoritesState = onlineFavoritesState,
                                importState = importState,
                                playerState = playerState,
                                settingsState = settingsState,
                                onMyIntent = component.myStore::dispatch,
                                onLibraryIntent = component.libraryStore::dispatch,
                                onOnlineLibraryIntent = onOnlineLibraryIntent,
                                onPlaylistsIntent = component.playlistsStore::dispatch,
                                onOnlinePlaylistsIntent = onOnlinePlaylistsIntent,
                                onFavoritesIntent = component.favoritesStore::dispatch,
                                onOnlineFavoritesIntent = onOnlineFavoritesIntent,
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
                                hideMiniPlayerBar = false,
                                onOpenAddToPlaylist = {
                                    openAddToPlaylist(effectivePlayerSnapshot.currentTrack)
                                },
                            )
                        } else {
                            AutomotiveLandscapeShell(
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it },
                                platform = component.platform,
                                myState = myState,
                                libraryState = libraryState,
                                onlineLibraryState = onlineLibraryState,
                                playlistsState = playlistsState,
                                onlinePlaylistsState = onlinePlaylistsState,
                                favoritesState = favoritesState,
                                onlineFavoritesState = onlineFavoritesState,
                                importState = importState,
                                playerState = playerState,
                                settingsState = settingsState,
                                onMyIntent = component.myStore::dispatch,
                                onLibraryIntent = component.libraryStore::dispatch,
                                onOnlineLibraryIntent = onOnlineLibraryIntent,
                                onPlaylistsIntent = component.playlistsStore::dispatch,
                                onOnlinePlaylistsIntent = onOnlinePlaylistsIntent,
                                onFavoritesIntent = component.favoritesStore::dispatch,
                                onOnlineFavoritesIntent = onOnlineFavoritesIntent,
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
                                    openAddToPlaylist(effectivePlayerSnapshot.currentTrack)
                                },
                            )
                        }

                        LynMusicTheme(
                            themeTokens = shellThemeTokens,
                            textPalette = shellTextPalette,
                        ) {
                            PlayerDrawerHost(
                                visible = presentation.isExpanded,
                                platform = component.platform,
                                logger = component.logger,
                                state = playerState,
                                appDisplayScalePreset = settingsState.appDisplayScalePreset,
                                showCompactPlayerLyrics = settingsState.showCompactPlayerLyrics,
                                playerArtworkStyle = settingsState.playerArtworkStyle,
                                showEqualizerEntry = component.platform.capabilities.supportsEqualizer &&
                                    component.equalizerPlatformService.isSupported,
                                onOpenEqualizer = component.equalizerPlatformService::openEqualizer,
                                lyricsShareThemeTokens = shellThemeTokens,
                                lyricsShareTextPalette = shellTextPalette,
                                onPlayerIntent = onPlayerIntent,
                                isFavorite = playerFavoriteBinding.isFavorite,
                                canToggleFavorite = playerFavoriteBinding.canToggleFavorite,
                                onToggleFavorite = {
                                    if (playerFavoriteBinding.canToggleFavorite) {
                                        effectivePlayerSnapshot.currentTrack?.let { track ->
                                            val onlineSourceId = playerFavoriteBinding.onlineSourceId
                                            if (onlineSourceId != null) {
                                                onOnlineFavoritesIntent(
                                                    OnlineFavoritesIntent.SetFavorite(
                                                        sourceId = onlineSourceId,
                                                        track = track,
                                                        favorite = !playerFavoriteBinding.isFavorite,
                                                    )
                                                )
                                            } else {
                                                component.favoritesStore.dispatch(FavoritesIntent.ToggleFavorite(track))
                                            }
                                        }
                                    }
                                },
                                onOpenAddToPlaylist = {
                                    openAddToPlaylist(effectivePlayerSnapshot.currentTrack)
                                },
                                onOpenQueue = {
                                    presentation.showQueue(true)
                                },
                                onlineNavigationSourceId = playerFavoriteBinding.onlineSourceId,
                                onOpenLibraryNavigationTarget = { target ->
                                    presentation.collapse()
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
                            drawerSide = QueueDrawerSide.Start,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (presentation.isManualLyricsSearchVisible) {
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
                                    Text(uiString(Res.string.cast_notification_permission_title))
                                },
                                text = {
                                    Text(uiString(Res.string.cast_notification_permission_description))
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
                                        Text(uiString(Res.string.common_continue))
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
                                        Text(uiString(Res.string.common_cancel))
                                    }
                                },
                            )
                        }
                        pendingPlaylistTrack?.let { track ->
                            val onlinePlaylistSourceId = pendingOnlinePlaylistSourceId
                            val useOnlinePlaylistTargets = onlinePlaylistSourceId != null
                            val onlinePlaylistTargets = if (
                                useOnlinePlaylistTargets &&
                                onlinePlaylistsState.sourceId == onlinePlaylistSourceId
                            ) {
                                onlinePlaylistsState.playlists
                            } else {
                                emptyList()
                            }
                            PlaylistAddDialog(
                                track = track,
                                isLoadingTargets = if (useOnlinePlaylistTargets) {
                                    onlinePlaylistsState.sourceId != onlinePlaylistSourceId ||
                                        onlinePlaylistsState.isLoading ||
                                        onlinePlaylistsState.isMutating
                                } else {
                                    playlistsState.isLoadingContent
                                },
                                targets = if (useOnlinePlaylistTargets) {
                                    buildPlaylistAddTargets(
                                        playlists = onlinePlaylistTargets,
                                        favoriteTrackIds = emptySet(),
                                        trackId = track.id,
                                        includeLiked = false,
                                    )
                                } else {
                                    buildPlaylistAddTargets(
                                        playlists = playlistsState.playlists,
                                        favoriteTrackIds = favoritesState.favoriteTrackIds,
                                        trackId = track.id,
                                    )
                                },
                                compact = compact,
                                onDismiss = ::closeAddToPlaylist,
                                onAddTarget = { target ->
                                    closeAddToPlaylist()
                                    if (useOnlinePlaylistTargets) {
                                        if (target.kind == PlaylistKind.USER) {
                                            onOnlinePlaylistsIntent(
                                                OnlinePlaylistsIntent.AddTrack(
                                                    playlistId = target.id,
                                                    track = track,
                                                    sourceId = onlinePlaylistSourceId,
                                                ),
                                            )
                                        }
                                    } else {
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
                                    }
                                },
                                onCreatePlaylistAndAdd = { name ->
                                    closeAddToPlaylist()
                                    if (useOnlinePlaylistTargets) {
                                        onOnlinePlaylistsIntent(
                                            OnlinePlaylistsIntent.CreatePlaylistAndAddTrack(
                                                name = name,
                                                track = track,
                                                sourceId = onlinePlaylistSourceId,
                                            ),
                                        )
                                    } else {
                                        component.playlistsStore.dispatch(
                                            PlaylistsIntent.CreatePlaylistAndAddTrack(name, track),
                                        )
                                    }
                                },
                            )
                        }
                        (playerState.message ?: operationNotice?.message)?.let { message ->
                            ToastCard(
                                message = message,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 20.dp, vertical = 24.dp)
                                    .navigationBarsPadding(),
                            )
                        }
                        val secondaryNotice = secondaryToastMessage(
                            onlineFavoritesErrorMessage = onlineFavoritesState.errorMessage,
                            onlinePlaylistsErrorMessage = onlinePlaylistsState.errorMessage,
                            playlistsMessage = playlistsState.message,
                            onlineFavoritesMessage = onlineFavoritesState.message,
                            onlinePlaylistsMessage = onlinePlaylistsState.message,
                        )
                        secondaryNotice?.let { message ->
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
}

internal fun <T> secondaryToastMessage(
    onlineFavoritesErrorMessage: T?,
    onlinePlaylistsErrorMessage: T?,
    playlistsMessage: T?,
    onlineFavoritesMessage: T?,
    onlinePlaylistsMessage: T?,
): T? {
    return onlineFavoritesErrorMessage
        ?: onlinePlaylistsErrorMessage
        ?: playlistsMessage
        ?: onlineFavoritesMessage
        ?: onlinePlaylistsMessage
}

internal fun OnlineLibraryIntent.isPassiveLibrarySourceClear(): Boolean {
    return this is OnlineLibraryIntent.SelectSource && persist && sourceId.isNullOrBlank()
}

internal fun OnlineLibraryIntent.shouldClearRememberedOnlineLibrarySource(
    currentOnlineSourceId: String?,
    rememberedOnlineSourceId: String?,
): Boolean {
    return isPassiveLibrarySourceClear() &&
        currentOnlineSourceId == null &&
        !rememberedOnlineSourceId.isNullOrBlank()
}

internal fun OnlineLibraryIntent.shouldStartOnlineLibraryStore(): Boolean {
    return when (this) {
        is OnlineLibraryIntent.SelectSource -> !sourceId.isNullOrBlank()
        OnlineLibraryIntent.ClearError -> false
        else -> true
    }
}

internal fun OnlineFavoritesIntent.isPassiveFavoritesSourceClear(): Boolean {
    return this is OnlineFavoritesIntent.SelectSource && persist && sourceId.isNullOrBlank()
}

internal fun OnlineFavoritesIntent.shouldClearRememberedOnlineFavoritesSource(
    currentOnlineSourceId: String?,
    rememberedOnlineSourceId: String?,
): Boolean {
    return isPassiveFavoritesSourceClear() &&
        currentOnlineSourceId == null &&
        !rememberedOnlineSourceId.isNullOrBlank()
}

internal fun OnlineFavoritesIntent.shouldStartOnlineFavoritesStore(): Boolean {
    return when (this) {
        is OnlineFavoritesIntent.SelectSource -> !sourceId.isNullOrBlank()
        OnlineFavoritesIntent.ClearMessage -> false
        else -> true
    }
}

internal fun OnlinePlaylistsIntent.isPassivePlaylistsSourceClear(): Boolean {
    return this is OnlinePlaylistsIntent.SelectSource && persist && sourceId.isNullOrBlank()
}

internal fun OnlinePlaylistsIntent.shouldClearRememberedOnlinePlaylistsSource(
    currentOnlineSourceId: String?,
    rememberedOnlineSourceId: String?,
): Boolean {
    return isPassivePlaylistsSourceClear() &&
        currentOnlineSourceId == null &&
        !rememberedOnlineSourceId.isNullOrBlank()
}

internal fun OnlinePlaylistsIntent.shouldStartOnlinePlaylistsStore(): Boolean {
    return when (this) {
        is OnlinePlaylistsIntent.SelectSource -> !sourceId.isNullOrBlank()
        is OnlinePlaylistsIntent.SelectPlaylist -> playlistId != null
        OnlinePlaylistsIntent.ClearMessage,
        OnlinePlaylistsIntent.ClearPlaylistImportReport -> false
        else -> true
    }
}

private fun activateStartupStores(
    component: LynMusicAppComponent,
    selectedTab: AppTab,
    pendingPlaylistTrack: Track?,
) {
    when (resolveAutomotiveTab(selectedTab)) {
        AppTab.My -> component.myStore.ensureStarted()
        AppTab.Library -> {
            component.libraryStore.ensureStarted()
            component.onlineLibraryStore.ensureStartedIfRememberedSource()
        }
        AppTab.Favorites -> {
            component.favoritesStore.ensureContentStarted()
            component.onlineFavoritesStore.ensureStartedIfRememberedSource()
        }
        AppTab.Playlists -> {
            component.playlistsStore.ensureContentStarted()
            component.onlinePlaylistsStore.ensureStartedIfRememberedSource()
        }
        AppTab.Tags -> Unit
        AppTab.Sources, AppTab.Settings -> Unit
    }
    if (pendingPlaylistTrack != null) {
        component.playlistsStore.ensureContentStarted()
    }
}
