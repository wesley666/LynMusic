package top.iwesley.lyn.music.feature.player

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*

/** Freeze a result's transform before CAS while playback/lyrics publishes new content. */
class PlayerLyricsShareContentConcurrencyTest {
    @Test fun metadataChangeRejectsOldSuccessDuringCasRetry() = raceContent(lyricsChange = false, fail = false)
    @Test fun metadataChangeRejectsOldFailureDuringCasRetry() = raceContent(lyricsChange = false, fail = true)
    @Test fun lyricsChangeRejectsOldSuccessDuringCasRetry() = raceContent(lyricsChange = true, fail = false)
    @Test fun lyricsChangeRejectsOldFailureDuringCasRetry() = raceContent(lyricsChange = true, fail = true)

    @Test fun metadataChangeDuringUnchangedFontCompletionSchedulesNewPreview() =
        raceContent(lyricsChange = false, fail = false, fontCompletion = true)
    @Test fun lyricsChangeDuringUnchangedFontCompletionSchedulesNewPreview() =
        raceContent(lyricsChange = true, fail = false, fontCompletion = true)

    private fun raceContent(lyricsChange: Boolean, fail: Boolean, fontCompletion: Boolean = false) = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val resultAtCas = CompletableDeferred<Unit>()
        val releaseCas = CountDownLatch(1)
        val freeze = AtomicBoolean(false)
        val results = CopyOnWriteArrayList<PlayerState>()
        try {
            val track = Track("track", "source", "Title", mediaLocator = "", relativePath = "")
            val states = MutableStateFlow(PlayerState(snapshot = PlaybackSnapshot(queue = listOf(track), currentIndex = 0),
                lyrics = LyricsDocument(listOf(LyricsLine(0, "Original line")), sourceId = "source", rawPayload = "original"), highlightedLineIndex = 0))
            val started = CompletableDeferred<Unit>()
            val oldResult = CompletableDeferred<Result<ByteArray>>()
            val fontResult = CompletableDeferred<List<LyricsShareFontOption>>()
            val latestStarted = CompletableDeferred<LyricsShareCardModel>()
            val latestResult = CompletableDeferred<Unit>()
            var render = 0
            val service = object : LyricsSharePlatformService by CopyTestShareService() {
                override suspend fun listAvailableFontFamilies() = Result.success(fontResult.await())
                override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> = when (render++) {
                    0 -> Result.success(model.lifecycleImage())
                    1 -> { started.complete(Unit); oldResult.await() }
                    else -> { latestStarted.complete(model); latestResult.await(); Result.success(model.lifecycleImage()) }
                }
            }
            val sequence = AtomicLong()
            val share = PlayerLyricsShare(scope, service, UnsupportedLyricsShareFontLibraryPlatformService,
                LifecycleSharePreferences(), states,
                updateState = { transform ->
                    states.update { current ->
                        val next = transform(current)
                        val isOldResult = next.sharePreviewBytes?.decodeToString() == "obsolete" || next.sharePreviewError != null
                        val completing = if (fontCompletion) current.availableLyricsShareFonts.isEmpty() &&
                            next.availableLyricsShareFonts.isNotEmpty() else isOldResult
                        if (completing && freeze.compareAndSet(true, false)) {
                            resultAtCas.complete(Unit)
                            check(releaseCas.await(5, TimeUnit.SECONDS)) { "Timed out releasing result CAS" }
                        }
                        next
                    }
                    results += states.value
                }, newSession = { PlayerTaskSession(sequence.incrementAndGet(), track.id) },
                closeSearch = {}, refreshLyricsFonts = {}, nextOperationId = { sequence.incrementAndGet() })
            share.dispatch(PlayerIntent.BeginLyricsShare)
            withTimeout(5_000) { states.first { it.hasFreshSharePreview } }
            share.dispatch(PlayerIntent.BuildLyricsSharePreview)
            withTimeout(5_000) { started.await() }
            freeze.set(true)
            if (fontCompletion) {
                share.dispatch(PlayerIntent.RequestLyricsShareFonts)
                fontResult.complete(listOf(LyricsShareFontOption("Serif", "Serif")))
            } else {
                oldResult.complete(if (fail) Result.failure(IllegalStateException("obsolete"))
                    else Result.success("obsolete".encodeToByteArray()))
            }
            withTimeout(5_000) { resultAtCas.await() }
            // The sole worker cannot run the content observer until this completion is released.
            // Preview acceptance and font reconciliation must both check the full current content.
            states.update { current ->
                if (lyricsChange) current.copy(lyrics = current.lyrics!!.copy(lines = listOf(LyricsLine(0, "Corrected line"))))
                else current.copy(snapshot = current.snapshot.copy(metadataTitle = "Corrected title", metadataArtworkLocator = "/corrected.jpg"))
            }
            releaseCas.countDown()
            val latest = withTimeout(5_000) { latestStarted.await() }
            assertFalse(results.any { it.sharePreviewBytes?.decodeToString() == "obsolete" })
            assertFalse(results.any { it.sharePreviewError != null })
            assertTrue(states.value.isShareRendering)
            if (lyricsChange) assertEquals(listOf("Corrected line"), latest.lyricsLines)
            else {
                assertEquals("Corrected title", latest.title)
                assertEquals("/corrected.jpg", latest.artworkLocator)
            }
            latestResult.complete(Unit)
            withTimeout(5_000) { states.first { it.hasFreshSharePreview && !it.isShareRendering } }
            assertContentEquals(latest.lifecycleImage(), states.value.sharePreviewBytes)
        } finally {
            releaseCas.countDown()
            scope.coroutineContext.job.cancelAndJoin()
            dispatcher.close()
        }
    }
}
