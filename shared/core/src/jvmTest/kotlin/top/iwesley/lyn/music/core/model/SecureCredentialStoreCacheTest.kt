package top.iwesley.lyn.music.core.model

import java.util.ArrayDeque
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class SecureCredentialStoreCacheTest {

    @Test
    fun `same key is cached after first read`() = runBlocking {
        val delegate = RecordingSecureCredentialStore(
            initialValues = linkedMapOf("nav-cred" to "plain-pass"),
        )
        val store = delegate.withSecureInMemoryCache()

        assertEquals("plain-pass", store.get("nav-cred"))
        assertEquals("plain-pass", store.get("nav-cred"))

        assertEquals(1, delegate.getCount("nav-cred"))
    }

    @Test
    fun `different keys maintain independent caches`() = runBlocking {
        val delegate = RecordingSecureCredentialStore(
            initialValues = linkedMapOf(
                "nav-cred" to "nav-pass",
                "dav-cred" to "dav-pass",
            ),
        )
        val store = delegate.withSecureInMemoryCache()

        assertEquals("nav-pass", store.get("nav-cred"))
        assertEquals("dav-pass", store.get("dav-cred"))
        assertEquals("nav-pass", store.get("nav-cred"))
        assertEquals("dav-pass", store.get("dav-cred"))

        assertEquals(1, delegate.getCount("nav-cred"))
        assertEquals(1, delegate.getCount("dav-cred"))
    }

    @Test
    fun `put refreshes cache without extra reads`() = runBlocking {
        val delegate = RecordingSecureCredentialStore(
            initialValues = linkedMapOf("nav-cred" to "old-pass"),
        )
        val store = delegate.withSecureInMemoryCache() as MemoryCachedSecureCredentialStore

        assertEquals("old-pass", store.get("nav-cred"))
        val previous = requireNotNull(store.cachedValueForTest("nav-cred"))

        store.put("nav-cred", "new-pass")

        assertTrue(previous.isClearedForTest())
        assertEquals("new-pass", store.get("nav-cred"))
        assertEquals(1, delegate.getCount("nav-cred"))
        assertEquals(1, delegate.putCount("nav-cred"))
    }

    @Test
    fun `remove clears cache and next read goes back to delegate`() = runBlocking {
        val delegate = RecordingSecureCredentialStore(
            initialValues = linkedMapOf("nav-cred" to "plain-pass"),
        )
        val store = delegate.withSecureInMemoryCache() as MemoryCachedSecureCredentialStore

        assertEquals("plain-pass", store.get("nav-cred"))
        val previous = requireNotNull(store.cachedValueForTest("nav-cred"))

        store.remove("nav-cred")
        delegate.set("nav-cred", "restored-pass")

        assertTrue(previous.isClearedForTest())
        assertEquals("restored-pass", store.get("nav-cred"))
        assertEquals(2, delegate.getCount("nav-cred"))
        assertEquals(1, delegate.removeCount("nav-cred"))
    }

    @Test
    fun `null reads are not cached`() = runBlocking {
        val delegate = RecordingSecureCredentialStore()
        val store = delegate.withSecureInMemoryCache()

        assertEquals(null, store.get("missing"))
        assertEquals(null, store.get("missing"))

        assertEquals(2, delegate.getCount("missing"))
    }

    @Test
    fun `failed reads are not cached and later recovery still works`() = runBlocking {
        val delegate = RecordingSecureCredentialStore()
        delegate.scriptGet(
            "nav-cred",
            ReadStep.Failure(IllegalStateException("keystore unavailable")),
            ReadStep.Value("plain-pass"),
        )
        val store = delegate.withSecureInMemoryCache()

        assertFailsWith<IllegalStateException> {
            store.get("nav-cred")
        }
        assertEquals("plain-pass", store.get("nav-cred"))
        assertEquals("plain-pass", store.get("nav-cred"))

        assertEquals(2, delegate.getCount("nav-cred"))
    }

    @Test
    fun `clear zeroes underlying bytes`() {
        val bytes = "plain-pass".encodeToByteArray()
        val cached = CachedSecretBytes(bytes)

        cached.clear()

        assertContentEquals(ByteArray(bytes.size), bytes)
        assertTrue(cached.isClearedForTest())
    }
}

private class RecordingSecureCredentialStore(
    initialValues: Map<String, String> = emptyMap(),
) : SecureCredentialStore {
    private val values = linkedMapOf<String, String>().apply { putAll(initialValues) }
    private val getCounts = linkedMapOf<String, Int>()
    private val putCounts = linkedMapOf<String, Int>()
    private val removeCounts = linkedMapOf<String, Int>()
    private val scriptedReads = linkedMapOf<String, ArrayDeque<ReadStep>>()

    override suspend fun put(key: String, value: String) {
        putCounts[key] = putCount(key) + 1
        values[key] = value
    }

    override suspend fun get(key: String): String? {
        getCounts[key] = getCount(key) + 1
        val scripted = scriptedReads[key]
        if (scripted != null && scripted.isNotEmpty()) {
            return when (val next = scripted.removeFirst()) {
                is ReadStep.Value -> next.value
                is ReadStep.Failure -> throw next.throwable
            }
        }
        return values[key]
    }

    override suspend fun remove(key: String) {
        removeCounts[key] = removeCount(key) + 1
        values.remove(key)
    }

    fun scriptGet(key: String, vararg steps: ReadStep) {
        scriptedReads[key] = ArrayDeque(steps.toList())
    }

    fun set(key: String, value: String) {
        values[key] = value
    }

    fun getCount(key: String): Int = getCounts[key] ?: 0

    fun putCount(key: String): Int = putCounts[key] ?: 0

    fun removeCount(key: String): Int = removeCounts[key] ?: 0
}

private sealed interface ReadStep {
    data class Value(val value: String?) : ReadStep
    data class Failure(val throwable: Throwable) : ReadStep
}
