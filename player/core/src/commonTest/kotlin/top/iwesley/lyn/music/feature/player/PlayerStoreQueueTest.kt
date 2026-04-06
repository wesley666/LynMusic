package top.iwesley.lyn.music.feature.player

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

        assertEquals(listOf("track-1", "track-2", "track-3"), playbackRepository.lastPlayTracks?.map { it.id })
        assertEquals(1, playbackRepository.lastPlayStartIndex)
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
    var lastArtworkOverride: String? = null
        private set

    override val snapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot.asStateFlow()

    fun updateSnapshot(snapshot: PlaybackSnapshot) {
        mutableSnapshot.value = snapshot
    }

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
