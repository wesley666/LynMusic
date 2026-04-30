package top.iwesley.lyn.music.core.model

enum class SystemAudioFocusChange {
    Gain,
    Loss,
    LossTransient,
    LossTransientCanDuck,
}

enum class SystemAudioFocusCommand {
    None,
    Play,
    Pause,
}

data class SystemAudioFocusState(
    val shouldResumeAfterFocusGain: Boolean = false,
)

data class SystemAudioFocusChangeResult(
    val state: SystemAudioFocusState,
    val command: SystemAudioFocusCommand = SystemAudioFocusCommand.None,
)

fun resolveSystemAudioFocusChange(
    state: SystemAudioFocusState,
    change: SystemAudioFocusChange,
    isPlaying: Boolean,
    hasCurrentTrack: Boolean,
): SystemAudioFocusChangeResult {
    return when (change) {
        SystemAudioFocusChange.Gain -> {
            if (state.shouldResumeAfterFocusGain && hasCurrentTrack) {
                SystemAudioFocusChangeResult(
                    state = state.copy(shouldResumeAfterFocusGain = false),
                    command = SystemAudioFocusCommand.Play,
                )
            } else {
                SystemAudioFocusChangeResult(
                    state = state.copy(shouldResumeAfterFocusGain = false),
                )
            }
        }

        SystemAudioFocusChange.Loss -> {
            SystemAudioFocusChangeResult(
                state = state.copy(shouldResumeAfterFocusGain = false),
                command = if (isPlaying) SystemAudioFocusCommand.Pause else SystemAudioFocusCommand.None,
            )
        }

        SystemAudioFocusChange.LossTransient -> {
            SystemAudioFocusChangeResult(
                state = state.copy(shouldResumeAfterFocusGain = isPlaying),
                command = if (isPlaying) SystemAudioFocusCommand.Pause else SystemAudioFocusCommand.None,
            )
        }

        SystemAudioFocusChange.LossTransientCanDuck -> {
            SystemAudioFocusChangeResult(state = state)
        }
    }
}

fun shouldKeepAudioFocusWhilePausedForResume(state: SystemAudioFocusState): Boolean {
    return state.shouldResumeAfterFocusGain
}

fun shouldKeepPlaybackNotificationForeground(
    isPlaying: Boolean,
    audioFocusState: SystemAudioFocusState,
): Boolean {
    return isPlaying || audioFocusState.shouldResumeAfterFocusGain
}
