package top.iwesley.lyn.music

import top.iwesley.lyn.music.testing.formatTrackTechnicalSummary
import top.iwesley.lyn.music.testing.resolveMiniPlayerLyricsText
import top.iwesley.lyn.music.testing.hasMiniPlayerLyricsContent
import top.iwesley.lyn.music.testing.formatAndroidCurrentPlaybackAudioQuality

import kotlinx.coroutines.test.runTest

import top.iwesley.lyn.music.testing.formatCurrentNavidromePlaybackAudioQuality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.compose.ui.unit.dp
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine
import top.iwesley.lyn.music.core.model.NavidromeAudioQuality
import top.iwesley.lyn.music.core.model.PlaybackAudioFormat
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.PlatformCapabilities
import top.iwesley.lyn.music.core.model.PlatformDescriptor
import top.iwesley.lyn.music.core.model.PlayerArtworkStyle
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.feature.player.PlayerIntent

class MiniPlayerBarLogicTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `automotive landscape mini player is only selected for automotive platform`() = runTest {
        assertTrue(shouldUseAutomotiveLandscapeMiniPlayer(testPlatform(ANDROID_AUTOMOTIVE_PLATFORM_NAME)))
        assertFalse(shouldUseAutomotiveLandscapeMiniPlayer(testPlatform(ANDROID_PLATFORM_NAME)))
        assertFalse(shouldUseAutomotiveLandscapeMiniPlayer(testPlatform("Desktop")))
    }

    @Test
    fun `automotive landscape mini player action order prioritizes playback controls`() = runTest {
        assertEquals(
            listOf(
                AutomotiveLandscapeMiniPlayerAction.Previous,
                AutomotiveLandscapeMiniPlayerAction.PlayPause,
                AutomotiveLandscapeMiniPlayerAction.Next,
                AutomotiveLandscapeMiniPlayerAction.Queue,
                AutomotiveLandscapeMiniPlayerAction.Favorite,
            ),
            automotiveLandscapeMiniPlayerActions(),
        )
    }

    @Test
    fun `mini player lyrics prefers highlighted line`() = runTest {
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
    fun `mini player lyrics falls back to first non blank line`() = runTest {
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
    fun `mini player lyrics returns loading text while lyrics are preparing`() = runTest {
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
    fun `mini player lyrics returns null when there is no lyric text and not loading`() = runTest {
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
    fun `mini player lyrics content is only ready for actual lyric text`() = runTest {
        assertFalse(hasMiniPlayerLyricsContent(showPortraitLyrics = true, lyricsText = null))
        assertFalse(hasMiniPlayerLyricsContent(showPortraitLyrics = true, lyricsText = ""))
        assertFalse(hasMiniPlayerLyricsContent(showPortraitLyrics = true, lyricsText = "正在准备歌词"))
        assertFalse(hasMiniPlayerLyricsContent(showPortraitLyrics = false, lyricsText = "第一句"))
        assertTrue(hasMiniPlayerLyricsContent(showPortraitLyrics = true, lyricsText = "第一句"))
    }

    @Test
    fun `compact player lyrics prefers highlighted line`() = runTest {
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
    fun `compact player lyrics falls back to first non blank line`() = runTest {
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
    fun `compact player lyrics returns null when there is no actual lyric text`() = runTest {
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
    fun `compact player lyrics does not surface loading placeholder without lyrics`() = runTest {
        assertNull(
            resolveCompactPlayerLyricsText(
                lyrics = null,
                highlightedLineIndex = -1,
            ),
        )
    }

    @Test
    fun `compact player lyrics visibility requires enabled setting and actual lyric text`() = runTest {
        assertFalse(shouldShowCompactPlayerLyrics(enabled = false, compactLyricsText = "第一句"))
        assertFalse(shouldShowCompactPlayerLyrics(enabled = true, compactLyricsText = null))
        assertFalse(shouldShowCompactPlayerLyrics(enabled = true, compactLyricsText = ""))
        assertTrue(shouldShowCompactPlayerLyrics(enabled = true, compactLyricsText = "第一句"))
    }

    @Test
    fun `track audio quality formats all navidrome fields`() = runTest {
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
    fun `track audio quality omits missing fields`() = runTest {
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
    fun `track audio quality returns null without quality fields`() = runTest {
        assertNull(formatTrackAudioQuality(sampleQualityTrack()))
    }

    @Test
    fun `current navidrome playback audio quality formats only navidrome tracks`() = runTest {
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
    fun `current navidrome playback audio quality hides until gateway reports quality`() = runTest {
        assertNull(
            formatCurrentNavidromePlaybackAudioQuality(
                track = sampleQualityTrack(),
                audioQuality = null,
            ),
        )
    }

    @Test
    fun `current playback audio format formats all fields`() = runTest {
        assertEquals(
            "44.1kHz · 320kbps · 2ch",
            formatCurrentPlaybackAudioFormat(
                PlaybackAudioFormat(
                    bitRateBps = 320_000,
                    samplingRateHz = 44_100,
                    channelCount = 2,
                ),
            ),
        )
    }

    @Test
    fun `current playback audio format omits missing bitrate`() = runTest {
        assertEquals(
            "48kHz · 2ch",
            formatCurrentPlaybackAudioFormat(
                PlaybackAudioFormat(
                    samplingRateHz = 48_000,
                    channelCount = 2,
                ),
            ),
        )
    }

    @Test
    fun `current playback audio format omits missing sampling rate`() = runTest {
        assertEquals(
            "192kbps · 1ch",
            formatCurrentPlaybackAudioFormat(
                PlaybackAudioFormat(
                    bitRateBps = 192_000,
                    channelCount = 1,
                ),
            ),
        )
    }

    @Test
    fun `current playback audio format returns null without fields`() = runTest {
        assertNull(formatCurrentPlaybackAudioFormat(PlaybackAudioFormat()))
        assertNull(formatCurrentPlaybackAudioFormat(null))
    }

    @Test
    fun `android current playback audio quality prefers exoplayer bitrate`() = runTest {
        assertEquals(
            "44.1kHz · 256kbps · 2ch",
            formatAndroidCurrentPlaybackAudioQuality(
                track = sampleQualityTrack(),
                audioFormat = PlaybackAudioFormat(
                    bitRateBps = 256_000,
                    samplingRateHz = 44_100,
                    channelCount = 2,
                ),
                navidromeQuality = NavidromeAudioQuality.Kbps192,
            ),
        )
    }

    @Test
    fun `android current playback audio quality falls back to navidrome bitrate`() = runTest {
        assertEquals(
            "44.1kHz · 192kbps · 2ch",
            formatAndroidCurrentPlaybackAudioQuality(
                track = sampleQualityTrack(),
                audioFormat = PlaybackAudioFormat(
                    samplingRateHz = 44_100,
                    channelCount = 2,
                ),
                navidromeQuality = NavidromeAudioQuality.Kbps192,
            ),
        )
        assertEquals(
            "192kbps",
            formatAndroidCurrentPlaybackAudioQuality(
                track = sampleQualityTrack(),
                audioFormat = null,
                navidromeQuality = NavidromeAudioQuality.Kbps192,
            ),
        )
    }

    @Test
    fun `android current playback audio quality falls back to original navidrome quality`() = runTest {
        assertEquals(
            "44.1kHz · 原始 · 2ch",
            formatAndroidCurrentPlaybackAudioQuality(
                track = sampleQualityTrack(),
                audioFormat = PlaybackAudioFormat(
                    samplingRateHz = 44_100,
                    channelCount = 2,
                ),
                navidromeQuality = NavidromeAudioQuality.Original,
            ),
        )
    }

    @Test
    fun `android current playback audio quality does not fallback for non navidrome tracks`() = runTest {
        assertEquals(
            "48kHz · 2ch",
            formatAndroidCurrentPlaybackAudioQuality(
                track = sampleQualityTrack(mediaLocator = "file:///music/blue.flac"),
                audioFormat = PlaybackAudioFormat(
                    samplingRateHz = 48_000,
                    channelCount = 2,
                ),
                navidromeQuality = NavidromeAudioQuality.Kbps192,
            ),
        )
        assertNull(
            formatAndroidCurrentPlaybackAudioQuality(
                track = sampleQualityTrack(mediaLocator = "file:///music/blue.flac"),
                audioFormat = null,
                navidromeQuality = NavidromeAudioQuality.Kbps192,
            ),
        )
    }

    @Test
    fun `track technical summary includes format audio quality and size`() = runTest {
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
    fun `track technical summary omits missing audio quality but keeps format and size`() = runTest {
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
    fun `player info vinyl size grows with larger compact space`() = runTest {
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
    fun `player info vinyl size keeps compact sizing identical with or without lyrics`() = runTest {
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
    fun `player info vinyl size is capped on large wide layouts`() = runTest {
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
    fun `player artwork display visual factors keep layout height stable`() = runTest {
        assertEquals(1f, playerArtworkDisplayVisualWidthFactor(PlayerArtworkStyle.VINYL))
        assertEquals(1.24f, playerArtworkDisplayVisualWidthFactor(PlayerArtworkStyle.HALF_RECORD))
        assertEquals(1f, playerArtworkDisplayVisualWidthFactor(PlayerArtworkStyle.MINIMAL_COVER))

        PlayerArtworkStyle.entries.forEach { style ->
            assertEquals(1f, playerArtworkDisplayVisualHeightFactor(style))
        }
    }

    @Test
    fun `player artwork swipe resolves skip intent by threshold and direction`() = runTest {
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
    fun `player artwork drag offset is clamped to visual bounds`() = runTest {
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

    @Test
    fun `player seek position resolves only on valid seekable playback`() = runTest {
        val seekableSnapshot = PlaybackSnapshot(
            durationMs = 100_000L,
            canSeek = true,
        )

        assertEquals(50_000L, resolvePlayerSeekPositionMs(50_000L, seekableSnapshot))
        assertEquals(0L, resolvePlayerSeekPositionMs(-1L, seekableSnapshot))
        assertEquals(100_000L, resolvePlayerSeekPositionMs(120_000L, seekableSnapshot))
        assertNull(resolvePlayerSeekPositionMs(null, seekableSnapshot))
        assertNull(
            resolvePlayerSeekPositionMs(
                50_000L,
                seekableSnapshot.copy(canSeek = false),
            ),
        )
        assertNull(
            resolvePlayerSeekPositionMs(
                50_000L,
                seekableSnapshot.copy(durationMs = 0L),
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

    private fun testPlatform(name: String): PlatformDescriptor {
        return PlatformDescriptor(
            name = name,
            capabilities = PlatformCapabilities(
                supportsLocalFolderImport = false,
                supportsSambaImport = false,
                supportsWebDavImport = false,
                supportsNavidromeImport = false,
                supportsSystemMediaControls = false,
            ),
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
