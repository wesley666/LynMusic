package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.buildDirectSambaUrl
import top.iwesley.lyn.music.core.model.DEFAULT_SAMBA_PORT
import top.iwesley.lyn.music.core.model.formatSambaEndpoint
import top.iwesley.lyn.music.core.model.parseSambaPath

class SambaPathTest {

    @Test
    fun `parse samba path splits share and directory`() {
        val parsed = parseSambaPath("Media/Library/Pop")

        requireNotNull(parsed)
        assertEquals("Media", parsed.shareName)
        assertEquals("Library/Pop", parsed.directoryPath)
    }

    @Test
    fun `parse samba path returns null for blank path`() {
        assertNull(parseSambaPath("   "))
    }

    @Test
    fun `format samba endpoint hides default port`() {
        assertEquals(
            "nas.local/Media/Library",
            formatSambaEndpoint("nas.local", DEFAULT_SAMBA_PORT, "Media/Library"),
        )
    }

    @Test
    fun `format samba endpoint includes custom port`() {
        assertEquals(
            "nas.local:1445/Media/Library",
            formatSambaEndpoint("nas.local", 1445, "Media/Library"),
        )
    }

    @Test
    fun `direct samba url encodes credentials and path segments`() {
        val url = buildDirectSambaUrl(
            server = "nas.local",
            port = 1445,
            shareName = "Media Files",
            remotePath = "流行/#1 hit?.mp3",
            username = "guest user",
            password = "p@ ss",
        )

        assertTrue(url.startsWith("smb://guest%20user:p%40%20ss@nas.local:1445/"))
        assertTrue(url.contains("Media%20Files"))
        assertTrue(url.contains("%E6%B5%81%E8%A1%8C"))
        assertTrue(url.contains("%231%20hit%3F.mp3"))
    }
}
