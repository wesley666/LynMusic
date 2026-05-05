package top.iwesley.lyn.music.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.iwesley.lyn.music.cast.CastSessionForegroundCallbacks
import top.iwesley.lyn.music.cast.CastSessionForegroundPlatformService
import top.iwesley.lyn.music.cast.CastSessionForegroundState
import top.iwesley.lyn.music.cast.CastSessionStatus
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.PlaybackSnapshot

fun createAndroidCastSessionForegroundPlatformService(
    context: Context,
    artworkCacheStore: ArtworkCacheStore,
): CastSessionForegroundPlatformService {
    return AndroidCastSessionForegroundPlatformService(
        context = context.applicationContext,
        artworkCacheStore = artworkCacheStore,
    )
}

private class AndroidCastSessionForegroundPlatformService(
    private val context: Context,
    private val artworkCacheStore: ArtworkCacheStore,
) : CastSessionForegroundPlatformService {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var callbacks = CastSessionForegroundCallbacks()
    private var latestState: CastSessionForegroundState? = null
    private var lastArtworkKey: String? = null
    private var lastArtworkBitmap: Bitmap? = null
    private var started = false

    init {
        AndroidCastSessionServiceRegistry.controller = this
    }

    override fun bind(callbacks: CastSessionForegroundCallbacks) {
        this.callbacks = callbacks
    }

    override suspend fun start() {
        if (started) return
        started = runCatching {
            AndroidCastSessionForegroundService.start(context)
        }.isSuccess
    }

    override suspend fun update(state: CastSessionForegroundState) {
        latestState = state
        lastArtworkBitmap = resolveArtworkBitmap(state.snapshot)
        if (started) {
            AndroidCastSessionForegroundService.requestSync(context)
        }
    }

    override suspend fun stop() {
        if (!started && latestState == null) return
        started = false
        latestState = null
        lastArtworkKey = null
        lastArtworkBitmap = null
        AndroidCastSessionForegroundService.stop(context)
    }

    override suspend fun close() {
        stop()
        callbacks = CastSessionForegroundCallbacks()
        AndroidCastSessionServiceRegistry.controller = null
    }

    fun handleServiceAction(action: String?) {
        when (action) {
            AndroidCastSessionForegroundService.ACTION_TOGGLE_PLAY_PAUSE -> {
                serviceScope.launch { callbacks.togglePlayPause() }
            }
            AndroidCastSessionForegroundService.ACTION_SKIP_NEXT -> {
                serviceScope.launch { callbacks.skipNext() }
            }
            AndroidCastSessionForegroundService.ACTION_SKIP_PREVIOUS -> {
                serviceScope.launch { callbacks.skipPrevious() }
            }
            AndroidCastSessionForegroundService.ACTION_STOP_CAST -> {
                serviceScope.launch { callbacks.stopCast() }
            }
        }
    }

    fun buildNotification(): Notification? {
        val state = latestState ?: return null
        val snapshot = state.snapshot
        val track = snapshot.currentTrack ?: return null
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, AndroidCastSessionForegroundService.CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }
        val deviceName = state.castState.selectedDeviceName
        val routeText = when (state.castState.status) {
            CastSessionStatus.Connecting -> deviceName?.let { "正在连接 $it" } ?: "正在连接投屏设备"
            CastSessionStatus.Casting -> deviceName?.let { "投屏到 $it" } ?: "正在投屏"
            else -> deviceName?.let { "投屏到 $it" } ?: "正在投屏"
        }
        val metadataText = listOfNotNull(
            snapshot.currentDisplayArtistName,
            snapshot.currentDisplayAlbumTitle,
        ).joinToString(" · ").ifBlank { track.artistName.orEmpty() }
        val contentText = listOf(metadataText, routeText)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { routeText }
        val durationMs = snapshot.durationMs.coerceAtLeast(track.durationMs).coerceAtLeast(0L)
        val positionMs = snapshot.positionMs.coerceIn(0L, durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE)
        return builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(snapshot.currentDisplayTitle.ifBlank { track.title })
            .setContentText(contentText)
            .setLargeIcon(lastArtworkBitmap)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true)
            .setContentIntent(buildContentIntent())
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    "上一首",
                    buildServicePendingIntent(AndroidCastSessionForegroundService.ACTION_SKIP_PREVIOUS),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    if (snapshot.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (snapshot.isPlaying) "暂停" else "播放",
                    buildServicePendingIntent(AndroidCastSessionForegroundService.ACTION_TOGGLE_PLAY_PAUSE),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next,
                    "下一首",
                    buildServicePendingIntent(AndroidCastSessionForegroundService.ACTION_SKIP_NEXT),
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "停止投屏",
                    buildServicePendingIntent(AndroidCastSessionForegroundService.ACTION_STOP_CAST),
                ).build(),
            )
            .apply {
                if (durationMs > 0L) {
                    val maxProgress = durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    val progress = positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    setProgress(maxProgress, progress, false)
                }
            }
            .build()
    }

    private suspend fun resolveArtworkBitmap(snapshot: PlaybackSnapshot): Bitmap? {
        val artworkLookup = AndroidNotificationArtworkLookup.from(snapshot) ?: run {
            lastArtworkKey = null
            return null
        }
        val cacheVersion = artworkCacheStore.observeVersion(artworkLookup.cacheKey).first()
        val artworkKey = artworkLookup.bitmapKey(cacheVersion)
        if (artworkKey == lastArtworkKey && lastArtworkBitmap != null) return lastArtworkBitmap
        val resolvedBitmap = resolveAndroidNotificationArtworkBitmap(
            locator = artworkLookup.locator,
            artworkCacheKey = artworkLookup.cacheKey,
            artworkCacheStore = artworkCacheStore,
        )
        if (resolvedBitmap == null) {
            lastArtworkKey = null
            return null
        }
        lastArtworkKey = artworkKey
        return resolvedBitmap
    }

    private fun buildContentIntent(): PendingIntent {
        val intent = (
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName)
                ?: Intent().setPackage(context.packageName)
            ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            2001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildServicePendingIntent(action: String): PendingIntent {
        val intent = Intent(context, AndroidCastSessionForegroundService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

internal class AndroidCastSessionForegroundService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                START_NOT_STICKY
            }
            else -> {
                ensureChannel()
                val requiresForegroundStart = intent?.action == ACTION_START
                val controller = AndroidCastSessionServiceRegistry.controller
                if (controller == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                controller.handleServiceAction(intent?.action)
                val notification = controller.buildNotification()
                    ?: if (requiresForegroundStart) buildBootstrapNotification() else null
                if (notification == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (requiresForegroundStart) {
                    startForeground(NOTIFICATION_ID, notification)
                } else {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
                START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?) = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "投屏播放",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示当前电视投屏歌曲和投屏控制。"
                setShowBadge(false)
            },
        )
    }

    private fun buildBootstrapNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("LynMusic")
            .setContentText("正在同步投屏状态")
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "lynmusic.cast.session"
        const val ACTION_START = "top.iwesley.lyn.music.action.START_CAST_SESSION"
        const val ACTION_SYNC = "top.iwesley.lyn.music.action.SYNC_CAST_SESSION"
        const val ACTION_TOGGLE_PLAY_PAUSE = "top.iwesley.lyn.music.action.CAST_TOGGLE_PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = "top.iwesley.lyn.music.action.CAST_SKIP_NEXT"
        const val ACTION_SKIP_PREVIOUS = "top.iwesley.lyn.music.action.CAST_SKIP_PREVIOUS"
        const val ACTION_STOP_CAST = "top.iwesley.lyn.music.action.CAST_STOP"
        private const val ACTION_STOP_SERVICE = "top.iwesley.lyn.music.action.STOP_CAST_SESSION_SERVICE"
        private const val NOTIFICATION_ID = 5107

        fun start(context: Context) {
            val intent = Intent(context, AndroidCastSessionForegroundService::class.java)
                .setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun requestSync(context: Context) {
            val intent = Intent(context, AndroidCastSessionForegroundService::class.java)
                .setAction(ACTION_SYNC)
            runCatching { context.startService(intent) }
        }

        fun stop(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
            val intent = Intent(context, AndroidCastSessionForegroundService::class.java)
                .setAction(ACTION_STOP_SERVICE)
            runCatching { context.startService(intent) }
            runCatching { context.stopService(Intent(context, AndroidCastSessionForegroundService::class.java)) }
        }
    }
}

private object AndroidCastSessionServiceRegistry {
    @Volatile
    var controller: AndroidCastSessionForegroundPlatformService? = null
}
