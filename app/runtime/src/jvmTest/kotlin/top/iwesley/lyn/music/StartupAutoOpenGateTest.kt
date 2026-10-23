package top.iwesley.lyn.music

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class StartupAutoOpenGateTest {
    @Test
    fun `only the first completed restore returns the startup request`() = runBlocking {
        val gate = StartupAutoOpenGate()
        val first = gate.restore(true) { "restored" }
        val second = gate.restore(true) { "existing" }
        assertEquals("restored", first.result)
        assertTrue(first.autoOpenRequested)
        assertEquals("existing", second.result)
        assertFalse(second.autoOpenRequested)
    }

    @Test
    fun `disabled first request consumes startup opportunity`() = runBlocking {
        val gate = StartupAutoOpenGate()
        assertFalse(gate.restore(false) { Unit }.autoOpenRequested)
        assertFalse(gate.restore(true) { Unit }.autoOpenRequested)
    }

    @Test
    fun `new host lifecycle gets a new opportunity`() = runBlocking {
        assertTrue(StartupAutoOpenGate().restore(true) { Unit }.autoOpenRequested)
        assertTrue(StartupAutoOpenGate().restore(true) { Unit }.autoOpenRequested)
    }

    @Test
    fun `cancelled restoration transfers original request to next host`() = runBlocking {
        val gate = StartupAutoOpenGate()
        val started = CompletableDeferred<Unit>()
        val outcome = CompletableDeferred<Boolean>()
        val first = launch { gate.restore(true) { started.complete(Unit); awaitCancellation() } }
        started.await()
        val second = launch { outcome.complete(gate.restore(false) { Unit }.autoOpenRequested) }
        assertFalse(outcome.isCompleted)
        first.cancelAndJoin()
        assertTrue(outcome.await())
        second.join()
    }

    @Test
    fun `overlapping host cannot consume the same startup request twice`() = runBlocking {
        val gate = StartupAutoOpenGate()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val outcome = CompletableDeferred<Boolean>()
        val first = launch {
            assertTrue(gate.restore(true) { started.complete(Unit); finish.await() }.autoOpenRequested)
        }
        started.await()
        val second = launch { outcome.complete(gate.restore(true) { Unit }.autoOpenRequested) }
        assertFalse(outcome.isCompleted)
        finish.complete(Unit)
        first.join()
        assertFalse(outcome.await())
        second.join()
    }

    @Test
    fun `failed restoration preserves original request for retry`() = runBlocking {
        val gate = StartupAutoOpenGate()
        assertFailsWith<IllegalStateException> { gate.restore(true) { error("failed") } }
        assertTrue(gate.restore(false) { "retry" }.autoOpenRequested)
    }
}
