package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricsShareCopyMenuTest {
    @Test
    fun `copy menu labels include image and text options`() {
        assertEquals(listOf("复制图片", "复制文字"), lyricsShareCopyMenuLabels())
    }

    @Test
    fun `copy menu is enabled only when selection exists and no export action is busy`() {
        assertTrue(
            isLyricsShareCopyMenuEnabled(
                selectedLineCount = 1,
                isSaving = false,
                isCopying = false,
            ),
        )
        assertFalse(
            isLyricsShareCopyMenuEnabled(
                selectedLineCount = 0,
                isSaving = false,
                isCopying = false,
            ),
        )
        assertFalse(
            isLyricsShareCopyMenuEnabled(
                selectedLineCount = 1,
                isSaving = true,
                isCopying = false,
            ),
        )
        assertFalse(
            isLyricsShareCopyMenuEnabled(
                selectedLineCount = 1,
                isSaving = false,
                isCopying = true,
            ),
        )
    }
}
