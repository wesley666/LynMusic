package top.iwesley.lyn.music.platform

import java.security.SecureRandom
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AndroidCastProxySessionRegistry(
    private val onEmpty: suspend () -> Unit,
) {
    private val mutex = Mutex()
    private val random = SecureRandom()
    private val entries = mutableMapOf<String, AndroidCastProxySessionEntry>()

    suspend fun register(resource: AndroidCastProxyResource): AndroidCastProxySessionEntry {
        return mutex.withLock {
            val token = generateToken()
            val entry = AndroidCastProxySessionEntry(token = token, resource = resource)
            entries[token] = entry
            entry
        }
    }

    suspend fun get(token: String): AndroidCastProxySessionEntry? {
        return mutex.withLock { entries[token] }
    }

    suspend fun remove(token: String) {
        val shouldStop = mutex.withLock {
            entries.remove(token)?.resource?.close()
            entries.isEmpty()
        }
        if (shouldStop) {
            onEmpty()
        }
    }

    suspend fun clear() {
        val shouldStop = mutex.withLock {
            entries.values.forEach { entry -> entry.resource.close() }
            val hadEntries = entries.isNotEmpty()
            entries.clear()
            hadEntries
        }
        if (shouldStop) {
            onEmpty()
        }
    }

    suspend fun isEmpty(): Boolean = mutex.withLock { entries.isEmpty() }

    private fun generateToken(): String {
        while (true) {
            val bytes = ByteArray(TOKEN_BYTES)
            random.nextBytes(bytes)
            val token = bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
            if (!entries.containsKey(token)) return token
        }
    }
}

internal data class AndroidCastProxySessionEntry(
    val token: String,
    val resource: AndroidCastProxyResource,
)

private const val TOKEN_BYTES = 24
