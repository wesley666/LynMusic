package top.iwesley.lyn.music.platform

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
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
import kotlinx.coroutines.suspendCancellableCoroutine
import top.iwesley.lyn.music.buildLynMusicAppComponent
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
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlaybackGateway
import top.iwesley.lyn.music.core.model.PlaybackGatewayState
import top.iwesley.lyn.music.core.model.PlaybackPreferencesStore
import top.iwesley.lyn.music.core.model.RequestMethod
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
import top.iwesley.lyn.music.core.model.parseNavidromeSongLocator
import top.iwesley.lyn.music.core.model.parseSambaLocator
import top.iwesley.lyn.music.core.model.parseSambaPath
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase
import top.iwesley.lyn.music.domain.resolveNavidromeStreamUrl
import top.iwesley.lyn.music.domain.scanNavidromeLibrary
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun createAndroidAppComponent(activity: ComponentActivity): top.iwesley.lyn.music.LynMusicAppComponent {
    val database = buildLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(
            context = activity.applicationContext,
            name = activity.applicationContext.getDatabasePath("lynmusic.db").absolutePath,
        ),
    )
    val secureStore = AndroidCredentialStore(activity.applicationContext)
    val playbackPreferencesStore = AndroidPlaybackPreferencesStore(activity.applicationContext)
    val logger = ConsoleDiagnosticLogger(enabled = activity.applicationContext.isDebuggableApp(), label = "Android")
    val navidromeHttpClient = AndroidLyricsHttpClient()
    return buildLynMusicAppComponent(
        platform = PlatformDescriptor(
            name = "Android",
            capabilities = PlatformCapabilities(
                supportsLocalFolderImport = true,
                supportsSambaImport = true,
                supportsWebDavImport = true,
                supportsNavidromeImport = true,
                supportsSystemMediaControls = true,
            ),
        ),
        database = database,
        importSourceGateway = AndroidImportSourceGateway(activity, logger, navidromeHttpClient),
        playbackGateway = AndroidPlaybackGateway(activity.applicationContext, database, secureStore, playbackPreferencesStore, logger),
        playbackPreferencesStore = playbackPreferencesStore,
        secureCredentialStore = secureStore,
        lyricsHttpClient = navidromeHttpClient,
        artworkCacheStore = createAndroidArtworkCacheStore(activity.applicationContext),
        logger = logger,
    )
}

private class AndroidLyricsHttpClient : LyricsHttpClient {
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

private class AndroidCredentialStore(
    context: Context,
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
        }.getOrElse {
            preferences.edit().remove(key).apply()
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
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        return cipher.doFinal(encrypted).decodeToString()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val existing = keyStore.getKey(CREDENTIAL_KEY_ALIAS, null) as? SecretKey
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

private class AndroidPlaybackPreferencesStore(
    context: Context,
) : PlaybackPreferencesStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("lynmusic.settings", Context.MODE_PRIVATE)
    private val mutableUseSambaCache = MutableStateFlow(
        preferences.getBoolean(KEY_USE_SAMBA_CACHE, true),
    )

    override val useSambaCache: StateFlow<Boolean> = mutableUseSambaCache.asStateFlow()

    override suspend fun setUseSambaCache(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_USE_SAMBA_CACHE, enabled).apply()
        mutableUseSambaCache.value = enabled
    }
}

private fun Context.isDebuggableApp(): Boolean {
    return applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}

private class AndroidImportSourceGateway(
    private val activity: ComponentActivity,
    private val logger: DiagnosticLogger,
    private val navidromeHttpClient: LyricsHttpClient,
) : ImportSourceGateway {
    private var folderContinuation: (LocalFolderSelection?) -> Unit = {}

    private val picker = activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            activity.contentResolver.takePersistableUriPermission(
                uri,
                IntentFlags.ReadWriteUriPermission,
            )
        }
        folderContinuation(
            uri?.let {
                LocalFolderSelection(
                    label = DocumentFile.fromTreeUri(activity, uri)?.name ?: "本地音乐",
                    persistentReference = it.toString(),
                )
            },
        )
    }

    override suspend fun pickLocalFolder(): LocalFolderSelection? {
        return suspendCancellableCoroutine { continuation ->
            folderContinuation = { selection -> continuation.resume(selection) }
            picker.launch(null)
        }
    }

    override suspend fun scanLocalFolder(selection: LocalFolderSelection, sourceId: String): ImportScanReport {
        val treeUri = Uri.parse(selection.persistentReference)
        val root = DocumentFile.fromTreeUri(activity, treeUri) ?: error("Cannot open tree uri: $treeUri")
        val tracks = mutableListOf<top.iwesley.lyn.music.core.model.ImportedTrackCandidate>()
        walkDocumentTree(root, "", tracks)
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
                val tracks = mutableListOf<top.iwesley.lyn.music.core.model.ImportedTrackCandidate>()
                collectSambaTracks(share, sambaPath.directoryPath, "", sourceId, tracks)
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
        return scanAndroidWebDav(draft, sourceId, logger)
    }

    override suspend fun scanNavidrome(draft: NavidromeSourceDraft, sourceId: String): ImportScanReport {
        return scanNavidromeLibrary(draft, sourceId, navidromeHttpClient, logger)
    }

    private fun walkDocumentTree(
        folder: DocumentFile,
        relativeDirectory: String,
        sink: MutableList<top.iwesley.lyn.music.core.model.ImportedTrackCandidate>,
    ) {
        folder.listFiles().forEach { file ->
            val fileName = file.name ?: return@forEach
            val nextRelative = listOf(relativeDirectory, fileName).filter { it.isNotBlank() }.joinToString("/")
            when {
                file.isDirectory -> walkDocumentTree(file, nextRelative, sink)
                file.isFile && isSupportedAudio(fileName) -> sink += readAndroidCandidate(file, nextRelative)
            }
        }
    }

    private fun readAndroidCandidate(
        file: DocumentFile,
        relativePath: String,
    ): top.iwesley.lyn.music.core.model.ImportedTrackCandidate {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(activity, file.uri)
            top.iwesley.lyn.music.core.model.ImportedTrackCandidate(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: file.name?.substringBeforeLast('.').orEmpty(),
                artistName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                albumTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')
                    ?.toIntOrNull(),
                discNumber = runCatching {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                        ?.substringBefore('/')
                        ?.toIntOrNull()
                }.getOrNull(),
                mediaLocator = file.uri.toString(),
                relativePath = relativePath,
                artworkLocator = retriever.embeddedPicture
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { bytes -> storeAndroidArtwork(relativePath, bytes) },
                sizeBytes = file.length(),
                modifiedAt = file.lastModified(),
            )
        }.onSuccess { candidate ->
            logger.debug(METADATA_LOG_TAG) {
                buildMetadataLogMessage(
                    relativePath = relativePath,
                    candidate = candidate,
                )
            }
        }.getOrElse {
            logger.warn(METADATA_LOG_TAG) {
                "parse-fallback path=$relativePath reason=${it.message ?: it::class.simpleName} title=${file.name?.substringBeforeLast('.') ?: "未知曲目"}"
            }
            top.iwesley.lyn.music.core.model.ImportedTrackCandidate(
                title = file.name?.substringBeforeLast('.') ?: "未知曲目",
                mediaLocator = file.uri.toString(),
                relativePath = relativePath,
                sizeBytes = file.length(),
                modifiedAt = file.lastModified(),
            )
        }.also {
            runCatching { retriever.release() }
        }
    }

    private fun storeAndroidArtwork(relativePath: String, bytes: ByteArray): String {
        val artworkDirectory = File(activity.cacheDir, "artwork").apply {
            mkdirs()
        }
        val fileName = buildString {
            append(relativePath.hashCode().toUInt().toString(16))
            append(".img")
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
    ) {
        val listPath = joinSegments(baseDirectory, relativeDirectory)
        share.list(listPath).forEach { info ->
            val name = info.fileName
            if (name == "." || name == "..") return@forEach
            val childRelative = joinSegments(relativeDirectory, name)
            val childPath = joinSegments(baseDirectory, childRelative)
            val isDirectory = share.folderExists(childPath)
            if (isDirectory) {
                collectSambaTracks(share, baseDirectory, childRelative, sourceId, sink)
            } else if (isSupportedAudio(name)) {
                sink += top.iwesley.lyn.music.core.model.ImportedTrackCandidate(
                    title = name.substringBeforeLast('.'),
                    mediaLocator = buildSambaLocator(sourceId, childRelative),
                    relativePath = childRelative,
                    sizeBytes = runCatching { info.endOfFile }.getOrDefault(0L),
                )
            }
        }
    }
}

private class AndroidPlaybackGateway(
    private val context: Context,
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val playbackPreferencesStore: PlaybackPreferencesStore,
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
                mutableState.update { it.copy(errorMessage = error.message ?: "播放器出错") }
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

    override suspend fun load(track: Track, playWhenReady: Boolean, startPositionMs: Long) {
        val webDavTarget = resolveAndroidWebDavPlaybackTarget(
            database = database,
            secureCredentialStore = secureCredentialStore,
            locator = track.mediaLocator,
            logger = logger,
        )
        val navidrome = if (webDavTarget == null) parseNavidromeSongLocator(track.mediaLocator) else null
        val resolvedUri = if (webDavTarget == null) resolveLocator(track.mediaLocator) else null
        onPlayerThread {
            if (webDavTarget != null) {
                currentRemoteLogTag = "WebDav"
                currentRemoteLabel = webDavTarget.requestUrl
                player.setMediaSource(webDavTarget.mediaSource)
            } else {
                currentRemoteLogTag = if (navidrome != null) "Navidrome" else null
                currentRemoteLabel = if (navidrome != null) track.mediaLocator else null
                player.setMediaItem(MediaItem.fromUri(checkNotNull(resolvedUri)))
            }
            player.prepare()
            player.seekTo(startPositionMs)
            player.playWhenReady = playWhenReady
        }
        ensureProgressTicker()
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

    private suspend fun resolveLocator(locator: String): Uri {
        resolveNavidromeStreamUrl(database, secureCredentialStore, locator)?.let { return Uri.parse(it) }
        val samba = parseSambaLocator(locator) ?: return Uri.parse(locator)
        if (!playbackPreferencesStore.useSambaCache.value) {
            logger.info(SAMBA_LOG_TAG) {
                "direct-link-requested source=${samba.first} but Android playback still falls back to cache"
            }
        }
        val source = database.importSourceDao().getById(samba.first) ?: error("Missing SMB source")
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
        val cacheFile = File(context.cacheDir, "${samba.first}-${samba.second.substringAfterLast('/')}").apply {
            parentFile?.mkdirs()
        }
        val remotePath = joinSegments(sambaPath.directoryPath, samba.second)
        if (cacheFile.exists()) {
            logger.debug(SAMBA_LOG_TAG) {
                "cache-hit source=${samba.first} endpoint=$endpoint remotePath=$remotePath cache=${cacheFile.absolutePath}"
            }
            return Uri.fromFile(cacheFile)
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
            logger.error(SAMBA_LOG_TAG, throwable) {
                "stream-fetch-failed source=${samba.first} endpoint=$endpoint remotePath=$remotePath elapsedMs=${System.currentTimeMillis() - startedAt}"
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
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = if (duration > 0) duration else it.durationMs,
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

private fun joinSegments(left: String, right: String): String {
    return listOf(left.trim('/'), right.trim('/'))
        .filter { it.isNotBlank() }
        .joinToString("/")
}

private const val SAMBA_LOG_TAG = "Samba"
private const val METADATA_LOG_TAG = "Metadata"
private const val KEY_USE_SAMBA_CACHE = "use_samba_cache"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val CREDENTIAL_KEY_ALIAS = "lynmusic.credentials.master"
private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
private const val ENCRYPTED_VALUE_PREFIX = "enc:v1:"
private const val GCM_IV_LENGTH_BYTES = 12
private const val GCM_TAG_LENGTH_BITS = 128

private fun isSupportedAudio(fileName: String): Boolean {
    return fileName.substringAfterLast('.', "").lowercase() in setOf("mp3", "m4a", "aac", "wav", "flac")
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
        append(" lyrics=android-unavailable")
    }
}
