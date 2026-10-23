package top.iwesley.lyn.music

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class StartupRestoration<T>(val result: T, val autoOpenRequested: Boolean)

/** A host-lifecycle gate; it never changes playback or presentation state itself. */
class StartupAutoOpenGate {
    private val mutex = Mutex()
    private var requestedOnStartup: Boolean? = null
    private var completed = false

    suspend fun <T> restore(requested: Boolean, hydrate: suspend () -> T): StartupRestoration<T> = mutex.withLock {
        val initialRequest = requestedOnStartup ?: requested.also { requestedOnStartup = it }
        val result = hydrate()
        val outcome = StartupRestoration(result, initialRequest && !completed)
        completed = true
        outcome
    }
}
