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

/** An initialization that loses its session must not schedule work for a newer one. */
class PlayerLyricsShareInitializationConcurrencyTest {
    @Test fun initializationBeforeCasCannotRetryReopenedSession() = raceInitialization(afterCas = false, newTrack = false)
    @Test fun initializationBeforeCasCannotRetryNewTrackSession() = raceInitialization(afterCas = false, newTrack = true)
    @Test fun initializationAfterCasCannotRetryReopenedSession() = raceInitialization(afterCas = true, newTrack = false)
    @Test fun initializationAfterCasCannotRetryNewTrackSession() = raceInitialization(afterCas = true, newTrack = true)

    private fun raceInitialization(afterCas: Boolean, newTrack: Boolean) = runBlocking {
        val dispatcher = Executors.newFixedThreadPool(3).asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val initializationBlocked = CompletableDeferred<Unit>()
        val releaseInitialization = CountDownLatch(1)
        val freeze = AtomicBoolean(true)
        val renders = AtomicInteger()
        val retryResult = CompletableDeferred<Unit>()
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
                    if (renders.incrementAndGet() == 1) return Result.failure(IllegalStateException("New session failed"))
                    retryResult.await()
                    return Result.success(model.lifecycleImage())
                }
            }
            fun pauseInitialization() {
                initializationBlocked.complete(Unit)
                check(releaseInitialization.await(5, TimeUnit.SECONDS)) { "Timed out releasing old initialization" }
            }
            val share = PlayerLyricsShare(
                scope, service, UnsupportedLyricsShareFontLibraryPlatformService,
                UnsupportedLyricsShareFontPreferencesStore, states,
                updateState = { transform ->
                    var pauseAfterCas = false
                    states.update { current ->
                        val next = transform(current)
                        if (current.isLyricsShareInitializing && !next.isLyricsShareInitializing &&
                            next.lyricsShareSession != null && freeze.compareAndSet(true, false)
                        ) {
                            if (afterCas) pauseAfterCas = true else pauseInitialization()
                        }
                        next
                    }
                    if (pauseAfterCas) pauseInitialization()
                },
                newSession = { PlayerTaskSession(sequence.incrementAndGet(), states.value.effectiveSnapshot.currentTrack?.id) },
                closeSearch = {}, refreshLyricsFonts = {}, nextOperationId = { sequence.incrementAndGet() },
            )
            share.dispatch(PlayerIntent.BeginLyricsShare)
            withTimeout(5_000) { initializationBlocked.await() }
            val oldSession = states.value.lyricsShareSession
            val oldJobs = scope.coroutineContext.job.children.flatMap { it.children.toList() }.toList()
            share.dispatch(PlayerIntent.EndLyricsShare)
            if (newTrack) states.update {
                it.copy(snapshot = it.snapshot.copy(queue = listOf(track.copy(id = "next", title = "Next"))))
            }
            share.dispatch(PlayerIntent.BeginLyricsShare)
            val latest = withTimeout(5_000) {
                states.first { it.sharePreviewError != null && !it.isShareRendering }
            }
            assertNotEquals(oldSession, latest.lyricsShareSession)
            assertFalse(latest.isLyricsShareInitializing)
            assertEquals(if (newTrack) "next" else track.id, latest.lyricsShareSession?.trackId)

            // The old initialization either loses its ready-state CAS or resumes after publishing
            // it. In both cases its cancelled tail must leave the newer failed preview untouched.
            releaseInitialization.countDown()
            withTimeout(5_000) { oldJobs.joinAll() }
            val after = states.value
            assertEquals(latest.lyricsShareSession, after.lyricsShareSession)
            assertEquals(latest.sharePreviewError, after.sharePreviewError, "Old initialization must preserve the newer error")
            assertFalse(after.isShareRendering, "Old initialization must not retry the newer failed preview")
            assertNull(after.lyricsSharePreviewRequestId)
            assertEquals(latest.lyricsSharePreviewRevision, after.lyricsSharePreviewRevision)
            assertEquals(latest.shareCardModel, after.shareCardModel)
            assertEquals(latest.selectedLyricsLineIndices, after.selectedLyricsLineIndices)
            assertEquals(1, renders.get(), "Only the new session's initial preview should have rendered")

            // A user-requested retry still renders the current session's content successfully.
            share.dispatch(PlayerIntent.BuildLyricsSharePreview)
            assertTrue(states.value.isShareRendering)
            assertTrue(states.value.lyricsSharePreviewRevision > latest.lyricsSharePreviewRevision)
            retryResult.complete(Unit)
            val retried = withTimeout(5_000) {
                states.first { renders.get() == 2 && it.hasFreshSharePreview && !it.isShareRendering }
            }
            assertEquals(latest.lyricsShareSession, retried.lyricsShareSession)
            assertNull(retried.sharePreviewError)
            assertNull(retried.lyricsSharePreviewRequestId)
            assertEquals(latest.shareCardModel, retried.sharePreviewModel)
            assertContentEquals(requireNotNull(latest.shareCardModel).lifecycleImage(), retried.sharePreviewBytes)
        } finally {
            releaseInitialization.countDown()
            scope.coroutineContext.job.cancelAndJoin()
            dispatcher.close()
        }
    }
}
