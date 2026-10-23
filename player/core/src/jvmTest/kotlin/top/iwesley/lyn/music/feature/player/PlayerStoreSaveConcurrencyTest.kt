package top.iwesley.lyn.music.feature.player

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.core.mvi.BaseStore

class PlayerStoreSaveConcurrencyTest {
    @Test fun oldSuccessCannotFinishNewSaveOrReleaseItsDeduplication() = raceSave(failFirst = false, newSession = false)
    @Test fun oldFailureCannotFinishNewSaveOrReleaseItsDeduplication() = raceSave(failFirst = true, newSession = false)
    @Test fun oldSuccessCannotPublishIntoReopenedShareDuringCasRetry() = raceSave(failFirst = false, newSession = true)
    @Test fun oldFailureCannotPublishIntoReopenedShareDuringCasRetry() = raceSave(failFirst = true, newSession = true)

    private fun raceSave(failFirst: Boolean, newSession: Boolean) = runBlocking {
        val worker = AtomicReference<Thread>()
        val dispatcher = Executors.newSingleThreadExecutor { task ->
            Thread(task, "save-race-worker").also(worker::set)
        }.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val firstGate = CompletableDeferred<Unit>()
            val secondGate = CompletableDeferred<Unit>()
            val firstStarted = CompletableDeferred<Unit>()
            val secondStarted = CompletableDeferred<Unit>()
            val calls = AtomicInteger()
            val service = object : LyricsSharePlatformService by CopyTestShareService() {
                override suspend fun saveImage(pngBytes: ByteArray, suggestedName: String): Result<LyricsShareSaveResult> {
                    val first = calls.incrementAndGet() == 1
                    if (first) { firstStarted.complete(Unit); firstGate.await() }
                    else { secondStarted.complete(Unit); secondGate.await() }
                    return if (first && failFirst) Result.failure(IllegalStateException("old save failed"))
                    else Result.success(savedShareImage())
                }
            }
            val track = Track("original", "source", "Original", mediaLocator = "", relativePath = "")
            val store = PlayerStore(CommitPlaybackRepository(PlaybackSnapshot(queue = listOf(track), currentIndex = 0)),
                CopyTestLyricsRepository(), scope, lyricsSharePlatformService = service)
            withTimeout(5_000) { store.state.first { it.lyrics != null } }
            store.dispatch(PlayerIntent.BeginLyricsShare)
            withTimeout(5_000) { store.state.first { it.hasFreshSharePreview } }
            store.dispatch(PlayerIntent.SaveLyricsShareImage)
            withTimeout(5_000) { firstStarted.await() }
            val oldSession = store.state.value.lyricsShareSession
            val oldRequest = store.state.value.lyricsShareSaveRequestId
            val field = BaseStore::class.java.getDeclaredField("mutableState").apply { isAccessible = true }
            val expected = synchronized(field.get(store)) {
                firstGate.complete(Unit)
                val deadline = System.nanoTime() + 5_000_000_000L
                while (true) {
                    val thread = worker.get()
                    val frames = thread.stackTrace
                    if (thread.state == Thread.State.BLOCKED &&
                        frames.any { it.className.endsWith("StateFlowImpl") && it.methodName == "updateState" } &&
                        frames.any { it.className.endsWith("PlayerLyricsShare") && it.methodName.contains("completeImageSave") }
                    ) break
                    check(System.nanoTime() < deadline) { "Worker did not reach save completion CAS" }
                    Thread.sleep(1)
                }
                if (newSession) {
                    store.dispatch(PlayerIntent.EndLyricsShare)
                    store.dispatch(PlayerIntent.BeginLyricsShare)
                } else {
                    store.dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
                    store.dispatch(PlayerIntent.SaveLyricsShareImage)
                }
                store.state.value.let { it.lyricsShareSession to it.lyricsShareSaveRequestId }
            }
            if (newSession) {
                withTimeout(5_000) { store.state.first { it.hasFreshSharePreview && it.operationNotices.isNotEmpty() } }
                assertNotEquals(oldSession, expected.first)
                assertFalse(store.state.value.isShareSaving)
                assertNull(store.state.value.lyricsShareSaveRequestId)
            } else {
                withTimeout(5_000) { secondStarted.await() }
                assertNotEquals(oldRequest, assertNotNull(expected.second))
                assertTrue(store.state.value.isShareSaving)
                assertEquals(expected.second, store.state.value.lyricsShareSaveRequestId)
                // The old worker's finally ran before the second save started. It must not clear
                // the second request's deduplication record, or this click would enqueue a third save.
                store.dispatch(PlayerIntent.SaveLyricsShareImage)
                assertEquals(expected.second, store.state.value.lyricsShareSaveRequestId)
            }
            assertEquals(expected.first, store.state.value.lyricsShareSession)
            assertNull(store.state.value.shareMessage)
            assertEquals(1, store.state.value.operationNotices.size)
            if (!newSession) {
                secondGate.complete(Unit)
                withTimeout(5_000) { store.state.first { !it.isShareSaving } }
                withContext(dispatcher) { }
                assertEquals(2, calls.get())
                assertNull(store.state.value.lyricsShareSaveRequestId)
                assertEquals(savedShareImage().message, store.state.value.shareMessage)
            }
            Unit
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
            dispatcher.close()
        }
    }
}
