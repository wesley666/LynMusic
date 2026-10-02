package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.ArtworkWritePolicy

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AndroidArtworkCacheStoreTest {
    @Test
    fun `local file URI spaces are accepted without allowing remote authorities`() = runBlocking {
        val directory = Files.createTempDirectory("lynmusic-file-uri").toFile()
        try {
            SharedAndroidArtworkCacheStore.resetForTesting()
            val source = File(directory, "cover art file.png").apply { writeBytes(completePngPayload()) }
            val store = SharedAndroidArtworkCacheStore.get(directory)
            val encoded = source.toURI().toString()
            val locators = listOf("file://${source.absolutePath}", encoded, encoded.replaceFirst("%20", " "))
            for ((index, locator) in locators.withIndex()) {
                val result = assertNotNull(store.cache(locator, "album-$index"))
                assertTrue(result.changed)
                assertTrue(File(result.locator).readBytes().contentEquals(source.readBytes()))
            }
            kotlin.test.assertNull(store.cache("file://server${source.absolutePath}", "remote-authority"))
            val padded = File(directory, "padded cover.jpg").apply {
                writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte(), 0, 0))
            }
            val fallback = assertNotNull(store.cache("file://${padded.absolutePath}", "padded"))
            assertEquals(padded.absolutePath, fallback.locator)
            kotlin.test.assertFalse(fallback.changed)
            kotlin.test.assertFalse(store.hasCached("padded"))
            assertEquals(0L, store.observeVersion("padded").first())
        } finally {
            SharedAndroidArtworkCacheStore.resetForTesting()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `shared artwork cache store reuses process instance`() {
        val cacheDirectory = Files.createTempDirectory("lynmusic-android-artwork-cache").toFile()
        try {
            SharedAndroidArtworkCacheStore.resetForTesting()

            val first = SharedAndroidArtworkCacheStore.get(cacheDirectory)
            val second = SharedAndroidArtworkCacheStore.get(cacheDirectory)

            assertTrue(first === second)
        } finally {
            SharedAndroidArtworkCacheStore.resetForTesting()
            cacheDirectory.deleteRecursively()
        }
    }

    @Test
    fun `shared artwork cache store publishes versions when cache is replaced`() = runBlocking {
        val cacheDirectory = Files.createTempDirectory("lynmusic-android-artwork-cache").toFile()
        val sourceDirectory = Files.createTempDirectory("lynmusic-android-artwork-source").toFile()
        try {
            SharedAndroidArtworkCacheStore.resetForTesting()
            val store = SharedAndroidArtworkCacheStore.get(cacheDirectory)
            val cacheKey = "album:source:artist:album"
            val firstSource = File(sourceDirectory, "first.png").apply {
                writeBytes(completePngPayload())
            }
            val secondSource = File(sourceDirectory, "second.png").apply {
                writeBytes(completePngPayload())
            }

            assertEquals(0L, store.observeVersion(cacheKey).first())

            assertNotNull(store.cache(firstSource.absolutePath, cacheKey, policy = ArtworkWritePolicy.Replace)?.locator)
            assertEquals(1L, store.observeVersion(cacheKey).first())

            assertNotNull(store.cache(secondSource.absolutePath, cacheKey, policy = ArtworkWritePolicy.Replace)?.locator)
            assertEquals(2L, store.observeVersion(cacheKey).first())
        } finally {
            SharedAndroidArtworkCacheStore.resetForTesting()
            cacheDirectory.deleteRecursively()
            sourceDirectory.deleteRecursively()
        }
    }
}

private fun completePngPayload(): ByteArray {
    return byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
        0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
    )
}
