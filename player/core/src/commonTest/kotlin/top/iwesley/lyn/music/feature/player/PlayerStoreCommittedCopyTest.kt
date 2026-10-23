package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.repository.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerStoreCommittedCopyTest {
    @Test fun imageCopySurvivesClose() = copyAfter(true, Exit.Close)
    @Test fun imageCopySurvivesTrackChange() = copyAfter(true, Exit.Track)
    @Test fun textCopySurvivesClose() = copyAfter(false, Exit.Close)
    @Test fun textCopySurvivesTrackChange() = copyAfter(false, Exit.Track)
    @Test fun imageCopyCompletesWithoutExit() = copyAfter(true, Exit.None)
    @Test fun textCopyCompletesWithoutExit() = copyAfter(false, Exit.None)
    @Test fun imageCopyCapturesContentBeforeWorkerStarts() = captureBeforeWorker(true)
    @Test fun textCopyCapturesContentBeforeWorkerStarts() = captureBeforeWorker(false)
    @Test fun oldCopyDoesNotClearNewSessionsBusyState() = copyAcrossSessions(false)
    @Test fun oldCopyDoesNotClearNewSongsBusyState() = copyAcrossSessions(true)

    @Test fun committedRenderingSurvivesCloseWhileLivePreviewIsCancelled() = withFixture {
        share.renderGate = CompletableDeferred()
        open()
        assertTrue(store.state.value.isShareRendering)
        val capturedModel = share.models.single()
        store.dispatch(PlayerIntent.CopyLyricsShareImage)
        drain()
        assertEquals(2, share.models.size)
        assertEquals(capturedModel, share.models.last())
        closeShare()
        nextTrack()
        drain()
        assertEquals(1, share.cancelledRenders)
        share.renderGate!!.complete(Unit)
        drain()
        release(0)
        assertEquals(listOf("image:" + capturedModel.copyTestBytes().decodeToString()), share.writes)
        assertNull(store.state.value.sharePreviewBytes)
        assertNull(store.state.value.shareMessage)
        assertEquals(1, store.state.value.operationNotices.size)
    }

    @Test fun queuedTextWaitsForEarlierImageRenderingAndBothUseCapturedContent() = withFixture {
        share.renderGate = CompletableDeferred()
        open()
        val imageModel = share.models.single()
        store.dispatch(PlayerIntent.CopyLyricsShareImage)
        drain()
        store.dispatch(PlayerIntent.ClearLyricsSelection)
        store.dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        drain()
        store.dispatch(PlayerIntent.CopyLyricsShareText)
        val lastRequest = store.state.value.lyricsShareCopyRequestId
        drain()
        assertTrue(share.attempts.isEmpty(), "Text must not overtake image rendering")
        share.renderGate!!.complete(Unit)
        drain()
        assertEquals(1, share.attempts.size)
        release(0)
        assertEquals(lastRequest, store.state.value.lyricsShareCopyRequestId)
        assertTrue(store.state.value.isShareCopying)
        assertNull(store.state.value.shareMessage)
        release(1)
        assertEquals(listOf("image:" + imageModel.copyTestBytes().decodeToString(), "text:original second"), share.writes)
        assertEquals(1, store.state.value.operationNotices.size)
        assertFalse(store.state.value.isShareCopying)
    }

    @Test fun selectionTemplateAndFontEditsKeepPendingCopyOwnership() = withFixture {
        open()
        store.dispatch(PlayerIntent.CopyLyricsShareText)
        drain()
        val request = store.state.value.lyricsShareCopyRequestId
        val edits = listOf(
            PlayerIntent.ToggleLyricsLineSelection(1),
            PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT),
            PlayerIntent.RequestLyricsShareFonts,
            PlayerIntent.LyricsShareFontChanged("Another font"),
            PlayerIntent.ClearLyricsSelection,
        )
        for (edit in edits) {
            store.dispatch(edit)
            drain()
            assertTrue(store.state.value.isShareCopying)
            assertEquals(request, store.state.value.lyricsShareCopyRequestId)
        }
        release(0)
        assertEquals(listOf("text:original first"), share.writes)
        assertFalse(store.state.value.isShareCopying)
    }

    @Test fun threeMixedCopiesKeepLatestOwnershipWhenMiddleCopyFails() = withFixture {
        open()
        share.failedCopies += 1
        store.dispatch(PlayerIntent.CopyLyricsShareText)
        store.dispatch(PlayerIntent.CopyLyricsShareImage)
        store.dispatch(PlayerIntent.CopyLyricsShareText)
        val latest = assertNotNull(store.state.value.lyricsShareCopyRequestId)
        drain()
        release(0)
        assertEquals(latest, store.state.value.lyricsShareCopyRequestId)
        assertTrue(store.state.value.isShareCopying)
        release(1)
        assertEquals(latest, store.state.value.lyricsShareCopyRequestId)
        assertTrue(store.state.value.isShareCopying)
        assertNull(store.state.value.shareMessage)
        assertEquals(2, store.state.value.operationNotices.size)
        release(2)
        assertEquals(listOf("text:original first", "text:original first"), share.writes)
        assertNull(store.state.value.lyricsShareCopyRequestId)
        assertFalse(store.state.value.isShareCopying)
        assertNotNull(store.state.value.shareMessage)
    }

    @Test fun failedImageCopyAllowsRetry() = failedCopyCanRetry(true)
    @Test fun thrownTextCopyFailureAllowsRetry() = failedCopyCanRetry(false)

    @Test fun oldFailureDoesNotReplaceNewSessionMessageAndWorkerContinues() = withFixture {
        open()
        share.failedCopies += 0
        store.dispatch(PlayerIntent.CopyLyricsShareImage)
        drain()
        closeShare()
        open()
        store.dispatch(PlayerIntent.CopyLyricsShareText)
        val request = store.state.value.lyricsShareCopyRequestId
        drain()
        release(0)
        assertEquals(request, store.state.value.lyricsShareCopyRequestId)
        assertTrue(store.state.value.isShareCopying)
        assertNull(store.state.value.shareMessage)
        assertEquals(1, store.state.value.operationNotices.size)
        release(1)
        assertEquals(listOf("text:original first"), share.writes)
    }

    @Test fun renderFailureReportsCompletionWithoutChangingNewSessionPreview() = withFixture {
        share.renderGate = CompletableDeferred()
        open()
        store.dispatch(PlayerIntent.CopyLyricsShareImage)
        drain()
        share.failedRenders += 1 // The committed render; the first render belongs to the live session.
        closeShare()
        share.renderGate!!.complete(Unit)
        share.renderGate = null
        open()
        val preview = store.state.value.sharePreviewBytes
        store.dispatch(PlayerIntent.CopyLyricsShareText)
        drain()
        assertEquals(1, store.state.value.operationNotices.size)
        assertNull(store.state.value.sharePreviewError)
        assertContentEquals(preview, store.state.value.sharePreviewBytes)
        assertTrue(store.state.value.isShareCopying)
        release(0)
        assertEquals(listOf("text:original first"), share.writes)
    }

    @Test fun blockedSaveDoesNotBlockClipboardQueue() = withFixture {
        open()
        store.dispatch(PlayerIntent.SaveLyricsShareImage)
        store.dispatch(PlayerIntent.CopyLyricsShareText)
        drain()
        release(0)
        assertEquals(listOf("text:original first"), share.writes)
        assertTrue(store.state.value.isShareSaving)
        assertFalse(store.state.value.isShareCopying)
    }

    @Test fun storeShutdownStillCancelsCommittedCopy() = withFixture {
        open()
        store.dispatch(PlayerIntent.CopyLyricsShareText)
        drain()
        close()
        assertEquals(1, share.cancelledCopies)
        assertTrue(share.writes.isEmpty())
    }

    @Test fun copyWithoutShareSessionIsIgnored() = withFixture {
        store.dispatch(PlayerIntent.CopyLyricsShareImage)
        store.dispatch(PlayerIntent.CopyLyricsShareText)
        drain()
        assertTrue(share.attempts.isEmpty())
        assertFalse(store.state.value.isShareCopying)
        assertNull(store.state.value.lyricsShareCopyRequestId)
    }

    private fun copyAfter(image: Boolean, exit: Exit) = withFixture {
        open()
        store.dispatch(copyIntent(image))
        assertTrue(store.state.value.isShareCopying)
        assertNotNull(store.state.value.lyricsShareCopyRequestId)
        drain()
        assertEquals(1, share.attempts.size)
        when (exit) {
            Exit.Close -> closeShare()
            Exit.Track -> nextTrack()
            Exit.None -> Unit
        }
        drain()
        release(0)
        assertEquals(1, share.writes.size)
        assertEquals(0, share.cancelledCopies)
        assertFalse(store.state.value.isShareCopying)
        assertNull(store.state.value.lyricsShareCopyRequestId)
        if (exit == Exit.None) {
            assertNotNull(store.state.value.shareMessage)
            assertTrue(store.state.value.operationNotices.isEmpty())
        } else {
            assertNull(store.state.value.lyricsShareSession)
            assertNull(store.state.value.shareMessage)
            assertEquals(1, store.state.value.operationNotices.size)
        }
    }

    private fun captureBeforeWorker(image: Boolean) = withFixture {
        open()
        val preview = store.state.value.sharePreviewBytes!!
        val expected = if (image) "image:" + preview.decodeToString() else "text:original first"
        store.dispatch(copyIntent(image))
        preview.fill(0) // The submitted image must own a copy of cached preview bytes.
        closeShare()
        nextTrack()
        drain()
        release(0)
        assertEquals(listOf(expected), share.writes)
        assertEquals("next", store.state.value.effectiveSnapshot.currentTrack?.id)
        assertNull(store.state.value.sharePreviewBytes)
        assertEquals(1, store.state.value.operationNotices.size)
    }

    private fun copyAcrossSessions(changeTrack: Boolean) = withFixture {
        open()
        store.dispatch(PlayerIntent.CopyLyricsShareImage)
        drain()
        val expectedImage = share.attempts.single()
        closeShare()
        if (changeTrack) { nextTrack(); drain() }
        open()
        val session = store.state.value.lyricsShareSession
        val preview = store.state.value.sharePreviewBytes
        store.dispatch(PlayerIntent.CopyLyricsShareText)
        val request = store.state.value.lyricsShareCopyRequestId
        drain()
        release(0)
        assertEquals(session, store.state.value.lyricsShareSession)
        assertEquals(request, store.state.value.lyricsShareCopyRequestId)
        assertTrue(store.state.value.isShareCopying)
        assertNull(store.state.value.shareMessage)
        assertContentEquals(preview, store.state.value.sharePreviewBytes)
        release(1)
        assertEquals(listOf(expectedImage, "text:" + if (changeTrack) "next first" else "original first"), share.writes)
        assertFalse(store.state.value.isShareCopying)
        assertEquals(1, store.state.value.operationNotices.size)
    }

    private fun failedCopyCanRetry(image: Boolean) = withFixture {
        open()
        if (image) share.failedCopies += 0 else share.thrownCopies += 0
        store.dispatch(copyIntent(image))
        val failedRequest = store.state.value.lyricsShareCopyRequestId
        drain()
        release(0)
        assertFalse(store.state.value.isShareCopying)
        assertNull(store.state.value.lyricsShareCopyRequestId)
        assertNotNull(store.state.value.shareMessage)
        store.dispatch(copyIntent(image))
        assertNotEquals(failedRequest, store.state.value.lyricsShareCopyRequestId)
        assertNull(store.state.value.shareMessage)
        drain()
        release(1)
        assertEquals(1, share.writes.size)
        assertFalse(store.state.value.isShareCopying)
    }

    private fun copyIntent(image: Boolean) = if (image) PlayerIntent.CopyLyricsShareImage else PlayerIntent.CopyLyricsShareText
    private enum class Exit { Close, Track, None }
    private fun withFixture(block: Fixture.() -> Unit) = runTest {
        val fixture = Fixture(this)
        try { fixture.block() } finally { fixture.close() }
    }
    private class Fixture(private val test: TestScope) : AutoCloseable {
        private val scope = CoroutineScope(StandardTestDispatcher(test.testScheduler) + SupervisorJob())
        val track = Track("original", "source", "Original", mediaLocator = "", relativePath = "")
        val playback = CommitPlaybackRepository(PlaybackSnapshot(queue = listOf(track), currentIndex = 0))
        val share = CopyTestShareService()
        val store = PlayerStore(playback, CopyTestLyricsRepository(), scope, lyricsSharePlatformService = share,
            lyricsShareFontPreferencesStore = object : LyricsShareFontPreferencesStore {
                override val selectedLyricsShareFontKey = MutableStateFlow<String?>(null)
                override suspend fun setSelectedLyricsShareFontKey(value: String?) { selectedLyricsShareFontKey.value = value }
            })
        init { drain() }
        fun drain() = test.testScheduler.advanceUntilIdle()
        fun open() { store.dispatch(PlayerIntent.BeginLyricsShare); drain() }
        fun closeShare() { store.dispatch(PlayerIntent.EndLyricsShare); drain() }
        fun nextTrack() { playback.snapshot.value = PlaybackSnapshot(queue = listOf(track.copy(id = "next", title = "Next")), currentIndex = 0) }
        fun release(index: Int) { share.copyGates[index].complete(Unit); drain() }
        override fun close() { scope.cancel(); drain() }
    }
}

internal fun LyricsShareCardModel.copyTestBytes() = "$title|$template|$fontKey|${lyricsLines.joinToString("/")}".encodeToByteArray()

internal class CopyTestShareService : LyricsSharePlatformService {
    val copyGates = mutableListOf<CompletableDeferred<Unit>>()
    val saveGate = CompletableDeferred<Unit>()
    var renderGate: CompletableDeferred<Unit>? = null
    val models = mutableListOf<LyricsShareCardModel>()
    val attempts = mutableListOf<String>()
    val writes = mutableListOf<String>()
    val failedCopies = mutableSetOf<Int>()
    val thrownCopies = mutableSetOf<Int>()
    val failedRenders = mutableSetOf<Int>()
    var cancelledCopies = 0
    var cancelledRenders = 0
    override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> {
        val index = models.size
        models += model
        try { renderGate?.await() } catch (e: CancellationException) { cancelledRenders++; throw e }
        return if (index in failedRenders) Result.failure(IllegalStateException("render failed")) else Result.success(model.copyTestBytes())
    }
    override suspend fun copyImage(pngBytes: ByteArray) = write("image:" + pngBytes.decodeToString())
    override suspend fun copyText(text: String) = write("text:" + text)
    private suspend fun write(value: String): Result<Unit> {
        val index = attempts.size
        attempts += value
        val gate = copyGates.getOrNull(index) ?: CompletableDeferred<Unit>().also { copyGates += it }
        try { gate.await() } catch (e: CancellationException) { cancelledCopies++; throw e }
        if (index in thrownCopies) error("clipboard unavailable")
        if (index in failedCopies) return Result.failure(IllegalStateException("clipboard unavailable"))
        writes += value
        return Result.success(Unit)
    }
    override suspend fun saveImage(pngBytes: ByteArray, suggestedName: String): Result<LyricsShareSaveResult> {
        saveGate.await()
        return Result.success(LyricsShareSaveResult(UiText.Raw("saved")))
    }
    override suspend fun listAvailableFontFamilies() = Result.success(listOf(LyricsShareFontOption("Serif", "Serif"), LyricsShareFontOption("Another font", "Another font")))
}

internal class CopyTestLyricsRepository : LyricsRepository {
    override suspend fun getLyrics(track: Track) = ResolvedLyricsResult(LyricsDocument(
        listOf(LyricsLine(0, track.id + " first"), LyricsLine(1000, track.id + " second")), sourceId = "source", rawPayload = track.id,
    ))
    override suspend fun searchLyricsCandidates(track: Track, includeTrackProvidedCandidate: Boolean) = emptyList<LyricsSearchCandidate>()
    override suspend fun searchWorkflowSongCandidates(track: Track) = emptyList<WorkflowSongCandidate>()
    override suspend fun resolveWorkflowSongCandidate(track: Track, candidate: WorkflowSongCandidate): ResolvedLyricsResult = error("Not used")
    override suspend fun applyLyricsCandidate(trackId: String, candidate: LyricsSearchCandidate, mode: LyricsSearchApplyMode): AppliedLyricsResult = error("Not used")
    override suspend fun applyWorkflowSongCandidate(trackId: String, candidate: WorkflowSongCandidate, mode: LyricsSearchApplyMode): AppliedLyricsResult = error("Not used")
}
