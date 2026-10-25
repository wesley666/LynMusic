package top.iwesley.lyn.music

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import top.iwesley.lyn.music.core.model.PlayerLyricsStyle
import top.iwesley.lyn.music.platform.JvmAppPreferencesStore

class JvmPlayerLyricsStylePreferencesTest {
    @Test fun `playback style survives restart independently of sharing font`() = runTest {
        val file = Files.createTempDirectory("lyrics-style").resolve("settings.properties").toFile()
        val original = JvmAppPreferencesStore(file)
        original.setSelectedLyricsShareFontKey("Serif")
        val style = PlayerLyricsStyle(0xfffa8072.toInt(), 1.5f, "imported:abc", showTranslation = false)
        original.setPlayerLyricsStyle(style)
        val reopened = JvmAppPreferencesStore(file)
        assertEquals(style, reopened.playerLyricsStyle.value)
        assertEquals("Serif", reopened.selectedLyricsShareFontKey.value)
        reopened.setPlayerLyricsStyle(PlayerLyricsStyle())
        assertEquals(PlayerLyricsStyle(), JvmAppPreferencesStore(file).playerLyricsStyle.value)
        assertEquals("Serif", JvmAppPreferencesStore(file).selectedLyricsShareFontKey.value)
    }
}
