package top.iwesley.lyn.music

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import top.iwesley.lyn.music.platform.normalizeDesktopVlcSelection
import top.iwesley.lyn.music.platform.resolveDesktopVlcEffectivePath

class VlcSupportTest {

    @Test
    fun `windows vlc directory validation requires both native libraries`() {
        val directory = Path.of("C:/Program Files/VideoLAN/VLC")

        val valid = normalizeDesktopVlcSelection(
            selection = directory,
            osName = "Windows 11",
            listEntries = { listOf("libvlc.dll", "libvlccore.dll", "plugins") },
        )
        val invalid = normalizeDesktopVlcSelection(
            selection = directory,
            osName = "Windows 11",
            listEntries = { listOf("libvlc.dll") },
        )

        assertEquals(directory.toAbsolutePath().normalize(), valid)
        assertNull(invalid)
    }

    @Test
    fun `linux vlc directory validation accepts versioned shared libraries`() {
        val directory = Path.of("/usr/lib/vlc")

        val valid = normalizeDesktopVlcSelection(
            selection = directory,
            osName = "Linux",
            listEntries = { listOf("libvlc.so.5", "libvlccore.so.9", "plugins") },
        )

        assertEquals(directory.toAbsolutePath().normalize(), valid)
    }

    @Test
    fun `macos vlc app selection normalizes to native library directory`() {
        val appBundle = Path.of("/Applications/VLC.app")
        val expected = appBundle.toAbsolutePath().normalize().resolve("Contents").resolve("MacOS").resolve("lib")

        val normalized = normalizeDesktopVlcSelection(
            selection = appBundle,
            osName = "Mac OS X",
            listEntries = { path ->
                if (path == expected) {
                    listOf("libvlc.dylib", "libvlccore.dylib", "plugins")
                } else {
                    emptyList()
                }
            },
        )

        assertEquals(expected, normalized)
    }

    @Test
    fun `manual vlc path takes precedence over auto detected path`() {
        assertEquals(
            "/manual/vlc/lib",
            resolveDesktopVlcEffectivePath(
                manualPath = "/manual/vlc/lib",
                autoDetectedPath = "/auto/vlc/lib",
            ),
        )
        assertEquals(
            "/auto/vlc/lib",
            resolveDesktopVlcEffectivePath(
                manualPath = null,
                autoDetectedPath = "/auto/vlc/lib",
            ),
        )
    }
}
