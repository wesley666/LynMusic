package top.iwesley.lyn.music

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
import kotlin.test.assertTrue
import top.iwesley.lyn.music.core.model.LyricsDocument
import top.iwesley.lyn.music.core.model.LyricsLine
import top.iwesley.lyn.music.core.model.LyricsSearchCandidate
import top.iwesley.lyn.music.core.model.PlaybackSnapshot
import top.iwesley.lyn.music.core.model.Track
import top.iwesley.lyn.music.core.model.WorkflowSongCandidate
import top.iwesley.lyn.music.data.repository.LyricsRepository
import top.iwesley.lyn.music.data.repository.PlaybackRepository
import top.iwesley.lyn.music.data.repository.AppliedWorkflowLyricsResult
import top.iwesley.lyn.music.data.repository.ResolvedLyricsResult
import top.iwesley.lyn.music.feature.player.PlayerIntent
import top.iwesley.lyn.music.feature.player.PlayerStore

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerStoreManualLyricsSearchTest {

    @Test
    fun `opening manual lyrics search prefills current display values`() = runTest {
        val track = sampleTrack()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val playbackRepository = FakePlaybackRepository(
            PlaybackSnapshot(
                queue = listOf(track),
                currentIndex = 0,
                metadataTitle = "显示标题",
                metadataArtistName = "显示歌手",
                metadataAlbumTitle = "显示专辑",
            ),
        )
        val lyricsRepository = FakeLyricsRepository()
        val store = PlayerStore(playbackRepository, lyricsRepository, scope)

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenManualLyricsSearch)
        advanceUntilIdle()

        val state = store.state.value
        assertTrue(state.isManualLyricsSearchVisible)
        assertEquals("显示标题", state.manualLyricsTitle)
        assertEquals("显示歌手", state.manualLyricsArtistName)
        assertEquals("显示专辑", state.manualLyricsAlbumTitle)
        scope.cancel()
    }

    @Test
    fun `manual search uses edited fields and applying result updates lyrics state`() = runTest {
        val track = sampleTrack()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val candidate = LyricsSearchCandidate(
            sourceId = "manual-source",
            sourceName = "手动源",
            document = LyricsDocument(
                lines = listOf(
                    LyricsLine(timestampMs = 1_000L, text = "第一句"),
                    LyricsLine(timestampMs = 2_000L, text = "第二句"),
                ),
                sourceId = "manual-source",
                rawPayload = "[00:01.00]第一句\n[00:02.00]第二句",
            ),
            artworkLocator = "/tmp/manual-cover.jpg",
        )
        val playbackRepository = FakePlaybackRepository(
            PlaybackSnapshot(
                queue = listOf(track),
                currentIndex = 0,
                positionMs = 1_500L,
            ),
        )
        val lyricsRepository = FakeLyricsRepository(
            searchResults = listOf(candidate),
            appliedDocument = candidate.document,
        )
        val store = PlayerStore(playbackRepository, lyricsRepository, scope)

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenManualLyricsSearch)
        store.dispatch(PlayerIntent.ManualLyricsTitleChanged("手动标题"))
        store.dispatch(PlayerIntent.ManualLyricsArtistChanged("手动歌手"))
        store.dispatch(PlayerIntent.ManualLyricsAlbumChanged("手动专辑"))
        advanceUntilIdle()

        store.dispatch(PlayerIntent.SearchManualLyrics)
        advanceUntilIdle()

        assertEquals("手动标题", lyricsRepository.lastSearchTrack?.title)
        assertEquals("手动歌手", lyricsRepository.lastSearchTrack?.artistName)
        assertEquals("手动专辑", lyricsRepository.lastSearchTrack?.albumTitle)
        assertEquals(false, lyricsRepository.lastIncludeTrackProvidedCandidate)
        assertEquals(listOf(candidate), store.state.value.manualLyricsResults)

        store.dispatch(PlayerIntent.ApplyManualLyricsCandidate(candidate))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(track.id, lyricsRepository.appliedTrackId)
        assertEquals(candidate, lyricsRepository.appliedCandidate)
        assertEquals(candidate.document, state.lyrics)
        assertEquals(0, state.highlightedLineIndex)
        assertFalse(state.isManualLyricsSearchVisible)
        assertTrue(state.manualLyricsResults.isEmpty())
        assertEquals("/tmp/manual-cover.jpg", playbackRepository.snapshot.value.currentDisplayArtworkLocator)
        scope.cancel()
    }

    @Test
    fun `manual search hides current track provided candidate after form changes`() = runTest {
        val track = sampleTrack()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val currentTrackCandidate = LyricsSearchCandidate(
            sourceId = "embedded-tag",
            sourceName = "歌曲标签",
            document = LyricsDocument(
                lines = listOf(LyricsLine(timestampMs = null, text = "当前歌曲歌词")),
                sourceId = "embedded-tag",
                rawPayload = "当前歌曲歌词",
            ),
            isTrackProvided = true,
        )
        val externalCandidate = LyricsSearchCandidate(
            sourceId = "remote-source",
            sourceName = "远程源",
            document = LyricsDocument(
                lines = listOf(LyricsLine(timestampMs = null, text = "外部歌词")),
                sourceId = "remote-source",
                rawPayload = "外部歌词",
            ),
        )
        val playbackRepository = FakePlaybackRepository(
            PlaybackSnapshot(
                queue = listOf(track),
                currentIndex = 0,
            ),
        )
        val lyricsRepository = FakeLyricsRepository(
            searchResults = listOf(currentTrackCandidate, externalCandidate),
        )
        val store = PlayerStore(playbackRepository, lyricsRepository, scope)

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenManualLyricsSearch)
        store.dispatch(PlayerIntent.ManualLyricsTitleChanged("另一首歌"))
        advanceUntilIdle()

        store.dispatch(PlayerIntent.SearchManualLyrics)
        advanceUntilIdle()

        assertEquals(false, lyricsRepository.lastIncludeTrackProvidedCandidate)
        assertEquals(listOf(externalCandidate), store.state.value.manualLyricsResults)
        scope.cancel()
    }

    @Test
    fun `manual search keeps current track provided candidate when form still matches current song`() = runTest {
        val track = sampleTrack()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val currentTrackCandidate = LyricsSearchCandidate(
            sourceId = "embedded-tag",
            sourceName = "歌曲标签",
            document = LyricsDocument(
                lines = listOf(LyricsLine(timestampMs = null, text = "当前歌曲歌词")),
                sourceId = "embedded-tag",
                rawPayload = "当前歌曲歌词",
            ),
            isTrackProvided = true,
        )
        val playbackRepository = FakePlaybackRepository(
            PlaybackSnapshot(
                queue = listOf(track),
                currentIndex = 0,
            ),
        )
        val lyricsRepository = FakeLyricsRepository(
            searchResults = listOf(currentTrackCandidate),
        )
        val store = PlayerStore(playbackRepository, lyricsRepository, scope)

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenManualLyricsSearch)
        advanceUntilIdle()

        store.dispatch(PlayerIntent.SearchManualLyrics)
        advanceUntilIdle()

        assertEquals(true, lyricsRepository.lastIncludeTrackProvidedCandidate)
        assertEquals(listOf(currentTrackCandidate), store.state.value.manualLyricsResults)
        scope.cancel()
    }

    @Test
    fun `manual search exposes workflow song candidates and applying one updates lyrics`() = runTest {
        val track = sampleTrack()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val workflowCandidate = WorkflowSongCandidate(
            sourceId = "workflow-1",
            sourceName = "Workflow 源",
            id = "song-42",
            title = "工作流标题",
            artists = listOf("工作流歌手"),
            album = "工作流专辑",
            durationSeconds = 123,
            imageUrl = "https://img.example.com/cover.jpg",
        )
        val appliedDocument = LyricsDocument(
            lines = listOf(LyricsLine(timestampMs = 500L, text = "Workflow 第一句")),
            sourceId = "workflow-1",
            rawPayload = "[00:00.50]Workflow 第一句",
        )
        val playbackRepository = FakePlaybackRepository(
            PlaybackSnapshot(
                queue = listOf(track),
                currentIndex = 0,
                positionMs = 700L,
            ),
        )
        val lyricsRepository = FakeLyricsRepository(
            workflowResults = listOf(workflowCandidate),
            appliedDocument = appliedDocument,
        )
        val store = PlayerStore(playbackRepository, lyricsRepository, scope)

        advanceUntilIdle()
        store.dispatch(PlayerIntent.OpenManualLyricsSearch)
        store.dispatch(PlayerIntent.SearchManualLyrics)
        advanceUntilIdle()

        assertEquals(listOf(workflowCandidate), store.state.value.manualWorkflowSongResults)
        assertEquals("原始标题", lyricsRepository.lastWorkflowSearchTrack?.title)

        store.dispatch(PlayerIntent.ApplyWorkflowSongCandidate(workflowCandidate))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(track.id, lyricsRepository.appliedTrackId)
        assertEquals(workflowCandidate, lyricsRepository.appliedWorkflowCandidate)
        assertEquals(appliedDocument, state.lyrics)
        assertEquals(0, state.highlightedLineIndex)
        assertFalse(state.isManualLyricsSearchVisible)
        assertTrue(state.manualWorkflowSongResults.isEmpty())
        assertEquals("https://img.example.com/cover.jpg", playbackRepository.snapshot.value.currentDisplayArtworkLocator)
        scope.cancel()
    }

    private fun sampleTrack(): Track {
        return Track(
            id = "track-1",
            sourceId = "local-1",
            title = "原始标题",
            artistName = "原始歌手",
            albumTitle = "原始专辑",
            durationMs = 123_000L,
            mediaLocator = "file:///music/song.mp3",
            relativePath = "song.mp3",
        )
    }
}

private class FakePlaybackRepository(
    initialSnapshot: PlaybackSnapshot,
) : PlaybackRepository {
    private val mutableSnapshot = MutableStateFlow(initialSnapshot)

    override val snapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot.asStateFlow()

    override suspend fun playTracks(tracks: List<Track>, startIndex: Int) = Unit
    override suspend fun togglePlayPause() = Unit
    override suspend fun skipNext() = Unit
    override suspend fun skipPrevious() = Unit
    override suspend fun seekTo(positionMs: Long) = Unit
    override suspend fun setVolume(volume: Float) = Unit
    override suspend fun cycleMode() = Unit
    override suspend fun overrideCurrentTrackArtwork(artworkLocator: String?) {
        val snapshot = mutableSnapshot.value
        val currentTrack = snapshot.currentTrack ?: return
        val currentIndex = snapshot.currentIndex
        if (currentIndex !in snapshot.queue.indices) return
        val updatedQueue = snapshot.queue.toMutableList().also { queue ->
            queue[currentIndex] = currentTrack.copy(artworkLocator = artworkLocator)
        }
        mutableSnapshot.value = snapshot.copy(
            queue = updatedQueue,
            metadataArtworkLocator = artworkLocator,
        )
    }
    override suspend fun close() = Unit
}

private class FakeLyricsRepository(
    private val searchResults: List<LyricsSearchCandidate> = emptyList(),
    private val workflowResults: List<WorkflowSongCandidate> = emptyList(),
    private val appliedDocument: LyricsDocument? = null,
) : LyricsRepository {
    var lastSearchTrack: Track? = null
        private set
    var lastIncludeTrackProvidedCandidate: Boolean? = null
        private set
    var lastWorkflowSearchTrack: Track? = null
        private set
    var appliedTrackId: String? = null
        private set
    var appliedCandidate: LyricsSearchCandidate? = null
        private set
    var appliedWorkflowCandidate: WorkflowSongCandidate? = null
        private set

    override suspend fun getLyrics(track: Track): ResolvedLyricsResult? = null

    override suspend fun searchLyricsCandidates(track: Track, includeTrackProvidedCandidate: Boolean): List<LyricsSearchCandidate> {
        lastSearchTrack = track
        lastIncludeTrackProvidedCandidate = includeTrackProvidedCandidate
        return if (includeTrackProvidedCandidate) {
            searchResults
        } else {
            searchResults.filter { !it.isTrackProvided }
        }
    }

    override suspend fun searchWorkflowSongCandidates(track: Track): List<WorkflowSongCandidate> {
        lastWorkflowSearchTrack = track
        return workflowResults
    }

    override suspend fun resolveWorkflowSongCandidate(track: Track, candidate: WorkflowSongCandidate): ResolvedLyricsResult {
        return ResolvedLyricsResult(
            document = appliedDocument ?: error("No applied document configured"),
            artworkLocator = candidate.imageUrl,
        )
    }

    override suspend fun applyLyricsCandidate(trackId: String, candidate: LyricsSearchCandidate): LyricsDocument {
        appliedTrackId = trackId
        appliedCandidate = candidate
        return appliedDocument ?: candidate.document
    }

    override suspend fun applyWorkflowSongCandidate(trackId: String, candidate: WorkflowSongCandidate): AppliedWorkflowLyricsResult {
        appliedTrackId = trackId
        appliedWorkflowCandidate = candidate
        return AppliedWorkflowLyricsResult(
            document = appliedDocument ?: error("No applied document configured"),
            artworkLocator = candidate.imageUrl,
        )
    }
}
