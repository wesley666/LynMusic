package top.iwesley.lyn.music.desktop.ui

import top.iwesley.lyn.music.*

import kotlinx.coroutines.test.runTest

import kotlin.test.Test
import top.iwesley.lyn.music.desktop.ui.testing.assertLocalizedEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.OfflineDownload
import top.iwesley.lyn.music.core.model.OfflineDownloadStatus
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.SourceWithStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.core.model.buildWebDavLocator
import top.iwesley.lyn.music.feature.importing.ImportState
import top.iwesley.lyn.music.feature.offline.batchDownloadInsufficientSpaceMessage
import top.iwesley.lyn.music.feature.offline.batchDownloadSizeEstimateLabel
import top.iwesley.lyn.music.feature.offline.estimateBatchDownloadSize
import top.iwesley.lyn.music.feature.offline.ActiveBatchDownloadState

class OfflineDownloadUiTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `offline batch download status summary shows determinate total size`() = runTest {
        val summary = offlineBatchDownloadStatusSummary(
            activeBatchDownload = ActiveBatchDownloadState(
                trackIds = listOf("first", "second"),
                processedCount = 1,
                estimatedTotalBytes = 3L * 1024L * 1024L,
            ),
            downloadsByTrackId = mapOf(
                "first" to completedDownload(trackId = "first", downloadedBytes = 1L * 1024L * 1024L),
                "second" to completedDownload(trackId = "second", downloadedBytes = 0L),
            ),
        )

        assertLocalizedEquals("正在批量下载 1/2 首 · 1.0 MB / 3.0 MB", summary?.label)
        assertLocalizedEquals(0.33333334f, summary?.progress)
    }

    @Test
    fun `offline batch download status summary shows approximate and unknown sizes`() = runTest {
        val approximate = offlineBatchDownloadStatusSummary(
            activeBatchDownload = ActiveBatchDownloadState(
                trackIds = listOf("nav"),
                processedCount = 0,
                estimatedTotalBytes = 2_400_000L,
                approximate = true,
            ),
            downloadsByTrackId = emptyMap(),
        )
        val unknown = offlineBatchDownloadStatusSummary(
            activeBatchDownload = ActiveBatchDownloadState(
                trackIds = listOf("first", "second"),
                processedCount = 0,
                unknownCount = 1,
            ),
            downloadsByTrackId = mapOf(
                "first" to completedDownload(trackId = "first", downloadedBytes = 512L * 1024L),
            ),
        )

        assertLocalizedEquals("正在批量下载 0/1 首 · 0 B / 约 2.3 MB", approximate?.label)
        assertLocalizedEquals(0f, approximate?.progress)
        assertLocalizedEquals("正在批量下载 0/2 首 · 已下载 512.0 KB · 1 首未知", unknown?.label)
        assertNull(unknown?.progress)
        assertNull(offlineBatchDownloadStatusSummary(null, emptyMap()))
    }

    @Test
    fun `batch download size estimate sums non navidrome source sizes`() = runTest {
        val tracks = listOf(
            sampleWebDavTrack("first", sizeBytes = 1L * 1024L * 1024L),
            sampleWebDavTrack("second", sizeBytes = 2L * 1024L * 1024L),
        )

        val estimate = estimateBatchDownloadSize(tracks, emptyMap())

        assertLocalizedEquals(3L * 1024L * 1024L, estimate.totalBytes)
        assertLocalizedEquals(0, estimate.unknownCount)
        assertLocalizedEquals("3.0 MB", batchDownloadSizeEstimateLabel(estimate))
    }

    @Test
    fun `batch download size estimate uses selected navidrome quality`() = runTest {
        val track = sampleNavidromeTrack(
            id = "nav",
            sizeBytes = 5L * 1024L * 1024L,
            durationMs = 60_000L,
        )

        val originalEstimate = estimateBatchDownloadSize(listOf(track), emptyMap(), NavidromeAudioQuality.Original)
        val transcodedEstimate = estimateBatchDownloadSize(listOf(track), emptyMap(), NavidromeAudioQuality.Kbps320)

        assertLocalizedEquals(5L * 1024L * 1024L, originalEstimate.totalBytes)
        assertFalse(originalEstimate.approximate)
        assertLocalizedEquals("5.0 MB", batchDownloadSizeEstimateLabel(originalEstimate))
        assertLocalizedEquals(2_400_000L, transcodedEstimate.totalBytes)
        assertTrue(transcodedEstimate.approximate)
        assertLocalizedEquals("约 2.3 MB", batchDownloadSizeEstimateLabel(transcodedEstimate))
    }

    @Test
    fun `batch download size estimate skips completed matching quality`() = runTest {
        val nav = sampleNavidromeTrack("nav", durationMs = 60_000L)
        val webDav = sampleWebDavTrack("webdav", sizeBytes = 3L * 1024L * 1024L)
        val downloads = mapOf(
            nav.id to completedDownload(trackId = nav.id, quality = NavidromeAudioQuality.Kbps192),
            webDav.id to completedDownload(trackId = webDav.id),
        )

        val matchingEstimate = estimateBatchDownloadSize(
            tracks = listOf(nav, webDav),
            downloadsByTrackId = downloads,
            quality = NavidromeAudioQuality.Kbps192,
        )
        val replacementEstimate = estimateBatchDownloadSize(
            tracks = listOf(nav, webDav),
            downloadsByTrackId = downloads,
            quality = NavidromeAudioQuality.Kbps320,
        )

        assertLocalizedEquals("无需下载", batchDownloadSizeEstimateLabel(matchingEstimate))
        assertLocalizedEquals(2, matchingEstimate.skippedCount)
        assertLocalizedEquals("约 2.3 MB", batchDownloadSizeEstimateLabel(replacementEstimate))
        assertLocalizedEquals(1, replacementEstimate.skippedCount)
    }

    @Test
    fun `batch download size estimate reports unknown sizes`() = runTest {
        val known = sampleWebDavTrack("known", sizeBytes = 1L * 1024L * 1024L)
        val unknown = sampleWebDavTrack("unknown", sizeBytes = 0L)
        val unknownOnly = estimateBatchDownloadSize(listOf(unknown), emptyMap())

        val mixedEstimate = estimateBatchDownloadSize(listOf(known, unknown), emptyMap())

        assertLocalizedEquals("未知", batchDownloadSizeEstimateLabel(unknownOnly))
        assertLocalizedEquals("1.0 MB + 1 首未知", batchDownloadSizeEstimateLabel(mixedEstimate))
    }

    @Test
    fun `batch download space check requires one gigabyte reserve`() = runTest {
        val estimate = estimateBatchDownloadSize(
            tracks = listOf(sampleWebDavTrack("first", sizeBytes = 512L * 1024L * 1024L)),
            downloadsByTrackId = emptyMap(),
        )

        assertLocalizedEquals(
            "存储空间不足：预计下载 512.0 MB，需预留 1.0 GB，可用 1.0 GB。",
            batchDownloadInsufficientSpaceMessage(estimate, availableSpaceBytes = 1L * 1024L * 1024L * 1024L),
        )
        assertLocalizedEquals(
            null,
            batchDownloadInsufficientSpaceMessage(estimate, availableSpaceBytes = 1536L * 1024L * 1024L),
        )
        assertLocalizedEquals(null, batchDownloadInsufficientSpaceMessage(estimate, availableSpaceBytes = null))
    }

    private fun completedDownload(
        trackId: String = "song-1",
        quality: NavidromeAudioQuality = NavidromeAudioQuality.Original,
        localMediaLocator: String? = "/tmp/offline/song.mp3",
        status: OfflineDownloadStatus = OfflineDownloadStatus.Completed,
        downloadedBytes: Long = 1_024L,
    ): OfflineDownload {
        return OfflineDownload(
            trackId = trackId,
            sourceId = "source-1",
            originalMediaLocator = "remote://$trackId",
            localMediaLocator = localMediaLocator,
            quality = quality,
            status = status,
            downloadedBytes = downloadedBytes,
        )
    }

    private fun sampleNavidromeTrack(
        id: String,
        sizeBytes: Long = 0L,
        durationMs: Long = 0L,
    ): Track {
        return sampleTrack(
            id = id,
            mediaLocator = buildNavidromeSongLocator("source-1", id),
            sizeBytes = sizeBytes,
            durationMs = durationMs,
        )
    }

    private fun sampleWebDavTrack(
        id: String,
        sizeBytes: Long = 0L,
    ): Track {
        return sampleTrack(
            id = id,
            mediaLocator = buildWebDavLocator("source-1", "$id.mp3"),
            sizeBytes = sizeBytes,
        )
    }

    private fun sampleTrack(
        id: String,
        mediaLocator: String = "file:///music/$id.mp3",
        sizeBytes: Long = 0L,
        durationMs: Long = 0L,
    ): Track {
        return Track(
            id = id,
            sourceId = "source-1",
            title = id,
            durationMs = durationMs,
            mediaLocator = mediaLocator,
            relativePath = "$id.mp3",
            sizeBytes = sizeBytes,
        )
    }

    private fun importStateWithOnlineSource(): ImportState {
        return ImportState(
            capabilities = testPlatformCapabilities(),
            sources = listOf(
                SourceWithStatus(
                    source = ImportSource(
                        id = "source-1",
                        type = ImportSourceType.NAVIDROME,
                        label = "Navidrome",
                        rootReference = "https://navidrome.example",
                        enabled = true,
                        indexMode = ImportSourceIndexMode.ONLINE,
                    ),
                ),
            ),
        )
    }

    private fun testPlatformCapabilities(): PlatformCapabilities {
        return PlatformCapabilities(
            supportsLocalFolderImport = true,
            supportsSambaImport = true,
            supportsWebDavImport = true,
            supportsNavidromeImport = true,
            supportsSystemMediaControls = true,
        )
    }
}
