package top.iwesley.lyn.music

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.buildSambaLocator
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.platform.buildAndroidSambaSourceReference
import top.iwesley.lyn.music.platform.buildSambaCacheFileName
import top.iwesley.lyn.music.platform.resolveSambaSourceSpec
import top.iwesley.lyn.music.platform.shouldUseAndroidSambaDirectPlayback

class AndroidSambaPlaybackSupportTest {

    @Test
    fun `android samba direct playback is enabled only when cache is disabled`() = runTest {
        val locator = buildSambaLocator("source-1", "Music/Test.mp3")

        assertTrue(shouldUseAndroidSambaDirectPlayback(locator, useSambaCache = false))
        assertFalse(shouldUseAndroidSambaDirectPlayback(locator, useSambaCache = true))
        assertFalse(shouldUseAndroidSambaDirectPlayback("file:///tmp/test.mp3", useSambaCache = false))
    }

    @Test
    fun `resolve samba source spec normalizes port share path and credential`() = runTest {
        val source = ImportSourceEntity(
            id = "smb-1",
            type = "SAMBA",
            label = "NAS",
            rootReference = "Media/Music",
            server = "nas.local",
            shareName = "1445",
            directoryPath = "Media/Music",
            username = "guest",
            credentialKey = "cred-1",
            allowInsecureTls = false,
            enabled = true,
            lastScannedAt = null,
            createdAt = 0L,
        )

        val spec = resolveSambaSourceSpec(
            source = source,
            locatorRelativePath = "Artist/Song.mp3",
            fallbackRelativePath = "Artist/Song.mp3",
        )

        assertEquals("smb-1", spec.sourceId)
        assertEquals("nas.local:1445/Media/Music", spec.endpoint)
        assertEquals(1445, spec.port)
        assertEquals("Media", spec.shareName)
        assertEquals("Music/Artist/Song.mp3", spec.remotePath)
        assertEquals("Artist/Song.mp3", spec.relativePath)
        assertEquals("guest", spec.username)
        assertEquals("cred-1", spec.credentialKey)

        // Sources rooted at the server keep the share as the first segment of each track path.
        val serverRooted = resolveSambaSourceSpec(source.copy(directoryPath = null, rootReference = ""), "Media/Music/Artist/Song.mp3")
        assertEquals("nas.local:1445", serverRooted.endpoint)
        assertEquals("Media", serverRooted.shareName)
        assertEquals("Music/Artist/Song.mp3", serverRooted.remotePath)

        val failure = assertFailsWith<UiTextException> {
            resolveSambaSourceSpec(source.copy(directoryPath = "", shareName = "1445"), "")
        }
        assertEquals("The SMB path must include a share name, such as Media or Media/Music.", resolveUiText(failure.text, AppLanguage.English))
        assertEquals("SMB 路徑至少需要包含共用名稱，例如 Media 或 Media/Music。", resolveUiText(failure.text, AppLanguage.TraditionalChinese))
    }

    @Test
    fun `samba cache keys keep legacy names and tell shares apart for server rooted sources`() = runTest {
        val legacy = ImportSourceEntity(
            id = "smb-1",
            type = "SAMBA",
            label = "NAS",
            rootReference = "Media/Music",
            server = "nas.local",
            shareName = "445",
            directoryPath = "Media/Music",
            username = "",
            credentialKey = null,
            allowInsecureTls = false,
            enabled = true,
            lastScannedAt = null,
            createdAt = 0L,
        )
        val legacySpec = resolveSambaSourceSpec(legacy, "Artist/Song.mp3")
        assertEquals("Music/Artist/Song.mp3", legacySpec.cacheKeyPath)
        assertTrue(buildSambaCacheFileName("smb-1", legacySpec.cacheKeyPath).startsWith("smb-1-Music_Artist_Song-"))
        assertTrue(buildSambaCacheFileName("smb-1", legacySpec.cacheKeyPath).endsWith(".mp3"))
        assertEquals(
            buildSambaCacheFileName("smb-1", legacySpec.cacheKeyPath),
            buildSambaCacheFileName("smb-1", legacySpec.cacheKeyPath),
        )

        val serverRooted = legacy.copy(rootReference = "", directoryPath = null)
        val media = resolveSambaSourceSpec(serverRooted, "Media/Music/a.mp3")
        val backup = resolveSambaSourceSpec(serverRooted, "Backup/Music/a.mp3")
        assertEquals(media.remotePath, backup.remotePath)
        assertEquals("Media/Music/a.mp3", media.cacheKeyPath)
        assertTrue(buildSambaCacheFileName("smb-1", media.cacheKeyPath) != buildSambaCacheFileName("smb-1", backup.cacheKeyPath))
    }

    @Test
    fun `non ascii folders that sanitize alike still get different cache names`() = runTest {
        assertTrue(buildSambaCacheFileName("smb-1", "音乐/Music/a.mp3") != buildSambaCacheFileName("smb-1", "备份/Music/a.mp3"))
        assertTrue(buildSambaCacheFileName("smb-1", "周杰伦/01.mp3") != buildSambaCacheFileName("smb-1", "陈奕迅/01.mp3"))
    }

    @Test
    fun `long samba cache names are capped and keep their extension`() = runTest {
        val first = buildSambaCacheFileName("smb-1", "a".repeat(300) + "/one.flac")
        val second = buildSambaCacheFileName("smb-1", "a".repeat(300) + "/two.flac")

        assertTrue(first.length <= 200)
        assertTrue(first.endsWith(".flac"))
        assertTrue(first != second)
    }

    @Test
    fun `android samba source reference keeps endpoint share and remote path`() = runTest {
        assertEquals(
            "endpoint=nas.local:445/Media/Music share=Media remotePath=Music/Test.mp3",
            buildAndroidSambaSourceReference(
                endpoint = "nas.local:445/Media/Music",
                shareName = "Media",
                remotePath = "Music/Test.mp3",
            ),
        )
    }
}
