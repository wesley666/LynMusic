package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.test.runTest
import kotlin.test.*
import top.iwesley.lyn.music.resources.*

class ImportScanTextTest {
    private val languages = listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese, AppLanguage.English)

    @Test fun wrappedApplicationFailureKeepsDescriptionAndDiagnosticsSeparate() = runTest {
        val path = "用户目录/song %1\$s.flac"
        val description = uiText(Res.string.source_folder_missing, path)
        val original = UiTextException(description)
        val wrapped = IllegalStateException("第三方中文详情", original)
        val failure = audioImportFailure(path, wrapped)
        for (language in languages) {
            assertEquals(resolveUiText(description, language), resolveUiText(failure.reasonUiText(), language))
        }
        assertEquals(path, failure.relativePath)
        assertEquals("第三方中文详情", failure.reason)
        assertSame(original, wrapped.cause)
    }

    @Test fun ordinaryDetailsAndExceptionNamesRemainVerbatim() = runTest {
        for (error in listOf(IllegalStateException("系统原文 %1\$s "), IllegalStateException(" "))) {
            val failure = audioImportFailure("path", error)
            val expected = error.message?.takeIf { it.isNotBlank() } ?: "IllegalStateException"
            for (language in languages) assertEquals(expected, resolveUiText(failure.reasonUiText(), language))
            assertEquals(expected, failure.reason)
        }
    }

    @Test fun unnamedFailureUsesLocalizedFallbackWithoutChangingScanResults() = runTest {
        val failure = audioImportFailure("原始路径", object : Throwable() {})
        val report = ImportScanReport(emptyList(), failures = listOf(failure), discoveredAudioFileCount = 1)
        for (language in languages) {
            assertEquals(resolveUiText(uiText(Res.string.common_read_failed), language), resolveUiText(failure.reasonUiText(), language))
        }
        assertEquals("读取失败。", failure.reason)
        assertEquals("原始路径", failure.relativePath)
        assertEquals(1, report.discoveredAudioFileCount)
        assertEquals(listOf(failure), report.failures)
    }

    @Test fun warningDescriptionsPreserveOrderAndFallbackPerItem() = runTest {
        val description = uiText(Res.string.source_account_no_syncable_tracks, "用户来源")
        val warnings = listOf(
            ImportScanWarning("应用诊断", description),
            ImportScanWarning("服务端中文原文 %1\$s"),
        )
        val texts = warnings.map { it.warningUiText() }
        for (language in languages) {
            assertEquals(resolveUiText(description, language), resolveUiText(texts[0], language))
            assertEquals(warnings[1].diagnostic, resolveUiText(texts[1], language))
        }
        assertEquals(description, warnings[0].warningUiText())
        assertEquals(UiText.Raw(warnings[1].diagnostic), warnings[1].warningUiText())
        assertEquals(listOf("应用诊断", "服务端中文原文 %1\$s"), warnings.map { it.diagnostic })
    }
}
