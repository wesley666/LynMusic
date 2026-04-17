package top.iwesley.lyn.music

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.platform.appendJvmFilePickerDefaultExtension
import top.iwesley.lyn.music.platform.isJvmNativeFilePickerMacOs
import top.iwesley.lyn.music.platform.resolveJvmFileDialogSelection

class JvmNativeFilePickerTest {
    @Test
    fun `macos detection matches os name containing mac`() {
        assertTrue(isJvmNativeFilePickerMacOs("Mac OS X"))
        assertTrue(isJvmNativeFilePickerMacOs("macOS"))
        assertFalse(isJvmNativeFilePickerMacOs("Darwin"))
        assertFalse(isJvmNativeFilePickerMacOs("Windows 11"))
        assertFalse(isJvmNativeFilePickerMacOs("Linux"))
    }

    @Test
    fun `save file default extension appends png once`() {
        val withoutExtension = Path.of("/tmp/lynmusic-share")
        val withLowercaseExtension = Path.of("/tmp/lynmusic-share.png")
        val withUppercaseExtension = Path.of("/tmp/lynmusic-share.PNG")

        assertEquals(
            Path.of("/tmp/lynmusic-share.png"),
            appendJvmFilePickerDefaultExtension(withoutExtension, "png"),
        )
        assertEquals(
            withLowercaseExtension,
            appendJvmFilePickerDefaultExtension(withLowercaseExtension, ".png"),
        )
        assertEquals(
            withUppercaseExtension,
            appendJvmFilePickerDefaultExtension(withUppercaseExtension, "png"),
        )
    }

    @Test
    fun `file dialog selection combines directory and file`() {
        assertEquals(
            Path.of("/Applications/VLC.app"),
            resolveJvmFileDialogSelection("/Applications", "VLC.app"),
        )
        assertEquals(
            Path.of("/Users/demo/Music"),
            resolveJvmFileDialogSelection("/Users/demo/", "Music"),
        )
        assertNull(resolveJvmFileDialogSelection("/Applications", null))
        assertNull(resolveJvmFileDialogSelection("/Applications", ""))
    }
}
