package top.iwesley.lyn.music.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal class TvRendererBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> TvUpnpRendererService.start(context)
        }
    }
}
