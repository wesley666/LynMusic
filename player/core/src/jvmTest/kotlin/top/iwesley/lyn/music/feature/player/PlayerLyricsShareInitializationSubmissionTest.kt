package top.iwesley.lyn.music.feature.player

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*

/** Publishing a ready draft does not let initialization retry subsequent UI work. */
class PlayerLyricsShareInitializationSubmissionTest {
    @Test fun initializationPreservesNewSelectionFailure() = raceInitialization(Edit.Selection)
    @Test fun initializationPreservesNewTemplateFailure() = raceInitialization(Edit.Template)
    @Test fun initializationPreservesReselectedContentFailure() = raceInitialization(Edit.Reselect)
    @Test fun initializationPreservesUserRetryFailure() = raceInitialization(Edit.Retry)
    @Test fun initializationDoesNotDuplicateSuccessfulPreview() = raceInitialization(Edit.Selection, fail = false)
    @Test fun initializationDoesNotRestoreClearedSelection() = raceInitialization(Edit.Clear, fail = false)
    @Test fun uninterruptedInitializationGeneratesOnePreview() = raceInitialization(Edit.None, fail = false)
    @Test fun initialPreviewFailureAllowsExplicitRetry() = raceInitialization(Edit.None)

    private enum class Edit { Selection, Template, Reselect, Retry, Clear, None }

    private fun raceInitialization(edit: Edit, fail: Boolean = true) = runBlocking {
        val dispatcher = Executors.newFixedThreadPool(3).asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val ready = CompletableDeferred<Unit>()
        val releaseInitialization = CountDownLatch(1)
        val freeze = AtomicBoolean(true)
        val renders = AtomicInteger()
        val allowRetry = AtomicBoolean(false)
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
            val sequence = AtomicLong()
            val service = object : LyricsSharePlatformService by CopyTestShareService() {
                override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> {
                    val count = renders.incrementAndGet()
                    if (count == 1 && fail) return Result.failure(IllegalStateException("Current preview failed"))
                    if (count > 1 && !allowRetry.get()) awaitCancellation()
                    return Result.success(model.lifecycleImage())
                }
            }
            val share = PlayerLyricsShare(
                scope, service, UnsupportedLyricsShareFontLibraryPlatformService,
                UnsupportedLyricsShareFontPreferencesStore, states,
                updateState = { transform ->
                    var pause = false
                    states.update { current ->
                        val next = transform(current)
                        if (current.isLyricsShareInitializing && !next.isLyricsShareInitializing &&
                            next.lyricsShareSession != null && freeze.compareAndSet(true, false)
                        ) pause = true
                        next
                    }
                    // The UI can act on the ready state while initialization is still returning
                    // from publication. Hold that boundary without blocking the UI or renderer.
                    if (pause) {
                        ready.complete(Unit)
                        check(releaseInitialization.await(5, TimeUnit.SECONDS)) { "Timed out releasing initialization" }
                    }
                },
                newSession = { PlayerTaskSession(sequence.incrementAndGet(), states.value.effectiveSnapshot.currentTrack?.id) },
                closeSearch = {}, refreshLyricsFonts = {}, nextOperationId = { sequence.incrementAndGet() },
            )
            share.dispatch(PlayerIntent.BeginLyricsShare)
            withTimeout(5_000) { ready.await() }
            assertFalse(states.value.isLyricsShareInitializing)
            assertEquals(0, renders.get())
            val initializationJobs = scope.coroutineContext.job.children.flatMap { it.children.toList() }.toList()
            assertTrue(initializationJobs.isNotEmpty())
            when (edit) {
                Edit.Selection -> share.dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
                Edit.Template -> share.dispatch(PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT))
                Edit.Reselect -> {
                    share.dispatch(PlayerIntent.ClearLyricsSelection)
                    share.dispatch(PlayerIntent.ToggleLyricsLineSelection(0))
                }
                Edit.Retry -> share.dispatch(PlayerIntent.BuildLyricsSharePreview)
                Edit.Clear -> share.dispatch(PlayerIntent.ClearLyricsSelection)
                Edit.None -> Unit
            }
            suspend fun awaitPreview(): PlayerState = withTimeout(5_000) {
                states.first { !it.isShareRendering && if (fail) it.sharePreviewError != null else it.hasFreshSharePreview }
            }
            val latest = if (edit == Edit.Clear || edit == Edit.None) states.value else awaitPreview()
            releaseInitialization.countDown()
            withTimeout(5_000) { initializationJobs.joinAll() }
            val completed = if (edit == Edit.None) awaitPreview() else states.value

            assertEquals(latest.lyricsShareSession, completed.lyricsShareSession)
            assertFalse(completed.isShareRendering)
            assertNull(completed.lyricsSharePreviewRequestId)
            assertEquals(latest.shareCardModel, completed.shareCardModel)
            assertEquals(latest.selectedLyricsLineIndices, completed.selectedLyricsLineIndices)
            assertEquals(if (edit == Edit.Clear) 0 else 1, renders.get(), "Initialization must not add a duplicate render")
            if (edit == Edit.None) {
                if (fail) assertNotNull(completed.sharePreviewError) else {
                    assertNull(completed.sharePreviewError)
                    assertContentEquals(requireNotNull(latest.lyricsShareModel()).lifecycleImage(), completed.sharePreviewBytes)
                }
            } else {
                assertEquals(latest.sharePreviewError, completed.sharePreviewError, "Initialization must preserve the newer error")
                assertEquals(latest.sharePreviewModel, completed.sharePreviewModel)
                assertContentEquals(latest.sharePreviewBytes, completed.sharePreviewBytes)
                if (edit != Edit.Clear) assertEquals(latest.lyricsSharePreviewRevision, completed.lyricsSharePreviewRevision)
            }
            if (fail) {
                allowRetry.set(true)
                share.dispatch(PlayerIntent.BuildLyricsSharePreview)
                val retried = withTimeout(5_000) { states.first { it.hasFreshSharePreview && !it.isShareRendering } }
                assertEquals(2, renders.get())
                assertEquals(latest.lyricsShareSession, retried.lyricsShareSession)
                assertEquals(latest.lyricsShareModel(), retried.sharePreviewModel)
                assertNull(retried.sharePreviewError)
                assertNull(retried.lyricsSharePreviewRequestId)
                assertContentEquals(requireNotNull(latest.lyricsShareModel()).lifecycleImage(), retried.sharePreviewBytes)
            }
        } finally {
            releaseInitialization.countDown()
            scope.coroutineContext.job.cancelAndJoin()
            dispatcher.close()
        }
    }
}
