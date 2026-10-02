package top.iwesley.lyn.music.platform

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.SystemPlaybackControlCallbacks
import top.iwesley.lyn.music.core.model.SystemPlaybackControlsPlatformService
import top.iwesley.lyn.music.core.model.UnsupportedSystemPlaybackControlsPlatformService
import top.iwesley.lyn.music.core.model.trackArtworkCacheKey
import top.iwesley.lyn.music.core.model.warn

internal data class JvmSystemPlaybackControlsPlatformServiceRegistration(
    val service: SystemPlaybackControlsPlatformService,
    val isSupported: Boolean,
)

internal fun createJvmSystemPlaybackControlsPlatformService(
    logger: DiagnosticLogger = NoopDiagnosticLogger,
    artworkCacheStore: ArtworkCacheStore = createJvmArtworkCacheStore(),
    osName: String = System.getProperty("os.name").orEmpty(),
    bridgeLoader: () -> MacOsNowPlayingBridge = { JnaMacOsNowPlayingBridge.load() },
): JvmSystemPlaybackControlsPlatformServiceRegistration {
    if (!isMacOsName(osName)) {
        return JvmSystemPlaybackControlsPlatformServiceRegistration(
            service = UnsupportedSystemPlaybackControlsPlatformService,
            isSupported = false,
        )
    }
    val bridge = runCatching { bridgeLoader() }
        .onFailure { throwable ->
            logger.warn(JVM_SYSTEM_PLAYBACK_CONTROLS_TAG) {
                "macOS now playing bridge unavailable: ${throwable.message.orEmpty()}"
            }
        }
        .getOrNull()
        ?: return JvmSystemPlaybackControlsPlatformServiceRegistration(
            service = UnsupportedSystemPlaybackControlsPlatformService,
            isSupported = false,
        )
    return JvmSystemPlaybackControlsPlatformServiceRegistration(
        service = JvmSystemPlaybackControlsPlatformService(
            bridge = bridge,
            artworkCacheStore = artworkCacheStore,
        ),
        isSupported = true,
    )
}

internal data class JvmNowPlayingPayload(
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkPath: String?,
    val durationMs: Long,
    val positionMs: Long,
    val isPlaying: Boolean,
    val canSeek: Boolean,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
)

internal fun buildJvmNowPlayingPayload(
    snapshot: PlaybackSnapshot,
    artworkPath: String?,
): JvmNowPlayingPayload? {
    val track = snapshot.currentTrack ?: return null
    val durationMs = maxOf(snapshot.durationMs, track.durationMs, 0L)
    val positionMs = snapshot.positionMs.coerceAtLeast(0L)
    val hasQueueNavigation = snapshot.queue.size > 1
    return JvmNowPlayingPayload(
        title = snapshot.currentDisplayTitle.ifBlank { track.title }.ifBlank { "LynMusic" },
        artist = snapshot.currentDisplayArtistName?.trim()?.takeIf { it.isNotBlank() },
        album = snapshot.currentDisplayAlbumTitle?.trim()?.takeIf { it.isNotBlank() },
        artworkPath = artworkPath?.trim()?.takeIf { it.isNotBlank() },
        durationMs = durationMs,
        positionMs = if (durationMs > 0L) positionMs.coerceAtMost(durationMs) else positionMs,
        isPlaying = snapshot.isPlaying,
        canSeek = snapshot.canSeek,
        hasNext = hasQueueNavigation,
        hasPrevious = hasQueueNavigation,
    )
}

internal class JvmSystemPlaybackControlsPlatformService(
    private val bridge: MacOsNowPlayingBridge,
    private val artworkCacheStore: ArtworkCacheStore = createJvmArtworkCacheStore(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : SystemPlaybackControlsPlatformService {
    private var callbacks = SystemPlaybackControlCallbacks()
    private var latestArtworkLocator: String? = null
    private var latestArtworkCacheKey: String? = null
    private var latestArtworkPath: String? = null

    init {
        bridge.setCommandHandler { command ->
            when (command) {
                is MacOsNowPlayingCommand.Play -> scope.launch { callbacks.play() }
                is MacOsNowPlayingCommand.Pause -> scope.launch { callbacks.pause() }
                is MacOsNowPlayingCommand.TogglePlayPause -> scope.launch { callbacks.togglePlayPause() }
                is MacOsNowPlayingCommand.Next -> scope.launch { callbacks.skipNext() }
                is MacOsNowPlayingCommand.Previous -> scope.launch { callbacks.skipPrevious() }
                is MacOsNowPlayingCommand.Seek -> scope.launch { callbacks.seekTo(command.positionMs) }
            }
        }
    }

    override fun bind(callbacks: SystemPlaybackControlCallbacks) {
        this.callbacks = callbacks
    }

    override suspend fun updateSnapshot(snapshot: PlaybackSnapshot) {
        val currentTrack = snapshot.currentTrack
        if (currentTrack == null) {
            latestArtworkLocator = null
            latestArtworkCacheKey = null
            latestArtworkPath = null
            bridge.clear()
            return
        }
        val artworkPath = resolveArtworkPath(snapshot)
        buildJvmNowPlayingPayload(snapshot, artworkPath)
            ?.let(bridge::update)
            ?: bridge.clear()
    }

    override suspend fun close() {
        bridge.clear()
        bridge.dispose()
        scope.cancel()
    }

    private suspend fun resolveArtworkPath(snapshot: PlaybackSnapshot): String? {
        val normalized = snapshot.currentDisplayArtworkLocator?.trim()?.takeIf { it.isNotBlank() }
        val cacheKey = snapshot.currentTrack
            ?.let(::trackArtworkCacheKey)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: normalized
        if (normalized == latestArtworkLocator && cacheKey == latestArtworkCacheKey) return latestArtworkPath
        latestArtworkLocator = normalized
        latestArtworkCacheKey = cacheKey
        latestArtworkPath = cacheKey
            ?.let(artworkCacheStore::peekCachedTarget)
            ?.target
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: normalized?.let { artworkCacheStore.cache(it, cacheKey ?: it)?.locator }
        return latestArtworkPath
    }
}

internal sealed interface MacOsNowPlayingCommand {
    data object Play : MacOsNowPlayingCommand
    data object Pause : MacOsNowPlayingCommand
    data object TogglePlayPause : MacOsNowPlayingCommand
    data object Next : MacOsNowPlayingCommand
    data object Previous : MacOsNowPlayingCommand
    data class Seek(val positionMs: Long) : MacOsNowPlayingCommand
}

internal interface MacOsNowPlayingBridge {
    fun setCommandHandler(handler: (MacOsNowPlayingCommand) -> Unit)
    fun update(payload: JvmNowPlayingPayload)
    fun clear()
    fun dispose()
}

private class JnaMacOsNowPlayingBridge private constructor(
    private val nativeLibrary: LynMusicNowPlayingNativeLibrary,
    private val handle: Pointer,
    @Suppress("unused")
    private val nativeCallback: LynMusicNowPlayingCommandCallback,
    private val commandHandlerRef: AtomicReference<(MacOsNowPlayingCommand) -> Unit>,
) : MacOsNowPlayingBridge {
    private var isDisposed = false

    override fun setCommandHandler(handler: (MacOsNowPlayingCommand) -> Unit) {
        commandHandlerRef.set(handler)
    }

    override fun update(payload: JvmNowPlayingPayload) {
        if (isDisposed) return
        nativeLibrary.lyn_music_now_playing_update(
            handle,
            payload.title,
            payload.artist,
            payload.album,
            payload.artworkPath,
            payload.durationMs,
            payload.positionMs,
            payload.isPlaying.toNativeInt(),
            payload.canSeek.toNativeInt(),
            payload.hasNext.toNativeInt(),
            payload.hasPrevious.toNativeInt(),
        )
    }

    override fun clear() {
        if (isDisposed) return
        nativeLibrary.lyn_music_now_playing_clear(handle)
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        nativeLibrary.lyn_music_now_playing_dispose(handle)
    }

    companion object {
        fun load(): JnaMacOsNowPlayingBridge {
            val nativeLibrary = Native.load(
                extractMacOsNowPlayingBridgeLibrary().toAbsolutePath().toString(),
                LynMusicNowPlayingNativeLibrary::class.java,
                mapOf(Library.OPTION_STRING_ENCODING to Charsets.UTF_8.name()),
            )
            val commandHandlerRef = AtomicReference<(MacOsNowPlayingCommand) -> Unit>({})
            val callback = LynMusicNowPlayingCommandCallback { command, value ->
                decodeMacOsNowPlayingCommand(command, value)?.let { decoded ->
                    commandHandlerRef.get().invoke(decoded)
                }
            }
            val handle = nativeLibrary.lyn_music_now_playing_create(callback)
                ?: error("macOS now playing bridge returned a null handle")
            return JnaMacOsNowPlayingBridge(nativeLibrary, handle, callback, commandHandlerRef)
        }
    }
}

private fun decodeMacOsNowPlayingCommand(command: Int, value: Double): MacOsNowPlayingCommand? {
    return when (command) {
        NATIVE_COMMAND_PLAY -> MacOsNowPlayingCommand.Play
        NATIVE_COMMAND_PAUSE -> MacOsNowPlayingCommand.Pause
        NATIVE_COMMAND_TOGGLE_PLAY_PAUSE -> MacOsNowPlayingCommand.TogglePlayPause
        NATIVE_COMMAND_NEXT -> MacOsNowPlayingCommand.Next
        NATIVE_COMMAND_PREVIOUS -> MacOsNowPlayingCommand.Previous
        NATIVE_COMMAND_SEEK -> MacOsNowPlayingCommand.Seek((value * 1_000.0).toLong().coerceAtLeast(0L))
        else -> null
    }
}

private interface LynMusicNowPlayingNativeLibrary : Library {
    fun lyn_music_now_playing_create(callback: LynMusicNowPlayingCommandCallback): Pointer?

    fun lyn_music_now_playing_update(
        handle: Pointer,
        title: String,
        artist: String?,
        album: String?,
        artworkPath: String?,
        durationMs: Long,
        positionMs: Long,
        isPlaying: Int,
        canSeek: Int,
        hasNext: Int,
        hasPrevious: Int,
    ): Int

    fun lyn_music_now_playing_clear(handle: Pointer): Int

    fun lyn_music_now_playing_dispose(handle: Pointer): Int
}

private fun interface LynMusicNowPlayingCommandCallback : Callback {
    fun invoke(command: Int, value: Double)
}

@OptIn(ExperimentalPathApi::class)
private fun extractMacOsNowPlayingBridgeLibrary(): java.nio.file.Path {
    val resourceName = "/native/macos/libLynMusicNowPlayingBridge.dylib"
    val resource = JnaMacOsNowPlayingBridge::class.java.getResourceAsStream(resourceName)
        ?: error("Missing resource $resourceName")
    val directory = Files.createTempDirectory("lynmusic-nowplaying-")
    val target = directory.resolve("libLynMusicNowPlayingBridge.dylib")
    target.deleteIfExists()
    resource.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    }
    directory.toFile().deleteOnExit()
    target.toFile().deleteOnExit()
    return target
}

private fun Boolean.toNativeInt(): Int = if (this) 1 else 0

private fun isMacOsName(osName: String): Boolean = osName.contains("mac", ignoreCase = true)

private const val JVM_SYSTEM_PLAYBACK_CONTROLS_TAG = "JvmSystemPlaybackControls"
private const val NATIVE_COMMAND_PLAY = 1
private const val NATIVE_COMMAND_PAUSE = 2
private const val NATIVE_COMMAND_TOGGLE_PLAY_PAUSE = 3
private const val NATIVE_COMMAND_NEXT = 4
private const val NATIVE_COMMAND_PREVIOUS = 5
private const val NATIVE_COMMAND_SEEK = 6
