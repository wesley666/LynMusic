package top.iwesley.lyn.music.platform

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.cast.CastMediaUrlResolver
import top.iwesley.lyn.music.cast.CastProxySession
import top.iwesley.lyn.music.cast.inferCastMimeType
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.SecureCredentialStore
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.parseSambaLocator
import top.iwesley.lyn.music.core.model.parseWebDavLocator
import top.iwesley.lyn.music.data.db.LynMusicDatabase

internal class AndroidCastMediaUrlResolver(
    context: Context,
    private val database: LynMusicDatabase,
    private val secureCredentialStore: SecureCredentialStore,
    private val logger: DiagnosticLogger,
) : CastMediaUrlResolver {
    private val appContext = context.applicationContext
    private val server: AndroidCastProxyServer
    private val registry: AndroidCastProxySessionRegistry

    init {
        lateinit var serverRef: AndroidCastProxyServer
        registry = AndroidCastProxySessionRegistry(
            onEmpty = {
                serverRef.stopIfIdle()
                AndroidCastProxyForegroundService.stop(appContext)
            },
        )
        server = AndroidCastProxyServer(
            registry = registry,
            logger = logger,
        )
        serverRef = server
    }

    override suspend fun resolve(
        track: Track,
        snapshot: PlaybackSnapshot,
    ): Result<CastProxySession> {
        return runCatching {
            val resource = resolveResource(track)
                ?: error("当前歌曲暂不支持投屏。")
            AndroidCastProxyForegroundService.start(appContext)
            val port = runCatching { server.ensureStarted() }.getOrElse { throwable ->
                AndroidCastProxyForegroundService.stop(appContext)
                resource.close()
                throw throwable
            }
            val host = resolveLocalIpv4Address(appContext)
                ?: run {
                    resource.close()
                    server.stopIfIdle()
                    AndroidCastProxyForegroundService.stop(appContext)
                    error("无法获取手机局域网地址。")
                }
            val entry = registry.register(resource)
            val uri = "http://$host:$port/cast/stream/${entry.token}"
            logger.info(CAST_PROXY_LOG_TAG) {
                "session-created track=${track.id} token=${entry.token} mime=${resource.mimeType} length=${resource.length ?: -1L}"
            }
            AndroidCastProxySession(
                registry = registry,
                token = entry.token,
                uri = uri,
                mimeType = resource.mimeType,
                durationMs = snapshot.durationMs.takeIf { it > 0L } ?: track.durationMs,
            )
        }
    }

    override suspend fun release() {
        registry.clear()
        server.stop()
        AndroidCastProxyForegroundService.stop(appContext)
    }

    private suspend fun resolveResource(track: Track): AndroidCastProxyResource? {
        val mimeType = inferCastMimeType(track.relativePath.ifBlank { track.mediaLocator })
        resolveAndroidOfflinePlaybackTarget(database, track)?.let { target ->
            return LocalCastProxyResource.fromFile(
                context = appContext,
                file = target.file,
                mimeType = mimeType,
            )
        }
        resolveAndroidLocalTrackFile(track.mediaLocator)?.let { file ->
            return LocalCastProxyResource.fromFile(
                context = appContext,
                file = file,
                mimeType = mimeType,
            )
        }
        val localUri = resolveAndroidLocalTrackUri(track.mediaLocator)
        if (localUri != null && localUri.scheme.equals("content", ignoreCase = true)) {
            return LocalCastProxyResource.fromUri(
                context = appContext,
                uri = localUri,
                mimeType = mimeType,
            )
        }
        if (parseWebDavLocator(track.mediaLocator) != null) {
            return WebDavCastProxyResource.create(
                database = database,
                secureCredentialStore = secureCredentialStore,
                track = track,
                mimeType = mimeType,
                logger = logger,
            )
        }
        if (parseSambaLocator(track.mediaLocator) != null) {
            return SambaCastProxyResource.create(
                database = database,
                secureCredentialStore = secureCredentialStore,
                track = track,
                mimeType = mimeType,
                logger = logger,
            )
        }
        return null
    }
}

private class AndroidCastProxySession(
    private val registry: AndroidCastProxySessionRegistry,
    private val token: String,
    override val uri: String,
    override val mimeType: String,
    override val durationMs: Long,
) : CastProxySession {
    private var closed = false

    override suspend fun close() {
        if (closed) return
        closed = true
        registry.remove(token)
    }
}

private suspend fun resolveLocalIpv4Address(context: Context): String? = withContext(Dispatchers.IO) {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val activeAddress = connectivityManager
        ?.activeNetwork
        ?.let(connectivityManager::getLinkProperties)
        ?.linkAddresses
        ?.asSequence()
        ?.map { it.address }
        ?.filterIsInstance<Inet4Address>()
        ?.firstOrNull { !it.isLoopbackAddress && !it.isAnyLocalAddress }
        ?.hostAddress
    if (!activeAddress.isNullOrBlank()) {
        return@withContext activeAddress
    }
    NetworkInterface.getNetworkInterfaces()
        .asSequence()
        .filter { networkInterface -> networkInterface.isUp && !networkInterface.isLoopback }
        .flatMap { networkInterface -> networkInterface.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { address -> !address.isLoopbackAddress && !address.isAnyLocalAddress }
        ?.hostAddress
}
