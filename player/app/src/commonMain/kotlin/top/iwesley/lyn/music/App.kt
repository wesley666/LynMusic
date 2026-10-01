package top.iwesley.lyn.music

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import top.iwesley.lyn.music.cast.CastBackgroundRunSettingsOpener
import top.iwesley.lyn.music.cast.CastNotificationPermissionRequester
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.AppTab
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.CompositeSystemPlaybackControlsPlatformService
import top.iwesley.lyn.music.core.model.DesktopLyricsPlatformService
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.EqualizerPlatformService
import top.iwesley.lyn.music.core.model.MenuBarLyricsControlsPlatformService
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaylistKind
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.UiText
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
import top.iwesley.lyn.music.feature.online.OnlineFavoritesIntent
import top.iwesley.lyn.music.feature.online.OnlineFavoritesStore
import top.iwesley.lyn.music.feature.online.OnlineLibraryIntent
import top.iwesley.lyn.music.feature.online.OnlineLibraryStore
import top.iwesley.lyn.music.feature.online.OnlinePlaylistsIntent
import top.iwesley.lyn.music.feature.online.OnlinePlaylistsStore
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.feature.player.PlayerStore
import top.iwesley.lyn.music.feature.player.DESKTOP_LYRICS_LOADING_TEXT
import top.iwesley.lyn.music.feature.player.resolveDesktopLyricsOverlayText
import top.iwesley.lyn.music.feature.playlists.PlaylistsIntent
import top.iwesley.lyn.music.feature.playlists.PlaylistsStore
import top.iwesley.lyn.music.feature.settings.SettingsEffect
import top.iwesley.lyn.music.feature.settings.SettingsIntent
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
    val onlineLibraryStore: OnlineLibraryStore,
    val playlistsStore: PlaylistsStore,
    val onlinePlaylistsStore: OnlinePlaylistsStore,
    val favoritesStore: FavoritesStore,
    val onlineFavoritesStore: OnlineFavoritesStore,
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
    val desktopLyricsPlatformService: DesktopLyricsPlatformService,
    val equalizerPlatformService: EqualizerPlatformService,
    private val scope: CoroutineScope,
    private val onDispose: suspend () -> Unit,
) {
    private val resourceDisposer = AppResourceDisposer(
        scope = scope,
        logger = logger,
        onDispose = onDispose,
    )

    fun dispose(): Result<Unit> = resourceDisposer.dispose()
}

internal class AppResourceDisposer(
    private val scope: CoroutineScope,
    private val logger: DiagnosticLogger,
    private val scopeShutdownTimeoutMillis: Long = APP_SCOPE_SHUTDOWN_TIMEOUT_MILLIS,
    private val onDispose: suspend () -> Unit,
) {
    private val disposeMutex = Mutex()
    private var disposeResult: Result<Unit>? = null

    fun dispose(): Result<Unit> = runBlocking {
        disposeMutex.withLock {
            disposeResult ?: runCatching {
                closeAppResourcesBestEffort(
                    logger = logger,
                    resources = listOf(
                        AppCloseAction("shared-scope") {
                            stopAppScope(scope, scopeShutdownTimeoutMillis)
                        },
                        AppCloseAction("owned-resources") { onDispose() },
                    ),
                )
            }.also { result ->
                disposeResult = result
                result.exceptionOrNull()?.let { error ->
                    runCatching {
                        logger.error(APP_DISPOSE_LOG_TAG, error) { "应用资源释放未完全成功。" }
                    }
                }
            }
        }
    }
}

internal suspend fun stopAppScope(
    scope: CoroutineScope,
    timeoutMillis: Long = APP_SCOPE_SHUTDOWN_TIMEOUT_MILLIS,
) {
    val job = scope.coroutineContext[Job] ?: return
    withTimeout(timeoutMillis.coerceAtLeast(1L)) {
        job.cancelAndJoin()
    }
}

fun buildPlayerAppComponent(
    sharedGraph: SharedGraph,
    playerRuntimeServices: PlayerRuntimeServices,
): LynMusicAppComponent {
    val systemPlaybackControlsPlatformService = CompositeSystemPlaybackControlsPlatformService(
        listOf(
            playerRuntimeServices.systemPlaybackControlsPlatformService,
            playerRuntimeServices.menuBarLyricsControlsPlatformService,
        ),
    )
    val playbackRepository = playerRuntimeServices.playbackRepository ?: DefaultPlaybackRepository(
        database = sharedGraph.database,
        gateway = requireNotNull(playerRuntimeServices.playbackGateway) {
            "PlayerRuntimeServices must provide playbackGateway or playbackRepository"
        },
        playbackPreferencesStore = playerRuntimeServices.playbackPreferencesStore,
        scope = sharedGraph.scope,
        systemPlaybackControlsPlatformService = systemPlaybackControlsPlatformService,
        logger = sharedGraph.logger,
        playbackStatsReporter = sharedGraph.playbackStatsReporter,
        hydrateImmediately = false,
    )
    val playerStore = PlayerStore(
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
    )
    sharedGraph.scope.launchDesktopLyricsSync(
        settingsStore = sharedGraph.settingsStore,
        playerStore = playerStore,
        desktopLyricsPlatformService = sharedGraph.desktopLyricsPlatformService,
    )
    sharedGraph.scope.launchMenuBarLyricsControlsSync(
        settingsStore = sharedGraph.settingsStore,
        playerStore = playerStore,
        menuBarLyricsControlsPlatformService = playerRuntimeServices.menuBarLyricsControlsPlatformService,
    )
    return LynMusicAppComponent(
        platform = sharedGraph.platform,
        logger = sharedGraph.logger,
        myStore = sharedGraph.myStore,
        libraryStore = sharedGraph.libraryStore,
        onlineLibraryStore = sharedGraph.onlineLibraryStore,
        playlistsStore = sharedGraph.playlistsStore,
        onlinePlaylistsStore = sharedGraph.onlinePlaylistsStore,
        favoritesStore = sharedGraph.favoritesStore,
        onlineFavoritesStore = sharedGraph.onlineFavoritesStore,
        musicTagsStore = sharedGraph.musicTagsStore,
        importStore = sharedGraph.importStore,
        offlineDownloadStore = sharedGraph.offlineDownloadStore,
        playerStore = playerStore,
        settingsStore = sharedGraph.settingsStore,
        lyricsRepository = sharedGraph.lyricsRepository,
        artworkCacheStore = sharedGraph.artworkCacheStore,
        appDisplayScalePreset = sharedGraph.appDisplayScalePreset,
        castBackgroundRunSettingsOpener = playerRuntimeServices.castBackgroundRunSettingsOpener,
        castNotificationPermissionRequester = playerRuntimeServices.castNotificationPermissionRequester,
        desktopLyricsPlatformService = sharedGraph.desktopLyricsPlatformService,
        equalizerPlatformService = playerRuntimeServices.equalizerPlatformService,
        scope = sharedGraph.scope,
        onDispose = {
            closeAppResourcesBestEffort(
                logger = sharedGraph.logger,
                resources = listOf(
                    AppCloseAction("cast-session") { playerRuntimeServices.castSessionForegroundPlatformService.close() },
                    AppCloseAction("cast-gateway") { playerRuntimeServices.castGateway.release() },
                    AppCloseAction("cast-media-resolver") { playerRuntimeServices.castMediaUrlResolver.release() },
                    AppCloseAction("desktop-lyrics") { sharedGraph.desktopLyricsPlatformService.release() },
                    AppCloseAction("menu-bar-controls") { playerRuntimeServices.menuBarLyricsControlsPlatformService.close() },
                    AppCloseAction("playback-repository") { playbackRepository.close() },
                    AppCloseAction("desktop-resources") { playerRuntimeServices.closeDesktopResources() },
                ),
            )
        },
    )
}

private const val APP_DISPOSE_LOG_TAG = "AppDispose"
internal const val APP_SCOPE_SHUTDOWN_TIMEOUT_MILLIS = 2_000L

internal data class AppCloseAction(
    val name: String,
    val close: suspend () -> Unit,
)

internal suspend fun closeAppResourcesBestEffort(
    logger: DiagnosticLogger,
    resources: List<AppCloseAction>,
) {
    val failures = mutableListOf<Throwable>()
    resources.forEach { resource ->
        runCatching { resource.close() }.onFailure { error ->
            failures += error
            runCatching {
                logger.error(APP_DISPOSE_LOG_TAG, error) { "关闭资源失败：${resource.name}" }
            }.exceptionOrNull()?.let { loggingError -> error.addSuppressedSafely(loggingError) }
        }
    }
    failures.firstOrNull()?.let { primary ->
        failures.drop(1).forEach { failure -> primary.addSuppressedSafely(failure) }
        throw primary
    }
}

private fun Throwable.addSuppressedSafely(failure: Throwable) {
    if (failure === this || suppressedExceptions.any { suppressed -> suppressed === failure }) return
    runCatching { addSuppressed(failure) }
}

private fun CoroutineScope.launchDesktopLyricsSync(
    settingsStore: SettingsStore,
    playerStore: PlayerStore,
    desktopLyricsPlatformService: DesktopLyricsPlatformService,
) {
    if (!desktopLyricsPlatformService.isSupported) return
    launch {
        desktopLyricsPlatformService.closeRequests.collect {
            settingsStore.dispatch(SettingsIntent.ShowDesktopLyricsChanged(false))
        }
    }
    if (!desktopLyricsPlatformService.consumesAppLyricsUpdates) return
    launch {
        var lastEnabled = false
        var lastText: String? = null
        combine(settingsStore.state, playerStore.state, top.iwesley.lyn.music.core.model.AppLanguageRuntime.effectiveLanguageRevision) { settings, player, _ -> settings to player }
            .collectLatest { (settings, player) ->
                val enabled = settings.showDesktopLyrics && desktopLyricsPlatformService.hasOverlayPermission()
                if (!enabled) {
                    if (lastEnabled || lastText != null) {
                        desktopLyricsPlatformService.setDesktopLyricsEnabled(false)
                        desktopLyricsPlatformService.hideLyrics()
                    }
                    lastEnabled = false
                    lastText = null
                    return@collectLatest
                }
                if (!lastEnabled) {
                    desktopLyricsPlatformService.setDesktopLyricsEnabled(true)
                    lastEnabled = true
                }
                val text = resolveDesktopLyricsOverlayText(
                    lyrics = player.lyrics,
                    highlightedLineIndex = player.highlightedLineIndex,
                    isLyricsLoading = player.isLyricsLoading,
                )?.let { top.iwesley.lyn.music.core.model.resolveUiText(it, top.iwesley.lyn.music.core.model.AppLanguageRuntime.effectiveLanguage.value) }
                if (text == null) {
                    if (lastText != null) {
                        desktopLyricsPlatformService.hideLyrics()
                    }
                    lastText = null
                } else if (text != lastText) {
                    desktopLyricsPlatformService.updateLyrics(text)
                    lastText = text
                }
            }
    }
}

private fun CoroutineScope.launchMenuBarLyricsControlsSync(
    settingsStore: SettingsStore,
    playerStore: PlayerStore,
    menuBarLyricsControlsPlatformService: MenuBarLyricsControlsPlatformService,
) {
    if (!menuBarLyricsControlsPlatformService.isSupported) return
    launch {
        var lastEnabled = false
        var lastText: String? = null
        var lastTrackId: String? = null
        combine(settingsStore.state, playerStore.state, top.iwesley.lyn.music.core.model.AppLanguageRuntime.effectiveLanguageRevision) { settings, player, _ -> settings to player }
            .collectLatest { (settings, player) ->
                val enabled = settings.showMenuBarLyricsControls
                if (!enabled) {
                    if (lastEnabled || lastText != null) {
                        menuBarLyricsControlsPlatformService.updateLyrics(null)
                        menuBarLyricsControlsPlatformService.setEnabled(false)
                    }
                    lastEnabled = false
                    lastText = null
                    lastTrackId = null
                    return@collectLatest
                }
                if (!lastEnabled) {
                    menuBarLyricsControlsPlatformService.setEnabled(true)
                    lastEnabled = true
                }
                val trackId = player.snapshot.currentTrack?.id
                val fallbackText = resolveMenuBarLyricsFallbackText(player)
                if (trackId != lastTrackId) {
                    menuBarLyricsControlsPlatformService.updateLyrics(fallbackText)
                    lastText = fallbackText
                    lastTrackId = trackId
                }
                val resolvedLyricsText = resolveDesktopLyricsOverlayText(
                    lyrics = player.lyrics,
                    highlightedLineIndex = player.highlightedLineIndex,
                    isLyricsLoading = player.isLyricsLoading,
                )
                val text = resolvedLyricsText
                    ?.takeUnless { it == DESKTOP_LYRICS_LOADING_TEXT }
                    ?.let { top.iwesley.lyn.music.core.model.resolveUiText(it, top.iwesley.lyn.music.core.model.AppLanguageRuntime.effectiveLanguage.value) }
                    ?: fallbackText
                if (text != lastText) {
                    menuBarLyricsControlsPlatformService.updateLyrics(text)
                    lastText = text
                }
            }
    }
}

private fun resolveMenuBarLyricsFallbackText(player: PlayerState): String? {
    return player.snapshot.currentTrack?.let {
        player.snapshot.currentDisplayTitle.trim().ifBlank { it.title.trim() }.ifBlank { "LynMusic" }
    }
}

@Composable
fun App(
    component: LynMusicAppComponent,
    startupAutoOpenGate: StartupAutoOpenGate,
    desktopWindowChrome: DesktopWindowChrome = DesktopWindowChrome(),
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
        val musicTagsState by component.musicTagsStore.state.collectAsState()
        val importState by component.importStore.state.collectAsState()
        val offlineDownloadState by component.offlineDownloadStore.state.collectAsState()
        val playerState by component.playerStore.state.collectAsState()
        val settingsState by component.settingsStore.state.collectAsState()
        var visibleStartupWarning by remember(startupWarning) { mutableStateOf(startupWarning) }
        var selectedTab by rememberSaveable { mutableStateOf(defaultSelectedAppTab) }
        var pendingPlaylistTrack by remember { mutableStateOf<Track?>(null) }
        var shouldRestoreOnlinePlaylistSource by remember { mutableStateOf(false) }
        var onlinePlaylistSourceRestoreTarget by remember { mutableStateOf<String?>(null) }
        var onlinePlaylistSourceRestoreObservedChange by remember { mutableStateOf(false) }
        var pendingLibraryNavigationTarget by remember { mutableStateOf<LibraryNavigationTarget?>(null) }
        var isMusicTagsMobileEditorVisible by rememberSaveable { mutableStateOf(false) }
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
                launch {
                    startupAutoOpenGate.runStartupHydration(
                        requested = shouldAutoOpenPlayerOnStartup(
                            enabled = settingsState.autoOpenPlayerOnStartup,
                            platform = component.platform,
                        ),
                    ) { expandPlayerAfterHydration ->
                        val hydrationJob = component.playerStore.startHydration(
                            expandPlayerAfterHydration = expandPlayerAfterHydration,
                        )
                        hydrationJob.join()
                        if (hydrationJob.isCancelled) {
                            throw CancellationException("Startup playback hydration was cancelled.")
                        }
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
                                phoneNavigation = layoutProfile.usesPortraitLibraryNavigation,
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
                                musicTagsState = musicTagsState,
                                musicTagsEffects = component.musicTagsStore.effects,
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
                                    openAddToPlaylist(effectivePlayerSnapshot.currentTrack)
                                },
                            )
                        } else {
                            DesktopShell(
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
                                musicTagsState = musicTagsState,
                                musicTagsEffects = component.musicTagsStore.effects,
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
                                    openAddToPlaylist(effectivePlayerSnapshot.currentTrack)
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
                                    component.playerStore.dispatch(
                                        PlayerIntent.QueueVisibilityChanged(
                                            true
                                        )
                                    )
                                },
                                onlineNavigationSourceId = playerFavoriteBinding.onlineSourceId,
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
                        playerState.message?.let { message ->
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

internal fun shouldAutoOpenPlayerOnStartup(
    enabled: Boolean,
    platform: PlatformDescriptor,
): Boolean {
    return enabled && !platform.isAndroidTV()
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
    when (resolveAppTabForPlatform(selectedTab, component.platform)) {
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
        AppTab.Tags -> component.musicTagsStore.ensureStarted()
        AppTab.Sources, AppTab.Settings -> Unit
    }
    if (pendingPlaylistTrack != null) {
        component.playlistsStore.ensureContentStarted()
    }
}
