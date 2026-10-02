@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package top.iwesley.lyn.music.platform

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import top.iwesley.lyn.music.core.model.buildIosArtworkCacheLocator
import top.iwesley.lyn.music.core.model.parseIosArtworkCacheLocator

class IosArtworkCacheStoreTest {
    @Test
    fun importedArtworkStoresLogicalLocatorAndResolvesInsideCurrentContainer() = runBlocking {
        val trackCacheKey = uniqueKey("track")
        val albumCacheKey = uniqueKey("album")
        val locator = assertNotNull(storeIosImportedArtwork(trackCacheKey, JPEG_PAYLOAD))
        val sourceFileName = assertNotNull(parseIosArtworkCacheLocator(locator))
        val sourcePath = "${iosArtworkCacheDirectory()}/$sourceFileName"
        val albumPath = "${iosArtworkCacheDirectory()}/${albumCacheKey.stableArtworkCacheHash()}.jpg"
        try {
            assertEquals(buildIosArtworkCacheLocator(sourceFileName), locator)
            assertFalse(locator.contains("/var/mobile/Containers/Data/Application/"))
            assertTrue(NSFileManager.defaultManager.fileExistsAtPath(sourcePath))

            val resolved = createIosArtworkCacheStore().cache(locator, albumCacheKey)?.locator

            assertEquals(albumPath, resolved)
            assertTrue(NSFileManager.defaultManager.fileExistsAtPath(albumPath))
        } finally {
            removeFiles(sourcePath, albumPath)
        }
    }

    @Test
    fun lyricsShareResolvesLogicalArtworkLocatorBeforeReadingFile() = runBlocking {
        val trackCacheKey = uniqueKey("lyrics-share-track")
        val albumCacheKey = uniqueKey("lyrics-share-album")
        val locator = assertNotNull(storeIosImportedArtwork(trackCacheKey, JPEG_PAYLOAD))
        val sourceFileName = assertNotNull(parseIosArtworkCacheLocator(locator))
        val sourcePath = "${iosArtworkCacheDirectory()}/$sourceFileName"
        val albumPath = "${iosArtworkCacheDirectory()}/${albumCacheKey.stableArtworkCacheHash()}.jpg"
        try {
            val resolved = resolveIosLyricsShareArtworkTarget(
                normalizedLocator = locator,
                artworkCacheKey = albumCacheKey,
                artworkCacheStore = createIosArtworkCacheStore(),
            )

            assertEquals(albumPath, resolved)
            assertContentEquals(JPEG_PAYLOAD, readIosLocalBytes(assertNotNull(resolved)))
        } finally {
            removeFiles(sourcePath, albumPath)
        }
    }

    @Test
    fun lyricsShareRelocatesLegacyArtworkPathBeforeReadingFile() = runBlocking {
        val directory = iosArtworkCacheDirectory()
        val fileName = "${uniqueKey("lyrics-share-legacy").stableArtworkCacheHash()}.jpg"
        val relocatedPath = "$directory/$fileName"
        val albumCacheKey = uniqueKey("lyrics-share-legacy-album")
        val albumPath = "$directory/${albumCacheKey.stableArtworkCacheHash()}.jpg"
        val legacyPath =
            "/var/mobile/Containers/Data/Application/OLD/Library/Caches/lynmusic-artwork-cache/$fileName"
        try {
            assertTrue(writeIosFileBytes(relocatedPath, JPEG_PAYLOAD))

            val resolved = resolveIosLyricsShareArtworkTarget(
                normalizedLocator = legacyPath,
                artworkCacheKey = albumCacheKey,
                artworkCacheStore = createIosArtworkCacheStore(),
            )

            assertEquals(albumPath, resolved)
            assertContentEquals(JPEG_PAYLOAD, readIosLocalBytes(assertNotNull(resolved)))
        } finally {
            removeFiles(relocatedPath, albumPath)
        }
    }

    @Test
    fun legacyAbsolutePathRelocatesBySafeFileNameAfterContainerUuidChanges() = runBlocking {
        val directory = iosArtworkCacheDirectory()
        val fileName = "${uniqueKey("legacy").stableArtworkCacheHash()}.jpg"
        val relocatedPath = "$directory/$fileName"
        val albumCacheKey = uniqueKey("legacy-album")
        val albumPath = "$directory/${albumCacheKey.stableArtworkCacheHash()}.jpg"
        val legacyPath =
            "/var/mobile/Containers/Data/Application/OLD-UUID/Library/Caches/lynmusic-artwork-cache/$fileName"
        try {
            assertTrue(writeIosFileBytes(relocatedPath, JPEG_PAYLOAD))

            val resolved = createIosArtworkCacheStore().cache(legacyPath, albumCacheKey)?.locator

            assertEquals(albumPath, resolved)
            assertTrue(NSFileManager.defaultManager.fileExistsAtPath(albumPath))
        } finally {
            removeFiles(relocatedPath, albumPath)
        }
    }

    @Test
    fun missingTrackPathFallsBackToValidAlbumCache() = runBlocking {
        val directory = iosArtworkCacheDirectory()
        val albumCacheKey = uniqueKey("fallback-album")
        val albumPath = "$directory/${albumCacheKey.stableArtworkCacheHash()}.jpg"
        val missingLegacyPath =
            "/var/mobile/Containers/Data/Application/OLD-UUID/Library/Caches/lynmusic-artwork-cache/missing.jpg"
        try {
            assertTrue(writeIosFileBytes(albumPath, JPEG_PAYLOAD))

            assertEquals(albumPath, createIosArtworkCacheStore().cache(missingLegacyPath, albumCacheKey)?.locator)
        } finally {
            removeFiles(albumPath)
        }
    }

    @Test
    fun missingTrackAndAlbumCacheReturnNull() = runBlocking {
        val missingLocator = assertNotNull(buildIosArtworkCacheLocator("missing-${Random.nextLong().toULong()}.jpg"))

        assertNull(createIosArtworkCacheStore().cache(missingLocator, uniqueKey("missing-album"))?.locator)
    }

    @Test
    fun failedAtomicRenamePreservesPreviousCacheFile() {
        val directory = createTemporaryDirectory("atomic-artwork")
        val fileName = "artwork.jpg"
        val output = "$directory/$fileName"
        try {
            assertTrue(writeIosFileBytes(output, JPEG_PAYLOAD))

            val result = writeIosArtworkCacheFileAtomically(
                directory = directory,
                fileName = fileName,
                payload = SECOND_JPEG_PAYLOAD,
                cachePrefix = "artwork",
                replaceExisting = true,
                renameFile = { _, _ -> -1 },
            )

            assertNull(result)
            assertContentEquals(JPEG_PAYLOAD, readIosLocalBytes(output))
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(directory, error = null)
        }
    }

    private fun uniqueKey(label: String): String = "$label-${Random.nextLong().toULong()}"

    private fun createTemporaryDirectory(label: String): String {
        val path = NSTemporaryDirectory() + "lynmusic-$label-${Random.nextLong().toULong()}"
        assertTrue(
            NSFileManager.defaultManager.createDirectoryAtPath(
                path = path,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            ),
        )
        return path
    }

    private fun removeFiles(vararg paths: String) {
        paths.forEach { path -> NSFileManager.defaultManager.removeItemAtPath(path, error = null) }
    }

    private companion object {
        val JPEG_PAYLOAD = byteArrayOf(
            0xFF.toByte(),
            0xD8.toByte(),
            0xFF.toByte(),
            0x00,
            0xFF.toByte(),
            0xD9.toByte(),
        )
        val SECOND_JPEG_PAYLOAD = byteArrayOf(
            0xFF.toByte(),
            0xD8.toByte(),
            0xFF.toByte(),
            0x01,
            0x02,
            0xFF.toByte(),
            0xD9.toByte(),
        )
    }
}
