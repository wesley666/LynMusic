package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import top.iwesley.lyn.music.resources.*

class StoredErrorTextTest {
    @Test fun oneSnapshotUsesCapturedLanguageEvenWhenSelectionChangesDuringResolution() = runTest {
        val previous = AppLanguageRuntime.appLanguage.value
        try {
            AppLanguageRuntime.update(AppLanguage.English)
            val text = uiText(Res.string.ui_error_with_context, uiText(Res.string.common_close), UiText.Raw("第三方 100%\n%1\$s "))
            var resolutions = 0
            val saved = resolveUiTextForStorage(text, "fallback") { description, captured ->
                resolutions++
                assertEquals(AppLanguage.English, captured)
                AppLanguageRuntime.update(AppLanguage.TraditionalChinese)
                resolveUiText(description, captured)
            }
            assertEquals("Close\n第三方 100%\n%1\$s ", saved)
            assertEquals(1, resolutions)
            assertEquals(AppLanguage.TraditionalChinese, AppLanguageRuntime.effectiveLanguage.value)
        } finally { AppLanguageRuntime.update(previous) }
    }

    @Test fun resourceReadFailureUsesPlainFallbackAndCancellationIsNotSwallowed() = runTest {
        val text = uiText(Res.string.source_folder_missing, "/用户路径")
        assertEquals("Scan failed.", resolveUiTextForStorage(text, "Scan failed.") { _, _ ->
            throw IllegalStateException("resource read failed")
        })
        val cancelled = CancellationException("cancelled 原文")
        assertSame(cancelled, assertFailsWith<CancellationException> {
            resolveUiTextForStorage(text, "Scan failed.") { _, _ -> throw cancelled }
        })
        assertSame(cancelled, assertFailsWith<CancellationException> {
            cancelled.errorMessageForStorage(text, "Scan failed.")
        })
    }

    @Test fun ordinaryDetailsAndWrappedResourceFailuresKeepTheirOriginalException() = runTest {
        val previous = AppLanguageRuntime.appLanguage.value
        try {
            val raw = "原始服务端错误 %1\$s\n100% "
            val detail = IllegalStateException(raw)
            val description = uiText(Res.string.source_folder_missing, "/用户路径")
            val resourceFailure = UiTextException(description, detail, "explicit diagnostic")
            val wrapped = IllegalStateException("wrapper", resourceFailure)
            for (language in listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese)) {
                AppLanguageRuntime.update(language)
                assertEquals(raw, detail.errorMessageForStorage(description, "Scan failed."))
                assertEquals(resolveUiText(description, language), wrapped.errorMessageForStorage(description, "Scan failed."))
                assertEquals("wrapper", wrapped.message)
                assertSame(resourceFailure, wrapped.cause)
                assertSame(detail, resourceFailure.cause)
            }
        } finally { AppLanguageRuntime.update(previous) }
    }
}
