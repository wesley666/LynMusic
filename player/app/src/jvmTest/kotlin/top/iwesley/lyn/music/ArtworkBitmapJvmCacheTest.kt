package top.iwesley.lyn.music

import java.io.File
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import top.iwesley.lyn.music.core.model.NavidromeLocatorResolver
import top.iwesley.lyn.music.core.model.NavidromeLocatorRuntime
import top.iwesley.lyn.music.core.model.buildNavidromeCoverLocator
import top.iwesley.lyn.music.core.model.stableArtworkCacheHash
import top.iwesley.lyn.music.platform.loadBundledDefaultCoverBytes
import top.iwesley.lyn.music.platform.loadJvmArtworkBytes

class ArtworkBitmapJvmCacheTest {

    @Test
    fun `direct remote artwork writes cache reuses it and rebuilds after deletion`() {
        val temporaryUserHome = Files.createTempDirectory("lynmusic-player-artwork-home")
        val payload = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
            0x01,
        )
        val locator = "https://img.example.com/cover"
        var requestCount = 0

        val first = runBlocking {
            loadJvmArtworkBytes(locator, userHomePath = temporaryUserHome.absolutePathString()) { target ->
                requestCount += 1
                assertEquals(locator, target)
                payload
            }
        }
        val cacheDirectory = File(temporaryUserHome.toFile(), ".lynmusic/artwork-cache")
        val firstCacheFile = assertNotNull(cacheDirectory.listFiles()?.singleOrNull())

        assertContentEquals(payload, first)
        assertEquals(1, requestCount)
        assertTrue(firstCacheFile.name.startsWith(locator.stableArtworkCacheHash()))
        assertTrue(firstCacheFile.name.endsWith(".png"))

        val second = runBlocking {
            loadJvmArtworkBytes(locator, userHomePath = temporaryUserHome.absolutePathString()) { target ->
                requestCount += 1
                assertEquals(locator, target)
                payload
            }
        }

        assertContentEquals(payload, second)
        assertEquals(1, requestCount)

        firstCacheFile.delete()

        val third = runBlocking {
            loadJvmArtworkBytes(locator, userHomePath = temporaryUserHome.absolutePathString()) { target ->
                requestCount += 1
                assertEquals(locator, target)
                payload
            }
        }
        val rebuiltCacheFile = assertNotNull(cacheDirectory.listFiles()?.singleOrNull())

        assertContentEquals(payload, third)
        assertEquals(2, requestCount)
        assertTrue(rebuiltCacheFile.name.startsWith(locator.stableArtworkCacheHash()))
    }

    @Test
    fun `navidrome artwork caches by original locator hash`() {
        val temporaryUserHome = Files.createTempDirectory("lynmusic-player-navidrome-artwork-home")
        val payload = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
            0x02,
        )
        val locator = buildNavidromeCoverLocator("nav-source", "cover-123")
        val actualUrl = "https://demo.example.com/rest/getCoverArt.view?id=cover-123"
        NavidromeLocatorRuntime.install(
            object : NavidromeLocatorResolver {
                override suspend fun resolveStreamUrl(locator: String): String? = null

                override suspend fun resolveCoverArtUrl(locator: String): String? = actualUrl
            },
        )

        val resolved = runBlocking {
            loadJvmArtworkBytes(locator, userHomePath = temporaryUserHome.absolutePathString()) { target ->
                assertEquals(actualUrl, target)
                payload
            }
        }
        val cacheDirectory = File(temporaryUserHome.toFile(), ".lynmusic/artwork-cache")
        val cacheFile = assertNotNull(cacheDirectory.listFiles()?.singleOrNull())

        assertContentEquals(payload, resolved)
        assertTrue(cacheFile.name.startsWith(locator.stableArtworkCacheHash()))
    }

    @Test
    fun `preview mode downloads remote artwork without writing cache`() {
        val temporaryUserHome = Files.createTempDirectory("lynmusic-player-preview-artwork-home")
        val payload = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
            0x03,
        )
        val locator = "https://img.example.com/preview"
        var requestCount = 0

        val first = runBlocking {
            loadJvmArtworkBytes(locator, cacheRemote = false, userHomePath = temporaryUserHome.absolutePathString()) {
                requestCount += 1
                payload
            }
        }
        val second = runBlocking {
            loadJvmArtworkBytes(locator, cacheRemote = false, userHomePath = temporaryUserHome.absolutePathString()) {
                requestCount += 1
                payload
            }
        }
        val cacheDirectory = File(temporaryUserHome.toFile(), ".lynmusic/artwork-cache")

        assertContentEquals(payload, first)
        assertContentEquals(payload, second)
        assertEquals(2, requestCount)
        assertTrue(cacheDirectory.listFiles().isNullOrEmpty())
    }

    @Test
    fun `missing locator falls back to bundled default cover`() {
        val expected = runBlocking { loadBundledDefaultCoverBytes() }
        val actual = runBlocking {
            loadJvmArtworkBytes(null) {
                error("missing locator should not trigger remote fetch")
            }
        }

        assertNotNull(expected)
        assertContentEquals(expected, actual)
    }

    @Test
    fun `invalid local artwork falls back to bundled default cover`() {
        val expected = runBlocking { loadBundledDefaultCoverBytes() }
        val actual = runBlocking {
            loadJvmArtworkBytes("file:///definitely-missing-cover.png") {
                error("invalid local path should not trigger remote fetch")
            }
        }

        assertNotNull(expected)
        assertContentEquals(expected, actual)
    }
}
