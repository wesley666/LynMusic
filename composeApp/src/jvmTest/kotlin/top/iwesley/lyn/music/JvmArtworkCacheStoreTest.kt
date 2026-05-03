package top.iwesley.lyn.music

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import top.iwesley.lyn.music.core.model.stableArtworkCacheHash
import top.iwesley.lyn.music.platform.createJvmArtworkCacheStore

class JvmArtworkCacheStoreTest {

    @Test
    fun `cache reuses existing file for same locator and redownloads after deletion`() {
        synchronized(USER_HOME_LOCK) {
            val originalUserHome = System.getProperty("user.home")
            val temporaryUserHome = kotlin.io.path.createTempDirectory("lynmusic-artwork-cache-home")
            val requestCount = AtomicInteger(0)
            val payload = completePngPayload(0x01)
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/cover") { exchange ->
                    requestCount.incrementAndGet()
                    exchange.sendResponseHeaders(200, payload.size.toLong())
                    exchange.responseBody.use { it.write(payload) }
                }
                start()
            }
            try {
                System.setProperty("user.home", temporaryUserHome.absolutePathString())
                val store = createJvmArtworkCacheStore()
                val locator = "http://127.0.0.1:${server.address.port}/cover"

                val first = runBlocking { store.cache(locator, locator) }
                val second = runBlocking { store.cache(locator, locator) }

                val firstPath = assertNotNull(first)
                assertEquals(firstPath, second)
                assertTrue(firstPath.endsWith(".png"))
                assertEquals(1, requestCount.get())

                File(firstPath).delete()

                val third = runBlocking { store.cache(locator, locator) }

                assertEquals(firstPath, third)
                assertEquals(2, requestCount.get())
            } finally {
                System.setProperty("user.home", originalUserHome)
                server.stop(0)
            }
        }
    }

    @Test
    fun `cache rejects incomplete remote payload without writing cache`() {
        synchronized(USER_HOME_LOCK) {
            val originalUserHome = System.getProperty("user.home")
            val temporaryUserHome = kotlin.io.path.createTempDirectory("lynmusic-artwork-cache-invalid-home")
            val payload = truncatedPngPayload()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/cover") { exchange ->
                    exchange.sendResponseHeaders(200, payload.size.toLong())
                    exchange.responseBody.use { it.write(payload) }
                }
                start()
            }
            try {
                System.setProperty("user.home", temporaryUserHome.absolutePathString())
                val store = createJvmArtworkCacheStore()
                val locator = "http://127.0.0.1:${server.address.port}/cover"

                val result = runBlocking { store.cache(locator, locator) }
                val cacheDirectory = File(temporaryUserHome.toFile(), ".lynmusic/artwork-cache")

                assertNull(result)
                assertTrue(cacheDirectory.listFiles().isNullOrEmpty())
            } finally {
                System.setProperty("user.home", originalUserHome)
                server.stop(0)
            }
        }
    }

    @Test
    fun `cache promotes old locator keyed file to album key without redownloading`() {
        synchronized(USER_HOME_LOCK) {
            val originalUserHome = System.getProperty("user.home")
            val temporaryUserHome = kotlin.io.path.createTempDirectory("lynmusic-artwork-cache-album-home")
            val requestCount = AtomicInteger(0)
            val payload = completePngPayload(0x04)
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/cover") { exchange ->
                    requestCount.incrementAndGet()
                    exchange.sendResponseHeaders(200, payload.size.toLong())
                    exchange.responseBody.use { it.write(payload) }
                }
                start()
            }
            try {
                System.setProperty("user.home", temporaryUserHome.absolutePathString())
                val store = createJvmArtworkCacheStore()
                val locator = "http://127.0.0.1:${server.address.port}/cover"
                val albumKey = "album:source-1:album-1"

                val legacy = assertNotNull(runBlocking { store.cache(locator, locator) })
                val promoted = assertNotNull(runBlocking { store.cache(locator, albumKey) })

                assertEquals(1, requestCount.get())
                assertTrue(File(legacy).isFile)
                assertTrue(File(promoted).isFile)
                assertTrue(runBlocking { store.hasCached(albumKey) })
                assertEquals(payload.toList(), File(promoted).readBytes().toList())
            } finally {
                System.setProperty("user.home", originalUserHome)
                server.stop(0)
            }
        }
    }

    @Test
    fun `replace existing album cache swaps valid file after new payload succeeds`() {
        synchronized(USER_HOME_LOCK) {
            val originalUserHome = System.getProperty("user.home")
            val temporaryUserHome = kotlin.io.path.createTempDirectory("lynmusic-artwork-cache-replace-home")
            val firstPayload = completePngPayload(0x05)
            val secondPayload = completePngPayload(0x06)
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/first") { exchange ->
                    exchange.sendResponseHeaders(200, firstPayload.size.toLong())
                    exchange.responseBody.use { it.write(firstPayload) }
                }
                createContext("/second") { exchange ->
                    exchange.sendResponseHeaders(200, secondPayload.size.toLong())
                    exchange.responseBody.use { it.write(secondPayload) }
                }
                start()
            }
            try {
                System.setProperty("user.home", temporaryUserHome.absolutePathString())
                val store = createJvmArtworkCacheStore()
                val firstLocator = "http://127.0.0.1:${server.address.port}/first"
                val secondLocator = "http://127.0.0.1:${server.address.port}/second"
                val albumKey = "album:source-1:album-1"

                val first = assertNotNull(runBlocking { store.cache(firstLocator, albumKey) })
                val second = assertNotNull(
                    runBlocking {
                        store.cache(
                            locator = secondLocator,
                            cacheKey = albumKey,
                            replaceExisting = true,
                        )
                    },
                )

                assertEquals(first, second)
                assertEquals(secondPayload.toList(), File(second).readBytes().toList())
                assertTrue(runBlocking { store.hasCached(albumKey) })
            } finally {
                System.setProperty("user.home", originalUserHome)
                server.stop(0)
            }
        }
    }

    @Test
    fun `cache ignores temporary and damaged files for same locator`() {
        synchronized(USER_HOME_LOCK) {
            val originalUserHome = System.getProperty("user.home")
            val temporaryUserHome = kotlin.io.path.createTempDirectory("lynmusic-artwork-cache-temp-home")
            val requestCount = AtomicInteger(0)
            val payload = completePngPayload(0x02)
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                createContext("/cover") { exchange ->
                    requestCount.incrementAndGet()
                    exchange.sendResponseHeaders(200, payload.size.toLong())
                    exchange.responseBody.use { it.write(payload) }
                }
                start()
            }
            try {
                System.setProperty("user.home", temporaryUserHome.absolutePathString())
                val store = createJvmArtworkCacheStore()
                val locator = "http://127.0.0.1:${server.address.port}/cover"
                val cacheDirectory = File(temporaryUserHome.toFile(), ".lynmusic/artwork-cache")
                val cachePrefix = locator.stableArtworkCacheHash()
                File(cacheDirectory, "$cachePrefix.png.tmp-old").writeBytes(completePngPayload(0x03))
                File(cacheDirectory, "$cachePrefix.png").writeBytes(truncatedPngPayload())

                val result = assertNotNull(runBlocking { store.cache(locator, locator) })
                val finalFiles = cacheDirectory.listFiles()
                    .orEmpty()
                    .filter { file -> !file.name.contains(".tmp-") }

                assertEquals(1, requestCount.get())
                assertTrue(result.endsWith(".png"))
                assertEquals(1, finalFiles.size)
                assertEquals(result, finalFiles.single().absolutePath)
            } finally {
                System.setProperty("user.home", originalUserHome)
                server.stop(0)
            }
        }
    }
}

private val USER_HOME_LOCK = Any()

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
