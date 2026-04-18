package top.iwesley.lyn.music

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.platform.jvmSameNameLyricsPath
import top.iwesley.lyn.music.platform.readJvmLocalSameNameLyricsFile

class JvmSameNameLyricsSupportTest {
    @Test
    fun `jvm same name lyrics path points to sibling lrc`() {
        val audioPath = Files.createTempDirectory("lynmusic-sidecar-path")
            .resolve("Artist")
            .resolve("aaa.mp3")

        val lyricsPath = jvmSameNameLyricsPath(audioPath)

        assertEquals(audioPath.parent.resolve("aaa.lrc"), lyricsPath)
    }

    @Test
    fun `jvm local same name lyrics reads sibling lrc`() {
        val directory = Files.createTempDirectory("lynmusic-sidecar-lrc")
        val audioPath = directory.resolve("aaa.mp3")
        val lyricsPath = directory.resolve("aaa.lrc")
        Files.createFile(audioPath)
        lyricsPath.writeText("[00:01.00]sidecar line")

        val lyrics = readJvmLocalSameNameLyricsFile(audioPath)

        assertEquals("[00:01.00]sidecar line", lyrics)
    }
}
