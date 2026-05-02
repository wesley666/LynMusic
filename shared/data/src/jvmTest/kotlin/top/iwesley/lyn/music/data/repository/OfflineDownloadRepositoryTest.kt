package top.iwesley.lyn.music.data.repository

import androidx.room.Room
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.OfflineDownloadGateway
import top.iwesley.lyn.music.core.model.OfflineDownloadProgress
import top.iwesley.lyn.music.core.model.OfflineDownloadResult
import top.iwesley.lyn.music.core.model.OfflineDownloadStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.OfflineDownloadEntity
import top.iwesley.lyn.music.data.db.buildLynMusicDatabase

class OfflineDownloadRepositoryTest {

    @Test
    fun `download stores navidrome quality and completes without content length`() = runTest {
        val database = createOfflineTestDatabase()
        val gateway = RecordingOfflineDownloadGateway(
            result = OfflineDownloadResult(
                localMediaLocator = "offline://track-1.mp3",
                sizeBytes = 4096L,
                totalBytes = null,
            ),
            progressEvents = listOf(
                OfflineDownloadProgress(downloadedBytes = 1024L, totalBytes = null),
                OfflineDownloadProgress(downloadedBytes = 4096L, totalBytes = null),
            ),
        )
        val repository = DefaultOfflineDownloadRepository(database, gateway)

        val result = repository.download(navidromeTrack(), NavidromeAudioQuality.Kbps192)

        assertTrue(result.isSuccess)
        val row = database.offlineDownloadDao().getByTrackId("track-1")
        assertEquals(OfflineDownloadStatus.Completed.name, row?.status)
        assertEquals(NavidromeAudioQuality.Kbps192.name, row?.quality)
        assertEquals("offline://track-1.mp3", row?.localMediaLocator)
        assertEquals(4096L, row?.downloadedBytes)
        assertEquals(4096L, row?.totalBytes)
        assertEquals(listOf(NavidromeAudioQuality.Kbps192), gateway.downloadRequests.map { it.second })
        assertEquals(
            OfflineDownloadStatus.Completed,
            repository.downloads.first().getValue("track-1").status,
        )
    }

    @Test
    fun `local tracks are rejected before gateway download`() = runTest {
        val database = createOfflineTestDatabase()
        val gateway = RecordingOfflineDownloadGateway()
        val repository = DefaultOfflineDownloadRepository(database, gateway)

        val result = repository.download(localTrack(), NavidromeAudioQuality.Original)

        assertTrue(result.isFailure)
        assertNull(database.offlineDownloadDao().getByTrackId("local-track"))
        assertEquals(emptyList(), gateway.downloadRequests)
    }

    @Test
    fun `replacement keeps previous local copy when new download fails`() = runTest {
        val database = createOfflineTestDatabase()
        database.offlineDownloadDao().upsert(
            completedRow(
                trackId = "track-1",
                localMediaLocator = "offline://old.flac",
                quality = NavidromeAudioQuality.Original,
            ),
        )
        val gateway = RecordingOfflineDownloadGateway(
            failure = IllegalStateException("network closed"),
            existingLocators = mutableSetOf("offline://old.flac"),
        )
        val repository = DefaultOfflineDownloadRepository(database, gateway)

        val result = repository.download(navidromeTrack(), NavidromeAudioQuality.Kbps128)

        assertTrue(result.isFailure)
        val row = database.offlineDownloadDao().getByTrackId("track-1")
        assertEquals(OfflineDownloadStatus.Failed.name, row?.status)
        assertEquals(NavidromeAudioQuality.Kbps128.name, row?.quality)
        assertEquals("offline://old.flac", row?.localMediaLocator)
        assertEquals("offline://old.flac", repository.resolveOfflineMediaLocator("track-1"))
        assertEquals(emptyList(), gateway.deletedLocators)
    }

    @Test
    fun `successful replacement deletes previous local copy after new copy is ready`() = runTest {
        val database = createOfflineTestDatabase()
        database.offlineDownloadDao().upsert(
            completedRow(
                trackId = "track-1",
                localMediaLocator = "offline://old.flac",
                quality = NavidromeAudioQuality.Original,
            ),
        )
        val gateway = RecordingOfflineDownloadGateway(
            result = OfflineDownloadResult(
                localMediaLocator = "offline://new.mp3",
                sizeBytes = 2048L,
                totalBytes = 2048L,
            ),
            existingLocators = mutableSetOf("offline://old.flac"),
        )
        val repository = DefaultOfflineDownloadRepository(database, gateway)

        val result = repository.download(navidromeTrack(), NavidromeAudioQuality.Kbps320)

        assertTrue(result.isSuccess)
        val row = database.offlineDownloadDao().getByTrackId("track-1")
        assertEquals(OfflineDownloadStatus.Completed.name, row?.status)
        assertEquals(NavidromeAudioQuality.Kbps320.name, row?.quality)
        assertEquals("offline://new.mp3", row?.localMediaLocator)
        assertEquals(listOf("offline://old.flac"), gateway.deletedLocators)
    }

    @Test
    fun `restore incomplete downloads marks pending and downloading as failed`() = runTest {
        val database = createOfflineTestDatabase()
        database.offlineDownloadDao().upsert(
            completedRow(
                trackId = "completed-track",
                localMediaLocator = "offline://ready.flac",
                quality = NavidromeAudioQuality.Original,
            ),
        )
        database.offlineDownloadDao().upsert(
            offlineRow(trackId = "pending-track", status = OfflineDownloadStatus.Pending),
        )
        database.offlineDownloadDao().upsert(
            offlineRow(trackId = "downloading-track", status = OfflineDownloadStatus.Downloading),
        )
        val gateway = RecordingOfflineDownloadGateway()
        val repository = DefaultOfflineDownloadRepository(database, gateway)

        repository.restoreIncompleteDownloads()

        assertEquals(1, gateway.cleanupPartialCalls)
        assertEquals(
            OfflineDownloadStatus.Completed.name,
            database.offlineDownloadDao().getByTrackId("completed-track")?.status,
        )
        assertEquals(
            OfflineDownloadStatus.Failed.name,
            database.offlineDownloadDao().getByTrackId("pending-track")?.status,
        )
        assertEquals(
            OfflineDownloadStatus.Failed.name,
            database.offlineDownloadDao().getByTrackId("downloading-track")?.status,
        )
    }

    @Test
    fun `cancel download deletes record and cleans partial files`() = runTest {
        val database = createOfflineTestDatabase()
        database.offlineDownloadDao().upsert(
            offlineRow(trackId = "track-1", status = OfflineDownloadStatus.Downloading),
        )
        val gateway = RecordingOfflineDownloadGateway()
        val repository = DefaultOfflineDownloadRepository(database, gateway)

        val result = repository.cancelDownload("track-1")

        assertTrue(result.isSuccess)
        assertNull(database.offlineDownloadDao().getByTrackId("track-1"))
        assertEquals(1, gateway.cleanupPartialCalls)
    }

    @Test
    fun `delete downloads by source removes local files and rows`() = runTest {
        val database = createOfflineTestDatabase()
        database.offlineDownloadDao().upsert(
            completedRow(
                trackId = "track-1",
                sourceId = "nav-source",
                localMediaLocator = "offline://one.mp3",
                quality = NavidromeAudioQuality.Kbps192,
            ),
        )
        database.offlineDownloadDao().upsert(
            completedRow(
                trackId = "track-2",
                sourceId = "other-source",
                localMediaLocator = "offline://two.mp3",
                quality = NavidromeAudioQuality.Kbps192,
            ),
        )
        val gateway = RecordingOfflineDownloadGateway(
            existingLocators = mutableSetOf("offline://one.mp3", "offline://two.mp3"),
        )
        val repository = DefaultOfflineDownloadRepository(database, gateway)

        val result = repository.deleteDownloadsBySource("nav-source")

        assertTrue(result.isSuccess)
        assertNull(database.offlineDownloadDao().getByTrackId("track-1"))
        assertEquals("offline://two.mp3", database.offlineDownloadDao().getByTrackId("track-2")?.localMediaLocator)
        assertEquals(listOf("offline://one.mp3"), gateway.deletedLocators)
    }

    @Test
    fun `available space delegates to gateway`() = runTest {
        val database = createOfflineTestDatabase()
        val gateway = RecordingOfflineDownloadGateway(nextAvailableSpaceBytes = 12_345L)
        val repository = DefaultOfflineDownloadRepository(database, gateway)

        val result = repository.availableSpaceBytes()

        assertTrue(result.isSuccess)
        assertEquals(12_345L, result.getOrThrow())
        assertEquals(1, gateway.availableSpaceCalls)
    }

    @Test
    fun `available space may be unknown`() = runTest {
        val database = createOfflineTestDatabase()
        val gateway = RecordingOfflineDownloadGateway(nextAvailableSpaceBytes = null)
        val repository = DefaultOfflineDownloadRepository(database, gateway)

        val result = repository.availableSpaceBytes()

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }
}

private fun createOfflineTestDatabase(): LynMusicDatabase {
    val path = Files.createTempFile("lynmusic-offline-download", ".db")
    return buildLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(name = path.absolutePathString()),
    )
}

private fun navidromeTrack(
    trackId: String = "track-1",
    sourceId: String = "nav-source",
    songId: String = "song-1",
): Track {
    return Track(
        id = trackId,
        sourceId = sourceId,
        title = "Song",
        artistName = "Artist",
        albumTitle = "Album",
        durationMs = 180_000L,
        mediaLocator = buildNavidromeSongLocator(sourceId, songId),
        relativePath = "Artist/Album/Song.flac",
        sizeBytes = 123_456L,
        modifiedAt = 1L,
    )
}

private fun localTrack(): Track {
    return Track(
        id = "local-track",
        sourceId = "local-source",
        title = "Local",
        mediaLocator = "file:///music/local.flac",
        relativePath = "local.flac",
        sizeBytes = 100L,
        modifiedAt = 1L,
    )
}

private fun completedRow(
    trackId: String,
    sourceId: String = "nav-source",
    localMediaLocator: String,
    quality: NavidromeAudioQuality,
): OfflineDownloadEntity {
    return offlineRow(
        trackId = trackId,
        sourceId = sourceId,
        localMediaLocator = localMediaLocator,
        quality = quality,
        status = OfflineDownloadStatus.Completed,
        downloadedBytes = 1024L,
        totalBytes = 1024L,
    )
}

private fun offlineRow(
    trackId: String,
    sourceId: String = "nav-source",
    localMediaLocator: String? = null,
    quality: NavidromeAudioQuality = NavidromeAudioQuality.Original,
    status: OfflineDownloadStatus,
    downloadedBytes: Long = 0L,
    totalBytes: Long? = null,
): OfflineDownloadEntity {
    return OfflineDownloadEntity(
        trackId = trackId,
        sourceId = sourceId,
        originalMediaLocator = buildNavidromeSongLocator(sourceId, trackId),
        localMediaLocator = localMediaLocator,
        quality = quality.name,
        status = status.name,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        updatedAt = 1L,
        errorMessage = null,
    )
}

private class RecordingOfflineDownloadGateway(
    private val result: OfflineDownloadResult = OfflineDownloadResult(
        localMediaLocator = "offline://default.mp3",
        sizeBytes = 1L,
        totalBytes = 1L,
    ),
    private val failure: Throwable? = null,
    private val progressEvents: List<OfflineDownloadProgress> = emptyList(),
    private val existingLocators: MutableSet<String> = mutableSetOf(),
    private val nextAvailableSpaceBytes: Long? = null,
) : OfflineDownloadGateway {
    val downloadRequests = mutableListOf<Pair<Track, NavidromeAudioQuality>>()
    val deletedLocators = mutableListOf<String>()
    var cleanupPartialCalls = 0
    var availableSpaceCalls = 0

    override suspend fun download(
        track: Track,
        quality: NavidromeAudioQuality,
        onProgress: suspend (OfflineDownloadProgress) -> Unit,
    ): OfflineDownloadResult {
        downloadRequests += track to quality
        progressEvents.forEach { onProgress(it) }
        failure?.let { throw it }
        existingLocators += result.localMediaLocator
        return result
    }

    override suspend fun delete(localMediaLocator: String): Result<Unit> {
        deletedLocators += localMediaLocator
        existingLocators -= localMediaLocator
        return Result.success(Unit)
    }

    override suspend fun exists(localMediaLocator: String): Boolean {
        return localMediaLocator in existingLocators
    }

    override suspend fun clearAll(): Result<Unit> {
        existingLocators.clear()
        return Result.success(Unit)
    }

    override suspend fun sizeBytes(): Long {
        return existingLocators.size.toLong()
    }

    override suspend fun availableSpaceBytes(): Long? {
        availableSpaceCalls += 1
        return nextAvailableSpaceBytes
    }

    override suspend fun cleanupPartialFiles(): Result<Unit> {
        cleanupPartialCalls += 1
        return Result.success(Unit)
    }
}
