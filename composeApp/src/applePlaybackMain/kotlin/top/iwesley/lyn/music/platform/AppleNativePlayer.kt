package top.iwesley.lyn.music.platform

import top.iwesley.lyn.music.core.model.AppleResolvedMediaLocator

/** The player surface [ApplePlaybackGateway] drives; called only on the main thread. */
internal interface ApplePlaybackEngine {
    var onProgress: (() -> Unit)?
    var onCompleted: (() -> Unit)?
    var onFailed: ((String?) -> Unit)?

    fun load(locator: AppleResolvedMediaLocator)
    fun stopAndClear()
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun canSeek(): Boolean
    fun setVolume(volume: Float)
    fun isPlaying(): Boolean
    fun positionMs(): Long
    fun durationMs(): Long?
    fun volume(): Float
    fun errorMessage(): String?
    fun release()
}

internal expect class AppleNativePlayer(platformLabel: String) : ApplePlaybackEngine {
    override var onProgress: (() -> Unit)?
    override var onCompleted: (() -> Unit)?
    override var onFailed: ((String?) -> Unit)?

    override fun load(locator: AppleResolvedMediaLocator)
    override fun stopAndClear()
    override fun play()
    override fun pause()
    override fun seekTo(positionMs: Long)
    override fun canSeek(): Boolean
    override fun setVolume(volume: Float)
    override fun isPlaying(): Boolean
    override fun positionMs(): Long
    override fun durationMs(): Long?
    override fun volume(): Float
    override fun errorMessage(): String?
    override fun release()
}
