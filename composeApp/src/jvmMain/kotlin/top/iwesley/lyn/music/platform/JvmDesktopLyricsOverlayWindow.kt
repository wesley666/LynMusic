package top.iwesley.lyn.music.platform

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JWindow
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import top.iwesley.lyn.music.core.model.DesktopLyricsPosition
import top.iwesley.lyn.music.core.model.DesktopLyricsPositionPreferencesStore
import top.iwesley.lyn.music.core.model.DesktopLyricsPositionSaveController
import top.iwesley.lyn.music.core.model.DesktopLyricsViewport
import top.iwesley.lyn.music.core.model.DesktopLyricsWindowLocation
import top.iwesley.lyn.music.core.model.DesktopLyricsPlatformService
import top.iwesley.lyn.music.core.model.DiagnosticLogger
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.calculateDesktopLyricsPosition
import top.iwesley.lyn.music.core.model.calculateDesktopLyricsWindowLocation
import top.iwesley.lyn.music.core.model.error

internal interface JvmDesktopLyricsOverlayWindowAdapter {
    fun showText(text: String)
    fun hide()
    fun release()
    fun setCloseRequestHandler(handler: () -> Unit)
    fun restorePosition(position: DesktopLyricsPosition?)
    // Fired once on actual movement, not on mouse press or an ordinary click.
    fun setPositionChangeStartedHandler(handler: () -> Unit)
    fun setPositionChangeFinishedHandler(handler: (DesktopLyricsPosition?) -> Unit)
}

internal class JvmDesktopLyricsPlatformService(
    private val window: JvmDesktopLyricsOverlayWindowAdapter = AwtJvmDesktopLyricsOverlayWindow(),
    private val positionPreferencesStore: DesktopLyricsPositionPreferencesStore? = null,
    private val persistenceScope: CoroutineScope? = null,
    private val logger: DiagnosticLogger = NoopDiagnosticLogger,
) : DesktopLyricsPlatformService {
    private val mutableCloseRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val positionChangesLock = Any()
    private var acceptsPositionChanges = false
    private var positionChangeInProgress = false
    private val positionSaveController = if (
        positionPreferencesStore != null && persistenceScope != null
    ) {
        DesktopLyricsPositionSaveController(
            scope = persistenceScope,
            onSaveFailure = { error ->
                logger.error(DESKTOP_LYRICS_LOG_TAG, error) { "保存桌面歌词位置失败。" }
            },
        ) { position -> positionPreferencesStore.setDesktopLyricsPosition(position) }
    } else {
        null
    }

    override val isSupported: Boolean = true
    override val consumesAppLyricsUpdates: Boolean = true
    override val closeRequests: Flow<Unit> = mutableCloseRequests.asSharedFlow()

    init {
        window.restorePosition(positionPreferencesStore?.desktopLyricsPosition?.value)
        window.setCloseRequestHandler {
            setAcceptsPositionChanges(false)
            mutableCloseRequests.tryEmit(Unit)
        }
        window.setPositionChangeStartedHandler {
            synchronized(positionChangesLock) {
                if (acceptsPositionChanges) {
                    positionChangeInProgress = true
                    positionSaveController?.discardPending()
                } else {
                    positionChangeInProgress = false
                }
            }
        }
        window.setPositionChangeFinishedHandler { position ->
            synchronized(positionChangesLock) {
                val shouldSubmit = acceptsPositionChanges && positionChangeInProgress
                positionChangeInProgress = false
                if (shouldSubmit && position != null) positionSaveController?.submit(position)
            }
        }
    }

    override fun hasOverlayPermission(): Boolean = true

    override suspend fun requestOverlayPermission(): Boolean = true

    override suspend fun setDesktopLyricsEnabled(enabled: Boolean) {
        setAcceptsPositionChanges(enabled)
        if (!enabled) {
            window.hide()
        }
    }

    override suspend fun updateLyrics(text: String) {
        window.showText(text)
    }

    override suspend fun hideLyrics() {
        window.hide()
    }

    override suspend fun release() {
        setAcceptsPositionChanges(false)
        window.release()
    }

    private fun setAcceptsPositionChanges(enabled: Boolean) {
        synchronized(positionChangesLock) {
            val wasEnabled = acceptsPositionChanges
            acceptsPositionChanges = enabled
            if (!enabled || !wasEnabled) positionChangeInProgress = false
            if (!enabled) positionSaveController?.discardPending()
        }
    }
}

private const val DESKTOP_LYRICS_LOG_TAG = "DesktopLyrics"

private class AwtJvmDesktopLyricsOverlayWindow : JvmDesktopLyricsOverlayWindowAdapter {
    private var window: JWindow? = null
    private var label: JLabel? = null
    private var panel: RoundedLyricsPanel? = null
    private var closeButton: CloseOverlayButton? = null
    private var closeRequestHandler: (() -> Unit)? = null
    private var positionChangeStartedHandler: (() -> Unit)? = null
    private var positionChangeFinishedHandler: ((DesktopLyricsPosition?) -> Unit)? = null
    private var controlsVisible = false
    private var preferredPosition: DesktopLyricsPosition? = null
    private var dragStartScreen: Point? = null
    private var dragStartWindow: Point? = null
    private var dragMoved = false
    private val hideControlsTimer = Timer(1000) {
        setControlsVisible(false)
    }.apply {
        isRepeats = false
    }

    override fun showText(text: String) {
        runOnEdt {
            if (GraphicsEnvironment.isHeadless()) return@runOnEdt
            val resolvedWindow = ensureWindow()
            label?.text = text
            val display = packForPreferredDisplay(resolvedWindow)
            placeAtPreferredPositionOrDefault(resolvedWindow, display)
            resolvedWindow.isVisible = true
        }
    }

    override fun hide() {
        runOnEdt {
            finishPositionChange(null)
            hideControlsTimer.stop()
            setControlsVisible(false)
            window?.isVisible = false
        }
    }

    override fun release() {
        runOnEdt {
            finishPositionChange(null)
            hideControlsTimer.stop()
            window?.dispose()
            window = null
            label = null
            panel = null
            closeButton = null
            preferredPosition = null
            controlsVisible = false
        }
    }

    override fun setCloseRequestHandler(handler: () -> Unit) {
        closeRequestHandler = handler
    }

    override fun restorePosition(position: DesktopLyricsPosition?) {
        preferredPosition = position
    }

    override fun setPositionChangeStartedHandler(handler: () -> Unit) {
        positionChangeStartedHandler = handler
    }

    override fun setPositionChangeFinishedHandler(handler: (DesktopLyricsPosition?) -> Unit) {
        positionChangeFinishedHandler = handler
    }

    private fun ensureWindow(): JWindow {
        window?.let { return it }
        val nextLabel = JLabel("", SwingConstants.CENTER).apply {
            foreground = Color.WHITE
            font = font.deriveFont(22f)
            border = BorderFactory.createEmptyBorder(
                LABEL_VERTICAL_PADDING,
                LABEL_NORMAL_HORIZONTAL_PADDING,
                LABEL_VERTICAL_PADDING,
                LABEL_NORMAL_HORIZONTAL_PADDING,
            )
            putClientProperty("html.disable", true)
            minimumSize = Dimension(240, 56)
        }
        val nextCloseButton = CloseOverlayButton {
            hideControlsTimer.stop()
            setControlsVisible(false)
            window?.isVisible = false
            closeRequestHandler?.invoke()
        }.apply {
            addMouseListener(hoverMouseAdapter)
        }
        val panel = RoundedLyricsPanel(nextLabel, nextCloseButton)
        val nextWindow = JWindow().apply {
            isAlwaysOnTop = true
            background = Color(0, 0, 0, 0)
            contentPane = panel
            addWindowFocusListener(object : WindowAdapter() {
                override fun windowGainedFocus(event: WindowEvent) {
                    showControls()
                }

                override fun windowLostFocus(event: WindowEvent) {
                    scheduleHideControls()
                }
            })
            addMouseListener(dragMouseAdapter)
            addMouseMotionListener(dragMouseAdapter)
        }
        installDragHandlers(panel)
        installDragHandlers(nextLabel)
        label = nextLabel
        this.panel = panel
        closeButton = nextCloseButton
        window = nextWindow
        return nextWindow
    }

    private val dragMouseAdapter = object : MouseAdapter() {
        override fun mouseEntered(event: MouseEvent) {
            showControls()
        }

        override fun mouseExited(event: MouseEvent) {
            scheduleHideControls()
        }

        override fun mousePressed(event: MouseEvent) {
            showControls()
            dragStartScreen = event.locationOnScreen
            dragStartWindow = window?.location
            dragMoved = false
        }

        override fun mouseDragged(event: MouseEvent) {
            val startScreen = dragStartScreen ?: return
            val startWindow = dragStartWindow ?: return
            val current = event.locationOnScreen
            val target = window ?: return
            if (!dragMoved) {
                if (current == startScreen) return
                positionChangeStartedHandler?.invoke()
                dragMoved = true
            }
            target.location = Point(
                startWindow.x + current.x - startScreen.x,
                startWindow.y + current.y - startScreen.y,
            )
            preferredPosition = positionForWindow(target)
            showControls()
        }

        override fun mouseReleased(event: MouseEvent) {
            val target = window
            val position = if (dragMoved) target?.let(::positionForWindow) else null
            if (position != null && target != null) {
                preferredPosition = position
                val display = packForPreferredDisplay(target)
                placeAtPreferredPositionOrDefault(target, display)
            }
            positionChangeFinishedHandler?.invoke(position)
            resetPositionChangeState()
        }
    }

    private fun finishPositionChange(position: DesktopLyricsPosition?) {
        val hadActivePositionChange = dragStartScreen != null || dragStartWindow != null
        if (hadActivePositionChange) positionChangeFinishedHandler?.invoke(position)
        resetPositionChangeState()
    }

    private fun resetPositionChangeState() {
        dragStartScreen = null
        dragStartWindow = null
        dragMoved = false
    }

    private val hoverMouseAdapter = object : MouseAdapter() {
        override fun mouseEntered(event: MouseEvent) {
            showControls()
        }

        override fun mouseExited(event: MouseEvent) {
            scheduleHideControls()
        }
    }

    private fun installDragHandlers(component: Component) {
        component.addMouseListener(dragMouseAdapter)
        component.addMouseMotionListener(dragMouseAdapter)
    }

    private fun showControls() {
        hideControlsTimer.stop()
        setControlsVisible(true)
    }

    private fun scheduleHideControls() {
        hideControlsTimer.restart()
    }

    private fun setControlsVisible(visible: Boolean) {
        if (controlsVisible == visible) return
        controlsVisible = visible
        closeButton?.isVisible = visible
        val target = window ?: return
        val display = packForPreferredDisplay(target)
        if (target.isVisible) {
            placeAtPreferredPositionOrDefault(target, display)
        }
    }

    private fun packForPreferredDisplay(target: JWindow): JvmLyricsDisplay? {
        val display = displayFor(preferredPosition)
        val viewportWidth = display?.viewport?.width ?: target.graphicsConfiguration.bounds.width
        panel?.availableViewportWidth = viewportWidth
        target.pack()
        return display
    }

    private fun placeAtPreferredPositionOrDefault(
        target: JWindow,
        resolvedDisplay: JvmLyricsDisplay? = null,
    ) {
        val position = preferredPosition
        if (position == null) {
            placeBottomCenter(target, resolvedDisplay ?: displayFor(null))
            return
        }
        val display = resolvedDisplay ?: displayFor(position) ?: run {
            placeBottomCenter(target, displayFor(null))
            return
        }
        val location = calculateDesktopLyricsWindowLocation(
            position = position,
            windowWidth = target.width,
            windowHeight = target.height,
            viewport = display.viewport,
        )
        target.setLocation(location.x, location.y)
    }

    private fun positionForWindow(target: JWindow): DesktopLyricsPosition? {
        val display = displayForWindow(target) ?: return null
        return calculateDesktopLyricsPosition(
            windowX = target.x,
            windowY = target.y,
            windowWidth = target.width,
            windowHeight = target.height,
            viewport = display.viewport,
            displayId = display.id,
        )
    }

    private fun displayFor(position: DesktopLyricsPosition?): JvmLyricsDisplay? {
        val displays = availableDisplays()
        return position?.let { savedPosition ->
            displays.firstOrNull { display -> display.id == savedPosition.displayId }
        }
            ?: displays.firstOrNull { display -> display.isDefault }
            ?: displays.firstOrNull()
    }

    private fun displayForWindow(target: JWindow): JvmLyricsDisplay? {
        val displays = availableDisplays()
        if (displays.isEmpty()) return null
        val centerX = target.x + target.width / 2
        val centerY = target.y + target.height / 2
        return displays.firstOrNull { display -> display.viewport.contains(centerX, centerY) }
            ?: displays.minByOrNull { display -> display.viewport.distanceSquaredTo(centerX, centerY) }
    }

    private fun availableDisplays(): List<JvmLyricsDisplay> {
        val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val defaultId = environment.defaultScreenDevice.getIDstring()
        return environment.screenDevices.map { device ->
            val configuration = device.defaultConfiguration
            val bounds = configuration.bounds
            val insets = runCatching {
                Toolkit.getDefaultToolkit().getScreenInsets(configuration)
            }.getOrNull()
            JvmLyricsDisplay(
                id = device.getIDstring(),
                isDefault = device.getIDstring() == defaultId,
                viewport = DesktopLyricsViewport(
                    left = bounds.x + (insets?.left ?: 0),
                    top = bounds.y + (insets?.top ?: 0),
                    width = (bounds.width - (insets?.left ?: 0) - (insets?.right ?: 0)).coerceAtLeast(0),
                    height = (bounds.height - (insets?.top ?: 0) - (insets?.bottom ?: 0)).coerceAtLeast(0),
                ),
            )
        }
    }

    private fun placeBottomCenter(
        target: JWindow,
        display: JvmLyricsDisplay?,
    ) {
        val viewport = display?.viewport ?: run {
            val configuration = target.graphicsConfiguration
            val bounds = configuration.bounds
            val insets = runCatching {
                Toolkit.getDefaultToolkit().getScreenInsets(configuration)
            }.getOrNull()
            DesktopLyricsViewport(
                left = bounds.x + (insets?.left ?: 0),
                top = bounds.y + (insets?.top ?: 0),
                width = (bounds.width - (insets?.left ?: 0) - (insets?.right ?: 0)).coerceAtLeast(0),
                height = (bounds.height - (insets?.top ?: 0) - (insets?.bottom ?: 0)).coerceAtLeast(0),
            )
        }
        val location = calculateJvmDesktopLyricsBottomCenterLocation(
            viewport = viewport,
            windowWidth = target.width,
            windowHeight = target.height,
        )
        target.setLocation(location.x, location.y)
    }

    private fun runOnEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeLater(block)
        }
    }

    private companion object {
        const val LABEL_VERTICAL_PADDING = 12
        const val LABEL_NORMAL_HORIZONTAL_PADDING = 28
    }
}

private data class JvmLyricsDisplay(
    val id: String,
    val isDefault: Boolean,
    val viewport: DesktopLyricsViewport,
)

internal fun calculateJvmDesktopLyricsWindowWidth(
    preferredWidth: Int,
    viewportWidth: Int,
    reservedHorizontalSpace: Int = 96,
    minimumWidth: Int = 240,
): Int {
    val safeViewportWidth = viewportWidth.coerceAtLeast(1)
    val maximumWidth = (
        safeViewportWidth - reservedHorizontalSpace.coerceAtLeast(0)
    ).coerceAtLeast(1)
    val effectiveMinimumWidth = minimumWidth.coerceAtLeast(1).coerceAtMost(maximumWidth)
    return preferredWidth.coerceAtLeast(effectiveMinimumWidth).coerceAtMost(maximumWidth)
}

internal fun calculateJvmDesktopLyricsBottomCenterLocation(
    viewport: DesktopLyricsViewport,
    windowWidth: Int,
    windowHeight: Int,
    bottomMargin: Int = 96,
): DesktopLyricsWindowLocation {
    val safeViewportWidth = viewport.width.coerceAtLeast(0)
    val safeViewportHeight = viewport.height.coerceAtLeast(0)
    val safeWindowWidth = windowWidth.coerceAtLeast(0)
    val safeWindowHeight = windowHeight.coerceAtLeast(0)
    val maxX = viewport.left + (safeViewportWidth - safeWindowWidth).coerceAtLeast(0)
    val maxY = viewport.top + (safeViewportHeight - safeWindowHeight).coerceAtLeast(0)
    val desiredX = viewport.left + (safeViewportWidth - safeWindowWidth) / 2
    val desiredY = viewport.top + safeViewportHeight - safeWindowHeight - bottomMargin.coerceAtLeast(0)
    return DesktopLyricsWindowLocation(
        x = desiredX.coerceIn(viewport.left, maxX),
        y = desiredY.coerceIn(viewport.top, maxY),
    )
}

private fun DesktopLyricsViewport.contains(x: Int, y: Int): Boolean {
    return x >= left && x < left + width && y >= top && y < top + height
}

private fun DesktopLyricsViewport.distanceSquaredTo(x: Int, y: Int): Long {
    val nearestX = x.coerceIn(left, left + width.coerceAtLeast(0))
    val nearestY = y.coerceIn(top, top + height.coerceAtLeast(0))
    val deltaX = (x - nearestX).toLong()
    val deltaY = (y - nearestY).toLong()
    return deltaX * deltaX + deltaY * deltaY
}

private class CloseOverlayButton(
    private val onClose: () -> Unit,
) : JComponent() {
    private var pressed = false

    init {
        toolTipText = "关闭桌面歌词"
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        preferredSize = Dimension(24, 24)
        minimumSize = Dimension(24, 24)
        isOpaque = false
        isFocusable = true
        isVisible = false
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                pressed = contains(event.point)
            }

            override fun mouseReleased(event: MouseEvent) {
                val shouldClose = pressed && contains(event.point)
                pressed = false
                if (shouldClose) {
                    onClose()
                }
            }

            override fun mouseExited(event: MouseEvent) {
                pressed = false
            }
        })
    }

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color(255, 255, 255, 235)
            g.stroke = BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            val size = 10
            val x = (width - size) / 2
            val y = (height - size) / 2
            val inset = 2
            g.drawLine(x + inset, y + inset, x + size - inset, y + size - inset)
            g.drawLine(x + size - inset, y + inset, x + inset, y + size - inset)
        } finally {
            g.dispose()
        }
    }
}

private class RoundedLyricsPanel(
    private val label: JLabel,
    private val closeButton: CloseOverlayButton,
) : JPanel(null) {
    var availableViewportWidth: Int = Int.MAX_VALUE

    init {
        isOpaque = false
        add(label)
        add(closeButton)
        setComponentZOrder(closeButton, 0)
        setComponentZOrder(label, 1)
    }

    override fun getPreferredSize(): Dimension {
        val labelSize = label.preferredSize
        return Dimension(
            calculateJvmDesktopLyricsWindowWidth(
                preferredWidth = labelSize.width,
                viewportWidth = availableViewportWidth,
            ),
            labelSize.height.coerceAtLeast(56),
        )
    }

    override fun doLayout() {
        label.setBounds(0, 0, width, height)
        val buttonSize = closeButton.preferredSize
        closeButton.setBounds(4, 3, buttonSize.width, buttonSize.height)
    }

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color(0, 0, 0, 150)
            g.fillRoundRect(0, 0, width, height, 28, 28)
        } finally {
            g.dispose()
        }
        super.paintComponent(graphics)
    }
}
