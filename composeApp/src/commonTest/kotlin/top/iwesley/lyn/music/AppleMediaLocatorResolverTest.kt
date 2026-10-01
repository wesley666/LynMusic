package top.iwesley.lyn.music

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import top.iwesley.lyn.music.core.model.AppleMediaLocatorResolver
import top.iwesley.lyn.music.core.model.AppleResolvedMediaLocator
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.resolveUiText

class AppleMediaLocatorResolverTest {
    @Test
    fun unsupportedLocatorsKeepDiagnosticsAndTranslatedDescriptions() = runTest {
        val cases = listOf(
            "" to listOf("Apple platforms cannot play an empty media locator.", "Apple 平台无法播放空的媒体定位符。", "Apple 平台無法播放空的媒體定位符。"),
            "content://media/audio/1" to listOf("Apple platforms do not support Android content URIs.", "Apple 平台暂不支持 Android content URI。", "Apple 平台暫不支援 Android content URI。"),
            "lynmusic-smb://source-id/share/song.mp3" to listOf("Apple platforms do not support Samba media locators in v1.", "Apple 平台 v1 暂不支持 Samba locator。", "Apple 平台 v1 暫不支援 Samba locator。"),
            "lynmusic-webdav://source-id/music/song.mp3" to listOf("Apple platforms do not support WebDAV media locators in v1.", "Apple 平台 v1 暂不支持 WebDAV locator。", "Apple 平台 v1 暫不支援 WebDAV locator。"),
            "invalid:locator" to listOf("Apple platforms do not support this media locator.", "Apple 平台暂不支持当前媒体定位符。", "Apple 平台暫不支援目前的媒體定位符。"),
        )
        val diagnostics = listOf(
            "apple_media_empty",
            "apple_android_uri_unsupported",
            "apple_samba_unsupported",
            "apple_webdav_unsupported",
            "apple_media_unsupported",
        )
        val languages = listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese)
        cases.forEachIndexed { index, (locator, expected) ->
            val unsupported = assertIs<AppleResolvedMediaLocator.Unsupported>(AppleMediaLocatorResolver.resolve(locator))
            assertEquals(expected, languages.map { resolveUiText(unsupported.messageText, it) })
            assertEquals(diagnostics[index], unsupported.message)
        }
    }
    @Test
    fun resolvesFileUrls() = runTest {
        val result = AppleMediaLocatorResolver.resolve("file:///tmp/demo.mp3")

        assertEquals(
            AppleResolvedMediaLocator.FileUrl("file:///tmp/demo.mp3"),
            result,
        )
    }

    @Test
    fun resolvesAbsolutePaths() = runTest {
        val result = AppleMediaLocatorResolver.resolve("/Users/demo/Music/test.m4a")

        assertEquals(
            AppleResolvedMediaLocator.AbsolutePath("/Users/demo/Music/test.m4a"),
            result,
        )
    }

    @Test
    fun rejectsSambaLocator() = runTest {
        val result = AppleMediaLocatorResolver.resolve("lynmusic-smb://source-id/share/song.mp3")

        val unsupported = assertIs<AppleResolvedMediaLocator.Unsupported>(result)
        assertEquals("apple_samba_unsupported", unsupported.message)
    }

    @Test
    fun rejectsWebDavLocator() = runTest {
        val result = AppleMediaLocatorResolver.resolve("lynmusic-webdav://source-id/music/song.mp3")

        val unsupported = assertIs<AppleResolvedMediaLocator.Unsupported>(result)
        assertEquals("apple_webdav_unsupported", unsupported.message)
    }
}
