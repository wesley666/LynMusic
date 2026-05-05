package top.iwesley.lyn.music.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import top.iwesley.lyn.music.cast.CastBackgroundRunSettingsOpener

class AndroidCastBackgroundRunSettingsOpener(
    context: Context,
) : CastBackgroundRunSettingsOpener {
    private val appContext = context.applicationContext

    override fun openSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${appContext.packageName}")
            },
        ) || requestIgnoreBatteryOptimizations()
    }

    private fun requestIgnoreBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${appContext.packageName}")
            },
        )
    }

    private fun startActivity(intent: Intent): Boolean {
        return runCatching {
            appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}
