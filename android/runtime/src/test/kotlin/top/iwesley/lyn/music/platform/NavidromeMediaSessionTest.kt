package top.iwesley.lyn.music.platform

import android.app.Application
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import java.lang.reflect.Proxy
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.repository.PlaybackRepository

@UnstableApi
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NavidromeMediaSessionTest {
    @Test fun systemControllerUsesWholeTrackTimelineAndRoutesAbsoluteSeekThroughRepository() = runTest {
        val f = Fixture(backgroundScope)
        val player = f.player
        runCurrent()
        assertEquals(120_000L, player.duration)
        assertEquals(45_000L, player.currentPosition)
        assertEquals(50_000L, player.bufferedPosition)
        assertTrue(player.isCurrentMediaItemSeekable)
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        player.seekTo(80_000L)
        runCurrent()
        assertEquals(listOf(80_000L), f.seeks)

        // Paused seeks must also refresh the system timeline even though play state and duration are unchanged.
        f.snapshots.value = f.snapshots.value.copy(positionMs = 80_000L)
        runCurrent()
        assertEquals(80_000L, player.currentPosition)
        f.snapshots.value = f.snapshots.value.copy(canSeek = false)
        runCurrent()
        assertFalse(player.isCurrentMediaItemSeekable)
        assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_FORWARD))
        assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_BACK))
    }

    @Test fun pendingCapabilityRestoreFreezesSystemClockAndResumesWithoutSnapshotChange() = runTest {
        val f = Fixture(backgroundScope, playing = true, awaitingPosition = true)
        runCurrent()
        assertTrue(f.player.playWhenReady)
        assertEquals(Player.STATE_BUFFERING, f.player.playbackState)
        assertTrue(f.player.isLoading)
        assertFalse(f.player.isPlaying)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertEquals(45_000L, f.player.currentPosition)
        assertEquals(120_000L, f.player.duration)

        f.native.setPlaying(true)
        assertEquals(Player.STATE_BUFFERING, f.player.playbackState)
        // Position, play intent and canSeek do not change: this flow must independently invalidate state.
        f.awaitingPosition.value = false
        runCurrent()
        assertEquals(Player.STATE_READY, f.player.playbackState)
        assertFalse(f.player.isLoading)
        assertTrue(f.player.isPlaying)
        assertEquals(45_000L, f.player.currentPosition)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertEquals(50_000L, f.player.currentPosition)
    }

    @Test fun pausedRestoreLeavesBufferingWithoutAdvancingOrStartingPlayback() = runTest {
        val f = Fixture(backgroundScope, awaitingPosition = true)
        runCurrent()
        assertEquals(Player.STATE_BUFFERING, f.player.playbackState)
        assertFalse(f.player.playWhenReady)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertEquals(45_000L, f.player.currentPosition)

        f.awaitingPosition.value = false
        runCurrent()
        assertEquals(Player.STATE_READY, f.player.playbackState)
        assertFalse(f.player.isLoading)
        assertFalse(f.player.playWhenReady)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertEquals(45_000L, f.player.currentPosition)
    }

    @Test fun ordinaryReadyPlaybackKeepsAdvancingSystemClock() = runTest {
        val f = Fixture(backgroundScope, playing = true)
        f.native.setPlaying(true)
        runCurrent()
        assertEquals(Player.STATE_READY, f.player.playbackState)
        assertTrue(f.player.isPlaying)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertEquals(50_000L, f.player.currentPosition)
    }

    @Test fun fragmentRestartsReportWholeTrackPositionsWhilePlayingAndPaused() = runTest {
        for (playing in listOf(false, true)) {
            val f = Fixture(backgroundScope, playing = playing)
            f.native.setPlaying(playing)
            runCurrent()
            val events = mutableListOf<Pair<Player.PositionInfo, Int>>()
            f.player.addListener(object : Player.Listener {
                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                    events += newPosition to reason
                }
            })
            for (target in listOf(45_000L, 80_000L, 0L, 12_000L)) {
                f.snapshots.value = f.snapshots.value.copy(positionMs = target)
                runCurrent()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                events.clear()
                // Exo starts each replacement stream at segment time zero, including backward seeks.
                f.native.discontinueAt(0L, Player.DISCONTINUITY_REASON_REMOVE)
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertEquals(target, f.player.currentPosition)
                val (position, reason) = events.single()
                assertEquals(target, position.positionMs)
                assertEquals(target, position.contentPositionMs)
                assertEquals(Player.DISCONTINUITY_REASON_REMOVE, reason)
                assertEquals(playing, f.player.playWhenReady)
            }
        }
    }

    @Test fun ordinaryStreamsKeepTheirNativeDiscontinuityPosition() = runTest {
        val f = Fixture(backgroundScope, navidrome = false)
        runCurrent()
        val positions = mutableListOf<Long>()
        f.player.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                positions += newPosition.positionMs
            }
        })
        // A native seek callback may arrive before the repository publishes the new position.
        f.native.discontinueAt(12_000L, Player.DISCONTINUITY_REASON_SEEK)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(12_000L), positions)
    }

    @Test fun handledReadFailureKeepsTheSystemSessionBufferingAtTheSavedPosition() = runTest {
        val f = Fixture(backgroundScope, playing = true, awaitingPosition = true)
        runCurrent()
        val error = PlaybackException("read failed", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        f.native.fail(error)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertNull(f.player.playerError)
        assertEquals(Player.STATE_BUFFERING, f.player.playbackState)
        assertTrue(f.player.playWhenReady)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertEquals(45_000L, f.player.currentPosition)

        f.awaitingPosition.value = false
        f.snapshots.value = f.snapshots.value.copy(isPlaying = false, errorMessage = "recovery exhausted")
        runCurrent()
        assertSame(error, f.player.playerError)
        assertEquals(Player.STATE_IDLE, f.player.playbackState)
        assertEquals(45_000L, f.player.currentPosition)
    }

    @Test fun anOrdinaryNativeErrorRemainsVisibleToTheSystemSession() = runTest {
        val f = Fixture(backgroundScope)
        runCurrent()
        val error = PlaybackException("decoder failed", null, PlaybackException.ERROR_CODE_DECODING_FAILED)
        f.native.fail(error)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertSame(error, f.player.playerError)
        assertEquals(Player.STATE_IDLE, f.player.playbackState)
    }

    private class Fixture(scope: CoroutineScope, playing: Boolean = false, awaitingPosition: Boolean = false,
                          navidrome: Boolean = true) {
        private val track = Track("song", "nav", "Song", durationMs = 120_000,
            mediaLocator = buildNavidromeSongLocator("nav", "song"), relativePath = "song.flac")
        val snapshots = MutableStateFlow(PlaybackSnapshot(queue = listOf(track), currentIndex = 0,
            positionMs = 45_000, durationMs = 120_000, canSeek = true, isPlaying = playing))
        val awaitingPosition = MutableStateFlow(awaitingPosition)
        val seeks = mutableListOf<Long>()
        private val repository = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PlaybackRepository::class.java)) { _, method, args ->
            when (method.name) {
                "getSnapshot" -> snapshots
                "seekTo" -> { seeks += args!![0] as Long; Unit }
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as PlaybackRepository
        val native = NativePlayer()
        private val artwork = object : ArtworkCacheStore {
            override suspend fun cache(locator: String, cacheKey: String, policy: ArtworkWritePolicy): ArtworkCacheResult? = null
        }
        val player = LynMediaSessionPlayer(native, scope, repository, NoopDiagnosticLogger, artwork,
            playFromMediaId = {}, navidromeBufferPositionMs = { 50_000L.takeIf { navidrome } },
            navidromeAwaitingPosition = this.awaitingPosition)
    }

    private class NativePlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        private var nativeState = State.Builder()
                .setAvailableCommands(Player.Commands.Builder().addAll(Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_METADATA,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD).build())
                .setPlaylist(listOf(MediaItemData.Builder("fragment")
                    .setMediaItem(MediaItem.fromUri("https://example.test/stream?timeOffset=45"))
                    .setDurationUs(75_000_000).setIsSeekable(false).build()))
                .setCurrentMediaItemIndex(0)
                .setPlaybackState(Player.STATE_READY)
                .setContentPositionMs(0L)
                .setContentBufferedPositionMs { 5_000L }
                .setTotalBufferedDurationMs { 5_000L }
                .build()
        override fun getState(): State = nativeState
        fun discontinueAt(positionMs: Long, reason: Int) {
            nativeState = nativeState.buildUpon().setContentPositionMs(positionMs)
                .setPositionDiscontinuity(reason, positionMs).build()
            invalidateState()
            nativeState = nativeState.buildUpon().clearPositionDiscontinuity().build()
        }
        fun setPlaying(playing: Boolean) {
            nativeState = nativeState.buildUpon()
                .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
            invalidateState()
        }
        fun fail(error: PlaybackException) {
            nativeState = nativeState.buildUpon().setPlaybackState(Player.STATE_IDLE).setPlayerError(error).build()
            invalidateState()
        }
    }
}
