package top.iwesley.lyn.music

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.AppStorageCategory
import top.iwesley.lyn.music.platform.JvmAppStorageGateway

class JvmAppStorageGatewayTest {
    @Test
    fun `gateway reads and clears configured cache directories`() = runTest {
        val root = Files.createTempDirectory("lynmusic-storage-test")
        root.resolve("artwork-cache").createDirectories()
        root.resolve("artwork").createDirectories()
        root.resolve("cache").createDirectories()
        root.resolve("offline").createDirectories()
        root.resolve("artwork-cache/cover-a.jpg").writeText("1234")
        root.resolve("artwork/cover-b.jpg").writeText("12")
        root.resolve("cache/track.bin").writeText("123456")
        root.resolve("offline/song.mp3").writeText("12345")

        val gateway = JvmAppStorageGateway(root.toFile())

        val initial = gateway.loadStorageSnapshot().getOrThrow()
        assertEquals(6L, initial.categories.first { it.category == AppStorageCategory.PlaybackCache }.sizeBytes)
        assertEquals(6L, initial.categories.first { it.category == AppStorageCategory.Artwork }.sizeBytes)
        assertEquals(5L, initial.categories.first { it.category == AppStorageCategory.OfflineDownloads }.sizeBytes)
        assertEquals(17L, initial.totalSizeBytes)
        assertEquals(listOf(root.toFile().absolutePath), initial.paths)

        gateway.clearCategory(AppStorageCategory.Artwork).getOrThrow()

        val cleared = gateway.loadStorageSnapshot().getOrThrow()
        assertEquals(0L, cleared.categories.first { it.category == AppStorageCategory.Artwork }.sizeBytes)
        assertEquals(6L, cleared.categories.first { it.category == AppStorageCategory.PlaybackCache }.sizeBytes)
        assertEquals(5L, cleared.categories.first { it.category == AppStorageCategory.OfflineDownloads }.sizeBytes)
        assertEquals(11L, cleared.totalSizeBytes)

        gateway.clearCategory(AppStorageCategory.OfflineDownloads).getOrThrow()

        val offlineCleared = gateway.loadStorageSnapshot().getOrThrow()
        assertEquals(0L, offlineCleared.categories.first { it.category == AppStorageCategory.OfflineDownloads }.sizeBytes)
        assertEquals(6L, offlineCleared.totalSizeBytes)
    }
}
