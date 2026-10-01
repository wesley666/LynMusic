package top.iwesley.lyn.music.platform

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiErrorText

class JvmLyricsShareFontUiTextTest {
    @Test fun invalidFontFailureFollowsLanguageChangesWithoutReloading() = runTest {
        val file = Files.createTempFile("lynmusic-invalid-font-", ".ttf").toFile()
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val error = assertFailsWith<UiTextException> { validateJvmImportedFontFile(file) }
            val text = error.uiErrorText()
            assertEquals("Unable to load the selected font file.", resolveUiText(text, AppLanguage.English))
            assertEquals("無法載入所選字型檔案。", resolveUiText(text, AppLanguage.TraditionalChinese))
            assertEquals("无法加载所选字体文件。", resolveUiText(text, AppLanguage.SimplifiedChinese))
            assertEquals("Unable to load the selected font file.", resolveUiText(text, AppLanguage.English))
            assertEquals("lyrics_font_file_unreadable", error.message)
            assertEquals(4L, file.length())
        } finally { file.delete() }
    }

    @Test fun emptyFontFileAlsoProducesAnApplicationDescription() = runTest {
        val file = Files.createTempFile("lynmusic-empty-font-", ".otf").toFile()
        try {
            val error = assertFailsWith<UiTextException> { validateJvmImportedFontFile(file) }
            assertEquals("Unable to load the selected font file.", resolveUiText(error.uiErrorText(), AppLanguage.English))
        } finally { file.delete() }
    }
}
