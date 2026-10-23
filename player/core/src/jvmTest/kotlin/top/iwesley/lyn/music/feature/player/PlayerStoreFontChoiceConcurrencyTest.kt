package top.iwesley.lyn.music.feature.player

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.core.mvi.BaseStore

class PlayerStoreFontChoiceConcurrencyTest {
    @Test fun oldPreferenceFailureCannotOverwriteNewChoiceDuringCasRetry() = raceFailure(false)
    @Test fun oldPreferenceFailureCannotReportIntoReopenedShareDuringCasRetry() = raceFailure(true)

    private fun raceFailure(reopen: Boolean) = runBlocking {
        val worker = AtomicReference<Thread>()
        val dispatcher = Executors.newSingleThreadExecutor { task ->
            Thread(task, "font-choice-race-worker").also(worker::set)
        }.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val started = CompletableDeferred<Unit>()
            val result = CompletableDeferred<Unit>()
            val preferences = object : LyricsShareFontPreferencesStore {
                override val selectedLyricsShareFontKey = MutableStateFlow<String?>("Serif")
                override suspend fun setSelectedLyricsShareFontKey(value: String?) {
                    if (value == "Another font") {
                        started.complete(Unit)
                        result.await()
                        error("Controlled preference failure")
                    }
                    selectedLyricsShareFontKey.value = value
                }
            }
            val track = Track("original", "source", "Original", mediaLocator = "", relativePath = "")
            val store = PlayerStore(
                CommitPlaybackRepository(PlaybackSnapshot(queue = listOf(track), currentIndex = 0)),
                CopyTestLyricsRepository(), scope, lyricsSharePlatformService = CopyTestShareService(),
                lyricsShareFontPreferencesStore = preferences,
            )
            withTimeout(5_000) { store.state.first { it.lyrics != null } }
            store.dispatch(PlayerIntent.BeginLyricsShare)
            withTimeout(5_000) { store.state.first { it.hasFreshSharePreview } }
            store.dispatch(PlayerIntent.RequestLyricsShareFonts)
            withTimeout(5_000) { store.state.first { it.availableLyricsShareFonts.isNotEmpty() } }
            store.dispatch(PlayerIntent.LyricsShareFontChanged("Another font"))
            withTimeout(5_000) { started.await() }
            // Let the edit's preview publish before freezing the preference failure's own CAS.
            withTimeout(5_000) { store.state.first { it.hasFreshSharePreview } }
            val oldSession = store.state.value.lyricsShareSession
            val field = BaseStore::class.java.getDeclaredField("mutableState").apply { isAccessible = true }
            synchronized(field.get(store)) {
                result.complete(Unit)
                val deadline = System.nanoTime() + 5_000_000_000L
                while (true) {
                    val thread = worker.get()
                    val frames = thread.stackTrace
                    if (thread.state == Thread.State.BLOCKED &&
                        frames.any { it.className.endsWith("StateFlowImpl") && it.methodName == "updateState" } &&
                        frames.any { it.className.endsWith("PlayerLyricsShare") && it.methodName.contains("persistFontChoice") }
                    ) break
                    check(System.nanoTime() < deadline) { "Worker did not reach preference failure CAS" }
                    Thread.sleep(1)
                }
                if (reopen) {
                    store.dispatch(PlayerIntent.EndLyricsShare)
                    store.dispatch(PlayerIntent.BeginLyricsShare)
                } else store.dispatch(PlayerIntent.LyricsShareFontChanged("Serif"))
            }
            withContext(dispatcher) { }
            withTimeout(5_000) { store.state.first { it.hasFreshSharePreview } }
            assertNull(store.state.value.shareMessage)
            assertTrue(store.state.value.operationNotices.isEmpty())
            if (reopen) {
                assertNotEquals(oldSession, store.state.value.lyricsShareSession)
                assertEquals("Another font", store.state.value.selectedLyricsShareFontKey)
            } else {
                assertEquals(oldSession, store.state.value.lyricsShareSession)
                assertEquals("Serif", store.state.value.selectedLyricsShareFontKey)
                assertEquals("Serif", preferences.selectedLyricsShareFontKey.value)
            }
            Unit
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            dispatcher.close()
        }
    }
}
