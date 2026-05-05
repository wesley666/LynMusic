package top.iwesley.lyn.music.platform

import android.app.Service
import android.os.Build

internal fun Service.stopForegroundCompat(removeNotification: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        stopForeground(
            if (removeNotification) {
                Service.STOP_FOREGROUND_REMOVE
            } else {
                Service.STOP_FOREGROUND_DETACH
            },
        )
    } else {
        @Suppress("DEPRECATION")
        stopForeground(removeNotification)
    }
}
