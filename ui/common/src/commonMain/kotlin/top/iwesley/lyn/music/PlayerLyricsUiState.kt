package top.iwesley.lyn.music

import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsShareFontOption
import top.iwesley.lyn.music.core.model.PlayerLyricsStyle

/** The host resolves local/cast playback; shared UI never selects a playback source. */
data class LyricsPlaybackSource(val isCast: Boolean = false, val deviceId: String? = null)

/** [lyrics] already includes the user calibration. Do not add [lyricsUserOffsetMs] again. */
data class PlayerLyricsUiState(
    val trackId: String,
    val trackSourceId: String,
    val lyrics: LyricsDocument? = null,
    val lyricsVersion: String? = null,
    val lyricsUserOffsetMs: Long = 0L,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val highlightedLineIndex: Int = -1,
    val isLyricsLoading: Boolean = false,
    val hasLyricsLookupCompleted: Boolean = false,
    val playbackSource: LyricsPlaybackSource = LyricsPlaybackSource(),
)

/** Retained in callbacks so a late click cannot calibrate a different song/version. */
data class LyricsCalibrationTarget(val trackId: String, val lyricsVersion: String)

data class LyricsCalibrationUiState(
    val offsetMs: Long = 0L,
    val target: LyricsCalibrationTarget? = null,
)

data class PlayerLyricsSettingsUiState(
    val style: PlayerLyricsStyle = PlayerLyricsStyle(),
    val availableFonts: List<LyricsShareFontOption> = emptyList(),
    val isFontsLoading: Boolean = false,
    val calibration: LyricsCalibrationUiState = LyricsCalibrationUiState(),
)
