package top.iwesley.lyn.music.platform

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import top.iwesley.lyn.music.resources.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.ImportScanFailure
import top.iwesley.lyn.music.core.model.UiTextException
import top.iwesley.lyn.music.core.model.reasonUiText
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiText

class IosAudioImportFailureTest {
    @Test fun missingFileUsesCurrentLanguageAndKeepsPathAndDiagnostic() = runTest {
        checkIosAudioFileAvailable(true)
        val error = assertFailsWith<UiTextException> { checkIosAudioFileAvailable(false) }
        val failure = iosAudioImportFailure("用户目录/歌曲.flac", error)
        val expected = listOf(
            AppLanguage.English to "The file no longer exists or its provider is currently offline.",
            AppLanguage.SimplifiedChinese to "文件已不存在或文件提供方当前离线。",
            AppLanguage.TraditionalChinese to "檔案已不存在或檔案提供方目前離線。",
            AppLanguage.English to "The file no longer exists or its provider is currently offline.",
        )
        expected.forEach { (language, text) ->
            assertEquals(text, resolveUiText(failure.reasonUiText(), language))
        }
        assertEquals("用户目录/歌曲.flac", failure.relativePath)
        assertEquals(error.message, failure.reason)
    }

    @Test fun wrappedApplicationErrorRetainsDescriptorAndOriginalWrapperDetail() = runTest {
        val description = uiText(Res.string.ios_audio_file_unavailable)
        val failure = iosAudioImportFailure("file.mp3", IllegalStateException("provider wrapper", UiTextException(description)))
        assertEquals(description, failure.reasonText)
        assertEquals("provider wrapper", failure.reason)
    }

    @Test fun providerAndHistoricalDetailsRemainOriginalInEveryLanguage() = runTest {
        val detail = "提供方原始错误 %1\$s /用户目录/歌曲.mp3"
        val failures = listOf(
            iosAudioImportFailure("file.mp3", IllegalStateException(detail)),
            ImportScanFailure("old.mp3", detail),
        )
        listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese).forEach { language ->
            failures.forEach { assertEquals(detail, resolveUiText(it.reasonUiText(), language)) }
        }
    }

    @Test fun emptyProviderDetailUsesLocalizedReadFallback() = runTest {
        listOf(IllegalStateException(), IllegalStateException(" ")).forEach { error ->
            val failure = iosAudioImportFailure("file.mp3", error)
            assertEquals("ios_audio_file_read_failed", failure.reason)
            assertEquals("The file cannot be read or its provider is offline.", resolveUiText(failure.reasonUiText(), AppLanguage.English))
            assertEquals("檔案無法讀取或檔案提供方離線。", resolveUiText(failure.reasonUiText(), AppLanguage.TraditionalChinese))
        }
    }
}
