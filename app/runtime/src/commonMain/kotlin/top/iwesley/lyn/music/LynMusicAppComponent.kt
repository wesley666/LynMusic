package top.iwesley.lyn.music

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
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.CompositeSystemPlaybackControlsPlatformService
import top.iwesley.lyn.music.core.model.DesktopLyricsPlatformService
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.EqualizerPlatformService
import top.iwesley.lyn.music.core.model.MenuBarLyricsControlsPlatformService
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.data.repository.DefaultPlaybackRepository
import top.iwesley.lyn.music.data.repository.LyricsRepository
import top.iwesley.lyn.music.data.repository.PlayerRuntimeServices
import top.iwesley.lyn.music.feature.favorites.FavoritesStore
import top.iwesley.lyn.music.feature.importing.ImportStore
import top.iwesley.lyn.music.feature.library.LibraryStore
import top.iwesley.lyn.music.feature.my.MyStore
import top.iwesley.lyn.music.feature.offline.OfflineDownloadStore
import top.iwesley.lyn.music.feature.online.OnlineFavoritesStore
import top.iwesley.lyn.music.feature.online.OnlineLibraryStore
import top.iwesley.lyn.music.feature.online.OnlinePlaylistsStore
import top.iwesley.lyn.music.feature.player.PlayerState
import top.iwesley.lyn.music.feature.player.PlayerStore
import top.iwesley.lyn.music.feature.player.DESKTOP_LYRICS_LOADING_TEXT
import top.iwesley.lyn.music.feature.player.resolveDesktopLyricsOverlayText
import top.iwesley.lyn.music.feature.playlists.PlaylistsStore
import top.iwesley.lyn.music.feature.settings.SettingsIntent
import top.iwesley.lyn.music.feature.settings.SettingsStore
import top.iwesley.lyn.music.feature.tags.MusicTagsStore

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
        playerLyricsStylePreferencesStore = playerRuntimeServices.playerLyricsStylePreferencesStore,
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
