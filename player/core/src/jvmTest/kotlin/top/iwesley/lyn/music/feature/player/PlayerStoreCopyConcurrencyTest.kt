package top.iwesley.lyn.music.feature.player

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.core.mvi.BaseStore

class PlayerStoreCopyConcurrencyTest {
    @Test fun oldCompletionCannotClearNewCopyAfterCasRetries() = raceCopy(failFirst = false, newSession = false)
    @Test fun oldFailureCannotReplaceNewCopyFeedbackAfterCasRetries() = raceCopy(failFirst = true, newSession = false)
    @Test fun oldCompletionCannotPublishIntoReopenedShareAfterCasRetries() = raceCopy(failFirst = false, newSession = true)
    @Test fun oldFailureCannotPublishIntoReopenedShareAfterCasRetries() = raceCopy(failFirst = true, newSession = true)

    private fun raceCopy(failFirst: Boolean, newSession: Boolean) = runBlocking {
        val worker = AtomicReference<Thread>()
        val dispatcher = Executors.newSingleThreadExecutor { task ->
            Thread(task, "clipboard-race-worker").also(worker::set)
        }.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val firstGate = CompletableDeferred<Unit>()
            val secondGate = CompletableDeferred<Unit>()
            val secondStarted = CompletableDeferred<Unit>()
            val share = CopyTestShareService().apply {
                copyGates += firstGate
                copyGates += secondGate
                if (failFirst) failedCopies += 0
            }
            val service = object : LyricsSharePlatformService by share {
                override suspend fun copyText(text: String): Result<Unit> {
                    secondStarted.complete(Unit)
                    return share.copyText(text)
                }
            }
            val track = Track("original", "source", "Original", mediaLocator = "", relativePath = "")
            val store = PlayerStore(
                CommitPlaybackRepository(PlaybackSnapshot(queue = listOf(track), currentIndex = 0)),
                CopyTestLyricsRepository(), scope, lyricsSharePlatformService = service,
            )
            withTimeout(5_000) { store.state.first { it.lyrics != null } }
            store.dispatch(PlayerIntent.BeginLyricsShare)
            withTimeout(5_000) { store.state.first { it.hasFreshSharePreview } }
            store.dispatch(PlayerIntent.CopyLyricsShareImage)
            val oldSession = store.state.value.lyricsShareSession
            val oldRequest = store.state.value.lyricsShareCopyRequestId
            // Hold the JVM StateFlow monitor to stop the completion after its transform but before
            // CAS. Dispatch on the lock-owning thread can reenter it and invalidate that transform.
            val field = BaseStore::class.java.getDeclaredField("mutableState").apply { isAccessible = true }
            val expected = synchronized(field.get(store)) {
                firstGate.complete(Unit)
                val deadline = System.nanoTime() + 5_000_000_000L
                while (true) {
                    val thread = worker.get()
                    val frames = thread.stackTrace
                    if (thread.state == Thread.State.BLOCKED &&
                        frames.any { it.className.endsWith("StateFlowImpl") && it.methodName == "updateState" } &&
                        frames.any { it.className.endsWith("PlayerLyricsShare") && it.methodName.contains("completeLyricsCopy") }
                    ) break
                    check(System.nanoTime() < deadline) { "Worker did not reach clipboard completion CAS" }
                    Thread.sleep(1)
                }
                if (newSession) {
                    store.dispatch(PlayerIntent.EndLyricsShare)
                    store.dispatch(PlayerIntent.BeginLyricsShare)
                } else {
                    store.dispatch(PlayerIntent.CopyLyricsShareText)
                }
                store.state.value.let { it.lyricsShareSession to it.lyricsShareCopyRequestId }
            }
            if (newSession) {
                withTimeout(5_000) { store.state.first { it.hasFreshSharePreview && it.operationNotices.isNotEmpty() } }
                assertNotEquals(oldSession, expected.first)
                assertFalse(store.state.value.isShareCopying)
                assertNull(store.state.value.lyricsShareCopyRequestId)
            } else {
                withTimeout(5_000) { secondStarted.await() }
                assertNotEquals(oldRequest, assertNotNull(expected.second))
                assertEquals(expected.second, store.state.value.lyricsShareCopyRequestId)
                assertTrue(store.state.value.isShareCopying)
            }
            assertEquals(expected.first, store.state.value.lyricsShareSession)
            assertNull(store.state.value.shareMessage)
            assertEquals(1, store.state.value.operationNotices.size)
            if (!newSession) {
                secondGate.complete(Unit)
                withTimeout(5_000) { store.state.first { !it.isShareCopying } }
                assertNull(store.state.value.lyricsShareCopyRequestId)
                assertNotNull(store.state.value.shareMessage)
                assertEquals("text:original first", share.writes.last())
                assertEquals(if (failFirst) 1 else 2, share.writes.size)
            }
            Unit
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            dispatcher.close()
        }
    }
}
