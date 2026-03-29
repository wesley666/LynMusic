package top.iwesley.lyn.music.platform

internal actual object AppleAudioSessionCoordinator {
    actual fun configureForPlayback() = Unit

    actual fun deactivate() = Unit
}
