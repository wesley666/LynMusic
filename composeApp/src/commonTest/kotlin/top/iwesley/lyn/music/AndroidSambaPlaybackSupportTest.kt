package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.buildSambaLocator
import top.iwesley.lyn.music.data.db.ImportSourceEntity
import top.iwesley.lyn.music.platform.buildAndroidSambaSourceReference
import top.iwesley.lyn.music.platform.resolveSambaSourceSpec
import top.iwesley.lyn.music.platform.shouldUseAndroidSambaDirectPlayback

class AndroidSambaPlaybackSupportTest {

    @Test
    fun `android samba direct playback is enabled only when cache is disabled`() {
        val locator = buildSambaLocator("source-1", "Music/Test.mp3")

        assertTrue(shouldUseAndroidSambaDirectPlayback(locator, useSambaCache = false))
        assertFalse(shouldUseAndroidSambaDirectPlayback(locator, useSambaCache = true))
        assertFalse(shouldUseAndroidSambaDirectPlayback("file:///tmp/test.mp3", useSambaCache = false))
    }

    @Test
    fun `resolve samba source spec normalizes port share path and credential`() {
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
    }

    @Test
    fun `android samba source reference keeps endpoint share and remote path`() {
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
