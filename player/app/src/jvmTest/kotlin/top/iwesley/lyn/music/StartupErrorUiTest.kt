package top.iwesley.lyn.music

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.resources.*

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.*

class StartupErrorUiTest {
    @Test fun dataLocationErrorsResolveWrappedDescriptionsAndPreserveOrdinaryDetails() = runTest {
        val described = UiTextArgumentException(uiText(Res.string.data_location_target_must_be_empty))
        val text = startupDataLocationErrorText(IllegalStateException("wrapper 原文", described))
        assertEquals(described.text, text)
        assertEquals("The target LynMusic directory must be empty.", resolveUiText(text, AppLanguage.English))
        assertEquals("目标 LynMusic 目录必须为空。", resolveUiText(text, AppLanguage.SimplifiedChinese))
        assertEquals("目標 LynMusic 目錄必須為空。", resolveUiText(text, AppLanguage.TraditionalChinese))
        assertEquals(UiText.Raw("第三方 原文 %1\$s"), startupDataLocationErrorText(IllegalStateException("第三方 原文 %1\$s")))
    }
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `startup database error details include stack trace and cause`() = runTest {
        val error = IllegalStateException(
            "component failed",
            IllegalArgumentException("root cause"),
        )

        val details = requireNotNull(startupDatabaseErrorDetails(error))

        assertContains(details, "java.lang.IllegalStateException: component failed")
        assertContains(details, "Caused by: java.lang.IllegalArgumentException: root cause")
        assertContains(details, "StartupErrorUiTest")
    }
}
