package top.iwesley.lyn.music.feature.player

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*

/** An observed font change must not become a fresh request after a newer preview has failed. */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class PlayerLyricsShareObservationConcurrencyTest {
    @Test fun cancelledFallbackObservationPreservesNewSelectionFailure() = raceObservation("Fallback")
    @Test fun cancelledFallbackObservationPreservesNewTemplateFailure() = raceObservation("Fallback", template = true)
    @Test fun cancelledNormalizedFontObservationPreservesNewSelectionFailure() = raceObservation("serif")
    @Test fun cancelledNormalizedFontObservationPreservesNewTemplateFailure() = raceObservation("serif", template = true)
    @Test fun obsoleteObservationDoesNotDuplicateSuccessfulPreview() = raceObservation("Fallback", result = ResultMode.Success)
    @Test fun obsoleteObservationDoesNotReplaceRunningPreview() = raceObservation("Fallback", result = ResultMode.Pending)
    @Test fun obsoleteObservationCannotRetryReopenedSession() = raceObservation("Fallback", finish = Finish.Reopen)
    @Test fun obsoleteObservationCannotRetryNewTrackSession() = raceObservation("Fallback", finish = Finish.NewTrack)

    private enum class ResultMode { Failure, Success, Pending }
    private enum class Finish { Edit, Reopen, NewTrack }

    private fun raceObservation(
        fontKey: String, template: Boolean = false,
        result: ResultMode = ResultMode.Failure, finish: Finish = Finish.Edit,
    ) = runBlocking {
        val dispatcher = Executors.newFixedThreadPool(3).asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val observationCaptured = CompletableDeferred<Unit>()
        val observationCompleted = CompletableDeferred<Unit>()
        val fontPublished = CompletableDeferred<Unit>()
        val secondRenderStarted = CompletableDeferred<Unit>()
        val finishSecondRender = CompletableDeferred<Unit>()
        val releaseObservation = CountDownLatch(1)
        val releaseFont = CountDownLatch(1)
        val freezeObservation = AtomicBoolean(false)
        val freezeFont = AtomicBoolean(true)
        val inObserver = ThreadLocal<Boolean>()
        val pausedEmission = ThreadLocal<Boolean>()
        val fontJob = AtomicReference<Job>()
        val renders = AtomicInteger()
        val allowRetry = AtomicBoolean(false)
        val sequence = AtomicLong()
        try {
            val track = Track("track", "source", "Title", mediaLocator = "", relativePath = "")
            val states = MutableStateFlow(PlayerState(
                snapshot = PlaybackSnapshot(queue = listOf(track), currentIndex = 0),
                lyrics = LyricsDocument(
                    listOf(LyricsLine(0, "First"), LyricsLine(1000, "Second")),
                    sourceId = "source", rawPayload = "original",
                ),
                highlightedLineIndex = 0,
            ))
            val observed = object : StateFlow<PlayerState> by states {
                override val value: PlayerState get() {
                    val captured = states.value
                    if (inObserver.get() == true && captured.selectedLyricsShareFontKey == fontKey &&
                        captured.shareCardModel != captured.lyricsShareModel() &&
                        freezeObservation.compareAndSet(true, false)
                    ) {
                        // Preserve the observation, then allow a newer UI preview to complete before
                        // the observer acts on it. No private method names or scheduler timing needed.
                        pausedEmission.set(true)
                        observationCaptured.complete(Unit)
                        check(releaseObservation.await(5, TimeUnit.SECONDS)) { "Timed out releasing observation" }
                    }
                    return captured
                }

                override suspend fun collect(collector: FlowCollector<PlayerState>): Nothing =
                    states.collect(object : FlowCollector<PlayerState> {
                        override suspend fun emit(value: PlayerState) {
                            inObserver.set(true)
                            pausedEmission.set(false)
                            try {
                                collector.emit(value)
                            } finally {
                                if (pausedEmission.get() == true) observationCompleted.complete(Unit)
                                pausedEmission.remove()
                                inObserver.remove()
                            }
                        }
                    })
            }
            val service = object : LyricsSharePlatformService by CopyTestShareService() {
                override suspend fun listAvailableFontFamilies(): Result<List<LyricsShareFontOption>> {
                    fontJob.set(currentCoroutineContext().job)
                    return Result.success(listOf(LyricsShareFontOption(fontKey, fontKey)))
                }

                override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> {
                    val count = renders.incrementAndGet()
                    if (count == 2) {
                        secondRenderStarted.complete(Unit)
                        if (result == ResultMode.Pending) finishSecondRender.await()
                        if (result == ResultMode.Failure) return Result.failure(IllegalStateException("Newer preview failed"))
                    }
                    if (count >= 3 && !allowRetry.get()) awaitCancellation()
                    return Result.success(model.lifecycleImage())
                }
            }
            val share = PlayerLyricsShare(
                scope, service, UnsupportedLyricsShareFontLibraryPlatformService, LifecycleSharePreferences(), observed,
                updateState = { transform ->
                    var pause = false
                    states.update { current ->
                        val next = transform(current)
                        if (current.isLyricsShareFontsLoading && !next.isLyricsShareFontsLoading &&
                            next.availableLyricsShareFonts.isNotEmpty() && freezeFont.compareAndSet(true, false)
                        ) pause = true
                        next
                    }
                    if (pause) {
                        fontPublished.complete(Unit)
                        check(releaseFont.await(5, TimeUnit.SECONDS)) { "Timed out releasing font task" }
                    }
                },
                newSession = { PlayerTaskSession(sequence.incrementAndGet(), states.value.effectiveSnapshot.currentTrack?.id) },
                closeSearch = {}, refreshLyricsFonts = {}, nextOperationId = { sequence.incrementAndGet() },
            )
            share.dispatch(PlayerIntent.BeginLyricsShare)
            val opened = withTimeout(5_000) { states.first { it.hasFreshSharePreview } }
            freezeObservation.set(true)
            share.dispatch(PlayerIntent.RequestLyricsShareFonts)
            withTimeout(5_000) { fontPublished.await(); observationCaptured.await() }
            share.dispatch(PlayerIntent.InvalidateLyricsShareFontCache)
            if (finish == Finish.Edit) {
                share.dispatch(if (template) PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT)
                    else PlayerIntent.ToggleLyricsLineSelection(1))
            } else {
                share.close()
                if (finish == Finish.NewTrack) states.update {
                    it.copy(snapshot = it.snapshot.copy(queue = listOf(track.copy(id = "next", title = "Next"))))
                }
                share.dispatch(PlayerIntent.BeginLyricsShare)
            }
            withTimeout(5_000) { secondRenderStarted.await() }
            val latest = withTimeout(5_000) { states.first {
                when (result) {
                    ResultMode.Failure -> it.sharePreviewError != null && !it.isShareRendering
                    ResultMode.Success -> it.hasFreshSharePreview && !it.isShareRendering
                    ResultMode.Pending -> it.isShareRendering
                }
            } }
            releaseFont.countDown()
            withTimeout(5_000) { requireNotNull(fontJob.get()).join() }
            assertTrue(requireNotNull(fontJob.get()).isCancelled, "Font lookup cancellation must still take effect")
            releaseObservation.countDown()
            withTimeout(5_000) { observationCompleted.await() }

            val completed = states.value
            assertEquals(latest.sharePreviewError, completed.sharePreviewError, "An obsolete observation must not clear a newer error")
            assertEquals(latest.lyricsSharePreviewRevision, completed.lyricsSharePreviewRevision)
            assertEquals(latest.lyricsSharePreviewRequestId, completed.lyricsSharePreviewRequestId)
            assertEquals(latest.isShareRendering, completed.isShareRendering)
            assertEquals(latest.lyricsShareSession, completed.lyricsShareSession)
            assertEquals(latest.shareCardModel, completed.shareCardModel)
            assertEquals(latest.sharePreviewModel, completed.sharePreviewModel)
            assertEquals(latest.selectedLyricsLineIndices, completed.selectedLyricsLineIndices)
            assertContentEquals(latest.sharePreviewBytes, completed.sharePreviewBytes)
            assertEquals(2, renders.get(), "The old observation must not generate an extra preview")
            if (finish == Finish.Edit) assertEquals(opened.lyricsShareSession, completed.lyricsShareSession)
            else assertNotEquals(opened.lyricsShareSession, completed.lyricsShareSession)

            if (result == ResultMode.Failure) {
                allowRetry.set(true)
                share.dispatch(PlayerIntent.BuildLyricsSharePreview)
                val retried = withTimeout(5_000) { states.first { it.hasFreshSharePreview && !it.isShareRendering } }
                assertEquals(3, renders.get())
                assertEquals(latest.lyricsShareSession, retried.lyricsShareSession)
                assertEquals(latest.lyricsShareModel(), retried.sharePreviewModel)
                assertNull(retried.sharePreviewError)
                assertNull(retried.lyricsSharePreviewRequestId)
                assertContentEquals(requireNotNull(latest.lyricsShareModel()).lifecycleImage(), retried.sharePreviewBytes)
            } else if (result == ResultMode.Pending) {
                finishSecondRender.complete(Unit)
                val completedPreview = withTimeout(5_000) { states.first { it.hasFreshSharePreview && !it.isShareRendering } }
                assertEquals(2, renders.get())
                assertContentEquals(requireNotNull(latest.lyricsShareModel()).lifecycleImage(), completedPreview.sharePreviewBytes)
            }
        } finally {
            releaseFont.countDown()
            releaseObservation.countDown()
            finishSecondRender.complete(Unit)
            scope.coroutineContext.job.cancelAndJoin()
            dispatcher.close()
        }
    }
}
