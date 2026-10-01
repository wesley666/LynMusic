package top.iwesley.lyn.music

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.core.model.resolveUiText

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import top.iwesley.lyn.music.core.model.AppLanguage
import top.iwesley.lyn.music.core.model.AppLanguageRuntime
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine

class LyricsSearchContentTypeTest {
    @Test
    fun summariesUseZeroOneAndTwoLinesInAllLanguages() = runTest {
        val originalLanguage = AppLanguageRuntime.appLanguage.value
        val languages = listOf(AppLanguage.English, AppLanguage.SimplifiedChinese, AppLanguage.TraditionalChinese)
        val expected = listOf(
            listOf("Plain text · 0 lines", "Plain text · 1 line", "Plain text · 2 lines"),
            listOf("纯文本 · 0 行", "纯文本 · 1 行", "纯文本 · 2 行"),
            listOf("純文本 · 0 行", "純文本 · 1 行", "純文本 · 2 行"),
        )
        try {
            languages.forEachIndexed { index, language ->
                AppLanguageRuntime.update(language)
                assertEquals(expected[index], (0..2).map { resolveUiText(LyricsSearchContentType.PLAIN.summaryText(it), AppLanguageRuntime.effectiveLanguage.value) })
            }
            AppLanguageRuntime.update(AppLanguage.English)
            assertEquals("Plain-text lyrics · 1 line", resolveUiText(LyricsSearchContentType.PLAIN.summaryText(1, describeType = true), AppLanguageRuntime.effectiveLanguage.value))
        } finally {
            AppLanguageRuntime.update(originalLanguage)
        }
    }

    @Test
    fun sharingSummaryPreservesRawLabelsAndChangesPluralWithTheSameDescription() = runTest {
        val originalLanguage = AppLanguageRuntime.appLanguage.value
        val text = lyricsSearchLineCountSummary(top.iwesley.lyn.music.core.model.UiText.Raw("服务端类型"), 1)
        try {
            AppLanguageRuntime.update(AppLanguage.English)
            assertEquals("服务端类型 · 1 line", resolveUiText(text, AppLanguageRuntime.effectiveLanguage.value))
            AppLanguageRuntime.update(AppLanguage.TraditionalChinese)
            assertEquals("服务端类型 · 1 行", resolveUiText(text, AppLanguageRuntime.effectiveLanguage.value))
        } finally {
            AppLanguageRuntime.update(originalLanguage)
        }
    }
    @Test
    fun labelsFollowRepeatedLanguageChangesOnExistingContentTypes() = runTest {
        val originalLanguage = AppLanguageRuntime.appLanguage.value
        val types = LyricsSearchContentType.entries.toList()
        val document = LyricsDocument(listOf(LyricsLine(null, "原始歌词")), sourceId = "test-source", rawPayload = "原始歌词")
        val labels = listOf(
            AppLanguage.English to listOf("Word by word", "Line by line", "Plain text"),
            AppLanguage.SimplifiedChinese to listOf("逐字", "逐行", "纯文本"),
            AppLanguage.TraditionalChinese to listOf("逐字", "逐行", "純文本"),
        )
        val descriptions = listOf(
            listOf("Word-synced lyrics", "Line-synced lyrics", "Plain-text lyrics"),
            listOf("逐字歌词", "逐行歌词", "纯文本歌词"),
            listOf("逐字歌詞", "逐行歌詞", "純文本歌詞"),
        )
        try {
            repeat(2) {
                labels.forEachIndexed { index, (language, expected) ->
                    AppLanguageRuntime.update(language)
                    assertEquals(expected, types.map { resolveUiText(it.shortLabel, language) })
                    assertEquals(descriptions[index], types.map { resolveUiText(it.descriptionLabel, language) })
                    assertSame(types.last(), resolveLyricsSearchContentType(document))
                    assertEquals("原始歌词", document.lines.single().text)
                }
            }
        } finally {
            AppLanguageRuntime.update(originalLanguage)
        }
    }
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `enhanced lyrics are labeled as word by word`() = runTest {
        val document = LyricsDocument(
            lines = listOf(LyricsLine(timestampMs = 1_000L, text = "你好")),
            sourceId = "test-source",
            rawPayload = "[00:01.00]<00:01.00>你<00:01.30>好",
        )

        assertEquals(LyricsSearchContentType.WORD, resolveLyricsSearchContentType(document))
    }

    @Test
    fun `synced line lyrics are labeled as line by line`() = runTest {
        val document = LyricsDocument(
            lines = listOf(LyricsLine(timestampMs = 1_000L, text = "第一句")),
            sourceId = "test-source",
            rawPayload = "[00:01.00]第一句",
        )

        assertEquals(LyricsSearchContentType.LINE, resolveLyricsSearchContentType(document))
    }

    @Test
    fun `plain lyrics are labeled as plain text`() = runTest {
        val document = LyricsDocument(
            lines = listOf(LyricsLine(timestampMs = null, text = "第一句")),
            sourceId = "test-source",
            rawPayload = "第一句",
        )

        assertEquals(LyricsSearchContentType.PLAIN, resolveLyricsSearchContentType(document))
    }
}
