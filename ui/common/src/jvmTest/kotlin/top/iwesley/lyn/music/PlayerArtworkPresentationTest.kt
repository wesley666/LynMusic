package top.iwesley.lyn.music

import kotlin.test.*
import kotlinx.coroutines.test.runTest
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.testing.formatTrackTechnicalSummary

class PlayerArtworkPresentationTest {
    @BeforeTest fun language() { AppLanguageRuntime.update(AppLanguage.SimplifiedChinese) }

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
    fun `player artwork display visual factors keep layout height stable`() = runTest {
        assertEquals(1f, playerArtworkDisplayVisualWidthFactor(PlayerArtworkStyle.VINYL))
        assertEquals(1.24f, playerArtworkDisplayVisualWidthFactor(PlayerArtworkStyle.HALF_RECORD))
        assertEquals(1f, playerArtworkDisplayVisualWidthFactor(PlayerArtworkStyle.MINIMAL_COVER))

        PlayerArtworkStyle.entries.forEach { style ->
            assertEquals(1f, playerArtworkDisplayVisualHeightFactor(style))
        }
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
