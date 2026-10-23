package top.iwesley.lyn.music.feature.player

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.core.mvi.BaseStore
import top.iwesley.lyn.music.data.repository.*

class PlayerStoreApplicationConcurrencyTest {
    @Test fun newerSubmissionPreventsOldCompletionFromClosingSearch() =
        raceCompletion(Boundary.Completion, Interaction.Submit)

    @Test fun newerSubmissionPreventsOldFailureFromReplacingItsMessage() =
        raceCompletion(Boundary.Failure, Interaction.Submit)

    @Test fun newerSubmissionPreventsOldLyricsFromReplacingCurrentLyrics() =
        raceCompletion(Boundary.Lyrics, Interaction.Submit)

    @Test fun newSessionRemainsOpenWhenOldCompletionRetries() =
        raceCompletion(Boundary.Completion, Interaction.NewSession)

    @Test fun editingTheSameQueryStillInvalidatesOldCompletion() =
        raceCompletion(Boundary.Completion, Interaction.Edit)

    @Test fun restartingSearchInvalidatesOldCompletionBeforeLookupStarts() =
        raceCompletion(Boundary.Completion, Interaction.Search)

    private fun raceCompletion(boundary: Boundary, interaction: Interaction) = runBlocking {
        val worker = AtomicReference<Thread>()
        val dispatcher = Executors.newSingleThreadExecutor { task ->
            Thread(task, "application-race-worker").also(worker::set)
        }.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val repository = GatedLyricsRepository(boundary)
            val track = Track("original", "source", "Original", mediaLocator = "", relativePath = "")
            // An unchanged cover produces no snapshot emission that could accidentally mask the race.
            val playback = CommitPlaybackRepository(PlaybackSnapshot(
                queue = listOf(track), currentIndex = 0, metadataArtworkLocator = "cover-A",
            ))
            val store = PlayerStore(playback, repository, scope)
            withTimeout(5_000) { store.state.first { it.lyrics != null } }
            val candidate = LyricsSearchCandidate("source", "Source", document("first"), artworkLocator = "cover-A")
            val mode = if (boundary == Boundary.Lyrics) LyricsSearchApplyMode.FULL else LyricsSearchApplyMode.ARTWORK_ONLY
            store.dispatch(PlayerIntent.BeginManualLyricsSearch)
            store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate, mode))
            val expectedSession = atCompletionCas(store, worker, repository.firstGate, boundary) {
                if (interaction == Interaction.NewSession) {
                    store.dispatch(PlayerIntent.EndManualLyricsSearch)
                    store.dispatch(PlayerIntent.BeginManualLyricsSearch)
                }
                val session = assertNotNull(store.state.value.manualLyricsSearchSession)
                when (interaction) {
                    Interaction.Submit, Interaction.NewSession -> store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(
                        candidate.copy(document = document("second"), artworkLocator = "cover-B"), mode,
                    ))
                    Interaction.Edit -> store.dispatch(PlayerIntent.ManualLyricsTitleChanged("Original"))
                    Interaction.Search -> store.dispatch(PlayerIntent.SearchManualLyrics)
                }
                session
            }
            val hasNextApplication = interaction == Interaction.Submit || interaction == Interaction.NewSession
            withTimeout(5_000) {
                if (hasNextApplication) repository.secondStarted.await()
                else store.state.first { it.operationNotices.isNotEmpty() }
            }
            assertFalse(repository.secondGate.isCompleted)
            assertEquals(expectedSession, store.state.value.manualLyricsSearchSession,
                "The old completion must not close a search with a newer interaction")
            assertEquals("auto:original", store.state.value.lyrics?.rawPayload)
            assertNull(store.state.value.message, "An older failure must use operation feedback, not the active search")
            assertEquals(1, store.state.value.operationNotices.size)
            if (hasNextApplication) {
                assertNotNull(store.state.value.manualLyricsApplicationRequestId)
                repository.secondGate.complete(Unit)
                withTimeout(5_000) { store.state.first { it.manualLyricsSearchSession == null } }
                assertNull(store.state.value.manualLyricsApplicationRequestId)
                assertEquals(if (boundary == Boundary.Failure) 1 else 2, repository.commits.get())
                assertEquals("cover-B", playback.snapshot.value.currentDisplayArtworkLocator)
                if (boundary == Boundary.Lyrics) assertEquals("second", store.state.value.lyrics?.rawPayload)
            } else {
                assertNull(store.state.value.manualLyricsApplicationRequestId)
                assertEquals(1, repository.commits.get())
            }
            Unit
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            dispatcher.close()
        }
    }

    /**
     * Pause the worker after its update transform has run but before StateFlow's CAS. The test
     * thread owns the monitor, so synchronous dispatch can reenter it and publish the newer state.
     * This deliberately uses the JVM StateFlow implementation to exercise a real CAS retry without
     * introducing a scheduling hook into production code; a changed implementation fails by timeout.
     */
    private fun <T> atCompletionCas(
        store: PlayerStore,
        worker: AtomicReference<Thread>,
        gate: CompletableDeferred<Unit>,
        boundary: Boundary,
        interact: () -> T,
    ): T {
        val field = BaseStore::class.java.getDeclaredField("mutableState").apply { isAccessible = true }
        return synchronized(field.get(store)) {
            gate.complete(Unit)
            val deadline = System.nanoTime() + 5_000_000_000L
            while (true) {
                val thread = worker.get()
                val frames = thread.stackTrace
                if (thread.state == Thread.State.BLOCKED &&
                    frames.any { it.className.endsWith("StateFlowImpl") && it.methodName == "updateState" } &&
                    frames.any { it.className.endsWith(boundary.owner) && it.methodName.contains(boundary.method) }
                ) break
                check(System.nanoTime() < deadline) { "Worker did not reach the $boundary CAS boundary" }
                Thread.sleep(1)
            }
            interact()
        }
    }

    private enum class Interaction { Submit, NewSession, Edit, Search }
    private enum class Boundary(val owner: String, val method: String) {
        Completion("PlayerStore", "applySubmittedLyrics"),
        Failure("PlayerStore", "reportApplicationFailure"),
        Lyrics("PlayerLyricsSettings", "accept"),
    }

    private class GatedLyricsRepository(private val boundary: Boundary) : LyricsRepository {
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val commits = AtomicInteger()
        private val calls = AtomicInteger()
        override suspend fun getLyrics(track: Track) = ResolvedLyricsResult(document("auto:" + track.id))
        override suspend fun searchLyricsCandidates(track: Track, includeTrackProvidedCandidate: Boolean) = emptyList<LyricsSearchCandidate>()
        override suspend fun searchWorkflowSongCandidates(track: Track) = emptyList<WorkflowSongCandidate>()
        override suspend fun resolveWorkflowSongCandidate(track: Track, candidate: WorkflowSongCandidate): ResolvedLyricsResult = error("Not used")
        override suspend fun applyWorkflowSongCandidate(trackId: String, candidate: WorkflowSongCandidate, mode: LyricsSearchApplyMode): AppliedLyricsResult = error("Not used")
        override suspend fun applyLyricsCandidate(trackId: String, candidate: LyricsSearchCandidate, mode: LyricsSearchApplyMode): AppliedLyricsResult {
            if (calls.incrementAndGet() == 1) {
                firstGate.await()
                if (boundary == Boundary.Failure) error("First application failed")
            } else {
                secondStarted.complete(Unit)
                secondGate.await()
            }
            commits.incrementAndGet()
            return AppliedLyricsResult(candidate.document.takeUnless { mode == LyricsSearchApplyMode.ARTWORK_ONLY }, candidate.artworkLocator)
        }
    }

    private companion object {
        fun document(payload: String) = LyricsDocument(listOf(LyricsLine(0, "Line")), sourceId = "source", rawPayload = payload)
    }
}
