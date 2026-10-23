package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.LyricsSearchApplyMode

class LyricsSearchUiModesTest {

    @Test
    fun `lyrics search apply modes include artwork only when artwork exists`() {
        assertEquals(
            listOf(
                LyricsSearchApplyMode.FULL,
                LyricsSearchApplyMode.LYRICS_ONLY,
                LyricsSearchApplyMode.ARTWORK_ONLY,
            ),
            lyricsSearchApplyModes("https://img.example.com/cover.jpg"),
        )
    }

    @Test
    fun `lyrics search apply modes omit artwork only when artwork is missing`() {
        assertEquals(
            listOf(
                LyricsSearchApplyMode.FULL,
                LyricsSearchApplyMode.LYRICS_ONLY,
            ),
            lyricsSearchApplyModes(" "),
        )
    }
}
