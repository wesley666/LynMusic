package top.iwesley.lyn.music.mobile.ui.platform

import android.os.Build
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.toNavigationEvent

@Composable
internal actual fun PlatformMusicDetailBackInput(navigationVersion: Long) {
    if (Build.VERSION.SDK_INT < 34) return
    val activity = LocalOnBackPressedDispatcherOwner.current as? ComponentActivity ?: return
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher ?: return
    DisposableEffect(activity, dispatcher, navigationVersion) {
        // Registered after Activity's input, this owns the current content gesture. Replacing
        // the navigation version retires its callback, including callbacks retained by the OS.
        val input = MusicDetailBackInput(activity.onBackInvokedDispatcher)
        dispatcher.addInput(input, NavigationEventDispatcher.PRIORITY_DEFAULT)
        onDispose {
            input.cancelAndRetire()
            dispatcher.removeInput(input)
        }
    }
}

@RequiresApi(34)
private class MusicDetailBackInput(
    private val invoker: OnBackInvokedDispatcher,
) : NavigationEventInput() {
    private var callback: Callback? = null

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        if (hasEnabledHandlers && callback == null) {
            val registration = Callback()
            callback = registration
            invoker.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, registration)
        } else if (!hasEnabledHandlers) {
            cancelAndRetire()
        }
    }

    override fun onRemoved() {
        // The dispatcher is already detached here; disposal cancels before removing the input.
        retire(cancel = false)
    }

    fun cancelAndRetire() = retire(cancel = true)

    private fun retire(cancel: Boolean) {
        val registration = callback ?: return
        callback = null
        if (cancel && registration.inProgress) {
            registration.inProgress = false
            dispatchOnBackCancelled()
        }
        invoker.unregisterOnBackInvokedCallback(registration)
    }

    private inner class Callback : OnBackAnimationCallback {
        var inProgress = false

        override fun onBackStarted(backEvent: BackEvent) {
            if (callback !== this) return
            inProgress = true
            dispatchOnBackStarted(backEvent.toNavigationEvent())
        }

        override fun onBackProgressed(backEvent: BackEvent) {
            if (callback === this && inProgress) dispatchOnBackProgressed(backEvent.toNavigationEvent())
        }

        override fun onBackCancelled() {
            if (callback !== this || !inProgress) return
            inProgress = false
            dispatchOnBackCancelled()
        }

        override fun onBackInvoked() {
            if (callback !== this) return
            inProgress = false
            // Also support ordinary button returns that do not provide a started event.
            dispatchOnBackCompleted()
        }
    }
}
