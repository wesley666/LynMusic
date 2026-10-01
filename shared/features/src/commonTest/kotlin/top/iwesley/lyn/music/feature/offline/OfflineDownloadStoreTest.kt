package top.iwesley.lyn.music.feature.offline

import top.iwesley.lyn.music.resources.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import top.iwesley.lyn.music.testing.assertLocalizedEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.AppLanguageRuntime
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiText
import top.iwesley.lyn.music.data.repository.OfflineDownloadRepository
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.core.model.buildWebDavLocator
import top.iwesley.lyn.music.feature.TestOfflineDownloadRepository
import top.iwesley.lyn.music.feature.testOfflineDownload

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineDownloadStoreTest {
    @Test
    fun describedDownloadFailureReachesUiAndRetainsStateAcrossLanguages() = runTest {
        val track = sampleWebDavTrack(id = "described-failure")
        val fixture = TestOfflineDownloadRepository(initialDownloads = mapOf(track.id to testOfflineDownload(track.id, track.sourceId)))
        var downloadCalls = 0
        val repository = object : OfflineDownloadRepository by fixture {
            override suspend fun download(track: Track, quality: NavidromeAudioQuality): Result<Unit> {
                downloadCalls++
                return Result.failure(UiTextException(uiText(Res.string.offline_write_failed)))
            }
        }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val originalLanguage = AppLanguageRuntime.appLanguage.value
        try {
            val store = OfflineDownloadStore(repository, scope)
            advanceUntilIdle()
            store.dispatch(OfflineDownloadIntent.Download(track))
            advanceUntilIdle()
            val state = store.state.value
            val message = assertNotNull(state.message)
            val languages = listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese)
            val expected = listOf("Failed to save the offline file.", "离线文件写入失败。", "離線檔案寫入失敗。")
            languages.forEachIndexed { index, language ->
                AppLanguageRuntime.update(language)
                runCurrent()
                assertEquals(expected[index], resolveUiText(message, AppLanguageRuntime.effectiveLanguage.value))
                assertSame(state, store.state.value)
            }
            assertEquals(setOf(track.id), state.downloadsByTrackId.keys)
            assertEquals(1, downloadCalls)
        } finally {
            AppLanguageRuntime.update(originalLanguage)
            scope.cancel()
        }
    }

    @Test
    fun `refresh available space writes bytes into state`() = runTest {
        val repository = TestOfflineDownloadRepository(nextAvailableSpaceBytes = 5_368_709_120L)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = OfflineDownloadStore(repository, scope)
        advanceUntilIdle()

        store.dispatch(OfflineDownloadIntent.RefreshAvailableSpace)
        advanceUntilIdle()

        assertLocalizedEquals(5_368_709_120L, store.state.value.availableSpaceBytes)
        assertFalse(store.state.value.availableSpaceLoading)
        assertLocalizedEquals(1, repository.availableSpaceCalls)
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
        assertLocalizedEquals(1, repository.availableSpaceCalls)
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

        assertLocalizedEquals(emptyList(), repository.downloadRequests)
        assertLocalizedEquals(
            "存储空间不足：预计下载 512.0 MB，需预留 1.0 GB，可用 1.0 GB。",
            store.state.value.message,
        )
        assertNull(store.state.value.activeBatchDownload)
        assertLocalizedEquals(1, repository.availableSpaceCalls)
        scope.cancel()
    }

    @Test
    fun `batch download exposes active progress while running and clears when finished`() = runTest {
        val first = sampleWebDavTrack(id = "first", sizeBytes = 1L * 1024L * 1024L)
        val second = sampleWebDavTrack(id = "second", sizeBytes = 2L * 1024L * 1024L)
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        val repository = TestOfflineDownloadRepository().apply {
            downloadGatesByTrackId[first.id] = firstGate
            downloadGatesByTrackId[second.id] = secondGate
        }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = OfflineDownloadStore(repository, scope)
        advanceUntilIdle()

        store.dispatch(OfflineDownloadIntent.DownloadMany(listOf(first, second)))
        runCurrent()

        val started = assertNotNull(store.state.value.activeBatchDownload)
        assertLocalizedEquals(listOf(first.id, second.id), started.trackIds)
        assertLocalizedEquals(0, started.processedCount)
        assertLocalizedEquals(0, started.successCount)
        assertLocalizedEquals(0, started.failureCount)
        assertLocalizedEquals(3L * 1024L * 1024L, started.estimatedTotalBytes)
        assertLocalizedEquals(0, started.unknownCount)

        firstGate.complete(Unit)
        runCurrent()

        val afterFirst = assertNotNull(store.state.value.activeBatchDownload)
        assertLocalizedEquals(1, afterFirst.processedCount)
        assertLocalizedEquals(1, afterFirst.successCount)
        assertLocalizedEquals(0, afterFirst.failureCount)

        secondGate.complete(Unit)
        advanceUntilIdle()

        assertNull(store.state.value.activeBatchDownload)
        assertLocalizedEquals("批量下载完成：成功 2 首。", store.state.value.message)
        scope.cancel()
    }

    @Test
    fun `single download does not expose active batch download`() = runTest {
        val track = sampleWebDavTrack(id = "single")
        val gate = CompletableDeferred<Unit>()
        val repository = TestOfflineDownloadRepository(downloadGate = gate)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = OfflineDownloadStore(repository, scope)
        advanceUntilIdle()

        store.dispatch(OfflineDownloadIntent.Download(track))
        runCurrent()

        assertNull(store.state.value.activeBatchDownload)
        store.dispatch(OfflineDownloadIntent.CancelActiveBatchDownload)
        runCurrent()
        assertLocalizedEquals(emptyList(), repository.cancelRequests)
        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(store.state.value.activeBatchDownload)
        scope.cancel()
    }

    @Test
    fun `cancel active batch download cancels current track and does not start remaining tracks`() = runTest {
        val first = sampleWebDavTrack(id = "first")
        val second = sampleWebDavTrack(id = "second")
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        val repository = TestOfflineDownloadRepository().apply {
            downloadGatesByTrackId[first.id] = firstGate
            downloadGatesByTrackId[second.id] = secondGate
        }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = OfflineDownloadStore(repository, scope)
        advanceUntilIdle()

        store.dispatch(OfflineDownloadIntent.DownloadMany(listOf(first, second)))
        runCurrent()
        assertNotNull(store.state.value.activeBatchDownload)
        assertLocalizedEquals(listOf(first.id to NavidromeAudioQuality.Original), repository.downloadRequests)

        store.dispatch(OfflineDownloadIntent.CancelActiveBatchDownload)
        runCurrent()

        assertNull(store.state.value.activeBatchDownload)
        assertLocalizedEquals("已取消批量下载。", store.state.value.message)
        assertLocalizedEquals(listOf(first.id), repository.cancelRequests)
        assertLocalizedEquals(listOf(first.id to NavidromeAudioQuality.Original), repository.downloadRequests)
        secondGate.complete(Unit)
        advanceUntilIdle()
        scope.cancel()
    }

    @Test
    fun `cancel active batch download without active batch has no side effects`() = runTest {
        val repository = TestOfflineDownloadRepository()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = OfflineDownloadStore(repository, scope)
        advanceUntilIdle()

        store.dispatch(OfflineDownloadIntent.CancelActiveBatchDownload)
        advanceUntilIdle()

        assertNull(store.state.value.activeBatchDownload)
        assertNull(store.state.value.message)
        assertLocalizedEquals(emptyList(), repository.cancelRequests)
        scope.cancel()
    }

    @Test
    fun `batch download with only skipped tracks does not expose active progress`() = runTest {
        val completed = sampleWebDavTrack(id = "completed")
        val local = sampleLocalTrack(id = "local")
        val repository = TestOfflineDownloadRepository(
            initialDownloads = mapOf(
                completed.id to testOfflineDownload(
                    trackId = completed.id,
                    sourceId = completed.sourceId,
                ),
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = OfflineDownloadStore(repository, scope)
        advanceUntilIdle()

        store.dispatch(OfflineDownloadIntent.DownloadMany(listOf(completed, local)))
        advanceUntilIdle()

        assertLocalizedEquals(emptyList(), repository.downloadRequests)
        assertNull(store.state.value.activeBatchDownload)
        assertLocalizedEquals("批量下载完成：跳过 2 首。", store.state.value.message)
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

        assertLocalizedEquals(listOf(track.id to NavidromeAudioQuality.Original), repository.downloadRequests)
        assertLocalizedEquals("批量下载完成：成功 1 首。", store.state.value.message)
        assertLocalizedEquals(1, repository.availableSpaceCalls)
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

        assertLocalizedEquals(listOf(pendingNavidrome.id to NavidromeAudioQuality.Kbps192), repository.downloadRequests)
        assertLocalizedEquals("批量下载完成：成功 1 首，跳过 2 首。", store.state.value.message)
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

        assertLocalizedEquals(
            listOf(
                first.id to NavidromeAudioQuality.Original,
                second.id to NavidromeAudioQuality.Original,
                third.id to NavidromeAudioQuality.Original,
            ),
            repository.downloadRequests,
        )
        assertLocalizedEquals("批量下载完成：成功 2 首，失败 1 首。", store.state.value.message)
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
