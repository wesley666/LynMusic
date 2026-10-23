package top.iwesley.lyn.music.desktop.ui

import top.iwesley.lyn.music.*

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.desktop.ui.testing.libraryFolderDetailSubtitle
import top.iwesley.lyn.music.desktop.ui.testing.libraryFolderSummaryLabel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import top.iwesley.lyn.music.core.model.Track

class LibraryFolderLabelTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }





    @Test
    fun `folder labels and summaries use source fallback and path display`() = runTest {
        val tree = deriveLibraryFolderTree(
            tracks = listOf(
                sampleFolderTrack(
                    id = "song",
                    sourceId = "source-1",
                    relativePath = "Parent/Child/Song.flac",
                ),
            ),
            sourceLabelsById = emptyMap(),
        )
        val root = tree.nodesByKey.getValue(LibraryFolderKey("source-1", ""))
        val parent = tree.nodesByKey.getValue(LibraryFolderKey("source-1", "Parent"))
        val child = assertNotNull(tree.nodesByKey[LibraryFolderKey("source-1", "Parent/Child")])

        assertEquals("source-1", root.name)
        assertEquals("来源根目录", libraryFolderDetailSubtitle(root))
        assertEquals("Parent", libraryFolderDetailSubtitle(parent))
        assertEquals("1 首歌曲 · 1 个子文件夹", libraryFolderSummaryLabel(parent))
        assertEquals("1 首歌曲", libraryFolderSummaryLabel(child))
    }
}

private fun sampleFolderTrack(
    id: String,
    sourceId: String,
    relativePath: String,
): Track {
    return Track(
        id = id,
        sourceId = sourceId,
        title = id,
        durationMs = 180_000L,
        mediaLocator = "file:///music/$id.flac",
        relativePath = relativePath,
    )
}
