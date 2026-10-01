package top.iwesley.lyn.music.domain

import kotlinx.coroutines.test.runTest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.*

class WorkflowUiValidationTest {
    private val validJson = """{
        "id":"workflow-1", "name":"用户来源名", "kind":"workflow",
        "search":{"url":"https://server.test/search", "resultPath":"data", "mapping":{"id":"id", "title":"title", "artists":"artists"}},
        "lyrics":{"steps":[{"url":"https://server.test/item", "payloadPath":"lyrics"}]}
    }"""

    @Test fun missingMappingIsLocalizedAndUserSourceNameStaysOriginal() = runTest {
        val valid = parseWorkflowLyricsSourceConfig(validJson)
        assertEquals("用户来源名", valid.name)
        val error = error(validJson.replace(", \"artists\":\"artists\"", ""))
        val text = error.uiErrorText()
        assertEquals("workflow.search.mapping is missing fields: artists.", resolveUiText(text, AppLanguage.English))
        assertEquals("workflow.search.mapping 缺少字段：artists。", resolveUiText(text, AppLanguage.SimplifiedChinese))
        assertEquals("workflow.search.mapping 缺少欄位：artists。", resolveUiText(text, AppLanguage.TraditionalChinese))
        assertEquals("workflow.search.mapping is missing fields: artists.", resolveUiText(text, AppLanguage.English))
    }

    @Test fun invalidKindTypesEnumsStepsAndUnknownFieldsAreResourceFailures() = runTest {
        listOf(
            validJson.replace("\"kind\":\"workflow\"", "\"kind\":\"其他值\""),
            validJson.replace("\"search\":{\"url\":\"https://server.test/search\", \"resultPath\":\"data\", \"mapping\":{\"id\":\"id\", \"title\":\"title\", \"artists\":\"artists\"}}", "\"search\":[]"),
            validJson.replace("\"steps\":[{\"url\":\"https://server.test/item\", \"payloadPath\":\"lyrics\"}]", "\"steps\":[]"),
            validJson.replace(", \"payloadPath\":\"lyrics\"", ""),
            validJson.replace("\"url\":\"https://server.test/search\"", "\"method\":\"非法原值\",\"url\":\"https://server.test/search\""),
            validJson.replace("\"id\":\"id\"", "\"id\":{\"raw\":123}"),
            validJson.replace("\"url\":\"https://server.test/search\"", "\"url\":\"https://server.test/{undefined_variable}\""),
            validJson.replace("\"kind\":\"workflow\"", "\"kind\":\"workflow\",\"unexpected\":true"),
        ).forEach { json ->
            val text = error(json).uiErrorText()
            listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese).forEach { language ->
                assertTrue(resolveUiText(text, language).isNotBlank())
            }
        }
        val enumError = error(validJson.replace("\"url\":\"https://server.test/search\"", "\"method\":\"非法原值\",\"url\":\"https://server.test/search\""))
        assertEquals("Field method has an invalid value: \"非法原值\".", resolveUiText(enumError.uiErrorText(), AppLanguage.English))
    }

    @Test fun thirdPartyJsonSyntaxDetailsArePreserved() = runTest {
        val error = checkNotNull(runCatching { parseWorkflowLyricsSourceConfig("{") }.exceptionOrNull())
        assertFalse(error is UiTextFailure)
        assertEquals(error.message.orEmpty(), resolveUiText(error.uiErrorDetail(), AppLanguage.TraditionalChinese))
    }

    private fun error(json: String): Throwable = checkNotNull(runCatching { parseWorkflowLyricsSourceConfig(json) }.exceptionOrNull()).also { assertIs<UiTextFailure>(it) }
}
