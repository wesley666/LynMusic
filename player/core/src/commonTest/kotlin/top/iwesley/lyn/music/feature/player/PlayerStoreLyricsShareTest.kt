package top.iwesley.lyn.music.feature.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine
import top.iwesley.lyn.music.core.model.LyricsSearchApplyMode
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
import top.iwesley.lyn.music.core.model.LyricsShareCardModel
import top.iwesley.lyn.music.core.model.LyricsSharePlatformService
import top.iwesley.lyn.music.core.model.LyricsShareSaveResult
import top.iwesley.lyn.music.core.model.LyricsShareTemplate
import top.iwesley.lyn.music.core.model.PlaybackMode
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WorkflowSongCandidate
import top.iwesley.lyn.music.data.repository.AppliedLyricsResult
import top.iwesley.lyn.music.data.repository.LyricsRepository
import top.iwesley.lyn.music.data.repository.PlaybackRepository
import top.iwesley.lyn.music.data.repository.ResolvedLyricsResult

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerStoreLyricsShareTest {

    @Test
    fun `open lyrics share preselects highlighted line and builds preview`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val lyrics = syncedLyrics()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeLyricsSharePlaybackRepository(
            PlaybackSnapshot(
                queue = listOf(track),
                currentIndex = 0,
                mode = PlaybackMode.ORDER,
                positionMs = 1_500L,
            ),
        )
        val shareService = FakeLyricsSharePlatformService()
        val store = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = FakeLyricsShareRepository(lyrics),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()

        val state = store.state.value
        assertTrue(state.isLyricsShareVisible)
        assertEquals(setOf(0), state.selectedLyricsLineIndices)
        assertEquals(listOf("第一句"), state.shareCardModel?.lyricsLines)
        assertEquals(1, shareService.buildPreviewCalls)
        assertEquals(FakeLyricsSharePlatformService.previewBytes.toList(), state.sharePreviewBytes?.toList())
        assertEquals(setOf(0), state.sharePreviewSelection)
        assertTrue(state.hasFreshSharePreview)
        scope.cancel()
    }

    @Test
    fun `toggling lyrics selection rebuilds preview`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val lyrics = syncedLyrics()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val shareService = FakeLyricsSharePlatformService()
        val store = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                    positionMs = 1_500L,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(lyrics),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()
        store.dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        advanceUntilIdle()

        assertEquals(setOf(0, 1), store.state.value.selectedLyricsLineIndices)
        assertEquals(listOf("第一句", "第二句"), store.state.value.shareCardModel?.lyricsLines)
        assertEquals(2, shareService.buildPreviewCalls)
        assertEquals(setOf(0, 1), store.state.value.sharePreviewSelection)
        assertTrue(store.state.value.hasFreshSharePreview)
        scope.cancel()
    }

    @Test
    fun `toggling lyrics selection keeps previous preview visible while next preview renders`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val lyrics = syncedLyrics()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val delayedPreview = CompletableDeferred<Result<ByteArray>>()
        val shareService = FakeLyricsSharePlatformService(
            previewResults = ArrayDeque(
                listOf(
                    PreviewResponse.Immediate(Result.success(FakeLyricsSharePlatformService.previewBytes)),
                    PreviewResponse.Deferred(delayedPreview),
                ),
            ),
        )
        val store = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                    positionMs = 1_500L,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(lyrics),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()

        val previousPreview = store.state.value.sharePreviewBytes
        store.dispatch(PlayerIntent.ToggleLyricsLineSelection(1))
        runCurrent()

        assertTrue(store.state.value.isShareRendering)
        assertEquals(previousPreview?.toList(), store.state.value.sharePreviewBytes?.toList())
        assertEquals(setOf(0), store.state.value.sharePreviewSelection)
        assertFalse(store.state.value.hasFreshSharePreview)

        delayedPreview.complete(Result.success(byteArrayOf(0x09, 0x08, 0x07, 0x06)))
        advanceUntilIdle()

        assertEquals(setOf(0, 1), store.state.value.sharePreviewSelection)
        assertEquals(byteArrayOf(0x09, 0x08, 0x07, 0x06).toList(), store.state.value.sharePreviewBytes?.toList())
        assertTrue(store.state.value.hasFreshSharePreview)
        scope.cancel()
    }

    @Test
    fun `switching lyrics share template rebuilds preview with selected template`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val shareService = FakeLyricsSharePlatformService(
            previewResults = ArrayDeque(
                listOf(
                    PreviewResponse.Immediate(Result.success(FakeLyricsSharePlatformService.previewBytes)),
                    PreviewResponse.Immediate(Result.success(byteArrayOf(0x05, 0x06, 0x07))),
                ),
            ),
        )
        val store = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                    positionMs = 1_500L,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(syncedLyrics()),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()
        store.dispatch(PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(LyricsShareTemplate.ARTWORK_TINT, state.selectedLyricsShareTemplate)
        assertEquals(LyricsShareTemplate.ARTWORK_TINT, state.sharePreviewTemplate)
        assertEquals(LyricsShareTemplate.ARTWORK_TINT, shareService.lastPreviewModel?.template)
        assertEquals(byteArrayOf(0x05, 0x06, 0x07).toList(), state.sharePreviewBytes?.toList())
        assertTrue(state.hasFreshSharePreview)
        assertEquals(2, shareService.buildPreviewCalls)
        scope.cancel()
    }

    @Test
    fun `track change dismisses lyrics share and clears preview`() = runTest {
        val firstTrack = sampleTrack("track-1", "第一首")
        val secondTrack = sampleTrack("track-2", "第二首")
        val lyrics = syncedLyrics()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeLyricsSharePlaybackRepository(
            PlaybackSnapshot(
                queue = listOf(firstTrack),
                currentIndex = 0,
                positionMs = 1_500L,
            ),
        )
        val store = PlayerStore(
            playbackRepository = playbackRepository,
            lyricsRepository = FakeLyricsShareRepository(lyrics),
            storeScope = scope,
            lyricsSharePlatformService = FakeLyricsSharePlatformService(),
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()
        store.dispatch(PlayerIntent.LyricsShareTemplateChanged(LyricsShareTemplate.ARTWORK_TINT))
        advanceUntilIdle()

        playbackRepository.updateSnapshot(
            PlaybackSnapshot(
                queue = listOf(secondTrack),
                currentIndex = 0,
                positionMs = 0L,
            ),
        )
        advanceUntilIdle()

        val state = store.state.value
        assertFalse(state.isLyricsShareVisible)
        assertTrue(state.selectedLyricsLineIndices.isEmpty())
        assertEquals(LyricsShareTemplate.ARTWORK_TINT, state.selectedLyricsShareTemplate)
        assertNull(state.shareCardModel)
        assertNull(state.sharePreviewBytes)
        scope.cancel()
    }

    @Test
    fun `save and copy require at least one selected lyric line`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val lyrics = pureTextLyrics()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val shareService = FakeLyricsSharePlatformService()
        val store = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(lyrics),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()
        store.dispatch(PlayerIntent.SaveLyricsShareImage)
        advanceUntilIdle()

        assertEquals("请先选择至少一句歌词", store.state.value.shareMessage)
        assertEquals(0, shareService.saveImageCalls)

        store.dispatch(PlayerIntent.CopyLyricsShareImage)
        advanceUntilIdle()

        assertEquals("请先选择至少一句歌词", store.state.value.shareMessage)
        assertEquals(0, shareService.copyImageCalls)
        scope.cancel()
    }

    @Test
    fun `preview failure surfaces visible error`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val shareService = FakeLyricsSharePlatformService(
            previewResult = Result.failure(IllegalStateException("boom")),
        )
        val store = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                    positionMs = 1_500L,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(syncedLyrics()),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()

        assertTrue(store.state.value.sharePreviewError?.contains("boom") == true)
        assertNull(store.state.value.sharePreviewBytes)
        scope.cancel()
    }

    @Test
    fun `save and copy use generated preview bytes`() = runTest {
        val track = sampleTrack("track-1", "第一首")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val shareService = FakeLyricsSharePlatformService()
        val store = PlayerStore(
            playbackRepository = FakeLyricsSharePlaybackRepository(
                PlaybackSnapshot(
                    queue = listOf(track),
                    currentIndex = 0,
                    positionMs = 1_500L,
                ),
            ),
            lyricsRepository = FakeLyricsShareRepository(syncedLyrics()),
            storeScope = scope,
            lyricsSharePlatformService = shareService,
        )

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenLyricsShare)
        advanceUntilIdle()
        store.dispatch(PlayerIntent.CopyLyricsShareImage)
        advanceUntilIdle()
        assertEquals(1, shareService.copyImageCalls)
        assertEquals("图片已复制", store.state.value.shareMessage)

        store.dispatch(PlayerIntent.SaveLyricsShareImage)
        advanceUntilIdle()
        assertEquals(1, shareService.saveImageCalls)
        assertEquals("图片已保存到文件", store.state.value.shareMessage)
        scope.cancel()
    }

    private fun sampleTrack(id: String, title: String): Track {
        return Track(
            id = id,
            sourceId = "local-1",
            title = title,
            artistName = "歌手 A",
            albumTitle = "专辑 A",
            durationMs = 180_000L,
            mediaLocator = "file:///music/$id.mp3",
            relativePath = "$title.mp3",
            artworkLocator = "file:///music/$id.jpg",
        )
    }

    private fun syncedLyrics(): LyricsDocument {
        return LyricsDocument(
            lines = listOf(
                LyricsLine(timestampMs = 1_000L, text = "第一句"),
                LyricsLine(timestampMs = 2_000L, text = "第二句"),
                LyricsLine(timestampMs = 3_000L, text = "第三句"),
            ),
            sourceId = "lyrics-1",
            rawPayload = "[00:01.00]第一句\n[00:02.00]第二句\n[00:03.00]第三句",
        )
    }

    private fun pureTextLyrics(): LyricsDocument {
        return LyricsDocument(
            lines = listOf(
                LyricsLine(timestampMs = null, text = "纯文本第一句"),
                LyricsLine(timestampMs = null, text = ""),
                LyricsLine(timestampMs = null, text = "纯文本第二句"),
            ),
            sourceId = "lyrics-2",
            rawPayload = "纯文本第一句\n\n纯文本第二句",
        )
    }
}

private class FakeLyricsSharePlaybackRepository(
    initialSnapshot: PlaybackSnapshot,
) : PlaybackRepository {
    private val mutableSnapshot = MutableStateFlow(initialSnapshot)

    override val snapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot.asStateFlow()

    fun updateSnapshot(snapshot: PlaybackSnapshot) {
        mutableSnapshot.value = snapshot
    }

    override suspend fun playTracks(tracks: List<Track>, startIndex: Int) = Unit

    override suspend fun togglePlayPause() = Unit

    override suspend fun skipNext() = Unit

    override suspend fun skipPrevious() = Unit

    override suspend fun seekTo(positionMs: Long) = Unit

    override suspend fun setVolume(volume: Float) = Unit

    override suspend fun cycleMode() = Unit

    override suspend fun overrideCurrentTrackArtwork(artworkLocator: String?) = Unit

    override suspend fun close() = Unit
}

private class FakeLyricsShareRepository(
    private val document: LyricsDocument,
) : LyricsRepository {
    override suspend fun getLyrics(track: Track): ResolvedLyricsResult {
        return ResolvedLyricsResult(document = document, artworkLocator = track.artworkLocator)
    }

    override suspend fun searchLyricsCandidates(
        track: Track,
        includeTrackProvidedCandidate: Boolean,
    ): List<LyricsSearchCandidate> = emptyList()

    override suspend fun applyLyricsCandidate(
        trackId: String,
        candidate: LyricsSearchCandidate,
        mode: LyricsSearchApplyMode,
    ): AppliedLyricsResult {
        error("Not used in lyrics share tests")
    }

    override suspend fun searchWorkflowSongCandidates(track: Track): List<WorkflowSongCandidate> = emptyList()

    override suspend fun resolveWorkflowSongCandidate(track: Track, candidate: WorkflowSongCandidate): ResolvedLyricsResult {
        error("Not used in lyrics share tests")
    }

    override suspend fun applyWorkflowSongCandidate(
        trackId: String,
        candidate: WorkflowSongCandidate,
        mode: LyricsSearchApplyMode,
    ): AppliedLyricsResult {
        error("Not used in lyrics share tests")
    }
}

private class FakeLyricsSharePlatformService(
    private val previewResult: Result<ByteArray> = Result.success(previewBytes),
    private val previewResults: ArrayDeque<PreviewResponse> = ArrayDeque(),
    private val saveResult: Result<LyricsShareSaveResult> = Result.success(LyricsShareSaveResult("图片已保存到文件")),
    private val copyResult: Result<Unit> = Result.success(Unit),
) : LyricsSharePlatformService {
    var buildPreviewCalls: Int = 0
        private set
    var saveImageCalls: Int = 0
        private set
    var copyImageCalls: Int = 0
        private set
    var lastPreviewModel: LyricsShareCardModel? = null
        private set

    override suspend fun buildPreview(model: LyricsShareCardModel): Result<ByteArray> {
        buildPreviewCalls += 1
        lastPreviewModel = model
        return previewResults.removeFirstOrNull()?.await() ?: previewResult
    }

    override suspend fun saveImage(pngBytes: ByteArray, suggestedName: String): Result<LyricsShareSaveResult> {
        saveImageCalls += 1
        assertEquals(previewBytes.toList(), pngBytes.toList())
        return saveResult
    }

    override suspend fun copyImage(pngBytes: ByteArray): Result<Unit> {
        copyImageCalls += 1
        assertEquals(previewBytes.toList(), pngBytes.toList())
        return copyResult
    }

    companion object {
        val previewBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
    }
}

private sealed interface PreviewResponse {
    suspend fun await(): Result<ByteArray>

    data class Immediate(
        private val result: Result<ByteArray>,
    ) : PreviewResponse {
        override suspend fun await(): Result<ByteArray> = result
    }

    data class Deferred(
        private val deferred: CompletableDeferred<Result<ByteArray>>,
    ) : PreviewResponse {
        override suspend fun await(): Result<ByteArray> = deferred.await()
    }
}
