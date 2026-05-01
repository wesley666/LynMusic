package top.iwesley.lyn.music.platform

import androidx.room.Room
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.Properties
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.SharedRuntimeServices
import top.iwesley.lyn.music.buildPlayerAppComponent
import top.iwesley.lyn.music.buildSharedGraph
import top.iwesley.lyn.music.core.model.AudioTagGateway
import top.iwesley.lyn.music.core.model.AudioTagEditorPlatformService
import top.iwesley.lyn.music.core.model.AudioTagPatch
import top.iwesley.lyn.music.core.model.AudioTagSnapshot
import top.iwesley.lyn.music.core.model.ConsoleDiagnosticLogger
import top.iwesley.lyn.music.core.model.CompactPlayerLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.DEFAULT_SAMBA_PORT
import top.iwesley.lyn.music.core.model.DesktopVlcPreferencesStore
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportScanFailure
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.NonNavidromeAudioScanResult
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.core.model.PlaybackLoadToken
import top.iwesley.lyn.music.core.model.PlaybackPreferencesStore
import top.iwesley.lyn.music.core.model.LyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.DEFAULT_PLAYBACK_VOLUME
import top.iwesley.lyn.music.core.model.SAME_NAME_LRC_MAX_BYTES
import top.iwesley.lyn.music.core.model.SambaCachePreferencesStore
import top.iwesley.lyn.music.core.model.ThemePreferencesStore
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.defaultCustomThemeTokens
import top.iwesley.lyn.music.core.model.defaultThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.normalizePlaybackVolume
import top.iwesley.lyn.music.core.model.withThemePalette
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.SameNameLyricsFileGateway
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.VlcPathPickerPlatformService
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.buildSambaLocator
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.formatSambaEndpoint
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.joinSambaPath
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator
import top.iwesley.lyn.music.core.model.normalizeSambaPath
import top.iwesley.lyn.music.core.model.parseSambaLocator
import top.iwesley.lyn.music.core.model.parseSambaPath
import top.iwesley.lyn.music.core.model.parseNavidromeCoverLocator
import top.iwesley.lyn.music.core.model.parseNavidromeSongLocator
import top.iwesley.lyn.music.core.model.parseWebDavLocator
import top.iwesley.lyn.music.core.model.sameNameLyricsRelativePath
import top.iwesley.lyn.music.core.model.unsupportedAudioImportFailure
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.core.model.withSecureInMemoryCache
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.openLynMusicDatabase
import top.iwesley.lyn.music.data.repository.PlayerRuntimeServices
import top.iwesley.lyn.music.domain.resolveNavidromeStreamUrl
import top.iwesley.lyn.music.domain.scanNavidromeLibrary
import top.iwesley.lyn.music.domain.testNavidromeConnection
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.LibrarySourceFilterPreferencesStore
import top.iwesley.lyn.music.feature.library.TrackSortMode
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File as SmbRemoteFile
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.log.LogEventListener
import uk.co.caprica.vlcj.log.LogLevel
import uk.co.caprica.vlcj.log.NativeLog
import uk.co.caprica.vlcj.media.Media
import uk.co.caprica.vlcj.media.MediaEventAdapter
import uk.co.caprica.vlcj.media.MediaParsedStatus
import uk.co.caprica.vlcj.media.MediaRef
import uk.co.caprica.vlcj.media.Meta
import uk.co.caprica.vlcj.media.MetaData
import uk.co.caprica.vlcj.media.callback.CallbackMedia
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

fun createJvmAppComponent(): top.iwesley.lyn.music.LynMusicAppComponent {
    val database = openLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(
            name = File(File(System.getProperty("user.home")), ".lynmusic/lynmusic.db").apply {
                parentFile?.mkdirs()
            }.absolutePath,
        ),
    ).getOrThrow()
    val logger = ConsoleDiagnosticLogger(enabled = true, label = "Desktop")
    logger.info("Desktop") {
        "你好 process pid=${ProcessHandle.current().pid()}"
    }
    val secureStore = createJvmSecureCredentialStore(logger).withSecureInMemoryCache()
    val appPreferencesStore = JvmAppPreferencesStore()
    val lyricsShareFontLibraryPlatformService = JvmLyricsShareFontLibraryPlatformService()
    val playbackGateway = JvmPlaybackGateway(
        database = database,
        secureCredentialStore = secureStore,
        playbackPreferencesStore = appPreferencesStore,
        desktopVlcPreferencesStore = appPreferencesStore,
        logger = logger,
    )
    val navidromeHttpClient = JvmLyricsHttpClient()
    val platform = PlatformDescriptor(
        name = "Desktop",
        capabilities = PlatformCapabilities(
            supportsLocalFolderImport = true,
            supportsSambaImport = true,
            supportsWebDavImport = true,
            supportsNavidromeImport = true,
            supportsSystemMediaControls = false,
        ),
    )
    val sharedGraph = buildSharedGraph(
        platform = platform,
        database = database,
        runtimeServices = SharedRuntimeServices(
            importSourceGateway = JvmImportSourceGateway(logger, navidromeHttpClient),
            secureCredentialStore = secureStore,
            sambaCachePreferencesStore = appPreferencesStore,
            themePreferencesStore = appPreferencesStore,
            compactPlayerLyricsPreferencesStore = appPreferencesStore,
            autoPlayOnStartupPreferencesStore = appPreferencesStore,
            desktopVlcPreferencesStore = appPreferencesStore,
            librarySourceFilterPreferencesStore = appPreferencesStore,
            lyricsShareFontLibraryPlatformService = lyricsShareFontLibraryPlatformService,
            lyricsShareFontPreferencesStore = appPreferencesStore,
            lyricsHttpClient = navidromeHttpClient,
            artworkCacheStore = createJvmArtworkCacheStore(),
            appStorageGateway = createJvmAppStorageGateway(),
            deviceInfoGateway = createJvmDeviceInfoGateway(),
            audioTagGateway = JvmAudioTagGateway(
                database = database,
                secureCredentialStore = secureStore,
                logger = logger,
            ),
            sameNameLyricsFileGateway = JvmSameNameLyricsFileGateway(
                database = database,
                secureCredentialStore = secureStore,
                logger = logger,
            ),
            audioTagEditorPlatformService = JvmAudioTagEditorPlatformService(),
            vlcPathPickerPlatformService = JvmVlcPathPickerPlatformService(),
            logger = logger,
        ),
    )
    return buildPlayerAppComponent(
        sharedGraph = sharedGraph,
        playerRuntimeServices = PlayerRuntimeServices(
            playbackGateway = playbackGateway,
            playbackPreferencesStore = appPreferencesStore,
            lyricsSharePlatformService = JvmLyricsSharePlatformService(lyricsShareFontLibraryPlatformService),
            lyricsShareFontLibraryPlatformService = lyricsShareFontLibraryPlatformService,
            lyricsShareFontPreferencesStore = appPreferencesStore,
        ),
    )
}

private class JvmLyricsHttpClient : LyricsHttpClient {
    private val client = HttpClient(OkHttp)

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        return runCatching {
            val response = client.request {
                url(request.url)
                this.method = when (request.method) {
                    top.iwesley.lyn.music.core.model.RequestMethod.GET -> HttpMethod.Get
                    top.iwesley.lyn.music.core.model.RequestMethod.POST -> HttpMethod.Post
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

private class JvmAppPreferencesStore : PlaybackPreferencesStore, SambaCachePreferencesStore, ThemePreferencesStore,
    CompactPlayerLyricsPreferencesStore, DesktopVlcPreferencesStore, LyricsShareFontPreferencesStore,
    LibrarySourceFilterPreferencesStore {
    private val settingsFile = File(File(System.getProperty("user.home")), ".lynmusic/settings.properties").apply {
        parentFile?.mkdirs()
    }
    private val mutableUseSambaCache = MutableStateFlow(readUseSambaCache())
    private val mutablePlaybackVolume = MutableStateFlow(readPlaybackVolume())
    private val mutableShowCompactPlayerLyrics = MutableStateFlow(readShowCompactPlayerLyrics())
    private val mutableAutoPlayOnStartup = MutableStateFlow(readAutoPlayOnStartup())
    private val mutableLibrarySourceFilter = MutableStateFlow(readLibrarySourceFilter(KEY_LIBRARY_SOURCE_FILTER))
    private val mutableFavoritesSourceFilter = MutableStateFlow(readLibrarySourceFilter(KEY_FAVORITES_SOURCE_FILTER))
    private val mutableLibraryTrackSortMode = MutableStateFlow(
        readTrackSortMode(KEY_LIBRARY_TRACK_SORT_MODE, TrackSortMode.TITLE),
    )
    private val mutableFavoritesTrackSortMode = MutableStateFlow(
        readTrackSortMode(KEY_FAVORITES_TRACK_SORT_MODE, TrackSortMode.ADDED_AT),
    )
    private val mutableSelectedTheme = MutableStateFlow(readSelectedTheme())
    private val mutableCustomThemeTokens = MutableStateFlow(readCustomThemeTokens())
    private val mutableTextPalettePreferences = MutableStateFlow(readTextPalettePreferences())
    private val mutableDesktopVlcManualPath = MutableStateFlow(readDesktopVlcManualPath())
    private val mutableDesktopVlcAutoDetectedPath = MutableStateFlow<String?>(null)
    private val mutableDesktopVlcEffectivePath = MutableStateFlow(
        resolveDesktopVlcEffectivePath(
            manualPath = mutableDesktopVlcManualPath.value,
            autoDetectedPath = mutableDesktopVlcAutoDetectedPath.value,
        ),
    )
    private val mutableSelectedLyricsShareFontKey = MutableStateFlow(readSelectedLyricsShareFontKey())

    override val useSambaCache: StateFlow<Boolean> = mutableUseSambaCache.asStateFlow()
    override val playbackVolume: StateFlow<Float> = mutablePlaybackVolume.asStateFlow()
    override val showCompactPlayerLyrics: StateFlow<Boolean> = mutableShowCompactPlayerLyrics.asStateFlow()
    override val autoPlayOnStartup: StateFlow<Boolean> = mutableAutoPlayOnStartup.asStateFlow()
    override val selectedTheme: StateFlow<AppThemeId> = mutableSelectedTheme.asStateFlow()
    override val customThemeTokens: StateFlow<AppThemeTokens> = mutableCustomThemeTokens.asStateFlow()
    override val textPalettePreferences: StateFlow<AppThemeTextPalettePreferences> = mutableTextPalettePreferences.asStateFlow()
    override val desktopVlcManualPath: StateFlow<String?> = mutableDesktopVlcManualPath.asStateFlow()
    override val desktopVlcAutoDetectedPath: StateFlow<String?> = mutableDesktopVlcAutoDetectedPath.asStateFlow()
    override val desktopVlcEffectivePath: StateFlow<String?> = mutableDesktopVlcEffectivePath.asStateFlow()
    override val selectedLyricsShareFontKey: StateFlow<String?> = mutableSelectedLyricsShareFontKey.asStateFlow()
    override val librarySourceFilter: StateFlow<LibrarySourceFilter> = mutableLibrarySourceFilter.asStateFlow()
    override val favoritesSourceFilter: StateFlow<LibrarySourceFilter> = mutableFavoritesSourceFilter.asStateFlow()
    override val libraryTrackSortMode: StateFlow<TrackSortMode> = mutableLibraryTrackSortMode.asStateFlow()
    override val favoritesTrackSortMode: StateFlow<TrackSortMode> = mutableFavoritesTrackSortMode.asStateFlow()

    override suspend fun setUseSambaCache(enabled: Boolean) {
        val properties = loadProperties()
        properties.setProperty(KEY_USE_SAMBA_CACHE, enabled.toString())
        persistProperties(properties)
        mutableUseSambaCache.value = enabled
    }

    override suspend fun setPlaybackVolume(volume: Float) {
        val normalizedVolume = normalizePlaybackVolume(volume)
        val properties = loadProperties()
        properties.setProperty(KEY_PLAYBACK_VOLUME, normalizedVolume.toString())
        persistProperties(properties)
        mutablePlaybackVolume.value = normalizedVolume
    }

    override suspend fun setShowCompactPlayerLyrics(enabled: Boolean) {
        val properties = loadProperties()
        properties.setProperty(KEY_SHOW_COMPACT_PLAYER_LYRICS, enabled.toString())
        persistProperties(properties)
        mutableShowCompactPlayerLyrics.value = enabled
    }

    override suspend fun setAutoPlayOnStartup(enabled: Boolean) {
        val properties = loadProperties()
        properties.setProperty(KEY_AUTO_PLAY_ON_STARTUP, enabled.toString())
        persistProperties(properties)
        mutableAutoPlayOnStartup.value = enabled
    }

    override suspend fun setLibrarySourceFilter(filter: LibrarySourceFilter) {
        val properties = loadProperties()
        properties.setProperty(KEY_LIBRARY_SOURCE_FILTER, filter.name)
        persistProperties(properties)
        mutableLibrarySourceFilter.value = filter
    }

    override suspend fun setFavoritesSourceFilter(filter: LibrarySourceFilter) {
        val properties = loadProperties()
        properties.setProperty(KEY_FAVORITES_SOURCE_FILTER, filter.name)
        persistProperties(properties)
        mutableFavoritesSourceFilter.value = filter
    }

    override suspend fun setLibraryTrackSortMode(mode: TrackSortMode) {
        val properties = loadProperties()
        properties.setProperty(KEY_LIBRARY_TRACK_SORT_MODE, mode.name)
        persistProperties(properties)
        mutableLibraryTrackSortMode.value = mode
    }

    override suspend fun setFavoritesTrackSortMode(mode: TrackSortMode) {
        val properties = loadProperties()
        properties.setProperty(KEY_FAVORITES_TRACK_SORT_MODE, mode.name)
        persistProperties(properties)
        mutableFavoritesTrackSortMode.value = mode
    }

    override suspend fun setSelectedTheme(themeId: AppThemeId) {
        val properties = loadProperties()
        properties.setProperty(KEY_SELECTED_THEME, themeId.name)
        persistProperties(properties)
        mutableSelectedTheme.value = themeId
    }

    override suspend fun setCustomThemeTokens(tokens: AppThemeTokens) {
        val properties = loadProperties()
        properties.setProperty(KEY_CUSTOM_THEME_BACKGROUND_ARGB, tokens.backgroundArgb.toString())
        properties.setProperty(KEY_CUSTOM_THEME_ACCENT_ARGB, tokens.accentArgb.toString())
        properties.setProperty(KEY_CUSTOM_THEME_FOCUS_ARGB, tokens.focusArgb.toString())
        persistProperties(properties)
        mutableCustomThemeTokens.value = tokens
    }

    override suspend fun setTextPalette(themeId: AppThemeId, palette: AppThemeTextPalette) {
        val properties = loadProperties()
        properties.setProperty(textPaletteKey(themeId), palette.name)
        persistProperties(properties)
        mutableTextPalettePreferences.value = mutableTextPalettePreferences.value.withThemePalette(themeId, palette)
    }

    override suspend fun setDesktopVlcManualPath(path: String?) {
        val normalizedPath = path?.trim()?.takeIf { it.isNotBlank() }
        val properties = loadProperties()
        if (normalizedPath == null) {
            properties.remove(KEY_DESKTOP_VLC_MANUAL_PATH)
        } else {
            properties.setProperty(KEY_DESKTOP_VLC_MANUAL_PATH, normalizedPath)
        }
        persistProperties(properties)
        mutableDesktopVlcManualPath.value = normalizedPath
        mutableDesktopVlcEffectivePath.value = resolveDesktopVlcEffectivePath(
            manualPath = mutableDesktopVlcManualPath.value,
            autoDetectedPath = mutableDesktopVlcAutoDetectedPath.value,
        )
    }

    override suspend fun setDesktopVlcAutoDetectedPath(path: String?) {
        mutableDesktopVlcAutoDetectedPath.value = path?.trim()?.takeIf { it.isNotBlank() }
        mutableDesktopVlcEffectivePath.value = resolveDesktopVlcEffectivePath(
            manualPath = mutableDesktopVlcManualPath.value,
            autoDetectedPath = mutableDesktopVlcAutoDetectedPath.value,
        )
    }

    override suspend fun setSelectedLyricsShareFontKey(value: String?) {
        val normalizedValue = value?.trim()?.takeIf { it.isNotBlank() }
        val properties = loadProperties()
        if (normalizedValue == null) {
            properties.remove(KEY_LYRICS_SHARE_FONT_KEY)
        } else {
            properties.setProperty(KEY_LYRICS_SHARE_FONT_KEY, normalizedValue)
        }
        persistProperties(properties)
        mutableSelectedLyricsShareFontKey.value = normalizedValue
    }

    private fun readUseSambaCache(): Boolean {
        return loadProperties().getProperty(KEY_USE_SAMBA_CACHE)?.toBooleanStrictOrNull() ?: false
    }

    private fun readPlaybackVolume(): Float {
        return normalizePlaybackVolume(loadProperties().getProperty(KEY_PLAYBACK_VOLUME)?.toFloatOrNull() ?: DEFAULT_PLAYBACK_VOLUME)
    }

    private fun readShowCompactPlayerLyrics(): Boolean {
        return loadProperties().getProperty(KEY_SHOW_COMPACT_PLAYER_LYRICS)?.toBooleanStrictOrNull() ?: false
    }

    private fun readAutoPlayOnStartup(): Boolean {
        return loadProperties().getProperty(KEY_AUTO_PLAY_ON_STARTUP)?.toBooleanStrictOrNull() ?: false
    }

    private fun readLibrarySourceFilter(key: String): LibrarySourceFilter {
        val name = loadProperties().getProperty(key)
        return LibrarySourceFilter.entries.firstOrNull { it.name == name } ?: LibrarySourceFilter.ALL
    }

    private fun readTrackSortMode(key: String, defaultMode: TrackSortMode): TrackSortMode {
        val name = loadProperties().getProperty(key)
        return TrackSortMode.entries.firstOrNull { it.name == name } ?: defaultMode
    }

    private fun readSelectedTheme(): AppThemeId {
        val name = loadProperties().getProperty(KEY_SELECTED_THEME)
        return AppThemeId.entries.firstOrNull { it.name == name } ?: AppThemeId.Ocean
    }

    private fun readDesktopVlcManualPath(): String? {
        return loadProperties().getProperty(KEY_DESKTOP_VLC_MANUAL_PATH)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun readSelectedLyricsShareFontKey(): String? {
        return loadProperties().getProperty(KEY_LYRICS_SHARE_FONT_KEY)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun readCustomThemeTokens(): AppThemeTokens {
        val properties = loadProperties()
        val defaults = defaultCustomThemeTokens()
        return AppThemeTokens(
            backgroundArgb = properties.getProperty(KEY_CUSTOM_THEME_BACKGROUND_ARGB)?.toIntOrNull() ?: defaults.backgroundArgb,
            accentArgb = properties.getProperty(KEY_CUSTOM_THEME_ACCENT_ARGB)?.toIntOrNull() ?: defaults.accentArgb,
            focusArgb = properties.getProperty(KEY_CUSTOM_THEME_FOCUS_ARGB)?.toIntOrNull() ?: defaults.focusArgb,
        )
    }

    private fun readTextPalettePreferences(): AppThemeTextPalettePreferences {
        val properties = loadProperties()
        val defaults = defaultThemeTextPalettePreferences()
        return AppThemeTextPalettePreferences(
            classic = readTextPalette(properties, textPaletteKey(AppThemeId.Classic), defaults.classic),
            forest = readTextPalette(properties, textPaletteKey(AppThemeId.Forest), defaults.forest),
            ocean = readTextPalette(properties, textPaletteKey(AppThemeId.Ocean), defaults.ocean),
            sand = readTextPalette(properties, textPaletteKey(AppThemeId.Sand), defaults.sand),
            custom = readTextPalette(properties, textPaletteKey(AppThemeId.Custom), defaults.custom),
        )
    }

    private fun readTextPalette(
        properties: Properties,
        key: String,
        fallback: AppThemeTextPalette,
    ): AppThemeTextPalette {
        val name = properties.getProperty(key)
        return AppThemeTextPalette.entries.firstOrNull { it.name == name } ?: fallback
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

    private fun loadProperties(): Properties {
        val properties = Properties()
        if (settingsFile.exists()) {
            settingsFile.inputStream().use { input -> properties.load(input) }
        }
        return properties
    }

    private fun persistProperties(properties: Properties) {
        settingsFile.outputStream().use { output ->
            properties.store(output, "LynMusic settings")
        }
    }
}

private const val KEY_SELECTED_THEME = "selected_theme"
private const val KEY_CUSTOM_THEME_BACKGROUND_ARGB = "custom_theme_background_argb"
private const val KEY_CUSTOM_THEME_ACCENT_ARGB = "custom_theme_accent_argb"
private const val KEY_CUSTOM_THEME_FOCUS_ARGB = "custom_theme_focus_argb"
private const val KEY_THEME_TEXT_PALETTE_CLASSIC = "theme_text_palette_classic"
private const val KEY_THEME_TEXT_PALETTE_FOREST = "theme_text_palette_forest"
private const val KEY_THEME_TEXT_PALETTE_OCEAN = "theme_text_palette_ocean"
private const val KEY_THEME_TEXT_PALETTE_SAND = "theme_text_palette_sand"
private const val KEY_THEME_TEXT_PALETTE_CUSTOM = "theme_text_palette_custom"
private const val KEY_DESKTOP_VLC_MANUAL_PATH = "desktop_vlc_manual_path"
private const val KEY_LYRICS_SHARE_FONT_KEY = "lyrics_share_font_key"

private class JvmAudioTagGateway(
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val logger: DiagnosticLogger,
) : AudioTagGateway {
    override suspend fun canEdit(track: Track): Boolean {
        return resolveJvmLocalTrackPath(track.mediaLocator) != null
    }

    override suspend fun canWrite(track: Track): Boolean {
        return resolveJvmLocalTrackPath(track.mediaLocator) != null
    }

    override suspend fun read(track: Track): Result<AudioTagSnapshot> {
        return try {
            val path = resolveJvmLocalTrackPath(track.mediaLocator)
            Result.success(
                when {
                    path != null -> JvmAudioTagReader.readSnapshot(
                        path = path,
                        relativePath = track.relativePath.ifBlank { path.fileName?.toString().orEmpty() },
                        logger = logger,
                    )

                    parseSambaLocator(track.mediaLocator) != null -> readJvmSambaTrackSnapshot(
                        database = database,
                        secureCredentialStore = secureCredentialStore,
                        track = track,
                        logger = logger,
                    )

                    else -> error("当前仅支持桌面本地文件或 Samba 远端的音频标签读取。")
                },
            )
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    override suspend fun write(track: Track, patch: AudioTagPatch): Result<AudioTagSnapshot> {
        return runCatching {
            val path = resolveJvmLocalTrackPath(track.mediaLocator)
                ?: error("当前仅支持桌面本地文件的音频标签写回。")
            JvmAudioTagEditor.writeSnapshot(
                path = path,
                relativePath = track.relativePath.ifBlank { path.fileName?.toString().orEmpty() },
                patch = patch,
                logger = logger,
            )
        }
    }
}

private class JvmSameNameLyricsFileGateway(
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val logger: DiagnosticLogger,
) : SameNameLyricsFileGateway {
    override suspend fun readSameNameLyrics(track: Track): Result<String?> {
        return runCatching {
            when {
                parseNavidromeSongLocator(track.mediaLocator) != null -> null
                resolveJvmLocalTrackPath(track.mediaLocator) != null ->
                    readJvmLocalSameNameLyricsFile(requireNotNull(resolveJvmLocalTrackPath(track.mediaLocator)))

                parseSambaLocator(track.mediaLocator) != null -> readJvmSambaSameNameLyrics(
                    database = database,
                    secureCredentialStore = secureCredentialStore,
                    track = track,
                    logger = logger,
                )

                parseWebDavLocator(track.mediaLocator) != null -> readJvmWebDavSameNameLyrics(
                    database = database,
                    secureCredentialStore = secureCredentialStore,
                    track = track,
                    logger = logger,
                )

                else -> null
            }
        }
    }
}

private class JvmAudioTagEditorPlatformService : AudioTagEditorPlatformService {
    override suspend fun pickArtworkBytes(): Result<ByteArray?> {
        return runCatching {
            val path = JvmNativeFilePicker.pickOpenFile(
                title = "选择图片文件",
                extensionFilter = JvmFileExtensionFilter(
                    description = "图片文件",
                    rawExtensions = listOf("jpg", "jpeg", "png", "webp", "bmp", "gif"),
                ),
            ) ?: return@runCatching null
            Files.readAllBytes(path)
        }
    }

    override suspend fun loadArtworkBytes(locator: String): Result<ByteArray?> {
        return runCatching {
            val rawTarget = normalizeArtworkLocator(locator)?.trim().orEmpty()
            if (rawTarget.isBlank()) {
                null
            } else {
                val target = if (parseNavidromeCoverLocator(rawTarget) != null) {
                    NavidromeLocatorRuntime.resolveCoverArtUrl(rawTarget).orEmpty()
                } else {
                    rawTarget
                }
                when {
                    target.isBlank() -> null
                    target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true) ->
                        URL(target).openStream().use { it.readBytes() }

                    target.startsWith("file://", ignoreCase = true) ->
                        Files.readAllBytes(Path.of(URI(target)))

                    else -> Files.readAllBytes(Path.of(target))
                }
            }
        }
    }
}

private class JvmVlcPathPickerPlatformService : VlcPathPickerPlatformService {
    override suspend fun pickVlcDirectory(): Result<String?> {
        return runCatching {
            val selectedPath = JvmNativeFilePicker.pickFileOrDirectory("选择 VLC 路径") ?: return@runCatching null
            val normalizedPath = normalizeDesktopVlcSelection(selectedPath)
                ?: error(desktopVlcInvalidSelectionMessage())
            normalizedPath.toString()
        }
    }
}

private fun resolveJvmLocalTrackPath(locator: String): Path? {
    val value = locator.trim()
    if (value.isBlank()) return null
    return runCatching {
        when {
            value.startsWith("file://", ignoreCase = true) -> Path.of(URI(value))
            value.startsWith("/") -> Path.of(value)
            Regex("^[A-Za-z]:[/\\\\].*").matches(value) -> Path.of(value)
            else -> Path.of(value).takeIf { it.isAbsolute }
        }
    }.getOrNull()
}

private data class JvmSambaTagReadTarget(
    val sourceId: String,
    val endpoint: String,
    val server: String,
    val port: Int,
    val shareName: String,
    val remotePath: String,
    val relativePath: String,
    val username: String,
    val password: String,
)

private suspend fun readJvmSambaTrackSnapshot(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    track: Track,
    logger: DiagnosticLogger,
): AudioTagSnapshot {
    val target = resolveJvmSambaTagReadTarget(
        database = database,
        secureCredentialStore = secureCredentialStore,
        track = track,
    ) ?: error("当前歌曲不是 Samba 远端媒体。")
    logger.info(SAMBA_LOG_TAG) {
        "tag-read-start source=${target.sourceId} endpoint=${target.endpoint} remotePath=${target.remotePath}"
    }
    val client = SMBClient()
    return try {
        client.connect(target.server, target.port).use { connection ->
            val session = connection.authenticate(
                AuthenticationContext(target.username, target.password.toCharArray(), ""),
            )
            session.use {
                val share = session.connectShare(target.shareName) as DiskShare
                share.use {
                    val sizeBytes = share.getFileInformation(target.remotePath)
                        .standardInformation
                        .endOfFile
                    share.openFile(
                        target.remotePath,
                        setOf(AccessMask.GENERIC_READ),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null,
                    ).use { smbFile ->
                        val metadata = readJvmSambaRemoteMetadata(
                            file = smbFile,
                            sourceId = target.sourceId,
                            relativePath = target.relativePath,
                            remotePath = target.remotePath,
                            sizeBytes = sizeBytes,
                            logger = logger,
                        ) ?: error("Samba 远端没有可解析的音频标签。")
                        logger.info(SAMBA_LOG_TAG) {
                            "tag-read-complete source=${target.sourceId} endpoint=${target.endpoint} remotePath=${target.remotePath} title=${metadata.title.orEmpty()}"
                        }
                        metadata.toAudioTagSnapshot(target.relativePath) { bytes ->
                            storeJvmRemoteArtwork(target.relativePath, bytes)
                        }
                    }
                }
            }
        }
    } catch (throwable: Throwable) {
        throw IllegalStateException("读取 Samba 远端标签失败: ${throwable.message.orEmpty()}", throwable)
    } finally {
        runCatching { client.close() }
    }
}

private suspend fun readJvmSambaSameNameLyrics(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    track: Track,
    logger: DiagnosticLogger,
): String? {
    val target = resolveJvmSambaTagReadTarget(
        database = database,
        secureCredentialStore = secureCredentialStore,
        track = track,
    ) ?: return null
    val lyricsRemotePath = sameNameLyricsRelativePath(target.remotePath) ?: return null
    val client = SMBClient()
    return try {
        client.connect(target.server, target.port).use { connection ->
            val session = connection.authenticate(
                AuthenticationContext(target.username, target.password.toCharArray(), ""),
            )
            session.use {
                val share = session.connectShare(target.shareName) as DiskShare
                share.use {
                    if (!share.fileExists(lyricsRemotePath)) return null
                    val sizeBytes = share.getFileInformation(lyricsRemotePath)
                        .standardInformation
                        .endOfFile
                    if (sizeBytes <= 0L || sizeBytes > SAME_NAME_LRC_MAX_BYTES) return null
                    logger.debug(SAMBA_LOG_TAG) {
                        "same-name-lrc-read source=${target.sourceId} endpoint=${target.endpoint} remotePath=$lyricsRemotePath bytes=$sizeBytes"
                    }
                    share.openFile(
                        lyricsRemotePath,
                        setOf(AccessMask.GENERIC_READ),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null,
                    ).use { smbFile ->
                        decodeJvmSameNameLyricsBytes(readSambaBytes(smbFile, 0L, sizeBytes.toInt()))
                    }
                }
            }
        }
    } finally {
        runCatching { client.close() }
    }
}

private suspend fun resolveJvmSambaTagReadTarget(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    track: Track,
): JvmSambaTagReadTarget? {
    val samba = parseSambaLocator(track.mediaLocator) ?: return null
    val source = database.importSourceDao().getById(samba.first)?.takeIf { it.enabled } ?: return null
    val shareName = source.shareName
    val storedPort = shareName?.toIntOrNull()
    val storedPath = when {
        storedPort != null -> normalizeSambaPath(source.directoryPath)
        shareName.isNullOrBlank() -> normalizeSambaPath(source.directoryPath)
        else -> normalizeSambaPath(joinSambaPath(shareName, source.directoryPath.orEmpty()))
    }
    val sambaPath = parseSambaPath(storedPath)
        ?: error("SMB source path is missing a share name.")
    val password = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
    return JvmSambaTagReadTarget(
        sourceId = samba.first,
        endpoint = formatSambaEndpoint(source.server.orEmpty(), storedPort, storedPath),
        server = source.server.orEmpty(),
        port = storedPort ?: DEFAULT_SAMBA_PORT,
        shareName = sambaPath.shareName,
        remotePath = joinSambaPath(sambaPath.directoryPath, samba.second),
        relativePath = track.relativePath.ifBlank { samba.second },
        username = source.username.orEmpty(),
        password = password,
    )
}

private class JvmImportSourceGateway(
    private val logger: DiagnosticLogger,
    private val navidromeHttpClient: LyricsHttpClient,
) : ImportSourceGateway {
    override suspend fun pickLocalFolder(): LocalFolderSelection? {
        val path = JvmNativeFilePicker.pickDirectory("选择本地音乐文件夹") ?: return null
        return LocalFolderSelection(
            label = path.name.ifBlank { path.toString() },
            persistentReference = path.toString(),
        )
    }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun scanLocalFolder(selection: LocalFolderSelection, sourceId: String): ImportScanReport {
        val root = Path.of(selection.persistentReference)
        if (!Files.exists(root)) {
            error("Folder does not exist: ${selection.persistentReference}")
        }
        val tracks = mutableListOf<top.iwesley.lyn.music.core.model.ImportedTrackCandidate>()
        val failures = mutableListOf<ImportScanFailure>()
        var discoveredAudioFileCount = 0
        Files.walk(root).use { stream ->
            stream.filter { path -> path.isRegularFile() }
                .forEach { path ->
                    val relativePath = root.relativize(path).invariantSeparatorsPathString
                    when (classifyJvmScannedAudioFile(path.name)) {
                        NonNavidromeAudioScanResult.NOT_AUDIO -> Unit
                        NonNavidromeAudioScanResult.IMPORT_UNSUPPORTED -> {
                            discoveredAudioFileCount += 1
                            failures += unsupportedAudioImportFailure(relativePath)
                        }

                        NonNavidromeAudioScanResult.IMPORT_SUPPORTED -> {
                            discoveredAudioFileCount += 1
                            runCatching {
                                ImportedCandidateFactory.fromPath(path, relativePath, logger)
                            }.onSuccess { candidate ->
                                tracks += candidate
                            }.onFailure { throwable ->
                                failures += ImportScanFailure(
                                    relativePath = relativePath,
                                    reason = scanFailureReason(throwable),
                                )
                                logger.warn(LOCAL_IMPORT_LOG_TAG) {
                                    "candidate-failed source=$sourceId path=$relativePath reason=${throwable.message.orEmpty()}"
                                }
                            }
                        }
                    }
                }
        }
        return ImportScanReport(
            tracks = tracks,
            discoveredAudioFileCount = discoveredAudioFileCount,
            failures = failures,
        )
    }

    override suspend fun testSamba(draft: SambaSourceDraft) {
        val sambaPath = parseSambaPath(draft.path)
            ?: error("SMB 路径至少需要包含共享名，例如 Media 或 Media/Music。")
        val endpoint = formatSambaEndpoint(draft.server, draft.port, draft.path)
        val startedAt = System.currentTimeMillis()
        logger.info(SAMBA_LOG_TAG) {
            "test-connect-start server:${draft.server} port:${draft.port} user:${draft.username} " +
                    "endpoint=$endpoint hasCredentials=${draft.password.isNotBlank()}"
        }
        runCatching {
            SMBClient().connect(draft.server, draft.port ?: DEFAULT_SAMBA_PORT).use { connection ->
                logger.debug(SAMBA_LOG_TAG) {
                    "test-connect-ok endpoint=$endpoint remoteHost=${connection.remoteHostname}"
                }
                val session = connection.authenticate(AuthenticationContext(draft.username, draft.password.toCharArray(), ""))
                logger.debug(SAMBA_LOG_TAG) {
                    "test-auth-ok endpoint=$endpoint share=${sambaPath.shareName}"
                }
                val share = session.connectShare(sambaPath.shareName) as DiskShare
                if (sambaPath.directoryPath.isNotBlank() && !share.folderExists(sambaPath.directoryPath)) {
                    error("SMB 路径不存在或无法访问。")
                }
            }
        }.onSuccess {
            logger.info(SAMBA_LOG_TAG) {
                "test-connect-complete endpoint=$endpoint elapsedMs=${System.currentTimeMillis() - startedAt}"
            }
        }.onFailure { throwable ->
            logger.error(SAMBA_LOG_TAG, throwable) {
                "test-connect-failed endpoint=$endpoint elapsedMs=${System.currentTimeMillis() - startedAt}"
            }
        }.getOrThrow()
    }

    override suspend fun scanSamba(draft: SambaSourceDraft, sourceId: String): ImportScanReport {
        val sambaPath = parseSambaPath(draft.path)
            ?: error("SMB 路径至少需要包含共享名，例如 Media 或 Media/Music。")
        val endpoint = formatSambaEndpoint(draft.server, draft.port, draft.path)
        val startedAt = System.currentTimeMillis()
        logger.info(SAMBA_LOG_TAG) {
            "scan-start source=$sourceId endpoint=$endpoint hasCredentials=${draft.username.isNotBlank() || draft.password.isNotBlank()}"
        }
        return runCatching {
            val client = SMBClient()
            client.connect(draft.server, draft.port ?: DEFAULT_SAMBA_PORT).use { connection ->
                logger.debug(SAMBA_LOG_TAG) {
                    "connect-ok source=$sourceId endpoint=$endpoint remoteHost=${connection.remoteHostname}"
                }
                val session = connection.authenticate(AuthenticationContext(draft.username, draft.password.toCharArray(), ""))
                logger.debug(SAMBA_LOG_TAG) {
                    "auth-ok source=$sourceId endpoint=$endpoint share=${sambaPath.shareName}"
                }
                val share = session.connectShare(sambaPath.shareName) as DiskShare
                val baseDirectory = sambaPath.directoryPath
                val tracks = mutableListOf<top.iwesley.lyn.music.core.model.ImportedTrackCandidate>()
                val failures = mutableListOf<ImportScanFailure>()
                val discoveredAudioFileCount = collectSambaTracks(share, baseDirectory, "", sourceId, tracks, failures)
                ImportScanReport(
                    tracks = tracks,
                    discoveredAudioFileCount = discoveredAudioFileCount,
                    failures = failures,
                )
            }
        }.onSuccess { report ->
            logger.info(SAMBA_LOG_TAG) {
                "scan-complete source=$sourceId endpoint=$endpoint trackCount=${report.tracks.size} elapsedMs=${System.currentTimeMillis() - startedAt}"
            }
        }.onFailure { throwable ->
            logger.error(SAMBA_LOG_TAG, throwable) {
                "scan-failed source=$sourceId endpoint=$endpoint elapsedMs=${System.currentTimeMillis() - startedAt}"
            }
        }.getOrThrow()
    }

    override suspend fun testWebDav(draft: WebDavSourceDraft) {
        testJvmWebDavConnection(draft, logger)
    }

    override suspend fun scanWebDav(draft: WebDavSourceDraft, sourceId: String): ImportScanReport {
        return scanJvmWebDav(draft, sourceId, logger)
    }

    override suspend fun testNavidrome(draft: NavidromeSourceDraft) {
        testNavidromeConnection(draft, navidromeHttpClient, logger)
    }

    override suspend fun scanNavidrome(draft: NavidromeSourceDraft, sourceId: String): ImportScanReport {
        return scanNavidromeLibrary(
            draft = draft,
            sourceId = sourceId,
            httpClient = navidromeHttpClient,
            supportedImportExtensions = JVM_SUPPORTED_IMPORT_AUDIO_EXTENSIONS,
            logger = logger,
        )
    }

    private fun collectSambaTracks(
        share: DiskShare,
        baseDirectory: String,
        relativeDirectory: String,
        sourceId: String,
        sink: MutableList<top.iwesley.lyn.music.core.model.ImportedTrackCandidate>,
        failures: MutableList<ImportScanFailure>,
    ): Int {
        var discoveredAudioFileCount = 0
        val listPath = joinSegments(baseDirectory, relativeDirectory)
        share.list(listPath).forEach { fileInfo ->
            val name = fileInfo.fileName
            if (name == "." || name == "..") return@forEach
            val childRelative = joinSegments(relativeDirectory, name)
            val childPath = joinSegments(baseDirectory, childRelative)
            val isDirectory = share.folderExists(childPath)
            if (isDirectory) {
                discoveredAudioFileCount += collectSambaTracks(
                    share = share,
                    baseDirectory = baseDirectory,
                    relativeDirectory = childRelative,
                    sourceId = sourceId,
                    sink = sink,
                    failures = failures,
                )
            } else {
                when (classifyJvmScannedAudioFile(name)) {
                    NonNavidromeAudioScanResult.NOT_AUDIO -> Unit
                    NonNavidromeAudioScanResult.IMPORT_UNSUPPORTED -> {
                        discoveredAudioFileCount += 1
                        failures += unsupportedAudioImportFailure(childRelative)
                    }

                    NonNavidromeAudioScanResult.IMPORT_SUPPORTED -> {
                        discoveredAudioFileCount += 1
                        val sizeBytes = runCatching { fileInfo.endOfFile }.getOrDefault(0L)
                        runCatching {
                            resolveJvmSambaScanCandidate(
                                share = share,
                                sourceId = sourceId,
                                relativePath = childRelative,
                                remotePath = childPath,
                                sizeBytes = sizeBytes,
                            )
                        }.onFailure { throwable ->
                            logger.warn(SAMBA_LOG_TAG) {
                                "metadata-failed source=$sourceId remotePath=$childPath reason=${throwable.message.orEmpty()}"
                            }
                        }.recoverCatching {
                            ImportedCandidateFactory.fromRemotePath(
                                sourceId = sourceId,
                                relativePath = childRelative,
                                sizeBytes = sizeBytes,
                            )
                        }.onSuccess { candidate ->
                            sink += candidate
                        }.onFailure { throwable ->
                            failures += ImportScanFailure(
                                relativePath = childRelative,
                                reason = scanFailureReason(throwable),
                            )
                        }
                    }
                }
            }
        }
        return discoveredAudioFileCount
    }

    private fun resolveJvmSambaScanCandidate(
        share: DiskShare,
        sourceId: String,
        relativePath: String,
        remotePath: String,
        sizeBytes: Long,
    ): top.iwesley.lyn.music.core.model.ImportedTrackCandidate {
        val fallback = ImportedCandidateFactory.fromRemotePath(
            sourceId = sourceId,
            relativePath = relativePath,
            sizeBytes = sizeBytes,
        )
        if (sizeBytes <= 0L) return fallback
        share.openFile(
            remotePath,
            setOf(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        ).use { smbFile ->
            val metadata = readJvmSambaRemoteMetadata(
                file = smbFile,
                sourceId = sourceId,
                relativePath = relativePath,
                remotePath = remotePath,
                sizeBytes = sizeBytes,
                logger = logger,
            )
            if (metadata == null || !metadata.hasMeaningfulMetadata(relativePath)) {
                logger.info(SAMBA_LOG_TAG) {
                    "metadata-miss source=$sourceId remotePath=$remotePath"
                }
                return fallback
            }
            val candidate = ImportedCandidateFactory.fromRemoteMetadata(
                sourceId = sourceId,
                relativePath = relativePath,
                sizeBytes = sizeBytes,
                metadata = metadata,
                storeArtwork = { bytes -> storeJvmRemoteArtwork(relativePath, bytes) },
            )
            logger.info(SAMBA_LOG_TAG) {
                "metadata-hit source=$sourceId remotePath=$remotePath title=${candidate.title} artist=${candidate.artistName.orEmpty()} album=${candidate.albumTitle.orEmpty()}"
            }
            return candidate
        }
    }
}

private fun readJvmSambaRemoteMetadata(
    file: SmbRemoteFile,
    sourceId: String,
    relativePath: String,
    remotePath: String,
    sizeBytes: Long,
    logger: DiagnosticLogger,
): RemoteAudioMetadata? {
    val initialHeadBytes = sizeBytes
        .coerceAtMost(RemoteAudioMetadataProbe.HEAD_PROBE_BYTES)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
    var totalProbeBytes = initialHeadBytes.toLong()
    var headBytes = readSambaBytes(file, 0L, initialHeadBytes)
    val requiredHeadBytes = RemoteAudioMetadataProbe.requiredExpandedHeadBytes(relativePath, headBytes)
    if (requiredHeadBytes != null && requiredHeadBytes > headBytes.size) {
        if (requiredHeadBytes > RemoteAudioMetadataProbe.MAX_HEAD_PROBE_BYTES) {
            logger.info(SAMBA_LOG_TAG) {
                "metadata-skip source=$sourceId remotePath=$remotePath reason=head-too-large requested=$requiredHeadBytes"
            }
            return null
        }
        val expandedHeadBytes = requiredHeadBytes
            .coerceAtMost(sizeBytes)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        totalProbeBytes = expandedHeadBytes.toLong()
        headBytes = readSambaBytes(file, 0L, expandedHeadBytes)
        logger.debug(SAMBA_LOG_TAG) {
            "metadata-range-expand source=$sourceId remotePath=$remotePath bytes=${headBytes.size}"
        }
    }
    val tailBytes = if (RemoteAudioMetadataProbe.shouldReadTail(relativePath)) {
        val requestedTailBytes = sizeBytes
            .coerceAtMost(RemoteAudioMetadataProbe.TAIL_PROBE_BYTES)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        if (totalProbeBytes + requestedTailBytes > RemoteAudioMetadataProbe.MAX_TOTAL_PROBE_BYTES) {
            logger.info(SAMBA_LOG_TAG) {
                "metadata-skip source=$sourceId remotePath=$remotePath reason=tail-over-budget requested=$requestedTailBytes"
            }
            null
        } else {
            totalProbeBytes += requestedTailBytes
            readSambaBytes(
                file = file,
                fileOffset = (sizeBytes - requestedTailBytes.toLong()).coerceAtLeast(0L),
                length = requestedTailBytes,
            )
        }
    } else {
        null
    }
    logger.debug(SAMBA_LOG_TAG) {
        "metadata-range-read source=$sourceId remotePath=$remotePath head=${headBytes.size} tail=${tailBytes?.size ?: 0}"
    }
    return RemoteAudioMetadataProbe.parse(
        relativePath = relativePath,
        headBytes = headBytes,
        tailBytes = tailBytes,
    )
}

private class JvmPlaybackGateway(
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val playbackPreferencesStore: PlaybackPreferencesStore,
    private val desktopVlcPreferencesStore: DesktopVlcPreferencesStore,
    private val logger: DiagnosticLogger,
) : PlaybackGateway {
    private val mutableState = MutableStateFlow(PlaybackGatewayState())
    private val scope = CoroutineScope(Dispatchers.IO)
    private val autoDiscovery = createDesktopAutoVlcDiscovery(logger)
    private val autoDetectedVlcPath = autoDiscovery?.discoveredPath()?.trim()?.takeIf { it.isNotBlank() }
    private val preferredVlcPath = resolveDesktopVlcEffectivePath(
        manualPath = desktopVlcPreferencesStore.desktopVlcManualPath.value,
        autoDetectedPath = autoDetectedVlcPath,
    )
    private val discovery = if (desktopVlcPreferencesStore.desktopVlcManualPath.value.isNullOrBlank()) {
        autoDiscovery ?: NativeDiscovery()
    } else {
        createDesktopVlcDiscovery(preferredVlcPath)
    }
    private val vlcRuntime = createJvmVlcRuntime(discovery, logger)
    private val factory = vlcRuntime?.factory
    private val nativeLog = vlcRuntime?.nativeLog
    private val mediaPlayer = vlcRuntime?.mediaPlayer
    private val sambaCacheDir = File(File(System.getProperty("user.home")), ".lynmusic/cache").apply {
        mkdirs()
    }
    private var currentCallbackMedia: CallbackMedia? = null
    private val recentVlcLogs = ArrayDeque<String>(MAX_RECENT_VLC_LOGS)
    @Volatile
    private var currentPlaybackTarget: String? = null
    @Volatile
    private var currentSourceReference: String? = null
    @Volatile
    private var currentTrackForMetadata: Track? = null
    private val nativeLogListener = object : LogEventListener {
        override fun log(
            level: LogLevel,
            module: String?,
            file: String?,
            line: Int?,
            name: String?,
            header: String?,
            id: Int?,
            message: String?,
        ) {
            val normalizedMessage = message?.trim()?.takeIf { it.isNotEmpty() } ?: return
            val entry = buildString {
                append(level.name)
                module?.takeIf { it.isNotBlank() }?.let { append(" module=").append(it) }
                name?.takeIf { it.isNotBlank() }?.let { append(" name=").append(it) }
                header?.takeIf { it.isNotBlank() }?.let { append(" header=").append(it) }
                file?.takeIf { it.isNotBlank() }?.let {
                    append(" file=").append(it)
                    line?.let { lineNumber -> append(':').append(lineNumber) }
                }
                id?.let { append(" id=").append(it) }
                append(" message=").append(normalizedMessage)
            }
            rememberRecentVlcLog(entry)
            when (level) {
                LogLevel.ERROR -> logger.error(VLC_LOG_TAG) { entry }
                LogLevel.WARNING -> logger.warn(VLC_LOG_TAG) { entry }
                else -> logger.debug(VLC_LOG_TAG) { entry }
            }
        }
    }

    override val state: StateFlow<PlaybackGatewayState> = mutableState.asStateFlow()

    init {
        logger.info(SAMBA_LOG_TAG) {
            "cache-dir path=${sambaCacheDir.absolutePath}"
        }
        scope.launch {
            desktopVlcPreferencesStore.setDesktopVlcAutoDetectedPath(autoDetectedVlcPath)
        }
        logger.info(VLC_LOG_TAG) {
            "native-discovery autoDetectedPath=${autoDetectedVlcPath.orEmpty()} manualPath=${desktopVlcPreferencesStore.desktopVlcManualPath.value.orEmpty()} effectivePath=${preferredVlcPath.orEmpty()}"
        }
        if (mediaPlayer == null) {
            logger.warn(VLC_LOG_TAG) {
                "playback-disabled reason=vlc-native-unavailable"
            }
        }
        nativeLog?.apply {
            setLevel(LogLevel.NOTICE)
            addLogListener(nativeLogListener)
        }
        mediaPlayer?.events()?.addMediaEventListener(object  : MediaEventAdapter() {
            override fun mediaDurationChanged(media: Media?, newDuration: Long) {
                super.mediaDurationChanged(media, newDuration)
                logger.info(VLC_LOG_TAG) {
                    "mediaDurationChanged $newDuration"
                }
            }

            override fun mediaParsedChanged(media: Media?, newStatus: MediaParsedStatus?) {
                super.mediaParsedChanged(media, newStatus)
                logger.info(VLC_LOG_TAG) {
                    "mediaParsedChanged $newStatus ${media?.info()?.duration()}"
                }
            }

        })
        mediaPlayer?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun mediaPlayerReady(mediaPlayer: MediaPlayer?) {
                val activePlayer = mediaPlayer ?: return
                val track = currentTrackForMetadata
                val playbackTarget = currentPlaybackTarget ?: return
                val parseAccepted = activePlayer.media().parsing().parse(3_000)
                val parseStatus = activePlayer.media().parsing().status()
                val info = activePlayer.media().info()
                val metaData = activePlayer.media().meta().asMetaData()
                mutableState.update {
                    it.copy(
                        durationMs = info.duration().coerceAtLeast(0L),
                        canSeek = activePlayer.status().isSeekable(),
                        metadataTitle = resolveJvmVlcMetadataFallback(
                            primaryValue = track?.title,
                            vlcValue = sanitizeJvmVlcMetadataTitle(metaData.value(Meta.TITLE)),
                            previousValue = it.metadataTitle,
                        ),
                        metadataArtistName = resolveJvmVlcMetadataFallback(
                            primaryValue = track?.artistName,
                            vlcValue = metaData.value(Meta.ARTIST)
                                .ifBlank { metaData.value(Meta.ALBUM_ARTIST) },
                            previousValue = it.metadataArtistName,
                        ),
                        metadataAlbumTitle = resolveJvmVlcMetadataFallback(
                            primaryValue = track?.albumTitle,
                            vlcValue = metaData.value(Meta.ALBUM),
                            previousValue = it.metadataAlbumTitle,
                        ),
                        errorMessage = null,
                    )
                }
                logger.info(VLC_LOG_TAG) {
                    buildVlcMetadataLogMessage(
                        track = track,
                        playbackTarget = playbackTarget,
                        sourceReference = currentSourceReference,
                        parseAccepted = parseAccepted,
                        parseStatus = formatJvmVlcParseStatus(parseStatus),
                        durationMs = info.duration(),
                        metaData = metaData,
                    )
                }
            }

            override fun playing(mediaPlayer: MediaPlayer?) {
                mutableState.update {
                    it.copy(
                        isPlaying = true,
                        canSeek = mediaPlayer?.status()?.isSeekable() ?: it.canSeek,
                        errorMessage = null,
                    )
                }
            }

            override fun paused(mediaPlayer: MediaPlayer?) {
                mutableState.update {
                    it.copy(isPlaying = false)
                }
            }

            override fun stopped(mediaPlayer: MediaPlayer?) {
                mutableState.update {
                    it.copy(
                        isPlaying = false,
                        positionMs = 0L,
                        canSeek = false,
                    )
                }
            }

            override fun timeChanged(mediaPlayer: MediaPlayer?, newTime: Long) {
                mutableState.update {
                    it.copy(positionMs = newTime.coerceAtLeast(0L))
                }
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer?, newLength: Long) {
                logger.info(VLC_LOG_TAG) {
                    "lengthChanged:${newLength}"
                }
                mutableState.update {
                    it.copy(
                        durationMs = newLength.coerceAtLeast(0L),
                        canSeek = mediaPlayer?.status()?.isSeekable() ?: it.canSeek,
                    )
                }
            }

            override fun seekableChanged(mediaPlayer: MediaPlayer?, newSeekable: Int) {
                mutableState.update {
                    it.copy(canSeek = newSeekable != 0)
                }
            }

            override fun mediaChanged(mediaPlayer: MediaPlayer?, media: MediaRef?) {
                super.mediaChanged(mediaPlayer, media)
            }

            override fun finished(mediaPlayer: MediaPlayer?) {
                mutableState.update {
                    it.copy(
                        isPlaying = false,
                        positionMs = 0L,
                        canSeek = false,
                        completionCount = it.completionCount + 1,
                    )
                }
            }

            override fun error(mediaPlayer: MediaPlayer?) {
                logger.error(VLC_LOG_TAG) {
                    "playback-error target=${currentPlaybackTarget.orEmpty()} source=${currentSourceReference.orEmpty()} recentLogs=${recentVlcLogSummary()}"
                }
                mutableState.update {
                    it.copy(
                        errorMessage = "桌面播放器无法播放当前媒体。",
                        canSeek = false,
                        errorRevision = it.errorRevision + 1L,
                    )
                }
            }
        })
    }

    override suspend fun load(
        track: Track,
        playWhenReady: Boolean,
        startPositionMs: Long,
        loadToken: PlaybackLoadToken,
    ) {
        val activeMediaPlayer = mediaPlayer
        if (activeMediaPlayer == null) {
            handleVlcUnavailable(
                action = "load",
                positionMs = startPositionMs,
                errorMessage = if (playWhenReady) DESKTOP_VLC_UNAVAILABLE_MESSAGE else null,
                clearMetadata = true,
            )
            currentCallbackMedia = null
            currentPlaybackTarget = null
            currentSourceReference = track.mediaLocator
            currentTrackForMetadata = track
            return
        }
        try {
            runCatching { activeMediaPlayer.controls().stop() }
            currentCallbackMedia = null
            currentPlaybackTarget = null
            currentSourceReference = null
            currentTrackForMetadata = track
            val webDavTarget = resolveJvmWebDavPlaybackTarget(
                database = database,
                secureCredentialStore = secureCredentialStore,
                locator = track.mediaLocator,
                logger = logger,
            )
            val sambaTarget = if (webDavTarget == null && shouldUseJvmSambaCallback(track.mediaLocator, playbackPreferencesStore.useSambaCache.value)) {
                resolveJvmSambaPlaybackTarget(
                    database = database,
                    secureCredentialStore = secureCredentialStore,
                    locator = track.mediaLocator,
                    logger = logger,
                )
            } else {
                null
            }
            val currentNavidromeAudioQuality =
                if (webDavTarget == null && sambaTarget == null && parseNavidromeSongLocator(track.mediaLocator) != null) {
                    NavidromeAudioQuality.Original
                } else {
                    null
                }
            val actualPlaybackSource = when {
                sambaTarget != null -> sambaTarget.sourceReference
                webDavTarget != null -> webDavTarget.requestUrl
                else -> resolveLocator(track.mediaLocator)
            }
            val sourceReference = when {
                parseNavidromeSongLocator(track.mediaLocator) != null -> track.mediaLocator
                else -> actualPlaybackSource
            }
            if (!loadToken.isCurrent()) {
                logger.debug(VLC_LOG_TAG) {
                    "load-discarded-stale request=${loadToken.requestId} track=${track.id} before-start"
                }
                return
            }
            val playbackTarget = when {
                webDavTarget != null -> "webdav-callback://${track.id}"
                sambaTarget != null -> buildJvmSambaPlaybackTarget(track.id)
                else -> sourceReference
            }
            currentPlaybackTarget = playbackTarget
            currentSourceReference = sourceReference
            currentCallbackMedia = webDavTarget?.media ?: sambaTarget?.media
            mutableState.update {
                it.copy(
                    isPlaying = playWhenReady,
                    positionMs = 0L,
                    durationMs = 0L,
                    canSeek = false,
                    metadataTitle = null,
                    metadataArtistName = null,
                    metadataAlbumTitle = null,
                    currentNavidromeAudioQuality = currentNavidromeAudioQuality,
                    errorMessage = null,
                )
            }
            val started = if (playWhenReady) {
                if (webDavTarget != null) {
                    activeMediaPlayer.media().start(webDavTarget.media)
                } else if (sambaTarget != null) {
                    activeMediaPlayer.media().start(sambaTarget.media)
                } else {
                    activeMediaPlayer.media().start(actualPlaybackSource)
                }
            } else {
                if (webDavTarget != null) {
                    activeMediaPlayer.media().startPaused(webDavTarget.media)
                } else if (sambaTarget != null) {
                    activeMediaPlayer.media().startPaused(sambaTarget.media)
                } else {
                    activeMediaPlayer.media().startPaused(actualPlaybackSource)
                }
            }
            if (!started) {
                logger.error(VLC_LOG_TAG) {
                    "start-failed target=$playbackTarget source=$sourceReference playWhenReady=$playWhenReady recentLogs=${recentVlcLogSummary()}"
                }
            }
            check(started) { "Unable to load media $playbackTarget" }
            if (webDavTarget != null) {
                val expectedTrackId = track.id
                val expectedSourceReference = sourceReference
                scope.launch {
                    val metadata = requestJvmWebDavMetadata(
                        database = database,
                        secureCredentialStore = secureCredentialStore,
                        locator = track.mediaLocator,
                        logger = logger,
                    ) ?: return@launch
                    if (currentTrackForMetadata?.id != expectedTrackId || currentSourceReference != expectedSourceReference) return@launch
                    mutableState.update {
                        it.copy(
                            metadataTitle = metadata.title.takeIf { value -> value.isNotBlank() } ?: it.metadataTitle,
                            metadataArtistName = metadata.artistName?.takeIf { value -> value.isNotBlank() } ?: it.metadataArtistName,
                            metadataAlbumTitle = metadata.albumTitle?.takeIf { value -> value.isNotBlank() } ?: it.metadataAlbumTitle,
                            durationMs = metadata.durationMs.takeIf { value -> value > 0L } ?: it.durationMs,
                            canSeek = activeMediaPlayer.status().isSeekable(),
                        )
                    }
                }
            }
            if (startPositionMs > 0) {
                activeMediaPlayer.controls().setTime(startPositionMs)
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            logger.error(VLC_LOG_TAG, throwable) {
                "load-failed track=${track.id} locator=${track.mediaLocator} playWhenReady=$playWhenReady startPositionMs=$startPositionMs target=${currentPlaybackTarget.orEmpty()} source=${currentSourceReference.orEmpty()}"
            }
            currentCallbackMedia = null
            mutableState.update {
                it.copy(
                    isPlaying = false,
                    positionMs = startPositionMs.coerceAtLeast(0L),
                    durationMs = 0L,
                    canSeek = false,
                    errorMessage = buildJvmPlaybackLoadFailureMessage(throwable),
                    errorRevision = it.errorRevision + 1L,
                )
            }
        }
    }

    override suspend fun play() {
        val activeMediaPlayer = mediaPlayer
        if (activeMediaPlayer == null) {
            handleVlcUnavailable(action = "play")
            return
        }
        activeMediaPlayer.controls().play()
    }

    override suspend fun pause() {
        val activeMediaPlayer = mediaPlayer
        if (activeMediaPlayer == null) {
            mutableState.update { it.copy(isPlaying = false) }
            return
        }
        activeMediaPlayer.controls().pause()
    }

    override suspend fun seekTo(positionMs: Long) {
        val activeMediaPlayer = mediaPlayer
        if (activeMediaPlayer == null) {
            handleVlcUnavailable(
                action = "seek",
                positionMs = positionMs,
            )
            return
        }
        if (!activeMediaPlayer.status().isSeekable()) {
            mutableState.update { it.copy(canSeek = false) }
            return
        }
        activeMediaPlayer.controls().setTime(positionMs)
    }

    override suspend fun setVolume(volume: Float) {
        val normalized = volume.coerceIn(0f, 1f)
        mediaPlayer?.audio()?.setVolume((normalized * 100).roundToInt())
        mutableState.update { it.copy(volume = normalized) }
    }

    override suspend fun release() {
        currentTrackForMetadata = null
        currentCallbackMedia = null
        currentPlaybackTarget = null
        currentSourceReference = null
        mediaPlayer?.release()
        nativeLog?.removeLogListener(nativeLogListener)
        nativeLog?.release()
        factory?.release()
        scope.cancel()
    }

    private suspend fun resolveLocator(locator: String): String {
        resolveNavidromeStreamUrl(database, secureCredentialStore, locator)?.let { return it }
        val samba = parseSambaLocator(locator) ?: return locator
        val source = database.importSourceDao().getById(samba.first)?.takeIf { it.enabled }
            ?: error("Samba 来源不可用。")
        val shareName = source.shareName
        val storedPort = shareName?.toIntOrNull()
        val storedPath = when {
            storedPort != null -> normalizeSambaPath(source.directoryPath)
            shareName.isNullOrBlank() -> normalizeSambaPath(source.directoryPath)
            else -> normalizeSambaPath(joinSambaPath(shareName, source.directoryPath.orEmpty()))
        }
        val sambaPath = parseSambaPath(storedPath)
            ?: error("SMB source path is missing a share name.")
        val endpoint = formatSambaEndpoint(source.server.orEmpty(), storedPort, storedPath)
        val password = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
        val username = source.username.orEmpty()
        val remotePath = joinSambaPath(sambaPath.directoryPath, samba.second)
        if (!playbackPreferencesStore.useSambaCache.value) {
            error("Desktop Samba direct-link playback is disabled. Expected SMB callback target for locator=$locator")
        }
        val cacheFile = File(sambaCacheDir, buildSambaCacheFileName(samba.first, remotePath))
        if (cacheFile.exists()) {
            logger.debug(SAMBA_LOG_TAG) {
                "cache-hit source=${samba.first} endpoint=$endpoint remotePath=$remotePath cache=${cacheFile.absolutePath}"
            }
            return cacheFile.absolutePath
        }
        val startedAt = System.currentTimeMillis()
        logger.info(SAMBA_LOG_TAG) {
            "stream-fetch-start source=${samba.first} endpoint=$endpoint remotePath=$remotePath"
        }
        runCatching {
            val client = SMBClient()
            client.connect(source.server.orEmpty(), storedPort ?: DEFAULT_SAMBA_PORT).use { connection ->
                logger.debug(SAMBA_LOG_TAG) {
                    "stream-connect-ok source=${samba.first} endpoint=$endpoint remoteHost=${connection.remoteHostname}"
                }
                val session = connection.authenticate(
                    AuthenticationContext(source.username.orEmpty(), password.toCharArray(), ""),
                )
                val share = session.connectShare(sambaPath.shareName) as DiskShare
                share.openFile(
                    remotePath,
                    setOf(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null,
                ).use { smbFile ->
                    cacheFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var offset = 0L
                        while (true) {
                            val read = smbFile.read(buffer, offset)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            offset += read
                        }
                    }
                }
            }
        }.onSuccess {
            logger.info(SAMBA_LOG_TAG) {
                "stream-fetch-complete source=${samba.first} endpoint=$endpoint remotePath=$remotePath size=${cacheFile.length()} elapsedMs=${System.currentTimeMillis() - startedAt}"
            }
        }.onFailure { throwable ->
            cacheFile.delete()
            logger.error(SAMBA_LOG_TAG, throwable) {
                "stream-fetch-failed source=${samba.first} endpoint=$endpoint remotePath=$remotePath elapsedMs=${System.currentTimeMillis() - startedAt}"
            }
            throw throwable
        }
        return cacheFile.absolutePath
    }

    private fun rememberRecentVlcLog(entry: String) {
        synchronized(recentVlcLogs) {
            if (recentVlcLogs.size >= MAX_RECENT_VLC_LOGS) {
                recentVlcLogs.removeFirst()
            }
            recentVlcLogs.addLast(entry)
        }
    }

    private fun recentVlcLogSummary(): String {
        return synchronized(recentVlcLogs) {
            if (recentVlcLogs.isEmpty()) {
                "none"
            } else {
                recentVlcLogs.joinToString(separator = " || ")
            }
        }
    }

    private fun handleVlcUnavailable(
        action: String,
        positionMs: Long? = null,
        errorMessage: String? = DESKTOP_VLC_UNAVAILABLE_MESSAGE,
        clearMetadata: Boolean = false,
    ) {
        logger.warn(VLC_LOG_TAG) {
            "$action-unavailable reason=vlc-native-unavailable"
        }
        mutableState.update { state ->
            state.copy(
                isPlaying = false,
                positionMs = positionMs?.coerceAtLeast(0L) ?: state.positionMs,
                durationMs = 0L,
                canSeek = false,
                metadataTitle = if (clearMetadata) null else state.metadataTitle,
                metadataArtistName = if (clearMetadata) null else state.metadataArtistName,
                metadataAlbumTitle = if (clearMetadata) null else state.metadataAlbumTitle,
                errorMessage = errorMessage,
                errorRevision = if (errorMessage != null) state.errorRevision + 1L else state.errorRevision,
            )
        }
    }
}

private data class JvmVlcRuntime(
    val factory: MediaPlayerFactory,
    val mediaPlayer: MediaPlayer,
    val nativeLog: NativeLog?,
)

private fun createDesktopAutoVlcDiscovery(logger: DiagnosticLogger): NativeDiscovery? {
    return runCatching {
        NativeDiscovery().apply { discover() }
    }.onFailure { throwable ->
        logger.warn(VLC_LOG_TAG) {
            "native-discovery-failed message=${throwable.message.orEmpty()}"
        }
    }.getOrNull()
}

private fun createJvmVlcRuntime(
    discovery: NativeDiscovery,
    logger: DiagnosticLogger,
): JvmVlcRuntime? {
    var createdFactory: MediaPlayerFactory? = null
    var createdNativeLog: NativeLog? = null
    return runCatching {
        val factory = MediaPlayerFactory(discovery)
        createdFactory = factory
        val nativeLog = runCatching { factory.application().newLog() }
            .onFailure { throwable ->
                logger.warn(VLC_LOG_TAG) {
                    "native-log-init-failed message=${throwable.message.orEmpty()}"
                }
            }
            .getOrNull()
        createdNativeLog = nativeLog
        val mediaPlayer = factory.mediaPlayers().newMediaPlayer()
        JvmVlcRuntime(
            factory = factory,
            mediaPlayer = mediaPlayer,
            nativeLog = nativeLog,
        )
    }.onFailure { throwable ->
        logger.error(VLC_LOG_TAG, throwable) {
            "native-init-failed message=${throwable.message.orEmpty()}"
        }
        createdNativeLog?.release()
        createdFactory?.release()
    }.getOrNull()
}

private object ImportedCandidateFactory {
    fun fromPath(
        path: Path,
        relativePath: String,
        logger: DiagnosticLogger,
    ): top.iwesley.lyn.music.core.model.ImportedTrackCandidate {
        return JvmAudioTagReader.read(path, relativePath, logger)
    }

    fun fromRemotePath(
        sourceId: String,
        relativePath: String,
        sizeBytes: Long = 0L,
    ): top.iwesley.lyn.music.core.model.ImportedTrackCandidate {
        val name = relativePath.substringAfterLast('/').substringBeforeLast('.')
        return top.iwesley.lyn.music.core.model.ImportedTrackCandidate(
            title = name,
            mediaLocator = buildSambaLocator(sourceId, relativePath),
            relativePath = relativePath,
            sizeBytes = sizeBytes,
        )
    }

    fun fromRemoteMetadata(
        sourceId: String,
        relativePath: String,
        sizeBytes: Long,
        metadata: RemoteAudioMetadata,
        storeArtwork: (ByteArray) -> String?,
    ): top.iwesley.lyn.music.core.model.ImportedTrackCandidate {
        val fallbackTitle = relativePath.substringAfterLast('/').substringBeforeLast('.')
        return top.iwesley.lyn.music.core.model.ImportedTrackCandidate(
            title = metadata.title?.trim()?.takeIf { it.isNotBlank() } ?: fallbackTitle,
            artistName = metadata.artistName?.trim()?.takeIf { it.isNotBlank() },
            albumTitle = metadata.albumTitle?.trim()?.takeIf { it.isNotBlank() },
            durationMs = metadata.durationMs?.coerceAtLeast(0L) ?: 0L,
            trackNumber = metadata.trackNumber,
            discNumber = metadata.discNumber,
            mediaLocator = buildSambaLocator(sourceId, relativePath),
            relativePath = relativePath,
            artworkLocator = metadata.artworkBytes?.takeIf { it.isNotEmpty() }?.let(storeArtwork),
            embeddedLyrics = metadata.embeddedLyrics?.trim()?.takeIf { it.isNotBlank() },
            sizeBytes = sizeBytes,
        )
    }
}

private fun readSambaBytes(
    file: SmbRemoteFile,
    fileOffset: Long,
    length: Int,
): ByteArray {
    if (length <= 0) return ByteArray(0)
    val buffer = ByteArray(length)
    var totalRead = 0
    var currentOffset = fileOffset
    while (totalRead < length) {
        val read = file.read(buffer, currentOffset, totalRead, length - totalRead)
        if (read <= 0) break
        totalRead += read
        currentOffset += read.toLong()
    }
    return if (totalRead == buffer.size) buffer else buffer.copyOf(totalRead)
}

private fun RemoteAudioMetadata.toAudioTagSnapshot(
    relativePath: String,
    storeArtwork: (ByteArray) -> String?,
): AudioTagSnapshot {
    return AudioTagSnapshot(
        title = title?.trim()?.takeIf { it.isNotBlank() } ?: relativePath.substringAfterLast('/').substringBeforeLast('.'),
        artistName = artistName?.trim()?.takeIf { it.isNotBlank() },
        albumTitle = albumTitle?.trim()?.takeIf { it.isNotBlank() },
        trackNumber = trackNumber,
        discNumber = discNumber,
        embeddedLyrics = embeddedLyrics?.trim()?.takeIf { it.isNotBlank() },
        artworkLocator = artworkBytes?.takeIf { it.isNotEmpty() }?.let(storeArtwork),
    )
}

private fun storeJvmRemoteArtwork(relativePath: String, bytes: ByteArray): String? {
    if (bytes.isEmpty()) return null
    val fileName = buildString {
        append(relativePath.hashCode().toUInt().toString(16))
        append('-')
        append(bytes.contentHashCode().toUInt().toString(16))
        append(inferArtworkFileExtension(bytes = bytes))
    }
    val target = File(jvmRemoteArtworkDirectory, fileName)
    if (!target.exists() || target.length() != bytes.size.toLong()) {
        target.writeBytes(bytes)
    }
    return target.absolutePath
}

private fun joinSegments(left: String, right: String): String {
    return listOf(left.trim('/'), right.trim('/'))
        .filter { it.isNotBlank() }
        .joinToString("/")
}

private const val KEY_USE_SAMBA_CACHE = "use_samba_cache"
private const val KEY_PLAYBACK_VOLUME = "playback_volume"
private const val KEY_SHOW_COMPACT_PLAYER_LYRICS = "show_compact_player_lyrics"
private const val KEY_AUTO_PLAY_ON_STARTUP = "auto_play_on_startup"
private const val KEY_LIBRARY_SOURCE_FILTER = "library_source_filter"
private const val KEY_FAVORITES_SOURCE_FILTER = "favorites_source_filter"
private const val KEY_LIBRARY_TRACK_SORT_MODE = "library_track_sort_mode"
private const val KEY_FAVORITES_TRACK_SORT_MODE = "favorites_track_sort_mode"
private const val MAX_RECENT_VLC_LOGS = 8

private fun buildSambaCacheFileName(sourceId: String, remotePath: String): String {
    val sanitized = remotePath.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return "$sourceId-$sanitized"
}

private val jvmRemoteArtworkDirectory = File(File(System.getProperty("user.home")), ".lynmusic/artwork").apply {
    mkdirs()
}

internal const val SAMBA_LOG_TAG = "Samba"
private const val LOCAL_IMPORT_LOG_TAG = "LocalImport"
private const val VLC_LOG_TAG = "VLC"
private const val DESKTOP_VLC_UNAVAILABLE_MESSAGE = "未检测到 VLC，请安装或在设置手动选择 VLC 路径。"

private fun scanFailureReason(throwable: Throwable): String {
    return throwable.message?.takeIf { it.isNotBlank() }
        ?: throwable::class.simpleName
        ?: "读取失败。"
}

private fun buildJvmPlaybackLoadFailureMessage(throwable: Throwable): String {
    val detail = throwable.message?.takeIf { it.isNotBlank() }
        ?: throwable::class.simpleName
        ?: "未知错误"
    return "访问歌曲失败：$detail"
}

private fun buildVlcMetadataLogMessage(
    track: Track?,
    playbackTarget: String,
    sourceReference: String?,
    parseAccepted: Boolean,
    parseStatus: String,
    durationMs: Long,
    metaData: MetaData,
): String {
    return buildString {
        append("metadata track=")
        append(track?.id)
        append(" target=")
        append(playbackTarget)
        sourceReference?.takeIf { it.isNotBlank() }?.let {
            append(" source=")
            append(it)
        }
        append(" parseAccepted=")
        append(parseAccepted)
        append(" parseStatus=")
        append(parseStatus)
        append(" durationMs=")
        append(durationMs)
        append(" title=")
        append(metaData.value(Meta.TITLE))
        append(" artist=")
        append(metaData.value(Meta.ARTIST))
        append(" album=")
        append(metaData.value(Meta.ALBUM))
        append(" albumArtist=")
        append(metaData.value(Meta.ALBUM_ARTIST))
        append(" trackNo=")
        append(metaData.value(Meta.TRACK_NUMBER))
        append(" discNo=")
        append(metaData.value(Meta.DISC_NUMBER))
        append(" artworkUrl=")
        append(metaData.value(Meta.ARTWORK_URL))
        append(" nowPlaying=")
        append(metaData.value(Meta.NOW_PLAYING))
    }
}

internal fun formatJvmVlcParseStatus(parseStatus: MediaParsedStatus?): String {
    return parseStatus?.name ?: "UNKNOWN"
}

private fun MetaData.value(meta: Meta): String {
    return get(meta)?.trim().orEmpty()
}

internal fun sanitizeJvmVlcMetadataTitle(title: String?): String? {
    val normalized = title?.trim().orEmpty()
    if (normalized.isBlank()) return null
    if (INTERNAL_VLC_TITLE_PREFIXES.any { prefix -> normalized.startsWith(prefix, ignoreCase = true) }) {
        return null
    }
    return normalized
}

internal fun resolveJvmVlcMetadataFallback(
    primaryValue: String?,
    vlcValue: String?,
    previousValue: String?,
): String? {
    if (!primaryValue.isNullOrBlank()) return null
    return vlcValue?.trim()?.takeIf { it.isNotBlank() }
        ?: previousValue?.trim()?.takeIf { it.isNotBlank() }
}

private val INTERNAL_VLC_TITLE_PREFIXES = listOf(
    "imem://",
    "fd://",
)
