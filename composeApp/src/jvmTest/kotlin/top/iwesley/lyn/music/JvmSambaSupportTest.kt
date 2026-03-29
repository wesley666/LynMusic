package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.buildSambaLocator
import top.iwesley.lyn.music.platform.buildJvmSambaPlaybackTarget
import top.iwesley.lyn.music.platform.buildJvmSambaSourceReference
import top.iwesley.lyn.music.platform.shouldUseJvmSambaCallback

class JvmSambaSupportTest {

    @Test
    fun `samba callback is enabled only when cache is disabled`() {
        val locator = buildSambaLocator("source-1", "Music/Test.mp3")

        assertTrue(shouldUseJvmSambaCallback(locator, useSambaCache = false))
        assertFalse(shouldUseJvmSambaCallback(locator, useSambaCache = true))
        assertFalse(shouldUseJvmSambaCallback("file:///tmp/test.mp3", useSambaCache = false))
    }

    @Test
    fun `samba playback target uses callback scheme instead of smb url`() {
        assertEquals("samba-callback://track-42", buildJvmSambaPlaybackTarget("track-42"))
    }

    @Test
    fun `samba source reference keeps endpoint share and remote path`() {
        assertEquals(
            "endpoint=nas.local:445/Media/Music share=Media remotePath=Music/Test.mp3",
            buildJvmSambaSourceReference(
                endpoint = "nas.local:445/Media/Music",
                shareName = "Media",
                remotePath = "Music/Test.mp3",
            ),
        )
    }
}
