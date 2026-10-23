package top.iwesley.lyn.music.mobile.ui

import top.iwesley.lyn.music.*

import top.iwesley.lyn.music.mobile.ui.testing.artistSummaryLabel
import top.iwesley.lyn.music.mobile.ui.testing.navidromeDownloadMenuLabel
import top.iwesley.lyn.music.mobile.ui.testing.offlineAvailableSpaceLabel

import kotlinx.coroutines.test.runTest

import kotlin.test.Test
import top.iwesley.lyn.music.mobile.ui.testing.assertLocalizedEquals
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

class CollectionUiLogicTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }



    @Test
    fun `navidrome menu labels current offline quality`() = runTest {
        val download = completedDownload(quality = NavidromeAudioQuality.Kbps192)

        assertTrue(isCurrentOfflineDownloadQuality(download, NavidromeAudioQuality.Kbps192))
        assertLocalizedEquals("已下载 192 kbps", navidromeDownloadMenuLabel(NavidromeAudioQuality.Kbps192, download))
        assertLocalizedEquals("重新下载 320 kbps", navidromeDownloadMenuLabel(NavidromeAudioQuality.Kbps320, download))
    }

    @Test
    fun `navidrome menu does not mark completed record without local file as downloaded`() = runTest {
        val download = completedDownload(
            quality = NavidromeAudioQuality.Original,
            localMediaLocator = null,
        )

        assertFalse(isCurrentOfflineDownloadQuality(download, NavidromeAudioQuality.Original))
        assertLocalizedEquals("重新下载 原始音质", navidromeDownloadMenuLabel(NavidromeAudioQuality.Original, download))
    }

    @Test
    fun `navidrome menu labels missing download as download action`() = runTest {
        assertLocalizedEquals("下载 原始音质", navidromeDownloadMenuLabel(NavidromeAudioQuality.Original, null))
    }















    @Test
    fun `library tab online mode does not show favorites message fallback`() = runTest {
        assertNull(
            libraryTabBrowserMessage(
                isOnlineMode = true,
                onlineErrorMessage = null,
                favoritesMessage = "喜欢刷新失败",
            ),
        )
        assertLocalizedEquals(
            "在线曲库加载失败",
            libraryTabBrowserMessage(
                isOnlineMode = true,
                onlineErrorMessage = "在线曲库加载失败",
                favoritesMessage = "喜欢刷新失败",
            ),
        )
    }

    @Test
    fun `library tab local mode keeps favorites message fallback`() = runTest {
        assertLocalizedEquals(
            "本地收藏已更新",
            libraryTabBrowserMessage(
                isOnlineMode = false,
                onlineErrorMessage = "在线曲库加载失败",
                favoritesMessage = "本地收藏已更新",
            ),
        )
    }

    @Test
    fun `library tab dismiss target follows current mode`() = runTest {
        assertLocalizedEquals(
            LibraryTabMessageDismissTarget.OnlineLibrary,
            libraryTabMessageDismissTarget(isOnlineMode = true),
        )
        assertLocalizedEquals(
            LibraryTabMessageDismissTarget.Favorites,
            libraryTabMessageDismissTarget(isOnlineMode = false),
        )
    }

    @Test
    fun `artist summary label hides unknown online counts instead of showing zero`() = runTest {
        assertLocalizedEquals("在线艺人", artistSummaryLabel(trackCount = null, albumCount = null))
        assertLocalizedEquals("12 首歌曲", artistSummaryLabel(trackCount = 12, albumCount = null))
        assertLocalizedEquals("3 张专辑", artistSummaryLabel(trackCount = null, albumCount = 3))
        assertLocalizedEquals("12 首歌曲 · 3 张专辑", artistSummaryLabel(trackCount = 12, albumCount = 3))
    }

    @Test
    fun `offline download row indicator maps download states`() = runTest {
        assertLocalizedEquals(
            OfflineDownloadRowIndicatorState.Downloading,
            offlineDownloadRowIndicatorState(completedDownload(status = OfflineDownloadStatus.Pending)),
        )
        assertLocalizedEquals(
            OfflineDownloadRowIndicatorState.Downloading,
            offlineDownloadRowIndicatorState(completedDownload(status = OfflineDownloadStatus.Downloading)),
        )
        assertLocalizedEquals(
            OfflineDownloadRowIndicatorState.Downloaded,
            offlineDownloadRowIndicatorState(completedDownload(status = OfflineDownloadStatus.Completed)),
        )
        assertLocalizedEquals(
            null,
            offlineDownloadRowIndicatorState(
                completedDownload(
                    status = OfflineDownloadStatus.Completed,
                    localMediaLocator = null,
                ),
            ),
        )
        assertLocalizedEquals(
            null,
            offlineDownloadRowIndicatorState(completedDownload(status = OfflineDownloadStatus.Failed)),
        )
        assertLocalizedEquals(null, offlineDownloadRowIndicatorState(null))
    }

    @Test
    fun `offline available space labels loading unknown and gigabytes`() = runTest {
        assertLocalizedEquals("计算中", offlineAvailableSpaceLabel(availableSpaceBytes = null, loading = true))
        assertLocalizedEquals("未知", offlineAvailableSpaceLabel(availableSpaceBytes = null, loading = false))
        assertLocalizedEquals("0.0 GB", formatOfflineAvailableSpaceGb(0L))
        assertLocalizedEquals("0.5 GB", formatOfflineAvailableSpaceGb(512L * 1024L * 1024L))
        assertLocalizedEquals("2.0 GB", formatOfflineAvailableSpaceGb(2L * 1024L * 1024L * 1024L))
        assertLocalizedEquals("1.2 GB", formatOfflineAvailableSpaceGb(1_288_490_188L))
    }





    @Test
    fun `track selection helpers toggle prune and select visible tracks`() = runTest {
        val first = sampleTrack("first")
        val second = sampleTrack("second")
        val third = sampleTrack("third")

        assertLocalizedEquals(listOf("first"), toggleTrackSelection(emptyList(), "first"))
        assertLocalizedEquals(emptyList(), toggleTrackSelection(listOf("first"), "first"))
        assertLocalizedEquals(
            listOf("first", "second"),
            toggleAllVisibleTrackSelection(listOf("first"), listOf(first, second)),
        )
        assertLocalizedEquals(
            emptyList(),
            toggleAllVisibleTrackSelection(listOf("first", "second"), listOf(first, second)),
        )
        assertLocalizedEquals(listOf("second"), pruneSelectedTrackIds(listOf("first", "second"), listOf(second, third)))
        assertLocalizedEquals(listOf(second), selectedTracksInVisibleOrder(listOf(first, second), listOf("second")))
    }

    @Test
    fun `detects navidrome tracks for batch quality selection`() = runTest {
        assertTrue(hasNavidromeTracks(listOf(sampleNavidromeTrack("nav"))))
        assertFalse(hasNavidromeTracks(listOf(sampleTrack("local"))))
    }



















    @Test
    fun `batch selection request only handles new usable keys`() = runTest {
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
