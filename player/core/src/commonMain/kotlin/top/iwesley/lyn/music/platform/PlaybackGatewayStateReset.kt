package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.PlaybackGatewayState

fun PlaybackGatewayState.resetForTrackSwitch(
    volumeOverride: Float = volume,
    isPlayingOverride: Boolean = false,
): PlaybackGatewayState {
    return copy(
        isPlaying = isPlayingOverride,
        positionMs = 0L,
        durationMs = 0L,
        canSeek = false,
        volume = volumeOverride.coerceIn(0f, 1f),
        metadataTitle = null,
        metadataArtistName = null,
        metadataAlbumTitle = null,
        currentNavidromeAudioQuality = null,
        currentPlaybackAudioFormat = null,
        errorMessage = null,
        errorText = null,
    )
}
