package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.PlaybackGatewayState

internal fun PlaybackGatewayState.resetForTrackSwitch(
    volumeOverride: Float = volume,
): PlaybackGatewayState {
    return copy(
        isPlaying = false,
        positionMs = 0L,
        durationMs = 0L,
        volume = volumeOverride.coerceIn(0f, 1f),
        metadataTitle = null,
        metadataArtistName = null,
        metadataAlbumTitle = null,
        errorMessage = null,
    )
}
