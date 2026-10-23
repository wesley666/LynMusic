package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsShareTasksTest {
    @Test fun lateSchedulingCannotReplaceOrCancelANewerTask() = runTest {
        val tasks = LyricsShareTasks(PlayerTaskSession(1, "track"), backgroundScope)
        var latestCancelled = false
        var obsoleteStarted = false
        tasks.preview.launch(3) {
            try { awaitCancellation() } finally { latestCancelled = true }
        }
        runCurrent()
        tasks.preview.launch(1) { obsoleteStarted = true }
        tasks.preview.cancel(2)
        runCurrent()
        assertFalse(obsoleteStarted)
        assertFalse(latestCancelled)
        tasks.cancel()
        runCurrent()
        assertTrue(latestCancelled)
    }

    @Test fun completedOldTaskCannotLoseHandleToPendingReplacement() = runTest {
        val tasks = LyricsShareTasks(PlayerTaskSession(1, "track"), backgroundScope)
        val oldResult = CompletableDeferred<Unit>()
        var replacementCancelled = false
        tasks.preview.launch(1) { withContext(NonCancellable) { oldResult.await() } }
        runCurrent()
        tasks.preview.launch(2) {
            try { awaitCancellation() } finally { replacementCancelled = true }
        }
        runCurrent()
        oldResult.complete(Unit)
        runCurrent()
        tasks.preview.cancel(3)
        runCurrent()
        assertTrue(replacementCancelled)
        tasks.cancel()
    }

    @Test fun sessionCancellationStopsAllThreeKindsOfWorkAndRejectsLateStarts() = runTest {
        val tasks = LyricsShareTasks(PlayerTaskSession(1, "track"), backgroundScope)
        val cancelled = mutableSetOf<String>()
        for ((name, slot) in listOf("init" to tasks.initialization, "fonts" to tasks.fonts, "preview" to tasks.preview)) {
            slot.launch(1) { try { awaitCancellation() } finally { cancelled += name } }
        }
        runCurrent()
        tasks.cancel()
        runCurrent()
        assertEquals(setOf("init", "fonts", "preview"), cancelled)
        var lateStarted = false
        tasks.fonts.launch(2) { lateStarted = true }
        runCurrent()
        assertFalse(lateStarted)
    }
}
