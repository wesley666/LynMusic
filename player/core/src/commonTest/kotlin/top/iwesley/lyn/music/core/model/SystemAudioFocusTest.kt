package top.iwesley.lyn.music.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemAudioFocusTest {

    @Test
    fun `transient loss while playing pauses and keeps resume marker`() {
        val result = resolveSystemAudioFocusChange(
            state = SystemAudioFocusState(),
            change = SystemAudioFocusChange.LossTransient,
            isPlaying = true,
            hasCurrentTrack = true,
        )

        assertEquals(SystemAudioFocusCommand.Pause, result.command)
        assertTrue(result.state.shouldResumeAfterFocusGain)
        assertTrue(shouldKeepAudioFocusWhilePausedForResume(result.state))
    }

    @Test
    fun `gain after transient loss plays and clears resume marker`() {
        val result = resolveSystemAudioFocusChange(
            state = SystemAudioFocusState(shouldResumeAfterFocusGain = true),
            change = SystemAudioFocusChange.Gain,
            isPlaying = false,
            hasCurrentTrack = true,
        )

        assertEquals(SystemAudioFocusCommand.Play, result.command)
        assertFalse(result.state.shouldResumeAfterFocusGain)
    }

    @Test
    fun `duckable transient loss does not pause`() {
        val result = resolveSystemAudioFocusChange(
            state = SystemAudioFocusState(),
            change = SystemAudioFocusChange.LossTransientCanDuck,
            isPlaying = true,
            hasCurrentTrack = true,
        )

        assertEquals(SystemAudioFocusCommand.None, result.command)
        assertFalse(result.state.shouldResumeAfterFocusGain)
    }

    @Test
    fun `permanent loss pauses without resume marker`() {
        val result = resolveSystemAudioFocusChange(
            state = SystemAudioFocusState(shouldResumeAfterFocusGain = true),
            change = SystemAudioFocusChange.Loss,
            isPlaying = true,
            hasCurrentTrack = true,
        )

        assertEquals(SystemAudioFocusCommand.Pause, result.command)
        assertFalse(result.state.shouldResumeAfterFocusGain)
        assertFalse(
            shouldKeepPlaybackNotificationForeground(
                isPlaying = false,
                audioFocusState = result.state,
            ),
        )
    }

    @Test
    fun `transient loss while already paused does not set resume marker`() {
        val result = resolveSystemAudioFocusChange(
            state = SystemAudioFocusState(),
            change = SystemAudioFocusChange.LossTransient,
            isPlaying = false,
            hasCurrentTrack = true,
        )

        assertEquals(SystemAudioFocusCommand.None, result.command)
        assertFalse(result.state.shouldResumeAfterFocusGain)
    }

    @Test
    fun `playback notification stays foreground while waiting for focus resume`() {
        assertTrue(
            shouldKeepPlaybackNotificationForeground(
                isPlaying = false,
                audioFocusState = SystemAudioFocusState(shouldResumeAfterFocusGain = true),
            ),
        )
    }

    @Test
    fun `playback notification stays foreground while playing`() {
        assertTrue(
            shouldKeepPlaybackNotificationForeground(
                isPlaying = true,
                audioFocusState = SystemAudioFocusState(),
            ),
        )
    }

    @Test
    fun `playback notification does not stay foreground for user paused playback`() {
        assertFalse(
            shouldKeepPlaybackNotificationForeground(
                isPlaying = false,
                audioFocusState = SystemAudioFocusState(),
            ),
        )
    }
}
