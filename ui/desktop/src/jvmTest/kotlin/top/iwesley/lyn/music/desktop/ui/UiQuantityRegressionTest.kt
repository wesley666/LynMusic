package top.iwesley.lyn.music.desktop.ui

import top.iwesley.lyn.music.*

import top.iwesley.lyn.music.desktop.ui.testing.libraryFolderSummaryLabel

import kotlinx.coroutines.test.runTest


import top.iwesley.lyn.music.resources.*

import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.AppLanguageRuntime

class UiQuantityRegressionTest {
    @Test fun folderSummarySelectsEachQuantityIndependentlyInAllLanguages() = runTest {
        withLanguages { language ->
            for (songs in 0..2) for (children in 0..2) {
                val folder = LibraryFolderNode(LibraryFolderKey("source", ""), "用户目录", "用户来源", "source", "", songs, songs, children)
                val songLabel = if (language == AppLanguage.English) "$songs ${if (songs == 1) "song" else "songs"}" else "$songs 首歌曲"
                val childLabel = when (language) {
                    AppLanguage.English -> "$children ${if (children == 1) "subfolder" else "subfolders"}"
                    AppLanguage.TraditionalChinese -> "$children 個子資料夾"
                    else -> "$children 个子文件夹"
                }
                assertEquals(if (children > 0) "$songLabel · $childLabel" else songLabel, libraryFolderSummaryLabel(folder))
                assertEquals("用户目录", folder.name)
            }
        }
    }









    private suspend fun withLanguages(block: suspend (AppLanguage) -> Unit) {
        val original = AppLanguageRuntime.appLanguage.value
        try {
            listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese).forEach { language ->
                AppLanguageRuntime.update(language)
                block(language)
            }
        } finally { AppLanguageRuntime.update(original) }
    }
}
