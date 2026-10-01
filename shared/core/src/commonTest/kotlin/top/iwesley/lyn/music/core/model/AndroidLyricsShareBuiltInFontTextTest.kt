package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.core.model.resolveUiText

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AndroidLyricsShareBuiltInFontTextTest {
    @Test fun allTenAndroidAliasesHaveLabelsInEveryLanguage() = runTest {
        val fixtures = listOf(
            listOf("sans-serif", "Sans serif", "无衬线", "無襯線"),
            listOf("sans-serif-medium", "Sans serif medium", "无衬线中黑体", "無襯線中黑體"),
            listOf("sans-serif-black", "Sans serif black", "无衬线重黑体", "無襯線重黑體"),
            listOf("serif", "Serif", "衬线", "襯線"),
            listOf("monospace", "Monospace", "等宽", "等寬"),
            listOf("serif-monospace", "Serif monospace", "衬线等宽", "襯線等寬"),
            listOf("sans-serif-condensed", "Sans serif condensed", "无衬线紧凑体", "無襯線窄體"),
            listOf("sans-serif-condensed-medium", "Sans serif condensed medium", "无衬线紧凑中黑体", "無襯線窄體中黑體"),
            listOf("cursive", "Cursive", "手写体", "手寫體"),
            listOf("casual", "Casual", "休闲体", "休閒體"),
        )
        fixtures.forEach { (key, english, simplified, traditional) ->
            val text = assertNotNull(androidLyricsShareBuiltInFontNameText(key))
            listOf(
                AppLanguage.English to english, AppLanguage.SimplifiedChinese to simplified,
                AppLanguage.TraditionalChinese to traditional, AppLanguage.English to english,
            ).forEach { (language, expected) -> assertEquals(expected, resolveUiText(text, language), key) }
        }
    }

    @Test fun aliasesIgnoreCaseButDoNotTranslateImportedOrUnknownFamilyNames() = runTest {
        assertEquals(androidLyricsShareBuiltInFontNameText("serif"), androidLyricsShareBuiltInFontNameText(DEFAULT_LYRICS_SHARE_FONT_KEY))
        assertEquals(androidLyricsShareBuiltInFontNameText("sans-serif"), androidLyricsShareBuiltInFontNameText("SANS-SERIF"))
        listOf(null, "", "Arial", "用户字体", "imported:abc123").forEach {
            assertNull(androidLyricsShareBuiltInFontNameText(it))
        }
    }
}
