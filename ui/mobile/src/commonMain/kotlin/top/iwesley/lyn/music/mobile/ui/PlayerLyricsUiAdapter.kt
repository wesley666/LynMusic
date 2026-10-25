package top.iwesley.lyn.music.mobile.ui

import top.iwesley.lyn.music.*
import top.iwesley.lyn.music.cast.CastSessionStatus
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.feature.player.PlayerState

internal fun PlayerState.toLyricsUiState(track: Track): PlayerLyricsUiState {
    val snapshot = effectiveSnapshot
    val usingCast = castState.status == CastSessionStatus.Casting ||
        (castState.status == CastSessionStatus.Connecting &&
            castQueueIndex?.let { it in this.snapshot.queue.indices } == true)
    return PlayerLyricsUiState(
        trackId = track.id,
        trackSourceId = track.sourceId,
        lyrics = lyrics,
        lyricsVersion = lyricsVersion,
        lyricsUserOffsetMs = lyricsUserOffsetMs,
        positionMs = snapshot.positionMs,
        durationMs = snapshot.durationMs,
        isPlaying = snapshot.isPlaying,
        highlightedLineIndex = highlightedLineIndex,
        isLyricsLoading = isLyricsLoading,
        hasLyricsLookupCompleted = hasLyricsLookupCompleted,
        playbackSource = LyricsPlaybackSource(usingCast, castState.selectedDeviceId.takeIf { usingCast }),
    )
}

internal fun PlayerState.toLyricsSettingsUiState(): PlayerLyricsSettingsUiState {
    val trackId = effectiveSnapshot.currentTrack?.id
    val version = lyricsVersion
    val target = if (originalLyrics?.isSynced == true && version != null && trackId != null && trackId == lyricsTrackId) {
        LyricsCalibrationTarget(trackId, version)
    } else null
    return PlayerLyricsSettingsUiState(
        style = lyricsStyle,
        availableFonts = availableLyricsFonts,
        isFontsLoading = isLyricsFontsLoading,
        calibration = LyricsCalibrationUiState(lyricsUserOffsetMs, target),
    )
}
