package top.iwesley.lyn.music

import top.iwesley.lyn.music.testing.importScanProgressLabel
import top.iwesley.lyn.music.testing.importSourceTrackCountLabel
import kotlinx.coroutines.test.runTest


import top.iwesley.lyn.music.testing.remoteSourceEditorTrackCountLabel

import kotlin.test.Test
import top.iwesley.lyn.music.testing.assertLocalizedEquals as assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.ImportScanFailure
import top.iwesley.lyn.music.core.model.ImportScanPhase
import top.iwesley.lyn.music.core.model.ImportScanProgress
import top.iwesley.lyn.music.core.model.ImportScanSummary
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode

class SourceCardLogicTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `scan summary presentation is absent when summary is missing`() = runTest {
        val presentation = buildSourceScanSummaryPresentation(
            summary = null,
            canShowFailures = true,
        )

        assertNull(presentation)
    }

    @Test
    fun `scan summary presentation keeps failure action hidden when no failures exist`() = runTest {
        val presentation = buildSourceScanSummaryPresentation(
            summary = ImportScanSummary(
                sourceId = "nav-1",
                discoveredAudioFileCount = 3,
                importedTrackCount = 3,
            ),
            canShowFailures = true,
        )

        requireNotNull(presentation)
        assertEquals("发现 3 个音频文件，成功导入 3 首，0 个失败", presentation.summaryText)
        assertFalse(presentation.showFailuresButton)
    }

    @Test
    fun `scan summary presentation shows failure action for navidrome failures`() = runTest {
        val presentation = buildSourceScanSummaryPresentation(
            summary = ImportScanSummary(
                sourceId = "nav-1",
                discoveredAudioFileCount = 3,
                importedTrackCount = 2,
                failures = listOf(
                    ImportScanFailure(
                        relativePath = "Artist A/Album A/Bad.ogg",
                        reason = "当前平台暂不支持导入该音频格式。",
                    ),
                ),
            ),
            canShowFailures = true,
        )

        requireNotNull(presentation)
        assertEquals("发现 3 个音频文件，成功导入 2 首，1 个失败", presentation.summaryText)
        assertTrue(presentation.showFailuresButton)
        assertEquals(listOf("Artist A/Album A/Bad.ogg"), presentation.summary.failures.map { it.relativePath })
    }

    @Test
    fun `scan summary presentation keeps failure action hidden without handler`() = runTest {
        val presentation = buildSourceScanSummaryPresentation(
            summary = ImportScanSummary(
                sourceId = "nav-1",
                discoveredAudioFileCount = 2,
                importedTrackCount = 1,
                failures = listOf(
                    ImportScanFailure(
                        relativePath = "Artist A/Album A/Bad.ogg",
                        reason = "当前平台暂不支持导入该音频格式。",
                    ),
                ),
            ),
            canShowFailures = false,
        )

        requireNotNull(presentation)
        assertFalse(presentation.showFailuresButton)
    }

    @Test
    fun `scan summary presentation uses online mode wording for online source`() = runTest {
        val presentation = buildSourceScanSummaryPresentation(
            summary = ImportScanSummary(
                sourceId = "nav-1",
                discoveredAudioFileCount = 456_748,
                importedTrackCount = 0,
            ),
            canShowFailures = true,
            isOnlineSource = true,
            remoteTrackCount = 456_748,
        )

        requireNotNull(presentation)
        assertEquals(
            "在线模式已启用，远端共有 456748 首歌曲，未写入本地曲库索引。",
            presentation.summaryText,
        )
        assertFalse(presentation.showFailuresButton)
    }

    @Test
    fun `scan progress label omits total when source total is unknown`() = runTest {
        val progress = ImportScanProgress(
            sourceId = "nav-1",
            phase = ImportScanPhase.Scanning,
            importedTrackCount = 128,
        )

        assertEquals("已导入第 128 首", importScanProgressLabel(progress))
        assertNull(importScanProgressFraction(progress))
    }

    @Test
    fun `scan progress label includes total when source total is known`() = runTest {
        val progress = ImportScanProgress(
            sourceId = "emby-1",
            phase = ImportScanPhase.Scanning,
            importedTrackCount = 128,
            totalTrackCount = 430,
        )

        assertEquals("正在导入第 128/430 首", importScanProgressLabel(progress))
        assertEquals(128f / 430f, importScanProgressFraction(progress))
    }

    @Test
    fun `persisting progress label shows library update state`() = runTest {
        val progress = ImportScanProgress(
            sourceId = "emby-1",
            phase = ImportScanPhase.Persisting,
            importedTrackCount = 430,
            totalTrackCount = 430,
        )

        assertEquals("正在更新曲库…", importScanProgressLabel(progress))
        assertNull(importScanProgressFraction(progress))
    }

    @Test
    fun `source track count label uses local count for indexed source`() = runTest {
        assertEquals(
            "12 首歌曲",
            importSourceTrackCountLabel(
                indexMode = ImportSourceIndexMode.LOCAL_INDEX,
                localTrackCount = 12,
                remoteTrackCount = null,
            ),
        )
    }

    @Test
    fun `source track count label falls back to zero for missing indexed count`() = runTest {
        assertEquals(
            "0 首歌曲",
            importSourceTrackCountLabel(
                indexMode = ImportSourceIndexMode.LOCAL_INDEX,
                localTrackCount = null,
                remoteTrackCount = null,
            ),
        )
    }

    @Test
    fun `source track count label uses remote count for online source`() = runTest {
        assertEquals(
            "77 首远端歌曲",
            importSourceTrackCountLabel(
                indexMode = ImportSourceIndexMode.ONLINE,
                localTrackCount = 12,
                remoteTrackCount = 77,
            ),
        )
    }

    @Test
    fun `source track count label does not treat unknown online count as empty`() = runTest {
        assertEquals(
            "远端歌曲数未知",
            importSourceTrackCountLabel(
                indexMode = ImportSourceIndexMode.ONLINE,
                localTrackCount = 12,
                remoteTrackCount = null,
            ),
        )
    }

    @Test
    fun `remote source editor shows current imported track count`() = runTest {
        assertEquals(
            "当前已导入 32 首歌曲",
            remoteSourceEditorTrackCountLabel(
                indexMode = ImportSourceIndexMode.LOCAL_INDEX,
                currentTrackCount = 32,
                remoteTrackCount = null,
            ),
        )
    }

    @Test
    fun `remote source editor shows empty imported track count state`() = runTest {
        assertEquals(
            "当前还没有导入歌曲",
            remoteSourceEditorTrackCountLabel(
                indexMode = ImportSourceIndexMode.LOCAL_INDEX,
                currentTrackCount = null,
                remoteTrackCount = null,
            ),
        )
    }

    @Test
    fun `remote source editor shows online remote track count`() = runTest {
        assertEquals(
            "当前远端共有 77 首歌曲",
            remoteSourceEditorTrackCountLabel(
                indexMode = ImportSourceIndexMode.ONLINE,
                currentTrackCount = 32,
                remoteTrackCount = 77,
            ),
        )
    }

    @Test
    fun `remote source editor does not show stale local count for unknown online remote count`() = runTest {
        assertEquals(
            "当前远端歌曲数未知",
            remoteSourceEditorTrackCountLabel(
                indexMode = ImportSourceIndexMode.ONLINE,
                currentTrackCount = 32,
                remoteTrackCount = null,
            ),
        )
    }
}
