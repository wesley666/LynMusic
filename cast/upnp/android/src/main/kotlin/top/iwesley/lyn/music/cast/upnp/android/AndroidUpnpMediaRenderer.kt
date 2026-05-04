package top.iwesley.lyn.music.cast.upnp.android

import android.content.Context
import android.net.wifi.WifiManager
import java.util.UUID
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.info

class AndroidUpnpMediaRenderer(
    context: Context,
    private val friendlyName: String = DEFAULT_RENDERER_FRIENDLY_NAME,
    private val callback: UpnpMediaRendererCallback,
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
) {
    private val appContext = context.applicationContext
    private var nativeHandle: Long = 0L
    private var multicastLock: WifiManager.MulticastLock? = null

    val isRunning: Boolean
        get() = nativeHandle != 0L

    @Synchronized
    fun start(): String? {
        if (nativeHandle != 0L) return null
        val handle = runCatching {
            System.loadLibrary("lyn_upnp_cast")
            nativeCreateRenderer(
                callback = this,
                friendlyName = friendlyName,
                uuid = rendererUuid(),
            )
        }.getOrElse { throwable ->
            logger.error(RENDERER_LOG_TAG, throwable) { "load-native-upnp-renderer-failed" }
            return "当前设备暂不支持 DLNA 接收。"
        }
        if (handle == 0L) {
            return "DLNA 接收端初始化失败。"
        }
        nativeHandle = handle
        acquireMulticastLock()
        val error = nativeStartRenderer(handle)
        if (error != null) {
            release()
            return error
        }
        updateTransportState(UpnpRendererTransportState.NoMediaPresent)
        logger.info(RENDERER_LOG_TAG) { "upnp-renderer-started name=$friendlyName" }
        return null
    }

    @Synchronized
    fun release() {
        val handle = nativeHandle
        nativeHandle = 0L
        releaseMulticastLock()
        if (handle != 0L) {
            runCatching { nativeReleaseRenderer(handle) }
        }
    }

    fun updateTransportState(
        state: UpnpRendererTransportState,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
    ) {
        val handle = nativeHandle
        if (handle == 0L) return
        nativeUpdateTransportState(
            handle = handle,
            state = state.upnpValue,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
        )
    }

    fun updateVolume(volumePercent: Int, muted: Boolean) {
        val handle = nativeHandle
        if (handle == 0L) return
        nativeUpdateVolume(
            handle = handle,
            volumePercent = volumePercent.coerceIn(0, 100),
            muted = muted,
        )
    }

    @Suppress("unused")
    private fun handleSetMedia(uri: String, metadata: String): Boolean {
        val media = parseUpnpRendererMedia(uri, metadata)
        if (media == null) {
            logger.info(RENDERER_LOG_TAG) { "upnp-renderer-reject-media uri=$uri" }
            return false
        }
        return runCatching { callback.onSetMedia(media) }
            .getOrElse { throwable ->
                logger.error(RENDERER_LOG_TAG, throwable) { "upnp-renderer-set-media-failed" }
                false
            }
    }

    @Suppress("unused")
    private fun handlePlay(): Boolean = callRendererCallback("play") { callback.onPlay() }

    @Suppress("unused")
    private fun handlePause(): Boolean = callRendererCallback("pause") { callback.onPause() }

    @Suppress("unused")
    private fun handleStop(): Boolean = callRendererCallback("stop") { callback.onStop() }

    @Suppress("unused")
    private fun handleSeek(unit: String, target: String): Boolean {
        if (!unit.equals("REL_TIME", ignoreCase = true) && !unit.equals("ABS_TIME", ignoreCase = true)) {
            return false
        }
        val positionMs = parseUpnpDurationToMs(target) ?: return false
        return callRendererCallback("seek") { callback.onSeek(positionMs) }
    }

    @Suppress("unused")
    private fun handleSetVolume(volumePercent: Int): Boolean {
        return callRendererCallback("set-volume") { callback.onSetVolume(volumePercent.coerceIn(0, 100)) }
    }

    @Suppress("unused")
    private fun handleSetMute(muted: Boolean): Boolean {
        return callRendererCallback("set-mute") { callback.onSetMute(muted) }
    }

    private fun callRendererCallback(action: String, block: () -> Boolean): Boolean {
        return runCatching(block)
            .getOrElse { throwable ->
                logger.error(RENDERER_LOG_TAG, throwable) { "upnp-renderer-$action-failed" }
                false
            }
    }

    @Suppress("DEPRECATION")
    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        multicastLock = wifiManager.createMulticastLock("LynMusicRenderer").apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { lock ->
            if (lock.isHeld) {
                runCatching { lock.release() }
            }
        }
        multicastLock = null
    }

    private fun rendererUuid(): String {
        val preferences = appContext.getSharedPreferences(RENDERER_PREFS_NAME, Context.MODE_PRIVATE)
        val existing = preferences.getString(RENDERER_UUID_KEY, null)
            ?.takeIf { it.isNotBlank() }
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        preferences.edit().putString(RENDERER_UUID_KEY, generated).apply()
        return generated
    }

    private external fun nativeCreateRenderer(callback: Any, friendlyName: String, uuid: String): Long
    private external fun nativeStartRenderer(handle: Long): String?
    private external fun nativeUpdateTransportState(handle: Long, state: String, positionMs: Long, durationMs: Long)
    private external fun nativeUpdateVolume(handle: Long, volumePercent: Int, muted: Boolean)
    private external fun nativeReleaseRenderer(handle: Long)

    companion object {
        const val DEFAULT_RENDERER_FRIENDLY_NAME = "LynMusic TV"
    }
}

private const val RENDERER_LOG_TAG = "CastRenderer"
private const val RENDERER_PREFS_NAME = "lynmusic_upnp_renderer"
private const val RENDERER_UUID_KEY = "renderer_uuid"
