package top.iwesley.lyn.music

import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import top.iwesley.lyn.music.core.model.ArtworkCachedTarget
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.NavidromeLocatorResolver
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.buildNavidromeCoverLocator

class ArtworkImageTargetTest {

    @Test
    fun `resolver uses project cached file and includes local file version in memory key`() = runBlocking {
        val cachedFile = Files.createTempFile("lynmusic-artwork-target", ".png").toFile()
        cachedFile.writeBytes(byteArrayOf(1, 2, 3))
        cachedFile.setLastModified(1_700_000_000_000L)
        val store = FakeArtworkCacheStore(cachedFile.absolutePath)

        val resolved = requireNotNull(
            resolveLynArtworkTarget(
                locator = "https://img.example.com/cover.png",
                cacheKey = "album:source:album-1",
                cacheRemote = true,
                artworkCacheStore = store,
            ),
        )
        val model = LynArtworkModel(
            locator = resolved.locator,
            cacheKey = "album:source:album-1",
            target = resolved.target,
            targetVersion = resolved.version,
            isLocalFileTarget = resolved.isLocalFile,
            cacheRemote = true,
            maxDecodeSizePx = ArtworkDecodeSize.Thumbnail,
            cacheVersion = 0L,
        )

        assertEquals(cachedFile.absolutePath, resolved.target)
        assertEquals("3:1700000000000", resolved.version)
        assertTrue(resolved.isLocalFile)
        assertEquals(listOf("https://img.example.com/cover.png" to "album:source:album-1"), store.requests)
        assertEquals(
            "lyn-artwork:album:source:album-1:256:3:1700000000000",
            lynArtworkMemoryCacheKey(model),
        )
        assertEquals(
            "lyn-artwork:album:source:album-1:256:3:1700000000000:v7",
            lynArtworkMemoryCacheKey(model.copy(cacheVersion = 7L)),
        )
    }

    @Test
    fun `local file version changes when file metadata changes`() = runBlocking {
        val cachedFile = Files.createTempFile("lynmusic-artwork-target-version", ".png").toFile()
        cachedFile.writeBytes(byteArrayOf(1, 2, 3))
        cachedFile.setLastModified(1_700_000_000_000L)
        val store = FakeArtworkCacheStore(cachedFile.absolutePath)

        val first = requireNotNull(resolveLynArtworkTarget("https://img.example.com/cover.png", null, true, store))
        cachedFile.writeBytes(byteArrayOf(1, 2, 3, 4))
        cachedFile.setLastModified(1_700_000_010_000L)
        val second = requireNotNull(resolveLynArtworkTarget("https://img.example.com/cover.png", null, true, store))

        assertNotEquals(first.version, second.version)
        assertEquals("4:1700000010000", second.version)
    }

    @Test
    fun `resolver falls back to original target when project cache fails`() = runBlocking {
        val store = FakeArtworkCacheStore(error = IllegalStateException("cache unavailable"))

        val resolved = requireNotNull(
            resolveLynArtworkTarget(
                locator = "https://img.example.com/cover.png",
                cacheKey = null,
                cacheRemote = true,
                artworkCacheStore = store,
            ),
        )

        assertEquals("https://img.example.com/cover.png", resolved.target)
        assertFalse(resolved.isLocalFile)
    }

    @Test
    fun `cache remote false skips project cache`() = runBlocking {
        val store = FakeArtworkCacheStore(error = IllegalStateException("should not be called"))

        val resolved = requireNotNull(
            resolveLynArtworkTarget(
                locator = "https://img.example.com/preview.png",
                cacheKey = null,
                cacheRemote = false,
                artworkCacheStore = store,
            ),
        )

        assertEquals("https://img.example.com/preview.png", resolved.target)
        assertEquals(emptyList(), store.requests)
    }

    @Test
    fun `navidrome passthrough cache target falls back to resolved cover url`() = runBlocking {
        val locator = buildNavidromeCoverLocator("nav-source", "cover-123")
        val coverUrl = "https://demo.example.com/rest/getCoverArt.view?id=cover-123"
        NavidromeLocatorRuntime.install(
            object : NavidromeLocatorResolver {
                override suspend fun resolveStreamUrl(
                    locator: String,
                    audioQuality: NavidromeAudioQuality,
                ): String? = null

                override suspend fun resolveCoverArtUrl(locator: String): String? = coverUrl
            },
        )

        val resolved = requireNotNull(
            resolveLynArtworkTarget(
                locator = locator,
                cacheKey = null,
                cacheRemote = true,
                artworkCacheStore = FakeArtworkCacheStore(target = locator),
            ),
        )

        assertEquals(locator, resolved.locator)
        assertEquals(coverUrl, resolved.target)
        assertFalse(resolved.isLocalFile)
    }

    @Test
    fun `file locator is versioned`() = runBlocking {
        val file = Files.createTempFile("lynmusic-artwork-local", ".png").toFile()
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        file.setLastModified(1_700_000_020_000L)

        val resolved = requireNotNull(
            resolveLynArtworkTarget(
                locator = file.toPath().absolutePathString(),
                cacheKey = null,
                cacheRemote = false,
                artworkCacheStore = FakeArtworkCacheStore(),
            ),
        )

        assertEquals(file.absolutePath, resolved.target)
        assertEquals("5:1700000020000", resolved.version)
        assertTrue(resolved.isLocalFile)
    }

    @Test
    fun `initial target uses cached album target before async resolve`() {
        val store = FakeArtworkCacheStore(
            cachedTarget = ArtworkCachedTarget(
                target = "/cache/artwork/album.png",
                version = "99:1700000030000",
                isLocalFile = true,
            ),
        )

        val initial = requireNotNull(
            initialLynArtworkTarget(
                normalized = "https://img.example.com/cover.png",
                requestCacheKey = "album:source:album-1",
                cacheRemote = true,
                artworkCacheStore = store,
            ),
        )

        assertEquals("https://img.example.com/cover.png", initial.locator)
        assertEquals("/cache/artwork/album.png", initial.target)
        assertEquals("99:1700000030000", initial.version)
        assertTrue(initial.isLocalFile)
        assertEquals(listOf("album:source:album-1"), store.peekRequests)
        assertEquals(emptyList(), store.requests)
    }

    @Test
    fun `initial target falls back to non remote locator when cache target is missing`() {
        val store = FakeArtworkCacheStore()

        val initial = requireNotNull(
            initialLynArtworkTarget(
                normalized = "/local/cover.png",
                requestCacheKey = "/local/cover.png",
                cacheRemote = false,
                artworkCacheStore = store,
            ),
        )

        assertEquals("/local/cover.png", initial.target)
        assertFalse(initial.isLocalFile)
    }

    @Test
    fun `initial target ignores project cached target when remote cache is disabled`() {
        val store = FakeArtworkCacheStore(
            cachedTarget = ArtworkCachedTarget(
                target = "/cache/artwork/album.png",
                version = "99:1700000030000",
                isLocalFile = true,
            ),
        )

        val initial = requireNotNull(
            initialLynArtworkTarget(
                normalized = "https://img.example.com/preview.png",
                requestCacheKey = "https://img.example.com/preview.png",
                cacheRemote = false,
                artworkCacheStore = store,
            ),
        )

        assertEquals("https://img.example.com/preview.png", initial.target)
        assertFalse(initial.isLocalFile)
        assertEquals(emptyList(), store.peekRequests)
    }
}

private class FakeArtworkCacheStore(
    private val target: String? = null,
    private val error: Throwable? = null,
    private val cachedTarget: ArtworkCachedTarget? = null,
) : ArtworkCacheStore {
    val requests = mutableListOf<Pair<String, String>>()
    val peekRequests = mutableListOf<String>()

    override suspend fun cache(locator: String, cacheKey: String, replaceExisting: Boolean): String? {
        requests += locator to cacheKey
        error?.let { throw it }
        return target ?: locator
    }

    override fun peekCachedTarget(cacheKey: String): ArtworkCachedTarget? {
        peekRequests += cacheKey
        return cachedTarget
    }
}
