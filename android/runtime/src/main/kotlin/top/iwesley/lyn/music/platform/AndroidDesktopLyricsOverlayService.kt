package top.iwesley.lyn.music.platform

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.core.model.AndroidDiagnosticLogger
import top.iwesley.lyn.music.core.model.DesktopLyricsPosition
import top.iwesley.lyn.music.core.model.DesktopLyricsPositionSaveController
import top.iwesley.lyn.music.core.model.DesktopLyricsPlatformService
import top.iwesley.lyn.music.core.model.DesktopLyricsViewport
import top.iwesley.lyn.music.core.model.DesktopLyricsWindowLocation
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.calculateDesktopLyricsPosition
import top.iwesley.lyn.music.core.model.calculateDesktopLyricsWindowLocation
import top.iwesley.lyn.music.core.model.error
import top.iwesley.lyn.music.core.model.withSecureInMemoryCache
import top.iwesley.lyn.music.data.repository.DefaultLyricsRepository
import top.iwesley.lyn.music.data.repository.LyricsRepository
import top.iwesley.lyn.music.domain.RemoteSourceAddressSelector
import top.iwesley.lyn.music.feature.player.findDesktopLyricsHighlightedLine
import top.iwesley.lyn.music.feature.player.resolveDesktopLyricsOverlayText
import kotlin.math.abs

class AndroidDesktopLyricsOverlayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val logger = AndroidDiagnosticLogger(enabled = true, label = "Android Desktop Lyrics")
    private val viewportCache = AndroidDesktopLyricsViewportCache()
    private val overlayLayoutState = AndroidDesktopLyricsLayoutState()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { setOverlayControlsVisible(false) }
    private lateinit var preferencesStore: AndroidAppPreferencesStore
    private lateinit var lyricsRepository: LyricsRepository
    private lateinit var windowManager: WindowManager
    private lateinit var positionSaveController: DesktopLyricsPositionSaveController
    private var overlayView: View? = null
    private var overlayContainerView: DesktopLyricsOverlayFrameLayout? = null
    private var lyricsTextView: TextView? = null
    private var closeButtonView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var overlayControlsVisible = false
    private var preferredPosition: DesktopLyricsPosition? = null
    private var overlayLayoutListener: ViewTreeObserver.OnPreDrawListener? = null
    private var overlayLayoutListenerView: View? = null
    private var overlayCoordinates = AndroidDesktopLyricsWindowCoordinates()
    private var positionSaveAfterLayout = false
    private var dragExceededTouchSlop = false
    private var dragPositionChanged = false
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartX = 0
    private var dragStartY = 0
    private var currentLyricsRequestKey: String? = null
    private var currentLyrics: LyricsDocument? = null
    private var lyricsLoading = false
    private var lyricsLoadJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        preferencesStore = AndroidAppPreferencesStore(applicationContext)
        preferredPosition = preferencesStore.desktopLyricsPosition.value
        positionSaveController = DesktopLyricsPositionSaveController(
            scope = serviceScope,
            onSaveFailure = { error ->
                logger.error(DESKTOP_LYRICS_LOG_TAG, error) { "保存桌面歌词位置失败。" }
            },
        ) { position -> preferencesStore.setDesktopLyricsPosition(position) }
        lyricsRepository = createServiceLyricsRepository()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        observeDesktopLyrics()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> hideOverlay()
            ACTION_STOP -> {
                positionSaveController.discardPending()
                hideOverlay()
                stopSelf()
            }
            else -> Unit
        }
        return START_STICKY
    }

    override fun onDestroy() {
        positionSaveController.discardPending()
        mainHandler.removeCallbacks(hideControlsRunnable)
        lyricsLoadJob?.cancel()
        hideOverlay()
        serviceScope.cancel()
        preferencesStore.close()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        viewportCache.clear()
        overlayView?.let { view ->
            view.requestApplyInsets()
            scheduleOverlayLayout(view, shouldRefreshViewport = true)
        }
    }

    private fun observeDesktopLyrics() {
        serviceScope.launch {
            combine(
                preferencesStore.showDesktopLyrics,
                AndroidPlaybackRuntimeRegistry.repository,
            ) { enabled, repository -> enabled to repository }
                .collectLatest { (enabled, repository) ->
                    if (!enabled || !canDrawOverlays(applicationContext)) {
                        positionSaveController.discardPending()
                        if (enabled && !canDrawOverlays(applicationContext)) {
                            preferencesStore.setShowDesktopLyrics(false)
                        }
                        clearLyricsState()
                        hideOverlay()
                        if (!enabled) stopSelf()
                        return@collectLatest
                    }
                    val playbackRepository = repository
                    if (playbackRepository == null) {
                        clearLyricsState()
                        hideOverlay()
                    } else {
                        playbackRepository.snapshot.collectLatest(::updateForSnapshot)
                    }
                }
        }
    }

    private fun updateForSnapshot(snapshot: PlaybackSnapshot) {
        if (!canDrawOverlays(applicationContext)) {
            handleOverlayPermissionRevoked()
            return
        }
        val track = snapshot.currentTrack
        if (track == null) {
            clearLyricsState()
            hideOverlay()
            return
        }
        val lookupTrack = snapshot.toLyricsLookupTrack()
        val requestKey = lookupTrack.lyricsRequestKey()
        if (requestKey != currentLyricsRequestKey) {
            currentLyricsRequestKey = requestKey
            currentLyrics = null
            lyricsLoading = true
            lyricsLoadJob?.cancel()
            lyricsLoadJob = serviceScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { lyricsRepository.getLyrics(lookupTrack) }.getOrNull()
                }
                if (currentLyricsRequestKey != requestKey) return@launch
                currentLyrics = result?.document
                lyricsLoading = false
                renderSnapshot(snapshot)
            }
        }
        renderSnapshot(snapshot)
    }

    private fun renderSnapshot(snapshot: PlaybackSnapshot) {
        val highlightedLineIndex = findDesktopLyricsHighlightedLine(
            lyrics = currentLyrics,
            positionMs = snapshot.positionMs,
        )
        val text = resolveDesktopLyricsOverlayText(
            lyrics = currentLyrics,
            highlightedLineIndex = highlightedLineIndex,
            isLyricsLoading = lyricsLoading,
        )
        if (text == null) {
            hideOverlay()
        } else {
            showText(text)
        }
    }

    private fun showText(text: String) {
        if (!canDrawOverlays(applicationContext)) {
            handleOverlayPermissionRevoked()
            return
        }
        if (text.isBlank()) return
        val view = ensureOverlayView()
        val params = overlayParams ?: return
        val textView = lyricsTextView ?: return
        val textChanged = textView.text.toString() != text
        if (textChanged) textView.text = text
        val wasAdded = view.parent == null
        if (wasAdded) {
            windowManager.addView(view, params)
        } else if (textChanged) {
            windowManager.updateViewLayout(view, params)
        }
        if (wasAdded) view.requestApplyInsets()
        if (wasAdded || textChanged) {
            scheduleOverlayLayout(view, shouldRefreshViewport = wasAdded)
        }
    }

    private fun hideOverlay() {
        mainHandler.removeCallbacks(hideControlsRunnable)
        cancelScheduledOverlayLayout()
        overlayLayoutState.clear()
        dragExceededTouchSlop = false
        dragPositionChanged = false
        positionSaveAfterLayout = false
        overlayCoordinates = AndroidDesktopLyricsWindowCoordinates()
        overlayControlsVisible = false
        closeButtonView?.visibility = View.GONE
        val view = overlayView ?: return
        if (view.parent != null) {
            runCatching { windowManager.removeView(view) }
        }
    }

    private fun ensureOverlayView(): View {
        overlayView?.let { return it }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            @Suppress("RtlHardcoded") // Position persistence uses physical screen coordinates.
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = 0
        }
        overlayParams = params
        val viewport = currentViewport()
        val textView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = calculateAndroidDesktopLyricsTextMaxWidth(
                viewport = viewport,
                reservedHorizontalSpace = dp(96),
            )
            setOnTouchListener(::handleDragTouch)
        }
        val closeButton = CloseOverlayButton(this).apply {
            contentDescription = "关闭桌面歌词"
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            setOnClickListener { closeDesktopLyricsFromOverlay() }
        }
        return DesktopLyricsOverlayFrameLayout(this).apply {
            maximumWidth = calculateAndroidDesktopLyricsOverlayMaxWidth(viewport)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(Color.argb(150, 0, 0, 0))
            }
            setOnTouchListener(::handleDragTouch)
            setOnApplyWindowInsetsListener { target, insets ->
                scheduleOverlayLayout(target, shouldRefreshViewport = true)
                insets
            }
            addView(
                textView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ).apply {
                    leftMargin = dp(24)
                    topMargin = dp(10)
                    rightMargin = dp(24)
                    bottomMargin = dp(10)
                },
            )
            addView(
                closeButton,
                FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP or Gravity.START).apply {
                    leftMargin = dp(4)
                    topMargin = dp(3)
                },
            )
            lyricsTextView = textView
            closeButtonView = closeButton
            overlayContainerView = this
            overlayView = this
        }
    }

    private fun handleDragTouch(view: View, event: MotionEvent): Boolean {
        val params = overlayParams ?: return false
        val deltaX = event.rawX - dragStartRawX
        val deltaY = event.rawY - dragStartRawY
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                showOverlayControlsTemporarily()
                dragExceededTouchSlop = false
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                dragStartX = params.x
                dragStartY = params.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
                if (!dragExceededTouchSlop && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) {
                    dragExceededTouchSlop = true
                    overlayLayoutState.beginDrag()
                    positionSaveController.discardPending()
                    positionSaveAfterLayout = false
                }
                if (!dragExceededTouchSlop) return true
                params.x = dragStartX + deltaX.toInt()
                params.y = dragStartY + deltaY.toInt()
                val target = overlayView ?: view
                preferredPosition = calculatePosition(
                    target,
                    overlayCoordinates.toScreen(DesktopLyricsWindowLocation(params.x, params.y)),
                )
                dragPositionChanged = true
                showOverlayControlsTemporarily()
                windowManager.updateViewLayout(target, params)
                return true
            }

            MotionEvent.ACTION_UP -> {
                finishOverlayDrag(overlayView ?: view)
                if (!dragExceededTouchSlop) {
                    showOverlayControlsTemporarily()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                finishOverlayDrag(overlayView ?: view)
                scheduleOverlayControlsHide()
                return true
            }
        }
        return true
    }

    private fun placeBottomCenter(view: View): Boolean {
        val location = calculateAndroidDesktopLyricsBottomCenterLocation(
            viewport = currentViewport(),
            windowWidth = view.width,
            windowHeight = view.height,
            bottomMargin = dp(96),
        )
        return applyScreenLocation(view, location)
    }

    private fun placeAtPreferredPositionOrDefault(view: View): Boolean {
        val position = preferredPosition
        if (position == null) {
            return placeBottomCenter(view)
        }
        val location = calculateDesktopLyricsWindowLocation(
            position = position,
            windowWidth = view.width,
            windowHeight = view.height,
            viewport = currentViewport(),
        )
        return applyScreenLocation(view, location)
    }

    private fun applyScreenLocation(view: View, location: DesktopLyricsWindowLocation): Boolean {
        val params = overlayParams ?: return false
        val offset = overlayCoordinates.toWindow(location)
        if (params.x == offset.x && params.y == offset.y) return false
        params.x = offset.x
        params.y = offset.y
        if (view.parent != null) {
            windowManager.updateViewLayout(view, params)
        }
        return true
    }

    private fun calculatePosition(
        view: View,
        screen: DesktopLyricsWindowLocation,
    ): DesktopLyricsPosition {
        return calculateDesktopLyricsPosition(
            windowX = screen.x,
            windowY = screen.y,
            windowWidth = view.width,
            windowHeight = view.height,
            viewport = currentViewport(),
        )
    }

    private fun currentViewport(): DesktopLyricsViewport {
        return viewportCache.current(::resolveCurrentViewport)
    }

    private fun refreshCurrentViewport(): DesktopLyricsViewport {
        return viewportCache.refresh(::resolveCurrentViewport)
    }

    private fun resolveCurrentViewport(): AndroidDesktopLyricsViewportResolution {
        return resolveAndroidDesktopLyricsViewport(
            windowManager = windowManager,
            fallbackDisplayMetrics = resources.displayMetrics,
        )
    }

    private fun updateOverlayWidthConstraints(): Boolean {
        val textView = lyricsTextView ?: return false
        val containerView = overlayContainerView ?: return false
        val viewport = currentViewport()
        val maxWidth = calculateAndroidDesktopLyricsTextMaxWidth(
            viewport = viewport,
            reservedHorizontalSpace = dp(96),
        )
        val overlayMaxWidth = calculateAndroidDesktopLyricsOverlayMaxWidth(viewport)
        var changed = false
        if (textView.maxWidth != maxWidth) {
            textView.maxWidth = maxWidth
            changed = true
        }
        if (containerView.maximumWidth != overlayMaxWidth) {
            containerView.maximumWidth = overlayMaxWidth
            changed = true
        }
        return changed
    }

    private fun scheduleOverlayLayout(
        view: View,
        shouldRefreshViewport: Boolean = false,
    ) {
        overlayLayoutState.requestLayout(
            refreshViewport = shouldRefreshViewport || !viewportCache.hasConfirmedViewport,
        )
        ensureOverlayLayoutListener(view)
    }

    private fun ensureOverlayLayoutListener(view: View) {
        if (view.parent == null || !overlayLayoutState.shouldObserveNextLayout) return
        if (overlayLayoutListenerView === view && overlayLayoutListener != null) {
            view.requestLayout()
            return
        }
        cancelScheduledOverlayLayout()
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (overlayLayoutListener !== this || overlayLayoutListenerView !== view) return true
                // Insets/measurement callbacks may have queued another layout. Do not pair its
                // new LayoutParams with the previous frame's screen position.
                if (view.isLayoutRequested) return true
                view.viewTreeObserver.removeOnPreDrawListener(this)
                overlayLayoutListener = null
                overlayLayoutListenerView = null
                applyOverlayLayoutAfterMeasurement(view)
                return true
            }
        }
        overlayLayoutListener = listener
        overlayLayoutListenerView = view
        view.viewTreeObserver.addOnPreDrawListener(listener)
        view.requestLayout()
    }

    private fun applyOverlayLayoutAfterMeasurement(view: View) {
        if (view.parent == null) return
        val request = overlayLayoutState.consumeAfterLayout() ?: return
        val params = overlayParams ?: return
        val screenPosition = IntArray(2)
        view.getLocationOnScreen(screenPosition)
        val screen = DesktopLyricsWindowLocation(screenPosition[0], screenPosition[1])
        overlayCoordinates = AndroidDesktopLyricsWindowCoordinates.fromLayout(
            screen = screen,
            appliedOffset = DesktopLyricsWindowLocation(params.x, params.y),
        )
        if (request.refreshViewport) refreshCurrentViewport()
        if (dragPositionChanged) {
            preferredPosition = calculatePosition(view, screen)
            dragPositionChanged = false
            positionSaveAfterLayout = true
        }
        if (updateOverlayWidthConstraints()) {
            overlayLayoutState.requestLayout()
            ensureOverlayLayoutListener(view)
            return
        }
        if (request.correctPosition && placeAtPreferredPositionOrDefault(view)) {
            // Confirm the actual position in the next traversal before saving. A confirmation
            // alone must not repeatedly correct a position constrained by the system.
            overlayLayoutState.requestPositionConfirmation()
            ensureOverlayLayoutListener(view)
            return
        }
        if (positionSaveAfterLayout) {
            positionSaveAfterLayout = false
            val position = calculatePosition(view, screen)
            preferredPosition = position
            positionSaveController.submit(position)
        }
    }

    private fun cancelScheduledOverlayLayout() {
        val listener = overlayLayoutListener
        val listenerView = overlayLayoutListenerView
        overlayLayoutListener = null
        overlayLayoutListenerView = null
        if (listener != null && listenerView != null) {
            val observer = listenerView.viewTreeObserver
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
        }
    }

    private fun finishOverlayDrag(view: View) {
        overlayLayoutState.finishDrag()
        scheduleOverlayLayout(view)
    }

    private fun showOverlayControlsTemporarily() {
        setOverlayControlsVisible(true)
        scheduleOverlayControlsHide()
    }

    private fun scheduleOverlayControlsHide() {
        mainHandler.removeCallbacks(hideControlsRunnable)
        mainHandler.postDelayed(hideControlsRunnable, 3_000L)
    }

    private fun setOverlayControlsVisible(visible: Boolean) {
        if (overlayControlsVisible == visible) return
        overlayControlsVisible = visible
        closeButtonView?.visibility = if (visible) View.VISIBLE else View.GONE
        val view = overlayView ?: return
        if (view.parent != null) {
            overlayParams?.let { params ->
                windowManager.updateViewLayout(view, params)
            }
        }
        scheduleOverlayLayout(view)
    }

    private fun closeDesktopLyricsFromOverlay() {
        positionSaveController.discardPending()
        clearLyricsState()
        hideOverlay()
        serviceScope.launch {
            preferencesStore.setShowDesktopLyrics(false)
            stopSelf()
        }
    }

    private fun handleOverlayPermissionRevoked() {
        positionSaveController.discardPending()
        clearLyricsState()
        hideOverlay()
        serviceScope.launch {
            preferencesStore.setShowDesktopLyrics(false)
            stopSelf()
        }
    }

    private fun clearLyricsState() {
        lyricsLoadJob?.cancel()
        lyricsLoadJob = null
        currentLyricsRequestKey = null
        currentLyrics = null
        lyricsLoading = false
    }

    private fun createServiceLyricsRepository(): LyricsRepository {
        val database = openAndroidRuntimeDatabase(applicationContext)
        val secureStore = AndroidCredentialStore(applicationContext, logger).withSecureInMemoryCache()
        val networkConnectionTypeProvider = AndroidNetworkConnectionTypeProvider.get(applicationContext)
        val remoteSourceAddressSelector = RemoteSourceAddressSelector(networkConnectionTypeProvider)
        val httpClient = AndroidLyricsHttpClient()
        val artworkCacheStore = createAndroidArtworkCacheStore(applicationContext)
        return DefaultLyricsRepository(
            database = database,
            httpClient = httpClient,
            secureCredentialStore = secureStore,
            audioTagGateway = AndroidAudioTagGateway(
                context = applicationContext,
                database = database,
                secureCredentialStore = secureStore,
                logger = logger,
            ),
            sameNameLyricsFileGateway = AndroidSameNameLyricsFileGateway(
                context = applicationContext,
                database = database,
                secureCredentialStore = secureStore,
                logger = logger,
            ),
            artworkCacheStore = artworkCacheStore,
            logger = logger,
            addressSelector = remoteSourceAddressSelector,
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun PlaybackSnapshot.toLyricsLookupTrack(): Track {
        val track = requireNotNull(currentTrack)
        return track.copy(
            title = currentDisplayTitle,
            artistName = currentDisplayArtistName,
            albumTitle = currentDisplayAlbumTitle,
        )
    }

    private fun Track.lyricsRequestKey(): String {
        return listOf(id, title, artistName.orEmpty(), albumTitle.orEmpty()).joinToString("|")
    }

    companion object {
        private const val DESKTOP_LYRICS_LOG_TAG = "DesktopLyrics"
        internal const val ACTION_START = "top.iwesley.lyn.music.action.DESKTOP_LYRICS_START"
        internal const val ACTION_HIDE = "top.iwesley.lyn.music.action.DESKTOP_LYRICS_HIDE"
        internal const val ACTION_STOP = "top.iwesley.lyn.music.action.DESKTOP_LYRICS_STOP"
    }
}

private class DesktopLyricsOverlayFrameLayout(context: Context) : FrameLayout(context) {
    var maximumWidth: Int = Int.MAX_VALUE
        set(value) {
            val normalized = value.coerceAtLeast(1)
            if (field == normalized) return
            field = normalized
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthLimit = maximumWidth
        if (widthLimit == Int.MAX_VALUE) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = View.MeasureSpec.getSize(widthMeasureSpec)
        val cappedWidthMeasureSpec = when (widthMode) {
            View.MeasureSpec.UNSPECIFIED -> View.MeasureSpec.makeMeasureSpec(
                widthLimit,
                View.MeasureSpec.AT_MOST,
            )

            else -> View.MeasureSpec.makeMeasureSpec(
                widthSize.coerceAtMost(widthLimit),
                widthMode,
            )
        }
        super.onMeasure(cappedWidthMeasureSpec, heightMeasureSpec)
    }
}

class AndroidDesktopLyricsPlatformService(
    private val context: Context,
) : DesktopLyricsPlatformService {
    private val appContext = context.applicationContext

    override val isSupported: Boolean = true
    override val consumesAppLyricsUpdates: Boolean = false
    override val closeRequests: Flow<Unit> = emptyFlow()

    override fun hasOverlayPermission(): Boolean = canDrawOverlays(appContext)

    override suspend fun requestOverlayPermission(): Boolean {
        if (hasOverlayPermission()) return true
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${appContext.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
        return hasOverlayPermission()
    }

    override suspend fun setDesktopLyricsEnabled(enabled: Boolean) {
        val action = if (enabled) {
            AndroidDesktopLyricsOverlayService.ACTION_START
        } else {
            AndroidDesktopLyricsOverlayService.ACTION_STOP
        }
        if (enabled && !hasOverlayPermission()) return
        sendServiceIntent(Intent(appContext, AndroidDesktopLyricsOverlayService::class.java).setAction(action))
    }

    override suspend fun updateLyrics(text: String) = Unit

    override suspend fun hideLyrics() {
        sendServiceIntent(
            Intent(appContext, AndroidDesktopLyricsOverlayService::class.java)
                .setAction(AndroidDesktopLyricsOverlayService.ACTION_HIDE),
        )
    }

    override suspend fun release() = Unit

    private fun sendServiceIntent(intent: Intent) {
        runCatching { appContext.startService(intent) }
    }
}

private fun canDrawOverlays(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
}

private class CloseOverlayButton(context: Context) : View(context) {
    private val strokeWidth = 2f * context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = this@CloseOverlayButton.strokeWidth
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = width * 0.32f
        canvas.drawLine(inset, inset, width - inset, height - inset, paint)
        canvas.drawLine(width - inset, inset, inset, height - inset, paint)
    }
}
