package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.redactRemoteSourceUrlForLog
import top.iwesley.lyn.music.core.model.markCurrentRemoteSourceReachable
import top.iwesley.lyn.music.core.model.effectiveConnectTimeoutMillis
import top.iwesley.lyn.music.core.model.diagnosticMessage
import top.iwesley.lyn.music.resources.*
import top.iwesley.lyn.music.core.model.audioImportFailure

import top.iwesley.lyn.music.core.model.APP_LANGUAGE_PREFERENCE_KEY
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.checkUi
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.core.model.resolveUiString
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.playbackLoadFailureText
import top.iwesley.lyn.music.core.model.AppLanguagePreferencesStore
import top.iwesley.lyn.music.core.model.AppLanguageRuntime
import top.iwesley.lyn.music.core.model.appLanguageOrDefault

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
import io.ktor.http.contentType
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.ArrayDeque
import java.util.Properties
import java.util.Timer
import java.util.TimerTask
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import top.iwesley.lyn.music.SharedRuntimeServices
import top.iwesley.lyn.music.buildPlayerAppComponent
import top.iwesley.lyn.music.buildSharedGraph
import top.iwesley.lyn.music.isJvmMacOs
import top.iwesley.lyn.music.logJvmStartupFailure
import top.iwesley.lyn.music.core.model.AudioTagGateway
import top.iwesley.lyn.music.core.model.AudioTagEditorPlatformService
import top.iwesley.lyn.music.core.model.AudioTagPatch
import top.iwesley.lyn.music.core.model.AudioTagSnapshot
import top.iwesley.lyn.music.core.model.ConsoleDiagnosticLogger
import top.iwesley.lyn.music.core.model.CompactPlayerLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.DEFAULT_MINIMIZE_WINDOW_ON_CLOSE
import top.iwesley.lyn.music.core.model.DEFAULT_SAMBA_PORT
import top.iwesley.lyn.music.core.model.DesktopLyricsPosition
import top.iwesley.lyn.music.core.model.DesktopLyricsPositionPreferencesStore
import top.iwesley.lyn.music.core.model.DesktopLyricsPreferencesStore
import top.iwesley.lyn.music.core.model.DesktopVlcPreferencesStore
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.EmbyCredential
import top.iwesley.lyn.music.core.model.EmbySourceDraft
import top.iwesley.lyn.music.core.model.FnMusicSourceDraft
import top.iwesley.lyn.music.domain.scanFnMusicLibrary
import top.iwesley.lyn.music.domain.testFnMusicConnection
import top.iwesley.lyn.music.core.model.IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS
import top.iwesley.lyn.music.core.model.ImportScanFailure
import top.iwesley.lyn.music.core.model.ImportScanPhase
import top.iwesley.lyn.music.core.model.ImportScanProgress
import top.iwesley.lyn.music.core.model.ImportScanProgressSink
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportStreamingScanReport
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.ImportTrackBatchSink
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.oneShotTextContent
import top.iwesley.lyn.music.core.model.MenuBarLyricsControlsPreferencesStore
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.NavidromeLibraryProbe
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.NonNavidromeAudioScanResult
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.core.model.PlaybackLoadToken
import top.iwesley.lyn.music.core.model.PlaybackPreferencesStore
import top.iwesley.lyn.music.core.model.PlayerArtworkStyle
import top.iwesley.lyn.music.core.model.PlayerArtworkStylePreferencesStore
import top.iwesley.lyn.music.core.model.LyricsShareFontPreferencesStore
import top.iwesley.lyn.music.core.model.JvmAppDataDirectory
import top.iwesley.lyn.music.core.model.DEFAULT_PLAYBACK_VOLUME
import top.iwesley.lyn.music.core.model.SAME_NAME_LRC_MAX_BYTES
import top.iwesley.lyn.music.core.model.SambaCachePreferencesStore
import top.iwesley.lyn.music.core.model.ThemePreferencesStore
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.AutoOpenPlayerOnStartupPreferencesStore
import top.iwesley.lyn.music.core.model.defaultCustomThemeTokens
import top.iwesley.lyn.music.core.model.defaultThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.desktopLyricsPositionOrNull
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.isCompleteArtworkPayload
import top.iwesley.lyn.music.core.model.normalizePlaybackVolume
import top.iwesley.lyn.music.core.model.playerArtworkStyleOrDefault
import top.iwesley.lyn.music.core.model.RemotePlaybackUrlCandidate
import top.iwesley.lyn.music.core.model.stableArtworkBytesHash
import top.iwesley.lyn.music.core.model.withThemePalette
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.SameNameLyricsFileGateway
import top.iwesley.lyn.music.core.model.SubsonicSourceDraft
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.VlcPathPickerPlatformService
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.WifiNetworkConnectionTypeProvider
import top.iwesley.lyn.music.core.model.WindowClosePreferencesStore
import top.iwesley.lyn.music.core.model.buildSambaLocator
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.formatSambaEndpoint
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.joinSambaPath
import top.iwesley.lyn.music.core.model.RemoteDirectoryEntry
import top.iwesley.lyn.music.core.model.planSambaScanTargets
import top.iwesley.lyn.music.core.model.scanSelectedFolders
import top.iwesley.lyn.music.core.model.resolveSambaRemoteFile
import top.iwesley.lyn.music.core.model.normalizeArtworkLocator
import top.iwesley.lyn.music.core.model.normalizeSambaPath
import top.iwesley.lyn.music.core.model.parseSambaLocator
import top.iwesley.lyn.music.core.model.parseSambaPath
import top.iwesley.lyn.music.core.model.parseEmbyCoverLocator
import top.iwesley.lyn.music.core.model.parseFnMusicCoverLocator
import top.iwesley.lyn.music.core.model.readRemoteSourceCandidateBytes
import top.iwesley.lyn.music.core.model.parseEmbySongLocator
import top.iwesley.lyn.music.core.model.parseSubsonicCompatibleCoverLocator
import top.iwesley.lyn.music.core.model.parseSubsonicCompatibleSongLocator
import top.iwesley.lyn.music.core.model.parseWebDavLocator
import top.iwesley.lyn.music.core.model.sameNameLyricsRelativePath
import top.iwesley.lyn.music.core.model.unsupportedAudioImportFailure
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.core.model.withSecureInMemoryCache
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.openLynMusicDatabase
import top.iwesley.lyn.music.data.repository.DailyRecommendationDateChangeNotifier
import top.iwesley.lyn.music.data.repository.DailyRecommendationDateKeyProvider
import top.iwesley.lyn.music.data.repository.PlayerRuntimeServices
import top.iwesley.lyn.music.domain.resolveNavidromeStreamUrl
import top.iwesley.lyn.music.domain.resolveNavidromeStreamUrlCandidates
import top.iwesley.lyn.music.domain.resolveEmbyStreamUrl
import top.iwesley.lyn.music.domain.resolveEmbyStreamUrlCandidates
import top.iwesley.lyn.music.domain.RemoteSourceAddressSelector
import top.iwesley.lyn.music.domain.RemoteSourceResolvedUrl
import top.iwesley.lyn.music.domain.isRemoteSourceAddressFallbackAllowed
import top.iwesley.lyn.music.domain.readRemotePlaybackUrlCandidateWithFallback
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

/** Load only the UI language before database initialization and early startup messages. */
internal fun initializeJvmAppLanguage(
    dataLocationManager: JvmDataLocationManager = JvmDataLocationManager(),
) {
    AppLanguageRuntime.updateSystemLanguage(java.util.Locale.getDefault().toLanguageTag())
    val language = try {
        val settingsFile = File(dataLocationManager.currentRootDirectory(), "settings.properties")
        val properties = JvmSettingsPropertiesFile(settingsFile).load()
        appLanguageOrDefault(properties.getProperty(APP_LANGUAGE_PREFERENCE_KEY)?.trim())
    } catch (error: Throwable) {
        logJvmStartupFailure(stage = "app-language", error = error)
        AppLanguage.System
    }
    AppLanguageRuntime.update(language)
}

fun createJvmAppComponent(
    dataLocationManager: JvmDataLocationManager = JvmDataLocationManager(),
): top.iwesley.lyn.music.LynMusicAppComponent {
    val osName = System.getProperty("os.name").orEmpty()
    JvmAppDataDirectory.initialize(dataLocationManager.currentRootDirectory())
    val resourceGuard = JvmDesktopResourceGuard()
    val database = openLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(
            name = JvmAppDataDirectory.resolve("lynmusic.db").apply {
                parentFile?.mkdirs()
            }.absolutePath,
        ),
    ).getOrThrow()
    resourceGuard.register { database.close() }
    return try {
    val logger = ConsoleDiagnosticLogger(enabled = true, label = "Desktop")
    logger.info("Desktop") {
        "你好 process pid=${ProcessHandle.current().pid()}"
    }
    val secureStore = createJvmSecureCredentialStore(logger).withSecureInMemoryCache()
    val appPreferencesStore = JvmAppPreferencesStore()
    val lyricsShareFontLibraryPlatformService = JvmLyricsShareFontLibraryPlatformService()
    val artworkCacheStore = createJvmArtworkCacheStore()
    val systemPlaybackControls = createJvmSystemPlaybackControlsPlatformService(
        logger = logger,
        artworkCacheStore = artworkCacheStore,
    )
    val systemPlaybackControlsToken = resourceGuard.register { systemPlaybackControls.service.close() }
    val menuBarLyricsControls = createJvmMenuBarLyricsControlsPlatformService(
        logger = logger,
    )
    val menuBarLyricsControlsToken = resourceGuard.register { menuBarLyricsControls.service.close() }
    val remoteSourceAddressSelector = RemoteSourceAddressSelector(WifiNetworkConnectionTypeProvider)
    val navidromeHttpClient = JvmLyricsHttpClient()
    resourceGuard.register { navidromeHttpClient.close() }
    val playbackGateway = JvmPlaybackGateway(
        database = database,
        secureCredentialStore = secureStore,
        playbackPreferencesStore = appPreferencesStore,
        desktopVlcPreferencesStore = appPreferencesStore,
        logger = logger,
        addressSelector = remoteSourceAddressSelector,
        fnMusicHttpClient = navidromeHttpClient,
    )
    val playbackGatewayToken = resourceGuard.register { playbackGateway.release() }
    val platform = PlatformDescriptor(
        name = "Desktop",
        capabilities = PlatformCapabilities(
            supportsLocalFolderImport = true,
            supportsSambaImport = true,
            supportsWebDavImport = true,
            supportsNavidromeImport = true,
            supportsFnMusicImport = true,
            supportsSystemMediaControls = systemPlaybackControls.isSupported,
            supportsDesktopLyrics = true,
            supportsMenuBarLyricsControls = menuBarLyricsControls.isSupported,
            supportsMacOsWindowCloseBehavior = isJvmMacOs(osName),
            supportsCustomDataLocation = isJvmWindowsOs(osName),
            supportsPlaybackProgressFlowerParticles = false,
        ),
    )
    val sharedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val sharedScopeToken = resourceGuard.register { stopJvmAppScope(sharedScope) }
    val desktopLyricsPlatformService = JvmDesktopLyricsPlatformService(
        positionPreferencesStore = appPreferencesStore,
        persistenceScope = sharedScope,
        logger = logger,
    )
    val desktopLyricsToken = resourceGuard.register { desktopLyricsPlatformService.release() }
    val sharedGraph = buildSharedGraph(
        platform = platform,
        database = database,
        runtimeServices = SharedRuntimeServices(
            importSourceGateway = JvmImportSourceGateway(logger, navidromeHttpClient),
            secureCredentialStore = secureStore,
            sambaCachePreferencesStore = appPreferencesStore,
            themePreferencesStore = appPreferencesStore,
            compactPlayerLyricsPreferencesStore = appPreferencesStore,
            desktopLyricsPreferencesStore = appPreferencesStore,
            menuBarLyricsControlsPreferencesStore = appPreferencesStore,
            autoPlayOnStartupPreferencesStore = appPreferencesStore,
            autoOpenPlayerOnStartupPreferencesStore = appPreferencesStore,
            windowClosePreferencesStore = appPreferencesStore,
            playerArtworkStylePreferencesStore = appPreferencesStore,
            desktopVlcPreferencesStore = appPreferencesStore,
            networkConnectionTypeProvider = WifiNetworkConnectionTypeProvider,
            remoteSourceAddressSelector = remoteSourceAddressSelector,
            librarySourceFilterPreferencesStore = appPreferencesStore,
            lyricsShareFontLibraryPlatformService = lyricsShareFontLibraryPlatformService,
            lyricsShareFontPreferencesStore = appPreferencesStore,
            lyricsHttpClient = navidromeHttpClient,
            artworkCacheStore = artworkCacheStore,
            appStorageGateway = createJvmAppStorageGateway(database = database),
            appDataLocationPlatformService = JvmAppDataLocationPlatformService(dataLocationManager),
            offlineDownloadGateway = createJvmOfflineDownloadGateway(
                database = database,
                secureCredentialStore = secureStore,
                logger = logger,
                addressSelector = remoteSourceAddressSelector,
                fnMusicHttpClient = navidromeHttpClient,
            ),
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
            dailyRecommendationDateKeyProvider = JvmDailyRecommendationDateKeyProvider,
            dailyRecommendationDateChangeNotifier = JvmDailyRecommendationDateChangeNotifier(
                JvmDailyRecommendationDateKeyProvider,
            ),
            desktopLyricsPlatformService = desktopLyricsPlatformService,
            logger = logger,
        ),
        scope = sharedScope,
    )
    check(sharedGraph.scope === sharedScope) { "SharedGraph 未保留 JVM 运行期 scope。" }
    val component = buildPlayerAppComponent(
        sharedGraph = sharedGraph,
        playerRuntimeServices = PlayerRuntimeServices(
            playbackGateway = playbackGateway,
            playbackPreferencesStore = appPreferencesStore,
            lyricsSharePlatformService = JvmLyricsSharePlatformService(lyricsShareFontLibraryPlatformService),
            lyricsShareFontLibraryPlatformService = lyricsShareFontLibraryPlatformService,
            lyricsShareFontPreferencesStore = appPreferencesStore,
            systemPlaybackControlsPlatformService = systemPlaybackControls.service,
            menuBarLyricsControlsPlatformService = menuBarLyricsControls.service,
            closeDesktopResources = { resourceGuard.closeAll().getOrThrow() },
        ),
    )
    resourceGuard.transfer(sharedScopeToken)
    resourceGuard.transfer(desktopLyricsToken)
    resourceGuard.transfer(playbackGatewayToken)
    resourceGuard.transfer(menuBarLyricsControlsToken)
    resourceGuard.transfer(systemPlaybackControlsToken)
    component
    } catch (error: Throwable) {
        resourceGuard.closeAllBlocking().exceptionOrNull()?.let { closeError ->
            error.addSuppressedSafely(closeError)
        }
        throw error
    }
}

private suspend fun stopJvmAppScope(scope: CoroutineScope) {
    val job = scope.coroutineContext[Job] ?: return
    withTimeout(JVM_APP_SCOPE_SHUTDOWN_TIMEOUT_MILLIS) {
        job.cancelAndJoin()
    }
}

private const val JVM_APP_SCOPE_SHUTDOWN_TIMEOUT_MILLIS = 2_000L

private object JvmDailyRecommendationDateKeyProvider : DailyRecommendationDateKeyProvider {
    override fun currentDateKey(): String = LocalDate.now().toString()
}

private class JvmDailyRecommendationDateChangeNotifier(
    private val dateKeyProvider: DailyRecommendationDateKeyProvider,
) : DailyRecommendationDateChangeNotifier {
    private val mutableDateKeys = MutableStateFlow(dateKeyProvider.currentDateKey())
    private val timer = Timer("daily-recommendation-date", true)

    override val dateKeys: Flow<String> = mutableDateKeys.asStateFlow()

    init {
        scheduleNextMidnightRefresh()
    }

    override fun refreshCurrentDateKey() {
        mutableDateKeys.value = dateKeyProvider.currentDateKey()
    }

    private fun scheduleNextMidnightRefresh() {
        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    refreshCurrentDateKey()
                    scheduleNextMidnightRefresh()
                }
            },
            millisUntilNextLocalMidnight(),
        )
    }
}

private fun millisUntilNextLocalMidnight(): Long {
    val now = ZonedDateTime.now()
    val nextMidnight = now.toLocalDate()
        .plusDays(1)
        .atStartOfDay(now.zone)
    return (Duration.between(now, nextMidnight).toMillis() + 1_000L)
        .coerceAtLeast(1_000L)
}

internal class JvmLyricsHttpClient : LyricsHttpClient {
    private val client = HttpClient(OkHttp) {
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
                    top.iwesley.lyn.music.core.model.RequestMethod.GET -> HttpMethod.Get
                    top.iwesley.lyn.music.core.model.RequestMethod.POST -> HttpMethod.Post
                    top.iwesley.lyn.music.core.model.RequestMethod.DELETE -> HttpMethod.Delete
                }
                request.headers.forEach { (key, value) -> headers.append(key, value) }
                request.body?.let { body ->
                    // A write OkHttp must not resend; see [LyricsRequest.allowTransportRetry].
                    if (request.allowTransportRetry) setBody(body) else setBody(oneShotTextContent(body, contentType()))
                }
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
            LyricsHttpResponse(
                statusCode = response.status.value,
                body = response.bodyAsText(),
                headers = response.headers.entries().associate { entry ->
                    entry.key to entry.value.joinToString(",")
                },
            )
        }
    }

    internal fun close() {
        client.close()
    }
}

internal class JvmAppPreferencesStore(
    settingsFile: File = defaultJvmSettingsFile(),
) : AppLanguagePreferencesStore, PlaybackPreferencesStore, SambaCachePreferencesStore, ThemePreferencesStore,
    CompactPlayerLyricsPreferencesStore, DesktopLyricsPreferencesStore, DesktopLyricsPositionPreferencesStore,
    MenuBarLyricsControlsPreferencesStore,
    DesktopVlcPreferencesStore, LyricsShareFontPreferencesStore, PlayerArtworkStylePreferencesStore,
    LibrarySourceFilterPreferencesStore, WindowClosePreferencesStore, AutoOpenPlayerOnStartupPreferencesStore {
    private val propertiesFile = JvmSettingsPropertiesFile(settingsFile)
    private val mutableAppLanguage = MutableStateFlow(appLanguageOrDefault(readNullablePreference(APP_LANGUAGE_PREFERENCE_KEY)))
    override val appLanguage: StateFlow<AppLanguage> = mutableAppLanguage.asStateFlow()
    init { AppLanguageRuntime.install(this, java.util.Locale.getDefault().toLanguageTag()) }
    override suspend fun setAppLanguage(language: AppLanguage) {
        AppLanguageRuntime.updateSystemLanguage(java.util.Locale.getDefault().toLanguageTag())
        updateProperties(
            mutate = { setProperty(APP_LANGUAGE_PREFERENCE_KEY, language.storageValue) },
            onPersisted = {
                mutableAppLanguage.value = language
                AppLanguageRuntime.update(language)
            },
        )
    }

    private val mutableUseSambaCache = MutableStateFlow(readUseSambaCache())
    private val mutablePlaybackVolume = MutableStateFlow(readPlaybackVolume())
    private val mutableShowCompactPlayerLyrics = MutableStateFlow(readShowCompactPlayerLyrics())
    private val mutableShowDesktopLyrics = MutableStateFlow(readShowDesktopLyrics())
    private val mutableDesktopLyricsPosition = MutableStateFlow(readDesktopLyricsPosition())
    private val mutableShowMenuBarLyricsControls = MutableStateFlow(readShowMenuBarLyricsControls())
    private val mutableAutoPlayOnStartup = MutableStateFlow(readAutoPlayOnStartup())
    private val mutableAutoOpenPlayerOnStartup = MutableStateFlow(readAutoOpenPlayerOnStartup())
    private val mutableMinimizeWindowOnClose = MutableStateFlow(readMinimizeWindowOnClose())
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
    private val mutableDesktopVlcManualPath = MutableStateFlow(readDesktopVlcManualPath())
    private val mutablePlayerArtworkStyle = MutableStateFlow(readPlayerArtworkStyle())
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
    override val showDesktopLyrics: StateFlow<Boolean> = mutableShowDesktopLyrics.asStateFlow()
    override val desktopLyricsPosition: StateFlow<DesktopLyricsPosition?> =
        mutableDesktopLyricsPosition.asStateFlow()
    override val showMenuBarLyricsControls: StateFlow<Boolean> = mutableShowMenuBarLyricsControls.asStateFlow()
    override val autoPlayOnStartup: StateFlow<Boolean> = mutableAutoPlayOnStartup.asStateFlow()
    override val autoOpenPlayerOnStartup: StateFlow<Boolean> =
        mutableAutoOpenPlayerOnStartup.asStateFlow()
    override val minimizeWindowOnClose: StateFlow<Boolean> = mutableMinimizeWindowOnClose.asStateFlow()
    override val selectedTheme: StateFlow<AppThemeId> = mutableSelectedTheme.asStateFlow()
    override val customThemeTokens: StateFlow<AppThemeTokens> = mutableCustomThemeTokens.asStateFlow()
    override val textPalettePreferences: StateFlow<AppThemeTextPalettePreferences> = mutableTextPalettePreferences.asStateFlow()
    override val desktopVlcManualPath: StateFlow<String?> = mutableDesktopVlcManualPath.asStateFlow()
    override val desktopVlcAutoDetectedPath: StateFlow<String?> = mutableDesktopVlcAutoDetectedPath.asStateFlow()
    override val desktopVlcEffectivePath: StateFlow<String?> = mutableDesktopVlcEffectivePath.asStateFlow()
    override val selectedLyricsShareFontKey: StateFlow<String?> = mutableSelectedLyricsShareFontKey.asStateFlow()
    override val playerArtworkStyle: StateFlow<PlayerArtworkStyle> = mutablePlayerArtworkStyle.asStateFlow()
    override val librarySourceFilter: StateFlow<LibrarySourceFilter> = mutableLibrarySourceFilter.asStateFlow()
    override val favoritesSourceFilter: StateFlow<LibrarySourceFilter> = mutableFavoritesSourceFilter.asStateFlow()
    override val onlineLibrarySourceId: StateFlow<String?> = mutableOnlineLibrarySourceId.asStateFlow()
    override val onlineFavoritesSourceId: StateFlow<String?> = mutableOnlineFavoritesSourceId.asStateFlow()
    override val onlinePlaylistsSourceId: StateFlow<String?> = mutableOnlinePlaylistsSourceId.asStateFlow()
    override val libraryTrackSortMode: StateFlow<TrackSortMode> = mutableLibraryTrackSortMode.asStateFlow()
    override val favoritesTrackSortMode: StateFlow<TrackSortMode> = mutableFavoritesTrackSortMode.asStateFlow()

    override suspend fun setUseSambaCache(enabled: Boolean) {
        updateProperties(
            mutate = { setProperty(KEY_USE_SAMBA_CACHE, enabled.toString()) },
            onPersisted = { mutableUseSambaCache.value = enabled },
        )
    }

    override suspend fun setPlaybackVolume(volume: Float) {
        val normalizedVolume = normalizePlaybackVolume(volume)
        updateProperties(
            mutate = { setProperty(KEY_PLAYBACK_VOLUME, normalizedVolume.toString()) },
            onPersisted = { mutablePlaybackVolume.value = normalizedVolume },
        )
    }

    override suspend fun setShowCompactPlayerLyrics(enabled: Boolean) {
        updateProperties(
            mutate = { setProperty(KEY_SHOW_COMPACT_PLAYER_LYRICS, enabled.toString()) },
            onPersisted = { mutableShowCompactPlayerLyrics.value = enabled },
        )
    }

    override suspend fun setShowDesktopLyrics(enabled: Boolean) {
        updateProperties(
            mutate = { setProperty(KEY_SHOW_DESKTOP_LYRICS, enabled.toString()) },
            onPersisted = { mutableShowDesktopLyrics.value = enabled },
        )
    }

    override suspend fun setDesktopLyricsPosition(position: DesktopLyricsPosition) {
        val normalized = desktopLyricsPositionOrNull(
            centerXFraction = position.centerXFraction,
            centerYFraction = position.centerYFraction,
            displayId = position.displayId,
        ) ?: return
        updateProperties(
            mutate = {
                setProperty(KEY_DESKTOP_LYRICS_POSITION_X, normalized.centerXFraction.toString())
                setProperty(KEY_DESKTOP_LYRICS_POSITION_Y, normalized.centerYFraction.toString())
                val displayId = normalized.displayId
                if (displayId == null) {
                    remove(KEY_DESKTOP_LYRICS_POSITION_DISPLAY_ID)
                } else {
                    setProperty(KEY_DESKTOP_LYRICS_POSITION_DISPLAY_ID, displayId)
                }
            },
            onPersisted = { mutableDesktopLyricsPosition.value = normalized },
        )
    }

    override suspend fun setShowMenuBarLyricsControls(enabled: Boolean) {
        updateProperties(
            mutate = { setProperty(KEY_SHOW_MENU_BAR_LYRICS_CONTROLS, enabled.toString()) },
            onPersisted = { mutableShowMenuBarLyricsControls.value = enabled },
        )
    }

    override suspend fun setAutoPlayOnStartup(enabled: Boolean) {
        updateProperties(
            mutate = { setProperty(KEY_AUTO_PLAY_ON_STARTUP, enabled.toString()) },
            onPersisted = { mutableAutoPlayOnStartup.value = enabled },
        )
    }

    override suspend fun setAutoOpenPlayerOnStartup(enabled: Boolean) {
        updateProperties(
            mutate = { setProperty(KEY_AUTO_OPEN_PLAYER_ON_STARTUP, enabled.toString()) },
            onPersisted = { mutableAutoOpenPlayerOnStartup.value = enabled },
        )
    }

    override suspend fun setMinimizeWindowOnClose(enabled: Boolean) {
        updateProperties(
            mutate = { setProperty(KEY_MACOS_MINIMIZE_WINDOW_ON_CLOSE, enabled.toString()) },
            onPersisted = { mutableMinimizeWindowOnClose.value = enabled },
        )
    }

    override suspend fun setPlayerArtworkStyle(style: PlayerArtworkStyle) {
        updateProperties(
            mutate = { setProperty(KEY_PLAYER_ARTWORK_STYLE, style.name) },
            onPersisted = { mutablePlayerArtworkStyle.value = style },
        )
    }

    override suspend fun setLibrarySourceFilter(filter: LibrarySourceFilter) {
        updateProperties(
            mutate = { setProperty(KEY_LIBRARY_SOURCE_FILTER, filter.name) },
            onPersisted = { mutableLibrarySourceFilter.value = filter },
        )
    }

    override suspend fun setFavoritesSourceFilter(filter: LibrarySourceFilter) {
        updateProperties(
            mutate = { setProperty(KEY_FAVORITES_SOURCE_FILTER, filter.name) },
            onPersisted = { mutableFavoritesSourceFilter.value = filter },
        )
    }

    override suspend fun setOnlineLibrarySourceId(sourceId: String?) {
        val normalizedSourceId = normalizeNullablePreference(sourceId)
        updateNullablePreference(
            key = KEY_ONLINE_LIBRARY_SOURCE_ID,
            value = normalizedSourceId,
            onPersisted = { mutableOnlineLibrarySourceId.value = normalizedSourceId },
        )
    }

    override suspend fun setOnlineFavoritesSourceId(sourceId: String?) {
        val normalizedSourceId = normalizeNullablePreference(sourceId)
        updateNullablePreference(
            key = KEY_ONLINE_FAVORITES_SOURCE_ID,
            value = normalizedSourceId,
            onPersisted = { mutableOnlineFavoritesSourceId.value = normalizedSourceId },
        )
    }

    override suspend fun setOnlinePlaylistsSourceId(sourceId: String?) {
        val normalizedSourceId = normalizeNullablePreference(sourceId)
        updateNullablePreference(
            key = KEY_ONLINE_PLAYLISTS_SOURCE_ID,
            value = normalizedSourceId,
            onPersisted = { mutableOnlinePlaylistsSourceId.value = normalizedSourceId },
        )
    }

    override suspend fun setLibraryTrackSortMode(mode: TrackSortMode) {
        updateProperties(
            mutate = { setProperty(KEY_LIBRARY_TRACK_SORT_MODE, mode.name) },
            onPersisted = { mutableLibraryTrackSortMode.value = mode },
        )
    }

    override suspend fun setFavoritesTrackSortMode(mode: TrackSortMode) {
        updateProperties(
            mutate = { setProperty(KEY_FAVORITES_TRACK_SORT_MODE, mode.name) },
            onPersisted = { mutableFavoritesTrackSortMode.value = mode },
        )
    }

    override suspend fun setSelectedTheme(themeId: AppThemeId) {
        updateProperties(
            mutate = { setProperty(KEY_SELECTED_THEME, themeId.name) },
            onPersisted = { mutableSelectedTheme.value = themeId },
        )
    }

    override suspend fun setCustomThemeTokens(tokens: AppThemeTokens) {
        updateProperties(
            mutate = {
                setProperty(KEY_CUSTOM_THEME_BACKGROUND_ARGB, tokens.backgroundArgb.toString())
                setProperty(KEY_CUSTOM_THEME_ACCENT_ARGB, tokens.accentArgb.toString())
                setProperty(KEY_CUSTOM_THEME_FOCUS_ARGB, tokens.focusArgb.toString())
            },
            onPersisted = { mutableCustomThemeTokens.value = tokens },
        )
    }

    override suspend fun setTextPalette(themeId: AppThemeId, palette: AppThemeTextPalette) {
        updateProperties(
            mutate = { setProperty(textPaletteKey(themeId), palette.name) },
            onPersisted = {
                mutableTextPalettePreferences.value =
                    mutableTextPalettePreferences.value.withThemePalette(themeId, palette)
            },
        )
    }

    override suspend fun setDesktopVlcManualPath(path: String?) {
        val normalizedPath = path?.trim()?.takeIf { it.isNotBlank() }
        updateProperties(
            mutate = {
                if (normalizedPath == null) {
                    remove(KEY_DESKTOP_VLC_MANUAL_PATH)
                } else {
                    setProperty(KEY_DESKTOP_VLC_MANUAL_PATH, normalizedPath)
                }
            },
            onPersisted = {
                mutableDesktopVlcManualPath.value = normalizedPath
                mutableDesktopVlcEffectivePath.value = resolveDesktopVlcEffectivePath(
                    manualPath = mutableDesktopVlcManualPath.value,
                    autoDetectedPath = mutableDesktopVlcAutoDetectedPath.value,
                )
            },
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
        updateProperties(
            mutate = {
                if (normalizedValue == null) {
                    remove(KEY_LYRICS_SHARE_FONT_KEY)
                } else {
                    setProperty(KEY_LYRICS_SHARE_FONT_KEY, normalizedValue)
                }
            },
            onPersisted = { mutableSelectedLyricsShareFontKey.value = normalizedValue },
        )
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

    private fun readShowDesktopLyrics(): Boolean {
        return loadProperties().getProperty(KEY_SHOW_DESKTOP_LYRICS)?.toBooleanStrictOrNull() ?: false
    }

    private fun readDesktopLyricsPosition(): DesktopLyricsPosition? {
        val properties = loadProperties()
        return desktopLyricsPositionOrNull(
            centerXFraction = properties.getProperty(KEY_DESKTOP_LYRICS_POSITION_X)?.toFloatOrNull(),
            centerYFraction = properties.getProperty(KEY_DESKTOP_LYRICS_POSITION_Y)?.toFloatOrNull(),
            displayId = properties.getProperty(KEY_DESKTOP_LYRICS_POSITION_DISPLAY_ID),
        )
    }

    private fun readShowMenuBarLyricsControls(): Boolean {
        return loadProperties().getProperty(KEY_SHOW_MENU_BAR_LYRICS_CONTROLS)?.toBooleanStrictOrNull() ?: false
    }

    private fun readAutoPlayOnStartup(): Boolean {
        return loadProperties().getProperty(KEY_AUTO_PLAY_ON_STARTUP)?.toBooleanStrictOrNull() ?: false
    }

    private fun readAutoOpenPlayerOnStartup(): Boolean {
        return loadProperties().getProperty(KEY_AUTO_OPEN_PLAYER_ON_STARTUP)?.toBooleanStrictOrNull() ?: false
    }

    private fun readMinimizeWindowOnClose(): Boolean {
        return minimizeWindowOnCloseOrDefault(
            loadProperties().getProperty(KEY_MACOS_MINIMIZE_WINDOW_ON_CLOSE),
        )
    }

    private fun readPlayerArtworkStyle(): PlayerArtworkStyle {
        return playerArtworkStyleOrDefault(loadProperties().getProperty(KEY_PLAYER_ARTWORK_STYLE))
    }

    private fun readLibrarySourceFilter(key: String): LibrarySourceFilter {
        val name = loadProperties().getProperty(key)
        return LibrarySourceFilter.entries.firstOrNull { it.name == name } ?: LibrarySourceFilter.ALL
    }

    private fun readNullablePreference(key: String): String? {
        return normalizeNullablePreference(loadProperties().getProperty(key))
    }

    private fun normalizeNullablePreference(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotBlank() }
    }

    private suspend fun updateNullablePreference(
        key: String,
        value: String?,
        onPersisted: () -> Unit,
    ) {
        updateProperties(
            mutate = {
                if (value == null) {
                    remove(key)
                } else {
                    setProperty(key, value)
                }
            },
            onPersisted = onPersisted,
        )
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
        return propertiesFile.load()
    }

    private suspend fun updateProperties(
        mutate: Properties.() -> Unit,
        onPersisted: () -> Unit,
    ) {
        propertiesFile.update(mutate, onPersisted)
    }
}

internal class JvmSettingsPropertiesFile(
    settingsFile: File,
) {
    private val settingsFile = settingsFile.apply { parentFile?.mkdirs() }
    private val updateMutex = Mutex()

    fun load(): Properties {
        val properties = Properties()
        if (settingsFile.exists()) {
            settingsFile.inputStream().use { input -> properties.load(input) }
        }
        return properties
    }

    suspend fun update(
        mutate: Properties.() -> Unit,
        onPersisted: () -> Unit = {},
    ) {
        updateMutex.withLock {
            val properties = load().apply(mutate)
            persist(properties)
            onPersisted()
        }
    }

    private fun persist(properties: Properties) {
        val parentDirectory = settingsFile.parentFile ?: File(".")
        parentDirectory.mkdirs()
        val temporaryFile = File.createTempFile("${settingsFile.name}.", ".tmp", parentDirectory)
        try {
            temporaryFile.outputStream().use { output ->
                properties.store(output, "LynMusic settings")
            }
            try {
                Files.move(
                    temporaryFile.toPath(),
                    settingsFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporaryFile.toPath(),
                    settingsFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            temporaryFile.delete()
        }
    }
}

private fun defaultJvmSettingsFile(): File {
    return JvmAppDataDirectory.resolve("settings.properties")
}

internal fun minimizeWindowOnCloseOrDefault(value: String?): Boolean {
    return value?.toBooleanStrictOrNull() ?: DEFAULT_MINIMIZE_WINDOW_ON_CLOSE
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
                parseSubsonicCompatibleSongLocator(track.mediaLocator) != null -> null
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
                title = resolveUiString(Res.string.desktop_picker_artwork_title),
                extensionFilter = JvmFileExtensionFilter(
                    description = resolveUiString(Res.string.desktop_picker_image_files),
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
                val remoteCoverCandidates = if (
                    parseSubsonicCompatibleCoverLocator(rawTarget) != null ||
                    parseEmbyCoverLocator(rawTarget) != null ||
                    parseFnMusicCoverLocator(rawTarget) != null
                ) {
                    NavidromeLocatorRuntime.resolveCoverArtUrlCandidates(rawTarget).orEmpty()
                } else {
                    emptyList()
                }
                val target = remoteCoverCandidates.firstOrNull()?.value ?: rawTarget
                when {
                    target.isBlank() -> null
                    target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true) ->
                        loadRemoteArtworkBytes(
                            remoteCoverCandidates.takeIf { it.isNotEmpty() }
                                ?: listOf(RemotePlaybackUrlCandidate(value = target)),
                        )

                    target.startsWith("file://", ignoreCase = true) ->
                        Files.readAllBytes(Path.of(URI(target)))

                    else -> Files.readAllBytes(Path.of(target))
                }
            }
        }
    }

    private suspend fun loadRemoteArtworkBytes(
        targets: List<RemotePlaybackUrlCandidate>,
    ): ByteArray? {
        val resolved = readRemotePlaybackUrlCandidateWithFallback(
            candidates = targets,
            read = { target -> readRemoteSourceCandidateBytes(target) },
            isValidPayload = ::isCompleteArtworkPayload,
        ) ?: return null
        NavidromeLocatorRuntime.markResolvedUrlSuccess(resolved.first)
        return resolved.second
    }
}

private class JvmVlcPathPickerPlatformService : VlcPathPickerPlatformService {
    override suspend fun pickVlcDirectory(): Result<String?> {
        return runCatching {
            val selectedPath = JvmNativeFilePicker.pickFileOrDirectory(resolveUiString(Res.string.vlc_select_path_action)) ?: return@runCatching null
            val normalizedPath = normalizeDesktopVlcSelection(selectedPath)
                ?: throw UiTextException(desktopVlcInvalidSelectionMessage())
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
    val spec = resolveSambaSourceSpec(source, samba.second, fallbackRelativePath = track.relativePath)
    val password = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
    return JvmSambaTagReadTarget(
        sourceId = samba.first,
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

internal class JvmImportSourceGateway(
    private val logger: DiagnosticLogger,
    private val navidromeHttpClient: LyricsHttpClient,
) : ImportSourceGateway {
    override suspend fun pickLocalFolder(): LocalFolderSelection? {
        val path = JvmNativeFilePicker.pickDirectory(resolveUiString(Res.string.desktop_picker_music_folder_title)) ?: return null
        return LocalFolderSelection(
            label = path.name.ifBlank { path.toString() },
            persistentReference = path.toString(),
        )
    }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun scanLocalFolder(selection: LocalFolderSelection, sourceId: String): ImportScanReport {
        return scanLocalFolder(selection, sourceId, ImportScanProgressSink.NoOp)
    }

    override suspend fun scanLocalFolder(
        selection: LocalFolderSelection,
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        val root = Path.of(selection.persistentReference)
        if (!Files.exists(root)) {
            throw UiTextException(uiText(Res.string.source_folder_missing, selection.persistentReference))
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
                                progressSink.onProgress(
                                    ImportScanProgress(
                                        sourceId = sourceId,
                                        phase = ImportScanPhase.Scanning,
                                        importedTrackCount = tracks.size,
                                    ),
                                )
                            }.onFailure { throwable ->
                                failures += audioImportFailure(relativePath, throwable)
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
        // Sources rooted at the server only need to reach and log in; folders are checked when browsed or scanned.
        val sambaPath = parseSambaPath(draft.path)
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
                    "test-auth-ok endpoint=$endpoint share=${sambaPath?.shareName.orEmpty()}"
                }
                if (sambaPath != null) {
                    val share = session.connectShare(sambaPath.shareName) as DiskShare
                    if (sambaPath.directoryPath.isNotBlank() && !share.folderExists(sambaPath.directoryPath)) {
                        throw UiTextException(uiText(Res.string.samba_path_unavailable))
                    }
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

    override suspend fun listSambaDirectories(
        draft: SambaSourceDraft,
        relativePath: String,
    ): List<RemoteDirectoryEntry> {
        val endpoint = formatSambaEndpoint(draft.server, draft.port, draft.path)
        return runCatching {
            SMBClient().connect(draft.server, draft.port ?: DEFAULT_SAMBA_PORT).use { connection ->
                val session = connection.authenticate(AuthenticationContext(draft.username, draft.password.toCharArray(), ""))
                val location = resolveSambaRemoteFile(draft.path, relativePath)
                val names = if (location == null) {
                    listSambaDiskShares(session)
                } else {
                    (session.connectShare(location.shareName) as DiskShare).use { share ->
                        listSambaChildDirectories(share, location.directoryPath)
                    }
                }
                names.map { name -> RemoteDirectoryEntry(name = name, relativePath = joinSambaPath(relativePath, name)) }
            }
        }.onFailure { throwable ->
            logger.warn(SAMBA_LOG_TAG) {
                "list-folders-failed endpoint=$endpoint path=$relativePath reason=${throwable.message.orEmpty()}"
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
        val targets = planSambaScanTargets(draft.path, draft.selectedDirectories)
        if (targets.isEmpty()) throw UiTextException(uiText(Res.string.samba_path_missing_share))
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
                    "auth-ok source=$sourceId endpoint=$endpoint folders=${targets.size}"
                }
                val tracks = mutableListOf<top.iwesley.lyn.music.core.model.ImportedTrackCandidate>()
                val failures = mutableListOf<ImportScanFailure>()
                val shares = mutableMapOf<String, DiskShare>()
                val folderScan = scanSelectedFolders(
                    folders = targets,
                    failures = failures,
                    folderPath = { it.relativePrefix },
                ) { target ->
                    val share = shares.getOrPut(target.shareName) { session.connectShare(target.shareName) as DiskShare }
                    collectSambaTracks(
                        share = share,
                        baseDirectory = target.directoryPath,
                        relativeDirectory = "",
                        sourceId = sourceId,
                        sink = tracks,
                        failures = failures,
                        progressSink = progressSink,
                        relativePrefix = target.relativePrefix,
                    )
                }
                ImportScanReport(
                    tracks = tracks,
                    discoveredAudioFileCount = folderScan.discoveredAudioFileCount,
                    unreadableFolders = folderScan.unreadableFolders,
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

    override suspend fun listWebDavDirectories(
        draft: WebDavSourceDraft,
        relativePath: String,
    ): List<RemoteDirectoryEntry> = listJvmWebDavDirectories(draft, relativePath, logger)

    override suspend fun scanWebDav(draft: WebDavSourceDraft, sourceId: String): ImportScanReport {
        return scanWebDav(draft, sourceId, ImportScanProgressSink.NoOp)
    }

    override suspend fun scanWebDav(
        draft: WebDavSourceDraft,
        sourceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        return scanJvmWebDav(draft, sourceId, logger, progressSink)
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
            supportedImportExtensions = JVM_SUPPORTED_IMPORT_AUDIO_EXTENSIONS,
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
            supportedImportExtensions = JVM_SUPPORTED_IMPORT_AUDIO_EXTENSIONS,
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
            supportedImportExtensions = JVM_SUPPORTED_IMPORT_AUDIO_EXTENSIONS,
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
            supportedImportExtensions = JVM_SUPPORTED_IMPORT_AUDIO_EXTENSIONS,
            logger = logger,
            progressSink = progressSink,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }

    override suspend fun testFnMusic(draft: FnMusicSourceDraft, deviceId: String) {
        testFnMusicConnection(
            draft = draft,
            deviceId = deviceId,
            httpClient = navidromeHttpClient,
            logger = logger,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }

    override suspend fun scanFnMusic(draft: FnMusicSourceDraft, sourceId: String, deviceId: String): ImportScanReport {
        return scanFnMusic(draft, sourceId, deviceId, ImportScanProgressSink.NoOp)
    }

    override suspend fun scanFnMusic(
        draft: FnMusicSourceDraft,
        sourceId: String,
        deviceId: String,
        progressSink: ImportScanProgressSink,
    ): ImportScanReport {
        return scanFnMusicLibrary(
            draft = draft,
            sourceId = sourceId,
            deviceId = deviceId,
            httpClient = navidromeHttpClient,
            supportedImportExtensions = JVM_SUPPORTED_IMPORT_AUDIO_EXTENSIONS,
            logger = logger,
            progressSink = progressSink,
            timeoutMillis = IMPORT_SOURCE_REQUEST_TIMEOUT_MILLIS,
        )
    }

    private fun collectSambaTracks(
        share: DiskShare,
        baseDirectory: String,
        relativeDirectory: String,
        sourceId: String,
        sink: MutableList<top.iwesley.lyn.music.core.model.ImportedTrackCandidate>,
        failures: MutableList<ImportScanFailure>,
        progressSink: ImportScanProgressSink,
        relativePrefix: String = "",
    ): Int {
        var discoveredAudioFileCount = 0
        val listPath = joinSegments(baseDirectory, relativeDirectory)
        share.list(listPath).forEach { fileInfo ->
            val name = fileInfo.fileName
            if (name == "." || name == "..") return@forEach
            val childDirectory = joinSegments(relativeDirectory, name)
            // Track paths are relative to the source root, which sits [relativePrefix] above the scanned folder.
            val childRelative = joinSegments(relativePrefix, childDirectory)
            val childPath = joinSegments(baseDirectory, childDirectory)
            val isDirectory = share.folderExists(childPath)
            if (isDirectory) {
                discoveredAudioFileCount += collectSambaTracks(
                    share = share,
                    baseDirectory = baseDirectory,
                    relativeDirectory = childDirectory,
                    sourceId = sourceId,
                    sink = sink,
                    failures = failures,
                    progressSink = progressSink,
                    relativePrefix = relativePrefix,
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

private data class JvmRemotePlaybackFallback(
    val candidates: List<RemoteSourceResolvedUrl>,
    val selectedIndex: Int,
    val track: Track,
    val sourceReference: String,
    val playWhenReady: Boolean,
    val loadToken: PlaybackLoadToken,
) {
    fun currentCandidate(): RemoteSourceResolvedUrl? = candidates.getOrNull(selectedIndex)
}

internal class JvmPlaybackGateway(
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val playbackPreferencesStore: PlaybackPreferencesStore,
    private val desktopVlcPreferencesStore: DesktopVlcPreferencesStore,
    private val logger: DiagnosticLogger,
    private val addressSelector: RemoteSourceAddressSelector = RemoteSourceAddressSelector(WifiNetworkConnectionTypeProvider),
    /** Logs in to FN Music before playback; FN Music tracks cannot play without it. */
    private val fnMusicHttpClient: LyricsHttpClient? = null,
    private val runtimeInitializer: suspend () -> JvmVlcRuntimeInitializationResult = {
        createJvmVlcRuntimeInitializationResult(
            desktopVlcPreferencesStore = desktopVlcPreferencesStore,
            logger = logger,
        )
    },
    runtimeDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PlaybackGateway {
    private val mutableState = MutableStateFlow(PlaybackGatewayState())
    private val scope = CoroutineScope(SupervisorJob() + runtimeDispatcher)
    private val runtimeLock = Any()
    private val nativePlaybackMutex = Mutex()
    private val pendingInitialSeekLock = Any()
    private var runtimeState: JvmVlcRuntimeState = JvmVlcRuntimeState.Initializing
    private var pendingLoad: PendingVlcLoad? = null
    private var pendingInitialSeek: PendingInitialSeek? = null
    private val sambaCacheDir = JvmAppDataDirectory.resolve("cache").apply {
        mkdirs()
    }
    private var currentCallbackMedia: CallbackMedia? = null

    /** Frees what [currentCallbackMedia] holds outside VLC; see [JvmWebDavPlaybackTarget.release]. */
    @Volatile
    private var currentCallbackMediaRelease: (() -> Unit)? = null
    private val recentVlcLogs = ArrayDeque<String>(MAX_RECENT_VLC_LOGS)
    @Volatile
    private var currentPlaybackTarget: String? = null
    @Volatile
    private var currentSourceReference: String? = null
    @Volatile
    private var currentTrackForMetadata: Track? = null
    @Volatile
    private var currentRemotePlaybackFallback: JvmRemotePlaybackFallback? = null
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
            val result = runCatching {
                runtimeInitializer()
            }.getOrElse { throwable ->
                if (throwable is CancellationException) throw throwable
                logger.error(VLC_LOG_TAG, throwable) {
                    "native-init-failed message=${throwable.message.orEmpty()}"
                }
                JvmVlcRuntimeInitializationResult(
                    runtime = null,
                    autoDetectedPath = null,
                    manualPath = desktopVlcPreferencesStore.desktopVlcManualPath.value,
                    effectivePath = null,
                )
            }
            handleRuntimeInitializationResult(result)
        }
    }

    private fun createMediaEventListener(): MediaEventAdapter {
        return object : MediaEventAdapter() {
            override fun mediaDurationChanged(media: Media?, newDuration: Long) {
                super.mediaDurationChanged(media, newDuration)
                logger.info(VLC_LOG_TAG) {
                    "mediaDurationChanged $newDuration"
                }
            }

            override fun mediaParsedChanged(media: Media?, newStatus: MediaParsedStatus?) {
                super.mediaParsedChanged(media, newStatus)
                logger.info(VLC_LOG_TAG) {
                    "mediaParsedChanged $newStatus"
                }
            }
        }
    }

    private fun createMediaPlayerEventListener(): MediaPlayerEventAdapter {
        return object : MediaPlayerEventAdapter() {
            override fun mediaPlayerReady(mediaPlayer: MediaPlayer?) {
                val track = currentTrackForMetadata
                val playbackTarget = currentPlaybackTarget ?: return
                mutableState.update {
                    it.copy(
                        metadataTitle = track?.title?.takeIf { value -> value.isNotBlank() } ?: it.metadataTitle,
                        metadataArtistName = track?.artistName?.takeIf { value -> value.isNotBlank() } ?: it.metadataArtistName,
                        metadataAlbumTitle = track?.albumTitle?.takeIf { value -> value.isNotBlank() } ?: it.metadataAlbumTitle,
                        errorMessage = null,
                        errorText = null,
                    )
                }
                logger.info(VLC_LOG_TAG) {
                    "media-player-ready track=${track?.id.orEmpty()} target=$playbackTarget source=${currentSourceReference.orEmpty()}"
                }
                schedulePendingInitialSeek("ready")
            }

            override fun playing(mediaPlayer: MediaPlayer?) {
                currentRemotePlaybackFallback?.currentCandidate()?.let { candidate ->
                    candidate.sourceId.takeIf { it.isNotBlank() }?.let { sourceId ->
                        addressSelector.markSuccess(sourceId, candidate.kind)
                    }
                }
                mutableState.update {
                    it.copy(
                        isPlaying = true,
                        errorMessage = null,
                        errorText = null,
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
                    )
                }
            }

            override fun seekableChanged(mediaPlayer: MediaPlayer?, newSeekable: Int) {
                mutableState.update {
                    it.copy(canSeek = newSeekable != 0)
                }
                if (newSeekable != 0) {
                    schedulePendingInitialSeek("seekable")
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
                val recentLogs = recentVlcLogSummary()
                logger.error(VLC_LOG_TAG) {
                    "playback-error target=${redactRemoteSourceUrlForLog(currentPlaybackTarget.orEmpty())} source=${currentSourceReference.orEmpty()} recentLogs=$recentLogs"
                }
                if (tryScheduleRemoteAddressFallback(recentLogs)) {
                    return
                }
                mutableState.update {
                    it.copy(
                        errorMessage = (DESKTOP_MEDIA_FAILED_TEXT).diagnosticMessage(),
                        errorText = DESKTOP_MEDIA_FAILED_TEXT,
                        canSeek = false,
                        errorRevision = it.errorRevision + 1L,
                    )
                }
            }
        }
    }

    private fun tryScheduleRemoteAddressFallback(errorDetail: String): Boolean {
        val fallback = currentRemotePlaybackFallback ?: return false
        if (!fallback.loadToken.isCurrent()) return false
        if (!isJvmRemotePlaybackFallbackAllowed(errorDetail)) return false
        val nextIndex = fallback.selectedIndex + 1
        val nextCandidate = fallback.candidates.getOrNull(nextIndex) ?: return false
        val runtime = synchronized(runtimeLock) {
            (runtimeState as? JvmVlcRuntimeState.Ready)?.runtime
        } ?: return false
        val retryPositionMs = mutableState.value.positionMs.coerceAtLeast(0L)
        val retryPlayWhenReady = mutableState.value.isPlaying || fallback.playWhenReady
        val nextFallback = fallback.copy(
            selectedIndex = nextIndex,
            playWhenReady = retryPlayWhenReady,
        )
        currentRemotePlaybackFallback = nextFallback
        logger.warn(VLC_LOG_TAG) {
            "remote-address-fallback retry index=$nextIndex url=${redactRemoteSourceUrlForLog(nextCandidate.value)} recentLogs=$errorDetail"
        }
        mutableState.update { it.copy(errorMessage = null, errorText = null) }
        scope.launch {
            applyRemoteAddressFallback(
                runtime = runtime,
                fallback = nextFallback,
                candidate = nextCandidate,
                retryPositionMs = retryPositionMs,
                retryPlayWhenReady = retryPlayWhenReady,
            )
        }
        return true
    }

    private suspend fun applyRemoteAddressFallback(
        runtime: JvmVlcPlaybackRuntime,
        fallback: JvmRemotePlaybackFallback,
        candidate: RemoteSourceResolvedUrl,
        retryPositionMs: Long,
        retryPlayWhenReady: Boolean,
    ) {
        var pendingSeek: PendingInitialSeek? = null
        var skipped = false
        var started = false
        try {
            nativePlaybackMutex.withLock {
                if (
                    !fallback.loadToken.isCurrent() ||
                    !isCurrentRuntime(runtime) ||
                    currentRemotePlaybackFallback != fallback
                ) {
                    skipped = true
                    return@withLock
                }
                runCatching { runtime.stop() }
                clearRecentVlcLogs()
                clearCurrentCallbackMedia()
                currentPlaybackTarget = candidate.value
                currentSourceReference = fallback.sourceReference
                currentTrackForMetadata = fallback.track
                mutableState.update {
                    it.copy(
                        isPlaying = retryPlayWhenReady,
                        canSeek = false,
                        errorMessage = null,
                        errorText = null,
                    )
                }
                pendingSeek = if (retryPositionMs > 0L) {
                    PendingInitialSeek(
                        runtime = runtime,
                        trackId = fallback.track.id,
                        sourceReference = fallback.sourceReference,
                        positionMs = retryPositionMs,
                        loadToken = fallback.loadToken,
                    )
                } else {
                    null
                }
                pendingSeek?.let(::replacePendingInitialSeek) ?: clearPendingInitialSeek()
                val playbackMedia = JvmVlcPlaybackMedia.Source(candidate.value)
                started = if (retryPlayWhenReady) {
                    runtime.start(playbackMedia)
                } else {
                    runtime.startPaused(playbackMedia)
                }
            }
            if (skipped) return
            if (!started) {
                pendingSeek?.let(::clearPendingInitialSeek)
                logger.error(VLC_LOG_TAG) {
                    "remote-address-fallback-start-failed target=${redactRemoteSourceUrlForLog(candidate.value)} source=${fallback.sourceReference} recentLogs=${recentVlcLogSummary()}"
                }
                mutableState.update {
                    it.copy(
                        isPlaying = false,
                        canSeek = false,
                        errorMessage = (DESKTOP_MEDIA_FAILED_TEXT).diagnosticMessage(),
                        errorText = DESKTOP_MEDIA_FAILED_TEXT,
                        errorRevision = it.errorRevision + 1L,
                    )
                }
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            pendingSeek?.let(::clearPendingInitialSeek)
            logger.error(VLC_LOG_TAG, throwable) {
                "remote-address-fallback-failed target=${redactRemoteSourceUrlForLog(candidate.value)} source=${fallback.sourceReference}"
            }
            mutableState.update {
                it.copy(
                    isPlaying = false,
                    canSeek = false,
                    errorMessage = buildJvmPlaybackLoadFailureMessage(throwable),
                    errorText = playbackLoadFailureText(throwable),
                    errorRevision = it.errorRevision + 1L,
                )
            }
        }
    }

    private suspend fun handleRuntimeInitializationResult(result: JvmVlcRuntimeInitializationResult) {
        var initializedRuntime: JvmVlcPlaybackRuntime? = result.runtime
        val pending: PendingVlcLoad?
        val runtimeToRelease: JvmVlcPlaybackRuntime?
        synchronized(runtimeLock) {
            if (runtimeState is JvmVlcRuntimeState.Released) {
                runtimeToRelease = initializedRuntime
                initializedRuntime = null
                pending = null
            } else {
                runtimeToRelease = null
                val runtimeAvailable = initializedRuntime != null && prepareRuntime(initializedRuntime)
                if (!runtimeAvailable) {
                    initializedRuntime = null
                }
                pending = pendingLoad
                pendingLoad = null
                runtimeState = initializedRuntime?.let { JvmVlcRuntimeState.Ready(it) }
                    ?: JvmVlcRuntimeState.Unavailable
            }
        }
        runtimeToRelease?.release()
        if (runtimeToRelease != null) return

        val activeRuntime = initializedRuntime
        val autoDetectedPath = if (activeRuntime != null) result.autoDetectedPath else null
        runCatching {
            desktopVlcPreferencesStore.setDesktopVlcAutoDetectedPath(autoDetectedPath)
        }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
            logger.warn(VLC_LOG_TAG) {
                "auto-detected-path-update-failed message=${throwable.message.orEmpty()}"
            }
        }
        logger.info(VLC_LOG_TAG) {
            "native-discovery autoDetectedPath=${autoDetectedPath.orEmpty()} manualPath=${result.manualPath.orEmpty()} effectivePath=${result.effectivePath.orEmpty()}"
        }
        if (activeRuntime != null) {
            nativePlaybackMutex.withLock {
                if (isCurrentRuntime(activeRuntime)) {
                    activeRuntime.setVolume((mutableState.value.volume.coerceIn(0f, 1f) * 100).roundToInt())
                }
            }
        }
        if (activeRuntime == null) {
            logger.warn(VLC_LOG_TAG) {
                "playback-disabled reason=vlc-native-unavailable"
            }
            pending?.takeIf { it.loadToken.isCurrent() }?.let {
                handleVlcUnavailable(
                    action = "load",
                    positionMs = it.startPositionMs,
                    errorText = if (it.playWhenReady) DESKTOP_VLC_UNAVAILABLE_TEXT else null,
                    clearMetadata = true,
                )
            }
            return
        }
        pending?.takeIf { it.loadToken.isCurrent() }?.let {
            loadWithRuntime(
                runtime = activeRuntime,
                track = it.track,
                playWhenReady = it.playWhenReady,
                startPositionMs = it.startPositionMs,
                loadToken = it.loadToken,
            )
        }
    }

    private fun prepareRuntime(runtime: JvmVlcPlaybackRuntime): Boolean {
        return runCatching {
            runtime.addLogListener(nativeLogListener)
            runtime.addMediaEventListener(createMediaEventListener())
            runtime.addMediaPlayerEventListener(createMediaPlayerEventListener())
        }.onFailure { throwable ->
            logger.error(VLC_LOG_TAG, throwable) {
                "native-listener-init-failed message=${throwable.message.orEmpty()}"
            }
            runtime.release()
        }.isSuccess
    }

    override suspend fun load(
        track: Track,
        playWhenReady: Boolean,
        startPositionMs: Long,
        loadToken: PlaybackLoadToken,
    ) {
        if (!loadToken.isCurrent()) {
            logger.debug(VLC_LOG_TAG) {
                "load-discarded-stale request=${loadToken.requestId} track=${track.id} before-prepare"
            }
            return
        }
        val pending = PendingVlcLoad(
            track = track,
            playWhenReady = playWhenReady,
            startPositionMs = startPositionMs,
            loadToken = loadToken,
        )
        when (val loadDecision = prepareLoad(pending)) {
            is JvmVlcLoadDecision.Ready -> loadWithRuntime(
                runtime = loadDecision.runtime,
                track = track,
                playWhenReady = playWhenReady,
                startPositionMs = startPositionMs,
                loadToken = loadToken,
            )

            JvmVlcLoadDecision.Initializing -> handleVlcInitializingLoad(pending)
            JvmVlcLoadDecision.Unavailable -> {
                if (!loadToken.isCurrent()) {
                    logger.debug(VLC_LOG_TAG) {
                        "load-discarded-stale request=${loadToken.requestId} track=${track.id} unavailable"
                    }
                    return
                }
                handleVlcUnavailable(
                    action = "load",
                    positionMs = startPositionMs,
                    errorText = if (playWhenReady) DESKTOP_VLC_UNAVAILABLE_TEXT else null,
                    clearMetadata = true,
                )
                clearCurrentCallbackMedia()
                currentPlaybackTarget = null
                currentSourceReference = track.mediaLocator
                currentTrackForMetadata = track
                currentRemotePlaybackFallback = null
            }

            JvmVlcLoadDecision.Released -> Unit
        }
    }

    /** Drops the current callback media and frees what it held outside VLC (after VLC stopped reading it). */
    private fun clearCurrentCallbackMedia() {
        currentCallbackMedia = null
        val release = currentCallbackMediaRelease ?: return
        currentCallbackMediaRelease = null
        runCatching(release)
    }

    private suspend fun loadWithRuntime(
        runtime: JvmVlcPlaybackRuntime,
        track: Track,
        playWhenReady: Boolean,
        startPositionMs: Long,
        loadToken: PlaybackLoadToken,
    ) {
        var initialSeekForLoad: PendingInitialSeek? = null
        // An FN Music target resolved here holds a stream (and maybe a temporary file) that only VLC's close would
        // free; every path that ends without VLC taking the media must release it itself.
        var unhandedRelease: (() -> Unit)? = null
        try {
            if (!loadToken.isCurrent()) {
                logger.debug(VLC_LOG_TAG) {
                    "load-discarded-stale request=${loadToken.requestId} track=${track.id} before-resolve"
                }
                return
            }
            val offlineTarget = resolveJvmOfflinePlaybackPath(database, track)
            val webDavTarget = if (offlineTarget == null) resolveJvmWebDavPlaybackTarget(
                database = database,
                secureCredentialStore = secureCredentialStore,
                locator = track.mediaLocator,
                logger = logger,
            ) else null
            val fnMusicTarget = if (offlineTarget == null && webDavTarget == null && fnMusicHttpClient != null) {
                resolveJvmFnMusicPlaybackTarget(
                    database = database,
                    secureCredentialStore = secureCredentialStore,
                    locator = track.mediaLocator,
                    httpClient = fnMusicHttpClient,
                    addressSelector = addressSelector,
                    logger = logger,
                )
            } else {
                null
            }
            unhandedRelease = fnMusicTarget?.release
            val sambaTarget = if (
                offlineTarget == null &&
                webDavTarget == null &&
                fnMusicTarget == null &&
                shouldUseJvmSambaCallback(track.mediaLocator, playbackPreferencesStore.useSambaCache.value)
            ) {
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
                if (offlineTarget == null && webDavTarget == null && sambaTarget == null && parseSubsonicCompatibleSongLocator(track.mediaLocator) != null) {
                    NavidromeAudioQuality.Original
                } else {
                    null
                }
            val remotePlaybackCandidates = if (offlineTarget == null && webDavTarget == null && fnMusicTarget == null && sambaTarget == null) {
                resolveLocatorCandidates(track.mediaLocator)
            } else {
                null
            }?.takeIf { it.isNotEmpty() }
            val selectedRemotePlaybackCandidate = remotePlaybackCandidates?.firstOrNull()
            val actualPlaybackSource = when {
                offlineTarget != null -> offlineTarget
                sambaTarget != null -> sambaTarget.sourceReference
                webDavTarget != null -> webDavTarget.requestUrl
                fnMusicTarget != null -> fnMusicTarget.requestUrl
                selectedRemotePlaybackCandidate != null -> selectedRemotePlaybackCandidate.value
                else -> resolveLocator(track.mediaLocator)
            }
            val sourceReference = when {
                offlineTarget != null -> track.mediaLocator
                parseSubsonicCompatibleSongLocator(track.mediaLocator) != null -> track.mediaLocator
                parseEmbySongLocator(track.mediaLocator) != null -> track.mediaLocator
                fnMusicTarget != null -> track.mediaLocator
                else -> actualPlaybackSource
            }
            if (!loadToken.isCurrent()) {
                logger.debug(VLC_LOG_TAG) {
                    "load-discarded-stale request=${loadToken.requestId} track=${track.id} before-start"
                }
                return
            }
            val playbackTarget = when {
                offlineTarget != null -> offlineTarget
                webDavTarget != null -> "webdav-callback://${track.id}"
                fnMusicTarget != null -> "fnmusic-callback://${track.id}"
                sambaTarget != null -> buildJvmSambaPlaybackTarget(track.id)
                selectedRemotePlaybackCandidate != null -> selectedRemotePlaybackCandidate.value
                else -> sourceReference
            }
            val playbackMedia = when {
                webDavTarget != null -> JvmVlcPlaybackMedia.Callback(webDavTarget.media)
                fnMusicTarget != null -> JvmVlcPlaybackMedia.Callback(fnMusicTarget.media)
                sambaTarget != null -> JvmVlcPlaybackMedia.Callback(sambaTarget.media)
                else -> JvmVlcPlaybackMedia.Source(actualPlaybackSource)
            }
            var loadSkipped = false
            val started = nativePlaybackMutex.withLock {
                if (!loadToken.isCurrent()) {
                    logger.debug(VLC_LOG_TAG) {
                        "load-discarded-stale request=${loadToken.requestId} track=${track.id} before-native"
                    }
                    loadSkipped = true
                    return@withLock true
                }
                if (!isCurrentRuntime(runtime)) {
                    logger.debug(VLC_LOG_TAG) {
                        "load-discarded-released request=${loadToken.requestId} track=${track.id}"
                    }
                    loadSkipped = true
                    return@withLock true
                }
                runCatching { runtime.stop() }
                clearRecentVlcLogs()
                if (!loadToken.isCurrent()) {
                    logger.debug(VLC_LOG_TAG) {
                        "load-discarded-stale request=${loadToken.requestId} track=${track.id} after-stop"
                    }
                    loadSkipped = true
                    return@withLock true
                }
                if (!isCurrentRuntime(runtime)) {
                    logger.debug(VLC_LOG_TAG) {
                        "load-discarded-released request=${loadToken.requestId} track=${track.id} after-stop"
                    }
                    loadSkipped = true
                    return@withLock true
                }
                clearCurrentCallbackMedia()
                currentPlaybackTarget = null
                currentSourceReference = null
                currentRemotePlaybackFallback = null
                currentTrackForMetadata = track
                currentPlaybackTarget = playbackTarget
                currentSourceReference = sourceReference
                currentRemotePlaybackFallback = remotePlaybackCandidates?.let { candidates ->
                    JvmRemotePlaybackFallback(
                        candidates = candidates,
                        selectedIndex = 0,
                        track = track,
                        sourceReference = sourceReference,
                        playWhenReady = playWhenReady,
                        loadToken = loadToken,
                    )
                }
                currentCallbackMedia = webDavTarget?.media ?: fnMusicTarget?.media ?: sambaTarget?.media
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
                        errorText = null,
                    )
                }
                initialSeekForLoad = if (startPositionMs > 0) {
                    PendingInitialSeek(
                        runtime = runtime,
                        trackId = track.id,
                        sourceReference = sourceReference,
                        positionMs = startPositionMs,
                        loadToken = loadToken,
                    )
                } else {
                    null
                }
                initialSeekForLoad?.let(::replacePendingInitialSeek) ?: clearPendingInitialSeek()
                val mediaStarted = if (playWhenReady) {
                    runtime.start(playbackMedia)
                } else {
                    runtime.startPaused(playbackMedia)
                }
                if (mediaStarted) {
                    // VLC owns the media now; its close, or the next load replacing it, frees the stream.
                    currentCallbackMediaRelease = unhandedRelease
                    unhandedRelease = null
                }
                mediaStarted
            }
            if (loadSkipped) return
            if (!started) {
                initialSeekForLoad?.let(::clearPendingInitialSeek)
                val recentLogs = recentVlcLogSummary()
                logger.error(VLC_LOG_TAG) {
                    "start-failed target=${redactRemoteSourceUrlForLog(playbackTarget)} source=$sourceReference playWhenReady=$playWhenReady recentLogs=$recentLogs"
                }
                if (tryScheduleRemoteAddressFallback(recentLogs)) {
                    return
                }
            }
            checkUi(started) { uiText(Res.string.playback_vlc_media_load_failed, playbackTarget) }
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
                        )
                    }
                }
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            initialSeekForLoad?.let(::clearPendingInitialSeek)
            logger.error(VLC_LOG_TAG, throwable) {
                "load-failed track=${track.id} locator=${track.mediaLocator} playWhenReady=$playWhenReady startPositionMs=$startPositionMs target=${currentPlaybackTarget.orEmpty()} source=${currentSourceReference.orEmpty()}"
            }
            // Not released: a load failing before it stopped VLC leaves the previous media playing.
            currentCallbackMedia = null
            mutableState.update {
                it.copy(
                    isPlaying = false,
                    positionMs = startPositionMs.coerceAtLeast(0L),
                    durationMs = 0L,
                    canSeek = false,
                    errorMessage = buildJvmPlaybackLoadFailureMessage(throwable),
                    errorText = playbackLoadFailureText(throwable),
                    errorRevision = it.errorRevision + 1L,
                )
            }
        } finally {
            // Stale, skipped, failed to start, failed or cancelled before VLC took the media.
            unhandedRelease?.let { runCatching(it) }
        }
    }

    override suspend fun play() {
        when (val runtimeState = preparePlay()) {
            is JvmVlcRuntimeState.Ready -> nativePlaybackMutex.withLock {
                if (isCurrentRuntime(runtimeState.runtime)) {
                    runtimeState.runtime.play()
                }
            }
            JvmVlcRuntimeState.Initializing -> handleVlcInitializingAction()
            JvmVlcRuntimeState.Unavailable -> handleVlcUnavailable(action = "play")
            JvmVlcRuntimeState.Released -> Unit
        }
    }

    override suspend fun pause() {
        when (val runtimeState = preparePause()) {
            is JvmVlcRuntimeState.Ready -> nativePlaybackMutex.withLock {
                if (isCurrentRuntime(runtimeState.runtime)) {
                    runtimeState.runtime.pause()
                }
            }
            JvmVlcRuntimeState.Initializing,
            JvmVlcRuntimeState.Unavailable,
            JvmVlcRuntimeState.Released -> mutableState.update { it.copy(isPlaying = false, errorMessage = null, errorText = null) }
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        when (val runtimeState = prepareSeek(positionMs)) {
            is JvmVlcRuntimeState.Ready -> nativePlaybackMutex.withLock {
                if (!isCurrentRuntime(runtimeState.runtime)) {
                    return@withLock
                }
                if (!runtimeState.runtime.canSeek()) {
                    mutableState.update { it.copy(canSeek = false) }
                    return@withLock
                }
                clearPendingInitialSeek()
                runtimeState.runtime.setTime(positionMs)
            }

            JvmVlcRuntimeState.Initializing -> mutableState.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
            JvmVlcRuntimeState.Unavailable -> handleVlcUnavailable(
                action = "seek",
                positionMs = positionMs,
            )
            JvmVlcRuntimeState.Released -> Unit
        }
    }

    override suspend fun setVolume(volume: Float) {
        val normalized = volume.coerceIn(0f, 1f)
        val runtime = synchronized(runtimeLock) {
            (runtimeState as? JvmVlcRuntimeState.Ready)?.runtime
        }
        if (runtime != null) {
            nativePlaybackMutex.withLock {
                if (isCurrentRuntime(runtime)) {
                    runtime.setVolume((normalized * 100).roundToInt())
                }
            }
        }
        mutableState.update { it.copy(volume = normalized) }
    }

    override suspend fun release() {
        val runtime = synchronized(runtimeLock) {
            val current = (runtimeState as? JvmVlcRuntimeState.Ready)?.runtime
            runtimeState = JvmVlcRuntimeState.Released
            pendingLoad = null
            current
        }
        currentTrackForMetadata = null
        currentCallbackMedia = null
        currentPlaybackTarget = null
        currentSourceReference = null
        currentRemotePlaybackFallback = null
        clearPendingInitialSeek()
        if (runtime != null) {
            nativePlaybackMutex.withLock {
                runtime.removeLogListener(nativeLogListener)
                runtime.release()
            }
        }
        // After VLC let go of the media, so the temporary file is no longer open.
        clearCurrentCallbackMedia()
        scope.cancel()
    }

    private fun isCurrentRuntime(runtime: JvmVlcPlaybackRuntime): Boolean {
        return synchronized(runtimeLock) {
            (runtimeState as? JvmVlcRuntimeState.Ready)?.runtime === runtime
        }
    }

    private fun replacePendingInitialSeek(pending: PendingInitialSeek) {
        synchronized(pendingInitialSeekLock) {
            pendingInitialSeek = pending
        }
    }

    private fun clearPendingInitialSeek(pending: PendingInitialSeek? = null) {
        synchronized(pendingInitialSeekLock) {
            if (pending == null || pendingInitialSeek === pending) {
                pendingInitialSeek = null
            }
        }
    }

    private fun currentPendingInitialSeek(): PendingInitialSeek? {
        return synchronized(pendingInitialSeekLock) { pendingInitialSeek }
    }

    private fun isPendingInitialSeekActive(pending: PendingInitialSeek): Boolean {
        return synchronized(pendingInitialSeekLock) { pendingInitialSeek === pending }
    }

    private fun schedulePendingInitialSeek(trigger: String) {
        val pending = currentPendingInitialSeek() ?: return
        scope.launch {
            applyPendingInitialSeek(pending, trigger)
        }
    }

    private suspend fun applyPendingInitialSeek(pending: PendingInitialSeek, trigger: String) {
        try {
            if (!pending.loadToken.isCurrent()) {
                clearPendingInitialSeek(pending)
                return
            }
            if (!isPendingInitialSeekActive(pending)) return
            if (currentTrackForMetadata?.id != pending.trackId || currentSourceReference != pending.sourceReference) {
                clearPendingInitialSeek(pending)
                return
            }
            nativePlaybackMutex.withLock {
                if (!isPendingInitialSeekActive(pending)) return@withLock
                if (!pending.loadToken.isCurrent()) {
                    clearPendingInitialSeek(pending)
                    return@withLock
                }
                if (!isCurrentRuntime(pending.runtime)) {
                    clearPendingInitialSeek(pending)
                    return@withLock
                }
                if (currentTrackForMetadata?.id != pending.trackId || currentSourceReference != pending.sourceReference) {
                    clearPendingInitialSeek(pending)
                    return@withLock
                }
                pending.runtime.setTime(pending.positionMs)
                clearPendingInitialSeek(pending)
                logger.debug(VLC_LOG_TAG) {
                    "initial-seek-applied trigger=$trigger track=${pending.trackId} source=${pending.sourceReference} positionMs=${pending.positionMs}"
                }
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            clearPendingInitialSeek(pending)
            logger.warn(VLC_LOG_TAG) {
                "initial-seek-failed trigger=$trigger track=${pending.trackId} positionMs=${pending.positionMs} message=${throwable.message.orEmpty()}"
            }
        }
    }

    private fun prepareLoad(pending: PendingVlcLoad): JvmVlcLoadDecision {
        return synchronized(runtimeLock) {
            when (val currentState = runtimeState) {
                is JvmVlcRuntimeState.Ready -> JvmVlcLoadDecision.Ready(currentState.runtime)
                JvmVlcRuntimeState.Initializing -> {
                    pendingLoad = pending
                    JvmVlcLoadDecision.Initializing
                }
                JvmVlcRuntimeState.Unavailable -> JvmVlcLoadDecision.Unavailable
                JvmVlcRuntimeState.Released -> JvmVlcLoadDecision.Released
            }
        }
    }

    private fun preparePlay(): JvmVlcRuntimeState {
        return synchronized(runtimeLock) {
            when (val currentState = runtimeState) {
                is JvmVlcRuntimeState.Ready -> currentState
                JvmVlcRuntimeState.Initializing -> {
                    pendingLoad = pendingLoad?.copy(playWhenReady = true)
                    currentState
                }
                JvmVlcRuntimeState.Unavailable,
                JvmVlcRuntimeState.Released -> currentState
            }
        }
    }

    private fun preparePause(): JvmVlcRuntimeState {
        return synchronized(runtimeLock) {
            when (val currentState = runtimeState) {
                is JvmVlcRuntimeState.Ready -> currentState
                JvmVlcRuntimeState.Initializing -> {
                    pendingLoad = pendingLoad?.copy(playWhenReady = false)
                    currentState
                }
                JvmVlcRuntimeState.Unavailable,
                JvmVlcRuntimeState.Released -> currentState
            }
        }
    }

    private fun prepareSeek(positionMs: Long): JvmVlcRuntimeState {
        return synchronized(runtimeLock) {
            when (val currentState = runtimeState) {
                is JvmVlcRuntimeState.Ready -> currentState
                JvmVlcRuntimeState.Initializing -> {
                    pendingLoad = pendingLoad?.copy(startPositionMs = positionMs.coerceAtLeast(0L))
                    currentState
                }
                JvmVlcRuntimeState.Unavailable,
                JvmVlcRuntimeState.Released -> currentState
            }
        }
    }

    private fun handleVlcInitializingLoad(pending: PendingVlcLoad) {
        if (!pending.loadToken.isCurrent()) {
            logger.debug(VLC_LOG_TAG) {
                "load-discarded-stale request=${pending.loadToken.requestId} track=${pending.track.id} initializing"
            }
            return
        }
        logger.info(VLC_LOG_TAG) {
            "load-pending reason=vlc-native-initializing track=${pending.track.id} playWhenReady=${pending.playWhenReady}"
        }
        clearCurrentCallbackMedia()
        currentPlaybackTarget = null
        currentSourceReference = pending.track.mediaLocator
        currentTrackForMetadata = pending.track
        currentRemotePlaybackFallback = null
        mutableState.update { state ->
            val message = if (pending.playWhenReady) DESKTOP_VLC_INITIALIZING_MESSAGE else null
            state.copy(
                isPlaying = false,
                positionMs = pending.startPositionMs.coerceAtLeast(0L),
                durationMs = 0L,
                canSeek = false,
                metadataTitle = null,
                metadataArtistName = null,
                metadataAlbumTitle = null,
                errorMessage = message,
                errorText = if (message != null) DESKTOP_VLC_INITIALIZING_TEXT else null,
                errorRevision = if (message != null) state.errorRevision + 1L else state.errorRevision,
            )
        }
    }

    private fun handleVlcInitializingAction() {
        logger.info(VLC_LOG_TAG) {
            "play-pending reason=vlc-native-initializing"
        }
        mutableState.update { state ->
            state.copy(
                isPlaying = false,
                canSeek = false,
                errorMessage = DESKTOP_VLC_INITIALIZING_MESSAGE,
                errorText = DESKTOP_VLC_INITIALIZING_TEXT,
                errorRevision = state.errorRevision + 1L,
            )
        }
    }

    private suspend fun resolveLocatorCandidates(locator: String): List<RemoteSourceResolvedUrl>? {
        resolveNavidromeStreamUrlCandidates(
            database = database,
            secureCredentialStore = secureCredentialStore,
            locator = locator,
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

    private suspend fun resolveLocator(locator: String): String {
        resolveNavidromeStreamUrl(
            database = database,
            secureCredentialStore = secureCredentialStore,
            locator = locator,
            addressSelector = addressSelector,
        )?.let { return it }
        resolveEmbyStreamUrl(
            database = database,
            secureCredentialStore = secureCredentialStore,
            locator = locator,
            addressSelector = addressSelector,
        )?.let { return it }
        val samba = parseSambaLocator(locator) ?: return locator
        val source = database.importSourceDao().getById(samba.first)?.takeIf { it.enabled }
            ?: throw UiTextException(uiText(Res.string.samba_source_unavailable))
        val spec = resolveSambaSourceSpec(source, samba.second)
        val endpoint = spec.endpoint
        val password = source.credentialKey?.let { secureCredentialStore.get(it) }.orEmpty()
        val username = spec.username
        val remotePath = spec.remotePath
        if (!playbackPreferencesStore.useSambaCache.value) {
            throw UiTextException(uiText(Res.string.samba_direct_target_missing))
        }
        val cacheFile = File(sambaCacheDir, buildSambaCacheFileName(samba.first, spec.cacheKeyPath))
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
            client.connect(spec.server, spec.port).use { connection ->
                logger.debug(SAMBA_LOG_TAG) {
                    "stream-connect-ok source=${samba.first} endpoint=$endpoint remoteHost=${connection.remoteHostname}"
                }
                val session = connection.authenticate(
                    AuthenticationContext(source.username.orEmpty(), password.toCharArray(), ""),
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

    private fun clearRecentVlcLogs() {
        synchronized(recentVlcLogs) {
            recentVlcLogs.clear()
        }
    }

    private fun recentVlcLogSummary(): String {
        return synchronized(recentVlcLogs) {
            if (recentVlcLogs.isEmpty()) {
                "none"
            } else {
                redactRemoteSourceUrlForLog(recentVlcLogs.joinToString(separator = " || "))
            }
        }
    }

    private fun handleVlcUnavailable(
        action: String,
        positionMs: Long? = null,
        errorText: UiText? = DESKTOP_VLC_UNAVAILABLE_TEXT,
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
                errorMessage = errorText?.let { (it).diagnosticMessage() },
                errorText = errorText,
                errorRevision = if (errorText != null) state.errorRevision + 1L else state.errorRevision,
            )
        }
    }
}

internal data class JvmVlcRuntimeInitializationResult(
    val runtime: JvmVlcPlaybackRuntime?,
    val autoDetectedPath: String?,
    val manualPath: String?,
    val effectivePath: String?,
)

internal interface JvmVlcPlaybackRuntime {
    val nativeLibraryPath: String?

    fun addLogListener(listener: LogEventListener)
    fun removeLogListener(listener: LogEventListener)
    fun addMediaEventListener(listener: MediaEventAdapter)
    fun addMediaPlayerEventListener(listener: MediaPlayerEventAdapter)
    fun stop()
    fun start(media: JvmVlcPlaybackMedia): Boolean
    fun startPaused(media: JvmVlcPlaybackMedia): Boolean
    fun play()
    fun pause()
    fun canSeek(): Boolean
    fun setTime(positionMs: Long)
    fun setVolume(volumePercent: Int)
    fun release()
}

internal sealed interface JvmVlcPlaybackMedia {
    data class Source(val value: String) : JvmVlcPlaybackMedia
    data class Callback(val value: CallbackMedia) : JvmVlcPlaybackMedia
}

private sealed interface JvmVlcRuntimeState {
    data object Initializing : JvmVlcRuntimeState
    data class Ready(val runtime: JvmVlcPlaybackRuntime) : JvmVlcRuntimeState
    data object Unavailable : JvmVlcRuntimeState
    data object Released : JvmVlcRuntimeState
}

private sealed interface JvmVlcLoadDecision {
    data class Ready(val runtime: JvmVlcPlaybackRuntime) : JvmVlcLoadDecision
    data object Initializing : JvmVlcLoadDecision
    data object Unavailable : JvmVlcLoadDecision
    data object Released : JvmVlcLoadDecision
}

private data class PendingVlcLoad(
    val track: Track,
    val playWhenReady: Boolean,
    val startPositionMs: Long,
    val loadToken: PlaybackLoadToken,
)

private data class PendingInitialSeek(
    val runtime: JvmVlcPlaybackRuntime,
    val trackId: String,
    val sourceReference: String,
    val positionMs: Long,
    val loadToken: PlaybackLoadToken,
)

private class LibVlcPlaybackRuntime(
    private val factory: MediaPlayerFactory,
    private val mediaPlayer: MediaPlayer,
    private val nativeLog: NativeLog?,
) : JvmVlcPlaybackRuntime {
    override val nativeLibraryPath: String?
        get() = factory.nativeLibraryPath()

    override fun addLogListener(listener: LogEventListener) {
        nativeLog?.apply {
            setLevel(LogLevel.NOTICE)
            addLogListener(listener)
        }
    }

    override fun removeLogListener(listener: LogEventListener) {
        nativeLog?.removeLogListener(listener)
    }

    override fun addMediaEventListener(listener: MediaEventAdapter) {
        mediaPlayer.events().addMediaEventListener(listener)
    }

    override fun addMediaPlayerEventListener(listener: MediaPlayerEventAdapter) {
        mediaPlayer.events().addMediaPlayerEventListener(listener)
    }

    override fun stop() {
        mediaPlayer.controls().stop()
    }

    override fun start(media: JvmVlcPlaybackMedia): Boolean {
        return prepareAndPlay(media)
    }

    override fun startPaused(media: JvmVlcPlaybackMedia): Boolean {
        return prepareAndPlay(media, VLC_START_PAUSED_OPTION)
    }

    private fun prepareAndPlay(media: JvmVlcPlaybackMedia, vararg options: String): Boolean {
        val prepared = when (media) {
            is JvmVlcPlaybackMedia.Source -> mediaPlayer.media().prepare(media.value, *options)
            is JvmVlcPlaybackMedia.Callback -> mediaPlayer.media().prepare(media.value, *options)
        }
        if (prepared) {
            mediaPlayer.controls().play()
        }
        return prepared
    }

    override fun play() {
        mediaPlayer.controls().play()
    }

    override fun pause() {
        mediaPlayer.controls().pause()
    }

    override fun canSeek(): Boolean = mediaPlayer.status().isSeekable()

    override fun setTime(positionMs: Long) {
        mediaPlayer.controls().setTime(positionMs)
    }

    override fun setVolume(volumePercent: Int) {
        mediaPlayer.audio().setVolume(volumePercent)
    }

    override fun release() {
        mediaPlayer.release()
        nativeLog?.release()
        factory.release()
    }
}

private fun createJvmVlcRuntimeInitializationResult(
    desktopVlcPreferencesStore: DesktopVlcPreferencesStore,
    logger: DiagnosticLogger,
): JvmVlcRuntimeInitializationResult {
    val manualPath = desktopVlcPreferencesStore.desktopVlcManualPath.value
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    val discovery = if (manualPath == null) {
        NativeDiscovery()
    } else {
        createDesktopVlcDiscovery(manualPath)
    }
    val runtime = createJvmVlcRuntime(discovery, logger)
    val autoDetectedPath = if (manualPath == null) {
        runtime?.nativeLibraryPath?.trim()?.takeIf { it.isNotBlank() }
    } else {
        null
    }
    return JvmVlcRuntimeInitializationResult(
        runtime = runtime,
        autoDetectedPath = autoDetectedPath,
        manualPath = manualPath,
        effectivePath = resolveDesktopVlcEffectivePath(
            manualPath = manualPath,
            autoDetectedPath = autoDetectedPath,
        ),
    )
}

private fun createJvmVlcRuntime(
    discovery: NativeDiscovery,
    logger: DiagnosticLogger,
): JvmVlcPlaybackRuntime? {
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
        LibVlcPlaybackRuntime(
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
        append(bytes.stableArtworkBytesHash())
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
private const val KEY_SHOW_DESKTOP_LYRICS = "show_desktop_lyrics"
private const val KEY_DESKTOP_LYRICS_POSITION_X = "desktop_lyrics_position_x"
private const val KEY_DESKTOP_LYRICS_POSITION_Y = "desktop_lyrics_position_y"
private const val KEY_DESKTOP_LYRICS_POSITION_DISPLAY_ID = "desktop_lyrics_position_display_id"
private const val KEY_SHOW_MENU_BAR_LYRICS_CONTROLS = "show_menu_bar_lyrics_controls"
private const val KEY_AUTO_PLAY_ON_STARTUP = "auto_play_on_startup"
private const val KEY_AUTO_OPEN_PLAYER_ON_STARTUP = "auto_open_player_on_startup"
private const val KEY_MACOS_MINIMIZE_WINDOW_ON_CLOSE = "macos_minimize_window_on_close"
private const val KEY_PLAYER_ARTWORK_STYLE = "player_artwork_style"
private const val KEY_LIBRARY_SOURCE_FILTER = "library_source_filter"
private const val KEY_FAVORITES_SOURCE_FILTER = "favorites_source_filter"
private const val KEY_ONLINE_LIBRARY_SOURCE_ID = "online_library_source_id"
private const val KEY_ONLINE_FAVORITES_SOURCE_ID = "online_favorites_source_id"
private const val KEY_ONLINE_PLAYLISTS_SOURCE_ID = "online_playlists_source_id"
private const val KEY_LIBRARY_TRACK_SORT_MODE = "library_track_sort_mode"
private const val KEY_FAVORITES_TRACK_SORT_MODE = "favorites_track_sort_mode"
private const val MAX_RECENT_VLC_LOGS = 8

private val jvmRemoteArtworkDirectory by lazy {
    JvmAppDataDirectory.resolve("artwork").apply { mkdirs() }
}

internal const val SAMBA_LOG_TAG = "Samba"
private const val LOCAL_IMPORT_LOG_TAG = "LocalImport"
private const val VLC_LOG_TAG = "VLC"
private val DESKTOP_VLC_UNAVAILABLE_TEXT = uiText(Res.string.playback_vlc_unavailable)
private val DESKTOP_MEDIA_FAILED_TEXT = uiText(Res.string.playback_media_failed)
private val DESKTOP_VLC_INITIALIZING_TEXT = uiText(Res.string.playback_vlc_initializing)
private val DESKTOP_VLC_INITIALIZING_MESSAGE: String get() = (DESKTOP_VLC_INITIALIZING_TEXT).diagnosticMessage()
private const val VLC_START_PAUSED_OPTION = "start-paused"

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

internal fun isJvmRemotePlaybackFallbackAllowed(errorDetail: String): Boolean {
    return isRemoteSourceAddressFallbackAllowed(IllegalStateException(errorDetail))
}
