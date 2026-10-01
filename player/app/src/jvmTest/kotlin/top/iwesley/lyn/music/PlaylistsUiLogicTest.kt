package top.iwesley.lyn.music

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.testing.formatPlaylistImportFailedLineIssue
import top.iwesley.lyn.music.testing.playlistImportReportSummary

import top.iwesley.lyn.music.resources.*

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.PlaylistDetail
import top.iwesley.lyn.music.core.model.PlaylistSummary
import top.iwesley.lyn.music.data.repository.PlaylistImportLineIssue
import top.iwesley.lyn.music.data.repository.PlaylistImportReport
import top.iwesley.lyn.music.data.repository.PlaylistImportFailedLineIssue
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.AppLanguageRuntime
import top.iwesley.lyn.music.core.model.uiText

class PlaylistsUiLogicTest {
    @Test fun failedImportLineFollowsLanguageAndKeepsUserAndLegacyText() = runTest {
        val previousLanguage = AppLanguageRuntime.appLanguage.value
        try {
            val issue = PlaylistImportFailedLineIssue(7, "用户歌曲 - 用户歌手", "诊断原文", uiText(Res.string.playlist_add_track_failed))
            val expected = listOf(
                AppLanguage.English to "Line 7: 用户歌曲 - 用户歌手 (Failed to add to playlist.)",
                AppLanguage.SimplifiedChinese to "第 7 行：用户歌曲 - 用户歌手（加入歌单失败。）",
                AppLanguage.TraditionalChinese to "第 7 行：用户歌曲 - 用户歌手（加入歌單失敗。）",
                AppLanguage.English to "Line 7: 用户歌曲 - 用户歌手 (Failed to add to playlist.)",
            )
            expected.forEach { (language, text) ->
                AppLanguageRuntime.update(language)
                assertEquals(text, formatPlaylistImportFailedLineIssue(issue))
                assertTrue(formatPlaylistImportFailedLineIssue(issue.copy(messageText = null)).contains("诊断原文"))
            }
            assertEquals("诊断原文", issue.message)
        } finally { AppLanguageRuntime.update(previousLanguage) }
    }

    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `detail loading stays hidden when no playlist is selected`() = runTest {
        val state = buildPlaylistDetailPresentationState(
            selectedPlaylistId = null,
            detail = null,
            playlists = samplePlaylists(),
        )

        assertFalse(state.shouldShowDetailPane)
        assertFalse(state.isDetailSwitchLoading)
        assertNull(state.resolvedDetail)
        assertNull(state.requestedPlaylistName)
    }

    @Test
    fun `detail loading shows while selected playlist detail is still missing`() = runTest {
        val state = buildPlaylistDetailPresentationState(
            selectedPlaylistId = "playlist-2",
            detail = null,
            playlists = samplePlaylists(),
        )

        assertTrue(state.shouldShowDetailPane)
        assertTrue(state.isDetailSwitchLoading)
        assertNull(state.resolvedDetail)
        assertEquals("通勤", state.requestedPlaylistName)
    }

    @Test
    fun `detail loading shows and hides stale detail when detail id does not match selection`() = runTest {
        val state = buildPlaylistDetailPresentationState(
            selectedPlaylistId = "playlist-2",
            detail = PlaylistDetail(id = "playlist-1", name = "晨跑"),
            playlists = samplePlaylists(),
        )

        assertTrue(state.shouldShowDetailPane)
        assertTrue(state.isDetailSwitchLoading)
        assertNull(state.resolvedDetail)
        assertEquals("通勤", state.requestedPlaylistName)
    }

    @Test
    fun `detail loading hides when matching playlist detail is ready`() = runTest {
        val detail = PlaylistDetail(id = "playlist-2", name = "通勤")
        val state = buildPlaylistDetailPresentationState(
            selectedPlaylistId = "playlist-2",
            detail = detail,
            playlists = samplePlaylists(),
        )

        assertTrue(state.shouldShowDetailPane)
        assertFalse(state.isDetailSwitchLoading)
        assertEquals(detail, state.resolvedDetail)
        assertEquals("通勤", state.requestedPlaylistName)
    }

    @Test
    fun `playlist summary artwork locator ignores blank values`() = runTest {
        assertNull(playlistSummaryArtworkLocator(PlaylistSummary(id = "empty", name = "空")))
        assertNull(playlistSummaryArtworkLocator(PlaylistSummary(id = "blank", name = "空白", artworkLocator = " ")))
        assertEquals(
            "/art/latest.jpg",
            playlistSummaryArtworkLocator(
                PlaylistSummary(id = "cover", name = "封面", artworkLocator = "/art/latest.jpg"),
            ),
        )
        assertNull(playlistSummaryArtworkCacheKey(PlaylistSummary(id = "empty-key", name = "空")))
        assertNull(
            playlistSummaryArtworkCacheKey(
                PlaylistSummary(id = "blank-key", name = "空白", artworkCacheKey = " "),
            ),
        )
        assertEquals(
            "album:local-1:album-1",
            playlistSummaryArtworkCacheKey(
                PlaylistSummary(id = "cover-key", name = "封面", artworkCacheKey = "album:local-1:album-1"),
            ),
        )
    }

    @Test
    fun `playlist import action is available only for loaded detail`() = runTest {
        assertFalse(canShowPlaylistImportAction(null))
        assertTrue(canShowPlaylistImportAction(PlaylistDetail(id = "playlist-1", name = "晨跑")))
    }

    @Test
    fun `playlist import confirm requires text and idle state`() = runTest {
        assertFalse(canConfirmPlaylistImport("", isImporting = false))
        assertFalse(canConfirmPlaylistImport("   ", isImporting = false))
        assertFalse(canConfirmPlaylistImport("咖啡恋曲 - 旺福", isImporting = true))
        assertTrue(canConfirmPlaylistImport("咖啡恋曲 - 旺福", isImporting = false))
    }

    @Test
    fun `playlist import assistant url stays fixed`() = runTest {
        assertEquals("https://music.unmeta.cn/", PlaylistImportAssistantUrl)
    }

    @Test
    fun `playlist import text field lines adapt to dialog height`() = runTest {
        assertEquals(2, playlistImportTextFieldLines(340.dp))
        assertEquals(3, playlistImportTextFieldLines(400.dp))
        assertEquals(4, playlistImportTextFieldLines(460.dp))
        assertEquals(6, playlistImportTextFieldLines(560.dp))
    }

    @Test
    fun `playlist import dialog layout shrinks for app display size`() = runTest {
        val defaultLayout = playlistImportDialogLayout(maxWidth = 393.dp, maxHeight = 820.dp)
        val largeDisplayLayout = playlistImportDialogLayout(maxWidth = 360.dp, maxHeight = 560.dp)

        assertEquals(560.dp, defaultLayout.maxHeight)
        assertTrue(largeDisplayLayout.maxHeight < defaultLayout.maxHeight)
        assertTrue(largeDisplayLayout.contentVerticalPadding < defaultLayout.contentVerticalPadding)
        assertEquals(3, largeDisplayLayout.textFieldLines)
    }

    @Test
    fun `playlist import dialog layout keeps usable minimum on tight height`() = runTest {
        val layout = playlistImportDialogLayout(maxWidth = 320.dp, maxHeight = 380.dp)

        assertEquals(12.dp, layout.outerHorizontalPadding)
        assertEquals(8.dp, layout.outerVerticalPadding)
        assertEquals(320.dp, layout.maxHeight)
        assertEquals(2, layout.textFieldLines)
    }

    @Test
    fun `playlist import report summary includes successful and skipped counts`() = runTest {
        val summary = playlistImportReportSummary(
            PlaylistImportReport(
                addedCount = 2,
                alreadyExistsCount = 1,
                duplicateInputCount = 1,
                malformedLines = listOf(PlaylistImportLineIssue(lineNumber = 4, rawText = "坏格式")),
                notMatchedLines = listOf(PlaylistImportLineIssue(lineNumber = 5, rawText = "找不到 - 歌手")),
            ),
        )

        assertEquals("已加入 2 首，已存在 1 首，重复 1 首，未导入 2 行", summary)
    }

    @Test
    fun `playlist track trailing width follows duration visibility`() = runTest {
        assertEquals(112.dp, playlistTrackTrailingWidth(selectionMode = false, showDuration = true))
        assertEquals(48.dp, playlistTrackTrailingWidth(selectionMode = false, showDuration = false))
        assertEquals(56.dp, playlistTrackTrailingWidth(selectionMode = true, showDuration = true))
        assertEquals(0.dp, playlistTrackTrailingWidth(selectionMode = true, showDuration = false))
    }

    private fun samplePlaylists(): List<PlaylistSummary> = listOf(
        PlaylistSummary(id = "playlist-1", name = "晨跑"),
        PlaylistSummary(id = "playlist-2", name = "通勤"),
    )
}
