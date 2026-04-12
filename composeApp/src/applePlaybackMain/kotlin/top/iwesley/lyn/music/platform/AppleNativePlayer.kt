package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.AppleResolvedMediaLocator

internal expect class AppleNativePlayer(platformLabel: String) {
    var onProgress: (() -> Unit)?
    var onCompleted: (() -> Unit)?
    var onFailed: ((String?) -> Unit)?

    fun load(locator: AppleResolvedMediaLocator)
    fun stopAndClear()
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun isPlaying(): Boolean
    fun positionMs(): Long
    fun durationMs(): Long?
    fun volume(): Float
    fun errorMessage(): String?
    fun release()
}
