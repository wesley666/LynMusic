package top.iwesley.lyn.music

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartupAutoOpenGateTest {
    @kotlin.test.BeforeTest
    fun selectFixtureLanguage() {
        top.iwesley.lyn.music.core.model.AppLanguageRuntime.update(top.iwesley.lyn.music.core.model.AppLanguage.SimplifiedChinese)
    }

    @Test
    fun `gate allows only the first requested startup expansion`() = runBlocking {
        val gate = StartupAutoOpenGate()
        val expansionRequests = mutableListOf<Boolean>()

        gate.runStartupHydration(requested = true) { expansionRequests += it }
        gate.runStartupHydration(requested = true) { expansionRequests += it }

        assertTrue(expansionRequests[0])
        assertFalse(expansionRequests[1])
    }

    @Test
    fun `disabled first request still consumes the startup opportunity`() = runBlocking {
        val gate = StartupAutoOpenGate()
        val expansionRequests = mutableListOf<Boolean>()

        gate.runStartupHydration(requested = false) { expansionRequests += it }
        gate.runStartupHydration(requested = true) { expansionRequests += it }

        assertFalse(expansionRequests[0])
        assertFalse(expansionRequests[1])
    }

    @Test
    fun `new host lifecycle receives a fresh startup opportunity`() = runBlocking {
        val previousGate = StartupAutoOpenGate()
        var previousExpansionRequest = false
        previousGate.runStartupHydration(requested = true) {
            previousExpansionRequest = it
        }

        val newGate = StartupAutoOpenGate()
        var newExpansionRequest = false
        newGate.runStartupHydration(requested = true) {
            newExpansionRequest = it
        }

        assertTrue(previousExpansionRequest)
        assertTrue(newExpansionRequest)
    }

    @Test
    fun `cancelled hydration transfers the original startup request to the next host`() = runBlocking {
        val gate = StartupAutoOpenGate()
        val firstHydrationStarted = CompletableDeferred<Unit>()
        val secondExpansionRequest = CompletableDeferred<Boolean>()
        val firstHydration = launch {
            gate.runStartupHydration(requested = true) { expandPlayerAfterHydration ->
                assertTrue(expandPlayerAfterHydration)
                firstHydrationStarted.complete(Unit)
                awaitCancellation()
            }
        }
        firstHydrationStarted.await()
        val secondHydration = launch {
            gate.runStartupHydration(requested = false) { expandPlayerAfterHydration ->
                secondExpansionRequest.complete(expandPlayerAfterHydration)
            }
        }

        assertFalse(secondExpansionRequest.isCompleted)
        firstHydration.cancelAndJoin()

        assertTrue(secondExpansionRequest.await())
        secondHydration.join()
    }

    @Test
    fun `overlapping host waits and stays collapsed after first hydration completes`() = runBlocking {
        val gate = StartupAutoOpenGate()
        val firstHydrationStarted = CompletableDeferred<Unit>()
        val finishFirstHydration = CompletableDeferred<Unit>()
        val secondExpansionRequest = CompletableDeferred<Boolean>()
        val firstHydration = launch {
            gate.runStartupHydration(requested = true) { expandPlayerAfterHydration ->
                assertTrue(expandPlayerAfterHydration)
                firstHydrationStarted.complete(Unit)
                finishFirstHydration.await()
            }
        }
        firstHydrationStarted.await()
        val secondHydration = launch {
            gate.runStartupHydration(requested = true) { expandPlayerAfterHydration ->
                secondExpansionRequest.complete(expandPlayerAfterHydration)
            }
        }

        assertFalse(secondExpansionRequest.isCompleted)
        finishFirstHydration.complete(Unit)
        firstHydration.join()

        assertFalse(secondExpansionRequest.await())
        secondHydration.join()
    }
}
