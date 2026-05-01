package top.iwesley.lyn.music.platform

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.SharedGraph
import top.iwesley.lyn.music.SharedRuntimeServices
import top.iwesley.lyn.music.buildSharedGraph
import top.iwesley.lyn.music.core.model.AndroidDiagnosticLogger
import top.iwesley.lyn.music.core.model.AppDisplayPreferencesStore
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.AudioTagGateway
import top.iwesley.lyn.music.core.model.AudioTagPatch
import top.iwesley.lyn.music.core.model.AudioTagSnapshot
import top.iwesley.lyn.music.core.model.CompactPlayerLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.DEFAULT_SAMBA_PORT
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.GlobalDiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportScanFailure
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.NavidromeAudioQualityPreferencesStore
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.NetworkConnectionType
import top.iwesley.lyn.music.core.model.NetworkConnectionTypeProvider
import top.iwesley.lyn.music.core.model.NonNavidromeAudioScanResult
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.core.model.PlaybackLoadToken
import top.iwesley.lyn.music.core.model.PlaybackPreferencesStore
import top.iwesley.lyn.music.core.model.LyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.RequestMethod
import top.iwesley.lyn.music.core.model.DEFAULT_PLAYBACK_VOLUME
import top.iwesley.lyn.music.core.model.SAME_NAME_LRC_MAX_BYTES
import top.iwesley.lyn.music.core.model.SambaCachePreferencesStore
import top.iwesley.lyn.music.core.model.ThemePreferencesStore
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.appDisplayScalePresetOrDefault
import top.iwesley.lyn.music.core.model.defaultCustomThemeTokens
import top.iwesley.lyn.music.core.model.defaultThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.navidromeAudioQualityOrDefault
import top.iwesley.lyn.music.core.model.normalizePlaybackVolume
import top.iwesley.lyn.music.core.model.resolveNavidromeAudioQualityForCurrentNetwork
import top.iwesley.lyn.music.core.model.withThemePalette
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.SameNameLyricsFileGateway
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.buildSambaLocator
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.formatSambaEndpoint
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.joinSambaPath
import top.iwesley.lyn.music.core.model.normalizeSambaPath
import top.iwesley.lyn.music.core.model.parseNavidromeSongLocator
import top.iwesley.lyn.music.core.model.parseSambaLocator
import top.iwesley.lyn.music.core.model.parseSambaPath
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
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AndroidRuntimeGraph(
    val sharedGraph: SharedGraph,
    val playerRuntimeServices: PlayerRuntimeServices,
)

fun openAndroidRuntimeDatabase(context: Context): LynMusicDatabase {
    return openLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(
            context = context.applicationContext,
            name = context.applicationContext.getDatabasePath("lynmusic.db").absolutePath,
        ),
    ).getOrThrow()
}

fun createAndroidRuntimeGraph(
    activity: ComponentActivity,
    platformName: String = "Android",
): AndroidRuntimeGraph {
    val database = openAndroidRuntimeDatabase(activity.applicationContext)
    return createAndroidRuntimeGraph(
        activity = activity,
        database = database,
        platformName = platformName,
    )
}

fun createAndroidRuntimeGraph(
    activity: ComponentActivity,
    database: LynMusicDatabase,
    platformName: String = "Android",
): AndroidRuntimeGraph {
    val logger = AndroidDiagnosticLogger(enabled = activity.applicationContext.isDebuggableApp(), label = platformName)
    GlobalDiagnosticLogger.installStrategy(logger)
    val secureStore = AndroidCredentialStore(
        context = activity.applicationContext,
        logger = logger,
    ).withSecureInMemoryCache()
    val appPreferencesStore = AndroidAppPreferencesStore(activity.applicationContext)
    val networkConnectionTypeProvider = AndroidNetworkConnectionTypeProvider(activity.applicationContext)
    val lyricsShareFontLibraryPlatformService = AndroidLyricsShareFontLibraryPlatformService(activity)
    val navidromeHttpClient = AndroidLyricsHttpClient()
    val platform = PlatformDescriptor(
        name = platformName,
        capabilities = PlatformCapabilities(
            supportsLocalFolderImport = true,
            supportsSambaImport = true,
            supportsWebDavImport = true,
            supportsNavidromeImport = true,
            supportsSystemMediaControls = true,
            supportsAppDisplayScaleAdjustment = true,
        ),
    )
    val sharedGraph = buildSharedGraph(
        platform = platform,
        database = database,
        runtimeServices = SharedRuntimeServices(
            importSourceGateway = AndroidImportSourceGateway(activity, logger, navidromeHttpClient),
            secureCredentialStore = secureStore,
            sambaCachePreferencesStore = appPreferencesStore,
            themePreferencesStore = appPreferencesStore,
            appDisplayPreferencesStore = appPreferencesStore,
            compactPlayerLyricsPreferencesStore = appPreferencesStore,
            autoPlayOnStartupPreferencesStore = appPreferencesStore,
            navidromeAudioQualityPreferencesStore = appPreferencesStore,
            networkConnectionTypeProvider = networkConnectionTypeProvider,
            librarySourceFilterPreferencesStore = appPreferencesStore,
            lyricsShareFontLibraryPlatformService = lyricsShareFontLibraryPlatformService,
            lyricsShareFontPreferencesStore = appPreferencesStore,
            lyricsHttpClient = navidromeHttpClient,
            artworkCacheStore = createAndroidArtworkCacheStore(activity.applicationContext),
            appStorageGateway = createAndroidAppStorageGateway(activity.applicationContext, database),
            deviceInfoGateway = createAndroidDeviceInfoGateway(activity),
            audioTagGateway = AndroidAudioTagGateway(
                context = activity.applicationContext,
                database = database,
                secureCredentialStore = secureStore,
                logger = logger,
            ),
            sameNameLyricsFileGateway = AndroidSameNameLyricsFileGateway(
                context = activity.applicationContext,
                database = database,
                secureCredentialStore = secureStore,
                logger = logger,
            ),
            audioTagEditorPlatformService = AndroidAudioTagEditorPlatformService(activity),
            logger = logger,
        ),
    )
    return AndroidRuntimeGraph(
        sharedGraph = sharedGraph,
        playerRuntimeServices = PlayerRuntimeServices(
            playbackGateway = AndroidPlaybackGateway(
                context = activity.applicationContext,
                database = database,
                secureCredentialStore = secureStore,
                playbackPreferencesStore = appPreferencesStore,
                navidromeAudioQualityPreferencesStore = appPreferencesStore,
                networkConnectionTypeProvider = networkConnectionTypeProvider,
                logger = logger,
            ),
            playbackPreferencesStore = appPreferencesStore,
            lyricsSharePlatformService = AndroidLyricsSharePlatformService(activity, lyricsShareFontLibraryPlatformService),
            lyricsShareFontLibraryPlatformService = lyricsShareFontLibraryPlatformService,
            lyricsShareFontPreferencesStore = appPreferencesStore,
            systemPlaybackControlsPlatformService = createAndroidSystemPlaybackControlsPlatformService(activity.applicationContext),
        ),
    )
}

internal class AndroidLyricsHttpClient : LyricsHttpClient {
    private val client = HttpClient(OkHttp)

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

internal class AndroidCredentialStore(
    context: Context,
    private val logger: DiagnosticLogger,
) : SecureCredentialStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("lynmusic.credentials", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override suspend fun put(key: String, value: String) {
        preferences.edit().putString(key, encrypt(value)).apply()
    }

    override suspend fun get(key: String): String? {
        val stored = preferences.getString(key, null) ?: return null
        if (!stored.startsWith(ENCRYPTED_VALUE_PREFIX)) {
            runCatching {
                preferences.edit().putString(key, encrypt(stored)).apply()
            }
            return stored
        }
        return runCatching {
            decrypt(stored)
        }.getOrElse { throwable ->
            logger.warn(CREDENTIAL_LOG_TAG) {
                "Failed to decrypt credential for key=$key. Keeping the stored value so transient keystore " +
                    "failures do not erase Navidrome credentials. cause=${throwable::class.simpleName ?: "Unknown"}"
            }
            null
        }
    }

    override suspend fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(value.encodeToByteArray())
        val payload = cipher.iv + encrypted
        return ENCRYPTED_VALUE_PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val payload = Base64.decode(value.removePrefix(ENCRYPTED_VALUE_PREFIX), Base64.DEFAULT)
        require(payload.size > GCM_IV_LENGTH_BYTES) { "Encrypted credential payload is invalid." }
        val iv = payload.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val encrypted = payload.copyOfRange(GCM_IV_LENGTH_BYTES, payload.size)
        val secretKey = getExistingSecretKeyOrNull()
            ?: error("Android credential master key is unavailable for decryption.")
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey,
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        return cipher.doFinal(encrypted).decodeToString()
    }

    private fun getExistingSecretKeyOrNull(): SecretKey? {
        return keyStore.getKey(CREDENTIAL_KEY_ALIAS, null) as? SecretKey
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val existing = getExistingSecretKeyOrNull()
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                CREDENTIAL_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}

internal class AndroidAppPreferencesStore(
    context: Context,
) : PlaybackPreferencesStore, SambaCachePreferencesStore, ThemePreferencesStore, AppDisplayPreferencesStore,
    CompactPlayerLyricsPreferencesStore, NavidromeAudioQualityPreferencesStore, LibrarySourceFilterPreferencesStore,
    LyricsShareFontPreferencesStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("lynmusic.settings", Context.MODE_PRIVATE)
    private val mutableUseSambaCache = MutableStateFlow(
        preferences.getBoolean(KEY_USE_SAMBA_CACHE, false),
    )
    private val mutablePlaybackVolume = MutableStateFlow(readPlaybackVolume())
    private val mutableShowCompactPlayerLyrics = MutableStateFlow(
        preferences.getBoolean(KEY_SHOW_COMPACT_PLAYER_LYRICS, false),
    )
    private val mutableAutoPlayOnStartup = MutableStateFlow(
        preferences.getBoolean(KEY_AUTO_PLAY_ON_STARTUP, false),
    )
    private val mutableAppDisplayScalePreset = MutableStateFlow(
        readAppDisplayScalePreset(),
    )
    private val mutableNavidromeWifiAudioQuality = MutableStateFlow(
        readNavidromeAudioQuality(KEY_NAVIDROME_WIFI_AUDIO_QUALITY, NavidromeAudioQuality.Original),
    )
    private val mutableNavidromeMobileAudioQuality = MutableStateFlow(
        readNavidromeAudioQuality(KEY_NAVIDROME_MOBILE_AUDIO_QUALITY, NavidromeAudioQuality.Kbps192),
    )
    private val mutableLibrarySourceFilter = MutableStateFlow(
        readLibrarySourceFilter(KEY_LIBRARY_SOURCE_FILTER),
    )
    private val mutableFavoritesSourceFilter = MutableStateFlow(
        readLibrarySourceFilter(KEY_FAVORITES_SOURCE_FILTER),
    )
    private val mutableLibraryTrackSortMode = MutableStateFlow(
        readTrackSortMode(KEY_LIBRARY_TRACK_SORT_MODE, TrackSortMode.TITLE),
    )
    private val mutableFavoritesTrackSortMode = MutableStateFlow(
        readTrackSortMode(KEY_FAVORITES_TRACK_SORT_MODE, TrackSortMode.ADDED_AT),
    )
    private val mutableSelectedTheme = MutableStateFlow(readSelectedTheme())
    private val mutableCustomThemeTokens = MutableStateFlow(readCustomThemeTokens())
    private val mutableTextPalettePreferences = MutableStateFlow(readTextPalettePreferences())
    private val mutableSelectedLyricsShareFontKey = MutableStateFlow(
        preferences.getString(KEY_LYRICS_SHARE_FONT_KEY, null)?.trim()?.takeIf { it.isNotBlank() },
    )

    override val useSambaCache: StateFlow<Boolean> = mutableUseSambaCache.asStateFlow()
    override val playbackVolume: StateFlow<Float> = mutablePlaybackVolume.asStateFlow()
    override val showCompactPlayerLyrics: StateFlow<Boolean> = mutableShowCompactPlayerLyrics.asStateFlow()
    override val autoPlayOnStartup: StateFlow<Boolean> = mutableAutoPlayOnStartup.asStateFlow()
    override val appDisplayScalePreset: StateFlow<AppDisplayScalePreset> = mutableAppDisplayScalePreset.asStateFlow()
    override val navidromeWifiAudioQuality: StateFlow<NavidromeAudioQuality> =
        mutableNavidromeWifiAudioQuality.asStateFlow()
    override val navidromeMobileAudioQuality: StateFlow<NavidromeAudioQuality> =
        mutableNavidromeMobileAudioQuality.asStateFlow()
    override val selectedTheme: StateFlow<AppThemeId> = mutableSelectedTheme.asStateFlow()
    override val customThemeTokens: StateFlow<AppThemeTokens> = mutableCustomThemeTokens.asStateFlow()
    override val textPalettePreferences: StateFlow<AppThemeTextPalettePreferences> = mutableTextPalettePreferences.asStateFlow()
    override val selectedLyricsShareFontKey: StateFlow<String?> = mutableSelectedLyricsShareFontKey.asStateFlow()
    override val librarySourceFilter: StateFlow<LibrarySourceFilter> = mutableLibrarySourceFilter.asStateFlow()
    override val favoritesSourceFilter: StateFlow<LibrarySourceFilter> = mutableFavoritesSourceFilter.asStateFlow()
    override val libraryTrackSortMode: StateFlow<TrackSortMode> = mutableLibraryTrackSortMode.asStateFlow()
    override val favoritesTrackSortMode: StateFlow<TrackSortMode> = mutableFavoritesTrackSortMode.asStateFlow()

    override suspend fun setUseSambaCache(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_USE_SAMBA_CACHE, enabled).apply()
        mutableUseSambaCache.value = enabled
    }

    override suspend fun setPlaybackVolume(volume: Float) {
        val normalizedVolume = normalizePlaybackVolume(volume)
        preferences.edit().putFloat(KEY_PLAYBACK_VOLUME, normalizedVolume).apply()
        mutablePlaybackVolume.value = normalizedVolume
    }

    override suspend fun setShowCompactPlayerLyrics(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_COMPACT_PLAYER_LYRICS, enabled).apply()
        mutableShowCompactPlayerLyrics.value = enabled
    }

    override suspend fun setAutoPlayOnStartup(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_PLAY_ON_STARTUP, enabled).apply()
        mutableAutoPlayOnStartup.value = enabled
    }

    override suspend fun setAppDisplayScalePreset(preset: AppDisplayScalePreset) {
        preferences.edit().putString(KEY_APP_DISPLAY_SCALE_PRESET, preset.name).apply()
        mutableAppDisplayScalePreset.value = preset
    }

    override suspend fun setNavidromeWifiAudioQuality(quality: NavidromeAudioQuality) {
        preferences.edit().putString(KEY_NAVIDROME_WIFI_AUDIO_QUALITY, quality.name).apply()
        mutableNavidromeWifiAudioQuality.value = quality
    }

    override suspend fun setNavidromeMobileAudioQuality(quality: NavidromeAudioQuality) {
        preferences.edit().putString(KEY_NAVIDROME_MOBILE_AUDIO_QUALITY, quality.name).apply()
        mutableNavidromeMobileAudioQuality.value = quality
    }

    override suspend fun setSelectedLyricsShareFontKey(value: String?) {
        val normalizedValue = value?.trim()?.takeIf { it.isNotBlank() }
        preferences.edit().putString(KEY_LYRICS_SHARE_FONT_KEY, normalizedValue).apply()
        mutableSelectedLyricsShareFontKey.value = normalizedValue
    }

    override suspend fun setLibrarySourceFilter(filter: LibrarySourceFilter) {
        preferences.edit().putString(KEY_LIBRARY_SOURCE_FILTER, filter.name).apply()
        mutableLibrarySourceFilter.value = filter
    }

    override suspend fun setFavoritesSourceFilter(filter: LibrarySourceFilter) {
        preferences.edit().putString(KEY_FAVORITES_SOURCE_FILTER, filter.name).apply()
        mutableFavoritesSourceFilter.value = filter
    }

    override suspend fun setLibraryTrackSortMode(mode: TrackSortMode) {
        preferences.edit().putString(KEY_LIBRARY_TRACK_SORT_MODE, mode.name).apply()
        mutableLibraryTrackSortMode.value = mode
    }

    override suspend fun setFavoritesTrackSortMode(mode: TrackSortMode) {
        preferences.edit().putString(KEY_FAVORITES_TRACK_SORT_MODE, mode.name).apply()
        mutableFavoritesTrackSortMode.value = mode
    }

    override suspend fun setSelectedTheme(themeId: AppThemeId) {
        preferences.edit().putString(KEY_SELECTED_THEME, themeId.name).apply()
        mutableSelectedTheme.value = themeId
    }

    override suspend fun setCustomThemeTokens(tokens: AppThemeTokens) {
        preferences.edit()
            .putInt(KEY_CUSTOM_THEME_BACKGROUND_ARGB, tokens.backgroundArgb)
            .putInt(KEY_CUSTOM_THEME_ACCENT_ARGB, tokens.accentArgb)
            .putInt(KEY_CUSTOM_THEME_FOCUS_ARGB, tokens.focusArgb)
            .apply()
        mutableCustomThemeTokens.value = tokens
    }

    override suspend fun setTextPalette(themeId: AppThemeId, palette: AppThemeTextPalette) {
        preferences.edit().putString(textPaletteKey(themeId), palette.name).apply()
        mutableTextPalettePreferences.value = mutableTextPalettePreferences.value.withThemePalette(themeId, palette)
    }

    private fun readLibrarySourceFilter(key: String): LibrarySourceFilter {
        val name = preferences.getString(key, null)
        return LibrarySourceFilter.entries.firstOrNull { it.name == name } ?: LibrarySourceFilter.ALL
    }

    private fun readTrackSortMode(key: String, defaultMode: TrackSortMode): TrackSortMode {
        val name = preferences.getString(key, null)
        return TrackSortMode.entries.firstOrNull { it.name == name } ?: defaultMode
    }

    private fun readPlaybackVolume(): Float {
        return normalizePlaybackVolume(preferences.getFloat(KEY_PLAYBACK_VOLUME, DEFAULT_PLAYBACK_VOLUME))
    }

    private fun readSelectedTheme(): AppThemeId {
        val name = preferences.getString(KEY_SELECTED_THEME, null)
        return AppThemeId.entries.firstOrNull { it.name == name } ?: AppThemeId.Ocean
    }

    private fun readAppDisplayScalePreset(): AppDisplayScalePreset {
        return appDisplayScalePresetOrDefault(preferences.getString(KEY_APP_DISPLAY_SCALE_PRESET, null))
    }

    private fun readNavidromeAudioQuality(
        key: String,
        default: NavidromeAudioQuality,
    ): NavidromeAudioQuality {
        return navidromeAudioQualityOrDefault(preferences.getString(key, null), default)
    }

    private fun readCustomThemeTokens(): AppThemeTokens {
        val defaults = defaultCustomThemeTokens()
        return AppThemeTokens(
            backgroundArgb = preferences.getInt(KEY_CUSTOM_THEME_BACKGROUND_ARGB, defaults.backgroundArgb),
            accentArgb = preferences.getInt(KEY_CUSTOM_THEME_ACCENT_ARGB, defaults.accentArgb),
            focusArgb = preferences.getInt(KEY_CUSTOM_THEME_FOCUS_ARGB, defaults.focusArgb),
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
        val name = preferences.getString(key, null)
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
}

internal class AndroidNetworkConnectionTypeProvider(
    context: Context,
) : NetworkConnectionTypeProvider {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override fun currentNetworkConnectionType(): NetworkConnectionType {
        val manager = connectivityManager ?: return NetworkConnectionType.MOBILE
        val network = manager.activeNetwork ?: return NetworkConnectionType.MOBILE
        val capabilities = manager.getNetworkCapabilities(network) ?: return NetworkConnectionType.MOBILE
        return if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            NetworkConnectionType.WIFI
        } else {
            NetworkConnectionType.MOBILE
        }
    }
}

private class AndroidAudioTagGateway(
    private val context: Context,
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val logger: DiagnosticLogger,
) : AudioTagGateway {
    override suspend fun canEdit(track: Track): Boolean {
        val localFile = resolveAndroidLocalTrackFile(track.mediaLocator)
        return when {
            localFile != null -> localFile.isFile && localFile.canRead()
            else -> resolveAndroidLocalTrackUri(track.mediaLocator) != null
        }
    }

    override suspend fun canWrite(track: Track): Boolean {
        if (!hasDirectLocalFileAccess(context)) return false
        val localFile = resolveAndroidLocalTrackFile(track.mediaLocator) ?: return false
        return localFile.isFile && localFile.canWrite()
    }

    override suspend fun read(track: Track): Result<AudioTagSnapshot> {
        return try {
            val localFile = resolveAndroidLocalTrackFile(track.mediaLocator)
            val uri = resolveAndroidLocalTrackUri(track.mediaLocator)
            when {
                localFile != null -> runCatching {
                    AndroidAudioTagFileSupport.readSnapshot(
                        file = localFile,
                        relativePath = track.relativePath,
                        artworkDirectory = File(context.cacheDir, "artwork"),
                    )
                }.recoverCatching {
                    AndroidAudioTagReader.readSnapshot(
                        context = context,
                        uri = Uri.fromFile(localFile),
                        displayName = localFile.name,
                        artworkDirectory = File(context.cacheDir, "artwork"),
                        relativePath = track.relativePath,
                    ).getOrThrow()
                }

                uri != null -> AndroidAudioTagReader.readSnapshot(
                    context = context,
                    uri = uri,
                    displayName = track.relativePath.substringAfterLast('/'),
                    artworkDirectory = File(context.cacheDir, "artwork"),
                    relativePath = track.relativePath,
                )

                parseSambaLocator(track.mediaLocator) != null -> Result.success(
                    readAndroidSambaTrackSnapshot(
                        context = context,
                        database = database,
                        secureCredentialStore = secureCredentialStore,
                        track = track,
                        logger = logger,
                    ),
                )

                else -> Result.failure(IllegalStateException("当前仅支持 Android 本地 URI 或 Samba 远端的音频标签读取。"))
            }
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    override suspend fun write(track: Track, patch: AudioTagPatch): Result<AudioTagSnapshot> {
        return runCatching {
            val permissionLabel = directLocalFileAccessPermissionLabel()
            if (!hasDirectLocalFileAccess(context)) {
                error("当前文件没有写入权限，请重新导入本地文件夹并授予$permissionLabel。")
            }
            val localFile = resolveAndroidLocalTrackFile(track.mediaLocator)
                ?: error("当前歌曲通过 SAF 导入，未获得可写文件访问权限。请在来源页重新扫描并授予$permissionLabel。")
            if (!localFile.isFile || !localFile.canWrite()) {
                error("当前文件没有写入权限，请确认已授予$permissionLabel。")
            }
            val artworkDirectory = File(context.cacheDir, "artwork")
            AndroidAudioTagFileSupport.write(
                file = localFile,
                patch = patch,
                tempDirectory = File(context.cacheDir, "tag-edit"),
            )
            runCatching {
                AndroidAudioTagFileSupport.readSnapshot(
                    file = localFile,
                    relativePath = track.relativePath,
                    artworkDirectory = artworkDirectory,
                )
            }.recoverCatching {
                AndroidAudioTagReader.readSnapshot(
                    context = context,
                    uri = Uri.fromFile(localFile),
                    displayName = localFile.name,
                    artworkDirectory = artworkDirectory,
                    relativePath = track.relativePath,
                ).getOrThrow()
            }.getOrThrow()
        }
    }
}

private class AndroidSameNameLyricsFileGateway(
    private val context: Context,
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val logger: DiagnosticLogger,
) : SameNameLyricsFileGateway {
    override suspend fun readSameNameLyrics(track: Track): Result<String?> {
        return runCatching {
            val localFile = resolveAndroidLocalTrackFile(track.mediaLocator)
            when {
                parseNavidromeSongLocator(track.mediaLocator) != null -> null
                localFile != null -> readAndroidLocalSameNameLyricsFile(localFile)
                parseSambaLocator(track.mediaLocator) != null -> readAndroidSambaSameNameLyrics(
                    database = database,
                    secureCredentialStore = secureCredentialStore,
                    track = track,
                    logger = logger,
                )

                else -> readAndroidSafSameNameLyrics(track) ?: readAndroidWebDavSameNameLyrics(
                    database = database,
                    secureCredentialStore = secureCredentialStore,
                    track = track,
                    logger = logger,
                )
            }
        }
    }

    private suspend fun readAndroidSafSameNameLyrics(track: Track): String? {
        val source = database.importSourceDao().getById(track.sourceId)
            ?.takeIf { it.enabled && it.type == ImportSourceType.LOCAL_FOLDER.name }
            ?: return null
        val root = DocumentFile.fromTreeUri(context, Uri.parse(source.rootReference)) ?: return null
        val lyricsRelativePath = sameNameLyricsRelativePath(track.relativePath) ?: return null
        val document = findDocumentFile(root, lyricsRelativePath.split('/').filter { it.isNotBlank() })
            ?.takeIf { it.isFile && it.length() in 1..SAME_NAME_LRC_MAX_BYTES }
            ?: return null
        val bytes = context.contentResolver.openInputStream(document.uri)?.use(::readSameNameLyricsStream)
            ?: return null
        logger.debug(LOCAL_IMPORT_LOG_TAG) {
            "same-name-lrc-read source=${track.sourceId} relativePath=$lyricsRelativePath bytes=${bytes.size}"
        }
        return decodeAndroidSameNameLyricsBytes(bytes)
    }

    private fun findDocumentFile(root: DocumentFile, segments: List<String>): DocumentFile? {
        var current = root
        segments.forEach { segment ->
            current = current.findFile(segment) ?: return null
        }
        return current
    }
}

internal fun Context.isDebuggableApp(): Boolean {
    return applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
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

private class AndroidImportSourceGateway(
    private val activity: ComponentActivity,
    private val logger: DiagnosticLogger,
    private val navidromeHttpClient: LyricsHttpClient,
) : ImportSourceGateway {
    private var folderContinuation: ((LocalFolderSelection?) -> Unit)? = null
    private var legacyPermissionContinuation: ((Boolean) -> Unit)? = null

    private val picker = activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    IntentFlags.ReadWriteUriPermission,
                )
            }
        }
        resumeFolderSelection(
            uri?.let {
                LocalFolderSelection(
                    label = DocumentFile.fromTreeUri(activity, uri)?.name ?: "本地音乐",
                    persistentReference = it.toString(),
                )
            },
        )
    }

    private val legacyPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = hasDirectLocalFileAccess(activity)
        logger.info(LOCAL_IMPORT_LOG_TAG) {
            "legacy-storage-permission-result granted=$granted ${legacyDirectLocalFileAccessGrantSummary(grants)}"
        }
        val continuation = legacyPermissionContinuation
        legacyPermissionContinuation = null
        continuation?.invoke(granted)
    }

    private val manageAllFilesPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (folderContinuation != null) {
            picker.launch(null)
        }
    }

    override suspend fun pickLocalFolder(): LocalFolderSelection? {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                folderContinuation = { selection ->
                    if (continuation.isActive) {
                        continuation.resume(selection)
                    }
                }
                continuation.invokeOnCancellation {
                    folderContinuation = null
                    legacyPermissionContinuation = null
                }
                if (shouldRequestManageAllFilesAccess()) {
                    showManageAllFilesAccessPrompt()
                } else {
                    launchPickerAfterLegacyPermissionCheck()
                }
            }
        }
    }

    override suspend fun scanLocalFolder(selection: LocalFolderSelection, sourceId: String): ImportScanReport {
        resolveAndroidLocalTrackFile(selection.persistentReference)
            ?.takeIf { hasDirectLocalFileAccess(activity) && it.isDirectory }
            ?.let { root ->
                return scanLocalDirectory(root)
            }
        val treeUri = Uri.parse(selection.persistentReference)
        val resolvedDirectory = resolveTreeUriToDirectory(activity, treeUri)
        logger.info(LOCAL_IMPORT_LOG_TAG) {
            "resolve-tree-uri source=$sourceId treeUri=$treeUri directLocalFileAccess=${hasDirectLocalFileAccess(activity)} " +
                "resolvedDirectory=${resolvedDirectory?.absolutePath ?: "null"}"
        }
        resolvedDirectory
            ?.takeIf { it.isDirectory }
            ?.let { root ->
                return runCatching {
                    scanLocalDirectory(root)
                }.onFailure { throwable ->
                    logger.warn(LOCAL_IMPORT_LOG_TAG) {
                        "direct-scan-fallback root=${root.absolutePath} reason=${throwable.message.orEmpty()}"
                    }
                }.mapCatching { report ->
                    if (report.discoveredAudioFileCount == 0) {
                        logger.warn(LOCAL_IMPORT_LOG_TAG) {
                            "direct-scan-empty-fallback root=${root.absolutePath} treeUri=$treeUri"
                        }
                        scanLocalTree(treeUri)
                    } else {
                        report
                    }
                }.getOrElse {
                    scanLocalTree(treeUri)
                }
        }
        return scanLocalTree(treeUri)
    }

    override suspend fun testSamba(draft: SambaSourceDraft) {
        val sambaPath = parseSambaPath(draft.path)
            ?: error("SMB 路径至少需要包含共享名，例如 Media 或 Media/Music。")
        val endpoint = formatSambaEndpoint(draft.server, draft.port, draft.path)
        val startedAt = System.currentTimeMillis()
        logger.info(SAMBA_LOG_TAG) {
            "test-connect-start endpoint=$endpoint hasCredentials=${draft.username.isNotBlank() || draft.password.isNotBlank()}"
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
                val tracks = mutableListOf<top.iwesley.lyn.music.core.model.ImportedTrackCandidate>()
                val failures = mutableListOf<ImportScanFailure>()
                val discoveredAudioFileCount = collectSambaTracks(
                    share = share,
                    baseDirectory = sambaPath.directoryPath,
                    relativeDirectory = "",
                    sourceId = sourceId,
                    sink = tracks,
                    failures = failures,
                )
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
        testAndroidWebDavConnection(draft, logger)
    }

    override suspend fun scanWebDav(draft: WebDavSourceDraft, sourceId: String): ImportScanReport {
        return scanAndroidWebDav(
            draft = draft,
            sourceId = sourceId,
            artworkDirectory = File(activity.cacheDir, "artwork"),
            logger = logger,
        )
    }

    override suspend fun testNavidrome(draft: NavidromeSourceDraft) {
        testNavidromeConnection(draft, navidromeHttpClient, logger)
    }

    override suspend fun scanNavidrome(draft: NavidromeSourceDraft, sourceId: String): ImportScanReport {
        return scanNavidromeLibrary(
            draft = draft,
            sourceId = sourceId,
            httpClient = navidromeHttpClient,
            supportedImportExtensions = ANDROID_SUPPORTED_IMPORT_AUDIO_EXTENSIONS,
            logger = logger,
        )
    }

    private fun showManageAllFilesAccessPrompt() {
        AlertDialog.Builder(activity)
            .setTitle("需要文件管理权限")
            .setMessage("授予“管理所有文件”后，导入的本地歌曲可以直接编辑音乐标签；如果不授权，会回退到 SAF 只读导入。")
            .setPositiveButton("去授权") { _, _ ->
                manageAllFilesPermissionLauncher.launch(buildManageAllFilesAccessIntent(activity))
            }
            .setNegativeButton("使用 SAF") { _, _ ->
                picker.launch(null)
            }
            .setOnCancelListener {
                resumeFolderSelection(null)
            }
            .show()
    }

    private fun shouldRequestManageAllFilesAccess(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasManageAllFilesAccess(activity)
    }

    private fun launchPickerAfterLegacyPermissionCheck() {
        if (!shouldRequestLegacyDirectLocalFileAccess(activity)) {
            picker.launch(null)
            return
        }
        legacyPermissionContinuation = {
            if (folderContinuation != null) {
                picker.launch(null)
            }
        }
        runCatching {
            legacyPermissionLauncher.launch(legacyDirectLocalFileAccessPermissions())
        }.onFailure { throwable ->
            logger.warn(LOCAL_IMPORT_LOG_TAG) {
                "legacy-storage-permission-launch-failed reason=${throwable.message.orEmpty()}"
            }
            legacyPermissionContinuation = null
            if (folderContinuation != null) {
                picker.launch(null)
            }
        }
    }

    private fun resumeFolderSelection(selection: LocalFolderSelection?) {
        val continuation = folderContinuation
        folderContinuation = null
        legacyPermissionContinuation = null
        continuation?.invoke(selection)
    }

    private fun scanLocalTree(treeUri: Uri): ImportScanReport {
        val root = DocumentFile.fromTreeUri(activity, treeUri) ?: error("Cannot open tree uri: $treeUri")
        val tracks = mutableListOf<top.iwesley.lyn.music.core.model.ImportedTrackCandidate>()
        val failures = mutableListOf<ImportScanFailure>()
        val discoveredAudioFileCount = walkDocumentTree(root, "", tracks, failures)
        return ImportScanReport(
            tracks = tracks,
            discoveredAudioFileCount = discoveredAudioFileCount,
            failures = failures,
        )
    }

    private fun scanLocalDirectory(root: File): ImportScanReport {
        val tracks = mutableListOf<top.iwesley.lyn.music.core.model.ImportedTrackCandidate>()
        val failures = mutableListOf<ImportScanFailure>()
        val discoveredAudioFileCount = walkLocalDirectory(root, "", tracks, failures)
        return ImportScanReport(
            tracks = tracks,
            discoveredAudioFileCount = discoveredAudioFileCount,
            failures = failures,
        )
    }

    private fun walkDocumentTree(
        folder: DocumentFile,
        relativeDirectory: String,
        sink: MutableList<top.iwesley.lyn.music.core.model.ImportedTrackCandidate>,
        failures: MutableList<ImportScanFailure>,
    ): Int {
        var discoveredAudioFileCount = 0
        folder.listFiles()
            .sortedBy { it.name.orEmpty().lowercase() }
            .forEach { file ->
                val fileName = file.name ?: return@forEach
                val nextRelative = listOf(relativeDirectory, fileName).filter { it.isNotBlank() }.joinToString("/")
                when {
                    file.isDirectory -> discoveredAudioFileCount += walkDocumentTree(file, nextRelative, sink, failures)
                    file.isFile -> {
                        when (classifyAndroidScannedAudioFile(fileName)) {
                            NonNavidromeAudioScanResult.NOT_AUDIO -> Unit
                            NonNavidromeAudioScanResult.IMPORT_UNSUPPORTED -> {
                                discoveredAudioFileCount += 1
                                failures += unsupportedAudioImportFailure(nextRelative)
                            }

                            NonNavidromeAudioScanResult.IMPORT_SUPPORTED -> {
                                discoveredAudioFileCount += 1
                                runCatching {
                                    readAndroidCandidate(file, nextRelative)
                                }.onSuccess { candidate ->
                                    sink += candidate
                                }.onFailure { throwable ->
                                    failures += ImportScanFailure(
                                        relativePath = nextRelative,
                                        reason = scanFailureReason(throwable),
                                    )
                                    logger.warn(LOCAL_IMPORT_LOG_TAG) {
                                        "candidate-failed path=$nextRelative reason=${throwable.message.orEmpty()}"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        return discoveredAudioFileCount
    }

    private fun readAndroidCandidate(
        file: DocumentFile,
        relativePath: String,
    ): top.iwesley.lyn.music.core.model.ImportedTrackCandidate {
        return AndroidAudioTagReader.readCandidate(
            context = activity,
            uri = file.uri,
            displayName = file.name,
            relativePath = relativePath,
            artworkDirectory = File(activity.cacheDir, "artwork"),
            logger = logger,
            sizeBytes = file.length(),
            modifiedAt = file.lastModified(),
        )
    }

    private fun walkLocalDirectory(
        folder: File,
        relativeDirectory: String,
        sink: MutableList<top.iwesley.lyn.music.core.model.ImportedTrackCandidate>,
        failures: MutableList<ImportScanFailure>,
    ): Int {
        var discoveredAudioFileCount = 0
        folder.listFiles()
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
            .forEach { file ->
                val nextRelative = listOf(relativeDirectory, file.name).filter { it.isNotBlank() }.joinToString("/")
                when {
                    file.isDirectory -> discoveredAudioFileCount += walkLocalDirectory(file, nextRelative, sink, failures)
                    file.isFile -> {
                        when (classifyAndroidScannedAudioFile(file.name)) {
                            NonNavidromeAudioScanResult.NOT_AUDIO -> Unit
                            NonNavidromeAudioScanResult.IMPORT_UNSUPPORTED -> {
                                discoveredAudioFileCount += 1
                                failures += unsupportedAudioImportFailure(nextRelative)
                            }

                            NonNavidromeAudioScanResult.IMPORT_SUPPORTED -> {
                                discoveredAudioFileCount += 1
                                runCatching {
                                    readAndroidCandidate(file, nextRelative)
                                }.onSuccess { candidate ->
                                    sink += candidate
                                }.onFailure { throwable ->
                                    failures += ImportScanFailure(
                                        relativePath = nextRelative,
                                        reason = scanFailureReason(throwable),
                                    )
                                    logger.warn(LOCAL_IMPORT_LOG_TAG) {
                                        "candidate-failed path=$nextRelative reason=${throwable.message.orEmpty()}"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        return discoveredAudioFileCount
    }

    private fun readAndroidCandidate(
        file: File,
        relativePath: String,
    ): top.iwesley.lyn.music.core.model.ImportedTrackCandidate {
        return AndroidAudioTagReader.readCandidate(
            context = activity,
            uri = Uri.fromFile(file),
            displayName = file.name,
            relativePath = relativePath,
            artworkDirectory = File(activity.cacheDir, "artwork"),
            logger = logger,
            sizeBytes = file.length(),
            modifiedAt = file.lastModified(),
        )
    }

    private fun storeAndroidArtwork(relativePath: String, bytes: ByteArray): String {
        val artworkDirectory = File(activity.cacheDir, "artwork").apply {
            mkdirs()
        }
        val fileName = buildString {
            append(relativePath.hashCode().toUInt().toString(16))
            append(inferArtworkFileExtension(bytes = bytes))
        }
        val target = File(artworkDirectory, fileName)
        if (!target.exists() || target.length() != bytes.size.toLong()) {
            target.writeBytes(bytes)
        }
        return target.absolutePath
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
        share.list(listPath).forEach { info ->
            val name = info.fileName
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
                when (classifyAndroidScannedAudioFile(name)) {
                    NonNavidromeAudioScanResult.NOT_AUDIO -> Unit
                    NonNavidromeAudioScanResult.IMPORT_UNSUPPORTED -> {
                        discoveredAudioFileCount += 1
                        failures += unsupportedAudioImportFailure(childRelative)
                    }

                    NonNavidromeAudioScanResult.IMPORT_SUPPORTED -> {
                        discoveredAudioFileCount += 1
                        val sizeBytes = runCatching { info.endOfFile }.getOrDefault(0L)
                        runCatching {
                            resolveAndroidSambaScanCandidate(
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
                            buildAndroidRemoteFallbackCandidate(
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

    private fun resolveAndroidSambaScanCandidate(
        share: DiskShare,
        sourceId: String,
        relativePath: String,
        remotePath: String,
        sizeBytes: Long,
    ): top.iwesley.lyn.music.core.model.ImportedTrackCandidate {
        val fallback = buildAndroidRemoteFallbackCandidate(
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
            val metadata = readAndroidSambaRemoteMetadata(
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
            val candidate = top.iwesley.lyn.music.core.model.ImportedTrackCandidate(
                title = metadata.title?.trim()?.takeIf { it.isNotBlank() } ?: relativePath.substringAfterLast('/').substringBeforeLast('.'),
                artistName = metadata.artistName?.trim()?.takeIf { it.isNotBlank() },
                albumTitle = metadata.albumTitle?.trim()?.takeIf { it.isNotBlank() },
                durationMs = metadata.durationMs?.coerceAtLeast(0L) ?: 0L,
                trackNumber = metadata.trackNumber,
                discNumber = metadata.discNumber,
                mediaLocator = buildSambaLocator(sourceId, relativePath),
                relativePath = relativePath,
                artworkLocator = metadata.artworkBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
                    storeAndroidArtwork(relativePath, bytes)
                },
                embeddedLyrics = metadata.embeddedLyrics?.trim()?.takeIf { it.isNotBlank() },
                sizeBytes = sizeBytes,
            )
            logger.info(SAMBA_LOG_TAG) {
                "metadata-hit source=$sourceId remotePath=$remotePath title=${candidate.title} artist=${candidate.artistName.orEmpty()} album=${candidate.albumTitle.orEmpty()}"
            }
            return candidate
        }
    }
}

private data class AndroidSambaTagReadTarget(
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

private suspend fun readAndroidSambaTrackSnapshot(
    context: Context,
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    track: Track,
    logger: DiagnosticLogger,
): AudioTagSnapshot {
    val target = resolveAndroidSambaTagReadTarget(
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
                        val metadata = readAndroidSambaRemoteMetadata(
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
                            storeAndroidRemoteArtwork(context, target.relativePath, bytes)
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

private suspend fun readAndroidSambaSameNameLyrics(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    track: Track,
    logger: DiagnosticLogger,
): String? {
    val target = resolveAndroidSambaTagReadTarget(
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
                        decodeAndroidSameNameLyricsBytes(readSambaBytes(smbFile, 0L, sizeBytes.toInt()))
                    }
                }
            }
        }
    } finally {
        runCatching { client.close() }
    }
}

private suspend fun resolveAndroidSambaTagReadTarget(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    track: Track,
): AndroidSambaTagReadTarget? {
    val samba = parseSambaLocator(track.mediaLocator) ?: return null
    val source = database.importSourceDao().getById(samba.first)?.takeIf { it.enabled } ?: return null
    val spec = resolveSambaSourceSpec(
        source = source,
        locatorRelativePath = samba.second,
        fallbackRelativePath = track.relativePath.ifBlank { samba.second },
    )
    val password = spec.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
    return AndroidSambaTagReadTarget(
        sourceId = spec.sourceId,
        endpoint = spec.endpoint,
        server = spec.server,
        port = spec.port,
        shareName = spec.shareName,
        remotePath = spec.remotePath,
        relativePath = spec.relativePath,
        username = spec.username,
        password = password,
    )
}

private fun resolveAndroidLocalTrackUri(locator: String): Uri? {
    val value = locator.trim()
    if (value.isBlank()) return null
    resolveAndroidLocalTrackFile(value)?.let { file ->
        return Uri.fromFile(file)
    }
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
    return when (uri.scheme?.lowercase()) {
        "content", "file" -> uri
        else -> null
    }
}

internal class AndroidPlaybackGateway(
    private val context: Context,
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val playbackPreferencesStore: PlaybackPreferencesStore,
    private val navidromeAudioQualityPreferencesStore: NavidromeAudioQualityPreferencesStore,
    private val networkConnectionTypeProvider: NetworkConnectionTypeProvider,
    private val logger: DiagnosticLogger,
) : PlaybackGateway {
    private val player = ExoPlayer.Builder(context).build()
    private val playerHandler = Handler(player.applicationLooper)
    private val mutableState = MutableStateFlow(PlaybackGatewayState())
    private var released = false
    private var progressTickerRunning = false
    private var currentRemoteLogTag: String? = null
    private var currentRemoteLabel: String? = null

    init {
        logger.info(SAMBA_LOG_TAG) {
            "cache-dir path=${context.cacheDir.absolutePath}"
        }
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            if (released) return
            publishPlayerState()
            if (shouldKeepTickerRunning()) {
                playerHandler.postDelayed(this, 500L)
            } else {
                progressTickerRunning = false
            }
        }
    }

    override val state: StateFlow<PlaybackGatewayState> = mutableState.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publishPlayerState()
                if (isPlaying) {
                    ensureProgressTicker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    mutableState.update {
                        it.copy(
                            isPlaying = false,
                            positionMs = 0L,
                            canSeek = player.isCurrentMediaItemSeekable,
                            completionCount = it.completionCount + 1,
                        )
                    }
                } else {
                    publishPlayerState()
                    if (shouldKeepTickerRunning()) {
                        ensureProgressTicker()
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                currentRemoteLogTag?.let { tag ->
                    logger.error(tag, error) {
                        "play-failed locator=${currentRemoteLabel.orEmpty()}"
                    }
                }
                mutableState.update { it.copy(canSeek = false, errorMessage = error.message ?: "播放器出错") }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                publishPlayerState()
            }

            override fun onEvents(player: Player, events: Player.Events) {
                publishPlayerState()
            }
        })
    }

    override suspend fun load(
        track: Track,
        playWhenReady: Boolean,
        startPositionMs: Long,
        loadToken: PlaybackLoadToken,
    ) {
        logger.debug(PLAYBACK_LOG_TAG) {
            "load-start request=${loadToken.requestId} track=${track.id} locator=${track.mediaLocator} " +
                "playWhenReady=$playWhenReady startPositionMs=$startPositionMs"
        }
        if (!loadToken.isCurrent()) {
            logger.debug(PLAYBACK_LOG_TAG) {
                "load-discarded-stale request=${loadToken.requestId} track=${track.id} before-stop"
            }
            return
        }
        stopAndResetForTrackSwitch(loadToken)
        val webDavTarget = resolveAndroidWebDavPlaybackTarget(
            database = database,
            secureCredentialStore = secureCredentialStore,
            locator = track.mediaLocator,
            logger = logger,
        )
        val sambaTarget = if (
            webDavTarget == null &&
            shouldUseAndroidSambaDirectPlayback(track.mediaLocator, playbackPreferencesStore.useSambaCache.value)
        ) {
            resolveAndroidSambaPlaybackTarget(
                database = database,
                secureCredentialStore = secureCredentialStore,
                track = track,
                logger = logger,
            )
        } else {
            null
        }
        val navidrome = if (webDavTarget == null && sambaTarget == null) {
            parseNavidromeSongLocator(track.mediaLocator)
        } else {
            null
        }
        val navidromeAudioQuality = navidrome?.let {
            resolveNavidromeAudioQualityForCurrentNetwork(
                preferencesStore = navidromeAudioQualityPreferencesStore,
                networkConnectionTypeProvider = networkConnectionTypeProvider,
            )
        }
        val resolvedUri = if (webDavTarget == null && sambaTarget == null) {
            resolveLocator(track.mediaLocator, navidromeAudioQuality)
        } else {
            null
        }
        if (!loadToken.isCurrent()) {
            logger.debug(PLAYBACK_LOG_TAG) {
                "load-discarded-stale request=${loadToken.requestId} track=${track.id} before-prepare"
            }
            return
        }
        onPlayerThread {
            if (!loadToken.isCurrent()) {
                logger.debug(PLAYBACK_LOG_TAG) {
                    "load-discarded-stale request=${loadToken.requestId} track=${track.id} on-player-thread"
                }
                return@onPlayerThread
            }
            if (webDavTarget != null) {
                currentRemoteLogTag = "WebDav"
                currentRemoteLabel = webDavTarget.requestUrl
                player.setMediaSource(webDavTarget.mediaSource)
            } else if (sambaTarget != null) {
                currentRemoteLogTag = SAMBA_LOG_TAG
                currentRemoteLabel = sambaTarget.sourceReference
                player.setMediaSource(sambaTarget.mediaSource)
            } else {
                currentRemoteLogTag = if (navidrome != null) "Navidrome" else null
                currentRemoteLabel = if (navidrome != null) track.mediaLocator else null
                player.setMediaItem(MediaItem.fromUri(checkNotNull(resolvedUri)))
            }
            mutableState.update {
                it.copy(currentNavidromeAudioQuality = navidromeAudioQuality)
            }
            player.prepare()
            player.seekTo(startPositionMs)
            player.playWhenReady = playWhenReady
        }
        logger.debug(PLAYBACK_LOG_TAG) {
            "load-applied request=${loadToken.requestId} track=${track.id}"
        }
        ensureProgressTicker()
    }

    private suspend fun stopAndResetForTrackSwitch(loadToken: PlaybackLoadToken) {
        onPlayerThread {
            if (!loadToken.isCurrent()) {
                logger.debug(PLAYBACK_LOG_TAG) {
                    "load-discarded-stale request=${loadToken.requestId} before-stop-on-player-thread"
                }
                return@onPlayerThread
            }
            runCatching { player.stop() }
            player.clearMediaItems()
            currentRemoteLogTag = null
            currentRemoteLabel = null
            mutableState.update {
                it.resetForTrackSwitch(volumeOverride = player.volume)
            }
            playerHandler.removeCallbacks(progressTicker)
            progressTickerRunning = false
        }
    }

    override suspend fun play() {
        onPlayerThread {
            player.play()
        }
    }

    override suspend fun pause() {
        onPlayerThread {
            player.pause()
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        onPlayerThread {
            if (!player.isCurrentMediaItemSeekable) {
                publishPlayerState()
                return@onPlayerThread
            }
            player.seekTo(positionMs)
        }
    }

    override suspend fun setVolume(volume: Float) {
        val normalized = volume.coerceIn(0f, 1f)
        onPlayerThread {
            player.volume = normalized
            publishPlayerState()
        }
    }

    override suspend fun release() {
        released = true
        playerHandler.removeCallbacks(progressTicker)
        onPlayerThread {
            player.release()
        }
    }

    private suspend fun resolveLocator(
        locator: String,
        navidromeAudioQuality: NavidromeAudioQuality?,
    ): Uri {
        resolveNavidromeStreamUrl(
            database = database,
            secureCredentialStore = secureCredentialStore,
            locator = locator,
            audioQuality = navidromeAudioQuality ?: NavidromeAudioQuality.Original,
        )?.let { return Uri.parse(it) }
        val samba = parseSambaLocator(locator) ?: return Uri.parse(locator)
        if (!playbackPreferencesStore.useSambaCache.value) {
            error("Samba 直连播放失败: Android 预期使用直连 MediaSource，但错误地落入了缓存路径。")
        }
        val source = database.importSourceDao().getById(samba.first)?.takeIf { it.enabled }
            ?: error("SMB 来源不可用。")
        val spec = resolveSambaSourceSpec(
            source = source,
            locatorRelativePath = samba.second,
        )
        val password = spec.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
        val cacheFile = File(context.cacheDir, "${samba.first}-${samba.second.substringAfterLast('/')}").apply {
            parentFile?.mkdirs()
        }
        val remotePath = spec.remotePath
        if (cacheFile.exists()) {
            logger.debug(SAMBA_LOG_TAG) {
                "cache-hit source=${samba.first} endpoint=${spec.endpoint} remotePath=$remotePath cache=${cacheFile.absolutePath}"
            }
            return Uri.fromFile(cacheFile)
        }
        val startedAt = System.currentTimeMillis()
        logger.info(SAMBA_LOG_TAG) {
            "stream-fetch-start source=${samba.first} endpoint=${spec.endpoint} remotePath=$remotePath"
        }
        runCatching {
            val client = SMBClient()
            client.connect(spec.server, spec.port).use { connection ->
                logger.debug(SAMBA_LOG_TAG) {
                    "stream-connect-ok source=${samba.first} endpoint=${spec.endpoint} remoteHost=${connection.remoteHostname}"
                }
                val session = connection.authenticate(
                    AuthenticationContext(spec.username, password.toCharArray(), ""),
                )
                val share = session.connectShare(spec.shareName) as DiskShare
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
                "stream-fetch-complete source=${samba.first} endpoint=${spec.endpoint} remotePath=$remotePath size=${cacheFile.length()} elapsedMs=${System.currentTimeMillis() - startedAt}"
            }
        }.onFailure { throwable ->
            logger.error(SAMBA_LOG_TAG, throwable) {
                "stream-fetch-failed source=${samba.first} endpoint=${spec.endpoint} remotePath=$remotePath elapsedMs=${System.currentTimeMillis() - startedAt}"
            }
            throw throwable
        }
        return Uri.fromFile(cacheFile)
    }

    private suspend fun <T> onPlayerThread(block: () -> T): T {
        if (Looper.myLooper() == player.applicationLooper) {
            return block()
        }
        return suspendCancellableCoroutine { continuation ->
            playerHandler.post {
                runCatching(block).fold(
                    onSuccess = { continuation.resume(it) },
                    onFailure = { continuation.resumeWithException(it) },
                )
            }
        }
    }

    private fun publishPlayerState() {
        mutableState.update {
            val duration = player.duration.takeIf { value -> value > 0 } ?: 0L
            it.copy(
                isPlaying = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING),
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = if (duration > 0) duration else it.durationMs,
                canSeek = player.isCurrentMediaItemSeekable,
                volume = player.volume,
            )
        }
    }

    private fun ensureProgressTicker() {
        if (progressTickerRunning || released) return
        progressTickerRunning = true
        playerHandler.post(progressTicker)
    }

    private fun shouldKeepTickerRunning(): Boolean {
        return player.isPlaying || player.playbackState == Player.STATE_BUFFERING
    }
}

private object IntentFlags {
    const val ReadWriteUriPermission =
        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
}

private const val LOCAL_IMPORT_LOG_TAG = "LocalImport"
private const val PLAYBACK_LOG_TAG = "AndroidPlayback"

private fun joinSegments(left: String, right: String): String {
    return listOf(left.trim('/'), right.trim('/'))
        .filter { it.isNotBlank() }
        .joinToString("/")
}

private fun buildAndroidRemoteFallbackCandidate(
    sourceId: String,
    relativePath: String,
    sizeBytes: Long = 0L,
): top.iwesley.lyn.music.core.model.ImportedTrackCandidate {
    return top.iwesley.lyn.music.core.model.ImportedTrackCandidate(
        title = relativePath.substringAfterLast('/').substringBeforeLast('.'),
        mediaLocator = buildSambaLocator(sourceId, relativePath),
        relativePath = relativePath,
        sizeBytes = sizeBytes,
    )
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

private fun readAndroidSambaRemoteMetadata(
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

private fun storeAndroidRemoteArtwork(
    context: Context,
    relativePath: String,
    bytes: ByteArray,
): String? {
    if (bytes.isEmpty()) return null
    val artworkDirectory = File(context.cacheDir, "artwork").apply {
        mkdirs()
    }
    val fileName = buildString {
        append(relativePath.hashCode().toUInt().toString(16))
        append('-')
        append(bytes.contentHashCode().toUInt().toString(16))
        append(inferArtworkFileExtension(bytes = bytes))
    }
    val target = File(artworkDirectory, fileName)
    if (!target.exists() || target.length() != bytes.size.toLong()) {
        target.writeBytes(bytes)
    }
    return target.absolutePath
}

private const val SAMBA_LOG_TAG = "Samba"
private const val METADATA_LOG_TAG = "Metadata"
private const val CREDENTIAL_LOG_TAG = "CredentialStore"
private const val KEY_USE_SAMBA_CACHE = "use_samba_cache"
private const val KEY_PLAYBACK_VOLUME = "playback_volume"
private const val KEY_SHOW_COMPACT_PLAYER_LYRICS = "show_compact_player_lyrics"
private const val KEY_AUTO_PLAY_ON_STARTUP = "auto_play_on_startup"
private const val KEY_APP_DISPLAY_SCALE_PRESET = "app_display_scale_preset"
private const val KEY_NAVIDROME_WIFI_AUDIO_QUALITY = "navidrome_wifi_audio_quality"
private const val KEY_NAVIDROME_MOBILE_AUDIO_QUALITY = "navidrome_mobile_audio_quality"
private const val KEY_LYRICS_SHARE_FONT_KEY = "lyrics_share_font_key"
private const val KEY_LIBRARY_SOURCE_FILTER = "library_source_filter"
private const val KEY_FAVORITES_SOURCE_FILTER = "favorites_source_filter"
private const val KEY_LIBRARY_TRACK_SORT_MODE = "library_track_sort_mode"
private const val KEY_FAVORITES_TRACK_SORT_MODE = "favorites_track_sort_mode"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val CREDENTIAL_KEY_ALIAS = "lynmusic.credentials.master"
private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
private const val ENCRYPTED_VALUE_PREFIX = "enc:v1:"
private const val GCM_IV_LENGTH_BYTES = 12
private const val GCM_TAG_LENGTH_BITS = 128

private fun scanFailureReason(throwable: Throwable): String {
    return throwable.message?.takeIf { it.isNotBlank() }
        ?: throwable::class.simpleName
        ?: "读取失败。"
}

private fun buildMetadataLogMessage(
    relativePath: String,
    candidate: top.iwesley.lyn.music.core.model.ImportedTrackCandidate,
): String {
    return buildString {
        append("parsed path=")
        append(relativePath)
        append(" title=")
        append(candidate.title)
        append(" artist=")
        append(candidate.artistName.orEmpty())
        append(" album=")
        append(candidate.albumTitle.orEmpty())
        append(" durationMs=")
        append(candidate.durationMs)
        append(" track=")
        append(candidate.trackNumber?.toString().orEmpty())
        append(" disc=")
        append(candidate.discNumber?.toString().orEmpty())
        append(" artwork=")
        append(candidate.artworkLocator != null)
        append(" lyrics=")
        append(candidate.embeddedLyrics.toLyricsPreview())
    }
}

private fun String?.toLyricsPreview(maxLength: Int = 80): String {
    val text = this?.lineSequence()
        ?.map { it.trim() }
        ?.firstOrNull { it.isNotBlank() }
        .orEmpty()
    if (text.isBlank()) return "none"
    return text.take(maxLength)
}
