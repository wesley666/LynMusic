package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.resources.*

import top.iwesley.lyn.music.core.model.markCurrentRemoteSourceReachable
import top.iwesley.lyn.music.core.model.currentRemoteSourceConnectTimeoutMillis
import top.iwesley.lyn.music.core.model.APP_LANGUAGE_PREFERENCE_KEY
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.AppLanguagePreferencesStore
import top.iwesley.lyn.music.core.model.AppLanguageRuntime
import top.iwesley.lyn.music.core.model.appLanguageOrDefault
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.uiText

import androidx.room.Room
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.iwesley.lyn.music.SharedRuntimeServices
import top.iwesley.lyn.music.buildPlayerAppComponent
import top.iwesley.lyn.music.buildSharedGraph
import top.iwesley.lyn.music.core.model.ConsoleDiagnosticLogger
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.CompactPlayerLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.EmbyCredential
import top.iwesley.lyn.music.core.model.EmbySourceDraft
import top.iwesley.lyn.music.core.model.IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportScanWarning
import top.iwesley.lyn.music.core.model.ImportScanProgressSink
import top.iwesley.lyn.music.core.model.ImportStreamingScanReport
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.ImportTrackBatchSink
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.NavidromeAudioQualityPreferencesStore
import top.iwesley.lyn.music.core.model.NavidromeLibraryProbe
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.NetworkConnectionState
import top.iwesley.lyn.music.core.model.NetworkConnectionType
import top.iwesley.lyn.music.core.model.NetworkConnectionTypeProvider
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaybackPreferencesStore
import top.iwesley.lyn.music.core.model.PlayerArtworkStyle
import top.iwesley.lyn.music.core.model.PlayerArtworkStylePreferencesStore
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.DEFAULT_PLAYBACK_VOLUME
import top.iwesley.lyn.music.core.model.SambaCachePreferencesStore
import top.iwesley.lyn.music.core.model.ThemePreferencesStore
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.AutoOpenPlayerOnStartupPreferencesStore
import top.iwesley.lyn.music.core.model.defaultCustomThemeTokens
import top.iwesley.lyn.music.core.model.defaultThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.navidromeAudioQualityOrDefault
import top.iwesley.lyn.music.core.model.normalizePlaybackVolume
import top.iwesley.lyn.music.core.model.playerArtworkStyleOrDefault
import top.iwesley.lyn.music.core.model.withThemePalette
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.SubsonicSourceDraft
import top.iwesley.lyn.music.core.model.UnsupportedAudioTagEditorPlatformService
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.withSecureInMemoryCache
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.openLynMusicDatabase
import top.iwesley.lyn.music.data.repository.DailyRecommendationDateChangeNotifier
import top.iwesley.lyn.music.data.repository.DailyRecommendationDateKeyProvider
import top.iwesley.lyn.music.data.repository.PlayerRuntimeServices
import top.iwesley.lyn.music.domain.RemoteSourceAddressSelector
import top.iwesley.lyn.music.domain.scanEmbyLibrary
import top.iwesley.lyn.music.domain.scanNavidromeLibrary
import top.iwesley.lyn.music.domain.scanNavidromeLibraryStreaming
import top.iwesley.lyn.music.domain.probeNavidromeLibrary
import top.iwesley.lyn.music.domain.scanSubsonicLibrary
import top.iwesley.lyn.music.domain.testEmbyConnection
import top.iwesley.lyn.music.domain.testNavidromeConnection
import top.iwesley.lyn.music.domain.testSubsonicConnection
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.LibrarySourceFilterPreferencesStore
import top.iwesley.lyn.music.feature.library.TrackSortMode
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_uses_interface_type
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

fun createIosAppComponent(): top.iwesley.lyn.music.LynMusicAppComponent {
    val appPreferencesStore = IosAppPreferencesStore()
    val database = openLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(
            name = documentDirectory() + "/lynmusic.db",
        ),
    ).getOrThrow()
    val secureStore = IosKeychainCredentialStore().withSecureInMemoryCache()
    val networkConnectionTypeProvider = IosNetworkConnectionTypeProvider()
    val remoteSourceAddressSelector = RemoteSourceAddressSelector(networkConnectionTypeProvider)
    val navidromeHttpClient = IosLyricsHttpClient()
    val logger = ConsoleDiagnosticLogger(enabled = true, label = "iOS")
    val localFileAccessService = IosLocalFileAccessService(database)
    val platform = PlatformDescriptor(
        name = "iPhone / iPad",
        capabilities = PlatformCapabilities(
            supportsLocalFolderImport = true,
            supportsSambaImport = false,
            supportsWebDavImport = false,
            supportsNavidromeImport = true,
            supportsSystemMediaControls = true,
            supportsSystemLocalFolderPicker = true,
            supportsLocalFolderReauthorization = true,
        ),
    )
    val sharedGraph = buildSharedGraph(
        platform = platform,
        database = database,
        runtimeServices = SharedRuntimeServices(
            importSourceGateway = IosImportSourceGateway(navidromeHttpClient, logger),
            secureCredentialStore = secureStore,
            sambaCachePreferencesStore = appPreferencesStore,
            themePreferencesStore = appPreferencesStore,
            compactPlayerLyricsPreferencesStore = appPreferencesStore,
            autoPlayOnStartupPreferencesStore = appPreferencesStore,
            autoOpenPlayerOnStartupPreferencesStore = appPreferencesStore,
            navidromeAudioQualityPreferencesStore = appPreferencesStore,
            playerArtworkStylePreferencesStore = appPreferencesStore,
            networkConnectionTypeProvider = networkConnectionTypeProvider,
            remoteSourceAddressSelector = remoteSourceAddressSelector,
            librarySourceFilterPreferencesStore = appPreferencesStore,
            lyricsHttpClient = navidromeHttpClient,
            artworkCacheStore = createIosArtworkCacheStore(),
            appStorageGateway = createIosAppStorageGateway(),
            deviceInfoGateway = createIosDeviceInfoGateway(),
            audioTagGateway = IosAudioTagGateway(localFileAccessService),
            audioTagEditorPlatformService = UnsupportedAudioTagEditorPlatformService,
            sameNameLyricsFileGateway = IosSameNameLyricsFileGateway(localFileAccessService),
            dailyRecommendationDateKeyProvider = IosDailyRecommendationDateKeyProvider,
            dailyRecommendationDateChangeNotifier = IosDailyRecommendationDateChangeNotifier(
                IosDailyRecommendationDateKeyProvider,
            ),
            logger = logger,
        ),
    )
    return buildPlayerAppComponent(
        sharedGraph = sharedGraph,
        playerRuntimeServices = PlayerRuntimeServices(
            playbackGateway = ApplePlaybackGateway(
                platformLabel = "iOS",
                navidromeAudioQualityPreferencesStore = appPreferencesStore,
                networkConnectionTypeProvider = networkConnectionTypeProvider,
                addressSelector = remoteSourceAddressSelector,
                localMediaAccessResolver = IosAppleLocalMediaAccessResolver(localFileAccessService),
            ),
            playbackPreferencesStore = appPreferencesStore,
            lyricsShareFontPreferencesStore = appPreferencesStore,
            lyricsSharePlatformService = IosLyricsSharePlatformService(),
            systemPlaybackControlsPlatformService = createIosSystemPlaybackControlsPlatformService(),
        ),
    )
}

private object IosDailyRecommendationDateKeyProvider : DailyRecommendationDateKeyProvider {
    override fun currentDateKey(): String {
        val formatter = NSDateFormatter()
        formatter.locale = NSLocale(localeIdentifier = "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.stringFromDate(NSDate())
    }
}

private class IosDailyRecommendationDateChangeNotifier(
    private val dateKeyProvider: DailyRecommendationDateKeyProvider,
) : DailyRecommendationDateChangeNotifier {
    private val mutableDateKeys = MutableStateFlow(dateKeyProvider.currentDateKey())
    @Suppress("unused")
    private var observers: List<Any?> = emptyList()

    override val dateKeys: Flow<String> = mutableDateKeys.asStateFlow()

    init {
        val dayChangedObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = "NSCalendarDayChangedNotification",
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) {
            refreshCurrentDateKey()
        }
        val significantTimeObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = "UIApplicationSignificantTimeChangeNotification",
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) {
            refreshCurrentDateKey()
        }
        val activeObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = "UIApplicationDidBecomeActiveNotification",
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) {
            refreshCurrentDateKey()
        }
        observers = listOf(dayChangedObserver, significantTimeObserver, activeObserver)
    }

    override fun refreshCurrentDateKey() {
        mutableDateKeys.value = dateKeyProvider.currentDateKey()
    }
}

private const val IOS_REMOTE_SOURCE_FALLBACK_SOCKET_TIMEOUT_MILLIS = 10_000L

private class IosLyricsHttpClient : LyricsHttpClient {
    private val client = HttpClient(Darwin) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000L
            connectTimeoutMillis = 30_000L
            socketTimeoutMillis = 30_000L
        }
    }

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        return runCatching {
            val response = client.request {
                url(request.url)
                this.method = when (request.method) {
                    RequestMethod.GET -> HttpMethod.Get
                    RequestMethod.POST -> HttpMethod.Post
                    RequestMethod.DELETE -> HttpMethod.Delete
                }
                request.headers.forEach { (key, value) -> headers.append(key, value) }
                request.body?.let { setBody(it) }
                val requestTimeout = request.timeoutMillis?.takeIf { it > 0L }
                // Darwin only honours the socket (idle) timeout, so cap it while an alternate address remains.
                val fallbackSocketTimeout = currentRemoteSourceConnectTimeoutMillis()
                    ?.let { IOS_REMOTE_SOURCE_FALLBACK_SOCKET_TIMEOUT_MILLIS }
                val socketTimeout = listOfNotNull(requestTimeout, fallbackSocketTimeout).minOrNull()
                if (requestTimeout != null || socketTimeout != null) {
                    timeout {
                        requestTimeout?.let {
                            requestTimeoutMillis = it
                            connectTimeoutMillis = it
                        }
                        socketTimeout?.let { socketTimeoutMillis = it }
                    }
                }
            }
            markCurrentRemoteSourceReachable()
            LyricsHttpResponse(
                statusCode = response.status.value,
                body = response.bodyAsText(),
                headers = response.headers.entries().associate { entry ->
                    entry.key to entry.value.joinToString(",")
                },
            )
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosKeychainCredentialStore : SecureCredentialStore {
    override suspend fun put(key: String, value: String) {
        val baseQuery = keychainQuery(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to IOS_KEYCHAIN_SERVICE.toCFValue(),
            kSecAttrAccount to key.toCFValue(),
        )
        val updateStatus = SecItemUpdate(
            baseQuery,
            keychainQuery(kSecValueData to value.toKeychainData()),
        )
        when (updateStatus) {
            errSecSuccess -> Unit
            errSecItemNotFound -> {
                val addStatus = SecItemAdd(
                    keychainQuery(
                        kSecClass to kSecClassGenericPassword,
                        kSecAttrService to IOS_KEYCHAIN_SERVICE.toCFValue(),
                        kSecAttrAccount to key.toCFValue(),
                        kSecValueData to value.toKeychainData(),
                        kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                    ),
                    null,
                )
                check(addStatus == errSecSuccess) { "Keychain write failed: $addStatus" }
            }

            else -> error("Keychain update failed: $updateStatus")
        }
    }

    override suspend fun get(key: String): String? {
        return memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(
                keychainQuery(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to IOS_KEYCHAIN_SERVICE.toCFValue(),
                    kSecAttrAccount to key.toCFValue(),
                    kSecReturnData to kCFBooleanTrue,
                    kSecMatchLimit to kSecMatchLimitOne,
                ),
                result.ptr,
            )
            when (status) {
                errSecSuccess -> {
                    val released = result.value?.let { CFBridgingRelease(it) } as? NSData
                    released?.toUtf8String()
                }

                errSecItemNotFound -> null
                else -> error("Keychain read failed: $status")
            }
        }
    }

    override suspend fun remove(key: String) {
        val status = SecItemDelete(
            keychainQuery(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to IOS_KEYCHAIN_SERVICE.toCFValue(),
                kSecAttrAccount to key.toCFValue(),
            ),
        )
        if (status != errSecSuccess && status != errSecItemNotFound) {
            error("Keychain delete failed: $status")
        }
    }
}

private class IosAppPreferencesStore : AppLanguagePreferencesStore, PlaybackPreferencesStore, SambaCachePreferencesStore, ThemePreferencesStore,
    CompactPlayerLyricsPreferencesStore, NavidromeAudioQualityPreferencesStore, LyricsShareFontPreferencesStore,
    PlayerArtworkStylePreferencesStore, LibrarySourceFilterPreferencesStore,
    AutoOpenPlayerOnStartupPreferencesStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val mutableAppLanguage = MutableStateFlow(appLanguageOrDefault(defaults.stringForKey(APP_LANGUAGE_PREFERENCE_KEY)))
    override val appLanguage: StateFlow<AppLanguage> = mutableAppLanguage.asStateFlow()
    init {
        AppLanguageRuntime.install(this, platform.Foundation.NSLocale.preferredLanguages.firstOrNull() as? String ?: "en")
    }
    override suspend fun setAppLanguage(language: AppLanguage) {
        AppLanguageRuntime.updateSystemLanguage(NSLocale.preferredLanguages.firstOrNull() as? String ?: "en")
        defaults.setObject(language.storageValue, APP_LANGUAGE_PREFERENCE_KEY)
        mutableAppLanguage.value = language
        AppLanguageRuntime.update(language)
    }

    private val mutableUseSambaCache = MutableStateFlow(
        if (defaults.objectForKey(KEY_USE_SAMBA_CACHE) == null) false else defaults.boolForKey(KEY_USE_SAMBA_CACHE),
    )
    private val mutablePlaybackVolume = MutableStateFlow(readPlaybackVolume())
    private val mutableShowCompactPlayerLyrics = MutableStateFlow(
        if (defaults.objectForKey(KEY_SHOW_COMPACT_PLAYER_LYRICS) == null) {
            false
        } else {
            defaults.boolForKey(KEY_SHOW_COMPACT_PLAYER_LYRICS)
        },
    )
    private val mutableAutoPlayOnStartup = MutableStateFlow(
        if (defaults.objectForKey(KEY_AUTO_PLAY_ON_STARTUP) == null) {
            false
        } else {
            defaults.boolForKey(KEY_AUTO_PLAY_ON_STARTUP)
        },
    )
    private val mutableAutoOpenPlayerOnStartup = MutableStateFlow(
        if (defaults.objectForKey(KEY_AUTO_OPEN_PLAYER_ON_STARTUP) == null) {
            false
        } else {
            defaults.boolForKey(KEY_AUTO_OPEN_PLAYER_ON_STARTUP)
        },
    )
    private val mutableNavidromeWifiAudioQuality = MutableStateFlow(
        readNavidromeAudioQuality(KEY_NAVIDROME_WIFI_AUDIO_QUALITY, NavidromeAudioQuality.Original),
    )
    private val mutableNavidromeMobileAudioQuality = MutableStateFlow(
        readNavidromeAudioQuality(KEY_NAVIDROME_MOBILE_AUDIO_QUALITY, NavidromeAudioQuality.Kbps192),
    )
    private val mutableLibrarySourceFilter = MutableStateFlow(readLibrarySourceFilter(KEY_LIBRARY_SOURCE_FILTER))
    private val mutableFavoritesSourceFilter = MutableStateFlow(readLibrarySourceFilter(KEY_FAVORITES_SOURCE_FILTER))
    private val mutableOnlineLibrarySourceId = MutableStateFlow(readNullablePreference(KEY_ONLINE_LIBRARY_SOURCE_ID))
    private val mutableOnlineFavoritesSourceId = MutableStateFlow(readNullablePreference(KEY_ONLINE_FAVORITES_SOURCE_ID))
    private val mutableOnlinePlaylistsSourceId = MutableStateFlow(readNullablePreference(KEY_ONLINE_PLAYLISTS_SOURCE_ID))
    private val mutableLibraryTrackSortMode = MutableStateFlow(
        readTrackSortMode(KEY_LIBRARY_TRACK_SORT_MODE, TrackSortMode.TITLE),
    )
    private val mutableFavoritesTrackSortMode = MutableStateFlow(
        readTrackSortMode(KEY_FAVORITES_TRACK_SORT_MODE, TrackSortMode.ADDED_AT),
    )
    private val mutableSelectedTheme = MutableStateFlow(readSelectedTheme())
    private val mutableCustomThemeTokens = MutableStateFlow(readCustomThemeTokens())
    private val mutableTextPalettePreferences = MutableStateFlow(readTextPalettePreferences())
    private val mutablePlayerArtworkStyle = MutableStateFlow(readPlayerArtworkStyle())
    private val mutableSelectedLyricsShareFontKey = MutableStateFlow(readSelectedLyricsShareFontKey())

    override val useSambaCache: StateFlow<Boolean> = mutableUseSambaCache.asStateFlow()
    override val playbackVolume: StateFlow<Float> = mutablePlaybackVolume.asStateFlow()
    override val showCompactPlayerLyrics: StateFlow<Boolean> = mutableShowCompactPlayerLyrics.asStateFlow()
    override val autoPlayOnStartup: StateFlow<Boolean> = mutableAutoPlayOnStartup.asStateFlow()
    override val autoOpenPlayerOnStartup: StateFlow<Boolean> =
        mutableAutoOpenPlayerOnStartup.asStateFlow()
    override val navidromeWifiAudioQuality: StateFlow<NavidromeAudioQuality> =
        mutableNavidromeWifiAudioQuality.asStateFlow()
    override val navidromeMobileAudioQuality: StateFlow<NavidromeAudioQuality> =
        mutableNavidromeMobileAudioQuality.asStateFlow()
    override val selectedTheme: StateFlow<AppThemeId> = mutableSelectedTheme.asStateFlow()
    override val customThemeTokens: StateFlow<AppThemeTokens> = mutableCustomThemeTokens.asStateFlow()
    override val textPalettePreferences: StateFlow<AppThemeTextPalettePreferences> = mutableTextPalettePreferences.asStateFlow()
    override val playerArtworkStyle: StateFlow<PlayerArtworkStyle> = mutablePlayerArtworkStyle.asStateFlow()
    override val selectedLyricsShareFontKey: StateFlow<String?> = mutableSelectedLyricsShareFontKey.asStateFlow()
    override val librarySourceFilter: StateFlow<LibrarySourceFilter> = mutableLibrarySourceFilter.asStateFlow()
    override val favoritesSourceFilter: StateFlow<LibrarySourceFilter> = mutableFavoritesSourceFilter.asStateFlow()
    override val onlineLibrarySourceId: StateFlow<String?> = mutableOnlineLibrarySourceId.asStateFlow()
    override val onlineFavoritesSourceId: StateFlow<String?> = mutableOnlineFavoritesSourceId.asStateFlow()
    override val onlinePlaylistsSourceId: StateFlow<String?> = mutableOnlinePlaylistsSourceId.asStateFlow()
    override val libraryTrackSortMode: StateFlow<TrackSortMode> = mutableLibraryTrackSortMode.asStateFlow()
    override val favoritesTrackSortMode: StateFlow<TrackSortMode> = mutableFavoritesTrackSortMode.asStateFlow()

    override suspend fun setUseSambaCache(enabled: Boolean) {
        defaults.setBool(enabled, KEY_USE_SAMBA_CACHE)
        mutableUseSambaCache.value = enabled
    }

    override suspend fun setPlaybackVolume(volume: Float) {
        val normalizedVolume = normalizePlaybackVolume(volume)
        defaults.setDouble(normalizedVolume.toDouble(), KEY_PLAYBACK_VOLUME)
        mutablePlaybackVolume.value = normalizedVolume
    }

    override suspend fun setShowCompactPlayerLyrics(enabled: Boolean) {
        defaults.setBool(enabled, KEY_SHOW_COMPACT_PLAYER_LYRICS)
        mutableShowCompactPlayerLyrics.value = enabled
    }

    override suspend fun setAutoPlayOnStartup(enabled: Boolean) {
        defaults.setBool(enabled, KEY_AUTO_PLAY_ON_STARTUP)
        mutableAutoPlayOnStartup.value = enabled
    }

    override suspend fun setAutoOpenPlayerOnStartup(enabled: Boolean) {
        defaults.setBool(enabled, KEY_AUTO_OPEN_PLAYER_ON_STARTUP)
        mutableAutoOpenPlayerOnStartup.value = enabled
    }

    override suspend fun setNavidromeWifiAudioQuality(quality: NavidromeAudioQuality) {
        defaults.setObject(quality.name, KEY_NAVIDROME_WIFI_AUDIO_QUALITY)
        mutableNavidromeWifiAudioQuality.value = quality
    }

    override suspend fun setNavidromeMobileAudioQuality(quality: NavidromeAudioQuality) {
        defaults.setObject(quality.name, KEY_NAVIDROME_MOBILE_AUDIO_QUALITY)
        mutableNavidromeMobileAudioQuality.value = quality
    }

    override suspend fun setPlayerArtworkStyle(style: PlayerArtworkStyle) {
        defaults.setObject(style.name, KEY_PLAYER_ARTWORK_STYLE)
        mutablePlayerArtworkStyle.value = style
    }

    override suspend fun setSelectedLyricsShareFontKey(value: String?) {
        val normalizedValue = value?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedValue == null) {
            defaults.removeObjectForKey(KEY_LYRICS_SHARE_FONT_KEY)
        } else {
            defaults.setObject(normalizedValue, KEY_LYRICS_SHARE_FONT_KEY)
        }
        mutableSelectedLyricsShareFontKey.value = normalizedValue
    }

    override suspend fun setLibrarySourceFilter(filter: LibrarySourceFilter) {
        defaults.setObject(filter.name, KEY_LIBRARY_SOURCE_FILTER)
        mutableLibrarySourceFilter.value = filter
    }

    override suspend fun setFavoritesSourceFilter(filter: LibrarySourceFilter) {
        defaults.setObject(filter.name, KEY_FAVORITES_SOURCE_FILTER)
        mutableFavoritesSourceFilter.value = filter
    }

    override suspend fun setOnlineLibrarySourceId(sourceId: String?) {
        setNullablePreference(KEY_ONLINE_LIBRARY_SOURCE_ID, sourceId)
        mutableOnlineLibrarySourceId.value = normalizeNullablePreference(sourceId)
    }

    override suspend fun setOnlineFavoritesSourceId(sourceId: String?) {
        setNullablePreference(KEY_ONLINE_FAVORITES_SOURCE_ID, sourceId)
        mutableOnlineFavoritesSourceId.value = normalizeNullablePreference(sourceId)
    }

    override suspend fun setOnlinePlaylistsSourceId(sourceId: String?) {
        setNullablePreference(KEY_ONLINE_PLAYLISTS_SOURCE_ID, sourceId)
        mutableOnlinePlaylistsSourceId.value = normalizeNullablePreference(sourceId)
    }

    override suspend fun setLibraryTrackSortMode(mode: TrackSortMode) {
        defaults.setObject(mode.name, KEY_LIBRARY_TRACK_SORT_MODE)
        mutableLibraryTrackSortMode.value = mode
    }

    override suspend fun setFavoritesTrackSortMode(mode: TrackSortMode) {
        defaults.setObject(mode.name, KEY_FAVORITES_TRACK_SORT_MODE)
        mutableFavoritesTrackSortMode.value = mode
    }

    override suspend fun setSelectedTheme(themeId: AppThemeId) {
        defaults.setObject(themeId.name, KEY_SELECTED_THEME)
        mutableSelectedTheme.value = themeId
    }

    override suspend fun setCustomThemeTokens(tokens: AppThemeTokens) {
        defaults.setInteger(tokens.backgroundArgb.toLong(), KEY_CUSTOM_THEME_BACKGROUND_ARGB)
        defaults.setInteger(tokens.accentArgb.toLong(), KEY_CUSTOM_THEME_ACCENT_ARGB)
        defaults.setInteger(tokens.focusArgb.toLong(), KEY_CUSTOM_THEME_FOCUS_ARGB)
        mutableCustomThemeTokens.value = tokens
    }

    override suspend fun setTextPalette(themeId: AppThemeId, palette: AppThemeTextPalette) {
        defaults.setObject(palette.name, textPaletteKey(themeId))
        mutableTextPalettePreferences.value = mutableTextPalettePreferences.value.withThemePalette(themeId, palette)
    }

    private fun readLibrarySourceFilter(key: String): LibrarySourceFilter {
        val name = defaults.stringForKey(key)
        return LibrarySourceFilter.entries.firstOrNull { it.name == name } ?: LibrarySourceFilter.ALL
    }

    private fun readNullablePreference(key: String): String? {
        return normalizeNullablePreference(defaults.stringForKey(key))
    }

    private fun normalizeNullablePreference(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun setNullablePreference(key: String, value: String?) {
        val normalizedValue = normalizeNullablePreference(value)
        if (normalizedValue == null) {
            defaults.removeObjectForKey(key)
        } else {
            defaults.setObject(normalizedValue, key)
        }
    }

    private fun readTrackSortMode(key: String, defaultMode: TrackSortMode): TrackSortMode {
        val name = defaults.stringForKey(key)
        return TrackSortMode.entries.firstOrNull { it.name == name } ?: defaultMode
    }

    private fun readPlayerArtworkStyle(): PlayerArtworkStyle {
        return playerArtworkStyleOrDefault(defaults.stringForKey(KEY_PLAYER_ARTWORK_STYLE))
    }

    private fun readPlaybackVolume(): Float {
        val storedVolume = if (defaults.objectForKey(KEY_PLAYBACK_VOLUME) == null) {
            DEFAULT_PLAYBACK_VOLUME
        } else {
            defaults.doubleForKey(KEY_PLAYBACK_VOLUME).toFloat()
        }
        return normalizePlaybackVolume(storedVolume)
    }

    private fun readSelectedTheme(): AppThemeId {
        val name = defaults.stringForKey(KEY_SELECTED_THEME)
        return AppThemeId.entries.firstOrNull { it.name == name } ?: AppThemeId.Ocean
    }

    private fun readNavidromeAudioQuality(
        key: String,
        default: NavidromeAudioQuality,
    ): NavidromeAudioQuality {
        return navidromeAudioQualityOrDefault(defaults.stringForKey(key), default)
    }

    private fun readSelectedLyricsShareFontKey(): String? {
        return defaults.stringForKey(KEY_LYRICS_SHARE_FONT_KEY)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun readCustomThemeTokens(): AppThemeTokens {
        val defaultTokens = defaultCustomThemeTokens()
        return AppThemeTokens(
            backgroundArgb = readIntPreference(KEY_CUSTOM_THEME_BACKGROUND_ARGB, defaultTokens.backgroundArgb),
            accentArgb = readIntPreference(KEY_CUSTOM_THEME_ACCENT_ARGB, defaultTokens.accentArgb),
            focusArgb = readIntPreference(KEY_CUSTOM_THEME_FOCUS_ARGB, defaultTokens.focusArgb),
        )
    }

    private fun readTextPalettePreferences(): AppThemeTextPalettePreferences {
        val defaults = defaultThemeTextPalettePreferences()
        return AppThemeTextPalettePreferences(
            classic = readTextPalette(textPaletteKey(AppThemeId.Classic), defaults.classic),
            forest = readTextPalette(textPaletteKey(AppThemeId.Forest), defaults.forest),
            ocean = readTextPalette(textPaletteKey(AppThemeId.Ocean), defaults.ocean),
            sand = readTextPalette(textPaletteKey(AppThemeId.Sand), defaults.sand),
            custom = readTextPalette(textPaletteKey(AppThemeId.Custom), defaults.custom),
        )
    }

    private fun readTextPalette(key: String, fallback: AppThemeTextPalette): AppThemeTextPalette {
        val name = defaults.stringForKey(key)
        return AppThemeTextPalette.entries.firstOrNull { it.name == name } ?: fallback
    }

    private fun readIntPreference(key: String, fallback: Int): Int {
        return if (defaults.objectForKey(key) == null) fallback else defaults.integerForKey(key).toInt()
    }

    private fun textPaletteKey(themeId: AppThemeId): String {
        return when (themeId) {
            AppThemeId.Classic -> KEY_THEME_TEXT_PALETTE_CLASSIC
            AppThemeId.Forest -> KEY_THEME_TEXT_PALETTE_FOREST
            AppThemeId.Ocean -> KEY_THEME_TEXT_PALETTE_OCEAN
            AppThemeId.Sand -> KEY_THEME_TEXT_PALETTE_SAND
            AppThemeId.Custom -> KEY_THEME_TEXT_PALETTE_CUSTOM
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosNetworkConnectionTypeProvider : NetworkConnectionTypeProvider {
    private val mutableNetworkConnectionState = MutableStateFlow(
        NetworkConnectionState(type = NetworkConnectionType.MOBILE),
    )
    private val monitor = nw_path_monitor_create()
    private var hasReceivedPath = false

    override val networkConnectionState: StateFlow<NetworkConnectionState> =
        mutableNetworkConnectionState.asStateFlow()

    init {
        nw_path_monitor_set_update_handler(monitor) { path ->
            val current = mutableNetworkConnectionState.value
            val type = if (nw_path_uses_interface_type(path, nw_interface_type_wifi)) {
                NetworkConnectionType.WIFI
            } else {
                NetworkConnectionType.MOBILE
            }
            // The monitor only reports path changes, so every update after the first one (Wi-Fi to Wi-Fi,
            // VPN, interface changes) bumps the version and invalidates the cached remote source address.
            val isConnected = nw_path_get_status(path) == nw_path_status_satisfied
            val isInitialPath = !hasReceivedPath && type == current.type
            hasReceivedPath = true
            mutableNetworkConnectionState.value = if (isInitialPath) {
                current.copy(isConnected = isConnected)
            } else {
                NetworkConnectionState(
                    type = type,
                    version = current.version + 1L,
                    isConnected = isConnected,
                )
            }
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }
}

private class IosImportSourceGateway(
    private val navidromeHttpClient: LyricsHttpClient,
    private val logger: DiagnosticLogger,
) : ImportSourceGateway {
    private val localFolderPicker = IosLocalFolderPicker()
    private val localFolderScanner = IosLocalFolderScanner()

    override suspend fun pickLocalFolder(): LocalFolderSelection? = localFolderPicker.pick()

    override suspend fun scanLocalFolder(selection: LocalFolderSelection, sourceId: String): ImportScanReport {
        return scanLocalFolder(selection, sourceId, ImportScanProgressSink.NoOp)
    }

    override suspend fun scanLocalFolder(
        selection: LocalFolderSelection,
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        return localFolderScanner.scan(selection, sourceId, progressSink)
    }

    override suspend fun testSamba(draft: SambaSourceDraft) {
        throw UiTextException(uiText(Res.string.ios_samba_files_recommended))
    }

    override suspend fun scanSamba(draft: SambaSourceDraft, sourceId: String): ImportScanReport {
        return ImportScanReport(
            emptyList(),
            warnings = listOf(
                ImportScanWarning(
                    diagnostic = "当前 iOS 构建建议通过 Files 连接 SMB。",
                    text = uiText(Res.string.ios_samba_files_recommended),
                ),
            ),
        )
    }

    override suspend fun testWebDav(draft: WebDavSourceDraft) {
        throw UiTextException(uiText(Res.string.ios_webdav_not_supported))
    }

    override suspend fun scanWebDav(draft: WebDavSourceDraft, sourceId: String): ImportScanReport {
        return ImportScanReport(
            emptyList(),
            warnings = listOf(
                ImportScanWarning(
                    diagnostic = "当前 iOS 构建暂未实现应用内 WebDAV。",
                    text = uiText(Res.string.ios_webdav_not_supported),
                ),
            ),
        )
    }

    override suspend fun testNavidrome(draft: NavidromeSourceDraft) {
        testNavidromeConnection(
            draft = draft,
            httpClient = navidromeHttpClient,
            logger = logger,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }

    override suspend fun probeNavidrome(draft: NavidromeSourceDraft): NavidromeLibraryProbe {
        return probeNavidromeLibrary(
            draft = draft,
            httpClient = navidromeHttpClient,
            logger = logger,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }

    override suspend fun scanNavidrome(draft: NavidromeSourceDraft, sourceId: String): ImportScanReport {
        return scanNavidrome(draft, sourceId, ImportScanProgressSink.NoOp)
    }

    override suspend fun scanNavidrome(
        draft: NavidromeSourceDraft,
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        return scanNavidromeLibrary(
            draft = draft,
            sourceId = sourceId,
            httpClient = navidromeHttpClient,
            supportedImportExtensions = IOS_SUPPORTED_IMPORT_AUDIO_EXTENSIONS,
            logger = logger,
            progressSink = progressSink,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }

    override suspend fun scanNavidromeStreaming(
        draft: NavidromeSourceDraft,
        sourceId: String,
        progressSink: ImportScanProgressSink,
        trackBatchSink: ImportTrackBatchSink,
    ): ImportStreamingScanReport {
        return scanNavidromeLibraryStreaming(
            draft = draft,
            sourceId = sourceId,
            httpClient = navidromeHttpClient,
            supportedImportExtensions = IOS_SUPPORTED_IMPORT_AUDIO_EXTENSIONS,
            logger = logger,
            progressSink = progressSink,
            trackBatchSink = trackBatchSink,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }

    override suspend fun testSubsonic(draft: SubsonicSourceDraft) {
        testSubsonicConnection(
            draft = draft,
            httpClient = navidromeHttpClient,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }

    override suspend fun scanSubsonic(draft: SubsonicSourceDraft, sourceId: String): ImportScanReport {
        return scanSubsonic(draft, sourceId, ImportScanProgressSink.NoOp)
    }

    override suspend fun scanSubsonic(
        draft: SubsonicSourceDraft,
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        return scanSubsonicLibrary(
            draft = draft,
            sourceId = sourceId,
            httpClient = navidromeHttpClient,
            supportedImportExtensions = IOS_SUPPORTED_IMPORT_AUDIO_EXTENSIONS,
            progressSink = progressSink,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }

    override suspend fun testEmby(draft: EmbySourceDraft, deviceId: String): EmbyCredential {
        return testEmbyConnection(
            draft = draft,
            deviceId = deviceId,
            httpClient = navidromeHttpClient,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }

    override suspend fun testEmbyCredential(
        draft: EmbySourceDraft,
        credential: EmbyCredential,
        deviceId: String,
    ) {
        testEmbyConnection(
            draft = draft,
            credential = credential,
            deviceId = deviceId,
            httpClient = navidromeHttpClient,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }

    override suspend fun scanEmby(
        draft: EmbySourceDraft,
        credential: EmbyCredential,
        sourceId: String,
        deviceId: String,
    ): ImportScanReport {
        return scanEmby(draft, credential, sourceId, deviceId, ImportScanProgressSink.NoOp)
    }

    override suspend fun scanEmby(
        draft: EmbySourceDraft,
        credential: EmbyCredential,
        sourceId: String,
        deviceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        return scanEmbyLibrary(
            draft = draft,
            credential = credential,
            deviceId = deviceId,
            sourceId = sourceId,
            httpClient = navidromeHttpClient,
            supportedImportExtensions = IOS_SUPPORTED_IMPORT_AUDIO_EXTENSIONS,
            progressSink = progressSink,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val directoryUrl: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(directoryUrl?.path)
}

@OptIn(ExperimentalForeignApi::class)
private fun keychainQuery(vararg pairs: Pair<CFTypeRef?, CFTypeRef?>): CFMutableDictionaryRef? {
    val dictionary = CFDictionaryCreateMutable(null, pairs.size.toLong(), null, null)
    pairs.forEach { (key, value) ->
        CFDictionaryAddValue(dictionary, key, value)
    }
    return dictionary
}

@OptIn(ExperimentalForeignApi::class)
private fun String.toCFValue(): CFTypeRef? = CFBridgingRetain(this)

@OptIn(ExperimentalForeignApi::class)
private fun String.toKeychainData(): CFTypeRef? {
    val bytes = encodeToByteArray()
    return bytes.usePinned { pinned ->
        CFDataCreate(null, pinned.addressOf(0).reinterpret(), bytes.size.toLong())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toUtf8String(): String {
    val byteCount = length.toInt()
    if (byteCount == 0) return ""
    val byteArray = ByteArray(byteCount)
    byteArray.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return byteArray.decodeToString()
}

private const val IOS_KEYCHAIN_SERVICE = "top.iwesley.lyn.music.credentials"
private const val KEY_USE_SAMBA_CACHE = "use_samba_cache"
private const val KEY_PLAYBACK_VOLUME = "playback_volume"
private const val KEY_SHOW_COMPACT_PLAYER_LYRICS = "show_compact_player_lyrics"
private const val KEY_AUTO_PLAY_ON_STARTUP = "auto_play_on_startup"
private const val KEY_AUTO_OPEN_PLAYER_ON_STARTUP = "auto_open_player_on_startup"
private const val KEY_PLAYER_ARTWORK_STYLE = "player_artwork_style"
private const val KEY_NAVIDROME_WIFI_AUDIO_QUALITY = "navidrome_wifi_audio_quality"
private const val KEY_NAVIDROME_MOBILE_AUDIO_QUALITY = "navidrome_mobile_audio_quality"
private const val KEY_LIBRARY_SOURCE_FILTER = "library_source_filter"
private const val KEY_FAVORITES_SOURCE_FILTER = "favorites_source_filter"
private const val KEY_ONLINE_LIBRARY_SOURCE_ID = "online_library_source_id"
private const val KEY_ONLINE_FAVORITES_SOURCE_ID = "online_favorites_source_id"
private const val KEY_ONLINE_PLAYLISTS_SOURCE_ID = "online_playlists_source_id"
private const val KEY_LIBRARY_TRACK_SORT_MODE = "library_track_sort_mode"
private const val KEY_FAVORITES_TRACK_SORT_MODE = "favorites_track_sort_mode"
private const val KEY_SELECTED_THEME = "selected_theme"
private const val KEY_LYRICS_SHARE_FONT_KEY = "lyrics_share_font_key"
private const val KEY_CUSTOM_THEME_BACKGROUND_ARGB = "custom_theme_background_argb"
private const val KEY_CUSTOM_THEME_ACCENT_ARGB = "custom_theme_accent_argb"
private const val KEY_CUSTOM_THEME_FOCUS_ARGB = "custom_theme_focus_argb"
private const val KEY_THEME_TEXT_PALETTE_CLASSIC = "theme_text_palette_classic"
private const val KEY_THEME_TEXT_PALETTE_FOREST = "theme_text_palette_forest"
private const val KEY_THEME_TEXT_PALETTE_OCEAN = "theme_text_palette_ocean"
private const val KEY_THEME_TEXT_PALETTE_SAND = "theme_text_palette_sand"
private const val KEY_THEME_TEXT_PALETTE_CUSTOM = "theme_text_palette_custom"
