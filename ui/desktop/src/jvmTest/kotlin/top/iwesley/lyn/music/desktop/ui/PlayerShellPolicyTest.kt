package top.iwesley.lyn.music.desktop.ui

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.desktop.ui.testing.assertLocalizedEquals
import top.iwesley.lyn.music.desktop.ui.testing.compactPlayerOfflineDownloadStatusLabel
import top.iwesley.lyn.music.core.model.*

class PlayerShellPolicyTest {
    @BeforeTest fun language() { AppLanguageRuntime.update(AppLanguage.SimplifiedChinese) }

    @Test
    fun `queue drawer slide offset follows drawer side`() = runTest {
        assertLocalizedEquals(-420, queueDrawerHorizontalSlideOffset(QueueDrawerSide.Start, 420))
        assertLocalizedEquals(420, queueDrawerHorizontalSlideOffset(QueueDrawerSide.End, 420))
    }

    @Test
    fun `compact player offline download status labels download states`() = runTest {
        assertLocalizedEquals("下载到本机", compactPlayerOfflineDownloadStatusLabel(null))
        assertLocalizedEquals(
            "正在下载",
            compactPlayerOfflineDownloadStatusLabel(completedDownload(status = OfflineDownloadStatus.Pending)),
        )
        assertLocalizedEquals(
            "正在下载",
            compactPlayerOfflineDownloadStatusLabel(completedDownload(status = OfflineDownloadStatus.Downloading)),
        )
        assertLocalizedEquals(
            "已离线",
            compactPlayerOfflineDownloadStatusLabel(completedDownload(status = OfflineDownloadStatus.Completed)),
        )
        assertLocalizedEquals(
            "下载失败",
            compactPlayerOfflineDownloadStatusLabel(completedDownload(status = OfflineDownloadStatus.Failed)),
        )
    }

    @Test
    fun `secondary toast shows online favorite error before playlist success`() = runTest {
        val message = secondaryToastMessage(
            onlineFavoritesErrorMessage = "在线收藏失败",
            onlinePlaylistsErrorMessage = null,
            playlistsMessage = "歌单已创建",
            onlineFavoritesMessage = "已收藏",
            onlinePlaylistsMessage = null,
        )

        assertLocalizedEquals("在线收藏失败", message)
    }

    @Test
    fun `secondary toast includes online favorite success message`() = runTest {
        val message = secondaryToastMessage(
            onlineFavoritesErrorMessage = null,
            onlinePlaylistsErrorMessage = null,
            playlistsMessage = null,
            onlineFavoritesMessage = "已收藏",
            onlinePlaylistsMessage = "在线歌单已更新",
        )

        assertLocalizedEquals("已收藏", message)
    }

    @Test
    fun `secondary toast returns null without secondary messages`() = runTest {
        assertNull(
            secondaryToastMessage(
                onlineFavoritesErrorMessage = null,
                onlinePlaylistsErrorMessage = null,
                playlistsMessage = null,
                onlineFavoritesMessage = null,
                onlinePlaylistsMessage = null,
            ),
        )
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
}
