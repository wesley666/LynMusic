package top.iwesley.lyn.music.feature.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.core.model.buildWebDavLocator
import top.iwesley.lyn.music.feature.TestOfflineDownloadRepository
import top.iwesley.lyn.music.feature.testOfflineDownload

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineDownloadStoreTest {

    @Test
    fun `refresh available space writes bytes into state`() = runTest {
        val repository = TestOfflineDownloadRepository(nextAvailableSpaceBytes = 5_368_709_120L)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = OfflineDownloadStore(repository, scope)
        advanceUntilIdle()

        store.dispatch(OfflineDownloadIntent.RefreshAvailableSpace)
        advanceUntilIdle()

        assertEquals(5_368_709_120L, store.state.value.availableSpaceBytes)
        assertFalse(store.state.value.availableSpaceLoading)
        assertEquals(1, repository.availableSpaceCalls)
        scope.cancel()
    }

    @Test
    fun `refresh available space can return unknown`() = runTest {
        val repository = TestOfflineDownloadRepository(nextAvailableSpaceBytes = null)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = OfflineDownloadStore(repository, scope)
        advanceUntilIdle()

        store.dispatch(OfflineDownloadIntent.RefreshAvailableSpace)
        advanceUntilIdle()

        assertNull(store.state.value.availableSpaceBytes)
        assertFalse(store.state.value.availableSpaceLoading)
        assertEquals(1, repository.availableSpaceCalls)
        scope.cancel()
    }

    @Test
    fun `batch download stops when estimated size plus reserve exceeds available space`() = runTest {
        val track = sampleWebDavTrack(
            id = "first",
            sizeBytes = 512L * 1024L * 1024L,
        )
        val repository = TestOfflineDownloadRepository(nextAvailableSpaceBytes = 1L * 1024L * 1024L * 1024L)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = OfflineDownloadStore(repository, scope)
        advanceUntilIdle()

        store.dispatch(OfflineDownloadIntent.DownloadMany(listOf(track)))
        advanceUntilIdle()

        assertEquals(emptyList(), repository.downloadRequests)
        assertEquals(
            "存储空间不足：预计下载 512.0 MB，需预留 1.0 GB，可用 1.0 GB。",
            store.state.value.message,
        )
        assertEquals(1, repository.availableSpaceCalls)
        scope.cancel()
    }

    @Test
    fun `batch download continues when available space is unknown`() = runTest {
        val track = sampleWebDavTrack(
            id = "first",
            sizeBytes = 512L * 1024L * 1024L,
        )
        val repository = TestOfflineDownloadRepository(nextAvailableSpaceBytes = null)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = OfflineDownloadStore(repository, scope)
        advanceUntilIdle()

        store.dispatch(OfflineDownloadIntent.DownloadMany(listOf(track)))
        advanceUntilIdle()

        assertEquals(listOf(track.id to NavidromeAudioQuality.Original), repository.downloadRequests)
        assertEquals("批量下载完成：成功 1 首。", store.state.value.message)
        assertEquals(1, repository.availableSpaceCalls)
        scope.cancel()
    }

    @Test
    fun `batch download skips unsupported and completed matching quality tracks`() = runTest {
        val completedNavidrome = sampleNavidromeTrack(id = "nav-completed")
        val pendingNavidrome = sampleNavidromeTrack(id = "nav-pending")
        val unsupported = sampleLocalTrack(id = "local")
        val repository = TestOfflineDownloadRepository(
            initialDownloads = mapOf(
                completedNavidrome.id to testOfflineDownload(
                    trackId = completedNavidrome.id,
                    sourceId = completedNavidrome.sourceId,
                ).copy(quality = NavidromeAudioQuality.Kbps192),
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = OfflineDownloadStore(repository, scope)
        advanceUntilIdle()

        store.dispatch(
            OfflineDownloadIntent.DownloadMany(
                tracks = listOf(completedNavidrome, pendingNavidrome, unsupported),
                quality = NavidromeAudioQuality.Kbps192,
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf(pendingNavidrome.id to NavidromeAudioQuality.Kbps192), repository.downloadRequests)
        assertEquals("批量下载完成：成功 1 首，跳过 2 首。", store.state.value.message)
        scope.cancel()
    }

    @Test
    fun `batch download continues after individual failure`() = runTest {
        val first = sampleWebDavTrack(id = "first")
        val second = sampleWebDavTrack(id = "second")
        val third = sampleWebDavTrack(id = "third")
        val repository = TestOfflineDownloadRepository().apply {
            failingTrackIds = setOf(second.id)
        }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = OfflineDownloadStore(repository, scope)
        advanceUntilIdle()

        store.dispatch(OfflineDownloadIntent.DownloadMany(listOf(first, second, third)))
        advanceUntilIdle()

        assertEquals(
            listOf(
                first.id to NavidromeAudioQuality.Original,
                second.id to NavidromeAudioQuality.Original,
                third.id to NavidromeAudioQuality.Original,
            ),
            repository.downloadRequests,
        )
        assertEquals("批量下载完成：成功 2 首，失败 1 首。", store.state.value.message)
        scope.cancel()
    }
}

private fun sampleNavidromeTrack(id: String): Track {
    return sampleTrack(
        id = id,
        sourceId = "navidrome-source",
        mediaLocator = buildNavidromeSongLocator("navidrome-source", id),
    )
}

private fun sampleWebDavTrack(
    id: String,
    sizeBytes: Long = 0L,
): Track {
    return sampleTrack(
        id = id,
        sourceId = "webdav-source",
        mediaLocator = buildWebDavLocator("webdav-source", "$id.mp3"),
        sizeBytes = sizeBytes,
    )
}

private fun sampleLocalTrack(id: String): Track {
    return sampleTrack(
        id = id,
        sourceId = "local-source",
        mediaLocator = "file:///music/$id.mp3",
    )
}

private fun sampleTrack(
    id: String,
    sourceId: String,
    mediaLocator: String,
    sizeBytes: Long = 0L,
): Track {
    return Track(
        id = id,
        sourceId = sourceId,
        title = id,
        mediaLocator = mediaLocator,
        relativePath = "$id.mp3",
        sizeBytes = sizeBytes,
    )
}
