package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.ArtworkWritePolicy
import top.iwesley.lyn.music.core.model.ArtworkCacheResult

import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import top.iwesley.lyn.music.core.model.ArtworkCachedTarget
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.NavidromeLocatorResolver
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.RemotePlaybackUrlCandidate
import top.iwesley.lyn.music.core.model.buildFnMusicCoverLocator
import top.iwesley.lyn.music.core.model.buildIosArtworkCacheLocator
import top.iwesley.lyn.music.core.model.buildNavidromeCoverLocator
import top.iwesley.lyn.music.core.model.parseFnMusicCoverLocator

class ArtworkImageTargetTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }


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
            targetPending = false,
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
    fun `a resolver that fails costs the image instead of failing the composition`() = runBlocking {
        val coverLookups = mutableListOf<String>()
        NavidromeLocatorRuntime.install(
            object : NavidromeLocatorResolver {
                override suspend fun resolveStreamUrl(
                    locator: String,
                    audioQuality: NavidromeAudioQuality,
                ): String? = null

                override suspend fun resolveCoverArtUrl(locator: String): String? {
                    coverLookups += locator
                    throw java.net.UnknownHostException("Unable to resolve host \"5ddd.com\"")
                }

                override suspend fun resolveCoverArtUrlCandidates(locator: String): List<RemotePlaybackUrlCandidate>? {
                    coverLookups += locator
                    throw java.net.UnknownHostException("Unable to resolve host \"5ddd.com\"")
                }
            },
        )
        // Offline: the cache cannot download either, so resolution falls through to the resolver.
        val offlineCache = FakeArtworkCacheStore(error = java.io.IOException("offline"))

        try {
            for (locator in listOf(buildNavidromeCoverLocator("nav-source", "cover-1"), buildFnMusicCoverLocator("fn-source", "cover-2"))) {
                assertNull(
                    resolveLynArtworkTarget(
                        locator = locator,
                        cacheKey = null,
                        cacheRemote = true,
                        artworkCacheStore = offlineCache,
                    ),
                )
            }
            assertTrue(coverLookups.isNotEmpty())
            // The FN Music cover never reached the resolver at all.
            assertTrue(coverLookups.none { parseFnMusicCoverLocator(it) != null })
        } finally {
            NavidromeLocatorRuntime.install(
                object : NavidromeLocatorResolver {
                    override suspend fun resolveStreamUrl(locator: String, audioQuality: NavidromeAudioQuality): String? = null
                    override suspend fun resolveCoverArtUrl(locator: String): String? = null
                },
            )
        }
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
    fun `absolute local artwork path is normalized to readable file uri for coil`() {
        val file = Files.createTempFile("LynMusic 封面 #", ".png").toFile()
        file.writeBytes(byteArrayOf(1, 2, 3))

        val data = coilArtworkData(file.absolutePath)
        val resolvedPath = Paths.get(URI(data))

        assertTrue(data.startsWith("file:", ignoreCase = true))
        assertEquals(file.canonicalFile, resolvedPath.toFile().canonicalFile)
    }

    @Test
    fun `legacy windows file url is normalized to readable file uri for coil`() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) return
        val file = Files.createTempFile("LynMusic legacy artwork", ".png").toFile()
        file.writeBytes(byteArrayOf(1, 2, 3))
        val legacyFileUrl = "file://${file.absolutePath}"

        val data = coilArtworkData(legacyFileUrl)
        val resolvedPath = Paths.get(URI(data))

        assertTrue(data.startsWith("file:", ignoreCase = true))
        assertEquals(file.canonicalFile, resolvedPath.toFile().canonicalFile)
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
    fun `initial target defers ios artwork cache locator to platform resolver`() {
        val locator = requireNotNull(buildIosArtworkCacheLocator("f358180aff319859.jpg"))

        val initial = initialLynArtworkTarget(
            normalized = locator,
            requestCacheKey = "album:source:album-1",
            cacheRemote = true,
            artworkCacheStore = FakeArtworkCacheStore(),
        )

        assertEquals(null, initial)
    }

    @Test
    fun `initial target defers legacy ios artwork cache path to platform resolver`() {
        val legacyPath =
            "/var/mobile/Containers/Data/Application/OLD/Library/Caches/lynmusic-artwork-cache/f358180aff319859.jpg"

        val initial = initialLynArtworkTarget(
            normalized = legacyPath,
            requestCacheKey = "album:source:album-1",
            cacheRemote = false,
            artworkCacheStore = FakeArtworkCacheStore(),
        )

        assertEquals(null, initial)
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

    override suspend fun cache(locator: String, cacheKey: String, policy: ArtworkWritePolicy): ArtworkCacheResult? {
        requests += locator to cacheKey
        error?.let { throw it }
        return ArtworkCacheResult(target ?: locator, true)
    }

    override fun peekCachedTarget(cacheKey: String): ArtworkCachedTarget? {
        peekRequests += cacheKey
        return cachedTarget
    }
}
