package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*
import top.iwesley.lyn.music.core.model.*
import top.iwesley.lyn.music.data.repository.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerStoreCommittedOperationsTest {
    @Test fun directApplySurvivesSearchClose() = applyAfter(false, Trigger.Close)
    @Test fun directApplySurvivesQueryEdit() = applyAfter(false, Trigger.Edit)
    @Test fun directApplySurvivesTrackChange() = applyAfter(false, Trigger.Track)
    @Test fun workflowApplySurvivesSearchClose() = applyAfter(true, Trigger.Close)
    @Test fun workflowApplySurvivesQueryEdit() = applyAfter(true, Trigger.Edit)
    @Test fun workflowApplySurvivesTrackChange() = applyAfter(true, Trigger.Track)
    @Test fun saveSurvivesTrackChange() = saveAfter(Trigger.Track)
    @Test fun saveSurvivesShareClose() = saveAfter(Trigger.Close)
    @Test fun applyCompletesWithoutInvalidation() = applyAfter(false, Trigger.None)
    @Test fun saveCompletesWithoutInvalidation() = saveAfter(Trigger.None)

    @Test fun applyCapturesTrackBeforeWorkerStarts() = withFixture {
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate))
        store.dispatch(PlayerIntent.EndManualLyricsSearch)
        nextTrack()
        drain()
        lyrics.gate.complete(Unit)
        drain()
        assertEquals("original", lyrics.committedTrackId)
        assertEquals("next", store.state.value.effectiveSnapshot.currentTrack?.id)
        assertEquals("auto:next", store.state.value.lyrics?.rawPayload)
        assertTrue(playback.artworkOverrides.isEmpty())
    }

    @Test fun oldApplicationDoesNotCloseOrReplaceNewSession() = withFixture {
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate))
        drain()
        store.dispatch(PlayerIntent.EndManualLyricsSearch)
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        store.dispatch(PlayerIntent.ManualLyricsTitleChanged("New query"))
        val newSession = store.state.value.manualLyricsSearchSession
        lyrics.gate.complete(Unit)
        drain()
        assertTrue(lyrics.committed)
        assertEquals(newSession, store.state.value.manualLyricsSearchSession)
        assertEquals("New query", store.state.value.manualLyricsTitle)
        assertEquals("auto:original", store.state.value.lyrics?.rawPayload)
        assertEquals(listOf(candidate.artworkLocator), playback.artworkOverrides)
        assertNull(store.state.value.message)
        assertEquals(1, store.state.value.operationNotices.size)
    }

    @Test fun editingQueryDuringApplyKeepsTheNewQueryOpen() = withFixture {
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate))
        drain()
        store.dispatch(PlayerIntent.ManualLyricsTitleChanged("New query"))
        val session = store.state.value.manualLyricsSearchSession
        lyrics.gate.complete(Unit)
        drain()
        assertTrue(lyrics.committed)
        assertEquals(session, store.state.value.manualLyricsSearchSession)
        assertEquals("New query", store.state.value.manualLyricsTitle)
    }

    @Test fun applicationsCommitInSubmissionOrderAndLatestSelectionWins() = withFixture {
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate))
        drain()
        val next = candidate.copy(document = doc.copy(rawPayload = "new selection"))
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(next))
        drain()
        assertEquals(1, lyrics.applyCalls)
        lyrics.gate.complete(Unit)
        drain()
        assertEquals(listOf(doc.rawPayload, "new selection"), lyrics.committedPayloads)
        assertEquals("new selection", store.state.value.lyrics?.rawPayload)
        assertNull(store.state.value.manualLyricsSearchSession)
    }

    @Test fun repeatedClickDoesNotSubmitTheSamePendingApplicationTwice() = withFixture {
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        repeat(2) { store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate)) }
        lyrics.gate.complete(Unit)
        drain()
        assertEquals(1, lyrics.applyCalls)
    }

    @Test fun applicationRequestIdentityIsPublishedBeforeTheWorkerStartsAndClearedWithTheSession() = withFixture {
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate))
        val firstRequest = assertNotNull(store.state.value.manualLyricsApplicationRequestId)
        assertEquals(0, lyrics.applyCalls)
        store.dispatch(PlayerIntent.EndManualLyricsSearch)
        assertNull(store.state.value.manualLyricsApplicationRequestId)
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate))
        assertNotEquals(firstRequest, assertNotNull(store.state.value.manualLyricsApplicationRequestId))
        nextTrack()
        drain()
        assertNull(store.state.value.manualLyricsApplicationRequestId)
        lyrics.gate.complete(Unit)
        drain()
        assertEquals(2, lyrics.applyCalls)
        assertEquals("original", lyrics.committedTrackId)
        assertEquals("auto:next", store.state.value.lyrics?.rawPayload)
    }

    @Test fun currentFailureClearsApplicationRequestAndAllowsRetryingTheSameSelection() = withFixture {
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        val session = store.state.value.manualLyricsSearchSession
        lyrics.failNextApply = true
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate))
        val failedRequest = assertNotNull(store.state.value.manualLyricsApplicationRequestId)
        lyrics.gate.complete(Unit)
        drain()
        assertEquals(session, store.state.value.manualLyricsSearchSession)
        assertNull(store.state.value.manualLyricsApplicationRequestId)
        assertNotNull(store.state.value.message)
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate))
        assertNotEquals(failedRequest, assertNotNull(store.state.value.manualLyricsApplicationRequestId))
        drain()
        assertEquals(2, lyrics.applyCalls)
        assertTrue(lyrics.committed)
        assertNull(store.state.value.manualLyricsApplicationRequestId)
        assertNull(store.state.value.manualLyricsSearchSession)
    }

    @Test fun invalidatedApplicationDoesNotDeduplicateAResubmissionInTheSameSession() = withFixture {
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate))
        val oldRequest = assertNotNull(store.state.value.manualLyricsApplicationRequestId)
        drain()
        store.dispatch(PlayerIntent.ManualLyricsTitleChanged("New query"))
        assertNull(store.state.value.manualLyricsApplicationRequestId)
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate))
        val newRequest = assertNotNull(store.state.value.manualLyricsApplicationRequestId)
        assertNotEquals(oldRequest, newRequest)
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate))
        assertEquals(newRequest, store.state.value.manualLyricsApplicationRequestId)
        lyrics.gate.complete(Unit)
        drain()
        assertEquals(2, lyrics.applyCalls)
        assertEquals(1, store.state.value.operationNotices.size)
        assertNull(store.state.value.manualLyricsSearchSession)
    }

    @Test fun failedOldApplicationLeavesNewSessionUntouchedAndNextWriteStillRuns() = withFixture {
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        lyrics.failNextApply = true
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate))
        drain()
        store.dispatch(PlayerIntent.EndManualLyricsSearch)
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        val newSession = store.state.value.manualLyricsSearchSession
        lyrics.gate.complete(Unit)
        drain()
        assertEquals(newSession, store.state.value.manualLyricsSearchSession)
        assertNull(store.state.value.message)
        assertEquals(1, store.state.value.operationNotices.size)
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate))
        drain()
        assertTrue(lyrics.committed)
    }

    @Test fun saveCapturesContentBeforeWorkerStartsAndNeverUsesNextTrack() = withFixture {
        store.dispatch(PlayerIntent.BeginLyricsShare)
        drain()
        val originalBytes = store.state.value.sharePreviewBytes!!.copyOf()
        store.dispatch(PlayerIntent.SaveLyricsShareImage)
        store.dispatch(PlayerIntent.EndLyricsShare)
        nextTrack()
        drain()
        share.gate.complete(Unit)
        drain()
        assertContentEquals(originalBytes, share.savedBytes)
        assertTrue(share.savedName!!.contains("Original"))
        assertNull(store.state.value.shareMessage)
        assertEquals(1, store.state.value.operationNotices.size)
    }

    @Test fun saveRenderingContinuesAfterClosingWhileLivePreviewIsCancelled() = withFixture {
        share.renderGate = CompletableDeferred()
        store.dispatch(PlayerIntent.BeginLyricsShare)
        drain()
        assertTrue(store.state.value.isShareRendering)
        store.dispatch(PlayerIntent.SaveLyricsShareImage)
        drain()
        assertEquals(2, share.renderCalls)
        val originalModel = share.models.first()
        store.dispatch(PlayerIntent.EndLyricsShare)
        nextTrack()
        drain()
        assertEquals(1, share.cancelledRenders)
        share.renderGate!!.complete(Unit)
        share.gate.complete(Unit)
        drain()
        assertTrue(share.written)
        assertEquals(originalModel, share.models.last())
        assertNull(store.state.value.sharePreviewBytes)
    }

    @Test fun oldSaveCompletionDoesNotClearNewSessionsBusyStateOrMessage() = withFixture {
        store.dispatch(PlayerIntent.BeginLyricsShare)
        drain()
        store.dispatch(PlayerIntent.SaveLyricsShareImage)
        drain()
        val oldGate = share.gate
        share.gate = CompletableDeferred()
        store.dispatch(PlayerIntent.EndLyricsShare)
        store.dispatch(PlayerIntent.BeginLyricsShare)
        drain()
        val newSession = store.state.value.lyricsShareSession
        store.dispatch(PlayerIntent.SaveLyricsShareImage)
        oldGate.complete(Unit)
        drain()
        assertEquals(newSession, store.state.value.lyricsShareSession)
        assertTrue(store.state.value.isShareSaving)
        assertNull(store.state.value.shareMessage)
        assertEquals(1, store.state.value.operationNotices.size)
        share.gate.complete(Unit)
        drain()
        assertFalse(store.state.value.isShareSaving)
        assertEquals(2, share.saveCalls)
    }

    @Test fun failedSaveReportsCompletionWithoutMutatingNewShareAndNextSaveStillRuns() = withFixture {
        store.dispatch(PlayerIntent.BeginLyricsShare)
        drain()
        share.failNextSave = true
        store.dispatch(PlayerIntent.SaveLyricsShareImage)
        drain()
        store.dispatch(PlayerIntent.EndLyricsShare)
        store.dispatch(PlayerIntent.BeginLyricsShare)
        drain()
        val session = store.state.value.lyricsShareSession
        share.gate.complete(Unit)
        drain()
        assertEquals(session, store.state.value.lyricsShareSession)
        assertNull(store.state.value.shareMessage)
        val notice = store.state.value.operationNotices.single()
        store.dispatch(PlayerIntent.SaveLyricsShareImage)
        drain()
        assertTrue(share.written)
        store.dispatch(PlayerIntent.DismissOperationNotice(notice.id + 100))
        assertEquals(listOf(notice), store.state.value.operationNotices)
        store.dispatch(PlayerIntent.DismissOperationNotice(notice.id))
        assertTrue(store.state.value.operationNotices.isEmpty())
    }

    @Test fun lateOffsetReadCannotPublishAppliedLyricsIntoNewSearchSession() = withFixture {
        lyrics.offsetGate = CompletableDeferred()
        lyrics.gate.complete(Unit)
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate))
        drain()
        assertTrue(lyrics.committed)
        store.dispatch(PlayerIntent.EndManualLyricsSearch)
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        val newSession = store.state.value.manualLyricsSearchSession
        lyrics.offsetGate!!.complete(Unit)
        drain()
        assertEquals(newSession, store.state.value.manualLyricsSearchSession)
        assertEquals("auto:original", store.state.value.lyrics?.rawPayload)
        assertEquals(listOf(candidate.artworkLocator), playback.artworkOverrides)
    }

    @Test fun dismissingOldCompletionCannotDiscardLaterCompletion() = withFixture {
        lyrics.gate.complete(Unit)
        repeat(2) {
            store.dispatch(PlayerIntent.BeginManualLyricsSearch)
            store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate))
            store.dispatch(PlayerIntent.EndManualLyricsSearch)
            drain()
        }
        val notices = store.state.value.operationNotices
        assertEquals(2, notices.size)
        store.dispatch(PlayerIntent.DismissOperationNotice(notices.first().id))
        assertEquals(listOf(notices.last()), store.state.value.operationNotices)
        store.dispatch(PlayerIntent.DismissOperationNotice(notices.first().id))
        assertEquals(listOf(notices.last()), store.state.value.operationNotices)
    }


    @Test fun editingShareContentKeepsThePendingSaveBusy() = withFixture {
        store.dispatch(PlayerIntent.BeginLyricsShare)
        drain()
        store.dispatch(PlayerIntent.RequestLyricsShareFonts)
        drain()
        store.dispatch(PlayerIntent.SaveLyricsShareImage)
        drain()
        val requestId = assertNotNull(store.state.value.lyricsShareSaveRequestId)
        for (intent in listOf(
            PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT),
            PlayerIntent.LyricsShareFontChanged("Another font"),
            PlayerIntent.ClearLyricsSelection,
            PlayerIntent.ToggleLyricsLineSelection(0),
        )) {
            store.dispatch(intent)
            drain()
            assertTrue(store.state.value.isShareSaving, "Lost pending save after $intent")
            assertEquals(requestId, store.state.value.lyricsShareSaveRequestId)
        }
        assertEquals("Another font", store.state.value.selectedLyricsShareFontKey)
        share.gate.complete(Unit)
        drain()
        assertFalse(store.state.value.isShareSaving)
        assertNull(store.state.value.lyricsShareSaveRequestId)
    }

    @Test fun olderSaveSuccessCannotFinishANewerRequestInTheSameSession() = saveAgainInSameSession(failFirst = false)
    @Test fun olderSaveFailureCannotFinishANewerRequestInTheSameSession() = saveAgainInSameSession(failFirst = true)

    private fun saveAgainInSameSession(failFirst: Boolean) = withFixture {
        store.dispatch(PlayerIntent.BeginLyricsShare)
        drain()
        share.failNextSave = failFirst
        store.dispatch(PlayerIntent.SaveLyricsShareImage)
        drain()
        val session = store.state.value.lyricsShareSession
        val firstId = assertNotNull(store.state.value.lyricsShareSaveRequestId)
        val firstGate = share.gate
        share.gate = CompletableDeferred()
        store.dispatch(PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT))
        drain()
        store.dispatch(PlayerIntent.SaveLyricsShareImage)
        val secondId = assertNotNull(store.state.value.lyricsShareSaveRequestId)
        assertNotEquals(firstId, secondId)
        firstGate.complete(Unit)
        drain()
        assertEquals(session, store.state.value.lyricsShareSession)
        assertEquals(2, share.saveCalls)
        assertFalse(share.gate.isCompleted)
        assertTrue(store.state.value.isShareSaving)
        assertEquals(secondId, store.state.value.lyricsShareSaveRequestId)
        assertNull(store.state.value.shareMessage)
        assertEquals(firstId, store.state.value.operationNotices.single().id)
        share.gate.complete(Unit)
        drain()
        assertFalse(store.state.value.isShareSaving)
        assertNull(store.state.value.lyricsShareSaveRequestId)
        assertEquals(UiText.Raw("Saved"), store.state.value.shareMessage)
    }

    @Test fun repeatedSaveOfTheSameContentIsSubmittedOnce() = withFixture {
        store.dispatch(PlayerIntent.BeginLyricsShare)
        drain()
        repeat(3) { store.dispatch(PlayerIntent.SaveLyricsShareImage) }
        drain()
        assertEquals(1, share.saveCalls)
        share.gate.complete(Unit)
        drain()
        assertEquals(1, share.saveCalls)
        assertNull(store.state.value.lyricsShareSaveRequestId)
    }

    @Test fun failedSaveCanBeRetriedWithoutChangingTheContent() = withFixture {
        store.dispatch(PlayerIntent.BeginLyricsShare)
        drain()
        share.failNextSave = true
        store.dispatch(PlayerIntent.SaveLyricsShareImage)
        share.gate.complete(Unit)
        drain()
        assertFalse(store.state.value.isShareSaving)
        assertNull(store.state.value.lyricsShareSaveRequestId)
        store.dispatch(PlayerIntent.SaveLyricsShareImage)
        drain()
        assertEquals(2, share.saveCalls)
        assertTrue(share.written)
        assertEquals(UiText.Raw("Saved"), store.state.value.shareMessage)
    }

    @Test fun closingShareClearsItsSaveIdentityWithoutCancellingTheWrite() = withFixture {
        store.dispatch(PlayerIntent.BeginLyricsShare)
        drain()
        store.dispatch(PlayerIntent.SaveLyricsShareImage)
        val requestId = assertNotNull(store.state.value.lyricsShareSaveRequestId)
        drain()
        store.dispatch(PlayerIntent.EndLyricsShare)
        assertNull(store.state.value.lyricsShareSaveRequestId)
        assertFalse(store.state.value.isShareSaving)
        share.gate.complete(Unit)
        drain()
        assertTrue(share.written)
        assertEquals(requestId, store.state.value.operationNotices.single().id)
    }

    @Test fun artworkOnlyApplicationUpdatesTheOriginalTrackAfterSearchCloses() = withFixture {
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate, LyricsSearchApplyMode.ARTWORK_ONLY))
        drain()
        store.dispatch(PlayerIntent.EndManualLyricsSearch)
        lyrics.gate.complete(Unit)
        drain()
        assertEquals(candidate.artworkLocator, playback.snapshot.value.currentDisplayArtworkLocator)
        assertEquals("auto:original", store.state.value.lyrics?.rawPayload)
        assertNull(store.state.value.manualLyricsSearchSession)
    }

    @Test fun workflowArtworkUpdatesTheOriginalTrackAfterSearchCloses() = withFixture {
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        store.dispatch(PlayerIntent.ApplyWorkflowSongCandidate(
            WorkflowSongCandidate("source", "Source", "candidate", "Candidate", emptyList(), imageUrl = "workflow-cover"),
            LyricsSearchApplyMode.ARTWORK_ONLY,
        ))
        drain()
        store.dispatch(PlayerIntent.EndManualLyricsSearch)
        lyrics.gate.complete(Unit)
        drain()
        assertEquals("workflow-cover", playback.snapshot.value.currentDisplayArtworkLocator)
    }

    @Test fun consecutiveCoverApplicationsPublishInOrderAndKeepTheLatestCover() = withFixture {
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate, LyricsSearchApplyMode.ARTWORK_ONLY))
        drain()
        store.dispatch(PlayerIntent.EndManualLyricsSearch)
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate.copy(artworkLocator = "newer-cover"), LyricsSearchApplyMode.ARTWORK_ONLY))
        lyrics.gate.complete(Unit)
        drain()
        assertEquals(listOf(candidate.artworkLocator, "newer-cover"), playback.artworkOverrides)
        assertEquals("newer-cover", playback.snapshot.value.currentDisplayArtworkLocator)
        assertNull(store.state.value.manualLyricsSearchSession)
    }

    @Test fun laterLyricsOnlyApplicationDoesNotDiscardCommittedCover() = withFixture {
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate, LyricsSearchApplyMode.ARTWORK_ONLY))
        drain()
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate.copy(document = doc.copy(rawPayload = "new lyrics")), LyricsSearchApplyMode.LYRICS_ONLY))
        lyrics.gate.complete(Unit)
        drain()
        assertEquals(listOf(candidate.artworkLocator), playback.artworkOverrides)
        assertEquals(candidate.artworkLocator, playback.snapshot.value.currentDisplayArtworkLocator)
        assertEquals("new lyrics", store.state.value.lyrics?.rawPayload)
    }

    @Test fun trackChangeWhilePublishingCoverDoesNotModifyTheNewTrack() = withFixture {
        lyrics.gate.complete(Unit)
        playback.artworkGate = CompletableDeferred()
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate, LyricsSearchApplyMode.ARTWORK_ONLY))
        drain()
        assertEquals(1, playback.artworkAttempts)
        nextTrack()
        drain()
        playback.artworkGate!!.complete(Unit)
        drain()
        assertEquals("next", playback.snapshot.value.currentTrack?.id)
        assertNull(playback.snapshot.value.currentDisplayArtworkLocator)
        assertTrue(playback.artworkOverrides.isEmpty())
    }

    private fun applyAfter(workflow: Boolean, trigger: Trigger) = withFixture {
        store.dispatch(PlayerIntent.BeginManualLyricsSearch)
        store.dispatch(if (workflow) PlayerIntent.ApplyWorkflowSongCandidate(
            WorkflowSongCandidate("source", "Source", "candidate", "Candidate", emptyList()))
        else PlayerIntent.ApplyManualLyricsCandidate(candidate))
        drain()
        assertTrue(lyrics.started)
        when (trigger) {
            Trigger.Close -> store.dispatch(PlayerIntent.EndManualLyricsSearch)
            Trigger.Edit -> store.dispatch(PlayerIntent.ManualLyricsTitleChanged("New search query"))
            Trigger.Track -> nextTrack()
            Trigger.None -> Unit
        }
        drain()
        lyrics.gate.complete(Unit)
        drain()
        assertTrue(lyrics.committed, "Confirmed apply was discarded: workflow=$workflow trigger=$trigger cancelled=${lyrics.cancelled}")
        assertEquals("original", lyrics.committedTrackId)
    }

    private fun saveAfter(trigger: Trigger) = withFixture {
        store.dispatch(PlayerIntent.BeginLyricsShare)
        drain()
        assertNotNull(store.state.value.sharePreviewBytes)
        store.dispatch(PlayerIntent.SaveLyricsShareImage)
        drain()
        assertTrue(share.started)
        when (trigger) {
            Trigger.Close -> store.dispatch(PlayerIntent.EndLyricsShare)
            Trigger.Track -> nextTrack()
            else -> Unit
        }
        drain()
        share.gate.complete(Unit)
        drain()
        assertTrue(share.written, "Confirmed save was discarded: trigger=$trigger cancelled=${share.cancelled}")
    }

    private fun withFixture(block: Fixture.() -> Unit) = runTest {
        val fixture = Fixture(this)
        try { fixture.block() } finally { fixture.close() }
    }
    private enum class Trigger { Close, Edit, Track, None }

    private class Fixture(private val test: TestScope) : AutoCloseable {
        val doc = LyricsDocument(listOf(LyricsLine(0, "Line")), sourceId = "source", rawPayload = "[00:00]Line")
        val candidate = LyricsSearchCandidate("source", "Source", doc, artworkLocator = "https://fixture/cover.png")
        private val scope = CoroutineScope(StandardTestDispatcher(test.testScheduler) + SupervisorJob())
        val playback = CommitPlaybackRepository(PlaybackSnapshot(queue = listOf(
            Track("original", "source", "Original", mediaLocator = "", relativePath = "")), currentIndex = 0))
        val lyrics = CommitLyricsRepository(doc)
        val share = CommitShareService()
        val store = PlayerStore(playback, lyrics, scope, lyricsSharePlatformService = share,
            lyricsShareFontPreferencesStore = object : LyricsShareFontPreferencesStore {
                override val selectedLyricsShareFontKey = MutableStateFlow<String?>(null)
                override suspend fun setSelectedLyricsShareFontKey(value: String?) { selectedLyricsShareFontKey.value = value }
            },
        )
        init { drain() }
        fun drain() = test.testScheduler.advanceUntilIdle()
        fun nextTrack() { playback.snapshot.value = PlaybackSnapshot(queue = listOf(
            Track("next", "source", "Next", mediaLocator = "", relativePath = "")), currentIndex = 0) }
        override fun close() { scope.cancel(); drain() }
    }
}

private class CommitLyricsRepository(private val doc: LyricsDocument) : LyricsRepository {
    val gate = CompletableDeferred<Unit>()
    var started = false
    var cancelled = false
    var committed = false
    var committedTrackId: String? = null
    var offsetGate: CompletableDeferred<Unit>? = null
    override suspend fun readOffset(track: Track, version: String): Long? { offsetGate?.await(); return 0L }
    var applyCalls = 0
    var failNextApply = false
    val committedPayloads = mutableListOf<String>()
    override suspend fun getLyrics(track: Track) = ResolvedLyricsResult(doc.copy(rawPayload = "auto:" + track.id))
    override suspend fun searchLyricsCandidates(track: Track, includeTrackProvidedCandidate: Boolean): List<LyricsSearchCandidate> = emptyList()
    override suspend fun searchWorkflowSongCandidates(track: Track): List<WorkflowSongCandidate> = emptyList()
    override suspend fun resolveWorkflowSongCandidate(track: Track, candidate: WorkflowSongCandidate) = ResolvedLyricsResult(doc)
    override suspend fun applyLyricsCandidate(trackId: String, candidate: LyricsSearchCandidate, mode: LyricsSearchApplyMode) = commit(trackId, candidate.document, mode, candidate.artworkLocator)
    override suspend fun applyWorkflowSongCandidate(trackId: String, candidate: WorkflowSongCandidate, mode: LyricsSearchApplyMode) = commit(trackId, doc, mode, candidate.imageUrl)
    private suspend fun commit(trackId: String, document: LyricsDocument, mode: LyricsSearchApplyMode, artwork: String?): AppliedLyricsResult {
        applyCalls++
        started = true
        try { gate.await() } catch (e: CancellationException) { cancelled = true; throw e }
        if (failNextApply) { failNextApply = false; error("Apply failed") }
        committed = true
        committedTrackId = trackId
        committedPayloads += document.rawPayload
        return AppliedLyricsResult(document.takeUnless { mode == LyricsSearchApplyMode.ARTWORK_ONLY }, artworkLocator = artwork)
    }
}

private class CommitShareService : LyricsSharePlatformService {
    var gate = CompletableDeferred<Unit>()
    var started = false
    var cancelled = false
    var written = false
    var renderGate: CompletableDeferred<Unit>? = null
    var renderCalls = 0
    var cancelledRenders = 0
    var saveCalls = 0
    var failNextSave = false
    var savedBytes: ByteArray? = null
    var savedName: String? = null
    val models = mutableListOf<LyricsShareCardModel>()
    override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> {
        renderCalls++
        models += model
        try { renderGate?.await() } catch (e: CancellationException) { cancelledRenders++; throw e }
        return Result.success(byteArrayOf(1, 2, 3))
    }
    override suspend fun saveImage(pngBytes: ByteArray, suggestedName: String): Result<LyricsShareSaveResult> {
        started = true
        saveCalls++
        try { gate.await() } catch (e: CancellationException) { cancelled = true; throw e }
        if (failNextSave) { failNextSave = false; return Result.failure(IllegalStateException("Save failed")) }
        savedBytes = pngBytes.copyOf()
        savedName = suggestedName
        written = true
        return Result.success(LyricsShareSaveResult(UiText.Raw("Saved")))
    }
    override suspend fun copyImage(pngBytes: ByteArray) = Result.success(Unit)
    override suspend fun copyText(text: String) = Result.success(Unit)
    override suspend fun listAvailableFontFamilies() = Result.success(listOf(LyricsShareFontOption("Serif", "Serif"), LyricsShareFontOption("Another font", "Another font")))
}

internal class CommitPlaybackRepository(initial: PlaybackSnapshot) : PlaybackRepository {
    override val snapshot = MutableStateFlow(initial)
    override suspend fun hydratePersistedQueueIfNeeded() = PlaybackHydrationResult.ExistingPlayback
    override suspend fun playTracks(tracks: List<Track>, startIndex: Int) { snapshot.value = snapshot.value.copy(queue = tracks, currentIndex = startIndex) }
    override suspend fun playTransientTracks(tracks: List<Track>, startIndex: Int) = playTracks(tracks, startIndex)
    override suspend fun prepareExternalPlaybackQueue(tracks: List<Track>, startIndex: Int): PlaybackSnapshot? = null
    override suspend fun playQueueIndex(index: Int) { snapshot.value = snapshot.value.copy(currentIndex = index) }
    override suspend fun togglePlayPause() = Unit
    override suspend fun pause() = Unit
    override suspend fun skipNext() = Unit
    override suspend fun skipPrevious() = Unit
    override suspend fun seekTo(positionMs: Long) = Unit
    override suspend fun setVolume(volume: Float) = Unit
    override suspend fun cycleMode() = Unit
    val artworkOverrides = mutableListOf<String?>()
    var artworkGate: CompletableDeferred<Unit>? = null
    var artworkAttempts = 0
    override suspend fun overrideCurrentTrackArtwork(artworkLocator: String?, expectedTrackId: String?) {
        artworkAttempts++
        artworkGate?.await()
        if (snapshot.value.currentTrack?.id == expectedTrackId) {
            artworkOverrides += artworkLocator
            snapshot.value = snapshot.value.copy(metadataArtworkLocator = artworkLocator)
        }
    }
    override suspend fun close() = Unit
}
