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

/** Font results already accepted by the session must outlive cancellation of the font lookup. */
class PlayerLyricsShareFontPreviewCancellationTest {
    @Test fun fallbackAcceptedBeforeFontCancellationStillBuildsPreview() = cancelAfterPublication("Fallback")
    @Test fun normalizedFontAcceptedBeforeCancellationStillBuildsPreview() = cancelAfterPublication("serif")
    @Test fun fallbackPreviewFailureSurvivesCancellationAndAllowsRetry() = cancelAfterPublication("Fallback", failPreview = true)
    @Test fun normalizedFontPreviewFailureSurvivesCancellationAndAllowsRetry() = cancelAfterPublication("serif", failPreview = true)
    @Test fun closingAfterFontPublicationDoesNotRenderForClosedSession() = cancelAfterPublication("Fallback", finish = Finish.Close)
    @Test fun reopeningAfterFontPublicationPreservesNewPreviewFailure() =
        cancelAfterPublication("Fallback", finish = Finish.Reopen, failPreview = true)
    @Test fun changingTrackAfterFontPublicationPreservesNewPreviewFailure() =
        cancelAfterPublication("Fallback", finish = Finish.NewTrack, failPreview = true)

    private enum class Finish { Invalidate, Close, Reopen, NewTrack }

    private fun cancelAfterPublication(
        fontKey: String, finish: Finish = Finish.Invalidate, failPreview: Boolean = false,
    ) = runBlocking {
        // Hold the font task after publication so the content observer cannot process it yet.
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val published = CompletableDeferred<Unit>()
        val releaseFontTask = CountDownLatch(1)
        val freeze = AtomicBoolean(true)
        val renders = AtomicInteger()
        try {
            val track = Track("track", "source", "Title", mediaLocator = "", relativePath = "")
            val states = MutableStateFlow(PlayerState(
                snapshot = PlaybackSnapshot(queue = listOf(track), currentIndex = 0),
                lyrics = LyricsDocument(listOf(LyricsLine(0, "Line")), sourceId = "source", rawPayload = "original"),
                highlightedLineIndex = 0,
            ))
            val sequence = AtomicLong()
            val service = object : LyricsSharePlatformService by CopyTestShareService() {
                override suspend fun listAvailableFontFamilies() = Result.success(listOf(LyricsShareFontOption(fontKey, fontKey)))
                override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> {
                    if (renders.incrementAndGet() == 2 && failPreview) {
                        return Result.failure(IllegalStateException("Current preview failed"))
                    }
                    return Result.success(model.lifecycleImage())
                }
            }
            val share = PlayerLyricsShare(
                scope, service, UnsupportedLyricsShareFontLibraryPlatformService,
                LifecycleSharePreferences(), states,
                updateState = { transform ->
                    var pause = false
                    states.update { current ->
                        val next = transform(current)
                        if (current.isLyricsShareFontsLoading && !next.isLyricsShareFontsLoading &&
                            next.availableLyricsShareFonts.isNotEmpty() && freeze.compareAndSet(true, false)
                        ) pause = true
                        next
                    }
                    if (pause) {
                        published.complete(Unit)
                        check(releaseFontTask.await(5, TimeUnit.SECONDS)) { "Timed out releasing font completion" }
                    }
                },
                newSession = { PlayerTaskSession(sequence.incrementAndGet(), states.value.effectiveSnapshot.currentTrack?.id) },
                closeSearch = {}, refreshLyricsFonts = {}, nextOperationId = { sequence.incrementAndGet() },
            )
            share.dispatch(PlayerIntent.BeginLyricsShare)
            val opened = withTimeout(5_000) { states.first { it.hasFreshSharePreview } }
            share.dispatch(PlayerIntent.RequestLyricsShareFonts)
            withTimeout(5_000) { published.await() }
            assertEquals(fontKey, states.value.selectedLyricsShareFontKey)
            assertFalse(states.value.hasFreshSharePreview)
            val fontJobs = scope.coroutineContext.job.children.flatMap { it.children.toList() }.toList()
            assertTrue(fontJobs.isNotEmpty())
            share.dispatch(PlayerIntent.InvalidateLyricsShareFontCache)
            if (finish != Finish.Invalidate) share.close()
            if (finish == Finish.NewTrack) states.update {
                it.copy(snapshot = it.snapshot.copy(queue = listOf(track.copy(id = "next", title = "Next"))))
            }
            if (finish == Finish.Reopen || finish == Finish.NewTrack) share.dispatch(PlayerIntent.BeginLyricsShare)
            releaseFontTask.countDown()
            withTimeout(5_000) { fontJobs.joinAll() }
            assertTrue(fontJobs.all { it.isCancelled }, "Font lookup cancellation must still take effect")
            if (finish == Finish.Reopen || finish == Finish.NewTrack) {
                withTimeout(5_000) { states.first { it.sharePreviewError != null && !it.isShareRendering } }
            }
            // Drain the observer and its immediate renderer on the single worker.
            repeat(2) { withContext(dispatcher) { yield() } }

            val completed = states.value
            if (finish == Finish.Close) {
                assertNull(completed.lyricsShareSession)
                assertNull(completed.sharePreviewBytes)
                assertNull(completed.sharePreviewError)
                assertFalse(completed.isShareRendering)
                assertEquals(1, renders.get(), "The closed session must not render its accepted font change")
                return@runBlocking
            }
            if (finish == Finish.Invalidate) assertEquals(opened.lyricsShareSession, completed.lyricsShareSession)
            else assertNotEquals(opened.lyricsShareSession, completed.lyricsShareSession)
            assertEquals(if (finish == Finish.NewTrack) "next" else track.id, completed.lyricsShareSession?.trackId)
            assertTrue(completed.availableLyricsShareFonts.isEmpty())
            assertFalse(completed.isLyricsShareFontsLoading)
            assertFalse(completed.isShareRendering)
            assertNull(completed.lyricsSharePreviewRequestId)
            assertEquals(2, renders.get(), "Only the opening and current preview should render; failure must not auto-retry")
            if (failPreview) {
                assertNotNull(completed.sharePreviewError)
                assertFalse(completed.hasFreshSharePreview)
                share.dispatch(PlayerIntent.BuildLyricsSharePreview)
                val retried = withTimeout(5_000) {
                    states.first { renders.get() == 3 && it.hasFreshSharePreview && !it.isShareRendering }
                }
                assertEquals(completed.lyricsShareSession, retried.lyricsShareSession)
                assertEquals(fontKey, retried.sharePreviewFontKey)
                assertNull(retried.sharePreviewError)
                assertContentEquals(requireNotNull(retried.lyricsShareModel()).lifecycleImage(), retried.sharePreviewBytes)
            } else {
                assertNull(completed.sharePreviewError)
                assertTrue(completed.hasFreshSharePreview, "An accepted font change must render despite font task cancellation")
                assertEquals(fontKey, completed.sharePreviewFontKey)
                assertContentEquals(requireNotNull(completed.lyricsShareModel()).lifecycleImage(), completed.sharePreviewBytes)
            }
        } finally {
            releaseFontTask.countDown()
            scope.coroutineContext.job.cancelAndJoin()
            dispatcher.close()
        }
    }
}
