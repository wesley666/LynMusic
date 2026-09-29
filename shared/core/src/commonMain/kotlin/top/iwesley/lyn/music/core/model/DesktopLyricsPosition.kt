package top.iwesley.lyn.music.core.model

import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

const val DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS = 5_000L

data class DesktopLyricsPosition(
    val centerXFraction: Float,
    val centerYFraction: Float,
    val displayId: String? = null,
)

data class DesktopLyricsViewport(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

data class DesktopLyricsWindowLocation(
    val x: Int,
    val y: Int,
)

fun desktopLyricsPositionOrNull(
    centerXFraction: Float?,
    centerYFraction: Float?,
    displayId: String? = null,
): DesktopLyricsPosition? {
    val x = centerXFraction?.takeIf { value -> value.isFinite() && value in 0f..1f } ?: return null
    val y = centerYFraction?.takeIf { value -> value.isFinite() && value in 0f..1f } ?: return null
    return DesktopLyricsPosition(
        centerXFraction = x,
        centerYFraction = y,
        displayId = displayId?.trim()?.takeIf(String::isNotEmpty),
    )
}

fun calculateDesktopLyricsPosition(
    windowX: Int,
    windowY: Int,
    windowWidth: Int,
    windowHeight: Int,
    viewport: DesktopLyricsViewport,
    displayId: String? = null,
): DesktopLyricsPosition {
    val centerX = windowX + windowWidth.coerceAtLeast(0) / 2f
    val centerY = windowY + windowHeight.coerceAtLeast(0) / 2f
    val xFraction = if (viewport.width > 0) {
        (centerX - viewport.left) / viewport.width
    } else {
        0.5f
    }
    val yFraction = if (viewport.height > 0) {
        (centerY - viewport.top) / viewport.height
    } else {
        0.5f
    }
    return DesktopLyricsPosition(
        centerXFraction = xFraction.coerceIn(0f, 1f),
        centerYFraction = yFraction.coerceIn(0f, 1f),
        displayId = displayId?.trim()?.takeIf(String::isNotEmpty),
    )
}

fun calculateDesktopLyricsWindowLocation(
    position: DesktopLyricsPosition,
    windowWidth: Int,
    windowHeight: Int,
    viewport: DesktopLyricsViewport,
): DesktopLyricsWindowLocation {
    val normalized = desktopLyricsPositionOrNull(
        centerXFraction = position.centerXFraction,
        centerYFraction = position.centerYFraction,
        displayId = position.displayId,
    ) ?: DesktopLyricsPosition(0.5f, 0.5f)
    val safeViewportWidth = viewport.width.coerceAtLeast(0)
    val safeViewportHeight = viewport.height.coerceAtLeast(0)
    val safeWindowWidth = windowWidth.coerceAtLeast(0)
    val safeWindowHeight = windowHeight.coerceAtLeast(0)
    val desiredX = (
        viewport.left + normalized.centerXFraction * safeViewportWidth - safeWindowWidth / 2f
    ).roundToInt()
    val desiredY = (
        viewport.top + normalized.centerYFraction * safeViewportHeight - safeWindowHeight / 2f
    ).roundToInt()
    val maxX = viewport.left + (safeViewportWidth - safeWindowWidth).coerceAtLeast(0)
    val maxY = viewport.top + (safeViewportHeight - safeWindowHeight).coerceAtLeast(0)
    return DesktopLyricsWindowLocation(
        x = desiredX.coerceIn(viewport.left, maxX),
        y = desiredY.coerceIn(viewport.top, maxY),
    )
}

class DesktopLyricsPositionSaveController(
    private val scope: CoroutineScope,
    saveDelayMillis: Long = DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS,
    private val onSaveFailure: (Exception) -> Unit = {},
    private val savePosition: suspend (DesktopLyricsPosition) -> Unit,
) {
    private val delayMillis = saveDelayMillis.coerceAtLeast(0L)
    private val state = MutableStateFlow<SaveState>(SaveState.Idle)
    private val saveMutex = Mutex()

    fun submit(position: DesktopLyricsPosition) {
        val pending = SaveState.Pending(position)
        pending.job = scope.launch(start = CoroutineStart.LAZY) {
            delay(delayMillis)
            val saving = SaveState.Saving(pending)
            if (!state.compareAndSet(pending, saving)) return@launch
            try {
                withContext(NonCancellable) {
                    saveMutex.withLock {
                        savePosition(position)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onSaveFailure(error)
            } finally {
                state.compareAndSet(saving, SaveState.Idle)
            }
        }
        val previous = state.getAndUpdate { pending }
        if (previous is SaveState.Pending) previous.job.cancel()
        pending.job.start()
    }

    fun discardPending() {
        val discarded = state.getAndUpdate { current ->
            if (current is SaveState.Pending) SaveState.Idle else current
        }
        if (discarded is SaveState.Pending) discarded.job.cancel()
    }

    private sealed interface SaveState {
        data object Idle : SaveState

        class Pending(val position: DesktopLyricsPosition) : SaveState {
            lateinit var job: Job
        }

        class Saving(val pending: Pending) : SaveState
    }
}
