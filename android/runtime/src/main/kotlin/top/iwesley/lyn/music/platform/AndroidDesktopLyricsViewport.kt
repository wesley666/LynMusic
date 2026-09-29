package top.iwesley.lyn.music.platform

import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.RequiresApi
import top.iwesley.lyn.music.core.model.DesktopLyricsViewport
import top.iwesley.lyn.music.core.model.DesktopLyricsWindowLocation

internal data class AndroidDesktopLyricsDisplayGeometry(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val insetLeft: Int = 0,
    val insetTop: Int = 0,
    val insetRight: Int = 0,
    val insetBottom: Int = 0,
)

internal fun calculateAndroidDesktopLyricsViewport(
    geometry: AndroidDesktopLyricsDisplayGeometry,
): DesktopLyricsViewport {
    val displayWidth = geometry.width.coerceAtLeast(0)
    val displayHeight = geometry.height.coerceAtLeast(0)
    val insetLeft = geometry.insetLeft.coerceIn(0, displayWidth)
    val insetTop = geometry.insetTop.coerceIn(0, displayHeight)
    val insetRight = geometry.insetRight.coerceIn(0, displayWidth - insetLeft)
    val insetBottom = geometry.insetBottom.coerceIn(0, displayHeight - insetTop)
    return DesktopLyricsViewport(
        left = geometry.left + insetLeft,
        top = geometry.top + insetTop,
        width = displayWidth - insetLeft - insetRight,
        height = displayHeight - insetTop - insetBottom,
    )
}

internal fun calculateAndroidDesktopLyricsBottomCenterLocation(
    viewport: DesktopLyricsViewport,
    windowWidth: Int,
    windowHeight: Int,
    bottomMargin: Int,
): DesktopLyricsWindowLocation {
    val viewportWidth = viewport.width.coerceAtLeast(0)
    val viewportHeight = viewport.height.coerceAtLeast(0)
    val safeWindowWidth = windowWidth.coerceAtLeast(0)
    val safeWindowHeight = windowHeight.coerceAtLeast(0)
    val maxX = viewport.left + (viewportWidth - safeWindowWidth).coerceAtLeast(0)
    val maxY = viewport.top + (viewportHeight - safeWindowHeight).coerceAtLeast(0)
    val desiredX = viewport.left + (viewportWidth - safeWindowWidth) / 2
    val desiredY = viewport.top + viewportHeight - safeWindowHeight - bottomMargin.coerceAtLeast(0)
    return DesktopLyricsWindowLocation(
        x = desiredX.coerceIn(viewport.left, maxX),
        y = desiredY.coerceIn(viewport.top, maxY),
    )
}

internal fun calculateAndroidDesktopLyricsTextMaxWidth(
    viewport: DesktopLyricsViewport,
    reservedHorizontalSpace: Int,
): Int {
    return (viewport.width.coerceAtLeast(0) - reservedHorizontalSpace.coerceAtLeast(0)).coerceAtLeast(1)
}

internal fun calculateAndroidDesktopLyricsOverlayMaxWidth(
    viewport: DesktopLyricsViewport,
): Int = viewport.width.coerceAtLeast(1)

internal data class AndroidDesktopLyricsLegacyDisplayMetrics(
    val realWidth: Int,
    val realHeight: Int,
    val appWidth: Int,
    val appHeight: Int,
)

// On API 23-29, FLAG_LAYOUT_NO_LIMITS makes View.getWindowVisibleDisplayFrame() unreliable.
// Reserve the entire real/app metrics difference on both edges so the result is safe without
// guessing which edge owns a system decoration. Touch-only gesture insets on Android 10 are not
// necessarily represented by DisplayMetrics and therefore are outside this legacy guarantee.
internal fun calculateAndroidDesktopLyricsConservativeLegacyViewportOrNull(
    metrics: AndroidDesktopLyricsLegacyDisplayMetrics,
): DesktopLyricsViewport? {
    if (
        metrics.realWidth <= 0 ||
        metrics.realHeight <= 0 ||
        metrics.appWidth <= 0 ||
        metrics.appHeight <= 0 ||
        metrics.appWidth > metrics.realWidth ||
        metrics.appHeight > metrics.realHeight
    ) {
        return null
    }
    val horizontalInset = metrics.realWidth - metrics.appWidth
    val verticalInset = metrics.realHeight - metrics.appHeight
    val viewportWidth = metrics.realWidth.toLong() - horizontalInset.toLong() * 2L
    val viewportHeight = metrics.realHeight.toLong() - verticalInset.toLong() * 2L
    if (viewportWidth <= 0L || viewportHeight <= 0L) return null
    return DesktopLyricsViewport(
        left = horizontalInset,
        top = verticalInset,
        width = viewportWidth.toInt(),
        height = viewportHeight.toInt(),
    )
}

internal data class AndroidDesktopLyricsViewportResolution(
    val viewport: DesktopLyricsViewport,
    val isConfirmed: Boolean,
)

internal fun resolveAndroidDesktopLyricsLegacyViewportOrNull(
    metrics: AndroidDesktopLyricsLegacyDisplayMetrics,
): AndroidDesktopLyricsViewportResolution? {
    val conservativeViewport = calculateAndroidDesktopLyricsConservativeLegacyViewportOrNull(metrics)
    if (conservativeViewport != null) {
        return AndroidDesktopLyricsViewportResolution(
            viewport = conservativeViewport,
            isConfirmed = true,
        )
    }
    val physicalDisplayViewport = DesktopLyricsViewport(
        left = 0,
        top = 0,
        width = metrics.realWidth,
        height = metrics.realHeight,
    ).takeIf(DesktopLyricsViewport::isUsable) ?: return null
    return AndroidDesktopLyricsViewportResolution(
        viewport = physicalDisplayViewport,
        isConfirmed = false,
    )
}

internal class AndroidDesktopLyricsViewportCache {
    private var cachedResolution: AndroidDesktopLyricsViewportResolution? = null

    val hasConfirmedViewport: Boolean
        get() = cachedResolution?.isConfirmed == true

    fun current(resolve: () -> AndroidDesktopLyricsViewportResolution): DesktopLyricsViewport {
        return cachedResolution?.viewport ?: resolve().also { resolution ->
            cachedResolution = resolution
        }.viewport
    }

    fun refresh(resolve: () -> AndroidDesktopLyricsViewportResolution): DesktopLyricsViewport {
        return resolve().also { resolution -> cachedResolution = resolution }.viewport
    }

    fun clear() {
        cachedResolution = null
    }
}

internal data class AndroidDesktopLyricsLayoutRequest(
    val refreshViewport: Boolean,
    val correctPosition: Boolean = true,
)

internal class AndroidDesktopLyricsLayoutState {
    private var refreshViewportPending = false
    private var correctionPending = false
    private var positionCorrectionPending = false
    private var dragInProgress = false

    val shouldObserveNextLayout: Boolean
        get() = correctionPending && !dragInProgress

    fun requestLayout(refreshViewport: Boolean = false) {
        correctionPending = true
        positionCorrectionPending = true
        refreshViewportPending = refreshViewportPending || refreshViewport
    }

    fun requestPositionConfirmation() {
        correctionPending = true
    }

    fun beginDrag() {
        dragInProgress = true
    }

    fun finishDrag() {
        dragInProgress = false
        requestLayout()
    }

    fun consumeAfterLayout(): AndroidDesktopLyricsLayoutRequest? {
        if (!shouldObserveNextLayout) return null
        correctionPending = false
        return AndroidDesktopLyricsLayoutRequest(
            refreshViewport = refreshViewportPending,
            correctPosition = positionCorrectionPending,
        ).also {
            refreshViewportPending = false
            positionCorrectionPending = false
        }
    }

    fun clear() {
        refreshViewportPending = false
        correctionPending = false
        positionCorrectionPending = false
        dragInProgress = false
    }
}

// LayoutParams offsets are relative to the window's parent frame, not the physical screen.
// Build this mapping only from offsets and screen coordinates belonging to the same layout.
internal data class AndroidDesktopLyricsWindowCoordinates(
    val originX: Int = 0,
    val originY: Int = 0,
) {
    fun toScreen(offset: DesktopLyricsWindowLocation): DesktopLyricsWindowLocation =
        DesktopLyricsWindowLocation(offset.x + originX, offset.y + originY)

    fun toWindow(screen: DesktopLyricsWindowLocation): DesktopLyricsWindowLocation =
        DesktopLyricsWindowLocation(screen.x - originX, screen.y - originY)

    companion object {
        fun fromLayout(
            screen: DesktopLyricsWindowLocation,
            appliedOffset: DesktopLyricsWindowLocation,
        ): AndroidDesktopLyricsWindowCoordinates = AndroidDesktopLyricsWindowCoordinates(
            originX = screen.x - appliedOffset.x,
            originY = screen.y - appliedOffset.y,
        )
    }
}

internal fun resolveAndroidDesktopLyricsViewport(
    windowManager: WindowManager,
    fallbackDisplayMetrics: DisplayMetrics,
): AndroidDesktopLyricsViewportResolution {
    val windowMetricsViewport = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        viewportFromMaximumWindowMetrics(windowManager)
    } else {
        null
    }
    if (windowMetricsViewport != null && windowMetricsViewport.isUsable()) {
        return AndroidDesktopLyricsViewportResolution(
            viewport = windowMetricsViewport,
            isConfirmed = true,
        )
    }

    legacyDisplayMetricsOrNull(windowManager)
        ?.let(::resolveAndroidDesktopLyricsLegacyViewportOrNull)
        ?.let { resolution -> return resolution }

    val fallbackViewport = viewportFromLegacyDisplayMetrics(windowManager)?.takeIf(DesktopLyricsViewport::isUsable)
        ?: calculateAndroidDesktopLyricsViewport(
            AndroidDesktopLyricsDisplayGeometry(
                left = 0,
                top = 0,
                width = fallbackDisplayMetrics.widthPixels,
                height = fallbackDisplayMetrics.heightPixels,
            ),
        )
    return AndroidDesktopLyricsViewportResolution(
        viewport = fallbackViewport,
        isConfirmed = false,
    )
}

@RequiresApi(Build.VERSION_CODES.R)
private fun viewportFromMaximumWindowMetrics(windowManager: WindowManager): DesktopLyricsViewport? {
    return try {
        val metrics = windowManager.maximumWindowMetrics
        val bounds = metrics.bounds
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or
                WindowInsets.Type.systemGestures() or
                WindowInsets.Type.displayCutout(),
        )
        calculateAndroidDesktopLyricsViewport(
            AndroidDesktopLyricsDisplayGeometry(
                left = bounds.left,
                top = bounds.top,
                width = bounds.width(),
                height = bounds.height(),
                insetLeft = insets.left,
                insetTop = insets.top,
                insetRight = insets.right,
                insetBottom = insets.bottom,
            ),
        )
    } catch (_: Exception) {
        null
    }
}

@Suppress("DEPRECATION")
private fun legacyDisplayMetricsOrNull(
    windowManager: WindowManager,
): AndroidDesktopLyricsLegacyDisplayMetrics? {
    return try {
        val display = windowManager.defaultDisplay
        val realMetrics = DisplayMetrics()
        display.getRealMetrics(realMetrics)
        if (realMetrics.widthPixels <= 0 || realMetrics.heightPixels <= 0) return null
        val appMetrics = DisplayMetrics()
        try {
            display.getMetrics(appMetrics)
        } catch (_: Exception) {
            // Keep the zeroed app metrics so the legacy selector falls back to the valid real bounds.
        }
        AndroidDesktopLyricsLegacyDisplayMetrics(
            realWidth = realMetrics.widthPixels,
            realHeight = realMetrics.heightPixels,
            appWidth = appMetrics.widthPixels,
            appHeight = appMetrics.heightPixels,
        )
    } catch (_: Exception) {
        null
    }
}

@Suppress("DEPRECATION")
private fun viewportFromLegacyDisplayMetrics(windowManager: WindowManager): DesktopLyricsViewport? {
    return try {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        calculateAndroidDesktopLyricsViewport(
            AndroidDesktopLyricsDisplayGeometry(
                left = 0,
                top = 0,
                width = metrics.widthPixels,
                height = metrics.heightPixels,
            ),
        )
    } catch (_: Exception) {
        null
    }
}

private fun DesktopLyricsViewport.isUsable(): Boolean {
    return width > 0 && height > 0
}
