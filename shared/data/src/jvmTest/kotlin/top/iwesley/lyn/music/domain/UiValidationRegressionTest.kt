package top.iwesley.lyn.music.domain

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.AppLanguageRuntime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.UiTextFailure
import top.iwesley.lyn.music.core.model.normalizeWebDavRootUrl
import top.iwesley.lyn.music.core.model.resolveUiText
import top.iwesley.lyn.music.core.model.uiErrorText

class UiValidationRegressionTest {
    @Test fun sourceAddressValidationKeepsDescriptorsAcrossLanguages() = runTest {
        listOf<Pair<String, (String?) -> String>>(
            "Emby" to ::normalizeEmbyBaseUrl,
            "Navidrome" to ::normalizeNavidromeBaseUrl,
            "Subsonic" to ::normalizeSubsonicBaseUrl,
        ).forEach { (server, normalize) ->
            val error = assertNotNull(runCatching { normalize("https://host.test/?token=secret") }.exceptionOrNull())
            assertTrue(error is UiTextFailure)
            val text = error.uiErrorText()
            assertEquals("The $server address cannot contain a query or fragment.", resolveUiText(text, AppLanguage.English))
            assertEquals("$server 地址不能包含 query 或 fragment。", resolveUiText(text, AppLanguage.SimplifiedChinese))
            assertEquals("$server 位址不能包含 query 或 fragment。", resolveUiText(text, AppLanguage.TraditionalChinese))
        }
    }

    @Test fun webDavInputErrorsAreLocalizedAndThirdPartyDetailsRemainRaw() = runTest {
        val error = assertNotNull(runCatching { normalizeWebDavRootUrl(" ") }.exceptionOrNull())
        assertTrue(error is UiTextFailure)
        assertEquals("Enter the WebDAV root URL.", resolveUiText(error.uiErrorText(), AppLanguage.English))
        assertEquals("請填寫 WebDAV 根 URL。", resolveUiText(error.uiErrorText(), AppLanguage.TraditionalChinese))
        val serverError = IllegalStateException("server details: 原始内容")
        assertEquals("Operation failed.\nserver details: 原始内容", resolveUiText(serverError.uiErrorText(), AppLanguage.English))
    }
}
