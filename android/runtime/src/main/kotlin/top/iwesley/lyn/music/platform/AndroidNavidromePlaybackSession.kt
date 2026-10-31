package top.iwesley.lyn.music.platform

import androidx.media3.common.Player
import java.io.IOException
import java.util.UUID
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.info
import top.iwesley.lyn.music.core.model.warn
import top.iwesley.lyn.music.domain.NavidromeTranscodeOffsetSupport
import top.iwesley.lyn.music.domain.isRemoteSourceAddressFallbackAllowed
import top.iwesley.lyn.music.domain.navidromeStreamUrlAtOffset

internal enum class NavidromeReadRecoveryResult { NotApplicable, Recovering, Exhausted, AddressUnavailable, InvalidTarget }

/** All methods and response callbacks run on the player's application looper. */
internal interface NavidromePlaybackDriver {
    val mediaId: String?
    val positionMs: Long
    val durationMs: Long
    val seekable: Boolean
    val ready: Boolean
    val ended: Boolean
    fun open(url: String, mediaId: String, playWhenReady: Boolean, readRecovery: Boolean, onResponse: (NavidromeStreamResponse) -> Unit)
    fun seek(positionMs: Long)
    fun setPlaying(playing: Boolean)
    fun scheduleRecovery(delayMs: Long, action: () -> Unit): () -> Unit
}

/** Keeps the whole-track clock separate from Exo's clock for the currently opened fragment. */
internal class AndroidNavidromePlaybackSession(
    private val driver: NavidromePlaybackDriver,
    private var url: String,
    durationMs: Long,
    playWhenReady: Boolean,
    startPositionMs: Long,
    private val changed: () -> Unit,
    private val recoveryFailed: (NavidromeRecoveryFailure) -> Unit = {},
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
    private val currentRequest: () -> Boolean = { true },
) {
    private val id = UUID.randomUUID().toString()
    private var generation = 0L
    private var expectedMediaId = ""
    private var completedGeneration = -1L
    private var closed = false
    private var reconciling = false
    private var offsetStream = false
    private var responseReceived = false
    private var ranges = NavidromeRangeSupport.Unknown
    private var support = NavidromeTranscodeOffsetSupport.Unknown
    private var capabilityResolved = false
    private var pendingPositionMs: Long? = startPositionMs.coerceAtLeast(0L).takeIf { it > 0L }
    private var failed = false
    private var strictPositionRestore = false
    private var openingRecoveryOffset = false
    private var classifyingRecoveryAddress = false
    private var waitingForRecoveryCapability = false
    private var cancelScheduledRecovery: (() -> Unit)? = null
    private var recoveryVersion = 0L
    private var readRecoveryError: Throwable? = null
    private var readRecoveryAttempts = 0
    private var readRecoveryCheckpointMs: Long? = null
    private var recoveryFailureReported = false
    private val exhaustedAddresses = mutableSetOf<String>()

    var originMs = 0L
        private set
    var durationMs = durationMs.coerceAtLeast(0L)
        private set
    var wantsPlayback = playWhenReady
        private set
    var replacing = false
        private set

    private val ownsPlayer: Boolean get() = driver.mediaId == expectedMediaId
    private val serverSeek: Boolean get() = durationMs > 0L && support == NavidromeTranscodeOffsetSupport.Supported &&
        (ranges == NavidromeRangeSupport.None || offsetStream)
    val isCurrentRequest: Boolean get() = !closed && currentRequest()
    val canSeek: Boolean get() = isCurrentRequest && (serverSeek || (!offsetStream && ownsPlayer && driver.seekable))
    val awaitingPosition: Boolean get() = !failed && pendingPositionMs != null
    val isRecoveringRead: Boolean get() = isCurrentRequest && readRecoveryError != null && awaitingPosition
    val positionMs: Long get() {
        val position = pendingPositionMs ?: if (replacing || !ownsPlayer) originMs else originMs + driver.positionMs.coerceAtLeast(0L)
        return if (durationMs > 0L) position.coerceIn(0L, durationMs) else position.coerceAtLeast(0L)
    }

    fun start() = openStream()

    fun updateSupport(value: NavidromeTranscodeOffsetSupport) {
        if (!isCurrentRequest) return
        support = value
        capabilityResolved = true
        reconcile()
        changed()
    }

    fun reconcile() {
        if (!isCurrentRequest || replacing || reconciling || !ownsPlayer || failed) return
        reconciling = true
        try {
            if (waitingForRecoveryCapability) {
                if (!capabilityResolved) return
                waitingForRecoveryCapability = false
                val result = scheduleReadRecovery()
                if (result != NavidromeReadRecoveryResult.Recovering) {
                    failReadRecovery(checkNotNull(readRecoveryError), result, notify = true)
                }
                return
            }
            if (cancelScheduledRecovery != null) return
            if (!offsetStream && durationMs <= 0L && driver.durationMs > 0L) durationMs = driver.durationMs
            val pending = pendingPositionMs ?: return
            if (!driver.ready || !responseReceived) return
            if (openingRecoveryOffset) {
                openingRecoveryOffset = false
                pendingPositionMs = null
                strictPositionRestore = false
                driver.setPlaying(wantsPlayback)
                logger.info("NavidromePlayback") {
                    "read-recovery-ready generation=$generation target=$pending origin=$originMs position=$positionMs playing=$wantsPlayback"
                }
                return
            }
            if (classifyingRecoveryAddress && pending == 0L) {
                classifyingRecoveryAddress = false
                pendingPositionMs = null
                strictPositionRestore = false
                driver.setPlaying(wantsPlayback)
                return
            }
            when {
                !offsetStream && ranges == NavidromeRangeSupport.Bytes && driver.seekable -> nativeSeek(pending)
                serverSeek -> if (strictPositionRestore) openRecoveryOffset(pending) else serverSeek(pending)
                !capabilityResolved -> return
                !offsetStream && driver.seekable -> nativeSeek(pending)
                strictPositionRestore -> failReadRecovery(
                    readRecoveryError ?: IOException("Navidrome cannot restore the playback position"),
                    NavidromeReadRecoveryResult.AddressUnavailable, notify = true,
                )
                else -> {
                    // The old server cannot restore this position; retain the old start-at-zero behaviour.
                    pendingPositionMs = null
                    driver.setPlaying(wantsPlayback)
                }
            }
        } finally {
            reconciling = false
        }
    }

    fun seek(positionMs: Long) {
        if (!canSeek) return
        cancelRecovery()
        resetReadRecoveryBudget()
        exhaustedAddresses.clear()
        strictPositionRestore = false
        openingRecoveryOffset = false
        classifyingRecoveryAddress = false
        failed = false
        if (serverSeek) serverSeek(positionMs) else nativeSeek(positionMs)
        changed()
    }

    private fun nativeSeek(positionMs: Long) {
        completedGeneration = -1L
        pendingPositionMs = null
        strictPositionRestore = false
        openingRecoveryOffset = false
        classifyingRecoveryAddress = false
        resetReadRecoveryBudget()
        val target = if (durationMs > 0L) positionMs.coerceIn(0L, durationMs) else positionMs.coerceAtLeast(0L)
        driver.seek(target)
        driver.setPlaying(wantsPlayback)
    }

    private fun serverSeek(positionMs: Long) {
        // Navidrome accepts integer seconds. Avoid an empty response for an exact-end seek.
        val bounded = if (positionMs >= durationMs) (durationMs - 1_000L).coerceAtLeast(0L) else positionMs.coerceAtLeast(0L)
        originMs = bounded / 1_000L * 1_000L
        pendingPositionMs = null
        offsetStream = true
        openStream()
    }

    fun canReload(url: String): Boolean = isCurrentRequest && url !in exhaustedAddresses

    fun reload(url: String): Boolean {
        if (!canReload(url)) return false
        val position = positionMs
        cancelRecovery()
        readRecoveryAttempts = 0
        readRecoveryCheckpointMs = position.takeIf { readRecoveryError != null }
        recoveryFailureReported = false
        this.url = url
        // A new address can return a cached full resource. Classify it before applying an offset.
        originMs = 0L
        offsetStream = false
        ranges = NavidromeRangeSupport.Unknown
        classifyingRecoveryAddress = readRecoveryError != null
        pendingPositionMs = position.takeIf { it > 0L || classifyingRecoveryAddress }
        strictPositionRestore = position > 0L
        openingRecoveryOffset = false
        openStream()
        return true
    }

    /** Called by the gateway before publishing a final player error or changing address. */
    fun recoverReadFailure(error: Throwable): NavidromeReadRecoveryResult {
        if (!isCurrentRequest || !ownsPlayer) return NavidromeReadRecoveryResult.NotApplicable
        // These errors have already used Media3's position-preserving native retries.
        // Let the gateway apply the ordinary error/address fallback path, without requiring offsets.
        if (!offsetStream && driver.durationMs > 0L) return NavidromeReadRecoveryResult.NotApplicable
        val position = positionMs
        val readFailure = error.isNavidromeSequentialReadFailure() ||
            (readRecoveryError != null && isRemoteSourceAddressFallbackAllowed(error))
        val checkpoint = readRecoveryCheckpointMs
        if (pendingPositionMs == null && checkpoint != null && position >= checkpoint + 1_000L) {
            resetReadRecoveryBudget()
        }
        if (!readFailure) {
            return NavidromeReadRecoveryResult.NotApplicable
        }
        cancelRecovery()
        pendingPositionMs = position
        strictPositionRestore = true
        openingRecoveryOffset = false
        readRecoveryError = error
        recoveryFailureReported = false
        failed = false
        driver.setPlaying(false)
        val result = if (invalidRecoveryTarget(position)) {
            NavidromeReadRecoveryResult.InvalidTarget
        } else if (!capabilityResolved && !classifyingRecoveryAddress) {
            waitingForRecoveryCapability = true
            NavidromeReadRecoveryResult.Recovering
        } else {
            scheduleReadRecovery()
        }
        if (result != NavidromeReadRecoveryResult.Recovering) failReadRecovery(error, result, notify = false)
        changed()
        return result
    }

    private fun scheduleReadRecovery(): NavidromeReadRecoveryResult {
        val target = checkNotNull(pendingPositionMs)
        val offset = target / 1_000L * 1_000L
        if (invalidRecoveryTarget(target)) return NavidromeReadRecoveryResult.InvalidTarget
        val canOpenRecovery = classifyingRecoveryAddress ||
            (support == NavidromeTranscodeOffsetSupport.Supported && (ranges == NavidromeRangeSupport.None || offsetStream))
        if (!canOpenRecovery) return NavidromeReadRecoveryResult.AddressUnavailable
        if (readRecoveryAttempts >= 3) {
            exhaustedAddresses += url
            return NavidromeReadRecoveryResult.Exhausted
        }
        val delayMs = readRecoveryAttempts * 1_000L
        readRecoveryAttempts++
        readRecoveryCheckpointMs = target
        val version = recoveryVersion
        logger.warn("NavidromePlayback") {
            "read-recovery-scheduled generation=$generation attempt=$readRecoveryAttempts target=$target " +
                "offset=${offset / 1_000L} delay=$delayMs cause=" +
                generateSequence(readRecoveryError) { it.cause }.take(8).joinToString("->") { it.javaClass.simpleName }
        }
        cancelScheduledRecovery = driver.scheduleRecovery(delayMs) {
            if (isCurrentRequest && version == recoveryVersion) {
                cancelScheduledRecovery = null
                if (classifyingRecoveryAddress) openStream() else openRecoveryOffset(target)
            }
        }
        return NavidromeReadRecoveryResult.Recovering
    }

    private fun openRecoveryOffset(positionMs: Long) {
        val offset = positionMs / 1_000L * 1_000L
        if (invalidRecoveryTarget(positionMs)) {
            failReadRecovery(readRecoveryError ?: IOException("Navidrome recovery target is at the end of the track"),
                NavidromeReadRecoveryResult.InvalidTarget, notify = true)
            return
        }
        originMs = offset
        offsetStream = true
        classifyingRecoveryAddress = false
        openingRecoveryOffset = true
        logger.info("NavidromePlayback") {
            "read-recovery-open generation=${generation + 1L} target=$positionMs origin=$originMs attempt=$readRecoveryAttempts"
        }
        openStream()
    }

    private fun invalidRecoveryTarget(positionMs: Long): Boolean =
        durationMs > 0L && positionMs / 1_000L * 1_000L >= durationMs

    fun recoveryFailure(error: Throwable, result: NavidromeReadRecoveryResult): NavidromeRecoveryFailure =
        NavidromeRecoveryFailure(error, positionMs, result, expectedMediaId, recoveryVersion)

    fun acceptsRecoveryFailure(failure: NavidromeRecoveryFailure): Boolean =
        isCurrentRequest && ownsPlayer && failure.mediaId == expectedMediaId && failure.recoveryVersion == recoveryVersion

    private fun failReadRecovery(error: Throwable, result: NavidromeReadRecoveryResult, notify: Boolean) {
        cancelRecovery()
        failed = true
        if (result == NavidromeReadRecoveryResult.AddressUnavailable) exhaustedAddresses += url
        driver.setPlaying(false)
        if (!recoveryFailureReported) {
            recoveryFailureReported = true
            logger.warn("NavidromePlayback") { "read-recovery-failed generation=$generation position=$positionMs result=$result" }
            if (notify) recoveryFailed(recoveryFailure(error, result))
        }
    }

    private fun cancelRecovery() {
        recoveryVersion++
        cancelScheduledRecovery?.invoke()
        cancelScheduledRecovery = null
        waitingForRecoveryCapability = false
    }

    private fun resetReadRecoveryBudget() {
        readRecoveryError = null
        readRecoveryAttempts = 0
        readRecoveryCheckpointMs = null
    }

    private fun openStream() {
        if (!isCurrentRequest) return
        generation += 1L
        val opening = generation
        expectedMediaId = "$id:$opening"
        responseReceived = false
        failed = false
        replacing = true
        try {
            driver.open(navidromeStreamUrlAtOffset(url, originMs / 1_000L), expectedMediaId,
                wantsPlayback && pendingPositionMs == null, readRecoveryError != null) { response ->
                if (isCurrentRequest && opening == generation) {
                    responseReceived = true
                    ranges = response.ranges
                    if (durationMs <= 0L) durationMs = response.durationMs ?: 0L
                    // An offset of zero can become an ordinary full cached stream.
                    if (originMs == 0L && ranges == NavidromeRangeSupport.Bytes) offsetStream = false
                    reconcile()
                    changed()
                }
            }
        } finally {
            replacing = false
        }
        reconcile()
        changed()
    }

    fun setPlaying(playing: Boolean) {
        if (!isCurrentRequest) return
        wantsPlayback = playing
        val error = readRecoveryError
        if (playing && failed && error != null) {
            cancelRecovery()
            readRecoveryAttempts = 0
            readRecoveryCheckpointMs = null
            exhaustedAddresses.clear()
            openingRecoveryOffset = false
            recoveryFailureReported = false
            failed = false
            val result = if (invalidRecoveryTarget(positionMs)) {
                NavidromeReadRecoveryResult.InvalidTarget
            } else if (!capabilityResolved && !classifyingRecoveryAddress) {
                waitingForRecoveryCapability = true
                NavidromeReadRecoveryResult.Recovering
            } else {
                scheduleReadRecovery()
            }
            if (result != NavidromeReadRecoveryResult.Recovering) failReadRecovery(error, result, notify = true)
            changed()
            return
        }
        driver.setPlaying(playing && pendingPositionMs == null)
        changed()
    }

    fun onPlayWhenReadyChanged(playing: Boolean, reason: Int) {
        // App and MediaSession commands already update intent through setPlaying(). Exo reports
        // our temporary pauses as USER_REQUEST too, sometimes after a nested listener returns.
        // Only autonomous changes (audio focus, becoming noisy, etc.) can change intent here.
        if (isCurrentRequest && reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) wantsPlayback = playing
    }

    fun onFailure() {
        pendingPositionMs = positionMs
        cancelRecovery()
        failed = true
    }

    fun consumeEnded(): Boolean {
        if (!isCurrentRequest || failed || replacing || pendingPositionMs != null || !ownsPlayer || !driver.ended || completedGeneration == generation) return false
        completedGeneration = generation
        return true
    }

    fun close() {
        closed = true
        cancelRecovery()
        generation += 1L
    }
}
