package top.iwesley.lyn.music.cast.upnp.android

import top.iwesley.lyn.music.core.model.diagnosticMessage
import top.iwesley.lyn.music.resources.*

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.cast.CastDevice
import top.iwesley.lyn.music.cast.CastGateway
import top.iwesley.lyn.music.cast.CastMediaRequest
import top.iwesley.lyn.music.cast.CastPlaybackState
import top.iwesley.lyn.music.cast.CastSessionState
import top.iwesley.lyn.music.cast.CastSessionStatus
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.UiText
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiText

class AndroidUpnpCastGateway(
    context: Context,
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
) : CastGateway {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(CastSessionState())
    private val nativeHandle: Long
    private val nativeLoadFailure: Throwable?
    private var discoveryJob: Job? = null
    private var playbackPollingJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var released = false

    override val state: StateFlow<CastSessionState> = mutableState.asStateFlow()
    override val isSupported: Boolean
        get() = nativeHandle != 0L && nativeLoadFailure == null

    init {
        val result = runCatching {
            System.loadLibrary("lyn_upnp_cast")
            nativeCreate()
        }
        nativeHandle = result.getOrDefault(0L)
        nativeLoadFailure = result.exceptionOrNull()
        if (nativeHandle == 0L || nativeLoadFailure != null) {
            mutableState.value = CastSessionState(
                status = CastSessionStatus.Unsupported,
                errorMessage = (uiText(Res.string.cast_device_unsupported)).diagnosticMessage(),
                errorText = uiText(Res.string.cast_device_unsupported),
            )
            nativeLoadFailure?.let { error ->
                logger.error(CAST_LOG_TAG, error) { "load-native-upnp-cast-failed" }
            }
        }
    }

    override suspend fun startDiscovery() {
        if (!ensureAvailable()) return
        discoveryJob?.cancelAndJoin()
        discoveryJob = null
        acquireMulticastLock()
        val error = withContext(Dispatchers.IO) { nativeStartDiscovery(nativeHandle) }
        if (error != null) {
            fail(error)
            return
        }
        updateCastState {
            if (it.status == CastSessionStatus.Casting) {
                it.copy(errorMessage = null, errorText = null)
            } else {
                it.copy(
                    status = CastSessionStatus.Searching,
                    message = (uiText(Res.string.cast_searching)).diagnosticMessage(),
                    messageText = uiText(Res.string.cast_searching),
                    errorMessage = null,
                    errorText = null,
                )
            }
        }
        pollDevices()
        discoveryJob = scope.launch {
            while (isActive && !released) {
                delay(DISCOVERY_POLL_INTERVAL_MS)
                pollDevices()
            }
        }
    }

    override suspend fun stopDiscovery() {
        discoveryJob?.cancelAndJoin()
        discoveryJob = null
        releaseMulticastLock()
        updateCastState {
            if (it.status == CastSessionStatus.Searching) {
                it.copy(status = CastSessionStatus.Idle, message = null, messageText = null)
            } else {
                it
            }
        }
    }

    override suspend fun cast(deviceId: String, request: CastMediaRequest) {
        if (!ensureAvailable()) return
        val device = state.value.devices.firstOrNull { it.id == deviceId }
        val deviceName = device?.name?.takeIf { it.isNotBlank() }
        updateCastState {
            it.copy(
                status = CastSessionStatus.Connecting,
                selectedDeviceId = deviceId,
                selectedDeviceName = deviceName,
                message = (connectingText(deviceName)).diagnosticMessage(),
                messageText = connectingText(deviceName),
                errorMessage = null,
                errorText = null,
                playback = null,
            )
        }
        val metadata = buildUpnpDidl(request)
        val error = withContext(Dispatchers.IO) {
            nativeCastMedia(
                handle = nativeHandle,
                deviceId = deviceId,
                uri = request.uri,
                metadata = metadata,
            )
        }
        if (error != null) {
            fail(error, selectedDeviceId = deviceId, selectedDeviceName = deviceName)
            return
        }
        updateCastState {
            it.copy(
                status = CastSessionStatus.Casting,
                selectedDeviceId = deviceId,
                selectedDeviceName = deviceName,
                message = (castingText(deviceName)).diagnosticMessage(),
                messageText = castingText(deviceName),
                errorMessage = null,
                errorText = null,
                playback = CastPlaybackState(
                    durationMs = request.durationMs.coerceAtLeast(0L),
                    isPlaying = true,
                    canSeek = request.durationMs > 0L,
                    lastUpdatedAtMs = System.currentTimeMillis(),
                ),
            )
        }
        startPlaybackPolling(deviceId)
        logger.info(CAST_LOG_TAG) { "cast-started device=${deviceId} uri=${request.uri}" }
    }

    override suspend fun playCast() {
        controlSelectedDevice(
            nativeCall = { handle, deviceId -> nativePlayCast(handle, deviceId) },
            failureText = uiText(Res.string.cast_resume_failed),
        )
    }

    override suspend fun pauseCast() {
        controlSelectedDevice(
            nativeCall = { handle, deviceId -> nativePauseCast(handle, deviceId) },
            failureText = uiText(Res.string.cast_pause_failed),
        )
    }

    override suspend fun seekCast(positionMs: Long) {
        val normalizedPositionMs = positionMs.coerceAtLeast(0L)
        controlSelectedDevice(
            nativeCall = { handle, deviceId -> nativeSeekCast(handle, deviceId, normalizedPositionMs) },
            failureText = uiText(Res.string.cast_seek_failed),
        )
    }

    override suspend fun stopCast() {
        if (!ensureAvailable()) return
        playbackPollingJob?.cancelAndJoin()
        playbackPollingJob = null
        val selectedDeviceId = state.value.selectedDeviceId
        if (selectedDeviceId != null) {
            withContext(Dispatchers.IO) { nativeStopCast(nativeHandle, selectedDeviceId) }
        }
        updateCastState {
            it.copy(
                status = CastSessionStatus.Idle,
                selectedDeviceId = null,
                selectedDeviceName = null,
                message = null,
                messageText = null,
                errorMessage = null,
                errorText = null,
                playback = null,
            )
        }
    }

    override suspend fun release() {
        if (released) return
        released = true
        discoveryJob?.cancelAndJoin()
        discoveryJob = null
        playbackPollingJob?.cancelAndJoin()
        playbackPollingJob = null
        releaseMulticastLock()
        scope.cancel()
        if (nativeHandle != 0L) {
            withContext(Dispatchers.IO) { nativeRelease(nativeHandle) }
        }
    }

    private suspend fun pollDevices() {
        if (!isSupported || released) return
        val devices = withContext(Dispatchers.IO) {
            parseNativeDevices(nativeListDevices(nativeHandle))
        }
        updateCastState {
            it.copy(
                devices = devices,
                messageText = if (it.status == CastSessionStatus.Searching) discoveryText(devices.size) else it.messageText,
                message = if (it.status == CastSessionStatus.Searching) {
                    (discoveryText(devices.size)).diagnosticMessage()
                } else it.message,
            )
        }
    }

    private fun ensureAvailable(): Boolean {
        if (released) return false
        if (isSupported) return true
        updateCastState {
            it.copy(
                status = CastSessionStatus.Unsupported,
                errorMessage = (uiText(Res.string.cast_device_unsupported)).diagnosticMessage(),
                errorText = uiText(Res.string.cast_device_unsupported),
            )
        }
        return false
    }

    private fun fail(
        message: String,
        selectedDeviceId: String? = state.value.selectedDeviceId,
        selectedDeviceName: String? = state.value.selectedDeviceName,
        fallback: UiText = uiText(Res.string.cast_failed),
    ) {
        val text = nativeCastErrorText(message, fallback)
        updateCastState {
            it.copy(
                status = CastSessionStatus.Failed,
                selectedDeviceId = selectedDeviceId,
                selectedDeviceName = selectedDeviceName,
                message = null,
                messageText = null,
                errorMessage = if (message.startsWith("lyn_ui:") || message.isBlank()) {
                    (text).diagnosticMessage()
                } else message,
                errorText = text,
                playback = null,
            )
        }
    }

    private suspend fun controlSelectedDevice(
        nativeCall: suspend (handle: Long, deviceId: String) -> String?,
        failureText: UiText,
    ) {
        if (!ensureAvailable()) return
        val selectedDeviceId = state.value.selectedDeviceId ?: return
        val error = withContext(Dispatchers.IO) {
            nativeCall(nativeHandle, selectedDeviceId)
        }
        if (error != null) {
            fail(error, fallback = failureText)
            return
        }
        queryAndUpdatePlayback(selectedDeviceId)
    }

    private fun startPlaybackPolling(deviceId: String) {
        playbackPollingJob?.cancel()
        playbackPollingJob = scope.launch {
            queryAndUpdatePlayback(deviceId)
            while (isActive && !released) {
                delay(PLAYBACK_POLL_INTERVAL_MS)
                val current = state.value
                if (current.status != CastSessionStatus.Casting || current.selectedDeviceId != deviceId) break
                queryAndUpdatePlayback(deviceId)
            }
        }
    }

    private suspend fun queryAndUpdatePlayback(deviceId: String) {
        if (!isSupported || released) return
        val payload = withContext(Dispatchers.IO) {
            nativeQueryCastPlayback(nativeHandle, deviceId)
        }
        val playback = parseNativePlayback(payload) ?: return
        updateCastState { current ->
            if (current.status == CastSessionStatus.Casting && current.selectedDeviceId == deviceId) {
                current.copy(playback = playback)
            } else {
                current
            }
        }
    }

    private fun updateCastState(transform: (CastSessionState) -> CastSessionState) {
        mutableState.update { current ->
            transform(current).let { updated ->
                if (updated == current) {
                    current
                } else {
                    updated.copy(revision = current.revision + 1)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        multicastLock = wifiManager.createMulticastLock("LynMusicCastDiscovery").apply {
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

    private external fun nativeCreate(): Long
    private external fun nativeStartDiscovery(handle: Long): String?
    private external fun nativeListDevices(handle: Long): String
    private external fun nativeCastMedia(handle: Long, deviceId: String, uri: String, metadata: String): String?
    private external fun nativePlayCast(handle: Long, deviceId: String): String?
    private external fun nativePauseCast(handle: Long, deviceId: String): String?
    private external fun nativeSeekCast(handle: Long, deviceId: String, positionMs: Long): String?
    private external fun nativeQueryCastPlayback(handle: Long, deviceId: String): String
    private external fun nativeStopCast(handle: Long, deviceId: String): String?
    private external fun nativeRelease(handle: Long)
}

private fun parseNativeDevices(payload: String): List<CastDevice> {
    if (payload.isBlank()) return emptyList()
    return payload
        .split(RECORD_SEPARATOR)
        .filter { it.isNotBlank() }
        .mapNotNull { record ->
            val fields = record.split(FIELD_SEPARATOR)
            val id = fields.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CastDevice(
                id = id,
                name = fields.getOrNull(1).orEmpty(),
                description = fields.getOrNull(2)?.takeIf { it.isNotBlank() },
                modelName = fields.getOrNull(3)?.takeIf { it.isNotBlank() },
                manufacturer = fields.getOrNull(4)?.takeIf { it.isNotBlank() },
                location = fields.getOrNull(5)?.takeIf { it.isNotBlank() },
            )
        }
}

private fun parseNativePlayback(payload: String): CastPlaybackState? {
    if (payload.isBlank()) return null
    val fields = payload.split(FIELD_SEPARATOR)
    if (fields.size < 3) return null
    val transportState = fields[0].trim().uppercase()
    val positionMs = fields[1].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    val durationMs = fields[2].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    val isPlaying = transportState == "PLAYING" || transportState == "TRANSITIONING"
    val isPaused = transportState.startsWith("PAUSED")
    val isStoppedAtEnd = transportState == "STOPPED" &&
        durationMs > 0L &&
        positionMs >= (durationMs - CAST_ENDED_POSITION_TOLERANCE_MS).coerceAtLeast(0L)
    return CastPlaybackState(
        positionMs = positionMs.coerceAtMost(durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE),
        durationMs = durationMs,
        isPlaying = isPlaying,
        canSeek = durationMs > 0L && (isPlaying || isPaused || transportState == "STOPPED"),
        isEnded = transportState == "ENDED" || isStoppedAtEnd,
        lastUpdatedAtMs = System.currentTimeMillis(),
    )
}

private const val CAST_LOG_TAG = "Cast"
private const val DISCOVERY_POLL_INTERVAL_MS = 1_000L
private const val PLAYBACK_POLL_INTERVAL_MS = 1_000L
private const val CAST_ENDED_POSITION_TOLERANCE_MS = 1_500L
private const val RECORD_SEPARATOR = "\u001E"
private const val FIELD_SEPARATOR = "\u001F"
