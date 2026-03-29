package top.iwesley.lyn.music.platform

internal expect object AppleAudioSessionCoordinator {
    fun configureForPlayback()
    fun deactivate()
}
