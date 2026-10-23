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

/** A pending submission must not reclaim state after newer previews finish and edits round-trip. */
class PlayerLyricsSharePreviewSchedulingConcurrencyTest {
    @Test fun selectionRoundTripPreservesNewPreviewSuccess() = raceSubmission(template = false, failLatest = false)
    @Test fun selectionRoundTripPreservesNewPreviewFailureAndAllowsRetry() = raceSubmission(template = false, failLatest = true)
    @Test fun templateRoundTripPreservesNewPreviewSuccess() = raceSubmission(template = true, failLatest = false)
    @Test fun templateRoundTripPreservesNewPreviewFailureAndAllowsRetry() = raceSubmission(template = true, failLatest = true)

    private fun raceSubmission(template: Boolean, failLatest: Boolean) = runBlocking {
        val dispatcher = Executors.newFixedThreadPool(3).asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val firstSubmissionAtCas = CompletableDeferred<Unit>()
        val releaseSubmission = CountDownLatch(1)
        val freeze = AtomicBoolean(true)
        val renders = AtomicInteger()
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
                    return if (renders.incrementAndGet() == 2 && failLatest) {
                        Result.failure(IllegalStateException("Latest preview failed"))
                    } else Result.success(model.lifecycleImage())
                }
            }
            val share = PlayerLyricsShare(
                scope, service, UnsupportedLyricsShareFontLibraryPlatformService,
                UnsupportedLyricsShareFontPreferencesStore, states,
                updateState = { transform ->
                    states.update { current ->
                        val next = transform(current)
                        if (next.isShareRendering && next.lyricsSharePreviewRequestId != null &&
                            freeze.compareAndSet(true, false)
                        ) {
                            firstSubmissionAtCas.complete(Unit)
                            check(releaseSubmission.await(5, TimeUnit.SECONDS)) { "Timed out releasing preview submission CAS" }
                        }
                        next
                    }
                },
                newSession = { PlayerTaskSession(sequence.incrementAndGet(), track.id) },
                closeSearch = {}, refreshLyricsFonts = {}, nextOperationId = { sequence.incrementAndGet() },
            )
            share.dispatch(PlayerIntent.BeginLyricsShare)
            withTimeout(5_000) { firstSubmissionAtCas.await() }
            share.dispatch(if (template) PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT)
                else PlayerIntent.ToggleLyricsLineSelection(1))
            withTimeout(5_000) { states.first { renders.get() == 1 && it.hasFreshSharePreview && !it.isShareRendering } }
            share.dispatch(if (template) PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.NOTE)
                else PlayerIntent.ToggleLyricsLineSelection(1))
            val latest = withTimeout(5_000) {
                states.first {
                    renders.get() == 2 && !it.isShareRendering &&
                        (if (failLatest) it.sharePreviewError != null else it.hasFreshSharePreview)
                }
            }
            // Both newer previews are done. The content and nullable active ID now match the
            // frozen submission's original snapshot, forcing its update to retry the CAS.
            val pendingSessionTasks = scope.coroutineContext.job.children.flatMap { it.children.toList() }.toList()
            releaseSubmission.countDown()
            withTimeout(5_000) { pendingSessionTasks.joinAll() }

            val completed = states.value
            assertEquals(latest.sharePreviewError, completed.sharePreviewError, "An obsolete submission must not clear the newer error")
            assertFalse(completed.isShareRendering, "No render remains, so an obsolete submission must not reclaim busy state")
            assertNull(completed.lyricsSharePreviewRequestId)
            assertEquals(latest.shareCardModel, completed.shareCardModel)
            assertEquals(latest.sharePreviewModel, completed.sharePreviewModel)
            assertEquals(latest.selectedLyricsLineIndices, completed.selectedLyricsLineIndices)
            assertContentEquals(latest.sharePreviewBytes, completed.sharePreviewBytes)
            assertEquals(2, renders.get(), "An obsolete submission must not start another render")

            if (failLatest) {
                assertNotNull(completed.sharePreviewError)
                share.dispatch(PlayerIntent.BuildLyricsSharePreview)
                val retried = withTimeout(5_000) {
                    states.first { renders.get() == 3 && it.hasFreshSharePreview && !it.isShareRendering }
                }
                assertNull(retried.sharePreviewError)
                assertNull(retried.lyricsSharePreviewRequestId)
                assertEquals(latest.shareCardModel, retried.sharePreviewModel)
                assertContentEquals(requireNotNull(latest.shareCardModel).lifecycleImage(), retried.sharePreviewBytes)
            } else {
                assertTrue(completed.hasFreshSharePreview)
            }
        } finally {
            releaseSubmission.countDown()
            scope.coroutineContext.job.cancelAndJoin()
            dispatcher.close()
        }
    }
}
