package top.iwesley.lyn.music.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopLyricsPositionTest {
    @Test
    fun `position round trip preserves window center`() {
        val viewport = DesktopLyricsViewport(left = -1000, top = 40, width = 2000, height = 1000)
        val position = calculateDesktopLyricsPosition(
            windowX = -250,
            windowY = 340,
            windowWidth = 400,
            windowHeight = 100,
            viewport = viewport,
            displayId = "secondary",
        )

        assertEquals(0.475f, position.centerXFraction)
        assertEquals(0.35f, position.centerYFraction)
        assertEquals("secondary", position.displayId)
        assertEquals(
            DesktopLyricsWindowLocation(x = -250, y = 340),
            calculateDesktopLyricsWindowLocation(position, 400, 100, viewport),
        )
    }

    @Test
    fun `restored location is clamped into visible viewport`() {
        val viewport = DesktopLyricsViewport(left = 10, top = 20, width = 800, height = 600)

        assertEquals(
            DesktopLyricsWindowLocation(x = 610, y = 520),
            calculateDesktopLyricsWindowLocation(
                position = DesktopLyricsPosition(1f, 1f),
                windowWidth = 200,
                windowHeight = 100,
                viewport = viewport,
            ),
        )
        assertEquals(
            DesktopLyricsWindowLocation(x = 10, y = 20),
            calculateDesktopLyricsWindowLocation(
                position = DesktopLyricsPosition(0f, 0f),
                windowWidth = 900,
                windowHeight = 700,
                viewport = viewport,
            ),
        )
    }

    @Test
    fun `stored fractions are normalized and invalid values are rejected`() {
        assertEquals(
            DesktopLyricsPosition(0.25f, 0.75f, "display"),
            desktopLyricsPositionOrNull(0.25f, 0.75f, " display "),
        )
        assertNull(desktopLyricsPositionOrNull(-0.01f, 0.5f))
        assertNull(desktopLyricsPositionOrNull(0.5f, 1.01f))
        assertNull(desktopLyricsPositionOrNull(Float.NaN, 0.5f))
        assertNull(desktopLyricsPositionOrNull(0.5f, Float.POSITIVE_INFINITY))
        assertNull(desktopLyricsPositionOrNull(null, 0.5f))
    }

    @Test
    fun `position calculation clamps an offscreen dragged center`() {
        val viewport = DesktopLyricsViewport(left = 0, top = 0, width = 800, height = 600)

        assertEquals(
            DesktopLyricsPosition(0f, 1f),
            calculateDesktopLyricsPosition(
                windowX = -400,
                windowY = 700,
                windowWidth = 200,
                windowHeight = 100,
                viewport = viewport,
            ),
        )
    }

    @Test
    fun `position is saved only after remaining stable for five seconds`() = runTest {
        val savedPositions = mutableListOf<DesktopLyricsPosition>()
        val position = DesktopLyricsPosition(0.2f, 0.8f)
        val controller = DesktopLyricsPositionSaveController(backgroundScope) { savedPositions += it }

        controller.submit(position)
        runCurrent()
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS - 1L)
        runCurrent()
        assertTrue(savedPositions.isEmpty())

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf(position), savedPositions)
    }

    @Test
    fun `new submissions reset delay and only latest position is saved`() = runTest {
        val savedPositions = mutableListOf<DesktopLyricsPosition>()
        val first = DesktopLyricsPosition(0.2f, 0.8f)
        val latest = DesktopLyricsPosition(0.7f, 0.3f)
        val controller = DesktopLyricsPositionSaveController(backgroundScope) { savedPositions += it }

        controller.submit(first)
        runCurrent()
        advanceTimeBy(4_000L)
        controller.submit(latest)
        runCurrent()
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS - 1L)
        runCurrent()
        assertTrue(savedPositions.isEmpty())

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf(latest), savedPositions)
    }

    @Test
    fun `same position submission resets delay and discard prevents saving`() = runTest {
        val savedPositions = mutableListOf<DesktopLyricsPosition>()
        val position = DesktopLyricsPosition(0.4f, 0.6f)
        val controller = DesktopLyricsPositionSaveController(backgroundScope) { savedPositions += it }

        controller.submit(position)
        runCurrent()
        advanceTimeBy(4_000L)
        controller.submit(position)
        runCurrent()
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS - 1L)
        controller.discardPending()
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS + 1L)
        runCurrent()

        assertTrue(savedPositions.isEmpty())
    }

    @Test
    fun `failed save is not retried and does not block later positions`() = runTest {
        val savedPositions = mutableListOf<DesktopLyricsPosition>()
        val failures = mutableListOf<Exception>()
        val first = DesktopLyricsPosition(0.1f, 0.9f)
        val latest = DesktopLyricsPosition(0.6f, 0.4f)
        var shouldFail = true
        val controller = DesktopLyricsPositionSaveController(
            scope = backgroundScope,
            onSaveFailure = { failures += it },
        ) { position ->
            if (shouldFail) {
                shouldFail = false
                error("simulated write failure")
            }
            savedPositions += position
        }

        controller.submit(first)
        runCurrent()
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS)
        runCurrent()
        assertTrue(savedPositions.isEmpty())
        assertEquals("simulated write failure", failures.single().message)

        controller.submit(latest)
        runCurrent()
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS)
        runCurrent()
        assertEquals(listOf(latest), savedPositions)
    }

    @Test
    fun `discard does not interrupt a save that already started`() = runTest {
        val savedPositions = mutableListOf<DesktopLyricsPosition>()
        val saveStarted = CompletableDeferred<Unit>()
        val allowSaveToFinish = CompletableDeferred<Unit>()
        val position = DesktopLyricsPosition(0.3f, 0.7f)
        val controller = DesktopLyricsPositionSaveController(backgroundScope) { savedPosition ->
            saveStarted.complete(Unit)
            allowSaveToFinish.await()
            savedPositions += savedPosition
        }

        controller.submit(position)
        runCurrent()
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS)
        runCurrent()
        assertTrue(saveStarted.isCompleted)

        controller.discardPending()
        allowSaveToFinish.complete(Unit)
        runCurrent()

        assertEquals(listOf(position), savedPositions)
    }

    @Test
    fun `scope cancellation discards a position that is still waiting`() = runTest {
        val savedPositions = mutableListOf<DesktopLyricsPosition>()
        val controllerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val controller = DesktopLyricsPositionSaveController(controllerScope) { savedPositions += it }

        controller.submit(DesktopLyricsPosition(0.25f, 0.75f))
        runCurrent()
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS - 1L)
        controllerScope.cancel()
        advanceTimeBy(1L)
        runCurrent()

        assertTrue(savedPositions.isEmpty())
    }

    @Test
    fun `scope cancellation does not interrupt a save that already started`() = runTest {
        val savedPositions = mutableListOf<DesktopLyricsPosition>()
        val saveStarted = CompletableDeferred<Unit>()
        val allowSaveToFinish = CompletableDeferred<Unit>()
        val position = DesktopLyricsPosition(0.3f, 0.7f)
        val controllerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val controller = DesktopLyricsPositionSaveController(controllerScope) { savedPosition ->
            saveStarted.complete(Unit)
            allowSaveToFinish.await()
            savedPositions += savedPosition
        }

        controller.submit(position)
        runCurrent()
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS)
        runCurrent()
        assertTrue(saveStarted.isCompleted)

        controllerScope.cancel()
        runCurrent()
        assertTrue(savedPositions.isEmpty())
        allowSaveToFinish.complete(Unit)
        runCurrent()

        assertEquals(listOf(position), savedPositions)
    }

    @Test
    fun `started saves are serialized when a later position becomes stable`() = runTest {
        val first = DesktopLyricsPosition(0.2f, 0.8f)
        val latest = DesktopLyricsPosition(0.8f, 0.2f)
        val startedPositions = mutableListOf<DesktopLyricsPosition>()
        val allowFirstSaveToFinish = CompletableDeferred<Unit>()
        val controller = DesktopLyricsPositionSaveController(backgroundScope) { position ->
            startedPositions += position
            if (position == first) allowFirstSaveToFinish.await()
        }

        controller.submit(first)
        runCurrent()
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS)
        runCurrent()
        controller.submit(latest)
        runCurrent()
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS)
        runCurrent()
        assertEquals(listOf(first), startedPositions)

        allowFirstSaveToFinish.complete(Unit)
        runCurrent()
        assertEquals(listOf(first, latest), startedPositions)
    }

    @Test
    fun `fatal save errors are not reported as ordinary failures`() = runTest {
        val fatalError = TestFatalError()
        val uncaughtErrors = mutableListOf<Throwable>()
        val reportedFailures = mutableListOf<Exception>()
        val exceptionHandler = CoroutineExceptionHandler { _, error -> uncaughtErrors += error }
        val controllerScope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler) + exceptionHandler,
        )
        val controller = DesktopLyricsPositionSaveController(
            scope = controllerScope,
            onSaveFailure = { reportedFailures += it },
        ) { throw fatalError }

        controller.submit(DesktopLyricsPosition(0.2f, 0.8f))
        runCurrent()
        advanceTimeBy(DESKTOP_LYRICS_POSITION_SAVE_DELAY_MILLIS)
        runCurrent()

        assertTrue(reportedFailures.isEmpty())
        assertSame(fatalError, uncaughtErrors.single())
        controllerScope.cancel()
    }
}

private class TestFatalError : Error("fatal")
