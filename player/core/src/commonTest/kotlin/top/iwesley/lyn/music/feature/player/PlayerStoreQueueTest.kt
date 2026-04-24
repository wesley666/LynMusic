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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import top.iwesley.lyn.music.core.model.LyricsSearchApplyMode
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
import top.iwesley.lyn.music.core.model.PlaybackMode
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WorkflowSongCandidate
import top.iwesley.lyn.music.data.repository.AppliedLyricsResult
import top.iwesley.lyn.music.data.repository.LyricsRepository
import top.iwesley.lyn.music.data.repository.PlaybackRepository
import top.iwesley.lyn.music.data.repository.ResolvedLyricsResult

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerStoreQueueTest {

    @Test
    fun `queue visibility intent updates state`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val store = PlayerStore(playbackRepository, NoopQueueLyricsRepository(), scope)

        advanceUntilIdle()
        store.dispatch(PlayerIntent.QueueVisibilityChanged(true))
        advanceUntilIdle()

        assertEquals(true, store.state.value.isQueueVisible)
        scope.cancel()
    }

    @Test
    fun `play queue index jumps within current queue and closes drawer`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val store = PlayerStore(playbackRepository, NoopQueueLyricsRepository(), scope)

        advanceUntilIdle()
        store.dispatch(PlayerIntent.QueueVisibilityChanged(true))
        advanceUntilIdle()
        store.dispatch(PlayerIntent.PlayQueueIndex(1))
        advanceUntilIdle()

        assertEquals(1, playbackRepository.lastPlayQueueIndex)
        assertEquals(null, playbackRepository.lastPlayTracks)
        assertEquals("track-2", store.state.value.snapshot.currentTrack?.id)
        assertFalse(store.state.value.isQueueVisible)
        scope.cancel()
    }

    @Test
    fun `null current track closes queue drawer automatically`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val store = PlayerStore(playbackRepository, NoopQueueLyricsRepository(), scope)

        advanceUntilIdle()
        store.dispatch(PlayerIntent.QueueVisibilityChanged(true))
        advanceUntilIdle()
        playbackRepository.updateSnapshot(PlaybackSnapshot(mode = PlaybackMode.ORDER))
        advanceUntilIdle()

        assertFalse(store.state.value.isQueueVisible)
        scope.cancel()
    }

    @Test
    fun `playback error updates transient message once per track and error`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val store = PlayerStore(playbackRepository, NoopQueueLyricsRepository(), scope)

        advanceUntilIdle()
        playbackRepository.updateSnapshot(sampleSnapshot().copy(errorMessage = "访问歌曲失败"))
        advanceUntilIdle()

        assertEquals("访问歌曲失败", store.state.value.message)

        store.dispatch(PlayerIntent.ClearMessage)
        advanceUntilIdle()
        playbackRepository.updateSnapshot(sampleSnapshot().copy(positionMs = 1_000L, errorMessage = "访问歌曲失败"))
        advanceUntilIdle()

        assertEquals(null, store.state.value.message)
        scope.cancel()
    }

    @Test
    fun `same playback error is shown again for a different track`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val store = PlayerStore(playbackRepository, NoopQueueLyricsRepository(), scope)

        advanceUntilIdle()
        playbackRepository.updateSnapshot(sampleSnapshot().copy(errorMessage = "无法播放当前歌曲"))
        advanceUntilIdle()
        store.dispatch(PlayerIntent.ClearMessage)
        advanceUntilIdle()

        playbackRepository.updateSnapshot(sampleSnapshot().copy(currentIndex = 1, errorMessage = "无法播放当前歌曲"))
        advanceUntilIdle()

        assertEquals("无法播放当前歌曲", store.state.value.message)
        scope.cancel()
    }

    @Test
    fun `invalid queue index leaves playback state unchanged`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val store = PlayerStore(playbackRepository, NoopQueueLyricsRepository(), scope)

        advanceUntilIdle()
        store.dispatch(PlayerIntent.QueueVisibilityChanged(true))
        advanceUntilIdle()
        store.dispatch(PlayerIntent.PlayQueueIndex(99))
        advanceUntilIdle()

        assertEquals(null, playbackRepository.lastPlayTracks)
        assertEquals(null, playbackRepository.lastPlayQueueIndex)
        assertEquals("track-1", store.state.value.snapshot.currentTrack?.id)
        assertEquals(true, store.state.value.isQueueVisible)
        scope.cancel()
    }

    @Test
    fun `manual apply uses repository returned artwork locator`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val lyricsRepository = RecordingQueueLyricsRepository(
            applyResult = AppliedLyricsResult(
                document = LyricsDocument(
                    lines = listOf(LyricsLine(timestampMs = null, text = "manual line")),
                    sourceId = "direct-source",
                    rawPayload = "manual line",
                ),
                artworkLocator = "/tmp/cache/manual.jpg",
            ),
        )
        val store = PlayerStore(playbackRepository, lyricsRepository, scope)

        advanceUntilIdle()
        store.dispatch(
            PlayerIntent.ApplyManualLyricsCandidate(
                LyricsSearchCandidate(
                    sourceId = "direct-source",
                    sourceName = "Direct",
                    document = LyricsDocument(
                        lines = listOf(LyricsLine(timestampMs = null, text = "candidate line")),
                        sourceId = "direct-source",
                        rawPayload = "candidate line",
                    ),
                    artworkLocator = "https://img.example.com/raw.jpg",
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals("/tmp/cache/manual.jpg", playbackRepository.lastArtworkOverride)
        assertEquals("/tmp/cache/manual.jpg", store.state.value.snapshot.currentTrack?.artworkLocator)
        scope.cancel()
    }

    @Test
    fun `playback snapshot keeps up while previous automatic lyrics request is still pending`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakeQueuePlaybackRepository(sampleSnapshot())
        val lyricsRepository = DeferredQueueLyricsRepository()
        val store = PlayerStore(playbackRepository, lyricsRepository, scope)

        advanceUntilIdle()
        assertEquals(listOf("track-1"), lyricsRepository.requestedTrackIds)

        playbackRepository.updateSnapshot(sampleSnapshot().copy(currentIndex = 2))
        advanceUntilIdle()

        assertEquals("track-3", store.state.value.snapshot.currentTrack?.id)
        assertEquals(true, store.state.value.isLyricsLoading)
        assertEquals(listOf("track-1", "track-3"), lyricsRepository.requestedTrackIds)

        lyricsRepository.complete(
            trackId = "track-1",
            result = resolvedLyricsResult(
                sourceId = "source-track-1",
                line = "lyrics for track-1",
                artworkLocator = "/tmp/track-1.jpg",
            ),
        )
        advanceUntilIdle()

        assertEquals("track-3", store.state.value.snapshot.currentTrack?.id)
        assertEquals(null, store.state.value.lyrics)
        assertEquals(null, playbackRepository.lastArtworkOverride)

        lyricsRepository.complete(
            trackId = "track-3",
            result = resolvedLyricsResult(
                sourceId = "source-track-3",
                line = "lyrics for track-3",
                artworkLocator = "/tmp/track-3.jpg",
            ),
        )
        advanceUntilIdle()

        assertEquals("track-3", store.state.value.snapshot.currentTrack?.id)
        assertEquals("lyrics for track-3", store.state.value.lyrics?.rawPayload)
        assertEquals("/tmp/track-3.jpg", playbackRepository.lastArtworkOverride)
        scope.cancel()
    }

    private fun sampleSnapshot(): PlaybackSnapshot {
        return PlaybackSnapshot(
            queue = listOf(
                sampleTrack("track-1", "First Song"),
                sampleTrack("track-2", "Second Song"),
                sampleTrack("track-3", "Third Song"),
            ),
            currentIndex = 0,
            mode = PlaybackMode.ORDER,
            durationMs = 180_000L,
        )
    }

    private fun sampleTrack(id: String, title: String): Track {
        return Track(
            id = id,
            sourceId = "local-1",
            title = title,
            artistName = "Artist A",
            albumTitle = "Album A",
            durationMs = 180_000L,
            mediaLocator = "file:///music/$id.mp3",
            relativePath = "$title.mp3",
        )
    }
}

private class FakeQueuePlaybackRepository(
    initialSnapshot: PlaybackSnapshot,
) : PlaybackRepository {
    private val mutableSnapshot = MutableStateFlow(initialSnapshot)

    var lastPlayTracks: List<Track>? = null
        private set
    var lastPlayStartIndex: Int? = null
        private set
    var lastPlayQueueIndex: Int? = null
        private set
    var lastArtworkOverride: String? = null
        private set

    override val snapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot.asStateFlow()

    fun updateSnapshot(snapshot: PlaybackSnapshot) {
        mutableSnapshot.value = snapshot
    }

    override suspend fun hydratePersistedQueueIfNeeded() = Unit

    override suspend fun playTracks(tracks: List<Track>, startIndex: Int) {
        lastPlayTracks = tracks
        lastPlayStartIndex = startIndex
        val targetIndex = startIndex.coerceIn(0, tracks.lastIndex)
        mutableSnapshot.value = mutableSnapshot.value.copy(
            queue = tracks,
            currentIndex = targetIndex,
            durationMs = tracks[targetIndex].durationMs,
            metadataTitle = null,
            metadataArtistName = null,
            metadataAlbumTitle = null,
            metadataArtworkLocator = null,
        )
    }

    override suspend fun playQueueIndex(index: Int) {
        lastPlayQueueIndex = index
        val target = mutableSnapshot.value.queue.getOrNull(index) ?: return
        mutableSnapshot.value = mutableSnapshot.value.copy(
            currentIndex = index,
            durationMs = target.durationMs,
            metadataTitle = null,
            metadataArtistName = null,
            metadataAlbumTitle = null,
            metadataArtworkLocator = null,
        )
    }

    override suspend fun togglePlayPause() = Unit

    override suspend fun skipNext() = Unit

    override suspend fun skipPrevious() = Unit

    override suspend fun seekTo(positionMs: Long) = Unit

    override suspend fun setVolume(volume: Float) = Unit

    override suspend fun cycleMode() = Unit

    override suspend fun overrideCurrentTrackArtwork(artworkLocator: String?) {
        lastArtworkOverride = artworkLocator
        val currentIndex = mutableSnapshot.value.currentIndex
        val currentTrack = mutableSnapshot.value.currentTrack ?: return
        val updatedQueue = mutableSnapshot.value.queue.toMutableList()
        updatedQueue[currentIndex] = currentTrack.copy(artworkLocator = artworkLocator ?: currentTrack.artworkLocator)
        mutableSnapshot.value = mutableSnapshot.value.copy(queue = updatedQueue)
    }

    override suspend fun close() = Unit
}

private class NoopQueueLyricsRepository : LyricsRepository {
    override suspend fun getLyrics(track: Track): ResolvedLyricsResult? = null

    override suspend fun searchLyricsCandidates(
        track: Track,
        includeTrackProvidedCandidate: Boolean,
    ): List<LyricsSearchCandidate> = emptyList()

    override suspend fun applyLyricsCandidate(
        trackId: String,
        candidate: LyricsSearchCandidate,
        mode: LyricsSearchApplyMode,
    ): AppliedLyricsResult {
        error("Not used in queue tests")
    }

    override suspend fun searchWorkflowSongCandidates(track: Track): List<WorkflowSongCandidate> = emptyList()

    override suspend fun resolveWorkflowSongCandidate(track: Track, candidate: WorkflowSongCandidate): ResolvedLyricsResult {
        error("Not used in queue tests")
    }

    override suspend fun applyWorkflowSongCandidate(
        trackId: String,
        candidate: WorkflowSongCandidate,
        mode: LyricsSearchApplyMode,
    ): AppliedLyricsResult {
        error("Not used in queue tests")
    }
}

private class RecordingQueueLyricsRepository(
    private val applyResult: AppliedLyricsResult,
) : LyricsRepository {
    override suspend fun getLyrics(track: Track): ResolvedLyricsResult? = null

    override suspend fun searchLyricsCandidates(
        track: Track,
        includeTrackProvidedCandidate: Boolean,
    ): List<LyricsSearchCandidate> = emptyList()

    override suspend fun applyLyricsCandidate(
        trackId: String,
        candidate: LyricsSearchCandidate,
        mode: LyricsSearchApplyMode,
    ): AppliedLyricsResult {
        return applyResult
    }

    override suspend fun searchWorkflowSongCandidates(track: Track): List<WorkflowSongCandidate> = emptyList()

    override suspend fun resolveWorkflowSongCandidate(track: Track, candidate: WorkflowSongCandidate): ResolvedLyricsResult {
        error("Not used in queue tests")
    }

    override suspend fun applyWorkflowSongCandidate(
        trackId: String,
        candidate: WorkflowSongCandidate,
        mode: LyricsSearchApplyMode,
    ): AppliedLyricsResult {
        error("Not used in queue tests")
    }
}

private class DeferredQueueLyricsRepository : LyricsRepository {
    private val pendingResults = mutableMapOf<String, CompletableDeferred<ResolvedLyricsResult?>>()

    val requestedTrackIds = mutableListOf<String>()

    fun complete(trackId: String, result: ResolvedLyricsResult?) {
        pendingResults.getOrPut(trackId) { CompletableDeferred() }.complete(result)
    }

    override suspend fun getLyrics(track: Track): ResolvedLyricsResult? {
        requestedTrackIds += track.id
        return pendingResults.getOrPut(track.id) { CompletableDeferred() }.await()
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
        error("Not used in queue tests")
    }

    override suspend fun searchWorkflowSongCandidates(track: Track): List<WorkflowSongCandidate> = emptyList()

    override suspend fun resolveWorkflowSongCandidate(track: Track, candidate: WorkflowSongCandidate): ResolvedLyricsResult {
        error("Not used in queue tests")
    }

    override suspend fun applyWorkflowSongCandidate(
        trackId: String,
        candidate: WorkflowSongCandidate,
        mode: LyricsSearchApplyMode,
    ): AppliedLyricsResult {
        error("Not used in queue tests")
    }
}

private fun resolvedLyricsResult(
    sourceId: String,
    line: String,
    artworkLocator: String? = null,
): ResolvedLyricsResult {
    return ResolvedLyricsResult(
        document = LyricsDocument(
            lines = listOf(LyricsLine(timestampMs = null, text = line)),
            sourceId = sourceId,
            rawPayload = line,
        ),
        artworkLocator = artworkLocator,
    )
}
