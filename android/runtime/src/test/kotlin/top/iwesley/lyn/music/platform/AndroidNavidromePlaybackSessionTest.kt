package top.iwesley.lyn.music.platform

import androidx.media3.common.C
import androidx.media3.common.Player
import java.net.SocketTimeoutException
import kotlin.test.*
import top.iwesley.lyn.music.domain.NavidromeTranscodeOffsetSupport

class AndroidNavidromePlaybackSessionTest {
    @Test fun sequentialTranscodeSeeksInSecondsAndKeepsWholeTrackClock() {
        val f = Fixture()
        f.prepare()
        f.session.seek(45_890)
        assertTrue(f.driver.opens.last().url.endsWith("timeOffset=45"))
        assertEquals(45_000L, f.session.positionMs)
        assertTrue(f.session.canSeek)
        f.ready()
        f.driver.positionMs = 2_000
        f.driver.durationMs = 75_000
        f.session.reconcile()
        assertEquals(47_000L, f.session.positionMs)
        assertEquals(120_000L, f.session.durationMs)
        f.session.seek(12_500)
        assertEquals(12_000L, f.session.positionMs)
        assertTrue(f.driver.opens.last().url.endsWith("timeOffset=12"))
    }

    @Test fun completeCachedOrOriginalResourceUsesNativeSeeking() {
        val f = Fixture()
        f.prepare(NavidromeRangeSupport.Bytes, seekable = true)
        f.session.seek(45_890)
        assertEquals(1, f.driver.opens.size)
        assertEquals(45_890L, f.driver.positionMs)
        assertEquals(0L, f.session.originMs)
    }

    @Test fun unsupportedAndUnknownCapabilityPreserveNativeSeekWithoutOffsetRequests() {
        for (support in listOf(NavidromeTranscodeOffsetSupport.Unsupported, NavidromeTranscodeOffsetSupport.Unknown)) {
            val f = Fixture()
            f.prepare(support = support)
            assertFalse(f.session.canSeek)
            f.session.seek(30_000)
            assertEquals(1, f.driver.opens.size)
            f.driver.seekable = true
            f.session.seek(30_000)
            assertTrue(f.session.canSeek)
            assertEquals(30_000L, f.driver.positionMs)
            assertEquals(1, f.driver.opens.size)
        }
    }

    @Test fun durationIsRequiredForServerSeekAndCanComeFromResponse() {
        val f = Fixture(duration = 0)
        f.prepare()
        assertFalse(f.session.canSeek)
        f.driver.opens.last().response(NavidromeStreamResponse(NavidromeRangeSupport.None, 80_000))
        assertTrue(f.session.canSeek)
        assertEquals(80_000L, f.session.durationMs)
    }

    @Test fun initialRestoreWaitsForCapabilityAndNeverPlaysFromZero() {
        val f = Fixture(start = 45_500)
        assertFalse(f.driver.playRequested)
        assertEquals(45_500L, f.session.positionMs)
        f.ready()
        assertFalse(f.driver.playRequested)
        f.session.updateSupport(NavidromeTranscodeOffsetSupport.Supported)
        assertTrue(f.driver.playRequested)
        assertEquals(45_000L, f.session.positionMs)
        assertTrue(f.driver.opens.last().url.endsWith("timeOffset=45"))
    }

    @Test fun nativeRestoreDoesNotWaitForCapability() {
        val f = Fixture(start = 40_000)
        f.ready(NavidromeRangeSupport.Bytes, true)
        assertEquals(40_000L, f.session.positionMs)
        assertTrue(f.driver.playRequested)
        assertEquals(1, f.driver.opens.size)
    }

    @Test fun pausedSeekAndPausingDuringPreparationStayPaused() {
        val f = Fixture(playing = false)
        f.prepare()
        f.session.seek(30_000)
        assertFalse(f.driver.playRequested)
        f.session.setPlaying(true)
        f.session.seek(35_000)
        f.session.setPlaying(false)
        f.ready()
        assertFalse(f.driver.playRequested)
        assertFalse(f.session.wantsPlayback)
        assertEquals(35_000L, f.session.positionMs)
    }

    @Test fun pauseDuringRestoreIsNotOverriddenByLateCapability() {
        val f = Fixture(start = 60_000)
        f.ready()
        f.session.setPlaying(false)
        f.session.updateSupport(NavidromeTranscodeOffsetSupport.Supported)
        f.ready()
        assertFalse(f.driver.playRequested)
        assertEquals(60_000L, f.session.positionMs)
    }

    @Test fun latestSeekWinsAndLateResponsesCannotChangeIt() {
        val f = Fixture()
        f.prepare()
        f.session.seek(30_000)
        val obsolete = f.driver.opens.last()
        f.session.seek(80_000)
        obsolete.response(NavidromeStreamResponse(NavidromeRangeSupport.Bytes, 30_000))
        assertEquals(80_000L, f.session.positionMs)
        assertEquals(120_000L, f.session.durationMs)
        assertTrue(f.session.canSeek)
        f.ready(NavidromeRangeSupport.Bytes, true)
        f.session.seek(10_000)
        assertTrue(f.driver.opens.last().url.endsWith("timeOffset=10"))
    }

    @Test fun newAddressReclassifiesFullStreamBeforeRestoringAbsolutePosition() {
        val f = Fixture()
        f.prepare()
        f.session.seek(40_000)
        f.ready()
        f.driver.positionMs = 5_000
        f.session.reload("https://wan.test/rest/stream?id=1&maxBitRate=192")
        assertFalse(f.driver.opens.last().url.contains("timeOffset"))
        assertEquals(45_000L, f.session.positionMs)
        assertFalse(f.driver.playRequested)
        f.ready(NavidromeRangeSupport.Bytes, true)
        assertEquals(45_000L, f.driver.positionMs)
        assertEquals(0L, f.session.originMs)
        assertTrue(f.driver.playRequested)
    }

    @Test fun failedOffsetStreamRetainsPositionAndIntentForAddressFallback() {
        val f = Fixture()
        f.prepare()
        f.session.seek(60_000)
        f.ready()
        f.driver.positionMs = 5_000
        f.session.onFailure()
        f.session.reload("https://wan.test/rest/stream?id=1")
        f.ready()
        f.ready()
        assertTrue(f.driver.opens.last().url.endsWith("timeOffset=65"))
        assertTrue(f.driver.playRequested)
    }

    @Test fun delayedInternalPauseDoesNotOverwritePlaybackIntentDuringFallback() {
        val f = Fixture()
        f.prepare()
        f.session.seek(60_000)
        f.session.onFailure()
        f.session.reload("https://wan.test/rest/stream?id=1")
        assertFalse(f.driver.playRequested)
        // Exo can deliver this after reload() has returned from a player error listener.
        f.session.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        assertTrue(f.session.wantsPlayback)
        f.ready()
        f.ready()
        assertTrue(f.driver.playRequested)
    }

    @Test fun delayedInternalPlayDoesNotUndoUserPauseDuringFallback() {
        val f = Fixture()
        f.prepare()
        f.session.seek(60_000)
        f.session.onFailure()
        f.session.reload("https://wan.test/rest/stream?id=1")
        f.session.setPlaying(false)
        f.session.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        f.ready()
        f.ready()
        assertFalse(f.session.wantsPlayback)
        assertFalse(f.driver.playRequested)
    }

    @Test fun systemPauseDuringRestoreIsNotOverriddenByInternalCallbacksOrCapability() {
        for (reason in listOf(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS,
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)) {
            val f = Fixture(start = 45_000)
            f.ready()
            f.session.onPlayWhenReadyChanged(false, reason)
            f.session.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            f.session.updateSupport(NavidromeTranscodeOffsetSupport.Supported)
            f.ready()
            assertFalse(f.session.wantsPlayback)
            assertFalse(f.driver.playRequested)
        }
    }

    @Test fun zeroAndEndAreBoundedAndCompletionIsCountedOnce() {
        val f = Fixture()
        f.prepare()
        f.session.seek(Long.MAX_VALUE)
        assertEquals(119_000L, f.session.positionMs)
        f.ready()
        f.driver.ended = true
        assertTrue(f.session.consumeEnded())
        assertFalse(f.session.consumeEnded())
        f.session.seek(-1)
        assertEquals(0L, f.session.positionMs)
        assertFalse(f.driver.opens.last().url.contains("timeOffset"))
        assertFalse(f.session.consumeEnded())
    }

    @Test fun closingSessionRetiresDelayedResponsesAndCompletion() {
        val f = Fixture(start = 30_000)
        val late = f.driver.opens.single()
        f.session.close()
        f.session.updateSupport(NavidromeTranscodeOffsetSupport.Supported)
        late.response(NavidromeStreamResponse(NavidromeRangeSupport.None, 100_000))
        f.driver.ended = true
        assertFalse(f.session.canSeek)
        assertFalse(f.session.consumeEnded())
        assertEquals(1, f.driver.opens.size)
    }

    @Test fun fractionalLastSecondCanBeSelectedWithoutPrematureClamping() {
        val f = Fixture(duration = 120_800)
        f.prepare()
        f.session.seek(120_500)
        assertEquals(120_000L, f.session.positionMs)
        f.session.seek(120_800)
        assertEquals(119_000L, f.session.positionMs)
    }

    @Test fun seekingAfterNativeEndAllowsExactlyOneNewCompletion() {
        val f = Fixture()
        f.prepare(NavidromeRangeSupport.Bytes, true)
        f.driver.ended = true
        assertTrue(f.session.consumeEnded())
        f.session.seek(0)
        assertFalse(f.session.consumeEnded())
        f.driver.ended = true
        assertTrue(f.session.consumeEnded())
        assertFalse(f.session.consumeEnded())
    }

    @Test fun readRecoveryKeepsAbsolutePositionUntilTheNewOffsetIsReady() {
        val f = Fixture()
        f.prepare()
        f.session.seek(45_000)
        f.ready()
        f.driver.positionMs = 6_269
        f.driver.durationMs = 75_000
        assertEquals(NavidromeReadRecoveryResult.Recovering, f.session.recoverReadFailure(readError()))
        assertEquals(0L, f.driver.scheduled.last().delayMs)
        assertEquals(51_269L, f.session.positionMs)
        assertTrue(f.session.awaitingPosition)
        f.driver.positionMs = 0
        assertEquals(51_269L, f.session.positionMs)
        f.driver.runRecovery()
        assertTrue(f.driver.opens.last().url.endsWith("timeOffset=51"))
        assertFalse(f.driver.playRequested)
        assertEquals(51_269L, f.session.positionMs)
        f.driver.ended = true
        assertFalse(f.session.consumeEnded())
        f.driver.ended = false
        f.ready()
        assertEquals(51_000L, f.session.positionMs)
        assertFalse(f.session.awaitingPosition)
        assertTrue(f.driver.playRequested)
        assertTrue(f.failures.isEmpty())
    }

    @Test fun readRecoveryWaitsForCapabilityAndUsesTheLatestPauseIntent() {
        val f = Fixture()
        f.ready()
        f.driver.positionMs = 5_500
        assertEquals(NavidromeReadRecoveryResult.Recovering, f.session.recoverReadFailure(readError()))
        assertTrue(f.driver.scheduled.isEmpty())
        f.session.setPlaying(false)
        f.session.updateSupport(NavidromeTranscodeOffsetSupport.Supported)
        f.driver.runRecovery()
        f.ready()
        assertEquals(5_000L, f.session.positionMs)
        assertFalse(f.driver.playRequested)
        assertFalse(f.session.wantsPlayback)
    }

    @Test fun audioFocusLossDuringReadRecoveryStaysPausedWhenReady() {
        val f = Fixture()
        f.prepare()
        f.driver.positionMs = 8_500
        f.session.recoverReadFailure(readError())
        f.session.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
        f.driver.runRecovery()
        f.ready()
        assertFalse(f.driver.playRequested)
        assertFalse(f.session.wantsPlayback)
    }

    @Test fun consecutiveRecoveryFailuresUseOneBudgetIncludingOpenFailures() {
        val f = Fixture()
        exhaustReadRecovery(f)
        assertEquals(listOf(0L, 1_000L, 2_000L), f.driver.scheduled.map { it.delayMs })
        assertEquals(4, f.driver.opens.size)
        assertEquals(10_500L, f.session.positionMs)
        assertFalse(f.session.awaitingPosition)
        assertFalse(f.driver.playRequested)
        assertFalse(f.session.canReload("https://lan.test/rest/stream?id=1&maxBitRate=192"))
    }

    @Test fun aSecondOfForwardProgressResetsTheRecoveryBudget() {
        val f = Fixture()
        f.prepare()
        f.driver.positionMs = 10_500
        f.session.recoverReadFailure(readError())
        f.driver.runRecovery()
        f.ready()
        f.driver.positionMs = 2_500
        assertEquals(NavidromeReadRecoveryResult.Recovering, f.session.recoverReadFailure(readError()))
        assertEquals(listOf(0L, 0L), f.driver.scheduled.map { it.delayMs })
    }

    @Test fun anExplicitPlayRequestCanRetryAnExhaustedRecovery() {
        val f = Fixture()
        exhaustReadRecovery(f)
        f.session.setPlaying(true)
        assertTrue(f.session.isRecoveringRead)
        assertEquals(0L, f.driver.scheduled.last().delayMs)
        assertTrue(f.session.canReload("https://lan.test/rest/stream?id=1&maxBitRate=192"))
        f.driver.runRecovery()
        f.ready()
        assertTrue(f.driver.playRequested)
        assertEquals(10_000L, f.session.positionMs)
    }

    @Test fun forwardProgressStartsAFreshBudgetForALaterRecoveryOpenFailure() {
        val f = Fixture()
        f.prepare()
        f.driver.positionMs = 10_500
        f.session.recoverReadFailure(readError())
        f.driver.runRecovery()
        f.ready()
        f.driver.positionMs = 2_500
        assertEquals(NavidromeReadRecoveryResult.Recovering,
            f.session.recoverReadFailure(SocketTimeoutException("later reopen timed out")))
        assertEquals(listOf(0L, 0L), f.driver.scheduled.map { it.delayMs })
    }

    @Test fun decoderFailuresDuringRecoveryDoNotEnterTheNetworkRetryBudget() {
        val f = Fixture()
        f.prepare()
        f.driver.positionMs = 10_500
        f.session.recoverReadFailure(readError())
        f.driver.runRecovery()
        f.ready()
        val scheduled = f.driver.scheduled.size
        assertEquals(NavidromeReadRecoveryResult.NotApplicable,
            f.session.recoverReadFailure(IllegalStateException("decoder failed")))
        assertEquals(scheduled, f.driver.scheduled.size)
    }

    @Test fun exhaustedAddressFallsBackToACachedResourceAtTheSavedPosition() {
        val f = Fixture()
        exhaustReadRecovery(f)
        assertTrue(f.session.reload("https://wan.test/rest/stream?id=1"))
        f.ready(NavidromeRangeSupport.Bytes, seekable = true)
        assertEquals(10_500L, f.session.positionMs)
        assertEquals(10_500L, f.driver.positionMs)
        assertTrue(f.driver.playRequested)
        val opened = f.driver.opens.size
        assertFalse(f.session.reload("https://lan.test/rest/stream?id=1&maxBitRate=192"))
        assertEquals(opened, f.driver.opens.size)
    }

    @Test fun aSequentialFallbackStartsWithAFreshBudgetAndHoldsThePosition() {
        val f = Fixture()
        exhaustReadRecovery(f)
        f.session.reload("https://wan.test/rest/stream?id=1")
        f.ready()
        assertTrue(f.driver.opens.last().url.endsWith("timeOffset=10"))
        assertEquals(10_500L, f.session.positionMs)
        assertFalse(f.driver.playRequested)
        f.ready()
        assertTrue(f.driver.playRequested)
        assertEquals(NavidromeReadRecoveryResult.Recovering, f.session.recoverReadFailure(readError()))
        assertEquals(0L, f.driver.scheduled.last().delayMs)
    }

    @Test fun anUnrestorableFallbackReportsFailureOnceAndNeverStartsFromZero() {
        val f = Fixture()
        f.prepare(support = NavidromeTranscodeOffsetSupport.Unsupported)
        f.driver.positionMs = 9_000
        f.session.reload("https://wan.test/rest/stream?id=1")
        f.ready()
        f.driver.positionMs = 0
        assertEquals(9_000L, f.session.positionMs)
        assertFalse(f.driver.playRequested)
        assertFalse(f.session.awaitingPosition)
        assertEquals(9_000L, f.failures.single().second)
        f.session.reconcile()
        f.session.updateSupport(NavidromeTranscodeOffsetSupport.Supported)
        assertEquals(1, f.failures.size)
    }

    @Test fun failedFallbackClassificationRetriesBeforeApplyingAnOffset() {
        val f = Fixture()
        exhaustReadRecovery(f)
        f.session.reload("https://wan.test/rest/stream?id=1")
        assertEquals(NavidromeReadRecoveryResult.Recovering,
            f.session.recoverReadFailure(SocketTimeoutException("classification open timed out")))
        assertEquals(0L, f.driver.scheduled.last().delayMs)
        f.driver.runRecovery()
        assertFalse(f.driver.opens.last().url.contains("timeOffset"))
        assertEquals(10_500L, f.session.positionMs)
        f.ready(NavidromeRangeSupport.Bytes, seekable = true)
        assertEquals(10_500L, f.driver.positionMs)
        assertTrue(f.driver.playRequested)
    }

    @Test fun nativeReadFailuresAfterDefaultRetriesReturnToTheExistingAddressFallback() {
        for (support in listOf(NavidromeTranscodeOffsetSupport.Unknown, NavidromeTranscodeOffsetSupport.Unsupported)) {
            val f = Fixture()
            f.prepare(NavidromeRangeSupport.Unknown, seekable = true, support = support)
            f.driver.durationMs = 120_000
            f.driver.positionMs = 51_269
            assertEquals(NavidromeReadRecoveryResult.NotApplicable, f.session.recoverReadFailure(readError()))
            assertEquals(51_269L, f.session.positionMs)
            assertTrue(f.driver.scheduled.isEmpty())
            assertTrue(f.failures.isEmpty())
            assertFalse(f.session.awaitingPosition)

            f.session.onFailure()
            assertTrue(f.session.reload("https://wan.test/rest/stream?id=1"))
            f.ready(NavidromeRangeSupport.Bytes, seekable = true)
            assertEquals(51_269L, f.driver.positionMs)
            assertTrue(f.driver.playRequested)
            assertTrue(f.driver.opens.none { it.url.contains("timeOffset") })
        }
    }

    @Test fun trackAndResponseDurationDoNotReplaceTheExtractorDurationForReadRecovery() {
        for (trackDuration in listOf(0L, 120_000L)) {
            val f = Fixture(duration = trackDuration)
            f.prepare()
            f.driver.opens.last().response(NavidromeStreamResponse(NavidromeRangeSupport.None, 120_000L))
            f.driver.positionMs = 5_500
            assertEquals(120_000L, f.session.durationMs)
            assertEquals(C.TIME_UNSET, f.driver.durationMs)
            assertEquals(NavidromeReadRecoveryResult.Recovering, f.session.recoverReadFailure(readError()))
            f.driver.runRecovery()
            assertTrue(f.driver.opens.last().url.endsWith("timeOffset=5"))
        }
    }

    @Test fun zeroNativeDurationBeforePreparationDoesNotBypassReadRecovery() {
        val f = Fixture()
        f.prepare()
        f.driver.durationMs = 0L
        f.driver.positionMs = 5_500L
        assertEquals(NavidromeReadRecoveryResult.Recovering, f.session.recoverReadFailure(readError()))
        f.driver.runRecovery()
        assertTrue(f.driver.opens.last().url.endsWith("timeOffset=5"))
    }

    @Test fun aZeroPositionFallbackRetriesClassificationAndUsesTheLatestPauseIntent() {
        for (pause in listOf(false, true)) {
            val f = Fixture()
            exhaustReadRecovery(f, position = 0L)
            assertTrue(f.session.reload("https://wan.test/rest/stream?id=1"))
            assertTrue(f.session.awaitingPosition)
            assertTrue(f.session.isRecoveringRead)
            assertFalse(f.driver.playRequested)
            assertEquals(NavidromeReadRecoveryResult.Recovering,
                f.session.recoverReadFailure(SocketTimeoutException("classification open timed out")))
            assertEquals(0L, f.driver.scheduled.last().delayMs)
            f.driver.runRecovery()
            if (pause) f.session.setPlaying(false)
            val opened = f.driver.opens.size
            f.ready()
            f.session.reconcile()
            assertEquals(opened, f.driver.opens.size)
            assertEquals(0L, f.session.positionMs)
            assertFalse(f.session.awaitingPosition)
            assertFalse(f.session.isRecoveringRead)
            assertEquals(!pause, f.driver.playRequested)
            assertEquals(!pause, f.session.wantsPlayback)
            assertTrue(f.driver.opens.none { it.url.contains("timeOffset") })
            assertTrue(f.failures.isEmpty())
        }
    }

    @Test fun aZeroPositionFallbackUsesItsBudgetBeforeTryingTheNextAddress() {
        val f = Fixture()
        exhaustReadRecovery(f, position = 0L)
        val backup = "https://wan.test/rest/stream?id=1"
        f.session.reload(backup)
        repeat(3) {
            assertEquals(NavidromeReadRecoveryResult.Recovering,
                f.session.recoverReadFailure(SocketTimeoutException("classification open timed out")))
            f.driver.runRecovery()
        }
        assertEquals(NavidromeReadRecoveryResult.Exhausted,
            f.session.recoverReadFailure(SocketTimeoutException("classification open timed out")))
        assertEquals(listOf(0L, 1_000L, 2_000L), f.driver.scheduled.drop(3).map { it.delayMs })
        assertFalse(f.session.canReload(backup))
        assertFalse(f.session.canReload("https://lan.test/rest/stream?id=1&maxBitRate=192"))
        assertTrue(f.session.reload("https://backup.test/rest/stream?id=1"))
        f.ready()
        assertEquals(9, f.driver.opens.size)
        assertEquals(0L, f.session.positionMs)
        assertFalse(f.session.awaitingPosition)
        assertTrue(f.driver.playRequested)
        assertTrue(f.driver.opens.none { it.url.contains("timeOffset") })
    }

    @Test fun fallbackClassificationRetriesDoNotRequireOffsetCapability() {
        for (support in listOf(NavidromeTranscodeOffsetSupport.Unknown, NavidromeTranscodeOffsetSupport.Unsupported)) {
            val f = Fixture()
            exhaustReadRecovery(f)
            f.session.updateSupport(support)
            f.session.reload("https://wan.test/rest/stream?id=1")
            assertEquals(NavidromeReadRecoveryResult.Recovering,
                f.session.recoverReadFailure(SocketTimeoutException("classification open timed out")))
            f.driver.runRecovery()
            f.ready(NavidromeRangeSupport.Bytes, seekable = true)
            assertEquals(10_500L, f.driver.positionMs)
            assertTrue(f.driver.playRequested)
            assertFalse(f.session.awaitingPosition)
            assertTrue(f.driver.opens.last().url.contains("wan.test"))
            assertFalse(f.driver.opens.last().url.contains("timeOffset"))
            assertTrue(f.failures.isEmpty())
        }
    }

    @Test fun readyAtZeroRetainsTheBudgetUntilASecondOfForwardProgress() {
        val f = Fixture()
        exhaustReadRecovery(f, position = 0L)
        f.session.reload("https://wan.test/rest/stream?id=1")
        f.session.recoverReadFailure(SocketTimeoutException("classification open timed out"))
        f.driver.runRecovery()
        f.ready()
        f.driver.positionMs = 500
        assertEquals(NavidromeReadRecoveryResult.Recovering, f.session.recoverReadFailure(readError()))
        assertEquals(1_000L, f.driver.scheduled.last().delayMs)
        f.driver.runRecovery()
        f.ready()
        f.driver.positionMs = 1_500
        assertEquals(NavidromeReadRecoveryResult.Recovering, f.session.recoverReadFailure(readError()))
        assertEquals(0L, f.driver.scheduled.last().delayMs)
    }

    @Test fun unavailableCapabilityFreezesReadFailureWithoutAnOffsetRequest() {
        for (support in listOf(NavidromeTranscodeOffsetSupport.Unsupported, NavidromeTranscodeOffsetSupport.Unknown)) {
            val f = Fixture()
            f.prepare(support = support)
            f.driver.positionMs = 9_500
            assertEquals(NavidromeReadRecoveryResult.AddressUnavailable, f.session.recoverReadFailure(readError()))
            f.driver.positionMs = 0
            assertEquals(9_500L, f.session.positionMs)
            assertTrue(f.driver.scheduled.isEmpty())
            assertEquals(1, f.driver.opens.size)
            assertFalse(f.driver.playRequested)
        }
    }

    @Test fun aLateFailedCapabilityReportsOneTerminalFailure() {
        val f = Fixture()
        f.ready()
        f.driver.positionMs = 4_500
        f.session.recoverReadFailure(readError())
        f.session.updateSupport(NavidromeTranscodeOffsetSupport.Unknown)
        f.session.updateSupport(NavidromeTranscodeOffsetSupport.Unsupported)
        assertEquals(4_500L, f.failures.single().second)
        assertFalse(f.driver.playRequested)
        assertTrue(f.driver.scheduled.isEmpty())
    }

    @Test fun aUserSeekCancelsQueuedRecoveryAndRetiresLateCallbacks() {
        val f = Fixture()
        f.prepare()
        f.driver.positionMs = 10_500
        f.session.recoverReadFailure(readError())
        f.driver.runRecovery()
        val oldStream = f.driver.opens.last()
        f.session.recoverReadFailure(SocketTimeoutException("open timed out"))
        val oldTask = f.driver.scheduled.last()
        f.session.seek(80_000)
        assertTrue(oldTask.canceled)
        val opened = f.driver.opens.size
        oldTask.action()
        oldStream.response(NavidromeStreamResponse(NavidromeRangeSupport.None))
        assertEquals(opened, f.driver.opens.size)
        assertEquals(80_000L, f.session.positionMs)
        assertTrue(f.driver.opens.last().url.endsWith("timeOffset=80"))
        assertTrue(f.failures.isEmpty())
    }

    @Test fun changingAddressCancelsQueuedReadRecovery() {
        val f = Fixture()
        f.prepare()
        f.driver.positionMs = 7_500
        f.session.recoverReadFailure(readError())
        val oldTask = f.driver.scheduled.last()
        f.session.reload("https://wan.test/rest/stream?id=1")
        assertTrue(oldTask.canceled)
        val opened = f.driver.opens.size
        oldTask.action()
        assertEquals(opened, f.driver.opens.size)
        assertEquals(7_500L, f.session.positionMs)
        f.ready(NavidromeRangeSupport.Bytes, seekable = true)
        assertEquals(7_500L, f.driver.positionMs)
    }

    @Test fun closingASessionCancelsRecoveryBeforeTheNextTrack() {
        val f = Fixture()
        f.prepare()
        f.driver.positionMs = 7_500
        f.session.recoverReadFailure(readError())
        val task = f.driver.scheduled.last()
        val oldStream = f.driver.opens.last()
        f.session.close()
        task.action()
        oldStream.response(NavidromeStreamResponse(NavidromeRangeSupport.None))
        f.session.updateSupport(NavidromeTranscodeOffsetSupport.Supported)
        assertTrue(task.canceled)
        assertEquals(1, f.driver.opens.size)
        assertTrue(f.failures.isEmpty())
    }

    @Test fun aSupersededLoadTokenRetiresRecoveryBeforeTheSessionIsClosed() {
        var current = true
        val f = Fixture(currentRequest = { current })
        f.prepare()
        f.driver.positionMs = 7_500
        f.session.recoverReadFailure(readError())
        val task = f.driver.scheduled.last()
        val oldStream = f.driver.opens.last()
        current = false
        task.action()
        oldStream.response(NavidromeStreamResponse(NavidromeRangeSupport.None))
        f.session.updateSupport(NavidromeTranscodeOffsetSupport.Supported)
        assertFalse(f.session.isCurrentRequest)
        assertFalse(f.session.isRecoveringRead)
        assertEquals(1, f.driver.opens.size)
        assertTrue(f.failures.isEmpty())
    }

    @Test fun readRecoveryDoesNotRequestAnEmptyExactEndStream() {
        val f = Fixture()
        f.prepare()
        f.driver.positionMs = 120_000
        assertEquals(NavidromeReadRecoveryResult.InvalidTarget, f.session.recoverReadFailure(readError()))
        assertTrue(f.driver.scheduled.isEmpty())
        assertEquals(120_000L, f.session.positionMs)
    }

    @Test fun readRecoveryCanUseTheFractionalFinalSecondWithinTheRoundingLimit() {
        val f = Fixture(duration = 120_800)
        f.prepare()
        f.driver.positionMs = 120_800
        f.session.recoverReadFailure(readError())
        f.driver.runRecovery()
        assertTrue(f.driver.opens.last().url.endsWith("timeOffset=120"))
        assertEquals(120_800L, f.session.positionMs)
        f.ready()
        assertEquals(120_000L, f.session.positionMs)
    }

    @Test fun ordinaryErrorsDoNotEnterReadRecovery() {
        val f = Fixture()
        f.prepare()
        f.driver.positionMs = 5_000
        assertEquals(NavidromeReadRecoveryResult.NotApplicable, f.session.recoverReadFailure(IllegalStateException("decoder failed")))
        assertTrue(f.driver.scheduled.isEmpty())
        assertEquals(5_000L, f.session.positionMs)
    }

    private fun readError() = NavidromeSequentialReadException(SocketTimeoutException("read timed out"))

    private fun exhaustReadRecovery(f: Fixture, position: Long = 10_500L) {
        f.prepare()
        f.driver.positionMs = position
        repeat(3) { attempt ->
            val error = if (attempt == 0) readError() else SocketTimeoutException("recovery open timed out")
            assertEquals(NavidromeReadRecoveryResult.Recovering, f.session.recoverReadFailure(error))
            f.driver.runRecovery()
        }
        assertEquals(NavidromeReadRecoveryResult.Exhausted, f.session.recoverReadFailure(SocketTimeoutException("recovery open timed out")))
    }

    internal class Fixture(duration: Long = 120_000, start: Long = 0, playing: Boolean = true,
                          currentRequest: () -> Boolean = { true }) {
        val driver = Driver()
        val failures = mutableListOf<Pair<Throwable, Long>>()
        val failureNotices = mutableListOf<NavidromeRecoveryFailure>()
        var recoveryFailureHandler: (NavidromeRecoveryFailure) -> Unit = { failures += it.error to it.positionMs }
        val session = AndroidNavidromePlaybackSession(driver, "https://lan.test/rest/stream?id=1&maxBitRate=192",
            duration, playing, start, {}, recoveryFailed = {
                failureNotices += it
                recoveryFailureHandler(it)
            },
            currentRequest = currentRequest)
        init { session.start() }
        fun prepare(ranges: NavidromeRangeSupport = NavidromeRangeSupport.None, seekable: Boolean = false,
                    support: NavidromeTranscodeOffsetSupport = NavidromeTranscodeOffsetSupport.Supported) {
            session.updateSupport(support)
            ready(ranges, seekable)
        }
        fun ready(ranges: NavidromeRangeSupport = NavidromeRangeSupport.None, seekable: Boolean = false) {
            driver.ready = true
            driver.seekable = seekable
            driver.opens.last().response(NavidromeStreamResponse(ranges))
            session.reconcile()
        }
    }

    internal data class Open(val url: String, val response: (NavidromeStreamResponse) -> Unit)
    internal data class Scheduled(val delayMs: Long, val action: () -> Unit, var canceled: Boolean = false)
    internal class Driver : NavidromePlaybackDriver {
        override var mediaId: String? = null
        override var positionMs = 0L
        override var durationMs = C.TIME_UNSET
        override var seekable = false
        override var ready = false
        override var ended = false
        var playRequested = false
            private set
        val opens = mutableListOf<Open>()
        val scheduled = mutableListOf<Scheduled>()
        override fun open(url: String, mediaId: String, playWhenReady: Boolean, readRecovery: Boolean, onResponse: (NavidromeStreamResponse) -> Unit) {
            this.mediaId = mediaId
            positionMs = 0L
            durationMs = C.TIME_UNSET
            ready = false
            ended = false
            seekable = false
            playRequested = playWhenReady
            opens += Open(url, onResponse)
        }
        override fun seek(positionMs: Long) { this.positionMs = positionMs; ended = false }
        override fun setPlaying(playing: Boolean) { this.playRequested = playing }
        override fun scheduleRecovery(delayMs: Long, action: () -> Unit): () -> Unit {
            val task = Scheduled(delayMs, action)
            scheduled += task
            return { task.canceled = true }
        }
        fun runRecovery() {
            val task = scheduled.last()
            if (!task.canceled) task.action()
        }
    }
}
