package top.iwesley.lyn.music

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.testing.libraryFolderDetailSubtitle
import top.iwesley.lyn.music.testing.libraryFolderSummaryLabel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import top.iwesley.lyn.music.core.model.Track

class LibraryFolderTreeTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `folder tree keeps matching paths separate by source`() = runTest {
        val tree = deriveLibraryFolderTree(
            tracks = listOf(
                sampleFolderTrack(
                    id = "local-song",
                    sourceId = "local",
                    relativePath = "Artist/Album/Song.flac",
                ),
                sampleFolderTrack(
                    id = "dav-song",
                    sourceId = "dav",
                    relativePath = "Artist/Album/Song.flac",
                ),
            ),
            sourceLabelsById = mapOf(
                "local" to "本地音乐",
                "dav" to "WebDAV",
            ),
        )

        assertEquals(setOf("本地音乐", "WebDAV"), tree.rootFolders.map { it.name }.toSet())
        assertEquals(6, tree.folderCount)
        assertEquals(
            listOf("Artist"),
            tree.childFoldersByKey.getValue(LibraryFolderKey("local", "")).map { it.name },
        )
        assertEquals(
            listOf("Artist"),
            tree.childFoldersByKey.getValue(LibraryFolderKey("dav", "")).map { it.name },
        )
        assertEquals(
            listOf("local-song"),
            tree.directTracksByKey.getValue(LibraryFolderKey("local", "Artist/Album")).map { it.id },
        )
        assertEquals(
            listOf("dav-song"),
            tree.directTracksByKey.getValue(LibraryFolderKey("dav", "Artist/Album")).map { it.id },
        )
    }

    @Test
    fun `folder tree handles nested root blank and backslash paths`() = runTest {
        val tree = deriveLibraryFolderTree(
            tracks = listOf(
                sampleFolderTrack(
                    id = "root-song",
                    sourceId = "local",
                    relativePath = "Loose.mp3",
                ),
                sampleFolderTrack(
                    id = "blank-path",
                    sourceId = "local",
                    relativePath = "",
                ),
                sampleFolderTrack(
                    id = "nested-song",
                    sourceId = "local",
                    relativePath = "Artist\\Album\\Track.flac",
                ),
            ),
            sourceLabelsById = mapOf("local" to "本地音乐"),
        )
        val sourceRootKey = LibraryFolderKey("local", "")
        val artistKey = LibraryFolderKey("local", "Artist")
        val albumKey = LibraryFolderKey("local", "Artist/Album")

        assertEquals(listOf("本地音乐"), tree.rootFolders.map { it.name })
        assertEquals(listOf("Artist"), tree.childFoldersByKey.getValue(sourceRootKey).map { it.name })
        assertEquals(setOf("root-song", "blank-path"), tree.directTracksByKey.getValue(sourceRootKey).map { it.id }.toSet())
        assertEquals(listOf("Album"), tree.childFoldersByKey.getValue(artistKey).map { it.name })
        assertEquals(listOf("nested-song"), tree.directTracksByKey.getValue(albumKey).map { it.id })
        assertEquals(3, tree.nodesByKey.getValue(sourceRootKey).trackCount)
        assertEquals(1, tree.nodesByKey.getValue(albumKey).directTrackCount)
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
