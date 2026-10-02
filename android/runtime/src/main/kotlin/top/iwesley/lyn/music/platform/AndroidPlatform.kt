package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.redactRemoteSourceUrlForLog
import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.markCurrentRemoteSourceReachable
import top.iwesley.lyn.music.core.model.effectiveConnectTimeoutMillis
import top.iwesley.lyn.music.core.model.audioImportFailure
import androidx.lifecycle.lifecycleScope

import top.iwesley.lyn.music.core.model.APP_LANGUAGE_PREFERENCE_KEY
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.AppLanguagePreferencesStore
import top.iwesley.lyn.music.core.model.AppLanguageRuntime
import top.iwesley.lyn.music.core.model.appLanguageOrDefault
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.uiText

import android.app.AlertDialog
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.room.Room
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import top.iwesley.lyn.music.SharedGraph
import top.iwesley.lyn.music.SharedRuntimeServices
import top.iwesley.lyn.music.buildSharedGraph
import top.iwesley.lyn.music.cast.UnsupportedCastBackgroundRunSettingsOpener
import top.iwesley.lyn.music.cast.UnsupportedCastSessionForegroundPlatformService
import top.iwesley.lyn.music.cast.UnsupportedCastNotificationPermissionRequester
import top.iwesley.lyn.music.cast.upnp.android.AndroidUpnpCastGateway
import top.iwesley.lyn.music.core.model.AndroidDiagnosticLogger
import top.iwesley.lyn.music.core.model.AppDisplayPreferencesStore
import top.iwesley.lyn.music.core.model.AutoOpenPlayerOnStartupPreferencesStore
import top.iwesley.lyn.music.core.model.AppDisplayScalePreset
import top.iwesley.lyn.music.core.model.AudioTagGateway
import top.iwesley.lyn.music.core.model.AudioTagPatch
import top.iwesley.lyn.music.core.model.AudioTagSnapshot
import top.iwesley.lyn.music.core.model.CompactPlayerLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.DEFAULT_ANDROID_EXTENSION_DECODER_ENABLED
import top.iwesley.lyn.music.core.model.DEFAULT_SAMBA_PORT
import top.iwesley.lyn.music.core.model.DesktopLyricsPosition
import top.iwesley.lyn.music.core.model.DesktopLyricsPositionPreferencesStore
import top.iwesley.lyn.music.core.model.DesktopLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.EmbyCredential
import top.iwesley.lyn.music.core.model.EmbySourceDraft
import top.iwesley.lyn.music.core.model.GlobalDiagnosticLogger
import top.iwesley.lyn.music.core.model.IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS
import top.iwesley.lyn.music.core.model.ImportScanFailure
import top.iwesley.lyn.music.core.model.ImportScanPhase
import top.iwesley.lyn.music.core.model.ImportScanProgress
import top.iwesley.lyn.music.core.model.ImportScanProgressSink
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportStreamingScanReport
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.ImportTrackBatchSink
import top.iwesley.lyn.music.core.model.LocalFolderPickerMode
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.NavidromeAudioQualityPreferencesStore
import top.iwesley.lyn.music.core.model.NavidromeLibraryProbe
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.NetworkConnectionState
import top.iwesley.lyn.music.core.model.NetworkConnectionType
import top.iwesley.lyn.music.core.model.NetworkConnectionTypeProvider
import top.iwesley.lyn.music.core.model.NonNavidromeAudioScanResult
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaybackAudioFormat
import top.iwesley.lyn.music.core.model.PlaybackDecoderPreferencesStore
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.core.model.PlaybackLoadToken
import top.iwesley.lyn.music.core.model.PlaybackPreferencesStore
import top.iwesley.lyn.music.core.model.PlayerArtworkStyle
import top.iwesley.lyn.music.core.model.PlayerArtworkStylePreferencesStore
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
import top.iwesley.lyn.music.core.model.desktopLyricsPositionOrNull
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.navidromeAudioQualityOrDefault
import top.iwesley.lyn.music.core.model.normalizePlaybackVolume
import top.iwesley.lyn.music.core.model.playerArtworkStyleOrDefault
import top.iwesley.lyn.music.core.model.resolveNavidromeAudioQualityForCurrentNetwork
import top.iwesley.lyn.music.core.model.stableArtworkBytesHash
import top.iwesley.lyn.music.core.model.withThemePalette
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.SameNameLyricsFileGateway
import top.iwesley.lyn.music.core.model.SubsonicSourceDraft
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.buildSambaLocator
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.formatSambaEndpoint
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.joinSambaPath
import top.iwesley.lyn.music.core.model.normalizeSambaPath
import top.iwesley.lyn.music.core.model.parseEmbySongLocator
import top.iwesley.lyn.music.core.model.parseSubsonicCompatibleSongLocator
import top.iwesley.lyn.music.core.model.parseSambaLocator
import top.iwesley.lyn.music.core.model.parseSambaPath
import top.iwesley.lyn.music.core.model.sameNameLyricsRelativePath
import top.iwesley.lyn.music.core.model.unsupportedAudioImportFailure
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.core.model.withSecureInMemoryCache
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.openLynMusicDatabase
import top.iwesley.lyn.music.data.repository.DailyRecommendationDateChangeNotifier
import top.iwesley.lyn.music.data.repository.DailyRecommendationDateKeyProvider
import top.iwesley.lyn.music.data.repository.PlayerRuntimeServices
import top.iwesley.lyn.music.domain.RemoteSourceResolvedUrl
import top.iwesley.lyn.music.domain.resolveNavidromeStreamUrl
import top.iwesley.lyn.music.domain.resolveEmbyStreamUrl
import top.iwesley.lyn.music.domain.RemoteSourceAddressSelector
import top.iwesley.lyn.music.domain.isRemoteSourceAddressFallbackAllowed
import top.iwesley.lyn.music.domain.remoteCandidateIndexForNetworkChange
import top.iwesley.lyn.music.domain.resolveEmbyStreamUrlCandidates
import top.iwesley.lyn.music.domain.resolveNavidromeStreamUrlCandidates
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AndroidRuntimeGraph(
    val sharedGraph: SharedGraph,
    val playerRuntimeServices: PlayerRuntimeServices,
    private val ownsDatabase: Boolean = false,
) {
    private val failureCleanupStarted = AtomicBoolean(false)

    fun disposeAfterComponentBuildFailure(): Result<Unit> {
        if (!failureCleanupStarted.compareAndSet(false, true)) return Result.success(Unit)
        return runCatching {
            runBlocking {
                val failures = mutableListOf<Throwable>()

                suspend fun closeResource(close: suspend () -> Unit) {
                    runCatching { close() }.onFailure(failures::add)
                }

                closeResource {
                    withTimeout(ANDROID_RUNTIME_FAILURE_SCOPE_SHUTDOWN_TIMEOUT_MS) {
                        sharedGraph.scope.coroutineContext[Job]?.cancelAndJoin()
                    }
                }
                closeResource { playerRuntimeServices.castSessionForegroundPlatformService.close() }
                closeResource { playerRuntimeServices.castGateway.release() }
                closeResource { playerRuntimeServices.castMediaUrlResolver.release() }
                closeResource { sharedGraph.desktopLyricsPlatformService.release() }
                closeResource { playerRuntimeServices.menuBarLyricsControlsPlatformService.close() }
                closeResource { playerRuntimeServices.playbackRepository?.close() }
                closeResource { playerRuntimeServices.closeDesktopResources() }
                if (ownsDatabase) {
                    closeResource { sharedGraph.database.close() }
                }

                failures.firstOrNull()?.let { primary ->
                    failures.drop(1).forEach { failure ->
                        if (failure !== primary) runCatching { primary.addSuppressed(failure) }
                    }
                    throw primary
                }
            }
        }
    }
}

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
    val logger = AndroidDiagnosticLogger(enabled = true, label = platformName)
    val database = openAndroidRuntimeDatabase(activity.applicationContext)
    return createAndroidRuntimeGraphWithOwnedDatabase(database) {
        createAndroidRuntimeGraph(
            context = activity.applicationContext,
            database = database,
            activityActions = FixedAndroidActivityActions(activity, logger),
            platformName = platformName,
            logger = logger,
            ownsDatabase = true,
        )
    }
}

fun createAndroidRuntimeGraph(
    activity: ComponentActivity,
    database: LynMusicDatabase,
    platformName: String = "Android",
): AndroidRuntimeGraph {
    val logger = AndroidDiagnosticLogger(enabled = true, label = platformName)
    return createAndroidRuntimeGraph(
        context = activity.applicationContext,
        database = database,
        activityActions = FixedAndroidActivityActions(activity, logger),
        platformName = platformName,
        logger = logger,
        ownsDatabase = false,
    )
}

fun createAndroidRuntimeGraph(
    context: Context,
    activityActions: AndroidActivityActions,
    platformName: String = "Android",
): AndroidRuntimeGraph {
    val logger = AndroidDiagnosticLogger(enabled = true, label = platformName)
    val database = openAndroidRuntimeDatabase(context)
    return createAndroidRuntimeGraphWithOwnedDatabase(database) {
        createAndroidRuntimeGraph(
            context = context.applicationContext,
            database = database,
            activityActions = activityActions,
            platformName = platformName,
            logger = logger,
            ownsDatabase = true,
        )
    }
}

private fun createAndroidRuntimeGraphWithOwnedDatabase(
    database: LynMusicDatabase,
    factory: () -> AndroidRuntimeGraph,
): AndroidRuntimeGraph = try {
    factory()
} catch (error: Throwable) {
    runCatching { database.close() }
        .exceptionOrNull()
        ?.takeIf { closeError -> closeError !== error }
        ?.let { closeError -> runCatching { error.addSuppressed(closeError) } }
    throw error
}

private fun createAndroidRuntimeGraph(
    context: Context,
    database: LynMusicDatabase,
    activityActions: AndroidActivityActions,
    platformName: String,
    logger: DiagnosticLogger,
    ownsDatabase: Boolean,
): AndroidRuntimeGraph {
    GlobalDiagnosticLogger.installStrategy(logger)
    val secureStore = AndroidCredentialStore(
        context = context,
        logger = logger,
    ).withSecureInMemoryCache()
    val appPreferencesStore = AndroidAppPreferencesStore(context)
    val networkConnectionTypeProvider = AndroidNetworkConnectionTypeProvider.get(context)
    val remoteSourceAddressSelector = AndroidRemoteSourceAddressSelector.get(context)
    val lyricsShareFontLibraryPlatformService = AndroidLyricsShareFontLibraryPlatformService(context, activityActions)
    val navidromeHttpClient = AndroidLyricsHttpClient()
    val artworkCacheStore = createAndroidArtworkCacheStore(context)
    val platform = PlatformDescriptor(
        name = platformName,
        capabilities = PlatformCapabilities(
            supportsLocalFolderImport = true,
            supportsSambaImport = true,
            supportsWebDavImport = true,
            supportsNavidromeImport = true,
            supportsSystemMediaControls = true,
            supportsAppDisplayScaleAdjustment = true,
            supportsAndroidExtensionDecoder = true,
            supportsDesktopLyrics = true,
            supportsEqualizer = platformName.supportsAndroidEqualizer(),
            supportsPlaybackBackgroundArtworkBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            supportsSystemLocalFolderPicker = canResolveOpenDocumentTree(context),
        ),
    )
    val desktopLyricsPlatformService = AndroidDesktopLyricsPlatformService(context)
    val sharedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    return try {
        val dateChangeNotifier = AndroidDailyRecommendationDateChangeNotifier(
            context = context,
            activityResumedEvents = activityActions.activityResumedEvents,
            dateKeyProvider = AndroidDailyRecommendationDateKeyProvider,
            scope = sharedScope,
        )
        val sharedGraph = buildSharedGraph(
            platform = platform,
            database = database,
            runtimeServices = SharedRuntimeServices(
                importSourceGateway = AndroidImportSourceGateway(context, activityActions, logger, navidromeHttpClient),
                secureCredentialStore = secureStore,
                sambaCachePreferencesStore = appPreferencesStore,
                themePreferencesStore = appPreferencesStore,
                appDisplayPreferencesStore = appPreferencesStore,
                compactPlayerLyricsPreferencesStore = appPreferencesStore,
                desktopLyricsPreferencesStore = appPreferencesStore,
                autoPlayOnStartupPreferencesStore = appPreferencesStore,
                autoOpenPlayerOnStartupPreferencesStore = appPreferencesStore,
                navidromeAudioQualityPreferencesStore = appPreferencesStore,
                playbackDecoderPreferencesStore = appPreferencesStore,
                playerArtworkStylePreferencesStore = appPreferencesStore,
                networkConnectionTypeProvider = networkConnectionTypeProvider,
                remoteSourceAddressSelector = remoteSourceAddressSelector,
                librarySourceFilterPreferencesStore = appPreferencesStore,
                lyricsShareFontLibraryPlatformService = lyricsShareFontLibraryPlatformService,
                lyricsShareFontPreferencesStore = appPreferencesStore,
                lyricsHttpClient = navidromeHttpClient,
                artworkCacheStore = artworkCacheStore,
                appStorageGateway = createAndroidAppStorageGateway(context, database),
                offlineDownloadGateway = createAndroidOfflineDownloadGateway(
                    context = context,
                    database = database,
                    secureCredentialStore = secureStore,
                    logger = logger,
                    addressSelector = remoteSourceAddressSelector,
                ),
                deviceInfoGateway = createAndroidDeviceInfoGateway(context),
                audioTagGateway = AndroidAudioTagGateway(
                    context = context,
                    database = database,
                    secureCredentialStore = secureStore,
                    logger = logger,
                ),
                sameNameLyricsFileGateway = AndroidSameNameLyricsFileGateway(
                    context = context,
                    database = database,
                    secureCredentialStore = secureStore,
                    logger = logger,
                ),
                audioTagEditorPlatformService = AndroidAudioTagEditorPlatformService(context, activityActions),
                dailyRecommendationDateKeyProvider = AndroidDailyRecommendationDateKeyProvider,
                dailyRecommendationDateChangeNotifier = dateChangeNotifier,
                desktopLyricsPlatformService = desktopLyricsPlatformService,
                logger = logger,
            ),
            scope = sharedScope,
        )
        AndroidRuntimeGraph(
            sharedGraph = sharedGraph,
            playerRuntimeServices = PlayerRuntimeServices(
                playbackRepository = AndroidServiceBackedPlaybackRepository(context),
                playbackPreferencesStore = appPreferencesStore,
                equalizerPlatformService = if (platformName.supportsAndroidEqualizer()) {
                    AndroidEqualizerPlatformService(
                        context = context,
                        platformName = platformName,
                    )
                } else {
                    top.iwesley.lyn.music.core.model.UnsupportedEqualizerPlatformService
                },
                castGateway = AndroidUpnpCastGateway(
                    context = context,
                    logger = logger,
                ),
                castMediaUrlResolver = AndroidCastMediaUrlResolver(
                    context = context,
                    database = database,
                    secureCredentialStore = secureStore,
                    logger = logger,
                ),
                castBackgroundRunSettingsOpener = if (platformName == "Android") {
                    AndroidCastBackgroundRunSettingsOpener(context)
                } else {
                    UnsupportedCastBackgroundRunSettingsOpener
                },
                castNotificationPermissionRequester = if (platformName == "Android") {
                    AndroidCastNotificationPermissionRequester(context, activityActions)
                } else {
                    UnsupportedCastNotificationPermissionRequester
                },
                castSessionForegroundPlatformService = if (platformName == "Android") {
                    createAndroidCastSessionForegroundPlatformService(
                        context = context,
                        artworkCacheStore = artworkCacheStore,
                    )
                } else {
                    UnsupportedCastSessionForegroundPlatformService
                },
                lyricsSharePlatformService = AndroidLyricsSharePlatformService(
                    context = context,
                    activityActions = activityActions,
                    fontLibraryPlatformService = lyricsShareFontLibraryPlatformService,
                ),
                lyricsShareFontLibraryPlatformService = lyricsShareFontLibraryPlatformService,
                lyricsShareFontPreferencesStore = appPreferencesStore,
            ),
            ownsDatabase = ownsDatabase,
        )
    } catch (error: Throwable) {
        sharedScope.cancel()
        throw error
    }
}

private const val ANDROID_RUNTIME_FAILURE_SCOPE_SHUTDOWN_TIMEOUT_MS = 2_000L

private object AndroidDailyRecommendationDateKeyProvider : DailyRecommendationDateKeyProvider {
    override fun currentDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}

private fun String.supportsAndroidEqualizer(): Boolean {
    return this == ANDROID_EQUALIZER_PHONE_PLATFORM_NAME ||
        this == ANDROID_EQUALIZER_AUTOMOTIVE_PLATFORM_NAME
}

private class AndroidDailyRecommendationDateChangeNotifier(
    private val context: Context,
    activityResumedEvents: Flow<Unit>,
    private val dateKeyProvider: DailyRecommendationDateKeyProvider,
    scope: CoroutineScope,
) : DailyRecommendationDateChangeNotifier {
    private val mutableDateKeys = MutableStateFlow(dateKeyProvider.currentDateKey())
    override val dateKeys: Flow<String> = mutableDateKeys.asStateFlow()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshCurrentDateKey()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val collectorJob = scope.launchActivityResumedDateRefreshCollector(activityResumedEvents) {
            refreshCurrentDateKey()
        }
        collectorJob.invokeOnCompletion {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    override fun refreshCurrentDateKey() {
        mutableDateKeys.value = dateKeyProvider.currentDateKey()
    }
}

internal fun CoroutineScope.launchActivityResumedDateRefreshCollector(
    activityResumedEvents: Flow<Unit>,
    refresh: () -> Unit,
): Job = launch(start = CoroutineStart.UNDISPATCHED) {
    activityResumedEvents.collect {
        refresh()
    }
}

internal class AndroidLyricsHttpClient : LyricsHttpClient {
    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000L
            connectTimeoutMillis = 30_000L
            socketTimeoutMillis = 30_000L
        }
    }

    override suspend fun request(request: LyricsRequest): Result<LyricsHttpResponse> {
        return try {
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
                val connectTimeout = request.effectiveConnectTimeoutMillis()
                if (requestTimeout != null || connectTimeout != null) {
                    timeout {
                        requestTimeout?.let {
                            requestTimeoutMillis = it
                            socketTimeoutMillis = it
                        }
                        connectTimeout?.let { connectTimeoutMillis = it }
                    }
                }
            }
            markCurrentRemoteSourceReachable()
            Result.success(
                LyricsHttpResponse(
                    statusCode = response.status.value,
                    body = response.bodyAsText(),
                    headers = response.headers.entries().associate { entry ->
                        entry.key to entry.value.joinToString(",")
                    },
                ),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (throwable: Throwable) {
            Result.failure(throwable)
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
) : AppLanguagePreferencesStore, PlaybackPreferencesStore, SambaCachePreferencesStore, ThemePreferencesStore, AppDisplayPreferencesStore,
    CompactPlayerLyricsPreferencesStore, DesktopLyricsPreferencesStore, DesktopLyricsPositionPreferencesStore,
    NavidromeAudioQualityPreferencesStore, LibrarySourceFilterPreferencesStore,
    LyricsShareFontPreferencesStore, PlaybackDecoderPreferencesStore, PlayerArtworkStylePreferencesStore,
    AndroidEqualizerPreferencesStore, AutoOpenPlayerOnStartupPreferencesStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("lynmusic.settings", Context.MODE_PRIVATE)
    private val mutableAppLanguage = MutableStateFlow(appLanguageOrDefault(preferences.getString(APP_LANGUAGE_PREFERENCE_KEY, null)))
    override val appLanguage: StateFlow<AppLanguage> = mutableAppLanguage.asStateFlow()
    init { AppLanguageRuntime.install(this, java.util.Locale.getDefault().toLanguageTag()) }
    override suspend fun setAppLanguage(language: AppLanguage) {
        AppLanguageRuntime.updateSystemLanguage(java.util.Locale.getDefault().toLanguageTag())
        preferences.edit().putString(APP_LANGUAGE_PREFERENCE_KEY, language.storageValue).apply()
        mutableAppLanguage.value = language
        AppLanguageRuntime.update(language)
    }

    private val mutableUseSambaCache = MutableStateFlow(
        preferences.getBoolean(KEY_USE_SAMBA_CACHE, false),
    )
    private val mutablePlaybackVolume = MutableStateFlow(readPlaybackVolume())
    private val mutableShowCompactPlayerLyrics = MutableStateFlow(
        preferences.getBoolean(KEY_SHOW_COMPACT_PLAYER_LYRICS, false),
    )
    private val mutableShowDesktopLyrics = MutableStateFlow(
        preferences.getBoolean(KEY_SHOW_DESKTOP_LYRICS, false),
    )
    private val mutableDesktopLyricsPosition = MutableStateFlow(readDesktopLyricsPosition())
    private val mutableAutoPlayOnStartup = MutableStateFlow(
        preferences.getBoolean(KEY_AUTO_PLAY_ON_STARTUP, false),
    )
    private val mutableAutoOpenPlayerOnStartup = MutableStateFlow(
        preferences.getBoolean(KEY_AUTO_OPEN_PLAYER_ON_STARTUP, false),
    )
    private val mutableUseAndroidExtensionDecoder = MutableStateFlow(
        preferences.getBoolean(
            KEY_ANDROID_EXTENSION_DECODER_ENABLED,
            DEFAULT_ANDROID_EXTENSION_DECODER_ENABLED,
        ),
    )
    private val mutablePlayerArtworkStyle = MutableStateFlow(readPlayerArtworkStyle())
    private val mutableEqualizerEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_EQUALIZER_ENABLED, false),
    )
    private val mutableEqualizerPresetName = MutableStateFlow(
        preferences.getString(KEY_EQUALIZER_PRESET_NAME, null)?.takeIf { it.isNotBlank() },
    )
    private val mutableEqualizerBandLevels = MutableStateFlow(
        decodeAndroidEqualizerBandLevels(preferences.getString(KEY_EQUALIZER_BAND_LEVELS, null)),
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
    private val mutableSelectedLyricsShareFontKey = MutableStateFlow(
        preferences.getString(KEY_LYRICS_SHARE_FONT_KEY, null)?.trim()?.takeIf { it.isNotBlank() },
    )
    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KEY_ANDROID_EXTENSION_DECODER_ENABLED -> {
                    mutableUseAndroidExtensionDecoder.value = readUseAndroidExtensionDecoder()
                }

                KEY_PLAYER_ARTWORK_STYLE -> {
                    mutablePlayerArtworkStyle.value = readPlayerArtworkStyle()
                }

                KEY_EQUALIZER_ENABLED -> {
                    mutableEqualizerEnabled.value = preferences.getBoolean(KEY_EQUALIZER_ENABLED, false)
                }

                KEY_EQUALIZER_PRESET_NAME -> {
                    mutableEqualizerPresetName.value =
                        preferences.getString(KEY_EQUALIZER_PRESET_NAME, null)?.takeIf { it.isNotBlank() }
                }

                KEY_EQUALIZER_BAND_LEVELS -> {
                    mutableEqualizerBandLevels.value =
                        decodeAndroidEqualizerBandLevels(preferences.getString(KEY_EQUALIZER_BAND_LEVELS, null))
                }

                KEY_SHOW_DESKTOP_LYRICS -> {
                    mutableShowDesktopLyrics.value = preferences.getBoolean(KEY_SHOW_DESKTOP_LYRICS, false)
                }

                KEY_DESKTOP_LYRICS_POSITION_X,
                KEY_DESKTOP_LYRICS_POSITION_Y,
                KEY_DESKTOP_LYRICS_POSITION_DISPLAY_ID,
                -> {
                    mutableDesktopLyricsPosition.value = readDesktopLyricsPosition()
                }

                KEY_NAVIDROME_WIFI_AUDIO_QUALITY -> {
                    mutableNavidromeWifiAudioQuality.value =
                        readNavidromeAudioQuality(KEY_NAVIDROME_WIFI_AUDIO_QUALITY, NavidromeAudioQuality.Original)
                }

                KEY_NAVIDROME_MOBILE_AUDIO_QUALITY -> {
                    mutableNavidromeMobileAudioQuality.value =
                        readNavidromeAudioQuality(KEY_NAVIDROME_MOBILE_AUDIO_QUALITY, NavidromeAudioQuality.Kbps192)
                }
            }
        }

    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    fun close() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override val useSambaCache: StateFlow<Boolean> = mutableUseSambaCache.asStateFlow()
    override val playbackVolume: StateFlow<Float> = mutablePlaybackVolume.asStateFlow()
    override val showCompactPlayerLyrics: StateFlow<Boolean> = mutableShowCompactPlayerLyrics.asStateFlow()
    override val showDesktopLyrics: StateFlow<Boolean> = mutableShowDesktopLyrics.asStateFlow()
    override val desktopLyricsPosition: StateFlow<DesktopLyricsPosition?> =
        mutableDesktopLyricsPosition.asStateFlow()
    override val autoPlayOnStartup: StateFlow<Boolean> = mutableAutoPlayOnStartup.asStateFlow()
    override val autoOpenPlayerOnStartup: StateFlow<Boolean> =
        mutableAutoOpenPlayerOnStartup.asStateFlow()
    override val useAndroidExtensionDecoder: StateFlow<Boolean> =
        mutableUseAndroidExtensionDecoder.asStateFlow()
    override val playerArtworkStyle: StateFlow<PlayerArtworkStyle> = mutablePlayerArtworkStyle.asStateFlow()
    override val equalizerEnabled: StateFlow<Boolean> = mutableEqualizerEnabled.asStateFlow()
    override val equalizerPresetName: StateFlow<String?> = mutableEqualizerPresetName.asStateFlow()
    override val equalizerBandLevels: StateFlow<Map<Int, Int>> = mutableEqualizerBandLevels.asStateFlow()
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
    override val onlineLibrarySourceId: StateFlow<String?> = mutableOnlineLibrarySourceId.asStateFlow()
    override val onlineFavoritesSourceId: StateFlow<String?> = mutableOnlineFavoritesSourceId.asStateFlow()
    override val onlinePlaylistsSourceId: StateFlow<String?> = mutableOnlinePlaylistsSourceId.asStateFlow()
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

    override suspend fun setShowDesktopLyrics(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_DESKTOP_LYRICS, enabled).apply()
        mutableShowDesktopLyrics.value = enabled
    }

    override suspend fun setDesktopLyricsPosition(position: DesktopLyricsPosition) {
        val normalized = desktopLyricsPositionOrNull(
            centerXFraction = position.centerXFraction,
            centerYFraction = position.centerYFraction,
            displayId = position.displayId,
        ) ?: return
        preferences.edit()
            .putFloat(KEY_DESKTOP_LYRICS_POSITION_X, normalized.centerXFraction)
            .putFloat(KEY_DESKTOP_LYRICS_POSITION_Y, normalized.centerYFraction)
            .apply {
                normalized.displayId?.let { displayId ->
                    putString(KEY_DESKTOP_LYRICS_POSITION_DISPLAY_ID, displayId)
                } ?: remove(KEY_DESKTOP_LYRICS_POSITION_DISPLAY_ID)
            }
            .apply()
        mutableDesktopLyricsPosition.value = normalized
    }

    private fun readDesktopLyricsPosition(): DesktopLyricsPosition? {
        val x = runCatching {
            preferences.takeIf { it.contains(KEY_DESKTOP_LYRICS_POSITION_X) }
                ?.getFloat(KEY_DESKTOP_LYRICS_POSITION_X, Float.NaN)
        }.getOrNull()
        val y = runCatching {
            preferences.takeIf { it.contains(KEY_DESKTOP_LYRICS_POSITION_Y) }
                ?.getFloat(KEY_DESKTOP_LYRICS_POSITION_Y, Float.NaN)
        }.getOrNull()
        val displayId = runCatching {
            preferences.getString(KEY_DESKTOP_LYRICS_POSITION_DISPLAY_ID, null)
        }.getOrNull()
        return desktopLyricsPositionOrNull(x, y, displayId)
    }

    override suspend fun setAutoPlayOnStartup(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_PLAY_ON_STARTUP, enabled).apply()
        mutableAutoPlayOnStartup.value = enabled
    }

    override suspend fun setAutoOpenPlayerOnStartup(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_OPEN_PLAYER_ON_STARTUP, enabled).apply()
        mutableAutoOpenPlayerOnStartup.value = enabled
    }

    override suspend fun setUseAndroidExtensionDecoder(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ANDROID_EXTENSION_DECODER_ENABLED, enabled).apply()
        mutableUseAndroidExtensionDecoder.value = enabled
    }

    override suspend fun setPlayerArtworkStyle(style: PlayerArtworkStyle) {
        preferences.edit().putString(KEY_PLAYER_ARTWORK_STYLE, style.name).apply()
        mutablePlayerArtworkStyle.value = style
    }

    override suspend fun setEqualizerEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_EQUALIZER_ENABLED, enabled).apply()
        mutableEqualizerEnabled.value = enabled
    }

    override suspend fun setEqualizerPresetName(name: String?) {
        val normalizedName = name?.trim()?.takeIf { it.isNotBlank() }
        preferences.edit().putString(KEY_EQUALIZER_PRESET_NAME, normalizedName).apply()
        mutableEqualizerPresetName.value = normalizedName
    }

    override suspend fun setEqualizerBandLevels(levels: Map<Int, Int>) {
        val sanitizedLevels = levels
            .filterKeys { it > 0 }
            .mapValues { (_, level) -> level.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()) }
        preferences.edit()
            .putString(KEY_EQUALIZER_BAND_LEVELS, encodeAndroidEqualizerBandLevels(sanitizedLevels))
            .apply()
        mutableEqualizerBandLevels.value = sanitizedLevels
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

    private fun readNullablePreference(key: String): String? {
        return normalizeNullablePreference(preferences.getString(key, null))
    }

    private fun normalizeNullablePreference(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun setNullablePreference(key: String, value: String?) {
        val normalizedValue = normalizeNullablePreference(value)
        val editor = preferences.edit()
        if (normalizedValue == null) {
            editor.remove(key)
        } else {
            editor.putString(key, normalizedValue)
        }
        editor.apply()
    }

    private fun readTrackSortMode(key: String, defaultMode: TrackSortMode): TrackSortMode {
        val name = preferences.getString(key, null)
        return TrackSortMode.entries.firstOrNull { it.name == name } ?: defaultMode
    }

    private fun readPlaybackVolume(): Float {
        return normalizePlaybackVolume(preferences.getFloat(KEY_PLAYBACK_VOLUME, DEFAULT_PLAYBACK_VOLUME))
    }

    private fun readUseAndroidExtensionDecoder(): Boolean {
        return preferences.getBoolean(
            KEY_ANDROID_EXTENSION_DECODER_ENABLED,
            DEFAULT_ANDROID_EXTENSION_DECODER_ENABLED,
        )
    }

    private fun readPlayerArtworkStyle(): PlayerArtworkStyle {
        return playerArtworkStyleOrDefault(preferences.getString(KEY_PLAYER_ARTWORK_STYLE, null))
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

/** Process-wide selector so the UI, playback service and overlays share one remote address cache. */
internal object AndroidRemoteSourceAddressSelector {
    @Volatile
    private var instance: RemoteSourceAddressSelector? = null

    fun get(context: Context): RemoteSourceAddressSelector {
        return instance ?: synchronized(this) {
            instance ?: RemoteSourceAddressSelector(AndroidNetworkConnectionTypeProvider.get(context))
                .also { instance = it }
        }
    }
}

internal class AndroidNetworkConnectionTypeProvider private constructor(
    context: Context,
) : NetworkConnectionTypeProvider {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val stateLock = Any()
    private var currentNetworkSnapshot = readCurrentNetworkSnapshot()
    private val mutableNetworkConnectionState = MutableStateFlow(
        NetworkConnectionState(
            type = currentNetworkSnapshot.type,
            version = 0L,
            isConnected = currentNetworkSnapshot.isConnected,
        ),
    )

    override val networkConnectionState: StateFlow<NetworkConnectionState> = mutableNetworkConnectionState.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            publishCurrentNetworkConnectionState()
        }

        override fun onLost(network: Network) {
            publishCurrentNetworkConnectionState()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            publishCurrentNetworkConnectionState()
        }
    }

    init {
        registerNetworkCallback()
    }

    companion object {
        @Volatile
        private var instance: AndroidNetworkConnectionTypeProvider? = null

        fun get(context: Context): AndroidNetworkConnectionTypeProvider {
            return instance ?: synchronized(this) {
                instance ?: AndroidNetworkConnectionTypeProvider(context.applicationContext).also { instance = it }
            }
        }
    }

    private fun registerNetworkCallback() {
        val manager = connectivityManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                manager.registerDefaultNetworkCallback(networkCallback)
            } else {
                manager.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback)
            }
        }
    }

    private fun publishCurrentNetworkConnectionState() {
        val nextSnapshot = readCurrentNetworkSnapshot()
        synchronized(stateLock) {
            if (nextSnapshot == currentNetworkSnapshot) return
            currentNetworkSnapshot = nextSnapshot
            val current = mutableNetworkConnectionState.value
            mutableNetworkConnectionState.value = NetworkConnectionState(
                type = nextSnapshot.type,
                version = current.version + 1L,
                isConnected = nextSnapshot.isConnected,
            )
        }
    }

    private fun readCurrentNetworkSnapshot(): AndroidNetworkSnapshot {
        val manager = connectivityManager
            ?: return AndroidNetworkSnapshot(activeNetwork = null, type = NetworkConnectionType.MOBILE, isConnected = false)
        val network = manager.activeNetwork
            ?: return AndroidNetworkSnapshot(activeNetwork = null, type = NetworkConnectionType.MOBILE, isConnected = false)
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return AndroidNetworkSnapshot(activeNetwork = network, type = NetworkConnectionType.MOBILE, isConnected = false)
        val type = if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            NetworkConnectionType.WIFI
        } else {
            NetworkConnectionType.MOBILE
        }
        return AndroidNetworkSnapshot(activeNetwork = network, type = type, isConnected = true)
    }
}

private data class AndroidNetworkSnapshot(
    val activeNetwork: Network?,
    val type: NetworkConnectionType,
    val isConnected: Boolean,
)

internal class AndroidAudioTagGateway(
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
            val permissionText = uiText(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Res.string.tags_android_manage_all_files_permission
                } else {
                    Res.string.tags_android_storage_read_write_permission
                },
            )
            if (!hasDirectLocalFileAccess(context)) {
                throw UiTextException(
                    text = uiText(Res.string.tags_write_permission_reimport_required, permissionText),
                    diagnosticMessage = "当前文件没有写入权限，请重新导入本地文件夹并授予$permissionLabel。",
                )
            }
            val localFile = resolveAndroidLocalTrackFile(track.mediaLocator)
                ?: error("当前歌曲通过 SAF 导入，未获得可写文件访问权限。请在来源页重新扫描并授予$permissionLabel。")
            if (!localFile.isFile || !localFile.canWrite()) {
                throw UiTextException(
                    text = uiText(Res.string.tags_write_permission_required, permissionText),
                    diagnosticMessage = "当前文件没有写入权限，请确认已授予$permissionLabel。",
                )
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

internal class AndroidSameNameLyricsFileGateway(
    private val context: Context,
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val logger: DiagnosticLogger,
) : SameNameLyricsFileGateway {
    override suspend fun readSameNameLyrics(track: Track): Result<String?> {
        return runCatching {
            val localFile = resolveAndroidLocalTrackFile(track.mediaLocator)
            when {
                parseSubsonicCompatibleSongLocator(track.mediaLocator) != null -> null
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

class AndroidLocalFolderPicker(
    private val activity: ComponentActivity,
    private val logger: DiagnosticLogger = GlobalDiagnosticLogger,
) {
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
            if (hasDirectLocalFileAccess(activity)) {
                launchFallbackPicker()
            } else {
                launchSafPickerOrFallback()
            }
        }
    }

    private val fallbackPickerLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        activity.lifecycleScope.launch {
            val selection = if (result.resultCode == Activity.RESULT_OK) {
                AndroidLocalFolderPickerActivity.selectionFromResult(result.data)
            } else {
                null
            }
            resumeFolderSelection(selection)
        }
    }

    suspend fun pickLocalFolder(): LocalFolderSelection? {
        return pickLocalFolder(LocalFolderPickerMode.Automatic)
    }

    suspend fun pickLocalFolder(mode: LocalFolderPickerMode): LocalFolderSelection? {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                logPickerState(stage = "start mode=${mode.name}")
                folderContinuation = { selection ->
                    if (continuation.isActive) {
                        continuation.resume(selection)
                    }
                }
                continuation.invokeOnCancellation {
                    folderContinuation = null
                    legacyPermissionContinuation = null
                }
                when (mode) {
                    LocalFolderPickerMode.Automatic -> {
                        if (shouldRequestManageAllFilesAccess()) {
                            showManageAllFilesAccessPrompt()
                        } else {
                            launchPickerAfterLegacyPermissionCheck()
                        }
                    }

                    LocalFolderPickerMode.System -> {
                        launchSafPickerOrFallback()
                    }

                    LocalFolderPickerMode.BuiltIn -> {
                        launchFallbackPicker()
                    }
                }
            }
        }
    }

    internal fun cancelPendingRequest() {
        resumeFolderSelection(null)
    }

    private fun showManageAllFilesAccessPrompt() {
        val hasReadableUsbRoot = hasReadableAndroidUsbStorageRoot(activity, logger)
        val builder = AlertDialog.Builder(activity)
            .setTitle("需要文件管理权限")
            .setMessage(
                if (hasReadableUsbRoot) {
                    "授予“管理所有文件”后，可以浏览内置存储和 U 盘，并支持本地歌曲标签写回；也可以先仅浏览当前 U 盘。"
                } else {
                    "授予“管理所有文件”后，导入的本地歌曲可以直接编辑音乐标签；如果不授权，会回退到 SAF 只读导入。"
                },
            )
            .setPositiveButton("去授权") { _, _ ->
                logPermissionChoice("grant")
                manageAllFilesPermissionLauncher.launch(buildManageAllFilesAccessIntent(activity))
            }
            .setNeutralButton("取消") { _, _ ->
                logPermissionChoice("cancel")
                resumeFolderSelection(null)
            }
            .setOnCancelListener {
                logPermissionChoice("cancel")
                resumeFolderSelection(null)
            }
        if (hasReadableUsbRoot) {
            builder.setNegativeButton("仅浏览 U 盘") { _, _ ->
                logPermissionChoice("usb-only")
                launchFallbackPicker()
            }
        } else {
            builder.setNegativeButton("使用 SAF") { _, _ ->
                logPermissionChoice("saf")
                launchSafPickerOrFallback()
            }
        }
        builder.show()
    }

    private fun shouldRequestManageAllFilesAccess(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasManageAllFilesAccess(activity)
    }

    private fun launchPickerAfterLegacyPermissionCheck() {
        if (!shouldRequestLegacyDirectLocalFileAccess(activity)) {
            launchSafPickerOrFallback()
            return
        }
        legacyPermissionContinuation = {
            if (folderContinuation != null) {
                launchSafPickerOrFallback()
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
                launchSafPickerOrFallback()
            }
        }
    }

    private fun launchSafPickerOrFallback() {
        val safAvailable = canResolveOpenDocumentTree(activity)
        if (!safAvailable) {
            logger.warn(LOCAL_IMPORT_LOG_TAG) {
                "saf-tree-picker-unavailable fallback=local-folder-picker"
            }
            launchFallbackPicker()
            return
        }
        runCatching {
            picker.launch(null)
        }.onFailure { throwable ->
            logger.warn(LOCAL_IMPORT_LOG_TAG) {
                "saf-tree-picker-launch-failed fallback=local-folder-picker reason=${throwable.message.orEmpty()}"
            }
            launchFallbackPicker()
        }
    }

    private fun logPickerState(stage: String) {
        val safAvailable = canResolveOpenDocumentTree(activity)
        val hasManageAllFilesAccess = hasManageAllFilesAccess(activity)
        val hasDirectLocalFileAccess = hasDirectLocalFileAccess(activity)
        val readableUsbRoots = listAndroidStorageRoots(activity, logger).filter { root ->
            root.isRemovable && root.root.exists() && root.root.isDirectory && root.root.canRead()
        }
        logger.info(LOCAL_IMPORT_LOG_TAG) {
            "local-folder-picker-state stage=$stage safAvailable=$safAvailable " +
                "hasManageAllFilesAccess=$hasManageAllFilesAccess " +
                "hasDirectLocalFileAccess=$hasDirectLocalFileAccess " +
                "readableUsbRootCount=${readableUsbRoots.size} " +
                "readableUsbRoots=${readableUsbRoots.joinToString(separator = ";") { it.root.absolutePath }}"
        }
    }

    private fun logPermissionChoice(choice: String) {
        logger.info(LOCAL_IMPORT_LOG_TAG) {
            "permission-choice=$choice"
        }
    }

    private fun launchFallbackPicker() {
        runCatching {
            fallbackPickerLauncher.launch(AndroidLocalFolderPickerActivity.createIntent(activity))
        }.onFailure { throwable ->
            logger.warn(LOCAL_IMPORT_LOG_TAG) {
                "local-folder-picker-launch-failed reason=${throwable.message.orEmpty()}"
            }
            resumeFolderSelection(null)
        }
    }

    private fun resumeFolderSelection(selection: LocalFolderSelection?) {
        val continuation = folderContinuation
        folderContinuation = null
        legacyPermissionContinuation = null
        continuation?.invoke(selection)
    }
}

private class AndroidImportSourceGateway(
    context: Context,
    private val activityActions: AndroidActivityActions,
    private val logger: DiagnosticLogger,
    private val navidromeHttpClient: LyricsHttpClient,
) : ImportSourceGateway {
    private val context = context.applicationContext

    override suspend fun pickLocalFolder(): LocalFolderSelection? {
        return activityActions.pickLocalFolder(LocalFolderPickerMode.Automatic)
    }

    override suspend fun pickLocalFolder(mode: LocalFolderPickerMode): LocalFolderSelection? {
        return activityActions.pickLocalFolder(mode)
    }

    override suspend fun scanLocalFolder(selection: LocalFolderSelection, sourceId: String): ImportScanReport {
        return scanLocalFolder(selection, sourceId, ImportScanProgressSink.NoOp)
    }

    override suspend fun scanLocalFolder(
        selection: LocalFolderSelection,
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        val directLocalFileAccess = hasDirectLocalFileAccess(context)
        resolveAndroidLocalTrackFile(selection.persistentReference)
            ?.takeIf { it.isDirectory }
            ?.let { root ->
                val canScanDirectly = directLocalFileAccess || isWithinReadableAndroidUsbStorageRoot(context, root, logger)
                if (canScanDirectly) {
                    val branch = if (directLocalFileAccess) "direct-permission" else "usb-readable-root"
                    return scanLocalDirectoryWithLogging(
                        root = root,
                        sourceId = sourceId,
                        branch = branch,
                        progressSink = progressSink,
                    )
                }
                logger.warn(LOCAL_IMPORT_LOG_TAG) {
                    "direct-path-scan-denied source=$sourceId root=${root.absolutePath} " +
                        "directLocalFileAccess=$directLocalFileAccess"
                }
            }
        val treeUri = Uri.parse(selection.persistentReference)
        val resolvedDirectory = resolveTreeUriToDirectory(context, treeUri)
        logger.info(LOCAL_IMPORT_LOG_TAG) {
            "resolve-tree-uri source=$sourceId treeUri=$treeUri directLocalFileAccess=${hasDirectLocalFileAccess(context)} " +
                "resolvedDirectory=${resolvedDirectory?.absolutePath ?: "null"}"
        }
        resolvedDirectory
            ?.takeIf { it.isDirectory }
            ?.let { root ->
                return runCatching {
                    scanLocalDirectoryWithLogging(
                        root = root,
                        sourceId = sourceId,
                        branch = "direct-permission",
                        progressSink = progressSink,
                    )
                }.onFailure { throwable ->
                    logger.warn(LOCAL_IMPORT_LOG_TAG) {
                        "direct-scan-fallback root=${root.absolutePath} reason=${throwable.message.orEmpty()}"
                    }
                }.mapCatching { report ->
                    if (report.discoveredAudioFileCount == 0) {
                        logger.warn(LOCAL_IMPORT_LOG_TAG) {
                            "direct-scan-empty-fallback root=${root.absolutePath} treeUri=$treeUri"
                        }
                        scanLocalTreeWithLogging(
                            treeUri = treeUri,
                            sourceId = sourceId,
                            branch = "saf-tree",
                            progressSink = progressSink,
                        )
                    } else {
                        report
                    }
                }.getOrElse {
                    scanLocalTreeWithLogging(
                        treeUri = treeUri,
                        sourceId = sourceId,
                        branch = "saf-tree",
                        progressSink = progressSink,
                    )
                }
        }
        return scanLocalTreeWithLogging(
            treeUri = treeUri,
            sourceId = sourceId,
            branch = "saf-tree",
            progressSink = progressSink,
        )
    }

    override suspend fun testSamba(draft: SambaSourceDraft) {
        val sambaPath = parseSambaPath(draft.path)
            ?: throw UiTextException(uiText(Res.string.samba_path_missing_share))
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
                    throw UiTextException(uiText(Res.string.samba_path_unavailable))
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
        return scanSamba(draft, sourceId, ImportScanProgressSink.NoOp)
    }

    override suspend fun scanSamba(
        draft: SambaSourceDraft,
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        val sambaPath = parseSambaPath(draft.path)
            ?: throw UiTextException(uiText(Res.string.samba_path_missing_share))
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
                    progressSink = progressSink,
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
        return scanWebDav(draft, sourceId, ImportScanProgressSink.NoOp)
    }

    override suspend fun scanWebDav(
        draft: WebDavSourceDraft,
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        return scanAndroidWebDav(
            draft = draft,
            sourceId = sourceId,
            artworkDirectory = File(context.cacheDir, "artwork"),
            logger = logger,
            progressSink = progressSink,
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
            supportedImportExtensions = ANDROID_SUPPORTED_IMPORT_AUDIO_EXTENSIONS,
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
            supportedImportExtensions = ANDROID_SUPPORTED_IMPORT_AUDIO_EXTENSIONS,
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
            logger = logger,
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
            supportedImportExtensions = ANDROID_SUPPORTED_IMPORT_AUDIO_EXTENSIONS,
            logger = logger,
            progressSink = progressSink,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }

    override suspend fun testEmby(draft: EmbySourceDraft, deviceId: String): EmbyCredential {
        return testEmbyConnection(
            draft = draft,
            deviceId = deviceId,
            httpClient = navidromeHttpClient,
            logger = logger,
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
            logger = logger,
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
            supportedImportExtensions = ANDROID_SUPPORTED_IMPORT_AUDIO_EXTENSIONS,
            logger = logger,
            progressSink = progressSink,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }

    private fun scanLocalTree(
        treeUri: Uri,
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: error("Cannot open tree uri: $treeUri")
        val tracks = mutableListOf<top.iwesley.lyn.music.core.model.ImportedTrackCandidate>()
        val failures = mutableListOf<ImportScanFailure>()
        val discoveredAudioFileCount = walkDocumentTree(root, "", sourceId, tracks, failures, progressSink)
        return ImportScanReport(
            tracks = tracks,
            discoveredAudioFileCount = discoveredAudioFileCount,
            failures = failures,
        )
    }

    private fun scanLocalTreeWithLogging(
        treeUri: Uri,
        sourceId: String,
        branch: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        val startedAt = System.currentTimeMillis()
        logger.info(LOCAL_IMPORT_LOG_TAG) {
            "local-scan-start source=$sourceId branch=$branch treeUri=$treeUri"
        }
        return runCatching {
            scanLocalTree(treeUri, sourceId, progressSink)
        }.onSuccess { report ->
            logger.info(LOCAL_IMPORT_LOG_TAG) {
                "local-scan-complete source=$sourceId branch=$branch treeUri=$treeUri " +
                    "trackCount=${report.tracks.size} discoveredAudioFileCount=${report.discoveredAudioFileCount} " +
                    "failureCount=${report.failures.size} elapsedMs=${System.currentTimeMillis() - startedAt}"
            }
        }.onFailure { throwable ->
            logger.error(LOCAL_IMPORT_LOG_TAG, throwable) {
                "local-scan-failed source=$sourceId branch=$branch treeUri=$treeUri " +
                    "elapsedMs=${System.currentTimeMillis() - startedAt}"
            }
        }.getOrThrow()
    }

    private fun scanLocalDirectory(
        root: File,
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        val tracks = mutableListOf<top.iwesley.lyn.music.core.model.ImportedTrackCandidate>()
        val failures = mutableListOf<ImportScanFailure>()
        val discoveredAudioFileCount = walkLocalDirectory(root, "", sourceId, tracks, failures, progressSink)
        return ImportScanReport(
            tracks = tracks,
            discoveredAudioFileCount = discoveredAudioFileCount,
            failures = failures,
        )
    }

    private fun scanLocalDirectoryWithLogging(
        root: File,
        sourceId: String,
        branch: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        val startedAt = System.currentTimeMillis()
        logger.info(LOCAL_IMPORT_LOG_TAG) {
            "local-scan-start source=$sourceId branch=$branch root=${root.absolutePath}"
        }
        return runCatching {
            scanLocalDirectory(root, sourceId, progressSink)
        }.onSuccess { report ->
            logger.info(LOCAL_IMPORT_LOG_TAG) {
                "local-scan-complete source=$sourceId branch=$branch root=${root.absolutePath} " +
                    "trackCount=${report.tracks.size} discoveredAudioFileCount=${report.discoveredAudioFileCount} " +
                    "failureCount=${report.failures.size} elapsedMs=${System.currentTimeMillis() - startedAt}"
            }
        }.onFailure { throwable ->
            logger.error(LOCAL_IMPORT_LOG_TAG, throwable) {
                "local-scan-failed source=$sourceId branch=$branch root=${root.absolutePath} " +
                    "elapsedMs=${System.currentTimeMillis() - startedAt}"
            }
        }.getOrThrow()
    }

    private fun walkDocumentTree(
        folder: DocumentFile,
        relativeDirectory: String,
        sourceId: String,
        sink: MutableList<top.iwesley.lyn.music.core.model.ImportedTrackCandidate>,
        failures: MutableList<ImportScanFailure>,
        progressSink: ImportScanProgressSink,
    ): Int {
        var discoveredAudioFileCount = 0
        folder.listFiles()
            .sortedBy { it.name.orEmpty().lowercase() }
            .forEach { file ->
                val fileName = file.name ?: return@forEach
                val nextRelative = listOf(relativeDirectory, fileName).filter { it.isNotBlank() }.joinToString("/")
                when {
                    file.isDirectory -> discoveredAudioFileCount += walkDocumentTree(
                        file,
                        nextRelative,
                        sourceId,
                        sink,
                        failures,
                        progressSink,
                    )
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
                                    progressSink.onProgress(
                                        ImportScanProgress(
                                            sourceId = sourceId,
                                            phase = ImportScanPhase.Scanning,
                                            importedTrackCount = sink.size,
                                        ),
                                    )
                                }.onFailure { throwable ->
                                    failures += audioImportFailure(nextRelative, throwable)
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
            context = context,
            uri = file.uri,
            displayName = file.name,
            relativePath = relativePath,
            artworkDirectory = File(context.cacheDir, "artwork"),
            logger = logger,
            sizeBytes = file.length(),
            modifiedAt = file.lastModified(),
        )
    }

    private fun walkLocalDirectory(
        folder: File,
        relativeDirectory: String,
        sourceId: String,
        sink: MutableList<top.iwesley.lyn.music.core.model.ImportedTrackCandidate>,
        failures: MutableList<ImportScanFailure>,
        progressSink: ImportScanProgressSink,
    ): Int {
        var discoveredAudioFileCount = 0
        folder.listFiles()
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
            .forEach { file ->
                val nextRelative = listOf(relativeDirectory, file.name).filter { it.isNotBlank() }.joinToString("/")
                when {
                    file.isDirectory -> discoveredAudioFileCount += walkLocalDirectory(
                        file,
                        nextRelative,
                        sourceId,
                        sink,
                        failures,
                        progressSink,
                    )
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
                                    progressSink.onProgress(
                                        ImportScanProgress(
                                            sourceId = sourceId,
                                            phase = ImportScanPhase.Scanning,
                                            importedTrackCount = sink.size,
                                        ),
                                    )
                                }.onFailure { throwable ->
                                    failures += audioImportFailure(nextRelative, throwable)
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
            context = context,
            uri = Uri.fromFile(file),
            displayName = file.name,
            relativePath = relativePath,
            artworkDirectory = File(context.cacheDir, "artwork"),
            logger = logger,
            sizeBytes = file.length(),
            modifiedAt = file.lastModified(),
        )
    }

    private fun storeAndroidArtwork(relativePath: String, bytes: ByteArray): String {
        val artworkDirectory = File(context.cacheDir, "artwork").apply {
            mkdirs()
        }
        val fileName = buildString {
            append(bytes.stableArtworkBytesHash())
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
        progressSink: ImportScanProgressSink,
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
                    progressSink = progressSink,
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
                            progressSink.onProgress(
                                ImportScanProgress(
                                    sourceId = sourceId,
                                    phase = ImportScanPhase.Scanning,
                                    importedTrackCount = sink.size,
                                ),
                            )
                        }.onFailure { throwable ->
                            failures += audioImportFailure(childRelative, throwable)
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

private data class AndroidRemotePlaybackFallback(
    val candidates: List<RemoteSourceResolvedUrl>,
    val selectedIndex: Int,
) {
    fun currentCandidate(): RemoteSourceResolvedUrl? = candidates.getOrNull(selectedIndex)
}

@UnstableApi
internal class AndroidPlaybackGateway(
    private val context: Context,
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val playbackPreferencesStore: PlaybackPreferencesStore,
    private val equalizerPreferencesStore: AndroidEqualizerPreferencesStore,
    private val playbackDecoderPreferencesStore: PlaybackDecoderPreferencesStore,
    private val navidromeAudioQualityPreferencesStore: NavidromeAudioQualityPreferencesStore,
    private val networkConnectionTypeProvider: NetworkConnectionTypeProvider,
    private val addressSelector: RemoteSourceAddressSelector = RemoteSourceAddressSelector(networkConnectionTypeProvider),
    private val logger: DiagnosticLogger,
) : PlaybackGateway {
    private var activeAndroidExtensionDecoderEnabled =
        playbackDecoderPreferencesStore.useAndroidExtensionDecoder.value
    private var player = createPlayer(activeAndroidExtensionDecoderEnabled)
    private var playerHandler = Handler(player.applicationLooper)
    private val equalizerController = AndroidEqualizerController(
        preferencesStore = equalizerPreferencesStore,
        logger = logger,
    )
    private val mutableState = MutableStateFlow(PlaybackGatewayState())
    private var released = false
    private var progressTickerRunning = false
    private var currentRemoteLogTag: String? = null
    private var currentRemoteLabel: String? = null
    private var currentRemotePlaybackFallback: AndroidRemotePlaybackFallback? = null
    private var pendingLoadPlayWhenReady = false
    private var lastPublishedPlaybackLogKey: String? = null

    internal val currentPlayer: ExoPlayer
        get() = player

    internal var onPlayerRecreated: ((ExoPlayer) -> Unit)? = null

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

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            logger.info(PLAYBACK_LOG_TAG) {
                "player-is-playing-changed isPlaying=$isPlaying ${playerDebugSummary()}"
            }
            if (isPlaying) {
                pendingLoadPlayWhenReady = false
                currentRemotePlaybackFallback?.currentCandidate()?.let { candidate ->
                    if (candidate.sourceId.isNotBlank()) {
                        addressSelector.markSuccess(candidate.sourceId, candidate.kind)
                    }
                }
            }
            publishPlayerState()
            if (isPlaying) {
                ensureProgressTicker()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            logger.info(PLAYBACK_LOG_TAG) {
                "player-playback-state-changed playbackState=$playbackState ${playerDebugSummary()}"
            }
            if (playbackState == Player.STATE_ENDED) {
                pendingLoadPlayWhenReady = false
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

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            logger.warn(PLAYBACK_LOG_TAG) {
                "player-play-when-ready-changed playWhenReady=$playWhenReady reason=$reason ${playerDebugSummary()}"
            }
            publishPlayerState()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            pendingLoadPlayWhenReady = false
            logger.error(PLAYBACK_LOG_TAG, error) {
                "play-failed locator=${currentRemoteLabel.orEmpty()}"
            }
            if (tryApplyRemoteAddressFallback(error)) {
                return
            }
            mutableState.update { it.withAndroidPlaybackFailure(error) }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            publishPlayerState()
        }

        override fun onTracksChanged(tracks: Tracks) {
            mutableState.update {
                it.copy(currentPlaybackAudioFormat = tracks.selectedAudioFormat())
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            publishPlayerState()
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            logger.info(PLAYBACK_LOG_TAG) {
                "analyticsListener audio-decoder-initialized decoder=$decoderName"
            }
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            super.onAudioInputFormatChanged(eventTime, format, decoderReuseEvaluation)
            logger.info(PLAYBACK_LOG_TAG) {
                "analyticsListener onAudioInputFormatChanged format=$format"
            }
        }
    }

    private fun tryApplyRemoteAddressFallback(error: Throwable): Boolean {
        val fallback = currentRemotePlaybackFallback ?: return false
        if (!isRemoteSourceAddressFallbackAllowed(error)) return false
        return switchRemoteCandidate(fallback, fallback.selectedIndex + 1, reason = "retry")
    }

    /** Leaves a LAN stream as soon as the device drops onto mobile data instead of waiting for it to stall. */
    private fun switchRemoteCandidateForNetwork(networkState: NetworkConnectionState) {
        if (released) return
        val fallback = currentRemotePlaybackFallback ?: return
        val targetIndex = remoteCandidateIndexForNetworkChange(
            candidateKinds = fallback.candidates.map { it.kind },
            currentIndex = fallback.selectedIndex,
            networkState = networkState,
            isPlaybackActive = player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE,
        ) ?: return
        switchRemoteCandidate(fallback, targetIndex, reason = "network-change")
    }

    private fun switchRemoteCandidate(
        fallback: AndroidRemotePlaybackFallback,
        targetIndex: Int,
        reason: String,
    ): Boolean {
        val nextCandidate = fallback.candidates.getOrNull(targetIndex) ?: return false
        val retryPositionMs = player.currentPosition.takeIf { it >= 0L } ?: 0L
        val retryPlayWhenReady = player.playWhenReady || pendingLoadPlayWhenReady
        currentRemotePlaybackFallback = fallback.copy(selectedIndex = targetIndex)
        currentRemoteLabel = redactRemoteSourceUrlForLog(nextCandidate.value)
        logger.warn(PLAYBACK_LOG_TAG) {
            "remote-address-fallback $reason index=$targetIndex url=${redactRemoteSourceUrlForLog(nextCandidate.value)}"
        }
        player.setMediaItem(MediaItem.fromUri(Uri.parse(nextCandidate.value)))
        player.prepare()
        player.seekTo(retryPositionMs)
        player.playWhenReady = retryPlayWhenReady
        mutableState.update { it.copy(errorMessage = null, errorText = null) }
        return true
    }

    private val networkChangeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        player.addListener(playerListener)
        networkChangeScope.launch {
            networkConnectionTypeProvider.networkConnectionState
                .distinctUntilChangedBy { it.version }
                .drop(1)
                .collect { state ->
                    playerHandler.post { switchRemoteCandidateForNetwork(state) }
                }
        }

        player.addAnalyticsListener(analyticsListener)
        equalizerController.attachPlayer(player)

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
        applyRendererPreferenceForNextLoad()
        if (!loadToken.isCurrent()) {
            logger.debug(PLAYBACK_LOG_TAG) {
                "load-discarded-stale request=${loadToken.requestId} track=${track.id} after-renderer-preference"
            }
            return
        }
        stopAndResetForTrackSwitch(loadToken, playWhenReady)
        try {
            val offlineTarget = resolveAndroidOfflinePlaybackTarget(database, track)
            val isSubsonicCompatibleTrack = parseSubsonicCompatibleSongLocator(track.mediaLocator) != null
            val webDavTarget = if (offlineTarget == null) resolveAndroidWebDavPlaybackTarget(
                database = database,
                secureCredentialStore = secureCredentialStore,
                locator = track.mediaLocator,
                logger = logger,
            ) else null
            val sambaTarget = if (
                offlineTarget == null &&
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
            if (!loadToken.isCurrent()) {
                logger.debug(PLAYBACK_LOG_TAG) {
                    "load-discarded-stale request=${loadToken.requestId} track=${track.id} before-prepare"
                }
                return
            }
            val subsonicCompatible = if (offlineTarget == null && webDavTarget == null && sambaTarget == null) {
                parseSubsonicCompatibleSongLocator(track.mediaLocator)
            } else {
                null
            }
            val navidromeAudioQuality = when {
                offlineTarget != null && isSubsonicCompatibleTrack -> offlineTarget.quality
                subsonicCompatible != null -> resolveNavidromeAudioQualityForCurrentNetwork(
                    preferencesStore = navidromeAudioQualityPreferencesStore,
                    networkConnectionTypeProvider = networkConnectionTypeProvider,
                )
                else -> null
            }
            val remotePlaybackCandidates = if (offlineTarget == null && webDavTarget == null && sambaTarget == null) {
                resolveLocatorCandidates(track.mediaLocator, navidromeAudioQuality)
            } else {
                null
            }
            val resolvedUri = if (offlineTarget != null) {
                Uri.fromFile(offlineTarget.file)
            } else if (webDavTarget == null && sambaTarget == null) {
                remotePlaybackCandidates?.firstOrNull()?.value?.let(Uri::parse)
                    ?: resolveLocator(track.mediaLocator, navidromeAudioQuality)
            } else {
                null
            }
            if (!loadToken.isCurrent()) {
                logger.debug(PLAYBACK_LOG_TAG) {
                    "load-discarded-stale request=${loadToken.requestId} track=${track.id} before-player-thread"
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
                player.playWhenReady = playWhenReady
                logger.info(PLAYBACK_LOG_TAG) {
                    "load-player-set-play-when-ready request=${loadToken.requestId} track=${track.id} ${playerDebugSummary()}"
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
                    currentRemoteLogTag = when (subsonicCompatible?.sourceType) {
                        ImportSourceType.NAVIDROME -> "Navidrome"
                        ImportSourceType.SUBSONIC -> "Subsonic"
                        else -> null
                    } ?: if (parseEmbySongLocator(track.mediaLocator) != null) {
                        "Emby"
                    } else {
                        null
                    }
                    currentRemoteLabel = if (subsonicCompatible != null || parseEmbySongLocator(track.mediaLocator) != null) {
                        track.mediaLocator
                    } else {
                        null
                    }
                    currentRemotePlaybackFallback = remotePlaybackCandidates?.takeIf { it.isNotEmpty() }?.let { candidates ->
                        AndroidRemotePlaybackFallback(candidates = candidates, selectedIndex = 0)
                    }
                    player.setMediaItem(MediaItem.fromUri(checkNotNull(resolvedUri)))
                }
                mutableState.update {
                    it.copy(currentNavidromeAudioQuality = navidromeAudioQuality)
                }
                player.prepare()
                player.seekTo(startPositionMs)
                logger.info(PLAYBACK_LOG_TAG) {
                    "load-player-prepared request=${loadToken.requestId} track=${track.id} ${playerDebugSummary()}"
                }
            }
            logger.debug(PLAYBACK_LOG_TAG) {
                "load-applied request=${loadToken.requestId} track=${track.id}"
            }
            ensureProgressTicker()
        } catch (throwable: Throwable) {
            clearPendingLoadPlayWhenReady(loadToken)
            throw throwable
        }
    }

    private suspend fun stopAndResetForTrackSwitch(
        loadToken: PlaybackLoadToken,
        playWhenReady: Boolean,
    ) {
        onPlayerThread {
            if (!loadToken.isCurrent()) {
                logger.debug(PLAYBACK_LOG_TAG) {
                    "load-discarded-stale request=${loadToken.requestId} before-stop-on-player-thread"
                }
                return@onPlayerThread
            }
            pendingLoadPlayWhenReady = playWhenReady
            logger.warn(PLAYBACK_LOG_TAG) {
                "load-stop-and-reset request=${loadToken.requestId} playWhenReady=$playWhenReady before ${playerDebugSummary()}"
            }
            runCatching { player.stop() }
            player.clearMediaItems()
            currentRemoteLogTag = null
            currentRemoteLabel = null
            currentRemotePlaybackFallback = null
            mutableState.update {
                it.resetForTrackSwitch(
                    volumeOverride = player.volume,
                    isPlayingOverride = playWhenReady,
                )
            }
            playerHandler.removeCallbacks(progressTicker)
            progressTickerRunning = false
            logger.warn(PLAYBACK_LOG_TAG) {
                "load-stop-and-reset request=${loadToken.requestId} after ${playerDebugSummary()}"
            }
        }
    }

    private suspend fun clearPendingLoadPlayWhenReady(loadToken: PlaybackLoadToken) {
        onPlayerThread {
            if (!loadToken.isCurrent() || !pendingLoadPlayWhenReady) return@onPlayerThread
            pendingLoadPlayWhenReady = false
            publishPlayerState()
        }
    }

    private suspend fun applyRendererPreferenceForNextLoad() {
        val nextAndroidExtensionDecoderEnabled =
            playbackDecoderPreferencesStore.useAndroidExtensionDecoder.value
        if (nextAndroidExtensionDecoderEnabled == activeAndroidExtensionDecoderEnabled) return
        onPlayerThread {
            val previousHandler = playerHandler
            val previousPlayer = player
            val volume = previousPlayer.volume
            previousHandler.removeCallbacks(progressTicker)
            progressTickerRunning = false
            previousPlayer.removeListener(playerListener)
            runCatching { previousPlayer.stop() }
            previousPlayer.release()

            activeAndroidExtensionDecoderEnabled = nextAndroidExtensionDecoderEnabled
            player = createPlayer(nextAndroidExtensionDecoderEnabled).also { nextPlayer ->
                nextPlayer.volume = volume
                nextPlayer.addListener(playerListener)
                nextPlayer.addAnalyticsListener(analyticsListener)
            }
            equalizerController.attachPlayer(player)
            playerHandler = Handler(player.applicationLooper)
            onPlayerRecreated?.invoke(player)
        }
    }

    override suspend fun play() {
        onPlayerThread {
            logger.warn(PLAYBACK_LOG_TAG) {
                "gateway-play-request ${playerDebugSummary()}"
            }
            player.play()
        }
    }

    override suspend fun pause() {
        onPlayerThread {
            logger.warn(PLAYBACK_LOG_TAG) {
                "gateway-pause-request ${playerDebugSummary()}"
            }
            pendingLoadPlayWhenReady = false
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
        networkChangeScope.cancel()
        onPlayerRecreated = null
        equalizerController.release()
        playerHandler.removeCallbacks(progressTicker)
        onPlayerThread {
            pendingLoadPlayWhenReady = false
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
            addressSelector = addressSelector,
        )?.let { return Uri.parse(it) }
        resolveEmbyStreamUrl(
            database = database,
            secureCredentialStore = secureCredentialStore,
            locator = locator,
            addressSelector = addressSelector,
        )?.let { return Uri.parse(it) }
        val samba = parseSambaLocator(locator) ?: return Uri.parse(locator)
        if (!playbackPreferencesStore.useSambaCache.value) {
            throw UiTextException(uiText(Res.string.samba_direct_target_missing))
        }
        val source = database.importSourceDao().getById(samba.first)?.takeIf { it.enabled }
            ?: throw UiTextException(uiText(Res.string.samba_source_unavailable))
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

    private suspend fun resolveLocatorCandidates(
        locator: String,
        navidromeAudioQuality: NavidromeAudioQuality?,
    ): List<RemoteSourceResolvedUrl>? {
        resolveNavidromeStreamUrlCandidates(
            database = database,
            secureCredentialStore = secureCredentialStore,
            locator = locator,
            audioQuality = navidromeAudioQuality ?: NavidromeAudioQuality.Original,
            addressSelector = addressSelector,
        )?.let { return it }
        resolveEmbyStreamUrlCandidates(
            database = database,
            secureCredentialStore = secureCredentialStore,
            locator = locator,
            addressSelector = addressSelector,
        )?.let { return it }
        return null
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
            val nextIsPlaying = pendingLoadPlayWhenReady ||
                player.isPlaying ||
                (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
            val logKey = listOf(
                nextIsPlaying,
                pendingLoadPlayWhenReady,
                player.isPlaying,
                player.playWhenReady,
                player.playbackState,
                player.playbackSuppressionReason,
                it.errorMessage,
            ).joinToString("|")
            if (logKey != lastPublishedPlaybackLogKey) {
                lastPublishedPlaybackLogKey = logKey
                logger.info(PLAYBACK_LOG_TAG) {
                    "gateway-publish-state isPlaying=$nextIsPlaying ${playerDebugSummary()} error=${it.errorMessage.orEmpty()}"
                }
            }
            it.copy(
                isPlaying = nextIsPlaying,
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
        return pendingLoadPlayWhenReady || player.isPlaying || player.playbackState == Player.STATE_BUFFERING
    }

    private fun playerDebugSummary(): String {
        return "pending=$pendingLoadPlayWhenReady playerIsPlaying=${player.isPlaying} " +
            "playerPlayWhenReady=${player.playWhenReady} playbackState=${player.playbackState} " +
            "suppression=${player.playbackSuppressionReason} position=${player.currentPosition}"
    }

    @UnstableApi
    private fun createPlayer(useAndroidExtensionDecoder: Boolean): ExoPlayer {
        return if (useAndroidExtensionDecoder) {
            ExoPlayer.Builder(
                context,
                DefaultRenderersFactory(context).setExtensionRendererMode(
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER,
                ),
            ).build()
            //如果引入LibflacAudioRenderer需要使用下面的方式，因为它会在 extractor 阶段就用 libFLAC 解码，输出的 sampleMimeType 直接是 audio/raw。
            //webdav 和 samba 也要改 ，这里对他们不生效，他们自定义了 media source
//            val mediaSourceFactory = DefaultMediaSourceFactory(
//                context,
//                FfmpegFlacExtractorsFactory(),
//            )
//            ExoPlayer.Builder(
//                context,
//                LynAudioRenderersFactory(
//                    context = context,
//                    preferFfmpeg = true,
//                ),
//                mediaSourceFactory,
//            ).build()
        } else {
            //ExoPlayer.Builder(context).build()
            ExoPlayer.Builder(
                context,
                DefaultRenderersFactory(context).setExtensionRendererMode(
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON,
                ),
            ).build()
        }
    }
}

private fun Tracks.selectedAudioFormat(): PlaybackAudioFormat? {
    groups.forEach { group ->
        if (group.type != C.TRACK_TYPE_AUDIO) return@forEach
        for (index in 0 until group.length) {
            if (!group.isTrackSelected(index)) continue
            return group.getTrackFormat(index).toPlaybackAudioFormat()
        }
    }
    return null
}

@OptIn(UnstableApi::class)
private fun Format.toPlaybackAudioFormat(): PlaybackAudioFormat? {
    val bitRateBps = bitrate.takeIf { it != Format.NO_VALUE && it > 0 }
    val samplingRateHz = sampleRate.takeIf { it != Format.NO_VALUE && it > 0 }
    val channelCount = channelCount.takeIf { it != Format.NO_VALUE && it > 0 }
    if (bitRateBps == null && samplingRateHz == null && channelCount == null) return null
    return PlaybackAudioFormat(
        bitRateBps = bitRateBps,
        samplingRateHz = samplingRateHz,
        channelCount = channelCount,
    )
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
        append(bytes.stableArtworkBytesHash())
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
private const val KEY_SHOW_DESKTOP_LYRICS = "show_desktop_lyrics"
private const val KEY_DESKTOP_LYRICS_POSITION_X = "desktop_lyrics_position_x"
private const val KEY_DESKTOP_LYRICS_POSITION_Y = "desktop_lyrics_position_y"
private const val KEY_DESKTOP_LYRICS_POSITION_DISPLAY_ID = "desktop_lyrics_position_display_id"
private const val KEY_AUTO_PLAY_ON_STARTUP = "auto_play_on_startup"
private const val KEY_AUTO_OPEN_PLAYER_ON_STARTUP = "auto_open_player_on_startup"
private const val KEY_ANDROID_EXTENSION_DECODER_ENABLED = "android_extension_decoder_enabled"
private const val KEY_PLAYER_ARTWORK_STYLE = "player_artwork_style"
private const val KEY_APP_DISPLAY_SCALE_PRESET = "app_display_scale_preset"
private const val KEY_NAVIDROME_WIFI_AUDIO_QUALITY = "navidrome_wifi_audio_quality"
private const val KEY_NAVIDROME_MOBILE_AUDIO_QUALITY = "navidrome_mobile_audio_quality"
private const val KEY_LYRICS_SHARE_FONT_KEY = "lyrics_share_font_key"
private const val KEY_LIBRARY_SOURCE_FILTER = "library_source_filter"
private const val KEY_FAVORITES_SOURCE_FILTER = "favorites_source_filter"
private const val KEY_ONLINE_LIBRARY_SOURCE_ID = "online_library_source_id"
private const val KEY_ONLINE_FAVORITES_SOURCE_ID = "online_favorites_source_id"
private const val KEY_ONLINE_PLAYLISTS_SOURCE_ID = "online_playlists_source_id"
private const val KEY_LIBRARY_TRACK_SORT_MODE = "library_track_sort_mode"
private const val KEY_FAVORITES_TRACK_SORT_MODE = "favorites_track_sort_mode"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val CREDENTIAL_KEY_ALIAS = "lynmusic.credentials.master"
private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
private const val ENCRYPTED_VALUE_PREFIX = "enc:v1:"
private const val GCM_IV_LENGTH_BYTES = 12
private const val GCM_TAG_LENGTH_BITS = 128

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

/** Load UI language before opening the database, including in the crash process. */
fun initializeAndroidAppLanguage(context: Context) {
    AndroidAppPreferencesStore(context.applicationContext)
    initializeNativeUiStrings(context)
}
