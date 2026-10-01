package top.iwesley.lyn.music.core.model

import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.resources.*
import kotlin.test.*

class OfficialUiTextTest {
    @Test fun nestedParametersAndSeparatorsUseOneEnvironment() = runTest {
        val raw = "provider 原文 %1\$s\n100% "
        val text = uiText(Res.string.ui_error_with_context, uiText(Res.string.common_close), UiText.Raw(raw))
        for ((language, close) in listOf(AppLanguage.English to "Close", AppLanguage.SimplifiedChinese to "关闭", AppLanguage.TraditionalChinese to "關閉")) {
            assertEquals("$close\n$raw", resolveUiText(text, language))
            assertEquals("$close$close$raw", resolveUiText(UiText.Joined(listOf(uiText(Res.string.common_close), UiText.Raw(raw)), separatorText = uiText(Res.string.common_close)), language))
        }
        assertEquals("\n", resolveUiText(uiText(Res.string.ui_error_with_context, null, null), AppLanguage.English))
    }

    @Test fun pluralQuantityIsIndependentFromParameterOrder() = runTest {
        for (quantity in 0..2) {
            val text = uiPlural(Res.plurals.tv_search_result_track_count, quantity, "1 原文", quantity)
            assertEquals("Search “1 原文” · $quantity ${if (quantity == 1) "song" else "songs"}", resolveUiText(text, AppLanguage.English))
        }
    }

    @Test fun exceptionConstructionDoesNotReadOrFormatResources() = runTest {
        // A missing format argument would fail on display, but cannot affect exception construction.
        val text = uiText(Res.string.ui_error_with_context)
        val cause = IllegalArgumentException("third-party 原文")
        val error = UiTextException(text, cause)
        assertEquals("ui_error_with_context", error.message)
        assertSame(cause, error.cause)
        assertEquals(text, error.uiFailureTextOrNull())
        assertEquals("explicit diagnostic 原文", UiTextException(text, cause, "explicit diagnostic 原文").message)
        assertEquals("tv_search_result_track_count(quantity=2, arg1=原始查询, arg2=2)", uiPlural(Res.plurals.tv_search_result_track_count, 2, "原始查询", 2).diagnosticMessage())
        assertIs<IllegalArgumentException>(UiTextArgumentException(text))
        assertIs<UnsupportedOperationException>(UiTextUnsupportedException(text))
    }
}
