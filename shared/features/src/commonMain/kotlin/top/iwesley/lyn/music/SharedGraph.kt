package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.ArtworkWritePolicy
import top.iwesley.lyn.music.core.model.ArtworkCacheResult

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.AutoOpenPlayerOnStartupPreferencesStore
import top.iwesley.lyn.music.core.model.AutoPlayOnStartupPreferencesStore
import top.iwesley.lyn.music.core.model.AppStorageGateway
import top.iwesley.lyn.music.core.model.AppDataLocationPlatformService
import top.iwesley.lyn.music.core.model.CompositePlaybackStatsReporter
import top.iwesley.lyn.music.core.model.AppDisplayPreferencesStore
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.AudioTagGateway
import top.iwesley.lyn.music.core.model.AudioTagEditorPlatformService
import top.iwesley.lyn.music.core.model.CompactPlayerLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.DesktopLyricsPlatformService
import top.iwesley.lyn.music.core.model.DesktopLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.DesktopVlcPreferencesStore
import top.iwesley.lyn.music.core.model.DeviceInfoGateway
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.LyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.LyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.MenuBarLyricsControlsPreferencesStore
import top.iwesley.lyn.music.core.model.MobileNetworkConnectionTypeProvider
import top.iwesley.lyn.music.core.model.NavidromeAudioQualityPreferencesStore
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.NetworkConnectionTypeProvider
import top.iwesley.lyn.music.core.model.OfflineDownloadGateway
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaybackDecoderPreferencesStore
import top.iwesley.lyn.music.core.model.PlayerArtworkStylePreferencesStore
import top.iwesley.lyn.music.core.model.PlaybackStatsReporter
import top.iwesley.lyn.music.core.model.RemotePlaybackUrlCandidate
import top.iwesley.lyn.music.core.model.SambaCachePreferencesStore
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.SameNameLyricsFileGateway
import top.iwesley.lyn.music.core.model.ThemePreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedAppStorageGateway
import top.iwesley.lyn.music.core.model.UnsupportedAppDataLocationPlatformService
import top.iwesley.lyn.music.core.model.UnsupportedAudioTagEditorPlatformService
import top.iwesley.lyn.music.core.model.UnsupportedAudioTagGateway
import top.iwesley.lyn.music.core.model.UnsupportedAppDisplayPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedAutoOpenPlayerOnStartupPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedAutoPlayOnStartupPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedCompactPlayerLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedDesktopLyricsPlatformService
import top.iwesley.lyn.music.core.model.UnsupportedDesktopLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedDesktopVlcPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedDeviceInfoGateway
import top.iwesley.lyn.music.core.model.UnsupportedLyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.UnsupportedLyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedMenuBarLyricsControlsPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedNavidromeAudioQualityPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedOfflineDownloadGateway
import top.iwesley.lyn.music.core.model.UnsupportedPlaybackDecoderPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedPlayerArtworkStylePreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedSameNameLyricsFileGateway
import top.iwesley.lyn.music.core.model.UnsupportedWindowClosePreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedVlcPathPickerPlatformService
import top.iwesley.lyn.music.core.model.VlcPathPickerPlatformService
import top.iwesley.lyn.music.core.model.WindowClosePreferencesStore
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.repository.DefaultDailyRecommendationDateChangeNotifier
import top.iwesley.lyn.music.data.repository.DefaultAppUpdateRepository
import top.iwesley.lyn.music.data.repository.DefaultLyricsRepository
import top.iwesley.lyn.music.data.repository.DefaultSettingsRepository
import top.iwesley.lyn.music.data.repository.DailyRecommendationDateChangeNotifier
import top.iwesley.lyn.music.data.repository.DailyRecommendationDateKeyProvider
import top.iwesley.lyn.music.data.repository.EmbyPlaybackStatsReporter
import top.iwesley.lyn.music.data.repository.LocalPlaybackStatsReporter
import top.iwesley.lyn.music.data.repository.LyricsRepository
import top.iwesley.lyn.music.data.repository.NavidromePlaybackStatsReporter
import top.iwesley.lyn.music.data.repository.NavidromeOnlineRepository
import top.iwesley.lyn.music.data.repository.DefaultOfflineDownloadRepository
import top.iwesley.lyn.music.data.repository.RoomMyRepository
import top.iwesley.lyn.music.data.repository.RoomMusicTagsRepository
import top.iwesley.lyn.music.data.repository.RoomFavoritesRepository
import top.iwesley.lyn.music.data.repository.RoomImportSourceRepository
import top.iwesley.lyn.music.data.repository.RoomLibraryRepository
import top.iwesley.lyn.music.data.repository.RoomPlaylistRepository
import top.iwesley.lyn.music.data.repository.RoomTrackPlaybackStatsRepository
import top.iwesley.lyn.music.data.repository.UtcDailyRecommendationDateKeyProvider
import top.iwesley.lyn.music.domain.RemoteSourceResolvedUrl
import top.iwesley.lyn.music.domain.resolveNavidromeCoverArtUrl
import top.iwesley.lyn.music.domain.resolveNavidromeCoverArtUrlCandidates
import top.iwesley.lyn.music.domain.resolveNavidromeStreamUrl
import top.iwesley.lyn.music.domain.resolveNavidromeStreamUrlCandidates
import top.iwesley.lyn.music.domain.RemoteSourceAddressKind
import top.iwesley.lyn.music.domain.resolveEmbyCoverArtUrl
import top.iwesley.lyn.music.domain.resolveEmbyCoverArtUrlCandidates
import top.iwesley.lyn.music.domain.resolveEmbyStreamUrl
import top.iwesley.lyn.music.domain.resolveEmbyStreamUrlCandidates
import top.iwesley.lyn.music.domain.RemoteSourceAddressSelector
import top.iwesley.lyn.music.feature.favorites.FavoritesStore
import top.iwesley.lyn.music.feature.importing.ImportStore
import top.iwesley.lyn.music.feature.library.LibrarySourceFilterPreferencesStore
import top.iwesley.lyn.music.feature.library.LibraryStore
import top.iwesley.lyn.music.feature.my.MyStore
import top.iwesley.lyn.music.feature.offline.OfflineDownloadStore
import top.iwesley.lyn.music.feature.online.OnlineFavoritesStore
import top.iwesley.lyn.music.feature.online.OnlineLibraryStore
import top.iwesley.lyn.music.feature.online.OnlinePlaylistsStore
import top.iwesley.lyn.music.feature.playlists.PlaylistsStore
import top.iwesley.lyn.music.feature.settings.SettingsStore
import top.iwesley.lyn.music.feature.tags.MusicTagsStore
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime

data class SharedRuntimeServices(
    val importSourceGateway: ImportSourceGateway,
    val secureCredentialStore: SecureCredentialStore,
    val sambaCachePreferencesStore: SambaCachePreferencesStore,
    val themePreferencesStore: ThemePreferencesStore,
    val appDisplayPreferencesStore: AppDisplayPreferencesStore = UnsupportedAppDisplayPreferencesStore,
    val compactPlayerLyricsPreferencesStore: CompactPlayerLyricsPreferencesStore =
        UnsupportedCompactPlayerLyricsPreferencesStore,
    val desktopLyricsPreferencesStore: DesktopLyricsPreferencesStore =
        UnsupportedDesktopLyricsPreferencesStore,
    val menuBarLyricsControlsPreferencesStore: MenuBarLyricsControlsPreferencesStore =
        UnsupportedMenuBarLyricsControlsPreferencesStore,
    val autoPlayOnStartupPreferencesStore: AutoPlayOnStartupPreferencesStore =
        UnsupportedAutoPlayOnStartupPreferencesStore,
    val autoOpenPlayerOnStartupPreferencesStore: AutoOpenPlayerOnStartupPreferencesStore =
        UnsupportedAutoOpenPlayerOnStartupPreferencesStore,
    val windowClosePreferencesStore: WindowClosePreferencesStore =
        UnsupportedWindowClosePreferencesStore,
    val navidromeAudioQualityPreferencesStore: NavidromeAudioQualityPreferencesStore =
        UnsupportedNavidromeAudioQualityPreferencesStore,
    val playbackDecoderPreferencesStore: PlaybackDecoderPreferencesStore =
        UnsupportedPlaybackDecoderPreferencesStore,
    val playerArtworkStylePreferencesStore: PlayerArtworkStylePreferencesStore =
        UnsupportedPlayerArtworkStylePreferencesStore,
    val networkConnectionTypeProvider: NetworkConnectionTypeProvider = MobileNetworkConnectionTypeProvider,
    val remoteSourceAddressSelector: RemoteSourceAddressSelector =
        RemoteSourceAddressSelector(networkConnectionTypeProvider),
    val desktopVlcPreferencesStore: DesktopVlcPreferencesStore = UnsupportedDesktopVlcPreferencesStore,
    val librarySourceFilterPreferencesStore: LibrarySourceFilterPreferencesStore,
    val lyricsHttpClient: LyricsHttpClient,
    val dailyRecommendationDateKeyProvider: DailyRecommendationDateKeyProvider =
        UtcDailyRecommendationDateKeyProvider,
    val dailyRecommendationDateChangeNotifier: DailyRecommendationDateChangeNotifier =
        DefaultDailyRecommendationDateChangeNotifier(dailyRecommendationDateKeyProvider),
    val artworkCacheStore: ArtworkCacheStore = object : ArtworkCacheStore {
        override suspend fun cache(locator: String, cacheKey: String, policy: ArtworkWritePolicy): ArtworkCacheResult? = ArtworkCacheResult(locator, true)
    },
    val appStorageGateway: AppStorageGateway = UnsupportedAppStorageGateway,
    val appDataLocationPlatformService: AppDataLocationPlatformService =
        UnsupportedAppDataLocationPlatformService,
    val offlineDownloadGateway: OfflineDownloadGateway = UnsupportedOfflineDownloadGateway,
    val deviceInfoGateway: DeviceInfoGateway = UnsupportedDeviceInfoGateway,
    val lyricsShareFontLibraryPlatformService: LyricsShareFontLibraryPlatformService =
        UnsupportedLyricsShareFontLibraryPlatformService,
    val lyricsShareFontPreferencesStore: LyricsShareFontPreferencesStore =
        UnsupportedLyricsShareFontPreferencesStore,
    val audioTagGateway: AudioTagGateway = UnsupportedAudioTagGateway,
    val sameNameLyricsFileGateway: SameNameLyricsFileGateway = UnsupportedSameNameLyricsFileGateway,
    val audioTagEditorPlatformService: AudioTagEditorPlatformService = UnsupportedAudioTagEditorPlatformService,
    val vlcPathPickerPlatformService: VlcPathPickerPlatformService = UnsupportedVlcPathPickerPlatformService,
    val desktopLyricsPlatformService: DesktopLyricsPlatformService = UnsupportedDesktopLyricsPlatformService,
    val logger: DiagnosticLogger = NoopDiagnosticLogger,
)

class SharedGraph(
    val platform: PlatformDescriptor,
    val database: LynMusicDatabase,
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
    val settingsStore: SettingsStore,
    val lyricsRepository: LyricsRepository,
    val artworkCacheStore: ArtworkCacheStore,
    val playbackStatsReporter: PlaybackStatsReporter,
    val audioTagGateway: AudioTagGateway,
    val appDisplayScalePreset: kotlinx.coroutines.flow.StateFlow<AppDisplayScalePreset>,
    val desktopLyricsPlatformService: DesktopLyricsPlatformService,
    val logger: DiagnosticLogger,
    val scope: CoroutineScope,
)

private fun List<RemoteSourceResolvedUrl>.toRemotePlaybackUrlCandidates(): List<RemotePlaybackUrlCandidate> {
    return map { candidate ->
        RemotePlaybackUrlCandidate(
            sourceId = candidate.sourceId,
            addressKind = candidate.kind.name,
            value = candidate.value,
        )
    }
}

fun buildSharedGraph(
    platform: PlatformDescriptor,
    database: LynMusicDatabase,
    runtimeServices: SharedRuntimeServices,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
): SharedGraph {
    val libraryRepository = RoomLibraryRepository(database)
    val trackPlaybackStatsRepository = RoomTrackPlaybackStatsRepository(database)
    val offlineDownloadRepository = DefaultOfflineDownloadRepository(
        database = database,
        gateway = runtimeServices.offlineDownloadGateway,
    )
    val importSourceRepository = RoomImportSourceRepository(
        database = database,
        gateway = runtimeServices.importSourceGateway,
        secureCredentialStore = runtimeServices.secureCredentialStore,
        offlineDownloadGateway = runtimeServices.offlineDownloadGateway,
        addressSelector = runtimeServices.remoteSourceAddressSelector,
    )
    val settingsRepository = DefaultSettingsRepository(
        database = database,
        sambaCachePreferencesStore = runtimeServices.sambaCachePreferencesStore,
        themePreferencesStore = runtimeServices.themePreferencesStore,
        desktopVlcPreferencesStore = runtimeServices.desktopVlcPreferencesStore,
        appDisplayPreferencesStore = runtimeServices.appDisplayPreferencesStore,
        compactPlayerLyricsPreferencesStore = runtimeServices.compactPlayerLyricsPreferencesStore,
        desktopLyricsPreferencesStore = runtimeServices.desktopLyricsPreferencesStore,
        menuBarLyricsControlsPreferencesStore = runtimeServices.menuBarLyricsControlsPreferencesStore,
        autoPlayOnStartupPreferencesStore = runtimeServices.autoPlayOnStartupPreferencesStore,
        autoOpenPlayerOnStartupPreferencesStore = runtimeServices.autoOpenPlayerOnStartupPreferencesStore,
        windowClosePreferencesStore = runtimeServices.windowClosePreferencesStore,
        navidromeAudioQualityPreferencesStore = runtimeServices.navidromeAudioQualityPreferencesStore,
        playbackDecoderPreferencesStore = runtimeServices.playbackDecoderPreferencesStore,
        playerArtworkStylePreferencesStore = runtimeServices.playerArtworkStylePreferencesStore,
    )
    val appUpdateRepository = DefaultAppUpdateRepository(runtimeServices.lyricsHttpClient)
    NavidromeLocatorRuntime.install(
        object : top.iwesley.lyn.music.core.model.NavidromeLocatorResolver {
            override suspend fun resolveStreamUrl(
                locator: String,
                audioQuality: top.iwesley.lyn.music.core.model.NavidromeAudioQuality,
            ): String? {
                return resolveNavidromeStreamUrl(
                    database = database,
                    secureCredentialStore = runtimeServices.secureCredentialStore,
                    locator = locator,
                    audioQuality = audioQuality,
                    addressSelector = runtimeServices.remoteSourceAddressSelector,
                ) ?: resolveEmbyStreamUrl(
                    database = database,
                    secureCredentialStore = runtimeServices.secureCredentialStore,
                    locator = locator,
                    addressSelector = runtimeServices.remoteSourceAddressSelector,
                )
            }

            override suspend fun resolveStreamUrlCandidates(
                locator: String,
                audioQuality: top.iwesley.lyn.music.core.model.NavidromeAudioQuality,
            ): List<RemotePlaybackUrlCandidate>? {
                return resolveNavidromeStreamUrlCandidates(
                    database = database,
                    secureCredentialStore = runtimeServices.secureCredentialStore,
                    locator = locator,
                    audioQuality = audioQuality,
                    addressSelector = runtimeServices.remoteSourceAddressSelector,
                )?.toRemotePlaybackUrlCandidates() ?: resolveEmbyStreamUrlCandidates(
                    database = database,
                    secureCredentialStore = runtimeServices.secureCredentialStore,
                    locator = locator,
                    addressSelector = runtimeServices.remoteSourceAddressSelector,
                )?.toRemotePlaybackUrlCandidates()
            }

            override suspend fun resolveCoverArtUrl(locator: String): String? {
                return resolveNavidromeCoverArtUrl(
                    database = database,
                    secureCredentialStore = runtimeServices.secureCredentialStore,
                    locator = locator,
                    addressSelector = runtimeServices.remoteSourceAddressSelector,
                ) ?: resolveEmbyCoverArtUrl(
                    database = database,
                    secureCredentialStore = runtimeServices.secureCredentialStore,
                    locator = locator,
                    addressSelector = runtimeServices.remoteSourceAddressSelector,
                )
            }

            override suspend fun resolveCoverArtUrlCandidates(locator: String): List<RemotePlaybackUrlCandidate>? {
                return resolveNavidromeCoverArtUrlCandidates(
                    database = database,
                    secureCredentialStore = runtimeServices.secureCredentialStore,
                    locator = locator,
                    addressSelector = runtimeServices.remoteSourceAddressSelector,
                )?.toRemotePlaybackUrlCandidates() ?: resolveEmbyCoverArtUrlCandidates(
                    database = database,
                    secureCredentialStore = runtimeServices.secureCredentialStore,
                    locator = locator,
                    addressSelector = runtimeServices.remoteSourceAddressSelector,
                )?.toRemotePlaybackUrlCandidates()
            }

            override fun markResolvedUrlSuccess(candidate: RemotePlaybackUrlCandidate) {
                val kind = runCatching { RemoteSourceAddressKind.valueOf(candidate.addressKind) }.getOrNull()
                    ?: return
                candidate.sourceId.takeIf { it.isNotBlank() }?.let { sourceId ->
                    runtimeServices.remoteSourceAddressSelector.markSuccess(sourceId, kind)
                }
            }
        },
    )
    val lyricsRepository = DefaultLyricsRepository(
        database = database,
        httpClient = runtimeServices.lyricsHttpClient,
        secureCredentialStore = runtimeServices.secureCredentialStore,
        audioTagGateway = runtimeServices.audioTagGateway,
        sameNameLyricsFileGateway = runtimeServices.sameNameLyricsFileGateway,
        artworkCacheStore = runtimeServices.artworkCacheStore,
        logger = runtimeServices.logger,
        addressSelector = runtimeServices.remoteSourceAddressSelector,
    )
    val playbackStatsReporter = CompositePlaybackStatsReporter(
        reporters = listOf(
            NavidromePlaybackStatsReporter(
                database = database,
                secureCredentialStore = runtimeServices.secureCredentialStore,
                httpClient = runtimeServices.lyricsHttpClient,
                logger = runtimeServices.logger,
                addressSelector = runtimeServices.remoteSourceAddressSelector,
            ),
            EmbyPlaybackStatsReporter(
                database = database,
                secureCredentialStore = runtimeServices.secureCredentialStore,
                httpClient = runtimeServices.lyricsHttpClient,
                logger = runtimeServices.logger,
                addressSelector = runtimeServices.remoteSourceAddressSelector,
            ),
            LocalPlaybackStatsReporter(
                database = database,
            ),
        ),
        logger = runtimeServices.logger,
    )
    val favoritesRepository = RoomFavoritesRepository(
        database = database,
        secureCredentialStore = runtimeServices.secureCredentialStore,
        httpClient = runtimeServices.lyricsHttpClient,
        logger = runtimeServices.logger,
        addressSelector = runtimeServices.remoteSourceAddressSelector,
    )
    val playlistRepository = RoomPlaylistRepository(
        database = database,
        secureCredentialStore = runtimeServices.secureCredentialStore,
        httpClient = runtimeServices.lyricsHttpClient,
        logger = runtimeServices.logger,
        addressSelector = runtimeServices.remoteSourceAddressSelector,
    )
    val musicTagsRepository = RoomMusicTagsRepository(
        database = database,
        audioTagGateway = runtimeServices.audioTagGateway,
        artworkCacheStore = runtimeServices.artworkCacheStore,
    )
    val myRepository = RoomMyRepository(
        database = database,
        secureCredentialStore = runtimeServices.secureCredentialStore,
        httpClient = runtimeServices.lyricsHttpClient,
        logger = runtimeServices.logger,
        dailyRecommendationDateKeyProvider = runtimeServices.dailyRecommendationDateKeyProvider,
        dailyRecommendationDateChangeNotifier = runtimeServices.dailyRecommendationDateChangeNotifier,
        addressSelector = runtimeServices.remoteSourceAddressSelector,
    )
    val navidromeOnlineRepository = NavidromeOnlineRepository(
        database = database,
        secureCredentialStore = runtimeServices.secureCredentialStore,
        httpClient = runtimeServices.lyricsHttpClient,
        logger = runtimeServices.logger,
        addressSelector = runtimeServices.remoteSourceAddressSelector,
    )
    scope.launch {
        settingsRepository.ensureDefaults()
    }
    return SharedGraph(
        platform = platform,
        database = database,
        myStore = MyStore(
            repository = myRepository,
            storeScope = scope,
            startImmediately = false,
        ),
        libraryStore = LibraryStore(
            repository = libraryRepository,
            importSourceRepository = importSourceRepository,
            preferencesStore = runtimeServices.librarySourceFilterPreferencesStore,
            storeScope = scope,
            trackPlaybackStatsRepository = trackPlaybackStatsRepository,
            offlineDownloadRepository = offlineDownloadRepository,
            startImmediately = false,
        ),
        onlineLibraryStore = OnlineLibraryStore(
            repository = navidromeOnlineRepository,
            importSourceRepository = importSourceRepository,
            preferencesStore = runtimeServices.librarySourceFilterPreferencesStore,
            storeScope = scope,
            startImmediately = false,
        ),
        playlistsStore = PlaylistsStore(
            playlistRepository = playlistRepository,
            importSourceRepository = importSourceRepository,
            storeScope = scope,
            offlineDownloadRepository = offlineDownloadRepository,
            startImmediately = false,
        ),
        onlinePlaylistsStore = OnlinePlaylistsStore(
            repository = navidromeOnlineRepository,
            importSourceRepository = importSourceRepository,
            preferencesStore = runtimeServices.librarySourceFilterPreferencesStore,
            storeScope = scope,
            startImmediately = false,
        ),
        favoritesStore = FavoritesStore(
            favoritesRepository = favoritesRepository,
            importSourceRepository = importSourceRepository,
            preferencesStore = runtimeServices.librarySourceFilterPreferencesStore,
            storeScope = scope,
            trackPlaybackStatsRepository = trackPlaybackStatsRepository,
            offlineDownloadRepository = offlineDownloadRepository,
            startImmediately = false,
        ),
        onlineFavoritesStore = OnlineFavoritesStore(
            repository = navidromeOnlineRepository,
            importSourceRepository = importSourceRepository,
            preferencesStore = runtimeServices.librarySourceFilterPreferencesStore,
            storeScope = scope,
            startImmediately = false,
        ),
        musicTagsStore = MusicTagsStore(
            repository = musicTagsRepository,
            lyricsRepository = lyricsRepository,
            editorPlatformService = runtimeServices.audioTagEditorPlatformService,
            storeScope = scope,
            startImmediately = false,
        ),
        importStore = ImportStore(importSourceRepository, platform.capabilities, scope),
        offlineDownloadStore = OfflineDownloadStore(
            repository = offlineDownloadRepository,
            storeScope = scope,
        ),
        settingsStore = SettingsStore(
            repository = settingsRepository,
            scope = scope,
            appStorageGateway = runtimeServices.appStorageGateway,
            appDataLocationPlatformService = runtimeServices.appDataLocationPlatformService,
            deviceInfoGateway = runtimeServices.deviceInfoGateway,
            lyricsShareFontLibraryPlatformService = runtimeServices.lyricsShareFontLibraryPlatformService,
            lyricsShareFontPreferencesStore = runtimeServices.lyricsShareFontPreferencesStore,
            vlcPathPickerPlatformService = runtimeServices.vlcPathPickerPlatformService,
            appUpdateRepository = appUpdateRepository,
            desktopLyricsPlatformService = runtimeServices.desktopLyricsPlatformService,
        ),
        lyricsRepository = lyricsRepository,
        artworkCacheStore = runtimeServices.artworkCacheStore,
        playbackStatsReporter = playbackStatsReporter,
        audioTagGateway = runtimeServices.audioTagGateway,
        appDisplayScalePreset = runtimeServices.appDisplayPreferencesStore.appDisplayScalePreset,
        desktopLyricsPlatformService = runtimeServices.desktopLyricsPlatformService,
        logger = runtimeServices.logger,
        scope = scope,
    )
}
