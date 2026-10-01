package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine

class LyricsScrollTargetTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `returns first line when synced lyrics has no highlighted line yet`() {
        val lyrics = syncedLyricsDocument(
            LyricsLine(timestampMs = 1_000L, text = "第一句"),
            LyricsLine(timestampMs = 2_000L, text = "第二句"),
        )

        assertEquals(0, resolveLyricsScrollTarget(lyrics, highlightedLineIndex = -1))
    }

    @Test
    fun `returns highlighted line when synced lyrics already has one`() {
        val lyrics = syncedLyricsDocument(
            LyricsLine(timestampMs = 1_000L, text = "第一句"),
            LyricsLine(timestampMs = 2_000L, text = "第二句"),
            LyricsLine(timestampMs = 3_000L, text = "第三句"),
        )

        assertEquals(2, resolveLyricsScrollTarget(lyrics, highlightedLineIndex = 2))
    }

    @Test
    fun `returns null for plain lyrics before any highlighted line`() {
        val lyrics = plainLyricsDocument(
            LyricsLine(timestampMs = null, text = "第一句"),
            LyricsLine(timestampMs = null, text = "第二句"),
        )

        assertNull(resolveLyricsScrollTarget(lyrics, highlightedLineIndex = -1))
    }

    @Test
    fun `returns null for empty lyrics`() {
        val lyrics = LyricsDocument(
            lines = emptyList(),
            sourceId = "test-source",
            rawPayload = "",
        )

        assertNull(resolveLyricsScrollTarget(lyrics, highlightedLineIndex = -1))
    }

    @Test
    fun `returns null when lyrics are missing`() {
        assertNull(resolveLyricsScrollTarget(lyrics = null, highlightedLineIndex = -1))
    }

    private fun syncedLyricsDocument(vararg lines: LyricsLine): LyricsDocument {
        return LyricsDocument(
            lines = lines.toList(),
            sourceId = "test-source",
            rawPayload = "synced",
        )
    }

    private fun plainLyricsDocument(vararg lines: LyricsLine): LyricsDocument {
        return LyricsDocument(
            lines = lines.toList(),
            sourceId = "test-source",
            rawPayload = "plain",
        )
    }
}
