package top.iwesley.lyn.music.platform

import androidx.media3.common.Player
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

    private class Fixture(duration: Long = 120_000, start: Long = 0, playing: Boolean = true) {
        val driver = Driver()
        val session = AndroidNavidromePlaybackSession(driver, "https://lan.test/rest/stream?id=1&maxBitRate=192",
            duration, playing, start, {})
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

    private data class Open(val url: String, val response: (NavidromeStreamResponse) -> Unit)
    private class Driver : NavidromePlaybackDriver {
        override var mediaId: String? = null
        override var positionMs = 0L
        override var durationMs = 0L
        override var seekable = false
        override var ready = false
        override var ended = false
        var playRequested = false
            private set
        val opens = mutableListOf<Open>()
        override fun open(url: String, mediaId: String, playWhenReady: Boolean, onResponse: (NavidromeStreamResponse) -> Unit) {
            this.mediaId = mediaId
            positionMs = 0L
            ready = false
            ended = false
            seekable = false
            playRequested = playWhenReady
            opens += Open(url, onResponse)
        }
        override fun seek(positionMs: Long) { this.positionMs = positionMs; ended = false }
        override fun setPlaying(playing: Boolean) { this.playRequested = playing }
    }
}
