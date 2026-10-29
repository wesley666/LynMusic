package top.iwesley.lyn.music.platform

import androidx.media3.common.Player
import java.util.UUID
import top.iwesley.lyn.music.domain.NavidromeTranscodeOffsetSupport
import top.iwesley.lyn.music.domain.navidromeStreamUrlAtOffset

/** All methods and response callbacks run on the player's application looper. */
internal interface NavidromePlaybackDriver {
    val mediaId: String?
    val positionMs: Long
    val durationMs: Long
    val seekable: Boolean
    val ready: Boolean
    val ended: Boolean
    fun open(url: String, mediaId: String, playWhenReady: Boolean, onResponse: (NavidromeStreamResponse) -> Unit)
    fun seek(positionMs: Long)
    fun setPlaying(playing: Boolean)
}

/** Keeps the whole-track clock separate from Exo's clock for the currently opened fragment. */
internal class AndroidNavidromePlaybackSession(
    private val driver: NavidromePlaybackDriver,
    private var url: String,
    durationMs: Long,
    playWhenReady: Boolean,
    startPositionMs: Long,
    private val changed: () -> Unit,
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
    val canSeek: Boolean get() = !closed && (serverSeek || (!offsetStream && ownsPlayer && driver.seekable))
    val awaitingPosition: Boolean get() = !failed && pendingPositionMs != null
    val positionMs: Long get() {
        val position = pendingPositionMs ?: if (replacing || !ownsPlayer) originMs else originMs + driver.positionMs.coerceAtLeast(0L)
        return if (durationMs > 0L) position.coerceIn(0L, durationMs) else position.coerceAtLeast(0L)
    }

    fun start() = openStream()

    fun updateSupport(value: NavidromeTranscodeOffsetSupport) {
        if (closed) return
        support = value
        capabilityResolved = true
        reconcile()
        changed()
    }

    fun reconcile() {
        if (closed || replacing || reconciling || !ownsPlayer || failed) return
        reconciling = true
        try {
            if (!offsetStream && durationMs <= 0L && driver.durationMs > 0L) durationMs = driver.durationMs
            val pending = pendingPositionMs ?: return
            if (!driver.ready || !responseReceived) return
            when {
                !offsetStream && ranges == NavidromeRangeSupport.Bytes && driver.seekable -> nativeSeek(pending)
                serverSeek -> serverSeek(pending)
                !capabilityResolved -> return
                !offsetStream && driver.seekable -> nativeSeek(pending)
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
        failed = false
        if (serverSeek) serverSeek(positionMs) else nativeSeek(positionMs)
        changed()
    }

    private fun nativeSeek(positionMs: Long) {
        completedGeneration = -1L
        pendingPositionMs = null
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

    fun reload(url: String) {
        if (closed) return
        val position = positionMs
        this.url = url
        // A new address can return a cached full resource. Classify it before applying an offset.
        originMs = 0L
        offsetStream = false
        ranges = NavidromeRangeSupport.Unknown
        pendingPositionMs = position.takeIf { it > 0L }
        openStream()
    }

    private fun openStream() {
        if (closed) return
        generation += 1L
        val opening = generation
        expectedMediaId = "$id:$opening"
        responseReceived = false
        failed = false
        replacing = true
        try {
            driver.open(navidromeStreamUrlAtOffset(url, originMs / 1_000L), expectedMediaId,
                wantsPlayback && pendingPositionMs == null) { response ->
                if (!closed && opening == generation) {
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
        if (closed) return
        wantsPlayback = playing
        driver.setPlaying(playing && pendingPositionMs == null)
        changed()
    }

    fun onPlayWhenReadyChanged(playing: Boolean, reason: Int) {
        // App and MediaSession commands already update intent through setPlaying(). Exo reports
        // our temporary pauses as USER_REQUEST too, sometimes after a nested listener returns.
        // Only autonomous changes (audio focus, becoming noisy, etc.) can change intent here.
        if (!closed && reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) wantsPlayback = playing
    }

    fun onFailure() {
        failed = true
    }

    fun consumeEnded(): Boolean {
        if (closed || replacing || pendingPositionMs != null || !ownsPlayer || !driver.ended || completedGeneration == generation) return false
        completedGeneration = generation
        return true
    }

    fun close() {
        closed = true
        generation += 1L
    }
}
