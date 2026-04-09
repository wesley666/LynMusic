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
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.math.roundToInt
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
import top.iwesley.lyn.music.core.model.DEFAULT_SAMBA_PORT
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.NavidromeSourceDraft
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.core.model.PlaybackPreferencesStore
import top.iwesley.lyn.music.core.model.SambaCachePreferencesStore
import top.iwesley.lyn.music.core.model.ThemePreferencesStore
import top.iwesley.lyn.music.core.model.AppThemeId
import top.iwesley.lyn.music.core.model.AppThemeTextPalette
import top.iwesley.lyn.music.core.model.AppThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.AppThemeTokens
import top.iwesley.lyn.music.core.model.defaultCustomThemeTokens
import top.iwesley.lyn.music.core.model.defaultThemeTextPalettePreferences
import top.iwesley.lyn.music.core.model.inferArtworkFileExtension
import top.iwesley.lyn.music.core.model.withThemePalette
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.UnsupportedAudioTagEditorPlatformService
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
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import top.iwesley.lyn.music.data.repository.PlayerRuntimeServices
import top.iwesley.lyn.music.domain.resolveNavidromeStreamUrl
import top.iwesley.lyn.music.domain.scanNavidromeLibrary
import top.iwesley.lyn.music.feature.library.LibrarySourceFilter
import top.iwesley.lyn.music.feature.library.LibrarySourceFilterPreferencesStore
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
    val database = buildLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(
            name = File(File(System.getProperty("user.home")), ".lynmusic/lynmusic.db").apply {
                parentFile?.mkdirs()
            }.absolutePath,
        ),
    )
    val logger = ConsoleDiagnosticLogger(enabled = true, label = "Desktop")
    val secureStore = createJvmSecureCredentialStore(logger)
    val appPreferencesStore = JvmAppPreferencesStore()
    val playbackGateway = JvmPlaybackGateway(database, secureStore, appPreferencesStore, logger)
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
            librarySourceFilterPreferencesStore = appPreferencesStore,
            lyricsHttpClient = navidromeHttpClient,
            artworkCacheStore = createJvmArtworkCacheStore(),
            appStorageGateway = createJvmAppStorageGateway(),
            deviceInfoGateway = createJvmDeviceInfoGateway(),
            audioTagGateway = JvmAudioTagGateway(
                database = database,
                secureCredentialStore = secureStore,
                logger = logger,
            ),
            audioTagEditorPlatformService = JvmAudioTagEditorPlatformService(),
            logger = logger,
        ),
    )
    return buildPlayerAppComponent(
        sharedGraph = sharedGraph,
        playerRuntimeServices = PlayerRuntimeServices(
            playbackGateway = playbackGateway,
            playbackPreferencesStore = appPreferencesStore,
            lyricsSharePlatformService = JvmLyricsSharePlatformService(),
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

private class JvmAppPreferencesStore : PlaybackPreferencesStore, SambaCachePreferencesStore, ThemePreferencesStore, LibrarySourceFilterPreferencesStore {
    private val settingsFile = File(File(System.getProperty("user.home")), ".lynmusic/settings.properties").apply {
        parentFile?.mkdirs()
    }
    private val mutableUseSambaCache = MutableStateFlow(readUseSambaCache())
    private val mutableLibrarySourceFilter = MutableStateFlow(readLibrarySourceFilter(KEY_LIBRARY_SOURCE_FILTER))
    private val mutableFavoritesSourceFilter = MutableStateFlow(readLibrarySourceFilter(KEY_FAVORITES_SOURCE_FILTER))
    private val mutableSelectedTheme = MutableStateFlow(readSelectedTheme())
    private val mutableCustomThemeTokens = MutableStateFlow(readCustomThemeTokens())
    private val mutableTextPalettePreferences = MutableStateFlow(readTextPalettePreferences())

    override val useSambaCache: StateFlow<Boolean> = mutableUseSambaCache.asStateFlow()
    override val selectedTheme: StateFlow<AppThemeId> = mutableSelectedTheme.asStateFlow()
    override val customThemeTokens: StateFlow<AppThemeTokens> = mutableCustomThemeTokens.asStateFlow()
    override val textPalettePreferences: StateFlow<AppThemeTextPalettePreferences> = mutableTextPalettePreferences.asStateFlow()
    override val librarySourceFilter: StateFlow<LibrarySourceFilter> = mutableLibrarySourceFilter.asStateFlow()
    override val favoritesSourceFilter: StateFlow<LibrarySourceFilter> = mutableFavoritesSourceFilter.asStateFlow()

    override suspend fun setUseSambaCache(enabled: Boolean) {
        val properties = loadProperties()
        properties.setProperty(KEY_USE_SAMBA_CACHE, enabled.toString())
        persistProperties(properties)
        mutableUseSambaCache.value = enabled
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

    private fun readUseSambaCache(): Boolean {
        return loadProperties().getProperty(KEY_USE_SAMBA_CACHE)?.toBooleanStrictOrNull() ?: true
    }

    private fun readLibrarySourceFilter(key: String): LibrarySourceFilter {
        val name = loadProperties().getProperty(key)
        return LibrarySourceFilter.entries.firstOrNull { it.name == name } ?: LibrarySourceFilter.ALL
    }

    private fun readSelectedTheme(): AppThemeId {
        val name = loadProperties().getProperty(KEY_SELECTED_THEME)
        return AppThemeId.entries.firstOrNull { it.name == name } ?: AppThemeId.Classic
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

private class JvmAudioTagEditorPlatformService : AudioTagEditorPlatformService {
    override suspend fun pickArtworkBytes(): Result<ByteArray?> {
        return runCatching {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.FILES_ONLY
                isAcceptAllFileFilterUsed = true
                fileFilter = FileNameExtensionFilter(
                    "图片文件",
                    "jpg",
                    "jpeg",
                    "png",
                    "webp",
                    "bmp",
                    "gif",
                )
            }
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
                null
            } else {
                chooser.selectedFile?.toPath()?.let(Files::readAllBytes)
            }
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

private suspend fun resolveJvmSambaTagReadTarget(
    database: LynMusicDatabase,
    secureCredentialStore: SecureCredentialStore,
    track: Track,
): JvmSambaTagReadTarget? {
    val samba = parseSambaLocator(track.mediaLocator) ?: return null
    val source = database.importSourceDao().getById(samba.first) ?: error("Samba source missing")
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
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        val result = chooser.showOpenDialog(null)
        if (result != JFileChooser.APPROVE_OPTION) return null
        val file = chooser.selectedFile ?: return null
        return LocalFolderSelection(
            label = file.name.ifBlank { file.absolutePath },
            persistentReference = file.absolutePath,
        )
    }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun scanLocalFolder(selection: LocalFolderSelection, sourceId: String): ImportScanReport {
        val root = Path.of(selection.persistentReference)
        if (!Files.exists(root)) {
            error("Folder does not exist: ${selection.persistentReference}")
        }
        val tracks = Files.walk(root).use { stream ->
            stream.filter { path -> path.isRegularFile() && isSupportedAudio(path.name) }
                .map { path ->
                    val relativePath = root.relativize(path).invariantSeparatorsPathString
                    ImportedCandidateFactory.fromPath(path, relativePath, logger)
                }
                .toList()
        }
        return ImportScanReport(tracks)
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
                collectSambaTracks(share, baseDirectory, "", sourceId, tracks)
                ImportScanReport(tracks)
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

    override suspend fun scanWebDav(draft: WebDavSourceDraft, sourceId: String): ImportScanReport {
        return scanJvmWebDav(draft, sourceId, logger)
    }

    override suspend fun scanNavidrome(draft: NavidromeSourceDraft, sourceId: String): ImportScanReport {
        return scanNavidromeLibrary(draft, sourceId, navidromeHttpClient, logger)
    }

    private fun collectSambaTracks(
        share: DiskShare,
        baseDirectory: String,
        relativeDirectory: String,
        sourceId: String,
        sink: MutableList<top.iwesley.lyn.music.core.model.ImportedTrackCandidate>,
    ) {
        val listPath = joinSegments(baseDirectory, relativeDirectory)
        share.list(listPath).forEach { fileInfo ->
            val name = fileInfo.fileName
            if (name == "." || name == "..") return@forEach
            val childRelative = joinSegments(relativeDirectory, name)
            val childPath = joinSegments(baseDirectory, childRelative)
            val isDirectory = share.folderExists(childPath)
            if (isDirectory) {
                collectSambaTracks(share, baseDirectory, childRelative, sourceId, sink)
            } else if (isSupportedAudio(name)) {
                val sizeBytes = runCatching { fileInfo.endOfFile }.getOrDefault(0L)
                sink += runCatching {
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
                }.getOrElse {
                    ImportedCandidateFactory.fromRemotePath(
                        sourceId = sourceId,
                        relativePath = childRelative,
                        sizeBytes = sizeBytes,
                    )
                }
            }
        }
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
    private val logger: DiagnosticLogger,
) : PlaybackGateway {
    private val mutableState = MutableStateFlow(PlaybackGatewayState())
    private val scope = CoroutineScope(Dispatchers.IO)
    private val discovery = NativeDiscovery().apply { discover() }
    private val factory = MediaPlayerFactory()
    private val nativeLog: NativeLog? = runCatching { factory.application().newLog() }
        .onFailure { throwable ->
            logger.warn(VLC_LOG_TAG) {
                "native-log-init-failed message=${throwable.message.orEmpty()}"
            }
        }
        .getOrNull()
    private val mediaPlayer = factory.mediaPlayers().newMediaPlayer()
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
        nativeLog?.apply {
            setLevel(LogLevel.NOTICE)
            addLogListener(nativeLogListener)
        }
        mediaPlayer.events().addMediaEventListener(object  : MediaEventAdapter() {
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
        mediaPlayer.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
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
                        metadataTitle = sanitizeJvmVlcMetadataTitle(metaData.value(Meta.TITLE)) ?: it.metadataTitle,
                        metadataArtistName = metaData.value(Meta.ARTIST)
                            .ifBlank { metaData.value(Meta.ALBUM_ARTIST) }
                            .ifBlank { it.metadataArtistName },
                        metadataAlbumTitle = metaData.value(Meta.ALBUM)
                            .ifBlank { it.metadataAlbumTitle },
                        errorMessage = null,
                    )
                }
                logger.info(VLC_LOG_TAG) {
                    buildVlcMetadataLogMessage(
                        track = track,
                        playbackTarget = playbackTarget,
                        sourceReference = currentSourceReference,
                        parseAccepted = parseAccepted,
                        parseStatus = parseStatus.name,
                        durationMs = info.duration(),
                        metaData = metaData,
                    )
                }
            }

            override fun playing(mediaPlayer: MediaPlayer?) {
                mutableState.update {
                    it.copy(isPlaying = true, errorMessage = null)
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
                    it.copy(durationMs = newLength.coerceAtLeast(0L))
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
                        completionCount = it.completionCount + 1,
                    )
                }
            }

            override fun error(mediaPlayer: MediaPlayer?) {
                logger.error(VLC_LOG_TAG) {
                    "playback-error target=${currentPlaybackTarget.orEmpty()} source=${currentSourceReference.orEmpty()} recentLogs=${recentVlcLogSummary()}"
                }
                mutableState.update {
                    it.copy(errorMessage = "桌面播放器无法播放当前媒体。")
                }
            }
        })
    }

    override suspend fun load(track: Track, playWhenReady: Boolean, startPositionMs: Long) {
        runCatching { mediaPlayer.controls().stop() }
        currentCallbackMedia = null
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
        val actualPlaybackSource = when {
            sambaTarget != null -> sambaTarget.sourceReference
            webDavTarget != null -> webDavTarget.requestUrl
            else -> resolveLocator(track.mediaLocator)
        }
        val sourceReference = when {
            parseNavidromeSongLocator(track.mediaLocator) != null -> track.mediaLocator
            else -> actualPlaybackSource
        }
        val playbackTarget = when {
            webDavTarget != null -> "webdav-callback://${track.id}"
            sambaTarget != null -> buildJvmSambaPlaybackTarget(track.id)
            else -> sourceReference
        }
        currentPlaybackTarget = playbackTarget
        currentSourceReference = sourceReference
        currentTrackForMetadata = track
        currentCallbackMedia = webDavTarget?.media ?: sambaTarget?.media
        mutableState.update {
            it.copy(
                isPlaying = playWhenReady,
                positionMs = 0L,
                durationMs = 0L,
                metadataTitle = null,
                metadataArtistName = null,
                metadataAlbumTitle = null,
                errorMessage = null,
            )
        }
        val started = if (playWhenReady) {
            if (webDavTarget != null) {
                mediaPlayer.media().start(webDavTarget.media)
            } else if (sambaTarget != null) {
                mediaPlayer.media().start(sambaTarget.media)
            } else {
                mediaPlayer.media().start(actualPlaybackSource)
            }
        } else {
            if (webDavTarget != null) {
                mediaPlayer.media().startPaused(webDavTarget.media)
            } else if (sambaTarget != null) {
                mediaPlayer.media().startPaused(sambaTarget.media)
            } else {
                mediaPlayer.media().startPaused(actualPlaybackSource)
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
                    )
                }
            }
        }
        if (startPositionMs > 0) {
            mediaPlayer.controls().setTime(startPositionMs)
        }
    }

    override suspend fun play() {
        mediaPlayer.controls().play()
    }

    override suspend fun pause() {
        mediaPlayer.controls().pause()
    }

    override suspend fun seekTo(positionMs: Long) {
        mediaPlayer.controls().setTime(positionMs)
    }

    override suspend fun setVolume(volume: Float) {
        val normalized = volume.coerceIn(0f, 1f)
        mediaPlayer.audio().setVolume((normalized * 100).roundToInt())
        mutableState.update { it.copy(volume = normalized) }
    }

    override suspend fun release() {
        currentTrackForMetadata = null
        currentCallbackMedia = null
        currentPlaybackTarget = null
        currentSourceReference = null
        mediaPlayer.release()
        nativeLog?.removeLogListener(nativeLogListener)
        nativeLog?.release()
        factory.release()
        scope.cancel()
    }

    private suspend fun resolveLocator(locator: String): String {
        resolveNavidromeStreamUrl(database, secureCredentialStore, locator)?.let { return it }
        val samba = parseSambaLocator(locator) ?: return locator
        val source = database.importSourceDao().getById(samba.first) ?: error("Samba source missing")
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
private const val KEY_LIBRARY_SOURCE_FILTER = "library_source_filter"
private const val KEY_FAVORITES_SOURCE_FILTER = "favorites_source_filter"
private const val MAX_RECENT_VLC_LOGS = 8

private fun buildSambaCacheFileName(sourceId: String, remotePath: String): String {
    val sanitized = remotePath.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return "$sourceId-$sanitized"
}

private val jvmRemoteArtworkDirectory = File(File(System.getProperty("user.home")), ".lynmusic/artwork").apply {
    mkdirs()
}

internal const val SAMBA_LOG_TAG = "Samba"
private const val VLC_LOG_TAG = "VLC"

private fun isSupportedAudio(fileName: String): Boolean {
    return fileName.substringAfterLast('.', "").lowercase() in setOf("mp3", "m4a", "aac", "wav", "flac", "ape")
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

private val INTERNAL_VLC_TITLE_PREFIXES = listOf(
    "imem://",
    "fd://",
)
