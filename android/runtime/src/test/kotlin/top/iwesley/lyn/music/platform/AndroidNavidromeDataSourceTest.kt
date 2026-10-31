package top.iwesley.lyn.music.platform

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.sun.net.httpserver.HttpServer
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import top.iwesley.lyn.music.core.model.ArtworkCacheResult
import top.iwesley.lyn.music.core.model.ArtworkCacheStore
import top.iwesley.lyn.music.core.model.ArtworkWritePolicy
import top.iwesley.lyn.music.core.model.NoopDiagnosticLogger
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.buildNavidromeSongLocator
import top.iwesley.lyn.music.data.repository.PlaybackRepository
import top.iwesley.lyn.music.domain.NavidromeTranscodeOffsetSupport

@UnstableApi
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidNavidromeDataSourceTest {
    @Test fun unknownLengthMp3WithoutInterruptionKeepsAdvancing() {
        NavidromeMp3RetryFixture().use { fixture ->
            fixture.awaitReady()
            val before = fixture.session.positionMs
            fixture.session.setPlaying(true)
            fixture.awaitPlayingPast(2_000L)

            assertTrue(fixture.session.positionMs > before, fixture.diagnostics())
            assertEquals(1, fixture.requests.size, fixture.diagnostics())
            assertTrue(fixture.loadErrors.isEmpty(), fixture.diagnostics())
            assertTrue(fixture.internalDiscontinuities.isEmpty(), fixture.diagnostics())
            fixture.assertUnknownLengthMp3()
        }
    }

    @Test fun unknownLengthMp3ReadRetryKeepsWholeTrackPosition() {
        assertMp3ReadRetryKeepsPosition(offsetMs = 0L)
    }

    @Test fun offsetMp3ReadRetryKeepsWholeTrackPosition() {
        assertMp3ReadRetryKeepsPosition(offsetMs = 45_000L)
    }

    @Test fun anExistingOffsetFragmentCanRecoverEvenWhenItsResponseDeclaresByteRanges() {
        assertMp3ReadRetryKeepsPosition(offsetMs = 45_000L, offsetAcceptRanges = "bytes")
    }

    @Test fun anUnknownLengthRangeStreamStopsAtItsPositionInsteadOfRestartingFromZero() {
        NavidromeMp3RetryFixture(acceptRanges = "bytes").use { fixture ->
            fixture.awaitReady(requireCanSeek = false)
            assertEquals(C.TIME_UNSET, fixture.player.duration, fixture.diagnostics())
            assertFalse(fixture.player.isCurrentMediaItemSeekable, fixture.diagnostics())
            fixture.session.setPlaying(true)
            fixture.awaitPlayingPast(2_000L)
            val beforeReset = fixture.resetCurrentConnection()
            fixture.awaitReadFailureOrRollback()

            assertTrue(fixture.loadErrors.any { error ->
                generateSequence<Throwable>(error) { it.cause }.any { it is SocketException }
            }, fixture.diagnostics())
            assertTrue(fixture.internalDiscontinuities.none { it.afterWholeMs < it.beforeWholeMs - 999L }, fixture.diagnostics())
            assertTrue(assertNotNull(fixture.minimumWholePositionAfterReset, fixture.diagnostics()) >= beforeReset - 999L,
                fixture.diagnostics())
            assertEquals(1, fixture.recoveryFailures.size, fixture.diagnostics())
            assertEquals(NavidromeReadRecoveryResult.AddressUnavailable, fixture.terminalFailures.single().result, fixture.diagnostics())
            assertEquals(1, fixture.requests.size, fixture.diagnostics())
            assertFalse(fixture.player.isPlaying, fixture.diagnostics())
            assertEquals(0, fixture.completedStreams, fixture.diagnostics())
            assertEquals(0, fixture.endedCount, fixture.diagnostics())
            assertReadFailurePositionWasPreserved(fixture)
        }
    }

    @Test fun anUnknownLengthRangeStreamCanUseACachedBackupAndPreservePauseIntent() {
        for (pause in listOf(false, true)) {
            NavidromeMp3RetryFixture(acceptRanges = "bytes", pauseDuringRecovery = pause,
                backups = listOf(NavidromeMp3RetryFixture.BackupResource(cached = true))).use { fixture ->
                val beforeReset = startAndResetUnknownLengthStream(fixture)
                assertCachedFallback(fixture, beforeReset)
                assertEquals(!pause, fixture.session.wantsPlayback, fixture.diagnostics())
                assertEquals(!pause, fixture.player.playWhenReady, fixture.diagnostics())
                assertEquals(1, fixture.requests.count { it.path == "/rest/stream" }, fixture.diagnostics())
            }
        }
    }

    @Test fun unavailableOffsetCapabilitiesCanUseARealCachedBackup() {
        for (support in listOf(NavidromeTranscodeOffsetSupport.Unknown, NavidromeTranscodeOffsetSupport.Unsupported)) {
            NavidromeMp3RetryFixture(offsetSupport = support,
                backups = listOf(NavidromeMp3RetryFixture.BackupResource(cached = true))).use { fixture ->
                val beforeReset = startAndResetUnknownLengthStream(fixture)
                assertCachedFallback(fixture, beforeReset)
            }
        }
    }

    @Test fun aLateFailedCapabilityUsesTheSameRealHttpFallbackAndKeepsTheLatestPause() {
        NavidromeMp3RetryFixture(deferCapability = true,
            backups = listOf(NavidromeMp3RetryFixture.BackupResource(cached = true))).use { fixture ->
            val beforeReset = startAndResetUnknownLengthStream(fixture)
            fixture.awaitCapabilityWait()
            assertEquals(1, fixture.requests.size, fixture.diagnostics())
            fixture.session.setPlaying(false)
            fixture.session.updateSupport(NavidromeTranscodeOffsetSupport.Unknown)
            assertCachedFallback(fixture, beforeReset)
            assertFalse(fixture.player.playWhenReady, fixture.diagnostics())
        }
    }

    @Test fun aFailedRangeProbeCanStillRecoverUsingACachedBackup() {
        NavidromeMp3RetryFixture(acceptRanges = null, failRangeProbe = true,
            backups = listOf(NavidromeMp3RetryFixture.BackupResource(cached = true))).use { fixture ->
            val beforeReset = startAndResetUnknownLengthStream(fixture)
            assertCachedFallback(fixture, beforeReset)
            assertEquals(2, fixture.requests.count { it.path == "/rest/stream" }, fixture.diagnostics())
            assertTrue(fixture.requests.any { it.path == "/rest/stream" && it.range == "bytes=0-0" }, fixture.diagnostics())
        }
    }

    @Test fun anUnrestorableBackupContinuesToTheNextRealHttpAddress() {
        NavidromeMp3RetryFixture(offsetSupport = NavidromeTranscodeOffsetSupport.Unsupported,
            backups = listOf(NavidromeMp3RetryFixture.BackupResource(),
                NavidromeMp3RetryFixture.BackupResource(cached = true))).use { fixture ->
            val beforeReset = startAndResetUnknownLengthStream(fixture)
            assertCachedFallback(fixture, beforeReset, index = 1)
            assertEquals(1, fixture.requests.count { it.path == "/backup/0/rest/stream" }, fixture.diagnostics())
        }
    }

    @Test fun failedBackupOpensUseOneRecoveryBudgetBeforeTryingAnotherAddress() {
        NavidromeMp3RetryFixture(acceptRanges = "bytes",
            backups = listOf(NavidromeMp3RetryFixture.BackupResource(failOpens = true),
                NavidromeMp3RetryFixture.BackupResource(cached = true))).use { fixture ->
            val beforeReset = startAndResetUnknownLengthStream(fixture)
            assertCachedFallback(fixture, beforeReset, index = 1)
            assertEquals(4, fixture.requests.count { it.path == "/backup/0/rest/stream" }, fixture.diagnostics())
            assertEquals(3, fixture.recoveryTargets.size, fixture.diagnostics())
        }
    }

    @Test fun allUnrestorableRealHttpAddressesFreezeThePositionAndReportOneFailure() {
        NavidromeMp3RetryFixture(offsetSupport = NavidromeTranscodeOffsetSupport.Unknown,
            backups = listOf(NavidromeMp3RetryFixture.BackupResource())).use { fixture ->
            val beforeReset = startAndResetUnknownLengthStream(fixture)
            fixture.awaitTerminalReadFailure()
            val position = fixture.session.positionMs
            fixture.session.updateSupport(NavidromeTranscodeOffsetSupport.Unknown)
            fixture.session.reconcile()
            assertTrue(position >= beforeReset, fixture.diagnostics())
            assertEquals(position, fixture.terminalFailures.single().positionMs, fixture.diagnostics())
            assertEquals(position, fixture.session.positionMs, fixture.diagnostics())
            assertEquals(1, fixture.recoveryFailures.size, fixture.diagnostics())
            assertFalse(fixture.session.awaitingPosition, fixture.diagnostics())
            assertFalse(fixture.player.playWhenReady, fixture.diagnostics())
            assertTrue(fixture.loadErrors.any { it.isNavidromeSequentialReadFailure() }, fixture.diagnostics())
            assertTrue(fixture.internalDiscontinuities.none { it.afterWholeMs < it.beforeWholeMs - 999L }, fixture.diagnostics())
            assertEquals(0, fixture.endedCount, fixture.diagnostics())
            assertReadFailurePositionWasPreserved(fixture)
        }
    }

    private fun startAndResetUnknownLengthStream(fixture: NavidromeMp3RetryFixture): Long {
        fixture.awaitReady(requireCanSeek = false)
        assertEquals(C.TIME_UNSET, fixture.player.duration, fixture.diagnostics())
        fixture.session.setPlaying(true)
        fixture.awaitPlayingPast(2_000L)
        return fixture.resetCurrentConnection()
    }

    private fun assertCachedFallback(fixture: NavidromeMp3RetryFixture, beforeReset: Long, index: Int = 0) {
        fixture.awaitFallbackReady(index)
        if (fixture.session.wantsPlayback) fixture.awaitPlayingPast(7_300L)
        assertTrue(fixture.loadErrors.any { error ->
            generateSequence<Throwable>(error) { it.cause }.any { it is SocketException }
        }, fixture.diagnostics())
        assertTrue(fixture.loadErrors.any { it.isNavidromeSequentialReadFailure() }, fixture.diagnostics())
        assertTrue(fixture.requests.all { it.offsetSeconds == 0L }, fixture.diagnostics())
        assertTrue(fixture.requests.any { it.path == "/backup/$index/rest/stream" }, fixture.diagnostics())
        assertTrue(fixture.player.isCurrentMediaItemSeekable, fixture.diagnostics())
        assertTrue(fixture.recoveryFailures.isEmpty(), fixture.diagnostics())
        assertTrue(fixture.session.positionMs >= beforeReset - 999L, fixture.diagnostics())
        assertTrue(assertNotNull(fixture.minimumWholePositionAfterReset, fixture.diagnostics()) >= beforeReset - 999L,
            fixture.diagnostics())
        assertEquals(0, fixture.endedCount, fixture.diagnostics())
        assertReadFailurePositionWasPreserved(fixture)
    }

    private fun assertReadFailurePositionWasPreserved(fixture: NavidromeMp3RetryFixture) {
        val target = assertNotNull(fixture.firstReadFailurePositionMs, fixture.diagnostics())
        val minimum = assertNotNull(fixture.minimumWholePositionAfterReadFailure, fixture.diagnostics())
        assertTrue(minimum >= target - 999L,
            "Whole-track position moved from recovery checkpoint $target to $minimum.\n${fixture.diagnostics()}")
    }

    @Test fun xingMp3KeepsNativeReadRetriesWhenLengthAndRangeSupportAreUnknown() {
        NavidromeMp3RetryFixture(nativeSeekableMp3 = true,
            offsetSupport = NavidromeTranscodeOffsetSupport.Unknown).use { fixture ->
            fixture.awaitReady()
            assertTrue(fixture.player.isCurrentMediaItemSeekable, fixture.diagnostics())
            assertTrue(fixture.player.duration > 0L, fixture.diagnostics())
            assertTrue(fixture.player.duration != fixture.session.durationMs, fixture.diagnostics())
            assertTrue(fixture.requests.any { it.range == "bytes=0-0" }, fixture.diagnostics())
            val mediaId = fixture.player.currentMediaItem?.mediaId
            fixture.session.setPlaying(true)
            fixture.awaitPlayingPast(2_000L)
            val requestCount = fixture.requests.size
            val beforeReset = fixture.resetCurrentConnection()
            fixture.awaitReadRetry(requestCount)

            assertTrue(fixture.loadErrors.any { it.isNavidromeSequentialReadFailure() }, fixture.diagnostics())
            assertTrue(fixture.loadErrors.any { error ->
                generateSequence<Throwable>(error) { it.cause }.any { it is SocketException }
            }, fixture.diagnostics())
            assertTrue(fixture.loadErrorEndTimesMs.all { it > 0L }, fixture.diagnostics())
            assertTrue(fixture.requests.drop(requestCount).all { request ->
                request.offsetSeconds == 0L && request.range?.let {
                    Regex("bytes=([1-9]\\d*)-").matches(it)
                } == true
            }, fixture.diagnostics())
            assertEquals(mediaId, fixture.player.currentMediaItem?.mediaId, fixture.diagnostics())
            assertEquals(0L, fixture.session.originMs, fixture.diagnostics())
            assertTrue(fixture.recoveryTargets.isEmpty(), fixture.diagnostics())
            assertTrue(fixture.recoveryFailures.isEmpty(), fixture.diagnostics())
            assertTrue(fixture.internalDiscontinuities.isEmpty(), fixture.diagnostics())
            val minimumPosition = assertNotNull(fixture.minimumWholePositionAfterReset, fixture.diagnostics())
            assertTrue(minimumPosition >= beforeReset - 999L, fixture.diagnostics())
        }
    }

    @Test fun recoveryOpenFailuresStopAfterThreeActualHttpRequests() {
        NavidromeMp3RetryFixture(failRecoveryOpens = true).use { fixture ->
            fixture.awaitReady()
            fixture.session.setPlaying(true)
            fixture.awaitPlayingPast(2_000L)
            val position = fixture.resetCurrentConnection()
            fixture.awaitTerminalReadFailure()

            assertEquals(4, fixture.requests.size, fixture.diagnostics())
            assertEquals(3, fixture.recoveryTargets.size, fixture.diagnostics())
            assertEquals(1, fixture.recoveryFailures.size, fixture.diagnostics())
            assertEquals(fixture.recoveryTargets.first(), fixture.session.positionMs, fixture.diagnostics())
            assertTrue(fixture.session.positionMs >= position, fixture.diagnostics())
            assertFalse(fixture.player.isPlaying, fixture.diagnostics())
            assertFalse(fixture.session.awaitingPosition, fixture.diagnostics())
            assertTrue(fixture.requests.drop(1).all { it.offsetSeconds == fixture.recoveryTargets.first() / 1_000L }, fixture.diagnostics())
        }
    }

    @Test fun aUserPauseDuringActualMp3RecoveryDoesNotResumePlayback() {
        NavidromeMp3RetryFixture(pauseDuringRecovery = true).use { fixture ->
            fixture.awaitReady()
            fixture.session.setPlaying(true)
            fixture.awaitPlayingPast(2_000L)
            val requestCount = fixture.requests.size
            val before = fixture.resetCurrentConnection()
            fixture.awaitPausedReadRecovery(requestCount)

            assertFalse(fixture.session.wantsPlayback, fixture.diagnostics())
            assertFalse(fixture.player.playWhenReady, fixture.diagnostics())
            assertTrue(fixture.session.positionMs >= before - 999L, fixture.diagnostics())
            assertEquals(fixture.recoveryTargets.single() / 1_000L, fixture.requests.last().offsetSeconds, fixture.diagnostics())
            assertTrue(fixture.recoveryFailures.isEmpty(), fixture.diagnostics())
            assertReadFailurePositionWasPreserved(fixture)
        }
    }

    private fun assertMp3ReadRetryKeepsPosition(offsetMs: Long, offsetAcceptRanges: String = "none") {
        NavidromeMp3RetryFixture(offsetAcceptRanges = offsetAcceptRanges).use { fixture ->
            fixture.awaitReady()
            if (offsetMs > 0L) {
                fixture.session.seek(offsetMs)
                fixture.awaitReady(originMs = offsetMs)
            }
            fixture.session.setPlaying(true)
            fixture.awaitPlayingPast(2_000L)
            fixture.assertUnknownLengthMp3()
            val requestCount = fixture.requests.size
            val beforeReset = fixture.resetCurrentConnection()
            fixture.awaitReadRetry(requestCount)

            assertTrue(fixture.loadErrors.any { error ->
                generateSequence<Throwable>(error) { it.cause }.any { it is SocketException }
            }, fixture.diagnostics())
            assertTrue(fixture.session.wantsPlayback, fixture.diagnostics())
            assertEquals(fixture.recoveryTargets.single() / 1_000L, fixture.requests.last().offsetSeconds, fixture.diagnostics())
            assertEquals(0, fixture.completedStreams, fixture.diagnostics())
            assertEquals(0, fixture.endedCount, fixture.diagnostics())
            val minimumPosition = assertNotNull(fixture.minimumWholePositionAfterReset, fixture.diagnostics())
            assertTrue(
                minimumPosition >= beforeReset - 999L,
                "Whole-track progress moved backwards by at least ${beforeReset - minimumPosition} ms " +
                    "without a user seek during read recovery.\n${fixture.diagnostics()}",
            )
            assertReadFailurePositionWasPreserved(fixture)
        }
    }

    @Test fun actualExoFragmentReplacementReportsAbsoluteSystemPositions() = runTest {
        withServer(acceptRanges = "none", offsetAware = true) { fixture ->
            val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).setUsePlatformDiagnostics(false).build()
            val track = Track("song", "nav", "Song", durationMs = 2_000,
                mediaLocator = buildNavidromeSongLocator("nav", "song"), relativePath = "song.wav")
            val snapshots = MutableStateFlow(PlaybackSnapshot(queue = listOf(track), currentIndex = 0, durationMs = 2_000))
            val awaitingPosition = MutableStateFlow(false)
            lateinit var session: AndroidNavidromePlaybackSession
            fun publish() {
                if (session.replacing) return
                snapshots.value = snapshots.value.copy(positionMs = session.positionMs, canSeek = session.canSeek)
                awaitingPosition.value = session.awaitingPosition
            }
            session = AndroidNavidromePlaybackSession(ExoNavidromePlaybackDriver(player), fixture.url,
                durationMs = 2_000, playWhenReady = false, startPositionMs = 0, changed = ::publish)
            player.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) { session.reconcile(); publish() }
                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) { publish() }
            })
            val repository = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PlaybackRepository::class.java)) { _, method, _ ->
                when (method.name) {
                    "getSnapshot" -> snapshots
                    else -> error("Unexpected repository call: ${method.name}")
                }
            } as PlaybackRepository
            val artwork = object : ArtworkCacheStore {
                override suspend fun cache(locator: String, cacheKey: String, policy: ArtworkWritePolicy): ArtworkCacheResult? = null
            }
            val systemPlayer = LynMediaSessionPlayer(player, backgroundScope, repository, NoopDiagnosticLogger, artwork,
                playFromMediaId = {}, navidromeBufferPositionMs = { session.originMs + player.bufferedPosition },
                navidromeAwaitingPosition = awaitingPosition)
            val events = mutableListOf<Player.PositionInfo>()
            try {
                session.updateSupport(NavidromeTranscodeOffsetSupport.Supported)
                session.start()
                runPlayerUntil(player) { player.playbackState == Player.STATE_READY && session.canSeek }
                runCurrent()
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                systemPlayer.addListener(object : Player.Listener {
                    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                        events += newPosition
                    }
                })
                for (target in listOf(1_000L, 0L, 1_000L)) {
                    events.clear()
                    session.seek(target)
                    runPlayerUntil(player) { player.playbackState == Player.STATE_READY && session.originMs == target }
                    runCurrent()
                    Shadows.shadowOf(Looper.getMainLooper()).idle()
                    assertEquals(target, systemPlayer.currentPosition)
                    assertFalse(systemPlayer.playWhenReady)
                    assertTrue(events.isNotEmpty(), "the real replacement must emit a position event")
                    for (event in events) {
                        assertEquals(target, event.positionMs)
                        assertEquals(target, event.contentPositionMs)
                    }
                }
            } finally {
                session.close()
                player.release()
            }
        }
    }

    @Test fun actualExoPlayerResumesAfterOffsetFailureAndAddressFallback() {
        assertOffsetFailureFallback(pauseDuringRecovery = false)
    }

    @Test fun actualExoPlayerPreservesUserPauseDuringOffsetFailureRecovery() {
        assertOffsetFailureFallback(pauseDuringRecovery = true)
    }

    private fun assertOffsetFailureFallback(pauseDuringRecovery: Boolean) {
        withServer(acceptRanges = "none", offsetAware = true) { backup ->
            withServer(acceptRanges = "none", offsetAware = true, failOffset = true) { primary ->
                val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).setUsePlatformDiagnostics(false).build()
                val session = AndroidNavidromePlaybackSession(ExoNavidromePlaybackDriver(player), primary.url,
                    durationMs = 2_000L, playWhenReady = true, startPositionMs = 0L, changed = {})
                var fellBack = false
                val playChanges = mutableListOf<Boolean>()
                player.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) { session.reconcile() }
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        playChanges += playWhenReady
                        session.onPlayWhenReadyChanged(playWhenReady, reason)
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        if (!fellBack) {
                            fellBack = true
                            session.onFailure()
                            // Re-enter Exo from its error listener, as the gateway's fallback does.
                            session.reload(backup.url)
                            if (pauseDuringRecovery) session.setPlaying(false)
                        }
                    }
                })
                try {
                    session.updateSupport(NavidromeTranscodeOffsetSupport.Supported)
                    session.start()
                    runPlayerUntil(player) { player.playbackState == Player.STATE_READY && session.canSeek }
                    session.seek(1_000L)
                    runPlayerUntil(player) { fellBack && player.playbackState == Player.STATE_READY && session.originMs == 1_000L }
                    assertTrue(playChanges.contains(false), "recovery must exercise the internal pause callback")
                    assertTrue(primary.offsets.contains(1))
                    assertTrue(backup.offsets.contains(1))
                    assertEquals(!pauseDuringRecovery, session.wantsPlayback)
                    assertEquals(!pauseDuringRecovery, player.playWhenReady)
                } finally {
                    session.close()
                    player.release()
                }
            }
        }
    }

    @Test fun actualExoPlayerRestoresPausedOffsetStreamAndKeepsAbsoluteTime() {
        withServer(acceptRanges = "none", offsetAware = true) { fixture ->
            val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).setUsePlatformDiagnostics(false).build()
            val session = AndroidNavidromePlaybackSession(ExoNavidromePlaybackDriver(player), fixture.url,
                durationMs = 2_000L, playWhenReady = false, startPositionMs = 1_500L, changed = {})
            player.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) { session.reconcile() }
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    session.onPlayWhenReadyChanged(playWhenReady, reason)
                }
            })
            try {
                session.updateSupport(NavidromeTranscodeOffsetSupport.Supported)
                session.start()
                runPlayerUntil(player) { player.playbackState == Player.STATE_READY && session.originMs == 1_000L }
                assertEquals(1_000L, session.positionMs)
                assertEquals(2_000L, session.durationMs)
                assertFalse(player.playWhenReady)
                assertTrue(fixture.offsets.contains(1))
                session.seek(0)
                runPlayerUntil(player) { player.playbackState == Player.STATE_READY && session.originMs == 0L }
                assertEquals(0L, session.positionMs)
                assertEquals(2_000L, session.durationMs)
                assertFalse(player.playWhenReady)
            } finally {
                session.close()
                player.release()
            }
        }
    }

    private fun runPlayerUntil(player: ExoPlayer, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition()) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(10))
            check(player.playerError == null) { "Exo failed: ${player.playerError}" }
            check(System.nanoTime() < deadline) { "Exo timed out in state ${player.playbackState}" }
            Thread.sleep(10)
        }
    }

    @Test fun nonRangeTranscodeIsRecognizedWithoutProbeAndAudioIsUnchanged() {
        withServer(acceptRanges = "none") { fixture ->
            var response: NavidromeStreamResponse? = null
            val source = source { response = it }
            try {
                source.open(DataSpec(Uri.parse(fixture.url)))
                assertContentEquals(fixture.wave, readAll(source))
                assertEquals(NavidromeStreamResponse(NavidromeRangeSupport.None, 2_000), response)
                assertEquals(1, fixture.requests.size)
            } finally { source.close() }
        }
    }

    @Test fun fullResourceSupportsNativeByteRangeReads() {
        withServer(acceptRanges = "bytes", honorRange = true) { fixture ->
            val responses = mutableListOf<NavidromeStreamResponse>()
            val source = source { responses += it }
            try {
                source.open(DataSpec(Uri.parse(fixture.url)))
                source.close()
                source.open(DataSpec.Builder().setUri(fixture.url).setPosition(44).setLength(16).build())
                assertContentEquals(fixture.wave.copyOfRange(44, 60), readAll(source))
                assertEquals(NavidromeRangeSupport.Bytes, responses.single().ranges)
                assertEquals("bytes=44-59", fixture.requests.last())
            } finally { source.close() }
        }
    }

    @Test fun missingHeadersAreProbedUsingOneByteAndTheProbeIsNotUsedAsAudio() {
        for (honorRange in listOf(true, false)) {
            withServer(honorRange = honorRange) { fixture ->
                var response: NavidromeStreamResponse? = null
                val source = source { response = it }
                try {
                    source.open(DataSpec(Uri.parse(fixture.url)))
                    assertContentEquals(fixture.wave, readAll(source))
                    assertEquals(if (honorRange) NavidromeRangeSupport.Bytes else NavidromeRangeSupport.None, response?.ranges)
                    assertEquals(listOf("", "bytes=0-0"), fixture.requests.toList())
                } finally { source.close() }
            }
        }
    }

    @Test fun failedProbeDoesNotFailPlaybackOrInventRangeSupport() {
        withServer(failProbe = true) { fixture ->
            var response: NavidromeStreamResponse? = null
            val source = source { response = it }
            try {
                source.open(DataSpec(Uri.parse(fixture.url)))
                assertContentEquals(fixture.wave, readAll(source))
                assertEquals(NavidromeRangeSupport.Unknown, response?.ranges)
            } finally { source.close() }
        }
    }

    @Test fun responseParsingIsCaseInsensitiveAndRejectsInvalidDuration() {
        assertEquals(NavidromeRangeSupport.Unknown,
            navidromeStreamResponse(206, mapOf("Content-Range" to listOf("bytes 9-0/2"))).ranges)
        assertEquals(NavidromeStreamResponse(NavidromeRangeSupport.Bytes, 120_500),
            navidromeStreamResponse(206, mapOf("content-range" to listOf("bytes 0-0/100"), "x-content-duration" to listOf("120.5"))))
        for (value in listOf("NaN", "Infinity", "-1", "0", "unknown")) {
            assertNull(navidromeStreamResponse(200, mapOf("X-Content-Duration" to listOf(value))).durationMs)
        }
    }

    private fun source(response: (NavidromeStreamResponse) -> Unit) = AndroidNavidromeDataSource(
        DefaultHttpDataSource.Factory().createDataSource(), response)

    private fun readAll(source: AndroidNavidromeDataSource): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(1_024)
        while (true) {
            val count = source.read(buffer, 0, buffer.size)
            if (count == C.RESULT_END_OF_INPUT) return output.toByteArray()
            output.write(buffer, 0, count)
        }
    }

    private data class Fixture(val url: String, val wave: ByteArray, val requests: CopyOnWriteArrayList<String>,
                               val offsets: CopyOnWriteArrayList<Int>)

    private fun withServer(acceptRanges: String? = null, honorRange: Boolean = false, failProbe: Boolean = false,
                           offsetAware: Boolean = false, failOffset: Boolean = false,
                           block: (Fixture) -> Unit) {
        // Valid two-second PCM WAV fixture, not a fabricated Content-Length-only response.
        val pcm = ByteArray(8_000 * 2 * 2)
        val wave = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(pcm.size).put(pcm).array()
        val requests = CopyOnWriteArrayList<String>()
        val offsets = CopyOnWriteArrayList<Int>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rest/stream") { exchange ->
            try {
                val range = exchange.requestHeaders.getFirst("Range").orEmpty()
                requests += range
                val offset = Regex("(?:^|&)timeOffset=(\\d+)").find(exchange.requestURI.rawQuery.orEmpty())
                    ?.groupValues?.get(1)?.toInt() ?: 0
                offsets += offset
                val body = if (offsetAware && offset > 0) wave.copyOf(44 + 16_000).also {
                    ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(4, it.size - 8).putInt(40, it.size - 44)
                } else wave
                if ((failProbe && range.isNotEmpty()) || (failOffset && offset > 0)) {
                    exchange.sendResponseHeaders(503, -1)
                } else {
                    acceptRanges?.let { exchange.responseHeaders.set("Accept-Ranges", it) }
                    exchange.responseHeaders.set("Content-Type", "audio/wav")
                    exchange.responseHeaders.set("X-Content-Duration", "2")
                    val bounds = Regex("bytes=(\\d+)-(\\d*)").matchEntire(range)
                    val partial = honorRange && bounds != null
                    val start = if (partial) bounds.groupValues[1].toInt() else 0
                    val end = if (partial) bounds.groupValues[2].toIntOrNull() ?: body.lastIndex else body.lastIndex
                    if (partial) exchange.responseHeaders.set("Content-Range", "bytes $start-$end/${body.size}")
                    exchange.sendResponseHeaders(if (partial) 206 else 200, (end - start + 1).toLong())
                    exchange.responseBody.write(body, start, end - start + 1)
                }
            } finally { exchange.close() }
        }
        server.start()
        try { block(Fixture("http://127.0.0.1:${server.address.port}/rest/stream?id=track&maxBitRate=192", wave, requests, offsets)) }
        finally { server.stop(0) }
    }
}
