package top.iwesley.lyn.music.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.*

@OptIn(ExperimentalForeignApi::class)
internal actual object AppleAudioSessionCoordinator {
    actual fun configureForPlayback() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
    }

    actual fun deactivate() = Unit
}
