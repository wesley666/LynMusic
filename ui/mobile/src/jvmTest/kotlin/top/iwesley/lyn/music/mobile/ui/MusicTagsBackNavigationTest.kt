package top.iwesley.lyn.music.mobile.ui

import top.iwesley.lyn.music.*

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MusicTagsBackNavigationTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `music tags detail can navigate back when a track is selected`() {
        assertTrue(canNavigateBackFromMusicTagsDetail("track-1"))
    }

    @Test
    fun `music tags detail cannot navigate back without a selected track`() {
        assertFalse(canNavigateBackFromMusicTagsDetail(null))
    }
}
