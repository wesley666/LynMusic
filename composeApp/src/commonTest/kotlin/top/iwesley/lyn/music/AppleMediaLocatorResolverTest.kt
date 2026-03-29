package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import top.iwesley.lyn.music.core.model.AppleMediaLocatorResolver
import top.iwesley.lyn.music.core.model.AppleResolvedMediaLocator

class AppleMediaLocatorResolverTest {
    @Test
    fun resolvesFileUrls() {
        val result = AppleMediaLocatorResolver.resolve("file:///tmp/demo.mp3")

        assertEquals(
            AppleResolvedMediaLocator.FileUrl("file:///tmp/demo.mp3"),
            result,
        )
    }

    @Test
    fun resolvesAbsolutePaths() {
        val result = AppleMediaLocatorResolver.resolve("/Users/demo/Music/test.m4a")

        assertEquals(
            AppleResolvedMediaLocator.AbsolutePath("/Users/demo/Music/test.m4a"),
            result,
        )
    }

    @Test
    fun rejectsSambaLocator() {
        val result = AppleMediaLocatorResolver.resolve("lynmusic-smb://source-id/share/song.mp3")

        val unsupported = assertIs<AppleResolvedMediaLocator.Unsupported>(result)
        assertEquals("Apple 平台 v1 暂不支持 Samba locator。", unsupported.message)
    }

    @Test
    fun rejectsWebDavLocator() {
        val result = AppleMediaLocatorResolver.resolve("lynmusic-webdav://source-id/music/song.mp3")

        val unsupported = assertIs<AppleResolvedMediaLocator.Unsupported>(result)
        assertEquals("Apple 平台 v1 暂不支持 WebDAV locator。", unsupported.message)
    }
}
