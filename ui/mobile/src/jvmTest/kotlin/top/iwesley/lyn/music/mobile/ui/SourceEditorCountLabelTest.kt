package top.iwesley.lyn.music.mobile.ui

import top.iwesley.lyn.music.*

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import top.iwesley.lyn.music.mobile.ui.testing.assertLocalizedEquals as assertEquals
import top.iwesley.lyn.music.mobile.ui.testing.remoteSourceEditorTrackCountLabel
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode

class SourceEditorCountLabelTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
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
