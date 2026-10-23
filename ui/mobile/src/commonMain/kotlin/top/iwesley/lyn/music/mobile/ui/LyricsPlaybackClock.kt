package top.iwesley.lyn.music.mobile.ui

import top.iwesley.lyn.music.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import top.iwesley.lyn.music.cast.CastSessionStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.domain.EnhancedLyricsDisplayLine
import top.iwesley.lyn.music.feature.player.PlayerState
import kotlin.time.TimeSource

private const val LYRICS_EXTRAPOLATION_LIMIT_MS = 500L
private const val LYRICS_BACKWARD_JITTER_MS = 300L

/** The anchor only changes for a new playback report, never for a frame or a recomposition. */
@Stable
internal class LyricsPlaybackClock(
    initialPositionMs: Long,
    initialIsPlaying: Boolean,
    initialReportAtMs: Long,
) {
    var positionMs by mutableLongStateOf(initialPositionMs)
        private set
    private var reportedPositionMs = initialPositionMs
    private var reportedAtMs = initialReportAtMs
    private var isPlaying = initialIsPlaying
    private var presentationActive = false

    fun report(positionMs: Long, playing: Boolean, nowMs: Long) {
        if (reportedPositionMs == positionMs && isPlaying == playing) return
        reportedPositionMs = positionMs
        reportedAtMs = nowMs
        isPlaying = playing
        show(positionMs)
    }

    fun setPresentationActive(active: Boolean, nowMs: Long) {
        if (active && !presentationActive) {
            // Returning from browsing/background starts at actual playback, without old smoothing.
            positionMs = extrapolatedPosition(nowMs)
        }
        presentationActive = active
    }

    fun tick(nowMs: Long) = show(extrapolatedPosition(nowMs))

    fun canExtrapolate(nowMs: Long): Boolean =
        isPlaying && (nowMs - reportedAtMs).coerceAtLeast(0L) < LYRICS_EXTRAPOLATION_LIMIT_MS

    private fun extrapolatedPosition(nowMs: Long): Long = reportedPositionMs + if (isPlaying) {
        (nowMs - reportedAtMs).coerceIn(0L, LYRICS_EXTRAPOLATION_LIMIT_MS)
    } else {
        0L
    }

    private fun show(targetMs: Long) {
        val backwardMs = positionMs - targetMs
        if (!isPlaying || backwardMs <= 0L || backwardMs >= LYRICS_BACKWARD_JITTER_MS) {
            positionMs = targetMs
        }
    }
}

internal fun hasUnfinishedLyricsSegments(line: EnhancedLyricsDisplayLine?, positionMs: Long): Boolean =
    line?.segments?.any { segment ->
        positionMs < (segment.endTimeMs?.takeIf { it > segment.startTimeMs } ?: segment.startTimeMs)
    } == true

internal fun shouldAnimateLyricsFill(
    line: EnhancedLyricsDisplayLine?,
    positionMs: Long,
    isPlaying: Boolean,
    isVisible: Boolean,
    isLifecycleVisible: Boolean,
    isBrowsing: Boolean,
): Boolean = isPlaying && isVisible && isLifecycleVisible && !isBrowsing &&
    hasUnfinishedLyricsSegments(line, positionMs)

@Composable
internal fun rememberLyricsPlaybackClock(
    state: PlayerState,
    track: Track,
    line: EnhancedLyricsDisplayLine?,
    isLineVisible: Boolean,
    isBrowsing: Boolean,
    timeSource: TimeSource = TimeSource.Monotonic,
): LyricsPlaybackClock {
    val snapshot = state.effectiveSnapshot
    val lyrics = state.lyrics
    val offsetMs = lyrics?.offsetMs ?: 0L
    val usingCast = state.castState.status == CastSessionStatus.Casting ||
        (state.castState.status == CastSessionStatus.Connecting &&
            state.castQueueIndex?.let { it in state.snapshot.queue.indices } == true)
    val sourceKey = if (usingCast) state.castState.selectedDeviceId else null
    val origin = remember(timeSource) { timeSource.markNow() }
    val clock = remember(
        track.id, track.sourceId, usingCast, sourceKey,
        state.lyricsVersion, lyrics?.rawPayload, lyrics?.lines, offsetMs, state.lyricsUserOffsetMs, origin,
    ) {
        LyricsPlaybackClock(snapshot.positionMs, snapshot.isPlaying, origin.elapsedNow().inWholeMilliseconds)
    }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val presentationActive = isLineVisible && lifecycleState.isAtLeast(Lifecycle.State.STARTED) && !isBrowsing
    val enabled = shouldAnimateLyricsFill(
        line = line,
        positionMs = snapshot.positionMs + offsetMs,
        isPlaying = snapshot.isPlaying,
        isVisible = isLineVisible,
        isLifecycleVisible = lifecycleState.isAtLeast(Lifecycle.State.STARTED),
        isBrowsing = isBrowsing,
    )
    LaunchedEffect(clock, snapshot.positionMs, snapshot.isPlaying, enabled, presentationActive, line) {
        val nowMs = origin.elapsedNow().inWholeMilliseconds
        // Apply the report before checking the old anchor's limit. This also restarts a stalled clock.
        clock.report(snapshot.positionMs, snapshot.isPlaying, nowMs)
        clock.setPresentationActive(presentationActive, nowMs)
        if (!enabled) return@LaunchedEffect
        clock.tick(origin.elapsedNow().inWholeMilliseconds)
        while (clock.canExtrapolate(origin.elapsedNow().inWholeMilliseconds) &&
            hasUnfinishedLyricsSegments(line, clock.positionMs + offsetMs)
        ) {
            withFrameNanos { clock.tick(origin.elapsedNow().inWholeMilliseconds) }
        }
    }
    return clock
}
