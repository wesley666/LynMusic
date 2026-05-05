package top.iwesley.lyn.music.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

internal class AndroidCastProxyForegroundService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                START_NOT_STICKY
            }
            else -> {
                ensureChannel()
                startForeground(NOTIFICATION_ID, buildNotification())
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
                "投屏代理",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "为电视投屏提供本地音乐代理。"
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("LynMusic")
            .setContentText("正在为电视投屏当前歌曲")
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "lynmusic.cast.proxy"
        private const val ACTION_START = "top.iwesley.lyn.music.action.START_CAST_PROXY"
        private const val ACTION_STOP = "top.iwesley.lyn.music.action.STOP_CAST_PROXY"
        private const val NOTIFICATION_ID = 4107

        fun start(context: Context) {
            val intent = Intent(context, AndroidCastProxyForegroundService::class.java)
                .setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AndroidCastProxyForegroundService::class.java)
                .setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
            runCatching { context.stopService(Intent(context, AndroidCastProxyForegroundService::class.java)) }
        }
    }
}
