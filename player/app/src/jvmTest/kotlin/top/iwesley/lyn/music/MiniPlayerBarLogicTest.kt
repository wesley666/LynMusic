package top.iwesley.lyn.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.feature.player.PlayerIntent

class MiniPlayerBarLogicTest {

    @Test
    fun `mini player lyrics prefers highlighted line`() {
        val lyrics = lyricsDocument(
            LyricsLine(timestampMs = 1_000L, text = "第一句"),
            LyricsLine(timestampMs = 2_000L, text = "第二句"),
        )

        assertEquals(
            "第二句",
            resolveMiniPlayerLyricsText(
                lyrics = lyrics,
                highlightedLineIndex = 1,
                isLyricsLoading = false,
            ),
        )
    }

    @Test
    fun `mini player lyrics falls back to first non blank line`() {
        val lyrics = lyricsDocument(
            LyricsLine(timestampMs = 1_000L, text = "   "),
            LyricsLine(timestampMs = 2_000L, text = "第一句"),
            LyricsLine(timestampMs = 3_000L, text = "第二句"),
        )

        assertEquals(
            "第一句",
            resolveMiniPlayerLyricsText(
                lyrics = lyrics,
                highlightedLineIndex = -1,
                isLyricsLoading = false,
            ),
        )
    }

    @Test
    fun `mini player lyrics returns loading text while lyrics are preparing`() {
        assertEquals(
            "正在准备歌词",
            resolveMiniPlayerLyricsText(
                lyrics = null,
                highlightedLineIndex = -1,
                isLyricsLoading = true,
            ),
        )
    }

    @Test
    fun `mini player lyrics returns null when there is no lyric text and not loading`() {
        val lyrics = lyricsDocument(
            LyricsLine(timestampMs = 1_000L, text = "   "),
            LyricsLine(timestampMs = 2_000L, text = ""),
        )

        assertNull(
            resolveMiniPlayerLyricsText(
                lyrics = lyrics,
                highlightedLineIndex = -1,
                isLyricsLoading = false,
            ),
        )
    }

    @Test
    fun `mini player lyrics content is only ready for actual lyric text`() {
        assertFalse(hasMiniPlayerLyricsContent(showPortraitLyrics = true, lyricsText = null))
        assertFalse(hasMiniPlayerLyricsContent(showPortraitLyrics = true, lyricsText = ""))
        assertFalse(hasMiniPlayerLyricsContent(showPortraitLyrics = true, lyricsText = "正在准备歌词"))
        assertFalse(hasMiniPlayerLyricsContent(showPortraitLyrics = false, lyricsText = "第一句"))
        assertTrue(hasMiniPlayerLyricsContent(showPortraitLyrics = true, lyricsText = "第一句"))
    }

    @Test
    fun `compact player lyrics prefers highlighted line`() {
        val lyrics = lyricsDocument(
            LyricsLine(timestampMs = 1_000L, text = "第一句"),
            LyricsLine(timestampMs = 2_000L, text = "第二句"),
        )

        assertEquals(
            "第二句",
            resolveCompactPlayerLyricsText(
                lyrics = lyrics,
                highlightedLineIndex = 1,
            ),
        )
    }

    @Test
    fun `compact player lyrics falls back to first non blank line`() {
        val lyrics = lyricsDocument(
            LyricsLine(timestampMs = 1_000L, text = "   "),
            LyricsLine(timestampMs = 2_000L, text = "第一句"),
            LyricsLine(timestampMs = 3_000L, text = "第二句"),
        )

        assertEquals(
            "第一句",
            resolveCompactPlayerLyricsText(
                lyrics = lyrics,
                highlightedLineIndex = -1,
            ),
        )
    }

    @Test
    fun `compact player lyrics returns null when there is no actual lyric text`() {
        val lyrics = lyricsDocument(
            LyricsLine(timestampMs = 1_000L, text = "   "),
            LyricsLine(timestampMs = 2_000L, text = ""),
        )

        assertNull(
            resolveCompactPlayerLyricsText(
                lyrics = lyrics,
                highlightedLineIndex = -1,
            ),
        )
    }

    @Test
    fun `compact player lyrics does not surface loading placeholder without lyrics`() {
        assertNull(
            resolveCompactPlayerLyricsText(
                lyrics = null,
                highlightedLineIndex = -1,
            ),
        )
    }

    @Test
    fun `compact player lyrics visibility requires enabled setting and actual lyric text`() {
        assertFalse(shouldShowCompactPlayerLyrics(enabled = false, compactLyricsText = "第一句"))
        assertFalse(shouldShowCompactPlayerLyrics(enabled = true, compactLyricsText = null))
        assertFalse(shouldShowCompactPlayerLyrics(enabled = true, compactLyricsText = ""))
        assertTrue(shouldShowCompactPlayerLyrics(enabled = true, compactLyricsText = "第一句"))
    }

    @Test
    fun `track audio quality formats all navidrome fields`() {
        assertEquals(
            "16bit / 44.1kHz · 880kbps · 2ch",
            formatTrackAudioQuality(
                sampleQualityTrack(
                    bitDepth = 16,
                    samplingRate = 44_100,
                    bitRate = 880,
                    channelCount = 2,
                ),
            ),
        )
    }

    @Test
    fun `track audio quality omits missing fields`() {
        assertEquals(
            "44.1kHz · 2ch",
            formatTrackAudioQuality(
                sampleQualityTrack(
                    samplingRate = 44_100,
                    channelCount = 2,
                ),
            ),
        )
    }

    @Test
    fun `track audio quality returns null without quality fields`() {
        assertNull(formatTrackAudioQuality(sampleQualityTrack()))
    }

    @Test
    fun `current navidrome playback audio quality formats only navidrome tracks`() {
        assertEquals(
            "192kbps",
            formatCurrentNavidromePlaybackAudioQuality(
                track = sampleQualityTrack(),
                audioQuality = NavidromeAudioQuality.Kbps192,
            ),
        )
        assertNull(
            formatCurrentNavidromePlaybackAudioQuality(
                track = sampleQualityTrack(mediaLocator = "file:///music/blue.mp3"),
                audioQuality = NavidromeAudioQuality.Kbps192,
            ),
        )
    }

    @Test
    fun `current navidrome playback audio quality hides until gateway reports quality`() {
        assertNull(
            formatCurrentNavidromePlaybackAudioQuality(
                track = sampleQualityTrack(),
                audioQuality = null,
            ),
        )
    }

    @Test
    fun `track technical summary includes format audio quality and size`() {
        assertEquals(
            "FLAC · 16bit / 44.1kHz · 880kbps · 2ch · 12.3 MB",
            formatTrackTechnicalSummary(
                sampleQualityTrack(
                    sizeBytes = 12_897_485L,
                    bitDepth = 16,
                    samplingRate = 44_100,
                    bitRate = 880,
                    channelCount = 2,
                ),
            ),
        )
    }

    @Test
    fun `track technical summary omits missing audio quality but keeps format and size`() {
        assertEquals(
            "MP3 · 1.0 MB",
            formatTrackTechnicalSummary(
                sampleQualityTrack(
                    relativePath = "Artist A/Album A/Blue.mp3",
                    sizeBytes = 1_048_576L,
                ),
            ),
        )
    }

    @Test
    fun `player info vinyl size grows with larger compact space`() {
        val small = resolvePlayerInfoVinylSize(
            maxWidth = 320.dp,
            maxHeight = 360.dp,
            compact = true,
            hasCompactLyrics = false,
        )
        val large = resolvePlayerInfoVinylSize(
            maxWidth = 420.dp,
            maxHeight = 520.dp,
            compact = true,
            hasCompactLyrics = false,
        )

        assertTrue(large > small)
    }

    @Test
    fun `player info vinyl size keeps compact sizing identical with or without lyrics`() {
        val withoutLyrics = resolvePlayerInfoVinylSize(
            maxWidth = 390.dp,
            maxHeight = 520.dp,
            compact = true,
            hasCompactLyrics = false,
        )
        val withLyrics = resolvePlayerInfoVinylSize(
            maxWidth = 390.dp,
            maxHeight = 520.dp,
            compact = true,
            hasCompactLyrics = true,
        )

        assertEquals(withoutLyrics, withLyrics)
    }

    @Test
    fun `player info vinyl size is capped on large wide layouts`() {
        assertEquals(
            400.dp,
            resolvePlayerInfoVinylSize(
                maxWidth = 900.dp,
                maxHeight = 900.dp,
                compact = false,
                hasCompactLyrics = false,
            ),
        )
    }

    @Test
    fun `player artwork swipe resolves skip intent by threshold and direction`() {
        assertEquals(
            PlayerIntent.SkipNext,
            resolvePlayerArtworkSwipeIntent(
                finalOffsetPx = -72f,
                swipeThresholdPx = 72f,
            ),
        )
        assertEquals(
            PlayerIntent.SkipPrevious,
            resolvePlayerArtworkSwipeIntent(
                finalOffsetPx = 72f,
                swipeThresholdPx = 72f,
            ),
        )
        assertNull(
            resolvePlayerArtworkSwipeIntent(
                finalOffsetPx = -71.9f,
                swipeThresholdPx = 72f,
            ),
        )
        assertNull(
            resolvePlayerArtworkSwipeIntent(
                finalOffsetPx = 71.9f,
                swipeThresholdPx = 72f,
            ),
        )
        assertNull(
            resolvePlayerArtworkSwipeIntent(
                finalOffsetPx = 100f,
                swipeThresholdPx = 0f,
            ),
        )
    }

    @Test
    fun `player artwork drag offset is clamped to visual bounds`() {
        assertEquals(
            40f,
            resolvePlayerArtworkDragOffsetPx(
                currentOffsetPx = 25f,
                dragAmountPx = 15f,
                maxVisualOffsetPx = 80f,
            ),
        )
        assertEquals(
            80f,
            resolvePlayerArtworkDragOffsetPx(
                currentOffsetPx = 70f,
                dragAmountPx = 20f,
                maxVisualOffsetPx = 80f,
            ),
        )
        assertEquals(
            -80f,
            resolvePlayerArtworkDragOffsetPx(
                currentOffsetPx = -70f,
                dragAmountPx = -20f,
                maxVisualOffsetPx = 80f,
            ),
        )
        assertEquals(
            0f,
            resolvePlayerArtworkDragOffsetPx(
                currentOffsetPx = 20f,
                dragAmountPx = 10f,
                maxVisualOffsetPx = 0f,
            ),
        )
    }

    private fun lyricsDocument(vararg lines: LyricsLine): LyricsDocument {
        return LyricsDocument(
            lines = lines.toList(),
            sourceId = "test-source",
            rawPayload = "lyrics",
        )
    }

    private fun sampleQualityTrack(
        relativePath: String = "Artist A/Album A/Blue.flac",
        mediaLocator: String = "lynmusic-navidrome://nav-source/song-1",
        sizeBytes: Long = 0L,
        bitDepth: Int? = null,
        samplingRate: Int? = null,
        bitRate: Int? = null,
        channelCount: Int? = null,
    ): Track {
        return Track(
            id = "track-1",
            sourceId = "nav-source",
            title = "Blue",
            mediaLocator = mediaLocator,
            relativePath = relativePath,
            sizeBytes = sizeBytes,
            bitDepth = bitDepth,
            samplingRate = samplingRate,
            bitRate = bitRate,
            channelCount = channelCount,
        )
    }
}
