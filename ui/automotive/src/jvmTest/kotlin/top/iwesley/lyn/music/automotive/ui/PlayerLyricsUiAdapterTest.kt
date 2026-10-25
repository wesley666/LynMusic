package top.iwesley.lyn.music.automotive.ui

import kotlin.test.*
import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.cast.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.feature.player.PlayerState

class PlayerLyricsUiAdapterTest {
    private val local = Track("local", "source", "Local", mediaLocator = "", relativePath = "")
    private val remote = local.copy(id = "remote", title = "Remote", durationMs = 20_000L)
    private val original = LyricsDocument(listOf(LyricsLine(1_000L, "Line")), offsetMs = 200L, sourceId = "lyrics", rawPayload = "raw")
    private fun state() = PlayerState(
        snapshot = PlaybackSnapshot(queue = listOf(local, remote), currentIndex = 0, positionMs = 900L, durationMs = 10_000L),
        originalLyrics = original, lyrics = original.copy(offsetMs = 550L),
        lyricsTrackId = local.id, lyricsVersion = "v1", lyricsUserOffsetMs = 350L,
    )

    @Test fun `display mapping retains calibrated document without adding the offset again`() {
        val state = state()
        val ui = state.toLyricsUiState(local)
        assertSame(state.lyrics, ui.lyrics)
        assertEquals(550L, ui.lyrics?.offsetMs)
        assertEquals(350L, ui.lyricsUserOffsetMs)
        assertEquals(900L, ui.positionMs)
        assertEquals(LyricsPlaybackSource(), ui.playbackSource)
        assertEquals(LyricsCalibrationTarget("local", "v1"), state.toLyricsSettingsUiState().calibration.target)
        assertEquals(350L, state.toLyricsSettingsUiState().calibration.offsetMs)
    }

    @Test fun `cast and connecting source mappings use effective playback and calibrate only its song`() {
        for (status in listOf(CastSessionStatus.Casting, CastSessionStatus.Connecting)) {
            val state = state().copy(castQueueIndex = 1, castState = CastSessionState(
                status = status, selectedDeviceId = "speaker",
                playback = CastPlaybackState(positionMs = 4_000L, durationMs = 20_000L, isPlaying = true),
            ))
            val ui = state.toLyricsUiState(state.effectiveSnapshot.currentTrack!!)
            assertEquals("remote", ui.trackId)
            assertEquals(4_000L, ui.positionMs)
            assertEquals(20_000L, ui.durationMs)
            assertTrue(ui.isPlaying)
            assertEquals(LyricsPlaybackSource(true, "speaker"), ui.playbackSource)
            assertNull(state.toLyricsSettingsUiState().calibration.target)
            val ready = state.copy(lyricsTrackId = "remote", lyricsVersion = "remote-version")
            assertEquals(LyricsCalibrationTarget("remote", "remote-version"), ready.toLyricsSettingsUiState().calibration.target)
        }
        val connectingWithoutQueue = state().copy(castState = CastSessionState(status = CastSessionStatus.Connecting, selectedDeviceId = "speaker"))
        assertEquals(LyricsPlaybackSource(), connectingWithoutQueue.toLyricsUiState(local).playbackSource)
    }

    @Test fun `calibration is disabled during loading mismatch and plain lyrics`() {
        val state = state()
        for (unavailable in listOf(
            state.copy(originalLyrics = null),
            state.copy(lyricsVersion = null),
            state.copy(lyricsTrackId = "other"),
            state.copy(originalLyrics = original.copy(lines = listOf(LyricsLine(null, "Plain")))),
        )) {
            val ui = unavailable.toLyricsSettingsUiState()
            assertNull(ui.calibration.target)
            assertEquals(350L, ui.calibration.offsetMs)
        }
    }
}
