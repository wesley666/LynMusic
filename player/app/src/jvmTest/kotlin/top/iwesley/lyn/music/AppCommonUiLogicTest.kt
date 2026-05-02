package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.OfflineDownload
import top.iwesley.lyn.music.core.model.OfflineDownloadStatus

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

    private fun completedDownload(
        quality: NavidromeAudioQuality = NavidromeAudioQuality.Original,
        localMediaLocator: String? = "/tmp/offline/song.mp3",
        status: OfflineDownloadStatus = OfflineDownloadStatus.Completed,
    ): OfflineDownload {
        return OfflineDownload(
            trackId = "song-1",
            sourceId = "source-1",
            originalMediaLocator = "lynmusic-navidrome://source-1/song-1",
            localMediaLocator = localMediaLocator,
            quality = quality,
            status = status,
            downloadedBytes = 1_024L,
        )
    }
}
