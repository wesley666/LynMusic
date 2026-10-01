package top.iwesley.lyn.music

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.testing.lyricsShareCopyMenuLabels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricsShareCopyMenuTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `copy menu labels include image and text options`() = runTest {
        assertEquals(listOf("复制图片", "复制文字"), lyricsShareCopyMenuLabels())
    }

    @Test
    fun `copy menu is enabled only when selection exists and no export action is busy`() = runTest {
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
