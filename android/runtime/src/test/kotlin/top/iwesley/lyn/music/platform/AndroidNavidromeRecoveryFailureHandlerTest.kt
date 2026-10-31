package top.iwesley.lyn.music.platform

import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.test.*
import top.iwesley.lyn.music.domain.NavidromeTranscodeOffsetSupport
import top.iwesley.lyn.music.platform.AndroidNavidromePlaybackSessionTest.Fixture

class AndroidNavidromeRecoveryFailureHandlerTest {
    @Test fun unavailableCapabilitiesTryACachedBackupBeforeReportingFailure() {
        for (support in listOf(NavidromeTranscodeOffsetSupport.Unknown, NavidromeTranscodeOffsetSupport.Unsupported)) {
            val c = RoutedFixture()
            c.f.prepare(support = support)
            c.f.driver.positionMs = 9_500
            assertEquals(NavidromeReadRecoveryResult.AddressUnavailable, c.onError())
            assertEquals(9_500L, c.f.session.positionMs)
            assertTrue(c.f.session.awaitingPosition)
            assertFalse(c.f.session.canReload(PRIMARY))
            assertTrue(c.failures.isEmpty())
            c.f.ready(NavidromeRangeSupport.Bytes, seekable = true)
            assertEquals(9_500L, c.f.driver.positionMs)
            assertTrue(c.f.driver.playRequested)
            assertFalse(c.f.session.awaitingPosition)
            assertTrue(c.f.driver.opens.none { it.url.contains("timeOffset") })
        }
    }

    @Test fun delayedCapabilityFailuresUseTheSameFallbackHandler() {
        for (support in listOf(NavidromeTranscodeOffsetSupport.Unknown, NavidromeTranscodeOffsetSupport.Unsupported)) {
            val c = RoutedFixture()
            c.f.ready()
            c.f.driver.positionMs = 9_500
            assertEquals(NavidromeReadRecoveryResult.Recovering, c.onError())
            assertEquals(1, c.f.driver.opens.size)
            c.f.session.setPlaying(false)
            c.f.session.updateSupport(support)
            assertEquals(2, c.f.driver.opens.size)
            assertEquals(NavidromeReadRecoveryResult.AddressUnavailable, c.f.failureNotices.single().result)
            c.f.ready(NavidromeRangeSupport.Bytes, seekable = true)
            assertEquals(9_500L, c.f.driver.positionMs)
            assertFalse(c.f.driver.playRequested)
            assertTrue(c.failures.isEmpty())
        }
    }

    @Test fun failedRangeClassificationCanStillRestoreFromAnotherAddress() {
        val c = RoutedFixture()
        c.f.prepare(NavidromeRangeSupport.Unknown)
        c.f.driver.positionMs = 9_500
        assertEquals(NavidromeReadRecoveryResult.AddressUnavailable, c.onError())
        c.f.ready(NavidromeRangeSupport.Bytes, seekable = true)
        assertEquals(9_500L, c.f.driver.positionMs)
        assertEquals(1, c.fallbackCalls)
        assertTrue(c.failures.isEmpty())
    }

    @Test fun anUnrestorableBackupContinuesToTheNextAddressWithoutRevisitingFailures() {
        val c = RoutedFixture(backups = listOf(BACKUP, SECOND_BACKUP))
        c.f.prepare(support = NavidromeTranscodeOffsetSupport.Unsupported)
        c.f.driver.positionMs = 9_500
        c.onError()
        c.f.ready()
        assertEquals(SECOND_BACKUP, c.f.driver.opens.last().url)
        assertEquals(9_500L, c.f.session.positionMs)
        assertTrue(c.failures.isEmpty())
        assertFalse(c.f.session.canReload(PRIMARY))
        assertFalse(c.f.session.canReload(BACKUP))
        c.f.ready(NavidromeRangeSupport.Bytes, seekable = true)
        assertEquals(9_500L, c.f.driver.positionMs)
        assertTrue(c.f.driver.playRequested)
        assertEquals(3, c.f.driver.opens.size)
        assertFalse(c.f.session.consumeEnded())
    }

    @Test fun allUnrestorableAddressesReportOneFinalFailureAtTheSavedPosition() {
        val c = RoutedFixture(backups = listOf(BACKUP, SECOND_BACKUP))
        c.f.prepare(support = NavidromeTranscodeOffsetSupport.Unknown)
        c.f.driver.positionMs = 9_500
        c.onError()
        c.f.ready()
        c.f.ready()
        assertEquals(1, c.failures.size)
        assertEquals(9_500L, c.failures.single().positionMs)
        assertFalse(c.f.session.awaitingPosition)
        assertFalse(c.f.driver.playRequested)
        c.f.failureNotices.forEach(c.handler::handle)
        c.f.session.updateSupport(NavidromeTranscodeOffsetSupport.Unknown)
        assertEquals(1, c.failures.size)
        assertEquals(3, c.f.driver.opens.size)
    }

    @Test fun exhaustedReadRecoveryKeepsItsBudgetAndThenUsesTheSameFallbackHandler() {
        val c = RoutedFixture()
        c.f.prepare()
        c.f.driver.positionMs = 10_500
        repeat(3) { attempt ->
            val error = if (attempt == 0) readError() else SocketTimeoutException("recovery open timed out")
            assertEquals(NavidromeReadRecoveryResult.Recovering, c.onError(error))
            c.f.driver.runRecovery()
        }
        assertEquals(NavidromeReadRecoveryResult.Exhausted, c.onError(SocketTimeoutException("recovery open timed out")))
        assertEquals(listOf(0L, 1_000L, 2_000L), c.f.driver.scheduled.map { it.delayMs })
        c.f.ready(NavidromeRangeSupport.Bytes, seekable = true)
        assertEquals(10_500L, c.f.driver.positionMs)
        assertTrue(c.failures.isEmpty())
        assertFalse(c.f.session.canReload(PRIMARY))
    }

    @Test fun anInvalidTargetNeverTriesABackupEvenWhenCapabilityIsUnavailable() {
        for (support in listOf(null, NavidromeTranscodeOffsetSupport.Supported, NavidromeTranscodeOffsetSupport.Unknown)) {
            val c = RoutedFixture()
            if (support == null) c.f.ready() else c.f.prepare(support = support)
            c.f.driver.positionMs = 120_000
            assertEquals(NavidromeReadRecoveryResult.InvalidTarget, c.onError())
            assertEquals(0, c.fallbackCalls)
            assertEquals(1, c.f.driver.opens.size)
            assertEquals(NavidromeReadRecoveryResult.InvalidTarget, c.failures.single().result)
            assertEquals(120_000L, c.failures.single().positionMs)
        }
    }

    @Test fun nonRetryableErrorsDoNotTryOtherAddresses() {
        for (error in listOf(IOException("Response code: 403"), IllegalStateException("decoder failed"))) {
            val c = RoutedFixture()
            c.f.prepare()
            c.f.driver.positionMs = 9_500
            assertEquals(NavidromeReadRecoveryResult.NotApplicable, c.onError(error))
            assertEquals(0, c.fallbackCalls)
            assertSame(error, c.failures.single().error)
            assertEquals(9_500L, c.failures.single().positionMs)
        }
    }

    @Test fun aNativeSeekRejectsAnOldFailureEvenWhenTheMediaItemIsUnchanged() {
        val c = deferredFailure()
        val old = c.f.failureNotices.single()
        c.f.driver.seekable = true
        c.f.session.seek(20_000)
        assertEquals(old.mediaId, c.f.driver.mediaId)
        c.handler.handle(old)
        assertTrue(c.failures.isEmpty())
        assertEquals(0, c.fallbackCalls)
        assertEquals(20_000L, c.f.session.positionMs)
    }

    @Test fun oldFailuresAfterAddressChangesCloseOrNewLoadsAreIgnored() {
        repeat(3) { action ->
            val c = deferredFailure()
            val old = c.f.failureNotices.single()
            when (action) {
                0 -> c.f.session.reload(BACKUP)
                1 -> c.f.session.close()
                else -> c.current = false
            }
            c.handler.handle(old)
            assertTrue(c.failures.isEmpty())
            assertEquals(0, c.fallbackCalls)
        }
    }

    @Test fun duplicateFailureNotificationsAreSuppressedButAnExplicitRetryCanReportAgain() {
        val c = deferredFailure(backups = emptyList())
        val first = c.f.failureNotices.single()
        c.handler.handle(first)
        c.handler.handle(first)
        assertEquals(1, c.failures.size)
        c.f.session.setPlaying(true)
        val retry = c.f.failureNotices.last()
        assertEquals(first.mediaId, retry.mediaId)
        assertNotEquals(first.recoveryVersion, retry.recoveryVersion)
        c.handler.handle(retry)
        assertEquals(2, c.failures.size)
    }

    @Test fun routingThatCancelsTheFailureDoesNotPublishAnObsoleteError() {
        val f = Fixture()
        f.prepare(support = NavidromeTranscodeOffsetSupport.Unsupported)
        f.driver.positionMs = 9_500
        val result = f.session.recoverReadFailure(readError())
        val failure = f.session.recoveryFailure(readError(), result)
        val published = mutableListOf<NavidromeRecoveryFailure>()
        NavidromeRecoveryFailureHandler(f.session::acceptsRecoveryFailure,
            tryNextAddress = { f.session.close(); false }, publishFailure = { published += it }).handle(failure)
        assertTrue(published.isEmpty())
    }

    private fun deferredFailure(backups: List<String> = listOf(BACKUP)): RoutedFixture {
        val c = RoutedFixture(backups, deferNotifications = true)
        c.f.ready()
        c.f.driver.positionMs = 9_500
        assertEquals(NavidromeReadRecoveryResult.Recovering, c.onError())
        c.f.session.updateSupport(NavidromeTranscodeOffsetSupport.Unknown)
        return c
    }

    private class RoutedFixture(private val backups: List<String> = listOf(BACKUP), deferNotifications: Boolean = false) {
        var current = true
        val f = Fixture(currentRequest = { current })
        val failures = mutableListOf<NavidromeRecoveryFailure>()
        var fallbackCalls = 0
            private set
        private var selectedIndex = -1
        val handler = NavidromeRecoveryFailureHandler(f.session::acceptsRecoveryFailure,
            tryNextAddress = { nextAddress() }, publishFailure = { failures += it })

        init {
            f.recoveryFailureHandler = { if (!deferNotifications) handler.handle(it) }
        }

        fun onError(error: Throwable = readError()): NavidromeReadRecoveryResult {
            val result = f.session.recoverReadFailure(error)
            if (result != NavidromeReadRecoveryResult.Recovering) {
                f.session.onFailure()
                handler.handle(f.session.recoveryFailure(error, result))
            }
            return result
        }

        private fun nextAddress(): Boolean {
            fallbackCalls++
            while (++selectedIndex < backups.size) {
                val candidate = backups[selectedIndex]
                if (f.session.canReload(candidate)) return f.session.reload(candidate)
            }
            return false
        }
    }

    private companion object {
        const val PRIMARY = "https://lan.test/rest/stream?id=1&maxBitRate=192"
        const val BACKUP = "https://wan.test/rest/stream?id=1"
        const val SECOND_BACKUP = "https://backup.test/rest/stream?id=1"
        fun readError() = NavidromeSequentialReadException(SocketTimeoutException("read timed out"))
    }
}
