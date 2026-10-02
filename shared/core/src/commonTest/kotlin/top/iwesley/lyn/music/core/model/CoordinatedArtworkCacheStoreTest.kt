package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CoordinatedArtworkCacheStoreTest {
    @Test
    fun `cache hit never prepares remote payload`() = runTest {
        val backend = Backend().apply { entries["album"] = "existing" }
        val store = CoordinatedArtworkCacheStore(backend, StandardTestDispatcher(testScheduler))
        assertEquals("existing", store.cache("unreachable", "album")?.locator)
        assertEquals(0, backend.prepares)
        assertEquals(0L, store.observeVersion("album").first())
    }

    @Test
    fun `slow download does not hold lock even for colliding keys`() = runTest {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val gate = CompletableDeferred<Unit>()
        val backend = Backend().apply {
            gates["slow"] = gate
            entries["BB"] = "cached"
        }
        val store = CoordinatedArtworkCacheStore(backend, StandardTestDispatcher(testScheduler))
        val slow = async { store.cache("slow", "Aa")?.locator }
        runCurrent()
        assertFalse(slow.isCompleted)
        assertEquals("cached", store.cache("offline", "BB")?.locator)
        gate.complete(Unit)
        assertEquals("slow", slow.await())
    }

    @Test
    fun `late automatic payload cannot overwrite newer real artwork`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = Backend().apply {
            entries["album"] = "placeholder"
            gates["automatic"] = gate
        }
        val store = CoordinatedArtworkCacheStore(backend, StandardTestDispatcher(testScheduler))
        val automatic = async { store.cache("automatic", "album", policy = ArtworkWritePolicy.MissingOrPlaceholder)?.locator }
        runCurrent()
        assertEquals("manual", store.cache("manual", "album", policy = ArtworkWritePolicy.Replace)?.locator)
        gate.complete(Unit)
        assertEquals("manual", automatic.await())
        assertEquals("manual", backend.entries["album"])
        assertEquals(1L, store.observeVersion("album").first())
    }

    @Test
    fun `late keep existing payload uses result already committed by another request`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = Backend().apply { gates["slow"] = gate }
        val store = CoordinatedArtworkCacheStore(backend, StandardTestDispatcher(testScheduler))
        val slow = async { store.cache("slow", "album")?.locator }
        runCurrent()
        assertEquals("fast", store.cache("fast", "album")?.locator)
        gate.complete(Unit)
        assertEquals("fast", slow.await())
        assertEquals(listOf("fast"), backend.commits)
    }

    @Test
    fun `cancelled non cooperative download never commits or increments version`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = Backend().apply { gates["slow"] = gate }
        val store = CoordinatedArtworkCacheStore(backend, StandardTestDispatcher(testScheduler))
        val slow = async { store.cache("slow", "album")?.locator }
        runCurrent()
        slow.cancel()
        gate.complete(Unit)
        runCurrent()
        assertTrue(slow.isCancelled)
        assertTrue(backend.commits.isEmpty())
        assertEquals(0L, store.observeVersion("album").first())
    }

    @Test
    fun `failed commit does not publish cache version`() = runTest {
        val backend = Backend().apply { failCommit = true }
        val store = CoordinatedArtworkCacheStore(backend, StandardTestDispatcher(testScheduler))
        assertNull(store.cache("new", "album")?.locator)
        assertEquals(0L, store.observeVersion("album").first())
    }

    @Test
    fun `automatic policy creates missing artwork and preserves real artwork`() = runTest {
        val backend = Backend().apply { entries["real"] = "real-cover" }
        val store = CoordinatedArtworkCacheStore(backend, StandardTestDispatcher(testScheduler))
        assertEquals("new", store.cache("new", "missing", policy = ArtworkWritePolicy.MissingOrPlaceholder)?.locator)
        assertEquals("real-cover", store.cache("new", "real", policy = ArtworkWritePolicy.MissingOrPlaceholder)?.locator)
        assertEquals(1, backend.prepares)
        assertEquals(listOf(true), backend.legacyReads)
    }

    @Test
    fun `automatic payload replaces placeholder committed during download`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = Backend().apply { gates["automatic"] = gate }
        val store = CoordinatedArtworkCacheStore(backend, StandardTestDispatcher(testScheduler))
        val automatic = async { store.cache("automatic", "album", policy = ArtworkWritePolicy.MissingOrPlaceholder)?.locator }
        runCurrent()
        assertFalse(automatic.isCompleted)
        assertEquals("placeholder", store.cache("placeholder", "album")?.locator)
        gate.complete(Unit)
        assertEquals("automatic", automatic.await())
        assertEquals("automatic", backend.entries["album"])
        assertEquals(2L, store.observeVersion("album").first())
    }

    @Test
    fun `automatic payload preserves real cover committed into initially empty cache`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = Backend().apply { gates["automatic"] = gate }
        val store = CoordinatedArtworkCacheStore(backend, StandardTestDispatcher(testScheduler))
        val automatic = async { store.cache("automatic", "album", policy = ArtworkWritePolicy.MissingOrPlaceholder)?.locator }
        runCurrent()
        assertEquals("manual", store.cache("manual", "album", policy = ArtworkWritePolicy.Replace)?.locator)
        gate.complete(Unit)
        assertEquals("manual", automatic.await())
        assertEquals(listOf("manual"), backend.commits)
        assertEquals(1L, store.observeVersion("album").first())
    }

    @Test
    fun `result reports writes and cache hits without classifying keep existing artwork`() = runTest {
        val backend = Backend()
        val store = CoordinatedArtworkCacheStore(backend, StandardTestDispatcher(testScheduler))
        assertEquals(ArtworkCacheResult("cover", true), store.cache("cover", "album"))
        assertEquals(ArtworkCacheResult("cover", false), store.cache("other", "album"))
        assertTrue(backend.findChecks.all { !it })
    }

    @Test
    fun `placeholder classification occurs once per locked check`() = runTest {
        val backend = Backend().apply { entries["album"] = "placeholder" }
        val store = CoordinatedArtworkCacheStore(backend, StandardTestDispatcher(testScheduler))
        assertEquals(ArtworkCacheResult("cover", true), store.cache("cover", "album", ArtworkWritePolicy.MissingOrPlaceholder))
        assertEquals(listOf(true, true), backend.findChecks)
    }

    @Test
    fun `ordinary backend failure is logged and cancellation is propagated`() = runTest {
        val backend = Backend()
        val failures = mutableListOf<Throwable?>()
        val logger = object : DiagnosticLogger {
            override fun log(level: DiagnosticLogLevel, tag: String, message: String, throwable: Throwable?) { failures += throwable }
        }
        val store = CoordinatedArtworkCacheStore(backend, StandardTestDispatcher(testScheduler), logger)
        val failure = IllegalStateException("download failed")
        backend.prepareFailure = failure
        assertNull(store.cache("cover", "album"))
        assertEquals(listOf<Throwable?>(failure), failures)
        backend.prepareFailure = kotlinx.coroutines.CancellationException("cancelled")
        kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> { store.cache("cover", "album") }
        assertEquals(1, failures.size)
    }

    @Test
    fun `invalid local payload returns source path without committing or publishing version`() = runTest {
        val backend = Backend().apply {
            prepared = PreparedArtwork("file:///cover.jpg", byteArrayOf(1, 2, 3), sourcePath = "/cover.jpg")
        }
        val store = CoordinatedArtworkCacheStore(backend, StandardTestDispatcher(testScheduler))
        assertEquals(ArtworkCacheResult("/cover.jpg", false), store.cache("file:///cover.jpg", "album"))
        assertEquals(0, backend.commitAttempts)
        assertTrue(backend.entries.isEmpty())
        assertEquals(0L, store.observeVersion("album").first())
    }

    @Test
    fun `invalid remote payload has no local fallback`() = runTest {
        val backend = Backend().apply { prepared = PreparedArtwork("https://cover/image", byteArrayOf(1, 2, 3)) }
        val store = CoordinatedArtworkCacheStore(backend, StandardTestDispatcher(testScheduler))
        assertNull(store.cache("https://cover/image", "album"))
        assertEquals(0, backend.commitAttempts)
        assertTrue(backend.entries.isEmpty())
        assertEquals(0L, store.observeVersion("album").first())
    }

    @Test
    fun `local commit failure falls back without version change`() = runTest {
        val backend = Backend().apply {
            prepared = PreparedArtwork("local", byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte()), sourcePath = "/cover.jpg")
            failCommit = true
        }
        val store = CoordinatedArtworkCacheStore(backend, StandardTestDispatcher(testScheduler), NoopDiagnosticLogger)
        assertEquals(ArtworkCacheResult("/cover.jpg", false), store.cache("local", "album"))
        backend.failCommit = false
        backend.commitFailure = IllegalStateException("disk unavailable")
        assertEquals(ArtworkCacheResult("/cover.jpg", false), store.cache("local", "album"))
        assertEquals(2, backend.commitAttempts)
        assertTrue(backend.entries.isEmpty())
        assertEquals(0L, store.observeVersion("album").first())
    }

    @Test
    fun `local commit cancellation is propagated instead of returning fallback`() = runTest {
        val backend = Backend().apply {
            prepared = PreparedArtwork("local", byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte()), sourcePath = "/cover.jpg")
            commitFailure = kotlinx.coroutines.CancellationException("cancelled")
        }
        val store = CoordinatedArtworkCacheStore(backend, StandardTestDispatcher(testScheduler))
        kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> { store.cache("local", "album") }
        assertTrue(backend.entries.isEmpty())
        assertEquals(0L, store.observeVersion("album").first())
    }

    @Test
    fun `invalid local fallback still respects real cover committed during preparation`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val backend = Backend().apply {
            prepared = PreparedArtwork("local", byteArrayOf(1, 2, 3), sourcePath = "/cover.jpg")
            gates["local"] = gate
        }
        val store = CoordinatedArtworkCacheStore(backend, StandardTestDispatcher(testScheduler))
        val result = async { store.cache("local", "album", ArtworkWritePolicy.MissingOrPlaceholder) }
        runCurrent()
        backend.entries["album"] = "real-cover"
        gate.complete(Unit)
        assertEquals(ArtworkCacheResult("real-cover", false), result.await())
        assertEquals(0, backend.commitAttempts)
        assertEquals(0L, store.observeVersion("album").first())
    }

    private class Backend : ArtworkCacheBackend {
        val entries = mutableMapOf<String, String>()
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        val commits = mutableListOf<String>()
        val legacyReads = mutableListOf<Boolean>()
        val findChecks = mutableListOf<Boolean>()
        var prepareFailure: Exception? = null
        var prepared: PreparedArtwork? = null
        var commitFailure: Exception? = null
        var commitAttempts = 0
        var prepares = 0
        var failCommit = false
        override suspend fun find(cacheKey: String, detectPlaceholder: Boolean): ArtworkCacheEntry? {
            findChecks += detectPlaceholder
            return entries[cacheKey]?.let { ArtworkCacheEntry(it, detectPlaceholder && it == "placeholder") }
        }
        override suspend fun prepare(locator: String, cacheKey: String, allowLegacy: Boolean): PreparedArtwork {
            prepareFailure?.let { throw it }
            prepares++
            legacyReads += allowLegacy
            gates[locator]?.let { withContext(NonCancellable) { it.await() } }
            return prepared ?: PreparedArtwork(locator, byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte()))
        }
        override suspend fun commit(cacheKey: String, artwork: PreparedArtwork, replaceExisting: Boolean): ArtworkCacheCommit? {
            commitAttempts++
            commitFailure?.let { throw it }
            if (failCommit) return null
            entries[cacheKey] = artwork.locator
            commits += artwork.locator
            return ArtworkCacheCommit(artwork.locator, changed = true)
        }
        override fun peek(cacheKey: String): ArtworkCachedTarget? = null
    }
}
