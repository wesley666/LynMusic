package top.iwesley.lyn.music.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

internal class TvUpnpRendererService : Service() {
    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        TvUpnpRendererRouter.start(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        TvUpnpRendererRouter.start(this)
        return START_STICKY
    }

    override fun onDestroy() {
        TvUpnpRendererRouter.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "投屏接收",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "保持电视端 DLNA 投屏接收可发现。"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle("LynMusic TV")
            .setContentText(notificationText())
            .setContentIntent(activityPendingIntent())
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    private fun notificationText(): String {
        return when (TvUpnpRendererRouter.state.value.route) {
            TvRendererRoute.Music -> "正在接收音乐投屏"
            TvRendererRoute.Video -> "正在接收视频投屏"
            null -> "正在等待投屏"
        }
    }

    private fun activityPendingIntent(): PendingIntent {
        val targetActivity = when (TvUpnpRendererRouter.state.value.route) {
            TvRendererRoute.Music -> MusicActivity::class.java
            TvRendererRoute.Video -> VideoActivity::class.java
            null -> MainActivity::class.java
        }
        val intent = Intent(this, targetActivity)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this,
            REQUEST_OPEN_ACTIVITY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val CHANNEL_ID = "lynmusic.tv.renderer"
        private const val NOTIFICATION_ID = 4207
        private const val REQUEST_OPEN_ACTIVITY = 4208
        private const val ACTION_START = "top.iwesley.lyn.music.tv.action.START_RENDERER"

        fun start(context: Context): Boolean {
            val intent = Intent(context, TvUpnpRendererService::class.java).setAction(ACTION_START)
            return runCatching {
                ContextCompat.startForegroundService(context, intent)
                true
            }.getOrDefault(false)
        }
    }
}
