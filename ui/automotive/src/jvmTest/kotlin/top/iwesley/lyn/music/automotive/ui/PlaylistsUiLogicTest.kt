package top.iwesley.lyn.music.automotive.ui

import top.iwesley.lyn.music.*

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.automotive.ui.testing.formatPlaylistImportFailedLineIssue
import top.iwesley.lyn.music.automotive.ui.testing.playlistImportReportSummary

import top.iwesley.lyn.music.resources.*

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.PlaylistTrackEntry
import top.iwesley.lyn.music.feature.online.*
import top.iwesley.lyn.music.feature.playlists.*
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

    @Test fun playlistBindingKeepsLoadedDetailsAndReleasesOnlyAfterPop() {
        val route = MusicDetailRoute(entryId = "playlist:p", kind = "playlist", entityId = "p")
        val binding = PlaylistDetailBinding()
        val calls = mutableListOf<PlaylistsIntent>()
        binding.sync(route, PlaylistsState(), OnlinePlaylistsState(), emptySet(), false, calls::add, {})
        assertEquals(listOf<PlaylistsIntent>(PlaylistsIntent.SelectPlaylist("p")), calls)
        val loaded = PlaylistsState(selectedPlaylistId = "p", selectedPlaylist = PlaylistDetail("p", "Road"))
        repeat(3) { binding.sync(route, loaded, OnlinePlaylistsState(), emptySet(), false, calls::add, {}) }
        assertEquals(1, calls.size)
        binding.sync(null, loaded, OnlinePlaylistsState(), emptySet(), false, calls::add, {})
        assertEquals(PlaylistsIntent.BackToList, calls.last())
    }

    @Test fun playlistBindingSuspendsForTemporarySourcesAndDoesNotRetryRetainedErrors() {
        val route = MusicDetailRoute(entryId = "p", kind = "playlist", entityId = "p", sourceId = "a")
        val binding = PlaylistDetailBinding()
        val calls = mutableListOf<OnlinePlaylistsIntent>()
        binding.sync(route, PlaylistsState(), OnlinePlaylistsState(sourceId = "b"), setOf("a", "b"), true, {}, calls::add)
        assertTrue(calls.isEmpty())
        binding.sync(route, PlaylistsState(), OnlinePlaylistsState(sourceId = "b"), setOf("a", "b"), false, {}, calls::add)
        assertEquals(listOf<OnlinePlaylistsIntent>(OnlinePlaylistsIntent.SelectSource("a", persist = false)), calls)
        binding.sync(route, PlaylistsState(), OnlinePlaylistsState(sourceId = "a", selectedPlaylistId = "p"),
            setOf("a"), false, {}, calls::add, hasRetainedFailure = true)
        assertEquals(1, calls.size)
    }

    @Test fun duplicatePlaylistRowsKeepOriginalIndicesAndUniqueKeys() {
        val track = Track("one", "source", "One", mediaLocator = "", relativePath = "one")
        val detail = PlaylistDetail("p", "Road", tracks = listOf(PlaylistTrackEntry(track), PlaylistTrackEntry(track)))
        val rows = playlistDetailRows(detail, PlaylistsState(), MusicDetailRoute(kind = "playlist", entityId = "p"))
        assertEquals(listOf(0, 1), rows.map { it.rawIndex })
        assertEquals(2, rows.map { it.key }.toSet().size)
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
}
