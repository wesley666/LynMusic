package top.iwesley.lyn.music.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.iwesley.lyn.music.cast.CastNotificationPermissionRequester
import kotlin.coroutines.resume

class AndroidCastNotificationPermissionRequester(
    activity: ComponentActivity,
) : CastNotificationPermissionRequester {
    private val context: Context = activity.applicationContext
    private val mutex = Mutex()
    private var pendingContinuation: kotlin.coroutines.Continuation<Boolean>? = null
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        pendingContinuation?.resume(granted)
        pendingContinuation = null
    }

    override fun isRequestNeeded(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()
    }

    override suspend fun requestIfNeeded(): Boolean {
        if (!isRequestNeeded()) return true
        return mutex.withLock {
            if (!isRequestNeeded()) {
                return@withLock true
            }
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    pendingContinuation = continuation
                    continuation.invokeOnCancellation {
                        if (pendingContinuation === continuation) {
                            pendingContinuation = null
                        }
                    }
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun hasNotificationPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return false
    }
}
