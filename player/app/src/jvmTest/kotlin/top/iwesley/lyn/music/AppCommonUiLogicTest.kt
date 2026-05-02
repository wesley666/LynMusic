package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.OfflineDownload
import top.iwesley.lyn.music.core.model.OfflineDownloadStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.core.model.buildWebDavLocator

class AppCommonUiLogicTest {

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
    fun `offline available space labels loading unknown and gigabytes`() {
        assertEquals("计算中", offlineAvailableSpaceLabel(availableSpaceBytes = null, loading = true))
        assertEquals("未知", offlineAvailableSpaceLabel(availableSpaceBytes = null, loading = false))
        assertEquals("0.0 GB", formatOfflineAvailableSpaceGb(0L))
        assertEquals("0.5 GB", formatOfflineAvailableSpaceGb(512L * 1024L * 1024L))
        assertEquals("2.0 GB", formatOfflineAvailableSpaceGb(2L * 1024L * 1024L * 1024L))
        assertEquals("1.2 GB", formatOfflineAvailableSpaceGb(1_288_490_188L))
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

    private fun completedDownload(
        trackId: String = "song-1",
        quality: NavidromeAudioQuality = NavidromeAudioQuality.Original,
        localMediaLocator: String? = "/tmp/offline/song.mp3",
        status: OfflineDownloadStatus = OfflineDownloadStatus.Completed,
    ): OfflineDownload {
        return OfflineDownload(
            trackId = trackId,
            sourceId = "source-1",
            originalMediaLocator = "remote://$trackId",
            localMediaLocator = localMediaLocator,
            quality = quality,
            status = status,
            downloadedBytes = 1_024L,
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
