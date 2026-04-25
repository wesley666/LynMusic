package top.iwesley.lyn.music.platform

import androidx.room.Room
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.iwesley.lyn.music.SharedRuntimeServices
import top.iwesley.lyn.music.buildPlayerAppComponent
import top.iwesley.lyn.music.buildSharedGraph
import top.iwesley.lyn.music.core.model.ConsoleDiagnosticLogger
import top.iwesley.lyn.music.core.model.CompactPlayerLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaybackPreferencesStore
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.DEFAULT_PLAYBACK_VOLUME
import top.iwesley.lyn.music.core.model.SambaCachePreferencesStore
import top.iwesley.lyn.music.core.model.ThemePreferencesStore
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.defaultCustomThemeTokens
import top.iwesley.lyn.music.core.model.defaultThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.normalizePlaybackVolume
import top.iwesley.lyn.music.core.model.withThemePalette
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.UnsupportedAudioTagEditorPlatformService
import top.iwesley.lyn.music.core.model.UnsupportedAudioTagGateway
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.withSecureInMemoryCache
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.openLynMusicDatabase
import top.iwesley.lyn.music.data.repository.PlayerRuntimeServices
import top.iwesley.lyn.music.domain.scanNavidromeLibrary
import top.iwesley.lyn.music.domain.testNavidromeConnection
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.LibrarySourceFilterPreferencesStore
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
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
import platform.posix.memcpy

private val IOS_SUPPORTED_IMPORT_AUDIO_EXTENSIONS = setOf(
    "mp3",
    "m4a",
    "aac",
    "wav",
    "flac",
)

fun createIosAppComponent(): top.iwesley.lyn.music.LynMusicAppComponent {
    val database = openLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(
            name = documentDirectory() + "/lynmusic.db",
        ),
    ).getOrThrow()
    val secureStore = IosKeychainCredentialStore().withSecureInMemoryCache()
    val appPreferencesStore = IosAppPreferencesStore()
    val navidromeHttpClient = IosLyricsHttpClient()
    val platform = PlatformDescriptor(
        name = "iPhone / iPad",
        capabilities = PlatformCapabilities(
            supportsLocalFolderImport = false,
            supportsSambaImport = false,
            supportsWebDavImport = false,
            supportsNavidromeImport = true,
            supportsSystemMediaControls = true,
        ),
    )
    val sharedGraph = buildSharedGraph(
        platform = platform,
        database = database,
        runtimeServices = SharedRuntimeServices(
            importSourceGateway = IosImportSourceGateway(navidromeHttpClient),
            secureCredentialStore = secureStore,
            sambaCachePreferencesStore = appPreferencesStore,
            themePreferencesStore = appPreferencesStore,
            compactPlayerLyricsPreferencesStore = appPreferencesStore,
            librarySourceFilterPreferencesStore = appPreferencesStore,
            lyricsHttpClient = navidromeHttpClient,
            artworkCacheStore = createIosArtworkCacheStore(),
            appStorageGateway = createIosAppStorageGateway(),
            deviceInfoGateway = createIosDeviceInfoGateway(),
            audioTagGateway = UnsupportedAudioTagGateway,
            audioTagEditorPlatformService = UnsupportedAudioTagEditorPlatformService,
            logger = ConsoleDiagnosticLogger(enabled = true, label = "iOS"),
        ),
    )
    return buildPlayerAppComponent(
        sharedGraph = sharedGraph,
        playerRuntimeServices = PlayerRuntimeServices(
            playbackGateway = ApplePlaybackGateway(platformLabel = "iOS"),
            playbackPreferencesStore = appPreferencesStore,
            lyricsShareFontPreferencesStore = appPreferencesStore,
            lyricsSharePlatformService = IosLyricsSharePlatformService(),
            systemPlaybackControlsPlatformService = createIosSystemPlaybackControlsPlatformService(),
        ),
    )
}

private class IosLyricsHttpClient : LyricsHttpClient {
    private val client = HttpClient(Darwin)

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        return runCatching {
            val response = client.request {
                url(request.url)
                this.method = when (request.method) {
                    RequestMethod.GET -> HttpMethod.Get
                    RequestMethod.POST -> HttpMethod.Post
                }
                request.headers.forEach { (key, value) -> headers.append(key, value) }
                request.body?.let { setBody(it) }
            }
            LyricsHttpResponse(
                statusCode = response.status.value,
                body = response.bodyAsText(),
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

private class IosAppPreferencesStore : PlaybackPreferencesStore, SambaCachePreferencesStore, ThemePreferencesStore,
    CompactPlayerLyricsPreferencesStore, LyricsShareFontPreferencesStore, LibrarySourceFilterPreferencesStore {
    private val defaults = NSUserDefaults.standardUserDefaults
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
    private val mutableLibrarySourceFilter = MutableStateFlow(readLibrarySourceFilter(KEY_LIBRARY_SOURCE_FILTER))
    private val mutableFavoritesSourceFilter = MutableStateFlow(readLibrarySourceFilter(KEY_FAVORITES_SOURCE_FILTER))
    private val mutableSelectedTheme = MutableStateFlow(readSelectedTheme())
    private val mutableCustomThemeTokens = MutableStateFlow(readCustomThemeTokens())
    private val mutableTextPalettePreferences = MutableStateFlow(readTextPalettePreferences())
    private val mutableSelectedLyricsShareFontKey = MutableStateFlow(readSelectedLyricsShareFontKey())

    override val useSambaCache: StateFlow<Boolean> = mutableUseSambaCache.asStateFlow()
    override val playbackVolume: StateFlow<Float> = mutablePlaybackVolume.asStateFlow()
    override val showCompactPlayerLyrics: StateFlow<Boolean> = mutableShowCompactPlayerLyrics.asStateFlow()
    override val selectedTheme: StateFlow<AppThemeId> = mutableSelectedTheme.asStateFlow()
    override val customThemeTokens: StateFlow<AppThemeTokens> = mutableCustomThemeTokens.asStateFlow()
    override val textPalettePreferences: StateFlow<AppThemeTextPalettePreferences> = mutableTextPalettePreferences.asStateFlow()
    override val selectedLyricsShareFontKey: StateFlow<String?> = mutableSelectedLyricsShareFontKey.asStateFlow()
    override val librarySourceFilter: StateFlow<LibrarySourceFilter> = mutableLibrarySourceFilter.asStateFlow()
    override val favoritesSourceFilter: StateFlow<LibrarySourceFilter> = mutableFavoritesSourceFilter.asStateFlow()

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

private class IosImportSourceGateway(
    private val navidromeHttpClient: LyricsHttpClient,
) : ImportSourceGateway {
    override suspend fun pickLocalFolder(): LocalFolderSelection? = null

    override suspend fun scanLocalFolder(selection: LocalFolderSelection, sourceId: String): ImportScanReport {
        return ImportScanReport(emptyList(), warnings = listOf("当前 iOS 构建未实现应用内目录扫描，请通过 Files 接入后扩展。"))
    }

    override suspend fun testSamba(draft: SambaSourceDraft) {
        error("当前 iOS 构建建议通过 Files 连接 SMB。")
    }

    override suspend fun scanSamba(draft: SambaSourceDraft, sourceId: String): ImportScanReport {
        return ImportScanReport(emptyList(), warnings = listOf("当前 iOS 构建建议通过 Files 连接 SMB。"))
    }

    override suspend fun testWebDav(draft: WebDavSourceDraft) {
        error("当前 iOS 构建暂未实现应用内 WebDAV。")
    }

    override suspend fun scanWebDav(draft: WebDavSourceDraft, sourceId: String): ImportScanReport {
        return ImportScanReport(emptyList(), warnings = listOf("当前 iOS 构建暂未实现应用内 WebDAV。"))
    }

    override suspend fun testNavidrome(draft: NavidromeSourceDraft) {
        testNavidromeConnection(draft, navidromeHttpClient)
    }

    override suspend fun scanNavidrome(draft: NavidromeSourceDraft, sourceId: String): ImportScanReport {
        return scanNavidromeLibrary(
            draft = draft,
            sourceId = sourceId,
            httpClient = navidromeHttpClient,
            supportedImportExtensions = IOS_SUPPORTED_IMPORT_AUDIO_EXTENSIONS,
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
private const val KEY_LIBRARY_SOURCE_FILTER = "library_source_filter"
private const val KEY_FAVORITES_SOURCE_FILTER = "favorites_source_filter"
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
