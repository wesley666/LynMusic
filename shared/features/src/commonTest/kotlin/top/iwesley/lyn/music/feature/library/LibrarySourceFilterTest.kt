package top.iwesley.lyn.music.feature.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.ImportSource
import top.iwesley.lyn.music.core.model.ImportSourceIndexMode
import top.iwesley.lyn.music.core.model.ImportSourceType
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.testing.UiResourceTest
import top.iwesley.lyn.music.testing.assertLocalizedEquals

class LibrarySourceFilterTest : UiResourceTest() {
    @Test
    fun `options keep protocol order and individual sources in repository order`() = runTest {
        val sources = listOf(
            source("dav-2", ImportSourceType.WEBDAV, "公司"),
            source("nav-1", ImportSourceType.NAVIDROME, "在线").copy(indexMode = ImportSourceIndexMode.ONLINE),
            source("dav-disabled", ImportSourceType.WEBDAV, "停用").copy(enabled = false),
            source("local-1", ImportSourceType.LOCAL_FOLDER, "下载目录"),
            source("local-2", ImportSourceType.LOCAL_FOLDER, "NAS 缓存"),
            source("dav-1", ImportSourceType.WEBDAV, "家里"),
        )

        val options = buildLibrarySourceFilterOptions(sources)

        assertEquals(
            listOf(
                LibrarySourceFilter.ALL,
                LibrarySourceFilter.LOCAL_FOLDER,
                LibrarySourceFilter.Source("dav-2"),
                LibrarySourceFilter.Source("dav-1"),
                LibrarySourceFilter.DOWNLOADED,
            ),
            options.map { it.filter },
        )
        assertLocalizedEquals("本地文件夹", options[1].label)
        assertLocalizedEquals("本地文件夹", librarySourceFilterButtonLabel(LibrarySourceFilter.LOCAL_FOLDER, options))
        assertLocalizedEquals("WebDAV · 公司", options[2].label)
        assertLocalizedEquals("WebDAV · 家里", options[3].label)
    }

    @Test
    fun `local folder filter combines local source ids and excludes remote and unavailable ids`() {
        val types = mapOf(
            "local-1" to ImportSourceType.LOCAL_FOLDER,
            "local-2" to ImportSourceType.LOCAL_FOLDER,
            "dav-1" to ImportSourceType.WEBDAV,
        )
        val track = Track("track", "local-1", "Song", mediaLocator = "song", relativePath = "song")
        assertTrue(matchesLibrarySourceFilter(track, LibrarySourceFilter.LOCAL_FOLDER, types, emptyMap()))
        assertTrue(matchesLibrarySourceFilter(track.copy(sourceId = "local-2"), LibrarySourceFilter.LOCAL_FOLDER, types, emptyMap()))
        assertFalse(matchesLibrarySourceFilter(track.copy(sourceId = "dav-1"), LibrarySourceFilter.LOCAL_FOLDER, types, emptyMap()))
        assertFalse(matchesLibrarySourceFilter(track.copy(sourceId = "disabled-local"), LibrarySourceFilter.LOCAL_FOLDER, types, emptyMap()))
    }

    @Test
    fun `saved individual local selections normalize to the local folder group`() {
        val sources = listOf(
            source("local-1", ImportSourceType.LOCAL_FOLDER, "下载目录"),
            source("local-2", ImportSourceType.LOCAL_FOLDER, "NAS 缓存"),
            source("dav-1", ImportSourceType.WEBDAV, "公司"),
        )
        val options = buildLibrarySourceFilterOptions(sources)
        val types = sources.associate { it.id to it.type }
        assertEquals(LibrarySourceFilter.LOCAL_FOLDER, LibrarySourceFilter.Source("local-1").availableOrAll(options, types))
        assertEquals(LibrarySourceFilter.LOCAL_FOLDER, LibrarySourceFilter.Source("local-2").availableOrAll(options, types))
        assertEquals(LibrarySourceFilter.Source("dav-1"), LibrarySourceFilter.Source("dav-1").availableOrAll(options, types))
        assertEquals(LibrarySourceFilter.ALL, LibrarySourceFilter.LOCAL_FOLDER.availableOrAll(buildLibrarySourceFilterOptions(), emptyMap()))
    }

    @Test
    fun `names are trimmed and blank names fall back to existing source id`() = runTest {
        val options = buildLibrarySourceFilterOptions(
            listOf(
                source("dav-1", ImportSourceType.WEBDAV, "  家里  "),
                source("dav-2", ImportSourceType.WEBDAV, "  "),
            ),
        )

        assertLocalizedEquals("WebDAV · 家里", options[1].label)
        assertLocalizedEquals("WebDAV · dav-2", options[2].label)
        assertLocalizedEquals(
            "WebDAV · 家里",
            librarySourceFilterButtonLabel(LibrarySourceFilter.Source("dav-1"), options),
        )
    }

    @Test
    fun `same names and renamed sources retain distinct stable selection identities`() = runTest {
        val first = source("dav-1", ImportSourceType.WEBDAV, "NAS")
        val second = source("dav-2", ImportSourceType.WEBDAV, "NAS")
        val before = buildLibrarySourceFilterOptions(listOf(first, second))
        val after = buildLibrarySourceFilterOptions(listOf(first.copy(label = "家里"), second))

        assertEquals(before.map { it.filter }, after.map { it.filter })
        assertLocalizedEquals("WebDAV · 家里", after[1].label)
        assertLocalizedEquals("WebDAV · NAS", after[2].label)
        val track = Track(id = "track", sourceId = second.id, title = "Song", mediaLocator = "song", relativePath = "song")
        val types = mapOf(first.id to first.type, second.id to second.type)
        assertFalse(matchesLibrarySourceFilter(track, before[1].filter, types, emptyMap()))
        assertTrue(matchesLibrarySourceFilter(track, before[2].filter, types, emptyMap()))
    }

    @Test
    fun `selection codec preserves source identity and existing special filters`() {
        listOf(
            LibrarySourceFilter.ALL,
            LibrarySourceFilter.LOCAL_FOLDER,
            LibrarySourceFilter.DOWNLOADED,
            LibrarySourceFilter.Source("dav-1"),
            LibrarySourceFilter.Source("NAS:家里/音乐"),
        ).forEach { filter ->
            assertEquals(filter, decodeLibrarySourceFilter(encodeLibrarySourceFilter(filter)))
        }
        assertEquals(LibrarySourceFilter.ALL, decodeLibrarySourceFilter("ALL"))
        assertEquals(LibrarySourceFilter.LOCAL_FOLDER, decodeLibrarySourceFilter("LOCAL_FOLDER"))
        assertEquals(LibrarySourceFilter.DOWNLOADED, decodeLibrarySourceFilter("DOWNLOADED"))
    }

    @Test
    fun `old protocol values and malformed selections safely reset to all`() {
        val oldValues = ImportSourceType.entries.filter { it != ImportSourceType.LOCAL_FOLDER }.map { it.name }
        (oldValues + listOf(null, "", "unknown", "source:", "source:   ")).forEach { value ->
            assertEquals(LibrarySourceFilter.ALL, decodeLibrarySourceFilter(value), value)
        }
    }

    private fun source(id: String, type: ImportSourceType, label: String) =
        ImportSource(id = id, type = type, label = label, rootReference = "existing-root")
}
