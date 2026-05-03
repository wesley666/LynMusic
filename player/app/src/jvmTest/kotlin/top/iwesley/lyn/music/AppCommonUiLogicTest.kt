package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.OfflineDownload
import top.iwesley.lyn.music.core.model.OfflineDownloadStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.core.model.buildWebDavLocator
import top.iwesley.lyn.music.feature.offline.batchDownloadInsufficientSpaceMessage
import top.iwesley.lyn.music.feature.offline.batchDownloadSizeEstimateLabel
import top.iwesley.lyn.music.feature.offline.estimateBatchDownloadSize
import top.iwesley.lyn.music.feature.offline.ActiveBatchDownloadState

class AppCommonUiLogicTest {
    @Test
    fun `queue drawer slide offset follows drawer side`() {
        assertEquals(-420, queueDrawerHorizontalSlideOffset(QueueDrawerSide.Start, 420))
        assertEquals(420, queueDrawerHorizontalSlideOffset(QueueDrawerSide.End, 420))
    }

    @Test
    fun `navidrome menu labels current offline quality`() {
        val download = completedDownload(quality = NavidromeAudioQuality.Kbps192)

        assertTrue(isCurrentOfflineDownloadQuality(download, NavidromeAudioQuality.Kbps192))
        assertEquals("已下载 192 kbps", navidromeDownloadMenuLabel(NavidromeAudioQuality.Kbps192, download))
        assertEquals("重新下载 320 kbps", navidromeDownloadMenuLabel(NavidromeAudioQuality.Kbps320, download))
    }

    @Test
    fun `navidrome menu does not mark completed record without local file as downloaded`() {
        val download = completedDownload(
            quality = NavidromeAudioQuality.Original,
            localMediaLocator = null,
        )

        assertFalse(isCurrentOfflineDownloadQuality(download, NavidromeAudioQuality.Original))
        assertEquals("重新下载 原始音质", navidromeDownloadMenuLabel(NavidromeAudioQuality.Original, download))
    }

    @Test
    fun `navidrome menu labels missing download as download action`() {
        assertEquals("下载 原始音质", navidromeDownloadMenuLabel(NavidromeAudioQuality.Original, null))
    }

    @Test
    fun `compact player offline download status labels download states`() {
        assertEquals("下载到本机", compactPlayerOfflineDownloadStatusLabel(null))
        assertEquals(
            "正在下载",
            compactPlayerOfflineDownloadStatusLabel(completedDownload(status = OfflineDownloadStatus.Pending)),
        )
        assertEquals(
            "正在下载",
            compactPlayerOfflineDownloadStatusLabel(completedDownload(status = OfflineDownloadStatus.Downloading)),
        )
        assertEquals(
            "已离线",
            compactPlayerOfflineDownloadStatusLabel(completedDownload(status = OfflineDownloadStatus.Completed)),
        )
        assertEquals(
            "下载失败",
            compactPlayerOfflineDownloadStatusLabel(completedDownload(status = OfflineDownloadStatus.Failed)),
        )
    }

    @Test
    fun `offline download row indicator maps download states`() {
        assertEquals(
            OfflineDownloadRowIndicatorState.Downloading,
            offlineDownloadRowIndicatorState(completedDownload(status = OfflineDownloadStatus.Pending)),
        )
        assertEquals(
            OfflineDownloadRowIndicatorState.Downloading,
            offlineDownloadRowIndicatorState(completedDownload(status = OfflineDownloadStatus.Downloading)),
        )
        assertEquals(
            OfflineDownloadRowIndicatorState.Downloaded,
            offlineDownloadRowIndicatorState(completedDownload(status = OfflineDownloadStatus.Completed)),
        )
        assertEquals(
            null,
            offlineDownloadRowIndicatorState(
                completedDownload(
                    status = OfflineDownloadStatus.Completed,
                    localMediaLocator = null,
                ),
            ),
        )
        assertEquals(
            null,
            offlineDownloadRowIndicatorState(completedDownload(status = OfflineDownloadStatus.Failed)),
        )
        assertEquals(null, offlineDownloadRowIndicatorState(null))
    }

    @Test
    fun `offline available space labels loading unknown and gigabytes`() {
        assertEquals("计算中", offlineAvailableSpaceLabel(availableSpaceBytes = null, loading = true))
        assertEquals("未知", offlineAvailableSpaceLabel(availableSpaceBytes = null, loading = false))
        assertEquals("0.0 GB", formatOfflineAvailableSpaceGb(0L))
        assertEquals("0.5 GB", formatOfflineAvailableSpaceGb(512L * 1024L * 1024L))
        assertEquals("2.0 GB", formatOfflineAvailableSpaceGb(2L * 1024L * 1024L * 1024L))
        assertEquals("1.2 GB", formatOfflineAvailableSpaceGb(1_288_490_188L))
    }

    @Test
    fun `offline batch download status summary shows determinate total size`() {
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

        assertEquals("正在批量下载 1/2 首 · 1.0 MB / 3.0 MB", summary?.label)
        assertEquals(0.33333334f, summary?.progress)
    }

    @Test
    fun `offline batch download status summary shows approximate and unknown sizes`() {
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

        assertEquals("正在批量下载 0/1 首 · 0 B / 约 2.3 MB", approximate?.label)
        assertEquals(0f, approximate?.progress)
        assertEquals("正在批量下载 0/2 首 · 已下载 512.0 KB · 1 首未知", unknown?.label)
        assertNull(unknown?.progress)
        assertNull(offlineBatchDownloadStatusSummary(null, emptyMap()))
    }

    @Test
    fun `track selection helpers toggle prune and select visible tracks`() {
        val first = sampleTrack("first")
        val second = sampleTrack("second")
        val third = sampleTrack("third")

        assertEquals(listOf("first"), toggleTrackSelection(emptyList(), "first"))
        assertEquals(emptyList(), toggleTrackSelection(listOf("first"), "first"))
        assertEquals(
            listOf("first", "second"),
            toggleAllVisibleTrackSelection(listOf("first"), listOf(first, second)),
        )
        assertEquals(
            emptyList(),
            toggleAllVisibleTrackSelection(listOf("first", "second"), listOf(first, second)),
        )
        assertEquals(listOf("second"), pruneSelectedTrackIds(listOf("first", "second"), listOf(second, third)))
        assertEquals(listOf(second), selectedTracksInVisibleOrder(listOf(first, second), listOf("second")))
    }

    @Test
    fun `detects navidrome tracks for batch quality selection`() {
        assertTrue(hasNavidromeTracks(listOf(sampleNavidromeTrack("nav"))))
        assertFalse(hasNavidromeTracks(listOf(sampleTrack("local"))))
    }

    @Test
    fun `batch selection request only handles new usable keys`() {
        assertFalse(
            shouldHandleBatchSelectionRequest(
                requestKey = 0,
                lastHandledRequestKey = 0,
                supportsBatchDownload = true,
                hasVisibleTracks = true,
            ),
        )
        assertFalse(
            shouldHandleBatchSelectionRequest(
                requestKey = 1,
                lastHandledRequestKey = 1,
                supportsBatchDownload = true,
                hasVisibleTracks = true,
            ),
        )
        assertTrue(
            shouldHandleBatchSelectionRequest(
                requestKey = 2,
                lastHandledRequestKey = 1,
                supportsBatchDownload = true,
                hasVisibleTracks = true,
            ),
        )
        assertFalse(
            shouldHandleBatchSelectionRequest(
                requestKey = 2,
                lastHandledRequestKey = 1,
                supportsBatchDownload = false,
                hasVisibleTracks = true,
            ),
        )
        assertFalse(
            shouldHandleBatchSelectionRequest(
                requestKey = 2,
                lastHandledRequestKey = 1,
                supportsBatchDownload = true,
                hasVisibleTracks = false,
            ),
        )
    }

    @Test
    fun `batch download size estimate sums non navidrome source sizes`() {
        val tracks = listOf(
            sampleWebDavTrack("first", sizeBytes = 1L * 1024L * 1024L),
            sampleWebDavTrack("second", sizeBytes = 2L * 1024L * 1024L),
        )

        val estimate = estimateBatchDownloadSize(tracks, emptyMap())

        assertEquals(3L * 1024L * 1024L, estimate.totalBytes)
        assertEquals(0, estimate.unknownCount)
        assertEquals("3.0 MB", batchDownloadSizeEstimateLabel(estimate))
    }

    @Test
    fun `batch download size estimate uses selected navidrome quality`() {
        val track = sampleNavidromeTrack(
            id = "nav",
            sizeBytes = 5L * 1024L * 1024L,
            durationMs = 60_000L,
        )

        val originalEstimate = estimateBatchDownloadSize(listOf(track), emptyMap(), NavidromeAudioQuality.Original)
        val transcodedEstimate = estimateBatchDownloadSize(listOf(track), emptyMap(), NavidromeAudioQuality.Kbps320)

        assertEquals(5L * 1024L * 1024L, originalEstimate.totalBytes)
        assertFalse(originalEstimate.approximate)
        assertEquals("5.0 MB", batchDownloadSizeEstimateLabel(originalEstimate))
        assertEquals(2_400_000L, transcodedEstimate.totalBytes)
        assertTrue(transcodedEstimate.approximate)
        assertEquals("约 2.3 MB", batchDownloadSizeEstimateLabel(transcodedEstimate))
    }

    @Test
    fun `batch download size estimate skips completed matching quality`() {
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

        assertEquals("无需下载", batchDownloadSizeEstimateLabel(matchingEstimate))
        assertEquals(2, matchingEstimate.skippedCount)
        assertEquals("约 2.3 MB", batchDownloadSizeEstimateLabel(replacementEstimate))
        assertEquals(1, replacementEstimate.skippedCount)
    }

    @Test
    fun `batch download size estimate reports unknown sizes`() {
        val known = sampleWebDavTrack("known", sizeBytes = 1L * 1024L * 1024L)
        val unknown = sampleWebDavTrack("unknown", sizeBytes = 0L)
        val unknownOnly = estimateBatchDownloadSize(listOf(unknown), emptyMap())

        val mixedEstimate = estimateBatchDownloadSize(listOf(known, unknown), emptyMap())

        assertEquals("未知", batchDownloadSizeEstimateLabel(unknownOnly))
        assertEquals("1.0 MB + 1 首未知", batchDownloadSizeEstimateLabel(mixedEstimate))
    }

    @Test
    fun `batch download space check requires one gigabyte reserve`() {
        val estimate = estimateBatchDownloadSize(
            tracks = listOf(sampleWebDavTrack("first", sizeBytes = 512L * 1024L * 1024L)),
            downloadsByTrackId = emptyMap(),
        )

        assertEquals(
            "存储空间不足：预计下载 512.0 MB，需预留 1.0 GB，可用 1.0 GB。",
            batchDownloadInsufficientSpaceMessage(estimate, availableSpaceBytes = 1L * 1024L * 1024L * 1024L),
        )
        assertEquals(
            null,
            batchDownloadInsufficientSpaceMessage(estimate, availableSpaceBytes = 1536L * 1024L * 1024L),
        )
        assertEquals(null, batchDownloadInsufficientSpaceMessage(estimate, availableSpaceBytes = null))
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
}
