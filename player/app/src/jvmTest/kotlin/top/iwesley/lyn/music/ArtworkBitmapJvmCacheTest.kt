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
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
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
        val payload = completePngPayload(0x01)
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
        val payload = completePngPayload(0x02)
        val locator = buildNavidromeCoverLocator("nav-source", "cover-123")
        val actualUrl = "https://demo.example.com/rest/getCoverArt.view?id=cover-123"
        NavidromeLocatorRuntime.install(
            object : NavidromeLocatorResolver {
                override suspend fun resolveStreamUrl(
                    locator: String,
                    audioQuality: NavidromeAudioQuality,
                ): String? = null

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
        val payload = completePngPayload(0x03)
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
    fun `remote artwork with incomplete payload falls back without writing cache`() {
        val temporaryUserHome = Files.createTempDirectory("lynmusic-player-invalid-artwork-home")
        val expected = runBlocking { loadBundledDefaultCoverBytes() }
        val locator = "https://img.example.com/truncated"

        val actual = runBlocking {
            loadJvmArtworkBytes(locator, userHomePath = temporaryUserHome.absolutePathString()) {
                truncatedPngPayload()
            }
        }
        val cacheDirectory = File(temporaryUserHome.toFile(), ".lynmusic/artwork-cache")

        assertNotNull(expected)
        assertContentEquals(expected, actual)
        assertTrue(cacheDirectory.listFiles().isNullOrEmpty())
    }

    @Test
    fun `damaged cache file is ignored and rebuilt`() {
        val temporaryUserHome = Files.createTempDirectory("lynmusic-player-damaged-artwork-home")
        val payload = completePngPayload(0x04)
        val locator = "https://img.example.com/damaged"
        val cacheDirectory = File(temporaryUserHome.toFile(), ".lynmusic/artwork-cache").apply { mkdirs() }
        File(cacheDirectory, "${locator.stableArtworkCacheHash()}.png").writeBytes(truncatedPngPayload())
        var requestCount = 0

        val actual = runBlocking {
            loadJvmArtworkBytes(locator, userHomePath = temporaryUserHome.absolutePathString()) {
                requestCount += 1
                payload
            }
        }
        val cacheFile = assertNotNull(
            cacheDirectory.listFiles()?.singleOrNull { file -> !file.name.contains(".tmp-") },
        )

        assertContentEquals(payload, actual)
        assertEquals(1, requestCount)
        assertContentEquals(payload, Files.readAllBytes(cacheFile.toPath()))
    }

    @Test
    fun `temporary cache files are ignored`() {
        val temporaryUserHome = Files.createTempDirectory("lynmusic-player-temp-artwork-home")
        val payload = completePngPayload(0x05)
        val locator = "https://img.example.com/temp"
        val cacheDirectory = File(temporaryUserHome.toFile(), ".lynmusic/artwork-cache").apply { mkdirs() }
        File(cacheDirectory, "${locator.stableArtworkCacheHash()}.png.tmp-old").writeBytes(completePngPayload(0x06))
        var requestCount = 0

        val actual = runBlocking {
            loadJvmArtworkBytes(locator, userHomePath = temporaryUserHome.absolutePathString()) {
                requestCount += 1
                payload
            }
        }
        val finalCacheFiles = cacheDirectory.listFiles()
            .orEmpty()
            .filter { file -> !file.name.contains(".tmp-") }

        assertContentEquals(payload, actual)
        assertEquals(1, requestCount)
        assertEquals(1, finalCacheFiles.size)
        assertContentEquals(payload, Files.readAllBytes(finalCacheFiles.single().toPath()))
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

private fun completePngPayload(marker: Byte): ByteArray {
    return byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
        marker,
        0x00,
        0x00,
        0x00,
        0x00,
        0x49,
        0x45,
        0x4E,
        0x44,
        0xAE.toByte(),
        0x42,
        0x60,
        0x82.toByte(),
    )
}

private fun truncatedPngPayload(): ByteArray {
    return byteArrayOf(
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
}
