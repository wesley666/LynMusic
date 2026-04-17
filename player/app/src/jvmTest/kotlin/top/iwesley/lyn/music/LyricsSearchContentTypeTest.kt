package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine

class LyricsSearchContentTypeTest {
    @Test
    fun `enhanced lyrics are labeled as word by word`() {
        val document = LyricsDocument(
            lines = listOf(LyricsLine(timestampMs = 1_000L, text = "你好")),
            sourceId = "test-source",
            rawPayload = "[00:01.00]<00:01.00>你<00:01.30>好",
        )

        assertEquals(LyricsSearchContentType.WORD, resolveLyricsSearchContentType(document))
    }

    @Test
    fun `synced line lyrics are labeled as line by line`() {
        val document = LyricsDocument(
            lines = listOf(LyricsLine(timestampMs = 1_000L, text = "第一句")),
            sourceId = "test-source",
            rawPayload = "[00:01.00]第一句",
        )

        assertEquals(LyricsSearchContentType.LINE, resolveLyricsSearchContentType(document))
    }

    @Test
    fun `plain lyrics are labeled as plain text`() {
        val document = LyricsDocument(
            lines = listOf(LyricsLine(timestampMs = null, text = "第一句")),
            sourceId = "test-source",
            rawPayload = "第一句",
        )

        assertEquals(LyricsSearchContentType.PLAIN, resolveLyricsSearchContentType(document))
    }
}
