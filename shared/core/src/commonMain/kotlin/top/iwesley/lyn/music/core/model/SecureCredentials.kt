package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun SecureCredentialStore.withSecureInMemoryCache(): SecureCredentialStore {
    return this as? MemoryCachedSecureCredentialStore ?: MemoryCachedSecureCredentialStore(this)
}

internal class MemoryCachedSecureCredentialStore(
    private val delegate: SecureCredentialStore,
) : SecureCredentialStore {
    private val lock = Mutex()
    private val cachedValues = linkedMapOf<String, CachedSecretBytes>()

    override suspend fun put(key: String, value: String) {
        lock.withLock {
            delegate.put(key, value)
            cachedValues.remove(key)?.clear()
            cachedValues[key] = CachedSecretBytes(value.encodeToByteArray())
        }
    }

    override suspend fun get(key: String): String? {
        return lock.withLock {
            cachedValues[key]?.copyOutAsString()?.let { return@withLock it }
            val value = delegate.get(key) ?: return@withLock null
            cachedValues.remove(key)?.clear()
            cachedValues[key] = CachedSecretBytes(value.encodeToByteArray())
            value
        }
    }

    override suspend fun remove(key: String) {
        lock.withLock {
            delegate.remove(key)
            cachedValues.remove(key)?.clear()
        }
    }

    internal suspend fun cachedValueForTest(key: String): CachedSecretBytes? = lock.withLock { cachedValues[key] }
}

internal class CachedSecretBytes(
    private var bytes: ByteArray,
) {
    fun copyOutAsString(): String = bytes.decodeToString()

    fun clear() {
        for (index in bytes.indices) {
            bytes[index] = 0
        }
        bytes = ByteArray(0)
    }

    internal fun isClearedForTest(): Boolean = bytes.isEmpty()
}
