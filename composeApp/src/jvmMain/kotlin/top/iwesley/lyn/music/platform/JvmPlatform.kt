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
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.Properties
import javax.swing.JFileChooser
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
import top.iwesley.lyn.music.buildLynMusicAppComponent
import top.iwesley.lyn.music.core.model.ArtworkLoader
import top.iwesley.lyn.music.core.model.ConsoleDiagnosticLogger
import top.iwesley.lyn.music.core.model.DEFAULT_SAMBA_PORT
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.ImportScanReport
import top.iwesley.lyn.music.core.model.ImportSourceGateway
import top.iwesley.lyn.music.core.model.LocalFolderSelection
import top.iwesley.lyn.music.core.model.LyricsHttpClient
import top.iwesley.lyn.music.core.model.LyricsHttpResponse
import top.iwesley.lyn.music.core.model.LyricsRequest
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.core.model.PlaybackPreferencesStore
import top.iwesley.lyn.music.core.model.SambaSourceDraft
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WebDavSourceDraft
import top.iwesley.lyn.music.core.model.buildSambaLocator
import top.iwesley.lyn.music.core.model.debug
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.formatSambaEndpoint
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.joinSambaPath
import top.iwesley.lyn.music.core.model.normalizeSambaPath
import top.iwesley.lyn.music.core.model.parseSambaLocator
import top.iwesley.lyn.music.core.model.parseSambaPath
import top.iwesley.lyn.music.core.model.parseWebDavLocator
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
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
    val playbackPreferencesStore = JvmPlaybackPreferencesStore()
    val playbackGateway = JvmPlaybackGateway(database, secureStore, playbackPreferencesStore, logger)
    return buildLynMusicAppComponent(
        platform = PlatformDescriptor(
            name = "Desktop",
            capabilities = PlatformCapabilities(
                supportsLocalFolderImport = true,
                supportsSambaImport = true,
                supportsWebDavImport = true,
                supportsSystemMediaControls = false,
            ),
        ),
        database = database,
        importSourceGateway = JvmImportSourceGateway(logger),
        playbackGateway = playbackGateway,
        playbackPreferencesStore = playbackPreferencesStore,
        secureCredentialStore = secureStore,
        lyricsHttpClient = JvmLyricsHttpClient(),
        artworkCacheStore = createJvmArtworkCacheStore(),
        logger = logger,
        artworkLoader = object : ArtworkLoader {
            override suspend fun resolve(track: Track): String? = track.artworkLocator
        },
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

private class JvmPlaybackPreferencesStore : PlaybackPreferencesStore {
    private val settingsFile = File(File(System.getProperty("user.home")), ".lynmusic/settings.properties").apply {
        parentFile?.mkdirs()
    }
    private val mutableUseSambaCache = MutableStateFlow(readUseSambaCache())

    override val useSambaCache: StateFlow<Boolean> = mutableUseSambaCache.asStateFlow()

    override suspend fun setUseSambaCache(enabled: Boolean) {
        val properties = loadProperties()
        properties.setProperty(KEY_USE_SAMBA_CACHE, enabled.toString())
        settingsFile.outputStream().use { output ->
            properties.store(output, "LynMusic settings")
        }
        mutableUseSambaCache.value = enabled
    }

    private fun readUseSambaCache(): Boolean {
        return loadProperties().getProperty(KEY_USE_SAMBA_CACHE)?.toBooleanStrictOrNull() ?: true
    }

    private fun loadProperties(): Properties {
        val properties = Properties()
        if (settingsFile.exists()) {
            settingsFile.inputStream().use { input -> properties.load(input) }
        }
        return properties
    }
}

private class JvmImportSourceGateway(
    private val logger: DiagnosticLogger,
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
                sink += ImportedCandidateFactory.fromRemotePath(sourceId, childRelative)
            }
        }
    }
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
        val sourceReference = when {
            sambaTarget != null -> sambaTarget.sourceReference
            webDavTarget != null -> webDavTarget.requestUrl
            else -> resolveLocator(track.mediaLocator)
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
                mediaPlayer.media().start(sourceReference)
            }
        } else {
            if (webDavTarget != null) {
                mediaPlayer.media().startPaused(webDavTarget.media)
            } else if (sambaTarget != null) {
                mediaPlayer.media().startPaused(sambaTarget.media)
            } else {
                mediaPlayer.media().startPaused(sourceReference)
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
        val samba = parseSambaLocator(locator) ?: return locator
        val source = database.importSourceDao().getById(samba.first) ?: error("Samba source missing")
        val storedPort = source.shareName?.toIntOrNull()
        val storedPath = when {
            storedPort != null -> normalizeSambaPath(source.directoryPath)
            source.shareName.isNullOrBlank() -> normalizeSambaPath(source.directoryPath)
            else -> normalizeSambaPath(joinSambaPath(source.shareName, source.directoryPath.orEmpty()))
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

    fun fromRemotePath(sourceId: String, relativePath: String): top.iwesley.lyn.music.core.model.ImportedTrackCandidate {
        val name = relativePath.substringAfterLast('/').substringBeforeLast('.')
        return top.iwesley.lyn.music.core.model.ImportedTrackCandidate(
            title = name,
            mediaLocator = buildSambaLocator(sourceId, relativePath),
            relativePath = relativePath,
        )
    }
}

private fun joinSegments(left: String, right: String): String {
    return listOf(left.trim('/'), right.trim('/'))
        .filter { it.isNotBlank() }
        .joinToString("/")
}

private const val KEY_USE_SAMBA_CACHE = "use_samba_cache"
private const val MAX_RECENT_VLC_LOGS = 8

private fun buildSambaCacheFileName(sourceId: String, remotePath: String): String {
    val sanitized = remotePath.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return "$sourceId-$sanitized"
}

internal const val SAMBA_LOG_TAG = "Samba"
private const val VLC_LOG_TAG = "VLC"

private fun isSupportedAudio(fileName: String): Boolean {
    return fileName.substringAfterLast('.', "").lowercase() in setOf("mp3", "m4a", "aac", "wav")
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
