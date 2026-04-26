package top.iwesley.lyn.music

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.AppStorageGateway
import top.iwesley.lyn.music.core.model.AppDisplayPreferencesStore
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.AudioTagGateway
import top.iwesley.lyn.music.core.model.AudioTagEditorPlatformService
import top.iwesley.lyn.music.core.model.CompactPlayerLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.DesktopVlcPreferencesStore
import top.iwesley.lyn.music.core.model.DeviceInfoGateway
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.LyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.LyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.MobileNetworkConnectionTypeProvider
import top.iwesley.lyn.music.core.model.NavidromeAudioQualityPreferencesStore
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.NetworkConnectionTypeProvider
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.SambaCachePreferencesStore
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.SameNameLyricsFileGateway
import top.iwesley.lyn.music.core.model.ThemePreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedAppStorageGateway
import top.iwesley.lyn.music.core.model.UnsupportedAudioTagEditorPlatformService
import top.iwesley.lyn.music.core.model.UnsupportedAudioTagGateway
import top.iwesley.lyn.music.core.model.UnsupportedAppDisplayPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedCompactPlayerLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedDesktopVlcPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedDeviceInfoGateway
import top.iwesley.lyn.music.core.model.UnsupportedLyricsShareFontLibraryPlatformService
import top.iwesley.lyn.music.core.model.UnsupportedLyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedNavidromeAudioQualityPreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedSameNameLyricsFileGateway
import top.iwesley.lyn.music.core.model.UnsupportedVlcPathPickerPlatformService
import top.iwesley.lyn.music.core.model.VlcPathPickerPlatformService
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.repository.DefaultLyricsRepository
import top.iwesley.lyn.music.data.repository.DefaultSettingsRepository
import top.iwesley.lyn.music.data.repository.LyricsRepository
import top.iwesley.lyn.music.data.repository.RoomMusicTagsRepository
import top.iwesley.lyn.music.data.repository.RoomFavoritesRepository
import top.iwesley.lyn.music.data.repository.RoomImportSourceRepository
import top.iwesley.lyn.music.data.repository.RoomLibraryRepository
import top.iwesley.lyn.music.data.repository.RoomPlaylistRepository
import top.iwesley.lyn.music.domain.resolveNavidromeCoverArtUrl
import top.iwesley.lyn.music.domain.resolveNavidromeStreamUrl
import top.iwesley.lyn.music.feature.favorites.FavoritesStore
import top.iwesley.lyn.music.feature.importing.ImportStore
import top.iwesley.lyn.music.feature.library.LibrarySourceFilterPreferencesStore
import top.iwesley.lyn.music.feature.library.LibraryStore
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
    val navidromeAudioQualityPreferencesStore: NavidromeAudioQualityPreferencesStore =
        UnsupportedNavidromeAudioQualityPreferencesStore,
    val networkConnectionTypeProvider: NetworkConnectionTypeProvider = MobileNetworkConnectionTypeProvider,
    val desktopVlcPreferencesStore: DesktopVlcPreferencesStore = UnsupportedDesktopVlcPreferencesStore,
    val librarySourceFilterPreferencesStore: LibrarySourceFilterPreferencesStore,
    val lyricsHttpClient: LyricsHttpClient,
    val artworkCacheStore: ArtworkCacheStore = object : ArtworkCacheStore {
        override suspend fun cache(locator: String, cacheKey: String): String? = locator
    },
    val appStorageGateway: AppStorageGateway = UnsupportedAppStorageGateway,
    val deviceInfoGateway: DeviceInfoGateway = UnsupportedDeviceInfoGateway,
    val lyricsShareFontLibraryPlatformService: LyricsShareFontLibraryPlatformService =
        UnsupportedLyricsShareFontLibraryPlatformService,
    val lyricsShareFontPreferencesStore: LyricsShareFontPreferencesStore =
        UnsupportedLyricsShareFontPreferencesStore,
    val audioTagGateway: AudioTagGateway = UnsupportedAudioTagGateway,
    val sameNameLyricsFileGateway: SameNameLyricsFileGateway = UnsupportedSameNameLyricsFileGateway,
    val audioTagEditorPlatformService: AudioTagEditorPlatformService = UnsupportedAudioTagEditorPlatformService,
    val vlcPathPickerPlatformService: VlcPathPickerPlatformService = UnsupportedVlcPathPickerPlatformService,
    val logger: DiagnosticLogger = NoopDiagnosticLogger,
)

class SharedGraph(
    val platform: PlatformDescriptor,
    val database: LynMusicDatabase,
    val libraryStore: LibraryStore,
    val playlistsStore: PlaylistsStore,
    val favoritesStore: FavoritesStore,
    val musicTagsStore: MusicTagsStore,
    val importStore: ImportStore,
    val settingsStore: SettingsStore,
    val lyricsRepository: LyricsRepository,
    val audioTagGateway: AudioTagGateway,
    val appDisplayScalePreset: kotlinx.coroutines.flow.StateFlow<AppDisplayScalePreset>,
    val logger: DiagnosticLogger,
    val scope: CoroutineScope,
)

fun buildSharedGraph(
    platform: PlatformDescriptor,
    database: LynMusicDatabase,
    runtimeServices: SharedRuntimeServices,
): SharedGraph {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val libraryRepository = RoomLibraryRepository(database)
    val importSourceRepository = RoomImportSourceRepository(
        database = database,
        gateway = runtimeServices.importSourceGateway,
        secureCredentialStore = runtimeServices.secureCredentialStore,
    )
    val settingsRepository = DefaultSettingsRepository(
        database = database,
        sambaCachePreferencesStore = runtimeServices.sambaCachePreferencesStore,
        themePreferencesStore = runtimeServices.themePreferencesStore,
        desktopVlcPreferencesStore = runtimeServices.desktopVlcPreferencesStore,
        appDisplayPreferencesStore = runtimeServices.appDisplayPreferencesStore,
        compactPlayerLyricsPreferencesStore = runtimeServices.compactPlayerLyricsPreferencesStore,
        navidromeAudioQualityPreferencesStore = runtimeServices.navidromeAudioQualityPreferencesStore,
    )
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
                )
            }

            override suspend fun resolveCoverArtUrl(locator: String): String? {
                return resolveNavidromeCoverArtUrl(
                    database = database,
                    secureCredentialStore = runtimeServices.secureCredentialStore,
                    locator = locator,
                )
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
    )
    val favoritesRepository = RoomFavoritesRepository(
        database = database,
        secureCredentialStore = runtimeServices.secureCredentialStore,
        httpClient = runtimeServices.lyricsHttpClient,
        logger = runtimeServices.logger,
    )
    val playlistRepository = RoomPlaylistRepository(
        database = database,
        secureCredentialStore = runtimeServices.secureCredentialStore,
        httpClient = runtimeServices.lyricsHttpClient,
        logger = runtimeServices.logger,
    )
    val musicTagsRepository = RoomMusicTagsRepository(
        database = database,
        audioTagGateway = runtimeServices.audioTagGateway,
    )
    scope.launch {
        settingsRepository.ensureDefaults()
    }
    return SharedGraph(
        platform = platform,
        database = database,
        libraryStore = LibraryStore(
            repository = libraryRepository,
            importSourceRepository = importSourceRepository,
            preferencesStore = runtimeServices.librarySourceFilterPreferencesStore,
            storeScope = scope,
            startImmediately = false,
        ),
        playlistsStore = PlaylistsStore(
            playlistRepository = playlistRepository,
            importSourceRepository = importSourceRepository,
            storeScope = scope,
            startImmediately = false,
        ),
        favoritesStore = FavoritesStore(
            favoritesRepository = favoritesRepository,
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
        settingsStore = SettingsStore(
            repository = settingsRepository,
            scope = scope,
            appStorageGateway = runtimeServices.appStorageGateway,
            deviceInfoGateway = runtimeServices.deviceInfoGateway,
            lyricsShareFontLibraryPlatformService = runtimeServices.lyricsShareFontLibraryPlatformService,
            lyricsShareFontPreferencesStore = runtimeServices.lyricsShareFontPreferencesStore,
            vlcPathPickerPlatformService = runtimeServices.vlcPathPickerPlatformService,
        ),
        lyricsRepository = lyricsRepository,
        audioTagGateway = runtimeServices.audioTagGateway,
        appDisplayScalePreset = runtimeServices.appDisplayPreferencesStore.appDisplayScalePreset,
        logger = runtimeServices.logger,
        scope = scope,
    )
}
