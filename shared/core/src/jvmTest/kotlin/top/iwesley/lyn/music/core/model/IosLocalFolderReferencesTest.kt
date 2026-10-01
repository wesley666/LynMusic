package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.test.runTest

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosLocalFolderReferencesTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() { AppLanguageRuntime.update(AppLanguage.SimplifiedChinese) }

    @Test
    fun `folder reference round trips identity and bookmark without exposing bookmark`() = runTest {
        val bookmark = byteArrayOf(0, 1, 2, 127, -1)
        val reference = buildIosLocalFolderReference(
            identity = "file:///private/var/mobile/中文 音乐",
            bookmarkData = bookmark,
        )

        val parsed = parseIosLocalFolderReference(reference)

        assertEquals("file:///private/var/mobile/中文 音乐", parsed?.identity)
        assertContentEquals(bookmark, parsed?.bookmarkData)
        assertEquals(
            "IosLocalFolderReference(identity=<redacted>, bookmarkData=<redacted>)",
            parsed.toString(),
        )
        assertEquals("文件 App · 原地索引", resolveUiText(displayLocalFolderReference(reference), AppLanguage.SimplifiedChinese))
        assertFalse(resolveUiText(displayLocalFolderReference(reference), AppLanguage.SimplifiedChinese).contains(reference.substringAfterLast('/')))
    }

    @Test
    fun `folder identity ignores refreshed bookmark bytes`() = runTest {
        val first = buildIosLocalFolderReference("file:///Music", byteArrayOf(1, 2, 3))
        val refreshed = buildIosLocalFolderReference("file:///Music", byteArrayOf(4, 5, 6))

        assertEquals(localFolderPersistentIdentity(first), localFolderPersistentIdentity(refreshed))
    }

    @Test
    fun `corrupted iOS folder reference still hides persistent payload`() = runTest {
        val corrupted = "lynmusic-ios-folder://v1/not*valid/private-bookmark-payload"

        assertNull(parseIosLocalFolderReference(corrupted))
        assertEquals("文件 App · 原地索引", resolveUiText(displayLocalFolderReference(corrupted), AppLanguage.SimplifiedChinese))
    }

    @Test
    fun `media locator round trips nested unicode relative path`() = runTest {
        val locator = buildIosLocalMediaLocator(
            sourceId = "local-123",
            relativePath = "周杰伦/叶惠美/03 晴天.flac",
        )

        assertEquals(
            "local-123" to "周杰伦/叶惠美/03 晴天.flac",
            parseIosLocalMediaLocator(locator),
        )
        assertTrue(locator.startsWith("lynmusic-ios-local://local-123/"))
        assertFalse(locator.contains("晴天"))
    }

    @Test
    fun `media locator rejects traversal and invalid source id`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            buildIosLocalMediaLocator("local-1", "../secret.mp3")
        }
        assertFailsWith<IllegalArgumentException> {
            buildIosLocalMediaLocator("local/1", "song.mp3")
        }
        assertNull(parseIosLocalMediaLocator("lynmusic-ios-local://local-1/Li4vc2VjcmV0Lm1wMw=="))
    }
}
