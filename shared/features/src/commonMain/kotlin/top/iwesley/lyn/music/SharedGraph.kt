package top.iwesley.lyn.music

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.AudioTagGateway
import top.iwesley.lyn.music.core.model.AudioTagEditorPlatformService
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.SambaCachePreferencesStore
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.ThemePreferencesStore
import top.iwesley.lyn.music.core.model.UnsupportedAudioTagEditorPlatformService
import top.iwesley.lyn.music.core.model.UnsupportedAudioTagGateway
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
    val librarySourceFilterPreferencesStore: LibrarySourceFilterPreferencesStore,
    val lyricsHttpClient: LyricsHttpClient,
    val artworkCacheStore: ArtworkCacheStore = object : ArtworkCacheStore {
        override suspend fun cache(locator: String, cacheKey: String): String? = locator
    },
    val audioTagGateway: AudioTagGateway = UnsupportedAudioTagGateway,
    val audioTagEditorPlatformService: AudioTagEditorPlatformService = UnsupportedAudioTagEditorPlatformService,
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
    )
    NavidromeLocatorRuntime.install(
        object : top.iwesley.lyn.music.core.model.NavidromeLocatorResolver {
            override suspend fun resolveStreamUrl(locator: String): String? {
                return resolveNavidromeStreamUrl(
                    database = database,
                    secureCredentialStore = runtimeServices.secureCredentialStore,
                    locator = locator,
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
            scope = scope,
        ),
        playlistsStore = PlaylistsStore(
            playlistRepository = playlistRepository,
            importSourceRepository = importSourceRepository,
            storeScope = scope,
        ),
        favoritesStore = FavoritesStore(
            favoritesRepository = favoritesRepository,
            importSourceRepository = importSourceRepository,
            preferencesStore = runtimeServices.librarySourceFilterPreferencesStore,
            storeScope = scope,
        ),
        musicTagsStore = MusicTagsStore(
            repository = musicTagsRepository,
            lyricsRepository = lyricsRepository,
            editorPlatformService = runtimeServices.audioTagEditorPlatformService,
            storeScope = scope,
        ),
        importStore = ImportStore(importSourceRepository, platform.capabilities, scope),
        settingsStore = SettingsStore(settingsRepository, scope),
        lyricsRepository = lyricsRepository,
        audioTagGateway = runtimeServices.audioTagGateway,
        logger = runtimeServices.logger,
        scope = scope,
    )
}
