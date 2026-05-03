package top.iwesley.lyn.music.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ArtworkCacheVersionRegistryTest {
    @Test
    fun `default artwork cache store version is zero`() = runBlocking {
        val store = object : ArtworkCacheStore {
            override suspend fun cache(locator: String, cacheKey: String, replaceExisting: Boolean): String? = locator
        }

        assertEquals(0L, store.observeVersion("album:source:album-1").first())
        assertNull(store.peekCachedTarget("album:source:album-1"))
    }

    @Test
    fun `registry bumps only matching cache key`() = runBlocking {
        val registry = ArtworkCacheVersionRegistry()

        assertEquals(0L, registry.observe("album:source:a").first())
        assertEquals(0L, registry.observe("album:source:b").first())

        registry.bump("album:source:a")
        registry.bump("album:source:a")

        assertEquals(2L, registry.observe("album:source:a").first())
        assertEquals(0L, registry.observe("album:source:b").first())
    }

    @Test
    fun `target registry stores and overwrites matching cache key only`() {
        val registry = ArtworkCachedTargetRegistry()
        val first = ArtworkCachedTarget(
            target = "/cache/a.jpg",
            version = "10:100",
            isLocalFile = true,
        )
        val second = ArtworkCachedTarget(
            target = "/cache/b.jpg",
            version = "12:200",
            isLocalFile = true,
        )

        assertNull(registry.peek("album:source:a"))

        registry.put("album:source:a", first)
        registry.put("album:source:b", first)
        registry.put("album:source:a", second)

        assertEquals(second, registry.peek("album:source:a"))
        assertEquals(second, registry.peek(" album:source:a "))
        assertEquals(first, registry.peek("album:source:b"))
        assertNull(registry.peek(""))
    }
}
